import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

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
    void offensiveMissilesFireInsideStandardProsecutionRange() throws Exception {
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

        Ship distantTarget = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3300.0, 2500.0);
        ctx.ships.add(distantTarget);
        ctx.entityQuery.rebuild(ctx);

        Method fireIfAble = AISystem.class.getDeclaredMethod(
                "fireIfAble", GameContext.class, Ship.class, Ship.class, double.class, double.class);
        fireIfAble.setAccessible(true);
        int firedCount = (int) fireIfAble.invoke(
                null, ctx, shooter, distantTarget, GameContext.DT, Math.hypot(distantTarget.x - shooter.x, distantTarget.y - shooter.y));

        assertTrue(firedCount > 0, "expected offensive missiles to fire inside the standard prosecution range");
        Missile missile = ctx.projectiles.stream()
                .filter(Missile.class::isInstance)
                .map(Missile.class::cast)
                .findFirst()
                .orElse(null);
        assertNotNull(missile, "expected a missile projectile from the shot");
        assertSame(distantTarget, missile.target, "missile should lock the visible hostile inside the prosecution envelope");
    }

    @Test
    void playerOffensiveSecondaryMissilesAcquireTargetsAtThreeThousandMeters() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 7000, 4200, true, 2222L, false));
        Player player = new Player(ShipRole.FRIGATE, 1000.0, 2100.0);
        player.faction = Faction.ALLY;
        player.turrets.clear();
        ctx.player = player;

        Turret heavyRack = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        heavyRack.primary = false;
        heavyRack.missileRole = Turret.MissileRole.ANTI_HEAVY;
        heavyRack.angle = 0.0;
        heavyRack.setReady();
        player.addTurret(heavyRack);

        Ship target = new FleetShip(ShipRole.CRUISER, Faction.ENEMY,
                player.x + TargetingSystem.COMBAT_TARGETING_RANGE - 40.0,
                player.y);
        ctx.ships.add(player);
        ctx.ships.add(target);
        ctx.entityQuery.rebuild(ctx);

        assertSame(target, TargetingSystem.findClosestEnemyToPoint(ctx, player, player.x, player.y,
                        TargetingSystem.COMBAT_TARGETING_RANGE),
                "targeting should acquire the hostile inside the 3,000m combat envelope");

        Method selectSecondary = Player.class.getDeclaredMethod(
                "selectSecondaryMissileTarget", GameContext.class, Turret.class, Ship.class);
        selectSecondary.setAccessible(true);
        Ship acquired = (Ship) selectSecondary.invoke(player, ctx, heavyRack, null);

        assertSame(target, acquired,
                "player offensive secondary racks should choose visible hostiles inside the combat envelope");
    }

    @Test
    void blueFastMissilesLaunchAtControlledSpeedForPlayerAndAllies() {
        Ship target = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 600.0, 0.0);
        double originalBlueFastSpeed = Math.min(
                220.0 * Turret.MISSILE_SPEED_MULT * Missile.GLOBAL_SPEED_MULT * 2.35,
                Missile.MAX_RUNTIME_SPEED_M_PER_SEC);

        Player player = new Player(ShipRole.MOTHERSHIP, 0.0, 0.0);
        Missile playerMissile = fireFastMissile(player, target);
        assertEquals(originalBlueFastSpeed * Turret.BLUE_FAST_MISSILE_SPEED_MULT, playerMissile.speed, 1e-6,
                "player mothership fast missiles should launch at the controlled blue fast-missile speed");

        Ship ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        Missile allyMissile = fireFastMissile(ally, target);
        assertEquals(originalBlueFastSpeed * Turret.BLUE_FAST_MISSILE_SPEED_MULT, allyMissile.speed, 1e-6,
                "blue-team fast missiles should launch at the controlled blue fast-missile speed");

        Ship red = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 0.0, 0.0);
        Ship blueTarget = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 600.0, 0.0);
        Missile redMissile = fireFastMissile(red, blueTarget);
        assertEquals(originalBlueFastSpeed, redMissile.speed, 1e-6,
                "non-blue fast missile tuning should keep its previous speed");
    }

    @Test
    void blueFastMissilesClampConvertedHighSpeedRacks() {
        Ship target = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 600.0, 0.0);
        Player player = new Player(ShipRole.MOTHERSHIP, 0.0, 0.0);
        Turret rack = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        rack.primary = false;
        rack.missileRole = Turret.MissileRole.ANTI_LIGHT;
        rack.missileSpeed = 960.0;
        rack.setReady();
        player.addTurret(rack);

        Projectile fired = rack.fire(player, target, GameContext.DT);

        assertTrue(fired instanceof Missile, "expected high-speed fast rack to launch a missile");
        Missile missile = (Missile) fired;
        double expectedCap = Math.min(
                Turret.BLUE_FAST_MISSILE_MAX_PRE_FACTION_SPEED * Missile.GLOBAL_SPEED_MULT * 2.35,
                Missile.MAX_RUNTIME_SPEED_M_PER_SEC);
        assertEquals(expectedCap, missile.speed, 1e-6,
                "converted blue fast missile racks should clamp before global/faction speed multipliers");
    }

    @Test
    void missileRuntimeSpeedsAreCappedByRole() {
        Ship target = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 600.0, 0.0);
        Missile missile = new Missile(0.0, 0.0, 0.0, target, GameContext.DT,
                900.0, Math.toRadians(280.0), 5, 240, 7.0, Faction.ALLY);
        assertEquals(Missile.MAX_RUNTIME_SPEED_M_PER_SEC, missile.speed, 1e-6,
                "regular missiles should never launch faster than the global missile ceiling");

        missile.applyRoleSpeedCap(Turret.MissileRole.ANTI_HEAVY, GameContext.DT);
        assertEquals(Missile.HEAVY_RUNTIME_SPEED_M_PER_SEC, missile.speed, 1e-6,
                "heavy missiles should travel at the heavy missile ceiling");
    }

    @Test
    void bombersLaunchAntiShipTorpedoesAtStandardMissileSpeed() {
        FleetShip bomber = new FleetShip(ShipRole.BOMBER, Faction.ALLY, 0.0, 0.0);
        Turret rack = bomber.turrets.stream()
                .filter(turret -> turret.kind == Turret.Kind.MISSILE)
                .findFirst()
                .orElse(null);
        assertNotNull(rack, "bomber should mount a missile rack");
        assertEquals(Turret.MissileRole.ANTI_HEAVY, rack.missileRole,
                "bomber racks should be anti-ship torpedo racks");

        Ship target = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 900.0, 0.0);
        rack.setReady();
        Projectile fired = rack.fire(bomber, target, GameContext.DT);

        assertTrue(fired instanceof Missile, "expected bomber rack to launch a torpedo");
        Missile torpedo = (Missile) fired;
        assertEquals(Turret.MissileRole.ANTI_HEAVY, torpedo.role, "bomber torpedoes should be anti-heavy");
        assertEquals(Missile.MAX_RUNTIME_SPEED_M_PER_SEC, torpedo.speed, 1e-6,
                "bomber anti-ship torpedoes should use the standard missile speed cap");
    }

    @Test
    void antiShipTorpedoesRetargetOnlyNonSmallCraftWhenOriginalTargetDies() {
        Faction.clearCampaignAlliances();
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 9090L, false));
        Ship shooter = new FleetShip(ShipRole.BOMBER, Faction.ALLY, 0.0, 0.0);
        Ship destroyedTarget = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 600.0, 0.0);
        destroyedTarget.alive = false;
        destroyedTarget.hp = 0;
        Ship fighter = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 120.0, 0.0);
        Ship replacement = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 900.0, 0.0);

        Missile torpedo = new Missile(0.0, 0.0, 0.0, destroyedTarget, GameContext.DT,
                360.0, Math.toRadians(280.0), 5, 240, 7.0, Faction.ALLY);
        torpedo.role = Turret.MissileRole.ANTI_HEAVY;
        torpedo.applyRoleSpeedCap(torpedo.role, GameContext.DT);
        ctx.ships.add(shooter);
        ctx.ships.add(destroyedTarget);
        ctx.ships.add(fighter);
        ctx.ships.add(replacement);
        ctx.projectiles.add(torpedo);

        PhysicsSystem.update(ctx, GameContext.DT);

        assertTrue(torpedo.alive, "torpedo should stay alive when a non-fighter target remains");
        assertSame(replacement, torpedo.target,
                "anti-ship torpedoes should skip fighters and retarget another ship");
    }

    @Test
    void antiShipTorpedoesDetonateWhenNoNonSmallCraftTargetsRemain() {
        Faction.clearCampaignAlliances();
        Explosion.active.clear();
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 9191L, false));
        Ship shooter = new FleetShip(ShipRole.BOMBER, Faction.ALLY, 0.0, 0.0);
        Ship destroyedTarget = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 600.0, 0.0);
        destroyedTarget.alive = false;
        destroyedTarget.hp = 0;
        Ship fighter = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 120.0, 0.0);

        Missile torpedo = new Missile(0.0, 0.0, 0.0, destroyedTarget, GameContext.DT,
                360.0, Math.toRadians(280.0), 5, 240, 7.0, Faction.ALLY);
        torpedo.role = Turret.MissileRole.ANTI_HEAVY;
        torpedo.applyRoleSpeedCap(torpedo.role, GameContext.DT);
        ctx.ships.add(shooter);
        ctx.ships.add(destroyedTarget);
        ctx.ships.add(fighter);
        ctx.projectiles.add(torpedo);

        PhysicsSystem.update(ctx, GameContext.DT);

        assertFalse(torpedo.alive, "torpedo should detonate instead of loitering when only fighters remain");
        assertTrue(ctx.projectiles.isEmpty(), "detonated torpedo should be removed from active projectiles");
        assertFalse(Explosion.active.isEmpty(), "detonation should spawn an explosion effect");
    }

    @Test
    void secondaryBurstFollowersReuseLeaderRuntimeSpeed() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 1224L, false));
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        player.faction = Faction.ENEMY;
        player.turrets.clear();
        ctx.player = player;

        Turret rack = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        rack.primary = false;
        rack.missileRole = Turret.MissileRole.ANTI_MEDIUM;
        rack.missileSpeed = 180.0;
        rack.setReady();
        player.addTurret(rack);

        Ship target = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 600.0, 0.0);
        List<Projectile> fired = player.fireSecondary(ctx, target, GameContext.DT);

        assertEquals(3, fired.size(), "secondary launch should create a three-missile burst");
        double leaderSpeed = ((Missile) fired.get(0)).speed;
        assertTrue(leaderSpeed < Missile.MAX_RUNTIME_SPEED_M_PER_SEC,
                "test setup should keep the leader below the cap so double-multiplication is visible");
        for (Projectile projectile : fired) {
            assertTrue(projectile instanceof Missile, "secondary burst should only contain missiles");
            assertEquals(leaderSpeed, ((Missile) projectile).speed, 1e-6,
                    "burst followers should inherit the leader runtime speed without another global/faction multiply");
        }
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

    private static Missile fireFastMissile(Ship shooter, Ship target) {
        Turret missileTurret = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        missileTurret.missileRole = Turret.MissileRole.ANTI_LIGHT;
        missileTurret.primary = false;
        missileTurret.angle = 0.0;
        missileTurret.setReady();
        shooter.addTurret(missileTurret);
        Projectile fired = missileTurret.fire(shooter, target, GameContext.DT);
        assertTrue(fired instanceof Missile, "expected fast missile launch");
        return (Missile) fired;
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
        assertEquals(3000.0, heavyRange, 1e-6, "heavy missiles should use the standard prosecution range");

        missileTurret.setReady();
        Ship target = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 900.0, 0.0);
        Projectile fired = missileTurret.fire(shooter, target, GameContext.DT);
        assertTrue(fired instanceof Missile, "expected heavy turret to spawn a missile");
        Missile missile = (Missile) fired;
        assertEquals(Missile.HEAVY_RUNTIME_SPEED_M_PER_SEC, missile.speed, 1e-6,
                "heavy missiles should travel at the heavy missile speed");
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
    void tacticalStrikeTorpedoStripsShieldsButRespectsArmor() {
        Faction.clearCampaignAlliances();
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 7878L, false));
        Ship launcher = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0.0, 0.0);

        Ship battleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ENEMY, 280.0, 0.0);
        battleship.shield = Math.max(battleship.shield, 240.0);
        battleship.shieldMax = Math.max(battleship.shieldMax, battleship.shield);
        ShipRoomLayout.RoomId battleshipArmor = ShipRoomLayout.RoomId.BOW_ARMOR;
        double armorBefore = roomHp(battleship, battleshipArmor);
        int battleshipEventsBefore = battleship.recentRoomDamageEvents().size();

        Ship corvette = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ENEMY, 420.0, 0.0);
        corvette.shield = Math.max(corvette.shield, 140.0);
        corvette.shieldMax = Math.max(corvette.shieldMax, corvette.shield);

        Missile strikeBattleship = new Missile(0.0, 0.0, Math.PI, battleship, GameContext.DT, 360.0, Math.toRadians(360.0), 14, 900, 10.0, Faction.ALLY);
        strikeBattleship.strikeVisual = Missile.StrikeVisual.TORPEDO;
        strikeBattleship.x = battleship.x + battleship.radius;
        strikeBattleship.y = battleship.y;

        Missile strikeCorvette = new Missile(0.0, 0.0, Math.PI, corvette, GameContext.DT, 360.0, Math.toRadians(360.0), 14, 900, 10.0, Faction.ALLY);
        strikeCorvette.strikeVisual = Missile.StrikeVisual.TORPEDO;
        strikeCorvette.x = corvette.x + corvette.radius;
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
        assertTrue(battleship.alive && battleship.hp > 0, "tactical torpedo strike should not one-shot through battleship armor");
        assertTrue(corvette.alive && corvette.hp > 0, "tactical torpedo strike should not one-shot smaller-than-battleship targets through shields");
        assertTrue(roomHp(battleship, battleshipArmor) < armorBefore,
                "tactical torpedo strike should apply its follow-up hit to exterior armor");

        List<Ship.RoomDamageEvent> battleshipEvents = battleship.recentRoomDamageEvents()
                .subList(battleshipEventsBefore, battleship.recentRoomDamageEvents().size());
        assertTrue(battleshipEvents.stream().anyMatch(hit -> hit.roomId == battleshipArmor && hit.damage > 0.0),
                "tactical torpedo strike should record armor damage after shield strip");
        assertFalse(battleshipEvents.stream().anyMatch(hit -> hit.roomId != null
                        && !ShipRoomLayout.isArmorRoom(hit.roomId)
                        && hit.damage > 0.0
                        && !hit.fromHazard),
                "tactical torpedo strike should not leak interior room damage through live armor");
    }

    private static double roomHp(Ship ship, ShipRoomLayout.RoomId roomId) {
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.roomId == roomId) return room.hp;
        }
        return 0.0;
    }

}
