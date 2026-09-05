import app.ui.ThemeArt;

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

        validateThemeArt(errors, report);
        validateSfxManifest(errors, report);
        validateVoiceManifest(errors, report);
        validateScreenshotTargets(errors, report);
        validateExtractionArtifacts(resolvedRoot, errors, report);
        validateAlphaPlaceholderReport(resolvedRoot, assetFiles, report);

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

    private static void validateThemeArt(List<String> errors, List<String> report) {
        String[] requiredSlots = {
                ThemeArt.MENU_MAIN_SHELL,
                ThemeArt.MENU_SECTION_PANEL,
                ThemeArt.MENU_INSET_PANEL
        };
        String[] fallbackSlots = {
                ThemeArt.HUD_STANDARD_PANEL,
                ThemeArt.HUD_ALERT_PANEL,
                ThemeArt.HUD_STATUS_STRIP,
                ThemeArt.HUD_SPECIAL_FRAME,
                ThemeArt.HUD_RADAR_RING
        };
        int present = 0;
        for (String slot : requiredSlots) {
            boolean ok = ThemeArt.get(slot) != null;
            if (ok) present++;
            else errors.add("ui-theme: missing asset for " + slot);
        }
        int fallbackPresent = 0;
        for (String slot : fallbackSlots) {
            if (ThemeArt.get(slot) != null) fallbackPresent++;
        }
        report.add("ui-theme menu slots=" + present + "/" + requiredSlots.length
                + " hud overrides=" + fallbackPresent + "/" + fallbackSlots.length + " (fallback allowed)");
    }

    private static void validateSfxManifest(List<String> errors, List<String> report) {
        SfxManifest.CoverageReport coverage = SfxManifest.coverage();
        report.add("sfx events=" + coverage.okCount() + "/" + coverage.rows().size());
        for (SfxManifest.CoverageRow row : coverage.rows()) {
            if (!row.ok()) {
                errors.add("sfx: " + row.spec().eventId() + " has " + row.assetVariants()
                        + " variants, needs " + row.spec().requiredVariants());
            }
        }
    }

    private static void validateVoiceManifest(List<String> errors, List<String> report) {
        List<AudioSystem.VoiceEventSpec> rows = AudioSystem.voiceEventMatrix();
        if (rows.isEmpty()) {
            report.add("voice acting=removed");
            return;
        }
        int assetsOk = 0;
        int captionsOk = 0;
        for (AudioSystem.VoiceEventSpec row : rows) {
            if (row.assetVariants() >= row.requiredVariants()) assetsOk++;
            else errors.add("voice: " + row.role() + "/" + row.eventId() + " has " + row.assetVariants()
                    + " variants, needs " + row.requiredVariants());
            if (row.captionVariants() >= 1) captionsOk++;
            else errors.add("voice: " + row.role() + "/" + row.eventId() + " has no caption variant");
        }
        report.add("voice assets=" + assetsOk + "/" + rows.size());
        report.add("voice captions=" + captionsOk + "/" + rows.size());
    }

    private static void validateScreenshotTargets(List<String> errors, List<String> report) {
        ProductionReadinessLongevitySystem.State state = ProductionReadinessLongevitySystem.bootstrap(0L);
        List<String> targets = state.art.regressionScreenshots;
        report.add("screenshot targets=" + targets.size() + " -> " + String.join(",", targets));
        if (targets.size() < 5) errors.add("screenshots: fewer than five production targets");
        for (String target : targets) {
            if (target == null || target.isBlank()) {
                errors.add("screenshots: blank target");
            }
        }
        if (!targets.contains("campaign-map")) errors.add("screenshots: missing campaign-map target");
        if (!targets.contains("tactical-hud")) errors.add("screenshots: missing tactical-hud target");
        if (!targets.contains("accessibility-hud")) errors.add("screenshots: missing accessibility-hud target");
    }

    private static void validateExtractionArtifacts(Path root, List<String> errors, List<String> report) {
        StretchGoalsFleetDoctrineSystem.State state = StretchGoalsFleetDoctrineSystem.bootstrap(0L);
        int present = 0;
        for (StretchGoalsFleetDoctrineSystem.ExtractionPack pack : state.extractionPacks) {
            Path artifact = root.resolve(pack.artifact).normalize();
            if (Files.isRegularFile(artifact)) present++;
            else errors.add("extraction-pack: missing artifact " + pack.artifact);
        }
        report.add("extraction-pack artifacts=" + present + "/" + state.extractionPacks.size());
    }

    private static void validateAlphaPlaceholderReport(Path root, List<Path> assetFiles, List<String> report) throws IOException {
        Path alphaReport = root.resolve("docs/ALPHA_ASSET_APPROVAL_REPORT.md").normalize();
        int ownerReviewOpen = 0;
        if (Files.isRegularFile(alphaReport)) {
            for (String line : Files.readAllLines(alphaReport, StandardCharsets.UTF_8)) {
                if (line.trim().startsWith("- [ ]")) ownerReviewOpen++;
            }
        }

        long dropzoneAssets = assetFiles.stream()
                .filter(ProductionValidationHarness::isArt)
                .filter(path -> path.toString().replace('\\', '/').contains("environment_overhaul_dropzone/"))
                .count();
        long originalHudAssets = assetFiles.stream()
                .filter(ProductionValidationHarness::isArt)
                .filter(path -> path.toString().replace('\\', '/').contains("hud_panels/originals/"))
                .count();

        report.add("alpha-placeholder owner-review-open=" + ownerReviewOpen);
        report.add("alpha-placeholder environment-dropzone-art=" + dropzoneAssets);
        report.add("alpha-placeholder hud-originals-art=" + originalHudAssets);
        report.add("alpha-placeholder decision-doc="
                + (Files.isRegularFile(root.resolve("docs/PRODUCTION_COMPLETION_AUDIT.md").normalize()) ? "present" : "missing"));
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
