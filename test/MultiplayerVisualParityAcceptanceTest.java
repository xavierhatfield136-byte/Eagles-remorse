import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerVisualParityAcceptanceTest {

    @Test
    void simulatedEndpointsInterpolateRemoteShipAndAgreeOnHealthAndResult() {
        MultiplayerLoopbackDuelHarness harness = new MultiplayerLoopbackDuelHarness(
                MultiplayerRulesV1.defaultDuel(1700L, ShipRole.FRIGATE, ShipRole.FRIGATE));
        assertTrue(harness.connect().accepted());
        harness.startMatch(0L);

        MultiplayerPlayerSlotState host = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        MultiplayerClientPresentationV1 hostPresentation =
                new MultiplayerClientPresentationV1(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerClientPresentationV1 clientPresentation =
                new MultiplayerClientPresentationV1(MultiplayerRulesV1.CLIENT_SLOT_ID);
        List<Double> remoteClientXAsSeenByHost = new ArrayList<>();

        Ship clientShip = findShip(harness, client.controlledShipId);
        for (int i = 1; i <= 8; i++) {
            harness.sendClientInput(new MultiplayerCommandGate.PlayerInputFrame(
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    client.controlledShipId,
                    i,
                    i,
                    1.0f,
                    (i % 2 == 0) ? 0.25f : -0.25f,
                    Math.PI,
                    false,
                    false));
            harness.hostTick(GameContext.DT, i);
            MultiplayerBattleSnapshot snapshot = harness.clientView().latestSnapshot();
            hostPresentation.receiveSnapshot(snapshot);
            clientPresentation.receiveSnapshot(snapshot);
            MultiplayerClientPresentationV1.RenderState hostRender = hostPresentation.render();
            MultiplayerClientPresentationV1.RenderedShip remoteClient =
                    renderedShip(hostRender, client.controlledShipId);
            remoteClientXAsSeenByHost.add(remoteClient.x());
        }

        MultiplayerClientPresentationV1.RenderState hostRender = hostPresentation.render();
        MultiplayerClientPresentationV1.RenderState clientRender = clientPresentation.render();
        assertTrue(hostRender.debug().snapshotGapTicks() <= MultiplayerNetworkConditionsV1.ACCEPTABLE_SNAPSHOT_AGE_TICKS);
        assertTrue(clientRender.debug().snapshotGapTicks() <= MultiplayerNetworkConditionsV1.ACCEPTABLE_SNAPSHOT_AGE_TICKS);
        assertEquals(clientShip.hp, hostRender.localHud().hp());
        assertEquals(clientShip.hp, clientRender.localHud().hp());
        assertSmoothRemoteMovement(remoteClientXAsSeenByHost);

        Ship hostShip = findShip(harness, host.controlledShipId);
        hostShip.x = clientShip.x - 180.0;
        hostShip.y = clientShip.y;
        hostShip.shield = 0.0;
        for (int i = 9; i < 15 && !harness.hostScenario().lastResult().ended(); i++) {
            harness.sendClientInput(new MultiplayerCommandGate.PlayerInputFrame(
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    client.controlledShipId,
                    i,
                    i,
                    0.0f,
                    0.0f,
                    Math.PI,
                    true,
                    false));
            harness.hostTick(GameContext.DT, i);
            MultiplayerBattleSnapshot snapshot = harness.clientView().latestSnapshot();
            hostPresentation.receiveSnapshot(snapshot);
            clientPresentation.receiveSnapshot(snapshot);
            for (MultiplayerReplicationV1.AuthoritativeEvent event : harness.clientView().events()) {
                hostPresentation.receiveEvent(event);
                clientPresentation.receiveEvent(event);
            }
        }

        MultiplayerClientPresentationV1.RenderState hostFinal = hostPresentation.render();
        MultiplayerClientPresentationV1.RenderState clientFinal = clientPresentation.render();
        assertTrue(hostFinal.localHud().matchEnded());
        assertTrue(clientFinal.localHud().matchEnded());
        assertEquals(hostFinal.localHud().matchResult(), clientFinal.localHud().matchResult());
    }

    private static Ship findShip(MultiplayerLoopbackDuelHarness harness, int shipId) {
        for (Ship ship : harness.hostScenario().runtime().context().ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        throw new AssertionError("missing ship " + shipId);
    }

    private static MultiplayerClientPresentationV1.RenderedShip renderedShip(
            MultiplayerClientPresentationV1.RenderState state, int shipId) {
        for (MultiplayerClientPresentationV1.RenderedShip ship : state.ships()) {
            if (ship.shipId() == shipId) return ship;
        }
        throw new AssertionError("missing rendered ship " + shipId);
    }

    private static void assertSmoothRemoteMovement(List<Double> positions) {
        assertTrue(positions.size() >= 4, "need several rendered positions to judge smoothness");
        double totalMovement = 0.0;
        double largestStep = 0.0;
        for (int i = 1; i < positions.size(); i++) {
            double step = Math.abs(positions.get(i) - positions.get(i - 1));
            totalMovement += step;
            largestStep = Math.max(largestStep, step);
        }
        assertTrue(totalMovement > 0.1, "remote ship should visibly move across rendered snapshots");
        assertTrue(largestStep < 40.0, "remote interpolation should avoid large position jumps");
    }
}
