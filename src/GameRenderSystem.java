import java.awt.*;

public final class GameRenderSystem {
    private GameRenderSystem(){}

    private static final java.util.WeakHashMap<Ship, Integer> LAST_HP = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, Double> LAST_SHIELD = new java.util.WeakHashMap<>();

    public static void render(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        // Background (screen space)
        long seed = (ctx.config != null ? ctx.config.seed : 12345L);
        Renderer.drawSpaceBackground(g2, ctx.camX, ctx.camY, viewportW, viewportH, seed);
        drawModifierWorldTint(ctx, g2, viewportW, viewportH);
        double zoom = CameraSystem.normalizedZoom(ctx);

        // World space
        Graphics2D worldG = (Graphics2D) g2.create();
        worldG.scale(zoom, zoom);
        worldG.translate(-ctx.camX, -ctx.camY);

        worldG.setColor(new Color(255, 255, 255, 28));
        worldG.drawRect(0, 0, ctx.WORLD_W, ctx.WORLD_H);

        if (DevTools.isFancyVfxEnabled()) {
            updateDamageVfx(ctx);
        }

        Renderer.drawAsteroids(worldG, ctx.asteroids);
        Renderer.drawSalvage(worldG, ctx.salvage);
        Renderer.drawShips(worldG, ctx.ships);
        Renderer.drawProjectiles(worldG, ctx.projectiles);

        try { VFX.drawAll(worldG); } catch (Throwable ignored) {}

        try {
            for (Explosion e : Explosion.active) {
                double f = e.frac();
                int size = (e.maxT <= 0.20) ? 10 : 22;
                int a = (int) Math.round(180 * f);
                worldG.setColor(new Color(255, 200, 120, Math.max(0, Math.min(a, 220))));
                worldG.fillOval((int) Math.round(e.x - size / 2.0), (int) Math.round(e.y - size / 2.0), size, size);
            }
        } catch (Throwable ignored) {}

        Renderer.drawWorldMarkers(worldG, ctx.ships, ctx.lockedTarget);
        drawCampaignMarkers(ctx, worldG);
        worldG.dispose();

        int allyOre = EconomySystem.getOreTotalForFaction(ctx, Faction.ALLY);
        int enemyOre = EconomySystem.getOreTotalForFaction(ctx, Faction.ENEMY);
        boolean resRush = (ctx.config != null && ctx.config.mode == GameMode.RESOURCE_RUSH);

        Ship docked = EconomySystem.getDockedFriendlyBase(ctx);
        int hangarTier = UISystem.getMaxHangarTierForPlayer(ctx);
        int maxHangarTier = 3;
        int playerWingActive = CarrierSystem.countActiveWingByCarrier(ctx, ctx.player);
        int playerWingCap = (ctx.player != null && ctx.player.isCarrier) ? Math.max(0, ctx.player.maxFighters) : 0;
        int lockedWingActive = CarrierSystem.countActiveWingByCarrier(ctx, ctx.lockedTarget);
        int lockedWingCap = (ctx.lockedTarget != null && ctx.lockedTarget.isCarrier) ? Math.max(0, ctx.lockedTarget.maxFighters) : 0;
        String objectiveTitle = CampaignSystem.hudObjectiveTitle(ctx);
        String objectiveDetail = CampaignSystem.hudObjectiveDetail(ctx);
        if ((objectiveTitle == null || objectiveTitle.isBlank()) && LastStandSystem.isActive(ctx)) {
            objectiveTitle = LastStandSystem.hudTitle(ctx);
            objectiveDetail = LastStandSystem.hudDetail(ctx);
        }

        Renderer.drawHUD(
                g2,
                ctx.player,
                ctx.credits,
                hangarTier,
                (docked != null),
                ctx.shopOpen,
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
                zoom

        );
        drawModifierChips(ctx, g2, viewportW);

        Renderer.drawMinimap(g2, ctx.ships, ctx.player, viewportW, viewportH, ctx.waypointX, ctx.waypointY, ctx.mapPings);

        if (ctx.mapOpen) {
            Renderer.drawStrategicMap(g2, viewportW, viewportH, ctx.WORLD_W, ctx.WORLD_H, ctx.camX, ctx.camY,
                    CameraSystem.worldViewWidth(ctx, viewportW), CameraSystem.worldViewHeight(ctx, viewportH), ctx.player,
                    ctx.ships, ctx.asteroids, ctx.salvage, ctx.waypointX, ctx.waypointY, ctx.mapPings, ctx.eventBanner);
        }

        if (ctx.baseMenuOpen) {
            Ship base = EconomySystem.getDockedFriendlyBase(ctx);
            if (base != null) {
                BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(base, k -> new BaseUpgrades());
                Renderer.drawBaseUpgradeOverlay(g2, base.name, ctx.credits, base.oreStockpile,
                        up.hullLv, up.shieldLv, up.turretLv, up.miningLv, up.hangarLv,
                        maxHangarTier);
            }
        }

        drawCampaignTransitionOverlay(ctx, g2, viewportW, viewportH);

        if (ctx.state == GameState.PAUSED) {
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
// Dev debug overlay (F3)
if (DevTools.isDebugOverlay()) {
    try { DevOverlay.draw(g2, ctx, viewportW, viewportH); } catch (Throwable ignored) {}
}

    }

    private static void drawCampaignMarkers(GameContext ctx, Graphics2D g2) {
        if (!CampaignSystem.hasCapturePoint(ctx)) return;
        double x = CampaignSystem.captureX(ctx);
        double y = CampaignSystem.captureY(ctx);
        double r = CampaignSystem.captureRadius(ctx);

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

    private static void drawCampaignTransitionOverlay(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        if (!CampaignSystem.isTransitioning(ctx)) return;

        String label = CampaignSystem.transitionLabel(ctx);
        int secs = (int) Math.ceil(CampaignSystem.transitionSeconds(ctx));
        String timer = "Next sector in " + Math.max(0, secs) + "s";
        String top = CampaignSystem.transitionSummaryTop(ctx);
        String bottom = CampaignSystem.transitionSummaryBottom(ctx);

        int w = Math.min(620, viewportW - 80);
        int h = 148;
        int x = (viewportW - w) / 2;
        int y = viewportH - h - 36;

        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRoundRect(x, y, w, h, 16, 16);
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawRoundRect(x, y, w, h, 16, 16);

        g2.setFont(new Font("Consolas", Font.BOLD, 20));
        g2.setColor(new Color(255, 230, 150, 230));
        FontMetrics fm1 = g2.getFontMetrics();
        int tx1 = x + (w - fm1.stringWidth(label)) / 2;
        g2.drawString(label, tx1, y + 42);

        g2.setFont(new Font("Consolas", Font.PLAIN, 16));
        g2.setColor(new Color(255, 255, 255, 220));
        FontMetrics fm2 = g2.getFontMetrics();
        int tx2 = x + (w - fm2.stringWidth(timer)) / 2;
        g2.drawString(timer, tx2, y + 72);

        if (top != null && !top.isBlank()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(220, 235, 255, 220));
            FontMetrics fm3 = g2.getFontMetrics();
            int tx3 = x + (w - fm3.stringWidth(top)) / 2;
            g2.drawString(top, tx3, y + 98);
        }
        if (bottom != null && !bottom.isBlank()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(255, 230, 170, 225));
            FontMetrics fm4 = g2.getFontMetrics();
            int tx4 = x + (w - fm4.stringWidth(bottom)) / 2;
            g2.drawString(bottom, tx4, y + 120);
        }
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

            if (prevShield != null && prevShield > s.shield) {
                double ang = Math.random() * Math.PI * 2.0;
                double hx = s.x + Math.cos(ang) * (s.radius + 2);
                double hy = s.y + Math.sin(ang) * (s.radius + 2);
                VFX.spawnShieldRipple(hx, hy, s.radius + 6, new java.awt.Color(120, 220, 255));
            }

            if (prevHp != null && prevHp > s.hp) {
                double ang = Math.random() * Math.PI * 2.0;
                double hx = s.x + Math.cos(ang) * (s.radius * 0.85);
                double hy = s.y + Math.sin(ang) * (s.radius * 0.85);
                VFX.spawnHitSparks(hx, hy, Math.cos(ang), Math.sin(ang));
            }

            LAST_HP.put(s, s.hp);
            LAST_SHIELD.put(s, s.shield);
        }
    }

    // (Removed reflection bridge; hangar tier is computed from base upgrades.)
}
