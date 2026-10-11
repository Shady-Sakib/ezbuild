package ezbuild.build;

import java.util.Map;

/**
 * Constraints from the conversation, e.g. "better CPU than last time". Per tier: the new build's CPU (or GPU)
 * score should beat the previous one; builds that don't are only used when nothing better fits.
 */
public record PlanHints(Map<String, Double> minCpuByTier, Map<String, Double> minGpuByTier) {

    public static final PlanHints NONE = new PlanHints(Map.of(), Map.of());

    public PlanHints {
        minCpuByTier = minCpuByTier == null ? Map.of() : Map.copyOf(minCpuByTier);
        minGpuByTier = minGpuByTier == null ? Map.of() : Map.copyOf(minGpuByTier);
    }

    public double minCpu(String tier) {
        return tier == null ? 0 : minCpuByTier.getOrDefault(tier, 0.0);
    }

    public double minGpu(String tier) {
        return tier == null ? 0 : minGpuByTier.getOrDefault(tier, 0.0);
    }

    public boolean any() {
        return !minCpuByTier.isEmpty() || !minGpuByTier.isEmpty();
    }
}
