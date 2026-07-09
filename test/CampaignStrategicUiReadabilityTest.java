import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicUiReadabilityTest {

    @Test
    void campaignSummarySidebarHighlightsTravelHuntAndPressure() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-10";
        CampaignSystem.startTravelToSelectedLocation(ctx);

        List<String> lines = CampaignSystem.campaignSummarySidebarLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Travel: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Hunt Status: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Enemy Alert: ")));
    }

    @Test
    void selectedLocationSidebarIncludesDockingThreatAndRouteSignal() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";

        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Threat: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Docking: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Route: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Risk: ")));
    }

    @Test
    void navigationAndStrikeSidebarsStayCompactEnoughToScan() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";

        List<String> fleet = CampaignSystem.campaignFleetManagerLines(ctx);
        List<String> summary = CampaignSystem.campaignSummarySidebarLines(ctx);
        List<String> selected = CampaignSystem.selectedLocationSidebarLines(ctx);
        List<String> strikeTop = CampaignSystem.campaignStrikeManagerLines(ctx);
        List<String> strikeReadiness = CampaignSystem.campaignStrikeReadinessLines(ctx);
        List<String> strikeConsequences = CampaignSystem.campaignStrikeConsequenceLines(ctx);

        assertTrue(fleet.stream().anyMatch(line -> line.startsWith("Overmap Berths: ")));
        assertTrue(fleet.stream().anyMatch(line -> line.startsWith("Flagship Capability: ")));
        assertTrue(summary.size() <= 8, "navigation summary should be glanceable");
        assertTrue(selected.size() <= 15, "selected location details should fit the sidebar");
        assertTrue(strikeTop.size() <= 5, "strike control should avoid duplicated readiness prose");
        assertTrue(strikeReadiness.size() <= 4, "strike readiness should stay compact");
        assertTrue(strikeConsequences.size() <= 7, "strike consequences should stay compact");
    }

    @Test
    void galaxyMapZoomOutIsClampedAroundOperationalView() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;
        UISystem.resetStrategicMapZoom(ctx);

        for (int i = 0; i < 18; i++) {
            UISystem.stepStrategicMapZoom(ctx, -1, 960, 540, 1920, 1080);
        }

        assertTrue(UISystem.strategicMapZoom(ctx) >= 1.85,
                "galaxy map should not zoom out to the noisy full-board view");
    }

    @Test
    void strategicMapZoomRulesKeepMajorFacilitiesAndCullMinorClutter() throws Exception {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = 1.85;
        CampaignSystem.CampaignObjectiveMarker major = new CampaignSystem.CampaignObjectiveMarker(
                CampaignSystem.ObjectiveMarkerType.PRIMARY_OBJECTIVE,
                "Earthfall Bastion",
                "major facility",
                Faction.ENEMY,
                2400.0,
                800.0,
                120.0,
                82);
        CampaignSystem.CampaignObjectiveMarker minor = new CampaignSystem.CampaignObjectiveMarker(
                CampaignSystem.ObjectiveMarkerType.PROTECTED_ASSET,
                "Minor Prospecting Camp",
                "minor facility",
                Faction.TEAM_D,
                2600.0,
                980.0,
                80.0,
                24);

        assertTrue(shouldDrawObjectiveMarker(ctx, major), "major facilities should remain visible at far zoom");
        assertFalse(shouldDrawObjectiveMarker(ctx, minor), "minor facilities should hide at far zoom");

        ctx.ui.selectedCampaignContactLabel = minor.label;
        ctx.ui.selectedCampaignContactX = minor.x;
        ctx.ui.selectedCampaignContactY = minor.y;
        assertTrue(shouldDrawObjectiveMarker(ctx, minor), "selected minor facilities should remain visible");

        ctx.ui.selectedCampaignContactLabel = "";
        ctx.ui.selectedCampaignContactX = Double.NaN;
        ctx.ui.selectedCampaignContactY = Double.NaN;
        ctx.ui.strategicMapZoom = 3.4;
        assertTrue(shouldDrawObjectiveMarker(ctx, minor), "minor facilities should return at close zoom");
    }

    @Test
    void strategicMapSelectionRulesPreserveExistingClickBehavior() throws Exception {
        GameContext strategic = initializedCampaignContext();
        strategic.campaign.strategicOvermapMode = true;
        strategic.ui.selectedCampaignContactLabel = "Relay Contact";
        strategic.ui.selectedCampaignContactX = 1200.0;
        strategic.ui.selectedCampaignContactY = 1600.0;

        assertTrue(isSelectedMapMarker(strategic, "Other", 1210.0, 1610.0),
                "strategic contact click/selection should match nearby markers");
        assertTrue(isSelectedMapMarker(strategic, "Relay Contact", 3000.0, 3000.0),
                "strategic contact click/selection should match marker label");

        GameContext tactical = initializedCampaignContext();
        tactical.campaign.enabled = false;
        tactical.campaign.strategicOvermapMode = false;
        tactical.ui.tacticalMapSelectionLabel = "Kill Pocket";
        tactical.ui.tacticalMapSelectionX = 1800.0;
        tactical.ui.tacticalMapSelectionY = 1900.0;

        assertTrue(isSelectedMapMarker(tactical, "Other", 1810.0, 1905.0),
                "tactical selection should still match nearby map actions");
        assertTrue(isSelectedMapMarker(tactical, "Kill Pocket", 1000.0, 1000.0),
                "tactical selection should still match marker label");
    }

    @Test
    void campaignLocationControlViewSeparatesControlColorFromSiteType() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = true;

        CampaignSystem.CampaignLocation red = locationContaining(ctx, "RED CORRIDOR BREAKPOINT");
        CampaignSystem.CampaignLocation green = locationContaining(ctx, "GREEN ANCHORAGE");
        CampaignSystem.CampaignLocation yellow = locationContaining(ctx, "YELLOW COMMERCE");

        CampaignSystem.CampaignLocationControlView redView = CampaignSystem.campaignLocationControlView(ctx, red);
        CampaignSystem.CampaignLocationControlView greenView = CampaignSystem.campaignLocationControlView(ctx, green);
        CampaignSystem.CampaignLocationControlView yellowView = CampaignSystem.campaignLocationControlView(ctx, yellow);

        assertEquals(CampaignSystem.CampaignControlVisualState.RED, redView.control);
        assertEquals(CampaignSystem.CampaignControlVisualState.GREEN, greenView.control);
        assertEquals(CampaignSystem.CampaignControlVisualState.YELLOW, yellowView.control);
        assertTrue(redView.siteType.toUpperCase(java.util.Locale.US).contains("CHECKPOINT")
                        || redView.siteType.toUpperCase(java.util.Locale.US).contains("DEFENSE"),
                "hostile control should not erase the site's tactical type");
        assertTrue(yellowView.status.toUpperCase(java.util.Locale.US).contains("YELLOW"),
                "yellow trade locations should render as neutral Yellow control, not Red by default");
    }

    @Test
    void galaxyLeftPanelReadsAsMissionIntelligence() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";

        List<String> intel = CampaignSystem.campaignReceiverBoardLines(ctx);
        List<String> route = CampaignSystem.campaignDirectionFinderLines(ctx);
        List<String> changes = CampaignSystem.campaignCommsBoardLines(ctx);

        assertTrue(intel.stream().anyMatch(line -> line.startsWith("Objective: ")));
        assertTrue(intel.stream().anyMatch(line -> line.startsWith("Fleet: ")));
        assertTrue(route.stream().anyMatch(line -> line.startsWith("Travel Danger: ")));
        assertTrue(changes.stream().anyMatch(line -> line.startsWith("War Change: ")));
        assertTrue(intel.size() <= 8, "mission intel should stay glanceable");
        assertTrue(route.size() <= 8, "route threat block should stay glanceable");
    }

    @Test
    void strategicMapOverlayFiltersCoverFacilitiesMissionsFleetsRoutesAndIntel() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        st.selectedStrategicOverlayId = StrategicCampaignExpansionSystem.MapOverlay.FACILITIES.name();
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Facilities Filter  |")));

        st.selectedStrategicOverlayId = StrategicCampaignExpansionSystem.MapOverlay.MISSIONS.name();
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Missions Filter  |")));

        st.selectedStrategicOverlayId = StrategicCampaignExpansionSystem.MapOverlay.FLEETS.name();
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Fleets Filter  |")));

        st.selectedStrategicOverlayId = StrategicCampaignExpansionSystem.MapOverlay.ROUTES.name();
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Routes Filter  |")));

        st.selectedStrategicOverlayId = StrategicCampaignExpansionSystem.MapOverlay.INTEL.name();
        assertTrue(CampaignSystem.strategicOverlayInsightLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Intel Filter  |")));
    }

    @Test
    void overmapStrikeTabShowsReconButNoRemoteWeaponButtons() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);

        assertFalse(actions.stream().anyMatch(action -> action.id.startsWith("POSTURE_")));
        assertFalse(actions.stream().anyMatch(action -> action.id.equals("TORPEDO_STRIKE")));
        assertFalse(actions.stream().anyMatch(action -> action.id.equals("CARRIER_SORTIE")));
        assertFalse(actions.stream().anyMatch(action -> action.id.equals("ATOMIC_STRIKE")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("TRACK_TARGET")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("RECON_SWEEP")
                || action.id.equals("SIGNAL_SWEEP")));
    }

    @Test
    void disabledCampaignActionsAlwaysExposePlayerFacingReasons() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;

        for (UiState.CampaignCommandTab tab : UiState.CampaignCommandTab.values()) {
            ctx.ui.campaignCommandTab = tab;
            for (CampaignSystem.CampaignAction action : CampaignSystem.campaignVisibleActions(ctx)) {
                if (action == null || action.enabled) continue;
                assertFalse(action.disabledReason == null || action.disabledReason.isBlank(),
                        "disabled action should explain itself: tab=" + tab + " id=" + action.id);
            }
        }
    }

    @Test
    void fleetDebugReadoutIncludesDoctrineAndLifecycleFields() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, ctx.campaign, 0.2);

        List<String> debug = CampaignSystem.campaignFleetAiDebugLines(ctx);
        List<CampaignSystem.CampaignForceSummary> summaries = CampaignSystem.campaignForceSummaries(ctx);

        assertTrue(debug.stream().anyMatch(line -> line.contains("doctrine ")
                        && line.contains("fleeRatio=")
                        && line.contains("raidRatio=")),
                "fleet debug lines should expose doctrine thresholds");
        assertTrue(summaries.stream().anyMatch(summary -> summary.doctrineSummary.contains("doctrine")
                        && !summary.workState.isBlank()
                        && !summary.stopReason.isBlank()),
                "fleet summaries should expose doctrine and lifecycle fields");
    }

    @Test
    void fleetSummariesExposeCompactLabelsIntelLabelsAndTooltips() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, ctx.campaign, 0.2);

        List<CampaignSystem.CampaignForceSummary> summaries = CampaignSystem.campaignForceSummaries(ctx);

        assertTrue(summaries.stream().anyMatch(summary -> summary.statusLabel.contains(" - ")
                        && !summary.intelLabel.isBlank()),
                "fleet summaries should expose compact map labels and intel labels");
        assertTrue(summaries.stream().anyMatch(summary -> Double.isFinite(summary.lastKnownVelocityX)
                        && Double.isFinite(summary.lastKnownVelocityY)
                        && summary.lastSeenSec >= 0.0),
                "fleet summaries should expose last-known velocity and last-seen contact memory");
        assertTrue(summaries.stream().anyMatch(summary -> summary.tooltipLines.stream().anyMatch(line -> line.startsWith("Because: "))
                        && summary.tooltipLines.stream().anyMatch(line -> line.startsWith("Doing: "))
                        && summary.tooltipLines.stream().anyMatch(line -> line.startsWith("Next: "))),
                "fleet tooltips should provide Because/Doing/Next lines");
    }

    @Test
    void longFleetStatusLabelsRenderWithoutOverlappingAtDefaultZoom() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;
        ctx.ui.selectedCampaignContactLabel = "clustered fleet contacts";
        ctx.ui.selectedCampaignContactX = 2500.0;
        ctx.ui.selectedCampaignContactY = 2500.0;
        UISystem.resetStrategicMapZoom(ctx);

        List<CampaignSystem.CampaignSupportMarker> markers = List.of(
                new CampaignSystem.CampaignSupportMarker(CampaignSystem.SupportMarkerType.FORCE_STRIKE,
                        "Green Response Corvette Line Escorting Refugee Logistics Convoy",
                        "known force contact", Faction.TEAM_C, 2485.0, 2498.0, 120.0, 80),
                new CampaignSystem.CampaignSupportMarker(CampaignSystem.SupportMarkerType.FORCE_CONVOY,
                        "Yellow Commerce Spine Heavy Cargo Transfer Group",
                        "known force contact", Faction.TEAM_D, 2504.0, 2501.0, 120.0, 78),
                new CampaignSystem.CampaignSupportMarker(CampaignSystem.SupportMarkerType.FORCE_SEARCH,
                        "Red Hunter Killer Pursuit Armada Forward Scout Screen",
                        "known force contact", Faction.ENEMY, 2522.0, 2492.0, 120.0, 76),
                new CampaignSystem.CampaignSupportMarker(CampaignSystem.SupportMarkerType.FORCE_PATROL,
                        "Green Relay Defense Lattice Long Range Patrol",
                        "known force contact", Faction.TEAM_C, 2498.0, 2520.0, 120.0, 74),
                new CampaignSystem.CampaignSupportMarker(CampaignSystem.SupportMarkerType.FORCE_MINING,
                        "Yellow Mining Fleet Returning With Full Ore Holds",
                        "known force contact", Faction.TEAM_D, 2516.0, 2525.0, 120.0, 72)
        );

        BufferedImage frame = ScreenshotRegressionHarness.capture("campaign-map");
        assertTrue(frame.getWidth() >= 1280 && frame.getHeight() >= 720, "campaign-map screenshot should render");

        BufferedImage scratch = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scratch.createGraphics();
        List<Renderer.StrategicSupportLabelLayout> layouts;
        try {
            layouts = Renderer.strategicSupportMarkerLabelLayoutsForTest(g2, ctx,
                    new Rectangle(80, 64, 1120, 592), markers,
                    2100.0, 2100.0, 800.0, 800.0);
        } finally {
            g2.dispose();
        }

        assertTrue(layouts.size() == markers.size(), "every selected fleet marker should receive a visible label layout");
        for (int i = 0; i < layouts.size(); i++) {
            Rectangle a = layouts.get(i).bounds();
            assertTrue(a.width <= Math.max(88, 1120 / 6) + 8, "long labels should be compacted to map-safe width");
            for (int j = i + 1; j < layouts.size(); j++) {
                Rectangle padded = new Rectangle(a);
                padded.grow(1, 1);
                assertFalse(padded.intersects(layouts.get(j).bounds()),
                        "long fleet status labels should not overlap nearby map labels at default zoom");
            }
        }
    }

    @Test
    void campaignMapRendersAtStandardAndUltrawideWithoutBlanking() {
        BufferedImage standard = ScreenshotRegressionHarness.capture("campaign-map", 1280, 720);
        BufferedImage ultrawide = ScreenshotRegressionHarness.capture("campaign-map", 1920, 720);

        assertEquals(1280, standard.getWidth());
        assertEquals(720, standard.getHeight());
        assertEquals(1920, ultrawide.getWidth());
        assertEquals(720, ultrawide.getHeight());
        assertTrue(opaquePixels(standard) > 300_000, "standard campaign map should render substantial content");
        assertTrue(opaquePixels(ultrawide) > 450_000, "ultrawide campaign map should render substantial content");
    }

    @Test
    void lifecycleInvalidReportListsBrokenFleetWithReasonAndFix() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, ctx.campaign, 0.2);
        Object force = firstForceForFaction(ctx.campaign, Faction.ENEMY);
        String forceName = fieldString(force, "name");
        setEnumByName(force, "mission", "RAID");
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "AMBUSHING");
        setInt(force, "targetForceId", 0);
        setDouble(force, "intentTimerSec", 0.0);
        setDouble(force, "taskDeadlineSec", 0.0);
        ((List<?>) getObject(force, "routePoints")).clear();

        List<String> report = CampaignSystem.campaignFleetLifecycleInvalidReport(ctx);

        assertTrue(report.stream().anyMatch(line -> line.startsWith("INVALID: " + forceName)
                        && line.contains("reason=")
                        && line.contains("fix=")),
                "debug report should list invalid lifecycle failures with a reason and fix");
    }

    @Test
    void hubActionDetailShowsApproachUntilDockedThenShowsActionCost() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);

        String undocked = CampaignSystem.hubServiceActionDetail(ctx, selected, CampaignSystem.HubService.REPAIR);
        assertTrue(undocked.contains("APPROACH"));

        st.dockedGalaxyLocationId = selected.id;
        st.currentGalaxyLocationId = selected.id;
        String docked = CampaignSystem.hubServiceActionDetail(ctx, selected, CampaignSystem.HubService.REPAIR);
        assertFalse(docked.contains("APPROACH"));
        assertTrue(docked.startsWith("C "));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation locationContaining(GameContext ctx, String token) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.name.toUpperCase(java.util.Locale.US).contains(token)) {
                return location;
            }
        }
        throw new AssertionError("missing campaign location containing " + token);
    }

    private static void invokeForceSimulation(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        java.lang.reflect.Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static Object firstForceForFaction(CampaignSystem.CampaignState st, Faction faction) {
        for (Object force : st.campaignForces) {
            if (force != null && getObject(force, "faction") == faction) return force;
        }
        throw new AssertionError("missing force for faction " + faction);
    }

    private static String fieldString(Object target, String fieldName) {
        Object value = getObject(target, fieldName);
        return value == null ? "" : value.toString();
    }

    private static Object getObject(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumByName(Object target, String fieldName, String value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> enumType = field.getType();
        field.set(target, Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), value));
    }

    private static boolean shouldDrawObjectiveMarker(GameContext ctx,
                                                     CampaignSystem.CampaignObjectiveMarker marker) throws Exception {
        java.lang.reflect.Method method = Renderer.class.getDeclaredMethod(
                "shouldDrawObjectiveMarkerAtZoom",
                GameContext.class,
                CampaignSystem.CampaignObjectiveMarker.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, marker);
    }

    private static boolean isSelectedMapMarker(GameContext ctx, String label, double x, double y) throws Exception {
        java.lang.reflect.Method method = Renderer.class.getDeclaredMethod(
                "isSelectedMapMarker",
                GameContext.class,
                String.class,
                double.class,
                double.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, label, x, y);
    }

    private static int opaquePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > 12) count += 4;
            }
        }
        return count;
    }
}
