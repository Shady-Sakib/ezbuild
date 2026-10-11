package ezbuild.assistant;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads prompt templates from src/main/resources/prompts. */
public final class Prompts {

    private Prompts() { }

    public static String load(String name) {
        try {
            return new ClassPathResource("prompts/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Spring AI treats prompt text as a template, so braces in user text would break it. User text only ever
     * goes in the user message, never the system prompt.
     */
    public static String safe(String text) {
        if (text == null) return "";
        return text.replace('{', '(').replace('}', ')').replace('<', '(').replace('>', ')');
    }
}
