import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicMapWaypointSelectionTest {

    @Test
    void sameSectorMapClickKeepsExactClickedPoint() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 1500.0, 3000.0);
        ctx.player.faction = Faction.ALLY;
        BattlefieldSectorSystem.ensureLoadedSector(ctx);

        BattlefieldSectorSystem.SectorDefinition loaded = BattlefieldSectorSystem.loadedSector(ctx);
        assertNotNull(loaded);

        int viewW = 1280;
        int viewH = 720;
        Rectangle rect = Renderer.getStrategicMapInnerRect(viewW, viewH);
        double clickedWorldX = 4200.0;
        double clickedWorldY = 2150.0;
        int clickX = rect.x + (int) Math.round((clickedWorldX / ctx.WORLD_W) * rect.width);
        int clickY = rect.y + (int) Math.round((clickedWorldY / ctx.WORLD_H) * rect.height);
        double expectedX = GameMath.clamp(((clickX - rect.x) / (double) rect.width) * ctx.WORLD_W, 0, ctx.WORLD_W);
        double expectedY = GameMath.clamp(((clickY - rect.y) / (double) rect.height) * ctx.WORLD_H, 0, ctx.WORLD_H);

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

        assertEquals(loaded.id, BattlefieldSectorSystem.selectedSector(ctx).id);
        assertEquals(expectedX, ctx.ui.waypointX, 1e-6);
        assertEquals(expectedY, ctx.ui.waypointY, 1e-6);
    }

    @Test
    void missionWarpHopUsesOrthogonalNeighborInsteadOfDiagonalStep() {
        int source = CampaignSystem.missionSubzoneIndex(0, 1); // A2
        int target = CampaignSystem.missionSubzoneIndex(1, 2); // B3

        int hop = CampaignSystem.nextCampaignWarpHop(source, target);

        assertTrue(hop == CampaignSystem.missionSubzoneIndex(1, 1)
                        || hop == CampaignSystem.missionSubzoneIndex(0, 2),
                "warp hops should advance through an orthogonally adjacent subzone");
    }

    @Test
    void unifiedMissionSpaceAllowsDirectFireAcrossFormerSubzoneBorders() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.introSequenceActive = false;
        ctx.campaign.sectorElapsed = 1.0;

        Ship friendly = new FleetShip(ShipRole.FRIGATE, Faction.ALLY,
                CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(0, 1)),
                CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(0, 1)));
        Ship hostile = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY,
                CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(1, 1)),
                CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(1, 1)));

        assertTrue(CampaignSystem.missionSubzonesAllowDirectFire(ctx, friendly, hostile));

        hostile.x = CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(0, 1));
        hostile.y = CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(0, 1));
        hostile.campaignMissionSubzone = -1;

        assertTrue(CampaignSystem.missionSubzonesAllowDirectFire(ctx, friendly, hostile));
    }

    @Test
    void unifiedMissionSpaceDoesNotClampPlayerAtFormerSouthGridEdge() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.sectorElapsed = 1.0;
        ctx.player.x = CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(0, 1));
        ctx.player.y = CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(0, 2)) + 3200.0;
        double southOfFormerGrid = ctx.player.y;

        PhysicsSystem.update(ctx, GameContext.DT);

        assertEquals(southOfFormerGrid, ctx.player.y, 1e-6,
                "unified combat space should not snap the player back to the old mission-grid south edge");
    }

    @Test
    void tacticalMissionMapDefaultsToLoadedPocketInsteadOfWholeWorld() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.introSequenceActive = false;

        ctx.ui.mapOpen = true;
        ctx.state = GameState.MAP;
        UISystem.resetStrategicMapZoom(ctx);

        assertTrue(ctx.ui.mapOpen);
        assertTrue(UISystem.strategicMapZoom(ctx) > 1.5, "mission map should open closer than full-world scale");
        double focusY = UISystem.strategicMapWorldMinY(ctx) + UISystem.strategicMapViewHeight(ctx) * 0.5;
        assertEquals(ctx.player.y, focusY, CampaignSystem.missionSubzoneHeight(ctx) * 0.4);
    }

    @Test
    void tacticalMissionMapCanZoomOutBeyondLoadedPocketAndKeepRemoteFocus() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.introSequenceActive = false;

        ctx.ui.mapOpen = true;
        ctx.state = GameState.MAP;
        UISystem.resetStrategicMapZoom(ctx);
        double initialZoom = UISystem.strategicMapZoom(ctx);
        double initialViewW = UISystem.strategicMapViewWidth(ctx);

        for (int i = 0; i < 5; i++) {
            UISystem.stepStrategicMapZoom(ctx, -1, 640, 360, 1280, 720);
        }

        assertTrue(UISystem.strategicMapZoom(ctx) < initialZoom, "mission map should allow additional zoom-out");
        assertTrue(UISystem.strategicMapViewWidth(ctx) > initialViewW, "zooming out should reveal more mission space");

        double remoteX = Math.min(ctx.WORLD_W - 100.0, ctx.player.x + CampaignSystem.missionSubzoneWidth(ctx) * 1.6);
        double remoteY = Math.min(ctx.WORLD_H - 100.0, ctx.player.y + CampaignSystem.missionSubzoneHeight(ctx) * 0.9);
        ctx.ui.strategicMapFocusX = remoteX;
        ctx.ui.strategicMapFocusY = remoteY;

        double halfW = UISystem.strategicMapViewWidth(ctx) * 0.5;
        double halfH = UISystem.strategicMapViewHeight(ctx) * 0.5;
        double expectedFocusX = GameMath.clamp(remoteX, halfW, Math.max(halfW, ctx.WORLD_W - halfW));
        double expectedFocusY = GameMath.clamp(remoteY, halfH, Math.max(halfH, ctx.WORLD_H - halfH));
        assertEquals(expectedFocusX, UISystem.strategicMapFocusX(ctx), 1e-6);
        assertEquals(expectedFocusY, UISystem.strategicMapFocusY(ctx), 1e-6);
    }

    @Test
    void fleetSensorReadoutListsMissionOrePatchAndClickSetsWaypoint() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.introSequenceActive = false;
        ctx.ui.mapOpen = false;
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = false;
        ctx.ui.powerManagementOpen = false;
        ctx.ui.crewStationsOpen = false;
        ctx.ui.flightDeckOpen = false;
        ctx.state = GameState.RUNNING;

        double oreX = GameMath.clamp(ctx.player.x + 520.0, 500.0, ctx.WORLD_W - 500.0);
        double oreY = GameMath.clamp(ctx.player.y + 360.0, 500.0, ctx.WORLD_H - 500.0);
        ctx.asteroids.clear();
        ctx.asteroids.add(new Asteroid(oreX, oreY, 54.0, 900));
        ctx.asteroids.add(new Asteroid(oreX + 120.0, oreY + 80.0, 42.0, 700));

        List<GameRenderSystem.SensorNetEntry> entries = GameRenderSystem.sensorNetEntries(ctx, 4, 2);
        assertTrue(entries.stream().anyMatch(entry -> entry.title.startsWith("Ore Patch")),
                "sensor net should list mineable ore patches in the current mission");

        int viewW = 1280;
        int viewH = 720;
        boolean clickedOrePatch = false;
        for (int y = 20; y < 220 && !clickedOrePatch; y += 2) {
            ctx.ui.waypointX = Double.NaN;
            ctx.ui.waypointY = Double.NaN;
            ctx.ui.mapOpen = false;
            ctx.state = GameState.RUNNING;
            MouseEvent click = new MouseEvent(
                    new Canvas(),
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    viewW - 150,
                    y,
                    1,
                    false,
                    MouseEvent.BUTTON1
            );
            if (UISystem.handleFleetNetClick(ctx, click, viewW, viewH)
                    && Double.isFinite(ctx.ui.waypointX)
                    && Math.hypot(ctx.ui.waypointX - oreX, ctx.ui.waypointY - oreY) < 260.0) {
                clickedOrePatch = true;
            }
        }

        assertTrue(clickedOrePatch, "clicking an ore patch row should route the waypoint to that patch");
        assertTrue(ctx.ui.mapOpen, "clicking a sensor row should open the map focused on the routed patch");
    }

    @Test
    void unifiedMissionSpaceWarpsDirectlyToBaseWithoutStagingHop() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.introSequenceActive = false;
        ctx.campaign.sectorElapsed = 1.0;
        UISystem.closeAllOverlays(ctx);
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;
        Ship base = new FleetShip(ShipRole.BASE, ctx.player.faction,
                CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(5, 1)),
                CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, CampaignSystem.missionSubzoneIndex(5, 1)));
        ctx.ships.add(base);
        ctx.teamBases.put(ctx.player.faction, base);

        assertNotNull(base);
        assertTrue(GameplayActions.canIssueCombatAction(ctx));

        GameplayActions.tryTeleportToBase(ctx);

        assertTrue(ctx.player.isWarpCharging());
        assertEquals(base.x, ctx.player.warpExitX(), 1e-6);
        assertEquals(base.y, ctx.player.warpExitY(), 1e-6);
    }
}
