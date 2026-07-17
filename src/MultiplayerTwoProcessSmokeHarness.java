import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** CLI smoke harness used by tests/manual acceptance to prove host and client can run as separate JVMs. */
public final class MultiplayerTwoProcessSmokeHarness {
    private MultiplayerTwoProcessSmokeHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: MultiplayerTwoProcessSmokeHarness <host|client> <coord-dir>");
        }
        Path coordDir = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(coordDir);
        if ("host".equalsIgnoreCase(args[0])) {
            runHost(coordDir);
        } else if ("client".equalsIgnoreCase(args[0])) {
            runClient(coordDir);
        } else {
            throw new IllegalArgumentException("Unknown role: " + args[0]);
        }
    }

    private static void runHost(Path coordDir) throws Exception {
        MultiplayerLanTransportV1.LifecycleLog log = new MultiplayerLanTransportV1.LifecycleLog();
        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, "two-process-smoke", log)) {
            Files.writeString(coordDir.resolve("port.txt"),
                    Integer.toString(host.boundAddress().port()),
                    StandardCharsets.UTF_8);
            MultiplayerLanTransportV1.TransportResult accepted = host.acceptOnce(
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    5_000);
            if (!accepted.accepted() || accepted.peer() == null) {
                throw new IllegalStateException("host rejected client: " + accepted.reason());
            }
            MultiplayerMultipleCommandSourcesScenario scenario = new MultiplayerMultipleCommandSourcesScenario(
                    MultiplayerRulesV1.defaultDuel(2600L, ShipRole.FRIGATE, ShipRole.FRIGATE));
            MultiplayerPlayerSlotState hostSlot = scenario.runtime().slots().get(MultiplayerRulesV1.HOST_SLOT_ID);
            MultiplayerPlayerSlotState clientSlot = scenario.runtime().slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID);
            Ship hostShip = findShip(scenario, hostSlot.controlledShipId);
            Ship clientShip = findShip(scenario, clientSlot.controlledShipId);
            hostShip.x = clientShip.x - 180.0;
            hostShip.y = clientShip.y;
            hostShip.shield = 0.0;

            accepted.peer().sendSnapshot(1L, scenario.runtime().snapshot(0L));
            MultiplayerLanTransportV1.WireMessage input = accepted.peer().readNextMessage();
            if (input.kind() != MultiplayerLanTransportV1.WireKind.CLIENT_INPUT || input.inputFrame() == null) {
                throw new IllegalStateException("host expected client input frame");
            }
            accepted.peer().sendInputAck(new MultiplayerProtocolV1.InputAck(
                    input.inputFrame().slotId(), input.inputFrame().sequence(), 1L));
            scenario.enqueueInputFrame(input.inputFrame());
            scenario.tick(GameContext.DT, 1L);
            accepted.peer().sendSnapshot(2L, scenario.runtime().snapshot(1L));
            MultiplayerDuelVictoryEvaluator.MatchResult result = scenario.lastResult();
            if (!result.ended() || result.winningTeamId() != clientSlot.teamId) {
                throw new IllegalStateException("host expected client elimination victory: " + result.reason());
            }
            accepted.peer().sendEvent(new MultiplayerReplicationV1.AuthoritativeEvent(
                    MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                    null,
                    1L,
                    1L,
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    MultiplayerRulesV1.HOST_SLOT_ID,
                    result.reason()));
            MultiplayerLanTransportV1.WireMessage disconnect = accepted.peer().readNextMessage();
            if (disconnect.kind() != MultiplayerLanTransportV1.WireKind.DISCONNECT) {
                throw new IllegalStateException("host expected client disconnect");
            }
            accepted.peer().close();
            Files.writeString(coordDir.resolve("host.done"),
                    "HOST_OK " + accepted.peer().connectionId() + " " + result.reason(),
                    StandardCharsets.UTF_8);
            System.out.println("HOST_OK");
        }
    }

    private static void runClient(Path coordDir) throws Exception {
        Path portFile = coordDir.resolve("port.txt");
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!Files.isRegularFile(portFile) && System.nanoTime() < deadline) {
            Thread.sleep(25L);
        }
        if (!Files.isRegularFile(portFile)) {
            throw new IllegalStateException("client timed out waiting for host port");
        }
        int port = Integer.parseInt(Files.readString(portFile, StandardCharsets.UTF_8).trim());
        MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                new MultiplayerLanTransportV1.DirectAddress("127.0.0.1", port),
                MultiplayerProtocolV1.localFingerprint(),
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                "two-process-smoke",
                new MultiplayerLanTransportV1.LifecycleLog(),
                5_000);
        if (!connected.accepted() || connected.peer() == null) {
            throw new IllegalStateException("client failed to connect: " + connected.reason());
        }
        MultiplayerLanTransportV1.WireMessage initial = connected.peer().readNextMessage();
        if (initial.kind() != MultiplayerLanTransportV1.WireKind.FULL_SNAPSHOT || initial.snapshot() == null) {
            throw new IllegalStateException("client expected initial host snapshot");
        }
        int clientShipId = controlledShipId(initial.snapshot(), MultiplayerRulesV1.CLIENT_SLOT_ID);
        connected.peer().sendInputFrame(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                clientShipId,
                1L,
                1L,
                0.0f,
                0.0f,
                Math.PI,
                true,
                false));

        boolean acked = false;
        boolean snapshotReceived = false;
        boolean victoryReceived = false;
        String result = "";
        for (int i = 0; i < 4 && !victoryReceived; i++) {
            MultiplayerLanTransportV1.WireMessage message = connected.peer().readNextMessage();
            if (message.kind() == MultiplayerLanTransportV1.WireKind.INPUT_ACK
                    && message.inputAck() != null
                    && message.inputAck().inputSequence() == 1L) {
                acked = true;
            } else if (message.kind() == MultiplayerLanTransportV1.WireKind.FULL_SNAPSHOT
                    && message.snapshot() != null
                    && message.snapshot().hostTick() >= 1L) {
                snapshotReceived = true;
            } else if (message.kind() == MultiplayerLanTransportV1.WireKind.AUTHORITATIVE_EVENT
                    && message.event() != null
                    && message.event().type() == MultiplayerReplicationV1.EventType.VICTORY_DECLARED) {
                victoryReceived = true;
                result = message.event().detail();
            }
        }
        if (!acked || !snapshotReceived || !victoryReceived) {
            throw new IllegalStateException("client expected ack, final snapshot, and victory event");
        }
        connected.peer().sendDisconnect("client smoke complete");
        connected.peer().close();
        Files.writeString(coordDir.resolve("client.done"),
                "CLIENT_OK " + connected.peer().connectionId() + " " + result,
                StandardCharsets.UTF_8);
        System.out.println("CLIENT_OK");
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
}
