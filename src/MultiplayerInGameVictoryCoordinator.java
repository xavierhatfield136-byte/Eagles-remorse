/** Shared in-game V1 match-end behavior for host authority and client presentation. */
public final class MultiplayerInGameVictoryCoordinator {
    private static final String DEFAULT_RESULT = "Match ended";
    private static final double BANNER_SECONDS = 2.4;

    public record HostVictory(MultiplayerDuelVictoryEvaluator.MatchResult result,
                              MultiplayerReplicationV1.AuthoritativeEvent event) {}

    private MultiplayerInGameVictoryCoordinator() {}

    public static HostVictory evaluateHostVictory(GameContext ctx, long hostTick, long eventSequence) {
        if (ctx == null || ctx.multiplayerBattleRuntime == null || ctx.gameOver) return null;
        MultiplayerDuelVictoryEvaluator.MatchResult result =
                MultiplayerDuelVictoryEvaluator.evaluate(ctx, ctx.multiplayerBattleRuntime.slots());
        if (result == null || !result.ended()) return null;

        String detail = result.reason();
        applyMatchEnd(ctx, detail);
        MultiplayerReplicationV1.AuthoritativeEvent event =
                new MultiplayerReplicationV1.AuthoritativeEvent(
                        MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                        null,
                        eventSequence,
                        hostTick,
                        MultiplayerRulesV1.HOST_SLOT_ID,
                        0,
                        detail);
        return new HostVictory(result, event);
    }

    public static boolean applyClientEvent(GameContext ctx,
                                           MultiplayerReplicationV1.AuthoritativeEvent event) {
        if (ctx == null || event == null) return false;
        if (event.type() != MultiplayerReplicationV1.EventType.VICTORY_DECLARED) return false;
        applyMatchEnd(ctx, event.detail());
        return true;
    }

    public static int applyClientEvents(GameContext ctx,
                                        Iterable<MultiplayerReplicationV1.AuthoritativeEvent> events) {
        if (ctx == null || events == null) return 0;
        int applied = 0;
        for (MultiplayerReplicationV1.AuthoritativeEvent event : events) {
            if (applyClientEvent(ctx, event)) applied++;
        }
        return applied;
    }

    private static void applyMatchEnd(GameContext ctx, String detail) {
        if (ctx == null) return;
        String text = (detail == null || detail.isBlank()) ? DEFAULT_RESULT : detail.trim();
        ctx.gameOver = true;
        ctx.state = GameState.GAME_OVER;
        ctx.gameOverText = text;
        EventSystem.showBanner(ctx, text, BANNER_SECONDS);
    }
}
