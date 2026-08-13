import app.support.UserDataPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class CustomWeaponRegistry {
    public static final String DIRECTORY_NAME = "custom_weapons";
    public static final String DEFINITION_FILE = "definition.json";

    private final Path root;

    public CustomWeaponRegistry() {
        this(UserDataPaths.saveDir().resolve(DIRECTORY_NAME));
    }

    public CustomWeaponRegistry(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public void ensureRoot() throws IOException {
        Files.createDirectories(root);
    }

    public Path folderFor(UUID id) {
        if (id == null) throw new IllegalArgumentException("Custom weapon id is required");
        return resolveInsideRoot(id.toString());
    }

    public Path definitionPath(UUID id) {
        return folderFor(id).resolve(DEFINITION_FILE);
    }

    public List<String> validationFailures(CustomWeaponDefinition definition) {
        if (definition == null) return List.of("definition is required");
        return definition.validationFailures();
    }

    public void save(CustomWeaponDefinition definition) throws IOException {
        List<String> failures = validationFailures(definition);
        if (!failures.isEmpty()) throw new IllegalArgumentException(String.join("; ", failures));
        ensureRoot();
        Path folder = folderFor(definition.id);
        Files.createDirectories(folder);
        Path definitionFile = folder.resolve(DEFINITION_FILE);
        Path tempFile = folder.resolve(DEFINITION_FILE + ".tmp");
        Files.writeString(tempFile, definition.toJson(), StandardCharsets.UTF_8);
        try {
            Files.move(tempFile, definitionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, definitionFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Optional<CustomWeaponDefinition> load(UUID id) {
        try {
            Path definitionFile = definitionPath(id);
            if (!Files.isRegularFile(definitionFile)) return Optional.empty();
            CustomWeaponDefinition definition = CustomWeaponDefinition.fromJson(Files.readString(definitionFile, StandardCharsets.UTF_8));
            if (!validationFailures(definition).isEmpty()) return Optional.empty();
            return Optional.of(definition);
        } catch (RuntimeException | IOException ex) {
            return Optional.empty();
        }
    }

    public List<CustomWeaponDefinition> loadAll() throws IOException {
        ensureRoot();
        try (Stream<Path> paths = Files.list(root)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(path -> parseUuid(path.getFileName().toString()))
                    .flatMap(Optional::stream)
                    .map(this::load)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(def -> def.displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public List<Path> missingContent(CustomWeaponDefinition definition) {
        if (definition == null || definition.id == null) return List.of();
        Path folder = folderFor(definition.id);
        return Stream.of(definition.turretAsset, definition.projectileAsset, definition.thumbnailAsset)
                .filter(CustomWeaponDefinition::isSafeAssetName)
                .map(folder::resolve)
                .filter(path -> !Files.isRegularFile(path))
                .toList();
    }

    public boolean delete(UUID id) throws IOException {
        Path folder = folderFor(id);
        if (!Files.exists(folder)) return false;
        if (!folder.startsWith(root)) throw new IOException("Refusing to delete outside custom weapon root: " + folder);
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
        return true;
    }

    public Path resolveContentPath(CustomWeaponDefinition definition, String assetName) {
        if (definition == null || definition.id == null) throw new IllegalArgumentException("Custom weapon definition id is required");
        if (!CustomWeaponDefinition.isSafeAssetName(assetName)) {
            throw new IllegalArgumentException("Custom weapon asset must be a file inside the weapon folder");
        }
        Path folder = folderFor(definition.id);
        Path resolved = folder.resolve(assetName).normalize();
        if (!resolved.startsWith(folder)) {
            throw new IllegalArgumentException("Custom weapon content path escaped the weapon folder");
        }
        return resolved;
    }

    private Path resolveInsideRoot(String child) {
        Path resolved = root.resolve(child).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Custom weapon path escaped root");
        }
        return resolved;
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
