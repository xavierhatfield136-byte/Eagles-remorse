import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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
