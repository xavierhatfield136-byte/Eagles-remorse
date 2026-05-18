import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitanAbilitySystemTest {

    @Test
    void commandIntelTitanBoostsNearbyAlliedFireControl() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.COMMAND_INTEL_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 180.0, 0.0);

        ctx.ships.add(titan);
        ctx.ships.add(ally);
        ctx.entityQuery.rebuild(ctx);

        double baseSensor = ally.sensorRangeMultiplier();
        double baseCycle = ally.weaponCycleRateMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(ally.sensorRangeMultiplier() > baseSensor);
        assertTrue(ally.weaponCycleRateMultiplier() > baseCycle);
    }

    @Test
    void transportTitanHealsNearbyAllies() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.TRANSPORT_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 150.0, 0.0);

        ally.takePenetratingInternalDamage(10, ally.x, ally.y, 1.0, 0.0);
        int hpBefore = ally.hp;

        ctx.ships.add(titan);
        ctx.ships.add(ally);
        for (int i = 0; i < 240; i++) {
            ctx.entityQuery.rebuild(ctx);
            TitanAbilitySystem.update(ctx, GameContext.DT);
        }

        assertTrue(ally.hp > hpBefore);
    }

    @Test
    void interdictionTitanBreaksEnemyWarpCharges() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.INTERDICTION_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.BATTLECRUISER, Faction.ENEMY, 220.0, 0.0);

        ctx.ships.add(titan);
        ctx.ships.add(enemy);
        ctx.entityQuery.rebuild(ctx);

        assertTrue(enemy.beginBattlefieldWarp(1200.0, 0.0, 6.0));

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(enemy.isTemporarilyDisabled());
        assertTrue(!enemy.isWarpCharging());
    }

    @Test
    void interdictionTitanDisruptsHostileMissiles() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.INTERDICTION_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 40.0, 0.0);
        Missile missile = new Missile(220.0, 0.0, Math.PI, ally, GameContext.DT, 320.0, Math.toRadians(280.0), 5, 240, 7.0, Faction.ENEMY);

        ctx.ships.add(titan);
        ctx.ships.add(ally);
        ctx.projectiles.add(missile);
        ctx.entityQuery.rebuild(ctx);

        double speedBefore = missile.speed;
        double turnBefore = missile.turnRate;
        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(missile.speed < speedBefore);
        assertTrue(missile.turnRate < turnBefore);
    }

    @Test
    void carrierSupportTitanRearmsNearbyStrikeCraft() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.CARRIER_SUPPORT_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 90.0, 0.0);

        fighter.strikePrimaryMunitions = 0;
        fighter.strikeSecondaryMunitions = 0;

        ctx.ships.add(titan);
        ctx.ships.add(fighter);
        ctx.entityQuery.rebuild(ctx);

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertEquals(fighter.strikePrimaryMunitionsMax, fighter.strikePrimaryMunitions);
        assertEquals(fighter.strikeSecondaryMunitionsMax, fighter.strikeSecondaryMunitions);
    }

    @Test
    void bulwarkTitanImprovesNearbyCapitalShieldNetwork() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.BULWARK_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 180.0, 0.0);
        mothership.hasCIWS = true;
        mothership.shield = Math.max(0.0, mothership.shieldMax * 0.35);

        ctx.ships.add(titan);
        ctx.ships.add(mothership);
        ctx.entityQuery.rebuild(ctx);

        double baseShieldRegen = mothership.shieldRegenMultiplier();
        double baseCiws = mothership.ciwsRangeMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(mothership.shieldRegenMultiplier() > baseShieldRegen);
        assertTrue(mothership.ciwsRangeMultiplier() > baseCiws);
    }

    @Test
    void vanguardTitanPushesNearbyEscortsIntoFastAttackPosture() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.VANGUARD_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 160.0, 0.0);

        ctx.ships.add(titan);
        ctx.ships.add(ally);
        ctx.entityQuery.rebuild(ctx);

        double baseDamage = ally.weaponDamageMultiplier();
        double baseCycle = ally.weaponCycleRateMultiplier();
        double baseWarp = ally.warpChargeRateMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(ally.weaponDamageMultiplier() > baseDamage);
        assertTrue(ally.weaponCycleRateMultiplier() > baseCycle);
        assertTrue(ally.warpChargeRateMultiplier() > baseWarp);
    }

    @Test
    void boardingRecoveryTitanBombersCaptureWeakIsolatedTargets() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.BOARDING_RECOVERY_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 120.0, 0.0);

        enemy.shield = 0.0;
        enemy.hp = Math.max(1, (int) Math.round(enemy.hpMax * 0.40));

        ctx.ships.add(titan);
        ctx.ships.add(enemy);
        ctx.entityQuery.rebuild(ctx);

        assertTrue(CarrierSystem.tryLaunchFlight(ctx, titan) > 0);

        Ship bomber = null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.carrierOwnerId == titan.id && ship.role == ShipRole.BOMBER) {
                bomber = ship;
                break;
            }
        }
        assertNotNull(bomber);
        bomber.x = enemy.x - 6.0;
        bomber.y = enemy.y;

        ctx.entityQuery.rebuild(ctx);
        CarrierSystem.update(ctx, GameContext.DT);

        assertEquals(Faction.ALLY, enemy.faction);
        assertFalse(bomber.alive);
        assertTrue(ctx.ui.combatCallouts.stream().anyMatch(c -> c.text.contains("CONVERTED")));
    }

    @Test
    void shieldBastionTitanStabilizesNearbyFormation() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.SHIELD_BASTION_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 220.0, 0.0);
        ally.takePenetratingInternalDamage(14, ally.x, ally.y, 1.0, 0.0);
        ally.shield = Math.max(0.0, ally.shieldMax * 0.20);
        int hpBefore = ally.hp;
        double shieldBefore = ally.shield;

        ctx.ships.add(titan);
        ctx.ships.add(ally);
        for (int i = 0; i < 180; i++) {
            ctx.entityQuery.rebuild(ctx);
            TitanAbilitySystem.update(ctx, GameContext.DT);
        }

        assertTrue(ally.hp > hpBefore);
        assertTrue(ally.shield > shieldBefore);
        assertTrue(ally.supportFieldMultiplier() > 1.0);
    }

    @Test
    void fleetTeleporterTitanAcceleratesNearbyWarpCharge() {
        GameContext withTitan = testContext();
        FleetShip titan = new FleetShip(ShipRole.FLEET_TELEPORTER_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 180.0, 0.0);
        withTitan.ships.add(titan);
        withTitan.ships.add(ally);
        withTitan.entityQuery.rebuild(withTitan);

        GameContext baseline = testContext();
        FleetShip control = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 180.0, 0.0);
        baseline.ships.add(control);
        baseline.entityQuery.rebuild(baseline);

        TitanAbilitySystem.update(withTitan, GameContext.DT);
        assertTrue(ally.beginBattlefieldWarp(900.0, 0.0, 6.0));
        assertTrue(control.beginBattlefieldWarp(900.0, 0.0, 6.0));

        assertTrue(ally.warpChargeRateMultiplier() > control.warpChargeRateMultiplier());
        assertTrue(ally.warpChargeDuration() < control.warpChargeDuration());
    }

    @Test
    void artilleryTitanSuperweaponDeletesSubBattleshipTargets() {
        Ship.enableDeterministicRandom(2468L);
        try {
            GameContext ctx = testContext();
            FleetShip titan = new FleetShip(ShipRole.ARTILLERY_TITAN, Faction.ALLY, 0.0, 0.0);
            FleetShip enemy = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 720.0, 0.0);

            ctx.ships.add(titan);
            ctx.ships.add(enemy);
            ctx.entityQuery.rebuild(ctx);

            assertNull(titan.tryFireSuperweaponAt(enemy.x, enemy.y, GameContext.DT));
            Projectile fired = null;
            for (int i = 0; i < 240 && fired == null; i++) {
                titan.trackSuperweaponAim(enemy.x, enemy.y);
                titan.update(GameContext.DT);
                fired = titan.pollSuperweaponShot();
            }
            assertNotNull(fired);
            fired.radius = Math.max(fired.radius, enemy.radius * 1.4);
            fired.x = enemy.x + enemy.radius * 0.2;
            fired.y = enemy.y;
            ctx.projectiles.add(fired);

            CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

            assertTrue(enemy.dying || !enemy.alive || enemy.hp <= 0);
            assertTrue(ctx.ui.combatCallouts.stream().anyMatch(c -> c.text.contains("EXECUTED")));
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    @Test
    void eliteSupershipCommandTitanBuffsSupershipWing() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip supership = new FleetShip(ShipRole.SUPERSHIP, Faction.ALLY, 220.0, 0.0);
        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 260.0, 0.0);

        ctx.ships.add(titan);
        ctx.ships.add(supership);
        ctx.ships.add(escort);
        ctx.entityQuery.rebuild(ctx);

        double superDamage = supership.weaponDamageMultiplier();
        double superCycle = supership.weaponCycleRateMultiplier();
        double escortDamage = escort.weaponDamageMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(supership.weaponDamageMultiplier() > superDamage);
        assertTrue(supership.weaponCycleRateMultiplier() > superCycle);
        assertEquals(escortDamage, escort.weaponDamageMultiplier(), 1e-9);
    }

    @Test
    void eliteReinforcementsTitanBuffsHonorGuardAndScreensDifferently() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.ELITE_REINFORCEMENTS_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip battleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 200.0, 0.0);
        FleetShip escort = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ALLY, 240.0, 0.0);

        ctx.ships.add(titan);
        ctx.ships.add(battleship);
        ctx.ships.add(escort);
        ctx.entityQuery.rebuild(ctx);

        double battleDamage = battleship.weaponDamageMultiplier();
        double escortCiws = escort.ciwsRangeMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(battleship.weaponDamageMultiplier() > battleDamage);
        assertTrue(escort.ciwsRangeMultiplier() > escortCiws);
    }

    @Test
    void mobileStationTitanActsAsLocalServiceNode() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.MOBILE_STATION_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip carrier = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 180.0, 0.0);
        carrier.takePenetratingInternalDamage(12, carrier.x, carrier.y, 1.0, 0.0);
        carrier.shield = Math.max(0.0, carrier.shieldMax * 0.25);
        int hpBefore = carrier.hp;

        ctx.ships.add(titan);
        ctx.ships.add(carrier);
        for (int i = 0; i < 180; i++) {
            ctx.entityQuery.rebuild(ctx);
            TitanAbilitySystem.update(ctx, GameContext.DT);
        }

        assertTrue(carrier.hp > hpBefore);
        assertTrue(carrier.supportFieldMultiplier() > 1.0);
        assertTrue(carrier.strikeCraftTempoMultiplier() > 1.0);
    }

    @Test
    void hyperweaponTitanImprovesNearbyScreenControl() {
        GameContext ctx = testContext();
        FleetShip titan = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ALLY, 0.0, 0.0);
        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 180.0, 0.0);
        escort.hasCIWS = true;

        ctx.ships.add(titan);
        ctx.ships.add(escort);
        ctx.entityQuery.rebuild(ctx);

        double baseSensor = escort.sensorRangeMultiplier();
        double baseCiws = escort.ciwsRangeMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(escort.sensorRangeMultiplier() > baseSensor);
        assertTrue(escort.ciwsRangeMultiplier() > baseCiws);
    }

    @Test
    void mothershipProjectsFormationWideSupportField() {
        GameContext ctx = testContext();
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 220.0, 0.0);

        ctx.ships.add(mothership);
        ctx.ships.add(ally);
        ctx.entityQuery.rebuild(ctx);

        double baseDamage = ally.weaponDamageMultiplier();
        double baseShield = ally.shieldRegenMultiplier();
        double baseSupport = ally.supportFieldMultiplier();

        TitanAbilitySystem.update(ctx, GameContext.DT);

        assertTrue(ally.weaponDamageMultiplier() > baseDamage);
        assertTrue(ally.shieldRegenMultiplier() > baseShield);
        assertTrue(ally.supportFieldMultiplier() > baseSupport);
    }

    @Test
    void titanShopCategoryUsesSecondPageForLateRosterEntries() {
        assertEquals(2, Renderer.shopHullPageCount(ShopHullCategory.TITAN));
        assertEquals(1, Renderer.shopHullPageForRole(ShipRole.MOTHERSHIP));
        assertEquals(0, Renderer.shopHullPageForRole(ShipRole.TRANSPORT_TITAN));
    }

    @Test
    void campaignStartsPlayerInMothership() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);
    }

    @Test
    void mothershipAndMobileDockyardLaunchPicketEscortsFromFlightDecks() {
        GameContext ctx = testContext();
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 1200.0, 1200.0);
        FleetShip dockyard = new FleetShip(ShipRole.MOBILE_STATION_TITAN, Faction.ALLY, 1600.0, 1200.0);
        ctx.ships.add(mothership);
        ctx.ships.add(dockyard);

        assertTrue(mothership.supportsPicketFlightDeck());
        assertTrue(dockyard.supportsPicketFlightDeck());
        assertEquals(ShipRole.PICKET, mothership.flightDeckRoleAt(0));
        assertEquals(ShipRole.PICKET, dockyard.flightDeckRoleAt(0));

        assertEquals(1, CarrierSystem.tryLaunchFlight(ctx, mothership));
        assertEquals(1, CarrierSystem.tryLaunchFlight(ctx, dockyard));

        long launchedPickets = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.PICKET)
                .filter(ship -> ship.carrierOwnerId == mothership.id || ship.carrierOwnerId == dockyard.id)
                .count();
        assertEquals(2, launchedPickets);
    }

    @Test
    void shootingRangeShopUsesInfiniteCredits() {
        GameContext ctx = testContext();
        SpawnSystem.initWorld(ctx);
        ctx.ui.shopOpen = true;

        int creditsBefore = ctx.credits;
        UISystem.trySwapHull(ctx, ShipRole.DREADNOUGHT, 3200, 3);

        assertEquals(creditsBefore, ctx.credits);
        assertEquals(ShipRole.DREADNOUGHT, ctx.player.role);
    }

    private static GameContext testContext() {
        return new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
    }
}
