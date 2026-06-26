import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha battle-scale contract for Phase 9.
 *
 * The supported scale is intentionally finite: the game supports large,
 * readable fleet battles, not unlimited live ships.
 */
public final class PerformanceBattleScale {
    public static final int ORDINARY_SHIPS_PER_SIDE = 14;
    public static final int MAJOR_SHIPS_PER_SIDE = 100;
    public static final int TITAN_SHIPS_PER_SIDE = 72;
    public static final int LARGEST_SUPPORTED_SHIPS_PER_SIDE = 50;
    public static final int LARGEST_SUPPORTED_TOTAL_SHIPS = LARGEST_SUPPORTED_SHIPS_PER_SIDE * 2;
    public static final int STRESS_TEST_SHIPS_PER_SIDE = 160;
    public static final int STRESS_TEST_TOTAL_SHIPS = STRESS_TEST_SHIPS_PER_SIDE * 2;
    public static final int PLAYER_FACING_READABLE_ONSCREEN_SHIPS = 180;
    public static final int PROJECTILE_PRESSURE_TARGET = 760;
    public static final int WRECK_PRESSURE_TARGET = 500;
    public static final int VFX_PRESSURE_TARGET = 500;
    public static final int ORDINARY_TARGET_FPS = PerformanceGuardrails.ORDINARY_TARGET_FPS;
    public static final int LARGEST_SUPPORTED_FPS_FLOOR = PerformanceGuardrails.LARGEST_BATTLE_FLOOR_FPS;
    public static final int INTEGRATED_GRAPHICS_MIN_HEAP_MB = 2048;
    public static final int RECOMMENDED_DISCRETE_HEAP_MB = 4096;

    public enum Scenario {
        ORDINARY("ordinary", ORDINARY_SHIPS_PER_SIDE, 0, 0, 70, 40, 80,
                ORDINARY_TARGET_FPS, ORDINARY_TARGET_FPS),
        MAJOR_100_PER_SIDE("major-100-per-side", MAJOR_SHIPS_PER_SIDE, 12, 0, 520, 240, 360,
                45, LARGEST_SUPPORTED_FPS_FLOOR),
        TITAN_HEAVY("titan-heavy", TITAN_SHIPS_PER_SIDE, 10, 4, 560, 260, 420,
                45, LARGEST_SUPPORTED_FPS_FLOOR),
        LARGEST_SUPPORTED("largest-supported", LARGEST_SUPPORTED_SHIPS_PER_SIDE, 6, 0,
                180, 100, 180, LARGEST_SUPPORTED_FPS_FLOOR, LARGEST_SUPPORTED_FPS_FLOOR),
        STRESS_160_PER_SIDE("stress-160-per-side", STRESS_TEST_SHIPS_PER_SIDE, 18, 2,
                PROJECTILE_PRESSURE_TARGET, WRECK_PRESSURE_TARGET, VFX_PRESSURE_TARGET,
                LARGEST_SUPPORTED_FPS_FLOOR, LARGEST_SUPPORTED_FPS_FLOOR),
        CAPITAL_HEAVY("capital-heavy", 96, 30, 0, 520, 260, 360,
                45, LARGEST_SUPPORTED_FPS_FLOOR),
        MISSILE_HEAVY("missile-heavy", 112, 8, 0, PROJECTILE_PRESSURE_TARGET, 220, 420,
                45, LARGEST_SUPPORTED_FPS_FLOOR),
        WRECK_HEAVY("wreck-heavy-aftermath", 80, 14, 2, 360, WRECK_PRESSURE_TARGET, VFX_PRESSURE_TARGET,
                45, LARGEST_SUPPORTED_FPS_FLOOR),
        REPEATED_TACTICAL_ENTRIES("repeated-tactical-entries", 40, 4, 0, 220, 90, 180,
                ORDINARY_TARGET_FPS, ORDINARY_TARGET_FPS),
        LONG_STRATEGIC_CAMPAIGN("long-strategic-campaign", 0, 0, 0, 0, 0, 0,
                ORDINARY_TARGET_FPS, ORDINARY_TARGET_FPS),
        LARGE_CAMPAIGN_SAVE_LOAD("large-campaign-save-load", 0, 0, 0, 0, 0, 0,
                ORDINARY_TARGET_FPS, ORDINARY_TARGET_FPS);

        public final String id;
        public final int shipsPerSide;
        public final int capitalShipsTotal;
        public final int titanShipsTotal;
        public final int projectileTarget;
        public final int wreckTarget;
        public final int vfxTarget;
        public final int targetFps;
        public final int floorFps;

        Scenario(String id, int shipsPerSide, int capitalShipsTotal, int titanShipsTotal,
                 int projectileTarget, int wreckTarget, int vfxTarget, int targetFps, int floorFps) {
            this.id = id;
            this.shipsPerSide = shipsPerSide;
            this.capitalShipsTotal = capitalShipsTotal;
            this.titanShipsTotal = titanShipsTotal;
            this.projectileTarget = projectileTarget;
            this.wreckTarget = wreckTarget;
            this.vfxTarget = vfxTarget;
            this.targetFps = targetFps;
            this.floorFps = floorFps;
        }

        public int totalShips() {
            return shipsPerSide * 2;
        }

        public double frameBudgetMs() {
            return 1000.0 / Math.max(1, floorFps);
        }
    }

    private PerformanceBattleScale() {}

    public static Scenario scenario(String raw) {
        if (raw == null || raw.isBlank()) return Scenario.LARGEST_SUPPORTED;
        String normalized = raw.trim().toLowerCase(Locale.US).replace('_', '-');
        for (Scenario scenario : Scenario.values()) {
            if (scenario.id.equals(normalized)
                    || scenario.name().toLowerCase(Locale.US).replace('_', '-').equals(normalized)) {
                return scenario;
            }
        }
        return Scenario.LARGEST_SUPPORTED;
    }

    public static List<String> contractLines() {
        List<String> out = new ArrayList<>();
        out.add("Ordinary tactical play: " + ORDINARY_SHIPS_PER_SIDE + " ships per side, 60 FPS target.");
        out.add("Major battle stress: " + MAJOR_SHIPS_PER_SIDE + " ships per side, bounded projectile/VFX pressure.");
        out.add("Titan-heavy stress: " + TITAN_SHIPS_PER_SIDE + " ships per side with four titan hulls total.");
        out.add("Largest supported battle: " + LARGEST_SUPPORTED_SHIPS_PER_SIDE + " ships per side / "
                + LARGEST_SUPPORTED_TOTAL_SHIPS + " total, 30 FPS hard floor.");
        out.add("Stress-only battle: " + STRESS_TEST_SHIPS_PER_SIDE + " ships per side / "
                + STRESS_TEST_TOTAL_SHIPS + " total is measured but not promised as supported alpha scale.");
        out.add("Readable player-facing scale: keep visible on-screen ship density at or below "
                + PLAYER_FACING_READABLE_ONSCREEN_SHIPS + " through camera scale, culling, and Tactical FPS View.");
        out.add("No unlimited promise: encounters above " + LARGEST_SUPPORTED_TOTAL_SHIPS
                + " live ships are unsupported alpha stress/debug territory.");
        out.add("Minimum integrated target: " + INTEGRATED_GRAPHICS_MIN_HEAP_MB
                + "MB heap, 1280x720, adaptive/low visual detail, largest battle floor "
                + LARGEST_SUPPORTED_FPS_FLOOR + " FPS.");
        out.add("Recommended discrete target: " + RECOMMENDED_DISCRETE_HEAP_MB
                + "MB heap, 1920x1080, ordinary battle target " + ORDINARY_TARGET_FPS + " FPS.");
        return out;
    }

    public static List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (ORDINARY_SHIPS_PER_SIDE <= 0) errors.add("ordinary ship count missing");
        if (MAJOR_SHIPS_PER_SIDE < 100) errors.add("major battle below 100 ships per side");
        if (LARGEST_SUPPORTED_SHIPS_PER_SIDE < 50) errors.add("largest supported battle below 50 ships per side");
        if (STRESS_TEST_SHIPS_PER_SIDE < 160) errors.add("160 per-side stress scenario missing");
        if (LARGEST_SUPPORTED_TOTAL_SHIPS > 360) errors.add("largest supported battle exceeds readable alpha cap");
        if (PLAYER_FACING_READABLE_ONSCREEN_SHIPS >= STRESS_TEST_TOTAL_SHIPS) {
            errors.add("readable on-screen scale must remain below 160-per-side stress scale");
        }
        if (PROJECTILE_PRESSURE_TARGET < 700) errors.add("projectile pressure target too low");
        if (WRECK_PRESSURE_TARGET < PerformanceGuardrails.wreckChunkBudget()) {
            errors.add("wreck pressure target below high-quality budget");
        }
        if (ORDINARY_TARGET_FPS != 60) errors.add("ordinary target must remain 60 FPS");
        if (LARGEST_SUPPORTED_FPS_FLOOR != 30) errors.add("largest battle floor must remain 30 FPS");
        return errors;
    }
}
