import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererHudLayoutTest {

    @Test
    void combatHudPanelsAvoidCoreMenuAndEachOtherAtAlphaViewSizes() {
        assertPanelLayoutReadable(1280, 720, true, true);
        assertPanelLayoutReadable(1024, 576, true, true);
        assertPanelLayoutReadable(900, 540, false, true);
    }

    @Test
    void tutorialPanelAvoidsCoreMenuAtAlphaViewSize() {
        Rectangle panel = TutorialSystem.tutorialOverlayPanelRect(1280, 720, 350, 360);
        Rectangle coreMenu = Renderer.getCoreMenuBarRect(1280, 720);

        assertFalse(panel.intersects(coreMenu), "tutorial hints should not cover the core action bar");
        assertTrue(panel.x >= 0 && panel.y >= 0, "tutorial panel should stay on-screen");
        assertTrue(panel.x + panel.width <= 1280, "tutorial panel should fit horizontally");
        assertTrue(panel.y + panel.height <= 720, "tutorial panel should fit vertically");
    }

    @Test
    void strikePanelSitsAboveBeamWhenRoomAllows() {
        List<Rectangle> panels = Renderer.combatHudPanelRects(1280, 720, true, true);
        Rectangle beam = panels.get(0);
        Rectangle strike = panels.get(panels.size() - 1);

        assertTrue(strike.y + strike.height <= beam.y,
                "strike menu should sit above the beam mode menu when there is room");
    }

    @Test
    void powerManagementOverlayRendersInsideAlphaView() {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setClip(0, 0, image.getWidth(), image.getHeight());
            Renderer.drawPowerManagementOverlay(g2, new Player(ShipRole.MOTHERSHIP, 640.0, 360.0), 0);
        } finally {
            g2.dispose();
        }

        assertTrue(image.getRGB(image.getWidth() / 2, image.getHeight() / 2) != 0,
                "power management overlay should paint visible panel content");
    }

    @Test
    void shieldFxOnlyRendersDuringRecentDamageFlash() throws Exception {
        FleetShip ship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);
        ship.shieldActive = true;
        ship.shieldMax = Math.max(100.0, ship.shieldMax);
        ship.shield = ship.shieldMax;
        ship.resetShieldState();

        BufferedImage image = new BufferedImage(360, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Method shouldRender = Renderer.class.getDeclaredMethod(
                "shouldRenderShieldFx", Ship.class, Rectangle2D.class, Graphics2D.class);
        shouldRender.setAccessible(true);
        Rectangle2D bounds = new Rectangle2D.Double(0.0, 0.0, 180.0, 180.0);
        try {
            assertFalse((Boolean) shouldRender.invoke(null, ship, bounds, g2),
                    "idle shields should not paint a standing aura over the hull");

            ship.drainShieldByAmount(12.0, ship.x + 80.0, ship.y, -120.0, 0.0);
            assertTrue((Boolean) shouldRender.invoke(null, ship, bounds, g2),
                    "fresh shield damage should flash the shield effect");

            ship.update(1.35);
            assertFalse((Boolean) shouldRender.invoke(null, ship, bounds, g2),
                    "shield impact marks should not leave a lingering colored aura");
        } finally {
            g2.dispose();
        }
    }

    @Test
    void tacticalShipViewDoesNotDrawIdleShieldCircle() throws Exception {
        FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 80.0, 80.0);
        ship.shieldActive = true;
        ship.shieldMax = Math.max(80.0, ship.shieldMax);
        ship.shield = ship.shieldMax;
        ship.resetShieldState();

        BufferedImage image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Method drawTacticalShip = Renderer.class.getDeclaredMethod("drawTacticalShip", Graphics2D.class, Ship.class);
        drawTacticalShip.setAccessible(true);
        try {
            drawTacticalShip.invoke(null, g2, ship);
        } finally {
            g2.dispose();
        }

        int oldCircleX = (int) Math.round(ship.x + Math.max(8.0, (ship.radius + 12.0) * 1.08));
        int oldCircleY = (int) Math.round(ship.y);
        int alpha = (image.getRGB(oldCircleX, oldCircleY) >>> 24) & 0xff;
        assertTrue(alpha < 16, "idle tactical shields should not draw the old always-on circular aura");
    }

    @Test
    void commissioningCardsFitInsideEnlargedHullArea() throws Exception {
        Rectangle panel = Renderer.getShopOverlayRect(1280, 720);
        Method hullAreaMethod = Renderer.class.getDeclaredMethod("getShopHullArea", Rectangle.class);
        hullAreaMethod.setAccessible(true);
        Method cardMethod = Renderer.class.getDeclaredMethod("getShopHullCardRect", Rectangle.class, int.class);
        cardMethod.setAccessible(true);
        Rectangle hullArea = (Rectangle) hullAreaMethod.invoke(null, panel);

        for (int slot = 0; slot < 6; slot++) {
            Rectangle card = (Rectangle) cardMethod.invoke(null, panel, slot);
            assertTrue(hullArea.contains(card), "commissioning card should stay inside the hull bay");
        }
    }

    @Test
    void shopUpgradeCardsFitInsideEnlargedUpgradeArea() throws Exception {
        Rectangle panel = Renderer.getShopOverlayRect(1280, 720);
        Method upgradeAreaMethod = Renderer.class.getDeclaredMethod("getShopUpgradeArea", Rectangle.class);
        upgradeAreaMethod.setAccessible(true);
        Method cardMethod = Renderer.class.getDeclaredMethod("getShopUpgradeCardRect", Rectangle.class, int.class);
        cardMethod.setAccessible(true);
        Rectangle upgradeArea = (Rectangle) upgradeAreaMethod.invoke(null, panel);

        for (int slot = 0; slot < 7; slot++) {
            Rectangle card = (Rectangle) cardMethod.invoke(null, panel, slot);
            assertTrue(upgradeArea.contains(card), "upgrade card should stay inside the upgrade bay");
        }
    }

    @Test
    void formationMenuFitsAllFormationCardsInPanel() {
        Rectangle panel = Renderer.formationMenuRect(1280, 720);

        for (GameContext.FleetFormation formation : GameContext.FleetFormation.values()) {
            Rectangle card = Renderer.formationMenuOptionRect(1280, 720, formation);
            assertTrue(panel.contains(card), "formation card should stay inside the menu: " + formation);
        }
    }

    private static void assertPanelLayoutReadable(int viewW, int viewH, boolean cloak, boolean strike) {
        List<Rectangle> panels = Renderer.combatHudPanelRects(viewW, viewH, cloak, strike);
        Rectangle coreMenu = Renderer.getCoreMenuBarRect(viewW, viewH);
        assertTrue(panels.size() >= 3, "expected combat mode panels and strike panel at " + viewW + "x" + viewH);
        for (Rectangle panel : panels) {
            assertTrue(panel.x >= 0 && panel.y >= 0, "panel should stay on-screen");
            assertTrue(panel.x + panel.width <= viewW, "panel should fit horizontally");
            assertTrue(panel.y + panel.height <= viewH, "panel should fit vertically");
            assertFalse(panel.intersects(coreMenu), "combat HUD panel should not crowd the core menu");
        }
        for (int i = 0; i < panels.size(); i++) {
            for (int j = i + 1; j < panels.size(); j++) {
                assertFalse(panels.get(i).intersects(panels.get(j)),
                        "combat HUD panels should not overlap each other");
            }
        }
    }
}
