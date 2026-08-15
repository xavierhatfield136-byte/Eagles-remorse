package app.persistence;

import app.support.AppInfo;
import app.support.ErrorLog;
import app.support.UserDataPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * Local-only Commander Academy state and playtest telemetry.
 *
 * Files are written under the user's data directory, never the install folder
 * or repository. Telemetry is JSONL so playtesters can manually share it.
 */
public final class AcademyProgressStore {
    public static final int ACADEMY_VERSION = 1;
    private static final Object IO_LOCK = new Object();

    private AcademyProgressStore() {}

    public static final class Progress {
        public int version = ACADEMY_VERSION;
        public String sessionId = UUID.randomUUID().toString();
        public String currentChapter = "";
        public String currentStep = "";
        public String completedSteps = "";
        public String failedSteps = "";
        public String recoveredSteps = "";
        public String hintDisplayCounts = "";
        public String hintRepeatCounts = "";
        public String chapterStartedAtMillis = "";
        public String chapterCompletedAtMillis = "";
        public boolean completed = false;
        public String graduationSnapshot = "";

        public void normalize() {
            version = ACADEMY_VERSION;
            if (sessionId == null || sessionId.isBlank()) sessionId = UUID.randomUUID().toString();
            currentChapter = trim(currentChapter);
            currentStep = trim(currentStep);
            completedSteps = trim(completedSteps);
            failedSteps = trim(failedSteps);
            recoveredSteps = trim(recoveredSteps);
            hintDisplayCounts = trim(hintDisplayCounts);
            hintRepeatCounts = trim(hintRepeatCounts);
            chapterStartedAtMillis = trim(chapterStartedAtMillis);
            chapterCompletedAtMillis = trim(chapterCompletedAtMillis);
            graduationSnapshot = trim(graduationSnapshot);
        }
    }

    public static Progress load() {
        synchronized (IO_LOCK) {
            Progress progress = new Progress();
            Path file = progressFile();
            if (!Files.exists(file)) return progress;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
                props.load(in);
                progress.version = parseInt(props.getProperty("version"), ACADEMY_VERSION);
                progress.sessionId = props.getProperty("sessionId", progress.sessionId);
                progress.currentChapter = props.getProperty("currentChapter", "");
                progress.currentStep = props.getProperty("currentStep", "");
                progress.completedSteps = props.getProperty("completedSteps", "");
                progress.failedSteps = props.getProperty("failedSteps", "");
                progress.recoveredSteps = props.getProperty("recoveredSteps", "");
                progress.hintDisplayCounts = props.getProperty("hintDisplayCounts", "");
                progress.hintRepeatCounts = props.getProperty("hintRepeatCounts", "");
                progress.chapterStartedAtMillis = props.getProperty("chapterStartedAtMillis", "");
                progress.chapterCompletedAtMillis = props.getProperty("chapterCompletedAtMillis", "");
                progress.completed = Boolean.parseBoolean(props.getProperty("completed", "false"));
                progress.graduationSnapshot = props.getProperty("graduationSnapshot", "");
            } catch (IOException ex) {
                ErrorLog.logException("[academy] progress_load_failed path=" + file, ex);
            }
            progress.normalize();
            return progress;
        }
    }

    public static void save(Progress progress) {
        if (progress == null) return;
        synchronized (IO_LOCK) {
            progress.normalize();
            Path file = progressFile();
            Properties props = new Properties();
            props.setProperty("version", String.valueOf(progress.version));
            props.setProperty("sessionId", progress.sessionId);
            props.setProperty("currentChapter", progress.currentChapter);
            props.setProperty("currentStep", progress.currentStep);
            props.setProperty("completedSteps", progress.completedSteps);
            props.setProperty("failedSteps", progress.failedSteps);
            props.setProperty("recoveredSteps", progress.recoveredSteps);
            props.setProperty("hintDisplayCounts", progress.hintDisplayCounts);
            props.setProperty("hintRepeatCounts", progress.hintRepeatCounts);
            props.setProperty("chapterStartedAtMillis", progress.chapterStartedAtMillis);
            props.setProperty("chapterCompletedAtMillis", progress.chapterCompletedAtMillis);
            props.setProperty("completed", String.valueOf(progress.completed));
            props.setProperty("graduationSnapshot", progress.graduationSnapshot);
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file)) {
                    props.store(out, "Commander Academy progress");
                }
            } catch (IOException ex) {
                ErrorLog.logException("[academy] progress_save_failed path=" + file, ex);
            }
        }
    }

    public static Progress markStepStarted(String sessionId, String chapter, String step) {
        Progress progress = load();
        if (sessionId != null && !sessionId.isBlank()) progress.sessionId = sessionId.trim();
        progress.currentChapter = trim(chapter);
        progress.currentStep = trim(step);
        progress.chapterStartedAtMillis = putTokenValue(progress.chapterStartedAtMillis,
                progress.currentChapter, String.valueOf(System.currentTimeMillis()));
        save(progress);
        return progress;
    }

    public static Progress markStepCompleted(String sessionId, String chapter, String step) {
        Progress progress = markStepStarted(sessionId, chapter, step);
        String key = progress.currentChapter + "/" + progress.currentStep;
        if (!key.equals("/") && !containsToken(progress.completedSteps, key)) {
            progress.completedSteps = progress.completedSteps.isBlank()
                    ? key
                    : progress.completedSteps + "," + key;
        }
        progress.chapterCompletedAtMillis = putTokenValue(progress.chapterCompletedAtMillis,
                progress.currentChapter, String.valueOf(System.currentTimeMillis()));
        save(progress);
        return progress;
    }

    public static Progress markStepFailed(String sessionId, String chapter, String step, String result) {
        Progress progress = markStepStarted(sessionId, chapter, step);
        String key = progress.currentChapter + "/" + progress.currentStep;
        if (!key.equals("/") && !containsToken(progress.failedSteps, key)) {
            progress.failedSteps = progress.failedSteps.isBlank() ? key : progress.failedSteps + "," + key;
        }
        save(progress);
        recordEvent(progress.sessionId, "academy_chapter_failed", progress.currentChapter,
                progress.currentStep, 0.0, trim(result).isBlank() ? "failed" : result);
        return progress;
    }

    public static Progress markStepRecovered(String sessionId, String chapter, String step, String result) {
        Progress progress = markStepStarted(sessionId, chapter, step);
        String key = progress.currentChapter + "/" + progress.currentStep;
        if (!key.equals("/") && !containsToken(progress.recoveredSteps, key)) {
            progress.recoveredSteps = progress.recoveredSteps.isBlank() ? key : progress.recoveredSteps + "," + key;
        }
        save(progress);
        recordEvent(progress.sessionId, "academy_chapter_recovered", progress.currentChapter,
                progress.currentStep, 0.0, trim(result).isBlank() ? "recovered" : result);
        return progress;
    }

    public static Progress markAbandoned(String sessionId, String chapter, String step, double elapsedSeconds) {
        Progress progress = markStepStarted(sessionId, chapter, step);
        recordEvent(progress.sessionId, "academy_abandoned", progress.currentChapter,
                progress.currentStep, elapsedSeconds, "abandoned");
        return progress;
    }

    public static Progress markCompleted(String sessionId, String graduationSnapshot) {
        Progress progress = load();
        if (sessionId != null && !sessionId.isBlank()) progress.sessionId = sessionId.trim();
        progress.completed = true;
        progress.graduationSnapshot = trim(graduationSnapshot);
        save(progress);
        recordEvent(progress.sessionId, "graduation_snapshot_created", "Graduation",
                "snapshot", 0.0, progress.graduationSnapshot);
        return progress;
    }

    public static Progress recordHint(String sessionId, String chapter, String step, boolean repeat) {
        Progress progress = markStepStarted(sessionId, chapter, step);
        String key = progress.currentChapter + "/" + progress.currentStep;
        progress.hintDisplayCounts = incrementTokenValue(progress.hintDisplayCounts, key);
        if (repeat) progress.hintRepeatCounts = incrementTokenValue(progress.hintRepeatCounts, key);
        save(progress);
        recordEvent(progress.sessionId, repeat ? "academy_hint_repeated" : "academy_hint_shown",
                progress.currentChapter, progress.currentStep, 0.0, repeat ? "repeat" : "shown");
        return progress;
    }

    public static void recordEvent(String sessionId,
                                   String eventName,
                                   String chapter,
                                   String step,
                                   double elapsedSeconds,
                                   String result) {
        if (!telemetryEnabled()) return;
        String event = trim(eventName);
        if (event.isBlank()) return;
        String sid = trim(sessionId);
        if (sid.isBlank()) sid = load().sessionId;
        String line = "{"
                + "\"timestamp\":\"" + json(Instant.now().toString()) + "\","
                + "\"sessionId\":\"" + json(sid) + "\","
                + "\"gameVersion\":\"" + json(AppInfo.VERSION) + "\","
                + "\"academyVersion\":" + ACADEMY_VERSION + ","
                + "\"event\":\"" + json(event) + "\","
                + "\"chapter\":\"" + json(chapter) + "\","
                + "\"step\":\"" + json(step) + "\","
                + "\"elapsedSeconds\":" + Math.max(0.0, elapsedSeconds) + ","
                + "\"result\":\"" + json(result) + "\""
                + "}";
        Path file = telemetryFile();
        synchronized (IO_LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                ErrorLog.logException("[academy] telemetry_write_failed path=" + file, ex);
            }
        }
    }

    public static Path progressFile() {
        return UserDataPaths.saveDir().resolve("academy_progress.properties");
    }

    public static Path telemetryFile() {
        return UserDataPaths.logDir().resolve("academy_telemetry.jsonl");
    }

    public static boolean telemetryEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("game.academyTelemetry", "true"));
    }

    private static boolean containsToken(String csv, String token) {
        if (csv == null || token == null || token.isBlank()) return false;
        String[] parts = csv.split(",");
        for (String part : parts) {
            if (token.equals(part.trim())) return true;
        }
        return false;
    }

    private static String incrementTokenValue(String csv, String token) {
        int current = parseInt(tokenValue(csv, token), 0);
        return putTokenValue(csv, token, String.valueOf(current + 1));
    }

    private static String putTokenValue(String csv, String token, String value) {
        String key = trim(token);
        if (key.isBlank()) return trim(csv);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        String[] parts = trim(csv).isBlank() ? new String[0] : trim(csv).split(",");
        for (String part : parts) {
            String p = trim(part);
            if (p.isBlank()) continue;
            int idx = p.indexOf('=');
            String existingKey = idx < 0 ? p : p.substring(0, idx);
            if (out.length() > 0) out.append(',');
            if (key.equals(existingKey)) {
                out.append(key).append('=').append(trim(value));
                replaced = true;
            } else {
                out.append(p);
            }
        }
        if (!replaced) {
            if (out.length() > 0) out.append(',');
            out.append(key).append('=').append(trim(value));
        }
        return out.toString();
    }

    private static String tokenValue(String csv, String token) {
        String key = trim(token);
        if (key.isBlank() || csv == null || csv.isBlank()) return "";
        String[] parts = csv.split(",");
        for (String part : parts) {
            String p = trim(part);
            int idx = p.indexOf('=');
            if (idx <= 0) continue;
            if (key.equals(p.substring(0, idx))) return p.substring(idx + 1);
        }
        return "";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String json(String value) {
        String v = trim(value);
        StringBuilder out = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(' ');
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
