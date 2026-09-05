import org.junit.jupiter.api.Test;

import app.config.GameConfig;
import app.config.GameMode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void combatHudPanelListOmitsRemovedStrikePanel() {
        List<Rectangle> panels = Renderer.combatHudPanelRects(1280, 720, true, true);

        assertEquals(3, panels.size(), "combat HUD panel list should contain beam, missile, and cloak only");
    }

    @Test
    void primaryHudStackDoesNotOverlapVitalsWhenTargetXrayIsVisibleAt720p() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 77L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1200.0, 1200.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.alive = true;
        ctx.player.dying = false;
        ctx.player.hpMax = Math.max(1000, ctx.player.hpMax);
        ctx.player.hp = ctx.player.hpMax;
        FleetShip target = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 1600.0, 1200.0);
        target.name = "Red Training Cruiser";
        target.alive = true;
        target.dying = false;
        target.hpMax = Math.max(600, target.hpMax);
        target.hp = target.hpMax;
        ctx.lockedTarget = target;
        ctx.ships.add(ctx.player);
        ctx.ships.add(target);
        ctx.ui.hudDetail = GameContext.HudDetail.FULL;

        List<Rectangle> stack = Renderer.primaryHudStackRectsForTests(
                ctx,
                1280,
                720,
                "Current objective",
                "Hold the line and inspect the target.",
                "Use the command strip for orders.",
                "");
        List<Rectangle> vitals = Renderer.combatVitalsRectsForTests(ctx, 1280, 720);

        assertFalse(stack.isEmpty(), "left HUD stack should be present");
        assertFalse(vitals.isEmpty(), "player vitals should be present");
        for (Rectangle stackRect : stack) {
            for (Rectangle vitalRect : vitals) {
                Rectangle padded = new Rectangle(vitalRect);
                padded.grow(6, 4);
                assertFalse(stackRect.intersects(padded),
                        "readiness/objective stack should reserve room beside vitals and X-ray; stack="
                                + stackRect + " vital=" + vitalRect);
            }
        }
    }

    @Test
    void primaryHudStackDoesNotOverlapVitalsWhenOnlyPlayerXrayIsVisibleAt720p() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 78L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 1200.0, 1200.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.alive = true;
        ctx.player.dying = false;
        ctx.player.hpMax = Math.max(1000, ctx.player.hpMax);
        ctx.player.hp = ctx.player.hpMax;
        ctx.ui.hudDetail = GameContext.HudDetail.FULL;

        List<Rectangle> stack = Renderer.primaryHudStackRectsForTests(
                ctx,
                1280,
                720,
                "Trade hub collapse / anchorage firestorm",
                "Keep the flagship alive. Reach Earth.",
                "Use thrust and steering to stay mobile.",
                "");
        List<Rectangle> vitals = Renderer.combatVitalsRectsForTests(ctx, 1280, 720);

        assertFalse(stack.isEmpty(), "left HUD stack should be present");
        assertFalse(vitals.isEmpty(), "player vitals should be present");
        for (Rectangle stackRect : stack) {
            for (Rectangle vitalRect : vitals) {
                Rectangle padded = new Rectangle(vitalRect);
                padded.grow(6, 4);
                assertFalse(stackRect.intersects(padded),
                        "readiness/objective stack should reserve room beside solo player X-ray; stack="
                                + stackRect + " vital=" + vitalRect);
            }
        }
    }

    @Test
    void academyActiveMarkerCueAvoidsTutorialPanelAndCoreMenu() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.TUTORIAL, 5000, 5000, true, 88L, false));
        TutorialSystem.init(ctx, Faction.ALLY);
        CameraSystem.update(ctx, 1280, 720);
        ctx.camX -= 1600.0;
        ctx.camY -= 1200.0;

        Rectangle cue = TutorialSystem.activeMarkerCueRectForTest(ctx, 1280, 720);
        Rectangle panel = TutorialSystem.tutorialOverlayPanelRect(1280, 720, 560, 210);
        Rectangle coreMenu = Renderer.getCoreMenuBarRect(1280, 720);

        assertNotNull(cue, "fresh Academy should expose an active NAV marker cue");
        assertTrue(cue.x >= 0 && cue.y >= 0, "cue should stay on-screen");
        assertTrue(cue.x + cue.width <= 1280 && cue.y + cue.height <= 720, "cue should fit viewport");
        assertFalse(cue.intersects(panel), "cue should not hide under the tutorial panel");
        assertFalse(cue.intersects(coreMenu), "cue should not hide under the command strip");
    }

    @Test
    void cursorWeaponHintsAvoidCombatPanelsAndCoreMenu() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 99L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 640.0, 360.0);
        ctx.player.faction = Faction.ALLY;

        Rectangle hint = Renderer.cursorWeaponHintClusterRectForTests(ctx, 1280, 720, 1110, 640);
        Rectangle coreMenu = Renderer.getCoreMenuBarRect(1280, 720);
        List<Rectangle> panels = Renderer.combatHudPanelRects(1280, 720, false, false);

        assertNotNull(hint, "cursor help should have a measured cluster");
        assertFalse(hint.intersects(coreMenu), "cursor help should not cover the command strip");
        for (Rectangle panel : panels) {
            assertFalse(hint.intersects(panel), "cursor help should not cover weapon controls");
        }
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
    void shopHullPreviewUsesCurrentPlayerFactionSkin() {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);

        player.faction = Faction.ENEMY;
        assertEquals(Faction.ENEMY, Renderer.shopPreviewFactionForPlayer(player));

        player.faction = Faction.TEAM_C;
        assertEquals(Faction.TEAM_C, Renderer.shopPreviewFactionForPlayer(player));

        player.faction = Faction.DARK_YELLOW;
        assertEquals(Faction.TEAM_D, Renderer.shopPreviewFactionForPlayer(player),
                "yellow civil-war factions should use the shared yellow hull catalog");
    }

    @Test
    void gameplayUsesAuthoredFactionTurretSkins() throws Exception {
        assertTrue(Renderer.usesAuthoredTurretSkinsForGameplay(),
                "gameplay should use the authored faction turret PNGs when they are available");

        Class<?> libraryClass = null;
        for (Class<?> nested : Renderer.class.getDeclaredClasses()) {
            if ("TurretSkinLibrary".equals(nested.getSimpleName())) {
                libraryClass = nested;
                break;
            }
        }
        assertNotNull(libraryClass, "renderer should expose the turret skin loader");
        Method getSkin = libraryClass.getDeclaredMethod("getTurretSkin", String.class, ShipRole.class, Faction.class);
        getSkin.setAccessible(true);

        String[] styleKeys = {"twin_gun", "heavy_triple", "missile_pod", "beam_emitter", "stealth_flush", "ciws"};
        for (String styleKey : styleKeys) {
            BufferedImage enemySkin = (BufferedImage) getSkin.invoke(null, styleKey, ShipRole.FRIGATE, Faction.ENEMY);
            assertNotNull(enemySkin, "enemy turret PNG should load for red ships: " + styleKey);
        }

        String[] greenStyleKeys = {"twin_gun", "heavy_triple", "missile_pod", "beam_emitter", "stealth_flush", "ciws"};
        for (String styleKey : greenStyleKeys) {
            BufferedImage greenSkin = (BufferedImage) getSkin.invoke(null, styleKey, ShipRole.FRIGATE, Faction.TEAM_C);
            assertNotNull(greenSkin, "green turret PNG should load for team C ships: " + styleKey);
        }
    }

    @Test
    void missilePodsAndCiwsUseReducedGameplaySpriteScale() {
        assertEquals(0.50, Renderer.missilePodTurretSpriteScaleForTests(), 0.001,
                "missile launcher sprites should be half-size so they do not swamp hull art");
        assertEquals(0.50, Renderer.ciwsTurretSpriteScaleForTests(), 0.001,
                "CIWS sprites should be half-size so they remain readable but compact");
    }

    @Test
    void gameplayTurretsRenderOnlyWhenShipIsReadableOnScreen() {
        BufferedImage image = new BufferedImage(220, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
            assertFalse(Renderer.shouldRenderGameplayTurretsForShip(g2, frigate),
                    "frigate turrets should be hidden when the hull is too small to inspect");

            g2.scale(2.0, 2.0);
            assertTrue(Renderer.shouldRenderGameplayTurretsForShip(g2, frigate),
                    "zooming in should restore readable turret art");
            assertTrue(Renderer.tacticalShipScreenDiameterForTurretRendering(g2, frigate)
                    >= Renderer.gameplayTurretRenderMinScreenDiameterForTests());
        } finally {
            g2.dispose();
        }
    }

    @Test
    void xrayRoomIntegrityColorRampsFromGreenToBlack() {
        Color healthy = Renderer.xrayRoomIntegrityFillColorForTests(1.0);
        Color yellow = Renderer.xrayRoomIntegrityFillColorForTests(0.60);
        Color orange = Renderer.xrayRoomIntegrityFillColorForTests(0.30);
        Color red = Renderer.xrayRoomIntegrityFillColorForTests(0.10);
        Color destroyed = Renderer.xrayRoomIntegrityFillColorForTests(0.0);

        assertTrue(healthy.getGreen() > healthy.getRed(), "healthy rooms should read green");
        assertTrue(yellow.getRed() > 200 && yellow.getGreen() > 180 && yellow.getBlue() < 100,
                "moderately damaged rooms should read yellow");
        assertTrue(orange.getRed() > orange.getGreen() && orange.getGreen() > orange.getBlue(),
                "heavily damaged rooms should read orange");
        assertTrue(red.getRed() > red.getGreen() * 2 && red.getRed() > red.getBlue() * 2,
                "critical rooms should read red");
        assertEquals(0, destroyed.getRed(), "destroyed rooms should be black");
        assertEquals(0, destroyed.getGreen(), "destroyed rooms should be black");
        assertEquals(0, destroyed.getBlue(), "destroyed rooms should be black");
    }

    @Test
    void factionHullLightingDoesNotPaintTransparentSpritePadding() throws Exception {
        Class<?> shipRenderer = null;
        for (Class<?> nested : Renderer.class.getDeclaredClasses()) {
            if ("ShipRenderer".equals(nested.getSimpleName())) {
                shipRenderer = nested;
                break;
            }
        }
        assertNotNull(shipRenderer, "renderer should expose the ship renderer");
        Method lighting = shipRenderer.getDeclaredMethod("applyFactionSkinLighting",
                Graphics2D.class, Rectangle2D.class, Faction.class, Color.class, Color.class);
        lighting.setAccessible(true);

        BufferedImage image = new BufferedImage(48, 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(new Color(210, 220, 225, 255));
            g2.fillRect(21, 12, 6, 5);
            lighting.invoke(null, g2, new Rectangle2D.Double(0.0, 0.0, image.getWidth(), image.getHeight()),
                    Faction.TEAM_C, new Color(180, 210, 190), new Color(120, 245, 205));
        } finally {
            g2.dispose();
        }

        assertEquals(0, (image.getRGB(3, 3) >>> 24) & 0xff,
                "hull lighting should not create visible hitbox haze in transparent sprite padding");
        assertTrue(((image.getRGB(23, 14) >>> 24) & 0xff) > 0,
                "hull lighting should still affect existing hull pixels");
    }

    @Test
    void auxiliaryHullSkinLayersDoNotPaintTransparentSpritePadding() throws Exception {
        Class<?> shipRenderer = null;
        for (Class<?> nested : Renderer.class.getDeclaredClasses()) {
            if ("ShipRenderer".equals(nested.getSimpleName())) {
                shipRenderer = nested;
                break;
            }
        }
        assertNotNull(shipRenderer, "renderer should expose the ship renderer");
        Method drawLayerAtop = shipRenderer.getDeclaredMethod("drawSkinLayerAtop",
                Graphics2D.class, BufferedImage.class, int.class, int.class, int.class, int.class, float.class);
        drawLayerAtop.setAccessible(true);

        BufferedImage base = new BufferedImage(48, 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = base.createGraphics();
        BufferedImage overlay = new BufferedImage(48, 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = overlay.createGraphics();
        try {
            g2.setColor(new Color(210, 220, 225, 255));
            g2.fillRect(21, 12, 6, 5);

            og.setColor(new Color(80, 255, 200, 255));
            og.fillRect(0, 0, overlay.getWidth(), overlay.getHeight());

            drawLayerAtop.invoke(null, g2, overlay, 0, 0, overlay.getWidth(), overlay.getHeight(), 1.0f);
        } finally {
            og.dispose();
            g2.dispose();
        }

        assertEquals(0, (base.getRGB(3, 3) >>> 24) & 0xff,
                "auxiliary hull layers should not create haze in transparent sprite padding");
        assertTrue(((base.getRGB(23, 14) >>> 24) & 0xff) > 0,
                "auxiliary hull layers should still modify existing hull pixels");
    }

    @Test
    void gameplayTurretSpritesUseRequestedHalfScale() {
        assertEquals(0.22, Renderer.gameplayTurretGlobalScaleForTests(), 1e-9,
                "gameplay weapon sprites should render at 50% of their prior 0.44 global scale");
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
        int expectedPanels = cloak ? 3 : 2;
        assertEquals(expectedPanels, panels.size(),
                "expected only beam, missile, and optional cloak panels at " + viewW + "x" + viewH);
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
