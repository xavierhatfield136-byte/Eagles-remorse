/** Host-owned authoritative V1 multiplayer duel session. */
public final class MultiplayerHostBattleSession implements MultiplayerBattleSession {
    private final MultiplayerBattleRuntime runtime;

    public MultiplayerHostBattleSession(MultiplayerRulesV1.BattleSetup setup) {
        this.runtime = MultiplayerBattleRuntime.createAuthoritative(setup, false);
    }

    @Override
    public Kind kind() {
        return Kind.MULTIPLAYER_HOST;
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
