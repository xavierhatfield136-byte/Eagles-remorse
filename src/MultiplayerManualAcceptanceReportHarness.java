import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates and validates manual acceptance evidence files for V1 multiplayer gates. */
public final class MultiplayerManualAcceptanceReportHarness {
    private MultiplayerManualAcceptanceReportHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        String mode = options.getOrDefault("mode", "template");
        String scope = options.getOrDefault("scope", "interactive-two-process");
        Path report = Path.of(options.getOrDefault("report", defaultReport(scope)));
        boolean realLan = Boolean.parseBoolean(options.getOrDefault(
                "real-lan", String.valueOf("final-two-machine".equalsIgnoreCase(scope))));

        if ("template".equalsIgnoreCase(mode)) {
            writeTemplate(report, scope, realLan);
            System.out.println("templatePath=" + report.toAbsolutePath().normalize());
            return;
        }
        if (!"validate".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        MultiplayerReleaseReadinessV1.ManualAcceptanceEvidence evidence =
                MultiplayerReleaseReadinessV1.validateManualAcceptanceReport(report, scope, realLan);
        System.out.println("accepted=" + evidence.accepted());
        System.out.println("reason=" + evidence.reason());
        System.out.println("missingChecks=" + evidence.missingChecks().size());
        for (int i = 0; i < evidence.missingChecks().size(); i++) {
            System.out.println("missing." + (i + 1) + "=" + evidence.missingChecks().get(i));
        }
        if (!evidence.accepted()) {
            System.err.println("accepted=" + evidence.accepted());
            System.err.println("reason=" + evidence.reason());
            System.err.println("missingChecks=" + evidence.missingChecks().size());
            for (int i = 0; i < evidence.missingChecks().size(); i++) {
                System.err.println("missing." + (i + 1) + "=" + evidence.missingChecks().get(i));
            }
            throw new IllegalStateException("Manual acceptance report is incomplete");
        }
    }

    public static void writeTemplate(Path report, String scope, boolean realLan) throws Exception {
        Path absolute = report.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(absolute, templateText(scope, realLan), StandardCharsets.UTF_8);
    }

    public static String templateText(String scope, boolean realLan) {
        return templateText(scope, realLan,
                "build/reports/multiplayer-two-process-acceptance.txt",
                "build/reports/multiplayer-lan-preflight.txt",
                "build/reports/multiplayer-lan-host-acceptance.txt",
                "build/reports/multiplayer-lan-client-acceptance.txt");
    }

    public static String templateText(String scope,
                                      boolean realLan,
                                      String twoProcessReport,
                                      String preflightReport,
                                      String hostReport,
                                      String clientReport) {
        StringBuilder out = new StringBuilder();
        out.append("passed=false").append(System.lineSeparator());
        out.append("scope=").append(scope).append(System.lineSeparator());
        out.append("tester=").append(System.lineSeparator());
        out.append("build=").append(System.lineSeparator());
        out.append("date=").append(LocalDate.now()).append(System.lineSeparator());
        out.append("hostAddress=").append(realLan ? "<host-lan-ip>:46717" : "127.0.0.1:46717").append(System.lineSeparator());
        out.append("clientAddress=").append(realLan ? "<client-lan-ip-or-machine-name>" : "127.0.0.1").append(System.lineSeparator());
        out.append("twoProcessReport=").append(cleanPath(twoProcessReport)).append(System.lineSeparator());
        out.append("preflightReport=").append(cleanPath(preflightReport)).append(System.lineSeparator());
        out.append("hostReport=").append(cleanPath(hostReport)).append(System.lineSeparator());
        out.append("clientReport=").append(cleanPath(clientReport)).append(System.lineSeparator());
        for (String check : MultiplayerReleaseReadinessV1.requiredManualAcceptanceChecks()) {
            out.append(check).append("=false").append(System.lineSeparator());
        }
        out.append("notes=").append(System.lineSeparator());
        return out.toString();
    }

    private static String cleanPath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/');
    }

    private static String defaultReport(String scope) {
        if ("final-two-machine".equalsIgnoreCase(scope)) {
            return "build/reports/multiplayer-final-two-machine-manual.txt";
        }
        return "build/reports/multiplayer-interactive-two-process-manual.txt";
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
