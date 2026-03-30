import app.config.GameConfig;
import app.persistence.CampaignCheckpointStore;
import app.support.ErrorLog;
import app.support.SourceTreeHygiene;
import app.ui.AppShell;
import app.ui.MainMenuPanel;
import javax.swing.*;
import java.awt.*;

public class Main {
    private final AppShell shell;

    public Main() {
        shell = new AppShell(
                this::createGameView,
                this::loadResumeCampaignState,
                this::paintMenuSpaceBackground,
                () -> System.exit(0));
    }

    private AppShell.GameView createGameView(GameConfig config, Runnable showMenu, Runnable toggleFullscreen) {
        GamePanel panel = new GamePanel(config, showMenu, toggleFullscreen);
        return new AppShell.GameView() {
            @Override
            public JComponent component() {
                return panel;
            }

            @Override
            public void shutdown() {
                panel.shutdown();
            }

            @Override
            public void requestFocusInWindow() {
                panel.requestFocusInWindow();
            }
        };
    }

    private MainMenuPanel.ResumeCampaignState loadResumeCampaignState() {
        CampaignCheckpointStore.Checkpoint checkpoint = CampaignCheckpointStore.load();
        if (checkpoint == null) {
            return MainMenuPanel.ResumeCampaignState.unavailable(
                    "No checkpoint saved yet. Clear a sector in Campaign Ops to unlock resume.");
        }
        return MainMenuPanel.ResumeCampaignState.available(checkpoint.menuSummary(), checkpoint.toGameConfig());
    }

    private void paintMenuSpaceBackground(Graphics2D g2, double camX, double camY, int width, int height, long seed) {
        Renderer.drawSpaceBackground(g2, camX, camY, width, height, seed);
    }

    public void showWindow() {
        shell.showWindow();
    }

    public static void main(String[] args) {
        SourceTreeHygiene.purgeDefaultSourceTreeArtifacts();
        ErrorLog.installGlobalHandler();
        SwingUtilities.invokeLater(() -> new Main().showWindow());
    }
}
