import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyStoryQualitySystemTest {

    @Test
    void diplomacyStoryQualityLinesCoverReputationMemoryConsequencesAndPresentation() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        SpawnSystem.initWorld(ctx);
        ctx.campaign.greenContractFavor = 2;
        ctx.campaign.yellowLiberationFavor = 3;

        List<String> lines = DiplomacyStoryQualitySystem.allStoryQualityLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Reputation Reason  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Favors / Obligations  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Negotiation Scene  |  ")
                && line.contains("trade") && line.contains("prisoners") && line.contains("salvage")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Recurring Captain  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Rival Commander  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Rescue Return  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Faction Bulletin  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Crew Commentary  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Bridge Disagreement  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Officer State  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Captain Log  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Memorial Entry  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Prisoner Choice  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Civilian Collateral  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Temporary Alliance  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Betrayal Risk  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Ending Slide  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Narrative Pool  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Quiet Mode  |  ")));
    }
}
