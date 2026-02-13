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

    final GameContext ctx;
    private final Timer timer;

    private final Runnable exitToMenu;
    private final Runnable toggleFullscreen;

    private final PlayerControl controls;

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

        // Timer
        timer = new Timer((int)Math.round(1000.0/60.0), this);
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
        tick();
        repaint();
    }

    private void tick() {
        // Apply developer time scaling (F5) without changing the engine constant.
        final double dt = GameContext.DT * DevTools.getTimeScale();

        // Pause freezes simulation
        if (ctx.state == GameState.PAUSED) {
            if (ctx.eventBannerT > 0) ctx.eventBannerT -= GameContext.DT;
            return;
        }

        // Update controls -> aim
        controls.update(dt);
        double mouseWorldX = ctx.camX + controls.getMouseX();
        double mouseWorldY = ctx.camY + controls.getMouseY();
        ctx.cursorWorldX = mouseWorldX;
        ctx.cursorWorldY = mouseWorldY;
        controls.updateAim(mouseWorldX, mouseWorldY);

        // Physics: movement, firing, collisions
        PhysicsSystem.update(ctx, dt);

        // AI
        AISystem.update(ctx, dt);

        // Economy (mining/salvage/win checks)
        EconomySystem.update(ctx, dt);

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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GameRenderSystem.render(ctx, g2, viewportW(), viewportH());

        g2.dispose();
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

        // Mining hold E uses KeyListener semantics; bind press/release
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "miningDown", () -> ctx.miningKeyDown = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, true), "miningUp", () -> ctx.miningKeyDown = false);

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
}
