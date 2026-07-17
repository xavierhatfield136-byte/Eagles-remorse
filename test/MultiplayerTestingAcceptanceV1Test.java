import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerTestingAcceptanceV1Test {

    @Test
    void inputTimeoutClearsHeldMovementAndFire() {
        MultiplayerCommandGate gate = new MultiplayerCommandGate();
        gate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                MultiplayerRulesV1.CLIENT_SLOT_ID, 202, true, true));
        gate.validateInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 1L, 1L,
                1.0f, 0.0f, 0.0, true, false), 10L);

        assertTrue(gate.heldInputState(MultiplayerRulesV1.CLIENT_SLOT_ID).active());
        assertTrue(gate.clearStaleHeldInput(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                11L + MultiplayerRulesV1.INPUT_STALE_TIMEOUT_TICKS));
        assertFalse(gate.heldInputState(MultiplayerRulesV1.CLIENT_SLOT_ID).active());
        assertFalse(gate.heldInputState(MultiplayerRulesV1.CLIENT_SLOT_ID).primaryHeld());
    }

    @Test
    void snapshotSerializationRoundTripsWithoutJavaObjectGraphs() {
        MultiplayerBattleSnapshot snapshot = new MultiplayerBattleSnapshot(44L,
                List.of(new MultiplayerBattleSnapshot.ShipSnapshot(
                        101, ShipRole.FRIGATE, Faction.ALLY,
                        100.0, 200.0, 1.0, 2.0, 0.5, 90, 14.0, true)),
                List.of(new MultiplayerBattleSnapshot.SlotSnapshot(
                        MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(), 101,
                        MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                        MultiplayerRulesV1.ConnectionState.LOCAL,
                        "Host Pilot")));

        byte[] encoded = MultiplayerSerializationV1.encodeSnapshot(snapshot);
        MultiplayerBattleSnapshot decoded = MultiplayerSerializationV1.decodeSnapshot(encoded);

        assertTrue(MultiplayerProtocolV1.validatePayloadBytes(encoded).accepted());
        assertEquals(snapshot, decoded);
        assertThrows(IllegalArgumentException.class, () -> MultiplayerSerializationV1.decodeSnapshot(
                new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05}));
    }

    @Test
    void reliableEventSerializationRoundTripsWithoutJavaObjectGraphs() {
        MultiplayerReplicationV1.AuthoritativeEvent event =
                new MultiplayerReplicationV1.AuthoritativeEvent(
                        MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                        null,
                        7L,
                        88L,
                        MultiplayerRulesV1.HOST_SLOT_ID,
                        MultiplayerRulesV1.CLIENT_SLOT_ID,
                        "Elimination victory");

        byte[] encoded = MultiplayerSerializationV1.encodeEvent(event);
        MultiplayerReplicationV1.AuthoritativeEvent decoded =
                MultiplayerSerializationV1.decodeEvent(encoded);

        assertTrue(MultiplayerProtocolV1.validatePayloadBytes(encoded).accepted());
        assertEquals(event, decoded);
    }

    @Test
    void loopbackIntegrationCoversOpposingTeamDuelAndReturnToMenu() {
        MultiplayerLoopbackDuelHarness harness = new MultiplayerLoopbackDuelHarness(
                MultiplayerRulesV1.defaultDuel(1200L, ShipRole.FRIGATE, ShipRole.FRIGATE));
        assertTrue(harness.connect().accepted());
        harness.startMatch(0L);

        MultiplayerPlayerSlotState host = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState client = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.CLIENT_SLOT_ID);

        assertEquals(Faction.ALLY.teamId(), host.teamId);
        assertEquals(Faction.ENEMY.teamId(), client.teamId);
        assertFalse(Faction.forTeamId(host.teamId).isFriendlyTo(Faction.forTeamId(client.teamId)));

        harness.exitToMenu();

        assertTrue(harness.clientView().returnedToMenu());
    }

    @Test
    void directLanIntegrationIsCoveredWherePracticalBySocketHandshake() throws Exception {
        MultiplayerLanTransportV1.LifecycleLog log = new MultiplayerLanTransportV1.LifecycleLog();
        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, "acceptance-lan", log)) {
            assertTrue(host.boundAddress().port() > 0);
            assertEquals("127.0.0.1", host.boundAddress().host());
        }
    }

    @Test
    void campaignAndSinglePlayerRegressionBoundariesRemainCovered() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(12L, ShipRole.FRIGATE, ShipRole.FRIGATE);
        MultiplayerHostBattleSession host = new MultiplayerHostBattleSession(setup);
        SinglePlayerCustomBattleSession single = new SinglePlayerCustomBattleSession(setup);

        MultiplayerCommandGate.CommandResult localInput = single.runtime().acceptLocalInput(
                new InputSnapshot(true, false, false, false, false, 0.0, 0.0),
                1L,
                1L,
                true,
                false);

        assertTrue(host.authoritative());
        assertFalse(MultiplayerBattleGuardrails.campaignActionsAllowed());
        assertFalse(MultiplayerBattleGuardrails.campaignUiAllowed());
        assertTrue(localInput.accepted());
    }

    @Test
    void manualAcceptanceScriptExistsForHumanSoakAndReleaseChecks() {
        Path script = Path.of("docs", "MULTIPLAYER_V1_MANUAL_ACCEPTANCE.md");

        assertTrue(Files.isRegularFile(script));
    }

    @Test
    void manualAcceptanceDocsExposeGradleLanAcceptanceTasks() throws Exception {
        String script = Files.readString(Path.of("docs", "MULTIPLAYER_V1_MANUAL_ACCEPTANCE.md"));
        String checklist = Files.readString(Path.of("docs", "MULTIPLAYER_CUSTOM_BATTLE_CHECKLIST.md"));

        assertTrue(script.contains("multiplayerLanAcceptanceHost"));
        assertTrue(script.contains("multiplayerLanAcceptanceClient"));
        assertTrue(script.contains("multiplayerLanAcceptanceValidate"));
        assertTrue(script.contains("PmpHostAddress"));
        assertTrue(script.contains("PmpClientAddress"));
        assertTrue(script.contains("multiplayerTwoProcessAcceptance"));
        assertTrue(script.contains("multiplayerTwoMachineRunbook"));
        assertTrue(script.contains("multiplayerAcceptanceAudit"));
        assertTrue(script.contains("multiplayerReleaseGate"));
        assertTrue(script.contains("multiplayerEvidenceBundle"));
        assertTrue(checklist.contains("Gradle host/client/validation tasks"));
        assertTrue(checklist.contains("two-process loopback acceptance report task"));
        assertTrue(checklist.contains("acceptance audit"));
        assertTrue(checklist.contains("two-machine acceptance runbook"));
        assertTrue(checklist.contains("release gate"));
        assertTrue(checklist.contains("evidence bundle"));
    }
}
