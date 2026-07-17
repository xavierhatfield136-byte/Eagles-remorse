import app.config.MultiplayerLaunchConfig;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** In-game process launcher for the V1 multiplayer LAN duel harness. */
public final class MultiplayerInGameLaunchPanel extends JPanel {
    private final MultiplayerLaunchConfig launch;
    private final Runnable exitToMenu;
    private final JTextArea output;
    private volatile Process process;

    public MultiplayerInGameLaunchPanel(MultiplayerLaunchConfig launch, Runnable exitToMenu) {
        this.launch = launch;
        this.exitToMenu = exitToMenu;
        setName("multiplayerInGameLaunchPanel");
        setLayout(new BorderLayout(12, 12));
        setBackground(new Color(6, 12, 22));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel(launch.host() ? "Hosting Multiplayer Battle" : "Joining Multiplayer Battle");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        add(title, BorderLayout.NORTH);

        output = new JTextArea();
        output.setName("multiplayerInGameLaunchOutput");
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setForeground(new Color(211, 232, 228));
        output.setBackground(new Color(4, 10, 18));
        output.setFont(new Font("Consolas", Font.PLAIN, 13));
        add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        JButton back = new JButton("Back to Menu");
        back.setName("multiplayerInGameBackButton");
        back.addActionListener(e -> {
            shutdown();
            if (exitToMenu != null) exitToMenu.run();
        });
        actions.add(back);
        add(actions, BorderLayout.SOUTH);

        append("Starting V1 multiplayer LAN harness from inside the game...");
        append("Role: " + launch.role);
        append("Report: " + launch.reportPath);
        startProcess();
    }

    public void shutdown() {
        Process active = process;
        if (active != null && active.isAlive()) {
            append("Stopping multiplayer launcher process...");
            active.destroy();
        }
    }

    private void startProcess() {
        Thread worker = new Thread(() -> {
            try {
                List<String> command = command(launch);
                append("$ " + String.join(" ", command));
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                process = builder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        append(line);
                    }
                }
                int exit = process.waitFor();
                append(exit == 0
                        ? "Multiplayer launch completed successfully."
                        : "Multiplayer launch exited with code " + exit + ".");
            } catch (Exception ex) {
                append("Failed to launch multiplayer: " + ex.getMessage());
            }
        }, "multiplayer-in-game-launch");
        worker.setDaemon(true);
        worker.start();
    }

    static List<String> launchCommandForTests(MultiplayerLaunchConfig launch) {
        return command(launch);
    }

    private static List<String> command(MultiplayerLaunchConfig launch) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(javaBinary());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path", ""));
        cmd.add("MultiplayerLanDuelAcceptanceHarness");
        if (launch.host()) {
            cmd.add("host");
            cmd.add("--port=" + launch.port);
            if (!launch.advertisedHostAddress.isBlank()) {
                cmd.add("--host-address=" + launch.advertisedHostAddress);
            }
            if (launch.loopbackOnly) cmd.add("--loopback=true");
        } else {
            cmd.add("client");
            cmd.add("--connect=" + launch.resolvedDirectAddress());
            if (!launch.advertisedClientAddress.isBlank()) {
                cmd.add("--client-address=" + launch.advertisedClientAddress);
            }
        }
        cmd.add("--match=" + launch.matchId);
        cmd.add("--timeout-ms=" + launch.timeoutMs);
        cmd.add("--report=" + launch.reportPath);
        return cmd;
    }

    private static String javaBinary() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home", ""), "bin", executable).toString();
    }

    private void append(String line) {
        SwingUtilities.invokeLater(() -> {
            output.append(line + System.lineSeparator());
            output.setCaretPosition(output.getDocument().getLength());
        });
    }
}
