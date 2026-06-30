import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagshipOperationsSystemTest {
    @Test
    void schematicUsesLiveRoomsAndAutomationContainsEmergency() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 88L, false));
        SpawnSystem.initWorld(ctx);
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        FlagshipOperationsSystem.syncFromShip(state, ctx.player);
        assertFalse(state.compartments.isEmpty());
        String roomId = state.compartments.keySet().iterator().next();
        FlagshipOperationsSystem.setEmergency(state, roomId, 1.5, true, true);

        FlagshipOperationsSystem.update(state, 2.0);

        assertTrue(state.teams.values().stream().anyMatch(team -> roomId.equals(team.assignedCompartmentId)));
        assertTrue(state.compartments.get(roomId).fire < 1.5);
        assertTrue(FlagshipOperationsSystem.schematicLines(state).stream().anyMatch(line -> line.contains("FLAGSHIP")));
    }

    @Test
    void schematicTeamsHazardsAndAutomationRoundTrip() {
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        state.compartments.put("ENGINEERING", new FlagshipOperationsSystem.Compartment("ENGINEERING", "Engineering"));
        FlagshipOperationsSystem.setEmergency(state, "ENGINEERING", 0.9, true, true);
        assertTrue(FlagshipOperationsSystem.assignTeam(state, "dc-1", "ENGINEERING",
                FlagshipOperationsSystem.TeamOrder.CONTAIN_FIRE));
        state.automation = FlagshipOperationsSystem.AutomationMode.ADVISORY;

        FlagshipOperationsSystem.State restored =
                FlagshipOperationsSystem.restore(FlagshipOperationsSystem.serialize(state));

        assertEquals(FlagshipOperationsSystem.AutomationMode.ADVISORY, restored.automation);
        assertEquals(0.9, restored.compartments.get("ENGINEERING").fire, 0.0001);
        assertTrue(restored.compartments.get("ENGINEERING").decompressed);
        assertEquals("ENGINEERING", restored.teams.get("dc-1").assignedCompartmentId);
    }

    @Test
    void powerRoutingEmergencyRiskAndHazardSpreadAreDeterministicAndPersisted() {
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        FlagshipOperationsSystem.Compartment reactor =
                new FlagshipOperationsSystem.Compartment("REACTOR", "Reactor");
        FlagshipOperationsSystem.Compartment engineering =
                new FlagshipOperationsSystem.Compartment("ENGINEERING", "Engineering");
        reactor.systemType = FlagshipOperationsSystem.SystemType.REACTOR;
        engineering.systemType = FlagshipOperationsSystem.SystemType.ENGINEERING;
        reactor.powerDemand = 0;
        engineering.powerDemand = 80;
        state.compartments.put(reactor.id, reactor);
        state.compartments.put(engineering.id, engineering);
        assertTrue(FlagshipOperationsSystem.connect(state, reactor.id, engineering.id));
        state.powerGeneration = 30;
        state.emergencyReserves = 25;
        FlagshipOperationsSystem.routePower(state);
        assertTrue(engineering.disrupted);
        assertTrue(engineering.offlineReason.contains("Underpowered"));
        assertTrue(FlagshipOperationsSystem.emergencyRedistribute(state, engineering.id, 10));
        assertTrue(engineering.electricalFault > 0.0, "emergency power must carry explicit overload risk");
        assertFalse(FlagshipOperationsSystem.emergencyRedistribute(state, engineering.id, 5),
                "emergency redistribution must respect its cooldown");

        FlagshipOperationsSystem.setEmergency(state, reactor.id, 1.4, true, true);
        FlagshipOperationsSystem.setHazards(state, reactor.id, 0.8, 0.7, 0.6);
        FlagshipOperationsSystem.update(state, 2.0);
        assertTrue(engineering.fire > 0.0, "unsealed graph connections must propagate hazards");
        assertTrue(FlagshipOperationsSystem.setCompartmentSafety(state, engineering.id, true, true, false));

        FlagshipOperationsSystem.State restored =
                FlagshipOperationsSystem.restore(FlagshipOperationsSystem.serialize(state));
        assertEquals(30, restored.powerGeneration);
        assertEquals(state.repairParts, restored.repairParts);
        assertEquals(state.emergencyReserves, restored.emergencyReserves);
        assertTrue(restored.compartments.get(engineering.id).sealed);
        assertTrue(restored.compartments.get(engineering.id).connections.contains(reactor.id));
        assertTrue(restored.compartments.get(reactor.id).coolantLeak > 0.0);
    }
}
