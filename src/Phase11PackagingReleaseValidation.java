import app.support.AppInfo;
import app.support.UserDataPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * Phase 11 packaged-build and release-material validation.
 */
public final class Phase11PackagingReleaseValidation {
    public record DistributionChannel(String id, String artifact, String status, String notes) {}
    public record CleanMachineStep(String id, String evidence) {}

    private static final String APP_BUNDLE_NAME = "EaglesRemorse";
    private static final String APP_DISPLAY_NAME = "Eagles Remorse";
    private static final Path REPORT = Path.of("build", "reports", "phase11_packaging_release_validation.json");

    private Phase11PackagingReleaseValidation() {}

    public static List<DistributionChannel> distributionChannels() {
        return List.of(
                new DistributionChannel("itch", APP_BUNDLE_NAME + "-<version>.zip", "prepared",
                        "Portable ZIP is the canonical itch.io upload; butler may push the unpacked app-image directory."),
                new DistributionChannel("github", APP_BUNDLE_NAME + "-<version>.zip/.exe + Linux .tar.gz", "prepared",
                        "GitHub Actions builds native Windows and Linux artifacts and attaches them to releases."),
                new DistributionChannel("private", APP_BUNDLE_NAME + "-<version> platform archive", "prepared",
                        "Private distribution uses the appropriate platform archive plus its SHA256SUMS file."),
                new DistributionChannel("steam", "deferred", "investigated",
                        "Steam requires Steamworks onboarding, store/build review, app/depot setup, and SteamPipe upload scripts.")
        );
    }

    public static List<CleanMachineStep> cleanMachineSteps() {
        return List.of(
                new CleanMachineStep("install-no-jdk", "jpackage app-image includes a Java 21 runtime."),
                new CleanMachineStep("shortcut", "EXE task enables --win-menu and --win-shortcut when WiX is installed."),
                new CleanMachineStep("portable-zip", "Portable ZIP contains the app launcher, app JAR, and bundled runtime."),
                new CleanMachineStep("start-campaign", "Packaged launch uses Main and AppInfo metadata from the JAR manifest/resources."),
                new CleanMachineStep("save", "Runtime saves resolve through UserDataPaths.saveDir()."),
                new CleanMachineStep("exit", "Menu exit persists checkpoint through the user data directory."),
                new CleanMachineStep("relaunch-load", "Resume reads the same user data checkpoint path."),
                new CleanMachineStep("enter-tactical", "Campaign/tactical resources are bundled as JAR resources."),
                new CleanMachineStep("complete-mission", "Packaged smoke launch reaches the same Main entry point and resources."),
                new CleanMachineStep("writable-data", "Logs and saves use APPDATA/Application Support/XDG user paths."),
                new CleanMachineStep("uninstall-preserves-saves", "Runtime data is outside the install/app-image directory.")
        );
    }

    public static List<String> validateProjectContract(Path root) throws IOException {
        Path base = root == null ? Path.of(".") : root;
        List<String> errors = new ArrayList<>();

        requireFile(base.resolve("VERSION"), errors, "VERSION");
        requireFile(base.resolve("README.md"), errors, "README.md");
        requireFile(base.resolve("LICENSE.md"), errors, "LICENSE.md");
        String version = readVersion(base);
        requireFile(base.resolve("docs/release/RELEASE_NOTES_" + version + ".md"), errors, "release notes");
        requireFile(base.resolve("docs/release/SYSTEM_REQUIREMENTS.md"), errors, "system requirements");
        requireFile(base.resolve("docs/release/KNOWN_ISSUES.md"), errors, "known issues");
        requireFile(base.resolve("docs/release/SAVE_COMPATIBILITY_POLICY.md"), errors, "save compatibility policy");
        requireFile(base.resolve("docs/release/DISTRIBUTION_CHANNELS.md"), errors, "distribution channel plan");
        requireFile(base.resolve(".github/workflows/windows-package.yml"), errors, "GitHub Windows package workflow");
        requireFile(base.resolve(".github/workflows/linux-package.yml"), errors, "GitHub Linux package workflow");

        String build = Files.readString(base.resolve("build.gradle"), StandardCharsets.UTF_8);
        contains(build, "JavaLanguageVersion.of(21)", errors, "Gradle must target Java 21");
        contains(build, "packageWindowsAppImage", errors, "app-image task missing");
        contains(build, "packageWindowsZip", errors, "portable ZIP task missing");
        contains(build, "packageWindowsExe", errors, "EXE installer task missing");
        contains(build, "packageLinuxAppImage", errors, "Linux app-image task missing");
        contains(build, "packageLinuxTar", errors, "Linux portable tarball task missing");
        contains(build, "--win-menu", errors, "installer menu shortcut option missing");
        contains(build, "--win-shortcut", errors, "installer desktop shortcut option missing");
        contains(build, "hasWixTools", errors, "WiX detection missing");
        contains(build, "exclude \"ai_pipeline/**\"", errors, "generation asset exclude missing");

        if (!APP_DISPLAY_NAME.equals(AppInfo.APP_NAME)) errors.add("Unexpected AppInfo.APP_NAME: " + AppInfo.APP_NAME);
        if (AppInfo.VERSION == null || AppInfo.VERSION.isBlank()) errors.add("AppInfo.VERSION is blank");
        if (!UserDataPaths.saveDir().startsWith(UserDataPaths.root())) errors.add("saveDir is outside user data root");
        if (!UserDataPaths.logDir().startsWith(UserDataPaths.root())) errors.add("logDir is outside user data root");
        boolean explicitTestOrDevOverride = System.getProperty("game.userDataDir") != null;
        if (!explicitTestOrDevOverride
                && UserDataPaths.root().toAbsolutePath().normalize().startsWith(base.toAbsolutePath().normalize())) {
            errors.add("user data root points inside the source/install tree");
        }

        if (distributionChannels().size() != 4) errors.add("distribution channel count mismatch");
        if (cleanMachineSteps().size() != 11) errors.add("clean-machine step count mismatch");
        return errors;
    }

    public static List<String> validateBuiltArtifacts(Path root) throws Exception {
        return validateBuiltArtifacts(root, isWindows() ? "windows" : "linux");
    }

    public static List<String> validateBuiltArtifacts(Path root, String platform) throws Exception {
        Path base = root == null ? Path.of(".") : root;
        List<String> errors = new ArrayList<>(validateProjectContract(base));
        String version = readVersion(base);
        boolean windows = "windows".equalsIgnoreCase(platform);
        Path packageDir = base.resolve(Path.of("build", "package", windows ? "windows" : "linux")).normalize();
        Path appImage = packageDir.resolve(APP_BUNDLE_NAME);
        Path archive = packageDir.resolve(windows
                ? APP_BUNDLE_NAME + "-" + version + "-windows-x64-full.zip"
                : APP_BUNDLE_NAME + "-" + version + "-linux-x64.tar.gz");

        if (!Files.isDirectory(appImage)) errors.add("app image missing: " + appImage);
        if (!Files.isRegularFile(archive)) errors.add("portable archive missing: " + archive);

        Path appDir = appImage.resolve(windows ? "app" : Path.of("lib", "app").toString());
        Path runtimeDir = appImage.resolve(windows ? "runtime" : Path.of("lib", "runtime").toString());
        Path jar = findFirst(appDir, ".jar");
        if (jar == null) errors.add("packaged app JAR missing under " + appDir);
        else validateJar(jar, version, errors);

        Path runtimeRelease = runtimeDir.resolve("release");
        Path runtimeModules = runtimeDir.resolve(Path.of("lib", "modules"));
        Path runtimeJvm = windows
                ? runtimeDir.resolve(Path.of("bin", "server", "jvm.dll"))
                : runtimeDir.resolve(Path.of("lib", "server", "libjvm.so"));
        if (!Files.isRegularFile(runtimeRelease)) errors.add("bundled Java runtime release metadata missing: " + runtimeRelease);
        if (!Files.isRegularFile(runtimeModules)) errors.add("bundled Java runtime modules missing: " + runtimeModules);
        if (!Files.isRegularFile(runtimeJvm)) errors.add("bundled Java runtime JVM missing: " + runtimeJvm);
        Path launcher = appImage.resolve(windows ? APP_BUNDLE_NAME + ".exe" : Path.of("bin", APP_BUNDLE_NAME).toString());
        if (!Files.isRegularFile(launcher)) errors.add("app launcher missing: " + launcher);

        if (windows && Files.isRegularFile(archive)) validateZip(archive, errors);
        writeChecksums(packageDir, windows ? "SHA256SUMS-windows.txt" : "SHA256SUMS-linux.txt");
        return errors;
    }

    public static void main(String[] args) throws Exception {
        boolean strict = false;
        boolean built = false;
        String platform = isWindows() ? "windows" : "linux";
        Path report = REPORT;
        for (String arg : args) {
            if ("--strict".equalsIgnoreCase(arg)) strict = true;
            else if ("--built".equalsIgnoreCase(arg)) built = true;
            else if (arg.startsWith("--platform=")) platform = arg.substring("--platform=".length());
            else if (arg.startsWith("--report=")) report = Path.of(arg.substring("--report=".length()));
        }
        List<String> errors = built ? validateBuiltArtifacts(Path.of("."), platform) : validateProjectContract(Path.of("."));
        writeReport(report, errors, built);
        System.out.println("[phase11-packaging] built=" + built
                + " channels=" + distributionChannels().size()
                + " cleanMachineSteps=" + cleanMachineSteps().size()
                + " pass=" + errors.isEmpty());
        for (String error : errors) System.out.println("[phase11-packaging] error=" + error);
        if (strict && !errors.isEmpty()) throw new IllegalStateException("Phase 11 validation failed: " + errors);
    }

    private static void validateJar(Path jar, String version, List<String> errors) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            String manifestTitle = jf.getManifest().getMainAttributes().getValue("Implementation-Title");
            String manifestVersion = jf.getManifest().getMainAttributes().getValue("Implementation-Version");
            if (!APP_DISPLAY_NAME.equals(manifestTitle)) errors.add("JAR title mismatch: " + manifestTitle);
            if (!version.equals(manifestVersion)) errors.add("JAR version mismatch: " + manifestVersion + " expected " + version);
            requireEntry(jf, "VERSION", errors);
            requirePrefix(jf, "ship_skins/", errors);
            requirePrefix(jf, "ship_parts/", errors);
            requirePrefix(jf, "ship_wrecks/", errors);
            requirePrefix(jf, "turret_skins/", errors);
            requirePrefix(jf, "station_modules/", errors);
            requirePrefix(jf, "environment_overhaul_dropzone/", errors);
            requirePrefix(jf, "ui_theme/", errors);
            requirePrefix(jf, "audio/", errors);
            requirePrefix(jf, "voice/", errors);
            rejectPrefix(jf, "ai_pipeline/", errors);
            rejectPrefix(jf, "newshipskins/", errors);
            rejectSuffix(jf, ".psd", errors);
            rejectSuffix(jf, ".tmp", errors);
        }
    }

    private static void validateZip(Path zip, List<String> errors) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            boolean hasRuntimeRelease = zf.stream().anyMatch(e -> e.getName().endsWith("runtime/release"));
            boolean hasRuntimeModules = zf.stream().anyMatch(e -> e.getName().endsWith("runtime/lib/modules"));
            boolean hasJar = zf.stream().anyMatch(e -> e.getName().startsWith("app/") && e.getName().endsWith(".jar"));
            boolean hasSource = zf.stream().anyMatch(e -> e.getName().startsWith("src/"));
            boolean hasIde = zf.stream().anyMatch(e -> e.getName().startsWith(".idea/") || e.getName().endsWith(".iml"));
            if (!hasRuntimeRelease) errors.add("ZIP does not contain bundled runtime/release metadata");
            if (!hasRuntimeModules) errors.add("ZIP does not contain bundled runtime/lib/modules");
            if (!hasJar) errors.add("ZIP does not contain app JAR");
            if (hasSource) errors.add("ZIP contains source tree");
            if (hasIde) errors.add("ZIP contains IDE metadata");
        }
    }

    private static void writeChecksums(Path packageDir, String checksumFileName) throws Exception {
        if (!Files.isDirectory(packageDir)) return;
        List<Path> artifacts;
        try (var stream = Files.list(packageDir)) {
            artifacts = stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".zip") || name.endsWith(".exe") || name.endsWith(".msi")
                                || name.endsWith(".tar.gz");
                    })
                    .sorted()
                    .toList();
        }
        if (artifacts.isEmpty()) return;
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        List<String> lines = new ArrayList<>();
        for (Path artifact : artifacts) {
            sha256.reset();
            try (InputStream in = Files.newInputStream(artifact)) {
                in.transferTo(new java.io.OutputStream() {
                    @Override public void write(int b) { sha256.update((byte) b); }
                    @Override public void write(byte[] b, int off, int len) { sha256.update(b, off, len); }
                });
            }
            lines.add(HexFormat.of().formatHex(sha256.digest()) + "  " + artifact.getFileName());
        }
        Files.write(packageDir.resolve(checksumFileName), lines, StandardCharsets.UTF_8);
    }

    private static void writeReport(Path report, List<String> errors, boolean built) throws IOException {
        Files.createDirectories(report.toAbsolutePath().getParent());
        String json = "{\n"
                + "  \"phase\": 11,\n"
                + "  \"builtArtifacts\": " + built + ",\n"
                + "  \"status\": \"" + (errors.isEmpty() ? "PASS" : "FAIL") + "\",\n"
                + "  \"distributionChannels\": " + distributionChannels().size() + ",\n"
                + "  \"cleanMachineSteps\": " + cleanMachineSteps().size() + ",\n"
                + "  \"userDataRoot\": \"" + escape(UserDataPaths.root().toString()) + "\",\n"
                + "  \"errors\": " + jsonArray(errors) + "\n"
                + "}\n";
        Files.writeString(report, json, StandardCharsets.UTF_8);
    }

    private static String readVersion(Path root) throws IOException {
        return Files.readString(root.resolve("VERSION"), StandardCharsets.UTF_8).trim();
    }

    private static Path findFirst(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) return null;
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void requireFile(Path path, List<String> errors, String label) {
        if (!Files.isRegularFile(path)) errors.add(label + " missing: " + path);
    }

    private static void contains(String haystack, String needle, List<String> errors, String message) {
        if (haystack == null || !haystack.contains(needle)) errors.add(message);
    }

    private static void requireEntry(JarFile jf, String entry, List<String> errors) {
        if (jf.getEntry(entry) == null) errors.add("JAR missing " + entry);
    }

    private static void requirePrefix(JarFile jf, String prefix, List<String> errors) {
        if (jf.stream().noneMatch(entry -> entry.getName().startsWith(prefix))) errors.add("JAR missing resource prefix " + prefix);
    }

    private static void rejectPrefix(JarFile jf, String prefix, List<String> errors) {
        if (jf.stream().anyMatch(entry -> entry.getName().startsWith(prefix))) errors.add("JAR includes excluded resource prefix " + prefix);
    }

    private static void rejectSuffix(JarFile jf, String suffix, List<String> errors) {
        if (jf.stream().anyMatch(entry -> entry.getName().toLowerCase(Locale.ROOT).endsWith(suffix))) {
            errors.add("JAR includes excluded resource suffix " + suffix);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String jsonArray(List<String> values) {
        if (values.isEmpty()) return "[]";
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(escape(values.get(i))).append('"');
        }
        return out.append(']').toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
