import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererHoverTooltipTest {

    @Test
    void coreMenuButtonsExposeReadableHoverDescriptions() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        Rectangle shopButton = Renderer.getCoreMenuButtonRect(1280, 720, 0);

        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(
                ctx,
                1280,
                720,
                shopButton.x + shopButton.width / 2,
                shopButton.y + shopButton.height / 2);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.contains("SHOP"));
        assertTrue(tooltip.body.contains("Shop and loadout controls"));
    }

    @Test
    void hoveredShipsExposeRoleAndVitals() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2400.0, 2400.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        Ship enemy = new FleetShip(ShipRole.BATTLECRUISER, Faction.ENEMY, 2520.0, 2400.0);
        enemy.name = "Red Spear";
        ctx.ships.add(enemy);
        ctx.cursorWorldX = enemy.x;
        ctx.cursorWorldY = enemy.y;

        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(ctx, 1280, 720, 200, 200);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.contains("Red Spear"));
        assertTrue(tooltip.body.contains("Role: BATTLECRUISER"));
        assertTrue(tooltip.body.contains("Hull"));
    }

    @Test
    void objectiveCardHoverShowsFullObjectiveText() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2400.0, 2400.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        BufferedImage canvas = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        String objectiveTitle = "Secure Gamma Relay and escort the coalition command ship through the corridor";
        String objectiveDetail = "Hold formation around the allied flagship, keep raiders off its flank, and preserve the hull while the warp corridor stabilizes for extraction.";
        try {
            Renderer.drawHUD(
                    g2,
                    ctx.player,
                    ctx.credits,
                    3,
                    false,
                    false,
                    false,
                    null,
                    0,
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    ctx.resourceGoal,
                    "",
                    objectiveTitle,
                    objectiveDetail,
                    "",
                    0.0,
                    1.0,
                    0.0,
                    1.0,
                    0.0,
                    ctx.camX,
                    ctx.camY,
                    1280,
                    720,
                    1.0,
                    "",
                    ctx,
                    GameContext.HudDetail.MINIMAL,
                    "",
                    "");
        } finally {
            g2.dispose();
        }

        assertNotNull(ctx.ui.objectiveHoverRect);
        Rectangle objectiveCard = ctx.ui.objectiveHoverRect;
        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(
                ctx,
                1280,
                720,
                objectiveCard.x + objectiveCard.width / 2,
                objectiveCard.y + objectiveCard.height / 2);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.contains("OBJECTIVE"));
        assertTrue(tooltip.body.contains(objectiveTitle));
        assertTrue(tooltip.body.contains("warp corridor stabilizes"));
    }

    @Test
    void tacticalViewUsesBlackBackgroundAndGeometricShipSilhouettes() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.ui.tacticalViewEnabled = true;

        Ship ship = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 110.0, 110.0);
        ship.name = "Blue Spear";

        BufferedImage canvas = new BufferedImage(220, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            Renderer.drawSpaceBackground(g2, ctx, 0.0, 0.0, 220, 220, 1234L);
            Renderer.drawTacticalShips(g2, java.util.List.of(ship), 0.0, 0.0, 220.0, 220.0);
        } finally {
            g2.dispose();
        }

        assertTrue((canvas.getRGB(5, 5) & 0x00FFFFFF) == 0, "tactical view should black out the background");

        boolean foundShipPixel = false;
        for (int y = 70; y <= 150 && !foundShipPixel; y++) {
            for (int x = 70; x <= 150; x++) {
                if ((canvas.getRGB(x, y) & 0x00FFFFFF) != 0) {
                    foundShipPixel = true;
                    break;
                }
            }
        }
        assertTrue(foundShipPixel, "tactical ship rendering should still draw a visible geometric silhouette");
    }

    @Test
    void tacticalViewSkipsHeavyWorldLayersToPreserveFps() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.ui.tacticalViewEnabled = true;
        ctx.player = new Player(ShipRole.FRIGATE, 2400.0, 2400.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        Ship enemy = new FleetShip(ShipRole.BATTLECRUISER, Faction.ENEMY, 2520.0, 2400.0);
        ctx.ships.add(enemy);
        ctx.asteroids.add(new Asteroid(2450.0, 2380.0, 90.0, 400));
        ctx.salvage.add(new Salvage(2460.0, 2420.0, 25, 12, 20.0));
        ctx.projectiles.add(new Bullet(2440.0, 2400.0, 0.0, 1.0 / 60.0, Faction.ENEMY));
        ctx.camX = 2100.0;
        ctx.camY = 2040.0;

        BufferedImage canvas = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            GameRenderSystem.render(ctx, g2, 1280, 720);
        } finally {
            g2.dispose();
        }

        assertTrue(ctx.perf.drawnShips > 0, "tactical view should still render ship silhouettes");
        assertEquals(0, ctx.perf.drawnAsteroids, "tactical view should skip asteroid rendering for FPS");
        assertEquals(0, ctx.perf.drawnSalvage, "tactical view should skip salvage rendering for FPS");
        assertEquals(0, ctx.perf.drawnProjectiles, "tactical view should skip projectile rendering for FPS");
    }
}
