package app.support;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * User-writable runtime paths for installed, portable, and development builds.
 */
public final class UserDataPaths {
    private static final String APP_DIR_NAME = "Eagles Remorse";

    private UserDataPaths() {}

    public static Path root() {
        String override = trimOrNull(System.getProperty("game.userDataDir"));
        if (override != null) return Paths.get(override).toAbsolutePath().normalize();

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = trimOrNull(System.getenv("APPDATA"));
            if (appData != null) return Paths.get(appData, APP_DIR_NAME).toAbsolutePath().normalize();
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", APP_DIR_NAME).toAbsolutePath().normalize();
        }
        String xdgDataHome = trimOrNull(System.getenv("XDG_DATA_HOME"));
        if (xdgDataHome != null) return Paths.get(xdgDataHome, "eagles-remorse").toAbsolutePath().normalize();
        return Paths.get(home, ".local", "share", "eagles-remorse").toAbsolutePath().normalize();
    }

    public static Path saveDir() {
        return root().resolve("save");
    }

    public static Path logDir() {
        return root().resolve("logs");
    }

    public static Path legacyDevSaveDir() {
        return Paths.get("save").toAbsolutePath().normalize();
    }

    public static boolean isUserWritablePath(Path path) {
        if (path == null) return false;
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(root()) || normalized.startsWith(saveDir()) || normalized.startsWith(logDir());
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
