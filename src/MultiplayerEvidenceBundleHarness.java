import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a reviewable manifest of multiplayer acceptance evidence files and gate states. */
public final class MultiplayerEvidenceBundleHarness {
    public record EvidenceFile(String name, Path path, boolean exists, long bytes, String sha256) {}

    private MultiplayerEvidenceBundleHarness() {}

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
        Path auditReport = Path.of(options.getOrDefault(
                "audit-report", "build/reports/multiplayer-acceptance-audit.txt"));
        Path readinessReport = Path.of(options.getOrDefault(
                "readiness-report", "build/reports/multiplayer-two-machine-readiness.txt"));
        Path twoMachineLog = Path.of(options.getOrDefault(
                "two-machine-log", "build/reports/multiplayer-two-machine-acceptance-log.md"));
        Path output = Path.of(options.getOrDefault(
                "report", "build/reports/multiplayer-evidence-bundle.txt"));

        MultiplayerReleaseReadinessV1.AcceptanceAudit audit =
                MultiplayerReleaseReadinessV1.auditAcceptanceEvidence(
                        twoProcessReport, preflightReport, hostReport, clientReport,
                        interactiveReport, finalReport, twoMachineLog, readinessReport);
        MultiplayerReleaseReadinessV1.MultiplayerReleaseGate gate =
                MultiplayerReleaseReadinessV1.validateMultiplayerReleaseGate(
                        twoProcessReport, preflightReport, hostReport, clientReport,
                        interactiveReport, finalReport, twoMachineLog, readinessReport);
        List<EvidenceFile> files = evidenceFiles(
                twoProcessReport, preflightReport, hostReport, clientReport,
                interactiveReport, finalReport, auditReport, readinessReport, twoMachineLog);

        writeReport(output, audit, gate, files);
        System.out.println("bundleReport=" + output.toAbsolutePath().normalize());
        System.out.println("artifactCount=" + files.size());
        System.out.println("missingGates=" + audit.missingGates().size());
    }

    public static List<EvidenceFile> evidenceFiles(Path twoProcessReport,
                                                   Path preflightReport,
                                                   Path hostReport,
                                                   Path clientReport,
                                                   Path interactiveReport,
                                                   Path finalReport,
                                                   Path auditReport,
                                                   Path readinessReport,
                                                   Path twoMachineLog) throws Exception {
        ArrayList<EvidenceFile> files = new ArrayList<>();
        files.add(evidenceFile("twoProcessReport", twoProcessReport));
        files.add(evidenceFile("preflightReport", preflightReport));
        files.add(evidenceFile("hostReport", hostReport));
        files.add(evidenceFile("clientReport", clientReport));
        files.add(evidenceFile("interactiveManualReport", interactiveReport));
        files.add(evidenceFile("finalManualReport", finalReport));
        files.add(evidenceFile("acceptanceAuditReport", auditReport));
        files.add(evidenceFile("twoMachineReadinessReport", readinessReport));
        files.add(evidenceFile("twoMachineAcceptanceLog", twoMachineLog));
        return List.copyOf(files);
    }

    public static void writeReport(Path output,
                                   MultiplayerReleaseReadinessV1.AcceptanceAudit audit,
                                   MultiplayerReleaseReadinessV1.MultiplayerReleaseGate gate,
                                   List<EvidenceFile> files) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder out = new StringBuilder();
        MultiplayerProtocolV1.CompatibilityFingerprint fingerprint =
                MultiplayerProtocolV1.localFingerprint();
        MultiplayerProtocolV1.ContentManifest manifest = fingerprint.manifest();
        out.append("generatedAt=").append(Instant.now()).append(System.lineSeparator());
        out.append("protocolVersion=").append(fingerprint.protocolVersion()).append(System.lineSeparator());
        out.append("gameBuild=").append(fingerprint.gameBuild()).append(System.lineSeparator());
        out.append("manifest.rulesHash=").append(manifest.rulesHash()).append(System.lineSeparator());
        out.append("manifest.hullDefinitionsHash=").append(manifest.hullDefinitionsHash()).append(System.lineSeparator());
        out.append("manifest.weaponsHash=").append(manifest.weaponsHash()).append(System.lineSeparator());
        out.append("manifest.abilitiesHash=").append(manifest.abilitiesHash()).append(System.lineSeparator());
        out.append("manifest.arenaHash=").append(manifest.arenaHash()).append(System.lineSeparator());
        out.append("manifest.enabledModsHash=").append(manifest.enabledModsHash()).append(System.lineSeparator());
        out.append("manifest.requiredAssetsHash=").append(manifest.requiredAssetsHash()).append(System.lineSeparator());
        out.append("featureEnabled=").append(gate.featureEnabled()).append(System.lineSeparator());
        out.append("releaseAllowed=").append(gate.allowed()).append(System.lineSeparator());
        out.append("acceptanceComplete=").append(audit.complete()).append(System.lineSeparator());
        out.append("missingGates=").append(audit.missingGates().size()).append(System.lineSeparator());
        out.append("artifactCount=").append(files == null ? 0 : files.size()).append(System.lineSeparator());
        int artifactIndex = 1;
        if (files != null) {
            for (EvidenceFile file : files) {
                out.append("artifact.").append(artifactIndex).append(".name=").append(file.name()).append(System.lineSeparator());
                out.append("artifact.").append(artifactIndex).append(".path=").append(normalize(file.path())).append(System.lineSeparator());
                out.append("artifact.").append(artifactIndex).append(".exists=").append(file.exists()).append(System.lineSeparator());
                out.append("artifact.").append(artifactIndex).append(".bytes=").append(file.bytes()).append(System.lineSeparator());
                out.append("artifact.").append(artifactIndex).append(".sha256=").append(file.sha256()).append(System.lineSeparator());
                artifactIndex++;
            }
        }
        int gateIndex = 1;
        for (MultiplayerReleaseReadinessV1.AcceptanceGate acceptanceGate : audit.gates()) {
            out.append("gate.").append(gateIndex).append(".name=").append(acceptanceGate.name()).append(System.lineSeparator());
            out.append("gate.").append(gateIndex).append(".proven=").append(acceptanceGate.proven()).append(System.lineSeparator());
            out.append("gate.").append(gateIndex).append(".externalManual=").append(acceptanceGate.externalManual()).append(System.lineSeparator());
            out.append("gate.").append(gateIndex).append(".reason=").append(acceptanceGate.reason()).append(System.lineSeparator());
            gateIndex++;
        }
        Files.writeString(absolute, out.toString(), StandardCharsets.UTF_8);
    }

    private static EvidenceFile evidenceFile(String name, Path path) throws Exception {
        Path normalized = path == null ? Path.of("") : path.toAbsolutePath().normalize();
        if (path == null || !Files.isRegularFile(path)) {
            return new EvidenceFile(name, normalized, false, 0L, "");
        }
        byte[] bytes = Files.readAllBytes(path);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new EvidenceFile(name, normalized, true, bytes.length,
                HexFormat.of().formatHex(digest.digest(bytes)));
    }

    private static String normalize(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
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
