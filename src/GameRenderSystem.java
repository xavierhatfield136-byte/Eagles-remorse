import app.config.GameMode;
import app.ui.ThemeArt;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class GameRenderSystem {
    static final double MATCH_RENDER_CULL_PAD_METERS = 440.0;
    static final double LONG_RANGE_CONTACT_RENDER_METERS = 40_000.0;

    static final class SensorNetEntry {
        final String section;
        final String title;
        final String detail;
        final double x;
        final double y;
        final Color accent;
        final String banner;

        SensorNetEntry(String section, String title, String detail, double x, double y, Color accent, String banner) {
            this.section = (section == null || section.isBlank()) ? "CONTACTS" : section.trim();
            this.title = (title == null || title.isBlank()) ? "Unknown Contact" : title.trim();
            this.detail = (detail == null) ? "" : detail.trim();
            this.x = x;
            this.y = y;
            this.accent = (accent == null) ? new Color(132, 224, 255) : accent;
            this.banner = (banner == null || banner.isBlank()) ? this.title : banner.trim();
        }
    }

    private static final class OrePatchEntry {
        double x;
        double y;
        double weight;
        int ore;
        int asteroids;

        void add(Asteroid asteroid) {
            if (asteroid == null) return;
            double asteroidWeight = Math.max(1.0, asteroid.ore + Math.max(0.0, asteroid.radius - 18.0) * 8.0);
            if (asteroid.rich || asteroid.oreMax >= 650) asteroidWeight *= 1.35;
            x += asteroid.x * asteroidWeight;
            y += asteroid.y * asteroidWeight;
            weight += asteroidWeight;
            ore += Math.max(0, asteroid.ore);
            asteroids++;
        }

        double centerX() {
            return weight <= 0.0 ? x : x / weight;
        }

        double centerY() {
            return weight <= 0.0 ? y : y / weight;
        }
    }

    private GameRenderSystem(){}

    private static final java.util.WeakHashMap<Ship, Integer> LAST_HP = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, Double> LAST_SHIELD = new java.util.WeakHashMap<>();

    private static boolean paintThemedHudFrame(Graphics2D g2, int x, int y, int w, int h,
                                               Color accent, String slot, int arc) {
        if (g2 == null || w <= 0 || h <= 0) return false;
        BufferedImage image = ThemeArt.get(slot);
        if (image == null) return false;
        Color base = (accent == null) ? new Color(150, 190, 235, 180) : accent;
        Paint oldPaint = g2.getPaint();
        Composite oldComposite = g2.getComposite();
        g2.setPaint(new GradientPaint(x, y, new Color(6, 12, 22, 224), x, y + h, new Color(4, 8, 16, 212)));
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.setPaint(new GradientPaint(
                x + w * 0.14f, y + h * 0.10f, new Color(base.getRed(), base.getGreen(), base.getBlue(), 42),
                x + w * 0.48f, y + h * 0.42f, new Color(base.getRed(), base.getGreen(), base.getBlue(), 0)));
        g2.fillRoundRect(x + 6, y + 6, Math.max(8, w - 12), Math.max(8, h - 12),
                Math.max(8, arc - 4), Math.max(8, arc - 4));
        g2.setComposite(AlphaComposite.SrcOver);
        g2.drawImage(image, x, y, w, h, null);
        g2.setComposite(oldComposite);
        g2.setPaint(oldPaint);
        return true;
    }

    private static Rectangle themedContentRect(String slot, int x, int y, int w, int h) {
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(slot, w, h);
        return new Rectangle(
                x + metrics.left(),
                y + metrics.top(),
                Math.max(1, w - metrics.left() - metrics.right()),
                Math.max(1, h - metrics.top() - metrics.bottom()));
    }

    public static void render(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        Renderer.beginFramePerfCapture();
        if (CampaignSystem.isCampaignMapScreenActive(ctx)) {
            renderCampaignMapScreen(ctx, g2, viewportW, viewportH);
            return;
        }
        // Background (screen space)
        long seed = (ctx.config != null ? ctx.config.seed : 12345L);
        boolean tacticalFpsView = ctx != null && ctx.ui != null && ctx.ui.tacticalViewEnabled;
        Renderer.drawSpaceBackground(g2, ctx, ctx.camX, ctx.camY, viewportW, viewportH, seed);
        if (!tacticalFpsView) {
            drawModifierWorldTint(ctx, g2, viewportW, viewportH);
        }
        double zoom = CameraSystem.normalizedZoom(ctx);
        double cullPad = MATCH_RENDER_CULL_PAD_METERS;
        double viewMinX = ctx.camX - cullPad;
        double viewMinY = ctx.camY - cullPad;
        double viewMaxX = ctx.camX + CameraSystem.worldViewWidth(ctx, viewportW) + cullPad;
        double viewMaxY = ctx.camY + CameraSystem.worldViewHeight(ctx, viewportH) + cullPad;
        FogOfWarSystem.State renderFog = null;

        // World space
        Graphics2D worldG = (Graphics2D) g2.create();
        worldG.scale(zoom, zoom);
        worldG.translate(-ctx.camX, -ctx.camY);

        worldG.setColor(tacticalFpsView ? new Color(130, 180, 220, 28) : new Color(255, 255, 255, 28));
        worldG.drawRect(0, 0, ctx.WORLD_W, ctx.WORLD_H);

        if (!tacticalFpsView && DevTools.isFancyVfxEnabled()) {
            updateDamageVfx(ctx);
        }

        java.util.List<Ship> mapShips = fleetHubRenderShips(ctx);
        java.util.List<Ship> renderShips = renderScopedShips(ctx, mapShips);
        java.util.List<Asteroid> renderAsteroids = renderScopedAsteroids(ctx, ctx.asteroids);
        java.util.List<Salvage> renderSalvage = renderScopedSalvage(ctx, ctx.salvage);
        java.util.List<Projectile> renderProjectiles = renderScopedProjectiles(ctx, ctx.projectiles);

        ctx.perf.drawnAsteroids = Renderer.drawAsteroids(worldG, renderAsteroids, ctx.player, viewMinX, viewMinY, viewMaxX, viewMaxY);
        if (!tacticalFpsView && DevTools.isDebugOverlay() && DevTools.isAsteroidHeatmapEnabled()) {
            Renderer.drawAsteroidDangerHeatmap(worldG, renderAsteroids, viewMinX, viewMinY, viewMaxX, viewMaxY);
        }
        ctx.perf.drawnSalvage = Renderer.drawSalvage(worldG, renderSalvage, viewMinX, viewMinY, viewMaxX, viewMaxY);
        if (!tacticalFpsView) {
            drawTransportSupportAuras(ctx, worldG, renderShips, viewMinX, viewMinY, viewMaxX, viewMaxY);
        }

        ctx.perf.totalVfx = VFX.activeCount();
        ctx.perf.totalExplosions = Explosion.active.size();
        ctx.perf.totalWreckChunks = WreckChunk.activeCount();
        if (!tacticalFpsView) {
            try {
                ctx.perf.drawnVfx = VFX.drawAll(worldG, viewMinX, viewMinY, viewMaxX, viewMaxY,
                        (x, y) -> isInLoadedRenderZone(ctx, x, y));
            } catch (Throwable ignored) { ctx.perf.drawnVfx = 0; }

            ctx.perf.drawnExplosions = 0;
            try {
                for (Explosion e : Explosion.active) {
                    if (e == null) continue;
                    if (!isInLoadedRenderZone(ctx, e.x, e.y)) continue;
                    if (!isExplosionVisible(e, viewMinX, viewMinY, viewMaxX, viewMaxY)) continue;
                    ctx.perf.drawnExplosions++;
                    if (ctx.experience.reducedFlash && e.kind != Explosion.Kind.SHIELD_HIT) {
                        continue;
                    } else if (e.kind == Explosion.Kind.SHIELD_HIT) {
                        drawShieldImpactExplosion(worldG, e);
                    } else if (e.kind == Explosion.Kind.DESTABILIZER_PULSE) {
                        drawDestabilizerPulseExplosion(worldG, e);
                    } else if (e.kind == Explosion.Kind.SUPERWEAPON_BLAST) {
                        drawSuperweaponBlastExplosion(worldG, e);
                    } else if (e.kind == Explosion.Kind.STASIS_FIELD) {
                        drawStasisFieldExplosion(worldG, e);
                    } else if (e.kind == Explosion.Kind.FINAL_DETONATION) {
                        drawFinalDetonationExplosion(worldG, e);
                    } else {
                        drawDeathExplosion(worldG, e);
                    }
                }
            } catch (Throwable ignored) {}
        } else {
            ctx.perf.drawnVfx = 0;
            ctx.perf.drawnExplosions = 0;
        }

        if (!tacticalFpsView) {
            ctx.perf.drawnWreckChunks = WreckChunk.drawAll(worldG, viewMinX, viewMinY, viewMaxX, viewMaxY,
                    (x, y) -> isInLoadedRenderZone(ctx, x, y));
        } else {
            ctx.perf.drawnWreckChunks = 0;
        }

        Faction perspective = (renderFog == null || ctx.player == null) ? null : ctx.player.faction;
        long shipRenderStart = System.nanoTime();
        ctx.perf.drawnShips = Renderer.drawShips(worldG, renderShips, viewMinX, viewMinY, viewMaxX, viewMaxY, renderFog, perspective, ctx);
        ctx.perf.renderShipsMs = (System.nanoTime() - shipRenderStart) / 1_000_000.0;
        ctx.perf.shieldRenderMs = Renderer.frameShieldRenderMs();
        ctx.perf.drawnProjectiles = Renderer.drawProjectiles(worldG, renderShips, renderProjectiles, viewMinX, viewMinY, viewMaxX, viewMaxY, renderFog, perspective);
        ctx.perf.visibleSprites = ctx.perf.drawnShips + ctx.perf.drawnProjectiles + ctx.perf.drawnAsteroids
                + ctx.perf.drawnSalvage + ctx.perf.drawnWreckChunks + ctx.perf.drawnVfx + ctx.perf.drawnExplosions;
        if (!tacticalFpsView) {
            Renderer.drawSuperweaponAimCue(worldG, ctx.player, ctx.cursorWorldX, ctx.cursorWorldY);
            Renderer.drawNpcSuperweaponAimCues(worldG, renderShips, ctx.player, viewMinX, viewMinY, viewMaxX, viewMaxY, renderFog);

            Renderer.drawWorldMarkers(worldG, renderShips, ctx.lockedTarget, ctx.command.fleetCommandShips, ctx.command.fleetSharedTargets,
                    viewMinX, viewMinY, viewMaxX, viewMaxY, renderFog, perspective);
            Renderer.drawSelectedCommsContactWorld(worldG, ctx);
            if (CampaignSystem.isFleetHubSession(ctx)) {
                drawFleetSelectionMarker(worldG, CampaignSystem.fleetSelectedShip(ctx));
            }
            Renderer.drawCombatCallouts(worldG, ctx.ui.combatCallouts, viewMinX, viewMinY, viewMaxX, viewMaxY, ctx.fogOfWar);
            drawCampaignMarkers(ctx, worldG, viewMinX, viewMinY, viewMaxX, viewMaxY);
            TutorialSystem.drawWorldMarkers(ctx, worldG);
        }
        worldG.dispose();

        int allyOre = EconomySystem.getOreTotalForFaction(ctx, Faction.ALLY);
        int enemyOre = EconomySystem.getOreTotalForFaction(ctx, Faction.ENEMY);
        boolean resRush = (ctx.config != null && ctx.config.mode == GameMode.RESOURCE_RUSH);

        Ship docked = CampaignSystem.currentBaseUpgradeAnchor(ctx);
        int hangarTier = UISystem.getMaxHangarTierForPlayer(ctx);
        int maxHangarTier = CampaignSystem.isCampaignActive(ctx)
                ? CampaignSystem.campaignMaxHangarTier(ctx)
                : 3;
        int playerWingActive = CarrierSystem.countActiveWingByCarrier(ctx, ctx.player);
        int playerWingCap = (ctx.player != null && ctx.player.isCarrier) ? Math.max(0, ctx.player.maxFighters) : 0;
        int lockedWingActive = CarrierSystem.countActiveWingByCarrier(ctx, ctx.lockedTarget);
        int lockedWingCap = (ctx.lockedTarget != null && ctx.lockedTarget.isCarrier) ? Math.max(0, ctx.lockedTarget.maxFighters) : 0;
        String objectiveTitle = CampaignSystem.hudObjectiveTitle(ctx);
        String objectiveDetail = CampaignSystem.hudObjectiveDetail(ctx);
        if ((objectiveTitle == null || objectiveTitle.isBlank()) && TutorialSystem.isActive(ctx)) {
            objectiveTitle = TutorialSystem.hudTitle(ctx);
            objectiveDetail = TutorialSystem.hudDetail(ctx);
        }
        if ((objectiveTitle == null || objectiveTitle.isBlank()) && LastStandSystem.isActive(ctx)) {
            objectiveTitle = LastStandSystem.hudTitle(ctx);
            objectiveDetail = LastStandSystem.hudDetail(ctx);
        }
        String stationStatus = "STATIONS "
                + "C:" + (ctx.command.captainAutomation ? "AI" : "MAN") + "  "
                + "H:" + (ctx.command.helmAutomation ? "AI" : "MAN") + "  "
                + "T:" + (ctx.command.tacticalAutomation ? "AI" : "MAN") + "  "
                + "E:" + (ctx.command.engineeringAutomation ? "AI" : "MAN") + "  "
                + "S:" + (ctx.command.scienceAutomation ? "AI" : "MAN");
        String overlayStatus = activeOverlayLabel(ctx);
        String contextHint = buildContextHint(ctx, docked);

        long hudRenderStart = System.nanoTime();
        Renderer.drawHUD(
                g2,
                ctx.player,
                ctx.credits,
                hangarTier,
                (docked != null),
                ctx.ui.shopOpen,
                ctx.autoLockTurrets,
                ctx.lockedTarget,
                playerWingActive,
                playerWingCap,
                lockedWingActive,
                lockedWingCap,
                resRush,
                allyOre,
                enemyOre,
                ctx.resourceGoal,
                ctx.gameOverText,
                objectiveTitle,
                objectiveDetail,
                ctx.eventBanner,
                ctx.eventBannerT,
                ctx.orePriceMul,
                ctx.orePriceT,
                ctx.miningMul,
                ctx.miningT,
                ctx.camX,
                ctx.camY,
                viewportW,
                viewportH,
                zoom,
                stationStatus,
                ctx,
                ctx.ui.hudDetail,
                contextHint,
                overlayStatus

        );
        TutorialSystem.drawOverlay(ctx, g2, viewportW, viewportH);
        drawVoiceCaption(ctx, g2, viewportW, viewportH);
        drawModifierChips(ctx, g2, viewportW);
        Renderer.drawCommsPanel(g2, ctx, viewportW, viewportH);
        Renderer.drawCommsContextMenu(g2, ctx, viewportW, viewportH);
        Renderer.drawCommTradeMenu(g2, ctx, viewportW, viewportH);

        ctx.perf.renderHudMs = (System.nanoTime() - hudRenderStart) / 1_000_000.0;
        if (tacticalFpsView) {
            drawTacticalStatusOverlay(ctx, g2, viewportW, viewportH, zoom, stationStatus, overlayStatus);
        }

        if (ctx.ui.mapOpen) {
            long mapRenderStart = System.nanoTime();
            Renderer.drawStrategicMap(g2, ctx, viewportW, viewportH, ctx.WORLD_W, ctx.WORLD_H, ctx.camX, ctx.camY,
                    CameraSystem.worldViewWidth(ctx, viewportW), CameraSystem.worldViewHeight(ctx, viewportH), ctx.player,
                    mapShips, ctx.asteroids, ctx.salvage, ctx.ui.waypointX, ctx.ui.waypointY, ctx.ui.mapPings,
                    CampaignSystem.isCampaignActive(ctx) ? ctx.fogOfWar : null, ctx.eventBanner);
            TutorialSystem.drawStrategicMapOverlay(ctx, g2, viewportW, viewportH);
            ctx.perf.renderMapMs = (System.nanoTime() - mapRenderStart) / 1_000_000.0;
        } else {
            ctx.perf.renderMapMs = 0.0;
        }

        if (ctx.ui.baseMenuOpen) {
            Ship base = CampaignSystem.currentBaseUpgradeAnchor(ctx);
            if (base != null) {
                BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(base, k -> new BaseUpgrades());
                int baseOre = CampaignSystem.isCampaignActive(ctx) ? CampaignSystem.currentCampaignOre(ctx) : base.oreStockpile;
                Renderer.drawBaseUpgradeOverlay(g2, base, base.name, ctx.credits, baseOre,
                        up.hullLv, up.shieldLv, up.turretLv, up.miningLv, up.hangarLv,
                        maxHangarTier, CampaignSystem.isFleetHubSession(ctx));
            }
        }

        if (ctx.ui.powerManagementOpen && ctx.player != null) {
            Renderer.drawPowerManagementOverlay(g2, ctx.player, ctx.ui.powerManagementFocus);
        }

        if (ctx.ui.crewStationsOpen && ctx.player != null) {
            Renderer.drawCrewStationsOverlay(g2, ctx);
        }

        if (ctx.ui.flightDeckOpen && ctx.player != null) {
            Renderer.drawFlightDeckOverlay(g2, ctx.player, ctx.ui.flightDeckFocus);
        }

        drawCampaignMissionIntro(ctx, g2, viewportW, viewportH);
        drawCampaignTransitionOverlay(ctx, g2, viewportW, viewportH);

        if (CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) {
            Renderer.drawStrategicEncounterOverlay(g2, ctx, viewportW, viewportH);
        }
        Renderer.drawCampaignActionConfirmOverlay(g2, ctx, viewportW, viewportH);
        FirstHourOnboardingSystem.draw(ctx, g2, viewportW, viewportH);

        if (ctx.state == GameState.PAUSED && !CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) {
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRect(0, 0, viewportW, viewportH);

            g2.setColor(new Color(255, 255, 255, 235));
            g2.setFont(new Font("Consolas", Font.BOLD, 48));
            String msg = "PAUSED";
            FontMetrics fm = g2.getFontMetrics();
            int x = (viewportW - fm.stringWidth(msg)) / 2;
            int y = viewportH / 2 - 24;
            g2.drawString(msg, x, y);

            g2.setFont(new Font("Consolas", Font.PLAIN, 18));
            String hint = "ESC: resume   F10: menu   Alt+Enter: fullscreen";
            FontMetrics fm2 = g2.getFontMetrics();
            int hx = (viewportW - fm2.stringWidth(hint)) / 2;
            int hy = y + 42;
            g2.drawString(hint, hx, hy);
        }

        // Persistent quick-access overlays bar.
        Renderer.drawCoreMenuBar(g2, ctx, viewportW, viewportH);
        Renderer.drawCurrentContextLegend(g2, ctx, viewportW, viewportH);
        Renderer.drawControlsScreen(g2, ctx, viewportW, viewportH);
        TacticalCombatDepthSystem.drawOverlay(ctx, g2, viewportW, viewportH);
        if (ctx.experience.highContrastHud) {
            g2.setColor(new Color(245, 250, 255, 210));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawRect(3, 3, Math.max(1, viewportW - 7), Math.max(1, viewportH - 7));
        }
        int mouseX = (int) Math.round(ctx.cursorScreenX);
        int mouseY = (int) Math.round(ctx.cursorScreenY);
        Renderer.HoverTooltip hoverTooltip = Renderer.hoverTooltipAt(ctx, viewportW, viewportH, mouseX, mouseY);
        if (hoverTooltip != null) {
            ctx.ui.updateHoverTooltip(
                    hoverTooltip.key,
                    hoverTooltip.title,
                    hoverTooltip.body,
                    mouseX,
                    mouseY,
                    System.nanoTime(),
                    0L);
        } else {
            ctx.ui.clearHoverTooltip();
        }
        Renderer.drawHoverTooltip(g2, ctx.ui, mouseX, mouseY, viewportW, viewportH);
        ctx.perf.drawnUiPanels = visibleUiPanelCount(ctx);

// Dev debug overlay (F3)
if (DevTools.isDebugOverlay()) {
    try { DevOverlay.draw(g2, ctx, viewportW, viewportH); } catch (Throwable ignored) {}
}

    }

    private static void renderCampaignMapScreen(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        long seed = (ctx != null && ctx.config != null) ? ctx.config.seed : 12345L;
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, viewportW, viewportH);
        Renderer.drawSpaceBackground(g2, 0.0, 0.0, viewportW, viewportH, seed);
        Renderer.drawStrategicMap(g2, ctx, viewportW, viewportH, ctx.WORLD_W, ctx.WORLD_H,
                0.0, 0.0, viewportW, viewportH, ctx.player,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                Double.NaN, Double.NaN, ctx.ui.mapPings, null, ctx.eventBanner);
        drawCampaignMissionIntro(ctx, g2, viewportW, viewportH);
        drawCampaignTransitionOverlay(ctx, g2, viewportW, viewportH);
        if (CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) {
            Renderer.drawStrategicEncounterOverlay(g2, ctx, viewportW, viewportH);
        }
        Renderer.drawCampaignHubOverlay(g2, ctx, viewportW, viewportH);
        Renderer.drawCampaignActionConfirmOverlay(g2, ctx, viewportW, viewportH);
        FirstHourOnboardingSystem.draw(ctx, g2, viewportW, viewportH);
        ctx.perf.drawnAsteroids = 0;
        ctx.perf.drawnSalvage = 0;
        ctx.perf.drawnShips = 0;
        ctx.perf.drawnProjectiles = 0;
        ctx.perf.drawnVfx = 0;
        ctx.perf.drawnExplosions = 0;
        ctx.perf.drawnWreckChunks = 0;
        ctx.perf.visibleSprites = 0;
        ctx.perf.drawnUiPanels = visibleUiPanelCount(ctx);
        ctx.perf.totalVfx = 0;
        ctx.perf.totalExplosions = 0;
        ctx.perf.totalWreckChunks = WreckChunk.activeCount();
        ctx.perf.renderShipsMs = 0.0;
        ctx.perf.shieldRenderMs = 0.0;
        ctx.perf.renderHudMs = 0.0;
    }

    private static int visibleUiPanelCount(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return 0;
        int panels = 2; // HUD and persistent quick-access bar.
        if (ctx.ui.mapOpen || CampaignSystem.isCampaignMapScreenActive(ctx)) panels++;
        if (ctx.ui.shopOpen) panels++;
        if (ctx.ui.baseMenuOpen) panels++;
        if (ctx.ui.powerManagementOpen) panels++;
        if (ctx.ui.crewStationsOpen) panels++;
        if (ctx.ui.flightDeckOpen) panels++;
        if (ctx.ui.strategicEncounterPrompt.active) panels++;
        if (ctx.ui.campaignHubMenu.active) panels++;
        if (ctx.ui.campaignActionConfirm.active) panels++;
        if (ctx.state == GameState.PAUSED) panels++;
        return panels;
    }

    private static String activeOverlayLabel(GameContext ctx) {
        if (ctx == null) return "";
        boolean fleetHub = CampaignSystem.isFleetHubSession(ctx);
        if (ctx.ui.shopOpen) return fleetHub ? "OVERLAY: FLEET HANGAR" : "OVERLAY: SHOP/LOADOUT";
        if (ctx.ui.baseMenuOpen) return fleetHub ? "OVERLAY: FLEET UPGRADE CONSOLE" : "OVERLAY: BASE UPGRADES";
        if (ctx.ui.powerManagementOpen) return "OVERLAY: POWER MANAGEMENT";
        if (ctx.ui.crewStationsOpen) return "OVERLAY: CREW STATIONS";
        if (ctx.ui.flightDeckOpen) return "OVERLAY: FLIGHT DECK";
        if (ctx.ui.mapOpen) return "OVERLAY: STRATEGIC MAP";
        if (ctx.state == GameState.PAUSED) return "OVERLAY: PAUSED";
        return "";
    }

    private static String buildContextHint(GameContext ctx, Ship dockedBase) {
        if (ctx == null || ctx.player == null) return "";
        String tutorialHint = TutorialSystem.contextHint(ctx);
        if (tutorialHint != null && !tutorialHint.isBlank()) return tutorialHint;
        Ship p = ctx.player;

        if (ctx.ui.hasBlockingOverlay()) {
            return "Overlay active: combat input blocked, station AI continues running. Press ESC to close.";
        }
        if (!p.alive || p.dying || p.hp <= 0) {
            return "";
        }
        if (CampaignSystem.isFleetHubSession(ctx)) {
            Ship selected = CampaignSystem.fleetSelectedShip(ctx);
            String selectedName = (selected == null || selected.name == null || selected.name.isBlank())
                    ? "none"
                    : selected.name;
            String selectedRole = (selected == null || selected.role == null)
                    ? "UNKNOWN"
                    : selected.role.name();
            return "Fleet hub: click a ship to select it, TAB opens the fleet shop, B edits the selected hull, and Enter launches. Selected: "
                    + selectedName + " / " + selectedRole + ". Mouse wheel zooms.";
        }
        if (ctx.command.playerTeleportCharging) {
            double t = Math.max(0.0, ctx.command.playerTeleportChargeRemaining);
            return String.format("Battlefield warp charging: %.1fs remaining (- or Backspace, damage disrupts).", t);
        }

        int fireRooms = p.activeFireRoomCount();
        if (fireRooms > 0) {
            double fireLoad = p.totalFireIntensity();
            ShipRoomLayout.RoomId hotspot = p.hottestFireRoom();
            String hotspotLabel = (hotspot == null) ? "UNKNOWN" : hotspot.name();
            if (hotspot != null) {
                ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(p.role, p.faction, hotspot);
                if (def != null && def.label != null && !def.label.isBlank()) {
                    hotspotLabel = def.label;
                }
            }
            if (fireRooms >= 2 || fireLoad >= 2.1) {
                return "Fire emergency in " + hotspotLabel + ". Open crew stations (H), engineering, then press 8 to suppress.";
            }
            return "Local fire detected in " + hotspotLabel + ". Engineering can suppress hotspots with key 8.";
        }

        double hpFrac = (p.hpMax <= 0) ? 1.0 : (p.hp / (double) p.hpMax);
        double effectiveShieldMax = p.effectiveShieldCapacityMax();
        double shieldFrac = (effectiveShieldMax <= 0.0) ? 1.0 : (p.shield / Math.max(1e-9, effectiveShieldMax));
        if (hpFrac < 0.35 || shieldFrac < 0.20) {
            return "Critical survivability: fall back to friendly support and cycle DEFENSE power preset.";
        }
        if (dockedBase != null) {
            return "Docked at base: ore auto-deposits. Press B for base upgrades, TAB for loadout.";
        }
        if (ctx.lockedTarget == null || !ctx.lockedTarget.alive || ctx.lockedTarget.dying) {
            if (hasHostileNearPlayer(ctx, 920.0)) {
                return "No target lock while hostiles are nearby. Press L (or middle click) to lock quickly.";
            }
        } else if (p.hasSuperweapon && p.isSuperweaponCharging()) {
            return "Superweapon charging: hold heading steady until charge completes.";
        }
        if (p.isCarrier) {
            int active = CarrierSystem.countActiveWingByCarrier(ctx, p);
            if (active <= 0) {
                return "Carrier wing idle: press / to set 5 squad pairs, C to launch a 2-ship squad, V to set wing behavior.";
            }
        }
        return "Use N to cycle HUD detail (FULL/COMPACT/MINIMAL). Press J to toggle Tactical FPS View.";
    }

    private static void drawTacticalStatusOverlay(GameContext ctx, Graphics2D g2, int viewportW, int viewportH,
                                                  double zoom, String stationStatus, String overlayStatus) {
        if (ctx == null || g2 == null) return;

        Graphics2D hud = (Graphics2D) g2.create();
        try {
            int x = 16;
            int y = 16;
            int w = Math.min(390, Math.max(280, viewportW / 3));
            int h = 118;
            int lineH = 18;

            hud.setColor(new Color(0, 0, 0, 180));
            hud.fillRoundRect(x, y, w, h, 16, 16);
            hud.setColor(new Color(118, 182, 242, 168));
            hud.drawRoundRect(x, y, w, h, 16, 16);

            hud.setFont(new Font("Consolas", Font.BOLD, 16));
            hud.setColor(new Color(224, 236, 255, 230));
            hud.drawString("TACTICAL FPS VIEW", x + 14, y + 24);

            hud.setFont(new Font("Consolas", Font.PLAIN, 13));
            hud.setColor(new Color(196, 214, 234, 214));
            int textY = y + 46;
            String hull = (ctx.player == null)
                    ? "Hull: N/A"
                    : "Hull: " + Math.max(0, (int) Math.ceil(ctx.player.hp)) + "/" + Math.max(0, (int) Math.ceil(ctx.player.hpMax));
            double shieldMax = (ctx.player == null) ? 0.0 : ctx.player.effectiveShieldCapacityMax();
            String shield = (ctx.player == null)
                    ? "Shield: N/A"
                    : "Shield: " + Math.max(0, (int) Math.ceil(ctx.player.shield)) + "/" + Math.max(0, (int) Math.ceil(shieldMax));
            hud.drawString(hull, x + 14, textY);
            hud.drawString(shield, x + 14, textY + lineH);
            hud.drawString("Visible ships: " + ctx.perf.drawnShips + "    Zoom: " + String.format(java.util.Locale.US, "%.2fx", zoom),
                    x + 14, textY + lineH * 2);
            hud.drawString("Projectiles, fog, salvage, and markers hidden for FPS.", x + 14, textY + lineH * 3);

            String footer = (stationStatus != null && !stationStatus.isBlank()) ? stationStatus : overlayStatus;
            if (footer != null && !footer.isBlank()) {
                hud.setColor(new Color(153, 192, 230, 186));
                hud.drawString(footer, x + 14, textY + lineH * 4);
            }
        } finally {
            hud.dispose();
        }
    }

    private static boolean hasHostileNearPlayer(GameContext ctx, double radius) {
        if (ctx == null || ctx.player == null || ctx.ships == null || radius <= 0.0) return false;
        double r2 = radius * radius;
        for (Ship s : ctx.ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s == ctx.player) continue;
            if (s.faction == null || ctx.player.faction == null) continue;
            if (s.faction.isFriendlyTo(ctx.player.faction)) continue;
            if (GameMath.dist2(s.x, s.y, ctx.player.x, ctx.player.y) <= r2) return true;
        }
        return false;
    }

    private static void drawVoiceCaption(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        if (ctx == null || g2 == null) return;
        if (!ctx.ui.voiceCaptionsEnabled) return;
        if (ctx.ui.voiceCaptionT <= 0.0 || ctx.ui.voiceCaption == null || ctx.ui.voiceCaption.isBlank()) return;

        String text = ctx.experience.subtitleSpeakerLabels
                ? ctx.ui.voiceCaption
                : ctx.ui.voiceCaption.replaceFirst("^[A-Z ]{2,18}:\\s*", "");
        int subtitlePx = Math.max(11, (int) Math.round(14 * ctx.experience.subtitleScale * ctx.experience.uiTextScale));
        g2.setFont(new Font("Consolas", Font.BOLD, subtitlePx));
        FontMetrics fm = g2.getFontMetrics();

        int w = Math.min(viewportW - 28, fm.stringWidth(text) + 24);
        int h = 30;
        int x = (viewportW - w) / 2;
        int y = viewportH - 90;

        if (ctx.experience.subtitleBackground) {
            g2.setColor(new Color(0, 0, 0, 195));
            g2.fillRoundRect(x, y, w, h, 12, 12);
        }
        g2.setColor(new Color(205, 225, 255, 190));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        g2.setColor(new Color(245, 250, 255, 230));
        int tx = x + (w - fm.stringWidth(text)) / 2;
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, tx, ty);
    }

    private static void drawFleetSelectionMarker(Graphics2D g2, Ship ship) {
        if (g2 == null || ship == null || !ship.alive || ship.dying || ship.hp <= 0) return;
        double radius = Math.max(46.0, ship.radius * 1.9);
        int x = (int) Math.round(ship.x - radius);
        int y = (int) Math.round(ship.y - radius);
        int d = (int) Math.round(radius * 2.0);

        java.awt.Stroke oldStroke = g2.getStroke();
        java.awt.Font oldFont = g2.getFont();
        g2.setStroke(new BasicStroke(2.6f));
        g2.setColor(new Color(120, 240, 255, 210));
        g2.drawOval(x, y, d, d);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawOval((int) Math.round(ship.x - radius * 1.25), (int) Math.round(ship.y - radius * 1.25),
                (int) Math.round(radius * 2.5), (int) Math.round(radius * 2.5));
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        String label = "SELECTED";
        g2.drawString(label, (int) Math.round(ship.x + radius + 8), (int) Math.round(ship.y - radius - 6));
        g2.setFont(oldFont);
        g2.setStroke(oldStroke);
    }

    private static java.util.List<Ship> fleetHubRenderShips(GameContext ctx) {
        if (ctx == null || ctx.ships == null || ctx.ships.isEmpty()) return ctx == null ? java.util.List.of() : ctx.ships;
        if (!CampaignSystem.isFleetHubSession(ctx)) return ctx.ships;
        Ship hiddenBase = ctx.enemyBase;
        if (hiddenBase == null) return ctx.ships;
        java.util.ArrayList<Ship> out = new java.util.ArrayList<>(ctx.ships.size());
        for (Ship ship : ctx.ships) {
            if (ship == hiddenBase) continue;
            out.add(ship);
        }
        return out;
    }

    static java.util.List<Ship> renderScopedShips(GameContext ctx, java.util.List<Ship> ships) {
        if (ships == null || ships.isEmpty() || !hasLoadedRenderScope(ctx)) return ships;
        java.util.ArrayList<Ship> out = new java.util.ArrayList<>(ships.size());
        for (Ship ship : ships) {
            if (shouldRenderShipInLoadedZone(ctx, ship)) out.add(ship);
        }
        return out;
    }

    static java.util.List<Projectile> renderScopedProjectiles(GameContext ctx, java.util.List<Projectile> projectiles) {
        if (projectiles == null || projectiles.isEmpty() || !hasLoadedRenderScope(ctx)) return projectiles;
        java.util.ArrayList<Projectile> out = new java.util.ArrayList<>(projectiles.size());
        for (Projectile projectile : projectiles) {
            if (projectile != null && isInLoadedRenderZone(ctx, projectile.x, projectile.y)) out.add(projectile);
        }
        return out;
    }

    static java.util.List<Asteroid> renderScopedAsteroids(GameContext ctx, java.util.List<Asteroid> asteroids) {
        if (asteroids == null || asteroids.isEmpty() || !hasLoadedRenderScope(ctx)) return asteroids;
        java.util.ArrayList<Asteroid> out = new java.util.ArrayList<>(asteroids.size());
        for (Asteroid asteroid : asteroids) {
            if (asteroid != null && isInLoadedRenderZone(ctx, asteroid.x, asteroid.y)) out.add(asteroid);
        }
        return out;
    }

    static java.util.List<Salvage> renderScopedSalvage(GameContext ctx, java.util.List<Salvage> salvage) {
        if (salvage == null || salvage.isEmpty() || !hasLoadedRenderScope(ctx)) return salvage;
        java.util.ArrayList<Salvage> out = new java.util.ArrayList<>(salvage.size());
        for (Salvage drop : salvage) {
            if (drop != null && isInLoadedRenderZone(ctx, drop.x, drop.y)) out.add(drop);
        }
        return out;
    }

    static boolean shouldRenderShipInLoadedZone(GameContext ctx, Ship ship) {
        if (ship == null) return false;
        if (!hasLoadedRenderScope(ctx)) return true;
        if (ctx != null && ship == ctx.player) return true;
        BattlefieldSectorSystem.SectorDefinition loadedSector = loadedBattlefieldRenderSector(ctx);
        if (loadedSector == null) return true;
        BattlefieldSectorSystem.SectorDefinition shipSector = BattlefieldSectorSystem.sectorAt(ctx, ship.x, ship.y);
        if (shipSector != null && loadedSector.id.equals(shipSector.id)) return true;
        return allowLongRangeContactRendering(ctx, ship);
    }

    static boolean isInLoadedRenderZone(GameContext ctx, double x, double y) {
        if (!hasLoadedRenderScope(ctx)) return true;
        BattlefieldSectorSystem.SectorDefinition loadedSector = loadedBattlefieldRenderSector(ctx);
        if (loadedSector == null) return true;
        BattlefieldSectorSystem.SectorDefinition pointSector = BattlefieldSectorSystem.sectorAt(ctx, x, y);
        return pointSector != null && loadedSector.id.equals(pointSector.id);
    }

    private static boolean hasLoadedRenderScope(GameContext ctx) {
        if (ctx != null && ctx.config != null && ctx.config.mode == GameMode.SHOWCASE) return false;
        return BattlefieldSectorSystem.isEnabled(ctx) && !CampaignSystem.usesUnifiedMissionSpace(ctx);
    }

    private static BattlefieldSectorSystem.SectorDefinition loadedBattlefieldRenderSector(GameContext ctx) {
        if (!BattlefieldSectorSystem.isEnabled(ctx)) return null;
        BattlefieldSectorSystem.ensureLoadedSector(ctx);
        return BattlefieldSectorSystem.loadedSector(ctx);
    }

    private static boolean allowLongRangeContactRendering(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null || ctx.player == null || !ship.alive || ship.dying || ship.hp <= 0) return false;
        double d2 = GameMath.dist2(ctx.player.x, ctx.player.y, ship.x, ship.y);
        if (d2 > LONG_RANGE_CONTACT_RENDER_METERS * LONG_RANGE_CONTACT_RENDER_METERS) return false;
        Faction perspective = ctx.player.faction;
        if (perspective != null && ship.faction != null && ship.faction.isFriendlyTo(perspective)) return true;
        if (FogOfWarSystem.isVisibleToPerspective(ctx.fogOfWar, perspective, ship)) return true;
        return ctx.fogOfWar != null && ctx.fogOfWar.contactGhost(ship.id) != null;
    }

    private static void drawFleetNetOverlay(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        if (ctx == null || g2 == null || ctx.player == null || ctx.player.faction == null) return;

        String sensorSummary = FogOfWarSystem.isCombatFogEnabled(ctx) ? FogOfWarSystem.coverageSummary(ctx) : "";
        java.util.List<SensorNetEntry> entries = sensorNetEntries(ctx, 3, 1);
        java.util.List<String> squadLines = activeSquadSummaryLines(ctx);
        java.util.List<GameContext.FleetCommMessage> messages = recentFleetCommMessages(ctx, 2);
        java.util.List<String> sensorLines = sensorSummary.isBlank()
                ? java.util.List.of()
                : wrapLines(g2.getFontMetrics(new Font("Consolas", Font.PLAIN, 12)), sensorSummary, Math.min(300, Math.max(240, viewportW / 4)) - 24);
        if (squadLines.isEmpty() && messages.isEmpty() && sensorLines.isEmpty() && entries.isEmpty()) return;

        Font titleFont = new Font("Consolas", Font.BOLD, 13);
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        Font signalFont = new Font("Consolas", Font.PLAIN, 11);
        int w = Math.min(300, Math.max(240, viewportW / 4));
        int x = viewportW - w - 16;
        int y = 16;
        int h = 48 + Math.min(2, sensorLines.size()) * 15 + Math.min(2, squadLines.size()) * 15;
        if (!entries.isEmpty()) {
            h += 18;
            String currentSection = "";
            for (SensorNetEntry entry : entries) {
                if (entry == null) continue;
                if (!entry.section.equals(currentSection)) {
                    currentSection = entry.section;
                    h += 14;
                }
                h += 18;
            }
        }
        for (GameContext.FleetCommMessage msg : messages) {
            h += 22;
        }

        if (!paintThemedHudFrame(g2, x, y, w, h,
                new Color(140, 190, 255, 190), ThemeArt.HUD_STANDARD_PANEL, 16)) {
            g2.setColor(new Color(0, 0, 0, 165));
            g2.fillRoundRect(x, y, w, h, 16, 16);
            g2.setColor(new Color(140, 190, 255, 190));
            g2.drawRoundRect(x, y, w, h, 16, 16);
        }
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, h);

        int rowY = inner.y;
        g2.setFont(titleFont);
        g2.setColor(new Color(236, 244, 255, 230));
        g2.drawString("SENSOR NET", inner.x, rowY);
        g2.setFont(signalFont);
        g2.setColor(new Color(160, 220, 255, 178));
        g2.drawString("CLICK A TRACK TO ROUTE", inner.x + 112, rowY);
        rowY += 16;

        g2.setFont(bodyFont);
        for (int i = 0; i < sensorLines.size() && i < 2; i++) {
            String line = sensorLines.get(i);
            g2.setColor(new Color(120, 236, 255, 220));
            g2.drawString(line, inner.x, rowY);
            rowY += 15;
        }
        if (!entries.isEmpty()) {
            g2.setColor(new Color(255, 255, 255, 58));
            g2.drawLine(inner.x, rowY, inner.x + inner.width, rowY);
            rowY += 14;
            g2.setFont(signalFont);
            g2.setColor(new Color(255, 220, 164, 220));
            g2.drawString("PRIORITY TRACKS", inner.x, rowY);
            rowY += 14;
            String currentSection = "";
            for (SensorNetEntry entry : entries) {
                if (entry == null) continue;
                if (!entry.section.equals(currentSection)) {
                    currentSection = entry.section;
                    g2.setColor(new Color(255, 255, 255, 44));
                    g2.drawLine(inner.x, rowY - 8, inner.x + inner.width, rowY - 8);
                    g2.setColor(new Color(150, 220, 255, 210));
                    g2.drawString(currentSection, inner.x, rowY);
                    rowY += 14;
                }
                String line = sensorNetRow(entry);
                Color accent = entry.accent;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 56));
                g2.fillRoundRect(inner.x - 2, rowY - 11, inner.width + 4, 16, 8, 8);
                g2.setColor(new Color(238, 246, 255, 220));
                g2.drawString(line, inner.x + 4, rowY);
                rowY += 18;
            }
            g2.setFont(bodyFont);
        }
        if ((!sensorLines.isEmpty() || !entries.isEmpty()) && (!squadLines.isEmpty() || !messages.isEmpty())) {
            g2.setColor(new Color(255, 255, 255, 58));
            g2.drawLine(inner.x, rowY, inner.x + inner.width, rowY);
            rowY += 14;
        }
        for (int i = 0; i < squadLines.size() && i < 2; i++) {
            String line = squadLines.get(i);
            g2.setColor(new Color(168, 212, 255, 214));
            g2.drawString(line, inner.x, rowY);
            rowY += 15;
        }
        if (!squadLines.isEmpty() && !messages.isEmpty()) {
            g2.setColor(new Color(255, 255, 255, 58));
            g2.drawLine(inner.x, rowY, inner.x + inner.width, rowY);
            rowY += 14;
        }
        for (GameContext.FleetCommMessage msg : messages) {
            String compact = msg.channel + ": " + trimOverlayLine(msg.text, inner.width - 18);
            Color accent = squadColor(msg.faction, 210);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 88));
            g2.fillRoundRect(inner.x - 2, rowY - 10, inner.width + 4, 20, 10, 10);
            g2.setColor(new Color(244, 248, 255, 226));
            g2.drawString(compact, inner.x + 4, rowY + 2);
            rowY += 8;
            rowY += 14;
        }
    }

    static java.util.List<SensorNetEntry> sensorNetEntries(GameContext ctx, int maxMissionEntries, int maxSignalsPerSection) {
        java.util.ArrayList<SensorNetEntry> out = new java.util.ArrayList<>();
        if (ctx == null) return out;

        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        boolean showMissionOrePatches = CampaignSystem.usesMissionSubzones(ctx) && !CampaignSystem.isStrategicOvermapMode(ctx);
        java.util.List<CampaignSystem.CampaignObjectiveMarker> markers = showMissionOrePatches
                ? java.util.List.of()
                : CampaignSystem.activeObjectiveMarkers(ctx);
        int missionCount = 0;
        for (CampaignSystem.CampaignObjectiveMarker marker : markers) {
            if (marker == null) continue;
            if (marker.type == CampaignSystem.ObjectiveMarkerType.OPTIONAL_OBJECTIVE) continue;
            String key = "MISSION|" + marker.type + "|" + marker.label;
            if (!seen.add(key)) continue;
            out.add(new SensorNetEntry(
                    "MISSION",
                    marker.label,
                    marker.subtitle,
                    marker.x,
                    marker.y,
                    strategicObjectiveAccent(marker.type),
                    "TRACK SET: " + marker.label.toUpperCase(java.util.Locale.US)
            ));
            missionCount++;
            if (missionCount >= Math.max(0, maxMissionEntries)) break;
        }

        java.util.LinkedHashMap<String, Integer> countsBySection = new java.util.LinkedHashMap<>();
        appendMissionOrePatchEntries(ctx, out, seen, countsBySection, maxSignalsPerSection, showMissionOrePatches);
        appendCampaignContactEntries(ctx, out, seen, countsBySection, Math.max(2, maxSignalsPerSection + 2));

        java.util.EnumMap<FogOfWarSystem.SensorInterestKind, Integer> countsByKind =
                new java.util.EnumMap<>(FogOfWarSystem.SensorInterestKind.class);
        for (FogOfWarSystem.SensorInterestSignal signal : FogOfWarSystem.sensorInterestSignals(ctx)) {
            if (signal == null) continue;
            String section = sensorNetSection(signal.kind);
            int count = countsByKind.getOrDefault(signal.kind, 0);
            if (count >= Math.max(0, maxSignalsPerSection)) continue;
            String key = section + "|" + signal.kind + "|" + signal.label;
            if (!seen.add(key)) continue;
            countsByKind.put(signal.kind, count + 1);
            out.add(new SensorNetEntry(
                    section,
                    sensorNetTitle(signal),
                    sensorNetDetail(ctx, signal),
                    signal.x,
                    signal.y,
                    sensorSignalAccent(signal),
                    "SENSOR TRACK SET: " + sensorNetTitle(signal).toUpperCase(java.util.Locale.US)
            ));
            countsBySection.put(section, countsBySection.getOrDefault(section, 0) + 1);
        }
        appendCampaignPulseEntries(ctx, out, seen, countsBySection, maxSignalsPerSection);
        appendCampaignSensorSummaryEntry(ctx, out, seen);
        appendCampaignSignalEntries(ctx, CampaignSystem.discoverySignalSites(ctx), out, seen, countsBySection, maxSignalsPerSection);
        appendCampaignSignalEntries(ctx, CampaignSystem.recoverableWreckSignalSites(ctx), out, seen, countsBySection, maxSignalsPerSection);
        return out;
    }

    private static void appendCampaignContactEntries(GameContext ctx,
                                                     java.util.List<SensorNetEntry> out,
                                                     java.util.Set<String> seen,
                                                     java.util.Map<String, Integer> countsBySection,
                                                     int maxContacts) {
        java.util.List<CampaignSystem.CampaignContactReadout> contacts =
                CampaignSystem.campaignNearbyContactReadouts(ctx, Math.max(0, maxContacts));
        for (CampaignSystem.CampaignContactReadout contact : contacts) {
            if (contact == null) continue;
            String key = "CONTACT|" + contact.title + "|" + Math.round(contact.x) + "|" + Math.round(contact.y);
            if (!seen.add(key)) continue;
            out.add(new SensorNetEntry(
                    "NEARBY CONTACTS",
                    contact.title,
                    contact.detail,
                    contact.x,
                    contact.y,
                    contact.accent,
                    contact.banner
            ));
            countsBySection.put("NEARBY CONTACTS", countsBySection.getOrDefault("NEARBY CONTACTS", 0) + 1);
        }
    }

    private static void appendCampaignSensorSummaryEntry(GameContext ctx,
                                                         java.util.List<SensorNetEntry> out,
                                                         java.util.Set<String> seen) {
        java.util.List<String> lines = CampaignSystem.campaignSensorNetSummaryLines(ctx);
        if (lines.isEmpty()) return;
        String forecast = lines.get(0);
        String loss = lines.size() > 1 ? lines.get(1) : "";
        if (!seen.add("SENSOR|CAMPAIGN_SUMMARY")) return;
        double x = (ctx != null && ctx.player != null) ? ctx.player.x : 0.0;
        double y = (ctx != null && ctx.player != null) ? ctx.player.y : 0.0;
        out.add(new SensorNetEntry(
                "SENSOR",
                "Campaign Sensor Net",
                forecast + (loss.isBlank() ? "" : " | " + loss),
                x,
                y,
                new Color(132, 220, 255),
                "SENSOR NET SUMMARY"
        ));
    }

    private static void appendMissionOrePatchEntries(GameContext ctx,
                                                     java.util.List<SensorNetEntry> out,
                                                     java.util.Set<String> seen,
                                                     java.util.Map<String, Integer> countsBySection,
                                                     int maxSignalsPerSection,
                                                     boolean showMissionOrePatches) {
        if (ctx == null || ctx.asteroids == null || ctx.asteroids.isEmpty()) return;
        if (!showMissionOrePatches) return;

        java.util.LinkedHashMap<Long, OrePatchEntry> patches = new java.util.LinkedHashMap<>();
        double clusterSize = 760.0;
        for (Asteroid asteroid : ctx.asteroids) {
            if (asteroid == null || asteroid.ore <= 0) continue;
            if (!isInLoadedRenderZone(ctx, asteroid.x, asteroid.y)) continue;
            long cx = (long) Math.floor(asteroid.x / clusterSize);
            long cy = (long) Math.floor(asteroid.y / clusterSize);
            long key = (cx << 32) ^ (cy & 0xffffffffL);
            patches.computeIfAbsent(key, ignored -> new OrePatchEntry()).add(asteroid);
        }
        if (patches.isEmpty()) return;

        java.util.ArrayList<OrePatchEntry> ordered = new java.util.ArrayList<>(patches.values());
        ordered.removeIf(patch -> patch == null || patch.ore <= 0 || patch.asteroids <= 0);
        ordered.sort((a, b) -> Integer.compare(b.ore, a.ore));

        String section = "RESOURCE";
        int count = countsBySection.getOrDefault(section, 0);
        int limit = Math.max(0, maxSignalsPerSection);
        for (int i = 0; i < ordered.size() && count < limit; i++) {
            OrePatchEntry patch = ordered.get(i);
            double x = GameMath.clamp(patch.centerX(), 0.0, ctx.WORLD_W);
            double y = GameMath.clamp(patch.centerY(), 0.0, ctx.WORLD_H);
            String label = "Ore Patch " + (char) ('A' + Math.min(25, i));
            String key = section + "|ORE_PATCH|" + Math.round(x / 50.0) + "|" + Math.round(y / 50.0);
            if (!seen.add(key)) continue;
            int dist = (ctx.player == null) ? 0 : (int) Math.round(Math.hypot(x - ctx.player.x, y - ctx.player.y));
            out.add(new SensorNetEntry(
                    section,
                    label,
                    dist + "m  ORE " + patch.ore,
                    x,
                    y,
                    new Color(255, 210, 118),
                    "ORE PATCH ROUTE SET: " + label.toUpperCase(java.util.Locale.US)
            ));
            count++;
        }
        countsBySection.put(section, count);
    }

    private static String trimOverlayLine(String text, int maxWidthPx) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isBlank()) return "";
        int maxChars = Math.max(12, maxWidthPx / 6);
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static void appendCampaignPulseEntries(GameContext ctx,
                                                   java.util.List<SensorNetEntry> out,
                                                   java.util.Set<String> seen,
                                                   java.util.Map<String, Integer> countsBySection,
                                                   int maxSignalsPerSection) {
        for (CampaignSystem.CampaignSensorPulse pulse : CampaignSystem.campaignSensorPulses(ctx)) {
            if (pulse == null) continue;
            String section = pulse.relay ? "SENSOR" : "CONTACTS";
            int count = countsBySection.getOrDefault(section, 0);
            if (count >= Math.max(0, maxSignalsPerSection)) continue;
            String key = section + "|" + pulse.label + "|" + Math.round(pulse.x / 20.0) + "|" + Math.round(pulse.y / 20.0);
            if (!seen.add(key)) continue;
            String title = pulse.relay ? ("Relay: " + pulse.label) : ("Signature: " + pulse.label);
            String detail = ((ctx == null || ctx.player == null) ? 0 : (int) Math.round(Math.hypot(pulse.x - ctx.player.x, pulse.y - ctx.player.y)))
                    + "m  CONF " + (int) Math.round(pulse.strength * 100.0) + "%"
                    + (pulse.relay ? "  COVERAGE" : (pulse.hostile ? "  HOSTILE RETURN" : "  FRIENDLY RETURN"));
            out.add(new SensorNetEntry(
                    section,
                    title,
                    detail,
                    pulse.x,
                    pulse.y,
                    pulse.relay ? new Color(120, 196, 255) : (pulse.hostile ? new Color(255, 146, 126) : new Color(140, 224, 196)),
                    (pulse.relay ? "RELAY LOCK: " : "SENSOR RETURN: ") + pulse.label.toUpperCase(java.util.Locale.US)
            ));
            countsBySection.put(section, count + 1);
        }
    }

    private static void appendCampaignSignalEntries(GameContext ctx,
                                                    java.util.List<CampaignSystem.DiscoverySignalSite> sites,
                                                    java.util.List<SensorNetEntry> out,
                                                    java.util.Set<String> seen,
                                                    java.util.Map<String, Integer> countsBySection,
                                                    int maxSignalsPerSection) {
        if (sites == null || sites.isEmpty()) return;
        for (CampaignSystem.DiscoverySignalSite site : sites) {
            if (site == null) continue;
            String section = campaignSignalSection(site.kindTag);
            int count = countsBySection.getOrDefault(section, 0);
            if (count >= Math.max(0, maxSignalsPerSection)) continue;
            String key = section + "|" + site.kindTag + "|" + site.label;
            if (!seen.add(key)) continue;
            out.add(new SensorNetEntry(
                    section,
                    campaignSignalTitle(site),
                    campaignSignalDetail(ctx, site),
                    site.x,
                    site.y,
                    campaignSignalAccent(site.kindTag),
                    "TRACK SET: " + site.label.toUpperCase(java.util.Locale.US)
            ));
            countsBySection.put(section, count + 1);
        }
    }

    private static String sensorNetRow(SensorNetEntry entry) {
        if (entry == null) return "UNKNOWN CONTACT";
        String title = entry.title;
        if (title.length() > 18) {
            title = title.substring(0, 18).trim() + "...";
        }
        String detail = entry.detail;
        if (detail.length() > 20) {
            detail = detail.substring(0, 20).trim() + "...";
        }
        return detail.isBlank()
                ? title.toUpperCase(java.util.Locale.US)
                : title.toUpperCase(java.util.Locale.US) + "  |  " + detail.toUpperCase(java.util.Locale.US);
    }

    private static String sensorNetTitle(FogOfWarSystem.SensorInterestSignal signal) {
        if (signal == null) return "Unknown Signal";
        String label = (signal.label == null || signal.label.isBlank()) ? signal.kind.displayName() : signal.label.trim();
        return switch (signal.kind) {
            case ANOMALY -> "Anomaly: " + label;
            case ORE_VEIN -> "Ore Vein: " + label;
            case WRECKAGE -> "Wreckage: " + label;
            case CACHE -> "Cache: " + label;
            case CONTACT -> "Contact: " + label;
            case HAZARD -> "Hazard: " + label;
            case INTEL -> "Intel: " + label;
            case FLEET_ASSET -> "Fleet Asset: " + label;
            case INSTALLATION -> "Installation: " + label;
            case MASS_SIGNATURE -> "Mass Signature: " + label;
        };
    }

    private static String sensorNetDetail(GameContext ctx, FogOfWarSystem.SensorInterestSignal signal) {
        if (signal == null) return "";
        int dist = (ctx == null || ctx.player == null)
                ? 0
                : (int) Math.round(Math.hypot(signal.x - ctx.player.x, signal.y - ctx.player.y));
        String confidence = "CONF " + (int) Math.round(signal.strength * 100.0) + "%";
        return dist + "m  " + confidence;
    }

    private static String sensorNetSection(FogOfWarSystem.SensorInterestKind kind) {
        if (kind == null) return "CONTACTS";
        return switch (kind) {
            case ANOMALY, HAZARD, INTEL -> "ANOMALY";
            case WRECKAGE, CACHE, FLEET_ASSET -> "SALVAGE";
            case ORE_VEIN, MASS_SIGNATURE -> "RESOURCE";
            case CONTACT, INSTALLATION -> "CONTACTS";
        };
    }

    private static String campaignSignalSection(String kindTag) {
        String tag = (kindTag == null) ? "" : kindTag.trim().toUpperCase(java.util.Locale.US);
        return switch (tag) {
            case "ANOMALY", "DATA_RELAY", "MINEFIELD", "AMBUSH", "DRIFTING_TURRET" -> "ANOMALY";
            case "SALVAGE_HULK", "WRECK_FIELD", "SUPPLY_CACHE", "CACHE", "RECOVERABLE_WRECK", "FLEET_ASSET" -> "SALVAGE";
            case "ORE" -> "RESOURCE";
            case "REINFORCEMENT", "NEUTRAL_TRADER", "PRISON_BARGE" -> "CONTACTS";
            default -> "CONTACTS";
        };
    }

    private static String campaignSignalTitle(CampaignSystem.DiscoverySignalSite site) {
        if (site == null) return "Unknown Contact";
        String label = (site.label == null || site.label.isBlank()) ? "Unknown Contact" : site.label.trim();
        String tag = (site.kindTag == null) ? "" : site.kindTag.trim().toUpperCase(java.util.Locale.US);
        return switch (tag) {
            case "ANOMALY" -> "Anomaly: " + label;
            case "DATA_RELAY" -> "Intel: " + label;
            case "MINEFIELD", "AMBUSH", "DRIFTING_TURRET" -> "Hazard: " + label;
            case "SALVAGE_HULK", "WRECK_FIELD", "RECOVERABLE_WRECK" -> "Wreckage: " + label;
            case "SUPPLY_CACHE", "CACHE" -> "Cache: " + label;
            case "ORE" -> "Ore Vein: " + label;
            case "FLEET_ASSET" -> "Fleet Asset: " + label;
            case "REINFORCEMENT", "NEUTRAL_TRADER", "PRISON_BARGE" -> "Contact: " + label;
            default -> label;
        };
    }

    private static String campaignSignalDetail(GameContext ctx, CampaignSystem.DiscoverySignalSite site) {
        if (site == null) return "";
        int dist = (ctx == null || ctx.player == null)
                ? 0
                : (int) Math.round(Math.hypot(site.x - ctx.player.x, site.y - ctx.player.y));
        return dist + "m  AUTHORED";
    }

    private static Color campaignSignalAccent(String kindTag) {
        String tag = (kindTag == null) ? "" : kindTag.trim().toUpperCase(java.util.Locale.US);
        return switch (tag) {
            case "ANOMALY" -> new Color(180, 132, 255);
            case "DATA_RELAY" -> new Color(120, 196, 255);
            case "MINEFIELD", "AMBUSH", "DRIFTING_TURRET" -> new Color(255, 136, 126);
            case "SALVAGE_HULK", "WRECK_FIELD", "RECOVERABLE_WRECK" -> new Color(214, 224, 236);
            case "SUPPLY_CACHE", "CACHE", "ORE" -> new Color(245, 210, 126);
            case "FLEET_ASSET" -> new Color(196, 255, 164);
            case "REINFORCEMENT", "NEUTRAL_TRADER", "PRISON_BARGE" -> new Color(140, 224, 196);
            default -> new Color(132, 224, 255);
        };
    }

    private static Color strategicObjectiveAccent(CampaignSystem.ObjectiveMarkerType type) {
        if (type == null) return new Color(255, 220, 166);
        return switch (type) {
            case PRIMARY_OBJECTIVE, BOSS_TARGET -> new Color(255, 220, 166);
            case NEXT_ROUTE -> new Color(132, 224, 255);
            case ESCORT_TARGET, PROTECTED_ASSET -> new Color(132, 255, 176);
            case DESTROY_TARGET -> new Color(255, 124, 118);
            case CAPTURE_ZONE -> new Color(205, 170, 255);
            case OPTIONAL_OBJECTIVE -> new Color(255, 210, 120);
        };
    }

    private static Color sensorSignalAccent(FogOfWarSystem.SensorInterestSignal signal) {
        if (signal == null || signal.kind == null) return new Color(132, 224, 255);
        return switch (signal.kind) {
            case ANOMALY -> new Color(180, 132, 255);
            case ORE_VEIN -> new Color(255, 210, 118);
            case WRECKAGE -> new Color(214, 224, 236);
            case CACHE -> new Color(245, 210, 126);
            case CONTACT -> new Color(140, 224, 196);
            case HAZARD -> new Color(255, 136, 126);
            case INTEL -> new Color(120, 196, 255);
            case FLEET_ASSET -> new Color(196, 255, 164);
            case INSTALLATION -> new Color(255, 146, 124);
            case MASS_SIGNATURE -> new Color(132, 224, 255);
        };
    }

    private static java.util.List<String> activeSquadSummaryLines(GameContext ctx) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.ships == null) return out;
        int teamId = ctx.player.faction.teamId();
        java.util.List<Ship> leaders = new java.util.ArrayList<>();
        for (Ship s : ctx.ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.teamId() != teamId) continue;
            Integer leaderId = ctx.command.fleetSquadLeaderByShip.get(s.id);
            if (leaderId == null || leaderId != s.id) continue;
            leaders.add(s);
        }
        leaders.sort(java.util.Comparator.comparingInt(s -> ctx.command.fleetSquadIndexByShip.getOrDefault(s.id, Integer.MAX_VALUE)));
        for (Ship leader : leaders) {
            String label = ctx.command.fleetSquadLabelByShip.get(leader.id);
            if (label == null || label.isBlank()) continue;
            String role = ctx.command.fleetSquadRoleByShip.getOrDefault(leader.id, "Line");
            out.add(label + "  " + role.toUpperCase());
            if (out.size() >= 4) break;
        }
        return out;
    }

    private static java.util.List<GameContext.FleetCommMessage> recentFleetCommMessages(GameContext ctx, int maxCount) {
        java.util.ArrayList<GameContext.FleetCommMessage> out = new java.util.ArrayList<>();
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.fleetCommLog.isEmpty()) return out;
        int teamId = ctx.player.faction.teamId();
        for (int i = ctx.fleetCommLog.size() - 1; i >= 0 && out.size() < Math.max(0, maxCount); i--) {
            GameContext.FleetCommMessage msg = ctx.fleetCommLog.get(i);
            if (msg == null) continue;
            if (!msg.external && (msg.faction == null || msg.faction.teamId() != teamId)) continue;
            out.add(0, msg);
        }
        return out;
    }

    private static java.util.List<String> wrapLines(FontMetrics fm, String text, int maxWidth) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (fm == null || text == null || text.isBlank() || maxWidth <= 0) return out;
        String[] words = text.trim().split("\\s+");
        String line = "";
        for (String word : words) {
            if (word == null || word.isBlank()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(candidate) > maxWidth) {
                out.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private static Color squadColor(Faction faction, int alpha) {
        if (faction == null) return new Color(160, 200, 255, alpha);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(120, 196, 255, alpha);
            case ENEMY -> new Color(255, 126, 126, alpha);
            case TEAM_C -> new Color(144, 238, 154, alpha);
            case TEAM_D -> new Color(255, 212, 122, alpha);
            case BRIGHT_YELLOW -> new Color(255, 232, 86, alpha);
            case DARK_YELLOW -> new Color(210, 126, 46, alpha);
        };
    }

    private static void drawCampaignMarkers(GameContext ctx, Graphics2D g2,
                                            double minX, double minY, double maxX, double maxY) {
        if (ctx == null || g2 == null) return;
        Font oldFont = g2.getFont();
        for (CampaignSystem.CampaignLandmark landmark : CampaignSystem.landmarks(ctx)) {
            if (!isInLoadedRenderZone(ctx, landmark.x, landmark.y)) continue;
            drawCampaignLandmark(g2, landmark, minX, minY, maxX, maxY);
        }
        g2.setFont(oldFont);

        if (!CampaignSystem.hasCapturePoint(ctx)) return;
        double x = CampaignSystem.captureX(ctx);
        double y = CampaignSystem.captureY(ctx);
        double r = CampaignSystem.captureRadius(ctx);
        if (!isInLoadedRenderZone(ctx, x, y)) return;
        if (!isCircleVisible(x, y, r + 18.0, minX, minY, maxX, maxY)) return;

        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int ir = (int) Math.round(r);

        g2.setColor(new Color(255, 220, 120, 40));
        g2.fillOval(ix - ir, iy - ir, ir * 2, ir * 2);
        g2.setColor(new Color(255, 220, 120, 180));
        g2.drawOval(ix - ir, iy - ir, ir * 2, ir * 2);
        g2.drawLine(ix - 12, iy, ix + 12, iy);
        g2.drawLine(ix, iy - 12, ix, iy + 12);
    }

    private static void drawCampaignLandmark(Graphics2D g2, CampaignSystem.CampaignLandmark landmark,
                                             double minX, double minY, double maxX, double maxY) {
        if (g2 == null || landmark == null) return;
        double semanticRadius = Math.max(40.0, landmark.radius);
        if (!isCircleVisible(landmark.x, landmark.y, semanticRadius + 26.0, minX, minY, maxX, maxY)) return;

        int x = (int) Math.round(landmark.x);
        int y = (int) Math.round(landmark.y);
        int ir = (int) Math.round(MathUtil.clamp(semanticRadius * 0.23, 34.0, 112.0));
        Color fill = landmark.fillColor;
        Color edge = landmark.edgeColor;
        Color soft = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.min(48, fill.getAlpha()));
        Stroke oldStroke = g2.getStroke();

        if (landmark.type != CampaignSystem.LandmarkType.COLONY && landmark.type != CampaignSystem.LandmarkType.RING) {
            g2.setStroke(new BasicStroke(1.15f));
            g2.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(120, edge.getAlpha())));
            drawLandmarkArc(g2, x, y, ir + 18, 210.0, 84.0);
            drawLandmarkArc(g2, x, y, ir + 18, 20.0, 74.0);
            drawLandmarkBracket(g2, x, y, ir + 8, Math.max(12, ir / 3));
        }

        switch (landmark.type) {
            case PLANET, STAR -> {
                g2.setColor(soft);
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);
                g2.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(190, edge.getAlpha())));
                g2.drawOval(x - ir, y - ir, ir * 2, ir * 2);
                g2.setColor(new Color(255, 255, 255, landmark.type == CampaignSystem.LandmarkType.STAR ? 72 : 42));
                g2.fillOval(x - Math.max(4, ir / 8), y - Math.max(4, ir / 8), Math.max(8, ir / 4), Math.max(8, ir / 4));
                if (landmark.type == CampaignSystem.LandmarkType.STAR) {
                    g2.drawLine(x - ir / 2, y, x + ir / 2, y);
                    g2.drawLine(x, y - ir / 2, x, y + ir / 2);
                } else {
                    g2.drawOval(x - ir / 2, y - ir / 2, ir, ir);
                }
            }
            case RING -> {
                drawOrbitalExchangeStructure(g2, x, y, ir, fill, edge);
            }
            case RELAY -> {
                g2.setColor(soft);
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);
                g2.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(190, edge.getAlpha())));
                g2.drawOval(x - ir, y - ir, ir * 2, ir * 2);
                g2.drawOval(x - ir / 2, y - ir / 2, ir, ir);
                g2.drawLine(x - ir, y, x + ir, y);
                g2.drawLine(x, y - ir, x, y + ir);
            }
            case FORTRESS -> {
                g2.setColor(soft);
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);
                g2.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(190, edge.getAlpha())));
                g2.drawOval(x - ir, y - ir, ir * 2, ir * 2);
                int box = Math.max(26, (int) Math.round(ir * 0.85));
                g2.drawRect(x - box / 2, y - box / 2, box, box);
                g2.drawLine(x - box / 2, y, x + box / 2, y);
                g2.drawLine(x, y - box / 2, x, y + box / 2);
            }
            case COLONY -> drawArcologyLandmark(g2, x, y, ir, fill, edge);
            case FRONT, CORRIDOR -> {
                g2.setColor(soft);
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);
                g2.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(180, edge.getAlpha())));
                g2.drawOval(x - ir, y - ir, ir * 2, ir * 2);
                int wide = Math.max(30, (int) Math.round(ir * 1.35));
                int tall = Math.max(18, (int) Math.round(ir * 0.55));
                g2.drawOval(x - wide / 2, y - tall / 2, wide, tall);
                g2.drawLine(x - wide / 2, y, x + wide / 2, y);
            }
        }

        if (landmark.label != null && !landmark.label.isBlank()) {
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            FontMetrics titleFm = g2.getFontMetrics();
            int labelY = y - ir - 18;
            g2.setColor(new Color(18, 24, 34, 170));
            g2.fillRoundRect(x - titleFm.stringWidth(landmark.label) / 2 - 8, labelY - 11,
                    titleFm.stringWidth(landmark.label) + 16, 18, 8, 8);
            g2.setColor(new Color(242, 246, 255, 224));
            g2.drawString(landmark.label, x - titleFm.stringWidth(landmark.label) / 2, labelY + 2);
        }
        if (landmark.subtitle != null && !landmark.subtitle.isBlank()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 9));
            FontMetrics subtitleFm = g2.getFontMetrics();
            int subY = y - ir - 4;
            g2.setColor(new Color(210, 228, 242, 154));
            g2.drawString(landmark.subtitle, x - subtitleFm.stringWidth(landmark.subtitle) / 2, subY);
        }
        g2.setStroke(oldStroke);
    }

    private static void drawOrbitalExchangeStructure(Graphics2D g2, int x, int y, int ir, Color fill, Color edge) {
        Color haze = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.min(30, fill.getAlpha() + 8));
        Color line = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(170, edge.getAlpha()));
        Color faint = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(74, edge.getAlpha()));
        Stroke oldStroke = g2.getStroke();

        int iconR = MathUtil.clamp(ir, 28, 42);
        int wide = iconR * 3;
        int tall = Math.max(24, iconR);
        g2.setColor(haze);
        g2.fillOval(x - wide / 2, y - tall / 2, wide, tall);

        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(faint);
        g2.drawLine(x - wide / 2 + 6, y, x + wide / 2 - 6, y);
        g2.drawLine(x - iconR, y + tall / 3, x + iconR, y + tall / 3);

        drawSmallStationNode(g2, x - iconR, y + 1, iconR / 3, line, faint, true);
        drawSmallStationNode(g2, x, y - iconR / 5, iconR / 2, line, faint, false);
        drawSmallStationNode(g2, x + iconR, y + 2, iconR / 3, line, faint, true);

        int turretR = Math.max(5, iconR / 6);
        drawTinyDefenseTurret(g2, x - iconR / 2, y + tall / 3, turretR, line);
        drawTinyDefenseTurret(g2, x + iconR / 2, y + tall / 3, turretR, line);
        g2.setStroke(oldStroke);
    }

    private static void drawSmallStationNode(Graphics2D g2, int x, int y, int r, Color line, Color faint, boolean compact) {
        int coreW = compact ? Math.max(12, r * 2) : Math.max(18, r * 2);
        int coreH = compact ? Math.max(10, r + 5) : Math.max(16, r * 2);
        g2.setColor(new Color(10, 18, 28, 78));
        g2.fillRoundRect(x - coreW / 2, y - coreH / 2, coreW, coreH, 5, 5);
        g2.setColor(line);
        g2.drawRoundRect(x - coreW / 2, y - coreH / 2, coreW, coreH, 5, 5);
        g2.setColor(faint);
        g2.drawLine(x - coreW / 2 - r, y, x - coreW / 2, y);
        g2.drawLine(x + coreW / 2, y, x + coreW / 2 + r, y);
        g2.drawLine(x, y - coreH / 2 - r, x, y - coreH / 2);
        if (!compact) {
            g2.drawOval(x - r, y - r, r * 2, r * 2);
        }
    }

    private static void drawTinyDefenseTurret(Graphics2D g2, int x, int y, int r, Color line) {
        g2.setColor(new Color(10, 18, 28, 92));
        g2.fillOval(x - r, y - r, r * 2, r * 2);
        g2.setColor(line);
        g2.drawOval(x - r, y - r, r * 2, r * 2);
        g2.drawLine(x, y, x + r + 5, y - r / 2);
    }

    private static void drawArcologyLandmark(Graphics2D g2, int x, int y, int ir, Color fill, Color edge) {
        Color haze = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.min(30, fill.getAlpha() + 8));
        Color line = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(168, edge.getAlpha()));
        Color faint = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), Math.min(78, edge.getAlpha()));
        Stroke oldStroke = g2.getStroke();

        int iconR = MathUtil.clamp(ir, 28, 42);
        int wide = iconR * 3;
        int deckY = y + iconR / 3;
        g2.setColor(haze);
        g2.fillRoundRect(x - wide / 2, deckY - iconR, wide, iconR + 14, 18, 18);

        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(line);
        g2.drawLine(x - wide / 2, deckY, x + wide / 2, deckY);
        g2.drawLine(x - wide / 3, deckY + 7, x + wide / 3, deckY + 7);

        int towers = 4;
        for (int i = 0; i < towers; i++) {
            double t = (towers == 1) ? 0.5 : i / (double) (towers - 1);
            int tx = (int) Math.round(x - wide * 0.36 + t * wide * 0.72);
            int tw = Math.max(8, (int) Math.round(iconR * (0.16 + 0.05 * (i % 2))));
            int th = Math.max(18, (int) Math.round(iconR * (0.42 + 0.18 * Math.sin((i + 1) * 1.2))));
            g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.min(62, fill.getAlpha() + 24)));
            Polygon tower = new Polygon();
            tower.addPoint(tx - tw, deckY);
            tower.addPoint(tx - tw / 2, deckY - th);
            tower.addPoint(tx, deckY - th - Math.max(4, th / 6));
            tower.addPoint(tx + tw / 2, deckY - th);
            tower.addPoint(tx + tw, deckY);
            g2.fillPolygon(tower);
            g2.setColor(line);
            g2.drawPolygon(tower);
            g2.setColor(new Color(244, 252, 255, 48));
            g2.drawLine(tx, deckY - th - Math.max(4, th / 6), tx, deckY - 4);
        }

        drawSmallStationNode(g2, x - wide / 2 + iconR / 3, deckY - iconR / 5, iconR / 5, line, faint, true);
        drawSmallStationNode(g2, x + wide / 2 - iconR / 3, deckY - iconR / 5, iconR / 5, line, faint, true);
        drawTinyDefenseTurret(g2, x, deckY + 7, Math.max(5, iconR / 7), line);

        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(faint);
        for (int i = 0; i < 4; i++) {
            int px = x - wide / 2 + (i + 1) * wide / 5;
            g2.drawLine(px, deckY + 2, px - iconR / 6, deckY + Math.max(14, iconR / 2));
        }
        g2.setStroke(oldStroke);
    }

    private static void drawTiltedArc(Graphics2D g2, int x, int y, int wide, int tall,
                                      double tiltDeg, double startDeg, double extentDeg) {
        Graphics2D gx = (Graphics2D) g2.create();
        try {
            gx.rotate(Math.toRadians(tiltDeg), x, y);
            gx.draw(new java.awt.geom.Arc2D.Double(x - wide / 2.0, y - tall / 2.0,
                    wide, tall, startDeg, extentDeg, java.awt.geom.Arc2D.OPEN));
        } finally {
            gx.dispose();
        }
    }

    private static void drawLandmarkArc(Graphics2D g2, int x, int y, int radius, double startDeg, double extentDeg) {
        int size = radius * 2;
        g2.draw(new java.awt.geom.Arc2D.Double(x - radius, y - radius, size, size, startDeg, extentDeg, java.awt.geom.Arc2D.OPEN));
    }

    private static void drawLandmarkBracket(Graphics2D g2, int x, int y, int radius, int arm) {
        g2.drawLine(x - radius, y - arm, x - radius, y + arm);
        g2.drawLine(x + radius, y - arm, x + radius, y + arm);
        g2.drawLine(x - arm, y - radius, x + arm, y - radius);
        g2.drawLine(x - arm, y + radius, x + arm, y + radius);
    }

    private static void drawTransportSupportAuras(GameContext ctx, Graphics2D g2,
                                                  java.util.List<Ship> ships,
                                                  double minX, double minY, double maxX, double maxY) {
        if (ctx == null || g2 == null || ships == null) return;
        double zoom = Math.abs(g2.getTransform().getScaleX());
        if (zoom < 0.28) return;
        for (Ship s : ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.TRANSPORT && s.role != ShipRole.TRANSPORT_TITAN) continue;

            boolean titanTransport = s.role == ShipRole.TRANSPORT_TITAN;
            int r = (int) Math.round(Math.max(titanTransport ? 420.0 : 220.0, s.repairRange));
            if (!titanTransport && zoom < 0.42) continue;
            if (!isCircleVisible(s.x, s.y, r + 6.0, minX, minY, maxX, maxY)) continue;
            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y);

            Color ring = transportAuraColor(s.faction);
            Color fill = new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), titanTransport ? 22 : 18);
            Color edge = new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), titanTransport ? 124 : 82);

            g2.setColor(fill);
            g2.fillOval(x - r, y - r, r * 2, r * 2);
            g2.setColor(edge);
            g2.drawOval(x - r, y - r, r * 2, r * 2);
            if (titanTransport) {
                int r2 = (int) Math.round(r * 0.72);
                g2.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 104));
                g2.drawOval(x - r2, y - r2, r2 * 2, r2 * 2);
                g2.drawLine(x - 14, y, x + 14, y);
                g2.drawLine(x, y - 14, x, y + 14);
            }
        }
    }

    private static Color transportAuraColor(Faction faction) {
        if (faction == null) return new Color(170, 210, 255);
        return switch (faction) {
            case ALLY -> new Color(120, 210, 255);
            case ENEMY -> new Color(255, 130, 130);
            case TEAM_C -> new Color(138, 252, 154);
            case TEAM_D -> new Color(255, 198, 126);
            default -> new Color(170, 210, 255);
        };
    }

    private static void drawCampaignTransitionOverlay(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        if (!CampaignSystem.isTransitioning(ctx)) return;

        String label = CampaignSystem.transitionLabel(ctx);
        int secs = (int) Math.ceil(CampaignSystem.transitionSeconds(ctx));
        boolean fleetHub = CampaignSystem.isFleetHubSession(ctx);
        boolean awaitingHub = CampaignSystem.isAwaitingFleetHubChoice(ctx);
        String timer = fleetHub
                ? "Fleet hub open"
                : (awaitingHub ? "Fleet hub in " + Math.max(0, secs) + "s" : "Next sector in " + Math.max(0, secs) + "s");
        String top = CampaignSystem.transitionSummaryTop(ctx);
        String bottom = CampaignSystem.transitionSummaryBottom(ctx);

        int w = Math.min(1080, viewportW - 24);
        int x = (viewportW - w) / 2;
        int y = 10;

        g2.setFont(new Font("Consolas", Font.BOLD, 22));
        FontMetrics titleFm = g2.getFontMetrics();
        g2.setFont(new Font("Consolas", Font.PLAIN, 16));
        FontMetrics timerFm = g2.getFontMetrics();
        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        FontMetrics bodyFm = g2.getFontMetrics();
        if (label == null || label.isBlank()) label = "FLEET HANGAR";
        int textW = Math.max(260, w - 40);
        java.util.List<String> topLines = wrapLines(bodyFm, top, textW);
        java.util.List<String> bottomLines = wrapLines(bodyFm, bottom, textW);
        int bodyLineH = Math.max(16, bodyFm.getHeight());
        int bodyLines = topLines.size() + bottomLines.size();
        int h = 18 + titleFm.getHeight() + 4 + timerFm.getHeight() + 10 + Math.max(1, bodyLines) * bodyLineH + 12;

        if (!paintThemedHudFrame(g2, x, y, w, h,
                new Color(255, 214, 132, 190), ThemeArt.HUD_STANDARD_PANEL, 16)) {
            g2.setColor(new Color(0, 0, 0, 190));
            g2.fillRoundRect(x, y, w, h, 16, 16);
            g2.setColor(new Color(255, 255, 255, 180));
            g2.drawRoundRect(x, y, w, h, 16, 16);
        }
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, h);

        g2.setFont(new Font("Consolas", Font.BOLD, 22));
        g2.setColor(new Color(255, 230, 150, 230));
        int ty = inner.y;
        int tx1 = inner.x;
        g2.drawString(label, tx1, ty);

        g2.setFont(new Font("Consolas", Font.PLAIN, 16));
        g2.setColor(new Color(255, 255, 255, 220));
        int tx2 = inner.x + inner.width - timerFm.stringWidth(timer);
        g2.drawString(timer, tx2, ty);

        int rowY = inner.y + 26;
        if (!topLines.isEmpty()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(224, 236, 248, 220));
            for (String line : topLines) {
                g2.drawString(line, inner.x, rowY);
                rowY += bodyLineH;
            }
        }
        rowY += 4;
        if (!bottomLines.isEmpty()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 13));
            g2.setColor(new Color(255, 230, 170, 225));
            for (String line : bottomLines) {
                g2.drawString(line, inner.x, rowY);
                rowY += bodyLineH;
            }
        }
    }

    private static void drawCampaignMissionIntro(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        if (!CampaignSystem.shouldShowMissionIntro(ctx)) return;

        String title = CampaignSystem.missionIntroTitle(ctx);
        String body = CampaignSystem.missionIntroBody(ctx);
        double alphaFrac = CampaignSystem.missionIntroAlpha(ctx);
        int frameAlpha = (int) Math.round(170 + 45 * alphaFrac);

        int w = Math.min(860, viewportW - 60);
        int x = (viewportW - w) / 2;
        int y = 54;

        g2.setFont(new Font("Consolas", Font.BOLD, 20));
        FontMetrics titleFm = g2.getFontMetrics();
        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        FontMetrics bodyFm = g2.getFontMetrics();

        java.util.List<String> bodyLines = wrapMissionIntroBody(bodyFm, body, w - 34);
        int h = 24 + titleFm.getHeight() + 8 + Math.max(1, bodyLines.size()) * Math.max(16, bodyFm.getHeight()) + 18;

        g2.setColor(new Color(0, 0, 0, frameAlpha));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 214, 132, 210));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        g2.setFont(new Font("Consolas", Font.BOLD, 20));
        g2.setColor(new Color(255, 236, 180, 230));
        g2.drawString(title, x + 18, y + 30);

        int rowY = y + 58;
        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        g2.setColor(new Color(232, 240, 248, 225));
        for (String line : bodyLines) {
            g2.drawString(line, x + 18, rowY);
            rowY += Math.max(16, bodyFm.getHeight());
        }
    }

    private static java.util.List<String> wrapMissionIntroBody(FontMetrics fm, String body, int maxWidth) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (body == null || body.isBlank()) return out;
        for (String paragraph : body.split("\\R")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;
            out.addAll(wrapMissionIntroLine(fm, trimmed, maxWidth));
        }
        return out;
    }

    private static java.util.List<String> wrapMissionIntroLine(FontMetrics fm, String text, int maxWidth) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && fm.stringWidth(candidate) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static void drawModifierWorldTint(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        Color tint = CampaignSystem.worldTint(ctx);
        if (tint == null || tint.getAlpha() <= 0) return;
        g2.setColor(tint);
        g2.fillRect(0, 0, viewportW, viewportH);
    }

    private static void drawModifierChips(GameContext ctx, Graphics2D g2, int viewportW) {
        String[] chips = CampaignSystem.activeModifierLabels(ctx);
        if (chips == null || chips.length == 0) return;

        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();

        int x = viewportW - 18;
        int y = 16;
        for (String chip : chips) {
            if (chip == null || chip.isBlank()) continue;
            int w = fm.stringWidth(chip) + 16;
            int h = 20;
            int px = x - w;
            int py = y;

            g2.setColor(new Color(0, 0, 0, 145));
            g2.fillRoundRect(px, py, w, h, 10, 10);
            g2.setColor(new Color(255, 230, 150, 210));
            g2.drawRoundRect(px, py, w, h, 10, 10);
            g2.drawString(chip, px + 8, py + 14);

            y += h + 6;
        }
    }

    private static void drawShieldImpactExplosion(Graphics2D g2, Explosion e) {
        double rem = e.frac();
        double age = e.ageFrac();
        int outerR = (int) Math.round(6 + age * 14);
        int innerR = (int) Math.max(2, Math.round(2 + age * 7));

        int ringA = (int) MathUtil.clamp(180 * rem, 0, 210);
        int coreA = (int) MathUtil.clamp(140 * rem, 0, 170);

        g2.setColor(new Color(126, 222, 255, ringA));
        g2.drawOval((int) Math.round(e.x - outerR), (int) Math.round(e.y - outerR), outerR * 2, outerR * 2);

        g2.setColor(new Color(220, 248, 255, coreA));
        g2.fillOval((int) Math.round(e.x - innerR), (int) Math.round(e.y - innerR), innerR * 2, innerR * 2);
    }

    private static void drawDeathExplosion(Graphics2D g2, Explosion e) {
        double rem = e.frac();
        double age = e.ageFrac();

        int blastR = (int) Math.round(10 + age * 52);
        int coreR = (int) Math.round(5 + Math.min(1.0, age * 2.0) * 13);
        int ringR = (int) Math.round(18 + Math.max(0.0, age - 0.08) * 54);
        int smokeR = (int) Math.round(18 + Math.max(0.0, age - 0.20) * 52);

        int blastA = (int) MathUtil.clamp(232 * rem, 0, 240);
        int coreA = (int) MathUtil.clamp((age < 0.65 ? 248 : 188) * rem, 0, 248);
        int ringA = (int) MathUtil.clamp(Math.max(0.0, 1.0 - age * 0.74) * 178 * rem, 0, 190);
        int smokeA = (int) MathUtil.clamp(Math.max(0.0, age - 0.10) * 196 * rem, 0, 132);

        g2.setColor(new Color(255, 152, 88, blastA));
        g2.fillOval((int) Math.round(e.x - blastR), (int) Math.round(e.y - blastR), blastR * 2, blastR * 2);

        g2.setColor(new Color(255, 228, 180, coreA));
        g2.fillOval((int) Math.round(e.x - coreR), (int) Math.round(e.y - coreR), coreR * 2, coreR * 2);

        if (ringA > 4) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 206, 160, ringA));
            g2.drawOval((int) Math.round(e.x - ringR), (int) Math.round(e.y - ringR), ringR * 2, ringR * 2);
            g2.setStroke(old);
        }

        if (smokeA > 4) {
            g2.setColor(new Color(78, 78, 82, smokeA));
            g2.drawOval((int) Math.round(e.x - smokeR), (int) Math.round(e.y - smokeR), smokeR * 2, smokeR * 2);
        }
    }

    private static void drawSuperweaponBlastExplosion(Graphics2D g2, Explosion e) {
        double rem = e.frac();
        double age = e.ageFrac();

        int plasmaR = (int) Math.round(e.superweaponPlasmaRadius());
        int coreR = (int) Math.round(e.superweaponCoreRadius());
        int ring1R = (int) Math.round(e.superweaponRingRadius(0));
        int ring2R = (int) Math.round(e.superweaponRingRadius(1));
        int ring3R = (int) Math.round(e.superweaponRingRadius(2));
        int hazeR = (int) Math.round(e.superweaponHazeRadius());

        int plasmaA = (int) MathUtil.clamp(210 * rem, 0, 220);
        int coreA = (int) MathUtil.clamp((age < 0.55 ? 250 : 186) * rem, 0, 250);
        int ring1A = (int) MathUtil.clamp(235 * Math.max(0.0, 1.0 - age * 0.55) * rem, 0, 235);
        int ring2A = (int) MathUtil.clamp(205 * Math.max(0.0, 1.0 - age * 0.75) * rem, 0, 210);
        int ring3A = (int) MathUtil.clamp(168 * Math.max(0.0, 1.0 - age * 0.92) * rem, 0, 180);
        int hazeA = (int) MathUtil.clamp(118 * Math.max(0.0, 1.0 - age * 0.85) * rem, 0, 130);

        g2.setColor(new Color(255, 54, 54, plasmaA));
        g2.fillOval((int) Math.round(e.x - plasmaR), (int) Math.round(e.y - plasmaR), plasmaR * 2, plasmaR * 2);

        g2.setColor(new Color(255, 214, 190, coreA));
        g2.fillOval((int) Math.round(e.x - coreR), (int) Math.round(e.y - coreR), coreR * 2, coreR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke((float) e.superweaponRingStrokeWidth(0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 126, 126, ring1A));
        g2.drawOval((int) Math.round(e.x - ring1R), (int) Math.round(e.y - ring1R), ring1R * 2, ring1R * 2);

        g2.setStroke(new BasicStroke((float) e.superweaponRingStrokeWidth(1), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 186, 146, ring2A));
        g2.drawOval((int) Math.round(e.x - ring2R), (int) Math.round(e.y - ring2R), ring2R * 2, ring2R * 2);

        g2.setStroke(new BasicStroke((float) e.superweaponRingStrokeWidth(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 236, 210, ring3A));
        g2.drawOval((int) Math.round(e.x - ring3R), (int) Math.round(e.y - ring3R), ring3R * 2, ring3R * 2);
        g2.setStroke(old);

        if (hazeA > 4) {
            g2.setColor(new Color(120, 26, 26, hazeA));
            g2.drawOval((int) Math.round(e.x - hazeR), (int) Math.round(e.y - hazeR), hazeR * 2, hazeR * 2);
        }
    }

    private static void drawFinalDetonationExplosion(Graphics2D g2, Explosion e) {
        double rem = e.frac();
        double age = e.ageFrac();

        int plasmaR = (int) Math.round(e.finalDetonationPlasmaRadius());
        int coreR = (int) Math.round(e.finalDetonationCoreRadius());
        int ring1R = (int) Math.round(e.finalDetonationRingRadius(0));
        int ring2R = (int) Math.round(e.finalDetonationRingRadius(1));
        int ring3R = (int) Math.round(e.finalDetonationRingRadius(2));
        int hazeR = (int) Math.round(e.finalDetonationHazeRadius());

        int plasmaA = (int) MathUtil.clamp(220 * rem, 0, 232);
        int coreA = (int) MathUtil.clamp((age < 0.52 ? 252 : 188) * rem, 0, 252);
        int ring1A = (int) MathUtil.clamp(236 * Math.max(0.0, 1.0 - age * 0.58) * rem, 0, 240);
        int ring2A = (int) MathUtil.clamp(202 * Math.max(0.0, 1.0 - age * 0.74) * rem, 0, 210);
        int ring3A = (int) MathUtil.clamp(158 * Math.max(0.0, 1.0 - age * 0.92) * rem, 0, 172);
        int hazeA = (int) MathUtil.clamp(118 * Math.max(0.0, 1.0 - age * 0.84) * rem, 0, 128);

        g2.setColor(new Color(255, 86, 74, plasmaA));
        g2.fillOval((int) Math.round(e.x - plasmaR), (int) Math.round(e.y - plasmaR), plasmaR * 2, plasmaR * 2);

        g2.setColor(new Color(255, 226, 192, coreA));
        g2.fillOval((int) Math.round(e.x - coreR), (int) Math.round(e.y - coreR), coreR * 2, coreR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke((float) e.finalDetonationRingStrokeWidth(0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 148, 120, ring1A));
        g2.drawOval((int) Math.round(e.x - ring1R), (int) Math.round(e.y - ring1R), ring1R * 2, ring1R * 2);

        g2.setStroke(new BasicStroke((float) e.finalDetonationRingStrokeWidth(1), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 196, 152, ring2A));
        g2.drawOval((int) Math.round(e.x - ring2R), (int) Math.round(e.y - ring2R), ring2R * 2, ring2R * 2);

        g2.setStroke(new BasicStroke((float) e.finalDetonationRingStrokeWidth(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 234, 208, ring3A));
        g2.drawOval((int) Math.round(e.x - ring3R), (int) Math.round(e.y - ring3R), ring3R * 2, ring3R * 2);
        g2.setStroke(old);

        if (hazeA > 4) {
            g2.setColor(new Color(92, 22, 18, hazeA));
            g2.drawOval((int) Math.round(e.x - hazeR), (int) Math.round(e.y - hazeR), hazeR * 2, hazeR * 2);
        }
    }

    private static void drawStasisFieldExplosion(Graphics2D g2, Explosion e) {
        double rem = e.frac();
        int fieldR = (int) Math.round(e.stasisFieldRadius());
        int coreR = (int) Math.round(e.stasisFieldCoreRadius());
        int hazeR = (int) Math.round(fieldR * 1.08);

        int fieldA = (int) MathUtil.clamp(74 * (0.55 + rem * 0.45), 0, 92);
        int coreA = (int) MathUtil.clamp(146 * (0.40 + rem * 0.60), 0, 168);
        int ringA = (int) MathUtil.clamp(178 * (0.42 + rem * 0.58), 0, 196);
        int hazeA = (int) MathUtil.clamp(78 * rem, 0, 96);

        g2.setColor(new Color(118, 32, 32, fieldA));
        g2.fillOval((int) Math.round(e.x - fieldR), (int) Math.round(e.y - fieldR), fieldR * 2, fieldR * 2);

        g2.setColor(new Color(255, 108, 108, coreA));
        g2.fillOval((int) Math.round(e.x - coreR), (int) Math.round(e.y - coreR), coreR * 2, coreR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 196, 196, ringA));
        g2.drawOval((int) Math.round(e.x - fieldR), (int) Math.round(e.y - fieldR), fieldR * 2, fieldR * 2);
        g2.setStroke(old);

        if (hazeA > 4) {
            g2.setColor(new Color(86, 18, 18, hazeA));
            g2.drawOval((int) Math.round(e.x - hazeR), (int) Math.round(e.y - hazeR), hazeR * 2, hazeR * 2);
        }
    }

    private static void drawDestabilizerPulseExplosion(Graphics2D g2, Explosion e) {
        double rem = e.frac();
        double age = e.ageFrac();

        int waveR = (int) Math.round(e.destabilizerWaveRadius());
        int innerRingR = (int) Math.round(e.destabilizerInnerRingRadius());
        int outerRingR = (int) Math.round(e.destabilizerOuterRingRadius());
        int coronaR = (int) Math.round(e.destabilizerCoronaRadius());

        int waveA = (int) MathUtil.clamp(188 * Math.max(0.0, 1.0 - age * 0.55) * rem, 0, 200);
        int innerA = (int) MathUtil.clamp(230 * rem, 0, 235);
        int outerA = (int) MathUtil.clamp(168 * Math.max(0.0, 1.0 - age * 0.82) * rem, 0, 176);
        int coronaA = (int) MathUtil.clamp(220 * Math.max(0.0, 1.0 - age * 0.68) * rem, 0, 228);

        g2.setColor(new Color(96, 188, 255, waveA));
        g2.fillOval((int) Math.round(e.x - waveR), (int) Math.round(e.y - waveR), waveR * 2, waveR * 2);

        g2.setColor(new Color(232, 248, 255, coronaA));
        g2.fillOval((int) Math.round(e.x - coronaR), (int) Math.round(e.y - coronaR), coronaR * 2, coronaR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(4.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(156, 224, 255, innerA));
        g2.drawOval((int) Math.round(e.x - innerRingR), (int) Math.round(e.y - innerRingR), innerRingR * 2, innerRingR * 2);

        g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(214, 245, 255, outerA));
        g2.drawOval((int) Math.round(e.x - outerRingR), (int) Math.round(e.y - outerRingR), outerRingR * 2, outerRingR * 2);
        g2.setStroke(old);

        int spokeCount = 6;
        for (int i = 0; i < spokeCount; i++) {
            double theta = age * 1.3 + i * (Math.PI * 2.0 / spokeCount);
            double inner = Math.max(10.0, e.destabilizerCoronaRadius() * 0.72);
            double outer = Math.max(inner + 8.0, e.destabilizerWaveRadius() * 0.92);
            int x1 = (int) Math.round(e.x + Math.cos(theta) * inner);
            int y1 = (int) Math.round(e.y + Math.sin(theta) * inner);
            int x2 = (int) Math.round(e.x + Math.cos(theta) * outer);
            int y2 = (int) Math.round(e.y + Math.sin(theta) * outer);
            g2.setColor(new Color(228, 248, 255, (int) MathUtil.clamp(innerA * 0.72, 0, 190)));
            g2.drawLine(x1, y1, x2, y2);
        }
    }

    private static boolean isExplosionVisible(Explosion e, double minX, double minY, double maxX, double maxY) {
        if (e == null) return false;
        double radius = e.visualRadius();
        return isCircleVisible(e.x, e.y, radius, minX, minY, maxX, maxY);
    }

    private static boolean isCircleVisible(double x, double y, double radius,
                                           double minX, double minY, double maxX, double maxY) {
        double r = Math.max(0.0, radius);
        return x + r >= minX && x - r <= maxX && y + r >= minY && y - r <= maxY;
    }

    private static void updateDamageVfx(GameContext ctx) {
        if (ctx == null || ctx.ships == null) return;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive) {
                LAST_HP.remove(s);
                LAST_SHIELD.remove(s);
                continue;
            }

            Integer prevHp = LAST_HP.get(s);
            Double prevShield = LAST_SHIELD.get(s);
            // CollisionSystem already spawns per-hit impact effects.
            // Avoid duplicating hit VFX here to keep large fleet battles performant.
            if (prevShield != null && prevShield > s.shield) {
                // Intentionally no extra per-ship shield effect.
            }
            if (prevHp != null && prevHp > s.hp) {
                // Intentionally no extra per-ship hull effect.
            }

            LAST_HP.put(s, s.hp);
            LAST_SHIELD.put(s, s.shield);
        }
    }

    // (Removed reflection bridge; hangar tier is computed from base upgrades.)
}
