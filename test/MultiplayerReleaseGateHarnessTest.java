import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerReleaseGateHarnessTest {

    @AfterEach
    void clearFlagOverride() {
        System.clearProperty("game.feature.multiplayer_custom_battle");
    }

    @Test
    void releaseGateAllowsDisabledFeatureWithIncompleteEvidence() throws Exception {
        System.setProperty("game.feature.multiplayer_custom_battle", "false");
        Path dir = Files.createTempDirectory("mp-release-gate-disabled");

        MultiplayerReleaseReadinessV1.MultiplayerReleaseGate gate =
                MultiplayerReleaseReadinessV1.validateMultiplayerReleaseGate(
                        dir.resolve("missing-two-process.txt"),
                        dir.resolve("missing-preflight.txt"),
                        dir.resolve("missing-host.txt"),
                        dir.resolve("missing-client.txt"),
                        dir.resolve("missing-interactive.txt"),
                        dir.resolve("missing-final.txt"));

        assertTrue(gate.allowed(), gate.reason());
        assertFalse(gate.featureEnabled());
        assertFalse(gate.acceptanceComplete());
        assertFalse(gate.gates().isEmpty());
    }

    @Test
    void releaseGateRejectsEnabledFeatureWithIncompleteEvidence() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        Path dir = Path.of("build", "reports", "missing-release-gate");

        MultiplayerReleaseReadinessV1.MultiplayerReleaseGate gate =
                MultiplayerReleaseReadinessV1.validateMultiplayerReleaseGate(
                        dir.resolve("missing-two-process.txt"),
                        dir.resolve("missing-preflight.txt"),
                        dir.resolve("missing-host.txt"),
                        dir.resolve("missing-client.txt"),
                        dir.resolve("missing-interactive.txt"),
                        dir.resolve("missing-final.txt"));

        assertFalse(gate.allowed());
        assertTrue(gate.featureEnabled());
        assertFalse(gate.acceptanceComplete());
        assertTrue(gate.gates().stream().anyMatch(g -> !g.proven()));
    }

    @Test
    void harnessFailsWhenEnabledEvidenceIsIncomplete() throws Exception {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        Path dir = Files.createTempDirectory("mp-release-gate-harness");

        assertThrows(IllegalStateException.class, () ->
                MultiplayerReleaseGateHarness.main(new String[]{
                        "--two-process-report=" + dir.resolve("missing-two-process.txt"),
                        "--preflight-report=" + dir.resolve("missing-preflight.txt"),
                        "--host-report=" + dir.resolve("missing-host.txt"),
                        "--client-report=" + dir.resolve("missing-client.txt"),
                        "--interactive-report=" + dir.resolve("missing-interactive.txt"),
                        "--final-report=" + dir.resolve("missing-final.txt"),
                        "--two-machine-log=" + dir.resolve("missing-two-machine-log.md"),
                        "--readiness-report=" + dir.resolve("missing-readiness.txt")
                }));
    }
}
