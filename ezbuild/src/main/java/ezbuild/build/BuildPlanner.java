package ezbuild.build;

import ezbuild.dto.BuildPlan;
import ezbuild.dto.BuildRequirements;
import ezbuild.dto.CompatReport;
import ezbuild.dto.PlanPart;
import ezbuild.dto.UseCase;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.service.CompatibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static ezbuild.model.ProductCategory.CASE;
import static ezbuild.model.ProductCategory.COOLER;
import static ezbuild.model.ProductCategory.CPU;
import static ezbuild.model.ProductCategory.GPU;
import static ezbuild.model.ProductCategory.MOTHERBOARD;
import static ezbuild.model.ProductCategory.PSU;
import static ezbuild.model.ProductCategory.RAM;
import static ezbuild.model.ProductCategory.STORAGE;

/**
 * Deterministic build planner: same requirements + catalogue = same builds. The LLM never picks parts or
 * prices, it only fills BuildRequirements.
 */
@Component
@RequiredArgsConstructor
public class BuildPlanner {

    /** Tier -> share of the budget to aim for. */
    private static final Map<String, Double> TIERS = Map.of("Value", 0.85, "Balanced", 0.95, "Performance", 1.0);
    private static final List<String> TIER_ORDER = List.of("Value", "Balanced", "Performance");

    private final CompatibilityService compat;

    /** How many top CPU+GPU pairs get fully upgraded and compared per tier. */
    private static final int PAIRS_PER_TIER = 30;

    public PlanResult plan(BuildRequirements req, Map<ProductCategory, Product> locked, Catalog catalog) {
        return plan(req, locked, catalog, PlanHints.NONE);
    }

    /**
     * Tries every affordable CPU+GPU pair with the cheapest valid rest of the build, upgrades the best
     * candidates with the remaining budget, keeps the best score for the use case. Choosing CPU and GPU
     * together is what prevents lopsided builds; focus + hints handle "better CPU" requests.
     */
    public PlanResult plan(BuildRequirements req, Map<ProductCategory, Product> locked, Catalog catalog, PlanHints hints) {
        Map<ProductCategory, Product> fixed = locked == null ? Map.of() : locked;
        PlanHints h = hints == null ? PlanHints.NONE : hints;
        List<String> notes = new ArrayList<>();
        boolean needGpu = needsGpu(req, fixed);
        if (needGpu && (req.useCase() == UseCase.GENERAL || req.useCase() == UseCase.OFFICE) && !fixed.containsKey(GPU)
                && Prefs.from(req).gpu() != null) {
            req = req.withUseCase(UseCase.GAMING); // a GPU brand was requested: plan it like a gaming PC
        }
        final BuildRequirements r = req;
        Prefs prefs = Prefs.from(r);

        // cheapest valid build for every CPU+GPU pair (same for all tiers)
        List<Candidate> base = new ArrayList<>();
        for (Product cpu : cpuPool(catalog, fixed, prefs, needGpu)) {
            for (Product gpu : gpuPool(catalog, fixed, prefs, needGpu)) {
                Map<ProductCategory, Product> pinned = new EnumMap<>(ProductCategory.class);
                pinned.putAll(fixed);
                pinned.put(CPU, cpu);
                if (gpu != null) pinned.put(GPU, gpu);
                if (gpu != null && !fixed.containsKey(CPU) && !fixed.containsKey(GPU)
                        && CompatibilityService.bottleneckRatio(cpu, gpu) < 0.75) {
                    continue; // e.g. i3-12100F + RX 9070 GRE: the CPU would cripple the card
                }
                Map<ProductCategory, Product> parts = assemble(catalog, pinned, r, prefs, needGpu, 0, null);
                if (parts == null || !compat.check(parts.values(), true).ok()) continue;
                base.add(new Candidate(pinned, parts, total(parts), score(parts, r, PlanHints.NONE, null)));
            }
        }
        long minimum = base.stream().mapToLong(Candidate::total).min().orElse(0);
        if (r.budgetBdt() == null || base.isEmpty()) {
            if (base.isEmpty()) notes.add("No compatible combination is in stock right now.");
            return new PlanResult(List.of(), minimum, notes);
        }
        long budget = r.budgetBdt();
        if (budget < minimum) {
            notes.add("The cheapest compatible build for this request costs about " + minimum + " BDT.");
            return new PlanResult(List.of(), minimum, notes);
        }

        List<BuildPlan> plans = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();
        for (String tier : TIER_ORDER) {
            long target = Math.max(Math.min(budget, minimum), Math.round(budget * TIERS.get(tier)));
            final String t = tier;
            List<Candidate> shortlist = base.stream()
                    .filter(c -> c.total() <= target)
                    .map(c -> h.any() ? new Candidate(c.pinned(), c.parts(), c.total(), score(c.parts(), r, h, t)) : c)
                    .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                            .thenComparingLong(Candidate::total)
                            .thenComparing(c -> ids(c.parts()).toString()))
                    .limit(PAIRS_PER_TIER)
                    .toList();
            Map<ProductCategory, Product> best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Candidate c : shortlist) {
                Map<ProductCategory, Product> parts = new EnumMap<>(c.parts());
                upgrade(parts, catalog, c.pinned(), r, prefs, target);
                if (total(parts) > budget || !compat.check(parts.values(), true).ok()) continue;
                double sc = score(parts, r, h, tier);
                if (best == null || sc > bestScore + 1e-9
                        || (Math.abs(sc - bestScore) < 1e-9 && total(parts) < total(best))) {
                    best = parts;
                    bestScore = sc;
                }
            }
            if (best == null) continue;
            if (!seen.add(ids(best))) continue;
            final Map<ProductCategory, Product> chosen = best;
            boolean nearDuplicate = plans.stream().anyMatch(p -> samePair(p, chosen)
                    && Math.abs(p.total() - total(chosen)) < 0.03 * p.total());
            if (nearDuplicate) continue; // same CPU + GPU at almost the same price isn't a real alternative
            plans.add(toPlan(tier, best, budget, compat.check(best.values(), true)));
        }
        if (plans.isEmpty()) notes.add("No valid build could be made inside this budget.");
        if (!h.minCpuByTier().isEmpty() && plans.stream().noneMatch(p -> partScoreOf(p, CPU, r, catalog) > h.minCpu(p.tier()))) {
            notes.add("No faster CPU fits this budget without breaking compatibility.");
        }
        return new PlanResult(plans, minimum, notes);
    }

    private static boolean samePair(BuildPlan plan, Map<ProductCategory, Product> parts) {
        List<String> a = plan.parts().stream().filter(p -> p.category() == CPU || p.category() == GPU)
                .map(ezbuild.dto.PlanPart::productId).sorted().toList();
        List<String> b = parts.entrySet().stream().filter(e -> e.getKey() == CPU || e.getKey() == GPU)
                .map(e -> e.getValue().getId()).sorted().toList();
        return a.equals(b);
    }

    private record Candidate(Map<ProductCategory, Product> pinned, Map<ProductCategory, Product> parts,
                             long total, double score) { }

    private static List<String> ids(Map<ProductCategory, Product> parts) {
        return parts.values().stream().map(Product::getId).sorted().toList();
    }

    private static double partScoreOf(BuildPlan plan, ProductCategory c, BuildRequirements req, Catalog catalog) {
        return plan.parts().stream().filter(p -> p.category() == c).findFirst()
                .flatMap(p -> catalog.get(c).stream().filter(x -> x.getId().equals(p.productId())).findFirst())
                .map(p -> c == CPU ? cpuScore(p, req.useCase()) : gpuScore(p)).orElse(0.0);
    }

    private static List<Product> cpuPool(Catalog catalog, Map<ProductCategory, Product> fixed, Prefs prefs, boolean needGpu) {
        if (fixed.containsKey(CPU)) return List.of(fixed.get(CPU));
        List<Product> c = filter(filter(catalog.get(CPU), prefs.cpu()), prefs.any());
        if (fixed.containsKey(MOTHERBOARD)) {
            String socket = fixed.get(MOTHERBOARD).spec("socket");
            c = c.stream().filter(p -> socket == null || socket.equals(p.spec("socket"))).toList();
        }
        if (!needGpu) {
            List<Product> igpu = c.stream().filter(p -> p.specBool("igpu")).toList(); // office: prefer integrated graphics
            if (!igpu.isEmpty()) c = igpu;
        }
        return c;
    }

    private static List<Product> gpuPool(Catalog catalog, Map<ProductCategory, Product> fixed, Prefs prefs, boolean needGpu) {
        if (fixed.containsKey(GPU)) return List.of(fixed.get(GPU));
        if (!needGpu) return java.util.Collections.singletonList(null);
        List<Product> pool = filter(filter(catalog.get(GPU), prefs.gpu()), prefs.any());
        // skip display-only cards (GT 1030 class) when a real GPU is needed
        return preferOrAll(pool, g -> gpuScore(g) >= 20);
    }

    public BuildPlan toPlan(String tier, Map<ProductCategory, Product> parts, long budget, CompatReport report) {
        List<PlanPart> list = parts.values().stream()
                .sorted(Comparator.comparing(Product::getCategory))
                .map(PlanPart::of).toList();
        return new BuildPlan(tier, list, total(parts), budget, report);
    }

    // ---- assembly

    /** target = 0 means "cheapest valid parts" (used for the minimum budget). */
    Map<ProductCategory, Product> assemble(Catalog catalog, Map<ProductCategory, Product> fixed,
                                           BuildRequirements req, Prefs prefs, boolean needGpu, long target, String socketOnly) {
        Map<ProductCategory, Product> parts = new EnumMap<>(ProductCategory.class);
        Map<ProductCategory, Integer> w = weights(req.useCase(), needGpu);
        long lockedCost = fixed.values().stream().mapToLong(Product::effectivePrice).sum();
        long spendable = Math.max(0, target - lockedCost);
        int weightSum = w.entrySet().stream().filter(e -> !fixed.containsKey(e.getKey()))
                .mapToInt(Map.Entry::getValue).sum();
        java.util.function.Function<ProductCategory, Long> alloc = c ->
                weightSum == 0 ? 0L : spendable * w.getOrDefault(c, 0) / weightSum;

        // CPU
        Product cpu = fixed.get(CPU);
        if (cpu == null) {
            List<Product> c = filter(filter(catalog.get(CPU), prefs.cpu()), prefs.any());
            if (fixed.containsKey(MOTHERBOARD)) {
                String socket = fixed.get(MOTHERBOARD).spec("socket");
                c = c.stream().filter(p -> socket == null || socket.equals(p.spec("socket"))).toList();
            }
            if (socketOnly != null) c = c.stream().filter(p -> socketOnly.equals(p.spec("socket"))).toList();
            if (!needGpu) {
                c = c.stream().filter(p -> p.specBool("igpu")).toList();   // office builds: integrated graphics only
                if (c.isEmpty() && socketOnly != null) return null;
            }
            cpu = best(c, alloc.apply(CPU), Product::getPerfScore);
        }
        if (cpu == null) return null;
        parts.put(CPU, cpu);

        // GPU
        Product gpu = fixed.get(GPU);
        if (gpu == null && needGpu) {
            gpu = best(filter(filter(catalog.get(GPU), prefs.gpu()), prefs.any()), alloc.apply(GPU), Product::getPerfScore);
            if (gpu == null) return null;
        }
        if (gpu != null) parts.put(GPU, gpu);
        if (gpu == null && !cpu.specBool("igpu")) {
            // CPU has no graphics: switch to one that does, or add the cheapest GPU
            Product igpuCpu = best(catalog.get(CPU).stream().filter(p -> p.specBool("igpu"))
                            .filter(p -> socketOnly == null || socketOnly.equals(p.spec("socket"))).toList(),
                    alloc.apply(CPU), Product::getPerfScore);
            if (igpuCpu != null && !fixed.containsKey(CPU)) {
                cpu = igpuCpu;
                parts.put(CPU, cpu);
            } else if (!catalog.get(GPU).isEmpty()) {
                gpu = catalog.get(GPU).get(0);
                parts.put(GPU, gpu);
            } else {
                return null;
            }
        }

        // motherboard: same socket, memory type the CPU supports
        final Product theCpu = cpu;
        Product mb = fixed.get(MOTHERBOARD);
        if (mb == null) {
            List<Product> boards = catalog.get(MOTHERBOARD).stream()
                    .filter(b -> theCpu.spec("socket") != null && theCpu.spec("socket").equals(b.spec("socket")))
                    .filter(b -> CompatibilityService.cpuSupports(theCpu, b.spec("ram")))
                    .toList();
            if (fixed.containsKey(RAM)) {
                String type = fixed.get(RAM).spec("type");
                boards = boards.stream().filter(b -> type == null || type.equals(b.spec("ram"))).toList();
            }
            boards = filter(boards, prefs.any());
            if (theCpu.specInt("tdp") >= 105) {
                // hot/unlocked CPUs need proper power delivery, not an entry H610/H510/A520 board
                boards = preferOrAll(boards, b -> !List.of("H610", "H510", "H470", "A520").contains(
                        String.valueOf(b.spec("chipset")).toUpperCase(Locale.ROOT)));
            }
            if (prefs.wifi()) boards = preferOrAll(boards, b -> b.getName().toUpperCase(Locale.ROOT).contains("WIFI"));
            if (prefs.small()) boards = preferOrAll(boards, b -> "mATX".equalsIgnoreCase(b.spec("form")));
            mb = best(boards, alloc.apply(MOTHERBOARD), Product::effectivePriceInt);
        }
        if (mb == null) return null;
        parts.put(MOTHERBOARD, mb);

        // RAM: type from the board, size by use case
        final Product board = mb;
        Product ram = fixed.get(RAM);
        if (ram == null) {
            int minGb = minRamGb(req.useCase(), target);
            List<Product> sticks = catalog.get(RAM).stream()
                    .filter(r -> board.spec("ram") == null || board.spec("ram").equalsIgnoreCase(r.spec("type")))
                    .toList();
            sticks = filter(sticks, prefs.any());
            List<Product> bigEnough = sticks.stream().filter(r -> r.specInt("gb") >= minGb).toList();
            ram = best(bigEnough.isEmpty() ? sticks : bigEnough, alloc.apply(RAM), r -> r.specInt("gb") * 10 + r.specInt("mhz") / 400);
        }
        if (ram == null) return null;
        parts.put(RAM, ram);

        // storage: enough capacity, internal drives only, NVMe preferred
        Product drive = fixed.get(STORAGE);
        if (drive == null) {
            int need = req.storageNeedGb() != null ? req.storageNeedGb() : (req.useCase() == UseCase.OFFICE ? 480 : 1000);
            List<Product> drives = catalog.get(STORAGE).stream()
                    .filter(s -> !"Portable".equalsIgnoreCase(s.spec("iface")) && !"HDD".equalsIgnoreCase(s.spec("iface")))
                    .toList();
            List<Product> nvme = drives.stream().filter(s -> "NVMe".equalsIgnoreCase(s.spec("iface"))).toList();
            List<Product> pool = nvme.isEmpty() ? drives : nvme;
            List<Product> fits = pool.stream().filter(s -> s.specInt("gb") >= need).toList();
            drive = target == 0 ? cheapest(pool) : best(fits.isEmpty() ? pool : fits, alloc.apply(STORAGE), s -> s.specInt("gb"));
        }
        if (drive == null) return null;
        parts.put(STORAGE, drive);

        // PSU: cheapest that meets the recommended wattage (Gold for high-power builds)
        Product psu = fixed.get(PSU);
        if (psu == null) {
            int needW = compat.recommendedPsu(cpu, gpu);
            List<Product> ok = catalog.get(PSU).stream().filter(p -> p.specInt("watt") >= needW).toList();
            if (ok.isEmpty()) {
                int minW = compat.estimatedWattage(cpu, gpu);
                ok = catalog.get(PSU).stream().filter(p -> p.specInt("watt") >= minW).toList();
            }
            psu = cheapest(filter(preferGold(ok, cpu, gpu), prefs.any()));
        }
        if (psu == null) return null;
        parts.put(PSU, psu);

        // case: fits the board and the GPU length
        final Product theGpu = gpu;
        Product cse = fixed.get(CASE);
        if (cse == null) {
            List<Product> cases = catalog.get(CASE).stream()
                    .filter(c -> CompatibilityService.caseFits(c.spec("form"), board.spec("form")))
                    .filter(c -> theGpu == null || c.specInt("maxgpu") == 0 || theGpu.specInt("len") <= c.specInt("maxgpu"))
                    .toList();
            cases = filter(cases, prefs.any());
            if (prefs.small()) cases = preferOrAll(cases, c -> "mATX".equalsIgnoreCase(c.spec("form")));
            cse = best(cases, alloc.apply(CASE), Product::effectivePriceInt);
        }
        if (cse == null) return null;
        parts.put(CASE, cse);

        // cooler: for hot CPUs or ones sold without a box cooler, sized by peak power
        Product cooler = fixed.get(COOLER);
        if (cooler == null && needsCooler(cpu)) {
            int tdp = CompatibilityService.coolerNeed(cpu);
            List<Product> coolers = catalog.get(COOLER).stream().filter(c -> c.specInt("tdp") >= tdp).toList();
            cooler = cheapest(coolers.isEmpty() ? catalog.get(COOLER) : coolers);
        }
        if (cooler != null) parts.put(COOLER, cooler);
        return parts;
    }

    // ---- upgrade

    /**
     * Spends the leftover budget in use-case priority order (GPU first for gaming, CPU first for editing).
     * Every step is re-checked by the compatibility engine, so the build can't become invalid.
     */
    void upgrade(Map<ProductCategory, Product> parts, Catalog catalog, Map<ProductCategory, Product> fixed,
                 BuildRequirements req, Prefs prefs, long target) {
        List<ProductCategory> order = priority(req.useCase(), parts.containsKey(GPU));
        // most important category first, re-checked each step
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            for (ProductCategory c : order) {
                if (fixed.containsKey(c) || !parts.containsKey(c)) continue;
                changed |= tryBest(parts, catalog, fixed, prefs, c, target, Long.MAX_VALUE);
            }
            if (!changed) break;
        }
        fixPsu(parts, catalog, fixed);
        fixCooler(parts, catalog, fixed);
    }

    /** Swaps the part in category c for the best-scoring compatible one that keeps the build under target. */
    private boolean tryBest(Map<ProductCategory, Product> parts, Catalog catalog, Map<ProductCategory, Product> fixed,
                            Prefs prefs, ProductCategory c, long target, long priceCap) {
        Product current = parts.get(c);
        Predicate<Product> pref = c == CPU ? prefs.cpu() : c == GPU ? prefs.gpu() : null;
        List<Product> pool = filter(filter(catalog.get(c), pref), prefs.any());
        if (c == STORAGE) pool = pool.stream().filter(p -> !"Portable".equalsIgnoreCase(p.spec("iface"))
                && !"HDD".equalsIgnoreCase(p.spec("iface"))).toList();
        if (pref != null && pref.test(current)) pool = pool.stream().filter(pref).toList();
        List<Product> ranked = pool.stream()
                .filter(p -> p.effectivePrice() <= priceCap)
                .filter(p -> partScore(p) > partScore(current)
                        || (partScore(p) == partScore(current) && p.effectivePrice() < current.effectivePrice()))
                .sorted(Comparator.comparingDouble(BuildPlanner::partScore).reversed()
                        .thenComparingLong(Product::effectivePrice).thenComparing(Product::getId))
                .toList();
        for (Product candidate : ranked) {
            Map<ProductCategory, Product> trial = new EnumMap<>(parts);
            trial.put(c, candidate);
            fixPsu(trial, catalog, fixed);
            fixCooler(trial, catalog, fixed);
            if (total(trial) <= target && compat.check(trial.values(), true).ok()) {
                parts.clear();
                parts.putAll(trial);
                return true;
            }
        }
        return false;
    }

    /** Single-part score (higher is better). Boards and cases use price as a quality proxy. */
    static double partScore(Product p) {
        return switch (p.getCategory()) {
            case CPU -> cpuScore(p, UseCase.GENERAL);
            case GPU -> gpuScore(p);
            case RAM -> p.specInt("gb") * 10 + p.specInt("mhz") / 1000.0 - p.specInt("cl") / 100.0;
            case STORAGE -> ("NVMe".equalsIgnoreCase(p.spec("iface")) ? 1.5 : 1.0) * p.specInt("gb") + p.specInt("read") / 1000.0;
            default -> p.effectivePrice() / 1000.0;
        };
    }

    /**
     * CPU strength for a use case, roughly 0-100: gaming index for games, multi-core for
     * editing/rendering/AI, a single+multi blend for office. Falls back to perfScore.
     */
    public static double cpuScore(Product p, UseCase useCase) {
        if (p == null) return 0;
        int gaming = p.specInt("gaming");
        int st = p.specInt("st");
        int mt = p.specInt("mt");
        if (gaming == 0 && st == 0 && mt == 0) return p.getPerfScore();
        return switch (useCase) {
            case GAMING -> gaming > 0 ? gaming : p.getPerfScore();
            case STREAMING -> 0.6 * gaming + 0.4 * (mt / 350.0);
            case VIDEO_EDITING, RENDER_3D, AI_ML -> mt > 0 ? mt / 350.0 : p.getPerfScore();
            case OFFICE, GENERAL -> st > 0 && mt > 0 ? 0.5 * (st / 22.0) + 0.5 * (mt / 350.0) : p.getPerfScore();
        };
    }

    /** GPU strength, roughly 0-100 (relative 1080p index). */
    public static double gpuScore(Product p) {
        if (p == null) return 0;
        int gaming = p.specInt("gaming");
        return gaming > 0 ? gaming : p.getPerfScore();
    }

    /**
     * Whole-build score for the use case, with bottleneck/overkill penalties, focus bonus and hint penalties.
     */
    static double score(Map<ProductCategory, Product> parts, BuildRequirements req, PlanHints hints, String tier) {
        UseCase useCase = req.useCase();
        Product cpuP = parts.get(CPU);
        Product gpuP = parts.get(GPU);
        double cpu = cpuScore(cpuP, useCase);
        double gpu = gpuScore(gpuP);
        double ram = parts.containsKey(RAM) ? parts.get(RAM).specInt("gb") : 0;
        double ssd = parts.containsKey(STORAGE) ? parts.get(STORAGE).specInt("gb") : 0;
        double vram = gpuP == null ? 0 : gpuP.specInt("vram");
        double gamingCpu = cpuScore(cpuP, UseCase.GAMING);
        // bottleneck: GPU much stronger than the CPU; overkill: CPU far stronger than the GPU
        double bottleneck = gpuP == null ? 0 : Math.max(0, 0.9 * gpu - gamingCpu);
        double overkill = gpuP == null ? 0 : Math.max(0, gamingCpu - gpu - 25);
        double s = switch (useCase) {
            case GAMING -> 2.0 * gpu + 1.0 * cpu + ram / 4 + ssd / 1000 - 1.5 * bottleneck - 1.0 * overkill;
            case STREAMING -> 1.5 * gpu + 1.3 * cpu + ram / 4 + ssd / 1000 - 1.0 * bottleneck - 0.7 * overkill;
            case AI_ML -> 3.0 * gpu + cpu + ram / 2 + 2 * vram;
            case VIDEO_EDITING, RENDER_3D -> 1.6 * cpu + gpu + ram / 2 + ssd / 500 + vram / 2;
            case OFFICE, GENERAL -> gpuP == null
                    ? 1.6 * cpu + ram / 2 + ssd / 250
                    : 1.3 * cpu + 1.3 * gpu + ram / 2 + ssd / 500 - 1.0 * bottleneck - 1.0 * Math.max(0, cpu - gpu - 30);
        };
        if ("cpu".equals(req.focus())) s += 1.5 * cpu;
        if ("gpu".equals(req.focus())) s += 1.5 * gpu;
        if (hints != null && hints.minCpu(tier) > 0 && cpu <= hints.minCpu(tier)) s -= 500;
        if (hints != null && hints.minGpu(tier) > 0 && gpuP != null && gpu <= hints.minGpu(tier)) s -= 500;
        return s;
    }

    static double score(Map<ProductCategory, Product> parts, UseCase useCase) {
        return score(parts, new BuildRequirements(ezbuild.dto.Intent.BUILD_FROM_BUDGET, null, useCase, List.of(),
                List.of(), List.of(), null, null, "en", false, null, null, null), PlanHints.NONE, null);
    }

    private static List<ProductCategory> priority(UseCase useCase, boolean gpu) {
        List<ProductCategory> list = switch (useCase) {
            case GAMING, STREAMING -> List.of(GPU, CPU, RAM, STORAGE, MOTHERBOARD, CASE);
            case AI_ML -> List.of(GPU, RAM, CPU, STORAGE, MOTHERBOARD, CASE);
            case VIDEO_EDITING, RENDER_3D -> List.of(CPU, RAM, GPU, STORAGE, MOTHERBOARD, CASE);
            case OFFICE, GENERAL -> List.of(CPU, RAM, STORAGE, GPU, MOTHERBOARD, CASE);
        };
        return gpu ? list : list.stream().filter(c -> c != GPU).toList();
    }

    private void fixCooler(Map<ProductCategory, Product> parts, Catalog catalog, Map<ProductCategory, Product> fixed) {
        if (fixed.containsKey(COOLER)) return;
        Product cpu = parts.get(CPU);
        if (cpu == null || !needsCooler(cpu)) {
            parts.remove(COOLER);
            return;
        }
        Product current = parts.get(COOLER);
        int need = CompatibilityService.coolerNeed(cpu);
        if (current != null && current.specInt("tdp") >= need) return;
        List<Product> ok = catalog.get(COOLER).stream().filter(c -> c.specInt("tdp") >= need).toList();
        Product pick = cheapest(ok.isEmpty() ? catalog.get(COOLER) : ok);
        if (pick != null) parts.put(COOLER, pick);
    }

    private void fixPsu(Map<ProductCategory, Product> parts, Catalog catalog, Map<ProductCategory, Product> fixed) {
        if (fixed.containsKey(PSU) || !parts.containsKey(PSU)) return;
        int needW = compat.recommendedPsu(parts.get(CPU), parts.get(GPU));
        List<Product> ok = catalog.get(PSU).stream().filter(p -> p.specInt("watt") >= needW).toList();
        Product pick = cheapest(preferGold(ok, parts.get(CPU), parts.get(GPU)));
        if (pick != null) parts.put(PSU, pick);
    }

    // ---- helpers

    /** High-power builds (big turbo CPU or 220 W+ GPU) get an 80+ Gold unit when one fits. */
    private static List<Product> preferGold(List<Product> psus, Product cpu, Product gpu) {
        if (!CompatibilityService.highPower(cpu, gpu)) return psus;
        return preferOrAll(psus, p -> String.valueOf(p.spec("eff")).toUpperCase(Locale.ROOT)
                .matches(".*(GOLD|PLATINUM|TITANIUM).*"));
    }

    private static boolean needsGpu(BuildRequirements req, Map<ProductCategory, Product> fixed) {
        if (fixed.containsKey(GPU)) return true;
        if (Prefs.from(req).gpu() != null) return true;
        return switch (req.useCase()) {
            case OFFICE -> false;
            case GENERAL -> req.budgetBdt() != null && req.budgetBdt() >= 110_000;
            default -> true;
        };
    }

    private static boolean needsCooler(Product cpu) {
        if (cpu.spec("boxcooler") != null) return !cpu.specBool("boxcooler") || cpu.specInt("tdp") > 65;
        String n = cpu.getName().toUpperCase(Locale.ROOT);
        return cpu.specInt("tdp") > 65 || n.matches(".*\\d{4,5}KF?\\b.*") || n.contains("X3D")
                || n.matches(".*RYZEN \\d \\d{4}X\\b.*") && !n.contains("5600X");
    }

    private static int minRamGb(UseCase useCase, long target) {
        return switch (useCase) {
            case VIDEO_EDITING, RENDER_3D, AI_ML -> 32;
            case OFFICE -> 8;
            default -> target >= 180_000 ? 32 : 16;
        };
    }

    /** Budget weights in percent, tuned for current BD prices (GPUs and DDR5 are pricey). */
    static Map<ProductCategory, Integer> weights(UseCase useCase, boolean gpu) {
        Map<ProductCategory, Integer> w = new EnumMap<>(ProductCategory.class);
        int[] v = switch (useCase) {
            case GAMING -> new int[]{16, 38, 11, 11, 7, 7, 6, 4};
            case VIDEO_EDITING, RENDER_3D -> new int[]{24, 24, 11, 17, 10, 7, 5, 2};
            case STREAMING -> new int[]{20, 32, 11, 13, 8, 7, 6, 3};
            case AI_ML -> new int[]{14, 45, 9, 13, 8, 6, 3, 2};
            case OFFICE, GENERAL -> new int[]{30, 0, 18, 20, 14, 9, 9, 0};
        };
        if (gpu && v[1] == 0) v = new int[]{18, 34, 12, 12, 8, 7, 6, 3};
        ProductCategory[] order = {CPU, GPU, MOTHERBOARD, RAM, STORAGE, PSU, CASE, COOLER};
        for (int i = 0; i < order.length; i++) {
            if (order[i] == GPU && !gpu) continue;
            w.put(order[i], v[i]);
        }
        return w;
    }

    /** Best score within the allocation, else the cheapest. Ties: lower price, then id. */
    static Product best(List<Product> candidates, long allocation, java.util.function.ToIntFunction<Product> score) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (allocation <= 0) return cheapest(candidates);
        return candidates.stream().filter(p -> p.effectivePrice() <= allocation)
                .max(Comparator.comparingInt(score)
                        .thenComparing(Comparator.comparingLong(Product::effectivePrice).reversed())
                        .thenComparing(Comparator.comparing(Product::getId).reversed()))
                .orElseGet(() -> cheapest(candidates));
    }

    static Product cheapest(List<Product> candidates) {
        return candidates == null ? null : candidates.stream()
                .min(Comparator.comparingLong(Product::effectivePrice).thenComparing(Product::getId)).orElse(null);
    }

    private static List<Product> filter(List<Product> list, Predicate<Product> pref) {
        return pref == null ? list : preferOrAll(list, pref);
    }

    private static List<Product> preferOrAll(List<Product> list, Predicate<Product> pref) {
        List<Product> preferred = list.stream().filter(pref).toList();
        return preferred.isEmpty() ? list : preferred;
    }

    static long total(Map<ProductCategory, Product> parts) {
        return parts.values().stream().mapToLong(Product::effectivePrice).sum();
    }

    private static boolean eq(String a, String b) {
        return a == null || b == null || a.equalsIgnoreCase(b);
    }

    /** Brand preferences from the request. Soft filters: ignored if nothing matches. */
    record Prefs(Predicate<Product> cpu, Predicate<Product> gpu, Predicate<Product> any, boolean wifi, boolean small) {

        static Prefs from(BuildRequirements req) {
            Set<String> brands = new LinkedHashSet<>();
            req.preferredBrands().forEach(b -> brands.add(b.toLowerCase(Locale.ROOT).trim()));
            Predicate<Product> cpu = null;
            Predicate<Product> gpu = null;
            if (brands.contains("intel")) cpu = p -> "Intel".equalsIgnoreCase(p.getBrand());
            else if (brands.contains("amd") || brands.contains("ryzen")) cpu = p -> "AMD".equalsIgnoreCase(p.getBrand());
            if (brands.contains("nvidia") || brands.contains("geforce") || brands.contains("rtx")) {
                gpu = p -> p.getName().toUpperCase(Locale.ROOT).matches(".*(GEFORCE|RTX|GTX|GT ?\\d).*");
            } else if (brands.contains("radeon") || brands.contains("amd gpu")) {
                gpu = p -> p.getName().toUpperCase(Locale.ROOT).contains("RADEON");
            }
            Set<String> others = new LinkedHashSet<>(brands);
            others.removeAll(Set.of("intel", "amd", "ryzen", "nvidia", "geforce", "rtx", "radeon", "amd gpu"));
            Predicate<Product> any = others.isEmpty() ? null
                    : p -> others.contains(p.getBrand().toLowerCase(Locale.ROOT));
            Set<String> must = new LinkedHashSet<>();
            req.mustHave().forEach(m -> must.add(m.toLowerCase(Locale.ROOT)));
            return new Prefs(cpu, gpu, any, must.contains("wifi"), must.contains("small") || must.contains("compact"));
        }
    }
}
