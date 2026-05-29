import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileRoleBehaviorTest {

    @Test
    void interceptMissilesReloadThreeTimesFasterAndRenderSmaller() {
        Player shooter = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        Turret missile = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missile.primary = false;
        missile.cooldown = 0.15;
        missile.missileRole = Turret.MissileRole.INTERCEPT;
        shooter.addTurret(missile);

        Ship target = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 420.0, 0.0);
        missile.setReady();

        Projectile fired = missile.fire(shooter, target, GameContext.DT);
        assertNotNull(fired, "expected an interceptor missile to fire");
        assertTrue(fired instanceof Missile, "expected missile turret to spawn a Missile projectile");
        Missile launched = (Missile) fired;
        assertEquals(Turret.MissileRole.INTERCEPT, launched.role, "expected interceptor role tagging");
        assertEquals(0.5, launched.visualScale, 1e-6, "AAA missiles should render at half size");
        assertEquals(Ship.MISSILE_MIN_RELOAD_SECONDS / 3.0, missile.getCooldownRemaining(), 1e-6,
                "AAA missiles should cycle three times faster than the normal missile floor");
    }

    @Test
    void fastMissilesCanFireAcrossTheirCurrentSector() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, 1234L, false));
        ctx.enemyWaveTimer = 9999.0;

        Player player = new Player(ShipRole.FRIGATE, 300.0, 2500.0);
        ctx.player = player;
        ctx.ships.add(player);

        Ship shooter = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 500.0, 2500.0);
        Turret missileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missileTurret.missileRole = Turret.MissileRole.ANTI_LIGHT;
        missileTurret.primary = false;
        missileTurret.turnRate = Math.toRadians(720.0);
        missileTurret.angle = 0.0;
        missileTurret.setReady();
        shooter.addTurret(missileTurret);
        ctx.ships.add(shooter);

        Ship distantTarget = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 4600.0, 2500.0);
        ctx.ships.add(distantTarget);
        ctx.entityQuery.rebuild(ctx);

        Method fireIfAble = AISystem.class.getDeclaredMethod(
                "fireIfAble", GameContext.class, Ship.class, Ship.class, double.class, double.class);
        fireIfAble.setAccessible(true);
        int firedCount = (int) fireIfAble.invoke(
                null, ctx, shooter, distantTarget, GameContext.DT, Math.hypot(distantTarget.x - shooter.x, distantTarget.y - shooter.y));

        assertTrue(firedCount > 0, "expected FAST missiles to fire across the current sector");
        assertTrue(ctx.projectiles.get(0) instanceof Missile, "expected a missile projectile from the shot");
        Missile missile = (Missile) ctx.projectiles.get(0);
        assertSame(distantTarget, missile.target, "FAST missile should lock the visible hostile in the same sector");
    }

    @Test
    void interceptMissilesCanHitEnemyMissiles() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 4321L, false));

        Ship ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        Ship enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 200.0, 0.0);
        Missile hostileMissile = new Missile(180.0, 0.0, Math.PI, ally, GameContext.DT, 320.0, Math.toRadians(280.0), 2, 240, 7.0, Faction.ENEMY);
        Missile interceptor = new Missile(168.0, 0.0, 0.0, enemy, GameContext.DT, 420.0, Math.toRadians(480.0), 4, 240, 7.0, Faction.ALLY);
        interceptor.role = Turret.MissileRole.INTERCEPT;
        interceptor.projectileTarget = hostileMissile;

        ctx.ships.add(ally);
        ctx.ships.add(enemy);
        ctx.projectiles.add(interceptor);
        ctx.projectiles.add(hostileMissile);
        ctx.entityQuery.rebuild(ctx);

        CollisionSystem.handleProjectilesVsProjectiles(ctx, ctx.projectiles);

        assertFalse(interceptor.alive, "interceptor missile should expend itself on missile interception");
        assertFalse(hostileMissile.alive, "enemy missile should be destroyed by the AAA intercept");
    }

    @Test
    void heavyMissilesHaveExtendedRangeAndSmallerVisuals() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.LAST_STAND, 5000, 5000, true, 55L, false));
        Ship shooter = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        Turret missileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missileTurret.missileRole = Turret.MissileRole.ANTI_HEAVY;
        shooter.addTurret(missileTurret);

        Method rangeMethod = AISystem.class.getDeclaredMethod(
                "missileRangeForTurret", GameContext.class, Ship.class, Turret.class, double.class);
        rangeMethod.setAccessible(true);
        double heavyRange = (double) rangeMethod.invoke(null, ctx, shooter, missileTurret, 1300.0);
        assertTrue(heavyRange >= 1872.0 - 1e-6, "heavy missiles should have double their prior effective range");

        missileTurret.setReady();
        Ship target = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 900.0, 0.0);
        Projectile fired = missileTurret.fire(shooter, target, GameContext.DT);
        assertTrue(fired instanceof Missile, "expected heavy turret to spawn a missile");
        Missile missile = (Missile) fired;
        assertEquals(0.5, missile.visualScale, 1e-6, "heavy missiles should render at half size");
    }

    @Test
    void missilesStopTrackingTargetsThatAreAlreadyDying() {
        Ship target = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 0.0, 400.0);
        target.dying = true;
        Missile missile = new Missile(0.0, 0.0, 0.0, target, GameContext.DT, 300.0, Math.toRadians(280.0), 5, 240, 7.0, Faction.ALLY);

        missile.update(0.25);

        assertEquals(0.0, missile.angle, 1e-6, "AAA missiles should not keep steering around already-dead fighters");
    }

    @Test
    void tacticalStrikeTorpedoBypassesShieldAndArmorOnBattleshipAndCiwsCorvette() {
        Faction.clearCampaignAlliances();
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 7878L, false));
        Ship launcher = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0.0, 0.0);

        Ship battleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ENEMY, 280.0, 0.0);
        battleship.shield = Math.max(battleship.shield, 240.0);
        battleship.shieldMax = Math.max(battleship.shieldMax, battleship.shield);

        Ship corvette = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ENEMY, 420.0, 0.0);
        corvette.shield = Math.max(corvette.shield, 140.0);
        corvette.shieldMax = Math.max(corvette.shieldMax, corvette.shield);

        Missile strikeBattleship = new Missile(0.0, 0.0, 0.0, battleship, GameContext.DT, 360.0, Math.toRadians(360.0), 14, 900, 10.0, Faction.ALLY);
        strikeBattleship.strikeVisual = Missile.StrikeVisual.TORPEDO;
        strikeBattleship.x = battleship.x;
        strikeBattleship.y = battleship.y;

        Missile strikeCorvette = new Missile(0.0, 0.0, 0.0, corvette, GameContext.DT, 360.0, Math.toRadians(360.0), 14, 900, 10.0, Faction.ALLY);
        strikeCorvette.strikeVisual = Missile.StrikeVisual.TORPEDO;
        strikeCorvette.x = corvette.x;
        strikeCorvette.y = corvette.y;

        ctx.ships.add(launcher);
        ctx.ships.add(battleship);
        ctx.ships.add(corvette);
        ctx.projectiles.add(strikeBattleship);
        ctx.projectiles.add(strikeCorvette);
        ctx.entityQuery.rebuild(ctx);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        assertEquals(0.0, battleship.shield, 1e-6, "tactical torpedo strike should bypass and strip battleship shields");
        assertEquals(0.0, corvette.shield, 1e-6, "tactical torpedo strike should bypass and strip corvette shields");
        assertTrue(!battleship.alive || battleship.hp <= 0, "tactical torpedo strike should one-shot battleship-class targets");
        assertTrue(!corvette.alive || corvette.hp <= 0, "tactical torpedo strike should one-shot smaller-than-battleship targets");
    }

}
