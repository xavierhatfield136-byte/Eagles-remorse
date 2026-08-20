import app.config.GameConfig;
import app.config.GameMode;
import app.ui.MainMenuPanel;
import app.support.MenuDisplay;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Random;

/**
 * Disposable main-menu sandbox using real in-game ships, AI, projectiles, and rendering.
 * It is deliberately not connected to campaign state, saves, economy, or player inventory.
 */
public final class MainMenuBattlePanel extends JPanel implements MainMenuPanel.MenuBattleView {
    private static final long MENU_BATTLE_SEED = 0x5A17C0DEL ^ 0x4D454E55424177L;
    private static final int WORLD_W = 7000;
    private static final int WORLD_H = 4200;
    private static final int MAX_MENU_SHIPS = 18;
    private static final double FIXED_DT = 1.0 / 60.0;
    private static final Faction[] MENU_FACTIONS = Faction.fourTeamFactions();
    private static final ShipRole[] MENU_ROLES = Arrays.stream(ShipRole.values())
            .filter(role -> role != ShipRole.BASE && role != ShipRole.STATIC_TURRET)
            .toArray(ShipRole[]::new);

    private final double uiScale;
    private final Random random = new Random(MENU_BATTLE_SEED);
    private GameContext ctx;
    private double accumulator = 0.0;
    private double replacementTimer = 2.5;
    private double resetTimer = 0.0;
    private double cameraX = WORLD_W * 0.5;
    private double cameraY = WORLD_H * 0.5;
    private int scenarioIndex = 0;

    public MainMenuBattlePanel(double uiScale) {
        this.uiScale = uiScale;
        setOpaque(false);
        setMinimumSize(new Dimension(MenuDisplay.scaled(560, uiScale), MenuDisplay.scaled(360, uiScale)));
        startBattleScenario();
    }

    @Override
    public void update(double deltaSeconds) {
        if (deltaSeconds <= 0.0) return;
        accumulator += Math.min(0.10, deltaSeconds);
        int ticks = 0;
        while (accumulator >= FIXED_DT && ticks < 8) {
            tick(FIXED_DT);
            accumulator -= FIXED_DT;
            ticks++;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int w = getWidth();
            int h = getHeight();
            paintScope(g2, w, h);
            paintBattle(g2, w, h);
            paintOverlay(g2, w, h);
        } finally {
            g2.dispose();
        }
    }

    private void tick(double dt) {
        if (ctx == null) {
            startBattleScenario();
            return;
        }

        if (resetTimer > 0.0) {
            resetTimer -= dt;
            updateProjectilesOnly(dt);
            if (resetTimer <= 0.0) {
                startBattleScenario();
            }
            return;
        }

        AISystem.update(ctx, dt);
        CarrierSystem.update(ctx, dt);
        TitanAbilitySystem.update(ctx, dt);
        PhysicsSystem.update(ctx, dt);
        cleanupDeadShips();
        updateCamera(dt);

        int living = livingShipCount();
        int activeFactions = activeFactionCount();
        if (living <= 1 || activeFactions <= 1) {
            resetTimer = 3.0 + random.nextDouble() * 2.0;
            return;
        }

        replacementTimer -= dt;
        if (replacementTimer <= 0.0) {
            if (living < MAX_MENU_SHIPS) {
                spawnRandomShip();
            }
            replacementTimer = 1.8 + random.nextDouble() * 2.8;
        }
    }

    private void startBattleScenario() {
        ctx = new GameContext(new GameConfig(
                GameMode.FOUR_TEAM_DOMINATION,
                WORLD_W,
                WORLD_H,
                true,
                MENU_BATTLE_SEED + scenarioIndex++ * 7919L,
                false));
        ctx.enemyWaveTimer = 9999.0;
        ctx.camX = cameraX;
        ctx.camY = cameraY;
        ctx.zoom = 1.0;
        ctx.suppressAudio = true;
        Faction.clearCampaignAlliances();

        int total = 11 + random.nextInt(4);
        for (int i = 0; i < total; i++) {
            spawnRandomShip();
        }
        replacementTimer = 2.2 + random.nextDouble() * 2.5;
        resetTimer = 0.0;
        updateCamera(1.0);
    }

    private void spawnRandomShip() {
        if (ctx == null) return;
        Faction faction = MENU_FACTIONS[random.nextInt(MENU_FACTIONS.length)];
        ShipRole role = randomRole();
        double[] anchor = factionAnchor(faction);
        double x = clamp(anchor[0] + (random.nextDouble() - 0.5) * 620.0, 160.0, WORLD_W - 160.0);
        double y = clamp(anchor[1] + (random.nextDouble() - 0.5) * 500.0, 160.0, WORLD_H - 160.0);
        Ship ship = SpawnSystem.spawnCatalogShip(ctx, role, faction, x, y);
        if (ship == null) return;
        ship.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
        ship.attractModeStaggerPrimaryFire = true;
        ship.applyPrimaryWeaponFamily();
        ship.name = faction.transponderPrefix() + " " + readableRole(role);
        ship.angle = initialAngleTowardCenter(ship);
        ship.vx = Math.cos(ship.angle) * (0.6 + random.nextDouble() * 0.6);
        ship.vy = Math.sin(ship.angle) * (0.6 + random.nextDouble() * 0.6);
    }

    private ShipRole randomRole() {
        return MENU_ROLES[random.nextInt(MENU_ROLES.length)];
    }

    private double[] factionAnchor(Faction faction) {
        int lane = Math.floorMod(faction == null ? 0 : faction.ordinal(), 4);
        return switch (lane) {
            case 0 -> new double[]{WORLD_W * 0.34, WORLD_H * 0.38};
            case 1 -> new double[]{WORLD_W * 0.66, WORLD_H * 0.62};
            case 2 -> new double[]{WORLD_W * 0.62, WORLD_H * 0.34};
            default -> new double[]{WORLD_W * 0.38, WORLD_H * 0.66};
        };
    }

    private double initialAngleTowardCenter(Ship ship) {
        if (ship == null) return 0.0;
        double tx = WORLD_W * 0.5 + (random.nextDouble() - 0.5) * 420.0;
        double ty = WORLD_H * 0.5 + (random.nextDouble() - 0.5) * 320.0;
        return Math.atan2(ty - ship.y, tx - ship.x);
    }

    private void updateProjectilesOnly(double dt) {
        if (ctx == null) return;
        for (Projectile projectile : ctx.projectiles) {
            if (projectile != null) projectile.update(dt);
        }
        ctx.projectiles.removeIf(projectile -> projectile == null || !projectile.alive);
    }

    private void cleanupDeadShips() {
        if (ctx == null) return;
        ctx.ships.removeIf(ship -> ship == null || (!ship.alive && !ship.dying));
        if (ctx.projectiles.size() > 260) {
            int keepFrom = Math.max(0, ctx.projectiles.size() - 220);
            ctx.projectiles.subList(0, keepFrom).clear();
        }
    }

    private void updateCamera(double dt) {
        if (ctx == null || ctx.ships.isEmpty()) return;
        double sx = 0.0;
        double sy = 0.0;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive) continue;
            sx += ship.x;
            sy += ship.y;
            count++;
        }
        if (count <= 0) return;
        double targetX = clamp(sx / count, WORLD_W * 0.30, WORLD_W * 0.70);
        double targetY = clamp(sy / count, WORLD_H * 0.28, WORLD_H * 0.72);
        double blend = Math.min(1.0, dt * 0.8);
        cameraX += (targetX - cameraX) * blend;
        cameraY += (targetY - cameraY) * blend;
    }

    private int livingShipCount() {
        if (ctx == null) return 0;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.alive) count++;
        }
        return count;
    }

    private int activeFactionCount() {
        if (ctx == null) return 0;
        EnumMap<Faction, Boolean> present = new EnumMap<>(Faction.class);
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.alive && ship.faction != null) {
                present.put(ship.faction, Boolean.TRUE);
            }
        }
        return present.size();
    }

    private void paintScope(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, new Color(2, 8, 16, 126), 0, h,
                new Color(3, 5, 10, 54)));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(96, 157, 190, 25));
        int gap = Math.max(24, MenuDisplay.scaled(58, uiScale));
        for (int x = gap; x < w; x += gap) g2.drawLine(x, 0, x, h);
        for (int y = gap; y < h; y += gap) g2.drawLine(0, y, w, y);
        g2.setColor(new Color(255, 255, 255, 16));
        for (int y = 0; y < h; y += 4) g2.drawLine(0, y, w, y);
    }

    private void paintBattle(Graphics2D g2, int w, int h) {
        if (ctx == null) return;
        double zoom = battleZoom(w, h);
        double viewWorldW = w / zoom;
        double viewWorldH = h / zoom;
        double minX = cameraX - viewWorldW * 0.5;
        double minY = cameraY - viewWorldH * 0.5;
        double maxX = cameraX + viewWorldW * 0.5;
        double maxY = cameraY + viewWorldH * 0.5;

        Graphics2D world = (Graphics2D) g2.create();
        try {
            world.translate(w * 0.5, h * 0.5);
            world.scale(zoom, zoom);
            world.translate(-cameraX, -cameraY);
            Renderer.drawProjectiles(world, ctx.ships, ctx.projectiles, minX, minY, maxX, maxY, null, null);
            Renderer.drawShips(world, ctx.ships, minX, minY, maxX, maxY, null, null, ctx);
        } finally {
            world.dispose();
        }
    }

    private double battleZoom(int w, int h) {
        double fitW = w / 2200.0;
        double fitH = h / 1400.0;
        return clamp(Math.min(fitW, fitH), 0.30, 0.62);
    }

    private void paintOverlay(Graphics2D g2, int w, int h) {
        int pad = MenuDisplay.scaled(18, uiScale);
        g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, uiScale));
        FontMetrics titleFm = g2.getFontMetrics();
        int titleY = pad + titleFm.getAscent();
        g2.setColor(new Color(125, 214, 231, 210));
        g2.drawString("TACTICAL ATTRACT MODE", pad, titleY);
        g2.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 12, uiScale));
        FontMetrics subtitleFm = g2.getFontMetrics();
        int subtitleY = titleY + Math.max(MenuDisplay.scaled(14, uiScale),
                titleFm.getDescent() + subtitleFm.getAscent() + MenuDisplay.scaled(4, uiScale));
        g2.setColor(new Color(198, 211, 226, 166));
        g2.drawString("Real fleet sandbox - all factions, disposable state",
                pad, subtitleY);

        String status = factionStatus();
        Font oldFont = g2.getFont();
        g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 11, uiScale));
        int statusW = Math.max(MenuDisplay.scaled(260, uiScale), g2.getFontMetrics().stringWidth(status) + 22);
        int x = Math.max(10, w - statusW - pad);
        int y = Math.max(22, h - MenuDisplay.scaled(24, uiScale));
        g2.setColor(new Color(7, 12, 20, 138));
        g2.fillRoundRect(x - 10, y - 18, statusW, 28, 8, 8);
        g2.setColor(new Color(218, 229, 240, 188));
        g2.drawString(status, x, y);
        g2.setFont(oldFont);

        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(98, 150, 183, 44));
        g2.drawRect(0, 0, Math.max(1, w - 1), Math.max(1, h - 1));
        g2.setStroke(oldStroke);
    }

    private String factionStatus() {
        return "SHIPS " + livingShipCount() + "  /  FACTIONS " + activeFactionCount();
    }

    private static String readableRole(ShipRole role) {
        if (role == null) return "SHIP";
        return role.name().replace('_', ' ');
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
