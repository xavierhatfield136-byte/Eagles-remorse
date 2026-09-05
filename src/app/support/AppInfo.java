package app.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppInfo {
    private AppInfo() {}

    public static final String APP_NAME = "Eagles Remorse";
    public static final String VERSION = detectVersion();
    public static final boolean SKIP_TITLE_SEQUENCE = Boolean.getBoolean("game.skipTitle");

    public static String windowTitle() {
        return APP_NAME + " v" + VERSION;
    }

    public static String[] creditsLines() {
        return new String[]{
                APP_NAME + " v" + VERSION,
                "",
                "Created by",
                "Xavier Hatfield",
                "",
                "Design, Engineering, Writing, Game Direction",
                "Xavier Hatfield",
                "",
                "Additional Systems",
                "Campaign, AI, Rendering, UI, Audio, Tools",
                "",
                "Tools",
                "Java + Swing, Gradle, JUnit",
                "",
                "Special Thanks",
                "Playtesters and feedback contributors"
        };
    }

    private static String detectVersion() {
        String override = trimOrNull(System.getProperty("game.version"));
        if (override != null) return override;

        String resourceVersion = readVersionFromResource();
        if (resourceVersion != null) return resourceVersion;

        String fileVersion = readVersionFromFile();
        if (fileVersion != null) return fileVersion;

        return "dev";
    }

    private static String readVersionFromResource() {
        try (InputStream in = AppInfo.class.getResourceAsStream("/VERSION")) {
            if (in == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return trimOrNull(reader.readLine());
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String readVersionFromFile() {
        Path file = Paths.get("VERSION");
        if (!Files.exists(file)) return null;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return trimOrNull(reader.readLine());
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
