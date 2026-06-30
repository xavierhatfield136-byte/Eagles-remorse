import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooperativeCommandSystemTest {
    @Test
    void roleAuthorityIsDeterministicAndDisconnectFallsBackToAutomation() {
        CooperativeCommandSystem.State state = CooperativeCommandSystem.bootstrap();
        assertTrue(CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.HELM, "player-a"));
        assertTrue(CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.ENGINEERING, "player-b"));

        assertTrue(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.HELM,
                "player-a", 1, "course", "frontier").accepted());
        assertFalse(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.HELM,
                "player-a", 1, "course", "sol").accepted());
        assertFalse(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.HELM,
                "player-b", 2, "course", "sol").accepted());

        CooperativeCommandSystem.disconnect(state, "player-a");
        assertTrue(state.seats.get(CooperativeCommandSystem.Role.HELM).automated);
        assertFalse(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.HELM,
                "player-a", 3, "course", "sol").accepted());
        assertTrue(CooperativeCommandSystem.reconnect(state, "player-a"));
        assertTrue(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.HELM,
                "player-a", 3, "course", "sol").accepted());
    }

    @Test
    void seatOwnershipAndSequencesPersist() {
        CooperativeCommandSystem.State state = CooperativeCommandSystem.bootstrap();
        CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.TACTICAL, "player-c");
        CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.TACTICAL,
                "player-c", 7, "target", "enemy-12");

        CooperativeCommandSystem.State restored =
                CooperativeCommandSystem.restore(CooperativeCommandSystem.serialize(state));
        assertEquals("player-c", restored.seats.get(CooperativeCommandSystem.Role.TACTICAL).playerId);
        assertEquals(7, restored.seats.get(CooperativeCommandSystem.Role.TACTICAL).lastAcceptedSequence);
        assertTrue(CooperativeCommandSystem.roleLines(restored).stream().anyMatch(line -> line.contains("TACTICAL")));
        assertEquals(state.acceptedCommands, restored.acceptedCommands);
        assertEquals(CooperativeCommandSystem.diagnosticChecksum(state),
                CooperativeCommandSystem.diagnosticChecksum(restored));
    }

    @Test
    void authorityCaptainOverrideMessagingAndPayloadValidationAreExplicit() {
        CooperativeCommandSystem.State state = CooperativeCommandSystem.bootstrap();
        assertTrue(CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.CAPTAIN, "captain"));
        assertTrue(CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.ENGINEERING, "engineer"));
        assertFalse(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.ENGINEERING,
                "engineer", 1, "target", "enemy" ).accepted(),
                "engineering must not write tactical state");
        assertFalse(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.ENGINEERING,
                "engineer", 1, "", "bad" ).accepted());
        assertTrue(CooperativeCommandSystem.submit(state, CooperativeCommandSystem.Role.ENGINEERING,
                "engineer", 1, "power", "shields:70" ).accepted());
        assertTrue(CooperativeCommandSystem.captainOverride(state, "captain", 1,
                CooperativeCommandSystem.Role.HELM, "course", "frontier").accepted());
        assertTrue(CooperativeCommandSystem.postMessage(state, "engineer", "WARN", "Reactor load high"));
        assertFalse(state.sharedMessages.isEmpty());
        assertTrue(CooperativeCommandSystem.roleLines(state).stream()
                .anyMatch(line -> line.contains("AUTOMATED") && line.contains("responsibilities") == false));
    }
}
