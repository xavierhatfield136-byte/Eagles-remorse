import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignFriendlyReportingMilestoneEightTest {

    @Test
    void alliedFleetReportsOutsidePlayerSensorRange() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createForce(ctx, Faction.TEAM_C, "Distant Allied Report", 4200.0, 4200.0);
        int forceId = getInt(force, "id");
        ctx.campaign.playerGalaxyX = 500.0;
        ctx.campaign.playerGalaxyY = 500.0;

        CampaignSystem.updateCampaignIntelligenceForTest(ctx);
        CampaignSystem.CampaignIntelResolution resolution =
                CampaignSystem.campaignFleetIntelResolution(ctx, forceId, ctx.campaign.campaignIntelTick);
        assertNotNull(resolution);
        assertEquals(CampaignSystem.CampaignIntelObservationSource.ALLIED_REPORT, resolution.source);
        assertTrue(resolution.exactPosition());
    }

    @Test
    void jammingRemovesOnlyFriendlyReportAndPreservesPlayerSensor() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createForce(ctx, Faction.TEAM_C, "Nearby Allied Report", 1000.0, 1000.0);
        int forceId = getInt(force, "id");
        ctx.campaign.playerGalaxyX = 1000.0;
        ctx.campaign.playerGalaxyY = 1000.0;
        CampaignSystem.updateCampaignIntelligenceForTest(ctx);
        assertTrue(ctx.campaign.campaignFleetIntel.get(forceId).observations.containsKey(
                CampaignSystem.CampaignIntelObservationSource.ALLIED_REPORT));
        assertTrue(ctx.campaign.campaignFleetIntel.get(forceId).observations.containsKey(
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR));

        double x = getDouble(force, "x");
        double strength = getDouble(force, "strength");
        ctx.campaign.campaignCommunicationsJammed = true;
        CampaignSystem.updateCampaignIntelligenceForTest(ctx);

        assertFalse(ctx.campaign.campaignFleetIntel.get(forceId).observations.containsKey(
                CampaignSystem.CampaignIntelObservationSource.ALLIED_REPORT));
        CampaignSystem.CampaignIntelResolution resolution =
                CampaignSystem.campaignFleetIntelResolution(ctx, forceId, ctx.campaign.campaignIntelTick);
        assertNotNull(resolution);
        assertEquals(CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR, resolution.source);
        assertEquals(x, getDouble(force, "x"), 1e-9);
        assertEquals(strength, getDouble(force, "strength"), 1e-9);
        assertFalse(getBoolean(force, "destroyed"));
    }

    private static Object createForce(GameContext ctx, Faction faction, String name, double x, double y) throws Exception {
        Method ensure = CampaignSystem.class.getDeclaredMethod("ensureCampaignForce",
                CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class, Faction.class,
                String.class, String.class, String.class, double.class, double.class);
        ensure.setAccessible(true);
        return ensure.invoke(null, ctx.campaign, CampaignSystem.CampaignForceKind.TASK_FORCE, faction,
                name, "allied-base", "friendly reporting regression", x, y);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 88001L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }
}
