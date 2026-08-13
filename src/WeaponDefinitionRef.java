import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public sealed interface WeaponDefinitionRef
        permits WeaponDefinitionRef.BuiltinWeaponRef, WeaponDefinitionRef.CustomWeaponRef {
    String type();

    default boolean isCustom() {
        return this instanceof CustomWeaponRef;
    }

    static WeaponDefinitionRef builtin(String id) {
        return new BuiltinWeaponRef(id);
    }

    static WeaponDefinitionRef custom(UUID id) {
        return new CustomWeaponRef(id);
    }

    static Optional<WeaponDefinitionRef> fromJsonObject(Map<String, Object> object) {
        if (object == null || object.isEmpty()) return Optional.empty();
        String type = CustomShipJson.stringValue(object, "type", "").trim().toLowerCase(Locale.ROOT);
        if ("custom".equals(type)) {
            String idText = CustomShipJson.stringValue(object, "id", "");
            try {
                return Optional.of(custom(UUID.fromString(idText)));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        if ("builtin".equals(type)) {
            String id = CustomShipJson.stringValue(object, "id", "");
            return id.isBlank() ? Optional.empty() : Optional.of(builtin(id));
        }
        return Optional.empty();
    }

    default Map<String, Object> toJsonObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", type());
        if (this instanceof BuiltinWeaponRef builtin) {
            object.put("id", builtin.id());
        } else if (this instanceof CustomWeaponRef custom) {
            object.put("id", custom.id() == null ? "" : custom.id().toString());
        }
        return object;
    }

    record BuiltinWeaponRef(String id) implements WeaponDefinitionRef {
        @Override
        public String type() {
            return "builtin";
        }
    }

    record CustomWeaponRef(UUID id) implements WeaponDefinitionRef {
        @Override
        public String type() {
            return "custom";
        }
    }
}
