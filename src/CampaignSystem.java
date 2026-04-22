import app.config.GameMode;
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

    // Zone layout constants
    private static final double ZONE_WIDTH = 4000.0;
    private static final double ZONE_HEIGHT = 3000.0;
    private static final double ZONE_GAP_DISTANCE = 5000.0;
    private static final int ZONES_PER_ROW = 8;

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

        CampaignLandmark(LandmarkType type,
                         String label,
                         String subtitle,
                         double x,
                         double y,
                         double radius,
                         Color fillColor,
                         Color edgeColor) {
            this.type = (type == null) ? LandmarkType.COLONY : type;
            this.label = (label == null) ? "" : label;
            this.subtitle = (subtitle == null) ? "" : subtitle;
            this.x = x;
            this.y = y;
            this.radius = Math.max(40.0, radius);
            this.fillColor = (fillColor == null) ? new Color(120, 170, 220, 46) : fillColor;
            this.edgeColor = (edgeColor == null) ? new Color(200, 225, 255, 180) : edgeColor;
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
        String turretData = "";
        String primaryWeaponFamilyName = Ship.PrimaryWeaponFamily.ENERGY_BOLT.name();

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
    private static double getZoneX(int sector) {
        int col = (sector - 1) % ZONES_PER_ROW;
        return col * (ZONE_WIDTH + ZONE_GAP_DISTANCE);
    }

    private static double getZoneY(int sector) {
        int row = (sector - 1) / ZONES_PER_ROW;
        return row * (ZONE_HEIGHT + ZONE_GAP_DISTANCE);
    }

    private static double getZoneCenterX(int sector) {
        return getZoneX(sector) + ZONE_WIDTH / 2;
    }

    private static double getZoneCenterY(int sector) {
        return getZoneY(sector) + ZONE_HEIGHT / 2;
    }

    private static boolean canWarpBetweenZones(int sourceSector, int targetSector) {
        if (sourceSector == targetSector) return false;
        int sourceRow = (sourceSector - 1) / ZONES_PER_ROW;
        int sourceCol = (sourceSector - 1) % ZONES_PER_ROW;
        int targetRow = (targetSector - 1) / ZONES_PER_ROW;
        int targetCol = (targetSector - 1) % ZONES_PER_ROW;
        int dRow = Math.abs(sourceRow - targetRow);
        int dCol = Math.abs(sourceCol - targetCol);
        return (dRow <= 1 && dCol <= 1) && (dRow + dCol > 0);
    }

    private static double[] getWarpArrivalPoint(int sourceSector, int targetSector) {
        double sourceX = getZoneCenterX(sourceSector);
        double sourceY = getZoneCenterY(sourceSector);
        double targetX = getZoneCenterX(targetSector);
        double targetY = getZoneCenterY(targetSector);
        double dx = targetX - sourceX;
        double dy = targetY - sourceY;

        double arrivalX, arrivalY;
        double offset = 200.0; // inward offset

        if (Math.abs(dx) > Math.abs(dy)) {
            // Horizontal dominant
            if (dx > 0) {
                // Source left of target, arrive on left edge
                arrivalX = getZoneX(targetSector) + offset;
            } else {
                // Source right of target, arrive on right edge
                arrivalX = getZoneX(targetSector) + ZONE_WIDTH - offset;
            }
            arrivalY = getZoneCenterY(targetSector);
        } else if (Math.abs(dy) > Math.abs(dx)) {
            // Vertical dominant
            if (dy > 0) {
                // Source above target, arrive on top edge
                arrivalY = getZoneY(targetSector) + offset;
            } else {
                // Source below target, arrive on bottom edge
                arrivalY = getZoneY(targetSector) + ZONE_HEIGHT - offset;
            }
            arrivalX = getZoneCenterX(targetSector);
        } else {
            // Diagonal, arrive at corner
            if (dx > 0 && dy > 0) {
                // Source top-left of target, arrive top-left corner
                arrivalX = getZoneX(targetSector) + offset;
                arrivalY = getZoneY(targetSector) + offset;
            } else if (dx > 0 && dy < 0) {
                // Source bottom-left, arrive bottom-left
                arrivalX = getZoneX(targetSector) + offset;
                arrivalY = getZoneY(targetSector) + ZONE_HEIGHT - offset;
            } else if (dx < 0 && dy > 0) {
                // Source top-right, arrive top-right
                arrivalX = getZoneX(targetSector) + ZONE_WIDTH - offset;
                arrivalY = getZoneY(targetSector) + offset;
            } else {
                // Source bottom-right, arrive bottom-right
                arrivalX = getZoneX(targetSector) + ZONE_WIDTH - offset;
                arrivalY = getZoneY(targetSector) + ZONE_HEIGHT - offset;
            }
        }

        return new double[]{arrivalX, arrivalY};
    }

    private static final SectorScript[] SCRIPTS = new SectorScript[]{
            null,
            new SectorScript(1, ObjectiveType.SURVIVE, "Hold the trade-hub evacuation lanes", 360, 630, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(2, ObjectiveType.DESTROY, "Destroy customs-halo gunships before they seal the civilian aperture", 6, 690, BossKind.NONE, MapModifier.NEBULA),
            new SectorScript(3, ObjectiveType.DESTROY, "Break the red interdiction cordon at the jump ring", 12, 720, BossKind.NONE, MapModifier.NEBULA),
            new SectorScript(4, ObjectiveType.DESTROY, "Destroy the route-control blockers pinning the relay", 4, 750, BossKind.NONE, MapModifier.DEBRIS_FIELD),
            new SectorScript(5, ObjectiveType.DESTROY, "Destroy the reserve wing racing the relay", 10, 780, BossKind.NONE, MapModifier.DEBRIS_FIELD),
            new SectorScript(6, ObjectiveType.SURVIVE, "Recover the debris-wake caches before demolition ships erase them", 110, 720, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(7, ObjectiveType.BOSS, "Destroy the AI pursuit Titan", 1, 780, BossKind.MID_ALPHA, MapModifier.EMP_ZONE, MapModifier.GRAVITY_SHEAR),
            new SectorScript(8, ObjectiveType.ESCORT, "Keep the Exodus Transport Titan inside the Mothership's screen", 95, 780, BossKind.NONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(9, ObjectiveType.SURVIVE, "Cover the neutral broker hulls as they defect into the fleet", 65, 780, BossKind.NONE, MapModifier.RICH_DEPOSITS),
            new SectorScript(10, ObjectiveType.DESTROY, "Break the AI vanguard guarding the homeward lane", 16, 780, BossKind.NONE, MapModifier.RICH_DEPOSITS),
            new SectorScript(11, ObjectiveType.SURVIVE, "Secure depot ledgers and fuel stores before demolition charges fire", 85, 780, BossKind.NONE, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(12, ObjectiveType.SURVIVE, "Keep the green signatory couriers alive until the pact is signed", 95, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(13, ObjectiveType.DESTROY, "Destroy the jammer triad around Coalition Array Nysa", 3, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(14, ObjectiveType.DESTROY, "Destroy the relief wing trying to re-isolate Nysa", 8, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(15, ObjectiveType.DESTROY, "Silence Kharon's spotter towers and anchor guns", 4, 800, BossKind.NONE, MapModifier.GRAVITY_SHEAR, MapModifier.SOLAR_STORM),
            new SectorScript(16, ObjectiveType.BOSS, "Destroy the red Artillery Titan", 1, 840, BossKind.MID_BETA, MapModifier.GRAVITY_SHEAR, MapModifier.SOLAR_STORM),
            new SectorScript(17, ObjectiveType.DESTROY, "Destroy recon groups before they mark the coalition corridor", 6, 780, BossKind.NONE, MapModifier.NEBULA, MapModifier.SOLAR_STORM),
            new SectorScript(18, ObjectiveType.SURVIVE, "Hold the outer-Sol arrival corridor", 240, 780, BossKind.NONE, MapModifier.NEBULA, MapModifier.SOLAR_STORM),
            new SectorScript(19, ObjectiveType.DESTROY, "Destroy prison tenders and break the convoy clamps", 4, 840, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(20, ObjectiveType.ESCORT, "Keep the liberated recovery Titan close behind the Mothership", 100, 840, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(21, ObjectiveType.DESTROY, "Destroy the Luna orbital defense anchors", 3, 840, BossKind.NONE, MapModifier.EMP_ZONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(22, ObjectiveType.DESTROY, "Break the Luna reserve cordon and clear the Earth lane", 10, 840, BossKind.NONE, MapModifier.EMP_ZONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(23, ObjectiveType.DESTROY, "Destroy occupation uplink towers and cover the resistance launches", 4, 900, BossKind.NONE, MapModifier.SOLAR_STORM, MapModifier.GRAVITY_SHEAR),
            new SectorScript(24, ObjectiveType.FINAL_BOSS, "Destroy the AI Mothership over Earth", 1, 900, BossKind.FINAL, MapModifier.SOLAR_STORM, MapModifier.GRAVITY_SHEAR)
    };

    private static final SideObjectiveScript[] SIDE_SCRIPTS = new SideObjectiveScript[]{
            null,
            new SideObjectiveScript(1, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the Mothership pristine for 120s", 120, 160),
            new SideObjectiveScript(2, SideObjectiveType.CLEAR_BEFORE_TIME, "Break the customs halo in 540s", 540, 180),
            new SideObjectiveScript(3, SideObjectiveType.KILL_COUNT, "Destroy 8 interdiction ships", 8, 220),
            new SideObjectiveScript(4, SideObjectiveType.CLEAR_BEFORE_TIME, "Open the relay in 600s", 600, 240),
            new SideObjectiveScript(5, SideObjectiveType.CLEAR_BEFORE_TIME, "Break the relay relief wing in 620s", 620, 260),
            new SideObjectiveScript(6, SideObjectiveType.CLEAR_BEFORE_TIME, "Secure the caches in 540s", 540, 220),
            new SideObjectiveScript(7, SideObjectiveType.CLEAR_BEFORE_TIME, "Kill the pursuit Titan in 600s", 600, 240),
            new SideObjectiveScript(8, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the Exodus Titan undamaged for 90s", 90, 210),
            new SideObjectiveScript(9, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep three defectors alive for 90s", 90, 240),
            new SideObjectiveScript(10, SideObjectiveType.KILL_COUNT, "Destroy 10 vanguard escorts", 10, 230),
            new SideObjectiveScript(11, SideObjectiveType.CLEAR_BEFORE_TIME, "Secure the depot shelf in 560s", 560, 240),
            new SideObjectiveScript(12, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the lead signatory ship pristine for 80s", 80, 260),
            new SideObjectiveScript(13, SideObjectiveType.CLEAR_BEFORE_TIME, "Bring the Nysa array online in 600s", 600, 250),
            new SideObjectiveScript(14, SideObjectiveType.CLEAR_BEFORE_TIME, "Break the Nysa relief wing in 620s", 620, 280),
            new SideObjectiveScript(15, SideObjectiveType.KILL_COUNT, "Destroy 6 counterbattery escorts", 6, 260),
            new SideObjectiveScript(16, SideObjectiveType.KILL_COUNT, "Destroy 6 siege escorts", 6, 280),
            new SideObjectiveScript(17, SideObjectiveType.CLEAR_BEFORE_TIME, "Kill the recon screen in 560s", 560, 260),
            new SideObjectiveScript(18, SideObjectiveType.KILL_COUNT, "Destroy 14 attackers during the hold", 14, 300),
            new SideObjectiveScript(19, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the recovery Titan intact for 90s", 90, 320),
            new SideObjectiveScript(20, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep liberated crews secure for 100s", 100, 320),
            new SideObjectiveScript(21, SideObjectiveType.CLEAR_BEFORE_TIME, "Silence Luna's anchors in 620s", 620, 350),
            new SideObjectiveScript(22, SideObjectiveType.CLEAR_BEFORE_TIME, "Break the Luna cordon in 620s", 620, 360),
            new SideObjectiveScript(23, SideObjectiveType.CLEAR_BEFORE_TIME, "Blind the occupation uplinks in 660s", 660, 380),
            new SideObjectiveScript(24, SideObjectiveType.CLEAR_BEFORE_TIME, "End the occupation in 720s", 720, 400)
    };

    private static final SectorLore[] LORE = new SectorLore[]{
            null,
            new SectorLore(1, "ANCHORAGE FIRESTORM", "Far Trade Anchorage",
                    "Earth has fallen. Hold the evacuation lanes while Far Trade's arcology crowns, exchange ring, and refugee docks burn around the harbor approaches.",
                    "The trade colony is gutted, but the convoy escapes with civilians, treasury ledgers, and a road home."),
            new SectorLore(2, "CUSTOMS HALO COLLAPSE", "Outer Colony Jump Ring Approach",
                    "Destroy customs-halo gunships and interdiction cutters before they seal the civilian aperture and trap the convoy outside the ring.",
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
        public boolean enabled;
        public int sector = 1;
        public final int totalSectors = 24;
        public int act = 1;

        public ObjectiveType objectiveType = ObjectiveType.SURVIVE;
        public String objectiveLabel = "";
        public double objectiveProgress = 0.0;
        public double objectiveGoal = 1.0;

        public double sectorElapsed = 0.0;
        public double sectorTimeLimit = 600.0; // 10 minutes per sector target pacing

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
        public int authoredWaveCursor = 0;
        public final List<CampaignLandmark> landmarks = new ArrayList<>();
        public String objectivePhaseLabel = "";
        public String threatStateLabel = "";
        public int objectiveStage = 0;
        public int objectiveKillBaseline = 0;
        public final Set<Integer> objectiveAssetIds = new HashSet<>();
        public int objectiveAssetTotal = 0;
        public int objectiveAssetLosses = 0;
        public String objectiveAssetLabel = "";
        public int objectiveAssetRequiredSurvivors = 0;
        public String objectiveAssetFailureText = "";

        public double transitionTimer = 0.0;
        public String transitionLabel = "";
        public long sectorStartMillis = 0L;
        public String transitionSummaryTop = "";
        public String transitionSummaryBottom = "";

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

        public boolean unlockAuxGunGranted = false;
        public int unlockMissileTierGranted = 0;
        public boolean unlockCiwsGranted = false;
        public boolean unlockHullGranted = false;

        public boolean bossDropAegisArray = false;
        public boolean bossDropMissileCore = false;
        public boolean bossDropFlagCore = false;
        public int bossDropsCollected = 0;
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
                EventSystem.showBanner(ctx, "CAMPAIGN RESUMED: " + loreFor(checkpoint.nextSector).title, 2.2);
                startSector(ctx, checkpoint.nextSector);
            }
            return;
        }

        CampaignCheckpointStore.clear();
        ctx.credits = CAMPAIGN_STARTING_CREDITS;
        applyPersistedUnlockProfile(ctx, st);
        seedStartingBlueFleet(st);
        persistRunStart(ctx);

        if (ctx.config.mode == GameMode.FLEET) {
            enterFleetHub(ctx, st);
        } else {
            EventSystem.showBanner(ctx, "CAMPAIGN START: ACT I - " + actTitleFor(1), 2.2);
            startSector(ctx, 1);
        }
    }

    public static void update(GameContext ctx, double dt) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || ctx.gameOver) return;

        if (ctx.player == null || !ctx.player.alive || ctx.player.hp <= 0) {
            failRun(ctx, "DEFEAT: FLAGSHIP LOST");
            return;
        }

        refreshCampaignAlliances(st);

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

        if (st.enemyBaseWinConditionActive && isEnemyBaseDestroyed(ctx)) {
            st.objectiveProgress = st.objectiveGoal;
            onSectorComplete(ctx);
            return;
        }

        st.sectorElapsed += dt;
        if (st.sectorElapsed >= st.sectorTimeLimit) {
            failRun(ctx, "DEFEAT: SECTOR TIMEOUT");
            return;
        }

        syncPersistentFleetCasualties(ctx, st);
        detectHostileKills(ctx);
        detectObjectiveAssetLosses(ctx);
        updateAuthoredSectorScript(ctx, st);
        updateEscortFormationBehavior(ctx, st, dt);
        updateSideObjective(ctx, dt);
        updateObjective(ctx, dt);
    }

    public static boolean isCampaignActive(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled;
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
        int left = (int) Math.ceil(Math.max(0.0, st.sectorTimeLimit - st.sectorElapsed));
        String p = formatProgress(st.objectiveProgress, st.objectiveGoal);
        SectorLore lore = loreFor(st.sector);
        StringBuilder brief = new StringBuilder();
        appendObjectiveBriefPart(brief, lore.hudLead);
        appendObjectiveBriefPart(brief, st.objectiveLabel);
        appendObjectiveBriefPart(brief, lore.location);
        appendObjectiveBriefPart(brief, landmarkHud(st));
        appendObjectiveBriefPart(brief, st.objectivePhaseLabel);
        appendObjectiveBriefPart(brief, st.threatStateLabel);
        appendObjectiveBriefPart(brief, "OBJ " + p);
        if (st.objectiveType == ObjectiveType.ESCORT) {
            appendObjectiveBriefPart(brief, "FORMATION "
                    + (int) Math.round(MathUtil.clamp(st.escortFormationIntegrity, 0.0, 1.0) * 100.0) + "%");
        }
        appendObjectiveBriefPart(brief, objectiveAssetHud(st));
        appendObjectiveBriefPart(brief, "T-" + left + "s");
        String side = sideObjectiveHud(st);
        if (!side.isBlank()) appendObjectiveBriefPart(brief, "SIDE " + side);
        String drop = bossDropHud(st);
        if (!drop.isBlank()) appendObjectiveBriefPart(brief, "DROP " + drop);
        return brief.toString();
    }

    private static void appendObjectiveBriefPart(StringBuilder brief, String part) {
        if (brief == null || part == null) return;
        String text = part.trim();
        if (text.isEmpty()) return;
        if (brief.length() > 0) brief.append("   ");
        brief.append(text);
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
        return st != null && st.enabled
                && (st.transitionTimer > 0 || st.awaitingEpisodeLaunch || st.awaitingFleetHubChoice);
    }

    public static double transitionSeconds(GameContext ctx) {
        CampaignState st = state(ctx);
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
            st.transitionSummaryTop = "Fleet hangar open. Click a ship to focus it.";
        }
        // Always replace the bottom row with fleet hub controls (sector-clear screens use the same overlay).
        st.transitionSummaryBottom = "TAB: Fleet shop   |   B: Upgrade selected hull   |   ENTER launches";
        st.introSequenceActive = false;
        st.introPhase = 0;
        st.introTimer = 0.0;
        st.cinematicFocusX = Double.NaN;
        st.cinematicFocusY = Double.NaN;
        quietEpisodeInterlude(ctx, st);
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
        entry.turretData = serializeTurrets(ship);
        entry.primaryWeaponFamilyName = (ship.primaryWeaponFamily == null)
                ? Ship.PrimaryWeaponFamily.ENERGY_BOLT.name()
                : ship.primaryWeaponFamily.name();
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
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        return switch (resolved) {
            case ESCORT -> CAMPAIGN_ESCORT_CAP_UPGRADE_STEP;
            case LINE -> CAMPAIGN_LINE_CAP_UPGRADE_STEP;
            case CAPITAL -> CAMPAIGN_CAPITAL_CAP_UPGRADE_STEP;
            case TITAN -> 0;
        };
    }

    static int persistentFleetCapUpgradeMaxLevel(ShopHullCategory category) {
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        return switch (resolved) {
            case ESCORT -> CAMPAIGN_ESCORT_CAP_UPGRADE_MAX_LEVEL;
            case LINE -> CAMPAIGN_LINE_CAP_UPGRADE_MAX_LEVEL;
            case CAPITAL -> CAMPAIGN_CAPITAL_CAP_UPGRADE_MAX_LEVEL;
            case TITAN -> 0;
        };
    }

    static int persistentFleetCapUpgradeLevel(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCapUpgradeLevel(state(ctx), category);
    }

    static int persistentFleetCapUpgradeLevel(CampaignState st, ShopHullCategory category) {
        if (st == null) return 0;
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        return switch (resolved) {
            case ESCORT -> Math.max(0, Math.min(CAMPAIGN_ESCORT_CAP_UPGRADE_MAX_LEVEL, st.escortCapUpgradeLevel));
            case LINE -> Math.max(0, Math.min(CAMPAIGN_LINE_CAP_UPGRADE_MAX_LEVEL, st.lineCapUpgradeLevel));
            case CAPITAL -> Math.max(0, Math.min(CAMPAIGN_CAPITAL_CAP_UPGRADE_MAX_LEVEL, st.capitalCapUpgradeLevel));
            case TITAN -> 0;
        };
    }

    static int persistentFleetCapUpgradeBonus(CampaignState st, ShopHullCategory category) {
        return persistentFleetCapUpgradeLevel(st, category) * persistentFleetCapUpgradeStep(category);
    }

    static int persistentFleetCapUpgradeCreditCost(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCapUpgradeCreditCost(state(ctx), category);
    }

    static int persistentFleetCapUpgradeCreditCost(CampaignState st, ShopHullCategory category) {
        int level = persistentFleetCapUpgradeLevel(st, category);
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        return switch (resolved) {
            case ESCORT -> 900 + level * 450;
            case LINE -> 1_600 + level * 800;
            case CAPITAL -> 2_800 + level * 1_250;
            case TITAN -> 0;
        };
    }

    static int persistentFleetCapUpgradeOreCost(GameContext ctx, ShopHullCategory category) {
        return persistentFleetCapUpgradeOreCost(state(ctx), category);
    }

    static int persistentFleetCapUpgradeOreCost(CampaignState st, ShopHullCategory category) {
        int level = persistentFleetCapUpgradeLevel(st, category);
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        return switch (resolved) {
            case ESCORT -> 180 + level * 90;
            case LINE -> 320 + level * 150;
            case CAPITAL -> 560 + level * 240;
            case TITAN -> 0;
        };
    }

    public static boolean purchasePersistentFleetCapUpgrade(GameContext ctx, ShopHullCategory category) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || ctx.player == null || category == null) return false;
        if (category == ShopHullCategory.TITAN) {
            EventSystem.showBanner(ctx, "TITAN CAP FIXED BY MOTHERSHIP DOCTRINE", 1.8);
            return false;
        }

        int level = persistentFleetCapUpgradeLevel(st, category);
        int maxLevel = persistentFleetCapUpgradeMaxLevel(category);
        if (level >= maxLevel) {
            EventSystem.showBanner(ctx, category.label() + " EXPANSION MAXED", 1.8);
            return false;
        }

        int currentCap = persistentFleetCap(st, category);
        int liveCount = livePersistentFleetSlots(st, category);
        if (liveCount < currentCap) {
            EventSystem.showBanner(ctx, "FILL " + category.label() + " COMMAND FIRST", 1.8);
            return false;
        }

        int creditCost = persistentFleetCapUpgradeCreditCost(st, category);
        int oreCost = persistentFleetCapUpgradeOreCost(st, category);
        if (ctx.credits < creditCost || ctx.player.cargo < oreCost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS / ORE", 1.6);
            return false;
        }

        ctx.credits -= creditCost;
        ctx.player.cargo = Math.max(0, ctx.player.cargo - oreCost);
        switch (category) {
            case ESCORT -> st.escortCapUpgradeLevel = Math.min(maxLevel, st.escortCapUpgradeLevel + 1);
            case LINE -> st.lineCapUpgradeLevel = Math.min(maxLevel, st.lineCapUpgradeLevel + 1);
            case CAPITAL -> st.capitalCapUpgradeLevel = Math.min(maxLevel, st.capitalCapUpgradeLevel + 1);
            case TITAN -> { return false; }
        }
        EventSystem.showBanner(ctx,
                category.label() + " CAP +" + persistentFleetCapUpgradeStep(category),
                1.8);
        return true;
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
        int liveCount = livePersistentFleetSlots(st, category);
        int cap = persistentFleetCap(st, category);
        if (liveCount >= cap) {
            EventSystem.showBanner(ctx, category.label() + " COMMAND CAP REACHED", 1.8);
            return false;
        }

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
        if (ctx.credits < Math.max(0, creditCost) || ctx.player.cargo < oreCost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS / ORE", 1.6);
            return false;
        }

        ctx.credits -= Math.max(0, creditCost);
        ctx.player.cargo = Math.max(0, ctx.player.cargo - oreCost);

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
        if (ctx == null || st == null || !st.awaitingEpisodeLaunch || st.pendingEpisodeSector <= 0) return false;
        syncPersistentFleetEntrySnapshots(ctx, st);
        saveCheckpoint(ctx, st, st.pendingEpisodeSector);
        UISystem.closeAllOverlays(ctx);
        ctx.lockedTarget = null;
        ctx.state = GameState.RUNNING;
        startSector(ctx, st.pendingEpisodeSector);
        return true;
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
        return persistentFleetBaseCap(category) + persistentFleetCapUpgradeBonus(st, category);
    }

    static String persistentFleetCompactSummary(GameContext ctx) {
        int escort = livePersistentFleetCount(ctx, ShopHullCategory.ESCORT);
        int line = livePersistentFleetCount(ctx, ShopHullCategory.LINE);
        int capital = livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL);
        int titan = livePersistentFleetCount(ctx, ShopHullCategory.TITAN);
        return "E" + escort + "/" + persistentFleetCap(ctx, ShopHullCategory.ESCORT)
                + " L" + line + "/" + persistentFleetCap(ctx, ShopHullCategory.LINE)
                + " C" + capital + "/" + persistentFleetCap(ctx, ShopHullCategory.CAPITAL)
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
    }

    private static void seedStartingBlueFleet(CampaignState st) {
        if (st == null || !st.persistentBlueFleet.isEmpty()) return;
        addPersistentFleetEntry(st, ShipRole.PICKET, "Blue Screen One");
        addPersistentFleetEntry(st, ShipRole.FRIGATE, "Blue Guard One");
        addPersistentFleetEntry(st, ShipRole.CIWS_CORVETTE, "Blue Guard Two");
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
            Ship ship = spawnPersistentBlueShipFromFlagship(ctx, st, entry, titanIndex++, true);
            if (ship != null) {
                groupAnchors.put(entry.commandGroupId, ship);
            }
        }

        int reserveIndex = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || isTitanPersistentEntry(entry)) continue;
            Ship anchor = groupAnchors.get(entry.commandGroupId);
            if (anchor != null) {
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

        if (ctx != null) ctx.state = GameState.RUNNING;
        st.sector = sector;
        st.act = actForSector(sector);

        // Set player position to warp arrival point
        if (ctx != null && ctx.player != null) {
            double[] arrival = (sector == 1) 
                ? new double[]{getZoneCenterX(sector), getZoneCenterY(sector)}
                : getWarpArrivalPoint(sector - 1, sector);
            ctx.player.x = arrival[0];
            ctx.player.y = arrival[1];
            ctx.player.vx = 0.0;
            ctx.player.vy = 0.0;
            ctx.player.angle = -Math.PI / 2.0; // facing up
        }

        st.transitionTimer = 0.0;
        st.awaitingEpisodeLaunch = false;
        st.pendingEpisodeSector = 0;
        st.sectorElapsed = 0.0;
        st.kills = 0;
        st.knownHostiles.clear();
        st.authoredObjectiveHostiles.clear();
        st.authoredObjectiveKills = 0;
        st.authoredWaveCursor = 0;
        st.landmarks.clear();
        st.objectivePhaseLabel = "";
        st.threatStateLabel = "";
        st.objectiveStage = 0;
        st.objectiveKillBaseline = 0;
        st.objectiveAssetIds.clear();
        st.objectiveAssetTotal = 0;
        st.objectiveAssetLosses = 0;
        st.objectiveAssetLabel = "";
        st.objectiveAssetRequiredSurvivors = 0;
        st.objectiveAssetFailureText = "";
        st.captureArmed = false;
        st.bossTargetId = -1;
        st.bossKind = BossKind.NONE;
        st.bossPhaseOneTriggered = false;
        st.bossPhaseTwoTriggered = false;
        st.escortShip = null;
        st.transitionLabel = "";
        st.transitionSummaryTop = "";
        st.transitionSummaryBottom = "";
        st.sectorStartMillis = System.currentTimeMillis();
        st.introSequenceActive = false;
        st.introPhase = 0;
        st.introTimer = 0.0;
        st.introWarpX = Double.NaN;
        st.introWarpY = Double.NaN;
        st.cinematicFocusX = Double.NaN;
        st.cinematicFocusY = Double.NaN;
        st.escortFormationIntegrity = 0.0;

        refreshCampaignAlliances(st);
        rebalancePersistentCommandGroups(st);
        resetPersistentFleetSpawnHandles(st);
        pruneTransientUnits(ctx);
        FogOfWarSystem.reset(ctx);
        regroupPlayerAtAlliedBase(ctx);
        SpawnSystem.spawnAsteroidField(ctx);
        applyCampaignFleetBonuses(ctx, st);
        healAndRefitPlayer(ctx);
        ensureCampaignTitanInfrastructure(ctx);

        SectorScript script = configureObjective(ctx);
        applySectorModifiers(ctx, st, script);
        spawnSectorForces(ctx);
        populateSectorLandmarks(ctx, st);
        spawnPersistentBlueFleet(ctx, st);
        spawnCoalitionSupportFleet(ctx, st);
        captureSideObjectiveProtectedShip(ctx, st);
        st.enemyBaseWinConditionActive = hasLiveEnemyBase(ctx);
        snapshotHostiles(ctx, st.knownHostiles);

        ctx.enemyWaveTimer = nextWaveDelay(ctx);
        FogOfWarSystem.update(ctx);

        SectorLore lore = loreFor(st.sector);
        String msg = "SECTOR " + st.sector + "/" + st.totalSectors
                + "  " + lore.title
                + "  |  " + st.objectiveLabel;
        EventSystem.showBanner(ctx, msg, 3.2);
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
        st.landmarks.add(new CampaignLandmark(type, label, subtitle, clampedX, clampedY, radius, fill, edge));
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
        spawnEnemyAtPlayerOffset(ctx, ShipRole.VANGUARD_TITAN, 760, -80);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 860, -160);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 900, -40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 980, 60);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 1020, 140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 900, 200);
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
        if (st.authoredWaveCursor == 0 && t >= 45.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 820, -220);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 900, 90);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=1 wave=1 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 1 && t >= 130.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 860, -170);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 980, 20);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=1 wave=2 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 2 && t >= 220.0) {
            spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 980, -50);
            spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 1020, 150);
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.PICKET, Faction.TEAM_C, -240, 90, "Green Relief Screen");
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=1 wave=3 t=" + Math.round(t));
            return;
        }
        if (st.authoredWaveCursor == 3 && t >= 300.0) {
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
                st.objectiveLabel = "Destroy the relay relief wing and cover the Earth vector";
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

    private static boolean usesAuthoredDestroyProgress(CampaignState st) {
        if (st == null || st.objectiveType != ObjectiveType.DESTROY) return false;
        return switch (st.sector) {
            case 2, 4, 13, 15, 17, 19, 21, 23 -> true;
            default -> false;
        };
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
        if (st == null || st.objectiveAssetFailureText == null || st.objectiveAssetFailureText.isBlank()) {
            return "DEFEAT: OBJECTIVE ASSETS LOST";
        }
        return st.objectiveAssetFailureText;
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
            if (st.objectiveType == ObjectiveType.CAPTURE) return st.captureY;
            if (st.objectiveType == ObjectiveType.ESCORT && st.escortShip != null) return st.escortShip.y;
            if ((st.objectiveType == ObjectiveType.BOSS || st.objectiveType == ObjectiveType.FINAL_BOSS)) {
                Ship boss = findShipById(ctx, st.bossTargetId);
                if (boss != null) return boss.y;
            }
        }
        return (ctx != null && ctx.player != null) ? ctx.player.y : 2500.0;
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
        return SpawnSystem.spawnEnemy(ctx, role, x, y);
    }

    private static Ship spawnCampaignShip(GameContext ctx, ShipRole role, Faction faction, double x, double y, String name) {
        if (ctx == null || role == null || faction == null) return null;
        Ship ship = new FleetShip(role, faction,
                GameMath.clamp(x, 30.0, ctx.WORLD_W - 30.0),
                GameMath.clamp(y, 30.0, ctx.WORLD_H - 30.0));
        ctx.ships.add(ship);
        try { DoctrineRegistry.applyToShip(ship); } catch (Throwable ignored) {}
        if (ship != null && name != null && !name.isBlank()) {
            ship.name = name;
        }
        return ship;
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
                    failRun(ctx, "DEFEAT: ESCORT LOST");
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

        if (objectiveAssetQuotaFailed(st)) {
            failRun(ctx, objectiveAssetFailureText(st));
            return;
        }

        if (st.objectiveProgress >= st.objectiveGoal) {
            onSectorComplete(ctx);
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

    private static void onSectorComplete(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null) return;

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
        int nextSector = st.sector + 1;
        boolean hasNextEpisode = nextSector <= st.totalSectors;
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
        st.transitionSummaryBottom = "TAB: Open fleet hangar now   |   Auto-opens in ~"
                + ((int) Math.round(FLEET_HUB_AUTO_OPEN_DELAY)) + " seconds";
        st.awaitingFleetHubChoice = hasNextEpisode;
        st.fleetHubChoiceTimer = hasNextEpisode ? FLEET_HUB_AUTO_OPEN_DELAY : 0.0;
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
        ctx.player.cargo = Math.min(ctx.player.cargoMax, ctx.player.cargo + oreReward);
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
            for (Iterator<Integer> it = st.authoredObjectiveHostiles.iterator(); it.hasNext(); ) {
                Integer id = it.next();
                if (!aliveNow.contains(id)) {
                    st.authoredObjectiveKills++;
                    it.remove();
                }
            }
        }

        st.knownHostiles.clear();
        st.knownHostiles.addAll(aliveNow);
    }

    private static void detectObjectiveAssetLosses(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || st.objectiveAssetIds.isEmpty()) return;
        for (Iterator<Integer> it = st.objectiveAssetIds.iterator(); it.hasNext(); ) {
            Ship ship = findShipById(ctx, it.next());
            if (ship != null && ship.alive && !ship.dying && ship.hp > 0) continue;
            st.objectiveAssetLosses++;
            it.remove();
        }
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
        CampaignState st = state(ctx);
        ctx.gameOver = true;
        ctx.state = GameState.GAME_OVER;
        ctx.gameOverText = text;
        EventSystem.showBanner(ctx, text, 3.0);
        CampaignCheckpointStore.clear();
        persistRunResult(ctx, false);
        if (st != null) {
            logTelemetry("sector_fail",
                    "sector=" + st.sector +
                            " elapsedSec=" + Math.round(st.sectorElapsed) +
                            " objective=" + st.objectiveType +
                            " reason=" + text);
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

        CampaignCheckpointStore.Checkpoint cp = new CampaignCheckpointStore.Checkpoint();
        cp.worldW = ctx.WORLD_W;
        cp.worldH = ctx.WORLD_H;
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
        cp.escortCapUpgradeLevel = st.escortCapUpgradeLevel;
        cp.lineCapUpgradeLevel = st.lineCapUpgradeLevel;
        cp.capitalCapUpgradeLevel = st.capitalCapUpgradeLevel;
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
        st.escortCapUpgradeLevel = Math.max(0, Math.min(CAMPAIGN_ESCORT_CAP_UPGRADE_MAX_LEVEL, cp.escortCapUpgradeLevel));
        st.lineCapUpgradeLevel = Math.max(0, Math.min(CAMPAIGN_LINE_CAP_UPGRADE_MAX_LEVEL, cp.lineCapUpgradeLevel));
        st.capitalCapUpgradeLevel = Math.max(0, Math.min(CAMPAIGN_CAPITAL_CAP_UPGRADE_MAX_LEVEL, cp.capitalCapUpgradeLevel));
        restorePersistentBlueFleet(st, cp.persistentBlueFleet);
        ctx.miningBaseMul = Math.max(0.0, cp.miningBaseMul);
        ctx.orePriceBaseMul = Math.max(0.0, cp.orePriceBaseMul);

        restorePlayerFromCheckpoint(ctx.player, cp);
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
        return (titan == null) ? 0 : Math.max(4, titan.totalCommandHullCapacity());
    }

    private static void rebalancePersistentCommandGroups(CampaignState st) {
        if (st == null) return;

        java.util.Map<Integer, Integer> groupCap = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> groupLoad = new java.util.HashMap<>();
        java.util.List<PersistentFleetEntry> standards = new ArrayList<>();

        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            if (isTitanPersistentEntry(entry)) {
                entry.commandGroupId = entry.slotId;
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
            int groupId = bestPersistentCommandGroup(groupLoad, groupCap);
            if (groupId == CAMPAIGN_FLAGSHIP_COMMAND_GROUP) continue;
            entry.commandGroupId = groupId;
            groupLoad.put(groupId, groupLoad.getOrDefault(groupId, 0) + 1);
        }
    }

    private static int bestPersistentCommandGroup(java.util.Map<Integer, Integer> groupLoad,
                                                  java.util.Map<Integer, Integer> groupCap) {
        int bestId = CAMPAIGN_FLAGSHIP_COMMAND_GROUP;
        double bestScore = Double.POSITIVE_INFINITY;
        for (java.util.Map.Entry<Integer, Integer> capEntry : groupCap.entrySet()) {
            int groupId = capEntry.getKey();
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
        ship.fullyRepairHull();
        ship.resetShieldState();
        if (ship.shieldActive && ship.shieldMax > 0.0) {
            ship.shield = ship.shieldMax;
        }
        entry.activeShipId = ship.id;
        return ship;
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
                    .append(encodedTurrets).append(',')
                    .append((entry.primaryWeaponFamilyName == null || entry.primaryWeaponFamilyName.isBlank())
                            ? Ship.PrimaryWeaponFamily.ENERGY_BOLT.name()
                            : entry.primaryWeaponFamilyName.trim());
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
            String[] parts = entryRaw.split(",", 12);
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
                if (parts.length >= 11 && parts[10] != null && !parts[10].isBlank()) {
                    entry.turretData = new String(decoder.decode(parts[10].trim()), StandardCharsets.UTF_8);
                }
                if (parts.length >= 12 && parts[11] != null && !parts[11].isBlank()) {
                    entry.primaryWeaponFamilyName = parts[11].trim();
                }
                st.persistentBlueFleet.add(entry);
                st.nextPersistentFleetSlotId = Math.max(st.nextPersistentFleetSlotId, slotId + 1);
            } catch (Exception ignored) {
                // Skip malformed checkpoint fleet entries.
            }
        }
        rebalancePersistentCommandGroups(st);
    }

}
