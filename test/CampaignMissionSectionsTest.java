import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMissionSectionsTest {

    @Test
    void campaignSectorsSeedMissionSectionsAndDiscoveries() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        assertTrue(ctx.campaign.missionSections.size() >= 3, "campaign sectors should expose multiple mission sections");
        assertEquals(18, ctx.campaign.discoverySites.size(), "campaign sectors should now seed all subzones with optional pockets");
    }

    @Test
    void campaignDiscoveriesSeedExpandedEncounterPool() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        Set<String> kinds = new HashSet<>();
        for (Object site : ctx.campaign.discoverySites) {
            Object kind = getField(site, "kind");
            kinds.add(String.valueOf(kind));
        }

        assertTrue(kinds.contains("SALVAGE_HULK"));
        assertTrue(kinds.contains("SUPPLY_CACHE"));
        assertTrue(kinds.contains("DATA_RELAY"));
        assertTrue(kinds.contains("WRECK_FIELD"));
        assertTrue(kinds.contains("MINEFIELD"));
        assertTrue(kinds.contains("DRIFTING_TURRET"));
        assertTrue(kinds.contains("AMBUSH"));
        assertTrue(kinds.contains("REINFORCEMENT"));
        assertTrue(kinds.contains("NEUTRAL_TRADER"));
        assertTrue(kinds.contains("PRISON_BARGE"));
        assertTrue(kinds.contains("ANOMALY"));
        assertTrue(kinds.contains("FLEET_ASSET"));
    }

    @Test
    void campaignDiscoveryZonesSeedAmbientWorldContent() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        long supportOrTraderCount = ctx.ships.stream()
                .filter(ship -> ship != null)
                .filter(ship -> ship.role == ShipRole.TRANSPORT
                        || ship.role == ShipRole.HAULER
                        || ship.role == ShipRole.MINER)
                .count();
        long turretCount = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.STATIC_TURRET)
                .count();

        assertTrue(ctx.asteroids.size() >= 20, "campaign pockets should place ambient ore/anomaly fields across the mission");
        assertTrue(ctx.salvage.size() >= 12, "campaign pockets should place ambient salvage/wreckage across the mission");
        assertTrue(supportOrTraderCount >= 6, "campaign pockets should include traders, barges, or support ships before discovery");
        assertTrue(turretCount >= 4, "campaign pockets should include seeded defense or mine anchors in side zones");
    }

    @Test
    void missionProgressCapsUntilFleetReachesNextSection() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        ctx.campaign.objectiveProgress = 0.34;
        invokeUpdateMissionSectionFlow(ctx);

        assertEquals(1, ctx.campaign.activeMissionSection);
        assertTrue(ctx.campaign.missionSectionTravelLocked, "progress should pause until the fleet reaches the next section");

        Object activeSection = ctx.campaign.missionSections.get(ctx.campaign.activeMissionSection);
        ctx.player.x = getDoubleField(activeSection, "x");
        ctx.player.y = getDoubleField(activeSection, "y");

        invokeUpdateMissionSectionFlow(ctx);

        assertFalse(ctx.campaign.missionSectionTravelLocked, "arriving at the new section should unlock progress again");
    }

    @Test
    void enteringDiscoveryPocketMarksItFound() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        List<?> discoveries = ctx.campaign.discoverySites;
        Object first = discoveries.get(0);
        ctx.player.x = getDoubleField(first, "x");
        ctx.player.y = getDoubleField(first, "y");

        invokeUpdatePocketDiscoveries(ctx);

        assertTrue(ctx.campaign.discoveriesFound >= 1);
        assertTrue(getBooleanField(first, "discovered"));
    }

    @Test
    void missionSectionsAndDiscoveriesLandInsidePlayableSubzones() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        Method missionSubzoneForPoint = CampaignSystem.class.getDeclaredMethod(
                "missionSubzoneForPoint", int.class, double.class, double.class);
        missionSubzoneForPoint.setAccessible(true);

        for (Object section : ctx.campaign.missionSections) {
            int subzone = (int) missionSubzoneForPoint.invoke(
                    null, ctx.campaign.sector, getDoubleField(section, "x"), getDoubleField(section, "y"));
            assertTrue(subzone >= 0, "mission section should be placed inside a playable subzone");
        }

        for (Object site : ctx.campaign.discoverySites) {
            int subzone = (int) missionSubzoneForPoint.invoke(
                    null, ctx.campaign.sector, getDoubleField(site, "x"), getDoubleField(site, "y"));
            assertTrue(subzone >= 0, "discovery site should be placed inside a playable subzone");
        }
    }

    @Test
    void campaignWarpRoutingOnlyAdvancesOneSectorPerJump() {
        int source = CampaignSystem.missionSubzoneIndex(0, 1);
        int target = CampaignSystem.missionSubzoneIndex(5, 1);
        int hop = CampaignSystem.nextCampaignWarpHop(source, target);
        assertEquals(CampaignSystem.missionSubzoneIndex(1, 1), hop);

        int diagonalTarget = CampaignSystem.missionSubzoneIndex(5, 2);
        int diagonalHop = CampaignSystem.nextCampaignWarpHop(source, diagonalTarget);
        assertEquals(CampaignSystem.missionSubzoneIndex(1, 2), diagonalHop);
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static void invokeUpdateMissionSectionFlow(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateMissionSectionFlow",
                GameContext.class,
                CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign);
    }

    private static void invokeUpdatePocketDiscoveries(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updatePocketDiscoveries",
                GameContext.class,
                CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign);
    }

    private static double getDoubleField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
