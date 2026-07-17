import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates and validates the real two-machine multiplayer acceptance log. */
public final class MultiplayerTwoMachineAcceptanceLogHarness {
    private static final List<String> REQUIRED_FIELDS = List.of(
            "Build/version",
            "Commit or packaged build ID",
            "Host machine OS / CPU / RAM",
            "Client machine OS / CPU / RAM",
            "Network type",
            "Host LAN address and port",
            "Client LAN address",
            "Firewall rule created or confirmed",
            "Two-machine runbook directory",
            "Host preflight report path",
            "Host preflight candidate address used",
            "Host CLI report path",
            "Client CLI report path",
            "Host observed client endpoint",
            "Client reported local endpoint",
            "Final two-machine manual report path",
            "Evidence validator result",
            "Manual report validator result",
            "Acceptance audit result",
            "Release gate result",
            "Evidence bundle report path");

    private static final List<String> REQUIRED_OBSERVATIONS = List.of(
            "Snapshot gap / perceived latency",
            "Disconnects or reconnect attempts",
            "Errors or warnings",
            "Memory/process growth",
            "Follow-up defects filed");

    public record LogValidation(boolean accepted, List<String> missing) {
        public LogValidation {
            missing = missing == null ? List.of() : List.copyOf(missing);
        }
    }

    private MultiplayerTwoMachineAcceptanceLogHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        String mode = options.getOrDefault("mode", "template");
        Path log = Path.of(options.getOrDefault(
                "log", "build/reports/multiplayer-two-machine-acceptance-log.md"));

        if ("template".equalsIgnoreCase(mode)) {
            writeTemplate(log);
            System.out.println("logTemplate=" + log.toAbsolutePath().normalize());
            return;
        }
        if (!"validate".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        LogValidation validation = validate(log);
        System.out.println("accepted=" + validation.accepted());
        System.out.println("missing=" + validation.missing().size());
        for (int i = 0; i < validation.missing().size(); i++) {
            System.out.println("missing." + (i + 1) + "=" + validation.missing().get(i));
        }
        if (!validation.accepted()) {
            throw new IllegalStateException("Two-machine acceptance log is incomplete");
        }
    }

    public static void writeTemplate(Path log) throws Exception {
        Path absolute = log.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(absolute, templateText(), StandardCharsets.UTF_8);
    }

    public static LogValidation validate(Path log) throws Exception {
        if (log == null || !Files.isRegularFile(log)) {
            return new LogValidation(false, List.of("log file"));
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        ArrayList<String> missing = new ArrayList<>();
        for (String field : REQUIRED_FIELDS) {
            String value = fieldValue(text, field);
            if (value.isBlank()) missing.add(field);
            else if (containsPlaceholder(value)) missing.add(field + ".placeholder");
        }
        requireResultSuccess(text, missing, "Evidence validator result", "passed", "accepted", "true");
        requireResultSuccess(text, missing, "Manual report validator result", "accepted=true", "accepted", "passed");
        requireResultSuccess(text, missing, "Acceptance audit result", "complete=true");
        requireResultSuccess(text, missing, "Release gate result", "allowed=true");
        for (String observation : REQUIRED_OBSERVATIONS) {
            if (fieldValue(text, observation).isBlank()) missing.add(observation);
        }
        int unchecked = 0;
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [ ]")) unchecked++;
        }
        if (unchecked > 0) missing.add("required pass unchecked=" + unchecked);
        if (text.contains("127.0.0.1") || text.contains("localhost") || text.contains("::1")) {
            missing.add("loopback evidence");
        }
        return new LogValidation(missing.isEmpty(), missing);
    }

    public static String templateText() {
        return String.join(System.lineSeparator(),
                "# Multiplayer V1 Two-Machine Acceptance Log",
                "",
                "Generated template date: " + LocalDate.now(),
                "",
                "## Build And Machines",
                "",
                "- Build/version:",
                "- Commit or packaged build ID:",
                "- Host machine OS / CPU / RAM:",
                "- Client machine OS / CPU / RAM:",
                "- Network type:",
                "- Host LAN address and port:",
                "- Client LAN address:",
                "- Firewall rule created or confirmed:",
                "- Two-machine runbook directory:",
                "- Host preflight report path:",
                "- Host preflight candidate address used:",
                "- Host CLI report path:",
                "- Client CLI report path:",
                "- Host observed client endpoint:",
                "- Client reported local endpoint:",
                "- Final two-machine manual report path:",
                "- Evidence validator result:",
                "- Manual report validator result:",
                "- Acceptance audit result:",
                "- Release gate result:",
                "- Evidence bundle report path:",
                "",
                "## Required Pass",
                "",
                "- [ ] Host CLI report from `MultiplayerLanDuelAcceptanceHarness host` contains `passed=true`.",
                "- [ ] Client CLI report from `MultiplayerLanDuelAcceptanceHarness client` contains `passed=true`.",
                "- [ ] Both CLI reports contain `Elimination victory`.",
                "- [ ] Client joins host through direct LAN address.",
                "- [ ] Host is Blue and client is Red.",
                "- [ ] Both players control exactly one ship.",
                "- [ ] Both players can thrust, rotate, aim, and fire.",
                "- [ ] Remote ship movement is smooth enough for a personal battle.",
                "- [ ] Host authoritatively processes weapon hits.",
                "- [ ] Health and shield values match after snapshots arrive.",
                "- [ ] One ship is destroyed.",
                "- [ ] Both machines show the same winner.",
                "- [ ] Client disconnect awards host forfeit in a second match.",
                "- [ ] Host disconnect returns client to multiplayer menu in a third match.",
                "- [ ] Both processes return cleanly to the multiplayer menu.",
                "- [ ] Campaign saves and campaign state remain unchanged.",
                "",
                "## Observations",
                "",
                "- Snapshot gap / perceived latency:",
                "- Disconnects or reconnect attempts:",
                "- Errors or warnings:",
                "- Memory/process growth:",
                "- Follow-up defects filed:",
                "");
    }

    private static String fieldValue(String text, String field) {
        String prefix = "- " + field + ":";
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) return trimmed.substring(prefix.length()).trim();
        }
        return "";
    }

    private static boolean containsPlaceholder(String value) {
        return value.contains("<") || value.contains(">");
    }

    private static void requireResultSuccess(String text,
                                             List<String> missing,
                                             String field,
                                             String... tokens) {
        String value = fieldValue(text, field).toLowerCase();
        if (value.isBlank()) return;
        for (String token : tokens) {
            if (value.contains(token.toLowerCase())) return;
        }
        missing.add(field + ".success");
    }

    private static Map<String, String> parseArgs(String[] args) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String arg : args) {
            String text = arg == null ? "" : arg.trim();
            if (!text.startsWith("--")) continue;
            int eq = text.indexOf('=');
            if (eq > 2) out.put(text.substring(2, eq), text.substring(eq + 1));
            else out.put(text.substring(2), "true");
        }
        return out;
    }
}
