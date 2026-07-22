import java.util.List;
import java.util.Set;

/** Immutable host snapshot suitable for render/network publication. */
public record MultiplayerBattleSnapshot(long hostTick,
                                        long lastProcessedInputSequence,
                                        List<ShipSnapshot> ships,
                                        List<SlotSnapshot> slots,
                                        ObjectiveSummarySnapshot objectiveSummary) {
    public enum ReplaceableStateField {
        POSITION,
        VELOCITY,
        HEALTH,
        SHIELD,
        ALIVE_DEAD,
        OBJECTIVE_SUMMARY
    }

    public MultiplayerBattleSnapshot(long hostTick,
                                     List<ShipSnapshot> ships,
                                     List<SlotSnapshot> slots) {
        this(hostTick, 0L, ships, slots);
    }

    public MultiplayerBattleSnapshot(long hostTick,
                                     long lastProcessedInputSequence,
                                     List<ShipSnapshot> ships,
                                     List<SlotSnapshot> slots) {
        this(hostTick, lastProcessedInputSequence, ships, slots, ObjectiveSummarySnapshot.none());
    }

    public MultiplayerBattleSnapshot {
        hostTick = Math.max(0L, hostTick);
        lastProcessedInputSequence = Math.max(0L, lastProcessedInputSequence);
        ships = (ships == null) ? List.of() : List.copyOf(ships);
        slots = (slots == null) ? List.of() : List.copyOf(slots);
        if (objectiveSummary == null) objectiveSummary = ObjectiveSummarySnapshot.none();
    }

    public record ShipSnapshot(int shipId, ShipRole role, Faction faction,
                               double x, double y, double vx, double vy,
                               double angle, int hp, double shield, boolean alive) {}

    public record SlotSnapshot(int slotId, int teamId, int controlledShipId,
                               MultiplayerRulesV1.PlayerRole role,
                               MultiplayerRulesV1.ConnectionState connectionState,
                               String displayName) {}

    public record ObjectiveSummarySnapshot(String objectiveTypeId,
                                           boolean active,
                                           boolean complete,
                                           int owningTeamId,
                                           double progress,
                                           String summary) {
        public ObjectiveSummarySnapshot {
            objectiveTypeId = (objectiveTypeId == null || objectiveTypeId.isBlank())
                    ? "none"
                    : objectiveTypeId.trim();
            owningTeamId = Math.max(-1, owningTeamId);
            progress = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
            summary = (summary == null || summary.isBlank())
                    ? (active ? "Objective active" : "No active objective")
                    : summary.trim();
        }

        public static ObjectiveSummarySnapshot none() {
            return new ObjectiveSummarySnapshot("none", false, false, -1, 0.0, "No active objective");
        }
    }

    public record ObjectiveStateSnapshot(String objectiveTypeId,
                                         boolean active,
                                         boolean complete,
                                         int owningTeamId,
                                         double progress,
                                         int hostTeamScore,
                                         int clientTeamScore,
                                         double remainingSeconds,
                                         long revision,
                                         String summary) {
        public ObjectiveStateSnapshot {
            objectiveTypeId = (objectiveTypeId == null || objectiveTypeId.isBlank())
                    ? "none"
                    : objectiveTypeId.trim();
            owningTeamId = Math.max(-1, owningTeamId);
            progress = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
            hostTeamScore = Math.max(0, hostTeamScore);
            clientTeamScore = Math.max(0, clientTeamScore);
            remainingSeconds = Double.isFinite(remainingSeconds) ? Math.max(0.0, remainingSeconds) : 0.0;
            revision = Math.max(0L, revision);
            summary = (summary == null || summary.isBlank())
                    ? (active ? "Objective active" : "No active objective")
                    : summary.trim();
        }

        public ObjectiveSummarySnapshot toSummary() {
            return new ObjectiveSummarySnapshot(objectiveTypeId, active, complete, owningTeamId, progress, summary);
        }

        public static ObjectiveStateSnapshot none() {
            return new ObjectiveStateSnapshot("none", false, false, -1, 0.0,
                    0, 0, 0.0, 0L, "No active objective");
        }
    }

    public static Set<ReplaceableStateField> replaceableStateFields() {
        return Set.of(
                ReplaceableStateField.POSITION,
                ReplaceableStateField.VELOCITY,
                ReplaceableStateField.HEALTH,
                ReplaceableStateField.SHIELD,
                ReplaceableStateField.ALIVE_DEAD,
                ReplaceableStateField.OBJECTIVE_SUMMARY);
    }
}
