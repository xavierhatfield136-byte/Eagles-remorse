import javax.swing.*;
import java.awt.event.*;

public final class InputSystem {
    private InputSystem(){}

    public static PlayerControl install(GamePanel panel, GameContext ctx, Runnable exitToMenu, Runnable toggleFullscreen) {
        PlayerControl controls = new PlayerControl(ctx.player);

        panel.addKeyListener(controls);
        panel.addMouseMotionListener(controls);

        panel.installBindings(ctx, controls, exitToMenu, toggleFullscreen);

        // Dev scenarios (F6-F9)
        DevScenarios.installBindings(panel, ctx);

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

        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Shop-only weapon switching
                if (ctx.shopOpen) {
                    boolean handled = true;
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_3 -> UISystem.tryEquipEnergyBolt(ctx);
                        case KeyEvent.VK_4 -> UISystem.tryBuyBeamBolt(ctx);
                        case KeyEvent.VK_5 -> UISystem.tryBuyHullPlating(ctx);
                        case KeyEvent.VK_6 -> UISystem.tryBuyShieldArray(ctx);
                        case KeyEvent.VK_7 -> UISystem.tryAddGunTurret(ctx);
                        case KeyEvent.VK_8 -> UISystem.tryAddMissileRack(ctx);
                        case KeyEvent.VK_9 -> UISystem.tryUpgradeCIWS(ctx);
                        case KeyEvent.VK_F1 -> UISystem.trySwapHull(ctx, ShipRole.PATROL, 0, 0);
                        case KeyEvent.VK_F2 -> UISystem.trySwapHull(ctx, ShipRole.PICKET, 180, 0);
                        case KeyEvent.VK_F3 -> UISystem.trySwapHull(ctx, ShipRole.FRIGATE, 0, 0);
                        case KeyEvent.VK_F4 -> UISystem.trySwapHull(ctx, ShipRole.MISSILE_BOAT, 300, 0);
                        case KeyEvent.VK_F5 -> UISystem.trySwapHull(ctx, ShipRole.CIWS_CORVETTE, 250, 0);
                        case KeyEvent.VK_F6 -> UISystem.trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
                        case KeyEvent.VK_F7 -> UISystem.trySwapHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
                        case KeyEvent.VK_F8 -> UISystem.trySwapHull(ctx, ShipRole.BATTLECRUISER, 1600, 2);
                        case KeyEvent.VK_F9 -> UISystem.trySwapHull(ctx, ShipRole.BATTLESHIP, 2200, 2);
                        case KeyEvent.VK_F11 -> UISystem.trySwapHull(ctx, ShipRole.STEALTH_SHIP, 1200, 2);
                        case KeyEvent.VK_F12 -> UISystem.trySwapHull(ctx, ShipRole.DREADNOUGHT, 3200, 3);
                        default -> handled = false;
                    }
                    if (handled) return;
                }

                if (ctx.baseMenuOpen) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_1 -> UISystem.tryUpgradeBase(ctx, 1);
                        case KeyEvent.VK_2 -> UISystem.tryUpgradeBase(ctx, 2);
                        case KeyEvent.VK_3 -> UISystem.tryUpgradeBase(ctx, 3);
                        case KeyEvent.VK_4 -> UISystem.tryUpgradeBase(ctx, 4);
                        case KeyEvent.VK_5 -> UISystem.tryUpgradeBase(ctx, 5);
                        default -> {}
                    }
                    return;
                }

                // In-game ally spawn hotkeys (1-9)
                if (ctx.state != GameState.RUNNING) return;
                if (ctx.player == null || !ctx.player.alive) return;
                if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) return;

                ShipRole role = switch (e.getKeyCode()) {
                    case KeyEvent.VK_1 -> ShipRole.PICKET;
                    case KeyEvent.VK_2 -> ShipRole.PATROL;
                    case KeyEvent.VK_3 -> ShipRole.FRIGATE;
                    case KeyEvent.VK_4 -> ShipRole.MISSILE_BOAT;
                    case KeyEvent.VK_5 -> ShipRole.CIWS_CORVETTE;
                    case KeyEvent.VK_6 -> ShipRole.LIGHT_CRUISER;
                    case KeyEvent.VK_7 -> ShipRole.BATTLECRUISER;
                    case KeyEvent.VK_8 -> ShipRole.BATTLESHIP;
                    case KeyEvent.VK_9 -> ShipRole.DREADNOUGHT;
                    default -> null;
                };

                if (role != null) {
                    SpawnSystem.spawnAlly(ctx, role, ctx.player.x, ctx.player.y);
                }
            }
        });

        return controls;
    }
}
