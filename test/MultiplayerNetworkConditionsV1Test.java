import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerNetworkConditionsV1Test {

    @Test
    void simulatesLatencyJitterLossDuplicationReorderingMalformedAndBurstTraffic() {
        MultiplayerNetworkConditionsV1 conditions = new MultiplayerNetworkConditionsV1(
                new MultiplayerNetworkConditionsV1.ConditionProfile(
                        3, 1, 5, 3, 2, 2, 4));

        MultiplayerLoopbackTransport.Message first = message(1L);
        MultiplayerLoopbackTransport.Message second = message(2L);
        MultiplayerLoopbackTransport.Message third = message(3L);
        MultiplayerLoopbackTransport.Message fourth = message(4L);
        MultiplayerLoopbackTransport.Message fifth = message(5L);

        List<MultiplayerNetworkConditionsV1.Delivery> firstPlan = conditions.send(first, 10L);
        List<MultiplayerNetworkConditionsV1.Delivery> secondPlan = conditions.send(second, 10L);
        List<MultiplayerNetworkConditionsV1.Delivery> thirdPlan = conditions.send(third, 10L);
        List<MultiplayerNetworkConditionsV1.Delivery> fourthPlan = conditions.send(fourth, 10L);
        List<MultiplayerNetworkConditionsV1.Delivery> fifthPlan = conditions.send(fifth, 10L);

        assertEquals(13L, firstPlan.get(0).scheduledTick());
        assertEquals(14L, secondPlan.get(0).scheduledTick());
        assertEquals(12L, thirdPlan.get(0).scheduledTick());
        assertEquals(2, thirdPlan.size(), "third message should be duplicated");
        assertTrue(thirdPlan.get(1).duplicate());
        assertTrue(fourthPlan.get(0).malformed());
        assertTrue(fifthPlan.get(0).dropped());

        List<MultiplayerNetworkConditionsV1.Delivery> firstBurst = conditions.drainDue(20L);
        List<MultiplayerNetworkConditionsV1.Delivery> secondBurst = conditions.drainDue(20L);
        List<MultiplayerNetworkConditionsV1.Delivery> thirdBurst = conditions.drainDue(20L);

        assertEquals(2, firstBurst.size(), "burst limit should cap delivery");
        assertEquals(2, secondBurst.size(), "second burst should continue releasing queued messages");
        assertEquals(1, thirdBurst.size(), "third burst should release the final queued message");
        assertTrue(firstBurst.stream().anyMatch(MultiplayerNetworkConditionsV1.Delivery::malformed)
                || secondBurst.stream().anyMatch(MultiplayerNetworkConditionsV1.Delivery::malformed)
                || thirdBurst.stream().anyMatch(MultiplayerNetworkConditionsV1.Delivery::malformed));
        assertTrue(firstBurst.stream().anyMatch(MultiplayerNetworkConditionsV1.Delivery::duplicate)
                || secondBurst.stream().anyMatch(MultiplayerNetworkConditionsV1.Delivery::duplicate)
                || thirdBurst.stream().anyMatch(MultiplayerNetworkConditionsV1.Delivery::duplicate));
        assertEquals(0, conditions.queuedCount());
    }

    @Test
    void clientFreezeAndRecoveryHoldThenReleasePackets() {
        MultiplayerNetworkConditionsV1 conditions = new MultiplayerNetworkConditionsV1(
                MultiplayerNetworkConditionsV1.ConditionProfile.cleanLan());
        conditions.send(message(1L), 1L);
        conditions.freezeClient(2L);

        assertTrue(conditions.drainDue(20L).isEmpty());
        assertEquals(18L, conditions.recoverClient(20L));

        List<MultiplayerNetworkConditionsV1.Delivery> delivered = conditions.drainDue(20L);

        assertEquals(1, delivered.size());
        assertFalse(delivered.get(0).dropped());
    }

    @Test
    void hostFrameRateDegradationUsesFixedClockCatchUpClamp() {
        MultiplayerNetworkConditionsV1 conditions = new MultiplayerNetworkConditionsV1(
                MultiplayerNetworkConditionsV1.ConditionProfile.cleanLan());
        MultiplayerFixedStepClock clock = new MultiplayerFixedStepClock(60, 5);

        MultiplayerFixedStepClock.StepPlan plan =
                conditions.simulateHostFrameRateDegradation(clock, 1.0);

        assertEquals(5, plan.ticksToRun());
        assertTrue(plan.catchUpClamped());
    }

    @Test
    void phaseTwelveBudgetsAreExplicit() {
        assertEquals(80, MultiplayerNetworkConditionsV1.LAN_LATENCY_TARGET_MS);
        assertEquals(30, MultiplayerNetworkConditionsV1.ACCEPTABLE_SNAPSHOT_AGE_TICKS);
        assertEquals(MultiplayerProtocolV1.SNAPSHOT_RATE_HZ,
                MultiplayerNetworkConditionsV1.TARGET_SNAPSHOTS_PER_SECOND);
        assertEquals(MultiplayerReplicationV1.MAX_REPLICATED_SHIPS_V1,
                MultiplayerNetworkConditionsV1.MAX_V1_SUPPORTED_ENTITIES);
        assertEquals(MultiplayerReplicationV1.MAX_REPLICATED_PROJECTILES_V1,
                MultiplayerNetworkConditionsV1.MAX_V1_REPLICATED_PROJECTILES);
        assertTrue(MultiplayerNetworkConditionsV1.snapshotWithinNormalBudget(
                MultiplayerNetworkConditionsV1.MAX_NORMAL_SNAPSHOT_BYTES));
        assertTrue(MultiplayerNetworkConditionsV1.snapshotWithinPeakBudget(
                MultiplayerNetworkConditionsV1.MAX_PEAK_SNAPSHOT_BYTES));
        assertTrue(MultiplayerNetworkConditionsV1.clientBandwidthWithinBudget(
                MultiplayerNetworkConditionsV1.MAX_BYTES_PER_SECOND_PER_CLIENT));
        assertFalse(MultiplayerNetworkConditionsV1.snapshotWithinNormalBudget(
                MultiplayerNetworkConditionsV1.MAX_NORMAL_SNAPSHOT_BYTES + 1));
        assertFalse(MultiplayerNetworkConditionsV1.snapshotWithinPeakBudget(
                MultiplayerNetworkConditionsV1.MAX_PEAK_SNAPSHOT_BYTES + 1));
        assertFalse(MultiplayerNetworkConditionsV1.clientBandwidthWithinBudget(
                MultiplayerNetworkConditionsV1.MAX_BYTES_PER_SECOND_PER_CLIENT + 1));
    }

    private static MultiplayerLoopbackTransport.Message message(long sequence) {
        return new MultiplayerLoopbackTransport.Message(
                MultiplayerProtocolV1.MessageKind.FULL_SNAPSHOT,
                sequence,
                sequence,
                null,
                null,
                null,
                null,
                "");
    }
}
