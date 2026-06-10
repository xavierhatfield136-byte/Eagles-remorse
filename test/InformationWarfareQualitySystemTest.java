import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InformationWarfareQualitySystemTest {

    @Test
    void informationWarfareLinesCoverIntelBoardSourcesDeceptionScoutingAndArchives() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 202L, false));
        SpawnSystem.initWorld(ctx);

        List<String> lines = InformationWarfareQualitySystem.allInformationWarfareLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Intelligence Board  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Source Quality  |  ")
                && line.contains("scan") && line.contains("captured manifest") && line.contains("relay")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Decoy Operation  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Enemy Feint  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Scouting Order  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Probe Launch  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Contact Decay  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Officer Interpretation  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Intercepted Chatter  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Dead Zone  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Counterintelligence Sweep  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Sabotage Incident  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Agent Mission  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Propaganda Event  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Intel Archive  |  ")));
    }
}
