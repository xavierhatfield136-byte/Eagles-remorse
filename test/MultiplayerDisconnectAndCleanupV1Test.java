import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerDisconnectAndCleanupV1Test {

    @Test
    void clientDisconnectImmediatelyForfeitsToHost() {
        MultiplayerLoopbackDuelHarness harness = harness();
        harness.connect();
        harness.startMatch(0L);

        MultiplayerDisconnectPolicyV1.DisconnectResult result =
                MultiplayerDisconnectPolicyV1.handleDisconnect(
                        MultiplayerDisconnectPolicyV1.DisconnectActor.CLIENT,
                        harness.hostScenario().runtime().slots());

        int hostTeam = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.HOST_SLOT_ID).teamId;
        assertEquals(MultiplayerDisconnectPolicyV1.Outcome.CLIENT_FORFEIT_HOST_WINS, result.outcome());
        assertTrue(result.matchEnded());
        assertTrue(result.returnToMenu());
        assertEquals(hostTeam, result.winningTeamId());
        assertTrue(result.reason().contains("forfeit") || result.reason().contains("award"));
    }

    @Test
    void hostDisconnectClosesMatchWithoutHostMigration() {
        MultiplayerDisconnectPolicyV1.DisconnectResult result =
                MultiplayerDisconnectPolicyV1.handleDisconnect(
                        MultiplayerDisconnectPolicyV1.DisconnectActor.HOST,
                        null);

        assertEquals(MultiplayerDisconnectPolicyV1.Outcome.HOST_DISCONNECTED_MATCH_CLOSED, result.outcome());
        assertTrue(result.matchEnded());
        assertTrue(result.returnToMenu());
        assertEquals(-1, result.winningTeamId());
        assertTrue(result.reason().contains("no host migration"));
    }

    @Test
    void reconnectAndHostMigrationAreExplicitlyRejected() {
        MultiplayerDisconnectPolicyV1.DisconnectResult reconnect =
                MultiplayerDisconnectPolicyV1.rejectReconnect();
        MultiplayerDisconnectPolicyV1.DisconnectResult migration =
                MultiplayerDisconnectPolicyV1.rejectHostMigration();

        assertEquals(MultiplayerDisconnectPolicyV1.Outcome.RECONNECT_REJECTED, reconnect.outcome());
        assertFalse(reconnect.matchEnded());
        assertTrue(reconnect.reason().contains("Reconnect is unsupported"));
        assertEquals(MultiplayerDisconnectPolicyV1.Outcome.HOST_MIGRATION_REJECTED, migration.outcome());
        assertTrue(migration.reason().contains("Host migration is unsupported"));
    }

    @Test
    void stalledClientTimeoutUsesSameForfeitPolicy() {
        MultiplayerLoopbackDuelHarness harness = harness();
        harness.connect();
        harness.clientHeartbeat(1L);

        assertTrue(harness.clientTimedOut(2L + MultiplayerLoopbackTransport.HEARTBEAT_TIMEOUT_TICKS));

        MultiplayerDisconnectPolicyV1.DisconnectResult result =
                MultiplayerDisconnectPolicyV1.handleTimeout(
                        MultiplayerDisconnectPolicyV1.DisconnectActor.CLIENT,
                        harness.hostScenario().runtime().slots());

        assertEquals(MultiplayerDisconnectPolicyV1.Outcome.CLIENT_FORFEIT_HOST_WINS, result.outcome());
        assertTrue(result.reason().contains("timed out"));
    }

    @Test
    void cleanupScopeClearsQueuesBuffersListenersCloseablesAndThreads() throws Exception {
        MultiplayerNetworkCommandQueue queue = new MultiplayerNetworkCommandQueue();
        queue.enqueueInput(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID, 10, 1L, 1L,
                0.0f, 0.0f, 0.0, false, false));
        queue.enqueueCommand(new MultiplayerCommandGate.DiscreteCommand(
                MultiplayerRulesV1.HOST_SLOT_ID, 10, 1L,
                MultiplayerCommandGate.DiscreteCommandType.READY));
        MultiplayerMatchCleanupScope cleanup = new MultiplayerMatchCleanupScope(queue);
        AtomicBoolean closed = new AtomicBoolean(false);
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "multiplayer-cleanup-test");
        sleeper.start();

        cleanup.addSnapshot(new MultiplayerBattleSnapshot(1L, null, null));
        cleanup.addListener(() -> {});
        cleanup.addCloseable(() -> closed.set(true));
        cleanup.addBackgroundThread(sleeper);

        cleanup.close();
        sleeper.join(500L);

        assertTrue(cleanup.closed());
        assertTrue(queue.drainInputs().isEmpty());
        assertTrue(queue.drainCommands().isEmpty());
        assertEquals(0, cleanup.snapshotBufferSize());
        assertEquals(0, cleanup.listenerCount());
        assertEquals(0, cleanup.closeableCount());
        assertEquals(0, cleanup.backgroundThreadCount());
        assertTrue(closed.get());
        assertFalse(sleeper.isAlive());

        cleanup.close();
        assertTrue(cleanup.closed());
    }

    @Test
    void repeatedMatchCreationAndDestructionLeavesNoTrackedResources() {
        for (int i = 0; i < 20; i++) {
            MultiplayerNetworkCommandQueue queue = new MultiplayerNetworkCommandQueue();
            MultiplayerMatchCleanupScope cleanup = new MultiplayerMatchCleanupScope(queue);
            cleanup.addSnapshot(new MultiplayerBattleSnapshot(i, null, null));
            cleanup.addListener(() -> {});
            cleanup.addCloseable(() -> {});

            cleanup.close();

            assertEquals(0, cleanup.snapshotBufferSize());
            assertEquals(0, cleanup.listenerCount());
            assertEquals(0, cleanup.closeableCount());
            assertEquals(0, cleanup.backgroundThreadCount());
            assertTrue(cleanup.closed());
        }
    }

    private static MultiplayerLoopbackDuelHarness harness() {
        return new MultiplayerLoopbackDuelHarness(
                MultiplayerRulesV1.defaultDuel(990L, ShipRole.FRIGATE, ShipRole.FRIGATE));
    }
}
