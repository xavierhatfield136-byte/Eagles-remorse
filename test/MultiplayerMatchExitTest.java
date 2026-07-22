import app.config.MultiplayerLaunchConfig;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerMatchExitTest {

    @Test
    void leavingMultiplayerMatchUsesMenuCallbackWithoutTerminatingApplication() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        AtomicBoolean returnedToMenu = new AtomicBoolean(false);
        GamePanel panel = new GamePanel(ctx, () -> returnedToMenu.set(true), () -> {});

        try {
            Action toMenu = panel.getActionMap().get("toMenu");
            assertNotNull(toMenu);

            toMenu.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "toMenu"));

            assertTrue(returnedToMenu.get());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void activeMatchPeerDisconnectUsesMenuCallbackWithoutTerminatingApplication() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.disconnectedForTests("Host disconnected");
        AtomicBoolean returnedToMenu = new AtomicBoolean(false);
        GamePanel panel = new GamePanel(ctx, () -> returnedToMenu.set(true), () -> {});

        try {
            panel.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "tick"));

            assertTrue(returnedToMenu.get());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void activeMatchTransportErrorUsesMenuCallbackWithoutTerminatingApplication() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.errorForTests("Transport failed");
        AtomicBoolean returnedToMenu = new AtomicBoolean(false);
        GamePanel panel = new GamePanel(ctx, () -> returnedToMenu.set(true), () -> {});

        try {
            panel.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "tick"));

            assertTrue(returnedToMenu.get());
        } finally {
            panel.shutdown();
        }
    }
}
