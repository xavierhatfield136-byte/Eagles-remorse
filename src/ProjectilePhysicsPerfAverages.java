import app.state.PerfTelemetry;

/**
 * Aggregates projectile physics telemetry across performance harness runs.
 */
public final class ProjectilePhysicsPerfAverages {
    private int samples = 0;
    private double projectileUpdateMs = 0.0;
    private double projectileIndexMs = 0.0;
    private double projectileCiwsMs = 0.0;
    private double projectileVsProjectileMs = 0.0;
    private double projectileVsAsteroidMs = 0.0;
    private double projectileVsShipMs = 0.0;
    private double projectileCleanupMs = 0.0;
    private double shipAsteroidMs = 0.0;

    public void sample(PerfTelemetry perf) {
        if (perf == null) return;
        samples++;
        projectileUpdateMs += perf.projectileUpdateMs;
        projectileIndexMs += perf.projectileIndexMs;
        projectileCiwsMs += perf.projectileCiwsMs;
        projectileVsProjectileMs += perf.projectileVsProjectileMs;
        projectileVsAsteroidMs += perf.projectileVsAsteroidMs;
        projectileVsShipMs += perf.projectileVsShipMs;
        projectileCleanupMs += perf.projectileCleanupMs;
        shipAsteroidMs += perf.shipAsteroidMs;
    }

    public double projectileUpdateMs() { return avg(projectileUpdateMs); }
    public double projectileIndexMs() { return avg(projectileIndexMs); }
    public double projectileCiwsMs() { return avg(projectileCiwsMs); }
    public double projectileVsProjectileMs() { return avg(projectileVsProjectileMs); }
    public double projectileVsAsteroidMs() { return avg(projectileVsAsteroidMs); }
    public double projectileVsShipMs() { return avg(projectileVsShipMs); }
    public double projectileCleanupMs() { return avg(projectileCleanupMs); }
    public double shipAsteroidMs() { return avg(shipAsteroidMs); }

    private double avg(double sum) {
        return samples <= 0 ? 0.0 : sum / (double) samples;
    }
}
