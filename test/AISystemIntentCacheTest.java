import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AISystemIntentCacheTest {
    @Test
    void cachedIntentTargetClearsWhenTargetDies() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 4000, 4000, true, 123L, false));
        ctx.ships.clear();

        Player player = new Player(800.0, 1000.0);
        player.faction = Faction.ALLY;
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1000.0, 1000.0);
        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1250.0, 1000.0);

        ally.aiIntentTargetId = enemy.id;
        ally.aiIntentRetargetTimer = 1.0;

        ctx.player = player;
        ctx.ships.add(player);
        ctx.ships.add(ally);
        ctx.ships.add(enemy);

        enemy.alive = false;
        enemy.hp = 0;
        ctx.entityQuery.rebuild(ctx);

        AISystem.update(ctx, GameContext.DT);

        assertEquals(-1, ally.aiIntentTargetId);
        assertEquals(0.0, ally.aiIntentRetargetTimer, 0.0001);
    }
}
