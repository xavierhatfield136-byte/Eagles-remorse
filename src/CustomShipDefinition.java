import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CustomShipDefinition {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public final UUID id;
    public final String displayName;
    public final String declaredShipClass;
    public final int schemaVersion;
    public final int generatorVersion;
    public final String hullImagePath;
    public final String thumbnailImagePath;
    public final CustomHullClass hullClass;
    public final CustomCombatClassification combatClassification;
    public final CustomWeaponDoctrine weaponDoctrine;
    public final CustomDefenseBias defenseBias;
    public final ShipRole balanceTemplate;
    public final double radius;
    public final int hpMax;
    public final double shieldMax;
    public final double shieldRegen;
    public final double desiredSpeed;
    public final List<CustomWeaponMount> weapons;
    public final String roomLayoutPreset;

    public CustomShipDefinition(
            UUID id,
            String displayName,
            String declaredShipClass,
            int schemaVersion,
            int generatorVersion,
            String hullImagePath,
            String thumbnailImagePath,
            CustomHullClass hullClass,
            CustomCombatClassification combatClassification,
            CustomWeaponDoctrine weaponDoctrine,
            CustomDefenseBias defenseBias,
            ShipRole balanceTemplate,
            double radius,
            int hpMax,
            double shieldMax,
            double shieldRegen,
            double desiredSpeed,
            List<CustomWeaponMount> weapons,
            String roomLayoutPreset
    ) {
        this.id = id;
        this.displayName = normalizeText(displayName);
        this.declaredShipClass = normalizeText(declaredShipClass);
        this.schemaVersion = schemaVersion;
        this.generatorVersion = generatorVersion;
        this.hullImagePath = normalizePathText(hullImagePath, "hull.png");
        this.thumbnailImagePath = normalizePathText(thumbnailImagePath, "thumbnail.png");
        this.hullClass = hullClass == null ? CustomHullClass.FRIGATE : hullClass;
        this.combatClassification = combatClassification == null ? CustomCombatClassification.LINE : combatClassification;
        this.weaponDoctrine = weaponDoctrine == null ? CustomWeaponDoctrine.BALANCED : weaponDoctrine;
        this.defenseBias = defenseBias == null ? CustomDefenseBias.BALANCED : defenseBias;
        this.balanceTemplate = balanceTemplate == null ? ShipRole.FRIGATE : balanceTemplate;
        this.radius = radius;
        this.hpMax = hpMax;
        this.shieldMax = shieldMax;
        this.shieldRegen = shieldRegen;
        this.desiredSpeed = desiredSpeed;
        this.weapons = List.copyOf(weapons == null ? List.of() : weapons);
        this.roomLayoutPreset = normalizeText(roomLayoutPreset);
    }

    public static CustomShipDefinition createDraft(
            String displayName,
            String declaredShipClass,
            CustomHullClass hullClass,
            CustomCombatClassification combatClassification,
            CustomWeaponDoctrine weaponDoctrine,
            CustomDefenseBias defenseBias,
            ShipRole balanceTemplate,
            List<CustomWeaponMount> weapons
    ) {
        ShipRole template = balanceTemplate == null ? ShipRole.FRIGATE : balanceTemplate;
        return new CustomShipDefinition(
                UUID.randomUUID(),
                displayName,
                declaredShipClass,
                CURRENT_SCHEMA_VERSION,
                1,
                "hull.png",
                "thumbnail.png",
                hullClass,
                combatClassification,
                weaponDoctrine,
                defenseBias,
                template,
                40.0,
                10,
                4.0,
                0.15,
                150.0,
                weapons,
                "standard"
        );
    }

    public List<String> validationFailures() {
        List<String> failures = new ArrayList<>();
        if (id == null) failures.add("id is required");
        if (displayName == null || displayName.isBlank()) failures.add("displayName is required");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) failures.add("unsupported schemaVersion " + schemaVersion);
        if (generatorVersion <= 0) failures.add("generatorVersion must be positive");
        if (!isSafeRelativePath(hullImagePath)) failures.add("hullImagePath must stay inside the custom ship folder");
        if (!isSafeRelativePath(thumbnailImagePath)) failures.add("thumbnailImagePath must stay inside the custom ship folder");
        if (radius <= 0.0 || Double.isNaN(radius) || Double.isInfinite(radius)) failures.add("radius must be positive");
        if (hpMax <= 0) failures.add("hpMax must be positive");
        if (shieldMax < 0.0 || Double.isNaN(shieldMax) || Double.isInfinite(shieldMax)) failures.add("shieldMax must be non-negative");
        if (shieldRegen < 0.0 || Double.isNaN(shieldRegen) || Double.isInfinite(shieldRegen)) failures.add("shieldRegen must be non-negative");
        if (desiredSpeed <= 0.0 || Double.isNaN(desiredSpeed) || Double.isInfinite(desiredSpeed)) failures.add("desiredSpeed must be positive");
        if (weapons.isEmpty()) failures.add("at least one weapon mount is required");
        for (CustomWeaponMount weapon : weapons) {
            if (weapon == null) failures.add("weapon mount cannot be null");
            else failures.addAll(weapon.validationFailures());
        }
        return failures;
    }

    public String toJson() {
        return CustomShipJson.stringify(toJsonObject());
    }

    public static CustomShipDefinition fromJson(String json) {
        Object parsed = CustomShipJson.parse(json);
        Map<String, Object> object = CustomShipJson.objectValue(parsed);
        if (object.isEmpty()) throw new IllegalArgumentException("Custom ship definition must be a JSON object");
        List<CustomWeaponMount> mounts = new ArrayList<>();
        for (Object item : CustomShipJson.arrayValue(object, "weapons")) {
            Map<String, Object> mountObject = CustomShipJson.objectValue(item);
            if (!mountObject.isEmpty()) mounts.add(CustomWeaponMount.fromJsonObject(mountObject));
        }
        String idText = CustomShipJson.stringValue(object, "id", "");
        UUID id = idText.isBlank() ? null : UUID.fromString(idText);
        return new CustomShipDefinition(
                id,
                CustomShipJson.stringValue(object, "displayName", ""),
                CustomShipJson.stringValue(object, "declaredShipClass", ""),
                CustomShipJson.intValue(object, "schemaVersion", 0),
                CustomShipJson.intValue(object, "generatorVersion", 0),
                CustomShipJson.stringValue(object, "hullImagePath", "hull.png"),
                CustomShipJson.stringValue(object, "thumbnailImagePath", "thumbnail.png"),
                enumValue(CustomHullClass.class, CustomShipJson.stringValue(object, "hullClass", "FRIGATE"), CustomHullClass.FRIGATE),
                enumValue(CustomCombatClassification.class, CustomShipJson.stringValue(object, "combatClassification", "LINE"), CustomCombatClassification.LINE),
                enumValue(CustomWeaponDoctrine.class, CustomShipJson.stringValue(object, "weaponDoctrine", "BALANCED"), CustomWeaponDoctrine.BALANCED),
                enumValue(CustomDefenseBias.class, CustomShipJson.stringValue(object, "defenseBias", "BALANCED"), CustomDefenseBias.BALANCED),
                enumValue(ShipRole.class, CustomShipJson.stringValue(object, "balanceTemplate", "FRIGATE"), ShipRole.FRIGATE),
                CustomShipJson.doubleValue(object, "radius", 0.0),
                CustomShipJson.intValue(object, "hpMax", 0),
                CustomShipJson.doubleValue(object, "shieldMax", 0.0),
                CustomShipJson.doubleValue(object, "shieldRegen", 0.0),
                CustomShipJson.doubleValue(object, "desiredSpeed", 0.0),
                mounts,
                CustomShipJson.stringValue(object, "roomLayoutPreset", "")
        );
    }

    Map<String, Object> toJsonObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("id", id == null ? "" : id.toString());
        object.put("displayName", displayName);
        object.put("declaredShipClass", declaredShipClass);
        object.put("schemaVersion", schemaVersion);
        object.put("generatorVersion", generatorVersion);
        object.put("hullImagePath", hullImagePath);
        object.put("thumbnailImagePath", thumbnailImagePath);
        object.put("hullClass", hullClass.name());
        object.put("combatClassification", combatClassification.name());
        object.put("weaponDoctrine", weaponDoctrine.name());
        object.put("defenseBias", defenseBias.name());
        object.put("balanceTemplate", balanceTemplate.name());
        object.put("radius", radius);
        object.put("hpMax", hpMax);
        object.put("shieldMax", shieldMax);
        object.put("shieldRegen", shieldRegen);
        object.put("desiredSpeed", desiredSpeed);
        object.put("weapons", weapons.stream().map(CustomWeaponMount::toJsonObject).toList());
        object.put("roomLayoutPreset", roomLayoutPreset);
        return object;
    }

    static boolean isSafeRelativePath(String pathText) {
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
                && path.getNameCount() > 0
                && path.toString().length() <= 180;
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return value.trim();
    }

    private static String normalizePathText(String value, String fallback) {
        String normalized = normalizeText(value);
        return normalized.isEmpty() ? fallback : normalized.replace('\\', '/');
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
