import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyperweaponBehaviorTest {

    @Test
    void redSupershipShockwaveSkipsTitansUnlessDirect() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip redSupership = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 0.0, 0.0);
        FleetShip directTarget = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 420.0, 0.0);
        FleetShip titan = new FleetShip(ShipRole.BULWARK_TITAN, Faction.ALLY, 690.0, 0.0);

        ctx.ships.add(redSupership);
        ctx.ships.add(directTarget);
        ctx.ships.add(titan);
        ctx.entityQuery.rebuild(ctx);

        double titanShieldBefore = titan.shield;
        int titanHpBefore = titan.hp;

        DisruptorSlug slug = new DisruptorSlug(
                directTarget.x,
                directTarget.y,
                0.0,
                GameContext.DT,
                0.0,
                18,
                60,
                24.0,
                420.0,
                Faction.ENEMY
        );
        slug.sourceShipId = redSupership.id;
        ctx.projectiles.add(slug);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        Explosion.active.clear();
        Explosion.spawnSuperweaponBlast(directTarget.x, directTarget.y, redSupership.id, redSupership.faction);
        Explosion.updateAll(0.46);
        CollisionSystem.handleSuperweaponBlastRings(ctx);

        assertTrue(directTarget.getTemporaryDisableRemaining() > 0.0 || directTarget.shield < directTarget.shieldMax || directTarget.hp < directTarget.hpMax);
        assertEquals(titanShieldBefore, titan.shield, 1e-6);
        assertEquals(titanHpBefore, titan.hp);
        assertEquals(0.0, titan.getTemporaryDisableRemaining(), 1e-6);
    }

    @Test
    void blueHyperweaponPulseDeletesNonTitansAndHalvesTitans() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip blueHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip cruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 720.0, 0.0);
        FleetShip titan = new FleetShip(ShipRole.BULWARK_TITAN, Faction.ENEMY, 940.0, 0.0);

        ctx.ships.add(blueHyperweapon);
        ctx.ships.add(cruiser);
        ctx.ships.add(titan);
        ctx.entityQuery.rebuild(ctx);

        int titanHpBefore = titan.hp;
        double titanShieldBefore = titan.shield;

        Projectile shot = chargeAndFireSuperweapon(blueHyperweapon, cruiser.x, cruiser.y);
        assertInstanceOf(DestabilizerPulse.class, shot);
        shot.x = cruiser.x;
        shot.y = cruiser.y;
        ctx.projectiles.add(shot);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        assertTrue(cruiser.dying || !cruiser.alive || cruiser.hp <= 0);
        assertTrue(titan.shield < titanShieldBefore || titan.shield <= 0.0);
        assertTrue(titan.hp <= titanHpBefore / 2);
    }

    @Test
    void blueSupershipFiresAfterChargeAudioCompletes() {
        FleetShip blueSupership = new FleetShip(ShipRole.SUPERSHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 720.0, 0.0);

        assertEquals(Ship.SUPERWEAPON_CHARGE_SFX_SECONDS, blueSupership.superweaponChargeTime, 1e-6);
        assertEquals(10.0, SfxManifest.byId("super.blue.charge").cooldownSec(), 1e-6);
        assertNull(blueSupership.tryFireSuperweapon(target, GameContext.DT));
        assertTrue(blueSupership.isSuperweaponCharging());

        Projectile fired = null;
        for (int i = 0; i < 9 * 60; i++) {
            blueSupership.trackSuperweaponAim(target.x, target.y);
            blueSupership.update(GameContext.DT);
            fired = blueSupership.pollSuperweaponShot();
            assertNull(fired, "blue supership should not fire before the 10-second charge audio completes");
        }

        for (int i = 0; i < 90 && fired == null; i++) {
            blueSupership.trackSuperweaponAim(target.x, target.y);
            blueSupership.update(GameContext.DT);
            fired = blueSupership.pollSuperweaponShot();
        }

        assertNotNull(fired, "blue supership should fire as the charge audio completes");
    }

    @Test
    void redHyperweaponTargetsPelletLinesWithoutControlLoss() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip redHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 760.0, 0.0);

        ctx.ships.add(redHyperweapon);
        ctx.ships.add(target);
        ctx.entityQuery.rebuild(ctx);

        double normalSpeed = MovementModel.speedCeiling(target);
        double shieldBefore = target.shield;

        Projectile shot = chargeAndFireSuperweapon(ctx, redHyperweapon, target);
        SuperweaponShot first = assertInstanceOf(SuperweaponShot.class, shot);
        assertEquals(Ship.SuperweaponPattern.KINETIC_SHOTGUN, redHyperweapon.superweaponPattern);
        assertTrue(projectileSpeedPerSecond(first) >= 2500.0);

        List<SuperweaponShot> immediateShots = collectSuperweaponShots(first);
        Projectile queued;
        while ((queued = redHyperweapon.pollSuperweaponShot()) != null) {
            immediateShots.add(assertInstanceOf(SuperweaponShot.class, queued));
        }
        assertEquals(3, immediateShots.size(), "a single target should receive one pellet lane, not a whole cone");
        for (SuperweaponShot pellet : immediateShots) {
            assertTrue(projectileSpeedPerSecond(pellet) >= 2500.0);
            assertTrue(aimsAt(pellet, target, Math.toRadians(3.0)));
        }

        shot.x = target.x;
        shot.y = target.y;
        ctx.projectiles.add(shot);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);
        ctx.entityQuery.rebuild(ctx);
        CollisionSystem.handleStasisFields(ctx);

        assertFalse(target.isStasisFieldTrapped());
        assertEquals(0.0, target.getTemporaryDisableRemaining(), 1e-6);
        assertTrue(MovementModel.speedCeiling(target) >= normalSpeed * 0.75);
        assertTrue(target.canUseCombatSystems());
        assertTrue(target.canUseBattlefieldWarp());
        assertTrue(target.shield < shieldBefore);
    }

    @Test
    void redHyperweaponSpreadsPelletLinesAcrossLargestNearbyTargets() {
        GameContext ctx = combatContext();

        FleetShip redHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, 0.0, 0.0);
        FleetShip closeSmallLock = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 420.0, 0.0);
        FleetShip dreadnought = new FleetShip(ShipRole.DREADNOUGHT, Faction.ALLY, 1180.0, 260.0);
        FleetShip battleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, -980.0, 210.0);
        FleetShip cruiser = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 760.0, -620.0);
        ctx.ships.add(redHyperweapon);
        ctx.ships.add(closeSmallLock);
        ctx.ships.add(dreadnought);
        ctx.ships.add(battleship);
        ctx.ships.add(cruiser);
        for (int i = 0; i < 5; i++) {
            ctx.ships.add(new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 360.0 + i * 70.0, 130.0 + i * 18.0));
        }
        ctx.entityQuery.rebuild(ctx);

        Projectile firstProjectile = chargeAndFireSuperweapon(ctx, redHyperweapon, closeSmallLock);
        SuperweaponShot first = assertInstanceOf(SuperweaponShot.class, firstProjectile);
        List<SuperweaponShot> shots = collectSuperweaponShots(first);
        Projectile queued;
        while ((queued = redHyperweapon.pollSuperweaponShot()) != null) {
            shots.add(assertInstanceOf(SuperweaponShot.class, queued));
        }

        assertEquals(9, shots.size(), "initial barrage should create one pellet lane per valid large target");
        assertEquals(3, shotsAimedAt(shots, redHyperweapon, dreadnought));
        assertEquals(3, shotsAimedAt(shots, redHyperweapon, battleship));
        assertTrue(distinctAimLaneCount(shots) >= 3,
                "barrage should split into separate target lanes instead of a single cone or one focus target");
        assertEquals(0, shotsAimedAt(shots, redHyperweapon, closeSmallLock),
                "fighter-class locks should not steal a hyperweapon lane from larger formation targets");
    }

    @Test
    void redArtilleryTitanSlugHitsHarderThanBefore() {
        FleetShip artillery = new FleetShip(ShipRole.ARTILLERY_TITAN, Faction.ENEMY, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 980.0, 0.0);

        Projectile shot = chargeAndFireSuperweapon(artillery, target.x, target.y);
        DisruptorSlug slug = assertInstanceOf(DisruptorSlug.class, shot);

        assertTrue(artillery.superweaponDamage >= 210);
        assertTrue(slug.damage >= 190, "red artillery slug should hit harder while keeping the slug behavior");
    }

    @Test
    void redSupershipSlugDetonatesOnFirstShipHit() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip redSupership = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 0.0, 0.0);
        FleetShip firstTarget = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 420.0, 0.0);
        FleetShip laterTarget = new FleetShip(ShipRole.DREADNOUGHT, Faction.ALLY, 1340.0, 0.0);

        ctx.ships.add(redSupership);
        ctx.ships.add(firstTarget);
        ctx.ships.add(laterTarget);
        ctx.entityQuery.rebuild(ctx);

        double laterShieldBefore = laterTarget.shield;
        int laterHpBefore = laterTarget.hp;

        Projectile shot = chargeAndFireSuperweapon(redSupership, laterTarget.x, laterTarget.y);
        DisruptorSlug slug = assertInstanceOf(DisruptorSlug.class, shot);
        slug.x = firstTarget.x;
        slug.y = firstTarget.y;
        ctx.projectiles.add(slug);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        assertFalse(slug.alive);

        slug.x = laterTarget.x;
        slug.y = laterTarget.y;
        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        assertEquals(laterShieldBefore, laterTarget.shield, 1e-6);
        assertEquals(laterHpBefore, laterTarget.hp);
    }

    @Test
    void redHyperweaponShotgunContinuesFiringAtHighCadence() {
        Explosion.active.clear();

        FleetShip redHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.DREADNOUGHT, Faction.ALLY, 1660.0, 0.0);

        GameContext ctx = combatContext();
        ctx.ships.add(redHyperweapon);
        ctx.ships.add(target);
        ctx.ships.add(new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 1520.0, 240.0));
        ctx.ships.add(new FleetShip(ShipRole.CRUISER, Faction.ALLY, 1280.0, -360.0));
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 980.0, 520.0));
        ctx.ships.add(new FleetShip(ShipRole.MISSILE_BOAT, Faction.ALLY, 920.0, -520.0));
        ctx.entityQuery.rebuild(ctx);

        Projectile shot = chargeAndFireSuperweapon(ctx, redHyperweapon, target);
        assertInstanceOf(SuperweaponShot.class, shot);
        int fired = 1;
        while (redHyperweapon.pollSuperweaponShot() != null) fired++;

        for (int i = 0; i < 14; i++) {
            redHyperweapon.trackSuperweaponAim(target.x, target.y);
            redHyperweapon.update(GameContext.DT);
            Projectile queued;
            while ((queued = redHyperweapon.pollSuperweaponShot()) != null) {
                fired++;
                assertInstanceOf(SuperweaponShot.class, queued);
                assertTrue(projectileSpeedPerSecond(queued) >= 2500.0);
            }
        }

        assertTrue(fired >= 36);
    }

    @Test
    void greenHyperweaponFiresOversizedDirectBeam() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip greenHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.TEAM_C, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.BATTLESHIP, Faction.ENEMY, 1200.0, 0.0);

        ctx.ships.add(greenHyperweapon);
        ctx.ships.add(target);
        ctx.entityQuery.rebuild(ctx);

        Projectile shot = chargeAndFireSuperweapon(greenHyperweapon, target.x, target.y);
        PhaserBeam beam = assertInstanceOf(PhaserBeam.class, shot);

        assertEquals(Ship.UNIVERSAL_SPECIAL_WEAPON_RANGE, beam.length, 1e-6);
        assertTrue(beam.width >= 60.0);
        assertTrue(beam.damagePerSecond >= 300.0);
    }

    @Test
    void npcSuperweaponAimCueUsesUniversalSpecialWeaponRange() throws Exception {
        FleetShip greenHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.TEAM_C, 0.0, 0.0);

        Method method = Renderer.class.getDeclaredMethod("npcSuperweaponCueLength", Ship.class);
        method.setAccessible(true);
        double cueLength = (double) method.invoke(null, greenHyperweapon);

        assertEquals(Ship.UNIVERSAL_SPECIAL_WEAPON_RANGE, cueLength, 1e-6);
    }

    @Test
    void yellowHyperweaponWarheadKillsUnshieldedTierTwoTargetsButOnlyStripsShieldedOnes() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip yellowHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.TEAM_D, 0.0, 0.0);
        FleetShip unshieldedBattleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 760.0, 0.0);
        FleetShip shieldedBattleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 920.0, 0.0);
        unshieldedBattleship.shield = 0.0;

        ctx.ships.add(yellowHyperweapon);
        ctx.ships.add(unshieldedBattleship);
        ctx.ships.add(shieldedBattleship);
        ctx.entityQuery.rebuild(ctx);

        double shieldedShieldBefore = shieldedBattleship.shield;
        int shieldedHpBefore = shieldedBattleship.hp;

        Projectile shot = chargeAndFireSuperweapon(yellowHyperweapon, unshieldedBattleship.x, unshieldedBattleship.y);
        assertInstanceOf(Missile.class, shot);
        shot.x = unshieldedBattleship.x;
        shot.y = unshieldedBattleship.y;
        ctx.projectiles.add(shot);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        assertTrue(unshieldedBattleship.dying || !unshieldedBattleship.alive || unshieldedBattleship.hp <= 0);
        assertTrue(shieldedBattleship.alive);
        assertTrue(shieldedBattleship.shield < shieldedShieldBefore);
        assertEquals(shieldedHpBefore, shieldedBattleship.hp);
    }

    @Test
    void blueHyperweaponTitanGunMountsUseBalancedVisibleHullRows() {
        FleetShip hyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ALLY, 0.0, 0.0);

        java.util.List<Turret> guns = hyperweapon.turrets.stream()
                .filter(turret -> turret != null && turret.kind == Turret.Kind.GUN)
                .toList();

        assertEquals(4, guns.size());
        double minY = guns.stream().mapToDouble(turret -> turret.localY).min().orElse(0.0);
        double maxY = guns.stream().mapToDouble(turret -> turret.localY).max().orElse(0.0);
        double meanY = guns.stream().mapToDouble(turret -> turret.localY).average().orElse(0.0);
        ShipHullSilhouette.VisualBounds bounds = ShipHullSilhouette.visualBounds(
                hyperweapon.role, hyperweapon.radius, hyperweapon.faction);
        double visualCenterY = bounds == null ? 0.0 : 0.5 * (bounds.minY + bounds.maxY);
        assertTrue(minY < visualCenterY - 3.0, "expected port-side hyperweapon mounts");
        assertTrue(maxY > visualCenterY + 3.0, "expected starboard-side hyperweapon mounts");
        assertTrue(Math.abs(meanY - visualCenterY) <= hyperweapon.radius * 0.06,
                "hyperweapon mounts should not lean to one side");
        for (Turret turret : guns) {
            assertTrue(ShipHullSilhouette.visualHullContains(
                            hyperweapon.role, hyperweapon.radius, hyperweapon.faction, turret.localX, turret.localY),
                    "hyperweapon turret center should sit on visible hull art");
        }
    }

    private static GameContext combatContext() {
        return new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
    }

    private static Projectile chargeAndFireSuperweapon(Ship shooter, double targetX, double targetY) {
        Projectile fired = shooter.tryFireSuperweaponAt(targetX, targetY, GameContext.DT);
        if (fired != null) return fired;
        for (int i = 0; i < 720 && fired == null; i++) {
            shooter.trackSuperweaponAim(targetX, targetY);
            shooter.update(GameContext.DT);
            fired = shooter.pollSuperweaponShot();
        }
        assertNotNull(fired);
        return fired;
    }

    private static Projectile chargeAndFireSuperweapon(GameContext ctx, Ship shooter, Ship target) {
        Projectile fired = shooter.tryFireSuperweapon(ctx, target, GameContext.DT);
        if (fired != null) return fired;
        for (int i = 0; i < 720 && fired == null; i++) {
            shooter.trackSuperweaponAim(target.x, target.y);
            shooter.update(GameContext.DT);
            fired = shooter.pollSuperweaponShot();
        }
        assertNotNull(fired);
        return fired;
    }

    private static List<SuperweaponShot> collectSuperweaponShots(SuperweaponShot first) {
        List<SuperweaponShot> shots = new ArrayList<>();
        shots.add(first);
        return shots;
    }

    private static int shotsAimedAt(List<SuperweaponShot> shots, Ship shooter, Ship target) {
        int count = 0;
        for (SuperweaponShot shot : shots) {
            if (aimsAt(shot, target, Math.toRadians(3.0))) count++;
        }
        return count;
    }

    private static int distinctAimLaneCount(List<SuperweaponShot> shots) {
        List<Double> lanes = new ArrayList<>();
        for (SuperweaponShot shot : shots) {
            boolean matched = false;
            for (double lane : lanes) {
                if (Math.abs(MathUtil.normalizeAngle(shot.angle - lane)) <= Math.toRadians(3.0)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) lanes.add(shot.angle);
        }
        return lanes.size();
    }

    private static boolean aimsAt(SuperweaponShot shot, Ship target, double tolerance) {
        double expected = Math.atan2(target.y - shot.y, target.x - shot.x);
        double delta = Math.abs(MathUtil.normalizeAngle(shot.angle - expected));
        return delta <= tolerance;
    }

    private static double projectileSpeedPerSecond(Projectile projectile) {
        return Math.hypot(projectile.vx, projectile.vy) / GameContext.DT;
    }
}
