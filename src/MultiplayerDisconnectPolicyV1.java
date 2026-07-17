import java.util.Map;

/** V1 disconnect rules: simple, readable, and host-authoritative. */
public final class MultiplayerDisconnectPolicyV1 {
    private MultiplayerDisconnectPolicyV1() {}

    public enum DisconnectActor {
        HOST,
        CLIENT,
        UNKNOWN
    }

    public enum Outcome {
        CLIENT_FORFEIT_HOST_WINS,
        HOST_DISCONNECTED_MATCH_CLOSED,
        RECONNECT_REJECTED,
        HOST_MIGRATION_REJECTED,
        NO_ACTION
    }

    public record DisconnectResult(Outcome outcome,
                                   boolean matchEnded,
                                   boolean returnToMenu,
                                   int winningTeamId,
                                   String reason) {
        public DisconnectResult {
            if (outcome == null) outcome = Outcome.NO_ACTION;
            winningTeamId = Math.max(-1, winningTeamId);
            reason = (reason == null || reason.isBlank()) ? outcome.name() : reason.trim();
        }
    }

    public static DisconnectResult handleDisconnect(DisconnectActor actor,
                                                    Map<Integer, MultiplayerPlayerSlotState> slots) {
        return switch (actor == null ? DisconnectActor.UNKNOWN : actor) {
            case CLIENT -> new DisconnectResult(
                    Outcome.CLIENT_FORFEIT_HOST_WINS,
                    true,
                    true,
                    teamForSlot(slots, MultiplayerRulesV1.HOST_SLOT_ID),
                    "Client disconnected; V1 rules award the match to the host.");
            case HOST -> new DisconnectResult(
                    Outcome.HOST_DISCONNECTED_MATCH_CLOSED,
                    true,
                    true,
                    -1,
                    "Host disconnected; V1 has no host migration, so the match is closed.");
            case UNKNOWN -> new DisconnectResult(
                    Outcome.NO_ACTION,
                    false,
                    false,
                    -1,
                    "Unknown disconnect actor");
        };
    }

    public static DisconnectResult handleTimeout(DisconnectActor actor,
                                                 Map<Integer, MultiplayerPlayerSlotState> slots) {
        DisconnectResult base = handleDisconnect(actor, slots);
        if (actor == DisconnectActor.CLIENT) {
            return new DisconnectResult(base.outcome(), true, true, base.winningTeamId(),
                    "Client timed out; V1 rules treat this as a forfeit.");
        }
        if (actor == DisconnectActor.HOST) {
            return new DisconnectResult(base.outcome(), true, true, -1,
                    "Host timed out; V1 has no host migration, so the match is closed.");
        }
        return base;
    }

    public static DisconnectResult rejectReconnect() {
        return new DisconnectResult(
                Outcome.RECONNECT_REJECTED,
                false,
                false,
                -1,
                MultiplayerRulesV1.UnsupportedFeature.RECONNECT.rejectionMessage());
    }

    public static DisconnectResult rejectHostMigration() {
        return new DisconnectResult(
                Outcome.HOST_MIGRATION_REJECTED,
                false,
                false,
                -1,
                MultiplayerRulesV1.UnsupportedFeature.HOST_MIGRATION.rejectionMessage());
    }

    private static int teamForSlot(Map<Integer, MultiplayerPlayerSlotState> slots, int slotId) {
        if (slots == null) return -1;
        MultiplayerPlayerSlotState slot = slots.get(slotId);
        return slot == null ? -1 : slot.teamId;
    }
}
