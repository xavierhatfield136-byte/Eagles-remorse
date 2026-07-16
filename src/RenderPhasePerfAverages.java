import app.state.PerfTelemetry;

/**
 * Aggregates non-ship render phase telemetry across performance harness runs.
 */
public final class RenderPhasePerfAverages {
    private int samples = 0;
    private double backgroundMs = 0.0;
    private double worldCompositeMs = 0.0;
    private double vfxMs = 0.0;
    private double explosionsMs = 0.0;
    private double projectilesMs = 0.0;
    private double worldMarkersMs = 0.0;
    private double hudMs = 0.0;
    private int maxSimplifiedProjectiles = 0;

    public void sample(PerfTelemetry perf) {
        if (perf == null) return;
        samples++;
        backgroundMs += perf.renderBackgroundMs;
        worldCompositeMs += perf.renderWorldCompositeMs;
        vfxMs += perf.renderVfxMs;
        explosionsMs += perf.renderExplosionsMs;
        projectilesMs += perf.renderProjectilesMs;
        worldMarkersMs += perf.renderWorldMarkersMs;
        hudMs += perf.renderHudMs;
        maxSimplifiedProjectiles = Math.max(maxSimplifiedProjectiles, perf.simplifiedProjectiles);
    }

    public double backgroundMs() { return avg(backgroundMs); }
    public double worldCompositeMs() { return avg(worldCompositeMs); }
    public double vfxMs() { return avg(vfxMs); }
    public double explosionsMs() { return avg(explosionsMs); }
    public double projectilesMs() { return avg(projectilesMs); }
    public double worldMarkersMs() { return avg(worldMarkersMs); }
    public double hudMs() { return avg(hudMs); }
    public int maxSimplifiedProjectiles() { return maxSimplifiedProjectiles; }

    private double avg(double sum) {
        return samples <= 0 ? 0.0 : sum / (double) samples;
    }
}
