import app.config.MultiplayerLaunchConfig;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the live LAN socket workers for an in-game V1 duel. */
public final class MultiplayerInGameDuelSession implements Closeable {
    public enum State {
        STARTING,
        LISTENING,
        CONNECTED,
        MATCH_ENDED,
        DISCONNECTED,
        ERROR,
        CLOSED
    }

    private final MultiplayerLaunchConfig launch;
    private final MultiplayerLanTransportV1.LifecycleLog lifecycleLog =
            new MultiplayerLanTransportV1.LifecycleLog();
    private final ConcurrentLinkedQueue<MultiplayerCommandGate.PlayerInputFrame> inboundInputFrames =
            new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<MultiplayerReplicationV1.AuthoritativeEvent> inboundEvents =
            new ConcurrentLinkedQueue<>();
    private final AtomicReference<MultiplayerBattleSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicLong clientInputSequence = new AtomicLong();
    private final AtomicLong clientTick = new AtomicLong();
    private final AtomicLong snapshotSequence = new AtomicLong();

    private volatile State state = State.STARTING;
    private volatile String status = "Starting multiplayer session";
    private volatile boolean running = true;
    private volatile MultiplayerLanTransportV1.Host host;
    private volatile MultiplayerLanTransportV1.ConnectedPeer peer;
    private volatile Thread worker;
    private volatile long lastAckedInputSequence = -1L;

    private MultiplayerInGameDuelSession(MultiplayerLaunchConfig launch) {
        this.launch = launch == null
                ? MultiplayerLaunchConfig.host(MultiplayerLanTransportV1.DEFAULT_PORT, "127.0.0.1")
                : launch;
    }

    public static MultiplayerInGameDuelSession start(MultiplayerLaunchConfig launch) {
        MultiplayerInGameDuelSession session = new MultiplayerInGameDuelSession(launch);
        session.startWorker();
        return session;
    }

    public static MultiplayerInGameDuelSession fromConnectedPeer(MultiplayerLaunchConfig launch,
                                                                 MultiplayerLanTransportV1.ConnectedPeer peer) {
        MultiplayerInGameDuelSession session = new MultiplayerInGameDuelSession(launch);
        session.peer = peer;
        session.state = State.CONNECTED;
        session.status = peer == null ? "Missing connected peer" : "Connected to " + peer.remoteEndpoint();
        if (peer == null) {
            session.state = State.ERROR;
            return session;
        }
        try {
            peer.setReadTimeoutMs(MultiplayerTimeoutsV1.MATCH_READ_TIMEOUT_MS);
        } catch (IOException ex) {
            session.markError("Could not configure multiplayer peer: " + ex.getMessage());
            return session;
        }
        session.worker = new Thread(() -> session.readLoop(peer),
                launch != null && launch.host() ? "mp-duel-host-peer" : "mp-duel-client-peer");
        session.worker.setDaemon(true);
        session.worker.start();
        return session;
    }

    static MultiplayerInGameDuelSession disconnectedForTests(String status) {
        return sessionForTests(State.DISCONNECTED, status);
    }

    static MultiplayerInGameDuelSession errorForTests(String status) {
        return sessionForTests(State.ERROR, status);
    }

    static MultiplayerInGameDuelSession connectedForTests(String status) {
        return sessionForTests(State.CONNECTED, status);
    }

    static MultiplayerInGameDuelSession matchEndedForTests(String status) {
        return sessionForTests(State.MATCH_ENDED, status);
    }

    private static MultiplayerInGameDuelSession sessionForTests(State state, String status) {
        MultiplayerInGameDuelSession session = new MultiplayerInGameDuelSession(null);
        session.running = false;
        session.state = state == null ? State.DISCONNECTED : state;
        session.status = (status == null || status.isBlank()) ? session.state.name() : status;
        return session;
    }

    public State state() {
        return state;
    }

    public String status() {
        return status;
    }

    public boolean connected() {
        return state == State.CONNECTED && peer != null;
    }

    public void requestReturnToLobby(String reason) {
        if (state == State.DISCONNECTED || state == State.ERROR || state == State.CLOSED) return;
        running = false;
        state = State.MATCH_ENDED;
        status = (reason == null || reason.isBlank()) ? "Match complete; returning to lobby" : reason.trim();
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
    }

    public boolean readyToReleasePeerForLobby() {
        Thread currentWorker = worker;
        return peer == null || currentWorker == null || !currentWorker.isAlive();
    }

    public MultiplayerLanTransportV1.ConnectedPeer releasePeerForLobby() {
        if (!readyToReleasePeerForLobby()) return null;
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        peer = null;
        worker = null;
        running = false;
        state = State.CLOSED;
        status = "Returned to lobby";
        if (current != null) {
            try {
                current.setReadTimeoutMs(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS);
            } catch (IOException ignored) {
                // The lobby reader will surface any transport failure on its own path.
            }
        }
        return current;
    }

    public MultiplayerLanTransportV1.DirectAddress boundAddress() {
        MultiplayerLanTransportV1.Host current = host;
        return current == null ? null : current.boundAddress();
    }

    public List<MultiplayerCommandGate.PlayerInputFrame> drainInputFrames() {
        ArrayList<MultiplayerCommandGate.PlayerInputFrame> out = new ArrayList<>();
        MultiplayerCommandGate.PlayerInputFrame frame;
        while ((frame = inboundInputFrames.poll()) != null) {
            out.add(frame);
        }
        return out;
    }

    public MultiplayerBattleSnapshot latestSnapshot() {
        return latestSnapshot.get();
    }

    public List<MultiplayerReplicationV1.AuthoritativeEvent> drainEvents() {
        ArrayList<MultiplayerReplicationV1.AuthoritativeEvent> out = new ArrayList<>();
        MultiplayerReplicationV1.AuthoritativeEvent event;
        while ((event = inboundEvents.poll()) != null) {
            out.add(event);
        }
        return out;
    }

    public long nextClientInputSequence() {
        return clientInputSequence.incrementAndGet();
    }

    public long nextClientTick() {
        return clientTick.incrementAndGet();
    }

    public long lastAckedInputSequence() {
        return lastAckedInputSequence;
    }

    public void sendClientInput(MultiplayerCommandGate.PlayerInputFrame frame) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (frame == null || current == null || !running) return;
        try {
            current.sendInputFrame(frame);
        } catch (IOException ex) {
            markError("Could not send input: " + ex.getMessage());
        }
    }

    public void sendInputAck(MultiplayerProtocolV1.InputAck ack) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (ack == null || current == null || !running) return;
        try {
            current.sendInputAck(ack);
        } catch (IOException ex) {
            markError("Could not send input acknowledgement: " + ex.getMessage());
        }
    }

    public void publishHostSnapshot(MultiplayerBattleSnapshot snapshot) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (snapshot == null || current == null || !running) return;
        try {
            current.sendSnapshot(snapshotSequence.incrementAndGet(), snapshot);
        } catch (IOException ex) {
            markError("Could not publish host snapshot: " + ex.getMessage());
        }
    }

    public void publishHostEvent(MultiplayerReplicationV1.AuthoritativeEvent event) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (event == null || current == null || !running) return;
        try {
            current.sendEvent(event);
        } catch (IOException ex) {
            markError("Could not publish host event: " + ex.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        State previous = state;
        state = State.CLOSED;
        status = "Multiplayer session closed";
        MultiplayerLanTransportV1.ConnectedPeer currentPeer = peer;
        peer = null;
        if (currentPeer != null) {
            try {
                currentPeer.sendDisconnect("Session closed");
            } catch (IOException ignored) {
                // Best-effort courtesy packet; socket close below is authoritative.
            }
            closeQuietly(currentPeer);
        }
        MultiplayerLanTransportV1.Host currentHost = host;
        host = null;
        if (currentHost != null) closeQuietly(currentHost);
        Thread currentWorker = worker;
        if (currentWorker != null && currentWorker.isAlive()) {
            currentWorker.interrupt();
        }
        if (previous == State.ERROR) {
            status = "Multiplayer session closed after error";
        }
    }

    private void startWorker() {
        worker = new Thread(launch.host() ? this::runHost : this::runClient,
                launch.host() ? "mp-duel-host" : "mp-duel-client");
        worker.setDaemon(true);
        worker.start();
    }

    private void runHost() {
        try {
            host = launch.loopbackOnly
                    ? MultiplayerLanTransportV1.bindLoopback(launch.port, launch.matchId, lifecycleLog)
                    : MultiplayerLanTransportV1.bindAny(launch.port, launch.matchId, lifecycleLog);
            state = State.LISTENING;
            status = "Waiting for client at " + host.boundAddress();
            while (running && peer == null) {
                MultiplayerLanTransportV1.TransportResult accepted = host.acceptOnce(
                        MultiplayerProtocolV1.localFingerprint(),
                        MultiplayerRulesV1.CLIENT_SLOT_ID,
                        launch.timeoutMs);
                if (!running) return;
                if (accepted.accepted() && accepted.peer() != null) {
                    peer = accepted.peer();
                    state = State.CONNECTED;
                    status = "Client connected from " + peer.remoteEndpoint();
                    readLoop(peer);
                    return;
                }
            }
        } catch (IOException ex) {
            markError("Could not host multiplayer duel: " + ex.getMessage());
        } finally {
            if (running && state != State.ERROR && state != State.CONNECTED) {
                state = State.DISCONNECTED;
                status = "Host session disconnected";
            }
        }
    }

    private void runClient() {
        MultiplayerLanTransportV1.DirectAddress address;
        try {
            address = MultiplayerLanTransportV1.parseDirectAddress(launch.resolvedDirectAddress());
        } catch (IllegalArgumentException ex) {
            markError(ex.getMessage());
            return;
        }
        MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                address,
                MultiplayerProtocolV1.localFingerprint(),
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                launch.matchId,
                lifecycleLog,
                launch.timeoutMs);
        if (!running) return;
        if (!connected.accepted() || connected.peer() == null) {
            markError(connected.reason());
            return;
        }
        peer = connected.peer();
        state = State.CONNECTED;
        status = "Connected to host " + address;
        readLoop(peer);
    }

    private void readLoop(MultiplayerLanTransportV1.ConnectedPeer connectedPeer) {
        long heartbeatTick = Math.max(connectedPeer.lastValidMessageTick(), connectedPeer.lastOutboundMessageTick());
        long timeoutTickAdvance = readTimeoutTicks(MultiplayerTimeoutsV1.MATCH_READ_TIMEOUT_MS);
        while (running && connectedPeer != null) {
            try {
                MultiplayerLanTransportV1.WireMessage message = connectedPeer.readNextMessage();
                if (message == null) continue;
                connectedPeer.noteValidTraffic(heartbeatTick);
                heartbeatTick = Math.max(heartbeatTick, message.hostTick());
                heartbeatTick = Math.max(heartbeatTick, connectedPeer.lastValidMessageTick());
                if (message.kind() == MultiplayerLanTransportV1.WireKind.CLIENT_INPUT && message.inputFrame() != null) {
                    if (connectedPeer.playerSlotId() > 0
                            && message.inputFrame().slotId() != connectedPeer.playerSlotId()) {
                        markError("Input player slot does not match connection");
                        return;
                    }
                    inboundInputFrames.add(message.inputFrame());
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.FULL_SNAPSHOT && message.snapshot() != null) {
                    latestSnapshot.set(message.snapshot());
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.AUTHORITATIVE_EVENT && message.event() != null) {
                    inboundEvents.add(message.event());
                    if (message.event().type() == MultiplayerReplicationV1.EventType.VICTORY_DECLARED) {
                        requestReturnToLobby(message.event().detail());
                        return;
                    }
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.INPUT_ACK && message.inputAck() != null) {
                    lastAckedInputSequence = Math.max(lastAckedInputSequence, message.inputAck().inputSequence());
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.DISCONNECT) {
                    state = State.DISCONNECTED;
                    status = message.text().isBlank() ? "Peer disconnected" : message.text();
                    return;
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.ERROR) {
                    status = message.text().isBlank() ? status : message.text();
                }
            } catch (IOException ex) {
                if (ex instanceof SocketTimeoutException) {
                    heartbeatTick += timeoutTickAdvance;
                    if (connectedPeer.markDisconnectedIfTimedOut(heartbeatTick)) {
                        state = State.DISCONNECTED;
                        status = "Peer timed out";
                        return;
                    }
                    try {
                        connectedPeer.sendHeartbeatIfIdle(heartbeatTick);
                    } catch (IOException heartbeatEx) {
                        if (running) markError("Multiplayer connection lost: " + heartbeatEx.getMessage());
                        return;
                    }
                    continue;
                }
                if (running) markError("Multiplayer connection lost: " + ex.getMessage());
                return;
            } catch (IllegalArgumentException ex) {
                if (running) markError("Malformed multiplayer message: " + ex.getMessage());
                return;
            }
        }
    }

    private void markError(String message) {
        if (!running && state == State.CLOSED) return;
        state = State.ERROR;
        status = (message == null || message.isBlank()) ? "Multiplayer session error" : message.trim();
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Close is best-effort during shutdown/error paths.
        }
    }

    private static long readTimeoutTicks(int timeoutMs) {
        return Math.max(1L, Math.round(MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE * (timeoutMs / 1000.0)));
    }
}
