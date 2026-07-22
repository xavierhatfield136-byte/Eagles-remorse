package app.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellThreadOwnershipTest {

    @Test
    void appShellConstructionRequiresEventDispatchThread() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new AppShell(
                        (config, showMenu, toggleFullscreen) -> null,
                        () -> MainMenuPanel.ResumeCampaignState.unavailable("No checkpoint"),
                        null,
                        () -> {}));

        assertTrue(ex.getMessage().contains("Event Dispatch Thread"));
    }
}
