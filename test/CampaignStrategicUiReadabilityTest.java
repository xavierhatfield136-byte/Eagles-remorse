import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

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
    void lifecycleInvalidReportListsBrokenFleetWithReasonAndFix() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, ctx.campaign, 0.2);
        Object force = forceNamed(ctx.campaign, "Red Frontier Picket Patrol");
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

        assertTrue(report.stream().anyMatch(line -> line.startsWith("INVALID: Red Frontier Picket Patrol")
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

    private static Object forceNamed(CampaignSystem.CampaignState st, String name) {
        for (Object force : st.campaignForces) {
            if (force != null && name.equals(fieldString(force, "name"))) return force;
        }
        throw new AssertionError("missing force " + name);
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
}
