import app.config.MultiplayerLaunchConfig;
import app.config.MultiplayerMissionChoice;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLobbyPanelTest {

    @Test
    void hostLobbyExposesMissionReadyStartAndLeaveControls() {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.HEAVY_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            JComboBox<?> mission = (JComboBox<?>) findByName(panel, "multiplayerLobbyMissionSelector");
            JTextField seed = (JTextField) findByName(panel, "multiplayerLobbySeedField");
            JComboBox<?> map = (JComboBox<?>) findByName(panel, "multiplayerLobbyMapSelector");
            JButton ready = (JButton) findByName(panel, "multiplayerLobbyReadyButton");
            JButton start = (JButton) findByName(panel, "multiplayerLobbyStartButton");
            JButton leave = (JButton) findByName(panel, "multiplayerLobbyLeaveButton");
            JLabel assignment = (JLabel) findByName(panel, "multiplayerLobbyAssignmentLabel");
            JLabel teams = (JLabel) findByName(panel, "multiplayerLobbyTeamsLabel");
            JLabel compatibility = (JLabel) findByName(panel, "multiplayerLobbyCompatibilityLabel");
            JLabel address = (JLabel) findByName(panel, "multiplayerLobbyAddressLabel");

            assertNotNull(mission);
            assertNotNull(seed);
            assertNotNull(map);
            assertNotNull(ready);
            assertNotNull(start);
            assertNotNull(leave);
            assertNotNull(assignment);
            assertNotNull(teams);
            assertNotNull(compatibility);
            assertNotNull(address);
            assertTrue(teams.getText().contains("Host Blue"));
            assertTrue(teams.getText().contains("Client Red"));
            assertEquals(MultiplayerMissionChoice.HEAVY_DUEL, mission.getSelectedItem());
            assertEquals(true, seed.isEnabled());
            assertEquals(true, map.isEnabled());
            assertFalse(panel.startEnabledForTests());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void hostMissionSelectorMirrorsMultiplayerEligibleCatalogEntriesOnly() {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"),
                () -> {},
                () -> {},
                false);

        try {
            JComboBox<?> mission = (JComboBox<?>) findByName(panel, "multiplayerLobbyMissionSelector");
            assertNotNull(mission);
            java.util.Set<String> selectorIds = new java.util.HashSet<>();
            for (int i = 0; i < mission.getItemCount(); i++) {
                Object item = mission.getItemAt(i);
                assertTrue(item instanceof MultiplayerMissionChoice);
                selectorIds.add(((MultiplayerMissionChoice) item).missionId());
            }
            java.util.Set<String> catalogIds = CustomMissionCatalog
                    .multiplayerEntries(CustomMissionCatalog.v1SupportedCapabilities())
                    .stream()
                    .map(CustomMissionDescriptor::id)
                    .collect(Collectors.toUnmodifiableSet());

            assertEquals(catalogIds, selectorIds);
            assertTrue(selectorIds.contains(CustomMissionCatalog.LAST_STAND_ID));
            assertTrue(selectorIds.contains(CustomMissionCatalog.RESOURCE_RUSH_ID));
            assertTrue(selectorIds.contains(CustomMissionCatalog.FOUR_TEAM_DOMINATION_ID));
            assertTrue(selectorIds.contains(CustomMissionCatalog.SHOOTING_RANGE_ID));
            assertTrue(selectorIds.contains(CustomMissionCatalog.FLEET_SHOWCASE_ID));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void hostAddressDisplayLabelsLoopbackAndLanScope() {
        MultiplayerLaunchConfig loopback = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.HOST,
                "",
                "127.0.0.1",
                "",
                46717,
                1_000,
                "address-policy-loopback",
                "",
                true,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        MultiplayerLaunchConfig advertisedLan = MultiplayerLaunchConfig.host(46718, "192.168.1.20");

        assertTrue(MultiplayerLobbyPanel.hostAddressTextForTests(loopback, null).contains("local only"));
        String lanText = MultiplayerLobbyPanel.hostAddressTextForTests(advertisedLan, null);
        assertTrue(lanText.contains("192.168.1.20:46718"));
        assertTrue(lanText.contains("LAN only"));
        assertTrue(lanText.contains("not internet"));
    }

    @Test
    void clientLobbyMissionSelectorIsReadOnlyBeforeHostSnapshotArrives() {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""),
                () -> {},
                () -> {},
                false);

        try {
            JComboBox<?> mission = (JComboBox<?>) findByName(panel, "multiplayerLobbyMissionSelector");
            JTextField seed = (JTextField) findByName(panel, "multiplayerLobbySeedField");
            JComboBox<?> map = (JComboBox<?>) findByName(panel, "multiplayerLobbyMapSelector");
            JLabel seedValue = (JLabel) findByName(panel, "multiplayerLobbySeedValueLabel");
            JLabel mapValue = (JLabel) findByName(panel, "multiplayerLobbyMapValueLabel");
            JLabel assignment = (JLabel) findByName(panel, "multiplayerLobbyAssignmentLabel");
            JLabel teams = (JLabel) findByName(panel, "multiplayerLobbyTeamsLabel");
            JLabel compatibility = (JLabel) findByName(panel, "multiplayerLobbyCompatibilityLabel");

            assertNotNull(mission);
            assertFalse(mission.isEnabled());
            assertEquals(null, seed);
            assertEquals(null, map);
            assertNotNull(seedValue);
            assertNotNull(mapValue);
            assertNotNull(assignment);
            assertNotNull(teams);
            assertNotNull(compatibility);
            assertTrue(teams.getText().contains("Host Blue"));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void clientIgnoresOlderLobbySnapshots() {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""),
                () -> {},
                () -> {},
                false);

        try {
            MultiplayerLobbyWireV1.Snapshot newer = snapshot(5L, MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    777L, 5200, 3200, "newer");
            MultiplayerLobbyWireV1.Snapshot stale = snapshot(4L, MultiplayerMissionChoice.V1_DUEL.missionId(),
                    123L, 3600, 2200, "stale");

            assertTrue(panel.acceptClientSnapshotForTests(newer));
            assertFalse(panel.acceptClientSnapshotForTests(stale));

            assertEquals(5L, panel.latestClientSnapshotForTests().revision());
            assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    panel.latestClientSnapshotForTests().missionId());
            assertEquals(777L, panel.latestClientSnapshotForTests().seed());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void clientAssignmentDisplayUsesHostPublishedSlotHull() throws Exception {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""),
                () -> {},
                () -> {},
                false);

        try {
            JLabel assignment = (JLabel) findByName(panel, "multiplayerLobbyAssignmentLabel");
            assertNotNull(assignment);
            MultiplayerLobbyWireV1.Snapshot snapshot = new MultiplayerLobbyWireV1.Snapshot(
                    5L,
                    "lobby-1",
                    "match-1",
                    "nonce-1",
                    5L,
                    MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    777L,
                    5200,
                    3200,
                    ShipRole.CRUISER,
                    ShipRole.BATTLECRUISER,
                    false,
                    false,
                    false,
                    "Host",
                    "Client",
                    "ready");

            assertTrue(panel.acceptClientSnapshotForTests(snapshot));
            assertEventually(() -> assignment.getText().contains("BATTLECRUISER"), Duration.ofSeconds(2));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void networkSnapshotRefreshesVisibleClientStatusOnEventDispatchThread() throws Exception {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""),
                () -> {},
                () -> {},
                false);

        try {
            JLabel status = (JLabel) findByName(panel, "multiplayerLobbyStatusLabel");
            assertNotNull(status);
            MultiplayerLobbyWireV1.Snapshot snapshot = snapshot(6L,
                    MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    888L,
                    5200,
                    3200,
                    "network snapshot ready");
            AtomicBoolean accepted = new AtomicBoolean(false);
            AtomicBoolean calledFromEdt = new AtomicBoolean(true);

            Thread networkThread = new Thread(() -> {
                calledFromEdt.set(SwingUtilities.isEventDispatchThread());
                accepted.set(panel.acceptClientSnapshotForTests(snapshot));
            }, "mp-test-network-reader");
            networkThread.start();
            networkThread.join(1_000);

            assertFalse(networkThread.isAlive());
            assertTrue(accepted.get());
            assertFalse(calledFromEdt.get());
            assertEventually(() -> status.getText().contains("network snapshot ready"), Duration.ofSeconds(2));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void hostMissionChangeClearsReadyAndAdvancesRevision() {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            JComboBox<?> mission = (JComboBox<?>) findByName(panel, "multiplayerLobbyMissionSelector");
            assertNotNull(mission);
            MultiplayerLobbyV1 lobby = panel.lobbyForTests();
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
            lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
            long beforeRevision = lobby.revision();

            mission.setSelectedItem(MultiplayerMissionChoice.HEAVY_DUEL);

            assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    panel.selectedMissionIdForTests());
            assertEquals(beforeRevision + 1, lobby.revision());
            assertFalse(lobby.hostReady());
            assertFalse(lobby.clientReady());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void hostHandlingClientLeaveClearsClientSlot() {
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            MultiplayerLobbyV1 lobby = panel.lobbyForTests();
            assertTrue(lobby.join("Grace").accepted());
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
            lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());

            panel.handleHostLobbyCommandForTests(new MultiplayerLobbyWireV1.Command(
                    MultiplayerLobbyWireV1.CommandType.LEAVE,
                    false,
                    lobby.revision()));

            assertFalse(lobby.clientConnected());
            assertFalse(lobby.clientReady());
            assertTrue(panel.statusTextForTests().contains("Client left"));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void repeatedHostLobbyCreateAndShutdownReleasesPort() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        for (int i = 0; i < 3; i++) {
            MultiplayerLaunchConfig launch = new MultiplayerLaunchConfig(
                    MultiplayerLaunchConfig.Role.HOST,
                    "",
                    "127.0.0.1",
                    "",
                    port,
                    1_000,
                    "repeat-lobby-" + i,
                    "",
                    true,
                    false,
                    MultiplayerMissionChoice.V1_DUEL.missionId());
            MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(launch, () -> {}, () -> {}, true);
            Thread.sleep(80);
            panel.shutdown();
        }
    }

    @Test
    void lobbyNetworkReadsStartOnWorkerThreadOutsideEventDispatchThread() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        MultiplayerLobbyPanel[] holder = new MultiplayerLobbyPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MultiplayerLobbyPanel(
                new MultiplayerLaunchConfig(
                        MultiplayerLaunchConfig.Role.HOST,
                        "",
                        "127.0.0.1",
                        "",
                        port,
                        1_000,
                        "read-thread-ownership",
                        "",
                        true,
                        false,
                        MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                true));
        MultiplayerLobbyPanel panel = holder[0];

        try {
            assertEventually(() -> panel.workerForTests() != null, Duration.ofSeconds(2));
            Thread worker = panel.workerForTests();
            assertNotNull(worker);
            assertEquals("mp-lobby-host", worker.getName());
            assertFalse(worker.getName().contains("AWT-EventQueue"));
            assertFalse(SwingUtilities.isEventDispatchThread());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void occupiedHostPortShowsReadableFailureStatus() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            MultiplayerLaunchConfig launch = new MultiplayerLaunchConfig(
                    MultiplayerLaunchConfig.Role.HOST,
                    "",
                    "127.0.0.1",
                    "",
                    occupied.getLocalPort(),
                    1_000,
                    "occupied-port",
                    "",
                    true,
                    false,
                    MultiplayerMissionChoice.V1_DUEL.missionId());
            MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(launch, () -> {}, () -> {}, true);
            try {
                assertEventually(() -> panel.statusTextForTests().contains("Host lobby failed"),
                        Duration.ofSeconds(5));
            } finally {
                panel.shutdown();
            }
        }
    }

    @Test
    void invalidJoinAddressShowsReadableStatusWithoutBlockingPanel() throws Exception {
        MultiplayerLaunchConfig launch = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.CLIENT,
                "not:a:valid:address",
                "",
                "",
                46717,
                1_000,
                "invalid-address",
                "",
                false,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(launch, () -> {}, () -> {}, true);

        try {
            assertEventually(() -> panel.statusTextForTests().toLowerCase().contains("join failed")
                            || panel.statusTextForTests().toLowerCase().contains("ipv6"),
                    Duration.ofSeconds(3));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void unreachableJoinAddressShowsConnectionFailureWithoutFreezingPanel() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        MultiplayerLaunchConfig launch = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.CLIENT,
                "127.0.0.1:" + port,
                "",
                "",
                port,
                1_000,
                "unreachable-address",
                "",
                true,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        MultiplayerLobbyPanel panel = new MultiplayerLobbyPanel(launch, () -> {}, () -> {}, true);

        try {
            assertEventually(() -> {
                String status = panel.statusTextForTests().toLowerCase();
                return status.contains("connection") || status.contains("refused")
                        || status.contains("failed");
            }, Duration.ofSeconds(4));
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void versionMismatchKeepsClientOnJoinScreenWithReadableStatus() throws Exception {
        MultiplayerProtocolV1.CompatibilityFingerprint local = MultiplayerProtocolV1.localFingerprint();
        MultiplayerProtocolV1.CompatibilityFingerprint mismatchedHost =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION + 1,
                        local.gameBuild(),
                        local.manifest());

        try (MultiplayerLanTransportV1.Host host =
                     MultiplayerLanTransportV1.bindLoopback(0, "version-mismatch", null)) {
            CompletableFuture<MultiplayerLanTransportV1.TransportResult> rejected =
                    CompletableFuture.supplyAsync(() -> host.acceptOnce(
                            mismatchedHost,
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            2_000));
            MultiplayerLaunchConfig launch = new MultiplayerLaunchConfig(
                    MultiplayerLaunchConfig.Role.CLIENT,
                    "127.0.0.1:" + host.boundAddress().port(),
                    "",
                    "",
                    host.boundAddress().port(),
                    2_000,
                    "version-mismatch",
                    "",
                    true,
                    false,
                    MultiplayerMissionChoice.V1_DUEL.missionId());
            MultiplayerLobbyPanel client = new MultiplayerLobbyPanel(launch, () -> {}, () -> {}, true);

            try {
                assertEventually(() -> client.statusTextForTests().contains("Protocol mismatch"),
                        Duration.ofSeconds(4),
                        client::statusTextForTests);
                assertFalse(client.activeGamePanelForTests());
                assertFalse(rejected.get(1, TimeUnit.SECONDS).accepted());
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    void hostCancellationReturnsClientToReadableLobbyStatus() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        MultiplayerLaunchConfig hostLaunch = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.HOST,
                "",
                "127.0.0.1",
                "",
                port,
                1_000,
                "host-cancel",
                "",
                true,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        MultiplayerLaunchConfig clientLaunch = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.CLIENT,
                "127.0.0.1:" + port,
                "",
                "",
                port,
                1_000,
                "host-cancel",
                "",
                true,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        AtomicBoolean clientReturnedToSetup = new AtomicBoolean(false);
        MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(hostLaunch, () -> {}, () -> {}, true);
        MultiplayerLobbyPanel client = new MultiplayerLobbyPanel(clientLaunch,
                () -> clientReturnedToSetup.set(true), () -> {}, true);

        try {
            assertEventually(() -> client.statusTextForTests().toLowerCase().contains("connected")
                            || client.latestClientSnapshotForTests() != null,
                    Duration.ofSeconds(5));
            host.shutdown();
            assertEventually(() -> {
                String status = client.statusTextForTests().toLowerCase();
                return status.contains("host closed") || status.contains("host disconnected")
                        || status.contains("closed");
            }, Duration.ofSeconds(5));
            assertEventually(clientReturnedToSetup::get, Duration.ofSeconds(2));
        } finally {
            client.shutdown();
            host.shutdown();
        }
    }

    @Test
    void matchStartWaitsForPrepareLoadedAndBeginHandshake() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        MultiplayerLaunchConfig hostLaunch = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.HOST,
                "",
                "127.0.0.1",
                "",
                port,
                2_000,
                "loading-handshake",
                "",
                true,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        MultiplayerLaunchConfig clientLaunch = new MultiplayerLaunchConfig(
                MultiplayerLaunchConfig.Role.CLIENT,
                "127.0.0.1:" + port,
                "",
                "",
                port,
                2_000,
                "loading-handshake",
                "",
                true,
                false,
                MultiplayerMissionChoice.V1_DUEL.missionId());
        MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(hostLaunch, () -> {}, () -> {}, true);
        MultiplayerLobbyPanel client = new MultiplayerLobbyPanel(clientLaunch, () -> {}, () -> {}, true);

        try {
            assertEventually(() -> client.latestClientSnapshotForTests() != null, Duration.ofSeconds(5));
            JButton hostReady = (JButton) findByName(host, "multiplayerLobbyReadyButton");
            JButton clientReady = (JButton) findByName(client, "multiplayerLobbyReadyButton");
            JButton hostStart = (JButton) findByName(host, "multiplayerLobbyStartButton");
            assertNotNull(hostReady);
            assertNotNull(clientReady);
            assertNotNull(hostStart);

            clientReady.doClick();
            assertEventually(host::startEnabledForTests, Duration.ofSeconds(5));
            hostStart.doClick();

            assertEventually(host::matchPanelLaunchedForTests, Duration.ofSeconds(5),
                    () -> "host=" + host.statusTextForTests() + " client=" + client.statusTextForTests());
            assertEventually(client::matchPanelLaunchedForTests, Duration.ofSeconds(5),
                    () -> "host=" + host.statusTextForTests() + " client=" + client.statusTextForTests());
            assertEquals(1L, host.lastLaunchedMatchStartTickForTests());
            assertEquals(1L, client.lastLaunchedMatchStartTickForTests());
        } finally {
            client.shutdown();
            host.shutdown();
        }
    }

    @Test
    void matchLoadingTimeoutReturnsHostToLobbyWithReadableStatus() {
        MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            MultiplayerLobbyV1 lobby = host.lobbyForTests();
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
            lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
            host.beginMatchLoadingTimeoutForTests(System.nanoTime() - 1L);

            host.checkMatchLoadingTimeoutForTests();

            assertTrue(host.statusTextForTests().contains("timed out"));
            assertFalse(lobby.hostReady());
            assertFalse(lobby.clientReady());
            assertFalse(lobby.locked());
            assertFalse(host.activeGamePanelForTests());
        } finally {
            host.shutdown();
        }
    }

    @Test
    void lostConnectionDuringMatchLoadingCancelsCountdownAndReturnsHostToLobby() {
        MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            MultiplayerLobbyV1 lobby = host.lobbyForTests();
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
            lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
            host.beginMatchLoadingTimeoutForTests(System.nanoTime() + 5_000_000_000L);

            host.cancelMatchLoadingForTests("Client disconnected during match loading; returned to lobby");

            assertTrue(host.statusTextForTests().contains("disconnected"));
            assertFalse(lobby.hostReady());
            assertFalse(lobby.clientReady());
            assertFalse(lobby.locked());
            assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
            assertFalse(host.activeGamePanelForTests());
        } finally {
            host.shutdown();
        }
    }

    @Test
    void clientLoadingFailureReturnsHostToLobbyWithReadinessCleared() {
        MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            MultiplayerLobbyV1 lobby = host.lobbyForTests();
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
            lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
            assertTrue(lobby.lockForCountdown().accepted());
            lobby.startLoading();
            host.beginMatchLoadingTimeoutForTests(System.nanoTime() + 5_000_000_000L);

            host.handleHostLobbyCommandForTests(new MultiplayerLobbyWireV1.Command(
                    MultiplayerLobbyWireV1.CommandType.MATCH_LOADED,
                    false,
                    lobby.revision(),
                    "",
                    "wrong-match",
                    "wrong-digest",
                    false,
                    "Locked match specification mismatch"));

            assertTrue(host.statusTextForTests().contains("Client failed to load match"));
            assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
            assertFalse(lobby.hostReady());
            assertFalse(lobby.clientReady());
            assertFalse(lobby.locked());
            assertFalse(host.activeGamePanelForTests());
        } finally {
            host.shutdown();
        }
    }

    @Test
    void clientVoluntarilyLeavingActiveMatchReturnsToMultiplayerSetup() throws Exception {
        MultiplayerLobbyPanel client = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", "")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                    MultiplayerLaunchConfig.client("127.0.0.1:46717", "")
                            .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()));
            ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.connectedForTests("Connected to host");
            GamePanel gamePanel = client.installActiveGamePanelForTests(ctx);
            Action toMenu = gamePanel.getActionMap().get("toMenu");
            assertNotNull(toMenu);

            SwingUtilities.invokeAndWait(() ->
                    toMenu.actionPerformed(new ActionEvent(gamePanel, ActionEvent.ACTION_PERFORMED, "toMenu")));

            assertFalse(client.activeGamePanelForTests());
            assertTrue(client.statusTextForTests().contains("Multiplayer Setup"));
            assertNotNull(findByName(client, "multiplayerLobbyReadyButton"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    void hostDetectingClientDisconnectDuringActiveMatchFreesClientSlotAndReturnsLobbyOpen() throws Exception {
        MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            MultiplayerLobbyV1 lobby = host.lobbyForTests();
            assertTrue(lobby.join("Client").accepted());
            lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
            lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
            assertTrue(lobby.lockForCountdown().accepted());
            lobby.startLoading();
            lobby.startMatch();
            GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                    MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                            .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()));
            ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.disconnectedForTests("Client disconnected");
            GamePanel gamePanel = host.installActiveGamePanelForTests(ctx);

            SwingUtilities.invokeAndWait(() ->
                    gamePanel.actionPerformed(new ActionEvent(gamePanel, ActionEvent.ACTION_PERFORMED, "tick")));

            assertFalse(host.activeGamePanelForTests());
            assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
            assertFalse(lobby.clientConnected());
            assertFalse(lobby.hostReady());
            assertFalse(lobby.clientReady());
            assertTrue(host.statusTextForTests().contains("Client disconnected"));
        } finally {
            host.shutdown();
        }
    }

    @Test
    void normalMatchCompletionReturnsHostToLobbyWithReadinessClearedAndConnectionPreserved() throws Exception {
        try (MultiplayerLanTransportV1.Host transportHost =
                     MultiplayerLanTransportV1.bindLoopback(0, "post-match-return", null)) {
            CompletableFuture<MultiplayerLanTransportV1.TransportResult> accepted =
                    CompletableFuture.supplyAsync(() -> transportHost.acceptOnce(
                            MultiplayerProtocolV1.localFingerprint(),
                            MultiplayerRulesV1.CLIENT_SLOT_ID,
                            2_000));
            MultiplayerLanTransportV1.TransportResult clientConnected = MultiplayerLanTransportV1.connect(
                    transportHost.boundAddress(),
                    MultiplayerProtocolV1.localFingerprint(),
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    "post-match-return",
                    null,
                    2_000);
            MultiplayerLanTransportV1.TransportResult hostAccepted = accepted.get(2, TimeUnit.SECONDS);
            assertTrue(clientConnected.accepted(), clientConnected.reason());
            assertTrue(hostAccepted.accepted(), hostAccepted.reason());
            MultiplayerLanTransportV1.ConnectedPeer hostPeer = hostAccepted.peer();
            MultiplayerLanTransportV1.ConnectedPeer clientPeer = clientConnected.peer();
            MultiplayerLobbyPanel host = new MultiplayerLobbyPanel(
                    MultiplayerLaunchConfig.host(transportHost.boundAddress().port(), "127.0.0.1")
                            .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                    () -> {},
                    () -> {},
                    false);

            try {
                MultiplayerLobbyV1 lobby = host.lobbyForTests();
                assertTrue(lobby.join("Client").accepted());
                lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
                lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
                assertTrue(lobby.lockForCountdown().accepted());
                lobby.startLoading();
                lobby.startMatch();
                GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                        MultiplayerLaunchConfig.host(transportHost.boundAddress().port(), "127.0.0.1")
                                .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()));
                ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.fromConnectedPeer(
                        MultiplayerLaunchConfig.host(transportHost.boundAddress().port(), "127.0.0.1"),
                        hostPeer);
                ctx.gameOver = true;
                ctx.state = GameState.GAME_OVER;
                ctx.gameOverText = "Elimination victory";
                GamePanel gamePanel = host.installActiveGamePanelForTests(ctx);

                long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
                while (host.activeGamePanelForTests() && System.nanoTime() < deadline) {
                    SwingUtilities.invokeAndWait(() ->
                            gamePanel.actionPerformed(new ActionEvent(gamePanel, ActionEvent.ACTION_PERFORMED, "tick")));
                    Thread.sleep(50);
                }

                assertFalse(host.activeGamePanelForTests());
                assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
                assertTrue(lobby.clientConnected());
                assertFalse(lobby.hostReady());
                assertFalse(lobby.clientReady());
                assertEquals(hostPeer, host.lastResumedPeerForTests());
            } finally {
                host.shutdown();
                clientPeer.close();
            }
        }
    }

    @Test
    void clientAuthoritativeMatchCompletionReturnsToLobbyView() throws Exception {
        MultiplayerLobbyPanel client = new MultiplayerLobbyPanel(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", "")
                        .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()),
                () -> {},
                () -> {},
                false);

        try {
            GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                    MultiplayerLaunchConfig.client("127.0.0.1:46717", "")
                            .withMissionId(MultiplayerMissionChoice.V1_DUEL.missionId()));
            ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.matchEndedForTests("Elimination victory");
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;
            ctx.gameOverText = "Elimination victory";
            GamePanel gamePanel = client.installActiveGamePanelForTests(ctx);

            SwingUtilities.invokeAndWait(() ->
                    gamePanel.actionPerformed(new ActionEvent(gamePanel, ActionEvent.ACTION_PERFORMED, "tick")));

            assertFalse(client.activeGamePanelForTests());
            assertTrue(client.statusTextForTests().contains("returned to lobby"));
            assertNotNull(findByName(client, "multiplayerLobbyReadyButton"));
        } finally {
            client.shutdown();
        }
    }

    private static MultiplayerLobbyWireV1.Snapshot snapshot(long revision, String missionId,
                                                            long seed, int worldW, int worldH,
                                                            String status) {
        return new MultiplayerLobbyWireV1.Snapshot(
                revision,
                missionId,
                seed,
                worldW,
                worldH,
                false,
                false,
                false,
                "Host",
                "Client",
                status);
    }

    private static Component findByName(Component root, String name) {
        if (root == null) return null;
        if (root instanceof JComponent component && name.equals(component.getName())) {
            return component;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findByName(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertEventually(BooleanSupplier condition, Duration timeout) throws Exception {
        assertEventually(condition, timeout, () -> "");
    }

    private static void assertEventually(BooleanSupplier condition, Duration timeout,
                                         java.util.function.Supplier<String> detail) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(() -> {});
            }
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), detail.get());
    }
}
