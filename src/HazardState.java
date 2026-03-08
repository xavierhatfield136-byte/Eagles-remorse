/**
 * Ship-room hazard state contract.
 */
public final class HazardState {
    public final String hazardId;
    public final String roomId;
    public final String type;
    public final double intensity;
    public final double spreadTimer;
    public final String suppressionState;

    public HazardState(String hazardId,
                       String roomId,
                       String type,
                       double intensity,
                       double spreadTimer,
                       String suppressionState) {
        this.hazardId = (hazardId == null) ? "" : hazardId;
        this.roomId = (roomId == null) ? "" : roomId;
        this.type = (type == null || type.isBlank()) ? "unknown" : type;
        this.intensity = Math.max(0.0, intensity);
        this.spreadTimer = Math.max(0.0, spreadTimer);
        this.suppressionState = (suppressionState == null || suppressionState.isBlank())
                ? "none"
                : suppressionState;
    }
}
