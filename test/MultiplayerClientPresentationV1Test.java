import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerClientPresentationV1Test {

    @Test
    void clientInterpolatesBetweenHostSnapshotsAndReportsDebugMetrics() {
        MultiplayerClientPresentationV1 presentation =
                new MultiplayerClientPresentationV1(MultiplayerRulesV1.CLIENT_SLOT_ID);

        presentation.receiveSnapshot(snapshot(30L, 100.0, 500.0, 100, 100));
        presentation.receiveSnapshot(snapshot(33L, 160.0, 440.0, 95, 90));

        MultiplayerClientPresentationV1.RenderState render = presentation.render();
        MultiplayerClientPresentationV1.RenderedShip hostShip = render.ships().get(0);
        MultiplayerClientPresentationV1.RenderedShip clientShip = render.ships().get(1);

        assertEquals(100.0, hostShip.x(), 1e-9,
                "client renders behind the newest snapshot, using the older sample when delay equals snapshot gap");
        assertEquals(500.0, clientShip.x(), 1e-9);
        assertEquals(33L, render.debug().latestReceivedHostTick());
        assertEquals(30L, render.debug().renderedHostTick());
        assertEquals(MultiplayerClientPresentationV1.INTERPOLATION_DELAY_TICKS,
                render.debug().interpolationDelayTicks());
        assertEquals(3L, render.debug().snapshotGapTicks());
        assertEquals(0L, render.debug().extrapolationTicks());
        assertEquals(0.0, render.debug().correctionMagnitude(), 1e-9);
    }

    @Test
    void localHudAndRemoteMarkersComeFromHostSnapshotsAndEvents() {
        MultiplayerClientPresentationV1 presentation =
                new MultiplayerClientPresentationV1(MultiplayerRulesV1.CLIENT_SLOT_ID);
        presentation.receiveSnapshot(snapshot(33L, 120.0, 480.0, 80, 77));

        MultiplayerReplicationV1.AuthoritativeEvent victory =
                new MultiplayerReplicationV1.AuthoritativeEvent(
                        MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                        null, 4L, 33L, 0, 0, "Elimination victory");
        presentation.receiveEvent(victory);

        MultiplayerClientPresentationV1.RenderState render = presentation.render();

        assertEquals(MultiplayerRulesV1.CLIENT_SLOT_ID, render.localHud().slotId());
        assertEquals(77, render.localHud().hp());
        assertEquals(Faction.ENEMY.teamId(), render.localHud().teamId());
        assertTrue(render.localHud().matchEnded());
        assertEquals("Elimination victory", render.localHud().matchResult());
        assertEquals(1, render.remoteMarkers().size());
        assertEquals("Host", render.remoteMarkers().get(0).displayName());
        assertTrue(render.completeVisibility());
    }

    @Test
    void minimalPresentationBufferKeepsNewestOrderedSnapshotsOnly() {
        MultiplayerClientPresentationV1 presentation =
                new MultiplayerClientPresentationV1(MultiplayerRulesV1.CLIENT_SLOT_ID);

        presentation.receiveSnapshot(snapshot(20L, 100.0, 500.0, 100, 100));
        presentation.receiveSnapshot(snapshot(24L, 120.0, 480.0, 95, 90));
        presentation.receiveSnapshot(snapshot(22L, 110.0, 490.0, 98, 95));

        assertEquals(MultiplayerClientPresentationV1.PRESENTATION_BUFFER_CAPACITY,
                presentation.bufferedSnapshotCountForTests());
        assertEquals(List.of(22L, 24L), presentation.bufferedSnapshotTicksForTests());
    }

    @Test
    void clientCanOnlyRunPresentationWork() {
        assertTrue(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.INTERPOLATION));
        assertTrue(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.LOCAL_CAMERA));
        assertTrue(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.COSMETIC_PARTICLES));
        assertTrue(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.SOUND));
        assertTrue(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.TEMPORARY_PREDICTED_MUZZLE_EFFECTS));

        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_AI));
        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_DAMAGE));
        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_DEATH));
        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_OBJECTIVE_COMPLETION));
        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_SHIP_SPAWNING));
        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_TARGET_VALIDITY));
        assertFalse(MultiplayerClientPresentationV1.canClientRun(
                MultiplayerClientPresentationV1.ClientCapability.AUTHORITATIVE_VICTORY_EVALUATION));
    }

    private static MultiplayerBattleSnapshot snapshot(long hostTick,
                                                       double hostX,
                                                       double clientX,
                                                       int hostHp,
                                                       int clientHp) {
        return new MultiplayerBattleSnapshot(hostTick,
                List.of(
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                101, ShipRole.FRIGATE, Faction.ALLY,
                                hostX, 200.0, 1.0, 0.0, 0.0, hostHp, 20.0, hostHp > 0),
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                202, ShipRole.FRIGATE, Faction.ENEMY,
                                clientX, 200.0, -1.0, 0.0, Math.PI, clientHp, 18.0, clientHp > 0)),
                List.of(
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(), 101,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.LOCAL, "Host"),
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(), 202,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.CONNECTED, "Client")));
    }
}
