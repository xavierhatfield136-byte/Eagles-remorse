import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMultiSourceIntelMilestoneThreeTest {

    @Test
    void losingOneObservationPreservesOtherValidSources() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createEnemyForce(ctx, "Multi-source Contact");
        int forceId = getInt(force, "id");

        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                10L, 11L, 0.96, 1200.0, 1300.0, 0.0);
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.ALLIED_REPORT,
                CampaignSystem.CampaignIntelPrecision.APPROXIMATE,
                10L, 20L, 0.72, 1180.0, 1320.0, 180.0);

        CampaignSystem.CampaignIntelResolution exact = CampaignSystem.campaignFleetIntelResolution(ctx, forceId, 10L);
        assertNotNull(exact);
        assertEquals(CampaignSystem.CampaignIntelPrecision.EXACT, exact.precision);
        assertEquals(CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR, exact.source);

        CampaignSystem.CampaignIntelResolution afterSensorExpiry =
                CampaignSystem.campaignFleetIntelResolution(ctx, forceId, 12L);
        assertNotNull(afterSensorExpiry);
        assertEquals(CampaignSystem.CampaignIntelPrecision.APPROXIMATE, afterSensorExpiry.precision);
        assertEquals(CampaignSystem.CampaignIntelObservationSource.ALLIED_REPORT, afterSensorExpiry.source);
    }

    @Test
    void operationKnowledgeNeverCreatesExactFleetPosition() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createEnemyForce(ctx, "Operation-only Contact");
        int forceId = getInt(force, "id");

        CampaignSystem.recordCampaignOperationIntelObservation(ctx, "attack-red-test",
                CampaignSystem.CampaignIntelObservationSource.OPERATION_INTEL,
                CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY,
                30L, 50L, 0.8, 2500.0, 2600.0, 600.0);

        CampaignSystem.CampaignIntelResolution operation =
                CampaignSystem.campaignOperationIntelResolution(ctx, "attack-red-test", 30L);
        assertNotNull(operation);
        assertEquals(CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY, operation.precision);
        assertFalse(operation.exactPosition());
        assertEquals(null, CampaignSystem.campaignFleetIntelResolution(ctx, forceId, 30L));
    }

    @Test
    void intelUpdatesCannotMutatePhysicalFleetState() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createEnemyForce(ctx, "Read-only Intel Contact");
        int forceId = getInt(force, "id");
        double x = getDouble(force, "x");
        double y = getDouble(force, "y");
        double strength = getDouble(force, "strength");
        String mission = getObject(force, "mission").toString();

        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.MISSION_INTEL,
                CampaignSystem.CampaignIntelPrecision.APPROXIMATE,
                4L, 8L, 0.65, x + 400.0, y - 300.0, 500.0);
        CampaignSystem.campaignFleetIntelResolution(ctx, forceId, 5L);

        assertEquals(x, getDouble(force, "x"), 1e-9);
        assertEquals(y, getDouble(force, "y"), 1e-9);
        assertEquals(strength, getDouble(force, "strength"), 1e-9);
        assertEquals(mission, getObject(force, "mission").toString());
    }

    @Test
    void expiredExactObservationDoesNotQualifyAsLiveContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createEnemyForce(ctx, "Expiring Contact");
        int forceId = getInt(force, "id");
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                100L, 101L, 1.0, 1000.0, 1000.0, 0.0);

        assertTrue(CampaignSystem.campaignFleetHasExactIntel(ctx, forceId, 101L));
        assertFalse(CampaignSystem.campaignFleetHasExactIntel(ctx, forceId, 102L));
    }

    @Test
    void approximateIntelRendersAsNonInteractiveUncertaintyInsteadOfFleet() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createEnemyForce(ctx, "Approximate Rendering Contact");
        int forceId = getInt(force, "id");
        ctx.campaign.campaignIntelTick = 40L;
        ctx.campaign.playerGalaxyX = 1500.0;
        ctx.campaign.playerGalaxyY = 1500.0;
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.SITE_RADAR,
                CampaignSystem.CampaignIntelPrecision.APPROXIMATE,
                40L, 45L, 0.64, 1600.0, 1650.0, 260.0);

        CampaignSystem.CampaignSupportMarker marker = CampaignSystem.activeSupportMarkers(ctx).stream()
                .filter(candidate -> candidate != null && "Approximate Hostile Contact".equals(candidate.label))
                .findFirst().orElse(null);
        assertNotNull(marker);
        assertEquals(CampaignSystem.SupportMarkerType.INTEL, marker.type);
        assertFalse(marker.interactive);
        assertEquals(1600.0, marker.x, 1e-9);
        assertEquals(1650.0, marker.y, 1e-9);
    }

    @Test
    void exactMarkerUsesObservedPositionAndStrategicOnlyIntelEmitsNoFleetMarker() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object exactForce = createEnemyForce(ctx, "Exact Observation Fleet");
        int exactId = getInt(exactForce, "id");
        Object strategicForce = createEnemyForce(ctx, "Strategic-only Fleet");
        int strategicId = getInt(strategicForce, "id");
        ctx.campaign.campaignIntelTick = 60L;
        ctx.campaign.playerGalaxyX = 1800.0;
        ctx.campaign.playerGalaxyY = 1800.0;
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, exactId,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                60L, 60L, 0.95, 1810.0, 1790.0, 0.0);
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, strategicId,
                CampaignSystem.CampaignIntelObservationSource.OPERATION_INTEL,
                CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY,
                60L, 65L, 0.8, 2200.0, 2200.0, 0.0);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker exact = markers.stream()
                .filter(candidate -> candidate != null && "Exact Observation Fleet".equals(candidate.label))
                .findFirst().orElse(null);
        assertNotNull(exact);
        assertEquals(1810.0, exact.x, 1e-9);
        assertEquals(1790.0, exact.y, 1e-9);
        assertTrue(exact.interactive);
        assertFalse(markers.stream().anyMatch(candidate -> candidate != null
                && candidate.label.contains("Strategic-only Fleet")));
    }

    private static Object createEnemyForce(GameContext ctx, String name) throws Exception {
        Method ensure = CampaignSystem.class.getDeclaredMethod("ensureCampaignForce",
                CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class, Faction.class,
                String.class, String.class, String.class, double.class, double.class);
        ensure.setAccessible(true);
        return ensure.invoke(null, ctx.campaign, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                name, "test-origin", "intel regression", 1400.0, 1500.0);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 33001L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
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
}
