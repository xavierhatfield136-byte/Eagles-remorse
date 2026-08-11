import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponDoctrineBalanceTest {

    @Test
    void beamBoltFamilyUsesTheActualBeamBoltSpeed() {
        FleetShip ship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the battleship");

        gun.setReady();
        Projectile shot = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, shot);

        assertTrue(bolt.isBeamBolt(), "beam-bolt volley package should render as the beam projectile");
        assertEquals(Ship.BEAM_BOLT_SPEED, Math.hypot(bolt.vx, bolt.vy) / GameContext.DT, 1e-6);
    }

    @Test
    void beamBoltUsesOneSecondReloadAndRemembersItsTurretMount() {
        FleetShip ship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the battleship");
        assertEquals(Ship.BEAM_BOLT_RELOAD_SECONDS, gun.cooldown, 1e-6);

        gun.setReady();
        Projectile shot = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, shot);

        assertEquals(bolt.x, bolt.spawnX, 1e-6);
        assertEquals(bolt.y, bolt.spawnY, 1e-6);
        assertEquals(gun.localX, bolt.sourceTurretLocalX, 1e-6);
        assertEquals(gun.localY, bolt.sourceTurretLocalY, 1e-6);
    }

    @Test
    void blueMainBatteryReloadFloorsAtOneSecond() {
        FleetShip ship = new FleetShip(ShipRole.PICKET, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the picket");

        gun.setReady();
        Projectile first = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, first);
        assertTrue(bolt.damage > gun.damage,
                "blue main batteries should hit harder after the reload floor");

        assertNull(gun.fire(ship, null, GameContext.DT),
                "blue main batteries should not fire multiple times inside one second");

        gun.update(1.10);
        assertNull(gun.fire(ship, null, GameContext.DT),
                "blue main batteries should also wait for their last projectile to resolve (hit/despawn)");

        // Advance time until the first bolt despawns, which should release the fire lock.
        int guard = 0;
        while (bolt.alive && guard++ < 1000) {
            bolt.update(GameContext.DT);
            gun.update(GameContext.DT);
        }
        assertTrue(guard < 1000, "expected the energy bolt to despawn within a bounded number of ticks");

        assertTrue(gun.fire(ship, null, GameContext.DT) != null,
                "blue main batteries should be able to fire again after the prior shot resolves");
    }

    @Test
    void teamCBeamDamageFallsOffAtLongRange() throws Exception {
        FleetShip beamShip = new FleetShip(ShipRole.BATTLESHIP, Faction.TEAM_C, 0.0, 0.0);
        Turret gun = firstGun(beamShip);
        assertTrue(gun != null, "expected a primary gun on the Team C battleship");

        PhaserBeam beam = new PhaserBeam(beamShip, gun, 0.0, 3200.0, 14.0, 90.0, 90, Faction.TEAM_C);
        Method scale = CollisionSystem.class.getDeclaredMethod("scaleBeamDamage", PhaserBeam.class, int.class, double.class);
        scale.setAccessible(true);

        int closeDamage = (Integer) scale.invoke(null, beam, 12, 0.20);
        int farDamage = (Integer) scale.invoke(null, beam, 12, 0.85);

        assertTrue(closeDamage > farDamage,
                "phaser damage should taper off over long range (close=" + closeDamage + ", far=" + farDamage + ")");
    }

    @Test
    void greenShipsAlwaysUseConcentratedBeamFamily() {
        FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the Team C frigate");

        ship.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
        ship.applyPrimaryWeaponFamily();

        assertEquals(Ship.PrimaryWeaponFamily.BEAM_BOLT, ship.primaryWeaponFamily);
        assertTrue(ship.usesVolleyPrimaryFire(), "Green ships should fire concentrated beam volleys");
        assertTrue(!ship.usesStaggeredPrimaryFire(), "Green ships should not use rapid-fire beam staggering");
        assertEquals(Ship.BEAM_BOLT_RELOAD_SECONDS, gun.cooldown, 1e-6);
    }

    @Test
    void greenLightBeamTurretsUseOneSecondFiringWindow() {
        FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the Team C frigate");
        gun.setReady();

        PhaserBeam beam = assertInstanceOf(PhaserBeam.class, gun.fire(ship, null, GameContext.DT));

        assertEquals((int) Math.round(Ship.BEAM_BOLT_RELOAD_SECONDS / GameContext.DT), beam.life,
                "Green light beams should stay active for the one-second turret firing window");
        assertNull(gun.fire(ship, null, GameContext.DT),
                "Green light beam turrets should not fire another beam inside the one-second window");
    }

    @Test
    void blueProjectilesGainDamageWithFlightDistance() {
        FleetShip ship = new FleetShip(ShipRole.PICKET, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the picket");

        gun.setReady();
        Projectile shot = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, shot);

        assertTrue(bolt.damageGrowthPerUnit > 1e-9, "expected blue energy bolts to have damage growth enabled");
        int baseDamage = bolt.getEffectiveDamage();

        for (int i = 0; i < 90; i++) {
            bolt.update(GameContext.DT);
        }
        int grownDamage = bolt.getEffectiveDamage();
        assertTrue(grownDamage > baseDamage,
                "expected effective damage to grow with distance (base=" + baseDamage + ", grown=" + grownDamage + ")");
    }

    @Test
    void beamBoltPrimaryVolleysAllGunsTogether() {
        Player player = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.BEAM_BOLT;
        player.applyPrimaryWeaponFamily();

        List<Turret> guns = primaryGuns(player);
        assertTrue(guns.size() > 1, "expected multiple primary guns on the battleship");
        for (Turret gun : guns) gun.setReady();

        List<Projectile> shots = player.firePrimary(1200.0, 0.0, GameContext.DT);
        assertEquals(guns.size(), shots.size(),
                "beam-bolt volley package should volley all barrels together (" + guns.size() + " guns)");

        for (Projectile shot : shots) {
            EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, shot);
            assertTrue(bolt.isBeamBolt(), "expected beam-bolt visuals during synchronized volleys");
            assertTrue(bolt.usesCombinedBeamVisual(), "expected volley package to keep the merged multi-barrel beam visual");
        }
    }

    @Test
    void energyBoltPrimaryStaggersOneGunPerInterval() {
        Player player = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
        player.applyPrimaryWeaponFamily();

        List<Turret> guns = primaryGuns(player);
        assertTrue(guns.size() > 1, "expected multiple primary guns on the battleship");
        for (Turret gun : guns) gun.setReady();

        List<Projectile> firstVolley = player.firePrimary(1200.0, 0.0, GameContext.DT);
        assertEquals(1, firstVolley.size(), "staggered beam-bolt package should fire one barrel at a time");
        EnergyBolt firstBolt = assertInstanceOf(EnergyBolt.class, firstVolley.get(0));
        assertTrue(firstBolt.isBeamBolt(), "expected staggered package to keep the beam-bolt visuals");
        assertTrue(!firstBolt.usesCombinedBeamVisual(), "expected staggered package to render as a single barrel lane");

        List<Projectile> secondImmediate = player.firePrimary(1200.0, 0.0, GameContext.DT);
        assertEquals(0, secondImmediate.size(),
                "expected no firing until the stagger interval elapses (timer=" + player.primaryGunStaggerTimer + ")");

        player.update(Ship.ENERGY_BOLT_BARREL_STAGGER_INTERVAL_SECONDS);

        List<Projectile> secondVolley = player.firePrimary(1200.0, 0.0, GameContext.DT);
        assertEquals(1, secondVolley.size(), "expected the next barrel to fire after the stagger interval");
        EnergyBolt secondBolt = assertInstanceOf(EnergyBolt.class, secondVolley.get(0));
        assertTrue(secondBolt.isBeamBolt(), "expected staggered package to keep using the beam-bolt visuals");
        assertTrue(!secondBolt.usesCombinedBeamVisual(), "expected staggered package to keep using a single beam lane");
        boolean sameMount = Math.abs(firstBolt.sourceTurretLocalX - secondBolt.sourceTurretLocalX) <= 1e-6
                && Math.abs(firstBolt.sourceTurretLocalY - secondBolt.sourceTurretLocalY) <= 1e-6;
        assertTrue(!sameMount, "expected stagger to rotate across different turret barrels");
    }

    @Test
    void energyBoltPrimaryTapContinuesAcrossAllBarrels() {
        Player player = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
        player.applyPrimaryWeaponFamily();

        List<Turret> guns = primaryGuns(player);
        assertTrue(guns.size() > 1, "expected multiple primary guns on the battleship");
        for (Turret gun : guns) gun.setReady();

        // Simulate a quick tap: a single fire command frame should start a short multi-barrel sequence.
        List<Projectile> first = player.firePrimary(1200.0, 0.0, GameContext.DT, true);
        assertEquals(1, first.size(), "expected the first barrel to fire immediately on tap");
        assertEquals(guns.size() - 1, player.primaryGunStaggerBurstRemaining,
                "expected remaining barrels to be queued after a single tap");

        for (int i = 0; i < guns.size() - 1; i++) {
            player.update(Ship.ENERGY_BOLT_BARREL_STAGGER_INTERVAL_SECONDS);
            List<Projectile> next = player.firePrimary(1200.0, 0.0, GameContext.DT, false);
            assertEquals(1, next.size(),
                    "expected the tap-triggered sequence to continue firing across barrels");
        }
        assertEquals(0, player.primaryGunStaggerBurstRemaining, "expected the barrel sequence to complete");
    }

    private static Turret firstGun(Ship ship) {
        if (ship == null || ship.turrets == null) return null;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.kind == Turret.Kind.GUN && turret.primary) {
                return turret;
            }
        }
        return null;
    }

    private static List<Turret> primaryGuns(Ship ship) {
        List<Turret> out = new ArrayList<>();
        if (ship == null || ship.turrets == null) return out;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.kind == Turret.Kind.GUN && turret.primary) {
                out.add(turret);
            }
        }
        return out;
    }
}
