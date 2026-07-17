package app.ui;

import app.config.GameConfig;

import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
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
    }

    @Test
    void multiplayerEntryIsHiddenWhenFeatureFlagIsDisabled() {
        System.setProperty("game.feature.multiplayer_custom_battle", "false");
        MainMenuPanel panel = menu();

        try {
            assertNull(findByName(panel, "multiplayerEntryPanel"));
            assertNull(findByName(panel, "multiplayerHostBattleButton"));
            assertNull(findByName(panel, "multiplayerJoinBattleButton"));
            assertNull(findByName(panel, "multiplayerDirectAddressField"));
        } finally {
            panel.stopBackgroundTimerForTests();
        }
    }

    @Test
    void multiplayerEntryShowsHostJoinAndDirectAddressWhenFeatureFlagIsEnabled() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        MainMenuPanel panel = menu();

        try {
            assertNotNull(findByName(panel, "multiplayerEntryPanel"));
            assertNotNull(findByName(panel, "multiplayerHostBattleButton"));
            assertNotNull(findByName(panel, "multiplayerJoinBattleButton"));
            assertNotNull(findByName(panel, "multiplayerDirectAddressField"));
            assertNotNull(findByName(panel, "multiplayerDebugInfoLabel"));
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
    void multiplayerHostButtonStartsInGameHostLaunchConfig() {
        System.setProperty("game.feature.multiplayer_custom_battle", "true");
        AtomicReference<GameConfig> launched = new AtomicReference<>();
        MainMenuPanel panel = menu(launched);

        try {
            JTextField address = (JTextField) findByName(panel, "multiplayerDirectAddressField");
            assertNotNull(address);
            address.setText("192.168.1.20:46718");
            JButton host = (JButton) findByName(panel, "multiplayerHostBattleButton");
            assertNotNull(host);

            host.doClick();

            assertNotNull(launched.get());
            assertNotNull(launched.get().multiplayerLaunch);
            assertTrue(launched.get().multiplayerLaunch.host());
            assertEquals(46718, launched.get().multiplayerLaunch.port);
            assertEquals("192.168.1.20", launched.get().multiplayerLaunch.advertisedHostAddress);
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
            JButton join = (JButton) findByName(panel, "multiplayerJoinBattleButton");
            assertNotNull(join);

            join.doClick();

            assertNotNull(launched.get());
            assertNotNull(launched.get().multiplayerLaunch);
            assertTrue(!launched.get().multiplayerLaunch.host());
            assertEquals("192.168.1.20:46718", launched.get().multiplayerLaunch.resolvedDirectAddress());
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
}
