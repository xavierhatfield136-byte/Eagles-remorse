import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLanTransportV1Test {

    @Test
    void requirementsAreDirectLanOnlyWithoutInternetClaims() {
        MultiplayerLanTransportV1.TransportRequirements requirements =
                MultiplayerLanTransportV1.requirements();

        assertTrue(requirements.reliableOrderedDelivery());
        assertTrue(requirements.sequencedReplaceableSnapshots());
        assertTrue(requirements.connectionOrientedSessions());
        assertTrue(requirements.directIpJoin());
        assertTrue(requirements.manualLanAddressJoin());
        assertFalse(requirements.lanDiscovery());
        assertFalse(requirements.encryption());
        assertFalse(requirements.platformInvites());
        assertFalse(requirements.relay());
        assertFalse(requirements.internetHostingClaimed());
        assertTrue(requirements.limitationSummary().contains("Firewalls"));
        assertTrue(requirements.limitationSummary().contains("NAT"));
    }

    @Test
    void parsesManualLanAddresses() {
        assertEquals("127.0.0.1:46717",
                MultiplayerLanTransportV1.parseDirectAddress("").toString());
        assertEquals("localhost:46717",
                MultiplayerLanTransportV1.parseDirectAddress("localhost").toString());
        assertEquals("192.168.1.40:48000",
                MultiplayerLanTransportV1.parseDirectAddress("192.168.1.40:48000").toString());
        assertEquals("::1:47000",
                MultiplayerLanTransportV1.parseDirectAddress("[::1]:47000").toString());
    }

    @Test
    void privateLanAddressClassificationExcludesLoopbackAndPublicAddresses() throws Exception {
        assertTrue(MultiplayerLanTransportV1.isPrivateLanAddress(InetAddress.getByName("192.168.1.20")));
        assertTrue(MultiplayerLanTransportV1.isPrivateLanAddress(InetAddress.getByName("10.0.0.4")));
        assertFalse(MultiplayerLanTransportV1.isPrivateLanAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(MultiplayerLanTransportV1.isPrivateLanAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void directLoopbackTcpConnectionHandshakeHeartbeatDisconnectAndLogs() throws Exception {
        MultiplayerLanTransportV1.LifecycleLog hostLog = new MultiplayerLanTransportV1.LifecycleLog();
        MultiplayerLanTransportV1.LifecycleLog clientLog = new MultiplayerLanTransportV1.LifecycleLog();
        String matchId = "match-lan-loopback";
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, matchId, hostLog)) {
            CompletableFuture<MultiplayerLanTransportV1.TransportResult> accepted =
                    CompletableFuture.supplyAsync(() -> host.acceptOnce(
                            MultiplayerProtocolV1.localFingerprint(),
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            MultiplayerLanTransportV1.DEFAULT_ACCEPT_TIMEOUT_MS), executor);

            MultiplayerLanTransportV1.DirectAddress address =
                    MultiplayerLanTransportV1.parseDirectAddress("127.0.0.1:" + host.boundAddress().port());
            MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                    address,
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    matchId,
                    clientLog,
                    MultiplayerLanTransportV1.DEFAULT_CONNECT_TIMEOUT_MS);
            MultiplayerLanTransportV1.TransportResult hostAccepted =
                    accepted.get(3, TimeUnit.SECONDS);

            assertTrue(connected.accepted());
            assertTrue(hostAccepted.accepted());
            assertNotNull(connected.peer());
            assertNotNull(hostAccepted.peer());
            assertEquals(connected.peer().connectionId(), hostAccepted.peer().connectionId());

            connected.peer().sendHeartbeat(25L);
            MultiplayerLanTransportV1.WireMessage hostMessage = hostAccepted.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.HEARTBEAT, hostMessage.kind());
            assertEquals(25L, hostMessage.hostTick());
            assertEquals(25L, hostAccepted.peer().lastHeartbeatTick());
            assertEquals(25L, hostAccepted.peer().lastValidMessageTick());
            assertFalse(hostAccepted.peer().heartbeatTimedOut(
                    25L + MultiplayerLanTransportV1.HEARTBEAT_TIMEOUT_TICKS));

            assertFalse(hostAccepted.peer().sendHeartbeatIfIdle(26L));
            assertTrue(hostAccepted.peer().sendHeartbeatIfIdle(MultiplayerLanTransportV1.HEARTBEAT_INTERVAL_TICKS));
            MultiplayerLanTransportV1.WireMessage clientMessage = connected.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.HEARTBEAT, clientMessage.kind());
            assertEquals(MultiplayerLanTransportV1.HEARTBEAT_INTERVAL_TICKS, clientMessage.hostTick());
            assertEquals(MultiplayerLanTransportV1.HEARTBEAT_INTERVAL_TICKS,
                    connected.peer().lastValidMessageTick());

            connected.peer().sendDisconnect("client exit");
            MultiplayerLanTransportV1.WireMessage disconnect = hostAccepted.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.DISCONNECT, disconnect.kind());
            assertEquals("client exit", disconnect.text());

            connected.peer().close();
            hostAccepted.peer().close();
        } finally {
            executor.shutdownNow();
        }

        assertTrue(hostLog.containsEvent("bind_loopback"));
        assertTrue(hostLog.containsEvent("connected"));
        assertTrue(hostLog.containsEvent("heartbeat_receive"));
        assertTrue(hostLog.containsEvent("disconnect_receive"));
        assertTrue(clientLog.containsEvent("connect_attempt"));
        assertTrue(clientLog.containsEvent("connected"));
        assertTrue(clientLog.containsEvent("heartbeat_receive"));

        List<MultiplayerLanTransportV1.LifecycleEntry> entries = hostLog.entries();
        assertTrue(entries.stream().anyMatch(e -> e.matchId().equals(matchId)
                && !e.connectionId().isBlank()
                && e.playerSlotId() == MultiplayerRulesV1.CLIENT_SLOT_ID
                && e.protocolVersion() == MultiplayerProtocolV1.PROTOCOL_VERSION
                && !e.gameBuild().isBlank()));
    }

    @Test
    void directLoopbackTcpCarriesInputSnapshotsAcksAndEvents() throws Exception {
        MultiplayerLanTransportV1.LifecycleLog hostLog = new MultiplayerLanTransportV1.LifecycleLog();
        MultiplayerLanTransportV1.LifecycleLog clientLog = new MultiplayerLanTransportV1.LifecycleLog();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String matchId = "match-lan-battle-wire";

        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, matchId, hostLog)) {
            CompletableFuture<MultiplayerLanTransportV1.TransportResult> accepted =
                    CompletableFuture.supplyAsync(() -> host.acceptOnce(
                            MultiplayerProtocolV1.localFingerprint(),
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            MultiplayerLanTransportV1.DEFAULT_ACCEPT_TIMEOUT_MS), executor);

            MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                    host.boundAddress(),
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    matchId,
                    clientLog,
                    MultiplayerLanTransportV1.DEFAULT_CONNECT_TIMEOUT_MS);
            MultiplayerLanTransportV1.TransportResult hostAccepted =
                    accepted.get(3, TimeUnit.SECONDS);

            MultiplayerCommandGate.PlayerInputFrame input =
                    new MultiplayerCommandGate.PlayerInputFrame(
                            matchId,
                            MultiplayerProtocolV1.sessionNonceForMatch(matchId),
                            MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID),
                            MultiplayerRulesV1.CLIENT_SLOT_ID, 202, 4L, 5L,
                            1.0f, -0.25f, Math.PI, true, false);
            connected.peer().sendInputFrame(input);
            MultiplayerLanTransportV1.WireMessage inputMessage = hostAccepted.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.CLIENT_INPUT, inputMessage.kind());
            assertEquals(input, inputMessage.inputFrame());
            assertEquals(5L, hostAccepted.peer().lastValidMessageTick());
            assertFalse(hostAccepted.peer().peerTimedOut(
                    5L + MultiplayerLanTransportV1.HEARTBEAT_TIMEOUT_TICKS));
            assertEquals(matchId, inputMessage.inputFrame().matchId());
            assertEquals(MultiplayerCommandGate.GameplayCommandType.DIRECT_SHIP_INPUT,
                    inputMessage.inputFrame().commandType());
            assertEquals(MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID),
                    inputMessage.inputFrame().playerId());

            MultiplayerBattleSnapshot snapshot = new MultiplayerBattleSnapshot(9L, 4L, List.of(
                    new MultiplayerBattleSnapshot.ShipSnapshot(
                            202, ShipRole.FRIGATE, Faction.ENEMY, 10.0, 20.0,
                            1.0, 2.0, 0.5, 300, 20.0, true)
            ), List.of(
                    new MultiplayerBattleSnapshot.SlotSnapshot(
                            MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(), 202,
                            MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                            MultiplayerRulesV1.ConnectionState.CONNECTED, "Client")
            ));
            hostAccepted.peer().sendInputAck(new MultiplayerProtocolV1.InputAck(
                    MultiplayerRulesV1.CLIENT_SLOT_ID, 4L, 9L));
            hostAccepted.peer().sendSnapshot(7L, snapshot);
            hostAccepted.peer().sendEvent(new MultiplayerReplicationV1.AuthoritativeEvent(
                    MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                    null, 8L, 9L, MultiplayerRulesV1.CLIENT_SLOT_ID, 0,
                    "Elimination victory"));

            MultiplayerLanTransportV1.WireMessage ackMessage = connected.peer().readNextMessage();
            MultiplayerLanTransportV1.WireMessage snapshotMessage = connected.peer().readNextMessage();
            MultiplayerLanTransportV1.WireMessage eventMessage = connected.peer().readNextMessage();
            assertEquals(MultiplayerLanTransportV1.WireKind.INPUT_ACK, ackMessage.kind());
            assertEquals(4L, ackMessage.inputAck().inputSequence());
            assertEquals(MultiplayerLanTransportV1.WireKind.FULL_SNAPSHOT, snapshotMessage.kind());
            assertEquals(7L, snapshotMessage.sequence());
            assertEquals(snapshot, snapshotMessage.snapshot());
            assertEquals(4L, snapshotMessage.snapshot().lastProcessedInputSequence());
            assertEquals(MultiplayerLanTransportV1.WireKind.AUTHORITATIVE_EVENT, eventMessage.kind());
            assertEquals("Elimination victory", eventMessage.event().detail());
            assertEquals(9L, connected.peer().lastValidMessageTick());

            connected.peer().close();
            hostAccepted.peer().close();
        } finally {
            executor.shutdownNow();
        }

        assertTrue(hostLog.containsEvent("input_receive"));
        assertTrue(hostLog.containsEvent("ack_send"));
        assertTrue(hostLog.containsEvent("snapshot_send"));
        assertTrue(hostLog.containsEvent("event_send"));
        assertTrue(clientLog.containsEvent("input_send"));
        assertTrue(clientLog.containsEvent("ack_receive"));
        assertTrue(clientLog.containsEvent("snapshot_receive"));
        assertTrue(clientLog.containsEvent("event_receive"));
    }

    @Test
    void peerTimeoutMarksDisconnectAndWritesSpecificLifecycleLog() throws Exception {
        MultiplayerLanTransportV1.LifecycleLog hostLog = new MultiplayerLanTransportV1.LifecycleLog();
        MultiplayerLanTransportV1.LifecycleLog clientLog = new MultiplayerLanTransportV1.LifecycleLog();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String matchId = "match-lan-timeout";

        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, matchId, hostLog)) {
            CompletableFuture<MultiplayerLanTransportV1.TransportResult> accepted =
                    CompletableFuture.supplyAsync(() -> host.acceptOnce(
                            MultiplayerProtocolV1.localFingerprint(),
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            MultiplayerLanTransportV1.DEFAULT_ACCEPT_TIMEOUT_MS), executor);

            MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                    host.boundAddress(),
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    matchId,
                    clientLog,
                    MultiplayerLanTransportV1.DEFAULT_CONNECT_TIMEOUT_MS);
            MultiplayerLanTransportV1.TransportResult hostAccepted =
                    accepted.get(3, TimeUnit.SECONDS);

            assertTrue(connected.accepted());
            assertTrue(hostAccepted.accepted());
            assertTrue(hostAccepted.peer().markDisconnectedIfTimedOut(
                    MultiplayerLanTransportV1.HEARTBEAT_TIMEOUT_TICKS + 1L));
            assertTrue(hostAccepted.peer().disconnected());
            connected.peer().close();
        } finally {
            executor.shutdownNow();
        }

        assertTrue(hostLog.containsEvent("peer_timeout"));
    }

    @Test
    void incompatibleDirectLanClientIsRejectedWithReadableReason() throws Exception {
        MultiplayerLanTransportV1.LifecycleLog hostLog = new MultiplayerLanTransportV1.LifecycleLog();
        MultiplayerLanTransportV1.LifecycleLog clientLog = new MultiplayerLanTransportV1.LifecycleLog();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        MultiplayerProtocolV1.CompatibilityFingerprint hostFingerprint =
                MultiplayerProtocolV1.localFingerprint();
        MultiplayerProtocolV1.CompatibilityFingerprint badClient =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION + 1,
                        hostFingerprint.gameBuild(),
                        hostFingerprint.manifest());

        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, "match-reject", hostLog)) {
            CompletableFuture<MultiplayerLanTransportV1.TransportResult> accepted =
                    CompletableFuture.supplyAsync(() -> host.acceptOnce(
                            hostFingerprint,
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            MultiplayerLanTransportV1.DEFAULT_ACCEPT_TIMEOUT_MS), executor);
            MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                    host.boundAddress(),
                    badClient,
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    "match-reject",
                    clientLog,
                    MultiplayerLanTransportV1.DEFAULT_CONNECT_TIMEOUT_MS);
            MultiplayerLanTransportV1.TransportResult rejectedByHost =
                    accepted.get(3, TimeUnit.SECONDS);

            assertFalse(connected.accepted());
            assertFalse(rejectedByHost.accepted());
            assertTrue(connected.reason().contains("Protocol mismatch"));
            assertTrue(rejectedByHost.reason().contains("Protocol mismatch"));
            assertNull(connected.peer());
            assertNull(rejectedByHost.peer());
        } finally {
            executor.shutdownNow();
        }

        assertTrue(hostLog.containsEvent("reject"));
        assertTrue(clientLog.containsEvent("connect_rejected"));
    }

    @Test
    void failedManualLanConnectProducesReadableErrorAndLifecycleLog() {
        MultiplayerLanTransportV1.LifecycleLog log = new MultiplayerLanTransportV1.LifecycleLog();
        MultiplayerLanTransportV1.TransportResult result = MultiplayerLanTransportV1.connect(
                new MultiplayerLanTransportV1.DirectAddress("127.0.0.1", 9),
                MultiplayerProtocolV1.localFingerprint(),
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                "match-fail",
                log,
                100);

        assertFalse(result.accepted());
        assertNull(result.peer());
        assertTrue(log.containsEvent("connect_attempt"));
        assertTrue(log.containsEvent("connect_failed"));
    }
}
