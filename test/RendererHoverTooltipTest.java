import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RendererHoverTooltipTest {

    @Test
    void coreMenuButtonsExposeReadableHoverDescriptions() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        Rectangle blueButton = Renderer.getCoreMenuButtonRect(1280, 720, 0);

        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(
                ctx,
                1280,
                720,
                blueButton.x + blueButton.width / 2,
                blueButton.y + blueButton.height / 2);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.contains("BLUE"));
        assertTrue(tooltip.body.contains("Blue team showcase ships"));
    }

    @Test
    void tacticalEntryModalSuppressesUnderlyingHoverTooltip() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        Rectangle blueButton = Renderer.getCoreMenuButtonRect(1280, 720, 0);
        int mouseX = blueButton.x + blueButton.width / 2;
        int mouseY = blueButton.y + blueButton.height / 2;

        assertNotNull(Renderer.hoverTooltipAt(ctx, 1280, 720, mouseX, mouseY));

        ctx.ui.strategicEncounterPrompt.active = true;
        ctx.ui.strategicEncounterPrompt.kind = UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE;
        ctx.ui.strategicEncounterPrompt.title = "HOSTILE FORCE";
        ctx.ui.strategicEncounterPrompt.body = "Pre-battle deployment preview";

        assertNull(Renderer.hoverTooltipAt(ctx, 1280, 720, mouseX, mouseY));
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
    void gameplayHudTextAvoidsDenseInternalShorthand() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2400.0, 2400.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        Ship enemy = new FleetShip(ShipRole.HAULER, Faction.ENEMY, 2600.0, 2400.0);
        enemy.name = "Red Hauler";
        ctx.ships.add(enemy);
        ctx.lockedTarget = enemy;

        Method commandLinesMethod = Renderer.class.getDeclaredMethod(
                "buildCommandStatusLines",
                Player.class,
                int.class,
                boolean.class,
                boolean.class,
                int.class,
                int.class,
                int.class,
                double.class,
                double.class,
                double.class,
                double.class,
                String.class,
                GameContext.HudDetail.class,
                GameContext.class);
        commandLinesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> commandLines = (List<String>) commandLinesMethod.invoke(
                null,
                ctx.player,
                3,
                false,
                false,
                0,
                0,
                ctx.resourceGoal,
                1.0,
                0.0,
                1.0,
                0.0,
                "",
                GameContext.HudDetail.FULL,
                ctx);

        BufferedImage canvas = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        FontMetrics metrics;
        try {
            metrics = g2.getFontMetrics(new Font("Consolas", Font.PLAIN, 12));
        } finally {
            g2.dispose();
        }

        Method shipLinesMethod = Renderer.class.getDeclaredMethod(
                "buildShipSystemNoteLines",
                Player.class,
                Ship.class,
                int.class,
                int.class,
                String.class,
                String.class,
                String.class,
                GameContext.HudDetail.class,
                FontMetrics.class,
                int.class,
                GameContext.class);
        shipLinesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> shipLines = (List<String>) shipLinesMethod.invoke(
                null,
                ctx.player,
                enemy,
                0,
                0,
                "STATIONS C:AI H:MAN",
                "OVERLAY: POWER MANAGEMENT",
                "",
                GameContext.HudDetail.FULL,
                metrics,
                260,
                ctx);

        String combined = String.join("\n", commandLines) + "\n" + String.join("\n", shipLines);
        assertTrue(combined.contains("Shooting Range"));
        assertTrue(combined.contains("Shipyard Tier 3"));
        assertTrue(combined.contains("Hull"));
        assertTrue(combined.contains("Order:"));
        assertTrue(combined.contains("Target: Red Hauler"));
        assertFalse(combined.contains("SHOOTING_RANGE"));
        assertFalse(combined.contains("Fleet: E"));
        assertFalse(combined.contains("Command: Titans"));
        assertFalse(combined.contains("Comms: I"));
        assertFalse(combined.contains("STATIONS"));
        assertFalse(combined.contains("OVERLAY:"));
    }

    @Test
    void campaignActionButtonsExposeFullHoverDescriptions() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;

        int viewW = 1280;
        int viewH = 720;
        Rectangle panel = Renderer.getStrategicMapSidebarRect(viewW, viewH, true);
        Method actionRect = Renderer.class.getDeclaredMethod("galaxyActionRect", GameContext.class, Rectangle.class, String.class);
        actionRect.setAccessible(true);
        Rectangle rect = (Rectangle) actionRect.invoke(null, ctx, panel, "SIGNAL_SWEEP");

        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(
                ctx,
                viewW,
                viewH,
                rect.x + rect.width / 2,
                rect.y + rect.height / 2);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.toUpperCase().contains("SWEEP"));
        assertFalse(tooltip.body.isBlank());
    }

    @Test
    void fleetOverlayModeTabsExposeHoverDescriptions() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);
        ctx.ui.shopOpen = true;

        Method modeRect = Renderer.class.getDeclaredMethod("getFleetOverlayModeTabRect", Rectangle.class, boolean.class);
        modeRect.setAccessible(true);
        Rectangle panel = Renderer.getShopOverlayRect(1280, 720);
        Rectangle refit = (Rectangle) modeRect.invoke(null, panel, true);

        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(
                ctx,
                1280,
                720,
                refit.x + refit.width / 2,
                refit.y + refit.height / 2);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.toUpperCase().contains("REFIT"));
        assertTrue(tooltip.body.contains("weapon mounts"));
    }

    @Test
    void fleetCommissioningColumnsStartBelowDoctrineStrip() throws Exception {
        Method upgradeArea = Renderer.class.getDeclaredMethod("getShopUpgradeArea", Rectangle.class);
        upgradeArea.setAccessible(true);
        Rectangle panel = Renderer.getShopOverlayRect(1280, 720);
        Rectangle columns = (Rectangle) upgradeArea.invoke(null, panel);

        int doctrineStripBottom = panel.y + 116 + 28;
        assertTrue(columns.y > doctrineStripBottom);
    }

    @Test
    void hoverTooltipRevealCanBeImmediate() {
        UiState ui = new UiState();
        long start = 1_000_000_000L;

        ui.updateHoverTooltip("test", "Title", "Body", 100, 100, start, 0L);
        assertFalse(ui.hoverTooltipVisible);

        ui.updateHoverTooltip("test", "Title", "Body", 100, 100, start + 1L, 0L);
        assertTrue(ui.hoverTooltipVisible);
    }

    @Test
    void supportFleetMarkersUseFactionColorInsteadOfGenericHazardRed() throws Exception {
        CampaignSystem.CampaignSupportMarker marker = new CampaignSystem.CampaignSupportMarker(
                CampaignSystem.SupportMarkerType.HAZARD,
                "Yellow Screen",
                "Waiting fleet",
                Faction.TEAM_D,
                100.0,
                100.0,
                120.0,
                50
        );

        Method colorMethod = Renderer.class.getDeclaredMethod(
                "strategicSupportMarkerColor",
                CampaignSystem.CampaignSupportMarker.class
        );
        colorMethod.setAccessible(true);
        java.awt.Color actual = (java.awt.Color) colorMethod.invoke(null, marker);
        Method factionColorMethod = Renderer.class.getDeclaredMethod(
                "factionMapColor",
                Faction.class,
                boolean.class,
                int.class
        );
        factionColorMethod.setAccessible(true);
        java.awt.Color expected = (java.awt.Color) factionColorMethod.invoke(null, Faction.TEAM_D, false, 220);

        assertEquals(expected, actual);
    }

    @Test
    void missionOuterThreatMarkersInheritSiteFactionColor() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        CampaignSystem.CampaignLocation location = ctx.campaign.galaxyMainPois.stream()
                .filter(loc -> loc != null && loc.name != null && loc.name.startsWith("Green "))
                .findFirst()
                .orElseThrow();

        Method markerMethod = CampaignSystem.class.getDeclaredMethod(
                "missionOuterThreatMarkers",
                CampaignSystem.CampaignLocation.class
        );
        markerMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<CampaignSystem.CampaignSupportMarker> markers =
                (java.util.List<CampaignSystem.CampaignSupportMarker>) markerMethod.invoke(null, location);

        assertFalse(markers.isEmpty());
        assertEquals(Faction.TEAM_C, markers.get(0).faction);
        assertTrue(markers.size() >= 4);
    }

    @Test
    void recurringContactMarkersUseContactFactionInsteadOfBlueAllyFallback() throws Exception {
        CampaignSystem.CampaignLocation location = new CampaignSystem.CampaignLocation(
                "test-voss",
                "Neutral Holding",
                0.0,
                0.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT,
                0.0f,
                false,
                0,
                ""
        );
        location.recurringContactId = "VOSS";
        location.recurringContactStatus = "rescue net loyal and answering your route";

        java.util.ArrayList<CampaignSystem.CampaignSupportMarker> markers = new java.util.ArrayList<>();
        Method method = CampaignSystem.class.getDeclaredMethod(
                "addDynamicTheaterMarkers",
                java.util.ArrayList.class,
                CampaignSystem.CampaignLocation.class
        );
        method.setAccessible(true);
        method.invoke(null, markers, location);

        CampaignSystem.CampaignSupportMarker contact = markers.stream()
                .filter(marker -> marker != null && "Captain Nadi Voss".equals(marker.label))
                .findFirst()
                .orElseThrow();

        assertEquals(Faction.TEAM_C, contact.faction);
    }

    @Test
    void neutralMissionOuterThreatMarkersResolveHostileRedInsteadOfBlueAlly() throws Exception {
        CampaignSystem.CampaignLocation location = new CampaignSystem.CampaignLocation(
                "neutral-main",
                "Contract Shipworks Myr",
                0.0,
                0.0,
                CampaignSystem.CampaignLocationType.MAIN_MISSION,
                0.35f,
                true,
                3,
                "Independent contract world"
        );

        Method markerMethod = CampaignSystem.class.getDeclaredMethod(
                "missionOuterThreatMarkers",
                CampaignSystem.CampaignLocation.class
        );
        markerMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<CampaignSystem.CampaignSupportMarker> markers =
                (java.util.List<CampaignSystem.CampaignSupportMarker>) markerMethod.invoke(null, location);

        assertFalse(markers.isEmpty());
        assertEquals(Faction.ENEMY, markers.get(0).faction);
    }

    @Test
    void redTeamObjectiveMarkersUseRedFactionOutline() throws Exception {
        CampaignSystem.CampaignObjectiveMarker marker = new CampaignSystem.CampaignObjectiveMarker(
                CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET,
                "Red Spear",
                "Hostile contact",
                Faction.ENEMY,
                100.0,
                100.0,
                120.0,
                90
        );

        Method colorMethod = Renderer.class.getDeclaredMethod(
                "strategicMarkerColor",
                CampaignSystem.CampaignObjectiveMarker.class
        );
        colorMethod.setAccessible(true);
        java.awt.Color actual = (java.awt.Color) colorMethod.invoke(null, marker);

        Method factionColorMethod = Renderer.class.getDeclaredMethod(
                "factionMapColor",
                Faction.class,
                boolean.class,
                int.class
        );
        factionColorMethod.setAccessible(true);
        java.awt.Color expected = (java.awt.Color) factionColorMethod.invoke(null, Faction.ENEMY, false, 220);

        assertEquals(expected, actual);
    }

    @Test
    void tacticalFpsViewUsesSimpleBackgroundAndOnlyTokenizesNonCriticalShips() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.ui.tacticalViewEnabled = true;
        ctx.player = new Player(ShipRole.FRIGATE, 110.0, 110.0);
        ctx.player.angle = 0.0;
        ctx.player.faction = Faction.ALLY;

        Ship ship = new FleetShip(ShipRole.BATTLECRUISER, Faction.ENEMY, 170.0, 110.0);
        ship.name = "Blue Spear";

        BufferedImage canvas = new BufferedImage(220, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            Renderer.beginFramePerfCapture();
            Renderer.drawSpaceBackground(g2, ctx, 0.0, 0.0, 220, 220, 1234L);
            Renderer.drawShips(g2, java.util.List.of(ctx.player, ship), 0.0, 0.0, 220.0, 220.0, null, null, ctx);
        } finally {
            g2.dispose();
        }

        assertTrue((canvas.getRGB(5, 5) & 0x00FFFFFF) != 0, "FPS view should use the simple performance background, not pure black");
        assertFalse(Renderer.shouldDrawPerformanceToken(ctx, ship), "legacy token gate remains disabled; FPS view uses tactical outlines directly");
        assertTrue(Renderer.frameShipSkinMs() > 0.0,
                "FPS view should keep baked hull skin rendering for nearby/front readable ship silhouettes");

        boolean foundShipPixel = false;
        for (int y = 70; y <= 150 && !foundShipPixel; y++) {
            for (int x = 70; x <= 150; x++) {
                if ((canvas.getRGB(x, y) & 0x00FFFFFF) != 0) {
                    foundShipPixel = true;
                    break;
                }
            }
        }
        assertTrue(foundShipPixel, "FPS view should still draw nearby/front ships visibly");
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

        assertTrue(ctx.perf.drawnShips > 0, "FPS view should still render ships");
        assertTrue(ctx.perf.drawnAsteroids > 0, "FPS view should keep nearby asteroid rendering readable");
        assertTrue(ctx.perf.drawnSalvage > 0, "FPS view should keep nearby salvage rendering readable");
        assertTrue(ctx.perf.drawnProjectiles > 0, "FPS view should keep nearby projectile rendering readable");
        assertEquals(0, ctx.perf.drawnVfx, "FPS view should skip heavy VFX for performance");
        assertEquals(0, ctx.perf.drawnExplosions, "FPS view should skip heavy explosion rendering for performance");
    }

    @Test
    void missionMapUsesTacticalOutlineEntityLayerInsteadOfDotOnlyLayer() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = 2.2;
        ctx.ui.strategicMapFocusX = 2500.0;
        ctx.ui.strategicMapFocusY = 2500.0;
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        Ship capital = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 2500.0, 2500.0);
        capital.name = "Map Outline Test Cruiser";
        ctx.ships.add(capital);

        BufferedImage canvas = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            GameRenderSystem.render(ctx, g2, 1280, 720);
        } finally {
            g2.dispose();
        }

        Rectangle inner = Renderer.getStrategicMapInnerRect(1280, 720);
        int cx = inner.x + inner.width / 2;
        int cy = inner.y + inner.height / 2;
        int changed = 0;
        for (int y = cy - 38; y <= cy + 38; y++) {
            for (int x = cx - 38; x <= cx + 38; x++) {
                int argb = canvas.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int r = (argb >>> 16) & 0xff;
                int g = (argb >>> 8) & 0xff;
                int b = argb & 0xff;
                if (alpha > 90 && (r > 90 || g > 120 || b > 150)) changed++;
            }
        }

        assertTrue(ctx.perf.renderMapMs > 0.0, "mission map overlay should render");
        assertTrue(changed > 140,
                "mission map should draw a visible tactical ship silhouette, not only a tiny dot");
    }
}
