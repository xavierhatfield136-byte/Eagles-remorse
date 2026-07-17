import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fails release validation when multiplayer is enabled without complete acceptance evidence. */
public final class MultiplayerReleaseGateHarness {
    private MultiplayerReleaseGateHarness() {}

    public static void main(String[] args) {
        Map<String, String> options = parseArgs(args);
        MultiplayerReleaseReadinessV1.MultiplayerReleaseGate gate =
                MultiplayerReleaseReadinessV1.validateMultiplayerReleaseGate(
                        Path.of(options.getOrDefault(
                                "two-process-report", "build/reports/multiplayer-two-process-acceptance.txt")),
                        Path.of(options.getOrDefault(
                                "preflight-report", "build/reports/multiplayer-lan-preflight.txt")),
                        Path.of(options.getOrDefault(
                                "host-report", "build/reports/multiplayer-lan-host-acceptance.txt")),
                        Path.of(options.getOrDefault(
                                "client-report", "build/reports/multiplayer-lan-client-acceptance.txt")),
                        Path.of(options.getOrDefault(
                                "interactive-report", "build/reports/multiplayer-interactive-two-process-manual.txt")),
                        Path.of(options.getOrDefault(
                                "final-report", "build/reports/multiplayer-final-two-machine-manual.txt")),
                        Path.of(options.getOrDefault(
                                "two-machine-log", "build/reports/multiplayer-two-machine-acceptance-log.md")),
                        Path.of(options.getOrDefault(
                                "readiness-report", "build/reports/multiplayer-two-machine-readiness.txt")));

        System.out.println("allowed=" + gate.allowed());
        System.out.println("featureEnabled=" + gate.featureEnabled());
        System.out.println("acceptanceComplete=" + gate.acceptanceComplete());
        System.out.println("reason=" + gate.reason());
        int missing = 0;
        for (MultiplayerReleaseReadinessV1.AcceptanceGate acceptanceGate : gate.gates()) {
            if (acceptanceGate != null && !acceptanceGate.proven()) missing++;
        }
        System.out.println("missingGates=" + missing);
        int index = 1;
        for (MultiplayerReleaseReadinessV1.AcceptanceGate acceptanceGate : gate.gates()) {
            if (acceptanceGate == null) continue;
            System.out.println("gate." + index + ".name=" + acceptanceGate.name());
            System.out.println("gate." + index + ".proven=" + acceptanceGate.proven());
            System.out.println("gate." + index + ".externalManual=" + acceptanceGate.externalManual());
            System.out.println("gate." + index + ".reason=" + acceptanceGate.reason());
            index++;
        }
        if (!gate.allowed()) {
            throw new IllegalStateException(gate.reason());
        }
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
