import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic network-condition wrapper for loopback/test transports. */
public final class MultiplayerNetworkConditionsV1 {
    public static final int LAN_LATENCY_TARGET_MS = 80;
    public static final int ACCEPTABLE_SNAPSHOT_AGE_TICKS = MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE / 2;
    public static final int TARGET_SNAPSHOTS_PER_SECOND = MultiplayerProtocolV1.SNAPSHOT_RATE_HZ;
    public static final int MAX_NORMAL_SNAPSHOT_BYTES = MultiplayerReplicationV1.TARGET_SNAPSHOT_BYTES;
    public static final int MAX_PEAK_SNAPSHOT_BYTES = MultiplayerReplicationV1.PEAK_SNAPSHOT_BYTES;
    public static final int MAX_BYTES_PER_SECOND_PER_CLIENT =
            MultiplayerReplicationV1.TARGET_BYTES_PER_SECOND_PER_CLIENT;
    public static final int MAX_V1_SUPPORTED_ENTITIES = MultiplayerReplicationV1.MAX_REPLICATED_SHIPS_V1;
    public static final int MAX_V1_REPLICATED_PROJECTILES =
            MultiplayerReplicationV1.MAX_REPLICATED_PROJECTILES_V1;

    private final ConditionProfile profile;
    private final ArrayList<ScheduledMessage> queue = new ArrayList<>();
    private long sentCount;
    private boolean clientFrozen;
    private long freezeStartedTick;

    public MultiplayerNetworkConditionsV1(ConditionProfile profile) {
        this.profile = profile == null ? ConditionProfile.cleanLan() : profile;
    }

    public record ConditionProfile(int baseLatencyTicks,
                                   int jitterTicks,
                                   int dropEveryNth,
                                   int duplicateEveryNth,
                                   int reorderEveryNth,
                                   int burstLimit,
                                   int malformedEveryNth) {
        public ConditionProfile {
            baseLatencyTicks = Math.max(0, baseLatencyTicks);
            jitterTicks = Math.max(0, jitterTicks);
            dropEveryNth = Math.max(0, dropEveryNth);
            duplicateEveryNth = Math.max(0, duplicateEveryNth);
            reorderEveryNth = Math.max(0, reorderEveryNth);
            burstLimit = Math.max(1, burstLimit);
            malformedEveryNth = Math.max(0, malformedEveryNth);
        }

        public static ConditionProfile cleanLan() {
            return new ConditionProfile(1, 0, 0, 0, 0, 64, 0);
        }

        public static ConditionProfile hostileTest() {
            return new ConditionProfile(3, 2, 5, 3, 2, 2, 7);
        }
    }

    public record Delivery(MultiplayerLoopbackTransport.Message message,
                           long scheduledTick,
                           boolean duplicate,
                           boolean malformed,
                           boolean dropped,
                           String note) {
        public Delivery {
            scheduledTick = Math.max(0L, scheduledTick);
            note = note == null ? "" : note.trim();
        }
    }

    private record ScheduledMessage(long order,
                                    long scheduledTick,
                                    MultiplayerLoopbackTransport.Message message,
                                    boolean duplicate,
                                    boolean malformed) {}

    public List<Delivery> send(MultiplayerLoopbackTransport.Message message, long currentTick) {
        sentCount++;
        if (message == null) {
            return List.of(new Delivery(null, Math.max(0L, currentTick),
                    false, true, true, "missing message"));
        }
        if (shouldApply(profile.dropEveryNth(), sentCount)) {
            return List.of(new Delivery(message, Math.max(0L, currentTick),
                    false, false, true, "simulated packet loss"));
        }

        long due = Math.max(0L, currentTick) + profile.baseLatencyTicks() + deterministicJitter(sentCount);
        boolean malformed = shouldApply(profile.malformedEveryNth(), sentCount);
        boolean reorder = shouldApply(profile.reorderEveryNth(), sentCount);
        long order = reorder ? -sentCount : sentCount;
        queue.add(new ScheduledMessage(order, due, message, false, malformed));

        ArrayList<Delivery> out = new ArrayList<>();
        out.add(new Delivery(message, due, false, malformed, false,
                malformed ? "simulated malformed message" : ""));
        if (shouldApply(profile.duplicateEveryNth(), sentCount)) {
            queue.add(new ScheduledMessage(order + 1_000_000L, due + 1L, message, true, malformed));
            out.add(new Delivery(message, due + 1L, true, malformed, false, "simulated duplicate"));
        }
        return out;
    }

    public List<Delivery> drainDue(long currentTick) {
        if (clientFrozen) return List.of();
        long safeTick = Math.max(0L, currentTick);
        queue.sort(Comparator
                .comparingLong(ScheduledMessage::scheduledTick)
                .thenComparingLong(ScheduledMessage::order));
        ArrayList<Delivery> out = new ArrayList<>();
        int delivered = 0;
        for (int i = 0; i < queue.size() && delivered < profile.burstLimit(); ) {
            ScheduledMessage scheduled = queue.get(i);
            if (scheduled.scheduledTick() > safeTick) {
                i++;
                continue;
            }
            queue.remove(i);
            out.add(new Delivery(scheduled.message(), scheduled.scheduledTick(),
                    scheduled.duplicate(), scheduled.malformed(),
                    false, scheduled.duplicate() ? "duplicate" : ""));
            delivered++;
        }
        return out;
    }

    public void freezeClient(long currentTick) {
        clientFrozen = true;
        freezeStartedTick = Math.max(0L, currentTick);
    }

    public long recoverClient(long currentTick) {
        if (!clientFrozen) return 0L;
        clientFrozen = false;
        return Math.max(0L, currentTick) - freezeStartedTick;
    }

    public int queuedCount() {
        return queue.size();
    }

    public MultiplayerFixedStepClock.StepPlan simulateHostFrameRateDegradation(
            MultiplayerFixedStepClock clock,
            double elapsedSeconds) {
        MultiplayerFixedStepClock safeClock = clock == null ? new MultiplayerFixedStepClock() : clock;
        return safeClock.planFrame(elapsedSeconds);
    }

    public static boolean snapshotWithinNormalBudget(int bytes) {
        return bytes >= 0 && bytes <= MAX_NORMAL_SNAPSHOT_BYTES;
    }

    public static boolean snapshotWithinPeakBudget(int bytes) {
        return bytes >= 0 && bytes <= MAX_PEAK_SNAPSHOT_BYTES;
    }

    public static boolean clientBandwidthWithinBudget(int bytesPerSecond) {
        return bytesPerSecond >= 0 && bytesPerSecond <= MAX_BYTES_PER_SECOND_PER_CLIENT;
    }

    private long deterministicJitter(long count) {
        int jitter = profile.jitterTicks();
        if (jitter <= 0) return 0L;
        long span = jitter * 2L + 1L;
        return (count % span) - jitter;
    }

    private static boolean shouldApply(int everyNth, long count) {
        return everyNth > 0 && count > 0 && count % everyNth == 0;
    }
}
