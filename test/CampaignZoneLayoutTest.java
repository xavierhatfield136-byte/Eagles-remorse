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
    void missionSubzonesUseBoundedContiguousPocketSpacing() throws Exception {
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

        assertEquals(5000.0, rightX - leftX, 0.001);
        assertEquals(5000.0, leftY - upperY, 0.001);
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

    @Test
    void legacyMainPoisKeepStableIdsWhileExposingFacilityMetadata() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        assertEquals(24, CampaignSystem.mainCampaignLocations(ctx).size());
        for (int i = 0; i < 24; i++) {
            CampaignSystem.CampaignLocation location = CampaignSystem.mainCampaignLocations(ctx).get(i);
            String legacyId = String.format(java.util.Locale.US, "poi-%02d", i + 1);
            assertEquals(legacyId, location.id);
            assertEquals(legacyId, location.legacyPoiId);
            assertEquals(String.format(java.util.Locale.US, "facility-%02d", i + 1), location.facilityId);
            assertTrue(location.zoneId != null && !location.zoneId.isBlank(),
                    "main campaign facility should expose a zone id");
            assertTrue(location.facilityType != CampaignSystem.CampaignFacilityType.UNKNOWN,
                    "main campaign facility should expose an explicit facility type");
            assertTrue(location.strategicValue >= 1 && location.strategicValue <= 5,
                    "strategic value should stay in the 1-5 rating range");
            assertEquals(CampaignSystem.CampaignIntelLevel.FULL, location.intelLevel);
        }
    }

    @Test
    void proceduralMinorSitesStartAsHiddenFacilitiesWithUnknownIntel() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        assertTrue(CampaignSystem.campaignAreasOfInterest(ctx).stream()
                .filter(location -> location != null && location.id.startsWith("aoi-proc-"))
                .allMatch(location -> !location.discovered
                        && location.facilityType != CampaignSystem.CampaignFacilityType.UNKNOWN
                        && location.intelLevel == CampaignSystem.CampaignIntelLevel.UNKNOWN));
    }

    @Test
    void strategicOvermapFacilitiesGenerateHomeBasedFleets() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = true;
        ctx.campaign.facilityFleetGenerationActive = true;

        invokeForceSimulation(ctx, 0.25);

        boolean found = false;
        for (Object force : ctx.campaign.campaignForces) {
            if (fieldString(force, "name").equals("Green Anchorage Pelagos Patrol Fleet")
                    && fieldString(force, "homeBaseId").equals("poi-01")
                    && fieldString(force, "sourceLocationId").equals("poi-01")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "strategic overmap should generate a home-based fleet from the Green anchorage facility");
    }

    @Test
    void openingCampaignForcesStayOutsideProtectedStartRadius() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = true;
        ctx.campaign.facilityFleetGenerationActive = true;
        ctx.campaign.sectorElapsed = 0.0;

        invokeForceSimulation(ctx, 0.25);

        for (Object force : ctx.campaign.campaignForces) {
            if (!"ENEMY".equals(fieldString(force, "faction"))) continue;
            double x = fieldDouble(force, "x");
            double y = fieldDouble(force, "y");
            double dist = Math.hypot(x - ctx.campaign.playerGalaxyX, y - ctx.campaign.playerGalaxyY);
            assertTrue(dist >= 2500.0,
                    "opening Red campaign force should not spawn directly on the player: " + fieldString(force, "name"));
        }
    }

    @Test
    void tacticalCampaignSpawnGracePushesRedShipsAwayFromPlayer() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);
        ctx.campaign.sectorElapsed = 2.0;

        Ship spawned = invokeSpawnEnemyAtPoint(ctx, ShipRole.PATROL, ctx.player.x + 30.0, ctx.player.y + 20.0);
        double dist = Math.hypot(spawned.x - ctx.player.x, spawned.y - ctx.player.y);
        assertTrue(dist >= 900.0,
                "Red ships spawned during the opening grace period should not pile directly onto the player");
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static Ship invokeSpawnEnemyAtPoint(GameContext ctx, ShipRole role, double x, double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "spawnEnemyAtPoint",
                GameContext.class,
                ShipRole.class,
                double.class,
                double.class
        );
        method.setAccessible(true);
        return (Ship) method.invoke(null, ctx, role, x, y);
    }

    private static void invokeForceSimulation(GameContext ctx, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, dt);
    }

    private static String fieldString(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static double fieldDouble(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
