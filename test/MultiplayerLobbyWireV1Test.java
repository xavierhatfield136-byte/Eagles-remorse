import app.config.MultiplayerMissionChoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLobbyWireV1Test {

    @Test
    void snapshotRoundTripsMissionReadinessAndStartState() {
        MultiplayerLobbyWireV1.Snapshot snapshot = new MultiplayerLobbyWireV1.Snapshot(
                12L,
                "lobby-1",
                "match-1",
                "nonce-1",
                12L,
                MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                4242L,
                5200,
                3200,
                ShipRole.CRUISER,
                ShipRole.BATTLECRUISER,
                true,
                true,
                true,
                "Host",
                "Client",
                "Starting match");

        MultiplayerLobbyWireV1.Snapshot decoded =
                MultiplayerLobbyWireV1.decodeSnapshot(MultiplayerLobbyWireV1.encodeSnapshot(snapshot));

        assertEquals(12L, decoded.revision());
        assertEquals("lobby-1", decoded.lobbyId());
        assertEquals("match-1", decoded.matchId());
        assertEquals("nonce-1", decoded.sessionNonce());
        assertEquals(12L, decoded.lockedConfigRevision());
        assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(), decoded.missionId());
        assertEquals(4242L, decoded.seed());
        assertEquals(5200, decoded.worldW());
        assertEquals(3200, decoded.worldH());
        assertEquals(ShipRole.CRUISER, decoded.hostHull());
        assertEquals(ShipRole.BATTLECRUISER, decoded.clientHull());
        assertTrue(decoded.hostReady());
        assertTrue(decoded.clientReady());
        assertTrue(decoded.matchStarting());
        assertEquals("Starting match", decoded.status());
    }

    @Test
    void readyCommandCarriesAcceptedRevision() {
        MultiplayerLobbyWireV1.Command command = new MultiplayerLobbyWireV1.Command(
                MultiplayerLobbyWireV1.CommandType.READY, true, 5L);

        MultiplayerLobbyWireV1.Command decoded =
                MultiplayerLobbyWireV1.decodeCommand(MultiplayerLobbyWireV1.encodeCommand(command));

        assertEquals(MultiplayerLobbyWireV1.CommandType.READY, decoded.type());
        assertTrue(decoded.ready());
        assertEquals(5L, decoded.acceptedRevision());
    }

    @Test
    void prepareAndBeginMatchRoundTripLockedDigestAndStartTick() {
        MultiplayerLobbyWireV1.PrepareMatch prepare =
                new MultiplayerLobbyWireV1.PrepareMatch("match-7", "digest-7", 12L);
        MultiplayerLobbyWireV1.BeginMatch begin =
                new MultiplayerLobbyWireV1.BeginMatch("match-7", "digest-7", 30L);

        String encodedPrepare = MultiplayerLobbyWireV1.encodePrepareMatch(prepare);
        String encodedBegin = MultiplayerLobbyWireV1.encodeBeginMatch(begin);
        MultiplayerLobbyWireV1.PrepareMatch decodedPrepare =
                MultiplayerLobbyWireV1.decodePrepareMatch(encodedPrepare);
        MultiplayerLobbyWireV1.BeginMatch decodedBegin =
                MultiplayerLobbyWireV1.decodeBeginMatch(encodedBegin);

        assertEquals("prepare", MultiplayerLobbyWireV1.payloadKind(encodedPrepare));
        assertEquals("begin", MultiplayerLobbyWireV1.payloadKind(encodedBegin));
        assertEquals("match-7", decodedPrepare.matchId());
        assertEquals("digest-7", decodedPrepare.lockedLaunchSpecDigest());
        assertEquals(12L, decodedPrepare.lockedConfigRevision());
        assertEquals("match-7", decodedBegin.matchId());
        assertEquals("digest-7", decodedBegin.lockedLaunchSpecDigest());
        assertEquals(30L, decodedBegin.startTick());
    }

    @Test
    void matchLoadedCommandCarriesDigestAndStatus() {
        MultiplayerLobbyWireV1.Command command = new MultiplayerLobbyWireV1.Command(
                MultiplayerLobbyWireV1.CommandType.MATCH_LOADED,
                false,
                12L,
                "Client",
                "match-7",
                "digest-7",
                true,
                "Loaded");

        MultiplayerLobbyWireV1.Command decoded =
                MultiplayerLobbyWireV1.decodeCommand(MultiplayerLobbyWireV1.encodeCommand(command));

        assertEquals(MultiplayerLobbyWireV1.CommandType.MATCH_LOADED, decoded.type());
        assertEquals("match-7", decoded.matchId());
        assertEquals("digest-7", decoded.lockedLaunchSpecDigest());
        assertTrue(decoded.loadAccepted());
        assertEquals("Loaded", decoded.loadStatus());
    }

    @Test
    void leaveCommandRoundTripsAsExplicitLobbyCommand() {
        MultiplayerLobbyWireV1.Command command = new MultiplayerLobbyWireV1.Command(
                MultiplayerLobbyWireV1.CommandType.LEAVE, false, 8L);

        MultiplayerLobbyWireV1.Command decoded =
                MultiplayerLobbyWireV1.decodeCommand(MultiplayerLobbyWireV1.encodeCommand(command));

        assertEquals(MultiplayerLobbyWireV1.CommandType.LEAVE, decoded.type());
        assertEquals(8L, decoded.acceptedRevision());
    }

    @Test
    void helloCommandCarriesEscapedPlayerName() {
        MultiplayerLobbyWireV1.Command command = new MultiplayerLobbyWireV1.Command(
                MultiplayerLobbyWireV1.CommandType.HELLO,
                false,
                0L,
                "Ada=Host|One");

        MultiplayerLobbyWireV1.Command decoded =
                MultiplayerLobbyWireV1.decodeCommand(MultiplayerLobbyWireV1.encodeCommand(command));

        assertEquals(MultiplayerLobbyWireV1.CommandType.HELLO, decoded.type());
        assertEquals("Ada=Host|One", decoded.playerName());
    }
}
