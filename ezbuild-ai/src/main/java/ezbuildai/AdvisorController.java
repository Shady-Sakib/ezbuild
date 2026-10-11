package ezbuildai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The two LLM calls, split out of the store. Never receives emails, phones or addresses. Protected by
 * ServiceKeyFilter (X-Service-Key).
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class AdvisorController {

    public record ExtractRequest(@NotBlank @Size(max = 500) String message, List<String> history) { }

    public record ExplainRequest(@NotBlank @Size(max = 500) String message, @Size(max = 30000) String plans,
                                 String language) { }

    public record ExplainResponse(String text) { }

    private final ChatClient extractor;
    private final ChatClient writer;

    public AdvisorController(ChatClient.Builder builder) throws IOException {
        this.extractor = builder.clone().defaultSystem(safe(load("extract.st"))).build();
        this.writer = builder.clone().defaultSystem(safe(load("explain.st"))).build();
    }

    @PostMapping("/extract")
    public Requirements extract(@Valid @RequestBody ExtractRequest body) {
        String history = body.history() == null || body.history().isEmpty() ? ""
                : "Earlier messages (oldest first):\n" + String.join("\n", body.history()) + "\n\n";
        return extractor.prompt()
                .user(safe(history + "Latest customer message:\n" + body.message()))
                .call()
                .entity(Requirements.class);
    }

    @PostMapping("/explain")
    public ExplainResponse explain(@Valid @RequestBody ExplainRequest body) {
        String text = writer.prompt()
                .user(safe("Reply language: " + body.language() + "\n\n" + body.plans()
                        + "\n=== CUSTOMER'S LATEST MESSAGE ===\n" + body.message()))
                .call()
                .content();
        return new ExplainResponse(text);
    }

    private static String load(String name) throws IOException {
        return new ClassPathResource("prompts/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    /** Prompt text is rendered as a template: neutralise braces. */
    private static String safe(String s) {
        return s == null ? "" : s.replace('{', '(').replace('}', ')').replace('<', '(').replace('>', ')');
    }
}
