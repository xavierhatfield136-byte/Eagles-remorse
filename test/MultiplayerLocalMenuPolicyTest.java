import app.config.MultiplayerLaunchConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiplayerLocalMenuPolicyTest {

    @Test
    void escapeWithoutOverlayReleasesInputsButDoesNotPauseMultiplayerSimulation() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        AtomicBoolean exited = new AtomicBoolean(false);
        ctx.state = GameState.RUNNING;
        ctx.firingPrimaryManual = true;
        ctx.firingPrimaryManualLatched = true;
        ctx.firingSecondaryManual = true;
        ctx.firingSecondaryManualLatched = true;

        GameplayActions.handleEscape(ctx, () -> exited.set(true));

        assertEquals(GameState.RUNNING, ctx.state);
        assertFalse(ctx.firingPrimaryManual);
        assertFalse(ctx.firingPrimaryManualLatched);
        assertFalse(ctx.firingSecondaryManual);
        assertFalse(ctx.firingSecondaryManualLatched);
        assertFalse(exited.get());
    }

    @Test
    void escapeClosesLocalOverlayWithoutPausingMultiplayerSimulation() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        ctx.state = GameState.MAP;
        ctx.ui.mapOpen = true;
        ctx.firingPrimaryManual = true;

        GameplayActions.handleEscape(ctx, () -> {});

        assertEquals(GameState.RUNNING, ctx.state);
        assertFalse(ctx.ui.mapOpen);
        assertFalse(ctx.firingPrimaryManual);
    }
}
