import app.state.PerfTelemetry;

/**
 * Aggregates ship-render telemetry across performance harness runs.
 */
public final class ShipRenderPerfAverages {
    private int samples = 0;
    private double skinMs = 0.0;
    private double detailMs = 0.0;
    private double engineMs = 0.0;
    private double hardpointMs = 0.0;
    private double damageMs = 0.0;
    private double energyMs = 0.0;
    private double nameMs = 0.0;
    private double tokenMs = 0.0;

    public void sample(PerfTelemetry perf) {
        if (perf == null) return;
        samples++;
        skinMs += perf.renderShipSkinMs;
        detailMs += perf.renderShipDetailMs;
        engineMs += perf.renderShipEngineMs;
        hardpointMs += perf.renderShipHardpointMs;
        damageMs += perf.renderShipDamageMs;
        energyMs += perf.renderShipEnergyMs;
        nameMs += perf.renderShipNameMs;
        tokenMs += perf.renderShipTokenMs;
    }

    public double skinMs() { return avg(skinMs); }
    public double detailMs() { return avg(detailMs); }
    public double engineMs() { return avg(engineMs); }
    public double hardpointMs() { return avg(hardpointMs); }
    public double damageMs() { return avg(damageMs); }
    public double energyMs() { return avg(energyMs); }
    public double nameMs() { return avg(nameMs); }
    public double tokenMs() { return avg(tokenMs); }

    private double avg(double sum) {
        return samples <= 0 ? 0.0 : sum / (double) samples;
    }
}
