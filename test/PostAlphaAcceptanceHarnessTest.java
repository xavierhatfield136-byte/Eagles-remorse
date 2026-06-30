import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAlphaAcceptanceHarnessTest {
    @Test
    void everyCivilWarOutcomeFamilyIsReachableConsequentialAndPersistent() {
        List<PostAlphaManualAcceptanceHarness.OutcomeEvidence> evidence =
                PostAlphaManualAcceptanceHarness.civilWarOutcomeFamilies();
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.values().length - 1, evidence.size());
        assertTrue(evidence.stream().allMatch(item -> item.expected() == item.actual()
                && item.persisted() && !item.consequence().isBlank()));
    }

    @Test
    void namedRivalSurvivesAndChangesBehaviorAcrossThreeEncounters() {
        PostAlphaManualAcceptanceHarness.RivalEvidence evidence =
                PostAlphaManualAcceptanceHarness.rivalThreeEncounterScenario();
        assertTrue(evidence.survived());
        assertEquals(3, evidence.encounters());
        assertTrue(evidence.adaptationLevel() >= 2);
        assertFalse(evidence.countermeasure().isBlank());
        assertEquals(3, evidence.memories().size());
    }

    @Test
    void everyAlternativeCampaignSupportsNewGameSaveLoadDefeatVictoryAndLongSession() {
        AlternativeCampaignSystem.State catalog = AlternativeCampaignSystem.bootstrap();
        assertEquals(List.of("blue-liberation", "bright-yellow", "dark-yellow"), catalog.definitions.values().stream()
                .filter(definition -> definition.releaseReady).map(definition -> definition.id).toList());
        for (String id : catalog.definitions.keySet()) {
            GameContext ctx = new GameContext(new GameConfig(
                    GameMode.CAMPAIGN_OPS, 5000, 5000, true, 10_200L + id.hashCode(), false));
            SpawnSystem.initWorld(ctx);
            AlternativeCampaignSystem.State state = AlternativeCampaignSystem.bootstrap();
            assertTrue(AlternativeCampaignSystem.start(ctx, state, id));
            for (int tick = 0; tick < 2_000; tick++) AlternativeCampaignSystem.advanceVariant(state);
            AlternativeCampaignSystem.State restored = AlternativeCampaignSystem.restore(
                    AlternativeCampaignSystem.serialize(state));
            assertEquals(id, restored.activeCampaignId);
            assertTrue(restored.started);
            AlternativeCampaignSystem.updateOutcome(restored, 100, false);
            assertTrue(restored.victorious);
            AlternativeCampaignSystem.updateOutcome(restored, 20, true);
            assertTrue(restored.defeated);
            assertFalse(restored.victorious);
        }
    }
}
