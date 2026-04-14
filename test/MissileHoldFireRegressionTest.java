import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileHoldFireRegressionTest {

    @Test
    void heldSecondaryFireOnlyLaunchesOncePerPress() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        player.addMissileTurret();
        ctx.player = player;
        ctx.ships.add(player);

        Ship target = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 420.0, 0.0);
        ctx.ships.add(target);
        ctx.lockedTarget = target;

        ctx.firingSecondaryManual = true;
        PhysicsSystem.update(ctx, GameContext.DT);
        int afterFirstUpdate = ctx.projectiles.size();
        assertTrue(afterFirstUpdate > 0, "expected one missile volley from the held press");

        PhysicsSystem.update(ctx, GameContext.DT);
        assertEquals(afterFirstUpdate, ctx.projectiles.size(),
                "holding the missile key should not auto-repeat the volley");

        ctx.firingSecondaryManual = false;
        ctx.firingSecondaryManualLatched = false;
        player.turrets.stream()
                .filter(t -> t != null && t.kind == Turret.Kind.MISSILE)
                .findFirst()
                .orElseThrow()
                .setReady();

        ctx.firingSecondaryManual = true;
        PhysicsSystem.update(ctx, GameContext.DT);
        assertTrue(ctx.projectiles.size() > afterFirstUpdate,
                "a fresh press after release should be able to launch again");
    }

    @Test
    void yellowPrimaryMissilesOnlyLaunchOncePerPress() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        player.faction = Faction.TEAM_D;
        player.applyHull(ShipRole.FRIGATE, 0.0, 0.0);
        ctx.player = player;
        ctx.ships.add(player);

        Ship target = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 420.0, 0.0);
        ctx.ships.add(target);

        ctx.firingPrimaryManual = true;
        PhysicsSystem.update(ctx, GameContext.DT);
        int afterFirstUpdate = ctx.projectiles.size();
        assertTrue(afterFirstUpdate > 0, "expected one missile volley from the held yellow primary fire");

        PhysicsSystem.update(ctx, GameContext.DT);
        assertEquals(afterFirstUpdate, ctx.projectiles.size(),
                "holding the yellow primary fire button should not auto-repeat the missile volley");

        ctx.firingPrimaryManual = false;
        ctx.firingPrimaryManualLatched = false;
        readyAllPrimaryMissiles(player);

        ctx.firingPrimaryManual = true;
        PhysicsSystem.update(ctx, GameContext.DT);
        assertTrue(ctx.projectiles.size() > afterFirstUpdate,
                "a fresh press after release should be able to launch the yellow volley again");
    }

    @Test
    void missileTurretsRespectTheOneSecondReloadFloor() {
        Player shooter = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        Turret missile = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missile.primary = false;
        missile.cooldown = 0.15;
        shooter.addTurret(missile);

        Ship target = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 420.0, 0.0);
        missile.setReady();

        Projectile first = missile.fire(shooter, target, GameContext.DT);
        assertTrue(first != null, "expected a missile volley from the ready turret");
        assertTrue(missile.getCooldownRemaining() >= Ship.MISSILE_MIN_RELOAD_SECONDS - 1e-6,
                "missile turrets should enter the reload floor after firing");
        assertNull(missile.fire(shooter, target, GameContext.DT),
                "a missile turret should not be able to fire again immediately");

        missile.update(Ship.MISSILE_MIN_RELOAD_SECONDS);
        assertTrue(missile.fire(shooter, target, GameContext.DT) != null,
                "after the reload floor elapses, the missile turret should fire again");
    }

    private static void readyAllPrimaryMissiles(Ship ship) {
        if (ship == null || ship.turrets == null) return;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.primary && turret.kind == Turret.Kind.MISSILE) {
                turret.setReady();
            }
        }
    }
}
