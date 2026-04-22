import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class OffSectorSimulationSystem {
    private static final double REMOTE_RESOLUTION_STEP_SECONDS = 1.25;
    private static final double STRATEGIC_REFRESH_WINDOW_SECONDS = 0.25;
    private static final int MAX_REMOTE_COMM_LOG = 8;
    private static final double COLLAPSE_SPACING_RADIUS = 165.0;
    private static final WeakHashMap<GameContext, State> STATES = new WeakHashMap<>();

    public enum ReinforcementProfile {
        BALANCED,
        DEFENSE,
        SPEARHEAD,
        FIRE_SUPPORT
    }

    public static final class ReinforcementDirective {
        public final String targetSectorId;
        public final ReinforcementProfile profile;
        public final int budgetDelta;

        ReinforcementDirective(String targetSectorId, ReinforcementProfile profile, int budgetDelta) {
            this.targetSectorId = (targetSectorId == null) ? "" : targetSectorId;
            this.profile = (profile == null) ? ReinforcementProfile.BALANCED : profile;
            this.budgetDelta = Math.max(-1, Math.min(2, budgetDelta));
        }
    }

    private static final class CollapsedForceSummary {
        final String sectorId;
        final Faction faction;
        final EnumMap<ShipRole, Integer> roleCounts = new EnumMap<>(ShipRole.class);
        int nominalShipCount = 0;
        int nominalPresence = 0;
        double totalCurrentStrength = 0.0;
        double totalMaxStrength = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;

        CollapsedForceSummary(String sectorId, Faction faction) {
            this.sectorId = (sectorId == null) ? "" : sectorId;
            this.faction = faction;
        }

        void absorb(Ship ship) {
            if (ship == null || ship.role == null) return;
            roleCounts.merge(ship.role, 1, Integer::sum);
            nominalShipCount++;
            nominalPresence += BattlefieldSectorSystem.presenceWeight(ship);
            totalCurrentStrength += collapsedStrengthCurrent(ship);
            totalMaxStrength += collapsedStrengthMax(ship);
            sumX += ship.x;
            sumY += ship.y;
        }

        boolean hasAnyStrength() {
            return nominalShipCount > 0 && totalCurrentStrength > 0.25 && totalMaxStrength > 0.25;
        }

        boolean applyAttrition(double damage) {
            if (!hasAnyStrength() || damage <= 0.0) return false;
            totalCurrentStrength = Math.max(0.0, totalCurrentStrength - damage);
            return true;
        }

        double integrityFraction() {
            if (!hasAnyStrength()) return 0.0;
            return MathUtil.clamp(totalCurrentStrength / Math.max(1e-6, totalMaxStrength), 0.0, 1.0);
        }

        int effectiveShipCount() {
            if (!hasAnyStrength()) return 0;
            return Math.max(1, Math.min(nominalShipCount,
                    (int) Math.ceil(nominalShipCount * integrityFraction() - 1e-9)));
        }

        int effectivePresence() {
            if (!hasAnyStrength()) return 0;
            return Math.max(1, Math.min(nominalPresence,
                    (int) Math.round(nominalPresence * integrityFraction())));
        }

        double averageX(GameContext ctx, BattlefieldSectorSystem.SectorDefinition sector) {
            if (nominalShipCount > 0) return clampToSectorX(ctx, sector, sumX / nominalShipCount);
            return (sector == null) ? 0.0 : sector.centerX(ctx);
        }

        double averageY(GameContext ctx, BattlefieldSectorSystem.SectorDefinition sector) {
            if (nominalShipCount > 0) return clampToSectorY(ctx, sector, sumY / nominalShipCount);
            return (sector == null) ? 0.0 : sector.centerY(ctx);
        }

        double restoredShipIntegrity() {
            int effective = effectiveShipCount();
            if (effective <= 0) return 0.0;
            double remainingShipEquivalents = nominalShipCount * integrityFraction();
            return MathUtil.clamp(remainingShipEquivalents / effective, 0.05, 1.0);
        }

        Map<ShipRole, Integer> restoredRoleCounts() {
            int effective = effectiveShipCount();
            if (effective <= 0 || nominalShipCount <= 0 || roleCounts.isEmpty()) {
                return Map.of();
            }

            LinkedHashMap<ShipRole, Integer> out = new LinkedHashMap<>();
            ArrayList<RoleAllocation> allocations = new ArrayList<>();
            int assigned = 0;

            for (Map.Entry<ShipRole, Integer> entry : roleCounts.entrySet()) {
                ShipRole role = entry.getKey();
                int count = Math.max(0, entry.getValue());
                if (role == null || count <= 0) continue;

                double ideal = count * (effective / (double) nominalShipCount);
                int base = Math.min(count, (int) Math.floor(ideal));
                out.put(role, base);
                assigned += base;
                allocations.add(new RoleAllocation(role, count, ideal - base));
            }

            allocations.sort((a, b) -> {
                int byRemainder = Double.compare(b.remainder, a.remainder);
                if (byRemainder != 0) return byRemainder;
                return Integer.compare(b.count, a.count);
            });

            int remaining = Math.max(0, effective - assigned);
            while (remaining > 0 && !allocations.isEmpty()) {
                boolean awarded = false;
                for (RoleAllocation item : allocations) {
                    int current = out.getOrDefault(item.role, 0);
                    if (current >= item.count) continue;
                    out.put(item.role, current + 1);
                    remaining--;
                    awarded = true;
                    if (remaining <= 0) break;
                }
                if (!awarded) break;
            }

            if (out.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
                ShipRole fallback = allocations.isEmpty() ? ShipRole.FRIGATE : allocations.get(0).role;
                out.put(fallback, 1);
            }
            return out;
        }
    }

    private static final class RoleAllocation {
        final ShipRole role;
        final int count;
        final double remainder;

        RoleAllocation(ShipRole role, int count, double remainder) {
            this.role = role;
            this.count = Math.max(0, count);
            this.remainder = remainder;
        }
    }

    private static final class RemotePressureTarget {
        final Ship ship;
        final CollapsedForceSummary summary;
        final Faction faction;

        RemotePressureTarget(Ship ship) {
            this.ship = ship;
            this.summary = null;
            this.faction = (ship == null) ? null : ship.faction;
        }

        RemotePressureTarget(Faction faction, CollapsedForceSummary summary) {
            this.ship = null;
            this.summary = summary;
            this.faction = faction;
        }

        boolean applyDamage(double damage) {
            if (ship != null) {
                if (!isLiveShip(ship)) return false;
                int applied = Math.max(1, (int) Math.round(damage));
                ship.takePenetratingInternalDamage(applied, ship.x, ship.y, 0.0, 0.0);
                return true;
            }
            return summary != null && summary.applyAttrition(damage);
        }
    }

    private static final class State {
        double timer = REMOTE_RESOLUTION_STEP_SECONDS;
        final Map<String, String> statusBySectorId = new HashMap<>();
        final EnumMap<Faction, ReinforcementDirective> directives = new EnumMap<>(Faction.class);
        final Set<String> abstractedSectorIds = new HashSet<>();
        final Map<String, EnumMap<Faction, CollapsedForceSummary>> collapsedBySectorId = new HashMap<>();
        double lastStrategicRefreshBucket = Double.NaN;
        String lastSelectedSectorId = "";
        String lastLoadedSectorId = "";
    }

    private OffSectorSimulationSystem() {}

    public static boolean update(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return false;
        if (!BattlefieldSectorSystem.isEnabled(ctx)) return false;

        State state = STATES.computeIfAbsent(ctx, key -> new State());
        refreshStrategicState(ctx, state);

        boolean stateChanged = reconcileCollapsedSectors(ctx, state);
        if (stateChanged) {
            BattlefieldSectorSystem.invalidateSnapshots(ctx);
            refreshStrategicState(ctx, state, true);
        }

        state.timer -= dt;
        while (state.timer <= 0.0) {
            state.timer += REMOTE_RESOLUTION_STEP_SECONDS;
            resolveRemoteSectors(ctx, state, REMOTE_RESOLUTION_STEP_SECONDS);
        }
        refreshStrategicState(ctx, state);
        return stateChanged;
    }

    public static ReinforcementDirective reinforcementDirective(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null) return new ReinforcementDirective("", ReinforcementProfile.BALANCED, 0);
        State state = STATES.computeIfAbsent(ctx, key -> new State());
        refreshStrategicState(ctx, state);
        ReinforcementDirective directive = state.directives.get(faction);
        return (directive == null) ? new ReinforcementDirective("", ReinforcementProfile.BALANCED, 0) : directive;
    }

    public static int reinforcementBudgetDelta(GameContext ctx, Faction faction) {
        return reinforcementDirective(ctx, faction).budgetDelta;
    }

    public static boolean isSectorAbstracted(GameContext ctx, BattlefieldSectorSystem.SectorDefinition sector) {
        if (ctx == null || sector == null) return false;
        State state = STATES.computeIfAbsent(ctx, key -> new State());
        refreshStrategicState(ctx, state);
        return state.abstractedSectorIds.contains(sector.id);
    }

    public static int collapsedPresence(GameContext ctx, String sectorId, int teamId) {
        if (ctx == null || sectorId == null || sectorId.isBlank()) return 0;
        if (teamId < 0) return 0;
        State state = STATES.get(ctx);
        if (state == null) return 0;

        int total = 0;
        Map<Faction, CollapsedForceSummary> summaries = state.collapsedBySectorId.get(sectorId);
        if (summaries == null || summaries.isEmpty()) return 0;
        for (Map.Entry<Faction, CollapsedForceSummary> entry : summaries.entrySet()) {
            Faction faction = entry.getKey();
            CollapsedForceSummary summary = entry.getValue();
            if (faction == null || summary == null) continue;
            if (faction.teamId() != teamId) continue;
            total += summary.effectivePresence();
        }
        return total;
    }

    public static int collapsedShipCount(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null) return 0;
        State state = STATES.get(ctx);
        if (state == null) return 0;

        int total = 0;
        for (Map<Faction, CollapsedForceSummary> summaries : state.collapsedBySectorId.values()) {
            if (summaries == null || summaries.isEmpty()) continue;
            for (Map.Entry<Faction, CollapsedForceSummary> entry : summaries.entrySet()) {
                Faction key = entry.getKey();
                CollapsedForceSummary summary = entry.getValue();
                if (key == null || summary == null) continue;
                if (key.teamId() != faction.teamId()) continue;
                total += summary.effectiveShipCount();
            }
        }
        return total;
    }

    public static double collapsedIntegrityFraction(GameContext ctx, String sectorId, Faction faction) {
        if (ctx == null || sectorId == null || sectorId.isBlank() || faction == null) return 0.0;
        State state = STATES.get(ctx);
        if (state == null) return 0.0;
        Map<Faction, CollapsedForceSummary> summaries = state.collapsedBySectorId.get(sectorId);
        if (summaries == null || summaries.isEmpty()) return 0.0;
        CollapsedForceSummary summary = summaries.get(faction);
        return (summary == null) ? 0.0 : summary.integrityFraction();
    }

    public static boolean shouldUseAbstractShipBehavior(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null) return false;
        if (!isLiveShip(ship)) return false;
        BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.sectorAt(ctx, ship.x, ship.y);
        if (!isSectorAbstracted(ctx, sector)) return false;
        return ship.role != ShipRole.BASE
                && ship.role != ShipRole.STATIC_TURRET
                && ship.role != ShipRole.MINER
                && ship.role != ShipRole.HAULER
                && ship.role != ShipRole.TRANSPORT;
    }

    private static boolean reconcileCollapsedSectors(GameContext ctx, State state) {
        if (ctx == null || state == null) return false;
        boolean changed = false;

        ArrayList<String> sectorsToRestore = new ArrayList<>();
        for (String sectorId : state.collapsedBySectorId.keySet()) {
            if (!state.abstractedSectorIds.contains(sectorId)) {
                sectorsToRestore.add(sectorId);
            }
        }
        for (String sectorId : sectorsToRestore) {
            changed |= restoreCollapsedSector(ctx, state, sectorId);
        }

        Map<String, List<Ship>> shipsToCollapseBySector = new HashMap<>();
        for (Ship ship : ctx.ships) {
            if (!isCollapsibleShip(ctx, ship)) continue;
            BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.sectorAt(ctx, ship.x, ship.y);
            if (sector == null || !state.abstractedSectorIds.contains(sector.id)) continue;
            shipsToCollapseBySector.computeIfAbsent(sector.id, ignored -> new ArrayList<>()).add(ship);
        }
        for (Map.Entry<String, List<Ship>> entry : shipsToCollapseBySector.entrySet()) {
            changed |= collapseShipsIntoSummary(ctx, state, entry.getKey(), entry.getValue());
        }

        ArrayList<String> emptySectorIds = new ArrayList<>();
        for (Map.Entry<String, EnumMap<Faction, CollapsedForceSummary>> entry : state.collapsedBySectorId.entrySet()) {
            EnumMap<Faction, CollapsedForceSummary> summaries = entry.getValue();
            if (summaries == null) {
                emptySectorIds.add(entry.getKey());
                continue;
            }
            summaries.entrySet().removeIf(item -> item == null || item.getValue() == null || !item.getValue().hasAnyStrength());
            if (summaries.isEmpty()) emptySectorIds.add(entry.getKey());
        }
        for (String sectorId : emptySectorIds) {
            state.collapsedBySectorId.remove(sectorId);
            changed = true;
        }
        return changed;
    }

    private static boolean collapseShipsIntoSummary(GameContext ctx,
                                                    State state,
                                                    String sectorId,
                                                    List<Ship> ships) {
        if (ctx == null || state == null || sectorId == null || ships == null || ships.isEmpty()) return false;
        EnumMap<Faction, CollapsedForceSummary> summaries =
                state.collapsedBySectorId.computeIfAbsent(sectorId, ignored -> new EnumMap<>(Faction.class));
        HashSet<Ship> removed = new HashSet<>();

        for (Ship ship : ships) {
            if (!isCollapsibleShip(ctx, ship)) continue;
            Faction faction = ship.faction;
            if (faction == null) continue;
            CollapsedForceSummary summary = summaries.computeIfAbsent(faction,
                    ignored -> new CollapsedForceSummary(sectorId, faction));
            summary.absorb(ship);
            removed.add(ship);
        }
        if (removed.isEmpty()) return false;

        if (ctx.lockedTarget != null && removed.contains(ctx.lockedTarget)) {
            ctx.lockedTarget = null;
        }
        ctx.ships.removeIf(removed::contains);
        return true;
    }

    private static boolean restoreCollapsedSector(GameContext ctx, State state, String sectorId) {
        if (ctx == null || state == null || sectorId == null || sectorId.isBlank()) return false;
        EnumMap<Faction, CollapsedForceSummary> summaries = state.collapsedBySectorId.remove(sectorId);
        if (summaries == null || summaries.isEmpty()) return false;

        BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.findSector(ctx, sectorId);
        int ordinal = 0;
        for (CollapsedForceSummary summary : summaries.values()) {
            ordinal = restoreForceSummary(ctx, sector, summary, ordinal);
        }
        return true;
    }

    private static int restoreForceSummary(GameContext ctx,
                                           BattlefieldSectorSystem.SectorDefinition sector,
                                           CollapsedForceSummary summary,
                                           int ordinalStart) {
        if (ctx == null || summary == null || !summary.hasAnyStrength()) return ordinalStart;

        Map<ShipRole, Integer> roleCounts = summary.restoredRoleCounts();
        double integrity = summary.restoredShipIntegrity();
        double centerX = summary.averageX(ctx, sector);
        double centerY = summary.averageY(ctx, sector);
        int ordinal = ordinalStart;

        for (Map.Entry<ShipRole, Integer> entry : roleCounts.entrySet()) {
            ShipRole role = entry.getKey();
            int count = Math.max(0, entry.getValue());
            if (role == null || count <= 0) continue;
            for (int i = 0; i < count; i++) {
                Ship restored = spawnCollapsedRepresentative(ctx, sector, summary.faction, role, centerX, centerY, integrity, ordinal);
                if (restored != null) {
                    ordinal++;
                }
            }
        }
        return ordinal;
    }

    private static Ship spawnCollapsedRepresentative(GameContext ctx,
                                                     BattlefieldSectorSystem.SectorDefinition sector,
                                                     Faction faction,
                                                     ShipRole role,
                                                     double centerX,
                                                     double centerY,
                                                     double integrity,
                                                     int ordinal) {
        if (ctx == null || role == null || faction == null) return null;

        double[] point = restorePoint(ctx, sector, centerX, centerY, ordinal);
        Ship ship = new FleetShip(role, faction, point[0], point[1]);
        double clampedIntegrity = MathUtil.clamp(integrity, 0.05, 1.0);
        ship.scaleCurrentHullIntegrity(clampedIntegrity);
        if (ship.shieldMax > 0.0) {
            ship.shield = Math.max(0.0, Math.min(ship.shieldMax, ship.shieldMax * clampedIntegrity));
        }
        ship.vx = 0.0;
        ship.vy = 0.0;
        ctx.ships.add(ship);
        try {
            DoctrineRegistry.applyToShip(ship);
        } catch (Throwable ignored) {
            // Optional doctrine layer.
        }
        return ship;
    }

    private static double[] restorePoint(GameContext ctx,
                                         BattlefieldSectorSystem.SectorDefinition sector,
                                         double centerX,
                                         double centerY,
                                         int ordinal) {
        if (ctx == null) return new double[]{0.0, 0.0};
        double baseX = (sector == null) ? centerX : clampToSectorX(ctx, sector, centerX);
        double baseY = (sector == null) ? centerY : clampToSectorY(ctx, sector, centerY);
        double angle = ordinal * 0.93;
        double radius = 40.0 + (ordinal % 4) * 28.0;
        double x = baseX + Math.cos(angle) * Math.min(COLLAPSE_SPACING_RADIUS, radius);
        double y = baseY + Math.sin(angle) * Math.min(COLLAPSE_SPACING_RADIUS, radius);
        if (sector != null) {
            x = clampToSectorX(ctx, sector, x);
            y = clampToSectorY(ctx, sector, y);
        } else {
            x = GameMath.clamp(x, 30.0, ctx.WORLD_W - 30.0);
            y = GameMath.clamp(y, 30.0, ctx.WORLD_H - 30.0);
        }
        return new double[]{x, y};
    }

    private static double clampToSectorX(GameContext ctx, BattlefieldSectorSystem.SectorDefinition sector, double x) {
        if (ctx == null) return 0.0;
        if (sector == null) return GameMath.clamp(x, 30.0, ctx.WORLD_W - 30.0);
        double minX = sector.minWorldX(ctx) + 30.0;
        double maxX = sector.maxWorldX(ctx) - 30.0;
        if (maxX < minX) {
            double mid = sector.centerX(ctx);
            minX = mid - 10.0;
            maxX = mid + 10.0;
        }
        return GameMath.clamp(x, minX, maxX);
    }

    private static double clampToSectorY(GameContext ctx, BattlefieldSectorSystem.SectorDefinition sector, double y) {
        if (ctx == null) return 0.0;
        if (sector == null) return GameMath.clamp(y, 30.0, ctx.WORLD_H - 30.0);
        double minY = sector.minWorldY(ctx) + 30.0;
        double maxY = sector.maxWorldY(ctx) - 30.0;
        if (maxY < minY) {
            double mid = sector.centerY(ctx);
            minY = mid - 10.0;
            maxY = mid + 10.0;
        }
        return GameMath.clamp(y, minY, maxY);
    }

    private static boolean isCollapsibleShip(GameContext ctx, Ship ship) {
        if (!isLiveShip(ship)) return false;
        if (ship == ctx.player) return false;
        if (ship.isWarpCharging()) return false;
        return switch (ship.role) {
            case BASE, STATIC_TURRET, MINER, HAULER, TRANSPORT -> false;
            default -> true;
        };
    }

    private static void resolveRemoteSectors(GameContext ctx, State state, double stepSeconds) {
        if (ctx == null || state == null || stepSeconds <= 0.0) return;
        BattlefieldSectorSystem.ensureLoadedSector(ctx);
        BattlefieldSectorSystem.SectorDefinition loadedSector = BattlefieldSectorSystem.loadedSector(ctx);
        Map<String, List<Ship>> shipsBySectorId = collectShipsBySector(ctx);
        boolean damagedAnySector = false;

        for (BattlefieldSectorSystem.SectorSnapshot snapshot : BattlefieldSectorSystem.snapshots(ctx)) {
            if (snapshot == null || snapshot.sector == null) continue;
            if (loadedSector != null && snapshot.sector.id.equalsIgnoreCase(loadedSector.id)) {
                rememberSectorStatus(state, snapshot);
                continue;
            }

            maybeReportSectorStatusChange(ctx, state, snapshot);
            List<Ship> ships = shipsBySectorId.getOrDefault(snapshot.sector.id, List.of());
            boolean damaged = resolveRemoteCombatPressure(ctx, state, snapshot, ships, stepSeconds);
            damaged |= resolveRemoteBaseSiege(ctx, snapshot, stepSeconds);
            damagedAnySector |= damaged;
        }

        if (damagedAnySector) {
            BattlefieldSectorSystem.invalidateSnapshots(ctx);
            refreshStrategicState(ctx, state, true);
        }
    }

    private static Map<String, List<Ship>> collectShipsBySector(GameContext ctx) {
        Map<String, List<Ship>> out = new HashMap<>();
        if (ctx == null || ctx.ships == null) return out;
        for (Ship ship : ctx.ships) {
            if (!isLiveShip(ship)) continue;
            BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.sectorAt(ctx, ship.x, ship.y);
            if (sector == null) continue;
            out.computeIfAbsent(sector.id, ignored -> new ArrayList<>()).add(ship);
        }
        return out;
    }

    private static boolean resolveRemoteCombatPressure(GameContext ctx,
                                                       State state,
                                                       BattlefieldSectorSystem.SectorSnapshot snapshot,
                                                       List<Ship> ships,
                                                       double stepSeconds) {
        if (ctx == null || snapshot == null || snapshot.dominantFaction == null) return false;
        if (snapshot.occupiedTeams < 2) return false;
        List<RemotePressureTarget> targets = selectRemotePressureTargets(state, snapshot, ships);
        if (targets.isEmpty()) return false;

        double pressureGap = Math.max(1.0, snapshot.dominantPresence - snapshot.secondaryPresence);
        double contestedMul = (snapshot.controlState == BattlefieldSectorSystem.ControlState.CONTESTED) ? 0.82 : 1.08;
        double damageBudget = (7.0 + pressureGap * 1.55 + Math.max(0, snapshot.occupiedTeams - 2) * 2.5)
                * contestedMul
                * (stepSeconds / REMOTE_RESOLUTION_STEP_SECONDS);
        double perTarget = Math.max(10.0, damageBudget / Math.max(1, targets.size()));

        boolean damaged = false;
        for (RemotePressureTarget target : targets) {
            if (target == null) continue;
            damaged |= target.applyDamage(perTarget);
        }
        if (damaged && state != null && snapshot.sector != null) {
            pruneCollapsedSector(state, snapshot.sector.id);
        }
        return damaged;
    }

    private static List<RemotePressureTarget> selectRemotePressureTargets(State state,
                                                                          BattlefieldSectorSystem.SectorSnapshot snapshot,
                                                                          List<Ship> ships) {
        if (snapshot == null || snapshot.sector == null || snapshot.dominantFaction == null) return List.of();
        Map<Integer, RemotePressureTarget> bestByTeam = new HashMap<>();
        Map<Integer, Double> scoreByTeam = new HashMap<>();
        if (ships != null) {
            for (Ship ship : ships) {
                if (!isEligiblePressureTarget(ship, false)) continue;
                if (ship.faction == null || snapshot.dominantFaction.isFriendlyTo(ship.faction)) continue;
                int teamId = ship.faction.teamId();
                double score = remoteAttritionPriority(ship);
                Double current = scoreByTeam.get(teamId);
                if (current == null || score > current) {
                    scoreByTeam.put(teamId, score);
                    bestByTeam.put(teamId, new RemotePressureTarget(ship));
                }
            }
        }
        if (state != null) {
            Map<Faction, CollapsedForceSummary> summaries = state.collapsedBySectorId.get(snapshot.sector.id);
            if (summaries != null && !summaries.isEmpty()) {
                for (Map.Entry<Faction, CollapsedForceSummary> entry : summaries.entrySet()) {
                    Faction faction = entry.getKey();
                    CollapsedForceSummary summary = entry.getValue();
                    if (faction == null || summary == null || !summary.hasAnyStrength()) continue;
                    if (snapshot.dominantFaction.isFriendlyTo(faction)) continue;
                    int teamId = faction.teamId();
                    double score = remoteCollapsedAttritionPriority(summary);
                    Double current = scoreByTeam.get(teamId);
                    if (current == null || score > current) {
                        scoreByTeam.put(teamId, score);
                        bestByTeam.put(teamId, new RemotePressureTarget(faction, summary));
                    }
                }
            }
        }
        return new ArrayList<>(bestByTeam.values());
    }

    private static boolean resolveRemoteBaseSiege(GameContext ctx,
                                                  BattlefieldSectorSystem.SectorSnapshot snapshot,
                                                  double stepSeconds) {
        if (ctx == null || snapshot == null || snapshot.sector == null) return false;
        if (snapshot.controlState != BattlefieldSectorSystem.ControlState.CONTROLLED) return false;
        if (snapshot.sector.anchorFaction == null || snapshot.dominantFaction == null) return false;
        if (snapshot.dominantFaction.isFriendlyTo(snapshot.sector.anchorFaction)) return false;

        Ship base = TeamSystem.getBaseForTeam(ctx, snapshot.sector.anchorFaction);
        if (!isEligiblePressureTarget(base, true)) return false;
        BattlefieldSectorSystem.SectorDefinition baseSector = BattlefieldSectorSystem.sectorAt(ctx, base.x, base.y);
        if (baseSector == null || !baseSector.id.equalsIgnoreCase(snapshot.sector.id)) return false;

        int hostilePresence = snapshot.presenceForTeamId(snapshot.dominantFaction.teamId());
        int defendingPresence = snapshot.presenceForTeamId(snapshot.sector.anchorFaction.teamId());
        if (hostilePresence <= defendingPresence) return false;

        double damage = (4.0 + (hostilePresence - defendingPresence) * 0.85)
                * (stepSeconds / REMOTE_RESOLUTION_STEP_SECONDS);
        base.takeDamage(Math.max(1, (int) Math.round(damage)), base.x, base.y);
        return true;
    }

    private static double remoteAttritionPriority(Ship ship) {
        if (ship == null || ship.role == null) return Double.NEGATIVE_INFINITY;
        double hullFrac = (ship.hpMax <= 0) ? 1.0 : MathUtil.clamp(ship.hp / (double) ship.hpMax, 0.0, 1.0);
        double score = ship.hpMax * 0.06 + ship.radius * 1.4;
        score += (1.0 - hullFrac) * 80.0;
        score += switch (ship.role) {
            case SUPERSHIP, DREADNOUGHT, BATTLESHIP, BATTLECRUISER, CARRIER, DRONE_CARRIER -> 160.0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, ARTILLERY_SHIP, MISSILE_BOAT, FRIGATE -> 110.0;
            case PICKET, PATROL, CIWS_CORVETTE, STEALTH_SHIP -> 70.0;
            case STATIC_TURRET -> 46.0;
            default -> 24.0;
        };
        return score;
    }

    private static boolean isEligiblePressureTarget(Ship ship, boolean includeBase) {
        if (!isLiveShip(ship)) return false;
        if (ship.role == ShipRole.BASE) return includeBase;
        if (ship.role == ShipRole.MINER || ship.role == ShipRole.HAULER || ship.role == ShipRole.TRANSPORT) return false;
        if (ship.isSmallCraft()) return false;
        return true;
    }

    private static boolean isLiveShip(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }

    private static void pruneCollapsedSector(State state, String sectorId) {
        if (state == null || sectorId == null || sectorId.isBlank()) return;
        Map<Faction, CollapsedForceSummary> summaries = state.collapsedBySectorId.get(sectorId);
        if (summaries == null || summaries.isEmpty()) {
            state.collapsedBySectorId.remove(sectorId);
            return;
        }
        summaries.entrySet().removeIf(entry -> entry == null || entry.getValue() == null || !entry.getValue().hasAnyStrength());
        if (summaries.isEmpty()) {
            state.collapsedBySectorId.remove(sectorId);
        }
    }

    private static void maybeReportSectorStatusChange(GameContext ctx,
                                                      State state,
                                                      BattlefieldSectorSystem.SectorSnapshot snapshot) {
        if (ctx == null || state == null || snapshot == null || snapshot.sector == null) return;
        String previous = state.statusBySectorId.get(snapshot.sector.id);
        String current = sectorStatusSignature(snapshot);
        state.statusBySectorId.put(snapshot.sector.id, current);
        if (previous == null || previous.equals(current)) return;

        String message = switch (snapshot.controlState) {
            case EMPTY -> snapshot.sector.label + " has gone quiet";
            case CONTESTED -> snapshot.sector.label + " is contested";
            case CONTROLLED -> snapshot.sector.label + " now under "
                    + ((snapshot.dominantFaction == null) ? "unknown" : snapshot.dominantFaction.teamName())
                    + " control";
        };
        postSectorComm(ctx, message);
    }

    private static void rememberSectorStatus(State state, BattlefieldSectorSystem.SectorSnapshot snapshot) {
        if (state == null || snapshot == null || snapshot.sector == null) return;
        state.statusBySectorId.put(snapshot.sector.id, sectorStatusSignature(snapshot));
    }

    private static String sectorStatusSignature(BattlefieldSectorSystem.SectorSnapshot snapshot) {
        if (snapshot == null || snapshot.sector == null) return "";
        int teamId = (snapshot.dominantFaction == null) ? -1 : snapshot.dominantFaction.teamId();
        return snapshot.controlState.name() + ":" + teamId + ":" + snapshot.occupiedTeams;
    }

    private static void postSectorComm(GameContext ctx, String text) {
        if (ctx == null || text == null || text.isBlank()) return;
        if (ctx.fleetCommLog.size() >= MAX_REMOTE_COMM_LOG) {
            ctx.fleetCommLog.remove(0);
        }
        ctx.fleetCommLog.add(new GameContext.FleetCommMessage(Faction.ALLY, "SECTOR", text, 7.0));
    }

    private static void refreshStrategicState(GameContext ctx, State state) {
        refreshStrategicState(ctx, state, false);
    }

    private static void refreshStrategicState(GameContext ctx, State state, boolean force) {
        if (ctx == null || state == null || !BattlefieldSectorSystem.isEnabled(ctx)) return;
        BattlefieldSectorSystem.ensureLoadedSector(ctx);
        BattlefieldSectorSystem.SectorDefinition loadedSector = BattlefieldSectorSystem.loadedSector(ctx);
        BattlefieldSectorSystem.SectorDefinition selectedSector = BattlefieldSectorSystem.selectedSector(ctx);
        String loadedSectorId = (loadedSector == null) ? "" : loadedSector.id;
        String selectedSectorId = (selectedSector == null) ? "" : selectedSector.id;
        double bucket = Math.floor(Math.max(0.0, ctx.battleElapsed) / STRATEGIC_REFRESH_WINDOW_SECONDS)
                * STRATEGIC_REFRESH_WINDOW_SECONDS;
        if (!force
                && Math.abs(state.lastStrategicRefreshBucket - bucket) <= 1e-9
                && loadedSectorId.equals(state.lastLoadedSectorId)
                && selectedSectorId.equals(state.lastSelectedSectorId)) {
            return;
        }
        state.lastStrategicRefreshBucket = bucket;
        state.lastLoadedSectorId = loadedSectorId;
        state.lastSelectedSectorId = selectedSectorId;

        List<BattlefieldSectorSystem.SectorSnapshot> snapshots = BattlefieldSectorSystem.snapshots(ctx);
        state.directives.clear();
        state.abstractedSectorIds.clear();

        Map<Integer, BattlefieldSectorSystem.SectorDefinition> objectiveByTeam = new HashMap<>();
        for (Faction faction : Faction.fourTeamFactions()) {
            if (!TeamSystem.isTeamAlive(ctx, faction)) continue;
            BattlefieldSectorSystem.SectorDefinition objective =
                    BattlefieldSectorSystem.objectiveSector(ctx, faction,
                            sectorCenterX(ctx, faction), sectorCenterY(ctx, faction));
            if (objective != null) {
                objectiveByTeam.put(faction.teamId(), objective);
            }
            state.directives.put(faction, buildDirective(ctx, faction, snapshots, objective));
        }

        for (BattlefieldSectorSystem.SectorSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.sector == null) continue;
            if (shouldAbstractSector(ctx, snapshot, loadedSector, selectedSector, objectiveByTeam)) {
                state.abstractedSectorIds.add(snapshot.sector.id);
            }
        }
    }

    private static ReinforcementDirective buildDirective(GameContext ctx,
                                                         Faction faction,
                                                         List<BattlefieldSectorSystem.SectorSnapshot> snapshots,
                                                         BattlefieldSectorSystem.SectorDefinition objective) {
        BattlefieldSectorSystem.SectorDefinition home = BattlefieldSectorSystem.homeSector(ctx, faction);
        BattlefieldSectorSystem.SectorSnapshot homeSnapshot = (home == null) ? null : BattlefieldSectorSystem.snapshotForSector(ctx, home.id);
        BattlefieldSectorSystem.SectorSnapshot objectiveSnapshot =
                (objective == null) ? null : BattlefieldSectorSystem.snapshotForSector(ctx, objective.id);

        int contested = 0;
        int threatened = 0;
        int favorable = 0;
        for (BattlefieldSectorSystem.SectorSnapshot snapshot : snapshots) {
            if (snapshot == null) continue;
            if (snapshot.controlState == BattlefieldSectorSystem.ControlState.CONTESTED) contested++;
            if (BattlefieldSectorSystem.isThreatenedForFaction(snapshot, faction)) threatened++;
            if (BattlefieldSectorSystem.isFriendlyControl(snapshot, faction)) favorable++;
        }

        ReinforcementProfile profile = ReinforcementProfile.BALANCED;
        int budget = 0;
        String targetId = (objective == null) ? "" : objective.id;
        if (BattlefieldSectorSystem.isThreatenedForFaction(homeSnapshot, faction)) {
            profile = ReinforcementProfile.DEFENSE;
            budget += 2;
            targetId = (home == null) ? targetId : home.id;
        } else if (objectiveSnapshot != null && !BattlefieldSectorSystem.isFriendlyControl(objectiveSnapshot, faction)) {
            profile = (objective != null && objective.anchorFaction != null && !objective.anchorFaction.isFriendlyTo(faction))
                    ? ReinforcementProfile.SPEARHEAD
                    : ReinforcementProfile.FIRE_SUPPORT;
            budget += 1;
        }
        if (threatened >= 2) budget += 1;
        if (contested >= 2 && profile == ReinforcementProfile.BALANCED) {
            profile = ReinforcementProfile.FIRE_SUPPORT;
        }
        if (favorable >= Math.max(2, snapshots.size() - 1) && threatened == 0 && contested == 0) {
            budget -= 1;
        }
        return new ReinforcementDirective(targetId, profile, budget);
    }

    private static boolean shouldAbstractSector(GameContext ctx,
                                                BattlefieldSectorSystem.SectorSnapshot snapshot,
                                                BattlefieldSectorSystem.SectorDefinition loadedSector,
                                                BattlefieldSectorSystem.SectorDefinition selectedSector,
                                                Map<Integer, BattlefieldSectorSystem.SectorDefinition> objectiveByTeam) {
        if (ctx == null || snapshot == null || snapshot.sector == null) return false;
        if (ctx.config != null && ctx.config.mode == app.config.GameMode.RESOURCE_RUSH) return false;
        BattlefieldSectorSystem.SectorDefinition sector = snapshot.sector;
        if (singleLoadedSectorMode(ctx)) {
            return loadedSector != null && !sector.id.equalsIgnoreCase(loadedSector.id);
        }
        if (loadedSector != null && sector.id.equalsIgnoreCase(loadedSector.id)) return false;
        if (selectedSector != null && sector.id.equalsIgnoreCase(selectedSector.id)) return false;
        if (snapshot.controlState == BattlefieldSectorSystem.ControlState.CONTESTED) return false;
        if (snapshot.occupiedTeams > 1) return false;
        if (snapshot.sector.anchorFaction != null && TeamSystem.isTeamAlive(ctx, snapshot.sector.anchorFaction)
                && BattlefieldSectorSystem.isThreatenedForFaction(snapshot, snapshot.sector.anchorFaction)) {
            return false;
        }
        for (BattlefieldSectorSystem.SectorDefinition objective : objectiveByTeam.values()) {
            if (objective != null && sector.id.equalsIgnoreCase(objective.id)) {
                return false;
            }
        }
        if (loadedSector == null) return false;
        return BattlefieldSectorSystem.hopDistance(ctx, loadedSector, sector) >= 1;
    }

    private static boolean singleLoadedSectorMode(GameContext ctx) {
        return BattlefieldSectorSystem.isEnabled(ctx);
    }

    private static double sectorCenterX(GameContext ctx, Faction faction) {
        BattlefieldSectorSystem.SectorDefinition home = BattlefieldSectorSystem.homeSector(ctx, faction);
        return (home == null) ? ctx.WORLD_W * 0.5 : home.centerX(ctx);
    }

    private static double sectorCenterY(GameContext ctx, Faction faction) {
        BattlefieldSectorSystem.SectorDefinition home = BattlefieldSectorSystem.homeSector(ctx, faction);
        return (home == null) ? ctx.WORLD_H * 0.5 : home.centerY(ctx);
    }

    private static double collapsedStrengthCurrent(Ship ship) {
        if (ship == null) return 0.0;
        double roleWeight = BattlefieldSectorSystem.presenceWeight(ship) * 28.0;
        return Math.max(8.0, ship.hp + ship.shield * 0.35 + roleWeight + ship.radius * 0.75);
    }

    private static double collapsedStrengthMax(Ship ship) {
        if (ship == null) return 0.0;
        double roleWeight = BattlefieldSectorSystem.presenceWeight(ship) * 28.0;
        return Math.max(8.0, ship.hpMax + ship.shieldMax * 0.35 + roleWeight + ship.radius * 0.75);
    }

    private static double remoteCollapsedAttritionPriority(CollapsedForceSummary summary) {
        if (summary == null || !summary.hasAnyStrength()) return Double.NEGATIVE_INFINITY;
        double integrityPenalty = (1.0 - summary.integrityFraction()) * 80.0;
        return summary.totalMaxStrength * 0.05
                + summary.nominalPresence * 9.0
                + summary.nominalShipCount * 6.0
                + integrityPenalty;
    }
}
