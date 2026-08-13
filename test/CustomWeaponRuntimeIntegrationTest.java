import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomWeaponRuntimeIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void customShipMountCanResolveAndFireSavedCustomWeapon() throws Exception {
        CustomWeaponRegistry weaponRegistry = new CustomWeaponRegistry(tempDir.resolve("custom_weapons"));
        UUID weaponId = UUID.randomUUID();
        CustomWeaponDefinition weapon = CustomWeaponRegistryTest.sampleWeapon(weaponId);
        Path weaponFolder = weaponRegistry.folderFor(weaponId);
        java.nio.file.Files.createDirectories(weaponFolder);
        CustomWeaponRegistryTest.writePng(weaponFolder.resolve("turret.png"), 12, 10);
        CustomWeaponRegistryTest.writePng(weaponFolder.resolve("projectile.png"), 8, 4);
        CustomWeaponRegistryTest.writePng(weaponFolder.resolve("thumbnail.png"), 18, 10);
        weaponRegistry.save(weapon);

        CustomShipDefinition shipDefinition = new CustomShipDefinition(
                UUID.randomUUID(),
                "Armed Local Ship",
                "Line Frigate",
                CustomShipDefinition.CURRENT_SCHEMA_VERSION,
                CustomShipGenerator.GENERATOR_VERSION,
                "hull.png",
                "thumbnail.png",
                CustomHullClass.FRIGATE,
                CustomCombatClassification.LINE,
                CustomWeaponDoctrine.GUNSHIP,
                CustomDefenseBias.BALANCED,
                ShipRole.FRIGATE,
                24.0,
                30,
                12.0,
                0.5,
                140.0,
                List.of(new CustomWeaponMount("custom", 0.62, 0.50, Turret.Kind.GUN,
                        1.0, 2, 700.0, 900.0, 90, WeaponDefinitionRef.custom(weaponId))),
                "standard");
        CustomShipRegistry shipRegistry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        Path shipFolder = shipRegistry.folderFor(shipDefinition.id);
        java.nio.file.Files.createDirectories(shipFolder);
        CustomWeaponRegistryTest.writePng(shipFolder.resolve("hull.png"), 20, 10);
        CustomWeaponRegistryTest.writePng(shipFolder.resolve("thumbnail.png"), 20, 10);

        FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_E, 100.0, 100.0);
        CustomShipRuntimeAdapter.applyToShip(ship, shipDefinition, shipRegistry, weaponRegistry);
        FleetShip target = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 400.0, 100.0);
        Turret turret = ship.turrets.getFirst();
        turret.angle = 0.0;

        Projectile projectile = turret.fire(ship, target, GameContext.DT);

        assertNotNull(turret.weaponProfile);
        assertEquals(weaponId, turret.weaponProfile.id());
        assertInstanceOf(CustomProjectile.class, projectile);
        assertTrue(projectile.damage >= weapon.damage);
        assertEquals(weaponId, ((CustomProjectile) projectile).customWeaponId);
    }
}
