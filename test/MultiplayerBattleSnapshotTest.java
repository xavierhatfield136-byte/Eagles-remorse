import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerBattleSnapshotTest {

    @Test
    void replaceableStateSnapshotContractCoversMovementDurabilityLifeAndObjectiveSummary() {
        Set<MultiplayerBattleSnapshot.ReplaceableStateField> fields =
                MultiplayerBattleSnapshot.replaceableStateFields();

        assertTrue(fields.contains(MultiplayerBattleSnapshot.ReplaceableStateField.POSITION));
        assertTrue(fields.contains(MultiplayerBattleSnapshot.ReplaceableStateField.VELOCITY));
        assertTrue(fields.contains(MultiplayerBattleSnapshot.ReplaceableStateField.HEALTH));
        assertTrue(fields.contains(MultiplayerBattleSnapshot.ReplaceableStateField.SHIELD));
        assertTrue(fields.contains(MultiplayerBattleSnapshot.ReplaceableStateField.ALIVE_DEAD));
        assertTrue(fields.contains(MultiplayerBattleSnapshot.ReplaceableStateField.OBJECTIVE_SUMMARY));

        MultiplayerBattleSnapshot snapshot = snapshotWithObjective();
        MultiplayerBattleSnapshot.ShipSnapshot ship = snapshot.ships().get(0);

        assertEquals(100.0, ship.x(), 1e-9);
        assertEquals(200.0, ship.y(), 1e-9);
        assertEquals(1.5, ship.vx(), 1e-9);
        assertEquals(-2.5, ship.vy(), 1e-9);
        assertEquals(75, ship.hp());
        assertEquals(12.0, ship.shield(), 1e-9);
        assertTrue(ship.alive());
        assertEquals("elimination", snapshot.objectiveSummary().objectiveTypeId());
    }

    @Test
    void snapshotSerializationRoundTripsObjectiveSummaryAndStillReadsSnap2() {
        MultiplayerBattleSnapshot snapshot = snapshotWithObjective();

        byte[] encoded = MultiplayerSerializationV1.encodeSnapshot(snapshot);
        MultiplayerBattleSnapshot decoded = MultiplayerSerializationV1.decodeSnapshot(encoded);

        assertEquals(snapshot, decoded);
        assertTrue(new String(encoded, StandardCharsets.UTF_8).startsWith("SNAP3|"));

        String legacySnap2 = "SNAP2|9|4|0|0";
        MultiplayerBattleSnapshot legacy =
                MultiplayerSerializationV1.decodeSnapshot(legacySnap2.getBytes(StandardCharsets.UTF_8));

        assertEquals(9L, legacy.hostTick());
        assertEquals(4L, legacy.lastProcessedInputSequence());
        assertEquals(MultiplayerBattleSnapshot.ObjectiveSummarySnapshot.none(), legacy.objectiveSummary());
    }

    @Test
    void objectiveStateSnapshotNormalizesAndProjectsToSummary() {
        MultiplayerBattleSnapshot.ObjectiveStateSnapshot state =
                new MultiplayerBattleSnapshot.ObjectiveStateSnapshot(
                        "capture",
                        true,
                        false,
                        Faction.ALLY.teamId(),
                        2.5,
                        -4,
                        3,
                        Double.NaN,
                        -9L,
                        "Holding relay");

        assertEquals(1.0, state.progress(), 1e-9);
        assertEquals(0, state.hostTeamScore());
        assertEquals(3, state.clientTeamScore());
        assertEquals(0.0, state.remainingSeconds(), 1e-9);
        assertEquals(0L, state.revision());
        assertEquals(
                new MultiplayerBattleSnapshot.ObjectiveSummarySnapshot(
                        "capture", true, false, Faction.ALLY.teamId(), 1.0, "Holding relay"),
                state.toSummary());
        assertEquals(MultiplayerBattleSnapshot.ObjectiveSummarySnapshot.none(),
                MultiplayerBattleSnapshot.ObjectiveStateSnapshot.none().toSummary());
    }

    private static MultiplayerBattleSnapshot snapshotWithObjective() {
        return new MultiplayerBattleSnapshot(
                44L,
                8L,
                List.of(new MultiplayerBattleSnapshot.ShipSnapshot(
                        101, ShipRole.FRIGATE, Faction.ALLY,
                        100.0, 200.0, 1.5, -2.5, 0.5,
                        75, 12.0, true)),
                List.of(new MultiplayerBattleSnapshot.SlotSnapshot(
                        MultiplayerRulesV1.HOST_SLOT_ID,
                        Faction.ALLY.teamId(),
                        101,
                        MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                        MultiplayerRulesV1.ConnectionState.LOCAL,
                        "Host")),
                new MultiplayerBattleSnapshot.ObjectiveSummarySnapshot(
                        "elimination", true, false, Faction.ALLY.teamId(), 0.5,
                        "Enemy hull damaged"));
    }
}
