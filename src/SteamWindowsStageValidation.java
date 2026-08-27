import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Validates the exact Windows directory staged for Steam without requiring Steamworks access. */
public final class SteamWindowsStageValidation {
    private static final String APP_BUNDLE_NAME = "EaglesRemorse";

    private SteamWindowsStageValidation() {}

    public static void main(String[] args) throws Exception {
        boolean strict = hasArg(args, "--strict");
        Path stage = pathArg(args, "--stage=", Path.of("build", "steam", "content", APP_BUNDLE_NAME));
        Path manifest = pathArg(args, "--manifest=", Path.of("build", "steam", "SHA256SUMS-steam-windows.txt"));
        Path report = pathArg(args, "--report=", Path.of("build", "reports", "steam_windows_stage.json"));

        List<String> errors = validate(stage);
        int fileCount = errors.isEmpty() ? writeManifest(stage, manifest) : countFiles(stage);
        long bytes = directoryBytes(stage);
        writeReport(report, stage, manifest, errors, fileCount, bytes);

        System.out.println("[steam-stage] stage=" + stage.toAbsolutePath().normalize());
        System.out.println("[steam-stage] files=" + fileCount + " bytes=" + bytes + " pass=" + errors.isEmpty());
        if (errors.isEmpty()) System.out.println("[steam-stage] manifest=" + manifest.toAbsolutePath().normalize());
        for (String error : errors) System.out.println("[steam-stage] error=" + error);
        if (strict && !errors.isEmpty()) throw new IllegalStateException("Steam stage validation failed: " + errors);
    }

    public static List<String> validate(Path stage) throws IOException {
        List<String> errors = new ArrayList<>();
        Path root = stage.toAbsolutePath().normalize();
        requireFile(root.resolve(APP_BUNDLE_NAME + ".exe"), errors, "Windows launcher");
        requireFile(root.resolve(Path.of("runtime", "release")), errors, "bundled Java runtime metadata");
        requireFile(root.resolve(Path.of("runtime", "lib", "modules")), errors, "bundled Java runtime modules");
        requireFile(root.resolve(Path.of("runtime", "bin", "server", "jvm.dll")), errors, "bundled JVM");

        Path app = root.resolve("app");
        if (!Files.isDirectory(app)) {
            errors.add("application directory missing: " + app);
        } else {
            try (var files = Files.list(app)) {
                if (files.noneMatch(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
                    errors.add("application JAR missing under " + app);
                }
            }
        }

        if (Files.isDirectory(root)) {
            try (var paths = Files.walk(root)) {
                paths.forEach(path -> {
                    Path relative = root.relativize(path);
                    String normalized = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                    String first = relative.getNameCount() == 0 ? "" : relative.getName(0).toString().toLowerCase(Locale.ROOT);
                    if (List.of("src", "test", "docs", "config", ".git", ".idea", ".gradle", "build").contains(first)) {
                        errors.add("development directory must not ship: " + relative);
                    }
                    if (normalized.endsWith(".java") || normalized.endsWith(".gradle")
                            || normalized.endsWith(".iml") || normalized.endsWith(".psd")
                            || normalized.endsWith(".pdb") || normalized.endsWith(".log")
                            || normalized.endsWith(".tmp") || normalized.endsWith(".bak")) {
                        errors.add("development file must not ship: " + relative);
                    }
                    if (normalized.endsWith("steam_appid.txt")
                            || normalized.endsWith("loginusers.vdf")
                            || normalized.endsWith("config.vdf")) {
                        errors.add("credential/development Steam file must not ship: " + relative);
                    }
                });
            }
        } else {
            errors.add("Steam stage directory missing: " + root);
        }
        return errors.stream().distinct().sorted().toList();
    }

    private static int writeManifest(Path stage, Path manifest) throws Exception {
        Path root = stage.toAbsolutePath().normalize();
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        }
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        List<String> lines = new ArrayList<>(files.size() + 2);
        lines.add("# Eagles Remorse Steam Windows stage");
        lines.add("# Root: " + root);
        for (Path file : files) {
            sha256.reset();
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) sha256.update(buffer, 0, read);
                }
            }
            String relative = root.relativize(file).toString().replace('\\', '/');
            lines.add(HexFormat.of().formatHex(sha256.digest()) + "  " + relative);
        }
        Path parent = manifest.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(manifest, lines, StandardCharsets.UTF_8);
        return files.size();
    }

    private static int countFiles(Path stage) throws IOException {
        if (!Files.isDirectory(stage)) return 0;
        try (var paths = Files.walk(stage)) {
            return (int) paths.filter(Files::isRegularFile).count();
        }
    }

    private static long directoryBytes(Path stage) throws IOException {
        if (!Files.isDirectory(stage)) return 0L;
        try (var paths = Files.walk(stage)) {
            return paths.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException ignored) {
                    return 0L;
                }
            }).sum();
        }
    }

    private static void writeReport(Path report, Path stage, Path manifest, List<String> errors,
                                    int fileCount, long bytes) throws IOException {
        Path parent = report.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder errorJson = new StringBuilder("[");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) errorJson.append(", ");
            errorJson.append('"').append(escape(errors.get(i))).append('"');
        }
        errorJson.append(']');
        String json = "{\n"
                + "  \"generatedAt\": \"" + Instant.now() + "\",\n"
                + "  \"status\": \"" + (errors.isEmpty() ? "PASS" : "FAIL") + "\",\n"
                + "  \"stage\": \"" + escape(stage.toAbsolutePath().normalize().toString()) + "\",\n"
                + "  \"manifest\": \"" + escape(manifest.toAbsolutePath().normalize().toString()) + "\",\n"
                + "  \"fileCount\": " + fileCount + ",\n"
                + "  \"bytes\": " + bytes + ",\n"
                + "  \"errors\": " + errorJson + "\n"
                + "}\n";
        Files.writeString(report, json, StandardCharsets.UTF_8);
    }

    private static void requireFile(Path path, List<String> errors, String label) {
        if (!Files.isRegularFile(path)) errors.add(label + " missing: " + path);
    }

    private static boolean hasArg(String[] args, String expected) {
        for (String arg : args) if (expected.equalsIgnoreCase(arg)) return true;
        return false;
    }

    private static Path pathArg(String[] args, String prefix, Path fallback) {
        for (String arg : args) if (arg.startsWith(prefix)) return Path.of(arg.substring(prefix.length()));
        return fallback;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
