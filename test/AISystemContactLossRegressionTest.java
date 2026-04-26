import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemContactLossRegressionTest {

    @Test
    void idleEnemyWanderFallsBackToFactionAnchorNotPlayerPosition() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 424242L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemyBase = new FleetShip(ShipRole.BASE, Faction.ENEMY, 4500.0, 2500.0);
        FleetShip enemy = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 4000.0, 2500.0);
        ctx.ships.add(enemyBase);
        ctx.ships.add(enemy);

        Method wander = AISystem.class.getDeclaredMethod("wander", GameContext.class, Ship.class, double.class);
        wander.setAccessible(true);
        wander.invoke(null, ctx, enemy, 1.0);

        assertTrue(enemy.vx > 0.0,
                "idle enemy fallback should bias toward its own fleet anchor instead of drifting toward the player's mothership");
    }
}
