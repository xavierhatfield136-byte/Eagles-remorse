import java.nio.file.Path;

public final class CustomShipRuntimeAdapter {
    private CustomShipRuntimeAdapter() {}

    public static void applyToShip(Ship ship, CustomShipDefinition definition, CustomShipRegistry registry) {
        applyToShip(ship, definition, registry, new CustomWeaponRegistry());
    }

    public static void applyToShip(Ship ship,
                                   CustomShipDefinition definition,
                                   CustomShipRegistry registry,
                                   CustomWeaponRegistry weaponRegistry) {
        if (ship == null || definition == null) return;
        CustomShipRegistry safeRegistry = registry == null ? new CustomShipRegistry() : registry;
        CustomWeaponRegistry safeWeaponRegistry = weaponRegistry == null ? new CustomWeaponRegistry() : weaponRegistry;
        if (!definition.validationFailures().isEmpty()) return;

        ship.customShipDefinitionId = definition.id;
        ship.customShipDefinition = definition;
        ship.customHullImagePath = safeRegistry.resolveContentPath(definition, definition.hullImagePath);
        ship.customThumbnailImagePath = safeRegistry.resolveContentPath(definition, definition.thumbnailImagePath);
        ship.name = definition.displayName == null || definition.displayName.isBlank()
                ? "Custom Ship"
                : definition.displayName;

        ship.radius = Math.max(6.0, definition.radius);
        ship.hpMax = Math.max(1, definition.hpMax);
        ship.hp = ship.hpMax;
        ship.shieldMax = Math.max(0.0, definition.shieldMax);
        ship.shield = ship.shieldMax;
        ship.shieldRegen = Math.max(0.0, definition.shieldRegen);
        ship.shieldActive = ship.shieldMax > 0.0;
        ship.desiredSpeed = Math.max(0.0, definition.desiredSpeed);
        ship.desiredSpeedBase = ship.desiredSpeed;
        ship.bountyValue = Math.max(0, (int) Math.round((ship.hpMax + ship.shieldMax) * 3.0));

        ship.turrets.clear();
        for (CustomWeaponMount mount : definition.weapons) {
            if (mount == null) continue;
            Turret turret = toTurret(mount, ship.radius, safeWeaponRegistry);
            ship.addTurret(turret);
        }
        ship.resetShieldState();
        ship.resetFlightDeckLoadout();
        ship.applyPrimaryWeaponFamily();
    }

    public static CustomShipDefinition loadForRef(ShipDefinitionRef ref, CustomShipRegistry registry) {
        if (!(ref instanceof ShipDefinitionRef.CustomShipRef customRef)) return null;
        CustomShipRegistry safeRegistry = registry == null ? new CustomShipRegistry() : registry;
        return safeRegistry.load(customRef.customShipId()).orElse(null);
    }

    private static Turret toTurret(CustomWeaponMount mount, double radius, CustomWeaponRegistry weaponRegistry) {
        double localX = normalizedToLocal(mount.normalizedX(), radius);
        double localY = normalizedToLocal(mount.normalizedY(), radius);
        Turret turret = new Turret(mount.weaponKind(), localX, localY);
        turret.cooldown = mount.cooldown();
        turret.damage = mount.damage();
        turret.primary = mount.weaponKind() == Turret.Kind.GUN;
        turret.radius = Math.max(3.0, Math.min(8.0, radius * 0.16));
        turret.barrelLen = Math.max(8.0, Math.min(22.0, radius * 0.42));
        if (mount.weaponKind() == Turret.Kind.MISSILE) {
            turret.missileSpeed = mount.projectileSpeed();
            turret.missileLife = mount.projectileLife();
            turret.primary = false;
        } else {
            turret.bulletSpeed = mount.projectileSpeed();
            turret.bulletLife = mount.projectileLife();
        }
        if (mount.weaponDefinitionRef() instanceof WeaponDefinitionRef.CustomWeaponRef customRef) {
            weaponRegistry.load(customRef.id())
                    .map(definition -> definition.toRuntimeProfile(weaponRegistry))
                    .ifPresent(profile -> {
                        turret.weaponProfile = profile;
                        turret.kind = Turret.Kind.GUN;
                        turret.primary = true;
                        turret.cooldown = profile.cooldownSeconds();
                        turret.damage = profile.damage();
                        turret.bulletSpeed = profile.projectileSpeedUnitsPerSecond();
                        turret.bulletLife = profile.projectileLifetimeFrames();
                    });
        }
        return turret;
    }

    private static double normalizedToLocal(double value, double radius) {
        return (MathUtil.clamp(value, 0.0, 1.0) - 0.5) * Math.max(1.0, radius * 2.0);
    }
}
