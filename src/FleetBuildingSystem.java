import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Section 4 fleet-building domain model. The campaign can adopt these records incrementally
 * without changing the existing persistent-fleet save format in one large migration.
 */
public final class FleetBuildingSystem {
    public enum HullFamily {
        COMBAT, COMMAND, STEALTH, ARTILLERY, ELECTRONIC_WARFARE, TRADE, MINING, REPAIR,
        SALVAGE, EVACUATION, HOSPITAL, LOGISTICS, BOARDING, MINE_WARFARE, RECOVERY, PROTOTYPE, TITAN
    }

    public enum ModuleSlot {
        WEAPON, DEFENSE, ENGINE, SENSOR, SUPPORT, CARGO, COMMAND
    }

    public enum ModuleRarity {
        COMMON, INDUSTRIAL, MILITARY, RARE, PROTOTYPE, CAPTURED
    }

    public enum CrewSpecialization {
        LINE_GUNNERY, DAMAGE_CONTROL, FLIGHT_OPERATIONS, ENGINEERING, LOGISTICS,
        RECON, ELECTRONIC_WARFARE, RESCUE, BOARDING
    }

    public enum CaptainPersonality {
        STEADY("holds formation and protects damaged allies"),
        AGGRESSIVE("presses damaged targets and accepts heat"),
        CAUTIOUS("preserves the hull and disengages early"),
        RESOURCEFUL("improvises repairs and salvage recoveries"),
        DISCIPLINED("follows doctrine closely under pressure");

        public final String doctrinePreference;

        CaptainPersonality(String doctrinePreference) {
            this.doctrinePreference = doctrinePreference;
        }
    }

    public static final class HullBudgets {
        public final int weight;
        public final int power;
        public final int heat;
        public final int crew;
        public final int maintenance;

        public HullBudgets(int weight, int power, int heat, int crew, int maintenance) {
            this.weight = Math.max(0, weight);
            this.power = Math.max(0, power);
            this.heat = Math.max(0, heat);
            this.crew = Math.max(0, crew);
            this.maintenance = Math.max(0, maintenance);
        }
    }

    public static final class HullProfile {
        public final ShipRole role;
        public final String battlefieldRole;
        public final String counter;
        public final String weakness;
        public final HullFamily family;
        public final HullBudgets budgets;
        public final String factionVariant;
        public final String silhouetteCheck;

        private HullProfile(ShipRole role, String battlefieldRole, String counter, String weakness,
                            HullFamily family, HullBudgets budgets, String factionVariant, String silhouetteCheck) {
            this.role = role;
            this.battlefieldRole = battlefieldRole;
            this.counter = counter;
            this.weakness = weakness;
            this.family = family;
            this.budgets = budgets;
            this.factionVariant = factionVariant;
            this.silhouetteCheck = silhouetteCheck;
        }
    }

    public static final class RefitModule {
        public final String id;
        public final ModuleSlot slot;
        public final ModuleRarity rarity;
        public final HullBudgets costs;
        public final String industrialSource;
        public final String effect;
        public final boolean capturedTech;

        public RefitModule(String id, ModuleSlot slot, ModuleRarity rarity, HullBudgets costs,
                           String industrialSource, String effect, boolean capturedTech) {
            this.id = clean(id, "module");
            this.slot = (slot == null) ? ModuleSlot.SUPPORT : slot;
            this.rarity = (rarity == null) ? ModuleRarity.COMMON : rarity;
            this.costs = (costs == null) ? new HullBudgets(0, 0, 0, 0, 0) : costs;
            this.industrialSource = clean(industrialSource, "open market");
            this.effect = clean(effect, "utility refit");
            this.capturedTech = capturedTech;
        }
    }

    public static final class RefitTemplate {
        public final String name;
        public final List<RefitModule> modules;
        public final String doctrine;

        public RefitTemplate(String name, List<RefitModule> modules, String doctrine) {
            this.name = clean(name, "Unnamed Loadout");
            this.modules = List.copyOf((modules == null) ? List.of() : modules);
            this.doctrine = clean(doctrine, "balanced fleet");
        }
    }

    public static final class RefitAssessment {
        public final boolean valid;
        public final List<String> warnings;
        public final int refitDays;
        public final double fieldReliability;

        private RefitAssessment(boolean valid, List<String> warnings, int refitDays, double fieldReliability) {
            this.valid = valid;
            this.warnings = List.copyOf(warnings);
            this.refitDays = Math.max(0, refitDays);
            this.fieldReliability = MathUtil.clamp(fieldReliability, 0.0, 1.0);
        }
    }

    public static final class ConstructionOrder {
        public final ShipRole role;
        public final String shipyardRegion;
        public int remainingDays;
        public final boolean specializedYard;

        public ConstructionOrder(ShipRole role, String shipyardRegion, int remainingDays, boolean specializedYard) {
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.shipyardRegion = clean(shipyardRegion, "general shipyard");
            this.remainingDays = Math.max(1, remainingDays);
            this.specializedYard = specializedYard;
        }

        public void advanceDay() {
            remainingDays = Math.max(0, remainingDays - 1);
        }
    }

    public static final class HullProgram {
        public final String name;
        public final HullFamily family;
        public final String purpose;
        public final int maintenanceBurden;

        private HullProgram(String name, HullFamily family, String purpose, int maintenanceBurden) {
            this.name = clean(name, "Unnamed Program");
            this.family = (family == null) ? HullFamily.COMBAT : family;
            this.purpose = clean(purpose, "fleet support");
            this.maintenanceBurden = Math.max(0, maintenanceBurden);
        }
    }

    public static final class Captain {
        public final String name;
        public final CaptainPersonality personality;
        public int experience;
        public int morale = 70;
        public int refusalRisk;

        public Captain(String name, CaptainPersonality personality) {
            this.name = clean(name, "Unnamed Captain");
            this.personality = (personality == null) ? CaptainPersonality.STEADY : personality;
        }
    }

    public static final class ServiceRecord {
        public final String event;
        public final String note;

        public ServiceRecord(String event, String note) {
            this.event = clean(event, "SERVICE");
            this.note = clean(note, "recorded");
        }
    }

    public static final class PersistentShip {
        public final String registryId;
        public final ShipRole role;
        public String name;
        public Captain captain;
        public CrewSpecialization crewSpecialization = CrewSpecialization.DAMAGE_CONTROL;
        public int crewExperience;
        public int morale = 70;
        public int kills;
        public int rescues;
        public int retreats;
        public int scars;
        public boolean destroyed;
        public final List<String> commendations = new ArrayList<>();
        public final List<ServiceRecord> history = new ArrayList<>();

        public PersistentShip(String registryId, ShipRole role, String name, Captain captain) {
            this.registryId = clean(registryId, "UNREGISTERED");
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.name = clean(name, "Unnamed Ship");
            this.captain = captain;
        }

        public void recordBattle(int newKills, boolean retreated, boolean majorEngagement) {
            kills += Math.max(0, newKills);
            if (retreated) retreats++;
            if (majorEngagement) scars++;
            crewExperience += Math.max(1, newKills + (majorEngagement ? 3 : 0));
            morale = clampMorale(morale + (retreated ? -4 : 3) + Math.min(6, newKills));
            history.add(new ServiceRecord("BATTLE", (majorEngagement ? "major engagement; " : "") + newKills + " confirmed kills"));
            earnNickname();
        }

        public void recordRescue(int survivors) {
            rescues += Math.max(0, survivors);
            morale = clampMorale(morale + 6);
            history.add(new ServiceRecord("RESCUE", Math.max(0, survivors) + " survivors recovered"));
            if (rescues >= 20 && !commendations.contains("Rescue Pennant")) commendations.add("Rescue Pennant");
            earnNickname();
        }

        public void sufferShortage(int severity) {
            morale = clampMorale(morale - Math.max(0, severity));
            if (captain != null) {
                captain.morale = clampMorale(captain.morale - Math.max(0, severity));
                captain.refusalRisk = Math.max(0, 45 - captain.morale);
            }
            history.add(new ServiceRecord("SHORTAGE", "crew morale reduced by " + Math.max(0, severity)));
        }

        public void destroy(String cause) {
            destroyed = true;
            morale = 0;
            history.add(new ServiceRecord("LOST", clean(cause, "destroyed in action")));
        }

        private void earnNickname() {
            if (name.contains("\"")) return;
            if (rescues >= 20) name += " \"Lifeline\"";
            else if (kills >= 12) name += " \"Linebreaker\"";
            else if (retreats >= 3) name += " \"Homebound\"";
        }
    }

    public static final class FleetArchive {
        private final Map<String, PersistentShip> active = new LinkedHashMap<>();
        private final List<PersistentShip> memorials = new ArrayList<>();

        public void commission(PersistentShip ship) {
            if (ship != null) active.put(ship.registryId, ship);
        }

        public PersistentShip lose(String registryId, String cause) {
            PersistentShip lost = active.remove(registryId);
            if (lost == null) return null;
            lost.destroy(cause);
            memorials.add(lost);
            return lost;
        }

        public PersistentShip commissionSuccessor(String lostRegistryId, String successorRegistryId, ShipRole role) {
            PersistentShip predecessor = null;
            for (PersistentShip memorial : memorials) {
                if (memorial.registryId.equals(lostRegistryId)) predecessor = memorial;
            }
            if (predecessor == null) return null;
            PersistentShip successor = new PersistentShip(successorRegistryId, role,
                    baseName(predecessor.name) + " II", predecessor.captain);
            successor.crewExperience = predecessor.crewExperience / 4;
            successor.history.add(new ServiceRecord("TRADITION", "inherits the name and traditions of " + predecessor.registryId));
            commission(successor);
            return successor;
        }

        public List<PersistentShip> activeShips() {
            return List.copyOf(active.values());
        }

        public List<PersistentShip> memorials() {
            return List.copyOf(memorials);
        }

        public List<String> screenLines() {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("FLEET ARCHIVE  |  ACTIVE " + active.size() + "  LOST " + memorials.size());
            for (PersistentShip ship : active.values()) lines.add(archiveLine(ship));
            for (PersistentShip ship : memorials) lines.add("MEMORIAL  |  " + archiveLine(ship));
            return lines;
        }
    }

    private static final Map<ShipRole, HullProfile> HULLS = buildHullProfiles();
    private static final List<HullProgram> SPECIALIST_PROGRAMS = buildSpecialistPrograms();
    private static final Map<String, RefitTemplate> SAVED_LOADOUTS = new LinkedHashMap<>();
    private static final List<ConstructionOrder> CONSTRUCTION_QUEUE = new ArrayList<>();
    private static final List<RefitModule> MODULE_CATALOG = buildModuleCatalog();
    private static final List<RefitTemplate> STANDARD_LOADOUTS = buildStandardLoadouts();

    private FleetBuildingSystem() {}

    public static HullProfile hullProfile(ShipRole role) {
        ShipRole resolved = (role == null) ? ShipRole.FRIGATE : role;
        return HULLS.get(resolved);
    }

    public static Map<ShipRole, HullProfile> hullRoster() {
        return Collections.unmodifiableMap(HULLS);
    }

    public static String battleCard(ShipRole role) {
        HullProfile profile = hullProfile(role);
        return profile.role + "  |  " + profile.battlefieldRole + "  |  COUNTER " + profile.counter
                + "  |  WEAKNESS " + profile.weakness;
    }

    public static void saveLoadout(RefitTemplate template) {
        if (template != null) SAVED_LOADOUTS.put(template.name, template);
    }

    public static RefitTemplate savedLoadout(String name) {
        return SAVED_LOADOUTS.get(name);
    }

    public static List<RefitModule> moduleCatalog() {
        return MODULE_CATALOG;
    }

    public static List<RefitTemplate> standardLoadouts() {
        return STANDARD_LOADOUTS;
    }

    public static RefitAssessment assessRefit(ShipRole role, RefitTemplate template, boolean fieldRefit,
                                              String factionTech, String shipyardRegion) {
        HullProfile hull = hullProfile(role);
        List<String> warnings = new ArrayList<>();
        HullBudgets used = sumCosts((template == null) ? List.of() : template.modules);
        checkBudget("weight", used.weight, hull.budgets.weight, warnings);
        checkBudget("power", used.power, hull.budgets.power, warnings);
        checkBudget("heat", used.heat, hull.budgets.heat, warnings);
        checkBudget("crew", used.crew, hull.budgets.crew, warnings);
        checkBudget("maintenance", used.maintenance, hull.budgets.maintenance, warnings);
        int days = Math.max(1, ((template == null) ? 0 : template.modules.size()) * 2);
        double reliability = 1.0;
        for (RefitModule module : (template == null) ? List.<RefitModule>of() : template.modules) {
            if (module.capturedTech) {
                warnings.add("captured module " + module.id + " requires integration testing");
                reliability -= 0.08;
            }
            if (factionTech != null && !factionTech.isBlank()
                    && !module.industrialSource.equalsIgnoreCase(factionTech)
                    && !module.industrialSource.equalsIgnoreCase("open market")) {
                warnings.add(module.id + " has faction-tech compatibility penalties");
                reliability -= 0.05;
            }
            if (shipyardRegion != null && !shipyardRegion.isBlank()
                    && !module.industrialSource.equalsIgnoreCase("open market")
                    && !module.industrialSource.equalsIgnoreCase(shipyardRegion)) {
                warnings.add(shipyardRegion + " lacks ideal specialization for " + module.id);
                days += 1;
            }
        }
        if (fieldRefit) {
            warnings.add("emergency field refit reduces reliability");
            days = Math.max(1, days / 2);
            reliability -= 0.18;
        }
        boolean valid = warnings.stream().noneMatch(line -> line.startsWith("budget exceeded"));
        return new RefitAssessment(valid, warnings, days, reliability);
    }

    public static void queueConstruction(ConstructionOrder order) {
        if (order != null) CONSTRUCTION_QUEUE.add(order);
    }

    public static List<ConstructionOrder> constructionQueue() {
        return List.copyOf(CONSTRUCTION_QUEUE);
    }

    public static String doctrineSuggestion(String doctrine) {
        String key = clean(doctrine, "balanced").toLowerCase(Locale.US);
        if (key.contains("artillery")) return "Artillery hull + command/intel spotter + two CIWS screens";
        if (key.contains("rescue")) return "Boarding/recovery hull + hospital transport + tug screen";
        if (key.contains("stealth")) return "Stealth hull + EW picket + fast logistics hauler";
        if (key.contains("mine")) return "Mine-warfare picket + CIWS corvette + recovery tug";
        return "Line cruiser + frigate screen + CIWS corvette + logistics transport";
    }

    public static String disciplineRiskLabel(int morale, int refusalRisk) {
        int resolvedMorale = Math.max(0, Math.min(100, morale));
        int resolvedRefusal = Math.max(0, Math.min(100, refusalRisk));
        if (resolvedMorale <= 10) return "MUTINY RISK";
        if (resolvedMorale <= 25) return "DESERTION RISK";
        if (resolvedRefusal > 0 || resolvedMorale <= 45) return "REFUSAL RISK";
        return "DISCIPLINE STEADY";
    }

    public static List<HullProgram> specialistPrograms() {
        return SPECIALIST_PROGRAMS;
    }

    public static String damagedModuleDecision(RefitModule module, int damagePercent, boolean replacementAvailable) {
        if (module == null) return "No damaged module selected.";
        if (!replacementAvailable) return "Repair " + module.id + ": no compatible replacement is locally available.";
        if (module.rarity == ModuleRarity.RARE || module.rarity == ModuleRarity.PROTOTYPE || module.capturedTech) {
            return "Repair " + module.id + ": preserve scarce technology unless reliability is mission-critical.";
        }
        if (damagePercent >= 55) return "Replace " + module.id + ": shop replacement is faster and more reliable.";
        return "Repair " + module.id + ": damage is within economical yard limits.";
    }

    public static boolean shipyardCanConstruct(String region, HullFamily family) {
        String key = clean(region, "general").toLowerCase(Locale.US);
        if (family == null || family == HullFamily.COMBAT) return true;
        if (key.contains("general")) return family != HullFamily.TITAN && family != HullFamily.PROTOTYPE;
        if (key.contains("blue")) return family == HullFamily.COMMAND || family == HullFamily.REPAIR
                || family == HullFamily.RECOVERY || family == HullFamily.HOSPITAL || family == HullFamily.TITAN;
        if (key.contains("red")) return family == HullFamily.ARTILLERY || family == HullFamily.BOARDING
                || family == HullFamily.PROTOTYPE || family == HullFamily.TITAN;
        if (key.contains("green")) return family == HullFamily.TRADE || family == HullFamily.MINING
                || family == HullFamily.SALVAGE || family == HullFamily.ELECTRONIC_WARFARE;
        if (key.contains("yellow")) return family == HullFamily.LOGISTICS || family == HullFamily.EVACUATION
                || family == HullFamily.MINE_WARFARE || family == HullFamily.RECOVERY;
        return false;
    }

    private static Map<ShipRole, HullProfile> buildHullProfiles() {
        EnumMap<ShipRole, HullProfile> out = new EnumMap<>(ShipRole.class);
        for (ShipRole role : ShipRole.values()) {
            HullFamily family = familyFor(role);
            RoleStats.Stats stats = RoleStats.get(role);
            int scale = Math.max(4, (int) Math.round(stats.radius / 3.0));
            String[] identity = identityFor(role, family);
            out.put(role, new HullProfile(role, identity[0], identity[1], identity[2], family,
                    new HullBudgets(scale * 5, scale * 4, scale * 3, scale * 3, scale * 2),
                    factionVariant(role), silhouetteCheck(role)));
        }
        return out;
    }

    private static List<HullProgram> buildSpecialistPrograms() {
        return List.of(
                new HullProgram("Free Trader", HullFamily.TRADE, "civilian trade and convoy hauling", 1),
                new HullProgram("Prospector Rig", HullFamily.MINING, "civilian mining and survey work", 2),
                new HullProgram("Workshop Tender", HullFamily.REPAIR, "field repair and module restoration", 3),
                new HullProgram("Wreck Stripper", HullFamily.SALVAGE, "salvage recovery and tow preparation", 2),
                new HullProgram("Evacuation Liner", HullFamily.EVACUATION, "civilian evacuation lift", 2),
                new HullProgram("Fleet Flagship", HullFamily.COMMAND, "specialist command and relay coverage", 4),
                new HullProgram("Ghost Cutter", HullFamily.STEALTH, "signature-managed reconnaissance", 4),
                new HullProgram("Siege Monitor", HullFamily.ARTILLERY, "screen-dependent long-range fire", 4),
                new HullProgram("Signals Frigate", HullFamily.ELECTRONIC_WARFARE, "jamming and countermeasure support", 3),
                new HullProgram("Mercy Ship", HullFamily.HOSPITAL, "hospital wards and rescue operations", 2),
                new HullProgram("Mobile Refinery", HullFamily.LOGISTICS, "ore processing and fleet logistics", 3),
                new HullProgram("Marine Transport", HullFamily.BOARDING, "boarding pods and marine lift", 3),
                new HullProgram("Mine Warfare Cutter", HullFamily.MINE_WARFARE, "mine laying and sweeping", 3),
                new HullProgram("Recovery Tug", HullFamily.RECOVERY, "tow, tractor, and disabled-hull recovery", 2),
                new HullProgram("Prototype Testbed", HullFamily.PROTOTYPE, "experimental modules with heavy upkeep", 7),
                new HullProgram("Faction Titan Families", HullFamily.TITAN, "faction-unique strategic command hulls", 8)
        );
    }

    private static List<RefitModule> buildModuleCatalog() {
        return List.of(
                new RefitModule("reinforced-frame", ModuleSlot.DEFENSE, ModuleRarity.INDUSTRIAL,
                        new HullBudgets(3, 1, 1, 1, 2), "open market", "hull durability", false),
                new RefitModule("aegis-screen", ModuleSlot.DEFENSE, ModuleRarity.MILITARY,
                        new HullBudgets(2, 3, 2, 1, 2), "Blue", "shield recovery and escort resilience", false),
                new RefitModule("long-lance-battery", ModuleSlot.WEAPON, ModuleRarity.RARE,
                        new HullBudgets(4, 4, 4, 2, 3), "Red", "long-range artillery fire", false),
                new RefitModule("green-logistics-suite", ModuleSlot.CARGO, ModuleRarity.INDUSTRIAL,
                        new HullBudgets(2, 2, 1, 2, 1), "Green", "cargo capacity and field resupply", false),
                new RefitModule("yellow-salvage-rig", ModuleSlot.SUPPORT, ModuleRarity.MILITARY,
                        new HullBudgets(2, 2, 2, 2, 2), "Yellow", "recovery and repair economy", false),
                new RefitModule("captured-ew-suite", ModuleSlot.SENSOR, ModuleRarity.CAPTURED,
                        new HullBudgets(2, 3, 2, 1, 2), "Red", "jamming and lock disruption", true),
                new RefitModule("prototype-phase-drive", ModuleSlot.ENGINE, ModuleRarity.PROTOTYPE,
                        new HullBudgets(3, 4, 3, 2, 4), "Blue", "high-risk mobility", false)
        );
    }

    private static List<RefitTemplate> buildStandardLoadouts() {
        RefitTemplate line = new RefitTemplate("Line Anchor",
                List.of(MODULE_CATALOG.get(0), MODULE_CATALOG.get(1)), "durable fleet line");
        RefitTemplate ghost = new RefitTemplate("Ghost Spotter",
                List.of(MODULE_CATALOG.get(5), MODULE_CATALOG.get(6)), "captured-tech reconnaissance");
        RefitTemplate tender = new RefitTemplate("Relief Tender",
                List.of(MODULE_CATALOG.get(3), MODULE_CATALOG.get(4)), "logistics and recovery");
        SAVED_LOADOUTS.put(line.name, line);
        SAVED_LOADOUTS.put(ghost.name, ghost);
        SAVED_LOADOUTS.put(tender.name, tender);
        return List.of(line, ghost, tender);
    }

    private static HullFamily familyFor(ShipRole role) {
        if (role == null) return HullFamily.COMBAT;
        if (role.isTitanOrMothership()) return HullFamily.TITAN;
        return switch (role) {
            case STEALTH_SHIP -> HullFamily.STEALTH;
            case ARTILLERY_SHIP -> HullFamily.ARTILLERY;
            case TRANSPORT, HAULER -> HullFamily.LOGISTICS;
            case MINER -> HullFamily.MINING;
            case BASE, STATIC_TURRET -> HullFamily.COMMAND;
            default -> HullFamily.COMBAT;
        };
    }

    private static String[] identityFor(ShipRole role, HullFamily family) {
        return switch (family) {
            case STEALTH -> text("ambush and reconnaissance", "isolated artillery", "long sensor fights");
            case ARTILLERY -> text("long-range fire support", "slow capitals", "fast flankers without screens");
            case LOGISTICS -> text("fleet resupply and evacuation", "attrition", "direct combat");
            case MINING -> text("resource extraction and salvage processing", "campaign shortages", "raiders");
            case COMMAND -> text("local command and defense anchor", "uncoordinated attackers", "siege weapons");
            case TITAN -> text("faction-unique command centerpiece", "fleet-scale problems", "isolation and upkeep");
            default -> combatIdentity(role);
        };
    }

    private static String[] combatIdentity(ShipRole role) {
        return switch (role) {
            case CIWS_CORVETTE, PD_CRAFT -> text("missile screen", "missile and bomber salvos", "heavy guns");
            case MISSILE_BOAT, BOMBER -> text("stand-off strike", "slow heavy hulls", "point defense");
            case CARRIER, DRONE_CARRIER -> text("sortie support", "isolated targets", "close pressure");
            case FIGHTER, DRONE, PATROL -> text("fast skirmisher", "exposed support hulls", "area denial");
            case PICKET, FRIGATE -> text("escort line", "light raiders", "capital batteries");
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> text("line anchor", "medium hulls", "flanks and logistics pressure");
            default -> text("flexible line combatant", "unsupported targets", "specialist counters");
        };
    }

    private static String factionVariant(ShipRole role) {
        return "Blue command-net / Red kinetic / Green aegis / Yellow assault variant for " + role.name().toLowerCase(Locale.US);
    }

    private static String silhouetteCheck(ShipRole role) {
        if (role == null) return "readable at combat zoom";
        if (role.isTitanOrMothership()) return "capital spine and broad command silhouette remain readable at combat zoom";
        if (role == ShipRole.ARTILLERY_SHIP) return "long weapon spine remains distinct from escort hulls at combat zoom";
        if (role == ShipRole.STEALTH_SHIP) return "narrow stealth wedge remains distinct from patrol hulls at combat zoom";
        return "role silhouette remains distinct at combat zoom";
    }

    private static HullBudgets sumCosts(List<RefitModule> modules) {
        int weight = 0;
        int power = 0;
        int heat = 0;
        int crew = 0;
        int maintenance = 0;
        for (RefitModule module : modules) {
            if (module == null) continue;
            weight += module.costs.weight;
            power += module.costs.power;
            heat += module.costs.heat;
            crew += module.costs.crew;
            maintenance += module.costs.maintenance;
        }
        return new HullBudgets(weight, power, heat, crew, maintenance);
    }

    private static void checkBudget(String label, int used, int max, List<String> warnings) {
        if (used > max) warnings.add("budget exceeded: " + label + " " + used + "/" + max);
    }

    private static int clampMorale(int morale) {
        return Math.max(0, Math.min(100, morale));
    }

    private static String archiveLine(PersistentShip ship) {
        return ship.name + "  |  " + ship.role + "  |  K " + ship.kills + " R " + ship.rescues
                + " SCARS " + ship.scars + " MORALE " + ship.morale;
    }

    private static String baseName(String name) {
        int quote = (name == null) ? -1 : name.indexOf(" \"");
        return (quote > 0) ? name.substring(0, quote) : clean(name, "Unnamed Ship");
    }

    private static String[] text(String role, String counter, String weakness) {
        return new String[]{role, counter, weakness};
    }

    private static String clean(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
