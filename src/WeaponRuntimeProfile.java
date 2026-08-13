import java.nio.file.Path;
import java.util.UUID;

public record WeaponRuntimeProfile(
        UUID id,
        String displayName,
        CustomWeaponFamily family,
        CustomWeaponRuntimeBehavior behavior,
        CustomDamageProfile damageProfile,
        CustomTargetProfile targetProfile,
        double cooldownSeconds,
        int damage,
        double projectileSpeedUnitsPerSecond,
        double rangeUnits,
        double projectileLifetimeSeconds,
        int projectileCount,
        double spreadDegrees,
        double turnRateDegreesPerSecond,
        double splashRadiusUnits,
        double shieldDamageMultiplier,
        double armorDamageMultiplier,
        double hullDamageMultiplier,
        double balanceBudgetCost,
        Path turretAssetPath,
        Path projectileAssetPath,
        double turretVisualScale,
        double projectileVisualScale
) {
    public WeaponRuntimeProfile {
        displayName = displayName == null || displayName.isBlank() ? "Custom Weapon" : displayName.trim();
        family = family == null ? CustomWeaponFamily.KINETIC_CANNON : family;
        behavior = behavior == null ? CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE : behavior;
        damageProfile = damageProfile == null ? CustomDamageProfile.BALANCED : damageProfile;
        targetProfile = targetProfile == null ? CustomTargetProfile.GENERAL_PURPOSE : targetProfile;
        cooldownSeconds = Math.max(0.05, finite(cooldownSeconds, 1.0));
        damage = Math.max(1, damage);
        projectileSpeedUnitsPerSecond = Math.max(1.0, finite(projectileSpeedUnitsPerSecond, 750.0));
        rangeUnits = Math.max(1.0, finite(rangeUnits, 900.0));
        projectileLifetimeSeconds = Math.max(GameContext.DT, finite(projectileLifetimeSeconds, rangeUnits / projectileSpeedUnitsPerSecond));
        projectileCount = MathUtil.clamp(projectileCount, 1, 16);
        spreadDegrees = Math.max(0.0, finite(spreadDegrees, 0.0));
        turnRateDegreesPerSecond = Math.max(0.0, finite(turnRateDegreesPerSecond, 0.0));
        splashRadiusUnits = Math.max(0.0, finite(splashRadiusUnits, 0.0));
        shieldDamageMultiplier = Math.max(0.0, finite(shieldDamageMultiplier, 1.0));
        armorDamageMultiplier = Math.max(0.0, finite(armorDamageMultiplier, 1.0));
        hullDamageMultiplier = Math.max(0.0, finite(hullDamageMultiplier, 1.0));
        balanceBudgetCost = Math.max(0.0, finite(balanceBudgetCost, 0.0));
        turretVisualScale = MathUtil.clamp(finite(turretVisualScale, 1.0), 0.2, 2.0);
        projectileVisualScale = MathUtil.clamp(finite(projectileVisualScale, 1.0), 0.2, 3.0);
    }

    public int projectileLifetimeFrames() {
        return Math.max(1, (int) Math.round(projectileLifetimeSeconds / GameContext.DT));
    }

    public boolean isV1ADirectProjectile() {
        return behavior == CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
