import app.config.GameConfig;
import app.config.GameMode;
import app.support.AppInfo;
import app.support.ErrorLog;
import javax.swing.*;
import java.awt.*;

/**
 * 3D sandbox bootstrap entrypoint.
 * Uses the existing simulation runtime and systems, but renders with placeholder 3D projection.
 */
public final class Main3D {
    private final JFrame frame;
    private Sandbox3DPanel panel;

    private Main3D(GameConfig config) {
        frame = new JFrame(AppInfo.windowTitle() + " [3D Sandbox]");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        panel = new Sandbox3DPanel(config, this::closeWindow);
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
    }

    private void show() {
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            if (panel != null) panel.requestFocusInWindow();
        });
    }

    private void closeWindow() {
        if (panel != null) {
            panel.shutdown();
            panel = null;
        }
        frame.dispose();
    }

    private static GameConfig parseConfig(String[] args) {
        long seed = System.nanoTime();
        int w = 10000;
        int h = 10000;
        boolean randomEvents = true;
        GameMode mode = GameMode.CAMPAIGN_OPS;

        if (args != null) {
            for (String raw : args) {
                if (raw == null) continue;
                String a = raw.trim();
                if (a.startsWith("--seed=")) {
                    try {
                        seed = Long.parseLong(a.substring("--seed=".length()).trim());
                    } catch (Exception ignored) {
                        // Keep default seed.
                    }
                } else if (a.equals("--small")) {
                    w = 5000;
                    h = 5000;
                } else if (a.equals("--large")) {
                    w = 20000;
                    h = 20000;
                } else if (a.equals("--no-random-events")) {
                    randomEvents = false;
                } else if (a.startsWith("--mode=")) {
                    mode = parseMode(a.substring("--mode=".length()));
                }
            }
        }
        if (mode == GameMode.CUSTOM_BATTLES) {
            String friendly = "FRIGATE:2,LIGHT_CRUISER:2,CRUISER:1,CARRIER:1,FIGHTER:4,BOMBER:2";
            String enemy = "FRIGATE:2,LIGHT_CRUISER:2,CRUISER:1,MISSILE_BOAT:2,PATROL:3,FIGHTER:4";
            return new GameConfig(mode, w, h, randomEvents, seed, false,
                    0, false, 1, friendly, enemy);
        }
        return new GameConfig(mode, w, h, randomEvents, seed, false);
    }

    private static GameMode parseMode(String raw) {
        if (raw == null) return GameMode.CAMPAIGN_OPS;
        return switch (raw.trim().toLowerCase()) {
            case "domination", "four-team", "four_team", "four-team-domination" -> GameMode.FOUR_TEAM_DOMINATION;
            case "custom", "custom-battle", "custom_battle" -> GameMode.CUSTOM_BATTLES;
            case "showcase" -> GameMode.SHOWCASE;
            case "range", "shooting-range", "shooting_range" -> GameMode.SHOOTING_RANGE;
            default -> GameMode.CAMPAIGN_OPS;
        };
    }

    public static void main(String[] args) {
        ErrorLog.installGlobalHandler();
        GameConfig config = parseConfig(args);
        SwingUtilities.invokeLater(() -> new Main3D(config).show());
    }
}
