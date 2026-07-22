import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerRulesV1Test {

    @Test
    void multiplayerCustomMissionEntryPointDefaultsOn() {
        assertTrue(MultiplayerRulesV1.entryPointEnabled(),
                "multiplayer custom mission entry points should be available from the main menu");
    }

    @Test
    void defaultDuelSetupIsAccepted() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(1234L, ShipRole.FRIGATE, ShipRole.CRUISER);

        MultiplayerRulesV1.ValidationResult result = MultiplayerRulesV1.validate(setup);

        assertTrue(result.accepted(), result.message());
    }

    @Test
    void v1ScopeConstantsRecordLockedRules() {
        assertFalse(MultiplayerRulesV1.CAMPAIGN_MULTIPLAYER_SUPPORTED);
        assertFalse(MultiplayerRulesV1.SAME_TEAM_COOP_SUPPORTED);
        assertTrue(MultiplayerRulesV1.AI_SHIPS_SUPPORTED);
        assertTrue(MultiplayerRulesV1.AI_SUPPORT_RULES_PROFILE_ID.equals("multiplayer:v1_ai_support"));
        assertFalse(MultiplayerRulesV1.RESPAWNS_SUPPORTED);
        assertFalse(MultiplayerRulesV1.RECONNECT_SUPPORTED);
        assertFalse(MultiplayerRulesV1.MID_MATCH_JOIN_SUPPORTED);
        assertFalse(MultiplayerRulesV1.HOST_MIGRATION_SUPPORTED);
        assertFalse(MultiplayerRulesV1.ACTIVE_MATCH_PAUSE_SUPPORTED);
        assertFalse(MultiplayerRulesV1.SUPERWEAPONS_SUPPORTED);
        assertFalse(MultiplayerRulesV1.BATTLEFIELD_WARP_SUPPORTED);
        assertTrue(MultiplayerRulesV1.PLAYER_COUNT == 2);
        assertTrue(MultiplayerRulesV1.VictoryRule.ELIMINATION != null);
        assertTrue(MultiplayerRulesV1.supportedCapabilities().contains(MultiplayerCapability.OPPOSING_PLAYERS));
        assertFalse(MultiplayerRulesV1.supportedCapabilities().contains(MultiplayerCapability.AI_REPLICATION));
        assertFalse(MultiplayerRulesV1.supportedCapabilities().contains(MultiplayerCapability.OBJECTIVE_REPLICATION));
    }

    @Test
    void futureCustomMissionRulesProfileExpandsCapabilitiesWithoutChangingV1() {
        assertTrue(MultiplayerRulesV1.RULES_PROFILE_ID.equals("multiplayer:v1"));
        assertTrue(MultiplayerRulesCustomMission.RULES_PROFILE_ID.equals("multiplayer:custom_mission_v2"));
        assertTrue(MultiplayerRulesCustomMission.supportedCapabilities()
                .contains(MultiplayerCapability.AI_REPLICATION));
        assertTrue(MultiplayerRulesCustomMission.supportedCapabilities()
                .contains(MultiplayerCapability.OBJECTIVE_REPLICATION));
        assertFalse(MultiplayerRulesV1.supportedCapabilities()
                .contains(MultiplayerCapability.AI_REPLICATION));
    }

    @Test
    void campaignLaunchConfigIsRejected() {
        GameConfig campaign = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false);

        MultiplayerRulesV1.ValidationResult result = MultiplayerRulesV1.validateBattleOnlyConfig(campaign);

        assertFalse(result.accepted());
        assertTrue(result.message().contains("Campaign multiplayer is unsupported"));
    }

    @Test
    void customBattleLaunchConfigIsAccepted() {
        GameConfig customBattle = new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 1234L, false);

        MultiplayerRulesV1.ValidationResult result = MultiplayerRulesV1.validateBattleOnlyConfig(customBattle);

        assertTrue(result.accepted(), result.message());
    }

    @Test
    void v1RejectsSameTeamCoopAndUnsupportedBattleFeatures() {
        MultiplayerRulesV1.BattleSetup invalid = new MultiplayerRulesV1.BattleSetup(
                1234L,
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY, ShipRole.FRIGATE, "Host"),
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.PLAYER, ShipRole.CRUISER, "Client"),
                MultiplayerRulesV1.VictoryRule.ELIMINATION,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true);

        MultiplayerRulesV1.ValidationResult result = MultiplayerRulesV1.validate(invalid);
        String errors = String.join("\n", result.errors());

        assertFalse(result.accepted());
        assertTrue(errors.contains("Same-team co-op is unsupported in V1"));
        assertTrue(errors.contains("Escorts are unsupported in V1"));
        assertTrue(errors.contains("Formations and fleet-wide orders are unsupported in V1"));
        assertTrue(errors.contains("Fog of war and sensor-filtered replication are unsupported in V1"));
        assertTrue(errors.contains("Respawns are unsupported in V1"));
        assertTrue(errors.contains("Reconnect is unsupported in V1"));
        assertTrue(errors.contains("Mid-match joining is unsupported in V1"));
        assertTrue(errors.contains("Host migration is unsupported in V1"));
        assertTrue(errors.contains("Active-match pause is unsupported in V1"));
        assertTrue(errors.contains("Superweapons are disabled in V1"));
        assertTrue(errors.contains("Battlefield warp is disabled in V1"));
    }

    @Test
    void v1RejectsNonShipPlayerSlots() {
        MultiplayerRulesV1.BattleSetup invalid = new MultiplayerRulesV1.BattleSetup(
                1234L,
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY, ShipRole.BASE, "Host"),
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY, ShipRole.CRUISER, "Client"),
                MultiplayerRulesV1.VictoryRule.ELIMINATION,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);

        MultiplayerRulesV1.ValidationResult result = MultiplayerRulesV1.validate(invalid);

        assertFalse(result.accepted());
        assertTrue(String.join("\n", result.errors()).contains("directly controllable ship hull"));
    }
}
