import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestPhaseTwoPressureAuditTest {

    @Test
    void routePressureRisesFromRedControlUnresolvedThreatsPlayerNoiseAndCampaignPhase() {
        GameContext baselineCtx = initializedCampaignContext();
        CampaignSystem.CampaignLocation earth = findLocation(baselineCtx, "poi-24");
        assertNotNull(earth);
        baselineCtx.campaign.selectedGalaxyLocationId = earth.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(baselineCtx));
        double baselineRisk = baselineCtx.campaign.galaxyTravel.interceptionRisk;

        GameContext pressuredCtx = initializedCampaignContext();
        applyLateRedPressure(pressuredCtx.campaign);
        CampaignSystem.CampaignLocation pressuredEarth = findLocation(pressuredCtx, "poi-24");
        assertNotNull(pressuredEarth);
        pressuredCtx.campaign.selectedGalaxyLocationId = pressuredEarth.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(pressuredCtx));

        assertTrue(pressuredCtx.campaign.galaxyTravel.interceptionRisk >= baselineRisk + 8.0,
                "Red control, ignored threats, player noise, and phase should materially raise Earth-route risk baseline="
                        + baselineRisk + " pressured=" + pressuredCtx.campaign.galaxyTravel.interceptionRisk
                        + " lines=" + CampaignSystem.selectedRouteAssessmentLines(pressuredCtx));
        assertTrue(CampaignSystem.selectedRouteAssessmentLines(pressuredCtx).stream()
                .anyMatch(line -> line.startsWith("Campaign Pressure: LATE")
                        && line.contains("Red ")
                        && line.contains("unresolved ")
                        && line.contains("noise ")));
    }

    @Test
    void alliedControlRelaysPatrolsAndCompletedOperationsReduceVisiblePressure() {
        GameContext pressuredCtx = initializedCampaignContext();
        applyLateRedPressure(pressuredCtx.campaign);
        softenForReliefComparison(pressuredCtx.campaign);
        CampaignSystem.CampaignLocation contested = findLocation(pressuredCtx, "poi-14");
        assertNotNull(contested);
        pressuredCtx.campaign.selectedGalaxyLocationId = contested.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(pressuredCtx));
        double pressuredRisk = pressuredCtx.campaign.galaxyTravel.interceptionRisk;

        GameContext supportedCtx = initializedCampaignContext();
        applyLateRedPressure(supportedCtx.campaign);
        softenForReliefComparison(supportedCtx.campaign);
        applyAlliedRelief(supportedCtx.campaign);
        CampaignSystem.CampaignLocation supportedContested = findLocation(supportedCtx, "poi-14");
        assertNotNull(supportedContested);
        supportedCtx.campaign.selectedGalaxyLocationId = supportedContested.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(supportedCtx));

        assertTrue(supportedCtx.campaign.galaxyTravel.interceptionRisk <= pressuredRisk - 5.0,
                "allied control, relays, patrols, and completed operations should reduce visible pressure pressured="
                        + pressuredRisk + " supported=" + supportedCtx.campaign.galaxyTravel.interceptionRisk
                        + " lines=" + CampaignSystem.selectedRouteAssessmentLines(supportedCtx));
        assertTrue(CampaignSystem.selectedRouteAssessmentLines(supportedCtx).stream()
                .anyMatch(line -> line.startsWith("Campaign Pressure:")
                        && line.contains("relief ")));
    }

    @Test
    void pressureBandsReachEarlyMidLateAndStandardEarthApproachIsDefended() {
        GameContext earlyCtx = initializedCampaignContext();
        CampaignSystem.CampaignLocation southern = findLocation(earlyCtx, "poi-02");
        assertNotNull(southern);
        earlyCtx.campaign.selectedGalaxyLocationId = southern.id;
        assertTrue(CampaignSystem.selectedRouteAssessmentLines(earlyCtx).stream()
                .anyMatch(line -> line.startsWith("Campaign Pressure: EARLY")));

        GameContext midCtx = initializedCampaignContext();
        midCtx.campaign.completedMainMissions = 10;
        midCtx.campaign.enemyAlertLevel = 45.0;
        midCtx.campaign.strategicExposureLevel = 35.0;
        CampaignSystem.CampaignLocation midTarget = findLocation(midCtx, "poi-16");
        assertNotNull(midTarget);
        midCtx.campaign.selectedGalaxyLocationId = midTarget.id;
        List<String> midLines = CampaignSystem.selectedRouteAssessmentLines(midCtx);
        assertTrue(midLines.stream()
                .anyMatch(line -> line.startsWith("Campaign Pressure: MID")), "mid lines=" + midLines);

        GameContext lateCtx = initializedCampaignContext();
        applyLateRedPressure(lateCtx.campaign);
        CampaignSystem.CampaignLocation earth = findLocation(lateCtx, "poi-24");
        assertNotNull(earth);
        lateCtx.campaign.selectedGalaxyLocationId = earth.id;
        assertTrue(CampaignSystem.selectedRouteAssessmentLines(lateCtx).stream()
                .anyMatch(line -> line.startsWith("Campaign Pressure: LATE")));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(lateCtx));
        assertTrue(lateCtx.campaign.galaxyTravel.interceptionRisk >= 55.0,
                "Standard Command Earth approach should carry meaningful escalation/resistance");
        assertTrue(CampaignSystem.campaignRumorBoardLines(lateCtx).stream()
                        .anyMatch(line -> line.startsWith("Contact Chain  |  "))
                || CampaignSystem.activeSupportMarkers(lateCtx).stream()
                        .anyMatch(marker -> marker.label.toUpperCase(java.util.Locale.US).contains("INTERDICTION")
                                || marker.subtitle.toUpperCase(java.util.Locale.US).contains("INTERDICTION")),
                "Earth approach should create or expose a player-readable interdiction/contact-chain consequence");
    }

    private static void applyLateRedPressure(CampaignSystem.CampaignState st) {
        st.completedMainMissions = 22;
        st.earthProgress = 0.72;
        st.enemyAlertLevel = 82.0;
        st.strategicExposureLevel = 76.0;
        st.recentStrikePressure = 64.0;
        for (CampaignSystem.CampaignLocation location : st.galaxyMainPois) {
            if (location == null) continue;
            if (location.missionIndex >= 7 || location.ownerFaction == Faction.ENEMY) {
                location.ownerFaction = Faction.ENEMY;
                location.controlState = CampaignSystem.CampaignControlVisualState.RED;
            }
            if (location.ownerFaction == Faction.ENEMY) {
                location.unresolvedAgeSec = Math.max(location.unresolvedAgeSec, 900.0);
                location.escalationStage = Math.max(location.escalationStage, 2);
            }
        }
        for (CampaignSystem.CampaignLocation location : st.galaxyAreasOfInterest) {
            if (location == null) continue;
            location.unresolvedAgeSec = Math.max(location.unresolvedAgeSec, 720.0);
            location.escalationStage = Math.max(location.escalationStage, 2);
            if (location.type == CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY) {
                location.ownerFaction = Faction.ENEMY;
                location.controlState = CampaignSystem.CampaignControlVisualState.RED;
            }
        }
    }

    private static void softenForReliefComparison(CampaignSystem.CampaignState st) {
        st.completedMainMissions = 8;
        st.earthProgress = 0.10;
        st.enemyAlertLevel = 34.0;
        st.strategicExposureLevel = 24.0;
        st.recentStrikePressure = 12.0;
        for (CampaignSystem.CampaignLocation location : st.galaxyMainPois) {
            if (location == null) continue;
            location.unresolvedAgeSec = Math.min(location.unresolvedAgeSec, 260.0);
            location.escalationStage = Math.min(location.escalationStage, 1);
        }
        for (CampaignSystem.CampaignLocation location : st.galaxyAreasOfInterest) {
            if (location == null) continue;
            location.unresolvedAgeSec = Math.min(location.unresolvedAgeSec, 260.0);
            location.escalationStage = Math.min(location.escalationStage, 1);
        }
    }

    private static void applyAlliedRelief(CampaignSystem.CampaignState st) {
        st.greenContractFavor = 8;
        st.yellowLiberationFavor = 6;
        for (CampaignSystem.CampaignLocation location : st.galaxyMainPois) {
            if (location == null || location.missionIndex >= 22) continue;
            location.ownerFaction = location.missionIndex <= 8 ? Faction.TEAM_C : Faction.BRIGHT_YELLOW;
            location.controlState = CampaignSystem.CampaignControlVisualState.GREEN;
            if (location.facilityType == CampaignSystem.CampaignFacilityType.RELAY
                    || location.facilityType == CampaignSystem.CampaignFacilityType.SENSOR_TOWER
                    || location.missionIndex <= 18) {
                location.completed = true;
                location.supportRouteStabilized = true;
            }
        }
        for (CampaignSystem.CampaignLocation location : st.galaxyAreasOfInterest) {
            if (location == null) continue;
            location.completed = true;
            location.supportRouteStabilized = true;
            location.ownerFaction = Faction.TEAM_C;
        }
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 72502L, false)
                .withExperience(ExperienceSettings.forPreset(ExperienceSettings.Preset.STANDARD)));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.state = GameState.FLEET;
        ctx.campaign.strategicOvermapMode = true;
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
}
