import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 2D schematic flagship command model. Personnel are abstract teams; no face, video, or voice assets. */
public final class FlagshipOperationsSystem {
    public enum TeamOrder { STANDBY, CONTAIN_FIRE, RESTORE_SYSTEM, EVACUATE, MEDICAL_RESPONSE }
    public enum AutomationMode { FULL, ADVISORY, MANUAL }
    public enum SystemType { BRIDGE_CIC, ENGINEERING, REACTOR, SENSORS, WEAPONS, PROPULSION, SHIELDS, HANGAR, MEDICAL, LIFE_SUPPORT, MARINES, STORES, DAMAGE_CONTROL, GENERAL }
    public record CombatEffects(double propulsion, double shields, double weapons, double sensors,
                                double lifeSupport, double hangar, double repairs, double medicalPerformance) {}
    public record CriticalWarning(String compartmentId, String text, String icon, String pattern,
                                  String optionalAudioCue, int priority) {}

    public static final class Compartment {
        public final String id;
        public String label;
        public double integrity = 1.0;
        public double fire;
        public boolean disrupted;
        public boolean decompressed;
        public int powerPriority = 2;
        public SystemType systemType = SystemType.GENERAL;
        public final List<String> connections = new ArrayList<>();
        public int powerDemand = 10;
        public int powerAllocated = 10;
        public double coolantLeak;
        public double electricalFault;
        public double structuralCollapse;
        public int personnelCapacity = 10;
        public int injuries;
        public boolean sealed;
        public boolean evacuated;
        public boolean vented;
        public String offlineReason = "Operational";

        Compartment(String id, String label) {
            this.id = normalize(id, "compartment");
            this.label = normalize(label, this.id);
        }
    }

    public static final class DamageControlTeam {
        public final String id;
        public String assignedCompartmentId = "";
        public TeamOrder order = TeamOrder.STANDBY;
        public int readiness = 100;
        public int casualties;
        public boolean available = true;

        DamageControlTeam(String id) { this.id = normalize(id, "team"); }
    }

    public static final class State {
        public final Map<String, Compartment> compartments = new LinkedHashMap<>();
        public final Map<String, DamageControlTeam> teams = new LinkedHashMap<>();
        public AutomationMode automation = AutomationMode.FULL;
        public int medicalCapacity = 20;
        public int casualties;
        public int powerGeneration = 100;
        public int availablePower = 100;
        public int emergencyPowerCooldownTicks;
        public int repairParts = 40;
        public int emergencyReserves = 20;
        public int marineReadiness = 100;
        public int storedCraft = 8;
        public int craftFuel = 100;
        public int craftAmmunition = 100;
        public int launchCapacity = 4;
        public int recoveryCapacity = 4;
        public int deckDamage;
        public int timePressurePercent = 100;
        public String selectedCompartmentId = "";
        public double schematicZoom = 1.0;
        public double schematicPanX;
        public double schematicPanY;
        public boolean schematicVisible;
        public boolean slowTimeRequested;
    }

    private FlagshipOperationsSystem() {}

    public static State bootstrap() {
        State state = new State();
        for (int i = 1; i <= 3; i++) state.teams.put("dc-" + i, new DamageControlTeam("dc-" + i));
        return state;
    }

    public static void syncFromShip(State state, Ship ship) {
        if (state == null || ship == null) return;
        String previous = null;
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            String id = room.roomId.name();
            Compartment compartment = state.compartments.computeIfAbsent(id,
                    ignored -> new Compartment(id, room.label));
            compartment.label = room.label;
            compartment.integrity = MathUtil.clamp(room.hp / Math.max(1.0, room.hpMax), 0.0, 1.0);
            compartment.fire = MathUtil.clamp(room.fireIntensity, 0.0, 2.0);
            compartment.disrupted = room.disrupted;
            compartment.systemType = inferSystemType(id, room.label);
            compartment.powerDemand = defaultPowerDemand(compartment.systemType);
            if (previous != null) connect(state, previous, id);
            previous = id;
        }
        routePower(state);
    }

    public static boolean connect(State state, String firstId, String secondId) {
        if (state == null || firstId == null || secondId == null || firstId.equals(secondId)) return false;
        Compartment first = state.compartments.get(firstId);
        Compartment second = state.compartments.get(secondId);
        if (first == null || second == null) return false;
        if (!first.connections.contains(secondId)) first.connections.add(secondId);
        if (!second.connections.contains(firstId)) second.connections.add(firstId);
        return true;
    }

    public static boolean assignTeam(State state, String teamId, String compartmentId, TeamOrder order) {
        if (state == null || !state.compartments.containsKey(compartmentId)) return false;
        DamageControlTeam team = state.teams.get(teamId);
        if (team == null || team.readiness <= 0) return false;
        team.assignedCompartmentId = compartmentId;
        team.order = order == null ? TeamOrder.STANDBY : order;
        return true;
    }

    public static void setEmergency(State state, String compartmentId, double fire, boolean decompressed, boolean disrupted) {
        Compartment compartment = state == null ? null : state.compartments.get(compartmentId);
        if (compartment == null) return;
        compartment.fire = MathUtil.clamp(fire, 0.0, 2.0);
        compartment.decompressed = decompressed;
        compartment.disrupted = disrupted;
    }

    public static void setHazards(State state, String compartmentId, double coolantLeak,
                                  double electricalFault, double structuralCollapse) {
        Compartment compartment = state == null ? null : state.compartments.get(compartmentId);
        if (compartment == null) return;
        compartment.coolantLeak = MathUtil.clamp(coolantLeak, 0.0, 2.0);
        compartment.electricalFault = MathUtil.clamp(electricalFault, 0.0, 2.0);
        compartment.structuralCollapse = MathUtil.clamp(structuralCollapse, 0.0, 2.0);
    }

    public static void routePower(State state) {
        if (state == null) return;
        int remaining = Math.max(0, state.powerGeneration + state.emergencyReserves - state.deckDamage / 4);
        ArrayList<Compartment> ordered = new ArrayList<>(state.compartments.values());
        ordered.sort((a, b) -> Integer.compare(b.powerPriority, a.powerPriority));
        for (Compartment compartment : ordered) {
            int demand = Math.max(0, compartment.powerDemand);
            compartment.powerAllocated = Math.min(demand, remaining);
            remaining -= compartment.powerAllocated;
            if (compartment.powerAllocated < demand) {
                compartment.disrupted = true;
                compartment.offlineReason = "Underpowered: " + compartment.powerAllocated + "/" + demand;
            } else if (compartment.integrity < 0.25) {
                compartment.disrupted = true;
                compartment.offlineReason = "Offline: severe compartment damage";
            } else {
                compartment.offlineReason = "Operational";
            }
        }
        state.availablePower = remaining;
    }

    public static boolean emergencyRedistribute(State state, String compartmentId, int extraPower) {
        Compartment target = state == null ? null : state.compartments.get(compartmentId);
        if (target == null || extraPower <= 0 || state.emergencyPowerCooldownTicks > 0
                || state.emergencyReserves < extraPower) return false;
        state.emergencyReserves -= extraPower;
        target.powerPriority = 4;
        target.electricalFault = MathUtil.clamp(target.electricalFault + extraPower / 100.0, 0.0, 2.0);
        state.emergencyPowerCooldownTicks = 5;
        routePower(state);
        return true;
    }

    public static boolean setCompartmentSafety(State state, String compartmentId, boolean sealed,
                                               boolean evacuate, boolean vent) {
        Compartment compartment = state == null ? null : state.compartments.get(compartmentId);
        if (compartment == null) return false;
        compartment.sealed = sealed;
        compartment.evacuated = evacuate;
        compartment.vented = vent;
        if (vent) {
            compartment.fire = Math.max(0.0, compartment.fire - 0.8);
            compartment.decompressed = true;
        }
        return true;
    }

    public static void restoreAtDock(State state, int repairParts, int medicalCapacity,
                                     int craftFuel, int craftAmmunition) {
        if (state == null) return;
        state.repairParts = Math.max(0, state.repairParts + repairParts);
        state.medicalCapacity = Math.max(0, state.medicalCapacity + medicalCapacity);
        state.craftFuel = MathUtil.clamp(state.craftFuel + craftFuel, 0, 100);
        state.craftAmmunition = MathUtil.clamp(state.craftAmmunition + craftAmmunition, 0, 100);
        for (DamageControlTeam team : state.teams.values()) {
            team.readiness = Math.min(100, team.readiness + 30);
            team.available = team.casualties < 5;
        }
    }

    public static void update(State state, double dt) {
        if (state == null || dt <= 0.0) return;
        if (state.automation == AutomationMode.FULL) autoAssign(state);
        if (state.emergencyPowerCooldownTicks > 0) state.emergencyPowerCooldownTicks--;
        spreadHazards(state, dt);
        applyMedicalAndCasualtyEffects(state);
        routePower(state);
        for (DamageControlTeam team : state.teams.values()) {
            Compartment compartment = state.compartments.get(team.assignedCompartmentId);
            if (compartment == null || team.order == TeamOrder.STANDBY || !team.available) continue;
            double rate = dt * Math.max(0.2, team.readiness / 100.0);
            switch (team.order) {
                case CONTAIN_FIRE -> {
                    compartment.fire = Math.max(0.0, compartment.fire - rate * 0.28);
                    compartment.coolantLeak = Math.max(0.0, compartment.coolantLeak - rate * 0.10);
                    compartment.electricalFault = Math.max(0.0, compartment.electricalFault - rate * 0.10);
                }
                case RESTORE_SYSTEM -> {
                    if (state.repairParts > 0) {
                        compartment.integrity = Math.min(1.0, compartment.integrity + rate * 0.035);
                        state.repairParts = Math.max(0, state.repairParts - 1);
                    }
                    if (compartment.integrity >= 0.45) compartment.disrupted = false;
                }
                case EVACUATE -> compartment.decompressed = false;
                case MEDICAL_RESPONSE -> state.casualties = Math.max(0, state.casualties - (int) Math.floor(rate));
                default -> { }
            }
            team.readiness = Math.max(0, team.readiness - (int) Math.ceil(dt * 0.15));
        }
    }

    private static void applyMedicalAndCasualtyEffects(State state) {
        int injuries = state.compartments.values().stream().mapToInt(c -> Math.max(0, c.injuries)).sum();
        state.casualties = Math.max(state.casualties, injuries);
        int untreated = Math.max(0, state.casualties - state.medicalCapacity);
        for (DamageControlTeam team : state.teams.values()) {
            team.readiness = Math.max(0, team.readiness - Math.min(20, untreated / Math.max(1, state.teams.size())));
            team.available = team.casualties < 5 && team.readiness > 0;
        }
    }

    public static CombatEffects combatEffects(State state) {
        if (state == null) return new CombatEffects(1, 1, 1, 1, 1, 1, 1, 1);
        return new CombatEffects(effectFor(state, SystemType.PROPULSION), effectFor(state, SystemType.SHIELDS),
                effectFor(state, SystemType.WEAPONS), effectFor(state, SystemType.SENSORS),
                effectFor(state, SystemType.LIFE_SUPPORT), effectFor(state, SystemType.HANGAR),
                Math.min(effectFor(state, SystemType.ENGINEERING), effectFor(state, SystemType.DAMAGE_CONTROL)),
                MathUtil.clamp(state.medicalCapacity / Math.max(1.0, state.casualties + state.medicalCapacity), 0.2, 1.0));
    }

    private static double effectFor(State state, SystemType type) {
        List<Compartment> relevant = state.compartments.values().stream().filter(c -> c.systemType == type).toList();
        if (relevant.isEmpty()) return 1.0;
        double sum = 0.0;
        for (Compartment c : relevant) {
            double power = c.powerDemand <= 0 ? 1.0 : MathUtil.clamp(c.powerAllocated / (double) c.powerDemand, 0.0, 1.0);
            double pressure = c.decompressed && !c.evacuated ? 0.35 : 1.0;
            sum += MathUtil.clamp(c.integrity * power * pressure * (c.disrupted ? 0.5 : 1.0), 0.05, 1.0);
        }
        return sum / relevant.size();
    }

    /** Ship rooms remain the damage authority; the schematic projects their state into live power-bus effects. */
    public static CombatEffects applyToShip(State state, Ship ship) {
        if (state == null || ship == null) return combatEffects(state);
        CombatEffects effects = combatEffects(state);
        ship.setPowerBusAllocation(0.18 * effects.propulsion(), 0.18 * effects.shields(),
                0.19 * effects.weapons(), 0.15 * effects.sensors(),
                0.18 * effects.repairs(), 0.12 * Math.min(effects.hangar(), effects.lifeSupport()));
        return effects;
    }

    public static int reconcileCampaignRepairs(State state, Ship ship, int availableParts) {
        if (state == null || ship == null || availableParts <= 0) return 0;
        int spent = Math.min(Math.min(availableParts, state.repairParts),
                Math.max(1, (int) Math.ceil((1.0 - combatEffects(state).repairs()) * 20.0)));
        if (spent <= 0) return 0;
        ship.applySupportField(spent * 0.0025, spent * 0.006, 1.0);
        state.repairParts -= spent;
        syncFromShip(state, ship);
        return spent;
    }

    public static boolean selectCompartment(State state, int direction) {
        if (state == null || state.compartments.isEmpty()) return false;
        List<String> ids = new ArrayList<>(state.compartments.keySet());
        int current = ids.indexOf(state.selectedCompartmentId);
        state.selectedCompartmentId = ids.get(Math.floorMod(current + (direction < 0 ? -1 : 1), ids.size()));
        return true;
    }

    public static double zoomSchematic(State state, double delta) {
        if (state == null) return 1.0;
        state.schematicZoom = MathUtil.clamp(state.schematicZoom + delta, 0.65, 2.5);
        return state.schematicZoom;
    }

    public static void panSchematic(State state, double dx, double dy) {
        if (state == null) return;
        state.schematicPanX = MathUtil.clamp(state.schematicPanX + dx, -1.0, 1.0);
        state.schematicPanY = MathUtil.clamp(state.schematicPanY + dy, -1.0, 1.0);
    }

    public static List<CriticalWarning> criticalWarnings(State state) {
        if (state == null) return List.of();
        ArrayList<CriticalWarning> out = new ArrayList<>();
        for (Compartment c : state.compartments.values()) {
            if (c.fire > 0.2) out.add(new CriticalWarning(c.id, c.label + " fire", "FIRE", "diagonal-stripes", "alarm.fire", 90));
            if (c.decompressed) out.add(new CriticalWarning(c.id, c.label + " decompressed", "PRESSURE", "crosshatch", "alarm.pressure", 100));
            if (c.powerDemand > 0 && c.powerAllocated < c.powerDemand) out.add(new CriticalWarning(c.id,
                    c.label + " underpowered", "POWER", "dotted", "alarm.power", 70));
        }
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return List.copyOf(out);
    }

    private static void spreadHazards(State state, double dt) {
        Map<String, double[]> additions = new LinkedHashMap<>();
        for (Compartment source : state.compartments.values()) {
            double damage = (source.fire * 0.004 + source.coolantLeak * 0.002 + source.electricalFault * 0.002
                    + source.structuralCollapse * 0.006) * dt * state.timePressurePercent / 100.0;
            source.integrity = Math.max(0.0, source.integrity - damage);
            if (source.integrity <= 0.0) {
                source.disrupted = true;
                source.evacuated = true;
            }
            if (source.evacuated || source.personnelCapacity <= 0) continue;
            if ((source.fire + (source.decompressed ? 1.0 : 0.0) + source.structuralCollapse) > 1.5) {
                int injuries = Math.max(0, (int) Math.floor(dt * 0.08));
                source.injuries += injuries;
                state.casualties += injuries;
            }
            if (source.sealed) continue;
            for (String neighborId : source.connections) {
                double[] add = additions.computeIfAbsent(neighborId, ignored -> new double[2]);
                add[0] += source.fire * 0.025 * dt;
                add[1] += source.decompressed ? 0.04 * dt : 0.0;
            }
        }
        for (Map.Entry<String, double[]> entry : additions.entrySet()) {
            Compartment target = state.compartments.get(entry.getKey());
            if (target == null) continue;
            target.fire = MathUtil.clamp(target.fire + entry.getValue()[0], 0.0, 2.0);
            if (entry.getValue()[1] >= 0.5) target.decompressed = true;
        }
    }

    private static void autoAssign(State state) {
        ArrayList<Compartment> emergencies = new ArrayList<>(state.compartments.values());
        emergencies.sort((a, b) -> Double.compare(emergencyScore(b), emergencyScore(a)));
        int index = 0;
        for (DamageControlTeam team : state.teams.values()) {
            if (index >= emergencies.size() || emergencyScore(emergencies.get(index)) <= 0.0) {
                team.assignedCompartmentId = "";
                team.order = TeamOrder.STANDBY;
                continue;
            }
            Compartment target = emergencies.get(index++);
            team.assignedCompartmentId = target.id;
            team.order = target.fire > 0.05 ? TeamOrder.CONTAIN_FIRE
                    : (target.decompressed ? TeamOrder.EVACUATE : TeamOrder.RESTORE_SYSTEM);
        }
    }

    private static double emergencyScore(Compartment c) {
        return c.fire * 3.0 + c.coolantLeak + c.electricalFault + c.structuralCollapse * 2.0
                + (c.decompressed ? 2.0 : 0.0) + (c.disrupted ? 1.0 : 0.0) + (1.0 - c.integrity);
    }

    private static SystemType inferSystemType(String id, String label) {
        String text = ((id == null ? "" : id) + " " + (label == null ? "" : label)).toUpperCase(Locale.ROOT);
        if (text.contains("BRIDGE") || text.contains("CIC") || text.contains("COMMAND")) return SystemType.BRIDGE_CIC;
        if (text.contains("REACTOR")) return SystemType.REACTOR;
        if (text.contains("ENGINEER") || text.contains("COOLANT")) return SystemType.ENGINEERING;
        if (text.contains("SENSOR") || text.contains("SCIENCE")) return SystemType.SENSORS;
        if (text.contains("SHIELD")) return SystemType.SHIELDS;
        if (text.contains("WEAPON") || text.contains("MAGAZINE") || text.contains("TACTICAL")) return SystemType.WEAPONS;
        if (text.contains("ENGINE") || text.contains("PROPULSION") || text.contains("HELM")) return SystemType.PROPULSION;
        if (text.contains("HANGAR") || text.contains("FLIGHT")) return SystemType.HANGAR;
        if (text.contains("MEDICAL") || text.contains("SICK")) return SystemType.MEDICAL;
        if (text.contains("LIFE") || text.contains("OXYGEN")) return SystemType.LIFE_SUPPORT;
        if (text.contains("MARINE") || text.contains("SECURITY")) return SystemType.MARINES;
        if (text.contains("STORE") || text.contains("CARGO")) return SystemType.STORES;
        if (text.contains("DAMAGE")) return SystemType.DAMAGE_CONTROL;
        return SystemType.GENERAL;
    }

    private static int defaultPowerDemand(SystemType type) {
        return switch (type) {
            case REACTOR -> 0; case PROPULSION, SHIELDS -> 20;
            case WEAPONS, HANGAR -> 15; case LIFE_SUPPORT, BRIDGE_CIC, SENSORS -> 12;
            default -> 8;
        };
    }

    public static List<String> schematicLines(State state) {
        if (state == null) return List.of("Flagship schematic unavailable");
        ArrayList<String> out = new ArrayList<>();
        out.add("FLAGSHIP  |  compartments " + state.compartments.size() + "  teams " + state.teams.size()
                + "  automation " + state.automation);
        for (Compartment c : state.compartments.values()) {
            out.add(c.label + "  |  integrity " + Math.round(c.integrity * 100.0) + "%  fire "
                    + String.format(Locale.US, "%.2f", c.fire) + "  pressure "
                    + (c.decompressed ? "LOST" : "SEALED") + "  power " + c.powerAllocated + "/" + c.powerDemand
                    + " P" + c.powerPriority + "  " + c.offlineReason + "  links " + c.connections);
        }
        for (DamageControlTeam team : state.teams.values()) {
            out.add("Team " + team.id + "  |  " + team.order + " -> "
                    + (team.assignedCompartmentId.isBlank() ? "unassigned" : team.assignedCompartmentId)
                    + "  readiness " + team.readiness + "%  " + (team.available ? "available" : "unavailable"));
        }
        return List.copyOf(out);
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        StringBuilder compartments = new StringBuilder();
        for (Compartment c : state.compartments.values()) {
            if (!compartments.isEmpty()) compartments.append(';');
            compartments.append(enc(encoder, c.id)).append(':').append(enc(encoder, c.label)).append(':')
                    .append(c.integrity).append(':').append(c.fire).append(':').append(c.disrupted).append(':')
                    .append(c.decompressed).append(':').append(c.powerPriority).append(':').append(c.systemType).append(':')
                    .append(enc(encoder, String.join(",", c.connections))).append(':').append(c.powerDemand).append(':')
                    .append(c.powerAllocated).append(':').append(c.coolantLeak).append(':').append(c.electricalFault).append(':')
                    .append(c.structuralCollapse).append(':').append(c.personnelCapacity).append(':').append(c.injuries).append(':')
                    .append(c.sealed).append(':').append(c.evacuated).append(':').append(c.vented).append(':')
                    .append(enc(encoder, c.offlineReason));
        }
        StringBuilder teams = new StringBuilder();
        for (DamageControlTeam team : state.teams.values()) {
            if (!teams.isEmpty()) teams.append(';');
            teams.append(enc(encoder, team.id)).append(':').append(enc(encoder, team.assignedCompartmentId)).append(':')
                    .append(team.order).append(':').append(team.readiness).append(':').append(team.casualties).append(':')
                    .append(team.available);
        }
        return state.automation + "|" + state.medicalCapacity + "|" + state.casualties + "|"
                + state.availablePower + "|" + compartments + "|" + teams + "|"
                + state.powerGeneration + ":" + state.emergencyPowerCooldownTicks + ":" + state.repairParts + ":"
                + state.emergencyReserves + ":" + state.marineReadiness + ":" + state.storedCraft + ":"
                + state.craftFuel + ":" + state.craftAmmunition + ":" + state.launchCapacity + ":"
                + state.recoveryCapacity + ":" + state.deckDamage + ":" + state.timePressurePercent + ":"
                + enc(encoder, state.selectedCompartmentId) + ":" + state.schematicZoom + ":"
                + state.schematicPanX + ":" + state.schematicPanY + ":" + state.schematicVisible + ":"
                + state.slowTimeRequested;
    }

    public static State restore(String raw) {
        State state = bootstrap();
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 6) return state;
        state.automation = parseEnum(parts[0], AutomationMode.FULL);
        state.medicalCapacity = Math.max(0, parseInt(parts[1], 20));
        state.casualties = Math.max(0, parseInt(parts[2], 0));
        state.availablePower = Math.max(0, parseInt(parts[3], 100));
        state.compartments.clear();
        for (String rawCompartment : parts[4].split(";")) {
            if (rawCompartment.isBlank()) continue;
            String[] f = rawCompartment.split(":", -1);
            if (f.length < 7) continue;
            Compartment c = new Compartment(dec(f[0], "compartment"), dec(f[1], "Compartment"));
            c.integrity = MathUtil.clamp(parseDouble(f[2], 1.0), 0.0, 1.0);
            c.fire = MathUtil.clamp(parseDouble(f[3], 0.0), 0.0, 2.0);
            c.disrupted = Boolean.parseBoolean(f[4]);
            c.decompressed = Boolean.parseBoolean(f[5]);
            c.powerPriority = MathUtil.clamp(parseInt(f[6], 2), 0, 4);
            if (f.length >= 20) {
                c.systemType = parseEnum(f[7], SystemType.GENERAL);
                String connections = dec(f[8], "");
                if (!connections.isBlank()) c.connections.addAll(List.of(connections.split(",")));
                c.powerDemand = Math.max(0, parseInt(f[9], defaultPowerDemand(c.systemType)));
                c.powerAllocated = Math.max(0, parseInt(f[10], c.powerDemand));
                c.coolantLeak = MathUtil.clamp(parseDouble(f[11], 0.0), 0.0, 2.0);
                c.electricalFault = MathUtil.clamp(parseDouble(f[12], 0.0), 0.0, 2.0);
                c.structuralCollapse = MathUtil.clamp(parseDouble(f[13], 0.0), 0.0, 2.0);
                c.personnelCapacity = Math.max(0, parseInt(f[14], 10));
                c.injuries = Math.max(0, parseInt(f[15], 0));
                c.sealed = Boolean.parseBoolean(f[16]);
                c.evacuated = Boolean.parseBoolean(f[17]);
                c.vented = Boolean.parseBoolean(f[18]);
                c.offlineReason = dec(f[19], "Operational");
            }
            state.compartments.put(c.id, c);
        }
        state.teams.clear();
        for (String rawTeam : parts[5].split(";")) {
            if (rawTeam.isBlank()) continue;
            String[] f = rawTeam.split(":", -1);
            if (f.length < 4) continue;
            DamageControlTeam team = new DamageControlTeam(dec(f[0], "team"));
            team.assignedCompartmentId = dec(f[1], "");
            team.order = parseEnum(f[2], TeamOrder.STANDBY);
            team.readiness = MathUtil.clamp(parseInt(f[3], 100), 0, 100);
            if (f.length >= 6) {
                team.casualties = Math.max(0, parseInt(f[4], 0));
                team.available = Boolean.parseBoolean(f[5]);
            }
            state.teams.put(team.id, team);
        }
        if (parts.length >= 7) {
            String[] f = parts[6].split(":", -1);
            if (f.length >= 12) {
                state.powerGeneration = Math.max(0, parseInt(f[0], 100));
                state.emergencyPowerCooldownTicks = Math.max(0, parseInt(f[1], 0));
                state.repairParts = Math.max(0, parseInt(f[2], 40));
                state.emergencyReserves = Math.max(0, parseInt(f[3], 20));
                state.marineReadiness = MathUtil.clamp(parseInt(f[4], 100), 0, 100);
                state.storedCraft = Math.max(0, parseInt(f[5], 8));
                state.craftFuel = MathUtil.clamp(parseInt(f[6], 100), 0, 100);
                state.craftAmmunition = MathUtil.clamp(parseInt(f[7], 100), 0, 100);
                state.launchCapacity = Math.max(0, parseInt(f[8], 4));
                state.recoveryCapacity = Math.max(0, parseInt(f[9], 4));
                state.deckDamage = MathUtil.clamp(parseInt(f[10], 0), 0, 100);
                state.timePressurePercent = MathUtil.clamp(parseInt(f[11], 100), 25, 200);
                if (f.length >= 18) {
                    state.selectedCompartmentId = dec(f[12], "");
                    state.schematicZoom = MathUtil.clamp(parseDouble(f[13], 1.0), 0.65, 2.5);
                    state.schematicPanX = MathUtil.clamp(parseDouble(f[14], 0.0), -1.0, 1.0);
                    state.schematicPanY = MathUtil.clamp(parseDouble(f[15], 0.0), -1.0, 1.0);
                    state.schematicVisible = Boolean.parseBoolean(f[16]);
                    state.slowTimeRequested = Boolean.parseBoolean(f[17]);
                }
            }
        }
        return state;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    private static String enc(Base64.Encoder e, String value) { return e.encodeToString(normalize(value, "").getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value, String fallback) { try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); } catch (Exception ignored) { return fallback; } }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static double parseDouble(String value, double fallback) { try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; } }
    private static <T extends Enum<T>> T parseEnum(String value, T fallback) { try { return Enum.valueOf(fallback.getDeclaringClass(), value); } catch (Exception ignored) { return fallback; } }
}
