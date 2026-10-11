package ezbuild.build;

import ezbuild.dto.BuildPlan;

import java.util.List;

/** Planner output: up to three valid plans, plus the cheapest valid total for "budget too low" replies. */
public record PlanResult(List<BuildPlan> plans, long minimumBudget, List<String> notes) {

    public PlanResult {
        plans = plans == null ? List.of() : List.copyOf(plans);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean empty() {
        return plans.isEmpty();
    }
}
