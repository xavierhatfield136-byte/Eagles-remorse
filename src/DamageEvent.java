/**
 * Structured damage event for replay/debug and telemetry analysis.
 */
public final class DamageEvent {
    public final String sourceId;
    public final int targetShipId;
    public final double worldX;
    public final double worldY;
    public final double localX;
    public final double localY;
    public final String damageType;
    public final double energy;
    public final long timestamp;
    public final RoomDamageResult roomDamageResult;

    public DamageEvent(String sourceId,
                       int targetShipId,
                       double worldX,
                       double worldY,
                       double localX,
                       double localY,
                       String damageType,
                       double energy,
                       long timestamp,
                       RoomDamageResult roomDamageResult) {
        this.sourceId = (sourceId == null || sourceId.isBlank()) ? "unknown" : sourceId;
        this.targetShipId = targetShipId;
        this.worldX = worldX;
        this.worldY = worldY;
        this.localX = localX;
        this.localY = localY;
        this.damageType = (damageType == null || damageType.isBlank()) ? "kinetic" : damageType;
        this.energy = Math.max(0.0, energy);
        this.timestamp = timestamp;
        this.roomDamageResult = roomDamageResult;
    }
}
