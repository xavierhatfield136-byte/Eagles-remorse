import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLoopbackDuelHarnessTest {

    @Test
    void loopbackConnectStartMoveFireDestroyVictoryAndReturnToMenu() {
        MultiplayerLoopbackDuelHarness harness = harness();
        assertTrue(harness.connect().accepted());
        harness.startMatch(0L);

        MultiplayerPlayerSlotState client = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        MultiplayerPlayerSlotState host = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.HOST_SLOT_ID);
        Ship clientShip = findShip(harness, client.controlledShipId);
        Ship hostShip = findShip(harness, host.controlledShipId);
        double clientX = clientShip.x;

        harness.sendClientInput(input(client.controlledShipId, 1L, 1L, 1.0f, false));
        harness.hostTick(GameContext.DT, 1L);

        assertTrue(clientShip.x < clientX, "client input should move its owned ship on the host");
        assertNotNull(harness.clientView().latestSnapshot());
        assertEquals(clientShip.hp, harness.clientView().latestHpForShip(client.controlledShipId));
        assertEquals(1L, harness.clientView().latestInputAck().inputSequence());

        hostShip.x = clientShip.x - 180.0;
        hostShip.y = clientShip.y;
        hostShip.shield = 0.0;

        for (int i = 0; i < 6 && !harness.hostScenario().lastResult().ended(); i++) {
            long tick = 2L + i;
            harness.sendClientInput(input(client.controlledShipId, tick, tick, 0.0f, true));
            harness.hostTick(GameContext.DT, tick);
        }

        assertTrue(harness.hostScenario().lastResult().ended());
        assertEquals(client.teamId, harness.hostScenario().lastResult().winningTeamId());
        assertTrue(harness.clientView().matchEnded());
        assertTrue(hasEvent(harness, MultiplayerReplicationV1.EventType.WEAPON_FIRED));
        assertTrue(hasEvent(harness, MultiplayerReplicationV1.EventType.HIT_CONFIRMED));
        assertTrue(hasEvent(harness, MultiplayerReplicationV1.EventType.SHIP_DESTROYED));
        assertTrue(hasEvent(harness, MultiplayerReplicationV1.EventType.VICTORY_DECLARED));
        assertEquals(hostShip.hp, harness.clientView().latestHpForShip(host.controlledShipId));

        harness.exitToMenu();

        assertTrue(harness.clientView().returnedToMenu());
        assertTrue(harness.transport().closed());
    }

    @Test
    void loopbackHeartbeatTimeoutIsHostOwned() {
        MultiplayerLoopbackDuelHarness harness = harness();
        assertTrue(harness.connect().accepted());

        harness.clientHeartbeat(1L);

        assertFalse(harness.clientTimedOut(1L + MultiplayerLoopbackTransport.HEARTBEAT_TIMEOUT_TICKS));
        assertTrue(harness.clientTimedOut(2L + MultiplayerLoopbackTransport.HEARTBEAT_TIMEOUT_TICKS));
    }

    @Test
    void loopbackRejectsIncompatibleClientBeforeMatchStart() {
        MultiplayerLoopbackTransport transport = new MultiplayerLoopbackTransport();
        MultiplayerProtocolV1.CompatibilityFingerprint host = MultiplayerProtocolV1.localFingerprint();
        MultiplayerProtocolV1.CompatibilityFingerprint client =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION + 1,
                        host.gameBuild(),
                        host.manifest());

        MultiplayerProtocolV1.CompatibilityResult result = transport.connect(host, client);

        assertFalse(result.accepted());
        assertFalse(transport.connected());
        assertTrue(result.reason().contains("Protocol mismatch"));
    }

    private static MultiplayerLoopbackDuelHarness harness() {
        return new MultiplayerLoopbackDuelHarness(
                MultiplayerRulesV1.defaultDuel(910L, ShipRole.FRIGATE, ShipRole.FRIGATE));
    }

    private static MultiplayerCommandGate.PlayerInputFrame input(int shipId, long sequence, long clientTick,
                                                                 float thrust, boolean primaryHeld) {
        return new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID, shipId, sequence, clientTick,
                thrust, 0.0f, Math.PI, primaryHeld, false);
    }

    private static Ship findShip(MultiplayerLoopbackDuelHarness harness, int shipId) {
        for (Ship ship : harness.hostScenario().runtime().context().ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        throw new AssertionError("missing ship " + shipId);
    }

    private static boolean hasEvent(MultiplayerLoopbackDuelHarness harness,
                                    MultiplayerReplicationV1.EventType type) {
        return harness.clientView().events().stream().anyMatch(e -> e.type() == type);
    }
}
