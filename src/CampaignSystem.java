import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
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

    private static final int AUTHORED_VERTICAL_SLICE_LAST_SECTOR = 3;

    private static final SectorScript[] SCRIPTS = new SectorScript[]{
            null,
            new SectorScript(1, ObjectiveType.SURVIVE, "Hold perimeter through attack waves", 360, 630, BossKind.NONE, MapModifier.NONE),
            new SectorScript(2, ObjectiveType.DESTROY, "Destroy scripted strike group", 16, 720, BossKind.NONE, MapModifier.NEBULA),
            new SectorScript(3, ObjectiveType.CAPTURE, "Eliminate relay guard, then hold beacon", 120, 780, BossKind.NONE, MapModifier.DEBRIS_FIELD),
            new SectorScript(4, ObjectiveType.BOSS, "Eliminate Mid-Boss Alpha", 1, 720, BossKind.MID_ALPHA, MapModifier.EMP_ZONE),
            new SectorScript(5, ObjectiveType.ESCORT, "Escort convoy to jump corridor", 180, 720, BossKind.NONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(6, ObjectiveType.DESTROY, "Break enemy vanguard", 24, 720, BossKind.NONE, MapModifier.RICH_DEPOSITS),
            new SectorScript(7, ObjectiveType.CAPTURE, "Secure deep-space array", 120, 720, BossKind.NONE, MapModifier.SOLAR_STORM),
            new SectorScript(8, ObjectiveType.BOSS, "Eliminate Mid-Boss Beta", 1, 780, BossKind.MID_BETA, MapModifier.GRAVITY_SHEAR),
            new SectorScript(9, ObjectiveType.SURVIVE, "Hold line under siege", 210, 720, BossKind.NONE, MapModifier.NEBULA, MapModifier.SOLAR_STORM),
            new SectorScript(10, ObjectiveType.ESCORT, "Escort carrier task force", 210, 780, BossKind.NONE, MapModifier.DEBRIS_FIELD, MapModifier.SUPPLY_WINDFALL),
            new SectorScript(11, ObjectiveType.DESTROY, "Cripple enemy fleet core", 28, 780, BossKind.NONE, MapModifier.EMP_ZONE, MapModifier.RESOURCE_DROUGHT),
            new SectorScript(12, ObjectiveType.FINAL_BOSS, "Destroy Enemy Flagship", 1, 840, BossKind.FINAL, MapModifier.SOLAR_STORM, MapModifier.GRAVITY_SHEAR)
    };

    private static final SideObjectiveScript[] SIDE_SCRIPTS = new SideObjectiveScript[]{
            null,
            new SideObjectiveScript(1, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Avoid hull damage for 120s", 120, 160),
            new SideObjectiveScript(2, SideObjectiveType.KILL_COUNT, "Destroy 12 hostiles", 12, 200),
            new SideObjectiveScript(3, SideObjectiveType.CLEAR_BEFORE_TIME, "Clear sector in 660s", 660, 240),
            new SideObjectiveScript(4, SideObjectiveType.CLEAR_BEFORE_TIME, "Clear sector in 560s", 560, 190),
            new SideObjectiveScript(5, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Avoid hull damage for 120s", 120, 210),
            new SideObjectiveScript(6, SideObjectiveType.KILL_COUNT, "Destroy 14 hostiles", 14, 230),
            new SideObjectiveScript(7, SideObjectiveType.CLEAR_BEFORE_TIME, "Clear sector in 560s", 560, 250),
            new SideObjectiveScript(8, SideObjectiveType.KILL_COUNT, "Destroy 8 hostiles", 8, 280),
            new SideObjectiveScript(9, SideObjectiveType.KILL_COUNT, "Destroy 16 hostiles", 16, 300),
            new SideObjectiveScript(10, SideObjectiveType.NO_HULL_DAMAGE_WINDOW, "Avoid hull damage for 150s", 150, 320),
            new SideObjectiveScript(11, SideObjectiveType.CLEAR_BEFORE_TIME, "Clear sector in 620s", 620, 350),
            new SideObjectiveScript(12, SideObjectiveType.CLEAR_BEFORE_TIME, "Clear sector in 700s", 700, 400)
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
        STANDARD("CAMPAIGN COMPLETE", "VICTORY: CAMPAIGN COMPLETE"),
        STRATEGIC_SUPREMACY("ALT ENDING: STRATEGIC SUPREMACY", "VICTORY: STRATEGIC SUPREMACY"),
        TRUE_RESTORATION("TRUE ENDING: RESTORED FRONTIER", "VICTORY: TRUE ENDING UNLOCKED"),
        PYRRHIC("ALT ENDING: PYRRHIC VICTORY", "VICTORY: PYRRHIC VICTORY");

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
        CampaignCheckpointStore.Checkpoint checkpoint = ctx.config.resumeCampaign ? CampaignCheckpointStore.load() : null;
        if (checkpoint != null && checkpoint.isUsable() && applyCheckpoint(ctx, st, checkpoint)) {
            EventSystem.showBanner(ctx, "CAMPAIGN RESUMED: SECTOR " + checkpoint.nextSector + "/12", 2.2);
            startSector(ctx, checkpoint.nextSector);
            return;
        }

        CampaignCheckpointStore.clear();
        applyPersistedUnlockProfile(ctx, st);
        persistRunStart(ctx);

        EventSystem.showBanner(ctx, "CAMPAIGN START: SECTOR 1/12", 2.0);
        startSector(ctx, 1);
    }

    public static void update(GameContext ctx, double dt) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled || ctx.gameOver) return;

        if (ctx.player == null || !ctx.player.alive || ctx.player.hp <= 0) {
            failRun(ctx, "DEFEAT: FLAGSHIP LOST");
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
        return "ACT " + st.act + "   SECTOR " + st.sector + "/" + st.totalSectors + "   ROUTE " + st.branchRoute;
    }

    public static String hudObjectiveDetail(GameContext ctx) {
        CampaignState st = state(ctx);
        if (st == null || !st.enabled) return "";
        int left = (int) Math.ceil(Math.max(0.0, st.sectorTimeLimit - st.sectorElapsed));
        String p = formatProgress(st.objectiveProgress, st.objectiveGoal);
        String mods = modifiersSummary(st.activeModifiers);
        String base = st.objectiveLabel + "   [" + p + "]   MOD: " + mods + "   " + failureHint(st.objectiveType) + "   T-" + left + "s";
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
        return st != null && st.enabled && st.transitionTimer > 0;
    }

    public static double transitionSeconds(GameContext ctx) {
        CampaignState st = state(ctx);
        return (st == null) ? 0.0 : st.transitionTimer;
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

    private static void startSector(GameContext ctx, int sector) {
        CampaignState st = state(ctx);
        if (st == null) return;

        st.sector = sector;
        st.act = actForSector(sector);
        st.transitionTimer = 0.0;
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

        pruneTransientUnits(ctx);
        regroupPlayerAtAlliedBase(ctx);
        SpawnSystem.spawnAsteroidField(ctx);
        healAndRefitPlayer(ctx);

        SectorScript script = configureObjective(ctx);
        applySectorModifiers(ctx, st, script);
        spawnSectorForces(ctx);
        st.enemyBaseWinConditionActive = hasLiveEnemyBase(ctx);
        snapshotHostiles(ctx, st.knownHostiles);

        ctx.enemyWaveTimer = nextWaveDelay(ctx);

        String msg = "SECTOR " + st.sector + "/" + st.totalSectors + ": " + st.objectiveLabel + " [" + modifiersSummary(st.activeModifiers) + "]";
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
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 520, -130);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 640, 140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 760, -260);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 820, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.PICKET, -120, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.PATROL, -180, -30);
    }

    private static void spawnSector2(GameContext ctx, CampaignState st) {
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 560, -200);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 640, -130);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.FRIGATE, 740, -220);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PATROL, 860, 90);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.PICKET, 920, 130);
        spawnAuthoredObjectiveEnemyAtPlayerOffset(ctx, st, ShipRole.MISSILE_BOAT, 980, -40);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -200, -50);
    }

    private static void spawnSector3(GameContext ctx, CampaignState st) {
        st.captureArmed = false;
        st.captureX = GameMath.clamp(ctx.player.x + 700, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 220, 220, ctx.WORLD_H - 220);
        st.captureRadius = 200.0;
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.FRIGATE, st.captureX + 90, st.captureY - 90);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.CIWS_CORVETTE, st.captureX - 120, st.captureY + 60);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.PATROL, st.captureX + 150, st.captureY + 100);
        spawnAuthoredObjectiveEnemyAtPoint(ctx, st, ShipRole.MISSILE_BOAT, st.captureX + 220, st.captureY - 40);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -100, 60);
        spawnAllyAtPlayerOffset(ctx, ShipRole.PICKET, -170, -40);
    }

    private static void spawnSector4(GameContext ctx, CampaignState st) {
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -120, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -180, -60);
        st.bossTargetId = spawnBoss(ctx, "MID-BOSS ALPHA");
    }

    private static void spawnSector5(GameContext ctx, CampaignState st) {
        st.escortShip = spawnConvoy(ctx, "Convoy");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 560, -120);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 760, -200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 860, 40);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 920, 120);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -120, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -180, -60);
    }

    private static void spawnSector6(GameContext ctx, CampaignState st) {
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 560, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 720, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 760, -30);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 900, -110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 980, 90);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PICKET, 840, 180);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.PATROL, 1020, 20);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -120, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -180, -60);
    }

    private static void spawnSector7(GameContext ctx, CampaignState st) {
        st.captureX = GameMath.clamp(ctx.player.x + 760, 220, ctx.WORLD_W - 220);
        st.captureY = GameMath.clamp(ctx.player.y + 140, 220, ctx.WORLD_H - 220);
        spawnEnemyAtPoint(ctx, ShipRole.LIGHT_CRUISER, st.captureX + 180, st.captureY - 130);
        spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX - 120, st.captureY + 90);
        spawnEnemyAtPoint(ctx, ShipRole.MISSILE_BOAT, st.captureX + 230, st.captureY + 80);
        spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX - 200, st.captureY - 10);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 80);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -220, -60);
    }

    private static void spawnSector8(GameContext ctx, CampaignState st) {
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -120, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -180, -60);
        spawnAllyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, -250, 90);
        st.bossTargetId = spawnBoss(ctx, "MID-BOSS BETA");
    }

    private static void spawnSector9(GameContext ctx, CampaignState st) {
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 580, -180);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 700, 170);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 820, -130);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 90);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 1050, -20);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -210, -60);
    }

    private static void spawnSector10(GameContext ctx, CampaignState st) {
        st.escortShip = spawnConvoy(ctx, "Carrier Task Convoy");
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 640, -140);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 740, 110);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BATTLECRUISER, 990, -20);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 880, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 840, -220);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -210, -60);
        spawnAllyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, -300, 60);
    }

    private static void spawnSector11(GameContext ctx, CampaignState st) {
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 700, -170);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, 790, 150);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.BATTLECRUISER, 980, -80);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.MISSILE_BOAT, 910, 200);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, 860, -240);
        spawnEnemyAtPlayerOffset(ctx, ShipRole.FRIGATE, 1020, 70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 80);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -220, -70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, -320, 20);
    }

    private static void spawnSector12(GameContext ctx, CampaignState st) {
        spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -140, 80);
        spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -220, -70);
        spawnAllyAtPlayerOffset(ctx, ShipRole.LIGHT_CRUISER, -320, 20);
        spawnAllyAtPlayerOffset(ctx, ShipRole.BATTLECRUISER, -420, -40);
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
            spawnAllyAtPlayerOffset(ctx, ShipRole.PICKET, -240, 90);
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
            spawnAllyAtPlayerOffset(ctx, ShipRole.CIWS_CORVETTE, -240, -60);
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
                st.objectiveLabel = "Hold relay beacon";
                st.authoredWaveCursor = 0;
                EventSystem.showBanner(ctx, "RELAY SECURED: HOLD POSITION", 2.2);
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
            spawnAllyAtPlayerOffset(ctx, ShipRole.FRIGATE, -240, 120);
            st.authoredWaveCursor++;
            logTelemetry("sector_script", "sector=3 wave=2 p=" + Math.round(st.objectiveProgress));
            return;
        }
        if (st.authoredWaveCursor == 2 && st.objectiveProgress >= 95.0) {
            spawnEnemyAtPoint(ctx, ShipRole.FRIGATE, st.captureX - 280, st.captureY - 40);
            spawnEnemyAtPoint(ctx, ShipRole.CIWS_CORVETTE, st.captureX + 320, st.captureY + 70);
            st.authoredWaveCursor++;
            EventSystem.showBanner(ctx, "HOLD THE BEACON", 2.0);
            logTelemetry("sector_script", "sector=3 wave=3 p=" + Math.round(st.objectiveProgress));
        }
    }

    private static int spawnBoss(GameContext ctx, String name) {
        Ship boss = SpawnSystem.spawnEnemy(ctx, ShipRole.BATTLECRUISER, ctx.player.x + 760, ctx.player.y - 120);
        boss.name = name;
        boss.hpMax = (int) Math.round(boss.hpMax * 1.9);
        boss.hp = boss.hpMax;
        boss.shieldMax *= 1.7;
        boss.shield = boss.shieldMax;
        for (Turret t : boss.turrets) {
            t.damage = Math.max(1, (int) Math.round(t.damage * 1.35));
            t.cooldown = Math.max(0.05, t.cooldown * 0.88);
        }
        SpawnSystem.spawnEnemy(ctx, ShipRole.MISSILE_BOAT, boss.x + 130, boss.y - 60);
        SpawnSystem.spawnEnemy(ctx, ShipRole.CIWS_CORVETTE, boss.x - 130, boss.y + 60);
        return boss.id;
    }

    private static int spawnFinalBoss(GameContext ctx) {
        Ship boss = SpawnSystem.spawnEnemy(ctx, ShipRole.DREADNOUGHT, ctx.player.x + 900, ctx.player.y - 180);
        boss.name = "ENEMY FLAGSHIP";
        boss.hpMax = (int) Math.round(boss.hpMax * 2.6);
        boss.hp = boss.hpMax;
        boss.shieldMax *= 2.2;
        boss.shield = boss.shieldMax;
        boss.shieldRegen *= 1.5;
        for (Turret t : boss.turrets) {
            t.damage = Math.max(1, (int) Math.round(t.damage * 1.55));
            t.cooldown = Math.max(0.05, t.cooldown * 0.84);
        }

        SpawnSystem.spawnEnemy(ctx, ShipRole.BATTLESHIP, boss.x + 170, boss.y + 90);
        SpawnSystem.spawnEnemy(ctx, ShipRole.BATTLECRUISER, boss.x - 170, boss.y - 90);
        SpawnSystem.spawnEnemy(ctx, ShipRole.MISSILE_BOAT, boss.x + 220, boss.y - 140);
        return boss.id;
    }

    private static SectorScript scriptFor(int sector) {
        int idx = Math.max(1, Math.min(12, sector));
        return SCRIPTS[idx];
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

    private static Ship spawnEnemyAtPoint(GameContext ctx, ShipRole role, double x, double y) {
        return SpawnSystem.spawnEnemy(ctx, role, x, y);
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
                    failRun(ctx, "DEFEAT: CONVOY DESTROYED");
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
        String bossDrop = grantBossDrop(ctx);
        int nextSector = st.sector + 1;
        boolean checkpointSaved = nextSector <= st.totalSectors && saveCheckpoint(ctx, st, nextSector);
        if (!checkpointSaved && nextSector > st.totalSectors) {
            CampaignCheckpointStore.clear();
        }

        boolean actBreak = isActBreakAfter(st.sector);
        st.transitionTimer = actBreak ? 7.0 : 3.5;
        st.transitionLabel = actBreak
                ? ("ACT " + (st.act + 1) + " INBOUND")
                : ("JUMPING TO SECTOR " + (st.sector + 1));
        st.transitionSummaryTop = "Sector " + st.sector + " clear: " + st.objectiveType + " in " + Math.round(st.sectorElapsed) + "s";
        st.transitionSummaryBottom = "+" + bonus + " credits   |   MOD: " + modifiersSummary(st.activeModifiers)
                + sideRewardSummary(st, sideBonus)
                + "   |   ROUTE: " + st.branchRoute
                + (bossDrop.isBlank() ? "" : "   |   DROP: " + bossDrop)
                + (unlock.isBlank() ? "" : "   |   " + unlock)
                + (checkpointSaved ? "   |   CHECKPOINT SAVED" : "");
        EventSystem.showBanner(ctx,
                "SECTOR CLEARED +" + bonus + " CREDITS"
                        + (sideBonus > 0 ? "  +SIDE " + sideBonus : "")
                        + (bossDrop.isBlank() ? "" : "  DROP ACQUIRED")
                        + "  ROUTE " + st.branchRoute
                        + (checkpointSaved ? "  CHECKPOINT SAVED" : "")
                        + (actBreak ? "  ACT BREAK" : ""),
                actBreak ? 4.0 : 2.4);
        logTelemetry("sector_clear",
                "sector=" + st.sector +
                        " elapsedSec=" + Math.round(st.sectorElapsed) +
                        " objective=" + st.objectiveType +
                        " bonus=" + bonus +
                        " sideBonus=" + sideBonus +
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

        ctx.ships.removeIf(s -> s != null && s != ctx.player && s.role != ShipRole.BASE);
    }

    private static void regroupPlayerAtAlliedBase(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
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
        return sector == 4 || sector == 8 || sector == 11;
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
            case ESCORT -> "FAIL: convoy destroyed";
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

        copyBaseCheckpoint(ctx.allyBase, ctx.baseUpgrades.get(ctx.allyBase), true, cp);
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

        restorePlayerFromCheckpoint(ctx.player, cp);
        restoreBaseCheckpoint(ctx.allyBase, ctx.baseUpgrades.get(ctx.allyBase),
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

}
