package ezbuild.build;

import ezbuild.Fixtures;
import ezbuild.dto.BuildPlan;
import ezbuild.dto.BuildRequirements;
import ezbuild.dto.Intent;
import ezbuild.dto.UseCase;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.service.CompatibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuildPlannerTest {

    private BuildPlanner planner;
    private Catalog catalog;

    @BeforeEach
    void setUp() {
        planner = new BuildPlanner(new CompatibilityService());
        catalog = Catalog.of(Fixtures.catalogue());
    }

    private static BuildRequirements req(Long budget, UseCase useCase, List<String> brands) {
        return new BuildRequirements(Intent.BUILD_FROM_BUDGET, budget, useCase, brands, List.of(), List.of(), null, null,
                "en", false, null, null, null);
    }

    @Test
    void gamingBuildsStayInsideBudgetAndAreValid() {
        PlanResult r = planner.plan(req(120_000L, UseCase.GAMING, List.of()), Map.of(), catalog);
        assertThat(r.plans()).isNotEmpty();
        for (BuildPlan p : r.plans()) {
            assertThat(p.total()).isLessThanOrEqualTo(120_000);
            assertThat(p.compat().ok()).isTrue();
            assertThat(p.parts()).anyMatch(x -> x.category() == ProductCategory.GPU);
        }
    }

    @Test
    void sameInputGivesSameOutput() {
        PlanResult a = planner.plan(req(150_000L, UseCase.GAMING, List.of()), Map.of(), catalog);
        PlanResult b = planner.plan(req(150_000L, UseCase.GAMING, List.of()), Map.of(), catalog);
        assertThat(a.plans().stream().map(BuildPlan::productIds).toList())
                .isEqualTo(b.plans().stream().map(BuildPlan::productIds).toList());
    }

    @Test
    void brandPreferencesAreHonoured() {
        PlanResult r = planner.plan(req(150_000L, UseCase.GAMING, List.of("intel", "nvidia")), Map.of(), catalog);
        assertThat(r.plans()).isNotEmpty();
        for (BuildPlan p : r.plans()) {
            assertThat(p.parts()).filteredOn(x -> x.category() == ProductCategory.CPU).allMatch(x -> x.brand().equals("Intel"));
            assertThat(p.parts()).filteredOn(x -> x.category() == ProductCategory.GPU).allMatch(x -> x.name().contains("GeForce"));
        }
    }

    @Test
    void lockedCpuIsKeptWithMatchingBoardAndRam() {
        Product cpu = Fixtures.find("cpu-06");
        PlanResult r = planner.plan(req(140_000L, UseCase.GAMING, List.of()), Map.of(ProductCategory.CPU, cpu), catalog);
        assertThat(r.plans()).isNotEmpty();
        for (BuildPlan p : r.plans()) {
            assertThat(p.productIds()).contains("cpu-06");
            assertThat(p.productIds()).contains("mb-05");
            assertThat(p.parts()).filteredOn(x -> x.category() == ProductCategory.RAM).allMatch(x -> x.name().contains("DDR5"));
        }
    }

    @Test
    void budgetTooLowReturnsMinimum() {
        PlanResult r = planner.plan(req(20_000L, UseCase.GAMING, List.of()), Map.of(), catalog);
        assertThat(r.plans()).isEmpty();
        assertThat(r.minimumBudget()).isGreaterThan(20_000);
    }

    @Test
    void officeBuildHasNoGpuAndUsesIntegratedGraphics() {
        PlanResult r = planner.plan(req(70_000L, UseCase.OFFICE, List.of()), Map.of(), catalog);
        assertThat(r.plans()).isNotEmpty();
        for (BuildPlan p : r.plans()) {
            assertThat(p.parts()).noneMatch(x -> x.category() == ProductCategory.GPU);
        }
    }

    @Test
    void noBudgetGivesNoPlans() {
        PlanResult r = planner.plan(req(null, UseCase.GAMING, List.of()), Map.of(), catalog);
        assertThat(r.plans()).isEmpty();
        assertThat(r.minimumBudget()).isPositive();
    }

    @Test
    void betterCpuFollowUpBeatsThePreviousCpuInEachTier() {
        PlanResult first = planner.plan(req(150_000L, UseCase.GAMING, List.of()), Map.of(), catalog);
        Map<String, Double> previous = new java.util.HashMap<>();
        for (BuildPlan p : first.plans()) {
            p.parts().stream().filter(x -> x.category() == ProductCategory.CPU).findFirst()
                    .ifPresent(x -> previous.put(p.tier(), BuildPlanner.cpuScore(Fixtures.find(x.productId()), UseCase.GAMING)));
        }
        BuildRequirements better = new BuildRequirements(Intent.BUILD_FROM_BUDGET, 150_000L, UseCase.GAMING, List.of(),
                List.of(), List.of(), null, null, "en", false, null, "cpu", true);
        PlanResult next = planner.plan(better, Map.of(), catalog, new PlanHints(previous, Map.of()));
        assertThat(next.plans()).isNotEmpty();
        assertThat(next.plans()).anyMatch(p -> p.parts().stream().filter(x -> x.category() == ProductCategory.CPU)
                .anyMatch(x -> BuildPlanner.cpuScore(Fixtures.find(x.productId()), UseCase.GAMING) > previous.getOrDefault(p.tier(), 0.0)));
    }
}
