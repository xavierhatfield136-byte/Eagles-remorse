import app.state.PerfTelemetry;

/**
 * Aggregates AI telemetry across performance harness runs.
 */
public final class AiPerfAverages {
    private int samples = 0;
    private double maintenanceMs = 0.0;
    private double fleetStateMs = 0.0;
    private double shipUtilityMs = 0.0;
    private double shipCombatMs = 0.0;
    private double shipCombatTargetMs = 0.0;
    private double shipCombatFightMs = 0.0;
    private double shipCombatFireMs = 0.0;
    private double avoidanceMs = 0.0;
    private double formationSyncMs = 0.0;
    private double boundsMs = 0.0;
    private double cacheQueryMs = 0.0;
    private double intentCacheHits = 0.0;
    private double intentCacheMisses = 0.0;
    private double intentInvalidations = 0.0;
    private double cheapTargetScores = 0.0;
    private double mediumTargetScores = 0.0;
    private double expensiveTargetScores = 0.0;
    private double movementReuseFrames = 0.0;

    public void sample(PerfTelemetry perf) {
        if (perf == null) return;
        samples++;
        maintenanceMs += perf.aiMaintenanceMs;
        fleetStateMs += perf.aiFleetStateMs;
        shipUtilityMs += perf.aiShipUtilityMs;
        shipCombatMs += perf.aiShipCombatMs;
        shipCombatTargetMs += perf.aiShipCombatTargetMs;
        shipCombatFightMs += perf.aiShipCombatFightMs;
        shipCombatFireMs += perf.aiShipCombatFireMs;
        avoidanceMs += perf.aiAvoidanceMs;
        formationSyncMs += perf.aiFormationSyncMs;
        boundsMs += perf.aiBoundsMs;
        cacheQueryMs += perf.aiCacheQueryMs;
        intentCacheHits += perf.aiIntentCacheHits;
        intentCacheMisses += perf.aiIntentCacheMisses;
        intentInvalidations += perf.aiIntentInvalidations;
        cheapTargetScores += perf.aiCheapTargetScores;
        mediumTargetScores += perf.aiMediumTargetScores;
        expensiveTargetScores += perf.aiExpensiveTargetScores;
        movementReuseFrames += perf.aiMovementReuseFrames;
    }

    public double maintenanceMs() { return avg(maintenanceMs); }
    public double fleetStateMs() { return avg(fleetStateMs); }
    public double shipUtilityMs() { return avg(shipUtilityMs); }
    public double shipCombatMs() { return avg(shipCombatMs); }
    public double shipCombatTargetMs() { return avg(shipCombatTargetMs); }
    public double shipCombatFightMs() { return avg(shipCombatFightMs); }
    public double shipCombatFireMs() { return avg(shipCombatFireMs); }
    public double avoidanceMs() { return avg(avoidanceMs); }
    public double formationSyncMs() { return avg(formationSyncMs); }
    public double boundsMs() { return avg(boundsMs); }
    public double cacheQueryMs() { return avg(cacheQueryMs); }
    public double intentCacheHits() { return avg(intentCacheHits); }
    public double intentCacheMisses() { return avg(intentCacheMisses); }
    public double intentInvalidations() { return avg(intentInvalidations); }
    public double cheapTargetScores() { return avg(cheapTargetScores); }
    public double mediumTargetScores() { return avg(mediumTargetScores); }
    public double expensiveTargetScores() { return avg(expensiveTargetScores); }
    public double movementReuseFrames() { return avg(movementReuseFrames); }

    private double avg(double sum) {
        return samples <= 0 ? 0.0 : sum / (double) samples;
    }
}
