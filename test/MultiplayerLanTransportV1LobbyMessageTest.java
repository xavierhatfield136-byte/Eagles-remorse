import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLanTransportV1LobbyMessageTest {

    @Test
    void lobbyStateAndCommandFramesTravelOverConnectedPeer() throws Exception {
        MultiplayerLanTransportV1.Host host =
                MultiplayerLanTransportV1.bindLoopback(0, "lobby-frame-test", null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MultiplayerLanTransportV1.TransportResult> acceptedFuture = executor.submit(() ->
                    host.acceptOnce(MultiplayerProtocolV1.localFingerprint(),
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            5_000));

            MultiplayerLanTransportV1.TransportResult client = MultiplayerLanTransportV1.connect(
                    host.boundAddress(),
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    "lobby-frame-test",
                    null,
                    5_000);
            MultiplayerLanTransportV1.TransportResult accepted = acceptedFuture.get(5, TimeUnit.SECONDS);

            assertTrue(client.accepted(), client.reason());
            assertTrue(accepted.accepted(), accepted.reason());

            accepted.peer().sendLobbyState("revision=1|missionId=core:heavy_duel");
            MultiplayerLanTransportV1.WireMessage state = client.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.LOBBY_STATE, state.kind());
            assertEquals("revision=1|missionId=core:heavy_duel", state.text());

            client.peer().sendLobbyCommand("type=READY|ready=true|acceptedRevision=1");
            MultiplayerLanTransportV1.WireMessage command = accepted.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.LOBBY_COMMAND, command.kind());
            assertEquals("type=READY|ready=true|acceptedRevision=1", command.text());

            client.peer().close();
            accepted.peer().close();
        } finally {
            executor.shutdownNow();
            host.close();
        }
    }
}
