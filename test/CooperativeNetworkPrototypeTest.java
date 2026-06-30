import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooperativeNetworkPrototypeTest {
    @Test
    void twoRoleHostAuthoritativeScenarioHandlesLatencyPauseDisconnectAndReconnect() {
        CooperativeNetworkPrototype.Session session = CooperativeNetworkPrototype.host("EAGLE-7", "host");
        assertTrue(CooperativeNetworkPrototype.join(session, "EAGLE-7", "client", 180, 0));
        assertTrue(CooperativeNetworkPrototype.assignRole(session, "host", CooperativeCommandSystem.Role.CAPTAIN));
        assertTrue(CooperativeNetworkPrototype.assignRole(session, "client", CooperativeCommandSystem.Role.HELM));
        assertTrue(CooperativeNetworkPrototype.setReady(session, "host", true));
        assertTrue(CooperativeNetworkPrototype.setReady(session, "client", true));
        assertTrue(CooperativeNetworkPrototype.launch(session, "host"));

        assertTrue(CooperativeNetworkPrototype.submit(session, CooperativeNetworkPrototype.PacketType.COMMAND,
                "client", CooperativeCommandSystem.Role.HELM, 1, "course", "frontier"));
        CooperativeNetworkPrototype.tick(session, 100);
        assertFalse(session.authoritative.acceptedCommands.containsKey("HELM:course"));
        CooperativeNetworkPrototype.tick(session, 100);
        assertEquals("frontier", session.authoritative.acceptedCommands.get("HELM:course"));
        assertTrue(CooperativeNetworkPrototype.checksumsMatch(session));

        CooperativeNetworkPrototype.submit(session, CooperativeNetworkPrototype.PacketType.PAUSE,
                "host", CooperativeCommandSystem.Role.CAPTAIN, 2, "pause", "true");
        CooperativeNetworkPrototype.tick(session, 1);
        assertEquals(CooperativeNetworkPrototype.Phase.PAUSED, session.phase);
        CooperativeNetworkPrototype.disconnect(session, "client");
        assertTrue(session.authoritative.seats.get(CooperativeCommandSystem.Role.HELM).automated);
        assertTrue(CooperativeNetworkPrototype.reconnect(session, "client"));
        assertFalse(session.authoritative.seats.get(CooperativeCommandSystem.Role.HELM).automated);
    }

    @Test
    void deterministicLossDiagnosticsAndHostExitAreExplicit() {
        CooperativeNetworkPrototype.Session session = CooperativeNetworkPrototype.host("TEST", "host");
        assertTrue(CooperativeNetworkPrototype.join(session, "TEST", "lossy", 500, 100));
        CooperativeNetworkPrototype.assignRole(session, "lossy", CooperativeCommandSystem.Role.TACTICAL);
        CooperativeNetworkPrototype.submit(session, CooperativeNetworkPrototype.PacketType.COMMAND,
                "lossy", CooperativeCommandSystem.Role.TACTICAL, 1, "target", "enemy");
        assertEquals(1, session.droppedPackets);
        assertFalse(session.diagnostics.isEmpty());
        CooperativeNetworkPrototype.disconnect(session, "host");
        assertEquals(CooperativeNetworkPrototype.Phase.ENDED, session.phase);
        assertTrue(session.diagnostics.stream().anyMatch(line -> line.contains("HOST_EXIT")));
    }
}

