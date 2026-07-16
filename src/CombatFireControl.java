/**
 * AI fire-control helpers.
 */
public final class CombatFireControl {
    private CombatFireControl() {}

    public static boolean canConsiderTarget(Ship shooter, Ship target) {
        if (shooter == null || target == null) return false;
        if (!CombatTargeting.isLiveTarget(target)) return false;
        if (shooter.faction == null || target.faction == null) return true;
        return !shooter.faction.isFriendlyTo(target.faction);
    }

    public static boolean canKeepCachedTarget(Ship shooter, Ship target) {
        return canConsiderTarget(shooter, target);
    }
}
