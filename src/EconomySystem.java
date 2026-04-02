import app.config.GameMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class EconomySystem {
    private EconomySystem(){}

    private static final double PERIODIC_MINER_SPAWN_INTERVAL = 20.0;
    private static final int PERIODIC_MINERS_PER_TEAM = 2;
    private static final int MAX_STATION_TURRETS_PER_BASE = 4;
    private static final double STATION_TURRET_RING_RADIUS = 210.0;
    private static final double STATION_TURRET_RING_JITTER = 36.0;
    private static final double STATION_TURRET_MIN_SPACING = 80.0;
    private static final double BASE_SHIP_DEPLOY_RADIUS = 260.0;
    private static final double BASE_SHIP_DEPLOY_JITTER = 64.0;
    private static final double BASE_SHIP_DEPLOY_MIN_SPACING = 96.0;
    private static final double BASE_SHIP_SUPPORT_RANGE = 1020.0;
    private static final double CARRIER_RESPAWN_RADIUS = 220.0;
    private static final double CARRIER_RESPAWN_JITTER = 54.0;
    private static final double CARRIER_RESPAWN_MIN_SPACING = 92.0;
    private static final double CARRIER_RESPAWN_SUPPORT_RANGE = 900.0;
    private static final double BASE_REPAIR_MIN_RANGE = 180.0;
    private static final double PLAYER_HAULER_SALE_RANGE = 120.0;
    private static final double TRANSPORT_SUPPORT_MIN_RANGE = 220.0;
    private static final double TRANSPORT_ROOM_HEAL_FRAC_PER_SEC = 0.05;
    private static final double TRANSPORT_FIRE_REDUCTION_FRAC_PER_SEC = 0.3333333333333333;
    private static final double HAULER_TRANSFER_RANGE = 120.0;
    private static final double HAULER_TRANSFER_PER_SEC = 120.0;
    private static final double HAULER_SEARCH_RANGE = 1600.0;
    private static final double HAULER_FULL_FRAC = 0.92;
    private static final double HAULER_TASK_LOCK_SECONDS = 0.85;
    private static final double NPC_REFIT_CHECK_INTERVAL = 1.25;
    private static final double ESCORT_FIGHTER_RESPAWN_SECONDS = 20.0;
    private static final int NPC_REFITS_PER_TEAM_PASS = 2;
    private static final double NPC_REFIT_COOLDOWN_SEC = 14.0;
    private static final double NPC_REFIT_DOCK_RANGE = 220.0;
    private static final double NPC_REFIT_THREAT_RANGE = 780.0;
    private static final double NPC_REFIT_MAX_JUMP_MUL = 1.90;
    private static final double NPC_BASE_UPGRADE_CHECK_INTERVAL = 4.5;
    private static final int NPC_BASE_UPGRADE_MIN_ORE_RESERVE = 160;
    private static final int NPC_BASE_UPGRADE_PASSES = 1;
    private static final WeakHashMap<GameContext, EnumMap<Faction, CommanderPersonality>> TEAM_PERSONALITIES = new WeakHashMap<>();
    private static final WeakHashMap<GameContext, EnumMap<Faction, Double>> TEAM_REFIT_TIMERS = new WeakHashMap<>();
    private static final WeakHashMap<GameContext, EnumMap<Faction, Double>> TEAM_BASE_UPGRADE_TIMERS = new WeakHashMap<>();
    private static final WeakHashMap<GameContext, Map<Integer, Double>> SHIP_REFIT_COOLDOWNS = new WeakHashMap<>();
    private static final WeakHashMap<GameContext, Map<Integer, Double>> ESCORT_RESPAWN_TIMERS = new WeakHashMap<>();
    private static final WeakHashMap<GameContext, Map<Integer, HaulerLogisticsState>> HAULER_LOGISTICS = new WeakHashMap<>();

    private enum CommanderPersonality {
        BALANCED,
        FLAGSHIP_CORE,
        ESCORT_WING,
        ARTILLERY_LINE,
        STEALTH_RAIDERS,
        CARRIER_GROUP
    }

    private enum CombatBucket {
        FLAGSHIP,
        ESCORT,
        ARTILLERY,
        STEALTH,
        CARRIER,
        LINE
    }

    private enum HaulerTask {
        COLLECT,
        DELIVER
    }

    private static final class HaulerLogisticsState {
        HaulerTask task = HaulerTask.COLLECT;
        int targetMinerId = -1;
        double taskLock = 0.0;
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;

        SpawnSystem.updateShootingRangeRespawns(ctx, dt);

        // Salvage drift
        for (int i = ctx.salvage.size() - 1; i >= 0; i--) {
            Salvage s = ctx.salvage.get(i);
            s.update(dt);
            if (s.life <= 0) ctx.salvage.remove(i);
        }

        // Mining for player (hold F)
        if ((ctx.miningKeyDown || ctx.command.miningAuto) && ctx.player != null) {
            doMining(ctx, ctx.player, dt);
        }

        // Player deposits mined ore when docked at a friendly base.
        handlePlayerDeposit(ctx);
        handlePlayerHaulerSales(ctx);

        // NPC mining & deposits
        handleNpcMiningAndDeposits(ctx, dt);
        updateNpcHaulerLogistics(ctx, dt);
        updateStationTurretStructures(ctx);
        updateShipEscortPrograms(ctx, dt);
        updateCarrierRespawnPrograms(ctx, dt);
        applyFriendlyBaseRepairAuras(ctx, dt);
        applyTransportSupportAuras(ctx, dt);
        updateNpcBaseUpgradePrograms(ctx, dt);
        updateNpcRefitPrograms(ctx, dt);

        // Periodic miner reinforcements for teams still in the match.
        updatePeriodicMinerReinforcements(ctx, dt);

        // Mode win checks
        if (ctx.config.mode == GameMode.RESOURCE_RUSH) checkResourceRushWin(ctx);
        if (ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) checkFourTeamDominationWin(ctx);
    }

    private static void doMining(GameContext ctx, Ship miner, double dt) {
        Asteroid a = findBestAsteroidNear(ctx, miner.x, miner.y, 220);
        if (a == null) return;

        double dtScaled = dt * ctx.miningMul * ctx.miningBaseMul;
        dtScaled *= CampaignSystem.miningRateMul(ctx);
        int mined = miner.tryMine(a, dtScaled);
        if (mined > 0) {
            try { VFX.spawnEngineWisp(miner.x, miner.y, miner.vx, miner.vy); } catch (Throwable ignored) {}
        }
    }

    public static Asteroid findBestAsteroidNear(GameContext ctx, double x, double y, double maxDist) {
        Asteroid best = null;
        double bestD2 = maxDist * maxDist;
        for (Asteroid a : ctx.asteroids) {
            double ore = getAsteroidOre(a);
            if (ore <= 0.01) continue;
            double d2 = GameMath.dist2(x, y, a.x, a.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = a;
            }
        }
        return best;
    }

    public static Ship getDockedFriendlyBase(GameContext ctx) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (!TeamSystem.isFriendlyToPlayer(ctx, s.faction)) continue;
            if (GameMath.dist2(ctx.player.x, ctx.player.y, s.x, s.y) < (120 * 120)) return s;
        }
        return null;
    }

    private static void handlePlayerDeposit(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (CampaignSystem.isCampaignActive(ctx)) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (ctx.player.cargo <= 0) return;

        Ship base = getDockedFriendlyBase(ctx);
        if (base == null) return;

        int moved = ctx.player.depositCargoTo(base);
        if (moved <= 0) return;

        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul;
        priceMul *= CampaignSystem.oreCreditMul(ctx);
        int baseCredits = (int) Math.round(moved * GameContext.ORE_PRICE * priceMul);
        ctx.credits += GameContext.scaleCreditEarnings(baseCredits);
    }

    private static void handlePlayerHaulerSales(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (CampaignSystem.isCampaignActive(ctx)) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (ctx.player.cargo <= 0) return;

        Ship hauler = findNearbyFriendlyHauler(ctx, ctx.player, PLAYER_HAULER_SALE_RANGE);
        if (hauler == null) return;

        int moved = ctx.player.cargo;
        if (moved <= 0) return;
        ctx.player.cargo = 0;

        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul;
        priceMul *= CampaignSystem.oreCreditMul(ctx);
        int baseCredits = (int) Math.round(moved * GameContext.ORE_PRICE * priceMul);
        ctx.credits += GameContext.scaleCreditEarnings(baseCredits);
        EventSystem.showBanner(ctx, "HAULER PURCHASED " + moved + " ORE", 0.9);
    }

    private static Ship findNearbyFriendlyHauler(GameContext ctx, Ship seller, double range) {
        if (ctx == null || seller == null || seller.faction == null || range <= 0.0) return null;
        Ship best = null;
        double bestD2 = range * range;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.HAULER) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || !s.faction.isFriendlyTo(seller.faction)) continue;
            double d2 = GameMath.dist2(seller.x, seller.y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    private static void handleNpcMiningAndDeposits(GameContext ctx, double dt) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.MINER) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            updateMinerState(ctx, s, dt);
        }
    }

    private static void updateNpcHaulerLogistics(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        Map<Integer, HaulerLogisticsState> haulerStates = HAULER_LOGISTICS.computeIfAbsent(ctx, k -> new HashMap<>());
        haulerStates.entrySet().removeIf(e -> !isLiveHaulerById(ctx, e.getKey()));
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.HAULER) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            HaulerLogisticsState state = haulerStates.computeIfAbsent(s.id, k -> new HaulerLogisticsState());
            updateHaulerState(ctx, s, state, dt);
        }
    }

    private static void updateHaulerState(GameContext ctx, Ship hauler, HaulerLogisticsState state, double dt) {
        if (ctx == null || hauler == null || state == null || dt <= 0.0) return;
        state.taskLock = Math.max(0.0, state.taskLock - dt);

        if (hauler.minerHomeBase == null || !hauler.minerHomeBase.alive || hauler.minerHomeBase.hp <= 0) {
            hauler.minerHomeBase = findHomeBaseFor(ctx, hauler);
        }
        Ship base = hauler.minerHomeBase;

        boolean fullEnough = hauler.cargo >= Math.max(1, (int) Math.round(hauler.cargoMax * HAULER_FULL_FRAC));
        if (fullEnough) {
            state.task = HaulerTask.DELIVER;
            state.targetMinerId = -1;
            state.taskLock = Math.max(state.taskLock, HAULER_TASK_LOCK_SECONDS);
        } else if (state.task == HaulerTask.DELIVER && hauler.cargo <= 0) {
            state.task = HaulerTask.COLLECT;
            state.targetMinerId = -1;
        }

        if (state.task == HaulerTask.DELIVER) {
            if (base != null && base.alive && base.hp > 0) {
                steerTo(hauler, base.x, base.y, dt);
                if (inDepositRange(hauler, base)) {
                    hauler.depositCargoTo(base);
                    if (hauler.cargo <= 0) {
                        state.task = HaulerTask.COLLECT;
                    }
                }
            } else {
                aiWander(hauler, dt, ctx.WORLD_W, ctx.WORLD_H);
            }
            return;
        }

        Ship targetMiner = resolveHaulerMinerTarget(ctx, hauler, state.targetMinerId, HAULER_SEARCH_RANGE);
        if (targetMiner == null) {
            targetMiner = findBestMinerForHauler(ctx, hauler, HAULER_SEARCH_RANGE);
            state.targetMinerId = (targetMiner == null) ? -1 : targetMiner.id;
        }

        if (targetMiner == null) {
            if (hauler.cargo > 0 && state.taskLock <= 0.0) {
                state.task = HaulerTask.DELIVER;
                state.targetMinerId = -1;
                state.taskLock = Math.max(state.taskLock, HAULER_TASK_LOCK_SECONDS);
                if (base != null && base.alive && base.hp > 0) {
                    steerTo(hauler, base.x, base.y, dt);
                    if (inDepositRange(hauler, base)) {
                        hauler.depositCargoTo(base);
                        if (hauler.cargo <= 0) {
                            state.task = HaulerTask.COLLECT;
                        }
                    }
                } else {
                    aiWander(hauler, dt, ctx.WORLD_W, ctx.WORLD_H);
                }
            } else if (hauler.cargo > 0 && base != null && base.alive && base.hp > 0 && inDepositRange(hauler, base)) {
                hauler.depositCargoTo(base);
                if (hauler.cargo <= 0) {
                    state.task = HaulerTask.COLLECT;
                }
            } else {
                holdPosition(hauler);
            }
            return;
        }

        double range = HAULER_TRANSFER_RANGE + hauler.radius + targetMiner.radius;
        double range2 = range * range;
        if (GameMath.dist2(hauler.x, hauler.y, targetMiner.x, targetMiner.y) > range2) {
            steerTo(hauler, targetMiner.x, targetMiner.y, dt);
            return;
        }

        int haulerRoom = Math.max(0, hauler.cargoMax - hauler.cargo);
        if (haulerRoom <= 0) return;
        int desired = Math.max(1, (int) Math.round(HAULER_TRANSFER_PER_SEC * dt));
        int moved = Math.min(haulerRoom, Math.min(targetMiner.cargo, desired));
        if (moved <= 0) return;

        targetMiner.cargo = Math.max(0, targetMiner.cargo - moved);
        hauler.cargo = Math.min(hauler.cargoMax, hauler.cargo + moved);
        state.taskLock = Math.max(state.taskLock, HAULER_TASK_LOCK_SECONDS);

        if (targetMiner.role == ShipRole.MINER
                && (targetMiner.minerState == Ship.MinerState.RETURN_TO_BASE || targetMiner.minerState == Ship.MinerState.DEPOSIT)
                && targetMiner.cargo < Math.max(1, (int) Math.round(targetMiner.cargoMax * 0.55))) {
            targetMiner.minerState = Ship.MinerState.SEEK_ASTEROID;
            targetMiner.minerTarget = null;
        }

        if (hauler.cargo >= Math.max(1, (int) Math.round(hauler.cargoMax * HAULER_FULL_FRAC))) {
            state.task = HaulerTask.DELIVER;
            state.targetMinerId = -1;
        } else if (targetMiner.cargo <= 0) {
            state.targetMinerId = -1;
        }
    }

    private static Ship resolveHaulerMinerTarget(GameContext ctx, Ship hauler, int targetMinerId, double maxDist) {
        if (ctx == null || hauler == null || targetMinerId <= 0) return null;
        Ship target = findShipById(ctx.ships, targetMinerId);
        if (target == null) return null;
        if (target.role != ShipRole.MINER) return null;
        if (!target.alive || target.dying || target.hp <= 0) return null;
        if (target.faction == null || hauler.faction == null || target.faction.teamId() != hauler.faction.teamId()) return null;
        if (target.cargo <= 0) return null;
        if (maxDist > 0.0 && GameMath.dist2(hauler.x, hauler.y, target.x, target.y) > maxDist * maxDist) return null;
        return target;
    }

    private static Ship findShipById(List<Ship> ships, int shipId) {
        if (ships == null || shipId <= 0) return null;
        for (Ship s : ships) {
            if (s != null && s.id == shipId) return s;
        }
        return null;
    }

    private static boolean isLiveHaulerById(GameContext ctx, int shipId) {
        Ship s = findShipById((ctx == null) ? null : ctx.ships, shipId);
        return s != null && s.role == ShipRole.HAULER && s.alive && !s.dying && s.hp > 0;
    }

    private static Ship findBestMinerForHauler(GameContext ctx, Ship hauler, double maxDist) {
        if (ctx == null || hauler == null || hauler.faction == null || maxDist <= 0.0) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double maxDist2 = maxDist * maxDist;

        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.MINER) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.teamId() != hauler.faction.teamId()) continue;
            if (s.cargo <= 0) continue;

            double d2 = GameMath.dist2(hauler.x, hauler.y, s.x, s.y);
            if (d2 > maxDist2) continue;

            double dist = Math.sqrt(Math.max(0.0, d2));
            double score = s.cargo * 2.6 - dist * 0.22;
            if (s.minerState == Ship.MinerState.RETURN_TO_BASE || s.minerState == Ship.MinerState.DEPOSIT) {
                score += 45.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static void updatePeriodicMinerReinforcements(GameContext ctx, double dt) {
        if (ctx == null) return;
        ctx.minerReinforcementTimer -= Math.max(0.0, dt);
        while (ctx.minerReinforcementTimer <= 0.0) {
            ctx.minerReinforcementTimer += PERIODIC_MINER_SPAWN_INTERVAL;
            spawnPeriodicMinersForAliveTeams(ctx);
        }
    }

    private static void spawnPeriodicMinersForAliveTeams(GameContext ctx) {
        java.util.EnumSet<Faction> teams = java.util.EnumSet.noneOf(Faction.class);
        teams.addAll(ctx.teamBases.keySet());
        if (ctx.allyBase != null) teams.add(Faction.ALLY);
        if (ctx.enemyBase != null) teams.add(Faction.ENEMY);

        for (Faction team : teams) {
            if (team == null) continue;
            if (!TeamSystem.isTeamAlive(ctx, team)) continue;

            Ship base = TeamSystem.getBaseForTeam(ctx, team);
            if (base == null) continue;
            if (!base.alive || base.dying || base.hp <= 0) continue;

            spawnMinersAtBase(ctx, team, base, PERIODIC_MINERS_PER_TEAM);
            spawnLogisticsSupportAtBase(ctx, team, base);
        }
    }

    private static void spawnMinersAtBase(GameContext ctx, Faction team, Ship base, int count) {
        int n = Math.max(0, count);
        for (int i = 0; i < n; i++) {
            double a = ctx.rng.nextDouble() * Math.PI * 2.0;
            double r = base.radius + 80.0 + ctx.rng.nextDouble() * 70.0;
            double sx = base.x + Math.cos(a) * r;
            double sy = base.y + Math.sin(a) * r;

            Ship miner = SpawnSystem.spawnTeamShip(ctx, ShipRole.MINER, team, sx, sy);
            if (miner == null) continue;
            miner.minerHomeBase = base;
            miner.minerState = Ship.MinerState.SEEK_ASTEROID;
        }
    }

    private static void spawnLogisticsSupportAtBase(GameContext ctx, Faction team, Ship base) {
        if (ctx == null || team == null || base == null) return;

        int miners = TeamSystem.countAliveMiners(ctx, team);
        int haulers = countAliveRoleForTeam(ctx, team, ShipRole.HAULER);
        int desiredHaulers = 0;
        if (miners >= 2) desiredHaulers = 1;
        if (miners >= 4) desiredHaulers = 2;
        desiredHaulers = Math.max(0, Math.min(2, desiredHaulers));

        while (haulers < desiredHaulers) {
            Ship hauler = spawnSupportRoleAtBase(ctx, base, team, ShipRole.HAULER);
            if (hauler == null) break;
            hauler.minerHomeBase = base;
            haulers++;
        }

        int transports = countAliveRoleForTeam(ctx, team, ShipRole.TRANSPORT);
        int combatShips = countTeamCombatShips(ctx, team);
        int desiredTransports = (combatShips >= 4) ? 1 : 0;
        desiredTransports = Math.max(0, Math.min(1, desiredTransports));

        while (transports < desiredTransports) {
            Ship transport = spawnSupportRoleAtBase(ctx, base, team, ShipRole.TRANSPORT);
            if (transport == null) break;
            transports++;
        }
    }

    private static Ship spawnSupportRoleAtBase(GameContext ctx, Ship base, Faction team, ShipRole role) {
        if (ctx == null || base == null || team == null || role == null) return null;
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = ctx.rng.nextDouble() * Math.PI * 2.0;
            double r = base.radius + 95.0 + ctx.rng.nextDouble() * 90.0;
            double sx = base.x + Math.cos(a) * r;
            double sy = base.y + Math.sin(a) * r;
            Ship ship = SpawnSystem.spawnTeamShip(ctx, role, team, sx, sy);
            if (ship != null) return ship;
        }
        return null;
    }

    private static void updateCarrierRespawnPrograms(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        List<Ship> carrierSnapshot = new ArrayList<>(ctx.ships);
        for (Ship carrier : carrierSnapshot) {
            if (carrier == null) continue;
            if (!carrier.isCarrier) continue;
            if (!carrier.alive || carrier.dying || carrier.hp <= 0) continue;
            if (CampaignSystem.isCampaignActive(ctx)
                    && carrier.faction != null
                    && carrier.faction.teamId() == Faction.ALLY.teamId()) continue;
            if (!carrier.canSpawnMobileReinforcement()) continue;

            int supported = countCarrierReinforcements(ctx, carrier);
            int cap = Math.max(2, Math.min(5, carrier.maxDefenders));
            if (supported >= cap) continue;

            Ship respawned = spawnCarrierReinforcement(ctx, carrier);
            if (respawned == null) continue;
            carrier.resetBaseSpawnTimer();
        }
    }

    private static void updateShipEscortPrograms(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        Map<Integer, Double> timers = ESCORT_RESPAWN_TIMERS.computeIfAbsent(ctx, k -> new java.util.HashMap<>());
        timers.replaceAll((id, time) -> Math.max(0.0, time - dt));

        List<Ship> anchors = new ArrayList<>(ctx.ships);
        for (Ship anchor : anchors) {
            if (!isEscortAnchor(anchor)) continue;
            if (CampaignSystem.isCampaignActive(ctx)
                    && anchor.faction != null
                    && anchor.faction.teamId() == Faction.ALLY.teamId()) continue;
            int active = countActiveEscortFighters(ctx, anchor);
            if (active >= 2) continue;

            double cooldown = timers.getOrDefault(anchor.id, 0.0);
            if (cooldown > 1e-6) continue;

            Ship escort = spawnEscortFighter(ctx, anchor, active);
            timers.put(anchor.id, (escort == null) ? 1.0 : ESCORT_FIGHTER_RESPAWN_SECONDS);
        }

        timers.entrySet().removeIf(e -> e == null || e.getKey() == null || findLiveShipById(ctx.ships, e.getKey()) == null);
    }

    private static boolean isEscortAnchor(Ship s) {
        if (s == null || !s.alive || s.dying || s.hp <= 0) return false;
        if (s.faction == null) return false;
        if (s.carrierOwnerId >= 0) return false;
        if (s.isSmallCraft()) return false;
        return s.role != ShipRole.BASE && s.role != ShipRole.STATIC_TURRET;
    }

    private static int countActiveEscortFighters(GameContext ctx, Ship anchor) {
        if (ctx == null || anchor == null || anchor.faction == null) return 0;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null || s.role != ShipRole.FIGHTER) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId >= 0) continue;
            if (s.faction == null || s.faction.teamId() != anchor.faction.teamId()) continue;
            if (s.escortAnchorId == anchor.id) count++;
        }
        return count;
    }

    private static Ship spawnEscortFighter(GameContext ctx, Ship anchor, int existingEscorts) {
        if (ctx == null || anchor == null || anchor.faction == null) return null;
        for (int attempt = 0; attempt < 10; attempt++) {
            double side = (((existingEscorts + attempt) & 1) == 0) ? -1.0 : 1.0;
            double fore = 34.0 + attempt * 6.0;
            double lateral = anchor.radius + 30.0 + attempt * 8.0;
            double sx = anchor.x + Math.cos(anchor.angle) * fore - Math.sin(anchor.angle) * side * lateral;
            double sy = anchor.y + Math.sin(anchor.angle) * fore + Math.cos(anchor.angle) * side * lateral;
            sx = GameMath.clamp(sx, 30.0, ctx.WORLD_W - 30.0);
            sy = GameMath.clamp(sy, 30.0, ctx.WORLD_H - 30.0);
            if (!isEscortSpawnClear(ctx, anchor, sx, sy)) continue;

            Ship escort = SpawnSystem.spawnTeamShip(ctx, ShipRole.FIGHTER, anchor.faction, sx, sy);
            if (escort == null) continue;
            escort.escortAnchorId = anchor.id;
            escort.escortSlotIndex = existingEscorts;
            escort.angle = anchor.angle;
            escort.vx = anchor.vx;
            escort.vy = anchor.vy;
            return escort;
        }
        return null;
    }

    private static boolean isEscortSpawnClear(GameContext ctx, Ship anchor, double x, double y) {
        if (ctx == null || anchor == null) return false;
        double minSpacing2 = 34.0 * 34.0;
        for (Ship s : ctx.ships) {
            if (s == null || s == anchor) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (GameMath.dist2(s.x, s.y, x, y) < minSpacing2) return false;
        }
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            double min = a.collisionRadius() + 18.0;
            if (GameMath.dist2(a.x, a.y, x, y) < min * min) return false;
        }
        return true;
    }

    private static int countCarrierReinforcements(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null || carrier.faction == null) return 0;
        int count = 0;
        double maxD2 = CARRIER_RESPAWN_SUPPORT_RANGE * CARRIER_RESPAWN_SUPPORT_RANGE;
        for (Ship s : ctx.ships) {
            if (s == null || s == carrier) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId >= 0) continue;
            if (s.faction == null || s.faction.teamId() != carrier.faction.teamId()) continue;
            if (!isCarrierRespawnRole(s.role)) continue;
            if (s.minerHomeBase == carrier || GameMath.dist2(s.x, s.y, carrier.x, carrier.y) <= maxD2) count++;
        }
        return count;
    }

    private static Ship spawnCarrierReinforcement(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null || carrier.faction == null) return null;
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = ctx.rng.nextDouble() * Math.PI * 2.0;
            double r = CARRIER_RESPAWN_RADIUS + (ctx.rng.nextDouble() - 0.5) * CARRIER_RESPAWN_JITTER;
            double sx = GameMath.clamp(carrier.x + Math.cos(a) * r, 30, ctx.WORLD_W - 30);
            double sy = GameMath.clamp(carrier.y + Math.sin(a) * r, 30, ctx.WORLD_H - 30);
            if (!isCarrierRespawnClear(ctx, carrier, sx, sy)) continue;

            ShipRole role = chooseCarrierRespawnRole(ctx, carrier);
            Ship ship = SpawnSystem.spawnTeamShip(ctx, role, carrier.faction, sx, sy);
            if (ship == null) continue;

            ship.minerHomeBase = carrier;
            ship.vx = carrier.vx * 0.75;
            ship.vy = carrier.vy * 0.75;
            ship.angle = carrier.angle;
            return ship;
        }
        return null;
    }

    private static boolean isCarrierRespawnClear(GameContext ctx, Ship carrier, double x, double y) {
        if (ctx == null || carrier == null) return false;
        double minSpacing2 = CARRIER_RESPAWN_MIN_SPACING * CARRIER_RESPAWN_MIN_SPACING;
        for (Ship s : ctx.ships) {
            if (s == null || s == carrier) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (GameMath.dist2(s.x, s.y, x, y) < minSpacing2) return false;
        }
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            double min = a.collisionRadius() + 26.0;
            if (GameMath.dist2(a.x, a.y, x, y) < min * min) return false;
        }
        return true;
    }

    private static ShipRole chooseCarrierRespawnRole(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return ShipRole.FRIGATE;
        ShipRole[] pool = {
                ShipRole.PATROL,
                ShipRole.PICKET,
                ShipRole.FRIGATE,
                ShipRole.MISSILE_BOAT,
                ShipRole.CIWS_CORVETTE,
                ShipRole.LIGHT_CRUISER,
                ShipRole.MEDIUM_CRUISER,
                ShipRole.CRUISER,
                ShipRole.TRANSPORT
        };
        int pick = ctx.rng.nextInt(pool.length);
        return pool[pick];
    }

    private static boolean isCarrierRespawnRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case PATROL, PICKET, FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE,
                    LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, TRANSPORT -> true;
            default -> false;
        };
    }

    private static int countAliveRoleForTeam(GameContext ctx, Faction team, ShipRole role) {
        if (ctx == null || team == null || role == null) return 0;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != role) continue;
            if (s.faction == null || s.faction.teamId() != team.teamId()) continue;
            count++;
        }
        return count;
    }

    private static Ship findLiveShipById(List<Ship> ships, int id) {
        if (ships == null || id <= 0) return null;
        for (Ship s : ships) {
            if (s != null && s.id == id && s.alive && !s.dying && s.hp > 0) return s;
        }
        return null;
    }

    private static int countTeamCombatShips(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return 0;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (!countsTowardFleetCombatTotals(s)) continue;
            if (s.faction == null || s.faction.teamId() != team.teamId()) continue;
            count++;
        }
        return count;
    }

    private static boolean countsTowardFleetCombatTotals(Ship s) {
        if (s == null) return false;
        if (!s.alive || s.dying || s.hp <= 0) return false;
        if (s.faction == null) return false;
        if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) return false;
        if (s.role == ShipRole.MINER || s.role == ShipRole.HAULER || s.role == ShipRole.TRANSPORT) return false;
        // Base support caps should track actual warships, not local utility craft or carrier-launched screens.
        if (s.isSmallCraft()) return false;
        if (s.carrierOwnerId >= 0) return false;
        return true;
    }

    private static void updateStationTurretStructures(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.config != null && ctx.config.mode == GameMode.SHOWCASE) return;

        java.util.LinkedHashSet<Ship> bases = new java.util.LinkedHashSet<>();
        if (ctx.allyBase != null) bases.add(ctx.allyBase);
        if (ctx.enemyBase != null) bases.add(ctx.enemyBase);
        if (ctx.teamBases != null) {
            for (Ship b : ctx.teamBases.values()) {
                if (b != null) bases.add(b);
            }
        }

        for (Ship base : bases) {
            if (base == null) continue;
            if (!base.alive || base.dying || base.hp <= 0) continue;
            if (base.role != ShipRole.BASE) continue;
            if (!base.canSpawnDefender()) continue;

            int combatNear = countCombatShipsForBase(ctx, base);
            int shipCapFromBase = Math.max(2, Math.min(12, base.maxDefenders));
            if (combatNear < shipCapFromBase) {
                Ship deployed = spawnCombatShipAtBase(ctx, base, combatNear, shipCapFromBase);
                if (deployed != null) {
                    base.resetBaseSpawnTimer();
                    continue;
                }
            }

            int current = countStationTurretsForBase(ctx, base);
            int capFromBase = Math.max(1, Math.min(MAX_STATION_TURRETS_PER_BASE, base.maxDefenders / 2));
            if (current >= capFromBase) continue;

            Ship turret = spawnStationTurretAtBase(ctx, base);
            if (turret == null) continue;

            turret.minerHomeBase = base; // ownership anchor for upkeep counting
            turret.vx = 0;
            turret.vy = 0;
            turret.desiredSpeed = 0;
            base.resetBaseSpawnTimer();
        }
    }

    private static int countStationTurretsForBase(GameContext ctx, Ship base) {
        int count = 0;
        double nearRange = (STATION_TURRET_RING_RADIUS + 160.0);
        double nearRange2 = nearRange * nearRange;

        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.STATIC_TURRET) continue;
            if (s.faction != base.faction) continue;

            if (s.minerHomeBase == base) {
                count++;
                continue;
            }

            // Backward-compatible fallback for older spawned turrets without anchor metadata.
            double d2 = GameMath.dist2(s.x, s.y, base.x, base.y);
            if (d2 <= nearRange2) count++;
        }
        return count;
    }

    private static int countCombatShipsForBase(GameContext ctx, Ship base) {
        if (ctx == null || base == null || base.faction == null) return 0;
        double maxD2 = BASE_SHIP_SUPPORT_RANGE * BASE_SHIP_SUPPORT_RANGE;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (!countsTowardFleetCombatTotals(s)) continue;
            if (s == base) continue;
            if (s.faction == null || s.faction.teamId() != base.faction.teamId()) continue;
            if (GameMath.dist2(s.x, s.y, base.x, base.y) > maxD2) continue;
            count++;
        }
        return count;
    }

    private static Ship spawnCombatShipAtBase(GameContext ctx, Ship base, int combatNear, int shipCapFromBase) {
        if (ctx == null || base == null || base.faction == null) return null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double a = ctx.rng.nextDouble() * Math.PI * 2.0;
            double r = BASE_SHIP_DEPLOY_RADIUS + (ctx.rng.nextDouble() - 0.5) * BASE_SHIP_DEPLOY_JITTER;
            double sx = base.x + Math.cos(a) * r;
            double sy = base.y + Math.sin(a) * r;
            sx = GameMath.clamp(sx, 30, ctx.WORLD_W - 30);
            sy = GameMath.clamp(sy, 30, ctx.WORLD_H - 30);
            if (!isCombatSpawnClear(ctx, base, sx, sy)) continue;

            ShipRole role = chooseCombatRoleForBase(ctx, base, combatNear, shipCapFromBase);
            Ship ship = SpawnSystem.spawnTeamShip(ctx, role, base.faction, sx, sy);
            if (ship == null) continue;

            // Freshly produced defenders roll out at reduced speed then join fleet AI next tick.
            ship.vx = base.vx * 0.6;
            ship.vy = base.vy * 0.6;
            ship.angle = base.angle;
            return ship;
        }
        return null;
    }

    private static boolean isCombatSpawnClear(GameContext ctx, Ship base, double x, double y) {
        if (ctx == null || base == null) return false;
        double minSpacing2 = BASE_SHIP_DEPLOY_MIN_SPACING * BASE_SHIP_DEPLOY_MIN_SPACING;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s == base) continue;
            if (GameMath.dist2(s.x, s.y, x, y) < minSpacing2) return false;
        }
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            double min = a.collisionRadius() + 32.0;
            if (GameMath.dist2(a.x, a.y, x, y) < min * min) return false;
        }
        return true;
    }

    private static ShipRole chooseCombatRoleForBase(GameContext ctx, Ship base, int combatNear, int shipCapFromBase) {
        if (ctx == null || base == null) return ShipRole.FRIGATE;
        CommanderPersonality personality = commanderPersonality(ctx, base.faction);
        EnumMap<CombatBucket, Integer> counts = countCombatBucketsForTeam(ctx, base.faction);
        int teamCombat = totalBucketCount(counts);
        int hangarTier = (base.faction == null) ? 0 : SpawnSystem.maxHangarTierForFaction(ctx, base.faction);
        int liveSuperships = (base.faction == null) ? 0 : countAliveRoleForTeam(ctx, base.faction, ShipRole.SUPERSHIP);
        EnumMap<CombatBucket, Integer> desired = desiredBucketsForTeam(teamCombat, personality);
        CombatBucket neededBucket = chooseNeededBucket(personality, counts, desired);

        double pressure = (shipCapFromBase <= 0) ? 1.0 : (combatNear / (double) shipCapFromBase);
        // Tier-3 teams should field flagship supers in a timely way instead of waiting for rare refit windows.
        if (hangarTier >= 3 && liveSuperships <= 0 && teamCombat >= Math.max(5, shipCapFromBase / 2) && pressure < 0.95) {
            if (ctx.rng.nextDouble() < 0.50) return ShipRole.SUPERSHIP;
        }
        if (pressure >= 0.82 && ctx.rng.nextDouble() < 0.72) {
            return chooseEscortPressureRole(ctx);
        }

        ShipRole role = chooseRoleForBucket(ctx, base, personality, neededBucket, pressure);
        if (role == null) role = ShipRole.FRIGATE;
        return role;
    }

    private static void applyFriendlyBaseRepairAuras(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        LinkedHashSet<Ship> bases = collectBases(ctx);
        for (Ship base : bases) {
            if (base == null) continue;
            if (base.role != ShipRole.BASE) continue;
            if (!base.alive || base.dying || base.hp <= 0) continue;

            double range = Math.max(BASE_REPAIR_MIN_RANGE, base.repairRange);
            double range2 = range * range;
            double supportMul = base.supportFieldMultiplier();
            double hullPerSec = Math.max(0.0, base.repairHullPerSec * supportMul);
            double shieldPerSec = Math.max(0.0, base.repairShieldPerSec * supportMul);
            if (hullPerSec <= 0.0 && shieldPerSec <= 0.0) continue;

            for (Ship ally : ctx.ships) {
                if (ally == null || ally == base) continue;
                if (!ally.alive || ally.dying || ally.hp <= 0) continue;
                if (ally.faction == null || base.faction == null) continue;
                if (ally.faction.teamId() != base.faction.teamId()) continue;
                if (ally.role == ShipRole.BASE || ally.role == ShipRole.STATIC_TURRET) continue;
                if (GameMath.dist2(ally.x, ally.y, base.x, base.y) > range2) continue;

                if (hullPerSec > 0.0) ally.healHull(hullPerSec * dt);
                if (shieldPerSec > 0.0) ally.healShield(shieldPerSec * dt);
            }
        }
    }

    private static void applyTransportSupportAuras(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        for (Ship transport : ctx.ships) {
            if (transport == null) continue;
            if (!transport.alive || transport.dying || transport.hp <= 0) continue;
            if (transport.role != ShipRole.TRANSPORT) continue;
            if (transport.faction == null) continue;

            double range = Math.max(TRANSPORT_SUPPORT_MIN_RANGE, transport.repairRange);
            double range2 = range * range;
            double supportMul = transport.supportFieldMultiplier();
            for (Ship ally : ctx.ships) {
                if (ally == null) continue;
                if (!ally.alive || ally.dying || ally.hp <= 0) continue;
                if (ally.faction == null || ally.faction.teamId() != transport.faction.teamId()) continue;
                if (ally.role == ShipRole.BASE || ally.role == ShipRole.STATIC_TURRET) continue;
                if (GameMath.dist2(ally.x, ally.y, transport.x, transport.y) > range2) continue;

                ally.applySupportField(
                        TRANSPORT_ROOM_HEAL_FRAC_PER_SEC * supportMul,
                        TRANSPORT_FIRE_REDUCTION_FRAC_PER_SEC * supportMul,
                        dt
                );
            }
        }
    }

    private static void updateNpcBaseUpgradePrograms(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        EnumMap<Faction, Double> teamTimers = teamBaseUpgradeTimers(ctx);
        EnumSet<Faction> teams = EnumSet.noneOf(Faction.class);
        teams.addAll(ctx.teamBases.keySet());
        if (ctx.allyBase != null) teams.add(Faction.ALLY);
        if (ctx.enemyBase != null) teams.add(Faction.ENEMY);

        for (Faction team : teams) {
            if (team == null) continue;
            if (!TeamSystem.isTeamAlive(ctx, team)) continue;

            Ship base = getBaseForFaction(ctx, team);
            if (base == null || !base.alive || base.dying || base.hp <= 0) continue;

            double timer = teamTimers.getOrDefault(team, NPC_BASE_UPGRADE_CHECK_INTERVAL * (0.55 + ctx.rng.nextDouble() * 0.85));
            timer -= dt;
            if (timer > 0.0) {
                teamTimers.put(team, timer);
                continue;
            }

            teamTimers.put(team, NPC_BASE_UPGRADE_CHECK_INTERVAL * (0.85 + ctx.rng.nextDouble() * 0.45));
            for (int i = 0; i < NPC_BASE_UPGRADE_PASSES; i++) {
                if (!runTeamBaseUpgradePass(ctx, team, base)) break;
            }
        }
    }

    private static boolean runTeamBaseUpgradePass(GameContext ctx, Faction team, Ship base) {
        if (ctx == null || team == null || base == null) return false;
        if (!base.alive || base.dying || base.hp <= 0) return false;
        if (base.faction == null || base.faction.teamId() != team.teamId()) return false;

        BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(base, k -> new BaseUpgrades());
        int oreBudget = Math.max(0, base.oreStockpile - npcOreReserveForBase(base, up));
        if (oreBudget <= 0) return false;

        CommanderPersonality personality = commanderPersonality(ctx, team);
        int hangarCandidate = (up.hangarLv < 3) ? 5 : 0;
        int chosen = 0;

        if (hangarCandidate != 0 && shouldPrioritizeHangarUpgrade(ctx, team, base, up, personality)) {
            int cost = npcBaseUpgradeOreEquivalent(hangarCandidate, up.hangarLv + 1);
            if (oreBudget >= cost) chosen = hangarCandidate;
        }

        if (chosen == 0) {
            List<Integer> order = baseUpgradePriorityOrder(ctx, team, base, up, personality);
            for (Integer which : order) {
                if (which == null) continue;
                int level = baseUpgradeLevel(up, which);
                int max = baseUpgradeMaxLevel(which);
                if (level >= max) continue;
                int cost = npcBaseUpgradeOreEquivalent(which, level + 1);
                if (cost <= 0 || oreBudget < cost) continue;
                chosen = which;
                break;
            }
        }

        if (chosen == 0) return false;
        int nextLv = baseUpgradeLevel(up, chosen) + 1;
        int cost = npcBaseUpgradeOreEquivalent(chosen, nextLv);
        if (cost <= 0 || base.oreStockpile < cost) return false;

        base.oreStockpile -= cost;
        applyBaseUpgrade(ctx, base, up, chosen);
        return true;
    }

    private static boolean shouldPrioritizeHangarUpgrade(GameContext ctx, Faction team, Ship base, BaseUpgrades up,
                                                         CommanderPersonality personality) {
        if (ctx == null || team == null || base == null || up == null) return false;
        if (up.hangarLv >= 3) return false;
        if (up.hangarLv <= 0) return true;

        EnumMap<CombatBucket, Integer> buckets = countCombatBucketsForTeam(ctx, team);
        int combatShips = totalBucketCount(buckets);
        int defendersNear = countCombatShipsForBase(ctx, base);
        int pressureTarget = 5 + up.hangarLv * 3;
        if (combatShips >= pressureTarget || defendersNear >= pressureTarget) return true;

        if (personality == CommanderPersonality.FLAGSHIP_CORE || personality == CommanderPersonality.CARRIER_GROUP
                || personality == CommanderPersonality.STEALTH_RAIDERS) {
            return ctx.rng.nextDouble() < 0.58;
        }
        return ctx.rng.nextDouble() < 0.32;
    }

    private static List<Integer> baseUpgradePriorityOrder(GameContext ctx, Faction team, Ship base, BaseUpgrades up,
                                                          CommanderPersonality personality) {
        List<Integer> order = new ArrayList<>(6);
        boolean underFire = hasHostileNear(ctx, base, NPC_REFIT_THREAT_RANGE * 1.25)
                || hasIncomingThreat(ctx, base, NPC_REFIT_THREAT_RANGE * 0.95);
        double hullFrac = base.hp / (double) Math.max(1, base.hpMax);
        double shieldFrac = (base.shieldMax > 1e-9) ? (base.shield / base.shieldMax) : 1.0;

        if (underFire || hullFrac < 0.78 || shieldFrac < 0.55) {
            addUniqueUpgrade(order, 2);
            addUniqueUpgrade(order, 1);
            addUniqueUpgrade(order, 3);
        } else {
            addUniqueUpgrade(order, 3);
            addUniqueUpgrade(order, 2);
            addUniqueUpgrade(order, 1);
        }

        if (up != null && up.hangarLv < 3) addUniqueUpgrade(order, 5);

        if (personality == CommanderPersonality.ARTILLERY_LINE) {
            moveUpgradeToFront(order, 3);
        } else if (personality == CommanderPersonality.CARRIER_GROUP || personality == CommanderPersonality.FLAGSHIP_CORE
                || personality == CommanderPersonality.STEALTH_RAIDERS) {
            moveUpgradeToFront(order, 5);
        } else if (personality == CommanderPersonality.ESCORT_WING) {
            moveUpgradeToFront(order, 2);
        }
        return order;
    }

    private static void applyBaseUpgrade(GameContext ctx, Ship base, BaseUpgrades up, int which) {
        if (ctx == null || base == null || up == null) return;
        switch (which) {
            case 1 -> {
                if (up.hullLv >= 5) return;
                up.hullLv++;
                base.hpMax += 40;
                base.healHull(40);
            }
            case 2 -> {
                if (up.shieldLv >= 5) return;
                up.shieldLv++;
                base.shieldMax += 30.0;
                base.shieldRegen += 0.8;
                base.shieldActive = true;
                base.shield += 30.0;
            }
            case 3 -> {
                if (up.turretLv >= 5) return;
                up.turretLv++;
                UISystem.applyTurretSystemsUpgrade(base, 1);
            }
            case 4 -> {
                if (up.miningLv >= 5) return;
                up.miningLv++;
                ctx.miningBaseMul = Math.min(2.0, ctx.miningBaseMul + 0.06);
                ctx.orePriceBaseMul = Math.min(2.0, ctx.orePriceBaseMul + 0.05);
            }
            case 5 -> {
                if (up.hangarLv >= 3) return;
                up.hangarLv++;
            }
            default -> {
                return;
            }
        }
    }

    private static int npcOreReserveForBase(Ship base, BaseUpgrades up) {
        int reserve = NPC_BASE_UPGRADE_MIN_ORE_RESERVE;
        if (base != null) reserve += Math.max(0, Math.min(260, base.maxDefenders * 12));
        if (up != null) reserve += Math.max(0, (3 - Math.min(3, up.hangarLv)) * 70);
        return reserve;
    }

    private static int npcBaseUpgradeOreEquivalent(int which, int nextLv) {
        if (which <= 0 || nextLv <= 0) return Integer.MAX_VALUE;
        int oreCost = baseUpgradeOreCost(which, nextLv);
        int creditCost = baseUpgradeCreditCost(which, nextLv);
        if (oreCost == Integer.MAX_VALUE || creditCost == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return oreCost + (int) Math.round(creditCost * 0.32);
    }

    private static int baseUpgradeCreditCost(int which, int nextLv) {
        if (nextLv <= 0) return Integer.MAX_VALUE;
        return switch (which) {
            case 1 -> 150 + 200 * nextLv;
            case 2 -> 170 + 210 * nextLv;
            case 3 -> 210 + 250 * nextLv;
            case 4 -> 140 + 170 * nextLv;
            case 5 -> 380 + 420 * nextLv;
            default -> Integer.MAX_VALUE;
        };
    }

    private static int baseUpgradeOreCost(int which, int nextLv) {
        if (nextLv <= 0) return Integer.MAX_VALUE;
        return switch (which) {
            case 1 -> 40 + 70 * nextLv;
            case 2 -> 50 + 80 * nextLv;
            case 3 -> 60 + 90 * nextLv;
            case 4 -> 40 + 110 * nextLv;
            case 5 -> 100 + 170 * nextLv;
            default -> Integer.MAX_VALUE;
        };
    }

    private static int baseUpgradeMaxLevel(int which) {
        return switch (which) {
            case 5 -> 3;
            case 1, 2, 3, 4 -> 5;
            default -> 0;
        };
    }

    private static int baseUpgradeLevel(BaseUpgrades up, int which) {
        if (up == null) return 0;
        return switch (which) {
            case 1 -> up.hullLv;
            case 2 -> up.shieldLv;
            case 3 -> up.turretLv;
            case 4 -> up.miningLv;
            case 5 -> up.hangarLv;
            default -> 0;
        };
    }

    private static void addUniqueUpgrade(List<Integer> out, int which) {
        if (out == null || which <= 0) return;
        if (!out.contains(which)) out.add(which);
    }

    private static void moveUpgradeToFront(List<Integer> order, int which) {
        if (order == null || which <= 0) return;
        if (!order.remove(Integer.valueOf(which))) return;
        order.add(0, which);
    }

    private static void updateNpcRefitPrograms(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        Map<Integer, Double> cooldowns = shipRefitCooldowns(ctx);
        if (!cooldowns.isEmpty()) {
            List<Integer> expired = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : cooldowns.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                double t = Math.max(0.0, e.getValue() - dt);
                if (t <= 0.0) expired.add(e.getKey());
                else e.setValue(t);
            }
            for (Integer key : expired) cooldowns.remove(key);
        }

        EnumMap<Faction, Double> teamTimers = teamRefitTimers(ctx);
        EnumSet<Faction> teams = EnumSet.noneOf(Faction.class);
        teams.addAll(ctx.teamBases.keySet());
        if (ctx.allyBase != null) teams.add(Faction.ALLY);
        if (ctx.enemyBase != null) teams.add(Faction.ENEMY);

        for (Faction team : teams) {
            if (team == null) continue;
            if (!TeamSystem.isTeamAlive(ctx, team)) continue;

            double timer = teamTimers.getOrDefault(team, NPC_REFIT_CHECK_INTERVAL * (0.35 + ctx.rng.nextDouble() * 0.65));
            timer -= dt;
            if (timer > 0.0) {
                teamTimers.put(team, timer);
                continue;
            }

            teamTimers.put(team, NPC_REFIT_CHECK_INTERVAL * (0.80 + ctx.rng.nextDouble() * 0.40));
            runTeamRefitPass(ctx, team);
        }
    }

    private static void runTeamRefitPass(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return;
        Ship base = getBaseForFaction(ctx, team);
        if (base == null || !base.alive || base.dying || base.hp <= 0) return;
        int maxHangarTier = SpawnSystem.maxHangarTierForFaction(ctx, team);

        CommanderPersonality personality = commanderPersonality(ctx, team);
        EnumMap<CombatBucket, Integer> counts = countCombatBucketsForTeam(ctx, team);
        EnumMap<CombatBucket, Integer> desired = desiredBucketsForTeam(totalBucketCount(counts), personality);
        List<Ship> candidates = collectRefitCandidates(ctx, team);
        if (candidates.isEmpty()) return;
        candidates.sort(Comparator.comparingDouble(EconomySystem::shipCombatScore));

        int refits = 0;
        Map<Integer, Double> cooldowns = shipRefitCooldowns(ctx);
        for (Ship ship : candidates) {
            if (refits >= NPC_REFITS_PER_TEAM_PASS) break;
            if (ship == null) continue;
            if (cooldowns.getOrDefault(ship.id, 0.0) > 0.0) continue;
            if (!isShipDockedAndSafeForRefit(ctx, ship, base)) continue;

            CombatBucket targetBucket = chooseNeededBucket(personality, counts, desired);
            ShipRole nextRole = chooseUpgradeTargetRole(ship, personality, targetBucket, base.oreStockpile, maxHangarTier);
            if (nextRole == null || nextRole == ship.role) continue;

            int oreCost = refitOreCost(ship.role, nextRole);
            if (oreCost <= 0 || oreCost == Integer.MAX_VALUE) continue;
            if (base.oreStockpile < oreCost) continue;

            ShipRole oldRole = ship.role;
            if (!applyNpcHullRefit(ctx, ship, nextRole, base)) continue;

            base.oreStockpile -= oreCost;
            cooldowns.put(ship.id, NPC_REFIT_COOLDOWN_SEC);
            addBucket(counts, bucketForRole(oldRole), -1);
            addBucket(counts, bucketForRole(nextRole), +1);
            refits++;
        }
    }

    private static List<Ship> collectRefitCandidates(GameContext ctx, Faction team) {
        List<Ship> out = new ArrayList<>();
        if (ctx == null || team == null) return out;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s instanceof Player) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.teamId() != team.teamId()) continue;
            if (s.carrierOwnerId >= 0) continue;
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) continue;
            if (s.role == ShipRole.MINER || s.role == ShipRole.HAULER || s.role == ShipRole.TRANSPORT) continue;
            out.add(s);
        }
        return out;
    }

    private static boolean isShipDockedAndSafeForRefit(GameContext ctx, Ship ship, Ship base) {
        if (ctx == null || ship == null || base == null) return false;
        double dockRange = Math.max(NPC_REFIT_DOCK_RANGE, base.radius + 130.0);
        if (GameMath.dist2(ship.x, ship.y, base.x, base.y) > dockRange * dockRange) return false;
        if (ship.hp <= 0 || ship.hpMax <= 0) return false;

        double hullFrac = ship.hp / (double) Math.max(1, ship.hpMax);
        double shieldFrac = (ship.shieldMax > 1e-9) ? (ship.shield / ship.shieldMax) : 1.0;
        if (hullFrac < 0.45 || shieldFrac < 0.15) return false;
        if (hasHostileNear(ctx, ship, NPC_REFIT_THREAT_RANGE)) return false;
        if (hasIncomingThreat(ctx, ship, NPC_REFIT_THREAT_RANGE * 0.65)) return false;
        return true;
    }

    private static boolean hasHostileNear(GameContext ctx, Ship ship, double range) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        double range2 = range * range;
        for (Ship other : ctx.ships) {
            if (other == null || other == ship) continue;
            if (!other.alive || other.dying || other.hp <= 0) continue;
            if (other.faction == null) continue;
            if (other.faction.teamId() == ship.faction.teamId()) continue;
            if (other.role == ShipRole.BASE || other.role == ShipRole.STATIC_TURRET) continue;
            if (other.role == ShipRole.MINER || other.role == ShipRole.HAULER || other.role == ShipRole.TRANSPORT) continue;
            if (GameMath.dist2(other.x, other.y, ship.x, ship.y) <= range2) return true;
        }
        return false;
    }

    private static boolean hasIncomingThreat(GameContext ctx, Ship ship, double range) {
        if (ctx == null || ship == null || ship.faction == null) return false;
        double range2 = range * range;
        for (Projectile p : ctx.projectiles) {
            if (p == null || !p.alive) continue;
            if (p.faction == null) continue;
            if (p.faction.teamId() == ship.faction.teamId()) continue;
            if (GameMath.dist2(p.x, p.y, ship.x, ship.y) <= range2) return true;
        }
        return false;
    }

    private static ShipRole chooseUpgradeTargetRole(Ship ship, CommanderPersonality personality, CombatBucket targetBucket,
                                                    int availableOre, int maxHangarTier) {
        if (ship == null) return null;
        ShipRole current = ship.role;
        if (current == null) return null;
        double currentScore = roleCombatScore(current);
        if (currentScore <= 0.0) return null;

        List<ShipRole> candidates = new ArrayList<>();
        addRoles(candidates, rolesForBucket(targetBucket, personality));
        addRoles(candidates, rolesForBucket(bucketForRole(current), personality));
        if (personality == CommanderPersonality.STEALTH_RAIDERS) {
            addRoles(candidates, ShipRole.STEALTH_SHIP, ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER);
        } else if (personality == CommanderPersonality.CARRIER_GROUP) {
            addRoles(candidates, ShipRole.DRONE_CARRIER, ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.DREADNOUGHT);
        }
        addRoles(candidates, defaultUpgradePath(current));

        for (ShipRole candidate : candidates) {
            if (candidate == null || candidate == current) continue;
            if (SpawnSystem.requiredHangarTierForRole(candidate) > maxHangarTier) continue;
            double nextScore = roleCombatScore(candidate);
            if (nextScore <= currentScore + 8.0) continue;
            if (nextScore > currentScore * NPC_REFIT_MAX_JUMP_MUL) continue;
            int oreCost = refitOreCost(current, candidate);
            if (oreCost == Integer.MAX_VALUE || oreCost <= 0) continue;
            if (availableOre < oreCost) continue;
            return candidate;
        }
        return null;
    }

    private static boolean applyNpcHullRefit(GameContext ctx, Ship ship, ShipRole role, Ship homeBase) {
        if (ctx == null || ship == null || role == null) return false;
        if (ship.faction == null) return false;
        if (ship instanceof Player) return false;
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) return false;

        FleetShip template = new FleetShip(role, ship.faction, ship.x, ship.y);
        try { DoctrineRegistry.applyToShip(template); } catch (Throwable ignored) {}

        double preserveAngle = ship.angle;
        double preserveVx = ship.vx;
        double preserveVy = ship.vy;
        Ship.PowerPreset preservePreset = ship.powerPreset;
        Ship.CrewOrder preserveCrew = ship.crewOrder;
        double[] preserveBuses = ship.powerBusFractions();
        Ship.EngineeringPriority preservePriority = ship.engineeringPriority();
        Ship.PowerBus preserveOverloadBus = ship.overloadBus();
        GameContext.FleetCommand override = (ctx.command.shipFleetCommandOverrides == null) ? null : ctx.command.shipFleetCommandOverrides.get(ship.id);

        copyShipFromTemplate(ship, template);

        ship.angle = preserveAngle;
        ship.vx = preserveVx * 0.25 + ((homeBase == null) ? 0.0 : homeBase.vx * 0.35);
        ship.vy = preserveVy * 0.25 + ((homeBase == null) ? 0.0 : homeBase.vy * 0.35);
        ship.setPowerBusAllocation(
                preserveBuses[0], preserveBuses[1], preserveBuses[2],
                preserveBuses[3], preserveBuses[4], preserveBuses[5]
        );
        ship.powerPreset = preservePreset;
        ship.crewOrder = preserveCrew;
        ship.setEngineeringPriority(preservePriority);
        ship.setOverloadBus(preserveOverloadBus);
        ship.setOverloadMode(false);
        ship.minerHomeBase = homeBase;
        ship.hp = ship.hpMax;
        ship.shield = ship.shieldMax;
        ship.alive = true;
        ship.dying = false;
        ship.bountyClaimed = false;
        ship.playerTaggedForKillCredit = false;
        ship.playerKillCreditPaid = false;
        ship.minerTarget = null;
        ship.miningTarget = null;
        ship.resetShieldState();
        ship.resetInternalSystems();
        ship.clearHullImpactMarks();
        if (override != null && ctx.command.shipFleetCommandOverrides != null) {
            ctx.command.shipFleetCommandOverrides.put(ship.id, override);
        }
        return true;
    }

    private static void copyShipFromTemplate(Ship dst, FleetShip src) {
        if (dst == null || src == null) return;
        dst.name = src.name;
        dst.faction = src.faction;
        dst.role = src.role;
        dst.radius = src.radius;

        dst.hpMax = src.hpMax;
        dst.hp = src.hp;
        dst.shieldMax = src.shieldMax;
        dst.shield = src.shield;
        dst.shieldRegen = src.shieldRegen;
        dst.shieldActive = src.shieldActive;
        dst.shieldRebootDelay = src.shieldRebootDelay;
        dst.shieldFacingMode = src.shieldFacingMode;
        dst.shieldFacingAngle = src.shieldFacingAngle;
        dst.shieldAutoTrackRate = src.shieldAutoTrackRate;
        dst.shieldDirectionalArc = src.shieldDirectionalArc;
        dst.desiredSpeed = src.desiredSpeed;
        dst.desiredSpeedBase = (src.desiredSpeedBase > 0.0) ? src.desiredSpeedBase : src.desiredSpeed;
        dst.bountyValue = src.bountyValue;

        dst.cargo = Math.min(dst.cargo, Math.max(0, src.cargoMax));
        dst.cargoMax = src.cargoMax;
        dst.miningRate = src.miningRate;
        dst.miningRange = src.miningRange;

        dst.isStealth = src.isStealth;
        dst.signature = src.signature;
        dst.revealTimer = src.revealTimer;
        dst.cloakEnabled = src.cloakEnabled;
        dst.cloakActive = src.cloakActive;
        dst.cloakEnergyMax = src.cloakEnergyMax;
        dst.cloakEnergy = src.cloakEnergy;
        dst.cloakDrainPerSec = src.cloakDrainPerSec;
        dst.cloakRechargePerSec = src.cloakRechargePerSec;
        dst.cloakMinEnergyToEngage = src.cloakMinEnergyToEngage;
        dst.cloakSignature = src.cloakSignature;

        dst.hasCIWS = src.hasCIWS;
        dst.ciwsRange = src.ciwsRange;
        dst.ciwsCooldown = src.ciwsCooldown;
        dst.ciwsQuality = src.ciwsQuality;
        dst.ciwsPelletsPerBurst = src.ciwsPelletsPerBurst;
        dst.ciwsPelletSpeed = src.ciwsPelletSpeed;
        dst.ciwsPelletDamage = src.ciwsPelletDamage;
        dst.ciwsPelletLife = src.ciwsPelletLife;
        dst.ciwsPelletRadius = src.ciwsPelletRadius;

        dst.hasSuperweapon = src.hasSuperweapon;
        dst.superweaponPattern = src.superweaponPattern;
        dst.superweaponChargeTime = src.superweaponChargeTime;
        dst.superweaponCooldown = src.superweaponCooldown;
        dst.superweaponDamage = src.superweaponDamage;
        dst.superweaponSpeed = src.superweaponSpeed;
        dst.superweaponLife = src.superweaponLife;
        dst.superweaponRadius = src.superweaponRadius;
        dst.superweaponMaxHits = src.superweaponMaxHits;
        dst.superweaponBeamDuration = src.superweaponBeamDuration;
        dst.superweaponBeamTickInterval = src.superweaponBeamTickInterval;
        dst.superweaponBeamDamageScale = src.superweaponBeamDamageScale;
        dst.resetSuperweaponCooldown();

        dst.isCarrier = src.isCarrier;
        dst.fighterLaunchCooldown = src.fighterLaunchCooldown;
        dst.maxFighters = src.maxFighters;
        dst.carrierCommandMode = src.carrierCommandMode;
        dst.carrierAutoLaunch = src.carrierAutoLaunch;
        dst.wingState = Ship.WingState.ATTACK;
        dst.carrierOwnerId = -1;
        dst.carrierOrphanTimer = -1.0;
        dst.flightDeckLaunchCursor = src.flightDeckLaunchCursor;
        for (int i = 0; i < dst.flightDeckLoadout.length; i++) {
            dst.flightDeckLoadout[i] = src.flightDeckRoleAt(i);
        }
        dst.strikePrimaryMunitionsMax = src.strikePrimaryMunitionsMax;
        dst.strikePrimaryMunitions = src.strikePrimaryMunitions;
        dst.strikeSecondaryMunitionsMax = src.strikeSecondaryMunitionsMax;
        dst.strikeSecondaryMunitions = src.strikeSecondaryMunitions;

        dst.isBase = src.isBase;
        dst.baseOwner = src.baseOwner;
        dst.captureProgress = src.captureProgress;
        dst.captureRadius = src.captureRadius;
        dst.captureTime = src.captureTime;
        dst.baseSpawnCooldown = src.baseSpawnCooldown;
        dst.maxDefenders = src.maxDefenders;
        dst.repairRange = src.repairRange;
        dst.repairHullPerSec = src.repairHullPerSec;
        dst.repairShieldPerSec = src.repairShieldPerSec;

        dst.primaryWeaponFamily = src.primaryWeaponFamily;
        dst.turrets.clear();
        for (Turret turret : src.turrets) {
            if (turret == null) continue;
            Turret nt = new Turret(turret.kind, turret.localX, turret.localY);
            nt.turnRate = turret.turnRate;
            nt.cooldown = turret.cooldown;
            nt.damage = turret.damage;
            nt.bulletSpeed = turret.bulletSpeed;
            nt.bulletLife = turret.bulletLife;
            nt.missileSpeed = turret.missileSpeed;
            nt.missileTurnRate = turret.missileTurnRate;
            nt.missileLife = turret.missileLife;
            nt.radius = turret.radius;
            nt.barrelLen = turret.barrelLen;
            nt.primary = turret.primary;
            dst.addTurret(nt);
        }
        dst.applyPrimaryWeaponFamily();
    }

    private static ShipRole chooseEscortPressureRole(GameContext ctx) {
        double roll = ctx.rng.nextDouble();
        if (roll < 0.42) return ShipRole.CIWS_CORVETTE;
        if (roll < 0.76) return ShipRole.PICKET;
        if (roll < 0.90) return ShipRole.FRIGATE;
        return ShipRole.PATROL;
    }

    private static ShipRole chooseRoleForBucket(GameContext ctx, Ship base, CommanderPersonality personality,
                                                CombatBucket bucket, double pressure) {
        if (ctx == null || bucket == null) return ShipRole.FRIGATE;
        int hangarTier = (base == null || base.faction == null) ? 0 : SpawnSystem.maxHangarTierForFaction(ctx, base.faction);
        double roll = ctx.rng.nextDouble();
        return switch (bucket) {
            case FLAGSHIP -> {
                if (personality == CommanderPersonality.CARRIER_GROUP && roll < 0.25) {
                    if (base != null && base.maxDefenders >= 10) yield ShipRole.CARRIER;
                    yield ShipRole.DRONE_CARRIER;
                }
                if (hangarTier >= 3 && base != null && base.maxDefenders >= 10 && pressure < 0.68 && roll < 0.14) yield ShipRole.SUPERSHIP;
                if (hangarTier >= 3 && base != null && base.maxDefenders >= 10 && pressure < 0.74 && roll < 0.28) yield ShipRole.DREADNOUGHT;
                if (base != null && base.maxDefenders >= 10 && pressure < 0.82 && roll < 0.48) yield ShipRole.BATTLESHIP;
                if (roll < 0.80) yield ShipRole.BATTLECRUISER;
                yield ShipRole.LIGHT_CRUISER;
            }
            case ESCORT -> {
                if (roll < 0.34) yield ShipRole.CIWS_CORVETTE;
                if (roll < 0.62) yield ShipRole.PICKET;
                if (roll < 0.86) yield ShipRole.FRIGATE;
                yield ShipRole.PATROL;
            }
            case ARTILLERY -> {
                if (base != null && base.maxDefenders >= 10 && pressure < 0.78 && roll < 0.16) yield ShipRole.BATTLECRUISER;
                if (roll < 0.24) yield ShipRole.ARTILLERY_SHIP;
                if (roll < 0.52) yield ShipRole.MISSILE_BOAT;
                if (roll < 0.76) yield ShipRole.LIGHT_CRUISER;
                if (roll < 0.90) yield ShipRole.MEDIUM_CRUISER;
                yield ShipRole.CRUISER;
            }
            case STEALTH -> {
                if (roll < 0.78) yield ShipRole.STEALTH_SHIP;
                yield ShipRole.PICKET;
            }
            case CARRIER -> {
                if (base != null && base.maxDefenders >= 10 && pressure < 0.74 && roll < 0.56) yield ShipRole.CARRIER;
                if (roll < 0.88) yield ShipRole.DRONE_CARRIER;
                yield ShipRole.FRIGATE;
            }
            case LINE -> {
                if (base != null && base.maxDefenders >= 10 && pressure < 0.74 && roll < 0.14) yield ShipRole.BATTLECRUISER;
                if (roll < 0.46) yield ShipRole.FRIGATE;
                if (roll < 0.76) yield ShipRole.LIGHT_CRUISER;
                if (roll < 0.92) yield ShipRole.MEDIUM_CRUISER;
                yield ShipRole.CRUISER;
            }
        };
    }

    private static ShipRole[] rolesForBucket(CombatBucket bucket, CommanderPersonality personality) {
        if (bucket == null) return new ShipRole[0];
        return switch (bucket) {
            case FLAGSHIP -> {
                if (personality == CommanderPersonality.CARRIER_GROUP) {
                    yield new ShipRole[]{ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
                }
                yield new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
            }
            case ESCORT -> new ShipRole[]{ShipRole.CIWS_CORVETTE, ShipRole.PICKET, ShipRole.FRIGATE, ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER};
            case ARTILLERY -> new ShipRole[]{ShipRole.ARTILLERY_SHIP, ShipRole.MISSILE_BOAT, ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP};
            case STEALTH -> new ShipRole[]{ShipRole.STEALTH_SHIP, ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER};
            case CARRIER -> new ShipRole[]{ShipRole.DRONE_CARRIER, ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.DREADNOUGHT};
            case LINE -> new ShipRole[]{ShipRole.FRIGATE, ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP};
        };
    }

    private static ShipRole[] defaultUpgradePath(ShipRole role) {
        if (role == null) return new ShipRole[0];
        return switch (role) {
            case PATROL -> new ShipRole[]{ShipRole.PICKET, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE, ShipRole.STEALTH_SHIP, ShipRole.LIGHT_CRUISER};
            case PICKET -> new ShipRole[]{ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE, ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.BATTLECRUISER};
            case FRIGATE -> new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP};
            case ARTILLERY_SHIP -> new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP};
            case CIWS_CORVETTE -> new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP};
            case MISSILE_BOAT -> new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP};
            case STEALTH_SHIP -> new ShipRole[]{ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER};
            case LIGHT_CRUISER -> new ShipRole[]{ShipRole.MEDIUM_CRUISER, ShipRole.CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT};
            case MEDIUM_CRUISER -> new ShipRole[]{ShipRole.CRUISER, ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
            case CRUISER -> new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
            case BATTLECRUISER -> new ShipRole[]{ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
            case BATTLESHIP -> new ShipRole[]{ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
            case DREADNOUGHT -> new ShipRole[]{ShipRole.SUPERSHIP};
            case DRONE_CARRIER -> new ShipRole[]{ShipRole.CARRIER, ShipRole.DREADNOUGHT};
            case CARRIER -> new ShipRole[]{ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP};
            default -> new ShipRole[0];
        };
    }

    private static void addRoles(List<ShipRole> out, ShipRole... roles) {
        if (out == null || roles == null) return;
        for (ShipRole r : roles) {
            if (r == null) continue;
            if (!out.contains(r)) out.add(r);
        }
    }

    private static CombatBucket chooseNeededBucket(CommanderPersonality personality,
                                                   EnumMap<CombatBucket, Integer> counts,
                                                   EnumMap<CombatBucket, Integer> desired) {
        CombatBucket best = personalityDefaultBucket(personality);
        double bestScore = 0.0;
        for (CombatBucket bucket : CombatBucket.values()) {
            int c = counts.getOrDefault(bucket, 0);
            int d = desired.getOrDefault(bucket, 0);
            int deficit = d - c;
            double score = deficit * bucketPriority(bucket, personality);
            if (score > bestScore) {
                bestScore = score;
                best = bucket;
            }
        }
        return best;
    }

    private static double bucketPriority(CombatBucket bucket, CommanderPersonality personality) {
        if (bucket == null) return 1.0;
        double base = switch (bucket) {
            case FLAGSHIP -> 1.30;
            case ESCORT -> 1.10;
            case ARTILLERY -> 1.12;
            case STEALTH -> 1.00;
            case CARRIER -> 1.00;
            case LINE -> 0.92;
        };
        if (personality == null) return base;
        return switch (personality) {
            case FLAGSHIP_CORE -> base + ((bucket == CombatBucket.FLAGSHIP) ? 0.45 : (bucket == CombatBucket.ESCORT ? 0.18 : 0.0));
            case ESCORT_WING -> base + ((bucket == CombatBucket.ESCORT) ? 0.42 : 0.0);
            case ARTILLERY_LINE -> base + ((bucket == CombatBucket.ARTILLERY) ? 0.46 : 0.0);
            case STEALTH_RAIDERS -> base + ((bucket == CombatBucket.STEALTH) ? 0.60 : 0.0);
            case CARRIER_GROUP -> base + ((bucket == CombatBucket.CARRIER) ? 0.60 : (bucket == CombatBucket.ESCORT ? 0.12 : 0.0));
            default -> base;
        };
    }

    private static CombatBucket personalityDefaultBucket(CommanderPersonality personality) {
        if (personality == null) return CombatBucket.LINE;
        return switch (personality) {
            case FLAGSHIP_CORE -> CombatBucket.FLAGSHIP;
            case ESCORT_WING -> CombatBucket.ESCORT;
            case ARTILLERY_LINE -> CombatBucket.ARTILLERY;
            case STEALTH_RAIDERS -> CombatBucket.STEALTH;
            case CARRIER_GROUP -> CombatBucket.CARRIER;
            default -> CombatBucket.LINE;
        };
    }

    private static EnumMap<CombatBucket, Integer> desiredBucketsForTeam(int combatCount, CommanderPersonality personality) {
        EnumMap<CombatBucket, Integer> desired = new EnumMap<>(CombatBucket.class);
        for (CombatBucket bucket : CombatBucket.values()) desired.put(bucket, 0);
        int total = Math.max(1, combatCount);

        double escortFrac = 0.28;
        double artilleryFrac = 0.22;
        double stealthFrac = 0.10;
        double carrierFrac = 0.10;

        if (personality == CommanderPersonality.FLAGSHIP_CORE) {
            escortFrac = 0.34;
            artilleryFrac = 0.21;
            stealthFrac = 0.05;
            carrierFrac = 0.08;
        } else if (personality == CommanderPersonality.ESCORT_WING) {
            escortFrac = 0.43;
            artilleryFrac = 0.16;
            stealthFrac = 0.08;
            carrierFrac = 0.06;
        } else if (personality == CommanderPersonality.ARTILLERY_LINE) {
            escortFrac = 0.22;
            artilleryFrac = 0.36;
            stealthFrac = 0.04;
            carrierFrac = 0.12;
        } else if (personality == CommanderPersonality.STEALTH_RAIDERS) {
            escortFrac = 0.24;
            artilleryFrac = 0.15;
            stealthFrac = 0.30;
            carrierFrac = 0.08;
        } else if (personality == CommanderPersonality.CARRIER_GROUP) {
            escortFrac = 0.26;
            artilleryFrac = 0.18;
            stealthFrac = 0.06;
            carrierFrac = 0.26;
        }

        int flagship = (combatCount >= 11) ? 2 : 1;
        int escort = Math.max(2, (int) Math.round(total * escortFrac));
        int artillery = Math.max(1, (int) Math.round(total * artilleryFrac));
        int stealth = Math.max(0, (int) Math.round(total * stealthFrac));
        int carrier = Math.max(0, (int) Math.round(total * carrierFrac));

        desired.put(CombatBucket.FLAGSHIP, flagship);
        desired.put(CombatBucket.ESCORT, escort);
        desired.put(CombatBucket.ARTILLERY, artillery);
        desired.put(CombatBucket.STEALTH, stealth);
        desired.put(CombatBucket.CARRIER, carrier);
        int used = flagship + escort + artillery + stealth + carrier;
        int line = Math.max(0, total - used);
        if (line == 0 && combatCount >= 6) line = 1;
        desired.put(CombatBucket.LINE, line);
        return desired;
    }

    private static EnumMap<CombatBucket, Integer> countCombatBucketsForTeam(GameContext ctx, Faction team) {
        EnumMap<CombatBucket, Integer> out = new EnumMap<>(CombatBucket.class);
        for (CombatBucket b : CombatBucket.values()) out.put(b, 0);
        if (ctx == null || team == null) return out;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.teamId() != team.teamId()) continue;
            if (s.carrierOwnerId >= 0) continue;
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) continue;
            if (s.role == ShipRole.MINER || s.role == ShipRole.HAULER || s.role == ShipRole.TRANSPORT) continue;
            addBucket(out, bucketForRole(s.role), 1);
        }
        return out;
    }

    private static void addBucket(EnumMap<CombatBucket, Integer> buckets, CombatBucket bucket, int delta) {
        if (buckets == null || bucket == null || delta == 0) return;
        int now = buckets.getOrDefault(bucket, 0);
        buckets.put(bucket, Math.max(0, now + delta));
    }

    private static int totalBucketCount(EnumMap<CombatBucket, Integer> buckets) {
        if (buckets == null) return 0;
        int total = 0;
        for (Integer v : buckets.values()) total += Math.max(0, (v == null) ? 0 : v);
        return total;
    }

    private static CombatBucket bucketForRole(ShipRole role) {
        if (role == null) return CombatBucket.LINE;
        return switch (role) {
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP, BATTLECRUISER -> CombatBucket.FLAGSHIP;
            case CARRIER, DRONE_CARRIER -> CombatBucket.CARRIER;
            case STEALTH_SHIP -> CombatBucket.STEALTH;
            case ARTILLERY_SHIP, MISSILE_BOAT, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> CombatBucket.ARTILLERY;
            case PATROL, PICKET, CIWS_CORVETTE, PD_CRAFT, FIGHTER, DRONE, BOMBER -> CombatBucket.ESCORT;
            default -> CombatBucket.LINE;
        };
    }

    private static double shipCombatScore(Ship ship) {
        if (ship == null) return 0.0;
        double role = roleCombatScore(ship.role);
        double hpFrac = ship.hp / (double) Math.max(1, ship.hpMax);
        double shieldFrac = (ship.shieldMax > 1e-9) ? (ship.shield / ship.shieldMax) : 1.0;
        return role + hpFrac * 12.0 + shieldFrac * 8.0;
    }

    private static double roleCombatScore(ShipRole role) {
        if (role == null) return 0.0;
        RoleStats.Stats st = RoleStats.get(role);
        double score = st.hpMax + st.shieldMax * 0.75 + st.desiredSpeed * 0.08 + st.bountyValue * 0.45;
        if (role == ShipRole.ARTILLERY_SHIP) score += 12.0;
        if (role == ShipRole.MISSILE_BOAT) score += 8.0;
        if (role == ShipRole.STEALTH_SHIP) score += 10.0;
        if (role == ShipRole.CARRIER || role == ShipRole.DRONE_CARRIER) score += 18.0;
        return Math.max(1.0, score);
    }

    private static int refitOreCost(ShipRole from, ShipRole to) {
        int fromTier = hullTierCost(from);
        int toTier = hullTierCost(to);
        if (toTier <= fromTier) return Integer.MAX_VALUE;
        int delta = toTier - fromTier;
        return Math.max(220, (int) Math.round(delta * 1.90 + 220.0));
    }

    private static int hullTierCost(ShipRole role) {
        if (role == null) return 0;
        return switch (role) {
            case PATROL -> 120;
            case PICKET -> 180;
            case FRIGATE -> 240;
            case ARTILLERY_SHIP -> 320;
            case CIWS_CORVETTE -> 250;
            case MISSILE_BOAT -> 300;
            case LIGHT_CRUISER -> 700;
            case MEDIUM_CRUISER -> 950;
            case CRUISER -> 1100;
            case BATTLECRUISER -> 1600;
            case BATTLESHIP -> 2200;
            case STEALTH_SHIP -> 1200;
            case DREADNOUGHT -> 3200;
            case CARRIER -> 2800;
            case DRONE_CARRIER -> 3000;
            case SUPERSHIP -> 5200;
            case PD_CRAFT -> 170;
            case FIGHTER -> 140;
            case BOMBER -> 190;
            case DRONE -> 100;
            default -> 260;
        };
    }

    private static CommanderPersonality commanderPersonality(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return CommanderPersonality.BALANCED;
        EnumMap<Faction, CommanderPersonality> map = teamPersonalities(ctx);
        CommanderPersonality cached = map.get(team);
        if (cached != null) return cached;

        long seed = ctx.config.seed;
        seed ^= ((long) team.ordinal() + 1L) * 0x9E3779B97F4A7C15L;
        seed ^= ((long) ctx.WORLD_W * 1315423911L);
        seed ^= ((long) ctx.WORLD_H * 2654435761L);
        java.util.Random r = new java.util.Random(seed);
        double roll = r.nextDouble();
        CommanderPersonality p;
        if (roll < 0.18) p = CommanderPersonality.FLAGSHIP_CORE;
        else if (roll < 0.34) p = CommanderPersonality.ESCORT_WING;
        else if (roll < 0.50) p = CommanderPersonality.ARTILLERY_LINE;
        else if (roll < 0.68) p = CommanderPersonality.STEALTH_RAIDERS;
        else if (roll < 0.84) p = CommanderPersonality.CARRIER_GROUP;
        else p = CommanderPersonality.BALANCED;
        map.put(team, p);
        return p;
    }

    private static EnumMap<Faction, CommanderPersonality> teamPersonalities(GameContext ctx) {
        return TEAM_PERSONALITIES.computeIfAbsent(ctx, k -> new EnumMap<>(Faction.class));
    }

    private static EnumMap<Faction, Double> teamRefitTimers(GameContext ctx) {
        return TEAM_REFIT_TIMERS.computeIfAbsent(ctx, k -> new EnumMap<>(Faction.class));
    }

    private static EnumMap<Faction, Double> teamBaseUpgradeTimers(GameContext ctx) {
        return TEAM_BASE_UPGRADE_TIMERS.computeIfAbsent(ctx, k -> new EnumMap<>(Faction.class));
    }

    private static Map<Integer, Double> shipRefitCooldowns(GameContext ctx) {
        return SHIP_REFIT_COOLDOWNS.computeIfAbsent(ctx, k -> new java.util.HashMap<>());
    }

    private static LinkedHashSet<Ship> collectBases(GameContext ctx) {
        LinkedHashSet<Ship> bases = new LinkedHashSet<>();
        if (ctx == null) return bases;
        if (ctx.allyBase != null) bases.add(ctx.allyBase);
        if (ctx.enemyBase != null) bases.add(ctx.enemyBase);
        if (ctx.teamBases != null) {
            for (Ship b : ctx.teamBases.values()) {
                if (b != null) bases.add(b);
            }
        }
        return bases;
    }

    private static Ship spawnStationTurretAtBase(GameContext ctx, Ship base) {
        if (ctx == null || base == null) return null;

        for (int attempt = 0; attempt < 12; attempt++) {
            double a = ctx.rng.nextDouble() * Math.PI * 2.0;
            double r = STATION_TURRET_RING_RADIUS + (ctx.rng.nextDouble() - 0.5) * STATION_TURRET_RING_JITTER;
            double sx = base.x + Math.cos(a) * r;
            double sy = base.y + Math.sin(a) * r;

            sx = GameMath.clamp(sx, 30, ctx.WORLD_W - 30);
            sy = GameMath.clamp(sy, 30, ctx.WORLD_H - 30);

            if (!isStationTurretSpawnClear(ctx, base, sx, sy)) continue;

            Ship turret = SpawnSystem.spawnTeamShip(ctx, ShipRole.STATIC_TURRET, base.faction, sx, sy);
            if (turret != null) {
                BaseUpgrades up = ctx.baseUpgrades.get(base);
                if (up != null && up.turretLv > 0) {
                    UISystem.applyTurretSystemsUpgrade(turret, up.turretLv);
                }
            }
            return turret;
        }
        return null;
    }

    private static boolean isStationTurretSpawnClear(GameContext ctx, Ship base, double x, double y) {
        if (ctx == null || base == null) return false;

        double minSpacing2 = STATION_TURRET_MIN_SPACING * STATION_TURRET_MIN_SPACING;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.STATIC_TURRET) continue;
            if (s.faction != base.faction) continue;

            if (GameMath.dist2(s.x, s.y, x, y) < minSpacing2) return false;
        }
        return true;
    }

    // ------------------------------
    // Miner AI state machine
    // ------------------------------

    private static final double MINER_SEARCH_RADIUS = 2400.0;
    private static final double MINER_DEPOSIT_RANGE = 120.0;
    private static final double MINER_FULL_FRAC = 0.90;

    private static void updateMinerState(GameContext ctx, Ship s, double dt) {
        // Ensure we have a home base
        if (s.minerHomeBase == null || !s.minerHomeBase.alive || s.minerHomeBase.hp <= 0) {
            s.minerHomeBase = findHomeBaseFor(ctx, s);
        }

        boolean hasBase = (s.minerHomeBase != null && s.minerHomeBase.alive && s.minerHomeBase.hp > 0);
        if (!hasBase && s.minerState != Ship.MinerState.IDLE) {
            s.minerState = Ship.MinerState.IDLE;
        }

        // Periodic debug logging (once per second)
        if (DevTools.isDebugOverlay()) {
            s.minerDebugTimer += dt;
            if (s.minerDebugTimer >= 1.0) {
                s.minerDebugTimer = 0.0;
                logMinerStatus(ctx, s);
            }
        }

        double cargo = getShipOre(s);
        double cargoMax = Math.max(1, s.cargoMax);
        boolean cargoFullEnough = cargo >= cargoMax * MINER_FULL_FRAC;

        if (cargoFullEnough
                && s.minerState != Ship.MinerState.RETURN_TO_BASE
                && s.minerState != Ship.MinerState.DEPOSIT
                && !hasNearbyHaulerWithCapacity(ctx, s, 260.0)) {
            s.minerState = Ship.MinerState.RETURN_TO_BASE;
        }

        switch (s.minerState) {
            case SEEK_ASTEROID -> {
                Asteroid a = findBestAsteroidForMiner(ctx, s, MINER_SEARCH_RADIUS);
                if (a == null) {
                    s.minerTarget = null;
                    s.minerDebugNote = findNoAsteroidReason(ctx);
                    aiWander(s, dt, ctx.WORLD_W, ctx.WORLD_H);
                    break;
                }
                s.minerTarget = a;
                s.minerDebugNote = "";
                s.minerState = Ship.MinerState.MOVE_TO_ASTEROID;
            }
            case MOVE_TO_ASTEROID -> {
                Asteroid a = s.minerTarget;
                if (a == null || getAsteroidOre(a) <= 0.01) {
                    s.minerTarget = null;
                    s.minerDebugNote = "target depleted";
                    s.minerState = Ship.MinerState.SEEK_ASTEROID;
                    break;
                }
                s.minerDebugNote = "";
                steerTo(s, a.x, a.y, dt);
                if (inMiningRange(s, a)) {
                    s.minerState = Ship.MinerState.MINING;
                }
            }
            case MINING -> {
                Asteroid a = s.minerTarget;
                if (a == null || getAsteroidOre(a) <= 0.01) {
                    s.minerTarget = null;
                    s.minerDebugNote = "target depleted";
                    s.minerState = Ship.MinerState.SEEK_ASTEROID;
                    break;
                }
                s.minerDebugNote = "";

                if (!inMiningRange(s, a)) {
                    s.minerState = Ship.MinerState.MOVE_TO_ASTEROID;
                    break;
                }

                double dtScaled = dt * ctx.miningMul * ctx.miningBaseMul;
                dtScaled *= CampaignSystem.miningRateMul(ctx);
                int mined = s.tryMine(a, dtScaled);
                if (mined > 0) {
                    try { VFX.spawnEngineWisp(s.x, s.y, s.vx, s.vy); } catch (Throwable ignored) {}
                }
                double newCargo = getShipOre(s);
                if (newCargo >= cargoMax * MINER_FULL_FRAC && !hasNearbyHaulerWithCapacity(ctx, s, 260.0)) {
                    s.minerState = Ship.MinerState.RETURN_TO_BASE;
                }
            }
            case RETURN_TO_BASE -> {
                if (!hasBase) {
                    s.minerState = Ship.MinerState.IDLE;
                    break;
                }
                Ship base = s.minerHomeBase;
                s.minerDebugNote = "";
                steerTo(s, base.x, base.y, dt);
                if (inDepositRange(s, base)) {
                    s.minerState = Ship.MinerState.DEPOSIT;
                }
            }
            case DEPOSIT -> {
                if (!hasBase) {
                    s.minerState = Ship.MinerState.IDLE;
                    break;
                }
                Ship base = s.minerHomeBase;
                s.minerDebugNote = "";
                if (inDepositRange(s, base)) {
                    int moved = s.depositCargoTo(base);
                    if (moved > 0 && TeamSystem.isFriendlyToPlayer(ctx, s.faction)) {
                        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul;
                        priceMul *= CampaignSystem.oreCreditMul(ctx);
                        int baseCredits = (int) Math.round(moved * GameContext.ORE_PRICE * priceMul);
                        ctx.credits += GameContext.scaleCreditEarnings(baseCredits);
                    }
                    // repair a bit (hp/hpMax are ints in this codebase)
                    int heal = (int) Math.round(18 * dt);
                    if (heal > 0) s.healHull(heal);
                }
                s.minerState = Ship.MinerState.SEEK_ASTEROID;
            }
            case IDLE -> {
                // If we regain a base, resume work.
                s.minerDebugNote = hasBase ? "" : "no base";
                if (hasBase) s.minerState = Ship.MinerState.SEEK_ASTEROID;
            }
        }
    }

    private static Ship findHomeBaseFor(GameContext ctx, Ship miner) {
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (s.faction != miner.faction) continue;
            if (!s.alive || s.hp <= 0) continue;
            double d2 = GameMath.dist2(miner.x, miner.y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    private static Asteroid findBestAsteroidForMiner(GameContext ctx, Ship miner, double maxDist) {
        Asteroid best = null;
        double bestD2 = maxDist * maxDist;
        if (ctx.asteroids == null || ctx.asteroids.isEmpty()) return null;
        for (Asteroid a : ctx.asteroids) {
            double ore = getAsteroidOre(a);
            if (ore <= 0.01) continue;
            double d2 = GameMath.dist2(miner.x, miner.y, a.x, a.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = a;
            }
        }
        return best;
    }

    private static String findNoAsteroidReason(GameContext ctx) {
        if (ctx.asteroids == null || ctx.asteroids.isEmpty()) return "no asteroids";
        for (Asteroid a : ctx.asteroids) {
            if (getAsteroidOre(a) > 0.01) return "";
        }
        return "all ore depleted";
    }

    private static boolean inMiningRange(Ship s, Asteroid a) {
        double dx = a.x - s.x;
        double dy = a.y - s.y;
        double reach = Math.max(0.0, s.miningRange) + s.radius + a.radius;
        return (dx * dx + dy * dy) <= (reach * reach);
    }

    private static boolean inDepositRange(Ship s, Ship base) {
        double dx = base.x - s.x;
        double dy = base.y - s.y;
        double reach = MINER_DEPOSIT_RANGE + s.radius + base.radius;
        return (dx * dx + dy * dy) <= (reach * reach);
    }

    private static boolean hasNearbyHaulerWithCapacity(GameContext ctx, Ship miner, double range) {
        if (ctx == null || miner == null || miner.faction == null || range <= 0.0) return false;
        double range2 = range * range;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.HAULER) continue;
            if (s.faction == null || s.faction.teamId() != miner.faction.teamId()) continue;
            if (s.cargo >= s.cargoMax) continue;
            if (GameMath.dist2(s.x, s.y, miner.x, miner.y) <= range2) return true;
        }
        return false;
    }

    private static void holdPosition(Ship s) {
        if (s == null) return;
        s.vx = 0.0;
        s.vy = 0.0;
    }

    private static void steerTo(Ship s, double tx, double ty, double dt) {
        double dx = tx - s.x;
        double dy = ty - s.y;
        double len = Math.sqrt(dx * dx + dy * dy) + 1e-9;
        double speed = Math.max(55.0, s.desiredSpeed);
        double vx = (dx / len) * speed;
        double vy = (dy / len) * speed;
        if (dt <= 0) {
            s.vx = 0;
            s.vy = 0;
            return;
        }
        s.vx = vx * dt;
        s.vy = vy * dt;
        s.angle = Math.atan2(vy, vx);
    }

    private static void logMinerStatus(GameContext ctx, Ship s) {
        StringBuilder sb = new StringBuilder();
        sb.append("MINER #").append(s.id).append(" ")
                .append(s.minerState).append(" cargo=").append(s.cargo).append("/").append(s.cargoMax);

        if (s.minerTarget != null) {
            double d = Math.hypot(s.minerTarget.x - s.x, s.minerTarget.y - s.y);
            sb.append(" distA=").append((int) Math.round(d));
        } else {
            sb.append(" distA=?");
        }

        if (s.minerHomeBase != null) {
            double d = Math.hypot(s.minerHomeBase.x - s.x, s.minerHomeBase.y - s.y);
            sb.append(" distB=").append((int) Math.round(d));
        } else {
            sb.append(" distB=?");
        }

        if (s.minerDebugNote != null && !s.minerDebugNote.isBlank()) {
            sb.append(" note=").append(s.minerDebugNote);
        }

        System.out.println(sb);
    }

    private static Ship getBaseForFaction(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null) return null;
        Ship direct = ctx.teamBases.get(faction);
        if (direct == null) {
            if (faction == Faction.ALLY) direct = ctx.allyBase;
            else if (faction == Faction.ENEMY) direct = ctx.enemyBase;
        }
        if (direct != null && direct.role == ShipRole.BASE && direct.faction == faction && direct.hp > 0) {
            return direct;
        }
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (s.faction != faction) continue;
            if (s.hp <= 0) continue;
            return s;
        }
        return null;
    }

    public static int getOreTotalForFaction(GameContext ctx, Faction f) {
        int total = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role == ShipRole.BASE && s.faction == f) total += s.oreStockpile;
        }
        return total;
    }

    private static void checkResourceRushWin(GameContext ctx) {
        int allyOre = getOreTotalForFaction(ctx, Faction.ALLY);
        int enemyOre = getOreTotalForFaction(ctx, Faction.ENEMY);
        boolean allyAlive = TeamSystem.isTeamAlive(ctx, Faction.ALLY);
        boolean enemyAlive = TeamSystem.isTeamAlive(ctx, Faction.ENEMY);

        if (!allyAlive || !enemyAlive) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;

            if (allyAlive == enemyAlive) {
                ctx.gameOverText = "DRAW";
                return;
            }

            Faction winner = allyAlive ? Faction.ALLY : Faction.ENEMY;
            Faction loser = allyAlive ? Faction.ENEMY : Faction.ALLY;
            boolean friendlyWin = (ctx.player != null && winner.isFriendlyTo(ctx.player.faction));
            String side = friendlyWin ? "VICTORY" : "DEFEAT";
            ctx.gameOverText = side + " - " + winner.teamName() + " ELIMINATED " + loser.teamName();
            return;
        }

        if (allyOre >= ctx.resourceGoal || enemyOre >= ctx.resourceGoal) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;

            if (allyOre == enemyOre) ctx.gameOverText = "DRAW";
            else if (allyOre > enemyOre) ctx.gameOverText = "VICTORY";
            else ctx.gameOverText = "DEFEAT";
        }
    }

    private static void checkFourTeamDominationWin(GameContext ctx) {
        Faction[] teams = Faction.fourTeamFactions();
        int alive = TeamSystem.countAliveTeams(ctx, teams);
        if (alive <= 1) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;

            Faction winner = TeamSystem.getLastAliveTeam(ctx, teams);
            if (winner == null) ctx.gameOverText = "DRAW";
            else ctx.gameOverText = winner.teamName() + " WINS";
        }
    }

    // ---- Compatibility helpers for varying codebases ----

    private static void aiGoTo(Ship s, double x, double y, double dt) {
        try { s.getClass().getMethod("aiGoTo", double.class, double.class, double.class).invoke(s, x, y, dt); return; } catch (Throwable ignored) {}
        // Fallback: direct velocity set using ship preferred speed.
        double dx = x - s.x, dy = y - s.y;
        double len = Math.sqrt(dx*dx + dy*dy) + 1e-9;
        double speed = Math.max(45.0, s.desiredSpeed);
        s.vx = (dx / len) * speed * dt;
        s.vy = (dy / len) * speed * dt;
    }

    private static void aiWander(Ship s, double dt, int w, int h) {
        try { s.getClass().getMethod("aiWander", double.class, int.class, int.class).invoke(s, dt, w, h); return; } catch (Throwable ignored) {}
        // Fallback wander: slow deterministic drift, with center pull to avoid edge camping.
        double t = System.nanoTime() * 1e-9 + (s.hashCode() * 0.001);
        double tx = s.x + Math.cos(t * 0.7) * 180.0;
        double ty = s.y + Math.sin(t * 0.9) * 180.0;
        tx = GameMath.clamp(tx, 80, w - 80);
        ty = GameMath.clamp(ty, 80, h - 80);
        aiGoTo(s, tx, ty, dt);
    }

    private static double getShipOre(Ship s) {
        try {
            Object v = s.getClass().getField("ore").get(s);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        try {
            Object v = s.getClass().getField("cargo").get(s);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private static double getAsteroidOre(Asteroid a) {
        try { return (double) a.getClass().getField("ore").get(a); } catch (Throwable ignored) {}
        try { return ((Number) a.getClass().getField("ore").get(a)).doubleValue(); } catch (Throwable ignored) {}
        return 0;
    }

}
