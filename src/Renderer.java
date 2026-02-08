import java.awt.*;
import java.util.List;
import java.util.Random;

public class Renderer {

    // ------------------------------------------------------------
    // Option 8: Strategic map / waypoints / pings
    // ------------------------------------------------------------
    public static final class MapPing {
        public double x, y;
        public double t; // seconds remaining
        public int faction; // 0=player, 1=ally, 2=enemy

        public MapPing(double x, double y, double t, int faction) {
            this.x = x;
            this.y = y;
            this.t = t;
            this.faction = faction;
        }
    }

    public static Rectangle getStrategicMapRect(int viewW, int viewH) {
        int pad = 52;
        int w = Math.min(860, viewW - pad * 2);
        int h = Math.min(560, viewH - pad * 2);
        int x = (viewW - w) / 2;
        int y = (viewH - h) / 2;
        return new Rectangle(x, y, w, h);
    }



    private static String fmt1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    // NEW: simple deterministic starfield background (screen space)
    public static void drawSpaceBackground(Graphics2D g2, double camX, double camY, int viewW, int viewH, long seed) {
        double px = camX * 0.20;
        double py = camY * 0.20;

        int tile = 256;
        int startX = (int) Math.floor(px / tile) - 1;
        int startY = (int) Math.floor(py / tile) - 1;
        int endX = (int) Math.floor((px + viewW) / tile) + 1;
        int endY = (int) Math.floor((py + viewH) / tile) + 1;

        for (int tx = startX; tx <= endX; tx++) {
            for (int ty = startY; ty <= endY; ty++) {
                long mix = seed;
                mix ^= (long) tx * 0x9E3779B97F4A7C15L;
                mix ^= (long) ty * 0xC2B2AE3D27D4EB4FL;
                mix ^= (mix >>> 33);
                mix *= 0xff51afd7ed558ccdL;
                mix ^= (mix >>> 33);

                Random r = new Random(mix);

                int stars = 10 + r.nextInt(10);
                for (int i = 0; i < stars; i++) {
                    int sx = tx * tile + r.nextInt(tile);
                    int sy = ty * tile + r.nextInt(tile);

                    int x = (int) Math.round(sx - px);
                    int y = (int) Math.round(sy - py);

                    int size = 1 + r.nextInt(2);
                    int a = 40 + r.nextInt(90);

                    g2.setColor(new Color(255, 255, 255, a));
                    g2.fillRect(x, y, size, size);
                }
            }
        }
    }

    public static void drawShips(Graphics2D g2, List<Ship> ships) {
        for (Ship s : ships) {
            if (s.alive) drawShip(g2, s);
        }
    }

    // ------------------------------
    // Asteroids (obstacles/resources)
    // ------------------------------

    public static void drawAsteroids(Graphics2D g2, List<Asteroid> asteroids) {
        if (asteroids == null) return;
        for (Asteroid a : asteroids) {
            if (a == null) continue;

            int r = (int) Math.round(a.radius);
            int x = (int) Math.round(a.x);
            int y = (int) Math.round(a.y);

            double frac = (a.oreMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) a.ore / (double) a.oreMax));

            // Main body
            int baseA = 150;
            int shade = (int) Math.round(70 + 80 * (0.35 + 0.65 * frac));
            g2.setColor(new Color(shade, shade, shade, baseA));
            g2.fillOval(x - r, y - r, r * 2, r * 2);

            // Subtle rim
            g2.setColor(new Color(255, 255, 255, 28));
            g2.drawOval(x - r, y - r, r * 2, r * 2);

            // Ore glow
            if (a.ore > 0) {
                int ir = Math.max(6, (int) Math.round(r * 0.55));
                int alpha = (int) Math.round(30 + 120 * frac);
                g2.setColor(new Color(255, 220, 140, MathUtil.clamp(alpha, 0, 200)));
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);

                // A little"twist" highlight
                double ang = a.spin;
                int hx = (int) Math.round(x + Math.cos(ang) * ir * 0.65);
                int hy = (int) Math.round(y + Math.sin(ang) * ir * 0.65);
                g2.setColor(new Color(255, 255, 255, MathUtil.clamp((int) (20 + 80 * frac), 0, 120)));
                g2.fillOval(hx - 3, hy - 3, 6, 6);
            }

            // Rich vein highlight
            if (a.rich) {
                int rr = (int) Math.round(r * 1.25);
                g2.setColor(new Color(255, 220, 120, 34));
                g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
                int rr2 = (int) Math.round(r * 1.45);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.drawOval(x - rr2, y - rr2, rr2 * 2, rr2 * 2);
            }
        }
    }

    // ------------------------------
    // Salvage pickups (random events)
    // ------------------------------

    public static void drawSalvage(Graphics2D g2, List<Salvage> salvage) {
        if (salvage == null) return;
        for (Salvage s : salvage) {
            if (s == null || !s.alive()) continue;

            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y);
            int r = (int) Math.round(s.radius);

            // Soft glow
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillOval(x - r * 2, y - r * 2, r * 4, r * 4);

            // Diamond "crate"
            Polygon p = new Polygon();
            p.addPoint(x, y - r);
            p.addPoint(x + r, y);
            p.addPoint(x, y + r);
            p.addPoint(x - r, y);

            int a = (int) Math.round(160 + 80 * Math.max(0.0, Math.min(1.0, s.life / 25.0)));
            g2.setColor(new Color(220, 240, 255, MathUtil.clamp(a, 0, 240)));
            g2.fillPolygon(p);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawPolygon(p);

            // Tiny hint for valuable drops
            if (s.credits >= 500 || s.ore >= 80) {
                g2.setColor(new Color(255, 220, 120, 60));
                g2.drawOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);
            }
        }
    }


    public static void drawProjectiles(Graphics2D g2, List<Projectile> projectiles) {
        for (Projectile p : projectiles) {
            if (!p.alive) continue;

            if (p instanceof CIWSPellet pellet) {
                int r = (int) Math.round(Math.max(1.0, pellet.radius));
                int x = (int) Math.round(pellet.x);
                int y = (int) Math.round(pellet.y);

                g2.setColor(new Color(255, 255, 255, 220));
                g2.fillOval(x - r, y - r, r * 2, r * 2);

                double lx = pellet.x - Math.cos(pellet.angle) * 10;
                double ly = pellet.y - Math.sin(pellet.angle) * 10;
                g2.setColor(new Color(255, 255, 255, 140));
                g2.drawLine(x, y, (int) Math.round(lx), (int) Math.round(ly));
                continue;
            }

            if (p instanceof Missile m) {
                g2.setColor(new Color(120, 220, 255));
                int d = (int) Math.round(m.radius * 2);
                g2.fillOval((int) Math.round(m.x - m.radius), (int) Math.round(m.y - m.radius), d, d);
                double lx = m.x + Math.cos(m.angle) * (m.radius + 5);
                double ly = m.y + Math.sin(m.angle) * (m.radius + 5);
                g2.drawLine((int) Math.round(m.x), (int) Math.round(m.y), (int) Math.round(lx), (int) Math.round(ly));
            } else if (p instanceof EnergyBolt eb) {
                // Yamato 2199-style heavy energy bolt (thick luminous shot + glow trail)
                int x = (int) Math.round(eb.x);
                int y = (int) Math.round(eb.y);

                // Short trailing glow based on velocity (per-tick) for visibility
                int tx = (int) Math.round(eb.x - eb.vx * 6.0);
                int ty = (int) Math.round(eb.y - eb.vy * 6.0);

                g2.setColor(new Color(120, 220, 255, 120));
                g2.drawLine(tx, ty, x, y);

                // Thicker core
                int r = (int) Math.round(Math.max(2.0, eb.radius));
                g2.setColor(new Color(190, 245, 255, 220));
                g2.fillOval(x - r, y - r, r * 2, r * 2);

                // Small forward "spark" to make direction obvious
                int fx = (int) Math.round(eb.x + Math.cos(eb.angle) * (r + 6));
                int fy = (int) Math.round(eb.y + Math.sin(eb.angle) * (r + 6));
                g2.setColor(new Color(220, 255, 255, 180));
                g2.drawLine(x, y, fx, fy);
            } else {
                // Bullet / generic projectile with a small motion trail
                int r = (int) Math.round(Math.max(1.0, p.radius));
                int x = (int) Math.round(p.x);
                int y = (int) Math.round(p.y);

                int tx = (int) Math.round(p.x - p.vx * 3.0);
                int ty = (int) Math.round(p.y - p.vy * 3.0);

                g2.setColor(new Color(255, 255, 160, 120));
                g2.drawLine(tx, ty, x, y);

                g2.setColor(new Color(255, 255, 180, 220));
                g2.fillOval(x - r, y - r, r * 2, r * 2);
            }
        }
    }

    public static void drawHUD(Graphics2D g2, Player player, int credits, int hangarTier, boolean dockedAtBase, boolean shopOpen, boolean autoLock, Ship lockedTarget,
                               boolean resourceRush, int allyOre, int enemyOre, int goal, String gameOverText,
                               String eventBanner, double eventBannerT, double orePriceMul, double orePriceT, double miningMul, double miningT,
                               double camX, double camY, int viewW, int viewH) {
        int x = 14;
        int y = 18;

        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        g2.setColor(new Color(255, 255, 255, 220));

        g2.drawString("SHIP: " + (player.role == null ? "" : player.role.name()), x, y);
        y += 15;

        g2.drawString("CREDITS: " + credits, x, y);
        y += 30;

        g2.drawString("HANGAR TIER: " + hangarTier + "  (dock + B to upgrade)", x, y);

        // Cargo / mining
        if (player.cargoMax > 0) {
            g2.drawString("CARGO: " + player.cargo + " / " + player.cargoMax + "   (Hold F to mine)", x, y);
            y += 18;
            if (dockedAtBase) {
                g2.setColor(new Color(160, 220, 255, 220));
                g2.drawString("DOCKED: Press B for Base Upgrades (1-5)", x, y);
                g2.setColor(new Color(255, 255, 255, 220));
            }
            y += 18;
        }


        // Event modifiers
        if (Math.abs(orePriceMul - 1.0) > 0.01 && orePriceT > 0) {
            g2.drawString("ORE PRICE: x" + fmt1(orePriceMul) + "  (" + (int) Math.ceil(orePriceT) + "s)", x, y);
            y += 18;
        }
        if (Math.abs(miningMul - 1.0) > 0.01 && miningT > 0) {
            g2.drawString("MINING RATE: x" + fmt1(miningMul) + "  (" + (int) Math.ceil(miningT) + "s)", x, y);
            y += 18;
        }

        if (resourceRush) {
            g2.drawString("RESOURCE RUSH: ALLY " + allyOre + "  ENEMY " + enemyOre + "  GOAL " + goal, x, y);
            y += 18;
            if (gameOverText != null && !gameOverText.isBlank()) {
                g2.setFont(new Font("Consolas", Font.BOLD, 18));
                g2.setColor(new Color(255, 255, 255, 220));
                g2.drawString(gameOverText, x, y + 6);
                g2.setFont(new Font("Consolas", Font.PLAIN, 14));
                g2.setColor(new Color(255, 255, 255, 220));
                y += 24;
            }
        }

        g2.drawString("HP: " + player.hp + " / " + player.hpMax, x, y);
        int barW = 240;
        int barH = 10;

        int hpY = y + 8;
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRect(x, hpY, barW, barH);
        double hpFrac = player.hpMax <= 0 ? 0 : Math.max(0, Math.min(1, (double) player.hp / player.hpMax));
        g2.setColor(new Color(80, 255, 120, 210));
        g2.fillRect(x + 1, hpY + 1, (int) Math.round((barW - 1) * hpFrac), barH - 1);

        int shY = hpY + 18;
        if (player.shieldActive && player.shieldMax > 0) {
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRect(x, shY, barW, barH);
            double shFrac = Math.max(0, Math.min(1, player.shield / player.shieldMax));
            g2.setColor(new Color(120, 200, 255, 210));
            g2.fillRect(x + 1, shY + 1, (int) Math.round((barW - 1) * shFrac), barH - 1);
        }

        y = 200;
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("LMB: guns   RMB: missiles", x, y);
        y += 18;
        g2.drawString("L: lock under mouse   [ ]: cycle targets   T: auto-lock", x, y);
        y += 18;
        g2.drawString("TAB: shop/loadout (5-9 upgrades, F1-F9/F11-F12 hulls)   B: base upgrades", x, y);
        y += 18;
        g2.drawString("Q: missile salvo   E: shield overcharge   F: mine", x, y);
        y += 18;
        g2.drawString("ESC: pause/resume   Alt+Enter: fullscreen", x, y);

        y += 22;
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("AUTO-LOCK: " + (autoLock ? "ON" : "OFF"), x, y);
        y += 18;

        if (lockedTarget == null || !lockedTarget.alive) {
            g2.drawString("LOCK: None", x, y);
        } else {
            double dx = lockedTarget.x - player.x;
            double dy = lockedTarget.y - player.y;
            int dist = (int) Math.round(Math.hypot(dx, dy));

            String role = (lockedTarget.role == null ? "" : lockedTarget.role.name());
            String fac  = (lockedTarget.faction == null ? "" : lockedTarget.faction.name());
            String hp   = lockedTarget.hp + "/" + lockedTarget.hpMax;

            // Color the lock line slightly by faction for readability.
            if (lockedTarget.faction == Faction.ENEMY) g2.setColor(new Color(255, 170, 170, 220));
            else if (lockedTarget.faction == Faction.ALLY) g2.setColor(new Color(170, 220, 255, 220));
            else g2.setColor(new Color(255, 255, 255, 220));

            g2.drawString("LOCK: " + lockedTarget.name + "  " + role + "  " + fac + "  HP " + hp + "  D " + dist, x, y);
            g2.setColor(new Color(255, 255, 255, 170));

            drawOffscreenTargetIndicator(g2, lockedTarget, camX, camY, viewW, viewH);
        }
        y += 18;// Top-center event banner
        if (eventBanner != null && !eventBanner.isBlank() && eventBannerT > 0) {
            int bw = 720;
            int bh = 34;
            int bx = (g2.getClipBounds().width - bw) / 2;
            int by = 10;

            int a = (int) Math.round(60 + 140 * Math.max(0.0, Math.min(1.0, eventBannerT / 3.0)));
            g2.setColor(new Color(0, 0, 0, MathUtil.clamp(a, 0, 190)));
            g2.fillRoundRect(bx, by, bw, bh, 14, 14);
            g2.setColor(new Color(255, 255, 255, 210));
            g2.setFont(new Font("Consolas", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            int tx = bx + (bw - fm.stringWidth(eventBanner)) / 2;
            int ty = by + 22;
            g2.drawString(eventBanner, tx, ty);

            // restore
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(255, 255, 255, 220));
        }



        if (shopOpen) {
            drawShopOverlay(g2, player, credits, hangarTier);
        }
    }

    public static void drawWorldMarkers(Graphics2D g2, List<Ship> ships, Ship lockedTarget) {
        if (lockedTarget != null && lockedTarget.alive) {
            int x = (int) Math.round(lockedTarget.x);
            int y = (int) Math.round(lockedTarget.y);
            int rr = (int) Math.round(lockedTarget.radius + 18);
            g2.setColor(new Color(255, 255, 255, 170));
            g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
            g2.drawLine(x - rr, y, x - rr + 10, y);
            g2.drawLine(x + rr, y, x + rr - 10, y);
            g2.drawLine(x, y - rr, x, y - rr + 10);
            g2.drawLine(x, y + rr, x, y + rr - 10);
        }

        if (ships == null) return;
        for (Ship s : ships) {
            if (!s.alive) continue;
            if (s.role != ShipRole.BASE) continue;

            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y - s.radius - 26);
            int w = 110;
            int h = 8;

            double p = Math.max(0, Math.min(1, s.captureProgress));

            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(x - w / 2, y, w, h, 8, 8);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(x - w / 2, y, w, h, 8, 8);

            g2.setColor(new Color(9, 189, 67, 200));
            g2.fillRoundRect(x - w / 2 + 1, y + 1, (int) Math.round((w - 2) * p), h - 2, 7, 7);

            g2.setColor(new Color(255, 90, 90, 110));
            int start = x - w / 2 + 1 + (int) Math.round((w - 2) * p);
            int rem = (x + w / 2 - 1) - start;
            if (rem > 0) g2.fillRoundRect(start, y + 1, rem, h - 2, 7, 7);
        }
    }


    private static void drawShopOverlay(Graphics2D g2, Player player, int credits, int hangarTier) {
        // Step 4B: "Shop clarity"
        // - Show what upgrades do (with current -> next deltas)
        // - Highlight affordability / requirements
        // - Keep layout readable in fullscreen by anchoring to bottom-left.

        Rectangle clip = g2.getClipBounds();
        int viewW = clip.width;
        int viewH = clip.height;

        int w = 700;
        int h = 700;
        int x = 10;
        int y = Math.max(40, viewH - h - 150);

        // Panel
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        // Header
        g2.setFont(new Font("Consolas", Font.BOLD, 15));
        g2.setColor(new Color(255, 255, 255, 230));
        g2.drawString("SHOP / LOADOUT", x + 14, y + 26);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 150));
        g2.drawString("TAB/ESC close   1-9 buy   F-keys swap hull", x + 14, y + 44);

        // Readouts
        int ty = y + 70;
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Credits: " + credits, x + 14, ty);
        g2.drawString("Hangar Tier: " + hangarTier, x + 190, ty);
        g2.drawString("Hull: " + (player.role == null ? "UNKNOWN" : player.role.name()), x + 350, ty);

        // Divider
        ty += 14;
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawLine(x + 14, ty, x + w - 14, ty);
        ty += 20;

        // ------------------------------
        // Upgrades 5-9
        // ------------------------------
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString("UPGRADES", x + 14, ty);
        ty += 18;

        int gunCount = 0;
        int missileCount = 0;
        if (player.turrets != null) {
            for (Turret t : player.turrets) {
                if (t == null) continue;
                if (t.kind == Turret.Kind.GUN) gunCount++;
                else if (t.kind == Turret.Kind.MISSILE) missileCount++;
            }
        }

        // 5: Hull +10
        {
            int cost = 60;
            boolean can = credits >= cost;
            String detail = "HP " + player.hpMax + " \u2192 " + (player.hpMax + 10);
            drawShopLine(g2, x + 14, ty, "5", "Hull Plating", detail, cost, can, true, null);
            ty += 22;
        }

        // 6: Shield +12 / regen +0.3
        {
            int cost = 70;
            boolean available = player.shieldActive && player.shieldMax > 0;
            boolean can = available && credits >= cost;
            String detail = available
                    ? ("Shield " + (int) Math.round(player.shieldMax) + " \u2192 " + (int) Math.round(player.shieldMax + 12)
                    + "   Regen " + fmt1(player.shieldRegen) + " \u2192 " + fmt1(player.shieldRegen + 0.3))
                    : "Unavailable (this hull has no shields)";
            drawShopLine(g2, x + 14, ty, "6", "Shield Array", detail, cost, can, available, null);
            ty += 22;
        }

        // 7: Add gun turret
        {
            int cost = 100;
            boolean can = credits >= cost;
            String detail = "Gun turrets " + gunCount + " \u2192 " + (gunCount + 1);
            drawShopLine(g2, x + 14, ty, "7", "Add Gun Turret", detail, cost, can, true, null);
            ty += 22;
        }

        // 8: Add missile rack
        {
            int cost = 140;
            boolean can = credits >= cost;
            String detail = "Missile racks " + missileCount + " \u2192 " + (missileCount + 1);
            drawShopLine(g2, x + 14, ty, "8", "Add Missile Rack", detail, cost, can, true, null);
            ty += 22;
        }

        // 9: CIWS upgrade
        {
            int cost = 120;
            boolean available = player.hasCIWS;
            boolean can = available && credits >= cost;

            double nextQ = Math.min(1.0, player.ciwsQuality + 0.20);
            double nextRange = Math.min(380.0, player.ciwsRange + 25.0);
            int nextPellets = Math.min(8, player.ciwsPelletsPerBurst + 1);
            double nextCd = Math.max(0.04, player.ciwsCooldown - 0.01);

            String detail = available
                    ? ("Quality " + fmt1(player.ciwsQuality) + " \u2192 " + fmt1(nextQ)
                    + "   Range " + (int) Math.round(player.ciwsRange) + " \u2192 " + (int) Math.round(nextRange)
                    + "   Burst " + player.ciwsPelletsPerBurst + " \u2192 " + nextPellets
                    + "   CD " + fmt1(player.ciwsCooldown) + " \u2192 " + fmt1(nextCd))
                    : "Unavailable (this hull has no CIWS)";
            drawShopLine(g2, x + 14, ty, "9", "Upgrade CIWS", detail, cost, can, available, null);
            ty += 26;
        }

        // Divider
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawLine(x + 14, ty - 10, x + w - 14, ty - 10);

        // ------------------------------
        // Hull swaps (F-keys)
        // ------------------------------
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString("HULL SWAP", x + 14, ty + 6);
        ty += 24;

        // Helper to draw hull option
        java.util.function.BiFunction<ShipRole, Integer, Integer> reqTier = (role, unused) -> switch (role) {
            case PATROL, PICKET, FRIGATE, MISSILE_BOAT, CIWS_CORVETTE -> 0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 1;
            case BATTLECRUISER, BATTLESHIP, STEALTH_SHIP -> 2;
            case DREADNOUGHT, CARRIER, DRONE_CARRIER, TRANSPORT -> 3;
            default -> 0;
        };

        ty = drawHullLine(g2, x + 14, ty, "F1", ShipRole.PATROL, 0, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F2", ShipRole.PICKET, 180, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F3", ShipRole.FRIGATE, 0, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F4", ShipRole.MISSILE_BOAT, 300, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F5", ShipRole.CIWS_CORVETTE, 250, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F6", ShipRole.LIGHT_CRUISER, 700, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F7", ShipRole.MEDIUM_CRUISER, 950, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F8", ShipRole.BATTLECRUISER, 1600, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F9", ShipRole.BATTLESHIP, 2200, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F11", ShipRole.STEALTH_SHIP, 1200, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F12", ShipRole.DREADNOUGHT, 3200, credits, hangarTier, player, reqTier);

        // Footer hint
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawString("Tip: If a hull is locked, upgrade a friendly base (B) to raise hangar tier.", x + 14, y + h - 16);
    }

    private static void drawShopLine(Graphics2D g2, int x, int y,
                                     String key, String title, String detail,
                                     int cost, boolean canAfford, boolean available, String rightTag) {

        // Key capsule
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y - 12, 30, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRoundRect(x, y - 12, 30, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString(key, x + 8, y + 2);

        int tx = x + 38;

        // Title
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(available ? new Color(255, 255, 255, 220) : new Color(255, 255, 255, 110));
        g2.drawString(title, tx, y + 2);

        // Detail
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(available ? new Color(255, 255, 255, 170) : new Color(255, 255, 255, 95));
        g2.drawString(detail, tx, y + 18);

        // Cost + tag (right aligned)
        String costStr = "$" + cost;
        if (rightTag != null && !rightTag.isBlank()) costStr = rightTag + "  " + costStr;

        FontMetrics fm = g2.getFontMetrics();
        int rightX = x + 540;
        int costW = fm.stringWidth(costStr);

        if (!available) {
            g2.setColor(new Color(255, 255, 255, 90));
        } else if (canAfford) {
            g2.setColor(new Color(120, 255, 170, 210));
        } else {
            g2.setColor(new Color(255, 120, 120, 210));
        }
        g2.drawString(costStr, rightX - costW, y + 2);
    }

    private static int drawHullLine(Graphics2D g2, int x, int y, String key, ShipRole role, int cost,
                                    int credits, int hangarTier, Player player,
                                    java.util.function.BiFunction<ShipRole, Integer, Integer> reqTier) {

        int req = reqTier.apply(role, 0);
        boolean meets = hangarTier >= req;
        boolean canAfford = credits >= cost;
        boolean current = player.role == role;

        String title = role == null ? "UNKNOWN" : role.name();
        String detail = "Requires Tier " + req + (req == 0 ? "" : "  (upgrade base)");
        String tag = current ? "CURRENT" : ("T" + req);

        // Color for requirement fail vs afford fail
        boolean available = meets;
        boolean canBuy = meets && canAfford;

        drawShopLine(g2, x, y, key, title, detail, cost, canBuy, available, tag);

        // Extra hint if locked by hangar tier
        if (!meets) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            g2.setColor(new Color(255, 200, 120, 200));
            g2.drawString("Locked: need hangar tier " + req, x + 38, y + 34);
            return y + 40;
        }

        return y + 22;
    }




    public static void drawBaseUpgradeOverlay(Graphics2D g2, String baseName, int credits, int baseOre,
                                              int hullLv, int shieldLv, int turretLv, int miningLv, int hangarLv,
                                              int maxHangarTier) {
        // "B" style: a diegetic sci-fi console panel (glow edges, grid, bars, subtle scanline).
        int w = 520;
        int h = 284;
        int pad = 22;
        int viewW = g2.getClipBounds().width;
        int x = viewW - w - pad;
        int y = 240;

        double t = System.nanoTime() / 1_000_000_000.0;
        int glowA = 55 + (int) Math.round(25 * (0.5 + 0.5 * Math.sin(t * 2.2)));

        // Outer glow
        g2.setColor(new Color(90, 220, 255, MathUtil.clamp(glowA, 30, 90)));
        g2.fillRoundRect(x - 4, y - 4, w + 8, h + 8, 24, 24);

        // Panel body
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(x, y, w, h, 20, 20);

        // Inner border
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(x, y, w, h, 20, 20);

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 18));
        for (int gx = x + 14; gx < x + w - 14; gx += 28) g2.drawLine(gx, y + 40, gx, y + h - 14);
        for (int gy = y + 40; gy < y + h - 14; gy += 22) g2.drawLine(x + 14, gy, x + w - 14, gy);

        // Header bar
        g2.setColor(new Color(20, 70, 90, 190));
        g2.fillRoundRect(x + 10, y + 10, w - 20, 26, 14, 14);
        g2.setColor(new Color(90, 220, 255, 110));
        g2.drawRoundRect(x + 10, y + 10, w - 20, 26, 14, 14);

        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.setColor(new Color(230, 250, 255, 230));
        g2.drawString("BASE UPGRADE CONSOLE  (ESC)", x + 18, y + 28);

        // Scanline sweep
        int sweepY = y + 42 + (int) Math.round(((Math.sin(t * 0.9) * 0.5 + 0.5)) * (h - 70));
        g2.setColor(new Color(90, 220, 255, 14));
        g2.fillRect(x + 10, sweepY, w - 20, 12);

        // Info
        if (baseName == null) baseName = "Base";
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        int ty = y + 58;
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Base: " + baseName, x + 18, ty);
        ty += 18;

        // Resource readouts (with small pills)
        drawPill(g2, x + 18, ty - 12, 150, "CREDITS", String.valueOf(credits));
        drawPill(g2, x + 178, ty - 12, 150, "BASE ORE", String.valueOf(baseOre));
        drawPill(g2, x + 338, ty - 12, 160, "HANGAR", hangarLv + " / " + maxHangarTier);
        ty += 30;

        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString("Press 1-5 to purchase:", x + 18, ty);
        ty += 18;

        // Costs mirror GamePanel (keep in sync)
        java.util.function.IntBinaryOperator cCost = (which, nextLv) -> switch (which) {
            case 1 -> 150 + 200 * nextLv;
            case 2 -> 170 + 210 * nextLv;
            case 3 -> 210 + 250 * nextLv;
            case 4 -> 140 + 170 * nextLv;
            case 5 -> 380 + 420 * nextLv;
            default -> 0;
        };
        java.util.function.IntBinaryOperator oCost = (which, nextLv) -> switch (which) {
            case 1 -> 40 + 70 * nextLv;
            case 2 -> 50 + 80 * nextLv;
            case 3 -> 60 + 90 * nextLv;
            case 4 -> 40 + 110 * nextLv;
            case 5 -> 100 + 170 * nextLv;
            default -> 0;
        };

        ty = drawUpgradeLineConsole(g2, x + 18, ty, 1, "Hull Fortification", hullLv, 5, new Color(120, 255, 170, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 2, "Shield Array",      shieldLv, 5, new Color(120, 200, 255, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 3, "Turret Systems",    turretLv, 5, new Color(255, 210, 130, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 4, "Mining Ops",        miningLv, 5, new Color(255, 230, 120, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 5, "Hangar Expansion",  hangarLv, 3, new Color(210, 170, 255, 220), cCost, oCost);

        g2.setColor(new Color(255, 255, 255, 130));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("Mining Ops boosts mining rate + ore sell value.", x + 18, y + h - 16);
    }

    private static void drawPill(Graphics2D g2, int x, int y, int w, String label, String value) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, 20, 12, 12);
        g2.setColor(new Color(90, 220, 255, 70));
        g2.drawRoundRect(x, y, w, 20, 12, 12);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(200, 240, 255, 210));
        g2.drawString(label, x + 8, y + 14);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 220));
        int vw = g2.getFontMetrics().stringWidth(value);
        g2.drawString(value, x + w - 8 - vw, y + 15);
    }

    private static int drawUpgradeLineConsole(Graphics2D g2, int x, int ty,
                                              int key, String name, int lv, int max, Color accent,
                                              java.util.function.IntBinaryOperator cCost,
                                              java.util.function.IntBinaryOperator oCost) {
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        // Key capsule
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, ty - 12, 22, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRoundRect(x, ty - 12, 22, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.drawString(String.valueOf(key), x + 7, ty + 2);

        int textX = x + 30;

        // Name
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 215));
        g2.drawString(name, textX, ty + 2);

        // Level bars
        int barX = x + 250;
        int barY = ty - 10;
        int barW = 10;
        int barH = 16;
        for (int i = 0; i < max; i++) {
            boolean on = i < lv;
            g2.setColor(on ? accent : new Color(255, 255, 255, 40));
            g2.fillRoundRect(barX + i * (barW + 4), barY, barW, barH, 6, 6);
        }

        // Cost / status
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        if (lv >= max) {
            g2.setColor(new Color(120, 255, 170, 210));
            g2.drawString("MAX", x + 250 + max * 14 + 12, ty + 2);
        } else {
            int next = lv + 1;
            int c = cCost.applyAsInt(key, next);
            int o = oCost.applyAsInt(key, next);
            g2.setColor(new Color(255, 255, 255, 190));
            g2.drawString(c + "c + " + o + " ore", x + 250 + max * 14 + 12, ty + 2);
        }

        // Divider line
        g2.setColor(new Color(255, 255, 255, 26));
        g2.drawLine(x, ty + 8, x + 480, ty + 8);
        return ty + 26;
    }

public static void drawMinimap(Graphics2D g2, List<Ship> ships, Player player, int viewW, int viewH, double waypointX, double waypointY, List<MapPing> pings) {
        if (ships == null || ships.isEmpty() || player == null) return;

        int pad = 14;
        int size = 170;
        int x0 = viewW - size - pad;
        int y0 = pad;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x0, y0, size, size, 16, 16);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.drawRoundRect(x0, y0, size, size, 16, 16);

        double view = 1500;
        double left = player.x - view / 2.0;
        double top = player.y - view / 2.0;

        for (Ship s : ships) {
            if (!s.alive) continue;

            double rx = (s.x - left) / view;
            double ry = (s.y - top) / view;
            if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

            int px = x0 + (int) Math.round(rx * size);
            int py = y0 + (int) Math.round(ry * size);

            if (s.faction == Faction.ENEMY) g2.setColor(new Color(255, 90, 90, 220));
            else if (s.faction == Faction.PLAYER) g2.setColor(new Color(90, 255, 140, 240));
            else g2.setColor(new Color(140, 180, 255, 220));

            int r = (s.role == ShipRole.BASE) ? 4 : 2;
            g2.fillOval(px - r, py - r, r * 2, r * 2);
        }

        // Waypoint marker (if inside minimap view)
        if (!Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
            double rx = (waypointX - left) / view;
            double ry = (waypointY - top) / view;
            if (rx >= 0 && rx <= 1 && ry >= 0 && ry <= 1) {
                int px = x0 + (int) Math.round(rx * size);
                int py = y0 + (int) Math.round(ry * size);
                g2.setColor(new Color(255, 255, 255, 210));
                g2.drawOval(px - 4, py - 4, 8, 8);
                g2.drawLine(px - 6, py, px - 2, py);
                g2.drawLine(px + 2, py, px + 6, py);
                g2.drawLine(px, py - 6, px, py - 2);
                g2.drawLine(px, py + 2, px, py + 6);
            }
        }

        // Pings (if inside minimap view)
        if (pings != null) {
            for (MapPing ping : pings) {
                if (ping == null || ping.t <= 0) continue;
                double rx = (ping.x - left) / view;
                double ry = (ping.y - top) / view;
                if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

                int px = x0 + (int) Math.round(rx * size);
                int py = y0 + (int) Math.round(ry * size);

                int a = MathUtil.clamp((int) Math.round(60 + 120 * Math.min(1, ping.t)), 0, 220);
                Color c = switch (ping.faction) {
                    case 2 -> new Color(255, 90, 90, a);
                    case 1 -> new Color(140, 180, 255, a);
                    default -> new Color(90, 255, 140, a);
                };
                g2.setColor(c);
                g2.drawOval(px - 5, py - 5, 10, 10);
            }
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawString("MINIMAP", x0 + 10, y0 + size - 10);
    }


    public static void drawStrategicMap(Graphics2D g2,
                                        int viewW, int viewH,
                                        int worldW, int worldH,
                                        double camX, double camY,
                                        Player player,
                                        List<Ship> ships,
                                        List<Asteroid> asteroids,
                                        List<Salvage> salvage,
                                        double waypointX, double waypointY,
                                        List<MapPing> pings,
                                        String bannerTopLine) {

        Rectangle r = getStrategicMapRect(viewW, viewH);

        // Backdrop + glow border (Style B)
        g2.setColor(new Color(0, 0, 0, 205));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        g2.setColor(new Color(140, 200, 255, 55));
        g2.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 24, 24);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        // Inner map area
        int pad = 18;
        Rectangle m = new Rectangle(r.x + pad, r.y + 44, r.width - pad * 2, r.height - 60);

        g2.setColor(new Color(255, 255, 255, 22));
        g2.fillRoundRect(m.x, m.y, m.width, m.height, 16, 16);
        g2.setColor(new Color(255, 255, 255, 55));
        g2.drawRoundRect(m.x, m.y, m.width, m.height, 16, 16);

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 22));
        int step = 80;
        for (int x = m.x + step; x < m.x + m.width; x += step) g2.drawLine(x, m.y, x, m.y + m.height);
        for (int y = m.y + step; y < m.y + m.height; y += step) g2.drawLine(m.x, y, m.x + m.width, y);

        // Title + help
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(new Color(255, 255, 255, 225));
        g2.drawString("STRATEGIC MAP", r.x + 18, r.y + 28);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("LMB: waypoint   RMB: ping   M/ESC: close", r.x + 18, r.y + r.height - 16);

        if (bannerTopLine != null && !bannerTopLine.isBlank()) {
            g2.setColor(new Color(140, 200, 255, 200));
            g2.drawString(bannerTopLine, r.x + 190, r.y + 28);
        }

        // Helpers: world -> map
        java.util.function.BiFunction<Double, Double, Point> W2M = (wx, wy) -> {
            int px = m.x + (int) Math.round((wx / Math.max(1.0, worldW)) * m.width);
            int py = m.y + (int) Math.round((wy / Math.max(1.0, worldH)) * m.height);
            return new Point(px, py);
        };

        // Asteroids
        if (asteroids != null) {
            g2.setColor(new Color(200, 200, 200, 80));
            for (Asteroid a : asteroids) {
                if (a == null) continue;
                Point p = W2M.apply(a.x, a.y);
                g2.fillRect(p.x, p.y, 2, 2);
            }
        }

        // Salvage
        if (salvage != null) {
            g2.setColor(new Color(255, 255, 255, 120));
            for (Salvage s : salvage) {
                if (s == null || !s.alive()) continue;
                Point p = W2M.apply(s.x, s.y);
                g2.fillOval(p.x - 1, p.y - 1, 3, 3);
            }
        }

        // Ships + bases
        if (ships != null) {
            for (Ship s : ships) {
                if (s == null || !s.alive) continue;
                Point p = W2M.apply(s.x, s.y);

                Color c = (s.faction == Faction.ENEMY)
                        ? new Color(255, 90, 90, 200)
                        : (s.faction == Faction.PLAYER ? new Color(90, 255, 140, 220) : new Color(140, 180, 255, 200));

                int rr = (s.role == ShipRole.BASE) ? 4 : 2;
                g2.setColor(c);
                g2.fillOval(p.x - rr, p.y - rr, rr * 2, rr * 2);
            }
        }

        // Waypoint
        if (!Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
            Point wp = W2M.apply(waypointX, waypointY);
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawOval(wp.x - 6, wp.y - 6, 12, 12);
            g2.drawLine(wp.x - 10, wp.y, wp.x - 3, wp.y);
            g2.drawLine(wp.x + 3, wp.y, wp.x + 10, wp.y);
            g2.drawLine(wp.x, wp.y - 10, wp.x, wp.y - 3);
            g2.drawLine(wp.x, wp.y + 3, wp.x, wp.y + 10);
        }

        // Pings
        if (pings != null) {
            for (MapPing ping : pings) {
                if (ping == null || ping.t <= 0) continue;
                Point pp = W2M.apply(ping.x, ping.y);

                int a = MathUtil.clamp((int) Math.round(60 + 120 * Math.min(1, ping.t)), 0, 220);
                Color c = switch (ping.faction) {
                    case 2 -> new Color(255, 90, 90, a);
                    case 1 -> new Color(140, 180, 255, a);
                    default -> new Color(90, 255, 140, a);
                };

                g2.setColor(c);
                g2.drawOval(pp.x - 8, pp.y - 8, 16, 16);
                g2.drawOval(pp.x - 4, pp.y - 4, 8, 8);
            }
        }

        // Camera viewport rectangle
        double vx0 = camX;
        double vy0 = camY;
        double vx1 = camX + viewW;
        double vy1 = camY + viewH;

        Point p0 = W2M.apply(vx0, vy0);
        Point p1 = W2M.apply(vx1, vy1);

        int rx = Math.min(p0.x, p1.x);
        int ry = Math.min(p0.y, p1.y);
        int rw = Math.abs(p1.x - p0.x);
        int rh = Math.abs(p1.y - p0.y);

        g2.setColor(new Color(255, 255, 255, 120));
        g2.drawRect(rx, ry, rw, rh);
    }


    // IMPORTANT: This is the method that was likely stubbed/empty in your current project.
    public static void drawShip(Graphics2D g2, Ship ship) {
        if (!ship.alive) return;

        // Color palette per faction
        Color hull;
        Color trim;
        if (ship.faction == Faction.ENEMY) {
            hull = new Color(220, 80, 80);
            trim = new Color(255, 170, 170);
        } else if (ship.faction == Faction.PLAYER) {
            hull = new Color(70, 220, 120);
            trim = new Color(200, 255, 220);
        } else {
            hull = new Color(120, 160, 245);
            trim = new Color(220, 230, 255);
        }

        int wx = (int) Math.round(ship.x);
        int wy = (int) Math.round(ship.y);

        Graphics2D g = (Graphics2D) g2.create();
        g.translate(wx, wy);
        g.rotate(ship.angle);

        // Stealth rendering: fade when not revealed.
        double sig = ship.effectiveSignature();
        if (ship.isStealth && sig < 0.99) {
            float a = (float) (0.22 + 0.78 * sig);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }

        Polygon hullPoly = switch (ship.role) {
            case PATROL -> hullPatrol(ship.radius);
            case PICKET -> hullPicket(ship.radius);
            case STEALTH_SHIP -> hullStealth(ship.radius);
            case FIGHTER -> hullFighter(ship.radius);
            case MISSILE_BOAT -> hullMissileBoat(ship.radius);
            case CIWS_CORVETTE -> hullCIWS(ship.radius);
            case LIGHT_CRUISER -> hullLightCruiser(ship.radius);
            case CRUISER, MEDIUM_CRUISER -> hullMediumCruiser(ship.radius);
            case BATTLECRUISER -> hullBattlecruiser(ship.radius);
            case BATTLESHIP -> hullBattleship(ship.radius);
            case DREADNOUGHT -> hullDreadnought(ship.radius);
            case CARRIER -> hullCarrier(ship.radius);
            case BASE -> hullBase(ship.radius);
            default -> hullFrigate(ship.radius);
        };

        // Shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.translate(4, 4);
        g.fillPolygon(hullPoly);
        g.translate(-4, -4);

        // Main hull (subtle shading gradient)
        int frontX = 0;
        int backX = 0;
        for (int i = 0; i < hullPoly.npoints; i++) {
            int px = hullPoly.xpoints[i];
            if (i == 0) { frontX = backX = px; }
            else {
                if (px > frontX) frontX = px;
                if (px < backX) backX = px;
            }
        }
        Color hullDark = new Color(Math.max(0, hull.getRed() - 35), Math.max(0, hull.getGreen() - 35), Math.max(0, hull.getBlue() - 35));
        Color hullLight = new Color(Math.min(255, hull.getRed() + 25), Math.min(255, hull.getGreen() + 25), Math.min(255, hull.getBlue() + 25));
        GradientPaint gp = new GradientPaint(backX, 0, hullDark, frontX, 0, hullLight);
        g.setPaint(gp);
        g.fillPolygon(hullPoly);
        g.setPaint(null);

        // Outline
        g.setColor(new Color(0, 0, 0, 110));
        g.drawPolygon(hullPoly);

        // Plating + deck details
        drawPlating(g, ship, hull, trim);

        // Engines
        drawEngines(g, ship);

        // Bridge / superstructure
        drawBridge(g, ship);

        // Shield ring
        if (ship.shieldActive && ship.shieldMax > 0 && ship.shield > 0) {
            double frac = Math.max(0, Math.min(1, ship.shield / ship.shieldMax));
            g.setColor(new Color(120, 200, 255, (int) (40 + 90 * frac)));
            int rr = (int) Math.round(ship.radius + 7);
            g.drawOval(-rr, -rr, rr * 2, rr * 2);
        }

        // Turrets
        drawTurrets(g, ship);

        // Damage decals / scorch marks
        drawDamageDecals(g, ship, hullPoly);

        // Stealth shimmer outline
        if (ship.isStealth && sig < 0.99) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g.setColor(new Color(120, 220, 255, 110));
            g.drawPolygon(hullPoly);
        }

        g.dispose();

        // Name tag
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
    }

    private static void drawPlating(Graphics2D g, Ship ship, Color hull, Color trim) {
        int r = (int) Math.round(ship.radius);

        // Armor belt (inset polygon)
        Polygon base = switch (ship.role) {
            case PATROL -> hullPatrol(ship.radius);
            case PICKET -> hullPicket(ship.radius);
            case STEALTH_SHIP -> hullStealth(ship.radius);
            case FIGHTER -> hullFighter(ship.radius);
            case MISSILE_BOAT -> hullMissileBoat(ship.radius);
            case CIWS_CORVETTE -> hullCIWS(ship.radius);
            case LIGHT_CRUISER -> hullLightCruiser(ship.radius);
            case CRUISER, MEDIUM_CRUISER -> hullMediumCruiser(ship.radius);
            case BATTLECRUISER -> hullBattlecruiser(ship.radius);
            case BATTLESHIP -> hullBattleship(ship.radius);
            case DREADNOUGHT -> hullDreadnought(ship.radius);
            case CARRIER -> hullCarrier(ship.radius);
            case BASE -> hullBase(ship.radius);
            default -> hullFrigate(ship.radius);
        };

        if (ship.role != ShipRole.BASE) {
            Polygon inset = scalePolygon(base, 0.78);
            int dr = clamp255(hull.getRed() - 40);
            int dg = clamp255(hull.getGreen() - 40);
            int db = clamp255(hull.getBlue() - 40);
            g.setColor(new Color(dr, dg, db, 120));
            g.fillPolygon(inset);

            g.setColor(new Color(255, 255, 255, 45));
            g.drawPolygon(inset);
        }

        // Deck stripe / panels
        g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 120));
        drawDeckDetails(g, ship);

        // Simple portholes / windows on larger hulls
        if (ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER || ship.role == ShipRole.CRUISER
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT
                || ship.role == ShipRole.CARRIER) {
            g.setColor(new Color(255, 255, 255, 65));
            int n = Math.max(4, r / 4);
            for (int i = 0; i < n; i++) {
                int px = -r / 3 + i * (r / 3);
                g.fillRect(px, -r / 4, 2, 2);
                g.fillRect(px, r / 4, 2, 2);
            }
        }
    }

    private static void drawBridge(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);
        if (ship.role == ShipRole.BASE) return;

        // Carriers already have a runway-style deck; give them an offset island.
        if (ship.role == ShipRole.CARRIER) {
            g.setColor(new Color(255, 255, 255, 120));
            g.fillRoundRect(2, -r / 2, r / 3, r / 3, 8, 8);
            g.setColor(new Color(0, 0, 0, 80));
            g.drawRoundRect(2, -r / 2, r / 3, r / 3, 8, 8);
            return;
        }

        // Stealth ships: low-profile bridge
        if (ship.role == ShipRole.STEALTH_SHIP) {
            g.setColor(new Color(255, 255, 255, 70));
            g.fillRoundRect(r / 6, -r / 6, r / 5, r / 3, 10, 10);
            return;
        }

        int bx = r / 6;
        int by = -r / 6;
        int bw = r / 3;
        int bh = r / 3;

        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.BATTLECRUISER) {
            bx = r / 10;
            by = -r / 5;
            bw = r / 2;
            bh = r / 2;
        }

        g.setColor(new Color(255, 255, 255, 110));
        g.fillRoundRect(bx, by, bw, bh, 10, 10);
        g.setColor(new Color(0, 0, 0, 90));
        g.drawRoundRect(bx, by, bw, bh, 10, 10);
    }

    private static void drawDeckDetails(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);

        switch (ship.role) {
            case CARRIER -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 8, 0, r + 8, 0);
                g.drawLine(-r + 8, -r / 3, r + 4, -r / 3);
                g.drawLine(-r + 8, r / 3, r + 4, r / 3);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawRect(-r / 2, -r + 6, r / 3, r / 2);

                g.setColor(new Color(255, 255, 255, 90));
                for (int i = 0; i < 5; i++) g.fillRect(-r / 2 + 3 + i * 5, -r + 10, 2, 2);
            }
            case MISSILE_BOAT -> {
                g.setColor(new Color(255, 255, 255, 110));
                g.drawRect(-r / 4, -r / 2, r / 2, r / 3);
                g.drawRect(-r / 4, r / 6, r / 2, r / 3);

                g.setColor(new Color(255, 255, 255, 70));
                g.drawLine(-r + 6, -r / 4, r - 2, -r / 4);
                g.drawLine(-r + 6, r / 4, r - 2, r / 4);
            }
            case CIWS_CORVETTE -> {
                g.setColor(new Color(255, 255, 255, 120));
                g.drawLine(-r / 2, 0, -r / 2, -r / 2);
                g.drawOval(-r / 2 - 4, -r / 2 - 10, 8, 8);

                g.setColor(new Color(255, 255, 255, 90));
                g.drawOval(-2, -2, 4, 4);

                g.setColor(new Color(255, 255, 255, 70));
                g.drawLine(-r + 4, 0, r, 0);
            }
            case BASE -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawOval(-r, -r, r * 2, r * 2);
                g.drawOval(-(r - 10), -(r - 10), (r - 10) * 2, (r - 10) * 2);

                g.setColor(new Color(255, 255, 255, 110));
                g.drawLine(0, -r, 0, r);
                g.drawLine(-r, 0, r, 0);
            }
            case PATROL -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, -r / 4, r + 6, -r / 6);
                g.drawLine(-r + 6, r / 4, r + 6, r / 6);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawOval(r / 6, -3, 6, 6);
            }
            case PICKET -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, 0, r + 8, 0);
                g.drawLine(-r / 2, -r / 3, r / 2, -r / 6);
                g.drawLine(-r / 2, r / 3, r / 2, r / 6);
            }
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(-r + 6, -r / 3, r + 8, -r / 8);
                g.drawLine(-r + 6, r / 3, r + 8, r / 8);
                g.setColor(new Color(255, 255, 255, 115));
                g.drawRect(-r / 4, -r / 5, r / 3, r / 2);
                g.drawRect(r / 10, -r / 7, r / 4, r / 3);
            }
            case BATTLECRUISER, BATTLESHIP, DREADNOUGHT -> {
                g.setColor(new Color(255, 255, 255, 75));
                g.drawLine(-r + 6, -r / 2, r + 10, -r / 6);
                g.drawLine(-r + 6, r / 2, r + 10, r / 6);
                g.drawLine(-r + 6, 0, r + 10, 0);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawRect(-r / 5, -r / 4, r / 3, r / 2);
                g.drawRect(r / 8, -r / 6, r / 3, r / 3);
            }
            default -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(-r + 6, -r / 3, r + 4, -r / 6);
                g.drawLine(-r + 6, r / 3, r + 4, r / 6);

                g.setColor(new Color(255, 255, 255, 110));
                g.drawRect(-r / 3, -r / 4, r / 3, r / 2);
            }
        }
    }

    private static Polygon scalePolygon(Polygon p, double s) {
        int n = p.npoints;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(p.xpoints[i] * s);
            ys[i] = (int) Math.round(p.ypoints[i] * s);
        }
        return new Polygon(xs, ys, n);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void drawEngines(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);
        if (ship.role == ShipRole.BASE) return;

        int ex = -r;
        int ey = 0;

        g.setColor(new Color(120, 220, 255, 120));
        g.fillOval(ex - 6, ey - 4, 8, 8);

        g.setColor(new Color(120, 220, 255, 70));
        g.fillOval(ex - 10, ey - 8, 14, 14);

        if (ship.role == ShipRole.CARRIER || ship.role == ShipRole.MISSILE_BOAT
                || ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER || ship.role == ShipRole.CRUISER
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT) {
            g.setColor(new Color(120, 220, 255, 120));
            g.fillOval(ex - 6, -r / 3 - 4, 8, 8);
            g.fillOval(ex - 6, r / 3 - 4, 8, 8);
        }

        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT) {
            g.setColor(new Color(120, 220, 255, 105));
            g.fillOval(ex - 8, -r / 2 - 4, 10, 10);
            g.fillOval(ex - 8, r / 2 - 4, 10, 10);
        }
    }

    private static void drawTurrets(Graphics2D g2, Ship ship) {
        for (Turret t : ship.turrets) {
            g2.setColor(new Color(255, 255, 255, 170));
            int r = (int) Math.round(t.radius);
            g2.fillOval((int) Math.round(t.localX - t.radius), (int) Math.round(t.localY - t.radius), r * 2, r * 2);

            double rel = MathUtil.normalizeAngle(t.angle - ship.angle);
            double bx = t.localX + Math.cos(rel) * t.barrelLen;
            double by = t.localY + Math.sin(rel) * t.barrelLen;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawLine((int) Math.round(t.localX), (int) Math.round(t.localY), (int) Math.round(bx), (int) Math.round(by));

            if (t.kind == Turret.Kind.MISSILE) {
                g2.setColor(new Color(255, 255, 255, 90));
                g2.drawRect((int) Math.round(t.localX - t.radius - 2), (int) Math.round(t.localY - t.radius - 2), (r + 2) * 2, (r + 2) * 2);
            }
        }

        if (ship.hasCIWS) {
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawOval(-3, -3, 6, 6);
            g2.drawLine(0, 0, 8, 0);
        }
    }


    private static void drawDamageDecals(Graphics2D g, Ship ship, Polygon hullPoly) {
        if (ship == null || hullPoly == null) return;
        if (ship.hpMax <= 0) return;

        double hpFrac = Math.max(0.0, Math.min(1.0, ship.hp / (double) ship.hpMax));
        double dmg = 1.0 - hpFrac; // 0..1
        if (dmg < 0.12) return;

        int r = (int) Math.max(8, Math.round(ship.radius));
        int n = (int) Math.round(3 + dmg * 10);

        long seed = (long) System.identityHashCode(ship) * 0x9E3779B97F4A7C15L;
        Random rng = new Random(seed);

        // Scorch marks
        for (int i = 0; i < n; i++) {
            int tries = 0;
            int px = 0, py = 0;
            while (tries++ < 14) {
                px = -r + rng.nextInt(r * 2 + 1);
                py = -r + rng.nextInt(r * 2 + 1);
                if (hullPoly.contains(px, py)) break;
            }

            int sz = (int) Math.max(3, Math.round(2 + rng.nextDouble() * (3 + dmg * 9)));
            int a = (int) MathUtil.clamp(40 + dmg * 120, 0, 160);
            g.setColor(new Color(0, 0, 0, a));
            g.fillOval(px - sz, py - sz, sz * 2, sz * 2);

            // hot edge / ember tint
            g.setColor(new Color(255, 200, 120, (int) MathUtil.clamp(18 + dmg * 45, 0, 80)));
            g.drawOval(px - sz, py - sz, sz * 2, sz * 2);
        }

        // If very damaged, add a little smoke haze on top
        if (dmg > 0.55) {
            int smoke = (int) Math.round(2 + dmg * 6);
            for (int i = 0; i < smoke; i++) {
                int tries = 0;
                int px = 0, py = 0;
                while (tries++ < 14) {
                    px = -r + rng.nextInt(r * 2 + 1);
                    py = -r + rng.nextInt(r * 2 + 1);
                    if (hullPoly.contains(px, py)) break;
                }
                int sz = (int) Math.max(6, Math.round(6 + rng.nextDouble() * 10));
                int a = (int) MathUtil.clamp(20 + (dmg - 0.55) * 140, 0, 110);
                g.setColor(new Color(30, 30, 30, a));
                g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
            }
        }
    }

    private static Polygon hullFighter(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(-r + 1, -r / 2);
        p.addPoint(-r, 0);
        p.addPoint(-r + 1, r / 2);
        return p;
    }

    private static Polygon hullFrigate(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 8, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 8, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullMissileBoat(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(r - 8, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 8, r / 2);
        return p;
    }

    private static Polygon hullCarrier(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 8, 0);
        p.addPoint(r - 8, -r);
        p.addPoint(-r, -r);
        p.addPoint(-r + 14, 0);
        p.addPoint(-r, r);
        p.addPoint(r - 8, r);
        return p;
    }

    private static Polygon hullCIWS(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullBase(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        // diamond-ish station
        p.addPoint(0, -r);
        p.addPoint(r, 0);
        p.addPoint(0, r);
        p.addPoint(-r, 0);
        return p;
    }

    // ------------------------------
    // New hull silhouettes (art pass)
    // ------------------------------

    private static Polygon hullPatrol(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 7, 0);
        p.addPoint(r - 2, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(r - 2, r / 2);
        return p;
    }

    private static Polygon hullPicket(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 9, 0);
        p.addPoint(r - 4, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 4, r / 2);
        return p;
    }

    private static Polygon hullStealth(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        // sleek diamond/knife
        p.addPoint(r + 10, 0);
        p.addPoint(r - 4, -r / 3);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, 0);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 4, r / 3);
        return p;
    }

    private static Polygon hullLightCruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 10, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r + 4, -r / 2);
        p.addPoint(-r, -r / 5);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 5);
        p.addPoint(-r + 4, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullMediumCruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 12, 0);
        p.addPoint(r - 7, -r / 2);
        p.addPoint(r - 12, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 6);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 6);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 12, r / 2);
        p.addPoint(r - 7, r / 2);
        return p;
    }

    private static Polygon hullBattlecruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 14, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(r - 14, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 4);
        p.addPoint(-r + 12, 0);
        p.addPoint(-r, r / 4);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 14, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullBattleship(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 16, 0);
        p.addPoint(r - 8, -r / 2);
        p.addPoint(r - 18, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 14, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 18, r / 2);
        p.addPoint(r - 8, r / 2);
        return p;
    }

    private static Polygon hullDreadnought(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 18, 0);
        p.addPoint(r - 10, -r / 2);
        p.addPoint(r - 22, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 2 + r / 6);
        p.addPoint(-r + 16, 0);
        p.addPoint(-r, r / 2 - r / 6);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 22, r / 2);
        p.addPoint(r - 10, r / 2);
        return p;
    }

    /**
     * If the locked target is offscreen, draw a small arrow at the edge of the screen pointing toward it.
     * Coordinates are in screen space (camX/camY are the world-space camera origin).
     */
    static void drawOffscreenTargetIndicator(Graphics2D g2, Ship target, double camX, double camY, int viewW, int viewH) {
        if (target == null || !target.alive) return;

        // Target in screen coords
        double sx = target.x - camX;
        double sy = target.y - camY;

        if (sx >= 0 && sx <= viewW && sy >= 0 && sy <= viewH) return; // on screen

        double cx = viewW / 2.0;
        double cy = viewH / 2.0;

        double vx = sx - cx;
        double vy = sy - cy;
        double len = Math.hypot(vx, vy);
        if (len < 1e-6) return;

        vx /= len;
        vy /= len;

        double margin = 22.0;

        // Ray from screen center: find earliest intersection with inset rectangle.
        double t = Double.POSITIVE_INFINITY;
        if (vx >  1e-6) t = Math.min(t, (viewW - margin - cx) / vx);
        if (vx < -1e-6) t = Math.min(t, (margin - cx) / vx);
        if (vy >  1e-6) t = Math.min(t, (viewH - margin - cy) / vy);
        if (vy < -1e-6) t = Math.min(t, (margin - cy) / vy);

        if (!Double.isFinite(t)) return;

        double px = cx + vx * t;
        double py = cy + vy * t;

        double size = 13.0;
        double perpX = -vy;
        double perpY =  vx;

        int x0 = (int) Math.round(px);
        int y0 = (int) Math.round(py);

        int x1 = (int) Math.round(px - vx * size + perpX * size * 0.55);
        int y1 = (int) Math.round(py - vy * size + perpY * size * 0.55);

        int x2 = (int) Math.round(px - vx * size - perpX * size * 0.55);
        int y2 = (int) Math.round(py - vy * size - perpY * size * 0.55);

        int[] xs = {x0, x1, x2};
        int[] ys = {y0, y1, y2};

        Color fill = new Color(255, 255, 255, 210);
        if (target.faction == Faction.ENEMY) fill = new Color(255, 170, 170, 220);
        if (target.faction == Faction.ALLY)  fill = new Color(170, 220, 255, 220);

        g2.setColor(fill);
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawPolygon(xs, ys, 3);
    }

}
