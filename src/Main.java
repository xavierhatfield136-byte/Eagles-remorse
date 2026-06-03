import app.config.GameConfig;
import app.persistence.CampaignCheckpointStore;
import app.support.ErrorLog;
import app.support.SourceTreeHygiene;
import app.ui.AppShell;
import app.ui.MainMenuPanel;
import javax.swing.*;
import java.awt.*;
import java.util.List;

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
            CampaignCheckpointStore.Checkpoint recovered = CampaignCheckpointStore.recoverLatestAutosave();
            if (recovered == null) {
                return MainMenuPanel.ResumeCampaignState.unavailable(
                        "No checkpoint saved yet. Clear a sector in Campaign Ops to unlock resume."
                                + slotStatusSuffix());
            }
            return MainMenuPanel.ResumeCampaignState.available(
                    "Recovered autosave: " + recovered.menuSummary() + slotStatusSuffix(),
                    recovered.toGameConfig(app.config.GameMode.CAMPAIGN_OPS));
        }
        return MainMenuPanel.ResumeCampaignState.available(
                checkpoint.menuSummary() + slotStatusSuffix(),
                checkpoint.toGameConfig(app.config.GameMode.CAMPAIGN_OPS));
    }

    private String slotStatusSuffix() {
        List<CampaignCheckpointStore.SlotSummary> slots = CampaignCheckpointStore.listSlots();
        long named = slots.stream().filter(s -> !s.autosave && !"primary".equals(s.id)).count();
        long autosaves = slots.stream().filter(s -> s.autosave).count();
        long recovery = slots.stream().filter(s -> !s.recoverable).count();
        if (named <= 0 && autosaves <= 0 && recovery <= 0) return "";
        String text = "<br>Slots " + named + "  |  Autosaves " + autosaves;
        if (recovery > 0) text += "  |  Recovery " + recovery;
        return text;
    }

    private void paintMenuSpaceBackground(Graphics2D g2, double camX, double camY, int width, int height, long seed) {
        Renderer.drawSpaceBackground(g2, camX, camY, width, height, seed);
    }

    public void showWindow() {
        shell.showWindow();
    }

    private static void startAssetWarmup() {
        Thread warmup = new Thread(() -> {
            try {
                ShipHullSilhouette.prewarmCaches();
                HullGeometry.prewarmCaches();
                // Multipart sprite prewarm is extremely allocation-heavy in giant test battles.
                // Keep runtime lazy-loading for these caches instead of competing with live combat.
            } catch (Throwable ignored) {
                // Warmup is opportunistic. Lazy runtime loading remains as a fallback.
            }
        }, "asset-prewarm");
        warmup.setDaemon(true);
        warmup.setPriority(Thread.MIN_PRIORITY);
        warmup.start();
    }

    public static void main(String[] args) {
        SourceTreeHygiene.purgeDefaultSourceTreeArtifacts();
        ErrorLog.installGlobalHandler();
        startAssetWarmup();
        SwingUtilities.invokeLater(() -> new Main().showWindow());
    }
}
