import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CampaignStrategicCommandHudTest {

    @Test
    void freeTravelCanBeSelectedAndStartedWithoutPoi() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(ctx, 900.0, 4200.0));
        assertTrue(CampaignSystem.hasSelectedFreeTravelTarget(ctx));
        assertNull(CampaignSystem.selectedCampaignLocation(ctx));

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(st.galaxyTravel.traveling);
        assertTrue(st.galaxyTravel.freeTravel);
        assertEquals("", st.galaxyTravel.destinationId);
        assertEquals("Free Course", st.galaxyTravel.destinationLabel);
    }

    @Test
    void transitTravelCanSurfaceDiscoveryContacts() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        int before = CampaignSystem.campaignAreasOfInterest(ctx).size();

        CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1400.0, 3200.0);
        CampaignSystem.startTravelToSelectedLocation(ctx);
        invokeTransitDiscovery(ctx, st);

        assertTrue(CampaignSystem.campaignAreasOfInterest(ctx).size() > before);
        assertNotNull(CampaignSystem.selectedCampaignLocation(ctx));
        assertTrue(CampaignSystem.selectedCampaignLocation(ctx).id.startsWith("transit-"));
    }

    @Test
    void commandHudSummariesExposeFleetResourcesAndStrikeControl() {
        GameContext ctx = initializedCampaignContext();
        List<String> fleet = CampaignSystem.campaignFleetManagerLines(ctx);
        List<String> fleetBoard = CampaignSystem.campaignFleetBoardSummaryLines(ctx);
        List<String> fleetRoster = CampaignSystem.campaignFleetRosterLines(ctx, 3);
        List<String> fleetCondition = CampaignSystem.campaignFleetConditionLines(ctx);
        List<String> fleetDetachments = CampaignSystem.campaignFleetDetachmentLines(ctx);
        List<String> resources = CampaignSystem.campaignResourceManagerLines(ctx);
        List<String> resourceTrend = CampaignSystem.campaignResourceTrendLines(ctx);
        List<String> resourceWarnings = CampaignSystem.campaignResourceWarningLines(ctx);
        List<String> strikes = CampaignSystem.campaignStrikeManagerLines(ctx);
        List<String> strikeReadiness = CampaignSystem.campaignStrikeReadinessLines(ctx);
        List<String> strikeConsequences = CampaignSystem.campaignStrikeConsequenceLines(ctx);
        List<String> navigation = CampaignSystem.campaignNavigationStationLines(ctx);
        List<String> receiver = CampaignSystem.campaignReceiverBoardLines(ctx);
        List<String> finder = CampaignSystem.campaignDirectionFinderLines(ctx);
        List<String> comms = CampaignSystem.campaignCommsBoardLines(ctx);

        assertTrue(fleet.stream().anyMatch(line -> line.startsWith("Command Hulls: ")));
        assertTrue(fleetBoard.stream().anyMatch(line -> line.startsWith("READY ")));
        assertEquals(3, fleetRoster.size());
        assertTrue(fleetCondition.stream().anyMatch(line -> line.startsWith("BATTLE READY ")));
        assertTrue(fleetCondition.stream().anyMatch(line -> line.startsWith("FLEET STRAIN ")));
        assertTrue(fleetDetachments.stream().anyMatch(line -> line.startsWith("FLAG GROUP")));
        assertTrue(resources.stream().anyMatch(line -> line.startsWith("Credits: ")));
        assertTrue(resources.stream().anyMatch(line -> line.startsWith("Fleet Strain: ")));
        assertTrue(resourceTrend.stream().anyMatch(line -> line.startsWith("Fuel State: ")));
        assertTrue(resourceWarnings.stream().anyMatch(line -> line.contains("ROUTE PREVIEW")));
        assertTrue(strikes.stream().anyMatch(line -> line.startsWith("Overmap Role: ")));
        assertTrue(strikeReadiness.stream().anyMatch(line -> line.startsWith("REMOTE STRIKES HELD")));
        assertTrue(strikeConsequences.stream().anyMatch(line -> line.startsWith("Exposure: ")));
        assertTrue(navigation.stream().anyMatch(line -> line.contains("Map use:")));
        assertTrue(navigation.stream().anyMatch(line -> line.startsWith("Reputation: ")));
        assertTrue(navigation.stream().anyMatch(line -> line.startsWith("Theater Shift: ")));
        assertTrue(receiver.stream().anyMatch(line -> line.startsWith("Band: ")));
        assertTrue(receiver.stream().anyMatch(line -> line.startsWith("Contact Pressure: ")));
        assertTrue(receiver.stream().anyMatch(line -> line.startsWith("Recommendation: ")));
        assertTrue(finder.stream().anyMatch(line -> line.startsWith("Bearing: ")));
        assertTrue(finder.stream().anyMatch(line -> line.startsWith("Route Trend: ")));
        assertTrue(finder.stream().anyMatch(line -> line.startsWith("Current Callout: ")));
        assertTrue(comms.stream().anyMatch(line -> line.startsWith("Green Channel Favor: ")));
        assertTrue(comms.stream().anyMatch(line -> line.startsWith("Contact Net: ")));
        assertTrue(comms.stream().anyMatch(line -> line.startsWith("Reputation: ")));
        assertTrue(comms.stream().anyMatch(line -> line.startsWith("Crew: ")));
        assertTrue(comms.stream().anyMatch(line -> line.startsWith("Lead  | ")));
        assertFalse(strikes.stream().anyMatch(line -> line.contains("Shift+LMB")));
    }

    @Test
    void afterActionReportConnectsBattleOutcomeToResourcesFleetAndTheater() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation hub = firstFriendlyServiceLocation(ctx);
        assertNotNull(hub);
        st.activeGalaxyEncounterLocationId = hub.id;
        st.objectiveLabel = "Escort the damaged convoy to the exit lane";
        st.objectivePhaseLabel = "PHASE: Jump drive charging";
        st.transitionSummaryTop = "Convoy survived under heavy fire.";
        st.transitionSummaryBottom = "Green repair crews opened a safer northern lane.";
        st.transitionRewardLine = "+supplies / +green favor";
        st.transitionRouteImpactLine = "route pressure reduced";
        st.lastTheaterOperationDebrief = "Red scouts redirected after losing the relay track.";
        st.campaignFuel = 37;
        st.campaignSupplies = 29;
        st.campaignAmmo = 41;
        st.campaignSalvage = 7;
        ctx.credits = 1234;
        ctx.player.cargo = 88;
        Object entry = st.persistentBlueFleet.get(0);
        setDoubleField(entry, "hullConditionFrac", 0.40);
        setDoubleField(entry, "shieldConditionFrac", 0.30);

        String report = String.join("\n", CampaignSystem.campaignAfterActionReportLines(ctx));

        assertTrue(report.contains("Battle Report: " + hub.name));
        assertTrue(report.contains("Objective: Escort the damaged convoy to the exit lane"));
        assertTrue(report.contains("Friendly Fleet: live"));
        assertTrue(report.contains("critical 1"));
        assertTrue(report.contains("Resources: credits 1234  ore 88  fuel 37  supplies 29  ammo 41  salvage 7"));
        assertTrue(report.contains("Reputation: "));
        assertTrue(report.contains("Intel: "));
        assertTrue(report.contains("Theater Pressure: "));
        assertTrue(report.contains("Follow-On: Red scouts redirected"));
    }

    @Test
    void campaignFleetRosterEntriesAreSortedSelectableAndScrollable() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.mapOpen = true;
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;

        List<CampaignSystem.CampaignFleetRosterEntry> entries = CampaignSystem.campaignFleetRosterEntries(ctx);
        assertTrue(entries.size() >= 3);
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i - 1).oreCost >= entries.get(i).oreCost,
                    "fleet roster should be ordered largest ore cost to smallest");
        }

        CampaignSystem.CampaignFleetRosterEntry target = entries.get(Math.min(2, entries.size() - 1));
        assertTrue(CampaignSystem.selectCampaignFleetRosterSlot(ctx, target.slotId));
        assertTrue(CampaignSystem.campaignFleetRosterEntries(ctx).stream()
                .anyMatch(entry -> entry.slotId == target.slotId && entry.selected));

        assertTrue(CampaignSystem.scrollCampaignFleetRoster(ctx, 1, 2));
        assertTrue(ctx.ui.campaignFleetRosterScroll > 0);
    }

    @Test
    void campaignFleetRosterCanOpenPersistentHullRefitFromOvermap() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.mapOpen = true;
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;

        CampaignSystem.CampaignFleetRosterEntry target = CampaignSystem.campaignFleetRosterEntries(ctx).stream()
                .filter(entry -> entry != null && entry.slotId > 0)
                .findFirst()
                .orElse(null);
        assertNotNull(target);

        assertTrue(CampaignSystem.selectCampaignFleetRosterSlot(ctx, target.slotId));
        assertTrue(CampaignSystem.openFocusedCampaignFleetEditor(ctx));

        assertTrue(ctx.ui.shopOpen);
        assertTrue(ctx.ui.fleetRefitMode);
        assertTrue(ctx.ui.fleetSelectedShipId > 0);
        assertTrue(ctx.ships.stream().anyMatch(ship ->
                ship != null
                        && ship.id == ctx.ui.fleetSelectedShipId
                        && ship.faction != null
                        && ship.faction.teamId() == Faction.ALLY.teamId()
                        && ship.alive
                        && !ship.dying
                        && ship.hp > 0));
    }

    @Test
    void tabSelectsFleetBoardDuringOpenSpaceTravelAndMapModeThenClosesCleanly() {
        GameContext openCtx = initializedCampaignContext();
        openCtx.campaign.introSequenceActive = false;
        openCtx.campaign.awaitingFleetHubChoice = false;
        openCtx.ui.shopOpen = false;
        openCtx.state = GameState.RUNNING;
        UISystem.toggleShop(openCtx);
        assertFalse(openCtx.ui.shopOpen);
        assertTrue(openCtx.ui.mapOpen);
        assertEquals(GameState.MAP, openCtx.state);
        assertEquals(UiState.CampaignCommandTab.FLEET, openCtx.ui.campaignCommandTab);
        UISystem.closeAllOverlays(openCtx);
        assertFalse(openCtx.ui.shopOpen);
        assertEquals(GameState.RUNNING, openCtx.state);

        GameContext travelCtx = initializedCampaignContext();
        travelCtx.campaign.introSequenceActive = false;
        travelCtx.campaign.awaitingFleetHubChoice = false;
        travelCtx.ui.shopOpen = false;
        travelCtx.state = GameState.RUNNING;
        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(travelCtx, 900.0, 4200.0));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(travelCtx));
        UISystem.toggleShop(travelCtx);
        assertFalse(travelCtx.ui.shopOpen);
        assertTrue(travelCtx.ui.mapOpen);
        assertEquals(GameState.MAP, travelCtx.state);
        assertEquals(UiState.CampaignCommandTab.FLEET, travelCtx.ui.campaignCommandTab);
        UISystem.closeAllOverlays(travelCtx);
        assertEquals(GameState.RUNNING, travelCtx.state);
        assertTrue(travelCtx.campaign.galaxyTravel.traveling, "closing the fleet tab should preserve campaign travel");

        GameContext mapCtx = initializedCampaignContext();
        mapCtx.ui.mapOpen = true;
        mapCtx.ui.shopOpen = false;
        mapCtx.state = GameState.MAP;
        mapCtx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
        UISystem.toggleShop(mapCtx);
        assertFalse(mapCtx.ui.shopOpen);
        assertTrue(mapCtx.ui.mapOpen);
        assertEquals(GameState.MAP, mapCtx.state);
        assertEquals(UiState.CampaignCommandTab.FLEET, mapCtx.ui.campaignCommandTab);
    }

    @Test
    void inWorldFleetManagementOpensWhileDockedWithoutShopOrBaseOverlayConflict() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation hub = firstFriendlyServiceLocation(ctx);
        assertNotNull(hub, "expected a friendly service hub");
        ctx.campaign.selectedGalaxyLocationId = hub.id;
        ctx.campaign.dockedGalaxyLocationId = hub.id;
        ctx.ui.shopOpen = true;
        ctx.ui.baseMenuOpen = true;

        UISystem.toggleBaseMenu(ctx);

        assertTrue(ctx.ui.mapOpen);
        assertFalse(ctx.ui.shopOpen);
        assertFalse(ctx.ui.baseMenuOpen);
        assertEquals(GameState.MAP, ctx.state);
        assertEquals(UiState.CampaignCommandTab.FLEET, ctx.ui.campaignCommandTab);
        UISystem.closeAllOverlays(ctx);
        assertEquals(GameState.RUNNING, ctx.state);
        assertEquals(hub.id, ctx.campaign.dockedGalaxyLocationId, "closing should preserve the docked campaign state");
    }

    @Test
    void sectorSpaceBaseHotkeyOpensCommandShipUpgradeConsole() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.sectorElapsed = 1.0;
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = false;
        ctx.ui.mapOpen = false;
        ctx.state = GameState.RUNNING;

        UISystem.toggleBaseMenu(ctx);

        assertFalse(ctx.ui.shopOpen);
        assertTrue(ctx.ui.baseMenuOpen);
        assertFalse(ctx.ui.mapOpen);
        assertEquals(GameState.BASE_MENU, ctx.state);
    }

    @Test
    void sectorMapDetectionRangeHidesDistantMinorMarkersButKeepsLargeLandmarks() {
        GameContext ctx = initializedCampaignContext();
        List<CampaignSystem.CampaignSupportMarker> nearbySupports = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker support = nearbySupports.stream()
                .filter(marker -> marker != null)
                .findFirst()
                .orElse(null);
        assertNotNull(support, "expected at least one live support marker in the overmap");

        ctx.campaign.playerGalaxyX = 5000.0;
        ctx.campaign.playerGalaxyY = 5000.0;
        List<CampaignSystem.CampaignSupportMarker> distantSupports = CampaignSystem.activeSupportMarkers(ctx);

        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.sectorElapsed = 1.0;
        ctx.campaign.playerGalaxyX = 0.0;
        ctx.campaign.playerGalaxyY = 0.0;
        ctx.campaign.landmarks.clear();

        ctx.campaign.landmarks.add(new CampaignSystem.CampaignLandmark(
                CampaignSystem.LandmarkType.RELAY,
                "Far Relay",
                "Minor lane relay",
                20000.0,
                0.0,
                120.0,
                null,
                null,
                false));
        ctx.campaign.landmarks.add(new CampaignSystem.CampaignLandmark(
                CampaignSystem.LandmarkType.COLONY,
                "Far Colony",
                "Large settled landmark",
                20000.0,
                80.0,
                280.0,
                null,
                null,
                false));

        List<CampaignSystem.CampaignLandmark> landmarks = CampaignSystem.strategicLandmarks(ctx);

        assertTrue(distantSupports.stream().noneMatch(marker ->
                marker != null
                        && marker.label.equals(support.label)
                        && Math.abs(marker.x - support.x) < 1.0
                        && Math.abs(marker.y - support.y) < 1.0));
        assertTrue(landmarks.stream().anyMatch(marker -> "Far Colony".equals(marker.label)));
        assertTrue(landmarks.stream().noneMatch(marker -> "Far Relay".equals(marker.label)));
    }

    @Test
    void inWorldFleetRosterExposesKeyHintsCargoForceReadinessGroupsAndCommitment() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
        List<String> fleet = CampaignSystem.campaignFleetManagerLines(ctx);
        List<String> roster = CampaignSystem.campaignFleetRosterLines(ctx, 2);
        List<String> archive = CampaignSystem.campaignFleetArchiveLines(ctx, 2);
        List<CampaignSystem.CampaignFleetRosterEntry> entries = CampaignSystem.campaignFleetRosterEntries(ctx);
        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);

        assertTrue(fleet.stream().anyMatch(line -> line.contains("TAB opens persistent fleet management")));
        assertFalse(roster.isEmpty());
        assertTrue(roster.stream().anyMatch(line -> line.contains("H ") && line.contains(" S ")));
        assertTrue(roster.stream().anyMatch(line -> line.contains("CARGO ")));
        assertTrue(roster.stream().anyMatch(line -> line.contains("FORCE ")));
        assertTrue(roster.stream().anyMatch(line -> line.contains("FLAG") || line.contains("DET ")));
        assertFalse(archive.isEmpty());
        assertTrue(archive.stream().anyMatch(line -> line.startsWith("SERVICE")));
        assertTrue(entries.stream().allMatch(entry -> entry.identityLabel.contains("counters")
                && entry.identityLabel.contains("weak to")));
        assertTrue(entries.stream().allMatch(entry -> entry.configurationLabel.contains("MAINT ")
                && entry.configurationLabel.contains("variant")
                && entry.configurationLabel.contains("combat zoom")));
        assertTrue(entries.stream().allMatch(entry -> entry.personnelLabel.contains("MORALE")));
        assertTrue(entries.stream().anyMatch(entry -> entry.readinessLabel.startsWith("READY")
                || entry.readinessLabel.startsWith("STRAINED") || entry.readinessLabel.startsWith("UNREADY")));
        assertTrue(actions.stream().anyMatch(action -> "FLEET_COMMIT_NOW".equals(action.id)));
        assertTrue(actions.stream().anyMatch(action -> "FLEET_ASSIGN_FLAG".equals(action.id)));
    }

    @Test
    void fleetTabExposesPreBattleFormationSelectionAndDeploymentPreview() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
        ctx.command.alliedFleetFormation = GameContext.FleetFormation.WEDGE;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        CampaignSystem.CampaignAction formation = actions.stream()
                .filter(action -> "PRE_BATTLE_FORMATION".equals(action.id))
                .findFirst()
                .orElse(null);
        assertNotNull(formation);
        assertTrue(formation.enabled);
        assertTrue(formation.shortDescription.contains("WEDGE"));

        assertTrue(formation.execute.execute(ctx));
        assertEquals(GameContext.FleetFormation.LINE, ctx.command.alliedFleetFormation);

        List<String> preview = CampaignSystem.tacticalDeploymentPreviewLines(ctx);
        assertTrue(preview.stream().anyMatch(line -> line.startsWith("Pre-Battle Formation: LINE")));
        assertTrue(preview.stream().anyMatch(line -> line.startsWith("Deployment Preview: Flagship left entry")));
        assertTrue(CampaignSystem.campaignFleetManagerLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Deployment Zones: allies left lane")));
    }

    @Test
    void tacticalObjectiveMarkersIncludeDeploymentPreviewForOpenSpaceContact() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicOvermapMode = false;
        st.galaxyEncounterActive = true;
        st.galaxyAmbientEncounterActive = false;
        ctx.command.alliedFleetFormation = GameContext.FleetFormation.SCREEN;

        List<CampaignSystem.CampaignObjectiveMarker> markers = CampaignSystem.activeObjectiveMarkers(ctx);
        assertTrue(markers.stream().anyMatch(marker -> marker.label.equals("Deployment Preview: SCREEN")
                && marker.subtitle.contains("escort coverage")));
    }

    @Test
    void shipyardPreviewAndStrategicAuthorityExposeLiveFleetBuildingContext() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation shipyard = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location != null && location.services.contains(CampaignSystem.HubService.SHIPYARD))
                .findFirst()
                .orElse(null);
        assertNotNull(shipyard);

        List<String> shipyardLines = CampaignSystem.hubServicePreviewLines(ctx, shipyard, CampaignSystem.HubService.SHIPYARD);
        assertTrue(shipyardLines.stream().anyMatch(line -> line.startsWith("Role: ")
                && line.contains("Counter: ") && line.contains("Weakness: ")));
        assertTrue(shipyardLines.stream().anyMatch(line -> line.startsWith("Maintenance: ") && line.contains("Variant: ")));
        assertTrue(shipyardLines.stream().anyMatch(line -> line.startsWith("Silhouette: ") && line.contains("combat zoom")));

        List<String> authority = CampaignSystem.campaignStrategicAuthorityLines(ctx);
        assertTrue(authority.stream().anyMatch(line -> line.startsWith("LIVE AUTHORITY  |  Nodes ")));
        assertTrue(authority.stream().anyMatch(line -> line.startsWith("TASK GROUPS  |  Friendly ")));
        assertTrue(authority.stream().anyMatch(line -> line.startsWith("WAR TIMELINE  |  Battles ")));
        assertTrue(authority.stream().anyMatch(line -> line.startsWith("DIRECTORS  |  ")));
        assertTrue(CampaignSystem.campaignStrategicExpansionLines(ctx).stream()
                .anyMatch(line -> line.startsWith("LIVE AUTHORITY  |  Nodes ")));
    }

    @Test
    void selectedLocationSidebarSurfacesWhyActionAndRisk() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation selected = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location != null)
                .findFirst()
                .orElse(null);
        assertNotNull(selected);

        ctx.campaign.selectedGalaxyLocationId = selected.id;
        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Why It Matters: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Gain: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("If Ignored: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Action Window: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Risk: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Primary Recommendation: ")));
    }

    @Test
    void actionRegistryExposesVisibleButtonsAndDisabledReasons() {
        GameContext ctx = initializedCampaignContext();
        List<CampaignSystem.CampaignAction> navActions = CampaignSystem.campaignVisibleActions(ctx);
        List<String> navIds = navActions.stream().map(action -> action.id).collect(Collectors.toList());
        assertTrue(navIds.contains("ENGAGE_COURSE"));
        assertTrue(navIds.contains("SET_WAYPOINT"));
        assertTrue(navIds.contains("SIGNAL_SWEEP"));
        CampaignSystem.CampaignAction engage = navActions.stream().filter(action -> "ENGAGE_COURSE".equals(action.id)).findFirst().orElse(null);
        assertNotNull(engage);
        assertTrue(engage.enabled || engage.disabledReason.contains("no course") || engage.disabledReason.contains("already engaged"));

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;
        List<CampaignSystem.CampaignAction> strikeActions = CampaignSystem.campaignVisibleActions(ctx);
        assertFalse(strikeActions.stream().anyMatch(action -> "TORPEDO_STRIKE".equals(action.id)));
        assertFalse(strikeActions.stream().anyMatch(action -> "CARRIER_SORTIE".equals(action.id)));
        assertFalse(strikeActions.stream().anyMatch(action -> "ATOMIC_STRIKE".equals(action.id)));
        assertTrue(strikeActions.stream().anyMatch(action -> "TRACK_TARGET".equals(action.id)));
        CampaignSystem.CampaignAction engageCourse = navActions.stream().filter(action -> "ENGAGE_COURSE".equals(action.id)).findFirst().orElse(null);
        assertNotNull(engageCourse);
        assertNotNull(engageCourse.disabledReason);
    }

    @Test
    void fleetTabShowsDirectPostureButtonsWithActiveLatchState() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        List<String> postureIds = actions.stream()
                .map(action -> action.id)
                .filter(id -> id.startsWith("POSTURE_"))
                .collect(Collectors.toList());

        assertTrue(postureIds.contains("POSTURE_SILENT_RUNNING"));
        assertTrue(postureIds.contains("POSTURE_COMBAT_PATROL"));
        assertTrue(postureIds.contains("POSTURE_RESCUE_PRIORITY"));
        assertTrue(postureIds.contains("POSTURE_RAIDER_DOCTRINE"));
        assertTrue(postureIds.contains("POSTURE_LOGISTICS_CONSERVATION"));
        assertTrue(postureIds.contains("POSTURE_RECON_SWEEP"));

        CampaignSystem.CampaignAction active = actions.stream()
                .filter(action -> "POSTURE_SILENT_RUNNING".equals(action.id))
                .findFirst()
                .orElse(null);
        assertNotNull(active);
        assertFalse(active.enabled);
        assertEquals(CampaignSystem.CampaignActionState.RECOMMENDED, active.state);
        assertTrue(active.disabledReason.toLowerCase().contains("already active"));
    }

    @Test
    void dockedHubShowsDirectServiceButtonsFromActionRegistry() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation repair = java.util.stream.Stream.concat(
                        CampaignSystem.mainCampaignLocations(ctx).stream(),
                        CampaignSystem.campaignAreasOfInterest(ctx).stream())
                .filter(location -> location != null && !location.services.isEmpty())
                .findFirst()
                .orElse(null);
        assertNotNull(repair);

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
        st.playerGalaxyX = repair.x;
        st.playerGalaxyY = repair.y;
        st.selectedGalaxyLocationId = repair.id;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        assertTrue(actions.stream().anyMatch(action -> action.id.startsWith("HUB_")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("HUB_" + repair.services.get(0).name())));
    }

    @Test
    void enterableSitesExposeSeparateResolutionButtons() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;

        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("SITE_EVAC_SURVIVORS")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("SITE_TOW_DAMAGED_HULL")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("SITE_STRIP_FOR_PARTS")));
    }

    @Test
    void atomicStrikeConfirmationIsHiddenOnOvermap() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicOvermapMode = true;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 180.0);
        setDouble(group, "y", st.playerGalaxyY + 120.0);
        setBoolean(group, "visible", true);
        setBoolean(group, "identified", true);
        setObject(group, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TARGET_QUALITY"));
        CampaignSystem.CampaignSupportMarker marker = CampaignSystem.nearestSupportMarker(ctx, st.playerGalaxyX + 180.0, st.playerGalaxyY + 120.0, 240.0);
        assertNotNull(marker);
        String intel = marker.subtitle.substring(0, marker.subtitle.indexOf('|')).trim();
        CampaignSystem.selectCampaignContactTarget(ctx, marker.label, marker.subtitle, intel, marker.x, marker.y, true, true);
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;
        st.selectedGalaxyLocationId = "";

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        assertFalse(actions.stream().anyMatch(action -> "ATOMIC_STRIKE".equals(action.id)));
        assertFalse(CampaignSystem.executeCampaignAction(ctx, "ATOMIC_STRIKE"));
        assertFalse(ctx.ui.campaignActionConfirm.active);
    }

    @Test
    void localSiteEncounterLaunchesAndReturnsWithAreaOutcome() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(resource);

        st.playerGalaxyX = resource.x;
        st.playerGalaxyY = resource.y;
        st.selectedGalaxyLocationId = resource.id;
        int oreBefore = CampaignSystem.currentCampaignOre(ctx);

        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(st.galaxyEncounterActive);
        assertTrue(getBooleanField(st, "galaxyAmbientEncounterActive"));
        assertTrue(st.objectiveSecured);

        assertTrue(CampaignSystem.completeMissionExtraction(ctx));
        assertFalse(st.galaxyEncounterActive);
        assertFalse(getBooleanField(st, "galaxyAmbientEncounterActive"));
        assertTrue(resource.consumed);
        assertTrue(CampaignSystem.currentCampaignOre(ctx) > oreBefore);
        assertTrue(CampaignSystem.transitionSummaryTop(ctx).contains("ORE RECOVERED"));
        assertTrue(CampaignSystem.transitionSummaryBottom(ctx).contains("Route detour paid off"));
    }

    @Test
    void ambientLocalSitesExposeTacticalMarkersForImportantContacts() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(resource);

        st.playerGalaxyX = resource.x;
        st.playerGalaxyY = resource.y;
        st.selectedGalaxyLocationId = resource.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        int expectedSubzone = CampaignSystem.missionSubzoneIndex(
                CampaignSystem.missionSubzoneColumns() / 2,
                CampaignSystem.missionSubzoneRows() / 2
        );

        List<CampaignSystem.CampaignObjectiveMarker> objectives = CampaignSystem.activeObjectiveMarkers(ctx);
        List<CampaignSystem.CampaignSupportMarker> supports = CampaignSystem.activeSupportMarkers(ctx);

        assertEquals(expectedSubzone, CampaignSystem.currentLoadedMissionSubzone(ctx));
        assertTrue(objectives.stream().anyMatch(marker -> marker.label.contains(resource.name)));
        assertTrue(supports.stream().anyMatch(marker -> marker.type == CampaignSystem.SupportMarkerType.RESOURCE));
        assertTrue(supports.stream().anyMatch(marker -> marker.type == CampaignSystem.SupportMarkerType.FACTION_CONTACT));
    }

    @Test
    void distressSiteRecoveredShipsPersistAfterExtraction() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;
        int fleetBefore = st.persistentBlueFleet.size();

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        Ship support = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.name != null
                        && (ship.name.contains("Distress") || ship.name.contains("Relief") || ship.name.contains("Lost")))
                .findFirst()
                .orElse(null);
        assertNotNull(support);
        CampaignSystem.noteAmbientSupportRequest(ctx, support);
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(st.persistentBlueFleet.size() > fleetBefore);
        assertTrue(CampaignSystem.transitionSummaryTop(ctx).contains("SHIP"));
        assertTrue(CampaignSystem.transitionSummaryBottom(ctx).contains("reactivated into your fleet"));
        List<String> distressLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(distressLines.stream().anyMatch(line -> line.contains("Rescue trace remains")));
    }

    @Test
    void storyEventSiteImprovesIntelAndFavorOnExtraction() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation story = firstLocationOfType(ctx, "STORY_EVENT");
        assertNotNull(story);

        st.playerGalaxyX = story.x;
        st.playerGalaxyY = story.y;
        st.selectedGalaxyLocationId = story.id;
        double intelBefore = st.campaignIntelLevel;
        int greenFavorBefore = st.greenContractFavor;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(story.consumed);
        assertTrue(st.campaignIntelLevel > intelBefore);
        assertTrue(st.greenContractFavor > greenFavorBefore);
    }

    @Test
    void hiddenCacheSiteNowAddsSalvageAlongsideStrikeStores() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation cache = firstLocationOfType(ctx, "HIDDEN_CACHE");
        assertNotNull(cache);

        st.playerGalaxyX = cache.x;
        st.playerGalaxyY = cache.y;
        st.selectedGalaxyLocationId = cache.id;
        st.strategicTorpedoCharges = Math.max(0, st.strategicTorpedoCharges - 1);
        int torpedoesBefore = st.strategicTorpedoCharges;
        int suppliesBefore = st.campaignSupplies;
        int salvageBefore = st.campaignSalvage;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(cache.consumed);
        assertTrue(st.strategicTorpedoCharges > torpedoesBefore);
        assertTrue(st.campaignSupplies > suppliesBefore);
        assertTrue(st.campaignSalvage > salvageBefore);
    }

    @Test
    void yellowStorySiteUsesDistinctAmbientTraffic() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(ctx.ships.stream().anyMatch(ship -> ship != null && ship.name != null && ship.name.contains("Lost Liner")));
        assertTrue(ctx.ships.stream().anyMatch(ship -> ship != null && ship.name != null && ship.name.contains("Relief Escort")));
    }

    @Test
    void sensorSweepMarksNearbySitesAndHostiles() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation cache = firstLocationOfType(ctx, "HIDDEN_CACHE");
        assertNotNull(cache);

        st.playerGalaxyX = cache.x;
        st.playerGalaxyY = cache.y;
        st.campaignSupplies = 12;
        st.campaignIntelLevel = 64.0;
        cache.discovered = false;
        st.selectedGalaxyLocationId = cache.id;

        Object group = firstSearchGroup(st);
        setDoubleField(group, "x", cache.x + 180.0);
        setDoubleField(group, "y", cache.y + 120.0);
        setBooleanField(group, "visible", false);
        setBooleanField(group, "identified", false);

        int pingBefore = ctx.ui.mapPings.size();
        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));

        assertTrue(cache.discovered);
        assertEquals("Target-Quality", CampaignSystem.selectedLocationIntelReadout(ctx));
        assertTrue(getBooleanField(group, "visible"));
        assertTrue(getBooleanField(group, "identified"));
        assertEquals("TARGET_QUALITY", getObject(group, "intelQuality").toString());
        assertTrue(ctx.ui.mapPings.size() > pingBefore);
    }

    @Test
    void missionBoardObjectiveTextStaysConcise() throws Exception {
        Object[] scripts = loadSectorScripts();
        for (Object script : scripts) {
            if (script == null) continue;
            String objective = (String) readField(script, "objectiveLabel");
            assertNotNull(objective);
            assertTrue(objective.length() <= 44, "Objective too long: " + objective);
        }

        CampaignSystem.CampaignState st = initializedCampaignContext().campaign;
        Method phase = CampaignSystem.class.getDeclaredMethod("initialPhaseLabel", CampaignSystem.CampaignState.class);
        Method threat = CampaignSystem.class.getDeclaredMethod("initialThreatLabel", CampaignSystem.CampaignState.class);
        phase.setAccessible(true);
        threat.setAccessible(true);
        for (int sector = 1; sector <= 24; sector++) {
            st.sector = sector;
            String phaseLabel = (String) phase.invoke(null, st);
            String threatLabel = (String) threat.invoke(null, st);
            assertTrue(phaseLabel.length() <= 52, "Phase too long: " + phaseLabel);
            assertTrue(threatLabel.length() <= 52, "Threat too long: " + threatLabel);
        }
    }

    @Test
    void regionalIdentityChangesTravelReadoutAndTransitContacts() throws Exception {
        GameContext southCtx = initializedCampaignContext();
        CampaignSystem.CampaignState south = southCtx.campaign;
        CampaignSystem.CampaignLocation southLoc = firstLocationOfType(southCtx, "RESOURCE_ZONE");
        assertNotNull(southLoc);
        south.playerGalaxyX = southLoc.x;
        south.playerGalaxyY = southLoc.y;
        south.selectedGalaxyLocationId = southLoc.id;

        List<String> southLines = CampaignSystem.selectedLocationSidebarLines(southCtx);
        assertTrue(southLines.stream().anyMatch(line -> line.contains("friendlier hubs, prospectors, lighter patrols")));

        CampaignSystem.selectCampaignFreeTravelTarget(southCtx, southLoc.x + 400.0, southLoc.y - 120.0);
        CampaignSystem.startTravelToSelectedLocation(southCtx);
        invokeTransitDiscovery(southCtx, south);
        assertTrue(CampaignSystem.selectedCampaignLocation(southCtx).name.startsWith("Shelter Ore Bloom"));

        GameContext northCtx = initializedCampaignContext();
        CampaignSystem.CampaignState north = northCtx.campaign;
        CampaignSystem.CampaignLocation northLoc = lastLocationOfType(northCtx, "DISTRESS_SIGNAL");
        assertNotNull(northLoc);
        north.playerGalaxyX = northLoc.x;
        north.playerGalaxyY = northLoc.y;
        north.selectedGalaxyLocationId = northLoc.id;

        List<String> northLines = CampaignSystem.selectedLocationSidebarLines(northCtx);
        assertTrue(northLines.stream().anyMatch(line -> line.contains("tight supplies, hard hunts, resistance flashes")));

        CampaignSystem.selectCampaignFreeTravelTarget(northCtx, northLoc.x + 420.0, northLoc.y - 150.0);
        CampaignSystem.startTravelToSelectedLocation(northCtx);
        invokeTransitDiscovery(northCtx, north);
        assertTrue(CampaignSystem.selectedCampaignLocation(northCtx).name.startsWith("Resistance Flash"));
    }

    @Test
    void ambientSitesGainRegionSpecificTraffic() {
        GameContext southCtx = initializedCampaignContext();
        CampaignSystem.CampaignState south = southCtx.campaign;
        CampaignSystem.CampaignLocation southResource = firstLocationOfType(southCtx, "RESOURCE_ZONE");
        assertNotNull(southResource);
        south.playerGalaxyX = southResource.x;
        south.playerGalaxyY = southResource.y;
        south.selectedGalaxyLocationId = southResource.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(southCtx));
        assertTrue(southCtx.ships.stream().anyMatch(ship -> ship != null && "Shelter Ore Tender".equals(ship.name)));

        GameContext northCtx = initializedCampaignContext();
        CampaignSystem.CampaignState north = northCtx.campaign;
        CampaignSystem.CampaignLocation northDistress = lastLocationOfType(northCtx, "DISTRESS_SIGNAL");
        assertNotNull(northDistress);
        north.playerGalaxyX = northDistress.x;
        north.playerGalaxyY = northDistress.y;
        north.selectedGalaxyLocationId = northDistress.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(northCtx));
        assertTrue(northCtx.ships.stream().anyMatch(ship -> ship != null && "Resistance Lift Escort".equals(ship.name)));
    }

    @Test
    void consumedSitesExposeMemoryThroughSupportMarkers() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation cache = firstLocationOfType(ctx, "HIDDEN_CACHE");
        assertNotNull(cache);

        st.playerGalaxyX = cache.x;
        st.playerGalaxyY = cache.y;
        st.selectedGalaxyLocationId = cache.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker cacheMarker = markers.stream()
                .filter(marker -> marker != null && cache.name.equals(marker.label))
                .findFirst()
                .orElse(null);
        assertNotNull(cacheMarker);
        assertTrue(cacheMarker.subtitle.contains("hidden stores recovered"));
        assertTrue(CampaignSystem.selectedLocationSidebarLines(ctx).stream().anyMatch(line -> line.contains("Cache opened and ballast cleared")));
    }

    @Test
    void uncertainContactsSharpenAndDoctrineAppearsInReadouts() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        setDouble(group, "x", st.playerGalaxyX + 260.0);
        setDouble(group, "y", st.playerGalaxyY + 40.0);
        setBoolean(group, "visible", true);
        setBoolean(group, "identified", false);
        setObject(group, "contactConfidence", enumConstant(fieldType(group, "contactConfidence"), "POSSIBLE_PATROL"));
        setObject(group, "doctrine", enumConstant(fieldType(group, "doctrine"), "INTERDICTION_GROUP"));

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker hostile = markers.stream()
                .filter(marker -> marker != null
                        && marker.type == CampaignSystem.SupportMarkerType.HAZARD
                        && (marker.subtitle.contains("Interdiction Group")
                        || marker.subtitle.contains("Spoiler Screen")))
                .findFirst()
                .orElse(null);
        assertNotNull(hostile);
        assertTrue(hostile.label.toLowerCase().contains("distress burst")
                || hostile.label.toLowerCase().contains("metallic debris"));
        assertTrue(hostile.subtitle.contains("Interdiction Group")
                || hostile.subtitle.contains("Spoiler Screen"));

        st.campaignIntelLevel = 70.0;
        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));
        assertEquals("IDENTIFIED_TASK_FORCE", getObject(group, "contactConfidence").toString());
    }

    @Test
    void reputationPressureAndAfterActionBoardsExposeReactiveState() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.greenContractFavor = 8;
        st.campaignIntelLevel = 62.0;
        st.transitionSummaryTop = "DISTRESS SITE CLEAR  |  SHIP RECOVERED";
        st.transitionSummaryBottom = "Recovered escort reactivated into your fleet.";
        st.transitionRewardLine = "yellow favor / escort";
        st.transitionRouteImpactLine = "rescue route stabilized";

        assertEquals("Reliable Rescue Force", CampaignSystem.campaignReputationReadout(ctx));
        assertFalse(CampaignSystem.theaterPressureReadout(ctx).isBlank());
        assertTrue(CampaignSystem.campaignCrewCommentaryLines(ctx).size() >= 2);
        assertTrue(CampaignSystem.campaignAfterActionPlateLines(ctx).stream().anyMatch(line -> line.contains("REPUTATION")));
        assertTrue(CampaignSystem.campaignAfterActionPlateLines(ctx).stream().anyMatch(line -> line.contains("REWARD")));
        assertTrue(CampaignSystem.campaignAfterActionPlateLines(ctx).stream().anyMatch(line -> line.contains("ROUTE")));
    }

    @Test
    void highExposureEscalatesSearchGroupsIntoPunishmentDoctrine() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroupWithMinTier(st, 3);
        assertNotNull(group);

        st.strategicExposureLevel = 84.0;
        st.enemyAlertLevel = 82.0;
        setDouble(group, "x", st.playerGalaxyX + 120.0);
        setDouble(group, "y", st.playerGalaxyY + 60.0);

        invokeGalaxySearchUpdate(ctx, st, 1.0);
        assertEquals("PUNISHMENT_FLEET", getObject(group, "doctrine").toString());
    }

    @Test
    void recurringContactsAndRouteScarsShowUpAfterSiteResolution() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Known Contact: Captain Nadi Voss")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Route State: ")));
    }

    @Test
    void recurringTransitDiscoveriesCanReappearWithNamedContacts() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1400.0, 3200.0);
        CampaignSystem.startTravelToSelectedLocation(ctx);

        boolean sawNamedContact = false;
        for (int i = 0; i < 5; i++) {
            invokeTransitDiscovery(ctx, st);
            CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
            if (selected != null && selected.name.contains("/")) {
                sawNamedContact = true;
                break;
            }
        }
        assertTrue(sawNamedContact);
    }

    @Test
    void hostileOvermapContactSelectionFeedsStrikeConsole() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 180.0);
        setDouble(group, "y", st.playerGalaxyY + 120.0);
        setBoolean(group, "visible", true);
        setBoolean(group, "identified", true);
        setObject(group, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TARGET_QUALITY"));

        CampaignSystem.CampaignSupportMarker marker = CampaignSystem.nearestSupportMarker(ctx, st.playerGalaxyX + 180.0, st.playerGalaxyY + 120.0, 240.0);
        assertNotNull(marker);
        assertEquals(CampaignSystem.SupportMarkerType.HAZARD, marker.type);
        String intel = marker.subtitle.substring(0, marker.subtitle.indexOf('|')).trim();
        CampaignSystem.selectCampaignContactTarget(ctx, marker.label, marker.subtitle, intel, marker.x, marker.y, true, true);
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;
        st.selectedGalaxyLocationId = "";

        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.contains("HOSTILE CONTACT")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("If Ignored: ")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Direct engage")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("Torpedo / atomic")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("Sortie / engage")));
        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        assertFalse(actions.stream().anyMatch(action -> "TORPEDO_STRIKE".equals(action.id)));
        CampaignSystem.CampaignAction engage = actions.stream().filter(action -> "ENGAGE_CONTACT".equals(action.id)).findFirst().orElse(null);
        assertNotNull(engage);
        assertTrue(engage.enabled);
    }

    @Test
    void hostileSearchGroupContactCanBeDirectlyEngagedFromStrikeConsole() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 140.0);
        setDouble(group, "y", st.playerGalaxyY + 90.0);
        setBoolean(group, "visible", true);
        setObject(group, "contactConfidence", enumConstant(fieldType(group, "contactConfidence"), "CONFIRMED_HOSTILE"));
        setObject(group, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TRACKED"));

        CampaignSystem.CampaignSupportMarker marker = CampaignSystem.nearestSupportMarker(ctx, st.playerGalaxyX + 140.0, st.playerGalaxyY + 90.0, 220.0);
        assertNotNull(marker);
        String intel = marker.subtitle.substring(0, marker.subtitle.indexOf('|')).trim();
        CampaignSystem.selectCampaignContactTarget(ctx, marker.label, marker.subtitle, intel, marker.x, marker.y, true, true);
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;
        st.selectedGalaxyLocationId = "";

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        CampaignSystem.CampaignAction engage = actions.stream().filter(action -> "ENGAGE_CONTACT".equals(action.id)).findFirst().orElse(null);
        assertNotNull(engage);
        assertTrue(engage.enabled);
        assertTrue(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
    }

    @Test
    void hostileSearchGroupCanBeHitByStrategicSortie() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 160.0);
        setDouble(group, "y", st.playerGalaxyY + 100.0);
        setBoolean(group, "visible", true);
        setObject(group, "contactConfidence", enumConstant(fieldType(group, "contactConfidence"), "IDENTIFIED_TASK_FORCE"));
        setObject(group, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TRACKED"));
        setBoolean(group, "identified", true);

        CampaignSystem.CampaignSupportMarker marker = CampaignSystem.nearestSupportMarker(ctx, st.playerGalaxyX + 160.0, st.playerGalaxyY + 100.0, 220.0);
        assertNotNull(marker);
        String intel = marker.subtitle.substring(0, marker.subtitle.indexOf('|')).trim();
        CampaignSystem.selectCampaignContactTarget(ctx, marker.label, marker.subtitle, intel, marker.x, marker.y, true, true);

        int ammoBefore = st.campaignAmmo;
        int fuelBefore = st.campaignFuel;
        int suppliesBefore = st.campaignSupplies;
        assertTrue(CampaignSystem.launchSelectedCampaignSortie(ctx));

        assertTrue(st.campaignAmmo < ammoBefore);
        assertTrue(st.campaignFuel < fuelBefore);
        assertTrue(st.campaignSupplies < suppliesBefore);
        assertTrue(List.of("RETURNING", "INVESTIGATING").contains(getObject(group, "behavior").toString()));
    }

    @Test
    void siteResolutionModeCanBeCycledBeforeEntryAndAffectsOutcome() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;

        assertEquals("Evacuate Survivors", CampaignSystem.selectedSiteResolutionModeReadout(ctx));
        assertTrue(CampaignSystem.cycleSelectedSiteResolutionMode(ctx));
        assertEquals("Tow Damaged Hull", CampaignSystem.selectedSiteResolutionModeReadout(ctx));
        assertTrue(CampaignSystem.cycleSelectedSiteResolutionMode(ctx));
        assertEquals("Strip For Parts", CampaignSystem.selectedSiteResolutionModeReadout(ctx));

        int salvageBefore = st.campaignSalvage;
        int favorBefore = st.yellowLiberationFavor;
        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(st.campaignSalvage > salvageBefore);
        assertEquals(favorBefore, st.yellowLiberationFavor);
        assertTrue(CampaignSystem.transitionSummaryTop(ctx).contains("PARTS SALVAGED"));
    }

    @Test
    void rescueChoicesChangeRelationshipStateAndLowerStrain() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        st.fleetStrain = 62.0;
        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        Ship support = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.name != null
                        && (ship.name.contains("Distress") || ship.name.contains("Relief") || ship.name.contains("Lost")))
                .findFirst()
                .orElse(null);
        assertNotNull(support);
        CampaignSystem.noteAmbientSupportRequest(ctx, support);
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(st.fleetStrain < 62.0);
        assertEquals("TRUSTED", st.vossRelationshipStateId);
        assertTrue(CampaignSystem.selectedLocationSidebarLines(ctx).stream()
                .anyMatch(line -> line.contains("Captain Nadi Voss")));
    }

    @Test
    void allyMarkResolutionCreatesRouteSupportInsteadOfMaxPayout() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation ore = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(ore);

        st.playerGalaxyX = ore.x;
        st.playerGalaxyY = ore.y;
        st.selectedGalaxyLocationId = ore.id;

        assertTrue(CampaignSystem.cycleSelectedSiteResolutionMode(ctx));
        assertTrue(CampaignSystem.cycleSelectedSiteResolutionMode(ctx));
        assertEquals("Mark For Allies", CampaignSystem.selectedSiteResolutionModeReadout(ctx));

        int favorBefore = st.greenContractFavor;
        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(st.greenContractFavor > favorBefore);
        assertTrue(CampaignSystem.selectedLocationSidebarLines(ctx).stream().anyMatch(line -> line.startsWith("Route State: ")));
        assertTrue(CampaignSystem.campaignAfterActionPlateLines(ctx).stream().anyMatch(line -> line.contains("ROUTE")));
    }

    @Test
    void fleetPostureCyclesAndChangesSweepCost() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        assertEquals("Silent Running", CampaignSystem.campaignFleetPostureReadout(ctx));
        assertTrue(CampaignSystem.cycleSelectedFleetPosture(ctx));
        assertEquals("Combat Patrol", CampaignSystem.campaignFleetPostureReadout(ctx));

        while (!"Recon Sweep".equals(CampaignSystem.campaignFleetPostureReadout(ctx))) {
            assertTrue(CampaignSystem.cycleSelectedFleetPosture(ctx));
        }
        assertTrue(CampaignSystem.campaignDirectionFinderLines(ctx).stream().anyMatch(line -> line.startsWith("Route Trend: ")));

        st.campaignSupplies = 20;
        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));
        assertEquals(17, st.campaignSupplies);
        assertTrue(CampaignSystem.campaignNavigationStationLines(ctx).stream().anyMatch(line -> line.startsWith("Posture: Recon Sweep")));
    }

    @Test
    void campaignActionHitboxesResolveToTheRenderedButtonCenters() throws Exception {
        GameContext ctx = initializedCampaignContext();
        assertTrue(CampaignSystem.isStrategicGalaxyMapMode(ctx));
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;

        int viewW = 1280;
        int viewH = 720;
        Rectangle panel = Renderer.getStrategicMapSidebarRect(viewW, viewH, true);
        Method actionRect = Renderer.class.getDeclaredMethod(
                "galaxyActionRect",
                GameContext.class,
                Rectangle.class,
                String.class
        );
        actionRect.setAccessible(true);

        for (String actionId : List.of("ENGAGE_COURSE", "SET_WAYPOINT", "SIGNAL_SWEEP")) {
            Rectangle rect = (Rectangle) actionRect.invoke(null, ctx, panel, actionId);
            assertNotNull(rect, "expected a rendered rect for " + actionId);
            Renderer.CampaignHubClickTarget target = Renderer.campaignHubClickTargetAt(
                    ctx,
                    viewW,
                    viewH,
                    rect.x + rect.width / 2,
                    rect.y + rect.height / 2
            );
            assertNotNull(target, "expected a click target at the center of " + actionId);
            assertEquals(Renderer.CampaignHubClickTarget.Kind.ACTION, target.kind);
            assertEquals(actionId, target.valueId);
        }
    }

    @Test
    void postureChangesTravelDrainAndRouteRisk() throws Exception {
        GameContext silentCtx = initializedCampaignContext();
        CampaignSystem.CampaignState silent = silentCtx.campaign;
        CampaignSystem.selectCampaignFreeTravelTarget(silentCtx, 1600.0, 3200.0);
        assertTrue(CampaignSystem.startTravelToSelectedLocation(silentCtx));
        int silentFuelBefore = silent.campaignFuel;
        int silentSuppliesBefore = silent.campaignSupplies;
        double silentRisk = silent.galaxyTravel.interceptionRisk;
        invokeTravelUpdate(silentCtx, silent, 10.0);
        int silentFuelLoss = silentFuelBefore - silent.campaignFuel;
        int silentSupplyLoss = silentSuppliesBefore - silent.campaignSupplies;

        GameContext patrolCtx = initializedCampaignContext();
        CampaignSystem.CampaignState patrol = patrolCtx.campaign;
        assertTrue(CampaignSystem.cycleSelectedFleetPosture(patrolCtx));
        assertEquals("Combat Patrol", CampaignSystem.campaignFleetPostureReadout(patrolCtx));
        CampaignSystem.selectCampaignFreeTravelTarget(patrolCtx, 1600.0, 3200.0);
        assertTrue(CampaignSystem.startTravelToSelectedLocation(patrolCtx));
        int patrolFuelBefore = patrol.campaignFuel;
        int patrolSuppliesBefore = patrol.campaignSupplies;
        double patrolRisk = patrol.galaxyTravel.interceptionRisk;
        invokeTravelUpdate(patrolCtx, patrol, 10.0);

        assertTrue((patrolFuelBefore - patrol.campaignFuel) > silentFuelLoss);
        assertTrue((patrolSuppliesBefore - patrol.campaignSupplies) > silentSupplyLoss);
        assertTrue(patrolRisk > silentRisk);
    }

    @Test
    void liveTravelAttritionUpdatesCheckpointedExpansionLedgerAndResourceBoard() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        EconomyLogisticsIndustrySystem.LogisticsLedger ledger = st.economyExpansion.logistics;
        int readinessBefore = ledger.readinessPercent;
        int fatigueBefore = ledger.crewFatigue;

        CampaignSystem.selectCampaignFreeTravelTarget(ctx,
                Math.min(ctx.WORLD_W - 120.0, st.playerGalaxyX + 240.0),
                Math.min(ctx.WORLD_H - 120.0, st.playerGalaxyY + 180.0));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        invokeTravelUpdate(ctx, st, 10.0);

        assertTrue(ledger.readinessPercent < readinessBefore);
        assertTrue(ledger.crewFatigue > fatigueBefore);
        assertEquals(st.campaignFuel, ledger.stores.get(EconomyLogisticsIndustrySystem.Resource.FUEL));
        assertEquals(st.campaignAmmo, ledger.stores.get(EconomyLogisticsIndustrySystem.Resource.AMMUNITION));
        assertEquals(st.campaignSupplies, ledger.stores.get(EconomyLogisticsIndustrySystem.Resource.PROVISIONS));
        assertTrue(CampaignSystem.campaignResourceManagerLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Expansion Ledger: Readiness ")));
    }

    @Test
    void longTravelAndLowStoresIncreaseFleetStrain() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.fleetStrain = 10.0;
        st.campaignFuel = 22;
        st.campaignSupplies = 18;
        while (!"Combat Patrol".equals(CampaignSystem.campaignFleetPostureReadout(ctx))) {
            assertTrue(CampaignSystem.cycleSelectedFleetPosture(ctx));
        }
        CampaignSystem.selectCampaignFreeTravelTarget(ctx,
                Math.min(ctx.WORLD_W - 120.0, st.playerGalaxyX + 240.0),
                Math.min(ctx.WORLD_H - 120.0, st.playerGalaxyY + 180.0));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));

        invokeTravelUpdate(ctx, st, 16.0);
        assertTrue(st.fleetStrain > 10.0);
        assertTrue(CampaignSystem.campaignCrewCommentaryLines(ctx).size() >= 2);
    }

    @Test
    void hubRepairRelievesStrainAndBuildsRelationship() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation repair = firstLocationOfType(ctx, "REPAIR_SITE");
        assertNotNull(repair);

        st.fleetStrain = 68.0;
        ctx.credits = 5000;
        st.campaignSupplies = 500;
        st.campaignSalvage = 500;
        st.economyExpansion.logistics.crewFatigue = 40;
        st.economyExpansion.logistics.readinessPercent = 60;
        int industrialReputationBefore = st.diplomacyNarrative.relationships.reputation
                .get(DiplomacyNarrativeCrewSystem.ReputationGroup.INDUSTRIAL);
        int bridgeLogBefore = st.diplomacyNarrative.officers.get(DiplomacyNarrativeCrewSystem.CrewStation.CAPTAIN)
                .captainLogEntries.size();

        assertTrue(invokeHubService(ctx, st, repair, "REPAIR"));
        assertTrue(st.fleetStrain < 68.0);
        assertEquals("TRUSTED", st.vossRelationshipStateId);
        assertTrue(st.economyExpansion.logistics.crewFatigue < 40);
        assertTrue(st.economyExpansion.logistics.readinessPercent > 60);
        assertEquals(st.campaignSalvage, st.economyExpansion.logistics.stores
                .get(EconomyLogisticsIndustrySystem.Resource.REPAIR_MATERIALS));
        assertEquals(industrialReputationBefore + 1, st.diplomacyNarrative.relationships.reputation
                .get(DiplomacyNarrativeCrewSystem.ReputationGroup.INDUSTRIAL));
        assertTrue(st.diplomacyNarrative.officers.get(DiplomacyNarrativeCrewSystem.CrewStation.CAPTAIN)
                .captainLogEntries.size() > bridgeLogBefore);
        assertTrue(CampaignSystem.campaignCommsBoardLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Expansion Reputation Mil ")));
    }

    @Test
    void rescuePriorityBiasesTransitContactsTowardDistress() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        while (!"Rescue Priority".equals(CampaignSystem.campaignFleetPostureReadout(ctx))) {
            assertTrue(CampaignSystem.cycleSelectedFleetPosture(ctx));
        }

        CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1400.0, 3200.0);
        CampaignSystem.startTravelToSelectedLocation(ctx);
        boolean sawDistress = false;
        for (int i = 0; i < 4; i++) {
            invokeTransitDiscovery(ctx, st);
            CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
            if (selected != null && selected.type.name().equals("DISTRESS_SIGNAL")) {
                sawDistress = true;
                break;
            }
        }
        assertTrue(sawDistress);
    }

    @Test
    void relayEchoLeadSpawnsFollowOnVaultChain() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation relay = new CampaignSystem.CampaignLocation(
                "chain-relay",
                "Relay Echo",
                st.playerGalaxyX + 180.0,
                st.playerGalaxyY + 80.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT,
                0.20f,
                false,
                0,
                "A low-power relay echo is bleeding local traffic."
        );
        relay.discovered = true;
        setObject(relay, "chainType", enumConstant(findNestedEnum("DiscoveryChainType"), "RELAY_ECHO"));
        setIntField(relay, "chainStage", 1);
        st.galaxyAreasOfInterest.add(relay);
        st.playerGalaxyX = relay.x;
        st.playerGalaxyY = relay.y;
        st.selectedGalaxyLocationId = relay.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        CampaignSystem.CampaignLocation followup = CampaignSystem.selectedCampaignLocation(ctx);
        assertNotNull(followup);
        assertEquals("RELAY_ECHO", getObject(followup, "chainType").toString());
        assertEquals(2, getIntField(followup, "chainStage"));
        assertEquals("HIDDEN_CACHE", followup.type.name());
        assertTrue(CampaignSystem.campaignAfterActionPlateLines(ctx).stream().anyMatch(line -> line.toLowerCase().contains("vault")));
    }

    @Test
    void ignoredContactsEscalateIntoConsequencesWhenLeftUnresolved() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation relay = new CampaignSystem.CampaignLocation(
                "ignored-relay",
                "Ignored Relay Echo",
                st.playerGalaxyX + 520.0,
                st.playerGalaxyY + 260.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT,
                0.36f,
                false,
                0,
                "A relay echo is lingering on a broken side-band."
        );
        relay.discovered = true;
        st.galaxyAreasOfInterest.add(relay);

        int searchGroupsBefore = searchGroupCount(st);
        invokeIgnoredContactEscalation(ctx, st, 50.0);
        assertEquals(1, relay.escalationStage);
        st.selectedGalaxyLocationId = relay.id;
        assertTrue(CampaignSystem.selectedLocationSidebarLines(ctx).stream().anyMatch(line -> line.contains("Contact State: Window narrowing")));
        assertTrue(relay.routeNote.contains("hostile listeners"));

        st.selectedGalaxyLocationId = "";
        invokeIgnoredContactEscalation(ctx, st, 60.0);
        assertEquals(2, relay.escalationStage);
        assertTrue(relay.completed);
        assertTrue(relay.scarNote.contains("relay went dark"));
        assertTrue(st.enemyAlertLevel > 0.0);
        assertEquals(searchGroupsBefore, searchGroupCount(st));
        assertFalse(st.pendingHostileReinforcements.isEmpty());
    }

    @Test
    void tacticalMapActionHitboxesResolveToRenderedButtonCenters() throws Exception {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.sectorElapsed = 1.0;
        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.MISSION;
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.OBJECTIVE;
        ctx.ui.tacticalMapSelectionLabel = "Kill Pocket";
        ctx.ui.tacticalMapSelectionSubtitle = "Primary objective";
        ctx.ui.tacticalMapSelectionDetail = "Hostile concentration";
        ctx.ui.tacticalMapSelectionX = 2200.0;
        ctx.ui.tacticalMapSelectionY = 1800.0;

        int viewW = 1280;
        int viewH = 720;
        Rectangle panel = Renderer.getStrategicMapSidebarRect(viewW, viewH, false);
        Method actionRect = Renderer.class.getDeclaredMethod(
                "tacticalActionRect",
                GameContext.class,
                Rectangle.class,
                String.class
        );
        actionRect.setAccessible(true);

        for (String actionId : List.of("TACTICAL_PLOT_COURSE", "TACTICAL_HOLD_POSITION", "TACTICAL_SEND_RECON")) {
            Rectangle rect = (Rectangle) actionRect.invoke(null, ctx, panel, actionId);
            assertNotNull(rect, "expected a rendered rect for " + actionId);
            Renderer.CampaignHubClickTarget target = Renderer.tacticalMapClickTargetAt(
                    ctx,
                    viewW,
                    viewH,
                    rect.x + rect.width / 2,
                    rect.y + rect.height / 2
            );
            assertNotNull(target, "expected a click target at the center of " + actionId);
            assertEquals(Renderer.CampaignHubClickTarget.Kind.ACTION, target.kind);
            assertEquals(actionId, target.valueId);
        }
    }

    @Test
    void strategicAndTacticalTopTabsFitTheirRenderedChipRects() throws Exception {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();

            Method galaxyRectsMethod = Renderer.class.getDeclaredMethod(
                    "galaxyCommandTabRects", int.class, int.class, int.class);
            galaxyRectsMethod.setAccessible(true);
            Method galaxyLabelMethod = Renderer.class.getDeclaredMethod(
                    "campaignCommandTabChipLabel", UiState.CampaignCommandTab.class);
            galaxyLabelMethod.setAccessible(true);
            Rectangle[] galaxyRects = (Rectangle[]) galaxyRectsMethod.invoke(null, 0, 0, 292);
            UiState.CampaignCommandTab[] commandTabs = UiState.CampaignCommandTab.values();
            for (int i = 0; i < commandTabs.length; i++) {
                String label = (String) galaxyLabelMethod.invoke(null, commandTabs[i]);
                assertTrue(fm.stringWidth(label) <= galaxyRects[i].width - 14,
                        "campaign tab label should fit: " + label);
            }

            Method tacticalRectsMethod = Renderer.class.getDeclaredMethod(
                    "tacticalMapTabRects", int.class, int.class, int.class);
            tacticalRectsMethod.setAccessible(true);
            Method tacticalLabelMethod = Renderer.class.getDeclaredMethod(
                    "tacticalMapTabChipLabel", UiState.TacticalMapTab.class);
            tacticalLabelMethod.setAccessible(true);
            Rectangle[] tacticalRects = (Rectangle[]) tacticalRectsMethod.invoke(null, 0, 0, 292);
            UiState.TacticalMapTab[] tacticalTabs = UiState.TacticalMapTab.values();
            for (int i = 0; i < tacticalTabs.length; i++) {
                String label = (String) tacticalLabelMethod.invoke(null, tacticalTabs[i]);
                assertTrue(fm.stringWidth(label) <= tacticalRects[i].width - 14,
                        "tactical tab label should fit: " + label);
            }
        } finally {
            g2.dispose();
        }
    }

    @Test
    void tacticalMissionCommandBayPlotCourseSetsWaypointFromSelection() throws Exception {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.sectorElapsed = 1.0;
        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.MISSION;
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.OBJECTIVE;
        ctx.ui.tacticalMapSelectionLabel = "Prototype Recovery";
        ctx.ui.tacticalMapSelectionSubtitle = "Discovery";
        ctx.ui.tacticalMapSelectionDetail = "Recoverable site";
        ctx.ui.tacticalMapSelectionX = 3100.0;
        ctx.ui.tacticalMapSelectionY = 2600.0;

        int viewW = 1280;
        int viewH = 720;
        Rectangle panel = Renderer.getStrategicMapSidebarRect(viewW, viewH, false);
        Method actionRect = Renderer.class.getDeclaredMethod(
                "tacticalActionRect",
                GameContext.class,
                Rectangle.class,
                String.class
        );
        actionRect.setAccessible(true);
        Rectangle rect = (Rectangle) actionRect.invoke(null, ctx, panel, "TACTICAL_PLOT_COURSE");
        assertNotNull(rect);

        MouseEvent click = new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                rect.x + rect.width / 2,
                rect.y + rect.height / 2,
                1,
                false,
                MouseEvent.BUTTON1
        );

        assertTrue(UISystem.handleCampaignMapUiClick(ctx, click, viewW, viewH));
        assertEquals(3100.0, ctx.ui.waypointX, 1e-6);
        assertEquals(2600.0, ctx.ui.waypointY, 1e-6);
    }

    @Test
    void overmapHostileContactClickBeatsNearbyMissionHitbox() {
        GameContext ctx = initializedCampaignContext();
        int viewW = 1280;
        int viewH = 720;
        CampaignSystem.CampaignLocation location = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(it -> it != null && it.primaryMission && "poi-06".equals(it.id))
                .findFirst()
                .orElse(null);
        assertNotNull(location);
        CampaignSystem.CampaignSupportMarker marker = CampaignSystem.activeSupportMarkers(ctx).stream()
                .filter(it -> it != null && it.type == CampaignSystem.SupportMarkerType.HAZARD)
                .filter(it -> it.label.contains(location.name))
                .findFirst()
                .orElse(null);
        assertNotNull(marker);

        ctx.ui.strategicMapFocusX = location.x;
        ctx.ui.strategicMapFocusY = location.y;
        Rectangle rect = Renderer.getStrategicMapInnerRect(viewW, viewH, true);
        double nx = (marker.x - UISystem.strategicMapWorldMinX(ctx)) / UISystem.strategicMapViewWidth(ctx);
        double ny = (marker.y - UISystem.strategicMapWorldMinY(ctx)) / UISystem.strategicMapViewHeight(ctx);
        int clickX = rect.x + (int) Math.round(nx * rect.width);
        int clickY = rect.y + (int) Math.round(ny * rect.height);

        MouseEvent click = new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                clickX,
                clickY,
                1,
                false,
                MouseEvent.BUTTON1
        );

        UISystem.handleMapClick(ctx, click, viewW, viewH);
        assertEquals(marker.label, CampaignSystem.selectedCampaignContactLabel(ctx));
        assertTrue(CampaignSystem.selectedCampaignContactHostile(ctx));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    @Test
    void frameSizedTravelUpdatesAccumulateOperationalStoreAttrition() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedFleetPostureId = "COMBAT_PATROL";
        st.galaxyTravel.traveling = true;
        st.galaxyTravel.targetX = st.playerGalaxyX + 10000.0;
        st.galaxyTravel.targetY = st.playerGalaxyY;
        st.galaxyTravel.speed = 1.0;
        int fuelBefore = st.campaignFuel;
        int suppliesBefore = st.campaignSupplies;
        int ammoBefore = st.campaignAmmo;

        for (int i = 0; i < 600; i++) {
            invokeTravelUpdate(ctx, st, 1.0 / 60.0);
        }

        assertTrue(st.campaignFuel < fuelBefore);
        assertTrue(st.campaignSupplies < suppliesBefore);
        assertTrue(st.campaignAmmo < ammoBefore);
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

    private static void invokeTransitDiscovery(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "spawnTransitDiscovery",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeGalaxySearchUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxySearchGroups",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeIgnoredContactEscalation(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateIgnoredContactEscalation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static boolean invokeHubService(GameContext ctx, CampaignSystem.CampaignState st, CampaignSystem.CampaignLocation location, String serviceName) throws Exception {
        Class<?> hubServiceType = findNestedEnum("HubService");
        Method method = CampaignSystem.class.getDeclaredMethod(
                "performHubService",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class,
                hubServiceType
        );
        method.setAccessible(true);
        Object service = enumConstant(hubServiceType, serviceName);
        return (boolean) method.invoke(null, ctx, st, location, service);
    }

    private static CampaignSystem.CampaignLocation firstLocationOfType(GameContext ctx, String typeName) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.type.name().equals(typeName)) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstFriendlyServiceLocation(GameContext ctx) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            String name = location == null || location.name == null ? "" : location.name.toUpperCase();
            String detail = location == null || location.detail == null ? "" : location.detail.toUpperCase();
            if (location != null && location.services != null && !location.services.isEmpty()
                    && (name.contains("GREEN")
                    || name.contains("YELLOW")
                    || detail.contains("GREEN")
                    || detail.contains("YELLOW")
                    || detail.contains("BROKER")
                    || detail.contains("COALITION")
                    || detail.contains("RESISTANCE"))) {
                return location;
            }
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.services != null && !location.services.isEmpty()) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation lastLocationOfType(GameContext ctx, String typeName) {
        CampaignSystem.CampaignLocation found = null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.type.name().equals(typeName)) found = location;
        }
        return found;
    }

    private static boolean getBooleanField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setDoubleField(Object target, String fieldName, double value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setDouble(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setIntField(Object target, String fieldName, int value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setBoolean(Object target, String fieldName, boolean value) {
        setBooleanField(target, fieldName, value);
    }

    private static void setDouble(Object target, String fieldName, double value) {
        setDoubleField(target, fieldName, value);
    }

    private static Class<?> fieldType(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getType();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) {
        try {
            Field field = st.getClass().getDeclaredField("galaxySearchGroups");
            field.setAccessible(true);
            List<?> groups = (List<?>) field.get(st);
            assertFalse(groups.isEmpty());
            return groups.get(0);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object firstSearchGroupWithMinTier(CampaignSystem.CampaignState st, int minTier) {
        try {
            Field field = st.getClass().getDeclaredField("galaxySearchGroups");
            field.setAccessible(true);
            List<?> groups = (List<?>) field.get(st);
            for (Object group : groups) {
                if (group == null) continue;
                Field tierField = group.getClass().getDeclaredField("tier");
                tierField.setAccessible(true);
                if (tierField.getInt(group) >= minTier) return group;
            }
            return null;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static int searchGroupCount(CampaignSystem.CampaignState st) {
        try {
            Field field = st.getClass().getDeclaredField("galaxySearchGroups");
            field.setAccessible(true);
            return ((List<?>) field.get(st)).size();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> findNestedEnum(String simpleName) {
        for (Class<?> nested : CampaignSystem.class.getDeclaredClasses()) {
            if (nested != null && simpleName.equals(nested.getSimpleName())) {
                return nested;
            }
        }
        throw new AssertionError("Missing nested enum: " + simpleName);
    }

    private static int getIntField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object[] loadSectorScripts() {
        try {
            Field field = CampaignSystem.class.getDeclaredField("SCRIPTS");
            field.setAccessible(true);
            return (Object[]) field.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setObject(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object getObject(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
