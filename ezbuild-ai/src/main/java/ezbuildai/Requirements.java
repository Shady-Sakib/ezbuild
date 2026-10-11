package ezbuildai;

import java.util.List;

/** Same JSON shape as ezbuild.dto.BuildRequirements (the contract between the two services). */
public record Requirements(
        Intent intent, Long budgetBdt, UseCase useCase, List<String> preferredBrands, List<String> lockedParts,
        List<String> mustHave, Integer storageNeedGb, Boolean monitorWanted, String replyLanguage,
        Boolean needsClarification, String clarifyingQuestion, String focus, Boolean anyBrand) {

    public enum Intent { BUILD_FROM_BUDGET, BUILD_AROUND_PARTS, CATALOG_QUESTION, EXPLAIN, SMALLTALK, OFF_TOPIC }

    public enum UseCase { GAMING, OFFICE, VIDEO_EDITING, RENDER_3D, AI_ML, STREAMING, GENERAL }
}
