import app.config.GameConfig;
import app.state.AssetLoadGuard;
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
    private boolean controllerPrimaryHeld = false;
    private boolean controllerSecondaryHeld = false;
    private boolean checkpointPersistedForShutdown = false;
    private boolean multiplayerTransportExitHandled = false;
    private boolean multiplayerMatchCompletionExitHandled = false;

    public GamePanel(GameConfig config, Runnable exitToMenu) {
        this(config, exitToMenu, null);
    }

    public GamePanel(GameConfig config, Runnable exitToMenu, Runnable toggleFullscreen) {
        this(new GameContext(config), true, exitToMenu, toggleFullscreen);
    }

    public GamePanel(GameContext context, Runnable exitToMenu, Runnable toggleFullscreen) {
        this(context, false, exitToMenu, toggleFullscreen);
    }

    private GamePanel(GameContext context, boolean initializeWorld, Runnable exitToMenu, Runnable toggleFullscreen) {
        this.exitToMenu = exitToMenu;
        this.toggleFullscreen = toggleFullscreen;

        this.ctx = context;
        this.runtime = new GameSimulationRuntime(this.ctx);

        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        // World init
        if (initializeWorld) {
            SpawnSystem.initWorld(ctx);
        }
        ExperienceRuntime.activate(ctx.experience);
        FirstHourOnboardingSystem.init(ctx);
        TacticalCombatDepthSystem.init(ctx);
        Renderer.prewarmAssetCaches(ctx.config.mode);
        AssetLoadGuard.markGameplayBegun();

        // Input
        controls = InputSystem.install(this, ctx, this::exitToMenu, toggleFullscreen);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                ExperienceRuntime.releaseHeldInputs(ctx);
                controllerPrimaryHeld = false;
                controllerSecondaryHeld = false;
                if (ctx.experience.pauseOnFocusLoss && ctx.state == GameState.RUNNING && !ctx.multiplayerBattle) {
                    ctx.state = GameState.PAUSED;
                    EventSystem.showBanner(ctx, "PAUSED: WINDOW FOCUS LOST", 1.2);
                }
            }
        });

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
        if (handleMultiplayerTransportAbort()) {
            return;
        }
        if (handleMultiplayerMatchCompletionReturn()) {
            return;
        }
        ExperienceRuntime.update(ctx);
        boolean controllerPrimary = ControllerInputSystem.isActionPressed("primaryDown");
        boolean controllerSecondary = ControllerInputSystem.isActionPressed("secondaryDown");
        if (controllerPrimary && !controllerPrimaryHeld) ExperienceRuntime.firingPressed(ctx, false);
        else if (!controllerPrimary && controllerPrimaryHeld) ExperienceRuntime.firingReleased(ctx, false);
        if (controllerSecondary && !controllerSecondaryHeld) ExperienceRuntime.firingPressed(ctx, true);
        else if (!controllerSecondary && controllerSecondaryHeld) ExperienceRuntime.firingReleased(ctx, true);
        controllerPrimaryHeld = controllerPrimary;
        controllerSecondaryHeld = controllerSecondary;
        InputSnapshot input = controls.snapshot();
        boolean shouldRepaint = runtime.advanceFrame(
                System.nanoTime(),
                input,
                viewportW(),
                viewportH(),
                Math.max(0.0, DevTools.getTimeScale()));
        if (handleMultiplayerTransportAbort()) {
            return;
        }
        if (handleMultiplayerMatchCompletionReturn()) {
            return;
        }
        if (runtime.consumeSafeMissionExitReady()) {
            exitToMenu();
            return;
        }
        if (shouldRepaint) {
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        long renderStart = System.nanoTime();
        AssetLoadGuard.beginRenderedFrame();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GameRenderSystem.render(ctx, g2, viewportW(), viewportH());
        } finally {
            g2.dispose();
            AssetLoadGuard.endRenderedFrame();
        }
        double renderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
        runtime.recordRenderMs(renderMs);
        PerformanceGuardrails.update(ctx);
    }

    public void shutdown() {
        if (timer.isRunning()) timer.stop();
        if (ctx.multiplayerInGameSession != null) {
            ctx.multiplayerInGameSession.close();
            ctx.multiplayerInGameSession = null;
        }
        persistCheckpointForShutdown();
    }

    @Override
    public void removeNotify() {
        shutdown();
        super.removeNotify();
    }

    private void exitToMenu() {
        persistCheckpointForShutdown();
        if (exitToMenu != null) exitToMenu.run();
    }

    private boolean handleMultiplayerTransportAbort() {
        if (multiplayerTransportExitHandled || !ctx.multiplayerBattle || ctx.multiplayerInGameSession == null) {
            return false;
        }
        MultiplayerInGameDuelSession.State state = ctx.multiplayerInGameSession.state();
        if (state != MultiplayerInGameDuelSession.State.DISCONNECTED
                && state != MultiplayerInGameDuelSession.State.ERROR) {
            return false;
        }
        multiplayerTransportExitHandled = true;
        String status = ctx.multiplayerInGameSession.status();
        if (status == null || status.isBlank()) {
            status = state == MultiplayerInGameDuelSession.State.ERROR
                    ? "Multiplayer connection failed"
                    : "Multiplayer connection closed";
        }
        EventSystem.showBanner(ctx, status, 1.4);
        exitToMenu();
        return true;
    }

    private boolean handleMultiplayerMatchCompletionReturn() {
        if (multiplayerMatchCompletionExitHandled || !ctx.multiplayerBattle || !ctx.gameOver) {
            return false;
        }
        MultiplayerInGameDuelSession session = ctx.multiplayerInGameSession;
        if (session != null) {
            session.requestReturnToLobby(ctx.gameOverText);
            if (!session.readyToReleasePeerForLobby()) {
                repaint();
                return true;
            }
        }
        multiplayerMatchCompletionExitHandled = true;
        exitToMenu();
        return true;
    }

    private void persistCheckpointForShutdown() {
        if (checkpointPersistedForShutdown) return;
        CampaignSystem.persistCheckpointForMenuExit(ctx);
        checkpointPersistedForShutdown = true;
    }

    // Key bindings live here so Swing can bind to this component, but the logic is in systems.
    void installBindings(GameContext ctx, PlayerControl controls, Runnable exitToMenu, Runnable toggleFullscreen) {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bind(im, am, "toggleShop", () -> GameplayActions.toggleShop(ctx));

        bind(im, am, "escape", () -> {
            GameplayActions.handleEscape(ctx, exitToMenu);
        });

        bind(im, am, "toggleBaseMenu", () -> GameplayActions.toggleBaseMenu(ctx));
        bind(im, am, "togglePowerManagement", () -> GameplayActions.togglePowerManagement(ctx));
        bind(im, am, "toggleCrewStations", () -> GameplayActions.toggleCrewStations(ctx));
        bind(im, am, "toggleFlightDeck", () -> GameplayActions.toggleFlightDeck(ctx));
        bind(im, am, "lockUnderMouse", () -> GameplayActions.lockUnderMouse(ctx, controls));
        bind(im, am, "cycleCommIntent", () -> GameplayActions.cycleCommIntent(ctx, +1));
        bind(im, am, "hailContact", () -> GameplayActions.hailCurrentContact(ctx));
        bind(im, am, "cycleLeft", () -> GameplayActions.cycleLockedTarget(ctx, -1));
        bind(im, am, "cycleRight", () -> GameplayActions.cycleLockedTarget(ctx, +1));

        bind(im, am, "toggleMap", () -> ExperienceRuntime.mapPressed(ctx));
        bind(im, am, "toggleMapUp", () -> ExperienceRuntime.mapReleased(ctx));
        bind(im, am, "cycleHudDetail", () -> GameplayActions.cycleHudDetail(ctx));
        bind(im, am, "toggleTacticalView", () -> GameplayActions.toggleTacticalView(ctx));
        bind(im, am, "cycleXrayFilter", () -> GameplayActions.cycleXrayFilter(ctx, +1));
        bind(im, am, "clearXrayFocus", () -> GameplayActions.clearXrayFocus(ctx));
        bind(im, am, "pingAtCursor", () -> GameplayActions.pingAtCursor(ctx, controls));
        bind(im, am, "setWaypoint", () -> GameplayActions.setWaypointAtCursor(ctx, controls));
        bind(im, am, "zoomIn", () -> {
            if (ctx.ui.mapOpen) {
                UISystem.stepStrategicMapZoom(ctx, +1, viewportW() / 2, viewportH() / 2, viewportW(), viewportH());
            } else {
                CameraSystem.stepZoom(ctx, +1);
                CameraSystem.update(ctx, viewportW(), viewportH());
            }
        });
        bind(im, am, "zoomOut", () -> {
            if (ctx.ui.mapOpen) {
                UISystem.stepStrategicMapZoom(ctx, -1, viewportW() / 2, viewportH() / 2, viewportW(), viewportH());
            } else {
                CameraSystem.stepZoom(ctx, -1);
                CameraSystem.update(ctx, viewportW(), viewportH());
            }
        });
        bind(im, am, "zoomReset", () -> {
            if (ctx.ui.mapOpen) {
                UISystem.resetStrategicMapZoom(ctx);
            } else {
                CameraSystem.resetZoom(ctx);
                CameraSystem.update(ctx, viewportW(), viewportH());
            }
        });

        // Toggle turret auto-lock
        bind(im, am, "toggleTurretAuto", () -> GameplayActions.toggleTurretAutoLock(ctx));

        // Fullscreen (Alt+Enter)
        bind(im, am, "fullscreen", () -> {
            if (toggleFullscreen != null) toggleFullscreen.run();
            requestFocusInWindow();
        });

        // Mining hold F uses KeyListener semantics; bind press/release
        bind(im, am, "miningDown", () -> ExperienceRuntime.miningPressed(ctx));
        bind(im, am, "miningUp", () -> ExperienceRuntime.miningReleased(ctx));

        // Abilities
        bind(im, am, "shieldOvercharge", () -> {
            GameplayActions.tryShieldOvercharge(ctx);
        });
        bind(im, am, "superweapon", () -> {
            GameplayActions.trySuperweapon(ctx);
        });
        bind(im, am, "carrierLaunch", () -> {
            GameplayActions.tryCarrierLaunch(ctx);
        });
        bind(im, am, "carrierRecall", () -> {
            GameplayActions.tryCarrierRecall(ctx);
        });
        bind(im, am, "carrierMode", () -> {
            GameplayActions.tryCarrierToggleMode(ctx);
        });
        bind(im, am, "carrierAutoLaunch", () -> {
            GameplayActions.tryCarrierToggleAutoLaunch(ctx);
        });
        bind(im, am, "battlefieldWarp", () -> {
            GameplayActions.tryTeleportToBase(ctx);
        });
        bind(im, am, "teleportToBase", () -> {
            GameplayActions.tryTeleportToBase(ctx);
        });
        bind(im, am, "cyclePowerPreset", () -> {
            GameplayActions.cyclePowerPreset(ctx);
        });
        bind(im, am, "cycleCrewOrder", () -> {
            GameplayActions.cycleCrewOrder(ctx);
        });
        bind(im, am, "toggleEmergencyThrust", () -> {
            GameplayActions.toggleEmergencyThrust(ctx);
        });

        // Menu
        bind(im, am, "toMenu", this::exitToMenu);
        bind(im, am, "overlayDiagnostics", () -> UISystem.printOverlayDiagnostics(ctx));
        bind(im, am, "toggleControlsScreen", () -> GameplayActions.toggleControlsScreen(ctx));
        bind(im, am, "skipOnboardingBeat", () -> {
            if (TutorialSystem.isActive(ctx)) TutorialSystem.skipCurrent(ctx);
            else FirstHourOnboardingSystem.skipCurrent(ctx);
        });
        bind(im, am, "toggleTutorialArchive", () -> {
            if (TutorialSystem.isActive(ctx)) TutorialSystem.toggleArchive(ctx);
            else FirstHourOnboardingSystem.toggleArchive(ctx);
        });
        bind(im, am, "toggleTacticalOrders", () -> TacticalCombatDepthSystem.toggleOverlay(ctx));
        bind(im, am, "cycleTacticalOrder", () -> GameplayActions.cycleTacticalOrder(ctx));
        bind(im, am, "toggleTacticalPause", () -> {
            if (ctx.multiplayerBattle) {
                EventSystem.showBanner(ctx, "PAUSE DISABLED IN MULTIPLAYER", 1.1);
                return;
            }
            TacticalCombatDepthSystem.togglePause(ctx);
        });
        bind(im, am, "cycleSupportMode", () -> TacticalCombatDepthSystem.cycleSupportMode(ctx));
        bind(im, am, "activateSupportMode", () -> TacticalCombatDepthSystem.activateSupportAtCursor(ctx));
        bind(im, am, "toggleOrientationHold", () -> TacticalCombatDepthSystem.toggleOrientationHold(ctx));
        bind(im, am, "toggleBulkheads", () -> TacticalCombatDepthSystem.toggleBulkheads(ctx));
        bind(im, am, "weaponOverdrive", () -> TacticalCombatDepthSystem.overdriveWeapons(ctx));
        bind(im, am, "cyclePointDefensePriority", () -> TacticalCombatDepthSystem.cyclePointDefensePriority(ctx));
        bind(im, am, "cycleTacticalDoctrine", () -> TacticalCombatDepthSystem.cycleDoctrine(ctx));
        bind(im, am, "cycleTacticalGroup", () -> TacticalCombatDepthSystem.cycleGroup(ctx));
        bind(im, am, "scuttleDisabledShip", () -> TacticalCombatDepthSystem.scuttleNearestDisabled(ctx));

        // Primary/secondary fire
        bind(im, am, "primaryDown", () -> ExperienceRuntime.firingPressed(ctx, false));
        bind(im, am, "primaryUp", () -> ExperienceRuntime.firingReleased(ctx, false));
        bind(im, am, "secondaryDown", () -> ExperienceRuntime.firingPressed(ctx, true));
        bind(im, am, "secondaryUp", () -> ExperienceRuntime.firingReleased(ctx, true));
    }

    private void bind(InputMap im, ActionMap am, String name, Runnable action) {
        KeyStroke ks = HotkeyRegistry.stroke(name);
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

}
