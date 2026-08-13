import java.util.UUID;

public final class CustomWeaponGenerator {
    public static final int GENERATOR_VERSION = 1;

    private CustomWeaponGenerator() {}

    public static CustomWeaponDefinition generate(CustomWeaponGenerationRequest request) {
        CustomWeaponGenerationRequest req = request == null
                ? new CustomWeaponGenerationRequest("Custom Cannon", CustomWeaponFamily.KINETIC_CANNON,
                CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE, CustomDamageProfile.BALANCED,
                CustomTargetProfile.GENERAL_PURPOSE, 0.8, 6, 760.0, 1200.0, 1, 0.0, 1.0, 1.0)
                : request;

        int projectileCount = 1;
        double lifetimeSeconds = Math.max(GameContext.DT, req.rangeUnits() / Math.max(1.0, req.projectileSpeedUnitsPerSecond()));
        double shieldMul = 1.0;
        double armorMul = 1.0;
        double hullMul = 1.0;
        switch (req.damageProfile()) {
            case SHIELD_PRESSURE -> {
                shieldMul = 1.35;
                armorMul = 0.82;
                hullMul = 0.90;
            }
            case ARMOR_PIERCING -> {
                shieldMul = 0.86;
                armorMul = 1.34;
                hullMul = 1.05;
            }
            case HULL_BREAKER -> {
                shieldMul = 0.82;
                armorMul = 1.05;
                hullMul = 1.32;
            }
            case ANTI_FIGHTER -> {
                shieldMul = 0.95;
                armorMul = 0.90;
                hullMul = 0.95;
            }
            case ANTI_CAPITAL -> {
                shieldMul = 1.06;
                armorMul = 1.16;
                hullMul = 1.10;
            }
            default -> {
            }
        }

        return new CustomWeaponDefinition(
                UUID.randomUUID(),
                req.displayName(),
                CustomWeaponDefinition.CURRENT_SCHEMA_VERSION,
                GENERATOR_VERSION,
                CustomWeaponDefinition.CURRENT_BALANCE_MODEL_VERSION,
                "turret.png",
                "projectile.png",
                "thumbnail.png",
                req.family(),
                CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE,
                req.damageProfile(),
                req.targetProfile(),
                req.cooldownSeconds(),
                req.damage(),
                req.projectileSpeedUnitsPerSecond(),
                req.rangeUnits(),
                lifetimeSeconds,
                projectileCount,
                req.spreadDegrees(),
                0.0,
                0.0,
                shieldMul,
                armorMul,
                hullMul,
                budgetCost(req, shieldMul, armorMul, hullMul, projectileCount),
                req.turretVisualScale(),
                req.projectileVisualScale()
        );
    }

    private static double budgetCost(CustomWeaponGenerationRequest req,
                                     double shieldMul,
                                     double armorMul,
                                     double hullMul,
                                     int projectileCount) {
        double dps = req.damage() * projectileCount / Math.max(0.05, req.cooldownSeconds());
        double rangeMul = Math.sqrt(Math.max(1.0, req.rangeUnits()) / 1000.0);
        double speedMul = Math.sqrt(Math.max(1.0, req.projectileSpeedUnitsPerSecond()) / 760.0);
        double spreadDiscount = 1.0 - Math.min(0.20, req.spreadDegrees() / 300.0);
        double profileMul = (shieldMul + armorMul + hullMul) / 3.0;
        return Math.max(1.0, dps * rangeMul * speedMul * spreadDiscount * profileMul);
    }
}
