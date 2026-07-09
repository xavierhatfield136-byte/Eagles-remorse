import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionOperationLifecycleMilestoneSixTest {

    @Test
    void musterMathIsZeroSafeAndUsesExplicitThresholds() {
        assertEquals(0.0, FactionAttackCommitmentSystem.musterRatio(0, 0), 1e-9);
        FactionAttackCommitmentSystem.State state = new FactionAttackCommitmentSystem.State();
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.ENEMY, "red-base", "green-site", 7, 0.0, 300.0),
                Faction.TEAM_C.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(result.accepted());
        FactionAttackCommitmentSystem.configureSortieRequirements(result.commitment(), 2, 0.60, 0.50,
                3, 120.0);

        FactionAttackCommitmentSystem.updateDerivedProgress(result.commitment(), 1, 70.0, 0.0);
        assertFalse(FactionAttackCommitmentSystem.readyToSortie(result.commitment()));
        FactionAttackCommitmentSystem.updateDerivedProgress(result.commitment(), 2, 70.0, 0.0);
        assertTrue(FactionAttackCommitmentSystem.readyToSortie(result.commitment()));
        assertEquals(2.0 / 3.0, result.commitment().musterProgress, 1e-9);
    }

    @Test
    void lifecycleTransitionsRejectIllegalPhaseJumps() {
        FactionAttackCommitmentSystem.State state = new FactionAttackCommitmentSystem.State();
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.TEAM_C, "green-base", "red-site", 9, 0.0, 300.0),
                Faction.ENEMY.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(result.accepted());
        assertFalse(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.RESOLVING));
        assertTrue(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.STAGING));
        assertTrue(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.READY_TO_SORTIE));
        assertTrue(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.EN_ROUTE));
        assertTrue(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.ASSAULTING));
        assertTrue(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.RESOLVING));
    }

    @Test
    void lifecycleProgressSurvivesSerialization() {
        FactionAttackCommitmentSystem.State state = new FactionAttackCommitmentSystem.State();
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.BRIGHT_YELLOW, "yellow-base", "red-site", 11, 25.0, 400.0),
                Faction.ENEMY.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        FactionAttackCommitmentSystem.configureSortieRequirements(result.commitment(), 2, 0.65, 0.55,
                4, 180.0);
        FactionAttackCommitmentSystem.updateDerivedProgress(result.commitment(), 3, 120.0, 0.42);
        assertTrue(FactionAttackCommitmentSystem.setPhase(state, result.operationId(),
                FactionAttackCommitmentSystem.Phase.STAGING));

        FactionAttackCommitmentSystem.State restored =
                FactionAttackCommitmentSystem.restore(FactionAttackCommitmentSystem.serialize(state));
        FactionAttackCommitmentSystem.Commitment commitment =
                FactionAttackCommitmentSystem.active(restored, FactionAttackCommitmentSystem.Slot.YELLOW);
        assertEquals(2, commitment.minimumFleetCount);
        assertEquals(0.65, commitment.minimumMusterRatio, 1e-9);
        assertEquals(0.55, commitment.minimumStrengthRatio, 1e-9);
        assertEquals(0.75, commitment.musterProgress, 1e-9);
        assertEquals(0.42, commitment.travelProgress, 1e-9);
    }
}
