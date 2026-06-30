import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicRaidTargetSystemTest {
    @Test
    void everyRaidTargetAppliesARelevantEffectWithoutChangingControl() {
        EnumSet<StrategicCampaignExpansionSystem.RaidTarget> exercised =
                EnumSet.noneOf(StrategicCampaignExpansionSystem.RaidTarget.class);
        for (StrategicCampaignExpansionSystem.RaidTarget type
                : StrategicCampaignExpansionSystem.RaidTarget.values()) {
            StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(9900L + type.ordinal());
            StrategicCampaignExpansionSystem.Territory target =
                    StrategicCampaignExpansionSystem.territory(state, "well");
            String controller = target.controller;
            String before = fingerprint(target);
            StrategicCampaignExpansionSystem.StrategicOperation raid =
                    StrategicCampaignExpansionSystem.startOperation(state,
                            StrategicCampaignExpansionSystem.OperationType.RAID,
                            "BRIGHT_YELLOW", "frontier", "well");
            StrategicCampaignExpansionSystem.configureRaidTarget(raid, type);
            assertTrue(StrategicCampaignExpansionSystem.completeOperation(state, raid.id, true));
            assertEquals(controller, target.controller);
            assertNotEquals(before, fingerprint(target), type.toString());
            exercised.add(type);
        }
        assertEquals(EnumSet.allOf(StrategicCampaignExpansionSystem.RaidTarget.class), exercised);
    }

    @Test
    void selectedRaidTargetPersistsWithTheOperation() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(9910L);
        StrategicCampaignExpansionSystem.StrategicOperation raid =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.RAID,
                        "BRIGHT_YELLOW", "frontier", "well");
        StrategicCampaignExpansionSystem.configureRaidTarget(raid,
                StrategicCampaignExpansionSystem.RaidTarget.INTELLIGENCE);
        StrategicCampaignExpansionSystem.State restored = StrategicCampaignExpansionSystem.restore(
                StrategicCampaignExpansionSystem.serialize(state), 9910L);
        assertEquals(StrategicCampaignExpansionSystem.RaidTarget.INTELLIGENCE,
                restored.operations.get(0).raidTarget);
    }

    private static String fingerprint(StrategicCampaignExpansionSystem.Territory target) {
        return target.infrastructure + ":" + target.morale + ":" + target.frontPressure + ":"
                + target.friendlyFleetStrength + ":" + target.fleetReadiness + ":" + target.defensiveReadiness + ":"
                + target.ammunition + ":" + target.shipyardCapacity + ":" + target.mineOutput + ":"
                + target.sensorCoverage + ":" + target.recentBattleMomentum;
    }
}
