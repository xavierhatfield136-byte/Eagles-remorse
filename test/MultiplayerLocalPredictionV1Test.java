import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLocalPredictionV1Test {

    @Test
    void predictionStaysDisabledUntilStableMeasuredNeedExists() {
        assertFalse(MultiplayerLocalPredictionV1.hostConfirmedMovementIsRequiredBeforeEnabling(
                true, true, false));
        assertFalse(MultiplayerLocalPredictionV1.hostConfirmedMovementIsRequiredBeforeEnabling(
                true, false, true));
        assertTrue(MultiplayerLocalPredictionV1.hostConfirmedMovementIsRequiredBeforeEnabling(
                true, true, true));
    }

    @Test
    void disabledPredictionDoesNotApplyOrRetainInputs() {
        MultiplayerLocalPredictionV1 prediction = new MultiplayerLocalPredictionV1(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                202,
                new MultiplayerLocalPredictionV1.PredictedShipState(202, 10.0, 20.0, 0.0),
                false);

        prediction.applyLocalInputImmediately(frame(202, 1L, 1.0f), 1.0, 100.0);

        assertEquals(10.0, prediction.state().x(), 1e-9);
        assertEquals(0, prediction.unacknowledgedCount());
    }

    @Test
    void predictsLocalPlayerMovementOnlyAndRetainsUnacknowledgedInputs() {
        MultiplayerLocalPredictionV1 prediction = new MultiplayerLocalPredictionV1(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                202,
                new MultiplayerLocalPredictionV1.PredictedShipState(202, 10.0, 20.0, 0.0),
                true);

        prediction.applyLocalInputImmediately(frame(202, 1L, 1.0f), 1.0, 100.0);
        prediction.applyLocalInputImmediately(new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L, 1L,
                1.0f, 0.0f, 0.0, false, false), 1.0, 100.0);

        assertEquals(110.0, prediction.state().x(), 1e-9);
        assertEquals(20.0, prediction.state().y(), 1e-9);
        assertEquals(1, prediction.unacknowledgedCount());
    }

    @Test
    void authoritativeStateAcksInputsThenReplaysRemainingInputs() {
        MultiplayerLocalPredictionV1 prediction = new MultiplayerLocalPredictionV1(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                202,
                new MultiplayerLocalPredictionV1.PredictedShipState(202, 0.0, 0.0, 0.0),
                true);
        prediction.applyLocalInputImmediately(frame(202, 1L, 1.0f), 1.0, 10.0);
        prediction.applyLocalInputImmediately(frame(202, 2L, 1.0f), 1.0, 10.0);
        prediction.applyLocalInputImmediately(frame(202, 3L, 1.0f), 1.0, 10.0);

        prediction.receiveAuthoritativeState(
                new MultiplayerLocalPredictionV1.PredictedShipState(202, 11.0, 0.0, 0.0),
                2L,
                1.0,
                10.0);

        assertEquals(21.0, prediction.state().x(), 1e-9);
        assertEquals(1, prediction.unacknowledgedCount());
        assertEquals(19.0, prediction.debug().correctionMagnitude(), 1e-9);
        assertEquals(1, prediction.debug().replayCount());
    }

    @Test
    void weaponHitsAndAiRemainHostAuthoritative() {
        assertFalse(MultiplayerLocalPredictionV1.predictsWeaponHits());
        assertFalse(MultiplayerLocalPredictionV1.predictsAi());
    }

    private static MultiplayerCommandGate.PlayerInputFrame frame(int shipId, long sequence, float thrust) {
        return new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                shipId,
                sequence,
                sequence,
                thrust,
                0.0f,
                0.0,
                true,
                false);
    }
}
