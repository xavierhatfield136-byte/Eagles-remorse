import app.config.GameConfig;
import app.config.ExperienceSettings;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicTravelPressureTest {

    @Test
    void routeAssessmentMakesNorthernRoutesRiskierAndSlower() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.CampaignLocation southernHub = findLocation(ctx, "poi-02");
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(southernHub);
        assertNotNull(northernObjective);

        st.selectedGalaxyLocationId = southernHub.id;
        CampaignSystem.startTravelToSelectedLocation(ctx);
        double southRisk = st.galaxyTravel.interceptionRisk;
        double southDuration = st.galaxyTravel.durationSec;
        CampaignSystem.stopCampaignTravel(ctx);

        st.selectedGalaxyLocationId = northernObjective.id;
        CampaignSystem.startTravelToSelectedLocation(ctx);
        double northRisk = st.galaxyTravel.interceptionRisk;
        double northDuration = st.galaxyTravel.durationSec;

        assertTrue(northRisk > southRisk, "northern route should be riskier than southern hub route");
        assertTrue(northDuration > southDuration, "northern route should take longer than nearby southern route");

        List<String> routeLines = CampaignSystem.selectedRouteAssessmentLines(ctx);
        assertTrue(routeLines.stream().anyMatch(line -> line.contains("Risk " + Math.round(northRisk) + "%")));
        assertTrue(routeLines.stream().anyMatch(line -> line.startsWith("Route Forecast: ")));
        assertTrue(routeLines.stream().anyMatch(line -> line.startsWith("Route Notes: ")));
    }

    @Test
    void directEarthwardBurnSeedsVisibleRouteInterdictionForStrikeUse() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(northernObjective);

        double fromX = st.playerGalaxyX;
        double fromY = st.playerGalaxyY;
        st.selectedGalaxyLocationId = northernObjective.id;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));

        Object laneContact = visibleHostileLaneContact(st, fromX, fromY, northernObjective.x, northernObjective.y);
        assertNotNull(laneContact, "direct Earthward travel should expose an interdiction contact in the route lane");
        assertEquals("INTERCEPTING", getEnumName(laneContact, "behavior"));
        assertEquals("TRACKED", getEnumName(laneContact, "intelQuality"));
        assertTrue(st.lastTransitEncounterDebrief.startsWith("Interdiction"),
                "route interdiction should explain why it happened");
    }

    @Test
    void diplomacyAndFavorSupportReduceLaterRoutePressure() throws Exception {
        GameContext baselineCtx = initializedCampaignContext();
        CampaignSystem.CampaignState baseline = baselineCtx.campaign;
        CampaignSystem.CampaignLocation northernObjective = findLocation(baselineCtx, "poi-22");
        assertNotNull(northernObjective);

        baseline.selectedGalaxyLocationId = northernObjective.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(baselineCtx));
        double baselineRisk = baseline.galaxyTravel.interceptionRisk;
        double baselineDuration = baseline.galaxyTravel.durationSec;

        GameContext supportedCtx = initializedCampaignContext();
        CampaignSystem.CampaignState supported = supportedCtx.campaign;
        supported.vossRelationshipStateId = "TRUSTED";
        supported.marrRelationshipStateId = "HELPED";
        supported.greenContractFavor = 4;
        supported.yellowLiberationFavor = 3;
        supported.selectedGalaxyLocationId = northernObjective.id;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(supportedCtx));
        assertTrue(supported.galaxyTravel.interceptionRisk < baselineRisk,
                "trusted allied support should reduce later route interception risk");
        assertTrue(supported.galaxyTravel.durationSec < baselineDuration,
                "trusted allied support should improve later route tempo");

        List<String> routeLines = CampaignSystem.selectedRouteAssessmentLines(supportedCtx);
        assertTrue(routeLines.stream().anyMatch(line -> line.startsWith("Allied Support: ")));
    }

    @Test
    void lowStoresShowRecoveryRouteAndBlockUnsustainableTravel() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(northernObjective);

        st.selectedGalaxyLocationId = northernObjective.id;
        st.campaignFuel = 1;
        st.campaignSupplies = 1;

        List<String> routeLines = CampaignSystem.selectedRouteAssessmentLines(ctx);
        assertTrue(routeLines.stream().anyMatch(line -> line.startsWith("Route Warning: ")));
        assertTrue(routeLines.stream().anyMatch(line -> line.startsWith("Recovery Route: ")));
        assertTrue(CampaignSystem.campaignVisibleActions(ctx).stream()
                .anyMatch(action -> action.id.equals("ENGAGE_COURSE")
                        && !action.enabled
                        && action.disabledReason.contains("critically low")));

        CampaignSystem.CampaignAction recovery = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> action.id.equals("PLOT_RECOVERY_ROUTE"))
                .findFirst()
                .orElseThrow();
        assertTrue(recovery.enabled);
        assertTrue(recovery.execute.execute(ctx));
        assertTrue(st.selectedGalaxyLocationId != null && !st.selectedGalaxyLocationId.isBlank());
    }

    @Test
    void selectedContactSidebarExplainsIgnoredEscalationOutcome() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfType(st, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertNotNull(distress);

        distress.discovered = true;
        distress.completed = false;
        distress.consumed = false;
        distress.escalationStage = 1;
        st.selectedGalaxyLocationId = distress.id;

        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.contains("rescue window can close")),
                "ignored distress contacts should describe the concrete consequence");
    }

    @Test
    void selectedLocationShowsTimeSensitiveContactWindow() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfType(st, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertNotNull(distress);

        distress.discovered = true;
        distress.completed = false;
        distress.consumed = false;
        distress.unresolvedAgeSec = 30.0;
        distress.escalationStage = 0;
        st.selectedGalaxyLocationId = distress.id;

        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Time Limit: rescue warning in ")));
    }

    @Test
    void encounterDensityFollowsDifficultyPreset() throws Exception {
        GameContext relaxed = initializedCampaignContext(ExperienceSettings.forPreset(ExperienceSettings.Preset.RELAXED));
        GameContext standard = initializedCampaignContext(ExperienceSettings.forPreset(ExperienceSettings.Preset.STANDARD));
        GameContext iron = initializedCampaignContext(ExperienceSettings.forPreset(ExperienceSettings.Preset.IRON_COMMAND));
        GameContext tacticalOnly = initializedCampaignContext(ExperienceSettings.forPreset(ExperienceSettings.Preset.TACTICAL_ONLY));

        assertTrue(routeInterdictionRiskFloor(relaxed) > routeInterdictionRiskFloor(standard));
        assertTrue(routeInterdictionRiskFloor(iron) < routeInterdictionRiskFloor(standard));
        assertTrue(routeInterdictionRiskFloor(tacticalOnly) >= 100.0);

        CampaignSystem.CampaignLocation northernObjective = findLocation(relaxed, "poi-22");
        assertNotNull(northernObjective);
        relaxed.campaign.selectedGalaxyLocationId = northernObjective.id;
        List<String> routeLines = CampaignSystem.selectedRouteAssessmentLines(relaxed);
        assertTrue(routeLines.stream().anyMatch(line -> line.equals("Encounter Density: Light  x0.58")));
    }

    @Test
    void resourceTrendShowsStrikeRecoveryPath() {
        GameContext ctx = initializedCampaignContext();

        List<String> lines = CampaignSystem.campaignResourceTrendLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("STRIKE RECOVERY")
                        && line.contains("REARM")
                        && line.contains("BUY fuel/supplies")
                        && line.contains("SELL salvage")),
                "resource trends should explain how to recover strategic strike capacity");
    }

    @Test
    void trafficAuditAddsLocalTrafficAndFalsePositiveReadouts() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignSupplies = 20;

        assertTrue(CampaignSystem.requestCampaignTrafficAudit(ctx));
        List<String> rumorLines = CampaignSystem.campaignRumorBoardLines(ctx);
        assertTrue(rumorLines.stream().anyMatch(line -> line.startsWith("Local Traffic  |  ")));
        assertTrue(rumorLines.stream().anyMatch(line -> line.startsWith("Traffic Audit  |  ")
                && (line.contains("miners") || line.contains("convoys") || line.contains("runners"))));

        st.lastFalsePositiveContactSummary = "False Positive  |  civilian lane contact near test hub";
        rumorLines = CampaignSystem.campaignRumorBoardLines(ctx);
        assertTrue(rumorLines.stream().anyMatch(line -> line.startsWith("False Positive  |  ")));
    }

    @Test
    void travelLegsRecordReplayVariationAndMultiJumpContactChains() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation southernHub = findLocation(ctx, "poi-02");
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(southernHub);
        assertNotNull(northernObjective);

        st.strategicOvermapMode = true;
        st.selectedGalaxyLocationId = southernHub.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        List<String> firstLeg = CampaignSystem.campaignTransitVariationLines(ctx);
        assertTrue(firstLeg.stream().anyMatch(line -> line.startsWith("Transit Story  |  ")));
        assertTrue(firstLeg.stream().anyMatch(line -> line.startsWith("Regional Event  |  ")));
        assertTrue(firstLeg.stream().anyMatch(line -> line.startsWith("Traffic Pattern  |  ")));
        assertTrue(firstLeg.stream().anyMatch(line -> line.contains("stage 1/3")));

        assertTrue(CampaignSystem.campaignRumorBoardLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Contact Chain  |  ")));

        CampaignSystem.stopCampaignTravel(ctx);
        st.selectedGalaxyLocationId = northernObjective.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        List<String> secondLeg = CampaignSystem.campaignTransitVariationLines(ctx);
        assertTrue(secondLeg.stream().anyMatch(line -> line.contains("stage 2/3")),
                "uncertain contact chain should unfold across several route legs");
        assertTrue(CampaignSystem.selectedRouteAssessmentLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Traffic Pattern  |  ")));
    }

    @Test
    void campaignSeedsOpenSpaceHostilePatrolPressure() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        int roamingGroups = 0;
        for (Object group : searchGroups(st)) {
            if (getString(group, "anchorLocationId").startsWith("roam-")) roamingGroups++;
        }
        assertTrue(roamingGroups >= 4, "campaign should seed roaming open-space hostile fleets");
    }

    @Test
    void strategicOverlayInsightsExposePatrolTradeSensorAndBattleLayers() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        st.selectedStrategicOverlayId = "HOSTILE_ROUTES";
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Hostile Routes Overlay")
                        && line.contains("patrol routes")
                        && line.contains("hostile shadows")));

        st.selectedStrategicOverlayId = "TRADE";
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Trade Overlay")
                        && line.contains("convoy lanes")
                        && line.contains("shortages")));

        st.selectedStrategicOverlayId = "SENSORS";
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Sensor Overlay")
                        && line.contains("relays")
                        && line.contains("hidden leads")));

        st.selectedStrategicOverlayId = "DANGER";
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Danger Overlay")
                        && line.contains("active battles")
                        && line.contains("recent battle scars")));
    }

    @Test
    void strategicBookmarksNameAndRevisitRouteTargets() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation resource = firstAreaOfType(st, CampaignSystem.CampaignLocationType.RESOURCE_ZONE);
        CampaignSystem.CampaignLocation salvage = firstAreaOfType(st, CampaignSystem.CampaignLocationType.SALVAGE_FIELD);
        CampaignSystem.CampaignLocation repair = firstAreaOfType(st, CampaignSystem.CampaignLocationType.REPAIR_SITE);
        assertNotNull(resource);
        assertNotNull(salvage);
        assertNotNull(repair);

        resource.discovered = true;
        salvage.discovered = true;
        repair.discovered = true;
        st.selectedGalaxyLocationId = resource.id;
        assertTrue(CampaignSystem.bookmarkSelectedStrategicTarget(ctx));
        st.selectedGalaxyLocationId = salvage.id;
        assertTrue(CampaignSystem.bookmarkSelectedStrategicTarget(ctx));
        st.selectedGalaxyLocationId = repair.id;
        assertTrue(CampaignSystem.bookmarkSelectedStrategicTarget(ctx));

        List<String> lines = CampaignSystem.campaignMapBookmarkLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Mining: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Salvage: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("* Repair: ") || line.startsWith("Repair: ")));

        st.selectedGalaxyLocationId = "";
        assertTrue(CampaignSystem.selectNextCampaignMapBookmark(ctx));
        assertTrue(st.selectedGalaxyLocationId != null && !st.selectedGalaxyLocationId.isBlank());
        assertTrue(CampaignSystem.campaignVisibleActions(ctx).stream()
                .anyMatch(action -> action.id.equals("NEXT_BOOKMARK") && action.enabled));
    }

    @Test
    void routeQueueSkipsConditionalRecoveryStopWhenStoresAreStable() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation recovery = findLocation(ctx, "poi-02");
        CampaignSystem.CampaignLocation resource = firstAreaOfType(st, CampaignSystem.CampaignLocationType.RESOURCE_ZONE);
        assertNotNull(recovery);
        assertNotNull(resource);

        st.strategicOvermapMode = true;
        st.campaignFuel = 120;
        st.campaignSupplies = 90;
        st.campaignAmmo = 110;
        st.selectedGalaxyLocationId = recovery.id;
        assertTrue(CampaignSystem.queueSelectedRouteStop(ctx));
        st.selectedGalaxyLocationId = resource.id;
        assertTrue(CampaignSystem.queueSelectedRouteStop(ctx));

        List<String> queued = CampaignSystem.campaignRouteQueueLines(ctx);
        assertTrue(queued.stream().anyMatch(line -> line.contains("IF LOW STORES")));
        assertTrue(queued.stream().anyMatch(line -> line.contains("ALWAYS")));

        assertTrue(CampaignSystem.startQueuedRoute(ctx));
        assertTrue(st.galaxyTravel.traveling);
        assertEquals(resource.id, st.galaxyTravel.destinationId);
        assertTrue(st.campaignRouteQueue.isEmpty(), "skipped recovery and active destination should both leave the queue");
    }

    @Test
    void routeQueueAdvancesToNextFreeSpaceStopAfterArrival() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicOvermapMode = true;
        double firstX = Math.min(ctx.WORLD_W - 500.0, st.playerGalaxyX + 320.0);
        double firstY = Math.min(ctx.WORLD_H - 500.0, st.playerGalaxyY + 280.0);
        double secondX = Math.min(ctx.WORLD_W - 220.0, firstX + 360.0);
        double secondY = Math.min(ctx.WORLD_H - 220.0, firstY + 260.0);

        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(ctx, firstX, firstY));
        assertTrue(CampaignSystem.queueSelectedRouteStop(ctx));
        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(ctx, secondX, secondY));
        assertTrue(CampaignSystem.queueSelectedRouteStop(ctx));

        assertTrue(CampaignSystem.startQueuedRoute(ctx));
        assertTrue(st.galaxyTravel.traveling);
        st.galaxyTravel.speed = 10000.0;
        invokeTravelUpdate(ctx, st, 0.1);

        assertTrue(st.galaxyTravel.traveling, "arrival should auto-engage the next queued free-space stop");
        assertEquals("", st.galaxyTravel.destinationId);
        assertEquals(secondX, st.galaxyTravel.targetX, 0.1);
        assertEquals(secondY, st.galaxyTravel.targetY, 0.1);
        assertTrue(st.campaignRouteQueue.isEmpty());
    }

    @Test
    void movingBlockadeLineRaisesRouteRiskAndCreatesMapMarker() throws Exception {
        GameContext baselineCtx = initializedCampaignContext();
        CampaignSystem.CampaignState baseline = baselineCtx.campaign;
        baseline.strategicOvermapMode = true;
        double startX = baseline.playerGalaxyX;
        double startY = baseline.playerGalaxyY;
        double targetX = Math.min(baselineCtx.WORLD_W - 300.0, startX + 1200.0);
        double targetY = startY;
        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(baselineCtx, targetX, targetY));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(baselineCtx));
        double baselineRisk = baseline.galaxyTravel.interceptionRisk;

        GameContext blockadeCtx = initializedCampaignContext();
        CampaignSystem.CampaignState blockade = blockadeCtx.campaign;
        blockade.strategicOvermapMode = true;
        blockade.playerGalaxyX = startX;
        blockade.playerGalaxyY = startY;
        Object force = firstEnemyCampaignForce(blockade);
        assertNotNull(force);
        setDouble(force, "x", startX + 600.0);
        setDouble(force, "y", startY - 420.0);
        setDouble(force, "targetX", startX + 600.0);
        setDouble(force, "targetY", startY + 520.0);
        setDouble(force, "strength", 95.0);
        setDouble(force, "speed", 180.0);
        setDouble(force, "uncertaintyRadius", 220.0);
        setDouble(force, "contactConfidence", 1.0);
        setBoolean(force, "visibleToPlayer", true);
        setBoolean(force, "simulationActive", true);
        setEnum(force, "intent", "GUARDING");
        setEnum(force, "contactState", "KNOWN");

        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(blockadeCtx, targetX, targetY));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(blockadeCtx));

        assertTrue(blockade.galaxyTravel.interceptionRisk > baselineRisk + 8.0,
                "a moving blockade line crossing the plotted route should materially raise route risk");
        assertTrue(CampaignSystem.activeSupportMarkers(blockadeCtx).stream()
                .anyMatch(marker -> marker.label.startsWith("Moving Blockade Line:")
                        && marker.subtitle.contains("MOVING BLOCKADE")));
    }

    @Test
    void arrivalConsequencesDifferentiateTimingStoresAndOverpreparedRuns() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfType(st, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        CampaignSystem.CampaignLocation resource = firstAreaOfType(st, CampaignSystem.CampaignLocationType.RESOURCE_ZONE);
        assertNotNull(distress);
        assertNotNull(resource);

        st.galaxyTravel.interceptionRisk = 10.0f;
        st.campaignFuel = 120;
        st.campaignSupplies = 90;
        st.campaignAmmo = 110;
        distress.escalationStage = 0;
        distress.unresolvedAgeSec = 10.0;
        assertTrue(invokeArrivalConsequence(ctx, st, distress).contains("early arrival"));

        distress.escalationStage = 2;
        distress.unresolvedAgeSec = 100.0;
        assertTrue(invokeArrivalConsequence(ctx, st, distress).contains("late arrival"));

        st.campaignFuel = 12;
        st.campaignSupplies = 9;
        st.campaignAmmo = 11;
        assertTrue(invokeArrivalConsequence(ctx, st, resource).contains("depleted arrival"));

        st.campaignFuel = 120;
        st.campaignSupplies = 90;
        st.campaignAmmo = 110;
        assertTrue(invokeArrivalConsequence(ctx, st, resource).contains("overprepared arrival"));
    }

    @Test
    void travelArrivalRecordsConsequenceDebrief() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfType(st, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertNotNull(distress);
        st.strategicOvermapMode = true;
        distress.discovered = true;
        distress.escalationStage = 0;
        distress.unresolvedAgeSec = 8.0;
        st.playerGalaxyX = Math.max(0.0, distress.x - 320.0);
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        st.galaxyTravel.speed = 10000.0;
        invokeTravelUpdate(ctx, st, 0.1);

        assertTrue(st.lastTransitEncounterDebrief.contains("Arrival Consequence: early arrival"));
    }

    @Test
    void selectedSiteAndContactExposeScanDetails() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfType(st, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertNotNull(distress);

        distress.discovered = true;
        st.selectedGalaxyLocationId = distress.id;
        List<String> siteLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(siteLines.stream().anyMatch(line -> line.startsWith("Scan Detail: Distress Cause: ")));

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setBoolean(group, "visible", true);
        setEnum(group, "contactConfidence", "IDENTIFIED_TASK_FORCE");
        setEnum(group, "intelQuality", "TARGET_QUALITY");
        double x = getDouble(group, "x");
        double y = getDouble(group, "y");
        st.selectedGalaxyLocationId = "";
        CampaignSystem.selectCampaignContactTarget(ctx, "test contact", "", "Target-Quality", x, y, true, true);

        List<String> contactLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(contactLines.stream().anyMatch(line -> line.contains("Route Intent: ")));
        assertTrue(contactLines.stream().anyMatch(line -> line.contains("Cargo Signature: ")));
    }

    @Test
    void searchGroupConfidenceTransitionsFromPossibleToIdentifiedToLost() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        st.campaignIntelLevel = 0.0;
        st.enemyAlertLevel = 0.0;

        double playerX = st.playerGalaxyX;
        double playerY = st.playerGalaxyY;
        double detection = getDouble(group, "detectionRange");

        setDouble(group, "x", playerX + detection * 1.15);
        setDouble(group, "y", playerY);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("POSSIBLE_PATROL", getEnumName(group, "contactConfidence"));

        setDouble(group, "x", playerX + detection * 0.35);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("IDENTIFIED_TASK_FORCE", getEnumName(group, "contactConfidence"));

        setDouble(group, "x", playerX + detection * 6.0);
        setDouble(group, "targetX", playerX + detection * 6.0);
        setDouble(group, "targetY", playerY);
        setDouble(group, "stateTimer", 999.0);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("LOST_CONTACT", getEnumName(group, "contactConfidence"));

        invokeSearchUpdate(ctx, st, 12.0);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("UNKNOWN_CONTACT", getEnumName(group, "contactConfidence"));
    }

    private static GameContext initializedCampaignContext() {
        return initializedCampaignContext(ExperienceSettings.forPreset(ExperienceSettings.Preset.STANDARD));
    }

    private static GameContext initializedCampaignContext(ExperienceSettings experience) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false)
                .withExperience(experience));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static double routeInterdictionRiskFloor(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("routeInterdictionRiskFloor", GameContext.class);
        method.setAccessible(true);
        return (double) method.invoke(null, ctx);
    }

    private static void invokeSearchUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxySearchGroups",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
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

    private static String invokeArrivalConsequence(GameContext ctx,
                                                   CampaignSystem.CampaignState st,
                                                   CampaignSystem.CampaignLocation location) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "arrivalConsequenceLine",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class,
                CampaignSystem.CampaignTravelState.class
        );
        method.setAccessible(true);
        return (String) method.invoke(null, ctx, st, location, st.galaxyTravel);
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

    private static CampaignSystem.CampaignLocation firstAreaOfType(CampaignSystem.CampaignState st,
                                                                   CampaignSystem.CampaignLocationType type) {
        for (CampaignSystem.CampaignLocation location : st.galaxyAreasOfInterest) {
            if (location != null && location.type == type) return location;
        }
        return null;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        List<?> groups = searchGroups(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static Object firstEnemyCampaignForce(CampaignSystem.CampaignState st) throws Exception {
        for (Object force : st.campaignForces) {
            if ("ENEMY".equals(getEnumName(force, "faction")) && !"PLAYER_FLEET".equals(getEnumName(force, "kind"))) {
                return force;
            }
        }
        return null;
    }

    private static List<?> searchGroups(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        return (List<?>) field.get(st);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> type = field.getType();
        field.set(target, Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), value));
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static String getEnumName(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return (value == null) ? "" : value.toString();
    }

    private static String getString(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return (value == null) ? "" : value.toString();
    }

    private static Object visibleHostileLaneContact(CampaignSystem.CampaignState st,
                                                    double fromX,
                                                    double fromY,
                                                    double targetX,
                                                    double targetY) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        for (Object group : groups) {
            if (group == null) continue;
            if (!getBoolean(group, "hostile") || !getBoolean(group, "visible")) continue;
            double x = getDouble(group, "x");
            double y = getDouble(group, "y");
            double lane = distancePointToSegment(x, y, fromX, fromY, targetX, targetY);
            if (lane <= 620.0) return group;
        }
        return null;
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static double distancePointToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 <= 1e-6) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2));
        double sx = ax + dx * t;
        double sy = ay + dy * t;
        return Math.hypot(px - sx, py - sy);
    }
}
