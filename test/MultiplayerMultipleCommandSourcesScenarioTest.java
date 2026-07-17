import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerMultipleCommandSourcesScenarioTest {

    @Test
    void assignsTwoOpposingPlayerOwnedShips() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();

        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);

        assertTrue(host.controlledShipId > 0);
        assertTrue(client.controlledShipId > 0);
        assertNotEquals(host.controlledShipId, client.controlledShipId);
        assertNotEquals(host.teamId, client.teamId);
    }

    @Test
    void twoCommandSourcesMoveOnlyTheirOwnedShips() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();
        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        Ship hostShip = findShip(scenario, host.controlledShipId);
        Ship clientShip = findShip(scenario, client.controlledShipId);
        double hostX = hostShip.x;
        double clientX = clientShip.x;

        scenario.enqueue(new ScriptedMultiplayerCommandSource(
                MultiplayerRulesV1.HOST_SLOT_ID, host.controlledShipId, 1.0f, 0.0f, false), 1L);
        scenario.enqueue(new ScriptedMultiplayerCommandSource(
                MultiplayerRulesV1.CLIENT_SLOT_ID, client.controlledShipId, 1.0f, 0.0f, false), 1L);
        scenario.tick(GameContext.DT, 1L);

        assertTrue(hostShip.x > hostX, "host ship should move under host source input");
        assertTrue(clientShip.x < clientX, "client ship starts facing left and should move under client source input");
    }

    @Test
    void bothSlotsCanThrustRotateAndAimThroughAuthoritativeInputPath() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();
        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        Ship hostShip = findShip(scenario, host.controlledShipId);
        Ship clientShip = findShip(scenario, client.controlledShipId);
        double hostX = hostShip.x;
        double hostAngle = hostShip.angle;
        double clientX = clientShip.x;
        double clientAngle = clientShip.angle;

        scenario.enqueueInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID, host.controlledShipId, 1L, 1L,
                1.0f, 1.0f, 0.75, false, false));
        scenario.enqueueInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID, client.controlledShipId, 1L, 1L,
                1.0f, -1.0f, 2.40, false, false));
        scenario.tick(GameContext.DT, 1L);

        assertTrue(hostShip.x > hostX);
        assertTrue(clientShip.x < clientX);
        assertTrue(hostShip.angle > hostAngle);
        assertTrue(clientShip.angle < clientAngle);
        assertEquals(0.75, scenario.lastAcceptedAimAngle(MultiplayerRulesV1.HOST_SLOT_ID), 1e-9);
        assertEquals(2.40, scenario.lastAcceptedAimAngle(MultiplayerRulesV1.CLIENT_SLOT_ID), 1e-9);
    }

    @Test
    void rejectedUnownedInputDoesNotMoveTargetShip() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();
        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        Ship clientShip = findShip(scenario, client.controlledShipId);
        double clientX = clientShip.x;

        scenario.enqueue(new ScriptedMultiplayerCommandSource(
                MultiplayerRulesV1.HOST_SLOT_ID, client.controlledShipId, 1.0f, 0.0f, false), 1L);
        scenario.tick(GameContext.DT, 1L);

        assertEquals(clientX, clientShip.x, 1e-9,
                "slot ownership validation should prevent host source from moving the client ship");
        assertTrue(host.controlledShipId > 0);
    }

    @Test
    void playerOwnedShipsAreExcludedFromAiControl() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();
        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        Ship hostShip = findShip(scenario, host.controlledShipId);
        FleetShip neutralAi = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 100.0, 100.0);

        assertFalse(scenario.shouldRunAiFor(hostShip));
        assertTrue(scenario.shouldRunAiFor(neutralAi));
    }

    @Test
    void hostSideFireDamageDeathAndVictoryResolveWithoutNetworkTransport() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();
        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        Ship hostShip = findShip(scenario, host.controlledShipId);
        Ship clientShip = findShip(scenario, client.controlledShipId);
        clientShip.x = hostShip.x + 180.0;
        clientShip.y = hostShip.y;
        clientShip.shield = 0.0;
        clientShip.scaleCurrentHullIntegrity(0.02);

        ScriptedMultiplayerCommandSource firing = new ScriptedMultiplayerCommandSource(
                MultiplayerRulesV1.HOST_SLOT_ID, host.controlledShipId, 0.0f, 0.0f, true);

        for (int i = 0; i < 3 && !scenario.lastResult().ended(); i++) {
            scenario.enqueue(firing, i + 1L);
            scenario.tick(GameContext.DT, i + 1L);
        }

        assertTrue(clientShip.dying || !clientShip.alive || clientShip.hp <= 0);
        assertTrue(scenario.lastResult().ended());
        assertEquals(host.teamId, scenario.lastResult().winningTeamId());
    }

    @Test
    void clientSlotCanFireThroughAuthoritativeHostPath() {
        MultiplayerMultipleCommandSourcesScenario scenario = scenario();
        MultiplayerPlayerSlotState host = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        Ship hostShip = findShip(scenario, host.controlledShipId);
        Ship clientShip = findShip(scenario, client.controlledShipId);
        hostShip.x = clientShip.x - 180.0;
        hostShip.y = clientShip.y;
        hostShip.shield = 0.0;

        ScriptedMultiplayerCommandSource firing = new ScriptedMultiplayerCommandSource(
                MultiplayerRulesV1.CLIENT_SLOT_ID, client.controlledShipId, 0.0f, 0.0f, true);

        for (int i = 0; i < 3 && !scenario.lastResult().ended(); i++) {
            scenario.enqueue(firing, i + 1L);
            scenario.tick(GameContext.DT, i + 1L);
        }

        assertTrue(hostShip.dying || !hostShip.alive || hostShip.hp <= 0);
        assertTrue(scenario.lastResult().ended());
        assertEquals(client.teamId, scenario.lastResult().winningTeamId());
    }

    private static MultiplayerMultipleCommandSourcesScenario scenario() {
        return new MultiplayerMultipleCommandSourcesScenario(
                MultiplayerRulesV1.defaultDuel(710L, ShipRole.FRIGATE, ShipRole.FRIGATE));
    }

    private static Ship findShip(MultiplayerMultipleCommandSourcesScenario scenario, int shipId) {
        for (Ship ship : scenario.runtime().context().ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        throw new AssertionError("missing ship " + shipId);
    }
}
