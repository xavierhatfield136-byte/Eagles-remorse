import app.persistence.AcademyProgressStore;

/**
 * Thin authority wrapper for the Commander Academy.
 *
 * The existing TutorialSystem still owns the concrete lessons; this director is
 * the stable integration point for menu, progress, AAR, and future graduation
 * work without duplicating combat or campaign simulation.
 */
public final class AcademyDirector {
    public static final int VERSION = AcademyProgressStore.ACADEMY_VERSION;

    private AcademyDirector() {}

    public static void start(GameContext ctx, Faction playerFaction) {
        TutorialSystem.init(ctx, playerFaction);
    }

    public static boolean isActive(GameContext ctx) {
        return TutorialSystem.isActive(ctx);
    }

    public static AcademyProgressStore.Progress progress() {
        return AcademyProgressStore.load();
    }

    public static void recordEvent(String sessionId,
                                   String event,
                                   String chapter,
                                   String step,
                                   double elapsedSeconds,
                                   String result) {
        AcademyProgressStore.recordEvent(sessionId, event, chapter, step, elapsedSeconds, result);
    }

    public static void recordFailure(String sessionId, String chapter, String step, String result) {
        AcademyProgressStore.markStepFailed(sessionId, chapter, step, result);
    }

    public static void recordRecovery(String sessionId, String chapter, String step, String result) {
        AcademyProgressStore.markStepRecovered(sessionId, chapter, step, result);
    }

    public static void recordAbandoned(String sessionId, String chapter, String step, double elapsedSeconds) {
        AcademyProgressStore.markAbandoned(sessionId, chapter, step, elapsedSeconds);
    }

    public static void recordHint(String sessionId, String chapter, String step, boolean repeat) {
        AcademyProgressStore.recordHint(sessionId, chapter, step, repeat);
    }
}
