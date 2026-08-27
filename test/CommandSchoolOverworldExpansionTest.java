import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandSchoolOverworldExpansionTest {

    @Test
    void tutorialModeStartsInsideSafeTacticalTrainingZone() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.campaign);
        assertTrue(ctx.campaign.commandSchoolTraining);
        assertFalse(ctx.campaign.strategicOvermapMode);
        assertTrue(ctx.campaign.galaxyAmbientEncounterActive);
        assertEquals(GameState.RUNNING, ctx.state);
        assertFalse(ctx.ui.mapOpen);
        assertTrue(TutorialSystem.hudTitle(ctx).contains("TUTORIAL"));

        Ship practiceDrone = ctx.ships.stream()
                .filter(ship -> ship != null && "Tutorial Drone".equals(ship.name))
                .findFirst()
                .orElse(null);
        assertNotNull(practiceDrone);
        assertTrue(practiceDrone.surrendered, "tutorial practice drone should not move or fight back");
        assertEquals(0.0, practiceDrone.desiredSpeedBase, 1e-6);

        CampaignSystem.CampaignLocation anchorage = location(ctx, CampaignSystem.COMMAND_SCHOOL_ANCHORAGE_ID);
        CampaignSystem.CampaignLocation hub = location(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        CampaignSystem.CampaignLocation ore = location(ctx, CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID);
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);

        assertNotNull(anchorage);
        assertNotNull(hub);
        assertNotNull(ore);
        assertNotNull(red);
        assertEquals(Faction.ALLY, anchorage.ownerFaction);
        assertEquals(Faction.BRIGHT_YELLOW, hub.ownerFaction);
        assertEquals(Faction.ENEMY, red.ownerFaction);
        assertEquals(CampaignSystem.CampaignLocationType.RESOURCE_ZONE, ore.type);
        assertFalse(CampaignSystem.persistCheckpointForMenuExit(ctx),
                "Command School must not save over or create a normal campaign checkpoint");
    }

    @Test
    void academyObjectiveOwnsTheHudInsteadOfLeakingCampaignGoals() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);

        GameRenderSystem.HudObjective objective = GameRenderSystem.resolveHudObjective(ctx);

        assertTrue(objective.title().contains("TUTORIAL"));
        assertTrue(objective.detail().contains("movement"));
        assertFalse(objective.title().contains("TRADE HUB COLLAPSE"));
        assertFalse(objective.detail().contains("Reach Earth"));
        assertEquals(GameContext.HudDetail.MINIMAL,
                GameRenderSystem.effectiveHudDetailForRenderPressure(ctx, GameContext.HudDetail.FULL));
        assertTrue(TutorialSystem.coreMenuActionVisible(ctx, 2),
                "the first lesson should keep the map shortcut available");
        assertFalse(TutorialSystem.coreMenuActionVisible(ctx, 0),
                "fleet management should stay hidden until its lesson");
        assertFalse(TutorialSystem.coreMenuActionVisible(ctx, 3),
                "power management should stay hidden until its lesson");
    }

    @Test
    void tutorialBaseDoesNotCreateShieldBubbleOrLeashPlayer() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);

        Ship base = ctx.ships.stream()
                .filter(ship -> ship != null && "Tutorial Base".equals(ship.name))
                .findFirst()
                .orElse(null);
        assertNotNull(base);
        assertFalse(base.shieldActive, "tutorial base should not show or imply a station shield bubble");
        assertEquals(0.0, base.shieldMax, 1e-6);
        assertEquals(0.0, base.shieldRegen, 1e-6);
        assertEquals(0.0, base.repairRange, 1e-6, "tutorial base should not draw a station aura");
        double visualClearance = RoleStats.get(ShipRole.BASE).radius * ShipHullSilhouette.skinRenderScale() + 220.0;
        assertTrue(Math.hypot(ctx.player.x - base.x, ctx.player.y - base.y) >= visualClearance,
                "tutorial player should start outside the visible station footprint");

        ctx.player.x = base.x + 420.0;
        ctx.player.y = base.y;
        PhysicsSystem.update(ctx, GameContext.DT);

        assertTrue(Math.hypot(ctx.player.x - base.x, ctx.player.y - base.y) > 300.0,
                "tutorial player should be able to leave the former station shield area");

        BufferedImage image = new BufferedImage(280, 280, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        base.x = 140.0;
        base.y = 140.0;
        Method drawFleetSelectionMarker = GameRenderSystem.class.getDeclaredMethod("drawFleetSelectionMarker",
                Graphics2D.class, Ship.class);
        drawFleetSelectionMarker.setAccessible(true);
        try {
            drawFleetSelectionMarker.invoke(null, g2, base);
        } finally {
            g2.dispose();
        }
        int oldRingX = (int) Math.round(base.x + Math.max(46.0, base.radius * 1.9));
        int oldRingY = (int) Math.round(base.y);
        int alpha = (image.getRGB(oldRingX, oldRingY) >>> 24) & 0xff;
        assertEquals(0, alpha, "tutorial base selection should not draw a shield-looking ring");
    }

    @Test
    void tutorialStationUpgradesFitWithinFrigateOreCapacity() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);

        Ship base = ctx.ships.stream()
                .filter(ship -> ship != null && "Tutorial Base".equals(ship.name))
                .findFirst()
                .orElse(null);
        assertNotNull(base);
        BaseUpgrades upgrades = ctx.baseUpgrades.get(base);
        assertNotNull(upgrades);
        assertEquals(1, upgrades.miningLv);
        assertEquals(2, upgrades.hangarLv);
        assertTrue(ctx.player.cargoMax <= 120, "tutorial starts in the frigate cargo band");

        int miningOre = CampaignSystem.baseUpgradeOreCost(ctx, base, 4, upgrades.miningLv + 1);
        int hangarOre = CampaignSystem.baseUpgradeOreCost(ctx, base, 5, upgrades.hangarLv + 1);
        assertTrue(miningOre <= ctx.player.cargoMax, "station ore/logistics upgrade must fit in one frigate haul");
        assertTrue(hangarOre <= ctx.player.cargoMax, "required hangar upgrade must fit in one frigate haul");
        assertTrue(miningOre + hangarOre <= ctx.player.cargoMax,
                "buying the ore/logistics lesson upgrade first should not block the required hangar step");

        ctx.campaign.oreLedger.storedOre = ctx.player.cargoMax;
        ctx.player.cargo = ctx.player.cargoMax;
        base.oreStockpile = ctx.player.cargoMax;
        ctx.player.x = base.x;
        ctx.player.y = base.y;
        ctx.ui.baseMenuOpen = true;

        assertSame(base, CampaignSystem.currentBaseUpgradeAnchor(ctx),
                "docked command-school station upgrades should target Tutorial Base, not the frigate");
        UISystem.tryUpgradeBase(ctx, 4);
        UISystem.tryUpgradeBase(ctx, 5);

        assertTrue(upgrades.miningLv >= 2);
        assertTrue(upgrades.hangarLv >= 3);
        assertTrue(base.oreStockpile >= 0);
    }

    @Test
    void bridgeLessonCanSwapPlayerIntoCarrierThroughLoadoutPanel() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "BRIDGE_SYSTEMS");

        Ship base = ctx.ships.stream()
                .filter(ship -> ship != null && "Tutorial Base".equals(ship.name))
                .findFirst()
                .orElse(null);
        assertNotNull(base);
        BaseUpgrades upgrades = ctx.baseUpgrades.get(base);
        assertNotNull(upgrades);
        upgrades.hangarLv = Math.max(upgrades.hangarLv, 3);
        ctx.credits = Math.max(ctx.credits, 2600);

        UISystem.toggleShop(ctx);
        assertTrue(ctx.ui.shopOpen);
        assertFalse(CampaignSystem.usesPersistentFleetShop(ctx),
                "tutorial tactical school must use flagship hull swap, not campaign fleet commissioning");
        UISystem.performHullSwapByRole(ctx, ShipRole.CARRIER);
        TutorialSystem.update(ctx, GameContext.DT);

        assertEquals(ShipRole.CARRIER, ctx.player.role);
        assertTrue(ctx.player.isCarrier);
        assertTrue(stateBool(tutorialState, "swappedToCarrier"),
                "bridge lesson should recognize the normal loadout carrier swap");
    }

    @Test
    void sampleOverworldUsesRealSelectionTravelAndArrivalState() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        CampaignSystem.returnCommandSchoolToOverworld(ctx, "test overmap");
        CampaignSystem.CampaignLocation hub = location(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        assertNotNull(hub);

        assertTrue(CampaignSystem.selectCampaignLocation(ctx, hub.x, hub.y));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertEquals("PLOT_COURSE", ctx.campaign.commandSchoolLastActionId);
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));
        assertTrue(ctx.campaign.galaxyTravel.traveling);

        for (int i = 0; i < 30 && ctx.campaign.galaxyTravel.traveling; i++) {
            CampaignSystem.update(ctx, 1.0);
        }

        assertEquals(CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID, ctx.campaign.currentGalaxyLocationId);
        assertFalse(ctx.campaign.galaxyTravel.traveling);
    }

    @Test
    void plotMovementLessonRecognizesHubCourseAfterImmediateEngage() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        CampaignSystem.returnCommandSchoolToOverworld(ctx, "test overmap");
        setLesson(ctx, tutorialState, "PLOT_MOVEMENT");
        CampaignSystem.CampaignLocation hub = location(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        assertNotNull(hub);

        assertTrue(CampaignSystem.selectCampaignLocation(ctx, hub.x, hub.y));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));
        assertEquals("ENGAGE_COURSE", ctx.campaign.commandSchoolLastActionId);

        TutorialSystem.update(ctx, GameContext.DT);

        assertTrue(stateBool(tutorialState, "plottedTrainingCourse"),
                "plot step should survive immediate Engage overwriting the last tutorial action");
        assertTrue(stateBool(tutorialState, "engagedTrainingCourse"));
    }

    @Test
    void plotMovementLessonRecognizesHubCourseWhenAlreadyInsideHubZone() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        CampaignSystem.returnCommandSchoolToOverworld(ctx, "test overmap");
        setLesson(ctx, tutorialState, "PLOT_MOVEMENT");
        CampaignSystem.CampaignLocation hub = location(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        assertNotNull(hub);
        ctx.campaign.playerGalaxyX = hub.x;
        ctx.campaign.playerGalaxyY = hub.y;
        ctx.campaign.currentGalaxyLocationId = "";
        ctx.campaign.dockedGalaxyLocationId = "";

        assertTrue(CampaignSystem.selectCampaignLocation(ctx, hub.x, hub.y));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));

        TutorialSystem.update(ctx, GameContext.DT);

        assertTrue(stateBool(tutorialState, "plottedTrainingCourse"),
                "plot step should accept the selected hub course even when Engage resolves as already docked");
        assertTrue(stateBool(tutorialState, "engagedTrainingCourse"));
        assertTrue(stateBool(tutorialState, "reachedTrainingHub"));
    }

    @Test
    void commandSchoolTutorialChecklistCanBeCompletedThroughSupportedActions() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);

        assertLesson(tutorialState, "FLIGHT_BASICS");
        movePlayerTo(ctx, stateDouble(tutorialState, "alphaX"), stateDouble(tutorialState, "alphaY"));
        TutorialSystem.update(ctx, GameContext.DT);
        assertTrue(TutorialSystem.handleStrategicMapClick(ctx,
                stateDouble(tutorialState, "betaX"),
                stateDouble(tutorialState, "betaY"),
                false));
        movePlayerTo(ctx, stateDouble(tutorialState, "betaX"), stateDouble(tutorialState, "betaY"));
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "TARGETING_AND_SENSORS");

        UISystem.addPing(ctx, stateDouble(tutorialState, "weaponsX"), stateDouble(tutorialState, "weaponsY"), 2.0);
        Ship drone = shipById(ctx, stateInt(tutorialState, "combatTargetId"));
        assertNotNull(drone);
        drone.hp = Math.max(0, drone.hpMax - 1);
        UISystem.cycleXrayFilterMode(ctx, 1);
        ctx.ui.xrayFocusedRoom = ShipRoomLayout.RoomId.ENGINES;
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "LOGISTICS_AND_REFIT");

        movePlayerTo(ctx, stateDouble(tutorialState, "miningX"), stateDouble(tutorialState, "miningY"));
        ctx.miningKeyDown = true;
        for (int i = 0; i < 8 && !stateBool(tutorialState, "minedOre"); i++) {
            EconomySystem.update(ctx, 1.0);
            TutorialSystem.update(ctx, GameContext.DT);
        }
        ctx.miningKeyDown = false;
        assertTrue(stateBool(tutorialState, "minedOre"));
        Ship base = shipById(ctx, stateInt(tutorialState, "homeBaseId"));
        assertNotNull(base);
        movePlayerTo(ctx, base.x, base.y);
        ctx.campaign.oreLedger.storedOre = Math.max(ctx.campaign.oreLedger.storedOre, 600);
        base.oreStockpile = Math.max(base.oreStockpile, 600);
        ctx.ui.baseMenuOpen = true;
        UISystem.tryUpgradeBase(ctx, 5);
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "BRIDGE_SYSTEMS");

        BaseUpgrades upgrades = ctx.baseUpgrades.get(base);
        assertNotNull(upgrades);
        upgrades.hangarLv = Math.max(upgrades.hangarLv, 3);
        ctx.credits = Math.max(ctx.credits, 2600);
        UISystem.toggleShop(ctx);
        UISystem.performHullSwapByRole(ctx, ShipRole.CARRIER);
        TutorialSystem.update(ctx, 1.0);
        UISystem.applyPowerPreset(ctx, Ship.PowerPreset.ATTACK);
        UISystem.toggleCrewStations(ctx);
        UISystem.applyCaptainPreset(ctx, 3);
        TutorialSystem.update(ctx, 1.0);
        for (int i = 0; i < 8 && !stateBool(tutorialState, "fireSuppressed"); i++) {
            UISystem.suppressHottestFire(ctx);
            TutorialSystem.update(ctx, 0.25);
        }
        assertTrue(stateBool(tutorialState, "fireSuppressed"));
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "CARRIER_AND_WARP");

        UISystem.toggleFlightDeck(ctx);
        TutorialSystem.update(ctx, GameContext.DT);
        UISystem.toggleFlightDeck(ctx);
        UISystem.tryCarrierLaunch(ctx);
        TutorialSystem.update(ctx, GameContext.DT);
        UISystem.tryCarrierToggleMode(ctx);
        TutorialSystem.update(ctx, GameContext.DT);
        assertTrue(CampaignSystem.completeSafeMissionExit(ctx));
        TutorialSystem.update(ctx, 1.0);
        assertTrue(stateBool(tutorialState, "openedFlightDeck"), "flight deck open did not register");
        assertTrue(stateBool(tutorialState, "launchedWing"), "carrier launch did not register");
        assertTrue(stateBool(tutorialState, "carrierModeChanged")
                || stateBool(tutorialState, "carrierAutoLaunchChanged"), "carrier behavior change did not register");
        assertTrue(stateBool(tutorialState, "withdrewToOverworld"), "withdraw did not return to the overworld");
        assertLesson(tutorialState, "SITE_SELECTION");
        travelLessonSelect(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "PLOT_MOVEMENT");

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));
        runCampaignTravel(ctx);
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "SCAN_AND_INTEL");

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "TRAFFIC_AUDIT"));
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "RESOURCE_SITE");

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));
        runCampaignTravel(ctx);
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "FLEET_ORGANIZATION");

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "FLEET_COMMIT_NOW"));
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "OVERWORLD_TO_MISSION");

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));
        runCampaignTravel(ctx);
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENTER_SITE"));
        TutorialSystem.update(ctx, 1.0);
        assertLesson(tutorialState, "COMPLETE");
    }

    @Test
    void commandSchoolEnterSiteTransitionsIntoTacticalSchoolWithoutOverworldEject() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "OVERWORLD_TO_MISSION");
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        assertNotNull(red);
        ctx.campaign.playerGalaxyX = red.x;
        ctx.campaign.playerGalaxyY = red.y;
        ctx.campaign.currentGalaxyLocationId = red.id;
        ctx.campaign.dockedGalaxyLocationId = red.id;
        ctx.campaign.selectedGalaxyLocationId = red.id;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENTER_SITE"));
        assertTrue(ctx.campaign.galaxyAmbientEncounterActive);
        assertFalse(ctx.campaign.strategicOvermapMode);

        for (int i = 0; i < 8; i++) {
            TutorialSystem.update(ctx, GameContext.DT);
            CampaignSystem.update(ctx, GameContext.DT);
            assertFalse(ctx.campaign.strategicOvermapMode, "training mission ejected on frame " + i);
            assertTrue(ctx.campaign.galaxyAmbientEncounterActive, "training site ended on frame " + i);
        }
        assertTrue(TutorialSystem.hudTitle(ctx).contains("TUTORIAL"));
        assertTrue(TutorialSystem.hudDetail(ctx).contains("complete"));
    }

    @Test
    void tutorialMissionMapUsesSubzoneFramingAndArrowPan() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "OVERWORLD_TO_MISSION");
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        assertNotNull(red);
        ctx.campaign.playerGalaxyX = red.x;
        ctx.campaign.playerGalaxyY = red.y;
        ctx.campaign.currentGalaxyLocationId = red.id;
        ctx.campaign.dockedGalaxyLocationId = red.id;
        ctx.campaign.selectedGalaxyLocationId = red.id;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENTER_SITE"));
        if (!ctx.ui.mapOpen) UISystem.toggleMap(ctx);
        double before = UISystem.strategicMapFocusX(ctx);
        boolean panLeft = before > ctx.WORLD_W * 0.5;
        ctx.cameraPanLeft = panLeft;
        ctx.cameraPanRight = !panLeft;

        UISystem.updateStrategicMapCameraPan(ctx, 1.0);

        assertTrue(CampaignSystem.usesMissionSubzones(ctx), "tutorial missions should use campaign mission map framing");
        assertTrue(UISystem.strategicMapViewWidth(ctx) < ctx.WORLD_W,
                "tutorial mission map should frame the active training sector instead of the full world");
        double after = UISystem.strategicMapFocusX(ctx);
        assertTrue(panLeft ? after < before : after > before,
                "arrow keys should pan the open tutorial mission map");
    }

    @Test
    void tutorialTrainingMapRecentersAfterEnteringMissionFromStaleOverworldFocus() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "OVERWORLD_TO_MISSION");
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        assertNotNull(red);
        ctx.campaign.playerGalaxyX = red.x;
        ctx.campaign.playerGalaxyY = red.y;
        ctx.campaign.currentGalaxyLocationId = red.id;
        ctx.campaign.dockedGalaxyLocationId = red.id;
        ctx.campaign.selectedGalaxyLocationId = red.id;
        ctx.ui.strategicMapFocusX = ctx.WORLD_W - 160.0;
        ctx.ui.strategicMapFocusY = ctx.WORLD_H - 160.0;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENTER_SITE"));
        for (int i = 0; i < 8; i++) {
            TutorialSystem.update(ctx, GameContext.DT);
            CampaignSystem.update(ctx, GameContext.DT);
        }

        ctx.ui.mapOpen = true;
        Rectangle map = Renderer.getStrategicMapInnerRect(1280, 720, false);
        java.awt.Point playerPoint = TutorialSystem.strategicMapPointForWorld(ctx, ctx.player.x, ctx.player.y, map);

        assertNotNull(playerPoint,
                "opening the tactical tutorial map after site entry should show the live player pocket");
        assertTrue(map.contains(playerPoint),
                "player should be projected inside the visible tutorial map after stale overmap focus is cleared");
    }

    @Test
    void tutorialScanLessonAcceptsCurrentReconActions() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "SCAN_AND_INTEL");
        TutorialSystem.update(ctx, GameContext.DT);

        assertEquals(UiState.CampaignCommandTab.NAV, ctx.ui.campaignCommandTab,
                "scan lesson should show the tab that contains recon actions");
        assertTrue(CampaignSystem.hasSelectedCampaignContactTarget(ctx));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "SIGNAL_SWEEP"));
        TutorialSystem.update(ctx, GameContext.DT);

        assertTrue(stateBool(tutorialState, "trackedTrainingContact"),
                "current recon/sweep actions should satisfy the old track-contact lesson gate");
    }

    @Test
    void tutorialPlayerCopyAvoidsRetiredResourceStockpiles() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        for (String lessonName : List.of(
                "OVERWORLD_MAP_READING",
                "SITE_SELECTION",
                "PLOT_MOVEMENT",
                "SCAN_AND_INTEL",
                "RESOURCE_SITE",
                "STATION_SERVICES",
                "FLEET_ORGANIZATION",
                "OVERWORLD_TO_MISSION",
                "FLIGHT_BASICS",
                "TARGETING_AND_SENSORS",
                "LOGISTICS_AND_REFIT",
                "BRIDGE_SYSTEMS",
                "CARRIER_AND_WARP")) {
            setLesson(ctx, tutorialState, lessonName);
            String visibleCopy = (TutorialSystem.hudDetail(ctx) + "\n" + TutorialSystem.contextHint(ctx))
                    .toLowerCase(java.util.Locale.US);
            assertFalse(visibleCopy.contains("fuel"), lessonName + " should not mention retired fuel stockpiles");
            assertFalse(visibleCopy.contains("supplies"), lessonName + " should not mention retired supplies stockpiles");
            assertFalse(visibleCopy.contains("ammo"), lessonName + " should not mention retired ammo stockpiles");
            assertFalse(visibleCopy.contains("salvage"), lessonName + " should not mention retired salvage stockpiles");
        }
    }

    @Test
    void tacticalMapDoesNotClampOffscreenSupportLabelsToFrameEdge() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = 2.4;
        ctx.ui.strategicMapFocusX = ctx.player.x;
        ctx.ui.strategicMapFocusY = ctx.player.y;
        Rectangle map = Renderer.getStrategicMapInnerRect(1280, 720, false);
        CampaignSystem.CampaignSupportMarker farMarker = new CampaignSystem.CampaignSupportMarker(
                CampaignSystem.SupportMarkerType.HAZARD,
                "Far Training Contact",
                "Outside the current tactical map window",
                Faction.ENEMY,
                ctx.WORLD_W - 120.0,
                ctx.WORLD_H - 120.0,
                90.0,
                80);
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            List<Renderer.StrategicSupportLabelLayout> labels =
                    Renderer.strategicSupportMarkerLabelLayoutsForTest(g2, ctx, map, List.of(farMarker),
                            UISystem.strategicMapWorldMinX(ctx),
                            UISystem.strategicMapWorldMinY(ctx),
                            UISystem.strategicMapViewWidth(ctx),
                            UISystem.strategicMapViewHeight(ctx));
            assertTrue(labels.isEmpty(),
                    "offscreen tactical contacts should not be clamped into edge labels");
        } finally {
            g2.dispose();
        }
    }

    @Test
    void tutorialMapMarkersUseZoomedWorldProjection() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        double alphaX = stateDouble(tutorialState, "alphaX");
        double alphaY = stateDouble(tutorialState, "alphaY");
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = 1.0;
        ctx.ui.strategicMapFocusX = alphaX;
        ctx.ui.strategicMapFocusY = alphaY;

        Rectangle map = Renderer.getStrategicMapInnerRect(1280, 720, false);
        java.awt.Point centered = TutorialSystem.strategicMapPointForWorld(ctx, alphaX, alphaY, map);
        assertNotNull(centered);
        int expectedCenteredX = expectedMapX(ctx, alphaX, map);
        int expectedCenteredY = expectedMapY(ctx, alphaY, map);
        assertEquals(expectedCenteredX, centered.x, 1,
                "tutorial markers should project through the same zoomed map window as clicks");
        assertEquals(expectedCenteredY, centered.y, 1,
                "tutorial markers should project through the same zoomed map window as clicks");

        ctx.ui.strategicMapZoom = 2.0;
        ctx.ui.strategicMapFocusX = alphaX + 240.0;
        ctx.ui.strategicMapFocusY = alphaY;
        java.awt.Point panned = TutorialSystem.strategicMapPointForWorld(ctx, alphaX, alphaY, map);
        assertNotNull(panned);
        int oldFullWorldX = map.x + (int) Math.round((alphaX / ctx.WORLD_W) * map.width);
        assertEquals(expectedMapX(ctx, alphaX, map), panned.x, 1);
        assertTrue(Math.abs(panned.x - oldFullWorldX) > 20,
                "zoomed tutorial markers should not use the old full-world projection");
    }

    @Test
    void tutorialMissionMapOnlyAcceptsClicksNearActiveMarker() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "FLIGHT_BASICS");
        ctx.ui.mapOpen = true;
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;
        ctx.ui.strategicMapZoom = 1.0;

        Rectangle map = Renderer.getStrategicMapInnerRect(1280, 720, false);
        MouseEvent centerClick = new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                map.x + map.width / 2,
                map.y + map.height / 2,
                1,
                false,
                MouseEvent.BUTTON1);
        UISystem.handleMapClick(ctx, centerClick, 1280, 720);

        assertFalse(Double.isFinite(ctx.ui.waypointX),
                "tutorial map should not turn broad empty map regions into warp waypoints");

        double alphaX = stateDouble(tutorialState, "alphaX");
        double alphaY = stateDouble(tutorialState, "alphaY");
        ctx.ui.strategicMapFocusX = alphaX;
        ctx.ui.strategicMapFocusY = alphaY;
        java.awt.Point alphaPoint = TutorialSystem.strategicMapPointForWorld(ctx, alphaX, alphaY, map);
        assertNotNull(alphaPoint);
        MouseEvent alphaClick = new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                alphaPoint.x,
                alphaPoint.y,
                1,
                false,
                MouseEvent.BUTTON1);
        UISystem.handleMapClick(ctx, alphaClick, 1280, 720);

        assertEquals(alphaX, ctx.ui.waypointX, 1e-6);
        assertEquals(alphaY, ctx.ui.waypointY, 1e-6);
    }

    @Test
    void commandSchoolLessonsCanBeSkippedAndArchived() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        String before = TutorialSystem.hudTitle(ctx);

        TutorialSystem.skipCurrent(ctx);
        String after = TutorialSystem.hudTitle(ctx);
        assertNotEquals(before, after);

        TutorialSystem.toggleArchive(ctx);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        try {
            TutorialSystem.drawOverlay(ctx, g2, 1280, 720);
        } finally {
            g2.dispose();
        }
    }

    private static GameContext tutorialContext() {
        return new GameContext(new GameConfig(GameMode.TUTORIAL, 5000, 5000, true, 8181L, false));
    }

    private static CampaignSystem.CampaignLocation location(GameContext ctx, String id) {
        List<CampaignSystem.CampaignLocation> main = CampaignSystem.mainCampaignLocations(ctx);
        for (CampaignSystem.CampaignLocation location : main) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static Object tutorialState(GameContext ctx) throws Exception {
        Field statesField = TutorialSystem.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<GameContext, Object> states = (Map<GameContext, Object>) statesField.get(null);
        return states.get(ctx);
    }

    private static double stateDouble(Object state, String fieldName) throws Exception {
        Field field = state.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(state);
    }

    private static int stateInt(Object state, String fieldName) throws Exception {
        Field field = state.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(state);
    }

    private static boolean stateBool(Object state, String fieldName) throws Exception {
        Field field = state.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(state);
    }

    private static int expectedMapX(GameContext ctx, double worldX, Rectangle map) {
        double nx = (worldX - UISystem.strategicMapWorldMinX(ctx)) / UISystem.strategicMapViewWidth(ctx);
        return map.x + (int) Math.round(nx * map.width);
    }

    private static int expectedMapY(GameContext ctx, double worldY, Rectangle map) {
        double ny = (worldY - UISystem.strategicMapWorldMinY(ctx)) / UISystem.strategicMapViewHeight(ctx);
        return map.y + (int) Math.round(ny * map.height);
    }

    private static Ship shipById(GameContext ctx, int id) {
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == id) return ship;
        }
        return null;
    }

    private static void movePlayerTo(GameContext ctx, double x, double y) {
        ctx.player.x = x;
        ctx.player.y = y;
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
    }

    private static void travelLessonSelect(GameContext ctx, String locationId) {
        CampaignSystem.CampaignLocation location = location(ctx, locationId);
        assertNotNull(location);
        assertTrue(CampaignSystem.selectCampaignLocation(ctx, location.x, location.y));
    }

    private static void runCampaignTravel(GameContext ctx) {
        for (int i = 0; i < 60 && ctx.campaign.galaxyTravel.traveling; i++) {
            CampaignSystem.update(ctx, 1.0);
        }
        assertFalse(ctx.campaign.galaxyTravel.traveling, "training route did not finish");
    }

    private static void assertLesson(Object state, String lessonName) throws Exception {
        assertEquals(lessonName, currentLessonName(state));
    }

    private static String currentLessonName(Object state) throws Exception {
        Field lessonIndex = state.getClass().getDeclaredField("lessonIndex");
        lessonIndex.setAccessible(true);
        int idx = lessonIndex.getInt(state);
        Class<?> lessonClass = Class.forName("TutorialSystem$LessonId");
        Object[] lessons = lessonClass.getEnumConstants();
        idx = Math.max(0, Math.min(lessons.length - 1, idx));
        return ((Enum<?>) lessons[idx]).name();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setLesson(GameContext ctx, Object state, String lessonName) throws Exception {
        Class<?> stateClass = state.getClass();
        Class<? extends Enum> lessonClass = (Class<? extends Enum>) Class.forName("TutorialSystem$LessonId");
        Enum lesson = Enum.valueOf((Class) lessonClass, lessonName);
        java.lang.reflect.Method enterLesson = TutorialSystem.class.getDeclaredMethod(
                "enterLesson", GameContext.class, stateClass, lessonClass, boolean.class);
        enterLesson.setAccessible(true);
        enterLesson.invoke(null, ctx, state, lesson, false);
    }
}
