package app.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Executable 1.0 save-field inventory. Every public checkpoint field is classified
 * here through an explicit exception set plus an authoritative-by-default rule.
 */
public final class CampaignSaveContract {
    public enum Status {
        AUTHORITATIVE_LIVE,
        DEBUG_READOUT_ONLY,
        FUTURE_MODEL_ONLY,
        MIGRATION_METADATA
    }

    public record FieldContract(String field, Status status, String defaultValue, String fallback) {}

    private static final Set<String> FUTURE_MODEL_FIELDS = Set.of(
            "strategicExpansionState",
            "operationsExpansionState",
            "fleetDoctrineExpansionState",
            "deepCampaignExpansionState",
            "communityContentState"
    );

    private static final Set<String> DEBUG_READOUT_FIELDS = Set.of(
            "redDirectorBrief",
            "greenDirectorBrief",
            "yellowDirectorBrief"
    );

    private static final Set<String> MIGRATION_METADATA_FIELDS = Set.of(
            "sourceVersion",
            "migrationApplied",
            "migrationRepairs",
            "migrationMessage"
    );

    private CampaignSaveContract() {}

    public static List<FieldContract> inventory() {
        CampaignCheckpointStore.Checkpoint defaults = new CampaignCheckpointStore.Checkpoint();
        ArrayList<FieldContract> out = new ArrayList<>();
        for (Field field : CampaignCheckpointStore.Checkpoint.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                Object value = field.get(defaults);
                Status status = statusFor(field.getName());
                out.add(new FieldContract(
                        field.getName(),
                        status,
                        String.valueOf(value),
                        fallbackFor(field.getName(), status)));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Checkpoint field is not readable: " + field.getName(), ex);
            }
        }
        out.sort(Comparator.comparing(FieldContract::field));
        return List.copyOf(out);
    }

    public static FieldContract field(String name) {
        if (name == null) return null;
        return inventory().stream().filter(row -> row.field().equals(name)).findFirst().orElse(null);
    }

    private static Status statusFor(String field) {
        if (MIGRATION_METADATA_FIELDS.contains(field)) return Status.MIGRATION_METADATA;
        if (FUTURE_MODEL_FIELDS.contains(field)) return Status.FUTURE_MODEL_ONLY;
        if (DEBUG_READOUT_FIELDS.contains(field)) return Status.DEBUG_READOUT_ONLY;
        return Status.AUTHORITATIVE_LIVE;
    }

    private static String fallbackFor(String field, Status status) {
        if (status == Status.FUTURE_MODEL_ONLY) return "deterministic seeded model default";
        if (status == Status.DEBUG_READOUT_ONLY) return "regenerated from live campaign state";
        if (status == Status.MIGRATION_METADATA) return "not persisted; populated while loading";
        return switch (field) {
            case "persistentBlueFleet", "campaignForces" -> "rebuild from seeded faction/player fleet inventory";
            case "campaignYardOrders", "campaignBaseQueues" -> "empty queue; never synthesize paid work";
            case "campaignOre", "cargo" -> "preserve legacy cargo, with minimum migration reserve";
            case "campaignTheaters", "strategicNodes" -> "recompute from stable location ownership IDs";
            case "diplomacyNarrativeState", "greenContractFavor", "yellowLiberationFavor" ->
                    "preserve counters; bootstrap missing narrative history";
            case "galaxyLocationStates" -> "rebuild authored environment/location defaults";
            default -> "Checkpoint.normalize() clamps or supplies the declared field default";
        };
    }
}
