package ezbuild.assistant;

import ezbuild.config.AppProperties;
import ezbuild.dto.BuildRequirements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the ezbuild-ai service (ADVISOR_MODE=remote) with the shared X-Service-Key. Timeouts + circuit
 * breaker drop the store into rule-based mode if the service is down.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ezbuild.advisor.mode", havingValue = "remote")
public class RemoteAdvisorClient implements AdvisorClient {

    private final RestClient restClient;
    private final AppProperties props;
    private final CircuitBreaker breaker = new CircuitBreaker(5, 60);

    public RemoteAdvisorClient(RestClient restClient, AppProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public record ExtractRequest(String message, List<String> history) { }

    public record ExplainRequest(String message, String plans, String language) { }

    public record ExplainResponse(String text) { }

    @Override
    public Optional<BuildRequirements> extract(String message, List<String> history) {
        if (!breaker.allow()) return Optional.empty();
        try {
            BuildRequirements r = restClient.post()
                    .uri(props.advisor().url() + "/v1/extract")
                    .header("X-Service-Key", props.advisor().key())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ExtractRequest(message, history))
                    .retrieve()
                    .body(BuildRequirements.class);
            breaker.success();
            return Optional.ofNullable(r);
        } catch (Exception e) {
            breaker.failure();
            log.warn("ezbuild-ai extract failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> explain(String message, String planSummary, String language) {
        if (!breaker.allow()) return Optional.empty();
        try {
            ExplainResponse r = restClient.post()
                    .uri(props.advisor().url() + "/v1/explain")
                    .header("X-Service-Key", props.advisor().key())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ExplainRequest(message, planSummary, language))
                    .retrieve()
                    .body(ExplainResponse.class);
            breaker.success();
            return Optional.ofNullable(r).map(ExplainResponse::text).filter(t -> !t.isBlank());
        } catch (Exception e) {
            breaker.failure();
            log.warn("ezbuild-ai explain failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String mode() {
        return "remote";
    }

    /** Not used right now; handy for a health page. */
    public Map<String, String> info() {
        return Map.of("url", props.advisor().url());
    }
}
