import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternativeCampaignSystemTest {
    @Test
    void campaignDefinitionsAreViableAndReuseSharedState() {
        AlternativeCampaignSystem.State state = AlternativeCampaignSystem.bootstrap();
        assertTrue(state.definitions.size() >= 8);
        assertTrue(state.definitions.values().stream().allMatch(definition -> definition.startingFuel > 0
                && definition.startingSupplies > 0 && definition.startingAmmo > 0
                && !definition.objective.isBlank() && !definition.victoryCondition.isBlank()));
        assertTrue(AlternativeCampaignSystem.validateDefinitions(state).isEmpty());
        assertFalse(state.definitions.get("bright-yellow").commandFaction
                .isFriendlyTo(state.definitions.get("dark-yellow").commandFaction));
    }

    @Test
    void brightYellowPerspectiveStartsAndPersistsWithoutForkingSimulation() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        SpawnSystem.initWorld(ctx);
        AlternativeCampaignSystem.State state = AlternativeCampaignSystem.bootstrap();

        assertTrue(AlternativeCampaignSystem.start(ctx, state, "bright-yellow"));
        assertEquals("YELLOW", ctx.campaign.branchRoute);
        assertEquals(state.definitions.get("bright-yellow").startingFuel, ctx.campaign.campaignFuel);
        assertTrue(AlternativeCampaignSystem.summaryLines(state).get(0).contains("Bright Yellow"));

        AlternativeCampaignSystem.State restored =
                AlternativeCampaignSystem.restore(AlternativeCampaignSystem.serialize(state));
        assertEquals("bright-yellow", restored.activeCampaignId);
        assertTrue(restored.started);
        assertEquals(state.deterministicSeed, restored.deterministicSeed);
        assertEquals(state.saveSlotId, restored.saveSlotId);
    }

    @Test
    void definitionsExposeDistinctRulesAndOutcomeFlowsWithoutForkingTheSimulation() {
        AlternativeCampaignSystem.State state = AlternativeCampaignSystem.bootstrap();
        assertTrue(state.definitions.get("red-offensive").mechanics.contains("Red command pressure"));
        assertTrue(state.definitions.get("bright-yellow").mechanics.contains("coalition obligation"));
        assertTrue(state.definitions.get("dark-yellow").mechanics.contains("Red dependency"));
        assertTrue(state.definitions.get("civilian-convoy").mechanics.contains("no conquest objective"));
        assertTrue(state.definitions.get("carrier-task-force").mechanics.contains("automated routine launches"));
        assertTrue(state.definitions.get("scavenger").mechanics.contains("war-history opportunities"));

        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 101L, false));
        SpawnSystem.initWorld(ctx);
        assertTrue(AlternativeCampaignSystem.start(ctx, state, "last-stand"));
        AlternativeCampaignSystem.scoreLastStand(state, 100, 100, 100, 100, 100);
        assertTrue(state.victorious);
        AlternativeCampaignSystem.State restored = AlternativeCampaignSystem.restore(
                AlternativeCampaignSystem.serialize(state));
        assertTrue(restored.victorious);
        assertEquals(100, restored.evacuationScore);

        AlternativeCampaignSystem.updateOutcome(restored, 20, true);
        assertTrue(restored.defeated);
        assertFalse(restored.victorious);
    }

    @Test
    void variantMechanicsUseSharedSystemsAndPersistWithoutLeakingBetweenStarts() {
        for (String id : AlternativeCampaignSystem.bootstrap().definitions.keySet()) {
            GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 202L, false));
            SpawnSystem.initWorld(ctx);
            AlternativeCampaignSystem.State state = AlternativeCampaignSystem.bootstrap();
            assertTrue(AlternativeCampaignSystem.start(ctx, state, id));
            assertEquals(id, state.activeCampaignId);
            assertTrue(state.objectiveProgress == 0 && !state.defeated && !state.victorious);
        }

        AlternativeCampaignSystem.State carrier = AlternativeCampaignSystem.bootstrap();
        carrier.activeCampaignId = "carrier-task-force";
        assertTrue(AlternativeCampaignSystem.planCarrierSortie(carrier, 4, true, true));
        assertFalse(AlternativeCampaignSystem.planCarrierSortie(carrier, 9, false, false));

        AlternativeCampaignSystem.State convoy = AlternativeCampaignSystem.bootstrap();
        convoy.activeCampaignId = "civilian-convoy";
        assertTrue(AlternativeCampaignSystem.deliverCivilianConvoy(convoy, 500, 60, true, true, true));
        assertTrue(convoy.civiliansDelivered > 0);

        AlternativeCampaignSystem.State lastStand = AlternativeCampaignSystem.bootstrap();
        lastStand.activeCampaignId = "last-stand";
        assertTrue(AlternativeCampaignSystem.requestLastStandReinforcement(lastStand));
        assertTrue(AlternativeCampaignSystem.requestLastStandReinforcement(lastStand));
        assertTrue(AlternativeCampaignSystem.requestLastStandReinforcement(lastStand));
        assertFalse(AlternativeCampaignSystem.requestLastStandReinforcement(lastStand));

        AlternativeCampaignSystem.State restored = AlternativeCampaignSystem.restore(
                AlternativeCampaignSystem.serialize(convoy));
        assertEquals(convoy.civiliansDelivered, restored.civiliansDelivered);
    }
}
