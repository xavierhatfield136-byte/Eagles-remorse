package app.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local-only playtest telemetry rollup for Commander Academy JSONL files.
 */
public final class AcademyTelemetrySummary {
    private AcademyTelemetrySummary() {}

    public static final class Summary {
        public int starts;
        public int completions;
        public int firstRetreats;
        public int firstRepairs;
        public final Map<String, Integer> failuresByChapter = new LinkedHashMap<>();
        public final Map<String, Integer> abandonmentsByChapter = new LinkedHashMap<>();
        public final Map<String, Integer> repeatedHintsByChapter = new LinkedHashMap<>();
        public final Map<String, Double> elapsedSecondsByChapter = new LinkedHashMap<>();
        public final Map<String, Integer> elapsedSamplesByChapter = new LinkedHashMap<>();

        public List<String> toLines() {
            return List.of(
                    "Academy starts: " + starts,
                    "Academy completions: " + completions,
                    "Abandonments by chapter: " + mapOrNone(abandonmentsByChapter),
                    "Failures by chapter: " + mapOrNone(failuresByChapter),
                    "Repeated hints by chapter: " + mapOrNone(repeatedHintsByChapter),
                    "First retreat events: " + firstRetreats,
                    "First repair events: " + firstRepairs,
                    "Average chapter time: " + averageMap());
        }

        private String averageMap() {
            if (elapsedSecondsByChapter.isEmpty()) return "none";
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Double> entry : elapsedSecondsByChapter.entrySet()) {
                int samples = Math.max(1, elapsedSamplesByChapter.getOrDefault(entry.getKey(), 1));
                if (out.length() > 0) out.append("; ");
                out.append(entry.getKey()).append('=')
                        .append(String.format(Locale.US, "%.1fs", entry.getValue() / samples));
            }
            return out.toString();
        }
    }

    public static Summary summarize(Path telemetryFile) {
        Summary summary = new Summary();
        if (telemetryFile == null || !Files.exists(telemetryFile)) return summary;
        try {
            for (String line : Files.readAllLines(telemetryFile, StandardCharsets.UTF_8)) {
                readLine(summary, line);
            }
        } catch (IOException ignored) {
            return summary;
        }
        return summary;
    }

    public static Summary summarizeDefault() {
        return summarize(AcademyProgressStore.telemetryFile());
    }

    public static void main(String[] args) {
        Path file = args != null && args.length > 0 && args[0] != null && !args[0].isBlank()
                ? Path.of(args[0])
                : AcademyProgressStore.telemetryFile();
        for (String line : summarize(file).toLines()) {
            System.out.println(line);
        }
    }

    private static void readLine(Summary summary, String line) {
        if (summary == null || line == null || line.isBlank()) return;
        String event = jsonValue(line, "event");
        String chapter = fallback(jsonValue(line, "chapter"), "unknown");
        double elapsed = jsonDouble(line, "elapsedSeconds");
        switch (event) {
            case "academy_started" -> summary.starts++;
            case "academy_completed" -> summary.completions++;
            case "academy_chapter_failed" -> increment(summary.failuresByChapter, chapter);
            case "academy_abandoned" -> increment(summary.abandonmentsByChapter, chapter);
            case "academy_hint_repeated" -> increment(summary.repeatedHintsByChapter, chapter);
            case "first_retreat" -> summary.firstRetreats++;
            case "first_repair" -> summary.firstRepairs++;
            default -> {
            }
        }
        if ("academy_chapter_completed".equals(event) && elapsed > 0.0) {
            summary.elapsedSecondsByChapter.merge(chapter, elapsed, Double::sum);
            summary.elapsedSamplesByChapter.merge(chapter, 1, Integer::sum);
        }
    }

    private static void increment(Map<String, Integer> map, String key) {
        map.merge(fallback(key, "unknown"), 1, Integer::sum);
    }

    private static String mapOrNone(Map<String, Integer> map) {
        return map == null || map.isEmpty() ? "none" : map.toString();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double jsonDouble(String line, String key) {
        String marker = "\"" + key + "\":";
        int start = line.indexOf(marker);
        if (start < 0) return 0.0;
        start += marker.length();
        int end = start;
        while (end < line.length()) {
            char c = line.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') end++;
            else break;
        }
        try {
            return Double.parseDouble(line.substring(start, end));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static String jsonValue(String line, String key) {
        String marker = "\"" + key + "\":\"";
        int start = line.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escape) {
                out.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> c;
                });
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }
}
