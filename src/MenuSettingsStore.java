import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Persists main-menu startup settings between sessions.
 */
public final class MenuSettingsStore {
    private MenuSettingsStore() {}

    private static final Path SAVE_DIR = Paths.get("save");
    private static final Path SETTINGS_FILE = SAVE_DIR.resolve("menu_settings.properties");
    private static final Object IO_LOCK = new Object();

    public static final class MenuSettings {
        public int version = 1;
        public String modeName = GameMode.CAMPAIGN_OPS.name();
        public int mapIndex = 0;
        public boolean randomEvents = true;
        public boolean fullscreen = false;
        public String seedText = "0";

        void normalize() {
            version = 1;
            if (modeName == null || modeName.isBlank() || !isKnownMode(modeName)) {
                modeName = GameMode.CAMPAIGN_OPS.name();
            }
            mapIndex = MathUtil.clamp(mapIndex, 0, 2);
            if (seedText == null) seedText = "0";
            seedText = seedText.trim();
            if (seedText.isBlank()) seedText = "0";
            if (seedText.length() > 32) seedText = seedText.substring(0, 32);
        }
    }

    public static MenuSettings load() {
        synchronized (IO_LOCK) {
            MenuSettings s = new MenuSettings();
            if (!Files.exists(SETTINGS_FILE)) return s;

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(SETTINGS_FILE, StandardOpenOption.READ)) {
                props.load(in);
                s.version = parseInt(props, "version", 1);
                s.modeName = props.getProperty("modeName", s.modeName);
                s.mapIndex = parseInt(props, "mapIndex", s.mapIndex);
                s.randomEvents = parseBoolean(props, "randomEvents", s.randomEvents);
                s.fullscreen = parseBoolean(props, "fullscreen", s.fullscreen);
                s.seedText = props.getProperty("seedText", s.seedText);
            } catch (IOException ex) {
                ErrorLog.logException("[settings] load_failed path=" + SETTINGS_FILE, ex);
            }

            s.normalize();
            return s;
        }
    }

    public static void save(MenuSettings s) {
        if (s == null) return;
        synchronized (IO_LOCK) {
            s.normalize();

            try {
                Files.createDirectories(SAVE_DIR);
            } catch (IOException ex) {
                ErrorLog.logException("[settings] save_failed mkdir path=" + SAVE_DIR, ex);
                return;
            }

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(s.version));
            props.setProperty("modeName", s.modeName);
            props.setProperty("mapIndex", String.valueOf(s.mapIndex));
            props.setProperty("randomEvents", String.valueOf(s.randomEvents));
            props.setProperty("fullscreen", String.valueOf(s.fullscreen));
            props.setProperty("seedText", s.seedText);

            Path tmp = SETTINGS_FILE.resolveSibling(SETTINGS_FILE.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(
                    tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
                 OutputStream out = Channels.newOutputStream(channel)) {
                props.store(out, "Main menu settings");
                out.flush();
                channel.force(true);
            } catch (IOException ex) {
                ErrorLog.logException("[settings] save_failed write path=" + tmp, ex);
                deleteTempQuietly(tmp);
                return;
            }

            try {
                Files.move(tmp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                try {
                    Files.move(tmp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex2) {
                    ErrorLog.logException("[settings] save_failed move path=" + SETTINGS_FILE, ex2);
                    deleteTempQuietly(tmp);
                }
            } catch (IOException ex) {
                ErrorLog.logException("[settings] save_failed move path=" + SETTINGS_FILE, ex);
                deleteTempQuietly(tmp);
            }
        }
    }

    public static GameMode resolveMode(String modeName) {
        if (modeName != null) {
            for (GameMode gm : GameMode.values()) {
                if (gm.name().equalsIgnoreCase(modeName.trim())) return gm;
            }
        }
        return GameMode.CAMPAIGN_OPS;
    }

    private static boolean isKnownMode(String modeName) {
        for (GameMode gm : GameMode.values()) {
            if (gm.name().equals(modeName)) return true;
        }
        return false;
    }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        return Boolean.parseBoolean(raw.trim());
    }

    private static void deleteTempQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
