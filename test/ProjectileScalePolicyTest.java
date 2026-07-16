import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileScalePolicyTest {
    @Test
    void pressureShortensCiwsLifeAndKeepsInterceptMissileRetargeting() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 7L, false));
        ctx.projectiles.clear();
        Ship owner = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1000.0, 1000.0);
        Ship target = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1200.0, 1000.0);
        for (int i = 0; i < 720; i++) {
            ctx.projectiles.add(new CIWSPellet(1000.0 + i, 1000.0, 0.0, GameContext.DT,
                    900.0, 1, 80, 1.5, Faction.ALLY));
        }
        ProjectileScalePolicy.FramePlan plan = ProjectileScalePolicy.planFor(ctx, 1);
        Missile intercept = new Missile(owner.x, owner.y, 0.0, target, GameContext.DT, owner.faction);
        intercept.role = Turret.MissileRole.INTERCEPT;

        assertTrue(plan.underPressure());
        assertEquals(5, plan.missileRetargetStride);
        assertTrue(plan.shouldRetargetMissile(intercept, false));
        assertTrue(plan.ciwsPelletsForBurst(12) < 12);
        assertTrue(plan.ciwsLifeFor(80) <= 4);
    }
}
