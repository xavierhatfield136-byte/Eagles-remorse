import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

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
    void redHyperweaponCreatesStasisFieldThatPinsTargets() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip redHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 760.0, 0.0);

        ctx.ships.add(redHyperweapon);
        ctx.ships.add(target);
        ctx.entityQuery.rebuild(ctx);

        double normalSpeed = MovementModel.speedCeiling(target);
        double shieldBefore = target.shield;

        Projectile shot = chargeAndFireSuperweapon(redHyperweapon, target.x, target.y);
        assertInstanceOf(DisruptorSlug.class, shot);
        shot.x = target.x;
        shot.y = target.y;
        ctx.projectiles.add(shot);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);
        ctx.entityQuery.rebuild(ctx);
        CollisionSystem.handleStasisFields(ctx);

        assertTrue(target.isStasisFieldTrapped());
        assertEquals(normalSpeed * 0.10, MovementModel.speedCeiling(target), 1e-6);
        assertFalse(target.canUseCombatSystems());
        assertFalse(target.canUseBattlefieldWarp());
        assertTrue(target.shield < shieldBefore);
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
    void redHyperweaponSlugDetonatesOnFirstShipHit() {
        Explosion.active.clear();
        GameContext ctx = combatContext();

        FleetShip redHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, 0.0, 0.0);
        FleetShip firstTarget = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 760.0, 0.0);
        FleetShip laterTarget = new FleetShip(ShipRole.DREADNOUGHT, Faction.ALLY, 1660.0, 0.0);

        ctx.ships.add(redHyperweapon);
        ctx.ships.add(firstTarget);
        ctx.ships.add(laterTarget);
        ctx.entityQuery.rebuild(ctx);

        double laterShieldBefore = laterTarget.shield;
        int laterHpBefore = laterTarget.hp;

        Projectile shot = chargeAndFireSuperweapon(redHyperweapon, laterTarget.x, laterTarget.y);
        DisruptorSlug slug = assertInstanceOf(DisruptorSlug.class, shot);
        slug.x = firstTarget.x;
        slug.y = firstTarget.y;
        ctx.projectiles.add(slug);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);
        ctx.entityQuery.rebuild(ctx);
        CollisionSystem.handleStasisFields(ctx);

        assertFalse(slug.alive);
        assertFalse(laterTarget.isStasisFieldTrapped());

        slug.x = laterTarget.x;
        slug.y = laterTarget.y;
        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);
        ctx.entityQuery.rebuild(ctx);
        CollisionSystem.handleStasisFields(ctx);

        assertFalse(laterTarget.isStasisFieldTrapped());
        assertEquals(laterShieldBefore, laterTarget.shield, 1e-6);
        assertEquals(laterHpBefore, laterTarget.hp);
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
    void hyperweaponTitanGunMountsStayBalancedAcrossCenterline() {
        FleetShip hyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ALLY, 0.0, 0.0);

        double averageLocalY = hyperweapon.turrets.stream()
                .filter(turret -> turret != null && turret.kind == Turret.Kind.GUN)
                .mapToDouble(turret -> turret.localY)
                .average()
                .orElse(Double.NaN);

        assertEquals(0.0, averageLocalY, 0.01);
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
}
