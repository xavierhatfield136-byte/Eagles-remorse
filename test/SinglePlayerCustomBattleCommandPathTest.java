import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinglePlayerCustomBattleCommandPathTest {

    @Test
    void customBattleRuntimeRoutesAcceptedInputThroughCommandGate() throws Exception {
        GameContext ctx = customBattleContext();
        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        Method tick = GameSimulationRuntime.class.getDeclaredMethod(
                "tick", double.class, InputSnapshot.class, int.class, int.class);
        tick.setAccessible(true);

        tick.invoke(runtime, GameContext.DT,
                new InputSnapshot(true, false, false, true, false, 640.0, 360.0),
                1280, 720);

        assertTrue(runtime.lastCustomBattleCommandResultForTests().accepted());
        assertTrue(Math.abs(ctx.player.vx) > 1e-6 || Math.abs(ctx.player.vy) > 1e-6,
                "accepted custom-battle input should still drive the player");
    }

    @Test
    void duplicateCustomBattleInputIsRejectedAndNeutralizedForSinglePlayerToo() {
        GameContext ctx = customBattleContext();
        ctx.firingPrimaryManual = true;
        SinglePlayerCustomBattleCommandPath path = new SinglePlayerCustomBattleCommandPath();
        InputSnapshot input = new InputSnapshot(true, false, false, false, false, 700.0, 360.0);

        SinglePlayerCustomBattleCommandPath.RoutedInput first =
                path.routeWithSequenceForTests(ctx, input, 800.0, 500.0, 3L, 3L);
        SinglePlayerCustomBattleCommandPath.RoutedInput duplicate =
                path.routeWithSequenceForTests(ctx, input, 800.0, 500.0, 3L, 4L);

        assertTrue(first.accepted(), first.result.reason());
        assertFalse(duplicate.accepted(), duplicate.result.reason());
        assertFalse(duplicate.movementInput.up, "rejected input should not keep thrusting");
        assertFalse(duplicate.primaryHeldForTick, "rejected input should not keep firing");
    }

    private static GameContext customBattleContext() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CUSTOM_BATTLES, 3000, 2200, true, 5150L, false));
        Player player = new Player(ShipRole.FRIGATE, 1000.0, 1000.0);
        player.faction = Faction.ALLY;
        ctx.player = player;
        ctx.ships.add(player);
        ctx.entityQuery.rebuild(ctx);
        return ctx;
    }
}
