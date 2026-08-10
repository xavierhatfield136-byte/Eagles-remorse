import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMapDisciplineTest {

    @Test
    void campaignMapDisciplineClearsCombatArtifactsAndTacticalOverlays() {
        GameContext ctx = strategicMapContext();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3000.0, 2500.0));
        ctx.projectiles.add(new Bullet(2600.0, 2500.0, 0.0, GameContext.DT, Faction.ENEMY));
        ctx.asteroids.add(new Asteroid(2800.0, 2400.0, 80.0));
        ctx.salvage.add(new Salvage(2700.0, 2600.0, 25, 5, 20.0));
        ctx.ui.shopOpen = true;
        ctx.ui.baseMenuOpen = true;
        ctx.ui.powerManagementOpen = true;
        ctx.ui.crewStationsOpen = true;
        ctx.ui.flightDeckOpen = true;
        ctx.ui.tacticalViewEnabled = true;
        ctx.firingPrimaryManual = true;
        ctx.firingPrimaryManualLatched = true;
        ctx.firingSecondaryManual = true;
        ctx.firingSecondaryManualLatched = true;
        ctx.firingPrimaryAuto = true;
        ctx.firingSecondaryAuto = true;
        ctx.miningKeyDown = true;
        ctx.command.playerTeleportCharging = true;
        ctx.command.playerTeleportChargeRemaining = 4.5;
        ctx.command.safeMissionExitPending = true;
        ctx.command.safeMissionExitReady = true;
        ctx.lockedTarget = ctx.ships.get(1);

        CampaignSystem.enforceCampaignMapDiscipline(ctx);

        assertEquals(GameState.MAP, ctx.state);
        assertTrue(ctx.ui.mapOpen);
        assertFalse(ctx.ui.shopOpen);
        assertFalse(ctx.ui.baseMenuOpen);
        assertFalse(ctx.ui.powerManagementOpen);
        assertFalse(ctx.ui.crewStationsOpen);
        assertFalse(ctx.ui.flightDeckOpen);
        assertFalse(ctx.ui.tacticalViewEnabled);
        assertEquals(1, ctx.ships.size(), "campaign map should not keep non-player tactical ships alive");
        assertTrue(ctx.projectiles.isEmpty());
        assertTrue(ctx.asteroids.isEmpty());
        assertTrue(ctx.salvage.isEmpty());
        assertFalse(ctx.firingPrimaryManual);
        assertFalse(ctx.firingPrimaryManualLatched);
        assertFalse(ctx.firingSecondaryManual);
        assertFalse(ctx.firingSecondaryManualLatched);
        assertFalse(ctx.firingPrimaryAuto);
        assertFalse(ctx.firingSecondaryAuto);
        assertFalse(ctx.miningKeyDown);
        assertFalse(ctx.command.playerTeleportCharging);
        assertEquals(0.0, ctx.command.playerTeleportChargeRemaining, 1e-9);
        assertFalse(ctx.command.safeMissionExitPending);
        assertFalse(ctx.command.safeMissionExitReady);
        assertNull(ctx.lockedTarget);
    }

    @Test
    void runtimeCampaignMapTickAppliesDisciplineBeforeCampaignUpdate() throws Exception {
        GameContext ctx = strategicMapContext();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3000.0, 2500.0));
        ctx.projectiles.add(new Bullet(2600.0, 2500.0, 0.0, GameContext.DT, Faction.ENEMY));
        ctx.ui.shopOpen = true;
        ctx.firingPrimaryAuto = true;

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        Method tick = GameSimulationRuntime.class.getDeclaredMethod(
                "tick", double.class, InputSnapshot.class, int.class, int.class);
        tick.setAccessible(true);
        tick.invoke(runtime, GameContext.DT, new InputSnapshot(false, false, false, false, false, 0.0, 0.0), 1280, 720);

        assertEquals(1, ctx.ships.size(), "campaign-map runtime tick should strip tactical ships before overmap update");
        assertTrue(ctx.projectiles.isEmpty(), "campaign-map runtime tick should strip projectiles before overmap update");
        assertFalse(ctx.ui.shopOpen, "campaign-map runtime tick should close tactical-only overlays");
        assertFalse(ctx.firingPrimaryAuto, "campaign-map runtime tick should stop combat firing state");
        assertEquals(GameState.MAP, ctx.state);
    }

    @Test
    void openMapArrowPanMovesMapWithoutMovingBattlefieldCamera() {
        GameContext ctx = strategicMapContext();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.ui.strategicMapFocusX = 2500.0;
        ctx.ui.strategicMapFocusY = 2500.0;
        ctx.cameraPanRight = true;

        CameraSystem.updateManualPan(ctx, 1.0);
        UISystem.updateStrategicMapCameraPan(ctx, 1.0);

        assertEquals(0.0, ctx.cameraOffsetX, 1e-9, "map-focused arrows should not move the battlefield camera");
        assertTrue(UISystem.strategicMapFocusX(ctx) > 2500.0, "map-focused arrows should pan the map");
    }

    @Test
    void closedMapArrowPanMovesBattlefieldCamera() {
        GameContext ctx = strategicMapContext();
        ctx.ui.mapOpen = false;
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.cameraPanRight = true;

        CameraSystem.updateManualPan(ctx, 1.0);

        assertTrue(ctx.cameraOffsetX > 0.0, "battlefield-focused arrows should pan the camera");
    }

    @Test
    void redMissionOuterThreatMarkersKeepRedFactionDespiteRecurringContact() throws Exception {
        CampaignSystem.CampaignLocation kharon = new CampaignSystem.CampaignLocation(
                "poi-14",
                "Red Listening Bastion Kharon",
                2400.0,
                2100.0,
                CampaignSystem.CampaignLocationType.MAIN_MISSION,
                0.72f,
                true,
                14,
                "Red Occupied Zone facility  |  hostile listening post");
        kharon.ownerFaction = Faction.ENEMY;
        kharon.recurringContactId = "MARR";
        kharon.missionOuterThreatSuppression = 0.0;

        Method method = CampaignSystem.class.getDeclaredMethod(
                "missionOuterThreatMarkers", CampaignSystem.CampaignLocation.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CampaignSystem.CampaignSupportMarker> markers =
                (List<CampaignSystem.CampaignSupportMarker>) method.invoke(null, kharon);

        assertFalse(markers.isEmpty(), "Kharon should emit outer-threat support markers");
        assertTrue(markers.stream().allMatch(marker -> marker.faction == Faction.ENEMY),
                "red-owned mission screens should remain red even when a yellow recurring contact exists");
    }

    @Test
    void edgeNonMissionFortressAnchorsAreDemotedAndRoamersMoveInward() throws Exception {
        GameContext ctx = strategicMapContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation left = new CampaignSystem.CampaignLocation(
                "aoi-edge-fort",
                "Red Edge Fort",
                50.0,
                2200.0,
                CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY,
                0.72f,
                false,
                0,
                "Non-mission edge fleet magnet");
        left.facilityType = CampaignSystem.CampaignFacilityType.FORTRESS;
        left.canSpawnFleets = true;
        left.strategicValue = 5;
        CampaignSystem.CampaignLocation center = new CampaignSystem.CampaignLocation(
                "aoi-center",
                "Center Reference",
                2500.0,
                2200.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT,
                0.2f,
                false,
                0,
                "Reference point");
        CampaignSystem.CampaignLocation right = new CampaignSystem.CampaignLocation(
                "aoi-right-reference",
                "Right Reference",
                4950.0,
                2200.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT,
                0.2f,
                false,
                0,
                "Reference point");
        st.galaxyAreasOfInterest.add(left);
        st.galaxyAreasOfInterest.add(center);
        st.galaxyAreasOfInterest.add(right);
        CampaignSystem.GalaxySearchGroup roam = new CampaignSystem.GalaxySearchGroup(
                77,
                "Open-Space Edge Roamer",
                80.0,
                2100.0,
                100.0,
                300.0,
                180.0,
                0.7f,
                CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY,
                4);
        roam.anchorLocationId = "roam-poi-19";
        roam.behavior = CampaignSystem.GalaxySearchBehavior.GUARDING;
        st.galaxySearchGroups.add(roam);

        Method method = CampaignSystem.class.getDeclaredMethod(
                "sanitizeEdgeFleetFortressAnchors", GameContext.class, CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st);

        assertEquals(CampaignSystem.CampaignFacilityType.LISTENING_POST, left.facilityType);
        assertFalse(left.canSpawnFleets);
        assertTrue(left.strategicValue <= 2);
        assertTrue(roam.x > 300.0, "edge roaming search groups should be moved inward");
        assertEquals(CampaignSystem.GalaxySearchBehavior.SEARCHING, roam.behavior);
    }

    private static GameContext strategicMapContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        st.strategicOvermapMode = true;
        ctx.campaign = st;
        ctx.ui.mapOpen = true;
        ctx.state = GameState.MAP;
        return ctx;
    }
}
