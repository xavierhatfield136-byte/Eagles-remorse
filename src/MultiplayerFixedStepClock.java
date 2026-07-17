/**
 * Fixed-step host clock for authoritative multiplayer battle simulation.
 *
 * Rendering may run at any cadence, but the host advances battle logic using this tick budget.
 */
public final class MultiplayerFixedStepClock {
    public record StepPlan(long firstTick, int ticksToRun, double tickSeconds, boolean catchUpClamped) {}

    private final int tickRate;
    private final int maxCatchUpTicksPerFrame;
    private final double tickSeconds;
    private double accumulatorSeconds = 0.0;
    private long nextTick = 0L;

    public MultiplayerFixedStepClock() {
        this(MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE, MultiplayerRulesV1.MAX_CATCH_UP_TICKS_PER_FRAME);
    }

    public MultiplayerFixedStepClock(int tickRate, int maxCatchUpTicksPerFrame) {
        this.tickRate = Math.max(1, tickRate);
        this.maxCatchUpTicksPerFrame = Math.max(1, maxCatchUpTicksPerFrame);
        this.tickSeconds = 1.0 / this.tickRate;
    }

    public StepPlan planFrame(double elapsedSeconds) {
        double safeElapsed = Double.isFinite(elapsedSeconds) ? Math.max(0.0, elapsedSeconds) : 0.0;
        accumulatorSeconds += safeElapsed;

        int availableTicks = (int) Math.floor(accumulatorSeconds / tickSeconds);
        int ticksToRun = Math.min(availableTicks, maxCatchUpTicksPerFrame);
        boolean clamped = availableTicks > maxCatchUpTicksPerFrame;
        if (clamped) {
            accumulatorSeconds = 0.0;
        } else {
            accumulatorSeconds -= ticksToRun * tickSeconds;
        }

        long first = nextTick;
        nextTick += ticksToRun;
        return new StepPlan(first, ticksToRun, tickSeconds, clamped);
    }

    public long nextTick() {
        return nextTick;
    }

    public double accumulatorSeconds() {
        return accumulatorSeconds;
    }

    public int tickRate() {
        return tickRate;
    }

    public int maxCatchUpTicksPerFrame() {
        return maxCatchUpTicksPerFrame;
    }
}
