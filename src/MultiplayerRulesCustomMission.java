import java.util.EnumSet;
import java.util.Set;

/** Future rules profile for replicated custom missions beyond the V1 two-player duel. */
public final class MultiplayerRulesCustomMission {
    public static final String RULES_PROFILE_ID = "multiplayer:custom_mission_v2";

    private static final Set<MultiplayerCapability> SUPPORTED_CAPABILITIES = EnumSet.of(
            MultiplayerCapability.OPPOSING_PLAYERS,
            MultiplayerCapability.COOP_PLAYERS,
            MultiplayerCapability.AI_REPLICATION,
            MultiplayerCapability.OBJECTIVE_REPLICATION,
            MultiplayerCapability.RESOURCE_REPLICATION,
            MultiplayerCapability.WAVE_REPLICATION,
            MultiplayerCapability.SHARED_TEAM_COMMANDS);

    private MultiplayerRulesCustomMission() {}

    public static Set<MultiplayerCapability> supportedCapabilities() {
        return Set.copyOf(SUPPORTED_CAPABILITIES);
    }
}
