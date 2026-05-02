import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FogOfWarSystemTest {

    @Test
    void friendlySensorsRevealNearbySpace() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 12345L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 2650.0, 2500.0);
        ctx.ships.add(escort);

        FogOfWarSystem.reset(ctx);
        FogOfWarSystem.update(ctx);

        assertTrue(ctx.fogOfWar.isVisibleAtWorld(2500.0, 2500.0), "player position should be visible");
        assertTrue(ctx.fogOfWar.isExploredAtWorld(2500.0, 2500.0), "player position should be explored");
        assertFalse(ctx.fogOfWar.isVisibleAtWorld(120.0, 120.0), "far space should remain hidden");
        assertTrue(FogOfWarSystem.countFriendlySensorSources(ctx) >= 2, "player + escort should contribute sensor coverage");
    }

    @Test
    void resetClearsFogMemory() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 67890L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 1500.0, 1500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FogOfWarSystem.update(ctx);
        assertTrue(ctx.fogOfWar.exploredCount() > 0, "initial update should mark some cells explored");

        FogOfWarSystem.reset(ctx);
        assertTrue(ctx.fogOfWar.exploredCount() == 0, "reset should clear explored cells");
        assertFalse(ctx.fogOfWar.isExploredAtWorld(1500.0, 1500.0), "reset should clear world visibility");
    }

    @Test
    void lostContactsLeaveGhostTraces() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 24680L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 2625.0, 2500.0);
        ctx.ships.add(enemy);

        FogOfWarSystem.reset(ctx);
        FogOfWarSystem.update(ctx);

        FogOfWarSystem.ContactGhost ghost = ctx.fogOfWar.contactGhost(enemy.id);
        assertNotNull(ghost, "a visible hostile should seed a ghost contact");
        double lastSeenX = ghost.x;
        double lastSeenY = ghost.y;

        enemy.x = 4700.0;
        enemy.y = 4700.0;
        FogOfWarSystem.update(ctx);

        ghost = ctx.fogOfWar.contactGhost(enemy.id);
        assertNotNull(ghost, "ghost contacts should persist after sensors lose track");
        assertTrue(ghost.ttlSeconds < ghost.maxTtlSeconds, "ghost contacts should decay over time");
        assertEquals(lastSeenX, ghost.x, 0.001, "ghost should remain pinned to the last known X position");
        assertEquals(lastSeenY, ghost.y, 0.001, "ghost should remain pinned to the last known Y position");
        assertTrue(FogOfWarSystem.coverageSummary(ctx).contains("ghost"), "sensor summary should mention ghost traces");

        FogOfWarSystem.reset(ctx);
        assertTrue(ctx.fogOfWar.contactGhost(enemy.id) == null, "reset should clear ghost contacts");
    }

    @Test
    void dyingHostilesDropGhostsImmediatelyButRemainVisibleInRevealedSpace() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 112233L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemy = new FleetShip(ShipRole.BATTLESHIP, Faction.ENEMY, 2625.0, 2500.0);
        ctx.ships.add(enemy);

        FogOfWarSystem.reset(ctx);
        FogOfWarSystem.update(ctx);
        assertNotNull(ctx.fogOfWar.contactGhost(enemy.id), "visible hostile should seed a ghost contact before sensors lose it");
        assertTrue(FogOfWarSystem.isVisibleToPerspective(ctx.fogOfWar, ctx.player.faction, enemy),
                "hostile in revealed space should be visible before the death sequence");

        enemy.dying = true;
        enemy.hp = 0;
        FogOfWarSystem.update(ctx);

        assertTrue(ctx.fogOfWar.contactGhost(enemy.id) == null,
                "ghost contact should be removed as soon as the hostile enters the death sequence");
        assertTrue(FogOfWarSystem.isVisibleToPerspective(ctx.fogOfWar, ctx.player.faction, enemy),
                "dying hostile should stay visible while its wreck animation plays in revealed space");
    }

    @Test
    void highSensorPowerFindsUnexploredOreSignals() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 13579L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.setPowerBusAllocation(0.10, 0.10, 0.10, 0.36, 0.18, 0.16);
        ctx.ships.add(ctx.player);
        ctx.asteroids.add(new Asteroid(4900.0, 2500.0, 52.0, 1200));
        ctx.asteroids.add(new Asteroid(4960.0, 2560.0, 44.0, 900));

        FogOfWarSystem.reset(ctx);
        FogOfWarSystem.update(ctx);

        assertFalse(ctx.fogOfWar.isExploredAtWorld(4900.0, 2500.0), "ore should remain unrevealed as terrain");
        assertTrue(FogOfWarSystem.sensorInterestSignals(ctx).stream()
                        .anyMatch(signal -> signal.kind == FogOfWarSystem.SensorInterestKind.ORE_VEIN),
                "high sensor allocation should mark unexplored rich ore as an anomaly");
        assertTrue(FogOfWarSystem.coverageSummary(ctx).contains("signal"),
                "sensor summary should include detected signal count once points of interest are tracked");
    }

    @Test
    void balancedSensorsDoNotMarkFarUnexploredInterests() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 97531L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.asteroids.add(new Asteroid(4050.0, 2500.0, 52.0, 1200));

        FogOfWarSystem.reset(ctx);
        FogOfWarSystem.update(ctx);

        assertTrue(FogOfWarSystem.sensorInterestSignals(ctx).isEmpty(),
                "balanced routing should not provide long-range points-of-interest intel");
    }

    @Test
    void highSensorPowerMarksCampaignAnomalySites() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 24681357L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 10);
        Object anomaly = null;
        for (Object site : ctx.campaign.discoverySites) {
            if (kindName(site).equals("ANOMALY")) {
                anomaly = site;
                break;
            }
        }
        assertTrue(anomaly != null, "campaign mission should seed at least one anomaly site");
        double anomalyX = getDoubleField(anomaly, "x");
        double anomalyY = getDoubleField(anomaly, "y");
        ctx.player.x = Math.max(200.0, anomalyX - 2200.0);
        ctx.player.y = anomalyY;
        ctx.player.setPowerBusAllocation(0.10, 0.10, 0.10, 0.36, 0.18, 0.16);
        FogOfWarSystem.reset(ctx);
        FogOfWarSystem.update(ctx);

        assertTrue(FogOfWarSystem.sensorInterestSignals(ctx).stream()
                        .anyMatch(signal -> signal.kind == FogOfWarSystem.SensorInterestKind.ANOMALY),
                "campaign anomaly discovery sites should surface on the strategic map like ore");
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        java.lang.reflect.Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static String kindName(Object site) {
        try {
            java.lang.reflect.Field field = site.getClass().getDeclaredField("kind");
            field.setAccessible(true);
            return String.valueOf(field.get(site));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static double getDoubleField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(target);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
