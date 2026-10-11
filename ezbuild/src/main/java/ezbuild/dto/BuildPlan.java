package ezbuild.dto;

import java.util.List;

/** One proposed build (planner or manual builder). Always rendered from data, never from model text. */
public record BuildPlan(String tier, List<PlanPart> parts, long total, long budget, CompatReport compat) {

    public BuildPlan {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public long leftover() {
        return budget > 0 ? budget - total : 0;
    }

    public List<String> productIds() {
        return parts.stream().map(PlanPart::productId).toList();
    }
}
