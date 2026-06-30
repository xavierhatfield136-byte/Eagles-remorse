import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Data-backed strategic tuning with safe built-in defaults for packaged builds. */
public final class StrategicTuning {
    private static final Properties VALUES = load();
    private StrategicTuning() {}

    public static int integer(String key, int fallback) {
        try { return Integer.parseInt(VALUES.getProperty(key, Integer.toString(fallback)).trim()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static Properties load() {
        Properties values = new Properties();
        Path path = Path.of("config", "strategic_expansion.properties");
        if (!Files.isRegularFile(path)) return values;
        try (InputStream in = Files.newInputStream(path)) { values.load(in); }
        catch (Exception ignored) { values.clear(); }
        return values;
    }
}
