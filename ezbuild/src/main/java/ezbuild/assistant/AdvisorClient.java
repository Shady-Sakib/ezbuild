package ezbuild.assistant;

import ezbuild.dto.BuildRequirements;

import java.util.List;
import java.util.Optional;

/**
 * The two LLM calls (extract requirements, write the reply). Local = Gemini in-process, Remote = ezbuild-ai
 * service. Empty Optional means the model is unavailable, so fall back to rule-based mode.
 */
public interface AdvisorClient {

    Optional<BuildRequirements> extract(String message, List<String> history);

    Optional<String> explain(String message, String planSummary, String language);

    String mode();
}
