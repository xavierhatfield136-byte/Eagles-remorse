import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CustomWeaponDefinition {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int CURRENT_BALANCE_MODEL_VERSION = 1;

    public final UUID id;
    public final String displayName;
    public final int schemaVersion;
    public final int generatorVersion;
    public final int balanceModelVersion;
    public final String turretAsset;
    public final String projectileAsset;
    public final String thumbnailAsset;
    public final CustomWeaponFamily family;
    public final CustomWeaponRuntimeBehavior behavior;
    public final CustomDamageProfile damageProfile;
    public final CustomTargetProfile targetProfile;
    public final double cooldownSeconds;
    public final int damage;
    public final double projectileSpeedUnitsPerSecond;
    public final double rangeUnits;
    public final double projectileLifetimeSeconds;
    public final int projectileCount;
    public final double spreadDegrees;
    public final double turnRateDegreesPerSecond;
    public final double splashRadiusUnits;
    public final double shieldDamageMultiplier;
    public final double armorDamageMultiplier;
    public final double hullDamageMultiplier;
    public final double balanceBudgetCost;
    public final double turretVisualScale;
    public final double projectileVisualScale;

    public CustomWeaponDefinition(
            UUID id,
            String displayName,
            int schemaVersion,
            int generatorVersion,
            int balanceModelVersion,
            String turretAsset,
            String projectileAsset,
            String thumbnailAsset,
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
            double turretVisualScale,
            double projectileVisualScale
    ) {
        this.id = id;
        this.displayName = normalizeText(displayName, "Custom Weapon");
        this.schemaVersion = schemaVersion;
        this.generatorVersion = generatorVersion;
        this.balanceModelVersion = balanceModelVersion;
        this.turretAsset = normalizeAsset(turretAsset, "turret.png");
        this.projectileAsset = normalizeAsset(projectileAsset, "projectile.png");
        this.thumbnailAsset = normalizeAsset(thumbnailAsset, "thumbnail.png");
        this.family = family == null ? CustomWeaponFamily.KINETIC_CANNON : family;
        this.behavior = behavior == null ? CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE : behavior;
        this.damageProfile = damageProfile == null ? CustomDamageProfile.BALANCED : damageProfile;
        this.targetProfile = targetProfile == null ? CustomTargetProfile.GENERAL_PURPOSE : targetProfile;
        this.cooldownSeconds = cooldownSeconds;
        this.damage = damage;
        this.projectileSpeedUnitsPerSecond = projectileSpeedUnitsPerSecond;
        this.rangeUnits = rangeUnits;
        this.projectileLifetimeSeconds = projectileLifetimeSeconds;
        this.projectileCount = projectileCount;
        this.spreadDegrees = spreadDegrees;
        this.turnRateDegreesPerSecond = turnRateDegreesPerSecond;
        this.splashRadiusUnits = splashRadiusUnits;
        this.shieldDamageMultiplier = shieldDamageMultiplier;
        this.armorDamageMultiplier = armorDamageMultiplier;
        this.hullDamageMultiplier = hullDamageMultiplier;
        this.balanceBudgetCost = balanceBudgetCost;
        this.turretVisualScale = turretVisualScale;
        this.projectileVisualScale = projectileVisualScale;
    }

    public List<String> validationFailures() {
        List<String> failures = new ArrayList<>();
        if (id == null) failures.add("id is required");
        if (displayName == null || displayName.isBlank()) failures.add("displayName is required");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) failures.add("unsupported schemaVersion " + schemaVersion);
        if (generatorVersion <= 0) failures.add("generatorVersion must be positive");
        if (balanceModelVersion <= 0) failures.add("balanceModelVersion must be positive");
        if (!isSafeAssetName(turretAsset)) failures.add("turretAsset must stay inside the custom weapon folder");
        if (!isSafeAssetName(projectileAsset)) failures.add("projectileAsset must stay inside the custom weapon folder");
        if (!isSafeAssetName(thumbnailAsset)) failures.add("thumbnailAsset must stay inside the custom weapon folder");
        if (behavior != CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE) {
            failures.add("V1-A supports DIRECT_PROJECTILE custom weapons only");
        }
        if (cooldownSeconds <= 0.0 || !Double.isFinite(cooldownSeconds)) failures.add("cooldownSeconds must be positive");
        if (damage <= 0) failures.add("damage must be positive");
        if (projectileSpeedUnitsPerSecond <= 0.0 || !Double.isFinite(projectileSpeedUnitsPerSecond)) {
            failures.add("projectileSpeedUnitsPerSecond must be positive");
        }
        if (rangeUnits <= 0.0 || !Double.isFinite(rangeUnits)) failures.add("rangeUnits must be positive");
        if (projectileLifetimeSeconds <= 0.0 || !Double.isFinite(projectileLifetimeSeconds)) {
            failures.add("projectileLifetimeSeconds must be positive");
        }
        if (projectileCount < 1 || projectileCount > 16) failures.add("projectileCount must be between 1 and 16");
        if (spreadDegrees < 0.0 || spreadDegrees > 60.0 || !Double.isFinite(spreadDegrees)) {
            failures.add("spreadDegrees must be between 0 and 60");
        }
        if (turnRateDegreesPerSecond < 0.0 || !Double.isFinite(turnRateDegreesPerSecond)) {
            failures.add("turnRateDegreesPerSecond must be non-negative");
        }
        if (splashRadiusUnits < 0.0 || !Double.isFinite(splashRadiusUnits)) {
            failures.add("splashRadiusUnits must be non-negative");
        }
        if (shieldDamageMultiplier < 0.0 || !Double.isFinite(shieldDamageMultiplier)) failures.add("shieldDamageMultiplier must be non-negative");
        if (armorDamageMultiplier < 0.0 || !Double.isFinite(armorDamageMultiplier)) failures.add("armorDamageMultiplier must be non-negative");
        if (hullDamageMultiplier < 0.0 || !Double.isFinite(hullDamageMultiplier)) failures.add("hullDamageMultiplier must be non-negative");
        if (balanceBudgetCost < 0.0 || !Double.isFinite(balanceBudgetCost)) failures.add("balanceBudgetCost must be non-negative");
        if (turretVisualScale <= 0.0 || !Double.isFinite(turretVisualScale)) failures.add("turretVisualScale must be positive");
        if (projectileVisualScale <= 0.0 || !Double.isFinite(projectileVisualScale)) failures.add("projectileVisualScale must be positive");
        failures.addAll(compatibilityFailures());
        return failures;
    }

    public List<String> compatibilityFailures() {
        List<String> failures = new ArrayList<>();
        if (behavior == CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE) {
            if (targetProfile == CustomTargetProfile.MISSILES) failures.add("DIRECT_PROJECTILE cannot use MISSILES target profile in V1-A");
            if (turnRateDegreesPerSecond > 0.0) failures.add("DIRECT_PROJECTILE cannot use homing turn rate");
        }
        return failures;
    }

    public WeaponRuntimeProfile toRuntimeProfile(CustomWeaponRegistry registry) {
        CustomWeaponRegistry safeRegistry = registry == null ? new CustomWeaponRegistry() : registry;
        return new WeaponRuntimeProfile(
                id,
                displayName,
                family,
                behavior,
                damageProfile,
                targetProfile,
                cooldownSeconds,
                damage,
                projectileSpeedUnitsPerSecond,
                rangeUnits,
                projectileLifetimeSeconds,
                projectileCount,
                spreadDegrees,
                turnRateDegreesPerSecond,
                splashRadiusUnits,
                shieldDamageMultiplier,
                armorDamageMultiplier,
                hullDamageMultiplier,
                balanceBudgetCost,
                safeRegistry.resolveContentPath(this, turretAsset),
                safeRegistry.resolveContentPath(this, projectileAsset),
                turretVisualScale,
                projectileVisualScale
        );
    }

    public String toJson() {
        return CustomShipJson.stringify(toJsonObject());
    }

    public static CustomWeaponDefinition fromJson(String json) {
        Object parsed = CustomShipJson.parse(json);
        Map<String, Object> object = CustomShipJson.objectValue(parsed);
        if (object.isEmpty()) throw new IllegalArgumentException("Custom weapon definition must be a JSON object");
        String idText = CustomShipJson.stringValue(object, "id", "");
        UUID id = idText.isBlank() ? null : UUID.fromString(idText);
        return new CustomWeaponDefinition(
                id,
                CustomShipJson.stringValue(object, "displayName", ""),
                CustomShipJson.intValue(object, "schemaVersion", 0),
                CustomShipJson.intValue(object, "generatorVersion", 0),
                CustomShipJson.intValue(object, "balanceModelVersion", 0),
                CustomShipJson.stringValue(object, "turretAsset", "turret.png"),
                CustomShipJson.stringValue(object, "projectileAsset", "projectile.png"),
                CustomShipJson.stringValue(object, "thumbnailAsset", "thumbnail.png"),
                enumValue(CustomWeaponFamily.class, CustomShipJson.stringValue(object, "family", "KINETIC_CANNON"), CustomWeaponFamily.KINETIC_CANNON),
                enumValue(CustomWeaponRuntimeBehavior.class, CustomShipJson.stringValue(object, "behavior", "DIRECT_PROJECTILE"), CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE),
                enumValue(CustomDamageProfile.class, CustomShipJson.stringValue(object, "damageProfile", "BALANCED"), CustomDamageProfile.BALANCED),
                enumValue(CustomTargetProfile.class, CustomShipJson.stringValue(object, "targetProfile", "GENERAL_PURPOSE"), CustomTargetProfile.GENERAL_PURPOSE),
                CustomShipJson.doubleValue(object, "cooldownSeconds", 0.0),
                CustomShipJson.intValue(object, "damage", 0),
                CustomShipJson.doubleValue(object, "projectileSpeedUnitsPerSecond", 0.0),
                CustomShipJson.doubleValue(object, "rangeUnits", 0.0),
                CustomShipJson.doubleValue(object, "projectileLifetimeSeconds", 0.0),
                CustomShipJson.intValue(object, "projectileCount", 0),
                CustomShipJson.doubleValue(object, "spreadDegrees", 0.0),
                CustomShipJson.doubleValue(object, "turnRateDegreesPerSecond", 0.0),
                CustomShipJson.doubleValue(object, "splashRadiusUnits", 0.0),
                CustomShipJson.doubleValue(object, "shieldDamageMultiplier", 1.0),
                CustomShipJson.doubleValue(object, "armorDamageMultiplier", 1.0),
                CustomShipJson.doubleValue(object, "hullDamageMultiplier", 1.0),
                CustomShipJson.doubleValue(object, "balanceBudgetCost", 0.0),
                CustomShipJson.doubleValue(object, "turretVisualScale", 1.0),
                CustomShipJson.doubleValue(object, "projectileVisualScale", 1.0)
        );
    }

    Map<String, Object> toJsonObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("id", id == null ? "" : id.toString());
        object.put("displayName", displayName);
        object.put("schemaVersion", schemaVersion);
        object.put("generatorVersion", generatorVersion);
        object.put("balanceModelVersion", balanceModelVersion);
        object.put("turretAsset", turretAsset);
        object.put("projectileAsset", projectileAsset);
        object.put("thumbnailAsset", thumbnailAsset);
        object.put("family", family.name());
        object.put("behavior", behavior.name());
        object.put("damageProfile", damageProfile.name());
        object.put("targetProfile", targetProfile.name());
        object.put("cooldownSeconds", cooldownSeconds);
        object.put("damage", damage);
        object.put("projectileSpeedUnitsPerSecond", projectileSpeedUnitsPerSecond);
        object.put("rangeUnits", rangeUnits);
        object.put("projectileLifetimeSeconds", projectileLifetimeSeconds);
        object.put("projectileCount", projectileCount);
        object.put("spreadDegrees", spreadDegrees);
        object.put("turnRateDegreesPerSecond", turnRateDegreesPerSecond);
        object.put("splashRadiusUnits", splashRadiusUnits);
        object.put("shieldDamageMultiplier", shieldDamageMultiplier);
        object.put("armorDamageMultiplier", armorDamageMultiplier);
        object.put("hullDamageMultiplier", hullDamageMultiplier);
        object.put("balanceBudgetCost", balanceBudgetCost);
        object.put("turretVisualScale", turretVisualScale);
        object.put("projectileVisualScale", projectileVisualScale);
        return object;
    }

    static boolean isSafeAssetName(String pathText) {
        if (pathText == null || pathText.isBlank()) return false;
        if (pathText.contains(":") || pathText.startsWith("/") || pathText.startsWith("\\")) return false;
        Path path;
        try {
            path = Path.of(pathText).normalize();
        } catch (RuntimeException ex) {
            return false;
        }
        return !path.isAbsolute()
                && !path.startsWith("..")
                && path.getNameCount() == 1
                && path.toString().length() <= 96;
    }

    private static String normalizeText(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String normalizeAsset(String value, String fallback) {
        String text = normalizeText(value, fallback).replace('\\', '/');
        return text.isBlank() ? fallback : text;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
