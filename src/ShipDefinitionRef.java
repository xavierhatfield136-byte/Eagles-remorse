import java.util.UUID;

public sealed interface ShipDefinitionRef permits ShipDefinitionRef.BuiltinShipRef, ShipDefinitionRef.CustomShipRef {
    ShipRole templateRole();

    default boolean isCustom() {
        return this instanceof CustomShipRef;
    }

    static ShipDefinitionRef builtin(ShipRole role) {
        return new BuiltinShipRef(role);
    }

    static ShipDefinitionRef custom(UUID customShipId, ShipRole templateRole) {
        return new CustomShipRef(customShipId, templateRole);
    }

    record BuiltinShipRef(ShipRole role) implements ShipDefinitionRef {
        public BuiltinShipRef {
            if (role == null) role = ShipRole.FRIGATE;
        }

        @Override
        public ShipRole templateRole() {
            return role;
        }
    }

    record CustomShipRef(UUID customShipId, ShipRole templateRole) implements ShipDefinitionRef {
        public CustomShipRef {
            if (customShipId == null) throw new IllegalArgumentException("customShipId is required");
            if (templateRole == null) templateRole = ShipRole.FRIGATE;
        }
    }
}
