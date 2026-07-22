import app.config.GameConfig;
import app.config.GameMode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Scope guard for the first custom-battle multiplayer slice.
 *
 * V1 is intentionally a small opposing-team duel. Expanded features belong in later rules profiles.
 */
public final class MultiplayerRulesV1 {
    public static final int HOST_SLOT_ID = 1;
    public static final int CLIENT_SLOT_ID = 2;
    public static final int PLAYER_COUNT = 2;
    public static final int AUTHORITATIVE_TICK_RATE = 60;
    public static final int MAX_CATCH_UP_TICKS_PER_FRAME = 5;
    public static final int MAX_INPUT_FRAMES_PER_SECOND = AUTHORITATIVE_TICK_RATE;
    public static final int INPUT_STALE_TIMEOUT_TICKS = 15;
    public static final String DEFAULT_ARENA_ID = "duel-arena-v1";
    public static final String RULES_PROFILE_ID = "multiplayer:v1";
    public static final String AI_SUPPORT_RULES_PROFILE_ID = "multiplayer:v1_ai_support";
    public static final boolean CAMPAIGN_MULTIPLAYER_SUPPORTED = false;
    public static final boolean SAME_TEAM_COOP_SUPPORTED = false;
    public static final boolean AI_SHIPS_SUPPORTED = true;
    public static final int AI_SUPPORT_SHIPS_PER_TEAM = 1;
    public static final boolean RESPAWNS_SUPPORTED = false;
    public static final boolean RECONNECT_SUPPORTED = false;
    public static final boolean MID_MATCH_JOIN_SUPPORTED = false;
    public static final boolean HOST_MIGRATION_SUPPORTED = false;
    public static final boolean ACTIVE_MATCH_PAUSE_SUPPORTED = false;
    public static final boolean SUPERWEAPONS_SUPPORTED = false;
    public static final boolean BATTLEFIELD_WARP_SUPPORTED = false;

    private MultiplayerRulesV1() {}

    public enum VictoryRule {
        ELIMINATION
    }

    public enum PlayerRole {
        DIRECT_SHIP
    }

    public enum ConnectionState {
        LOCAL,
        CONNECTED,
        DISCONNECTED
    }

    public enum UnsupportedFeature {
        CAMPAIGN_MULTIPLAYER("Campaign multiplayer is unsupported in V1"),
        SAME_TEAM_COOP("Same-team co-op is unsupported in V1"),
        AI_SHIPS("AI ships are unsupported in V1"),
        ESCORTS("Escorts are unsupported in V1"),
        FORMATIONS("Formations and fleet-wide orders are unsupported in V1"),
        FOG_OF_WAR("Fog of war and sensor-filtered replication are unsupported in V1"),
        RESPAWNS("Respawns are unsupported in V1"),
        RECONNECT("Reconnect is unsupported in V1"),
        MID_MATCH_JOIN("Mid-match joining is unsupported in V1"),
        HOST_MIGRATION("Host migration is unsupported in V1"),
        ACTIVE_MATCH_PAUSE("Active-match pause is unsupported in V1"),
        SUPERWEAPONS("Superweapons are disabled in V1"),
        BATTLEFIELD_WARP("Battlefield warp is disabled in V1");

        private final String rejectionMessage;

        UnsupportedFeature(String rejectionMessage) {
            this.rejectionMessage = rejectionMessage;
        }

        public String rejectionMessage() {
            return rejectionMessage;
        }
    }

    public record PlayerSlot(int slotId, Faction team, ShipRole hull, String displayName) {
        public PlayerSlot {
            if (displayName == null || displayName.isBlank()) {
                displayName = "Player " + Math.max(1, slotId);
            } else {
                displayName = displayName.trim();
            }
            if (hull == null) hull = ShipRole.FRIGATE;
        }
    }

    public record BattleSetup(
            long seed,
            String arenaId,
            PlayerSlot hostSlot,
            PlayerSlot clientSlot,
            VictoryRule victoryRule,
            boolean sameTeamCoop,
            boolean aiShips,
            boolean escorts,
            boolean formations,
            boolean fogOfWar,
            boolean respawns,
            boolean reconnect,
            boolean midMatchJoin,
            boolean hostMigration,
            boolean activeMatchPause,
            boolean superweapons,
            boolean battlefieldWarp) {
        public BattleSetup {
            if (arenaId == null || arenaId.isBlank()) arenaId = DEFAULT_ARENA_ID;
            else arenaId = arenaId.trim();
            if (victoryRule == null) victoryRule = VictoryRule.ELIMINATION;
        }
    }

    public record ValidationResult(boolean accepted, String message, List<String> errors) {
        public ValidationResult {
            message = (message == null || message.isBlank()) ? (accepted ? "Accepted" : "Rejected") : message.trim();
            errors = (errors == null) ? List.of() : List.copyOf(errors);
        }

        public static ValidationResult accepted(String message) {
            return new ValidationResult(true, message, List.of());
        }

        public static ValidationResult rejected(List<String> errors) {
            List<String> safe = (errors == null || errors.isEmpty()) ? List.of("Rejected") : List.copyOf(errors);
            return new ValidationResult(false, safe.getFirst(), safe);
        }
    }

    public static boolean entryPointEnabled() {
        return PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.MULTIPLAYER_CUSTOM_MISSIONS)
                || PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.MULTIPLAYER_CUSTOM_BATTLE);
    }

    public static BattleSetup defaultDuel(long seed, ShipRole hostHull, ShipRole clientHull) {
        return new BattleSetup(
                seed,
                DEFAULT_ARENA_ID,
                new PlayerSlot(HOST_SLOT_ID, Faction.ALLY, hostHull, "Host"),
                new PlayerSlot(CLIENT_SLOT_ID, Faction.ENEMY, clientHull, "Client"),
                VictoryRule.ELIMINATION,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    public static Set<MultiplayerCapability> supportedCapabilities() {
        return EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS);
    }

    public static ValidationResult validateBattleOnlyConfig(GameConfig config) {
        if (config == null) {
            return ValidationResult.rejected(List.of("Missing custom-battle launch config"));
        }
        if (config.mode != GameMode.CUSTOM_BATTLES) {
            return ValidationResult.rejected(List.of(UnsupportedFeature.CAMPAIGN_MULTIPLAYER.rejectionMessage()));
        }
        if (config.resumeCampaign) {
            return ValidationResult.rejected(List.of("Campaign resume is not allowed for multiplayer custom battles"));
        }
        return ValidationResult.accepted("Battle-only launch config accepted");
    }

    public static ValidationResult validate(BattleSetup setup) {
        ArrayList<String> errors = new ArrayList<>();
        if (setup == null) {
            errors.add("Missing multiplayer battle setup");
            return ValidationResult.rejected(errors);
        }
        validateSlot(setup.hostSlot, HOST_SLOT_ID, "Host", errors);
        validateSlot(setup.clientSlot, CLIENT_SLOT_ID, "Client", errors);

        if (setup.hostSlot != null && setup.clientSlot != null
                && setup.hostSlot.team != null && setup.clientSlot.team != null
                && setup.hostSlot.team.teamId() == setup.clientSlot.team.teamId()) {
            errors.add(UnsupportedFeature.SAME_TEAM_COOP.rejectionMessage());
        }
        if (setup.victoryRule != VictoryRule.ELIMINATION) {
            errors.add("Only elimination victory is supported in V1");
        }
        rejectIf(setup.sameTeamCoop, UnsupportedFeature.SAME_TEAM_COOP, errors);
        rejectIf(setup.escorts, UnsupportedFeature.ESCORTS, errors);
        rejectIf(setup.formations, UnsupportedFeature.FORMATIONS, errors);
        rejectIf(setup.fogOfWar, UnsupportedFeature.FOG_OF_WAR, errors);
        rejectIf(setup.respawns, UnsupportedFeature.RESPAWNS, errors);
        rejectIf(setup.reconnect, UnsupportedFeature.RECONNECT, errors);
        rejectIf(setup.midMatchJoin, UnsupportedFeature.MID_MATCH_JOIN, errors);
        rejectIf(setup.hostMigration, UnsupportedFeature.HOST_MIGRATION, errors);
        rejectIf(setup.activeMatchPause, UnsupportedFeature.ACTIVE_MATCH_PAUSE, errors);
        rejectIf(setup.superweapons, UnsupportedFeature.SUPERWEAPONS, errors);
        rejectIf(setup.battlefieldWarp, UnsupportedFeature.BATTLEFIELD_WARP, errors);

        if (!errors.isEmpty()) return ValidationResult.rejected(errors);
        return ValidationResult.accepted("V1 multiplayer duel setup accepted");
    }

    public static ValidationResult rejectUnsupported(UnsupportedFeature feature) {
        if (feature == null) return ValidationResult.rejected(List.of("Unsupported multiplayer feature"));
        return ValidationResult.rejected(List.of(feature.rejectionMessage()));
    }

    private static void validateSlot(PlayerSlot slot, int requiredSlotId, String label, List<String> errors) {
        if (slot == null) {
            errors.add(label + " slot is required");
            return;
        }
        if (slot.slotId != requiredSlotId) {
            errors.add(label + " slot must use slot id " + requiredSlotId);
        }
        if (slot.team == null) {
            errors.add(label + " slot must have a team");
        }
        if (slot.hull == null) {
            errors.add(label + " slot must have a hull");
        } else if (slot.hull == ShipRole.BASE || slot.hull == ShipRole.STATIC_TURRET) {
            errors.add(label + " slot must use a directly controllable ship hull");
        }
    }

    private static void rejectIf(boolean active, UnsupportedFeature feature, List<String> errors) {
        if (active) errors.add(feature.rejectionMessage());
    }
}
