package app.state;

/**
 * Runtime telemetry used by the dev overlay and frame pacing diagnostics.
 */
public final class PerfTelemetry {
    public double fps = 0.0;
    public double frameMs = 0.0;
    public double frameJitterMs = 0.0;
    public double updateMs = 0.0;
    public double renderMs = 0.0;
    public int updateSteps = 0;
    public int droppedUpdates = 0;
    public int drawnShips = 0;
    public int drawnProjectiles = 0;
    public int drawnAsteroids = 0;
    public int drawnSalvage = 0;
    public int drawnVfx = 0;
    public int drawnExplosions = 0;
    public int totalVfx = 0;
    public int totalExplosions = 0;
}
