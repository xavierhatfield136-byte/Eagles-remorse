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
 * - 12 sectors
 * - objective per sector
 * - act breaks
 * - paced unlock grants
 */
public final class CampaignSystem {
    private CampaignSystem() {}

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

    private static final class PersistentFleetEntry {
        final int slotId;
        final ShipRole role;
        final String name;
        boolean destroyed = false;
        int activeShipId = -1;

        PersistentFleetEntry(int slotId, ShipRole role, String name) {
            this.slotId = Math.max(1, slotId);
            this.role = (role == null) ? ShipRole.FRIGATE : role;
            this.name = (name == null || name.isBlank()) ? ("Blue Wing " + this.slotId) : name;
        }
    }

    private static final int AUTHORED_VERTICAL_SLICE_LAST_SECTOR = 3;
    private static final int CAMPAIGN_STARTING_CREDITS = 1000;
    private static final int CAMPAIGN_BLUE_FLEET_CAP = 24;
    private static final String[] ACT_TITLES = {
            "",
            "TRADE HUB COLLAPSE",
            "THE LONG ROAD HOME",
            "RETURN TO EARTH"
    };

    private static final SectorScript[] SCRIPTS = new SectorScript[]{
            null,
            new SectorScript(1, ObjectiveType.SURVIVE, "Hold the trade-hub evacuation lanes", 360, 630, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(2, ObjectiveType.DESTROY, "Break the red interdiction cordon", 8, 720, BossKind.NONE, MapModifier.NEBULA),
            new SectorScript(3, ObjectiveType.CAPTURE, "Seize the authority relay, then hold the uplink", 120, 780, BossKind.NONE, MapModifier.DEBRIS_FIELD),
            new SectorScript(4, ObjectiveType.BOSS, "Destroy the AI pursuit Titan", 1, 780, BossKind.MID_ALPHA, MapModifier.EMP_ZONE, MapModifier.GRAVITY_SHEAR),
            new SectorScript(5, ObjectiveType.ESCORT, "Escort the Exodus Transport Titan", 210, 780, BossKind.NONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(6, ObjectiveType.DESTROY, "Break the AI vanguard guarding the homeward lane", 12, 780, BossKind.NONE, MapModifier.RICH_DEPOSITS),
            new SectorScript(7, ObjectiveType.CAPTURE, "Secure the green contract array", 120, 780, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(8, ObjectiveType.BOSS, "Destroy the Ash Gate Artillery Titan", 1, 840, BossKind.MID_BETA, MapModifier.GRAVITY_SHEAR, MapModifier.SOLAR_STORM),
            new SectorScript(9, ObjectiveType.SURVIVE, "Hold the outer-Sol arrival corridor", 240, 780, BossKind.NONE, MapModifier.NEBULA, MapModifier.SOLAR_STORM),
            new SectorScript(10, ObjectiveType.ESCORT, "Escort the liberated recovery Titan", 220, 840, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(11, ObjectiveType.DESTROY, "Break the Luna orbital cordon", 14, 840, BossKind.NONE, MapModifier.EMP_ZONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(12, ObjectiveType.FINAL_BOSS, "Destroy the AI Mothership over Earth", 1, 900, BossKind.FINAL, MapModifier.SOLAR_STORM, MapModifier.GRAVITY_SHEAR)
    };

    private static final SideObjectiveScript[] SIDE_SCRIPTS = new SideObjectiveScript[]{
            null,
            new SideObjectiveScript(1, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the Mothership pristine for 120s", 120, 160),
            new SideObjectiveScript(2, SideObjectiveType.KILL_COUNT, "Destroy 6 interdiction ships", 6, 200),
            new SideObjectiveScript(3, SideObjectiveType.CLEAR_BEFORE_TIME, "Secure the relay in 660s", 660, 240),
            new SideObjectiveScript(4, SideObjectiveType.CLEAR_BEFORE_TIME, "Kill the pursuit Titan in 600s", 600, 220),
            new SideObjectiveScript(5, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep the Exodus Titan undamaged for 120s", 120, 210),
            new SideObjectiveScript(6, SideObjectiveType.KILL_COUNT, "Destroy 10 vanguard escorts", 10, 230),
            new SideObjectiveScript(7, SideObjectiveType.CLEAR_BEFORE_TIME, "Secure the contract array in 600s", 600, 250),
            new SideObjectiveScript(8, SideObjectiveType.KILL_COUNT, "Destroy 6 siege escorts", 6, 280),
            new SideObjectiveScript(9, SideObjectiveType.KILL_COUNT, "Destroy 14 attackers during the hold", 14, 300),
            new SideObjectiveScript(10, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Keep liberated crews secure for 150s", 150, 320),
            new SideObjectiveScript(11, SideObjectiveType.CLEAR_BEFORE_TIME, "Break the orbital cordon in 620s", 620, 350),
            new SideObjectiveScript(12, SideObjectiveType.CLEAR_BEFORE_TIME, "End the occupation in 720s", 720, 400)
    };

    private static final SectorLore[] LORE = new SectorLore[]{
            null,
            new SectorLore(1, "ANCHORAGE FIRESTORM", "Far Trade Anchorage",
                    "Earth has fallen. Hold the evacuation lanes while the colony burns.",
                    "The trade hub is gone, but the fleet still has a road home."),
            new SectorLore(2, "BREAKOUT VECTOR", "Outer Colony Jump Ring",
                    "Red interdiction packs are sealing the jump ring. Break the cordon before it collapses.",
                    "The first blockade is broken and the road home stays open."),
            new SectorLore(3, "LAST AUTHORITY RELAY", "Gate Relay Tethys",
                    "Seize the authority relay and keep the uplink alive long enough to chart the Earth vector.",
                    "Navigation control is restored and the homeward route is real."),
            new SectorLore(4, "RED KNIFE PURSUIT", "Burning Debris Wake",
                    "A pursuit Titan is closing fast. Kill it before it pins the refugee column in deep space.",
                    "The AI's first Titan hunter is down and the fleet escapes the kill box."),
            new SectorLore(5, "REFUGEE WAYLINE", "Civilian Exodus Corridor",
                    "Escort the Exodus Transport Titan carrying refugees, fuel, and state archives.",
                    "The civilian column survives and the fleet keeps its people with it."),
            new SectorLore(6, "BROKEN ARMISTICE", "Neutral Trade Spine",
                    "Break the AI vanguard and hold the lane long enough for neutral survivors to defect.",
                    "More hulls fall in behind the Mothership and the return fleet grows."),
            new SectorLore(7, "GREEN CONTRACT FRONT", "Coalition Array Nysa",
                    "Secure the green contract array and convince the coalition fleet to join the road home.",
                    "Green signals are back on the net and the coalition starts to form."),
            new SectorLore(8, "ASHEN GATE", "Siege Gate Kharon",
                    "A red Artillery Titan is blocking the Earthward lane. Silence it.",
                    "The siege gate is broken and the fleet can press toward Sol."),
            new SectorLore(9, "OUTER SOL HOLD", "Outer Sol Defense Ring",
                    "The AI knows you are coming. Hold formation while the coalition assembles for the final push.",
                    "The line holds. Sol is in reach and the coalition remains intact."),
            new SectorLore(10, "YELLOW BREAKCHAIN", "Liberation Corridor",
                    "Escort the liberated recovery Titan and its freed crews back into formation.",
                    "Yellow survivors are back in the fleet and the liberation war becomes real."),
            new SectorLore(11, "EARTH APPROACH", "Luna Perimeter",
                    "Break the orbital cordon, shatter the AI screen, and open a lane to Earth.",
                    "Earth is ahead. Only the occupation fleet remains."),
            new SectorLore(12, "HOMEWORLD LIBERATION", "Earth High Orbit",
                    "Destroy the AI Mothership over Earth and end the occupation.",
                    "The AI is broken, Earth is free, and the long road home is over.")
    };

    public static final class CampaignState {
        public boolean enabled;
        public int sector = 1;
        public final int totalSectors = 12;
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
        public boolean sideObjectiveCompleted = false;
        public boolean sideObjectiveFailed = false;
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
        if (ctx.config.mode != GameMode.CAMPAIGN_OPS) return;

        CampaignState st = new CampaignState();
        st.enabled = true;
        ctx.campaign = st;
        configureCampaignSession(ctx, st);
        CampaignCheckpointStore.Checkpoint checkpoint = ctx.config.resumeCampaign ? CampaignCheckpointStore.load() : null;
        if (checkpoint != null && checkpoint.isUsable() && applyCheckpoint(ctx, st, checkpoint)) {
            configureCampaignSession(ctx, st);
            EventSystem.showBanner(ctx, "CAMPAIGN RESUMED: " + loreFor(checkpoint.nextSector).title, 2.2);
            startSector(ctx, checkpoint.nextSector);
            return;
        }

        CampaignCheckpointStore.clear();
        ctx.credits = CAMPAIGN_STARTING_CREDITS;
        applyPersistedUnlockProfile(ctx, st);
        seedStartingBlueFleet(st);
        persistRunStart(ctx);

        EventSystem.showBanner(ctx, "CAMPAIGN START: ACT I - " + actTitleFor(1), 2.2);
        startSector(ctx, 1);
    }

    public static void update(GameContext ctx, double dt) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || ctx.gameOver) return;

        if (ctx.player == null || !ctx.player.alive || ctx.player.hp <= 0) {
            failRun(ctx, "DEFEAT: FLAGSHIP LOST");
            return;
        }

        refreshCampaignAlliances(st);

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
        updateAuthoredSectorScript(ctx, st);
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
        int base = (st.sector >= 10) ? 3 : (st.sector >= 5 ? 2 : 1);
        return Math.max(1, (int) Math.round(base * st.enemyWaveGroupMul));
    }

    public static String hudObjectiveTitle(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        SectorLore lore = loreFor(st.sector);
        return "ACT " + st.act + ": " + actTitleFor(st.act)
                + "   SECTOR " + st.sector + "/" + st.totalSectors
                + "   DOCTRINE " + st.branchRoute
                + "   " + lore.title;
    }

    public static String hudObjectiveDetail(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        int left = (int) Math.ceil(Math.max(0.0, st.sectorTimeLimit - st.sectorElapsed));
        String p = formatProgress(st.objectiveProgress, st.objectiveGoal);
        String mods = modifiersSummary(st.activeModifiers);
        SectorLore lore = loreFor(st.sector);
        String base = lore.location + "   " + lore.hudLead
                + "   OBJ: " + st.objectiveLabel
                + "   [" + p + "]   MOD: " + mods
                + "   " + failureHint(st.objectiveType)
                + "   T-" + left + "s";
        String side = sideObjectiveHud(st);
        String drop = bossDropHud(st);
        String withSide = side.isBlank() ? base : (base + "   SIDE: " + side);
        return drop.isBlank() ? withSide : (withSide + "   DROP: " + drop);
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

    public static boolean isTransitioning(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && (st.transitionTimer > 0 || st.awaitingEpisodeLaunch);
    }

    public static double transitionSeconds(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null || st.awaitingEpisodeLaunch) ? 0.0 : st.transitionTimer;
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
        return isCampaignActive(ctx);
    }

    public static boolean isPlayerControlLocked(GameContext ctx) {
        CampaignState st = state(ctx);
        return st != null && st.enabled && st.introSequenceActive;
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
        if (isCampaignActive(ctx)) {
            return ctx.player;
        }
        return EconomySystem.getDockedFriendlyBase(ctx);
    }

    public static int campaignOreCost(ShipRole role, int creditCost, int requiredTier) {
        int base = Math.max(18, (int) Math.round(Math.max(0, creditCost) * 0.18));
        int tierTax = Math.max(0, requiredTier) * 12;
        int roleTax = switch ((role == null) ? ShipRole.FRIGATE : role) {
            case PATROL, PICKET, FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE -> 0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, STEALTH_SHIP, TRANSPORT -> 22;
            case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, CARRIER, DRONE_CARRIER, SUPERSHIP -> 55;
            case TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                 INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN, MOBILE_STATION_TITAN, HYPERWEAPON_TITAN -> 90;
            case MOTHERSHIP -> 180;
            default -> 10;
        };
        return Math.max(15, base + tierTax + roleTax);
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
        if (livePersistentFleetSlots(st) >= CAMPAIGN_BLUE_FLEET_CAP) {
            EventSystem.showBanner(ctx, "BLUE FLEET COMMAND CAP REACHED", 1.8);
            return false;
        }

        int hangarTier = 0;
        BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(ctx.player, ignored -> new BaseUpgrades());
        if (up != null) {
            up.hangarLv = Math.max(up.hangarLv, 3);
            hangarTier = up.hangarLv;
        }
        if (hangarTier < requiredTier) {
            EventSystem.showBanner(ctx, "COMMAND BAY TIER TOO LOW", 1.6);
            return false;
        }

        int oreCost = campaignOreCost(role, creditCost, requiredTier);
        if (ctx.credits < Math.max(0, creditCost) || ctx.player.cargo < oreCost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS / ORE", 1.6);
            return false;
        }

        ctx.credits -= Math.max(0, creditCost);
        ctx.player.cargo = Math.max(0, ctx.player.cargo - oreCost);

        int slotId = st.nextPersistentFleetSlotId++;
        PersistentFleetEntry entry = new PersistentFleetEntry(slotId, role, generatedBlueFleetName(role, slotId));
        st.persistentBlueFleet.add(entry);
        TitanArchetype titan = TitanArchetype.fromShipRole(role);
        if (titan != null) {
            st.ownedTitans.add(titan);
        }
        spawnSinglePersistentBlueShip(ctx, st, entry, st.persistentBlueFleet.size() - 1);
        EventSystem.showBanner(ctx, "BLUE HULL COMMISSIONED: " + entry.name, 1.8);
        return true;
    }

    public static boolean launchPendingEpisode(GameContext ctx) {
        CampaignState st = state(ctx);
        if (ctx == null || st == null || !st.awaitingEpisodeLaunch || st.pendingEpisodeSector <= 0) return false;
        UISystem.closeAllOverlays(ctx);
        startSector(ctx, st.pendingEpisodeSector);
        return true;
    }

    public static int livePersistentFleetCount(GameContext ctx) {
        CampaignState st = state(ctx);
        return livePersistentFleetSlots(st);
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

        BaseUpgrades mothershipUpgrades = ctx.baseUpgrades.computeIfAbsent(ctx.player, ignored -> new BaseUpgrades());
        mothershipUpgrades.hangarLv = Math.max(mothershipUpgrades.hangarLv, 3);
        refreshCampaignAlliances(st);
    }

    private static void seedStartingBlueFleet(CampaignState st) {
        if (st == null || !st.persistentBlueFleet.isEmpty()) return;
        addPersistentFleetEntry(st, ShipRole.PICKET, "Blue Screen One");
        addPersistentFleetEntry(st, ShipRole.FRIGATE, "Blue Guard One");
        addPersistentFleetEntry(st, ShipRole.CIWS_CORVETTE, "Blue Guard Two");
    }

    private static void refreshCampaignAlliances(CampaignState st) {
        if (st == null) {
            Faction.clearCampaignAlliances();
            return;
        }
        boolean yellowAlliance = st.campaignBlueYellowAlliance || st.sector >= 10;
        st.campaignBlueYellowAlliance = yellowAlliance;
        Faction.configureCampaignAlliances(st.campaignBlueGreenAlliance, yellowAlliance);
    }

    private static void syncPersistentFleetCasualties(GameContext ctx, CampaignState st) {
        if (ctx == null || st == null || st.persistentBlueFleet.isEmpty()) return;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed || entry.activeShipId <= 0) continue;
            Ship live = findShipById(ctx, entry.activeShipId);
            if (live == null || !live.alive || live.dying || live.hp <= 0) {
                entry.destroyed = true;
                entry.activeShipId = -1;
            }
        }
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
        if (ctx == null || st == null) return;
        int liveIndex = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            spawnSinglePersistentBlueShip(ctx, st, entry, liveIndex++);
        }
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
        ctx.baseUpgrades.computeIfAbsent(base, ignored -> new BaseUpgrades());
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

        st.sector = sector;
        st.act = actForSector(sector);
        st.transitionTimer = 0.0;
        st.awaitingEpisodeLaunch = false;
        st.pendingEpisodeSector = 0;
        st.sectorElapsed = 0.0;
        st.kills = 0;
        st.knownHostiles.clear();
        st.authoredObjectiveHostiles.clear();
        st.authoredObjectiveKills = 0;
        st.authoredWaveCursor = 0;
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

        refreshCampaignAlliances(st);
        resetPersistentFleetSpawnHandles(st);
        pruneTransientUnits(ctx);
        regroupPlayerAtAlliedBase(ctx);
        SpawnSystem.spawnAsteroidField(ctx);
        healAndRefitPlayer(ctx);
        ensureCampaignTitanInfrastructure(ctx);

        SectorScript script = configureObjective(ctx);
        applySectorModifiers(ctx, st, script);
        spawnSectorForces(ctx);
        spawnPersistentBlueFleet(ctx, st);
        st.enemyBaseWinConditionActive = hasLiveEnemyBase(ctx);
        snapshotHostiles(ctx, st.knownHostiles);

        ctx.enemyWaveTimer = nextWaveDelay(ctx);

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
        st.sideObjectiveCompleted = false;
        st.sideObjectiveFailed = false;
        st.sideObjectiveBaseKills = st.kills;
        st.sideObjectiveStartPlayerHp = (ctx.player != null) ? ctx.player.hp : 0;
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
            default -> spawnSector12(ctx, st);
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
        st.introSequenceActive = true;
        st.introPhase = 0;
        st.introTimer = 0.0;
        st.cinematicFocusX = px + 40.0;
        st.cinematicFocusY = py - 40.0;
        st.introWarpX = GameMath.clamp(px + 900.0, 220.0, ctx.WORLD_W - 220.0);
        st.introWarpY = GameMath.clamp(py - 90.0, 220.0, ctx.WORLD_H - 220.0);
    }

    private static void spawnSector2(GameContext ctx, CampaignState st) {
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.VANGUARD_TITAN, 760, -80);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 860, -160);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.CIWS_CORVETTE, 900, -40);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 980, 60);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 1020, 140);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -360, 60, "Green Bulwark Titan Broker Shield");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 70, "Green Escort Spear");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -200, -50, "Green Screen Lance");
    }

    private static void spawnSector3(GameContext ctx, CampaignState st) {
        st.captureArmed = false;
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

    private static void spawnSector4(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -320, 40, "Green Bulwark Titan Vigilant Home");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Pursuit Screen");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Pursuit Flak");
        st.bossTargetId = spawnBoss(ctx, ShipRole.INTERDICTION_TITAN, "AI PURSUIT TITAN RED KNIFE", 1.55, 1.65);
    }

    private static void spawnSector5(GameContext ctx, CampaignState st) {
        st.escortShip = spawnEscortTitan(ctx, ShipRole.TRANSPORT_TITAN, Faction.TEAM_C, "Green Exodus Transport Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CARRIER_SUPPORT_TITAN, Faction.TEAM_C, -340, -120, "Green Carrier Support Titan Hearthwing");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 560, -120);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 760, -200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 860, 40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.VANGUARD_TITAN, 980, 120);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Refugee Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Refugee Flak");
    }

    private static void spawnSector6(GameContext ctx, CampaignState st) {
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

    private static void spawnSector7(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 140, 220, ctx.WORLD_H - 220);
        spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 180, st.captureY - 130);
        spawnEnemyAtPoint(ctx, ShipRole.INTERDICTION_TITAN, st.captureX + 260, st.captureY + 20);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 230, st.captureY + 80);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX - 200, st.captureY - 10);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -360, 80, "Green Contract Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -140, 80, "Green Contract Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -220, -60, "Green Contract Screen");
    }

    private static void spawnSector8(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.COMMAND_INTEL_TITAN, Faction.TEAM_C, -340, 90, "Green Contract Command Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120, 70, "Green Siege Scout");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_C, -180, -60, "Green Siege Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -250, 90, "Green Siege Cruiser");
        st.bossTargetId = spawnBoss(ctx, ShipRole.ARTILLERY_TITAN, "ASH GATE ARTILLERY TITAN", 1.60, 1.75);
    }

    private static void spawnSector9(GameContext ctx, CampaignState st) {
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

    private static void spawnSector10(GameContext ctx, CampaignState st) {
        st.escortShip = spawnEscortTitan(ctx, ShipRole.BOARDING_RECOVERY_TITAN, Faction.TEAM_D, "Liberated Yellow Recovery Titan");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CARRIER_SUPPORT_TITAN, Faction.TEAM_C, -360, -110, "Green Carrier Support Titan");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 640, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 740, 110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.VANGUARD_TITAN, 990, -20);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 880, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 840, -220);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_D, -140, 70, "Yellow Breakchain Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_D, -210, -60, "Yellow Breakchain Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -300, 60, "Green Liberation Cruiser");
    }

    private static void spawnSector11(GameContext ctx, CampaignState st) {
        spawnEnemyAtPlayerOffset(ctx, ShipRole.INTERDICTION_TITAN, 760, -120);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, 920, 80);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 790, 150);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 860, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 1020, 70);
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.ARTILLERY_TITAN, Faction.TEAM_C, -380, -40, "Green Artillery Titan Homebound");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_D, -140, 80, "Yellow Return Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_D, -220, -70, "Yellow Return Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -320, 20, "Green Earthway Cruiser");
    }

    private static void spawnSector12(GameContext ctx, CampaignState st) {
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BULWARK_TITAN, Faction.TEAM_C, -420, -120, "Green Bulwark Titan Aegis Return");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.ARTILLERY_TITAN, Faction.TEAM_C, -500, 40, "Green Artillery Titan Homebound");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CARRIER_SUPPORT_TITAN, Faction.TEAM_D, -540, 180, "Yellow Carrier Support Titan Renewal");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_D, -140, 80, "Yellow Earthfall Guard");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, Faction.TEAM_D, -220, -70, "Yellow Earthfall Flak");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, Faction.TEAM_C, -320, 20, "Green Homefront Cruiser");
        spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.BATTLECRUISER, Faction.TEAM_C, -420, -40, "Green Breakthrough Battlecruiser");
        st.bossTargetId = spawnFinalBoss(ctx);
    }

    private static void updateAuthoredSectorScript(GameContext ctx, CampaignState st) {
        if (st == null) return;
        if (st.sector > AUTHORED_VERTICAL_SLICE_LAST_SECTOR) return;

        switch (st.sector) {
            case 1 -> updateSector1Script(ctx, st);
            case 2 -> updateSector2Script(ctx, st);
            case 3 -> updateSector3Script(ctx, st);
            default -> {
                // No-op.
            }
        }
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
        if (!st.captureArmed) {
            if (st.authoredObjectiveHostiles.isEmpty()) {
                st.captureArmed = true;
                st.objectiveLabel = "Hold the relay uplink while jump authority is restored";
                st.authoredWaveCursor = 0;
                EventSystem.showBanner(ctx, "RELAY SECURED: HOLD THE UPLINK", 2.2);
                logTelemetry("sector_script", "sector=3 capture_armed t=" + Math.round(st.sectorElapsed));
            }
            return;
        }

        if (st.authoredWaveCursor == 0 && st.objectiveProgress >= 20.0) {
            spawnEnemyAtPoint(ctx, ShipRole.PATROL, st.captureX + 220, st.captureY - 120);
            spawnEnemyAtPoint(ctx, ShipRole.PICKET, st.captureX + 260, st.captureY + 40);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=3 wave=1 p=" + Math.round(st.objectiveProgress));
            return;
        }
        if (st.authoredWaveCursor == 1 && st.objectiveProgress >= 65.0) {
            spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX + 250, st.captureY - 150);
            spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 280, st.captureY + 10);
            spawnEnemyAtPoint(ctx, ShipRole.PATROL, st.captureX + 210, st.captureY + 150);
            spawnCampaignFactionAtPlayerOffset(ctx, ShipRole.FRIGATE, Faction.TEAM_C, -240, 120, "Green Uplink Guard");
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=3 wave=2 p=" + Math.round(st.objectiveProgress));
            return;
        }
        if (st.authoredWaveCursor == 2 && st.objectiveProgress >= 95.0) {
            spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX - 280, st.captureY - 40);
            spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX + 320, st.captureY + 70);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "HOLD THE EARTH VECTOR", 2.0);
            logTelemetry("sector_script", "sector=3 wave=3 p=" + Math.round(st.objectiveProgress));
        }
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
        int idx = Math.max(1, Math.min(12, sector));
        return SCRIPTS[idx];
    }

    private static SectorLore loreFor(int sector) {
        int idx = Math.max(1, Math.min(12, sector));
        SectorLore lore = LORE[idx];
        if (lore != null) return lore;
        return new SectorLore(idx, "UNTITLED SECTOR", "Unknown Theater", "Push the fleet onward.", "The fleet keeps moving.");
    }

    private static String actTitleFor(int act) {
        int idx = Math.max(1, Math.min(ACT_TITLES.length - 1, act));
        return ACT_TITLES[idx];
    }

    private static SideObjectiveScript sideScriptFor(int sector) {
        int idx = Math.max(1, Math.min(12, sector));
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
                case 1 -> 1.20;
                case 2 -> 1.15;
                case 3 -> 1.10;
                default -> 1.0;
            };
            double oreMul = switch (st.sector) {
                case 1 -> 1.15;
                case 2 -> 1.10;
                case 3 -> 1.08;
                default -> 1.0;
            };
            st.sectorCreditBonusMul *= sectorBonusMul;
            st.oreCreditMul *= oreMul;
        }
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
                if (st.sector == 2) {
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
                st.objectiveProgress = Math.min(st.objectiveGoal, st.objectiveProgress + dt);
            }
            case CAPTURE -> {
                if (st.sector == 3 && !st.captureArmed) {
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
                if (ctx.player == null || !ctx.player.alive || ctx.player.hp <= 0) {
                    markSideObjectiveFailed(ctx, st, "player_down");
                    return;
                }
                if (ctx.player.hp < st.sideObjectiveStartPlayerHp) {
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
        String bossDrop = grantBossDrop(ctx);
        int nextSector = st.sector + 1;
        boolean checkpointSaved = nextSector <= st.totalSectors && saveCheckpoint(ctx, st, nextSector);
        if (!checkpointSaved && nextSector > st.totalSectors) {
            CampaignCheckpointStore.clear();
        }

        boolean actBreak = isActBreakAfter(st.sector);
        SectorLore clearedLore = loreFor(st.sector);
        SectorLore nextLore = loreFor(Math.min(st.totalSectors, Math.max(1, nextSector)));
        st.transitionTimer = 0.0;
        st.awaitingEpisodeLaunch = nextSector <= st.totalSectors;
        st.pendingEpisodeSector = st.awaitingEpisodeLaunch ? nextSector : 0;
        st.transitionLabel = st.awaitingEpisodeLaunch
                ? ("EPISODE " + nextSector + ": " + nextLore.title)
                : (actBreak
                ? ("ACT " + (st.act + 1) + ": " + actTitleFor(st.act + 1))
                : ("JUMP TO " + nextLore.title));
        st.transitionSummaryTop = clearedLore.title + " secure. " + clearedLore.completionLead;
        st.transitionSummaryBottom = "+" + bonus + " credits   |   DOCTRINE: " + st.branchRoute
                + "   |   MOD: " + modifiersSummary(st.activeModifiers)
                + sideRewardSummary(st, sideBonus)
                + (storyReward.isBlank() ? "" : "   |   " + storyReward)
                + (bossDrop.isBlank() ? "" : "   |   DROP: " + bossDrop)
                + (unlock.isBlank() ? "" : "   |   " + unlock)
                + (checkpointSaved ? "   |   CHECKPOINT SAVED" : "")
                + (st.awaitingEpisodeLaunch ? "   |   PRESS ENTER TO LAUNCH" : "");
        quietEpisodeInterlude(ctx, st);
        EventSystem.showBanner(ctx,
                clearedLore.title + " SECURE  +" + bonus + " CREDITS"
                        + (sideBonus > 0 ? "  +SIDE " + sideBonus : "")
                        + (storyReward.isBlank() ? "" : "  FLEET EXPANDED")
                        + (bossDrop.isBlank() ? "" : "  DROP ACQUIRED")
                        + "  DOCTRINE " + st.branchRoute
                        + (checkpointSaved ? "  CHECKPOINT SAVED" : "")
                        + (st.awaitingEpisodeLaunch ? "  EPISODE READY" : "")
                        + (!st.awaitingEpisodeLaunch && actBreak ? "  ACT BREAK" : ""),
                st.awaitingEpisodeLaunch ? 3.2 : (actBreak ? 4.0 : 2.4));
        logTelemetry("sector_clear",
                "sector=" + st.sector +
                        " elapsedSec=" + Math.round(st.sectorElapsed) +
                        " objective=" + st.objectiveType +
                        " bonus=" + bonus +
                        " sideBonus=" + sideBonus +
                        " storyReward=" + (storyReward.isBlank() ? "none" : storyReward) +
                        " drop=" + (bossDrop.isBlank() ? "none" : bossDrop) +
                        " checkpoint=" + checkpointSaved +
                        " route=" + st.branchRoute +
                        " branchScore=" + st.branchScore);
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
            case 4 -> grantStoryResources(ctx, 220, 70, "ESCAPE CACHE RECOVERED");
            case 7 -> grantStoryResources(ctx, 340, 110, "GREEN MARKET ACCESS OPEN");
            case 10 -> grantStoryResources(ctx, 420, 140, "LIBERATION STORES TRANSFERRED");
            default -> "";
        };
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
            case 4 -> grantBossDropAegisArray(ctx, st);
            case 8 -> grantBossDropMissileCore(ctx, st);
            case 12 -> grantBossDropFlagCore(ctx, st);
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
        logTelemetry("boss_drop", "sector=4 drop=AegisArray");
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
        logTelemetry("boss_drop", "sector=8 drop=MissileCore turrets=" + Math.max(1, missileTurrets));
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
        logTelemetry("boss_drop", "sector=12 drop=FlagCore");
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

    private static void ensureCampaignHangarTier(GameContext ctx, Ship base) {
        if (ctx == null || base == null || base.role != ShipRole.BASE) return;
        BaseUpgrades upgrades = ctx.baseUpgrades.computeIfAbsent(base, ignored -> new BaseUpgrades());
        upgrades.hangarLv = Math.max(upgrades.hangarLv, 3);
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
            } else if (st.sideObjectiveType == SideObjectiveType.NO_HULL_DAMAGE_WINDOW
                    && ctx.player != null
                    && ctx.player.hp < st.sideObjectiveStartPlayerHp) {
                markSideObjectiveFailed(ctx, st, "hull_damage");
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
        if (sector <= 4) return 1;
        if (sector <= 8) return 2;
        return 3;
    }

    private static boolean isActBreakAfter(int sector) {
        return sector == 4 || sector == 8;
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
        cp.persistentBlueFleet = serializePersistentBlueFleet(st.persistentBlueFleet);
        cp.campaignBlueYellowAlliance = st.campaignBlueYellowAlliance;

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

        Ship anchor = currentBaseUpgradeAnchor(ctx);
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
        restorePersistentBlueFleet(st, cp.persistentBlueFleet);

        restorePlayerFromCheckpoint(ctx.player, cp);
        Ship anchor = currentBaseUpgradeAnchor(ctx);
        restoreBaseCheckpoint(anchor, ctx.baseUpgrades.get(anchor),
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
            upgrades.hullLv = MathUtil.clamp(hullLv, 0, 3);
            upgrades.shieldLv = MathUtil.clamp(shieldLv, 0, 3);
            upgrades.turretLv = MathUtil.clamp(turretLv, 0, 3);
            upgrades.miningLv = MathUtil.clamp(miningLv, 0, 3);
            upgrades.hangarLv = MathUtil.clamp(hangarLv, 0, 3);
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
        if (player == null || player.turrets.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Turret turret : player.turrets) {
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
                    .append(turret.primary);
        }
        return sb.toString();
    }

    private static void restoreTurrets(Player player, String raw) {
        if (player == null || raw == null || raw.isBlank()) return;
        player.turrets.clear();
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
                player.addTurret(turret);
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
        if (st == null || role == null) return;
        st.persistentBlueFleet.add(new PersistentFleetEntry(st.nextPersistentFleetSlotId++, role, name));
    }

    private static int livePersistentFleetSlots(CampaignState st) {
        if (st == null) return 0;
        int count = 0;
        for (PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry != null && !entry.destroyed) count++;
        }
        return count;
    }

    private static void spawnSinglePersistentBlueShip(GameContext ctx, CampaignState st, PersistentFleetEntry entry, int liveIndex) {
        if (ctx == null || st == null || entry == null || ctx.player == null || entry.destroyed) return;
        double lane = liveIndex % 3;
        double row = liveIndex / 3;
        double side = lane - 1.0;
        double aft = 180.0 + row * 92.0;
        double lateral = side * (130.0 + row * 12.0);
        double sx = ctx.player.x - Math.cos(ctx.player.angle) * aft - Math.sin(ctx.player.angle) * lateral;
        double sy = ctx.player.y - Math.sin(ctx.player.angle) * aft + Math.cos(ctx.player.angle) * lateral;
        sx = GameMath.clamp(sx, 40.0, ctx.WORLD_W - 40.0);
        sy = GameMath.clamp(sy, 40.0, ctx.WORLD_H - 40.0);

        Ship ship = new FleetShip(entry.role, Faction.ALLY, sx, sy);
        ship.name = entry.name;
        ship.angle = ctx.player.angle;
        ship.vx = ctx.player.vx;
        ship.vy = ctx.player.vy;
        ship.minerHomeBase = ctx.player;
        ctx.ships.add(ship);
        try { DoctrineRegistry.applyToShip(ship); } catch (Throwable ignored) {}
        entry.activeShipId = ship.id;
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
            String encodedName = encoder.encodeToString(entry.name.getBytes(StandardCharsets.UTF_8));
            sb.append(entry.slotId).append(',')
                    .append(entry.role.name()).append(',')
                    .append(entry.destroyed).append(',')
                    .append(encodedName);
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
            String[] parts = entryRaw.split(",", 4);
            if (parts.length < 4) continue;
            try {
                int slotId = Math.max(1, Integer.parseInt(parts[0].trim()));
                ShipRole role = parseEnum(parts[1], ShipRole.FRIGATE);
                boolean destroyed = Boolean.parseBoolean(parts[2].trim());
                String name = new String(decoder.decode(parts[3].trim()), StandardCharsets.UTF_8);
                PersistentFleetEntry entry = new PersistentFleetEntry(slotId, role, name);
                entry.destroyed = destroyed;
                st.persistentBlueFleet.add(entry);
                st.nextPersistentFleetSlotId = Math.max(st.nextPersistentFleetSlotId, slotId + 1);
            } catch (Exception ignored) {
                // Skip malformed checkpoint fleet entries.
            }
        }
    }

}
