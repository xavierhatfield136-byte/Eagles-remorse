import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomShipRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void savedDefinitionsRoundTripByUuidWithoutDependingOnImportPath() throws Exception {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition original = sampleDefinition(UUID.randomUUID(), "Ashen Linebreaker");

        registry.save(original);

        Optional<CustomShipDefinition> loaded = registry.load(original.id);
        assertTrue(loaded.isPresent());
        assertEquals(original.id, loaded.get().id);
        assertEquals("Ashen Linebreaker", loaded.get().displayName);
        assertEquals("Frigate", loaded.get().declaredShipClass);
        assertEquals("hull.png", loaded.get().hullImagePath);
        assertEquals(CustomWeaponDoctrine.GUNSHIP, loaded.get().weaponDoctrine);
        assertEquals(CustomDefenseBias.SHIELD_HEAVY, loaded.get().defenseBias);
        assertEquals(2, loaded.get().weapons.size());
    }

    @Test
    void duplicateDisplayNamesAreAllowedBecauseUuidIsIdentity() throws Exception {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition first = sampleDefinition(UUID.randomUUID(), "My Cruiser");
        CustomShipDefinition second = sampleDefinition(UUID.randomUUID(), "My Cruiser");

        registry.save(first);
        registry.save(second);

        List<CustomShipDefinition> definitions = registry.loadAll();
        assertEquals(2, definitions.size());
        assertTrue(definitions.stream().map(def -> def.id).distinct().count() == 2);
    }

    @Test
    void unsafeContentPathsCannotEscapeCustomShipFolder() {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition unsafe = new CustomShipDefinition(
                UUID.randomUUID(),
                "Escaper",
                "Cruiser",
                CustomShipDefinition.CURRENT_SCHEMA_VERSION,
                1,
                "../outside.png",
                "thumbnail.png",
                CustomHullClass.CRUISER,
                CustomCombatClassification.LINE,
                CustomWeaponDoctrine.BALANCED,
                CustomDefenseBias.BALANCED,
                ShipRole.CRUISER,
                50.0,
                12,
                4.0,
                0.1,
                120.0,
                List.of(sampleMount("forward")),
                "standard"
        );

        assertThrows(IllegalArgumentException.class, () -> registry.save(unsafe));
        assertThrows(IllegalArgumentException.class, () -> registry.resolveContentPath(unsafe, "../outside.png"));
    }

    @Test
    void malformedAndOldSchemaDefinitionsAreSkippedDuringLoadAll() throws Exception {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition valid = sampleDefinition(UUID.randomUUID(), "Valid Ship");
        registry.save(valid);

        writeDefinition(registry.root().resolve(UUID.randomUUID().toString()), "{ this is not json");
        CustomShipDefinition oldSchema = new CustomShipDefinition(
                UUID.randomUUID(),
                "Old Ship",
                "Frigate",
                0,
                1,
                "hull.png",
                "thumbnail.png",
                CustomHullClass.FRIGATE,
                CustomCombatClassification.LINE,
                CustomWeaponDoctrine.BALANCED,
                CustomDefenseBias.BALANCED,
                ShipRole.FRIGATE,
                40.0,
                10,
                4.0,
                0.1,
                150.0,
                List.of(sampleMount("forward")),
                "standard"
        );
        writeDefinition(registry.root().resolve(oldSchema.id.toString()), oldSchema.toJson());

        List<CustomShipDefinition> definitions = registry.loadAll();
        assertEquals(1, definitions.size());
        assertEquals(valid.id, definitions.getFirst().id);
    }

    @Test
    void registryCreatesMissingRootAndReportsMissingGeneratedAssets() throws Exception {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("missing").resolve("custom_ships"));
        CustomShipDefinition definition = sampleDefinition(UUID.randomUUID(), "No Assets Yet");

        registry.save(definition);

        assertTrue(Files.isDirectory(registry.root()));
        List<Path> missing = registry.missingContent(definition);
        assertEquals(2, missing.size());
        assertTrue(missing.stream().anyMatch(path -> path.endsWith("hull.png")));
        assertTrue(missing.stream().anyMatch(path -> path.endsWith("thumbnail.png")));
    }

    @Test
    void customShipRuntimeFoldersStayIgnoredByGit() throws IOException {
        String gitignore = Files.readString(Path.of(".gitignore"), StandardCharsets.UTF_8);

        assertTrue(gitignore.contains("save/custom_ships/"));
        assertTrue(gitignore.contains("custom_ships/"));
        assertFalse(gitignore.contains("assets/custom_ships/"));
    }

    @Test
    void imageProcessorCropsTransparentMarginsAndWritesLocalAssets() throws Exception {
        Path source = tempDir.resolve("import.png");
        BufferedImage image = new BufferedImage(12, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y <= 6; y++) {
            for (int x = 2; x <= 8; x++) {
                image.setRGB(x, y, 0xff6ac8ff);
            }
        }
        ImageIO.write(image, "png", source.toFile());
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition definition = sampleDefinition(UUID.randomUUID(), "Imported Ship");

        CustomShipImageProcessor.ProcessedImage processed =
                new CustomShipImageProcessor().processPng(source, definition, registry);

        assertEquals(7, processed.hullWidth());
        assertEquals(4, processed.hullHeight());
        assertTrue(Files.isRegularFile(registry.resolveContentPath(definition, "hull.png")));
        assertTrue(Files.isRegularFile(registry.resolveContentPath(definition, "thumbnail.png")));
        BufferedImage hull = ImageIO.read(registry.resolveContentPath(definition, "hull.png").toFile());
        assertEquals(7, hull.getWidth());
        assertEquals(4, hull.getHeight());
        assertEquals(0xff, (hull.getRGB(0, 0) >>> 24) & 0xff);
    }

    @Test
    void imageProcessorRejectsNonPngAndInvisibleImages() throws Exception {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition definition = sampleDefinition(UUID.randomUUID(), "Bad Import");
        CustomShipImageProcessor processor = new CustomShipImageProcessor();
        Path jpgNamedFile = tempDir.resolve("ship.jpg");
        Files.writeString(jpgNamedFile, "not used", StandardCharsets.UTF_8);
        Path invisiblePng = tempDir.resolve("invisible.png");
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", invisiblePng.toFile());

        assertThrows(IllegalArgumentException.class, () -> processor.processPng(jpgNamedFile, definition, registry));
        assertThrows(IllegalArgumentException.class, () -> processor.processPng(invisiblePng, definition, registry));
    }

    private static CustomShipDefinition sampleDefinition(UUID id, String displayName) {
        return new CustomShipDefinition(
                id,
                displayName,
                "Frigate",
                CustomShipDefinition.CURRENT_SCHEMA_VERSION,
                1,
                "hull.png",
                "thumbnail.png",
                CustomHullClass.FRIGATE,
                CustomCombatClassification.LINE,
                CustomWeaponDoctrine.GUNSHIP,
                CustomDefenseBias.SHIELD_HEAVY,
                ShipRole.FRIGATE,
                42.0,
                16,
                8.0,
                0.2,
                160.0,
                List.of(sampleMount("forward-left"), sampleMount("forward-right")),
                "standard"
        );
    }

    private static CustomWeaponMount sampleMount(String id) {
        return new CustomWeaponMount(
                id,
                id.endsWith("right") ? 0.62 : 0.38,
                0.44,
                Turret.Kind.GUN,
                1.0,
                3,
                750.0,
                900.0,
                120
        );
    }

    private static void writeDefinition(Path folder, String json) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(CustomShipRegistry.DEFINITION_FILE), json, StandardCharsets.UTF_8);
    }
}
