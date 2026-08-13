import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record CustomWeaponMount(
        String mountId,
        double normalizedX,
        double normalizedY,
        Turret.Kind weaponKind,
        double cooldown,
        int damage,
        double projectileSpeed,
        double range,
        int projectileLife,
        WeaponDefinitionRef weaponDefinitionRef
) {
    public CustomWeaponMount {
        mountId = normalizeText(mountId);
        weaponKind = weaponKind == null ? Turret.Kind.GUN : weaponKind;
    }

    public CustomWeaponMount(
            String mountId,
            double normalizedX,
            double normalizedY,
            Turret.Kind weaponKind,
            double cooldown,
            int damage,
            double projectileSpeed,
            double range,
            int projectileLife
    ) {
        this(mountId, normalizedX, normalizedY, weaponKind, cooldown, damage, projectileSpeed, range, projectileLife, null);
    }

    public List<String> validationFailures() {
        List<String> failures = new ArrayList<>();
        if (mountId == null || mountId.isBlank()) failures.add("weapon mount id is required");
        if (!isUnit(normalizedX)) failures.add("weapon " + mountId + " normalizedX must be between 0.0 and 1.0");
        if (!isUnit(normalizedY)) failures.add("weapon " + mountId + " normalizedY must be between 0.0 and 1.0");
        if (cooldown <= 0.0 || Double.isNaN(cooldown) || Double.isInfinite(cooldown)) failures.add("weapon " + mountId + " cooldown must be positive");
        if (damage <= 0) failures.add("weapon " + mountId + " damage must be positive");
        if (projectileSpeed <= 0.0 || Double.isNaN(projectileSpeed) || Double.isInfinite(projectileSpeed)) failures.add("weapon " + mountId + " projectile speed must be positive");
        if (range <= 0.0 || Double.isNaN(range) || Double.isInfinite(range)) failures.add("weapon " + mountId + " range must be positive");
        if (projectileLife <= 0) failures.add("weapon " + mountId + " projectile life must be positive");
        if (weaponDefinitionRef instanceof WeaponDefinitionRef.CustomWeaponRef customRef && customRef.id() == null) {
            failures.add("weapon " + mountId + " custom weapon id is required");
        }
        return failures;
    }

    Map<String, Object> toJsonObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("mountId", mountId);
        object.put("normalizedX", normalizedX);
        object.put("normalizedY", normalizedY);
        object.put("weaponKind", weaponKind.name());
        object.put("cooldown", cooldown);
        object.put("damage", damage);
        object.put("projectileSpeed", projectileSpeed);
        object.put("range", range);
        object.put("projectileLife", projectileLife);
        if (weaponDefinitionRef != null) object.put("weaponDefinitionRef", weaponDefinitionRef.toJsonObject());
        return object;
    }

    static CustomWeaponMount fromJsonObject(Map<String, Object> object) {
        WeaponDefinitionRef ref = WeaponDefinitionRef.fromJsonObject(
                CustomShipJson.objectValue(object.get("weaponDefinitionRef"))).orElse(null);
        return new CustomWeaponMount(
                CustomShipJson.stringValue(object, "mountId", ""),
                CustomShipJson.doubleValue(object, "normalizedX", 0.5),
                CustomShipJson.doubleValue(object, "normalizedY", 0.5),
                enumValue(Turret.Kind.class, CustomShipJson.stringValue(object, "weaponKind", "GUN"), Turret.Kind.GUN),
                CustomShipJson.doubleValue(object, "cooldown", 1.0),
                CustomShipJson.intValue(object, "damage", 1),
                CustomShipJson.doubleValue(object, "projectileSpeed", 750.0),
                CustomShipJson.doubleValue(object, "range", 900.0),
                CustomShipJson.intValue(object, "projectileLife", 120),
                ref
        );
    }

    private static boolean isUnit(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return value.trim();
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
