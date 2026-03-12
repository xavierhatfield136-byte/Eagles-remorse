import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Lightweight 3D sandbox panel.
 * Keeps simulation authoritative in existing systems via GameSimulationRuntime.
 */
final class Sandbox3DPanel extends JPanel implements ActionListener {
    private static final int VIEW_W = 1280;
    private static final int VIEW_H = 720;

    private final GameContext ctx;
    private final GameSimulationRuntime runtime;
    private final Timer timer;
    private final PlayerControl controls;
    private final Runnable onExit;

    // Placeholder camera tuning for pseudo-3D projection.
    private double cameraTilt = 0.82;
    private double cameraZoom = 1.0;

    Sandbox3DPanel(GameConfig config, Runnable onExit) {
        this.onExit = onExit;
        this.ctx = new GameContext(config);
        this.runtime = new GameSimulationRuntime(ctx);

        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setBackground(new Color(6, 10, 18));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        SpawnSystem.initWorld(ctx);

        controls = new PlayerControl(ctx.player);
        addKeyListener(controls);
        addMouseMotionListener(controls);

        installBindings();
        installListeners();
        DevScenarios.installBindings(this, ctx);

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
        if (shouldRepaint) repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        long renderStart = System.nanoTime();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Sandbox3DRenderer.render(ctx, g2, viewportW(), viewportH(), cameraTilt, cameraZoom);

        g2.dispose();
        runtime.recordRenderMs((System.nanoTime() - renderStart) / 1_000_000.0);
    }

    public void shutdown() {
        if (timer != null && timer.isRunning()) timer.stop();
    }

    @Override
    public void removeNotify() {
        shutdown();
        super.removeNotify();
    }

    private void installListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (UISystem.handleCoreMenuClick(ctx, e, viewportW(), viewportH())) {
                    return;
                }
                if (UISystem.handleXrayClick(ctx, e, viewportW(), viewportH())) {
                    return;
                }
                if (ctx.mapOpen) {
                    UISystem.handleMapClick(ctx, e, viewportW(), viewportH());
                    return;
                }
                if (ctx.state == GameState.PAUSED) return;
                if (ctx.shopOpen || ctx.baseMenuOpen || ctx.powerManagementOpen || ctx.crewStationsOpen) return;

                if (SwingUtilities.isLeftMouseButton(e)) ctx.firingPrimaryManual = true;
                if (SwingUtilities.isRightMouseButton(e)) ctx.firingSecondaryManual = true;
                if (SwingUtilities.isMiddleMouseButton(e)) GameplayActions.lockUnderMouse(ctx, controls);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) ctx.firingPrimaryManual = false;
                if (SwingUtilities.isRightMouseButton(e)) ctx.firingSecondaryManual = false;
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                if (GameplayActions.tryHandlePowerOverlayHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleCrewStationsHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleShopHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleBaseMenuHotkey(ctx, keyCode)) return;
                GameplayActions.tryHandleAllySpawnHotkey(ctx, keyCode);
                DevTools.handleKeyPressed(e);
            }
        });
    }

    private void installBindings() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape", () -> GameplayActions.handleEscape(ctx, onExit));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0, false), "toggleShop", () -> GameplayActions.toggleShop(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, false), "toggleBaseMenu", () -> GameplayActions.toggleBaseMenu(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_M, 0, false), "toggleMap", () -> GameplayActions.toggleMap(ctx));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0, false), "lockUnderMouse", () -> GameplayActions.lockUnderMouse(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0, false), "cycleLeft", () -> GameplayActions.cycleLockedTarget(ctx, -1));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0, false), "cycleRight", () -> GameplayActions.cycleLockedTarget(ctx, +1));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, false), "pingAtCursor", () -> GameplayActions.pingAtCursor(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false), "setWaypoint", () -> GameplayActions.setWaypointAtCursor(ctx, controls));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_T, 0, false), "toggleTurretAuto", () -> GameplayActions.toggleTurretAutoLock(ctx));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "shieldOvercharge", () -> GameplayActions.tryShieldOvercharge(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, false), "missileSalvo", () -> GameplayActions.tryMissileSalvo(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false), "carrierLaunch", () -> GameplayActions.tryCarrierLaunch(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "carrierRecall", () -> GameplayActions.tryCarrierRecall(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_V, 0, false), "carrierMode", () -> GameplayActions.tryCarrierToggleMode(ctx));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0, false), "carrierAutoLaunch", () -> GameplayActions.tryCarrierToggleAutoLaunch(ctx));

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0, false), "exit", () -> {
            if (onExit != null) onExit.run();
        });

        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "primaryDown", () -> ctx.firingPrimaryManual = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "primaryUp", () -> ctx.firingPrimaryManual = false);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, false), "secondaryDown", () -> ctx.firingSecondaryManual = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0, true), "secondaryUp", () -> ctx.firingSecondaryManual = false);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, false), "miningDown", () -> ctx.miningKeyDown = true);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true), "miningUp", () -> ctx.miningKeyDown = false);

        // Camera tuning for projection feel.
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0, false), "camTiltUp", () -> cameraTilt = GameMath.clamp(cameraTilt + 0.04, 0.55, 1.25));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0, false), "camTiltDown", () -> cameraTilt = GameMath.clamp(cameraTilt - 0.04, 0.55, 1.25));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK, false), "camZoomIn", () -> cameraZoom = GameMath.clamp(cameraZoom + 0.06, 0.70, 1.45));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK, false), "camZoomOut", () -> cameraZoom = GameMath.clamp(cameraZoom - 0.06, 0.70, 1.45));
    }

    private void bind(InputMap im, ActionMap am, KeyStroke key, String name, Runnable action) {
        im.put(key, name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }
}
