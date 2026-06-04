import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWeaponLockRangeTest {

    @Test
    void scienceNearestLockReachesDoubledPlayerWeaponLockRange() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 1000.0, 3000.0);
        ctx.player.faction = Faction.ALLY;

        Ship pressureTarget = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY,
                ctx.player.x + 3000.0,
                ctx.player.y);
        ctx.ships.add(ctx.player);
        ctx.ships.add(pressureTarget);
        ctx.entityQuery.rebuild(ctx);

        UISystem.scienceLockNearest(ctx);

        assertTrue(TargetingSystem.PLAYER_TARGET_LOCK_RANGE >= 3600.0);
        assertSame(pressureTarget, ctx.lockedTarget);
    }
}
