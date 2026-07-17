import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes an explicit audit of which V1 multiplayer acceptance gates are proven. */
public final class MultiplayerAcceptanceAuditHarness {
    private MultiplayerAcceptanceAuditHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        Path twoProcessReport = Path.of(options.getOrDefault(
                "two-process-report", "build/reports/multiplayer-two-process-acceptance.txt"));
        Path preflightReport = Path.of(options.getOrDefault(
                "preflight-report", "build/reports/multiplayer-lan-preflight.txt"));
        Path hostReport = Path.of(options.getOrDefault(
                "host-report", "build/reports/multiplayer-lan-host-acceptance.txt"));
        Path clientReport = Path.of(options.getOrDefault(
                "client-report", "build/reports/multiplayer-lan-client-acceptance.txt"));
        Path interactiveReport = Path.of(options.getOrDefault(
                "interactive-report", "build/reports/multiplayer-interactive-two-process-manual.txt"));
        Path finalReport = Path.of(options.getOrDefault(
                "final-report", "build/reports/multiplayer-final-two-machine-manual.txt"));
        Path twoMachineLog = Path.of(options.getOrDefault(
                "two-machine-log", "build/reports/multiplayer-two-machine-acceptance-log.md"));
        Path readinessReport = Path.of(options.getOrDefault(
                "readiness-report", "build/reports/multiplayer-two-machine-readiness.txt"));
        boolean strict = Boolean.parseBoolean(options.getOrDefault("strict", "false"));
        Path output = Path.of(options.getOrDefault(
                "report", "build/reports/multiplayer-acceptance-audit.txt"));

        MultiplayerReleaseReadinessV1.AcceptanceAudit audit =
                MultiplayerReleaseReadinessV1.auditAcceptanceEvidence(
                        twoProcessReport, preflightReport, hostReport, clientReport,
                        interactiveReport, finalReport, twoMachineLog, readinessReport);
        writeReport(output, audit);
        System.out.println("complete=" + audit.complete());
        System.out.println("missingGates=" + audit.missingGates().size());
        System.out.println("reportPath=" + output.toAbsolutePath().normalize());
        if (strict && !audit.complete()) {
            throw new IllegalStateException("Multiplayer acceptance audit has missing gates");
        }
    }

    public static void writeReport(Path output,
                                   MultiplayerReleaseReadinessV1.AcceptanceAudit audit) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder out = new StringBuilder();
        out.append("complete=").append(audit.complete()).append(System.lineSeparator());
        out.append("missingGates=").append(audit.missingGates().size()).append(System.lineSeparator());
        out.append("timestamp=").append(Instant.now()).append(System.lineSeparator());
        int index = 1;
        for (MultiplayerReleaseReadinessV1.AcceptanceGate gate : audit.gates()) {
            out.append("gate.").append(index).append(".name=").append(gate.name()).append(System.lineSeparator());
            out.append("gate.").append(index).append(".proven=").append(gate.proven()).append(System.lineSeparator());
            out.append("gate.").append(index).append(".externalManual=").append(gate.externalManual()).append(System.lineSeparator());
            out.append("gate.").append(index).append(".reason=").append(gate.reason()).append(System.lineSeparator());
            index++;
        }
        Files.writeString(absolute, out.toString(), StandardCharsets.UTF_8);
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
