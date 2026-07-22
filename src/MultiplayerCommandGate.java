import java.util.HashMap;
import java.util.Map;

/**
 * Host-side V1 command validation. The gate owns sequencing and slot/ship authority checks;
 * accepted commands may then be applied by the authoritative battle simulation.
 */
public final class MultiplayerCommandGate {
    public enum DiscreteCommandType {
        SELECT_TARGET,
        ACTIVATE_ABILITY,
        READY,
        LOBBY_CHANGE,
        FLEET_ORDER,
        FORMATION,
        ESCORT_ASSIGNMENT,
        REQUEST_RESPAWN,
        PAUSE,
        ACTIVATE_SUPERWEAPON,
        BATTLEFIELD_WARP,
        RECONNECT
    }

    public enum GameplayCommandType {
        DIRECT_SHIP_INPUT
    }

    public record SlotOwnership(int slotId, int controlledShipId, boolean connected, boolean ready, String playerId) {
        public SlotOwnership(int slotId, int controlledShipId, boolean connected, boolean ready) {
            this(slotId, controlledShipId, connected, ready, "");
        }

        public SlotOwnership {
            playerId = clean(playerId);
        }
    }

    public record PlayerInputFrame(
            String matchId,
            String sessionNonce,
            String playerId,
            GameplayCommandType commandType,
            int slotId,
            int controlledShipId,
            long sequence,
            long clientTick,
            float thrust,
            float turn,
            double aimAngle,
            boolean primaryHeld,
            boolean secondaryHeld) {
        public PlayerInputFrame(int slotId,
                                int controlledShipId,
                                long sequence,
                                long clientTick,
                                float thrust,
                                float turn,
                                double aimAngle,
                                boolean primaryHeld,
                                boolean secondaryHeld) {
            this("", "", "", GameplayCommandType.DIRECT_SHIP_INPUT,
                    slotId, controlledShipId, sequence, clientTick, thrust, turn,
                    aimAngle, primaryHeld, secondaryHeld);
        }

        public PlayerInputFrame(String matchId,
                                String sessionNonce,
                                String playerId,
                                int slotId,
                                int controlledShipId,
                                long sequence,
                                long clientTick,
                                float thrust,
                                float turn,
                                double aimAngle,
                                boolean primaryHeld,
                                boolean secondaryHeld) {
            this(matchId, sessionNonce, playerId, GameplayCommandType.DIRECT_SHIP_INPUT,
                    slotId, controlledShipId, sequence, clientTick, thrust, turn,
                    aimAngle, primaryHeld, secondaryHeld);
        }

        public PlayerInputFrame {
            matchId = clean(matchId);
            sessionNonce = clean(sessionNonce);
            playerId = clean(playerId);
            if (commandType == null) commandType = GameplayCommandType.DIRECT_SHIP_INPUT;
        }

        public PlayerInputFrame withIdentity(String matchId, String sessionNonce, String playerId) {
            return new PlayerInputFrame(matchId, sessionNonce, playerId, commandType, slotId, controlledShipId,
                    sequence, clientTick, thrust, turn, aimAngle, primaryHeld, secondaryHeld);
        }
    }

    public record DiscreteCommand(int slotId, int controlledShipId, long sequence, DiscreteCommandType type) {}

    public record CommandResult(boolean accepted, String reason, long sequence, long authoritativeTick) {
        public CommandResult(boolean accepted, String reason, long sequence) {
            this(accepted, reason, sequence, -1L);
        }

        public CommandResult {
            reason = (reason == null || reason.isBlank()) ? (accepted ? "Accepted" : "Rejected") : reason.trim();
        }
    }

    public record HeldInputState(boolean active, boolean primaryHeld, boolean secondaryHeld,
                                 float thrust, float turn, long lastHostTick) {}

    private final Map<Integer, SlotOwnership> slots = new HashMap<>();
    private final Map<Integer, String> playerIdsBySlot = new HashMap<>();
    private final Map<Integer, Long> lastInputSequenceBySlot = new HashMap<>();
    private final Map<Integer, Long> lastInputClientTickBySlot = new HashMap<>();
    private final Map<Integer, Long> lastDiscreteSequenceBySlot = new HashMap<>();
    private final Map<Integer, HeldInputState> heldInputsBySlot = new HashMap<>();
    private String expectedMatchId = "";
    private String expectedSessionNonce = "";

    public void configureMatchIdentity(String matchId, String sessionNonce) {
        expectedMatchId = clean(matchId);
        expectedSessionNonce = clean(sessionNonce);
    }

    public void registerSlot(SlotOwnership ownership) {
        if (ownership == null || ownership.slotId() <= 0) return;
        slots.put(ownership.slotId(), ownership);
        if (!ownership.playerId().isBlank()) {
            playerIdsBySlot.put(ownership.slotId(), ownership.playerId());
        }
    }

    public CommandResult validateInputFrame(PlayerInputFrame frame) {
        return validateInputFrame(frame, -1L);
    }

    public CommandResult validateInputFrame(PlayerInputFrame frame, long authoritativeTick) {
        if (frame == null) return new CommandResult(false, "Missing input frame", -1L);
        CommandResult identityResult = validateInputIdentity(frame);
        if (!identityResult.accepted()) return identityResult;
        SlotOwnership ownership = slots.get(frame.slotId());
        CommandResult ownershipResult = validateOwnership(ownership, frame.controlledShipId(), frame.sequence());
        if (!ownershipResult.accepted()) return ownershipResult;
        CommandResult commandTypeResult = validateGameplayCommandType(frame.commandType(), frame.sequence());
        if (!commandTypeResult.accepted()) return commandTypeResult;
        if (frame.sequence() <= lastInputSequenceBySlot.getOrDefault(frame.slotId(), Long.MIN_VALUE)) {
            return new CommandResult(false, "Stale or duplicate input sequence", frame.sequence());
        }
        Long previousClientTick = lastInputClientTickBySlot.get(frame.slotId());
        if (previousClientTick != null && frame.clientTick() <= previousClientTick) {
            return new CommandResult(false, "Input frame exceeds V1 frequency or repeats a client tick", frame.sequence());
        }
        if (!finite(frame.aimAngle())) {
            return new CommandResult(false, "Malformed aim angle", frame.sequence());
        }
        if (!finite(frame.thrust()) || Math.abs(frame.thrust()) > 1.0001f) {
            return new CommandResult(false, "Malformed thrust input", frame.sequence());
        }
        if (!finite(frame.turn()) || Math.abs(frame.turn()) > 1.0001f) {
            return new CommandResult(false, "Malformed turn input", frame.sequence());
        }
        lastInputSequenceBySlot.put(frame.slotId(), frame.sequence());
        lastInputClientTickBySlot.put(frame.slotId(), frame.clientTick());
        heldInputsBySlot.put(frame.slotId(), new HeldInputState(
                Math.abs(frame.thrust()) > 1e-6f || Math.abs(frame.turn()) > 1e-6f
                        || frame.primaryHeld() || frame.secondaryHeld(),
                frame.primaryHeld(), frame.secondaryHeld(), frame.thrust(), frame.turn(),
                Math.max(0L, authoritativeTick)));
        return new CommandResult(true, "Accepted", frame.sequence(), Math.max(-1L, authoritativeTick));
    }

    public CommandResult validateDiscreteCommand(DiscreteCommand command) {
        if (command == null) return new CommandResult(false, "Missing command", -1L);
        SlotOwnership ownership = slots.get(command.slotId());
        CommandResult ownershipResult = validateOwnership(ownership, command.controlledShipId(), command.sequence());
        if (!ownershipResult.accepted()) return ownershipResult;
        if (command.sequence() <= lastDiscreteSequenceBySlot.getOrDefault(command.slotId(), Long.MIN_VALUE)) {
            return new CommandResult(false, "Stale or duplicate command sequence", command.sequence());
        }
        if (command.type() == null) {
            return new CommandResult(false, "Missing command type", command.sequence());
        }
        BattleAuthority.Decision authority =
                HostBattleAuthority.INSTANCE.evaluate(BattleAuthorityOperation.forDiscreteCommand(command.type()));
        if (!authority.accepted()) {
            return new CommandResult(false, authority.reason(), command.sequence());
        }
        CommandResult ruleResult = validateV1Rule(command.type(), command.sequence());
        if (!ruleResult.accepted()) return ruleResult;
        lastDiscreteSequenceBySlot.put(command.slotId(), command.sequence());
        return new CommandResult(true, "Accepted", command.sequence());
    }

    public HeldInputState heldInputState(int slotId) {
        HeldInputState state = heldInputsBySlot.get(slotId);
        return state == null ? new HeldInputState(false, false, false, 0.0f, 0.0f, -1L) : state;
    }

    public long lastProcessedInputSequence() {
        long max = 0L;
        for (Long sequence : lastInputSequenceBySlot.values()) {
            if (sequence != null) max = Math.max(max, sequence);
        }
        return max;
    }

    public boolean clearStaleHeldInput(int slotId, long currentHostTick) {
        HeldInputState state = heldInputsBySlot.get(slotId);
        if (state == null || !state.active()) return false;
        if (currentHostTick - state.lastHostTick() <= MultiplayerRulesV1.INPUT_STALE_TIMEOUT_TICKS) return false;
        heldInputsBySlot.put(slotId, new HeldInputState(false, false, false, 0.0f, 0.0f, currentHostTick));
        return true;
    }

    private CommandResult validateOwnership(SlotOwnership ownership, int controlledShipId, long sequence) {
        if (ownership == null) return new CommandResult(false, "Unknown player slot", sequence);
        if (!ownership.connected()) return new CommandResult(false, "Player slot is disconnected", sequence);
        if (!ownership.ready()) return new CommandResult(false, "Player slot is not ready", sequence);
        if (ownership.controlledShipId() <= 0) return new CommandResult(false, "Player slot has no controlled ship", sequence);
        if (controlledShipId != ownership.controlledShipId()) {
            return new CommandResult(false, "Player does not own this ship", sequence);
        }
        return new CommandResult(true, "Accepted", sequence);
    }

    private CommandResult validateInputIdentity(PlayerInputFrame frame) {
        if (!expectedMatchId.isBlank() && !expectedMatchId.equals(frame.matchId())) {
            return new CommandResult(false, "Input match ID does not match the active match", frame.sequence());
        }
        if (!expectedSessionNonce.isBlank() && !expectedSessionNonce.equals(frame.sessionNonce())) {
            return new CommandResult(false, "Input session nonce is invalid", frame.sequence());
        }
        String expectedPlayerId = playerIdsBySlot.getOrDefault(frame.slotId(), "");
        boolean identityRequired = !expectedMatchId.isBlank()
                || !expectedSessionNonce.isBlank()
                || !frame.playerId().isBlank();
        if (identityRequired && !expectedPlayerId.isBlank() && !expectedPlayerId.equals(frame.playerId())) {
            return new CommandResult(false, "Player ID does not own this slot", frame.sequence());
        }
        return new CommandResult(true, "Accepted", frame.sequence());
    }

    private CommandResult validateV1Rule(DiscreteCommandType type, long sequence) {
        return switch (type) {
            case SELECT_TARGET, ACTIVATE_ABILITY, READY, LOBBY_CHANGE -> new CommandResult(true, "Accepted", sequence);
            case FLEET_ORDER -> reject(MultiplayerRulesV1.UnsupportedFeature.FORMATIONS, sequence);
            case FORMATION -> reject(MultiplayerRulesV1.UnsupportedFeature.FORMATIONS, sequence);
            case ESCORT_ASSIGNMENT -> reject(MultiplayerRulesV1.UnsupportedFeature.ESCORTS, sequence);
            case REQUEST_RESPAWN -> reject(MultiplayerRulesV1.UnsupportedFeature.RESPAWNS, sequence);
            case PAUSE -> reject(MultiplayerRulesV1.UnsupportedFeature.ACTIVE_MATCH_PAUSE, sequence);
            case ACTIVATE_SUPERWEAPON -> reject(MultiplayerRulesV1.UnsupportedFeature.SUPERWEAPONS, sequence);
            case BATTLEFIELD_WARP -> reject(MultiplayerRulesV1.UnsupportedFeature.BATTLEFIELD_WARP, sequence);
            case RECONNECT -> reject(MultiplayerRulesV1.UnsupportedFeature.RECONNECT, sequence);
        };
    }

    private CommandResult validateGameplayCommandType(GameplayCommandType type, long sequence) {
        if (type == GameplayCommandType.DIRECT_SHIP_INPUT) {
            return new CommandResult(true, "Accepted", sequence);
        }
        return new CommandResult(false, "Unsupported gameplay command type", sequence);
    }

    private CommandResult reject(MultiplayerRulesV1.UnsupportedFeature feature, long sequence) {
        return new CommandResult(false, feature.rejectionMessage(), sequence);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
