package ezbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads KEY=VALUE lines from .env into system properties before Spring starts, so ${MONGODB_URI} and friends
 * resolve. Real env vars and -D flags win. Copes with a BOM, quotes, "export " and CRLF. Never prints values.
 */
final class DotEnv {

    private DotEnv() {}

    static void load() {
        Path file = locate();
        if (file == null) {
            System.out.println("[EZBuild] No .env found (looked in " + Path.of("").toAbsolutePath()
                    + " and ./ezbuild). Using environment variables / defaults.");
            return;
        }
        List<String> loaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1); // Notepad adds a BOM
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).strip();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.isEmpty()) { skipped.add(key + "(empty)"); continue; }
                if (System.getenv(key) != null || System.getProperty(key) != null) {
                    skipped.add(key + "(already set)");
                    continue;
                }
                System.setProperty(key, value);
                loaded.add(key);
            }
            System.out.println("[EZBuild] Loaded .env from " + file + " -> " + loaded
                    + (skipped.isEmpty() ? "" : "  skipped: " + skipped));
        } catch (IOException e) {
            System.out.println("[EZBuild] Could not read " + file + ": " + e.getMessage());
        }
    }

    private static Path locate() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path p : List.of(cwd.resolve(".env"), cwd.resolve("ezbuild").resolve(".env"))) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }
}
