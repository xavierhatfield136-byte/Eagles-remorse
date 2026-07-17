import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Optional local-player-only movement prediction. Weapon hits and AI remain host-authoritative. */
public final class MultiplayerLocalPredictionV1 {
    private final int localSlotId;
    private final int localShipId;
    private final boolean enabled;
    private final ArrayList<MultiplayerCommandGate.PlayerInputFrame> unacknowledgedInputs = new ArrayList<>();
    private PredictedShipState state;
    private DebugState debug = new DebugState(0.0, 0);

    public MultiplayerLocalPredictionV1(int localSlotId, int localShipId,
                                        PredictedShipState initialState,
                                        boolean enabled) {
        this.localSlotId = Math.max(0, localSlotId);
        this.localShipId = Math.max(0, localShipId);
        this.state = initialState == null ? new PredictedShipState(localShipId, 0.0, 0.0, 0.0) : initialState;
        this.enabled = enabled;
    }

    public record PredictedShipState(int shipId, double x, double y, double angle) {
        public PredictedShipState {
            shipId = Math.max(0, shipId);
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            angle = finiteOrZero(angle);
        }
    }

    public record DebugState(double correctionMagnitude, int replayCount) {
        public DebugState {
            correctionMagnitude = Math.max(0.0, finiteOrZero(correctionMagnitude));
            replayCount = Math.max(0, replayCount);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public PredictedShipState state() {
        return state;
    }

    public DebugState debug() {
        return debug;
    }

    public int unacknowledgedCount() {
        return unacknowledgedInputs.size();
    }

    public boolean canPredict(MultiplayerCommandGate.PlayerInputFrame frame) {
        return enabled
                && frame != null
                && frame.slotId() == localSlotId
                && frame.controlledShipId() == localShipId;
    }

    public PredictedShipState applyLocalInputImmediately(MultiplayerCommandGate.PlayerInputFrame frame,
                                                        double dt,
                                                        double speed) {
        if (!canPredict(frame)) return state;
        unacknowledgedInputs.add(frame);
        state = apply(state, frame, dt, speed);
        return state;
    }

    public PredictedShipState receiveAuthoritativeState(PredictedShipState authoritative,
                                                        long lastProcessedInputSequence,
                                                        double dt,
                                                        double speed) {
        PredictedShipState host = authoritative == null ? state : authoritative;
        double correction = Math.hypot(state.x() - host.x(), state.y() - host.y());
        state = host;
        Iterator<MultiplayerCommandGate.PlayerInputFrame> it = unacknowledgedInputs.iterator();
        while (it.hasNext()) {
            MultiplayerCommandGate.PlayerInputFrame frame = it.next();
            if (frame.sequence() <= lastProcessedInputSequence) {
                it.remove();
            }
        }
        int replayed = 0;
        for (MultiplayerCommandGate.PlayerInputFrame frame : unacknowledgedInputs) {
            state = apply(state, frame, dt, speed);
            replayed++;
        }
        debug = new DebugState(correction, replayed);
        return state;
    }

    public List<MultiplayerCommandGate.PlayerInputFrame> unacknowledgedInputs() {
        return List.copyOf(unacknowledgedInputs);
    }

    public static boolean hostConfirmedMovementIsRequiredBeforeEnabling(boolean loopbackStable,
                                                                       boolean interpolationStable,
                                                                       boolean measuredNeed) {
        return loopbackStable && interpolationStable && measuredNeed;
    }

    public static boolean predictsWeaponHits() {
        return false;
    }

    public static boolean predictsAi() {
        return false;
    }

    private static PredictedShipState apply(PredictedShipState base,
                                            MultiplayerCommandGate.PlayerInputFrame frame,
                                            double dt,
                                            double speed) {
        double safeDt = Double.isFinite(dt) ? Math.max(0.0, dt) : 0.0;
        double safeSpeed = Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
        double nextAngle = base.angle() + frame.turn() * Math.PI * 0.95 * safeDt;
        double dx = Math.cos(nextAngle) * frame.thrust() * safeSpeed * safeDt;
        double dy = Math.sin(nextAngle) * frame.thrust() * safeSpeed * safeDt;
        return new PredictedShipState(base.shipId(), base.x() + dx, base.y() + dy, nextAngle);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
