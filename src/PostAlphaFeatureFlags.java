import java.util.EnumMap;
import java.util.Locale;

/** Default-package compatibility wrapper for the packaged post-alpha feature gate. */
public final class PostAlphaFeatureFlags {
    public enum Feature {
        TERRITORY_FRONTS, YELLOW_SPLIT, STRATEGIC_OPERATIONS,
        YELLOW_CIVIL_WAR, SUPPLY_PRESSURE, WAR_MEMORY,
        RIVAL_COMMANDERS, FLAGSHIP_OPERATIONS, BOARDING_RESCUE,
        FOCUSED_FACTION_ATTACKS,
        ALTERNATIVE_CAMPAIGNS, COOPERATIVE_COMMAND_PROTOTYPE,
        MULTIPLAYER_CUSTOM_BATTLE;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    private PostAlphaFeatureFlags() {}

    public static boolean enabled(Feature feature) {
        if (feature == null) return false;
        return app.config.PostAlphaFeatureFlags.enabled(
                app.config.PostAlphaFeatureFlags.Feature.valueOf(feature.name()));
    }

    public static EnumMap<Feature, Boolean> snapshot() {
        EnumMap<Feature, Boolean> out = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            out.put(feature, enabled(feature));
        }
        return out;
    }
}
