import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists keyboard, mouse, and controller remaps separately from campaign saves.
 */
public final class ControlSettingsStore {
    private static final Path FILE = Path.of("save", "control_settings.properties");

    private ControlSettingsStore() {}

    public static Properties load() {
        Properties props = new Properties();
        if (!Files.isRegularFile(FILE)) return props;
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
        } catch (IOException ignored) {
            // Defaults remain usable if preferences are unavailable.
        }
        return props;
    }

    public static void save(Properties props) {
        if (props == null) return;
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "Input settings");
            }
        } catch (IOException ignored) {
            // Input defaults remain usable if preferences cannot be persisted.
        }
    }
}
