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
        assertTrue(FogOfWarSystem.coverageSummary(ctx).contains("anomal"),
                "sensor summary should include anomaly count once points of interest are detected");
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
}
