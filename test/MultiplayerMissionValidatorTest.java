import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerMissionValidatorTest {

    @Test
    void acceptsCatalogBackedV1DuelSpecs() {
        MissionLaunchSpec spec = CustomMissionCatalog.resolveV1Duel(
                1L, 3600, 2200, ShipRole.FRIGATE, ShipRole.FRIGATE);

        MultiplayerMissionValidator.ValidationResult result =
                MultiplayerMissionValidator.validateForV1(spec);

        assertTrue(result.accepted(), result.message());
    }

    @Test
    void rejectsUnsupportedMissionRulesProfileObjectiveAndWorldSize() {
        MissionLaunchSpec invalid = new MissionLaunchSpec(
                "core:missing_mission",
                1,
                1L,
                1200,
                70000,
                duelSlots(ShipRole.FRIGATE, ShipRole.FRIGATE),
                duelSlots(ShipRole.FRIGATE, ShipRole.FRIGATE),
                "multiplayer:v9",
                "capture",
                MultiplayerRulesV1.VictoryRule.ELIMINATION);

        MultiplayerMissionValidator.ValidationResult result =
                MultiplayerMissionValidator.validateForV1(invalid);
        String errors = String.join("\n", result.errors());

        assertFalse(result.accepted());
        assertTrue(errors.contains("Selected mission unavailable: core:missing_mission"));
        assertTrue(errors.contains("Rules profile unsupported"));
        assertTrue(errors.contains("Invalid world size"));
        assertTrue(errors.contains("Unsupported objective replication: capture"));
    }

    @Test
    void rejectsObjectiveReplicationBeforeHostMatchLoading() {
        MissionLaunchSpec invalidObjective = new MissionLaunchSpec(
                CustomMissionCatalog.V1_DUEL_ID,
                CustomMissionCatalog.V1_DUEL_REVISION,
                7L,
                3600,
                2200,
                duelSlots(ShipRole.FRIGATE, ShipRole.FRIGATE),
                duelSlots(ShipRole.FRIGATE, ShipRole.FRIGATE),
                MultiplayerRulesV1.RULES_PROFILE_ID,
                "capture",
                MultiplayerRulesV1.VictoryRule.ELIMINATION);

        MultiplayerMissionValidator.ValidationResult result =
                MultiplayerMissionValidator.validateForV1(invalidObjective);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("Unsupported objective replication: capture"));
        assertThrows(IllegalArgumentException.class,
                () -> MultiplayerHostLaunchAdapter.toBattleSetup(invalidObjective, "Host", "Client"));
    }

    @Test
    void rejectsUnsupportedHullMissingDuplicateAndTeamConflictSlots() {
        MissionSlotSpec badHost = new MissionSlotSpec(
                MultiplayerRulesV1.HOST_SLOT_ID,
                Faction.ALLY.teamId(),
                ShipRole.BASE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "host");
        MissionSlotSpec duplicateHost = new MissionSlotSpec(
                MultiplayerRulesV1.HOST_SLOT_ID,
                Faction.ALLY.teamId(),
                ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "duplicate");
        MissionLaunchSpec invalid = new MissionLaunchSpec(
                CustomMissionCatalog.V1_DUEL_ID,
                CustomMissionCatalog.V1_DUEL_REVISION,
                1L,
                3600,
                2200,
                List.of(badHost, duplicateHost),
                List.of(badHost, duplicateHost),
                MultiplayerRulesV1.RULES_PROFILE_ID);

        MultiplayerMissionValidator.ValidationResult result =
                MultiplayerMissionValidator.validateForV1(invalid);
        String errors = String.join("\n", result.errors());

        assertFalse(result.accepted());
        assertTrue(errors.contains("Missing required player slot"));
    }

    @Test
    void rejectsDuplicateSlotsTeamConflictsAiAndEntityBudgetOverruns() {
        MissionSlotSpec host = new MissionSlotSpec(
                MultiplayerRulesV1.HOST_SLOT_ID,
                Faction.ALLY.teamId(),
                ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "host");
        MissionSlotSpec clientSameTeam = new MissionSlotSpec(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                Faction.ALLY.teamId(),
                ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "client");
        MissionSlotSpec duplicateClient = new MissionSlotSpec(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                Faction.ENEMY.teamId(),
                ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "duplicate-client");
        MissionSlotSpec aiEscort = new MissionSlotSpec(
                3,
                Faction.ALLY.teamId(),
                ShipRole.CIWS_CORVETTE,
                MissionSlotControlMode.AI_ONLY,
                false,
                "escort");
        MissionLaunchSpec invalid = new MissionLaunchSpec(
                CustomMissionCatalog.V1_DUEL_ID,
                CustomMissionCatalog.V1_DUEL_REVISION,
                1L,
                3600,
                2200,
                List.of(host, clientSameTeam, aiEscort),
                List.of(host, clientSameTeam, duplicateClient),
                MultiplayerRulesV1.RULES_PROFILE_ID);

        MultiplayerMissionValidator.ValidationResult result =
                MultiplayerMissionValidator.validateForV1(invalid);
        String errors = String.join("\n", result.errors());

        assertFalse(result.accepted());
        assertTrue(errors.contains("Too many entities"));
        assertTrue(errors.contains("Duplicate slot assignment"));
        assertTrue(errors.contains("Team conflict"));
        assertTrue(errors.contains("AI is unsupported"));
    }

    @Test
    void acceptsExplicitAiSupportProfileWithOneAiShipPerTeam() {
        MissionSlotSpec host = new MissionSlotSpec(
                MultiplayerRulesV1.HOST_SLOT_ID,
                Faction.ALLY.teamId(),
                ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "host");
        MissionSlotSpec client = new MissionSlotSpec(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                Faction.ENEMY.teamId(),
                ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "client");
        MissionSlotSpec hostSupport = new MissionSlotSpec(
                3,
                Faction.ALLY.teamId(),
                ShipRole.CIWS_CORVETTE,
                MissionSlotControlMode.AI_ONLY,
                false,
                "host-support");
        MissionSlotSpec clientSupport = new MissionSlotSpec(
                4,
                Faction.ENEMY.teamId(),
                ShipRole.CIWS_CORVETTE,
                MissionSlotControlMode.AI_ONLY,
                false,
                "client-support");
        MissionLaunchSpec spec = new MissionLaunchSpec(
                CustomMissionCatalog.V1_DUEL_ID,
                CustomMissionCatalog.V1_DUEL_REVISION,
                1L,
                3600,
                2200,
                List.of(host, client, hostSupport, clientSupport),
                List.of(host, client),
                MultiplayerRulesV1.AI_SUPPORT_RULES_PROFILE_ID);

        MultiplayerMissionValidator.ValidationResult result =
                MultiplayerMissionValidator.validateForV1(spec);
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerHostLaunchAdapter.toBattleSetup(spec, "Host", "Client");

        assertTrue(result.accepted(), String.join("\n", result.errors()));
        assertTrue(setup.aiShips());
    }

    @Test
    void hostAndClientAdaptersRejectInvalidLockedSpecs() {
        MissionLaunchSpec invalid = new MissionLaunchSpec(
                CustomMissionCatalog.V1_DUEL_ID,
                CustomMissionCatalog.V1_DUEL_REVISION,
                1L,
                1000,
                2200,
                duelSlots(ShipRole.FRIGATE, ShipRole.FRIGATE),
                duelSlots(ShipRole.FRIGATE, ShipRole.FRIGATE),
                MultiplayerRulesV1.RULES_PROFILE_ID);

        assertThrows(IllegalArgumentException.class,
                () -> MultiplayerHostLaunchAdapter.toBattleSetup(invalid, "Host", "Client"));
        assertThrows(IllegalArgumentException.class,
                () -> MultiplayerClientLaunchAdapter.prepare(
                        invalid,
                        CustomMissionCatalog.v1DuelDescriptor(),
                        CustomMissionCatalog.v1DuelTemplate(),
                        1L));
    }

    private static List<MissionSlotSpec> duelSlots(ShipRole hostHull, ShipRole clientHull) {
        return List.of(
                new MissionSlotSpec(
                        MultiplayerRulesV1.HOST_SLOT_ID,
                        Faction.ALLY.teamId(),
                        hostHull,
                        MissionSlotControlMode.PLAYER_REQUIRED,
                        true,
                        "host"),
                new MissionSlotSpec(
                        MultiplayerRulesV1.CLIENT_SLOT_ID,
                        Faction.ENEMY.teamId(),
                        clientHull,
                        MissionSlotControlMode.PLAYER_REQUIRED,
                        true,
                        "client"));
    }
}
