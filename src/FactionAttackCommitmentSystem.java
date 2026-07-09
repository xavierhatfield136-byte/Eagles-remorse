import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Authoritative one-target offensive commitment model.
 *
 * This class contains no rendering or territory mutation. Callers validate campaign-specific
 * legality transactionally, then project successful commitments into fleet orders and visuals.
 */
public final class FactionAttackCommitmentSystem {
    public enum Slot { GREEN, YELLOW, DARK_YELLOW, RED }
    public enum Phase {
        PLANNED,
        STAGING,
        MUSTERING,
        READY_TO_SORTIE,
        ACTIVE,
        EN_ROUTE,
        ENGAGING,
        ASSAULTING,
        HOLD,
        RESOLVING,
        RETURNING,
        COOLDOWN,
        COMPLETE,
        FAILED,
        CANCELLED,
        ABORTED,
        EXPIRED
    }

    public record Validation(boolean allowed, String reason) {
        public static Validation allow() { return new Validation(true, ""); }
        public static Validation reject(String reason) {
            return new Validation(false, reason == null || reason.isBlank() ? "Attack request rejected" : reason.trim());
        }
    }

    @FunctionalInterface
    public interface Validator {
        Validation validate(Request request);
    }

    public record Request(Faction faction,
                          String originLocationId,
                          String targetLocationId,
                          int supportingFleetId,
                          double startTimeSec,
                          double maxDurationSec) {}

    public record Result(boolean accepted,
                         boolean created,
                         String operationId,
                         String reason,
                         Commitment commitment) {}

    public static final class Commitment {
        public final String operationId;
        public final Slot slot;
        public final String originLocationId;
        public final String targetLocationId;
        public final String originalTargetOwnerId;
        public final double startTimeSec;
        public final double maxDurationSec;
        public final LinkedHashSet<Integer> supportingFleetIds = new LinkedHashSet<>();
        public Phase phase;
        public boolean released;
        public boolean ownershipApplied;
        public String lastOwnershipChange = "";
        public String terminalReason = "";
        public int minimumFleetCount = 1;
        public double minimumMusterRatio = 1.0;
        public double minimumStrengthRatio = 0.0;
        public int plannedFleetCount = 1;
        public double plannedStrength = 1.0;
        public int assembledFleetCount = 0;
        public double assembledStrength = 0.0;
        public double musterProgress = 0.0;
        public double travelProgress = 0.0;

        private Commitment(String operationId,
                           Slot slot,
                           String originLocationId,
                           String targetLocationId,
                           String originalTargetOwnerId,
                           double startTimeSec,
                           double maxDurationSec,
                           Phase phase) {
            this.operationId = operationId;
            this.slot = slot;
            this.originLocationId = stableId(originLocationId);
            this.targetLocationId = stableId(targetLocationId);
            this.originalTargetOwnerId = stableId(originalTargetOwnerId);
            this.startTimeSec = Math.max(0.0, startTimeSec);
            this.maxDurationSec = Math.max(1.0, maxDurationSec);
            this.phase = phase == null ? Phase.PLANNED : phase;
        }

        public boolean occupiesSlot() {
            return !released && phase != Phase.COMPLETE && phase != Phase.FAILED
                    && phase != Phase.CANCELLED && phase != Phase.ABORTED && phase != Phase.EXPIRED;
        }
    }

    public static final class State {
        private int nextOperationId = 1;
        private final EnumMap<Slot, Commitment> active = new EnumMap<>(Slot.class);
        private final EnumMap<Slot, String> latestRejection = new EnumMap<>(Slot.class);
        private final ArrayList<Commitment> history = new ArrayList<>();

        public Map<Slot, Commitment> activeCommitments() {
            return Collections.unmodifiableMap(active);
        }

        public List<Commitment> history() { return List.copyOf(history); }
        public String latestRejection(Slot slot) { return latestRejection.getOrDefault(slot, ""); }
        public int nextOperationId() { return nextOperationId; }
    }

    private FactionAttackCommitmentSystem() {}

    public static Slot slotFor(Faction faction) {
        if (faction == null) return null;
        if (faction == Faction.ENEMY) return Slot.RED;
        if (faction == Faction.DARK_YELLOW) return Slot.DARK_YELLOW;
        if (faction == Faction.BRIGHT_YELLOW || faction == Faction.TEAM_D) return Slot.YELLOW;
        if (faction == Faction.TEAM_C || faction == Faction.ALLY || faction == Faction.PLAYER) return Slot.GREEN;
        return null;
    }

    public static Result request(State state, Request request, String targetOwnerId, Validator validator) {
        if (state == null || request == null) return rejected(null, null, "Attack state or request is unavailable");
        Slot slot = slotFor(request.faction());
        if (slot == null) return rejected(state, null, "Faction has no strategic attack slot");
        String originId = stableId(request.originLocationId());
        String targetId = stableId(request.targetLocationId());
        if (originId.isBlank() || targetId.isBlank()) return rejected(state, slot, "Origin and target require stable IDs");
        if (originId.equals(targetId)) return rejected(state, slot, "Attack origin and target must differ");

        Commitment existing = state.active.get(slot);
        if (existing != null && existing.occupiesSlot()) {
            if (!existing.targetLocationId.equals(targetId)) {
                return rejected(state, slot, "Faction attack slot is committed to " + existing.targetLocationId);
            }
            Validation supportValidation = validator == null ? Validation.allow() : validator.validate(request);
            if (supportValidation == null || !supportValidation.allowed()) {
                return rejected(state, slot, supportValidation == null ? "Attack validation failed" : supportValidation.reason());
            }
            if (request.supportingFleetId() > 0) existing.supportingFleetIds.add(request.supportingFleetId());
            return new Result(true, false, existing.operationId, "Supporting existing target", existing);
        }

        for (Map.Entry<Slot, Commitment> entry : state.active.entrySet()) {
            Commitment other = entry.getValue();
            if (entry.getKey() != slot && other != null && other.occupiesSlot()
                    && other.targetLocationId.equals(targetId)) {
                return rejected(state, slot, "Target is already reserved by " + entry.getKey());
            }
        }

        Validation validation = validator == null ? Validation.allow() : validator.validate(request);
        if (validation == null || !validation.allowed()) {
            return rejected(state, slot, validation == null ? "Attack validation failed" : validation.reason());
        }

        // All validation is complete. Mutations begin here and are intentionally contiguous.
        String operationId = "attack-" + slot.name().toLowerCase(Locale.ROOT) + "-" + state.nextOperationId++;
        Commitment commitment = new Commitment(operationId, slot, originId, targetId, targetOwnerId,
                request.startTimeSec(), request.maxDurationSec(), Phase.PLANNED);
        if (request.supportingFleetId() > 0) commitment.supportingFleetIds.add(request.supportingFleetId());
        state.active.put(slot, commitment);
        state.latestRejection.remove(slot);
        return new Result(true, true, operationId, "", commitment);
    }

    public static Commitment active(State state, Slot slot) {
        if (state == null || slot == null) return null;
        Commitment commitment = state.active.get(slot);
        return commitment != null && commitment.occupiesSlot() ? commitment : null;
    }

    public static Commitment activeForTarget(State state, Faction faction, String targetLocationId) {
        Commitment commitment = active(state, slotFor(faction));
        return commitment != null && commitment.targetLocationId.equals(stableId(targetLocationId)) ? commitment : null;
    }

    public static boolean setPhase(State state, String operationId, Phase phase) {
        Commitment commitment = findActive(state, operationId);
        if (commitment == null || phase == null || commitment.released) return false;
        if (phase == Phase.COMPLETE || phase == Phase.FAILED || phase == Phase.CANCELLED
                || phase == Phase.ABORTED || phase == Phase.EXPIRED) return false;
        if (!legalTransition(commitment.phase, phase)) return false;
        commitment.phase = phase;
        return true;
    }

    private static boolean legalTransition(Phase from, Phase to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        if (to == Phase.HOLD) return from != Phase.COOLDOWN && from != Phase.RETURNING;
        if (from == Phase.HOLD) return to != Phase.PLANNED;
        return switch (from) {
            case PLANNED -> to == Phase.STAGING || to == Phase.MUSTERING || to == Phase.ACTIVE;
            case STAGING -> to == Phase.MUSTERING || to == Phase.READY_TO_SORTIE
                    || to == Phase.ACTIVE || to == Phase.EN_ROUTE;
            case MUSTERING -> to == Phase.STAGING || to == Phase.READY_TO_SORTIE;
            case READY_TO_SORTIE -> to == Phase.ACTIVE || to == Phase.EN_ROUTE;
            case ACTIVE -> to == Phase.STAGING || to == Phase.MUSTERING || to == Phase.READY_TO_SORTIE
                    || to == Phase.EN_ROUTE || to == Phase.ENGAGING || to == Phase.ASSAULTING
                    || to == Phase.RESOLVING || to == Phase.RETURNING;
            case EN_ROUTE -> to == Phase.ENGAGING || to == Phase.ASSAULTING
                    || to == Phase.RESOLVING || to == Phase.RETURNING;
            case ENGAGING -> to == Phase.ASSAULTING || to == Phase.RESOLVING || to == Phase.RETURNING;
            case ASSAULTING -> to == Phase.ENGAGING || to == Phase.RESOLVING || to == Phase.RETURNING;
            case RESOLVING -> to == Phase.RETURNING || to == Phase.COOLDOWN;
            case RETURNING -> to == Phase.COOLDOWN;
            case COOLDOWN, COMPLETE, FAILED, CANCELLED, ABORTED, EXPIRED -> false;
            case HOLD -> false;
        };
    }

    public static double musterRatio(int assembledFleetCount, int assignedFleetCount) {
        return assignedFleetCount <= 0 ? 0.0
                : Math.max(0, assembledFleetCount) / (double) assignedFleetCount;
    }

    public static void configureSortieRequirements(Commitment commitment,
                                                   int minimumFleetCount,
                                                   double minimumMusterRatio,
                                                   double minimumStrengthRatio,
                                                   int plannedFleetCount,
                                                   double plannedStrength) {
        if (commitment == null || commitment.released) return;
        commitment.minimumFleetCount = Math.max(1, minimumFleetCount);
        commitment.minimumMusterRatio = clamp01(minimumMusterRatio);
        commitment.minimumStrengthRatio = clamp01(minimumStrengthRatio);
        commitment.plannedFleetCount = Math.max(0, plannedFleetCount);
        commitment.plannedStrength = Math.max(0.0, plannedStrength);
    }

    public static void updateDerivedProgress(Commitment commitment,
                                             int assembledFleetCount,
                                             double assembledStrength,
                                             double travelProgress) {
        if (commitment == null || commitment.released) return;
        commitment.assembledFleetCount = Math.max(0, assembledFleetCount);
        commitment.assembledStrength = Math.max(0.0, assembledStrength);
        commitment.musterProgress = clamp01(musterRatio(commitment.assembledFleetCount,
                commitment.plannedFleetCount));
        commitment.travelProgress = clamp01(travelProgress);
    }

    public static boolean readyToSortie(Commitment commitment) {
        if (commitment == null || commitment.released) return false;
        double strengthRatio = commitment.plannedStrength <= 0.0 ? 0.0
                : commitment.assembledStrength / commitment.plannedStrength;
        return commitment.assembledFleetCount >= commitment.minimumFleetCount
                && musterRatio(commitment.assembledFleetCount, commitment.plannedFleetCount)
                >= commitment.minimumMusterRatio
                && strengthRatio >= commitment.minimumStrengthRatio;
    }

    public static boolean complete(State state, String operationId, String ownershipChange) {
        Commitment commitment = findActive(state, operationId);
        if (commitment == null) return false;
        if (ownershipChange != null && !ownershipChange.isBlank()) {
            commitment.lastOwnershipChange = ownershipChange.trim();
            commitment.ownershipApplied = true;
        }
        return release(state, commitment, Phase.COMPLETE, "completed");
    }

    public static boolean abort(State state, String operationId, String reason) {
        Commitment commitment = findActive(state, operationId);
        return commitment != null && release(state, commitment, Phase.ABORTED, reason);
    }

    public static boolean expire(State state, String operationId, String reason) {
        Commitment commitment = findActive(state, operationId);
        return commitment != null && release(state, commitment, Phase.EXPIRED, reason);
    }

    private static boolean release(State state, Commitment commitment, Phase phase, String reason) {
        if (state == null || commitment == null || commitment.released) return false;
        commitment.phase = phase;
        commitment.terminalReason = reason == null ? "" : reason.trim();
        commitment.released = true;
        if (state.active.get(commitment.slot) == commitment) state.active.remove(commitment.slot);
        state.history.add(commitment);
        while (state.history.size() > 64) state.history.remove(0);
        return true;
    }

    private static Commitment findActive(State state, String operationId) {
        if (state == null || operationId == null) return null;
        for (Commitment commitment : state.active.values()) {
            if (commitment != null && commitment.operationId.equals(operationId)) return commitment;
        }
        return null;
    }

    private static Result rejected(State state, Slot slot, String reason) {
        String safe = reason == null || reason.isBlank() ? "Attack request rejected" : reason.trim();
        if (state != null && slot != null) state.latestRejection.put(slot, safe);
        return new Result(false, false, "", safe, null);
    }

    public static List<String> diagnosticLines(State state) {
        if (state == null) return List.of("ATTACK COMMITMENTS unavailable");
        ArrayList<String> lines = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            Commitment commitment = active(state, slot);
            if (commitment == null) {
                lines.add(slot + " SLOT READY | last rejection " + state.latestRejection(slot));
            } else {
                lines.add(slot + " " + commitment.phase + " | " + commitment.operationId
                        + " | " + commitment.originLocationId + " -> " + commitment.targetLocationId
                        + " | start " + (int) commitment.startTimeSec
                        + " | fleets " + commitment.supportingFleetIds
                        + " | last rejection " + state.latestRejection(slot)
                        + " | last ownership " + commitment.lastOwnershipChange);
            }
        }
        return List.copyOf(lines);
    }

    public static String serialize(State state) {
        if (state == null) return "";
        ArrayList<String> records = new ArrayList<>();
        for (Commitment commitment : state.active.values()) {
            if (commitment == null || !commitment.occupiesSlot()) continue;
            String fleets = commitment.supportingFleetIds.stream().map(String::valueOf)
                    .reduce((a, b) -> a + "," + b).orElse("");
            records.add(String.join("|",
                    encode(commitment.operationId), commitment.slot.name(), encode(commitment.originLocationId),
                    encode(commitment.targetLocationId), encode(commitment.originalTargetOwnerId), commitment.phase.name(),
                    Double.toString(commitment.startTimeSec), Double.toString(commitment.maxDurationSec), encode(fleets),
                    Boolean.toString(commitment.ownershipApplied), encode(commitment.lastOwnershipChange),
                    Integer.toString(commitment.minimumFleetCount), Double.toString(commitment.minimumMusterRatio),
                    Double.toString(commitment.minimumStrengthRatio), Integer.toString(commitment.plannedFleetCount),
                    Double.toString(commitment.plannedStrength), Integer.toString(commitment.assembledFleetCount),
                    Double.toString(commitment.assembledStrength), Double.toString(commitment.musterProgress),
                    Double.toString(commitment.travelProgress)));
        }
        return state.nextOperationId + "#" + String.join(";", records);
    }

    public static State restore(String encoded) {
        State state = new State();
        if (encoded == null || encoded.isBlank()) return state;
        String[] top = encoded.split("#", 2);
        try { state.nextOperationId = Math.max(1, Integer.parseInt(top[0])); } catch (RuntimeException ignored) {}
        if (top.length < 2 || top[1].isBlank()) return state;
        for (String record : top[1].split(";")) {
            String[] fields = record.split("\\|", -1);
            if (fields.length < 11) continue;
            try {
                Slot slot = Slot.valueOf(fields[1]);
                Commitment commitment = new Commitment(decode(fields[0]), slot, decode(fields[2]), decode(fields[3]),
                        decode(fields[4]), Double.parseDouble(fields[6]), Double.parseDouble(fields[7]),
                        Phase.valueOf(fields[5]));
                String fleets = decode(fields[8]);
                if (!fleets.isBlank()) {
                    for (String fleet : fleets.split(",")) commitment.supportingFleetIds.add(Integer.parseInt(fleet));
                }
                commitment.ownershipApplied = Boolean.parseBoolean(fields[9]);
                commitment.lastOwnershipChange = decode(fields[10]);
                if (fields.length >= 20) {
                    commitment.minimumFleetCount = Math.max(1, Integer.parseInt(fields[11]));
                    commitment.minimumMusterRatio = clamp01(Double.parseDouble(fields[12]));
                    commitment.minimumStrengthRatio = clamp01(Double.parseDouble(fields[13]));
                    commitment.plannedFleetCount = Math.max(0, Integer.parseInt(fields[14]));
                    commitment.plannedStrength = Math.max(0.0, Double.parseDouble(fields[15]));
                    commitment.assembledFleetCount = Math.max(0, Integer.parseInt(fields[16]));
                    commitment.assembledStrength = Math.max(0.0, Double.parseDouble(fields[17]));
                    commitment.musterProgress = clamp01(Double.parseDouble(fields[18]));
                    commitment.travelProgress = clamp01(Double.parseDouble(fields[19]));
                }
                if (commitment.occupiesSlot() && !commitment.originLocationId.isBlank()
                        && !commitment.targetLocationId.isBlank()) state.active.put(slot, commitment);
            } catch (RuntimeException ignored) {
                // Malformed records are dropped; campaign integration records the recovery diagnostic.
            }
        }
        return state;
    }

    private static String stableId(String value) { return value == null ? "" : value.trim(); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stableId(value).getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) {
        if (value == null || value.isBlank()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
