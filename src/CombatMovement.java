/**
 * AI movement helpers that keep steering constraints in one place.
 */
public final class CombatMovement {
    private CombatMovement() {}

    public static double finiteSpeed(double speedPerSec) {
        if (!Double.isFinite(speedPerSec)) return 0.0;
        return Math.max(0.0, speedPerSec);
    }

    public static boolean shouldReuseMovementThink(AiScalePolicy.FramePlan scalePlan, Ship ship) {
        if (scalePlan == null || !scalePlan.isLargeBattle() || ship == null) return false;
        if (ship.aiForcedEngageTimer > 0.0) return false;
        if (ship.aiBadApproachTimer > 0.0) return false;
        if (ship.role == ShipRole.FIGHTER || ship.role == ShipRole.DRONE || ship.role == ShipRole.BOMBER
                || ship.role == ShipRole.PD_CRAFT) return false;
        return ship.aiMovementThinkTimer > 0.0;
    }

    public static double movementThinkSeconds(AiScalePolicy.FramePlan scalePlan, Ship ship) {
        if (scalePlan == null) return 0.0;
        double seconds = scalePlan.movementThinkSeconds;
        if (ship != null && ship.role != null && ship.role.isTitanOrMothership()) seconds *= 0.55;
        return Math.max(0.0, seconds);
    }
}
