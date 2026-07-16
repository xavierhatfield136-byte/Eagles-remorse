import app.state.PerfTelemetry;

/**
 * Aggregates broad physics-system telemetry across performance harness runs.
 */
public final class BroadPhysicsPerfAverages {
    private int samples = 0;
    private double shipUpdateMs = 0.0;
    private double superweaponPollMs = 0.0;
    private double playerWeaponMs = 0.0;
    private double playerTargetingMs = 0.0;
    private double playerAimMs = 0.0;
    private double playerPrimaryMs = 0.0;
    private double playerSecondaryMs = 0.0;
    private double postCollisionMs = 0.0;

    public void sample(PerfTelemetry perf) {
        if (perf == null) return;
        samples++;
        shipUpdateMs += perf.physicsShipUpdateMs;
        superweaponPollMs += perf.physicsSuperweaponPollMs;
        playerWeaponMs += perf.physicsPlayerWeaponMs;
        playerTargetingMs += perf.physicsPlayerTargetingMs;
        playerAimMs += perf.physicsPlayerAimMs;
        playerPrimaryMs += perf.physicsPlayerPrimaryMs;
        playerSecondaryMs += perf.physicsPlayerSecondaryMs;
        postCollisionMs += perf.physicsPostCollisionMs;
    }

    public double shipUpdateMs() { return avg(shipUpdateMs); }
    public double superweaponPollMs() { return avg(superweaponPollMs); }
    public double playerWeaponMs() { return avg(playerWeaponMs); }
    public double playerTargetingMs() { return avg(playerTargetingMs); }
    public double playerAimMs() { return avg(playerAimMs); }
    public double playerPrimaryMs() { return avg(playerPrimaryMs); }
    public double playerSecondaryMs() { return avg(playerSecondaryMs); }
    public double postCollisionMs() { return avg(postCollisionMs); }

    private double avg(double sum) {
        return samples <= 0 ? 0.0 : sum / (double) samples;
    }
}
