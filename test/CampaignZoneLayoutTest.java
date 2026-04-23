import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignZoneLayoutTest {

    @Test
    void campaignZonesKeepAtLeastFiveThousandUnitsBetweenNeighbors() throws Exception {
        Method centerX = CampaignSystem.class.getDeclaredMethod("getZoneCenterX", int.class);
        Method centerY = CampaignSystem.class.getDeclaredMethod("getZoneCenterY", int.class);
        centerX.setAccessible(true);
        centerY.setAccessible(true);

        double sector1X = (double) centerX.invoke(null, 1);
        double sector1Y = (double) centerY.invoke(null, 1);
        double sector2X = (double) centerX.invoke(null, 2);
        double sector2Y = (double) centerY.invoke(null, 2);
        double sector9X = (double) centerX.invoke(null, 9);
        double sector9Y = (double) centerY.invoke(null, 9);

        assertEquals(9000.0, sector2X - sector1X, 0.001);
        assertEquals(0.0, sector2Y - sector1Y, 0.001);
        assertEquals(0.0, sector9X - sector1X, 0.001);
        assertEquals(8000.0, sector9Y - sector1Y, 0.001);
        assertTrue(sector2X - sector1X >= 9000.0);
        assertTrue(sector9Y - sector1Y >= 8000.0);
    }

    @Test
    void campaignWorldContainsSeparatedZonesSoSectorTwoSpawnDoesNotClampToMapEdge() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 2);

        assertTrue(ctx.WORLD_W >= CampaignSystem.recommendedWorldWidth());
        assertTrue(ctx.WORLD_H >= CampaignSystem.recommendedWorldHeight());
        assertTrue(ctx.player.x > 9000.0 && ctx.player.x < 13000.0,
                "sector 2 arrival should land inside the physical sector-2 zone, not on the world edge");
        assertTrue(ctx.player.x < ctx.WORLD_W - 1000.0,
                "sector 2 arrival should not be clamped to the far-right map border");

        long overlappingFleetShips = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.faction == Faction.ALLY)
                .filter(ship -> Math.hypot(ship.x - ctx.player.x, ship.y - ctx.player.y)
                        < Math.max(120.0, ship.radius + ctx.player.radius + 20.0))
                .count();
        assertEquals(0, overlappingFleetShips,
                "persistent fleet ships should spawn in formation instead of stacking on the Mothership");
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }
}
