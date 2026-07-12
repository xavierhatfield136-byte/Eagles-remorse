import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignEncounterMapIdentityTest {

    @Test
    void ambientResourceEncounterUsesSparseLocalMapIdentity() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation resourceSite = CampaignSystem.campaignAreasOfInterest(ctx).stream()
                .filter(location -> location != null && location.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE)
                .findFirst()
                .orElse(null);
        assertNotNull(resourceSite);

        invokeCampaignPrivate("launchAmbientCampaignLocationEncounter",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class},
                ctx, ctx.campaign, resourceSite);

        List<CampaignSystem.CampaignLandmark> landmarks = CampaignSystem.strategicLandmarks(ctx);
        assertTrue(landmarks.stream().anyMatch(landmark ->
                        "Ore Drift".equals(landmark.label) || resourceSite.name.equals(landmark.label)),
                "resource encounters should get their own sparse mining-pocket identity");
        assertFalse(landmarks.stream().anyMatch(landmark ->
                        "SUPPORT RELAY".equalsIgnoreCase(landmark.label) || "RESERVE STAGING".equalsIgnoreCase(landmark.label)),
                "ambient resource encounters should not inherit full campaign district landmarks");
    }

    @Test
    void openSpaceInterceptStopsLeakingSectorWideTaskForceOverlay() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object group = firstSearchGroup(ctx.campaign);
        assertNotNull(group);

        invokeCampaignPrivate("launchGalaxySearchGroupEncounter",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, Class.forName("CampaignSystemModels$GalaxySearchGroup")},
                ctx, ctx.campaign, group);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.strategicTaskForceMarkers(ctx);
        assertTrue(markers.isEmpty(),
                "local intercept maps should not keep drawing the whole sector's strategic task-force overlay");
        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                        .anyMatch(marker -> "Intercept Pocket".equalsIgnoreCase(marker.label)),
                "open-space intercepts should expose their own local intercept objective instead");
        assertTrue(CampaignSystem.strategicLandmarks(ctx).stream()
                        .anyMatch(landmark -> "Open-Space Intercept".equalsIgnoreCase(landmark.label)
                                || "Anchored Intercept".equalsIgnoreCase(landmark.label)),
                "intercept encounters should use encounter-specific local landmarks");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static Object invokeCampaignPrivate(String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
