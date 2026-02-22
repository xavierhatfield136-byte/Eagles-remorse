import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * GamePanel is now a thin orchestrator.
 * Major systems live in separate files:
 * - GameContext (state)
 * - SpawnSystem, CameraSystem, PhysicsSystem, AISystem, EconomySystem, EventSystem, UISystem, TargetingSystem
 * - GameRenderSystem (drawing)
 * - InputSystem (bindings/listeners)
 */
public class GamePanel extends JPanel implements ActionListener {

    private static final int VIEW_W = 1280;
    private static final int VIEW_H = 720;
    private static final int TARGET_FPS = 60;
    private static final double TARGET_FRAME_MS = 1000.0 / TARGET_FPS;
    private static final long STEP_NS = 1_000_000_000L / TARGET_FPS;
    private static final long MAX_ELAPSED_NS = 250_000_000L;
    private static final int MAX_UPDATE_STEPS = 6;

    final GameContext ctx;
    private final Timer timer;

    private final Runnable exitToMenu;
    private final Runnable toggleFullscreen;

    private final PlayerControl controls;
    private long lastTickNs = 0L;
    private double accumulatorNs = 0.0;
    private double emaFrameMs = 0.0;
    private double emaJitterMs = 0.0;
    private double emaUpdateMs = 0.0;
    private double emaRenderMs = 0.0;
    private int droppedUpdateSteps = 0;

    public GamePanel(GameConfig config, Runnable exitToMenu) {
        this(config, exitToMenu, null);
    }

    public GamePanel(GameConfig config, Runnable exitToMenu, Runnable toggleFullscreen) {
        this.exitToMenu = exitToMenu;
        this.toggleFullscreen = toggleFullscreen;

        this.ctx = new GameContext(config);

        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        // World init
        SpawnSystem.initWorld(ctx);

        // Input
        controls = InputSystem.install(this, ctx, exitToMenu, toggleFullscreen);

        // Higher-frequency scheduler + fixed-timestep simulation smooths frame pacing.
        timer = new Timer(5, this);
        timer.setCoalesce(true);
        lastTickNs = System.nanoTime();
        timer.start();
    }

    int viewportW() {
        int w = getWidth();
        return (w > 0) ? w : VIEW_W;
    }

    int viewportH() {
        int h = getHeight();
        return (h > 0) ? h : VIEW_H;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        if (lastTickNs <= 0L) lastTickNs = now;

        long elapsedNs = now - lastTickNs;
        lastTickNs = now;
        if (elapsedNs < 0L) elapsedNs = 0L;
        if (elapsedNs > MAX_ELAPSED_NS) elapsedNs = MAX_ELAPSED_NS;

        double frameMs = elapsedNs / 1_000_000.0;
        emaFrameMs = smooth(emaFrameMs, frameMs, 0.15);
        emaJitterMs = smooth(emaJitterMs, Math.abs(frameMs - TARGET_FRAME_MS), 0.15);

        double timeScale = Math.max(0.0, DevTools.getTimeScale());
        accumulatorNs += elapsedNs * timeScale;

        int steps = 0;
        long updateNsTotal = 0L;
        while (accumulatorNs >= STEP_NS && steps < MAX_UPDATE_STEPS) {
            long t0 = System.nanoTime();
            tick(GameContext.DT);
            updateNsTotal += (System.nanoTime() - t0);
            accumulatorNs -= STEP_NS;
            steps++;
        }

        if (accumulatorNs >= STEP_NS) {
            int dropped = (int) Math.min(Integer.MAX_VALUE, Math.floor(accumulatorNs / STEP_NS));
            droppedUpdateSteps += Math.max(0, dropped);
            // Keep a small remainder so simulation can recover without spiraling.
            accumulatorNs = STEP_NS * 0.5;
        }

        double updateMs = updateNsTotal / 1_000_000.0;
        emaUpdateMs = smooth(emaUpdateMs, updateMs, 0.20);

        ctx.perfFrameMs = emaFrameMs;
        ctx.perfFps = (emaFrameMs <= 1e-6) ? 0.0 : (1000.0 / emaFrameMs);
        ctx.perfFrameJitterMs = emaJitterMs;
        ctx.perfUpdateMs = emaUpdateMs;
        ctx.perfUpdateSteps = steps;
        ctx.perfDroppedUpdates = droppedUpdateSteps;
        ctx.perfRenderMs = emaRenderMs;

        if (steps > 0 || ctx.state != GameState.PAUSED || ctx.eventBannerT > 0 || ctx.gameOver) {
            repaint();
        }
    }

    private void tick(double dt) {
        // Pause freezes simulation
        if (ctx.state == GameState.PAUSED) {
            if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;
            return;
        }

        // Update controls -> aim
        controls.update(dt);
        double mouseWorldX = ctx.camX + controls.getMouseX();
        double mouseWorldY = ctx.camY + controls.getMouseY();
        ctx.cursorWorldX = mouseWorldX;
        ctx.cursorWorldY = mouseWorldY;
        controls.updateAim(mouseWorldX, mouseWorldY);

        // Showcase mode: keep movement/camera responsive, but disable all autonomous systems.
        if (ctx.config.mode == GameMode.SHOWCASE) {
            PhysicsSystem.update(ctx, dt);
            if (ctx.player != null) {
                ctx.player.x = GameMath.clamp(ctx.player.x, 0, ctx.WORLD_W);
                ctx.player.y = GameMath.clamp(ctx.player.y, 0, ctx.WORLD_H);
            }
            UISystem.updatePings(ctx, dt);
            CameraSystem.update(ctx, viewportW(), viewportH());
            return;
        }

        // Physics: movement, firing, collisions
        PhysicsSystem.update(ctx, dt);

        // AI
        AISystem.update(ctx, dt);

        // Carrier craft launch/replenish
        CarrierSystem.update(ctx, dt);

        // Economy (mining/salvage/win checks)
        EconomySystem.update(ctx, dt);

        // Campaign progression / sector objectives
        CampaignSystem.update(ctx, dt);
        LastStandSystem.update(ctx, dt);

        // Pings fade
        UISystem.updatePings(ctx, dt);

        // Events
        EventSystem.update(ctx, dt);

        // Camera last
        CameraSystem.update(ctx, viewportW(), viewportH());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        long renderStart = System.nanoTime();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GameRenderSystem.render(ctx, g2, viewportW(), viewportH());

        g2.dispose();
        double renderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
        emaRenderMs = smooth(emaRenderMs, renderMs, 0.20);
        ctx.perfRenderMs = emaRenderMs;
    }

    public void shutdown() {
        if (timer.isRunning()) timer.stop();
    }

    @Override
    public void removeNotify() {
        shutdown();
        super.removeNotify();
    }

    // Key bindings live here so Swing can bind to this component, but the logic is in systems.
    void installBindings(GameContext ctx, PlayerControl controls, Runnable exitToMenu, Runnable toggleFullscreen) {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0, false), "toggleShop", () -> UISystem.toggleShop(ctx));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape", () -> {
            if (ctx.state == GameState.GAME_OVER || ctx.gameOver) {
                if (exitToMenu != null) exitToMenu.run();
                return;
            }
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) {
                UISystem.closeAllOverlays(ctx);
                return;
            }
            ctx.state = (ctx.state == GameState.PAUSED) ? GameState.RUNNING : GameState.PAUSED;
        });

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, false), "toggleBaseMenu", () -> UISystem.toggleBaseMenu(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0, false), "lockUnderMouse", () -> TargetingSystem.lockClosestToMouse(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0, false), "cycleLeft", () -> TargetingSystem.cycleLockedTarget(ctx, -1));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0, false), "cycleRight", () -> TargetingSystem.cycleLockedTarget(ctx, +1));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_M, 0, false), "toggleMap", () -> UISystem.toggleMap(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, false), "pingAtCursor", () -> UISystem.pingAtCursor(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false), "setWaypoint", () -> UISystem.setWaypointAtCursor(ctx, controls));

        // Toggle turret auto-lock
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_T, 0, false), "toggleTurretAuto", () -> ctx.autoLockTurrets = !ctx.autoLockTurrets);

        // Fullscreen (Alt+Enter)
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK, false), "fullscreen", () -> {
            if (toggleFullscreen != null) toggleFullscreen.run();
            requestFocusInWindow();
        });

        // Mining hold F uses KeyListener semantics; bind press/release
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, false), "miningDown", () -> ctx.miningKeyDown = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true), "miningUp", () -> ctx.miningKeyDown = false);

        // Abilities
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "shieldOvercharge", () -> {
            if (ctx.player == null || !ctx.player.alive) return;
            if (ctx.state != GameState.RUNNING) return;
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;
            ctx.player.tryShieldOvercharge();
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, false), "missileSalvo", () -> {
            if (ctx.player == null || !ctx.player.alive) return;
            if (ctx.state != GameState.RUNNING) return;
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;

            Ship target = null;
            if (isAlive(ctx.lockedTarget) && TeamSystem.isHostileToPlayer(ctx, ctx.lockedTarget.faction)) {
                target = ctx.lockedTarget;
            } else {
                target = findClosestEnemyToPoint(ctx, ctx.player.x, ctx.player.y, 1100);
            }
            if (target == null) return;
            if (!TeamSystem.isHostileToPlayer(ctx, target.faction)) return;
            ctx.projectiles.addAll(ctx.player.tryMissileSalvo(target, GameContext.DT));
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false), "carrierLaunch", () -> {
            if (ctx.player == null || !ctx.player.alive) return;
            if (ctx.state != GameState.RUNNING) return;
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;
            UISystem.tryCarrierLaunch(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "carrierRecall", () -> {
            if (ctx.player == null || !ctx.player.alive) return;
            if (ctx.state != GameState.RUNNING) return;
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;
            UISystem.tryCarrierRecall(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_V, 0, false), "carrierMode", () -> {
            if (ctx.player == null || !ctx.player.alive) return;
            if (ctx.state != GameState.RUNNING) return;
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;
            UISystem.tryCarrierToggleMode(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0, false), "carrierAutoLaunch", () -> {
            if (ctx.player == null || !ctx.player.alive) return;
            if (ctx.state != GameState.RUNNING) return;
            if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;
            UISystem.tryCarrierToggleAutoLaunch(ctx);
        });

        // Menu
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0, false), "toMenu", () -> { if (exitToMenu != null) exitToMenu.run(); });

        // Primary/secondary fire
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "primaryDown", () -> ctx.firingPrimary = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "primaryUp", () -> ctx.firingPrimary = false);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, false), "secondaryDown", () -> ctx.firingSecondary = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true), "secondaryUp", () -> ctx.firingSecondary = false);
    }

    private void bind(InputMap im, ActionMap am, KeyStroke ks, String name, Runnable action) {
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        return s.alive && !s.dying && s.hp > 0;
    }

    private static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (s.role == ShipRole.BASE) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    private static double smooth(double prev, double sample, double alpha) {
        if (sample < 0.0) sample = 0.0;
        if (prev <= 1e-9) return sample;
        return prev + (sample - prev) * Math.max(0.0, Math.min(1.0, alpha));
    }
}
