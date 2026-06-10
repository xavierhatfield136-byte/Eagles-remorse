import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtAudioPolishQualitySystemTest {

    @Test
    void artAudioPolishLinesCoverVisualEffectsAudioAndMenuPresentation() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 404L, false));
        SpawnSystem.initWorld(ctx);

        List<String> lines = ArtAudioPolishQualitySystem.allArtAudioPolishLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Damage Stage Visuals  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Placeholder Disposition  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Faction Hull Skins  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Turret Role Skins  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Damaged Critical Readability  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Multipart Wrecks  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Engine Plumes / Shield Impacts  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Missile Trail Variants  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Station Module Art  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Environmental Props  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Bridge Portrait Gate  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Layered Audio  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Adaptive Music  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Audio Ducking  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Important Captions  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Regional Ambience  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Title Menu Polish  |  ")));
    }
}
