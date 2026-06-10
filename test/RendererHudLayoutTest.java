import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
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
