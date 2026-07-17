import app.config.MultiplayerLaunchConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerInGameLaunchPanelTest {

    @Test
    void hostLaunchUsesJavaHarnessCommand() {
        List<String> command = MultiplayerInGameLaunchPanel.launchCommandForTests(
                MultiplayerLaunchConfig.host(46718, "192.168.1.20"));

        assertTrue(command.contains("MultiplayerLanDuelAcceptanceHarness"));
        assertTrue(command.contains("host"));
        assertTrue(command.contains("--port=46718"));
        assertTrue(command.contains("--host-address=192.168.1.20"));
    }

    @Test
    void clientLaunchUsesDirectAddress() {
        List<String> command = MultiplayerInGameLaunchPanel.launchCommandForTests(
                MultiplayerLaunchConfig.client("192.168.1.20:46718", ""));

        assertTrue(command.contains("MultiplayerLanDuelAcceptanceHarness"));
        assertTrue(command.contains("client"));
        assertTrue(command.contains("--connect=192.168.1.20:46718"));
    }
}
