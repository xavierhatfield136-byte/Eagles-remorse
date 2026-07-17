import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes the default pair of manual V1 multiplayer acceptance evidence templates. */
public final class MultiplayerManualAcceptanceTemplateSetHarness {
    private MultiplayerManualAcceptanceTemplateSetHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        Path interactiveReport = Path.of(options.getOrDefault(
                "interactive-report", "build/reports/multiplayer-interactive-two-process-manual.txt"));
        Path finalReport = Path.of(options.getOrDefault(
                "final-report", "build/reports/multiplayer-final-two-machine-manual.txt"));

        MultiplayerManualAcceptanceReportHarness.writeTemplate(
                interactiveReport, "interactive-two-process", false);
        MultiplayerManualAcceptanceReportHarness.writeTemplate(
                finalReport, "final-two-machine", true);

        System.out.println("interactiveTemplate=" + interactiveReport.toAbsolutePath().normalize());
        System.out.println("finalTemplate=" + finalReport.toAbsolutePath().normalize());
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
