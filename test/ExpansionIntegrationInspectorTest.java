import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpansionIntegrationInspectorTest {
    @Test
    void inspectorReportsActiveSystemsObservedEventsAndSeedOnlyCandidates() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 55L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.economyExpansion.logistics.crewFatigue = 7;
        DiplomacyNarrativeCrewSystem.changeReputation(ctx.campaign.diplomacyNarrative,
                DiplomacyNarrativeCrewSystem.ReputationGroup.CIVILIAN, 1, "Test hub service");

        List<String> lines = ExpansionIntegrationInspector.lines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.contains("active 8/8")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("ledger burden 7")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("reputation reasons 1")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Seed-only candidates")));
    }
}
