import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Authoritative V1 launch-spec validator shared by lobby, protocol, and runtime paths. */
public final class MultiplayerMissionValidator {
    private static final int MIN_WORLD_SIZE = 1800;
    private static final int MAX_WORLD_SIZE = 60000;

    public record ValidationResult(boolean accepted, List<String> errors) {
        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public String message() {
            if (accepted) return "Mission accepted";
            return errors.isEmpty() ? "Mission rejected" : errors.get(0);
        }
    }

    private MultiplayerMissionValidator() {}

    public static ValidationResult validateForV1(MissionLaunchSpec spec) {
        ArrayList<String> errors = new ArrayList<>();
        if (spec == null) {
            errors.add("Missing mission launch specification");
            return rejected(errors);
        }

        validateMissionIdentity(spec, errors);
        validateRulesProfile(spec, errors);
        validateWorldSize(spec, errors);
        validateObjective(spec, errors);
        validateRosterBudget(spec, errors);
        validatePlayerSlots(spec, errors);
        validateAiUnsupported(spec, errors);

        return errors.isEmpty() ? new ValidationResult(true, List.of()) : rejected(errors);
    }

    public static void requireV1(MissionLaunchSpec spec) {
        ValidationResult result = validateForV1(spec);
        if (!result.accepted()) throw new IllegalArgumentException(result.message());
    }

    private static void validateMissionIdentity(MissionLaunchSpec spec, List<String> errors) {
        CustomMissionDescriptor descriptor = CustomMissionCatalog.descriptorFor(spec.missionId());
        if (descriptor == null) {
            errors.add(MultiplayerProtocolV1.selectedMissionUnavailable(spec.missionId()).message());
            return;
        }
        if (!MultiplayerRulesV1.supportedCapabilities()
                .containsAll(descriptor.requiredMultiplayerCapabilities())) {
            errors.add("Rules profile does not support the selected mission capabilities");
        }
        if (spec.missionRevision() != descriptor.revision()) {
            errors.add("Selected mission revision is unavailable");
        }
    }

    private static void validateRulesProfile(MissionLaunchSpec spec, List<String> errors) {
        if (!MultiplayerRulesV1.RULES_PROFILE_ID.equals(spec.rulesProfileId())
                && !MultiplayerRulesV1.AI_SUPPORT_RULES_PROFILE_ID.equals(spec.rulesProfileId())) {
            errors.add("Rules profile unsupported");
        }
    }

    private static void validateWorldSize(MissionLaunchSpec spec, List<String> errors) {
        if (spec.worldW() < MIN_WORLD_SIZE || spec.worldW() > MAX_WORLD_SIZE
                || spec.worldH() < MIN_WORLD_SIZE || spec.worldH() > MAX_WORLD_SIZE) {
            errors.add("Invalid world size");
        }
    }

    private static void validateObjective(MissionLaunchSpec spec, List<String> errors) {
        if (!"elimination".equalsIgnoreCase(spec.objectiveType())) {
            errors.add("Unsupported objective replication: " + spec.objectiveType());
        }
        if (spec.victoryRule() != MultiplayerRulesV1.VictoryRule.ELIMINATION) {
            errors.add("Unsupported victory rule");
        }
    }

    private static void validateRosterBudget(MissionLaunchSpec spec, List<String> errors) {
        int maxShips = MultiplayerRulesV1.AI_SUPPORT_RULES_PROFILE_ID.equals(spec.rulesProfileId())
                ? MultiplayerReplicationV1.MAX_REPLICATED_SHIPS_V1
                : MultiplayerRulesV1.PLAYER_COUNT;
        if (spec.resolvedRosters().size() > maxShips) {
            errors.add("Too many entities for the current rules profile");
        }
    }

    private static void validatePlayerSlots(MissionLaunchSpec spec, List<String> errors) {
        MissionSlotSpec host = findPlayerSlot(spec, MultiplayerRulesV1.HOST_SLOT_ID);
        MissionSlotSpec client = findPlayerSlot(spec, MultiplayerRulesV1.CLIENT_SLOT_ID);
        if (host == null || client == null) {
            errors.add("Missing required player slot");
            return;
        }

        Set<Integer> playerSlotIds = new HashSet<>();
        for (MissionSlotSpec slot : spec.playerSlots()) {
            if (slot == null) continue;
            if (!playerSlotIds.add(slot.slotId())) errors.add("Duplicate slot assignment");
            if (slot.required() && slot.controlMode() != MissionSlotControlMode.PLAYER_REQUIRED) {
                errors.add("Required player slot must be player controlled");
            }
            validateHull(slot, errors);
        }
        if (host.teamId() == client.teamId()) {
            errors.add("Team conflict: V1 requires opposing player teams");
        }
    }

    private static void validateHull(MissionSlotSpec slot, List<String> errors) {
        if (slot == null) return;
        if (slot.defaultHull() == ShipRole.BASE || slot.defaultHull() == ShipRole.STATIC_TURRET) {
            errors.add("Unsupported hull");
        }
    }

    private static void validateAiUnsupported(MissionLaunchSpec spec, List<String> errors) {
        boolean aiProfile = MultiplayerRulesV1.AI_SUPPORT_RULES_PROFILE_ID.equals(spec.rulesProfileId());
        for (MissionSlotSpec slot : spec.resolvedRosters()) {
            if (slot == null) continue;
            validateHull(slot, errors);
            if (!aiProfile && (slot.controlMode() == MissionSlotControlMode.AI_ONLY
                    || slot.controlMode() == MissionSlotControlMode.PLAYER_OR_AI)) {
                errors.add("AI is unsupported by the active rules profile");
            }
        }
    }

    private static MissionSlotSpec findPlayerSlot(MissionLaunchSpec spec, int slotId) {
        for (MissionSlotSpec slot : spec.playerSlots()) {
            if (slot != null && slot.slotId() == slotId) return slot;
        }
        return null;
    }

    private static ValidationResult rejected(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
