import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Generates a consistent two-machine V1 multiplayer acceptance run package. */
public final class MultiplayerTwoMachineRunbookHarness {
    public record RunbookConfig(Path outputDir,
                                String hostAddress,
                                String clientAddress,
                                int port,
                                int timeoutMs,
                                String matchId,
                                String tester,
                                String buildId) {
        public RunbookConfig {
            outputDir = outputDir == null ? Path.of("build", "reports", "multiplayer-two-machine-run") : outputDir;
            hostAddress = clean(hostAddress, "<host-lan-ip>");
            clientAddress = clean(clientAddress, "<client-lan-ip>");
            port = port <= 0 || port > 65_535 ? MultiplayerLanTransportV1.DEFAULT_PORT : port;
            timeoutMs = Math.max(1_000, timeoutMs);
            matchId = clean(matchId, "manual-lan-acceptance");
            tester = clean(tester, "");
            buildId = clean(buildId, MultiplayerProtocolV1.localFingerprint().gameBuild());
        }
    }

    public record RunbookPaths(Path root,
                               Path readme,
                               Path hostScript,
                               Path clientScript,
                               Path auditScript,
                               Path interactiveManualReport,
                               Path finalManualReport,
                               Path twoMachineLog) {}

    private MultiplayerTwoMachineRunbookHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        RunbookConfig config = new RunbookConfig(
                Path.of(options.getOrDefault("dir", "build/reports/multiplayer-two-machine-run")),
                options.getOrDefault("host-address", "<host-lan-ip>"),
                options.getOrDefault("client-address", "<client-lan-ip>"),
                parseInt(options.getOrDefault("port", String.valueOf(MultiplayerLanTransportV1.DEFAULT_PORT))),
                parseInt(options.getOrDefault("timeout-ms", "60000")),
                options.getOrDefault("match", "manual-lan-acceptance"),
                options.getOrDefault("tester", ""),
                options.getOrDefault("build", MultiplayerProtocolV1.localFingerprint().gameBuild()));

        RunbookPaths paths = writeRunbook(config);
        System.out.println("runbookDir=" + paths.root().toAbsolutePath().normalize());
        System.out.println("hostScript=" + paths.hostScript().toAbsolutePath().normalize());
        System.out.println("clientScript=" + paths.clientScript().toAbsolutePath().normalize());
        System.out.println("auditScript=" + paths.auditScript().toAbsolutePath().normalize());
    }

    public static RunbookPaths writeRunbook(RunbookConfig config) throws Exception {
        Path root = config.outputDir().toAbsolutePath().normalize();
        Files.createDirectories(root);

        Path readme = root.resolve("README.md");
        Path hostScript = root.resolve("host-acceptance.ps1");
        Path clientScript = root.resolve("client-acceptance.ps1");
        Path auditScript = root.resolve("audit-acceptance.ps1");
        Path interactiveManual = root.resolve("interactive-two-process-manual.txt");
        Path finalManual = root.resolve("final-two-machine-manual.txt");
        Path twoMachineLog = root.resolve("two-machine-acceptance-log.md");

        Files.writeString(readme, readmeText(config), StandardCharsets.UTF_8);
        Files.writeString(hostScript, hostScriptText(config), StandardCharsets.UTF_8);
        Files.writeString(clientScript, clientScriptText(config), StandardCharsets.UTF_8);
        Files.writeString(auditScript, auditScriptText(config), StandardCharsets.UTF_8);
        Files.writeString(interactiveManual, manualTemplate(config, false), StandardCharsets.UTF_8);
        Files.writeString(finalManual, manualTemplate(config, true), StandardCharsets.UTF_8);
        Files.writeString(twoMachineLog, MultiplayerTwoMachineAcceptanceLogHarness.templateText(), StandardCharsets.UTF_8);

        return new RunbookPaths(root, readme, hostScript, clientScript, auditScript, interactiveManual, finalManual, twoMachineLog);
    }

    static String readmeText(RunbookConfig config) {
        String root = slash(config.outputDir().toAbsolutePath().normalize());
        return String.join(System.lineSeparator(),
                "# Multiplayer V1 Two-Machine Acceptance Runbook",
                "",
                "Generated: " + Instant.now(),
                "Match: " + config.matchId(),
                "Host address: " + config.hostAddress() + ":" + config.port(),
                "Client address: " + config.clientAddress(),
                "Timeout: " + config.timeoutMs() + " ms",
                "",
                "1. On the host machine, run `host-acceptance.ps1` from the repository root.",
                "2. On the client machine, run `client-acceptance.ps1` from the repository root.",
                "3. Fill `interactive-two-process-manual.txt` after the interactive two-process pass.",
                "4. Fill `final-two-machine-manual.txt` after the real two-machine pass.",
                "5. Fill `two-machine-acceptance-log.md` after the real two-machine pass.",
                "6. Run `audit-acceptance.ps1` from the repository root; it writes readiness evidence, validates reports, validates the two-machine log, writes the acceptance audit, runs the release gate, and writes the evidence bundle.",
                "",
                "Do not mark the checklist complete until the audit report says `complete=true` and the release gate prints `allowed=true`.",
                "Runbook directory: " + root,
                "");
    }

    static String hostScriptText(RunbookConfig config) {
        String preflight = report(config, "preflight.txt");
        String host = report(config, "host.txt");
        String twoProcess = report(config, "two-process.txt");
        return String.join(System.lineSeparator(),
                "$ErrorActionPreference = \"Stop\"",
                ".\\gradlew.bat \"-PmpReport=" + twoProcess + "\" \"-PmpTimeoutMs=15000\" multiplayerTwoProcessAcceptance",
                ".\\gradlew.bat \"-PmpPort=" + config.port() + "\" \"-PmpReport=" + preflight + "\" multiplayerLanPreflight",
                ".\\gradlew.bat \"-PmpPort=" + config.port() + "\" \"-PmpHostAddress=" + config.hostAddress()
                        + "\" \"-PmpTimeoutMs=" + config.timeoutMs()
                        + "\" \"-PmpMatch=" + config.matchId() + "\" \"-PmpReport=" + host + "\" multiplayerLanAcceptanceHost",
                "");
    }

    static String clientScriptText(RunbookConfig config) {
        String client = report(config, "client.txt");
        return String.join(System.lineSeparator(),
                "$ErrorActionPreference = \"Stop\"",
                ".\\gradlew.bat \"-PmpAddress=" + config.hostAddress() + ":" + config.port()
                        + "\" \"-PmpClientAddress=" + config.clientAddress()
                        + "\" \"-PmpTimeoutMs=" + config.timeoutMs() + "\" \"-PmpMatch=" + config.matchId()
                        + "\" \"-PmpReport=" + client + "\" multiplayerLanAcceptanceClient",
                "");
    }

    static String auditScriptText(RunbookConfig config) {
        return String.join(System.lineSeparator(),
                "$ErrorActionPreference = \"Stop\"",
                ".\\gradlew.bat \"-PmpPort=" + config.port()
                        + "\" \"-PmpRunDir=" + slash(config.outputDir().normalize())
                        + "\" \"-PmpTwoProcessReport=" + report(config, "two-process.txt")
                        + "\" \"-PmpInteractiveReport=" + report(config, "interactive-two-process-manual.txt")
                        + "\" \"-PmpFinalReport=" + report(config, "final-two-machine-manual.txt")
                        + "\" \"-PmpReadinessReport=" + report(config, "readiness.txt")
                        + "\" multiplayerTwoMachineReadiness",
                ".\\gradlew.bat \"-PmpHostReport=" + report(config, "host.txt")
                        + "\" \"-PmpClientReport=" + report(config, "client.txt")
                        + "\" multiplayerLanAcceptanceValidate",
                ".\\gradlew.bat \"-PmpMode=validate\" \"-PmpScope=interactive-two-process\" \"-PmpReport="
                        + report(config, "interactive-two-process-manual.txt") + "\" multiplayerManualAcceptanceReport",
                ".\\gradlew.bat \"-PmpMode=validate\" \"-PmpScope=final-two-machine\" \"-PmpReport="
                        + report(config, "final-two-machine-manual.txt") + "\" multiplayerManualAcceptanceReport",
                ".\\gradlew.bat \"-PmpMode=validate\" \"-PmpTwoMachineLog="
                        + report(config, "two-machine-acceptance-log.md") + "\" multiplayerTwoMachineAcceptanceLog",
                ".\\gradlew.bat \"-PmpTwoProcessReport=" + report(config, "two-process.txt")
                        + "\" \"-PmpPreflightReport=" + report(config, "preflight.txt")
                        + "\" \"-PmpHostReport=" + report(config, "host.txt")
                        + "\" \"-PmpClientReport=" + report(config, "client.txt")
                        + "\" \"-PmpInteractiveReport=" + report(config, "interactive-two-process-manual.txt")
                        + "\" \"-PmpFinalReport=" + report(config, "final-two-machine-manual.txt")
                        + "\" \"-PmpTwoMachineLog=" + report(config, "two-machine-acceptance-log.md")
                        + "\" \"-PmpReadinessReport=" + report(config, "readiness.txt")
                        + "\" \"-PmpAuditReport=" + report(config, "acceptance-audit.txt")
                        + "\" \"-PmpStrict=true\" multiplayerAcceptanceAudit",
                ".\\gradlew.bat \"-PmpTwoProcessReport=" + report(config, "two-process.txt")
                        + "\" \"-PmpPreflightReport=" + report(config, "preflight.txt")
                        + "\" \"-PmpHostReport=" + report(config, "host.txt")
                        + "\" \"-PmpClientReport=" + report(config, "client.txt")
                        + "\" \"-PmpInteractiveReport=" + report(config, "interactive-two-process-manual.txt")
                        + "\" \"-PmpFinalReport=" + report(config, "final-two-machine-manual.txt")
                        + "\" \"-PmpTwoMachineLog=" + report(config, "two-machine-acceptance-log.md")
                        + "\" \"-PmpReadinessReport=" + report(config, "readiness.txt")
                        + "\" multiplayerReleaseGate",
                ".\\gradlew.bat \"-PmpTwoProcessReport=" + report(config, "two-process.txt")
                        + "\" \"-PmpPreflightReport=" + report(config, "preflight.txt")
                        + "\" \"-PmpHostReport=" + report(config, "host.txt")
                        + "\" \"-PmpClientReport=" + report(config, "client.txt")
                        + "\" \"-PmpInteractiveReport=" + report(config, "interactive-two-process-manual.txt")
                        + "\" \"-PmpFinalReport=" + report(config, "final-two-machine-manual.txt")
                        + "\" \"-PmpAuditReport=" + report(config, "acceptance-audit.txt")
                        + "\" \"-PmpReadinessReport=" + report(config, "readiness.txt")
                        + "\" \"-PmpTwoMachineLog=" + report(config, "two-machine-acceptance-log.md")
                        + "\" \"-PmpBundleReport=" + report(config, "evidence-bundle.txt")
                        + "\" multiplayerEvidenceBundle",
                "");
    }

    private static String manualTemplate(RunbookConfig config, boolean finalTwoMachine) {
        String text = MultiplayerManualAcceptanceReportHarness.templateText(
                finalTwoMachine ? "final-two-machine" : "interactive-two-process",
                finalTwoMachine,
                report(config, "two-process.txt"),
                report(config, "preflight.txt"),
                report(config, "host.txt"),
                report(config, "client.txt"));
        text = text.replace("tester=", "tester=" + config.tester());
        text = text.replace("build=", "build=" + config.buildId());
        if (finalTwoMachine) {
            text = text.replace("hostAddress=<host-lan-ip>:46717",
                    "hostAddress=" + config.hostAddress() + ":" + config.port());
            text = text.replace("clientAddress=<client-lan-ip-or-machine-name>",
                    "clientAddress=" + config.clientAddress());
        }
        return text;
    }

    private static String report(RunbookConfig config, String filename) {
        return slash(config.outputDir().resolve(filename).normalize());
    }

    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
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
            return Integer.parseInt(clean(text, "0"));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String clean(String text, String fallback) {
        String value = text == null ? "" : text.trim();
        return value.isBlank() ? fallback : value;
    }
}
