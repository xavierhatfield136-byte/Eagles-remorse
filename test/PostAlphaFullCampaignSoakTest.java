import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAlphaFullCampaignSoakTest {
    @Test
    void longMultiSeedTerritorialCampaignHasNoIllegalCaptureOrUnrecoverableState() {
        for (long seed = 12_000L; seed < 12_012L; seed++) {
            StrategicCampaignExpansionSystem.StrategicSoakReport report =
                    StrategicCampaignExpansionSystem.runHeadlessStrategicSoak(seed, 25_000);
            assertTrue(report.passed(), report.diagnostics().toString());
            assertEquals(0, report.illegalCaptures());
            assertFalse(report.runawayFaction());
        }
    }
}
