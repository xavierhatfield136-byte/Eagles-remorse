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
                }
            }
        }
        return new GameConfig(GameMode.CAMPAIGN_OPS, w, h, randomEvents, seed, false);
    }

    public static void main(String[] args) {
        ErrorLog.installGlobalHandler();
        GameConfig config = parseConfig(args);
        SwingUtilities.invokeLater(() -> new Main3D(config).show());
    }
}
