import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Timed schematic operations for boarding and rescue; integrates through abstract teams and campaign consequences. */
public final class BoardingRescueSystem {
    public enum Objective { CAPTURE_BRIDGE, DISABLE_SHIP, RECOVER_INTELLIGENCE, RESCUE_PRISONERS, SABOTAGE, PREVENT_SCUTTLE, RECOVER_SURVIVORS }
    public enum Status { ACTIVE, PARTIAL_SUCCESS, SUCCEEDED, FAILED, ABORTED, SURRENDERED, CATASTROPHIC_LOSS }
    public enum Phase { APPROACH, HULL_BREACH, ENTRY, COMPARTMENT_MOVEMENT, OBJECTIVE, EXTRACTION, COMPLETE }
    public enum TargetType { SHIP, WRECK, STATION, ESCAPE_CRAFT, HAZARDOUS_ZONE }
    public enum TreatmentPolicy { HUMANE, STANDARD, AUSTERE }

    public static final class Operation {
        public final String id;
        public final Objective objective;
        public final String targetId;
        public TargetType targetType = TargetType.SHIP;
        public final List<String> assignedTeamIds = new ArrayList<>();
        public Status status = Status.ACTIVE;
        public Phase phase = Phase.APPROACH;
        public int intelligenceQuality;
        public int hiddenResistance = 50;
        public int estimatedResistance = 50;
        public int securitySystems = 50;
        public int marineReadiness = 50;
        public int doorControl = 50;
        public int sensorCoverage = 50;
        public int powerAvailability = 50;
        public int medicalCapacityRequired;
        public int transportCapacityRequired;
        public double timeRemainingSec;
        public double progress;
        public double radiation;
        public double debris;
        public double decompression;
        public double fire;
        public int casualties;
        public int survivorsRecovered;
        public int prisonersRecovered;
        public boolean hostileCounterBoarding;
        public boolean extractionReady;
        public boolean targetEligible = true;
        public double capturedHullCondition;
        public String capturedOwner = "";
        public String recommendedPlan = "";
        public String followUpOperation = "";
        public boolean consequencesApplied;

        Operation(String id, Objective objective, String targetId, int intelligenceQuality, double timeRemainingSec) {
            this.id = normalize(id, "operation");
            this.objective = objective == null ? Objective.RECOVER_SURVIVORS : objective;
            this.targetId = normalize(targetId, "unknown-target");
            this.intelligenceQuality = MathUtil.clamp(intelligenceQuality, 0, 100);
            this.timeRemainingSec = Math.max(1.0, timeRemainingSec);
        }
    }

    public static final class State {
        public final List<Operation> operations = new ArrayList<>();
        public int nextOperationId = 1;
        public int prisonersHeld;
        public int totalSurvivorsRecovered;
        public int prisonerCapacity = 20;
        public int prisonerSecurity = 75;
        public TreatmentPolicy treatmentPolicy = TreatmentPolicy.HUMANE;
        public int reputationDelta;
        public int moraleDelta;
        public int intelligenceRecovered;
        public final List<String> survivorRecords = new ArrayList<>();
        public final List<String> unresolvedConsequences = new ArrayList<>();
    }

    private BoardingRescueSystem() {}
    public static State bootstrap() { return new State(); }

    public static Operation start(State state, Objective objective, String targetId, int intelligenceQuality,
                                  double timeLimitSec, String... teamIds) {
        return startEligible(state, objective, targetId, TargetType.SHIP, intelligenceQuality, timeLimitSec,
                true, 20, 20, teamIds);
    }

    public static Operation startEligible(State state, Objective objective, String targetId, TargetType targetType,
                                          int intelligenceQuality, double timeLimitSec, boolean legallyEligible,
                                          int medicalCapacity, int transportCapacity, String... teamIds) {
        if (state == null) return null;
        if (!legallyEligible || timeLimitSec <= 0.0 || teamIds == null || teamIds.length == 0) return null;
        if (objective != Objective.RECOVER_SURVIVORS && targetType != TargetType.SHIP && targetType != TargetType.STATION) return null;
        Operation operation = new Operation("br-" + state.nextOperationId++, objective, targetId,
                intelligenceQuality, timeLimitSec);
        operation.targetType = targetType == null ? TargetType.SHIP : targetType;
        operation.medicalCapacityRequired = Math.max(0, medicalCapacity);
        operation.transportCapacityRequired = Math.max(0, transportCapacity);
        if (teamIds != null) for (String teamId : teamIds) if (teamId != null && !teamId.isBlank()) operation.assignedTeamIds.add(teamId.trim());
        operation.estimatedResistance = MathUtil.clamp(operation.hiddenResistance
                + (50 - operation.intelligenceQuality) / 3, 0, 100);
        operation.recommendedPlan = recommendedPlan(operation);
        state.operations.add(operation);
        return operation;
    }

    public static void update(State state, Operation operation, double dt, double hazard, boolean hostileInterference) {
        if (state == null || operation == null || operation.status != Status.ACTIVE || dt <= 0.0) return;
        operation.timeRemainingSec = Math.max(0.0, operation.timeRemainingSec - dt);
        double teams = Math.max(0.35, operation.assignedTeamIds.size());
        double intel = 0.55 + operation.intelligenceQuality / 200.0;
        double environment = hazard + operation.radiation + operation.debris + operation.decompression + operation.fire;
        double defenses = (operation.hiddenResistance + operation.securitySystems + operation.marineReadiness
                + operation.doorControl + operation.sensorCoverage) / 500.0;
        double danger = MathUtil.clamp(environment, 0.0, 6.0) * 0.07 + defenses * 0.16
                + (hostileInterference || operation.hostileCounterBoarding ? 0.22 : 0.0)
                + (operation.powerAvailability < 25 ? 0.08 : 0.0);
        operation.progress = MathUtil.clamp(operation.progress + dt * teams * intel * (1.0 - danger) * 6.0, 0.0, 100.0);
        operation.phase = phaseForProgress(operation.progress);
        if (danger > 0.28 && ((int) Math.floor(operation.timeRemainingSec)) % 7 == 0) operation.casualties++;
        if (operation.progress >= 100.0) {
            operation.status = Status.SUCCEEDED;
            operation.phase = Phase.COMPLETE;
            operation.extractionReady = true;
            if (operation.objective == Objective.RECOVER_SURVIVORS) {
                int capacity = Math.max(0, operation.transportCapacityRequired);
                operation.survivorsRecovered = Math.min(capacity,
                        Math.max(1, operation.assignedTeamIds.size() * 4 - operation.casualties));
                state.totalSurvivorsRecovered += operation.survivorsRecovered;
                state.moraleDelta += operation.survivorsRecovered;
                state.reputationDelta += Math.max(1, operation.survivorsRecovered / 2);
                state.survivorRecords.add(operation.targetId + ":" + operation.survivorsRecovered);
            } else if (operation.objective == Objective.RESCUE_PRISONERS) {
                operation.prisonersRecovered = Math.min(Math.max(0, state.prisonerCapacity - state.prisonersHeld),
                        Math.max(1, operation.assignedTeamIds.size() * 2 - operation.casualties));
                state.prisonersHeld += operation.prisonersRecovered;
            } else if (operation.objective == Objective.RECOVER_INTELLIGENCE) {
                state.intelligenceRecovered += Math.max(1, operation.intelligenceQuality / 10);
                operation.followUpOperation = "Investigate captured intelligence from " + operation.targetId;
                state.unresolvedConsequences.add(operation.followUpOperation);
            } else if (operation.objective == Objective.CAPTURE_BRIDGE) {
                operation.capturedHullCondition = MathUtil.clamp(1.0 - danger * 0.5, 0.05, 1.0);
                operation.capturedOwner = "CAPTOR";
            } else {
                state.prisonersHeld += Math.max(1, 3 - operation.casualties);
            }
        } else if (operation.timeRemainingSec <= 0.0) {
            operation.status = operation.casualties >= operation.assignedTeamIds.size() * 4
                    ? Status.CATASTROPHIC_LOSS : (operation.progress >= 50.0 ? Status.PARTIAL_SUCCESS : Status.FAILED);
            if (operation.status == Status.PARTIAL_SUCCESS && operation.objective == Objective.RECOVER_SURVIVORS) {
                operation.survivorsRecovered = Math.min(operation.transportCapacityRequired,
                        Math.max(1, operation.assignedTeamIds.size()));
                state.totalSurvivorsRecovered += operation.survivorsRecovered;
            }
            state.unresolvedConsequences.add(operation.targetId + ":" + operation.status);
        }
    }

    public static void setHazards(Operation operation, double decompression, double fire, double radiation, double debris) {
        if (operation == null) return;
        operation.decompression = MathUtil.clamp(decompression, 0.0, 2.0);
        operation.fire = MathUtil.clamp(fire, 0.0, 2.0);
        operation.radiation = MathUtil.clamp(radiation, 0.0, 2.0);
        operation.debris = MathUtil.clamp(debris, 0.0, 2.0);
    }

    public static String recommendedPlan(Operation operation) {
        if (operation == null) return "No operation";
        if (operation.objective == Objective.RECOVER_SURVIVORS) return "Prioritize medical and transport capacity; extract before the countdown";
        if (operation.intelligenceQuality < 40) return "Reconnoiter entry points before committing the main team";
        if (operation.securitySystems + operation.marineReadiness > 120) return "Disable power and sensors before breaching";
        return "Breach the safest known entry, secure the objective, then extract";
    }

    public static boolean surrender(Operation operation) {
        if (operation == null || operation.status != Status.ACTIVE) return false;
        operation.status = Status.SURRENDERED;
        operation.followUpOperation = "Prisoner and recovery transfer required";
        return true;
    }

    public static boolean scuttle(Operation operation) {
        if (operation == null || operation.status != Status.ACTIVE || operation.objective == Objective.PREVENT_SCUTTLE) return false;
        operation.status = Status.CATASTROPHIC_LOSS;
        operation.capturedHullCondition = 0.0;
        operation.followUpOperation = "Hazardous wreck and survivor search generated";
        return true;
    }

    public static boolean transferPrisoners(State state, int count) {
        if (state == null || count <= 0 || state.prisonersHeld < count) return false;
        state.prisonersHeld -= count;
        state.reputationDelta += state.treatmentPolicy == TreatmentPolicy.HUMANE ? count : 0;
        return true;
    }

    public static boolean releasePrisoners(State state, int count) { return transferPrisoners(state, count); }

    public static boolean exchangePrisoners(State state, int released, int returnedPersonnel) {
        if (!transferPrisoners(state, released)) return false;
        state.moraleDelta += Math.max(0, returnedPersonnel);
        state.reputationDelta += Math.max(0, released / 2);
        return true;
    }

    /** Intelligence questioning is bounded: no coercive treatment mechanic exists. */
    public static int questionPrisoners(State state, int count) {
        if (state == null || count <= 0 || state.prisonersHeld < count || state.prisonerSecurity < 30) return 0;
        int intelligence = Math.max(1, count / 2);
        if (state.treatmentPolicy == TreatmentPolicy.AUSTERE) state.reputationDelta -= count;
        state.intelligenceRecovered += intelligence;
        return intelligence;
    }

    public static void recordConductConsequences(State state, int casualties, int collateralDamage,
                                                 boolean surrenderViolation) {
        if (state == null) return;
        state.reputationDelta -= Math.max(0, casualties / 2) + Math.max(0, collateralDamage);
        if (surrenderViolation) state.reputationDelta -= 20;
        state.moraleDelta -= Math.max(0, casualties / 3);
        if (surrenderViolation) state.unresolvedConsequences.add("Surrender violation requires diplomatic review");
    }

    private static Phase phaseForProgress(double progress) {
        if (progress < 15) return Phase.APPROACH;
        if (progress < 30) return Phase.HULL_BREACH;
        if (progress < 45) return Phase.ENTRY;
        if (progress < 70) return Phase.COMPARTMENT_MOVEMENT;
        if (progress < 90) return Phase.OBJECTIVE;
        if (progress < 100) return Phase.EXTRACTION;
        return Phase.COMPLETE;
    }

    public static boolean abort(Operation operation) {
        if (operation == null || operation.status != Status.ACTIVE) return false;
        operation.status = operation.progress >= 50.0 ? Status.PARTIAL_SUCCESS : Status.ABORTED;
        return true;
    }

    public static String consequence(Operation operation) {
        if (operation == null) return "No operation recorded";
        return operation.objective + "  |  " + operation.status + "  |  progress " + Math.round(operation.progress)
                + "%  casualties " + operation.casualties + "  recovered " + operation.survivorsRecovered;
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        StringBuilder ops = new StringBuilder();
        for (Operation operation : state.operations) {
            if (!ops.isEmpty()) ops.append(';');
            ops.append(enc(encoder, operation.id)).append(':').append(operation.objective).append(':')
                    .append(enc(encoder, operation.targetId)).append(':').append(operation.status).append(':')
                    .append(operation.intelligenceQuality).append(':').append(operation.timeRemainingSec).append(':')
                    .append(operation.progress).append(':').append(operation.casualties).append(':')
                    .append(operation.survivorsRecovered).append(':')
                    .append(enc(encoder, String.join(",", operation.assignedTeamIds))).append(':')
                    .append(operation.targetType).append(':').append(operation.phase).append(':')
                    .append(operation.hiddenResistance).append(':').append(operation.estimatedResistance).append(':')
                    .append(operation.securitySystems).append(':').append(operation.marineReadiness).append(':')
                    .append(operation.doorControl).append(':').append(operation.sensorCoverage).append(':')
                    .append(operation.powerAvailability).append(':').append(operation.medicalCapacityRequired).append(':')
                    .append(operation.transportCapacityRequired).append(':').append(operation.radiation).append(':')
                    .append(operation.debris).append(':').append(operation.decompression).append(':').append(operation.fire).append(':')
                    .append(operation.prisonersRecovered).append(':').append(operation.hostileCounterBoarding).append(':')
                    .append(operation.extractionReady).append(':').append(operation.targetEligible).append(':')
                    .append(operation.capturedHullCondition).append(':').append(enc(encoder, operation.capturedOwner)).append(':')
                    .append(enc(encoder, operation.recommendedPlan)).append(':').append(enc(encoder, operation.followUpOperation)).append(':')
                    .append(operation.consequencesApplied);
        }
        return state.nextOperationId + "|" + state.prisonersHeld + "|" + state.totalSurvivorsRecovered + "|" + ops + "|"
                + state.prisonerCapacity + ":" + state.prisonerSecurity + ":" + state.treatmentPolicy + ":" + state.reputationDelta + ":"
                + state.moraleDelta + ":" + state.intelligenceRecovered + ":"
                + enc(encoder, String.join(",", state.survivorRecords)) + ":"
                + enc(encoder, String.join(",", state.unresolvedConsequences));
    }

    public static State restore(String raw) {
        State state = bootstrap();
        if (raw == null || raw.isBlank()) return state;
        String[] p = raw.split("\\|", -1);
        if (p.length < 4) return state;
        state.nextOperationId = Math.max(1, integer(p[0], 1));
        state.prisonersHeld = Math.max(0, integer(p[1], 0));
        state.totalSurvivorsRecovered = Math.max(0, integer(p[2], 0));
        for (String rawOperation : p[3].split(";")) {
            if (rawOperation.isBlank()) continue;
            String[] f = rawOperation.split(":", -1);
            if (f.length < 10) continue;
            Operation operation = new Operation(dec(f[0], "operation"), enumeration(f[1], Objective.RECOVER_SURVIVORS),
                    dec(f[2], "target"), integer(f[4], 0), decimal(f[5], 1.0));
            operation.status = enumeration(f[3], Status.ACTIVE);
            operation.progress = MathUtil.clamp(decimal(f[6], 0.0), 0.0, 100.0);
            operation.casualties = Math.max(0, integer(f[7], 0));
            operation.survivorsRecovered = Math.max(0, integer(f[8], 0));
            String teams = dec(f[9], "");
            if (!teams.isBlank()) operation.assignedTeamIds.addAll(List.of(teams.split(",")));
            if (f.length >= 33) {
                operation.targetType = enumeration(f[10], TargetType.SHIP);
                operation.phase = enumeration(f[11], Phase.APPROACH);
                operation.hiddenResistance = MathUtil.clamp(integer(f[12], 50), 0, 100);
                operation.estimatedResistance = MathUtil.clamp(integer(f[13], 50), 0, 100);
                operation.securitySystems = MathUtil.clamp(integer(f[14], 50), 0, 100);
                operation.marineReadiness = MathUtil.clamp(integer(f[15], 50), 0, 100);
                operation.doorControl = MathUtil.clamp(integer(f[16], 50), 0, 100);
                operation.sensorCoverage = MathUtil.clamp(integer(f[17], 50), 0, 100);
                operation.powerAvailability = MathUtil.clamp(integer(f[18], 50), 0, 100);
                operation.medicalCapacityRequired = Math.max(0, integer(f[19], 0));
                operation.transportCapacityRequired = Math.max(0, integer(f[20], 0));
                operation.radiation = MathUtil.clamp(decimal(f[21], 0.0), 0.0, 2.0);
                operation.debris = MathUtil.clamp(decimal(f[22], 0.0), 0.0, 2.0);
                operation.decompression = MathUtil.clamp(decimal(f[23], 0.0), 0.0, 2.0);
                operation.fire = MathUtil.clamp(decimal(f[24], 0.0), 0.0, 2.0);
                operation.prisonersRecovered = Math.max(0, integer(f[25], 0));
                operation.hostileCounterBoarding = Boolean.parseBoolean(f[26]);
                operation.extractionReady = Boolean.parseBoolean(f[27]);
                operation.targetEligible = Boolean.parseBoolean(f[28]);
                operation.capturedHullCondition = MathUtil.clamp(decimal(f[29], 0.0), 0.0, 1.0);
                operation.capturedOwner = dec(f[30], "");
                operation.recommendedPlan = dec(f[31], recommendedPlan(operation));
                operation.followUpOperation = dec(f[32], "");
                if (f.length >= 34) operation.consequencesApplied = Boolean.parseBoolean(f[33]);
            }
            state.operations.add(operation);
        }
        if (p.length >= 5) {
            String[] f = p[4].split(":", -1);
            if (f.length >= 8) {
                state.prisonerCapacity = Math.max(0, integer(f[0], 20));
                state.prisonerSecurity = MathUtil.clamp(integer(f[1], 75), 0, 100);
                state.treatmentPolicy = enumeration(f[2], TreatmentPolicy.HUMANE);
                state.reputationDelta = integer(f[3], 0);
                state.moraleDelta = integer(f[4], 0);
                state.intelligenceRecovered = Math.max(0, integer(f[5], 0));
                String survivors = dec(f[6], "");
                if (!survivors.isBlank()) state.survivorRecords.addAll(List.of(survivors.split(",")));
                String consequences = dec(f[7], "");
                if (!consequences.isBlank()) state.unresolvedConsequences.addAll(List.of(consequences.split(",")));
            } else if (f.length >= 7) {
                state.prisonerCapacity = Math.max(0, integer(f[0], 20));
                state.treatmentPolicy = enumeration(f[1], TreatmentPolicy.HUMANE);
                state.reputationDelta = integer(f[2], 0);
                state.moraleDelta = integer(f[3], 0);
                state.intelligenceRecovered = Math.max(0, integer(f[4], 0));
                String survivors = dec(f[5], "");
                if (!survivors.isBlank()) state.survivorRecords.addAll(List.of(survivors.split(",")));
                String consequences = dec(f[6], "");
                if (!consequences.isBlank()) state.unresolvedConsequences.addAll(List.of(consequences.split(",")));
            }
        }
        return state;
    }

    private static String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String enc(Base64.Encoder e, String value) { return e.encodeToString(normalize(value, "").getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value, String fallback) { try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); } catch (Exception ignored) { return fallback; } }
    private static int integer(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static double decimal(String value, double fallback) { try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; } }
    private static <T extends Enum<T>> T enumeration(String value, T fallback) { try { return Enum.valueOf(fallback.getDeclaringClass(), value); } catch (Exception ignored) { return fallback; } }
}
