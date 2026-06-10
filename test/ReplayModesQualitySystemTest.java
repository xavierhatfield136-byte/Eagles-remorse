import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayModesQualitySystemTest {

    @Test
    void replayModesLinesCoverReplayStatsModesSharingAndUnlocks() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 505L, false));
        SpawnSystem.initWorld(ctx);

        List<String> lines = ReplayModesQualitySystem.allReplayModeLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Deterministic Replay  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Cinematic Replay Camera  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Campaign Event Log  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Scenario Launch  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Challenge Mode  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("New Game Plus  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Skirmish Fleet Builder  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Faction Start  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Branching Chapter  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Historical Scenario  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Shareable Summary  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Async Campaign Sharing  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Spectator Mode  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("After-Action Export  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Balanced Unlocks  |  ")));
    }
}
