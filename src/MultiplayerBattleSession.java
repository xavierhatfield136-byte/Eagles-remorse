/** Session orchestration boundary for custom-battle multiplayer work. */
public interface MultiplayerBattleSession {
    enum Kind {
        SINGLE_PLAYER_CUSTOM_BATTLE,
        MULTIPLAYER_HOST,
        MULTIPLAYER_CLIENT
    }

    Kind kind();

    boolean authoritative();

    MultiplayerBattleRuntime runtime();
}
