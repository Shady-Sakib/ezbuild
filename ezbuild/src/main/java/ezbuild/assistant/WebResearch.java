package ezbuild.assistant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Live web research: one Gemini call with the built-in Google Search tool. Returns grounded notes plus source
 * titles. Notes are for performance/feature claims only; prices, stock and parts always come from our
 * catalogue. Cached 6 h. Off without a Gemini key or with AI_WEB_SEARCH=false; any failure just means no
 * notes.
 */
@Slf4j
@Component
public class WebResearch {

    public record Notes(String text, List<String> sources) { }

    private static final String SYSTEM = """
            You are a PC hardware research assistant for a computer store in Bangladesh.
            Use Google Search to find current, reputable information (reviews and benchmarks from sites such as
            TechPowerUp, Tom's Hardware, GamersNexus, Hardware Unboxed, AnandTech, Puget Systems).
            Answer with 4 to 8 short factual bullet points: relative performance with rough percentages,
            strengths, weaknesses, platform/upgrade notes, known issues. Do NOT mention prices or where to buy.
            If sources disagree, say so briefly. No introduction and no conclusion paragraph.""";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final Map<String, Cached> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > 200;
        }
    };

    private record Cached(Notes notes, Instant at) { }

    public WebResearch(RestClient restClient,
                       @Value("${spring.ai.google.genai.api-key:}") String apiKey,
                       @Value("${ezbuild.ai.research-model:${spring.ai.google.genai.chat.model:gemini-flash-latest}}") String model,
                       @Value("${ezbuild.ai.web-search:true}") boolean enabled) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank() && !"missing-key".equals(apiKey);
        log.info("Assistant web research (Google Search grounding): {}", this.enabled ? "on, model " + model : "off");
    }

    public boolean enabled() {
        return enabled;
    }

    /** Empty when disabled, on error, or when nothing useful came back. */
    public Optional<Notes> research(String question) {
        if (!enabled || question == null || question.isBlank()) return Optional.empty();
        String key = question.strip().toLowerCase();
        synchronized (cache) {
            Cached c = cache.get(key);
            if (c != null && c.at().isAfter(Instant.now().minus(Duration.ofHours(6)))) return Optional.of(c.notes());
        }
        try {
            Map<String, Object> body = Map.of(
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM))),
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text",
                            "Today's date: " + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Dhaka"))
                                    + ". Search for the latest information; newer sources win.\n\n" + question)))),
                    "tools", List.of(Map.of("google_search", Map.of())),
                    "generationConfig", Map.of("temperature", 0.2));
            @SuppressWarnings("unchecked")
            Map<String, Object> res = restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Notes notes = parse(res);
            if (notes == null) return Optional.empty();
            synchronized (cache) {
                cache.put(key, new Cached(notes, Instant.now()));
            }
            return Optional.of(notes);
        } catch (Exception e) {
            log.warn("Web research failed ({}): continuing without web notes", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    static Notes parse(Map<String, Object> res) {
        if (res == null) return null;
        List<Object> candidates = (List<Object>) res.get("candidates");
        if (candidates == null || candidates.isEmpty()) return null;
        Map<String, Object> first = (Map<String, Object>) candidates.get(0);
        StringBuilder text = new StringBuilder();
        Map<String, Object> content = (Map<String, Object>) first.get("content");
        if (content != null && content.get("parts") instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?, ?> m && m.get("text") instanceof String t) text.append(t);
            }
        }
        if (text.toString().isBlank()) return null;
        Set<String> sources = new LinkedHashSet<>();
        if (first.get("groundingMetadata") instanceof Map<?, ?> meta && meta.get("groundingChunks") instanceof List<?> chunks) {
            for (Object chunk : chunks) {
                if (chunk instanceof Map<?, ?> c && c.get("web") instanceof Map<?, ?> web && web.get("title") instanceof String title) {
                    sources.add(title);
                }
            }
        }
        return new Notes(text.toString().strip(), List.copyOf(new ArrayList<>(sources).subList(0, Math.min(5, sources.size()))));
    }
}
