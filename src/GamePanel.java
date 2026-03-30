import app.config.GameConfig;
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
    private final GameSimulationRuntime runtime;
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
        this.runtime = new GameSimulationRuntime(this.ctx);

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
        InputSnapshot input = controls.snapshot();
        boolean shouldRepaint = runtime.advanceFrame(
                System.nanoTime(),
                input,
                viewportW(),
                viewportH(),
                Math.max(0.0, DevTools.getTimeScale()));
        if (shouldRepaint) {
            repaint();
        }
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
        runtime.recordRenderMs(renderMs);
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

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0, false), "toggleShop", () -> GameplayActions.toggleShop(ctx));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape", () -> {
            GameplayActions.handleEscape(ctx, exitToMenu);
        });

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, false), "toggleBaseMenu", () -> GameplayActions.toggleBaseMenu(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_O, 0, false), "togglePowerManagement", () -> GameplayActions.togglePowerManagement(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_H, 0, false), "toggleCrewStations", () -> GameplayActions.toggleCrewStations(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, 0, false), "toggleFlightDeck", () -> GameplayActions.toggleFlightDeck(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0, false), "lockUnderMouse", () -> GameplayActions.lockUnderMouse(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0, false), "cycleLeft", () -> GameplayActions.cycleLockedTarget(ctx, -1));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0, false), "cycleRight", () -> GameplayActions.cycleLockedTarget(ctx, +1));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_M, 0, false), "toggleMap", () -> GameplayActions.toggleMap(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_N, 0, false), "cycleHudDetail", () -> GameplayActions.cycleHudDetail(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, 0, false), "cycleXrayFilter", () -> GameplayActions.cycleXrayFilter(ctx, +1));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_QUOTE, 0, false), "clearXrayFocus", () -> GameplayActions.clearXrayFocus(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, false), "pingAtCursor", () -> GameplayActions.pingAtCursor(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false), "setWaypoint", () -> GameplayActions.setWaypointAtCursor(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK, false), "zoomIn", () -> {
            CameraSystem.stepZoom(ctx, +1);
            CameraSystem.update(ctx, viewportW(), viewportH());
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK, false), "zoomOut", () -> {
            CameraSystem.stepZoom(ctx, -1);
            CameraSystem.update(ctx, viewportW(), viewportH());
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, false), "zoomReset", () -> {
            CameraSystem.resetZoom(ctx);
            CameraSystem.update(ctx, viewportW(), viewportH());
        });

        // Toggle turret auto-lock
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_T, 0, false), "toggleTurretAuto", () -> GameplayActions.toggleTurretAutoLock(ctx));

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
            GameplayActions.tryShieldOvercharge(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_X, 0, false), "superweapon", () -> {
            GameplayActions.trySuperweapon(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false), "carrierLaunch", () -> {
            GameplayActions.tryCarrierLaunch(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "carrierRecall", () -> {
            GameplayActions.tryCarrierRecall(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_V, 0, false), "carrierMode", () -> {
            GameplayActions.tryCarrierToggleMode(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0, false), "carrierAutoLaunch", () -> {
            GameplayActions.tryCarrierToggleAutoLaunch(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0, false), "battlefieldWarp", () -> {
            GameplayActions.tryTeleportToBase(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0, false), "teleportToBase", () -> {
            GameplayActions.tryTeleportToBase(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0, false), "cyclePowerPreset", () -> {
            GameplayActions.cyclePowerPreset(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_U, 0, false), "cycleCrewOrder", () -> {
            GameplayActions.cycleCrewOrder(ctx);
        });
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SEMICOLON, 0, false), "toggleEmergencyThrust", () -> {
            GameplayActions.toggleEmergencyThrust(ctx);
        });

        // Menu
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0, false), "toMenu", () -> { if (exitToMenu != null) exitToMenu.run(); });

        // Primary/secondary fire
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "primaryDown", () -> ctx.firingPrimaryManual = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "primaryUp", () -> ctx.firingPrimaryManual = false);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, false), "secondaryDown", () -> ctx.firingSecondaryManual = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true), "secondaryUp", () -> ctx.firingSecondaryManual = false);
    }

    private void bind(InputMap im, ActionMap am, KeyStroke ks, String name, Runnable action) {
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

}
