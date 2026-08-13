import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomWeaponRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void weaponDefinitionRoundTripsWithoutRegeneratingStats() throws Exception {
        CustomWeaponRegistry registry = new CustomWeaponRegistry(tempDir.resolve("custom_weapons"));
        CustomWeaponDefinition definition = sampleWeapon(UUID.randomUUID());

        registry.save(definition);
        CustomWeaponDefinition loaded = registry.load(definition.id).orElseThrow();

        assertEquals(definition.id, loaded.id);
        assertEquals(definition.displayName, loaded.displayName);
        assertEquals(definition.cooldownSeconds, loaded.cooldownSeconds);
        assertEquals(definition.damage, loaded.damage);
        assertEquals(definition.rangeUnits, loaded.rangeUnits);
        assertEquals(definition.projectileSpeedUnitsPerSecond, loaded.projectileSpeedUnitsPerSecond);
        assertEquals(definition.behavior, loaded.behavior);
        assertEquals(definition.balanceModelVersion, loaded.balanceModelVersion);
        assertEquals(definition.turretAsset, loaded.turretAsset);
        assertEquals(definition.projectileAsset, loaded.projectileAsset);
    }

    @Test
    void weaponCreationImportsSpritesAndKeepsThemLocal() throws Exception {
        Path turret = tempDir.resolve("turret.png");
        Path projectile = tempDir.resolve("projectile.png");
        writePng(turret, 12, 10);
        writePng(projectile, 8, 4);
        CustomWeaponRegistry registry = new CustomWeaponRegistry(tempDir.resolve("custom_weapons"));
        CustomWeaponCreationService service = new CustomWeaponCreationService(registry, new CustomWeaponAssetProcessor());

        CustomWeaponCreationService.CreationResult result = service.createFromPngs(turret, projectile,
                new CustomWeaponGenerationRequest("Local Laser", CustomWeaponFamily.ENERGY_BOLT,
                        CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE, CustomDamageProfile.SHIELD_PRESSURE,
                        CustomTargetProfile.GENERAL_PURPOSE, 0.6, 5, 920.0, 1500.0,
                        1, 0.0, 0.8, 1.1));

        assertNotNull(result.definition().id);
        assertTrue(result.folder().startsWith(registry.root()));
        assertTrue(Files.isRegularFile(result.folder().resolve("definition.json")));
        assertTrue(Files.isRegularFile(result.folder().resolve("turret.png")));
        assertTrue(Files.isRegularFile(result.folder().resolve("projectile.png")));
        assertTrue(Files.isRegularFile(result.folder().resolve("thumbnail.png")));
        assertEquals(1, service.savedWeapons().size());
        assertFalse(registry.missingContent(result.definition()).contains(result.folder().resolve("turret.png")));
    }

    @Test
    void weaponPathsCannotEscapeCustomWeaponRoot() {
        CustomWeaponDefinition unsafe = new CustomWeaponDefinition(
                UUID.randomUUID(),
                "Unsafe",
                CustomWeaponDefinition.CURRENT_SCHEMA_VERSION,
                CustomWeaponGenerator.GENERATOR_VERSION,
                CustomWeaponDefinition.CURRENT_BALANCE_MODEL_VERSION,
                "../turret.png",
                "projectile.png",
                "thumbnail.png",
                CustomWeaponFamily.KINETIC_CANNON,
                CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE,
                CustomDamageProfile.BALANCED,
                CustomTargetProfile.GENERAL_PURPOSE,
                1.0,
                4,
                800.0,
                1000.0,
                1.25,
                1,
                0.0,
                0.0,
                0.0,
                1.0,
                1.0,
                1.0,
                8.0,
                1.0,
                1.0);

        assertTrue(unsafe.validationFailures().stream().anyMatch(line -> line.contains("turretAsset")));
    }

    static CustomWeaponDefinition sampleWeapon(UUID id) {
        return new CustomWeaponDefinition(
                id,
                "Deterministic Cannon",
                CustomWeaponDefinition.CURRENT_SCHEMA_VERSION,
                CustomWeaponGenerator.GENERATOR_VERSION,
                CustomWeaponDefinition.CURRENT_BALANCE_MODEL_VERSION,
                "turret.png",
                "projectile.png",
                "thumbnail.png",
                CustomWeaponFamily.KINETIC_CANNON,
                CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE,
                CustomDamageProfile.ARMOR_PIERCING,
                CustomTargetProfile.GENERAL_PURPOSE,
                0.75,
                7,
                860.0,
                1400.0,
                1400.0 / 860.0,
                1,
                0.0,
                0.0,
                0.0,
                0.86,
                1.34,
                1.05,
                16.5,
                0.75,
                1.2);
    }

    static void writePng(Path path, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                image.setRGB(x, y, 0xffa8d8ff);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }
}
