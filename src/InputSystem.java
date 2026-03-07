import javax.swing.*;
import java.awt.event.*;

public final class InputSystem {
    private InputSystem(){}

    public static PlayerControl install(GamePanel panel, GameContext ctx, Runnable exitToMenu, Runnable toggleFullscreen) {
        PlayerControl controls = new PlayerControl(ctx.player);

        panel.addKeyListener(controls);
        panel.addMouseMotionListener(controls);
        panel.addMouseWheelListener(e -> {
            int rot = e.getWheelRotation();
            if (rot == 0) return;
            CameraSystem.stepZoom(ctx, -rot);
            CameraSystem.update(ctx, panel.viewportW(), panel.viewportH());
        });

        panel.installBindings(ctx, controls, exitToMenu, toggleFullscreen);

        // Dev scenarios (F6-F9/F11/Ctrl+F12)
        DevScenarios.installBindings(panel, ctx);

        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (UISystem.handleCoreMenuClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (UISystem.handleXrayClick(ctx, e, panel.viewportW(), panel.viewportH())) {
                    return;
                }
                if (ctx.mapOpen) {
                    UISystem.handleMapClick(ctx, e, panel.viewportW(), panel.viewportH());
                    return;
                }
                if (ctx.state == GameState.PAUSED) return;
                if (ctx.shopOpen || ctx.baseMenuOpen || ctx.powerManagementOpen || ctx.crewStationsOpen) return;

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
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                if (GameplayActions.tryHandlePowerOverlayHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleCrewStationsHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleShopHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleBaseMenuHotkey(ctx, keyCode)) return;
                if (GameplayActions.tryHandleAllySpawnHotkey(ctx, keyCode)) return;
                DevTools.handleKeyPressed(e);
            }
        });

        return controls;
    }
}
