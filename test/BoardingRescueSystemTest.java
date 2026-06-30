import org.junit.jupiter.api.Test;
import app.config.GameConfig;
import app.config.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardingRescueSystemTest {
    @Test
    void boardingAndRescueUseTeamsIntelTimeHazardsAndPartialOutcomes() {
        BoardingRescueSystem.State state = BoardingRescueSystem.bootstrap();
        BoardingRescueSystem.Operation rescue = BoardingRescueSystem.start(state,
                BoardingRescueSystem.Objective.RECOVER_SURVIVORS, "wreck-7", 80, 30, "dc-1", "dc-2");
        for (int i = 0; i < 20 && rescue.status == BoardingRescueSystem.Status.ACTIVE; i++) {
            BoardingRescueSystem.update(state, rescue, 1.0, 0.2, false);
        }
        assertEquals(BoardingRescueSystem.Status.SUCCEEDED, rescue.status);
        assertTrue(rescue.survivorsRecovered > 0);

        BoardingRescueSystem.Operation boarding = BoardingRescueSystem.start(state,
                BoardingRescueSystem.Objective.CAPTURE_BRIDGE, "enemy-4", 10, 2, "dc-3");
        BoardingRescueSystem.update(state, boarding, 2.0, 1.8, true);
        assertTrue(boarding.status == BoardingRescueSystem.Status.FAILED
                || boarding.status == BoardingRescueSystem.Status.PARTIAL_SUCCESS);
    }

    @Test
    void operationConsequencesRoundTrip() {
        BoardingRescueSystem.State state = BoardingRescueSystem.bootstrap();
        BoardingRescueSystem.Operation operation = BoardingRescueSystem.start(state,
                BoardingRescueSystem.Objective.RECOVER_SURVIVORS, "station-2", 60, 12, "dc-1");
        BoardingRescueSystem.update(state, operation, 3.0, 0.4, false);
        BoardingRescueSystem.abort(operation);

        BoardingRescueSystem.State restored = BoardingRescueSystem.restore(BoardingRescueSystem.serialize(state));
        assertEquals(1, restored.operations.size());
        assertEquals(operation.status, restored.operations.get(0).status);
        assertEquals(operation.targetId, restored.operations.get(0).targetId);
        assertTrue(BoardingRescueSystem.consequence(restored.operations.get(0)).contains("casualties"));
    }

    @Test
    void eligibilityPhasesHiddenResistanceHazardsCapacityAndConsequencesAreBounded() {
        BoardingRescueSystem.State state = BoardingRescueSystem.bootstrap();
        assertNull(BoardingRescueSystem.startEligible(state, BoardingRescueSystem.Objective.CAPTURE_BRIDGE,
                "neutral-ship", BoardingRescueSystem.TargetType.SHIP, 50, 30, false, 10, 10, "marine-1"));
        assertNull(BoardingRescueSystem.startEligible(state, BoardingRescueSystem.Objective.CAPTURE_BRIDGE,
                "wreck", BoardingRescueSystem.TargetType.WRECK, 50, 30, true, 10, 10, "marine-1"));

        BoardingRescueSystem.Operation operation = BoardingRescueSystem.startEligible(state,
                BoardingRescueSystem.Objective.RECOVER_INTELLIGENCE, "enemy-ship",
                BoardingRescueSystem.TargetType.SHIP, 35, 90, true, 10, 8, "marine-1", "marine-2");
        operation.hiddenResistance = 80;
        operation.securitySystems = 70;
        operation.marineReadiness = 65;
        operation.hostileCounterBoarding = true;
        BoardingRescueSystem.setHazards(operation, 0.6, 0.7, 0.8, 0.4);
        BoardingRescueSystem.update(state, operation, 4.0, 0.5, true);
        assertTrue(operation.phase.ordinal() >= BoardingRescueSystem.Phase.APPROACH.ordinal());
        assertFalse(operation.recommendedPlan.isBlank());
        assertTrue(operation.estimatedResistance != operation.hiddenResistance,
                "low-quality intelligence must remain separate from authoritative resistance");

        BoardingRescueSystem.State restored = BoardingRescueSystem.restore(BoardingRescueSystem.serialize(state));
        BoardingRescueSystem.Operation restoredOperation = restored.operations.get(0);
        assertEquals(80, restoredOperation.hiddenResistance);
        assertTrue(restoredOperation.hostileCounterBoarding);
        assertEquals(0.8, restoredOperation.radiation, 0.0001);
        assertEquals(operation.phase, restoredOperation.phase);
    }

    @Test
    void surrenderScuttleAndHumanePrisonerTransferProducePersistentFollowups() {
        BoardingRescueSystem.State state = BoardingRescueSystem.bootstrap();
        state.prisonersHeld = 8;
        state.treatmentPolicy = BoardingRescueSystem.TreatmentPolicy.HUMANE;
        assertTrue(BoardingRescueSystem.transferPrisoners(state, 3));
        assertEquals(5, state.prisonersHeld);
        assertEquals(3, state.reputationDelta);
        state.prisonersHeld = 10;
        assertTrue(BoardingRescueSystem.questionPrisoners(state, 4) > 0);
        assertTrue(BoardingRescueSystem.exchangePrisoners(state, 3, 2));
        BoardingRescueSystem.recordConductConsequences(state, 4, 3, true);
        assertTrue(state.reputationDelta < 0);
        assertTrue(state.unresolvedConsequences.stream().anyMatch(line -> line.contains("Surrender")));

        BoardingRescueSystem.Operation surrendered = BoardingRescueSystem.start(state,
                BoardingRescueSystem.Objective.SABOTAGE, "station-9", 60, 30, "marine-1");
        assertTrue(BoardingRescueSystem.surrender(surrendered));
        assertEquals(BoardingRescueSystem.Status.SURRENDERED, surrendered.status);

        BoardingRescueSystem.Operation scuttled = BoardingRescueSystem.start(state,
                BoardingRescueSystem.Objective.CAPTURE_BRIDGE, "ship-10", 60, 30, "marine-2");
        assertTrue(BoardingRescueSystem.scuttle(scuttled));
        assertEquals(BoardingRescueSystem.Status.CATASTROPHIC_LOSS, scuttled.status);
        assertFalse(scuttled.followUpOperation.isBlank());

        BoardingRescueSystem.State restored = BoardingRescueSystem.restore(BoardingRescueSystem.serialize(state));
        assertEquals(7, restored.prisonersHeld);
        assertEquals(state.reputationDelta, restored.reputationDelta);
        assertEquals(BoardingRescueSystem.Status.CATASTROPHIC_LOSS, restored.operations.get(1).status);
    }

    @Test
    void completedConsequencesReturnToStrategicCampaignExactlyOnce() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 600L, false));
        SpawnSystem.initWorld(ctx);
        BoardingRescueSystem.Operation rescue = BoardingRescueSystem.start(ctx.campaign.boardingRescue,
                BoardingRescueSystem.Objective.RECOVER_SURVIVORS, "wreck-600", 90, 60,
                "rescue-1", "rescue-2", "rescue-3");
        for (int i = 0; i < 60 && rescue.status == BoardingRescueSystem.Status.ACTIVE; i++) {
            BoardingRescueSystem.update(ctx.campaign.boardingRescue, rescue, 1.0, 0.0, false);
        }
        assertEquals(BoardingRescueSystem.Status.SUCCEEDED, rescue.status);
        assertTrue(CampaignSystem.applyBoardingRescueConsequences(ctx, rescue));
        assertFalse(CampaignSystem.applyBoardingRescueConsequences(ctx, rescue));
        assertTrue(ctx.campaign.strategicExpansion.warEvents.stream()
                .anyMatch(event -> event.id.equals("boarding-" + rescue.id)));
        assertTrue(ctx.campaign.warMemory.survivors.stream()
                .anyMatch(record -> record.sourceId().equals("wreck-600")));
    }
}
