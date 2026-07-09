import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestBaselineHarnessTest {
    @Test
    void approvedSeedsProduceTimelineMetadataAndInvariantReport() {
        OwnerPlaytestBaselineHarness.RunReport report = OwnerPlaytestBaselineHarness.run(60);

        assertEquals(60, report.seconds);
        assertFalse(report.gitCommit.isBlank());
        assertTrue(report.timelineRows.get(0).startsWith("seed,second,green_fleets"));
        for (long seed : OwnerPlaytestBaselineHarness.APPROVED_BASELINE_SEEDS) {
            assertTrue(report.timelineRows.stream().anyMatch(row -> row.startsWith(seed + ",0,")),
                    "missing initial baseline row for seed " + seed);
            assertTrue(report.timelineRows.stream().anyMatch(row -> row.startsWith(seed + ",60,")),
                    "missing 60 second baseline row for seed " + seed);
        }
        assertTrue(report.passed(), String.join("\n", report.failures));
    }
}
