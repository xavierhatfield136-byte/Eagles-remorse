import app.config.GameMode;

import java.util.EnumSet;
import java.util.Set;

public record CustomMissionDescriptor(String id,
                                      int revision,
                                      String displayName,
                                      String description,
                                      Set<GameMode> supportedLaunchModes,
                                      Set<MultiplayerCapability> requiredMultiplayerCapabilities) {
    public CustomMissionDescriptor {
        id = clean(id, "core:unknown");
        revision = Math.max(1, revision);
        displayName = clean(displayName, id);
        description = clean(description, "");
        supportedLaunchModes = supportedLaunchModes == null || supportedLaunchModes.isEmpty()
                ? Set.of(GameMode.CUSTOM_BATTLES)
                : Set.copyOf(supportedLaunchModes);
        requiredMultiplayerCapabilities = requiredMultiplayerCapabilities == null || requiredMultiplayerCapabilities.isEmpty()
                ? Set.of()
                : EnumSet.copyOf(requiredMultiplayerCapabilities);
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
