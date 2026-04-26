import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpAndStrikeCraftRegressionTest {

    @Test
    void carrierOwnedSmallCraftMoveWithWarpingParent() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, 77L, false));
        Ship carrier = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 600.0, 2500.0);
        Ship fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 750.0, 2500.0);
        fighter.carrierOwnerId = carrier.id;
        ctx.ships.add(carrier);
        ctx.ships.add(fighter);

        assertTrue(carrier.beginBattlefieldWarp(7400.0, 2500.0, 0.1));
        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        Method updateWarp = GameSimulationRuntime.class.getDeclaredMethod("updateSingleBattlefieldWarp", Ship.class, double.class);
        updateWarp.setAccessible(true);
        updateWarp.invoke(runtime, carrier, 1.0);

        assertFalse(carrier.isWarpCharging(), "carrier should complete the warp");
        assertTrue(Math.abs(fighter.x - carrier.x) < carrier.radius + 260.0,
                "fighter should be repositioned with its carrier after warp");
        assertTrue(Math.abs(fighter.y - carrier.y) < carrier.radius + 260.0,
                "fighter should stay close to its carrier after warp");
    }

    @Test
    void smallCraftSkipDeathAnimationAndExplodeImmediately() throws Exception {
        Ship fighter = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 0.0, 0.0);
        Method startDeathSequence = Ship.class.getDeclaredMethod("startDeathSequence");
        startDeathSequence.setAccessible(true);
        startDeathSequence.invoke(fighter);

        assertFalse(fighter.alive, "small craft should be fully destroyed immediately");
        assertFalse(fighter.dying, "small craft should not linger in the dying animation state");
    }

    @Test
    void dedicatedAaHullsCanAutoSwitchToAaaButGeneralHullsDoNot() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.LAST_STAND, 5000, 5000, true, 91L, false));
        Ship generalist = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        Turret frigateMissileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        generalist.addTurret(frigateMissileTurret);
        ctx.ships.add(generalist);

        Ship aaShip = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ALLY, 80.0, 0.0);
        Turret aaMissileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        aaShip.addTurret(aaMissileTurret);
        ctx.ships.add(aaShip);

        Ship allyAnchor = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, -120.0, 0.0);
        Ship enemyFighter = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 600.0, 0.0);
        Missile enemyMissile = new Missile(220.0, 0.0, Math.PI, allyAnchor, GameContext.DT, 320.0, Math.toRadians(280.0), 3, 240, 7.0, Faction.ENEMY);
        ctx.ships.add(allyAnchor);
        ctx.ships.add(enemyFighter);
        ctx.projectiles.add(enemyMissile);
        ctx.entityQuery.rebuild(ctx);

        Method adapt = AISystem.class.getDeclaredMethod("adaptMissileRolesToThreats", GameContext.class, Ship.class);
        adapt.setAccessible(true);

        adapt.invoke(null, ctx, generalist);
        adapt.invoke(null, ctx, aaShip);
        assertEquals(Turret.MissileRole.ANTI_LIGHT, frigateMissileTurret.missileRole,
                "general-purpose hulls should not auto-switch to AAA just because a missile appears");
        assertEquals(Turret.MissileRole.INTERCEPT, aaMissileTurret.missileRole,
                "dedicated AA hulls should still auto-switch to AAA when missiles are nearby");

        ctx.projectiles.clear();
        ctx.entityQuery.rebuild(ctx);
        adapt.invoke(null, ctx, generalist);
        adapt.invoke(null, ctx, aaShip);
        assertEquals(Turret.MissileRole.ANTI_LIGHT, frigateMissileTurret.missileRole,
                "nearby hostile fighters should force FAST mode when there is no missile threat");
        assertEquals(Turret.MissileRole.ANTI_LIGHT, aaMissileTurret.missileRole,
                "AA hulls should still use FAST missiles against small craft when there is no missile threat");
    }

    @Test
    void manuallyConfiguredAaaLaunchersAreNotOverwrittenOnGeneralHulls() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.LAST_STAND, 5000, 5000, true, 91L, false));
        Ship shooter = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        Turret missileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missileTurret.missileRole = Turret.MissileRole.INTERCEPT;
        shooter.addTurret(missileTurret);
        ctx.ships.add(shooter);

        Ship enemyCapital = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 900.0, 0.0);
        ctx.ships.add(enemyCapital);
        ctx.entityQuery.rebuild(ctx);

        Method adapt = AISystem.class.getDeclaredMethod("adaptMissileRolesToThreats", GameContext.class, Ship.class);
        adapt.setAccessible(true);
        adapt.invoke(null, ctx, shooter);

        assertEquals(Turret.MissileRole.INTERCEPT, missileTurret.missileRole,
                "campaign-installed AAA launchers should stay AAA on general-purpose hulls");
    }

    @Test
    void playerCannotToggleGeneralPurposeHullIntoAaaModeMidBattle() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.LAST_STAND, 5000, 5000, true, 91L, false));
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        Turret missileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missileTurret.missileRole = Turret.MissileRole.ANTI_LIGHT;
        player.addTurret(missileTurret);
        ctx.player = player;

        Method setPlayerMissileRole = UISystem.class.getDeclaredMethod(
                "setPlayerMissileRole", GameContext.class, Turret.MissileRole.class, String.class);
        setPlayerMissileRole.setAccessible(true);
        setPlayerMissileRole.invoke(null, ctx, Turret.MissileRole.INTERCEPT, "MISSILE MODE: AAA");

        assertEquals(Turret.MissileRole.ANTI_LIGHT, missileTurret.missileRole,
                "general-purpose hulls should not gain AAA missiles from the in-battle toggle");
    }
}
