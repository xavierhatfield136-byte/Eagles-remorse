public record CustomWeaponGenerationRequest(
        String displayName,
        CustomWeaponFamily family,
        CustomWeaponRuntimeBehavior behavior,
        CustomDamageProfile damageProfile,
        CustomTargetProfile targetProfile,
        double cooldownSeconds,
        int damage,
        double projectileSpeedUnitsPerSecond,
        double rangeUnits,
        int projectileCount,
        double spreadDegrees,
        double turretVisualScale,
        double projectileVisualScale
) {
    public CustomWeaponGenerationRequest {
        displayName = clean(displayName, "Custom Cannon");
        if (family == null) family = CustomWeaponFamily.KINETIC_CANNON;
        if (behavior == null) behavior = CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE;
        if (damageProfile == null) damageProfile = CustomDamageProfile.BALANCED;
        if (targetProfile == null) targetProfile = CustomTargetProfile.GENERAL_PURPOSE;
        cooldownSeconds = MathUtil.clamp(finite(cooldownSeconds, 0.8), 0.08, 12.0);
        damage = MathUtil.clamp(damage, 1, 80);
        projectileSpeedUnitsPerSecond = MathUtil.clamp(finite(projectileSpeedUnitsPerSecond, 760.0), 80.0, 1200.0);
        rangeUnits = MathUtil.clamp(finite(rangeUnits, 1200.0), 120.0, 3000.0);
        projectileCount = MathUtil.clamp(projectileCount, 1, 12);
        spreadDegrees = MathUtil.clamp(finite(spreadDegrees, 0.0), 0.0, 60.0);
        turretVisualScale = MathUtil.clamp(finite(turretVisualScale, 1.0), 0.25, 2.0);
        projectileVisualScale = MathUtil.clamp(finite(projectileVisualScale, 1.0), 0.25, 3.0);
    }

    private static String clean(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? fallback : text;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
