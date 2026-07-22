/** Policy boundary for local, host-authoritative, and client-presentation battle work. */
public interface BattleAuthority {
    record Decision(boolean accepted, String reason) {
        public Decision {
            reason = (reason == null || reason.isBlank())
                    ? (accepted ? "Accepted" : "Rejected by battle authority")
                    : reason.trim();
        }
    }

    Decision evaluate(BattleAuthorityOperation operation);

    default boolean permits(BattleAuthorityOperation operation) {
        return evaluate(operation).accepted();
    }

    static BattleAuthority forContext(GameContext ctx) {
        if (ctx == null || !ctx.multiplayerBattle || ctx.multiplayerAuthorityMode == MultiplayerAuthorityMode.NONE) {
            return LocalBattleAuthority.INSTANCE;
        }
        if (ctx.multiplayerAuthorityMode == MultiplayerAuthorityMode.HOST) {
            return HostBattleAuthority.INSTANCE;
        }
        return ClientBattleAuthority.INSTANCE;
    }
}
