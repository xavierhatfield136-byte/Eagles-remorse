import app.config.GameMode;
import app.config.GameConfig;
import app.persistence.CampaignCheckpointStore;
import app.persistence.CampaignUnlockProfile;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Campaign progression layer for a 2-hour run:
 * - 24 sectors
 * - objective per sector
 * - act breaks
 * - paced unlock grants
 */
public final class CampaignSystem {
    private CampaignSystem() {}

    // Fleet hub auto-open delay when player completes a sector
    private static final double FLEET_HUB_AUTO_OPEN_DELAY = 10.0;

    private enum BossKind {
        NONE,
        MID_ALPHA,
        MID_BETA,
        FINAL
    }

    private enum MapModifier {
        NONE("Clear Space"),
        NEBULA("Nebula"),
        DEBRIS_FIELD("Debris Field"),
        EMP_ZONE("EMP Zone"),
        RESOURCE_DROUGHT("Resource Drought"),
        RICH_DEPOSITS("Rich Deposits"),
        SOLAR_STORM("Solar Storm"),
        GRAVITY_SHEAR("Gravity Shear"),
        SUPPLY_WINDFALL("Supply Windfall");

        final String label;

        MapModifier(String label) {
            this.label = label;
        }
    }

    // Mission zone layout constants
    private static final int MISSION_ZONE_COLUMNS = 6;
    private static final int MISSION_ZONE_ROWS = 3;
    private static final double MISSION_SUBZONE_CLAMP_MARGIN = 180.0;
    private static final double DEFAULT_MISSION_SUBZONE_WIDTH = 5000.0;
    private static final double DEFAULT_MISSION_SUBZONE_HEIGHT = 5000.0;
    private static final double MAX_MISSION_SUBZONE_WIDTH = 20000.0;
    private static final double MAX_MISSION_SUBZONE_HEIGHT = 20000.0;
    private static final int ZONES_PER_ROW = 8;

    private static final class MissionLayout {
        final double subzoneWidth;
        final double subzoneHeight;
        final double subzoneGap;
        final double zoneWidth;
        final double zoneHeight;

        MissionLayout(double subzoneWidth, double subzoneHeight, double subzoneGap) {
            this.subzoneWidth = Math.max(600.0, subzoneWidth);
            this.subzoneHeight = Math.max(600.0, subzoneHeight);
            this.subzoneGap = Math.max(0.0, subzoneGap);
            this.zoneWidth = MISSION_ZONE_COLUMNS * this.subzoneWidth
                    + Math.max(0, MISSION_ZONE_COLUMNS - 1) * this.subzoneGap;
            this.zoneHeight = MISSION_ZONE_ROWS * this.subzoneHeight
                    + Math.max(0, MISSION_ZONE_ROWS - 1) * this.subzoneGap;
        }
    }

    static double clampedMissionSubzoneWidth(GameConfig config) {
        double raw = (config == null) ? DEFAULT_MISSION_SUBZONE_WIDTH : Math.max(1.0, config.worldW);
        return MathUtil.clamp(raw, 600.0, MAX_MISSION_SUBZONE_WIDTH);
    }

    static double clampedMissionSubzoneHeight(GameConfig config) {
        double raw = (config == null) ? DEFAULT_MISSION_SUBZONE_HEIGHT : Math.max(1.0, config.worldH);
        return MathUtil.clamp(raw, 600.0, MAX_MISSION_SUBZONE_HEIGHT);
    }

    private static final class SectorScript {
        final int sector;
        final ObjectiveType objectiveType;
        final String objectiveLabel;
        final double objectiveGoal;
        final double timeLimitSec;
        final BossKind bossKind;
        final MapModifier[] modifiers;

        SectorScript(int sector, ObjectiveType objectiveType, String objectiveLabel, double objectiveGoal, double timeLimitSec, BossKind bossKind, MapModifier... modifiers) {
            this.sector = sector;
            this.objectiveType = objectiveType;
            this.objectiveLabel = objectiveLabel;
            this.objectiveGoal = objectiveGoal;
            this.timeLimitSec = timeLimitSec;
            this.bossKind = bossKind;
            this.modifiers = (modifiers == null || modifiers.length == 0)
                    ? new MapModifier[]{MapModifier.NONE}
                    : modifiers;
        }
    }

    private static final class SideObjectiveScript {
        final int sector;
        final SideObjectiveType type;
        final String label;
        final double goal;
        final int rewardCredits;

        SideObjectiveScript(int sector, SideObjectiveType type, String label, double goal, int rewardCredits) {
            this.sector = sector;
            this.type = type;
            this.label = label;
            this.goal = goal;
            this.rewardCredits = rewardCredits;
        }
    }

    private static final class SectorLore {
        final int sector;
        final String title;
        final String location;
        final String hudLead;
        final String completionLead;

        SectorLore(int sector, String title, String location, String hudLead, String completionLead) {
            this.sector = sector;
            this.title = title;
            this.location = location;
            this.hudLead = hudLead;
            this.completionLead = completionLead;
        }
    }

    public enum LandmarkType {
        PLANET,
        STAR,
        RING,
        COLONY,
        RELAY,
        FORTRESS,
        FRONT,
        CORRIDOR
    }

    public static final class CampaignLandmark {
        public final LandmarkType type;
        public final String label;
        public final String subtitle;
        public final double x;
        public final double y;
        public final double radius;
        public final Color fillColor;
        public final Color edgeColor;
        public final boolean discoveryDerived;

        CampaignLandmark(LandmarkType type,
                         String label,
                         String subtitle,
                         double x,
                         double y,
                         double radius,
                         Color fillColor,
                         Color edgeColor,
                         boolean discoveryDerived) {
            this.type = (type == null) ? LandmarkType.COLONY : type;
            this.label = (label == null) ? "" : label;
            this.subtitle = (subtitle == null) ? "" : subtitle;
            this.x = x;
            this.y = y;
            this.radius = Math.max(40.0, radius);
            this.fillColor = (fillColor == null) ? new Color(120, 170, 220, 46) : fillColor;
            this.edgeColor = (edgeColor == null) ? new Color(200, 225, 255, 180) : edgeColor;
            this.discoveryDerived = discoveryDerived;
        }
    }

    private static final class MissionSection {
        final String label;
        final double x;
        final double y;
        final double radius;

        MissionSection(String label, double x, double y, double radius) {
            this.label = (label == null || label.isBlank()) ? "MISSION SITE" : label.trim();
            this.x = x;
            this.y = y;
            this.radius = Math.max(120.0, radius);
        }
    }

    private enum MissionTheme {
        BREAKTHROUGH,
        SALVAGE_RUN,
        RELAY_DEFENSE,
        MINE_CORRIDOR,
        PRISON_BREAK,
        ANOMALY_STORM
    }

    private enum DiscoveryKind {
        CACHE,
        ORE,
        REINFORCEMENT,
        AMBUSH,
        SALVAGE_HULK,
        SUPPLY_CACHE,
        DATA_RELAY,
        WRECK_FIELD,
        MINEFIELD,
        DRIFTING_TURRET,
        NEUTRAL_TRADER,
        PRISON_BARGE,
        ANOMALY,
        FLEET_ASSET
    }

    private static final class DiscoverySite {
        final String label;
        final String subtitle;
        final DiscoveryKind kind;
        final double x;
        final double y;
        final double radius;
        boolean discovered;

        DiscoverySite(String label, String subtitle, DiscoveryKind kind, double x, double y, double radius) {
            this.label = (label == null || label.isBlank()) ? "UNKNOWN CONTACT" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.kind = (kind == null) ? DiscoveryKind.CACHE : kind;
            this.x = x;
            this.y = y;
            this.radius = Math.max(110.0, radius);
        }
    }

    private static final class RecoverableWreckSite {
        final String label;
        final String subtitle;
        final ShipRole role;
        final double x;
        final double y;
        final double radius;
        boolean claimed;
        double lastThreatWarnAtSec = -1000.0;

        RecoverableWreckSite(String label, String subtitle, ShipRole role, double x, double y, double radius) {
            this.label = (label == null || label.isBlank()) ? "Recoverable Wreck" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.x = x;
            this.y = y;
            this.radius = Math.max(120.0, radius);
        }
    }

    public static final class DiscoverySignalSite {
        public final String label;
        public final String subtitle;
        public final String kindTag;
        public final double x;
        public final double y;
        public final double radius;

        DiscoverySignalSite(String label, String subtitle, double x, double y, double radius) {
            this(label, subtitle, "UNKNOWN", x, y, radius);
        }

        DiscoverySignalSite(String label, String subtitle, String kindTag, double x, double y, double radius) {
            this.label = (label == null || label.isBlank()) ? "UNKNOWN CONTACT" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.kindTag = (kindTag == null || kindTag.isBlank()) ? "UNKNOWN" : kindTag.trim();
            this.x = x;
            this.y = y;
            this.radius = Math.max(80.0, radius);
        }
    }

    public enum ObjectiveMarkerType {
        PRIMARY_OBJECTIVE,
        NEXT_ROUTE,
        ESCORT_TARGET,
        PROTECTED_ASSET,
        DESTROY_TARGET,
        CAPTURE_ZONE,
        BOSS_TARGET,
        OPTIONAL_OBJECTIVE
    }

    public enum SupportMarkerType {
        ANOMALY,
        FACTION_CONTACT,
        SALVAGE,
        RESOURCE,
        HAZARD,
        INTEL
    }

    public static final class CampaignObjectiveMarker {
        public final ObjectiveMarkerType type;
        public final String label;
        public final String subtitle;
        public final double x;
        public final double y;
        public final double radius;
        public final int priority;

        CampaignObjectiveMarker(ObjectiveMarkerType type,
                                String label,
                                String subtitle,
                                double x,
                                double y,
                                double radius,
                                int priority) {
            this.type = (type == null) ? ObjectiveMarkerType.PRIMARY_OBJECTIVE : type;
            this.label = (label == null || label.isBlank()) ? "OBJECTIVE" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.x = x;
            this.y = y;
            this.radius = Math.max(60.0, radius);
            this.priority = Math.max(0, priority);
        }
    }

    public static final class CampaignSupportMarker {
        public final SupportMarkerType type;
        public final String label;
        public final String subtitle;
        public final double x;
        public final double y;
        public final double radius;
        public final int priority;

        CampaignSupportMarker(SupportMarkerType type,
                              String label,
                              String subtitle,
                              double x,
                              double y,
                              double radius,
                              int priority) {
            this.type = (type == null) ? SupportMarkerType.ANOMALY : type;
            this.label = (label == null || label.isBlank()) ? "SUPPORT CONTACT" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.x = x;
            this.y = y;
            this.radius = Math.max(60.0, radius);
            this.priority = Math.max(0, priority);
        }
    }

    public enum CampaignLocationType {
        MAIN_MISSION,
        RESOURCE_ZONE,
        SALVAGE_FIELD,
        DISTRESS_SIGNAL,
        ENEMY_ACTIVITY,
        HIDDEN_CACHE,
        STORY_EVENT,
        REPAIR_SITE
    }

    public enum HubService {
        REPAIR("Repair Fleet"),
        TRADE("Trade Market"),
        REFIT("Refit Ships"),
        SHIPYARD("Build Ship"),
        SUPPLY("Buy Supplies"),
        INTEL("Gather Intel"),
        CONTRACTS("Contracts"),
        SALVAGE("Sell Salvage"),
        FUEL("Buy Fuel");

        public final String label;

        HubService(String label) {
            this.label = (label == null || label.isBlank()) ? name() : label;
        }
    }

    public static final class CampaignLocation {
        public final String id;
        public final String name;
        public final double x;
        public final double y;
        public final CampaignLocationType type;
        public final float threatLevel;
        public final boolean primaryMission;
        public final int missionIndex;
        public final String detail;
        public final List<HubService> services;
        public boolean discovered;
        public boolean completed;
        public boolean consumed;

        CampaignLocation(String id,
                         String name,
                         double x,
                         double y,
                         CampaignLocationType type,
                         float threatLevel,
                         boolean primaryMission,
                         int missionIndex,
                         String detail,
                         HubService... services) {
            this.id = (id == null || id.isBlank()) ? "loc" : id.trim();
            this.name = (name == null || name.isBlank()) ? "Unknown Location" : name.trim();
            this.x = x;
            this.y = y;
            this.type = (type == null) ? CampaignLocationType.STORY_EVENT : type;
            this.threatLevel = Math.max(0.0f, threatLevel);
            this.primaryMission = primaryMission;
            this.missionIndex = Math.max(0, missionIndex);
            this.detail = (detail == null) ? "" : detail.trim();
            ArrayList<HubService> resolvedServices = new ArrayList<>();
            if (services != null) {
                for (HubService service : services) {
                    if (service != null && !resolvedServices.contains(service)) {
                        resolvedServices.add(service);
                    }
                }
            }
            this.services = List.copyOf(resolvedServices);
            this.discovered = true;
        }
    }

    public static final class CampaignTravelState {
        public String originId = "";
        public String destinationId = "";
        public double progress = 0.0;
        public double durationSec = 0.0;
        public boolean traveling = false;
        public float interceptionRisk = 0.0f;

        public void clear() {
            originId = "";
            destinationId = "";
            progress = 0.0;
            durationSec = 0.0;
            traveling = false;
            interceptionRisk = 0.0f;
        }
    }

    private enum StrategicTaskForceKind {
        PATROL,
        STRIKE,
        STEALTH,
        CONVOY,
        SALVAGE
    }

    private static final class StrategicTaskForce {
        final int id;
        final StrategicTaskForceKind kind;
        final Faction faction;
        final String label;
        final boolean hostile;
        final boolean spawnsEncounter;
        final SupportMarkerType markerType;
        final Set<Integer> spawnedShipIds = new HashSet<>();
        int currentSubzone;
        int targetSubzone;
        double transitRemainingSec;
        double dwellRemainingSec;
        double maxStrength = 100.0;
        double currentStrength = 100.0;
        double disruptionRemainingSec = 0.0;
        double breakoffRemainingSec = 0.0;
        int torpedoStrikesSustained = 0;
        int sortieStrikesSustained = 0;
        boolean encounterSpawned = false;
        boolean encounterResolved = false;

        StrategicTaskForce(int id,
                           StrategicTaskForceKind kind,
                           Faction faction,
                           String label,
                           boolean hostile,
                           boolean spawnsEncounter,
                           SupportMarkerType markerType,
                           int currentSubzone,
                           double dwellRemainingSec) {
            this.id = Math.max(1, id);
            this.kind = (kind == null) ? StrategicTaskForceKind.PATROL : kind;
            this.faction = faction;
            this.label = (label == null || label.isBlank()) ? "Task Force" : label.trim();
            this.hostile = hostile;
            this.spawnsEncounter = spawnsEncounter;
            this.markerType = (markerType == null) ? SupportMarkerType.FACTION_CONTACT : markerType;
            this.currentSubzone = currentSubzone;
            this.targetSubzone = currentSubzone;
            this.dwellRemainingSec = Math.max(2.0, dwellRemainingSec);
        }
    }

    private enum DivisionStance {
        RESERVE,
        LINE,
        STRIKE,
        ESCORT,
        SCOUT
    }

    private static final class StrategicDivisionState {
        final int groupId;
        DivisionStance stance;
        int currentSubzone;
        int targetSubzone;
        double transitRemainingSec;

        StrategicDivisionState(int groupId, DivisionStance stance, int currentSubzone) {
            this.groupId = groupId;
            this.stance = (stance == null) ? DivisionStance.LINE : stance;
            this.currentSubzone = currentSubzone;
            this.targetSubzone = currentSubzone;
        }
    }

    public enum CampaignRouteKind {
        MAIN,
        SALVAGE,
        DEEP_STRIKE
    }

    public static final class CampaignRouteChoice {
        public final CampaignRouteKind kind;
        public final int targetSector;
        public final String title;
        public final String detail;
        public final int creditBonus;
        public final int oreBonus;
        public final int branchScoreDelta;

        CampaignRouteChoice(CampaignRouteKind kind,
                            int targetSector,
                            String title,
                            String detail,
                            int creditBonus,
                            int oreBonus,
                            int branchScoreDelta) {
            this.kind = (kind == null) ? CampaignRouteKind.MAIN : kind;
            this.targetSector = Math.max(1, targetSector);
            this.title = (title == null || title.isBlank()) ? "Route" : title;
            this.detail = (detail == null) ? "" : detail;
            this.creditBonus = Math.max(0, creditBonus);
            this.oreBonus = Math.max(0, oreBonus);
            this.branchScoreDelta = branchScoreDelta;
        }
    }

    private static final class PersistentFleetEntry {
        final int slotId;
        final ShipRole role;
        String name;
        boolean destroyed = false;
        int activeShipId = -1;
        int commandGroupId = 0;
        int hullLv = 0;
        int shieldLv = 0;
        int turretLv = 0;
        int miningLv = 0;
        int hangarLv = 0;
        int cargo = 0;
        int cargoMax = 0;
        String turretData = "";
        String primaryWeaponFamilyName = Ship.PrimaryWeaponFamily.ENERGY_BOLT.name();
        double relX = Double.NaN;
        double relY = Double.NaN;
        double relAngle = Double.NaN;

        PersistentFleetEntry(int slotId, ShipRole role, String name) {
            this.slotId = Math.max(1, slotId);
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.name = (name == null || name.isBlank()) ? ("Blue Wing " + this.slotId) : name;
        }
    }

    private static final int AUTHORED_VERTICAL_SLICE_LAST_SECTOR = 2;
    private static final int CAMPAIGN_STARTING_CREDITS = 1000;
    private static final int CAMPAIGN_BLUE_ESCORT_CAP = 15;
    private static final int CAMPAIGN_BLUE_LINE_CAP = 11;
    private static final int CAMPAIGN_BLUE_CAPITAL_CAP = 7;
    private static final int CAMPAIGN_ESCORT_CAP_UPGRADE_STEP = 2;
    private static final int CAMPAIGN_LINE_CAP_UPGRADE_STEP = 1;
    private static final int CAMPAIGN_CAPITAL_CAP_UPGRADE_STEP = 1;
    private static final int CAMPAIGN_ESCORT_CAP_UPGRADE_MAX_LEVEL = 5;
    private static final int CAMPAIGN_LINE_CAP_UPGRADE_MAX_LEVEL = 4;
    private static final int CAMPAIGN_CAPITAL_CAP_UPGRADE_MAX_LEVEL = 3;
    private static final int CAMPAIGN_PLAYER_STARTING_HANGAR_TIER = 1;
    private static final int CAMPAIGN_PLAYER_MAX_HANGAR_TIER = 5;
    private static final int CAMPAIGN_ENEMY_MAX_HANGAR_TIER = 3;
    private static final int CAMPAIGN_FLAGSHIP_COMMAND_GROUP = 0;
    private static final int CAMPAIGN_FLAGSHIP_STANDARD_COMMAND_CAPACITY = 5;
    private static final int CAMPAIGN_FLAGSHIP_BUILTIN_ELITE_COMMAND_CAPACITY = 1;
    private static final int CAMPAIGN_SUPERSHIP_UNLOCK_SECTOR = 6;
    private static final int CAMPAIGN_SUPERSHIP_FLAGSHIP_BERTH_TIER = 4;
    private static final int CAMPAIGN_TRANSPORT_FLEET_ORE_CAPACITY = 10_000;
    private static final int MISSION_EDGE_ENTRY_SAFE_COLUMN_DEPTH = 2;
    private static final int MISSION_INTERIOR_ENTRY_SAFE_COLUMN_DEPTH = 1;
    private static final double CAMPAIGN_POCKET_MARGIN = 220.0;
    private static final double DISCOVERY_JITTER_MIN_SEPARATION = 260.0;
    private static final double ESCORT_PLAYER_FORMATION_RADIUS = 360.0;
    private static final double ESCORT_SUPPORT_RADIUS = 460.0;
    private static final double ESCORT_THREAT_RADIUS = 620.0;
    private static final double ESCORT_PROGRESS_THRESHOLD = 0.58;
    private static final double ESCORT_TIGHT_SLOT_MARGIN = 18.0;
    private static final double ESCORT_TIGHT_HOLD_RADIUS = 44.0;
    private static final double ESCORT_TIGHT_CATCHUP_RADIUS = 260.0;
    private static final double ESCORT_TIGHT_CATCHUP_SPEED_MUL = 1.08;
    private static final String[] ACT_TITLES = {
            "",
            "TRADE HUB COLLAPSE",
            "THE LONG ROAD HOME",
            "RETURN TO EARTH"
    };

    // Zone layout methods
    private static int routeGridColumn(int sector) {
        return Math.max(0, (sector - 1) % ZONES_PER_ROW);
    }

    private static int routeGridRow(int sector) {
        return Math.max(0, (sector - 1) / ZONES_PER_ROW);
    }

    private static double galaxyBoardX(GameContext ctx, int column) {
        double width = (ctx == null) ? DEFAULT_MISSION_SUBZONE_WIDTH * ZONES_PER_ROW : Math.max(2400.0, ctx.WORLD_W);
        double margin = width * 0.10;
        double usable = Math.max(400.0, width - margin * 2.0);
        return margin + usable * (MathUtil.clamp(column, 0, 7) / 7.0);
    }

    private static double galaxyBoardY(GameContext ctx, int row) {
        double height = (ctx == null) ? DEFAULT_MISSION_SUBZONE_HEIGHT * 3.0 : Math.max(1800.0, ctx.WORLD_H);
        double margin = height * 0.16;
        double usable = Math.max(260.0, height - margin * 2.0);
        return margin + usable * (MathUtil.clamp(row, 0, 2) / 2.0);
    }

    private static MissionLayout missionLayout(GameConfig config) {
        double sectorWidth = clampedMissionSubzoneWidth(config);
        double sectorHeight = clampedMissionSubzoneHeight(config);
        // Mission subzones now share borders directly instead of floating far apart in one giant battlespace.
        // Physical separation is preserved by per-subzone clamping and same-subzone detection rules.
        return new MissionLayout(sectorWidth, sectorHeight, 0.0);
    }

    private static MissionLayout missionLayout(GameContext ctx) {
        return missionLayout((ctx == null) ? null : ctx.config);
    }

    private static double getZoneX(int sector) {
        return 0.0;
    }

    private static double getZoneY(int sector) {
        return 0.0;
    }

    private static double getZoneCenterX(int sector) {
        return getZoneX(sector) + missionLayout((GameConfig) null).zoneWidth / 2;
    }

    private static double getZoneCenterY(int sector) {
        return getZoneY(sector) + missionLayout((GameConfig) null).zoneHeight / 2;
    }

    static boolean usesMissionSubzones(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && ctx != null && ctx.config != null && ctx.config.mode == GameMode.CAMPAIGN_OPS;
    }

    public static boolean isStrategicGalaxyMapMode(GameContext ctx) {
        return isStrategicOvermapMode(ctx);
    }

    public static boolean isCampaignMapScreenActive(GameContext ctx) {
        CampaignState st = state(ctx);
        return ctx != null
                && st != null
                && st.enabled
                && isStrategicOvermapMode(st)
                && ctx.ui != null
                && ctx.ui.mapOpen
                && ctx.state == GameState.MAP;
    }

    public static List<CampaignLocation> mainCampaignLocations(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.galaxyMainPois.isEmpty()) return List.of();
        return List.copyOf(st.galaxyMainPois);
    }

    public static List<CampaignLocation> campaignAreasOfInterest(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.galaxyAreasOfInterest.isEmpty()) return List.of();
        return List.copyOf(st.galaxyAreasOfInterest);
    }

    public static CampaignLocation currentCampaignLocation(GameContext ctx) {
        CampaignState st = state(ctx);
        return campaignLocationById(st, (st == null) ? "" : st.currentGalaxyLocationId);
    }

    public static CampaignLocation selectedCampaignLocation(GameContext ctx) {
        CampaignState st = state(ctx);
        return campaignLocationById(st, (st == null) ? "" : st.selectedGalaxyLocationId);
    }

    public static List<HubService> selectedCampaignLocationServices(GameContext ctx) {
        CampaignLocation location = selectedCampaignLocation(ctx);
        return (location == null) ? List.of() : location.services;
    }

    public static int campaignFuel(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0 : Math.max(0, st.campaignFuel);
    }

    public static int campaignSupplies(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0 : Math.max(0, st.campaignSupplies);
    }

    public static int campaignAmmo(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0 : Math.max(0, st.campaignAmmo);
    }

    public static int campaignSalvageStock(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0 : Math.max(0, st.campaignSalvage);
    }

    public static CampaignTravelState campaignTravelState(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? null : st.galaxyTravel;
    }

    public static boolean openSelectedHubService(GameContext ctx, HubService service) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || ctx.ui == null || service == null) return false;
        CampaignLocation location = selectedCampaignLocation(ctx);
        if (location == null || !location.services.contains(service)) return false;
        ctx.ui.showCampaignHubMenu(location.id, service.name());
        EventSystem.showBanner(ctx, service.label.toUpperCase(Locale.US) + " - " + location.name.toUpperCase(Locale.US), 1.0);
        return true;
    }

    public static void closeHubServiceMenu(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.clearCampaignHubMenu();
    }

    public static boolean confirmSelectedHubService(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || ctx.ui == null || !ctx.ui.campaignHubMenu.active) return false;
        CampaignLocation location = campaignLocationById(st, ctx.ui.campaignHubMenu.locationId);
        HubService service = hubServiceById(ctx.ui.campaignHubMenu.serviceId);
        if (location == null || service == null) {
            ctx.ui.clearCampaignHubMenu();
            return false;
        }
        boolean result = performHubService(ctx, st, location, service);
        ctx.ui.clearCampaignHubMenu();
        return result;
    }

    public static CampaignLocation nearestCampaignLocation(GameContext ctx, double x, double y, double maxDist) {
        CampaignState st = state(ctx);
        if (st == null) return null;
        CampaignLocation best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (CampaignLocation location : allCampaignLocations(st)) {
            if (location == null || !location.discovered) continue;
            double d2 = GameMath.dist2(x, y, location.x, location.y);
            if (d2 > bestD2) continue;
            if (best == null || d2 < bestD2) {
                best = location;
                bestD2 = d2;
            }
        }
        return best;
    }

    public static boolean selectCampaignLocation(GameContext ctx, double x, double y) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !isStrategicGalaxyMapMode(ctx)) return false;
        CampaignLocation location = nearestCampaignLocation(ctx, x, y, 260.0);
        if (location == null) return false;
        st.selectedGalaxyLocationId = location.id;
        EventSystem.showBanner(ctx, "DESTINATION SELECTED: " + location.name.toUpperCase(Locale.US), 1.2);
        return true;
    }

    public static boolean startTravelToSelectedLocation(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !isStrategicGalaxyMapMode(ctx)) return false;
        if (st.galaxyTravel.traveling) {
            EventSystem.showBanner(ctx, "TRAVEL ALREADY IN PROGRESS", 1.2);
            return true;
        }
        CampaignLocation current = currentCampaignLocation(ctx);
        CampaignLocation destination = selectedCampaignLocation(ctx);
        if (current == null || destination == null) return false;
        if (current.id.equalsIgnoreCase(destination.id)) {
            if (destination.primaryMission && !destination.completed) {
                beginCampaignLocationEncounterChoice(ctx, st, destination);
            } else {
                EventSystem.showBanner(ctx, "ALREADY AT " + destination.name.toUpperCase(Locale.US), 1.1);
            }
            return true;
        }
        double dist = Math.hypot(destination.x - current.x, destination.y - current.y);
        st.galaxyTravel.originId = current.id;
        st.galaxyTravel.destinationId = destination.id;
        st.galaxyTravel.progress = 0.0;
        st.galaxyTravel.durationSec = Math.max(8.0, dist / 260.0);
        st.galaxyTravel.interceptionRisk = (float) MathUtil.clamp(
                8.0 + destination.threatLevel * 55.0 + st.enemyAlertLevel * 0.35 + st.earthProgress * 30.0,
                0.0, 95.0);
        st.galaxyTravel.traveling = true;
        EventSystem.showBanner(ctx,
                "TRAVELING TO " + destination.name.toUpperCase(Locale.US)
                        + "  ETA " + (int) Math.ceil(st.galaxyTravel.durationSec) + "S",
                1.5);
        return true;
    }

    static int missionSubzoneCount() {
        return MISSION_ZONE_COLUMNS * MISSION_ZONE_ROWS;
    }

    static int missionSubzoneColumns() {
        return MISSION_ZONE_COLUMNS;
    }

    static int missionSubzoneRows() {
        return MISSION_ZONE_ROWS;
    }

    static double missionSubzoneWidth() {
        return missionLayout((GameConfig) null).subzoneWidth;
    }

    static double missionSubzoneWidth(GameContext ctx) {
        return missionLayout(ctx).subzoneWidth;
    }

    static double missionSubzoneHeight() {
        return missionLayout((GameConfig) null).subzoneHeight;
    }

    static double missionSubzoneHeight(GameContext ctx) {
        return missionLayout(ctx).subzoneHeight;
    }

    static int missionSubzoneIndex(int col, int row) {
        if (col < 0 || col >= MISSION_ZONE_COLUMNS || row < 0 || row >= MISSION_ZONE_ROWS) return -1;
        return row * MISSION_ZONE_COLUMNS + col;
    }

    static int missionSubzoneColumn(int subzoneIndex) {
        if (subzoneIndex < 0) return -1;
        return subzoneIndex % MISSION_ZONE_COLUMNS;
    }

    static int missionSubzoneRow(int subzoneIndex) {
        if (subzoneIndex < 0) return -1;
        return subzoneIndex / MISSION_ZONE_COLUMNS;
    }

    static String missionSubzoneLabel(int subzoneIndex) {
        int col = missionSubzoneColumn(subzoneIndex);
        int row = missionSubzoneRow(subzoneIndex);
        if (col < 0 || row < 0) return "SECTOR";
        char rowTag = (char) ('A' + row);
        return rowTag + Integer.toString(col + 1);
    }

    static List<DiscoverySignalSite> anomalySignalSites(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.discoverySites.isEmpty()) return List.of();
        ArrayList<DiscoverySignalSite> out = new ArrayList<>();
        for (DiscoverySite site : st.discoverySites) {
            if (site == null || site.discovered || site.kind != DiscoveryKind.ANOMALY) continue;
            out.add(new DiscoverySignalSite(site.label, site.subtitle, site.kind.name(), site.x, site.y, site.radius));
        }
        return out;
    }

    static List<DiscoverySignalSite> discoverySignalSites(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.discoverySites.isEmpty()) return List.of();
        ArrayList<DiscoverySignalSite> out = new ArrayList<>();
        for (DiscoverySite site : st.discoverySites) {
            if (site == null || site.discovered || site.kind == null) continue;
            out.add(new DiscoverySignalSite(site.label, site.subtitle, site.kind.name(), site.x, site.y, site.radius));
        }
        return out;
    }

    static List<DiscoverySignalSite> recoverableWreckSignalSites(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.recoverableWreckSites.isEmpty()) return List.of();
        ArrayList<DiscoverySignalSite> out = new ArrayList<>();
        for (RecoverableWreckSite site : st.recoverableWreckSites) {
            if (site == null || site.claimed) continue;
            out.add(new DiscoverySignalSite(site.label, site.subtitle, "RECOVERABLE_WRECK", site.x, site.y, site.radius));
        }
        return out;
    }

    static List<CampaignObjectiveMarker> activeObjectiveMarkers(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return List.of();

        ArrayList<CampaignObjectiveMarker> out = new ArrayList<>();
        if (isStrategicOvermapMode(st)) {
            CampaignLocation current = currentCampaignLocation(ctx);
            CampaignLocation selected = selectedCampaignLocation(ctx);
            for (CampaignLocation poi : st.galaxyMainPois) {
                if (poi == null || !poi.discovered) continue;
                String subtitle = poi.detail;
                if (current != null && poi.id.equalsIgnoreCase(current.id)) {
                    subtitle = "Current location  |  " + poi.detail;
                } else if (selected != null && poi.id.equalsIgnoreCase(selected.id)) {
                    subtitle = "Selected destination  |  " + poi.detail;
                }
                if (poi.completed) {
                    subtitle = "Mission secured  |  " + poi.detail;
                }
                out.add(new CampaignObjectiveMarker(
                        (current != null && poi.id.equalsIgnoreCase(current.id))
                                ? ObjectiveMarkerType.NEXT_ROUTE
                                : ObjectiveMarkerType.PRIMARY_OBJECTIVE,
                        poi.name,
                        subtitle,
                        poi.x, poi.y,
                        160.0,
                        poi.completed ? 72 : 96));
            }
            for (RecoverableWreckSite wreck : st.recoverableWreckSites) {
                if (wreck == null || wreck.claimed) continue;
                out.add(new CampaignObjectiveMarker(
                        ObjectiveMarkerType.OPTIONAL_OBJECTIVE,
                        wreck.label,
                        wreck.subtitle.isBlank() ? "Optional fleet recovery opportunity" : wreck.subtitle,
                        wreck.x, wreck.y,
                        wreck.radius,
                        58));
            }
            return out;
        }

        if (!st.missionSections.isEmpty()) {
            int activeIndex = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
            MissionSection active = st.missionSections.get(activeIndex);
            if (active != null) {
                out.add(new CampaignObjectiveMarker(
                        st.missionSectionTravelLocked ? ObjectiveMarkerType.NEXT_ROUTE : ObjectiveMarkerType.PRIMARY_OBJECTIVE,
                        active.label,
                        st.missionSectionTravelLocked
                                ? "Fly the flagship here to resume mission progress"
                                : "Clear this pocket to unlock the next objective route",
                        active.x, active.y, active.radius,
                        st.missionSectionTravelLocked ? 100 : 95));
            }
            if (!st.missionSectionTravelLocked && activeIndex + 1 < st.missionSections.size()) {
                MissionSection next = st.missionSections.get(activeIndex + 1);
                if (next != null) {
                    out.add(new CampaignObjectiveMarker(
                            ObjectiveMarkerType.NEXT_ROUTE,
                            next.label,
                            "Next route after the current pocket is secured",
                            next.x, next.y, next.radius,
                            70));
                }
            }
        }

        if (st.objectiveType == ObjectiveType.ESCORT && st.escortShip != null
                && st.escortShip.alive && !st.escortShip.dying && st.escortShip.hp > 0) {
            out.add(new CampaignObjectiveMarker(
                    ObjectiveMarkerType.ESCORT_TARGET,
                    displayShipName(st.escortShip, "Escort Target"),
                    "Keep this ship alive",
                    st.escortShip.x, st.escortShip.y,
                    Math.max(180.0, st.escortShip.radius * 2.5),
                    96));
        }

        if (st.objectiveType == ObjectiveType.CAPTURE) {
            out.add(new CampaignObjectiveMarker(
                    ObjectiveMarkerType.CAPTURE_ZONE,
                    "Capture Zone",
                    "Clear defenders and secure this area",
                    st.captureX, st.captureY, st.captureRadius,
                    94));
        }

        if (st.objectiveType == ObjectiveType.BOSS || st.objectiveType == ObjectiveType.FINAL_BOSS) {
            Ship boss = findShipById(ctx, st.bossTargetId);
            if (boss != null && boss.alive && !boss.dying && boss.hp > 0) {
                out.add(new CampaignObjectiveMarker(
                        ObjectiveMarkerType.BOSS_TARGET,
                        displayShipName(boss, "Boss Target"),
                        "Break the flagship before time runs out",
                        boss.x, boss.y,
                        Math.max(220.0, boss.radius * 3.0),
                        98));
            }
        }

        for (Integer id : st.objectiveAssetIds) {
            Ship asset = findShipById(ctx, id == null ? -1 : id);
            if (asset == null || !asset.alive || asset.dying || asset.hp <= 0) continue;
            out.add(new CampaignObjectiveMarker(
                    ObjectiveMarkerType.PROTECTED_ASSET,
                    displayShipName(asset, trimmedOrFallback(st.objectiveAssetLabel, "Protected Asset")),
                    "Must survive for mission success",
                    asset.x, asset.y,
                    Math.max(150.0, asset.radius * 2.4),
                    88));
        }

        for (Integer id : st.authoredObjectiveHostiles) {
            Ship hostile = findShipById(ctx, id == null ? -1 : id);
            if (hostile == null || !hostile.alive || hostile.dying || hostile.hp <= 0) continue;
            out.add(new CampaignObjectiveMarker(
                    ObjectiveMarkerType.DESTROY_TARGET,
                    displayShipName(hostile, "Marked Target"),
                    "Required kill for mission completion",
                    hostile.x, hostile.y,
                    Math.max(150.0, hostile.radius * 2.5),
                    90));
        }

        for (RecoverableWreckSite wreck : st.recoverableWreckSites) {
            if (wreck == null || wreck.claimed) continue;
            out.add(new CampaignObjectiveMarker(
                    ObjectiveMarkerType.OPTIONAL_OBJECTIVE,
                    wreck.label,
                    wreck.subtitle.isBlank() ? "Optional fleet recovery opportunity" : wreck.subtitle,
                    wreck.x, wreck.y,
                    wreck.radius,
                    58));
        }

        return out;
    }

    static List<CampaignSupportMarker> activeSupportMarkers(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return List.of();
        if (isStrategicOvermapMode(st)) {
            ArrayList<CampaignSupportMarker> out = new ArrayList<>();
            for (CampaignLocation area : st.galaxyAreasOfInterest) {
                if (area == null || !area.discovered) continue;
                SupportMarkerType type = switch (area.type) {
                    case RESOURCE_ZONE -> SupportMarkerType.RESOURCE;
                    case SALVAGE_FIELD -> SupportMarkerType.SALVAGE;
                    case DISTRESS_SIGNAL, STORY_EVENT, REPAIR_SITE -> SupportMarkerType.FACTION_CONTACT;
                    case ENEMY_ACTIVITY -> SupportMarkerType.HAZARD;
                    case HIDDEN_CACHE -> SupportMarkerType.INTEL;
                    default -> SupportMarkerType.ANOMALY;
                };
                String subtitle = area.detail;
                if (area.consumed && area.type != CampaignLocationType.REPAIR_SITE) {
                    subtitle = "Survey complete  |  " + area.detail;
                }
                out.add(new CampaignSupportMarker(type, area.name, subtitle, area.x, area.y, 120.0, 40));
            }
            return out;
        }
        ArrayList<CampaignSupportMarker> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (DiscoverySignalSite site : discoverySignalSites(ctx)) {
            CampaignSupportMarker marker = supportMarkerFor(site);
            if (marker == null) continue;
            String key = marker.type + "|" + marker.label + "|" + Math.round(marker.x / 25.0) + "|" + Math.round(marker.y / 25.0);
            if (!seen.add(key)) continue;
            out.add(marker);
        }
        for (CampaignSupportMarker marker : strategicTaskForceMarkers(ctx)) {
            if (marker == null) continue;
            String key = marker.type + "|" + marker.label + "|" + Math.round(marker.x / 25.0) + "|" + Math.round(marker.y / 25.0);
            if (!seen.add(key)) continue;
            out.add(marker);
        }
        return out;
    }

    static List<CampaignSupportMarker> strategicTaskForceMarkers(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled || st.strategicTaskForces.isEmpty()) return List.of();
        ArrayList<CampaignSupportMarker> out = new ArrayList<>();
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            CampaignSupportMarker marker = supportMarkerForTaskForce(ctx, st, taskForce);
            if (marker != null) out.add(marker);
        }
        return out;
    }

    static CampaignSupportMarker nearestStrategicTaskForceMarker(GameContext ctx, double x, double y, double maxDist) {
        List<CampaignSupportMarker> markers = strategicTaskForceMarkers(ctx);
        if (markers.isEmpty()) return null;
        CampaignSupportMarker best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (CampaignSupportMarker marker : markers) {
            if (marker == null) continue;
            double range = Math.max(maxDist, marker.radius);
            double d2 = GameMath.dist2(x, y, marker.x, marker.y);
            if (d2 > range * range) continue;
            if (best == null || d2 < bestD2 || (Math.abs(d2 - bestD2) < 1e-6 && marker.priority > best.priority)) {
                best = marker;
                bestD2 = d2;
            }
        }
        return best;
    }

    static String strategicMapActionSummary(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return "";
        if (isStrategicOvermapMode(st)) {
            CampaignTravelState travel = st.galaxyTravel;
            if (travel.traveling) {
                CampaignLocation destination = campaignLocationById(st, travel.destinationId);
                int eta = (int) Math.ceil(Math.max(0.0, (1.0 - travel.progress) * travel.durationSec));
                return "TRAVEL: " + ((destination == null) ? "EN ROUTE" : destination.name.toUpperCase(Locale.US))
                        + "  ETA " + eta + "S  RISK " + (int) Math.round(travel.interceptionRisk) + "%";
            }
            return "TRAVEL READY  |  ARROWS PAN NORTH  |  SELECT DESTINATION, THEN PRESS T OR DOUBLE-CLICK TO DEPART";
        }
        int torpedoes = Math.max(0, st.strategicTorpedoCharges);
        int sortieCap = strategicSortieCapacity(ctx);
        int sortiesLeft = Math.max(0, sortieCap - st.strategicSortiesLaunched);
        int atomic = Math.max(0, st.strategicAtomicCharges);
        return "TORPEDOES " + torpedoes + "  SORTIES " + sortiesLeft + "  ATOMIC " + atomic;
    }

    static List<String> strategicDivisionSummaryLines(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || st.strategicDivisions.isEmpty()) return List.of();
        if (isStrategicOvermapMode(st)) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (StrategicDivisionState division : st.strategicDivisions.values()) {
            if (division == null) continue;
            String label = strategicDivisionLabel(st, division.groupId);
            int strength = (int) Math.round(strategicDivisionStrength(st, division.groupId));
            int ships = strategicDivisionShipCount(st, division.groupId);
            String zone = missionSubzoneLabel(division.currentSubzone);
            String move = (division.transitRemainingSec > 0.0 && division.targetSubzone >= 0 && division.targetSubzone != division.currentSubzone)
                    ? (" -> " + missionSubzoneLabel(division.targetSubzone))
                    : "";
            String selected = (ctx.ui != null && ctx.ui.selectedStrategicDivisionGroupId == division.groupId) ? " *" : "";
            out.add(label + "  " + division.stance.name() + "  " + zone + move + "  STR " + strength + "  SHIPS " + ships + selected);
            if (out.size() >= 6) break;
        }
        return out;
    }

    public static boolean cycleStrategicDivisionSelection(GameContext ctx, int dir) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || st.strategicDivisions.isEmpty()) return false;
        ArrayList<Integer> ids = new ArrayList<>(st.strategicDivisions.keySet());
        ids.sort(Integer::compareTo);
        int current = ctx.ui.selectedStrategicDivisionGroupId;
        int idx = ids.indexOf(current);
        if (idx < 0) idx = 0;
        int step = (dir < 0) ? -1 : 1;
        idx = Math.floorMod(idx + step, ids.size());
        ctx.ui.selectedStrategicDivisionGroupId = ids.get(idx);
        StrategicDivisionState division = st.strategicDivisions.get(ctx.ui.selectedStrategicDivisionGroupId);
        if (division != null) {
            EventSystem.showBanner(ctx,
                    "DIVISION SELECTED: " + strategicDivisionLabel(st, division.groupId) + " " + division.stance.name(),
                    1.0);
        }
        return true;
    }

    public static boolean issueStrategicDivisionOrder(GameContext ctx, double worldX, double worldY) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || st.strategicDivisions.isEmpty()) return false;
        int groupId = ctx.ui.selectedStrategicDivisionGroupId;
        StrategicDivisionState division = st.strategicDivisions.get(groupId);
        if (division == null) return false;
        if (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) {
            EventSystem.showBanner(ctx, "FLAG DIVISION FOLLOWS THE COMMAND SHIP", 1.2);
            return true;
        }
        int subzone = missionSubzoneForPoint(ctx, st.sector, worldX, worldY);
        if (subzone < 0) {
            EventSystem.showBanner(ctx, "DIVISION ORDER FAILED: INVALID POCKET", 1.2);
            return true;
        }
        division.targetSubzone = subzone;
        if (division.currentSubzone == subzone) {
            division.transitRemainingSec = 0.0;
            EventSystem.showBanner(ctx, "DIVISION HOLDING " + missionSubzoneLabel(subzone), 1.1);
            return true;
        }
        division.transitRemainingSec = strategicDivisionTransitSeconds(division, division.currentSubzone, subzone);
        EventSystem.showBanner(ctx,
                "DIVISION ORDER: " + strategicDivisionLabel(st, groupId) + " -> " + missionSubzoneLabel(subzone),
                1.3);
        return true;
    }

    public static boolean createDetachedStrategicDivision(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !isStrategicOvermapMode(st)) return false;
        PersistentFleetEntry anchor = chooseDetachmentAnchor(st);
        if (anchor == null) {
            EventSystem.showBanner(ctx, "NO FLAGSHIP-ASSIGNED SHIP AVAILABLE TO DETACH", 1.4);
            return true;
        }
        anchor.commandGroupId = anchor.slotId;
        PersistentFleetEntry escort = chooseDetachmentEscort(st, anchor);
        if (escort != null) {
            escort.commandGroupId = anchor.slotId;
        }
        StrategicDivisionState flagship = st.strategicDivisions.get(CAMPAIGN_FLAGSHIP_COMMAND_GROUP);
        int subzone = (flagship == null) ? currentLoadedMissionSubzone(ctx) : flagship.currentSubzone;
        if (subzone < 0 && ctx.player != null) {
            subzone = missionSubzoneForPoint(ctx, st.sector, ctx.player.x, ctx.player.y);
        }
        if (subzone < 0) subzone = missionSubzoneIndex(0, 1);
        st.strategicDivisions.put(anchor.slotId,
                new StrategicDivisionState(anchor.slotId, defaultDivisionStance(anchor), subzone));
        if (ctx.ui != null) {
            ctx.ui.selectedStrategicDivisionGroupId = anchor.slotId;
        }
        String escortLabel = (escort == null) ? "" : " + " + displayPersistentFleetEntryName(escort);
        EventSystem.showBanner(ctx,
                "DIVISION DETACHED: " + displayPersistentFleetEntryName(anchor) + escortLabel,
                1.6);
        return true;
    }

    public static boolean mergeSelectedStrategicDivisionIntoFlagship(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !isStrategicOvermapMode(st) || ctx.ui == null) return false;
        int groupId = ctx.ui.selectedStrategicDivisionGroupId;
        if (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) {
            EventSystem.showBanner(ctx, "FLAG DIVISION IS ALREADY THE MAIN FLEET", 1.2);
            return true;
        }
        StrategicDivisionState division = st.strategicDivisions.get(groupId);
        StrategicDivisionState flagship = st.strategicDivisions.get(CAMPAIGN_FLAGSHIP_COMMAND_GROUP);
        if (division == null || flagship == null) return false;
        PersistentFleetEntry anchor = persistentFleetEntryBySlotId(st, groupId);
        if (anchor != null && isTitanPersistentEntry(anchor)) {
            EventSystem.showBanner(ctx, "TITAN COMMAND DIVISIONS CANNOT MERGE INTO THE FLAGSHIP GROUP", 1.5);
            return true;
        }
        if (division.currentSubzone != flagship.currentSubzone) {
            EventSystem.showBanner(ctx, "MERGE REQUIRES BOTH DIVISIONS IN THE SAME POCKET", 1.5);
            return true;
        }
        boolean changed = false;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (Math.max(CAMPAIGN_FLAGSHIP_COMMAND_GROUP, entry.commandGroupId) != groupId) continue;
            entry.commandGroupId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
            changed = true;
        }
        st.strategicDivisions.remove(groupId);
        ctx.ui.selectedStrategicDivisionGroupId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
        if (changed) {
            EventSystem.showBanner(ctx,
                    "DIVISION MERGED INTO FLAG DIVISION: " + strategicDivisionLabel(st, groupId),
                    1.5);
        }
        return true;
    }

    static List<String> strategicTaskForceSummaryLines(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || st.strategicTaskForces.isEmpty()) return List.of();
        if (isStrategicOvermapMode(st)) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce == null || taskForce.encounterResolved) continue;
            String prefix = switch (taskForce.kind) {
                case PATROL -> taskForce.hostile ? "PATROL" : "SCREEN";
                case STRIKE -> "STRIKE";
                case STEALTH -> (taskForce.disruptionRemainingSec > 0.0 || taskForce.breakoffRemainingSec > 0.0)
                        ? "STEALTH"
                        : "GHOST";
                case CONVOY -> "CONVOY";
                case SALVAGE -> "SALVAGE";
            };
            String zone = missionSubzoneLabel(taskForce.currentSubzone);
            int strengthPct = (taskForce.maxStrength <= 1e-6)
                    ? 100
                    : (int) Math.round(100.0 * MathUtil.clamp(taskForce.currentStrength / taskForce.maxStrength, 0.0, 1.0));
            String pendingSuffix = (ctx != null
                    && ctx.ui != null
                    && ctx.ui.strategicEncounterPrompt.active
                    && ctx.ui.strategicEncounterPrompt.taskForceId == taskForce.id)
                    ? " - PENDING ENGAGEMENT"
                    : "";
            out.add(prefix + " " + taskForce.label + " - " + zone + " - " + strengthPct + "%" + pendingSuffix);
            if (out.size() >= 5) break;
        }
        return out;
    }

    public static boolean hasPendingStrategicEncounterChoice(GameContext ctx) {
        return ctx != null
                && ctx.ui != null
                && ctx.ui.strategicEncounterPrompt != null
                && ctx.ui.strategicEncounterPrompt.active
                && (ctx.ui.strategicEncounterPrompt.taskForceId > 0
                || (ctx.ui.strategicEncounterPrompt.kind == UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION
                && ctx.ui.strategicEncounterPrompt.campaignLocationId != null
                && !ctx.ui.strategicEncounterPrompt.campaignLocationId.isBlank()));
    }

    public static boolean launchStrategicTorpedoStrike(GameContext ctx, double worldX, double worldY) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return false;
        StrategicTaskForce taskForce = nearestHostileStrategicTaskForce(ctx, worldX, worldY, 240.0);
        if (taskForce == null) return false;
        if (taskForce.encounterSpawned) {
            EventSystem.showBanner(ctx, "CONTACT ALREADY IN TACTICAL COMBAT", 1.2);
            return true;
        }
        if (st.strategicTorpedoCharges <= 0) {
            EventSystem.showBanner(ctx, "NO TORPEDO STRIKES READY", 1.2);
            return true;
        }
        st.strategicTorpedoCharges--;
        double damageFrac = (taskForce.kind == StrategicTaskForceKind.STRIKE) ? 0.34
                : (taskForce.kind == StrategicTaskForceKind.STEALTH ? 0.22 : 0.30);
        taskForce.currentStrength = Math.max(0.0, taskForce.currentStrength - taskForce.maxStrength * damageFrac);
        taskForce.disruptionRemainingSec = Math.max(taskForce.disruptionRemainingSec, 18.0);
        taskForce.torpedoStrikesSustained++;
        resolveStrategicTaskForceAfterRemoteStrike(ctx, st, taskForce, "LONG-RANGE TORPEDO IMPACT");
        return true;
    }

    public static boolean launchStrategicSortie(GameContext ctx, double worldX, double worldY) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return false;
        StrategicTaskForce taskForce = nearestHostileStrategicTaskForce(ctx, worldX, worldY, 240.0);
        if (taskForce == null) return false;
        if (taskForce.encounterSpawned) {
            EventSystem.showBanner(ctx, "CONTACT ALREADY IN TACTICAL COMBAT", 1.2);
            return true;
        }
        int sortieCap = strategicSortieCapacity(ctx);
        if (sortieCap <= 0) {
            EventSystem.showBanner(ctx, "NO CARRIER SORTIES AVAILABLE", 1.2);
            return true;
        }
        if (st.strategicSortiesLaunched >= sortieCap) {
            EventSystem.showBanner(ctx, "SORTIE DECKS COMMITTED", 1.2);
            return true;
        }
        st.strategicSortiesLaunched++;
        double damageFrac = (taskForce.kind == StrategicTaskForceKind.STEALTH) ? 0.28 : 0.18;
        taskForce.currentStrength = Math.max(0.0, taskForce.currentStrength - taskForce.maxStrength * damageFrac);
        taskForce.disruptionRemainingSec = Math.max(taskForce.disruptionRemainingSec, 28.0);
        taskForce.sortieStrikesSustained++;
        resolveStrategicTaskForceAfterRemoteStrike(ctx, st, taskForce, "SORTIE STRIKE COMPLETE");
        return true;
    }

    public static boolean launchStrategicAtomicStrike(GameContext ctx, double worldX, double worldY) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return false;
        StrategicTaskForce taskForce = nearestHostileStrategicTaskForce(ctx, worldX, worldY, 240.0);
        if (taskForce == null) return false;
        if (taskForce.encounterSpawned) {
            EventSystem.showBanner(ctx, "CONTACT ALREADY IN TACTICAL COMBAT", 1.2);
            return true;
        }
        if (st.strategicAtomicCharges <= 0) {
            EventSystem.showBanner(ctx, "ATOMIC OPTION UNAVAILABLE", 1.2);
            return true;
        }

        st.strategicAtomicCharges--;
        int strikeSubzone = taskForce.currentSubzone;
        for (StrategicTaskForce candidate : st.strategicTaskForces) {
            if (candidate == null || candidate.encounterResolved || candidate.currentSubzone != strikeSubzone) continue;
            double damageFrac;
            if (candidate == taskForce) {
                damageFrac = 0.92;
            } else if (candidate.hostile) {
                damageFrac = 0.46;
            } else {
                damageFrac = 0.26;
            }
            candidate.currentStrength = Math.max(0.0, candidate.currentStrength - candidate.maxStrength * damageFrac);
            candidate.disruptionRemainingSec = Math.max(candidate.disruptionRemainingSec, 36.0);
            if (candidate.currentStrength <= 1.0) {
                candidate.currentStrength = 0.0;
                candidate.encounterResolved = true;
                candidate.encounterSpawned = false;
                candidate.spawnedShipIds.clear();
            }
        }
        if (hasPendingStrategicEncounterChoice(ctx)
                && ctx.ui.strategicEncounterPrompt.taskForceId == taskForce.id
                && taskForce.encounterResolved) {
            ctx.ui.clearStrategicEncounterPrompt();
            ctx.state = GameState.RUNNING;
        }
        EventSystem.showBanner(ctx, "ATOMIC STRIKE: " + taskForce.label.toUpperCase(Locale.US) + " POCKET SCOURED", 1.8);
        return true;
    }

    private static CampaignSupportMarker supportMarkerForTaskForce(GameContext ctx, CampaignState st, StrategicTaskForce taskForce) {
        if (ctx == null || st == null || taskForce == null || taskForce.encounterResolved) return null;
        if (taskForce.currentSubzone < 0) return null;
        double x = missionSubzoneCenterX(ctx, st.sector, taskForce.currentSubzone);
        double y = missionSubzoneCenterY(ctx, st.sector, taskForce.currentSubzone);
        String subtitle = strategicTaskForceSubtitle(taskForce);
        int priority = taskForce.hostile ? 44 : 34;
        String label = taskForce.label;
        if (taskForce.kind == StrategicTaskForceKind.STEALTH
                && taskForce.disruptionRemainingSec <= 0.0
                && taskForce.breakoffRemainingSec <= 0.0) {
            label = "Ghost Trace";
        }
        return new CampaignSupportMarker(taskForce.markerType, label, subtitle, x, y, 170.0, priority);
    }

    private static String strategicTaskForceSubtitle(StrategicTaskForce taskForce) {
        if (taskForce == null) return "";
        if (taskForce.kind == StrategicTaskForceKind.STEALTH && taskForce.breakoffRemainingSec > 0.0) {
            return "Raider cell cloaked and disengaging";
        }
        if (taskForce.disruptionRemainingSec > 0.0) {
            return String.format(Locale.US, "Disrupted for %.0fs", Math.ceil(taskForce.disruptionRemainingSec));
        }
        if (taskForce.kind == StrategicTaskForceKind.STEALTH) {
            return "Low-confidence ghost contact shadowing high-value pockets";
        }
        if (taskForce.encounterSpawned && !taskForce.encounterResolved) {
            return taskForce.hostile ? "Active contact in this sector pocket" : "Friendly contact holding this pocket";
        }
        if (taskForce.transitRemainingSec > 0.0 && taskForce.targetSubzone >= 0 && taskForce.targetSubzone != taskForce.currentSubzone) {
            return "Moving toward " + missionSubzoneLabel(taskForce.targetSubzone);
        }
        return switch (taskForce.kind) {
            case PATROL -> taskForce.hostile ? "Hostile patrol pattern" : "Escort patrol pattern";
            case STRIKE -> "Strike group maneuvering between pockets";
            case STEALTH -> "Low-signature raiders stalking isolated assets";
            case CONVOY -> "Supply convoy shifting between safe lanes";
            case SALVAGE -> "Recovery flotilla working a debris pocket";
        };
    }

    private static CampaignSupportMarker supportMarkerFor(DiscoverySignalSite site) {
        if (site == null) return null;
        String tag = (site.kindTag == null) ? "" : site.kindTag.trim().toUpperCase(Locale.US);
        SupportMarkerType type = switch (tag) {
            case "ANOMALY" -> SupportMarkerType.ANOMALY;
            case "REINFORCEMENT", "NEUTRAL_TRADER", "PRISON_BARGE" -> SupportMarkerType.FACTION_CONTACT;
            case "SALVAGE_HULK", "WRECK_FIELD", "FLEET_ASSET" -> SupportMarkerType.SALVAGE;
            case "CACHE", "SUPPLY_CACHE", "ORE" -> SupportMarkerType.RESOURCE;
            case "MINEFIELD", "AMBUSH", "DRIFTING_TURRET" -> SupportMarkerType.HAZARD;
            case "DATA_RELAY" -> SupportMarkerType.INTEL;
            default -> null;
        };
        if (type == null) return null;
        String label = supportMarkerLabel(type, tag, site.label, site.subtitle);
        String subtitle = supportMarkerSubtitle(type, tag, site.subtitle);
        int priority = switch (type) {
            case ANOMALY, FACTION_CONTACT, INTEL -> 38;
            case SALVAGE, RESOURCE -> 32;
            case HAZARD -> 28;
        };
        return new CampaignSupportMarker(type, label, subtitle, site.x, site.y, site.radius, priority);
    }

    private static String supportMarkerLabel(SupportMarkerType type, String tag, String label, String subtitle) {
        String base = trimmedOrFallback(label, "Support Contact");
        if (type == SupportMarkerType.FACTION_CONTACT) {
            if ("NEUTRAL_TRADER".equals(tag)) return "Broker Contact - " + base;
            if ("PRISON_BARGE".equals(tag)) return "Detention Contact - " + base;
            if ("REINFORCEMENT".equals(tag)) {
                String hint = (subtitle == null) ? "" : subtitle.toLowerCase(Locale.US);
                if (hint.contains("coalition")) return "Coalition Contact - " + base;
                if (hint.contains("friendly")) return "Friendly Contact - " + base;
                return "Support Contact - " + base;
            }
            return "Faction Contact - " + base;
        }
        if (type == SupportMarkerType.INTEL) return "Intel Relay - " + base;
        if (type == SupportMarkerType.ANOMALY) return "Anomaly - " + base;
        return base;
    }

    private static String supportMarkerSubtitle(SupportMarkerType type, String tag, String subtitle) {
        if (subtitle != null && !subtitle.isBlank()) return subtitle;
        if (type == SupportMarkerType.FACTION_CONTACT) {
            return switch (tag) {
                case "NEUTRAL_TRADER" -> "Broker traffic moving under sealed lights";
                case "PRISON_BARGE" -> "Distress traffic and detainee telemetry";
                case "REINFORCEMENT" -> "Friendly or coalition support traffic";
                default -> defaultSupportMarkerSubtitle(type);
            };
        }
        return defaultSupportMarkerSubtitle(type);
    }

    private static String defaultSupportMarkerSubtitle(SupportMarkerType type) {
        if (type == null) return "Support contact";
        return switch (type) {
            case ANOMALY -> "Optional anomaly contact";
            case FACTION_CONTACT -> "Optional faction contact";
            case SALVAGE -> "Optional salvage opportunity";
            case RESOURCE -> "Optional resource cache";
            case HAZARD -> "Optional hazard contact";
            case INTEL -> "Optional intel source";
        };
    }

    public static DiscoverySignalSite nearestDiscoverySignalSite(GameContext ctx, double x, double y, double maxDist) {
        CampaignState st = state(ctx);
        if (st == null || st.discoverySites.isEmpty()) return null;
        DiscoverySite best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (DiscoverySite site : st.discoverySites) {
            if (site == null || site.discovered) continue;
            double d2 = GameMath.dist2(x, y, site.x, site.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = site;
            }
        }
        if (best == null) return null;
        return new DiscoverySignalSite(best.label, best.subtitle, best.kind == null ? "UNKNOWN" : best.kind.name(),
                best.x, best.y, best.radius);
    }

    public static CampaignObjectiveMarker nearestObjectiveMarker(GameContext ctx, double x, double y, double maxDist) {
        List<CampaignObjectiveMarker> markers = activeObjectiveMarkers(ctx);
        if (markers.isEmpty()) return null;
        CampaignObjectiveMarker best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (CampaignObjectiveMarker marker : markers) {
            if (marker == null) continue;
            double range = Math.max(maxDist, marker.radius);
            double d2 = GameMath.dist2(x, y, marker.x, marker.y);
            if (d2 > range * range) continue;
            if (best == null || d2 < bestD2 || (Math.abs(d2 - bestD2) < 1e-6 && marker.priority > best.priority)) {
                best = marker;
                bestD2 = d2;
            }
        }
        return best;
    }

    public static CampaignSupportMarker nearestSupportMarker(GameContext ctx, double x, double y, double maxDist) {
        List<CampaignSupportMarker> markers = activeSupportMarkers(ctx);
        if (markers.isEmpty()) return null;
        CampaignSupportMarker best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (CampaignSupportMarker marker : markers) {
            if (marker == null) continue;
            double range = Math.max(maxDist, marker.radius);
            double d2 = GameMath.dist2(x, y, marker.x, marker.y);
            if (d2 > range * range) continue;
            if (best == null || d2 < bestD2 || (Math.abs(d2 - bestD2) < 1e-6 && marker.priority > best.priority)) {
                best = marker;
                bestD2 = d2;
            }
        }
        return best;
    }

    public static CampaignLandmark nearestStrategicLandmark(GameContext ctx, double x, double y, double maxDist) {
        List<CampaignLandmark> markers = strategicLandmarks(ctx);
        if (markers.isEmpty()) return null;
        CampaignLandmark best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (CampaignLandmark marker : markers) {
            if (marker == null) continue;
            double range = Math.max(maxDist, marker.radius);
            double d2 = GameMath.dist2(x, y, marker.x, marker.y);
            if (d2 > range * range) continue;
            if (best == null || d2 < bestD2) {
                best = marker;
                bestD2 = d2;
            }
        }
        return best;
    }

    public static double[] reserveSectionPoint(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.missionSections.isEmpty()) return null;
        for (MissionSection section : st.missionSections) {
            if (section == null || section.label == null) continue;
            String label = section.label.toUpperCase(Locale.US);
            if (label.contains("RESERVE") || label.contains("STAGING")) {
                return new double[]{section.x, section.y};
            }
        }
        return null;
    }

    public static String reserveSectionLabel(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.missionSections.isEmpty()) return "";
        for (MissionSection section : st.missionSections) {
            if (section == null || section.label == null) continue;
            String label = section.label.toUpperCase(Locale.US);
            if (label.contains("RESERVE") || label.contains("STAGING")) {
                return section.label;
            }
        }
        return "";
    }

    static double missionSubzoneMinX(int sector, int subzoneIndex) {
        return missionSubzoneMinX(null, sector, subzoneIndex);
    }

    static double missionSubzoneMinX(GameContext ctx, int sector, int subzoneIndex) {
        MissionLayout layout = missionLayout(ctx);
        int col = missionSubzoneColumn(subzoneIndex);
        return getZoneX(sector) + col * (layout.subzoneWidth + layout.subzoneGap);
    }

    static double missionSubzoneMinY(int sector, int subzoneIndex) {
        return missionSubzoneMinY(null, sector, subzoneIndex);
    }

    static double missionSubzoneMinY(GameContext ctx, int sector, int subzoneIndex) {
        MissionLayout layout = missionLayout(ctx);
        int row = missionSubzoneRow(subzoneIndex);
        return getZoneY(sector) + row * (layout.subzoneHeight + layout.subzoneGap);
    }

    static double missionSubzoneCenterX(int sector, int subzoneIndex) {
        return missionSubzoneCenterX(null, sector, subzoneIndex);
    }

    static double missionSubzoneCenterX(GameContext ctx, int sector, int subzoneIndex) {
        return missionSubzoneMinX(ctx, sector, subzoneIndex) + missionLayout(ctx).subzoneWidth * 0.5;
    }

    static double missionSubzoneCenterY(int sector, int subzoneIndex) {
        return missionSubzoneCenterY(null, sector, subzoneIndex);
    }

    static double missionSubzoneCenterY(GameContext ctx, int sector, int subzoneIndex) {
        return missionSubzoneMinY(ctx, sector, subzoneIndex) + missionLayout(ctx).subzoneHeight * 0.5;
    }

    static int missionSubzoneForPoint(int sector, double x, double y) {
        return missionSubzoneForPoint(null, sector, x, y);
    }

    static int missionSubzoneForPoint(GameContext ctx, int sector, double x, double y) {
        MissionLayout layout = missionLayout(ctx);
        double localX = x - getZoneX(sector);
        double localY = y - getZoneY(sector);
        if (localX < 0.0 || localY < 0.0 || localX > layout.zoneWidth || localY > layout.zoneHeight) return -1;
        double strideX = layout.subzoneWidth + layout.subzoneGap;
        double strideY = layout.subzoneHeight + layout.subzoneGap;
        int col = (int) Math.floor(localX / strideX);
        int row = (int) Math.floor(localY / strideY);
        if (col < 0 || col >= MISSION_ZONE_COLUMNS || row < 0 || row >= MISSION_ZONE_ROWS) return -1;
        double withinX = localX - col * strideX;
        double withinY = localY - row * strideY;
        if (withinX < 0.0 || withinX > layout.subzoneWidth) return -1;
        if (withinY < 0.0 || withinY > layout.subzoneHeight) return -1;
        return missionSubzoneIndex(col, row);
    }

    static int nearestMissionSubzone(int sector, double x, double y) {
        return nearestMissionSubzone(null, sector, x, y);
    }

    static int nearestMissionSubzone(GameContext ctx, int sector, double x, double y) {
        double bestDist2 = Double.POSITIVE_INFINITY;
        int best = -1;
        for (int row = 0; row < MISSION_ZONE_ROWS; row++) {
            for (int col = 0; col < MISSION_ZONE_COLUMNS; col++) {
                int subzone = missionSubzoneIndex(col, row);
                double cx = missionSubzoneCenterX(ctx, sector, subzone);
                double cy = missionSubzoneCenterY(ctx, sector, subzone);
                double dx = cx - x;
                double dy = cy - y;
                double dist2 = dx * dx + dy * dy;
                if (dist2 < bestDist2) {
                    bestDist2 = dist2;
                    best = subzone;
                }
            }
        }
        return best;
    }

    static double[] clampToMissionSubzone(GameContext ctx, int sector, int subzoneIndex, double x, double y) {
        if (subzoneIndex < 0) return new double[]{x, y};
        MissionLayout layout = missionLayout(ctx);
        double minX = missionSubzoneMinX(ctx, sector, subzoneIndex) + MISSION_SUBZONE_CLAMP_MARGIN;
        double maxX = missionSubzoneMinX(ctx, sector, subzoneIndex) + layout.subzoneWidth - MISSION_SUBZONE_CLAMP_MARGIN;
        double minY = missionSubzoneMinY(ctx, sector, subzoneIndex) + MISSION_SUBZONE_CLAMP_MARGIN;
        double maxY = missionSubzoneMinY(ctx, sector, subzoneIndex) + layout.subzoneHeight - MISSION_SUBZONE_CLAMP_MARGIN;
        if (ctx != null) {
            minX = Math.max(minX, MISSION_SUBZONE_CLAMP_MARGIN);
            minY = Math.max(minY, MISSION_SUBZONE_CLAMP_MARGIN);
            maxX = Math.min(maxX, ctx.WORLD_W - MISSION_SUBZONE_CLAMP_MARGIN);
            maxY = Math.min(maxY, ctx.WORLD_H - MISSION_SUBZONE_CLAMP_MARGIN);
        }
        return new double[]{
                GameMath.clamp(x, minX, Math.max(minX, maxX)),
                GameMath.clamp(y, minY, Math.max(minY, maxY))
        };
    }

    static int currentLoadedMissionSubzone(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return -1;
        return st.loadedMissionSubzone;
    }

    static int ensureShipMissionSubzone(GameContext ctx, Ship ship) {
        CampaignState st = state(ctx);
        if (st == null || ship == null) return -1;
        if (ship == ctx.player) {
            if (st.loadedMissionSubzone >= 0) {
                ship.campaignMissionSubzone = st.loadedMissionSubzone;
                return st.loadedMissionSubzone;
            }
        }
        int resolved = ship.campaignMissionSubzone;
        if (resolved >= 0) return resolved;
        resolved = missionSubzoneForPoint(ctx, st.sector, ship.x, ship.y);
        if (resolved < 0 && ship == ctx.player) {
            resolved = nearestMissionSubzone(ctx, st.sector, ship.x, ship.y);
        }
        if (resolved < 0 && st.loadedMissionSubzone >= 0) {
            resolved = st.loadedMissionSubzone;
        }
        ship.campaignMissionSubzone = resolved;
        return resolved;
    }

    static void setLoadedMissionSubzone(GameContext ctx, int subzoneIndex) {
        CampaignState st = state(ctx);
        if (st == null) return;
        if (subzoneIndex < 0) subzoneIndex = 0;
        st.loadedMissionSubzone = subzoneIndex;
        if (ctx != null && ctx.player != null) {
            ctx.player.campaignMissionSubzone = subzoneIndex;
        }
    }

    static int syncLoadedMissionSubzoneFromPlayer(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || ctx == null || ctx.player == null) return -1;
        int subzone = missionSubzoneForPoint(ctx, st.sector, ctx.player.x, ctx.player.y);
        if (subzone < 0) subzone = nearestMissionSubzone(ctx, st.sector, ctx.player.x, ctx.player.y);
        setLoadedMissionSubzone(ctx, subzone);
        return subzone;
    }

    static int missionSubzoneForShip(GameContext ctx, Ship ship) {
        if (ship == null) return -1;
        int subzone = ship.campaignMissionSubzone;
        if (subzone >= 0) return subzone;
        CampaignState st = state(ctx);
        if (st == null) return -1;
        return missionSubzoneForPoint(ctx, st.sector, ship.x, ship.y);
    }

    static int campaignMapSubzoneAtPoint(GameContext ctx, double x, double y) {
        CampaignState st = state(ctx);
        if (st == null) return -1;
        int subzone = missionSubzoneForPoint(ctx, st.sector, x, y);
        if (subzone >= 0) return subzone;
        return nearestMissionSubzone(ctx, st.sector, x, y);
    }

    static int nextCampaignWarpHop(int sourceSubzone, int targetSubzone) {
        if (sourceSubzone < 0 || targetSubzone < 0) return -1;
        int sourceCol = missionSubzoneColumn(sourceSubzone);
        int sourceRow = missionSubzoneRow(sourceSubzone);
        int targetCol = missionSubzoneColumn(targetSubzone);
        int targetRow = missionSubzoneRow(targetSubzone);
        if (sourceCol < 0 || sourceRow < 0 || targetCol < 0 || targetRow < 0) return -1;
        int dCol = Integer.compare(targetCol, sourceCol);
        int dRow = Integer.compare(targetRow, sourceRow);
        if (dCol == 0 && dRow == 0) return sourceSubzone;
        return missionSubzoneIndex(sourceCol + dCol, sourceRow + dRow);
    }

    static double[] campaignWarpArrivalPoint(GameContext ctx, int subzoneIndex) {
        CampaignState st = state(ctx);
        if (st == null || subzoneIndex < 0) return null;
        return new double[]{
                missionSubzoneCenterX(ctx, st.sector, subzoneIndex),
                missionSubzoneCenterY(ctx, st.sector, subzoneIndex)
        };
    }

    private static int missionEntrySubzone(int sourceSector, int targetSector) {
        if (targetSector <= 0) return missionSubzoneIndex(0, 1);
        double dx = routeGridColumn(targetSector) - routeGridColumn(Math.max(1, sourceSector));
        double dy = routeGridRow(targetSector) - routeGridRow(Math.max(1, sourceSector));
        int col = MISSION_ZONE_COLUMNS / 2;
        int row = MISSION_ZONE_ROWS / 2;
        if (Math.abs(dx) > Math.abs(dy)) {
            col = (dx >= 0.0) ? 0 : (MISSION_ZONE_COLUMNS - 1);
        } else if (Math.abs(dy) > Math.abs(dx)) {
            row = (dy >= 0.0) ? 0 : (MISSION_ZONE_ROWS - 1);
        } else {
            col = (dx >= 0.0) ? 0 : (MISSION_ZONE_COLUMNS - 1);
            row = (dy >= 0.0) ? 0 : (MISSION_ZONE_ROWS - 1);
        }
        return missionSubzoneIndex(col, row);
    }

    public static int recommendedWorldWidth() {
        return recommendedWorldWidth(null);
    }

    public static int recommendedWorldWidth(GameConfig config) {
        return (int) Math.ceil(missionLayout(config).zoneWidth);
    }

    public static int recommendedWorldHeight() {
        return recommendedWorldHeight(null);
    }

    public static int recommendedWorldHeight(GameConfig config) {
        return (int) Math.ceil(missionLayout(config).zoneHeight);
    }

    private static boolean canWarpBetweenZones(int sourceSector, int targetSector) {
        if (sourceSector == targetSector) return false;
        int sourceRow = routeGridRow(sourceSector);
        int sourceCol = routeGridColumn(sourceSector);
        int targetRow = routeGridRow(targetSector);
        int targetCol = routeGridColumn(targetSector);
        int dRow = Math.abs(sourceRow - targetRow);
        int dCol = Math.abs(sourceCol - targetCol);
        return (dRow <= 1 && dCol <= 1) && (dRow + dCol > 0);
    }

    private static double[] getWarpArrivalPoint(GameContext ctx, int sourceSector, int targetSector) {
        int entrySubzone = missionEntrySubzone(sourceSector, targetSector);
        return new double[]{
                missionSubzoneCenterX(ctx, targetSector, entrySubzone),
                missionSubzoneCenterY(ctx, targetSector, entrySubzone)
        };
    }

    private static final SectorScript[] SCRIPTS = new SectorScript[]{
            null,
            new SectorScript(1, ObjectiveType.SURVIVE, "Hold the evacuation lanes until the timer ends", 200, 200, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(2, ObjectiveType.DESTROY, "Destroy 6 strike ships and keep at least 2 convoy ships alive", 6, 840, BossKind.NONE, MapModifier.NEBULA),
            new SectorScript(3, ObjectiveType.DESTROY, "Destroy the ships blocking the jump ring", 6, 720, BossKind.NONE, MapModifier.NEBULA),
            new SectorScript(4, ObjectiveType.DESTROY, "Destroy 4 relay blockers", 4, 750, BossKind.NONE, MapModifier.DEBRIS_FIELD),
            new SectorScript(5, ObjectiveType.DESTROY, "Destroy the enemy relief force", 10, 780, BossKind.NONE, MapModifier.DEBRIS_FIELD),
            new SectorScript(6, ObjectiveType.SURVIVE, "Protect the recovery caches until the timer ends", 110, 720, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(7, ObjectiveType.BOSS, "Destroy the AI pursuit Titan", 1, 780, BossKind.MID_ALPHA, MapModifier.EMP_ZONE, MapModifier.GRAVITY_SHEAR),
            new SectorScript(8, ObjectiveType.ESCORT, "Escort the Exodus Transport Titan", 95, 780, BossKind.NONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(9, ObjectiveType.SURVIVE, "Protect the defecting broker ships until the timer ends", 65, 780, BossKind.NONE, MapModifier.RICH_DEPOSITS),
            new SectorScript(10, ObjectiveType.DESTROY, "Destroy the enemy vanguard fleet", 16, 780, BossKind.NONE, MapModifier.RICH_DEPOSITS),
            new SectorScript(11, ObjectiveType.SURVIVE, "Protect the depot ships until the timer ends", 85, 780, BossKind.NONE, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(12, ObjectiveType.SURVIVE, "Protect the signatory ships until the timer ends", 95, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(13, ObjectiveType.DESTROY, "Destroy the 3 jammer towers", 3, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(14, ObjectiveType.DESTROY, "Destroy the enemy relief force", 8, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(15, ObjectiveType.DESTROY, "Destroy the outer defense guns", 4, 800, BossKind.NONE, MapModifier.GRAVITY_SHEAR, MapModifier.SOLAR_STORM),
            new SectorScript(16, ObjectiveType.BOSS, "Destroy the red Artillery Titan", 1, 840, BossKind.MID_BETA, MapModifier.GRAVITY_SHEAR, MapModifier.SOLAR_STORM),
            new SectorScript(17, ObjectiveType.DESTROY, "Destroy 6 recon ships before they escape", 6, 780, BossKind.NONE, MapModifier.NEBULA, MapModifier.SOLAR_STORM),
            new SectorScript(18, ObjectiveType.SURVIVE, "Hold the corridor until the timer ends", 240, 780, BossKind.NONE, MapModifier.NEBULA, MapModifier.SOLAR_STORM),
            new SectorScript(19, ObjectiveType.DESTROY, "Destroy the prison ships and break the clamps", 4, 840, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(20, ObjectiveType.ESCORT, "Escort the recovery Titan", 100, 840, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(21, ObjectiveType.DESTROY, "Destroy the 3 orbital defense anchors", 3, 840, BossKind.NONE, MapModifier.EMP_ZONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(22, ObjectiveType.DESTROY, "Destroy the enemy reserve cordon", 10, 840, BossKind.NONE, MapModifier.EMP_ZONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(23, ObjectiveType.DESTROY, "Destroy the 4 uplink towers and protect the launch ships", 4, 900, BossKind.NONE, MapModifier.SOLAR_STORM, MapModifier.GRAVITY_SHEAR),
            new SectorScript(24, ObjectiveType.FINAL_BOSS, "Destroy the AI Mothership over Earth", 1, 900, BossKind.FINAL, MapModifier.SOLAR_STORM, MapModifier.GRAVITY_SHEAR)
    };

    private static final SideObjectiveScript[] SIDE_SCRIPTS = new SideObjectiveScript[]{
            null,
            new SideObjectiveScript(1, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Take no hull damage for 120s", 120, 160),
            new SideObjectiveScript(2, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 660s", 660, 180),
            new SideObjectiveScript(3, SideObjectiveType.KILL_COUNT, "Destroy 8 enemy ships", 8, 220),
            new SideObjectiveScript(4, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 600s", 600, 240),
            new SideObjectiveScript(5, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 620s", 620, 260),
            new SideObjectiveScript(6, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 540s", 540, 220),
            new SideObjectiveScript(7, SideObjectiveType.CLEAR_BEFORE_TIME, "Kill the boss in 600s", 600, 240),
            new SideObjectiveScript(8, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the escort undamaged for 90s", 90, 210),
            new SideObjectiveScript(9, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep 3 defectors alive for 90s", 90, 240),
            new SideObjectiveScript(10, SideObjectiveType.KILL_COUNT, "Destroy 10 escorts", 10, 230),
            new SideObjectiveScript(11, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 560s", 560, 240),
            new SideObjectiveScript(12, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the lead ship undamaged for 80s", 80, 260),
            new SideObjectiveScript(13, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 600s", 600, 250),
            new SideObjectiveScript(14, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 620s", 620, 280),
            new SideObjectiveScript(15, SideObjectiveType.KILL_COUNT, "Destroy 6 escorts", 6, 260),
            new SideObjectiveScript(16, SideObjectiveType.KILL_COUNT, "Destroy 6 escorts", 6, 280),
            new SideObjectiveScript(17, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 560s", 560, 260),
            new SideObjectiveScript(18, SideObjectiveType.KILL_COUNT, "Destroy 14 attackers during the hold", 14, 300),
            new SideObjectiveScript(19, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the recovery Titan undamaged for 90s", 90, 320),
            new SideObjectiveScript(20, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the escort undamaged for 100s", 100, 320),
            new SideObjectiveScript(21, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 620s", 620, 350),
            new SideObjectiveScript(22, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 620s", 620, 360),
            new SideObjectiveScript(23, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 660s", 660, 380),
            new SideObjectiveScript(24, SideObjectiveType.CLEAR_BEFORE_TIME, "Finish the mission in 720s", 720, 400)
    };

    private static final SectorLore[] LORE = new SectorLore[]{
            null,
            new SectorLore(1, "ANCHORAGE FIRESTORM", "Far Trade Anchorage",
                    "Earth has fallen. Hold the evacuation lanes while Far Trade's arcology crowns, exchange ring, and refugee docks burn around the harbor approaches.",
                    "The trade colony is gutted, but the convoy escapes with civilians, treasury ledgers, and a road home."),
            new SectorLore(2, "CUSTOMS HALO COLLAPSE", "Outer Colony Jump Ring Approach",
                    "Destroy all 6 customs-halo strike ships across the marked pockets while keeping at least 2 convoy hulls alive before the aperture closes and traps the convoy outside the ring.",
                    "The halo screen cracks, frightened civilian traffic slips through, and the jump approach stays open."),
            new SectorLore(3, "BREAKOUT VECTOR", "Outer Colony Jump Ring",
                    "Break the red interdiction cordon at the aperture itself before the outer-colony jump ring collapses into a kill box.",
                    "The first true blockade is broken and the return route opens into deep space."),
            new SectorLore(4, "LAST AUTHORITY RELAY", "Gate Relay Tethys",
                    "Destroy the route-control blockers pinning the last intact authority relay so the fleet can restore a lawful Earthward vector.",
                    "Route control breaks open, the transit net answers, and the homeward route becomes real."),
            new SectorLore(5, "RELAY RELIEF BREAK", "Tethys Relay Hinterlane",
                    "Destroy the reserve wing racing in behind Tethys before it can shut the Earth vector a second time.",
                    "The relief wing is shattered and the relay corridor stays open behind the fleet."),
            new SectorLore(6, "DEBRIS WAKE RECOVERY", "Burning Debris Wake",
                    "Recover state archives, fuel caskets, and convoy stragglers from the burning debris wake before demolition ships erase the evidence of survival.",
                    "The fleet pulls people, fuel, and legitimacy out of the fire before the wreck field goes dark."),
            new SectorLore(7, "RED KNIFE PURSUIT", "Shattered Traffic Lanes",
                    "A pursuit Titan is closing through the wreck wake of the breakout. Kill it before it catches the refugee column in the shattered lanes behind you.",
                    "The AI's first Titan hunter is down and the convoy punches out of the kill box."),
            new SectorLore(8, "REFUGEE WAYLINE", "Civilian Exodus Corridor",
                    "Keep the Exodus Transport Titan tight under the Mothership's screen through a short breakout window while hunter packs claw at the convoy flanks.",
                    "The civilian column survives the breakout and the fleet keeps its people, records, and legitimacy with it."),
            new SectorLore(9, "NEUTRAL TRADE SPINE", "Broker Yards And Slipway Habitats",
                    "Cover neutral broker hulls and defecting yard ships as they cross from the trade spine into coalition protection.",
                    "Neutral survivors choose the road home and the fleet grows because people still believe in it."),
            new SectorLore(10, "BROKEN ARMISTICE", "Trade Spine Defense Belt",
                    "Break the AI vanguard around the trade spine and keep the homeward lane open through the broker yards and bonded depots.",
                    "The vanguard breaks and the trade spine defects behind the Blue fleet."),
            new SectorLore(11, "LEDGER AND LOX", "Bonded Depot Shelf",
                    "Secure the bonded ledgers, refit stores, and fuel depots that keep the road home alive before demolition teams can burn them out.",
                    "The fleet keeps its fuel, books, and repair stores, and the journey home stays logistically possible."),
            new SectorLore(12, "SIGNATORY RUN", "Coalition Service Halos",
                    "Keep green courier and command hulls alive long enough to formalize the first coalition commitment under fire.",
                    "The pact is signed in motion and the green houses commit ships to the road home."),
            new SectorLore(13, "GREEN CONTRACT FRONT", "Coalition Array Nysa",
                    "Destroy the jammer triad around Coalition Array Nysa and let the green houses hear the coalition call again.",
                    "Nysa comes back online and the green contract front swings toward the fleet."),
            new SectorLore(14, "NYSA RELIEF BREAK", "Contract Array Rear Orbit",
                    "Destroy the reserve wing and command ship trying to re-isolate Nysa before the contract world is cut off again.",
                    "The counterattack breaks and the green alliance holds under pressure."),
            new SectorLore(15, "KHARON OUTER SCREEN", "Siege Gate Kharon",
                    "Silence Kharon's spotter towers, anchor guns, and siege beacons before the artillery Titan can fully range the lane.",
                    "The outer screen collapses and the gate's targeting spine starts to fail."),
            new SectorLore(16, "ASHEN GATE", "Siege Gate Furnace",
                    "A red Artillery Titan has turned Siege Gate Kharon into a furnace of burning transit steel. Silence it and reopen the Solward lane.",
                    "The siege gate is broken, its guns go dark, and the fleet can press into Sol."),
            new SectorLore(17, "OUTER SOL PROBE WAR", "Outer Sol Defense Fringe",
                    "Destroy recon groups and marker ships before they can vector the entire Sol defense ring onto the coalition corridor.",
                    "The probe war is won and the coalition stays hidden just long enough to form the final line."),
            new SectorLore(18, "OUTER SOL HOLD", "Coalition Assembly Ring",
                    "Hold the arrival corridor while scattered coalition task groups, tugs, and hospital ships finish assembling for the final push.",
                    "The line holds, Sol is in reach, and the coalition arrives intact enough to matter."),
            new SectorLore(19, "YELLOW BREAKCHAIN", "Liberation Corridor",
                    "Destroy prison tenders, tractor nodes, and clamp escorts holding liberated yellow crews inside the breakchain.",
                    "The prison chain breaks and liberated yellow crews begin to fall back under fleet protection."),
            new SectorLore(20, "YELLOW REJOIN", "Breakchain Debris Run",
                    "Keep the liberated recovery Titan close behind the Mothership while the fleet cuts through the breakchain debris run.",
                    "Yellow survivors rejoin the fleet in force and the liberation war becomes real."),
            new SectorLore(21, "LUNA ANCHOR SWEEP", "Luna Perimeter",
                    "Destroy the orbital defense anchors, foundry guns, and mass-driver batteries screening the lunar perimeter.",
                    "Luna's anchor grid goes dark and the Earth lane starts to crack open."),
            new SectorLore(22, "LUNA CORDON BREAK", "Earth Approach Lane",
                    "Break the reserve cordon around the Earth approach and force open the last lane to home.",
                    "The lunar cordon shatters and Earth finally lies ahead."),
            new SectorLore(23, "EARTHRISE INSURRECTION", "Earth Lift Terminus Belt",
                    "Destroy occupation uplink towers and keep resistance launches alive long enough to blind the AI over Earth.",
                    "Earth's resistance rises into orbit and the occupation finally starts to lose its grip."),
            new SectorLore(24, "HOMEWORLD LIBERATION", "Earth High Orbit",
                    "Destroy the AI Mothership over Earth's night-side city webs, orbital lift termini, and burning defense lattice, and end the occupation.",
                    "The AI is broken, Earth's orbit is reclaimed, and the long road home is finally over.")
    };

    public static final class CampaignState {
        public static final class CampaignOreLedger {
            public int storedOre = 0;

            public void normalize() {
                storedOre = Math.max(0, storedOre);
            }
        }

        public boolean enabled;
        public int sector = 1;
        public final int totalSectors = 24;
        public int act = 1;
        public final CampaignOreLedger oreLedger = new CampaignOreLedger();

        public ObjectiveType objectiveType = ObjectiveType.SURVIVE;
        public String objectiveLabel = "";
        public double objectiveProgress = 0.0;
        public double objectiveGoal = 1.0;

        public double sectorElapsed = 0.0;
        public double sectorTimeLimit = 600.0; // 10 minutes per sector target pacing
        public boolean objectiveSecured = false;
        public double extractionMinHoldSeconds = 200.0;

        public int kills = 0;
        public final Set<Integer> knownHostiles = new HashSet<>();
        public int bossTargetId = -1;

        public Ship escortShip = null;
        public double captureX = 0.0;
        public double captureY = 0.0;
        public double captureRadius = 180.0;
        public boolean captureArmed = false;

        public final Set<Integer> authoredObjectiveHostiles = new HashSet<>();
        public int authoredObjectiveKills = 0;
        public int lastAnnouncedAuthoredObjectiveKills = 0;
        public int authoredWaveCursor = 0;
        public int loadedMissionSubzone = missionSubzoneIndex(0, 1);
        public final List<CampaignLandmark> landmarks = new ArrayList<>();
        public final List<MissionSection> missionSections = new ArrayList<>();
        public int activeMissionSection = 0;
        public boolean missionSectionTravelLocked = false;
        public MissionTheme missionTheme = MissionTheme.BREAKTHROUGH;
        public final List<DiscoverySite> discoverySites = new ArrayList<>();
        public int discoveriesFound = 0;
        public final List<RecoverableWreckSite> recoverableWreckSites = new ArrayList<>();
        public int recoverableWrecksClaimed = 0;
        public String objectivePhaseLabel = "";
        public String threatStateLabel = "";
        public int objectiveStage = 0;
        public int mapPressureStage = 0;
        public int objectiveKillBaseline = 0;
        public final Set<Integer> objectiveAssetIds = new HashSet<>();
        public int objectiveAssetTotal = 0;
        public int objectiveAssetLosses = 0;
        public int lastAnnouncedObjectiveAssetLosses = 0;
        public String objectiveAssetLabel = "";
        public int objectiveAssetRequiredSurvivors = 0;
        public String objectiveAssetFailureText = "";

        public double transitionTimer = 0.0;
        public String transitionLabel = "";
        public long sectorStartMillis = 0L;
        public String transitionSummaryTop = "";
        public String transitionSummaryBottom = "";
        public double missionIntroTimer = 0.0;
        public boolean missionStartBanterPlayed = false;
        public int extractionWarningStage = 0;

        public BossKind bossKind = BossKind.NONE;
        public boolean bossPhaseOneTriggered = false;
        public boolean bossPhaseTwoTriggered = false;
        public boolean enemyBaseWinConditionActive = false;

        public MapModifier[] activeModifiers = new MapModifier[]{MapModifier.NONE};
        public double targetingRangeMul = 1.0;
        public double miningRateMul = 1.0;
        public double enemyWaveDelayMul = 1.0;
        public double enemyWaveGroupMul = 1.0;
        public double oreCreditMul = 1.0;
        public double sectorCreditBonusMul = 1.0;
        public boolean disableAutoLock = false;

        public SideObjectiveType sideObjectiveType = SideObjectiveType.NONE;
        public String sideObjectiveLabel = "";
        public double sideObjectiveProgress = 0.0;
        public double sideObjectiveGoal = 0.0;
        public int sideObjectiveRewardCredits = 0;
        public int sideObjectiveProtectedShipId = -1;
        public int sideObjectiveProtectedShipStartHp = 0;
        public boolean sideObjectiveCompleted = false;
        public boolean sideObjectiveFailed = false;
        public double escortFormationIntegrity = 0.0;
        public int sideObjectiveBaseKills = 0;
        public int sideObjectiveStartPlayerHp = 0;
        public int sideObjectivesCompletedTotal = 0;
        public int sideObjectivesFailedTotal = 0;
        public int sectorsCleared = 0;
        public int campaignKills = 0;
        public int branchScore = 0;
        public String branchRoute = "BALANCED";
        public final List<TitanArchetype> ownedTitans = new ArrayList<>();
        public final List<PersistentFleetEntry> persistentBlueFleet = new ArrayList<>();
        public int nextPersistentFleetSlotId = 1;
        public int escortCapUpgradeLevel = 0;
        public int lineCapUpgradeLevel = 0;
        public int capitalCapUpgradeLevel = 0;
        public boolean awaitingEpisodeLaunch = false;
        public int pendingEpisodeSector = 0;
        public int routeArrivalSourceSector = 0;
        public final List<CampaignRouteChoice> routeChoices = new ArrayList<>();
        public int selectedRouteChoice = 0;
        public boolean introSequenceActive = false;
        public int introPhase = 0;
        public double introTimer = 0.0;
        public double introWarpX = Double.NaN;
        public double introWarpY = Double.NaN;
        public double cinematicFocusX = Double.NaN;
        public double cinematicFocusY = Double.NaN;
        public boolean campaignBlueGreenAlliance = true;
        public boolean campaignBlueYellowAlliance = false;
        public boolean greenContractFleetJoined = false;
        public boolean yellowLiberationFleetJoined = false;
        public int greenContractFavor = 0;
        public int yellowLiberationFavor = 0;

        // Fleet hub choice timeout: When mission completes, let player choose to open fleet tab or wait for auto-open
        public boolean awaitingFleetHubChoice = false;
        public double fleetHubChoiceTimer = 0.0;
        public double persistentFleetHeading = Double.NaN;

        public boolean unlockAuxGunGranted = false;
        public int unlockMissileTierGranted = 0;
        public boolean unlockCiwsGranted = false;
        public boolean unlockHullGranted = false;

        public boolean bossDropAegisArray = false;
        public boolean bossDropMissileCore = false;
        public boolean bossDropFlagCore = false;
        public int bossDropsCollected = 0;
        public boolean strategicOvermapMode = true;
        public final List<CampaignLocation> galaxyMainPois = new ArrayList<>();
        public final List<CampaignLocation> galaxyAreasOfInterest = new ArrayList<>();
        public final CampaignTravelState galaxyTravel = new CampaignTravelState();
        public String currentGalaxyLocationId = "";
        public String selectedGalaxyLocationId = "";
        public String activeGalaxyEncounterLocationId = "";
        public int completedMainMissions = 0;
        public double earthProgress = 0.0;
        public double enemyAlertLevel = 0.0;
        public boolean galaxyEncounterActive = false;
        public int campaignFuel = 120;
        public int campaignSupplies = 90;
        public int campaignAmmo = 110;
        public int campaignSalvage = 35;
        public final List<StrategicTaskForce> strategicTaskForces = new ArrayList<>();
        public int nextStrategicTaskForceId = 1;
        public int strategicTorpedoCharges = 2;
        public int strategicSortiesLaunched = 0;
        public int strategicAtomicCharges = 0;
        public final java.util.Map<Integer, StrategicDivisionState> strategicDivisions = new java.util.LinkedHashMap<>();
    }

    public enum ObjectiveType {
        DESTROY,
        SURVIVE,
        ESCORT,
        CAPTURE,
        BOSS,
        FINAL_BOSS
    }

    public enum SideObjectiveType {
        NONE,
        KILL_COUNT,
        NO_HULL_DAMAGE_WINDOW,
        CLEAR_BEFORE_TIME
    }

    private enum BranchOutcome {
        STANDARD("EARTH LIBERATED", "VICTORY: EARTH LIBERATED"),
        STRATEGIC_SUPREMACY("ALT ENDING: DECISIVE LIBERATION", "VICTORY: AI FLEET SHATTERED"),
        TRUE_RESTORATION("TRUE ENDING: HOMEWORLD RESTORED", "VICTORY: TRUE RESTORATION"),
        PYRRHIC("ALT ENDING: EARTH LIBERATED AT GREAT COST", "VICTORY: PYRRHIC LIBERATION");

        final String gameOverText;
        final String bannerText;

        BranchOutcome(String gameOverText, String bannerText) {
            this.gameOverText = gameOverText;
            this.bannerText = bannerText;
        }
    }

    public static void init(GameContext ctx) {
        if (ctx == null || ctx.config == null) return;
        if (ctx.config.mode != GameMode.CAMPAIGN_OPS && ctx.config.mode != GameMode.FLEET) return;

        CampaignState st = new CampaignState();
        st.enabled = true;
        ctx.campaign = st;
        configureCampaignSession(ctx, st);
        CampaignCheckpointStore.Checkpoint checkpoint = ctx.config.resumeCampaign ? CampaignCheckpointStore.load() : null;
        if (checkpoint != null && checkpoint.isUsable() && applyCheckpoint(ctx, st, checkpoint)) {
            configureCampaignSession(ctx, st);
            if (ctx.config.mode == GameMode.FLEET) {
                enterFleetHub(ctx, st);
                FogOfWarSystem.update(ctx);
            } else {
                startSector(ctx, checkpoint.nextSector);
                if (isStrategicOvermapMode(st)) {
                    enterStrategicOvermap(ctx, st, "CAMPAIGN RESUMED: " + loreFor(checkpoint.nextSector).title);
                } else {
                    EventSystem.showBanner(ctx, "CAMPAIGN RESUMED: " + loreFor(checkpoint.nextSector).title, 2.2);
                }
            }
            return;
        }

        CampaignCheckpointStore.clear();
        ctx.credits = CAMPAIGN_STARTING_CREDITS;
        setCampaignOre(ctx, st, 0);
        applyPersistedUnlockProfile(ctx, st);
        seedStartingBlueFleet(st);
        initializeGalaxyCampaignMap(ctx, st);
        applyStartupPreset(ctx, st);
        persistRunStart(ctx);

        if (ctx.config.mode == GameMode.FLEET) {
            enterFleetHub(ctx, st);
        } else {
            startSector(ctx, 1);
            if (isStrategicOvermapMode(st)) {
                enterStrategicOvermap(ctx, st, "CAMPAIGN START: ACT I - " + actTitleFor(1));
            } else {
                EventSystem.showBanner(ctx, "CAMPAIGN START: ACT I - " + actTitleFor(1), 2.2);
            }
        }
    }

    private static void initializeGalaxyCampaignMap(GameContext ctx, CampaignState st) {
        if (st == null || !st.galaxyMainPois.isEmpty()) return;
        String[] names = {
                "Green Anchorage Pelagos",
                "Yellow Exchange Ilex",
                "Frontier Shipyard Carina",
                "Broker Relay Morrow",
                "Green Repair Port Hecate",
                "Yellow Commerce Spine Oris",
                "Dustline Listening Bastion",
                "Red Corridor Breakpoint",
                "Green Drydock Vesta",
                "Yellow Logistics Harbor Nysa",
                "Refinery Port Ashkel",
                "Coalition Relay Kharon",
                "Contract Shipworks Myr",
                "Yellow Escort Haven Oriel",
                "Frontier Arsenal Kharon Gate",
                "Green Stronghold Thessa",
                "Breakchain Recovery Ring",
                "Resistance Foundry Aster",
                "Luna Trade Anchorage",
                "Earthlane Forward Bastion",
                "Luna Perimeter Shipyard",
                "Inner Defense Relay Crown",
                "Earthrise Resistance Port",
                "Earth High Orbit"
        };
        String[] details = {
                "Green Team base  |  southern anchorage and coalition launch point.",
                "Yellow Team commerce hub  |  early trade and salvage exchange.",
                "Shipyard  |  first major build yard on the road north.",
                "Relay hub  |  long-range traffic and contract routing.",
                "Repair outpost  |  safer refit stop before the frontier.",
                "Yellow Team market port  |  resupply and carrier logistics.",
                "Listening bastion  |  intel-rich choke point on the northern climb.",
                "Hostile breakpoint  |  first hard gate on the route home.",
                "Green Team drydock  |  escorts, repairs, and fleet reshaping.",
                "Yellow Team logistics harbor  |  convoy economy under pressure.",
                "Industrial fuel port  |  ore refining and missile stores.",
                "Coalition relay base  |  navigational handoff deeper north.",
                "Contract shipworks  |  hard-choice detour for stronger hulls.",
                "Yellow Team haven  |  recovering crews and civilian lanes.",
                "Forward arsenal  |  fortified line before the inner war zone.",
                "Green Team stronghold  |  last dependable southern ally.",
                "Recovery ring  |  graveyard route with high-value detours.",
                "Resistance foundry  |  improvised build and repair capability.",
                "Luna trade anchorage  |  commerce under extreme search pressure.",
                "Earthlane bastion  |  strategic bottleneck before the final climb.",
                "Luna perimeter shipyard  |  late-game repair and heavy construction.",
                "Inner defense relay  |  command traffic near Earth.",
                "Resistance port  |  uprising staging base over Earth.",
                "Home approach  |  final objective and return to Earth."
        };
        double[][] poiNorm = {
                {0.42, 0.92}, {0.58, 0.88}, {0.30, 0.85}, {0.67, 0.82},
                {0.20, 0.78}, {0.50, 0.75}, {0.75, 0.72}, {0.39, 0.69},
                {0.14, 0.64}, {0.61, 0.62}, {0.79, 0.58}, {0.31, 0.55},
                {0.54, 0.50}, {0.22, 0.47}, {0.72, 0.43}, {0.44, 0.39},
                {0.16, 0.34}, {0.60, 0.30}, {0.82, 0.25}, {0.36, 0.21},
                {0.68, 0.16}, {0.24, 0.12}, {0.52, 0.08}, {0.50, 0.03}
        };
        for (int sector = 1; sector <= 24; sector++) {
            SectorLore lore = loreFor(sector);
            double[] point = campaignWorldPoint(ctx, poiNorm[sector - 1][0], poiNorm[sector - 1][1]);
            CampaignLocation poi = new CampaignLocation(
                    String.format(Locale.US, "poi-%02d", sector),
                    names[sector - 1],
                    point[0],
                    point[1],
                    CampaignLocationType.MAIN_MISSION,
                    0.16f + (sector - 1) / 28.0f,
                    true,
                    sector,
                    details[sector - 1] + "  |  Mission: " + lore.title,
                    hubServicesForMainPoi(sector));
            st.galaxyMainPois.add(poi);
        }
        addGalaxyArea(st, campaignArea(ctx, "aoi-cache-1", "Ghost Cache", 0.10, 0.90,
                CampaignLocationType.HIDDEN_CACHE, 0.18f, "Hidden supply caskets tucked off the southern trade drift."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-salvage-1", "Pelagos Wreck Garden", 0.72, 0.86,
                CampaignLocationType.SALVAGE_FIELD, 0.28f, "Wreck clusters scattered east of the first green lanes."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-distress-1", "Broker Distress Pulse", 0.82, 0.79,
                CampaignLocationType.DISTRESS_SIGNAL, 0.34f, "Civilian distress traffic drifting north of the market route."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-resource-1", "Ore Drift Delta", 0.12, 0.72,
                CampaignLocationType.RESOURCE_ZONE, 0.26f, "Mineral-rich detour west of the opening corridor."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-threat-1", "Knife Sweep Arc", 0.88, 0.66,
                CampaignLocationType.ENEMY_ACTIVITY, 0.54f, "Hostile patrol arc watching the eastern climb."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-repair-1", "Contract Repair Anchorage", 0.08, 0.57,
                CampaignLocationType.REPAIR_SITE, 0.12f, "Bonded repair slips hidden in a western pocket."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-story-1", "Silent Chapel Relay", 0.86, 0.49,
                CampaignLocationType.STORY_EVENT, 0.42f, "Encrypted relay tower broadcasting coalition ghost traffic."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-threat-2", "Luna Search Net", 0.90, 0.20,
                CampaignLocationType.ENEMY_ACTIVITY, 0.72f, "Heavy enemy sweep pressure approaching the lunar defenses."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-cache-2", "Resistance Dead Drop", 0.11, 0.26,
                CampaignLocationType.HIDDEN_CACHE, 0.36f, "Buried resistance stores under the western shadow route."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-salvage-2", "Breakchain Graveyard", 0.71, 0.36,
                CampaignLocationType.SALVAGE_FIELD, 0.46f, "Burned convoy hulks and stripped escort frames."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-resource-2", "Fuel Vein Kappa", 0.28, 0.18,
                CampaignLocationType.RESOURCE_ZONE, 0.40f, "Deep fuel pockets under intermittent northern raids."));
        addGalaxyArea(st, campaignArea(ctx, "aoi-distress-2", "Resistance SOS", 0.42, 0.11,
                CampaignLocationType.DISTRESS_SIGNAL, 0.66f, "High-risk resistance traffic close to Earth."));

        st.currentGalaxyLocationId = st.galaxyMainPois.get(0).id;
        st.selectedGalaxyLocationId = st.galaxyMainPois.size() > 1 ? st.galaxyMainPois.get(1).id : st.currentGalaxyLocationId;
        st.completedMainMissions = 0;
        st.earthProgress = 0.0;
        st.enemyAlertLevel = 0.0;
    }

    private static CampaignLocation campaignArea(GameContext ctx, String id, String name, double nx, double ny,
                                                 CampaignLocationType type, float threat, String detail) {
        double[] point = campaignWorldPoint(ctx, nx, ny);
        return new CampaignLocation(id, name, point[0], point[1], type, threat, false, 0, detail);
    }

    private static HubService[] hubServicesForMainPoi(int sector) {
        return switch (sector) {
            case 1, 5, 9, 16 -> new HubService[]{HubService.REPAIR, HubService.SUPPLY, HubService.INTEL, HubService.CONTRACTS};
            case 2, 6, 10, 14, 19 -> new HubService[]{HubService.TRADE, HubService.SALVAGE, HubService.FUEL, HubService.SUPPLY, HubService.CONTRACTS};
            case 3, 13, 21 -> new HubService[]{HubService.SHIPYARD, HubService.REFIT, HubService.SUPPLY};
            case 4, 12, 22 -> new HubService[]{HubService.INTEL, HubService.CONTRACTS, HubService.SUPPLY};
            case 8, 18, 23 -> new HubService[]{HubService.REPAIR, HubService.TRADE, HubService.INTEL};
            case 11, 15, 17, 20 -> new HubService[]{HubService.SHIPYARD, HubService.REPAIR, HubService.SUPPLY, HubService.INTEL};
            case 24 -> new HubService[]{HubService.INTEL};
            default -> new HubService[]{HubService.SUPPLY};
        };
    }

    private static double[] campaignWorldPoint(GameContext ctx, double nx, double ny) {
        double worldW = (ctx == null) ? 20000.0 : ctx.WORLD_W;
        double worldH = (ctx == null) ? 20000.0 : ctx.WORLD_H;
        double marginX = worldW * 0.08;
        double marginY = worldH * 0.04;
        double x = marginX + MathUtil.clamp(nx, 0.0, 1.0) * Math.max(1.0, worldW - marginX * 2.0);
        double y = marginY + MathUtil.clamp(ny, 0.0, 1.0) * Math.max(1.0, worldH - marginY * 2.0);
        return new double[]{x, y};
    }

    private static void applyStartupPreset(GameContext ctx, CampaignState st) {
        if (ctx == null || ctx.config == null || st == null) return;
        if ("galaxy_map_test".equalsIgnoreCase(ctx.config.startupPreset)) {
            configureGalaxyMapTestScenario(ctx, st);
        }
    }

    private static void configureGalaxyMapTestScenario(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        ctx.credits = Math.max(ctx.credits, GameContext.scaleCreditEarnings(4200));
        setCampaignOre(ctx, st, 280);
        st.strategicTorpedoCharges = 4;
        st.strategicAtomicCharges = 1;
        st.enemyAlertLevel = 18.0;
        st.branchScore = 4;
        st.branchRoute = branchRouteLabel(st.branchScore);

        ensureGalaxyTestFleetEntry(st, ShipRole.LIGHT_CRUISER, "Blue Test Spear");
        ensureGalaxyTestFleetEntry(st, ShipRole.CARRIER, "Blue Test Carrier");
        ensureGalaxyTestFleetEntry(st, ShipRole.FRIGATE, "Blue Test Guard");
        ensureGalaxyTestFleetEntry(st, ShipRole.CIWS_CORVETTE, "Blue Test Screen");
        ensureGalaxyTestFleetEntry(st, ShipRole.STEALTH_SHIP, "Blue Test Ghost");

        int cleared = 0;
        for (CampaignLocation poi : st.galaxyMainPois) {
            if (poi == null || !poi.primaryMission) continue;
            if (poi.missionIndex <= 4) {
                poi.completed = true;
                poi.consumed = true;
                cleared++;
            } else {
                poi.completed = false;
                poi.consumed = false;
            }
        }
        st.completedMainMissions = cleared;
        st.earthProgress = MathUtil.clamp(cleared / 24.0, 0.0, 1.0);
        st.currentGalaxyLocationId = "poi-05";
        st.selectedGalaxyLocationId = "poi-06";
        st.sector = 5;

        for (CampaignLocation aoi : st.galaxyAreasOfInterest) {
            if (aoi == null) continue;
            aoi.discovered = true;
            if ("aoi-cache-1".equalsIgnoreCase(aoi.id) || "aoi-repair-1".equalsIgnoreCase(aoi.id)) {
                aoi.consumed = false;
            }
        }
    }

    private static void ensureGalaxyTestFleetEntry(CampaignState st, ShipRole role, String name) {
        if (st == null || role == null) return;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (entry.role == role) return;
        }
        addPersistentFleetEntry(st, role, name);
    }

    private static HubService hubServiceById(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) return null;
        try {
            return HubService.valueOf(serviceId.trim().toUpperCase(Locale.US));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean performHubService(GameContext ctx, CampaignState st, CampaignLocation location, HubService service) {
        if (ctx == null || st == null || location == null || service == null) return false;
        switch (service) {
            case REPAIR -> {
                int cost = GameContext.scaleCreditEarnings(140);
                if (ctx.credits < cost) {
                    EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS FOR REPAIR", 1.4);
                    return false;
                }
                ctx.credits -= cost;
                if (ctx.player != null) {
                    ctx.player.fullyRepairHull();
                    if (ctx.player.shieldMax > 0.0) ctx.player.shield = ctx.player.shieldMax;
                }
                EventSystem.showBanner(ctx, "FLEET REPAIRED  -" + cost + " CREDITS", 1.6);
                return true;
            }
            case TRADE -> {
                int sale = st.campaignSalvage * 6;
                ctx.credits += sale;
                st.campaignSalvage = 0;
                st.campaignFuel += 10;
                st.campaignSupplies += 8;
                EventSystem.showBanner(ctx, "MARKET TRADE COMPLETE  +" + sale + " CREDITS", 1.6);
                return true;
            }
            case SHIPYARD -> {
                int cost = GameContext.scaleCreditEarnings(320);
                if (ctx.credits < cost) {
                    EventSystem.showBanner(ctx, "SHIPYARD ORDER REQUIRES " + cost + " CREDITS", 1.4);
                    return false;
                }
                ctx.credits -= cost;
                addPersistentFleetEntry(st, location.name.toUpperCase(Locale.US).contains("YELLOW")
                        ? ShipRole.TRANSPORT
                        : ShipRole.FRIGATE,
                        location.name + " Build");
                EventSystem.showBanner(ctx, "SHIPYARD ORDER ADDED TO FLEET", 1.7);
                return true;
            }
            case SUPPLY -> {
                st.campaignSupplies += 20;
                st.campaignAmmo += 18;
                ctx.credits = Math.max(0, ctx.credits - GameContext.scaleCreditEarnings(90));
                EventSystem.showBanner(ctx, "SUPPLIES PURCHASED", 1.5);
                return true;
            }
            case REFIT -> {
                EventSystem.showBanner(ctx, "REFIT DOCKET LOGGED - PLACEHOLDER PASS", 1.4);
                return true;
            }
            case INTEL -> {
                st.enemyAlertLevel = Math.max(0.0, st.enemyAlertLevel - 4.0);
                EventSystem.showBanner(ctx, "INTEL ACQUIRED - LOCAL THREAT PICTURE UPDATED", 1.5);
                return true;
            }
            case CONTRACTS -> {
                ctx.credits += GameContext.scaleCreditEarnings(110);
                EventSystem.showBanner(ctx, "CONTRACT ADVANCE RECEIVED", 1.5);
                return true;
            }
            case SALVAGE -> {
                int payout = st.campaignSalvage * 8;
                ctx.credits += payout;
                st.campaignSalvage = 0;
                EventSystem.showBanner(ctx, "SALVAGE SOLD  +" + payout + " CREDITS", 1.5);
                return true;
            }
            case FUEL -> {
                st.campaignFuel += 24;
                ctx.credits = Math.max(0, ctx.credits - GameContext.scaleCreditEarnings(70));
                EventSystem.showBanner(ctx, "FUEL STORES TOPPED UP", 1.5);
                return true;
            }
        }
        return false;
    }

    private static void addGalaxyArea(CampaignState st, CampaignLocation location) {
        if (st == null || location == null) return;
        st.galaxyAreasOfInterest.add(location);
    }

    public static void update(GameContext ctx, double dt) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || ctx.gameOver) return;
        if (st.galaxyMainPois.isEmpty()) {
            initializeGalaxyCampaignMap(ctx, st);
        }

        if (ctx.player == null || !ctx.player.alive || ctx.player.hp <= 0) {
            failRun(ctx, flagshipFailureText(st), "Campaign command ship destroyed");
            return;
        }

        refreshCampaignAlliances(st);
        st.missionIntroTimer = Math.max(0.0, st.missionIntroTimer - Math.max(0.0, dt));
        consolidateCampaignOreLedger(ctx, st, false);

        // Handle fleet hub choice timeout: auto-open after ~10 seconds
        if (st.awaitingFleetHubChoice) {
            st.fleetHubChoiceTimer -= dt;
            if (st.fleetHubChoiceTimer <= 0) {
                enterFleetHub(ctx, st);
                st.awaitingFleetHubChoice = false;
                st.fleetHubChoiceTimer = 0.0;
            }
            syncPersistentFleetCasualties(ctx, st);
            return;
        }

        if (st.awaitingEpisodeLaunch) {
            syncPersistentFleetCasualties(ctx, st);
            return;
        }

        if (st.transitionTimer > 0) {
            st.transitionTimer -= dt;
            if (st.transitionTimer <= 0) {
                int next = st.sector + 1;
                if (next > st.totalSectors) {
                    ctx.gameOver = true;
                    ctx.state = GameState.GAME_OVER;
                    CampaignCheckpointStore.clear();
                    finalizeCampaignOutcome(ctx, st);
                    persistRunResult(ctx, true);
                    return;
                }
                startSector(ctx, next);
            }
            return;
        }

        if (st.introSequenceActive) {
            updateSectorOneIntro(ctx, st, dt);
            return;
        }

        if (isStrategicOvermapMode(st)) {
            updateStrategicOvermapCampaign(ctx, st, dt);
            return;
        }

        if (st.enemyBaseWinConditionActive && isEnemyBaseDestroyed(ctx)) {
            st.objectiveProgress = st.objectiveGoal;
            secureSectorObjective(ctx, "SECTOR SECURE - EXTRACTION WINDOW OPEN");
            return;
        }

        st.sectorElapsed += dt;
        boolean sectorTimedOut = st.sectorElapsed >= st.sectorTimeLimit;

        syncPersistentFleetCasualties(ctx, st);
        detectHostileKills(ctx);
        updateStrategicTaskForces(ctx, st, dt);
        boolean missionPocketActive = isMissionPocketObjectiveActive(st);
        if (missionPocketActive) {
            detectObjectiveAssetLosses(ctx);
            updateAuthoredSectorScript(ctx, st);
            updateDistributedMapPressure(ctx, st);
        }
        updateEscortFormationBehavior(ctx, st, dt);
        updatePocketDiscoveries(ctx, st);
        updateRecoverableWreckSites(ctx, st);
        if (missionPocketActive) {
            updateSideObjective(ctx, dt);
        }
        updateMissionBanter(ctx, st);
        updateObjective(ctx, dt);
        if (ctx.gameOver || isTransitioning(ctx)) {
            return;
        }
        if (st.objectiveSecured) {
            return;
        }
        if (sectorTimedOut) {
            if (timeoutCountsAsSuccess(st)) {
                st.objectiveProgress = st.objectiveGoal;
                secureSectorObjective(ctx, "SECTOR SECURE - EXTRACTION WINDOW OPEN");
                return;
            }
            failRun(ctx, timeoutFailureText(st), timeoutFailureBanner(st));
        }
    }

    public static boolean isCampaignActive(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled;
    }

    public static boolean isStrategicOvermapMode(GameContext ctx) {
        return isStrategicOvermapMode(state(ctx));
    }

    private static boolean isStrategicOvermapMode(CampaignState st) {
        return st != null && st.enabled && st.strategicOvermapMode;
    }

    public static boolean useAuthoredWaveSchedule(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.sector <= AUTHORED_VERTICAL_SLICE_LAST_SECTOR;
    }

    public static boolean suppressRandomEvents(GameContext ctx) {
        return useAuthoredWaveSchedule(ctx);
    }

    public static double nextWaveDelay(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return 14.0 + ctx.rng.nextDouble() * 10.0;

        // Later sectors tighten pressure.
        double base = 13.0 - Math.min(6.0, st.sector * 0.45);
        return Math.max(5.0, (base + ctx.rng.nextDouble() * 3.0) * st.enemyWaveDelayMul);
    }

    public static int groupsPerWave(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return 1;
        int base = (st.sector >= 18) ? 3 : (st.sector >= 9 ? 2 : 1);
        return Math.max(1, (int) Math.round(base * st.enemyWaveGroupMul));
    }

    public static String hudObjectiveTitle(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        if (isStrategicOvermapMode(st)) {
            CampaignLocation current = currentCampaignLocation(ctx);
            return "RETURN TO EARTH  /  STRATEGIC GALAXY MAP  /  "
                    + ((current == null) ? "ROUTE UNKNOWN" : current.name.toUpperCase(Locale.US));
        }
        SectorLore lore = loreFor(st.sector);
        String objective = (st.objectiveLabel == null || st.objectiveLabel.isBlank())
                ? ""
                : st.objectiveLabel.trim();
        String episode = actTitleFor(st.act) + " / " + lore.title;
        if (objective.isEmpty()) return episode;
        return objective + "  |  " + episode;
    }

    public static String hudObjectiveDetail(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        if (isStrategicOvermapMode(st)) {
            return overmapHudDetail(ctx, st, false);
        }
        int left = (int) Math.ceil(Math.max(0.0, st.sectorTimeLimit - st.sectorElapsed));
        String p = formatProgress(st.objectiveProgress, st.objectiveGoal);
        ArrayList<String> lines = new ArrayList<>();
        addObjectiveLine(lines, objectiveMainLine(st));
        addObjectiveLine(lines, objectiveWinStateLine(st, left));
        addObjectiveLine(lines, objectiveTimerStateLine(st, left));
        addObjectiveLine(lines, objectiveCurrentTaskLine(st));
        addObjectiveLine(lines, objectiveFailureRiskLine(st));
        addObjectiveLine(lines, objectiveTransitLine(st));
        addObjectiveLine(lines, objectiveOptionalLine(st));
        addObjectiveLine(lines, objectiveThreatLine(st));
        addObjectiveLine(lines, objectiveProgressLine(st, p, left));
        return String.join("\n", lines);
    }

    public static String hudObjectiveExpandedDetail(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        if (isStrategicOvermapMode(st)) {
            return overmapHudDetail(ctx, st, true);
        }
        SectorLore lore = loreFor(st.sector);
        int left = (int) Math.ceil(Math.max(0.0, st.sectorTimeLimit - st.sectorElapsed));
        String p = formatProgress(st.objectiveProgress, st.objectiveGoal);
        ArrayList<String> lines = new ArrayList<>();
        addObjectiveLine(lines, objectiveMainLine(st));
        addObjectiveLine(lines, objectiveWinStateLine(st, left));
        addObjectiveLine(lines, objectiveTimerStateLine(st, left));
        addObjectiveLine(lines, objectiveCurrentTaskLine(st));
        addObjectiveLine(lines, objectiveFailureRiskLine(st));
        addObjectiveLine(lines, objectiveTransitLine(st));
        addObjectiveLine(lines, objectiveOptionalLine(st));
        addObjectiveLine(lines, objectiveThreatLine(st));
        addObjectiveLine(lines, objectiveProgressLine(st, p, left));
        addObjectiveLine(lines, objectivePocketLine(st));
        if (lore != null) {
            addObjectiveLine(lines, "Theater: " + lore.title + " / " + lore.location);
            addObjectiveLine(lines, "Why: " + lore.completionLead);
        }
        return String.join("\n", lines);
    }

    public static boolean shouldShowMissionIntro(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.missionIntroTimer > 0.0;
    }

    public static double missionIntroAlpha(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return 0.0;
        return MathUtil.clamp(st.missionIntroTimer / ((st.sector == 1) ? 14.0 : 8.5), 0.0, 1.0);
    }

    public static String missionIntroTitle(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        SectorLore lore = loreFor(st.sector);
        return "SECTOR " + st.sector + "/" + st.totalSectors + "  " + lore.title;
    }

    public static String missionIntroBody(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        SectorLore lore = loreFor(st.sector);
        int left = (int) Math.ceil(Math.max(0.0, st.sectorTimeLimit - st.sectorElapsed));
        String p = formatProgress(st.objectiveProgress, st.objectiveGoal);
        ArrayList<String> lines = new ArrayList<>();
        if (lore != null && lore.location != null && !lore.location.isBlank()) {
            addObjectiveLine(lines, "Location: " + lore.location);
        }
        addObjectiveLine(lines, "Long-range scanners: Multiple unresolved contacts are spread across this battlespace.");
        addObjectiveLine(lines, "Win condition: " + stripPrefix(objectiveWinLine(st, p, left), "Win:"));
        addObjectiveLine(lines, "Current task: " + stripPrefix(objectiveCurrentTaskLine(st), "Current Task:"));
        addObjectiveLine(lines, "Failure condition: " + objectiveFailureLine(st));
        String situationHint = missionSituationHint(st);
        if (!situationHint.isBlank()) {
            addObjectiveLine(lines, "Situation: " + situationHint);
        }
        if (lore != null) {
            addObjectiveLine(lines, "Why this matters: " + lore.completionLead);
        }
        addObjectiveLine(lines, "Suggested first move: " + stripPrefix(objectiveSuggestedMoveLine(st), "First move:"));
        return String.join("\n", lines);
    }

    private static void appendObjectiveBriefPart(StringBuilder brief, String part) {
        if (brief == null || part == null) return;
        String text = part.trim();
        if (text.isEmpty()) return;
        if (brief.length() > 0) brief.append("   ");
        brief.append(text);
    }

    private static void addObjectiveLine(List<String> lines, String line) {
        if (lines == null || line == null) return;
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) lines.add(trimmed);
    }

    private static String objectiveActionLine(CampaignState st) {
        if (st == null) return "";
        return "Action: " + trimmedOrFallback(st.objectiveLabel, "Advance the campaign objective");
    }

    private static String objectiveMainLine(CampaignState st) {
        if (st == null) return "";
        return "Main Objective: " + trimmedOrFallback(st.objectiveLabel, "Advance the campaign objective");
    }

    private static String objectiveWinStateLine(CampaignState st, int leftSeconds) {
        if (st == null) return "";
        if (timeoutCountsAsSuccess(st)) {
            if (st.objectiveAssetRequiredSurvivors > 0 && st.objectiveAssetTotal > 0) {
                String label = trimmedOrFallback(st.objectiveAssetLabel, "objective assets").toLowerCase(Locale.US);
                return "Win State: Reach T-0 with at least "
                        + st.objectiveAssetRequiredSurvivors
                        + " "
                        + label
                        + " alive";
            }
            return "Win State: Reach T-0 with the extraction intact";
        }
        return switch (st.objectiveType) {
            case SURVIVE -> "Win State: Hold until T-" + leftSeconds + "s";
            case DESTROY -> destroyObjectiveUsesMarkers(st)
                    ? "Win State: Destroy every required marked contact before T-0"
                    : "Win State: Destroy every required enemy ship before T-0";
            case ESCORT -> "Win State: Keep the escort alive until T-" + leftSeconds + "s";
            case CAPTURE -> "Win State: Secure the capture point before T-0";
            case BOSS -> "Win State: Break the boss before T-0";
            case FINAL_BOSS -> "Win State: Destroy the AI Mothership";
        };
    }

    private static String objectiveFailureRiskLine(CampaignState st) {
        if (st == null) return "";
        if (st.objectiveAssetRequiredSurvivors > 0 && st.objectiveAssetTotal > 0) {
            String label = trimmedOrFallback(st.objectiveAssetLabel, "objective assets");
            return "Failure Risk: Keep at least "
                    + st.objectiveAssetRequiredSurvivors
                    + " of "
                    + st.objectiveAssetTotal
                    + " "
                    + label
                    + " alive";
        }
        if (st.objectiveType == ObjectiveType.ESCORT && st.escortShip != null) {
            return "Failure Risk: Do not lose " + displayShipName(st.escortShip, "the escort target");
        }
        return "Failure Risk: " + objectiveFailureSummary(st);
    }

    private static String objectiveTimerStateLine(CampaignState st, int leftSeconds) {
        if (st == null) return "";
        if (timeoutCountsAsSuccess(st)) {
            if (st.objectiveAssetRequiredSurvivors > 0 && st.objectiveAssetTotal > 0) {
                return "Timer State: T-" + leftSeconds + "s. Extraction at T-0 is a win if convoy quota holds";
            }
            return "Timer State: T-" + leftSeconds + "s. Extraction at T-0 secures the sector";
        }
        return "Timer State: T-" + leftSeconds + "s. T-0 is a loss if the main objective is unresolved";
    }

    private static String objectiveCurrentTaskLine(CampaignState st) {
        if (st == null) return "";
        String current = currentMissionSectionLabel(st);
        if (st.missionSectionTravelLocked && !current.isBlank()) {
            String cleared = resolvedMissionSectionLabel(st);
            if (!cleared.isBlank() && !cleared.equals(current)) {
                return "Current Task: Pocket clear at " + cleared + "; fly the flagship to " + current + " to resume mission progress";
            }
            return "Current Task: Fly the flagship to " + current + " to resume mission progress";
        }
        if (timeoutCountsAsSuccess(st) && st.objectiveType == ObjectiveType.DESTROY) {
            int remaining = Math.max(0, (int) Math.ceil(st.objectiveGoal - st.objectiveProgress));
            if (remaining > 0 && !current.isBlank()) {
                return "Current Task: Clear " + current + " or hold the convoy lane to T-0 extraction";
            }
            if (remaining > 0) {
                return "Current Task: Hold the convoy lane or destroy the remaining marked strike ships";
            }
            return "Current Task: Hold the convoy lane until T-0 extraction";
        }
        if (!current.isBlank()) {
            return switch (st.objectiveType) {
                case SURVIVE -> "Current Task: Hold " + current;
                case DESTROY -> "Current Task: Clear " + current;
                case ESCORT -> "Current Task: Keep formation through " + current;
                case CAPTURE -> "Current Task: Secure " + current;
                case BOSS, FINAL_BOSS -> "Current Task: Push through " + current;
            };
        }
        return "Current Task: " + stripPrefix(objectiveSuggestedMoveLine(st), "First move:");
    }

    private static String objectiveOptionalLine(CampaignState st) {
        if (st == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        if (st.sideObjectiveType != SideObjectiveType.NONE && st.sideObjectiveLabel != null && !st.sideObjectiveLabel.isBlank()) {
            parts.add(st.sideObjectiveLabel.trim());
        }
        String discovery = discoveryHud(st);
        if (!discovery.isBlank()) {
            parts.add("Investigate " + cleanHudClause(discovery) + " scanner contacts");
        }
        if (parts.isEmpty()) return "";
        return "Optional: " + String.join("  |  ", parts);
    }

    private static String objectiveWinLine(CampaignState st, String progress, int leftSeconds) {
        if (st == null) return "";
        return switch (st.objectiveType) {
            case SURVIVE -> "Win: Hold until T-" + leftSeconds + "s.";
            case DESTROY -> {
                StringBuilder line;
                if (destroyObjectiveUsesMarkers(st)) {
                    line = new StringBuilder("Win: Destroy ")
                            .append((int) Math.ceil(st.objectiveGoal))
                            .append(" marked targets across the active pockets.");
                } else if (st.objectiveGoal > 1.0) {
                    line = new StringBuilder("Win: Destroy ")
                            .append((int) Math.ceil(st.objectiveGoal))
                            .append(" enemy ships.");
                } else {
                    line = new StringBuilder("Win: Destroy the required enemy ship.");
                }
                if (st.objectiveAssetRequiredSurvivors > 0 && st.objectiveAssetTotal > 0) {
                    line.append(" Keep at least ")
                            .append(st.objectiveAssetRequiredSurvivors)
                            .append(" of ")
                            .append(st.objectiveAssetTotal)
                            .append(" objective assets alive.");
                }
                if (timeoutCountsAsSuccess(st)) {
                    line.append(" If the timer expires with the convoy intact, the aperture escape is secured.");
                }
                if (!st.missionSections.isEmpty()) {
                    line.append(" Reach each new pocket to unlock the next contact group.");
                }
                line.append(" Progress ").append(progress).append(".");
                yield line.toString();
            }
            case ESCORT -> "Win: Keep the escort alive until T-" + leftSeconds + "s.";
            case CAPTURE -> "Win: Clear the defenders and secure the capture point.";
            case BOSS -> "Win: Break the boss hull before time runs out.";
            case FINAL_BOSS -> "Win: Destroy the AI Mothership.";
        };
    }

    private static String objectiveFailureLine(CampaignState st) {
        if (st == null) return "Run lost.";
        if (st.objectiveType == ObjectiveType.ESCORT && st.escortShip != null) {
            return "Lose the escort ship and the mission fails.";
        }
        String timerFailure = switch (st.objectiveType) {
            case SURVIVE -> "Flagship loss ends the run before the timer does.";
            case DESTROY, CAPTURE, BOSS, FINAL_BOSS -> timeoutCountsAsSuccess(st)
                    ? "Flagship loss ends the run before the convoys extract."
                    : "Timeout or flagship loss ends the run.";
            case ESCORT -> "Escort or flagship loss ends the run.";
        };
        if (st.objectiveAssetRequiredSurvivors > 0) {
            return objectiveAssetFailureText(st).replace("DEFEAT: ", "") + " " + timerFailure;
        }
        return timerFailure;
    }

    private static String objectiveAreaLine(CampaignState st, SectorLore lore) {
        String landmarks = landmarkHud(st);
        if (!landmarks.isBlank()) return landmarks.trim();
        if (lore != null && lore.location != null && !lore.location.isBlank()) {
            return "AO: " + lore.location.trim();
        }
        return "";
    }

    private static String objectiveLoreLeadLine(SectorLore lore) {
        if (lore == null || lore.hudLead == null || lore.hudLead.isBlank()) return "";
        return lore.hudLead.trim();
    }

    private static String objectivePocketLine(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return "";
        int total = st.missionSections.size();
        int index = Math.max(0, Math.min(total - 1, st.activeMissionSection));
        MissionSection current = st.missionSections.get(index);
        StringBuilder line = new StringBuilder("Route: Clear ");
        line.append(current.label);
        line.append(" to unlock the next pocket");
        if (index + 1 < total) {
            MissionSection next = st.missionSections.get(index + 1);
            line.append("  |  Then move the flagship to ").append(next.label);
        }
        String discoveries = discoveryHud(st);
        if (!discoveries.isBlank()) line.append("  |  ").append(cleanHudClause(discoveries)).append(" scanner contacts");
        return line.toString();
    }

    private static String objectiveTransitLine(CampaignState st) {
        if (st == null) return "";
        if (st.missionSectionTravelLocked) {
            String destination = currentMissionSectionLabel(st);
            return "Next Move: Pocket clear; route the flagship to " + destination + " because kills are paused until arrival";
        }
        return "Next Move: " + stripPrefix(objectiveSuggestedMoveLine(st), "First move:");
    }

    private static String objectiveSuggestedMoveLine(CampaignState st) {
        if (st == null) return "";
        String current = currentMissionSectionLabel(st);
        String next = nextMissionSectionLabel(st);
        if (!next.isBlank()) {
            return "First move: Clear " + current + ", then fly the flagship into " + next + " to unlock the next wave.";
        }
        if (!current.isBlank()) {
            return "First move: Clear " + current + " and sweep nearby scanner returns once the lane is stable.";
        }
        return "First move: Build a clean screen, then chase down long-range scanner contacts across the side pockets.";
    }

    private static String objectiveThreatLine(CampaignState st) {
        if (st == null) return "";
        String threat = firstHudClause(st.threatStateLabel, "THREAT:", "CONTACTS:", "RESERVES:", "VECTOR:", "DISCOVERY:");
        if (!threat.isEmpty()) return "Threat: " + threat;
        String phase = firstHudClause(st.objectivePhaseLabel, "PHASE:", "MAP:", "SECTION:", "TRANSIT:");
        if (!phase.isEmpty()) return "Now: " + phase;
        return "";
    }

    private static String objectiveProgressLine(CampaignState st, String progress, int leftSeconds) {
        if (st == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        parts.add("Progress " + progress);
        parts.add("T-" + leftSeconds + "s");
        String side = sideObjectiveHud(st);
        if (!side.isBlank()) parts.add("Side " + cleanHudClause(side));
        String discoveries = discoveryHud(st);
        if (!discoveries.isBlank()) parts.add(cleanHudClause(discoveries));
        if (st.objectiveType == ObjectiveType.ESCORT) {
            parts.add("Formation "
                    + (int) Math.round(MathUtil.clamp(st.escortFormationIntegrity, 0.0, 1.0) * 100.0) + "%");
        }
        String assets = objectiveAssetHud(st);
        if (!assets.isBlank()) parts.add(cleanHudClause(assets));
        return "Status: " + String.join("  |  ", parts);
    }

    private static String currentMissionSectionLabel(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return "";
        int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
        return st.missionSections.get(index).label;
    }

    private static String resolvedMissionSectionLabel(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return "";
        int index = resolvedObjectiveSectionIndex(st);
        index = Math.max(0, Math.min(st.missionSections.size() - 1, index));
        return st.missionSections.get(index).label;
    }

    private static String nextMissionSectionLabel(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return "";
        int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection + 1));
        if (index <= st.activeMissionSection || index >= st.missionSections.size()) return "";
        return st.missionSections.get(index).label;
    }

    private static String trimmedOrFallback(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        return text.trim();
    }

    private static String stripPrefix(String text, String prefix) {
        if (text == null) return "";
        if (prefix == null || prefix.isBlank()) return text.trim();
        String trimmed = text.trim();
        return trimmed.startsWith(prefix) ? trimmed.substring(prefix.length()).trim() : trimmed;
    }

    private static String displayShipName(Ship ship, String fallback) {
        if (ship == null) return fallback;
        if (ship.name != null && !ship.name.isBlank()) return ship.name.trim();
        if (ship.role != null) return ship.role.name().replace('_', ' ');
        return fallback;
    }

    private static String objectiveFailureSummary(CampaignState st) {
        if (st == null) return "Run lost if the flagship goes down";
        return switch (st.objectiveType) {
            case SURVIVE -> "Flagship loss ends the run before the timer does";
            case DESTROY, CAPTURE, BOSS, FINAL_BOSS -> timeoutCountsAsSuccess(st)
                    ? "Flagship loss ends the run before extraction"
                    : "Timeout or flagship loss ends the run";
            case ESCORT -> "Escort or flagship loss ends the run";
        };
    }

    private static String flagshipFailureText(CampaignState st) {
        if (st != null && timeoutCountsAsSuccess(st)) {
            return "DEFEAT: FLAGSHIP LOST BEFORE EXTRACTION";
        }
        return "DEFEAT: FLAGSHIP LOST";
    }

    private static String timeoutFailureText(CampaignState st) {
        return "DEFEAT: T-0 BEFORE OBJECTIVE COMPLETE";
    }

    private static String timeoutFailureBanner(CampaignState st) {
        if (st == null) return "Timer expired before the sector objective was secured";
        return switch (st.objectiveType) {
            case SURVIVE -> "Timer expired before the hold operation stabilized";
            case DESTROY -> "Timer expired before the marked objective group was fully cleared";
            case ESCORT -> "Timer expired before the escort objective reached extraction";
            case CAPTURE -> "Timer expired before the capture point was secured";
            case BOSS -> "Timer expired before the boss hull was broken";
            case FINAL_BOSS -> "Timer expired before the AI Mothership was destroyed";
        };
    }

    private static String firstHudClause(String text, String... preferredPrefixes) {
        if (text == null || text.isBlank()) return "";
        if (preferredPrefixes != null) {
            for (String prefix : preferredPrefixes) {
                String match = hudClause(text, prefix);
                if (!match.isEmpty()) return cleanHudClause(match);
            }
        }
        for (String part : text.split("\\s{3,}")) {
            String trimmed = cleanHudClause(part);
            if (!trimmed.isEmpty()) return trimmed;
        }
        return "";
    }

    private static String hudClause(String text, String prefix) {
        if (text == null || text.isBlank() || prefix == null || prefix.isBlank()) return "";
        for (String part : text.split("\\s{3,}")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) return trimmed;
        }
        return "";
    }

    private static String cleanHudClause(String text) {
        if (text == null) return "";
        String out = text.trim();
        if (out.startsWith("AO:")) out = out.substring(3).trim();
        if (out.startsWith("ASSETS:")) out = out.substring(7).trim();
        if (out.startsWith("PHASE:")) out = out.substring(6).trim();
        if (out.startsWith("THREAT:")) out = out.substring(7).trim();
        if (out.startsWith("MAP:")) out = out.substring(4).trim();
        if (out.startsWith("CONTACTS:")) out = out.substring(9).trim();
        if (out.startsWith("RESERVES:")) out = out.substring(9).trim();
        if (out.startsWith("VECTOR:")) out = out.substring(7).trim();
        if (out.startsWith("DISCOVERY:")) out = out.substring(10).trim();
        return out.trim();
    }

    private static String landmarkHud(CampaignState st) {
        if (st == null || st.landmarks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("   AO: ");
        int count = Math.min(2, st.landmarks.size());
        for (int i = 0; i < count; i++) {
            CampaignLandmark landmark = st.landmarks.get(i);
            if (landmark == null || landmark.label == null || landmark.label.isBlank()) continue;
            if (sb.length() > 7) sb.append(" / ");
            sb.append(landmark.label);
        }
        return (sb.length() <= 7) ? "" : sb.toString();
    }

    private static String objectiveAssetHud(CampaignState st) {
        if (st == null || st.objectiveAssetTotal <= 0) return "";
        if (st.objectiveAssetLabel == null || st.objectiveAssetLabel.isBlank()) return "";
        int alive = liveObjectiveAssets(st);
        String quota = (st.objectiveAssetRequiredSurvivors > 0)
                ? ("  SAFE>=" + st.objectiveAssetRequiredSurvivors)
                : "";
        return "   ASSETS: " + st.objectiveAssetLabel + " " + alive + "/" + st.objectiveAssetTotal + quota;
    }

    private static String missionSectionHud(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return "";
        int total = st.missionSections.size();
        int index = Math.max(0, Math.min(total - 1, st.activeMissionSection));
        MissionSection section = st.missionSections.get(index);
        String mode = st.missionSectionTravelLocked ? "TRANSIT" : "SITE";
        return mode + " " + (index + 1) + "/" + total + " " + section.label;
    }

    private static String discoveryHud(CampaignState st) {
        if (st == null || st.discoverySites.isEmpty()) return "";
        String text = "DISC " + st.discoveriesFound + "/" + st.discoverySites.size();
        int availableRecoveries = 0;
        for (RecoverableWreckSite site : st.recoverableWreckSites) {
            if (site != null && !site.claimed) availableRecoveries++;
        }
        if (availableRecoveries > 0) {
            text += "  REC " + availableRecoveries;
        }
        return text;
    }

    public static boolean hasCapturePoint(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.objectiveType == ObjectiveType.CAPTURE;
    }

    public static double captureX(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0.0 : st.captureX;
    }

    public static double captureY(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0.0 : st.captureY;
    }

    public static double captureRadius(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0.0 : st.captureRadius;
    }

    static List<CampaignLandmark> landmarks(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? List.of() : st.landmarks;
    }

    static List<CampaignLandmark> strategicLandmarks(GameContext ctx) {
        CampaignState st = state(ctx);
        if (isStrategicOvermapMode(st)) return List.of();
        if (st == null || !st.enabled || st.landmarks.isEmpty()) return List.of();
        ArrayList<CampaignLandmark> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (CampaignLandmark landmark : st.landmarks) {
            if (landmark == null || landmark.discoveryDerived) continue;
            String label = trimmedOrFallback(landmark.label, "");
            if (label.isBlank()) continue;
            String key = landmark.type + "|" + label + "|" + Math.round(landmark.x / 25.0) + "|" + Math.round(landmark.y / 25.0);
            if (!seen.add(key)) continue;
            out.add(landmark);
        }
        return out;
    }

    static int activeSector(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null || !st.enabled) ? 0 : st.sector;
    }

    static int objectiveStage(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null || !st.enabled) ? 0 : st.objectiveStage;
    }

    static double objectiveProgressRatio(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || st.objectiveGoal <= 0.0) return 0.0;
        return MathUtil.clamp(st.objectiveProgress / st.objectiveGoal, 0.0, 1.0);
    }

    static double sectorElapsedRatio(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || st.sectorTimeLimit <= 0.0) return 0.0;
        return MathUtil.clamp(st.sectorElapsed / st.sectorTimeLimit, 0.0, 1.0);
    }

    public static boolean isTransitioning(GameContext ctx) {
        CampaignState st = state(ctx);
        if (isStrategicOvermapMode(st)) return false;
        return st != null && st.enabled
                && (st.transitionTimer > 0 || st.awaitingEpisodeLaunch || st.awaitingFleetHubChoice);
    }

    public static boolean isSectorObjectiveSecured(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.objectiveSecured;
    }

    public static boolean canExtractFromCurrentSector(GameContext ctx) {
        CampaignState st = state(ctx);
        if (isStrategicOvermapMode(st)) return false;
        if (ctx == null || st == null || !st.enabled || st.awaitingEpisodeLaunch) return false;
        if (!st.objectiveSecured) return false;
        return st.sectorElapsed >= Math.max(0.0, st.extractionMinHoldSeconds);
    }

    public static String extractionReadinessBanner(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return "SAFE EXIT UNAVAILABLE";
        if (!st.objectiveSecured) return "COMPLETE THE OBJECTIVE BEFORE EXTRACTION";
        double left = Math.max(0.0, Math.ceil(st.extractionMinHoldSeconds - st.sectorElapsed));
        if (left > 0.0) return "EXTRACTION LOCKED FOR " + (int) left + "S";
        return "EXTRACTION READY";
    }

    public static double transitionSeconds(GameContext ctx) {
        CampaignState st = state(ctx);
        if (isStrategicOvermapMode(st)) return 0.0;
        if (st == null) return 0.0;
        if (st.awaitingFleetHubChoice) return Math.max(0.0, st.fleetHubChoiceTimer);
        if (st.awaitingEpisodeLaunch) return 0.0;
        return Math.max(0.0, st.transitionTimer);
    }

    public static boolean isAwaitingFleetHubChoice(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.awaitingFleetHubChoice;
    }

    public static String transitionLabel(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return "";
        return st.transitionLabel == null ? "" : st.transitionLabel;
    }

    public static String transitionSummaryTop(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return "";
        return st.transitionSummaryTop == null ? "" : st.transitionSummaryTop;
    }

    public static String transitionSummaryBottom(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return "";
        return st.transitionSummaryBottom == null ? "" : st.transitionSummaryBottom;
    }

    public static List<CampaignRouteChoice> routeChoices(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.routeChoices.isEmpty()) return List.of();
        return List.copyOf(st.routeChoices);
    }

    public static int selectedRouteChoiceIndex(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.routeChoices.isEmpty()) return -1;
        return MathUtil.clamp(st.selectedRouteChoice, 0, st.routeChoices.size() - 1);
    }

    public static CampaignRouteChoice selectedRouteChoice(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.routeChoices.isEmpty()) return null;
        int idx = MathUtil.clamp(st.selectedRouteChoice, 0, st.routeChoices.size() - 1);
        return st.routeChoices.get(idx);
    }

    public static boolean selectRouteChoice(GameContext ctx, int index) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled || st.routeChoices.isEmpty()) return false;
        if (!st.awaitingEpisodeLaunch && !st.awaitingFleetHubChoice) return false;
        int idx = MathUtil.clamp(index, 0, st.routeChoices.size() - 1);
        st.selectedRouteChoice = idx;
        applySelectedRouteChoice(ctx, st, false);
        CampaignRouteChoice choice = st.routeChoices.get(idx);
        EventSystem.showBanner(ctx, "ROUTE SELECTED: " + choice.title.toUpperCase(Locale.US), 1.4);
        return true;
    }

    public static double targetingRangeMul(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return 1.0;
        return st.targetingRangeMul;
    }

    public static double miningRateMul(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return 1.0;
        return st.miningRateMul;
    }

    public static double oreCreditMul(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return 1.0;
        return st.oreCreditMul;
    }

    public static int currentCampaignOre(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) {
            return (ctx == null || ctx.player == null) ? 0 : Math.max(0, ctx.player.cargo);
        }
        st.oreLedger.normalize();
        int flagshipOre = (ctx == null || ctx.player == null) ? 0 : Math.max(0, ctx.player.cargo);
        return Math.max(st.oreLedger.storedOre, flagshipOre);
    }

    public static boolean spendCampaignOre(GameContext ctx, int amount) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return false;
        int spend = Math.max(0, amount);
        if (ctx != null && ctx.player != null && ctx.player.cargo > st.oreLedger.storedOre) {
            setCampaignOre(ctx, st, ctx.player.cargo);
        }
        if (currentCampaignOre(ctx) < spend) return false;
        setCampaignOre(ctx, st, st.oreLedger.storedOre - spend);
        return true;
    }

    public static void grantCampaignOre(GameContext ctx, int amount) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return;
        int delta = Math.max(0, amount);
        if (delta <= 0) return;
        setCampaignOre(ctx, st, currentCampaignOre(ctx) + delta);
    }

    public static boolean suppressAutoLock(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.disableAutoLock;
    }

    public static boolean usesPersistentFleetShop(GameContext ctx) {
        return isFleetHubSession(ctx);
    }

    public static boolean isPlayerControlLocked(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.introSequenceActive;
    }

    public static boolean isFleetHubSession(GameContext ctx) {
        CampaignState st = state(ctx);
        return ctx != null
                && st != null
                && st.enabled
                && st.awaitingEpisodeLaunch;
    }

    public static boolean persistCheckpointForMenuExit(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled || ctx.player == null) return false;
        if (ctx.gameOver || ctx.state == GameState.GAME_OVER) return false;

        // When exiting via F10 (whether in mission or fleet hub), save the current checkpoint
        // This preserves ore, cargo, and ship inventory state
        int resumeSector = (st.awaitingFleetHubChoice || st.awaitingEpisodeLaunch)
                ? Math.max(1, st.pendingEpisodeSector > 0 ? st.pendingEpisodeSector : st.sector)
                : Math.max(1, st.sector);
        if (resumeSector > st.totalSectors) return false;
        return saveCheckpoint(ctx, st, resumeSector);
    }

    public static boolean hasCinematicFocus(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled
                && Double.isFinite(st.cinematicFocusX)
                && Double.isFinite(st.cinematicFocusY);
    }

    public static double cinematicFocusX(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null || !Double.isFinite(st.cinematicFocusX))
                ? ((ctx == null || ctx.player == null) ? 0.0 : ctx.player.x)
                : st.cinematicFocusX;
    }

    public static double cinematicFocusY(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null || !Double.isFinite(st.cinematicFocusY))
                ? ((ctx == null || ctx.player == null) ? 0.0 : ctx.player.y)
                : st.cinematicFocusY;
    }

    public static Ship currentBaseUpgradeAnchor(GameContext ctx) {
        if (ctx == null) return null;
        if (isFleetHubSession(ctx)) {
            Ship selected = fleetSelectedShip(ctx);
            if (selected != null) return selected;
            return ctx.player;
        }
        if (isCampaignActive(ctx)) {
            return ctx.player;
        }
        return EconomySystem.getDockedFriendlyBase(ctx);
    }

    public static Ship fleetSelectedShip(GameContext ctx) {
        if (ctx == null || ctx.ui == null || ctx.ships == null) return null;
        int selectedId = ctx.ui.fleetSelectedShipId;
        if (selectedId <= 0) return null;
        return findShipById(ctx, selectedId);
    }

    public static Ship selectFleetShipAtCursor(GameContext ctx, int screenX, int screenY) {
        if (ctx == null || ctx.ui == null || ctx.player == null) return null;
        if (!isFleetHubSession(ctx)) return null;

        double wx = CameraSystem.screenToWorldX(ctx, screenX);
        double wy = CameraSystem.screenToWorldY(ctx, screenY);
        Ship best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        for (Ship ship : ctx.ships) {
            if (!isFleetSelectionCandidate(ship)) continue;
            double radius = Math.max(38.0, ship.radius * 1.6 + 12.0);
            double d2 = GameMath.dist2(wx, wy, ship.x, ship.y);
            if (d2 > radius * radius || d2 >= bestDist2) continue;
            best = ship;
            bestDist2 = d2;
        }
        if (best == null) return null;

        ctx.ui.fleetSelectedShipId = best.id;
        ctx.ui.fleetSelectedTurretIndex = 0;
        ctx.lockedTarget = best;
        EventSystem.showBanner(ctx, "SELECTED: " + ((best.name == null || best.name.isBlank()) ? best.role.name() : best.name), 0.9);
        return best;
    }

    public static boolean tryEnterFleetHubImmediately(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.awaitingFleetHubChoice) return false;
        // Player pressed TAB to open fleet hub immediately instead of waiting
        enterFleetHub(ctx, st);
        st.awaitingFleetHubChoice = false;
        st.fleetHubChoiceTimer = 0.0;
        EventSystem.showBanner(ctx, "FLEET HANGAR OPENED", 1.5);
        return true;
    }

    private static void enterFleetHub(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        if (st.pendingEpisodeSector <= 0) {
            st.pendingEpisodeSector = Math.max(1, st.sector > 0 ? st.sector : 1);
        }
        st.awaitingEpisodeLaunch = true;
        st.transitionTimer = 0.0;
        if (st.transitionLabel == null || st.transitionLabel.isBlank()) st.transitionLabel = "FLEET HANGAR";
        if (st.transitionSummaryTop == null || st.transitionSummaryTop.isBlank()) {
            st.transitionSummaryTop = "Fleet hangar open. Click a ship to focus it, refit the fleet, then launch when ready.";
        }
        // Always replace the bottom row with fleet hub controls (sector-clear screens use the same overlay).
        st.transitionSummaryBottom = routeChoiceSummary(st)
                + "   |   1-3 select route   |   TAB fleet refit   |   B upgrades   |   ENTER launches";
        st.introSequenceActive = false;
        st.introPhase = 0;
        st.introTimer = 0.0;
        st.cinematicFocusX = Double.NaN;
        st.cinematicFocusY = Double.NaN;
        quietEpisodeInterlude(ctx, st);
        positionFleetHubPocket(ctx, st);
        resetPersistentFleetSpawnHandles(st);
        spawnPersistentBlueFleet(ctx, st);
        arrangeFleetHubFormation(ctx, st);
        if (ctx.player != null) {
            ctx.player.vx = 0.0;
            ctx.player.vy = 0.0;
        }
        ctx.cameraOffsetX = 0.0;
        ctx.cameraOffsetY = 0.0;
        ctx.firingPrimaryManual = false;
        ctx.firingPrimaryManualLatched = false;
        ctx.firingSecondaryManual = false;
        ctx.firingSecondaryManualLatched = false;
        ctx.firingPrimaryAuto = false;
        ctx.firingSecondaryAuto = false;
        ctx.miningKeyDown = false;
        ctx.command.playerTeleportCharging = false;
        ctx.command.playerTeleportChargeRemaining = 0.0;
        ctx.ui.fleetSelectedShipId = (ctx.player == null) ? -1 : ctx.player.id;
        ctx.ui.fleetSelectedTurretIndex = 0;
        ctx.ui.fleetRefitMode = true;
        ctx.lockedTarget = ctx.player;
        ctx.state = GameState.FLEET;
    }

    private static boolean hasPersistentFleetSnapshotPoses(CampaignState st) {
        if (st == null || st.persistentBlueFleet.isEmpty()) return false;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (hasPersistentEntryPose(entry)) return true;
        }
        return false;
    }

    private static void positionFleetHubPocket(GameContext ctx, CampaignState st) {
        if (ctx == null || ctx.player == null) return;
        double centerX = GameMath.clamp(ctx.WORLD_W * 0.32, 240.0, ctx.WORLD_W - 240.0);
        double centerY = GameMath.clamp(ctx.WORLD_H * 0.64, 240.0, ctx.WORLD_H - 240.0);
        ctx.player.x = centerX;
        ctx.player.y = centerY;
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        if (Double.isFinite(st.persistentFleetHeading)) {
            ctx.player.angle = st.persistentFleetHeading;
        }
        ensureFleetHubAsteroid(ctx, centerX + 320.0, centerY - 60.0);
    }

    private static void ensureFleetHubAsteroid(GameContext ctx, double x, double y) {
        if (ctx == null || ctx.asteroids == null) return;
        for (Asteroid asteroid : ctx.asteroids) {
            if (asteroid == null) continue;
            if (Math.hypot(asteroid.x - x, asteroid.y - y) <= 220.0) {
                return;
            }
        }
        ctx.asteroids.add(new Asteroid(x, y, 38.0, 360));
    }

    public static boolean isFleetSelectionCandidate(Ship ship) {
        if (ship == null || ship.faction == null) return false;
        if (ship.faction.teamId() != Faction.ALLY.teamId()) return false;
        return ship.alive && !ship.dying && ship.hp > 0;
    }

    private static void arrangeFleetHubFormation(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;

        double centerX = GameMath.clamp(ctx.WORLD_W * 0.5, 280.0, ctx.WORLD_W - 280.0);
        double centerY = GameMath.clamp(ctx.WORLD_H * 0.5, 280.0, ctx.WORLD_H - 280.0);
        ctx.player.x = centerX;
        ctx.player.y = centerY;
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.player.angle = -Math.PI / 2.0;

        java.util.List<Ship> titans = new ArrayList<>();
        java.util.List<Ship> others = new ArrayList<>();
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.activeShipId <= 0) continue;
            Ship ship = findShipById(ctx, entry.activeShipId);
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (isTitanPersistentEntry(entry)) {
                titans.add(ship);
            } else {
                others.add(ship);
            }
        }

        double titanRing = Math.max(260.0, ctx.player.radius + 240.0);
        for (int i = 0; i < titans.size(); i++) {
            Ship titan = titans.get(i);
            double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * i / Math.max(1, titans.size()));
            titan.x = centerX + Math.cos(angle) * titanRing;
            titan.y = centerY + Math.sin(angle) * titanRing;
            titan.vx = 0.0;
            titan.vy = 0.0;
            titan.angle = angle + Math.PI / 2.0;
        }

        double outerRing = titanRing + 260.0;
        for (int i = 0; i < others.size(); i++) {
            Ship ship = others.get(i);
            double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * i / Math.max(1, others.size()));
            ship.x = centerX + Math.cos(angle) * outerRing;
            ship.y = centerY + Math.sin(angle) * outerRing;
            ship.vx = 0.0;
            ship.vy = 0.0;
            ship.angle = angle + Math.PI / 2.0;
        }
    }

    private static void syncPersistentFleetEntrySnapshots(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.persistentBlueFleet.isEmpty()) return;
        if (ctx.player != null) {
            st.persistentFleetHeading = ctx.player.angle;
        }
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.activeShipId <= 0) continue;
            Ship ship = findShipById(ctx, entry.activeShipId);
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) {
                entry.destroyed = true;
                entry.activeShipId = -1;
                continue;
            }
            snapshotPersistentFleetEntry(ctx, st, entry, ship);
        }
    }

    private static void snapshotPersistentFleetEntry(GameContext ctx, CampaignState st, PersistentFleetEntry entry, Ship ship) {
        if (entry == null || ship == null) return;
        entry.name = (ship.name == null || ship.name.isBlank()) ? entry.name : ship.name;
        BaseUpgrades up = (ctx == null) ? null : ctx.baseUpgrades.get(ship);
        normalizeCampaignShipUpgrades(ship, up);
        entry.hullLv = (up == null) ? 0 : Math.max(0, up.hullLv);
        entry.shieldLv = (up == null) ? 0 : Math.max(0, up.shieldLv);
        entry.turretLv = (up == null) ? 0 : Math.max(0, up.turretLv);
        entry.miningLv = (up == null) ? 0 : Math.max(0, up.miningLv);
        entry.hangarLv = (up == null) ? 0 : Math.max(0, up.hangarLv);
        entry.cargo = Math.max(0, ship.cargo);
        entry.cargoMax = Math.max(0, ship.cargoMax);
        entry.turretData = serializeTurrets(ship);
        entry.primaryWeaponFamilyName = (ship.primaryWeaponFamily == null)
                ? Ship.PrimaryWeaponFamily.ENERGY_BOLT.name()
                : ship.primaryWeaponFamily.name();
        if (ctx != null && ctx.player != null && ship != ctx.player && !isFleetHubSession(ctx)) {
            double dx = ship.x - ctx.player.x;
            double dy = ship.y - ctx.player.y;
            double forward = Math.cos(ctx.player.angle);
            double side = Math.sin(ctx.player.angle);
            entry.relX = dx * forward + dy * side;
            entry.relY = -dx * side + dy * forward;
            entry.relAngle = MathUtil.normalizeAngle(ship.angle - ctx.player.angle);
        }
    }

    public static int campaignOreCost(ShipRole role, int creditCost, int requiredTier) {
        ShipRole resolved = (role == null) ? ShipRole.FRIGATE : role;
        int effectiveTier = campaignRequiredTier(resolved, requiredTier);
        return switch (resolved) {
            case PATROL -> 10;
            case PICKET -> 18;
            case FRIGATE -> 12;
            case ARTILLERY_SHIP -> 24;
            case MISSILE_BOAT -> 24;
            case CIWS_CORVETTE -> 20;
            case MINER -> 14;

            case LIGHT_CRUISER -> 52;
            case MEDIUM_CRUISER -> 68;
            case CRUISER -> 78;
            case BATTLECRUISER -> 108;
            case BATTLESHIP -> 138;
            case STEALTH_SHIP -> 94;
            case HAULER -> 34;
            case TRANSPORT -> 46;

            case DREADNOUGHT -> 210;
            case CARRIER -> 184;
            case DRONE_CARRIER -> 196;
            case SUPERSHIP -> 320;

            case TRANSPORT_TITAN -> 260;
            case BULWARK_TITAN -> 300;
            case CARRIER_SUPPORT_TITAN -> 315;
            case VANGUARD_TITAN -> 330;
            case INTERDICTION_TITAN -> 340;
            case COMMAND_INTEL_TITAN -> 320;
            case BOARDING_RECOVERY_TITAN -> 330;
            case ARTILLERY_TITAN -> 360;
            case SHIELD_BASTION_TITAN -> 380;
            case FLEET_TELEPORTER_TITAN -> 350;
            case ELITE_SUPERSHIP_COMMAND_TITAN -> 410;
            case ELITE_REINFORCEMENTS_TITAN -> 420;
            case MOBILE_STATION_TITAN -> 430;
            case HYPERWEAPON_TITAN -> 480;
            case MOTHERSHIP -> 720;

            default -> {
                int base = Math.max(18, (int) Math.round(Math.max(0, creditCost) * 0.10));
                int tierTax = Math.max(0, effectiveTier) * 16;
                yield Math.max(18, base + tierTax);
            }
        };
    }

    public static int marketCreditCostForRole(ShipRole role) {
        if (role == null) return 0;
        return switch (role) {
            case PATROL -> 140;
            case PICKET -> 180;
            case FRIGATE -> 220;
            case ARTILLERY_SHIP -> 320;
            case MISSILE_BOAT -> 300;
            case CIWS_CORVETTE -> 250;
            case MINER -> 160;

            case LIGHT_CRUISER -> 700;
            case MEDIUM_CRUISER -> 950;
            case CRUISER -> 1100;
            case BATTLECRUISER -> 1600;
            case BATTLESHIP -> 2200;
            case STEALTH_SHIP -> 1200;
            case TRANSPORT -> 460;
            case HAULER -> 260;

            case DREADNOUGHT -> 3200;
            case CARRIER -> 2800;
            case DRONE_CARRIER -> 3000;
            case SUPERSHIP -> 5200;

            case TRANSPORT_TITAN -> TitanArchetype.TRANSPORT.costCredits();
            case BULWARK_TITAN -> TitanArchetype.BULWARK.costCredits();
            case CARRIER_SUPPORT_TITAN -> TitanArchetype.CARRIER_SUPPORT.costCredits();
            case VANGUARD_TITAN -> TitanArchetype.VANGUARD.costCredits();
            case INTERDICTION_TITAN -> TitanArchetype.INTERDICTION.costCredits();
            case COMMAND_INTEL_TITAN -> TitanArchetype.COMMAND_INTEL.costCredits();
            case BOARDING_RECOVERY_TITAN -> TitanArchetype.BOARDING_RECOVERY.costCredits();
            case ARTILLERY_TITAN -> TitanArchetype.ARTILLERY.costCredits();
            case SHIELD_BASTION_TITAN -> TitanArchetype.SHIELD_BASTION.costCredits();
            case FLEET_TELEPORTER_TITAN -> TitanArchetype.FLEET_TELEPORTER.costCredits();
            case ELITE_SUPERSHIP_COMMAND_TITAN -> TitanArchetype.ELITE_SUPERSHIP_COMMAND.costCredits();
            case ELITE_REINFORCEMENTS_TITAN -> TitanArchetype.ELITE_REINFORCEMENTS.costCredits();
            case MOBILE_STATION_TITAN -> TitanArchetype.MOBILE_STATION.costCredits();
            case HYPERWEAPON_TITAN -> TitanArchetype.HYPERWEAPON.costCredits();
            case MOTHERSHIP -> 7200;

            default -> 0;
        };
    }

    static int campaignRequiredTier(ShipRole role, int requiredTier) {
        ShipRole resolved = (role == null) ? ShipRole.FRIGATE : role;
        return switch (resolved) {
            case SUPERSHIP -> 4;
            case INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                    MOBILE_STATION_TITAN -> 4;
            case ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN, HYPERWEAPON_TITAN -> 5;
            default -> Math.max(0, requiredTier);
        };
    }

    static int campaignMaxHangarTier(GameContext ctx) {
        return isCampaignActive(ctx) ? CAMPAIGN_PLAYER_MAX_HANGAR_TIER : CAMPAIGN_ENEMY_MAX_HANGAR_TIER;
    }

    public static boolean campaignShipUpgradeAvailable(Ship ship, int which) {
        if (ship == null || ship.role == null) return false;
        return switch (which) {
            case 1, 2 -> true;
            case 3 -> ship.turrets != null && !ship.turrets.isEmpty();
            case 4 -> campaignShipSupportsLogisticsUpgrade(ship);
            case 5 -> campaignShipSupportsHangarUpgrade(ship);
            default -> false;
        };
    }

    public static String campaignShipUpgradeTitle(Ship ship, int which) {
        if (ship == null || ship.role == null) return null;
        return switch (which) {
            case 1 -> "Hull Fortification";
            case 2 -> "Shield Array";
            case 3 -> campaignShipUpgradeAvailable(ship, 3) ? "Turret Systems" : null;
            case 4 -> campaignShipLogisticsUpgradeTitle(ship);
            case 5 -> campaignShipSupportsHangarUpgrade(ship) ? "Hangar Expansion" : null;
            default -> null;
        };
    }

    public static String campaignShipUpgradeFooter(Ship ship) {
        if (ship == null || ship.role == null) {
            return "Fleet edits apply to the selected hull.";
        }
        boolean logistics = campaignShipSupportsLogisticsUpgrade(ship);
        boolean hangar = campaignShipSupportsHangarUpgrade(ship);
        if (logistics && hangar) {
            return "This hull supports both logistics and hangar systems.";
        }
        if (logistics) {
            return "This hull supports logistics systems but no hangar bay.";
        }
        if (hangar) {
            return "This hull supports a hangar bay but no logistics slot.";
        }
        return "This hull only exposes combat systems.";
    }

    static void applyCampaignShipUpgradeDelta(GameContext ctx, Ship ship, int which, int levels) {
        if (ctx == null || ship == null) return;
        int n = Math.max(0, levels);
        if (n <= 0) return;
        switch (which) {
            case 1 -> {
                ship.hpMax = Math.max(1, ship.hpMax + 40 * n);
                ship.healHull(40 * n);
            }
            case 2 -> {
                ship.shieldActive = true;
                ship.shieldMax = Math.max(0.0, ship.shieldMax + 30.0 * n);
                ship.shieldRegen = Math.max(0.0, ship.shieldRegen + 0.8 * n);
                ship.shield = Math.min(ship.shieldMax, ship.shield + 30.0 * n);
            }
            case 3 -> UISystem.applyTurretSystemsUpgrade(ship, n);
            case 4 -> {
                if (campaignShipSupportsLogisticsUpgrade(ship)) {
                    ship.miningRate = Math.max(0.0, ship.miningRate + 1.4 * n);
                    ship.cargoMax = Math.max(0, ship.cargoMax + 20 * n);
                }
            }
            case 5 -> {
                if (campaignShipSupportsHangarUpgrade(ship) && ship.isCarrier) {
                    ship.maxFighters = Math.max(0, ship.maxFighters + n);
                }
            }
            default -> {
            }
        }
    }

    static void applyCampaignShipUpgrades(GameContext ctx, Ship ship, BaseUpgrades upgrades) {
        if (ctx == null || ship == null || upgrades == null) return;
        normalizeCampaignShipUpgrades(ship, upgrades);
        applyCampaignShipUpgradeDelta(ctx, ship, 1, upgrades.hullLv);
        applyCampaignShipUpgradeDelta(ctx, ship, 2, upgrades.shieldLv);
        applyCampaignShipUpgradeDelta(ctx, ship, 3, upgrades.turretLv);
        applyCampaignShipUpgradeDelta(ctx, ship, 4, upgrades.miningLv);
        applyCampaignShipUpgradeDelta(ctx, ship, 5, upgrades.hangarLv);
    }

    static String campaignShipUpgradeUnavailableReason(Ship ship, int which) {
        if (ship == null || ship.role == null) return "UPGRADE NOT AVAILABLE";
        return switch (which) {
            case 3 -> "THIS HULL HAS NO TURRETS";
            case 4 -> "THIS HULL HAS NO LOGISTICS SLOT";
            case 5 -> "THIS HULL HAS NO HANGAR BAY";
            default -> "UPGRADE NOT AVAILABLE";
        };
    }

    private static boolean campaignShipSupportsLogisticsUpgrade(Ship ship) {
        if (ship == null || ship.role == null) return false;
        return switch (ship.role) {
            case BASE, MOTHERSHIP, MINER, HAULER, TRANSPORT, TRANSPORT_TITAN -> true;
            default -> false;
        };
    }

    private static boolean campaignShipSupportsHangarUpgrade(Ship ship) {
        if (ship == null || ship.role == null) return false;
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.MOTHERSHIP) return true;
        return ship.isCarrier;
    }

    private static String campaignShipLogisticsUpgradeTitle(Ship ship) {
        if (!campaignShipSupportsLogisticsUpgrade(ship)) return null;
        return switch (ship.role) {
            case MINER -> "Mining Ops";
            case HAULER, TRANSPORT, TRANSPORT_TITAN -> "Cargo Ops";
            case BASE -> "Station Logistics";
            case MOTHERSHIP -> "Fleet Logistics";
            default -> "Logistics Ops";
        };
    }

    private static void normalizeCampaignShipUpgrades(Ship ship, BaseUpgrades upgrades) {
        if (ship == null || upgrades == null) return;
        upgrades.hullLv = MathUtil.clamp(upgrades.hullLv, 0, 5);
        upgrades.shieldLv = MathUtil.clamp(upgrades.shieldLv, 0, 5);
        upgrades.turretLv = campaignShipUpgradeAvailable(ship, 3) ? MathUtil.clamp(upgrades.turretLv, 0, 5) : 0;
        upgrades.miningLv = campaignShipSupportsLogisticsUpgrade(ship) ? MathUtil.clamp(upgrades.miningLv, 0, 5) : 0;
        upgrades.hangarLv = campaignShipSupportsHangarUpgrade(ship) ? MathUtil.clamp(upgrades.hangarLv, 0, 5) : 0;
    }

    static int campaignMinSectorForRole(ShipRole role) {
        if (role == ShipRole.SUPERSHIP) {
            return CAMPAIGN_SUPERSHIP_UNLOCK_SECTOR;
        }
        TitanArchetype titan = TitanArchetype.fromShipRole(role);
        return (titan == null) ? 1 : titan.availability().minSector();
    }

    static boolean campaignSectorRequirementMet(GameContext ctx, ShipRole role) {
        CampaignState st = state(ctx);
        return st != null && st.sector >= campaignMinSectorForRole(role);
    }

    static boolean campaignNeedsMobileStation(ShipRole role) {
        return role == ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN
                || role == ShipRole.ELITE_REINFORCEMENTS_TITAN
                || role == ShipRole.HYPERWEAPON_TITAN;
    }

    static int persistentFleetCapUpgradeStep(ShopHullCategory category) {
        return 0;
    }

    static int persistentFleetCapUpgradeMaxLevel(ShopHullCategory category) {
        return 0;
    }

    static int persistentFleetCapUpgradeLevel(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCapUpgradeLevel(state(ctx), category);
    }

    static int persistentFleetCapUpgradeLevel(CampaignState st, ShopHullCategory category) {
        return 0;
    }

    static int persistentFleetCapUpgradeBonus(CampaignState st, ShopHullCategory category) {
        return persistentFleetCapUpgradeLevel(st, category) * persistentFleetCapUpgradeStep(category);
    }

    static int persistentFleetCapUpgradeCreditCost(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCapUpgradeCreditCost(state(ctx), category);
    }

    static int persistentFleetCapUpgradeCreditCost(CampaignState st, ShopHullCategory category) {
        return 0;
    }

    static int persistentFleetCapUpgradeOreCost(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCapUpgradeOreCost(state(ctx), category);
    }

    static int persistentFleetCapUpgradeOreCost(CampaignState st, ShopHullCategory category) {
        return 0;
    }

    public static boolean purchasePersistentFleetCapUpgrade(GameContext ctx, ShopHullCategory category) {
        if (ctx == null || category == null) return false;
        EventSystem.showBanner(ctx, "CAMPAIGN UNIT CAPS REMOVED - GROWTH NOW LIMITED BY COMMAND, COST, AND ATTRITION", 2.2);
        return false;
    }

    static int campaignStandardCommandCost(ShipRole role) {
        if (role == null || role.isTitanOrMothership() || role == ShipRole.SUPERSHIP) return 0;
        return switch (role) {
            case DREADNOUGHT, CARRIER, DRONE_CARRIER -> 2;
            default -> 1;
        };
    }

    static int campaignEliteCommandCost(ShipRole role) {
        return (role == ShipRole.SUPERSHIP) ? 1 : 0;
    }

    static int campaignStandardCommandCapacity(GameContext ctx) {
        return standardCommandCapacity(state(ctx));
    }

    static int campaignStandardCommandUsed(GameContext ctx) {
        return standardCommandUsed(state(ctx));
    }

    static int campaignEliteCommandCapacity(GameContext ctx) {
        return eliteCommandCapacity(ctx, state(ctx));
    }

    static int campaignEliteCommandUsed(GameContext ctx) {
        return eliteCommandUsed(state(ctx));
    }

    static boolean hasOperationalMobileStation(GameContext ctx) {
        return hasOperationalRole(state(ctx), ShipRole.MOBILE_STATION_TITAN);
    }

    static boolean flagshipSupershipBerthOnline(GameContext ctx) {
        return flagshipEliteCommandCapacity(ctx, state(ctx)) > 0;
    }

    static String campaignCommissionRequirementsDetail(GameContext ctx, ShipRole role, int requiredTier) {
        int effectiveTier = campaignRequiredTier(role, requiredTier);
        StringBuilder detail = new StringBuilder("Requirements: shipyard T").append(effectiveTier);
        int minSector = campaignMinSectorForRole(role);
        if (minSector > 1) {
            detail.append(", sector ").append(minSector);
        }
        int standardCost = campaignStandardCommandCost(role);
        if (standardCost > 0) {
            detail.append(", standard command ").append(standardCost)
                    .append(" (")
                    .append(campaignStandardCommandUsed(ctx))
                    .append("/")
                    .append(campaignStandardCommandCapacity(ctx))
                    .append(" committed)");
        }
        int eliteCost = campaignEliteCommandCost(role);
        if (eliteCost > 0) {
            detail.append(", elite command ").append(eliteCost)
                    .append(" (")
                    .append(campaignEliteCommandUsed(ctx))
                    .append("/")
                    .append(campaignEliteCommandCapacity(ctx))
                    .append(" committed)");
            if (role == ShipRole.SUPERSHIP) {
                detail.append(". First berth unlocks at sector ")
                        .append(CAMPAIGN_SUPERSHIP_UNLOCK_SECTOR)
                        .append(" with shipyard T")
                        .append(CAMPAIGN_SUPERSHIP_FLAGSHIP_BERTH_TIER)
                        .append("; more come from an Elite Supership Command Titan");
            }
        }
        if (campaignNeedsMobileStation(role)) {
            detail.append(", commissioned Mobile Station Titan");
        }
        detail.append(".");
        return detail.toString();
    }

    public static boolean purchasePersistentBlueShip(GameContext ctx, ShipRole role, int creditCost, int requiredTier) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || ctx.player == null || role == null) return false;
        if (role == ShipRole.MOTHERSHIP) {
            EventSystem.showBanner(ctx, "MOTHERSHIP ALREADY UNDER COMMAND", 1.6);
            return false;
        }
        if (ctx.player.role != ShipRole.MOTHERSHIP) {
            EventSystem.showBanner(ctx, "CAMPAIGN COMMAND REQUIRES THE MOTHERSHIP", 1.6);
            return false;
        }
        int effectiveTier = campaignRequiredTier(role, requiredTier);
        ShopHullCategory category = ShopHullCategory.forRole(role);

        int hangarTier = 0;
        BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(ctx.player, ignored -> new BaseUpgrades().bindTo(ctx.player));
        if (up != null) {
            up.hangarLv = Math.max(up.hangarLv, CAMPAIGN_PLAYER_STARTING_HANGAR_TIER);
            hangarTier = up.hangarLv;
        }
        if (hangarTier < effectiveTier) {
            EventSystem.showBanner(ctx, "SHIPYARD T" + effectiveTier + " REQUIRED", 1.6);
            return false;
        }

        int minSector = campaignMinSectorForRole(role);
        if (st.sector < minSector) {
            EventSystem.showBanner(ctx, "UNLOCKS IN SECTOR " + minSector, 1.6);
            return false;
        }

        if (campaignNeedsMobileStation(role) && !hasOperationalRole(st, ShipRole.MOBILE_STATION_TITAN)) {
            EventSystem.showBanner(ctx, "MOBILE STATION TITAN REQUIRED", 1.8);
            return false;
        }

        int eliteCost = campaignEliteCommandCost(role);
        int eliteCapacity = eliteCommandCapacity(ctx, st);
        int eliteUsed = eliteCommandUsed(st);
        if (eliteCost > 0) {
            if (eliteCapacity <= 0) {
                if (role == ShipRole.SUPERSHIP) {
                    EventSystem.showBanner(ctx,
                            "REQUIRES SECTOR " + CAMPAIGN_SUPERSHIP_UNLOCK_SECTOR
                                    + " + SHIPYARD T" + CAMPAIGN_SUPERSHIP_FLAGSHIP_BERTH_TIER,
                            1.8);
                } else {
                    EventSystem.showBanner(ctx, "ELITE COMMAND TITAN REQUIRED", 1.8);
                }
                return false;
            }
            if (eliteUsed + eliteCost > eliteCapacity) {
                EventSystem.showBanner(ctx, "ELITE COMMAND CAP REACHED", 1.8);
                return false;
            }
        }

        int standardCost = campaignStandardCommandCost(role);
        int standardCapacity = standardCommandCapacity(st);
        int standardUsed = standardCommandUsed(st);
        if (standardCost > 0 && standardUsed + standardCost > standardCapacity) {
            EventSystem.showBanner(ctx, "STANDARD COMMAND GRID FULL", 1.8);
            return false;
        }

        int oreCost = campaignOreCost(role, creditCost, effectiveTier);
        if (ctx.credits < Math.max(0, creditCost) || currentCampaignOre(ctx) < oreCost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS / ORE", 1.6);
            return false;
        }

        ctx.credits -= Math.max(0, creditCost);
        spendCampaignOre(ctx, oreCost);

        int slotId = st.nextPersistentFleetSlotId++;
        PersistentFleetEntry entry = new PersistentFleetEntry(slotId, role, generatedBlueFleetName(role, slotId));
        st.persistentBlueFleet.add(entry);
        ArrayList<PersistentFleetEntry> spawnedPackage = new ArrayList<>();
        TitanArchetype titan = TitanArchetype.fromShipRole(role);
        if (titan != null && st.ownedTitans.size() < TitanFleetSystem.mothershipTitanCap()) {
            st.ownedTitans.add(titan);
        }
        if (titan == TitanArchetype.ELITE_REINFORCEMENTS) {
            queueEliteReinforcementPackage(st, entry, spawnedPackage);
        }
        rebalancePersistentCommandGroups(st);
        applyCampaignFleetBonuses(ctx, st);
        spawnPurchasedPersistentBlueShip(ctx, st, entry);
        for (PersistentFleetEntry supportEntry : spawnedPackage) {
            spawnPurchasedPersistentBlueShip(ctx, st, supportEntry);
        }
        if (isFleetHubSession(ctx)) {
            arrangeFleetHubFormation(ctx, st);
        }
        EventSystem.showBanner(ctx, "BLUE HULL COMMISSIONED: " + entry.name, 1.8);
        return true;
    }

    public static boolean launchPendingEpisode(GameContext ctx) {
        CampaignState st = state(ctx);
        if (isStrategicOvermapMode(st)) return false;
        if (ctx == null || st == null || !st.awaitingEpisodeLaunch || st.pendingEpisodeSector <= 0) return false;
        applySelectedRouteChoice(ctx, st, true);
        grantSelectedRouteReward(ctx, st);
        syncPersistentFleetEntrySnapshots(ctx, st);
        saveCheckpoint(ctx, st, st.pendingEpisodeSector);
        UISystem.closeAllOverlays(ctx);
        ctx.lockedTarget = null;
        ctx.state = GameState.RUNNING;
        startSector(ctx, st.pendingEpisodeSector);
        return true;
    }

    private static void updateStrategicOvermapCampaign(GameContext ctx, CampaignState st, double dt) {
        if (ctx == null || st == null) return;
        st.sectorElapsed += Math.max(0.0, dt);
        updateCampaignTravel(ctx, st, dt);
        syncPersistentFleetCasualties(ctx, st);
        detectHostileKills(ctx);
        updatePocketDiscoveries(ctx, st);
        updateRecoverableWreckSites(ctx, st);
        maintainStrategicOvermapView(ctx, st);
    }

    private static void updateCampaignTravel(GameContext ctx, CampaignState st, double dt) {
        if (ctx == null || st == null || !st.galaxyTravel.traveling) return;
        st.galaxyTravel.progress = Math.min(1.0, st.galaxyTravel.progress + Math.max(0.0, dt) / Math.max(0.1, st.galaxyTravel.durationSec));
        if (st.galaxyTravel.progress < 1.0) return;
        CampaignLocation destination = campaignLocationById(st, st.galaxyTravel.destinationId);
        st.currentGalaxyLocationId = (destination == null) ? st.currentGalaxyLocationId : destination.id;
        st.selectedGalaxyLocationId = st.currentGalaxyLocationId;
        st.galaxyTravel.clear();
        if (destination == null) return;
        if (!destination.primaryMission) {
            resolveAreaOfInterestArrival(ctx, st, destination);
        } else {
            st.sector = Math.max(1, destination.missionIndex);
            beginCampaignLocationEncounterChoice(ctx, st, destination);
        }
    }

    private static void resolveAreaOfInterestArrival(GameContext ctx, CampaignState st, CampaignLocation destination) {
        if (ctx == null || st == null || destination == null) return;
        if (destination.consumed && destination.type != CampaignLocationType.REPAIR_SITE) {
            EventSystem.showBanner(ctx, "AREA SECURE: " + destination.name.toUpperCase(Locale.US), 1.2);
            return;
        }
        switch (destination.type) {
            case RESOURCE_ZONE -> {
                int ore = 20 + (int) Math.round(destination.threatLevel * 30.0f);
                grantCampaignOre(ctx, ore);
                EventSystem.showBanner(ctx, "RESOURCE HARVEST +" + ore + " ORE", 1.6);
                destination.consumed = true;
            }
            case SALVAGE_FIELD -> {
                int credits = GameContext.scaleCreditEarnings(120 + (int) Math.round(destination.threatLevel * 120.0f));
                ctx.credits += credits;
                EventSystem.showBanner(ctx, "SALVAGE RECOVERED +" + credits + " CREDITS", 1.6);
                destination.consumed = true;
            }
            case HIDDEN_CACHE -> {
                st.strategicTorpedoCharges += 1;
                EventSystem.showBanner(ctx, "HIDDEN CACHE FOUND  +1 TORPEDO STRIKE", 1.6);
                destination.consumed = true;
            }
            case REPAIR_SITE -> EventSystem.showBanner(ctx, "REPAIR ANCHORAGE REACHED", 1.4);
            case DISTRESS_SIGNAL, STORY_EVENT, ENEMY_ACTIVITY -> EventSystem.showBanner(ctx,
                    destination.name.toUpperCase(Locale.US) + "  |  CONTACT REPORT LOGGED",
                    1.5);
            default -> EventSystem.showBanner(ctx, "LOCATION REACHED: " + destination.name.toUpperCase(Locale.US), 1.2);
        }
    }

    private static void beginCampaignLocationEncounterChoice(GameContext ctx, CampaignState st, CampaignLocation location) {
        if (ctx == null || st == null || location == null || ctx.ui == null) return;
        if (location.completed) {
            EventSystem.showBanner(ctx, "MISSION ALREADY SECURED: " + location.name.toUpperCase(Locale.US), 1.3);
            return;
        }
        ctx.ui.showCampaignLocationEncounterPrompt(
                location.id,
                "MISSION NODE: " + location.name.toUpperCase(Locale.US),
                campaignLocationEncounterBody(location),
                missionLocationLabel(location),
                campaignLocationStrengthReadout(ctx, st, location));
        ctx.state = GameState.PAUSED;
        EventSystem.showBanner(ctx, "ENCOUNTER READY: " + location.name.toUpperCase(Locale.US), 1.4);
    }

    private static String campaignLocationEncounterBody(CampaignLocation location) {
        if (location == null) return "Mission encounter available.";
        return "This location launches a single large tactical sector. "
                + "Take command to fight it directly, or auto-resolve it from the campaign layer.";
    }

    private static String missionLocationLabel(CampaignLocation location) {
        if (location == null) return "";
        return location.primaryMission
                ? "Main Mission " + Math.max(1, location.missionIndex) + " of 24"
                : "Area of Interest";
    }

    private static String campaignLocationStrengthReadout(GameContext ctx, CampaignState st, CampaignLocation location) {
        double fleet = galaxyCampaignFleetStrength(ctx, st);
        double threat = campaignLocationThreatValue(st, location);
        return "Fleet " + Math.round(fleet) + "  Threat " + Math.round(threat)
                + "  " + threatReadout(location == null ? 0.0f : location.threatLevel);
    }

    private static void maintainStrategicOvermapView(GameContext ctx, CampaignState st) {
        if (ctx == null || ctx.ui == null || st == null || !isStrategicOvermapMode(st) || ctx.gameOver) return;
        if (ctx.ui.shopOpen || ctx.ui.baseMenuOpen || ctx.ui.powerManagementOpen
                || ctx.ui.crewStationsOpen || ctx.ui.flightDeckOpen || ctx.ui.strategicEncounterPrompt.active) {
            return;
        }
        ctx.ui.mapOpen = true;
        if (ctx.state != GameState.GAME_OVER) {
            ctx.state = GameState.MAP;
        }
    }

    private static boolean hasLiveHostilePresenceInPlayerPocket(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return false;
        int playerSubzone = currentLoadedMissionSubzone(ctx);
        if (playerSubzone < 0) {
            playerSubzone = missionSubzoneForPoint(ctx, st.sector, ctx.player.x, ctx.player.y);
        }
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == ctx.player) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || ship.faction.isFriendlyTo(ctx.player.faction)) continue;
            int shipSubzone = missionSubzoneForPoint(ctx, st.sector, ship.x, ship.y);
            if (shipSubzone == playerSubzone) return true;
        }
        return false;
    }

    private static void enterStrategicOvermap(GameContext ctx, CampaignState st, String banner) {
        if (ctx == null || ctx.ui == null || st == null || !isStrategicOvermapMode(st) || ctx.gameOver) return;
        clearBattleEncounterWorld(ctx);
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = false;
        ctx.ui.powerManagementOpen = false;
        ctx.ui.crewStationsOpen = false;
        ctx.ui.flightDeckOpen = false;
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = Math.max(2.2, ctx.ui.strategicMapZoom);
        CampaignLocation current = currentCampaignLocation(ctx);
        if (current != null) {
            ctx.ui.strategicMapFocusX = current.x;
            ctx.ui.strategicMapFocusY = current.y;
        }
        ctx.state = GameState.MAP;
        if (banner != null && !banner.isBlank()) {
            EventSystem.showBanner(ctx, banner + "  |  STRATEGIC MAP ONLINE", 2.2);
        }
    }

    private static void clearBattleEncounterWorld(GameContext ctx) {
        if (ctx == null) return;
        ctx.projectiles.clear();
        ctx.salvage.clear();
        ctx.asteroids.clear();
        ctx.lockedTarget = null;
        ctx.enemyBase = null;
        ctx.allyBase = null;
        ctx.teamBases.clear();
        ctx.ships.removeIf(ship -> ship != null && ship != ctx.player);
        if (ctx.player != null) {
            ctx.player.vx = 0.0;
            ctx.player.vy = 0.0;
            ctx.player.resetWeaponCycleState();
        }
        VFX.clearAll();
        Explosion.active.clear();
        WreckChunk.clearAll();
        FogOfWarSystem.reset(ctx);
    }

    private static String overmapHudDetail(GameContext ctx, CampaignState st, boolean expanded) {
        if (ctx == null || st == null) return "";
        ArrayList<String> lines = new ArrayList<>();
        CampaignLocation current = currentCampaignLocation(ctx);
        CampaignLocation selected = selectedCampaignLocation(ctx);
        CampaignTravelState travel = st.galaxyTravel;
        addObjectiveLine(lines, "Mode: Strategic galaxy map only; ships and combat exist only inside encounters");
        addObjectiveLine(lines, "Main Missions: " + st.completedMainMissions + " / 24 complete");
        addObjectiveLine(lines, "Earth Progress: " + (int) Math.round(st.earthProgress * 100.0) + "%");
        addObjectiveLine(lines, "Current Location: " + ((current == null) ? "Unknown" : current.name));
        if (selected != null) {
            addObjectiveLine(lines, "Selected Destination: " + selected.name);
            addObjectiveLine(lines, "Threat Estimate: " + threatReadout(selected.threatLevel));
        }
        if (travel.traveling) {
            CampaignLocation destination = campaignLocationById(st, travel.destinationId);
            int eta = (int) Math.ceil(Math.max(0.0, (1.0 - travel.progress) * travel.durationSec));
            addObjectiveLine(lines, "Travel State: En route to " + ((destination == null) ? "destination" : destination.name) + "  ETA " + eta + "s");
            addObjectiveLine(lines, "Interception Risk: " + (int) Math.round(travel.interceptionRisk) + "%");
        } else {
            addObjectiveLine(lines, "Travel State: Idle and ready to depart");
        }
        addObjectiveLine(lines, "Enemy Alert: " + threatReadout((float) MathUtil.clamp(st.enemyAlertLevel / 100.0, 0.0, 1.0)));
        if (expanded) {
            addObjectiveLine(lines, "Controls: arrows pan camera north-south  |  LMB select destination  |  Double-click or T to travel  |  RMB ping");
            addObjectiveLine(lines, "Map Rule: This is a large strategic chart; only part of the route home is visible at once.");
            addObjectiveLine(lines, "Progression: Southern frontier below, contested mid-map ahead, Earth approach far to the north.");
            addObjectiveLine(lines, "Encounter Rule: Bases, hubs, and risky routes transition into one large tactical sector.");
        } else {
            addObjectiveLine(lines, "Task: Move north toward Earth, weigh safe hubs against risky detours, and plan the next stop.");
        }
        return String.join("\n", lines);
    }

    public static int livePersistentFleetCount(GameContext ctx) {
        CampaignState st = state(ctx);
        return livePersistentFleetSlots(st);
    }

    static int livePersistentFleetCount(GameContext ctx, ShopHullCategory category) {
        return livePersistentFleetSlots(state(ctx), category);
    }

    static int persistentFleetBaseCap(ShopHullCategory category) {
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        return switch (resolved) {
            case ESCORT -> CAMPAIGN_BLUE_ESCORT_CAP;
            case LINE -> CAMPAIGN_BLUE_LINE_CAP;
            case CAPITAL -> CAMPAIGN_BLUE_CAPITAL_CAP;
            case TITAN -> TitanFleetSystem.mothershipTitanCap();
        };
    }

    static int persistentFleetCap(ShopHullCategory category) {
        return persistentFleetBaseCap(category);
    }

    static int persistentFleetCap(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCap(state(ctx), category);
    }

    static int persistentFleetCap(CampaignState st, ShopHullCategory category) {
        return persistentFleetBaseCap(category);
    }

    static String persistentFleetCompactSummary(GameContext ctx) {
        int escort = livePersistentFleetCount(ctx, ShopHullCategory.ESCORT);
        int line = livePersistentFleetCount(ctx, ShopHullCategory.LINE);
        int capital = livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL);
        int titan = livePersistentFleetCount(ctx, ShopHullCategory.TITAN);
        return "E" + escort
                + " L" + line
                + " C" + capital
                + " T" + titan + "/" + persistentFleetCap(ctx, ShopHullCategory.TITAN);
    }

    private static int standardCommandCapacity(CampaignState st) {
        int capacity = CAMPAIGN_FLAGSHIP_STANDARD_COMMAND_CAPACITY;
        if (st == null) return capacity;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            TitanArchetype titan = (entry == null || entry.destroyed) ? null : TitanArchetype.fromShipRole(entry.role);
            if (titan == null) continue;
            capacity += titan.standardShipCommandCapacity();
        }
        return Math.max(0, capacity);
    }

    private static int standardCommandUsed(CampaignState st) {
        if (st == null) return 0;
        int used = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            used += campaignStandardCommandCost(entry.role);
        }
        return Math.max(0, used);
    }

    private static int eliteCommandCapacity(GameContext ctx, CampaignState st) {
        if (st == null) return 0;
        int capacity = flagshipEliteCommandCapacity(ctx, st);
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            TitanArchetype titan = (entry == null || entry.destroyed) ? null : TitanArchetype.fromShipRole(entry.role);
            if (titan == null) continue;
            capacity += titan.eliteSupershipCommandCapacity();
        }
        return Math.max(0, capacity);
    }

    private static int flagshipEliteCommandCapacity(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || !st.enabled || ctx.player == null) return 0;
        if (st.sector < CAMPAIGN_SUPERSHIP_UNLOCK_SECTOR) return 0;
        BaseUpgrades upgrades = ctx.baseUpgrades.get(ctx.player);
        int hangarTier = (upgrades == null) ? 0 : upgrades.hangarLv;
        if (hangarTier < CAMPAIGN_SUPERSHIP_FLAGSHIP_BERTH_TIER) return 0;
        return CAMPAIGN_FLAGSHIP_BUILTIN_ELITE_COMMAND_CAPACITY;
    }

    private static int eliteCommandUsed(CampaignState st) {
        if (st == null) return 0;
        int used = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            used += campaignEliteCommandCost(entry.role);
        }
        return Math.max(0, used);
    }

    private static boolean hasOperationalRole(CampaignState st, ShipRole role) {
        if (st == null || role == null) return false;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (entry.role == role) return true;
        }
        return false;
    }

    public static String[] activeModifierLabels(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || st.activeModifiers == null) return new String[0];
        String[] out = new String[st.activeModifiers.length];
        int n = 0;
        for (MapModifier m : st.activeModifiers) {
            if (m == null || m == MapModifier.NONE) continue;
            out[n++] = m.label;
        }
        if (n == out.length) return out;
        String[] trim = new String[n];
        System.arraycopy(out, 0, trim, 0, n);
        return trim;
    }

    public static Color worldTint(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || st.activeModifiers == null) return new Color(0, 0, 0, 0);
        int r = 0, g = 0, b = 0, a = 0;
        for (MapModifier m : st.activeModifiers) {
            Color t = tintFor(m);
            if (t == null) continue;
            r += t.getRed();
            g += t.getGreen();
            b += t.getBlue();
            a += t.getAlpha();
        }
        if (a <= 0) return new Color(0, 0, 0, 0);
        int n = Math.max(1, st.activeModifiers.length);
        return new Color(
                MathUtil.clamp(r / n, 0, 255),
                MathUtil.clamp(g / n, 0, 255),
                MathUtil.clamp(b / n, 0, 255),
                MathUtil.clamp(a / n, 0, 90)
        );
    }

    private static CampaignState state(GameContext ctx) {
        if (ctx == null) return null;
        return ctx.campaign;
    }

    private static List<CampaignLocation> allCampaignLocations(CampaignState st) {
        if (st == null) return List.of();
        ArrayList<CampaignLocation> out = new ArrayList<>(st.galaxyMainPois.size() + st.galaxyAreasOfInterest.size());
        out.addAll(st.galaxyMainPois);
        out.addAll(st.galaxyAreasOfInterest);
        return out;
    }

    private static CampaignLocation campaignLocationById(CampaignState st, String id) {
        if (st == null || id == null || id.isBlank()) return null;
        for (CampaignLocation location : allCampaignLocations(st)) {
            if (location != null && id.equalsIgnoreCase(location.id)) return location;
        }
        return null;
    }

    private static void initializeStrategicTaskForces(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        st.strategicTaskForces.clear();
        st.strategicDivisions.clear();
        st.strategicTorpedoCharges = 2 + ((st.sector >= 8) ? 1 : 0);
        st.strategicSortiesLaunched = 0;
        st.strategicAtomicCharges = strategicAtomicCapacity(ctx);
        int playerSubzone = currentLoadedMissionSubzone(ctx);
        if (playerSubzone < 0) playerSubzone = missionSubzoneIndex(0, 1);
        initializeStrategicDivisions(ctx, st, playerSubzone);

        int patrolSubzone = farthestSubzoneFrom(playerSubzone, Set.of());
        int strikeSubzone = farthestSubzoneFrom(playerSubzone, Set.of(patrolSubzone));
        int stealthSubzone = flankSubzone(playerSubzone, Set.of(patrolSubzone, strikeSubzone));
        int convoySubzone = supportSubzoneNear(playerSubzone, Set.of(patrolSubzone, strikeSubzone, stealthSubzone), true);
        int salvageSubzone = supportSubzoneNear(playerSubzone, Set.of(patrolSubzone, strikeSubzone, stealthSubzone, convoySubzone), false);

        addStrategicTaskForce(st, StrategicTaskForceKind.PATROL, Faction.ENEMY,
                "Red Patrol Group", true, true, SupportMarkerType.HAZARD, patrolSubzone, 8.0);
        addStrategicTaskForce(st, StrategicTaskForceKind.STRIKE, Faction.ENEMY,
                "Red Strike Group", true, true, SupportMarkerType.HAZARD, strikeSubzone, 12.0);
        if (st.sector >= 4) {
            addStrategicTaskForce(st, StrategicTaskForceKind.CONVOY, greenSupportFaction(st),
                    "Coalition Supply Convoy", false, false, SupportMarkerType.RESOURCE, convoySubzone, 14.0);
        }
        if (st.sector >= 6) {
            addStrategicTaskForce(st, StrategicTaskForceKind.STEALTH, Faction.ENEMY,
                    "Knife Raider Cell", true, true, SupportMarkerType.HAZARD, stealthSubzone, 10.0);
        }
        if (st.sector >= 3) {
            addStrategicTaskForce(st, StrategicTaskForceKind.SALVAGE, greenSupportFaction(st),
                    "Recovery Flotilla", false, false, SupportMarkerType.SALVAGE, salvageSubzone, 16.0);
        }
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce == null) continue;
            taskForce.maxStrength = rawStrategicTaskForceThreat(taskForce.kind, st.sector);
            taskForce.currentStrength = taskForce.maxStrength;
        }
    }

    private static StrategicTaskForce addStrategicTaskForce(CampaignState st,
                                                            StrategicTaskForceKind kind,
                                                            Faction faction,
                                                            String label,
                                                            boolean hostile,
                                                            boolean spawnsEncounter,
                                                            SupportMarkerType markerType,
                                                            int subzone,
                                                            double dwellSeconds) {
        if (st == null || subzone < 0) return null;
        StrategicTaskForce taskForce = new StrategicTaskForce(
                st.nextStrategicTaskForceId++,
                kind,
                faction,
                label,
                hostile,
                spawnsEncounter,
                markerType,
                subzone,
                dwellSeconds);
        st.strategicTaskForces.add(taskForce);
        return taskForce;
    }

    private static void updateStrategicTaskForces(GameContext ctx, CampaignState st, double dt) {
        if (ctx == null || st == null || st.strategicTaskForces.isEmpty() || dt <= 0.0) return;
        int playerSubzone = currentLoadedMissionSubzone(ctx);
        if (playerSubzone < 0 && ctx.player != null) {
            playerSubzone = missionSubzoneForPoint(ctx, st.sector, ctx.player.x, ctx.player.y);
        }
        updateStrategicDivisions(ctx, st, playerSubzone, dt);
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce == null || taskForce.encounterResolved) continue;
            updateStrategicTaskForceMovement(ctx, st, taskForce, playerSubzone, dt);
            updateStrategicStealthOperations(ctx, st, taskForce, playerSubzone);
            updateStrategicTaskForceEncounter(ctx, st, taskForce, playerSubzone);
        }
    }

    private static void updateStrategicTaskForceMovement(GameContext ctx, CampaignState st,
                                                         StrategicTaskForce taskForce,
                                                         int playerSubzone,
                                                         double dt) {
        if (ctx == null || st == null || taskForce == null) return;
        if (taskForce.encounterSpawned && !taskForce.encounterResolved) return;
        taskForce.disruptionRemainingSec = Math.max(0.0, taskForce.disruptionRemainingSec - dt);
        taskForce.breakoffRemainingSec = Math.max(0.0, taskForce.breakoffRemainingSec - dt);
        if (taskForce.disruptionRemainingSec > 1e-6) {
            taskForce.dwellRemainingSec = Math.max(taskForce.dwellRemainingSec, 4.0);
            return;
        }
        if (taskForce.transitRemainingSec > 0.0) {
            taskForce.transitRemainingSec = Math.max(0.0, taskForce.transitRemainingSec - dt);
            if (taskForce.transitRemainingSec <= 1e-6) {
                taskForce.currentSubzone = taskForce.targetSubzone;
                taskForce.dwellRemainingSec = 6.0 + ctx.rng.nextDouble() * 8.0;
            }
            return;
        }
        taskForce.dwellRemainingSec = Math.max(0.0, taskForce.dwellRemainingSec - dt);
        if (taskForce.dwellRemainingSec > 1e-6) return;

        List<Integer> neighbors = strategicNeighborSubzones(taskForce.currentSubzone);
        if (neighbors.isEmpty()) return;
        int next = chooseStrategicNextSubzone(ctx, taskForce, neighbors, playerSubzone);
        if (next < 0 || next == taskForce.currentSubzone) {
            taskForce.dwellRemainingSec = 5.0 + ctx.rng.nextDouble() * 6.0;
            return;
        }
        taskForce.targetSubzone = next;
        taskForce.transitRemainingSec = strategicTransitSeconds(taskForce, taskForce.currentSubzone, next);
    }

    private static void updateStrategicTaskForceEncounter(GameContext ctx, CampaignState st,
                                                          StrategicTaskForce taskForce,
                                                          int playerSubzone) {
        if (ctx == null || st == null || taskForce == null || !taskForce.hostile || !taskForce.spawnsEncounter) return;
        if (playerSubzone < 0 || taskForce.currentSubzone != playerSubzone) return;
        if (taskForce.kind == StrategicTaskForceKind.STEALTH) {
            double enemyStrength = strategicTaskForceThreat(taskForce, st.sector);
            double friendlyStrength = strategicFriendlyStrength(ctx, st, playerSubzone);
            if (taskForce.breakoffRemainingSec > 0.0 || friendlyStrength > enemyStrength * 1.6 || enemyStrength < 28.0) {
                taskForce.breakoffRemainingSec = Math.max(taskForce.breakoffRemainingSec, 22.0);
                taskForce.dwellRemainingSec = 0.0;
                EventSystem.showBanner(ctx, "RAIDER CELL CLOAKS AND BREAKS CONTACT", 1.3);
                return;
            }
        }
        if (!taskForce.encounterSpawned) {
            if (!hasPendingStrategicEncounterChoice(ctx)
                    || ctx.ui.strategicEncounterPrompt.taskForceId != taskForce.id) {
                beginStrategicEncounterChoice(ctx, st, taskForce, playerSubzone);
            }
            taskForce.transitRemainingSec = 0.0;
            taskForce.dwellRemainingSec = 999.0;
            return;
        }
        pruneStrategicTaskForceShips(ctx, taskForce);
        if (taskForce.spawnedShipIds.isEmpty()) {
            taskForce.encounterResolved = true;
            EventSystem.showBanner(ctx, "CONTACT CLEARED: " + taskForce.label.toUpperCase(Locale.US), 1.4);
        }
    }

    private static void pruneStrategicTaskForceShips(GameContext ctx, StrategicTaskForce taskForce) {
        if (ctx == null || taskForce == null || taskForce.spawnedShipIds.isEmpty()) return;
        taskForce.spawnedShipIds.removeIf(shipId -> {
            Ship ship = findShipById(ctx, shipId);
            return ship == null || !ship.alive || ship.dying || ship.hp <= 0;
        });
    }

    private static void updateStrategicStealthOperations(GameContext ctx, CampaignState st,
                                                         StrategicTaskForce taskForce,
                                                         int playerSubzone) {
        if (ctx == null || st == null || taskForce == null) return;
        if (taskForce.kind != StrategicTaskForceKind.STEALTH) return;
        if (taskForce.encounterResolved || taskForce.encounterSpawned) return;
        if (taskForce.currentSubzone < 0 || taskForce.currentSubzone == playerSubzone) return;
        if (taskForce.breakoffRemainingSec > 0.0) return;
        if (taskForce.dwellRemainingSec > 2.0 || taskForce.transitRemainingSec > 0.0) return;

        StrategicTaskForce victim = friendlyStrategicVictimInSubzone(st, taskForce.currentSubzone);
        if (victim != null) {
            victim.currentStrength = Math.max(0.0, victim.currentStrength - victim.maxStrength * 0.55);
            victim.disruptionRemainingSec = Math.max(victim.disruptionRemainingSec, 18.0);
            if (victim.currentStrength <= 1.0) {
                victim.encounterResolved = true;
                victim.encounterSpawned = false;
                victim.spawnedShipIds.clear();
            }
            taskForce.breakoffRemainingSec = 20.0;
            taskForce.dwellRemainingSec = 0.0;
            EventSystem.showBanner(ctx, "GHOST RAID: " + victim.label.toUpperCase(Locale.US) + " HIT", 1.4);
            return;
        }

        Ship asset = highValueShipInSubzone(ctx, st, taskForce.currentSubzone);
        if (asset != null) {
            int damage = Math.max(10, (int) Math.round(asset.hpMax * 0.18));
            asset.takeDamage(damage, asset.x, asset.y);
            taskForce.breakoffRemainingSec = 24.0;
            taskForce.dwellRemainingSec = 0.0;
            EventSystem.showBanner(ctx, "RAIDER STRIKE: " + displayShipName(asset, "ASSET").toUpperCase(Locale.US), 1.5);
            return;
        }

        RecoverableWreckSite wreck = recoverableWreckInSubzone(ctx, st, taskForce.currentSubzone);
        if (wreck != null) {
            wreck.claimed = true;
            taskForce.breakoffRemainingSec = 18.0;
            taskForce.dwellRemainingSec = 0.0;
            EventSystem.showBanner(ctx, "RAIDER CELL SCUTTLED A RECOVERY SITE", 1.4);
        }
    }

    private static void initializeStrategicDivisions(GameContext ctx, CampaignState st, int playerSubzone) {
        if (ctx == null || st == null) return;
        rebalancePersistentCommandGroups(st);
        StrategicDivisionState flagship = new StrategicDivisionState(
                CAMPAIGN_FLAGSHIP_COMMAND_GROUP,
                DivisionStance.RESERVE,
                playerSubzone);
        st.strategicDivisions.put(flagship.groupId, flagship);

        java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            int groupId = Math.max(CAMPAIGN_FLAGSHIP_COMMAND_GROUP, entry.commandGroupId);
            if (!seen.add(groupId) || groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) continue;
            st.strategicDivisions.put(groupId, new StrategicDivisionState(groupId, defaultDivisionStance(entry), playerSubzone));
        }
        if (ctx.ui != null) {
            ctx.ui.selectedStrategicDivisionGroupId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
        }
    }

    private static void updateStrategicDivisions(GameContext ctx, CampaignState st, int playerSubzone, double dt) {
        if (ctx == null || st == null || st.strategicDivisions.isEmpty()) return;
        for (StrategicDivisionState division : st.strategicDivisions.values()) {
            if (division == null) continue;
            if (division.groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) {
                division.currentSubzone = playerSubzone;
                division.targetSubzone = playerSubzone;
                division.transitRemainingSec = 0.0;
                continue;
            }
            if (division.transitRemainingSec > 0.0) {
                division.transitRemainingSec = Math.max(0.0, division.transitRemainingSec - Math.max(0.0, dt));
                if (division.transitRemainingSec <= 1e-6) {
                    division.currentSubzone = division.targetSubzone;
                }
            }
        }
    }

    private static void spawnStrategicTaskForceEncounter(GameContext ctx, CampaignState st, StrategicTaskForce taskForce) {
        if (ctx == null || st == null || taskForce == null) return;
        if (taskForce.currentStrength <= 1.0) {
            taskForce.encounterResolved = true;
            return;
        }
        double strengthFrac = (taskForce.maxStrength <= 1e-6)
                ? 1.0
                : MathUtil.clamp(taskForce.currentStrength / taskForce.maxStrength, 0.0, 1.0);
        double cx = missionSubzoneCenterX(ctx, st.sector, taskForce.currentSubzone);
        double cy = missionSubzoneCenterY(ctx, st.sector, taskForce.currentSubzone);
        switch (taskForce.kind) {
            case PATROL -> {
                if (strengthFrac > 0.34) {
                    strategicSpawn(ctx, taskForce, ShipRole.FRIGATE, taskForce.faction, cx + 100.0, cy - 70.0, taskForce.label + " Frigate");
                }
                if (strengthFrac > 0.18) {
                    strategicSpawn(ctx, taskForce, ShipRole.PICKET, taskForce.faction, cx - 120.0, cy + 80.0, "Patrol Screen");
                }
                if (st.sector >= 7 && strengthFrac > 0.76) {
                    strategicSpawn(ctx, taskForce, ShipRole.MISSILE_BOAT, taskForce.faction, cx + 160.0, cy + 110.0, "Patrol Missile Boat");
                }
            }
            case STRIKE -> {
                ShipRole spearhead = (st.sector >= 10) ? ShipRole.BATTLECRUISER : ShipRole.LIGHT_CRUISER;
                if (strengthFrac > 0.42) {
                    strategicSpawn(ctx, taskForce, spearhead, taskForce.faction, cx + 80.0, cy - 40.0, taskForce.label + " Spearhead");
                }
                if (strengthFrac > 0.20) {
                    strategicSpawn(ctx, taskForce, ShipRole.FRIGATE, taskForce.faction, cx - 150.0, cy + 90.0, "Strike Escort");
                }
                if (strengthFrac > 0.28) {
                    strategicSpawn(ctx, taskForce, ShipRole.CIWS_CORVETTE, taskForce.faction, cx + 170.0, cy + 120.0, "Strike Flak Screen");
                }
            }
            case STEALTH -> {
                if (strengthFrac > 0.24) {
                    strategicSpawn(ctx, taskForce, ShipRole.STEALTH_SHIP, taskForce.faction, cx + 60.0, cy - 30.0, taskForce.label);
                }
                if (strengthFrac > 0.16) {
                    strategicSpawn(ctx, taskForce, ShipRole.PICKET, taskForce.faction, cx - 140.0, cy + 90.0, "Raider Screen");
                }
            }
            case CONVOY -> {
                if (strengthFrac > 0.18) {
                    strategicSpawn(ctx, taskForce, ShipRole.TRANSPORT, taskForce.faction, cx, cy, taskForce.label);
                }
                if (strengthFrac > 0.12) {
                    strategicSpawn(ctx, taskForce, ShipRole.PATROL, taskForce.faction, cx + 110.0, cy - 70.0, "Convoy Escort");
                }
            }
            case SALVAGE -> {
                if (strengthFrac > 0.18) {
                    strategicSpawn(ctx, taskForce, ShipRole.HAULER, taskForce.faction, cx, cy, taskForce.label);
                }
                if (strengthFrac > 0.12) {
                    strategicSpawn(ctx, taskForce, ShipRole.PICKET, taskForce.faction, cx - 90.0, cy + 60.0, "Recovery Escort");
                }
            }
        }
        snapshotHostiles(ctx, st.knownHostiles);
    }

    public static boolean takeCommandOfPendingStrategicEncounter(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !hasPendingStrategicEncounterChoice(ctx)) return false;
        if (ctx.ui.strategicEncounterPrompt.kind == UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION) {
            CampaignLocation location = campaignLocationById(st, ctx.ui.strategicEncounterPrompt.campaignLocationId);
            if (location == null || location.completed) {
                ctx.ui.clearStrategicEncounterPrompt();
                ctx.state = GameState.RUNNING;
                return false;
            }
            return launchCampaignLocationEncounter(ctx, st, location);
        }
        StrategicTaskForce taskForce = strategicTaskForceById(st, ctx.ui.strategicEncounterPrompt.taskForceId);
        if (taskForce == null || taskForce.encounterResolved) {
            ctx.ui.clearStrategicEncounterPrompt();
            ctx.state = GameState.RUNNING;
            return false;
        }
        if (!taskForce.encounterSpawned) {
            spawnStrategicTaskForceEncounter(ctx, st, taskForce);
            taskForce.encounterSpawned = true;
            taskForce.transitRemainingSec = 0.0;
            taskForce.dwellRemainingSec = 999.0;
        }
        ctx.ui.clearStrategicEncounterPrompt();
        ctx.state = GameState.RUNNING;
        EventSystem.showBanner(ctx, "TAKE COMMAND: " + taskForce.label.toUpperCase(Locale.US), 1.5);
        return true;
    }

    public static boolean autoResolvePendingStrategicEncounter(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !hasPendingStrategicEncounterChoice(ctx)) return false;
        if (ctx.ui.strategicEncounterPrompt.kind == UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION) {
            CampaignLocation location = campaignLocationById(st, ctx.ui.strategicEncounterPrompt.campaignLocationId);
            if (location == null || location.completed) {
                ctx.ui.clearStrategicEncounterPrompt();
                ctx.state = GameState.RUNNING;
                return false;
            }
            return autoResolveCampaignLocationEncounter(ctx, st, location);
        }
        StrategicTaskForce taskForce = strategicTaskForceById(st, ctx.ui.strategicEncounterPrompt.taskForceId);
        if (taskForce == null || taskForce.encounterResolved) {
            ctx.ui.clearStrategicEncounterPrompt();
            ctx.state = GameState.RUNNING;
            return false;
        }

        int playerSubzone = currentLoadedMissionSubzone(ctx);
        if (playerSubzone < 0 && ctx.player != null) {
            playerSubzone = missionSubzoneForPoint(ctx, st.sector, ctx.player.x, ctx.player.y);
        }

        List<Ship> friendlies = liveFriendlyShipsInSubzone(ctx, playerSubzone);
        double friendlyStrength = strategicFriendlyStrength(ctx, st, playerSubzone);
        double enemyStrength = strategicTaskForceThreat(taskForce, st.sector);
        double ratio = friendlyStrength / Math.max(30.0, enemyStrength);

        applyStrategicAutoResolveDamage(ctx, friendlies, ratio, enemyStrength);
        syncPersistentFleetCasualties(ctx, st);
        syncPersistentFleetEntrySnapshots(ctx, st);

        taskForce.encounterResolved = true;
        taskForce.encounterSpawned = false;
        taskForce.spawnedShipIds.clear();
        taskForce.transitRemainingSec = 0.0;
        taskForce.dwellRemainingSec = 999.0;

        ctx.ui.clearStrategicEncounterPrompt();
        ctx.state = GameState.RUNNING;
        if (ratio >= 1.25) {
            EventSystem.showBanner(ctx, "AUTO-RESOLVE: DECISIVE VICTORY", 1.6);
        } else if (ratio >= 0.9) {
            EventSystem.showBanner(ctx, "AUTO-RESOLVE: CONTACT CLEARED WITH DAMAGE", 1.7);
        } else {
            EventSystem.showBanner(ctx, "AUTO-RESOLVE: PYRRHIC VICTORY", 1.7);
        }
        return true;
    }

    private static void beginStrategicEncounterChoice(GameContext ctx, CampaignState st,
                                                      StrategicTaskForce taskForce, int playerSubzone) {
        if (ctx == null || st == null || taskForce == null || ctx.ui == null) return;
        List<Ship> friendlies = liveFriendlyShipsInSubzone(ctx, playerSubzone);
        double friendlyStrength = strategicFriendlyStrength(ctx, st, playerSubzone);
        double enemyStrength = strategicTaskForceThreat(taskForce, st.sector);
        ctx.ui.showStrategicEncounterPrompt(
                taskForce.id,
                "CONTACT: " + taskForce.label.toUpperCase(Locale.US),
                strategicEncounterBody(taskForce),
                missionSubzoneLabel(taskForce.currentSubzone),
                "Fleet " + Math.round(friendlyStrength) + "  Enemy " + Math.round(enemyStrength));
        ctx.state = GameState.PAUSED;
        EventSystem.showBanner(ctx, "CONTACT REPORT: " + taskForce.label.toUpperCase(Locale.US), 1.4);
    }

    private static StrategicTaskForce strategicTaskForceById(CampaignState st, int taskForceId) {
        if (st == null || taskForceId <= 0) return null;
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce != null && taskForce.id == taskForceId) return taskForce;
        }
        return null;
    }

    private static String strategicEncounterBody(StrategicTaskForce taskForce) {
        if (taskForce == null) return "Unknown contact report.";
        return switch (taskForce.kind) {
            case PATROL -> "Enemy patrol pocket detected. Auto-resolve for speed, or take command to fight the intercept yourself.";
            case STRIKE -> "Heavy strike group moving in strength. Direct command gives your formation space to work with.";
            case STEALTH -> "Stealth raider contact. Manual command is safer if you want to protect key ships and screen the flank.";
            case CONVOY -> "Convoy contact detected.";
            case SALVAGE -> "Salvage detachment detected.";
        };
    }

    private static boolean launchCampaignLocationEncounter(GameContext ctx, CampaignState st, CampaignLocation location) {
        if (ctx == null || st == null || location == null) return false;
        st.activeGalaxyEncounterLocationId = location.id;
        st.galaxyEncounterActive = true;
        st.strategicOvermapMode = false;
        st.sector = Math.max(1, location.missionIndex);
        if (ctx.ui != null) {
            ctx.ui.clearStrategicEncounterPrompt();
        }
        ctx.state = GameState.RUNNING;
        EventSystem.showBanner(ctx, "TAKE COMMAND: " + location.name.toUpperCase(Locale.US), 1.5);
        startSector(ctx, st.sector);
        return true;
    }

    private static boolean autoResolveCampaignLocationEncounter(GameContext ctx, CampaignState st, CampaignLocation location) {
        if (ctx == null || st == null || location == null) return false;
        double fleetStrength = galaxyCampaignFleetStrength(ctx, st);
        double threat = campaignLocationThreatValue(st, location);
        double ratio = fleetStrength / Math.max(45.0, threat);
        applyGalaxyAutoResolveWear(ctx, st, ratio, location);
        syncPersistentFleetCasualties(ctx, st);
        syncPersistentFleetEntrySnapshots(ctx, st);
        markCampaignLocationCompleted(st, location);
        st.enemyAlertLevel = MathUtil.clamp(
                st.enemyAlertLevel + 4.0 + location.threatLevel * 10.0f,
                0.0,
                100.0);
        if (ctx.ui != null) {
            ctx.ui.clearStrategicEncounterPrompt();
        }
        ctx.state = GameState.RUNNING;
        String outcome = (ratio >= 1.20) ? "AUTO-RESOLVE: MISSION SECURED"
                : (ratio >= 0.9) ? "AUTO-RESOLVE: COSTLY VICTORY"
                : "AUTO-RESOLVE: PYRRHIC SUCCESS";
        EventSystem.showBanner(ctx, outcome + "  " + location.name.toUpperCase(Locale.US), 1.7);
        if (st.completedMainMissions >= st.galaxyMainPois.size()) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;
            finalizeCampaignOutcome(ctx, st);
            persistRunResult(ctx, true);
            return true;
        }
        st.strategicOvermapMode = true;
        startSector(ctx, Math.max(1, st.sector));
        return true;
    }

    private static StrategicTaskForce nearestHostileStrategicTaskForce(GameContext ctx, double x, double y, double maxDist) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || st.strategicTaskForces.isEmpty()) return null;
        StrategicTaskForce best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce == null || !taskForce.hostile || taskForce.encounterResolved || taskForce.currentSubzone < 0) continue;
            double tx = missionSubzoneCenterX(ctx, st.sector, taskForce.currentSubzone);
            double ty = missionSubzoneCenterY(ctx, st.sector, taskForce.currentSubzone);
            double d2 = GameMath.dist2(x, y, tx, ty);
            if (d2 < bestD2) {
                best = taskForce;
                bestD2 = d2;
            }
        }
        return best;
    }

    private static void resolveStrategicTaskForceAfterRemoteStrike(GameContext ctx, CampaignState st,
                                                                   StrategicTaskForce taskForce,
                                                                   String bannerPrefix) {
        if (ctx == null || st == null || taskForce == null) return;
        if (taskForce.currentStrength <= 1.0) {
            taskForce.currentStrength = 0.0;
            taskForce.encounterResolved = true;
            taskForce.encounterSpawned = false;
            taskForce.spawnedShipIds.clear();
            if (hasPendingStrategicEncounterChoice(ctx)
                    && ctx.ui.strategicEncounterPrompt.taskForceId == taskForce.id) {
                ctx.ui.clearStrategicEncounterPrompt();
                ctx.state = GameState.RUNNING;
            }
            EventSystem.showBanner(ctx, bannerPrefix + ": " + taskForce.label.toUpperCase(Locale.US) + " DESTROYED", 1.5);
            return;
        }
        EventSystem.showBanner(ctx, bannerPrefix + ": " + taskForce.label.toUpperCase(Locale.US), 1.4);
    }

    private static List<Ship> liveFriendlyShipsInSubzone(GameContext ctx, int subzone) {
        ArrayList<Ship> out = new ArrayList<>();
        if (ctx == null || ctx.ships == null || ctx.player == null || subzone < 0) return out;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || ctx.player.faction == null || !ship.faction.isFriendlyTo(ctx.player.faction)) continue;
            if (ensureShipMissionSubzone(ctx, ship) == subzone) out.add(ship);
        }
        return out;
    }

    private static double strategicFriendlyStrength(List<Ship> friendlies) {
        if (friendlies != null && !friendlies.isEmpty()) {
            double total = 0.0;
            for (Ship ship : friendlies) total += strategicShipStrength(ship);
            return total;
        }
        return 0.0;
    }

    private static double strategicFriendlyStrength(GameContext ctx, CampaignState st, int subzone) {
        if (ctx == null || st == null || subzone < 0) return 0.0;
        if (!st.strategicDivisions.isEmpty()) {
            double total = 0.0;
            for (StrategicDivisionState division : st.strategicDivisions.values()) {
                if (division == null || division.currentSubzone != subzone) continue;
                double stanceMul = switch (division.stance) {
                    case STRIKE -> 1.08;
                    case ESCORT -> 0.96;
                    case SCOUT -> 0.84;
                    case RESERVE -> 1.00;
                    case LINE -> 1.0;
                };
                total += strategicDivisionStrength(st, division.groupId) * stanceMul;
            }
            return total;
        }
        return strategicFriendlyStrength(liveFriendlyShipsInSubzone(ctx, subzone));
    }

    private static double galaxyCampaignFleetStrength(GameContext ctx, CampaignState st) {
        if (st == null) return 0.0;
        double total = 0.0;
        if (ctx != null && ctx.player != null && ctx.player.alive && !ctx.player.dying && ctx.player.hp > 0) {
            total += strategicShipStrength(ctx.player);
        }
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.role == null) continue;
            total += persistentEntryStrength(entry);
        }
        return total;
    }

    private static double persistentEntryStrength(PersistentFleetEntry entry) {
        if (entry == null || entry.destroyed || entry.role == null) return 0.0;
        ShipRole role = entry.role;
        return switch (role) {
            case FIGHTER, DRONE, BOMBER, PATROL, PICKET -> 16.0;
            case FRIGATE, CIWS_CORVETTE, MISSILE_BOAT, MINER, HAULER, TRANSPORT -> 28.0;
            case ARTILLERY_SHIP, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, STEALTH_SHIP -> 46.0;
            case BATTLECRUISER, BATTLESHIP, CARRIER, DRONE_CARRIER, DREADNOUGHT -> 82.0;
            case SUPERSHIP -> 120.0;
            default -> role.isTitanOrMothership() ? 150.0 : 36.0;
        };
    }

    private static double campaignLocationThreatValue(CampaignState st, CampaignLocation location) {
        if (location == null) return 0.0;
        double base = 42.0 + Math.max(0.0, location.threatLevel) * 95.0;
        double progressPressure = (st == null) ? 0.0 : st.earthProgress * 110.0;
        double missionPressure = location.primaryMission ? 18.0 + Math.max(0, location.missionIndex - 1) * 4.0 : 0.0;
        return base + progressPressure + missionPressure;
    }

    private static void applyGalaxyAutoResolveWear(GameContext ctx, CampaignState st, double ratio, CampaignLocation location) {
        if (ctx == null || st == null) return;
        double severity = (ratio >= 1.20) ? 0.06 : (ratio >= 0.9 ? 0.14 : 0.26);
        if (ctx.player != null && ctx.player.alive && !ctx.player.dying && ctx.player.hp > 0) {
            int damage = Math.max(1, (int) Math.round(ctx.player.hpMax * severity * 0.85));
            ctx.player.takeDamage(damage, ctx.player.x, ctx.player.y);
        }
        PersistentFleetEntry weakest = null;
        double weakestStrength = Double.POSITIVE_INFINITY;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            double entryStrength = persistentEntryStrength(entry);
            if (entryStrength <= 0.0) continue;
            if (entryStrength < weakestStrength) {
                weakestStrength = entryStrength;
                weakest = entry;
            }
        }
        if (ratio < 0.82 && weakest != null) {
            weakest.destroyed = true;
        }
        if (location != null && location.primaryMission) {
            int credits = GameContext.scaleCreditEarnings(120 + (int) Math.round(location.threatLevel * 90.0f));
            ctx.credits += credits;
        }
    }

    private static void markCampaignLocationCompleted(CampaignState st, CampaignLocation location) {
        if (st == null || location == null) return;
        location.completed = true;
        location.consumed = true;
        if (location.primaryMission) {
            int completeCount = 0;
            for (CampaignLocation poi : st.galaxyMainPois) {
                if (poi != null && poi.completed) completeCount++;
            }
            st.completedMainMissions = completeCount;
            st.earthProgress = MathUtil.clamp(
                    st.galaxyMainPois.isEmpty() ? 0.0 : completeCount / (double) st.galaxyMainPois.size(),
                    0.0,
                    1.0);
        }
    }

    private static double strategicShipStrength(Ship ship) {
        if (ship == null || ship.role == null || !ship.alive || ship.dying || ship.hp <= 0) return 0.0;
        double base = switch (ship.role) {
            case FIGHTER, DRONE, BOMBER, PATROL, PICKET -> 16.0;
            case FRIGATE, CIWS_CORVETTE, MISSILE_BOAT, MINER, HAULER, TRANSPORT -> 28.0;
            case ARTILLERY_SHIP, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, STEALTH_SHIP -> 46.0;
            case BATTLECRUISER, BATTLESHIP, CARRIER, DRONE_CARRIER, DREADNOUGHT -> 82.0;
            case SUPERSHIP -> 120.0;
            default -> ship.role.isTitanOrMothership() ? 150.0 : 36.0;
        };
        double hullFrac = (ship.hpMax <= 0) ? 0.0 : MathUtil.clamp(ship.hp / (double) ship.hpMax, 0.0, 1.0);
        double shieldFrac = (ship.shieldMax <= 0.0) ? 1.0 : MathUtil.clamp(ship.shield / ship.shieldMax, 0.0, 1.0);
        return base * Math.max(0.18, hullFrac) * (0.75 + shieldFrac * 0.25);
    }

    private static double strategicTaskForceThreat(StrategicTaskForce taskForce, int sector) {
        if (taskForce == null) return 0.0;
        double base = rawStrategicTaskForceThreat(taskForce.kind, sector);
        if (taskForce.maxStrength <= 1e-6) return base;
        return base * MathUtil.clamp(taskForce.currentStrength / taskForce.maxStrength, 0.0, 1.0);
    }

    private static double rawStrategicTaskForceThreat(StrategicTaskForceKind kind, int sector) {
        int stage = Math.max(1, sector);
        StrategicTaskForceKind resolved = (kind == null) ? StrategicTaskForceKind.PATROL : kind;
        return switch (resolved) {
            case PATROL -> 48.0 + stage * 3.0;
            case STRIKE -> 92.0 + stage * 5.0;
            case STEALTH -> 66.0 + stage * 4.0;
            case CONVOY -> 28.0 + stage * 2.0;
            case SALVAGE -> 24.0 + stage * 2.0;
        };
    }

    private static int strategicSortieCapacity(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null) return 0;
        int carriers = 0;
        if (ctx.player != null && ctx.player.alive && !ctx.player.dying && ctx.player.isCarrier) carriers++;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.role == null) continue;
            if (entry.role.isCarrierHull() || entry.role == ShipRole.CARRIER_SUPPORT_TITAN) carriers++;
        }
        return Math.max(0, carriers * 2);
    }

    private static int strategicAtomicCapacity(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null) return 0;
        boolean available = false;
        if (ctx.player != null && ctx.player.alive && !ctx.player.dying && ctx.player.hasSuperweapon) {
            available = true;
        }
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (entry.role == ShipRole.HYPERWEAPON_TITAN) {
                available = true;
                break;
            }
        }
        return available ? 1 : 0;
    }

    private static void applyStrategicAutoResolveDamage(GameContext ctx, List<Ship> friendlies,
                                                        double ratio, double enemyStrength) {
        if (ctx == null || friendlies == null || friendlies.isEmpty()) return;
        double severity = (ratio >= 1.25) ? 0.12 : (ratio >= 0.9 ? 0.24 : 0.42);
        double playerBias = (ratio >= 1.25) ? 0.75 : (ratio >= 0.9 ? 0.95 : 1.15);
        Ship weakestEscort = null;
        double weakestStrength = Double.POSITIVE_INFINITY;

        for (Ship ship : friendlies) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            double damageFrac = severity;
            if (ship == ctx.player) damageFrac *= playerBias;
            if (ship.role != null && ship.role.isTitanOrMothership()) damageFrac *= 0.86;
            if (ship.role == ShipRole.CIWS_CORVETTE || ship.role == ShipRole.PICKET || ship.role == ShipRole.PATROL) {
                damageFrac *= 1.12;
            }
            int damage = Math.max(1, (int) Math.round(ship.hpMax * damageFrac));
            ship.takeDamage(damage, ship.x, ship.y);

            if (ship != ctx.player) {
                double shipStrength = strategicShipStrength(ship);
                if (shipStrength < weakestStrength) {
                    weakestStrength = shipStrength;
                    weakestEscort = ship;
                }
            }
        }

        if (ratio < 0.9 && weakestEscort != null && weakestEscort.alive && !weakestEscort.dying) {
            weakestEscort.takeDamage(Math.max(weakestEscort.hpMax * 2, (int) Math.round(enemyStrength)),
                    weakestEscort.x, weakestEscort.y);
        }
    }

    private static Ship strategicSpawn(GameContext ctx, StrategicTaskForce taskForce,
                                       ShipRole role, Faction faction,
                                       double x, double y, String name) {
        Ship ship = spawnCampaignShip(ctx, role, faction, x, y, name);
        if (ship != null && taskForce != null) {
            taskForce.spawnedShipIds.add(ship.id);
        }
        return ship;
    }

    private static List<Integer> strategicNeighborSubzones(int subzone) {
        if (subzone < 0) return List.of();
        int col = missionSubzoneColumn(subzone);
        int row = missionSubzoneRow(subzone);
        ArrayList<Integer> out = new ArrayList<>(4);
        int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int next = missionSubzoneIndex(col + dir[0], row + dir[1]);
            if (next >= 0) out.add(next);
        }
        return out;
    }

    private static int chooseStrategicNextSubzone(GameContext ctx, StrategicTaskForce taskForce,
                                                  List<Integer> neighbors, int playerSubzone) {
        CampaignState st = state(ctx);
        if (ctx == null || taskForce == null || neighbors == null || neighbors.isEmpty()) return -1;
        if (neighbors.size() == 1) return neighbors.get(0);
        ArrayList<Integer> pool = new ArrayList<>(neighbors);
        if (taskForce.kind == StrategicTaskForceKind.STEALTH) {
            if (taskForce.breakoffRemainingSec > 0.0 || strategicTaskForceThreat(taskForce, (st == null) ? 1 : st.sector) < 30.0) {
                if (playerSubzone >= 0) {
                    pool.sort((a, b) -> Integer.compare(manhattanSubzoneDistance(b, playerSubzone), manhattanSubzoneDistance(a, playerSubzone)));
                    return pool.get(0);
                }
            }
            int raidTarget = stealthPrioritySubzone(ctx, st, playerSubzone);
            if (raidTarget >= 0) {
                pool.sort((a, b) -> Integer.compare(manhattanSubzoneDistance(a, raidTarget), manhattanSubzoneDistance(b, raidTarget)));
                return pool.get(0);
            }
            if (playerSubzone >= 0) {
                pool.sort((a, b) -> Integer.compare(
                        stealthFlankScore(a, playerSubzone),
                        stealthFlankScore(b, playerSubzone)));
                return pool.get(0);
            }
        }
        if (taskForce.hostile && playerSubzone >= 0 && ctx.rng.nextDouble() < 0.64) {
            pool.sort((a, b) -> Integer.compare(manhattanSubzoneDistance(a, playerSubzone), manhattanSubzoneDistance(b, playerSubzone)));
            return pool.get(0);
        }
        if (!taskForce.hostile && playerSubzone >= 0 && ctx.rng.nextDouble() < 0.55) {
            pool.sort((a, b) -> Integer.compare(manhattanSubzoneDistance(b, playerSubzone), manhattanSubzoneDistance(a, playerSubzone)));
            return pool.get(0);
        }
        return pool.get(ctx.rng.nextInt(pool.size()));
    }

    private static double strategicTransitSeconds(StrategicTaskForce taskForce, int fromSubzone, int toSubzone) {
        int distance = Math.max(1, manhattanSubzoneDistance(fromSubzone, toSubzone));
        double base = switch ((taskForce == null) ? StrategicTaskForceKind.PATROL : taskForce.kind) {
            case PATROL -> 14.0;
            case STRIKE -> 17.0;
            case STEALTH -> 11.0;
            case CONVOY -> 18.0;
            case SALVAGE -> 16.0;
        };
        return base * distance;
    }

    private static int manhattanSubzoneDistance(int a, int b) {
        int aCol = missionSubzoneColumn(a);
        int aRow = missionSubzoneRow(a);
        int bCol = missionSubzoneColumn(b);
        int bRow = missionSubzoneRow(b);
        if (aCol < 0 || aRow < 0 || bCol < 0 || bRow < 0) return 99;
        return Math.abs(aCol - bCol) + Math.abs(aRow - bRow);
    }

    private static int farthestSubzoneFrom(int anchor, Set<Integer> blocked) {
        int best = -1;
        int bestDist = Integer.MIN_VALUE;
        for (int row = 0; row < missionSubzoneRows(); row++) {
            for (int col = 0; col < missionSubzoneColumns(); col++) {
                int subzone = missionSubzoneIndex(col, row);
                if (subzone < 0 || (blocked != null && blocked.contains(subzone))) continue;
                int dist = manhattanSubzoneDistance(anchor, subzone);
                if (dist > bestDist) {
                    bestDist = dist;
                    best = subzone;
                }
            }
        }
        return best;
    }

    private static int flankSubzone(int anchor, Set<Integer> blocked) {
        int anchorRow = missionSubzoneRow(anchor);
        int preferred = missionSubzoneIndex(missionSubzoneColumns() - 1, Math.max(0, missionSubzoneRows() - 1 - Math.max(0, anchorRow)));
        if (preferred >= 0 && (blocked == null || !blocked.contains(preferred))) return preferred;
        return farthestSubzoneFrom(anchor, blocked);
    }

    private static int supportSubzoneNear(int anchor, Set<Integer> blocked, boolean upperHalf) {
        int row = upperHalf ? 0 : Math.max(0, missionSubzoneRows() - 1);
        for (int offset = 1; offset < missionSubzoneColumns(); offset++) {
            int col = Math.min(missionSubzoneColumns() - 1, Math.max(0, missionSubzoneColumn(anchor) + offset));
            int subzone = missionSubzoneIndex(col, row);
            if (subzone >= 0 && (blocked == null || !blocked.contains(subzone))) return subzone;
        }
        return farthestSubzoneFrom(anchor, blocked);
    }

    private static DivisionStance defaultDivisionStance(PersistentFleetEntry entry) {
        if (entry == null || entry.role == null) return DivisionStance.LINE;
        return switch (entry.role) {
            case STEALTH_SHIP, COMMAND_INTEL_TITAN -> DivisionStance.SCOUT;
            case CARRIER, DRONE_CARRIER, CARRIER_SUPPORT_TITAN, TRANSPORT_TITAN, BOARDING_RECOVERY_TITAN -> DivisionStance.ESCORT;
            case ARTILLERY_SHIP, CRUISER, BATTLECRUISER, ARTILLERY_TITAN, HYPERWEAPON_TITAN -> DivisionStance.STRIKE;
            case MOBILE_STATION_TITAN, BULWARK_TITAN -> DivisionStance.RESERVE;
            default -> DivisionStance.LINE;
        };
    }

    private static String strategicDivisionLabel(CampaignState st, int groupId) {
        if (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) return "FLAG DIVISION";
        PersistentFleetEntry anchor = persistentFleetEntryBySlotId(st, groupId);
        if (anchor != null && anchor.name != null && !anchor.name.isBlank()) {
            return anchor.name.toUpperCase(Locale.US);
        }
        return "DIVISION " + groupId;
    }

    private static double strategicDivisionStrength(CampaignState st, int groupId) {
        if (st == null) return 0.0;
        double total = 0.0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.role == null) continue;
            if (Math.max(CAMPAIGN_FLAGSHIP_COMMAND_GROUP, entry.commandGroupId) != groupId) continue;
            total += rawPersistentRoleStrength(entry.role);
        }
        if (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) {
            total += rawPersistentRoleStrength(ShipRole.MOTHERSHIP);
        }
        return total;
    }

    private static int strategicDivisionShipCount(CampaignState st, int groupId) {
        if (st == null) return 0;
        int count = (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) ? 1 : 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (Math.max(CAMPAIGN_FLAGSHIP_COMMAND_GROUP, entry.commandGroupId) == groupId) {
                count++;
            }
        }
        return count;
    }

    private static double rawPersistentRoleStrength(ShipRole role) {
        if (role == null) return 0.0;
        return switch (role) {
            case PATROL, PICKET, FIGHTER, DRONE, BOMBER -> 14.0;
            case FRIGATE, MISSILE_BOAT, CIWS_CORVETTE, MINER, HAULER, TRANSPORT -> 26.0;
            case ARTILLERY_SHIP, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, STEALTH_SHIP -> 44.0;
            case BATTLECRUISER, BATTLESHIP, CARRIER, DRONE_CARRIER, DREADNOUGHT -> 80.0;
            case SUPERSHIP -> 120.0;
            default -> role.isTitanOrMothership() ? 150.0 : 34.0;
        };
    }

    private static double strategicDivisionTransitSeconds(StrategicDivisionState division, int fromSubzone, int toSubzone) {
        int distance = Math.max(1, manhattanSubzoneDistance(fromSubzone, toSubzone));
        double base = switch ((division == null) ? DivisionStance.LINE : division.stance) {
            case SCOUT -> 10.0;
            case STRIKE -> 13.0;
            case ESCORT -> 14.0;
            case RESERVE -> 16.0;
            case LINE -> 15.0;
        };
        return base * distance;
    }

    private static PersistentFleetEntry chooseDetachmentAnchor(CampaignState st) {
        if (st == null) return null;
        PersistentFleetEntry best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.role == null) continue;
            if (entry.commandGroupId != CAMPAIGN_FLAGSHIP_COMMAND_GROUP) continue;
            double score = detachmentAnchorScore(entry.role);
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return best;
    }

    private static PersistentFleetEntry chooseDetachmentEscort(CampaignState st, PersistentFleetEntry anchor) {
        if (st == null || anchor == null) return null;
        if (persistentCommandGroupCapacity(anchor) <= 1) return null;
        PersistentFleetEntry best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.role == null || entry == anchor) continue;
            if (entry.commandGroupId != CAMPAIGN_FLAGSHIP_COMMAND_GROUP) continue;
            double score = detachmentEscortScore(anchor.role, entry.role);
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return (bestScore <= 0.0) ? null : best;
    }

    private static double detachmentAnchorScore(ShipRole role) {
        if (role == null) return 0.0;
        return switch (role) {
            case STEALTH_SHIP -> 120.0;
            case CARRIER, DRONE_CARRIER, CARRIER_SUPPORT_TITAN -> 112.0;
            case COMMAND_INTEL_TITAN -> 108.0;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER -> 100.0;
            case BATTLECRUISER, BATTLESHIP -> 96.0;
            case MISSILE_BOAT, ARTILLERY_SHIP -> 92.0;
            case FRIGATE, PICKET, PATROL, CIWS_CORVETTE -> 78.0;
            default -> role.isTitanOrMothership() ? 0.0 : 60.0;
        };
    }

    private static double detachmentEscortScore(ShipRole anchorRole, ShipRole escortRole) {
        if (escortRole == null) return 0.0;
        double base = switch (escortRole) {
            case FRIGATE -> 38.0;
            case CIWS_CORVETTE -> 42.0;
            case PICKET, PATROL -> 28.0;
            case MISSILE_BOAT -> 24.0;
            default -> 0.0;
        };
        if (base <= 0.0) return base;
        if (anchorRole != null && (anchorRole.isCarrierHull() || anchorRole == ShipRole.CARRIER_SUPPORT_TITAN)) {
            if (escortRole == ShipRole.CIWS_CORVETTE) base += 10.0;
            if (escortRole == ShipRole.FRIGATE) base += 6.0;
        }
        if (anchorRole == ShipRole.STEALTH_SHIP && escortRole == ShipRole.PICKET) {
            base += 8.0;
        }
        return base;
    }

    private static String displayPersistentFleetEntryName(PersistentFleetEntry entry) {
        if (entry == null) return "Detached Division";
        String label = (entry.name == null || entry.name.isBlank()) ? generatedBlueFleetName(entry.role, entry.slotId) : entry.name.trim();
        return label.toUpperCase(Locale.US);
    }

    private static int stealthPrioritySubzone(GameContext ctx, CampaignState st, int playerSubzone) {
        if (ctx == null || st == null) return playerSubzone;
        Ship asset = highValueShipInSubzone(ctx, st, -1);
        if (asset != null) {
            int subzone = missionSubzoneForPoint(ctx, st.sector, asset.x, asset.y);
            if (subzone >= 0) return subzone;
        }
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce == null || taskForce.hostile || taskForce.encounterResolved) continue;
            if (taskForce.currentSubzone >= 0) return taskForce.currentSubzone;
        }
        RecoverableWreckSite wreck = recoverableWreckInSubzone(ctx, st, -1);
        if (wreck != null) {
            int subzone = missionSubzoneForPoint(ctx, st.sector, wreck.x, wreck.y);
            if (subzone >= 0) return subzone;
        }
        return playerSubzone;
    }

    private static int stealthFlankScore(int subzone, int playerSubzone) {
        int dist = manhattanSubzoneDistance(subzone, playerSubzone);
        int rowBias = Math.abs(missionSubzoneRow(subzone) - missionSubzoneRow(playerSubzone));
        return Math.abs(dist - 1) * 10 - rowBias;
    }

    private static StrategicTaskForce friendlyStrategicVictimInSubzone(CampaignState st, int subzone) {
        if (st == null || subzone < 0) return null;
        for (StrategicTaskForce taskForce : st.strategicTaskForces) {
            if (taskForce == null || taskForce.hostile || taskForce.encounterResolved) continue;
            if (taskForce.currentSubzone == subzone) return taskForce;
        }
        return null;
    }

    private static Ship highValueShipInSubzone(GameContext ctx, CampaignState st, int subzone) {
        if (ctx == null || st == null) return null;
        if (st.escortShip != null && st.escortShip.alive && !st.escortShip.dying && st.escortShip.hp > 0) {
            int escortSubzone = missionSubzoneForPoint(ctx, st.sector, st.escortShip.x, st.escortShip.y);
            if (subzone < 0 || escortSubzone == subzone) return st.escortShip;
        }
        for (Integer id : st.objectiveAssetIds) {
            Ship asset = findShipById(ctx, (id == null) ? -1 : id);
            if (asset == null || !asset.alive || asset.dying || asset.hp <= 0) continue;
            int assetSubzone = missionSubzoneForPoint(ctx, st.sector, asset.x, asset.y);
            if (subzone < 0 || assetSubzone == subzone) return asset;
        }
        return null;
    }

    private static RecoverableWreckSite recoverableWreckInSubzone(GameContext ctx, CampaignState st, int subzone) {
        if (ctx == null || st == null || st.recoverableWreckSites.isEmpty()) return null;
        for (RecoverableWreckSite wreck : st.recoverableWreckSites) {
            if (wreck == null || wreck.claimed) continue;
            int wreckSubzone = missionSubzoneForPoint(ctx, st.sector, wreck.x, wreck.y);
            if (subzone < 0 || wreckSubzone == subzone) return wreck;
        }
        return null;
    }

    private static void setCampaignOre(GameContext ctx, CampaignState st, int ore) {
        if (st == null) return;
        st.oreLedger.storedOre = Math.max(0, ore);
        st.oreLedger.normalize();
        syncCampaignOreToFlagship(ctx, st);
    }

    private static void syncCampaignOreToFlagship(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        ctx.player.cargoMax = Math.max(ctx.player.cargoMax, st.oreLedger.storedOre);
        ctx.player.cargo = st.oreLedger.storedOre;
    }

    private static int drainPersistentFleetOreIntoCampaignLedger(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return 0;
        int drained = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.activeShipId <= 0) continue;
            Ship ship = findShipById(ctx, entry.activeShipId);
            if (ship == null || ship == ctx.player || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.cargo <= 0) continue;
            drained += Math.max(0, ship.cargo);
            ship.cargo = 0;
        }
        return drained;
    }

    private static void consolidateCampaignOreLedger(GameContext ctx, CampaignState st, boolean drainPersistentFleet) {
        if (ctx == null || st == null || ctx.player == null) return;
        int pooled = Math.max(0, ctx.player.cargo);
        if (drainPersistentFleet) {
            pooled += drainPersistentFleetOreIntoCampaignLedger(ctx, st);
        }
        setCampaignOre(ctx, st, pooled);
    }

    private static void configureCampaignSession(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        ctx.player.faction = Faction.ALLY;
        ctx.player.name = "Blue Mothership";
        ctx.player.applyHull(ShipRole.MOTHERSHIP, ctx.player.x, ctx.player.y);
        ctx.player.fullyRepairHull();
        if (ctx.player.shieldActive && ctx.player.shieldMax > 0.0) {
            ctx.player.shield = ctx.player.shieldMax;
        }

        Ship oldBlueBase = ctx.allyBase;
        if (oldBlueBase != null) {
            ctx.ships.remove(oldBlueBase);
            ctx.baseUpgrades.remove(oldBlueBase);
        }
        ctx.allyBase = null;
        ctx.teamBases.remove(Faction.ALLY);

        BaseUpgrades mothershipUpgrades = ctx.baseUpgrades.computeIfAbsent(ctx.player, ignored -> new BaseUpgrades().bindTo(ctx.player));
        mothershipUpgrades.hangarLv = Math.max(mothershipUpgrades.hangarLv, CAMPAIGN_PLAYER_STARTING_HANGAR_TIER);
        refreshCampaignAlliances(st);
        st.oreLedger.normalize();
        syncCampaignOreToFlagship(ctx, st);
    }

    private static void seedStartingBlueFleet(CampaignState st) {
        if (st == null || !st.persistentBlueFleet.isEmpty()) return;
        addPersistentFleetEntry(st, ShipRole.PICKET, "Blue Screen One");
        addPersistentFleetEntry(st, ShipRole.FRIGATE, "Blue Guard One");
        addPersistentFleetEntry(st, ShipRole.CIWS_CORVETTE, "Blue Guard Two");
        addPersistentFleetEntry(st, ShipRole.MINER, "Blue Prospector One");
        rebalancePersistentCommandGroups(st);
    }

    private static void refreshCampaignAlliances(CampaignState st) {
        if (st == null) {
            Faction.clearCampaignAlliances();
            return;
        }
        boolean yellowAlliance = st.campaignBlueYellowAlliance || st.sector >= 20;
        st.campaignBlueYellowAlliance = yellowAlliance;
        Faction.configureCampaignAlliances(st.campaignBlueGreenAlliance, yellowAlliance);
    }

    private static void syncPersistentFleetCasualties(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.persistentBlueFleet.isEmpty()) return;
        boolean changed = false;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.activeShipId <= 0) continue;
            Ship live = findShipById(ctx, entry.activeShipId);
            if (live == null || !live.alive || live.dying || live.hp <= 0) {
                entry.destroyed = true;
                entry.activeShipId = -1;
                changed = true;
            }
        }
        if (changed) rebalancePersistentCommandGroups(st);
    }

    private static void updateSectorOneIntro(GameContext ctx, CampaignState st, double dt) {
        if (ctx == null || st == null || ctx.player == null) return;
        st.introTimer += Math.max(0.0, dt);
        switch (st.introPhase) {
            case 0 -> {
                st.cinematicFocusX = ctx.player.x;
                st.cinematicFocusY = ctx.player.y;
                AudioSystem.playScriptedVoice(
                        ctx,
                        "captain",
                        "campaign_earthfall_alert_01",
                        "BLUE COMMAND",
                        "Emergency traffic from Sol. Earth has fallen. Rogue AI occupation confirmed. All blue elements are ordered to return home immediately.",
                        7.5);
                EventSystem.showBanner(ctx, "URGENT SOL TRAFFIC", 2.4);
                st.introPhase = 1;
                st.introTimer = 0.0;
            }
            case 1 -> {
                if (st.introTimer < 4.8) return;
                st.cinematicFocusX = st.introWarpX;
                st.cinematicFocusY = st.introWarpY;
                EventSystem.showBanner(ctx, "RED WARP SIGNATURES DETECTED", 2.1);
                st.introPhase = 2;
                st.introTimer = 0.0;
            }
            case 2 -> {
                if (st.introTimer < 2.0) return;
                spawnIntroRedDetachment(ctx, st);
                st.introPhase = 3;
                st.introTimer = 0.0;
            }
            default -> {
                if (st.introTimer < 2.8) return;
                st.introSequenceActive = false;
                st.introPhase = 0;
                st.introTimer = 0.0;
                st.cinematicFocusX = Double.NaN;
                st.cinematicFocusY = Double.NaN;
            }
        }
    }

    private static void resetPersistentFleetSpawnHandles(CampaignState st) {
        if (st == null) return;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null) continue;
            entry.activeShipId = -1;
        }
    }

    private static void spawnPersistentBlueFleet(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        rebalancePersistentCommandGroups(st);
        java.util.Map<Integer, Ship> groupAnchors = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> groupMemberIndices = new java.util.HashMap<>();
        int titanIndex = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (!isTitanPersistentEntry(entry)) continue;
            Ship ship = hasPersistentEntryPose(entry)
                    ? spawnPersistentBlueShipFromSavedPose(ctx, entry)
                    : spawnPersistentBlueShipFromFlagship(ctx, st, entry, titanIndex++, true);
            if (ship != null) {
                groupAnchors.put(entry.commandGroupId, ship);
            }
        }

        int reserveIndex = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || isTitanPersistentEntry(entry)) continue;
            Ship anchor = groupAnchors.get(entry.commandGroupId);
            if (hasPersistentEntryPose(entry)) {
                spawnPersistentBlueShipFromSavedPose(ctx, entry);
            } else if (anchor != null) {
                int memberIndex = groupMemberIndices.getOrDefault(entry.commandGroupId, 0);
                Ship ship = spawnPersistentBlueShipFromAnchor(ctx, st, entry, anchor, memberIndex);
                if (ship != null) {
                    groupMemberIndices.put(entry.commandGroupId, memberIndex + 1);
                }
            } else {
                spawnPersistentBlueShipFromFlagship(ctx, st, entry, reserveIndex++, false);
            }
        }
    }

    private static void spawnPurchasedPersistentBlueShip(GameContext ctx, CampaignState st, PersistentFleetEntry entry) {
        if (ctx == null || st == null || entry == null || entry.destroyed) return;
        if (hasPersistentEntryPose(entry)) {
            spawnPersistentBlueShipFromSavedPose(ctx, entry);
            return;
        }
        if (isTitanPersistentEntry(entry)) {
            int titanIndex = 0;
            for (PersistentFleetEntry candidate : st.persistentBlueFleet) {
                if (candidate == null || candidate.destroyed || !isTitanPersistentEntry(candidate)) continue;
                if (candidate == entry) break;
                titanIndex++;
            }
            spawnPersistentBlueShipFromFlagship(ctx, st, entry, titanIndex, true);
            return;
        }

        Ship anchor = findPersistentCommandAnchor(ctx, st, entry.commandGroupId);
        if (anchor != null) {
            int memberIndex = 0;
            for (PersistentFleetEntry candidate : st.persistentBlueFleet) {
                if (candidate == null || candidate == entry || candidate.destroyed || isTitanPersistentEntry(candidate)) continue;
                if (candidate.commandGroupId != entry.commandGroupId) continue;
                Ship live = findShipById(ctx, candidate.activeShipId);
                if (live != null && live.alive && !live.dying && live.hp > 0) {
                    memberIndex++;
                }
            }
            spawnPersistentBlueShipFromAnchor(ctx, st, entry, anchor, memberIndex);
            return;
        }

        int reserveIndex = 0;
        for (PersistentFleetEntry candidate : st.persistentBlueFleet) {
            if (candidate == null || candidate == entry || candidate.destroyed || isTitanPersistentEntry(candidate)) continue;
            if (candidate.commandGroupId != CAMPAIGN_FLAGSHIP_COMMAND_GROUP) continue;
            Ship live = findShipById(ctx, candidate.activeShipId);
            if (live != null && live.alive && !live.dying && live.hp > 0) {
                reserveIndex++;
            }
        }
        spawnPersistentBlueShipFromFlagship(ctx, st, entry, reserveIndex, false);
    }

    private static boolean hasPersistentEntryPose(PersistentFleetEntry entry) {
        return entry != null
                && Double.isFinite(entry.relX)
                && Double.isFinite(entry.relY)
                && Double.isFinite(entry.relAngle);
    }

    private static Ship spawnPersistentBlueShipFromSavedPose(GameContext ctx, PersistentFleetEntry entry) {
        if (ctx == null || ctx.player == null || !hasPersistentEntryPose(entry)) return null;
        double forward = Math.cos(ctx.player.angle);
        double side = Math.sin(ctx.player.angle);
        double sx = ctx.player.x + entry.relX * forward - entry.relY * side;
        double sy = ctx.player.y + entry.relX * side + entry.relY * forward;
        double angle = MathUtil.normalizeAngle(ctx.player.angle + entry.relAngle);
        return spawnPersistentBlueShipAtPose(ctx, entry, sx, sy, angle, ctx.player.vx, ctx.player.vy);
    }

    private static void quietEpisodeInterlude(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        Set<Integer> persistentIds = new HashSet<>();
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry != null && !entry.destroyed && entry.activeShipId > 0) {
                persistentIds.add(entry.activeShipId);
            }
        }
        ctx.projectiles.clear();
        ctx.salvage.clear();
        ctx.lockedTarget = null;
        ctx.ships.removeIf(s -> s != null
                && s != ctx.player
                && s != ctx.enemyBase
                && !persistentIds.contains(s.id));
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.firingPrimaryManual = false;
        ctx.firingPrimaryManualLatched = false;
        ctx.firingSecondaryManual = false;
        ctx.firingSecondaryManualLatched = false;
        ctx.firingPrimaryAuto = false;
        ctx.firingSecondaryAuto = false;
        ctx.miningKeyDown = false;
        ctx.command.playerTeleportCharging = false;
        ctx.command.playerTeleportChargeRemaining = 0.0;
        healAndRefitPlayer(ctx);
        for (Ship s : ctx.ships) {
            if (s == null || s == ctx.player) continue;
            if (!persistentIds.contains(s.id)) continue;
            s.resetWeaponCycleState();
            s.fullyRepairHull();
            if (s.shieldActive && s.shieldMax > 0.0) s.shield = s.shieldMax;
            s.vx = 0.0;
            s.vy = 0.0;
        }
    }

    private static Ship spawnCampaignBase(GameContext ctx, Faction faction, double x, double y, String name) {
        if (ctx == null || faction == null) return null;
        Ship base = new FleetShip(ShipRole.BASE, faction,
                GameMath.clamp(x, 40.0, ctx.WORLD_W - 40.0),
                GameMath.clamp(y, 40.0, ctx.WORLD_H - 40.0));
        if (name != null && !name.isBlank()) base.name = name;
        ctx.ships.add(base);
        ctx.baseUpgrades.computeIfAbsent(base, ignored -> new BaseUpgrades().bindTo(base));
        if (!ctx.teamBases.containsKey(faction)) ctx.teamBases.put(faction, base);
        if (faction == Faction.ENEMY) ctx.enemyBase = base;
        return base;
    }

    private static Ship spawnCampaignFactionAtPlayerOffset(GameContext ctx, ShipRole role, Faction faction,
                                                           double ox, double oy, String name) {
        if (ctx == null || ctx.player == null) return null;
        return spawnCampaignShip(ctx, role, faction, ctx.player.x + ox, ctx.player.y + oy, name);
    }

    private static Ship spawnEscortTitan(GameContext ctx, ShipRole role, Faction faction, String name) {
        if (ctx == null || ctx.player == null) return null;
        Ship anchor = TeamSystem.getBaseForTeam(ctx, faction);
        double sx = (anchor != null) ? anchor.x + 110.0 : (ctx.player.x - 180.0);
        double sy = (anchor != null) ? anchor.y + 90.0 : (ctx.player.y + 40.0);
        Ship titan = spawnCampaignShip(ctx, role, faction, sx, sy, name);
        if (titan != null) {
            titan.desiredSpeed = Math.max(48.0, titan.desiredSpeed);
        }
        return titan;
    }

    private static void startSector(GameContext ctx, int sector) {
        CampaignState st = state(ctx);
        if (st == null) return;
        boolean campaignMapMode = isStrategicOvermapMode(st);

        if (ctx != null) ctx.state = GameState.RUNNING;
        st.sector = sector;
        st.act = actForSector(sector);

        // Set player position to warp arrival point
        if (ctx != null && ctx.player != null) {
            int arrivalSourceSector = st.routeArrivalSourceSector > 0 ? st.routeArrivalSourceSector : sector - 1;
            double[] arrival = (sector == 1) 
                ? new double[]{missionSubzoneCenterX(ctx, sector, missionSubzoneIndex(0, 1)),
                        missionSubzoneCenterY(ctx, sector, missionSubzoneIndex(0, 1))}
                : getWarpArrivalPoint(ctx, arrivalSourceSector, sector);
            ctx.player.x = arrival[0];
            ctx.player.y = arrival[1];
            ctx.player.vx = 0.0;
            ctx.player.vy = 0.0;
            ctx.player.angle = Double.isFinite(st.persistentFleetHeading) ? st.persistentFleetHeading : -Math.PI / 2.0;
            int arrivalSubzone = missionSubzoneForPoint(ctx, sector, arrival[0], arrival[1]);
            if (arrivalSubzone < 0) arrivalSubzone = nearestMissionSubzone(ctx, sector, arrival[0], arrival[1]);
            setLoadedMissionSubzone(ctx, arrivalSubzone);
            ctx.player.campaignWarpSourceSubzone = -1;
        }

        st.transitionTimer = 0.0;
        st.awaitingEpisodeLaunch = false;
        st.pendingEpisodeSector = 0;
        st.routeArrivalSourceSector = 0;
        st.routeChoices.clear();
        st.selectedRouteChoice = 0;
        st.sectorElapsed = 0.0;
        st.kills = 0;
        st.knownHostiles.clear();
        st.authoredObjectiveHostiles.clear();
        st.authoredObjectiveKills = 0;
        st.lastAnnouncedAuthoredObjectiveKills = 0;
        st.authoredWaveCursor = 0;
        st.landmarks.clear();
        st.objectivePhaseLabel = "";
        st.threatStateLabel = "";
        st.objectiveStage = 0;
        st.mapPressureStage = 0;
        st.objectiveKillBaseline = 0;
        st.objectiveAssetIds.clear();
        st.objectiveAssetTotal = 0;
        st.objectiveAssetLosses = 0;
        st.lastAnnouncedObjectiveAssetLosses = 0;
        st.objectiveAssetLabel = "";
        st.objectiveAssetRequiredSurvivors = 0;
        st.objectiveAssetFailureText = "";
        st.missionSections.clear();
        st.activeMissionSection = 0;
        st.missionSectionTravelLocked = false;
        st.missionTheme = missionThemeForSector(st);
        st.discoverySites.clear();
        st.discoveriesFound = 0;
        st.recoverableWreckSites.clear();
        st.recoverableWrecksClaimed = 0;
        st.captureArmed = false;
        st.bossTargetId = -1;
        st.bossKind = BossKind.NONE;
        st.bossPhaseOneTriggered = false;
        st.bossPhaseTwoTriggered = false;
        st.escortShip = null;
        st.transitionLabel = "";
        st.transitionSummaryTop = "";
        st.transitionSummaryBottom = "";
        st.missionIntroTimer = 0.0;
        st.missionStartBanterPlayed = false;
        st.extractionWarningStage = 0;
        st.objectiveSecured = false;
        st.extractionMinHoldSeconds = 200.0;
        st.sectorStartMillis = System.currentTimeMillis();
        st.introSequenceActive = false;
        st.introPhase = 0;
        st.introTimer = 0.0;
        st.introWarpX = Double.NaN;
        st.introWarpY = Double.NaN;
        st.cinematicFocusX = Double.NaN;
        st.cinematicFocusY = Double.NaN;
        st.escortFormationIntegrity = 0.0;
        st.strategicTaskForces.clear();

        refreshCampaignAlliances(st);
        rebalancePersistentCommandGroups(st);
        resetPersistentFleetSpawnHandles(st);
        pruneTransientUnits(ctx);
        if (campaignMapMode) {
            clearBattleEncounterWorld(ctx);
        } else {
            FogOfWarSystem.reset(ctx);
            regroupPlayerAtAlliedBase(ctx);
            SpawnSystem.spawnAsteroidField(ctx);
        }
        applyCampaignFleetBonuses(ctx, st);
        healAndRefitPlayer(ctx);
        if (!campaignMapMode) {
            ensureCampaignTitanInfrastructure(ctx);
        }

        SectorScript script = scriptFor(st.sector);
        if (campaignMapMode) {
            configureStrategicOvermapTheater(ctx, st, script);
        } else {
            script = configureObjective(ctx);
        }
        applySectorModifiers(ctx, st, script);
        if (!campaignMapMode) {
            spawnSectorForces(ctx);
        }
        populateSectorLandmarks(ctx, st);
        enrichSectorMissionSpace(ctx, st);
        if (!campaignMapMode) {
            initializeStrategicTaskForces(ctx, st);
            spawnPersistentBlueFleet(ctx, st);
            spawnCoalitionSupportFleet(ctx, st);
            captureSideObjectiveProtectedShip(ctx, st);
        }
        st.enemyBaseWinConditionActive = !campaignMapMode && hasLiveEnemyBase(ctx);
        snapshotHostiles(ctx, st.knownHostiles);

        ctx.enemyWaveTimer = campaignMapMode ? Double.POSITIVE_INFINITY : nextWaveDelay(ctx);
        if (!campaignMapMode) {
            FogOfWarSystem.update(ctx);
        }

        SectorLore lore = loreFor(st.sector);
        String msg = campaignMapMode
                ? "THEATER " + st.sector + "/" + st.totalSectors + "  " + lore.title + "  |  STRATEGIC OVERMAP ACTIVE"
                : "SECTOR " + st.sector + "/" + st.totalSectors + "  " + lore.title + "  |  " + st.objectiveLabel;
        EventSystem.showBanner(ctx, msg, campaignMapMode ? 2.6 : 3.2);
        st.missionIntroTimer = campaignMapMode ? 0.0 : ((st.sector == 1) ? 14.0 : 8.5);
        if (!campaignMapMode) {
            seedWaypointFromObjectives(ctx, st, false);
        } else {
            enterStrategicOvermap(ctx, st, null);
        }
        logTelemetry("sector_start",
                "sector=" + st.sector +
                        " act=" + st.act +
                        " objective=" + st.objectiveType +
                        " goal=" + Math.round(st.objectiveGoal) +
                        " side=" + st.sideObjectiveType +
                        " route=" + st.branchRoute +
                        " mods=" + modifiersSummary(st.activeModifiers) +
                        " limitSec=" + Math.round(st.sectorTimeLimit));
    }

    private static SectorScript configureObjective(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return scriptFor(1);

        SectorScript script = scriptFor(st.sector);
        setObjective(st, script.objectiveType, script.objectiveLabel, script.objectiveGoal);
        st.sectorTimeLimit = script.timeLimitSec;
        st.bossKind = script.bossKind;
        configureSideObjective(ctx, st);
        return script;
    }

    private static void configureStrategicOvermapTheater(GameContext ctx, CampaignState st, SectorScript script) {
        if (st == null) return;
        SectorLore lore = loreFor(st.sector);
        st.objectiveType = ObjectiveType.SURVIVE;
        st.objectiveLabel = "Coordinate the fleet on the strategic overmap";
        st.objectiveGoal = 1.0;
        st.objectiveProgress = 0.0;
        st.sectorTimeLimit = 0.0;
        st.bossKind = BossKind.NONE;
        st.sideObjectiveType = SideObjectiveType.NONE;
        st.sideObjectiveLabel = "";
        st.sideObjectiveGoal = 0.0;
        st.sideObjectiveProgress = 0.0;
        st.sideObjectiveRewardCredits = 0;
        st.sideObjectiveProtectedShipId = -1;
        st.sideObjectiveProtectedShipStartHp = 0;
        st.sideObjectiveCompleted = false;
        st.sideObjectiveFailed = false;
        st.sideObjectiveBaseKills = st.kills;
        st.sideObjectiveStartPlayerHp = (ctx != null && ctx.player != null) ? ctx.player.hp : 0;
        st.objectivePhaseLabel = "MAP: Move divisions, prosecute contacts, and keep the flagship alive";
        st.threatStateLabel = "THREAT: Only direct contact launches tactical combat";
        st.transitionLabel = "STRATEGIC OVERMAP";
        st.transitionSummaryTop = lore.title + " is now a contact-driven theater rather than a scripted mission lane.";
        st.transitionSummaryBottom = "Shift+LMB torpedo  |  Shift+RMB sortie  |  Ctrl+Shift+LMB atomic  |  Alt+LMB division order";
        if (script != null) {
            st.activeModifiers = (script.modifiers == null || script.modifiers.length == 0)
                    ? new MapModifier[]{MapModifier.NONE}
                    : script.modifiers;
        }
    }

    private static void setObjective(CampaignState st, ObjectiveType type, String label, double goal) {
        st.objectiveType = type;
        st.objectiveLabel = label;
        st.objectiveGoal = Math.max(1.0, goal);
        st.objectiveProgress = 0.0;
    }

    private static void configureSideObjective(GameContext ctx, CampaignState st) {
        SideObjectiveScript side = sideScriptFor(st.sector);
        st.sideObjectiveType = side.type;
        st.sideObjectiveLabel = side.label;
        st.sideObjectiveGoal = Math.max(0.0, side.goal);
        st.sideObjectiveProgress = 0.0;
        st.sideObjectiveRewardCredits = GameContext.scaleCreditEarnings(Math.max(0, side.rewardCredits));
        st.sideObjectiveProtectedShipId = -1;
        st.sideObjectiveProtectedShipStartHp = 0;
        st.sideObjectiveCompleted = false;
        st.sideObjectiveFailed = false;
        st.sideObjectiveBaseKills = st.kills;
        st.sideObjectiveStartPlayerHp = (ctx.player != null) ? ctx.player.hp : 0;
        st.objectivePhaseLabel = initialPhaseLabel(st);
        st.threatStateLabel = initialThreatLabel(st);
    }

    private static String initialPhaseLabel(CampaignState st) {
        if (st == null) return "";
        return switch (st.sector) {
            case 1 -> "PHASE: Screen the anchorage while civilian lanes stay open";
            case 2 -> "PHASE: Intercept the customs-halo gunships before the aperture seals";
            case 3 -> "PHASE: Break the aperture cordon before the jump ring becomes a kill box";
            case 4 -> "PHASE: Sweep the relay blockers off the authority spindle";
            case 5 -> "PHASE: Break the reserve wing chasing the relay";
            case 6 -> "PHASE: Hold the debris field long enough to pull the caches free";
            case 7 -> "PHASE: Hold formation until the pursuit Titan commits";
            case 8 -> "PHASE: Keep the refugee titan screened and moving";
            case 9 -> "PHASE: Keep the broker defectors alive while they cross into formation";
            case 10 -> "PHASE: Shatter the trade-spine cordon before reserves entrench";
            case 11 -> "PHASE: Hold the depot shelf while the logistics teams recover stores";
            case 12 -> "PHASE: Keep the signatory run alive until the pact is sealed";
            case 13 -> "PHASE: Sweep the Nysa perimeter and cut the jammer triad";
            case 14 -> "PHASE: Break the Nysa relief wing before the array is isolated again";
            case 15 -> "PHASE: Silence the outer batteries feeding Kharon's fire-control net";
            case 16 -> "PHASE: Close through the siege lane and silence the artillery titan";
            case 17 -> "PHASE: Kill the probe war screen before it marks the coalition";
            case 18 -> "PHASE: Hold the coalition mustering corridor under pressure";
            case 19 -> "PHASE: Break the prison chain and free the yellow convoy";
            case 20 -> "PHASE: Keep the liberated recovery titan screened and moving";
            case 21 -> "PHASE: Silence Luna's anchor grid and foundry guns";
            case 22 -> "PHASE: Break the lunar reserve cordon and force the Earth lane";
            case 23 -> "PHASE: Blind the occupation uplinks and cover the resistance launches";
            case 24 -> "PHASE: Collapse the orbital defense ring and kill the AI flagship";
            default -> "PHASE: Advance the campaign objective";
        };
    }

    private static String initialThreatLabel(CampaignState st) {
        if (st == null) return "";
        return switch (st.sector) {
            case 1 -> "THREAT: Raider probes and panic around the colony docks";
            case 2 -> "THREAT: Customs-halo gunships locking down the civilian aperture";
            case 3 -> "THREAT: Interdiction packs covering the jump ring";
            case 4 -> "THREAT: Relay defenders and route-control lances";
            case 5 -> "THREAT: Relay reserves racing the vector from the hinterlane";
            case 6 -> "THREAT: Demolition ships erasing the convoy's political and fuel reserves";
            case 7 -> "THREAT: Titan pursuit group closing from the wake";
            case 8 -> "THREAT: Vanguard hunters probing the convoy flanks";
            case 9 -> "THREAT: Red raiders trying to kill the defectors before they switch sides";
            case 10 -> "THREAT: Cordon reserves ready to counter-punch";
            case 11 -> "THREAT: Demolition detachments burning ledgers and depot stores";
            case 12 -> "THREAT: Interceptors hunting the signatory run";
            case 13 -> "THREAT: Contract-array defenders and jammer escorts";
            case 14 -> "THREAT: Red relief wings trying to re-isolate Nysa";
            case 15 -> "THREAT: Spotter towers and anchor guns feeding Kharon's artillery";
            case 16 -> "THREAT: Siege escorts feeding the artillery gate";
            case 17 -> "THREAT: Probe groups and marker ships painting the coalition lane";
            case 18 -> "THREAT: Sol pickets converging on the corridor";
            case 19 -> "THREAT: Prison tenders and clamp escorts holding the yellow chain together";
            case 20 -> "THREAT: Breakchain raiders hunting the recovery line";
            case 21 -> "THREAT: Orbital defense groups screening Luna";
            case 22 -> "THREAT: Lunar reserve capitals protecting the Earth approach";
            case 23 -> "THREAT: Occupation uplinks and kill teams suppressing resistance launches";
            case 24 -> "THREAT: Full occupation fleet over Earth";
            default -> "THREAT: Hostile fleet contact expected";
        };
    }

    private static void spawnSectorForces(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || ctx.player == null) return;

        switch (st.sector) {
            case 1 -> spawnSector1(ctx, st);
            case 2 -> spawnSector2(ctx, st);
            case 3 -> spawnSector3(ctx, st);
            case 4 -> spawnSector4(ctx, st);
            case 5 -> spawnSector5(ctx, st);
            case 6 -> spawnSector6(ctx, st);
            case 7 -> spawnSector7(ctx, st);
            case 8 -> spawnSector8(ctx, st);
            case 9 -> spawnSector9(ctx, st);
            case 10 -> spawnSector10(ctx, st);
            case 11 -> spawnSector11(ctx, st);
            case 12 -> spawnSector12(ctx, st);
            case 13 -> spawnSector13(ctx, st);
            case 14 -> spawnSector14(ctx, st);
            case 15 -> spawnSector15(ctx, st);
            case 16 -> spawnSector16(ctx, st);
            case 17 -> spawnSector17(ctx, st);
            case 18 -> spawnSector18(ctx, st);
            case 19 -> spawnSector19(ctx, st);
            case 20 -> spawnSector20(ctx, st);
            case 21 -> spawnSector21(ctx, st);
            case 22 -> spawnSector22(ctx, st);
            case 23 -> spawnSector23(ctx, st);
            default -> spawnSector24(ctx, st);
        }
    }

    private static void populateSectorLandmarks(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        st.landmarks.clear();

        double px = ctx.player.x;
        double py = ctx.player.y;
        switch (st.sector) {
            case 1 -> {
                addLandmark(st, ctx, LandmarkType.COLONY, "Far Trade Anchorage", "Pelagos arcology crowns, refinery piers, and refugee docks",
                        px + 860.0, py - 720.0, 280.0,
                        new Color(96, 156, 210, 42), new Color(190, 228, 255, 176));
                addLandmark(st, ctx, LandmarkType.RING, "Anchorage Exchange Ring", "Broken customs loop and bonded market spine",
                        px + 910.0, py - 760.0, 420.0,
                        new Color(118, 200, 220, 20), new Color(162, 232, 248, 150));
            }
            case 2 -> {
                addLandmark(st, ctx, LandmarkType.RING, "Customs Halo", "Civilian aperture and outer-ring customs loop",
                        px + 1180.0, py - 140.0, 300.0,
                        new Color(94, 158, 238, 18), new Color(164, 212, 255, 168));
                addLandmark(st, ctx, LandmarkType.COLONY, "Cinder Anchorage", "Stripped arcology barges drifting off the lane",
                        px - 820.0, py + 520.0, 210.0,
                        new Color(144, 124, 98, 28), new Color(228, 198, 170, 150));
            }
            case 3 -> {
                addLandmark(st, ctx, LandmarkType.RING, "Outer Colony Jump Ring", "Civilian transit aperture and interdiction cordon",
                        px + 1160.0, py - 120.0, 320.0,
                        new Color(94, 158, 238, 18), new Color(164, 212, 255, 168));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Breakout Aperture", "The only safe vector out of the colony halo",
                        px + 860.0, py + 10.0, 180.0,
                        new Color(120, 176, 220, 18), new Color(200, 234, 255, 150));
            }
            case 4 -> {
                addLandmark(st, ctx, LandmarkType.RELAY, "Gate Relay Tethys", "Jump authority uplink and route-control spindle",
                        st.captureX, st.captureY, 190.0,
                        new Color(98, 166, 218, 24), new Color(206, 235, 255, 180));
                addLandmark(st, ctx, LandmarkType.RING, "Tethys Transit Halo", "Dormant route lattice around the relay spindle",
                        st.captureX + 60.0, st.captureY - 40.0, 300.0,
                        new Color(90, 186, 214, 16), new Color(150, 236, 255, 142));
            }
            case 5 -> {
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Tethys Relay Hinterlane", "Reserve approach lane feeding the relay",
                        st.captureX + 320.0, st.captureY - 20.0, 220.0,
                        new Color(120, 188, 126, 18), new Color(190, 245, 196, 155));
                addLandmark(st, ctx, LandmarkType.RELAY, "Authority Vector", "Green navigation handoff zone behind the relay",
                        st.captureX - 280.0, st.captureY + 80.0, 160.0,
                        new Color(120, 150, 170, 20), new Color(210, 232, 246, 142));
            }
            case 6 -> {
                addLandmark(st, ctx, LandmarkType.FRONT, "Burning Debris Wake", "Wreck belt from the breakout",
                        px + 980.0, py - 280.0, 260.0,
                        new Color(168, 116, 84, 22), new Color(236, 178, 146, 160));
                addLandmark(st, ctx, LandmarkType.COLONY, "Recovery Drift", "Archive barges and fuel caskets still afloat in the wake",
                        px + 1260.0, py - 60.0, 180.0,
                        new Color(158, 72, 72, 18), new Color(255, 144, 144, 154));
            }
            case 7 -> {
                addLandmark(st, ctx, LandmarkType.FRONT, "Shattered Traffic Lanes", "Wreck-choked pursuit lane behind the breakout",
                        px + 980.0, py - 280.0, 260.0,
                        new Color(168, 116, 84, 22), new Color(236, 178, 146, 160));
                addLandmark(st, ctx, LandmarkType.FRONT, "Pursuit Vector", "Red kill-box approach lane",
                        px + 1260.0, py - 60.0, 180.0,
                        new Color(158, 72, 72, 18), new Color(255, 144, 144, 154));
            }
            case 8 -> {
                double ex = (st.escortShip != null) ? st.escortShip.x : px + 520.0;
                double ey = (st.escortShip != null) ? st.escortShip.y : py - 40.0;
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Exodus Corridor", "Refugee wayline toward the spine",
                        ex + 460.0, ey - 60.0, 240.0,
                        new Color(120, 188, 126, 18), new Color(190, 245, 196, 155));
                addLandmark(st, ctx, LandmarkType.COLONY, "Archive Lifeboat Cluster", "Civilian traffic struggling to stay grouped",
                        ex - 420.0, ey + 260.0, 170.0,
                        new Color(120, 150, 170, 20), new Color(210, 232, 246, 142));
            }
            case 9 -> {
                addLandmark(st, ctx, LandmarkType.COLONY, "Neutral Trade Spine", "Broker depots, bonded yards, and defecting logistics slips",
                        px + 980.0, py - 320.0, 250.0,
                        new Color(120, 154, 198, 24), new Color(214, 230, 255, 166));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Broker Slipways", "Neutral defectors crossing into Blue protection",
                        px + 1320.0, py + 40.0, 180.0,
                        new Color(156, 68, 68, 18), new Color(255, 146, 146, 168));
            }
            case 10 -> {
                addLandmark(st, ctx, LandmarkType.COLONY, "Trade Spine Defense Belt", "Broker yards under hard military cordon",
                        px + 980.0, py - 320.0, 250.0,
                        new Color(120, 154, 198, 24), new Color(214, 230, 255, 166));
                addLandmark(st, ctx, LandmarkType.FORTRESS, "Red Cordon Bastion", "Entrenched blockade node over the spine",
                        px + 1320.0, py + 40.0, 180.0,
                        new Color(156, 68, 68, 18), new Color(255, 146, 146, 168));
            }
            case 11 -> {
                addLandmark(st, ctx, LandmarkType.COLONY, "Bonded Depot Shelf", "Fuel trains, ledger vaults, and refit locks",
                        px + 980.0, py - 320.0, 250.0,
                        new Color(120, 154, 198, 24), new Color(214, 230, 255, 166));
                addLandmark(st, ctx, LandmarkType.COLONY, "Refit Shelf", "Stores and bonded cargo still salvageable under fire",
                        px + 1320.0, py + 40.0, 180.0,
                        new Color(156, 68, 68, 18), new Color(255, 146, 146, 168));
            }
            case 12 -> {
                addLandmark(st, ctx, LandmarkType.RING, "Coalition Service Halos", "Courier lanes and broker sanctums around the pact route",
                        px + 960.0, py - 180.0, 300.0,
                        new Color(102, 196, 168, 24), new Color(190, 248, 226, 174));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Signatory Corridor", "Green command hulls crossing into open coalition protection",
                        px + 1260.0, py + 50.0, 180.0,
                        new Color(88, 170, 170, 18), new Color(162, 238, 236, 144));
            }
            case 13 -> {
                addLandmark(st, ctx, LandmarkType.RELAY, "Coalition Array Nysa", "Contract relay, broker sanctum, and fleet-signature exchange",
                        st.captureX, st.captureY, 210.0,
                        new Color(102, 196, 168, 24), new Color(190, 248, 226, 174));
                addLandmark(st, ctx, LandmarkType.RING, "Array Service Halo", "Civilian maintenance lattice over the contract world",
                        st.captureX + 80.0, st.captureY - 50.0, 320.0,
                        new Color(88, 170, 170, 18), new Color(162, 238, 236, 144));
            }
            case 14 -> {
                addLandmark(st, ctx, LandmarkType.RELAY, "Coalition Array Nysa", "Contract relay and green command anchor",
                        st.captureX, st.captureY, 210.0,
                        new Color(102, 196, 168, 24), new Color(190, 248, 226, 174));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Rear-Orbit Relief Lane", "Reserve approach route trying to re-isolate the array",
                        st.captureX + 360.0, st.captureY - 40.0, 220.0,
                        new Color(88, 170, 170, 18), new Color(162, 238, 236, 144));
            }
            case 15 -> {
                addLandmark(st, ctx, LandmarkType.FORTRESS, "Siege Gate Kharon", "Spotter towers, anchor guns, and siege beacons",
                        st.captureX, st.captureY, 240.0,
                        new Color(170, 88, 72, 20), new Color(248, 174, 152, 176));
                addLandmark(st, ctx, LandmarkType.RING, "Outer Siege Spine", "Targeting architecture feeding the artillery gate",
                        st.captureX + 160.0, st.captureY - 40.0, 360.0,
                        new Color(176, 118, 90, 16), new Color(240, 202, 168, 138));
            }
            case 16 -> {
                addLandmark(st, ctx, LandmarkType.FORTRESS, "Siege Gate Furnace", "Artillery gatehouse and burning transit steel",
                        px + 1080.0, py - 120.0, 240.0,
                        new Color(170, 88, 72, 20), new Color(248, 174, 152, 176));
                addLandmark(st, ctx, LandmarkType.RING, "Ash Gate Spine", "Collapsed transit architecture under bombardment",
                        px + 1210.0, py - 60.0, 360.0,
                        new Color(176, 118, 90, 16), new Color(240, 202, 168, 138));
            }
            case 17 -> {
                addLandmark(st, ctx, LandmarkType.STAR, "Sol", "The home star just beyond the defense fringe",
                        px + 1540.0, py - 980.0, 460.0,
                        new Color(255, 198, 116, 40), new Color(255, 236, 178, 164));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Probe Marker Belt", "Recon line trying to paint the coalition corridor",
                        px + 860.0, py + 60.0, 320.0,
                        new Color(142, 194, 244, 16), new Color(192, 226, 255, 146));
            }
            case 18 -> {
                addLandmark(st, ctx, LandmarkType.STAR, "Sol", "The home star beyond the defense ring",
                        px + 1540.0, py - 980.0, 460.0,
                        new Color(255, 198, 116, 40), new Color(255, 236, 178, 164));
                addLandmark(st, ctx, LandmarkType.RING, "Coalition Assembly Ring", "Arrival corridor for the final push",
                        px + 860.0, py + 60.0, 320.0,
                        new Color(142, 194, 244, 16), new Color(192, 226, 255, 146));
            }
            case 19 -> {
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Liberation Corridor", "Recovery line for freed yellow crews",
                        px + 420.0, py - 20.0, 230.0,
                        new Color(188, 174, 104, 18), new Color(255, 232, 152, 160));
                addLandmark(st, ctx, LandmarkType.FRONT, "Breakchain Clamp Field", "Prison tenders and chain nodes still holding the convoy",
                        px + 860.0, py + 140.0, 180.0,
                        new Color(126, 118, 90, 16), new Color(224, 206, 170, 138));
            }
            case 20 -> {
                double ex = (st.escortShip != null) ? st.escortShip.x : px + 520.0;
                double ey = (st.escortShip != null) ? st.escortShip.y : py - 40.0;
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Breakchain Debris Run", "Recovery route for the liberated yellow crews",
                        ex + 420.0, ey - 20.0, 230.0,
                        new Color(188, 174, 104, 18), new Color(255, 232, 152, 160));
                addLandmark(st, ctx, LandmarkType.FRONT, "Breakchain Wreck Line", "Debris from snapped convoy chains",
                        ex - 420.0, ey + 250.0, 180.0,
                        new Color(126, 118, 90, 16), new Color(224, 206, 170, 138));
            }
            case 21 -> {
                addLandmark(st, ctx, LandmarkType.PLANET, "Luna", "Mass-driver yards, foundry belts, and orbital defense guns",
                        px + 1300.0, py - 520.0, 260.0,
                        new Color(184, 194, 210, 30), new Color(242, 246, 255, 172));
                addLandmark(st, ctx, LandmarkType.PLANET, "Earthrise", "Occupied homeworld beyond the lunar defense lane",
                        px + 1880.0, py - 1020.0, 420.0,
                        new Color(92, 138, 220, 26), new Color(190, 220, 255, 156));
            }
            case 22 -> {
                addLandmark(st, ctx, LandmarkType.PLANET, "Luna", "Broken anchor grid and shattered foundry guns",
                        px + 1180.0, py - 420.0, 240.0,
                        new Color(184, 194, 210, 30), new Color(242, 246, 255, 172));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Earth Approach Lane", "Last reserve cordon between the fleet and home",
                        px + 1580.0, py - 160.0, 300.0,
                        new Color(92, 138, 220, 26), new Color(190, 220, 255, 156));
            }
            case 23 -> {
                addLandmark(st, ctx, LandmarkType.PLANET, "Earthrise", "Occupied homeworld under orbital blackout",
                        px + 1540.0, py - 920.0, 420.0,
                        new Color(92, 138, 220, 26), new Color(190, 220, 255, 156));
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "Lift Terminus Belt", "Resistance launches rising through the orbital lift lanes",
                        st.captureX, st.captureY, 240.0,
                        new Color(98, 148, 206, 14), new Color(202, 232, 255, 136));
            }
            default -> {
                addLandmark(st, ctx, LandmarkType.PLANET, "Earth", "Occupied homeworld under orbital fire and citywide blackout",
                        px + 1320.0, py - 640.0, 520.0,
                        new Color(86, 134, 220, 34), new Color(188, 220, 255, 176));
                addLandmark(st, ctx, LandmarkType.RING, "Earth High Orbit", "Occupation defense lattice and orbital lift termini",
                        px + 1320.0, py - 640.0, 760.0,
                        new Color(98, 148, 206, 14), new Color(202, 232, 255, 136));
                addLandmark(st, ctx, LandmarkType.PLANET, "Luna", "Shattered approach moon and abandoned gun line",
                        px + 840.0, py - 220.0, 180.0,
                        new Color(192, 198, 212, 24), new Color(246, 248, 255, 154));
            }
        }
    }

    private static void addLandmark(CampaignState st, GameContext ctx, LandmarkType type, String label, String subtitle,
                                    double x, double y, double radius, Color fill, Color edge) {
        if (st == null || ctx == null) return;
        double clampedX = GameMath.clamp(x, radius + 120.0, ctx.WORLD_W - radius - 120.0);
        double clampedY = GameMath.clamp(y, radius + 120.0, ctx.WORLD_H - radius - 120.0);
        st.landmarks.add(new CampaignLandmark(type, label, subtitle, clampedX, clampedY, radius, fill, edge, false));
    }

    private static void enrichSectorMissionSpace(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;

        boolean enteredFromRight = missionSubzoneColumn(st.loadedMissionSubzone) >= (MISSION_ZONE_COLUMNS / 2);
        int entryCol = missionSubzoneColumn(st.loadedMissionSubzone);
        int[] laneColumns = missionColumnsWithArrivalSafety(
                shuffledMissionColumns(ctx, st, enteredFromRight, "lane"),
                entryCol,
                true);
        int[] topColumns = missionColumnsWithArrivalSafety(
                shuffledMissionColumns(ctx, st, enteredFromRight, "top"),
                entryCol,
                true);
        int[] bottomColumns = missionColumnsWithArrivalSafety(
                shuffledMissionColumns(ctx, st, enteredFromRight, "bottom"),
                entryCol,
                true);
        int[] rearColumns = missionColumnsWithArrivalSafety(
                shuffledMissionColumns(ctx, st, enteredFromRight, "rear"),
                entryCol,
                false);

        int laneNearZone = missionSubzoneIndex(laneColumns[0], 1);
        int laneMidZone = missionSubzoneIndex(laneColumns[1], 1);
        int laneFarZone = missionSubzoneIndex(laneColumns[2], 1);
        int resourceZone = missionSubzoneIndex(topColumns[0], 0);
        int supportZone = missionSubzoneIndex(bottomColumns[0], 2);
        int reserveZone = missionSubzoneIndex(rearColumns[0], 1);

        double laneXNear = missionSubzoneCenterX(ctx, st.sector, laneNearZone);
        double laneY = missionSubzoneCenterY(ctx, st.sector, laneNearZone);
        double laneXMid = missionSubzoneCenterX(ctx, st.sector, laneMidZone);
        double laneXFar = missionSubzoneCenterX(ctx, st.sector, laneFarZone);
        double resourceX = missionSubzoneCenterX(ctx, st.sector, resourceZone);
        double resourceY = missionSubzoneCenterY(ctx, st.sector, resourceZone);
        double supportX = missionSubzoneCenterX(ctx, st.sector, supportZone);
        double supportY = missionSubzoneCenterY(ctx, st.sector, supportZone);
        double reserveX = missionSubzoneCenterX(ctx, st.sector, reserveZone);
        double reserveY = missionSubzoneCenterY(ctx, st.sector, reserveZone);

        MissionTheme theme = st.missionTheme;

        addLandmark(st, ctx, LandmarkType.CORRIDOR, themeSectionLabel(theme, 0, "FORWARD SCREEN"), "Main assault lane where the enemy pickets the route",
                laneXMid, laneY, 320.0, new Color(108, 164, 232, 16), new Color(188, 220, 255, 136));
        addLandmark(st, ctx, LandmarkType.COLONY, themeSectionLabel(theme, 1, "RESOURCE POCKET"), "Rich ore and salvage off the main lane",
                resourceX, resourceY, 260.0, new Color(214, 190, 114, 20), new Color(255, 232, 170, 152));
        addLandmark(st, ctx, LandmarkType.RELAY, themeSectionLabel(theme, 2, "SUPPORT RELAY"), "Fleet tenders and wounded ships regroup here",
                supportX, supportY, 250.0, new Color(116, 198, 172, 18), new Color(186, 248, 228, 160));
        addLandmark(st, ctx, LandmarkType.FRONT, themeSectionLabel(theme, 1, "RESERVE STAGING"), "Enemy reserve ships waiting to counterattack",
                reserveX, reserveY, 330.0, new Color(170, 88, 88, 18), new Color(248, 170, 170, 158));
        addLandmark(st, ctx, LandmarkType.COLONY, "FAINT TRANSPONDER", "Broken ships and drifting manifests may hide something useful",
                laneXFar, laneY, 220.0, new Color(122, 132, 168, 14), new Color(216, 226, 255, 120));
        addLandmark(st, ctx, LandmarkType.RELAY, "DISTRESS ECHO", "A weak coalition ping flickers beyond the relay lane",
                supportX, supportY - 110.0, 210.0,
                new Color(120, 164, 142, 14), new Color(196, 244, 228, 128));
        addLandmark(st, ctx, LandmarkType.FRONT, "DARK PICKET", "A silent contact sits just off the reserve lane",
                reserveX + (enteredFromRight ? -120.0 : 120.0), reserveY + 90.0, 210.0,
                new Color(154, 110, 110, 12), new Color(242, 184, 184, 124));

        spawnCampaignAsteroidPocket(ctx, resourceX, resourceY, 10 + Math.min(4, st.sector / 6), 1.45, true);
        spawnCampaignAsteroidPocket(ctx, laneXFar, laneY, 4 + Math.min(3, st.sector / 8), 0.75, false);
        spawnCampaignSalvagePocket(ctx, resourceX + 110.0, resourceY + 80.0, 4);
        spawnCampaignSalvagePocket(ctx, supportX - 90.0, supportY - 70.0, 2);
        addMissionThemeSetpieces(ctx, st, theme, laneXNear, laneXMid, laneXFar, laneY, resourceX, resourceY, supportX, supportY, reserveX, reserveY);

        spawnCampaignPatrolBand(ctx, st, laneXNear, laneY, 0);
        spawnCampaignPatrolBand(ctx, st, laneXMid, laneY + 110.0 * (((st.sector & 1) == 0) ? -1.0 : 1.0), 1);
        spawnCampaignReserveNode(ctx, st, reserveX, reserveY);
        spawnCampaignSupportPocket(ctx, st, supportX, supportY);
        configureMissionSections(st, theme, laneXNear, laneY, laneXMid, resourceX, resourceY, supportX, supportY, reserveX, reserveY);
        configureDiscoveries(ctx, st, enteredFromRight);
        seedAmbientDiscoveryPresence(ctx, st);

        st.objectivePhaseLabel = appendHudClause(st.objectivePhaseLabel, "MAP: " + missionThemeLead(theme));
        st.threatStateLabel = appendHudClause(st.threatStateLabel,
                "CONTACTS: " + missionThemeHudLabel(theme) + " pockets, support tracks, and scanner anomalies are all active.");
    }

    private static void addMissionThemeSetpieces(GameContext ctx, CampaignState st, MissionTheme theme,
                                                 double laneXNear, double laneXMid, double laneXFar, double laneY,
                                                 double resourceX, double resourceY,
                                                 double supportX, double supportY,
                                                 double reserveX, double reserveY) {
        if (ctx == null || st == null) return;
        switch (theme == null ? MissionTheme.BREAKTHROUGH : theme) {
            case SALVAGE_RUN -> {
                addLandmark(st, ctx, LandmarkType.COLONY, "RECOVERY VEIL", "Broken support hulls and work crews are cluttering the route.", laneXFar, laneY - 120.0, 240.0, new Color(196, 172, 118, 16), new Color(255, 228, 176, 122));
                spawnCampaignSalvagePocket(ctx, laneXMid - 100.0, laneY + 90.0, 5);
                spawnCampaignAsteroidPocket(ctx, supportX + 120.0, supportY + 30.0, 5, 0.9, false);
            }
            case RELAY_DEFENSE -> {
                addLandmark(st, ctx, LandmarkType.RELAY, "HANDOFF SPINE", "A live support spine is feeding escorts and telemetry into the fight.", supportX + 90.0, supportY - 110.0, 240.0, new Color(118, 196, 170, 16), new Color(194, 245, 225, 124));
                spawnCampaignShip(ctx, ShipRole.FRIGATE, greenSupportFaction(st), supportX + 70.0, supportY + 40.0, "Relay Guard");
                spawnCampaignShip(ctx, ShipRole.PICKET, greenSupportFaction(st), supportX - 120.0, supportY - 60.0, "Relay Screen");
            }
            case MINE_CORRIDOR -> {
                addLandmark(st, ctx, LandmarkType.FRONT, "DIRTY CROSSING", "Mine-control fragments and dark contacts are fouling the crossing lane.", laneXNear + 100.0, laneY - 120.0, 260.0, new Color(176, 102, 92, 15), new Color(246, 184, 176, 124));
                spawnCampaignSalvagePocket(ctx, laneXNear - 130.0, laneY + 70.0, 2);
            }
            case PRISON_BREAK -> {
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "BREAKCHAIN WAKE", "Released prisoner traffic and rescue tenders are tangling up the route.", supportX - 40.0, supportY + 120.0, 245.0, new Color(198, 170, 120, 14), new Color(255, 228, 172, 124));
                spawnCampaignShip(ctx, ShipRole.HAULER, greenSupportFaction(st), supportX + 120.0, supportY + 20.0, "Recovery Hauler");
                spawnCampaignShip(ctx, ShipRole.PATROL, greenSupportFaction(st), supportX - 100.0, supportY - 50.0, "Liberation Escort");
            }
            case ANOMALY_STORM -> {
                addLandmark(st, ctx, LandmarkType.RING, "PHASE KNOT", "Charge bleed and shear fronts are breaking up clean lines of attack.", laneXMid + 110.0, laneY - 140.0, 270.0, new Color(116, 124, 208, 14), new Color(198, 208, 255, 118));
                spawnCampaignAsteroidPocket(ctx, reserveX - 120.0, reserveY + 80.0, 3, 0.55, false);
                spawnCampaignSalvagePocket(ctx, supportX + 120.0, supportY - 100.0, 3);
            }
            case BREAKTHROUGH -> {
                addLandmark(st, ctx, LandmarkType.CORRIDOR, "BREACH WAKE", "The lane is still open enough for a direct punch if the screen breaks.", laneXFar + 100.0, laneY + 110.0, 220.0, new Color(132, 166, 220, 14), new Color(208, 228, 255, 116));
            }
        }
    }

    private static int[] shuffledMissionColumns(GameContext ctx, CampaignState st, boolean enteredFromRight, String tag) {
        int[] cols = new int[MISSION_ZONE_COLUMNS];
        for (int i = 0; i < MISSION_ZONE_COLUMNS; i++) cols[i] = i;
        Random rng = missionLayoutRandom(ctx, st, tag);
        for (int i = cols.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = cols[i];
            cols[i] = cols[j];
            cols[j] = tmp;
        }
        if (enteredFromRight) {
            for (int i = 0; i < cols.length; i++) cols[i] = MISSION_ZONE_COLUMNS - 1 - cols[i];
        }
        return cols;
    }

    private static int[] missionColumnsWithArrivalSafety(int[] cols, int entryCol, boolean preferNearestSafe) {
        if (cols == null || cols.length == 0) return new int[0];
        int[] out = cols.clone();
        int safeDepth = missionArrivalSafeColumnDepth(entryCol);
        int write = 0;
        for (int col : cols) {
            if (!isMissionArrivalSafeColumn(col, entryCol, safeDepth)) {
                out[write++] = col;
            }
        }
        int safeStart = write;
        for (int col : cols) {
            if (isMissionArrivalSafeColumn(col, entryCol, safeDepth)) {
                out[write++] = col;
            }
        }
        if (safeStart > 1) {
            int preferredIndex = 0;
            double preferredDistance = preferNearestSafe ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            for (int i = 0; i < safeStart; i++) {
                double distance = Math.abs(out[i] - entryCol);
                if ((preferNearestSafe && distance < preferredDistance)
                        || (!preferNearestSafe && distance > preferredDistance)) {
                    preferredDistance = distance;
                    preferredIndex = i;
                }
            }
            if (preferredIndex != 0) {
                int tmp = out[0];
                out[0] = out[preferredIndex];
                out[preferredIndex] = tmp;
            }
        }
        return out;
    }

    private static int missionArrivalSafeColumnDepth(int entryCol) {
        if (entryCol <= 0 || entryCol >= MISSION_ZONE_COLUMNS - 1) {
            return MISSION_EDGE_ENTRY_SAFE_COLUMN_DEPTH;
        }
        return MISSION_INTERIOR_ENTRY_SAFE_COLUMN_DEPTH;
    }

    private static boolean isMissionArrivalSafeColumn(int candidateCol, int entryCol, int safeDepth) {
        if (candidateCol < 0 || entryCol < 0 || safeDepth <= 0) return false;
        return Math.abs(candidateCol - entryCol) < safeDepth;
    }

    private static int relocateColumnAwayFromArrival(int candidateCol, int entryCol) {
        int safeDepth = missionArrivalSafeColumnDepth(entryCol);
        if (!isMissionArrivalSafeColumn(candidateCol, entryCol, safeDepth)) return candidateCol;
        int best = candidateCol;
        int bestDistance = Integer.MAX_VALUE;
        for (int col = 0; col < MISSION_ZONE_COLUMNS; col++) {
            if (isMissionArrivalSafeColumn(col, entryCol, safeDepth)) continue;
            int distance = Math.abs(col - candidateCol);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = col;
            }
        }
        return best;
    }

    private static boolean discoverySeedsHostilePresence(DiscoveryKind kind) {
        if (kind == null) return false;
        return switch (kind) {
            case AMBUSH, MINEFIELD, WRECK_FIELD -> true;
            default -> false;
        };
    }

    private static Random missionLayoutRandom(GameContext ctx, CampaignState st, String tag) {
        long seed = 0x9E3779B97F4A7C15L;
        if (ctx != null && ctx.config != null) seed ^= ctx.config.seed;
        if (st != null) {
            seed ^= (long) st.sector * 0x632BE59BD9B4E019L;
            seed ^= (long) (st.loadedMissionSubzone + 31) * 0x94D049BB133111EBL;
        }
        if (tag != null) seed ^= ((long) tag.hashCode() << 21);
        return new Random(seed);
    }

    private static MissionTheme missionThemeForSector(CampaignState st) {
        if (st == null) return MissionTheme.BREAKTHROUGH;
        return switch (st.sector) {
            case 1, 3, 10, 14, 18, 22 -> MissionTheme.BREAKTHROUGH;
            case 6, 8, 11, 20 -> MissionTheme.SALVAGE_RUN;
            case 4, 5, 9, 12, 13, 17 -> MissionTheme.RELAY_DEFENSE;
            case 2, 7, 15, 16, 21 -> MissionTheme.MINE_CORRIDOR;
            case 19, 23 -> MissionTheme.PRISON_BREAK;
            case 24 -> MissionTheme.ANOMALY_STORM;
            default -> switch (st.objectiveType) {
                case ESCORT, CAPTURE -> MissionTheme.RELAY_DEFENSE;
                case SURVIVE -> MissionTheme.SALVAGE_RUN;
                case BOSS, FINAL_BOSS -> MissionTheme.ANOMALY_STORM;
                default -> MissionTheme.BREAKTHROUGH;
            };
        };
    }

    private static String missionThemeHudLabel(MissionTheme theme) {
        if (theme == null) return "BREAKTHROUGH";
        return switch (theme) {
            case BREAKTHROUGH -> "BREAKTHROUGH";
            case SALVAGE_RUN -> "SALVAGE RUN";
            case RELAY_DEFENSE -> "RELAY DEFENSE";
            case MINE_CORRIDOR -> "MINE CORRIDOR";
            case PRISON_BREAK -> "PRISON BREAK";
            case ANOMALY_STORM -> "ANOMALY STORM";
        };
    }

    private static String missionThemeLead(MissionTheme theme) {
        if (theme == null) return "";
        return switch (theme) {
            case BREAKTHROUGH -> "Push through layered enemy pockets and keep the route open.";
            case SALVAGE_RUN -> "Work a cluttered field with enough breathing room to mine, recover, and regroup.";
            case RELAY_DEFENSE -> "Fight around friendly handoff nodes and protect the support spine.";
            case MINE_CORRIDOR -> "Expect trap pockets, dirty lanes, and flank contacts instead of a clean front.";
            case PRISON_BREAK -> "Split the line, hit captivity assets, and keep the recovery lane alive.";
            case ANOMALY_STORM -> "Expect unstable geometry, scattered contacts, and a less predictable front.";
        };
    }

    private static void configureMissionSections(CampaignState st,
                                                 MissionTheme theme,
                                                 double laneXNear, double laneY, double laneXMid,
                                                 double resourceX, double resourceY,
                                                 double supportX, double supportY,
                                                 double reserveX, double reserveY) {
        if (st == null) return;
        st.missionSections.clear();
        st.activeMissionSection = 0;
        st.missionSectionTravelLocked = false;

        switch (st.objectiveType) {
            case SURVIVE -> {
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 0, "FORWARD SCREEN"), laneXNear, laneY, 300.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 1, "SUPPORT RELAY"), supportX, supportY, 280.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 2, "RESERVE STAGING"), reserveX, reserveY, 320.0));
            }
            case DESTROY -> {
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 0, "FORWARD SCREEN"), laneXMid, laneY, 300.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 1, "RESERVE STAGING"), reserveX, reserveY, 320.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 2, "SUPPORT RELAY"), supportX, supportY, 280.0));
            }
            case ESCORT -> {
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 0, "FORWARD SCREEN"), laneXNear, laneY, 300.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 1, "RESOURCE POCKET"), resourceX, resourceY, 270.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 2, "SUPPORT RELAY"), supportX, supportY, 280.0));
            }
            case CAPTURE -> {
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 0, "FORWARD SCREEN"), laneXMid, laneY, 300.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 1, "SUPPORT RELAY"), supportX, supportY, 280.0));
                st.missionSections.add(new MissionSection(themeSectionLabel(theme, 2, "RESERVE STAGING"), reserveX, reserveY, 320.0));
            }
            case BOSS, FINAL_BOSS -> { }
        }
    }

    private static String themeSectionLabel(MissionTheme theme, int slot, String fallback) {
        if (theme == null) return fallback;
        return switch (theme) {
            case BREAKTHROUGH -> switch (slot) {
                case 0 -> "ASSAULT LANE";
                case 1 -> "BREACH POINT";
                case 2 -> "SUPPORT WAKE";
                default -> fallback;
            };
            case SALVAGE_RUN -> switch (slot) {
                case 0 -> "DEBRIS FRONT";
                case 1 -> "RECOVERY POCKET";
                case 2 -> "TENDER SCREEN";
                default -> fallback;
            };
            case RELAY_DEFENSE -> switch (slot) {
                case 0 -> "SCREEN RING";
                case 1 -> "HANDOFF NODE";
                case 2 -> "RELAY SHADOW";
                default -> fallback;
            };
            case MINE_CORRIDOR -> switch (slot) {
                case 0 -> "TRAP LANE";
                case 1 -> "DIRTY CROSSING";
                case 2 -> "KILL POCKET";
                default -> fallback;
            };
            case PRISON_BREAK -> switch (slot) {
                case 0 -> "CHAIN FRONT";
                case 1 -> "RECOVERY RUN";
                case 2 -> "BREAKOUT COVER";
                default -> fallback;
            };
            case ANOMALY_STORM -> switch (slot) {
                case 0 -> "SHEAR FRONT";
                case 1 -> "PHASE KNOT";
                case 2 -> "GHOST WAKE";
                default -> fallback;
            };
        };
    }

    private static void configureDiscoveries(GameContext ctx, CampaignState st, boolean enteredFromRight) {
        if (ctx == null || st == null) return;
        st.discoverySites.clear();
        st.discoveriesFound = 0;
        st.recoverableWreckSites.clear();
        st.recoverableWrecksClaimed = 0;
        addThemeDiscoverySites(ctx, st, enteredFromRight, st.missionTheme);
        trimDiscoverySitesToSectorBudget(st);
        addDiscoveryScannerLandmarks(ctx, st);
    }

    private static void addThemeDiscoverySites(GameContext ctx, CampaignState st, boolean enteredFromRight, MissionTheme theme) {
        switch (theme == null ? MissionTheme.BREAKTHROUGH : theme) {
            case SALVAGE_RUN -> {
                addDiscoverySite(ctx, st, enteredFromRight, 0, 1, 10.0, -180.0, 170.0, "Salvage Drift", "Recovery hulls and black boxes are still loose in the wake.", DiscoveryKind.SALVAGE_HULK);
                addDiscoverySite(ctx, st, enteredFromRight, 0, 2, 120.0, 120.0, 165.0, "Fuel Locker", "A resupply cache drifted out of formation and is still intact.", DiscoveryKind.SUPPLY_CACHE);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 0, -130.0, -60.0, 175.0, "Dead Tender", "A support tender is split open with recoverable stores still aboard.", DiscoveryKind.CACHE);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 0, 120.0, 110.0, 185.0, "Prospector Bloom", "Ore fragments and clipped cargo pods glitter across the pocket.", DiscoveryKind.ORE);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 2, -170.0, -140.0, 170.0, "Recovery Beacon", "Friendly tenders are pulsing a weak homing signal through the debris.", DiscoveryKind.REINFORCEMENT);
                addDiscoverySite(ctx, st, enteredFromRight, 3, 1, 150.0, -130.0, 175.0, "Trap Pods", "Inactive mines are buried inside the recovery route.", DiscoveryKind.MINEFIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 4, 0, -110.0, 90.0, 195.0, "Wreck Canyon", "Broken hull slabs form a drifting salvage canyon.", DiscoveryKind.WRECK_FIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 2, -150.0, 130.0, 190.0, "Prototype Fleet Cradle", "A sealed war cradle is hanging behind the working field.", DiscoveryKind.FLEET_ASSET);
            }
            case RELAY_DEFENSE -> {
                addDiscoverySite(ctx, st, enteredFromRight, 0, 0, -70.0, 120.0, 180.0, "Relay Fringe Trap", "A false beacon is trying to pull escorts off the handoff lane.", DiscoveryKind.AMBUSH);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 2, -160.0, 120.0, 170.0, "Support Echo", "Friendly traffic and repair bursts are clustering near the node.", DiscoveryKind.REINFORCEMENT);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 1, -120.0, 40.0, 175.0, "Broker Waystation", "Neutral logistics craft are ghosting along the relay shadow.", DiscoveryKind.NEUTRAL_TRADER);
                addDiscoverySite(ctx, st, enteredFromRight, 3, 0, 90.0, -110.0, 165.0, "Ghost Relay", "A half-dead data spine is still pushing tactical telemetry into the dark.", DiscoveryKind.DATA_RELAY);
                addDiscoverySite(ctx, st, enteredFromRight, 3, 2, 120.0, 70.0, 180.0, "Coalition Service Spur", "A sealed service spur still has spare cells and foam aboard.", DiscoveryKind.SUPPLY_CACHE);
                addDiscoverySite(ctx, st, enteredFromRight, 4, 1, -190.0, -130.0, 170.0, "Drifting Weapon Platform", "An old relay buoy still holds a live defense package.", DiscoveryKind.DRIFTING_TURRET);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 0, -130.0, -120.0, 185.0, "Warp-Shear Anomaly", "Charge bleed and sensor ghosts are knotting near the relay edge.", DiscoveryKind.ANOMALY);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 1, 200.0, 160.0, 175.0, "Dark Picket Shadow", "A silent contact is drifting just beyond the support line.", DiscoveryKind.AMBUSH);
            }
            case MINE_CORRIDOR -> {
                addDiscoverySite(ctx, st, enteredFromRight, 0, 1, 0.0, -200.0, 170.0, "Drift Mine Cluster", "Inactive signatures are nested along the lane like a snap-trap.", DiscoveryKind.MINEFIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 0, 0, -90.0, 130.0, 180.0, "Mute Beacon Trap", "A distress ping keeps repeating from a pocket that should already be dark.", DiscoveryKind.AMBUSH);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 2, -140.0, 120.0, 170.0, "Emergency Cache", "Fleet-grade foam and missile pallets are hidden off the dirty lane.", DiscoveryKind.SUPPLY_CACHE);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 0, 130.0, 110.0, 185.0, "Kill-Web Nodes", "Mine-control hardware is suspended in a tight interdiction knot.", DiscoveryKind.MINEFIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 2, -180.0, -150.0, 165.0, "Coalition Distress Echo", "A support transponder is flickering near a mined relay fringe.", DiscoveryKind.REINFORCEMENT);
                addDiscoverySite(ctx, st, enteredFromRight, 4, 0, -110.0, 90.0, 195.0, "Wreck Field", "Hull plates and dead drives are rotating through a dirty choke.", DiscoveryKind.WRECK_FIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 4, 2, 150.0, -90.0, 175.0, "Silent Barge", "A darkened prison hulk is drifting with sealed traffic logs.", DiscoveryKind.PRISON_BARGE);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 2, -150.0, 130.0, 190.0, "Prototype Recovery Cradle", "A salvage cradle hangs beyond the mined route.", DiscoveryKind.FLEET_ASSET);
            }
            case PRISON_BREAK -> {
                addDiscoverySite(ctx, st, enteredFromRight, 0, 1, 20.0, -210.0, 170.0, "Broken Chain Cache", "Released clamps and scuttled pods are still drifting through the line.", DiscoveryKind.CACHE);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 0, -120.0, -80.0, 175.0, "Detention Hulk", "A torn prison support hull is bleeding escape telemetry.", DiscoveryKind.PRISON_BARGE);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 2, -150.0, 100.0, 170.0, "Liberation Signal", "Friendly recovery traffic is searching for survivors off the lane.", DiscoveryKind.REINFORCEMENT);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 1, -140.0, 60.0, 175.0, "Broker Runner", "A neutral courier is trying to slip through the chaos.", DiscoveryKind.NEUTRAL_TRADER);
                addDiscoverySite(ctx, st, enteredFromRight, 3, 1, 140.0, -150.0, 175.0, "Clamp Mine Knot", "Chain-control mines are nested in a tight interdiction web.", DiscoveryKind.MINEFIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 4, 0, -110.0, 90.0, 195.0, "Escape Wreck Line", "Broken clamps, pods, and venting hulls mark the breakout route.", DiscoveryKind.WRECK_FIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 0, -130.0, -140.0, 185.0, "Panic Shear", "Violent local distortion is scrambling rescue telemetry.", DiscoveryKind.ANOMALY);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 2, -150.0, 130.0, 190.0, "Recovered Fleet Cradle", "A sealed fleet cradle is drifting beyond the prison lane.", DiscoveryKind.FLEET_ASSET);
            }
            case ANOMALY_STORM -> {
                addDiscoverySite(ctx, st, enteredFromRight, 0, 0, -80.0, 130.0, 180.0, "Storm Beacon", "A fractured beacon is flickering through the shear front.", DiscoveryKind.ANOMALY);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 0, -120.0, -80.0, 175.0, "Ghost Hulk", "A torn hull is phasing in and out of the contact picture.", DiscoveryKind.SALVAGE_HULK);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 2, -150.0, 110.0, 170.0, "Signal Fires", "Friendly burst traffic is ghosting in and out of the storm.", DiscoveryKind.REINFORCEMENT);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 1, -140.0, 60.0, 175.0, "Echo Market", "Neutral traffic is trying to sell passage through the distortion.", DiscoveryKind.NEUTRAL_TRADER);
                addDiscoverySite(ctx, st, enteredFromRight, 3, 0, 80.0, -120.0, 165.0, "Ghost Relay", "A half-dead data spine is still whispering through the phase knot.", DiscoveryKind.DATA_RELAY);
                addDiscoverySite(ctx, st, enteredFromRight, 4, 1, -200.0, -150.0, 170.0, "Phase Battery", "A weapon buoy is surfacing inside the shimmer.", DiscoveryKind.DRIFTING_TURRET);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 0, -130.0, -140.0, 185.0, "Warp-Shear Anomaly", "Sensor ghosts, gravity shimmer, and charge echoes are knotting together here.", DiscoveryKind.ANOMALY);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 2, -150.0, 130.0, 190.0, "Prototype Fleet Cradle", "A sealed war crate and emergency dock frame are hanging beyond the route.", DiscoveryKind.FLEET_ASSET);
            }
            case BREAKTHROUGH -> {
                addDiscoverySite(ctx, st, enteredFromRight, 0, 1, 0.0, -210.0, 170.0, "Debris Cache", "Black-boxes and salvage pods are tumbling off the route lane.", DiscoveryKind.CACHE);
                addDiscoverySite(ctx, st, enteredFromRight, 0, 0, -80.0, 130.0, 180.0, "Mute Beacon Trap", "A distress ping keeps repeating from a pocket that should already be dark.", DiscoveryKind.AMBUSH);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 0, -120.0, -80.0, 175.0, "Abandoned Hulk", "A torn support hull is drifting with power flickers and open cargo bays.", DiscoveryKind.SALVAGE_HULK);
                addDiscoverySite(ctx, st, enteredFromRight, 1, 2, -150.0, 110.0, 170.0, "Signal Fires", "Friendly burst traffic and shield blooms flicker through the debris haze.", DiscoveryKind.REINFORCEMENT);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 0, 130.0, 110.0, 185.0, "Prospector Bloom", "Ore fragments and clipped cargo pods glitter around the pocket.", DiscoveryKind.ORE);
                addDiscoverySite(ctx, st, enteredFromRight, 2, 1, -140.0, 60.0, 175.0, "Broker Waystation", "Neutral logistics craft are ghosting through the lane under sealed running lights.", DiscoveryKind.NEUTRAL_TRADER);
                addDiscoverySite(ctx, st, enteredFromRight, 3, 1, 140.0, -150.0, 175.0, "Kill-Web Nodes", "Dormant mine-control hardware is suspended in a tight interdiction knot.", DiscoveryKind.MINEFIELD);
                addDiscoverySite(ctx, st, enteredFromRight, 5, 2, -150.0, 130.0, 190.0, "Prototype Fleet Cradle", "A sealed war crate and emergency dock frame are hanging beyond the route.", DiscoveryKind.FLEET_ASSET);
            }
        }
    }

    private static void trimDiscoverySitesToSectorBudget(CampaignState st) {
        if (st == null || st.discoverySites.isEmpty()) return;
        int targetCount = Math.max(3, Math.min(6, 3 + (Math.max(1, st.sector) / 5)));
        if (st.discoverySites.size() <= targetCount) return;

        ArrayList<DiscoverySite> ordered = new ArrayList<>(st.discoverySites);
        ordered.sort((a, b) -> Double.compare(discoverySelectionScore(st, b), discoverySelectionScore(st, a)));

        st.discoverySites.clear();
        for (int i = 0; i < targetCount && i < ordered.size(); i++) {
            st.discoverySites.add(ordered.get(i));
        }
    }

    private static double discoverySelectionScore(CampaignState st, DiscoverySite site) {
        if (site == null) return Double.NEGATIVE_INFINITY;
        double score = discoveryBasePriority(site.kind);
        score += ((Math.abs(site.label.hashCode()) % 97) / 1000.0);
        if (st != null) {
            int sectorBias = Math.max(1, st.sector);
            switch (site.kind) {
                case CACHE, SUPPLY_CACHE, SALVAGE_HULK, WRECK_FIELD -> {
                    if (sectorBias <= 8) score += 1.2;
                }
                case REINFORCEMENT, DATA_RELAY, NEUTRAL_TRADER, PRISON_BARGE -> {
                    if (sectorBias >= 6) score += 0.8;
                }
                case MINEFIELD, AMBUSH, ANOMALY, FLEET_ASSET -> {
                    if (sectorBias >= 10) score += 1.0;
                }
                case ORE, DRIFTING_TURRET -> score += 0.3;
            }
        }
        return score;
    }

    private static double discoveryBasePriority(DiscoveryKind kind) {
        if (kind == null) return 0.0;
        return switch (kind) {
            case REINFORCEMENT, DATA_RELAY, ANOMALY, FLEET_ASSET -> 5.0;
            case AMBUSH, MINEFIELD, PRISON_BARGE, NEUTRAL_TRADER -> 4.0;
            case SALVAGE_HULK, SUPPLY_CACHE, WRECK_FIELD -> 3.4;
            case ORE, DRIFTING_TURRET, CACHE -> 2.8;
        };
    }

    private static void addDiscoveryScannerLandmarks(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.discoverySites.isEmpty()) return;
        for (DiscoverySite site : st.discoverySites) {
            if (site == null) continue;
            double radius = Math.max(140.0, site.radius + 40.0);
            double clampedX = GameMath.clamp(site.x, radius + 120.0, ctx.WORLD_W - radius - 120.0);
            double clampedY = GameMath.clamp(site.y, radius + 120.0, ctx.WORLD_H - radius - 120.0);
            st.landmarks.add(new CampaignLandmark(
                    discoveryLandmarkType(site.kind),
                    "SCAN CONTACT: " + site.label,
                    site.subtitle,
                    clampedX,
                    clampedY,
                    radius,
                    discoveryLandmarkFill(site.kind),
                    discoveryLandmarkEdge(site.kind),
                    true
            ));
        }
    }

    private static LandmarkType discoveryLandmarkType(DiscoveryKind kind) {
        if (kind == null) return LandmarkType.COLONY;
        return switch (kind) {
            case DATA_RELAY, REINFORCEMENT -> LandmarkType.RELAY;
            case AMBUSH, MINEFIELD -> LandmarkType.FRONT;
            case ANOMALY -> LandmarkType.RING;
            default -> LandmarkType.COLONY;
        };
    }

    private static Color discoveryLandmarkFill(DiscoveryKind kind) {
        if (kind == null) return new Color(134, 148, 176, 12);
        return switch (kind) {
            case REINFORCEMENT, SUPPLY_CACHE, DATA_RELAY -> new Color(116, 196, 168, 14);
            case AMBUSH, MINEFIELD -> new Color(176, 90, 90, 14);
            case ORE, CACHE, SALVAGE_HULK, WRECK_FIELD, FLEET_ASSET -> new Color(206, 178, 108, 14);
            case ANOMALY -> new Color(112, 124, 212, 12);
            default -> new Color(134, 148, 176, 12);
        };
    }

    private static Color discoveryLandmarkEdge(DiscoveryKind kind) {
        if (kind == null) return new Color(222, 234, 255, 118);
        return switch (kind) {
            case REINFORCEMENT, SUPPLY_CACHE, DATA_RELAY -> new Color(194, 246, 228, 132);
            case AMBUSH, MINEFIELD -> new Color(248, 178, 178, 132);
            case ORE, CACHE, SALVAGE_HULK, WRECK_FIELD, FLEET_ASSET -> new Color(255, 232, 168, 138);
            case ANOMALY -> new Color(198, 208, 255, 124);
            default -> new Color(222, 234, 255, 118);
        };
    }

    private static void addDiscoverySite(GameContext ctx, CampaignState st, boolean enteredFromRight,
                                         int column, int row, double dx, double dy, double radius,
                                         String label, String subtitle, DiscoveryKind kind) {
        if (ctx == null || st == null) return;
        int col = Math.max(0, Math.min(MISSION_ZONE_COLUMNS - 1,
                enteredFromRight ? (MISSION_ZONE_COLUMNS - 1 - column) : column));
        if (discoverySeedsHostilePresence(kind)) {
            col = relocateColumnAwayFromArrival(col, missionSubzoneColumn(st.loadedMissionSubzone));
        }
        int subzone = missionSubzoneIndex(col, Math.max(0, Math.min(MISSION_ZONE_ROWS - 1, row)));
        double centerX = missionSubzoneCenterX(ctx, st.sector, subzone);
        double centerY = missionSubzoneCenterY(ctx, st.sector, subzone);
        double offsetX = enteredFromRight ? -dx : dx;
        double[] jitter = jitteredDiscoveryPoint(ctx, st, label, centerX, centerY, offsetX, dy, radius);
        st.discoverySites.add(new DiscoverySite(label, subtitle, kind, jitter[0], jitter[1], radius));
    }

    private static double[] jitteredDiscoveryPoint(GameContext ctx, CampaignState st, String label,
                                                   double centerX, double centerY,
                                                   double baseOffsetX, double baseOffsetY, double radius) {
        double targetX = centerX + baseOffsetX;
        double targetY = centerY + baseOffsetY;
        if (ctx == null || st == null) return new double[]{targetX, targetY};

        MissionLayout layout = missionLayout(ctx);
        double limitX = Math.max(140.0, layout.subzoneWidth * 0.18);
        double limitY = Math.max(140.0, layout.subzoneHeight * 0.18);
        Random rng = missionLayoutRandom(ctx, st, "discovery:" + ((label == null) ? "unknown" : label));
        double bestX = targetX;
        double bestY = targetY;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 6; attempt++) {
            double jx = baseOffsetX + (rng.nextDouble() - 0.5) * 2.0 * limitX;
            double jy = baseOffsetY + (rng.nextDouble() - 0.5) * 2.0 * limitY;
            double x = GameMath.clamp(centerX + jx,
                    centerX - layout.subzoneWidth * 0.5 + CAMPAIGN_POCKET_MARGIN,
                    centerX + layout.subzoneWidth * 0.5 - CAMPAIGN_POCKET_MARGIN);
            double y = GameMath.clamp(centerY + jy,
                    centerY - layout.subzoneHeight * 0.5 + CAMPAIGN_POCKET_MARGIN,
                    centerY + layout.subzoneHeight * 0.5 - CAMPAIGN_POCKET_MARGIN);
            double score = minDiscoverySeparationScore(st, x, y);
            if (score > bestScore) {
                bestScore = score;
                bestX = x;
                bestY = y;
            }
            if (score >= DISCOVERY_JITTER_MIN_SEPARATION) break;
        }
        return new double[]{bestX, bestY};
    }

    private static double minDiscoverySeparationScore(CampaignState st, double x, double y) {
        if (st == null || st.discoverySites.isEmpty()) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (DiscoverySite existing : st.discoverySites) {
            if (existing == null) continue;
            double dist = Math.hypot(existing.x - x, existing.y - y);
            if (dist < best) best = dist;
        }
        return best;
    }

    private static void seedAmbientDiscoveryPresence(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.discoverySites.isEmpty()) return;
        for (DiscoverySite site : st.discoverySites) {
            if (site == null) continue;
            seedAmbientDiscoveryPresence(ctx, st, site);
        }
    }

    private static void seedAmbientDiscoveryPresence(GameContext ctx, CampaignState st, DiscoverySite site) {
        if (ctx == null || st == null || site == null) return;
        switch (site.kind) {
            case CACHE -> {
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 3);
                spawnCampaignAsteroidPocket(ctx, site.x + 80.0, site.y - 50.0, 2, 0.55, false);
                spawnCampaignShip(ctx, ShipRole.PATROL, greenSupportFaction(st), site.x - 110.0, site.y + 60.0, "Cache Sweep");
            }
            case ORE -> {
                spawnCampaignAsteroidPocket(ctx, site.x, site.y, 7, 1.15, true);
                spawnCampaignShip(ctx, ShipRole.MINER, greenSupportFaction(st), site.x - 80.0, site.y + 40.0, "Survey Prospector");
                spawnCampaignShip(ctx, ShipRole.PICKET, greenSupportFaction(st), site.x + 120.0, site.y - 70.0, "Survey Screen");
            }
            case REINFORCEMENT -> {
                Faction faction = greenSupportFaction(st);
                spawnCampaignShip(ctx, ShipRole.PATROL, faction, site.x + 70.0, site.y - 30.0, "Echo Scout");
                spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, site.x - 90.0, site.y + 70.0, "Echo Escort");
                if (st.sector >= 8) {
                    spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, faction, site.x + 150.0, site.y + 35.0, "Echo Flak Screen");
                }
                spawnCampaignSalvagePocket(ctx, site.x + 50.0, site.y - 80.0, 2);
            }
            case AMBUSH -> {
                spawnEnemyAtPoint(ctx, ShipRole.PATROL, site.x + 90.0, site.y - 50.0);
                spawnEnemyAtPoint(ctx, ShipRole.PICKET, site.x - 110.0, site.y + 80.0);
                if (st.sector >= 8) {
                    spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, site.x + 170.0, site.y + 20.0);
                }
                spawnCampaignSalvagePocket(ctx, site.x - 60.0, site.y - 70.0, 1);
            }
            case SALVAGE_HULK -> {
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 6);
                spawnCampaignAsteroidPocket(ctx, site.x - 100.0, site.y + 60.0, 2, 0.45, false);
                spawnCampaignShip(ctx, ShipRole.HAULER, greenSupportFaction(st), site.x + 100.0, site.y - 50.0, "Hulk Tender");
                addRecoverableWreckSite(st, site.x - 35.0, site.y + 25.0,
                        salvageRecoveryRoleForSector(st, site.kind),
                        site.label + " Recovery Frame",
                        "Recoverable hull scaffold hidden in the debris.");
            }
            case SUPPLY_CACHE -> {
                Faction faction = greenSupportFaction(st);
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 3);
                spawnCampaignShip(ctx, ShipRole.HAULER, faction, site.x - 60.0, site.y + 40.0, "Supply Runner");
                spawnCampaignShip(ctx, ShipRole.PICKET, faction, site.x + 110.0, site.y - 70.0, "Cache Guard");
            }
            case DATA_RELAY -> {
                Faction faction = greenSupportFaction(st);
                spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, faction, site.x, site.y, "Ghost Relay Node");
                spawnCampaignShip(ctx, ShipRole.PICKET, faction, site.x + 120.0, site.y - 80.0, "Relay Scout");
                if (st.sector >= 10) {
                    spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, site.x - 130.0, site.y + 60.0, "Relay Guard");
                }
                spawnCampaignSalvagePocket(ctx, site.x - 70.0, site.y + 40.0, 2);
            }
            case WRECK_FIELD -> {
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 8);
                spawnCampaignAsteroidPocket(ctx, site.x + 90.0, site.y - 40.0, 4, 0.8, false);
                spawnEnemyAtPoint(ctx, ShipRole.PATROL, site.x - 140.0, site.y + 60.0);
                addRecoverableWreckSite(st, site.x + 55.0, site.y - 35.0,
                        salvageRecoveryRoleForSector(st, site.kind),
                        "Recoverable Wreck Spine",
                        "A cracked war hull here could be reclaimed for the fleet.");
            }
            case MINEFIELD -> {
                spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.ENEMY, site.x - 120.0, site.y - 70.0, "Mine Anchor");
                spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.ENEMY, site.x + 110.0, site.y + 55.0, "Mine Anchor");
                spawnEnemyAtPoint(ctx, ShipRole.PICKET, site.x + 130.0, site.y + 90.0);
                if (st.sector >= 10) {
                    spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, site.x - 150.0, site.y + 120.0);
                }
                spawnCampaignAsteroidPocket(ctx, site.x + 40.0, site.y - 30.0, 2, 0.5, false);
            }
            case DRIFTING_TURRET -> {
                Faction faction = greenSupportFaction(st);
                spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, faction, site.x, site.y, "Dormant Defense Buoy");
                spawnCampaignShip(ctx, ShipRole.PATROL, faction, site.x - 95.0, site.y + 75.0, "Buoy Skirmisher");
                spawnCampaignSalvagePocket(ctx, site.x + 70.0, site.y - 70.0, 2);
            }
            case NEUTRAL_TRADER -> {
                Faction faction = greenSupportFaction(st);
                spawnCampaignShip(ctx, ShipRole.TRANSPORT, faction, site.x - 40.0, site.y, "Broker Spine");
                spawnCampaignShip(ctx, ShipRole.HAULER, faction, site.x - 140.0, site.y + 90.0, "Ledger Tender");
                spawnCampaignShip(ctx, ShipRole.MINER, faction, site.x + 120.0, site.y - 80.0, "Prospector Drift");
                spawnCampaignShip(ctx, ShipRole.PATROL, faction, site.x + 80.0, site.y + 100.0, "Broker Screen");
                spawnCampaignSalvagePocket(ctx, site.x - 30.0, site.y - 90.0, 1);
            }
            case PRISON_BARGE -> {
                Faction faction = yellowSupportFaction(st);
                spawnCampaignShip(ctx, ShipRole.TRANSPORT, faction, site.x - 30.0, site.y + 20.0, "Detention Barge");
                spawnCampaignShip(ctx, ShipRole.PATROL, faction, site.x + 100.0, site.y - 70.0, "Escape Screen");
                if (st.sector >= 12) {
                    spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, site.x + 160.0, site.y + 85.0, "Liberation Guard");
                }
                spawnCampaignSalvagePocket(ctx, site.x - 80.0, site.y + 90.0, 2);
            }
            case ANOMALY -> {
                spawnCampaignAsteroidPocket(ctx, site.x, site.y, 3, 0.95, true);
                spawnCampaignSalvagePocket(ctx, site.x + 70.0, site.y - 50.0, 2);
                spawnCampaignShip(ctx, ShipRole.PATROL, greenSupportFaction(st), site.x - 120.0, site.y + 60.0, "Anomaly Scout");
                addRecoverableWreckSite(st, site.x + 35.0, site.y + 40.0,
                        salvageRecoveryRoleForSector(st, site.kind),
                        "Anomaly Wreck Echo",
                        "The anomaly is preserving a reclaimable hull shell.");
            }
            case FLEET_ASSET -> {
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 4);
                Faction faction = Faction.ALLY;
                if (st.sector >= 16) {
                    spawnCampaignShip(ctx, ShipRole.LIGHT_CRUISER, faction, site.x + 20.0, site.y - 10.0, "Cradle Escort");
                } else {
                    spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, site.x + 20.0, site.y - 10.0, "Cradle Escort");
                }
                spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, faction, site.x - 110.0, site.y + 75.0, "Cradle Screen");
                addRecoverableWreckSite(st, site.x, site.y + 20.0,
                        salvageRecoveryRoleForSector(st, site.kind),
                        "Prototype Recovery Cradle",
                        "A fleet-grade chassis can be reclaimed if the lane is secure.");
            }
        }
    }

    private static void addRecoverableWreckSite(CampaignState st, double x, double y, ShipRole role, String label, String subtitle) {
        if (st == null || role == null) return;
        for (RecoverableWreckSite existing : st.recoverableWreckSites) {
            if (existing == null || existing.claimed) continue;
            if (GameMath.dist2(existing.x, existing.y, x, y) <= 180.0 * 180.0) return;
        }
        st.recoverableWreckSites.add(new RecoverableWreckSite(label, subtitle, role, x, y, 175.0));
    }

    private static ShipRole salvageRecoveryRoleForSector(CampaignState st, DiscoveryKind kind) {
        int sector = (st == null) ? 1 : Math.max(1, st.sector);
        if (kind == DiscoveryKind.FLEET_ASSET) {
            if (sector >= 18) return ShipRole.BATTLECRUISER;
            if (sector >= 12) return ShipRole.LIGHT_CRUISER;
            return ShipRole.FRIGATE;
        }
        if (kind == DiscoveryKind.ANOMALY) {
            if (sector >= 16) return ShipRole.ARTILLERY_SHIP;
            if (sector >= 10) return ShipRole.STEALTH_SHIP;
            return ShipRole.PICKET;
        }
        if (kind == DiscoveryKind.WRECK_FIELD) {
            if (sector >= 14) return ShipRole.MISSILE_BOAT;
            if (sector >= 8) return ShipRole.CIWS_CORVETTE;
            return ShipRole.PATROL;
        }
        if (sector >= 14) return ShipRole.FRIGATE;
        if (sector >= 8) return ShipRole.PICKET;
        return ShipRole.HAULER;
    }

    private static String appendHudClause(String base, String addition) {
        if (addition == null || addition.isBlank()) return (base == null) ? "" : base;
        if (base == null || base.isBlank()) return addition;
        return base + "   " + addition;
    }

    private static double zonePocketX(double zoneX, double fraction) {
        double zoneWidth = missionLayout((GameConfig) null).zoneWidth;
        return GameMath.clamp(zoneX + zoneWidth * fraction,
                zoneX + CAMPAIGN_POCKET_MARGIN,
                zoneX + zoneWidth - CAMPAIGN_POCKET_MARGIN);
    }

    private static double zonePocketY(double zoneY, double fraction) {
        double zoneHeight = missionLayout((GameConfig) null).zoneHeight;
        return GameMath.clamp(zoneY + zoneHeight * fraction,
                zoneY + CAMPAIGN_POCKET_MARGIN,
                zoneY + zoneHeight - CAMPAIGN_POCKET_MARGIN);
    }

    private static void spawnCampaignAsteroidPocket(GameContext ctx, double cx, double cy, int count, double oreMul, boolean forceRich) {
        if (ctx == null || count <= 0) return;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i / Math.max(1, count)) + ctx.rng.nextDouble() * 0.35;
            double dist = 70.0 + ctx.rng.nextDouble() * 180.0;
            double x = GameMath.clamp(cx + Math.cos(angle) * dist, 80.0, ctx.WORLD_W - 80.0);
            double y = GameMath.clamp(cy + Math.sin(angle) * dist, 80.0, ctx.WORLD_H - 80.0);
            double radius = 20.0 + ctx.rng.nextDouble() * 36.0;
            int ore = Math.max(120, (int) Math.round((220.0 + ctx.rng.nextDouble() * 520.0) * oreMul));
            Asteroid asteroid = new Asteroid(x, y, radius, ore);
            if (forceRich || ore >= 520) {
                asteroid.rich = true;
                asteroid.richness = Math.max(asteroid.richness, 1.9);
            }
            ctx.asteroids.add(asteroid);
        }
    }

    private static void spawnCampaignSalvagePocket(GameContext ctx, double cx, double cy, int count) {
        if (ctx == null || count <= 0) return;
        for (int i = 0; i < count; i++) {
            double ox = (ctx.rng.nextDouble() - 0.5) * 120.0;
            double oy = (ctx.rng.nextDouble() - 0.5) * 120.0;
            ctx.salvage.add(new Salvage(
                    GameMath.clamp(cx + ox, 40.0, ctx.WORLD_W - 40.0),
                    GameMath.clamp(cy + oy, 40.0, ctx.WORLD_H - 40.0),
                    40 + ctx.rng.nextInt(90),
                    18 + ctx.rng.nextInt(70),
                    240.0
            ));
        }
    }

    private static void spawnCampaignPatrolBand(GameContext ctx, CampaignState st, double x, double y, int intensity) {
        ShipRole[] roles = campaignPatrolRoles(st, intensity);
        double[][] offsets = {
                {0.0, 0.0},
                {-120.0, -90.0},
                {110.0, 80.0},
                {220.0, -30.0},
                {-220.0, 50.0}
        };
        for (int i = 0; i < roles.length && i < offsets.length; i++) {
            spawnEnemyAtPoint(ctx, roles[i], x + offsets[i][0], y + offsets[i][1]);
        }
    }

    private static ShipRole[] campaignPatrolRoles(CampaignState st, int intensity) {
        boolean late = st != null && st.sector >= 12;
        boolean endgame = st != null && st.sector >= 20;
        return switch (intensity) {
            case 0 -> late
                    ? new ShipRole[]{ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE, ShipRole.MISSILE_BOAT}
                    : new ShipRole[]{ShipRole.PATROL, ShipRole.FRIGATE, ShipRole.PICKET};
            case 1 -> endgame
                    ? new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                    : new ShipRole[]{ShipRole.FRIGATE, ShipRole.LIGHT_CRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
            default -> endgame
                    ? new ShipRole[]{ShipRole.BULWARK_TITAN, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT}
                    : new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE};
        };
    }

    private static void spawnCampaignReserveNode(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.ENEMY, x + 120.0, y - 110.0, "Reserve Turret Alpha");
        spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.ENEMY, x - 110.0, y + 90.0, "Reserve Turret Beta");
        spawnCampaignPatrolBand(ctx, st, x, y, 2);
    }

    private static void spawnCampaignSupportPocket(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        Faction supportFaction = (st.sector >= 12) ? Faction.TEAM_C : Faction.ALLY;
        spawnCampaignShip(ctx, ShipRole.HAULER, supportFaction, x, y, "Blue Route Tender");
        spawnCampaignShip(ctx, ShipRole.MINER, supportFaction, x - 130.0, y + 70.0, "Forward Prospector");
        if (st.sector >= 8) {
            spawnCampaignShip(ctx, ShipRole.FRIGATE, supportFaction, x + 140.0, y - 90.0, "Relay Guard");
        }
        if (st.sector >= 16) {
            spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, supportFaction, x + 40.0, y + 140.0, "Relay Flak");
        }
    }

    private static void spawnSector1(GameContext ctx, CampaignState st) {
        double px = ctx.player.x;
        double py = ctx.player.y;
        spawnCampaignBase(ctx, Faction.TEAM_C, px - 220.0, py - 40.0, "Green Exchange Spire");
        spawnCampaignBase(ctx, Faction.TEAM_C, px + 260.0, py - 260.0, "Green Market Bastion");
        spawnCampaignBase(ctx, Faction.TEAM_C, px + 280.0, py + 250.0, "Green Customs Pier");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT_TITAN, Faction.TEAM_C, -120, -160, "Green Ledger Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.MOBILE_STATION_TITAN, Faction.TEAM_C, 120, 150, "Green Harbor Forge");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.MINER, Faction.TEAM_C, -340, 120, "Trade Miner One");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.MINER, Faction.TEAM_C, -290, -170, "Trade Miner Two");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, 40, 210, "Cargo Lighter");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, 320, 20, "Merchant Spine");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_C, 180, -120, "Green Screen One");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, 340, -80, "Green Guard One");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, 360, 110, "Green Guard Two");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_C, -40, 260, "Green Harbor Screen");
        st.introSequenceActive = true;
        st.introPhase = 0;
        st.introTimer = 0.0;
        st.cinematicFocusX = px + 40.0;
        st.cinematicFocusY = py - 40.0;
        st.introWarpX = GameMath.clamp(px + 900.0, 220.0, ctx.WORLD_W - 220.0);
        st.introWarpY = GameMath.clamp(py - 90.0, 220.0, ctx.WORLD_H - 220.0);
    }

    private static void spawnSector2(GameContext ctx, CampaignState st) {
        Ship convoyA = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -360, -120, "Cinder Refugee Tender");
        Ship convoyB = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -420, 40, "Ledger Lighter");
        Ship convoyC = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -500, 160, "Customs Survivor One");
        if (convoyA != null) convoyA.desiredSpeed = Math.min(convoyA.desiredSpeed, 34.0);
        if (convoyB != null) convoyB.desiredSpeed = Math.min(convoyB.desiredSpeed, 32.0);
        if (convoyC != null) convoyC.desiredSpeed = Math.min(convoyC.desiredSpeed, 34.0);
        st.objectiveAssetLabel = "CONVOYS";
        registerObjectiveAsset(st, convoyA);
        registerObjectiveAsset(st, convoyB);
        registerObjectiveAsset(st, convoyC);
        registerObjectiveAssetQuota(st, 2, "DEFEAT: CIVILIAN APERTURE SEALED");

        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 760, -180);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.CIWS_CORVETTE, 850, -70);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 930, 40);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 1010, 150);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 840, 180);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 920, -220);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -300, 20, "Green Broker Shield");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 80, "Green Aperture Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -40, "Green Aperture Flak");
    }

    private static void spawnSector3(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 160, 220, ctx.WORLD_H - 220);
        st.captureRadius = 210.0;
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.VANGUARD_TITAN, 760, -80);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 860, -160);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.CIWS_CORVETTE, 900, -40);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 980, 60);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 1020, 140);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 900, 200);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -360, 60, "Green Bulwark Titan Broker Shield");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Green Escort Spear");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -200, -50, "Green Screen Lance");
    }

    private static void spawnSector4(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 700, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 220, 220, ctx.WORLD_H - 220);
        st.captureRadius = 200.0;
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.INTERDICTION_TITAN, st.captureX + 220, st.captureY - 20);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.FRIGATE, st.captureX + 90, st.captureY - 90);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.CIWS_CORVETTE, st.captureX - 120, st.captureY + 60);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.MISSILE_BOAT, st.captureX + 220, st.captureY - 140);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT_TITAN, Faction.TEAM_C, -340, 70, "Green Navigation Titan Atlas Memory");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -100, 60, "Green Relay Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_C, -170, -40, "Green Relay Screen");
    }

    private static void spawnSector5(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 160, 220, ctx.WORLD_H - 220);
        st.captureRadius = 210.0;
        spawnEnemyAtPoint(ctx, ShipRole.PATROL, st.captureX + 220, st.captureY - 120);
        spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 260, st.captureY + 40);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 280, st.captureY - 10);
        spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX + 250, st.captureY - 150);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 280, st.captureY + 10);
        spawnEnemyAtPoint(ctx, ShipRole.PATROL, st.captureX + 210, st.captureY + 150);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT_TITAN, Faction.TEAM_C, -340, 70, "Green Navigation Titan Atlas Memory");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Uplink Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Uplink Flak");
    }

    private static void spawnSector6(GameContext ctx, CampaignState st) {
        Ship archive = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -360, -80, "State Archive Barge");
        Ship fuelTender = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -420, 70, "Fuel Casket Tender");
        Ship straggler = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -500, 170, "Wake Straggler");
        if (archive != null) archive.desiredSpeed = Math.min(archive.desiredSpeed, 28.0);
        if (fuelTender != null) fuelTender.desiredSpeed = Math.min(fuelTender.desiredSpeed, 26.0);
        if (straggler != null) straggler.desiredSpeed = Math.min(straggler.desiredSpeed, 30.0);
        st.objectiveAssetLabel = "CACHES";
        registerObjectiveAsset(st, archive);
        registerObjectiveAsset(st, fuelTender);
        registerObjectiveAsset(st, straggler);
        registerObjectiveAssetQuota(st, 2, "DEFEAT: RECOVERY LINE SHATTERED");

        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 620, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 760, -200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 860, 40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 940, 140);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Wake Recovery Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -40, "Wake Recovery Flak");
    }

    private static void spawnSector7(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -320, 40, "Green Bulwark Titan Vigilant Home");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Pursuit Screen");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Pursuit Flak");
        st.bossTargetId = spawnBoss(ctx, ShipRole.INTERDICTION_TITAN, "AI PURSUIT TITAN RED KNIFE", 1.55, 1.65);
    }

    private static void spawnSector8(GameContext ctx, CampaignState st) {
        st.escortShip = spawnEscortTitan(ctx, ShipRole.TRANSPORT_TITAN, "Green Exodus Transport Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CARRIER_SUPPORT_TITAN, Faction.TEAM_C, -340, -120, "Green Carrier Support Titan Hearthwing");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 560, -120);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 760, -200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 860, 40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.VANGUARD_TITAN, 980, 120);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Refugee Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Refugee Flak");
    }

    private static void spawnSector9(GameContext ctx, CampaignState st) {
        Ship brokerA = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -360, -120, "Broker Defector One");
        Ship brokerB = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -440, 30, "Slipway Tender");
        Ship brokerC = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -520, 160, "Broker Defector Two");
        Ship brokerD = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.MINER, Faction.TEAM_C, -420, 220, "Yard Miner Turncoat");
        if (brokerA != null) brokerA.desiredSpeed = Math.min(brokerA.desiredSpeed, 32.0);
        if (brokerB != null) brokerB.desiredSpeed = Math.min(brokerB.desiredSpeed, 30.0);
        if (brokerC != null) brokerC.desiredSpeed = Math.min(brokerC.desiredSpeed, 32.0);
        if (brokerD != null) brokerD.desiredSpeed = Math.min(brokerD.desiredSpeed, 34.0);
        st.objectiveAssetLabel = "DEFECTORS";
        registerObjectiveAsset(st, brokerA);
        registerObjectiveAsset(st, brokerB);
        registerObjectiveAsset(st, brokerC);
        registerObjectiveAsset(st, brokerD);
        registerObjectiveAssetQuota(st, 3, "DEFEAT: DEFECTION CORRIDOR COLLAPSED");

        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 580, -180);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 700, 170);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 860, -40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 90);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 1040, -20);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Broker Lane Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -210, -60, "Broker Lane Flak");
    }

    private static void spawnSector10(GameContext ctx, CampaignState st) {
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, 760, -60);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 560, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 720, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 760, -30);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 900, -110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 980, 90);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.VANGUARD_TITAN, Faction.TEAM_C, -340, 40, "Green Vanguard Titan Waybreaker");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Lane Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Lane Flak");
    }

    private static void spawnSector11(GameContext ctx, CampaignState st) {
        Ship depotA = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -360, -120, "Bonded Fuel Train");
        Ship depotB = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -430, 20, "Ledger Vault Tender");
        Ship depotC = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -500, 170, "Refit Shelf Hauler");
        if (depotA != null) depotA.desiredSpeed = Math.min(depotA.desiredSpeed, 28.0);
        if (depotB != null) depotB.desiredSpeed = Math.min(depotB.desiredSpeed, 28.0);
        if (depotC != null) depotC.desiredSpeed = Math.min(depotC.desiredSpeed, 30.0);
        st.objectiveAssetLabel = "DEPOTS";
        registerObjectiveAsset(st, depotA);
        registerObjectiveAsset(st, depotB);
        registerObjectiveAsset(st, depotC);
        registerObjectiveAssetQuota(st, 2, "DEFEAT: DEPOT SHELF LOST");

        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 620, -180);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 760, -40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 880, 110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 980, -100);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Depot Recovery Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -40, "Depot Recovery Flak");
    }

    private static void spawnSector12(GameContext ctx, CampaignState st) {
        Ship courierA = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -360, -120, "Green Pact Courier One");
        Ship courierB = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.TRANSPORT, Faction.TEAM_C, -430, 10, "Green Pact Courier Two");
        Ship courierC = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -500, 150, "Green Signatory Vault");
        Ship courierD = spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -310, 210, "Green Signatory Escort");
        if (courierA != null) courierA.desiredSpeed = Math.min(courierA.desiredSpeed, 34.0);
        if (courierB != null) courierB.desiredSpeed = Math.min(courierB.desiredSpeed, 34.0);
        if (courierC != null) courierC.desiredSpeed = Math.min(courierC.desiredSpeed, 30.0);
        st.objectiveAssetLabel = "SIGNATORIES";
        registerObjectiveAsset(st, courierA);
        registerObjectiveAsset(st, courierB);
        registerObjectiveAsset(st, courierC);
        registerObjectiveAsset(st, courierD);
        registerObjectiveAssetQuota(st, 3, "DEFEAT: SIGNATORY RUN SHATTERED");

        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 640, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 740, 110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 880, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 840, -220);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -280, 20, "Green Contract Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -60, "Green Signatory Flak");
    }

    private static void spawnSector13(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 140, 220, ctx.WORLD_H - 220);
        st.captureRadius = 210.0;
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 240, st.captureY - 150);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 300, st.captureY + 20);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 180, st.captureY + 170);
        spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 180, st.captureY - 130);
        spawnEnemyAtPoint(ctx, ShipRole.INTERDICTION_TITAN, st.captureX + 260, st.captureY + 20);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 230, st.captureY + 80);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX - 200, st.captureY - 10);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -360, 80, "Green Contract Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 80, "Green Contract Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -60, "Green Contract Screen");
        Ship uplinkAlpha = spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.TEAM_C,
                st.captureX - 110, st.captureY + 95, "Green Contract Uplink Alpha");
        Ship uplinkBeta = spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.TEAM_C,
                st.captureX - 160, st.captureY - 105, "Green Contract Uplink Beta");
        st.objectiveAssetLabel = "UPLINKS";
        registerObjectiveAsset(st, uplinkAlpha);
        registerObjectiveAsset(st, uplinkBeta);
    }

    private static void spawnSector14(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 140, 220, ctx.WORLD_H - 220);
        st.captureRadius = 210.0;
        spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX + 350, st.captureY - 120);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 390, st.captureY + 40);
        spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 310, st.captureY + 170);
        spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 360, st.captureY - 40);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX + 260, st.captureY + 190);
        spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 420, st.captureY + 140);
        spawnEnemyAtPoint(ctx, ShipRole.INTERDICTION_TITAN, st.captureX + 410, st.captureY + 30);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 460, st.captureY - 130);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -340, 90, "Green Contract Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Nysa Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Nysa Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -250, 90, "Green Nysa Cruiser");
    }

    private static void spawnSector15(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 820, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 100, 220, ctx.WORLD_H - 220);
        st.captureRadius = 210.0;
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 240, st.captureY - 150);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 320, st.captureY - 20);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 260, st.captureY + 140);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 120, st.captureY + 210);
        spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 180, st.captureY - 130);
        spawnEnemyAtPoint(ctx, ShipRole.INTERDICTION_TITAN, st.captureX + 260, st.captureY + 20);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 230, st.captureY + 80);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -340, 90, "Green Siege Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Siege Scout");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Siege Flak");
    }

    private static void spawnSector16(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -340, 90, "Green Contract Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Siege Scout");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Siege Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -250, 90, "Green Siege Cruiser");
        st.bossTargetId = spawnBoss(ctx, ShipRole.ARTILLERY_TITAN, "ASH GATE ARTILLERY TITAN", 1.60, 1.75);
    }

    private static void spawnSector17(GameContext ctx, CampaignState st) {
        Ship assemblyA = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.TRANSPORT, -360, -120, "Coalition Tug One");
        Ship assemblyB = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.HAULER, -440, 20, "Hospital Stores Hauler");
        Ship assemblyC = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.TRANSPORT, -520, 170, "Refugee Clinic Tender");
        if (assemblyA != null) assemblyA.desiredSpeed = Math.min(assemblyA.desiredSpeed, 32.0);
        if (assemblyB != null) assemblyB.desiredSpeed = Math.min(assemblyB.desiredSpeed, 28.0);
        if (assemblyC != null) assemblyC.desiredSpeed = Math.min(assemblyC.desiredSpeed, 30.0);
        st.objectiveAssetLabel = "ASSEMBLY";
        registerObjectiveAsset(st, assemblyA);
        registerObjectiveAsset(st, assemblyB);
        registerObjectiveAsset(st, assemblyC);
        registerObjectiveAssetQuota(st, 2, "DEFEAT: COALITION CORRIDOR EXPOSED");

        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 780, -180);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 860, -60);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 930, 40);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.CIWS_CORVETTE, 1010, 140);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 920, 220);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 1080, -40);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Coalition Screen Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -40, "Coalition Screen Flak");
    }

    private static void spawnSector18(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.SHIELD_BASTION_TITAN, Faction.TEAM_C, -360, -80, "Green Shield Bastion Titan Solward");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -440, 120, "Green Bulwark Titan Aegis Return");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 580, -180);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 700, 170);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.INTERDICTION_TITAN, 860, -40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 90);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 1050, -20);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Green Sol Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -210, -60, "Green Sol Flak");
    }

    private static void spawnSector19(GameContext ctx, CampaignState st) {
        Ship recoveryTitan = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.BOARDING_RECOVERY_TITAN, -300, 30, "Liberated Yellow Recovery Titan");
        Ship cutter = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -420, 120, "Yellow Breakchain Cutter");
        if (recoveryTitan != null) recoveryTitan.desiredSpeed = Math.min(recoveryTitan.desiredSpeed, 24.0);
        if (cutter != null) cutter.desiredSpeed = Math.min(cutter.desiredSpeed, 28.0);
        st.objectiveAssetLabel = "LIBERATED HULLS";
        registerObjectiveAsset(st, recoveryTitan);
        registerObjectiveAsset(st, cutter);
        registerObjectiveAssetQuota(st, 1, "DEFEAT: LIBERATION TARGET LOST");

        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.STATIC_TURRET, 760, -200);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.STATIC_TURRET, 930, 20);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.INTERDICTION_TITAN, 820, 220);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.VANGUARD_TITAN, 980, -80);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 860, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 1020, 70);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -320, 20, "Green Liberation Cruiser");
    }

    private static void spawnSector20(GameContext ctx, CampaignState st) {
        st.escortShip = spawnEscortTitan(ctx, ShipRole.BOARDING_RECOVERY_TITAN, "Yellow Recovery Titan Renewal");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CARRIER_SUPPORT_TITAN, Faction.TEAM_C, -360, -110, "Green Carrier Support Titan");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 640, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 740, 110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.VANGUARD_TITAN, 990, -20);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 880, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 840, -220);
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 70, "Yellow Rejoin Guard");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -210, -60, "Yellow Rejoin Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -300, 60, "Green Liberation Cruiser");
    }

    private static void spawnSector21(GameContext ctx, CampaignState st) {
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.STATIC_TURRET, 760, -200);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.STATIC_TURRET, 930, 20);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.STATIC_TURRET, 820, 220);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.INTERDICTION_TITAN, 760, -120);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, 920, 80);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 790, 150);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 860, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 1020, 70);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.ARTILLERY_TITAN, Faction.TEAM_C, -380, -40, "Green Artillery Titan Homebound");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 80, "Yellow Return Guard");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -220, -70, "Yellow Return Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -320, 20, "Green Earthway Cruiser");
        Ship evacTender = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.TRANSPORT, -320, 170, "Yellow Evac Tender");
        Ship hospitalHauler = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.HAULER, -390, -130, "Yellow Hospital Hauler");
        if (evacTender != null) evacTender.desiredSpeed = Math.min(evacTender.desiredSpeed, 38.0);
        if (hospitalHauler != null) hospitalHauler.desiredSpeed = Math.min(hospitalHauler.desiredSpeed, 34.0);
        st.objectiveAssetLabel = "EVAC SHIPS";
        registerObjectiveAsset(st, evacTender);
        registerObjectiveAsset(st, hospitalHauler);
    }

    private static void spawnSector22(GameContext ctx, CampaignState st) {
        spawnEnemyAtPlayerOffset(ctx, ShipRole.INTERDICTION_TITAN, 760, -120);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, 920, 80);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 790, 150);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 860, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 1020, 70);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BATTLECRUISER, 1080, -40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 1140, 180);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.ARTILLERY_TITAN, Faction.TEAM_C, -380, -40, "Green Artillery Titan Homebound");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 80, "Yellow Return Guard");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -220, -70, "Yellow Return Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -320, 20, "Green Earthway Cruiser");
    }

    private static void spawnSector23(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 140, 220, ctx.WORLD_H - 220);
        st.captureRadius = 210.0;
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 240, st.captureY - 150);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 300, st.captureY + 20);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 180, st.captureY + 170);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.STATIC_TURRET, st.captureX + 80, st.captureY - 10);
        spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 180, st.captureY - 130);
        spawnEnemyAtPoint(ctx, ShipRole.INTERDICTION_TITAN, st.captureX + 260, st.captureY + 20);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 230, st.captureY + 80);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX - 200, st.captureY - 10);
        Ship liftA = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.TRANSPORT, -340, 120, "Earthrise Lift One");
        Ship liftB = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.TRANSPORT, -420, 10, "Earthrise Lift Two");
        Ship liftC = spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.HAULER, -500, 170, "Resistance Vault Ship");
        if (liftA != null) liftA.desiredSpeed = Math.min(liftA.desiredSpeed, 34.0);
        if (liftB != null) liftB.desiredSpeed = Math.min(liftB.desiredSpeed, 34.0);
        if (liftC != null) liftC.desiredSpeed = Math.min(liftC.desiredSpeed, 30.0);
        st.objectiveAssetLabel = "LIFTS";
        registerObjectiveAsset(st, liftA);
        registerObjectiveAsset(st, liftB);
        registerObjectiveAsset(st, liftC);
        registerObjectiveAssetQuota(st, 2, "DEFEAT: INSURRECTION LAUNCHES LOST");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -280, -30, "Green Homefront Bulwark");
    }

    private static void spawnSector24(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -420, -120, "Green Bulwark Titan Aegis Return");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.ARTILLERY_TITAN, Faction.TEAM_C, -500, 40, "Green Artillery Titan Homebound");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.CARRIER_SUPPORT_TITAN, -540, 180, "Yellow Carrier Support Titan Renewal");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 80, "Yellow Earthfall Guard");
        spawnCampaignAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -220, -70, "Yellow Earthfall Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -320, 20, "Green Homefront Cruiser");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BATTLECRUISER, Faction.TEAM_C, -420, -40, "Green Breakthrough Battlecruiser");
        st.bossTargetId = spawnFinalBoss(ctx);
    }

    private static void updateAuthoredSectorScript(GameContext ctx, CampaignState st) {
        if (st == null) return;
        if (st.sector <= AUTHORED_VERTICAL_SLICE_LAST_SECTOR) {
            switch (st.sector) {
                case 1 -> updateSector1Script(ctx, st);
                case 2 -> updateSector2Script(ctx, st);
                default -> {
                    // No-op.
                }
            }
            return;
        }
        updateLateCampaignPressure(ctx, st);
    }

    private static void updateSector1Script(GameContext ctx, CampaignState st) {
        double t = st.sectorElapsed;
        if (st.authoredWaveCursor == 0 && t >= 52.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 820, -220);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 900, 90);
            EventSystem.showBanner(ctx, "RAIDER PROBES INBOUND", 1.5);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=1 wave=1 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 1 && t >= 98.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 860, -170);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 980, 20);
            EventSystem.showBanner(ctx, "SECOND RAIDER PUSH", 1.5);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=1 wave=2 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 2 && t >= 142.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 960, -60);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 1020, 150);
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_C, -240, 90, "Green Relief Screen");
            EventSystem.showBanner(ctx, "GREEN RELIEF SCREEN ARRIVES", 1.8);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=1 wave=3 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 3 && t >= 174.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 920, -230);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 980, 190);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 1080, -40);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "FINAL ATTACK WAVE", 2.0);
            logTelemetry("sector_script", "sector=1 wave=4 t=" + Math.round(t));
        }
    }

    private static void updateSector2Script(GameContext ctx, CampaignState st) {
        double t = st.sectorElapsed;
        int remainingTargets = Math.max(0, (int) Math.ceil(st.objectiveGoal - st.objectiveProgress));
        int convoyAlive = liveObjectiveAssets(st);
        st.objectivePhaseLabel = "PHASE: Destroy the customs-halo strike ships across the marked pockets ("
                + remainingTargets + " remaining)";
        st.threatStateLabel = "THREAT: The civilian aperture seals if fewer than "
                + Math.max(1, st.objectiveAssetRequiredSurvivors)
                + " convoy hulls survive (" + convoyAlive + "/" + Math.max(0, st.objectiveAssetTotal) + " holding)";
        if (st.authoredWaveCursor == 0 && t >= 55.0) {
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 860, -160);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 930, -80);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 980, 30);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "STRIKE GROUP REINFORCEMENT", 1.8);
            logTelemetry("sector_script", "sector=2 wave=1 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 1 && t >= 150.0) {
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 900, 140);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 980, 180);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 1040, 70);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 930, 230);
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -240, -60, "Green Relief Flak");
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "STRIKE GROUP MAIN BODY", 1.8);
            logTelemetry("sector_script", "sector=2 wave=2 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 2 && t >= 250.0) {
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 980, -220);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 1060, -80);
            spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 1140, 30);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "LAST STRIKE ELEMENT", 1.8);
            logTelemetry("sector_script", "sector=2 wave=3 t=" + Math.round(t));
        }
    }

    private static void updateSector3Script(GameContext ctx, CampaignState st) {
        if (st.objectiveStage == 0) {
            int remaining = Math.max(0, st.authoredObjectiveHostiles.size());
            st.objectivePhaseLabel = "PHASE: Break the relay interdiction screen (" + remaining + " blockers remaining)";
            st.threatStateLabel = "THREAT: Route-control guns and raiders are pinning the authority relay";
            if (remaining == 0) {
                st.captureArmed = true;
                st.objectiveStage = 1;
                st.objectiveKillBaseline = st.kills;
                st.objectiveLabel = "Destroy the relay relief wing";
                st.objectiveGoal = 6.0;
                st.objectiveProgress = 0.0;
                st.authoredWaveCursor = 1;
                st.objectivePhaseLabel = "PHASE: Break the relief wing while green navigation hands off jump authority";
                st.threatStateLabel = "THREAT: Red reserve ships are racing the relay to shut the route again";
                spawnEnemyAtPoint(ctx, ShipRole.PATROL, st.captureX + 220, st.captureY - 120);
                spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 260, st.captureY + 40);
                spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 280, st.captureY - 10);
                EventSystem.showBanner(ctx, "RELAY SECURED: BREAK THE RELIEF WING", 2.2);
                logTelemetry("sector_script", "sector=3 stage=relief_break t=" + Math.round(st.sectorElapsed));
            }
            return;
        }

        int contactsToBreak = (int) Math.ceil(Math.max(0.0, st.objectiveGoal - st.objectiveProgress));
        st.objectivePhaseLabel = "PHASE: Break the relief wing and cover the route-control handoff (" + contactsToBreak + " contacts to shatter)";
        st.threatStateLabel = "THREAT: Relay reserves are converging to shut the Earth vector";
        if (st.authoredWaveCursor == 1 && st.objectiveProgress >= 2.0) {
            spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX + 250, st.captureY - 150);
            spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 280, st.captureY + 10);
            spawnEnemyAtPoint(ctx, ShipRole.PATROL, st.captureX + 210, st.captureY + 150);
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -240, 120, "Green Uplink Guard");
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "RELAY SCREEN COLLAPSING", 1.8);
            logTelemetry("sector_script", "sector=3 wave=2 p=" + Math.round(st.objectiveProgress));
            return;
        }
        if (st.authoredWaveCursor == 2 && st.objectiveProgress >= 4.0) {
            spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX - 280, st.captureY - 40);
            spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX + 320, st.captureY + 70);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "EARTH VECTOR OPENING", 2.0);
            logTelemetry("sector_script", "sector=3 wave=3 p=" + Math.round(st.objectiveProgress));
        }
    }

    private static void updateSector7Script(GameContext ctx, CampaignState st) {
        if (st.objectiveStage == 0) {
            int remaining = Math.max(0, st.authoredObjectiveHostiles.size());
            st.objectivePhaseLabel = "PHASE: Sweep the array perimeter and cut the jammer triad (" + remaining + " remaining)";
            st.threatStateLabel = "THREAT: Interdiction escorts are masking the uplink approach";
            if (st.authoredWaveCursor == 0 && st.sectorElapsed >= 32.0) {
                spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX + 330, st.captureY - 210);
                spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 380, st.captureY + 60);
                st.authoredWaveCursor++;
                EventSystem.showBanner(ctx, "JAMMER RELIEF WING INBOUND", 1.8);
                logTelemetry("sector_script", "sector=7 wave=1 t=" + Math.round(st.sectorElapsed));
                return;
            }
            if (st.authoredWaveCursor == 1 && st.sectorElapsed >= 92.0) {
                spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 360, st.captureY - 40);
                spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX + 260, st.captureY + 190);
                spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 420, st.captureY + 140);
                st.authoredWaveCursor++;
                EventSystem.showBanner(ctx, "SECOND ARRAY SCREEN DEPLOYING", 1.8);
                logTelemetry("sector_script", "sector=7 wave=2 t=" + Math.round(st.sectorElapsed));
                return;
            }
            if (remaining == 0) {
                st.captureArmed = true;
                st.objectiveStage = 1;
                st.objectiveKillBaseline = st.kills;
                st.objectiveLabel = "Destroy the red relief wing and bring the green contract net online";
                st.objectiveGoal = 8.0;
                st.objectiveProgress = 0.0;
                st.authoredWaveCursor = 1;
                st.objectivePhaseLabel = "PHASE: Break the relief wing while green command handshakes the coalition net";
                st.threatStateLabel = "THREAT: Red reserves are trying to re-jam the array before the houses commit";
                spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX + 350, st.captureY - 120);
                spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 390, st.captureY + 40);
                spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 310, st.captureY + 170);
                EventSystem.showBanner(ctx, "JAMMERS DOWN: BREAK THE RELIEF WING", 2.2);
                logTelemetry("sector_script", "sector=7 stage=relief_break t=" + Math.round(st.sectorElapsed));
            }
            return;
        }
        int contactsToBreak = (int) Math.ceil(Math.max(0.0, st.objectiveGoal - st.objectiveProgress));
        st.objectivePhaseLabel = "PHASE: Break the relief wing and secure the coalition handshake (" + contactsToBreak + " contacts to shatter)";
        st.threatStateLabel = "THREAT: Red reserve groups are counterattacking to keep the green houses isolated";
        if (st.authoredWaveCursor == 1 && st.objectiveProgress >= 3.0) {
            spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 360, st.captureY - 40);
            spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX + 260, st.captureY + 190);
            spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 420, st.captureY + 140);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "SECOND ARRAY SCREEN DEPLOYING", 1.8);
            logTelemetry("sector_script", "sector=7 wave=3 p=" + Math.round(st.objectiveProgress));
            return;
        }
        if (st.authoredWaveCursor == 2 && st.objectiveProgress >= 6.0) {
            spawnEnemyAtPoint(ctx, ShipRole.INTERDICTION_TITAN, st.captureX + 410, st.captureY + 30);
            spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 460, st.captureY - 130);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "FINAL CONTRACT COUNTERATTACK", 2.0);
            logTelemetry("sector_script", "sector=7 wave=4 p=" + Math.round(st.objectiveProgress));
        }
    }

    private static void updateSector11Script(GameContext ctx, CampaignState st) {
        if (st.objectiveStage == 0) {
            int remaining = Math.max(0, st.authoredObjectiveHostiles.size());
            st.objectivePhaseLabel = "PHASE: Silence the orbital anchor batteries (" + remaining + " remaining)";
            st.threatStateLabel = "THREAT: Red cordon groups are screening Luna's defense lattice";
            if (st.authoredWaveCursor == 0 && st.sectorElapsed >= 38.0) {
                spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 860, -210);
                spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 980, 120);
                st.authoredWaveCursor++;
                EventSystem.showBanner(ctx, "ANCHOR PICKETS MOVING TO INTERCEPT", 1.8);
                logTelemetry("sector_script", "sector=11 wave=1 t=" + Math.round(st.sectorElapsed));
                return;
            }
            if (st.authoredWaveCursor == 1 && st.sectorElapsed >= 96.0) {
                spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 1020, -30);
                spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 1080, 160);
                spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 960, 240);
                st.authoredWaveCursor++;
                EventSystem.showBanner(ctx, "LUNA CORDON RESERVES SCRAMBLING", 1.8);
                logTelemetry("sector_script", "sector=11 wave=2 t=" + Math.round(st.sectorElapsed));
                return;
            }
            if (remaining == 0) {
                st.objectiveStage = 1;
                st.objectiveKillBaseline = st.kills;
                st.objectiveLabel = "Break the Luna orbital cordon and clear the Earth lane";
                st.objectiveGoal = 10.0;
                st.objectiveProgress = 0.0;
                st.authoredWaveCursor = 0;
                st.objectivePhaseLabel = "PHASE: Push through the shattered perimeter and cover the evacuation line";
                st.threatStateLabel = "THREAT: Red reserve capitals are committing to protect the Earth approach";
                EventSystem.showBanner(ctx, "ANCHORS SILENCED: BREAK THE CORDON", 2.2);
                logTelemetry("sector_script", "sector=11 stage=cordon_break t=" + Math.round(st.sectorElapsed));
            }
            return;
        }
        updateLateDestroyPressure(ctx, st);
    }

    private static void updateLateCampaignPressure(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        switch (st.objectiveType) {
            case SURVIVE -> updateLateSurvivePressure(ctx, st);
            case DESTROY -> updateLateDestroyPressure(ctx, st);
            case ESCORT -> updateLateEscortPressure(ctx, st);
            case CAPTURE -> updateLateCapturePressure(ctx, st);
            case BOSS, FINAL_BOSS -> updateLateBossPressure(ctx, st);
        }
    }

    private static void updateEscortFormationBehavior(GameContext ctx, CampaignState st, double dt) {
        if (ctx == null || st == null || dt <= 0.0 || ctx.player == null) return;
        if (st.objectiveType != ObjectiveType.ESCORT) return;
        Ship escort = st.escortShip;
        if (escort == null || !escort.alive || escort.dying || escort.hp <= 0) return;

        double slotBack = ctx.player.radius + escort.radius + ESCORT_TIGHT_SLOT_MARGIN;
        double targetX = ctx.player.x - Math.cos(ctx.player.angle) * slotBack;
        double targetY = ctx.player.y - Math.sin(ctx.player.angle) * slotBack;
        double dx = targetX - escort.x;
        double dy = targetY - escort.y;
        double dist = Math.hypot(dx, dy);
        double playerVxPerSec = ctx.player.vx / dt;
        double playerVyPerSec = ctx.player.vy / dt;
        if (dist <= ESCORT_TIGHT_HOLD_RADIUS) {
            boolean thrusting = Math.hypot(playerVxPerSec, playerVyPerSec) > 1e-4;
            MovementModel.applyDesiredVelocity(escort, playerVxPerSec, playerVyPerSec, dt, thrusting);
            rotateShipToward(escort, ctx.player.angle, dt);
            return;
        }

        double correctionMul = (dist > ESCORT_TIGHT_CATCHUP_RADIUS) ? 2.8 : 1.7;
        double correctionSpeed = Math.min(
                Math.max(60.0, MovementModel.speedCeiling(escort) * ESCORT_TIGHT_CATCHUP_SPEED_MUL),
                dist * correctionMul);
        double ux = dx / Math.max(1e-6, dist);
        double uy = dy / Math.max(1e-6, dist);
        MovementModel.applyDesiredVelocity(
                escort,
                playerVxPerSec + ux * correctionSpeed,
                playerVyPerSec + uy * correctionSpeed,
                dt,
                true);
        rotateShipToward(escort, Math.atan2(ctx.player.y - escort.y, ctx.player.x - escort.x), dt);
    }

    private static void rotateShipToward(Ship ship, double desiredAngle, double dt) {
        if (ship == null || dt <= 0.0) return;
        double delta = MathUtil.normalizeAngle(desiredAngle - ship.angle);
        double maxDelta = MovementModel.turnRateRadPerSec(ship) * dt;
        delta = MathUtil.clamp(delta, -maxDelta, maxDelta);
        ship.angle = MathUtil.normalizeAngle(ship.angle + delta);
    }

    private static void updateLateSurvivePressure(GameContext ctx, CampaignState st) {
        if (st.authoredWaveCursor == 0 && st.sectorElapsed >= 50.0) {
            launchPressureStage(ctx, st, "ENEMY PROBE WING INBOUND",
                    "PHASE: Screen the arrival lane against forward probes",
                    "THREAT: Red scouts are testing the corridor edges",
                    pressureRolesFor(st, 0));
            return;
        }
        if (st.authoredWaveCursor == 1 && st.sectorElapsed >= 135.0) {
            launchPressureStage(ctx, st, "HEAVY BREAKTHROUGH FORMING",
                    "PHASE: Hold the line while heavier ships push the lane",
                    "THREAT: Capital-weight contacts are entering the battlespace",
                    pressureRolesFor(st, 1));
            return;
        }
        if (st.authoredWaveCursor == 2 && st.sectorElapsed >= 225.0) {
            launchPressureStage(ctx, st, "KILL BOX CLOSING",
                    "PHASE: Survive the final convergence and keep the corridor intact",
                    "THREAT: Reserve strike elements are collapsing on the mustering lane",
                    pressureRolesFor(st, 2));
        }
    }

    private static void updateLateDestroyPressure(GameContext ctx, CampaignState st) {
        double ratio = progressRatio(st);
        if (st.authoredWaveCursor == 0 && (st.sectorElapsed >= 45.0 || ratio >= 0.20)) {
            launchPressureStage(ctx, st, "CORDON RESERVES SCRAMBLING",
                    "PHASE: Keep pressing before the reserve line stabilizes",
                    "THREAT: Red reserve escorts are moving to seal the breach",
                    pressureRolesFor(st, 0));
            return;
        }
        if (st.authoredWaveCursor == 1 && (st.sectorElapsed >= 120.0 || ratio >= 0.50)) {
            launchPressureStage(ctx, st, "COUNTER-ATTACK WING COMMITTED",
                    "PHASE: Break the second cordon before it can mass guns",
                    "THREAT: Counter-attack capitals are joining the screen",
                    pressureRolesFor(st, 1));
            return;
        }
        if (st.authoredWaveCursor == 2 && (st.sectorElapsed >= 215.0 || ratio >= 0.78)) {
            launchPressureStage(ctx, st, "LAST RED STAND",
                    "PHASE: Shatter the final reserve and open the lane completely",
                    "THREAT: The enemy is throwing its last standing reserve into the breach",
                    pressureRolesFor(st, 2));
        }
    }

    private static void updateLateEscortPressure(GameContext ctx, CampaignState st) {
        double ratio = progressRatio(st);
        if (st.authoredWaveCursor == 0 && (st.sectorElapsed >= 35.0 || ratio >= 0.16)) {
            launchPressureStage(ctx, st, "PURSUIT SCREEN DETECTED",
                    "PHASE: Keep the flagship screened while pursuit ships bracket the lane",
                    "THREAT: Fast red hunters are probing the escort perimeter",
                    pressureRolesFor(st, 0));
            return;
        }
        if (st.authoredWaveCursor == 1 && (st.sectorElapsed >= 110.0 || ratio >= 0.45)) {
            launchPressureStage(ctx, st, "MISSILE AMBUSH ON THE FLANK",
                    "PHASE: Absorb the missile wave without losing formation integrity",
                    "THREAT: Ambush elements are trying to strip the escort screen",
                    pressureRolesFor(st, 1));
            return;
        }
        if (st.authoredWaveCursor == 2 && (st.sectorElapsed >= 195.0 || ratio >= 0.74)) {
            launchPressureStage(ctx, st, "INTERDICTION NET DEPLOYING",
                    "PHASE: Push the command ship through the last interception barrier",
                    "THREAT: Heavy pursuit elements are trying to pin the convoy in place",
                    pressureRolesFor(st, 2));
        }
    }

    private static void updateLateCapturePressure(GameContext ctx, CampaignState st) {
        double ratio = progressRatio(st);
        if (st.authoredWaveCursor == 0 && (st.sectorElapsed >= 30.0 || ratio >= 0.18)) {
            launchPressureStage(ctx, st, "RELIEF COLUMN INBOUND",
                    "PHASE: Hold the objective while the first relief column arrives",
                    "THREAT: Local defenders are calling in response ships",
                    pressureRolesFor(st, 0));
            return;
        }
        if (st.authoredWaveCursor == 1 && (st.sectorElapsed >= 105.0 || ratio >= 0.48)) {
            launchPressureStage(ctx, st, "RECAPTURE PACKAGE COMMITTING",
                    "PHASE: Keep the point clean while heavier hulls hit the perimeter",
                    "THREAT: Electronic-war and missile hulls are contesting the uplink",
                    pressureRolesFor(st, 1));
            return;
        }
        if (st.authoredWaveCursor == 2 && (st.sectorElapsed >= 190.0 || ratio >= 0.78)) {
            launchPressureStage(ctx, st, "FINAL RECAPTURE PUSH",
                    "PHASE: Beat back the final retake attempt and secure the array",
                    "THREAT: The enemy is committing a last recapture package",
                    pressureRolesFor(st, 2));
        }
    }

    private static void updateLateBossPressure(GameContext ctx, CampaignState st) {
        Ship boss = findShipById(ctx, st.bossTargetId);
        if (boss == null || !boss.alive || boss.hp <= 0) return;
        double hpFrac = (boss.hpMax <= 0) ? 0.0 : (boss.hp / (double) boss.hpMax);
        if (st.authoredWaveCursor == 0 && hpFrac <= 0.82) {
            launchPressureStage(ctx, st, "BOSS ESCORTS REDEPLOYING",
                    "PHASE: Break through the first reserve screen around the flagship",
                    "THREAT: Secondary escorts are moving to cover the command hull",
                    pressureRolesFor(st, 0));
            return;
        }
        if (st.authoredWaveCursor == 1 && hpFrac <= 0.54) {
            launchPressureStage(ctx, st, "HEAVY ESCORTS JOINING THE FIGHT",
                    "PHASE: Keep the flagship under pressure while the escort wall thickens",
                    "THREAT: Heavy support hulls are reinforcing the boss",
                    pressureRolesFor(st, 1));
            return;
        }
        if (st.authoredWaveCursor == 2 && hpFrac <= 0.28) {
            launchPressureStage(ctx, st, "FINAL DEFENSE SCREEN",
                    "PHASE: Collapse the last defense layer and finish the command hull",
                    "THREAT: The occupation fleet is committing its final close guard",
                    pressureRolesFor(st, 2));
        }
    }

    private static double progressRatio(CampaignState st) {
        if (st == null || st.objectiveGoal <= 1e-6) return 0.0;
        return MathUtil.clamp(st.objectiveProgress / st.objectiveGoal, 0.0, 1.0);
    }

    private static void updatePocketDiscoveries(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null || st.discoverySites.isEmpty()) return;
        for (DiscoverySite site : st.discoverySites) {
            if (site == null || site.discovered) continue;
            if (!isPlayerInsideRadius(ctx.player, site.x, site.y, site.radius)) continue;
            site.discovered = true;
            st.discoveriesFound++;
            announceDiscoverySite(ctx, st, site);
            resolveDiscoverySite(ctx, st, site);
        }
    }

    private static void updateRecoverableWreckSites(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null || st.recoverableWreckSites.isEmpty()) return;
        double now = Math.max(0.0, st.sectorElapsed);
        for (RecoverableWreckSite site : st.recoverableWreckSites) {
            if (site == null || site.claimed) continue;
            if (!isPlayerInsideRadius(ctx.player, site.x, site.y, site.radius)) continue;
            if (hostilesNearPoint(ctx, site.x, site.y, 620.0) > 0) {
                if (now - site.lastThreatWarnAtSec >= 2.0) {
                    EventSystem.showBanner(ctx, "RECOVERY BLOCKED: HOSTILES TOO CLOSE TO " + site.label.toUpperCase(Locale.US), 1.8);
                    AudioSystem.playContextBanter(ctx, "captain", "recovery_blocked",
                            "CAPTAIN",
                            "Too hot to recover that hull. Clear the pocket first.",
                            2.0, 6.0, 2);
                    site.lastThreatWarnAtSec = now;
                }
                continue;
            }
            claimRecoverableWreckSite(ctx, st, site);
        }
    }

    private static void resolveDiscoverySite(GameContext ctx, CampaignState st, DiscoverySite site) {
        if (ctx == null || st == null || site == null) return;
        switch (site.kind) {
            case CACHE -> {
                grantStoryResources(ctx, 120 + st.sector * 6, 18 + st.sector * 2, site.label);
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 4);
            }
            case ORE -> {
                grantStoryResources(ctx, 60 + st.sector * 4, 42 + st.sector * 3, site.label);
                spawnCampaignAsteroidPocket(ctx, site.x, site.y, 5, 1.2, false);
            }
            case REINFORCEMENT -> {
                grantStoryResources(ctx, 90 + st.sector * 5, 16 + st.sector, site.label);
                addCoalitionFavor(st, 1, 0);
                spawnDiscoverySupportWing(ctx, st, site.x, site.y, "Relay Guard Detachment");
                EventSystem.showBanner(ctx, "DISCOVERY: COALITION SUPPORT ANSWERS THE CALL", 2.2);
            }
            case AMBUSH -> {
                spawnDiscoveryAmbush(ctx, st, site.x, site.y);
                EventSystem.showBanner(ctx, "DISCOVERY: RESERVE PICKET SPRINGS AN AMBUSH", 2.2);
            }
            case SALVAGE_HULK -> {
                grantStoryResources(ctx, 150 + st.sector * 7, 20 + st.sector, site.label);
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 8);
                spawnCampaignShip(ctx, ShipRole.HAULER, greenSupportFaction(st), site.x + 60.0, site.y - 40.0, "Recovered Tender");
                EventSystem.showBanner(ctx, "DISCOVERY: SALVAGE HULK CRACKED OPEN - RECOVERY SIGNAL CONFIRMED", 2.2);
            }
            case SUPPLY_CACHE -> {
                grantStoryResources(ctx, 80 + st.sector * 4, 12 + st.sector, site.label);
                applyLocalizedFleetRefit(ctx, site.x, site.y, 480.0, 22.0, 32.0);
                EventSystem.showBanner(ctx, "DISCOVERY: FIELD SUPPLY CACHE RESTORES THE SCREEN", 2.2);
            }
            case DATA_RELAY -> {
                grantStoryResources(ctx, 140 + st.sector * 8, 0, site.label);
                shiftBranchScore(st, 1);
                addCoalitionFavor(st, 1, 0);
                EventSystem.showBanner(ctx, "DISCOVERY: GHOST RELAY REVEALS FLEET INTEL", 2.2);
            }
            case WRECK_FIELD -> {
                grantStoryResources(ctx, 90 + st.sector * 5, 16 + st.sector * 2, site.label);
                spawnCampaignSalvagePocket(ctx, site.x, site.y, 10);
                spawnCampaignAsteroidPocket(ctx, site.x + 90.0, site.y - 60.0, 4, 0.8, false);
                EventSystem.showBanner(ctx, "DISCOVERY: WRECK FIELD YIELDS PARTS, DRIFT ORE, AND A RECOVERY LEAD", 2.2);
            }
            case MINEFIELD -> {
                spawnDiscoveryMinefield(ctx, st, site.x, site.y);
                EventSystem.showBanner(ctx, "DISCOVERY: DRIFT MINE CLUSTER ARMS AROUND THE FLEET", 2.2);
            }
            case DRIFTING_TURRET -> {
                grantStoryResources(ctx, 70 + st.sector * 4, 6 + st.sector, site.label);
                Faction faction = greenSupportFaction(st);
                spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, faction, site.x, site.y, "Recovered Defense Buoy");
                if (st.sector >= 10) {
                    spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, faction, site.x + 120.0, site.y + 50.0, "Buoy Screen");
                }
                EventSystem.showBanner(ctx, "DISCOVERY: DRIFTING WEAPON PLATFORM REACTIVATED", 2.2);
            }
            case NEUTRAL_TRADER -> {
                grantStoryResources(ctx, 110 + st.sector * 6, 14 + st.sector, site.label);
                addCoalitionFavor(st, 1, 0);
                spawnDiscoveryTraderConvoy(ctx, st, site.x, site.y);
                EventSystem.showBanner(ctx, "DISCOVERY: BROKER CARAVAN TRADES THROUGH THE BLACKOUT", 2.2);
            }
            case PRISON_BARGE -> {
                grantStoryResources(ctx, 100 + st.sector * 5, 18 + st.sector, site.label);
                addCoalitionFavor(st, 0, 1);
                spawnDiscoveryRescueWing(ctx, st, site.x, site.y);
                EventSystem.showBanner(ctx, "DISCOVERY: PRISON BARGE SURVIVORS JOIN THE COLUMN", 2.2);
            }
            case ANOMALY -> resolveAnomalySite(ctx, st, site);
            case FLEET_ASSET -> {
                grantStoryResources(ctx, 130 + st.sector * 7, 10 + st.sector, site.label);
                applyLocalizedFleetRefit(ctx, site.x, site.y, 520.0, 16.0, 22.0);
                spawnDiscoveryFleetAsset(ctx, st, site.x, site.y);
                shiftBranchScore(st, 1);
                EventSystem.showBanner(ctx, "DISCOVERY: PROTOTYPE FLEET ASSET MARKS A RECOVERABLE HULL", 2.2);
            }
        }
    }

    private static void announceDiscoverySite(GameContext ctx, CampaignState st, DiscoverySite site) {
        if (ctx == null || st == null || site == null) return;
        String label = trimmedOrFallback(site.label, "scanner contact");
        switch (site.kind) {
            case ANOMALY -> AudioSystem.playContextBanter(ctx, "science", "scanner_anomaly_found",
                    "SCIENCE",
                    "Scanner contact resolved. " + label + " is an anomaly pocket.",
                    2.4, 7.0, 2);
            case AMBUSH, MINEFIELD, DRIFTING_TURRET -> AudioSystem.playContextBanter(ctx, "tactical", "scanner_threat_found",
                    "TACTICAL",
                    "Scanner contact resolved. " + label + " is hostile.",
                    2.2, 6.0, 2);
            case REINFORCEMENT, DATA_RELAY, NEUTRAL_TRADER, PRISON_BARGE -> AudioSystem.playContextBanter(ctx, "comms", "scanner_contact_found",
                    "COMMS",
                    "Scanner contact resolved. " + label + " may change the battlespace.",
                    2.4, 6.5, 2);
            case SALVAGE_HULK, WRECK_FIELD, FLEET_ASSET -> AudioSystem.playContextBanter(ctx, "engineering", "scanner_salvage_found",
                    "ENGINEERING",
                    "Scanner contact resolved. " + label + " looks recoverable.",
                    2.4, 6.5, 2);
            case CACHE, SUPPLY_CACHE, ORE -> AudioSystem.playContextBanter(ctx, "science", "scanner_resource_found",
                    "SCIENCE",
                    "Scanner contact resolved. " + label + " is a resource pocket.",
                    2.2, 5.5, 2);
        }
    }

    private static void spawnDiscoverySupportWing(GameContext ctx, CampaignState st, double x, double y, String prefix) {
        if (ctx == null || st == null) return;
        Faction faction = greenSupportFaction(st);
        spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, x + 70.0, y - 40.0, prefix);
        spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, faction, x - 70.0, y + 40.0, "Relay Flak Detachment");
        if (st.sector >= 14) {
            spawnCampaignShip(ctx, ShipRole.MISSILE_BOAT, faction, x + 130.0, y + 110.0, "Relay Spear Detachment");
        }
    }

    private static void spawnDiscoveryAmbush(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        spawnEnemyAtPoint(ctx, ShipRole.PATROL, x + 80.0, y - 30.0);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, x + 150.0, y + 55.0);
        spawnEnemyAtPoint(ctx, ShipRole.PICKET, x - 90.0, y + 80.0);
        if (st.sector >= 12) {
            spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, x - 160.0, y - 70.0);
        }
    }

    private static void spawnDiscoveryMinefield(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.ENEMY, x - 120.0, y - 60.0, "Mine Anchor Alpha");
        spawnCampaignShip(ctx, ShipRole.STATIC_TURRET, Faction.ENEMY, x + 140.0, y + 70.0, "Mine Anchor Beta");
        spawnEnemyAtPoint(ctx, ShipRole.PICKET, x + 40.0, y - 120.0);
        if (st.sector >= 10) {
            spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, x - 150.0, y + 130.0);
        }
    }

    private static void spawnDiscoveryTraderConvoy(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        Faction faction = greenSupportFaction(st);
        spawnCampaignShip(ctx, ShipRole.TRANSPORT, faction, x - 40.0, y, "Broker Spine");
        spawnCampaignShip(ctx, ShipRole.HAULER, faction, x - 130.0, y + 90.0, "Ledger Tender");
        if (st.sector >= 12) {
            spawnCampaignShip(ctx, ShipRole.MINER, faction, x + 110.0, y - 80.0, "Prospector Escort");
        }
    }

    private static void spawnDiscoveryRescueWing(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        Faction faction = yellowSupportFaction(st);
        spawnCampaignShip(ctx, ShipRole.TRANSPORT, faction, x - 60.0, y + 30.0, "Liberation Tender");
        spawnCampaignShip(ctx, ShipRole.PATROL, faction, x + 80.0, y - 40.0, "Escape Screen One");
        if (st.sector >= 13) {
            spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, x + 150.0, y + 90.0, "Escape Screen Two");
        }
    }

    private static void spawnDiscoveryFleetAsset(GameContext ctx, CampaignState st, double x, double y) {
        if (ctx == null || st == null) return;
        Faction faction = Faction.ALLY;
        if (st.sector >= 18) {
            spawnCampaignShip(ctx, ShipRole.BATTLECRUISER, faction, x, y, "Recovered Fleet Spine");
            spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, faction, x - 120.0, y + 120.0, "Fleet Spine Screen");
        } else if (st.sector >= 10) {
            spawnCampaignShip(ctx, ShipRole.LIGHT_CRUISER, faction, x, y, "Recovered Strike Hull");
            spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, x - 120.0, y + 100.0, "Strike Hull Screen");
        } else {
            spawnCampaignShip(ctx, ShipRole.FRIGATE, faction, x, y, "Recovered Escort Hull");
            spawnCampaignShip(ctx, ShipRole.CIWS_CORVETTE, faction, x - 110.0, y + 80.0, "Escort Hull Screen");
        }
    }

    private static void resolveAnomalySite(GameContext ctx, CampaignState st, DiscoverySite site) {
        if (ctx == null || st == null || site == null) return;
        int roll = Math.max(0, Math.floorMod(st.sector + st.discoveriesFound + (int) Math.round(site.x + site.y), 4));
        describeAnomalySite(ctx, site, roll);
        switch (roll) {
            case 0 -> {
                applyLocalizedFleetRefit(ctx, site.x, site.y, 540.0, 18.0, 28.0);
                EventSystem.showBanner(ctx, "DISCOVERY: ANOMALY UNWINDS INTO A REPAIR MIST", 2.2);
            }
            case 1 -> {
                grantStoryResources(ctx, 90 + st.sector * 5, 10 + st.sector, site.label);
                shiftBranchScore(st, 1);
                addRecoverableWreckSite(st, site.x + 45.0, site.y - 35.0,
                        salvageRecoveryRoleForSector(st, site.kind),
                        "Ghost-Chassis Echo",
                        "An anomaly-stabilized hull frame is now recoverable.");
                EventSystem.showBanner(ctx, "DISCOVERY: SENSOR GHOSTS EXPOSE NEW FLEET VECTORS AND A RECOVERY HULL", 2.2);
            }
            case 2 -> {
                spawnCampaignAsteroidPocket(ctx, site.x, site.y, 4, 1.05, true);
                grantStoryResources(ctx, 40 + st.sector * 3, 26 + st.sector * 2, site.label);
                EventSystem.showBanner(ctx, "DISCOVERY: WARP ECHO CONDENSES INTO RARE ORE", 2.2);
            }
            default -> {
                spawnDiscoveryAmbush(ctx, st, site.x, site.y);
                EventSystem.showBanner(ctx, "DISCOVERY: GRAVITY SHEAR HIDES A HOSTILE SCREEN", 2.2);
            }
        }
    }

    private static void describeAnomalySite(GameContext ctx, DiscoverySite site, int roll) {
        if (ctx == null || site == null) return;
        String label = trimmedOrFallback(site.label, "anomaly contact");
        String line = switch (Math.floorMod(roll, 4)) {
            case 0 -> label + " is bleeding off charge into a repair mist. It should help any hulls that hold close.";
            case 1 -> label + " is wrapping itself around old wreck mass. There may be a recoverable hull shell inside it.";
            case 2 -> label + " is condensing charge into rare ore and debris. This pocket may be worth working before we leave.";
            default -> label + " is masking hostile motion. Expect the distortion to hide an ambush.";
        };
        AudioSystem.playContextBanter(ctx, "science", "anomaly_zone_description",
                "SCIENCE",
                line,
                2.8, 9.0, 2);
    }

    private static void applyLocalizedFleetRefit(GameContext ctx, double x, double y, double radius, double hullRepair, double shieldRepair) {
        if (ctx == null || ctx.ships == null) return;
        double rr = Math.max(120.0, radius);
        double rr2 = rr * rr;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || !ship.faction.isFriendlyTo(Faction.ALLY)) continue;
            if (GameMath.dist2(ship.x, ship.y, x, y) > rr2) continue;
            ship.healHull(hullRepair);
            ship.healShield(shieldRepair);
        }
    }

    private static void addCoalitionFavor(CampaignState st, int greenDelta, int yellowDelta) {
        if (st == null) return;
        st.greenContractFavor = Math.max(0, st.greenContractFavor + Math.max(0, greenDelta));
        st.yellowLiberationFavor = Math.max(0, st.yellowLiberationFavor + Math.max(0, yellowDelta));
    }

    private static void shiftBranchScore(CampaignState st, int delta) {
        if (st == null || delta == 0) return;
        st.branchScore += delta;
        st.branchRoute = branchRouteLabel(st.branchScore);
    }

    private static int hostilesNearPoint(GameContext ctx, double x, double y, double radius) {
        if (ctx == null || ctx.ships == null || ctx.player == null || ctx.player.faction == null) return 0;
        double rr = Math.max(120.0, radius);
        double rr2 = rr * rr;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0 || ship.faction == null) continue;
            if (ship.faction.isFriendlyTo(ctx.player.faction)) continue;
            if (GameMath.dist2(ship.x, ship.y, x, y) <= rr2) count++;
        }
        return count;
    }

    private static void claimRecoverableWreckSite(GameContext ctx, CampaignState st, RecoverableWreckSite site) {
        if (ctx == null || st == null || site == null || site.claimed || site.role == null) return;
        PersistentFleetEntry entry = addPersistentFleetEntry(st, site.role,
                "Recovered " + roleDisplayName(site.role), CAMPAIGN_FLAGSHIP_COMMAND_GROUP);
        if (entry == null) return;
        rebalancePersistentCommandGroups(st);
        spawnPurchasedPersistentBlueShip(ctx, st, entry);
        site.claimed = true;
        st.recoverableWrecksClaimed++;
        shiftBranchScore(st, 1);
        EventSystem.showBanner(ctx, "RECOVERY COMPLETE: " + roleDisplayName(site.role).toUpperCase(Locale.US) + " JOINS THE FLEET", 2.4);
        EventSystem.showWorldCallout(ctx, site.x, site.y, "RECOVERED HULL", new Color(186, 240, 180), 3.0);
        AudioSystem.playContextBanter(ctx, "engineering", "recovery_complete",
                "ENGINEERING",
                "Recovery frame is live. We've brought that hull onto our net.",
                2.2, 7.0, 2);
    }

    private static String roleDisplayName(ShipRole role) {
        if (role == null) return "Hull";
        String raw = role.name().toLowerCase(Locale.US).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static Faction greenSupportFaction(CampaignState st) {
        if (st != null && st.campaignBlueGreenAlliance) return Faction.TEAM_C;
        return Faction.ALLY;
    }

    private static Faction yellowSupportFaction(CampaignState st) {
        if (st != null && st.campaignBlueYellowAlliance) return Faction.TEAM_D;
        return Faction.ALLY;
    }

    private static void updateMissionSectionFlow(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.missionSections.isEmpty()) return;
        if (st.objectiveType == ObjectiveType.DESTROY
                && usesAuthoredDestroyProgress(st)
                && st.objectiveProgress >= st.objectiveGoal - 1e-6) {
            return;
        }

        int sectionCount = st.missionSections.size();
        int currentIndex = Math.max(0, Math.min(sectionCount - 1, st.activeMissionSection));
        if (st.missionSectionTravelLocked) {
            MissionSection target = st.missionSections.get(currentIndex);
            if (ctx.player != null && isPlayerInsideRadius(ctx.player, target.x, target.y, target.radius)) {
                st.missionSectionTravelLocked = false;
                EventSystem.showBanner(ctx, "MISSION SECTION REACHED: " + target.label, 2.0);
                AudioSystem.playContextBanter(ctx, "helm", "mission_section_reached",
                        "HELM", "Flagship inside " + target.label + ". We are back on the objective lane.",
                        2.5, 9.0, 2);
                spawnMissionSectionArrivalWave(ctx, st, currentIndex, target);
            }
        }

        int lastUnlockedIndex = st.missionSectionTravelLocked ? Math.max(0, currentIndex - 1) : currentIndex;
        double unlockedCap = missionSectionProgressCap(sectionCount, lastUnlockedIndex);
        st.objectiveProgress = Math.min(st.objectiveProgress, unlockedCap);

        if (!st.missionSectionTravelLocked && currentIndex < sectionCount - 1) {
            double stageCap = missionSectionProgressCap(sectionCount, currentIndex);
            if (st.objectiveProgress >= stageCap - 1e-6) {
                st.objectiveProgress = Math.min(st.objectiveProgress, stageCap);
                st.activeMissionSection = Math.min(sectionCount - 1, currentIndex + 1);
                st.missionSectionTravelLocked = true;
                MissionSection next = st.missionSections.get(st.activeMissionSection);
                seedWaypointFromObjectives(ctx, st, true);
                EventSystem.showBanner(ctx, "REPOSITION TO " + next.label, 2.1);
                AudioSystem.playContextBanter(ctx, "captain", "mission_section_reposition",
                        "BLUE COMMAND", "Pocket clear. Shift the flagship to " + next.label + " before kill credit resumes.",
                        2.8, 10.0, 2);
            }
        }

        decorateMissionSectionHud(st);
    }

    private static void decorateMissionSectionHud(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return;
        int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
        MissionSection section = st.missionSections.get(index);
        String phaseClause = st.missionSectionTravelLocked
                ? "TRANSIT: Fleet-jump toward " + section.label
                : "SECTION: Fight in " + section.label;
        String threatClause = st.missionSectionTravelLocked
                ? "VECTOR: The next fight will not progress until the flagship reaches the new pocket"
                : "DISCOVERY: Sweep side pockets for caches, ore, support, or hidden ambushes";
        st.objectivePhaseLabel = appendHudClause(stripDynamicHudClauses(st.objectivePhaseLabel), phaseClause);
        st.threatStateLabel = appendHudClause(stripDynamicHudClauses(st.threatStateLabel), threatClause);
    }

    private static String stripDynamicHudClauses(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : text.split("\\s{3,}")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("SECTION:")
                    || trimmed.startsWith("TRANSIT:")
                    || trimmed.startsWith("VECTOR:")
                    || trimmed.startsWith("DISCOVERY:")) {
                continue;
            }
            if (out.length() > 0) out.append("   ");
            out.append(trimmed);
        }
        return out.toString();
    }

    private static double missionSectionProgressCap(int sectionCount, int sectionIndex) {
        if (sectionCount <= 0) return 1.0;
        int clampedIndex = Math.max(0, Math.min(sectionCount - 1, sectionIndex));
        return (clampedIndex + 1) / (double) sectionCount;
    }

    private static void seedWaypointFromObjectives(GameContext ctx, CampaignState st, boolean requireTravelLock) {
        if (ctx == null || ctx.ui == null || st == null) return;
        if (requireTravelLock && !st.missionSectionTravelLocked) return;
        List<CampaignObjectiveMarker> markers = activeObjectiveMarkers(ctx);
        CampaignObjectiveMarker best = null;
        for (CampaignObjectiveMarker marker : markers) {
            if (marker == null) continue;
            if (requireTravelLock && marker.type != ObjectiveMarkerType.NEXT_ROUTE) continue;
            if (best == null || marker.priority > best.priority) {
                best = marker;
            }
        }
        if (best == null) return;
        ctx.ui.waypointX = GameMath.clamp(best.x, 0, ctx.WORLD_W);
        ctx.ui.waypointY = GameMath.clamp(best.y, 0, ctx.WORLD_H);
    }

    private static boolean isPlayerInsideRadius(Player player, double x, double y, double radius) {
        if (player == null) return false;
        double range = Math.max(20.0, radius + player.radius);
        return GameMath.dist2(player.x, player.y, x, y) <= range * range;
    }

    private static void spawnMissionSectionArrivalWave(GameContext ctx, CampaignState st, int sectionIndex, MissionSection section) {
        if (ctx == null || st == null || section == null) return;
        ShipRole lead = (st.objectiveType == ObjectiveType.SURVIVE || st.objectiveType == ObjectiveType.ESCORT)
                ? ShipRole.FRIGATE
                : ShipRole.MISSILE_BOAT;
        ShipRole escort = (sectionIndex >= 2 || st.sector >= 16) ? ShipRole.LIGHT_CRUISER : ShipRole.PATROL;
        spawnEnemyAtPoint(ctx, lead, section.x + 140.0, section.y - 70.0);
        spawnEnemyAtPoint(ctx, escort, section.x + 220.0, section.y + 90.0);
        if (sectionIndex >= 1) {
            spawnEnemyAtPoint(ctx, ShipRole.PICKET, section.x - 120.0, section.y + 110.0);
        }
    }

    private static void updateDistributedMapPressure(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        double[] thresholds = {35.0, 80.0, 140.0};
        while (st.mapPressureStage < thresholds.length && st.sectorElapsed >= thresholds[st.mapPressureStage]) {
            int stage = st.mapPressureStage;
            st.mapPressureStage++;
            boolean enteredFromRight = missionSubzoneColumn(st.loadedMissionSubzone) >= (MISSION_ZONE_COLUMNS / 2);
            int reserveZone = missionSubzoneIndex(enteredFromRight ? 1 : 4, 1);
            double reserveX = missionSubzoneCenterX(ctx, st.sector, reserveZone);
            double reserveY = missionSubzoneCenterY(ctx, st.sector, reserveZone);
            ShipRole[] roles = distributedPressureRoles(st, stage);
            double[][] offsets = {
                    {0.0, 0.0},
                    {120.0, -120.0},
                    {-110.0, 100.0},
                    {220.0, 40.0},
                    {-220.0, -20.0}
            };
            for (int i = 0; i < roles.length && i < offsets.length; i++) {
                spawnEnemyAtPoint(ctx, roles[i], reserveX + offsets[i][0], reserveY + offsets[i][1]);
            }
            EventSystem.showBanner(ctx, "ENEMY RESERVES COMMITTING FROM RESERVE STAGING", 1.8);
            st.threatStateLabel = appendHudClause(st.threatStateLabel, "RESERVES: Reserve staging is spilling into the next pocket.");
            AudioSystem.playContextBanter(ctx, "tactical", "mission_reserves_committing",
                    "TACTICAL", "Reserve staging just lit up. Fresh hostiles are crossing into the fight.",
                    2.6, 12.0, 2);
        }
    }

    private static void updateMissionBanter(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.awaitingEpisodeLaunch || st.awaitingFleetHubChoice || st.transitionTimer > 0.0) {
            return;
        }
        if (!st.missionStartBanterPlayed && !st.introSequenceActive && st.sectorElapsed >= 1.0) {
            st.missionStartBanterPlayed = true;
            playMissionStartBanter(ctx, st);
        }
        if (st.sectorTimeLimit <= 0.0 || st.objectiveProgress >= st.objectiveGoal - 1e-6) {
            return;
        }
        double remaining = st.sectorTimeLimit - st.sectorElapsed;
        if (remaining <= 45.0 && st.extractionWarningStage < 2) {
            st.extractionWarningStage = 2;
            AudioSystem.playContextBanter(ctx, "captain", "mission_timeout_critical",
                    "BLUE COMMAND", "Forty-five seconds. Finish the pocket or break for extraction now.",
                    2.5, 18.0, 3);
        } else if (remaining <= 120.0 && st.extractionWarningStage < 1) {
            st.extractionWarningStage = 1;
            String caption = timeoutCountsAsSuccess(st)
                    ? "Two minutes. Keep the convoy alive and be ready to extract on the mark."
                    : "Two minutes. Objective still incomplete. Keep pressure on the primary target.";
            AudioSystem.playContextBanter(ctx, "science", "mission_timeout_warning",
                    "SCIENCE", caption, 2.5, 18.0, 2);
        }
    }

    private static void playMissionStartBanter(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        String label = st.objectiveLabel == null ? "the objective" : st.objectiveLabel.toLowerCase(Locale.US);
        String situation = missionSituationHint(st);
        switch (st.objectiveType) {
            case DESTROY -> AudioSystem.playContextBanter(ctx, "captain", "mission_destroy_start",
                    "BLUE COMMAND", joinBanterClauses(
                            "Weapons free. Break " + label + " before red can regroup around it.",
                            situation),
                    2.7, 14.0, 2);
            case ESCORT -> AudioSystem.playContextBanter(ctx, "helm", "mission_escort_start",
                    "HELM", joinBanterClauses(
                            "Escort lane is live. Keep our protected ships inside the flagship screen.",
                            situation),
                    2.7, 14.0, 2);
            case SURVIVE -> AudioSystem.playContextBanter(ctx, "tactical", "mission_survive_start",
                    "TACTICAL", joinBanterClauses(
                            "Red will try to grind us down here. Hold formation and bleed their pushes.",
                            situation),
                    2.7, 14.0, 2);
            case CAPTURE -> AudioSystem.playContextBanter(ctx, "science", "mission_capture_start",
                    "SCIENCE", joinBanterClauses(
                            "Capture zone is marked. We need the flagship parked on it long enough to lock control.",
                            situation),
                    2.7, 14.0, 2);
            case BOSS, FINAL_BOSS -> AudioSystem.playContextBanter(ctx, "engineering", "mission_boss_start",
                    "ENGINEERING", joinBanterClauses(
                            "Heavy signature confirmed. Keep the flagship stable and expect phase changes.",
                            situation),
                    2.7, 14.0, 2);
        }
    }

    private static String missionSituationHint(CampaignState st) {
        if (st == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        if (st.missionTheme != null) {
            parts.add(missionThemeLead(st.missionTheme));
        }
        if (st.discoverySites != null) {
            for (DiscoverySite site : st.discoverySites) {
                if (site == null || site.kind != DiscoveryKind.ANOMALY) continue;
                parts.add("Science is tracking anomaly behavior in " + trimmedOrFallback(site.label, "the anomaly pocket").toLowerCase(Locale.US) + ".");
                break;
            }
        }
        if (parts.isEmpty()) return "";
        return String.join(" ", parts);
    }

    private static String joinBanterClauses(String main, String addon) {
        String a = (main == null) ? "" : main.trim();
        String b = (addon == null) ? "" : addon.trim();
        if (a.isBlank()) return b;
        if (b.isBlank()) return a;
        return a + " " + b;
    }

    private static ShipRole[] distributedPressureRoles(CampaignState st, int stage) {
        boolean late = st != null && st.sector >= 12;
        boolean endgame = st != null && st.sector >= 20;
        return switch (stage) {
            case 0 -> late
                    ? new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                    : new ShipRole[]{ShipRole.PATROL, ShipRole.FRIGATE, ShipRole.PICKET};
            case 1 -> endgame
                    ? new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                    : new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
            default -> endgame
                    ? new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                    : new ShipRole[]{ShipRole.BULWARK_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE};
        };
    }

    private static boolean usesAuthoredDestroyProgress(CampaignState st) {
        if (st == null || st.objectiveType != ObjectiveType.DESTROY) return false;
        return switch (st.sector) {
            case 2, 4, 13, 15, 17, 19, 21, 23 -> true;
            case 3 -> st.objectiveStage == 0;
            default -> false;
        };
    }

    private static boolean destroyObjectiveUsesMarkers(CampaignState st) {
        if (st == null || st.objectiveType != ObjectiveType.DESTROY) return false;
        return usesAuthoredDestroyProgress(st) || !st.authoredObjectiveHostiles.isEmpty();
    }

    private static boolean timeoutCountsAsSuccess(CampaignState st) {
        if (st == null) return false;
        if (st.sector == 1) return true;
        if (st.sector == 2) {
            return st.objectiveAssetRequiredSurvivors <= 0 || !objectiveAssetQuotaFailed(st);
        }
        return false;
    }

    private static boolean objectiveAssetQuotaFailed(CampaignState st) {
        if (st == null || st.objectiveAssetRequiredSurvivors <= 0) return false;
        return liveObjectiveAssets(st) < st.objectiveAssetRequiredSurvivors;
    }

    private static int liveObjectiveAssets(CampaignState st) {
        if (st == null) return 0;
        return Math.max(0, st.objectiveAssetTotal - st.objectiveAssetLosses);
    }

    private static String objectiveAssetFailureText(CampaignState st) {
        String label = objectiveAssetFailureLabel(st);
        if (!label.isBlank()) {
            return "DEFEAT: " + label + " BELOW SAFE COUNT";
        }
        return "DEFEAT: OBJECTIVE ASSETS BELOW SAFE COUNT";
    }

    private static String objectiveAssetFailureBanner(CampaignState st) {
        if (st == null) return "Objective asset quota failed";
        StringBuilder detail = new StringBuilder();
        if (st.objectiveAssetFailureText != null && !st.objectiveAssetFailureText.isBlank()) {
            detail.append(stripPrefix(st.objectiveAssetFailureText, "DEFEAT:"));
        } else {
            detail.append("Objective asset quota failed");
        }
        String label = objectiveAssetFailureLabel(st);
        if (!label.isBlank() && st.objectiveAssetTotal > 0) {
            if (detail.length() > 0) detail.append("  |  ");
            detail.append(Math.max(0, liveObjectiveAssets(st)))
                    .append("/")
                    .append(st.objectiveAssetTotal)
                    .append(" ")
                    .append(label)
                    .append(" survived");
        }
        return detail.toString();
    }

    private static String objectiveAssetFailureLabel(CampaignState st) {
        if (st == null || st.objectiveAssetLabel == null || st.objectiveAssetLabel.isBlank()) return "";
        return st.objectiveAssetLabel.trim().toUpperCase(Locale.US);
    }

    private static void launchPressureStage(GameContext ctx, CampaignState st, String banner,
                                            String phaseLabel, String threatLabel, ShipRole... roles) {
        if (ctx == null || st == null || roles == null || roles.length == 0) return;
        spawnPressurePackage(ctx, st, roles);
        st.authoredWaveCursor++;
        st.objectivePhaseLabel = phaseLabel;
        st.threatStateLabel = threatLabel;
        EventSystem.showBanner(ctx, banner, 2.0);
        logTelemetry("sector_pressure",
                "sector=" + st.sector + " stage=" + st.authoredWaveCursor + " objective=" + st.objectiveType);
    }

    private static void spawnPressurePackage(GameContext ctx, CampaignState st, ShipRole... roles) {
        if (ctx == null || st == null || roles == null || roles.length == 0) return;
        double anchorX = objectiveAnchorX(ctx, st);
        double anchorY = objectiveAnchorY(ctx, st);
        double baseX = GameMath.clamp(anchorX + 720.0 + st.authoredWaveCursor * 110.0, 180.0, ctx.WORLD_W - 180.0);
        double baseY = GameMath.clamp(anchorY + ((st.authoredWaveCursor % 2 == 0) ? -170.0 : 150.0), 180.0, ctx.WORLD_H - 180.0);
        double[][] slots = {
                {0.0, 0.0},
                {120.0, -120.0},
                {150.0, 110.0},
                {280.0, -60.0},
                {320.0, 140.0},
                {420.0, 20.0}
        };
        for (int i = 0; i < roles.length && i < slots.length; i++) {
            ShipRole role = roles[i];
            if (role == null) continue;
            spawnEnemyAtPoint(ctx, role, baseX + slots[i][0], baseY + slots[i][1]);
        }
    }

    private static double objectiveAnchorX(GameContext ctx, CampaignState st) {
        if (st != null) {
            if (!st.missionSections.isEmpty()
                    && st.objectiveType != ObjectiveType.ESCORT
                    && st.objectiveType != ObjectiveType.BOSS
                    && st.objectiveType != ObjectiveType.FINAL_BOSS) {
                int sectionIndex = resolvedObjectiveSectionIndex(st);
                return st.missionSections.get(sectionIndex).x;
            }
            if (st.objectiveType == ObjectiveType.CAPTURE) return st.captureX;
            if (st.objectiveType == ObjectiveType.ESCORT && st.escortShip != null) return st.escortShip.x;
            if ((st.objectiveType == ObjectiveType.BOSS || st.objectiveType == ObjectiveType.FINAL_BOSS)) {
                Ship boss = findShipById(ctx, st.bossTargetId);
                if (boss != null) return boss.x;
            }
        }
        return (ctx != null && ctx.player != null) ? ctx.player.x : 2500.0;
    }

    private static double objectiveAnchorY(GameContext ctx, CampaignState st) {
        if (st != null) {
            if (!st.missionSections.isEmpty()
                    && st.objectiveType != ObjectiveType.ESCORT
                    && st.objectiveType != ObjectiveType.BOSS
                    && st.objectiveType != ObjectiveType.FINAL_BOSS) {
                int sectionIndex = resolvedObjectiveSectionIndex(st);
                return st.missionSections.get(sectionIndex).y;
            }
            if (st.objectiveType == ObjectiveType.CAPTURE) return st.captureY;
            if (st.objectiveType == ObjectiveType.ESCORT && st.escortShip != null) return st.escortShip.y;
            if ((st.objectiveType == ObjectiveType.BOSS || st.objectiveType == ObjectiveType.FINAL_BOSS)) {
                Ship boss = findShipById(ctx, st.bossTargetId);
                if (boss != null) return boss.y;
            }
        }
        return (ctx != null && ctx.player != null) ? ctx.player.y : 2500.0;
    }

    static boolean hasStrategicObjectiveMarker(GameContext ctx) {
        return !activeObjectiveMarkers(ctx).isEmpty();
    }

    static double strategicObjectiveMarkerX(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st != null && !st.missionSections.isEmpty()) {
            int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
            return st.missionSections.get(index).x;
        }
        return objectiveAnchorX(ctx, st);
    }

    static double strategicObjectiveMarkerY(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st != null && !st.missionSections.isEmpty()) {
            int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
            return st.missionSections.get(index).y;
        }
        return objectiveAnchorY(ctx, st);
    }

    static String strategicObjectiveMarkerLabel(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st != null && !st.missionSections.isEmpty()) {
            int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
            MissionSection section = st.missionSections.get(index);
            if (section != null && section.label != null && !section.label.isBlank()) {
                return section.label;
            }
        }
        String title = hudObjectiveTitle(ctx);
        if (title != null && !title.isBlank()) return title;
        return "OBJECTIVE";
    }

    private static ShipRole[] pressureRolesFor(CampaignState st, int stage) {
        if (st == null) return new ShipRole[]{ShipRole.FRIGATE};
        boolean late = st.sector >= 13;
        boolean endgame = st.sector >= 21;
        return switch (st.objectiveType) {
            case SURVIVE -> switch (stage) {
                case 0 -> late
                        ? new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.LIGHT_CRUISER}
                        : new ShipRole[]{ShipRole.PATROL, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
                case 1 -> endgame
                        ? new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
                default -> endgame
                        ? new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.VANGUARD_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
            };
            case DESTROY -> switch (stage) {
                case 0 -> late
                        ? new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT}
                        : new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.PICKET};
                case 1 -> endgame
                        ? new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
                default -> endgame
                        ? new ShipRole[]{ShipRole.BULWARK_TITAN, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
            };
            case ESCORT -> switch (stage) {
                case 0 -> new ShipRole[]{ShipRole.PATROL, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
                case 1 -> late
                        ? new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.MISSILE_BOAT, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
                default -> endgame
                        ? new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.VANGUARD_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
            };
            case CAPTURE -> switch (stage) {
                case 0 -> new ShipRole[]{ShipRole.CIWS_CORVETTE, ShipRole.PATROL, ShipRole.LIGHT_CRUISER};
                case 1 -> late
                        ? new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
                default -> endgame
                        ? new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.BATTLECRUISER, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.COMMAND_INTEL_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
            };
            case BOSS, FINAL_BOSS -> switch (stage) {
                case 0 -> new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
                case 1 -> late
                        ? new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER, ShipRole.MISSILE_BOAT}
                        : new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT};
                default -> endgame
                        ? new ShipRole[]{ShipRole.BULWARK_TITAN, ShipRole.BATTLECRUISER, ShipRole.CIWS_CORVETTE}
                        : new ShipRole[]{ShipRole.INTERDICTION_TITAN, ShipRole.LIGHT_CRUISER, ShipRole.MISSILE_BOAT};
            };
        };
    }

    private static int spawnBoss(GameContext ctx, ShipRole role, String name, double hpMul, double shieldMul) {
        Ship boss = spawnCampaignShip(ctx, role, Faction.ENEMY, ctx.player.x + 760, ctx.player.y - 120, name);
        if (boss == null) return -1;
        boss.name = name;
        boss.hpMax = (int) Math.round(boss.hpMax * hpMul);
        boss.hp = boss.hpMax;
        boss.shieldMax *= shieldMul;
        boss.shield = boss.shieldMax;
        for (Turret t : boss.turrets) {
            t.damage = Math.max(1, (int) Math.round(t.damage * 1.35));
            t.cooldown = Math.max(0.05, t.cooldown * 0.88);
        }
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, boss.x + 160, boss.y - 80);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, boss.x - 150, boss.y + 100);
        spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, boss.x + 220, boss.y + 40);
        return boss.id;
    }

    private static int spawnFinalBoss(GameContext ctx) {
        Ship boss = spawnCampaignShip(ctx, ShipRole.MOTHERSHIP, Faction.ENEMY,
                ctx.player.x + 960, ctx.player.y - 120, "AI MOTHERSHIP EARTHFALL");
        if (boss == null) return -1;
        boss.hpMax = (int) Math.round(boss.hpMax * 2.2);
        boss.hp = boss.hpMax;
        boss.shieldMax *= 2.6;
        boss.shield = boss.shieldMax;
        boss.shieldRegen *= 1.5;
        for (Turret t : boss.turrets) {
            t.damage = Math.max(1, (int) Math.round(t.damage * 1.55));
            t.cooldown = Math.max(0.05, t.cooldown * 0.84);
        }
        spawnCampaignShip(ctx, ShipRole.BULWARK_TITAN, Faction.ENEMY, boss.x + 220, boss.y + 140, "Earthfall Bulwark");
        spawnCampaignShip(ctx, ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, boss.x - 240, boss.y - 150, "Earthfall Lance");
        spawnEnemyAtPoint(ctx, ShipRole.BATTLESHIP, boss.x + 170, boss.y + 90);
        spawnEnemyAtPoint(ctx, ShipRole.BATTLECRUISER, boss.x - 170, boss.y - 90);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, boss.x + 260, boss.y - 180);
        return boss.id;
    }

    private static SectorScript scriptFor(int sector) {
        int idx = Math.max(1, Math.min(SCRIPTS.length - 1, sector));
        return SCRIPTS[idx];
    }

    private static SectorLore loreFor(int sector) {
        int idx = Math.max(1, Math.min(LORE.length - 1, sector));
        SectorLore lore = LORE[idx];
        if (lore != null) return lore;
        return new SectorLore(idx, "UNTITLED SECTOR", "Unknown Theater", "Push the fleet onward.", "The fleet keeps moving.");
    }

    private static String actTitleFor(int act) {
        int idx = Math.max(1, Math.min(ACT_TITLES.length - 1, act));
        return ACT_TITLES[idx];
    }

    private static SideObjectiveScript sideScriptFor(int sector) {
        int idx = Math.max(1, Math.min(SIDE_SCRIPTS.length - 1, sector));
        SideObjectiveScript s = SIDE_SCRIPTS[idx];
        if (s == null) return new SideObjectiveScript(idx, SideObjectiveType.NONE, "", 0.0, 0);
        return s;
    }

    private static Ship spawnEnemyAtPlayerOffset(GameContext ctx, ShipRole role, double ox, double oy) {
        return spawnEnemyAtPoint(ctx, role, ctx.player.x + ox, ctx.player.y + oy);
    }

    private static Ship spawnAuthoredObjectiveEnemyAtPlayerOffset(GameContext ctx, CampaignState st, ShipRole role, double ox, double oy) {
        Ship s = spawnEnemyAtPlayerOffset(ctx, role, ox, oy);
        registerAuthoredObjectiveHostile(st, s);
        return s;
    }

    private static Ship spawnAllyAtPlayerOffset(GameContext ctx, ShipRole role, double ox, double oy) {
        return SpawnSystem.spawnAlly(ctx, role, ctx.player.x + ox, ctx.player.y + oy);
    }

    private static Ship spawnCampaignAllyAtPlayerOffset(GameContext ctx, ShipRole role, double ox, double oy, String name) {
        return spawnCampaignShip(ctx, role, Faction.ALLY, ctx.player.x + ox, ctx.player.y + oy, name);
    }

    private static Ship spawnEnemyAtPoint(GameContext ctx, ShipRole role, double x, double y) {
        Ship ship = SpawnSystem.spawnEnemy(ctx, role, x, y);
        primeCampaignEnemyForContact(ship);
        return ship;
    }

    private static Ship spawnCampaignShip(GameContext ctx, ShipRole role, Faction faction, double x, double y, String name) {
        if (ctx == null || role == null || faction == null) return null;
        Ship ship = new FleetShip(role, faction,
                GameMath.clamp(x, 30.0, ctx.WORLD_W - 30.0),
                GameMath.clamp(y, 30.0, ctx.WORLD_H - 30.0));
        primeCampaignEnemyForContact(ship);
        ctx.ships.add(ship);
        try { DoctrineRegistry.applyToShip(ship); } catch (Throwable ignored) {}
        if (ship != null && name != null && !name.isBlank()) {
            ship.name = name;
        }
        return ship;
    }

    private static void primeCampaignEnemyForContact(Ship ship) {
        if (ship == null || ship.faction != Faction.ENEMY) return;
        ship.aiForcedEngageTimer = Math.max(ship.aiForcedEngageTimer, 26.0);
        ship.aiArrivalFireDelayTimer = Math.max(ship.aiArrivalFireDelayTimer, 3.0);
    }

    private static Ship spawnAuthoredObjectiveEnemyAtPoint(GameContext ctx, CampaignState st, ShipRole role, double x, double y) {
        Ship s = spawnEnemyAtPoint(ctx, role, x, y);
        registerAuthoredObjectiveHostile(st, s);
        return s;
    }

    private static void registerAuthoredObjectiveHostile(CampaignState st, Ship s) {
        if (st == null || s == null) return;
        st.authoredObjectiveHostiles.add(s.id);
    }

    private static void registerObjectiveAsset(CampaignState st, Ship s) {
        if (st == null || s == null) return;
        if (st.objectiveAssetIds.add(s.id)) {
            st.objectiveAssetTotal++;
        }
    }

    private static void registerObjectiveAssetQuota(CampaignState st, int requiredSurvivors, String failureText) {
        if (st == null) return;
        st.objectiveAssetRequiredSurvivors = Math.max(0, requiredSurvivors);
        st.objectiveAssetFailureText = (failureText == null) ? "" : failureText;
    }

    private static Ship spawnConvoy(GameContext ctx, String name) {
        Ship base = TeamSystem.getBaseForTeam(ctx, Faction.ALLY);
        double sx = (base != null) ? base.x + 80 : ctx.player.x - 120;
        double sy = (base != null) ? base.y + 80 : ctx.player.y;
        Ship convoy = SpawnSystem.spawnAlly(ctx, ShipRole.TRANSPORT, sx, sy);
        convoy.name = name;
        convoy.desiredSpeed = Math.max(55.0, convoy.desiredSpeed);
        return convoy;
    }

    private static Ship spawnEscortTitan(GameContext ctx, ShipRole role, String name) {
        Ship base = TeamSystem.getBaseForTeam(ctx, Faction.ALLY);
        double sx = (base != null) ? base.x + 110 : ctx.player.x - 180;
        double sy = (base != null) ? base.y + 90 : ctx.player.y + 40;
        Ship titan = spawnCampaignShip(ctx, role, Faction.ALLY, sx, sy, name);
        if (titan != null) {
            titan.desiredSpeed = Math.max(48.0, titan.desiredSpeed);
        }
        return titan;
    }

    private static void spawnCoalitionSupportFleet(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        int greenTier = greenContractTier(st);
        if (greenTier >= 1 && st.sector >= 13) {
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -260, 220, "Green Contract Cruiser");
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, 300, "Green Contract Flak");
        }
        if (greenTier >= 2 && st.sector >= 13) {
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -380, 180, "Green Contract Frigate");
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.HAULER, Faction.TEAM_C, -420, 280, "Green Contract Tender");
        }
        if (greenTier >= 3 && st.sector >= 14) {
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -520, 70, "Green Contract Relay Titan");
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_C, -450, 360, "Green Contract Screen Two");
        }

        int yellowTier = yellowLiberationTier(st);
        if (yellowTier >= 1 && st.sector >= 20) {
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, Faction.TEAM_D, -340, 250, "Yellow Liberation Missile Boat");
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_D, -260, 330, "Yellow Liberation Flak");
        }
        if (yellowTier >= 2 && st.sector >= 20) {
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_D, -430, 180, "Yellow Liberation Frigate");
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_D, -380, 340, "Yellow Liberation Screen");
        }
        if (yellowTier >= 3 && st.sector >= 21) {
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BOARDING_RECOVERY_TITAN, Faction.TEAM_D, -560, 120, "Yellow Liberation Recovery Titan");
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, Faction.TEAM_D, -500, 300, "Yellow Liberation Cutter");
        }
    }

    private static void applySectorModifiers(GameContext ctx, CampaignState st, SectorScript script) {
        st.activeModifiers = script.modifiers;
        st.targetingRangeMul = 1.0;
        st.miningRateMul = 1.0;
        st.enemyWaveDelayMul = 1.0;
        st.enemyWaveGroupMul = 1.0;
        st.oreCreditMul = 1.0;
        st.sectorCreditBonusMul = 1.0;
        st.disableAutoLock = false;

        for (MapModifier mod : script.modifiers) {
            switch (mod) {
                case NONE -> {}
                case NEBULA -> {
                    st.targetingRangeMul *= 0.72;
                    st.enemyWaveDelayMul *= 1.08;
                }
                case DEBRIS_FIELD -> {
                    st.miningRateMul *= 0.86;
                    st.enemyWaveDelayMul *= 1.05;
                }
                case EMP_ZONE -> {
                    st.targetingRangeMul *= 0.65;
                    st.disableAutoLock = true;
                }
                case RESOURCE_DROUGHT -> {
                    st.miningRateMul *= 0.65;
                    st.oreCreditMul *= 1.30;
                    scaleAsteroidOre(ctx, 0.75, false);
                }
                case RICH_DEPOSITS -> {
                    st.miningRateMul *= 1.30;
                    st.oreCreditMul *= 0.85;
                    scaleAsteroidOre(ctx, 1.35, true);
                }
                case SOLAR_STORM -> {
                    st.enemyWaveGroupMul *= 1.25;
                    st.targetingRangeMul *= 0.90;
                }
                case GRAVITY_SHEAR -> {
                    st.enemyWaveDelayMul *= 0.90;
                    st.miningRateMul *= 0.90;
                }
                case SUPPLY_WINDFALL -> {
                    st.sectorCreditBonusMul *= 1.20;
                    st.oreCreditMul *= 1.10;
                }
            }
        }

        // Vertical-slice tuning (first 30 minutes): front-load income to keep upgrades moving
        // while scripted wave pressure increases each sector.
        if (st.sector <= AUTHORED_VERTICAL_SLICE_LAST_SECTOR) {
            double sectorBonusMul = switch (st.sector) {
                case 1 -> 1.08;
                case 2 -> 1.06;
                case 3 -> 1.04;
                default -> 1.0;
            };
            double oreMul = switch (st.sector) {
                case 1 -> 1.06;
                case 2 -> 1.04;
                case 3 -> 1.02;
                default -> 1.0;
            };
            st.sectorCreditBonusMul *= sectorBonusMul;
            st.oreCreditMul *= oreMul;
        }

        applySectorThreatBaseline(st);
        applyFleetPressureScaling(st);
    }

    private static void applySectorThreatBaseline(CampaignState st) {
        if (st == null) return;
        int sectorIndex = Math.max(0, st.sector - 1);
        st.enemyWaveGroupMul *= 1.0 + sectorIndex * 0.05;
        st.enemyWaveDelayMul *= Math.max(0.72, 1.0 - sectorIndex * 0.025);
        if (st.sector >= 15) st.enemyWaveGroupMul *= 1.10;
        if (st.sector >= 21) st.enemyWaveDelayMul *= 0.92;
    }

    private static void applyFleetPressureScaling(CampaignState st) {
        if (st == null) return;
        int standardUsed = standardCommandUsed(st);
        int eliteUsed = eliteCommandUsed(st);
        int titanCount = livePersistentFleetSlots(st, ShopHullCategory.TITAN);
        int extraStandard = Math.max(0, standardUsed - CAMPAIGN_FLAGSHIP_STANDARD_COMMAND_CAPACITY);
        st.enemyWaveGroupMul *= 1.0 + extraStandard * 0.04 + eliteUsed * 0.10 + titanCount * 0.08;
        double delayPenalty = 1.0 - extraStandard * 0.015 - eliteUsed * 0.035 - titanCount * 0.025;
        st.enemyWaveDelayMul *= Math.max(0.70, delayPenalty);
    }

    private static void scaleAsteroidOre(GameContext ctx, double mul, boolean forceRichVisual) {
        if (ctx == null || ctx.asteroids == null) return;
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            a.oreMax = Math.max(1, (int) Math.round(a.oreMax * mul));
            a.ore = Math.min(a.oreMax, Math.max(0, (int) Math.round(a.ore * mul)));
            if (forceRichVisual && a.oreMax >= 450) {
                a.rich = true;
                a.richness = Math.max(a.richness, 1.8);
            }
        }
    }

    private static void updateObjective(GameContext ctx, double dt) {
        CampaignState st = state(ctx);
        if (st == null) return;

        switch (st.objectiveType) {
            case SURVIVE -> st.objectiveProgress = Math.min(st.objectiveGoal, st.objectiveProgress + dt);
            case DESTROY -> {
                if (usesAuthoredDestroyProgress(st)) {
                    st.objectiveProgress = Math.min(st.objectiveGoal, st.authoredObjectiveKills);
                } else {
                    st.objectiveProgress = Math.min(st.objectiveGoal, st.kills);
                }
            }
            case BOSS, FINAL_BOSS -> {
                Ship boss = findShipById(ctx, st.bossTargetId);
                if (boss != null && boss.alive && boss.hp > 0) {
                    updateBossPhases(ctx, st, boss);
                    st.objectiveProgress = 0.0;
                } else {
                    st.objectiveProgress = st.objectiveGoal;
                }
            }
            case ESCORT -> {
                if (st.escortShip == null || !st.escortShip.alive || st.escortShip.hp <= 0) {
                    failRun(ctx, "DEFEAT: ESCORT LOST", "Escort target destroyed");
                    return;
                }
                st.escortFormationIntegrity = escortFormationIntegrity(ctx, st);
                if (st.escortFormationIntegrity >= ESCORT_PROGRESS_THRESHOLD) {
                    double gain = dt * Math.max(0.45, st.escortFormationIntegrity);
                    st.objectiveProgress = Math.min(st.objectiveGoal, st.objectiveProgress + gain);
                } else {
                    double deficit = ESCORT_PROGRESS_THRESHOLD - st.escortFormationIntegrity;
                    double decay = dt * (0.30 + deficit * 1.35);
                    st.objectiveProgress = Math.max(0.0, st.objectiveProgress - decay);
                }
            }
            case CAPTURE -> {
                if (!st.captureArmed) {
                    st.objectiveProgress = 0.0;
                    break;
                }
                boolean playerInside = false;
                if (ctx.player != null) {
                    double d2 = GameMath.dist2(ctx.player.x, ctx.player.y, st.captureX, st.captureY);
                    playerInside = d2 <= st.captureRadius * st.captureRadius;
                }
                boolean contested = hostileInsideCapture(ctx, st.captureX, st.captureY, st.captureRadius);
                if (playerInside && !contested) {
                    st.objectiveProgress = Math.min(st.objectiveGoal, st.objectiveProgress + dt);
                } else {
                    st.objectiveProgress = Math.max(0.0, st.objectiveProgress - dt * 0.5);
                }
            }
        }

        updateMissionSectionFlow(ctx, st);

        if (!st.missionSectionTravelLocked && objectiveAssetQuotaFailed(st)) {
            failRun(ctx, objectiveAssetFailureText(st), objectiveAssetFailureBanner(st));
            return;
        }

        if (st.objectiveProgress >= st.objectiveGoal) {
            secureSectorObjective(ctx, "OBJECTIVE COMPLETE - EXTRACTION WINDOW OPEN");
        }
    }

    private static void updateSideObjective(GameContext ctx, double dt) {
        CampaignState st = state(ctx);
        if (st == null || st.sideObjectiveType == SideObjectiveType.NONE) return;
        if (st.sideObjectiveCompleted || st.sideObjectiveFailed) return;

        switch (st.sideObjectiveType) {
            case KILL_COUNT -> {
                int deltaKills = Math.max(0, st.kills - st.sideObjectiveBaseKills);
                st.sideObjectiveProgress = Math.min(st.sideObjectiveGoal, deltaKills);
                if (st.sideObjectiveProgress >= st.sideObjectiveGoal) {
                    markSideObjectiveCompleted(ctx, st);
                }
            }
            case NO_HULL_DAMAGE_WINDOW -> {
                Ship protectedShip = captureSideObjectiveProtectedShip(ctx, st);
                if (protectedShip == null || !protectedShip.alive || protectedShip.hp <= 0) {
                    markSideObjectiveFailed(ctx, st, (st.objectiveType == ObjectiveType.ESCORT) ? "escort_down" : "player_down");
                    return;
                }
                if (protectedShip.hp < st.sideObjectiveProtectedShipStartHp) {
                    markSideObjectiveFailed(ctx, st, "hull_damage");
                    return;
                }
                st.sideObjectiveProgress = Math.min(st.sideObjectiveGoal, st.sectorElapsed);
                if (st.sideObjectiveProgress >= st.sideObjectiveGoal) {
                    markSideObjectiveCompleted(ctx, st);
                }
            }
            case CLEAR_BEFORE_TIME -> {
                st.sideObjectiveProgress = Math.min(st.sideObjectiveGoal, st.sectorElapsed);
                if (st.sectorElapsed > st.sideObjectiveGoal) {
                    markSideObjectiveFailed(ctx, st, "time_limit");
                }
            }
            case NONE -> {}
        }
    }

    private static void markSideObjectiveCompleted(GameContext ctx, CampaignState st) {
        if (st.sideObjectiveCompleted) return;
        st.sideObjectiveCompleted = true;
        EventSystem.showBanner(ctx, "SIDE OBJECTIVE COMPLETE +" + st.sideObjectiveRewardCredits + " CREDITS", 1.8);
        logTelemetry("side_complete",
                "sector=" + st.sector +
                        " type=" + st.sideObjectiveType +
                        " reward=" + st.sideObjectiveRewardCredits);
    }

    private static void markSideObjectiveFailed(GameContext ctx, CampaignState st, String reason) {
        if (st.sideObjectiveFailed) return;
        st.sideObjectiveFailed = true;
        logTelemetry("side_fail",
                "sector=" + st.sector +
                        " type=" + st.sideObjectiveType +
                        " reason=" + reason);
    }

    private static void updateBossPhases(GameContext ctx, CampaignState st, Ship boss) {
        if (boss == null) return;
        double hpFrac = (boss.hpMax <= 0) ? 0.0 : (boss.hp / (double) boss.hpMax);

        if (!st.bossPhaseOneTriggered) {
            double t1 = (st.bossKind == BossKind.FINAL) ? 0.75 : 0.70;
            if (hpFrac <= t1) {
                st.bossPhaseOneTriggered = true;
                triggerBossPhaseOne(ctx, st, boss);
            }
        }

        if (!st.bossPhaseTwoTriggered) {
            double t2 = (st.bossKind == BossKind.FINAL) ? 0.45 : 0.35;
            if (hpFrac <= t2) {
                st.bossPhaseTwoTriggered = true;
                triggerBossPhaseTwo(ctx, st, boss);
            }
        }
    }

    private static void triggerBossPhaseOne(GameContext ctx, CampaignState st, Ship boss) {
        for (Turret t : boss.turrets) {
            t.cooldown = Math.max(0.05, t.cooldown * 0.92);
        }
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, boss.x + 180, boss.y - 110);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, boss.x - 160, boss.y + 90);
        EventSystem.showBanner(ctx, boss.name + " PHASE 2", 2.0);
        logTelemetry("boss_phase", "sector=" + st.sector + " phase=1 boss=" + boss.name);
    }

    private static void triggerBossPhaseTwo(GameContext ctx, CampaignState st, Ship boss) {
        boss.shieldRegen *= 1.25;
        for (Turret t : boss.turrets) {
            t.damage = Math.max(1, (int) Math.round(t.damage * 1.15));
            t.cooldown = Math.max(0.05, t.cooldown * 0.90);
        }
        spawnEnemyAtPoint(ctx, ShipRole.BATTLECRUISER, boss.x + 230, boss.y + 120);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, boss.x - 210, boss.y - 100);
        EventSystem.showBanner(ctx, boss.name + " FINAL PHASE", 2.0);
        logTelemetry("boss_phase", "sector=" + st.sector + " phase=2 boss=" + boss.name);
    }

    private static void buildRouteChoices(GameContext ctx, CampaignState st, int mainSector) {
        if (st == null) return;
        st.routeChoices.clear();
        st.selectedRouteChoice = 0;
        st.routeArrivalSourceSector = st.sector;
        if (mainSector > st.totalSectors) {
            st.pendingEpisodeSector = 0;
            return;
        }

        SectorLore mainLore = loreFor(mainSector);
        st.routeChoices.add(new CampaignRouteChoice(
                CampaignRouteKind.MAIN,
                mainSector,
                "Main Route",
                "Continue to " + mainLore.location + ". " + mainLore.hudLead,
                0,
                0,
                0));

        int salvageSector = mainSector + 1;
        if (salvageSector <= st.totalSectors) {
            SectorLore lore = loreFor(salvageSector);
            int credits = GameContext.scaleCreditEarnings(120 + st.sector * 18);
            int ore = 45 + st.sector * 5;
            st.routeChoices.add(new CampaignRouteChoice(
                    CampaignRouteKind.SALVAGE,
                    salvageSector,
                    "Off-Path Salvage",
                    "Follow a sensor detour toward " + lore.location + ". Richer stores, longer road.",
                    credits,
                    ore,
                    1));
        }

        int strikeSector = mainSector + 2;
        if (strikeSector <= st.totalSectors && st.sector >= 3) {
            SectorLore lore = loreFor(strikeSector);
            int credits = GameContext.scaleCreditEarnings(210 + st.sector * 26);
            st.routeChoices.add(new CampaignRouteChoice(
                    CampaignRouteKind.DEEP_STRIKE,
                    strikeSector,
                    "Deep Strike",
                    "Jump past the lane into " + lore.location + ". Harder strategic tempo, stronger doctrine gain.",
                    credits,
                    0,
                    2));
        }

        applySelectedRouteChoice(ctx, st, true);
    }

    private static void applySelectedRouteChoice(GameContext ctx, CampaignState st, boolean quiet) {
        if (st == null || st.routeChoices.isEmpty()) return;
        int idx = MathUtil.clamp(st.selectedRouteChoice, 0, st.routeChoices.size() - 1);
        st.selectedRouteChoice = idx;
        CampaignRouteChoice choice = st.routeChoices.get(idx);
        st.pendingEpisodeSector = choice.targetSector;
        SectorLore lore = loreFor(choice.targetSector);
        st.transitionLabel = "EPISODE " + choice.targetSector + ": " + lore.title;
        if (st.awaitingEpisodeLaunch) {
            st.transitionSummaryBottom = routeChoiceSummary(st)
                    + "   |   1-3 select route   |   ENTER launches";
        } else if (st.awaitingFleetHubChoice) {
            st.transitionSummaryBottom = routeChoiceSummary(st)
                    + "   |   TAB: fleet hangar   |   Auto-opens in ~"
                    + ((int) Math.round(Math.max(0.0, st.fleetHubChoiceTimer))) + "s";
        }
        if (!quiet && ctx != null) {
            saveCheckpoint(ctx, st, st.pendingEpisodeSector);
        }
    }

    private static String routeChoiceSummary(CampaignState st) {
        if (st == null || st.routeChoices.isEmpty()) return "Route: main path";
        StringBuilder sb = new StringBuilder("Routes:");
        for (int i = 0; i < st.routeChoices.size(); i++) {
            CampaignRouteChoice choice = st.routeChoices.get(i);
            if (choice == null) continue;
            sb.append(' ');
            if (i == st.selectedRouteChoice) sb.append('[');
            sb.append(i + 1).append(' ').append(choice.title).append(" -> S").append(choice.targetSector);
            if (choice.creditBonus > 0 || choice.oreBonus > 0) {
                sb.append(" +");
                if (choice.creditBonus > 0) sb.append(choice.creditBonus).append('c');
                if (choice.creditBonus > 0 && choice.oreBonus > 0) sb.append('/');
                if (choice.oreBonus > 0) sb.append(choice.oreBonus).append(" ore");
            }
            if (i == st.selectedRouteChoice) sb.append(']');
        }
        return sb.toString();
    }

    private static void grantSelectedRouteReward(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.routeChoices.isEmpty()) return;
        CampaignRouteChoice choice = st.routeChoices.get(MathUtil.clamp(st.selectedRouteChoice, 0, st.routeChoices.size() - 1));
        if (choice == null || choice.kind == CampaignRouteKind.MAIN) return;
        if (choice.creditBonus > 0) ctx.credits += choice.creditBonus;
        if (choice.oreBonus > 0) grantCampaignOre(ctx, choice.oreBonus);
        st.branchScore += choice.branchScoreDelta;
        st.branchRoute = branchRouteLabel(st.branchScore);
        EventSystem.showBanner(ctx,
                "ROUTE COMMITTED: " + choice.title.toUpperCase(Locale.US)
                        + (choice.creditBonus > 0 ? "  +" + choice.creditBonus + "C" : "")
                        + (choice.oreBonus > 0 ? "  +" + choice.oreBonus + " ORE" : ""),
                1.8);
    }

    private static void onSectorComplete(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return;
        st.objectiveSecured = false;

        int bonusBase = (int) Math.round((250 + st.sector * 70) * st.sectorCreditBonusMul);
        int bonus = GameContext.scaleCreditEarnings(bonusBase);
        int sideBonus = resolveSideObjectiveBonusOnClear(ctx, st);
        ctx.credits += bonus;
        if (sideBonus > 0) ctx.credits += sideBonus;
        updateBranchProgress(st, sideBonus);
        persistSectorProgress(ctx, st.sector);
        String unlock = grantSectorUnlock(ctx);
        String storyReward = grantStoryFleetReward(ctx, st);
        String sectorOutcome = grantSectorOutcomeReward(ctx, st);
        advanceCoalitionMomentumOnSectorClear(st);
        String bossDrop = grantBossDrop(ctx);
        if (st.galaxyEncounterActive && st.activeGalaxyEncounterLocationId != null && !st.activeGalaxyEncounterLocationId.isBlank()) {
            finishGalaxyEncounterAndReturn(ctx, st, bonus, sideBonus, storyReward, sectorOutcome, bossDrop);
            return;
        }
        int nextSector = st.sector + 1;
        boolean hasNextEpisode = nextSector <= st.totalSectors;
        if (hasNextEpisode) {
            buildRouteChoices(ctx, st, nextSector);
            applySelectedRouteChoice(ctx, st, true);
            nextSector = Math.max(1, st.pendingEpisodeSector);
            hasNextEpisode = nextSector <= st.totalSectors;
        } else {
            st.routeChoices.clear();
            st.selectedRouteChoice = 0;
            st.routeArrivalSourceSector = 0;
            st.pendingEpisodeSector = 0;
        }
        boolean checkpointSaved = hasNextEpisode && saveCheckpoint(ctx, st, nextSector);
        if (!checkpointSaved && nextSector > st.totalSectors) {
            CampaignCheckpointStore.clear();
        }

        boolean actBreak = isActBreakAfter(st.sector);
        SectorLore clearedLore = loreFor(st.sector);
        SectorLore nextLore = loreFor(Math.min(st.totalSectors, Math.max(1, nextSector)));
        st.transitionTimer = 0.0;
        st.awaitingEpisodeLaunch = false;
        st.pendingEpisodeSector = hasNextEpisode ? nextSector : 0;
        st.transitionLabel = hasNextEpisode
                ? ("EPISODE " + nextSector + ": " + nextLore.title)
                : (actBreak
                ? ("ACT " + (st.act + 1) + ": " + actTitleFor(st.act + 1))
                : ("JUMP TO " + nextLore.title));
        st.transitionSummaryTop = clearedLore.title + " secure. " + clearedLore.completionLead;
        st.transitionSummaryBottom = routeChoiceSummary(st)
                + "   |   TAB: fleet hangar   |   Auto-opens in ~"
                + ((int) Math.round(FLEET_HUB_AUTO_OPEN_DELAY)) + "s";
        st.awaitingFleetHubChoice = hasNextEpisode;
        st.fleetHubChoiceTimer = hasNextEpisode ? FLEET_HUB_AUTO_OPEN_DELAY : 0.0;
        consolidateCampaignOreLedger(ctx, st, true);
        EventSystem.showBanner(ctx,
                clearedLore.title + " SECURE  +" + bonus + " CREDITS"
                        + (sideBonus > 0 ? "  +SIDE " + sideBonus : "")
                        + (storyReward.isBlank() ? "" : "  FLEET EXPANDED")
                        + (sectorOutcome.isBlank() ? "" : "  OUTCOME SECURED")
                        + (bossDrop.isBlank() ? "" : "  DROP ACQUIRED")
                        + "  DOCTRINE " + st.branchRoute
                        + (checkpointSaved ? "  CHECKPOINT SAVED" : "")
                        + (hasNextEpisode ? "  EPISODE READY" : "")
                        + (!hasNextEpisode && actBreak ? "  ACT BREAK" : ""),
                hasNextEpisode ? 3.2 : (actBreak ? 4.0 : 2.4));
        logTelemetry("sector_clear",
                "sector=" + st.sector +
                        " elapsedSec=" + Math.round(st.sectorElapsed) +
                        " objective=" + st.objectiveType +
                        " bonus=" + bonus +
                        " sideBonus=" + sideBonus +
                        " storyReward=" + (storyReward.isBlank() ? "none" : storyReward) +
                        " outcome=" + (sectorOutcome.isBlank() ? "none" : sectorOutcome) +
                        " drop=" + (bossDrop.isBlank() ? "none" : bossDrop) +
                        " checkpoint=" + checkpointSaved +
                        " route=" + st.branchRoute +
                        " branchScore=" + st.branchScore);

        if (!hasNextEpisode) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;
            finalizeCampaignOutcome(ctx, st);
            persistRunResult(ctx, true);
        }
    }

    private static void finishGalaxyEncounterAndReturn(GameContext ctx, CampaignState st,
                                                       int bonus, int sideBonus,
                                                       String storyReward, String sectorOutcome,
                                                       String bossDrop) {
        CampaignLocation location = campaignLocationById(st, st.activeGalaxyEncounterLocationId);
        markCampaignLocationCompleted(st, location);
        st.enemyAlertLevel = MathUtil.clamp(
                st.enemyAlertLevel + 6.0 + ((location == null) ? 0.0 : location.threatLevel * 12.0f),
                0.0,
                100.0);
        st.galaxyEncounterActive = false;
        st.activeGalaxyEncounterLocationId = "";
        st.awaitingEpisodeLaunch = false;
        st.pendingEpisodeSector = 0;
        st.awaitingFleetHubChoice = false;
        st.fleetHubChoiceTimer = 0.0;
        st.transitionTimer = 0.0;
        st.routeChoices.clear();
        st.selectedRouteChoice = 0;
        st.routeArrivalSourceSector = 0;
        st.strategicOvermapMode = true;
        consolidateCampaignOreLedger(ctx, st, true);

        String title = (location == null) ? loreFor(st.sector).title : location.name;
        EventSystem.showBanner(ctx,
                title.toUpperCase(Locale.US) + " SECURED"
                        + "  +" + bonus + " CREDITS"
                        + (sideBonus > 0 ? "  +SIDE " + sideBonus : "")
                        + (storyReward.isBlank() ? "" : "  FLEET EXPANDED")
                        + (sectorOutcome.isBlank() ? "" : "  OUTCOME SECURED")
                        + (bossDrop.isBlank() ? "" : "  DROP ACQUIRED")
                        + "  GALAXY MAP UPDATED",
                3.0);

        if (st.completedMainMissions >= st.galaxyMainPois.size()) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;
            finalizeCampaignOutcome(ctx, st);
            persistRunResult(ctx, true);
            return;
        }

        startSector(ctx, Math.max(1, st.sector));
    }

    private static void secureSectorObjective(GameContext ctx, String banner) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || st.objectiveSecured) return;
        st.objectiveSecured = true;
        st.objectiveProgress = Math.max(st.objectiveProgress, st.objectiveGoal);
        st.awaitingFleetHubChoice = false;
        st.fleetHubChoiceTimer = 0.0;
        st.transitionTimer = 0.0;
        st.transitionSummaryTop = "Objective secured. Sweep the field, mine what you can, and extract when you are ready.";
        double holdLeft = Math.max(0.0, Math.ceil(st.extractionMinHoldSeconds - st.sectorElapsed));
        if (holdLeft > 0.0) {
            st.transitionSummaryBottom = "Extraction unlocks in " + (int) holdLeft + "s   |   Safe Exit becomes available after the hold timer";
        } else {
            st.transitionSummaryBottom = "Objective secured   |   Press SAFE EXIT to return to menu   |   Then choose CONTINUE CAMPAIGN to reopen the fleet hangar";
        }
        EventSystem.showBanner(ctx, (banner == null || banner.isBlank()) ? "OBJECTIVE COMPLETE" : banner, 2.0);
        if (holdLeft <= 0.0) {
            AudioSystem.playContextBanter(ctx, "captain", "mission_extract_ready",
                    "BLUE COMMAND",
                    "Primary objective secure. Press Safe Exit when you are ready to return to command and refit the fleet.",
                    2.8, 10.0, 2);
        }
    }

    public static boolean completeMissionExtraction(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.enabled) return false;
        if (!canExtractFromCurrentSector(ctx)) return false;
        onSectorComplete(ctx);
        return true;
    }

    private static String grantSectorOutcomeReward(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return "";
        detectObjectiveAssetLosses(ctx);
        if (st.objectiveAssetTotal <= 0) return "";
        int survivors = Math.max(0, st.objectiveAssetTotal - st.objectiveAssetLosses);
        return switch (st.sector) {
            case 13 -> resolveSector13Outcome(ctx, st, survivors);
            case 21 -> resolveSector21Outcome(ctx, st, survivors);
            default -> "";
        };
    }

    private static String resolveSector13Outcome(GameContext ctx, CampaignState st, int survivors) {
        if (survivors >= st.objectiveAssetTotal) {
            int credits = GameContext.scaleCreditEarnings(140);
            ctx.credits += credits;
            st.greenContractFavor += 1;
            st.branchScore += 1;
            st.branchRoute = branchRouteLabel(st.branchScore);
            EventSystem.showBanner(ctx, "UPLINK GRID SAVED  +" + credits + "C  GREEN FAVOR +1", 2.4);
            return "UPLINK GRID SAVED +" + credits + "c +Green favor";
        }
        if (survivors > 0) {
            st.greenContractFavor += 1;
            EventSystem.showBanner(ctx, "PARTIAL ARRAY SALVAGE  GREEN FAVOR +1", 2.2);
            return "PARTIAL ARRAY SALVAGE +Green favor";
        }
        st.branchScore -= 1;
        st.branchRoute = branchRouteLabel(st.branchScore);
        EventSystem.showBanner(ctx, "CONTRACT ARRAY MAULED", 2.0);
        return "Contract array mauled";
    }

    private static String resolveSector21Outcome(GameContext ctx, CampaignState st, int survivors) {
        if (survivors >= st.objectiveAssetTotal) {
            int credits = GameContext.scaleCreditEarnings(180);
            ctx.credits += credits;
            st.yellowLiberationFavor += 1;
            st.branchScore += 1;
            st.branchRoute = branchRouteLabel(st.branchScore);
            EventSystem.showBanner(ctx, "EVACUATION LINE SECURED  +" + credits + "C  YELLOW FAVOR +1", 2.4);
            return "Evacuation line secured +" + credits + "c +Yellow favor";
        }
        if (survivors > 0) {
            st.yellowLiberationFavor += 1;
            EventSystem.showBanner(ctx, "EVAC SHIPS PARTIALLY SAVED  YELLOW FAVOR +1", 2.2);
            return "Partial evacuation success +Yellow favor";
        }
        st.yellowLiberationFavor = Math.max(0, st.yellowLiberationFavor - 1);
        st.branchScore -= 1;
        st.branchRoute = branchRouteLabel(st.branchScore);
        EventSystem.showBanner(ctx, "EVACUATION LINE SHATTERED", 2.0);
        return "Evacuation line shattered -Yellow favor";
    }

    private static String grantSectorUnlock(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.sector % 2 != 0 || ctx.player == null) return "";

        String unlock;
        int duplicateBonus = 0;
        switch (st.sector) {
            case 2 -> {
                if (!st.unlockAuxGunGranted) {
                    ctx.player.addGunTurret();
                    st.unlockAuxGunGranted = true;
                    unlock = "UNLOCK: AUX GUN MODULE";
                } else {
                    duplicateBonus = GameContext.scaleCreditEarnings(160);
                    unlock = "TECH CACHE: +" + duplicateBonus + " CREDITS";
                }
            }
            case 4 -> {
                if (st.unlockMissileTierGranted < 1) {
                    ctx.player.addMissileTurret();
                    st.unlockMissileTierGranted = 1;
                    unlock = "UNLOCK: MISSILE RACK";
                } else {
                    duplicateBonus = GameContext.scaleCreditEarnings(200);
                    unlock = "TECH CACHE: +" + duplicateBonus + " CREDITS";
                }
            }
            case 6 -> {
                if (!st.unlockCiwsGranted) {
                    ctx.player.hasCIWS = true;
                    ctx.player.ciwsRange = Math.max(ctx.player.ciwsRange, 260);
                    st.unlockCiwsGranted = true;
                    unlock = "UNLOCK: CIWS SUITE";
                } else {
                    duplicateBonus = GameContext.scaleCreditEarnings(220);
                    unlock = "TECH CACHE: +" + duplicateBonus + " CREDITS";
                }
            }
            case 8 -> {
                if (!st.unlockHullGranted) {
                    applyReinforcedHullPackage(ctx.player);
                    st.unlockHullGranted = true;
                    unlock = "UNLOCK: REINFORCED HULL PACKAGE";
                } else {
                    duplicateBonus = GameContext.scaleCreditEarnings(260);
                    unlock = "TECH CACHE: +" + duplicateBonus + " CREDITS";
                }
            }
            case 10 -> {
                if (st.unlockMissileTierGranted < 2) {
                    ctx.player.addMissileTurret();
                    st.unlockMissileTierGranted = 2;
                    unlock = "UNLOCK: HEAVY MISSILE PACKAGE";
                } else {
                    duplicateBonus = GameContext.scaleCreditEarnings(300);
                    unlock = "TECH CACHE: +" + duplicateBonus + " CREDITS";
                }
            }
            default -> unlock = "UNLOCK: DOCTRINE UPGRADE";
        }
        if (duplicateBonus > 0) ctx.credits += duplicateBonus;
        EventSystem.showBanner(ctx, unlock, 2.8);
        return unlock;
    }

    private static String grantStoryFleetReward(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return "";
        return switch (st.sector) {
            case 6 -> grantStoryResources(ctx, 220, 70, "WAKE CACHE RECOVERED");
            case 12 -> grantGreenContractPackage(ctx, st);
            case 20 -> grantYellowLiberationPackage(ctx, st);
            default -> "";
        };
    }

    private static String grantGreenContractPackage(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return "";
        st.greenContractFleetJoined = true;
        st.greenContractFavor += st.sideObjectiveCompleted ? 2 : 1;
        return grantStoryResources(ctx, 340, 110,
                "GREEN CONTRACT TIER " + greenContractTier(st) + " TASK GROUP JOINED");
    }

    private static String grantYellowLiberationPackage(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return "";
        st.yellowLiberationFleetJoined = true;
        st.campaignBlueYellowAlliance = true;
        st.yellowLiberationFavor += st.sideObjectiveCompleted ? 2 : 1;
        return grantStoryResources(ctx, 420, 140,
                "YELLOW LIBERATION TIER " + yellowLiberationTier(st) + " TASK GROUP JOINED");
    }

    private static void advanceCoalitionMomentumOnSectorClear(CampaignState st) {
        if (st == null) return;
        int momentumGain = st.sideObjectiveCompleted ? 2 : 1;
        if (st.greenContractFleetJoined && st.sector >= 13 && st.sector <= 18) {
            st.greenContractFavor += momentumGain;
        }
        if (st.yellowLiberationFleetJoined && st.sector >= 21 && st.sector <= 24) {
            st.yellowLiberationFavor += momentumGain;
        }
    }

    private static int greenContractTier(CampaignState st) {
        if (st == null || !st.greenContractFleetJoined) return 0;
        return MathUtil.clamp(1 + Math.max(0, st.greenContractFavor) / 2, 1, 3);
    }

    private static int yellowLiberationTier(CampaignState st) {
        if (st == null || !st.yellowLiberationFleetJoined) return 0;
        return MathUtil.clamp(1 + Math.max(0, st.yellowLiberationFavor) / 2, 1, 3);
    }

    private static String coalitionSupportHud(CampaignState st) {
        if (st == null) return "";
        int greenTier = greenContractTier(st);
        int yellowTier = yellowLiberationTier(st);
        if (greenTier <= 0 && yellowTier <= 0) return "";
        return "   COALITION G" + Math.max(0, greenTier) + " Y" + Math.max(0, yellowTier);
    }

    private static String coalitionSupportSummary(CampaignState st) {
        if (st == null) return "";
        int greenTier = greenContractTier(st);
        int yellowTier = yellowLiberationTier(st);
        if (greenTier <= 0 && yellowTier <= 0) return "";
        return "   |   COALITION G" + Math.max(0, greenTier) + "/Y" + Math.max(0, yellowTier);
    }

    private static String grantStoryResources(GameContext ctx, int credits, int ore, String label) {
        if (ctx == null || ctx.player == null) return "";
        int creditReward = GameContext.scaleCreditEarnings(Math.max(0, credits));
        int oreReward = Math.max(0, ore);
        ctx.credits += creditReward;
        grantCampaignOre(ctx, oreReward);
        String message = label + "  +" + creditReward + "C  +" + oreReward + " ORE";
        EventSystem.showBanner(ctx, message, 2.6);
        return label + " +" + creditReward + "c +" + oreReward + " ore";
    }

    private static String grantBossDrop(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || ctx == null || ctx.player == null) return "";
        if (st.objectiveType != ObjectiveType.BOSS && st.objectiveType != ObjectiveType.FINAL_BOSS) return "";

        return switch (st.sector) {
            case 7 -> grantBossDropAegisArray(ctx, st);
            case 16 -> grantBossDropMissileCore(ctx, st);
            case 24 -> grantBossDropFlagCore(ctx, st);
            default -> "";
        };
    }

    private static String grantBossDropAegisArray(GameContext ctx, CampaignState st) {
        if (st.bossDropAegisArray) return "";
        st.bossDropAegisArray = true;
        st.bossDropsCollected++;

        ctx.player.hpMax += 14;
        ctx.player.healHull(14);
        if (ctx.player.shieldActive) {
            ctx.player.shieldMax += 28.0;
            ctx.player.shield = Math.min(ctx.player.shieldMax, ctx.player.shield + 28.0);
            ctx.player.shieldRegen *= 1.14;
        }

        String drop = "Aegis Array (+hull/shield regen)";
        EventSystem.showBanner(ctx, "BOSS DROP: " + drop, 3.0);
        logTelemetry("boss_drop", "sector=" + st.sector + " drop=AegisArray");
        return drop;
    }

    private static String grantBossDropMissileCore(GameContext ctx, CampaignState st) {
        if (st.bossDropMissileCore) return "";
        st.bossDropMissileCore = true;
        st.bossDropsCollected++;

        int missileTurrets = 0;
        for (Turret t : ctx.player.turrets) {
            if (t == null || t.kind != Turret.Kind.MISSILE) continue;
            missileTurrets++;
            t.damage = Math.max(1, (int) Math.round(t.damage * 1.25));
            t.cooldown = Math.max(0.22, t.cooldown * 0.85);
            t.missileTurnRate *= 1.10;
            t.missileSpeed *= 1.08;
            t.missileLife = (int) Math.round(t.missileLife * 1.10);
        }
        if (missileTurrets == 0) {
            ctx.player.addMissileTurret();
        }

        String drop = "Missile Core (+missile alpha/tracking)";
        EventSystem.showBanner(ctx, "BOSS DROP: " + drop, 3.0);
        logTelemetry("boss_drop", "sector=" + st.sector + " drop=MissileCore turrets=" + Math.max(1, missileTurrets));
        return drop;
    }

    private static String grantBossDropFlagCore(GameContext ctx, CampaignState st) {
        if (st.bossDropFlagCore) return "";
        st.bossDropFlagCore = true;
        st.bossDropsCollected++;

        ctx.player.hpMax += 36;
        ctx.player.healHull(36);
        if (ctx.player.shieldActive) {
            ctx.player.shieldMax += 40.0;
            ctx.player.shield = Math.min(ctx.player.shieldMax, ctx.player.shield + 40.0);
        }
        if (ctx.player.hasCIWS) {
            ctx.player.upgradeCIWS();
        } else {
            ctx.player.hasCIWS = true;
            ctx.player.ciwsRange = Math.max(ctx.player.ciwsRange, 250.0);
            ctx.player.ciwsQuality = Math.max(ctx.player.ciwsQuality, 0.55);
            ctx.player.ciwsPelletsPerBurst = Math.max(ctx.player.ciwsPelletsPerBurst, 2);
            ctx.player.ciwsCooldown = Math.min(ctx.player.ciwsCooldown, 0.11);
        }

        String drop = "Flag Core (+core durability/CIWS)";
        EventSystem.showBanner(ctx, "BOSS DROP: " + drop, 3.4);
        logTelemetry("boss_drop", "sector=" + st.sector + " drop=FlagCore");
        return drop;
    }

    private static void detectHostileKills(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return;

        Set<Integer> aliveNow = new HashSet<>();
        for (Ship s : ctx.ships) {
            if (!isTrackableHostile(ctx, s)) continue;
            aliveNow.add(s.id);
        }

        for (Integer id : st.knownHostiles) {
            if (!aliveNow.contains(id)) {
                st.kills++;
                st.campaignKills++;
            }
        }

        if (!st.authoredObjectiveHostiles.isEmpty()) {
            int priorAuthoredKills = st.authoredObjectiveKills;
            for (Iterator<Integer> it = st.authoredObjectiveHostiles.iterator(); it.hasNext(); ) {
                Integer id = it.next();
                if (!aliveNow.contains(id)) {
                    st.authoredObjectiveKills++;
                    it.remove();
                }
            }
            if (st.authoredObjectiveKills > priorAuthoredKills) {
                announceAuthoredObjectiveKillProgress(ctx, st, st.authoredObjectiveKills - priorAuthoredKills);
            }
        }

        st.knownHostiles.clear();
        st.knownHostiles.addAll(aliveNow);
    }

    private static void detectObjectiveAssetLosses(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.objectiveAssetIds.isEmpty()) return;
        ArrayList<String> lostNames = new ArrayList<>();
        for (Iterator<Integer> it = st.objectiveAssetIds.iterator(); it.hasNext(); ) {
            Ship ship = findShipById(ctx, it.next());
            if (ship != null && ship.alive && !ship.dying && ship.hp > 0) continue;
            lostNames.add(displayShipName(ship, trimmedOrFallback(st.objectiveAssetLabel, "Objective Asset")));
            st.objectiveAssetLosses++;
            it.remove();
        }
        if (!lostNames.isEmpty()) {
            announceObjectiveAssetLosses(ctx, st, lostNames);
        }
    }

    private static void announceAuthoredObjectiveKillProgress(GameContext ctx, CampaignState st, int killsThisTick) {
        if (ctx == null || st == null || killsThisTick <= 0) return;
        if (!usesAuthoredDestroyProgress(st)) return;
        int total = Math.max(1, (int) Math.ceil(st.objectiveGoal));
        int killed = Math.max(0, st.authoredObjectiveKills);
        int remaining = Math.max(0, total - killed);
        String noun = (total == 1) ? "TARGET" : "TARGETS";
        if (remaining <= 0) {
            EventSystem.showBanner(ctx, "ALL MARKED TARGETS DESTROYED", 2.0);
            AudioSystem.playContextBanter(ctx, "captain", "objective_destroy_complete",
                    "CAPTAIN",
                    "Marked targets down. Push to the next objective.",
                    2.2, 7.5, 2);
        } else if (killsThisTick == 1) {
            EventSystem.showBanner(ctx,
                    "MARKED TARGET DESTROYED  " + remaining + " " + noun + " REMAIN",
                    1.8);
            AudioSystem.playContextBanter(ctx, "tactical", "objective_destroy_progress",
                    "TACTICAL",
                    remaining + " marked " + ((remaining == 1) ? "target remains." : "targets remain."),
                    2.0, 4.5, 2);
        } else {
            EventSystem.showBanner(ctx,
                    "MARKED TARGETS DESTROYED +" + killsThisTick + "  " + remaining + " " + noun + " REMAIN",
                    1.9);
            AudioSystem.playContextBanter(ctx, "tactical", "objective_destroy_progress",
                    "TACTICAL",
                    killsThisTick + " marked targets down. " + remaining + " remain.",
                    2.1, 4.5, 2);
        }
        st.lastAnnouncedAuthoredObjectiveKills = killed;
    }

    private static void announceObjectiveAssetLosses(GameContext ctx, CampaignState st, List<String> lostNames) {
        if (ctx == null || st == null || lostNames == null || lostNames.isEmpty()) return;
        int survivors = Math.max(0, st.objectiveAssetTotal - st.objectiveAssetLosses);
        String label = trimmedOrFallback(st.objectiveAssetLabel, "OBJECTIVE ASSETS").toUpperCase(Locale.US);
        String quota = (st.objectiveAssetRequiredSurvivors > 0)
                ? "  SAFE>=" + st.objectiveAssetRequiredSurvivors
                : "";
        String names = summarizeBannerNames(lostNames, 2);
        if (lostNames.size() == 1) {
            EventSystem.showBanner(ctx,
                    label + " LOST: " + names.toUpperCase(Locale.US)
                            + "  " + survivors + "/" + st.objectiveAssetTotal + quota,
                    2.0);
            AudioSystem.playContextBanter(ctx, "captain", "objective_asset_loss",
                    "CAPTAIN",
                    names + " is down. Keep the rest alive.",
                    2.3, 6.5, 3);
        } else {
            EventSystem.showBanner(ctx,
                    label + " LOSSES +" + lostNames.size()
                            + "  " + survivors + "/" + st.objectiveAssetTotal + quota,
                    2.0);
            AudioSystem.playContextBanter(ctx, "captain", "objective_asset_loss",
                    "CAPTAIN",
                    "We just lost objective assets. Hold the surviving hulls together.",
                    2.4, 6.5, 3);
        }
        if (st.objectiveAssetRequiredSurvivors > 0 && survivors == st.objectiveAssetRequiredSurvivors) {
            EventSystem.showBanner(ctx,
                    label + " AT MINIMUM SAFE COUNT  " + survivors + "/" + st.objectiveAssetTotal,
                    2.2);
            AudioSystem.playContextBanter(ctx, "helm", "objective_asset_min_safe",
                    "HELM",
                    "We are down to the minimum safe count. No more losses.",
                    2.3, 7.0, 3);
        }
        st.lastAnnouncedObjectiveAssetLosses = st.objectiveAssetLosses;
    }

    private static String summarizeBannerNames(List<String> names, int maxCount) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = Math.min(Math.max(1, maxCount), names.size());
        for (int i = 0; i < count; i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(name.trim());
        }
        if (names.size() > count) {
            if (sb.length() > 0) sb.append(" +");
            sb.append(names.size() - count).append(" MORE");
        }
        return sb.toString();
    }

    private static boolean hostileInsideCapture(GameContext ctx, double x, double y, double r) {
        double r2 = r * r;
        for (Ship s : ctx.ships) {
            if (!isTrackableHostile(ctx, s)) continue;
            if (GameMath.dist2(s.x, s.y, x, y) <= r2) return true;
        }
        return false;
    }

    private static boolean isTrackableHostile(GameContext ctx, Ship s) {
        if (s == null) return false;
        if (!s.alive || s.dying || s.hp <= 0) return false;
        if (s.carrierOwnerId >= 0) return false;
        if (s.role == ShipRole.BASE) return false;
        return TeamSystem.isHostileToPlayer(ctx, s.faction);
    }

    private static void snapshotHostiles(GameContext ctx, Set<Integer> out) {
        out.clear();
        for (Ship s : ctx.ships) {
            if (isTrackableHostile(ctx, s)) out.add(s.id);
        }
    }

    private static void pruneTransientUnits(GameContext ctx) {
        ctx.projectiles.clear();
        ctx.salvage.clear();
        ctx.lockedTarget = null;
        ctx.ships.removeIf(s -> s != null && s != ctx.player && s != ctx.enemyBase);
        for (Ship ship : ctx.ships) {
            if (ship != null) ship.resetWeaponCycleState();
        }
        ctx.allyBase = null;
        ctx.teamBases.clear();
        if (ctx.enemyBase != null) {
            ctx.teamBases.put(Faction.ENEMY, ctx.enemyBase);
        }
    }

    private static void regroupPlayerAtAlliedBase(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (isCampaignActive(ctx)) {
            ctx.player.vx = 0.0;
            ctx.player.vy = 0.0;
            return;
        }
        double[] spawn = SpawnSystem.playerRespawnPose(ctx);
        if (spawn == null || spawn.length < 3) return;
        ctx.player.respawnAt(spawn[0], spawn[1], spawn[2]);
    }

    private static void healAndRefitPlayer(GameContext ctx) {
        if (ctx.player == null) return;
        ctx.player.fullyRepairHull();
        if (ctx.player.shieldActive && ctx.player.shieldMax > 0) {
            ctx.player.shield = ctx.player.shieldMax;
        }
        ctx.player.resetWeaponCycleState();
    }

    private static void ensureCampaignTitanInfrastructure(GameContext ctx) {
        if (ctx == null || ctx.baseUpgrades == null) return;
        ensureCampaignHangarTier(ctx, ctx.allyBase);
        ensureCampaignHangarTier(ctx, ctx.enemyBase);
    }

    private static Ship captureSideObjectiveProtectedShip(GameContext ctx, CampaignState st) {
        if (st == null || st.sideObjectiveType != SideObjectiveType.NO_HULL_DAMAGE_WINDOW) return null;
        Ship target = sideObjectiveProtectedShip(ctx, st);
        if (target == null) {
            st.sideObjectiveProtectedShipId = -1;
            st.sideObjectiveProtectedShipStartHp = 0;
            return null;
        }
        if (st.sideObjectiveProtectedShipId != target.id) {
            st.sideObjectiveProtectedShipId = target.id;
            st.sideObjectiveProtectedShipStartHp = Math.max(0, target.hp);
        }
        return target;
    }

    private static Ship sideObjectiveProtectedShip(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return null;
        if (st.objectiveType == ObjectiveType.ESCORT
                && st.escortShip != null
                && st.escortShip.alive
                && !st.escortShip.dying
                && st.escortShip.hp > 0) {
            return st.escortShip;
        }
        return ctx.player;
    }

    private static void ensureCampaignHangarTier(GameContext ctx, Ship base) {
        if (ctx == null || base == null || base.role != ShipRole.BASE) return;
        BaseUpgrades upgrades = ctx.baseUpgrades.computeIfAbsent(base, ignored -> new BaseUpgrades().bindTo(base));
        upgrades.hangarLv = Math.max(upgrades.hangarLv, CAMPAIGN_ENEMY_MAX_HANGAR_TIER);
    }

    private static void ensureStartingTitanRoster(CampaignState st) {
        if (st == null || !st.ownedTitans.isEmpty()) return;
        st.ownedTitans.add(TitanArchetype.TRANSPORT);
        st.ownedTitans.add(TitanArchetype.BULWARK);
    }

    private static void applyPersistedUnlockProfile(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        CampaignUnlockProfile profile = ctx.campaignUnlockProfile;
        if (profile == null) return;

        if (profile.gunTier >= 1) {
            ctx.player.addGunTurret();
            st.unlockAuxGunGranted = true;
        }
        if (profile.missileTier >= 1) {
            ctx.player.addMissileTurret();
            st.unlockMissileTierGranted = Math.max(st.unlockMissileTierGranted, 1);
        }
        if (profile.ciwsUnlocked) {
            ctx.player.hasCIWS = true;
            ctx.player.ciwsRange = Math.max(ctx.player.ciwsRange, 260);
            st.unlockCiwsGranted = true;
        }
        if (profile.reinforcedHullUnlocked) {
            applyReinforcedHullPackage(ctx.player);
            st.unlockHullGranted = true;
        }
        if (profile.missileTier >= 2) {
            ctx.player.addMissileTurret();
            st.unlockMissileTierGranted = Math.max(st.unlockMissileTierGranted, 2);
        }

        logTelemetry("profile_apply",
                "bestSector=" + profile.bestSectorCleared +
                        " unlocks=" + profile.summary() +
                        " runs=" + profile.runsWon + "/" + profile.runsStarted);
    }

    private static void applyReinforcedHullPackage(Player player) {
        if (player == null) return;
        player.hpMax += 30;
        player.healHull(30);
        if (player.shieldActive) {
            player.shieldMax += 20;
            player.shield = Math.min(player.shieldMax, player.shield + 20);
        }
    }

    private static int resolveSideObjectiveBonusOnClear(GameContext ctx, CampaignState st) {
        if (st.sideObjectiveType == SideObjectiveType.NONE) return 0;
        if (!st.sideObjectiveCompleted && !st.sideObjectiveFailed) {
            if (st.sideObjectiveType == SideObjectiveType.CLEAR_BEFORE_TIME) {
                if (st.sectorElapsed <= st.sideObjectiveGoal) {
                    markSideObjectiveCompleted(ctx, st);
                } else {
                    markSideObjectiveFailed(ctx, st, "late_clear");
                }
            } else if (st.sideObjectiveType == SideObjectiveType.NO_HULL_DAMAGE_WINDOW) {
                Ship protectedShip = captureSideObjectiveProtectedShip(ctx, st);
                if (protectedShip == null
                        || !protectedShip.alive
                        || protectedShip.hp <= 0
                        || protectedShip.hp < st.sideObjectiveProtectedShipStartHp) {
                    markSideObjectiveFailed(ctx, st, "hull_damage");
                }
            }
        }
        return st.sideObjectiveCompleted ? st.sideObjectiveRewardCredits : 0;
    }

    private static String sideRewardSummary(CampaignState st, int sideBonus) {
        if (st == null || st.sideObjectiveType == SideObjectiveType.NONE) return "";
        if (sideBonus > 0) return "   |   SIDE +" + sideBonus + " credits";
        if (st.sideObjectiveFailed) return "   |   SIDE FAILED";
        return "";
    }

    private static void updateBranchProgress(CampaignState st, int sideBonus) {
        if (st == null) return;
        st.sectorsCleared++;

        boolean fastClear = st.sectorElapsed <= (st.sectorTimeLimit * 0.82);
        if (fastClear) st.branchScore += 1;

        if (sideBonus > 0) {
            st.sideObjectivesCompletedTotal++;
            st.branchScore += 2;
        } else if (st.sideObjectiveType != SideObjectiveType.NONE) {
            st.sideObjectivesFailedTotal++;
            st.branchScore -= 1;
        }

        st.branchRoute = branchRouteLabel(st.branchScore);
    }

    private static String branchRouteLabel(int score) {
        if (score >= 8) return "SPEARHEAD";
        if (score >= 4) return "DISCIPLINED";
        if (score <= -3) return "ATTRITION";
        return "BALANCED";
    }

    private static BranchOutcome determineBranchOutcome(GameContext ctx, CampaignState st) {
        double hpFrac = 0.0;
        if (ctx != null && ctx.player != null && ctx.player.hpMax > 0) {
            hpFrac = Math.max(0.0, Math.min(1.0, ctx.player.hp / (double) ctx.player.hpMax));
        }

        if (st.sideObjectivesCompletedTotal >= 8
                && st.sideObjectivesFailedTotal <= 2
                && st.branchScore >= 10
                && hpFrac >= 0.55) {
            return BranchOutcome.TRUE_RESTORATION;
        }
        if (st.sideObjectivesCompletedTotal >= 5 && st.branchScore >= 5) {
            return BranchOutcome.STRATEGIC_SUPREMACY;
        }
        if (hpFrac <= 0.25 || st.sideObjectivesCompletedTotal <= 2 || st.branchScore <= -2) {
            return BranchOutcome.PYRRHIC;
        }
        return BranchOutcome.STANDARD;
    }

    private static void finalizeCampaignOutcome(GameContext ctx, CampaignState st) {
        BranchOutcome ending = determineBranchOutcome(ctx, st);
        ctx.gameOverText = ending.gameOverText + " (" + st.branchRoute + ")";
        EventSystem.showBanner(ctx, ending.bannerText, 4.0);
        logTelemetry("campaign_end",
                "ending=" + ending.name() +
                        " route=" + st.branchRoute +
                        " sectors=" + st.sectorsCleared +
                        " sideOK=" + st.sideObjectivesCompletedTotal +
                        " sideFail=" + st.sideObjectivesFailedTotal +
                        " kills=" + st.campaignKills +
                        " branchScore=" + st.branchScore);
    }

    private static int actForSector(int sector) {
        if (sector <= 8) return 1;
        if (sector <= 16) return 2;
        return 3;
    }

    private static boolean isActBreakAfter(int sector) {
        return sector == 8 || sector == 16;
    }

    private static String formatProgress(double progress, double goal) {
        if (goal <= 0.0) return "0/0";
        if (goal >= 30.0) {
            int p = (int) Math.floor(progress);
            int g = (int) Math.floor(goal);
            return p + "/" + g;
        }
        return String.format(java.util.Locale.US, "%.0f/%.0f", progress, goal);
    }

    private static String sideObjectiveHud(CampaignState st) {
        if (st == null || st.sideObjectiveType == SideObjectiveType.NONE) return "";
        if (st.sideObjectiveCompleted) return "COMPLETE +" + st.sideObjectiveRewardCredits + "c";
        if (st.sideObjectiveFailed) return "FAILED";

        String progress = sideProgressLabel(st);
        if (st.sideObjectiveRewardCredits > 0) {
            return st.sideObjectiveLabel + " [" + progress + "] +" + st.sideObjectiveRewardCredits + "c";
        }
        return st.sideObjectiveLabel + " [" + progress + "]";
    }

    private static String bossDropHud(CampaignState st) {
        if (st == null || st.bossDropsCollected <= 0) return "";
        StringBuilder sb = new StringBuilder();
        if (st.bossDropAegisArray) sb.append("AEGIS");
        if (st.bossDropMissileCore) {
            if (sb.length() > 0) sb.append("+");
            sb.append("MISSILE");
        }
        if (st.bossDropFlagCore) {
            if (sb.length() > 0) sb.append("+");
            sb.append("FLAG");
        }
        return sb.toString();
    }

    private static String sideProgressLabel(CampaignState st) {
        return switch (st.sideObjectiveType) {
            case KILL_COUNT -> formatProgress(st.sideObjectiveProgress, st.sideObjectiveGoal);
            case NO_HULL_DAMAGE_WINDOW -> {
                int left = (int) Math.ceil(Math.max(0.0, st.sideObjectiveGoal - st.sideObjectiveProgress));
                yield "T-" + left + "s";
            }
            case CLEAR_BEFORE_TIME -> {
                int left = (int) Math.ceil(Math.max(0.0, st.sideObjectiveGoal - st.sectorElapsed));
                yield "T-" + left + "s";
            }
            case NONE -> "";
        };
    }

    private static String modifiersSummary(MapModifier[] modifiers) {
        if (modifiers == null || modifiers.length == 0) return MapModifier.NONE.label;
        StringBuilder sb = new StringBuilder();
        for (MapModifier m : modifiers) {
            if (m == null || m == MapModifier.NONE) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.label);
        }
        return (sb.length() == 0) ? MapModifier.NONE.label : sb.toString();
    }

    private static double escortFormationIntegrity(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null || st.escortShip == null) return 0.0;
        Ship escort = st.escortShip;
        if (!escort.alive || escort.dying || escort.hp <= 0) return 0.0;

        double playerDist = Math.hypot(ctx.player.x - escort.x, ctx.player.y - escort.y);
        double playerCover = 1.0 - MathUtil.clamp(
                (playerDist - ESCORT_PLAYER_FORMATION_RADIUS) / Math.max(180.0, ESCORT_PLAYER_FORMATION_RADIUS),
                0.0, 1.0);

        int supportCount = 0;
        int threatCount = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == escort) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            double dist = Math.hypot(ship.x - escort.x, ship.y - escort.y);
            if (ship.faction != null && ship.faction.isFriendlyTo(escort.faction)) {
                if (ship.role != ShipRole.BASE
                        && ship != ctx.player
                        && dist <= ESCORT_SUPPORT_RADIUS) {
                    supportCount++;
                }
            } else if (dist <= ESCORT_THREAT_RADIUS) {
                threatCount++;
            }
        }

        double supportScore = MathUtil.clamp(supportCount / 3.0, 0.0, 1.0);
        if (playerCover < 0.35) {
            supportScore *= 0.55;
        }
        double threatPenalty = Math.min(0.34, threatCount * 0.08);
        return MathUtil.clamp(playerCover * 0.74 + supportScore * 0.34 - threatPenalty, 0.0, 1.0);
    }

    private static Color tintFor(MapModifier m) {
        if (m == null) return null;
        return switch (m) {
            case NONE -> new Color(0, 0, 0, 0);
            case NEBULA -> new Color(90, 70, 120, 34);
            case DEBRIS_FIELD -> new Color(100, 90, 70, 28);
            case EMP_ZONE -> new Color(70, 120, 145, 34);
            case RESOURCE_DROUGHT -> new Color(125, 95, 55, 30);
            case RICH_DEPOSITS -> new Color(80, 120, 80, 26);
            case SOLAR_STORM -> new Color(165, 105, 55, 34);
            case GRAVITY_SHEAR -> new Color(100, 80, 125, 32);
            case SUPPLY_WINDFALL -> new Color(90, 130, 95, 24);
        };
    }

    private static String failureHint(ObjectiveType type) {
        return switch (type) {
            case ESCORT -> "FAIL: escort lost";
            case BOSS, FINAL_BOSS -> "FAIL: timeout / player death";
            default -> "FAIL: timeout";
        };
    }

    private static String threatReadout(float threatLevel) {
        float t = MathUtil.clamp(threatLevel, 0.0f, 1.0f);
        if (t < 0.18f) return "LOW";
        if (t < 0.34f) return "MODERATE";
        if (t < 0.52f) return "HIGH";
        if (t < 0.74f) return "SEVERE";
        return "OVERWHELMING";
    }

    private static boolean isMissionPocketObjectiveActive(CampaignState st) {
        return st == null || st.missionSections.isEmpty() || !st.missionSectionTravelLocked;
    }

    private static int resolvedObjectiveSectionIndex(CampaignState st) {
        if (st == null || st.missionSections.isEmpty()) return 0;
        int index = Math.max(0, Math.min(st.missionSections.size() - 1, st.activeMissionSection));
        if (st.missionSectionTravelLocked && index > 0) {
            index--;
        }
        return index;
    }

    private static Ship findShipById(GameContext ctx, int id) {
        if (id < 0) return null;
        for (Ship s : ctx.ships) {
            if (s != null && s.id == id) return s;
        }
        return null;
    }

    private static void logTelemetry(String event, String detail) {
        System.out.println("[campaign] " + event + " " + detail);
    }

    private static void failRun(GameContext ctx, String text) {
        failRun(ctx, text, text);
    }

    private static void failRun(GameContext ctx, String text, String bannerText) {
        CampaignState st = state(ctx);
        ctx.gameOver = true;
        ctx.state = GameState.GAME_OVER;
        ctx.gameOverText = text;
        EventSystem.showBanner(ctx, (bannerText == null || bannerText.isBlank()) ? text : bannerText, 3.0);
        CampaignCheckpointStore.clear();
        if (st != null) {
            setCampaignOre(ctx, st, 0);
        }
        persistRunResult(ctx, false);
        if (st != null) {
            logTelemetry("sector_fail",
                    "sector=" + st.sector +
                            " elapsedSec=" + Math.round(st.sectorElapsed) +
                            " objective=" + st.objectiveType +
                            " reason=" + text +
                            " banner=" + ((bannerText == null) ? "" : bannerText));
        }
    }

    private static boolean isEnemyBaseDestroyed(GameContext ctx) {
        Ship enemyBase = TeamSystem.getBaseForTeam(ctx, Faction.ENEMY);
        if (enemyBase == null) return true;
        return !enemyBase.alive || enemyBase.dying || enemyBase.hp <= 0;
    }

    private static boolean hasLiveEnemyBase(GameContext ctx) {
        Ship enemyBase = TeamSystem.getBaseForTeam(ctx, Faction.ENEMY);
        if (enemyBase == null) return false;
        return enemyBase.alive && !enemyBase.dying && enemyBase.hp > 0;
    }

    private static void persistRunStart(GameContext ctx) {
        CampaignUnlockProfile profile = (ctx == null) ? null : ctx.campaignUnlockProfile;
        if (profile == null) return;
        CampaignCheckpointStore.clear();
        profile.markRunStarted();
        CampaignUnlockProfile.save(profile);
    }

    private static void persistRunResult(GameContext ctx, boolean won) {
        CampaignUnlockProfile profile = (ctx == null) ? null : ctx.campaignUnlockProfile;
        if (profile == null) return;
        if (won) profile.markRunWon();
        CampaignUnlockProfile.save(profile);
    }

    private static void persistSectorProgress(GameContext ctx, int sector) {
        CampaignUnlockProfile profile = (ctx == null) ? null : ctx.campaignUnlockProfile;
        if (profile == null) return;
        if (profile.recordSectorClear(sector)) {
            CampaignUnlockProfile.save(profile);
            logTelemetry("profile_save",
                    "bestSector=" + profile.bestSectorCleared +
                            " unlocks=" + profile.summary());
        }
    }

    private static boolean saveCheckpoint(GameContext ctx, CampaignState st, int nextSector) {
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, st, nextSector);
        if (checkpoint == null) return false;
        CampaignCheckpointStore.save(checkpoint);
        return true;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, CampaignState st, int nextSector) {
        if (ctx == null || st == null || ctx.player == null) return null;
        consolidateCampaignOreLedger(ctx, st, true);

        CampaignCheckpointStore.Checkpoint cp = new CampaignCheckpointStore.Checkpoint();
        cp.worldW = (int) Math.round(clampedMissionSubzoneWidth(ctx.config));
        cp.worldH = (int) Math.round(clampedMissionSubzoneHeight(ctx.config));
        cp.randomEvents = (ctx.config == null) || ctx.config.randomEvents;
        cp.seed = checkpointSeed(ctx, nextSector);
        cp.nextSector = nextSector;
        cp.credits = ctx.credits;
        cp.sectorsCleared = st.sectorsCleared;
        cp.campaignKills = st.campaignKills;
        cp.branchScore = st.branchScore;
        cp.branchRoute = st.branchRoute;
        cp.sideObjectivesCompletedTotal = st.sideObjectivesCompletedTotal;
        cp.sideObjectivesFailedTotal = st.sideObjectivesFailedTotal;
        cp.unlockAuxGunGranted = st.unlockAuxGunGranted;
        cp.unlockMissileTierGranted = st.unlockMissileTierGranted;
        cp.unlockCiwsGranted = st.unlockCiwsGranted;
        cp.unlockHullGranted = st.unlockHullGranted;
        cp.bossDropAegisArray = st.bossDropAegisArray;
        cp.bossDropMissileCore = st.bossDropMissileCore;
        cp.bossDropFlagCore = st.bossDropFlagCore;
        cp.bossDropsCollected = st.bossDropsCollected;
        cp.ownedTitans = TitanFleetSystem.serializeOwnedTitans(st.ownedTitans);
        syncPersistentFleetEntrySnapshots(ctx, st);
        cp.persistentBlueFleet = serializePersistentBlueFleet(st.persistentBlueFleet);
        cp.escortCapUpgradeLevel = 0;
        cp.lineCapUpgradeLevel = 0;
        cp.capitalCapUpgradeLevel = 0;
        cp.campaignBlueYellowAlliance = st.campaignBlueYellowAlliance;
        cp.greenContractFleetJoined = st.greenContractFleetJoined;
        cp.yellowLiberationFleetJoined = st.yellowLiberationFleetJoined;
        cp.greenContractFavor = st.greenContractFavor;
        cp.yellowLiberationFavor = st.yellowLiberationFavor;
        cp.miningBaseMul = ctx.miningBaseMul;
        cp.orePriceBaseMul = ctx.orePriceBaseMul;

        Player player = ctx.player;
        cp.playerFactionName = (player.faction == null) ? Faction.PLAYER.name() : player.faction.name();
        cp.playerRoleName = (player.role == null) ? ShipRole.FRIGATE.name() : player.role.name();
        cp.primaryWeaponFamilyName = (player.primaryWeaponFamily == null)
                ? Ship.PrimaryWeaponFamily.ENERGY_BOLT.name()
                : player.primaryWeaponFamily.name();
        cp.hpMax = player.hpMax;
        cp.shieldMax = player.shieldMax;
        cp.shieldRegen = player.shieldRegen;
        cp.shieldActive = player.shieldActive;
        cp.campaignOre = st.oreLedger.storedOre;
        cp.cargo = player.cargo;
        cp.cargoMax = player.cargoMax;
        cp.miningRate = player.miningRate;
        cp.miningRange = player.miningRange;
        cp.hasCIWS = player.hasCIWS;
        cp.ciwsRange = player.ciwsRange;
        cp.ciwsCooldown = player.ciwsCooldown;
        cp.ciwsQuality = player.ciwsQuality;
        cp.ciwsPelletsPerBurst = player.ciwsPelletsPerBurst;
        cp.ciwsPelletSpeed = player.ciwsPelletSpeed;
        cp.ciwsPelletDamage = player.ciwsPelletDamage;
        cp.ciwsPelletLife = player.ciwsPelletLife;
        cp.ciwsPelletRadius = player.ciwsPelletRadius;
        cp.powerPresetName = (player.powerPreset == null) ? Ship.PowerPreset.BALANCED.name() : player.powerPreset.name();
        cp.crewOrderName = (player.crewOrder == null) ? Ship.CrewOrder.BALANCED.name() : player.crewOrder.name();
        cp.engineeringPriorityName = player.engineeringPriority().name();
        cp.overloadBusName = player.overloadBus().name();
        cp.powerBuses = serializePowerBuses(player.powerBusFractions());
        cp.turretData = serializeTurrets(player);
        cp.isCarrier = player.isCarrier;
        cp.maxFighters = player.maxFighters;
        cp.carrierCommandModeName = (player.carrierCommandMode == null)
                ? Ship.CarrierCommandMode.ATTACK.name()
                : player.carrierCommandMode.name();
        cp.carrierAutoLaunch = player.carrierAutoLaunch;
        cp.flightDeckLoadout = serializeFlightDeck(player);

        Ship anchor = ctx.player;
        copyBaseCheckpoint(anchor, ctx.baseUpgrades.get(anchor), true, cp);
        copyBaseCheckpoint(ctx.enemyBase, ctx.baseUpgrades.get(ctx.enemyBase), false, cp);
        cp.normalize();
        return cp;
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignState st, CampaignCheckpointStore.Checkpoint cp) {
        if (ctx == null || st == null || cp == null || ctx.player == null) return false;
        cp.normalize();

        ctx.credits = cp.credits;
        st.sector = cp.nextSector;
        st.act = actForSector(cp.nextSector);
        st.sectorsCleared = cp.sectorsCleared;
        st.campaignKills = cp.campaignKills;
        st.branchScore = cp.branchScore;
        st.branchRoute = cp.branchRoute;
        st.sideObjectivesCompletedTotal = cp.sideObjectivesCompletedTotal;
        st.sideObjectivesFailedTotal = cp.sideObjectivesFailedTotal;
        st.unlockAuxGunGranted = cp.unlockAuxGunGranted;
        st.unlockMissileTierGranted = cp.unlockMissileTierGranted;
        st.unlockCiwsGranted = cp.unlockCiwsGranted;
        st.unlockHullGranted = cp.unlockHullGranted;
        st.bossDropAegisArray = cp.bossDropAegisArray;
        st.bossDropMissileCore = cp.bossDropMissileCore;
        st.bossDropFlagCore = cp.bossDropFlagCore;
        st.bossDropsCollected = cp.bossDropsCollected;
        TitanFleetSystem.restoreOwnedTitans(st, cp.ownedTitans);
        st.campaignBlueYellowAlliance = cp.campaignBlueYellowAlliance;
        st.greenContractFleetJoined = cp.greenContractFleetJoined;
        st.yellowLiberationFleetJoined = cp.yellowLiberationFleetJoined;
        st.greenContractFavor = cp.greenContractFavor;
        st.yellowLiberationFavor = cp.yellowLiberationFavor;
        st.escortCapUpgradeLevel = 0;
        st.lineCapUpgradeLevel = 0;
        st.capitalCapUpgradeLevel = 0;
        restorePersistentBlueFleet(st, cp.persistentBlueFleet);
        ctx.miningBaseMul = Math.max(0.0, cp.miningBaseMul);
        ctx.orePriceBaseMul = Math.max(0.0, cp.orePriceBaseMul);
        setCampaignOre(ctx, st, Math.max(0, cp.campaignOre));

        restorePlayerFromCheckpoint(ctx.player, cp);
        syncCampaignOreToFlagship(ctx, st);
        restoreBaseCheckpoint(ctx.player, ctx.baseUpgrades.get(ctx.player),
                cp.allyOreStockpile, cp.allyHullLv, cp.allyShieldLv, cp.allyTurretLv, cp.allyMiningLv, cp.allyHangarLv);
        restoreBaseCheckpoint(ctx.enemyBase, ctx.baseUpgrades.get(ctx.enemyBase),
                cp.enemyOreStockpile, cp.enemyHullLv, cp.enemyShieldLv, cp.enemyTurretLv, cp.enemyMiningLv, cp.enemyHangarLv);
        return true;
    }

    private static void restorePlayerFromCheckpoint(Player player, CampaignCheckpointStore.Checkpoint cp) {
        if (player == null || cp == null) return;

        double px = player.x;
        double py = player.y;
        player.applyHull(parseEnum(cp.playerRoleName, ShipRole.FRIGATE), px, py);
        player.faction = parseEnum(cp.playerFactionName, Faction.PLAYER);
        player.name = "Player";
        player.hpMax = Math.max(1, cp.hpMax);
        player.hp = player.hpMax;
        player.shieldMax = Math.max(0.0, cp.shieldMax);
        player.shieldRegen = Math.max(0.0, cp.shieldRegen);
        player.shieldActive = cp.shieldActive;
        player.shield = player.shieldActive ? player.shieldMax : 0.0;
        player.resetShieldState();
        player.cargo = Math.max(0, cp.cargo);
        player.cargoMax = Math.max(0, cp.cargoMax);
        player.miningRate = Math.max(0.0, cp.miningRate);
        player.miningRange = Math.max(0.0, cp.miningRange);
        player.hasCIWS = cp.hasCIWS;
        player.ciwsRange = Math.max(0.0, cp.ciwsRange);
        player.ciwsCooldown = Math.max(0.02, cp.ciwsCooldown);
        player.ciwsQuality = MathUtil.clamp(cp.ciwsQuality, 0.0, 1.0);
        player.ciwsPelletsPerBurst = Math.max(1, cp.ciwsPelletsPerBurst);
        player.ciwsPelletSpeed = Math.max(0.0, cp.ciwsPelletSpeed);
        player.ciwsPelletDamage = Math.max(1, cp.ciwsPelletDamage);
        player.ciwsPelletLife = Math.max(1, cp.ciwsPelletLife);
        player.ciwsPelletRadius = Math.max(0.1, cp.ciwsPelletRadius);
        player.powerPreset = parseEnum(cp.powerPresetName, Ship.PowerPreset.BALANCED);
        double[] buses = parsePowerBuses(cp.powerBuses);
        player.setPowerBusAllocation(buses[0], buses[1], buses[2], buses[3], buses[4], buses[5]);
        player.crewOrder = parseEnum(cp.crewOrderName, Ship.CrewOrder.BALANCED);
        player.setEngineeringPriority(parseEnum(cp.engineeringPriorityName, Ship.EngineeringPriority.BALANCED));
        player.setOverloadBus(parseEnum(cp.overloadBusName, Ship.PowerBus.TACTICAL));
        player.setOverloadMode(false);
        restoreTurrets(player, cp.turretData);
        player.resyncShopUpgradeTrackers();
        player.primaryWeaponFamily = parseEnum(cp.primaryWeaponFamilyName, Ship.PrimaryWeaponFamily.ENERGY_BOLT);
        player.applyPrimaryWeaponFamily();
        player.isCarrier = cp.isCarrier;
        player.maxFighters = Math.max(0, cp.maxFighters);
        player.carrierCommandMode = parseEnum(cp.carrierCommandModeName, Ship.CarrierCommandMode.ATTACK);
        player.carrierAutoLaunch = cp.carrierAutoLaunch;
        restoreFlightDeck(player, cp.flightDeckLoadout);
        player.vx = 0.0;
        player.vy = 0.0;
    }

    private static void copyBaseCheckpoint(Ship base, BaseUpgrades upgrades, boolean ally, CampaignCheckpointStore.Checkpoint cp) {
        if (cp == null) return;
        int ore = (base == null) ? 0 : Math.max(0, base.oreStockpile);
        int hullLv = (upgrades == null) ? 0 : upgrades.hullLv;
        int shieldLv = (upgrades == null) ? 0 : upgrades.shieldLv;
        int turretLv = (upgrades == null) ? 0 : upgrades.turretLv;
        int miningLv = (upgrades == null) ? 0 : upgrades.miningLv;
        int hangarLv = (upgrades == null) ? 0 : upgrades.hangarLv;
        if (ally) {
            cp.allyOreStockpile = ore;
            cp.allyHullLv = hullLv;
            cp.allyShieldLv = shieldLv;
            cp.allyTurretLv = turretLv;
            cp.allyMiningLv = miningLv;
            cp.allyHangarLv = hangarLv;
        } else {
            cp.enemyOreStockpile = ore;
            cp.enemyHullLv = hullLv;
            cp.enemyShieldLv = shieldLv;
            cp.enemyTurretLv = turretLv;
            cp.enemyMiningLv = miningLv;
            cp.enemyHangarLv = hangarLv;
        }
    }

    private static void restoreBaseCheckpoint(Ship base, BaseUpgrades upgrades, int oreStockpile,
                                              int hullLv, int shieldLv, int turretLv, int miningLv, int hangarLv) {
        if (base != null) {
            base.oreStockpile = Math.max(0, oreStockpile);
        }
        if (upgrades != null) {
            upgrades.bindTo(base);
            int maxHangarLv = (base != null && base.role == ShipRole.MOTHERSHIP)
                    ? CAMPAIGN_PLAYER_MAX_HANGAR_TIER
                    : CAMPAIGN_ENEMY_MAX_HANGAR_TIER;
            upgrades.hullLv = MathUtil.clamp(hullLv, 0, 5);
            upgrades.shieldLv = MathUtil.clamp(shieldLv, 0, 5);
            upgrades.turretLv = MathUtil.clamp(turretLv, 0, 5);
            upgrades.miningLv = MathUtil.clamp(miningLv, 0, 5);
            upgrades.hangarLv = MathUtil.clamp(hangarLv, 0, maxHangarLv);
        }
    }

    private static String serializePowerBuses(double[] buses) {
        double[] values = (buses == null || buses.length < 6)
                ? new double[]{0.18, 0.18, 0.19, 0.15, 0.18, 0.12}
                : buses;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.6f", values[i]));
        }
        return sb.toString();
    }

    private static double[] parsePowerBuses(String raw) {
        double[] out = new double[]{0.18, 0.18, 0.19, 0.15, 0.18, 0.12};
        if (raw == null || raw.isBlank()) return out;
        String[] parts = raw.split(",");
        for (int i = 0; i < out.length && i < parts.length; i++) {
            try {
                out[i] = Math.max(0.0, Double.parseDouble(parts[i].trim()));
            } catch (Exception ignored) {
                // Keep fallback value.
            }
        }
        return out;
    }

    private static String serializeTurrets(Player player) {
        return serializeTurrets((Ship) player);
    }

    private static String serializeTurrets(Ship ship) {
        if (ship == null || ship.turrets.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Turret turret : ship.turrets) {
            if (turret == null) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(turret.kind.name()).append('|')
                    .append(String.format(Locale.US, "%.4f", turret.localX)).append('|')
                    .append(String.format(Locale.US, "%.4f", turret.localY)).append('|')
                    .append(String.format(Locale.US, "%.6f", turret.turnRate)).append('|')
                    .append(String.format(Locale.US, "%.6f", turret.cooldown)).append('|')
                    .append(turret.damage).append('|')
                    .append(String.format(Locale.US, "%.4f", turret.bulletSpeed)).append('|')
                    .append(turret.bulletLife).append('|')
                    .append(String.format(Locale.US, "%.4f", turret.missileSpeed)).append('|')
                    .append(String.format(Locale.US, "%.6f", turret.missileTurnRate)).append('|')
                    .append(turret.missileLife).append('|')
                    .append(String.format(Locale.US, "%.4f", turret.radius)).append('|')
                    .append(String.format(Locale.US, "%.4f", turret.barrelLen)).append('|')
                    .append(turret.primary).append('|')
                    .append((turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM.name() : turret.missileRole.name());
        }
        return sb.toString();
    }

    private static void restoreTurrets(Player player, String raw) {
        restoreTurrets((Ship) player, raw);
        if (player != null) {
            player.resyncShopUpgradeTrackers();
        }
    }

    private static void restoreTurrets(Ship ship, String raw) {
        if (ship == null || raw == null || raw.isBlank()) return;
        ship.turrets.clear();
        String[] entries = raw.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split("\\|");
            if (parts.length < 14) continue;
            try {
                Turret turret = new Turret(
                        parseEnum(parts[0], Turret.Kind.GUN),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]));
                turret.turnRate = Double.parseDouble(parts[3]);
                turret.cooldown = Double.parseDouble(parts[4]);
                turret.damage = Integer.parseInt(parts[5]);
                turret.bulletSpeed = Double.parseDouble(parts[6]);
                turret.bulletLife = Integer.parseInt(parts[7]);
                turret.missileSpeed = Double.parseDouble(parts[8]);
                turret.missileTurnRate = Double.parseDouble(parts[9]);
                turret.missileLife = Integer.parseInt(parts[10]);
                turret.radius = Double.parseDouble(parts[11]);
                turret.barrelLen = Double.parseDouble(parts[12]);
                turret.primary = Boolean.parseBoolean(parts[13]);
                if (parts.length >= 15) {
                    turret.missileRole = parseEnum(parts[14], Turret.MissileRole.ANTI_MEDIUM);
                } else {
                    turret.missileRole = Turret.MissileRole.ANTI_MEDIUM;
                }
                ship.addTurret(turret);
            } catch (Exception ignored) {
                // Skip malformed turret entries and keep the rest.
            }
        }
    }

    private static String serializeFlightDeck(Player player) {
        if (player == null || player.flightDeckLoadout == null || player.flightDeckLoadout.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < player.flightDeckLoadout.length; i++) {
            if (i > 0) sb.append(',');
            ShipRole role = player.flightDeckLoadout[i];
            sb.append(role == null ? "" : role.name());
        }
        return sb.toString();
    }

    private static void restoreFlightDeck(Player player, String raw) {
        if (player == null || player.flightDeckLoadout == null) return;
        for (int i = 0; i < player.flightDeckLoadout.length; i++) {
            player.flightDeckLoadout[i] = null;
        }
        if (raw == null || raw.isBlank()) return;
        String[] parts = raw.split(",");
        for (int i = 0; i < player.flightDeckLoadout.length && i < parts.length; i++) {
            String name = parts[i].trim();
            if (name.isEmpty()) continue;
            player.flightDeckLoadout[i] = parseEnum(name, ShipRole.FIGHTER);
        }
    }

    private static long checkpointSeed(GameContext ctx, int nextSector) {
        long baseSeed = (ctx != null && ctx.config != null) ? ctx.config.seed : 0L;
        long sectorMix = 0x9E3779B97F4A7C15L * Math.max(1L, nextSector);
        long branchMix = 0xC2B2AE3D27D4EB4FL * Math.max(0L, (ctx != null && ctx.campaign != null) ? ctx.campaign.branchScore + 7L : 7L);
        return baseSeed ^ sectorMix ^ branchMix;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(String name, E fallback) {
        if (fallback == null) return null;
        if (name != null) {
            try {
                return Enum.valueOf(fallback.getDeclaringClass(), name.trim());
            } catch (Exception ignored) {
                // Fall back below.
            }
        }
        return fallback;
    }

    private static void addPersistentFleetEntry(CampaignState st, ShipRole role, String name) {
        addPersistentFleetEntry(st, role, name, CAMPAIGN_FLAGSHIP_COMMAND_GROUP);
    }

    private static PersistentFleetEntry addPersistentFleetEntry(CampaignState st, ShipRole role, String name, int commandGroupId) {
        if (st == null || role == null) return null;
        PersistentFleetEntry entry = new PersistentFleetEntry(st.nextPersistentFleetSlotId++, role, name);
        entry.commandGroupId = Math.max(CAMPAIGN_FLAGSHIP_COMMAND_GROUP, commandGroupId);
        st.persistentBlueFleet.add(entry);
        return entry;
    }

    private static boolean isTitanPersistentEntry(PersistentFleetEntry entry) {
        return entry != null && TitanArchetype.fromShipRole(entry.role) != null;
    }

    private static int persistentCommandGroupCapacity(PersistentFleetEntry entry) {
        TitanArchetype titan = (entry == null) ? null : TitanArchetype.fromShipRole(entry.role);
        if (titan != null) return Math.max(4, titan.totalCommandHullCapacity());
        if (entry == null || entry.role == null) return 1;
        if (entry.role.isCarrierHull() || entry.role == ShipRole.BATTLECRUISER || entry.role == ShipRole.BATTLESHIP) return 3;
        if (entry.role == ShipRole.CRUISER || entry.role == ShipRole.MEDIUM_CRUISER
                || entry.role == ShipRole.LIGHT_CRUISER || entry.role == ShipRole.STEALTH_SHIP) return 2;
        return 1;
    }

    private static void rebalancePersistentCommandGroups(CampaignState st) {
        if (st == null) return;

        java.util.Map<Integer, Integer> groupCap = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> groupLoad = new java.util.HashMap<>();
        java.util.Set<Integer> titanGroups = new java.util.HashSet<>();
        java.util.List<PersistentFleetEntry> standards = new ArrayList<>();

        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (isTitanPersistentEntry(entry)) {
                entry.commandGroupId = entry.slotId;
                groupCap.put(entry.slotId, persistentCommandGroupCapacity(entry));
                groupLoad.put(entry.slotId, 0);
                titanGroups.add(entry.slotId);
            } else if (entry.commandGroupId == entry.slotId) {
                groupCap.put(entry.slotId, persistentCommandGroupCapacity(entry));
                groupLoad.put(entry.slotId, 0);
            } else {
                standards.add(entry);
            }
        }

        if (groupCap.isEmpty()) {
            for (PersistentFleetEntry entry : standards) {
                entry.commandGroupId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
            }
            return;
        }

        java.util.List<PersistentFleetEntry> unassigned = new ArrayList<>();
        for (PersistentFleetEntry entry : standards) {
            int currentGroup = entry.commandGroupId;
            Integer load = groupLoad.get(currentGroup);
            Integer cap = groupCap.get(currentGroup);
            if (currentGroup != CAMPAIGN_FLAGSHIP_COMMAND_GROUP
                    && load != null
                    && cap != null
                    && load < cap) {
                groupLoad.put(currentGroup, load + 1);
            } else {
                entry.commandGroupId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
                unassigned.add(entry);
            }
        }

        for (PersistentFleetEntry entry : unassigned) {
            int groupId = bestPersistentCommandGroup(groupLoad, groupCap, titanGroups);
            if (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) continue;
            entry.commandGroupId = groupId;
            groupLoad.put(groupId, groupLoad.getOrDefault(groupId, 0) + 1);
        }
    }

    private static int bestPersistentCommandGroup(java.util.Map<Integer, Integer> groupLoad,
                                                  java.util.Map<Integer, Integer> groupCap,
                                                  java.util.Set<Integer> eligibleGroups) {
        int bestId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
        double bestScore = Double.POSITIVE_INFINITY;
        for (java.util.Map.Entry<Integer, Integer> capEntry : groupCap.entrySet()) {
            int groupId = capEntry.getKey();
            if (eligibleGroups != null && !eligibleGroups.contains(groupId)) continue;
            int cap = Math.max(1, capEntry.getValue());
            int load = groupLoad.getOrDefault(groupId, 0);
            if (load >= cap) continue;
            double fill = load / (double) cap;
            double score = fill - (cap - load) * 0.001;
            if (score < bestScore) {
                bestScore = score;
                bestId = groupId;
            }
        }
        return bestId;
    }

    private static int livePersistentFleetSlots(CampaignState st) {
        if (st == null) return 0;
        int count = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry != null && !entry.destroyed) count++;
        }
        return count;
    }

    private static int livePersistentFleetSlots(CampaignState st, ShopHullCategory category) {
        if (st == null) return 0;
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        int count = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (ShopHullCategory.forRole(entry.role) == resolved) count++;
        }
        return count;
    }

    private static Ship spawnPersistentBlueShipFromFlagship(GameContext ctx, CampaignState st, PersistentFleetEntry entry,
                                                            int liveIndex, boolean titanAnchor) {
        if (ctx == null || st == null || entry == null || ctx.player == null || entry.destroyed) return null;
        double sx;
        double sy;
        if (titanAnchor) {
            double side = ((liveIndex & 1) == 0) ? -1.0 : 1.0;
            double row = liveIndex / 2.0;
            double aft = 300.0 + row * 150.0;
            double lateral = side * (250.0 + row * 44.0);
            sx = ctx.player.x - Math.cos(ctx.player.angle) * aft - Math.sin(ctx.player.angle) * lateral;
            sy = ctx.player.y - Math.sin(ctx.player.angle) * aft + Math.cos(ctx.player.angle) * lateral;
        } else {
            double lane = liveIndex % 3;
            double row = liveIndex / 3.0;
            double side = lane - 1.0;
            double aft = 180.0 + row * 92.0;
            double lateral = side * (130.0 + row * 12.0);
            sx = ctx.player.x - Math.cos(ctx.player.angle) * aft - Math.sin(ctx.player.angle) * lateral;
            sy = ctx.player.y - Math.sin(ctx.player.angle) * aft + Math.cos(ctx.player.angle) * lateral;
        }
        return spawnPersistentBlueShipAtPose(ctx, entry, sx, sy, ctx.player.angle, ctx.player.vx, ctx.player.vy);
    }

    private static Ship spawnPersistentBlueShipFromAnchor(GameContext ctx, CampaignState st, PersistentFleetEntry entry,
                                                          Ship anchor, int memberIndex) {
        if (ctx == null || st == null || entry == null || anchor == null || entry.destroyed) return null;
        double lane = memberIndex % 3;
        double row = memberIndex / 3.0;
        double side = lane - 1.0;
        double aft = Math.max(160.0, anchor.radius + 90.0) + row * 84.0;
        double lateral = side * (140.0 + row * 14.0);
        double sx = anchor.x - Math.cos(anchor.angle) * aft - Math.sin(anchor.angle) * lateral;
        double sy = anchor.y - Math.sin(anchor.angle) * aft + Math.cos(anchor.angle) * lateral;
        return spawnPersistentBlueShipAtPose(ctx, entry, sx, sy, anchor.angle, anchor.vx, anchor.vy);
    }

    private static Ship spawnPersistentBlueShipAtPose(GameContext ctx, PersistentFleetEntry entry,
                                                      double sx, double sy, double angle, double vx, double vy) {
        if (ctx == null || entry == null) return null;
        sx = GameMath.clamp(sx, 40.0, ctx.WORLD_W - 40.0);
        sy = GameMath.clamp(sy, 40.0, ctx.WORLD_H - 40.0);

        Ship ship = new FleetShip(entry.role, Faction.ALLY, sx, sy);
        ship.name = entry.name;
        ship.angle = angle;
        ship.vx = vx;
        ship.vy = vy;
        ship.minerHomeBase = ctx.player;
        ctx.ships.add(ship);
        BaseUpgrades upgrades = ctx.baseUpgrades.computeIfAbsent(ship, ignored -> new BaseUpgrades().bindTo(ship));
        upgrades.hullLv = MathUtil.clamp(entry.hullLv, 0, 5);
        upgrades.shieldLv = MathUtil.clamp(entry.shieldLv, 0, 5);
        upgrades.turretLv = MathUtil.clamp(entry.turretLv, 0, 5);
        upgrades.miningLv = MathUtil.clamp(entry.miningLv, 0, 5);
        upgrades.hangarLv = MathUtil.clamp(entry.hangarLv, 0, 5);
        restoreTurrets(ship, entry.turretData);
        ship.primaryWeaponFamily = parseEnum(entry.primaryWeaponFamilyName, Ship.PrimaryWeaponFamily.ENERGY_BOLT);
        ship.applyPrimaryWeaponFamily();
        try { DoctrineRegistry.applyToShip(ship); } catch (Throwable ignored) {}
        applyPersistentShipUpgradeLevels(ctx, ship, upgrades);
        applyPersistentCampaignShipBonuses(state(ctx), entry, ship);
        ship.cargoMax = Math.max(ship.cargoMax, entry.cargoMax);
        ship.cargo = Math.min(ship.cargoMax, Math.max(0, entry.cargo));
        ship.fullyRepairHull();
        ship.resetShieldState();
        if (ship.shieldActive && ship.shieldMax > 0.0) {
            ship.shield = ship.shieldMax;
        }
        entry.activeShipId = ship.id;
        return ship;
    }

    static void warpPersistentFleetMinersWithPlayer(GameContext ctx, int arrivedCampaignSubzone) {
        if (ctx == null || ctx.player == null || !isCampaignActive(ctx)) return;
        int slot = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == ctx.player) continue;
            if (ship.role != ShipRole.MINER) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || ctx.player.faction == null || ship.faction.teamId() != ctx.player.faction.teamId()) {
                continue;
            }
            if (ship.minerHomeBase != ctx.player) continue;

            double side = ((slot & 1) == 0) ? -1.0 : 1.0;
            double row = slot / 2.0;
            double trail = Math.max(180.0, ctx.player.radius + 130.0) + row * 56.0;
            double lateral = side * (140.0 + row * 22.0);
            double px = ctx.player.x - Math.cos(ctx.player.angle) * trail - Math.sin(ctx.player.angle) * lateral;
            double py = ctx.player.y - Math.sin(ctx.player.angle) * trail + Math.cos(ctx.player.angle) * lateral;
            if (arrivedCampaignSubzone >= 0) {
                double[] clamped = clampToMissionSubzone(ctx, state(ctx).sector, arrivedCampaignSubzone, px, py);
                if (clamped != null && clamped.length >= 2) {
                    px = clamped[0];
                    py = clamped[1];
                }
                ship.campaignMissionSubzone = arrivedCampaignSubzone;
                ship.campaignWarpSourceSubzone = -1;
            }
            ship.x = px;
            ship.y = py;
            ship.angle = ctx.player.angle;
            ship.vx = ctx.player.vx;
            ship.vy = ctx.player.vy;
            ship.minerTarget = null;
            ship.minerState = Ship.MinerState.SEEK_ASTEROID;
            slot++;
        }
    }

    private static void applyPersistentShipUpgradeLevels(GameContext ctx, Ship ship, BaseUpgrades upgrades) {
        if (ctx == null || ship == null || upgrades == null) return;
        applyCampaignShipUpgrades(ctx, ship, upgrades);
    }

    private static void applyCampaignFleetBonuses(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        if (hasOperationalRole(st, ShipRole.TRANSPORT_TITAN)) {
            ctx.player.cargoMax = Math.max(ctx.player.cargoMax, CAMPAIGN_TRANSPORT_FLEET_ORE_CAPACITY);
        }
        syncCampaignOreToFlagship(ctx, st);
    }

    private static void queueEliteReinforcementPackage(CampaignState st, PersistentFleetEntry titanEntry,
                                                       List<PersistentFleetEntry> out) {
        if (st == null || titanEntry == null || out == null) return;
        int groupId = titanEntry.slotId;
        PersistentFleetEntry battleship = addPersistentFleetEntry(
                st,
                ShipRole.BATTLESHIP,
                "Blue Honor Battleship " + groupId,
                groupId);
        PersistentFleetEntry battlecruiser = addPersistentFleetEntry(
                st,
                ShipRole.BATTLECRUISER,
                "Blue Honor Battlecruiser " + groupId,
                groupId);
        PersistentFleetEntry frigate = addPersistentFleetEntry(
                st,
                ShipRole.FRIGATE,
                "Blue Honor Frigate " + groupId,
                groupId);
        PersistentFleetEntry screen = addPersistentFleetEntry(
                st,
                ShipRole.CIWS_CORVETTE,
                "Blue Honor Screen " + groupId,
                groupId);
        if (battleship != null) out.add(battleship);
        if (battlecruiser != null) out.add(battlecruiser);
        if (frigate != null) out.add(frigate);
        if (screen != null) out.add(screen);
    }

    private static void applyPersistentCampaignShipBonuses(CampaignState st, PersistentFleetEntry entry, Ship ship) {
        if (st == null || entry == null || ship == null) return;
        if (entry.role == null || entry.role.isTitanOrMothership()) return;
        if (entry.commandGroupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) return;
        PersistentFleetEntry anchor = persistentFleetEntryBySlotId(st, entry.commandGroupId);
        if (anchor == null || anchor.role == null) return;
        if (anchor.role == ShipRole.ELITE_REINFORCEMENTS_TITAN) {
            applyEliteReinforcementVeterancy(ship);
        }
    }

    private static void applyEliteReinforcementVeterancy(Ship ship) {
        if (ship == null || ship.role == null) return;
        if (ship.role.isTitanOrMothership() || ship.isSmallCraft()) return;
        if (ship.role == ShipRole.TRANSPORT || ship.role == ShipRole.MINER || ship.role == ShipRole.HAULER) return;

        boolean capital = ship.role == ShipRole.BATTLECRUISER
                || ship.role == ShipRole.BATTLESHIP
                || ship.role == ShipRole.DREADNOUGHT
                || ship.role == ShipRole.SUPERSHIP
                || ship.role.isCarrierHull();
        double hullMultiplier = capital ? 1.18 : 1.10;
        double shieldMultiplier = capital ? 1.18 : 1.10;
        double speedMultiplier = capital ? 1.08 : 1.05;
        double shieldRegenMultiplier = capital ? 1.14 : 1.10;
        double turretCooldownMultiplier = capital ? 0.92 : 0.95;
        int turretDamageBonus = capital ? 1 : 0;

        ship.hpMax = Math.max(1, (int) Math.round(ship.hpMax * hullMultiplier));
        ship.shieldMax = Math.max(0.0, ship.shieldMax * shieldMultiplier);
        ship.shieldRegen *= shieldRegenMultiplier;
        ship.desiredSpeed *= speedMultiplier;
        ship.desiredSpeedBase = Math.max(0.0, ship.desiredSpeed);
        if (ship.hasCIWS) {
            ship.ciwsRange += capital ? 34.0 : 24.0;
            ship.ciwsCooldown = Math.max(0.05, ship.ciwsCooldown * (capital ? 0.92 : 0.95));
            ship.ciwsQuality = Math.min(1.0, ship.ciwsQuality + (capital ? 0.10 : 0.06));
        }
        for (Turret turret : ship.turrets) {
            if (turret == null) continue;
            if (turret.kind == Turret.Kind.GUN) {
                turret.cooldown = Math.max(0.08, turret.cooldown * turretCooldownMultiplier);
                turret.damage = Math.max(1, turret.damage + turretDamageBonus);
                turret.bulletSpeed *= 1.04;
                turret.bulletLife += capital ? 24 : 12;
            } else if (turret.kind == Turret.Kind.MISSILE) {
                turret.cooldown = Math.max(0.45, turret.cooldown * turretCooldownMultiplier);
                turret.damage = Math.max(1, turret.damage + turretDamageBonus);
                turret.missileSpeed *= 1.05;
                turret.missileTurnRate *= 1.04;
                turret.missileLife += capital ? 28 : 14;
            }
        }
        ship.rebuildDefenseStateForCurrentStats();
        ship.fullyRepairHull();
        ship.resetShieldState();
        if (ship.shieldActive && ship.shieldMax > 0.0) {
            ship.shield = ship.shieldMax;
        }
    }

    private static PersistentFleetEntry persistentFleetEntryBySlotId(CampaignState st, int slotId) {
        if (st == null || slotId <= 0) return null;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry != null && entry.slotId == slotId) {
                return entry;
            }
        }
        return null;
    }

    private static Ship findPersistentCommandAnchor(GameContext ctx, CampaignState st, int commandGroupId) {
        if (ctx == null || st == null || commandGroupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) return null;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || !isTitanPersistentEntry(entry)) continue;
            if (entry.commandGroupId != commandGroupId) continue;
            Ship live = findShipById(ctx, entry.activeShipId);
            if (live != null && live.alive && !live.dying && live.hp > 0) {
                return live;
            }
        }
        return null;
    }

    private static void spawnIntroRedDetachment(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null) return;
        double x = Double.isFinite(st.introWarpX) ? st.introWarpX : ctx.player.x + 820.0;
        double y = Double.isFinite(st.introWarpY) ? st.introWarpY : ctx.player.y - 80.0;
        Explosion.spawnDestabilizerPulse(x, y, 220.0);
        Explosion.spawnDestabilizerPulse(x + 90.0, y - 60.0, 160.0);
        Explosion.spawnDestabilizerPulse(x - 110.0, y + 80.0, 160.0);
        spawnCampaignShip(ctx, ShipRole.VANGUARD_TITAN, Faction.ENEMY, x, y, "Red Knife Advance Titan");
        spawnCampaignShip(ctx, ShipRole.FRIGATE, Faction.ENEMY, x + 120.0, y - 120.0, "Red Strike Frigate");
        spawnCampaignShip(ctx, ShipRole.MISSILE_BOAT, Faction.ENEMY, x + 160.0, y + 20.0, "Red Strike Missile Boat");
        spawnCampaignShip(ctx, ShipRole.PICKET, Faction.ENEMY, x + 70.0, y + 130.0, "Red Pursuit Picket");
        snapshotHostiles(ctx, st.knownHostiles);
        EventSystem.showBanner(ctx, "RED DETACHMENT ATTACKING", 2.2);
    }

    private static String generatedBlueFleetName(ShipRole role, int slotId) {
        String title = (role == null) ? "Hull" : role.name().replace('_', ' ');
        return "Blue " + title + " " + Math.max(1, slotId);
    }

    private static String serializePersistentBlueFleet(List<PersistentFleetEntry> fleet) {
        if (fleet == null || fleet.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (PersistentFleetEntry entry : fleet) {
            if (entry == null || entry.role == null) continue;
            if (sb.length() > 0) sb.append(';');
            String encodedName = encoder.encodeToString(((entry.name == null) ? "" : entry.name).getBytes(StandardCharsets.UTF_8));
            String encodedTurrets = encoder.encodeToString(((entry.turretData == null) ? "" : entry.turretData).getBytes(StandardCharsets.UTF_8));
            sb.append(entry.slotId).append(',')
                    .append(entry.role.name()).append(',')
                    .append(entry.destroyed).append(',')
                    .append(encodedName).append(',')
                    .append(entry.commandGroupId).append(',')
                    .append(MathUtil.clamp(entry.hullLv, 0, 5)).append(',')
                    .append(MathUtil.clamp(entry.shieldLv, 0, 5)).append(',')
                    .append(MathUtil.clamp(entry.turretLv, 0, 5)).append(',')
                    .append(MathUtil.clamp(entry.miningLv, 0, 5)).append(',')
                    .append(MathUtil.clamp(entry.hangarLv, 0, 5)).append(',')
                    .append(Math.max(0, entry.cargo)).append(',')
                    .append(Math.max(0, entry.cargoMax)).append(',')
                    .append(encodedTurrets).append(',')
                    .append((entry.primaryWeaponFamilyName == null || entry.primaryWeaponFamilyName.isBlank())
                            ? Ship.PrimaryWeaponFamily.ENERGY_BOLT.name()
                            : entry.primaryWeaponFamilyName.trim()).append(',')
                    .append(Double.isFinite(entry.relX) ? entry.relX : "nan").append(',')
                    .append(Double.isFinite(entry.relY) ? entry.relY : "nan").append(',')
                    .append(Double.isFinite(entry.relAngle) ? entry.relAngle : "nan");
        }
        return sb.toString();
    }

    private static void restorePersistentBlueFleet(CampaignState st, String raw) {
        if (st == null) return;
        st.persistentBlueFleet.clear();
        st.nextPersistentFleetSlotId = 1;
        if (raw == null || raw.isBlank()) return;
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (String entryRaw : raw.split(";")) {
            if (entryRaw == null || entryRaw.isBlank()) continue;
            String[] parts = entryRaw.split(",", 17);
            if (parts.length < 4) continue;
            try {
                int slotId = Math.max(1, parseInt(parts[0], 1));
                ShipRole role = parseEnum(parts[1], ShipRole.FRIGATE);
                boolean destroyed = Boolean.parseBoolean(parts[2].trim());
                String name = new String(decoder.decode(parts[3].trim()), StandardCharsets.UTF_8);
                PersistentFleetEntry entry = new PersistentFleetEntry(slotId, role, name);
                entry.destroyed = destroyed;
                entry.commandGroupId = (parts.length >= 5)
                        ? Math.max(CAMPAIGN_FLAGSHIP_COMMAND_GROUP, parseInt(parts[4], CAMPAIGN_FLAGSHIP_COMMAND_GROUP))
                        : CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
                entry.hullLv = (parts.length >= 6) ? MathUtil.clamp(parseInt(parts[5], 0), 0, 5) : 0;
                entry.shieldLv = (parts.length >= 7) ? MathUtil.clamp(parseInt(parts[6], 0), 0, 5) : 0;
                entry.turretLv = (parts.length >= 8) ? MathUtil.clamp(parseInt(parts[7], 0), 0, 5) : 0;
                entry.miningLv = (parts.length >= 9) ? MathUtil.clamp(parseInt(parts[8], 0), 0, 5) : 0;
                entry.hangarLv = (parts.length >= 10) ? MathUtil.clamp(parseInt(parts[9], 0), 0, 5) : 0;
                entry.cargo = (parts.length >= 11) ? Math.max(0, parseInt(parts[10], 0)) : 0;
                entry.cargoMax = (parts.length >= 12) ? Math.max(0, parseInt(parts[11], 0)) : 0;
                if (parts.length >= 13 && parts[12] != null && !parts[12].isBlank()) {
                    entry.turretData = new String(decoder.decode(parts[12].trim()), StandardCharsets.UTF_8);
                }
                if (parts.length >= 14 && parts[13] != null && !parts[13].isBlank()) {
                    entry.primaryWeaponFamilyName = parts[13].trim();
                }
                if (parts.length >= 15) entry.relX = parseDouble(parts[14], Double.NaN);
                if (parts.length >= 16) entry.relY = parseDouble(parts[15], Double.NaN);
                if (parts.length >= 17) entry.relAngle = parseDouble(parts[16], Double.NaN);
                st.persistentBlueFleet.add(entry);
                st.nextPersistentFleetSlotId = Math.max(st.nextPersistentFleetSlotId, slotId + 1);
            } catch (Exception ignored) {
                // Skip malformed checkpoint fleet entries.
            }
        }
        rebalancePersistentCommandGroups(st);
    }

}
