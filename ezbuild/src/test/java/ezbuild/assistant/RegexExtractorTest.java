package ezbuild.assistant;

import ezbuild.dto.BuildRequirements;
import ezbuild.dto.Intent;
import ezbuild.dto.UseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexExtractorTest {

    private final RegexExtractor ex = new RegexExtractor();

    @Test
    void englishBudgetAndUseCase() {
        BuildRequirements r = ex.extract("Gaming PC under 80k for 1080p");
        assertThat(r.budgetBdt()).isEqualTo(80_000L);
        assertThat(r.useCase()).isEqualTo(UseCase.GAMING);
        assertThat(r.intent()).isEqualTo(Intent.BUILD_FROM_BUDGET);
    }

    @Test
    void lakhAndBanglish() {
        BuildRequirements r = ex.extract("amar 1.5 lakh takar editing pc lagbe");
        assertThat(r.budgetBdt()).isEqualTo(150_000L);
        assertThat(r.useCase()).isEqualTo(UseCase.VIDEO_EDITING);
        assertThat(r.replyLanguage()).isEqualTo("banglish");
    }

    @Test
    void hajar() {
        assertThat(ex.extract("60 hajar takar office pc").budgetBdt()).isEqualTo(60_000L);
    }

    @Test
    void modelNumbersAndSpecsAreNotMoney() {
        BuildRequirements r = ex.extract("I own an i5-12400F and want 6000MHz RAM with RTX 5060, budget 1.2 lakh");
        assertThat(r.budgetBdt()).isEqualTo(120_000L);
        assertThat(r.lockedParts()).contains("i5-12400f", "rtx 5060");
        assertThat(r.intent()).isEqualTo(Intent.BUILD_AROUND_PARTS);
    }

    @Test
    void missingBudgetAsksForIt() {
        BuildRequirements r = ex.extract("I need a PC for video editing");
        assertThat(r.budgetBdt()).isNull();
        assertThat(r.needsClarification()).isTrue();
    }

    @Test
    void offTopicIsDetected() {
        assertThat(ex.extract("write my essay about climate").intent()).isEqualTo(Intent.OFF_TOPIC);
    }

    @Test
    void banglaDigitsAndScript() {
        BuildRequirements r = ex.extract("৮০ হাজার টাকার গেমিং পিসি");
        assertThat(r.budgetBdt()).isEqualTo(80_000L);
        assertThat(r.replyLanguage()).isEqualTo("bn");
    }

    @Test
    void betterCpuAnyBrandIsAFocusedFollowUp() {
        BuildRequirements r = ex.extract("I'd prefer a better performing cpu in this budget doesnt matter if it's intel or amd");
        assertThat(r.focus()).isEqualTo("cpu");
        assertThat(r.anyBrand()).isTrue();
        assertThat(r.preferredBrands()).doesNotContain("intel", "amd");
        assertThat(RegexExtractor.isRefinement("make it cheaper")).isTrue();
    }

    @Test
    void comparisonsAreQuestionsNotBuilds() {
        BuildRequirements r = ex.extract("is ryzen 5 7600 better than i5 12400f?");
        assertThat(r.intent()).isEqualTo(Intent.EXPLAIN);
        assertThat(r.lockedParts()).isNotEmpty();
    }
}
