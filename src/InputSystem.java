import javax.swing.*;
import java.awt.event.*;

public final class InputSystem {
    private static final long CAMERA_RESET_DOUBLE_TAP_NS = 300_000_000L;

    private InputSystem(){}

    public static PlayerControl install(GamePanel panel, GameContext ctx, Runnable exitToMenu, Runnable toggleFullscreen) {
        PlayerControl controls = new PlayerControl(ctx.player);

        panel.addKeyListener(controls);
        panel.addMouseMotionListener(controls);
        panel.addMouseWheelListener(e -> {
            int rot = e.getWheelRotation();
            if (rot == 0) return;
            if (ctx.ui.commTradeMenu.active) {
                CommSystem.adjustTradeQuantity(ctx, -rot);
                return;
            }
            if (ctx.ui.mapOpen) {
                if (UISystem.handleCampaignMapWheel(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                UISystem.stepStrategicMapZoom(ctx, -rot, e.getX(), e.getY(), panel.viewportW(), panel.viewportH());
            } else {
                CameraSystem.stepZoom(ctx, -rot);
                CameraSystem.update(ctx, panel.viewportW(), panel.viewportH());
            }
        });

        panel.installBindings(ctx, controls, exitToMenu, toggleFullscreen);

        // Dev scenarios (F6-F9/F11/Ctrl+F12)
        DevScenarios.installBindings(panel, ctx);

        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isShiftDown() && SwingUtilities.isRightMouseButton(e)) {
                    TacticalCombatDepthSystem.issueSelectedOrder(
                            ctx,
                            CameraSystem.screenToWorldX(ctx, e.getX()),
                            CameraSystem.screenToWorldY(ctx, e.getY()));
                    return;
                }
                if (ctx.ui.controlsScreenOpen && !ctx.ui.controlsCaptureAction.isBlank()
                        && e.getButton() > 0) {
                    HotkeyRegistry.RemapResult result = HotkeyRegistry.remapMouseDetailed(ctx.ui.controlsCaptureAction, e.getButton());
                    ctx.ui.controlsStatusMessage = result.message();
                    if (result.accepted()) ctx.ui.controlsCaptureAction = "";
                    return;
                }
                if (UISystem.handleCoreMenuClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleCommTradeMenuClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleCommsContextMenuClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleCommsPanelClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleHudPanelClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleFleetNetClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleShopClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleXrayClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (ctx.ui.mapOpen) {
                    if (UISystem.handleCampaignMapUiClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                        return;
                    }
                    UISystem.handleMapClick(ctx, e, panel.viewportW(), panel.viewportH());
                    return;
                }
                if (ctx.state == GameState.PAUSED) return;
                if (ctx.ui.shopOpen || ctx.ui.baseMenuOpen || ctx.ui.commsOpen || ctx.ui.powerManagementOpen
                        || ctx.ui.crewStationsOpen || ctx.ui.flightDeckOpen) return;

                if (SwingUtilities.isRightMouseButton(e)
                        && UISystem.tryOpenCommsContextAtWorld(
                        ctx,
                        CameraSystem.screenToWorldX(ctx, e.getX()),
                        CameraSystem.screenToWorldY(ctx, e.getY()),
                        e.getX(),
                        e.getY())) {
                    return;
                }

                if (ctx.state == GameState.FLEET) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        CampaignSystem.selectFleetShipAtCursor(ctx, e.getX(), e.getY());
                    }
                    return;
                }

                HotkeyRegistry.noteMouseInput();
                if (e.getButton() == HotkeyRegistry.mouseButton("primaryDown")) ExperienceRuntime.firingPressed(ctx, false);
                if (e.getButton() == HotkeyRegistry.mouseButton("secondaryDown")) ExperienceRuntime.firingPressed(ctx, true);
                if (e.getButton() == HotkeyRegistry.mouseButton("lockUnderMouse")) GameplayActions.lockUnderMouse(ctx, controls);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (e.getButton() == HotkeyRegistry.mouseButton("primaryDown")) ExperienceRuntime.firingReleased(ctx, false);
                if (e.getButton() == HotkeyRegistry.mouseButton("secondaryDown")) ExperienceRuntime.firingReleased(ctx, true);
            }
        });

        panel.addKeyListener(new KeyAdapter() {
            private long leftTapNs = Long.MIN_VALUE;
            private long rightTapNs = Long.MIN_VALUE;
            private long upTapNs = Long.MIN_VALUE;
            private long downTapNs = Long.MIN_VALUE;

            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                HotkeyRegistry.noteKeyboardInput();
                if (e.isControlDown() && keyCode == KeyEvent.VK_G) {
                    TacticalCombatDepthSystem.selectNearestFriendlyIntoActiveGroup(ctx);
                    e.consume();
                    return;
                }
                if (ctx.ui.controlsScreenOpen) {
                    if (!ctx.ui.controlsCaptureAction.isBlank()) {
                        HotkeyRegistry.RemapResult result = HotkeyRegistry.remapKeyboardDetailed(
                                ctx.ui.controlsCaptureAction,
                                KeyStroke.getKeyStrokeForEvent(e));
                        ctx.ui.controlsStatusMessage = result.message();
                        if (result.accepted()) ctx.ui.controlsCaptureAction = "";
                        e.consume();
                        return;
                    }
                    if (e.isControlDown() && keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_6) {
                        HotkeyRegistry.restoreDefaults(HotkeyRegistry.Scope.values()[keyCode - KeyEvent.VK_1]);
                        ctx.ui.controlsStatusMessage = "Restored "
                                + HotkeyRegistry.Scope.values()[keyCode - KeyEvent.VK_1].name()
                                + " defaults.";
                        e.consume();
                        return;
                    }
                    if (keyCode == KeyEvent.VK_UP) {
                        ctx.ui.controlsSelectedIndex = Math.max(0, ctx.ui.controlsSelectedIndex - 1);
                        e.consume();
                        return;
                    }
                    if (keyCode == KeyEvent.VK_DOWN) {
                        ctx.ui.controlsSelectedIndex++;
                        e.consume();
                        return;
                    }
                    if (keyCode == KeyEvent.VK_ENTER) {
                        java.util.List<HotkeyRegistry.Binding> found = HotkeyRegistry.search(ctx.ui.controlsSearchQuery);
                        if (!found.isEmpty()) {
                            ctx.ui.controlsSelectedIndex = Math.min(ctx.ui.controlsSelectedIndex, found.size() - 1);
                            ctx.ui.controlsCaptureAction = found.get(ctx.ui.controlsSelectedIndex).action();
                            ctx.ui.controlsStatusMessage = "Press a key for " + ctx.ui.controlsCaptureAction + ".";
                        }
                        e.consume();
                        return;
                    }
                    if (keyCode == KeyEvent.VK_BACK_SPACE && !ctx.ui.controlsSearchQuery.isEmpty()) {
                        ctx.ui.controlsSearchQuery = ctx.ui.controlsSearchQuery.substring(0, ctx.ui.controlsSearchQuery.length() - 1);
                        e.consume();
                        return;
                    }
                    char ch = e.getKeyChar();
                    if (!Character.isISOControl(ch) && ctx.ui.controlsSearchQuery.length() < 32) {
                        ctx.ui.controlsSearchQuery += ch;
                        e.consume();
                        return;
                    }
                }
                if (GameplayActions.tryHandleCommTradeMenuHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleStrategicEncounterHotkey(ctx, e)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleCampaignEpisodeHotkey(ctx, e)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleMapHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                handleCameraPanKeyPressed(ctx, keyCode);
                if (GameplayActions.tryHandlePowerOverlayHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleCrewStationsHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleFlightDeckHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleShopHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleBaseMenuHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleShootingRangeHotkey(ctx, e)) {
                    e.consume();
                    return;
                }
                if (GameplayActions.tryHandleAllySpawnHotkey(ctx, keyCode)) {
                    e.consume();
                    return;
                }
                DevTools.handleKeyPressed(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> ctx.cameraPanLeft = false;
                    case KeyEvent.VK_RIGHT -> ctx.cameraPanRight = false;
                    case KeyEvent.VK_UP -> ctx.cameraPanUp = false;
                    case KeyEvent.VK_DOWN -> ctx.cameraPanDown = false;
                    default -> {
                    }
                }
            }

            private void handleCameraPanKeyPressed(GameContext ctx, int keyCode) {
                long now = System.nanoTime();
                switch (keyCode) {
                    case KeyEvent.VK_LEFT -> {
                        if (ctx.cameraPanLeft) return;
                        ctx.cameraPanLeft = true;
                        if (now - leftTapNs <= CAMERA_RESET_DOUBLE_TAP_NS) CameraSystem.resetManualOffset(ctx);
                        leftTapNs = now;
                    }
                    case KeyEvent.VK_RIGHT -> {
                        if (ctx.cameraPanRight) return;
                        ctx.cameraPanRight = true;
                        if (now - rightTapNs <= CAMERA_RESET_DOUBLE_TAP_NS) CameraSystem.resetManualOffset(ctx);
                        rightTapNs = now;
                    }
                    case KeyEvent.VK_UP -> {
                        if (ctx.cameraPanUp) return;
                        ctx.cameraPanUp = true;
                        if (now - upTapNs <= CAMERA_RESET_DOUBLE_TAP_NS) CameraSystem.resetManualOffset(ctx);
                        upTapNs = now;
                    }
                    case KeyEvent.VK_DOWN -> {
                        if (ctx.cameraPanDown) return;
                        ctx.cameraPanDown = true;
                        if (now - downTapNs <= CAMERA_RESET_DOUBLE_TAP_NS) CameraSystem.resetManualOffset(ctx);
                        downTapNs = now;
                    }
                    default -> {
                    }
                }
            }
        });

        return controls;
    }
}
