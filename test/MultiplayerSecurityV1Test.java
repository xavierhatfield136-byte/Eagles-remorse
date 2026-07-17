import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerSecurityV1Test {

    @Test
    void validatesPayloadsAndRejectsJavaObjectStreams() {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();

        assertTrue(security.validatePayload(new byte[]{1, 2, 3}).accepted());
        assertFalse(security.validatePayload(new byte[]{
                (byte) 0xAC, (byte) 0xED, 0x00, 0x05
        }).accepted());
        assertFalse(security.validatePayload(null).accepted());
    }

    @Test
    void clampsNumericInputBeforeCommandGateValidation() {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();
        MultiplayerCommandGate gate = gate(true, true, 202);

        MultiplayerCommandGate.PlayerInputFrame wild = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                202,
                1L,
                1L,
                4.0f,
                -5.0f,
                Math.PI * 5.0,
                true,
                false);

        MultiplayerSecurityV1.SecureInput secure = security.sanitizeInput(wild);
        MultiplayerCommandGate.CommandResult result =
                security.validateSanitizedInput(gate, wild, 10L);

        assertFalse(secure.malformed());
        assertEquals(1.0f, secure.frame().thrust(), 1e-6f);
        assertEquals(-1.0f, secure.frame().turn(), 1e-6f);
        assertTrue(secure.frame().aimAngle() >= 0.0 && secure.frame().aimAngle() < Math.PI * 2.0);
        assertTrue(result.accepted());
        assertTrue(security.suspiciousCommands().stream()
                .anyMatch(e -> e.reason().contains("Clamped")));
    }

    @Test
    void malformedInputsAreRejectedWithoutCrashing() {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();
        MultiplayerCommandGate gate = gate(true, true, 202);
        MultiplayerCommandGate.PlayerInputFrame malformed = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                202,
                1L,
                1L,
                0.0f,
                0.0f,
                Double.NaN,
                false,
                false);

        MultiplayerCommandGate.CommandResult result =
                security.validateSanitizedInput(gate, malformed, 1L);

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("aim angle"));
        assertFalse(security.suspiciousCommands().isEmpty());
    }

    @Test
    void rejectsUnknownDisconnectedUnreadyAndUnownedCommands() {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();
        MultiplayerCommandGate disconnected = gate(false, true, 202);
        MultiplayerCommandGate unready = gate(true, false, 202);
        MultiplayerCommandGate ready = gate(true, true, 202);

        MultiplayerCommandGate.CommandResult unknownResult =
                security.validateSanitizedInput(new MultiplayerCommandGate(),
                        input(999, 202, 1L), 1L);
        MultiplayerCommandGate.CommandResult disconnectedResult =
                security.validateSanitizedInput(disconnected,
                        input(MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 1L), 1L);
        MultiplayerCommandGate.CommandResult unreadyResult =
                security.validateSanitizedInput(unready,
                        input(MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 1L), 1L);
        MultiplayerCommandGate.CommandResult unownedResult =
                security.validateSanitizedInput(ready,
                        input(MultiplayerRulesV1.CLIENT_SLOT_ID, 101, 1L), 1L);

        assertFalse(unknownResult.accepted());
        assertFalse(disconnectedResult.accepted());
        assertFalse(unreadyResult.accepted());
        assertFalse(unownedResult.accepted());
        assertTrue(security.suspiciousCommands().size() >= 4);
    }

    @Test
    void rejectsUnsupportedDiscreteCommandsAndLogsSuspicion() {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();
        MultiplayerCommandGate gate = gate(true, true, 202);

        MultiplayerCommandGate.CommandResult result = security.validateDiscreteCommand(
                gate,
                new MultiplayerCommandGate.DiscreteCommand(
                        MultiplayerRulesV1.CLIENT_SLOT_ID,
                        202,
                        1L,
                        MultiplayerCommandGate.DiscreteCommandType.REQUEST_RESPAWN));

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("Respawns are unsupported"));
        assertTrue(security.suspiciousCommands().stream()
                .anyMatch(e -> e.reason().contains("Respawns are unsupported")));
    }

    @Test
    void avoidsExposingLocalDataAndRedactsPrivateAddressesForLogs() {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();

        assertTrue(security.exposesForbiddenLocalData("C:\\Users\\xhatf\\save.sav"));
        assertTrue(security.exposesForbiddenLocalData("config/post_alpha_feature_flags.properties"));
        assertTrue(security.exposesForbiddenLocalData("password=abc"));
        assertFalse(security.exposesForbiddenLocalData("ordinary disconnect reason"));

        assertEquals("127.0.0.1:46717", security.redactAddressForLog("127.0.0.1:46717"));
        assertEquals("private-lan-address:46717", security.redactAddressForLog("192.168.1.44:46717"));
        assertEquals("private-lan-address:46717", security.redactAddressForLog("172.20.4.5:46717"));
        assertEquals("remote-address:46717", security.redactAddressForLog("203.0.113.10:46717"));
    }

    private static MultiplayerCommandGate gate(boolean connected, boolean ready, int shipId) {
        MultiplayerCommandGate gate = new MultiplayerCommandGate();
        gate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                shipId,
                connected,
                ready));
        return gate;
    }

    private static MultiplayerCommandGate.PlayerInputFrame input(int slotId, int shipId, long sequence) {
        return new MultiplayerCommandGate.PlayerInputFrame(
                slotId,
                shipId,
                sequence,
                sequence,
                0.0f,
                0.0f,
                0.0,
                false,
                false);
    }
}
