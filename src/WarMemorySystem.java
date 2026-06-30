import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Structured, factual campaign memory. It never fabricates missing facts. */
public final class WarMemorySystem {
    public static final int MAX_CHRONICLE = 1024;

    public static final class LocationRecord {
        public final String id;
        public String currentName;
        public String owner = "NEUTRAL";
        public int damage;
        public int reconstruction;
        public boolean evacuated;
        public boolean abandoned;
        public final List<String> disabledServices = new ArrayList<>();
        public final List<String> ownershipHistory = new ArrayList<>();
        public final List<String> unresolvedNeeds = new ArrayList<>();
        LocationRecord(String id, String name) { this.id = norm(id, "location"); this.currentName = norm(name, this.id); }
    }

    public static final class ShipRecord {
        public final String id;
        public String name;
        public String role;
        public String faction;
        public String commander = "";
        public int victories, defeats, rescues, retreats, captures;
        public boolean destroyed, civilian;
        public final List<String> scars = new ArrayList<>();
        public final List<String> honors = new ArrayList<>();
        public final List<String> serviceHistory = new ArrayList<>();
        ShipRecord(String id, String name, String role, String faction) {
            this.id = norm(id, "ship"); this.name = norm(name, this.id);
            this.role = norm(role, "unknown"); this.faction = norm(faction, "NEUTRAL");
        }
    }

    public record SurvivorRecord(String id, String sourceId, String description, int recoveredTick,
                                 int moraleBonus, String followUpMission) {}
    public record ChronicleEntry(String eventId, int tick, String type, String faction, String territoryId,
                                 String fleetId, String commanderId, String fact, String consequence,
                                 String sourceEventId, boolean turningPoint) {}
    public record RepairResult(boolean completed, int timeSpent, int resourcesSpent, int remainingDamage) {}
    public record FollowUpMission(String id, String locationId, String title, String objective,
                                  int urgency, String sourceFact) {}

    public static final class State {
        public final Map<String, LocationRecord> locations = new LinkedHashMap<>();
        public final Map<String, ShipRecord> ships = new LinkedHashMap<>();
        public final List<SurvivorRecord> survivors = new ArrayList<>();
        public final List<ChronicleEntry> chronicle = new ArrayList<>();
    }

    private WarMemorySystem() {}
    public static State bootstrap() { return new State(); }

    public static LocationRecord recordLocation(State state, String id, String name, String owner, int damage,
                                                List<String> disabledServices, boolean evacuated, boolean abandoned,
                                                String unresolvedNeed, int tick) {
        if (state == null) return null;
        LocationRecord record = state.locations.computeIfAbsent(norm(id, "location"), key -> new LocationRecord(key, name));
        if (!record.owner.equals(norm(owner, "NEUTRAL"))) record.ownershipHistory.add("T" + tick + ":" + record.owner + "->" + owner);
        record.owner = norm(owner, "NEUTRAL");
        record.damage = MathUtil.clamp(damage, 0, 100);
        record.evacuated = evacuated;
        record.abandoned = abandoned;
        record.disabledServices.clear(); if (disabledServices != null) record.disabledServices.addAll(disabledServices);
        if (unresolvedNeed != null && !unresolvedNeed.isBlank()) record.unresolvedNeeds.add(unresolvedNeed.trim());
        return record;
    }

    public static boolean renameLocation(State state, String id, String newName, String reason, int tick) {
        LocationRecord record = state == null ? null : state.locations.get(id);
        if (record == null || newName == null || newName.isBlank()) return false;
        addChronicle(state, new ChronicleEntry("rename-" + id + "-" + tick, tick, "location", record.owner, id,
                "", "", "Renamed " + record.currentName + " to " + newName.trim(), norm(reason, "recorded event"),
                "location:" + id, false));
        record.currentName = newName.trim();
        return true;
    }

    public static RepairResult repairLocation(State state, String id, int availableTime, int availableResources) {
        LocationRecord record = state == null ? null : state.locations.get(id);
        if (record == null || availableTime <= 0 || availableResources <= 0) return new RepairResult(false, 0, 0, record == null ? 0 : record.damage);
        int repaired = Math.min(record.damage, Math.min(availableTime * 2, availableResources));
        record.damage -= repaired;
        record.reconstruction = MathUtil.clamp(record.reconstruction + repaired, 0, 100);
        if (record.damage == 0) record.disabledServices.clear();
        return new RepairResult(record.damage == 0, (int) Math.ceil(repaired / 2.0), repaired, record.damage);
    }

    public static ShipRecord recordShip(State state, String id, String name, String role, String faction,
                                        String commander, String serviceFact) {
        if (state == null) return null;
        ShipRecord record = state.ships.computeIfAbsent(norm(id, "ship"), key -> new ShipRecord(key, name, role, faction));
        record.name = norm(name, record.name); record.role = norm(role, record.role); record.faction = norm(faction, record.faction);
        record.commander = norm(commander, record.commander);
        if (serviceFact != null && !serviceFact.isBlank()) record.serviceHistory.add(serviceFact.trim());
        return record;
    }

    public static void destroyShip(State state, String id, int tick, String cause, boolean civilian) {
        ShipRecord record = state == null ? null : state.ships.get(id);
        if (record == null) return;
        record.destroyed = true; record.civilian = civilian;
        String fact = record.name + " was lost" + (civilian ? " with civilian status" : "");
        record.serviceHistory.add("T" + tick + ": " + fact + " — " + norm(cause, "cause unknown"));
        addChronicle(state, new ChronicleEntry("ship-loss-" + id + "-" + tick, tick, "ship-loss", record.faction,
                "", id, record.commander, fact, norm(cause, "cause unknown"), "ship:" + id, true));
    }

    public static boolean inheritHonors(State state, String predecessorId, String successorId, String successorName) {
        ShipRecord predecessor = state == null ? null : state.ships.get(predecessorId);
        if (predecessor == null || !predecessor.destroyed) return false;
        ShipRecord successor = recordShip(state, successorId, successorName, predecessor.role, predecessor.faction, "",
                "Inherited traditions from " + predecessor.name);
        successor.honors.addAll(predecessor.honors);
        successor.honors.add("Successor to " + predecessor.name);
        return true;
    }

    public static SurvivorRecord recordSurvivors(State state, String id, String sourceId, String description,
                                                  int tick, int moraleBonus, String followUpMission) {
        if (state == null) return null;
        SurvivorRecord record = new SurvivorRecord(norm(id, "survivors"), norm(sourceId, "unknown"),
                norm(description, "Recovered survivors"), Math.max(0, tick), Math.max(0, moraleBonus),
                norm(followUpMission, "No follow-up assigned"));
        state.survivors.add(record); while (state.survivors.size() > 512) state.survivors.remove(0);
        return record;
    }

    public static void ingestStrategicFacts(State memory, StrategicCampaignExpansionSystem.State strategic) {
        if (memory == null || strategic == null) return;
        for (StrategicCampaignExpansionSystem.WarEvent event : strategic.warEvents) {
            addChronicle(memory, new ChronicleEntry(event.id, event.tick, event.category, "UNKNOWN", "", "", "",
                    event.title + ": " + event.detail, event.consequence, event.id, event.major));
        }
    }

    public static void addChronicle(State state, ChronicleEntry entry) {
        if (state == null || entry == null) return;
        for (int i = 0; i < state.chronicle.size(); i++) if (state.chronicle.get(i).eventId().equals(entry.eventId())) { state.chronicle.set(i, entry); return; }
        state.chronicle.add(entry);
        state.chronicle.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
        while (state.chronicle.size() > MAX_CHRONICLE) state.chronicle.remove(0);
    }

    public static List<ChronicleEntry> filter(State state, Integer fromTick, Integer toTick, String faction,
                                              String territory, String fleet, String commander, String type) {
        if (state == null) return List.of();
        return state.chronicle.stream().filter(e -> fromTick == null || e.tick() >= fromTick)
                .filter(e -> toTick == null || e.tick() <= toTick).filter(e -> blank(faction) || contains(e.faction(), faction))
                .filter(e -> blank(territory) || contains(e.territoryId(), territory)).filter(e -> blank(fleet) || contains(e.fleetId(), fleet))
                .filter(e -> blank(commander) || contains(e.commanderId(), commander)).filter(e -> blank(type) || contains(e.type(), type)).toList();
    }

    public static List<String> turningPointSummary(State state) {
        if (state == null) return List.of();
        return state.chronicle.stream().filter(ChronicleEntry::turningPoint)
                .map(e -> "T" + e.tick() + " | " + e.fact() + " | consequence " + e.consequence()
                        + " | source " + e.sourceEventId()).toList();
    }

    public static List<FollowUpMission> followUpMissions(State state) {
        if (state == null) return List.of();
        ArrayList<FollowUpMission> out = new ArrayList<>();
        for (LocationRecord location : state.locations.values()) {
            if (location == null) continue;
            int serial = 0;
            for (String need : location.unresolvedNeeds) {
                out.add(new FollowUpMission("followup-" + location.id + "-" + serial++, location.id,
                        "Return to " + location.currentName,
                        "Resolve population need: " + need,
                        MathUtil.clamp(40 + location.damage / 2 + (location.evacuated ? 20 : 0), 0, 100), need));
            }
            if (location.damage > 0) {
                out.add(new FollowUpMission("repair-" + location.id, location.id,
                        "Rebuild " + location.currentName,
                        "Deliver repair capacity and restore disabled services",
                        MathUtil.clamp(location.damage, 0, 100), "recorded damage " + location.damage));
            }
            if (location.evacuated || location.abandoned) {
                out.add(new FollowUpMission("population-" + location.id, location.id,
                        "Population Return Assessment",
                        "Revisit the known site and determine whether civilians can safely return",
                        location.abandoned ? 85 : 65, location.abandoned ? "abandoned population center" : "evacuation record"));
            }
        }
        out.sort((a, b) -> Integer.compare(b.urgency(), a.urgency()));
        return List.copyOf(out);
    }

    public static List<String> factionNewsLines(State state, String faction) {
        if (state == null) return List.of();
        String filter = faction == null ? "" : faction.trim();
        return state.chronicle.stream()
                .filter(entry -> filter.isBlank() || contains(entry.faction(), filter))
                .map(entry -> "T" + entry.tick() + " | " + norm(entry.faction(), "Unaffiliated")
                        + " bulletin | " + entry.fact() + " | verified source " + entry.sourceEventId())
                .toList();
    }

    public static String exportAfterActionReport(State state) {
        StringBuilder out = new StringBuilder("AFTER-ACTION CAMPAIGN REPORT\n");
        for (ChronicleEntry entry : state == null ? List.<ChronicleEntry>of() : state.chronicle) {
            out.append("T").append(entry.tick()).append(" [").append(entry.type()).append("] ")
                    .append(entry.fact()).append(" -> ").append(entry.consequence())
                    .append(" {source=").append(entry.sourceEventId()).append("}\n");
        }
        return out.toString();
    }

    public static List<String> historicalScenarioSeeds(State state) {
        if (state == null) return List.of();
        return state.chronicle.stream().filter(e -> e.turningPoint() && e.type().toLowerCase(Locale.ROOT).contains("battle"))
                .map(e -> "scenario:" + e.eventId() + ":tick=" + e.tick() + ":source=" + e.sourceEventId()).toList();
    }

    public static List<ShipRecord> memorialSearch(State state, String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (state == null) return List.of();
        return state.ships.values().stream().filter(ship -> ship.destroyed)
                .filter(ship -> needle.isBlank() || (ship.name + " " + ship.faction + " " + ship.commander).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        StringBuilder locations = new StringBuilder(), ships = new StringBuilder(), survivors = new StringBuilder(), chronicle = new StringBuilder();
        for (LocationRecord r : state.locations.values()) { if (!locations.isEmpty()) locations.append(';'); locations.append(b(e,r.id)).append(':').append(b(e,r.currentName)).append(':').append(b(e,r.owner)).append(':').append(r.damage).append(':').append(r.reconstruction).append(':').append(r.evacuated).append(':').append(r.abandoned).append(':').append(b(e,String.join("\u001f",r.disabledServices))).append(':').append(b(e,String.join("\u001f",r.ownershipHistory))).append(':').append(b(e,String.join("\u001f",r.unresolvedNeeds))); }
        for (ShipRecord r : state.ships.values()) { if (!ships.isEmpty()) ships.append(';'); ships.append(b(e,r.id)).append(':').append(b(e,r.name)).append(':').append(b(e,r.role)).append(':').append(b(e,r.faction)).append(':').append(b(e,r.commander)).append(':').append(r.victories).append(':').append(r.defeats).append(':').append(r.rescues).append(':').append(r.retreats).append(':').append(r.captures).append(':').append(r.destroyed).append(':').append(r.civilian).append(':').append(b(e,String.join("\u001f",r.scars))).append(':').append(b(e,String.join("\u001f",r.honors))).append(':').append(b(e,String.join("\u001f",r.serviceHistory))); }
        for (SurvivorRecord r : state.survivors) { if (!survivors.isEmpty()) survivors.append(';'); survivors.append(b(e,r.id())).append(':').append(b(e,r.sourceId())).append(':').append(b(e,r.description())).append(':').append(r.recoveredTick()).append(':').append(r.moraleBonus()).append(':').append(b(e,r.followUpMission())); }
        for (ChronicleEntry r : state.chronicle) { if (!chronicle.isEmpty()) chronicle.append(';'); chronicle.append(b(e,r.eventId())).append(':').append(r.tick()).append(':').append(b(e,r.type())).append(':').append(b(e,r.faction())).append(':').append(b(e,r.territoryId())).append(':').append(b(e,r.fleetId())).append(':').append(b(e,r.commanderId())).append(':').append(b(e,r.fact())).append(':').append(b(e,r.consequence())).append(':').append(b(e,r.sourceEventId())).append(':').append(r.turningPoint()); }
        return locations + "|" + ships + "|" + survivors + "|" + chronicle;
    }

    public static State restore(String raw) {
        State s = bootstrap(); if (raw == null || raw.isBlank()) return s; String[] p = raw.split("\\|",-1); if (p.length < 4) return s;
        for(String x:p[0].split(";")){String[]f=x.split(":",-1);if(f.length<10)continue;LocationRecord r=new LocationRecord(d(f[0],"location"),d(f[1],"Location"));r.owner=d(f[2],"NEUTRAL");r.damage=i(f[3]);r.reconstruction=i(f[4]);r.evacuated=Boolean.parseBoolean(f[5]);r.abandoned=Boolean.parseBoolean(f[6]);add(r.disabledServices,d(f[7],""));add(r.ownershipHistory,d(f[8],""));add(r.unresolvedNeeds,d(f[9],""));s.locations.put(r.id,r);}
        for(String x:p[1].split(";")){String[]f=x.split(":",-1);if(f.length<15)continue;ShipRecord r=new ShipRecord(d(f[0],"ship"),d(f[1],"Ship"),d(f[2],"unknown"),d(f[3],"NEUTRAL"));r.commander=d(f[4],"");r.victories=i(f[5]);r.defeats=i(f[6]);r.rescues=i(f[7]);r.retreats=i(f[8]);r.captures=i(f[9]);r.destroyed=Boolean.parseBoolean(f[10]);r.civilian=Boolean.parseBoolean(f[11]);add(r.scars,d(f[12],""));add(r.honors,d(f[13],""));add(r.serviceHistory,d(f[14],""));s.ships.put(r.id,r);}
        for(String x:p[2].split(";")){String[]f=x.split(":",-1);if(f.length<6)continue;s.survivors.add(new SurvivorRecord(d(f[0],"survivors"),d(f[1],"unknown"),d(f[2],"Recovered survivors"),i(f[3]),i(f[4]),d(f[5],"")));}
        for(String x:p[3].split(";")){String[]f=x.split(":",-1);if(f.length<11)continue;s.chronicle.add(new ChronicleEntry(d(f[0],"event"),i(f[1]),d(f[2],"event"),d(f[3],"UNKNOWN"),d(f[4],""),d(f[5],""),d(f[6],""),d(f[7],""),d(f[8],""),d(f[9],f[0]),Boolean.parseBoolean(f[10])));}
        return s;
    }

    private static String norm(String v,String f){return v==null||v.isBlank()?f:v.trim();} private static boolean blank(String v){return v==null||v.isBlank();}
    private static boolean contains(String v,String q){return v!=null&&v.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));}
    private static String b(Base64.Encoder e,String v){return e.encodeToString((v==null?"":v).getBytes(StandardCharsets.UTF_8));}
    private static String d(String v,String f){try{return new String(Base64.getUrlDecoder().decode(v),StandardCharsets.UTF_8);}catch(Exception ignored){return f;}}
    private static int i(String v){try{return Integer.parseInt(v);}catch(Exception ignored){return 0;}}
    private static void add(List<String> out,String value){if(value==null||value.isBlank())return;for(String x:value.split("\u001f",-1))if(!x.isBlank())out.add(x);}
}
