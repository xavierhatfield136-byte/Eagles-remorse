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
    public double aiMs = 0.0;
    public double aiMaintenanceMs = 0.0;
    public double aiFleetStateMs = 0.0;
    public double aiShipUtilityMs = 0.0;
    public double aiShipCombatMs = 0.0;
    public double aiShipCombatTargetMs = 0.0;
    public double aiShipCombatFightMs = 0.0;
    public double aiShipCombatFireMs = 0.0;
    public double aiAvoidanceMs = 0.0;
    public double aiFormationSyncMs = 0.0;
    public double aiBoundsMs = 0.0;
    public double aiCacheQueryMs = 0.0;
    public double campaignMs = 0.0;
    public double assetDecodeMs = 0.0;
    public double gcMs = 0.0;
    public double renderShipsMs = 0.0;
    public double renderHudMs = 0.0;
    public double renderMapMs = 0.0;
    public double shieldRenderMs = 0.0;
    public int updateSteps = 0;
    public int droppedUpdates = 0;
    public int drawnShips = 0;
    public int drawnProjectiles = 0;
    public int drawnAsteroids = 0;
    public int drawnSalvage = 0;
    public int drawnWreckChunks = 0;
    public int drawnVfx = 0;
    public int drawnExplosions = 0;
    public int visibleSprites = 0;
    public int drawnUiPanels = 0;
    public int totalVfx = 0;
    public int totalExplosions = 0;
    public int totalWreckChunks = 0;
    public int assetDecodeCount = 0;
    public int assetDecodeDuringFrameCount = 0;
    public int gameplayDiskLoadCount = 0;
    public int gcCollections = 0;
    public int cachedImageCount = 0;
    public long spriteMemoryBytes = 0L;
    public long spriteMemoryBudgetBytes = 0L;
    public String visualQuality = "HIGH";
    public String performanceWarning = "";
    public int aiPreferredTargetHits = 0;
    public int aiPreferredTargetMisses = 0;
    public int aiImmediateThreatHits = 0;
    public int aiImmediateThreatMisses = 0;
    public int aiSensorSignalHits = 0;
    public int aiSensorSignalMisses = 0;
}
