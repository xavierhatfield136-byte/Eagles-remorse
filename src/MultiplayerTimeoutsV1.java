/** Named timeout policy for V1 direct LAN multiplayer. */
public final class MultiplayerTimeoutsV1 {
    public static final int HANDSHAKE_TIMEOUT_MS = 2_000;
    public static final int LOBBY_HEARTBEAT_INTERVAL_TICKS = MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE;
    public static final int LOBBY_HEARTBEAT_TIMEOUT_TICKS = MultiplayerLoopbackTransport.HEARTBEAT_TIMEOUT_TICKS;
    public static final int MATCH_LOADING_TIMEOUT_MS = 5_000;
    public static final int MATCH_HEARTBEAT_INTERVAL_TICKS = MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE;
    public static final int MATCH_HEARTBEAT_TIMEOUT_TICKS = MultiplayerLoopbackTransport.HEARTBEAT_TIMEOUT_TICKS;
    public static final int LOBBY_READ_TIMEOUT_MS = 500;
    public static final int MATCH_READ_TIMEOUT_MS = 1_000;

    private MultiplayerTimeoutsV1() {}
}
