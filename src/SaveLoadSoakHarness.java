import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repeated checkpoint save/load soak using an isolated temporary save file.
 */
public final class SaveLoadSoakHarness {
    private SaveLoadSoakHarness() {}

    public static void main(String[] args) throws Exception {
        int cycles = 100;
        for (String arg : args) if (arg != null && arg.startsWith("--cycles=")) cycles = Math.max(1, Integer.parseInt(arg.substring(9)));
        Path dir = Files.createTempDirectory("eagles-remorse-save-soak-");
        Path checkpoint = dir.resolve("campaign_checkpoint.properties");
        System.setProperty("codex.checkpointFile", checkpoint.toString());
        for (int i = 0; i < cycles; i++) {
            CampaignCheckpointStore.Checkpoint out = new CampaignCheckpointStore.Checkpoint();
            out.seed = 9000L + i;
            out.nextSector = 2 + (i % 20);
            out.credits = 1000 + i;
            out.currentGalaxyLocationId = "soak-location-" + i;
            out.galaxyEncounterActive = (i & 1) == 0;
            CampaignCheckpointStore.save(out);
            CampaignCheckpointStore.Checkpoint loaded = CampaignCheckpointStore.load();
            if (loaded == null || loaded.seed != out.seed || loaded.nextSector != out.nextSector
                    || !out.currentGalaxyLocationId.equals(loaded.currentGalaxyLocationId)) {
                throw new IllegalStateException("checkpoint mismatch at cycle " + i);
            }
            loaded.toGameConfig(GameMode.CAMPAIGN_OPS);
        }
        CampaignCheckpointStore.clear();
        Files.deleteIfExists(dir);
        System.out.println("[save-load-soak] cycles=" + cycles + " checks: PASS");
    }
}
