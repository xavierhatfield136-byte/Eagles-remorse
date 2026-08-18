package app.ui;

import app.config.GameConfig;
import app.config.GameMode;
import app.config.MultiplayerMissionChoice;
import app.config.PlayerTeamChoice;

import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuPanelMultiplayerEntryTest {

    @AfterEach
    void clearFeatureOverride() {
        System.clearProperty("game.feature.multiplayer_custom_battle");
        System.clearProperty("game.feature.multiplayer_custom_missions");
        System.clearProperty("game.dev.menu");
    }

    @Test
    void multiplayerEntryIsHiddenWhenFeatureFlagIsDisabled() {
        System.setProperty("game.feature.multiplayer_custom_battle", "false");
        System.setProperty("game.feature.multiplayer_custom_missions", "false");
        MainMenuPanel panel = menu();

        try {
            assertNull(findByName(panel, "multiplayerEntryPanel"));
            assertNull(findByName(panel, "multiplayerHostBattleButton"));
            assertNull(findByName(panel, "multiplayerJoinBattleButton"));
            assertNull(findByName(panel, "multiplayerDiagnosticsButton"));
            assertNull(findByName(panel, "multiplayerDirectAddressField"));
            assertNull(findByName(panel, "multiplayerPlayerNameField"));
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void customBattleHasDirectMainMenuEntry() {
        MainMenuPanel panel = menu();

        try {
            JButton customBattle = (JButton) findByName(panel, "customBattleButton");

            assertNotNull(customBattle);
            assertEquals("Custom Battle", customBattle.getText());
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void mainMenuExposesShipyardEntry() {
        MainMenuPanel panel = menu();

        try {
            JButton shipyard = (JButton) findByName(panel, "customShipCreatorButton");

            assertNotNull(shipyard);
            assertEquals("Shipyard", shipyard.getText());
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void customBattleModeAllowsTeamEForPlayerSelection() throws Exception {
        java.lang.reflect.Method allowedTeams = MainMenuPanel.class.getDeclaredMethod(
                "allowedTeamsForMode", GameMode.class);
        allowedTeams.setAccessible(true);

        PlayerTeamChoice[] customBattleTeams = (PlayerTeamChoice[]) allowedTeams.invoke(null, GameMode.CUSTOM_BATTLES);
        PlayerTeamChoice[] lastStandTeams = (PlayerTeamChoice[]) allowedTeams.invoke(null, GameMode.LAST_STAND);

        assertTrue(java.util.Arrays.asList(customBattleTeams).contains(PlayerTeamChoice.TEAM_E));
        assertTrue(!java.util.Arrays.asList(lastStandTeams).contains(PlayerTeamChoice.TEAM_E));
    }

    @Test
    void tutorialEntryUsesAcademyLabel() {
        MainMenuPanel panel = menu();

        try {
            JButton tutorial = (JButton) findByName(panel, "tutorialStartButton");

            assertNotNull(tutorial);
            assertEquals("Commander's Academy", tutorial.getText());
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void mainMenuExposesControlsSettingsButton() {
        MainMenuPanel panel = menu();

        try {
            JButton controls = (JButton) findByName(panel, InputBindingsDialog.CONTROLS_BUTTON_NAME);

            assertNotNull(controls);
            assertEquals("Controls", controls.getText());
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerEntryShowsHostJoinAndDirectAddressWhenMissionFeatureFlagIsEnabled() {
        System.setProperty("game.feature.multiplayer_custom_missions", "true");
        MainMenuPanel panel = menu();

        try {
            assertNotNull(findByName(panel, "multiplayerEntryPanel"));
            assertNotNull(findByName(panel, "multiplayerHostBattleButton"));
            assertNotNull(findByName(panel, "multiplayerJoinBattleButton"));
            assertNull(findByName(panel, "multiplayerDiagnosticsButton"));
            assertNotNull(findByName(panel, "multiplayerDirectAddressField"));
            assertNotNull(findByName(panel, "multiplayerPlayerNameField"));
            assertNotNull(findByName(panel, "multiplayerMissionSelector"));
            assertNotNull(findByName(panel, "multiplayerDebugInfoLabel"));
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerEntryStillSupportsLegacyCustomBattleFeatureFlagAlias() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        MainMenuPanel panel = menu();

        try {
            assertNotNull(findByName(panel, "multiplayerEntryPanel"));
            assertNotNull(findByName(panel, "multiplayerHostBattleButton"));
            assertNotNull(findByName(panel, "multiplayerJoinBattleButton"));
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerDialogExposesGradleAcceptanceLaunchCommands() {
        String host = MainMenuPanel.multiplayerAcceptanceCommandForTests(
                "Host Battle", "192.168.1.20:46717");
        String join = MainMenuPanel.multiplayerAcceptanceCommandForTests(
                "Join Battle", "192.168.1.20:46717");

        assertTrue(host.contains("multiplayerLanAcceptanceHost"));
        assertTrue(host.contains("-PmpPort=46717"));
        assertTrue(join.contains("multiplayerLanAcceptanceClient"));
        assertTrue(join.contains("-PmpAddress=192.168.1.20:46717"));
    }

    @Test
    void multiplayerMissionSelectorExposesEveryMenuMissionMode() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        MainMenuPanel panel = menu();

        try {
            JComboBox<?> missions = (JComboBox<?>) findByName(panel, "multiplayerMissionSelector");
            assertNotNull(missions);
            Set<String> missionIds = new HashSet<>();
            for (int i = 0; i < missions.getItemCount(); i++) {
                Object item = missions.getItemAt(i);
                assertTrue(item instanceof MultiplayerMissionChoice);
                missionIds.add(((MultiplayerMissionChoice) item).missionId());
            }

            Set<String> expected = java.util.Arrays.stream(MultiplayerMissionChoice.values())
                    .map(MultiplayerMissionChoice::missionId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

            assertEquals(expected, missionIds);
            assertTrue(missionIds.contains(MultiplayerMissionChoice.LAST_STAND.missionId()));
            assertTrue(missionIds.contains(MultiplayerMissionChoice.RESOURCE_RUSH.missionId()));
            assertTrue(missionIds.contains(MultiplayerMissionChoice.FOUR_TEAM_DOMINATION.missionId()));
            assertTrue(missionIds.contains(MultiplayerMissionChoice.SHOOTING_RANGE.missionId()));
            assertTrue(missionIds.contains(MultiplayerMissionChoice.SHOWCASE.missionId()));
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void visibleModeDropdownCanSelectMatchingMultiplayerMission() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        MainMenuPanel panel = menu();

        try {
            JComboBox<?> mode = findComboContaining(panel, GameMode.RESOURCE_RUSH);
            JComboBox<?> missions = (JComboBox<?>) findByName(panel, "multiplayerMissionSelector");
            assertNotNull(mode);
            assertNotNull(missions);

            mode.setSelectedItem(GameMode.RESOURCE_RUSH);

            assertEquals(MultiplayerMissionChoice.RESOURCE_RUSH, missions.getSelectedItem());
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerHostButtonStartsInGameHostLaunchConfig() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        AtomicReference<GameConfig> launched = new AtomicReference<>();
        MainMenuPanel panel = menu(launched);

        try {
            JTextField address = (JTextField) findByName(panel, "multiplayerDirectAddressField");
            assertNotNull(address);
            address.setText("192.168.1.20:46718");
            JComboBox<?> missions = (JComboBox<?>) findByName(panel, "multiplayerMissionSelector");
            assertNotNull(missions);
            missions.setSelectedItem(MultiplayerMissionChoice.HEAVY_DUEL);
            JTextField name = (JTextField) findByName(panel, "multiplayerPlayerNameField");
            assertNotNull(name);
            name.setText("Ada");
            JButton host = (JButton) findByName(panel, "multiplayerHostBattleButton");
            assertNotNull(host);

            host.doClick();

            assertNotNull(launched.get());
            assertNotNull(launched.get().multiplayerLaunch);
            assertTrue(launched.get().multiplayerLaunch.host());
            assertEquals(46718, launched.get().multiplayerLaunch.port);
            assertEquals("192.168.1.20", launched.get().multiplayerLaunch.advertisedHostAddress);
            assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    launched.get().multiplayerLaunch.missionId);
            assertEquals("Ada", launched.get().multiplayerLaunch.hostPlayerName);
            assertEquals("Client", launched.get().multiplayerLaunch.clientPlayerName);
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerJoinButtonStartsInGameClientLaunchConfig() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        AtomicReference<GameConfig> launched = new AtomicReference<>();
        MainMenuPanel panel = menu(launched);

        try {
            JTextField address = (JTextField) findByName(panel, "multiplayerDirectAddressField");
            assertNotNull(address);
            address.setText("192.168.1.20:46718");
            JComboBox<?> missions = (JComboBox<?>) findByName(panel, "multiplayerMissionSelector");
            assertNotNull(missions);
            missions.setSelectedItem(MultiplayerMissionChoice.HEAVY_DUEL);
            JTextField name = (JTextField) findByName(panel, "multiplayerPlayerNameField");
            assertNotNull(name);
            name.setText("Grace");
            JButton join = (JButton) findByName(panel, "multiplayerJoinBattleButton");
            assertNotNull(join);

            join.doClick();

            assertNotNull(launched.get());
            assertNotNull(launched.get().multiplayerLaunch);
            assertTrue(!launched.get().multiplayerLaunch.host());
            assertEquals("192.168.1.20:46718", launched.get().multiplayerLaunch.resolvedDirectAddress());
            assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    launched.get().multiplayerLaunch.missionId);
            assertEquals("Host", launched.get().multiplayerLaunch.hostPlayerName);
            assertEquals("Grace", launched.get().multiplayerLaunch.clientPlayerName);
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerDiagnosticsButtonStartsHarnessLaunchConfig() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        System.setProperty("game.dev.menu", "true");
        AtomicReference<GameConfig> launched = new AtomicReference<>();
        MainMenuPanel panel = menu(launched);

        try {
            JTextField address = (JTextField) findByName(panel, "multiplayerDirectAddressField");
            assertNotNull(address);
            address.setText("192.168.1.20:46718");
            JComboBox<?> missions = (JComboBox<?>) findByName(panel, "multiplayerMissionSelector");
            assertNotNull(missions);
            missions.setSelectedItem(MultiplayerMissionChoice.HEAVY_DUEL);
            JTextField name = (JTextField) findByName(panel, "multiplayerPlayerNameField");
            assertNotNull(name);
            name.setText("Lin");
            JButton diagnostics = (JButton) findByName(panel, "multiplayerDiagnosticsButton");
            assertNotNull(diagnostics);

            diagnostics.doClick();

            assertNotNull(launched.get());
            assertNotNull(launched.get().multiplayerLaunch);
            assertTrue(launched.get().multiplayerLaunch.host());
            assertTrue(launched.get().multiplayerLaunch.diagnosticsHarness);
            assertEquals(46718, launched.get().multiplayerLaunch.port);
            assertEquals("192.168.1.20", launched.get().multiplayerLaunch.advertisedHostAddress);
            assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                    launched.get().multiplayerLaunch.missionId);
            assertEquals("Lin", launched.get().multiplayerLaunch.hostPlayerName);
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    private static MainMenuPanel menu() {
        return menu(new AtomicReference<>());
    }

    private static MainMenuPanel menu(AtomicReference<GameConfig> launched) {
        return new MainMenuPanel(
                launched::set,
                () -> {},
                () -> {},
                () -> MainMenuPanel.ResumeCampaignState.unavailable("No checkpoint"),
                null);
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

    private static JComboBox<?> findComboContaining(Component root, Object item) {
        if (root instanceof JComboBox<?> combo) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (item.equals(combo.getItemAt(i))) return combo;
            }
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JComboBox<?> found = findComboContaining(child, item);
                if (found != null) return found;
            }
        }
        return null;
    }
}
