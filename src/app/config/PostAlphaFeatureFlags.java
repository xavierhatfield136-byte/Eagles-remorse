package app.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Properties;

/** Packaged feature gate facade for UI and other named-package code. */
public final class PostAlphaFeatureFlags {
    public enum Feature {
        TERRITORY_FRONTS(true), YELLOW_SPLIT(true), STRATEGIC_OPERATIONS(true),
        YELLOW_CIVIL_WAR(true), SUPPLY_PRESSURE(true), WAR_MEMORY(false),
        RIVAL_COMMANDERS(false), FLAGSHIP_OPERATIONS(false), BOARDING_RESCUE(false),
        FOCUSED_FACTION_ATTACKS(false),
        ALTERNATIVE_CAMPAIGNS(false), COOPERATIVE_COMMAND_PROTOTYPE(false),
        MULTIPLAYER_CUSTOM_BATTLE(false);

        final boolean safeDefault;
        Feature(boolean safeDefault) { this.safeDefault = safeDefault; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    private static final Path CONFIG = Path.of("config", "post_alpha_feature_flags.properties");
    private static final EnumMap<Feature, Boolean> VALUES = load();

    private PostAlphaFeatureFlags() {}

    public static boolean enabled(Feature feature) {
        if (feature == null) return false;
        String override = System.getProperty("game.feature." + feature.key(), "").trim();
        if (!override.isEmpty()) return Boolean.parseBoolean(override);
        return VALUES.getOrDefault(feature, feature.safeDefault);
    }

    public static EnumMap<Feature, Boolean> snapshot() {
        return new EnumMap<>(VALUES);
    }

    private static EnumMap<Feature, Boolean> load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(CONFIG)) {
            try (InputStream input = Files.newInputStream(CONFIG)) {
                properties.load(input);
            } catch (IOException ignored) {
                // Safe defaults keep foundational systems available and prototypes disabled.
            }
        }
        EnumMap<Feature, Boolean> values = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            values.put(feature, Boolean.parseBoolean(properties.getProperty(feature.key(),
                    Boolean.toString(feature.safeDefault))));
        }
        return values;
    }
}
