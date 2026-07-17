import java.util.Map;

/** Host-side V1 elimination victory evaluator. Clients only display this result later. */
public final class MultiplayerDuelVictoryEvaluator {
    public record MatchResult(boolean ended, int winningTeamId, String reason) {
        public MatchResult {
            reason = (reason == null || reason.isBlank()) ? "In progress" : reason.trim();
        }
    }

    private MultiplayerDuelVictoryEvaluator() {}

    public static MatchResult evaluate(GameContext ctx, Map<Integer, MultiplayerPlayerSlotState> slots) {
        if (ctx == null || slots == null || slots.isEmpty()) {
            return new MatchResult(false, -1, "In progress");
        }

        int aliveTeam = -1;
        int aliveTeams = 0;
        for (MultiplayerPlayerSlotState slot : slots.values()) {
            if (slot == null || slot.controlledShipId <= 0) continue;
            Ship ship = findShip(ctx, slot.controlledShipId);
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (aliveTeam != slot.teamId) {
                aliveTeam = slot.teamId;
                aliveTeams++;
            }
        }
        if (aliveTeams == 1) {
            return new MatchResult(true, aliveTeam, "Elimination victory");
        }
        if (aliveTeams == 0) {
            return new MatchResult(true, -1, "Mutual destruction");
        }
        return new MatchResult(false, -1, "In progress");
    }

    private static Ship findShip(GameContext ctx, int shipId) {
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }
}
