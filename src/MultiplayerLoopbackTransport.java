import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** In-memory host/client transport used before direct LAN sockets exist. */
public final class MultiplayerLoopbackTransport {
    public static final int HEARTBEAT_TIMEOUT_TICKS = MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE * 8;

    public enum Endpoint {
        HOST,
        CLIENT
    }

    public record Message(MultiplayerProtocolV1.MessageKind kind,
                          long sequence,
                          long hostTick,
                          MultiplayerCommandGate.PlayerInputFrame inputFrame,
                          MultiplayerBattleSnapshot snapshot,
                          MultiplayerProtocolV1.InputAck inputAck,
                          MultiplayerReplicationV1.AuthoritativeEvent event,
                          String text) {
        public Message {
            if (kind == null) kind = MultiplayerProtocolV1.MessageKind.ERROR_MESSAGE;
            sequence = Math.max(0L, sequence);
            hostTick = Math.max(0L, hostTick);
            text = text == null ? "" : text.trim();
        }

        public MultiplayerProtocolV1.Envelope envelope() {
            return new MultiplayerProtocolV1.Envelope(kind, sequence, hostTick, estimatedPayloadBytes());
        }

        private int estimatedPayloadBytes() {
            int bytes = 24 + text.length();
            if (inputFrame != null) bytes += 48;
            if (inputAck != null) bytes += 24;
            if (event != null) bytes += 64 + event.detail().length();
            if (snapshot != null) bytes += 64 + snapshot.ships().size() * 96 + snapshot.slots().size() * 64;
            return bytes;
        }
    }

    private final Queue<Message> toHost = new ArrayDeque<>();
    private final Queue<Message> toClient = new ArrayDeque<>();
    private boolean connected;
    private boolean closed;
    private long lastHostHeartbeatTick = 0L;
    private long lastClientHeartbeatTick = 0L;

    public MultiplayerProtocolV1.CompatibilityResult connect(
            MultiplayerProtocolV1.CompatibilityFingerprint host,
            MultiplayerProtocolV1.CompatibilityFingerprint client) {
        MultiplayerProtocolV1.CompatibilityResult result =
                MultiplayerProtocolV1.validateCompatibility(host, client);
        connected = result.accepted();
        sendToClient(new Message(
                connected ? MultiplayerProtocolV1.MessageKind.HELLO_ACCEPTED
                        : MultiplayerProtocolV1.MessageKind.HELLO_REJECTED,
                0L, 0L, null, null, null, null, result.reason()));
        return result;
    }

    public boolean connected() {
        return connected && !closed;
    }

    public boolean closed() {
        return closed;
    }

    public void sendInputToHost(MultiplayerCommandGate.PlayerInputFrame frame) {
        if (!connected()) return;
        sendToHost(new Message(MultiplayerProtocolV1.MessageKind.CLIENT_INPUT_FRAME,
                frame == null ? 0L : frame.sequence(),
                frame == null ? 0L : frame.clientTick(),
                frame, null, null, null, ""));
    }

    public void sendSnapshotToClient(long sequence, MultiplayerBattleSnapshot snapshot, long hostTick) {
        if (!connected()) return;
        sendToClient(new Message(MultiplayerProtocolV1.MessageKind.FULL_SNAPSHOT,
                sequence, hostTick, null, snapshot, null, null, ""));
    }

    public void sendAckToClient(MultiplayerProtocolV1.InputAck ack) {
        if (!connected()) return;
        sendToClient(new Message(MultiplayerProtocolV1.MessageKind.INPUT_ACK,
                ack == null ? 0L : ack.inputSequence(),
                ack == null ? 0L : ack.authoritativeTick(),
                null, null, ack, null, ""));
    }

    public void sendEventToClient(long sequence, long hostTick,
                                  MultiplayerReplicationV1.AuthoritativeEvent event) {
        if (!connected()) return;
        sendToClient(new Message(kindForEvent(event == null ? null : event.type()),
                sequence, hostTick, null, null, null, event, ""));
    }

    public void heartbeat(Endpoint from, long hostTick) {
        if (!connected()) return;
        long tick = Math.max(0L, hostTick);
        if (from == Endpoint.HOST) {
            lastHostHeartbeatTick = tick;
            sendToClient(new Message(MultiplayerProtocolV1.MessageKind.HEARTBEAT,
                    tick, tick, null, null, null, null, "host"));
        } else {
            lastClientHeartbeatTick = tick;
            sendToHost(new Message(MultiplayerProtocolV1.MessageKind.HEARTBEAT,
                    tick, tick, null, null, null, null, "client"));
        }
    }

    public boolean timedOut(Endpoint endpoint, long currentHostTick) {
        long last = endpoint == Endpoint.HOST ? lastHostHeartbeatTick : lastClientHeartbeatTick;
        return connected() && Math.max(0L, currentHostTick) - last > HEARTBEAT_TIMEOUT_TICKS;
    }

    public List<Message> drainForHost() {
        return drain(toHost);
    }

    public List<Message> drainForClient() {
        return drain(toClient);
    }

    public void close(String reason) {
        if (!closed) {
            sendToClient(new Message(MultiplayerProtocolV1.MessageKind.DISCONNECT_NOTICE,
                    0L, 0L, null, null, null, null,
                    reason == null ? "Match closed" : reason));
        }
        closed = true;
        connected = false;
        toHost.clear();
    }

    private void sendToHost(Message message) {
        if (message == null) return;
        if (!MultiplayerProtocolV1.validateEnvelope(message.envelope()).accepted()) return;
        toHost.add(message);
    }

    private static MultiplayerProtocolV1.MessageKind kindForEvent(MultiplayerReplicationV1.EventType type) {
        return switch (type == null ? MultiplayerReplicationV1.EventType.HIT_CONFIRMED : type) {
            case SHIP_SPAWNED -> MultiplayerProtocolV1.MessageKind.SPAWN;
            case SHIP_DESPAWNED -> MultiplayerProtocolV1.MessageKind.DESPAWN;
            case SHIP_DESTROYED -> MultiplayerProtocolV1.MessageKind.DEATH;
            case VICTORY_DECLARED -> MultiplayerProtocolV1.MessageKind.MATCH_END;
            case WEAPON_FIRED, HIT_CONFIRMED, EXPLOSION_OCCURRED, OBJECTIVE_COMPLETED,
                    PLAYER_DISCONNECTED, CONTROL_OWNERSHIP_CHANGED ->
                    MultiplayerProtocolV1.MessageKind.AUTHORITATIVE_EVENT;
        };
    }

    private void sendToClient(Message message) {
        if (message == null) return;
        if (!MultiplayerProtocolV1.validateEnvelope(message.envelope()).accepted()) return;
        toClient.add(message);
    }

    private static List<Message> drain(Queue<Message> queue) {
        ArrayList<Message> out = new ArrayList<>();
        while (!queue.isEmpty()) {
            out.add(queue.remove());
        }
        return out;
    }
}
