import java.awt.*;
import java.util.List;

public final class GameRenderSystem {
    private GameRenderSystem(){}

    private static final java.util.WeakHashMap<Ship, Integer> LAST_HP = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, Double> LAST_SHIELD = new java.util.WeakHashMap<>();

    public static void render(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        // Background (screen space)
        long seed = (ctx.config != null ? ctx.config.seed : 12345L);
        Renderer.drawSpaceBackground(g2, ctx.camX, ctx.camY, viewportW, viewportH, seed);

        // World space
        g2.translate(-ctx.camX, -ctx.camY);

        g2.setColor(new Color(255, 255, 255, 28));
        g2.drawRect(0, 0, ctx.WORLD_W, ctx.WORLD_H);

        if (DevTools.isFancyVfxEnabled()) {
            updateDamageVfx(ctx);
        }

        Renderer.drawAsteroids(g2, ctx.asteroids);
        Renderer.drawSalvage(g2, ctx.salvage);
        Renderer.drawShips(g2, ctx.ships);
        Renderer.drawProjectiles(g2, ctx.projectiles);

        try { VFX.drawAll(g2); } catch (Throwable ignored) {}

        try {
            for (Explosion e : Explosion.active) {
                double f = e.frac();
                int size = (e.maxT <= 0.20) ? 10 : 22;
                int a = (int) Math.round(180 * f);
                g2.setColor(new Color(255, 200, 120, Math.max(0, Math.min(a, 220))));
                g2.fillOval((int) Math.round(e.x - size / 2.0), (int) Math.round(e.y - size / 2.0), size, size);
            }
        } catch (Throwable ignored) {}

        Renderer.drawWorldMarkers(g2, ctx.ships, ctx.lockedTarget);

        // Back to screen space
        g2.translate(ctx.camX, ctx.camY);

        int allyOre = EconomySystem.getOreTotalForFaction(ctx, Faction.ALLY);
        int enemyOre = EconomySystem.getOreTotalForFaction(ctx, Faction.ENEMY);
        boolean resRush = (ctx.config != null && ctx.config.mode == GameMode.RESOURCE_RUSH);

        Ship docked = EconomySystem.getDockedFriendlyBase(ctx);
        int hangarTier = UISystem.getMaxHangarTierForPlayer(ctx);
        int maxHangarTier = 3;

        Renderer.drawHUD(
                g2,
                ctx.player,
                ctx.credits,
                hangarTier,
                (docked != null),
                ctx.shopOpen,
                ctx.autoLockTurrets,
                ctx.lockedTarget,
                resRush,
                allyOre,
                enemyOre,
                ctx.resourceGoal,
                ctx.gameOverText,
                ctx.eventBanner,
                ctx.eventBannerT,
                ctx.orePriceMul,
                ctx.orePriceT,
                ctx.miningMul,
                ctx.miningT,
                ctx.camX,
                ctx.camY,
                viewportW,
                viewportH

        );

        Renderer.drawMinimap(g2, ctx.ships, ctx.player, viewportW, viewportH, ctx.waypointX, ctx.waypointY, ctx.mapPings);

        if (ctx.mapOpen) {
            Renderer.drawStrategicMap(g2, viewportW, viewportH, ctx.WORLD_W, ctx.WORLD_H, ctx.camX, ctx.camY, ctx.player,
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
