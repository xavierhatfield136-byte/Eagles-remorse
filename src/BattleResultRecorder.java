import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures start/end ship state for one tactical engagement and exposes the
 * latest normalized result to AAR, academy, and future telemetry systems.
 */
public final class BattleResultRecorder {
    private static final double MIN_RECORDABLE_SECONDS = 0.15;

    private boolean active = false;
    private boolean finished = false;
    private String battleId = "";
    private long startedAtMillis = 0L;
    private double startedBattleElapsed = 0.0;
    private int startCredits = 0;
    private int startOre = 0;
    private final Map<Integer, BattleResult.ShipSnapshot> startSnapshots = new LinkedHashMap<>();
    private BattleResult latestResult = null;
    private AfterActionReport latestReport = null;
    private PersistentBattleRecord latestPersistentRecord = null;

    public void update(GameContext ctx) {
        if (ctx == null || ctx.config == null) return;
        if (active && !finished && terminalContextReached(ctx)) {
            finish(ctx, terminalText(ctx));
            return;
        }
        if (!active && canStart(ctx)) {
            start(ctx);
        }
    }

    public void reset() {
        active = false;
        finished = false;
        battleId = "";
        startedAtMillis = 0L;
        startedBattleElapsed = 0.0;
        startCredits = 0;
        startOre = 0;
        startSnapshots.clear();
        latestResult = null;
        latestReport = null;
        latestPersistentRecord = null;
    }

    public boolean active() {
        return active && !finished;
    }

    public BattleResult latestResult() {
        return latestResult;
    }

    public AfterActionReport latestReport() {
        return latestReport;
    }

    public PersistentBattleRecord latestPersistentRecord() {
        return latestPersistentRecord;
    }

    public BattleResult finish(GameContext ctx, String terminalText) {
        if (ctx == null || finished || !active) return latestResult;
        ArrayList<BattleResult.ShipSnapshot> ended = new ArrayList<>();
        for (Ship ship : ctx.ships) {
            if (ship == null) continue;
            BattleResult.ShipSnapshot start = startSnapshots.get(ship.id);
            ended.add(snapshotFor(ctx, ship, start, shipDestroyed(ship), false));
        }
        for (BattleResult.ShipSnapshot start : startSnapshots.values()) {
            if (start == null || containsShip(ended, start.id)) continue;
            ended.add(new BattleResult.ShipSnapshot(
                    start.id,
                    start.stableShipId,
                    start.persistentFleetSlotId,
                    start.name,
                    start.faction,
                    start.role,
                    start.customContent,
                    start.academyTrainingShip,
                    start.startHull,
                    start.startShield,
                    0,
                    0.0,
                    start.maxHull,
                    start.maxShield,
                    true,
                    false,
                    start.kills,
                    Math.max(start.repairEstimate, BattleResult.repairEstimate(start.startHull, 0))));
        }
        latestResult = BattleResult.fromContext(
                ctx,
                ended,
                battleId,
                startedAtMillis,
                startedBattleElapsed,
                startCredits,
                startOre,
                terminalText);
        latestReport = BattleAnalysisService.analyze(latestResult);
        latestPersistentRecord = PersistentBattleRecord.from(latestResult, latestReport);
        finished = true;
        active = false;
        return latestResult;
    }

    private void start(GameContext ctx) {
        active = true;
        finished = false;
        battleId = BattleResult.sourceFor(ctx).name().toLowerCase() + "-"
                + Math.max(0L, ctx.config.seed) + "-"
                + Math.max(0L, Math.round(ctx.battleElapsed * 1000.0));
        startedAtMillis = System.currentTimeMillis();
        startedBattleElapsed = Math.max(0.0, ctx.battleElapsed);
        startCredits = Math.max(0, ctx.credits);
        startOre = currentOre(ctx);
        startSnapshots.clear();
        for (Ship ship : ctx.ships) {
            if (ship == null) continue;
            startSnapshots.put(ship.id, snapshotFor(ctx, ship, null, shipDestroyed(ship), false));
        }
    }

    private static BattleResult.ShipSnapshot snapshotFor(GameContext ctx,
                                                        Ship ship,
                                                        BattleResult.ShipSnapshot start,
                                                        boolean destroyed,
                                                        boolean withdrew) {
        int startHull = start == null ? ship.hp : start.startHull;
        double startShield = start == null ? ship.shield : start.startShield;
        CampaignSystem.PersistentFleetEntry fleetEntry = persistentFleetEntryForShip(ctx, ship);
        int persistentSlotId = fleetEntry == null ? (start == null ? -1 : start.persistentFleetSlotId) : fleetEntry.slotId;
        int kills = fleetEntry == null ? (start == null ? 0 : start.kills) : fleetEntry.kills;
        boolean academyTrainingShip = BattleResult.sourceFor(ctx) == BattleResult.BattleSource.ACADEMY
                || (ship.name != null && ship.name.toLowerCase(java.util.Locale.US).contains("training"));
        return new BattleResult.ShipSnapshot(
                ship.id,
                BattleResult.stableShipIdFor(ship.id, persistentSlotId),
                persistentSlotId,
                ship.name,
                ship.faction,
                ship.role,
                ship.customShipDefinitionId != null || ship.customShipDefinition != null,
                academyTrainingShip,
                startHull,
                startShield,
                Math.max(0, ship.hp),
                Math.max(0.0, ship.shield),
                Math.max(1, ship.hpMax),
                Math.max(0.0, ship.shieldMax),
                destroyed,
                withdrew,
                kills,
                BattleResult.repairEstimate(startHull, Math.max(0, ship.hp)));
    }

    private static CampaignSystem.PersistentFleetEntry persistentFleetEntryForShip(GameContext ctx, Ship ship) {
        if (ctx == null || ctx.campaign == null || ship == null) return null;
        for (CampaignSystem.PersistentFleetEntry entry : ctx.campaign.persistentBlueFleet) {
            if (entry != null && entry.activeShipId == ship.id) return entry;
        }
        return null;
    }

    private static boolean canStart(GameContext ctx) {
        if (ctx.gameOver) return false;
        if (ctx.multiplayerBattle && ctx.multiplayerAuthorityMode == MultiplayerAuthorityMode.CLIENT_PRESENTATION) return false;
        if (CampaignSystem.isCampaignMapScreenActive(ctx) || CampaignSystem.isFleetHubSession(ctx)) return false;
        if (BattleResult.sourceFor(ctx) == BattleResult.BattleSource.SHOWCASE) return false;
        return hasCombatRoster(ctx);
    }

    private static boolean hasCombatRoster(GameContext ctx) {
        if (ctx == null || ctx.ships == null || ctx.player == null || ctx.player.faction == null) return false;
        boolean friendly = false;
        boolean hostile = false;
        for (Ship ship : ctx.ships) {
            if (ship == null || shipDestroyed(ship) || ship.role == ShipRole.BASE) continue;
            if (ship.faction == null) continue;
            if (ship.faction.isFriendlyTo(ctx.player.faction)) friendly = true;
            if (ship.faction.isHostileTo(ctx.player.faction)) hostile = true;
            if (friendly && hostile) return true;
        }
        return false;
    }

    private static boolean terminalContextReached(GameContext ctx) {
        if (ctx == null) return false;
        double duration = Math.max(0.0, ctx.battleElapsed) - Math.max(0.0, ctx.battleResultRecorder.startedBattleElapsed);
        if (duration < MIN_RECORDABLE_SECONDS && !ctx.gameOver) return false;
        if (ctx.gameOver) return true;
        if (CampaignSystem.isCampaignMapScreenActive(ctx)) return true;
        if (!hasCombatRoster(ctx)) return true;
        return false;
    }

    private static String terminalText(GameContext ctx) {
        if (ctx == null) return "Battle ended";
        if (ctx.gameOverText != null && !ctx.gameOverText.isBlank()) return ctx.gameOverText;
        if (ctx.campaign != null) {
            String top = CampaignSystem.transitionSummaryTop(ctx);
            String bottom = CampaignSystem.transitionSummaryBottom(ctx);
            if (top != null && !top.isBlank()) {
                return bottom == null || bottom.isBlank() ? top : top + "  |  " + bottom;
            }
        }
        if (CampaignSystem.isCampaignMapScreenActive(ctx)) return "WITHDRAWAL COMPLETE";
        return "Battle ended";
    }

    private static boolean containsShip(List<BattleResult.ShipSnapshot> ships, int id) {
        for (BattleResult.ShipSnapshot ship : ships) {
            if (ship != null && ship.id == id) return true;
        }
        return false;
    }

    private static boolean shipDestroyed(Ship ship) {
        return ship == null || !ship.alive || ship.dying || ship.hp <= 0;
    }

    private static int currentOre(GameContext ctx) {
        if (ctx == null) return 0;
        if (ctx.campaign != null && ctx.campaign.oreLedger != null) {
            return Math.max(0, ctx.campaign.oreLedger.storedOre);
        }
        int ore = 0;
        if (ctx.player != null) ore += Math.max(0, ctx.player.cargo);
        if (ctx.allyBase != null) ore += Math.max(0, ctx.allyBase.oreStockpile);
        return ore;
    }
}
