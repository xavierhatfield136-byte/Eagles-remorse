import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooperativeProductionReadinessTest {
    @Test
    void everyPlayerCountAndRoleCombinationAssignsDeterministically() {
        CooperativeCommandSystem.Role[] roles = CooperativeCommandSystem.Role.values();
        for (int mask = 1; mask < (1 << roles.length); mask++) {
            List<CooperativeCommandSystem.Role> selected = new ArrayList<>();
            for (int i = 0; i < roles.length; i++) if ((mask & (1 << i)) != 0) selected.add(roles[i]);
            CooperativeNetworkPrototype.Session session = CooperativeNetworkPrototype.host("ROLES", "p0");
            for (int i = 1; i < selected.size(); i++) {
                assertTrue(CooperativeNetworkPrototype.join(session, "ROLES", "p" + i, i * 15, 0));
            }
            for (int i = 0; i < selected.size(); i++) {
                assertTrue(CooperativeNetworkPrototype.assignRole(session, "p" + i, selected.get(i)));
            }
            assertEquals(selected.size(), session.authoritative.seats.values().stream()
                    .filter(seat -> !seat.automated).count());
        }
    }

    @Test
    void eachSinglePlayerRoleHasAUsefulPracticeLoopAndAuthority() {
        for (CooperativeCommandSystem.Role role : CooperativeCommandSystem.Role.values()) {
            CooperativeCommandSystem.State state = CooperativeCommandSystem.bootstrap();
            assertTrue(CooperativeCommandSystem.assign(state, role, "solo"));
            List<String> practice = CooperativeCommandSystem.practiceScenario(role);
            assertTrue(practice.size() >= 3);
            String command = switch (role) {
                case CAPTAIN -> "priority"; case HELM -> "course"; case TACTICAL -> "target";
                case ENGINEERING -> "power"; case SCIENCE -> "scan"; case STRATEGIC_COMMAND -> "operation";
            };
            assertTrue(CooperativeCommandSystem.submit(state, role, "solo", 1, command, practice.get(0)).accepted());
        }
    }

    @Test
    void captainDelegationOverridesVotingAndAccessibilityAreExplicit() {
        CooperativeCommandSystem.State state = CooperativeCommandSystem.bootstrap();
        CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.CAPTAIN, "captain");
        CooperativeCommandSystem.assign(state, CooperativeCommandSystem.Role.HELM, "helm");
        state.votePolicy = CooperativeCommandSystem.VotePolicy.MAJORITY;
        assertTrue(CooperativeCommandSystem.requestVote(state, "captain", "commit reserve fleet"));
        assertTrue(CooperativeCommandSystem.castVote(state, "captain", true));
        assertTrue(CooperativeCommandSystem.castVote(state, "helm", true));
        assertTrue(CooperativeCommandSystem.resolveVote(state));
        assertTrue(CooperativeCommandSystem.captainOverride(state, "captain", 1,
                CooperativeCommandSystem.Role.HELM, "course", "relief route").accepted());

        CooperativeNetworkPrototype.Session session = CooperativeNetworkPrototype.host("ACCESS", "captain");
        CooperativeNetworkPrototype.join(session, "ACCESS", "helm", 50, 0);
        assertTrue(CooperativeNetworkPrototype.configureAccessibility(session, "captain", 1.5, true, true, true));
        assertTrue(CooperativeNetworkPrototype.configureAccessibility(session, "helm", 0.9, false, false, true));
        assertEquals(1.5, session.clients.get("captain").uiScale, 0.0001);
        assertEquals(0.9, session.clients.get("helm").uiScale, 0.0001);
        assertTrue(session.clients.get("captain").highContrast);
        assertFalse(session.clients.get("helm").highContrast);
    }

    @Test
    void tacticalStrategicOrdersUiAndTimeFrameSaveLoadAcrossClients() {
        CooperativeNetworkPrototype.Session session = CooperativeNetworkPrototype.host("SAVE", "host");
        CooperativeNetworkPrototype.join(session, "SAVE", "client", 90, 0);
        CooperativeNetworkPrototype.assignRole(session, "host", CooperativeCommandSystem.Role.CAPTAIN);
        CooperativeNetworkPrototype.assignRole(session, "client", CooperativeCommandSystem.Role.STRATEGIC_COMMAND);
        CooperativeNetworkPrototype.configureAccessibility(session, "client", 1.3, true, true, true);
        CooperativeNetworkPrototype.setReady(session, "host", true);
        CooperativeNetworkPrototype.setReady(session, "client", true);
        CooperativeNetworkPrototype.launch(session, "host");
        assertTrue(CooperativeNetworkPrototype.publishAuthoritativeFrame(session, "host",
                "ships=41;projectiles=18", "territories=27;operations=6",
                "tab=FLEET;selection=frontier", 0.25));
        String checkpoint = CooperativeNetworkPrototype.checkpoint(session);
        CooperativeNetworkPrototype.Session restored = CooperativeNetworkPrototype.restoreCheckpoint(checkpoint);
        assertNotNull(restored);
        assertEquals(session.phase, restored.phase);
        assertEquals(session.tacticalState, restored.tacticalState);
        assertEquals(session.strategicState, restored.strategicState);
        assertEquals(session.sharedUiState, restored.sharedUiState);
        assertEquals(0.25, restored.timeScale, 0.0001);
        assertEquals(2, restored.clients.size());
        assertTrue(restored.clients.get("client").highContrast);
        assertTrue(CooperativeNetworkPrototype.checksumsMatch(restored));
    }

    @Test
    void threeHourVirtualSoakMaintainsAuthorityAutomationAndChecksums() {
        CooperativeNetworkPrototype.Session session = CooperativeNetworkPrototype.host("SOAK", "p0");
        CooperativeCommandSystem.Role[] roles = CooperativeCommandSystem.Role.values();
        for (int i = 1; i < roles.length; i++) CooperativeNetworkPrototype.join(session, "SOAK", "p" + i, 40 + i * 10, 3);
        for (int i = 0; i < roles.length; i++) {
            CooperativeNetworkPrototype.assignRole(session, "p" + i, roles[i]);
            CooperativeNetworkPrototype.setReady(session, "p" + i, true);
        }
        assertTrue(CooperativeNetworkPrototype.launch(session, "p0"));
        long[] sequences = new long[roles.length];
        for (int second = 0; second < 3 * 60 * 60; second++) {
            if (second % 30 == 0) {
                int i = (second / 30) % roles.length;
                String key = switch (roles[i]) {
                    case CAPTAIN -> "priority"; case HELM -> "course"; case TACTICAL -> "target";
                    case ENGINEERING -> "power"; case SCIENCE -> "scan"; case STRATEGIC_COMMAND -> "operation";
                };
                CooperativeNetworkPrototype.submit(session, CooperativeNetworkPrototype.PacketType.COMMAND,
                        "p" + i, roles[i], ++sequences[i], key, "tick-" + second);
            }
            if (second == 3600) CooperativeNetworkPrototype.disconnect(session, "p3");
            if (second == 3660) assertTrue(CooperativeNetworkPrototype.reconnect(session, "p3"));
            CooperativeNetworkPrototype.tick(session, 1000);
        }
        assertEquals(3L * 60 * 60 * 1000, session.clockMs);
        assertTrue(CooperativeNetworkPrototype.checksumsMatch(session));
        assertEquals(CooperativeNetworkPrototype.Phase.RUNNING, session.phase);
        assertFalse(session.authoritative.seats.get(CooperativeCommandSystem.Role.ENGINEERING).automated);
    }
}
