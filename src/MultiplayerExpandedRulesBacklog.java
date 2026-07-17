import java.util.EnumMap;
import java.util.Map;

/** Considered post-V1 rule expansions. Every item is disabled until promoted into a later rules profile. */
public final class MultiplayerExpandedRulesBacklog {
    private final EnumMap<Expansion, Decision> decisions = new EnumMap<>(Expansion.class);

    public MultiplayerExpandedRulesBacklog() {
        add(Expansion.ADDITIONAL_ARENAS, "Needs arena manifest replication and lobby selection.");
        add(Expansion.HULL_SELECTION_EXPANSION, "Needs balance pass and content manifest hashing per hull set.");
        add(Expansion.AI_FILLED_TEAMS, "Needs host-only AI replication budget and commander authority.");
        add(Expansion.BASE_DESTRUCTION_VICTORY, "Needs objective replication and match-end event coverage.");
        add(Expansion.SCORE_TIMER_VICTORY, "Needs synchronized timer, scoring events, and overtime rules.");
        add(Expansion.CUSTOM_OBJECTIVE_VICTORY, "Needs objective schema, replication, and UI presentation.");
        add(Expansion.RESPAWN, "Needs spawn reservation, invulnerability rules, and anti-stall handling.");
        add(Expansion.SUPERWEAPONS, "Needs bandwidth budget and host-only activation validation.");
        add(Expansion.BATTLEFIELD_WARP, "Needs deterministic warp resolution and anti-desync tests.");
        add(Expansion.HAZARDS_MINES_SALVAGE_COMPLEX_PROJECTILES, "Needs projectile/hazard replication expansion.");
        add(Expansion.FOG_OF_WAR_VISIBILITY_FILTERS, "Needs per-client visibility snapshots and sensor rules.");
        add(Expansion.RECONNECT, "Needs slot reservation, reconnect token, full resync, stale command discard, and control restore.");
        add(Expansion.INTERNET_HOSTING, "Needs NAT/firewall/relay/platform networking decision.");
        add(Expansion.PASSWORDS_OR_INVITE_CODES, "Needs broader connection-security design.");
    }

    public enum Expansion {
        ADDITIONAL_ARENAS,
        HULL_SELECTION_EXPANSION,
        AI_FILLED_TEAMS,
        BASE_DESTRUCTION_VICTORY,
        SCORE_TIMER_VICTORY,
        CUSTOM_OBJECTIVE_VICTORY,
        RESPAWN,
        SUPERWEAPONS,
        BATTLEFIELD_WARP,
        HAZARDS_MINES_SALVAGE_COMPLEX_PROJECTILES,
        FOG_OF_WAR_VISIBILITY_FILTERS,
        RECONNECT,
        INTERNET_HOSTING,
        PASSWORDS_OR_INVITE_CODES
    }

    public record Decision(boolean considered, boolean enabledByDefault, String requiredBeforeEnablement) {
        public Decision {
            requiredBeforeEnablement = (requiredBeforeEnablement == null || requiredBeforeEnablement.isBlank())
                    ? "Requires a later rules profile."
                    : requiredBeforeEnablement.trim();
        }
    }

    public Decision decision(Expansion expansion) {
        return decisions.get(expansion);
    }

    public Map<Expansion, Decision> decisions() {
        return Map.copyOf(decisions);
    }

    public boolean canEnableInV1(Expansion expansion) {
        return false;
    }

    private void add(Expansion expansion, String requirement) {
        decisions.put(expansion, new Decision(true, false, requirement));
    }
}
