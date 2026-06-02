import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Executable local production-data audit used by the Gradle verification task. */
public final class ProductionValidationHarness {
    private static final Path REPORT = Path.of("build", "reports", "production-validation.txt");

    private ProductionValidationHarness() {}

    public static void main(String[] args) throws Exception {
        List<String> errors = validate(Path.of("."));
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production validation failed:\n - " + String.join("\n - ", errors));
        }
    }

    public static List<String> validate(Path root) throws IOException {
        Path resolvedRoot = (root == null) ? Path.of(".") : root;
        List<String> errors = new ArrayList<>();
        List<String> report = new ArrayList<>();

        CommunityContentSystem.State content = CommunityContentSystem.bootstrap(0L);
        List<String> contentErrors = CommunityContentSystem.loadContentPack(content, resolvedRoot);
        errors.addAll(contentErrors);
        report.add("content-pack rows=" + loadedRowCount(content) + " errors=" + contentErrors.size());

        Path assets = resolvedRoot.resolve("assets").normalize();
        List<Path> assetFiles = regularFiles(assets);
        long artFiles = assetFiles.stream().filter(ProductionValidationHarness::isArt).count();
        long audioFiles = assetFiles.stream().filter(ProductionValidationHarness::isAudio).count();
        report.add("art files=" + artFiles);
        report.add("audio files=" + audioFiles);
        if (artFiles == 0) errors.add("assets: no art files found");
        if (audioFiles == 0) errors.add("assets: no audio files found");

        Map<String, List<String>> duplicates = duplicateNames(assetFiles);
        report.add("duplicate lowercase file names=" + duplicates.size());
        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            report.add("duplicate " + entry.getKey() + " -> " + String.join(" | ", entry.getValue()));
        }

        requireRegularFile(resolvedRoot, "docs/CAMPAIGN_SAVE_SCHEMA.md", errors, report);
        requireRegularFile(resolvedRoot, "config/balance_data_export.csv", errors, report);
        requireRegularFile(resolvedRoot, "config/content-pack/manifest.properties", errors, report);

        Files.createDirectories(REPORT.getParent());
        List<String> output = new ArrayList<>();
        output.add("Production validation report");
        output.addAll(report);
        output.add("errors=" + errors.size());
        output.addAll(errors);
        Files.write(REPORT, output, StandardCharsets.UTF_8);
        return List.copyOf(errors);
    }

    private static void requireRegularFile(Path root, String relative, List<String> errors, List<String> report) {
        Path file = root.resolve(relative).normalize();
        boolean present = Files.isRegularFile(file);
        report.add(relative + "=" + (present ? "present" : "missing"));
        if (!present) errors.add(relative + ": file is missing");
    }

    private static int loadedRowCount(CommunityContentSystem.State state) {
        int count = 0;
        for (List<CommunityContentSystem.DefinitionRow> rows : state.contentPack.loadedDefinitions.values()) {
            count += rows.size();
        }
        return count;
    }

    private static List<Path> regularFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private static boolean isArt(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static boolean isAudio(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".mp3");
    }

    private static Map<String, List<String>> duplicateNames(List<Path> files) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Path file : files) {
            String key = file.getFileName().toString().toLowerCase(Locale.ROOT);
            byName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(file.toString());
        }
        byName.entrySet().removeIf(entry -> entry.getValue().size() < 2);
        return byName;
    }
}
