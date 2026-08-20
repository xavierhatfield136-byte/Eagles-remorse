import app.config.GameMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class AISystem {
    private AISystem(){}
    static final double UNIVERSAL_SUPERWEAPON_RANGE = Ship.UNIVERSAL_SPECIAL_WEAPON_RANGE;

    private static final class FleetState {
        final Map<Integer, Ship> flagships = new HashMap<>();
        final Map<Integer, List<Ship>> members = new HashMap<>();
        final Map<Integer, Faction> teamFactions = new HashMap<>();
        final Map<Integer, Integer> shipGroupKeys = new HashMap<>();
        final Map<Integer, Ship> sharedTargets = new HashMap<>();
        final Map<Integer, List<Ship>> focusTargets = new HashMap<>();
        final Map<Integer, Double> sharedTargetConfidence = new HashMap<>();
        final Map<Integer, Ship> missileThreatFocus = new HashMap<>();
        final Map<Integer, DangerMemory> dangerMemory = new HashMap<>();
        final Map<Integer, SquadObjective> squadObjectives = new HashMap<>();
        final Map<Integer, GameContext.FleetFormation> autoFormation = new HashMap<>();
        final Map<Integer, Double> autoFormationSpacing = new HashMap<>();
        final Map<Integer, String> squadLabels = new HashMap<>();
        final Map<Integer, String> squadRoles = new HashMap<>();
        final Map<Integer, Integer> squadLeaders = new HashMap<>();
        final Map<Integer, Integer> squadIndexes = new HashMap<>();
    }

    private static final class SharedTargetChoice {
        final Ship target;
        final double confidence;

        SharedTargetChoice(Ship target, double confidence) {
            this.target = target;
            this.confidence = confidence;
        }
    }

    private enum IntentType {
        FIGHT,
        WANDER,
        FLEET_ACTION,
        SCREEN,
        REARM,
        HOLD
    }

    private static final class ShipPerception {
        final Ship immediateThreat;
        final Ship selectedTarget;
        final boolean abstractedSectorBehavior;

        ShipPerception(Ship immediateThreat, Ship selectedTarget, boolean abstractedSectorBehavior) {
            this.immediateThreat = immediateThreat;
            this.selectedTarget = selectedTarget;
            this.abstractedSectorBehavior = abstractedSectorBehavior;
        }
    }

    private static final class ShipIntent {
        final IntentType type;
        final Ship target;
        final Ship anchor;

        ShipIntent(IntentType type, Ship target) {
            this(type, target, null);
        }

        ShipIntent(IntentType type, Ship target, Ship anchor) {
            this.type = (type == null) ? IntentType.WANDER : type;
            this.target = target;
            this.anchor = anchor;
        }
    }

    private static final class EscortPerception {
        final Ship anchor;
        final Ship threat;

        EscortPerception(Ship anchor, Ship threat) {
            this.anchor = anchor;
            this.threat = threat;
        }
    }

    private static final class RearmPerception {
        final Ship tender;

        RearmPerception(Ship tender) {
            this.tender = tender;
        }
    }

    private static final class PdEscortPerception {
        final Ship anchor;
        final Ship threat;

        PdEscortPerception(Ship anchor, Ship threat) {
            this.anchor = anchor;
            this.threat = threat;
        }
    }

    private static final class CarrierCraftPerception {
        final boolean needsRearm;
        final Ship target;

        CarrierCraftPerception(boolean needsRearm, Ship target) {
            this.needsRearm = needsRearm;
            this.target = target;
        }
    }

    private static final class DangerMemory {
        double x;
        double y;
        double intensity;
        double ttl;
    }

    private enum SquadObjective {
        INTERCEPT,
        FLANK,
        HOLD,
        RESERVE
    }

    private static final Map<Integer, DangerMemory> TEAM_DANGER_MEMORY = new HashMap<>();
    private static final Map<Integer, Double> TARGET_KILL_CONFIRM_TIMERS = new HashMap<>();
    private static final Map<Integer, Double> TEAM_COMMAND_DELAY_TIMERS = new HashMap<>();
    private static final Map<Integer, Integer> TEAM_DELAYED_TARGET_IDS = new HashMap<>();
    private static final Map<Integer, Double> CLOSEST_RETARGET_TIMERS = new HashMap<>();
    private static final Map<Integer, Integer> CLOSEST_RETARGET_TARGET_IDS = new HashMap<>();
    private static final Map<Integer, Double> IMMEDIATE_THREAT_SCAN_TIMERS = new HashMap<>();
    private static final Map<Integer, Double> ENGAGEMENT_SCAN_BACKOFF_TIMERS = new HashMap<>();
    private static final Map<Integer, Integer> TEAM_STABLE_SHARED_TARGET_IDS = new HashMap<>();
    private static final Map<Integer, Double> TEAM_STABLE_SHARED_TARGET_CONFIDENCE = new HashMap<>();
    private static final Map<Integer, Double> TEAM_STABLE_SHARED_TARGET_TTL = new HashMap<>();
    private static final double REPAIR_ORDER_SAFE_SECONDS = 20.0;
    private static final double BATTLEFIELD_WARP_TRIGGER_RANGE = 1700.0;
    private static final double BATTLEFIELD_WARP_SAFE_RADIUS = 640.0;
    private static final double HOSTILE_STARBASE_WARP_EXCLUSION_RADIUS = 1100.0;
    private static final double MODE_OPENING_WARP_DISCIPLINE_SECONDS = 30.0;
    private static final double MODE_HOSTILE_BASE_WARP_BUFFER = 520.0;
    private static final double ESCORT_WARP_SUPPORT_RANGE = 320.0;
    private static final double FLEET_REJOIN_WARP_RANGE = 1500.0;
    private static final double FLEET_REJOIN_ANCHOR_RANGE = 900.0;
    static final double STANDARD_PROSECUTION_RANGE = 3000.0;
    private static final double PLAYER_FLEET_PROSECUTION_RANGE = STANDARD_PROSECUTION_RANGE;
    private static final double RED_FLEET_PROSECUTION_RANGE = STANDARD_PROSECUTION_RANGE;
    private static final int MAX_FLEET_COMM_LOG = 8;
    private static final int RESOURCE_RUSH_TEAM_SHIP_CAP = 18;
    private static final int RESOURCE_RUSH_ESTIMATED_SHIPS_PER_GROUP = 4;
    private static final ThreadLocal<ScratchLists> SCRATCH = ThreadLocal.withInitial(ScratchLists::new);

    private static final class ScratchLists {
        final ArrayDeque<ArrayList<Ship>> shipLists = new ArrayDeque<>();
        final ArrayDeque<ArrayList<Missile>> missileLists = new ArrayDeque<>();
        final ArrayDeque<ArrayList<Integer>> intLists = new ArrayDeque<>();
        final ArrayDeque<java.util.HashSet<Integer>> intSets = new ArrayDeque<>();
    }

    private static final class AIFrameCache {
        final Map<Integer, Ship> preferredEnemyTargets = new HashMap<>();
        final Map<Long, Ship> immediateThreats = new HashMap<>();
        List<FogOfWarSystem.SensorInterestSignal> sensorInterestSignals = null;
        final Map<Long, FleetStateBuilder.StrengthSummary> strengthSummaries = new HashMap<>();
        long queryComputeNs = 0L;
        int preferredTargetHits = 0;
        int preferredTargetMisses = 0;
        int immediateThreatHits = 0;
        int immediateThreatMisses = 0;
        int sensorSignalHits = 0;
        int sensorSignalMisses = 0;
        int intentCacheHits = 0;
        int intentCacheMisses = 0;
        int intentInvalidations = 0;
        int cheapTargetScores = 0;
        int mediumTargetScores = 0;
        int expensiveTargetScores = 0;
        int movementReuseFrames = 0;
    }

    private static long shipCombatTargetNs = 0L;
    private static long shipCombatFightNs = 0L;
    private static long shipCombatFireNs = 0L;
    private static long aiFrameIndex = 0L;
    private static final ThreadLocal<AIFrameCache> ACTIVE_FRAME_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<AiScalePolicy.FramePlan> ACTIVE_SCALE_PLAN = new ThreadLocal<>();

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;
        if (ctx.config != null
                && (ctx.config.mode == GameMode.SHOWCASE
                || ctx.config.mode == GameMode.SHOOTING_RANGE
                || ctx.config.mode == GameMode.TUTORIAL)) return;
        if (!DevTools.isAIEnabled()) return;
        long maintenanceNs = 0L;
        long fleetStateNs = 0L;
        long shipUtilityNs = 0L;
        long shipCombatNs = 0L;
        long avoidanceNs = 0L;
        long formationSyncNs = 0L;
        long boundsNs = 0L;
        shipCombatTargetNs = 0L;
        shipCombatFightNs = 0L;
        shipCombatFireNs = 0L;
        ACTIVE_FRAME_CACHE.set(new AIFrameCache());
        AiScalePolicy.FramePlan scalePlan = AiScalePolicy.planFor(ctx, aiFrameIndex++);
        ACTIVE_SCALE_PLAN.set(scalePlan);

        try {
            long phaseStart = System.nanoTime();
            tickFleetCommLog(ctx, Math.max(0.0, dt));
            decayKillConfirmTimers(Math.max(0.0, dt));
            pruneClosestRetargetState(ctx.ships);

            // Generic wave spawner (disabled for Last Stand and 4-team, which have custom pacing).
            if (!ctx.multiplayerBattle
                    && ctx.config.mode != GameMode.LAST_STAND
                    && ctx.config.mode != GameMode.FOUR_TEAM_DOMINATION
                    && !CampaignSystem.useAuthoredWaveSchedule(ctx)) {
                ctx.enemyWaveTimer -= dt;
                if (ctx.enemyWaveTimer <= 0) {
                    ctx.enemyWaveTimer = CampaignSystem.nextWaveDelay(ctx);
                    int groups = CampaignSystem.groupsPerWave(ctx);
                    int enemyGroups = groups;
                    int allyGroups = Math.max(1, groups - 1);
                    enemyGroups = Math.max(1, enemyGroups + OffSectorSimulationSystem.reinforcementBudgetDelta(ctx, Faction.ENEMY));
                    allyGroups = Math.max(0, allyGroups + OffSectorSimulationSystem.reinforcementBudgetDelta(ctx, Faction.ALLY));

                    if (ctx.config.mode == GameMode.RESOURCE_RUSH) {
                        // Resource Rush should not snowball into red-only pressure.
                        allyGroups = groups;
                        int enemyAlive = TeamSystem.countAliveShips(ctx, Faction.ENEMY);
                        int allyAlive = TeamSystem.countAliveShips(ctx, Faction.ALLY);
                        int deficit = enemyAlive - allyAlive;
                        if (deficit >= 3) {
                            allyGroups += Math.min(2, deficit / 3);
                        }
                        allyGroups = Math.max(0, allyGroups + OffSectorSimulationSystem.reinforcementBudgetDelta(ctx, Faction.ALLY));
                        enemyGroups = Math.max(1, enemyGroups);
                        enemyGroups = resourceRushCappedGroupCount(enemyAlive, enemyGroups);
                        allyGroups = resourceRushCappedGroupCount(allyAlive, allyGroups);
                    }
                    if (CampaignSystem.isCampaignActive(ctx)) {
                        allyGroups = 0;
                    }
                    for (int i = 0; i < enemyGroups; i++) {
                        double[] spawn = teamWaveStagingPoint(ctx, Faction.ENEMY);
                        SpawnSystem.spawnEnemyGroup(
                                ctx,
                                spawn[0] + (ctx.rng.nextDouble() - 0.5) * 220.0,
                                spawn[1] + (ctx.rng.nextDouble() - 0.5) * 220.0
                        );
                    }

                    for (int i = 0; i < allyGroups; i++) {
                        double[] spawn = teamWaveStagingPoint(ctx, Faction.ALLY);
                        SpawnSystem.spawnAllyGroup(
                                ctx,
                                spawn[0] + (ctx.rng.nextDouble() - 0.5) * 220.0,
                                spawn[1] + (ctx.rng.nextDouble() - 0.5) * 220.0
                        );
                    }
                }
            }
            maintenanceNs += System.nanoTime() - phaseStart;

            phaseStart = System.nanoTime();
            FleetState fleetState = buildFleetState(ctx, dt);
            fleetStateNs += System.nanoTime() - phaseStart;

            phaseStart = System.nanoTime();
            updateShipWillpower(ctx, dt);
            shipUtilityNs += System.nanoTime() - phaseStart;

            // per ship AI
            for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying) continue;
            if (s == ctx.player) continue;
            if (ctx.multiplayerBattle && ctx.multiplayerPlayerControlledShipIds.contains(s.id)) continue;
            if (s.isWarpCharging()) {
                long utilityStart = System.nanoTime();
                steerWarpChargingShip(s, dt);
                s.tryCIWS(dt, ctx);
                shipUtilityNs += System.nanoTime() - utilityStart;
                continue;
            }
            long utilityStart = System.nanoTime();
            updateStealthCloakIntent(ctx, s);
            tickClosestWeaponRetarget(ctx, s, dt);
            adaptMissileRolesToThreats(ctx, s);
            maybeFireAutonomousInterceptMissiles(ctx, s, dt);
            if (s.aiBadApproachTimer > 0.0) {
                s.aiBadApproachTimer = Math.max(0.0, s.aiBadApproachTimer - Math.max(0.0, dt));
                if (s.aiBadApproachTimer <= 0.0) s.aiBadApproachAngle = Double.NaN;
            }
            if (s.aiNoFireTimer > 0.0) {
                s.aiNoFireTimer = Math.max(0.0, s.aiNoFireTimer - Math.max(0.0, dt) * 0.35);
            }
            if (s.aiForcedEngageTimer > 0.0) {
                s.aiForcedEngageTimer = Math.max(0.0, s.aiForcedEngageTimer - Math.max(0.0, dt));
            }
            if (s.aiArrivalFireDelayTimer > 0.0) {
                s.aiArrivalFireDelayTimer = Math.max(0.0, s.aiArrivalFireDelayTimer - Math.max(0.0, dt));
            }
            if (s.aiMissileStandoffTimer > 0.0) {
                s.aiMissileStandoffTimer = Math.max(0.0, s.aiMissileStandoffTimer - Math.max(0.0, dt));
                if (s.aiMissileStandoffTimer <= 0.0) s.aiMissileStandoffTargetId = -1;
            }
            if (s.aiTargetCommitTimer > 0.0) {
                s.aiTargetCommitTimer = Math.max(0.0, s.aiTargetCommitTimer - Math.max(0.0, dt));
                if (s.aiTargetCommitTimer <= 0.0) {
                    s.aiCommittedTargetId = -1;
                    s.aiMissileStandoffTargetId = -1;
                }
            }
            tickCachedAiIntent(ctx, s, dt);
            if (s.surrendered) {
                s.vx *= 0.88;
                s.vy *= 0.88;
                s.desiredSpeed = 0.0;
                s.tryCIWS(dt, ctx);
                shipUtilityNs += System.nanoTime() - utilityStart;
                continue;
            }
            shipUtilityNs += System.nanoTime() - utilityStart;
            long combatStart = System.nanoTime();
            if (tryRunSpecializedShipAI(ctx, s, dt)) {
                shipCombatNs += System.nanoTime() - combatStart;
                long avoidStart = System.nanoTime();
                applyAvoidanceForScale(ctx, scalePlan, s, dt);
                avoidanceNs += System.nanoTime() - avoidStart;
                continue;
            }

            combatStart = System.nanoTime();
            runCombatShipAI(ctx, fleetState, s, dt);
            shipCombatNs += System.nanoTime() - combatStart;
            long avoidStart = System.nanoTime();
            applyAvoidanceForScale(ctx, scalePlan, s, dt);
            avoidanceNs += System.nanoTime() - avoidStart;
            }

            phaseStart = System.nanoTime();
            synchronizeFlagshipWarpFormations(ctx, fleetState);
            formationSyncNs += System.nanoTime() - phaseStart;

            // keep bounds
            phaseStart = System.nanoTime();
            for (Ship s : ctx.ships) {
                if (s == null) continue;
                if (!s.alive || s.dying) continue;
                s.x = GameMath.clamp(s.x, 0, ctx.WORLD_W);
                s.y = GameMath.clamp(s.y, 0, ctx.WORLD_H);
            }
            boundsNs += System.nanoTime() - phaseStart;

            if (ctx.perf != null) {
                ctx.perf.aiMs = (maintenanceNs + fleetStateNs + shipUtilityNs + shipCombatNs
                        + avoidanceNs + formationSyncNs + boundsNs) / 1_000_000.0;
                ctx.perf.aiMaintenanceMs = maintenanceNs / 1_000_000.0;
                ctx.perf.aiFleetStateMs = fleetStateNs / 1_000_000.0;
                ctx.perf.aiShipUtilityMs = shipUtilityNs / 1_000_000.0;
                ctx.perf.aiShipCombatMs = shipCombatNs / 1_000_000.0;
                ctx.perf.aiShipCombatTargetMs = shipCombatTargetNs / 1_000_000.0;
                ctx.perf.aiShipCombatFightMs = shipCombatFightNs / 1_000_000.0;
                ctx.perf.aiShipCombatFireMs = shipCombatFireNs / 1_000_000.0;
                ctx.perf.aiAvoidanceMs = avoidanceNs / 1_000_000.0;
                ctx.perf.aiFormationSyncMs = formationSyncNs / 1_000_000.0;
                ctx.perf.aiBoundsMs = boundsNs / 1_000_000.0;
                AIFrameCache cache = currentFrameCache();
                ctx.perf.aiCacheQueryMs = (cache == null) ? 0.0 : cache.queryComputeNs / 1_000_000.0;
                ctx.perf.aiPreferredTargetHits = (cache == null) ? 0 : cache.preferredTargetHits;
                ctx.perf.aiPreferredTargetMisses = (cache == null) ? 0 : cache.preferredTargetMisses;
                ctx.perf.aiImmediateThreatHits = (cache == null) ? 0 : cache.immediateThreatHits;
                ctx.perf.aiImmediateThreatMisses = (cache == null) ? 0 : cache.immediateThreatMisses;
                ctx.perf.aiSensorSignalHits = (cache == null) ? 0 : cache.sensorSignalHits;
                ctx.perf.aiSensorSignalMisses = (cache == null) ? 0 : cache.sensorSignalMisses;
                ctx.perf.aiIntentCacheHits = (cache == null) ? 0 : cache.intentCacheHits;
                ctx.perf.aiIntentCacheMisses = (cache == null) ? 0 : cache.intentCacheMisses;
                ctx.perf.aiIntentInvalidations = (cache == null) ? 0 : cache.intentInvalidations;
                ctx.perf.aiCheapTargetScores = (cache == null) ? 0 : cache.cheapTargetScores;
                ctx.perf.aiMediumTargetScores = (cache == null) ? 0 : cache.mediumTargetScores;
                ctx.perf.aiExpensiveTargetScores = (cache == null) ? 0 : cache.expensiveTargetScores;
                ctx.perf.aiMovementReuseFrames = (cache == null) ? 0 : cache.movementReuseFrames;
            }
        } finally {
            ACTIVE_FRAME_CACHE.remove();
            ACTIVE_SCALE_PLAN.remove();
        }
    }

    private static void applyAvoidanceForScale(GameContext ctx, AiScalePolicy.FramePlan scalePlan, Ship ship, double dt) {
        if (scalePlan == null || scalePlan.shouldRunAvoidance(ship)) {
            applyAsteroidAvoidance(ctx, ship, dt);
            applyProjectileLaneAvoidance(ctx, ship, dt);
        }
    }

    private static void tickCachedAiIntent(GameContext ctx, Ship ship, double dt) {
        if (ship == null) return;
        double step = Math.max(0.0, dt);
        if (ship.aiIntentRetargetTimer > 0.0) {
            ship.aiIntentRetargetTimer = Math.max(0.0, ship.aiIntentRetargetTimer - step);
        }
        if (ship.aiMovementThinkTimer > 0.0) {
            ship.aiMovementThinkTimer = Math.max(0.0, ship.aiMovementThinkTimer - step);
        }
        if (ship.aiIntentTargetId > 0 && ctx != null && ctx.entityQuery != null) {
            Ship target = ctx.entityQuery.findShipById(ship.aiIntentTargetId);
            if (!isAlive(target)) {
                clearCachedAiIntent(ship, true);
            }
        }
    }

    private static void runCombatShipAI(GameContext ctx, FleetState fleetState, Ship ship, double dt) {
        if (ctx == null || ship == null) return;
        ShipPerception perception = perceiveCombatSituation(ctx, fleetState, ship, dt);
        ShipIntent intent = chooseCombatIntent(ctx, fleetState, ship, perception);
        executeCombatIntent(ctx, fleetState, ship, dt, intent);
    }

    private static boolean tryRunSpecializedShipAI(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null || dt <= 0.0) return false;
        if (runStaticDefenseShipAI(ctx, ship, dt)) return true;
        if (runMinerDefenseShipAI(ctx, ship, dt)) return true;
        if (runPdEscortShipAI(ctx, ship, dt)) return true;
        if (handleEscortFighterBehavior(ctx, ship, dt)) return true;
        if (runCarrierStrikeCraftAI(ctx, ship, dt)) return true;
        return handleStandaloneStrikeCraftRearm(ctx, ship, dt);
    }

    private static boolean runStaticDefenseShipAI(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null) return false;
        if (ship.role != ShipRole.BASE && ship.role != ShipRole.STATIC_TURRET) return false;

        ship.vx = 0;
        ship.vy = 0;
        Ship target = periodicClosestRetargetTarget(ctx, ship);
        if (!isAlive(target)) target = preferredEnemyTargetCached(ctx, ship);
        if (isAlive(target)) {
            fireIfAble(ctx, ship, target, dt, Math.hypot(target.x - ship.x, target.y - ship.y));
        }
        ship.tryCIWS(dt, ctx);
        return true;
    }

    private static boolean runMinerDefenseShipAI(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null) return false;
        if (ship.role != ShipRole.MINER) return false;

        Ship target = periodicClosestRetargetTarget(ctx, ship);
        if (!isAlive(target)) target = preferredEnemyTargetCached(ctx, ship);
        if (isAlive(target)) {
            double d = Math.hypot(target.x - ship.x, target.y - ship.y);
            if (d <= 280.0) {
                fireIfAble(ctx, ship, target, dt, d);
            }
        }
        ship.tryCIWS(dt, ctx);
        return true;
    }

    private static boolean runPdEscortShipAI(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null) return false;
        if (ship.role != ShipRole.PD_CRAFT || ship.carrierOwnerId >= 0 || ship.minerHomeBase == null) return false;

        PdEscortPerception perception = perceivePdEscortSituation(ctx, ship);
        if (perception == null || !isAlive(perception.anchor)) {
            return false;
        }
        ShipIntent intent = choosePdEscortIntent(perception);
        executePdEscortIntent(ctx, ship, dt, intent);
        ship.tryCIWS(dt, ctx);
        return true;
    }

    private static PdEscortPerception perceivePdEscortSituation(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null) return null;
        Ship escortCarrier = ship.minerHomeBase;
        boolean validCarrierAnchor = isAlive(escortCarrier)
                && escortCarrier.isCarrier
                && escortCarrier.faction != null
                && ship.faction != null
                && escortCarrier.faction.teamId() == ship.faction.teamId();
        if (!validCarrierAnchor) {
            ship.minerHomeBase = null;
            return null;
        }
        Ship threat = findEscortThreatNearCarrier(ctx, ship, escortCarrier);
        return new PdEscortPerception(escortCarrier, threat);
    }

    private static ShipIntent choosePdEscortIntent(PdEscortPerception perception) {
        if (perception == null || !isAlive(perception.anchor)) {
            return new ShipIntent(IntentType.WANDER, null);
        }
        if (isAlive(perception.threat)) {
            return new ShipIntent(IntentType.FIGHT, perception.threat, perception.anchor);
        }
        return new ShipIntent(IntentType.SCREEN, null, perception.anchor);
    }

    private static void executePdEscortIntent(GameContext ctx, Ship ship, double dt, ShipIntent intent) {
        if (ctx == null || ship == null || intent == null) return;
        if (intent.type == IntentType.FIGHT && isAlive(intent.target)) {
            fight(ctx, ship, intent.target, dt);
            return;
        }
        if (intent.type != IntentType.SCREEN || !isAlive(intent.anchor)) {
            wander(ctx, ship, dt);
            return;
        }
        Ship escortCarrier = intent.anchor;
        double orbitRange = Math.max(360.0, escortCarrier.radius + 220.0);
        double speed = Math.max(95.0, MovementModel.speedCeiling(ship) * 0.92);
        orbit(ship, escortCarrier.x, escortCarrier.y, orbitRange, speed, dt, ((ship.id & 1) == 0) ? 1.0 : -1.0);
    }

    private static boolean runCarrierStrikeCraftAI(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null) return false;
        if (ship.carrierOwnerId < 0) return false;

        CarrierCraftPerception perception = perceiveCarrierCraftSituation(ctx, ship);
        ShipIntent intent = chooseCarrierCraftIntent(perception);
        executeCarrierCraftIntent(ctx, ship, dt, intent);
        ship.tryCIWS(dt, ctx);
        return true;
    }

    private static CarrierCraftPerception perceiveCarrierCraftSituation(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null) return new CarrierCraftPerception(false, null);
        boolean needsRearm = ship.needsStrikeCraftRearm();
        Ship target = null;
        if (!needsRearm) {
            target = aggressiveFighterInterceptTarget(ctx, ship);
            if (!isAlive(target)) target = periodicClosestRetargetTarget(ctx, ship);
            if (!isAlive(target)) target = CarrierSystem.preferredTargetForCraft(ctx, ship, null);
            if (!isAlive(target)) target = preferredEnemyTargetCached(ctx, ship);
            if (!isAlive(target)) target = immediateThreatCached(ctx, ship, Math.max(180.0, preferredRange(ship) * 0.78));
        }
        return new CarrierCraftPerception(needsRearm, target);
    }

    private static ShipIntent chooseCarrierCraftIntent(CarrierCraftPerception perception) {
        if (perception == null) return new ShipIntent(IntentType.HOLD, null);
        if (perception.needsRearm) return new ShipIntent(IntentType.REARM, null);
        if (isAlive(perception.target)) return new ShipIntent(IntentType.FIGHT, perception.target);
        return new ShipIntent(IntentType.HOLD, null);
    }

    private static void executeCarrierCraftIntent(GameContext ctx, Ship ship, double dt, ShipIntent intent) {
        if (ctx == null || ship == null || intent == null) return;
        if (intent.type == IntentType.FIGHT && isAlive(intent.target)) {
            fight(ctx, ship, intent.target, dt);
        }
    }

    private static Ship aggressiveFighterInterceptTarget(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null || ship.role != ShipRole.FIGHTER || ship.faction == null) return null;
        Ship smallCraft = TargetingSystem.findClosestHostileSmallCraft(ctx, ship, ship.x, ship.y,
                Math.max(1100.0, preferredRange(ship) * 2.8));
        if (isAlive(smallCraft)) return smallCraft;
        return null;
    }

    private static ShipPerception perceiveCombatSituation(GameContext ctx, FleetState fleetState, Ship ship, double dt) {
        if (ctx == null || ship == null) {
            return new ShipPerception(null, null, false);
        }
        boolean abstracted = OffSectorSimulationSystem.shouldUseAbstractShipBehavior(ctx, ship);
        Ship immediate = null;
        Ship selected = null;
        if (abstracted) {
            immediate = immediateThreatCached(ctx, ship, Math.max(180.0, preferredRange(ship) * 0.56));
            selected = immediate;
        } else {
            long targetStart = System.nanoTime();
            selected = selectEngagementTarget(ctx, fleetState, ship, dt);
            shipCombatTargetNs += System.nanoTime() - targetStart;
            immediate = committedOrImmediateThreat(ctx, ship, selected);
        }
        return new ShipPerception(immediate, selected, abstracted);
    }

    private static ShipIntent chooseCombatIntent(GameContext ctx, FleetState fleetState, Ship ship, ShipPerception perception) {
        if (ctx == null || ship == null || perception == null) {
            return new ShipIntent(IntentType.WANDER, null);
        }
        if (perception.abstractedSectorBehavior) {
            return isAlive(perception.immediateThreat)
                    ? new ShipIntent(IntentType.FIGHT, perception.immediateThreat)
                    : new ShipIntent(IntentType.WANDER, null);
        }
        if (shouldYieldToFleetExecution(ctx, fleetState, ship)) {
            return new ShipIntent(IntentType.FLEET_ACTION, perception.selectedTarget);
        }
        return isAlive(perception.selectedTarget)
                ? new ShipIntent(IntentType.FIGHT, perception.selectedTarget)
                : new ShipIntent(IntentType.WANDER, null);
    }

    private static void executeCombatIntent(GameContext ctx, FleetState fleetState, Ship ship, double dt, ShipIntent intent) {
        if (ctx == null || ship == null || intent == null) return;
        switch (intent.type) {
            case FIGHT -> {
                if (isAlive(intent.target)) {
                    fight(ctx, ship, intent.target, dt);
                } else {
                    wander(ctx, ship, dt);
                }
            }
            case FLEET_ACTION -> {
                if (!applyFleetBehavior(ctx, fleetState, ship, dt, intent.target)) {
                    if (isAlive(intent.target)) {
                        fight(ctx, ship, intent.target, dt);
                    } else {
                        wander(ctx, ship, dt);
                    }
                }
            }
            case WANDER -> wander(ctx, ship, dt);
        }
    }

    private static boolean shouldYieldToFleetExecution(GameContext ctx, FleetState fleetState, Ship ship) {
        if (ctx == null || fleetState == null || ship == null || ship.faction == null) return false;
        int teamId = fleetGroupKeyForShip(fleetState, ship);
        Ship flagship = fleetState.flagships.get(teamId);
        if (flagship == null) return false;
        List<Ship> members = fleetState.members.get(teamId);
        return members != null && members.size() > 1;
    }

    private static AIFrameCache currentFrameCache() {
        return ACTIVE_FRAME_CACHE.get();
    }

    private static AiScalePolicy.FramePlan currentScalePlan() {
        return ACTIVE_SCALE_PLAN.get();
    }

    private static Ship cachedIntentTarget(GameContext ctx, FleetState state, Ship seeker, Ship sharedHint) {
        AIFrameCache cache = currentFrameCache();
        if (ctx == null || seeker == null || seeker.aiIntentRetargetTimer <= 0.0 || seeker.aiIntentTargetId <= 0) {
            if (cache != null) cache.intentCacheMisses++;
            return null;
        }
        Ship target = (ctx.entityQuery == null)
                ? findLiveShipById(ctx.ships, seeker.aiIntentTargetId)
                : ctx.entityQuery.findShipById(seeker.aiIntentTargetId);
        if (!isCachedIntentTargetValid(ctx, state, seeker, target, sharedHint)) {
            clearCachedAiIntent(seeker, true);
            if (cache != null) {
                cache.intentCacheMisses++;
            }
            return null;
        }
        if (cache != null) cache.intentCacheHits++;
        return target;
    }

    private static boolean isCachedIntentTargetValid(GameContext ctx, FleetState state, Ship seeker, Ship target, Ship sharedHint) {
        if (!CombatFireControl.canKeepCachedTarget(seeker, target)) return false;
        if (!TargetingSystem.isDetectableToObserver(seeker, target)) return false;
        if (!canShipThreatenTarget(ctx, seeker, target)) return false;
        double d = Math.hypot(target.x - seeker.x, target.y - seeker.y);
        double keepRange = Math.max(520.0, preferredRange(seeker) * 2.05 + target.radius);
        if (d > keepRange && target != sharedHint) return false;
        if (forcedToHoldFight(seeker, target)) return true;
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null && scalePlan.isLargeBattle()) {
            if (target == sharedHint && shouldCommitToSharedTarget(state, seeker, target)) return true;
            return quickCanTakeFightEstimate(seeker, target, d);
        }
        return canTakeFightMetric(ctx, seeker, target);
    }

    private static Ship rememberCachedAiIntent(Ship seeker, IntentType type, Ship target) {
        if (seeker == null || !isAlive(target)) return target;
        seeker.aiIntentTypeOrdinal = (type == null) ? -1 : type.ordinal();
        seeker.aiIntentTargetId = target.id;
        seeker.aiIntentRetargetTimer = Math.max(seeker.aiIntentRetargetTimer,
                CombatTargeting.intentReuseSeconds(currentScalePlan(), seeker));
        return target;
    }

    private static void clearCachedAiIntent(Ship seeker, boolean countInvalidation) {
        if (seeker == null) return;
        seeker.aiIntentTypeOrdinal = -1;
        seeker.aiIntentTargetId = -1;
        seeker.aiIntentRetargetTimer = 0.0;
        seeker.aiCachedDesiredRange = Double.NaN;
        seeker.aiCachedMovementMode = 0;
        if (countInvalidation) {
            AIFrameCache cache = currentFrameCache();
            if (cache != null) cache.intentInvalidations++;
        }
    }

    private static Ship preferredEnemyTargetCached(GameContext ctx, Ship seeker) {
        if (ctx == null || seeker == null) return null;
        AIFrameCache cache = currentFrameCache();
        if (cache == null) return TargetingSystem.getPreferredEnemyTarget(ctx, seeker);
        if (cache.preferredEnemyTargets.containsKey(seeker.id)) {
            cache.preferredTargetHits++;
            return cache.preferredEnemyTargets.get(seeker.id);
        }
        long start = System.nanoTime();
        Ship target = TargetingSystem.getPreferredEnemyTarget(ctx, seeker);
        cache.queryComputeNs += System.nanoTime() - start;
        cache.preferredTargetMisses++;
        cache.preferredEnemyTargets.put(seeker.id, target);
        return target;
    }

    private static Ship immediateThreatCached(GameContext ctx, Ship seeker, double radius) {
        if (ctx == null || seeker == null) return null;
        AIFrameCache cache = currentFrameCache();
        if (cache == null) return findImmediateThreatUncached(ctx, seeker, radius);
        long key = (((long) seeker.id) << 32) ^ (quantizeThreatRadius(radius) & 0xffffffffL);
        if (cache.immediateThreats.containsKey(key)) {
            cache.immediateThreatHits++;
            return cache.immediateThreats.get(key);
        }
        long start = System.nanoTime();
        Ship target = findImmediateThreatUncached(ctx, seeker, radius);
        cache.queryComputeNs += System.nanoTime() - start;
        cache.immediateThreatMisses++;
        cache.immediateThreats.put(key, target);
        return target;
    }

    private static int quantizeThreatRadius(double radius) {
        return (int) Math.round(Math.max(0.0, radius) * 0.1);
    }

    private static List<FogOfWarSystem.SensorInterestSignal> sensorInterestSignalsCached(GameContext ctx) {
        if (ctx == null) return List.of();
        AIFrameCache cache = currentFrameCache();
        if (cache == null) return FogOfWarSystem.sensorInterestSignals(ctx);
        if (cache.sensorInterestSignals == null) {
            long start = System.nanoTime();
            cache.sensorInterestSignals = FogOfWarSystem.sensorInterestSignals(ctx);
            cache.queryComputeNs += System.nanoTime() - start;
            cache.sensorSignalMisses++;
        } else {
            cache.sensorSignalHits++;
        }
        return cache.sensorInterestSignals;
    }

    private static Ship committedOrImmediateThreat(GameContext ctx, Ship ship, Ship selected) {
        if (isAlive(selected)) return selected;
        if (ctx == null || ship == null) return null;
        return immediateThreatCached(ctx, ship, Math.max(180.0, preferredRange(ship) * 0.56));
    }

    private static void updateShipWillpower(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        ArrayList<Ship> nearby = new ArrayList<>();
        for (Ship ship : ctx.ships) {
            if (!canSurrender(ship)) continue;
            if (ship.surrendered) {
                if (shouldRecoverWillpower(ctx, ship, nearby)) {
                    ship.clearSurrenderState();
                }
                continue;
            }
            if (ship.aiForcedEngageTimer > 0.0 || ship.isWarpCharging()) continue;
            if (!shouldSurrender(ctx, ship, nearby)) continue;
            ship.enterSurrenderState(18.0);
            EventSystem.showWorldCallout(ctx, ship.x, ship.y - ship.radius - 20.0, "SURRENDERING", new java.awt.Color(255, 226, 154), 1.4);
            if (ctx.player != null && ship.faction != null && !ship.faction.isFriendlyTo(ctx.player.faction)) {
                EventSystem.showBanner(ctx, ship.name + " IS SURRENDERING", 1.0);
            }
        }
    }

    private static boolean canSurrender(Ship ship) {
        if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) return false;
        if (ship.faction == null) return false;
        if (ship.isSmallCraft()) return false;
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) return false;
        if (ship.role.isTitanOrMothership()) return false;
        return true;
    }

    private static boolean shouldRecoverWillpower(GameContext ctx, Ship ship, ArrayList<Ship> nearby) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        if (ship.surrenderLockTimer > 0.0) return false;
        nearby.clear();
        ctx.entityQuery.collectAliveShipsNear(ship.x, ship.y, 520.0, nearby);
        int friendly = 0;
        int hostile = 0;
        for (Ship other : nearby) {
            if (other == null || other == ship || !other.alive || other.dying || other.hp <= 0) continue;
            if (other.faction == null) continue;
            if (ship.faction.isFriendlyTo(other.faction)) friendly++;
            else hostile++;
        }
        return friendly >= 2 && hostile == 0;
    }

    private static boolean shouldSurrender(GameContext ctx, Ship ship, ArrayList<Ship> nearby) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        double hullFrac = (ship.hpMax <= 0) ? 0.0 : MathUtil.clamp(ship.hp / (double) ship.hpMax, 0.0, 1.0);
        double shieldFrac = (ship.shieldMax <= 1e-6) ? 0.0 : MathUtil.clamp(ship.shield / ship.shieldMax, 0.0, 1.0);
        if (hullFrac > 0.30 && (hullFrac + shieldFrac) > 0.34) return false;
        if (ship.secondsSinceDamage() > 2.5) return false;
        if (ship.aiCommittedTargetId > 0 && ship.aiTargetCommitTimer > 2.0) return false;

        nearby.clear();
        ctx.entityQuery.collectAliveShipsNear(ship.x, ship.y, 680.0, nearby);
        int friendly = 0;
        int hostile = 0;
        int hostileCapitals = 0;
        for (Ship other : nearby) {
            if (other == null || other == ship || !other.alive || other.dying || other.hp <= 0 || other.faction == null) continue;
            if (ship.faction.isFriendlyTo(other.faction)) {
                if (!other.isSmallCraft()) friendly++;
            } else {
                hostile++;
                if (!other.isSmallCraft() && (other.role == ShipRole.CRUISER || other.role == ShipRole.BATTLECRUISER
                        || other.role == ShipRole.BATTLESHIP || other.role == ShipRole.DREADNOUGHT
                        || other.role == ShipRole.SUPERSHIP || other.role.isTitanOrMothership())) {
                    hostileCapitals++;
                }
            }
        }
        boolean isolated = friendly <= 1;
        boolean overwhelmed = hostile >= Math.max(2, friendly + 2) || hostileCapitals > 0;
        boolean disabled = ship.isTemporarilyDisabled() || ship.isStasisFieldTrapped() || ship.isDestabilized();
        return isolated && overwhelmed && (disabled || hullFrac <= 0.22 || (hullFrac + shieldFrac) <= 0.26);
    }

    static int resourceRushCappedGroupCount(int aliveShips, int requestedGroups) {
        int desired = Math.max(0, requestedGroups);
        int shipsAlive = Math.max(0, aliveShips);
        int remainingBudget = Math.max(0, RESOURCE_RUSH_TEAM_SHIP_CAP - shipsAlive);
        if (desired <= 0 || remainingBudget <= 0) return 0;
        int maxGroups = Math.max(0, remainingBudget / RESOURCE_RUSH_ESTIMATED_SHIPS_PER_GROUP);
        if (maxGroups <= 0 && remainingBudget > 0 && shipsAlive <= RESOURCE_RUSH_TEAM_SHIP_CAP - 2) {
            maxGroups = 1;
        }
        return Math.min(desired, maxGroups);
    }

    private static FleetState buildFleetState(GameContext ctx, double dt) {
        FleetState out = new FleetState();
        if (ctx == null || ctx.ships == null) return out;
        FleetStateBuildCache buildCache = new FleetStateBuildCache();
        decayCommCommandOverrides(ctx, dt);
        if (ctx.command.shipFleetCommandOverrides != null && !ctx.command.shipFleetCommandOverrides.isEmpty()) {
            ctx.command.shipFleetCommandOverrides.entrySet().removeIf(e -> !hasLiveShipId(ctx.ships, e.getKey()));
        }
        if (ctx.command.fleetCommandShips != null) ctx.command.fleetCommandShips.clear();
        if (ctx.command.fleetSharedTargets != null) ctx.command.fleetSharedTargets.clear();
        if (ctx.command.fleetResolvedCommands != null) ctx.command.fleetResolvedCommands.clear();
        if (ctx.command.fleetResolvedFormations != null) ctx.command.fleetResolvedFormations.clear();
        if (ctx.command.fleetSquadLabelByShip != null) ctx.command.fleetSquadLabelByShip.clear();
        if (ctx.command.fleetSquadRoleByShip != null) ctx.command.fleetSquadRoleByShip.clear();
        if (ctx.command.fleetSquadLeaderByShip != null) ctx.command.fleetSquadLeaderByShip.clear();
        if (ctx.command.fleetSquadIndexByShip != null) ctx.command.fleetSquadIndexByShip.clear();
        markKillConfirmTargets(ctx);

        Map<Integer, Double> bestScore = new HashMap<>();
        for (Ship s : ctx.ships) {
            if (!FleetStateBuilder.isFleetMemberCandidate(s)) continue;

            int groupKey = fleetGroupKeyForShip(ctx, s);
            out.shipGroupKeys.put(s.id, groupKey);
            out.members.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(s);
            out.teamFactions.putIfAbsent(groupKey, s.faction);

            if (canLeadFleetFormation(s)) {
                double score = flagshipScore(s);
                Double current = bestScore.get(groupKey);
                if (current == null || score > current) {
                    bestScore.put(groupKey, score);
                    out.flagships.put(groupKey, s);
                }
            }
        }
        if (ctx.player != null && isAlive(ctx.player)) {
            int playerGroup = playerFleetGroupKey(ctx);
            if (out.members.containsKey(playerGroup)) {
                out.flagships.put(playerGroup, ctx.player);
            }
        }

        for (List<Ship> members : out.members.values()) {
            members.sort(Comparator.comparingDouble(AISystem::flagshipScore).reversed());
        }

        for (Map.Entry<Integer, List<Ship>> e : out.members.entrySet()) {
            int teamId = e.getKey();
            List<Ship> members = e.getValue();
            Ship flagship = out.flagships.get(teamId);
            SharedTargetChoice shared = reuseStableSharedTarget(ctx, teamId, members, flagship, buildCache, dt);
            if (shared == null) {
                shared = selectSharedTargetForTeam(ctx, members, flagship, buildCache);
                storeStableSharedTarget(teamId, shared);
            }
            Faction teamFaction = out.teamFactions.get(teamId);
            shared = applyCommandLatencyAndFog(ctx, teamId, teamFaction, shared, dt);
            if (shared != null && shared.target != null) {
                out.sharedTargets.put(teamId, shared.target);
                out.sharedTargetConfidence.put(teamId, shared.confidence);
            }
            Ship threatened = selectMissileThreatFocusForTeam(ctx, members, flagship);
            if (threatened != null) out.missileThreatFocus.put(teamId, threatened);
            assignSquadObjectives(out, teamId, members, flagship, (shared == null) ? null : shared.target, threatened);
            assignSquadronIdentities(out, teamId, members, flagship);
            List<Ship> focusTargets = buildTeamFocusTargets(ctx, members, flagship, (shared == null) ? null : shared.target, threatened);
            if (!focusTargets.isEmpty()) out.focusTargets.put(teamId, focusTargets);
        }
        pruneTeamTransientState(out.members.keySet());

        updateTeamDangerMemory(ctx, out, Math.max(0.0, dt));
        refreshTeamFormationPlans(ctx, out);

        for (Map.Entry<Integer, Ship> e : out.flagships.entrySet()) {
            Faction f = out.teamFactions.get(e.getKey());
            if (f != null && ctx.command.fleetCommandShips != null) {
                if (shouldPublishFleetCommandShip(ctx, f, e.getValue())) {
                    ctx.command.fleetCommandShips.put(f, e.getValue());
                }
            }
        }
        for (Map.Entry<Integer, Ship> e : out.sharedTargets.entrySet()) {
            Faction f = out.teamFactions.get(e.getKey());
            if (f != null && ctx.command.fleetSharedTargets != null) {
                Ship flagship = out.flagships.get(e.getKey());
                if (shouldPublishFleetCommandShip(ctx, f, flagship)) {
                    ctx.command.fleetSharedTargets.put(f, e.getValue());
                }
            }
        }
        syncFleetPresentation(ctx, out);
        return out;
    }

    private static final class FleetStateBuildCache {
        final Map<Ship, Double> observerSensorMul = new IdentityHashMap<>();
        final Map<Ship, Double> targetSignatureMul = new IdentityHashMap<>();
    }

    private static void decayCommCommandOverrides(GameContext ctx, double dt) {
        if (ctx == null || ctx.command == null) return;
        double step = Math.max(0.0, dt);
        if (ctx.command.shipFleetCommandOverrideTimers != null && !ctx.command.shipFleetCommandOverrideTimers.isEmpty()) {
            List<Integer> expired = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : ctx.command.shipFleetCommandOverrideTimers.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                double remain = Math.max(0.0, e.getValue() - step);
                if (remain <= 0.0 || !hasLiveShipId(ctx.ships, e.getKey())) {
                    expired.add(e.getKey());
                } else {
                    e.setValue(remain);
                }
            }
            for (Integer id : expired) {
                if (id == null) continue;
                ctx.command.shipFleetCommandOverrideTimers.remove(id);
                if (ctx.command.shipFleetCommandOverrides != null) {
                    ctx.command.shipFleetCommandOverrides.remove(id);
                }
            }
        }
        if (ctx.command.shipCommActionCooldowns != null && !ctx.command.shipCommActionCooldowns.isEmpty()) {
            List<Integer> cooled = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : ctx.command.shipCommActionCooldowns.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                double remain = Math.max(0.0, e.getValue() - step);
                if (remain <= 0.0 || !hasLiveShipId(ctx.ships, e.getKey())) {
                    cooled.add(e.getKey());
                } else {
                    e.setValue(remain);
                }
            }
            for (Integer id : cooled) {
                if (id == null) continue;
                ctx.command.shipCommActionCooldowns.remove(id);
            }
        }
        if (ctx.command.shipCommCeasefireTimers != null && !ctx.command.shipCommCeasefireTimers.isEmpty()) {
            List<Integer> expired = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : ctx.command.shipCommCeasefireTimers.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                double remain = Math.max(0.0, e.getValue() - step);
                if (remain <= 0.0 || !hasLiveShipId(ctx.ships, e.getKey())) {
                    expired.add(e.getKey());
                } else {
                    e.setValue(remain);
                }
            }
            for (Integer id : expired) {
                if (id == null) continue;
                ctx.command.shipCommCeasefireTimers.remove(id);
            }
        }
    }

    private static SharedTargetChoice selectSharedTargetForTeam(GameContext ctx, List<Ship> members, Ship flagship,
                                                                FleetStateBuildCache buildCache) {
        if (ctx == null || members == null || members.isEmpty()) return null;
        Ship anchor = (flagship != null) ? flagship : members.get(0);
        if (anchor == null || anchor.faction == null) return null;

        double cx = 0.0;
        double cy = 0.0;
        int n = 0;
        for (Ship s : members) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            cx += s.x;
            cy += s.y;
            n++;
        }
        if (n <= 0) return null;
        cx /= n;
        cy /= n;

        int aliveCount = 0;
        for (Ship s : members) {
            if (isAlive(s)) aliveCount++;
        }
        if (aliveCount <= 0) return null;

        double maxMemberDist = 0.0;
        for (Ship s : members) {
            if (!isAlive(s)) continue;
            double d = Math.hypot(s.x - cx, s.y - cy);
            if (d > maxMemberDist) maxMemberDist = d;
        }

        double queryRadius = Math.max(2200.0, maxThreatSearchRadius(ctx, anchor) + maxMemberDist + 180.0);
        ArrayList<Ship> candidates = borrowShipScratch();
        ctx.entityQuery.collectHostileShipsNear(anchor.faction, cx, cy, queryRadius, candidates);

        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestConfidence = 0.0;
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        int observerSampleCap = (scalePlan != null && scalePlan.isLargeBattle()) ? 28 : Integer.MAX_VALUE;
        try {
            for (Ship enemy : candidates) {
                if (!isAlive(enemy)) continue;
                if (enemy.role == ShipRole.BASE) continue;

                int observers = 0;
                int observerSamples = 0;
                double observerPriority = 0.0;
                double observerConfidence = 0.0;
                for (Ship observer : members) {
                    if (!isAlive(observer)) continue;
                    observerSamples++;
                    if (isDetectableToObserverCached(ctx, observer, enemy, buildCache)) {
                        double dObs = Math.hypot(enemy.x - observer.x, enemy.y - observer.y);
                        double ewConf = observerEWConfidence(ctx, observer, enemy, dObs, buildCache);
                        observers++;
                        observerPriority += threatPriority(observer.role, enemy.role) * ewConf;
                        observerConfidence += ewConf;
                    }
                    if (observerSamples >= observerSampleCap) break;
                }
                if (observers <= 0) continue;

                double confidenceDenom = (observerSampleCap == Integer.MAX_VALUE)
                        ? Math.max(1e-9, aliveCount)
                        : Math.max(1.0, observerSamples);
                double confidence = Math.max(0.0, Math.min(1.0, observerConfidence / confidenceDenom));
                double avgPriority = observerPriority / Math.max(1.0, observerConfidence);
                double hpFrac = (enemy.hpMax <= 0) ? 1.0 : (enemy.hp / (double) enemy.hpMax);
                double d = Math.hypot(enemy.x - cx, enemy.y - cy);
                double score = (1.0 - hpFrac) * 720.0;
                score += roleWeightForFlagship(enemy.role) * 26.0;
                score += avgPriority * 92.0;
                score += Math.max(0.0, 1200.0 - d) * 0.15;
                score += retreatIntentBonus(enemy, cx, cy, hpFrac);
                score += confidence * 110.0;
                score += sectorTargetPriorityBias(ctx, anchor, enemy);
                if (confidence < 0.32) score -= (0.32 - confidence) * 340.0;
                score -= killConfirmTargetPenalty(enemy, hpFrac);
                if (enemy == ctx.lockedTarget) score += 260.0;
                if (score > bestScore) {
                    bestScore = score;
                    best = enemy;
                    bestConfidence = confidence;
                }
            }
        } finally {
            releaseShipScratch(candidates);
        }
        return (best == null) ? null : new SharedTargetChoice(best, bestConfidence);
    }

    private static List<Ship> buildTeamFocusTargets(GameContext ctx, List<Ship> members, Ship flagship,
                                                    Ship primary, Ship threatenedAlly) {
        ArrayList<Ship> focus = new ArrayList<>(4);
        if (ctx == null || members == null || members.isEmpty()) return focus;
        Ship anchor = isAlive(flagship) ? flagship : members.get(0);
        if (anchor == null || anchor.faction == null) return focus;
        addFocusTarget(focus, primary);
        if (isAlive(threatenedAlly)) {
            Ship threat = findImmediateThreat(ctx, threatenedAlly,
                    Math.max(720.0, preferredRange(threatenedAlly) * 1.8));
            addFocusTarget(focus, threat);
        }

        double cx = 0.0;
        double cy = 0.0;
        int live = 0;
        for (Ship member : members) {
            if (!isAlive(member)) continue;
            cx += member.x;
            cy += member.y;
            live++;
        }
        if (live <= 0) return focus;
        cx /= live;
        cy /= live;

        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            double radius = Math.max(1800.0, maxThreatSearchRadius(ctx, anchor) + 800.0);
            ctx.entityQuery.collectHostileShipsNear(anchor.faction, cx, cy, radius, nearby);
            for (int pick = focus.size(); pick < 4; pick++) {
                Ship best = null;
                double bestScore = Double.NEGATIVE_INFINITY;
                for (Ship enemy : nearby) {
                    if (!isAlive(enemy) || containsShip(focus, enemy)) continue;
                    if (enemy.role == ShipRole.BASE) continue;
                    if (!TargetingSystem.isDetectableToObserver(anchor, enemy)) continue;
                    double d = Math.hypot(enemy.x - cx, enemy.y - cy);
                    double hpFrac = enemy.hpMax <= 0 ? 1.0 : enemy.hp / (double) enemy.hpMax;
                    double score = roleWeightForFlagship(enemy.role) * 28.0
                            + threatPriority(anchor.role, enemy.role) * 96.0
                            + (1.0 - hpFrac) * 280.0
                            + Math.max(0.0, 1500.0 - d) * 0.12
                            + targetVulnerabilityScore(anchor, enemy);
                    if (enemy == ctx.lockedTarget) score += 240.0;
                    if (score > bestScore) {
                        bestScore = score;
                        best = enemy;
                    }
                }
                if (!addFocusTarget(focus, best)) break;
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return focus;
    }

    private static boolean addFocusTarget(List<Ship> focus, Ship target) {
        if (focus == null || !isAlive(target) || containsShip(focus, target)) return false;
        focus.add(target);
        return true;
    }

    private static boolean containsShip(List<Ship> ships, Ship target) {
        if (ships == null || target == null) return false;
        for (Ship ship : ships) {
            if (ship == target || (ship != null && ship.id == target.id)) return true;
        }
        return false;
    }

    private static boolean isDetectableToObserverCached(GameContext ctx, Ship observer, Ship target,
                                                        FleetStateBuildCache buildCache) {
        if (standardProsecutionContactAvailable(observer, target)) return true;
        if (playerFleetProsecutionContactAvailable(ctx, observer, target)) return true;
        if (redFleetProsecutionContactAvailable(ctx, observer, target)) return true;
        double sensorMul = cachedObserverSensorMultiplier(observer, buildCache);
        double targetSigMul = cachedTargetSignatureMultiplier(target, buildCache);
        return TargetingSystem.isDetectableToObserver(ctx, observer, target, sensorMul, targetSigMul);
    }

    private static boolean playerFleetProsecutionContactAvailable(GameContext ctx, Ship observer, Ship target) {
        if (ctx == null || observer == null || target == null || observer.faction == null || target.faction == null) return false;
        if (!isFriendlyToPlayer(ctx, observer) || observer.faction.isFriendlyTo(target.faction)) return false;
        return Math.hypot(target.x - observer.x, target.y - observer.y) <= PLAYER_FLEET_PROSECUTION_RANGE;
    }

    private static boolean redFleetProsecutionContactAvailable(GameContext ctx, Ship observer, Ship target) {
        if (observer == null || target == null || observer.faction == null || target.faction == null) return false;
        if (observer.faction != Faction.ENEMY || observer.faction.isFriendlyTo(target.faction)) return false;
        return Math.hypot(target.x - observer.x, target.y - observer.y) <= RED_FLEET_PROSECUTION_RANGE;
    }

    private static boolean standardProsecutionContactAvailable(Ship observer, Ship target) {
        if (!isAlive(observer) || !isAlive(target)) return false;
        if (observer.faction == null || target.faction == null || observer.faction.isFriendlyTo(target.faction)) return false;
        if (Math.hypot(target.x - observer.x, target.y - observer.y) > STANDARD_PROSECUTION_RANGE) return false;
        return hasStandardProsecutionWeapon(observer, target);
    }

    private static boolean hasStandardProsecutionWeapon(Ship observer, Ship target) {
        if (observer == null) return false;
        if (observer.hasSuperweapon) return true;
        if (observer.turrets == null) return false;
        for (Turret turret : observer.turrets) {
            if (turret == null) continue;
            if (turret.kind == Turret.Kind.GUN && !Turret.usesCiwsPelletsAgainst(observer, turret, target)) {
                return true;
            }
            if (isOffensiveMissileTurret(turret)) {
                return true;
            }
        }
        return false;
    }

    private static SharedTargetChoice reuseStableSharedTarget(GameContext ctx, int teamId, List<Ship> members,
                                                              Ship flagship, FleetStateBuildCache buildCache, double dt) {
        if (ctx == null || members == null || members.isEmpty()) return null;
        double ttl = Math.max(0.0, TEAM_STABLE_SHARED_TARGET_TTL.getOrDefault(teamId, 0.0) - Math.max(0.0, dt));
        if (ttl <= 1e-6) {
            TEAM_STABLE_SHARED_TARGET_TTL.remove(teamId);
            return null;
        }
        TEAM_STABLE_SHARED_TARGET_TTL.put(teamId, ttl);
        Integer cachedId = TEAM_STABLE_SHARED_TARGET_IDS.get(teamId);
        if (cachedId == null) return null;
        Ship cached = findLiveShipById(ctx.ships, cachedId);
        if (!isAlive(cached)) return null;

        Ship anchor = isAlive(flagship) ? flagship : members.get(0);
        if (anchor == null || anchor.faction == null || cached.faction == null) return null;
        if (anchor.faction.isFriendlyTo(cached.faction)) return null;
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null && scalePlan.isLargeBattle()) {
            double maxRange = Math.max(2600.0, maxThreatSearchRadius(ctx, anchor) + 900.0);
            if (Math.hypot(cached.x - anchor.x, cached.y - anchor.y) <= maxRange) {
                double confidence = TEAM_STABLE_SHARED_TARGET_CONFIDENCE.getOrDefault(teamId, 0.42);
                return new SharedTargetChoice(cached, Math.max(0.24, confidence * 0.96));
            }
        }

        double cx = 0.0;
        double cy = 0.0;
        int n = 0;
        double maxMemberDist = 0.0;
        for (Ship s : members) {
            if (!isAlive(s)) continue;
            cx += s.x;
            cy += s.y;
            n++;
        }
        if (n <= 0) return null;
        cx /= n;
        cy /= n;
        for (Ship s : members) {
            if (!isAlive(s)) continue;
            double d = Math.hypot(s.x - cx, s.y - cy);
            if (d > maxMemberDist) maxMemberDist = d;
        }
        double queryRadius = Math.max(2200.0, maxThreatSearchRadius(ctx, anchor) + maxMemberDist + 180.0);
        if (Math.hypot(cached.x - cx, cached.y - cy) > queryRadius) return null;

        int aliveCount = 0;
        double confidenceSum = 0.0;
        for (Ship observer : members) {
            if (!isAlive(observer)) continue;
            aliveCount++;
            if (!isDetectableToObserverCached(ctx, observer, cached, buildCache)) continue;
            double dObs = Math.hypot(cached.x - observer.x, cached.y - observer.y);
            confidenceSum += observerEWConfidence(ctx, observer, cached, dObs, buildCache);
        }
        if (aliveCount <= 0 || confidenceSum <= 0.04) return null;
        double confidence = Math.max(0.0, Math.min(1.0, confidenceSum / Math.max(1.0, aliveCount)));
        if (confidence < 0.22) return null;
        return new SharedTargetChoice(cached, Math.max(confidence,
                TEAM_STABLE_SHARED_TARGET_CONFIDENCE.getOrDefault(teamId, 0.0) * 0.92));
    }

    private static void storeStableSharedTarget(int teamId, SharedTargetChoice shared) {
        if (shared == null || !isAlive(shared.target)) {
            TEAM_STABLE_SHARED_TARGET_IDS.remove(teamId);
            TEAM_STABLE_SHARED_TARGET_CONFIDENCE.remove(teamId);
            TEAM_STABLE_SHARED_TARGET_TTL.remove(teamId);
            return;
        }
        TEAM_STABLE_SHARED_TARGET_IDS.put(teamId, shared.target.id);
        TEAM_STABLE_SHARED_TARGET_CONFIDENCE.put(teamId, shared.confidence);
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        double ttl = 0.14 + Math.max(0.0, Math.min(0.22, shared.confidence * 0.16));
        if (scalePlan != null && scalePlan.isLargeBattle()) {
            ttl = Math.max(ttl, 0.36 + Math.max(0.0, Math.min(0.32, shared.confidence * 0.22)));
        }
        TEAM_STABLE_SHARED_TARGET_TTL.put(teamId, ttl);
    }

    private static Ship selectMissileThreatFocusForTeam(GameContext ctx, List<Ship> members, Ship flagship) {
        if (ctx == null || members == null || members.isEmpty()) return null;
        if (ctx.projectiles == null || ctx.projectiles.isEmpty()) return null;
        Ship anchor = (flagship != null) ? flagship : members.get(0);
        if (anchor == null || anchor.faction == null) return null;

        Map<Integer, Integer> incomingByShipId = new HashMap<>();
        Map<Integer, Ship> shipById = new HashMap<>();
        for (Ship s : members) {
            if (!isAlive(s)) continue;
            shipById.put(s.id, s);
        }
        if (shipById.isEmpty()) return null;

        ArrayList<Missile> missiles = borrowMissileScratch();
        try {
            ctx.entityQuery.collectMissilesNear(anchor.x, anchor.y, 1800.0, missiles);
            for (Missile m : missiles) {
                if (!m.alive || !isAlive(m.target)) continue;
                Ship t = m.target;
                if (t.faction == null || m.faction == null || anchor.faction.isFriendlyTo(m.faction)) continue;
                if (!shipById.containsKey(t.id)) continue;
                incomingByShipId.merge(t.id, 1, Integer::sum);
            }
        } finally {
            releaseMissileScratch(missiles);
        }

        Ship best = null;
        int bestIncoming = 0;
        for (Map.Entry<Integer, Integer> e : incomingByShipId.entrySet()) {
            Ship candidate = shipById.get(e.getKey());
            int count = e.getValue();
            if (!isAlive(candidate)) continue;
            if (count > bestIncoming) {
                bestIncoming = count;
                best = candidate;
            }
        }
        return best;
    }

    private static void syncFleetPresentation(GameContext ctx, FleetState state) {
        if (ctx == null || state == null) return;
        syncFleetMap(ctx.command.fleetSquadLabelByShip, state.squadLabels);
        syncFleetMap(ctx.command.fleetSquadRoleByShip, state.squadRoles);
        syncFleetMap(ctx.command.fleetSquadLeaderByShip, state.squadLeaders);
        syncFleetMap(ctx.command.fleetSquadIndexByShip, state.squadIndexes);
        syncResolvedFleetOrders(ctx, state);
        updateFleetNetTraffic(ctx, state);
    }

    private static void syncResolvedFleetOrders(GameContext ctx, FleetState state) {
        if (ctx == null || state == null) return;
        for (Map.Entry<Integer, Ship> e : state.flagships.entrySet()) {
            if (e == null) continue;
            int teamId = e.getKey();
            Ship flagship = e.getValue();
            Faction teamFaction = state.teamFactions.get(teamId);
            if (teamFaction == null || !isAlive(flagship)) continue;

            GameContext.FleetCommand command = resolveFleetCommand(ctx, flagship, flagship);
            boolean publish = shouldPublishFleetCommandShip(ctx, teamFaction, flagship);
            if (publish && ctx.command.fleetResolvedCommands != null) {
                ctx.command.fleetResolvedCommands.put(teamFaction,
                        (command == null) ? GameContext.FleetCommand.AUTO : command);
            }

            boolean playerDirected = playerCanDirectTeamFleet(ctx, flagship, flagship);
            GameContext.FleetFormation formation = playerDirected
                    ? ctx.command.alliedFleetFormation
                    : state.autoFormation.getOrDefault(teamId, GameContext.FleetFormation.WEDGE);
            if (publish && ctx.command.fleetResolvedFormations != null) {
                ctx.command.fleetResolvedFormations.put(teamFaction,
                        (formation == null) ? GameContext.FleetFormation.WEDGE : formation);
            }
        }
    }

    private static void updateFleetNetTraffic(GameContext ctx, FleetState state) {
        if (ctx == null || state == null || ctx.player == null || ctx.player.faction == null) return;
        int playerTeamId = playerFleetGroupKey(ctx);
        java.util.HashSet<Integer> activeKeys = borrowIntSetScratch();
        try {
            for (Map.Entry<Integer, String> e : state.squadLabels.entrySet()) {
                Integer shipId = e.getKey();
                if (shipId == null) continue;
                Integer leaderId = state.squadLeaders.get(shipId);
                if (leaderId == null || leaderId.intValue() != shipId.intValue()) continue;
                Ship leader = findShipById(ctx.ships, shipId);
                if (!isAlive(leader) || leader.faction == null || fleetGroupKeyForShip(state, leader) != playerTeamId) continue;

                Ship flagship = state.flagships.get(playerTeamId);
                SquadObjective objective = state.squadObjectives.getOrDefault(shipId, SquadObjective.HOLD);
                GameContext.FleetCommand command = resolveFleetCommand(ctx, leader, flagship);
                Ship target = state.sharedTargets.get(playerTeamId);
                int key = shipId;
                activeKeys.add(key);
                boolean trackTarget = objective == SquadObjective.FLANK || command == GameContext.FleetCommand.ATTACK;
                long signature = fleetTrafficSignature(command, objective,
                        trackTarget && isAlive(target) ? target.id : -1,
                        leader.isWarpCharging());
                Long previous = ctx.command.fleetSquadStatusMemory.get(key);
                if (previous != null && previous.longValue() != signature) {
                    postFleetComm(ctx, leader.faction, e.getValue(), describeSquadTraffic(leader, e.getValue(), command, objective, target));
                }
                ctx.command.fleetSquadStatusMemory.put(key, signature);
            }
            if (!ctx.command.fleetSquadStatusMemory.isEmpty()) {
                ctx.command.fleetSquadStatusMemory.entrySet().removeIf(entry -> entry == null || !activeKeys.contains(entry.getKey()));
            }
        } finally {
            releaseIntSetScratch(activeKeys);
        }
    }

    private static long fleetTrafficSignature(GameContext.FleetCommand command,
                                              SquadObjective objective,
                                              int targetId,
                                              boolean warpCharging) {
        int commandOrdinal = (command == null) ? -1 : command.ordinal();
        int objectiveOrdinal = (objective == null) ? -1 : objective.ordinal();
        long commandBits = ((long) (commandOrdinal + 1) & 0xffL) << 56;
        long objectiveBits = ((long) (objectiveOrdinal + 1) & 0xffL) << 48;
        long warpBits = warpCharging ? (1L << 47) : 0L;
        long targetBits = ((long) targetId) & 0xffffffffL;
        return commandBits | objectiveBits | warpBits | targetBits;
    }

    private static String describeSquadTraffic(Ship leader, String label, GameContext.FleetCommand command,
                                               SquadObjective objective, Ship target) {
        if (leader != null && leader.isWarpCharging()) return "warping to reform with command";
        if (command == null) command = GameContext.FleetCommand.AUTO;
        return switch (objective) {
            case INTERCEPT -> "screening inbound threats";
            case FLANK -> isAlive(target) ? "flanking target " + target.id : "swinging wide on the flank";
            case RESERVE -> (command == GameContext.FleetCommand.REPAIR || command == GameContext.FleetCommand.RETREAT)
                    ? "falling back for repairs"
                    : "holding in reserve";
            case HOLD -> switch (command) {
                case ATTACK -> isAlive(target) ? "advancing on target " + target.id : "pushing forward";
                case DEFEND, ESCORT, FORM_UP -> "holding the battle line";
                case RETREAT, REPAIR, RTB -> "covering the withdrawal";
                case MINE -> "covering the mining screen";
                default -> "maintaining formation";
            };
        };
    }

    private static void postFleetComm(GameContext ctx, Faction faction, String channel, String text) {
        if (ctx == null || text == null || text.isBlank()) return;
        if (ctx.fleetCommLog.size() >= MAX_FLEET_COMM_LOG) {
            ctx.fleetCommLog.remove(0);
        }
        ctx.fleetCommLog.add(new GameContext.FleetCommMessage(faction, channel, text, 8.5));
    }

    private static void tickFleetCommLog(GameContext ctx, double dt) {
        if (ctx == null || ctx.fleetCommLog.isEmpty()) return;
        ctx.fleetCommLog.removeIf(msg -> {
            if (msg == null) return true;
            msg.ttl -= dt;
            return msg.ttl <= 0.0;
        });
    }

    private static Ship findShipById(List<Ship> ships, int shipId) {
        if (ships == null || shipId <= 0) return null;
        for (Ship s : ships) {
            if (s != null && s.id == shipId) return s;
        }
        return null;
    }

    private static SharedTargetChoice applyCommandLatencyAndFog(GameContext ctx, int teamId, Faction teamFaction,
                                                                SharedTargetChoice rawChoice, double dt) {
        if (rawChoice == null || rawChoice.target == null) {
            double t = TEAM_COMMAND_DELAY_TIMERS.getOrDefault(teamId, 0.0);
            if (t > 0.0) TEAM_COMMAND_DELAY_TIMERS.put(teamId, Math.max(0.0, t - Math.max(0.0, dt)));
            return rawChoice;
        }
        if (ctx == null || teamFaction == null || ctx.player == null || ctx.player.faction == null) return rawChoice;
        if (teamFaction.isFriendlyTo(ctx.player.faction)) {
            TEAM_DELAYED_TARGET_IDS.put(teamId, rawChoice.target.id);
            TEAM_COMMAND_DELAY_TIMERS.put(teamId, 0.0);
            return rawChoice;
        }

        // Enemy fleets react with lag as contact confidence falls.
        double hold = TEAM_COMMAND_DELAY_TIMERS.getOrDefault(teamId, 0.0);
        int delayedId = TEAM_DELAYED_TARGET_IDS.getOrDefault(teamId, -1);
        Ship delayedTarget = findLiveShipById(ctx.ships, delayedId);
        double fogPenalty = 0.06 + (1.0 - Math.max(0.0, Math.min(1.0, rawChoice.confidence))) * 0.26;
        double effectiveConfidence = Math.max(0.08, Math.min(1.0, rawChoice.confidence * (1.0 - fogPenalty)));

        if (hold > 0.0) {
            hold = Math.max(0.0, hold - Math.max(0.0, dt));
            TEAM_COMMAND_DELAY_TIMERS.put(teamId, hold);
            if (isAlive(delayedTarget)) {
                return new SharedTargetChoice(delayedTarget, Math.max(0.08, effectiveConfidence * 0.74));
            }
        }

        if (isAlive(delayedTarget) && delayedTarget.id != rawChoice.target.id) {
            double switchDelay = 0.20 + (1.0 - effectiveConfidence) * 0.72;
            TEAM_COMMAND_DELAY_TIMERS.put(teamId, switchDelay);
            TEAM_DELAYED_TARGET_IDS.put(teamId, delayedTarget.id);
            return new SharedTargetChoice(delayedTarget, Math.max(0.08, effectiveConfidence * 0.70));
        }

        TEAM_DELAYED_TARGET_IDS.put(teamId, rawChoice.target.id);
        TEAM_COMMAND_DELAY_TIMERS.put(teamId, 0.0);
        return new SharedTargetChoice(rawChoice.target, effectiveConfidence);
    }

    private static void pruneTeamTransientState(java.util.Set<Integer> liveTeamIds) {
        if (liveTeamIds == null) return;
        TEAM_COMMAND_DELAY_TIMERS.entrySet().removeIf(e -> !liveTeamIds.contains(e.getKey()));
        TEAM_DELAYED_TARGET_IDS.entrySet().removeIf(e -> !liveTeamIds.contains(e.getKey()));
        TEAM_STABLE_SHARED_TARGET_IDS.entrySet().removeIf(e -> !liveTeamIds.contains(e.getKey()));
        TEAM_STABLE_SHARED_TARGET_CONFIDENCE.entrySet().removeIf(e -> !liveTeamIds.contains(e.getKey()));
        TEAM_STABLE_SHARED_TARGET_TTL.entrySet().removeIf(e -> !liveTeamIds.contains(e.getKey()));
    }

    private static Ship findLiveShipById(List<Ship> ships, int id) {
        if (ships == null || id <= 0) return null;
        for (Ship s : ships) {
            if (!isAlive(s)) continue;
            if (s.id == id) return s;
        }
        return null;
    }

    private static Ship findClosestHostileBase(GameContext ctx, Ship seeker) {
        if (ctx == null || seeker == null || seeker.faction == null || ctx.ships == null) return null;
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship s : ctx.ships) {
            if (!isAlive(s)) continue;
            if (s.role != ShipRole.BASE) continue;
            if (s.faction == null || seeker.faction.isFriendlyTo(s.faction)) continue;
            double d2 = dist2(seeker.x, seeker.y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    private static void assignSquadObjectives(FleetState state, int teamId, List<Ship> members, Ship flagship,
                                              Ship sharedTarget, Ship threatenedAlly) {
        if (state == null || members == null || members.isEmpty()) return;
        if (flagship != null) state.squadObjectives.put(flagship.id, SquadObjective.HOLD);
        List<Ship> pool = new ArrayList<>();
        for (Ship s : members) {
            if (!isAlive(s)) continue;
            if (s == flagship) continue;
            pool.add(s);
        }
        if (pool.isEmpty()) return;

        int n = pool.size();
        int reserveQuota = Math.max((n >= 4) ? 1 : 0, (int) Math.round(n * 0.18));
        int interceptQuota = Math.max((n >= 3) ? 1 : 0, (int) Math.round(n * 0.20) + (isAlive(threatenedAlly) ? 1 : 0));
        int flankQuota = isAlive(sharedTarget)
                ? Math.max((n >= 5) ? 1 : 0, (int) Math.round(n * 0.24))
                : Math.max(0, (int) Math.round(n * 0.10));
        int quotaSum = reserveQuota + interceptQuota + flankQuota;
        if (quotaSum > n) {
            int excess = quotaSum - n;
            int cut = Math.min(excess, flankQuota);
            flankQuota -= cut;
            excess -= cut;
            cut = Math.min(excess, interceptQuota);
            interceptQuota -= cut;
            excess -= cut;
            reserveQuota = Math.max(0, reserveQuota - excess);
        }

        Map<Integer, SquadObjective> assigned = new HashMap<>();
        IdentityHashMap<Ship, Boolean> assignedShips = new IdentityHashMap<>();
        int reserveAssigned = 0;
        int interceptAssigned = 0;
        int flankAssigned = 0;
        for (Ship s : pool) {
            if (isSupportRole(s.role) || hullFrac(s) < 0.52 || shieldFrac(s) < 0.30) {
                assigned.put(s.id, SquadObjective.RESERVE);
                assignedShips.put(s, Boolean.TRUE);
                reserveAssigned++;
                continue;
            }
            if (isCapitalRole(s.role) && shouldRotateCapitalBehindLine(s, members, flagship)) {
                assigned.put(s.id, SquadObjective.RESERVE);
                assignedShips.put(s, Boolean.TRUE);
                reserveAssigned++;
                continue;
            }
            if (isPointDefenseRole(s) && isAlive(threatenedAlly)) {
                assigned.put(s.id, SquadObjective.INTERCEPT);
                assignedShips.put(s, Boolean.TRUE);
                interceptAssigned++;
            }
        }

        while (reserveAssigned < reserveQuota) {
            Ship pick = pickBestUnassigned(pool, assignedShips, s -> reserveScore(s, members, flagship));
            if (pick == null) break;
            assigned.put(pick.id, SquadObjective.RESERVE);
            assignedShips.put(pick, Boolean.TRUE);
            reserveAssigned++;
        }
        while (interceptAssigned < interceptQuota) {
            Ship pick = pickBestUnassigned(pool, assignedShips, s -> interceptScore(s, threatenedAlly));
            if (pick == null) break;
            assigned.put(pick.id, SquadObjective.INTERCEPT);
            assignedShips.put(pick, Boolean.TRUE);
            interceptAssigned++;
        }
        while (flankAssigned < flankQuota) {
            Ship pick = pickBestUnassigned(pool, assignedShips, s -> flankScore(s, sharedTarget));
            if (pick == null) break;
            assigned.put(pick.id, SquadObjective.FLANK);
            assignedShips.put(pick, Boolean.TRUE);
            flankAssigned++;
        }

        for (Ship s : pool) {
            SquadObjective objective = assigned.getOrDefault(s.id, SquadObjective.HOLD);
            state.squadObjectives.put(s.id, objective);
        }
    }

    private static void assignSquadronIdentities(FleetState state, int teamId, List<Ship> members, Ship flagship) {
        if (state == null || members == null || members.isEmpty()) return;
        int nextIndex = 1;
        if (isAlive(flagship)) {
            registerSquadronChunk(state, nextIndex++, "COMMAND", "Command", flagship);
        }

        List<Ship> intercept = collectObjectiveMembers(state, members, flagship, SquadObjective.INTERCEPT);
        List<Ship> flank = collectObjectiveMembers(state, members, flagship, SquadObjective.FLANK);
        List<Ship> hold = collectObjectiveMembers(state, members, flagship, SquadObjective.HOLD);
        List<Ship> reserve = collectObjectiveMembers(state, members, flagship, SquadObjective.RESERVE);

        nextIndex = assignSquadronChunks(state, intercept, "PICKET", "Intercept", nextIndex, 3);
        nextIndex = assignSquadronChunks(state, flank, "LANCE", "Flank", nextIndex, 3);
        nextIndex = assignSquadronChunks(state, hold, "SPEAR", "Battleline", nextIndex, 4);
        assignSquadronChunks(state, reserve, "RESERVE", "Reserve", nextIndex, 4);
    }

    private static List<Ship> collectObjectiveMembers(FleetState state, List<Ship> members, Ship flagship, SquadObjective objective) {
        List<Ship> out = new ArrayList<>();
        if (state == null || members == null || objective == null) return out;
        for (Ship s : members) {
            if (!isAlive(s) || s == flagship) continue;
            if (state.squadObjectives.getOrDefault(s.id, SquadObjective.HOLD) != objective) continue;
            out.add(s);
        }
        return out;
    }

    private static int assignSquadronChunks(FleetState state, List<Ship> ships, String prefix, String role, int nextIndex, int chunkSize) {
        if (state == null || ships == null || ships.isEmpty()) return nextIndex;
        int safeChunkSize = Math.max(1, chunkSize);
        for (int start = 0; start < ships.size(); start += safeChunkSize) {
            int end = Math.min(ships.size(), start + safeChunkSize);
            String label = prefix + " " + nextIndex;
            Ship[] chunk = new Ship[end - start];
            for (int i = start; i < end; i++) {
                chunk[i - start] = ships.get(i);
            }
            registerSquadronChunk(state, nextIndex, label, role, chunk);
            nextIndex++;
        }
        return nextIndex;
    }

    private static void registerSquadronChunk(FleetState state, int squadIndex, String label, String role, Ship... chunk) {
        if (state == null || chunk == null || chunk.length <= 0) return;
        Ship leader = null;
        for (Ship s : chunk) {
            if (!isAlive(s)) continue;
            if (leader == null) leader = s;
        }
        if (!isAlive(leader)) return;
        for (Ship s : chunk) {
            if (!isAlive(s)) continue;
            state.squadLabels.put(s.id, label);
            state.squadRoles.put(s.id, role);
            state.squadLeaders.put(s.id, leader.id);
            state.squadIndexes.put(s.id, squadIndex);
        }
    }

    private interface ShipScore {
        double score(Ship s);
    }

    private static Ship pickBestUnassigned(List<Ship> pool, IdentityHashMap<Ship, Boolean> assignedShips, ShipScore scoreFn) {
        if (pool == null || pool.isEmpty() || scoreFn == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < pool.size(); i++) {
            Ship s = pool.get(i);
            if (!isAlive(s)) continue;
            if (assignedShips.containsKey(s)) continue;
            double score = scoreFn.score(s);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static double reserveScore(Ship s, List<Ship> members, Ship flagship) {
        if (s == null) return Double.NEGATIVE_INFINITY;
        double hf = hullFrac(s);
        double sf = shieldFrac(s);
        double score = (1.0 - hf) * 2.2 + (1.0 - sf) * 1.7;
        if (isSupportRole(s.role)) score += 1.6;
        if (isCapitalRole(s.role) && shouldRotateCapitalBehindLine(s, members, flagship)) score += 1.2;
        return score;
    }

    private static double interceptScore(Ship s, Ship threatenedAlly) {
        if (s == null) return Double.NEGATIVE_INFINITY;
        double score = 0.0;
        if (isPointDefenseRole(s)) score += 3.1;
        if (isSkirmishRole(s.role)) score += 1.2;
        if (isSupportRole(s.role)) score -= 1.6;
        score += Math.min(1.2, Math.max(0.0, s.desiredSpeed / 220.0));
        if (isAlive(threatenedAlly)) score += 0.6;
        score += hullFrac(s) * 0.4 + shieldFrac(s) * 0.3;
        return score;
    }

    private static double flankScore(Ship s, Ship sharedTarget) {
        if (s == null) return Double.NEGATIVE_INFINITY;
        double score = 0.0;
        if (isSkirmishRole(s.role)) score += 2.2;
        if (s.role == ShipRole.STEALTH_SHIP || s.role == ShipRole.BOMBER) score += 0.7;
        if (isPointDefenseRole(s)) score -= 0.5;
        if (isSupportRole(s.role)) score -= 1.8;
        if (!isAlive(sharedTarget)) score -= 0.5;
        score += hullFrac(s) * 0.5 + shieldFrac(s) * 0.4;
        return score;
    }

    private static boolean shouldRotateCapitalBehindLine(Ship capital, List<Ship> members, Ship flagship) {
        if (!isAlive(capital) || !isCapitalRole(capital.role)) return false;
        if (!isAlive(flagship) || flagship == capital) return false;
        double selfDur = hullFrac(capital) * 0.62 + shieldFrac(capital) * 0.38;
        if (selfDur > 0.70) return false;
        double bestPeer = -1.0;
        for (Ship other : members) {
            if (!isAlive(other) || other == capital) continue;
            if (!isCapitalRole(other.role)) continue;
            double peerDur = hullFrac(other) * 0.62 + shieldFrac(other) * 0.38;
            if (peerDur > bestPeer) bestPeer = peerDur;
        }
        return bestPeer >= selfDur + 0.14;
    }

    private static void updateTeamDangerMemory(GameContext ctx, FleetState state, double dt) {
        if (state == null) return;
        for (Map.Entry<Integer, List<Ship>> e : state.members.entrySet()) {
            int teamId = e.getKey();
            List<Ship> members = e.getValue();
            DangerMemory memory = TEAM_DANGER_MEMORY.computeIfAbsent(teamId, k -> new DangerMemory());
            if (memory.ttl > 0.0) memory.ttl = Math.max(0.0, memory.ttl - dt);

            DangerMemory sample = sampleTeamDanger(ctx, members);
            if (sample != null && sample.intensity > 0.0) {
                double blend = (memory.ttl > 0.0) ? 0.62 : 0.0;
                memory.x = memory.x * blend + sample.x * (1.0 - blend);
                memory.y = memory.y * blend + sample.y * (1.0 - blend);
                memory.intensity = Math.max(sample.intensity, memory.intensity * 0.70);
                memory.ttl = Math.max(memory.ttl, 2.8);
            } else if (memory.ttl <= 0.0) {
                memory.intensity = 0.0;
            }

            if (memory.ttl > 0.0 && memory.intensity > 0.05) {
                DangerMemory snapshot = new DangerMemory();
                snapshot.x = memory.x;
                snapshot.y = memory.y;
                snapshot.intensity = memory.intensity;
                snapshot.ttl = memory.ttl;
                state.dangerMemory.put(teamId, snapshot);
            }
        }
    }

    private static DangerMemory sampleTeamDanger(GameContext ctx, List<Ship> members) {
        if (ctx == null || members == null || members.isEmpty()) return null;
        double cx = 0.0;
        double cy = 0.0;
        int n = 0;
        Faction teamFaction = null;
        for (Ship s : members) {
            if (!isAlive(s)) continue;
            cx += s.x;
            cy += s.y;
            n++;
            if (teamFaction == null) teamFaction = s.faction;
        }
        if (n <= 0 || teamFaction == null) return null;
        cx /= n;
        cy /= n;

        double wx = 0.0;
        double wy = 0.0;
        double weight = 0.0;

        if (ctx.projectiles != null) {
            for (Projectile p : ctx.projectiles) {
                if (p == null || !p.alive) continue;
                if (p.faction == null || teamFaction.isFriendlyTo(p.faction)) continue;
                double d = Math.hypot(p.x - cx, p.y - cy);
                if (d > 920.0) continue;
                double w = Math.max(0.05, 1.0 - d / 920.0);
                wx += p.x * w;
                wy += p.y * w;
                weight += w;

                // Forecast likely impact corridors a short time ahead.
                double forecastTicks = Math.min(28.0, Math.max(10.0, 0.35 * 60.0));
                double fx = p.x + p.vx * forecastTicks;
                double fy = p.y + p.vy * forecastTicks;
                double fd = Math.hypot(fx - cx, fy - cy);
                if (fd <= 980.0) {
                    double wf = Math.max(0.04, 1.0 - fd / 980.0);
                    if (p instanceof Missile) wf *= 1.25;
                    wx += fx * wf;
                    wy += fy * wf;
                    weight += wf;
                }
            }
        }

        if (ctx.asteroids != null) {
            for (Asteroid a : ctx.asteroids) {
                if (a == null) continue;
                double d = Math.hypot(a.x - cx, a.y - cy);
                if (d > 680.0) continue;
                double w = (a.collisionRadius() / 90.0) * Math.max(0.0, 1.0 - d / 680.0);
                if (w <= 0.01) continue;
                wx += a.x * w;
                wy += a.y * w;
                weight += w;
            }
        }

        if (weight <= 0.01) return null;
        DangerMemory sample = new DangerMemory();
        sample.x = wx / weight;
        sample.y = wy / weight;
        sample.intensity = Math.max(0.0, Math.min(1.0, weight / 8.0));
        sample.ttl = 2.8;
        return sample;
    }

    private static void refreshTeamFormationPlans(GameContext ctx, FleetState state) {
        if (state == null) return;
        for (Map.Entry<Integer, List<Ship>> e : state.members.entrySet()) {
            int teamId = e.getKey();
            Ship flagship = state.flagships.get(teamId);
            Ship target = state.sharedTargets.get(teamId);
            DangerMemory danger = state.dangerMemory.get(teamId);
            GameContext.FleetFormation formation = GameContext.FleetFormation.WEDGE;
            double spacingMul = 1.0;

            if (isAlive(flagship)) {
                double asteroidDensity = localAsteroidDensity(ctx, flagship.x, flagship.y, 660.0);
                double targetDist = isAlive(target)
                        ? Math.hypot(target.x - flagship.x, target.y - flagship.y)
                        : Double.POSITIVE_INFINITY;
                double dangerIntensity = (danger == null) ? 0.0 : danger.intensity;

                if (dangerIntensity > 0.46 || asteroidDensity > 0.48) {
                    formation = GameContext.FleetFormation.SCREEN;
                } else if (isAlive(target) && targetDist > 760.0) {
                    formation = GameContext.FleetFormation.LINE;
                } else if (isAlive(target) && targetDist < 420.0) {
                    formation = GameContext.FleetFormation.SCREEN;
                } else {
                    formation = GameContext.FleetFormation.WEDGE;
                }

                spacingMul += asteroidDensity * 0.34 + dangerIntensity * 0.28;
                if (formation == GameContext.FleetFormation.LINE) spacingMul += 0.08;
                if (formation == GameContext.FleetFormation.SCREEN) spacingMul += 0.16;
                if (isAlive(target) && targetDist < 380.0) spacingMul -= 0.12;
                spacingMul = Math.max(0.85, Math.min(1.65, spacingMul));
            }

            state.autoFormation.put(teamId, formation);
            state.autoFormationSpacing.put(teamId, spacingMul);
        }
    }

    private static double localAsteroidDensity(GameContext ctx, double x, double y, double radius) {
        if (ctx == null || ctx.asteroids == null || ctx.asteroids.isEmpty() || radius <= 0.0) return 0.0;
        double area = Math.PI * radius * radius;
        double occupied = 0.0;
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            double d = Math.hypot(a.x - x, a.y - y);
            if (d > radius + a.collisionRadius()) continue;
            double rr = a.collisionRadius();
            occupied += Math.PI * rr * rr;
        }
        return Math.max(0.0, Math.min(1.0, occupied / Math.max(1.0, area * 0.72)));
    }

    private static boolean hasLiveShipId(List<Ship> ships, Integer id) {
        if (ships == null || id == null) return false;
        for (Ship s : ships) {
            if (s == null) continue;
            if (s.id != id) continue;
            return s.alive && !s.dying && s.hp > 0;
        }
        return false;
    }

    private static int playerFleetGroupKey(GameContext ctx) {
        return (ctx != null && ctx.player != null && ctx.player.faction != null)
                ? ctx.player.faction.teamId()
                : 0;
    }

    private static int fleetGroupKeyForShip(GameContext ctx, Ship ship) {
        if (ship == null || ship.faction == null) return 0;
        int teamId = ship.faction.teamId();
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return teamId;
        if (!CampaignSystem.isCampaignActive(ctx)) return teamId;
        if (ship.minerHomeBase == ctx.player && !isPlayerCommandedFleetFaction(ctx, ship.faction)) {
            ship.minerHomeBase = null;
            ship.escortAnchorId = -1;
            return teamId;
        }
        if (!ship.faction.isFriendlyTo(ctx.player.faction)) return teamId;
        if (ship == ctx.player || (ship.minerHomeBase == ctx.player && isPlayerCommandedFleetFaction(ctx, ship.faction))) {
            return playerFleetGroupKey(ctx);
        }
        int subzone = ship.campaignMissionSubzone;
        int subzoneBucket = subzone >= 0 ? Math.min(999, subzone) : 999;
        return 100_000 + teamId * 1_000 + subzoneBucket;
    }

    private static int fleetGroupKeyForShip(FleetState state, Ship ship) {
        if (state != null && ship != null) {
            Integer key = state.shipGroupKeys.get(ship.id);
            if (key != null) return key;
        }
        return (ship != null && ship.faction != null) ? ship.faction.teamId() : 0;
    }

    private static boolean shouldPublishFleetCommandShip(GameContext ctx, Faction faction, Ship flagship) {
        if (faction == null || flagship == null) return false;
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return true;
        if (!faction.isFriendlyTo(ctx.player.faction)) return true;
        return flagship == ctx.player;
    }

    private static double flagshipScore(Ship s) {
        if (s == null) return 0.0;
        return roleWeightForFlagship(s.role) * 1000.0 + s.hpMax * 3.0 + s.radius * 8.0 + s.id * 0.0001;
    }

    private static boolean canLeadFleetFormation(Ship s) {
        if (!isAlive(s)) return false;
        if (s.isSmallCraft()) return false;
        if (s.role == ShipRole.MINER) return false;
        return s.role != ShipRole.HAULER && s.role != ShipRole.TRANSPORT;
    }

    private static boolean ignoresPlayerFormationOrders(Ship ship) {
        if (ship == null) return false;
        return ship.role == ShipRole.MINER || ship.role == ShipRole.HAULER;
    }

    private static double roleWeightForFlagship(ShipRole role) {
        if (role == null) return 1.0;
        Double titanWeight = titanFlagshipWeight(role);
        if (titanWeight != null) return titanWeight;
        return switch (role) {
            case SUPERSHIP -> 16.0;
            case DREADNOUGHT -> 15.0;
            case BATTLESHIP -> 14.0;
            case BATTLECRUISER -> 13.0;
            case CARRIER, DRONE_CARRIER -> 12.0;
            case MEDIUM_CRUISER, CRUISER -> 11.0;
            case LIGHT_CRUISER -> 10.0;
            case TRANSPORT -> 9.0;
            case MISSILE_BOAT, FRIGATE, CIWS_CORVETTE -> 8.0;
            case PICKET, PATROL, STEALTH_SHIP -> 6.0;
            case HAULER -> 4.5;
            case BOMBER, PD_CRAFT, FIGHTER, DRONE -> 3.0;
            case MINER -> 2.0;
            default -> 1.0;
        };
    }

    private static boolean applyFleetBehavior(GameContext ctx, FleetState state, Ship s, double dt, Ship targetHint) {
        if (ctx == null || state == null || s == null) return false;
        if (s.faction == null) return false;
        int teamId = fleetGroupKeyForShip(state, s);
        Ship flagship = state.flagships.get(teamId);
        if (flagship == null || !flagship.alive || flagship.dying || flagship.hp <= 0) return false;
        if (ignoresPlayerFormationOrders(s)) return false;

        boolean playerDirected = playerCanDirectTeamFleet(ctx, s, flagship);
        GameContext.FleetCommand cmd = resolveFleetCommand(ctx, s, flagship);
        if (cmd == null || cmd == GameContext.FleetCommand.AUTO) return false;
        if (isRepairOrderCommand(cmd)) {
            s.tryInstantRepairFromOrder(REPAIR_ORDER_SAFE_SECONDS);
        }
        applyHazardCommandPosture(s, cmd);

        Ship target = isAlive(targetHint) ? targetHint : null;
        if (!isAlive(target)) {
            long targetStart = System.nanoTime();
            target = selectEngagementTarget(ctx, state, s, dt);
            shipCombatTargetNs += System.nanoTime() - targetStart;
        }
        Ship base = TeamSystem.getBaseForTeam(ctx, s.faction);
        Ship escortAnchor = escortAnchorForCommand(ctx, s, cmd);
        target = constrainTargetForCommand(ctx, s, flagship, base, cmd, target);
        double speed = MovementModel.speedCeiling(s);
        SquadObjective objective = (state.squadObjectives == null)
                ? SquadObjective.HOLD
                : state.squadObjectives.getOrDefault(s.id, SquadObjective.HOLD);
        if (playerDirected) {
            // When player is fleet flagship, explicit command posture should dominate
            // over autonomous reserve/flank role reassignment.
            objective = SquadObjective.HOLD;
        }
        if (cmd == GameContext.FleetCommand.DEFEND
                || cmd == GameContext.FleetCommand.ESCORT
                || cmd == GameContext.FleetCommand.REPAIR
                || cmd == GameContext.FleetCommand.RTB
                || cmd == GameContext.FleetCommand.RETREAT
                || cmd == GameContext.FleetCommand.MINE) {
            objective = SquadObjective.HOLD;
        }
        double teamConfidence = sharedTargetConfidence(state, s);

        if (s == flagship) {
            switch (cmd) {
                case RETREAT, RTB, REPAIR -> {
                    if (base != null) {
                        moveToward(s, base.x, base.y, speed, dt);
                    } else {
                        wander(ctx, s, dt);
                    }
                    if (target != null) {
                        double d = Math.hypot(target.x - s.x, target.y - s.y);
                        if (shouldAttemptFireOnSensorContact(ctx, s, target, d, objective)) {
                            fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                        }
                    }
                    s.tryCIWS(dt, ctx);
                    return true;
                }
                case DEFEND, FORM_UP, ESCORT -> {
                    Ship defendAnchor = (cmd == GameContext.FleetCommand.ESCORT && escortAnchor != null) ? escortAnchor : base;
                    double keep = (defendAnchor == null) ? Math.max(260.0, preferredRange(s) * 0.90)
                            : Math.max(260.0, defendAnchor.radius + 150.0);
                    double defendPerimeter = (defendAnchor == null) ? Math.max(780.0, preferredRange(s) * 1.8)
                            : Math.max(860.0, defendAnchor.radius + 560.0);
                    boolean targetInPerimeter = target != null
                            && (defendAnchor == null
                            ? Math.hypot(target.x - s.x, target.y - s.y) <= defendPerimeter
                            : Math.hypot(target.x - defendAnchor.x, target.y - defendAnchor.y) <= defendPerimeter);
                    if (target != null && target.alive && !target.dying && targetInPerimeter) {
                        double d = Math.hypot(target.x - s.x, target.y - s.y);
                        if (d > preferredRange(s) * 1.12) {
                            moveToward(s, target.x, target.y, speed * 1.00, dt);
                        } else {
                            fight(ctx, s, target, dt, teamConfidence, SquadObjective.HOLD);
                        }
                    } else if (defendAnchor != null) {
                        if (cmd == GameContext.FleetCommand.ESCORT && maybeStartEscortAnchorWarp(ctx, s, defendAnchor)) {
                            s.tryCIWS(dt, ctx);
                            return true;
                        }
                        orbit(s, defendAnchor.x, defendAnchor.y, keep, speed * 0.84, dt, ((s.id & 1) == 0) ? 1.0 : -1.0);
                    } else {
                        wander(ctx, s, dt);
                    }
                    s.tryCIWS(dt, ctx);
                    return true;
                }
                case MINE -> {
                    Asteroid ast = EconomySystem.findBestAsteroidNear(ctx, s.x, s.y, 2400.0);
                    if (ast != null) moveToward(s, ast.x, ast.y, speed * 0.9, dt);
                    else if (base != null) moveToward(s, base.x, base.y, speed * 0.8, dt);
                    if (target != null) {
                        double d = Math.hypot(target.x - s.x, target.y - s.y);
                        if (shouldAttemptFireOnSensorContact(ctx, s, target, d, objective)) {
                            fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                        }
                    }
                    s.tryCIWS(dt, ctx);
                    return true;
                }
                default -> {
                    // ATTACK falls back to regular AI.
                    return false;
                }
            }
        }

        List<Ship> members = state.members.get(teamId);
        GameContext.FleetFormation desiredFormation = playerDirected
                ? ctx.command.alliedFleetFormation
                : state.autoFormation.getOrDefault(teamId, GameContext.FleetFormation.WEDGE);
        double spacingMul = state.autoFormationSpacing.getOrDefault(teamId, 1.0);
        Ship commandAnchor = (cmd == GameContext.FleetCommand.ESCORT && escortAnchor != null) ? escortAnchor : flagship;
        if (usesEnemyFacingFormation(desiredFormation)) {
            Ship assaultContact = assaultFormationContact(ctx, state, s, commandAnchor, target);
            if (isAlive(assaultContact)) {
                target = assaultContact;
            }
        }
        int slot = formationSlotIndex(members, flagship, s);
        int wingCount = formationWingCount(members, flagship);
        double formationSpacing = preferredRange(s) * 0.35 * spacingMul;
        double[] anchor = usesEnemyFacingFormation(desiredFormation)
                ? assaultFormationAnchor(commandAnchor, members, flagship, s, formationSpacing, cmd, target, desiredFormation)
                : formationAnchor(commandAnchor, slot, wingCount, s.radius, formationSpacing, desiredFormation, cmd);
        Ship threatFocus = state.missileThreatFocus.get(teamId);
        if (isPointDefenseRole(s) && isAlive(threatFocus) && threatFocus != s) {
            anchor[0] = anchor[0] * 0.48 + threatFocus.x * 0.52;
            anchor[1] = anchor[1] * 0.48 + threatFocus.y * 0.52;
        }
        if (objective == SquadObjective.FLANK && isAlive(target)) {
            double dx = target.x - flagship.x;
            double dy = target.y - flagship.y;
            double dl = Math.hypot(dx, dy) + 1e-9;
            double ux = dx / dl;
            double uy = dy / dl;
            double side = ((slot & 1) == 0) ? -1.0 : 1.0;
            double tx = -uy * side;
            double ty = ux * side;
            double flankRange = Math.max(220.0, preferredRange(s) * 0.90);
            anchor[0] = target.x - ux * flankRange + tx * flankRange * 0.85;
            anchor[1] = target.y - uy * flankRange + ty * flankRange * 0.85;
        } else if (objective == SquadObjective.RESERVE) {
            double bx = -Math.cos(flagship.angle);
            double by = -Math.sin(flagship.angle);
            double back = Math.max(240.0, preferredRange(s) * 0.85 + slot * 24.0);
            anchor[0] = flagship.x + bx * back;
            anchor[1] = flagship.y + by * back;
        } else if (objective == SquadObjective.INTERCEPT && isAlive(threatFocus)) {
            anchor[0] = anchor[0] * 0.44 + threatFocus.x * 0.56;
            anchor[1] = anchor[1] * 0.44 + threatFocus.y * 0.56;
        }
        Integer squadLeaderId = state.squadLeaders.get(s.id);
        if (squadLeaderId != null && squadLeaderId != s.id) {
            Ship squadLeader = findShipById(ctx.ships, squadLeaderId);
            if (isAlive(squadLeader) && squadLeader.faction != null && s.faction != null
                    && fleetGroupKeyForShip(state, squadLeader) == fleetGroupKeyForShip(state, s)) {
                double squadPull = switch (objective) {
                    case INTERCEPT -> 0.58;
                    case FLANK -> 0.64;
                    case RESERVE -> 0.42;
                    case HOLD -> 0.48;
                };
                anchor[0] = anchor[0] * (1.0 - squadPull) + squadLeader.x * squadPull;
                anchor[1] = anchor[1] * (1.0 - squadPull) + squadLeader.y * squadPull;
            }
        }
        DangerMemory danger = state.dangerMemory.get(teamId);
        if (danger != null && danger.ttl > 0.0 && danger.intensity > 0.05) {
            double dx = s.x - danger.x;
            double dy = s.y - danger.y;
            double len = Math.hypot(dx, dy) + 1e-9;
            double repel = Math.max(0.0, 640.0 - len) / 640.0;
            if (repel > 0.0) {
                double shift = 220.0 * danger.intensity * repel;
                anchor[0] += (dx / len) * shift;
                anchor[1] += (dy / len) * shift;
            }
        }
        double coherence = battlelineCoherenceScore(members, flagship, s, target);
        if (coherence < -0.18) {
            // Penalize isolated outrunners by pulling them back into coherent battle-line spacing.
            anchor[0] = anchor[0] * 0.64 + flagship.x * 0.36;
            anchor[1] = anchor[1] * 0.64 + flagship.y * 0.36;
        } else if (coherence > 0.32 && objective == SquadObjective.HOLD && isAlive(target)) {
            // Reward coherent line behavior with slight forward commitment.
            anchor[0] = anchor[0] * 0.82 + target.x * 0.18;
            anchor[1] = anchor[1] * 0.82 + target.y * 0.18;
        }
        double coherenceSpeedMul = (coherence < -0.18) ? 0.86 : (coherence > 0.32 ? 1.04 : 1.0);
        if (cmd == GameContext.FleetCommand.ESCORT && escortAnchor != null && maybeStartEscortAnchorWarp(ctx, s, escortAnchor)) {
            s.tryCIWS(dt, ctx);
            return true;
        }
        if (maybeStartFleetRejoinWarp(ctx, s, flagship, anchor[0], anchor[1], cmd, objective, target)) {
            s.tryCIWS(dt, ctx);
            return true;
        }
        boolean assaultConfrontation = desiredFormation == GameContext.FleetFormation.ASSAULT
                && isAlive(target)
                && shouldAssaultShipSeekConfrontation(s);
        boolean offensiveConfrontation = desiredFormation == GameContext.FleetFormation.OFFENSIVE
                && isAlive(target)
                && shouldOffensiveShipSeekConfrontation(s);

        switch (cmd) {
            case RETREAT, RTB, REPAIR -> {
                if (base != null) {
                    moveToward(s, base.x, base.y, speed, dt);
                } else {
                    moveToward(s, flagship.x, flagship.y, speed * 0.9, dt);
                }
                if (target != null) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (shouldAttemptFireOnSensorContact(ctx, s, target, d, objective)) {
                        fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                    }
                }
            }
            case DEFEND, FORM_UP, ESCORT -> {
                double ad = Math.hypot(anchor[0] - s.x, anchor[1] - s.y);
                if ((assaultConfrontation || offensiveConfrontation) && ad <= Math.max(120.0, s.radius * 4.0)) {
                    s.aiForcedEngageTimer = Math.max(s.aiForcedEngageTimer, 0.75);
                    fight(ctx, s, target, dt, teamConfidence, SquadObjective.INTERCEPT);
                } else if (ad > Math.max(70.0, s.radius * 2.5)) {
                    double spdMul = (objective == SquadObjective.INTERCEPT) ? 1.06 : (objective == SquadObjective.RESERVE ? 0.82 : 0.92);
                    double assaultMul = (assaultConfrontation || offensiveConfrontation) ? 1.12 : 1.0;
                    moveToward(s, anchor[0], anchor[1], speed * spdMul * assaultMul * coherenceSpeedMul, dt);
                } else {
                    Ship followAnchor = (commandAnchor == null) ? flagship : commandAnchor;
                    setVelPerSec(s, followAnchor.vx / Math.max(1e-9, dt), followAnchor.vy / Math.max(1e-9, dt), dt);
                    rotateShipToward(s, followAnchor.angle, dt);
                }
                if (target != null) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (shouldAttemptFireOnSensorContact(ctx, s, target, d, objective)) {
                        fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                    }
                }
            }
            case MINE -> {
                if (s.role == ShipRole.HAULER || s.role == ShipRole.TRANSPORT) {
                    Asteroid ast = EconomySystem.findBestAsteroidNear(ctx, s.x, s.y, 2000.0);
                    if (ast != null) moveToward(s, ast.x, ast.y, speed * 0.85 * coherenceSpeedMul, dt);
                    else moveToward(s, anchor[0], anchor[1], speed * 0.85 * coherenceSpeedMul, dt);
                } else {
                    moveToward(s, anchor[0], anchor[1], speed * 0.9 * coherenceSpeedMul, dt);
                }
                if (target != null) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (shouldAttemptFireOnSensorContact(ctx, s, target, d, objective)) {
                        fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                    }
                }
            }
            case ATTACK -> {
                if (assaultConfrontation || offensiveConfrontation) {
                    s.aiForcedEngageTimer = Math.max(s.aiForcedEngageTimer, 0.75);
                    double ad = Math.hypot(anchor[0] - s.x, anchor[1] - s.y);
                    if (ad > Math.max(150.0, s.radius * 4.8)) {
                        moveToward(s, anchor[0], anchor[1], speed * 1.10 * coherenceSpeedMul, dt);
                    } else {
                        fight(ctx, s, target, dt, teamConfidence, SquadObjective.INTERCEPT);
                    }
                } else if (playerDirected) {
                    double ad = Math.hypot(anchor[0] - s.x, anchor[1] - s.y);
                    double anchorHold = Math.max(120.0, s.radius * 3.8);
                    if (ad > anchorHold) {
                        moveToward(s, anchor[0], anchor[1], speed * 1.04 * coherenceSpeedMul, dt);
                    } else if (target != null && target.alive && !target.dying) {
                        fight(ctx, s, target, dt, teamConfidence, SquadObjective.HOLD);
                    } else {
                        moveToward(s, anchor[0], anchor[1], speed * 0.92 * coherenceSpeedMul, dt);
                    }
                } else if (target != null && target.alive && !target.dying && objective != SquadObjective.RESERVE) {
                    fight(ctx, s, target, dt, teamConfidence, objective);
                } else {
                    double spdMul = (objective == SquadObjective.INTERCEPT) ? 1.08 : (objective == SquadObjective.RESERVE ? 0.80 : 0.96);
                    moveToward(s, anchor[0], anchor[1], speed * spdMul * coherenceSpeedMul, dt);
                }
            }
            default -> {
                return false;
            }
        }

        s.tryCIWS(dt, ctx);
        return true;
    }

    private static Ship constrainTargetForCommand(GameContext ctx, Ship self, Ship flagship, Ship base,
                                                  GameContext.FleetCommand cmd, Ship target) {
        if (cmd == null || !isAlive(target) || self == null) return null;
        if (cmd == GameContext.FleetCommand.ATTACK) return target;

        double dSelf = Math.hypot(target.x - self.x, target.y - self.y);
        double cx = (base != null) ? base.x : ((flagship != null) ? flagship.x : self.x);
        double cy = (base != null) ? base.y : ((flagship != null) ? flagship.y : self.y);
        double dAnchor = Math.hypot(target.x - cx, target.y - cy);

        return switch (cmd) {
            case DEFEND, ESCORT -> (dAnchor <= Math.max(840.0, ((base == null) ? 520.0 : base.radius + 560.0))
                    || dSelf <= 560.0) ? target : null;
            case FORM_UP -> (dSelf <= 1300.0) ? target : null;
            case REPAIR, RTB, RETREAT, MINE -> (dSelf <= 420.0) ? target : null;
            default -> target;
        };
    }

    private static boolean isRepairOrderCommand(GameContext.FleetCommand cmd) {
        if (cmd == null) return false;
        return cmd == GameContext.FleetCommand.REPAIR
                || cmd == GameContext.FleetCommand.RTB
                || cmd == GameContext.FleetCommand.RETREAT;
    }

    private static void applyHazardCommandPosture(Ship ship, GameContext.FleetCommand cmd) {
        if (ship == null || cmd == null) return;
        double fireLoad = ship.totalFireIntensity();
        int fireRooms = ship.activeFireRoomCount();

        if (isRepairOrderCommand(cmd) || fireRooms >= 2 || fireLoad >= 1.9) {
            ship.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
            ship.setPowerPreset(Ship.PowerPreset.DEFENSE);
            ship.setEngineeringPriority(Ship.EngineeringPriority.REACTOR);
            ship.setOverloadMode(false);
            return;
        }

        if (cmd == GameContext.FleetCommand.ATTACK) {
            ship.crewOrder = Ship.CrewOrder.GUNNERY;
            ship.setPowerPreset(Ship.PowerPreset.ATTACK);
        } else if (cmd == GameContext.FleetCommand.DEFEND || cmd == GameContext.FleetCommand.ESCORT) {
            ship.crewOrder = Ship.CrewOrder.ENGINEERING;
            ship.setPowerPreset(Ship.PowerPreset.DEFENSE);
        }
    }

    private static GameContext.FleetCommand resolveFleetCommand(GameContext ctx, Ship ship, Ship flagship) {
        if (ctx == null || ship == null) return GameContext.FleetCommand.AUTO;
        if (ship.aiForcedEngageTimer > 0.0) {
            return isSupportRole(ship.role) ? GameContext.FleetCommand.DEFEND : GameContext.FleetCommand.ATTACK;
        }
        GameContext.FleetCommand override = ctx.command.shipFleetCommandOverrides.get(ship.id);
        if (override != null && override != GameContext.FleetCommand.AUTO) return override;

        if (playerCanDirectTeamFleet(ctx, ship, flagship)
                && ship != ctx.player
                && ctx.command.alliedFleetCommand != null
                && ctx.command.alliedFleetCommand != GameContext.FleetCommand.AUTO) {
            return ctx.command.alliedFleetCommand;
        }

        double hpFrac = hullFrac(ship);
        double shFrac = shieldFrac(ship);
        double fireLoad = ship.totalFireIntensity();
        int fireRooms = ship.activeFireRoomCount();
        GameContext.FleetCommand personal = null;
        if (fireRooms >= 3 || fireLoad >= 3.4) personal = GameContext.FleetCommand.RETREAT;
        else if (fireRooms >= 2 || fireLoad >= 2.1) personal = GameContext.FleetCommand.REPAIR;
        else if (hpFrac < 0.38 || shFrac < 0.22) personal = GameContext.FleetCommand.RETREAT;
        else if (ship.role == ShipRole.MINER) personal = GameContext.FleetCommand.MINE;

        if (personal != null) {
            if (ship != flagship && shouldHoldWithFlagship(ctx, ship, flagship, personal)) {
                return GameContext.FleetCommand.FORM_UP;
            }
            return personal;
        }
        if (flagship != null) {
            double fhp = hullFrac(flagship);
            double fsh = shieldFrac(flagship);
            if (fhp < 0.34 || fsh < 0.20) {
                if (ship != flagship && !flagshipHasExplicitRtbOrder(ctx, flagship)) {
                    return GameContext.FleetCommand.FORM_UP;
                }
                return GameContext.FleetCommand.RETREAT;
            }
            Ship threat = (ctx.command.fleetSharedTargets == null || flagship.faction == null)
                    ? null
                    : ctx.command.fleetSharedTargets.get(flagship.faction);
            if (!isAlive(threat)) threat = TargetingSystem.getPreferredEnemyTarget(ctx, flagship);
            if (threat != null) {
                double td = Math.hypot(threat.x - flagship.x, threat.y - flagship.y);
                if (td <= 2200.0) {
                    double targetHull = hullFrac(threat);
                    if (hpFrac > 0.52 && shFrac > 0.28) return GameContext.FleetCommand.ATTACK;
                    if (targetHull < 0.58) return GameContext.FleetCommand.ATTACK;
                    if (td > 1400.0) return GameContext.FleetCommand.FORM_UP;
                    return GameContext.FleetCommand.DEFEND;
                }
            }
            if (ship == flagship && hpFrac > 0.66 && shFrac > 0.40) return GameContext.FleetCommand.ATTACK;
            if (ship != flagship && hpFrac > 0.55 && shFrac > 0.30) return GameContext.FleetCommand.FORM_UP;
        }
        if (!isSupportRole(ship.role) && hpFrac > 0.64 && shFrac > 0.34) return GameContext.FleetCommand.ATTACK;
        return GameContext.FleetCommand.DEFEND;
    }

    private static boolean shouldHoldWithFlagship(GameContext ctx,
                                                  Ship ship,
                                                  Ship flagship,
                                                  GameContext.FleetCommand personal) {
        if (ctx == null || ship == null || flagship == null) return false;
        if (ship == flagship) return false;
        if (!isAlive(flagship)) return false;
        if (personal != GameContext.FleetCommand.REPAIR && personal != GameContext.FleetCommand.RETREAT) return false;
        return !flagshipHasExplicitRtbOrder(ctx, flagship);
    }

    private static boolean flagshipHasExplicitRtbOrder(GameContext ctx, Ship flagship) {
        if (ctx == null || flagship == null) return false;
        GameContext.FleetCommand override = ctx.command.shipFleetCommandOverrides.get(flagship.id);
        if (override == GameContext.FleetCommand.RTB) return true;
        return flagship == ctx.player && ctx.command.alliedFleetCommand == GameContext.FleetCommand.RTB;
    }

    private static Ship escortAnchorForCommand(GameContext ctx, Ship ship, GameContext.FleetCommand cmd) {
        if (ctx == null || ship == null || cmd != GameContext.FleetCommand.ESCORT) return null;
        if (ship.escortAnchorId <= 0) return null;
        Ship anchor = findLiveShipById(ctx.ships, ship.escortAnchorId);
        if (!isAlive(anchor)) {
            ship.escortAnchorId = -1;
            return null;
        }
        return anchor;
    }

    private static Ship sharedTargetForTeam(FleetState state, Ship s) {
        if (state == null || s == null || s.faction == null) return null;
        return state.sharedTargets.get(fleetGroupKeyForShip(state, s));
    }

    private static Ship focusTargetForShip(FleetState state, Ship s) {
        if (state == null || s == null || s.faction == null) return null;
        int teamId = fleetGroupKeyForShip(state, s);
        List<Ship> focus = state.focusTargets.get(teamId);
        if (focus != null && !focus.isEmpty()) {
            int squadIndex = state.squadIndexes.getOrDefault(s.id, 0);
            Ship target = CombatTargeting.focusTargetForSquad(focus, squadIndex);
            if (isAlive(target)) return target;
        }
        return state.sharedTargets.get(teamId);
    }

    private static Ship selectEngagementTarget(GameContext ctx, FleetState state, Ship seeker, double dt) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;
        Ship fighterIntercept = aggressiveFighterInterceptTarget(ctx, seeker);
        if (isAlive(fighterIntercept) && TargetingSystem.isDetectableToObserver(seeker, fighterIntercept)) {
            clearEngagementScanBackoff(seeker);
            commitToTarget(seeker, fighterIntercept, targetCommitDuration(seeker, fighterIntercept, SquadObjective.INTERCEPT));
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, fighterIntercept);
        }
        Ship shared = sharedTargetForTeam(state, seeker);
        if (!isAlive(shared)) shared = focusTargetForShip(state, seeker);
        Ship immediate = scanImmediateThreatWithBackoff(
                ctx, seeker, Math.max(0.0, dt), immediateThreatResponseRange(seeker));
        if (isAlive(immediate)) {
            clearEngagementScanBackoff(seeker);
            commitToTarget(seeker, immediate, targetCommitDuration(seeker, immediate, SquadObjective.INTERCEPT));
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, immediate);
        }

        if (shouldCommitToSharedTarget(state, seeker, shared)
                && canShipThreatenTarget(ctx, seeker, shared)) {
            clearEngagementScanBackoff(seeker);
            commitToTarget(seeker, shared, targetCommitDuration(seeker, shared, SquadObjective.HOLD));
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, shared);
        }

        Ship cached = cachedIntentTarget(ctx, state, seeker, shared);
        if (isAlive(cached)) return cached;

        Ship committed = committedTarget(ctx, seeker);
        Ship preferred = null;
        if (shouldMaintainCommittedTarget(ctx, state, seeker, committed, shared, preferred)) {
            clearEngagementScanBackoff(seeker);
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, committed);
        }

        Ship periodic = periodicClosestRetargetTarget(ctx, seeker);
        if (isAlive(periodic)
                && canShipThreatenTarget(ctx, seeker, periodic)
                && canTakeFightForTargetSelection(ctx, seeker, periodic)) {
            clearEngagementScanBackoff(seeker);
            commitToTarget(seeker, periodic, targetCommitDuration(seeker, periodic, SquadObjective.HOLD));
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, periodic);
        }

        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null && !scalePlan.shouldRunFullDecisionScan(seeker)) {
            if (isAlive(committed)) return committed;
            if (isAlive(periodic)) return periodic;
            if (isAlive(shared)) return shared;
            return null;
        }

        if (shouldDeferEngagementScan(seeker, Math.max(0.0, dt))) {
            if (isAlive(committed) && canTakeFightForTargetSelection(ctx, seeker, committed)) {
                return rememberCachedAiIntent(seeker, IntentType.FIGHT, committed);
            }
            if (isAlive(shared) && canTakeFightForTargetSelection(ctx, seeker, shared)) {
                return rememberCachedAiIntent(seeker, IntentType.FIGHT, shared);
            }
            preferred = preferredEnemyTargetCached(ctx, seeker);
            if (isAlive(preferred) && canTakeFightForTargetSelection(ctx, seeker, preferred)) {
                return rememberCachedAiIntent(seeker, IntentType.FIGHT, preferred);
            }
            return null;
        }

        preferred = preferredEnemyTargetCached(ctx, seeker);
        if (isAlive(preferred)
                && canShipThreatenTarget(ctx, seeker, preferred)
                && canTakeFightForTargetSelection(ctx, seeker, preferred)) {
            clearEngagementScanBackoff(seeker);
            commitToTarget(seeker, preferred, targetCommitDuration(seeker, preferred, SquadObjective.HOLD));
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, preferred);
        }

        Ship reachable = findBestReachableEnemyTarget(ctx, state, seeker, shared, preferred);
        if (isAlive(reachable)) {
            clearEngagementScanBackoff(seeker);
            commitToTarget(seeker, reachable, targetCommitDuration(seeker, reachable, SquadObjective.HOLD));
            return rememberCachedAiIntent(seeker, IntentType.FIGHT, reachable);
        }

        if (isAlive(preferred) || isAlive(shared)) {
            armEngagementScanBackoff(ctx, seeker, false);
        } else {
            armEngagementScanBackoff(ctx, seeker, true);
        }

        if (isAlive(preferred)) return rememberCachedAiIntent(seeker, IntentType.FIGHT, preferred);
        if (isAlive(shared)) return rememberCachedAiIntent(seeker, IntentType.FIGHT, shared);
        if (isAlive(committed)) return rememberCachedAiIntent(seeker, IntentType.FIGHT, committed);
        clearCachedAiIntent(seeker, false);
        return null;
    }

    private static Ship scanImmediateThreatWithBackoff(GameContext ctx, Ship seeker, double dt, double radius) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;
        double t = IMMEDIATE_THREAT_SCAN_TIMERS.getOrDefault(seeker.id, 0.0) - dt;
        if (t > 0.0) {
            IMMEDIATE_THREAT_SCAN_TIMERS.put(seeker.id, t);
            return null;
        }
        Ship immediate = immediateThreatCached(ctx, seeker, radius);
        double jitter = (ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
        double baseCadence = isAlive(immediate)
                ? 0.08
                : idleImmediateThreatCadence(seeker.role);
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null) {
            baseCadence *= scalePlan.immediateThreatCadenceMultiplier;
        }
        IMMEDIATE_THREAT_SCAN_TIMERS.put(seeker.id, baseCadence * (0.88 + jitter * 0.42));
        return immediate;
    }

    private static double immediateThreatResponseRange(Ship seeker) {
        if (seeker == null) return 420.0;
        double preferred = preferredRange(seeker);
        double hullBuffer = Math.max(160.0, seeker.radius * 3.4);
        double range = Math.max(420.0, preferred * 1.08 + hullBuffer);
        if (isPointDefenseRole(seeker)) range = Math.max(range, 920.0);
        if (isSupportRole(seeker.role)) range = Math.max(range, preferred * 1.24 + hullBuffer);
        return Math.min(STANDARD_PROSECUTION_RANGE, range);
    }

    private static double idleImmediateThreatCadence(ShipRole role) {
        if (role == null) return 0.22;
        return switch (role) {
            case DRONE, FIGHTER, STEALTH_SHIP, PATROL -> 0.14;
            case FRIGATE, PICKET, CIWS_CORVETTE, MISSILE_BOAT -> 0.18;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 0.22;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP, CARRIER, DRONE_CARRIER -> 0.28;
            case BASE, STATIC_TURRET -> 0.34;
            case MINER, HAULER, TRANSPORT -> 0.30;
            default -> 0.22;
        };
    }

    private static boolean shouldDeferEngagementScan(Ship seeker, double dt) {
        if (seeker == null) return false;
        double t = ENGAGEMENT_SCAN_BACKOFF_TIMERS.getOrDefault(seeker.id, 0.0) - dt;
        if (t > 0.0) {
            ENGAGEMENT_SCAN_BACKOFF_TIMERS.put(seeker.id, t);
            return true;
        }
        ENGAGEMENT_SCAN_BACKOFF_TIMERS.remove(seeker.id);
        return false;
    }

    private static void clearEngagementScanBackoff(Ship seeker) {
        if (seeker == null) return;
        ENGAGEMENT_SCAN_BACKOFF_TIMERS.remove(seeker.id);
    }

    private static void armEngagementScanBackoff(GameContext ctx, Ship seeker, boolean hardMiss) {
        if (seeker == null) return;
        double jitter = (ctx == null || ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
        double base = hardMiss ? hardMissScanBackoff(seeker.role) : softMissScanBackoff(seeker.role);
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null) {
            base *= scalePlan.engagementScanBackoffMultiplier;
        }
        ENGAGEMENT_SCAN_BACKOFF_TIMERS.put(seeker.id, base * (0.85 + jitter * 0.45));
    }

    private static double softMissScanBackoff(ShipRole role) {
        if (role == null) return 0.22;
        return switch (role) {
            case DRONE, FIGHTER, STEALTH_SHIP, PATROL -> 0.14;
            case FRIGATE, PICKET, CIWS_CORVETTE, MISSILE_BOAT -> 0.18;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 0.24;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP, CARRIER, DRONE_CARRIER -> 0.30;
            case BASE, STATIC_TURRET -> 0.34;
            case MINER, HAULER, TRANSPORT -> 0.28;
            default -> 0.22;
        };
    }

    private static double hardMissScanBackoff(ShipRole role) {
        if (role == null) return 0.58;
        return switch (role) {
            case DRONE, FIGHTER, STEALTH_SHIP, PATROL -> 0.30;
            case FRIGATE, PICKET, CIWS_CORVETTE, MISSILE_BOAT -> 0.42;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 0.62;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP, CARRIER, DRONE_CARRIER -> 0.82;
            case BASE, STATIC_TURRET -> 1.05;
            case MINER, HAULER, TRANSPORT -> 0.92;
            default -> 0.58;
        };
    }

    private static Ship findImmediateThreat(GameContext ctx, Ship seeker, double radius) {
        return immediateThreatCached(ctx, seeker, radius);
    }

    private static Ship findImmediateThreatUncached(GameContext ctx, Ship seeker, double radius) {
        if (ctx == null || seeker == null || seeker.faction == null || radius <= 0.0) return null;
        Ship best = null;
        double bestD2 = radius * radius;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(seeker.faction, seeker.x, seeker.y, radius, nearby);
            for (int i = 0; i < nearby.size(); i++) {
                Ship enemy = nearby.get(i);
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!TargetingSystem.isDetectableToObserver(seeker, enemy)) continue;
                double d2 = dist2(seeker.x, seeker.y, enemy.x, enemy.y);
                if (d2 >= bestD2) continue;
                bestD2 = d2;
                best = enemy;
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return best;
    }

    private static Ship findEscortThreatNearCarrier(GameContext ctx, Ship escort, Ship carrier) {
        if (ctx == null || escort == null || carrier == null || escort.faction == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double maxDist = 920.0;
        double maxDist2 = maxDist * maxDist;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(escort.faction, carrier.x, carrier.y, maxDist, nearby);
            for (Ship enemy : nearby) {
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!TargetingSystem.isDetectableToObserver(escort, enemy)) continue;

                double dCarrier2 = dist2(enemy.x, enemy.y, carrier.x, carrier.y);
                if (dCarrier2 > maxDist2) continue;
                double dEscort = Math.hypot(enemy.x - escort.x, enemy.y - escort.y);
                double dCarrier = Math.sqrt(Math.max(0.0, dCarrier2));

                double score = Math.max(0.0, 1000.0 - dCarrier) * 0.9;
                score += Math.max(0.0, 900.0 - dEscort) * 0.5;
                score += threatPriority(escort.role, enemy.role) * 120.0;
                if (isMissileThreatRole(enemy.role)) score += 190.0;
                if (enemy.role == ShipRole.CARRIER || enemy.role == ShipRole.DRONE_CARRIER) score += 80.0;
                if (score > bestScore) {
                    bestScore = score;
                    best = enemy;
                }
            }
        } finally {
            releaseShipScratch(nearby);
        }
        if (isAlive(best)) return best;
        return findImmediateThreat(ctx, escort, Math.max(280.0, preferredRange(escort) * 1.2));
    }

    private static boolean handleEscortFighterBehavior(GameContext ctx, Ship s, double dt) {
        if (ctx == null || s == null || dt <= 0.0) return false;
        if (s.role != ShipRole.FIGHTER) return false;
        if (s.carrierOwnerId >= 0) return false;
        if (s.escortAnchorId <= 0) return false;

        EscortPerception perception = perceiveEscortFighterSituation(ctx, s);
        if (perception == null || !isAlive(perception.anchor)) {
            return false;
        }
        ShipIntent intent = chooseEscortFighterIntent(s, perception);
        executeEscortFighterIntent(ctx, s, dt, intent);
        s.tryCIWS(dt, ctx);
        return true;
    }

    private static EscortPerception perceiveEscortFighterSituation(GameContext ctx, Ship escort) {
        if (ctx == null || escort == null || escort.escortAnchorId <= 0) return null;
        Ship anchor = findLiveShipById(ctx.ships, escort.escortAnchorId);
        if (!isAlive(anchor) || anchor.isSmallCraft()) {
            escort.escortAnchorId = -1;
            return null;
        }
        Ship threat = designatedEscortTarget(ctx, escort, anchor);
        if (!isAlive(threat)) {
            threat = findEscortThreatNearAnchor(ctx, escort, anchor);
        }
        return new EscortPerception(anchor, threat);
    }

    private static ShipIntent chooseEscortFighterIntent(Ship escort, EscortPerception perception) {
        if (escort == null || perception == null || !isAlive(perception.anchor)) {
            return new ShipIntent(IntentType.WANDER, null);
        }
        if (isAlive(perception.threat)) {
            return new ShipIntent(IntentType.FIGHT, perception.threat, perception.anchor);
        }
        return new ShipIntent(IntentType.SCREEN, null, perception.anchor);
    }

    private static void executeEscortFighterIntent(GameContext ctx, Ship escort, double dt, ShipIntent intent) {
        if (ctx == null || escort == null || intent == null) return;
        if (intent.type == IntentType.FIGHT && isAlive(intent.target)) {
            commitToTarget(escort, intent.target, targetCommitDuration(escort, intent.target, SquadObjective.INTERCEPT));
            fight(ctx, escort, intent.target, dt, 1.0, SquadObjective.INTERCEPT);
            return;
        }
        if (intent.type != IntentType.SCREEN || !isAlive(intent.anchor)) {
            wander(ctx, escort, dt);
            return;
        }

        Ship anchor = intent.anchor;
        double speed = Math.max(136.0, MovementModel.speedCeiling(escort) * 0.98);
        double side = ((escort.escortSlotIndex & 1) == 0) ? -1.0 : 1.0;
        double screenForward = anchor.radius + 340.0 + 110.0 * Math.max(0, escort.escortSlotIndex);
        double lateral = anchor.radius + 170.0 + 64.0 * Math.max(0, escort.escortSlotIndex);
        double tx = anchor.x + Math.cos(anchor.angle) * screenForward
                - Math.sin(anchor.angle) * side * lateral;
        double ty = anchor.y + Math.sin(anchor.angle) * screenForward
                + Math.cos(anchor.angle) * side * lateral;
        if (dist2(escort.x, escort.y, anchor.x, anchor.y) > ESCORT_WARP_SUPPORT_RANGE * ESCORT_WARP_SUPPORT_RANGE * 1.2) {
            tx = anchor.x + Math.cos(anchor.angle) * (anchor.radius + 110.0)
                    - Math.sin(anchor.angle) * side * (anchor.radius + 92.0);
            ty = anchor.y + Math.sin(anchor.angle) * (anchor.radius + 110.0)
                    + Math.cos(anchor.angle) * side * (anchor.radius + 92.0);
        }
        moveToward(escort, tx, ty, speed, dt);
    }

    private static Ship findEscortThreatNearAnchor(GameContext ctx, Ship escort, Ship anchor) {
        if (ctx == null || escort == null || anchor == null || escort.faction == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double maxDist = Math.max(1400.0, anchor.radius + 980.0);
        double maxDist2 = maxDist * maxDist;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(escort.faction, anchor.x, anchor.y, maxDist, nearby);
            for (Ship enemy : nearby) {
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!TargetingSystem.isDetectableToObserver(escort, enemy)) continue;

                double dAnchor2 = dist2(enemy.x, enemy.y, anchor.x, anchor.y);
                if (dAnchor2 > maxDist2) continue;

                double dEscort = Math.hypot(enemy.x - escort.x, enemy.y - escort.y);
                double dAnchor = Math.sqrt(Math.max(0.0, dAnchor2));
                double score = Math.max(0.0, 1100.0 - dAnchor) * 0.88;
                score += Math.max(0.0, 800.0 - dEscort) * 0.44;
                score += threatPriority(escort.role, enemy.role) * 120.0;
                if (enemy.role == ShipRole.BOMBER) score += 260.0;
                if (enemy.role == ShipRole.DRONE || enemy.role == ShipRole.FIGHTER) score += 170.0;
                if (enemy.role == ShipRole.CARRIER || enemy.role == ShipRole.DRONE_CARRIER) score += 220.0;
                if (enemy.carrierOwnerId >= 0) score += 90.0;
                if (score > bestScore) {
                    bestScore = score;
                    best = enemy;
                }
            }
        } finally {
            releaseShipScratch(nearby);
        }
        if (isAlive(best)) return best;
        return findImmediateThreat(ctx, escort, Math.max(260.0, preferredRange(escort) * 1.2));
    }

    private static Ship designatedEscortTarget(GameContext ctx, Ship escort, Ship anchor) {
        if (ctx == null || escort == null || anchor == null || escort.faction == null) return null;
        Ship committed = committedTarget(ctx, escort);
        if (isValidEscortTarget(ctx, escort, anchor, committed)) return committed;
        Ship shared = (ctx.command == null || ctx.command.fleetSharedTargets == null) ? null
                : ctx.command.fleetSharedTargets.get(escort.faction);
        if (isValidEscortTarget(ctx, escort, anchor, shared)) return shared;
        if (isValidEscortTarget(ctx, escort, anchor, ctx.lockedTarget)) return ctx.lockedTarget;
        return null;
    }

    private static boolean isValidEscortTarget(GameContext ctx, Ship escort, Ship anchor, Ship target) {
        if (ctx == null || escort == null || anchor == null || !isAlive(target)) return false;
        if (escort.faction == null || target.faction == null) return false;
        if (escort.faction.isFriendlyTo(target.faction)) return false;
        if (!TargetingSystem.isDetectableToObserver(escort, target)) return false;
        double dAnchor = Math.hypot(target.x - anchor.x, target.y - anchor.y);
        return dAnchor <= Math.max(1900.0, anchor.radius + 1320.0);
    }

    private static Ship findBestReachableEnemyTarget(GameContext ctx, FleetState state, Ship seeker, Ship sharedHint, Ship preferredHint) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;
        Ship fighterIntercept = aggressiveFighterInterceptTarget(ctx, seeker);
        if (isAlive(fighterIntercept)
                && TargetingSystem.isDetectableToObserver(seeker, fighterIntercept)) {
            return fighterIntercept;
        }
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        int cap = CombatTargeting.targetCandidateCap(scalePlan);
        Ship[] candidates = new Ship[cap];
        double[] cheapScores = new double[cap];
        int candidateCount = 0;
        double focusBias = factionSharedFocusBias(seeker.faction);
        double aggressionBias = factionAggressionBias(seeker.faction);
        double standoffBias = factionStandoffBias(seeker.faction);
        Ship committed = committedTarget(ctx, seeker);
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(seeker.faction, seeker.x, seeker.y, maxThreatSearchRadius(ctx, seeker), nearby);
            for (Ship enemy : nearby) {
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!TargetingSystem.isDetectableToObserver(seeker, enemy)) continue;
                if (!canShipThreatenTarget(ctx, seeker, enemy)) continue;
                double d = Math.hypot(enemy.x - seeker.x, enemy.y - seeker.y);
                double score = cheapTargetCandidateScore(ctx, seeker, enemy, sharedHint, preferredHint, committed, d, focusBias);
                AIFrameCache cache = currentFrameCache();
                if (cache != null) cache.cheapTargetScores++;
                if (candidateCount < cap) {
                    candidates[candidateCount] = enemy;
                    cheapScores[candidateCount] = score;
                    candidateCount++;
                } else {
                    int worst = 0;
                    double worstScore = cheapScores[0];
                    for (int i = 1; i < cap; i++) {
                        if (cheapScores[i] < worstScore) {
                            worstScore = cheapScores[i];
                            worst = i;
                        }
                    }
                    if (score > worstScore) {
                        candidates[worst] = enemy;
                        cheapScores[worst] = score;
                    }
                }
            }
        } finally {
            releaseShipScratch(nearby);
        }

        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidateCount; i++) {
            Ship enemy = candidates[i];
            if (!isAlive(enemy)) continue;
            double d = Math.hypot(enemy.x - seeker.x, enemy.y - seeker.y);
            double ewConf = observerEWConfidence(ctx, seeker, enemy, d);
            double score = cheapScores[i];
            score += (ewConf - 0.42) * 150.0;
            score += targetAngleAdvantageScore(seeker, enemy);
            score -= hostileBasePressurePenalty(ctx, seeker, enemy);
            score += sectorTargetPriorityBias(ctx, seeker, enemy);
            score -= killConfirmTargetPenalty(enemy, hullFrac(enemy)) * Math.max(0.40, 1.0 - focusBias * 0.18);
            if (ewConf < 0.22) {
                score -= (0.22 - ewConf) * 280.0;
            }
            if (state != null && shouldCommitToSharedTarget(state, seeker, enemy)) {
                score += 36.0 + sharedTargetConfidence(state, seeker) * 42.0;
            }
            AIFrameCache cache = currentFrameCache();
            if (cache != null) cache.mediumTargetScores++;

            if (CombatTargeting.shouldRunFullCandidateScore(scalePlan, seeker, enemy)) {
                if (cache != null) cache.expensiveTargetScores++;
                double fightMargin = canTakeFightMargin(ctx, seeker, enemy);
                score += fightMargin * 165.0;
                if (fightMargin < 0.0 && combinedDurabilityFrac(enemy) > 0.32) {
                    score -= 180.0 + Math.abs(fightMargin) * 120.0;
                }
                double localSupport = localSupportBiasAtPoint(ctx, seeker.faction, enemy.x, enemy.y, 680.0);
                score += localSupport * 82.0;
                if (!isCapitalRole(seeker.role) && localSupport < -1.25 + aggressionBias * 0.20 - standoffBias * 0.22) {
                    score -= 220.0 + Math.max(0.0, -localSupport - 1.25) * 34.0;
                }
                int[] nearbyCounts = countCombatantsSplitNearPoint(ctx, seeker.faction, enemy.x, enemy.y, 420.0);
                int friendlyNear = nearbyCounts[0];
                int hostileNear = nearbyCounts[1];
                int overCommit = friendlyNear - hostileNear - 2;
                double targetValue = roleWeightForFlagship(enemy.role) + threatPriority(seeker.role, enemy.role) * 0.35;
                double overCommitPenalty = Math.max(0.0, overCommit) * 44.0;
                if (enemy == sharedHint) overCommitPenalty *= Math.max(0.42, 1.0 - focusBias * 0.28);
                if (targetValue > 4.4 || combinedDurabilityFrac(enemy) < 0.34) overCommitPenalty *= 0.62;
                score -= overCommitPenalty;
            } else if (!quickCanTakeFightEstimate(seeker, enemy, d) && combinedDurabilityFrac(enemy) > 0.34) {
                score -= 260.0;
            }

            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private static double cheapTargetCandidateScore(GameContext ctx, Ship seeker, Ship enemy,
                                                    Ship sharedHint, Ship preferredHint, Ship committed,
                                                    double distance, double focusBias) {
        double score = Math.max(0.0, 1500.0 - distance) * 0.92;
        score += roleWeightForFlagship(enemy.role) * 18.0;
        if (enemy == sharedHint) score += 180.0 + focusBias * 95.0;
        if (enemy == preferredHint) score += 140.0;
        if (enemy == committed) score += 170.0 + Math.max(0.0, seeker.aiTargetCommitTimer) * 32.0;
        if (distance < 240.0) score += 360.0;
        score += threatPriority(seeker.role, enemy.role) * 72.0;
        score += aggressiveFighterTargetBias(seeker, enemy);
        score += targetVulnerabilityScore(seeker, enemy);
        if (ctx != null && BattlefieldSectorSystem.isEnabled(ctx)) {
            score += sectorTargetPriorityBias(ctx, seeker, enemy) * 0.35;
        }
        return score;
    }

    private static double aggressiveFighterTargetBias(Ship seeker, Ship enemy) {
        if (seeker == null || enemy == null || seeker.role != ShipRole.FIGHTER) return 0.0;
        double score = 0.0;
        if (enemy.role == ShipRole.FIGHTER) score += 760.0;
        else if (enemy.role == ShipRole.BOMBER) score += 720.0;
        else if (enemy.role == ShipRole.DRONE || enemy.role == ShipRole.PD_CRAFT) score += 520.0;
        else if (enemy.isSmallCraft()) score += 420.0;
        if (enemy.carrierOwnerId >= 0) score += 180.0;
        return score;
    }

    static double sectorTargetPriorityBias(GameContext ctx, Ship seeker, Ship target) {
        if (!BattlefieldSectorSystem.isEnabled(ctx) || seeker == null || target == null) return 0.0;
        if (seeker.faction == null || target.faction == null) return 0.0;

        BattlefieldSectorSystem.SectorDefinition targetSector = BattlefieldSectorSystem.sectorAt(ctx, target.x, target.y);
        if (targetSector == null) return 0.0;
        BattlefieldSectorSystem.SectorDefinition seekerSector = BattlefieldSectorSystem.sectorAt(ctx, seeker.x, seeker.y);
        BattlefieldSectorSystem.SectorDefinition objectiveSector =
                BattlefieldSectorSystem.objectiveSector(ctx, seeker.faction, seeker.x, seeker.y);
        BattlefieldSectorSystem.SectorDefinition homeSector = BattlefieldSectorSystem.homeSector(ctx, seeker.faction);
        BattlefieldSectorSystem.SectorSnapshot targetSnapshot =
                BattlefieldSectorSystem.snapshotForSector(ctx, targetSector.id);

        double score = 0.0;
        if (seekerSector != null && targetSector.id.equalsIgnoreCase(seekerSector.id)) {
            score += 86.0;
        }
        if (objectiveSector != null && targetSector.id.equalsIgnoreCase(objectiveSector.id)) {
            score += 228.0;
        }
        if (homeSector != null && targetSector.id.equalsIgnoreCase(homeSector.id)) {
            score += BattlefieldSectorSystem.isThreatenedForFaction(targetSnapshot, seeker.faction) ? 280.0 : 48.0;
        }
        if (targetSnapshot != null && targetSnapshot.controlState == BattlefieldSectorSystem.ControlState.CONTESTED) {
            score += 92.0;
        }
        if (targetSnapshot != null && BattlefieldSectorSystem.isFriendlyControl(targetSnapshot, seeker.faction)) {
            score += 58.0;
        }
        if (targetSnapshot != null && BattlefieldSectorSystem.isThreatenedForFaction(targetSnapshot, seeker.faction)) {
            score += 112.0;
        }

        int hopsFromSeeker = BattlefieldSectorSystem.hopDistance(ctx, seekerSector, targetSector);
        if (hopsFromSeeker > 1) {
            score -= (hopsFromSeeker - 1) * 54.0;
        }

        int hopsToObjective = BattlefieldSectorSystem.hopDistance(ctx, targetSector, objectiveSector);
        if (hopsToObjective > 0) {
            score -= hopsToObjective * 44.0;
        }
        return score;
    }

    private static boolean canShipThreatenTarget(GameContext ctx, Ship seeker, Ship target) {
        if (!isAlive(seeker) || !isAlive(target)) return false;
        boolean prosecutionContact = standardProsecutionContactAvailable(seeker, target)
                || playerFleetProsecutionContactAvailable(ctx, seeker, target)
                || redFleetProsecutionContactAvailable(ctx, seeker, target);
        if (!prosecutionContact && !TargetingSystem.isDetectableToObserver(seeker, target)) return false;
        double rangeMul = (ctx == null) ? 1.0 : CampaignSystem.targetingRangeMul(ctx);
        boolean ciwsIntercept = Turret.usesCiwsPelletsAgainst(seeker, firstGunTurret(seeker), target);
        if (TargetingSystem.isCiwsOnlyTarget(target) && !ciwsIntercept) return false;
        double d = Math.hypot(target.x - seeker.x, target.y - seeker.y);
        if (d <= 240.0) return true;
        if (d <= sustainedEngagementRangeForTarget(ctx, seeker, target)) return true;

        if (seeker.hasSuperweapon && d <= UNIVERSAL_SUPERWEAPON_RANGE * rangeMul) return true;
        for (Turret t : seeker.turrets) {
            if (t == null) continue;
            boolean offensiveMissile = t.kind == Turret.Kind.MISSILE
                    && (t.missileRole == null || t.missileRole != Turret.MissileRole.INTERCEPT);
            boolean offensiveGun = t.kind == Turret.Kind.GUN
                    && !Turret.usesCiwsPelletsAgainst(seeker, t, target);
            double maxRange = (offensiveGun || offensiveMissile) ? STANDARD_PROSECUTION_RANGE
                    : ((seeker.role == ShipRole.BASE || seeker.role == ShipRole.STATIC_TURRET)
                    ? ((t.kind == Turret.Kind.MISSILE) ? 1400.0 : 900.0)
                    : ((t.kind == Turret.Kind.MISSILE) ? 900.0 : 520.0));
            if (!offensiveGun && !offensiveMissile) maxRange *= rangeMul;
            if (d <= maxRange * 1.12) return true;
        }
        return false;
    }

    private static boolean shouldAttemptFireOnSensorContact(GameContext ctx, Ship seeker, Ship target, double dist, SquadObjective objective) {
        if (objective == SquadObjective.RESERVE) return false;
        return dist <= sustainedEngagementRangeForTarget(ctx, seeker, target);
    }

    private static boolean hasFireAuthorityContact(GameContext ctx, Ship shooter, Ship target) {
        if (!isAlive(shooter) || !isAlive(target)) return false;
        if (standardProsecutionContactAvailable(shooter, target)) return true;
        if (playerFleetProsecutionContactAvailable(ctx, shooter, target)) return true;
        if (redFleetProsecutionContactAvailable(ctx, shooter, target)) return true;
        if (TargetingSystem.isDetectableToObserver(ctx, shooter, target)) return true;
        return blueCommandContactAvailable(ctx, shooter, target);
    }

    private static boolean blueCommandContactAvailable(GameContext ctx, Ship shooter, Ship target) {
        if (ctx == null || shooter == null || target == null || ctx.player == null) return false;
        if (shooter == ctx.player || !isFriendlyToPlayer(ctx, shooter)) return false;
        if (!isAlive(ctx.player)) return false;
        if (shooter.faction == null || target.faction == null || shooter.faction.isFriendlyTo(target.faction)) return false;
        return TargetingSystem.isDetectableToObserver(ctx, ctx.player, target);
    }

    private static double blueCommandFireSensorConfidence(GameContext ctx, Ship shooter, Ship target, double shooterDist) {
        double own = observerEWConfidence(ctx, shooter, target, shooterDist);
        if (!blueCommandContactAvailable(ctx, shooter, target)) return own;
        double commandDist = Math.hypot(target.x - ctx.player.x, target.y - ctx.player.y);
        return Math.max(own, observerEWConfidence(ctx, ctx.player, target, commandDist));
    }

    private static boolean shouldCommitToSharedTarget(FleetState state, Ship s, Ship target) {
        if (!isAlive(target)) return false;
        if (state == null || s == null || s.faction == null) return true;
        Double conf = state.sharedTargetConfidence.get(fleetGroupKeyForShip(state, s));
        if (conf == null) return true;
        double c = Math.max(0.0, Math.min(1.0, conf));
        c += factionSharedFocusBias(s.faction) * 0.10;
        c += sharedTargetUrgencyBonus(s, target);
        if (isKillConfirmActive(target)) {
            if (hullFrac(target) < 0.11) return false;
            c *= 0.60;
        }
        if (c >= 0.56) return true;
        if (isSkirmishRole(s.role)) return c >= 0.24;
        if (isSupportRole(s.role)) return c >= 0.42;
        return c >= 0.34;
    }

    private static Ship committedTarget(GameContext ctx, Ship seeker) {
        if (ctx == null || seeker == null) return null;
        if (seeker.aiCommittedTargetId <= 0 || seeker.aiTargetCommitTimer <= 0.0) return null;
        Ship target = findLiveShipById(ctx.ships, seeker.aiCommittedTargetId);
        if (!isAlive(target)) return null;
        if (target.faction == null || seeker.faction == null) return null;
        if (seeker.faction.isFriendlyTo(target.faction)) return null;
        if (!TargetingSystem.isDetectableToObserver(seeker, target)) return null;
        return target;
    }

    private static void commitToTarget(Ship seeker, Ship target, double duration) {
        if (seeker == null || !isAlive(target)) return;
        boolean newTarget = seeker.aiCommittedTargetId != target.id;
        seeker.aiCommittedTargetId = target.id;
        seeker.aiTargetCommitTimer = Math.max(seeker.aiTargetCommitTimer, Math.max(0.3, duration));
        if (newTarget && hasOffensiveMissileTurret(seeker)) {
            seeker.aiMissileStandoffTargetId = target.id;
            seeker.aiMissileStandoffTimer = Math.max(seeker.aiMissileStandoffTimer, openingMissileStandoffSeconds(seeker));
        }
    }

    private static void clearTargetCommitment(Ship seeker) {
        if (seeker == null) return;
        seeker.aiCommittedTargetId = -1;
        seeker.aiTargetCommitTimer = 0.0;
        seeker.aiMissileStandoffTimer = 0.0;
        seeker.aiMissileStandoffTargetId = -1;
    }

    private static double targetCommitDuration(Ship seeker, Ship target, SquadObjective objective) {
        if (seeker == null || target == null) return 2.0;
        double duration = 2.6 + factionCommitmentBias(seeker.faction);
        if (isCapitalRole(target.role) || target.role == ShipRole.CARRIER || target.role == ShipRole.DRONE_CARRIER) duration += 0.9;
        if (isSupportRole(target.role) || isMissileThreatRole(target.role)) duration += 0.5;
        if (isSkirmishRole(seeker.role)) duration -= 0.25;
        if (isSupportRole(seeker.role)) duration += 0.30;
        if (objective == SquadObjective.INTERCEPT) duration += 0.35;
        if (objective == SquadObjective.RESERVE) duration -= 0.55;
        return Math.max(1.1, duration);
    }

    private static boolean shouldMaintainCommittedTarget(GameContext ctx, FleetState state, Ship seeker, Ship target,
                                                         Ship sharedHint, Ship preferredHint) {
        if (!isAlive(target) || seeker == null) return false;
        if (!canShipThreatenTarget(ctx, seeker, target)) return false;
        if (forcedToHoldFight(seeker, target)) return true;
        if (target == sharedHint && shouldCommitToSharedTarget(state, seeker, target)) return true;
        double d = Math.hypot(target.x - seeker.x, target.y - seeker.y);
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null && scalePlan.isLargeBattle()
                && !CombatTargeting.shouldRunFullCandidateScore(scalePlan, seeker, target)) {
            if (!quickCanTakeFightEstimate(seeker, target, d) && combinedDurabilityFrac(target) > 0.34) {
                return false;
            }
            if (target == preferredHint && seeker.aiTargetCommitTimer > 0.18) return true;
            if (combinedDurabilityFrac(target) < 0.38) return true;
            double quickKeepRange = Math.max(420.0,
                    preferredRange(seeker) * (1.55 + factionCommitmentBias(seeker.faction) * 0.08));
            return seeker.aiTargetCommitTimer > 0.0 && d <= quickKeepRange;
        }
        double support = localSupportBalance(ctx, seeker, target, Math.max(620.0, preferredRange(seeker) * 1.55));
        double selfDur = combinedDurabilityFrac(seeker);
        if (!isCapitalRole(seeker.role) && support < -1.35 && selfDur < 0.44 + factionRetreatBias(seeker.faction) * 0.04) {
            return false;
        }
        if (!canTakeFightMetric(ctx, seeker, target) && combinedDurabilityFrac(target) > 0.34) {
            return false;
        }
        if (target == preferredHint && seeker.aiTargetCommitTimer > 0.18) return true;
        if (combinedDurabilityFrac(target) < 0.38) return true;
        double keepRange = Math.max(420.0, preferredRange(seeker) * (1.55 + factionCommitmentBias(seeker.faction) * 0.08));
        return seeker.aiTargetCommitTimer > 0.0 && d <= keepRange;
    }

    private static double sharedTargetUrgencyBonus(Ship seeker, Ship target) {
        if (seeker == null || target == null) return 0.0;
        double bonus = 0.0;
        if (isMissileThreatRole(target.role)) bonus += 0.08;
        if (isSupportRole(target.role)) bonus += 0.06;
        if (isCapitalRole(target.role) || target.role == ShipRole.CARRIER || target.role == ShipRole.DRONE_CARRIER) bonus += 0.10;
        if (combinedDurabilityFrac(target) < 0.40) bonus += 0.06;
        if (seeker.role == ShipRole.BOMBER && isCapitalRole(target.role)) bonus += 0.06;
        return bonus;
    }

    private static void pruneClosestRetargetState(List<Ship> ships) {
        if (ships == null || ships.isEmpty()) {
            CLOSEST_RETARGET_TIMERS.clear();
            CLOSEST_RETARGET_TARGET_IDS.clear();
            IMMEDIATE_THREAT_SCAN_TIMERS.clear();
            ENGAGEMENT_SCAN_BACKOFF_TIMERS.clear();
            return;
        }
        java.util.HashSet<Integer> liveIds = borrowIntSetScratch();
        try {
            for (Ship s : ships) {
                if (!isAlive(s)) continue;
                liveIds.add(s.id);
            }
            CLOSEST_RETARGET_TIMERS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
            CLOSEST_RETARGET_TARGET_IDS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
            IMMEDIATE_THREAT_SCAN_TIMERS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
            ENGAGEMENT_SCAN_BACKOFF_TIMERS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
        } finally {
            releaseIntSetScratch(liveIds);
        }
    }

    private static void tickClosestWeaponRetarget(GameContext ctx, Ship seeker, double dt) {
        if (ctx == null || seeker == null || seeker.faction == null) return;
        if (!isAlive(seeker)) return;
        double t = CLOSEST_RETARGET_TIMERS.getOrDefault(seeker.id, 0.0) - Math.max(0.0, dt);
        if (t > 0.0) {
            CLOSEST_RETARGET_TIMERS.put(seeker.id, t);
            return;
        }

        double primarySearch = Math.max(440.0, preferredRange(seeker) * 2.1);
        if (seeker.role == ShipRole.BASE || seeker.role == ShipRole.STATIC_TURRET) primarySearch = 1700.0;
        Ship closest = findClosestThreatenableEnemy(ctx, seeker, primarySearch);
        if (!isAlive(closest)) closest = findClosestThreatenableEnemy(ctx, seeker, 3000.0);

        if (isAlive(closest)) {
            CLOSEST_RETARGET_TARGET_IDS.put(seeker.id, closest.id);
        } else {
            CLOSEST_RETARGET_TARGET_IDS.remove(seeker.id);
        }

        double jitter = (ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
        double nextRetarget = 4.3 + jitter * 1.6; // ~5 seconds average, slightly desynced.
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (scalePlan != null) {
            nextRetarget *= scalePlan.closestRetargetCadenceMultiplier;
        }
        CLOSEST_RETARGET_TIMERS.put(seeker.id, nextRetarget);
    }

    private static Ship periodicClosestRetargetTarget(GameContext ctx, Ship seeker) {
        if (ctx == null || seeker == null) return null;
        int id = CLOSEST_RETARGET_TARGET_IDS.getOrDefault(seeker.id, -1);
        if (id <= 0) return null;
        Ship target = findLiveShipById(ctx.ships, id);
        if (!isAlive(target)) return null;
        if (target.faction == null || seeker.faction == null) return null;
        if (seeker.faction.isFriendlyTo(target.faction)) return null;
        if (!TargetingSystem.isDetectableToObserver(seeker, target)) return null;
        return target;
    }

    private static Ship findClosestThreatenableEnemy(GameContext ctx, Ship seeker, double maxDist) {
        if (ctx == null || seeker == null || seeker.faction == null || maxDist <= 0.0) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(seeker.faction, seeker.x, seeker.y, maxDist, nearby);
            for (Ship enemy : nearby) {
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!TargetingSystem.isDetectableToObserver(seeker, enemy)) continue;
                double d2 = dist2(seeker.x, seeker.y, enemy.x, enemy.y);
                if (d2 >= bestD2) continue;
                if (!canShipThreatenTarget(ctx, seeker, enemy)) continue;
                bestD2 = d2;
                best = enemy;
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return best;
    }

    private static boolean playerCanDirectTeamFleet(GameContext ctx, Ship ship, Ship flagship) {
        if (ctx == null || ship == null || flagship == null || ctx.player == null) return false;
        if (!isAlive(flagship) || flagship != ctx.player) return false;
        if (ship.faction == null || ctx.player.faction == null) return false;
        return isPlayerCommandedFleetFaction(ctx, ship.faction);
    }

    private static boolean isPlayerCommandedFleetFaction(GameContext ctx, Faction faction) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null || faction == null) return false;
        return faction == ctx.player.faction || faction == Faction.ALLY || faction == Faction.PLAYER;
    }

    private static boolean isPointDefenseRole(Ship s) {
        if (s == null || s.role == null) return false;
        return switch (s.role) {
            case PD_CRAFT, CIWS_CORVETTE, PICKET, PATROL -> true;
            default -> false;
        };
    }

    private static boolean isSkirmishRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case FIGHTER, BOMBER, DRONE, PD_CRAFT, PICKET, PATROL, STEALTH_SHIP -> true;
            default -> false;
        };
    }

    private static boolean isSupportRole(ShipRole role) {
        return role != null && role.isSupportHull();
    }

    private static boolean isMissileThreatRole(ShipRole role) {
        return role != null && role.isHeavyMissileThreat();
    }

    private static boolean isCapitalRole(ShipRole role) {
        return role != null && role.isCapitalCombatant();
    }

    private enum AssaultLayer {
        ESCORT,
        LINE,
        CAPITAL,
        CENTRAL_TITAN,
        MOTHERSHIP,
        REAR_SUPPORT
    }

    private static boolean isLineHullOrLarger(ShipRole role) {
        if (role == null) return false;
        ShopHullCategory category = ShopHullCategory.forRole(role);
        return category == ShopHullCategory.LINE
                || category == ShopHullCategory.CAPITAL
                || category == ShopHullCategory.TITAN;
    }

    private static boolean isFriendlyToPlayer(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        if (ctx.player == null || ctx.player.faction == null) return false;
        return ctx.player.faction.isFriendlyTo(ship.faction);
    }

    private static Ship selectLargestSuperweaponTarget(GameContext ctx, Ship shooter, double range) {
        if (ctx == null || shooter == null || shooter.faction == null) return null;
        if (range <= 1.0) return null;

        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(shooter.faction, shooter.x, shooter.y, range, nearby);

            Ship best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            double r2 = range * range;
            for (Ship enemy : nearby) {
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!isLineHullOrLarger(enemy.role)) continue;
                if (!TargetingSystem.isDetectableToObserver(shooter, enemy)) continue;

                double d2 = dist2(shooter.x, shooter.y, enemy.x, enemy.y);
                if (d2 > r2) continue;

                double score = roleWeightForFlagship(enemy.role) * 1000.0 + enemy.hpMax * 3.0 + enemy.radius * 8.0;
                // Only a tiebreaker: we still prefer "largest", but avoid throwing shots away at extreme edge range.
                score -= Math.sqrt(d2) * 0.18;
                if (score > bestScore) {
                    bestScore = score;
                    best = enemy;
                }
            }
            return best;
        } finally {
            releaseShipScratch(nearby);
        }
    }

    private static double battlelineCoherenceScore(List<Ship> members, Ship flagship, Ship ship, Ship target) {
        if (!isAlive(flagship) || !isAlive(ship)) return 0.0;
        if (members == null || members.isEmpty()) return 0.0;

        double dFlag = Math.hypot(ship.x - flagship.x, ship.y - flagship.y);
        double cohesion = 1.0 - Math.max(0.0, Math.min(1.0, dFlag / Math.max(220.0, preferredRange(ship) * 2.8)));
        double outrunnerPenalty = 0.0;
        double crossfireBonus = 0.0;

        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        if (isAlive(target) && (scalePlan == null || !scalePlan.isLargeBattle())) {
            double tx = target.x - flagship.x;
            double ty = target.y - flagship.y;
            double tl = Math.hypot(tx, ty) + 1e-9;
            double ux = tx / tl;
            double uy = ty / tl;
            double projection = (ship.x - flagship.x) * ux + (ship.y - flagship.y) * uy;
            double leadBudget = Math.max(170.0, preferredRange(ship) * 1.1);
            if (projection > leadBudget) {
                outrunnerPenalty = Math.min(0.9, (projection - leadBudget) / Math.max(80.0, preferredRange(ship) * 1.7));
            }

            double sx = ship.x - target.x;
            double sy = ship.y - target.y;
            double sideSelf = sx * uy - sy * ux;
            int opposing = 0;
            for (Ship other : members) {
                if (!isAlive(other) || other == ship) continue;
                if (other == flagship) continue;
                double ox = other.x - target.x;
                double oy = other.y - target.y;
                double sideOther = ox * uy - oy * ux;
                if (sideSelf == 0.0 || sideOther == 0.0) continue;
                if (sideSelf * sideOther < 0.0) {
                    double od = Math.hypot(other.x - target.x, other.y - target.y);
                    if (od <= Math.max(260.0, preferredRange(other) * 1.9)) opposing++;
                }
            }
            crossfireBonus = Math.min(0.45, opposing * 0.10);
        }

        return cohesion - outrunnerPenalty + crossfireBonus;
    }

    private static double retreatIntentBonus(Ship enemy, double teamCx, double teamCy, double hpFrac) {
        if (enemy == null || hpFrac > 0.60) return 0.0;
        double ex = enemy.x - teamCx;
        double ey = enemy.y - teamCy;
        double en = Math.hypot(ex, ey);
        if (en < 1e-6) return 0.0;
        double ev = Math.hypot(enemy.vx, enemy.vy);
        if (ev < 1e-6) return 0.0;
        double away = (ex * enemy.vx + ey * enemy.vy) / (en * ev);
        if (away <= 0.18) return 0.0;
        double retreatStrength = Math.max(0.0, Math.min(1.0, (away - 0.18) / 0.82));
        return retreatStrength * (1.0 - hpFrac) * 180.0;
    }

    private static double threatPriority(ShipRole observer, ShipRole enemy) {
        if (enemy == null) return 1.0;
        if (observer == null) return defaultThreatPriority(enemy);
        return switch (observer) {
            case FIGHTER, BOMBER, DRONE, STEALTH_SHIP ->
                    switch (enemy) {
                        case MISSILE_BOAT, CARRIER, DRONE_CARRIER, TRANSPORT -> 2.9;
                        case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 1.2;
                        case MINER, HAULER -> 1.8;
                        default -> defaultThreatPriority(enemy);
                    };
            case PD_CRAFT, CIWS_CORVETTE, PICKET, PATROL ->
                    switch (enemy) {
                        case MISSILE_BOAT, BOMBER, FIGHTER, DRONE -> 3.0;
                        case CARRIER, DRONE_CARRIER -> 2.5;
                        default -> defaultThreatPriority(enemy);
                    };
            case FRIGATE, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER ->
                    switch (enemy) {
                        case CARRIER, DRONE_CARRIER, TRANSPORT -> 2.7;
                        case MISSILE_BOAT -> 2.6;
                        case DREADNOUGHT, SUPERSHIP -> 1.7;
                        default -> defaultThreatPriority(enemy);
                    };
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP ->
                    switch (enemy) {
                        case DREADNOUGHT, SUPERSHIP, BATTLESHIP, BATTLECRUISER -> 2.8;
                        case CARRIER, DRONE_CARRIER -> 2.6;
                        default -> defaultThreatPriority(enemy);
                    };
            case CARRIER, DRONE_CARRIER ->
                    switch (enemy) {
                        case MISSILE_BOAT, STEALTH_SHIP, FIGHTER, BOMBER -> 2.8;
                        default -> defaultThreatPriority(enemy);
                    };
            case MINER, HAULER, TRANSPORT ->
                    switch (enemy) {
                        case PICKET, PATROL, FIGHTER, BOMBER, STEALTH_SHIP -> 2.4;
                        default -> defaultThreatPriority(enemy);
                    };
            default -> defaultThreatPriority(enemy);
        };
    }

    private static double defaultThreatPriority(ShipRole enemy) {
        Double titanPriority = titanThreatPriority(enemy);
        if (titanPriority != null) return titanPriority;
        return switch (enemy) {
            case SUPERSHIP -> 3.1;
            case DREADNOUGHT -> 2.9;
            case BATTLESHIP -> 2.6;
            case BATTLECRUISER -> 2.4;
            case CARRIER, DRONE_CARRIER -> 2.3;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER -> 2.1;
            case MISSILE_BOAT -> 2.2;
            case TRANSPORT, HAULER, MINER -> 1.7;
            case BASE -> 2.0;
            default -> 1.5;
        };
    }

    private static double factionAggressionBias(Faction faction) {
        if (faction == null) return 0.0;
        return switch (faction) {
            case PLAYER, ALLY -> 0.0;
            case ENEMY -> 0.28;
            case TEAM_C -> -0.10;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> 0.16;
            case TEAM_E -> 0.0;
        };
    }

    private static double factionStandoffBias(Faction faction) {
        if (faction == null) return 0.0;
        return switch (faction) {
            case PLAYER, ALLY -> 0.02;
            case ENEMY -> -0.10;
            case TEAM_C -> 0.34;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> -0.06;
            case TEAM_E -> 0.0;
        };
    }

    private static double factionCommitmentBias(Faction faction) {
        if (faction == null) return 0.0;
        return switch (faction) {
            case PLAYER, ALLY -> 0.35;
            case ENEMY -> 0.60;
            case TEAM_C -> 0.22;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> 0.95;
            case TEAM_E -> 0.35;
        };
    }

    private static double factionSharedFocusBias(Faction faction) {
        if (faction == null) return 0.0;
        return switch (faction) {
            case PLAYER, ALLY -> 0.28;
            case ENEMY -> 0.55;
            case TEAM_C -> 0.20;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> 0.42;
            case TEAM_E -> 0.28;
        };
    }

    private static double factionRetreatBias(Faction faction) {
        if (faction == null) return 0.0;
        return switch (faction) {
            case PLAYER, ALLY -> 0.0;
            case ENEMY -> -0.08;
            case TEAM_C -> 0.26;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> -0.24;
            case TEAM_E -> 0.0;
        };
    }

    private static double combinedDurabilityFrac(Ship s) {
        if (s == null) return 0.0;
        double hullWeight = 0.65;
        double shieldWeight = 0.35;
        if (s.faction == Faction.TEAM_C) {
            hullWeight = 0.40;
            shieldWeight = 0.60;
        } else if (s.faction != null && s.faction.isYellowLineage()) {
            hullWeight = 0.92;
            shieldWeight = 0.08;
        }
        return hullFrac(s) * hullWeight + shieldFrac(s) * shieldWeight;
    }

    private static double targetVulnerabilityScore(Ship seeker, Ship target) {
        if (target == null) return 0.0;
        double durability = combinedDurabilityFrac(target);
        double score = (1.0 - durability) * 310.0;
        if (isSupportRole(target.role)) score += 55.0;
        if (isMissileThreatRole(target.role)) score += 44.0;
        if (isCapitalRole(target.role) && durability < 0.58) score += 62.0;
        if (seeker != null && seeker.role == ShipRole.BOMBER && isCapitalRole(target.role)) score += 90.0;
        if (seeker != null && isPointDefenseRole(seeker) && isMissileThreatRole(target.role)) score += 80.0;
        return score;
    }

    private static double targetAngleAdvantageScore(Ship seeker, Ship target) {
        if (!isAlive(seeker) || !isAlive(target)) return 0.0;
        double toSeeker = Math.atan2(seeker.y - target.y, seeker.x - target.x);
        double facingDelta = Math.abs(MathUtil.normalizeAngle(toSeeker - target.angle));
        double flankFrac = Math.max(0.0, Math.min(1.0, facingDelta / Math.PI));
        return flankFrac * 72.0;
    }

    private static double hostileBasePressurePenalty(GameContext ctx, Ship seeker, Ship target) {
        if (ctx == null || seeker == null || target == null) return 0.0;
        Ship hostileBase = findClosestHostileBase(ctx, seeker);
        if (!isAlive(hostileBase)) return 0.0;
        double d = Math.hypot(target.x - hostileBase.x, target.y - hostileBase.y);
        double safe = hostileBase.radius + Math.max(300.0, preferredRange(hostileBase) * 0.80);
        if (d >= safe) return 0.0;
        return (safe - d) / Math.max(1.0, safe) * 240.0;
    }

    private static double sharedTargetConfidence(FleetState state, Ship ship) {
        if (state == null || ship == null || ship.faction == null) return 1.0;
        Double c = state.sharedTargetConfidence.get(fleetGroupKeyForShip(state, ship));
        if (c == null) return 1.0;
        return Math.max(0.0, Math.min(1.0, c));
    }

    private static int formationSlotIndex(List<Ship> members, Ship flagship, Ship ship) {
        if (members == null || ship == null) return 0;
        int idx = 0;
        for (Ship s : members) {
            if (s == null || s == flagship) continue;
            if (s == ship) return idx;
            idx++;
        }
        return idx;
    }

    private static int formationWingCount(List<Ship> members, Ship flagship) {
        if (members == null || members.isEmpty()) return 0;
        int n = 0;
        for (Ship s : members) {
            if (s == null || s == flagship) continue;
            if (!isAlive(s)) continue;
            n++;
        }
        return Math.max(0, n);
    }

    private static double commandFormationLeadBias(GameContext.FleetCommand cmd) {
        if (cmd == null) return 0.0;
        return switch (cmd) {
            case ATTACK -> 0.55;
            case FORM_UP -> 0.28;
            case ESCORT -> 0.10;
            case DEFEND -> -0.22;
            case RETREAT, RTB, REPAIR -> -0.36;
            default -> 0.0;
        };
    }

    private static double[] formationAnchor(Ship flagship, int slot, int wingCount, double radius, double baseSpacing,
                                            GameContext.FleetFormation formation, GameContext.FleetCommand command) {
        if (flagship == null) return new double[]{0.0, 0.0};
        return formationAnchorAt(flagship.x, flagship.y, flagship.angle, slot, wingCount, radius, baseSpacing, formation, command);
    }

    private static double[] assaultFormationAnchor(Ship flagship, List<Ship> members, Ship teamFlagship, Ship ship,
                                                   double baseSpacing, GameContext.FleetCommand command, Ship contact) {
        return assaultFormationAnchor(flagship, members, teamFlagship, ship, baseSpacing, command, contact,
                GameContext.FleetFormation.ASSAULT);
    }

    private static double[] assaultFormationAnchor(Ship flagship, List<Ship> members, Ship teamFlagship, Ship ship,
                                                   double baseSpacing, GameContext.FleetCommand command, Ship contact,
                                                   GameContext.FleetFormation formation) {
        if (flagship == null) return new double[]{0.0, 0.0};
        return assaultFormationAnchorAt(flagship.x, flagship.y, flagship.angle, members, teamFlagship, ship,
                baseSpacing, command, contact, formation);
    }

    private static boolean usesEnemyFacingFormation(GameContext.FleetFormation formation) {
        return formation == GameContext.FleetFormation.ASSAULT
                || formation == GameContext.FleetFormation.DEFENSIVE
                || formation == GameContext.FleetFormation.OFFENSIVE;
    }

    private static double[] formationAnchorAt(double flagshipX, double flagshipY, double flagshipAngle,
                                              int slot, int wingCount, double radius, double baseSpacing,
                                              GameContext.FleetFormation formation, GameContext.FleetCommand command) {
        double spacing = Math.max(70.0, baseSpacing + radius * 1.2);
        double fx = Math.cos(flagshipAngle);
        double fy = Math.sin(flagshipAngle);
        double rx = -fy;
        double ry = fx;

        int n = Math.max(0, slot);
        double offX;
        double offY;
        GameContext.FleetFormation f = (formation == null) ? GameContext.FleetFormation.WEDGE : formation;
        switch (f) {
            case LINE -> {
                int aliveWing = Math.max(1, wingCount);
                int cols = Math.min(9, Math.max(3, aliveWing));
                if ((cols & 1) == 0) cols += 1;
                int colIndex = n % cols;
                int row = n / cols;
                double center = (cols - 1) * 0.5;
                double col = colIndex - center;
                offX = rx * col * spacing * 0.95 - fx * row * spacing * 0.92;
                offY = ry * col * spacing * 0.95 - fy * row * spacing * 0.92;
            }
            case SCREEN -> {
                double ang = (n % 8) * (Math.PI * 2.0 / 8.0);
                int ringIndex = n / 8;
                double ring = spacing * (1.2 + ringIndex * 0.75);
                offX = Math.cos(ang) * ring;
                offY = Math.sin(ang) * ring;
            }
            default -> {
                int row = (n / 2) + 1;
                double side = ((n & 1) == 0) ? -1.0 : 1.0;
                offX = -fx * row * spacing + rx * side * row * spacing * 0.78;
                offY = -fy * row * spacing + ry * side * row * spacing * 0.78;
            }
        }
        double lead = commandFormationLeadBias(command) * spacing;
        offX += fx * lead;
        offY += fy * lead;
        return new double[]{flagshipX + offX, flagshipY + offY};
    }

    private static double[] assaultFormationAnchorAt(double flagshipX, double flagshipY, double flagshipAngle,
                                                     List<Ship> members, Ship flagship, Ship ship,
                                                     double baseSpacing, GameContext.FleetCommand command, Ship contact) {
        return assaultFormationAnchorAt(flagshipX, flagshipY, flagshipAngle, members, flagship, ship,
                baseSpacing, command, contact, GameContext.FleetFormation.ASSAULT);
    }

    private static double[] assaultFormationAnchorAt(double flagshipX, double flagshipY, double flagshipAngle,
                                                     List<Ship> members, Ship flagship, Ship ship,
                                                     double baseSpacing, GameContext.FleetCommand command, Ship contact,
                                                     GameContext.FleetFormation formation) {
        if (ship == null) return new double[]{flagshipX, flagshipY};
        double spacing = Math.max(76.0, baseSpacing + ship.radius * 1.15);
        double dx = isAlive(contact) ? contact.x - flagshipX : 0.0;
        double dy = isAlive(contact) ? contact.y - flagshipY : 0.0;
        double contactDist = Math.hypot(dx, dy);
        double fx = (contactDist > 1e-6) ? dx / contactDist : Math.cos(flagshipAngle);
        double fy = (contactDist > 1e-6) ? dy / contactDist : Math.sin(flagshipAngle);
        double rx = -fy;
        double ry = fx;

        AssaultLayer shipLayer = assaultLayerForShip(ship);
        int[] layerCounts = new int[AssaultLayer.values().length];
        int[] layerPositions = new int[AssaultLayer.values().length];
        int shipIndex = 0;

        if (members != null) {
            for (Ship member : members) {
                if (!isAlive(member) || member == flagship) continue;
                AssaultLayer layer = assaultLayerForShip(member);
                int ordinal = layer.ordinal();
                if (member == ship) shipIndex = layerPositions[ordinal];
                layerPositions[ordinal]++;
                layerCounts[ordinal]++;
            }
        }

        double commandAdvance = switch (command) {
            case ATTACK -> 0.55;
            case FORM_UP -> 0.28;
            case ESCORT -> 0.18;
            case DEFEND -> -0.08;
            case RETREAT, RTB, REPAIR -> -0.28;
            default -> 0.12;
        };
        if (formation == GameContext.FleetFormation.DEFENSIVE) {
            commandAdvance = Math.min(commandAdvance, 0.08);
        } else if (formation == GameContext.FleetFormation.OFFENSIVE) {
            commandAdvance = Math.max(commandAdvance, 0.48);
        }
        double layerDepth = assaultLayerDepth(shipLayer);
        double[] layout = assaultLayerLayout(shipLayer, shipIndex, layerCounts[shipLayer.ordinal()], spacing);
        double offForward = spacing * (layerDepth + commandAdvance) - layout[1];
        if (contactDist > spacing * 2.0) {
            double desiredBetween = contactDist * assaultLayerContactFraction(shipLayer, formation);
            double layeredForward = spacing * (layerDepth + commandAdvance);
            double minForward = Math.max(0.0, ship.radius + 90.0);
            double maxForward = Math.max(minForward, contactDist - contact.radius - ship.radius - 80.0);
            offForward = MathUtil.clamp(Math.max(layeredForward, desiredBetween) - layout[1], minForward, maxForward);
        }
        double offSide = layout[0];

        return new double[]{
                flagshipX + fx * offForward + rx * offSide,
                flagshipY + fy * offForward + ry * offSide
        };
    }

    private static double assaultLayerContactFraction(AssaultLayer layer) {
        return assaultLayerContactFraction(layer, GameContext.FleetFormation.ASSAULT);
    }

    private static double assaultLayerContactFraction(AssaultLayer layer, GameContext.FleetFormation formation) {
        if (formation == GameContext.FleetFormation.DEFENSIVE) {
            return switch (layer) {
                case ESCORT -> 0.78;
                case LINE -> 0.68;
                case CAPITAL -> 0.56;
                case CENTRAL_TITAN -> 0.42;
                case MOTHERSHIP -> 0.0;
                case REAR_SUPPORT -> 0.20;
            };
        }
        if (formation == GameContext.FleetFormation.OFFENSIVE) {
            return switch (layer) {
                case ESCORT -> 0.72;
                case LINE -> 0.64;
                case CAPITAL -> 0.50;
                case CENTRAL_TITAN -> 0.36;
                case MOTHERSHIP -> 0.0;
                case REAR_SUPPORT -> 0.16;
            };
        }
        return switch (layer) {
            case ESCORT -> 0.66;
            case LINE -> 0.56;
            case CAPITAL -> 0.42;
            case CENTRAL_TITAN -> 0.30;
            case MOTHERSHIP -> 0.0;
            case REAR_SUPPORT -> 0.12;
        };
    }

    private static Ship assaultFormationContact(GameContext ctx, FleetState state, Ship seeker,
                                                Ship protectedAnchor, Ship current) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;
        if (isAssaultContactUsable(ctx, seeker, current)) return current;

        Ship shared = focusTargetForShip(state, seeker);
        if (isAssaultContactUsable(ctx, seeker, shared)) return shared;

        Ship anchor = isAlive(protectedAnchor) ? protectedAnchor : seeker;
        double searchRange = Math.max(1650.0, preferredRange(seeker) * 2.75);
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(seeker.faction, anchor.x, anchor.y, searchRange, nearby);
            for (Ship enemy : nearby) {
                if (!isAssaultContactUsable(ctx, seeker, enemy)) continue;
                double anchorDist = Math.hypot(enemy.x - anchor.x, enemy.y - anchor.y);
                double seekerDist = Math.hypot(enemy.x - seeker.x, enemy.y - seeker.y);
                double score = roleWeightForFlagship(enemy.role) * 85.0
                        + threatPriority(seeker.role, enemy.role) * 60.0
                        - anchorDist * 0.42
                        - seekerDist * 0.18;
                if (isMissileThreatRole(enemy.role)) score += 95.0;
                if (enemy.role == ShipRole.CARRIER || enemy.role == ShipRole.DRONE_CARRIER) score += 70.0;
                if (score > bestScore) {
                    bestScore = score;
                    best = enemy;
                }
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return best;
    }

    private static boolean isAssaultContactUsable(GameContext ctx, Ship seeker, Ship contact) {
        if (!isAlive(seeker) || !isAlive(contact)) return false;
        if (seeker.faction == null || contact.faction == null || seeker.faction.isFriendlyTo(contact.faction)) return false;
        return hasFireAuthorityContact(ctx, seeker, contact);
    }

    private static boolean shouldAssaultShipSeekConfrontation(Ship ship) {
        if (!isAlive(ship) || ship.role == null) return false;
        if (ship.role == ShipRole.PICKET) return true;
        if (assaultLayerForShip(ship) != AssaultLayer.LINE) return false;
        if (hullFrac(ship) < 0.42) return false;
        return ship.shieldMax <= 1e-6 || shieldFrac(ship) >= 0.16;
    }

    private static boolean shouldOffensiveShipSeekConfrontation(Ship ship) {
        if (!isAlive(ship) || ship.role == null) return false;
        if (isSupportRole(ship.role)) return false;
        if (hullFrac(ship) < 0.34) return false;
        return ship.shieldMax <= 1e-6 || shieldFrac(ship) >= 0.12;
    }

    private static AssaultLayer assaultLayerForShip(Ship ship) {
        if (ship == null || ship.role == null) return AssaultLayer.LINE;
        ShipRole role = ship.role;
        if (role.isMothership()) return AssaultLayer.MOTHERSHIP;
        if (isAssaultRearSupportRole(role)) return AssaultLayer.REAR_SUPPORT;
        if (isAssaultCentralTitanRole(role)) return AssaultLayer.CENTRAL_TITAN;
        if (isAssaultEscortRole(ship)) return AssaultLayer.ESCORT;
        if (isAssaultCapitalRole(role)) return AssaultLayer.CAPITAL;
        return AssaultLayer.LINE;
    }

    private static boolean isAssaultEscortRole(Ship ship) {
        if (ship == null || ship.role == null) return false;
        if (isPointDefenseRole(ship)) return true;
        return switch (ship.role) {
            case FIGHTER, DRONE, PD_CRAFT, PICKET, PATROL, CIWS_CORVETTE -> true;
            default -> false;
        };
    }

    private static boolean isAssaultCapitalRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER,
                 BATTLESHIP, DREADNOUGHT, SUPERSHIP -> true;
            default -> false;
        };
    }

    private static boolean isAssaultCentralTitanRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case TRANSPORT_TITAN,
                 BULWARK_TITAN,
                 VANGUARD_TITAN,
                 INTERDICTION_TITAN,
                 SHIELD_BASTION_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN,
                 HYPERWEAPON_TITAN -> true;
            default -> false;
        };
    }

    private static boolean isAssaultRearSupportRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case MINER, HAULER, TRANSPORT,
                 CARRIER, DRONE_CARRIER,
                 CARRIER_SUPPORT_TITAN,
                 COMMAND_INTEL_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN,
                 FLEET_TELEPORTER_TITAN,
                 ELITE_REINFORCEMENTS_TITAN,
                 MOBILE_STATION_TITAN -> true;
            default -> false;
        };
    }

    private static double assaultLayerDepth(AssaultLayer layer) {
        return switch (layer) {
            case ESCORT -> 2.75;
            case LINE -> 1.65;
            case CAPITAL -> 0.55;
            case CENTRAL_TITAN -> -0.20;
            case MOTHERSHIP -> -1.10;
            case REAR_SUPPORT -> -2.10;
        };
    }

    private static double[] assaultLayerLayout(AssaultLayer layer, int index, int count, double spacing) {
        int safeCount = Math.max(1, count);
        int cols = assaultLayerColumns(layer, safeCount);
        int colIndex = Math.max(0, index) % cols;
        int row = Math.max(0, index) / cols;
        double center = (cols - 1) * 0.5;
        double col = colIndex - center;

        double lateralMul = switch (layer) {
            case ESCORT -> 0.82;
            case LINE -> 0.92;
            case CAPITAL -> 1.02;
            case CENTRAL_TITAN -> 0.95;
            case MOTHERSHIP -> 0.75;
            case REAR_SUPPORT -> 0.88;
        };
        double rowSpacingMul = switch (layer) {
            case ESCORT -> 0.52;
            case LINE -> 0.64;
            case CAPITAL -> 0.78;
            case CENTRAL_TITAN -> 0.84;
            case MOTHERSHIP -> 0.72;
            case REAR_SUPPORT -> 0.80;
        };

        double lateral = col * spacing * lateralMul;
        double rowBack = row * spacing * rowSpacingMul;
        return new double[]{lateral, rowBack};
    }

    private static int assaultLayerColumns(AssaultLayer layer, int count) {
        int safeCount = Math.max(1, count);
        int desired = switch (layer) {
            case ESCORT -> Math.min(8, Math.max(2, safeCount));
            case LINE -> Math.min(6, Math.max(2, safeCount));
            case CAPITAL -> Math.min(4, Math.max(1, safeCount));
            case CENTRAL_TITAN -> Math.min(3, Math.max(1, safeCount));
            case MOTHERSHIP -> 1;
            case REAR_SUPPORT -> Math.min(4, Math.max(1, safeCount));
        };
        if (desired > 1 && (desired & 1) == 0) desired -= 1;
        return Math.max(1, desired);
    }

    private static void synchronizeFlagshipWarpFormations(GameContext ctx, FleetState state) {
        if (ctx == null || state == null) return;
        for (Map.Entry<Integer, Ship> entry : state.flagships.entrySet()) {
            Ship flagship = (entry == null) ? null : entry.getValue();
            if (!isAlive(flagship) || !flagship.isWarpCharging()) continue;

            List<Ship> members = state.members.get(entry.getKey());
            if (members == null || members.isEmpty()) continue;

            int wingCount = formationWingCount(members, flagship);
            double remaining = Math.max(0.1, flagship.warpChargeRemaining());
            for (Ship member : members) {
                if (!isAlive(member) || member == flagship) continue;
                if (!shouldJoinFlagshipWarp(ctx, member, flagship)) continue;

                boolean playerDirected = playerCanDirectTeamFleet(ctx, member, flagship);
                GameContext.FleetFormation desiredFormation = playerDirected
                        ? ctx.command.alliedFleetFormation
                        : state.autoFormation.getOrDefault(entry.getKey(), GameContext.FleetFormation.WEDGE);
                double spacingMul = state.autoFormationSpacing.getOrDefault(entry.getKey(), 1.0);
                GameContext.FleetCommand cmd = resolveFleetCommand(ctx, member, flagship);
                if (cmd == null || cmd == GameContext.FleetCommand.AUTO) cmd = GameContext.FleetCommand.FORM_UP;
                int slot = formationSlotIndex(members, flagship, member);
                double memberSpacing = preferredRange(member) * 0.35 * spacingMul;
                Ship assaultContact = usesEnemyFacingFormation(desiredFormation)
                        ? assaultFormationContact(ctx, state, member, flagship, null)
                        : null;
                double[] exit = usesEnemyFacingFormation(desiredFormation)
                        ? assaultFormationAnchorAt(
                                flagship.warpExitX(),
                                flagship.warpExitY(),
                                flagship.angle,
                                members,
                                flagship,
                                member,
                                memberSpacing,
                                cmd,
                                assaultContact,
                                desiredFormation)
                        : formationAnchorAt(
                                flagship.warpExitX(),
                                flagship.warpExitY(),
                                flagship.angle,
                                slot,
                                wingCount,
                                member.radius,
                                memberSpacing,
                                desiredFormation,
                                cmd);
                boolean wasCharging = member.isWarpCharging();
                boolean started = member.beginBattlefieldWarpFollowing(exit[0], exit[1], remaining, flagship.id);
                if (started && BattlefieldSectorSystem.isEnabled(ctx)) {
                    BattlefieldSectorSystem.SectorDefinition sourceSector = BattlefieldSectorSystem.sectorAt(ctx, member.x, member.y);
                    if (sourceSector == null) {
                        sourceSector = BattlefieldSectorSystem.sectorAt(ctx, flagship.x, flagship.y);
                    }
                    member.setWarpSourceSectorId(sourceSector == null ? "" : sourceSector.id);
                }
                if (started && !wasCharging && ctx.player != null && member.faction != null
                        && ctx.player.faction != null
                        && member.faction.teamId() == ctx.player.faction.teamId()) {
                    String label = ctx.command.fleetSquadLabelByShip.getOrDefault(member.id, "ESCORT");
                    postFleetComm(ctx, member.faction, label, "matching command warp profile");
                }
            }
        }
    }

    private static void fight(GameContext ctx, Ship s, Ship target, double dt) {
        fight(ctx, s, target, dt, 1.0, SquadObjective.HOLD);
    }

    private static void fight(GameContext ctx, Ship s, Ship target, double dt, double teamConfidence, SquadObjective objective) {
        long fightStart = System.nanoTime();
        try {
            if (ctx == null || s == null || target == null) return;
            if (objective == null) objective = SquadObjective.HOLD;
            commitToTarget(s, target, targetCommitDuration(s, target, objective));
            if (maybeStartBattlefieldWarp(ctx, s, target,
                    Math.max(preferredRange(s) * 1.18, s.radius + target.radius + 160.0))) {
                s.tryCIWS(dt, ctx);
                return;
            }
            if (CombatMovement.shouldReuseMovementThink(currentScalePlan(), s)) {
                double d = Math.hypot(target.x - s.x, target.y - s.y);
                rotateShipTowardAssist(s, Math.atan2(target.y - s.y, target.x - s.x), dt,
                        maxTurnRateRadPerSec(s) * 0.72);
                int shotsFired = fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                updateEngagementMemory(s, target, dt, d,
                        Double.isFinite(s.aiCachedDesiredRange) ? s.aiCachedDesiredRange : preferredRange(s),
                        shotsFired > 0, objective);
                AIFrameCache cache = currentFrameCache();
                if (cache != null) cache.movementReuseFrames++;
                s.tryCIWS(dt, ctx);
                return;
            }
            double baseRange = preferredRange(s);
            double range = baseRange;
            double aggression = roleAggressionBias(s.role) + factionAggressionBias(s.faction);
            double standoff = roleStandoffBias(s.role) + factionStandoffBias(s.faction);
            double approachMul = roleApproachSpeedMul(s.role);
            double orbitMul = roleOrbitSpeedMul(s.role);
            range *= (1.0 + standoff * 0.18);
            double selfHull = hullFrac(s);
            double selfShield = shieldFrac(s);
            double targetHull = hullFrac(target);
            boolean dogfightRole = isDogfightRole(s.role);
            boolean smallCraftDogfight = dogfightRole && s.isSmallCraft() && target.isSmallCraft();
            double pushHullReq = 0.62 - aggression * 0.10;
            double pushShieldReq = 0.36 - aggression * 0.09;
            double targetVulnReq = 0.45 + aggression * 0.08;
            double fallbackHullReq = 0.46 + standoff * 0.08 - aggression * 0.05 + factionRetreatBias(s.faction) * 0.10;
            double fallbackShieldReq = 0.26 + standoff * 0.07 - aggression * 0.04 + factionRetreatBias(s.faction) * 0.08;
            boolean forceFallback = false;
            if (s.faction == Faction.TEAM_C) {
                pushShieldReq += 0.08;
                fallbackShieldReq += 0.10;
                if (selfShield < 0.24) forceFallback = true;
            }
            if (s.faction != null && s.faction.isYellowLineage()) {
                pushHullReq -= 0.05;
                fallbackHullReq -= 0.10;
                targetVulnReq += 0.04;
            }
            boolean push = (selfHull > pushHullReq && selfShield > pushShieldReq && targetHull < targetVulnReq);
            boolean fallBack = (selfHull < fallbackHullReq || selfShield < fallbackShieldReq);
            if (forceFallback) fallBack = true;
            double d2 = dist2(s.x, s.y, target.x, target.y);
            double d = Math.sqrt(d2);
            boolean detailedFightAssessment = shouldRunDetailedFightAssessment(s);
            double supportBalance = detailedFightAssessment
                    ? localSupportBalance(ctx, s, target, Math.max(560.0, range * 1.55))
                    : 0.0;
            if (!isCapitalRole(s.role) && supportBalance < -0.95) fallBack = true;
            if (supportBalance > 1.15 && selfHull > 0.52 && selfShield > 0.30) push = true;
            if (push && supportBalance < -0.45) push = false;
            if (objective == SquadObjective.RESERVE && supportBalance < 0.25) fallBack = true;
            if (objective == SquadObjective.INTERCEPT && supportBalance > -0.30 && selfHull > 0.40) fallBack = false;
            if (push) range *= Math.max(0.86, 0.92 - aggression * 0.04);
            if (fallBack) range *= 1.32 + Math.max(0.0, standoff) * 0.10;
            double personalRangeMul = 0.94 + (((s.id * 97) & 7) * 0.02);
            range *= personalRangeMul;
            double standoffFloor = smallCraftDogfight
                    ? s.radius + target.radius + 64.0
                    : Math.max(baseRange * 0.88, s.radius + target.radius + 220.0);
            if (range < standoffFloor) range = standoffFloor;
            double openingMissileRange = openingMissileStandoffRange(ctx, s, target);
            if (openingMissileRange > 0.0 && !smallCraftDogfight && !push && !fallBack) {
                range = Math.max(range, openingMissileRange);
            }
            if (smallCraftDogfight) {
                double dogfightGunRange = effectivePrimaryGunRangeAgainstTarget(
                        s, target, 720.0 * gunRangeRoleMul(s.role) * CampaignSystem.targetingRangeMul(ctx));
                if (dogfightGunRange > 1.0) {
                    double desiredOrbitRange = Math.max(standoffFloor, dogfightGunRange * 0.82);
                    range = Math.min(range, desiredOrbitRange);
                }
            }
            boolean canTakeFight = detailedFightAssessment
                    ? canTakeFightMetric(ctx, s, target)
                    : quickCanTakeFightEstimate(s, target, d);
            if (!canTakeFight) {
                fallBack = true;
                push = false;
                range *= 1.24 + Math.max(0.0, standoff) * 0.08;
            }
            if (forcedToHoldFight(s, target)) {
                fallBack = false;
                if (!isSupportRole(s.role)) {
                    push = true;
                }
                range *= 0.94;
            }

            double speed = MovementModel.speedCeiling(s);
            double orbitDir = ((s.hashCode() & 1) == 0) ? 1.0 : -1.0;
            if (s.aiBadApproachTimer > 0.0 && Double.isFinite(s.aiBadApproachAngle)) {
                double toTarget = Math.atan2(target.y - s.y, target.x - s.x);
                double laneDelta = Math.abs(MathUtil.normalizeAngle(toTarget - s.aiBadApproachAngle));
                if (laneDelta < Math.toRadians(30.0)) {
                    orbitDir = -orbitDir;
                    range *= 1.06;
                }
            }

            boolean handledMovement = applyRoleSpecificAttackMovement(
                    ctx, s, target, dt, range, d, speed, push, fallBack,
                    approachMul, orbitMul, aggression, standoff, orbitDir
            );
            if (!handledMovement) {
                if (d > range * 1.22) {
                    if (s.aiBadApproachTimer > 0.0 && Double.isFinite(s.aiBadApproachAngle)) {
                        double tx = target.x - s.x;
                        double ty = target.y - s.y;
                        double tl = Math.hypot(tx, ty) + 1e-9;
                        tx /= tl;
                        ty /= tl;
                        double side = ((s.id & 1) == 0) ? -1.0 : 1.0;
                        double sx = -ty * side;
                        double sy = tx * side;
                        moveToward(s, target.x + sx * 140.0, target.y + sy * 140.0, speed * (push ? 1.08 : 1.0) * approachMul, dt);
                    } else {
                        moveToward(s, target.x, target.y, speed * (push ? 1.08 : 1.0) * approachMul, dt);
                    }
                } else if (fallBack && d < range * (0.95 + Math.max(0.0, standoff) * 0.12 - Math.max(0.0, aggression) * 0.10)) {
                    retreatFromTarget(ctx, s, target, speed * (1.0 + Math.max(0.0, standoff) * 0.10), dt);
                } else {
                    double orbitSpeed = speed * (fallBack ? 0.98 : 0.95) * orbitMul;
                    if (dogfightRole) orbitSpeed *= 0.88;
                    orbit(s, target.x, target.y, range, orbitSpeed, dt, orbitDir);
                }
            }

            if (dogfightRole) {
                double noseAngle = Math.atan2(target.y - s.y, target.x - s.x);
                rotateShipTowardAssist(s, noseAngle, dt, maxTurnRateRadPerSec(s) * 1.35);
            }

            int shotsFired = fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
            updateEngagementMemory(s, target, dt, d, range, shotsFired > 0, objective);
            s.aiCachedDesiredRange = range;
            s.aiMovementThinkTimer = Math.max(s.aiMovementThinkTimer,
                    CombatMovement.movementThinkSeconds(currentScalePlan(), s));
            s.tryCIWS(dt, ctx);
        } finally {
            shipCombatFightNs += System.nanoTime() - fightStart;
        }
    }

    private static void wander(GameContext ctx, Ship s, double dt) {
        double tx;
        double ty;
        double[] featurePoint = sensorDrivenNavigationAnchor(ctx, s);
        if (featurePoint != null) {
            tx = featurePoint[0];
            ty = featurePoint[1];
        } else {
            double[] sectorPoint = BattlefieldSectorSystem.navigationPoint(ctx, s == null ? null : s.faction,
                    s == null ? 0.0 : s.x, s == null ? 0.0 : s.y, s == null ? 0 : s.id);
            if (sectorPoint != null) {
                tx = sectorPoint[0];
                ty = sectorPoint[1];
            } else {
                double[] idleAnchor = idleNavigationAnchor(ctx, s);
                tx = idleAnchor[0];
                ty = idleAnchor[1];
            }
        }
        if (maybeStartBattlefieldWarp(ctx, s, tx, ty, Math.max(180.0, s.radius + 110.0))) {
            s.tryCIWS(dt, ctx);
            return;
        }
        moveToward(s, tx, ty, Math.max(32.0, MovementModel.speedCeiling(s) * 0.7), dt);

        s.tryCIWS(dt, ctx);
    }

    private static double[] sensorDrivenNavigationAnchor(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null || ship.faction == null || ctx.player == null || ctx.player.faction == null) {
            return null;
        }
        if (!ship.faction.isFriendlyTo(ctx.player.faction)) {
            return null;
        }
        List<FogOfWarSystem.SensorInterestSignal> signals = sensorInterestSignalsCached(ctx);
        if (signals.isEmpty()) {
            return null;
        }

        FogOfWarSystem.SensorInterestSignal best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean scoutRole = ship.role == ShipRole.PICKET
                || ship.role == ShipRole.PATROL
                || ship.role == ShipRole.STEALTH_SHIP
                || ship.role == ShipRole.FIGHTER
                || ship.role == ShipRole.DRONE;
        boolean supportRole = ship.role == ShipRole.TRANSPORT
                || ship.role == ShipRole.HAULER
                || ship.role == ShipRole.MINER;

        for (FogOfWarSystem.SensorInterestSignal signal : signals) {
            if (signal == null || signal.kind == null) continue;
            double dx = signal.x - ship.x;
            double dy = signal.y - ship.y;
            double dist = Math.hypot(dx, dy);
            if (dist < Math.max(180.0, ship.radius * 3.0)) continue;
            double score = signal.strength * 180.0 - dist * 0.055;
            switch (signal.kind) {
                case CONTACT, INSTALLATION, MASS_SIGNATURE -> score += scoutRole ? 135.0 : 75.0;
                case INTEL, ANOMALY, FLEET_ASSET -> score += scoutRole ? 115.0 : 62.0;
                case ORE_VEIN, CACHE, WRECKAGE -> score += supportRole ? 130.0 : 28.0;
                case HAZARD -> score += scoutRole ? 42.0 : -24.0;
                default -> score += 0.0;
            }
            if (signal.uncertaintyRadius > 240.0 && !scoutRole) {
                score -= Math.min(70.0, (signal.uncertaintyRadius - 240.0) * 0.12);
            }
            if (score > bestScore) {
                bestScore = score;
                best = signal;
            }
        }

        if (best == null || bestScore < 18.0) {
            return null;
        }
        return new double[]{best.x, best.y};
    }

    private static double[] idleNavigationAnchor(GameContext ctx, Ship s) {
        double sx = (s == null) ? 0.0 : s.x;
        double sy = (s == null) ? 0.0 : s.y;
        if (ctx == null || s == null) return new double[]{sx, sy};

        double anchorX = sx;
        double anchorY = sy;
        Ship ownBase = (s.faction == null) ? null : TeamSystem.getBaseForTeam(ctx, s.faction);
        if (isAlive(ownBase)) {
            anchorX = ownBase.x;
            anchorY = ownBase.y;
        } else {
            Ship flagship = findFlagshipForFaction(ctx, s.faction, s);
            if (isAlive(flagship)) {
                anchorX = flagship.x;
                anchorY = flagship.y;
            }
        }

        double jitterRadius = isSkirmishRole(s.role) ? 520.0 : (isCapitalRole(s.role) ? 340.0 : 430.0);
        double jx = ((ctx.rng == null) ? Math.random() : ctx.rng.nextDouble()) - 0.5;
        double jy = ((ctx.rng == null) ? Math.random() : ctx.rng.nextDouble()) - 0.5;
        return new double[]{anchorX + jx * jitterRadius, anchorY + jy * jitterRadius};
    }

    private static Ship findFlagshipForFaction(GameContext ctx, Faction faction, Ship fallback) {
        if (ctx == null || faction == null || ctx.ships == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship ship : ctx.ships) {
            if (!isAlive(ship) || ship.faction == null) continue;
            if (ship.faction.teamId() != faction.teamId()) continue;
            if (!canLeadFleetFormation(ship)) continue;
            double score = flagshipScore(ship);
            if (score > bestScore) {
                bestScore = score;
                best = ship;
            }
        }
        return isAlive(best) ? best : null;
    }

    private static int fireIfAble(GameContext ctx, Ship s, Ship target, double dt, double dist) {
        return fireIfAble(ctx, s, target, dt, dist, 1.0, SquadObjective.HOLD);
    }

    private static int fireIfAble(GameContext ctx, Ship s, Ship target, double dt, double dist,
                                   double teamConfidence, SquadObjective objective) {
        long fireStart = System.nanoTime();
        try {
            if (ctx == null || s == null || target == null || ctx.projectiles == null) return 0;
            if (!CombatFireControl.canConsiderTarget(s, target)) return 0;
            if (s.aiArrivalFireDelayTimer > 0.0) return 0;
            if (!hasFireAuthorityContact(ctx, s, target)) return 0;
            boolean ciwsIntercept = Turret.usesCiwsPelletsAgainst(s, firstGunTurret(s), target);
            if (TargetingSystem.isCiwsOnlyTarget(target) && !ciwsIntercept) return 0;
            if (objective == null) objective = SquadObjective.HOLD;
            double rangeMul = CampaignSystem.targetingRangeMul(ctx);
            double sensorConfidence = blueCommandFireSensorConfidence(ctx, s, target, dist);
            double confidence = Math.max(0.0, Math.min(1.0, sensorConfidence * Math.max(0.20, teamConfidence)));
            boolean killConfirm = isKillConfirmActive(target);
            boolean overkillLikely = isOverkillLikely(ctx, s, target);
            double targetHull = hullFrac(target);
            int firedCount = 0;

        if (s.hasSuperweapon && (s.isSuperweaponCharging() || s.isSuperweaponBeamActive())) {
            s.trackSuperweaponAim(target.x, target.y);
            if ((s.role == ShipRole.SUPERSHIP || s.role == ShipRole.HYPERWEAPON_TITAN)
                    && s.isSuperweaponCharging()) {
                rotateShipTowardAssist(s, s.getSuperweaponAimAngle(), dt, Math.toRadians(260.0));
            }
        }

        if (s.hasSuperweapon) {
            double superweaponRange = UNIVERSAL_SUPERWEAPON_RANGE * rangeMul;

            Ship swTarget = target;
            double swDist = dist;
            double swConfidence = confidence;
            boolean swKillConfirm = killConfirm;
            boolean swOverkillLikely = overkillLikely;
            double swHull = targetHull;

            boolean canStartSuperweapon = s.canFireSuperweapon();
            Ship largest = selectLargestSuperweaponTarget(ctx, s, superweaponRange);
            boolean aggressiveLargestTargeting = isAlive(largest)
                    && (canStartSuperweapon || s.isSuperweaponCharging() || s.isSuperweaponBeamActive());
            if (aggressiveLargestTargeting) {
                swTarget = largest;
                swDist = Math.hypot(largest.x - s.x, largest.y - s.y);
                double swSensorConfidence = observerEWConfidence(ctx, s, swTarget, swDist);
                swConfidence = Math.max(0.0, Math.min(1.0, swSensorConfidence * Math.max(0.20, teamConfidence)));
                swKillConfirm = isKillConfirmActive(swTarget);
                swOverkillLikely = isOverkillLikely(ctx, s, swTarget);
                swHull = hullFrac(swTarget);
            }

            if (swDist <= superweaponRange) {
                boolean superShip = (s.role == ShipRole.SUPERSHIP || s.role == ShipRole.HYPERWEAPON_TITAN);
                boolean allowSuperweapon;

                if (aggressiveLargestTargeting && swTarget != null && isLineHullOrLarger(swTarget.role)) {
                    allowSuperweapon = canStartSuperweapon && !swKillConfirm;
                } else if (superShip) {
                    // Normal supership gating (still more aggressive than non-superweapon ships).
                    double confGate = isCapitalRole(swTarget.role) ? 0.38 : 0.46;
                    double hullGate = isCapitalRole(swTarget.role) ? 0.12 : 0.18;
                    allowSuperweapon = swConfidence >= confGate && swHull > hullGate && !swKillConfirm;
                } else {
                    allowSuperweapon = swConfidence >= 0.62 && swHull > 0.30 && !swKillConfirm && !swOverkillLikely;
                }

                if (objective == SquadObjective.RESERVE && !aggressiveLargestTargeting) {
                    allowSuperweapon = allowSuperweapon
                            && swConfidence >= (superShip ? 0.58 : 0.78)
                            && swDist <= superweaponRange * (superShip ? 0.82 : 0.70);
                }
                if (objective == SquadObjective.INTERCEPT && !aggressiveLargestTargeting) {
                    allowSuperweapon = allowSuperweapon && swConfidence >= (superShip ? 0.44 : 0.56);
                }

                if ((allowSuperweapon || s.isSuperweaponCharging() || s.isSuperweaponBeamActive()) && swTarget != null) {
                    s.trackSuperweaponAim(swTarget.x, swTarget.y);
                }

                Projectile shot = allowSuperweapon ? s.tryFireSuperweapon(ctx, swTarget, dt) : null;
                if (shot != null) {
                    ctx.projectiles.add(shot);
                    ScreenShake.kick(3.5);
                    firedCount++;
                }
            }
        }

        boolean blueTeam = (s.faction == Faction.PLAYER || s.faction == Faction.ALLY);
        boolean menuStagger = s.attractModeStaggerPrimaryFire;
        boolean energyBoltStagger = menuStagger || (blueTeam && s.usesStaggeredPrimaryFire());
        boolean beamBoltVolley = !menuStagger && blueTeam && s.usesVolleyPrimaryFire();
        boolean dogfightRole = isDogfightRole(s.role);

        // Aim all turrets first so we can coordinate synchronized beam-bolt volleys with a single readiness check.
        for (Turret t : s.turrets) {
            if (t == null) continue;
            if (t.kind == Turret.Kind.GUN) {
                if (Turret.usesCiwsPelletsAgainst(s, t, target)) {
                    t.aimAtLead(dt, s, target, Turret.effectiveInterceptorProjectileSpeed(s, t));
                } else if (s.faction == Faction.TEAM_C) {
                    // Directed-energy guns should bias direct tracking, not projectile lead.
                    t.aimAt(dt, s, target);
                } else {
                    t.aimAtLead(dt, s, target, Turret.effectiveGunProjectileSpeed(s, t));
                }
            } else {
                t.aimAt(dt, s, target);
            }
        }

        // Rough engagement gating by weapon kind (shared across turrets on the same ship).
        double gunRange = (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) ? 760.0 : 460.0;
        double missileRange = (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) ? 2450.0 : 1780.0;
        gunRange = gunRange * gunRangeRoleMul(s.role) * rangeMul;
        missileRange = missileRange * missileRangeRoleMul(s.role) * rangeMul;
        double sustainedRange = sustainedEngagementRangeForTarget(ctx, s, target);

        ArrayList<Integer> mainGunIndices = null;
        int mainGunCount = 0;
        if (energyBoltStagger || beamBoltVolley) {
            mainGunIndices = borrowIntScratch();
            for (int i = 0; i < s.turrets.size(); i++) {
                Turret t = s.turrets.get(i);
                if (t == null || t.kind != Turret.Kind.GUN) continue;
                if (Turret.usesCiwsPelletsAgainst(s, t, target)) continue;
                mainGunIndices.add(i);
            }
            mainGunCount = mainGunIndices.size();
        }

        boolean useStagger = energyBoltStagger && mainGunCount > 1;
        boolean useVolley = beamBoltVolley && mainGunCount > 1;

        boolean volleyReady = false;
        if (useVolley) {
            volleyReady = true;
            for (int idx : mainGunIndices) {
                        Turret t = s.turrets.get(idx);
                        double authorityRange = gunFireAuthorityRange(ctx, s, t, target, gunRange);
                        if (!isGunTurretReadyToFire(s, t, target, dist, authorityRange, dogfightRole, objective, killConfirm, overkillLikely, targetHull)) {
                            volleyReady = false;
                            break;
                        }
            }
        }

        int selectedGunIndex = -1;
        int selectedGunNextCursor = -1;
        if (useStagger && s.primaryGunStaggerTimer <= 0.0) {
            int start = Math.floorMod(s.primaryGunStaggerCursor, mainGunCount);
            for (int i = 0; i < mainGunCount; i++) {
                int ord = (start + i) % mainGunCount;
                int idx = mainGunIndices.get(ord);
                Turret t = s.turrets.get(idx);
                double authorityRange = gunFireAuthorityRange(ctx, s, t, target, gunRange);
                if (!isGunTurretReadyToFire(s, t, target, dist, authorityRange, dogfightRole, objective, killConfirm, overkillLikely, targetHull)) {
                    continue;
                }
                selectedGunIndex = idx;
                selectedGunNextCursor = ord + 1;
                break;
            }
        }

        boolean staggerFired = false;
        try {
            for (int i = 0; i < s.turrets.size(); i++) {
                Turret t = s.turrets.get(i);
                if (t == null) continue;

                if (t.kind == Turret.Kind.MISSILE) {
                    Ship missileTarget = resolveMissileTarget(ctx, s, t, target, missileRange);
                    if (!isAlive(missileTarget)) continue;
                    double missileDist = Math.hypot(missileTarget.x - s.x, missileTarget.y - s.y);
                    Turret.MissileRole missileRole = (t.missileRole == null)
                            ? Turret.MissileRole.ANTI_MEDIUM : t.missileRole;
                    double allowedMissileRange = (missileRole == Turret.MissileRole.INTERCEPT)
                            ? missileRangeForTurret(t, missileRange)
                            : STANDARD_PROSECUTION_RANGE;
                    if (missileDist > allowedMissileRange) continue;
                    if (!t.canFire()) continue;

                    double wx = t.worldX(s);
                    double wy = t.worldY(s);
                    double desired = Math.atan2(missileTarget.y - wy, missileTarget.x - wx);
                    double delta = Math.abs(MathUtil.normalizeAngle(desired - t.angle));
                    double missileTolerance = (t.missileRole == Turret.MissileRole.INTERCEPT)
                            ? Math.toRadians(46)
                            : Math.toRadians(28);
                    if (delta > missileTolerance) continue;

                    if (!shouldFireMissileWithDiscipline(
                            ctx, s, missileTarget, missileDist, confidence, objective,
                            isKillConfirmActive(missileTarget), isOverkillLikely(ctx, s, missileTarget))) {
                        continue;
                    }

                    Projectile p = t.fire(s, missileTarget, dt);
                    if (p != null) {
                        ctx.projectiles.add(p);
                        firedCount++;
                    }
                    continue;
                }

                // --- GUN turrets ---
                boolean ciwsStyle = Turret.usesCiwsPelletsAgainst(s, t, target);
                double allowedGunRange = gunFireAuthorityRange(ctx, s, t, target, gunRange);
                if (dist > allowedGunRange) continue;
                if (!ciwsStyle) {
                    if (useVolley && !volleyReady) continue;
                    if (useStagger) {
                        if (selectedGunIndex < 0 || i != selectedGunIndex) continue;
                    }
                }

                if (!t.canFire()) continue;

                double wx = t.worldX(s);
                double wy = t.worldY(s);
                double desired = Math.atan2(target.y - wy, target.x - wx);
                double delta = Math.abs(MathUtil.normalizeAngle(desired - t.angle));
                double tol = ciwsStyle ? Math.toRadians(24) : (dogfightRole ? Math.toRadians(24) : Math.toRadians(14));
                if (delta > tol) continue;

                if (!ciwsStyle && (killConfirm || overkillLikely) && objective != SquadObjective.INTERCEPT) {
                    // Preserve gun cycles when target is already collapsing unless we're in dedicated intercept duty.
                    if (dist > 280.0 && targetHull < 0.18) continue;
                }

                Projectile p = t.fire(s, target, dt);
                if (p != null) {
                    ctx.projectiles.add(p);
                    firedCount++;
                    if (useStagger && !staggerFired) {
                        staggerFired = true;
                        s.primaryGunStaggerCursor = selectedGunNextCursor;
                        s.primaryGunStaggerTimer = Ship.ENERGY_BOLT_BARREL_STAGGER_INTERVAL_SECONDS;
                    }
                }
            }
        } finally {
            if (mainGunIndices != null) releaseIntScratch(mainGunIndices);
        }
            return firedCount;
        } finally {
            shipCombatFireNs += System.nanoTime() - fireStart;
        }
    }

    private static Ship resolveMissileTarget(GameContext ctx, Ship shooter, Turret turret, Ship fallback, double baseMissileRange) {
        if (ctx == null || shooter == null || turret == null) return fallback;
        Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
        if (role == Turret.MissileRole.INTERCEPT) {
            return TargetingSystem.findClosestHostileSmallCraft(ctx, shooter, shooter.x, shooter.y, missileRangeForTurret(turret, baseMissileRange));
        }
        return fallback;
    }

    private static double missileRangeForTurret(GameContext ctx, Ship shooter, Turret turret, double baseMissileRange) {
        return missileRangeForTurret(turret, baseMissileRange);
    }

    private static double missileRangeForTurret(Turret turret, double baseMissileRange) {
        if (turret == null) return baseMissileRange;
        Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
        if (role != Turret.MissileRole.INTERCEPT) return STANDARD_PROSECUTION_RANGE;
        return switch (role) {
            case ANTI_HEAVY -> Math.max(baseMissileRange, baseMissileRange * 1.44);
            case ANTI_LIGHT -> Math.max(baseMissileRange, baseMissileRange * 3.5);
            case ANTI_MEDIUM -> baseMissileRange;
            case INTERCEPT -> Math.min(baseMissileRange, 820.0);
        };
    }

    private static void maybeFireAutonomousInterceptMissiles(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null || ship.faction == null || !ship.alive || ship.dying) return;
        double baseMissileRange = ((ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) ? 1850.0 : 1300.0)
                * missileRangeRoleMul(ship.role) * CampaignSystem.targetingRangeMul(ctx);
        for (Turret turret : ship.turrets) {
            if (turret == null || turret.kind != Turret.Kind.MISSILE) continue;
            Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
            if (role != Turret.MissileRole.INTERCEPT) continue;
            if (!turret.canFire()) continue;

            double range = missileRangeForTurret(turret, baseMissileRange);
            Ship target = TargetingSystem.findClosestHostileSmallCraft(ctx, ship, ship.x, ship.y, range);
            if (!isAlive(target)) continue;
            turret.aimAt(dt, ship, target);
            double wx = turret.worldX(ship);
            double wy = turret.worldY(ship);
            double desired = Math.atan2(target.y - wy, target.x - wx);
            double delta = Math.abs(MathUtil.normalizeAngle(desired - turret.angle));
            if (delta > Math.toRadians(54.0)) continue;

            Projectile p = turret.fire(ship, target, dt);
            if (p != null) {
                ctx.projectiles.add(p);
            }
        }
    }

    private static void adaptMissileRolesToThreats(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null || ship.faction == null || !ship.alive || ship.dying) return;
        boolean dedicatedAaHull = switch (ship.role) {
            case CIWS_CORVETTE, PD_CRAFT, PICKET, PATROL -> true;
            default -> false;
        };
        boolean smallCraftNearby = hostileSmallCraftNear(ctx, ship, 1320.0);
        Turret.MissileRole desired = dedicatedAaHull && incomingMissileThreatNear(ctx, ship, 920.0)
                ? Turret.MissileRole.INTERCEPT : Turret.MissileRole.ANTI_LIGHT;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.kind == Turret.Kind.MISSILE) {
                if (dedicatedAaHull || (smallCraftNearby && turret.missileRole != Turret.MissileRole.INTERCEPT)) {
                    turret.missileRole = desired;
                }
            }
        }
    }

    private static boolean hostileSmallCraftNear(GameContext ctx, Ship ship, double radius) {
        if (ctx == null || ship == null || ship.faction == null || radius <= 0.0) return false;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(ship.faction, ship.x, ship.y, radius, nearby);
            for (Ship hostile : nearby) {
                if (isAlive(hostile) && hostile.isSmallCraft()) return true;
            }
            return false;
        } finally {
            releaseShipScratch(nearby);
        }
    }

    private static boolean isGunTurretReadyToFire(Ship host, Turret t, Ship target, double dist, double gunRange,
                                                  boolean dogfightRole, SquadObjective objective,
                                                  boolean killConfirm, boolean overkillLikely, double targetHull) {
        if (host == null || t == null || target == null) return false;
        if (dist > Math.max(gunRange, effectiveGunRangeForTarget(host, t, target, gunRange))) return false;
        if (!t.canFire()) return false;

        double wx = t.worldX(host);
        double wy = t.worldY(host);
        double desired = Math.atan2(target.y - wy, target.x - wx);
        double delta = Math.abs(MathUtil.normalizeAngle(desired - t.angle));
        double tol = dogfightRole ? Math.toRadians(24) : Math.toRadians(14);
        if (delta > tol) return false;

        if ((killConfirm || overkillLikely) && objective != SquadObjective.INTERCEPT) {
            if (dist > 280.0 && targetHull < 0.18) return false;
        }
        return true;
    }

    private static ArrayList<Ship> borrowShipScratch() {
        ScratchLists scratch = SCRATCH.get();
        ArrayList<Ship> list = scratch.shipLists.pollFirst();
        if (list == null) return new ArrayList<>(96);
        list.clear();
        return list;
    }

    private static void releaseShipScratch(ArrayList<Ship> list) {
        if (list == null) return;
        list.clear();
        SCRATCH.get().shipLists.offerFirst(list);
    }

    private static ArrayList<Missile> borrowMissileScratch() {
        ScratchLists scratch = SCRATCH.get();
        ArrayList<Missile> list = scratch.missileLists.pollFirst();
        if (list == null) return new ArrayList<>(48);
        list.clear();
        return list;
    }

    private static void releaseMissileScratch(ArrayList<Missile> list) {
        if (list == null) return;
        list.clear();
        SCRATCH.get().missileLists.offerFirst(list);
    }

    private static ArrayList<Integer> borrowIntScratch() {
        ScratchLists scratch = SCRATCH.get();
        ArrayList<Integer> list = scratch.intLists.pollFirst();
        if (list == null) return new ArrayList<>(8);
        list.clear();
        return list;
    }

    private static void releaseIntScratch(ArrayList<Integer> list) {
        if (list == null) return;
        list.clear();
        SCRATCH.get().intLists.offerFirst(list);
    }

    private static java.util.HashSet<Integer> borrowIntSetScratch() {
        ScratchLists scratch = SCRATCH.get();
        java.util.HashSet<Integer> set = scratch.intSets.pollFirst();
        if (set == null) return new java.util.HashSet<>(32);
        set.clear();
        return set;
    }

    private static void releaseIntSetScratch(java.util.HashSet<Integer> set) {
        if (set == null) return;
        set.clear();
        SCRATCH.get().intSets.offerFirst(set);
    }

    private static <V> void syncFleetMap(Map<Integer, V> destination, Map<Integer, V> source) {
        FleetPresentationSync.syncMap(destination, source);
    }


    static double effectiveGunRangeForTarget(Ship host, Turret turret, Ship target, double baseGunRange) {
        if (host == null || turret == null || target == null) return Math.max(0.0, baseGunRange);
        if (!Turret.usesCiwsPelletsAgainst(host, turret, target)) return Math.max(0.0, baseGunRange);
        double ciwsEnvelope = Math.max(0.0, host.effectiveCiwsRange());
        int pelletLife = Math.max(8, Math.min(turret.bulletLife, host.ciwsPelletLife > 0 ? host.ciwsPelletLife : turret.bulletLife));
        double pelletReach = Turret.effectiveInterceptorProjectileSpeed(host, turret) * GameContext.DT * pelletLife;
        double practicalRange = Math.min(ciwsEnvelope, pelletReach * 0.92);
        return Math.max(host.radius + target.radius + 24.0, Math.min(Math.max(0.0, baseGunRange), practicalRange));
    }

    static double gunFireAuthorityRange(GameContext ctx, Ship host, Turret turret, Ship target, double baseGunRange) {
        double weaponRange = effectiveGunRangeForTarget(host, turret, target, baseGunRange);
        if (host == null || turret == null || target == null) return weaponRange;
        if (Turret.usesCiwsPelletsAgainst(host, turret, target)) return weaponRange;
        return Math.max(weaponRange, STANDARD_PROSECUTION_RANGE);
    }

    static double greenMainGunAuthorityRange(Ship host, Ship target, double baseGunRange) {
        double range = Math.max(0.0, baseGunRange) * 0.58;
        double floor = (host == null || target == null) ? 260.0 : host.radius + target.radius + 190.0;
        return Math.max(STANDARD_PROSECUTION_RANGE, Math.max(floor, range));
    }

    static double effectivePrimaryGunRangeAgainstTarget(Ship host, Ship target, double baseGunRange) {
        if (host == null || target == null || host.turrets == null) return Math.max(0.0, baseGunRange);
        double best = 0.0;
        for (Turret turret : host.turrets) {
            if (turret == null || turret.kind != Turret.Kind.GUN) continue;
            best = Math.max(best, effectiveGunRangeForTarget(host, turret, target, baseGunRange));
        }
        return (best > 0.0) ? best : Math.max(0.0, baseGunRange);
    }

    private static double openingMissileStandoffRange(GameContext ctx, Ship host, Ship target) {
        if (!isAlive(host) || !isAlive(target) || host.turrets == null) return 0.0;
        if (host.aiMissileStandoffTimer <= 0.0 || host.aiMissileStandoffTargetId != target.id) return 0.0;
        double rangeMul = CampaignSystem.targetingRangeMul(ctx);
        double baseMissileRange = ((host.role == ShipRole.BASE || host.role == ShipRole.STATIC_TURRET) ? 2450.0 : 1780.0)
                * missileRangeRoleMul(host.role) * rangeMul;
        double bestMissileRange = 0.0;
        for (Turret turret : host.turrets) {
            if (!isOffensiveMissileTurret(turret)) continue;
            bestMissileRange = Math.max(bestMissileRange, missileRangeForTurret(turret, baseMissileRange));
        }
        if (bestMissileRange <= 0.0) return 0.0;

        double baseGunRange = ((host.role == ShipRole.BASE || host.role == ShipRole.STATIC_TURRET) ? 760.0 : 460.0)
                * gunRangeRoleMul(host.role) * rangeMul;
        double gunRange = effectivePrimaryGunRangeAgainstTarget(host, target, baseGunRange);
        double preferred = preferredRange(host);
        double floor = Math.max(preferred * 1.18, gunRange + Math.max(180.0, host.radius + target.radius + 80.0));
        double desired = Math.max(floor, bestMissileRange * 0.68);
        return Math.min(bestMissileRange * 0.82, desired);
    }

    private static boolean hasOffensiveMissileTurret(Ship ship) {
        if (ship == null || ship.turrets == null) return false;
        for (Turret turret : ship.turrets) {
            if (isOffensiveMissileTurret(turret)) return true;
        }
        return false;
    }

    private static boolean isOffensiveMissileTurret(Turret turret) {
        if (turret == null || turret.kind != Turret.Kind.MISSILE) return false;
        Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
        return role != Turret.MissileRole.INTERCEPT;
    }

    private static double openingMissileStandoffSeconds(Ship ship) {
        if (ship == null) return 0.0;
        return switch (ship.role) {
            case MISSILE_BOAT, ARTILLERY_SHIP, BOMBER -> 7.0;
            case CARRIER, DRONE_CARRIER, BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 6.0;
            default -> 5.0;
        };
    }

    static double sustainedEngagementRangeForTarget(GameContext ctx, Ship observer, Ship target) {
        if (observer == null || target == null) return 0.0;
        double range = TargetingSystem.detectionRangeForObserver(observer, target);
        if (ctx != null) range *= CampaignSystem.targetingRangeMul(ctx);
        if (ctx != null
                && observer.faction != null
                && target.faction != null
                && isFriendlyToPlayer(ctx, observer)
                && !observer.faction.isFriendlyTo(target.faction)) {
            range = Math.max(range, PLAYER_FLEET_PROSECUTION_RANGE);
        }
        if (standardProsecutionContactAvailable(observer, target)) {
            range = Math.max(range, STANDARD_PROSECUTION_RANGE);
        }
        if (redFleetProsecutionContactAvailable(ctx, observer, target)) {
            range = Math.max(range, RED_FLEET_PROSECUTION_RANGE);
        }
        return Math.max(0.0, range);
    }

    private static Turret firstGunTurret(Ship ship) {
        if (ship == null || ship.turrets == null) return null;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.kind == Turret.Kind.GUN) return turret;
        }
        return null;
    }

    private static void updateEngagementMemory(Ship s, Ship target, double dt, double dist, double range,
                                               boolean firedNow, SquadObjective objective) {
        if (s == null) return;
        if (!isAlive(target)) {
            s.aiNoFireTimer = 0.0;
            clearTargetCommitment(s);
            return;
        }
        s.aiLastEngagementX = target.x;
        s.aiLastEngagementY = target.y;
        if (dist <= Math.max(320.0, range * 1.35)) {
            commitToTarget(s, target, targetCommitDuration(s, target, objective));
        }
        if (firedNow) {
            s.aiNoFireTimer = Math.max(0.0, s.aiNoFireTimer - dt * 2.2);
            commitToTarget(s, target, targetCommitDuration(s, target, objective) + 1.4);
            return;
        }
        if (objective == SquadObjective.RESERVE) {
            s.aiNoFireTimer = Math.max(0.0, s.aiNoFireTimer - dt);
            return;
        }
        if (dist <= Math.max(240.0, range * 1.2)) {
            s.aiNoFireTimer += Math.max(0.0, dt);
            if (s.aiNoFireTimer >= 1.0) {
                s.aiBadApproachTimer = Math.max(s.aiBadApproachTimer, 2.4);
                s.aiBadApproachAngle = Math.atan2(target.y - s.y, target.x - s.x);
                s.aiNoFireTimer = 0.0;
            }
        } else {
            s.aiNoFireTimer = Math.max(0.0, s.aiNoFireTimer - dt * 0.7);
        }
    }

    private static boolean shouldFireMissileWithDiscipline(GameContext ctx, Ship shooter, Ship target, double dist,
                                                           double confidence, SquadObjective objective,
                                                           boolean killConfirm, boolean overkillLikely) {
        if (!isAlive(shooter) || !isAlive(target)) return false;
        double targetHull = hullFrac(target);
        double minConfidence = 0.40;
        if (shooter.role == ShipRole.MISSILE_BOAT) minConfidence = 0.56;
        else if (isSupportRole(shooter.role)) minConfidence = 0.52;
        if (objective == SquadObjective.RESERVE) minConfidence += 0.18;
        if (objective == SquadObjective.INTERCEPT) minConfidence -= 0.06;
        if (dist > preferredRange(shooter) * 1.15) minConfidence += 0.08;
        if (confidence < minConfidence) return false;
        if (killConfirm && targetHull < 0.22) return false;
        if (overkillLikely && targetHull < 0.32 && objective != SquadObjective.INTERCEPT) return false;
        return true;
    }

    private static boolean applyRoleSpecificAttackMovement(GameContext ctx, Ship s, Ship target, double dt,
                                                           double range, double dist, double speed,
                                                           boolean push, boolean fallBack,
                                                           double approachMul, double orbitMul,
                                                           double aggression, double standoff,
                                                           double orbitDir) {
        if (s == null || target == null) return false;

        if (isAttackRunRole(s.role)) {
            // Screen and strafe at standoff range. (Old attack-pass behavior collapsed fleets into point-blank blobs.)
            double screenRange = range * 1.02;
            double tooClose = range * 0.88;
            double tooFar = range * 1.22;
            if (dist < tooClose || (fallBack && dist < screenRange * 1.02)) {
                retreatFromTarget(ctx, s, target, speed * 1.06, dt);
                return true;
            }
            if (dist > tooFar) {
                moveTowardAttackVector(s, target, speed * 1.05 * approachMul, dt, orbitDir, 160.0);
                return true;
            }
            holdOrbitFacingTarget(s, target, screenRange, speed * 0.78 * orbitMul, dt, orbitDir, 1.18);
            return true;
        }

        if (isBomberStrikeRole(s.role)) {
            double releaseRange = range * 0.96;
            if (dist > releaseRange * 1.10) {
                moveTowardAttackVector(s, target, speed * 0.98 * approachMul, dt, orbitDir, 150.0);
                return true;
            }
            if (fallBack || dist < releaseRange * 0.74 || s.aiNoFireTimer > 0.85) {
                retreatFromTarget(ctx, s, target, speed * 1.04, dt);
                return true;
            }
            holdOrbitFacingTarget(s, target, releaseRange, speed * 0.76 * orbitMul, dt, orbitDir, 1.18);
            return true;
        }

        if (isKitingRole(s.role)) {
            double kiteRange = range * (1.04 + Math.max(0.0, standoff) * 0.08);
            if (dist < kiteRange * 0.92 || (fallBack && dist < kiteRange * 1.08)) {
                retreatFromTarget(ctx, s, target, speed * (1.02 + Math.max(0.0, standoff) * 0.06), dt);
                return true;
            }
            if (dist > kiteRange * 1.20) {
                moveTowardFacingTarget(s, target, speed * 0.94 * approachMul, dt, 1.10);
                return true;
            }
            holdOrbitFacingTarget(s, target, kiteRange, speed * 0.72 * orbitMul, dt, orbitDir, 1.12);
            return true;
        }

        if (isBrawlerRole(s.role)) {
            // "Brawlers" still commit slightly, but never to point-blank range.
            double commitRange = range * (push ? 0.96 : 1.00);
            double brawlSpeed = speed * (1.00 + Math.max(0.0, aggression) * 0.06);
            if (dist > commitRange * 1.06) {
                moveTowardFacingTarget(s, target, brawlSpeed * approachMul, dt, 1.20);
                return true;
            }
            if (fallBack && dist < commitRange * 0.92) {
                retreatFromTarget(ctx, s, target, speed * 0.96, dt);
                return true;
            }
            holdOrbitFacingTarget(s, target, commitRange, speed * 0.74 * orbitMul, dt, orbitDir, 1.16);
            return true;
        }

        if (isLineShipRole(s.role)) {
            double lineRange = range * 0.98;
            if (dist > lineRange * 1.14) {
                moveTowardFacingTarget(s, target, speed * 0.92 * approachMul, dt, 1.06);
                return true;
            }
            if (fallBack && dist < lineRange * 0.88) {
                retreatFromTarget(ctx, s, target, speed * 0.92, dt);
                return true;
            }
            holdOrbitFacingTarget(s, target, lineRange, speed * 0.66 * orbitMul, dt, orbitDir, 1.06);
            return true;
        }

        return false;
    }

    private static double preferredRange(Ship s) {
        if (s == null) return 380;
        Double titanRange = titanPreferredRange(s.role);
        if (titanRange != null) return titanRange;
        // Keep roles feeling different
        return switch (s.role) {
            case FIGHTER, DRONE -> 520;
            case PD_CRAFT -> 640;
            case BOMBER -> 860;
            case PATROL -> 560;
            case PICKET -> 860;
            case FRIGATE -> 660;
            case ARTILLERY_SHIP -> 980;
            case CIWS_CORVETTE -> 620;
            case MISSILE_BOAT -> 1180;
            case LIGHT_CRUISER -> 760;
            case MEDIUM_CRUISER, CRUISER -> 840;
            case BATTLECRUISER -> 820;
            case BATTLESHIP -> 940;
            case DREADNOUGHT -> 1020;
            case SUPERSHIP -> 1100;
            case CARRIER -> 1200;
            case DRONE_CARRIER -> 1120;
            case TRANSPORT, HAULER, MINER -> 1100;
            case STEALTH_SHIP -> 720;
            default -> 520; // fallback for any roles you add later
        };

    }

    private static double roleAggressionBias(ShipRole role) {
        if (role == null) return 0.0;
        Double titanBias = titanAggressionBias(role);
        if (titanBias != null) return titanBias;
        return switch (role) {
            case DRONE -> 0.65;
            case FIGHTER -> 0.55;
            case STEALTH_SHIP -> 0.55;
            case PATROL -> 0.45;
            case CIWS_CORVETTE -> 0.35;
            case BATTLECRUISER -> 0.30;
            case FRIGATE -> 0.10;
            case PD_CRAFT -> 0.10;
            case LIGHT_CRUISER -> 0.08;
            case MEDIUM_CRUISER, CRUISER -> 0.00;
            case PICKET -> -0.05;
            case ARTILLERY_SHIP -> -0.18;
            case BATTLESHIP -> -0.12;
            case DRONE_CARRIER -> -0.16;
            case DREADNOUGHT -> -0.18;
            case SUPERSHIP -> -0.22;
            case MISSILE_BOAT -> -0.25;
            case CARRIER -> -0.28;
            case BOMBER -> 0.15;
            case TRANSPORT, HAULER, MINER -> -0.35;
            default -> 0.0;
        };
    }

    private static double roleStandoffBias(ShipRole role) {
        if (role == null) return 0.0;
        Double titanBias = titanStandoffBias(role);
        if (titanBias != null) return titanBias;
        return switch (role) {
            case ARTILLERY_SHIP -> 0.68;
            case MISSILE_BOAT -> 0.75;
            case TRANSPORT, HAULER, MINER -> 0.70;
            case CARRIER -> 0.62;
            case SUPERSHIP -> 0.48;
            case DRONE_CARRIER -> 0.46;
            case DREADNOUGHT -> 0.40;
            case BOMBER -> 0.40;
            case PICKET -> 0.35;
            case BATTLESHIP -> 0.32;
            case MEDIUM_CRUISER, CRUISER -> 0.18;
            case PD_CRAFT -> 0.15;
            case LIGHT_CRUISER -> 0.12;
            case FRIGATE -> 0.00;
            case STEALTH_SHIP -> -0.05;
            case BATTLECRUISER -> -0.12;
            case CIWS_CORVETTE -> -0.20;
            case PATROL -> -0.25;
            case FIGHTER -> -0.30;
            case DRONE -> -0.35;
            default -> 0.0;
        };
    }

    private static double roleApproachSpeedMul(ShipRole role) {
        if (role == null) return 1.0;
        Double titanMul = titanApproachSpeedMul(role);
        if (titanMul != null) return titanMul;
        return switch (role) {
            case DRONE -> 1.22;
            case FIGHTER -> 1.18;
            case STEALTH_SHIP -> 1.15;
            case CIWS_CORVETTE -> 1.14;
            case PATROL -> 1.12;
            case BATTLECRUISER -> 1.06;
            case PD_CRAFT -> 1.05;
            case FRIGATE, LIGHT_CRUISER -> 1.00;
            case MEDIUM_CRUISER, CRUISER -> 0.98;
            case PICKET -> 0.95;
            case ARTILLERY_SHIP -> 0.90;
            case DRONE_CARRIER -> 0.92;
            case MISSILE_BOAT, BATTLESHIP -> 0.92;
            case DREADNOUGHT -> 0.88;
            case CARRIER -> 0.86;
            case SUPERSHIP -> 0.82;
            case TRANSPORT, HAULER, MINER -> 0.80;
            case BOMBER -> 1.00;
            default -> 1.0;
        };
    }

    private static double roleOrbitSpeedMul(ShipRole role) {
        if (role == null) return 1.0;
        Double titanMul = titanOrbitSpeedMul(role);
        if (titanMul != null) return titanMul;
        return switch (role) {
            case FIGHTER -> 1.20;
            case DRONE -> 1.18;
            case STEALTH_SHIP -> 1.12;
            case CIWS_CORVETTE -> 1.10;
            case PATROL, PD_CRAFT -> 1.05;
            case FRIGATE -> 1.00;
            case BATTLECRUISER -> 1.00;
            case PICKET, LIGHT_CRUISER -> 0.98;
            case MEDIUM_CRUISER, CRUISER -> 0.95;
            case BOMBER -> 0.94;
            case ARTILLERY_SHIP -> 0.90;
            case MISSILE_BOAT -> 0.88;
            case BATTLESHIP -> 0.86;
            case DRONE_CARRIER -> 0.84;
            case DREADNOUGHT -> 0.80;
            case CARRIER -> 0.78;
            case SUPERSHIP -> 0.74;
            case TRANSPORT, HAULER, MINER -> 0.70;
            default -> 1.0;
        };
    }

    private static double gunRangeRoleMul(ShipRole role) {
        if (role == null) return 1.0;
        Double titanMul = titanGunRangeMul(role);
        if (titanMul != null) return titanMul;
        return switch (role) {
            case PICKET -> 1.44;
            case ARTILLERY_SHIP -> 1.46;
            case BATTLESHIP -> 1.38;
            case DREADNOUGHT -> 1.42;
            case SUPERSHIP -> 1.48;
            case BATTLECRUISER -> 1.22;
            case LIGHT_CRUISER -> 1.16;
            case MEDIUM_CRUISER, CRUISER -> 1.20;
            case CARRIER, DRONE_CARRIER -> 1.08;
            case FRIGATE -> 1.04;
            case MISSILE_BOAT -> 0.96;
            case CIWS_CORVETTE -> 0.90;
            case PATROL -> 0.92;
            case STEALTH_SHIP -> 0.94;
            case FIGHTER, DRONE, PD_CRAFT, BOMBER -> 0.82;
            case TRANSPORT, HAULER, MINER -> 0.78;
            default -> 1.0;
        };
    }

    private static double missileRangeRoleMul(ShipRole role) {
        if (role == null) return 1.0;
        Double titanMul = titanMissileRangeMul(role);
        if (titanMul != null) return titanMul;
        return switch (role) {
            case MISSILE_BOAT -> 1.42;
            case BOMBER -> 1.26;
            case CARRIER -> 1.22;
            case DRONE_CARRIER -> 1.18;
            case DREADNOUGHT -> 1.22;
            case SUPERSHIP -> 1.28;
            case BATTLESHIP -> 1.18;
            case BATTLECRUISER -> 1.12;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 1.08;
            case STEALTH_SHIP -> 1.04;
            case ARTILLERY_SHIP -> 1.02;
            case FRIGATE -> 1.02;
            case CIWS_CORVETTE -> 0.92;
            case PICKET, PATROL -> 0.90;
            case FIGHTER, DRONE, PD_CRAFT -> 0.80;
            case TRANSPORT, HAULER, MINER -> 0.86;
            default -> 1.0;
        };
    }

    private static Double titanFlagshipWeight(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case MOTHERSHIP -> 18.0;
            case HYPERWEAPON_TITAN, ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 17.0;
            case MOBILE_STATION_TITAN, CARRIER_SUPPORT_TITAN, BULWARK_TITAN, SHIELD_BASTION_TITAN -> 16.0;
            case TRANSPORT_TITAN, VANGUARD_TITAN, INTERDICTION_TITAN, COMMAND_INTEL_TITAN,
                    BOARDING_RECOVERY_TITAN, ARTILLERY_TITAN, FLEET_TELEPORTER_TITAN -> 15.0;
            default -> null;
        };
    }

    private static Double titanThreatPriority(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case MOTHERSHIP -> 3.35;
            case HYPERWEAPON_TITAN -> 3.25;
            case ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN, BULWARK_TITAN, SHIELD_BASTION_TITAN -> 3.05;
            case MOBILE_STATION_TITAN, CARRIER_SUPPORT_TITAN, COMMAND_INTEL_TITAN, FLEET_TELEPORTER_TITAN -> 2.95;
            case TRANSPORT_TITAN, VANGUARD_TITAN, INTERDICTION_TITAN, BOARDING_RECOVERY_TITAN, ARTILLERY_TITAN -> 2.85;
            default -> null;
        };
    }

    private static Double titanPreferredRange(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case TRANSPORT_TITAN -> 1080.0;
            case BULWARK_TITAN -> 900.0;
            case CARRIER_SUPPORT_TITAN -> 1180.0;
            case VANGUARD_TITAN -> 860.0;
            case INTERDICTION_TITAN -> 920.0;
            case COMMAND_INTEL_TITAN -> 1080.0;
            case BOARDING_RECOVERY_TITAN -> 920.0;
            case ARTILLERY_TITAN -> 1320.0;
            case SHIELD_BASTION_TITAN -> 980.0;
            case FLEET_TELEPORTER_TITAN -> 1120.0;
            case ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 1220.0;
            case MOBILE_STATION_TITAN -> 1180.0;
            case HYPERWEAPON_TITAN -> 1380.0;
            case MOTHERSHIP -> 1280.0;
            default -> null;
        };
    }

    private static Double titanAggressionBias(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case VANGUARD_TITAN -> 0.22;
            case BOARDING_RECOVERY_TITAN, INTERDICTION_TITAN -> 0.10;
            case BULWARK_TITAN, ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 0.04;
            case COMMAND_INTEL_TITAN, TRANSPORT_TITAN -> -0.08;
            case ARTILLERY_TITAN, SHIELD_BASTION_TITAN -> -0.18;
            case CARRIER_SUPPORT_TITAN, MOBILE_STATION_TITAN, MOTHERSHIP -> -0.24;
            case FLEET_TELEPORTER_TITAN -> -0.12;
            case HYPERWEAPON_TITAN -> -0.28;
            default -> null;
        };
    }

    private static Double titanStandoffBias(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case TRANSPORT_TITAN, CARRIER_SUPPORT_TITAN, MOBILE_STATION_TITAN, MOTHERSHIP -> 0.66;
            case COMMAND_INTEL_TITAN, ARTILLERY_TITAN, HYPERWEAPON_TITAN -> 0.62;
            case SHIELD_BASTION_TITAN -> 0.54;
            case FLEET_TELEPORTER_TITAN, ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 0.50;
            case BULWARK_TITAN -> 0.22;
            case VANGUARD_TITAN -> -0.08;
            case INTERDICTION_TITAN, BOARDING_RECOVERY_TITAN -> 0.08;
            default -> null;
        };
    }

    private static Double titanApproachSpeedMul(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case VANGUARD_TITAN, FLEET_TELEPORTER_TITAN -> 0.96;
            case INTERDICTION_TITAN, BOARDING_RECOVERY_TITAN -> 0.92;
            case BULWARK_TITAN, SHIELD_BASTION_TITAN -> 0.82;
            case ARTILLERY_TITAN, HYPERWEAPON_TITAN -> 0.78;
            case MOBILE_STATION_TITAN, MOTHERSHIP -> 0.72;
            case TRANSPORT_TITAN, CARRIER_SUPPORT_TITAN, COMMAND_INTEL_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 0.84;
            default -> null;
        };
    }

    private static Double titanOrbitSpeedMul(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case VANGUARD_TITAN, FLEET_TELEPORTER_TITAN -> 0.90;
            case INTERDICTION_TITAN, BOARDING_RECOVERY_TITAN -> 0.86;
            case BULWARK_TITAN, SHIELD_BASTION_TITAN -> 0.76;
            case ARTILLERY_TITAN, HYPERWEAPON_TITAN -> 0.70;
            case MOBILE_STATION_TITAN, MOTHERSHIP -> 0.62;
            case TRANSPORT_TITAN, CARRIER_SUPPORT_TITAN, COMMAND_INTEL_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 0.74;
            default -> null;
        };
    }

    private static Double titanGunRangeMul(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case ARTILLERY_TITAN -> 1.48;
            case HYPERWEAPON_TITAN -> 1.44;
            case ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN, MOTHERSHIP -> 1.30;
            case BULWARK_TITAN, SHIELD_BASTION_TITAN -> 1.24;
            case COMMAND_INTEL_TITAN -> 1.22;
            case VANGUARD_TITAN -> 1.18;
            case INTERDICTION_TITAN, BOARDING_RECOVERY_TITAN, FLEET_TELEPORTER_TITAN -> 1.10;
            case CARRIER_SUPPORT_TITAN, MOBILE_STATION_TITAN -> 1.04;
            case TRANSPORT_TITAN -> 0.92;
            default -> null;
        };
    }

    private static Double titanMissileRangeMul(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case HYPERWEAPON_TITAN -> 1.26;
            case ARTILLERY_TITAN, ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN -> 1.18;
            case INTERDICTION_TITAN, BOARDING_RECOVERY_TITAN, FLEET_TELEPORTER_TITAN -> 1.14;
            case CARRIER_SUPPORT_TITAN, MOBILE_STATION_TITAN, MOTHERSHIP -> 1.12;
            case COMMAND_INTEL_TITAN -> 1.08;
            case VANGUARD_TITAN -> 1.06;
            case BULWARK_TITAN, SHIELD_BASTION_TITAN -> 1.02;
            case TRANSPORT_TITAN -> 0.94;
            default -> null;
        };
    }

    private static boolean isDogfightRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case FIGHTER, DRONE, BOMBER, PD_CRAFT, PATROL, STEALTH_SHIP -> true;
            default -> false;
        };
    }

    private static boolean isAttackRunRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case FIGHTER, DRONE, PATROL, STEALTH_SHIP -> true;
            default -> false;
        };
    }

    private static boolean isBomberStrikeRole(ShipRole role) {
        return role == ShipRole.BOMBER;
    }

    private static boolean isKitingRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case MISSILE_BOAT, CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, MINER -> true;
            default -> false;
        };
    }

    private static boolean isBrawlerRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case FRIGATE, CIWS_CORVETTE, PD_CRAFT, PICKET -> true;
            default -> false;
        };
    }

    private static boolean isLineShipRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP -> true;
            default -> false;
        };
    }

    private static void moveTowardFacingTarget(Ship s, Ship target, double speedPerSec, double dt, double faceRateMul) {
        if (s == null || target == null) return;
        moveToward(s, target.x, target.y, speedPerSec, dt);
        double aim = Math.atan2(target.y - s.y, target.x - s.x);
        rotateShipTowardAssist(s, aim, dt, maxTurnRateRadPerSec(s) * Math.max(0.2, faceRateMul));
    }

    private static void moveTowardAttackVector(Ship s, Ship target, double speedPerSec, double dt,
                                               double orbitDir, double lateralOffset) {
        if (s == null || target == null) return;
        double dx = target.x - s.x;
        double dy = target.y - s.y;
        double len = Math.hypot(dx, dy) + 1e-9;
        double ux = dx / len;
        double uy = dy / len;
        double sideX = -uy * orbitDir;
        double sideY = ux * orbitDir;
        double tx = target.x + sideX * lateralOffset;
        double ty = target.y + sideY * lateralOffset;
        moveToward(s, tx, ty, speedPerSec, dt);
        rotateShipTowardAssist(s, Math.atan2(target.y - s.y, target.x - s.x), dt, maxTurnRateRadPerSec(s) * 1.18);
    }

    private static void executeAttackPass(Ship s, Ship target, double speedPerSec, double dt,
                                          double orbitDir, double forwardLead, double lateralOffset) {
        if (s == null || target == null) return;
        double dx = target.x - s.x;
        double dy = target.y - s.y;
        double len = Math.hypot(dx, dy) + 1e-9;
        double ux = dx / len;
        double uy = dy / len;
        double sideX = -uy * orbitDir;
        double sideY = ux * orbitDir;
        double tx = target.x + ux * forwardLead + sideX * lateralOffset;
        double ty = target.y + uy * forwardLead + sideY * lateralOffset;
        moveToward(s, tx, ty, speedPerSec, dt);
        rotateShipTowardAssist(s, Math.atan2(target.y - s.y, target.x - s.x), dt, maxTurnRateRadPerSec(s) * 1.24);
    }

    private static void holdOrbitFacingTarget(Ship s, Ship target, double desiredRange, double speedPerSec,
                                              double dt, double dir, double faceRateMul) {
        if (s == null || target == null) return;
        orbit(s, target.x, target.y, desiredRange, speedPerSec, dt, dir);
        rotateShipTowardAssist(s, Math.atan2(target.y - s.y, target.x - s.x), dt,
                maxTurnRateRadPerSec(s) * Math.max(0.2, faceRateMul));
    }

    // --- helpers (dt-safe) ---

    private static void setVelPerSec(Ship s, double vxPerSec, double vyPerSec, double dt) {
        if (s == null) return;
        if (dt <= 0) { s.vx = 0; s.vy = 0; return; }
        boolean thrusting = Math.hypot(vxPerSec, vyPerSec) > 1e-4;
        MovementModel.applyDesiredVelocity(s, vxPerSec, vyPerSec, dt, thrusting);
    }

    private static double maxTurnRateRadPerSec(Ship s) {
        return MovementModel.turnRateRadPerSec(s);
    }

    private static void rotateShipToward(Ship s, double desiredAngle, double dt) {
        if (s == null || dt <= 0.0) return;
        double delta = MathUtil.normalizeAngle(desiredAngle - s.angle);
        double maxDelta = maxTurnRateRadPerSec(s) * dt;
        delta = MathUtil.clamp(delta, -maxDelta, maxDelta);
        s.angle = MathUtil.normalizeAngle(s.angle + delta);
    }

    private static void rotateShipTowardAssist(Ship s, double desiredAngle, double dt, double rateRadPerSec) {
        if (s == null || dt <= 0.0) return;
        double delta = MathUtil.normalizeAngle(desiredAngle - s.angle);
        double maxDelta = Math.max(0.0, rateRadPerSec) * dt;
        delta = MathUtil.clamp(delta, -maxDelta, maxDelta);
        s.angle = MathUtil.normalizeAngle(s.angle + delta);
    }

    private static void moveToward(Ship s, double tx, double ty, double speedPerSec, double dt) {
        speedPerSec = CombatMovement.finiteSpeed(speedPerSec);
        double dx = tx - s.x;
        double dy = ty - s.y;
        double len = Math.sqrt(dx*dx + dy*dy) + 1e-9;
        double vx = (dx/len) * speedPerSec;
        double vy = (dy/len) * speedPerSec;
        setVelPerSec(s, vx, vy, dt);
        rotateShipToward(s, Math.atan2(vy, vx), dt);
    }

    private static void retreatFromTarget(GameContext ctx, Ship s, Ship target, double speedPerSec, double dt) {
        if (s == null || target == null) return;
        double dx = s.x - target.x;
        double dy = s.y - target.y;
        double len = Math.hypot(dx, dy) + 1e-9;
        double ux = dx / len;
        double uy = dy / len;
        double txL = -uy;
        double tyL = ux;
        double txR = uy;
        double tyR = -ux;
        double laneBlend = 0.40;
        double candLx = ux * (1.0 - laneBlend) + txL * laneBlend;
        double candLy = uy * (1.0 - laneBlend) + tyL * laneBlend;
        double candRx = ux * (1.0 - laneBlend) + txR * laneBlend;
        double candRy = uy * (1.0 - laneBlend) + tyR * laneBlend;
        double lLen = Math.hypot(candLx, candLy) + 1e-9;
        double rLen = Math.hypot(candRx, candRy) + 1e-9;
        candLx /= lLen; candLy /= lLen;
        candRx /= rLen; candRy /= rLen;
        double riskL = retreatCorridorRisk(ctx, s, target, candLx, candLy);
        double riskR = retreatCorridorRisk(ctx, s, target, candRx, candRy);
        double dirX = (riskL <= riskR) ? candLx : candRx;
        double dirY = (riskL <= riskR) ? candLy : candRy;
        double tangX = (riskL <= riskR) ? txL : txR;
        double tangY = (riskL <= riskR) ? tyL : tyR;
        double weave = Math.sin(System.nanoTime() * 1e-9 * 3.6 + s.id * 0.21);
        double vx = (dirX * 0.90 + tangX * 0.18 * weave) * speedPerSec;
        double vy = (dirY * 0.90 + tangY * 0.18 * weave) * speedPerSec;

        // Bias retreat back toward friendly cover instead of just running blind.
        Ship base = (ctx == null || s.faction == null) ? null : TeamSystem.getBaseForTeam(ctx, s.faction);
        if (isAlive(base) && base != s) {
            double bdx = base.x - s.x;
            double bdy = base.y - s.y;
            double bl = Math.hypot(bdx, bdy) + 1e-9;
            double pull = (s.faction == Faction.TEAM_C) ? 0.34 : 0.24;
            vx += (bdx / bl) * speedPerSec * pull;
            vy += (bdy / bl) * speedPerSec * pull;
        }

        // If we have a nearby flagship, bias retreat back into fleet cohesion.
        Ship flagship = (ctx == null || ctx.command.fleetCommandShips == null || s.faction == null)
                ? null
                : ctx.command.fleetCommandShips.get(s.faction);
        if (isAlive(flagship) && flagship != s) {
            double fdx = flagship.x - s.x;
            double fdy = flagship.y - s.y;
            double fl = Math.hypot(fdx, fdy) + 1e-9;
            vx += (fdx / fl) * speedPerSec * 0.26;
            vy += (fdy / fl) * speedPerSec * 0.26;
        }
        if (ctx != null && ctx.asteroids != null) {
            Asteroid cover = bestRetreatCoverAsteroid(ctx, s, target);
            if (cover != null) {
                double cdx = cover.x - s.x;
                double cdy = cover.y - s.y;
                double cl = Math.hypot(cdx, cdy) + 1e-9;
                vx += (cdx / cl) * speedPerSec * 0.22;
                vy += (cdy / cl) * speedPerSec * 0.22;
            }
        }
        setVelPerSec(s, vx, vy, dt);
        rotateShipToward(s, Math.atan2(vy, vx), dt);
    }

    private static double retreatCorridorRisk(GameContext ctx, Ship self, Ship primaryThreat, double dirX, double dirY) {
        if (ctx == null || self == null) return 0.0;
        double look = Math.max(260.0, preferredRange(self) * 0.90);
        double px = self.x + dirX * look;
        double py = self.y + dirY * look;
        double risk = 0.0;
        if (isAlive(primaryThreat)) {
            risk += singleThreatArcRisk(primaryThreat, px, py) * 1.25;
        }
        Ship hostileBase = findClosestHostileBase(ctx, self);
        if (isAlive(hostileBase)) {
            double dBase = Math.hypot(px - hostileBase.x, py - hostileBase.y);
            double safe = hostileBase.radius + Math.max(320.0, preferredRange(hostileBase) * 0.78);
            if (dBase < safe) {
                risk += (safe - dBase) / Math.max(1.0, safe) * 2.0;
            }
        }
        for (Ship enemy : ctx.ships) {
            if (!isAlive(enemy) || enemy == primaryThreat) continue;
            if (self.faction != null && enemy.faction != null && self.faction.isFriendlyTo(enemy.faction)) continue;
            double d = Math.hypot(enemy.x - self.x, enemy.y - self.y);
            if (d > 1400.0) continue;
            risk += singleThreatArcRisk(enemy, px, py);
        }
        if (ctx.asteroids != null) {
            for (Asteroid a : ctx.asteroids) {
                if (a == null) continue;
                double d = Math.hypot(a.x - px, a.y - py);
                double safe = self.radius + a.collisionRadius() + 26.0;
                if (d < safe) {
                    risk += (safe - d) / safe * 0.9;
                }
            }
        }
        return risk;
    }

    private static Asteroid bestRetreatCoverAsteroid(GameContext ctx, Ship self, Ship threat) {
        if (ctx == null || self == null || threat == null || ctx.asteroids == null) return null;
        Asteroid best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            double dSelf = Math.hypot(a.x - self.x, a.y - self.y);
            if (dSelf > 420.0) continue;
            double dThreat = Math.hypot(a.x - threat.x, a.y - threat.y);
            if (dThreat >= Math.hypot(self.x - threat.x, self.y - threat.y)) continue;
            double lineDist = pointToSegmentDistance(a.x, a.y, self.x, self.y, threat.x, threat.y);
            double score = Math.max(0.0, 260.0 - lineDist) * 1.2 + Math.max(0.0, 420.0 - dSelf) * 0.35 + a.collisionRadius() * 0.2;
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private static double pointToSegmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double abx = bx - ax;
        double aby = by - ay;
        double denom = abx * abx + aby * aby;
        if (denom <= 1e-9) return Math.hypot(px - ax, py - ay);
        double t = ((px - ax) * abx + (py - ay) * aby) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        double qx = ax + abx * t;
        double qy = ay + aby * t;
        return Math.hypot(px - qx, py - qy);
    }

    private static double singleThreatArcRisk(Ship enemy, double px, double py) {
        if (!isAlive(enemy)) return 0.0;
        double d = Math.hypot(px - enemy.x, py - enemy.y);
        double maxRange = Math.max(320.0, preferredRange(enemy) * 1.45);
        if (d > maxRange) return 0.0;
        double toP = Math.atan2(py - enemy.y, px - enemy.x);
        double facing = Math.abs(MathUtil.normalizeAngle(toP - enemy.angle));
        double arc = Math.toRadians(68.0);
        if (enemy.role == ShipRole.MISSILE_BOAT || enemy.role == ShipRole.BATTLECRUISER || enemy.role == ShipRole.BATTLESHIP) {
            arc = Math.toRadians(78.0);
        }
        double arcFactor = Math.max(0.0, 1.0 - facing / arc);
        double rangeFactor = Math.max(0.0, 1.0 - d / maxRange);
        double weight = 0.45 + roleWeightForFlagship(enemy.role) * 0.05;
        return arcFactor * rangeFactor * weight;
    }

    private static void applyProjectileLaneAvoidance(GameContext ctx, Ship s, double dt) {
        if (ctx == null || s == null || dt <= 0.0) return;
        if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET || s.role == ShipRole.MINER) return;
        if (ctx.projectiles == null || ctx.projectiles.isEmpty()) return;

        double vxPerSec = s.vx / dt;
        double vyPerSec = s.vy / dt;
        double speed = Math.hypot(vxPerSec, vyPerSec);
        if (speed < 16.0) return;

        double dodgeX = 0.0;
        double dodgeY = 0.0;
        double totalWeight = 0.0;

        for (Projectile p : ctx.projectiles) {
            if (p == null || !p.alive) continue;
            if (p.faction == null || (s.faction != null && s.faction.isFriendlyTo(p.faction))) continue;

            double ticksAhead = Math.min(28.0, Math.max(10.0, speed / 8.0));
            double fx = p.x + p.vx * ticksAhead;
            double fy = p.y + p.vy * ticksAhead;
            double dx = s.x - fx;
            double dy = s.y - fy;
            double dist = Math.hypot(dx, dy);
            double hazard = s.radius + Math.max(1.0, p.radius) + 18.0;
            if (dist >= hazard) continue;

            double relX = s.x - p.x;
            double relY = s.y - p.y;
            double closing = relX * p.vx + relY * p.vy;
            if (closing <= 0.0) continue;

            double pvLen = Math.hypot(p.vx, p.vy) + 1e-9;
            double nx = p.vx / pvLen;
            double ny = p.vy / pvLen;
            double sideSigned = relX * (-ny) + relY * nx;
            double side = (sideSigned >= 0.0) ? 1.0 : -1.0;
            double lx = -ny * side;
            double ly = nx * side;

            double w = Math.max(0.05, 1.0 - dist / Math.max(1e-9, hazard));
            if (p instanceof Missile) w *= 1.25;
            dodgeX += lx * w;
            dodgeY += ly * w;
            totalWeight += w;
        }

        if (totalWeight <= 0.01) return;
        double len = Math.hypot(dodgeX, dodgeY) + 1e-9;
        dodgeX /= len;
        dodgeY /= len;

        double blend = Math.max(0.18, Math.min(0.58, 0.20 + totalWeight * 0.14));
        double newVx = vxPerSec * (1.0 - blend) + dodgeX * speed * blend;
        double newVy = vyPerSec * (1.0 - blend) + dodgeY * speed * blend;
        setVelPerSec(s, newVx, newVy, dt);
        rotateShipToward(s, Math.atan2(newVy, newVx), dt);
    }

    private static double observerEWConfidence(GameContext ctx, Ship observer, Ship target, double dist) {
        return observerEWConfidence(ctx, observer, target, dist, null);
    }

    private static double observerEWConfidence(GameContext ctx, Ship observer, Ship target, double dist,
                                               FleetStateBuildCache buildCache) {
        if (observer == null || target == null) return 0.0;
        double sensor = Math.max(0.20, cachedObserverSensorMultiplier(observer, buildCache));
        double sensorNorm = Math.max(0.20, Math.min(1.20, sensor));
        double targetSigMul = cachedTargetSignatureMultiplier(target, buildCache);
        double rangeBudget = TargetingSystem.detectionRangeForObserver(observer, target, sensor, targetSigMul);
        double distConf = Math.max(0.08, Math.min(1.0, 1.0 - dist / Math.max(520.0, rangeBudget)));
        double ewFactor = 1.0;
        double conf = (sensorNorm * 0.62 + distConf * 0.38) * ewFactor;
        return Math.max(0.05, Math.min(1.0, conf));
    }

    private static double cachedObserverSensorMultiplier(Ship observer, FleetStateBuildCache buildCache) {
        if (observer == null) return 1.0;
        if (buildCache == null) return observer.sensorRangeMultiplier();
        Double cached = buildCache.observerSensorMul.get(observer);
        if (cached != null) return cached;
        double sensor = observer.sensorRangeMultiplier();
        buildCache.observerSensorMul.put(observer, sensor);
        return sensor;
    }

    private static double cachedTargetSignatureMultiplier(Ship target, FleetStateBuildCache buildCache) {
        if (target == null) return 1.0;
        if (buildCache == null) return TargetingSystem.targetSignatureMultiplier(target);
        Double cached = buildCache.targetSignatureMul.get(target);
        if (cached != null) return cached;
        double signature = TargetingSystem.targetSignatureMultiplier(target);
        buildCache.targetSignatureMul.put(target, signature);
        return signature;
    }

    private static void updateStealthCloakIntent(GameContext ctx, Ship ship) {
        if (!isAlive(ship) || ctx == null) return;
        if (!ship.isStealth) return;

        double energyFrac = ship.cloakEnergyFrac();
        double preferred = Math.max(260.0, preferredRange(ship));
        double nearestEnemy = nearestDetectableHostileDistance(ctx, ship, Math.max(1400.0, preferred * 2.2));
        boolean missilesClose = incomingMissileThreatNear(ctx, ship, Math.max(420.0, ship.radius * 13.0));
        boolean underPressure = missilesClose
                || ship.cloakThreatTimer > 0.0
                || hullFrac(ship) < 0.72
                || shieldFrac(ship) < 0.40;
        boolean nearFight = nearestEnemy < Math.max(760.0, preferred * 1.45);
        boolean attackRunWindow = nearestEnemy > Math.max(180.0, preferred * 0.55)
                && nearestEnemy < Math.max(980.0, preferred * 1.85);
        boolean conserve = energyFrac < 0.34 && !missilesClose && ship.cloakThreatTimer <= 0.0;

        Ship.CloakControlMode desired = Ship.CloakControlMode.CHARGE;
        if (ship.revealTimer > 0.15) {
            desired = Ship.CloakControlMode.CHARGE;
        } else if (missilesClose) {
            desired = Ship.CloakControlMode.ACTIVE;
        } else if (conserve) {
            desired = Ship.CloakControlMode.CHARGE;
        } else if (underPressure && nearFight) {
            desired = Ship.CloakControlMode.ACTIVE;
        } else if (attackRunWindow && energyFrac >= 0.60) {
            desired = Ship.CloakControlMode.ACTIVE;
        }

        ship.setCloakControlMode(desired);
    }

    private static boolean incomingMissileThreatNear(GameContext ctx, Ship ship, double radius) {
        if (ctx == null || ship == null || radius <= 0.0) return false;
        ArrayList<Missile> missiles = borrowMissileScratch();
        try {
            ctx.entityQuery.collectMissilesNear(ship.x, ship.y, radius, missiles);
            for (Missile missile : missiles) {
                if (missile == null || !missile.alive || missile.faction == null) continue;
                if (ship.faction != null && ship.faction.isFriendlyTo(missile.faction)) continue;
                if (missile.target == ship) return true;
                if (GameMath.dist2(missile.x, missile.y, ship.x, ship.y) <= radius * radius) return true;
            }
            return false;
        } finally {
            releaseMissileScratch(missiles);
        }
    }

    private static double nearestDetectableHostileDistance(GameContext ctx, Ship ship, double radius) {
        if (ctx == null || ship == null || ship.faction == null || radius <= 0.0) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(ship.faction, ship.x, ship.y, radius, nearby);
            for (Ship enemy : nearby) {
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (!TargetingSystem.isDetectableToObserver(ship, enemy)) continue;
                double d = Math.hypot(enemy.x - ship.x, enemy.y - ship.y);
                if (d < best) best = d;
            }
            return best;
        } finally {
            releaseShipScratch(nearby);
        }
    }

    private static void decayKillConfirmTimers(double dt) {
        if (TARGET_KILL_CONFIRM_TIMERS.isEmpty()) return;
        TARGET_KILL_CONFIRM_TIMERS.entrySet().removeIf(e -> {
            double t = e.getValue() - dt;
            if (t <= 0.0) return true;
            e.setValue(t);
            return false;
        });
    }

    private static void markKillConfirmTargets(GameContext ctx) {
        if (ctx == null || ctx.ships == null) return;
        for (Ship s : ctx.ships) {
            if (s == null || s.faction == null) continue;
            if (s.hpMax <= 0) continue;
            double hf = hullFrac(s);
            if (s.dying || (s.hp > 0 && hf < 0.16)) {
                double ttl = s.dying ? 1.2 : 0.8;
                Double old = TARGET_KILL_CONFIRM_TIMERS.get(s.id);
                if (old == null || old < ttl) TARGET_KILL_CONFIRM_TIMERS.put(s.id, ttl);
            }
        }
    }

    private static boolean isKillConfirmActive(Ship target) {
        if (target == null) return false;
        Double t = TARGET_KILL_CONFIRM_TIMERS.get(target.id);
        return t != null && t > 0.0;
    }

    private static double killConfirmTargetPenalty(Ship target, double hpFrac) {
        if (!isKillConfirmActive(target)) return 0.0;
        double weak = Math.max(0.0, 1.0 - hpFrac);
        return 280.0 + weak * 440.0;
    }

    private static boolean isOverkillLikely(GameContext ctx, Ship shooter, Ship target) {
        if (ctx == null || shooter == null || target == null || ctx.projectiles == null) return false;
        int incomingMissiles = 0;
        for (Projectile p : ctx.projectiles) {
            if (!(p instanceof Missile m)) continue;
            if (!m.alive || m.target != target) continue;
            if (m.faction == null || shooter.faction == null) continue;
            if (!m.faction.isFriendlyTo(shooter.faction)) continue;
            incomingMissiles++;
        }
        if (incomingMissiles <= 0) return false;
        double effectiveHp = Math.max(1.0, target.hp + Math.max(0.0, target.shield));
        double expectedVolley = incomingMissiles * 7.0;
        if (target.role == ShipRole.DREADNOUGHT || target.role == ShipRole.SUPERSHIP
                || target.role == ShipRole.BULWARK_TITAN || target.role == ShipRole.SHIELD_BASTION_TITAN
                || target.role == ShipRole.MOBILE_STATION_TITAN || target.role == ShipRole.MOTHERSHIP) {
            expectedVolley *= 0.75;
        }
        return expectedVolley >= effectiveHp * 1.15;
    }

    private static double localSupportBalance(GameContext ctx, Ship seeker, Ship target, double radius) {
        if (ctx == null || seeker == null || target == null || seeker.faction == null) return 0.0;
        double mx = (seeker.x + target.x) * 0.5;
        double my = (seeker.y + target.y) * 0.5;
        return localSupportBiasAtPoint(ctx, seeker.faction, mx, my, radius);
    }

    private static double localSupportBiasAtPoint(GameContext ctx, Faction perspective, double x, double y, double radius) {
        FleetStateBuilder.StrengthSummary summary = localStrengthSummary(ctx, perspective, x, y, radius);
        return summary.supportFriendly - summary.supportHostile;
    }


    private static boolean canTakeFightMetric(GameContext ctx, Ship seeker, Ship target) {
        if (!isAlive(seeker) || !isAlive(target)) return false;
        if (forcedToHoldFight(seeker, target)) return true;
        double immediateRange = Math.max(220.0, seeker.radius + target.radius + 140.0);
        if (Math.hypot(target.x - seeker.x, target.y - seeker.y) <= immediateRange) {
            return true;
        }
        return canTakeFightMargin(ctx, seeker, target) >= 0.0;
    }

    private static boolean canTakeFightForTargetSelection(GameContext ctx, Ship seeker, Ship target) {
        if (!isAlive(seeker) || !isAlive(target)) return false;
        AiScalePolicy.FramePlan scalePlan = currentScalePlan();
        double d = Math.hypot(target.x - seeker.x, target.y - seeker.y);
        if (CombatTargeting.shouldRunFullCandidateScore(scalePlan, seeker, target)) {
            return canTakeFightMetric(ctx, seeker, target);
        }
        return quickCanTakeFightEstimate(seeker, target, d);
    }

    private static boolean shouldRunDetailedFightAssessment(Ship ship) {
        return CombatTargeting.shouldRunDetailedFightAssessment(currentScalePlan(), ship);
    }

    private static boolean quickCanTakeFightEstimate(Ship seeker, Ship target, double dist) {
        if (!isAlive(seeker) || !isAlive(target)) return false;
        if (forcedToHoldFight(seeker, target)) return true;
        double immediateRange = Math.max(220.0, seeker.radius + target.radius + 140.0);
        if (dist <= immediateRange) return true;
        if (combinedDurabilityFrac(target) < 0.30) return true;
        double durability = combinedDurabilityFrac(seeker);
        if (isCapitalRole(seeker.role)) return durability >= 0.24;
        if (isDogfightRole(seeker.role)) return durability >= 0.22;
        return durability >= 0.34;
    }

    private static double canTakeFightMargin(GameContext ctx, Ship seeker, Ship target) {
        if (ctx == null || seeker == null || target == null || seeker.faction == null) return 0.0;
        double contestRadius = Math.max(420.0, preferredRange(seeker) * 1.35);
        double supportRadius = Math.max(340.0, preferredRange(seeker) * 1.10);
        double selfStrength = projectedCombatStrength(seeker);
        double[] targetStrength = combatStrengthSplitNearPoint(ctx, seeker.faction, target.x, target.y, contestRadius);
        double friendlyAtTarget = targetStrength[0];
        double hostileAtTarget = targetStrength[1];
        double friendlyNearSeeker = Math.max(0.0,
                combatStrengthNearPoint(ctx, seeker.faction, seeker.x, seeker.y, supportRadius, true) - selfStrength);

        double effectiveFriendly = friendlyAtTarget;
        if (dist2(seeker.x, seeker.y, target.x, target.y) > contestRadius * contestRadius) {
            effectiveFriendly += selfStrength;
        }
        effectiveFriendly += friendlyNearSeeker * supportConvergenceBias(seeker);

        double effectiveHostile = Math.max(projectedCombatStrength(target), hostileAtTarget);
        double aggression = roleAggressionBias(seeker.role) + factionAggressionBias(seeker.faction);
        double caution = Math.max(0.0, roleStandoffBias(seeker.role) + factionStandoffBias(seeker.faction))
                + Math.max(0.0, factionRetreatBias(seeker.faction));
        double requiredRatio = 1.00 + fightSupportNeedBias(seeker);
        if (isDogfightRole(seeker.role)) requiredRatio += 0.08;
        if (isCapitalRole(seeker.role)) requiredRatio -= 0.10;
        if (isCapitalRole(target.role) || target.role == ShipRole.CARRIER || target.role == ShipRole.DRONE_CARRIER) {
            requiredRatio -= 0.08;
        }
        if (isSupportRole(target.role) || isMissileThreatRole(target.role)) requiredRatio -= 0.04;
        requiredRatio += caution * 0.12;
        requiredRatio -= Math.max(0.0, aggression) * 0.10;
        requiredRatio += Math.max(0.0, 0.55 - combinedDurabilityFrac(seeker)) * 0.30;
        requiredRatio -= Math.max(0.0, 0.60 - combinedDurabilityFrac(target)) * 0.18;
        requiredRatio = Math.max(0.74, Math.min(1.28, requiredRatio));

        double ratio = effectiveFriendly / Math.max(0.35, effectiveHostile);
        return ratio - requiredRatio;
    }

    private static double fightSupportNeedBias(Ship ship) {
        if (ship == null) return 0.0;
        double bias = 0.0;
        if (ship.role != null) {
            bias += switch (ship.role) {
                case FIGHTER, DRONE, BOMBER, PD_CRAFT -> 0.12;
                case PATROL, PICKET, CIWS_CORVETTE -> 0.05;
                case FRIGATE, LIGHT_CRUISER -> 0.00;
                case MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> -0.04;
                case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> -0.08;
                case MISSILE_BOAT, ARTILLERY_SHIP, CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, MINER -> 0.10;
                default -> 0.0;
            };
        }
        if (ship.faction != null) {
            bias += switch (ship.faction) {
                case PLAYER, ALLY -> -0.02;
                case ENEMY -> -0.12;
                case TEAM_C -> 0.12;
                case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> -0.08;
                case TEAM_E -> 0.0;
            };
        }
        return bias;
    }

    private static double supportConvergenceBias(Ship ship) {
        if (ship == null) return 0.72;
        double bias = 0.72;
        if (ship.role != null) {
            bias += switch (ship.role) {
                case FIGHTER, DRONE, BOMBER, PD_CRAFT -> 0.14;
                case PATROL, PICKET, FRIGATE -> 0.08;
                case MISSILE_BOAT, ARTILLERY_SHIP, CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, MINER -> -0.06;
                case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> -0.04;
                default -> 0.0;
            };
        }
        if (ship.faction != null) {
            bias += switch (ship.faction) {
                case PLAYER, ALLY -> 0.04;
                case ENEMY -> 0.08;
                case TEAM_C -> -0.10;
                case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> 0.12;
                case TEAM_E -> 0.0;
            };
        }
        return Math.max(0.48, Math.min(1.05, bias));
    }

    private static double combatStrengthNearPoint(GameContext ctx, Faction perspective, double x, double y, double radius, boolean friendly) {
        FleetStateBuilder.StrengthSummary summary = localStrengthSummary(ctx, perspective, x, y, radius);
        return friendly ? summary.combatFriendly : summary.combatHostile;
    }

    private static double[] combatStrengthSplitNearPoint(GameContext ctx, Faction perspective, double x, double y, double radius) {
        FleetStateBuilder.StrengthSummary summary = localStrengthSummary(ctx, perspective, x, y, radius);
        return new double[]{summary.combatFriendly, summary.combatHostile};
    }

    private static FleetStateBuilder.StrengthSummary localStrengthSummary(GameContext ctx, Faction perspective,
                                                                          double x, double y, double radius) {
        if (ctx == null || perspective == null || radius <= 0.0) {
            return new FleetStateBuilder.StrengthSummary(0.0, 0.0, 0.0, 0.0, 0, 0);
        }
        long key = FleetStateBuilder.strengthCacheKey(perspective, x, y, radius);
        AIFrameCache cache = currentFrameCache();
        if (cache != null) {
            FleetStateBuilder.StrengthSummary cached = cache.strengthSummaries.get(key);
            if (cached != null) return cached;
        }
        double r2 = radius * radius;
        double combatFriendly = 0.0;
        double combatHostile = 0.0;
        double supportFriendly = 0.0;
        double supportHostile = 0.0;
        int countFriendly = 0;
        int countHostile = 0;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectAliveShipsNear(x, y, radius, nearby);
            for (Ship s : nearby) {
                if (!isAlive(s) || s.faction == null) continue;
                if (!isSupportRelevantCombatant(s.role)) continue;
                double d2 = dist2(s.x, s.y, x, y);
                if (d2 > r2) continue;
                double dist = Math.sqrt(Math.max(0.0, d2));
                double combatFalloff = Math.max(0.35, 1.0 - (dist / radius) * 0.42);
                double supportFalloff = Math.max(0.35, 1.0 - (dist / radius) * 0.45);
                boolean friendly = perspective.isFriendlyTo(s.faction);
                if (friendly) {
                    combatFriendly += projectedCombatStrength(s) * combatFalloff;
                    supportFriendly += supportCombatWeight(s.role) * supportFalloff;
                    countFriendly++;
                } else {
                    combatHostile += projectedCombatStrength(s) * combatFalloff;
                    supportHostile += supportCombatWeight(s.role) * supportFalloff;
                    countHostile++;
                }
            }
        } finally {
            releaseShipScratch(nearby);
        }
        FleetStateBuilder.StrengthSummary summary = new FleetStateBuilder.StrengthSummary(
                combatFriendly, combatHostile, supportFriendly, supportHostile, countFriendly, countHostile);
        if (cache != null) cache.strengthSummaries.put(key, summary);
        return summary;
    }

    private static double projectedCombatStrength(Ship ship) {
        if (!isAlive(ship)) return 0.0;
        double durability = Math.max(0.20, combinedDurabilityFrac(ship));
        return supportCombatWeight(ship.role) * (0.55 + durability * 0.70);
    }

    private static int countCombatantsNearPoint(GameContext ctx, Faction perspective, double x, double y, double radius, boolean friendly) {
        if (ctx == null || perspective == null || radius <= 0.0) return 0;
        int count = 0;
        double r2 = radius * radius;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectAliveShipsNear(x, y, radius, nearby);
            for (Ship s : nearby) {
                if (!isAlive(s) || s.faction == null) continue;
                if (!isSupportRelevantCombatant(s.role)) continue;
                boolean isFriendly = perspective.isFriendlyTo(s.faction);
                if (friendly != isFriendly) continue;
                if (dist2(s.x, s.y, x, y) <= r2) count++;
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return count;
    }

    private static int[] countCombatantsSplitNearPoint(GameContext ctx, Faction perspective, double x, double y, double radius) {
        FleetStateBuilder.StrengthSummary summary = localStrengthSummary(ctx, perspective, x, y, radius);
        return new int[]{summary.countFriendly, summary.countHostile};
    }


    private static double maxThreatSearchRadius(GameContext ctx, Ship seeker) {
        double rangeMul = (ctx == null) ? 1.0 : CampaignSystem.targetingRangeMul(ctx);
        double maxRange = 240.0;
        if (seeker != null && seeker.hasSuperweapon) {
            maxRange = Math.max(maxRange, UNIVERSAL_SUPERWEAPON_RANGE * rangeMul);
        }
        if (seeker != null && seeker.turrets != null) {
            for (Turret turret : seeker.turrets) {
                if (turret == null) continue;
                double turretRange = (turret.kind == Turret.Kind.GUN || isOffensiveMissileTurret(turret))
                        ? STANDARD_PROSECUTION_RANGE
                        : ((seeker.role == ShipRole.BASE || seeker.role == ShipRole.STATIC_TURRET)
                        ? ((turret.kind == Turret.Kind.MISSILE) ? 1400.0 : 900.0)
                        : ((turret.kind == Turret.Kind.MISSILE) ? 900.0 : 520.0));
                maxRange = Math.max(maxRange, turretRange * rangeMul * 1.12);
            }
        }
        return Math.max(520.0, maxRange + 260.0);
    }

    private static boolean isSupportRelevantCombatant(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case BASE, STATIC_TURRET, MINER -> false;
            default -> true;
        };
    }

    private static double supportCombatWeight(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case SUPERSHIP -> 4.4;
            case DREADNOUGHT -> 3.8;
            case BATTLESHIP -> 3.3;
            case BATTLECRUISER -> 2.9;
            case CRUISER, MEDIUM_CRUISER -> 2.6;
            case LIGHT_CRUISER -> 2.2;
            case CARRIER, DRONE_CARRIER -> 2.1;
            case MISSILE_BOAT -> 1.8;
            case FRIGATE, CIWS_CORVETTE -> 1.6;
            case PICKET, PATROL, STEALTH_SHIP -> 1.3;
            case FIGHTER, BOMBER, PD_CRAFT, DRONE -> 0.9;
            case HAULER, TRANSPORT -> 0.8;
            default -> 1.0;
        };
    }

    private static boolean handleStandaloneStrikeCraftRearm(GameContext ctx, Ship s, double dt) {
        if (ctx == null || s == null || dt <= 0.0) return false;
        if (!s.usesLimitedStrikeCraftMunitions()) return false;
        if (s.carrierOwnerId >= 0) return false;
        if (!s.needsStrikeCraftRearm()) return false;

        RearmPerception perception = perceiveStrikeCraftRearmSituation(ctx, s);
        ShipIntent intent = chooseStrikeCraftRearmIntent(perception);
        executeStrikeCraftRearmIntent(s, dt, intent);
        return true;
    }

    private static RearmPerception perceiveStrikeCraftRearmSituation(GameContext ctx, Ship craft) {
        if (ctx == null || craft == null) return new RearmPerception(null);
        return new RearmPerception(nearestFriendlyStrikeCraftTender(ctx, craft));
    }

    private static ShipIntent chooseStrikeCraftRearmIntent(RearmPerception perception) {
        if (perception == null || !isAlive(perception.tender)) {
            return new ShipIntent(IntentType.HOLD, null);
        }
        return new ShipIntent(IntentType.REARM, perception.tender);
    }

    private static void executeStrikeCraftRearmIntent(Ship craft, double dt, ShipIntent intent) {
        if (craft == null || intent == null) return;
        if (intent.type != IntentType.REARM || !isAlive(intent.target)) {
            setVelPerSec(craft, 0.0, 0.0, dt);
            return;
        }

        Ship tender = intent.target;
        double recoverRange = tender.radius + craft.radius + 18.0;
        double d2 = dist2(craft.x, craft.y, tender.x, tender.y);
        if (d2 <= recoverRange * recoverRange) {
            craft.reloadStrikeCraftMunitions();
            double orbitRange = Math.max(recoverRange + 24.0, tender.radius + 56.0);
            orbit(craft, tender.x, tender.y, orbitRange, Math.max(80.0, MovementModel.speedCeiling(craft) * 0.72), dt,
                    ((craft.id & 1) == 0) ? 1.0 : -1.0);
            return;
        }

        moveToward(craft, tender.x, tender.y, Math.max(110.0, MovementModel.speedCeiling(craft) * 0.96), dt);
    }

    private static boolean maybeStartBattlefieldWarp(GameContext ctx, Ship s, Ship target, double desiredOffset) {
        if (!isAlive(target)) return false;
        if (isModeWarpBaseDiveSuppressed(ctx, s, target)) return false;
        double dx = target.x - s.x;
        double dy = target.y - s.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) return false;
        double ux = dx / len;
        double uy = dy / len;
        double exitX = target.x - ux * desiredOffset;
        double exitY = target.y - uy * desiredOffset;
        return maybeStartBattlefieldWarp(ctx, s, exitX, exitY, desiredOffset);
    }

    private static boolean maybeStartBattlefieldWarp(GameContext ctx, Ship s, double targetX, double targetY, double desiredOffset) {
        if (ctx == null || s == null) return false;
        if (!s.canUseBattlefieldWarp()) return false;
        if (s.isWarpCharging()) return true;
        if (s.aiForcedEngageTimer > 0.0) return false;
        if (s.secondsSinceDamage() < 10.0) return false;
        if (findImmediateThreat(ctx, s, BATTLEFIELD_WARP_SAFE_RADIUS) != null) return false;

        double dx = targetX - s.x;
        double dy = targetY - s.y;
        double dist = Math.hypot(dx, dy);
        if (dist < BATTLEFIELD_WARP_TRIGGER_RANGE) return false;

        double exitX = GameMath.clamp(targetX, 36.0, ctx.WORLD_W - 36.0);
        double exitY = GameMath.clamp(targetY, 36.0, ctx.WORLD_H - 36.0);
        double exitDist = Math.hypot(exitX - s.x, exitY - s.y);
        if (exitDist < BATTLEFIELD_WARP_TRIGGER_RANGE * 0.48) return false;
        if (isInsideHostileStarbaseWarpExclusion(ctx, s, exitX, exitY)) return false;
        if (isModeWarpDestinationTooDeep(ctx, s, exitX, exitY)) return false;

        boolean started = s.beginBattlefieldWarp(exitX, exitY, 10.0);
        if (started && BattlefieldSectorSystem.isEnabled(ctx)) {
            BattlefieldSectorSystem.SectorDefinition currentSector = BattlefieldSectorSystem.sectorAt(ctx, s.x, s.y);
            s.setWarpSourceSectorId(currentSector == null ? "" : currentSector.id);
        }
        return started;
    }

    private static boolean isInsideHostileStarbaseWarpExclusion(GameContext ctx, Ship ship, double x, double y) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        for (Ship base : ctx.teamBases.values()) {
            if (!isAlive(base) || base.faction == null) continue;
            if (ship.faction.isFriendlyTo(base.faction)) continue;
            double exclusion = Math.max(HOSTILE_STARBASE_WARP_EXCLUSION_RADIUS, base.radius + 260.0);
            if (Math.hypot(x - base.x, y - base.y) < exclusion) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModeWarpBaseDiveSuppressed(GameContext ctx, Ship ship, Ship target) {
        if (ctx == null || ship == null || target == null) return false;
        if (!isDisciplinedWarpMode(ctx)) return false;
        if (ctx.battleElapsed > MODE_OPENING_WARP_DISCIPLINE_SECONDS) return false;
        return target.role == ShipRole.BASE;
    }

    private static boolean isModeWarpDestinationTooDeep(GameContext ctx, Ship ship, double exitX, double exitY) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        if (!isDisciplinedWarpMode(ctx)) return false;

        Ship friendlyBase = TeamSystem.getBaseForTeam(ctx, ship.faction);
        double friendlyDist = isAlive(friendlyBase)
                ? Math.hypot(exitX - friendlyBase.x, exitY - friendlyBase.y)
                : Double.POSITIVE_INFINITY;
        double nearestHostileBaseDist = Double.POSITIVE_INFINITY;
        Ship nearestHostileBase = null;
        for (Ship base : ctx.teamBases.values()) {
            if (!isAlive(base) || base.faction == null) continue;
            if (ship.faction.isFriendlyTo(base.faction)) continue;
            double d = Math.hypot(exitX - base.x, exitY - base.y);
            if (d < nearestHostileBaseDist) {
                nearestHostileBaseDist = d;
                nearestHostileBase = base;
            }
        }
        if (!isAlive(nearestHostileBase)) return false;

        double hostileSafe = nearestHostileBase.radius
                + Math.max(320.0, preferredRange(nearestHostileBase) * 0.88)
                + MODE_HOSTILE_BASE_WARP_BUFFER;
        if (nearestHostileBaseDist < hostileSafe) return true;

        if (ctx.battleElapsed <= MODE_OPENING_WARP_DISCIPLINE_SECONDS) {
            if (nearestHostileBaseDist < friendlyDist * 0.95) return true;
            if (isAlive(friendlyBase)) {
                double currentFriendlyDist = Math.hypot(ship.x - friendlyBase.x, ship.y - friendlyBase.y);
                double maxOpeningAdvance = Math.max(currentFriendlyDist + 520.0, BATTLEFIELD_WARP_TRIGGER_RANGE * 0.78);
                if (friendlyDist > maxOpeningAdvance && nearestHostileBaseDist < friendlyDist + 260.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDisciplinedWarpMode(GameContext ctx) {
        if (ctx == null || ctx.config == null) return false;
        return ctx.config.mode == GameMode.RESOURCE_RUSH
                || ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION;
    }

    private static boolean maybeStartFleetRejoinWarp(GameContext ctx, Ship s, Ship flagship,
                                                     double anchorX, double anchorY,
                                                     GameContext.FleetCommand cmd,
                                                     SquadObjective objective,
                                                     Ship target) {
        if (ctx == null || s == null || flagship == null) return false;
        if (s == flagship || s == ctx.player) return false;
        if (s.isWarpCharging()) return true;
        if (!shouldJoinFlagshipWarp(ctx, s, flagship) || isHeavilyDamagedForWarp(s)) return false;
        if (cmd == null) return false;
        if (cmd == GameContext.FleetCommand.RETREAT
                || cmd == GameContext.FleetCommand.RTB
                || cmd == GameContext.FleetCommand.REPAIR
                || cmd == GameContext.FleetCommand.MINE) {
            return false;
        }
        if (objective == SquadObjective.RESERVE) return false;

        double distToFlagship = Math.hypot(flagship.x - s.x, flagship.y - s.y);
        double distToAnchor = Math.hypot(anchorX - s.x, anchorY - s.y);
        if (distToFlagship < FLEET_REJOIN_WARP_RANGE && distToAnchor < FLEET_REJOIN_ANCHOR_RANGE) {
            return false;
        }
        if (isAlive(target)) {
            double threatDist = Math.hypot(target.x - s.x, target.y - s.y);
            if (threatDist <= Math.max(520.0, preferredRange(s) * 1.18)) return false;
        }

        double desiredOffset = Math.max(180.0, Math.min(340.0, s.radius + preferredRange(s) * 0.30));
        boolean started = maybeStartBattlefieldWarp(ctx, s, anchorX, anchorY, desiredOffset);
        if (started && ctx.player != null && s.faction != null
                && ctx.player.faction != null
                && s.faction.teamId() == ctx.player.faction.teamId()) {
            String label = ctx.command.fleetSquadLabelByShip.getOrDefault(s.id, "ESCORT");
            postFleetComm(ctx, s.faction, label, "warping back into formation");
        }
        return started;
    }

    private static boolean maybeStartEscortAnchorWarp(GameContext ctx, Ship s, Ship anchor) {
        if (ctx == null || s == null || anchor == null) return false;
        if (s == anchor || s == ctx.player) return false;
        if (!s.canUseBattlefieldWarp() || s.isWarpCharging()) return s.isWarpCharging();
        if (isHeavilyDamagedForWarp(s)) return false;
        double[] slot = escortWarpAnchorPoint(ctx, s, anchor);
        double dist = Math.hypot(slot[0] - s.x, slot[1] - s.y);
        if (dist < Math.max(720.0, anchor.radius + 320.0)) return false;
        double desiredOffset = Math.max(120.0, Math.min(260.0, s.radius + anchor.radius * 0.35));
        return maybeStartBattlefieldWarp(ctx, s, slot[0], slot[1], desiredOffset);
    }

    private static double[] escortWarpAnchorPoint(GameContext ctx, Ship s, Ship anchor) {
        if (s == null || anchor == null) return new double[]{0.0, 0.0};
        int slot = reservedEscortSlotIndex(ctx, s, anchor);
        double side = ((slot & 1) == 0) ? -1.0 : 1.0;
        int rank = slot / 2;
        double forward = anchor.radius + 210.0 + rank * 72.0;
        double lateral = anchor.radius + 150.0 + rank * 64.0;
        double fx = Math.cos(anchor.angle);
        double fy = Math.sin(anchor.angle);
        double rx = -fy;
        double ry = fx;
        return new double[]{
                anchor.x + fx * forward + rx * side * lateral,
                anchor.y + fy * forward + ry * side * lateral
        };
    }

    private static int reservedEscortSlotIndex(GameContext ctx, Ship s, Ship anchor) {
        if (s == null || anchor == null) return 0;
        if (s.escortSlotIndex > 0) return s.escortSlotIndex;
        if (ctx == null || ctx.ships == null || anchor.id <= 0) return Math.max(0, s.escortSlotIndex);
        int slot = 0;
        for (Ship other : ctx.ships) {
            if (other == null || other == anchor) continue;
            if (!isAlive(other)) continue;
            if (other.escortAnchorId != anchor.id) continue;
            if (other.id == s.id) return slot;
            slot++;
        }
        return Math.max(0, slot);
    }

    private static boolean shouldJoinFlagshipWarp(GameContext ctx, Ship ship, Ship flagship) {
        if (!isAlive(ship) || !isAlive(flagship)) return false;
        if (ship == flagship) return false;
        if (ctx != null && ship == ctx.player) return false;
        if (ignoresPlayerFormationOrders(ship)) return false;
        if (!ship.canUseBattlefieldWarp()) return false;
        if (ship.faction == null || flagship.faction == null) return false;
        if (ship.faction.teamId() != flagship.faction.teamId()) return false;
        return !isCriticallyDamagedForWarp(ship);
    }

    private static boolean isCriticallyDamagedForWarp(Ship ship) {
        if (ship == null) return true;
        if (hullFrac(ship) < 0.32) return true;
        if (combinedDurabilityFrac(ship) < 0.28) return true;
        return ship.activeFireRoomCount() >= 2 || ship.totalFireIntensity() >= 1.8;
    }

    private static boolean isHeavilyDamagedForWarp(Ship ship) {
        if (ship == null) return true;
        if (hullFrac(ship) < 0.56) return true;
        if (combinedDurabilityFrac(ship) < 0.50) return true;
        return ship.activeFireRoomCount() >= 2 || ship.totalFireIntensity() >= 1.6;
    }

    private static void steerWarpChargingShip(Ship ship, double dt) {
        if (ship == null || dt <= 0.0) return;
        double tx = ship.warpExitX();
        double ty = ship.warpExitY();
        if (!Double.isFinite(tx) || !Double.isFinite(ty)) {
            setVelPerSec(ship, 0.0, 0.0, dt);
            return;
        }

        double dist = Math.hypot(tx - ship.x, ty - ship.y);
        if (dist <= Math.max(80.0, ship.radius * 2.4)) {
            setVelPerSec(ship, 0.0, 0.0, dt);
            return;
        }

        double speed = MovementModel.speedCeiling(ship);
        double slowRadius = Math.max(260.0, ship.radius * 8.0);
        double speedMul = MathUtil.clamp(dist / slowRadius, 0.42, 1.0);
        moveToward(ship, tx, ty, speed * speedMul, dt);
    }

    private static Ship nearestFriendlyStrikeCraftTender(GameContext ctx, Ship craft) {
        if (ctx == null || craft == null || craft.faction == null) return null;
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship candidate : ctx.ships) {
            if (!isAlive(candidate) || candidate.faction == null) continue;
            if (!craft.faction.isFriendlyTo(candidate.faction)) continue;
            if (!candidate.isBase && !candidate.isCarrier) continue;
            double d2 = dist2(craft.x, craft.y, candidate.x, candidate.y);
            if (d2 >= bestD2) continue;
            bestD2 = d2;
            best = candidate;
        }
        return best;
    }

    private static boolean isAlive(Ship s) {
        return s != null && s.alive && !s.dying && s.hp > 0;
    }

    private static double hullFrac(Ship s) {
        if (s == null || s.hpMax <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, s.hp / (double) s.hpMax));
    }

    private static double shieldFrac(Ship s) {
        if (s == null || !s.shieldActive) return 1.0;
        double effectiveMax = s.effectiveShieldCapacityMax();
        if (effectiveMax <= 0.0) return 1.0;
        return Math.max(0.0, Math.min(1.0, s.shield / Math.max(1e-9, effectiveMax)));
    }

    private static void orbit(Ship s, double cx, double cy, double desiredRange, double speedPerSec, double dt, double dir) {
        double dx = cx - s.x;
        double dy = cy - s.y;
        double d = Math.sqrt(dx*dx + dy*dy) + 1e-9;

        double ux = dx / d;
        double uy = dy / d;

        // Tangent
        double tx = -uy * dir;
        double ty = ux * dir;

        // Radial correction
        double err = d - desiredRange; // + too far
        double radial = Math.max(-1.0, Math.min(1.0, err / Math.max(1.0, desiredRange)));
        double blend = 0.55;

        double vx = (tx * (1.0 - blend) + ux * blend * radial) * speedPerSec;
        double vy = (ty * (1.0 - blend) + uy * blend * radial) * speedPerSec;

        setVelPerSec(s, vx, vy, dt);
        rotateShipToward(s, Math.atan2(vy, vx), dt);
    }

    private static double[] teamWaveStagingPoint(GameContext ctx, Faction faction) {
        double[] sectorPoint = BattlefieldSectorSystem.stagingPoint(ctx, faction);
        if (sectorPoint != null) {
            return sectorPoint;
        }
        Ship base = TeamSystem.getBaseForTeam(ctx, faction);
        if (isAlive(base)) {
            double side = (base.x <= ctx.WORLD_W * 0.5) ? 1.0 : -1.0;
            double sx = GameMath.clamp(base.x + side * 340.0, 60.0, ctx.WORLD_W - 60.0);
            double sy = GameMath.clamp(base.y + (((faction.teamId() & 1) == 0) ? -220.0 : 220.0), 60.0, ctx.WORLD_H - 60.0);
            return new double[]{sx, sy};
        }
        double fallbackX = (faction == Faction.ENEMY) ? ctx.WORLD_W - 220.0 : 220.0;
        double fallbackY = (faction == Faction.ENEMY) ? ctx.WORLD_H * 0.35 : ctx.WORLD_H * 0.65;
        return new double[]{fallbackX, fallbackY};
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return dx*dx + dy*dy;
    }

    private static boolean forcedToHoldFight(Ship seeker, Ship target) {
        if (!isAlive(seeker) || !isAlive(target)) return false;
        if (seeker.aiForcedEngageTimer <= 0.0) return false;
        if (seeker.faction == null || target.faction == null) return false;
        return !seeker.faction.isFriendlyTo(target.faction);
    }

    private static void applyAsteroidAvoidance(GameContext ctx, Ship s, double dt) {
        if (ctx == null || s == null || dt <= 0.0) return;
        if (s.role == ShipRole.MINER) return;
        if (ctx.asteroids == null || ctx.asteroids.isEmpty()) return;

        double vxPerSec = s.vx / dt;
        double vyPerSec = s.vy / dt;
        double speed = Math.hypot(vxPerSec, vyPerSec);
        if (speed < 6.0) return;

        double nx = vxPerSec / speed;
        double ny = vyPerSec / speed;
        double lookScale = BalanceConfig.asteroidAvoidanceLookaheadScale(s.role);
        double clearScale = BalanceConfig.asteroidAvoidanceClearanceScale(s.role);
        double lookAhead = Math.max(BalanceConfig.ASTEROID_AVOID_LOOKAHEAD_BASE,
                s.radius * 4.0 + speed * BalanceConfig.ASTEROID_AVOID_LOOKAHEAD_SPEED) * lookScale;

        Asteroid threat = null;
        double best = Double.POSITIVE_INFINITY;
        double threatSide = 0.0;

        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;

            double rx = a.x - s.x;
            double ry = a.y - s.y;
            double forward = rx * nx + ry * ny;
            if (forward < 0.0 || forward > lookAhead) continue;

            double sideSigned = rx * (-ny) + ry * nx;
            double side = Math.abs(sideSigned);
            double clearancePad = BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * clearScale;
            double clearance = side - (s.radius + a.collisionRadius() + clearancePad);
            if (clearance >= 0.0) continue;

            // Favor nearer threats with larger overlap.
            double score = forward + Math.abs(clearance) * 0.8;
            if (score < best) {
                best = score;
                threat = a;
                threatSide = sideSigned;
            }
        }

        if (threat == null) return;

        double awayX = s.x - threat.x;
        double awayY = s.y - threat.y;
        double awayLen = Math.hypot(awayX, awayY) + 1e-9;
        awayX /= awayLen;
        awayY /= awayLen;

        // Pick a tangent lane that steers around the asteroid instead of backing up.
        double tangentSign = (threatSide >= 0.0) ? -1.0 : 1.0;
        double tx = -ny * tangentSign;
        double ty = nx * tangentSign;

        double forwardBias = 0.45 / Math.max(0.70, lookScale);
        double tangentBias = 0.95 * Math.max(0.75, clearScale);
        double awayBias = 0.90 * Math.max(0.70, clearScale);
        double newVx = (nx * forwardBias + tx * tangentBias + awayX * awayBias);
        double newVy = (ny * forwardBias + ty * tangentBias + awayY * awayBias);
        double newLen = Math.hypot(newVx, newVy) + 1e-9;
        newVx = (newVx / newLen) * speed;
        newVy = (newVy / newLen) * speed;

        setVelPerSec(s, newVx, newVy, dt);
        rotateShipToward(s, Math.atan2(newVy, newVx), dt);
    }
}
