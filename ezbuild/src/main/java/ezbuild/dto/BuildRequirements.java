package ezbuild.dto;

import java.util.List;
import java.util.Locale;

/**
 * Typed result of requirement extraction (LLM or regex); this, never raw model text, reaches the planner.
 * focus = "cpu"/"gpu" when that part should be prioritised; anyBrand = the customer doesn't care about
 * CPU/GPU brand.
 */
public record BuildRequirements(
        Intent intent,
        Long budgetBdt,
        UseCase useCase,
        List<String> preferredBrands,
        List<String> lockedParts,
        List<String> mustHave,
        Integer storageNeedGb,
        Boolean monitorWanted,
        String replyLanguage,
        Boolean needsClarification,
        String clarifyingQuestion,
        String focus,
        Boolean anyBrand) {

    public BuildRequirements {
        preferredBrands = preferredBrands == null ? List.of() : List.copyOf(preferredBrands);
        lockedParts = lockedParts == null ? List.of() : List.copyOf(lockedParts);
        mustHave = mustHave == null ? List.of() : List.copyOf(mustHave);
        if (intent == null) intent = Intent.SMALLTALK;
        if (useCase == null) useCase = UseCase.GENERAL;
        if (budgetBdt != null && (budgetBdt < 0 || budgetBdt > 5_000_000)) budgetBdt = null;
        if (replyLanguage == null || replyLanguage.isBlank()) replyLanguage = "en";
        if (focus != null) {
            focus = focus.toLowerCase(Locale.ROOT).trim();
            if (!focus.equals("cpu") && !focus.equals("gpu")) focus = null;
        }
        if (Boolean.TRUE.equals(anyBrand)) {
            // anyBrand: drop CPU/GPU maker preferences, keep others (e.g. asus)
            preferredBrands = preferredBrands.stream()
                    .filter(b -> !List.of("intel", "amd", "ryzen", "nvidia", "geforce", "rtx", "radeon")
                            .contains(b.toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public boolean wantsBuild() {
        return intent == Intent.BUILD_FROM_BUDGET || intent == Intent.BUILD_AROUND_PARTS;
    }

    public BuildRequirements withBudget(Long budget) {
        return new BuildRequirements(intent, budget, useCase, preferredBrands, lockedParts, mustHave,
                storageNeedGb, monitorWanted, replyLanguage, needsClarification, clarifyingQuestion, focus, anyBrand);
    }

    public BuildRequirements withUseCase(UseCase next) {
        return new BuildRequirements(intent, budgetBdt, next, preferredBrands, lockedParts, mustHave,
                storageNeedGb, monitorWanted, replyLanguage, needsClarification, clarifyingQuestion, focus, anyBrand);
    }

    public BuildRequirements withIntent(Intent next) {
        return new BuildRequirements(next, budgetBdt, useCase, preferredBrands, lockedParts, mustHave,
                storageNeedGb, monitorWanted, replyLanguage, needsClarification, clarifyingQuestion, focus, anyBrand);
    }

    /** Follow-up message: anything left empty is taken from the previous request in the chat. */
    public BuildRequirements inheritFrom(BuildRequirements prev) {
        if (prev == null) return this;
        // if anyBrand is set, the constructor strips the maker brands from the inherited list
        List<String> brands = !preferredBrands.isEmpty() ? preferredBrands : prev.preferredBrands();
        return new BuildRequirements(intent,
                budgetBdt != null ? budgetBdt : prev.budgetBdt(),
                useCase != UseCase.GENERAL ? useCase : prev.useCase(),
                brands,
                !lockedParts.isEmpty() ? lockedParts : prev.lockedParts(),
                !mustHave.isEmpty() ? mustHave : prev.mustHave(),
                storageNeedGb != null ? storageNeedGb : prev.storageNeedGb(),
                monitorWanted != null ? monitorWanted : prev.monitorWanted(),
                replyLanguage,
                needsClarification, clarifyingQuestion,
                focus,
                anyBrand);
    }

    /** Stable key for caching build plans. */
    public String cacheKey() {
        return intent + "|" + budgetBdt + "|" + useCase + "|" + preferredBrands + "|" + lockedParts + "|"
                + mustHave + "|" + storageNeedGb + "|" + focus;
    }
}
