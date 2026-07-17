import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a local readiness report before the external two-machine multiplayer pass. */
public final class MultiplayerTwoMachineReadinessHarness {
    public record ReadinessCheck(String name, boolean passed, String reason) {
        public ReadinessCheck {
            name = clean(name);
            reason = clean(reason);
        }
    }

    public record ReadinessReport(int port,
                                  List<ReadinessCheck> checks,
                                  MultiplayerLanPreflightHarness.PreflightReport preflight) {
        public ReadinessReport {
            port = Math.max(0, port);
            checks = checks == null ? List.of() : List.copyOf(checks);
        }

        public boolean passed() {
            for (ReadinessCheck check : checks) {
                if (check != null && !check.passed()) return false;
            }
            return !checks.isEmpty();
        }

        public String toText() {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("passed=" + passed());
            lines.add("port=" + port);
            lines.add("timestamp=" + Instant.now());
            if (preflight != null) {
                lines.add("preflight.passed=" + preflight.passed());
                lines.add("preflight.portBindable=" + preflight.portBindable());
                lines.add("preflight.candidateAddressCount=" + preflight.candidateAddresses().size());
                for (int i = 0; i < preflight.candidateAddresses().size(); i++) {
                    lines.add("preflight.candidateAddress." + (i + 1) + "=" + preflight.candidateAddresses().get(i));
                }
            }
            lines.add("checkCount=" + checks.size());
            for (int i = 0; i < checks.size(); i++) {
                ReadinessCheck check = checks.get(i);
                lines.add("check." + (i + 1) + ".name=" + check.name());
                lines.add("check." + (i + 1) + ".passed=" + check.passed());
                lines.add("check." + (i + 1) + ".reason=" + check.reason());
            }
            return String.join(System.lineSeparator(), lines);
        }
    }

    private MultiplayerTwoMachineReadinessHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        int port = parseInt(options.getOrDefault(
                "port", String.valueOf(MultiplayerLanTransportV1.DEFAULT_PORT)));
        Path runbookDir = Path.of(options.getOrDefault(
                "runbook-dir", "build/reports/multiplayer-two-machine-run"));
        Path twoProcessReport = Path.of(options.getOrDefault(
                "two-process-report", "build/reports/multiplayer-two-process-acceptance.txt"));
        Path interactiveReport = Path.of(options.getOrDefault(
                "interactive-report", "build/reports/multiplayer-interactive-two-process-manual.txt"));
        Path finalReport = Path.of(options.getOrDefault(
                "final-report", "build/reports/multiplayer-final-two-machine-manual.txt"));
        Path output = Path.of(options.getOrDefault(
                "report", "build/reports/multiplayer-two-machine-readiness.txt"));
        boolean strict = Boolean.parseBoolean(options.getOrDefault("strict", "false"));

        ReadinessReport report = inspect(port, runbookDir, twoProcessReport, interactiveReport, finalReport);
        writeReport(output, report);
        System.out.println(report.toText());
        if (strict && !report.passed()) {
            throw new IllegalStateException("Two-machine multiplayer readiness checks failed");
        }
    }

    public static ReadinessReport inspect(int port,
                                          Path runbookDir,
                                          Path twoProcessReport,
                                          Path interactiveReport,
                                          Path finalReport) {
        MultiplayerLanPreflightHarness.PreflightReport preflight = MultiplayerLanPreflightHarness.run(port);
        ArrayList<ReadinessCheck> checks = new ArrayList<>();
        checks.add(new ReadinessCheck("host port bindable", preflight.portBindable(),
                preflight.portBindable()
                        ? "Port can be bound locally"
                        : "Port " + Math.max(0, port) + " is not bindable on this machine"));
        checks.add(new ReadinessCheck("candidate LAN address", !preflight.candidateAddresses().isEmpty(),
                preflight.candidateAddresses().isEmpty()
                        ? "No non-loopback IPv4 LAN address was found"
                        : "Candidate LAN address count=" + preflight.candidateAddresses().size()));
        checks.add(requiredFile("runbook README", runbookDir.resolve("README.md")));
        checks.add(requiredFile("host acceptance script", runbookDir.resolve("host-acceptance.ps1")));
        checks.add(requiredFile("client acceptance script", runbookDir.resolve("client-acceptance.ps1")));
        checks.add(requiredFile("audit acceptance script", runbookDir.resolve("audit-acceptance.ps1")));
        checks.add(requiredFile("two-process acceptance report", twoProcessReport));
        checks.add(requiredFile("interactive manual template", interactiveReport));
        checks.add(requiredFile("final two-machine manual template", finalReport));
        return new ReadinessReport(port, checks, preflight);
    }

    private static ReadinessCheck requiredFile(String name, Path path) {
        Path normalized = path == null ? Path.of("") : path.toAbsolutePath().normalize();
        boolean exists = path != null && Files.isRegularFile(path);
        return new ReadinessCheck(name, exists,
                exists ? "Found " + normalized : "Missing " + normalized);
    }

    public static void writeReport(Path output, ReadinessReport report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(absolute, report.toText() + System.lineSeparator(), StandardCharsets.UTF_8);
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

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(clean(text));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid integer: " + text);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
