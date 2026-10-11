package ezbuild.assistant;

import ezbuild.dto.BuildRequirements;
import ezbuild.dto.Intent;
import ezbuild.dto.UseCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prompt-injection guard: model output can only fill typed fields. */
class AssistantMergeTest {

    @Test
    void regexFillsWhatTheModelMissed() {
        BuildRequirements llm = new BuildRequirements(Intent.BUILD_FROM_BUDGET, null, UseCase.GENERAL, List.of(), List.of(),
                List.of(), null, null, "en", false, null, null, null);
        BuildRequirements regex = new RegexExtractor().extract("gaming pc 1 lakh");
        BuildRequirements merged = AssistantService.merge(llm, regex);
        assertThat(merged.budgetBdt()).isEqualTo(100_000L);
        assertThat(merged.useCase()).isEqualTo(UseCase.GAMING);
    }

    @Test
    void absurdBudgetsAreDropped() {
        BuildRequirements r = new BuildRequirements(Intent.BUILD_FROM_BUDGET, -5L, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(r.budgetBdt()).isNull();
        assertThat(r.useCase()).isEqualTo(UseCase.GENERAL);
    }
}
