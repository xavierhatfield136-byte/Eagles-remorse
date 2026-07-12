import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicLoopIntegrationTest {

    @Test
    void strategicLoopSupportsIntelTravelContactsMissionResolutionAndNorthboundContinuation() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation start = CampaignSystem.currentCampaignLocation(ctx);
        CampaignSystem.CampaignLocation cache = findLocation(ctx, "aoi-cache-1");
        CampaignSystem.CampaignLocation mission = findLocation(ctx, "poi-08");
        CampaignSystem.CampaignLocation north = findLocation(ctx, "poi-20");

        assertNotNull(start);
        assertNotNull(cache);
        assertNotNull(mission);
        assertNotNull(north);

        st.selectedGalaxyLocationId = start.id;
        st.strategicExposureLevel = Math.max(12.0, st.strategicExposureLevel);
        double intelBefore = st.campaignIntelLevel;
        double exposureBefore = st.strategicExposureLevel;
        int visibleBefore = visibleSearchGroupCount(st);
        assertTrue(CampaignSystem.openSelectedHubService(ctx, CampaignSystem.HubService.INTEL));
        assertTrue(CampaignSystem.confirmSelectedHubService(ctx));
        assertTrue(st.campaignIntelLevel > intelBefore);
        assertTrue(st.strategicExposureLevel < exposureBefore);
        assertTrue(visibleSearchGroupCount(st) >= visibleBefore);

        st.selectedGalaxyLocationId = cache.id;
        st.strategicTorpedoCharges = Math.max(0, st.strategicTorpedoCharges - 1);
        int torpedoesBefore = st.strategicTorpedoCharges;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        tickTravelUntilSettled(ctx, st, 400);
        assertEquals(cache.id, st.currentGalaxyLocationId);
        assertEquals(cache.id, st.dockedGalaxyLocationId);
        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));
        assertEquals(torpedoesBefore + 1, st.strategicTorpedoCharges);

        double alertBeforeIntercept = st.enemyAlertLevel;
        int suppliesBeforeIntercept = st.campaignSupplies;
        Object group = firstSearchGroup(st);
        st.sectorElapsed = 999.0;
        setDouble(group, "x", st.playerGalaxyX);
        setDouble(group, "y", st.playerGalaxyY);
        setString(group, "anchorLocationId", "");
        invokeDetectionUpdate(ctx, st, 0.1);
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
        assertTrue(st.strategicOvermapMode);
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(st.enemyAlertLevel >= alertBeforeIntercept);
        assertTrue(st.campaignSupplies <= suppliesBeforeIntercept);

        st.selectedGalaxyLocationId = mission.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        tickTravelUntilPrompt(ctx, st, 500);
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION, ctx.ui.strategicEncounterPrompt.kind);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.contains("single large tactical sector"));
        assertTrue(ctx.ui.strategicEncounterPrompt.strengthReadout.contains("MANUAL PRIORITY")
                || ctx.ui.strategicEncounterPrompt.strengthReadout.contains("AUTO-RESOLVE: VIABLE"));
        int completedBefore = st.completedMainMissions;
        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
        assertTrue(st.completedMainMissions > completedBefore);
        assertTrue(st.strategicOvermapMode);
        assertTrue(CampaignSystem.earthProgress(ctx) > 0.0);

        st.selectedGalaxyLocationId = north.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(st.galaxyTravel.traveling);
        List<String> summary = CampaignSystem.campaignSummarySidebarLines(ctx);
        assertTrue(summary.stream().anyMatch(line -> line.startsWith("Travel: ")));
        assertTrue(summary.stream().anyMatch(line -> line.startsWith("Hunt Status: ")));
        assertTrue(summary.stream().anyMatch(line -> line.startsWith("Alert / Pressure: ")));
    }

    @Test
    void overmapCarriesTensionThroughRiskUncertaintyDockingAndRoutePlanning() {
        GameContext ctx = initializedGalaxyTestContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation south = findLocation(ctx, "poi-06");
        CampaignSystem.CampaignLocation north = findLocation(ctx, "poi-20");

        assertNotNull(south);
        assertNotNull(north);

        st.selectedGalaxyLocationId = south.id;
        int southRisk = parsePercentLine(CampaignSystem.compactRouteAssessmentLines(ctx), "Risk: ");

        st.selectedGalaxyLocationId = north.id;
        int northRisk = parsePercentLine(CampaignSystem.compactRouteAssessmentLines(ctx), "Risk: ");
        List<String> selectedLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        List<String> intelLines = CampaignSystem.galaxyIntelSummaryLines(ctx);
        List<String> summary = CampaignSystem.campaignSummarySidebarLines(ctx);

        assertTrue(northRisk > southRisk);
        assertTrue(selectedLines.stream().anyMatch(line -> line.startsWith("Docking: ")));
        assertTrue(selectedLines.stream().anyMatch(line -> line.startsWith("Route: ")));
        assertTrue(selectedLines.stream().anyMatch(line -> line.startsWith("Risk: ")));
        assertTrue(intelLines.stream().anyMatch(line -> line.startsWith("Intel Quality")));
        assertTrue(intelLines.stream().anyMatch(line -> line.startsWith("Operational Exposure")));
        assertTrue(summary.stream().anyMatch(line -> line.startsWith("Hunt Status: ")));
        assertTrue(summary.stream().anyMatch(line -> line.startsWith("Alert / Pressure: ")));
    }

    @Test
    void localSiteSuppressesRemoteCampaignBattleInterventionPrompts() throws Exception {
        GameContext ctx = initializedPlayerFacingCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation loaded = findLocation(ctx, "poi-01");
        CampaignSystem.CampaignLocation remote = findLocation(ctx, "poi-20");

        assertNotNull(loaded);
        assertNotNull(remote);
        assertFalse(CampaignSystem.isStrategicOvermapMode(ctx));
        assertEquals(loaded.id, st.activeGalaxyEncounterLocationId);

        Object remoteGreen = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.TEAM_C,
                "Remote Green Test Patrol",
                remote.name,
                "Remote prompt regression friendly",
                remote.x,
                remote.y);
        Object remoteRed = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT,
                Faction.ENEMY,
                "Remote Red Test Patrol",
                remote.name,
                "Remote prompt regression hostile",
                remote.x + 12.0,
                remote.y + 12.0);
        Object remoteBattle = newCampaignBattle(st, remoteGreen, remoteRed);

        invokeMaybeShowCampaignBattleIntervention(ctx, st, remoteBattle);

        assertFalse(ctx.ui.strategicEncounterPrompt.active,
                "loaded local sites should not offer join prompts for battles in a different overmap zone");

        Object localGreen = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.TEAM_C,
                "Local Green Test Patrol",
                loaded.name,
                "Local prompt regression friendly",
                loaded.x,
                loaded.y);
        Object localRed = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT,
                Faction.ENEMY,
                "Local Red Test Patrol",
                loaded.name,
                "Local prompt regression hostile",
                loaded.x + 10.0,
                loaded.y + 10.0);
        Object localBattle = newCampaignBattle(st, localGreen, localRed);

        invokeMaybeShowCampaignBattleIntervention(ctx, st, localBattle);

        assertTrue(ctx.ui.strategicEncounterPrompt.active,
                "battles anchored to the loaded local site should still offer an intervention prompt");
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE, ctx.ui.strategicEncounterPrompt.kind);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static GameContext initializedGalaxyTestContext() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 2468L, false,
                0, false, 1, "", "", "galaxy_map_test"));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static GameContext initializedPlayerFacingCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false)
                .withAutoLaunchCampaignStartSite(true));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static void tickTravelUntilSettled(GameContext ctx, CampaignSystem.CampaignState st, int maxTicks) throws Exception {
        for (int i = 0; i < maxTicks; i++) {
            invokeTravelUpdate(ctx, st, 1.0);
            if (!st.galaxyTravel.traveling && !ctx.ui.strategicEncounterPrompt.active) {
                return;
            }
        }
        throw new AssertionError("travel did not settle");
    }

    private static void tickTravelUntilPrompt(GameContext ctx, CampaignSystem.CampaignState st, int maxTicks) throws Exception {
        for (int i = 0; i < maxTicks; i++) {
            invokeTravelUpdate(ctx, st, 1.0);
            if (ctx.ui.strategicEncounterPrompt.active) {
                return;
            }
        }
        throw new AssertionError("travel did not raise encounter prompt");
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeDetectionUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxyDetectionAndInterception",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static Object ensureCampaignForce(CampaignSystem.CampaignState st,
                                              CampaignSystem.CampaignForceKind kind,
                                              Faction faction,
                                              String name,
                                              String origin,
                                              String purpose,
                                              double x,
                                              double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForceWithoutDeploymentCost",
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignForceKind.class,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class
        );
        method.setAccessible(true);
        return method.invoke(null, st, kind, faction, name, origin, purpose, x, y);
    }

    private static Object newCampaignBattle(CampaignSystem.CampaignState st, Object a, Object b) throws Exception {
        Class<?> forceClass = Class.forName("CampaignSystemModels$CampaignForce");
        Class<?> battleClass = Class.forName("CampaignSystemModels$CampaignBattle");
        java.lang.reflect.Constructor<?> ctor = battleClass.getDeclaredConstructor(int.class, forceClass, forceClass);
        ctor.setAccessible(true);
        Object battle = ctor.newInstance(st.nextCampaignBattleId++, a, b);
        Field battlesField = CampaignSystem.CampaignState.class.getDeclaredField("campaignBattles");
        battlesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> battles = (List<Object>) battlesField.get(st);
        battles.add(battle);
        return battle;
    }

    private static void invokeMaybeShowCampaignBattleIntervention(GameContext ctx,
                                                                  CampaignSystem.CampaignState st,
                                                                  Object battle) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maybeShowCampaignBattleIntervention",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                Class.forName("CampaignSystemModels$CampaignBattle")
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, battle);
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        assertTrue(!groups.isEmpty());
        return groups.get(0);
    }

    private static int visibleSearchGroupCount(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        int count = 0;
        for (Object group : groups) {
            Field visible = group.getClass().getDeclaredField("visible");
            visible.setAccessible(true);
            if (visible.getBoolean(group)) {
                count++;
            }
        }
        return count;
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setString(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int parsePercentLine(List<String> lines, String prefix) {
        Pattern pattern = Pattern.compile("(\\d+)%");
        for (String line : lines) {
            if (line == null || !line.startsWith(prefix)) continue;
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        throw new AssertionError("missing percent line for prefix " + prefix);
    }
}
