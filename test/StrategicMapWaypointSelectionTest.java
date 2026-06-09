import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

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
