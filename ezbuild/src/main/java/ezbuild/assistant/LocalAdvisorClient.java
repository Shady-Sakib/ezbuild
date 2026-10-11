package ezbuild.assistant;

import ezbuild.dto.BuildRequirements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Gemini via Spring AI inside this app (ADVISOR_MODE=local, the default). */
@Slf4j
@Component
@ConditionalOnProperty(name = "ezbuild.advisor.mode", havingValue = "local", matchIfMissing = true)
public class LocalAdvisorClient implements AdvisorClient {

    private final ChatClient extractor;
    private final ChatClient writer;
    private final CircuitBreaker breaker = new CircuitBreaker(5, 60);

    public LocalAdvisorClient(ObjectProvider<ChatClient.Builder> builders) {
        ChatClient.Builder b1 = null;
        ChatClient.Builder b2 = null;
        try {
            b1 = builders.getIfAvailable();
            b2 = builders.getIfAvailable();
        } catch (Exception e) {
            log.warn("Spring AI chat client unavailable: {}", e.getMessage());
        }
        this.extractor = b1 == null ? null : b1.defaultSystem(Prompts.safe(Prompts.load("extract.st"))).build();
        this.writer = b2 == null ? null : b2.defaultSystem(Prompts.safe(Prompts.load("explain.st"))).build();
        if (extractor == null) log.warn("No Spring AI chat model configured: the assistant runs in degraded mode.");
    }

    @Override
    public Optional<BuildRequirements> extract(String message, List<String> history) {
        if (extractor == null || !breaker.allow()) return Optional.empty();
        try {
            String context = history.isEmpty() ? "" : "Earlier messages (oldest first):\n" + String.join("\n", history) + "\n\n";
            BuildRequirements r = extractor.prompt()
                    .user(Prompts.safe(context + "Latest customer message:\n" + message))
                    .call()
                    .entity(BuildRequirements.class);
            breaker.success();
            return Optional.ofNullable(r);
        } catch (Exception e) {
            breaker.failure();
            log.warn("LLM extract failed ({}): using regex fallback", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> explain(String message, String planSummary, String language) {
        if (writer == null || !breaker.allow()) return Optional.empty();
        try {
            String text = writer.prompt()
                    .user(Prompts.safe("Reply language: " + language + "\n\n" + planSummary
                            + "\n=== CUSTOMER'S LATEST MESSAGE ===\n" + message))
                    .call()
                    .content();
            breaker.success();
            return Optional.ofNullable(text).filter(t -> !t.isBlank());
        } catch (Exception e) {
            breaker.failure();
            log.warn("LLM explain failed ({}): using template text", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public String mode() {
        return extractor == null ? "degraded" : "local";
    }
}
