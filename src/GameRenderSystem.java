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
        double cullPad = 220.0;
        double viewMinX = ctx.camX - cullPad;
        double viewMinY = ctx.camY - cullPad;
        double viewMaxX = ctx.camX + CameraSystem.worldViewWidth(ctx, viewportW) + cullPad;
        double viewMaxY = ctx.camY + CameraSystem.worldViewHeight(ctx, viewportH) + cullPad;

        // World space
        Graphics2D worldG = (Graphics2D) g2.create();
        worldG.scale(zoom, zoom);
        worldG.translate(-ctx.camX, -ctx.camY);

        worldG.setColor(new Color(255, 255, 255, 28));
        worldG.drawRect(0, 0, ctx.WORLD_W, ctx.WORLD_H);

        if (DevTools.isFancyVfxEnabled()) {
            updateDamageVfx(ctx);
        }

        ctx.perfDrawnAsteroids = Renderer.drawAsteroids(worldG, ctx.asteroids, ctx.player, viewMinX, viewMinY, viewMaxX, viewMaxY);
        if (DevTools.isDebugOverlay() && DevTools.isAsteroidHeatmapEnabled()) {
            Renderer.drawAsteroidDangerHeatmap(worldG, ctx.asteroids, viewMinX, viewMinY, viewMaxX, viewMaxY);
        }
        ctx.perfDrawnSalvage = Renderer.drawSalvage(worldG, ctx.salvage, viewMinX, viewMinY, viewMaxX, viewMaxY);
        drawTransportSupportAuras(ctx, worldG, viewMinX, viewMinY, viewMaxX, viewMaxY);
        ctx.perfDrawnShips = Renderer.drawShips(worldG, ctx.ships, viewMinX, viewMinY, viewMaxX, viewMaxY);
        WreckChunk.drawAll(worldG, viewMinX, viewMinY, viewMaxX, viewMaxY);
        ctx.perfDrawnProjectiles = Renderer.drawProjectiles(worldG, ctx.projectiles, viewMinX, viewMinY, viewMaxX, viewMaxY);
        Renderer.drawSuperweaponAimCue(worldG, ctx.player, ctx.cursorWorldX, ctx.cursorWorldY);
        Renderer.drawNpcSuperweaponAimCues(worldG, ctx.ships, ctx.player, viewMinX, viewMinY, viewMaxX, viewMaxY);

        ctx.perfTotalVfx = VFX.activeCount();
        try { ctx.perfDrawnVfx = VFX.drawAll(worldG, viewMinX, viewMinY, viewMaxX, viewMaxY); } catch (Throwable ignored) { ctx.perfDrawnVfx = 0; }

        ctx.perfTotalExplosions = Explosion.active.size();
        ctx.perfDrawnExplosions = 0;
        try {
            for (Explosion e : Explosion.active) {
                if (e == null) continue;
                if (!isExplosionVisible(e, viewMinX, viewMinY, viewMaxX, viewMaxY)) continue;
                ctx.perfDrawnExplosions++;
                if (e.kind == Explosion.Kind.SHIELD_HIT) {
                    drawShieldImpactExplosion(worldG, e);
                } else if (e.kind == Explosion.Kind.DESTABILIZER_PULSE) {
                    drawDestabilizerPulseExplosion(worldG, e);
                } else if (e.kind == Explosion.Kind.SUPERWEAPON_BLAST) {
                    drawSuperweaponBlastExplosion(worldG, e);
                } else if (e.kind == Explosion.Kind.FINAL_DETONATION) {
                    drawFinalDetonationExplosion(worldG, e);
                } else {
                    drawDeathExplosion(worldG, e);
                }
            }
        } catch (Throwable ignored) {}

        Renderer.drawWorldMarkers(worldG, ctx.ships, ctx.lockedTarget, ctx.fleetCommandShips, ctx.fleetSharedTargets,
                viewMinX, viewMinY, viewMaxX, viewMaxY);
        drawCampaignMarkers(ctx, worldG, viewMinX, viewMinY, viewMaxX, viewMaxY);
        TutorialSystem.drawWorldMarkers(ctx, worldG);
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
        if ((objectiveTitle == null || objectiveTitle.isBlank()) && TutorialSystem.isActive(ctx)) {
            objectiveTitle = TutorialSystem.hudTitle(ctx);
            objectiveDetail = TutorialSystem.hudDetail(ctx);
        }
        if ((objectiveTitle == null || objectiveTitle.isBlank()) && LastStandSystem.isActive(ctx)) {
            objectiveTitle = LastStandSystem.hudTitle(ctx);
            objectiveDetail = LastStandSystem.hudDetail(ctx);
        }
        String stationStatus = "STATIONS "
                + "C:" + (ctx.captainAutomation ? "AI" : "MAN") + "  "
                + "H:" + (ctx.helmAutomation ? "AI" : "MAN") + "  "
                + "T:" + (ctx.tacticalAutomation ? "AI" : "MAN") + "  "
                + "E:" + (ctx.engineeringAutomation ? "AI" : "MAN") + "  "
                + "S:" + (ctx.scienceAutomation ? "AI" : "MAN");
        String overlayStatus = activeOverlayLabel(ctx);
        String contextHint = buildContextHint(ctx, docked);

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
                zoom,
                stationStatus,
                ctx,
                ctx.hudDetail,
                contextHint,
                overlayStatus

        );
        TutorialSystem.drawOverlay(ctx, g2, viewportW, viewportH);
        drawVoiceCaption(ctx, g2, viewportW, viewportH);
        drawModifierChips(ctx, g2, viewportW);

        Renderer.drawMinimap(g2, ctx.ships, ctx.player, viewportW, viewportH, ctx.waypointX, ctx.waypointY, ctx.mapPings);
        TutorialSystem.drawMinimapOverlay(ctx, g2, viewportW, viewportH);

        if (ctx.mapOpen) {
            Renderer.drawStrategicMap(g2, viewportW, viewportH, ctx.WORLD_W, ctx.WORLD_H, ctx.camX, ctx.camY,
                    CameraSystem.worldViewWidth(ctx, viewportW), CameraSystem.worldViewHeight(ctx, viewportH), ctx.player,
                    ctx.ships, ctx.asteroids, ctx.salvage, ctx.waypointX, ctx.waypointY, ctx.mapPings, ctx.eventBanner);
            TutorialSystem.drawStrategicMapOverlay(ctx, g2, viewportW, viewportH);
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

        if (ctx.powerManagementOpen && ctx.player != null) {
            Renderer.drawPowerManagementOverlay(g2, ctx.player, ctx.powerManagementFocus);
        }

        if (ctx.crewStationsOpen && ctx.player != null) {
            Renderer.drawCrewStationsOverlay(g2, ctx);
        }

        if (ctx.flightDeckOpen && ctx.player != null) {
            Renderer.drawFlightDeckOverlay(g2, ctx.player, ctx.flightDeckFocus);
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

        // Persistent quick-access overlays bar.
        Renderer.drawCoreMenuBar(g2, ctx, viewportW, viewportH);

// Dev debug overlay (F3)
if (DevTools.isDebugOverlay()) {
    try { DevOverlay.draw(g2, ctx, viewportW, viewportH); } catch (Throwable ignored) {}
}

    }

    private static String activeOverlayLabel(GameContext ctx) {
        if (ctx == null) return "";
        if (ctx.shopOpen) return "OVERLAY: SHOP/LOADOUT";
        if (ctx.baseMenuOpen) return "OVERLAY: BASE UPGRADES";
        if (ctx.powerManagementOpen) return "OVERLAY: POWER MANAGEMENT";
        if (ctx.crewStationsOpen) return "OVERLAY: CREW STATIONS";
        if (ctx.flightDeckOpen) return "OVERLAY: FLIGHT DECK";
        if (ctx.mapOpen) return "OVERLAY: STRATEGIC MAP";
        if (ctx.state == GameState.PAUSED) return "OVERLAY: PAUSED";
        return "";
    }

    private static String buildContextHint(GameContext ctx, Ship dockedBase) {
        if (ctx == null || ctx.player == null) return "";
        String tutorialHint = TutorialSystem.contextHint(ctx);
        if (tutorialHint != null && !tutorialHint.isBlank()) return tutorialHint;
        Ship p = ctx.player;

        if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen || ctx.powerManagementOpen
                || ctx.crewStationsOpen || ctx.flightDeckOpen) {
            return "Overlay active: combat input blocked, station AI continues running. Press ESC to close.";
        }
        if (!p.alive || p.dying || p.hp <= 0) {
            return "";
        }
        if (ctx.playerTeleportCharging) {
            double t = Math.max(0.0, ctx.playerTeleportChargeRemaining);
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
        return "Use N to cycle HUD detail (FULL/COMPACT/MINIMAL).";
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
        if (!ctx.voiceCaptionsEnabled) return;
        if (ctx.voiceCaptionT <= 0.0 || ctx.voiceCaption == null || ctx.voiceCaption.isBlank()) return;

        String text = ctx.voiceCaption;
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();

        int w = Math.min(viewportW - 28, fm.stringWidth(text) + 24);
        int h = 30;
        int x = (viewportW - w) / 2;
        int y = viewportH - 90;

        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(205, 225, 255, 190));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        g2.setColor(new Color(245, 250, 255, 230));
        int tx = x + (w - fm.stringWidth(text)) / 2;
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, tx, ty);
    }
    private static void drawCampaignMarkers(GameContext ctx, Graphics2D g2,
                                            double minX, double minY, double maxX, double maxY) {
        if (!CampaignSystem.hasCapturePoint(ctx)) return;
        double x = CampaignSystem.captureX(ctx);
        double y = CampaignSystem.captureY(ctx);
        double r = CampaignSystem.captureRadius(ctx);
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

    private static void drawTransportSupportAuras(GameContext ctx, Graphics2D g2,
                                                  double minX, double minY, double maxX, double maxY) {
        if (ctx == null || g2 == null || ctx.ships == null) return;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.TRANSPORT) continue;

            int r = (int) Math.round(Math.max(220.0, s.repairRange));
            if (!isCircleVisible(s.x, s.y, r + 6.0, minX, minY, maxX, maxY)) continue;
            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y);

            Color ring = transportAuraColor(s.faction);
            Color fill = new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 26);
            Color mid = new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 62);
            Color edge = new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 130);

            g2.setColor(fill);
            g2.fillOval(x - r, y - r, r * 2, r * 2);
            g2.setColor(mid);
            g2.drawOval(x - r, y - r, r * 2, r * 2);
            int r2 = (int) Math.round(r * 0.66);
            g2.setColor(edge);
            g2.drawOval(x - r2, y - r2, r2 * 2, r2 * 2);
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

        int blastR = (int) Math.round(8 + age * 44);
        int coreR = (int) Math.round(4 + Math.min(1.0, age * 1.9) * 11);
        int smokeR = (int) Math.round(14 + Math.max(0.0, age - 0.24) * 42);

        int blastA = (int) MathUtil.clamp(220 * rem, 0, 230);
        int coreA = (int) MathUtil.clamp((age < 0.65 ? 240 : 180) * rem, 0, 240);
        int smokeA = (int) MathUtil.clamp(Math.max(0.0, age - 0.14) * 180 * rem, 0, 120);

        g2.setColor(new Color(255, 152, 88, blastA));
        g2.fillOval((int) Math.round(e.x - blastR), (int) Math.round(e.y - blastR), blastR * 2, blastR * 2);

        g2.setColor(new Color(255, 228, 180, coreA));
        g2.fillOval((int) Math.round(e.x - coreR), (int) Math.round(e.y - coreR), coreR * 2, coreR * 2);

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

        int plasmaA = (int) MathUtil.clamp(206 * rem, 0, 220);
        int coreA = (int) MathUtil.clamp((age < 0.52 ? 245 : 180) * rem, 0, 245);
        int ring1A = (int) MathUtil.clamp(225 * Math.max(0.0, 1.0 - age * 0.62) * rem, 0, 228);
        int ring2A = (int) MathUtil.clamp(188 * Math.max(0.0, 1.0 - age * 0.78) * rem, 0, 198);
        int ring3A = (int) MathUtil.clamp(142 * Math.max(0.0, 1.0 - age * 0.96) * rem, 0, 160);
        int hazeA = (int) MathUtil.clamp(104 * Math.max(0.0, 1.0 - age * 0.88) * rem, 0, 116);

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




