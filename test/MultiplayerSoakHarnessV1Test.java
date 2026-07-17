import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerSoakHarnessV1Test {

    @Test
    void loopbackSoakRoutineRunsWithoutSnapshotLossOrTimeout() {
        MultiplayerSoakHarnessV1 harness = new MultiplayerSoakHarnessV1(
                MultiplayerRulesV1.defaultDuel(1900L, ShipRole.FRIGATE, ShipRole.FRIGATE));

        MultiplayerSoakHarnessV1.SoakReport report = harness.runLoopbackTicks(360);

        assertTrue(report.passed(), report.failureReason());
        assertTrue(report.returnedToMenu(), "soak routine should cleanly return to menu");
        assertTrue(report.snapshotsReceived() > 300, "soak should receive regular snapshots");
        assertTrue(report.maxSnapshotGapTicks() <= MultiplayerNetworkConditionsV1.ACCEPTABLE_SNAPSHOT_AGE_TICKS,
                "snapshot gaps should stay inside V1 budget");
    }
}
