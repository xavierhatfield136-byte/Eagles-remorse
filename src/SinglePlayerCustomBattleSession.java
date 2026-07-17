/** Single-player custom battle wrapper that uses the same command/session contracts as multiplayer. */
public final class SinglePlayerCustomBattleSession implements MultiplayerBattleSession {
    private final MultiplayerBattleRuntime runtime;

    public SinglePlayerCustomBattleSession(MultiplayerRulesV1.BattleSetup setup) {
        this.runtime = MultiplayerBattleRuntime.createAuthoritative(setup, true);
    }

    @Override
    public Kind kind() {
        return Kind.SINGLE_PLAYER_CUSTOM_BATTLE;
    }

    @Override
    public boolean authoritative() {
        return true;
    }

    @Override
    public MultiplayerBattleRuntime runtime() {
        return runtime;
    }
}
