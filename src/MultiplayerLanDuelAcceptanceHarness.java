import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** CLI harness for the real two-machine V1 direct-LAN acceptance duel. */
public final class MultiplayerLanDuelAcceptanceHarness {
    public record AcceptanceReport(boolean passed,
                                   String role,
                                   String address,
                                   String localEndpoint,
                                   String remoteEndpoint,
                                   String connectionId,
                                   String result,
                                   int snapshotsReceived,
                                   boolean inputAcked,
                                   boolean returnedToMenu,
                                   String failureReason) {
        public AcceptanceReport {
            role = clean(role, "");
            address = clean(address, "");
            localEndpoint = clean(localEndpoint, "");
            remoteEndpoint = clean(remoteEndpoint, "");
            connectionId = clean(connectionId, "");
            result = clean(result, "");
            snapshotsReceived = Math.max(0, snapshotsReceived);
            failureReason = clean(failureReason, "");
        }

        public String toText() {
            return String.join(System.lineSeparator(),
                    "passed=" + passed,
                    "role=" + role,
                    "address=" + address,
                    "localEndpoint=" + localEndpoint,
                    "remoteEndpoint=" + remoteEndpoint,
                    "connectionId=" + connectionId,
                    "result=" + result,
                    "snapshotsReceived=" + snapshotsReceived,
                    "inputAcked=" + inputAcked,
                    "returnedToMenu=" + returnedToMenu,
                    "failureReason=" + failureReason,
                    "timestamp=" + Instant.now());
        }

        public AcceptanceReport(boolean passed,
                                String role,
                                String address,
                                String connectionId,
                                String result,
                                int snapshotsReceived,
                                boolean inputAcked,
                                boolean returnedToMenu,
                                String failureReason) {
            this(passed, role, address, "", "", connectionId, result,
                    snapshotsReceived, inputAcked, returnedToMenu, failureReason);
        }
    }

    private MultiplayerLanDuelAcceptanceHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        String role = args.length == 0 ? "" : args[0].trim().toLowerCase();
        AcceptanceReport report;
        if ("host".equals(role)) {
            report = runHost(
                    parseInt(options.getOrDefault("port", String.valueOf(MultiplayerLanTransportV1.DEFAULT_PORT))),
                    Boolean.parseBoolean(options.getOrDefault("loopback", "false")),
                    options.getOrDefault("host-address", ""),
                    options.getOrDefault("match", "manual-lan-acceptance"),
                    parseInt(options.getOrDefault("timeout-ms", "30000")));
        } else if ("client".equals(role)) {
            report = runClient(
                    MultiplayerLanTransportV1.parseDirectAddress(options.getOrDefault("connect", "")),
                    options.getOrDefault("client-address", ""),
                    options.getOrDefault("match", "manual-lan-acceptance"),
                    parseInt(options.getOrDefault("timeout-ms", "30000")));
        } else if ("validate".equals(role)) {
            MultiplayerReleaseReadinessV1.TwoMachineEvidence evidence =
                    MultiplayerReleaseReadinessV1.validateTwoMachineCliEvidence(
                            Path.of(required(options, "host-report")),
                            Path.of(required(options, "client-report")));
            System.out.println(evidenceToText(evidence));
            if (!evidence.accepted()) {
                throw new IllegalStateException(evidence.reason());
            }
            return;
        } else {
            throw new IllegalArgumentException(
                    "Usage: MultiplayerLanDuelAcceptanceHarness host --port=46717 [--loopback=true] "
                            + "or client --connect=HOST:PORT "
                            + "or validate --host-report=PATH --client-report=PATH");
        }

        String reportPath = options.get("report");
        if (reportPath != null && !reportPath.isBlank()) {
            writeReport(Path.of(reportPath), report);
        }
        System.out.println(report.toText());
        if (!report.passed()) {
            throw new IllegalStateException(report.failureReason());
        }
    }

    public static AcceptanceReport runHost(int port, boolean loopbackOnly, String matchId, int timeoutMs) {
        return runHost(port, loopbackOnly, "", matchId, timeoutMs);
    }

    public static AcceptanceReport runHost(int port,
                                           boolean loopbackOnly,
                                           String advertisedHostAddress,
                                           String matchId,
                                           int timeoutMs) {
        MultiplayerLanTransportV1.LifecycleLog log = new MultiplayerLanTransportV1.LifecycleLog();
        try (MultiplayerLanTransportV1.Host host = loopbackOnly
                ? MultiplayerLanTransportV1.bindLoopback(port, matchId, log)
                : MultiplayerLanTransportV1.bindAny(port, matchId, log)) {
            System.out.println("HOST_LISTENING " + host.boundAddress());
            MultiplayerLanTransportV1.TransportResult accepted = host.acceptOnce(
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    timeoutMs);
            if (!accepted.accepted() || accepted.peer() == null) {
                return fail("host", host.boundAddress().toString(), "", accepted.reason());
            }
            String reportAddress = advertisedEndpoint(advertisedHostAddress, port, host.boundAddress().toString());
            return runAcceptedHostDuel(accepted.peer(), reportAddress);
        } catch (Exception ex) {
            return fail("host", "", "", readable(ex));
        }
    }

    public static AcceptanceReport runClient(MultiplayerLanTransportV1.DirectAddress address,
                                             String matchId,
                                             int timeoutMs) {
        return runClient(address, "", matchId, timeoutMs);
    }

    public static AcceptanceReport runClient(MultiplayerLanTransportV1.DirectAddress address,
                                             String advertisedClientAddress,
                                             String matchId,
                                             int timeoutMs) {
        MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                address,
                MultiplayerProtocolV1.localFingerprint(),
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                matchId,
                new MultiplayerLanTransportV1.LifecycleLog(),
                timeoutMs);
        if (!connected.accepted() || connected.peer() == null) {
            return fail("client", address == null ? "" : address.toString(), "", connected.reason());
        }
        try {
            return runConnectedClientDuel(connected.peer(), address.toString(), advertisedClientAddress);
        } catch (Exception ex) {
            return fail("client", address.toString(), connected.peer().connectionId(), readable(ex));
        }
    }

    static AcceptanceReport runAcceptedHostDuel(MultiplayerLanTransportV1.ConnectedPeer peer,
                                                String address) throws IOException {
        MultiplayerMultipleCommandSourcesScenario scenario = new MultiplayerMultipleCommandSourcesScenario(
                MultiplayerRulesV1.defaultDuel(2600L, ShipRole.FRIGATE, ShipRole.FRIGATE));
        MultiplayerPlayerSlotState hostSlot = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
        MultiplayerPlayerSlotState clientSlot = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        Ship hostShip = findShip(scenario, hostSlot.controlledShipId);
        Ship clientShip = findShip(scenario, clientSlot.controlledShipId);
        hostShip.x = clientShip.x - 180.0;
        hostShip.y = clientShip.y;
        hostShip.shield = 0.0;

        peer.sendSnapshot(1L, scenario.runtime().snapshot(0L));
        MultiplayerLanTransportV1.WireMessage input = peer.readNextMessage();
        if (input.kind() != MultiplayerLanTransportV1.WireKind.CLIENT_INPUT || input.inputFrame() == null) {
            throw new IOException("host expected client input frame");
        }
        peer.sendInputAck(new MultiplayerProtocolV1.InputAck(
                input.inputFrame().slotId(), input.inputFrame().sequence(), 1L));
        scenario.enqueueInputFrame(input.inputFrame());
        scenario.tick(GameContext.DT, 1L);
        peer.sendSnapshot(2L, scenario.runtime().snapshot(1L));

        MultiplayerDuelVictoryEvaluator.MatchResult result = scenario.lastResult();
        if (!result.ended() || result.winningTeamId() != clientSlot.teamId) {
            throw new IOException("host expected client elimination victory: " + result.reason());
        }
        peer.sendEvent(new MultiplayerReplicationV1.AuthoritativeEvent(
                MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                null,
                1L,
                1L,
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                MultiplayerRulesV1.HOST_SLOT_ID,
                result.reason()));
        MultiplayerLanTransportV1.WireMessage disconnect = peer.readNextMessage();
        boolean returnedToMenu = disconnect.kind() == MultiplayerLanTransportV1.WireKind.DISCONNECT;
        peer.close();
        return new AcceptanceReport(returnedToMenu, "host", address,
                peer.localEndpoint(), peer.remoteEndpoint(), peer.connectionId(),
                result.reason(), 2, true, returnedToMenu,
                returnedToMenu ? "" : "host expected client disconnect");
    }

    static AcceptanceReport runConnectedClientDuel(MultiplayerLanTransportV1.ConnectedPeer peer,
                                                   String address,
                                                   String advertisedClientAddress) throws IOException {
        MultiplayerLanTransportV1.WireMessage initial = peer.readNextMessage();
        if (initial.kind() != MultiplayerLanTransportV1.WireKind.FULL_SNAPSHOT || initial.snapshot() == null) {
            throw new IOException("client expected initial host snapshot");
        }
        int clientShipId = controlledShipId(initial.snapshot(), MultiplayerRulesV1.CLIENT_SLOT_ID);
        peer.sendInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                clientShipId,
                1L,
                1L,
                0.0f,
                0.0f,
                Math.PI,
                true,
                false));

        int snapshots = 1;
        boolean acked = false;
        boolean victory = false;
        String result = "";
        for (int i = 0; i < 4 && !victory; i++) {
            MultiplayerLanTransportV1.WireMessage message = peer.readNextMessage();
            if (message.kind() == MultiplayerLanTransportV1.WireKind.INPUT_ACK
                    && message.inputAck() != null
                    && message.inputAck().inputSequence() == 1L) {
                acked = true;
            } else if (message.kind() == MultiplayerLanTransportV1.WireKind.FULL_SNAPSHOT
                    && message.snapshot() != null
                    && message.snapshot().hostTick() >= 1L) {
                snapshots++;
            } else if (message.kind() == MultiplayerLanTransportV1.WireKind.AUTHORITATIVE_EVENT
                    && message.event() != null
                    && message.event().type() == MultiplayerReplicationV1.EventType.VICTORY_DECLARED) {
                victory = true;
                result = message.event().detail();
            }
        }
        boolean passed = acked && snapshots >= 2 && victory;
        peer.sendDisconnect("manual LAN duel complete");
        peer.close();
        String localEndpoint = clean(advertisedClientAddress, peer.localEndpoint());
        return new AcceptanceReport(passed, "client", address,
                localEndpoint, peer.remoteEndpoint(), peer.connectionId(),
                result, snapshots, acked, true,
                passed ? "" : "client expected ack, final snapshot, and victory event");
    }

    private static Ship findShip(MultiplayerMultipleCommandSourcesScenario scenario, int shipId) {
        for (Ship ship : scenario.runtime().context().ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        throw new IllegalStateException("missing ship " + shipId);
    }

    private static int controlledShipId(MultiplayerBattleSnapshot snapshot, int slotId) {
        for (MultiplayerBattleSnapshot.SlotSnapshot slot : snapshot.slots()) {
            if (slot.slotId() == slotId) return slot.controlledShipId();
        }
        throw new IllegalStateException("missing slot " + slotId);
    }

    private static void writeReport(Path reportPath, AcceptanceReport report) throws IOException {
        Path absolute = reportPath.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(absolute, report.toText() + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String evidenceToText(MultiplayerReleaseReadinessV1.TwoMachineEvidence evidence) {
        return String.join(System.lineSeparator(),
                "accepted=" + evidence.accepted(),
                "reason=" + evidence.reason(),
                "hostAddress=" + evidence.hostAddress(),
                "clientAddress=" + evidence.clientAddress(),
                "hostLocalEndpoint=" + evidence.hostLocalEndpoint(),
                "hostRemoteEndpoint=" + evidence.hostRemoteEndpoint(),
                "clientLocalEndpoint=" + evidence.clientLocalEndpoint(),
                "clientRemoteEndpoint=" + evidence.clientRemoteEndpoint(),
                "hostConnectionId=" + evidence.hostConnectionId(),
                "clientConnectionId=" + evidence.clientConnectionId());
    }

    private static Map<String, String> parseArgs(String[] args) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i] == null ? "" : args[i].trim();
            if (!arg.startsWith("--")) continue;
            int eq = arg.indexOf('=');
            if (eq > 2) {
                out.put(arg.substring(2, eq).trim().toLowerCase(), arg.substring(eq + 1).trim());
            } else {
                out.put(arg.substring(2).trim().toLowerCase(), "true");
            }
        }
        return out;
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid integer: " + text);
        }
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key);
        }
        return value;
    }

    private static AcceptanceReport fail(String role, String address, String connectionId, String reason) {
        return new AcceptanceReport(false, role, address, "", "", connectionId, "", 0, false, false, reason);
    }

    private static String advertisedEndpoint(String hostAddress, int port, String fallback) {
        String host = clean(hostAddress, "");
        if (host.isBlank()) return fallback;
        return host.contains(":") ? host : host + ":" + port;
    }

    private static String readable(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        return message == null || message.isBlank() ? "LAN acceptance failed" : message;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
