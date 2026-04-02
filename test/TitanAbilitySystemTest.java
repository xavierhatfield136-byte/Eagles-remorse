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
