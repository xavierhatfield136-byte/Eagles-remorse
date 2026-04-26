import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignZoneLayoutTest {

    @Test
    void campaignMissionWorldScalesWithConfiguredSectorSize() {
        GameConfig small = new GameConfig(GameMode.CAMPAIGN_OPS, 1800, 1400, true, 1L, false);
        GameConfig large = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1L, false);
        assertTrue(CampaignSystem.recommendedWorldWidth(large) > CampaignSystem.recommendedWorldWidth(small));
        assertTrue(CampaignSystem.recommendedWorldHeight(large) > CampaignSystem.recommendedWorldHeight(small));
    }

    @Test
    void missionSubzonesKeepThreeThousandUnitVoidBetweenNeighbors() throws Exception {
        Method centerX = CampaignSystem.class.getDeclaredMethod("missionSubzoneCenterX", int.class, int.class);
        Method centerY = CampaignSystem.class.getDeclaredMethod("missionSubzoneCenterY", int.class, int.class);
        Method missionSubzoneIndex = CampaignSystem.class.getDeclaredMethod("missionSubzoneIndex", int.class, int.class);
        centerX.setAccessible(true);
        centerY.setAccessible(true);
        missionSubzoneIndex.setAccessible(true);

        int left = (int) missionSubzoneIndex.invoke(null, 0, 1);
        int right = (int) missionSubzoneIndex.invoke(null, 1, 1);
        int upper = (int) missionSubzoneIndex.invoke(null, 0, 0);

        double leftX = (double) centerX.invoke(null, 1, left);
        double rightX = (double) centerX.invoke(null, 1, right);
        double leftY = (double) centerY.invoke(null, 1, left);
        double upperY = (double) centerY.invoke(null, 1, upper);

        assertEquals(10000.0, rightX - leftX, 0.001);
        assertEquals(10000.0, leftY - upperY, 0.001);
    }

    @Test
    void campaignWorldContainsSeparatedZonesSoSectorTwoSpawnDoesNotClampToMapEdge() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 2);

        assertTrue(ctx.WORLD_W >= CampaignSystem.recommendedWorldWidth(ctx.config));
        assertTrue(ctx.WORLD_H >= CampaignSystem.recommendedWorldHeight(ctx.config));
        assertTrue(ctx.player.x > 2000.0 && ctx.player.x < 3000.0,
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

        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "RESOURCE POCKET".equals(l.label)));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "RESERVE STAGING".equals(l.label)));

        double minEnemyX = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.ENEMY)
                .mapToDouble(ship -> ship.x)
                .min()
                .orElse(ctx.player.x);
        double maxEnemyX = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.ENEMY)
                .mapToDouble(ship -> ship.x)
                .max()
                .orElse(ctx.player.x);
        assertTrue(maxEnemyX - minEnemyX > 900.0,
                "campaign sectors should distribute enemy contacts across the zone instead of one local cluster");
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }
}
