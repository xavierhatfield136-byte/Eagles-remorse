import app.config.ExperienceSettings;
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
    public static final int ORDINARY_TARGET_FPS = 60;
    public static final int LARGEST_BATTLE_FLOOR_FPS = 30;
    public static final double ORDINARY_FRAME_BUDGET_MS = 1000.0 / ORDINARY_TARGET_FPS;
    public static final double LARGEST_BATTLE_FRAME_BUDGET_MS = 1000.0 / LARGEST_BATTLE_FLOOR_FPS;
    private static final long SPRITE_MEMORY_BUDGET_BYTES = 384L * 1024L * 1024L;
    private static VisualQuality quality = VisualQuality.HIGH;
    private static ExperienceSettings.VisualDetail requestedVisualDetail = ExperienceSettings.VisualDetail.AUTO;
    private static int overBudgetFrames = 0;
    private static int recoveryFrames = 0;
    private static long lastGcCount = gcCount();
    private static long lastGcMs = gcMs();

    private PerformanceGuardrails() {}

    public static void update(PerfTelemetry perf) {
        if (perf == null) return;
        if (requestedVisualDetail != null && requestedVisualDetail != ExperienceSettings.VisualDetail.AUTO) {
            quality = qualityFor(requestedVisualDetail);
        }
        double frame = Math.max(perf.frameMs, perf.renderMs);
        if (requestedVisualDetail == null || requestedVisualDetail == ExperienceSettings.VisualDetail.AUTO) {
            updateAdaptiveQuality(frame);
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
        perf.visualQuality = quality.name() + (requestedVisualDetail == ExperienceSettings.VisualDetail.AUTO ? " AUTO" : " MANUAL");
        perf.performanceWarning = AssetLoadGuard.lastWarning();
        if (perf.spriteMemoryBytes > SPRITE_MEMORY_BUDGET_BYTES) {
            perf.performanceWarning = "SPRITE MEMORY BUDGET EXCEEDED";
            quality = stepDown(quality);
        }
    }

    public static void update(GameContext ctx) {
        applyExperienceSettings(ctx == null || ctx.config == null ? null : ctx.config.experience);
        update(ctx == null ? null : ctx.perf);
    }

    public static void applyExperienceSettings(ExperienceSettings settings) {
        requestedVisualDetail = (settings == null || settings.visualDetail == null)
                ? ExperienceSettings.VisualDetail.AUTO
                : settings.visualDetail;
    }

    public static ExperienceSettings.VisualDetail requestedVisualDetail() {
        return requestedVisualDetail;
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
        requestedVisualDetail = ExperienceSettings.VisualDetail.AUTO;
    }

    private static void updateAdaptiveQuality(double frame) {
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
    }

    private static VisualQuality qualityFor(ExperienceSettings.VisualDetail detail) {
        if (detail == null) return VisualQuality.HIGH;
        return switch (detail) {
            case AUTO, HIGH -> VisualQuality.HIGH;
            case MEDIUM -> VisualQuality.MEDIUM;
            case LOW -> VisualQuality.LOW;
        };
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
