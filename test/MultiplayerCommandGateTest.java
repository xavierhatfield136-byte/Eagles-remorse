import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerCommandGateTest {

    @Test
    void acceptsFreshInputForOwnedShip() {
        MultiplayerCommandGate gate = readyGate();

        MultiplayerCommandGate.CommandResult result = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L, 0L,
                        0.5f, -0.25f, Math.PI * 0.5, true, false),
                8L);

        assertTrue(result.accepted(), result.reason());
        assertTrue(result.authoritativeTick() == 8L);
        assertTrue(gate.heldInputState(MultiplayerRulesV1.HOST_SLOT_ID).primaryHeld());
    }

    @Test
    void rejectsInputForUnownedShip() {
        MultiplayerCommandGate gate = readyGate();

        MultiplayerCommandGate.CommandResult result = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        MultiplayerRulesV1.HOST_SLOT_ID, 202, 1L, 0L,
                        0.5f, 0.0f, 0.0, false, false));

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("does not own"));
    }

    @Test
    void rejectsStaleInputSequence() {
        MultiplayerCommandGate gate = readyGate();
        gate.validateInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID, 101, 4L, 0L,
                0.0f, 0.0f, 0.0, false, false));

        MultiplayerCommandGate.CommandResult result = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 4L, 1L,
                        0.0f, 0.0f, 0.0, false, false));

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("Stale or duplicate"));
    }

    @Test
    void rejectsMalformedContinuousInput() {
        MultiplayerCommandGate gate = readyGate();

        MultiplayerCommandGate.CommandResult result = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L, 0L,
                        2.0f, 0.0f, 0.0, false, false));

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("thrust"));
    }

    @Test
    void rejectsRepeatedClientTicksForInputFrequencyLimit() {
        MultiplayerCommandGate gate = readyGate();
        gate.validateInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L, 4L,
                0.0f, 0.0f, 0.0, false, false));

        MultiplayerCommandGate.CommandResult result = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 2L, 4L,
                        0.0f, 0.0f, 0.0, false, false));

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("frequency"));
    }

    @Test
    void rejectsInputWithMismatchedMatchIdentity() {
        MultiplayerCommandGate gate = new MultiplayerCommandGate();
        gate.configureMatchIdentity("match-42", "nonce-42");
        gate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                MultiplayerRulesV1.CLIENT_SLOT_ID, 202, true, true, "player-slot-2"));

        MultiplayerCommandGate.CommandResult wrongMatch = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        "match-41", "nonce-42", "player-slot-2",
                        MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 1L, 1L,
                        0.0f, 0.0f, 0.0, false, false));
        MultiplayerCommandGate.CommandResult wrongNonce = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        "match-42", "nonce-41", "player-slot-2",
                        MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 1L, 1L,
                        0.0f, 0.0f, 0.0, false, false));
        MultiplayerCommandGate.CommandResult wrongPlayer = gate.validateInputFrame(
                new MultiplayerCommandGate.PlayerInputFrame(
                        "match-42", "nonce-42", "player-slot-1",
                        MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 1L, 1L,
                        0.0f, 0.0f, 0.0, false, false));

        assertFalse(wrongMatch.accepted());
        assertTrue(wrongMatch.reason().contains("match ID"));
        assertFalse(wrongNonce.accepted());
        assertTrue(wrongNonce.reason().contains("session nonce"));
        assertFalse(wrongPlayer.accepted());
        assertTrue(wrongPlayer.reason().contains("Player ID"));
    }

    @Test
    void clearsStaleHeldInputAfterHostOwnedTimeout() {
        MultiplayerCommandGate gate = readyGate();
        gate.validateInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L, 1L,
                        1.0f, 0.0f, 0.0, true, false),
                10L);

        boolean clearedEarly = gate.clearStaleHeldInput(
                MultiplayerRulesV1.HOST_SLOT_ID,
                10L + MultiplayerRulesV1.INPUT_STALE_TIMEOUT_TICKS);
        boolean clearedLate = gate.clearStaleHeldInput(
                MultiplayerRulesV1.HOST_SLOT_ID,
                11L + MultiplayerRulesV1.INPUT_STALE_TIMEOUT_TICKS);

        assertFalse(clearedEarly);
        assertTrue(clearedLate);
        assertFalse(gate.heldInputState(MultiplayerRulesV1.HOST_SLOT_ID).active());
    }

    @Test
    void rejectsV1UnsupportedDiscreteCommands() {
        MultiplayerCommandGate gate = readyGate();

        MultiplayerCommandGate.CommandResult superweapon = gate.validateDiscreteCommand(
                new MultiplayerCommandGate.DiscreteCommand(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L,
                        MultiplayerCommandGate.DiscreteCommandType.ACTIVATE_SUPERWEAPON));
        MultiplayerCommandGate.CommandResult formation = gate.validateDiscreteCommand(
                new MultiplayerCommandGate.DiscreteCommand(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 2L,
                        MultiplayerCommandGate.DiscreteCommandType.FORMATION));

        assertFalse(superweapon.accepted());
        assertTrue(superweapon.reason().contains("Superweapons are disabled"));
        assertFalse(formation.accepted());
        assertTrue(formation.reason().contains("Formations and fleet-wide orders"));
    }

    @Test
    void acceptsV1AllowedDiscreteCommand() {
        MultiplayerCommandGate gate = readyGate();

        MultiplayerCommandGate.CommandResult result = gate.validateDiscreteCommand(
                new MultiplayerCommandGate.DiscreteCommand(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L,
                        MultiplayerCommandGate.DiscreteCommandType.SELECT_TARGET));

        assertTrue(result.accepted(), result.reason());
    }

    @Test
    void acceptsV1AbilityAndLobbyCommands() {
        MultiplayerCommandGate gate = readyGate();

        MultiplayerCommandGate.CommandResult ability = gate.validateDiscreteCommand(
                new MultiplayerCommandGate.DiscreteCommand(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L,
                        MultiplayerCommandGate.DiscreteCommandType.ACTIVATE_ABILITY));
        MultiplayerCommandGate.CommandResult lobby = gate.validateDiscreteCommand(
                new MultiplayerCommandGate.DiscreteCommand(
                        MultiplayerRulesV1.HOST_SLOT_ID, 101, 2L,
                        MultiplayerCommandGate.DiscreteCommandType.LOBBY_CHANGE));

        assertTrue(ability.accepted(), ability.reason());
        assertTrue(lobby.accepted(), lobby.reason());
    }

    @Test
    void localInputAdapterUsesMultiplayerInputFrameShape() {
        InputSnapshot input = new InputSnapshot(true, false, true, false, false, 0.0, 10.0);

        MultiplayerCommandGate.PlayerInputFrame frame = MultiplayerInputFrameAdapter.fromLocalInput(
                MultiplayerRulesV1.HOST_SLOT_ID, 101, 7L, 12L, input, true, false);

        assertTrue(frame.thrust() > 0.0f);
        assertTrue(frame.turn() < 0.0f);
        assertTrue(frame.primaryHeld());
        assertTrue(Double.isFinite(frame.aimAngle()));
    }

    @Test
    void localInputAdapterCanStampGameplayEnvelopeIdentity() {
        InputSnapshot input = new InputSnapshot(true, false, false, false, false, 0.0, 10.0);

        MultiplayerCommandGate.PlayerInputFrame frame = MultiplayerInputFrameAdapter.fromLocalInput(
                "match-42", "nonce-42", "player-slot-2",
                MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 7L, 12L, input, true, false);

        assertTrue("match-42".equals(frame.matchId()));
        assertTrue("nonce-42".equals(frame.sessionNonce()));
        assertTrue("player-slot-2".equals(frame.playerId()));
        assertTrue(frame.commandType() == MultiplayerCommandGate.GameplayCommandType.DIRECT_SHIP_INPUT);
    }

    private static MultiplayerCommandGate readyGate() {
        MultiplayerCommandGate gate = new MultiplayerCommandGate();
        gate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                MultiplayerRulesV1.HOST_SLOT_ID, 101, true, true));
        gate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                MultiplayerRulesV1.CLIENT_SLOT_ID, 202, true, true));
        return gate;
    }
}
