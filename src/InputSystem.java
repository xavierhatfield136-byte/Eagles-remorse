import javax.swing.*;
import java.awt.event.*;

public final class InputSystem {
    private InputSystem(){}

    public static PlayerControl install(GamePanel panel, GameContext ctx, Runnable exitToMenu, Runnable toggleFullscreen) {
        PlayerControl controls = new PlayerControl(ctx.player);

        panel.addKeyListener(controls);
        panel.addMouseMotionListener(controls);

        panel.installBindings(ctx, controls, exitToMenu, toggleFullscreen);

        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (ctx.mapOpen) {
                    UISystem.handleMapClick(ctx, e, panel.viewportW(), panel.viewportH());
                    return;
                }
                if (ctx.state == GameState.PAUSED) return;
                if (ctx.shopOpen || ctx.baseMenuOpen) return;

                if (SwingUtilities.isLeftMouseButton(e)) ctx.firingPrimary = true;
                if (SwingUtilities.isRightMouseButton(e)) ctx.firingSecondary = true;
                if (SwingUtilities.isMiddleMouseButton(e)) TargetingSystem.lockClosestToMouse(ctx, controls);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) ctx.firingPrimary = false;
                if (SwingUtilities.isRightMouseButton(e)) ctx.firingSecondary = false;
            }
        });

        return controls;
    }
}
