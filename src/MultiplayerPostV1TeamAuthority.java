import java.util.HashMap;
import java.util.Map;

/** Post-V1 same-team and AI-fleet authority model, gated behind a stable opposing-team duel. */
public final class MultiplayerPostV1TeamAuthority {
    private final boolean opposingDuelStable;
    private final boolean sameTeamSlotsEnabled;
    private final boolean aiShipsEnabled;
    private final Map<Integer, SlotAuthority> slots = new HashMap<>();

    public MultiplayerPostV1TeamAuthority(boolean opposingDuelStable,
                                          boolean sameTeamSlotsEnabled,
                                          boolean aiShipsEnabled) {
        this.opposingDuelStable = opposingDuelStable;
        this.sameTeamSlotsEnabled = opposingDuelStable && sameTeamSlotsEnabled;
        this.aiShipsEnabled = opposingDuelStable && aiShipsEnabled;
    }

    public enum AuthorityRole {
        FLEET_COMMANDER,
        DIRECT_PILOT
    }

    public enum TeamCommand {
        DIRECT_SHIP_INPUT,
        TARGET_SELECTION,
        FLEET_ORDER,
        FORMATION,
        ESCORT_ORDER,
        AI_ESCORT_SCOPE
    }

    public record SlotAuthority(int slotId,
                                int teamId,
                                int controlledShipId,
                                AuthorityRole role,
                                boolean captainAuthorityUiVisible) {
        public SlotAuthority {
            slotId = Math.max(0, slotId);
            teamId = Math.max(0, teamId);
            controlledShipId = Math.max(0, controlledShipId);
            if (role == null) role = AuthorityRole.DIRECT_PILOT;
            captainAuthorityUiVisible = role == AuthorityRole.FLEET_COMMANDER && captainAuthorityUiVisible;
        }
    }

    public record AuthorityResult(boolean accepted, String reason) {
        public AuthorityResult {
            reason = (reason == null || reason.isBlank())
                    ? (accepted ? "Accepted" : "Rejected")
                    : reason.trim();
        }
    }

    public enum SensorSharing {
        COMPLETE_TEAM_VISIBILITY,
        LOCAL_ONLY
    }

    public boolean sameTeamSlotsEnabled() {
        return sameTeamSlotsEnabled;
    }

    public boolean aiShipsEnabled() {
        return aiShipsEnabled;
    }

    public boolean opposingDuelStable() {
        return opposingDuelStable;
    }

    public void registerSlot(SlotAuthority authority) {
        if (authority != null) slots.put(authority.slotId(), authority);
    }

    public AuthorityResult validate(int slotId, TeamCommand command) {
        SlotAuthority slot = slots.get(slotId);
        if (slot == null) return new AuthorityResult(false, "Unknown player slot");
        TeamCommand safeCommand = command == null ? TeamCommand.DIRECT_SHIP_INPUT : command;
        return switch (safeCommand) {
            case DIRECT_SHIP_INPUT, TARGET_SELECTION -> new AuthorityResult(true, "Accepted");
            case FLEET_ORDER, FORMATION, ESCORT_ORDER, AI_ESCORT_SCOPE -> {
                if (slot.role() == AuthorityRole.FLEET_COMMANDER) {
                    yield new AuthorityResult(true, "Accepted");
                }
                yield new AuthorityResult(false, "Only the fleet commander may issue team-wide orders");
            }
        };
    }

    public SensorSharing sensorSharingForSameTeam() {
        return sameTeamSlotsEnabled ? SensorSharing.COMPLETE_TEAM_VISIBILITY : SensorSharing.LOCAL_ONLY;
    }

    public AuthorityResult validateAiEscortScope(int slotId, int escortTeamId) {
        if (!aiShipsEnabled) return new AuthorityResult(false, "AI ships are disabled until after the V1 duel is stable");
        SlotAuthority slot = slots.get(slotId);
        if (slot == null) return new AuthorityResult(false, "Unknown player slot");
        if (slot.teamId() != escortTeamId) return new AuthorityResult(false, "AI escort must belong to the commander's team");
        return validate(slotId, TeamCommand.AI_ESCORT_SCOPE);
    }
}
