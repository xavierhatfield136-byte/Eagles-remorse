import app.config.GameMode;
import app.config.GameConfig;
import app.persistence.CampaignCheckpointStore;
import app.persistence.CampaignSaveContract;
import app.persistence.CampaignUnlockProfile;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;


class CampaignSystemModels {
    CampaignSystemModels() {}
    enum BossKind {
        NONE,
        MID_ALPHA,
        MID_BETA,
        FINAL
    }

    enum MapModifier {
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

    public static final class TacticalEnvironmentRule {
        public final String identity;
        public final String backgroundPolicy;
        public final double sensorMultiplier;
        public final double movementMultiplier;
        public final double weaponRangeMultiplier;
        public final String hazard;
        public final String aiBehavior;
        public final String counterplay;

        TacticalEnvironmentRule(String identity,
                                String backgroundPolicy,
                                double sensorMultiplier,
                                double movementMultiplier,
                                double weaponRangeMultiplier,
                                String hazard,
                                String aiBehavior,
                                String counterplay) {
            this.identity = trimmedOrFallback(identity, "Clear Space");
            this.backgroundPolicy = trimmedOrFallback(backgroundPolicy, "Deep-space starfield");
            this.sensorMultiplier = MathUtil.clamp(sensorMultiplier, 0.2, 1.5);
            this.movementMultiplier = MathUtil.clamp(movementMultiplier, 0.2, 1.5);
            this.weaponRangeMultiplier = MathUtil.clamp(weaponRangeMultiplier, 0.2, 1.5);
            this.hazard = trimmedOrFallback(hazard, "None");
            this.aiBehavior = trimmedOrFallback(aiBehavior, "Standard navigation");
            this.counterplay = trimmedOrFallback(counterplay, "No special counterplay required");
        }
    }

    // Mission zone layout constants
    static final int MISSION_ZONE_COLUMNS = 6;
    static final int MISSION_ZONE_ROWS = 3;
    static final double MISSION_SUBZONE_CLAMP_MARGIN = 180.0;
    // Mission subzones are kept for pacing/metadata, but physical boundary walls are disabled.
    static final boolean MISSION_SUBZONE_BOUNDARY_CONSTRAINTS = false;
    static final double DEFAULT_MISSION_SUBZONE_WIDTH = 5000.0;
    static final double DEFAULT_MISSION_SUBZONE_HEIGHT = 5000.0;
    static final double MAX_MISSION_SUBZONE_WIDTH = 5000.0;
    static final double MAX_MISSION_SUBZONE_HEIGHT = 5000.0;
    static final int ZONES_PER_ROW = 8;
    static final double AMBIENT_SITE_POCKET_WIDTH = 12000.0;
    static final double AMBIENT_SITE_POCKET_HEIGHT = 9000.0;
    static final double AMBIENT_SITE_POCKET_RADIUS = 2800.0;

    static final class MissionLayout {
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

    enum TacticalApproachDirection {
        WEST,
        EAST,
        NORTH,
        SOUTH,
        NORTHWEST,
        NORTHEAST,
        SOUTHWEST,
        SOUTHEAST
    }

    static double clampedMissionSubzoneWidth(GameConfig config) {
        double raw = (config == null) ? DEFAULT_MISSION_SUBZONE_WIDTH : Math.max(1.0, config.worldW);
        return MathUtil.clamp(raw, 600.0, MAX_MISSION_SUBZONE_WIDTH);
    }

    static double clampedMissionSubzoneHeight(GameConfig config) {
        double raw = (config == null) ? DEFAULT_MISSION_SUBZONE_HEIGHT : Math.max(1.0, config.worldH);
        return MathUtil.clamp(raw, 600.0, MAX_MISSION_SUBZONE_HEIGHT);
    }

    static final class SectorScript {
        final int sector;
        final CampaignSystem.ObjectiveType objectiveType;
        final String objectiveLabel;
        final double objectiveGoal;
        final double timeLimitSec;
        final BossKind bossKind;
        final MapModifier[] modifiers;

        SectorScript(int sector, CampaignSystem.ObjectiveType objectiveType, String objectiveLabel, double objectiveGoal, double timeLimitSec, BossKind bossKind, MapModifier... modifiers) {
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

    static final class SideObjectiveScript {
        final int sector;
        final CampaignSystem.SideObjectiveType type;
        final String label;
        final double goal;
        final int rewardCredits;

        SideObjectiveScript(int sector, CampaignSystem.SideObjectiveType type, String label, double goal, int rewardCredits) {
            this.sector = sector;
            this.type = type;
            this.label = label;
            this.goal = goal;
            this.rewardCredits = rewardCredits;
        }
    }

    static final class SectorLore {
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

    static final class MissionSection {
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

    enum MissionTheme {
        BREAKTHROUGH,
        SALVAGE_RUN,
        RELAY_DEFENSE,
        MINE_CORRIDOR,
        PRISON_BREAK,
        ANOMALY_STORM
    }

    enum DiscoveryKind {
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

    static final class DiscoverySite {
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

    static final class RecoverableWreckSite {
        final String label;
        final String subtitle;
        final ShipRole role;
        final double x;
        final double y;
        final double radius;
        boolean claimed;
        double salvageValue;
        double lastThreatWarnAtSec = -1000.0;
        double underFireProgressSec = 0.0;

        RecoverableWreckSite(String label, String subtitle, ShipRole role, double x, double y, double radius) {
            this.label = (label == null || label.isBlank()) ? "Recoverable Wreck" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.x = x;
            this.y = y;
            this.radius = Math.max(120.0, radius);
            this.salvageValue = Math.max(35.0, this.radius * 0.36);
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
        INTEL,
        FORCE_BASE_DEFENSE,
        FORCE_PATROL,
        FORCE_CONVOY,
        FORCE_MINING,
        FORCE_SEARCH,
        FORCE_STRIKE
    }

    public enum CampaignForceContactState {
        KNOWN,
        SUSPECTED,
        STALE
    }

    public enum CampaignIntelPrecision {
        UNKNOWN,
        STRATEGIC_ONLY,
        APPROXIMATE,
        EXACT
    }

    public enum CampaignIntelObservationSource {
        PLAYER_SENSOR,
        ALLIED_REPORT,
        SITE_RADAR,
        MISSION_INTEL,
        OPERATION_INTEL
    }

    public static final class CampaignIntelObservation {
        public final CampaignIntelObservationSource source;
        public final CampaignIntelPrecision precision;
        public final long observedTick;
        public final long validUntilTick;
        public final double confidence;
        public final double knownX;
        public final double knownY;
        public final double uncertaintyRadius;

        CampaignIntelObservation(CampaignIntelObservationSource source,
                                  CampaignIntelPrecision precision,
                                  long observedTick,
                                  long validUntilTick,
                                  double confidence,
                                  double knownX,
                                  double knownY,
                                  double uncertaintyRadius) {
            this.source = source == null ? CampaignIntelObservationSource.MISSION_INTEL : source;
            this.precision = precision == null ? CampaignIntelPrecision.UNKNOWN : precision;
            this.observedTick = Math.max(0L, observedTick);
            this.validUntilTick = Math.max(this.observedTick, validUntilTick);
            this.confidence = MathUtil.clamp(confidence, 0.0, 1.0);
            this.knownX = knownX;
            this.knownY = knownY;
            this.uncertaintyRadius = Math.max(0.0, uncertaintyRadius);
        }

        boolean validAt(long tick) {
            return tick >= observedTick && tick <= validUntilTick && precision != CampaignIntelPrecision.UNKNOWN;
        }
    }

    public static final class CampaignFleetIntelRecord {
        public final int forceId;
        public final java.util.EnumMap<CampaignIntelObservationSource, CampaignIntelObservation> observations =
                new java.util.EnumMap<>(CampaignIntelObservationSource.class);

        CampaignFleetIntelRecord(int forceId) {
            this.forceId = Math.max(1, forceId);
        }
    }

    public static final class CampaignOperationIntelRecord {
        public final String operationId;
        public final java.util.EnumMap<CampaignIntelObservationSource, CampaignIntelObservation> observations =
                new java.util.EnumMap<>(CampaignIntelObservationSource.class);

        CampaignOperationIntelRecord(String operationId) {
            this.operationId = operationId == null ? "" : operationId.trim();
        }
    }

    public static final class CampaignIntelResolution {
        public final CampaignIntelObservationSource source;
        public final CampaignIntelPrecision precision;
        public final long observedTick;
        public final long validUntilTick;
        public final double confidence;
        public final double knownX;
        public final double knownY;
        public final double uncertaintyRadius;

        CampaignIntelResolution(CampaignIntelObservation observation) {
            this.source = observation.source;
            this.precision = observation.precision;
            this.observedTick = observation.observedTick;
            this.validUntilTick = observation.validUntilTick;
            this.confidence = observation.confidence;
            this.knownX = observation.knownX;
            this.knownY = observation.knownY;
            this.uncertaintyRadius = observation.uncertaintyRadius;
        }

        public boolean exactPosition() {
            return precision == CampaignIntelPrecision.EXACT
                    && Double.isFinite(knownX) && Double.isFinite(knownY);
        }
    }

    public static final class CampaignFleetInspectorView {
        public final int forceId;
        public final String displayName;
        public final String faction;
        public final String mission;
        public final String destination;
        public final String operationId;
        public final CampaignIntelPrecision precision;
        public final CampaignIntelObservationSource source;
        public final long ageTicks;
        public final double confidence;
        public final double knownX;
        public final double knownY;
        public final double uncertaintyRadius;
        public final boolean liveActionsAllowed;

        CampaignFleetInspectorView(int forceId, String displayName, String faction, String mission,
                                   String destination, String operationId, CampaignIntelResolution intel,
                                   long currentTick) {
            this.forceId = forceId;
            this.displayName = displayName;
            this.faction = faction;
            this.mission = mission;
            this.destination = destination;
            this.operationId = operationId;
            this.precision = intel.precision;
            this.source = intel.source;
            this.ageTicks = Math.max(0L, currentTick - intel.observedTick);
            this.confidence = intel.confidence;
            this.knownX = intel.knownX;
            this.knownY = intel.knownY;
            this.uncertaintyRadius = intel.uncertaintyRadius;
            this.liveActionsAllowed = intel.exactPosition();
        }
    }

    public static final class CampaignOperationInspectorView {
        public final String operationId;
        public final String faction;
        public final String rallySite;
        public final String targetSite;
        public final String phase;
        public final double musterProgress;
        public final double travelProgress;
        public final int knownFleetCount;
        public final long ageTicks;
        public final double confidence;

        CampaignOperationInspectorView(String operationId, String faction, String rallySite,
                                       String targetSite, String phase, double musterProgress,
                                       double travelProgress, int knownFleetCount, long ageTicks,
                                       double confidence) {
            this.operationId = operationId;
            this.faction = faction;
            this.rallySite = rallySite;
            this.targetSite = targetSite;
            this.phase = phase;
            this.musterProgress = musterProgress;
            this.travelProgress = travelProgress;
            this.knownFleetCount = knownFleetCount;
            this.ageTicks = ageTicks;
            this.confidence = confidence;
        }
    }

    public enum CampaignContactCertainty {
        CONFIRMED_REAL,
        UNIDENTIFIED_REAL,
        LAST_KNOWN,
        PREDICTED,
        LOST,
        STALE
    }

    static final double STRATEGIC_DETECTION_RANGE_MUL = 1.5;
    static final double CAMPAIGN_TACTICAL_SPAWN_GRACE_SEC = 10.0;
    static final double CAMPAIGN_TACTICAL_SPAWN_SAFE_RADIUS = 900.0;

    public static final class CampaignObjectiveMarker {
        public final ObjectiveMarkerType type;
        public final String label;
        public final String subtitle;
        public final Faction faction;
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
            this(type, label, subtitle, null, x, y, radius, priority);
        }

        CampaignObjectiveMarker(ObjectiveMarkerType type,
                                String label,
                                String subtitle,
                                Faction faction,
                                double x,
                                double y,
                                double radius,
                                int priority) {
            this.type = (type == null) ? ObjectiveMarkerType.PRIMARY_OBJECTIVE : type;
            this.label = (label == null || label.isBlank()) ? "OBJECTIVE" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.faction = faction;
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
        public final Faction faction;
        public final double x;
        public final double y;
        public final double radius;
        public final int priority;
        public final boolean interactive;
        public final int sourceForceId;

        CampaignSupportMarker(SupportMarkerType type,
                              String label,
                              String subtitle,
                              double x,
                              double y,
                              double radius,
                              int priority) {
            this(type, label, subtitle, null, x, y, radius, priority, true, 0);
        }

        CampaignSupportMarker(SupportMarkerType type,
                              String label,
                              String subtitle,
                              Faction faction,
                              double x,
                              double y,
                              double radius,
                              int priority) {
            this(type, label, subtitle, faction, x, y, radius, priority, true, 0);
        }

        CampaignSupportMarker(SupportMarkerType type,
                              String label,
                              String subtitle,
                              Faction faction,
                              double x,
                              double y,
                              double radius,
                              int priority,
                              boolean interactive) {
            this(type, label, subtitle, faction, x, y, radius, priority, interactive, 0);
        }

        CampaignSupportMarker(SupportMarkerType type,
                              String label,
                              String subtitle,
                              Faction faction,
                              double x,
                              double y,
                              double radius,
                              int priority,
                              boolean interactive,
                              int sourceForceId) {
            this.type = (type == null) ? SupportMarkerType.ANOMALY : type;
            this.label = (label == null || label.isBlank()) ? "SUPPORT CONTACT" : label.trim();
            this.subtitle = (subtitle == null) ? "" : subtitle.trim();
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.radius = Math.max(60.0, radius);
            this.priority = Math.max(0, priority);
            this.interactive = interactive;
            this.sourceForceId = Math.max(0, sourceForceId);
        }
    }

    public static final class TacticalMissionBriefing {
        public final String title;
        public final String primaryObjective;
        public final String successCondition;
        public final String failureCondition;
        public final List<String> protectedAssets;
        public final String requiredQuota;
        public final String timer;
        public final String optionalObjective;
        public final String optionalReward;
        public final String enemyStrength;
        public final String intelligenceCaveat;
        public final String recommendedFirstAction;

        TacticalMissionBriefing(String title,
                                String primaryObjective,
                                String successCondition,
                                String failureCondition,
                                List<String> protectedAssets,
                                String requiredQuota,
                                String timer,
                                String optionalObjective,
                                String optionalReward,
                                String enemyStrength,
                                String intelligenceCaveat,
                                String recommendedFirstAction) {
            this.title = trimmedOrFallback(title, "TACTICAL MISSION BRIEFING");
            this.primaryObjective = trimmedOrFallback(primaryObjective, "Advance the campaign objective");
            this.successCondition = trimmedOrFallback(successCondition, "Complete the primary objective");
            this.failureCondition = trimmedOrFallback(failureCondition, "Flagship loss ends the operation");
            this.protectedAssets = protectedAssets == null || protectedAssets.isEmpty()
                    ? List.of("Blue command flagship")
                    : List.copyOf(protectedAssets);
            this.requiredQuota = trimmedOrFallback(requiredQuota, "No additional quota");
            this.timer = trimmedOrFallback(timer, "No mission timer");
            this.optionalObjective = trimmedOrFallback(optionalObjective, "None");
            this.optionalReward = trimmedOrFallback(optionalReward, "None");
            this.enemyStrength = trimmedOrFallback(enemyStrength, "No confirmed hostile count");
            this.intelligenceCaveat = trimmedOrFallback(intelligenceCaveat, "Unidentified contacts may remain");
            this.recommendedFirstAction = trimmedOrFallback(recommendedFirstAction, "Form a defensive screen and assess contacts");
        }
    }

    public static final class FleetFormationCutout {
        public final int forceId;
        public final String fleetLabel;
        public final String shipLabel;
        public final ShipRole role;
        public final FleetFormationRole formationRole;
        public final Faction faction;
        public final double x;
        public final double y;
        public final double offsetX;
        public final double offsetY;
        public final double hullCondition;
        public final boolean known;
        public final boolean damaged;
        public final boolean disabled;
        public final boolean retreating;
        public final String displayIcon;

        FleetFormationCutout(int forceId,
                             String fleetLabel,
                             String shipLabel,
                             ShipRole role,
                             FleetFormationRole formationRole,
                             Faction faction,
                             double x,
                             double y,
                             double offsetX,
                             double offsetY,
                             double hullCondition,
                             boolean known,
                             boolean damaged,
                             boolean disabled,
                             boolean retreating,
                             String displayIcon) {
            this.forceId = Math.max(0, forceId);
            this.fleetLabel = trimmedOrFallback(fleetLabel, "Task Force");
            this.shipLabel = trimmedOrFallback(shipLabel, roleDisplayName(role));
            this.role = role == null ? ShipRole.PATROL : role;
            this.formationRole = formationRole == null ? FleetFormationRole.SCREEN_LEFT : formationRole;
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.hullCondition = MathUtil.clamp(hullCondition, 0.0, 100.0);
            this.known = known;
            this.damaged = damaged;
            this.disabled = disabled;
            this.retreating = retreating;
            this.displayIcon = trimmedOrFallback(displayIcon, "cutout-escort");
        }
    }

    public static final class CampaignMapBookmark {
        public final String label;
        public final String category;
        public final String locationId;
        public final double x;
        public final double y;

        CampaignMapBookmark(String label, String category, String locationId, double x, double y) {
            this.label = (label == null || label.isBlank()) ? "Map Ping" : label.trim();
            this.category = (category == null || category.isBlank()) ? "Ping" : category.trim();
            this.locationId = (locationId == null) ? "" : locationId.trim();
            this.x = x;
            this.y = y;
        }
    }

    public static final class CampaignRouteQueueStop {
        public final String label;
        public final String category;
        public final String locationId;
        public final double x;
        public final double y;
        public final String condition;

        CampaignRouteQueueStop(String label, String category, String locationId, double x, double y, String condition) {
            this.label = (label == null || label.isBlank()) ? "Queued Stop" : label.trim();
            this.category = (category == null || category.isBlank()) ? "Route" : category.trim();
            this.locationId = (locationId == null) ? "" : locationId.trim();
            this.x = x;
            this.y = y;
            this.condition = (condition == null || condition.isBlank()) ? "ALWAYS" : condition.trim();
        }
    }

    public static final class TheaterBand {
        public final String id;
        public final String label;
        public final double minYNorm;
        public final double maxYNorm;
        public final String controlToken;
        public final double controlScore;
        public final double supplyState;
        public final double threatPressure;
        public final double greenInfluence;
        public final double yellowInfluence;
        public final double redInfluence;

        TheaterBand(String id,
                    String label,
                    double minYNorm,
                    double maxYNorm,
                    String controlToken,
                    double controlScore,
                    double supplyState,
                    double threatPressure,
                    double greenInfluence,
                    double yellowInfluence,
                    double redInfluence) {
            this.id = (id == null || id.isBlank()) ? "FRONTIER" : id.trim();
            this.label = (label == null || label.isBlank()) ? this.id : label.trim();
            this.minYNorm = MathUtil.clamp(minYNorm, 0.0, 1.0);
            this.maxYNorm = MathUtil.clamp(maxYNorm, 0.0, 1.0);
            this.controlToken = (controlToken == null || controlToken.isBlank()) ? "CONTESTED" : controlToken.trim();
            this.controlScore = MathUtil.clamp(controlScore, -100.0, 100.0);
            this.supplyState = MathUtil.clamp(supplyState, 0.0, 100.0);
            this.threatPressure = MathUtil.clamp(threatPressure, 0.0, 100.0);
            this.greenInfluence = MathUtil.clamp(greenInfluence, 0.0, 100.0);
            this.yellowInfluence = MathUtil.clamp(yellowInfluence, 0.0, 100.0);
            this.redInfluence = MathUtil.clamp(redInfluence, 0.0, 100.0);
        }
    }

    public enum CampaignControlVisualState {
        GREEN,
        YELLOW,
        RED,
        CONTESTED,
        UNKNOWN
    }

    public static final class CampaignLocationControlView {
        public final CampaignControlVisualState control;
        public final String siteType;
        public final String status;
        public final double contestProgress;
        public final double threatPressure;

        CampaignLocationControlView(CampaignControlVisualState control,
                                    String siteType,
                                    String status,
                                    double contestProgress,
                                    double threatPressure) {
            this.control = (control == null) ? CampaignControlVisualState.UNKNOWN : control;
            this.siteType = (siteType == null || siteType.isBlank()) ? "Site" : siteType.trim();
            this.status = (status == null || status.isBlank()) ? this.control.name() : status.trim();
            this.contestProgress = MathUtil.clamp(contestProgress, -120.0, 120.0);
            this.threatPressure = MathUtil.clamp(threatPressure, 0.0, 100.0);
        }
    }

    public static final class CampaignMissionBoardEntry {
        public final String id;
        public final Faction faction;
        public final String family;
        public final String targetLocationId;
        public final String objective;
        public final String risk;
        public final String reward;
        public final String timePressure;
        public final String opposition;
        public final int reputationDelta;
        public final int redHostilityDelta;

        CampaignMissionBoardEntry(String id,
                                  Faction faction,
                                  String family,
                                  String targetLocationId,
                                  String objective,
                                  String risk,
                                  String reward,
                                  String timePressure,
                                  String opposition,
                                  int reputationDelta,
                                  int redHostilityDelta) {
            this.id = trimmedOrFallback(id, "board-mission");
            this.faction = faction == null ? Faction.TEAM_C : faction;
            this.family = trimmedOrFallback(family, "operation");
            this.targetLocationId = targetLocationId == null ? "" : targetLocationId.trim();
            this.objective = trimmedOrFallback(objective, "Support local operations");
            this.risk = trimmedOrFallback(risk, "Moderate");
            this.reward = trimmedOrFallback(reward, "Reputation and supplies");
            this.timePressure = trimmedOrFallback(timePressure, "Open");
            this.opposition = trimmedOrFallback(opposition, "Unknown");
            this.reputationDelta = Math.max(0, reputationDelta);
            this.redHostilityDelta = Math.max(0, redHostilityDelta);
        }

        public String line() {
            return CampaignSystem.factionBoardName(faction) + "  |  " + family + "  |  " + objective
                    + "  |  risk " + risk
                    + "  |  reward " + reward
                    + "  |  pressure " + timePressure
                    + "  |  opposition " + opposition;
        }
    }

    public enum CampaignRouteSegmentKind {
        LOCAL_ZONE,
        SUPPLY_LINE,
        CONTESTED_LANE,
        BLOCKADE_LINE,
        PLAYER_PLOTTED
    }

    public static final class CampaignRouteSegment {
        public final CampaignRouteSegmentKind kind;
        public final CampaignControlVisualState control;
        public final String fromLocationId;
        public final String toLocationId;
        public final double fromX;
        public final double fromY;
        public final double toX;
        public final double toY;
        public final double danger;
        public final boolean playerRoute;

        CampaignRouteSegment(CampaignRouteSegmentKind kind,
                             CampaignControlVisualState control,
                             String fromLocationId,
                             String toLocationId,
                             double fromX,
                             double fromY,
                             double toX,
                             double toY,
                             double danger,
                             boolean playerRoute) {
            this.kind = kind == null ? CampaignRouteSegmentKind.LOCAL_ZONE : kind;
            this.control = control == null ? CampaignControlVisualState.UNKNOWN : control;
            this.fromLocationId = fromLocationId == null ? "" : fromLocationId.trim();
            this.toLocationId = toLocationId == null ? "" : toLocationId.trim();
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.danger = MathUtil.clamp(danger, 0.0, 100.0);
            this.playerRoute = playerRoute;
        }
    }

    public static final class CampaignInvasionArrow {
        public final int forceId;
        public final Faction faction;
        public final String forceName;
        public final String sourceLocationId;
        public final String targetLocationId;
        public final String label;
        public final double fromX;
        public final double fromY;
        public final double toX;
        public final double toY;
        public final double strength;
        public final double etaSeconds;

        CampaignInvasionArrow(int forceId,
                              Faction faction,
                              String forceName,
                              String sourceLocationId,
                              String targetLocationId,
                              String label,
                              double fromX,
                              double fromY,
                              double toX,
                              double toY,
                              double strength,
                              double etaSeconds) {
            this.forceId = Math.max(0, forceId);
            this.faction = faction;
            this.forceName = trimmedOrFallback(forceName, "Invasion Fleet");
            this.sourceLocationId = sourceLocationId == null ? "" : sourceLocationId.trim();
            this.targetLocationId = targetLocationId == null ? "" : targetLocationId.trim();
            this.label = trimmedOrFallback(label, "invasion route");
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.strength = MathUtil.clamp(strength, 0.0, 100.0);
            this.etaSeconds = Math.max(0.0, etaSeconds);
        }
    }

    public static final class CampaignTerritoryEdgeView {
        public final String fromId;
        public final String toId;
        public final double fromX;
        public final double fromY;
        public final double toX;
        public final double toY;
        public final boolean directed;
        public final boolean legalInvasion;
        public final boolean blocked;
        public final boolean supplyCapable;
        public final String explanation;

        CampaignTerritoryEdgeView(String fromId, String toId, double fromX, double fromY,
                                  double toX, double toY, boolean directed, boolean legalInvasion,
                                  boolean blocked, boolean supplyCapable, String explanation) {
            this.fromId = fromId;
            this.toId = toId;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.directed = directed;
            this.legalInvasion = legalInvasion;
            this.blocked = blocked;
            this.supplyCapable = supplyCapable;
            this.explanation = explanation;
        }
    }

    public record CampaignTerritoryOverlayView(String id, String name, double x, double y,
                                               Faction faction, String insignia, String pattern,
                                               StrategicCampaignExpansionSystem.TerritoryControlState controlState,
                                               StrategicCampaignExpansionSystem.SupplyState supplyState,
                                               int pressure, boolean supplySource, boolean frontLine,
                                               boolean activeOperation, boolean beachhead,
                                               String statusText) {}

    public record CampaignBattleScarView(String battleId, String locationId, double x, double y,
                                         String scar, String outcome, int casualties,
                                         int salvageRemaining, int survivorWindowTicks) {}

    public static final class CampaignFinalBattleReadiness {
        public final int readinessScore;
        public final int greenSupport;
        public final int yellowSupport;
        public final int capturedShipyards;
        public final int capturedRelays;
        public final int destroyedRedFortresses;
        public final int liberatedYellowHubs;
        public final int remainingRedDefenses;
        public final String summary;
        public final List<String> lines;

        CampaignFinalBattleReadiness(int readinessScore,
                                     int greenSupport,
                                     int yellowSupport,
                                     int capturedShipyards,
                                     int capturedRelays,
                                     int destroyedRedFortresses,
                                     int liberatedYellowHubs,
                                     int remainingRedDefenses,
                                     String summary,
                                     List<String> lines) {
            this.readinessScore = (int) Math.round(MathUtil.clamp(readinessScore, 0, 100));
            this.greenSupport = Math.max(0, greenSupport);
            this.yellowSupport = Math.max(0, yellowSupport);
            this.capturedShipyards = Math.max(0, capturedShipyards);
            this.capturedRelays = Math.max(0, capturedRelays);
            this.destroyedRedFortresses = Math.max(0, destroyedRedFortresses);
            this.liberatedYellowHubs = Math.max(0, liberatedYellowHubs);
            this.remainingRedDefenses = Math.max(0, remainingRedDefenses);
            this.summary = trimmedOrFallback(summary, "Earth assault readiness unknown");
            this.lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public enum CampaignAidType {
        ORE,
        CREDITS,
        INTELLIGENCE,
        SHIP
    }

    public static final class CampaignAidPreview {
        public final Faction recipient;
        public final CampaignAidType type;
        public final int amount;
        public final int fleetSlotId;
        public final String cost;
        public final int reputationGain;
        public final String strategicEffect;
        public final boolean available;
        public final String unavailableReason;
        public final boolean confirmationRequired;

        CampaignAidPreview(Faction recipient,
                           CampaignAidType type,
                           int amount,
                           int fleetSlotId,
                           String cost,
                           int reputationGain,
                           String strategicEffect,
                           boolean available,
                           String unavailableReason,
                           boolean confirmationRequired) {
            this.recipient = CampaignSystem.isBrightYellowFaction(recipient) ? Faction.BRIGHT_YELLOW : Faction.TEAM_C;
            this.type = type == null ? CampaignAidType.CREDITS : type;
            this.amount = Math.max(0, amount);
            this.fleetSlotId = Math.max(0, fleetSlotId);
            this.cost = trimmedOrFallback(cost, "No cost");
            this.reputationGain = Math.max(0, reputationGain);
            this.strategicEffect = trimmedOrFallback(strategicEffect, "Faction support improves");
            this.available = available;
            this.unavailableReason = available ? "" : trimmedOrFallback(unavailableReason, "Unavailable");
            this.confirmationRequired = confirmationRequired;
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

    public enum CampaignFacilityType {
        UNKNOWN,
        FORTRESS,
        RESUPPLY_BASE,
        MINING_OPERATION,
        REPAIR_YARD,
        SHIPYARD,
        RELAY,
        LISTENING_POST,
        CIVILIAN_HUB,
        REBEL_HIDEOUT,
        BLOCKADE,
        PRISON_CAMP,
        FUEL_DEPOT,
        DERELICT_BATTLEFIELD,
        SENSOR_TOWER,
        BOSS_STAGING_AREA
    }

    public enum CampaignIntelLevel {
        UNKNOWN,
        PARTIAL,
        GOOD,
        FULL
    }

    public enum HubService {
        REPAIR("Request Support"),
        TRADE("Request Trade"),
        REFIT("Refit Ships"),
        SHIPYARD("Purchase Ships"),
        SUPPLY("Buy Ore"),
        STRIKE_REARM("Service Strikes"),
        INTEL("Gather Intel"),
        CONTRACTS("Job Board"),
        SALVAGE("Sell Ore Lot"),
        FUEL("Ore Delivery");

        public final String label;

        HubService(String label) {
            this.label = (label == null || label.isBlank()) ? name() : label;
        }
    }

    enum HubAlignment {
        GREEN,
        YELLOW,
        FRONTIER
    }

    enum GalaxyRegionIdentity {
        SOUTHERN_SHELTER,
        CONTESTED_BELT,
        EARTHWARDED_NORTH
    }

    enum TheaterId {
        SOUTHERN("Southern Zone - Green Controlled"),
        FRONTIER("Lower-Middle Zone - Yellow Controlled"),
        LUNAR("Upper-Middle Zone - Red Occupied"),
        EARTH("Northern Zone - Red Core");

        final String label;

        TheaterId(String label) {
            this.label = (label == null || label.isBlank()) ? name() : label;
        }
    }

    enum TheaterControlState {
        BLUE_GREEN_CONTROLLED,
        CONTESTED,
        RED_CONTROLLED
    }

    enum TheaterNodeType {
        SHIPYARD,
        RELAY,
        LOGISTICS_HUB,
        DEFENSE_ANCHOR,
        RESOURCE_FIELD
    }

    enum TheaterNodeOwner {
        BLUE_GREEN,
        RED,
        CONTESTED,
        NEUTRAL
    }

    static final class CampaignTheaterState {
        final TheaterId id;
        final String label;
        final double minYNorm;
        final double maxYNorm;
        double controlScore = 0.0;
        double supplyState = 50.0;
        double threatPressure = 50.0;
        double redPresence = 0.0;
        double greenPresence = 0.0;
        double yellowActivity = 0.0;
        double greenInfluence = 0.0;
        double yellowInfluence = 0.0;
        double redInfluence = 0.0;
        double danger = 50.0;
        double tradeHealth = 70.0;
        double routeRisk = 30.0;
        double marketPressure = 0.0;
        double installationIntegrity = 100.0;
        TheaterControlState controlState = TheaterControlState.CONTESTED;

        CampaignTheaterState(TheaterId id, String label, double minYNorm, double maxYNorm) {
            this.id = (id == null) ? TheaterId.FRONTIER : id;
            this.label = (label == null || label.isBlank()) ? this.id.label : label.trim();
            this.minYNorm = MathUtil.clamp(minYNorm, 0.0, 1.0);
            this.maxYNorm = MathUtil.clamp(maxYNorm, 0.0, 1.0);
            CampaignSystem.applyStartingInfluence(this);
        }
    }

    static final class StrategicNodeState {
        final String locationId;
        final TheaterId theaterId;
        final TheaterNodeType type;
        TheaterNodeOwner owner;
        double contestProgress = 0.0;
        double takeoverCooldownSec = 0.0;
        TheaterNodeOwner lastOwner;
        String lastCaptureRejection = "";

        StrategicNodeState(String locationId, TheaterId theaterId, TheaterNodeType type, TheaterNodeOwner owner) {
            this.locationId = (locationId == null) ? "" : locationId.trim();
            this.theaterId = (theaterId == null) ? TheaterId.FRONTIER : theaterId;
            this.type = (type == null) ? TheaterNodeType.RELAY : type;
            this.owner = (owner == null) ? TheaterNodeOwner.NEUTRAL : owner;
            this.lastOwner = this.owner;
        }
    }

    static final class HubProfile {
        final HubAlignment alignment;
        final double regionPressure;
        final double quality;
        final double priceMul;
        final double supportMul;
        final double tradeMul;
        final double logisticsMul;

        HubProfile(HubAlignment alignment,
                   double regionPressure,
                   double quality,
                   double priceMul,
                   double supportMul,
                   double tradeMul,
                   double logisticsMul) {
            this.alignment = (alignment == null) ? HubAlignment.FRONTIER : alignment;
            this.regionPressure = MathUtil.clamp(regionPressure, 0.0, 1.0);
            this.quality = Math.max(0.5, quality);
            this.priceMul = Math.max(0.4, priceMul);
            this.supportMul = Math.max(0.5, supportMul);
            this.tradeMul = Math.max(0.5, tradeMul);
            this.logisticsMul = Math.max(0.5, logisticsMul);
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
        public List<HubService> services;
        public String facilityId = "";
        public String legacyPoiId = "";
        public String zoneId = "";
        public CampaignFacilityType facilityType = CampaignFacilityType.UNKNOWN;
        public Faction ownerFaction = Faction.ALLY;
        public CampaignControlVisualState controlState = CampaignControlVisualState.UNKNOWN;
        public int strategicValue = 1;
        public double defenseStrength = 0.0;
        public double resourceValue = 0.0;
        public int oreStockpile = 0;
        public int repairSupplyStockpile = 0;
        public int ammunitionStockpile = 0;
        public int fuelStockpile = 0;
        public int dockedShipCount = 0;
        public int damagedShipCount = 0;
        public int destroyedShipCount = 0;
        public int constructionQueueCount = 0;
        public int repairQueueCount = 0;
        public int defenseGarrisonShips = 0;
        public int localPatrolShips = 0;
        public int miningAssignments = 0;
        public int convoyAssignments = 0;
        public String stationDamageState = "intact";
        public String stationServiceState = "online";
        public final java.util.LinkedHashSet<String> stationMemoryFlags = new java.util.LinkedHashSet<>();
        public final List<String> missionTags = new ArrayList<>();
        public final List<Integer> linkedFleetIds = new ArrayList<>();
        public final List<String> linkedRouteIds = new ArrayList<>();
        public boolean canChangeOwner = true;
        public boolean canSpawnFleets = false;
        public String alertState = "quiet";
        public String displayLabel = "";
        public String longDetail = "";
        public double lastChangedAtSec = 0.0;
        public double intelStaleAtSec = 0.0;
        public CampaignIntelLevel fleetIntelLevel = CampaignIntelLevel.UNKNOWN;
        public CampaignIntelLevel intelLevel = CampaignIntelLevel.UNKNOWN;
        public boolean discovered;
        public boolean completed;
        public boolean consumed;
        public boolean destroyed;
        public String scarNote = "";
        public String routeNote = "";
        public String recurringContactId = "";
        public String recurringContactStatus = "";
        public boolean supportRouteStabilized = false;
        public ContactIntelQuality intelQuality = ContactIntelQuality.UNKNOWN;
        public DiscoveryChainType chainType = DiscoveryChainType.NONE;
        public int chainStage = 0;
        public double unresolvedAgeSec = 0.0;
        public int escalationStage = 0;
        public double missionOuterThreatSuppression = 0.0;
        public double missionOuterThreatDisruptionSec = 0.0;
        public double strategicRepairMultiplier = 1.0;
        public double strategicAmmoMultiplier = 1.0;
        public double strategicReinforcementMultiplier = 1.0;
        public double strategicConstructionMultiplier = 1.0;
        public double strategicMoraleMultiplier = 1.0;
        public double strategicInvasionMultiplier = 1.0;

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
            this.facilityId = this.id;
            this.legacyPoiId = this.id.startsWith("poi-") ? this.id : "";
            this.facilityType = CampaignSystem.defaultFacilityTypeForLocationType(this.type);
            this.ownerFaction = CampaignSystem.defaultOwnerFactionForLocation(this.type, this.name);
            this.strategicValue = CampaignSystem.defaultStrategicValueForFacility(this.facilityType, primaryMission);
            this.intelLevel = primaryMission ? CampaignIntelLevel.FULL : CampaignIntelLevel.GOOD;
            CampaignSystem.initializeFacilityOperationalFields(this);
            ArrayList<HubService> resolvedServices = new ArrayList<>();
            if (services != null) {
                for (HubService service : services) {
                    if (service != null && !resolvedServices.contains(service)) {
                        resolvedServices.add(service);
                    }
                }
            }
            this.services = new ArrayList<>(resolvedServices);
            this.discovered = true;
            this.missionOuterThreatSuppression = primaryMission ? 0.0 : 1.0;
        }
    }

    public static final class CampaignTravelState {
        public String originId = "";
        public String destinationId = "";
        public String destinationLabel = "";
        public double progress = 0.0;
        public double durationSec = 0.0;
        public boolean traveling = false;
        public boolean freeTravel = false;
        public float interceptionRisk = 0.0f;
        public double targetX = Double.NaN;
        public double targetY = Double.NaN;
        public double speed = 0.0;

        public void clear() {
            originId = "";
            destinationId = "";
            destinationLabel = "";
            progress = 0.0;
            durationSec = 0.0;
            traveling = false;
            freeTravel = false;
            interceptionRisk = 0.0f;
            targetX = Double.NaN;
            targetY = Double.NaN;
            speed = 0.0;
        }
    }

    enum GalaxySearchBehavior {
        PATROLLING,
        SEARCHING,
        INVESTIGATING,
        INTERCEPTING,
        GUARDING,
        RETURNING
    }

    enum GalaxyContactConfidence {
        UNKNOWN_CONTACT,
        POSSIBLE_PATROL,
        CONFIRMED_HOSTILE,
        IDENTIFIED_TASK_FORCE,
        LOST_CONTACT
    }

    enum ContactIntelQuality {
        UNKNOWN,
        CLASSIFIED,
        IDENTIFIED,
        TRACKED,
        TARGET_QUALITY
    }

    enum SensorSignatureClass {
        ENGINE_PLUME,
        COMMS_CHATTER,
        MASS_SHADOW,
        WEAPONS_HEAT
    }

    enum GalaxySearchDoctrine {
        SCOUT_SCREEN,
        HUNTER_KILLER,
        BLOCKADE_GROUP,
        INTERDICTION_GROUP,
        PUNISHMENT_FLEET
    }

    enum FleetCommitment {
        AUTO,
        COMMIT,
        HOLD_BACK,
        RESERVE
    }

    enum CampaignReputationState {
        UNKNOWN_FLEET,
        RELIABLE_RESCUE_FORCE,
        RAIDER_THREAT,
        LIBERATION_SYMBOL,
        OVEREXTENDED_COMMAND,
        HIGH_EXPOSURE_TARGET
    }

    enum FleetPosture {
        SILENT_RUNNING,
        COMBAT_PATROL,
        RESCUE_PRIORITY,
        RAIDER_DOCTRINE,
        LOGISTICS_CONSERVATION,
        RECON_SWEEP
    }

    enum StrategicStrikePayload {
        TORPEDO,
        SORTIE,
        ATOMIC
    }

    enum StrategicStrikeTargetKind {
        SEARCH_GROUP,
        TASK_FORCE,
        MISSION_OUTER_THREAT,
        CAMPAIGN_FORCE
    }

    enum TheaterPressureState {
        PATROL_NET_EXPANDING,
        BLOCKADE_TIGHTENING,
        TRADE_LANES_UNSTABLE,
        SUPPLY_LINES_WEAKENING,
        HIDDEN_HOSTILES_ACTIVE
    }

    enum SiteResolutionMode {
        FAST_STRIP,
        CAREFUL_SECURE,
        MARK_FOR_ALLIES,
        EVAC_SURVIVORS,
        TOW_DAMAGED_HULL,
        STRIP_FOR_PARTS,
        QUIET_DECODE,
        ALLY_BROADCAST,
        JAM_AND_DESTROY
    }

    enum DiscoveryChainType {
        NONE,
        RELAY_ECHO,
        WRECK_TRAIL,
        FALSE_DISTRESS,
        MISSING_PATROL,
        SMUGGLER_LEAD
    }

    enum CampaignRelationshipState {
        UNKNOWN,
        HELPED,
        TRUSTED,
        OWED_FAVOR,
        NEGLECTED,
        HOSTILE,
        MISSING,
        DESTROYED
    }

    public enum CampaignActionCategory {
        NAVIGATION,
        SERVICES,
        STRIKES,
        SUPPORT,
        POSTURE,
        SITE_RESOLUTION,
        SENSORS
    }

    public enum CampaignActionState {
        AVAILABLE,
        DISABLED,
        WARNING,
        RECOMMENDED
    }

    @FunctionalInterface
    public interface CampaignActionExecutor {
        boolean execute(GameContext ctx);
    }

    public static final class CampaignAction {
        public final String id;
        public final String label;
        public final String shortDescription;
        public final String tooltip;
        public final CampaignActionCategory category;
        public final boolean visible;
        public final boolean enabled;
        public final String disabledReason;
        public final CampaignActionState state;
        public final boolean primary;
        public final String shortcut;
        public final CampaignActionExecutor execute;

        CampaignAction(String id,
                       String label,
                       String shortDescription,
                       String tooltip,
                       CampaignActionCategory category,
                       boolean visible,
                       boolean enabled,
                       String disabledReason,
                       CampaignActionState state,
                       boolean primary,
                       String shortcut,
                       CampaignActionExecutor execute) {
            this.id = (id == null) ? "" : id.trim();
            this.label = (label == null || label.isBlank()) ? this.id : label.trim();
            this.shortDescription = (shortDescription == null) ? "" : shortDescription.trim();
            this.tooltip = (tooltip == null) ? "" : tooltip.trim();
            this.category = (category == null) ? CampaignActionCategory.NAVIGATION : category;
            this.visible = visible;
            this.enabled = enabled;
            this.disabledReason = (disabledReason == null) ? "" : disabledReason.trim();
            this.state = (state == null) ? (enabled ? CampaignActionState.AVAILABLE : CampaignActionState.DISABLED) : state;
            this.primary = primary;
            this.shortcut = (shortcut == null) ? "" : shortcut.trim();
            this.execute = execute;
        }
    }

    static final class GalaxyRouteAssessment {
        final double distance;
        final double northPressure;
        final double hostileCoverage;
        final double supportCoverage;
        final double opportunityCoverage;
        final double logisticsPressure;
        final double interceptionRisk;
        final double cruiseSpeed;
        final double durationSec;

        GalaxyRouteAssessment(double distance,
                              double northPressure,
                              double hostileCoverage,
                              double supportCoverage,
                              double opportunityCoverage,
                              double logisticsPressure,
                              double interceptionRisk,
                              double cruiseSpeed,
                              double durationSec) {
            this.distance = distance;
            this.northPressure = northPressure;
            this.hostileCoverage = hostileCoverage;
            this.supportCoverage = supportCoverage;
            this.opportunityCoverage = opportunityCoverage;
            this.logisticsPressure = logisticsPressure;
            this.interceptionRisk = interceptionRisk;
            this.cruiseSpeed = cruiseSpeed;
            this.durationSec = durationSec;
        }
    }

    static final class StrategicRoleProfile {
        int stealthHullCount;
        int carrierHullCount;
        int heavyHullCount;
        int strikeHullCount;
        int logisticsHullCount;
        int screenHullCount;

        double stealthCoverage() {
            return MathUtil.clamp(stealthHullCount * 0.18, 0.0, 0.42);
        }

        double carrierProjection() {
            return MathUtil.clamp(carrierHullCount * 0.20, 0.0, 0.70);
        }

        double heavyPresence() {
            return MathUtil.clamp(heavyHullCount * 0.18, 0.0, 0.72);
        }

        double strikeReach() {
            return MathUtil.clamp(strikeHullCount * 0.12, 0.0, 0.48);
        }

        double logisticsSupport() {
            return MathUtil.clamp(logisticsHullCount * 0.16, 0.0, 0.54);
        }

        double screenCoverage() {
            return MathUtil.clamp(screenHullCount * 0.10, 0.0, 0.36);
        }

        double roleCoverageBonus() {
            int categories = 0;
            if (stealthHullCount > 0) categories++;
            if (carrierHullCount > 0) categories++;
            if (heavyHullCount > 0) categories++;
            if (strikeHullCount > 0) categories++;
            if (logisticsHullCount > 0) categories++;
            if (screenHullCount > 0) categories++;
            return 1.0 + Math.max(0, categories - 1) * 0.035;
        }
    }

    static final class StrategicCountermeasureProfile {
        double interception;
        double jamming;
        double decoy;
        double evasion;
        double alertResponse;
    }

    static final class StrategicStrikeObject {
        final int id;
        final StrategicStrikePayload payload;
        final StrategicStrikeTargetKind targetKind;
        final int targetId;
        final String targetLocationId;
        final Faction owner;
        String targetLabel;
        double x;
        double y;
        double targetX;
        double targetY;
        double speed;
        double ageSec;

        StrategicStrikeObject(int id,
                              StrategicStrikePayload payload,
                              StrategicStrikeTargetKind targetKind,
                              int targetId,
                              String targetLocationId,
                              String targetLabel,
                              Faction owner,
                              double x,
                              double y,
                              double targetX,
                              double targetY,
                              double speed) {
            this.id = Math.max(1, id);
            this.payload = (payload == null) ? StrategicStrikePayload.TORPEDO : payload;
            this.targetKind = (targetKind == null) ? StrategicStrikeTargetKind.SEARCH_GROUP : targetKind;
            this.targetId = Math.max(0, targetId);
            this.targetLocationId = (targetLocationId == null) ? "" : targetLocationId.trim();
            this.targetLabel = (targetLabel == null || targetLabel.isBlank()) ? "Hostile Contact" : targetLabel.trim();
            this.owner = (owner == null) ? Faction.PLAYER : owner;
            this.x = x;
            this.y = y;
            this.targetX = targetX;
            this.targetY = targetY;
            this.speed = Math.max(80.0, speed);
            this.ageSec = 0.0;
        }
    }

    static final class StrikePreflight {
        final String actionId;
        final String targetLabel;
        final double targetX;
        final double targetY;
        final boolean valid;
        final String reason;
        final int ammoCost;
        final int fuelCost;
        final int supplyCost;
        final int chargeCost;
        final String effect;
        final String retaliation;

        StrikePreflight(String actionId,
                        String targetLabel,
                        boolean valid,
                        String reason,
                        int ammoCost,
                        int fuelCost,
                        int supplyCost,
                        int chargeCost,
                        String effect,
                        String retaliation) {
            this(actionId, targetLabel, Double.NaN, Double.NaN, valid, reason,
                    ammoCost, fuelCost, supplyCost, chargeCost, effect, retaliation);
        }

        StrikePreflight(String actionId,
                        String targetLabel,
                        double targetX,
                        double targetY,
                        boolean valid,
                        String reason,
                        int ammoCost,
                        int fuelCost,
                        int supplyCost,
                        int chargeCost,
                        String effect,
                        String retaliation) {
            this.actionId = (actionId == null) ? "" : actionId;
            this.targetLabel = (targetLabel == null || targetLabel.isBlank()) ? "NO TARGET SELECTED" : targetLabel;
            this.targetX = targetX;
            this.targetY = targetY;
            this.valid = valid;
            this.reason = (reason == null) ? "" : reason;
            this.ammoCost = Math.max(0, ammoCost);
            this.fuelCost = Math.max(0, fuelCost);
            this.supplyCost = Math.max(0, supplyCost);
            this.chargeCost = Math.max(0, chargeCost);
            this.effect = (effect == null) ? "" : effect;
            this.retaliation = (retaliation == null) ? "" : retaliation;
        }
    }

    public static final class StrikeAvailabilityBrief {
        public final String strikeName;
        public final String inventory;
        public final String resourceCost;
        public final String capacityCost;
        public final String requiredIntel;
        public final String estimatedEffect;
        public final String retaliationRisk;
        public final boolean available;
        public final String unavailableReason;
        public final String replenishment;

        StrikeAvailabilityBrief(String strikeName,
                                String inventory,
                                String resourceCost,
                                String capacityCost,
                                String requiredIntel,
                                String estimatedEffect,
                                String retaliationRisk,
                                boolean available,
                                String unavailableReason,
                                String replenishment) {
            this.strikeName = strikeName;
            this.inventory = inventory;
            this.resourceCost = resourceCost;
            this.capacityCost = capacityCost;
            this.requiredIntel = requiredIntel;
            this.estimatedEffect = estimatedEffect;
            this.retaliationRisk = retaliationRisk;
            this.available = available;
            this.unavailableReason = unavailableReason;
            this.replenishment = replenishment;
        }
    }

    static final class StrikeCost {
        final int ammo;
        final int fuel;
        final int supplies;
        final int charges;

        StrikeCost(int ammo, int fuel, int supplies, int charges) {
            this.ammo = Math.max(0, ammo);
            this.fuel = Math.max(0, fuel);
            this.supplies = Math.max(0, supplies);
            this.charges = Math.max(0, charges);
        }
    }

    static final class StrategicStrikeTargetLock {
        final String label;
        final String intelLabel;
        final double x;
        final double y;

        StrategicStrikeTargetLock(String label, String intelLabel, double x, double y) {
            this.label = (label == null || label.isBlank()) ? "Hostile Contact" : label.trim();
            this.intelLabel = (intelLabel == null || intelLabel.isBlank()) ? "Tracked" : intelLabel.trim();
            this.x = x;
            this.y = y;
        }
    }

    static final class GalaxySearchGroup {
        final int id;
        final String label;
        final double speed;
        final double detectionRange;
        final double interceptRange;
        final float threatLevel;
        final CampaignLocationType anchorType;
        final int tier;
        double x;
        double y;
        double targetX;
        double targetY;
        double searchRadius;
        double stateTimer;
        boolean hostile = true;
        boolean visible = false;
        boolean identified = false;
        GalaxyContactConfidence contactConfidence = GalaxyContactConfidence.UNKNOWN_CONTACT;
        ContactIntelQuality intelQuality = ContactIntelQuality.UNKNOWN;
        GalaxySearchDoctrine doctrine = GalaxySearchDoctrine.SCOUT_SCREEN;
        double contactFadeSec = 0.0;
        GalaxySearchBehavior behavior = GalaxySearchBehavior.PATROLLING;
        String anchorLocationId = "";
        double trackIntegrity = 0.0;
        double decoyRisk = 0.0;
        double engineSignature = 0.0;
        double commsSignature = 0.0;
        double massSignature = 0.0;
        double heatSignature = 0.0;
        double focusedTrackLockSec = 0.0;
        double scoutPressureSec = 0.0;
        double lastKnownX;
        double lastKnownY;
        double lastKnownAgeSec = 0.0;
        boolean openingSpawnProtectionApplied = false;

        GalaxySearchGroup(int id, String label, double x, double y,
                          double speed, double detectionRange, double interceptRange,
                          float threatLevel, CampaignLocationType anchorType, int tier) {
            this.id = Math.max(1, id);
            this.label = (label == null || label.isBlank()) ? "Unknown Contact" : label.trim();
            this.x = x;
            this.y = y;
            this.targetX = x;
            this.targetY = y;
            this.lastKnownX = x;
            this.lastKnownY = y;
            this.speed = Math.max(20.0, speed);
            this.detectionRange = Math.max(120.0, detectionRange);
            this.interceptRange = Math.max(90.0, interceptRange);
            this.threatLevel = Math.max(0.0f, threatLevel);
            this.anchorType = (anchorType == null) ? CampaignLocationType.ENEMY_ACTIVITY : anchorType;
            this.tier = Math.max(1, tier);
            this.searchRadius = 620.0;
            this.stateTimer = 8.0;
            this.trackIntegrity = 18.0;
        }
    }

    static final class CampaignInstallationThreatCase {
        final int id;
        final String locationId;
        final String forceName;
        final String origin;
        final String warning;
        final double threatLevel;
        boolean active = true;

        CampaignInstallationThreatCase(int id,
                                       String locationId,
                                       String forceName,
                                       String origin,
                                       String warning,
                                       double threatLevel) {
            this.id = Math.max(1, id);
            this.locationId = (locationId == null) ? "" : locationId.trim();
            this.forceName = (forceName == null || forceName.isBlank()) ? "Unknown Infiltration Cell" : forceName.trim();
            this.origin = (origin == null || origin.isBlank()) ? "unknown hostile ingress" : origin.trim();
            this.warning = (warning == null || warning.isBlank()) ? "Hostile infiltrators are operating inside the installation approach." : warning.trim();
            this.threatLevel = MathUtil.clamp(threatLevel, 0.0, 1.0);
        }
    }

    static final class PendingHostileReinforcement {
        final int id;
        final String label;
        final String sourceLocationId;
        final String targetLocationId;
        final double sourceX;
        final double sourceY;
        final double targetX;
        final double targetY;
        final double threatLevel;
        final GalaxySearchDoctrine doctrine;
        double etaSec;
        boolean warned = false;
        boolean warnedMid = false;
        boolean warnedFinal = false;

        PendingHostileReinforcement(int id,
                                    String label,
                                    String sourceLocationId,
                                    String targetLocationId,
                                    double sourceX,
                                    double sourceY,
                                    double targetX,
                                    double targetY,
                                    double threatLevel,
                                    GalaxySearchDoctrine doctrine,
                                    double etaSec) {
            this.id = Math.max(1, id);
            this.label = trimmedOrFallback(label, "Incoming Hostile Fleet");
            this.sourceLocationId = (sourceLocationId == null) ? "" : sourceLocationId.trim();
            this.targetLocationId = (targetLocationId == null) ? "" : targetLocationId.trim();
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.threatLevel = MathUtil.clamp(threatLevel, 0.05, 1.0);
            this.doctrine = (doctrine == null) ? GalaxySearchDoctrine.SCOUT_SCREEN : doctrine;
            this.etaSec = Math.max(2.0, etaSec);
        }
    }

    enum CampaignBattleStage {
        FORMING,
        SKIRMISHING,
        DECISIVE,
        RETREATING,
        RESOLVED
    }

    static final class CampaignBattle {
        final int id;
        final Set<Integer> participantForceIds = new HashSet<>();
        double x;
        double y;
        CampaignBattleStage stage = CampaignBattleStage.FORMING;
        double elapsedSec = 0.0;
        double durationSec = 16.0;
        double importance = 0.0;
        boolean playerAwareness = false;
        boolean attritionApplied = false;
        boolean resolved = false;
        boolean interventionPrompted = false;
        boolean interventionResolved = false;
        String playerIntervention = "";
        String playerPromptSuppressedReason = "";
        String outcomeReport = "";
        String winnerFollowUp = "";
        String loserFollowUp = "";
        String participantManifest = "";

        CampaignBattle(int id, CampaignSystem.CampaignForce a, CampaignSystem.CampaignForce b) {
            this.id = Math.max(1, id);
            if (a != null) participantForceIds.add(a.id);
            if (b != null) participantForceIds.add(b.id);
            this.x = ((a == null ? 0.0 : a.x) + (b == null ? 0.0 : b.x)) * 0.5;
            this.y = ((a == null ? 0.0 : a.y) + (b == null ? 0.0 : b.y)) * 0.5;
            this.importance = MathUtil.clamp(((a == null ? 0.0 : a.strength) + (b == null ? 0.0 : b.strength)) / 140.0, 0.1, 1.0);
            this.participantManifest = CampaignSystem.campaignBattleParticipantManifest(a, b);
        }
    }

    static final class NpcForceContact {
        final int forceId;
        double x;
        double y;
        double confidence;
        double ageSec;

        NpcForceContact(CampaignSystem.CampaignForce force, double confidence) {
            this.forceId = force == null ? 0 : force.id;
            this.x = force == null ? 0.0 : force.x;
            this.y = force == null ? 0.0 : force.y;
            this.confidence = MathUtil.clamp(confidence, 0.0, 1.0);
            this.ageSec = 0.0;
        }
    }

    static final class SensorRelayNode {
        final int id;
        final String label;
        double x;
        double y;
        double radius;
        double ttlSec;
        boolean scout;

        SensorRelayNode(int id, String label, double x, double y, double radius, double ttlSec, boolean scout) {
            this.id = Math.max(1, id);
            this.label = (label == null || label.isBlank()) ? (scout ? "Scout Relay" : "Relay Drone") : label.trim();
            this.x = x;
            this.y = y;
            this.radius = Math.max(120.0, radius);
            this.ttlSec = Math.max(1.0, ttlSec);
            this.scout = scout;
        }
    }

    static final class CampaignSensorPulse {
        final String label;
        final double x;
        final double y;
        final double strength;
        final double uncertaintyRadius;
        final boolean hostile;
        final boolean relay;

        CampaignSensorPulse(String label, double x, double y, double strength, double uncertaintyRadius, boolean hostile, boolean relay) {
            this.label = (label == null || label.isBlank()) ? "Signal" : label.trim();
            this.x = x;
            this.y = y;
            this.strength = MathUtil.clamp(strength, 0.0, 1.0);
            this.uncertaintyRadius = Math.max(60.0, uncertaintyRadius);
            this.hostile = hostile;
            this.relay = relay;
        }
    }

    enum StrategicTaskForceKind {
        PATROL,
        STRIKE,
        STEALTH,
        CONVOY,
        SALVAGE
    }

    static final class StrategicTaskForce {
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

    public enum CampaignForceKind {
        PLAYER_FLEET,
        PATROL_GROUP,
        TASK_FORCE,
        BASE_DEFENSE,
        CONVOY,
        MINING_GROUP,
        TRADE_GROUP,
        INSTALLATION_TRAFFIC,
        STRIKE_DETACHMENT,
        LOCAL_FORCE
    }

    public enum CampaignForceIntent {
        PATROLLING,
        GUARDING,
        SEARCHING,
        INTERCEPTING,
        ESCORTING,
        MINING,
        RETREATING,
        REINFORCING,
        REPAIRING,
        REGROUPING,
        DOCKING,
        HOLDING
    }

    public enum YellowAlignment {
        COERCED_HOSTILE,
        TRANSACTIONAL_NEUTRAL,
        LIBERATED_FRIENDLY
    }

    public enum FleetContactIntelLevel {
        UNKNOWN_CONTACT,
        ESTIMATED_SIZE,
        FACTION_THREAT,
        FORMATION_SILHOUETTES,
        FULL_IDENTIFICATION
    }

    public enum FleetFormationRole {
        FLAGSHIP,
        VANGUARD,
        SCREEN_LEFT,
        SCREEN_RIGHT,
        REARGUARD,
        CARRIER_CORE,
        SUPPORT_REAR,
        TRANSPORT_GROUP,
        MINER_GROUP,
        SCOUT_WING
    }

    enum CampaignFleetMission {
        PATROL,
        RECON,
        INTERCEPT,
        ESCORT,
        REINFORCE,
        RAID,
        REPAIR,
        CAPTURE,
        BLOCKADE,
        CONVOY,
        COUNTER_SORTIE
    }

    enum CampaignFleetState {
        IDLE,
        MOVING,
        SEARCHING,
        PURSUING,
        ENGAGING,
        RETREATING,
        DESTROYED
    }

    enum CampaignForceWorkState {
        TRAVELING,
        WORKING,
        REACTING,
        FIGHTING,
        RECOVERING,
        WAITING_WITH_PURPOSE
    }

    enum CampaignForceMissionState {
        ASSIGNED,
        TRAVELING,
        ARRIVED,
        WORKING,
        COMPLETED,
        FAILED,
        RETREATING,
        RECOVERING,
        REASSIGNING
    }

    enum CampaignForceStopReason {
        NONE,
        GUARDING,
        MINING,
        SALVAGING,
        REPAIRING,
        REFUELING,
        TRADING,
        LOADING,
        UNLOADING,
        SCANNING,
        HIDING,
        AMBUSHING,
        BLOCKADING,
        STAGING,
        WAITING_FOR_ESCORT,
        WAITING_FOR_REINFORCEMENTS,
        RECOVERING_SURVIVORS,
        RECOVERING,
        HOLDING_LINE,
        AVOIDING_SUPERIOR_THREAT
    }

    enum CampaignForceReassignmentCondition {
        NONE,
        WORK_COMPLETE,
        TARGET_DESTROYED,
        TARGET_MISSING,
        ROUTE_BLOCKED,
        THREAT_TOO_STRONG,
        LOW_FUEL,
        LOW_AMMO,
        LOW_REPAIR_CAPACITY,
        LOW_CREW_READINESS,
        CARGO_FULL,
        CARGO_EMPTY,
        TIMER_EXPIRED,
        DIRECTOR_RECALL
    }

    enum CampaignFleetTemplate {
        SCOUT,
        PATROL,
        INTERCEPTOR,
        CONVOY
    }

    enum CampaignForceCargoKind {
        NONE,
        ORE,
        SALVAGE,
        TRADE_GOODS,
        SUPPLIES,
        LOOT
    }

    enum CampaignShipPoolStatus {
        ACTIVE,
        DOCKED,
        DAMAGED,
        DESTROYED,
        UNDER_REPAIR,
        UNDER_CONSTRUCTION,
        RESERVE
    }

    enum CampaignBaseQueueType {
        REPAIR,
        CONSTRUCTION
    }

    static final class CampaignShipPoolRecord {
        final int id;
        final Faction faction;
        ShipRole role;
        CampaignShipPoolStatus status;
        String baseId;
        int forceId;
        double condition;
        String name;

        CampaignShipPoolRecord(int id,
                               Faction faction,
                               ShipRole role,
                               CampaignShipPoolStatus status,
                               String baseId,
                               int forceId,
                               double condition,
                               String name) {
            this.id = Math.max(1, id);
            this.faction = faction == null ? Faction.ALLY : faction;
            this.role = role == null ? ShipRole.PATROL : role;
            this.status = status == null ? CampaignShipPoolStatus.RESERVE : status;
            this.baseId = (baseId == null) ? "" : baseId.trim();
            this.forceId = Math.max(0, forceId);
            this.condition = MathUtil.clamp(condition, 0.0, 100.0);
            this.name = trimmedOrFallback(name, roleDisplayName(this.role));
        }
    }

    static final class CampaignBaseQueueEntry {
        final int id;
        final CampaignBaseQueueType type;
        final Faction faction;
        final ShipRole role;
        final String baseId;
        int shipRecordId;
        double remainingSec;
        int oreCost;
        int repairSupplyCost;

        CampaignBaseQueueEntry(int id,
                               CampaignBaseQueueType type,
                               Faction faction,
                               ShipRole role,
                               String baseId,
                               int shipRecordId,
                               double remainingSec,
                               int oreCost,
                               int repairSupplyCost) {
            this.id = Math.max(1, id);
            this.type = type == null ? CampaignBaseQueueType.CONSTRUCTION : type;
            this.faction = faction == null ? Faction.ALLY : faction;
            this.role = role == null ? ShipRole.PATROL : role;
            this.baseId = (baseId == null) ? "" : baseId.trim();
            this.shipRecordId = Math.max(0, shipRecordId);
            this.remainingSec = Math.max(0.0, remainingSec);
            this.oreCost = Math.max(0, oreCost);
            this.repairSupplyCost = Math.max(0, repairSupplyCost);
        }
    }

    static final class CampaignFactionDoctrine {
        final String label;
        final double defensiveBias;
        final double escortPriority;
        final double patrolPriority;
        final double cautiousPursuitLimit;
        final double rescuePriority;
        final double civilianRiskTolerance;
        final double profitPriority;
        final double fleePowerRatio;
        final double salvagePriority;
        final double aggressionBias;
        final double raidPriority;
        final double raidAttackRatio;
        final double retreatThreshold;

        CampaignFactionDoctrine(String label,
                                double defensiveBias,
                                double escortPriority,
                                double patrolPriority,
                                double cautiousPursuitLimit,
                                double rescuePriority,
                                double civilianRiskTolerance,
                                double profitPriority,
                                double fleePowerRatio,
                                double salvagePriority,
                                double aggressionBias,
                                double raidPriority,
                                double raidAttackRatio,
                                double retreatThreshold) {
            this.label = label == null ? "Unknown Doctrine" : label;
            this.defensiveBias = defensiveBias;
            this.escortPriority = escortPriority;
            this.patrolPriority = patrolPriority;
            this.cautiousPursuitLimit = cautiousPursuitLimit;
            this.rescuePriority = rescuePriority;
            this.civilianRiskTolerance = civilianRiskTolerance;
            this.profitPriority = profitPriority;
            this.fleePowerRatio = fleePowerRatio;
            this.salvagePriority = salvagePriority;
            this.aggressionBias = aggressionBias;
            this.raidPriority = raidPriority;
            this.raidAttackRatio = raidAttackRatio;
            this.retreatThreshold = retreatThreshold;
        }
    }

    static final CampaignFactionDoctrine GREEN_DOCTRINE = new CampaignFactionDoctrine(
            "Green defensive escort doctrine",
            0.82, 0.86, 0.78, 760.0, 0.88, 0.18,
            0.22, 0.92, 0.34, 0.42, 0.30, 0.72, 42.0);
    static final CampaignFactionDoctrine YELLOW_DOCTRINE = new CampaignFactionDoctrine(
            "Yellow profit-and-survival doctrine",
            0.28, 0.42, 0.36, 520.0, 0.30, 0.24,
            0.88, 1.05, 0.82, 0.26, 0.18, 0.62, 56.0);
    static final CampaignFactionDoctrine RED_DOCTRINE = new CampaignFactionDoctrine(
            "Red scout-and-raid doctrine",
            0.36, 0.20, 0.54, 1120.0, 0.16, 0.06,
            0.18, 0.72, 0.24, 0.90, 0.86, 0.82, 34.0);

    static final class CampaignFleetBase {
        final String id;
        final Faction faction;
        final double x;
        final double y;
        final int launchCapacity;
        double dispatchCooldownSec = 0.0;

        CampaignFleetBase(String id, Faction faction, double x, double y, int launchCapacity) {
            this.id = (id == null || id.isBlank()) ? "base" : id.trim();
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.launchCapacity = Math.max(1, launchCapacity);
        }
    }

    static final class PlayerContact {
        double x = Double.NaN;
        double y = Double.NaN;
        double confidence = 0.0;
        double timeSinceSeen = 9999.0;
        double searchRadius = 220.0;

        void clear() {
            x = Double.NaN;
            y = Double.NaN;
            confidence = 0.0;
            timeSinceSeen = 9999.0;
            searchRadius = 220.0;
        }
    }


    public static final class CampaignForceSummary {
        public final int id;
        public final CampaignForceKind kind;
        public final Faction faction;
        public final String name;
        public final String origin;
        public final String purpose;
        public final int shipCount;
        public final boolean hostile;
        public final CampaignForceIntent intent;
        public final double strength;
        public final double readiness;
        public final double supply;
        public final double x;
        public final double y;
        public final double targetX;
        public final double targetY;
        public final double contactConfidence;
        public final double uncertaintyRadius;
        public final double lastKnownAgeSec;
        public final double lastKnownVelocityX;
        public final double lastKnownVelocityY;
        public final double lastSeenSec;
        public final boolean visibleToPlayer;
        public final CampaignForceContactState contactState;
        public final CampaignContactCertainty contactCertainty;
        public final String mission;
        public final String homeBaseId;
        public final String destinationLocationId;
        public final String workState;
        public final String missionState;
        public final String stopReason;
        public final String reassignmentCondition;
        public final double stationaryTimeSec;
        public final double antiIdleTimerSec;
        public final double taskDeadlineSec;
        public final double workRemainingSec;
        public final double cargoLoad;
        public final double cargoCapacity;
        public final String cargoKind;
        public final double fuelLevel;
        public final double ammoLevel;
        public final double repairCapacity;
        public final double crewReadiness;
        public final double riskTolerance;
        public final double operatingRadius;
        public final String doctrineSummary;
        public final String because;
        public final String doing;
        public final String next;
        public final String statusLabel;
        public final String intelLabel;
        public final List<String> tooltipLines;

        CampaignForceSummary(CampaignForce force) {
            this(null, null, force);
        }

        CampaignForceSummary(GameContext ctx, CampaignState st, CampaignForce force) {
            this.id = force == null ? 0 : force.id;
            this.kind = force == null ? CampaignForceKind.LOCAL_FORCE : force.kind;
            this.faction = force == null ? null : force.faction;
            this.name = force == null ? "" : force.name;
            this.origin = force == null ? "" : force.origin;
            this.purpose = force == null ? "" : force.purpose;
            this.shipCount = force == null ? 0 : CampaignForceRosterSystem.concreteShipCount(ctx, st, force);
            this.hostile = force != null && force.faction == Faction.ENEMY;
            this.intent = force == null ? CampaignForceIntent.HOLDING : force.intent;
            this.strength = force == null ? 0.0 : force.strength;
            this.readiness = force == null ? 0.0 : force.readiness;
            this.supply = force == null ? 0.0 : force.supply;
            this.x = force == null ? 0.0 : force.x;
            this.y = force == null ? 0.0 : force.y;
            this.targetX = force == null ? 0.0 : force.targetX;
            this.targetY = force == null ? 0.0 : force.targetY;
            this.contactConfidence = force == null ? 0.0 : force.contactConfidence;
            this.uncertaintyRadius = force == null ? 0.0 : force.uncertaintyRadius;
            this.lastKnownAgeSec = force == null ? 0.0 : force.lastKnownAgeSec;
            this.lastKnownVelocityX = force == null ? 0.0 : force.lastKnownVelocityX;
            this.lastKnownVelocityY = force == null ? 0.0 : force.lastKnownVelocityY;
            this.lastSeenSec = force == null ? 0.0 : force.lastSeenSec;
            this.visibleToPlayer = force != null && force.visibleToPlayer;
            this.contactState = force == null ? CampaignForceContactState.STALE : force.contactState;
            this.contactCertainty = CampaignSystem.campaignContactCertainty(force);
            this.mission = force == null || force.mission == null ? "" : force.mission.name();
            this.homeBaseId = force == null ? "" : force.homeBaseId;
            this.destinationLocationId = force == null ? "" : force.destinationLocationId;
            this.workState = force == null || force.workState == null ? "" : force.workState.name();
            this.missionState = force == null || force.missionState == null ? "" : force.missionState.name();
            this.stopReason = force == null || force.stopReason == null ? "" : force.stopReason.name();
            this.reassignmentCondition = force == null || force.reassignmentCondition == null ? "" : force.reassignmentCondition.name();
            this.stationaryTimeSec = force == null ? 0.0 : force.stationaryTimeSec;
            this.antiIdleTimerSec = force == null ? 0.0 : force.antiIdleTimerSec;
            this.taskDeadlineSec = force == null ? 0.0 : force.taskDeadlineSec;
            this.workRemainingSec = force == null ? 0.0 : force.workRemainingSec;
            this.cargoLoad = force == null ? 0.0 : force.cargoLoad;
            this.cargoCapacity = force == null ? 0.0 : force.cargoCapacity;
            this.cargoKind = force == null || force.cargoKind == null ? "" : force.cargoKind.name();
            this.fuelLevel = force == null ? 0.0 : force.fuelLevel;
            this.ammoLevel = force == null ? 0.0 : force.ammoLevel;
            this.repairCapacity = force == null ? 0.0 : force.repairCapacity;
            this.crewReadiness = force == null ? 0.0 : force.crewReadiness;
            this.riskTolerance = force == null ? 0.0 : force.riskTolerance;
            this.operatingRadius = force == null ? 0.0 : force.operatingRadius;
            this.doctrineSummary = force == null ? "" : CampaignSystem.doctrineSummary(null, force);
            this.because = force == null ? "" : CampaignSystem.fleetBecauseLine(force);
            this.doing = force == null ? "" : CampaignSystem.fleetDoingLine(force);
            this.next = force == null ? "" : CampaignSystem.fleetNextLine(force);
            this.statusLabel = force == null ? "" : CampaignSystem.campaignForceCompactStatusLabel(null, force);
            this.intelLabel = force == null ? "" : CampaignSystem.campaignForceIntelDisplayLabel(force);
            this.tooltipLines = force == null ? List.of() : CampaignSystem.fleetTooltipLines(force);
        }
    }

    static final class CampaignForce {
        final int id;
        final CampaignForceKind kind;
        final Faction faction;
        final String name;
        final String origin;
        final String purpose;
        final Set<Integer> shipIds = new HashSet<>();
        double x;
        double y;
        double targetX;
        double targetY;
        double speed = 0.0;
        double strength = 100.0;
        double readiness = 100.0;
        double supply = 100.0;
        double morale = 100.0;
        double fuelPressure = 0.0;
        double hullIntegrity = 100.0;
        double intentTimerSec = 0.0;
        String sourceLocationId = "";
        String destinationLocationId = "";
        String homeBaseId = "";
        CampaignForceIntent intent = CampaignForceIntent.HOLDING;
        CampaignFleetMission mission = CampaignFleetMission.PATROL;
        CampaignFleetState state = CampaignFleetState.IDLE;
        CampaignFleetTemplate template = CampaignFleetTemplate.PATROL;
        CampaignForceWorkState workState = CampaignForceWorkState.WAITING_WITH_PURPOSE;
        CampaignForceMissionState missionState = CampaignForceMissionState.ASSIGNED;
        CampaignForceStopReason stopReason = CampaignForceStopReason.NONE;
        CampaignForceReassignmentCondition reassignmentCondition = CampaignForceReassignmentCondition.NONE;
        double stationaryTimeSec = 0.0;
        double antiIdleTimerSec = 0.0;
        double taskDeadlineSec = 0.0;
        double workRemainingSec = 0.0;
        double lastTaskUpdateSec = 0.0;
        double lastStopReasonChangeSec = 0.0;
        double lastLifecycleX = Double.NaN;
        double lastLifecycleY = Double.NaN;
        double lastAntiIdleResetX = Double.NaN;
        double lastAntiIdleResetY = Double.NaN;
        CampaignFleetMission lastAntiIdleMission = null;
        CampaignForceMissionState lastAntiIdleMissionState = null;
        CampaignForceWorkState lastAntiIdleWorkState = null;
        CampaignForceStopReason lastAntiIdleStopReason = null;
        int lastAntiIdleTargetForceId = -1;
        String lastAntiIdleDestinationId = "";
        double lastAntiIdleTargetX = Double.NaN;
        double lastAntiIdleTargetY = Double.NaN;
        double antiIdleReassignCooldownSec = 0.0;
        double cargoLoad = 0.0;
        double cargoCapacity = 0.0;
        CampaignForceCargoKind cargoKind = CampaignForceCargoKind.NONE;
        double fuelLevel = 100.0;
        double ammoLevel = 100.0;
        double repairCapacity = 100.0;
        double crewReadiness = 100.0;
        double riskTolerance = 50.0;
        double operatingRadius = 900.0;
        int linkedSearchGroupId = 0;
        boolean openingSpawnProtectionApplied = false;
        String assignedOperationId = "";
        int parentForceId = 0;
        int targetForceId = 0;
        int currentRouteIndex = 0;
        double deployedStrength = 0.0;
        double reportedSurvivingStrength = Double.NaN;
        double contactConfidence = 1.0;
        double uncertaintyRadius = 120.0;
        double stealthRating = 0.0;
        double lastKnownX;
        double lastKnownY;
        double lastKnownAgeSec = 0.0;
        double lastKnownVelocityX = 0.0;
        double lastKnownVelocityY = 0.0;
        double lastSeenSec = 0.0;
        boolean visibleToPlayer = true;
        boolean simulationActive = true;
        boolean protectedCivilianTraffic = false;
        boolean hadTacticalMembers = false;
        double rosterTransitionGraceSec = 0.0;
        String rosterTransitionReason = "";
        CampaignForceContactState contactState = CampaignForceContactState.KNOWN;
        final List<double[]> routePoints = new ArrayList<>();
        final List<double[]> patrolWaypoints = new ArrayList<>();
        final Map<Integer, NpcForceContact> knownHostileContacts = new HashMap<>();
        int patrolWaypointIndex = 0;
        double npcEngagementCooldownSec = 0.0;
        boolean destroyed = false;
        String removalReason = "";

        CampaignForce(int id, CampaignForceKind kind, Faction faction, String name, String origin, String purpose, double x, double y) {
            this.id = Math.max(1, id);
            this.kind = (kind == null) ? CampaignForceKind.LOCAL_FORCE : kind;
            this.faction = faction;
            this.name = (name == null || name.isBlank()) ? "Campaign Force" : name.trim();
            this.origin = (origin == null || origin.isBlank()) ? "Campaign theater" : origin.trim();
            this.purpose = (purpose == null || purpose.isBlank()) ? "Operating under campaign orders" : purpose.trim();
            this.x = x;
            this.y = y;
            this.targetX = x;
            this.targetY = y;
            this.lastKnownX = x;
            this.lastKnownY = y;
        }
    }

    static final int CAMPAIGN_AFTER_ACTION_HISTORY_CAP = 18;
    static final int CAMPAIGN_CAPTAIN_LOG_CAP = 36;
    static final int CAMPAIGN_MEMORY_FLAG_CAP = 64;

    static final class AfterActionReport {
        final int id;
        final String title;
        final String location;
        final String result;
        final String losses;
        final String resources;
        final String consequence;
        final String nextAction;
        final String whyThisMatters;
        final int theaterTick;

        AfterActionReport(int id,
                          String title,
                          String location,
                          String result,
                          String losses,
                          String resources,
                          String consequence,
                          String nextAction,
                          String whyThisMatters,
                          int theaterTick) {
            this.id = Math.max(1, id);
            this.title = trimmedOrFallback(title, "Battle Report");
            this.location = trimmedOrFallback(location, "Unknown Theater");
            this.result = trimmedOrFallback(result, "Outcome pending");
            this.losses = trimmedOrFallback(losses, "Losses pending");
            this.resources = trimmedOrFallback(resources, "Resource impact pending");
            this.consequence = trimmedOrFallback(consequence, "No strategic consequence recorded");
            this.nextAction = trimmedOrFallback(nextAction, "Return to campaign map");
            this.whyThisMatters = trimmedOrFallback(whyThisMatters, "This event changes the campaign picture.");
            this.theaterTick = Math.max(0, theaterTick);
        }

        String signature() {
            return title + "|" + location + "|" + result + "|" + consequence + "|" + theaterTick;
        }
    }

    static final class CampaignLogEntry {
        final int id;
        final String category;
        final String title;
        final String detail;
        final String consequence;
        final boolean major;
        final int theaterTick;

        CampaignLogEntry(int id,
                         String category,
                         String title,
                         String detail,
                         String consequence,
                         boolean major,
                         int theaterTick) {
            this.id = Math.max(1, id);
            this.category = trimmedOrFallback(category, "campaign");
            this.title = trimmedOrFallback(title, "Campaign event");
            this.detail = trimmedOrFallback(detail, "No detail recorded");
            this.consequence = trimmedOrFallback(consequence, "No consequence recorded");
            this.major = major;
            this.theaterTick = Math.max(0, theaterTick);
        }
    }

    static final class EncounterShipManifestEntry {
        final int shipRecordId;
        final ShipRole role;
        final String name;
        final FleetFormationRole formationRole;
        final double condition;
        final double armorCondition;
        final double crewReadiness;
        final double ammoLevel;
        final boolean retreatIntent;

        EncounterShipManifestEntry(ShipRole role, String name) {
            this(0, role, name, null, 100.0, 100.0, 100.0, 100.0, false);
        }

        EncounterShipManifestEntry(ShipRole role, String name, FleetFormationRole formationRole, double condition) {
            this(0, role, name, formationRole, condition, condition, 100.0, 100.0, false);
        }

        EncounterShipManifestEntry(int shipRecordId,
                                   ShipRole role,
                                   String name,
                                   FleetFormationRole formationRole,
                                   double condition,
                                   double armorCondition,
                                   double crewReadiness,
                                   double ammoLevel,
                                   boolean retreatIntent) {
            this.shipRecordId = Math.max(0, shipRecordId);
            this.role = role == null ? ShipRole.PATROL : role;
            this.name = (name == null || name.isBlank()) ? roleDisplayName(this.role) : name.trim();
            this.formationRole = formationRole == null ? CampaignSystem.fleetFormationRole(this.role, 0, CampaignForceKind.LOCAL_FORCE) : formationRole;
            this.condition = MathUtil.clamp(condition, 0.0, 100.0);
            this.armorCondition = MathUtil.clamp(armorCondition, 0.0, 100.0);
            this.crewReadiness = MathUtil.clamp(crewReadiness, 0.0, 100.0);
            this.ammoLevel = MathUtil.clamp(ammoLevel, 0.0, 100.0);
            this.retreatIntent = retreatIntent;
        }
    }

    static final class EncounterForceManifest {
        final int forceId;
        final CampaignForceKind kind;
        final Faction faction;
        final String name;
        final String purpose;
        final List<EncounterShipManifestEntry> ships = new ArrayList<>();

        EncounterForceManifest(int forceId,
                               CampaignForceKind kind,
                               Faction faction,
                               String name,
                               String purpose) {
            this.forceId = Math.max(0, forceId);
            this.kind = kind == null ? CampaignForceKind.LOCAL_FORCE : kind;
            this.faction = faction;
            this.name = (name == null || name.isBlank()) ? "Encounter Force" : name.trim();
            this.purpose = (purpose == null || purpose.isBlank()) ? "Commit campaign force ships into battle" : purpose.trim();
        }
    }

    enum CoalitionSupportReason {
        PLAYER_REQUEST,
        SELECTED_PARTICIPANT,
        NEARBY_RESPONSE,
        AUTHORED_MISSION
    }

    static final class CoalitionParticipation {
        final int sourceForceId;
        final Faction faction;
        final CoalitionSupportReason reason;
        final Set<String> committedCampaignShipKeys = new HashSet<>();
        final Set<Integer> tacticalShipIds = new HashSet<>();
        int requestedShipCount;
        int spawnedShipCount;
        String sourceForceName = "";

        CoalitionParticipation(int sourceForceId,
                               Faction faction,
                               CoalitionSupportReason reason,
                               int requestedShipCount,
                               String sourceForceName) {
            this.sourceForceId = Math.max(0, sourceForceId);
            this.faction = faction;
            this.reason = reason == null ? CoalitionSupportReason.NEARBY_RESPONSE : reason;
            this.requestedShipCount = Math.max(0, requestedShipCount);
            this.sourceForceName = trimmedOrFallback(sourceForceName, "Coalition support");
        }
    }

    static final class CampaignForceSpawnContext {
        final CampaignForceKind kind;
        final Faction faction;
        final String name;
        final String origin;
        final String purpose;

        CampaignForceSpawnContext(CampaignForceKind kind,
                                  Faction faction,
                                  String name,
                                  String origin,
                                  String purpose) {
            this.kind = (kind == null) ? CampaignForceKind.LOCAL_FORCE : kind;
            this.faction = faction;
            this.name = (name == null || name.isBlank()) ? "Campaign Force" : name.trim();
            this.origin = (origin == null || origin.isBlank()) ? "Campaign theater" : origin.trim();
            this.purpose = (purpose == null || purpose.isBlank()) ? "Operating under campaign orders" : purpose.trim();
        }
    }

    enum DivisionStance {
        RESERVE,
        LINE,
        STRIKE,
        ESCORT,
        SCOUT
    }

    static final class StrategicDivisionState {
        final int groupId;
        DivisionStance stance;
        int currentSubzone;
        int targetSubzone;
        double transitRemainingSec;
        double lastOrderX = Double.NaN;
        double lastOrderY = Double.NaN;

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


    public static final class CampaignFleetRosterEntry {
        public final int slotId;
        public final ShipRole role;
        public final String name;
        public final String roleLabel;
        public final String groupLabel;
        public final String commitmentLabel;
        public final String readinessLabel;
        public final String forceLabel;
        public final String cargoLabel;
        public final String unavailableReason;
        public final String identityLabel;
        public final String configurationLabel;
        public final String personnelLabel;
        public final int oreCost;
        public final double hullFraction;
        public final double armorFraction;
        public final double shieldFraction;
        public final boolean selected;
        public final boolean editable;

        CampaignFleetRosterEntry(CampaignState st, PersistentFleetEntry entry, boolean selected) {
            this.slotId = (entry == null) ? -1 : entry.slotId;
            this.role = (entry == null || entry.role == null) ? ShipRole.FRIGATE : entry.role;
            this.name = CampaignSystem.displayPersistentFleetEntryName(entry);
            this.roleLabel = roleDisplayName(this.role).toUpperCase(Locale.US);
            this.groupLabel = (entry == null || entry.commandGroupId == CampaignSystem.CAMPAIGN_FLAGSHIP_COMMAND_GROUP)
                    ? "FLAG GROUP"
                    : ("GROUP " + entry.commandGroupId);
            this.commitmentLabel = CampaignSystem.fleetCommitmentLabel(CampaignSystem.resolveFleetCommitment(entry == null ? "" : entry.tacticalCommitmentId));
            this.hullFraction = MathUtil.clamp(entry == null ? 1.0 : entry.hullConditionFrac, 0.0, 1.0);
            this.armorFraction = MathUtil.clamp(entry == null ? 1.0 : entry.armorConditionFrac, 0.0, 1.0);
            this.shieldFraction = MathUtil.clamp(entry == null ? 1.0 : entry.shieldConditionFrac, 0.0, 1.0);
            this.oreCost = CampaignSystem.campaignOreCost(this.role, CampaignSystem.shipyardOfferCreditCost(this.role), CampaignSystem.campaignRequiredTier(this.role, 1));
            this.selected = selected;
            this.editable = entry != null && !entry.destroyed;
            this.forceLabel = campaignForceLabelForPersistentEntry(st, entry);
            this.cargoLabel = campaignCargoLabelForPersistentEntry(entry);
            String absence = (entry == null || entry.tacticalAbsenceReason == null) ? "" : entry.tacticalAbsenceReason.trim();
            this.unavailableReason = entry == null || entry.destroyed
                    ? "destroyed"
                    : absence;
            FleetBuildingSystem.HullProfile profile = FleetBuildingSystem.hullProfile(this.role);
            this.identityLabel = profile.battlefieldRole + "  |  counters " + profile.counter
                    + "  |  weak to " + profile.weakness;
            this.configurationLabel = persistentFleetConfigurationLabel(profile);
            this.personnelLabel = persistentFleetPersonnelLabel(entry);
            String condition = (hullFraction >= 0.86 && shieldFraction >= 0.76)
                    ? "READY"
                    : (hullFraction < 0.44 || shieldFraction < 0.28 ? "UNREADY" : "STRAINED");
            this.readinessLabel = "COMBAT CONDITION " + condition
                    + " H" + (int) Math.round(hullFraction * 100.0)
                    + " A" + (int) Math.round(armorFraction * 100.0)
                    + " S" + (int) Math.round(shieldFraction * 100.0);
        }
    }

    public static final class CampaignContactReadout {
        public final String title;
        public final String detail;
        public final double x;
        public final double y;
        public final Color accent;
        public final String banner;

        CampaignContactReadout(String title, String detail, double x, double y, Color accent, String banner) {
            this.title = (title == null || title.isBlank()) ? "Unknown Contact" : title.trim();
            this.detail = (detail == null) ? "" : detail.trim();
            this.x = x;
            this.y = y;
            this.accent = (accent == null) ? new Color(132, 220, 255) : accent;
            this.banner = (banner == null || banner.isBlank()) ? "TRACK SET: " + this.title : banner.trim();
        }
    }

    public static final class CampaignInterceptLine {
        public final int forceId;
        public final String label;
        public final double fromX;
        public final double fromY;
        public final double toX;
        public final double toY;

        CampaignInterceptLine(int forceId, String label, double fromX, double fromY, double toX, double toY) {
            this.forceId = Math.max(0, forceId);
            this.label = (label == null || label.isBlank()) ? "Hostile Intercept" : label.trim();
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    static final class PersistentFleetEntry {
        final int slotId;
        final ShipRole role;
        String name;
        String factionName = Faction.ALLY.name();
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
        double hullConditionFrac = 1.0;
        double armorConditionFrac = 1.0;
        double shieldConditionFrac = 1.0;
        double relX = Double.NaN;
        double relY = Double.NaN;
        double relAngle = Double.NaN;
        String tacticalCommitmentId = FleetCommitment.AUTO.name();
        String tacticalAbsenceReason = "";
        String captainName;
        String captainPersonalityId;
        String crewSpecializationId = FleetBuildingSystem.CrewSpecialization.DAMAGE_CONTROL.name();
        int crewExperience = 0;
        int morale = 70;
        int refusalRisk = 0;
        int kills = 0;
        int rescues = 0;
        int retreats = 0;
        int scars = 0;
        String commendations = "";
        String serviceHistory = "";
        String refitTemplateName = "Standard";

        PersistentFleetEntry(int slotId, ShipRole role, String name) {
            this.slotId = Math.max(1, slotId);
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.name = (name == null || name.isBlank()) ? ("Blue Wing " + this.slotId) : name;
            this.captainName = "Captain " + generatedCaptainName(this.slotId);
            this.captainPersonalityId = FleetBuildingSystem.CaptainPersonality.values()[
                    Math.floorMod(this.slotId - 1, FleetBuildingSystem.CaptainPersonality.values().length)].name();
        }
    }

    public enum CampaignYardOrderKind {
        CONSTRUCTION,
        REFIT
    }

    public enum CampaignProductionLane {
        ESCORT,
        FRIGATE_DESTROYER,
        CRUISER,
        CAPITAL,
        TITAN_SPECIAL
    }

    public static final class CampaignYardOrder {
        public final int id;
        public final CampaignYardOrderKind kind;
        public final ShipRole role;
        public final CampaignProductionLane lane;
        public final Faction producingFaction;
        public final int fleetSlotId;
        public final String sourceLocationId;
        public final String sourceLabel;
        public final String templateName;
        public final int creditCost;
        public final int oreCost;
        public final int salvageCost;
        public final double totalSeconds;
        public double remainingSeconds;

        CampaignYardOrder(int id,
                          CampaignYardOrderKind kind,
                          ShipRole role,
                          Faction producingFaction,
                          int fleetSlotId,
                          String sourceLocationId,
                          String sourceLabel,
                          String templateName,
                          int creditCost,
                          int oreCost,
                          int salvageCost,
                          double totalSeconds) {
            this.id = Math.max(1, id);
            this.kind = (kind == null) ? CampaignYardOrderKind.CONSTRUCTION : kind;
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.lane = CampaignSystem.campaignProductionLane(this.role);
            this.producingFaction = producingFaction == null ? Faction.ALLY : producingFaction;
            this.fleetSlotId = Math.max(0, fleetSlotId);
            this.sourceLocationId = trimmedOrFallback(sourceLocationId, "");
            this.sourceLabel = trimmedOrFallback(sourceLabel, "Frontier Yard");
            this.templateName = trimmedOrFallback(templateName, "Standard");
            this.creditCost = Math.max(0, creditCost);
            this.oreCost = Math.max(0, oreCost);
            this.salvageCost = Math.max(0, salvageCost);
            this.totalSeconds = Math.max(1.0, totalSeconds);
            this.remainingSeconds = this.totalSeconds;
        }
    }

    private static String generatedCaptainName(int slotId) {
        String[] given = {"Mira", "Tarin", "Sera", "Ilex", "Nadi", "Venn", "Rook", "Aster"};
        String[] family = {"Vale", "Morrow", "Kest", "Orin", "Dax", "Sol", "Reyes", "Ward"};
        int index = Math.max(1, slotId) - 1;
        return given[index % given.length] + " " + family[(index / given.length + index) % family.length];
    }

    static String persistentFleetPersonnelLabel(PersistentFleetEntry entry) {
        if (entry == null) return "CREW RECORD UNAVAILABLE";
        FleetBuildingSystem.CaptainPersonality personality = CampaignCodec.parseEnum(
                entry.captainPersonalityId, FleetBuildingSystem.CaptainPersonality.STEADY);
        return entry.captainName + "  |  " + personality.name().replace('_', ' ')
                + "  |  XP " + Math.max(0, entry.crewExperience)
                + "  MORALE " + MathUtil.clamp(entry.morale, 0, 100)
                + "  " + FleetBuildingSystem.disciplineRiskLabel(entry.morale, entry.refusalRisk)
                + (entry.refusalRisk > 0 ? " " + MathUtil.clamp(entry.refusalRisk, 0, 100) + "%" : "");
    }

    static String persistentFleetConfigurationLabel(FleetBuildingSystem.HullProfile profile) {
        if (profile == null) return "PROFILE UNAVAILABLE";
        return "MAINT " + profile.budgets.maintenance
                + "  |  " + profile.factionVariant
                + "  |  " + profile.silhouetteCheck;
    }

    static String campaignCargoLabelForPersistentEntry(PersistentFleetEntry entry) {
        if (entry == null) return "CARGO 0";
        if (entry.cargoMax > 0) {
            return "CARGO " + Math.max(0, entry.cargo) + "/" + Math.max(0, entry.cargoMax);
        }
        return "CARGO " + Math.max(0, entry.cargo);
    }

    static String campaignForceLabelForPersistentEntry(CampaignState st, PersistentFleetEntry entry) {
        if (st == null || entry == null) return "FORCE Blue Command Fleet";
        Integer forceId = entry.activeShipId > 0 ? st.shipCampaignForceIds.get(entry.activeShipId) : null;
        CampaignForce force = (forceId == null) ? null : CampaignSystem.campaignForceById(st, forceId);
        String name = (force == null || force.name == null || force.name.isBlank()) ? "Blue Command Fleet" : force.name;
        return "FORCE " + name;
    }

    static final int AUTHORED_VERTICAL_SLICE_LAST_SECTOR = 2;
    static final int CAMPAIGN_STARTING_CREDITS = 1000;
    static final int CAMPAIGN_BLUE_ESCORT_CAP = 15;
    static final int CAMPAIGN_BLUE_LINE_CAP = 11;
    static final int CAMPAIGN_BLUE_CAPITAL_CAP = 7;
    static final int CAMPAIGN_ESCORT_CAP_UPGRADE_STEP = 2;
    static final int CAMPAIGN_LINE_CAP_UPGRADE_STEP = 1;
    static final int CAMPAIGN_CAPITAL_CAP_UPGRADE_STEP = 1;
    static final int CAMPAIGN_ESCORT_CAP_UPGRADE_MAX_LEVEL = 5;
    static final int CAMPAIGN_LINE_CAP_UPGRADE_MAX_LEVEL = 4;
    static final int CAMPAIGN_CAPITAL_CAP_UPGRADE_MAX_LEVEL = 3;
    static final int CAMPAIGN_PLAYER_STARTING_HANGAR_TIER = 1;
    static final int CAMPAIGN_PLAYER_MAX_HANGAR_TIER = 5;
    static final int CAMPAIGN_ENEMY_MAX_HANGAR_TIER = 3;

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
        public double nextTacticalOreAsteroidAtSec = 0.0;
        public boolean objectiveSecured = false;
        public double extractionMinHoldSeconds = 200.0;

        public int kills = 0;
        public int lastDetectedKillCount = 0;
        public final Set<Integer> knownHostiles = new HashSet<>();
        public final Set<Integer> knownBlueShips = new HashSet<>();
        public final Set<Integer> knownGreenShips = new HashSet<>();
        public final Set<Integer> knownYellowShips = new HashSet<>();
        public final Set<Integer> knownRedShips = new HashSet<>();
        public int blueShipLosses = 0;
        public int greenShipLosses = 0;
        public int yellowShipLosses = 0;
        public int redShipLosses = 0;
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
        public int loadedMissionSubzone = CampaignSystem.missionSubzoneIndex(0, 1);
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
        public boolean protectFlagshipObjectiveActive = false;
        public int protectFlagshipStartHp = 0;
        public double protectFlagshipMinHullFrac = 0.35;
        public boolean emergencyExtractionObjectiveActive = false;
        public int emergencyExtractionShipId = -1;
        public double emergencyExtractionProgressSec = 0.0;
        public double emergencyExtractionGoalSec = 8.0;
        public double emergencyExtractionRadius = 320.0;
        public boolean salvageUnderFireObjectiveActive = false;
        public double salvageUnderFireGoalSec = 9.0;
        public boolean convoyLaneDefenseObjectiveActive = false;
        public boolean civilianTrafficConstraintActive = false;
        public boolean minefieldBreachObjectiveActive = false;
        public final List<double[]> minefieldBreachRoutePoints = new ArrayList<>();
        public int minefieldBreachRouteIndex = 0;
        public double minefieldBreachProgressSec = 0.0;
        public double minefieldBreachHoldSec = 3.0;
        public double minefieldBreachRadius = 210.0;
        public boolean pursuitObjectiveActive = false;
        public int pursuitTargetShipId = -1;
        public double pursuitEscapeX = 0.0;
        public double pursuitEscapeY = 0.0;
        public double pursuitEscapeRadius = 280.0;
        public double pursuitDisableProgressSec = 0.0;
        public double pursuitDisableHoldSec = 2.0;
        public boolean retreatCorridorObjectiveActive = false;
        public double retreatCorridorX = 0.0;
        public double retreatCorridorY = 0.0;
        public double retreatCorridorRadius = 260.0;
        public double retreatCorridorProgressSec = 0.0;
        public double retreatCorridorHoldSec = 4.0;
        public boolean holdFireNearCiviliansObjectiveActive = false;
        public double holdFireCivilianRadius = 520.0;
        public int holdFireCivilianViolations = 0;
        public boolean disableSalvageTargetObjectiveActive = false;
        public int disableSalvageTargetShipId = -1;
        public boolean boardingCaptureObjectiveActive = false;
        public int boardingCaptureTargetShipId = -1;
        public double boardingCaptureProgressSec = 0.0;
        public double boardingCaptureGoalSec = 5.0;
        public double boardingCaptureRadius = 260.0;

        public double transitionTimer = 0.0;
        public String transitionLabel = "";
        public long sectorStartMillis = 0L;
        public String transitionSummaryTop = "";
        public String transitionSummaryBottom = "";
        public String transitionRewardLine = "";
        public String transitionRouteImpactLine = "";
        public String selectedFleetPostureId = "";
        public String selectedSiteResolutionModeId = "";
        public String activeSiteResolutionModeId = "";
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
        public int environmentHazardPulseIndex = -1;

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
        public final List<CampaignSystem.PersistentFleetEntry> persistentBlueFleet = new ArrayList<>();
        public int nextPersistentFleetSlotId = 1;
        public final List<CampaignSystem.CampaignYardOrder> campaignYardOrders = new ArrayList<>();
        public int nextCampaignYardOrderId = 1;
        public String selectedStrategicOverlayId = StrategicCampaignExpansionSystem.MapOverlay.CONTROL.name();
        public FactionAttackCommitmentSystem.State factionAttackCommitments = new FactionAttackCommitmentSystem.State();
        public boolean expandedTerritoryDetails = true;
        public int selectedCampaignTaskGroupId = 0;
        public int escortCapUpgradeLevel = 0;
        public int lineCapUpgradeLevel = 0;
        public int capitalCapUpgradeLevel = 0;
        public boolean awaitingEpisodeLaunch = false;
        public int pendingEpisodeSector = 0;
        public int routeArrivalSourceSector = 0;
        public final List<CampaignSystem.CampaignRouteChoice> routeChoices = new ArrayList<>();
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
        public double fleetStrain = 18.0;
        public String vossRelationshipStateId = "";
        public String marrRelationshipStateId = "";
        public String rookRelationshipStateId = "";

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
        public boolean strategicOvermapMode = false;
        public boolean commandSchoolTraining = false;
        public String commandSchoolLastActionId = "";
        public boolean facilityFleetGenerationActive = false;
        public boolean strategicBootstrapLocked = false;
        public final List<CampaignLocation> galaxyMainPois = new ArrayList<>();
        public final List<CampaignLocation> galaxyAreasOfInterest = new ArrayList<>();
        public final List<CampaignTheaterState> campaignTheaters = new ArrayList<>();
        public final List<StrategicNodeState> strategicNodes = new ArrayList<>();
        public final List<String> theaterWarRecentEvents = new ArrayList<>();
        public final List<CampaignSystem.AfterActionReport> campaignAfterActionReports = new ArrayList<>();
        public final List<CampaignSystem.CampaignLogEntry> campaignCaptainLog = new ArrayList<>();
        public final java.util.LinkedHashSet<String> campaignMemoryFlags = new java.util.LinkedHashSet<>();
        public final java.util.LinkedHashMap<Integer, CampaignShipPoolRecord> campaignShipPool = new java.util.LinkedHashMap<>();
        public final java.util.HashMap<Integer, Integer> tacticalShipPoolRecordIds = new java.util.HashMap<>();
        public final List<CampaignBaseQueueEntry> campaignBaseQueues = new ArrayList<>();
        public int nextCampaignShipRecordId = 1;
        public int nextCampaignBaseQueueId = 1;
        public boolean campaignFiniteEconomyInitialized = false;
        public double campaignEconomyTickAccumulatorSec = 0.0;
        public int nextAfterActionReportId = 1;
        public int nextCampaignLogEntryId = 1;
        public final java.util.LinkedHashSet<String> completedCampaignBoardMissionIds = new java.util.LinkedHashSet<>();
        public final java.util.LinkedHashSet<String> expiredCampaignBoardMissionIds = new java.util.LinkedHashSet<>();
        public List<CampaignRouteSegment> cachedCampaignRouteSegments = List.of();
        public String cachedCampaignRouteSegmentKey = "";
        public double theaterWarTickAccumulatorSec = 0.0;
        public int theaterWarTickIndex = 0;
        public String selectedTheaterId = "";
        public String lastTheaterOperationBrief = "";
        public String lastTheaterOperationDebrief = "";
        public String lastTransitEncounterDebrief = "";
        public String lastTransitStoryLine = "";
        public String lastRegionalEventLine = "";
        public String lastRouteTrafficPatternLine = "";
        public String lastContactChainLine = "";
        public String activeContactChainLabel = "";
        public int activeContactChainStage = 0;
        public int activeContactChainJumpsRemaining = 0;
        public int transitVariationSerial = 0;
        public String lastTrafficAuditSummary = "";
        public String lastFalsePositiveContactSummary = "";
        public String lastContactScanSummary = "";
        public double lastSensorSweepAtSec = -1000.0;
        public int lastTheaterOperationTick = -1;
        public double blueInterventionReserve = 100.0;
        public int earthOperationStage = 0;
        public boolean redGlobalCollapseActive = false;
        public final CampaignTravelState galaxyTravel = new CampaignTravelState();
        public String currentGalaxyLocationId = "";
        public String selectedGalaxyLocationId = "";
        public String activeGalaxyEncounterLocationId = "";
        public int activeGalaxyEncounterSearchGroupId = 0;
        public final java.util.LinkedHashSet<Integer> activeGalaxyEncounterForceIds = new java.util.LinkedHashSet<>();
        public int activeGalaxyEncounterParentForceId = 0;
        public int completedMainMissions = 0;
        public double earthProgress = 0.0;
        public double enemyAlertLevel = 0.0;
        public double campaignIntelLevel = 28.0;
        public double strategicExposureLevel = 0.0;
        public double recentStrikePressure = 0.0;
        public String lastStrikeReportTitle = "";
        public String lastStrikeReportDetail = "";
        public boolean galaxyEncounterActive = false;
        public boolean galaxyAmbientEncounterActive = false;
        public int activeInstallationThreatCaseId = 0;
        public boolean galaxyAmbientSupportRequested = false;
        public final Set<Integer> galaxyAmbientHiredShipIds = new HashSet<>();
        public final List<CoalitionParticipation> activeCoalitionParticipations = new ArrayList<>();
        public double galaxyAmbientPocketCenterX = Double.NaN;
        public double galaxyAmbientPocketCenterY = Double.NaN;
        public double galaxyAmbientPocketRadius = 0.0;
        public int campaignFuel = 120;
        public int campaignSupplies = 90;
        public int campaignAmmo = 110;
        public int campaignSalvage = 35;
        public boolean transportRepairSupportActive = false;
        public int transportRepairSupportShips = 0;
        public double transportRepairSupplyRemainder = 0.0;
        public double travelFuelAttritionRemainder = 0.0;
        public double travelSupplyAttritionRemainder = 0.0;
        public double travelAmmoAttritionRemainder = 0.0;
        public double playerGalaxyX = Double.NaN;
        public double playerGalaxyY = Double.NaN;
        public double playerGalaxyHeadingDeg = -90.0;
        public String dockedGalaxyLocationId = "";
        public double selectedFreeGalaxyTargetX = Double.NaN;
        public double selectedFreeGalaxyTargetY = Double.NaN;
        public final List<CampaignMapBookmark> campaignMapBookmarks = new ArrayList<>();
        public final List<CampaignRouteQueueStop> campaignRouteQueue = new ArrayList<>();
        public int selectedCampaignMapBookmarkIndex = -1;
        public double transitEventCooldownSec = 4.0;
        public double transitEncounterPressure = 0.0;
        public double transitNextEncounterThreshold = 6.0;
        public int transitContactEventsThisLeg = 0;
        public int transitContactTargetThisLeg = 0;
        public int transientGalaxySiteSerial = 0;
        public final List<GalaxySearchGroup> galaxySearchGroups = new ArrayList<>();
        public int nextGalaxySearchGroupId = 1;
        public final List<PendingHostileReinforcement> pendingHostileReinforcements = new ArrayList<>();
        public int nextPendingHostileReinforcementId = 1;
        public final List<CampaignBattle> campaignBattles = new ArrayList<>();
        public int nextCampaignBattleId = 1;
        public final List<CampaignInstallationThreatCase> installationThreatCases = new ArrayList<>();
        public int nextInstallationThreatCaseId = 1;
        public final List<StrategicTaskForce> strategicTaskForces = new ArrayList<>();
        public final java.util.LinkedHashSet<String> defeatedStrategicTaskForceKeys = new java.util.LinkedHashSet<>();
        public int nextStrategicTaskForceId = 1;
        public final List<CampaignSystem.CampaignForce> campaignForces = new ArrayList<>();
        public final java.util.LinkedHashSet<String> defeatedCampaignForceKeys = new java.util.LinkedHashSet<>();
        public final Map<Integer, Integer> shipCampaignForceIds = new HashMap<>();
        public final Map<Integer, String> shipCampaignSpawnCategories = new HashMap<>();
        public final Set<Integer> fallbackOwnedShipIds = new HashSet<>();
        public final List<String> campaignForceAuditWarnings = new ArrayList<>();
        public final java.util.LinkedHashSet<String> processedResourceTransactionIds = new java.util.LinkedHashSet<>();
        public int nextCampaignForceId = 1;
        public long campaignIntelTick = 0L;
        public boolean campaignCommunicationsJammed = false;
        public final java.util.LinkedHashMap<Integer, CampaignFleetIntelRecord> campaignFleetIntel =
                new java.util.LinkedHashMap<>();
        public final java.util.LinkedHashMap<String, CampaignOperationIntelRecord> campaignOperationIntel =
                new java.util.LinkedHashMap<>();
        public int lastChecklistFleetSeedSector = 0;
        public String activeCampaignSpawnCategory = "";
        public CampaignSystem.CampaignForceSpawnContext activeCampaignForceContext = null;
        public final List<StrategicStrikeObject> strategicStrikeObjects = new ArrayList<>();
        public int nextStrategicStrikeObjectId = 1;
        public int strategicTorpedoCharges = CampaignSystem.STARTING_TORPEDO_INVENTORY;
        public int strategicSortiesLaunched = 0;
        public int strategicAtomicCharges = CampaignSystem.STARTING_ATOMIC_INVENTORY;
        public double torpedoStrikeCooldownSec = 0.0;
        public double carrierSortieCooldownSec = 0.0;
        public double atomicStrikeCooldownSec = 0.0;
        public final List<Integer> pendingReserveReinforcementSlots = new ArrayList<>();
        public double reserveReinforcementTimerSec = 0.0;
        public final java.util.Map<Integer, CampaignSystem.StrategicDivisionState> strategicDivisions = new java.util.LinkedHashMap<>();
        public final List<SensorRelayNode> sensorRelayNodes = new ArrayList<>();
        public int nextSensorRelayId = 1;
        public final PlayerContact enemyPlayerContact = new PlayerContact();
        public double campaignForceSimAccumulatorSec = 0.0;
        public int campaignForceSimTickCount = 0;
        public double overmapGhostFleetSweepAccumulatorSec = 0.0;
        public int proximityAlertForceId = 0;
        public int proximityAlertStage = 0;
        public double proximityAlertCooldownSec = 0.0;
        public boolean manualEncounterCommitInProgress = false;
        public double factionDirectorAccumulatorSec = 0.0;
        public String redDirectorBrief = "Red director awaiting theater picture";
        public String greenDirectorBrief = "Green director awaiting theater picture";
        public String yellowDirectorBrief = "Yellow director awaiting theater picture";
        public String saveRecoveryMessage = "";
        public StrategicCampaignExpansionSystem.State strategicExpansion = StrategicCampaignExpansionSystem.bootstrap(0L);
        public long strategicProjectionFingerprint = Long.MIN_VALUE;
        public int strategicProjectionBuildCount;
        public EconomyLogisticsIndustrySystem.State economyExpansion = EconomyLogisticsIndustrySystem.bootstrap(0L);
        public DiplomacyNarrativeCrewSystem.State diplomacyNarrative = DiplomacyNarrativeCrewSystem.bootstrap(0L);
        public OperationsInformationCommandSystem.State operationsExpansion = OperationsInformationCommandSystem.bootstrap(0L);
        public FlagshipOperationsSystem.State flagshipOperations = FlagshipOperationsSystem.bootstrap();
        public BoardingRescueSystem.State boardingRescue = BoardingRescueSystem.bootstrap();
        public AlternativeCampaignSystem.State alternativeCampaign = AlternativeCampaignSystem.bootstrap();
        public CooperativeCommandSystem.State cooperativeCommand = CooperativeCommandSystem.bootstrap();
        public WarMemorySystem.State warMemory = WarMemorySystem.bootstrap();
        public ProductionReadinessLongevitySystem.State productionReadiness = ProductionReadinessLongevitySystem.bootstrap(0L);
        public StretchGoalsFleetDoctrineSystem.State fleetDoctrineExpansion = StretchGoalsFleetDoctrineSystem.bootstrap(0L);
        public DeepCampaignSimulationSystem.State deepCampaignExpansion = DeepCampaignSimulationSystem.bootstrap(0L);
        public CommunityContentSystem.State communityContent = CommunityContentSystem.bootstrap(0L);
        public boolean strikeCinematicActive = false;
        public String strikeCinematicType = "";
        public String strikeCinematicSourceLabel = "";
        public String strikeCinematicTargetLabel = "";
        public double strikeCinematicTimer = 0.0;
        public double strikeCinematicSourceX = Double.NaN;
        public double strikeCinematicSourceY = Double.NaN;
        public double strikeCinematicTargetX = Double.NaN;
        public double strikeCinematicTargetY = Double.NaN;
        public double strikeCinematicPayloadX = Double.NaN;
        public double strikeCinematicPayloadY = Double.NaN;
        public double strikeCinematicBlastRadius = 0.0;
        public double strikeCinematicDamageScale = 0.0;
        public boolean strikeCinematicAtomic = false;
        public boolean strikeCinematicDestroyedTarget = false;
        public boolean strikeBattleEventActive = false;
        public String strikeBattleEventType = "";
        public String strikeBattleEventTarget = "";
        public String strikeBattleEventResolution = "";
        public final java.util.Map<Integer, Double> tacticalStrikeBomberEgressTimers = new java.util.HashMap<>();
        public final java.util.Map<Integer, Integer> tacticalStrikeBomberTargetIds = new java.util.HashMap<>();
        public final java.util.Map<Integer, Integer> tacticalStrikeBomberPayloadsRemaining = new java.util.HashMap<>();
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

    enum BranchOutcome {
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


    static String trimmedOrFallback(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        return text.trim();
    }

    static String roleDisplayName(ShipRole role) {
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
}
