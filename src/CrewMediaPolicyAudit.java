import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Release gate for the post-alpha prohibition on synthetic crew media. */
public final class CrewMediaPolicyAudit {
    private static final Path POLICY = Path.of("docs", "POST_ALPHA_CONTENT_MEDIA_POLICY.md");
    private static final Path PROVENANCE = Path.of("config", "crew_media_provenance.csv");
    private static final Path BASELINE = Path.of("config", "crew_media_legacy_baseline.csv");
    private static final List<Path> FROZEN_ROOTS = List.of(
            Path.of("assets", "crew_portraits"),
            Path.of("assets", "voice"));
    private static final Set<String> MEDIA_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".webp", ".gif",
            ".wav", ".ogg", ".mp3", ".mp4", ".webm", ".mov");

    private CrewMediaPolicyAudit() {}

    public record AuditResult(List<String> errors, List<String> warnings, int frozenAssetsChecked) {
        public boolean passed() { return errors.isEmpty(); }
    }

    public static AuditResult audit() {
        ArrayList<String> errors = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        requireText(POLICY, List.of(
                "must not create", "AI-generated crew face", "synthetic crew performance",
                "abstract teams", "complete written captions", "synthetic=false"), errors);
        requireText(PROVENANCE, List.of(
                "asset_path,category,origin,creator_or_performer,license,consent_reference,status,reviewer,synthetic",
                "legacy_alpha_unverified", "frozen_legacy_not_for_expansion"), errors);

        Map<String, String> expected = readBaseline(errors);
        int checked = 0;
        HashSet<String> seen = new HashSet<>();
        for (Path root : FROZEN_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    if (!isMedia(path)) continue;
                    String relative = normalized(path);
                    seen.add(relative);
                    checked++;
                    String expectedHash = expected.get(relative);
                    if (expectedHash == null) {
                        errors.add("Unregistered crew media added after the frozen alpha baseline: " + relative);
                    } else {
                        String actualHash = sha256(path, errors);
                        if (!expectedHash.equals(actualHash)) {
                            errors.add("Frozen legacy crew media changed without provenance review: " + relative);
                        }
                    }
                }
            } catch (IOException ex) {
                errors.add("Unable to inventory " + normalized(root) + ": " + ex.getMessage());
            }
        }
        for (String registered : expected.keySet()) {
            if (!seen.contains(registered)) {
                warnings.add("Frozen legacy asset is absent (removal is allowed, regeneration is not): " + registered);
            }
        }

        requireAbstractPersonnelSource(Path.of("src", "FlagshipOperationsSystem.java"), errors);
        requireAbstractPersonnelSource(Path.of("src", "BoardingRescueSystem.java"), errors);
        warnings.add("Legacy alpha portrait/TTS tooling remains quarantined; it is not an approved expansion workflow.");
        return new AuditResult(List.copyOf(errors), List.copyOf(warnings), checked);
    }

    private static void requireAbstractPersonnelSource(Path source, List<String> errors) {
        if (!Files.isRegularFile(source)) {
            errors.add("Missing abstract-personnel implementation: " + normalized(source));
            return;
        }
        try {
            String text = Files.readString(source, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (!(text.contains("team") && (text.contains("readiness") || text.contains("automation")
                    || text.contains("progress") || text.contains("casualties")))) {
                errors.add("Personnel implementation lacks abstract team/status state: " + normalized(source));
            }
            for (String forbidden : List.of("crewportraitsystem", "assets/crew_portraits", "text-to-speech", "talking-head")) {
                if (text.contains(forbidden)) errors.add("Forbidden crew-media dependency in " + normalized(source) + ": " + forbidden);
            }
        } catch (IOException ex) {
            errors.add("Unable to inspect " + normalized(source) + ": " + ex.getMessage());
        }
    }

    private static Map<String, String> readBaseline(List<String> errors) {
        HashMap<String, String> result = new HashMap<>();
        if (!Files.isRegularFile(BASELINE)) {
            errors.add("Missing frozen crew-media baseline: " + normalized(BASELINE));
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(BASELINE, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).replace("\"", "");
                String[] columns = line.split(",", -1);
                if (columns.length < 2) continue;
                result.put(columns[0].replace('\\', '/'), columns[1].toLowerCase(Locale.ROOT));
            }
        } catch (IOException ex) {
            errors.add("Unable to read frozen crew-media baseline: " + ex.getMessage());
        }
        return result;
    }

    private static void requireText(Path path, List<String> required, List<String> errors) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            for (String token : required) {
                if (!text.contains(token)) errors.add("Policy file " + normalized(path) + " is missing: " + token);
            }
        } catch (IOException ex) {
            errors.add("Missing policy file " + normalized(path) + ": " + ex.getMessage());
        }
    }

    private static boolean isMedia(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return MEDIA_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String sha256(Path path, List<String> errors) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException ex) {
            errors.add("Unable to hash " + normalized(path) + ": " + ex.getMessage());
            return "";
        }
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    public static void main(String[] args) {
        boolean strict = List.of(args).contains("--strict");
        AuditResult result = audit();
        result.warnings().forEach(message -> System.out.println("WARN: " + message));
        result.errors().forEach(message -> System.err.println("ERROR: " + message));
        System.out.println("Crew media policy audit: checked=" + result.frozenAssetsChecked()
                + " errors=" + result.errors().size() + " warnings=" + result.warnings().size());
        if (strict && !result.passed()) System.exit(1);
    }
}
