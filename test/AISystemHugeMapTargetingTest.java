import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemHugeMapTargetingTest {
    @Test
    void hugeMapTargetingDoesNotScoreEveryHostileForEveryDecision() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 60000, 60000, true, 321L, false));
        ctx.ships.clear();

        Player player = new Player(1000.0, 1000.0);
        player.faction = Faction.ALLY;
        ctx.player = player;
        ctx.ships.add(player);

        for (int i = 0; i < 170; i++) {
            ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY,
                    1050.0 + (i % 17) * 36.0,
                    1120.0 + (i / 17) * 36.0));
        }
        for (int i = 0; i < 18; i++) {
            ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY,
                    1650.0 + (i % 6) * 42.0,
                    1120.0 + (i / 6) * 42.0));
        }
        for (int i = 0; i < 150; i++) {
            ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY,
                    30000.0 + (i % 15) * 180.0,
                    30000.0 + (i / 15) * 180.0));
        }

        ctx.entityQuery.rebuild(ctx);
        AISystem.update(ctx, GameContext.DT);

        assertTrue(ctx.perf.aiCheapTargetScores < 80,
                "huge-map targeting should use local candidates and cached focus targets, not score every hostile");
    }
}
