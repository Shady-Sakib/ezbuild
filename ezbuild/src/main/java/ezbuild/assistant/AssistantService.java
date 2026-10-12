package ezbuild.assistant;

import ezbuild.build.BuildPlanner;
import ezbuild.build.Catalog;
import ezbuild.build.PartResolver;
import ezbuild.build.PlanHints;
import ezbuild.build.PlanResult;
import ezbuild.dto.BuildPlan;
import ezbuild.dto.BuildRequirements;
import ezbuild.dto.ChatReply;
import ezbuild.dto.Intent;
import ezbuild.dto.ProductFilter;
import ezbuild.model.ChatMessage;
import ezbuild.model.ChatSession;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.model.User;
import ezbuild.repository.ChatMessageRepository;
import ezbuild.repository.ChatSessionRepository;
import ezbuild.repository.ProductRepository;
import ezbuild.security.RateLimiter;
import ezbuild.service.BusinessException;
import ezbuild.service.NotFoundException;
import ezbuild.service.ProductSearchService;
import ezbuild.util.Fmt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Chat pipeline: extract requirements (LLM, regex fills gaps) -> resolve named parts -> planner builds 3
 * tiers -> compatibility check -> LLM explains using catalogue facts (+ web notes) -> saved. Build cards come
 * from planner data, so the model can never change a part or a price.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int DAILY_LIMIT = 100;
    private static final int WEB_WAIT_SECONDS = 15;

    private final AdvisorClient advisor;
    private final RegexExtractor regexExtractor;
    private final TemplateWriter templates;
    private final PartResolver partResolver;
    private final BuildPlanner planner;
    private final ProductRepository productRepository;
    private final ProductSearchService searchService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final RateLimiter rateLimiter;
    private final WebResearch webResearch;

    public ChatReply chat(User user, String sessionId, String message) {
        if (!rateLimiter.tryAcquire("chat-day:" + user.getId(), DAILY_LIMIT, Duration.ofDays(1))) {
            throw new BusinessException("You've reached today's chat limit. Please come back tomorrow.");
        }
        String text = message.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        ChatSession session = sessionId == null || sessionId.isBlank() ? newSession(user, text) : owned(sessionId, user);
        List<String> history = history(session.getId());
        List<BuildPlan> previousBuilds = lastBuilds(session.getId());
        save(session.getId(), "user", text, List.of(), List.of());

        // 1. requirements: model first, regex fills the gaps (or replaces the model when it is down).
        // Web research runs at the same time on the raw message, so the two slow calls overlap.
        BuildRequirements regex = regexExtractor.extract(text);
        CompletableFuture<String> webFuture = regex.intent() == Intent.OFF_TOPIC || text.length() < 12
                ? CompletableFuture.completedFuture("")
                : CompletableFuture.supplyAsync(() -> webNotes(text));
        BuildRequirements extracted = advisor.extract(text, history).map(r -> merge(r, regex)).orElse(regex);
        boolean degraded = "degraded".equals(advisor.mode());

        // 2. follow-ups ("better CPU", "Intel instead", "cheaper") inherit the previous budget and use case
        BuildRequirements prev = session.getLastRequirements();
        boolean refinement = prev != null && !previousBuilds.isEmpty()
                && RegexExtractor.isRefinement(lower) && !RegexExtractor.isComparison(lower);
        if (prev != null && (extracted.wantsBuild() || refinement)) {
            if (!extracted.wantsBuild()) extracted = extracted.withIntent(Intent.BUILD_FROM_BUDGET);
            extracted = extracted.inheritFrom(prev);
            if (RegexExtractor.wantsCheaper(lower) && regex.budgetBdt() == null && extracted.budgetBdt() != null) {
                long cheapestShown = previousBuilds.stream().mapToLong(BuildPlan::total).min().orElse(extracted.budgetBdt());
                extracted = extracted.withBudget(Math.max(10_000, (cheapestShown * 9 / 10) / 1_000 * 1_000));
            }
        }
        if (extracted.budgetBdt() == null && extracted.wantsBuild()) {
            Long earlier = earlierBudget(session.getId());
            if (earlier != null) extracted = extracted.withBudget(earlier);
        }
        final BuildRequirements req = extracted;

        String reply;
        List<BuildPlan> builds = List.of();
        List<String> suggestions = new ArrayList<>();
        List<Product> catalogue = productRepository.findAll().stream().filter(Product::isActive).toList();
        Map<String, Product> byId = catalogue.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        String conversation = history.isEmpty() ? "" : String.join("\n", history);

        switch (req.intent()) {
            case BUILD_FROM_BUDGET, BUILD_AROUND_PARTS -> {
                PartResolver.Resolution res = partResolver.resolve(req.lockedParts(), catalogue);
                if (!res.ambiguous().isEmpty()) {
                    var entry = res.ambiguous().entrySet().iterator().next();
                    reply = "I found a few matches for \"" + entry.getKey() + "\". Which one do you mean?";
                    entry.getValue().forEach(p -> suggestions.add("Build around " + p.getName()));
                    break;
                }
                if (req.budgetBdt() == null) {
                    List<Product> related = categoryProducts(text);
                    String facts = related.isEmpty() ? "" : "=== EZBUILD CATALOGUE FACTS (in stock) ===\n"
                            + related.stream().map(templates::productFacts).collect(Collectors.joining("\n"));
                    String web = awaitWeb(webFuture);
                    reply = advisor.explain(text, context(conversation, "The customer wants something built or bought but has "
                                    + "not given a budget. If they asked about a specific part or feature, answer that first "
                                    + "using the catalogue facts and web notes (name matching products we stock). Then ask "
                                    + "warmly for their budget in taka and main use so you can plan a full build.", facts, web),
                            req.replyLanguage()).orElseGet(() -> templates.askBudget(req)) + sources(web);
                    suggestions.addAll(List.of("80k", "1 lakh", "1.5 lakh"));
                    session.setLastRequirements(req);
                    break;
                }
                PlanHints hints = hints(req, previousBuilds, byId, refinement);
                PlanResult result = planner.plan(req, res.locked(), Catalog.of(catalogue), hints);
                if (result.empty()) {
                    reply = result.minimumBudget() > 0 ? templates.tooLow(req, result.minimumBudget())
                            : "Sorry, I couldn't make a compatible build from what's in stock right now.";
                    if (result.minimumBudget() > 0) suggestions.add("Build for " + Fmt.taka(roundUp(result.minimumBudget())));
                    break;
                }
                builds = result.plans();
                session.setLastRequirements(req);
                String unresolved = res.unresolved().isEmpty() ? ""
                        : "\n\n(I couldn't find " + String.join(", ", res.unresolved()) + " in our stock, so I picked alternatives.)";
                String web = awaitWeb(webFuture);
                String facts = "=== PLAN DATA (prices, parts and specs from the EZBuild catalogue) ===\n"
                        + templates.planSummary(builds, byId, req)
                        + (refinement ? "\n=== PREVIOUS SUGGESTION IN THIS CHAT ===\n" + templates.previousSummary(previousBuilds, byId, req) : "")
                        + (result.notes().isEmpty() ? "" : "\nPlanner notes: " + String.join(" ", result.notes()));
                String task = "Explain these builds to the customer: use case " + req.useCase().getLabel()
                        + ", budget " + Fmt.taka(req.budgetBdt())
                        + (req.focus() == null ? "" : ", they asked to prioritise the " + req.focus().toUpperCase(Locale.ROOT))
                        + (Boolean.TRUE.equals(req.anyBrand()) ? ", they are fine with either Intel or AMD" : "")
                        + (refinement ? ". This is a follow-up: say clearly what changed compared to the previous suggestion and why" : "")
                        + ".";
                reply = advisor.explain(text, context(conversation, task, facts, web), req.replyLanguage())
                        .orElseGet(() -> templates.builds(req, result.plans(), byId)) + unresolved + sources(web);
                suggestions.addAll(followUps(req));
            }
            case CATALOG_QUESTION, EXPLAIN -> {
                List<Product> mentioned = new ArrayList<>(mentioned(req, text));
                for (Product p : categoryProducts(text)) {
                    if (mentioned.size() >= 12) break;
                    if (mentioned.stream().noneMatch(m -> m.getId().equals(p.getId()))) mentioned.add(p);
                }
                String facts = mentioned.isEmpty() ? ""
                        : "=== EZBUILD CATALOGUE FACTS ===\n" + mentioned.stream().map(templates::productFacts).collect(Collectors.joining("\n"));
                String web = awaitWeb(webFuture);
                String task = req.intent() == Intent.CATALOG_QUESTION
                        ? "Answer the customer's product question using the catalogue facts (price, stock, specs). "
                          + "If we don't stock it, say so and suggest the closest item from the facts if any."
                        : "Answer the customer's PC hardware question in a helpful, detailed way. Compare the products "
                          + "using their specs and the web notes, give a clear recommendation for their likely use, "
                          + "and quote EZBuild prices only from the catalogue facts.";
                reply = advisor.explain(text, context(conversation, task, facts, web), req.replyLanguage())
                        .orElseGet(() -> mentioned.isEmpty() ? templates.explainFallback()
                                : "Here's what we have:\n" + mentioned.stream().map(p -> "• " + templates.productFacts(p))
                                .collect(Collectors.joining("\n"))) + sources(web);
                if (!mentioned.isEmpty()) suggestions.add("Build a PC around " + mentioned.get(0).getName());
                if (prev != null) suggestions.add("Show my builds again");
            }
            case OFF_TOPIC -> reply = templates.offTopic(req);
            default -> {
                reply = advisor.explain(text, context(conversation, "Small talk or greeting. Reply naturally in one to three sentences, "
                                + "then offer to plan a PC (ask for budget and use) or answer hardware questions.", "", ""),
                        req.replyLanguage()).orElseGet(() -> templates.greeting(req));
                suggestions.addAll(List.of("Gaming PC 1 lakh", "Office PC 50k", "Editing PC 1.5 lakh"));
            }
        }
        save(session.getId(), "assistant", reply, builds, suggestions);
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session);
        log.info("chat user={} intent={} budget={} focus={} builds={} mode={} web={}", user.getUsername(), req.intent(),
                req.budgetBdt(), req.focus(), builds.size(), advisor.mode(), webResearch.enabled());
        return new ChatReply(session.getId(), reply, builds, suggestions, degraded);
    }

    public List<ChatSession> sessions(User user) {
        return sessionRepository.findTop20ByUserIdOrderByLastActiveAtDesc(user.getId());
    }

    public List<ChatMessage> messages(User user, String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(owned(sessionId, user).getId());
    }

    public void delete(User user, String sessionId) {
        ChatSession s = owned(sessionId, user);
        messageRepository.deleteBySessionId(s.getId());
        sessionRepository.delete(s);
    }

    public String mode() {
        return advisor.mode();
    }


    // ---- conversation helpers

    /**
     * Builds the single context block for the writer model: conversation, task, catalogue facts, web notes.
     */
    static String context(String conversation, String task, String facts, String web) {
        StringBuilder sb = new StringBuilder();
        sb.append("Today's date: ").append(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Dhaka"))).append("\n\n");
        if (!conversation.isBlank()) sb.append("=== CONVERSATION SO FAR (oldest first) ===\n").append(conversation).append("\n\n");
        sb.append("=== YOUR TASK ===\n").append(task).append("\n\n");
        if (!facts.isBlank()) sb.append(facts).append("\n\n");
        if (!web.isBlank()) sb.append("=== WEB NOTES (from a live Google search; use for performance claims, never for prices) ===\n")
                .append(web).append("\n");
        return sb.toString();
    }

    /**
     * "Better CPU" follow-up: each tier's new CPU should beat that tier's previous one (soft rule in the
     * planner).
     */
    private PlanHints hints(BuildRequirements req, List<BuildPlan> previous, Map<String, Product> byId, boolean refinement) {
        if (!refinement || previous.isEmpty() || req.focus() == null) return PlanHints.NONE;
        Map<String, Double> cpu = new java.util.HashMap<>();
        Map<String, Double> gpu = new java.util.HashMap<>();
        for (BuildPlan plan : previous) {
            for (var part : plan.parts()) {
                Product p = byId.get(part.productId());
                if (p == null) continue;
                if (part.category() == ProductCategory.CPU) cpu.merge(plan.tier(), BuildPlanner.cpuScore(p, req.useCase()), Math::max);
                if (part.category() == ProductCategory.GPU) gpu.merge(plan.tier(), BuildPlanner.gpuScore(p), Math::max);
            }
        }
        return "cpu".equals(req.focus()) ? new PlanHints(cpu, Map.of()) : new PlanHints(Map.of(), gpu);
    }


    private String webNotes(String question) {
        return webResearch.research(question).map(n -> n.text() + (n.sources().isEmpty() ? ""
                : "\n[sources: " + String.join(", ", n.sources()) + "]")).orElse("");
    }

    /** Waits at most a few seconds for web notes; a slow search never holds up the reply. */
    private static String awaitWeb(CompletableFuture<String> f) {
        try {
            return f.get(WEB_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.info("Web research took longer than {}s: replying without it", WEB_WAIT_SECONDS);
            return "";
        }
    }

    /** Adds a short "Sources:" footer when web notes were used. */
    private static String sources(String web) {
        int i = web.lastIndexOf("[sources: ");
        if (i < 0) return "";
        return "\n\nSources: " + web.substring(i + 10, web.length() - 1);
    }

    /** Catalogue products named in the message ("7600 vs 12400F"): best text match per name. */
    private List<Product> mentioned(BuildRequirements req, String text) {
        List<String> names = req.lockedParts().isEmpty() ? List.of(text) : req.lockedParts();
        java.util.LinkedHashMap<String, Product> found = new java.util.LinkedHashMap<>();
        for (String q : names) {
            searchService.search(new ProductFilter(q, null, null, null, null, false, false, Map.of(), "relevance", 0,
                            req.lockedParts().isEmpty() ? 4 : 1))
                    .content().forEach(p -> found.putIfAbsent(p.getId(), p));
        }
        return new ArrayList<>(found.values()).subList(0, Math.min(6, found.size()));
    }

    /**
     * In-stock products of any category the message names ("a card that supports DLSS 5" -> graphics cards),
     * cheapest first, so the writer can recommend from real stock.
     */
    private List<Product> categoryProducts(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        java.util.Set<ProductCategory> cats = new java.util.LinkedHashSet<>();
        if (lower.matches(".*\\b(card|gpu|graphics|vga|rtx|gtx|radeon|geforce|dlss|fsr|ray tracing)\\b.*")) cats.add(ProductCategory.GPU);
        if (lower.matches(".*\\b(cpu|processor|ryzen|intel core)\\b.*")) cats.add(ProductCategory.CPU);
        if (lower.matches(".*\\b(ssd|nvme|hdd|storage|hard disk)\\b.*")) cats.add(ProductCategory.STORAGE);
        if (lower.matches(".*\\b(ram|memory|ddr4|ddr5)\\b.*")) cats.add(ProductCategory.RAM);
        if (lower.matches(".*\\b(psu|power supply)\\b.*")) cats.add(ProductCategory.PSU);
        if (lower.matches(".*\\b(monitor|display|screen)\\b.*")) cats.add(ProductCategory.MONITOR);
        if (lower.matches(".*\\b(motherboard|mobo|board)\\b.*")) cats.add(ProductCategory.MOTHERBOARD);
        if (lower.matches(".*\\b(cooler|cooling|aio)\\b.*")) cats.add(ProductCategory.COOLER);
        if (lower.matches(".*\\b(case|casing|cabinet)\\b.*")) cats.add(ProductCategory.CASE);
        List<Product> out = new ArrayList<>();
        for (ProductCategory c : cats) {
            searchService.search(new ProductFilter(null, c, null, null, null, true, false, Map.of(), "price_asc", 0, 12))
                    .content().forEach(out::add);
        }
        return out.subList(0, Math.min(24, out.size()));
    }

    private static List<String> followUps(BuildRequirements req) {
        List<String> list = new ArrayList<>();
        list.add("Better CPU");
        list.add("Better GPU");
        list.add("Cheaper option");
        boolean intel = req.preferredBrands().contains("intel");
        boolean amd = req.preferredBrands().contains("amd") || req.preferredBrands().contains("ryzen");
        list.add(intel ? "AMD instead" : amd ? "Intel instead" : "2TB storage");
        return list;
    }

    /** Builds attached to the most recent assistant reply in this chat. */
    private List<BuildPlan> lastBuilds(String sessionId) {
        for (ChatMessage m : messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, 10))) {
            if ("assistant".equals(m.getRole()) && !m.getBuilds().isEmpty()) return m.getBuilds();
        }
        return List.of();
    }

    /** Model output wins; regex values fill whatever it left empty. */
    static BuildRequirements merge(BuildRequirements llm, BuildRequirements regex) {
        Intent intent = llm.intent();
        if (intent == Intent.SMALLTALK && regex.wantsBuild()) intent = regex.intent();
        List<String> brands = llm.preferredBrands().isEmpty() ? regex.preferredBrands() : llm.preferredBrands();
        List<String> locked = llm.lockedParts().isEmpty() ? regex.lockedParts() : llm.lockedParts();
        return new BuildRequirements(intent,
                llm.budgetBdt() != null ? llm.budgetBdt() : regex.budgetBdt(),
                llm.useCase() == ezbuild.dto.UseCase.GENERAL ? regex.useCase() : llm.useCase(),
                brands, locked,
                llm.mustHave().isEmpty() ? regex.mustHave() : llm.mustHave(),
                llm.storageNeedGb() != null ? llm.storageNeedGb() : regex.storageNeedGb(),
                llm.monitorWanted(),
                regex.replyLanguage().equals("en") ? llm.replyLanguage() : regex.replyLanguage(),
                llm.needsClarification(), llm.clarifyingQuestion(),
                llm.focus() != null ? llm.focus() : regex.focus(),
                Boolean.TRUE.equals(llm.anyBrand()) || Boolean.TRUE.equals(regex.anyBrand()));
    }

    private ChatSession newSession(User user, String firstMessage) {
        ChatSession s = new ChatSession();
        s.setUserId(user.getId());
        s.setTitle(firstMessage.length() > 40 ? firstMessage.substring(0, 40) + "…" : firstMessage);
        return sessionRepository.save(s);
    }

    private ChatSession owned(String sessionId, User user) {
        return sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new NotFoundException("Chat not found."));
    }

    private List<String> history(String sessionId) {
        List<ChatMessage> last = new ArrayList<>(messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId,
                PageRequest.of(0, 10)));
        Collections.reverse(last);
        return last.stream().map(m -> m.getRole() + ": " + truncate(m.getText(), 300)).toList();
    }

    /** "make it cheaper" after "gaming pc 1 lakh": reuse the last budget given in this chat. */
    private Long earlierBudget(String sessionId) {
        List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, 10));
        for (ChatMessage m : msgs) {
            if ("assistant".equals(m.getRole()) && !m.getBuilds().isEmpty()) return m.getBuilds().get(0).budget();
            if ("user".equals(m.getRole())) {
                Long b = regexExtractor.budget(m.getText());
                if (b != null) return b;
            }
        }
        return null;
    }

    private void save(String sessionId, String role, String text, List<BuildPlan> builds, List<String> suggestions) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setText(text);
        m.setBuilds(new ArrayList<>(builds));
        m.setSuggestions(new ArrayList<>(suggestions));
        messageRepository.save(m);
    }

    private static long roundUp(long v) {
        return ((v + 4_999) / 5_000) * 5_000;
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }

}
