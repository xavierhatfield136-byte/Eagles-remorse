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
            if (ctx.ui.mapOpen) {
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
                if (UISystem.handleCoreMenuClick(ctx, e, panel.viewportW(), panel.viewportH())) {
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
                    UISystem.handleMapClick(ctx, e, panel.viewportW(), panel.viewportH());
                    return;
                }
                if (ctx.state == GameState.PAUSED) return;
                if (ctx.ui.shopOpen || ctx.ui.baseMenuOpen || ctx.ui.powerManagementOpen
                        || ctx.ui.crewStationsOpen || ctx.ui.flightDeckOpen) return;

                if (ctx.state == GameState.FLEET) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        CampaignSystem.selectFleetShipAtCursor(ctx, e.getX(), e.getY());
                    }
                    return;
                }

                if (SwingUtilities.isLeftMouseButton(e)) ctx.firingPrimaryManual = true;
                if (SwingUtilities.isRightMouseButton(e)) ctx.firingSecondaryManual = true;
                if (SwingUtilities.isMiddleMouseButton(e)) GameplayActions.lockUnderMouse(ctx, controls);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) ctx.firingPrimaryManual = false;
                if (SwingUtilities.isRightMouseButton(e)) ctx.firingSecondaryManual = false;
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
                handleCameraPanKeyPressed(ctx, keyCode);
                if (GameplayActions.tryHandleCampaignEpisodeHotkey(ctx, e)) return;
                if (GameplayActions.tryHandleMapHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandlePowerOverlayHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleCrewStationsHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleFlightDeckHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleShopHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleBaseMenuHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleShootingRangeHotkey(ctx, e)) return;
                if (GameplayActions.tryHandleAllySpawnHotkey(ctx, keyCode)) return;
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
