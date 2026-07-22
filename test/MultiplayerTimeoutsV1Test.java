import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerTimeoutsV1Test {

    @Test
    void timeoutPolicyNamesHandshakeLobbyLoadingAndMatchBudgets() {
        assertEquals(MultiplayerTimeoutsV1.HANDSHAKE_TIMEOUT_MS,
                MultiplayerLanTransportV1.DEFAULT_CONNECT_TIMEOUT_MS);
        assertEquals(MultiplayerTimeoutsV1.HANDSHAKE_TIMEOUT_MS,
                MultiplayerLanTransportV1.DEFAULT_ACCEPT_TIMEOUT_MS);
        assertEquals(MultiplayerTimeoutsV1.MATCH_HEARTBEAT_INTERVAL_TICKS,
                MultiplayerLanTransportV1.HEARTBEAT_INTERVAL_TICKS);
        assertEquals(MultiplayerTimeoutsV1.MATCH_HEARTBEAT_TIMEOUT_TICKS,
                MultiplayerLanTransportV1.HEARTBEAT_TIMEOUT_TICKS);
        assertTrue(MultiplayerTimeoutsV1.LOBBY_HEARTBEAT_INTERVAL_TICKS > 0);
        assertTrue(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS > 0);
        assertTrue(MultiplayerTimeoutsV1.MATCH_READ_TIMEOUT_MS > 0);
        assertTrue(MultiplayerTimeoutsV1.MATCH_LOADING_TIMEOUT_MS
                >= MultiplayerTimeoutsV1.HANDSHAKE_TIMEOUT_MS);
    }
}
