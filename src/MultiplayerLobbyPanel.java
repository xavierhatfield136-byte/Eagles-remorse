import app.config.MultiplayerLaunchConfig;
import app.config.MultiplayerMissionChoice;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

/** In-app V1 multiplayer lobby that gates match start before handing off to GamePanel. */
public final class MultiplayerLobbyPanel extends JPanel {
    private static final int VIEW_W = 1280;
    private static final int VIEW_H = 720;

    private final MultiplayerLaunchConfig launch;
    private final Runnable exitToMenu;
    private final Runnable toggleFullscreen;
    private final boolean hostRole;
    private final MultiplayerLobbyV1 lobby;
    private final JComboBox<MultiplayerMissionChoice> missionSelector;
    private final JTextField seedField;
    private final JComboBox<MapChoice> mapSelector;
    private final JLabel seedValueLabel;
    private final JLabel mapValueLabel;
    private final JLabel assignmentLabel;
    private final JLabel teamsLabel;
    private final JLabel compatibilityLabel;
    private final JLabel statusLabel;
    private final JLabel readinessLabel;
    private final JLabel addressLabel;
    private final JButton readyButton;
    private final JButton startButton;
    private final JButton leaveButton;
    private final Timer uiTimer;
    private final JPanel lobbyRoot;
    private MultiplayerNetworkWriteQueue networkWrites;

    private volatile boolean running;
    private volatile MultiplayerLanTransportV1.Host hostSocket;
    private volatile MultiplayerLanTransportV1.ConnectedPeer peer;
    private volatile Thread worker;
    private volatile MultiplayerLobbyWireV1.Snapshot latestClientSnapshot;
    private volatile boolean localReady;
    private volatile boolean matchLaunching;
    private volatile String selectedMissionId;
    private volatile long selectedSeed;
    private volatile int selectedWorldW;
    private volatile int selectedWorldH;
    private volatile MultiplayerLobbyWireV1.Snapshot pendingLockedSnapshot;
    private volatile String pendingLockedLaunchSpecDigest = "";
    private volatile GameContext pendingGameContext;
    private volatile long pendingMatchStartTick;
    private volatile long matchLoadingDeadlineNs;
    private volatile String statusText = "Lobby starting";
    private volatile GamePanel activeGamePanel;
    private volatile boolean returnedToSetup;
    private volatile MultiplayerLanTransportV1.ConnectedPeer lastResumedPeerForTests;

    public MultiplayerLobbyPanel(MultiplayerLaunchConfig launch, Runnable exitToMenu, Runnable toggleFullscreen) {
        this(launch, exitToMenu, toggleFullscreen, true);
    }

    MultiplayerLobbyPanel(MultiplayerLaunchConfig launch, Runnable exitToMenu,
                          Runnable toggleFullscreen, boolean startNetworking) {
        this.launch = launch == null
                ? MultiplayerLaunchConfig.host(MultiplayerLanTransportV1.DEFAULT_PORT, "127.0.0.1")
                : launch;
        this.exitToMenu = exitToMenu;
        this.toggleFullscreen = toggleFullscreen;
        this.hostRole = this.launch.host();
        this.networkWrites = new MultiplayerNetworkWriteQueue(
                this.hostRole ? "mp-lobby-host-write" : "mp-lobby-client-write");
        this.selectedMissionId = MultiplayerMissionChoice.fromMissionId(this.launch.missionId).missionId();
        this.lobby = new MultiplayerLobbyV1(
                this.launch.hostPlayerName,
                this.launch.clientPlayerName,
                ShipRole.FRIGATE,
                ShipRole.FRIGATE);
        MissionLaunchSpec initialSpec = MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                this.launch.withMissionId(selectedMissionId), System.nanoTime());
        this.selectedSeed = this.launch.missionSeed > 0L ? this.launch.missionSeed : initialSpec.seed();
        this.selectedWorldW = this.launch.missionWorldW > 0 ? this.launch.missionWorldW : initialSpec.worldW();
        this.selectedWorldH = this.launch.missionWorldH > 0 ? this.launch.missionWorldH : initialSpec.worldH();
        applySelectedMissionToLobby(false);

        setName("multiplayerLobbyPanel");
        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setBackground(new Color(5, 10, 18));
        setLayout(new BorderLayout());

        JPanel root = new JPanel();
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(72, 96, 72, 96));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel title = label(hostRole ? "Multiplayer Host Lobby" : "Multiplayer Client Lobby", 32, Font.BOLD);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(18));

        JPanel info = card();
        info.setLayout(new GridLayout(0, 1, 0, 10));
        missionSelector = new JComboBox<>(MultiplayerMissionChoice.values());
        missionSelector.setName("multiplayerLobbyMissionSelector");
        missionSelector.setSelectedItem(MultiplayerMissionChoice.fromMissionId(selectedMissionId));
        missionSelector.setEnabled(hostRole);
        missionSelector.addActionListener(e -> {
            String previousMissionId = selectedMissionId;
            selectedMissionId = selectedMissionId();
            boolean missionChanged = !selectedMissionId.equals(previousMissionId);
            if (hostRole && missionChanged) {
                localReady = false;
                applySelectedMissionToLobby(true);
                lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, false);
                sendHostSnapshot("Mission updated");
            }
            refreshUi();
        });
        info.add(row("Mission", missionSelector));
        seedField = new JTextField(String.valueOf(selectedSeed), 14);
        seedField.setName("multiplayerLobbySeedField");
        seedField.setEnabled(hostRole);
        seedField.addActionListener(e -> hostUpdateSeedAndMap());
        seedValueLabel = valueLabel(String.valueOf(selectedSeed));
        seedValueLabel.setName("multiplayerLobbySeedValueLabel");
        info.add(row("Seed", hostRole ? seedField : seedValueLabel));
        mapSelector = new JComboBox<>(MapChoice.values());
        mapSelector.setName("multiplayerLobbyMapSelector");
        mapSelector.setEnabled(hostRole);
        mapSelector.setSelectedItem(MapChoice.closest(selectedWorldW, selectedWorldH));
        mapSelector.addActionListener(e -> hostUpdateSeedAndMap());
        mapValueLabel = valueLabel(mapLabel(selectedWorldW, selectedWorldH));
        mapValueLabel.setName("multiplayerLobbyMapValueLabel");
        info.add(row("Map", hostRole ? mapSelector : mapValueLabel));
        assignmentLabel = valueLabel("");
        assignmentLabel.setName("multiplayerLobbyAssignmentLabel");
        info.add(row("Assigned Ship", assignmentLabel));
        teamsLabel = valueLabel("");
        teamsLabel.setName("multiplayerLobbyTeamsLabel");
        info.add(row("Teams", teamsLabel));
        compatibilityLabel = valueLabel("");
        compatibilityLabel.setName("multiplayerLobbyCompatibilityLabel");
        info.add(row("Compatibility", compatibilityLabel));
        addressLabel = valueLabel("");
        addressLabel.setName("multiplayerLobbyAddressLabel");
        info.add(row(hostRole ? "Lobby Address" : "Host Address", addressLabel));
        readinessLabel = valueLabel("");
        info.add(row("Readiness", readinessLabel));
        statusLabel = valueLabel(statusText);
        statusLabel.setName("multiplayerLobbyStatusLabel");
        info.add(row("Status", statusLabel));
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(info);
        root.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new GridLayout(1, 3, 12, 0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(740, 48));
        readyButton = button("Ready");
        readyButton.setName("multiplayerLobbyReadyButton");
        readyButton.addActionListener(e -> toggleReady());
        startButton = button(hostRole ? "Start Match" : "Waiting");
        startButton.setName("multiplayerLobbyStartButton");
        startButton.addActionListener(e -> hostStartMatch());
        leaveButton = button("Leave");
        leaveButton.setName("multiplayerLobbyLeaveButton");
        leaveButton.addActionListener(e -> {
            shutdown();
            if (exitToMenu != null) exitToMenu.run();
        });
        actions.add(readyButton);
        actions.add(startButton);
        actions.add(leaveButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(actions);
        lobbyRoot = root;
        add(lobbyRoot, BorderLayout.CENTER);

        uiTimer = new Timer(200, e -> refreshUi());
        uiTimer.setCoalesce(true);
        uiTimer.start();
        refreshUi();
        if (startNetworking) startNetworking();
    }

    public void shutdown() {
        uiTimer.stop();
        if (activeGamePanel != null) {
            activeGamePanel.shutdown();
            activeGamePanel = null;
        }
        closeLobbyNetworking(false);
    }

    public void requestGameFocus() {
        if (activeGamePanel != null) {
            activeGamePanel.requestFocusInWindow();
        } else {
            requestFocusInWindow();
        }
    }

    boolean startEnabledForTests() {
        return startButton.isEnabled();
    }

    private void startNetworking() {
        running = true;
        worker = new Thread(hostRole ? this::runHostLobby : this::runClientLobby,
                hostRole ? "mp-lobby-host" : "mp-lobby-client");
        worker.setDaemon(true);
        worker.start();
    }

    private void runHostLobby() {
        try {
            hostSocket = launch.loopbackOnly
                    ? MultiplayerLanTransportV1.bindLoopback(launch.port, launch.matchId, null)
                    : MultiplayerLanTransportV1.bindAny(launch.port, launch.matchId, null);
            setNetworkStatus("Waiting for client");
            while (running && peer == null) {
                MultiplayerLanTransportV1.TransportResult accepted = hostSocket.acceptOnce(
                        MultiplayerProtocolV1.localFingerprint(),
                        MultiplayerRulesV1.CLIENT_SLOT_ID,
                        Math.min(1_000, launch.timeoutMs));
                if (!running) return;
                if (accepted.accepted() && accepted.peer() != null) {
                    peer = accepted.peer();
                    peer.setReadTimeoutMs(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS);
                    setNetworkStatus("Client connected");
                    lobby.recordCompatibilityResult(true, accepted.reason());
                    lobby.join(launch.clientPlayerName);
                    sendHostSnapshot("Client joined");
                    hostReadLoop(peer);
                } else if (!accepted.accepted() && accepted.reason() != null
                        && !accepted.reason().isBlank()
                        && !"Accept timed out".equalsIgnoreCase(accepted.reason())) {
                    lobby.recordCompatibilityResult(false, accepted.reason());
                }
            }
        } catch (IOException ex) {
            if (running) setNetworkStatus("Host lobby failed: " + ex.getMessage());
        }
    }

    private void hostReadLoop(MultiplayerLanTransportV1.ConnectedPeer connectedPeer) {
        long heartbeatTick = Math.max(connectedPeer.lastValidMessageTick(), connectedPeer.lastOutboundMessageTick());
        long timeoutTickAdvance = readTimeoutTicks(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS);
        while (running && activeGamePanel == null && connectedPeer != null) {
            try {
                MultiplayerLanTransportV1.WireMessage message = connectedPeer.readNextMessage();
                connectedPeer.noteValidTraffic(heartbeatTick);
                heartbeatTick = Math.max(heartbeatTick, message.hostTick());
                heartbeatTick = Math.max(heartbeatTick, connectedPeer.lastValidMessageTick());
                if (message.kind() == MultiplayerLanTransportV1.WireKind.LOBBY_COMMAND) {
                    handleHostLobbyCommand(MultiplayerLobbyWireV1.decodeCommand(message.text()));
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.DISCONNECT) {
                    if (matchLaunching) {
                        returnHostToLobbyAfterLoadingFailure(
                                "Client disconnected during match loading; returned to lobby");
                        lobby.clientDisconnected("client_disconnected_during_loading");
                        peer = null;
                        return;
                    }
                    setNetworkStatus("Client left lobby");
                    peer = null;
                    lobby.clientDisconnected("client_left_lobby");
                    return;
                }
            } catch (IOException ex) {
                if (ex instanceof SocketTimeoutException) {
                    heartbeatTick += timeoutTickAdvance;
                    if (connectedPeer.markDisconnectedIfTimedOut(heartbeatTick)) {
                        setNetworkStatus("Client timed out");
                        peer = null;
                        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, false);
                        return;
                    }
                    try {
                        connectedPeer.sendHeartbeatIfIdle(heartbeatTick);
                    } catch (IOException heartbeatEx) {
                        if (matchLaunching) {
                            returnHostToLobbyAfterLoadingFailure(
                                    "Client disconnected during match loading; returned to lobby");
                            lobby.clientDisconnected("client_disconnected_during_loading");
                            peer = null;
                            return;
                        }
                        if (running && !matchLaunching) {
                            setNetworkStatus("Client disconnected: " + heartbeatEx.getMessage());
                        }
                        return;
                    }
                    continue;
                }
                if (matchLaunching) {
                    returnHostToLobbyAfterLoadingFailure(
                            "Client disconnected during match loading; returned to lobby");
                    lobby.clientDisconnected("client_disconnected_during_loading");
                    peer = null;
                    return;
                }
                if (running && !matchLaunching) setNetworkStatus("Client disconnected: " + ex.getMessage());
                return;
            }
        }
    }

    private void runClientLobby() {
        try {
            MultiplayerLanTransportV1.DirectAddress address =
                    MultiplayerLanTransportV1.parseDirectAddress(launch.resolvedDirectAddress());
            setNetworkStatus("Connecting to " + address);
            MultiplayerLanTransportV1.TransportResult connected = MultiplayerLanTransportV1.connect(
                    address,
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    launch.matchId,
                    null,
                    launch.timeoutMs);
            if (!running) return;
            if (!connected.accepted() || connected.peer() == null) {
                setNetworkStatus(connected.reason());
                return;
            }
            peer = connected.peer();
            peer.setReadTimeoutMs(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS);
            setNetworkStatus("Connected to host");
            sendClientLobbyCommand(
                    new MultiplayerLobbyWireV1.Command(
                            MultiplayerLobbyWireV1.CommandType.HELLO,
                            false,
                            0L,
                            launch.clientPlayerName),
                    "Could not send hello");
            clientReadLoop(peer);
        } catch (IOException | IllegalArgumentException ex) {
            if (running) setNetworkStatus("Join failed: " + ex.getMessage());
        }
    }

    private void clientReadLoop(MultiplayerLanTransportV1.ConnectedPeer connectedPeer) {
        long heartbeatTick = Math.max(connectedPeer.lastValidMessageTick(), connectedPeer.lastOutboundMessageTick());
        long timeoutTickAdvance = readTimeoutTicks(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS);
        while (running && activeGamePanel == null && connectedPeer != null) {
            try {
                MultiplayerLanTransportV1.WireMessage message = connectedPeer.readNextMessage();
                connectedPeer.noteValidTraffic(heartbeatTick);
                heartbeatTick = Math.max(heartbeatTick, message.hostTick());
                heartbeatTick = Math.max(heartbeatTick, connectedPeer.lastValidMessageTick());
                if (message.kind() == MultiplayerLanTransportV1.WireKind.LOBBY_STATE) {
                    String kind = MultiplayerLobbyWireV1.payloadKind(message.text());
                    if ("prepare".equals(kind)) {
                        handleClientPrepareMatch(MultiplayerLobbyWireV1.decodePrepareMatch(message.text()));
                    } else if ("begin".equals(kind)) {
                        handleClientBeginMatch(MultiplayerLobbyWireV1.decodeBeginMatch(message.text()));
                        return;
                    } else {
                        MultiplayerLobbyWireV1.Snapshot snapshot =
                                MultiplayerLobbyWireV1.decodeSnapshot(message.text());
                        acceptClientSnapshot(snapshot);
                    }
                } else if (message.kind() == MultiplayerLanTransportV1.WireKind.DISCONNECT) {
                    returnClientToSetupFromNetwork("Host closed lobby");
                    return;
                }
            } catch (IOException ex) {
                if (ex instanceof SocketTimeoutException) {
                    heartbeatTick += timeoutTickAdvance;
                    if (connectedPeer.markDisconnectedIfTimedOut(heartbeatTick)) {
                        returnClientToSetupFromNetwork("Host timed out");
                        return;
                    }
                    try {
                        connectedPeer.sendHeartbeatIfIdle(heartbeatTick);
                    } catch (IOException heartbeatEx) {
                        if (running && !matchLaunching) {
                            returnClientToSetupFromNetwork("Host disconnected: " + heartbeatEx.getMessage());
                        }
                        return;
                    }
                    continue;
                }
                if (running && !matchLaunching) returnClientToSetupFromNetwork("Host disconnected: " + ex.getMessage());
                return;
            }
        }
    }

    private void handleHostLobbyCommand(MultiplayerLobbyWireV1.Command command) {
        if (command == null) return;
        if (command.type() == MultiplayerLobbyWireV1.CommandType.HELLO) {
            MultiplayerLobbyV1.LobbyResult result = lobby.setClientDisplayName(command.playerName());
            setNetworkStatus(result.reason());
            sendHostSnapshot(statusText);
            return;
        }
        if (command.type() == MultiplayerLobbyWireV1.CommandType.LEAVE) {
            setNetworkStatus("Client left lobby");
            lobby.clientDisconnected("client_left_lobby");
            sendHostSnapshot(statusText);
            return;
        }
        if (command.type() == MultiplayerLobbyWireV1.CommandType.MATCH_LOADED) {
            handleHostMatchLoaded(command);
            return;
        }
        MultiplayerLobbyV1.LobbyResult result = lobby.setReady(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                command.ready(),
                command.acceptedRevision());
        setNetworkStatus(result.reason());
        sendHostSnapshot(statusText);
    }

    private void toggleReady() {
        localReady = !localReady;
        if (hostRole) {
            MultiplayerLobbyV1.LobbyResult result = lobby.setReady(
                    MultiplayerRulesV1.HOST_SLOT_ID, localReady, lobby.revision());
            statusText = result.reason();
            sendHostSnapshot(statusText);
        } else {
            MultiplayerLobbyWireV1.Snapshot snapshot = latestClientSnapshot;
            long revision = snapshot == null ? 0L : snapshot.revision();
            MultiplayerLanTransportV1.ConnectedPeer current = peer;
            if (current != null) {
                sendClientLobbyCommand(
                        new MultiplayerLobbyWireV1.Command(
                                MultiplayerLobbyWireV1.CommandType.READY, localReady, revision),
                        "Could not send ready state");
                statusText = localReady ? "Ready queued" : "Unready queued";
            }
        }
        refreshUi();
    }

    private void hostStartMatch() {
        hostUpdateSeedAndMap();
        if (!hostRole || !canHostStart()) return;
        MultiplayerLobbyV1.LobbyResult hostReady = lobby.setReady(
                MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        localReady = hostReady.accepted();
        if (!hostReady.accepted()) {
            statusText = hostReady.reason();
            refreshUi();
            return;
        }
        MultiplayerMissionValidator.ValidationResult missionValidation = currentMissionValidation();
        if (!missionValidation.accepted()) {
            statusText = missionValidation.message();
            lobby.recordCompatibilityResult(false, missionValidation.message());
            refreshUi();
            return;
        }
        lobby.recordCompatibilityResult(true, "Mission compatible");
        MultiplayerLobbyV1.LobbyResult lock = lobby.lockForCountdown();
        if (!lock.accepted()) {
            statusText = lock.reason();
            refreshUi();
            return;
        }
        lobby.startLoading();
        matchLaunching = true;
        pendingLockedSnapshot = hostSnapshot("Preparing match");
        pendingLockedLaunchSpecDigest = lockedLaunchSpecDigest(pendingLockedSnapshot);
        pendingGameContext = prepareGameContext(pendingLockedSnapshot);
        matchLoadingDeadlineNs = System.nanoTime()
                + Math.max(MultiplayerTimeoutsV1.MATCH_LOADING_TIMEOUT_MS, launch.timeoutMs) * 1_000_000L;
        statusText = "Waiting for client to load match";
        sendHostSnapshot(statusText);
        sendPrepareMatch(pendingLockedSnapshot, pendingLockedLaunchSpecDigest);
    }

    private void handleHostMatchLoaded(MultiplayerLobbyWireV1.Command command) {
        MultiplayerLobbyWireV1.Snapshot lockedSnapshot = pendingLockedSnapshot;
        String digest = pendingLockedLaunchSpecDigest;
        if (lockedSnapshot == null || digest.isBlank()) {
            setNetworkStatus("Unexpected match-loaded response");
            return;
        }
        if (!command.loadAccepted()) {
            returnHostToLobbyAfterLoadingFailure(command.loadStatus().isBlank()
                    ? "Client failed to load match"
                    : "Client failed to load match: " + command.loadStatus());
            sendHostSnapshot(statusText);
            return;
        }
        if (!lockedSnapshot.matchId().equals(command.matchId())
                || !digest.equals(command.lockedLaunchSpecDigest())) {
            returnHostToLobbyAfterLoadingFailure("Client loaded a different match specification; returned to lobby");
            sendHostSnapshot(statusText);
            return;
        }
        lobby.startMatch();
        pendingMatchStartTick = 1L;
        matchLoadingDeadlineNs = 0L;
        sendBeginMatch(lockedSnapshot, digest, pendingMatchStartTick);
        setNetworkStatus("Starting match");
        launchMatchOnEventThread(lockedSnapshot);
    }

    private void handleClientPrepareMatch(MultiplayerLobbyWireV1.PrepareMatch prepare) {
        MultiplayerLobbyWireV1.Snapshot snapshot = latestClientSnapshot;
        if (snapshot == null) {
            sendMatchLoaded(prepare, false, "Missing locked lobby snapshot");
            setNetworkStatus("Missing locked lobby snapshot");
            return;
        }
        String expectedDigest = lockedLaunchSpecDigest(snapshot);
        boolean valid = prepare != null
                && prepare.matchId().equals(snapshot.matchId())
                && prepare.lockedConfigRevision() == snapshot.lockedConfigRevision()
                && prepare.lockedLaunchSpecDigest().equals(expectedDigest);
        if (!valid) {
            sendMatchLoaded(prepare, false, "Locked match specification mismatch");
            setNetworkStatus("Locked match specification mismatch");
            return;
        }
        pendingLockedSnapshot = snapshot;
        pendingLockedLaunchSpecDigest = expectedDigest;
        pendingGameContext = prepareGameContext(snapshot);
        matchLaunching = true;
        setNetworkStatus("Match loaded");
        sendMatchLoaded(prepare, true, "Loaded");
    }

    private void handleClientBeginMatch(MultiplayerLobbyWireV1.BeginMatch begin) {
        MultiplayerLobbyWireV1.Snapshot snapshot = pendingLockedSnapshot;
        String digest = pendingLockedLaunchSpecDigest;
        if (begin == null || snapshot == null
                || !begin.matchId().equals(snapshot.matchId())
                || !begin.lockedLaunchSpecDigest().equals(digest)) {
            setNetworkStatus("Begin match did not match prepared specification");
            matchLaunching = false;
            return;
        }
        pendingMatchStartTick = begin.startTick();
        launchMatchOnEventThread(snapshot);
    }

    private void launchMatchOnEventThread(MultiplayerLobbyWireV1.Snapshot snapshot) {
        Runnable task = () -> {
            try {
                launchMatch(snapshot);
            } catch (RuntimeException ex) {
                statusText = "Could not enter match: " + ex.getMessage();
                matchLaunching = false;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void launchMatch(MultiplayerLobbyWireV1.Snapshot lockedSnapshot) {
        if (activeGamePanel != null) return;
        MultiplayerLanTransportV1.ConnectedPeer matchPeer = peer;
        if (matchPeer == null) {
            statusText = "Cannot start without a connected peer";
            matchLaunching = false;
            return;
        }
        if (lockedSnapshot == null) lockedSnapshot = hostSnapshot("Starting match");
        selectedMissionId = MultiplayerMissionChoice.fromMissionId(lockedSnapshot.missionId()).missionId();
        selectedSeed = lockedSnapshot.seed();
        selectedWorldW = lockedSnapshot.worldW();
        selectedWorldH = lockedSnapshot.worldH();
        uiTimer.stop();
        closeLobbyNetworking(true);
        MultiplayerLaunchConfig matchLaunch = launch.withMatchId(lockedSnapshot.matchId()).withMissionSettings(
                        selectedMissionId, selectedSeed, selectedWorldW, selectedWorldH)
                .withPlayerNames(lockedSnapshot.hostName(), lockedSnapshot.clientName());
        GameContext ctx = pendingGameContext == null
                ? MultiplayerPlayableDuelContextFactory.create(matchLaunch)
                : pendingGameContext;
        ctx.multiplayerLobbyId = lockedSnapshot.lobbyId();
        ctx.multiplayerLockedConfigRevision = lockedSnapshot.lockedConfigRevision();
        ctx.multiplayerMatchStartTick = pendingMatchStartTick;
        ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.fromConnectedPeer(matchLaunch, matchPeer);
        pendingGameContext = null;
        matchLoadingDeadlineNs = 0L;
        activeGamePanel = new GamePanel(ctx, () -> returnFromActiveMatch(ctx), toggleFullscreen);
        removeAll();
        setLayout(new BorderLayout());
        add(activeGamePanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> activeGamePanel.requestFocusInWindow());
    }

    private GameContext prepareGameContext(MultiplayerLobbyWireV1.Snapshot snapshot) {
        MultiplayerLaunchConfig matchLaunch = launch.withMatchId(snapshot.matchId()).withMissionSettings(
                        snapshot.missionId(), snapshot.seed(), snapshot.worldW(), snapshot.worldH())
                .withPlayerNames(snapshot.hostName(), snapshot.clientName());
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(matchLaunch);
        ctx.multiplayerLobbyId = snapshot.lobbyId();
        ctx.multiplayerLockedConfigRevision = snapshot.lockedConfigRevision();
        return ctx;
    }

    private void closeLobbyNetworking(boolean transferPeer) {
        running = false;
        MultiplayerLanTransportV1.Host currentHost = hostSocket;
        hostSocket = null;
        if (currentHost != null) {
            try {
                currentHost.close();
            } catch (IOException ignored) {
            }
        }
        if (!transferPeer) {
            MultiplayerLanTransportV1.ConnectedPeer currentPeer = peer;
            peer = null;
            if (currentPeer != null) {
                if (!hostRole) {
                    networkWrites.submitAndWait(() -> currentPeer.sendLobbyCommand(
                            MultiplayerLobbyWireV1.encodeCommand(
                                    new MultiplayerLobbyWireV1.Command(
                                            MultiplayerLobbyWireV1.CommandType.LEAVE,
                                            false,
                                            latestClientSnapshot == null ? 0L : latestClientSnapshot.revision()))),
                            500L);
                }
                networkWrites.submitAndWait(() -> currentPeer.sendDisconnect("Leaving lobby"), 500L);
                try {
                    currentPeer.close();
                } catch (IOException ignored) {
                }
            }
        }
        networkWrites.close();
        Thread currentWorker = worker;
        if (currentWorker != null && currentWorker != Thread.currentThread()) {
            try {
                currentWorker.join(800);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
    }

    private void returnFromActiveMatch(GameContext matchContext) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> returnFromActiveMatch(matchContext));
            return;
        }
        MultiplayerInGameDuelSession session = matchContext == null ? null : matchContext.multiplayerInGameSession;
        MultiplayerInGameDuelSession.State state = session == null ? MultiplayerInGameDuelSession.State.CLOSED : session.state();
        String sessionStatus = session == null ? "" : session.status();
        boolean normalCompletion = matchContext != null && matchContext.gameOver
                && state != MultiplayerInGameDuelSession.State.DISCONNECTED
                && state != MultiplayerInGameDuelSession.State.ERROR;
        MultiplayerLanTransportV1.ConnectedPeer retainedPeer =
                normalCompletion && session != null ? session.releasePeerForLobby() : null;
        boolean failedTransport = state == MultiplayerInGameDuelSession.State.DISCONNECTED
                || state == MultiplayerInGameDuelSession.State.ERROR;
        statusText = activeMatchReturnStatus(state, sessionStatus);

        GamePanel panel = activeGamePanel;
        activeGamePanel = null;
        if (panel != null) {
            panel.shutdown();
        }

        matchLaunching = false;
        clearPendingMatchLoad();
        if (hostRole) {
            if (lobby.state() == MultiplayerLobbyV1.LobbyState.IN_MATCH
                    || lobby.state() == MultiplayerLobbyV1.LobbyState.POST_MATCH
                    || lobby.locked()) {
                if (normalCompletion) {
                    lobby.endMatch();
                }
                lobby.returnToLobby();
            }
            if (!normalCompletion && (failedTransport || state == MultiplayerInGameDuelSession.State.CLOSED)) {
                lobby.clientDisconnected(statusText);
            }
        }

        showLobbyViewAfterMatch();
        if (retainedPeer != null) {
            resumeLobbyNetworkingWithPeer(retainedPeer, normalCompletion);
        }
    }

    private String activeMatchReturnStatus(MultiplayerInGameDuelSession.State state, String sessionStatus) {
        if (sessionStatus != null && !sessionStatus.isBlank()
                && (state == MultiplayerInGameDuelSession.State.DISCONNECTED
                || state == MultiplayerInGameDuelSession.State.ERROR)) {
            return sessionStatus.trim();
        }
        if (state == MultiplayerInGameDuelSession.State.ERROR) {
            return "Multiplayer transport failed; returned to setup";
        }
        if (state == MultiplayerInGameDuelSession.State.DISCONNECTED) {
            return "Peer disconnected; returned to setup";
        }
        if (state == MultiplayerInGameDuelSession.State.MATCH_ENDED) {
            return "Match complete; returned to lobby";
        }
        return hostRole ? "Match closed; returned to lobby" : "Left match; returned to Multiplayer Setup";
    }

    private void showLobbyViewAfterMatch() {
        removeAll();
        setLayout(new BorderLayout());
        add(lobbyRoot, BorderLayout.CENTER);
        if (!uiTimer.isRunning()) {
            uiTimer.start();
        }
        refreshUi();
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void resumeLobbyNetworkingWithPeer(MultiplayerLanTransportV1.ConnectedPeer retainedPeer,
                                               boolean normalCompletion) {
        if (retainedPeer == null) return;
        networkWrites = new MultiplayerNetworkWriteQueue(
                hostRole ? "mp-lobby-host-write" : "mp-lobby-client-write");
        peer = retainedPeer;
        lastResumedPeerForTests = retainedPeer;
        running = true;
        returnedToSetup = false;
        matchLaunching = false;
        localReady = false;
        try {
            retainedPeer.setReadTimeoutMs(MultiplayerTimeoutsV1.LOBBY_READ_TIMEOUT_MS);
        } catch (IOException ex) {
            setNetworkStatus("Could not resume lobby connection: " + ex.getMessage());
            return;
        }
        worker = new Thread(() -> {
            if (hostRole) {
                if (normalCompletion) {
                    sendHostSnapshot("Match complete; returned to lobby");
                }
                hostReadLoop(retainedPeer);
            } else {
                clientReadLoop(retainedPeer);
            }
        }, hostRole ? "mp-lobby-host-return" : "mp-lobby-client-return");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean acceptClientSnapshot(MultiplayerLobbyWireV1.Snapshot snapshot) {
        if (snapshot == null) return false;
        MultiplayerLobbyWireV1.Snapshot current = latestClientSnapshot;
        if (current != null && snapshot.revision() < current.revision()) {
            return false;
        }
        latestClientSnapshot = snapshot;
        selectedMissionId = snapshot.missionId();
        selectedSeed = snapshot.seed();
        selectedWorldW = snapshot.worldW();
        selectedWorldH = snapshot.worldH();
        if (!snapshot.status().isBlank()) {
            statusText = snapshot.status();
        }
        refreshUiOnEventThread();
        return true;
    }

    boolean acceptClientSnapshotForTests(MultiplayerLobbyWireV1.Snapshot snapshot) {
        return acceptClientSnapshot(snapshot);
    }

    void handleHostLobbyCommandForTests(MultiplayerLobbyWireV1.Command command) {
        handleHostLobbyCommand(command);
    }

    MultiplayerLobbyWireV1.Snapshot latestClientSnapshotForTests() {
        return latestClientSnapshot;
    }

    MultiplayerLobbyV1 lobbyForTests() {
        return lobby;
    }

    String selectedMissionIdForTests() {
        return selectedMissionId;
    }

    String statusTextForTests() {
        return statusText;
    }

    boolean activeGamePanelForTests() {
        return activeGamePanel != null;
    }

    Thread workerForTests() {
        return worker;
    }

    MultiplayerLanTransportV1.ConnectedPeer peerForTests() {
        return peer;
    }

    MultiplayerLanTransportV1.ConnectedPeer lastResumedPeerForTests() {
        return lastResumedPeerForTests;
    }

    long pendingMatchStartTickForTests() {
        return pendingMatchStartTick;
    }

    void beginMatchLoadingTimeoutForTests(long deadlineNs) {
        matchLaunching = true;
        pendingLockedSnapshot = hostSnapshot("Preparing match");
        pendingLockedLaunchSpecDigest = "digest-for-test";
        matchLoadingDeadlineNs = deadlineNs;
    }

    void cancelMatchLoadingForTests(String status) {
        returnHostToLobbyAfterLoadingFailure(status);
    }

    void checkMatchLoadingTimeoutForTests() {
        checkMatchLoadingTimeout();
    }

    GamePanel installActiveGamePanelForTests(GameContext ctx) {
        activeGamePanel = new GamePanel(ctx, () -> returnFromActiveMatch(ctx), () -> {});
        removeAll();
        setLayout(new BorderLayout());
        add(activeGamePanel, BorderLayout.CENTER);
        if (uiTimer.isRunning()) {
            uiTimer.stop();
        }
        revalidate();
        repaint();
        return activeGamePanel;
    }

    private void sendHostSnapshot(String status) {
        if (!hostRole) return;
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (current == null) return;
        enqueueNetworkWrite(
                () -> current.sendLobbyState(MultiplayerLobbyWireV1.encodeSnapshot(hostSnapshot(status))),
                "Could not publish lobby state");
    }

    private void sendPrepareMatch(MultiplayerLobbyWireV1.Snapshot snapshot, String digest) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (snapshot == null || current == null) return;
        enqueueNetworkWrite(
                () -> current.sendLobbyState(MultiplayerLobbyWireV1.encodePrepareMatch(
                        new MultiplayerLobbyWireV1.PrepareMatch(
                                snapshot.matchId(),
                                digest,
                                snapshot.lockedConfigRevision()))),
                "Could not send prepare-match");
    }

    private void sendBeginMatch(MultiplayerLobbyWireV1.Snapshot snapshot, String digest, long startTick) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (snapshot == null || current == null) return;
        boolean sent = networkWrites.submitAndWait(
                () -> current.sendLobbyState(MultiplayerLobbyWireV1.encodeBeginMatch(
                        new MultiplayerLobbyWireV1.BeginMatch(snapshot.matchId(), digest, startTick))),
                1_000L);
        if (!sent) {
            Throwable failure = networkWrites.lastFailure();
            setNetworkStatus("Could not send begin-match: "
                    + (failure == null || failure.getMessage() == null ? "write failed" : failure.getMessage()));
        }
    }

    private void sendMatchLoaded(MultiplayerLobbyWireV1.PrepareMatch prepare,
                                 boolean accepted,
                                 String status) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (prepare == null || current == null) return;
        enqueueNetworkWrite(
                () -> current.sendLobbyCommand(MultiplayerLobbyWireV1.encodeCommand(
                        new MultiplayerLobbyWireV1.Command(
                                MultiplayerLobbyWireV1.CommandType.MATCH_LOADED,
                                false,
                                prepare.lockedConfigRevision(),
                                launch.clientPlayerName,
                                prepare.matchId(),
                                prepare.lockedLaunchSpecDigest(),
                                accepted,
                                status))),
                "Could not send match-loaded");
    }

    private void sendClientLobbyCommand(MultiplayerLobbyWireV1.Command command, String failurePrefix) {
        MultiplayerLanTransportV1.ConnectedPeer current = peer;
        if (command == null || current == null) return;
        enqueueNetworkWrite(
                () -> current.sendLobbyCommand(MultiplayerLobbyWireV1.encodeCommand(command)),
                failurePrefix);
    }

    private void enqueueNetworkWrite(MultiplayerNetworkWriteQueue.WriteTask task, String failurePrefix) {
        networkWrites.submit(task).thenAccept(sent -> {
            if (!sent && running) {
                Throwable failure = networkWrites.lastFailure();
                String detail = failure == null || failure.getMessage() == null
                        ? "write failed"
                        : failure.getMessage();
                setNetworkStatus(failurePrefix + ": " + detail);
            }
        });
    }

    private MultiplayerLobbyWireV1.Snapshot hostSnapshot(String status) {
        return new MultiplayerLobbyWireV1.Snapshot(
                lobby.revision(),
                lobby.lobbyId(),
                lobby.activeMatchId().isBlank() ? launch.matchId : lobby.activeMatchId(),
                lobby.activeSessionNonce().isBlank()
                        ? MultiplayerProtocolV1.sessionNonceForMatch(launch.matchId)
                        : lobby.activeSessionNonce(),
                lobby.lockedDuelConfig() == null ? lobby.revision() : lobby.lockedDuelConfig().lobbyRevision(),
                selectedMissionId(),
                selectedSeed,
                selectedWorldW,
                selectedWorldH,
                lobby.missionConfig().hostHull(),
                lobby.missionConfig().clientHull(),
                lobby.hostReady(),
                lobby.clientReady(),
                matchLaunching,
                lobby.hostName(),
                lobby.clientName(),
                status);
    }

    private String lockedLaunchSpecDigest(MultiplayerLobbyWireV1.Snapshot snapshot) {
        if (snapshot == null) return "";
        MissionLaunchSpec spec = MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                launch.withMissionSettings(snapshot.missionId(), snapshot.seed(), snapshot.worldW(), snapshot.worldH()),
                snapshot.seed());
        return MissionDigest.lockedLaunchSpecDigest(spec, snapshot.lockedConfigRevision());
    }

    private void applySelectedMissionToLobby(boolean resetMapToMissionDefault) {
        MissionLaunchSpec spec = MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                launch.withMissionSettings(selectedMissionId, selectedSeed, 0, 0), selectedSeed);
        if (resetMapToMissionDefault || selectedWorldW <= 0 || selectedWorldH <= 0) {
            selectedWorldW = spec.worldW();
            selectedWorldH = spec.worldH();
            if (mapSelector != null) mapSelector.setSelectedItem(MapChoice.closest(selectedWorldW, selectedWorldH));
        }
        ShipRole hostHull = ShipRole.FRIGATE;
        ShipRole clientHull = ShipRole.FRIGATE;
        for (MissionSlotSpec slot : spec.playerSlots()) {
            if (slot.slotId() == MultiplayerRulesV1.HOST_SLOT_ID) {
                hostHull = slot.defaultHull();
            } else if (slot.slotId() == MultiplayerRulesV1.CLIENT_SLOT_ID) {
                clientHull = slot.defaultHull();
            }
        }
        lobby.hostSetMissionConfig(new MultiplayerLobbyV1.LobbyMissionConfig(
                selectedMissionId,
                selectedSeed,
                selectedWorldW,
                selectedWorldH,
                hostHull,
                clientHull));
    }

    private void hostUpdateSeedAndMap() {
        if (!hostRole) return;
        long previousSeed = selectedSeed;
        int previousWorldW = selectedWorldW;
        int previousWorldH = selectedWorldH;
        selectedMissionId = selectedMissionId();
        selectedSeed = parseSeed(seedField == null ? "" : seedField.getText(), selectedSeed);
        MapChoice map = mapSelector == null
                ? MapChoice.closest(selectedWorldW, selectedWorldH)
                : (MapChoice) mapSelector.getSelectedItem();
        if (map == null) map = MapChoice.closest(selectedWorldW, selectedWorldH);
        selectedWorldW = map.worldW;
        selectedWorldH = map.worldH;
        boolean changed = previousSeed != selectedSeed
                || previousWorldW != selectedWorldW
                || previousWorldH != selectedWorldH;
        if (changed) {
            applySelectedMissionToLobby(false);
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, false);
            localReady = false;
            sendHostSnapshot("Lobby settings updated");
        }
        refreshUi();
    }

    private boolean canHostStart() {
        return hostRole
                && peer != null
                && lobby.clientReady()
                && !matchLaunching
                && currentMissionValidation().accepted();
    }

    private void refreshUi() {
        if (activeGamePanel != null) return;
        checkMatchLoadingTimeout();
        if (hostRole) {
            addressLabel.setText(hostAddressText());
            assignmentLabel.setText(assignmentText(MultiplayerRulesV1.HOST_SLOT_ID));
            teamsLabel.setText(teamText());
            MultiplayerMissionValidator.ValidationResult validation = currentMissionValidation();
            compatibilityLabel.setText(!validation.accepted()
                    ? validation.message()
                    : (peer == null
                            ? "Waiting for compatible client"
                            : "Compatible: protocol " + MultiplayerProtocolV1.PROTOCOL_VERSION));
            readinessLabel.setText((lobby.hostReady() ? "Host ready" : "Host not ready")
                    + " / " + (lobby.clientReady() ? "Client ready" : "Client not ready"));
            readyButton.setText(lobby.hostReady() ? "Unready" : "Ready");
            startButton.setEnabled(canHostStart());
            startButton.setText("Start Match");
        } else {
            MultiplayerLobbyWireV1.Snapshot snapshot = latestClientSnapshot;
            if (snapshot != null) {
                missionSelector.setSelectedItem(MultiplayerMissionChoice.fromMissionId(snapshot.missionId()));
                seedValueLabel.setText(String.valueOf(snapshot.seed()));
                mapValueLabel.setText(mapLabel(snapshot.worldW(), snapshot.worldH()));
                assignmentLabel.setText(assignmentText(snapshot, MultiplayerRulesV1.CLIENT_SLOT_ID));
                teamsLabel.setText(teamText(snapshot));
                compatibilityLabel.setText("Compatible: protocol " + MultiplayerProtocolV1.PROTOCOL_VERSION);
                readinessLabel.setText((snapshot.hostReady() ? "Host ready" : "Host not ready")
                        + " / " + (snapshot.clientReady() ? "Client ready" : "Client not ready"));
            } else {
                assignmentLabel.setText("Waiting for host mission");
                teamsLabel.setText("Host Blue / Client Red");
                compatibilityLabel.setText(peer == null ? "Connecting" : "Compatible: protocol "
                        + MultiplayerProtocolV1.PROTOCOL_VERSION);
                readinessLabel.setText(localReady ? "Ready pending" : "Not ready");
            }
            addressLabel.setText(launch.resolvedDirectAddress());
            readyButton.setText(localReady ? "Unready" : "Ready");
            startButton.setEnabled(false);
            startButton.setText("Waiting");
        }
        statusLabel.setText(statusText);
    }

    private void setNetworkStatus(String status) {
        if (status == null) return;
        statusText = status;
        refreshUiOnEventThread();
    }

    private void returnClientToSetupFromNetwork(String status) {
        setNetworkStatus(status);
        if (hostRole || returnedToSetup || exitToMenu == null) return;
        returnedToSetup = true;
        SwingUtilities.invokeLater(exitToMenu);
    }

    private void refreshUiOnEventThread() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshUi();
        } else {
            SwingUtilities.invokeLater(this::refreshUi);
        }
    }

    private void checkMatchLoadingTimeout() {
        if (!hostRole || !matchLaunching || activeGamePanel != null || matchLoadingDeadlineNs <= 0L) return;
        if (System.nanoTime() < matchLoadingDeadlineNs) return;
        returnHostToLobbyAfterLoadingFailure("Match loading timed out; returned to lobby");
        sendHostSnapshot(statusText);
    }

    private void returnHostToLobbyAfterLoadingFailure(String status) {
        statusText = (status == null || status.isBlank())
                ? "Match loading failed; returned to lobby"
                : status.trim();
        matchLaunching = false;
        clearPendingMatchLoad();
        lobby.returnToLobby();
        if (!SwingUtilities.isEventDispatchThread()) {
            refreshUiOnEventThread();
        }
    }

    private void clearPendingMatchLoad() {
        pendingLockedSnapshot = null;
        pendingLockedLaunchSpecDigest = "";
        pendingGameContext = null;
        pendingMatchStartTick = 0L;
        matchLoadingDeadlineNs = 0L;
    }

    private String hostAddressText() {
        MultiplayerLanTransportV1.Host currentHost = hostSocket;
        if (currentHost != null) return hostAddressText(launch, currentHost);
        return hostAddressText(launch, null);
    }

    static String hostAddressTextForTests(MultiplayerLaunchConfig launch,
                                          MultiplayerLanTransportV1.Host currentHost) {
        return hostAddressText(launch, currentHost);
    }

    private static String hostAddressText(MultiplayerLaunchConfig launch,
                                          MultiplayerLanTransportV1.Host currentHost) {
        MultiplayerLaunchConfig safeLaunch = launch == null
                ? MultiplayerLaunchConfig.host(MultiplayerLanTransportV1.DEFAULT_PORT, "127.0.0.1")
                : launch;
        int port = currentHost == null ? safeLaunch.port : currentHost.boundAddress().port();
        if (safeLaunch.loopbackOnly) {
            return "127.0.0.1:" + port + " (local only)";
        }
        String advertised = safeLaunch.advertisedHostAddress == null ? "" : safeLaunch.advertisedHostAddress.trim();
        if (!advertised.isBlank() && !"127.0.0.1".equals(advertised) && !"localhost".equalsIgnoreCase(advertised)) {
            return advertised + ":" + port + " (LAN only; not internet)";
        }
        List<MultiplayerLanTransportV1.DirectAddress> addresses =
                MultiplayerLanTransportV1.detectedPrivateLanAddresses(port);
        if (!addresses.isEmpty()) {
            return String.join(", ", addresses.stream().map(MultiplayerLanTransportV1.DirectAddress::toString).toList())
                    + " (LAN only; not internet)";
        }
        return "Listening on port " + port + " (enter this machine's private LAN IP; not internet)";
    }

    private String selectedMissionId() {
        Object selected = missionSelector.getSelectedItem();
        if (selected instanceof MultiplayerMissionChoice choice) return choice.missionId();
        return MultiplayerMissionChoice.fromMissionId(selectedMissionId).missionId();
    }

    private MultiplayerMissionValidator.ValidationResult currentMissionValidation() {
        return MultiplayerMissionValidator.validateForV1(currentMissionSpec());
    }

    private MissionLaunchSpec currentMissionSpec() {
        return MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                launch.withMissionSettings(selectedMissionId, selectedSeed, selectedWorldW, selectedWorldH),
                selectedSeed);
    }

    private String assignmentText(int slotId) {
        MissionLaunchSpec spec = currentMissionSpec();
        MissionSlotSpec slot = null;
        for (MissionSlotSpec candidate : spec.playerSlots()) {
            if (candidate.slotId() == slotId) {
                slot = candidate;
                break;
            }
        }
        if (slot == null) return "Unassigned";
        String owner = slotId == MultiplayerRulesV1.HOST_SLOT_ID ? "Host" : "Client";
        return owner + ": " + slot.defaultHull();
    }

    private String teamText() {
        MultiplayerRulesV1.BattleSetup setup = lobby.currentSetup();
        return "Host " + setup.hostSlot().team().teamName()
                + " / Client " + setup.clientSlot().team().teamName();
    }

    private static String teamText(MultiplayerLobbyWireV1.Snapshot snapshot) {
        if (snapshot == null) return "Host Blue / Client Red";
        return "Host Blue / Client Red";
    }

    private static String assignmentText(MultiplayerLobbyWireV1.Snapshot snapshot, int slotId) {
        if (snapshot == null) return "Unassigned";
        if (slotId == MultiplayerRulesV1.HOST_SLOT_ID) return "Host: " + snapshot.hostHull();
        if (slotId == MultiplayerRulesV1.CLIENT_SLOT_ID) return "Client: " + snapshot.clientHull();
        return "Unassigned";
    }

    private static JPanel card() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(10, 23, 36));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(67, 129, 152)),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        panel.setMaximumSize(new Dimension(740, 400));
        return panel;
    }

    private static JPanel row(String name, JComponent value) {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);
        JLabel label = label(name + ":", 15, Font.BOLD);
        label.setPreferredSize(new Dimension(140, 26));
        panel.add(label, BorderLayout.WEST);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel valueLabel(String text) {
        JLabel label = label(text, 15, Font.PLAIN);
        label.setForeground(new Color(216, 232, 240));
        return label;
    }

    private static JLabel label(String text, int size, int style) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(229, 241, 247));
        label.setFont(new Font("Consolas", style, size));
        return label;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setFont(new Font("Consolas", Font.BOLD, 15));
        return button;
    }

    private static long parseSeed(String raw, long fallback) {
        try {
            long parsed = Long.parseLong(raw == null ? "" : raw.trim());
            return Math.max(0L, parsed);
        } catch (RuntimeException ignored) {
            return Math.max(0L, fallback);
        }
    }

    private static String mapLabel(int worldW, int worldH) {
        return worldW + " x " + worldH;
    }

    private static long readTimeoutTicks(int timeoutMs) {
        return Math.max(1L, Math.round(MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE * (timeoutMs / 1000.0)));
    }

    private enum MapChoice {
        V1_DUEL("Duel (3600 x 2200)", 3600, 2200),
        HEAVY_DUEL("Heavy (5200 x 3200)", 5200, 3200),
        LARGE("Large (7000 x 4200)", 7000, 4200);

        final String label;
        final int worldW;
        final int worldH;

        MapChoice(String label, int worldW, int worldH) {
            this.label = label;
            this.worldW = worldW;
            this.worldH = worldH;
        }

        static MapChoice closest(int worldW, int worldH) {
            MapChoice best = V1_DUEL;
            long bestDelta = Long.MAX_VALUE;
            for (MapChoice choice : values()) {
                long delta = Math.abs((long) choice.worldW - worldW)
                        + Math.abs((long) choice.worldH - worldH);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = choice;
                }
            }
            return best;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
