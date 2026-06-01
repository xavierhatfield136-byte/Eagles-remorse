import app.state.AssetLoadGuard;
import app.state.PerfTelemetry;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Runtime performance budgets and adaptive visual-quality policy.
 */
public final class PerformanceGuardrails {
    public enum VisualQuality {
        HIGH,
        MEDIUM,
        LOW,
        EMERGENCY
    }

    private static final double FRAME_BUDGET_MS = 16.67;
    private static final long SPRITE_MEMORY_BUDGET_BYTES = 384L * 1024L * 1024L;
    private static VisualQuality quality = VisualQuality.HIGH;
    private static int overBudgetFrames = 0;
    private static int recoveryFrames = 0;
    private static long lastGcCount = gcCount();
    private static long lastGcMs = gcMs();

    private PerformanceGuardrails() {}

    public static void update(PerfTelemetry perf) {
        if (perf == null) return;
        double frame = Math.max(perf.frameMs, perf.renderMs);
        if (frame > FRAME_BUDGET_MS * 1.20) {
            overBudgetFrames++;
            recoveryFrames = 0;
        } else if (frame < FRAME_BUDGET_MS * 0.82) {
            recoveryFrames++;
            overBudgetFrames = Math.max(0, overBudgetFrames - 1);
        }
        if (overBudgetFrames >= 90) {
            quality = stepDown(quality);
            overBudgetFrames = 0;
        } else if (recoveryFrames >= 360) {
            quality = stepUp(quality);
            recoveryFrames = 0;
        }

        long gcCount = gcCount();
        long gcMs = gcMs();
        perf.gcCollections = (int) Math.max(0L, gcCount - lastGcCount);
        perf.gcMs = Math.max(0L, gcMs - lastGcMs);
        lastGcCount = gcCount;
        lastGcMs = gcMs;
        perf.assetDecodeCount = AssetLoadGuard.decodeCount();
        perf.assetDecodeDuringFrameCount = AssetLoadGuard.decodeDuringFrameCount();
        perf.gameplayDiskLoadCount = AssetLoadGuard.gameplayDiskLoadCount();
        perf.assetDecodeMs = AssetLoadGuard.decodeMs();
        perf.spriteMemoryBytes = AssetLoadGuard.spriteMemoryBytes();
        perf.spriteMemoryBudgetBytes = SPRITE_MEMORY_BUDGET_BYTES;
        perf.cachedImageCount = AssetLoadGuard.trackedImageCount();
        perf.visualQuality = quality.name();
        perf.performanceWarning = AssetLoadGuard.lastWarning();
        if (perf.spriteMemoryBytes > SPRITE_MEMORY_BUDGET_BYTES) {
            perf.performanceWarning = "SPRITE MEMORY BUDGET EXCEEDED";
            quality = stepDown(quality);
        }
    }

    public static VisualQuality quality() { return quality; }
    public static int projectileTrailStride() {
        return switch (quality) {
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
            case EMERGENCY -> 5;
        };
    }
    public static int wreckChunkBudget() {
        return switch (quality) {
            case HIGH -> 500;
            case MEDIUM -> 360;
            case LOW -> 240;
            case EMERGENCY -> 140;
        };
    }
    public static int vfxDrawStride() {
        return switch (quality) {
            case HIGH -> 1;
            case MEDIUM -> 1;
            case LOW -> 2;
            case EMERGENCY -> 3;
        };
    }
    public static boolean simplifyDistantShip(Ship ship, double camX, double camY) {
        if (ship == null || quality == VisualQuality.HIGH) return false;
        double distance = Math.hypot(ship.x - camX, ship.y - camY);
        double threshold = (quality == VisualQuality.MEDIUM) ? 1500.0 : 900.0;
        return distance >= threshold;
    }

    static void resetForTest() {
        quality = VisualQuality.HIGH;
        overBudgetFrames = 0;
        recoveryFrames = 0;
        lastGcCount = gcCount();
        lastGcMs = gcMs();
    }

    private static VisualQuality stepDown(VisualQuality value) {
        return switch (value) {
            case HIGH -> VisualQuality.MEDIUM;
            case MEDIUM -> VisualQuality.LOW;
            case LOW, EMERGENCY -> VisualQuality.EMERGENCY;
        };
    }

    private static VisualQuality stepUp(VisualQuality value) {
        return switch (value) {
            case HIGH, MEDIUM -> VisualQuality.HIGH;
            case LOW -> VisualQuality.MEDIUM;
            case EMERGENCY -> VisualQuality.LOW;
        };
    }

    private static long gcCount() {
        long value = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0L) value += count;
        }
        return value;
    }

    private static long gcMs() {
        long value = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time > 0L) value += time;
        }
        return value;
    }
}
