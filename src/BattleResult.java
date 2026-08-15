import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import app.support.AppInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Normalized tactical battle outcome used by after-action reports, academy
 * feedback, and future Steam-facing onboarding telemetry.
 */
public final class BattleResult {
    public static final int BATTLE_RESULT_SCHEMA_VERSION = 1;

    public enum BattleSource {
        CAMPAIGN,
        CUSTOM_BATTLE,
        ACADEMY,
        LAST_STAND,
        RESOURCE_RUSH,
        FOUR_TEAM_DOMINATION,
        SHOOTING_RANGE,
        SHOWCASE,
        MULTIPLAYER,
        UNKNOWN
    }

    public enum TacticalResult {
        VICTORY,
        DEFEAT,
        WITHDRAWAL,
        DRAW,
        ONGOING,
        ABORTED,
        UNKNOWN
    }

    public enum MissionResult {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE,
        NOT_APPLICABLE,
        UNKNOWN
    }

    public static final class ShipSnapshot {
        public final int id;
        public final String stableShipId;
        public final int persistentFleetSlotId;
        public final String name;
        public final Faction faction;
        public final ShipRole role;
        public final boolean customContent;
        public final boolean academyTrainingShip;
        public final int startHull;
        public final double startShield;
        public final int endHull;
        public final double endShield;
        public final int maxHull;
        public final double maxShield;
        public final boolean destroyed;
        public final boolean withdrew;
        public final int kills;
        public final int repairEstimate;

        public ShipSnapshot(int id,
                            String name,
                            Faction faction,
                            ShipRole role,
                            boolean customContent,
                            int startHull,
                            double startShield,
                            int endHull,
                            double endShield,
                            int maxHull,
                            double maxShield,
                            boolean destroyed,
                            boolean withdrew) {
            this(id,
                    stableShipIdFor(id, -1),
                    -1,
                    name,
                    faction,
                    role,
                    customContent,
                    false,
                    startHull,
                    startShield,
                    endHull,
                    endShield,
                    maxHull,
                    maxShield,
                    destroyed,
                    withdrew,
                    0,
                    repairEstimate(startHull, endHull));
        }

        public ShipSnapshot(int id,
                            String stableShipId,
                            int persistentFleetSlotId,
                            String name,
                            Faction faction,
                            ShipRole role,
                            boolean customContent,
                            boolean academyTrainingShip,
                            int startHull,
                            double startShield,
                            int endHull,
                            double endShield,
                            int maxHull,
                            double maxShield,
                            boolean destroyed,
                            boolean withdrew,
                            int kills,
                            int repairEstimate) {
            this.id = id;
            this.stableShipId = trimmedOrFallback(stableShipId, stableShipIdFor(id, persistentFleetSlotId));
            this.persistentFleetSlotId = Math.max(-1, persistentFleetSlotId);
            this.name = trimmedOrFallback(name, "Ship");
            this.faction = faction;
            this.role = role;
            this.customContent = customContent;
            this.academyTrainingShip = academyTrainingShip;
            this.startHull = Math.max(0, startHull);
            this.startShield = Math.max(0.0, startShield);
            this.endHull = Math.max(0, endHull);
            this.endShield = Math.max(0.0, endShield);
            this.maxHull = Math.max(1, maxHull);
            this.maxShield = Math.max(0.0, maxShield);
            this.destroyed = destroyed;
            this.withdrew = withdrew;
            this.kills = Math.max(0, kills);
            this.repairEstimate = Math.max(0, repairEstimate);
        }

        public boolean isFriendlyTo(Faction playerFaction) {
            return faction != null && playerFaction != null && faction.isFriendlyTo(playerFaction);
        }

        public boolean isHostileTo(Faction playerFaction) {
            return faction != null && playerFaction != null && faction.isHostileTo(playerFaction);
        }

        public double startHealthValue() {
            return startHull + startShield;
        }

        public double endHealthValue() {
            return endHull + endShield;
        }

        public double damageTaken() {
            return Math.max(0.0, startHealthValue() - endHealthValue());
        }

        public double hullFraction() {
            return maxHull <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, endHull / (double) maxHull));
        }

        public double shieldFraction() {
            if (maxShield <= 1e-6) return 1.0;
            return Math.max(0.0, Math.min(1.0, endShield / maxShield));
        }

        public String roleLabel() {
            return role == null ? "SHIP" : role.name().replace('_', ' ');
        }

        public String factionLabel() {
            return faction == null ? "Unknown" : faction.teamName();
        }
    }

    public static final class AnalysisInsight {
        public enum Category {
            PRIMARY_FACTOR,
            SECONDARY_FACTOR,
            WARNING,
            RECOMMENDED_NEXT_ACTION,
            DETAIL
        }

        public final String ruleId;
        public final Category category;
        public final int priority;
        public final double confidence;
        public final String playerText;
        public final String debugExplanation;

        public AnalysisInsight(String ruleId,
                               Category category,
                               int priority,
                               double confidence,
                               String playerText,
                               String debugExplanation) {
            this.ruleId = trimmedOrFallback(ruleId, "analysis.unknown");
            this.category = category == null ? Category.DETAIL : category;
            this.priority = Math.max(0, priority);
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.playerText = trimmedOrFallback(playerText, "Battle factor recorded.");
            this.debugExplanation = trimmedOrFallback(debugExplanation, "No debug explanation recorded.");
        }
    }

    public final int schemaVersion;
    public final String battleId;
    public final String gameVersion;
    public final int saveSchemaVersion;
    public final long startedAtMillis;
    public final long endedAtMillis;
    public final double durationSeconds;
    public final BattleSource source;
    public final String modeLabel;
    public final String missionId;
    public final String missionTitle;
    public final int campaignSector;
    public final String campaignSubzone;
    public final String location;
    public final String difficultyPreset;
    public final String difficultySummary;
    public final Faction playerFaction;
    public final int playerTeamId;
    public final long seed;
    public final TacticalResult tacticalResult;
    public final MissionResult missionResult;
    public final String terminalText;
    public final int startCredits;
    public final int endCredits;
    public final int startOre;
    public final int endOre;
    public final int salvageEarned;
    public final int missionRewardEarned;
    public final int repairCostEstimate;
    public final int replacementCostEstimate;
    public final boolean campaignStateChanged;
    public final int salvagePickupsRemaining;
    public final int projectilesRemaining;
    public final List<ShipSnapshot> ships;

    public BattleResult(String battleId,
                        String gameVersion,
                        long startedAtMillis,
                        long endedAtMillis,
                        double durationSeconds,
                        BattleSource source,
                        String modeLabel,
                        String missionTitle,
                        String location,
                        Faction playerFaction,
                        int playerTeamId,
                        long seed,
                        TacticalResult tacticalResult,
                        MissionResult missionResult,
                        String terminalText,
                        int startCredits,
                        int endCredits,
                        int startOre,
                        int endOre,
                        int salvagePickupsRemaining,
                        int projectilesRemaining,
                        List<ShipSnapshot> ships) {
        this(BATTLE_RESULT_SCHEMA_VERSION,
                battleId,
                gameVersion,
                CampaignCheckpointStore.currentVersion(),
                startedAtMillis,
                endedAtMillis,
                durationSeconds,
                source,
                modeLabel,
                "",
                missionTitle,
                0,
                "",
                location,
                "",
                "",
                playerFaction,
                playerTeamId,
                seed,
                tacticalResult,
                missionResult,
                terminalText,
                startCredits,
                endCredits,
                startOre,
                endOre,
                Math.max(0, endOre - startOre),
                Math.max(0, endCredits - startCredits),
                0,
                0,
                false,
                salvagePickupsRemaining,
                projectilesRemaining,
                ships);
    }

    public BattleResult(int schemaVersion,
                        String battleId,
                        String gameVersion,
                        int saveSchemaVersion,
                        long startedAtMillis,
                        long endedAtMillis,
                        double durationSeconds,
                        BattleSource source,
                        String modeLabel,
                        String missionId,
                        String missionTitle,
                        int campaignSector,
                        String campaignSubzone,
                        String location,
                        String difficultyPreset,
                        String difficultySummary,
                        Faction playerFaction,
                        int playerTeamId,
                        long seed,
                        TacticalResult tacticalResult,
                        MissionResult missionResult,
                        String terminalText,
                        int startCredits,
                        int endCredits,
                        int startOre,
                        int endOre,
                        int salvageEarned,
                        int missionRewardEarned,
                        int repairCostEstimate,
                        int replacementCostEstimate,
                        boolean campaignStateChanged,
                        int salvagePickupsRemaining,
                        int projectilesRemaining,
                        List<ShipSnapshot> ships) {
        this.schemaVersion = Math.max(1, schemaVersion);
        this.battleId = trimmedOrFallback(battleId, "battle-" + Math.max(0L, endedAtMillis));
        this.gameVersion = trimmedOrFallback(gameVersion, "dev");
        this.saveSchemaVersion = Math.max(0, saveSchemaVersion);
        this.startedAtMillis = Math.max(0L, startedAtMillis);
        this.endedAtMillis = Math.max(this.startedAtMillis, endedAtMillis);
        this.durationSeconds = Math.max(0.0, durationSeconds);
        this.source = source == null ? BattleSource.UNKNOWN : source;
        this.modeLabel = trimmedOrFallback(modeLabel, this.source.name());
        this.missionId = trimmedOrFallback(missionId, "");
        this.missionTitle = trimmedOrFallback(missionTitle, "Tactical Engagement");
        this.campaignSector = Math.max(0, campaignSector);
        this.campaignSubzone = trimmedOrFallback(campaignSubzone, "");
        this.location = trimmedOrFallback(location, "Uncharted Sector");
        this.difficultyPreset = trimmedOrFallback(difficultyPreset, "Unspecified");
        this.difficultySummary = trimmedOrFallback(difficultySummary, "");
        this.playerFaction = playerFaction;
        this.playerTeamId = Math.max(0, playerTeamId);
        this.seed = seed;
        this.tacticalResult = tacticalResult == null ? TacticalResult.UNKNOWN : tacticalResult;
        this.missionResult = missionResult == null ? MissionResult.UNKNOWN : missionResult;
        this.terminalText = trimmedOrFallback(terminalText, "Battle ended");
        this.startCredits = Math.max(0, startCredits);
        this.endCredits = Math.max(0, endCredits);
        this.startOre = Math.max(0, startOre);
        this.endOre = Math.max(0, endOre);
        this.salvageEarned = Math.max(0, salvageEarned);
        this.missionRewardEarned = Math.max(0, missionRewardEarned);
        this.repairCostEstimate = Math.max(0, repairCostEstimate);
        this.replacementCostEstimate = Math.max(0, replacementCostEstimate);
        this.campaignStateChanged = campaignStateChanged;
        this.salvagePickupsRemaining = Math.max(0, salvagePickupsRemaining);
        this.projectilesRemaining = Math.max(0, projectilesRemaining);
        this.ships = List.copyOf(ships == null ? List.of() : ships);
    }

    public static BattleResult fromContext(GameContext ctx,
                                           List<ShipSnapshot> snapshots,
                                           String battleId,
                                           long startedAtMillis,
                                           double startedBattleElapsed,
                                           int startCredits,
                                           int startOre,
                                           String terminalText) {
        long endedAt = System.currentTimeMillis();
        GameMode mode = (ctx == null || ctx.config == null) ? null : ctx.config.mode;
        Faction playerFaction = (ctx == null || ctx.player == null) ? null : ctx.player.faction;
        int playerTeamId = (ctx == null || ctx.config == null) ? 0 : ctx.config.playerTeamId;
        long seed = (ctx == null || ctx.config == null) ? 0L : ctx.config.seed;
        double duration = Math.max(0.0, (ctx == null ? 0.0 : ctx.battleElapsed) - Math.max(0.0, startedBattleElapsed));
        if (duration <= 1e-6 && startedAtMillis > 0L) {
            duration = Math.max(0.0, (endedAt - startedAtMillis) / 1000.0);
        }
        String terminal = trimmedOrFallback(terminalText, ctx == null ? "" : ctx.gameOverText);
        TacticalResult tactical = tacticalResultFromText(terminal, ctx != null && ctx.gameOver);
        MissionResult mission = missionResultFor(tactical, terminal, mode);
        int currentOre = currentOre(ctx);
        return new BattleResult(
                BATTLE_RESULT_SCHEMA_VERSION,
                battleId,
                AppInfo.VERSION,
                CampaignCheckpointStore.currentVersion(),
                startedAtMillis,
                endedAt,
                duration,
                sourceFor(ctx),
                mode == null ? "Unknown" : mode.toString(),
                missionIdFor(ctx),
                missionTitleFor(ctx),
                campaignSectorFor(ctx),
                campaignSubzoneFor(ctx),
                locationFor(ctx),
                difficultyPresetFor(ctx),
                difficultySummaryFor(ctx),
                playerFaction,
                playerTeamId,
                seed,
                tactical,
                mission,
                terminal,
                Math.max(0, startCredits),
                ctx == null ? 0 : Math.max(0, ctx.credits),
                Math.max(0, startOre),
                currentOre,
                Math.max(0, currentOre - Math.max(0, startOre)),
                Math.max(0, (ctx == null ? 0 : ctx.credits) - Math.max(0, startCredits)),
                repairCostEstimate(snapshots, playerFaction),
                replacementCostEstimate(snapshots, playerFaction),
                campaignStateChanged(ctx, terminal, currentOre, startOre),
                ctx == null || ctx.salvage == null ? 0 : ctx.salvage.size(),
                ctx == null || ctx.projectiles == null ? 0 : ctx.projectiles.size(),
                snapshots);
    }

    public static BattleSource sourceFor(GameContext ctx) {
        if (ctx == null || ctx.config == null || ctx.config.mode == null) return BattleSource.UNKNOWN;
        if (ctx.multiplayerBattle) return BattleSource.MULTIPLAYER;
        return switch (ctx.config.mode) {
            case TUTORIAL -> BattleSource.ACADEMY;
            case CAMPAIGN_OPS, FLEET -> BattleSource.CAMPAIGN;
            case CUSTOM_BATTLES -> BattleSource.CUSTOM_BATTLE;
            case LAST_STAND -> BattleSource.LAST_STAND;
            case RESOURCE_RUSH -> BattleSource.RESOURCE_RUSH;
            case FOUR_TEAM_DOMINATION -> BattleSource.FOUR_TEAM_DOMINATION;
            case SHOOTING_RANGE -> BattleSource.SHOOTING_RANGE;
            case SHOWCASE -> BattleSource.SHOWCASE;
        };
    }

    public static TacticalResult tacticalResultFromText(String text, boolean gameOver) {
        String lower = trimmedOrFallback(text, "").toLowerCase(Locale.US);
        if (lower.contains("withdraw") || lower.contains("retreat")) return TacticalResult.WITHDRAWAL;
        if (lower.contains("draw")) return TacticalResult.DRAW;
        if (lower.contains("victory") || lower.contains("wins") || lower.contains("secure")) return TacticalResult.VICTORY;
        if (lower.contains("defeat") || lower.contains("lost") || lower.contains("failed")) return TacticalResult.DEFEAT;
        return gameOver ? TacticalResult.UNKNOWN : TacticalResult.ONGOING;
    }

    public int friendlyShipsStarted() {
        return countShips(true, false, false);
    }

    public int friendlyShipsSurvived() {
        return countShips(true, false, true);
    }

    public int friendlyShipsLost() {
        int lost = 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction) && ship.destroyed) lost++;
        }
        return lost;
    }

    public int hostileShipsStarted() {
        return countShips(false, true, false);
    }

    public int hostileShipsDestroyed() {
        int lost = 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isHostileTo(playerFaction) && ship.destroyed) lost++;
        }
        return lost;
    }

    public int customShipsInBattle() {
        int count = 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.customContent) count++;
        }
        return count;
    }

    public double friendlyDamageTaken() {
        return damageTaken(true);
    }

    public double hostileDamageTaken() {
        return damageTaken(false);
    }

    public int friendlyRepairEstimate() {
        int total = 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction)) total += ship.repairEstimate;
        }
        return total;
    }

    public int friendlyReplacementEstimate() {
        int total = 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction) && ship.destroyed) {
                total += Math.max(250, ship.maxHull * 8);
            }
        }
        return total;
    }

    public double averageFriendlyHullFraction() {
        double total = 0.0;
        int count = 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction) && !ship.destroyed) {
                total += ship.hullFraction();
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    public List<ShipSnapshot> mostDamagedFriendlyShips(int limit) {
        ArrayList<ShipSnapshot> out = new ArrayList<>();
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction) && ship.damageTaken() > 0.0) {
                out.add(ship);
            }
        }
        out.sort((a, b) -> Double.compare(b.damageTaken(), a.damageTaken()));
        return out.subList(0, Math.min(Math.max(0, limit), out.size()));
    }

    public List<String> enemyFactionLabels() {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isHostileTo(playerFaction)) labels.add(ship.factionLabel());
        }
        return List.copyOf(labels);
    }

    public String resultLabel() {
        return switch (tacticalResult) {
            case VICTORY -> "Victory";
            case DEFEAT -> "Defeat";
            case WITHDRAWAL -> "Withdrawal";
            case DRAW -> "Draw";
            case ONGOING -> "Ongoing";
            case ABORTED -> "Aborted";
            case UNKNOWN -> "Unknown";
        };
    }

    public String durationLabel() {
        long seconds = Math.round(durationSeconds);
        long minutes = seconds / 60L;
        long remaining = seconds % 60L;
        return minutes + "m " + remaining + "s";
    }

    public String startedAtIso() {
        return Instant.ofEpochMilli(startedAtMillis).toString();
    }

    private int countShips(boolean friendly, boolean hostile, boolean survivedOnly) {
        int count = 0;
        for (ShipSnapshot ship : ships) {
            if (ship == null) continue;
            boolean include = friendly && ship.isFriendlyTo(playerFaction);
            include |= hostile && ship.isHostileTo(playerFaction);
            if (!include) continue;
            if (survivedOnly && ship.destroyed) continue;
            count++;
        }
        return count;
    }

    private double damageTaken(boolean friendly) {
        double total = 0.0;
        for (ShipSnapshot ship : ships) {
            if (ship == null) continue;
            if (friendly && !ship.isFriendlyTo(playerFaction)) continue;
            if (!friendly && !ship.isHostileTo(playerFaction)) continue;
            total += ship.damageTaken();
        }
        return total;
    }

    private static MissionResult missionResultFor(TacticalResult tactical, String terminal, GameMode mode) {
        if (mode == GameMode.SHOWCASE || mode == GameMode.SHOOTING_RANGE) return MissionResult.NOT_APPLICABLE;
        String lower = trimmedOrFallback(terminal, "").toLowerCase(Locale.US);
        if (tactical == TacticalResult.VICTORY) return MissionResult.SUCCESS;
        if (tactical == TacticalResult.DEFEAT) return MissionResult.FAILURE;
        if (tactical == TacticalResult.WITHDRAWAL) {
            return lower.contains("objective secure") || lower.contains("site open")
                    ? MissionResult.PARTIAL_SUCCESS
                    : MissionResult.FAILURE;
        }
        if (tactical == TacticalResult.DRAW) return MissionResult.PARTIAL_SUCCESS;
        return MissionResult.UNKNOWN;
    }

    private static String missionTitleFor(GameContext ctx) {
        if (ctx == null || ctx.campaign == null) return "Tactical Engagement";
        String objective = ctx.campaign.objectiveLabel;
        if (objective != null && !objective.isBlank()) return objective.trim();
        String phase = ctx.campaign.objectivePhaseLabel;
        if (phase != null && !phase.isBlank()) return phase.trim();
        return "Campaign Engagement";
    }

    private static String missionIdFor(GameContext ctx) {
        if (ctx == null || ctx.campaign == null) return "";
        String active = ctx.campaign.activeGalaxyEncounterLocationId;
        if (active != null && !active.isBlank()) return active.trim();
        String selected = ctx.campaign.selectedGalaxyLocationId;
        if (selected != null && !selected.isBlank()) return selected.trim();
        return "sector-" + Math.max(1, ctx.campaign.sector);
    }

    private static int campaignSectorFor(GameContext ctx) {
        return ctx == null || ctx.campaign == null ? 0 : Math.max(1, ctx.campaign.sector);
    }

    private static String campaignSubzoneFor(GameContext ctx) {
        if (ctx == null || ctx.campaign == null) return "";
        int subzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
        if (subzone < 0 && ctx.player != null) subzone = ctx.player.campaignMissionSubzone;
        return subzone < 0 ? "" : CampaignSystem.missionSubzoneLabel(subzone);
    }

    private static String difficultyPresetFor(GameContext ctx) {
        if (ctx == null || ctx.experience == null || ctx.experience.preset == null) return "";
        return ctx.experience.preset.toString();
    }

    private static String difficultySummaryFor(GameContext ctx) {
        if (ctx == null || ctx.experience == null) return "";
        return "lethality x" + String.format(Locale.US, "%.2f", ctx.experience.combatLethality)
                + ", pressure x" + String.format(Locale.US, "%.2f", ctx.experience.strategicPressure)
                + ", attrition x" + String.format(Locale.US, "%.2f", ctx.experience.attrition);
    }

    private static String locationFor(GameContext ctx) {
        if (ctx == null || ctx.campaign == null) return "Open Space";
        String transition = ctx.campaign.transitionLabel;
        if (transition != null && !transition.isBlank()) return transition.trim();
        return "Sector " + Math.max(1, ctx.campaign.sector);
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

    static String stableShipIdFor(int id, int persistentFleetSlotId) {
        if (persistentFleetSlotId > 0) return "campaign-slot-" + persistentFleetSlotId;
        return "tactical-ship-" + Math.max(0, id);
    }

    static int repairEstimate(int startHull, int endHull) {
        return Math.max(0, startHull - endHull) * 2;
    }

    private static int repairCostEstimate(List<ShipSnapshot> ships, Faction playerFaction) {
        int total = 0;
        if (ships == null) return 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction)) total += ship.repairEstimate;
        }
        return total;
    }

    private static int replacementCostEstimate(List<ShipSnapshot> ships, Faction playerFaction) {
        int total = 0;
        if (ships == null) return 0;
        for (ShipSnapshot ship : ships) {
            if (ship != null && ship.isFriendlyTo(playerFaction) && ship.destroyed) {
                total += Math.max(250, ship.maxHull * 8);
            }
        }
        return total;
    }

    private static boolean campaignStateChanged(GameContext ctx, String terminal, int currentOre, int startOre) {
        if (ctx == null || ctx.campaign == null) return false;
        if (currentOre != Math.max(0, startOre)) return true;
        String text = trimmedOrFallback(terminal, "").toLowerCase(Locale.US);
        return text.contains("secured")
                || text.contains("saved")
                || text.contains("reputation")
                || text.contains("objective")
                || text.contains("sector")
                || text.contains("withdraw");
    }

    static String trimmedOrFallback(String value, String fallback) {
        String out = value == null ? "" : value.trim();
        return out.isBlank() ? (fallback == null ? "" : fallback) : out;
    }
}
