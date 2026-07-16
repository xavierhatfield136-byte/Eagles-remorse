/**
 * AI target-selection cadence helpers.
 */
public final class CombatTargeting {
    private CombatTargeting() {}

    public static boolean shouldRunDetailedFightAssessment(AiScalePolicy.FramePlan scalePlan, Ship ship) {
        return scalePlan == null || !scalePlan.isLargeBattle() || scalePlan.shouldRunFullDecisionScan(ship);
    }

    public static int targetCandidateCap(AiScalePolicy.FramePlan scalePlan) {
        return scalePlan == null ? 48 : Math.max(4, scalePlan.targetCandidateCap);
    }

    public static double intentReuseSeconds(AiScalePolicy.FramePlan scalePlan, Ship ship) {
        if (scalePlan == null) return 0.04;
        double seconds = scalePlan.intentReuseSeconds;
        if (ship != null && (ship.aiForcedEngageTimer > 0.0 || isCommandShip(ship))) {
            seconds *= 0.55;
        }
        return Math.max(0.0, seconds);
    }

    public static boolean shouldRunFullCandidateScore(AiScalePolicy.FramePlan scalePlan, Ship ship, Ship target) {
        if (scalePlan == null || !scalePlan.isLargeBattle()) return true;
        if (ship == null || target == null) return false;
        if (scalePlan.shouldRunFullDecisionScan(ship)) return true;
        if (ship.aiForcedEngageTimer > 0.0) return true;
        if (isCommandShip(ship)) return true;
        return isCommandShip(target);
    }

    public static Ship focusTargetForSquad(java.util.List<Ship> focusTargets, int squadIndex) {
        if (focusTargets == null || focusTargets.isEmpty()) return null;
        for (int i = 0; i < focusTargets.size(); i++) {
            Ship candidate = focusTargets.get(Math.floorMod(squadIndex + i, focusTargets.size()));
            if (isLiveTarget(candidate)) return candidate;
        }
        return null;
    }

    public static boolean isLiveTarget(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }

    private static boolean isCommandShip(Ship ship) {
        return ship != null && ship.role != null
                && (ship.role.isTitanOrMothership()
                || ship.role == ShipRole.SUPERSHIP
                || ship.role == ShipRole.DREADNOUGHT
                || ship.role == ShipRole.BATTLESHIP
                || ship.role == ShipRole.CARRIER
                || ship.role == ShipRole.DRONE_CARRIER);
    }
}
