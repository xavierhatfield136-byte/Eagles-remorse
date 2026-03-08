import java.util.Collections;
import java.util.List;

/**
 * Result payload produced by room-local damage resolution.
 */
public final class RoomDamageResult {
    public static final RoomDamageResult NONE = new RoomDamageResult(
            "",
            0.0,
            0.0,
            0,
            List.of(),
            0,
            0.0
    );

    public final String roomId;
    public final double hpBefore;
    public final double hpAfter;
    public final int hazardRolls;
    public final List<String> subsystemTransitions;
    public final int shipStructuralDelta;
    public final double roomLocalHpLoss;

    public RoomDamageResult(String roomId,
                            double hpBefore,
                            double hpAfter,
                            int hazardRolls,
                            List<String> subsystemTransitions,
                            int shipStructuralDelta) {
        this(
                roomId,
                hpBefore,
                hpAfter,
                hazardRolls,
                subsystemTransitions,
                shipStructuralDelta,
                Math.max(0.0, hpBefore - hpAfter)
        );
    }

    public RoomDamageResult(String roomId,
                            double hpBefore,
                            double hpAfter,
                            int hazardRolls,
                            List<String> subsystemTransitions,
                            int shipStructuralDelta,
                            double roomLocalHpLoss) {
        this.roomId = (roomId == null) ? "" : roomId;
        this.hpBefore = Math.max(0.0, hpBefore);
        this.hpAfter = Math.max(0.0, hpAfter);
        this.hazardRolls = Math.max(0, hazardRolls);
        this.subsystemTransitions = (subsystemTransitions == null)
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(subsystemTransitions));
        this.shipStructuralDelta = shipStructuralDelta;
        this.roomLocalHpLoss = Math.max(0.0, roomLocalHpLoss);
    }
}
