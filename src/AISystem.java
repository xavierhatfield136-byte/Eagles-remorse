import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AISystem {
    private AISystem(){}

    private static final class FleetState {
        final Map<Integer, Ship> flagships = new HashMap<>();
        final Map<Integer, List<Ship>> members = new HashMap<>();
        final Map<Integer, Faction> teamFactions = new HashMap<>();
        final Map<Integer, Ship> sharedTargets = new HashMap<>();
        final Map<Integer, Double> sharedTargetConfidence = new HashMap<>();
        final Map<Integer, Ship> missileThreatFocus = new HashMap<>();
        final Map<Integer, DangerMemory> dangerMemory = new HashMap<>();
        final Map<Integer, SquadObjective> squadObjectives = new HashMap<>();
        final Map<Integer, GameContext.FleetFormation> autoFormation = new HashMap<>();
        final Map<Integer, Double> autoFormationSpacing = new HashMap<>();
    }

    private static final class SharedTargetChoice {
        final Ship target;
        final double confidence;

        SharedTargetChoice(Ship target, double confidence) {
            this.target = target;
            this.confidence = confidence;
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
    private static final double REPAIR_ORDER_SAFE_SECONDS = 20.0;

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;
        if (ctx.config != null
                && (ctx.config.mode == GameMode.SHOWCASE || ctx.config.mode == GameMode.SHOOTING_RANGE)) return;
        if (!DevTools.isAIEnabled()) return;
        decayKillConfirmTimers(Math.max(0.0, dt));
        pruneClosestRetargetState(ctx.ships);

        // Generic wave spawner (disabled for Last Stand and 4-team, which have custom pacing).
        if (ctx.config.mode != GameMode.LAST_STAND
                && ctx.config.mode != GameMode.FOUR_TEAM_DOMINATION
                && !CampaignSystem.useAuthoredWaveSchedule(ctx)) {
            ctx.enemyWaveTimer -= dt;
            if (ctx.enemyWaveTimer <= 0) {
                ctx.enemyWaveTimer = CampaignSystem.nextWaveDelay(ctx);
                int groups = CampaignSystem.groupsPerWave(ctx);
                int enemyGroups = groups;
                int allyGroups = Math.max(1, groups - 1);

                if (ctx.config.mode == GameMode.RESOURCE_RUSH) {
                    // Resource Rush should not snowball into red-only pressure.
                    allyGroups = groups;
                    int enemyAlive = TeamSystem.countAliveShips(ctx, Faction.ENEMY);
                    int allyAlive = TeamSystem.countAliveShips(ctx, Faction.ALLY);
                    int deficit = enemyAlive - allyAlive;
                    if (deficit >= 3) {
                        allyGroups += Math.min(2, deficit / 3);
                    }
                }
                for (int i = 0; i < enemyGroups; i++) {
                    SpawnSystem.spawnEnemyGroup(
                            ctx,
                            ctx.player.x + 900 + ctx.rng.nextDouble() * 500,
                            ctx.player.y - 600 + ctx.rng.nextDouble() * 400
                    );
                }

                for (int i = 0; i < allyGroups; i++) {
                    SpawnSystem.spawnAllyGroup(
                            ctx,
                            ctx.player.x - 900 - ctx.rng.nextDouble() * 500,
                            ctx.player.y + 600 + ctx.rng.nextDouble() * 400
                    );
                }
            }
        }

        FleetState fleetState = buildFleetState(ctx, dt);

        // per ship AI
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying) continue;
            if (s == ctx.player) continue;
            tickClosestWeaponRetarget(ctx, s, dt);
            if (s.aiBadApproachTimer > 0.0) {
                s.aiBadApproachTimer = Math.max(0.0, s.aiBadApproachTimer - Math.max(0.0, dt));
                if (s.aiBadApproachTimer <= 0.0) s.aiBadApproachAngle = Double.NaN;
            }
            if (s.aiNoFireTimer > 0.0) {
                s.aiNoFireTimer = Math.max(0.0, s.aiNoFireTimer - Math.max(0.0, dt) * 0.35);
            }
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) {
                // Bases and static defense turrets stay put but can still defend themselves.
                s.vx = 0;
                s.vy = 0;
                Ship target = periodicClosestRetargetTarget(ctx, s);
                if (!isAlive(target)) target = TargetingSystem.getPreferredEnemyTarget(ctx, s);
                if (target != null && target.alive && !target.dying) {
                    fireIfAble(ctx, s, target, dt, Math.hypot(target.x - s.x, target.y - s.y));
                }
                s.tryCIWS(dt, ctx.projectiles);
                continue;
            }
            if (s.role == ShipRole.MINER) {
                // Mining movement is handled in EconomySystem. Miners only do opportunistic self-defense here.
                Ship target = periodicClosestRetargetTarget(ctx, s);
                if (!isAlive(target)) target = TargetingSystem.getPreferredEnemyTarget(ctx, s);
                if (target != null && target.alive && !target.dying) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (d <= 280) {
                        fireIfAble(ctx, s, target, dt, d);
                    }
                }
                s.tryCIWS(dt, ctx.projectiles);
                continue;
            }
            if (s.role == ShipRole.PD_CRAFT && s.carrierOwnerId < 0 && s.minerHomeBase != null) {
                Ship escortCarrier = s.minerHomeBase;
                boolean validCarrierAnchor = isAlive(escortCarrier)
                        && escortCarrier.isCarrier
                        && escortCarrier.faction != null
                        && s.faction != null
                        && escortCarrier.faction.teamId() == s.faction.teamId();
                if (!validCarrierAnchor) {
                    s.minerHomeBase = null;
                } else {
                    Ship target = findEscortThreatNearCarrier(ctx, s, escortCarrier);
                    if (isAlive(target)) {
                        fight(ctx, s, target, dt);
                    } else {
                        double orbitRange = Math.max(130.0, escortCarrier.radius + 95.0);
                        double speed = Math.max(95.0, MovementModel.speedCeiling(s) * 0.92);
                        orbit(s, escortCarrier.x, escortCarrier.y, orbitRange, speed, dt, ((s.id & 1) == 0) ? 1.0 : -1.0);
                    }
                    s.tryCIWS(dt, ctx.projectiles);
                    applyAsteroidAvoidance(ctx, s, dt);
                    applyProjectileLaneAvoidance(ctx, s, dt);
                    continue;
                }
            }
            if (s.carrierOwnerId >= 0) {
                // Carrier-launched craft movement is owned by CarrierSystem; keep only lightweight fire control here.
                Ship target = periodicClosestRetargetTarget(ctx, s);
                if (!isAlive(target)) target = TargetingSystem.getPreferredEnemyTarget(ctx, s);
                if (!isAlive(target)) target = findImmediateThreat(ctx, s, Math.max(180.0, preferredRange(s) * 0.78));
                if (target != null && target.alive && !target.dying) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    fireIfAble(ctx, s, target, dt, d);
                }
                s.tryCIWS(dt, ctx.projectiles);
                applyAsteroidAvoidance(ctx, s, dt);
                applyProjectileLaneAvoidance(ctx, s, dt);
                continue;
            }

            if (applyFleetBehavior(ctx, fleetState, s, dt)) {
                applyAsteroidAvoidance(ctx, s, dt);
                applyProjectileLaneAvoidance(ctx, s, dt);
                continue;
            }

            Ship target = selectEngagementTarget(ctx, fleetState, s, dt);
            if (target != null && target.alive && !target.dying) {
                fight(ctx, s, target, dt);
            } else {
                wander(ctx, s, dt);
            }
            applyAsteroidAvoidance(ctx, s, dt);
            applyProjectileLaneAvoidance(ctx, s, dt);
        }

        // keep bounds
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying) continue;
            s.x = GameMath.clamp(s.x, 0, ctx.WORLD_W);
            s.y = GameMath.clamp(s.y, 0, ctx.WORLD_H);
        }
    }

    private static FleetState buildFleetState(GameContext ctx, double dt) {
        FleetState out = new FleetState();
        if (ctx == null || ctx.ships == null) return out;
        if (ctx.shipFleetCommandOverrides != null && !ctx.shipFleetCommandOverrides.isEmpty()) {
            ctx.shipFleetCommandOverrides.entrySet().removeIf(e -> !hasLiveShipId(ctx.ships, e.getKey()));
        }
        if (ctx.fleetCommandShips != null) ctx.fleetCommandShips.clear();
        if (ctx.fleetSharedTargets != null) ctx.fleetSharedTargets.clear();
        markKillConfirmTargets(ctx);

        Map<Integer, Double> bestScore = new HashMap<>();
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) continue;
            if (s.faction == null) continue;

            int teamId = s.faction.teamId();
            out.members.computeIfAbsent(teamId, k -> new ArrayList<>()).add(s);
            out.teamFactions.putIfAbsent(teamId, s.faction);

            double score = flagshipScore(s);
            Double current = bestScore.get(teamId);
            if (current == null || score > current) {
                bestScore.put(teamId, score);
                out.flagships.put(teamId, s);
            }
        }

        for (List<Ship> members : out.members.values()) {
            members.sort(Comparator.comparingDouble(AISystem::flagshipScore).reversed());
        }

        for (Map.Entry<Integer, List<Ship>> e : out.members.entrySet()) {
            int teamId = e.getKey();
            List<Ship> members = e.getValue();
            Ship flagship = out.flagships.get(teamId);
            SharedTargetChoice shared = selectSharedTargetForTeam(ctx, members, flagship);
            Faction teamFaction = out.teamFactions.get(teamId);
            shared = applyCommandLatencyAndFog(ctx, teamId, teamFaction, shared, dt);
            if (shared != null && shared.target != null) {
                out.sharedTargets.put(teamId, shared.target);
                out.sharedTargetConfidence.put(teamId, shared.confidence);
            }
            Ship threatened = selectMissileThreatFocusForTeam(ctx, members, flagship);
            if (threatened != null) out.missileThreatFocus.put(teamId, threatened);
            assignSquadObjectives(out, teamId, members, flagship, (shared == null) ? null : shared.target, threatened);
        }
        pruneTeamTransientState(out.members.keySet());

        updateTeamDangerMemory(ctx, out, Math.max(0.0, dt));
        refreshTeamFormationPlans(ctx, out);

        for (Map.Entry<Integer, Ship> e : out.flagships.entrySet()) {
            Faction f = out.teamFactions.get(e.getKey());
            if (f != null && ctx.fleetCommandShips != null) {
                ctx.fleetCommandShips.put(f, e.getValue());
            }
        }
        for (Map.Entry<Integer, Ship> e : out.sharedTargets.entrySet()) {
            Faction f = out.teamFactions.get(e.getKey());
            if (f != null && ctx.fleetSharedTargets != null) {
                ctx.fleetSharedTargets.put(f, e.getValue());
            }
        }
        return out;
    }

    private static SharedTargetChoice selectSharedTargetForTeam(GameContext ctx, List<Ship> members, Ship flagship) {
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

        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestConfidence = 0.0;
        for (Ship enemy : ctx.ships) {
            if (!isAlive(enemy)) continue;
            if (enemy.faction == null || anchor.faction.isFriendlyTo(enemy.faction)) continue;
            if (enemy.role == ShipRole.BASE) continue;

            int observers = 0;
            double observerPriority = 0.0;
            double observerConfidence = 0.0;
            for (Ship observer : members) {
                if (!isAlive(observer)) continue;
                if (TargetingSystem.isDetectableToObserver(observer, enemy)) {
                    double dObs = Math.hypot(enemy.x - observer.x, enemy.y - observer.y);
                    double ewConf = observerEWConfidence(ctx, observer, enemy, dObs);
                    observers++;
                    observerPriority += threatPriority(observer.role, enemy.role) * ewConf;
                    observerConfidence += ewConf;
                }
            }
            if (observers <= 0) continue;

            double confidence = Math.max(0.0, Math.min(1.0, (observerConfidence / Math.max(1e-9, aliveCount))));
            double avgPriority = observerPriority / Math.max(1.0, observerConfidence);
            double hpFrac = (enemy.hpMax <= 0) ? 1.0 : (enemy.hp / (double) enemy.hpMax);
            double d = Math.hypot(enemy.x - cx, enemy.y - cy);
            double score = (1.0 - hpFrac) * 720.0;
            score += roleWeightForFlagship(enemy.role) * 26.0;
            score += avgPriority * 92.0;
            score += Math.max(0.0, 1200.0 - d) * 0.15;
            score += retreatIntentBonus(enemy, cx, cy, hpFrac);
            score += confidence * 110.0;
            if (confidence < 0.32) score -= (0.32 - confidence) * 340.0;
            score -= killConfirmTargetPenalty(enemy, hpFrac);
            if (enemy == ctx.lockedTarget) score += 260.0;
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
                bestConfidence = confidence;
            }
        }
        return (best == null) ? null : new SharedTargetChoice(best, bestConfidence);
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

        for (Projectile p : ctx.projectiles) {
            if (!(p instanceof Missile m)) continue;
            if (!m.alive || !isAlive(m.target)) continue;
            Ship t = m.target;
            if (t.faction == null || m.faction == null || anchor.faction.isFriendlyTo(m.faction)) continue;
            if (!shipById.containsKey(t.id)) continue;
            incomingByShipId.merge(t.id, 1, Integer::sum);
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

        // Enemy fleets react with lag; jamming and low confidence increase the lag.
        double hold = TEAM_COMMAND_DELAY_TIMERS.getOrDefault(teamId, 0.0);
        int delayedId = TEAM_DELAYED_TARGET_IDS.getOrDefault(teamId, -1);
        Ship delayedTarget = findLiveShipById(ctx.ships, delayedId);
        double fogPenalty = 0.06 + (1.0 - Math.max(0.0, Math.min(1.0, rawChoice.confidence))) * 0.26;
        if (ctx.scienceJamming) fogPenalty += 0.14;
        double effectiveConfidence = Math.max(0.08, Math.min(1.0, rawChoice.confidence * (1.0 - fogPenalty)));

        if (hold > 0.0) {
            hold = Math.max(0.0, hold - Math.max(0.0, dt));
            TEAM_COMMAND_DELAY_TIMERS.put(teamId, hold);
            if (isAlive(delayedTarget)) {
                return new SharedTargetChoice(delayedTarget, Math.max(0.08, effectiveConfidence * 0.74));
            }
        }

        if (isAlive(delayedTarget) && delayedTarget.id != rawChoice.target.id) {
            double switchDelay = 0.20 + (1.0 - effectiveConfidence) * 0.72 + (ctx.scienceJamming ? 0.25 : 0.0);
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
        for (Ship s : pool) {
            if (isSupportRole(s.role) || hullFrac(s) < 0.52 || shieldFrac(s) < 0.30) {
                assigned.put(s.id, SquadObjective.RESERVE);
                continue;
            }
            if (isCapitalRole(s.role) && shouldRotateCapitalBehindLine(s, members, flagship)) {
                assigned.put(s.id, SquadObjective.RESERVE);
                continue;
            }
            if (isPointDefenseRole(s) && isAlive(threatenedAlly)) {
                assigned.put(s.id, SquadObjective.INTERCEPT);
            }
        }

        while (countAssigned(assigned, SquadObjective.RESERVE) < reserveQuota) {
            Ship pick = pickBestUnassigned(pool, assigned, s -> reserveScore(s, members, flagship));
            if (pick == null) break;
            assigned.put(pick.id, SquadObjective.RESERVE);
        }
        while (countAssigned(assigned, SquadObjective.INTERCEPT) < interceptQuota) {
            Ship pick = pickBestUnassigned(pool, assigned, s -> interceptScore(s, threatenedAlly));
            if (pick == null) break;
            assigned.put(pick.id, SquadObjective.INTERCEPT);
        }
        while (countAssigned(assigned, SquadObjective.FLANK) < flankQuota) {
            Ship pick = pickBestUnassigned(pool, assigned, s -> flankScore(s, sharedTarget));
            if (pick == null) break;
            assigned.put(pick.id, SquadObjective.FLANK);
        }

        for (Ship s : pool) {
            SquadObjective objective = assigned.getOrDefault(s.id, SquadObjective.HOLD);
            state.squadObjectives.put(s.id, objective);
        }
    }

    private static int countAssigned(Map<Integer, SquadObjective> assigned, SquadObjective objective) {
        if (assigned == null || objective == null || assigned.isEmpty()) return 0;
        int count = 0;
        for (SquadObjective o : assigned.values()) {
            if (o == objective) count++;
        }
        return count;
    }

    private interface ShipScore {
        double score(Ship s);
    }

    private static Ship pickBestUnassigned(List<Ship> pool, Map<Integer, SquadObjective> assigned, ShipScore scoreFn) {
        if (pool == null || pool.isEmpty() || scoreFn == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship s : pool) {
            if (!isAlive(s)) continue;
            if (assigned.containsKey(s.id)) continue;
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

    private static double flagshipScore(Ship s) {
        if (s == null) return 0.0;
        return roleWeightForFlagship(s.role) * 1000.0 + s.hpMax * 3.0 + s.radius * 8.0 + s.id * 0.0001;
    }

    private static double roleWeightForFlagship(ShipRole role) {
        if (role == null) return 1.0;
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

    private static boolean applyFleetBehavior(GameContext ctx, FleetState state, Ship s, double dt) {
        if (ctx == null || state == null || s == null) return false;
        if (s.faction == null) return false;
        int teamId = s.faction.teamId();
        Ship flagship = state.flagships.get(teamId);
        if (flagship == null || !flagship.alive || flagship.dying || flagship.hp <= 0) return false;
        if (s.role == ShipRole.MINER) return false;

        boolean playerDirected = playerCanDirectTeamFleet(ctx, s, flagship);
        GameContext.FleetCommand cmd = resolveFleetCommand(ctx, s, flagship);
        if (cmd == null || cmd == GameContext.FleetCommand.AUTO) return false;
        if (isRepairOrderCommand(cmd)) {
            s.tryInstantRepairFromOrder(REPAIR_ORDER_SAFE_SECONDS);
        }
        applyHazardCommandPosture(s, cmd);

        Ship target = selectEngagementTarget(ctx, state, s, dt);
        Ship base = TeamSystem.getBaseForTeam(ctx, s.faction);
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
                        if (d <= 380.0) fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                    }
                    s.tryCIWS(dt, ctx.projectiles);
                    return true;
                }
                case DEFEND, FORM_UP, ESCORT -> {
                    double keep = (base == null) ? Math.max(260.0, preferredRange(s) * 0.90)
                            : Math.max(260.0, base.radius + 150.0);
                    double defendPerimeter = (base == null) ? Math.max(780.0, preferredRange(s) * 1.8)
                            : Math.max(860.0, base.radius + 560.0);
                    boolean targetInPerimeter = target != null
                            && (base == null
                            ? Math.hypot(target.x - s.x, target.y - s.y) <= defendPerimeter
                            : Math.hypot(target.x - base.x, target.y - base.y) <= defendPerimeter);
                    if (target != null && target.alive && !target.dying && targetInPerimeter) {
                        double d = Math.hypot(target.x - s.x, target.y - s.y);
                        if (d > preferredRange(s) * 1.12) {
                            moveToward(s, target.x, target.y, speed * 1.00, dt);
                        } else {
                            fight(ctx, s, target, dt, teamConfidence, SquadObjective.HOLD);
                        }
                    } else if (base != null) {
                        orbit(s, base.x, base.y, keep, speed * 0.84, dt, ((s.id & 1) == 0) ? 1.0 : -1.0);
                    } else {
                        wander(ctx, s, dt);
                    }
                    s.tryCIWS(dt, ctx.projectiles);
                    return true;
                }
                case MINE -> {
                    Asteroid ast = EconomySystem.findBestAsteroidNear(ctx, s.x, s.y, 2400.0);
                    if (ast != null) moveToward(s, ast.x, ast.y, speed * 0.9, dt);
                    else if (base != null) moveToward(s, base.x, base.y, speed * 0.8, dt);
                    if (target != null) {
                        double d = Math.hypot(target.x - s.x, target.y - s.y);
                        if (d <= 360.0) fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                    }
                    s.tryCIWS(dt, ctx.projectiles);
                    return true;
                }
                default -> {
                    // ATTACK falls back to regular AI.
                    return false;
                }
            }
        }

        List<Ship> members = state.members.get(teamId);
        int slot = formationSlotIndex(members, flagship, s);
        int wingCount = formationWingCount(members, flagship);
        GameContext.FleetFormation desiredFormation = playerDirected
                ? ctx.alliedFleetFormation
                : state.autoFormation.getOrDefault(teamId, GameContext.FleetFormation.WEDGE);
        double spacingMul = state.autoFormationSpacing.getOrDefault(teamId, 1.0);
        double[] anchor = formationAnchor(
                flagship, slot, wingCount, s.radius, preferredRange(s) * 0.35 * spacingMul, desiredFormation, cmd);
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

        switch (cmd) {
            case RETREAT, RTB, REPAIR -> {
                if (base != null) {
                    moveToward(s, base.x, base.y, speed, dt);
                } else {
                    moveToward(s, flagship.x, flagship.y, speed * 0.9, dt);
                }
                if (target != null) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (d <= 320.0 && objective != SquadObjective.RESERVE) {
                        fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                    }
                }
            }
            case DEFEND, FORM_UP, ESCORT -> {
                double ad = Math.hypot(anchor[0] - s.x, anchor[1] - s.y);
                if (ad > Math.max(70.0, s.radius * 2.5)) {
                    double spdMul = (objective == SquadObjective.INTERCEPT) ? 1.06 : (objective == SquadObjective.RESERVE ? 0.82 : 0.92);
                    moveToward(s, anchor[0], anchor[1], speed * spdMul * coherenceSpeedMul, dt);
                } else {
                    setVelPerSec(s, flagship.vx / Math.max(1e-9, dt), flagship.vy / Math.max(1e-9, dt), dt);
                    rotateShipToward(s, flagship.angle, dt);
                }
                if (target != null) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (d <= preferredRange(s) * 1.5 && objective != SquadObjective.RESERVE) {
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
                    if (d <= 360.0) fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
                }
            }
            case ATTACK -> {
                if (playerDirected) {
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

        s.tryCIWS(dt, ctx.projectiles);
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
        GameContext.FleetCommand override = ctx.shipFleetCommandOverrides.get(ship.id);
        if (override != null && override != GameContext.FleetCommand.AUTO) return override;

        if (playerCanDirectTeamFleet(ctx, ship, flagship)
                && ship != ctx.player
                && ctx.alliedFleetCommand != null
                && ctx.alliedFleetCommand != GameContext.FleetCommand.AUTO) {
            return ctx.alliedFleetCommand;
        }

        double hpFrac = hullFrac(ship);
        double shFrac = shieldFrac(ship);
        double fireLoad = ship.totalFireIntensity();
        int fireRooms = ship.activeFireRoomCount();
        if (fireRooms >= 3 || fireLoad >= 3.4) return GameContext.FleetCommand.RETREAT;
        if (fireRooms >= 2 || fireLoad >= 2.1) return GameContext.FleetCommand.REPAIR;
        if (hpFrac < 0.38 || shFrac < 0.22) return GameContext.FleetCommand.RETREAT;
        if (ship.role == ShipRole.MINER) return GameContext.FleetCommand.MINE;
        if (flagship != null) {
            double fhp = hullFrac(flagship);
            double fsh = shieldFrac(flagship);
            if (fhp < 0.34 || fsh < 0.20) return GameContext.FleetCommand.RETREAT;
            Ship threat = (ctx.fleetSharedTargets == null || flagship.faction == null)
                    ? null
                    : ctx.fleetSharedTargets.get(flagship.faction);
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

    private static Ship sharedTargetForTeam(FleetState state, Ship s) {
        if (state == null || s == null || s.faction == null) return null;
        return state.sharedTargets.get(s.faction.teamId());
    }

    private static Ship selectEngagementTarget(GameContext ctx, FleetState state, Ship seeker, double dt) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;

        Ship immediate = scanImmediateThreatWithBackoff(
                ctx, seeker, Math.max(0.0, dt), Math.max(210.0, preferredRange(seeker) * 0.62));
        if (isAlive(immediate)) {
            clearEngagementScanBackoff(seeker);
            return immediate;
        }

        Ship periodic = periodicClosestRetargetTarget(ctx, seeker);
        if (isAlive(periodic) && canShipThreatenTarget(ctx, seeker, periodic)) {
            clearEngagementScanBackoff(seeker);
            return periodic;
        }

        Ship shared = sharedTargetForTeam(state, seeker);
        if (shouldCommitToSharedTarget(state, seeker, shared) && canShipThreatenTarget(ctx, seeker, shared)) {
            clearEngagementScanBackoff(seeker);
            return shared;
        }

        if (shouldDeferEngagementScan(seeker, Math.max(0.0, dt))) {
            if (isAlive(shared)) return shared;
            return null;
        }

        Ship preferred = TargetingSystem.getPreferredEnemyTarget(ctx, seeker);
        if (isAlive(preferred) && canShipThreatenTarget(ctx, seeker, preferred)) {
            clearEngagementScanBackoff(seeker);
            return preferred;
        }

        Ship reachable = findBestReachableEnemyTarget(ctx, seeker, shared, preferred);
        if (isAlive(reachable)) {
            clearEngagementScanBackoff(seeker);
            return reachable;
        }

        if (isAlive(preferred) || isAlive(shared)) {
            armEngagementScanBackoff(ctx, seeker, false);
        } else {
            armEngagementScanBackoff(ctx, seeker, true);
        }

        if (isAlive(preferred)) return preferred;
        if (isAlive(shared)) return shared;
        return null;
    }

    private static Ship scanImmediateThreatWithBackoff(GameContext ctx, Ship seeker, double dt, double radius) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;
        double t = IMMEDIATE_THREAT_SCAN_TIMERS.getOrDefault(seeker.id, 0.0) - dt;
        if (t > 0.0) {
            IMMEDIATE_THREAT_SCAN_TIMERS.put(seeker.id, t);
            return null;
        }
        Ship immediate = findImmediateThreat(ctx, seeker, radius);
        double jitter = (ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
        double baseCadence = isAlive(immediate)
                ? 0.08
                : idleImmediateThreatCadence(seeker.role);
        IMMEDIATE_THREAT_SCAN_TIMERS.put(seeker.id, baseCadence * (0.88 + jitter * 0.42));
        return immediate;
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
        if (ctx == null || seeker == null || seeker.faction == null || radius <= 0.0) return null;
        Ship best = null;
        double bestD2 = radius * radius;
        for (Ship enemy : ctx.ships) {
            if (!isAlive(enemy) || enemy.faction == null) continue;
            if (seeker.faction.isFriendlyTo(enemy.faction)) continue;
            if (!TargetingSystem.isDetectableToObserver(seeker, enemy)) continue;
            double d2 = dist2(seeker.x, seeker.y, enemy.x, enemy.y);
            if (d2 >= bestD2) continue;
            bestD2 = d2;
            best = enemy;
        }
        return best;
    }

    private static Ship findEscortThreatNearCarrier(GameContext ctx, Ship escort, Ship carrier) {
        if (ctx == null || escort == null || carrier == null || escort.faction == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double maxDist = 920.0;
        double maxDist2 = maxDist * maxDist;

        for (Ship enemy : ctx.ships) {
            if (!isAlive(enemy) || enemy.faction == null) continue;
            if (escort.faction.isFriendlyTo(enemy.faction)) continue;
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
        if (isAlive(best)) return best;
        return findImmediateThreat(ctx, escort, Math.max(280.0, preferredRange(escort) * 1.2));
    }

    private static Ship findBestReachableEnemyTarget(GameContext ctx, Ship seeker, Ship sharedHint, Ship preferredHint) {
        if (ctx == null || seeker == null || seeker.faction == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship enemy : ctx.ships) {
            if (!isAlive(enemy) || enemy.faction == null) continue;
            if (seeker.faction.isFriendlyTo(enemy.faction)) continue;
            if (!TargetingSystem.isDetectableToObserver(seeker, enemy)) continue;
            if (!canShipThreatenTarget(ctx, seeker, enemy)) continue;
            double d = Math.hypot(enemy.x - seeker.x, enemy.y - seeker.y);
            double score = Math.max(0.0, 1500.0 - d) * 0.92;
            score += roleWeightForFlagship(enemy.role) * 18.0;
            if (enemy == sharedHint) score += 180.0;
            if (enemy == preferredHint) score += 140.0;
            if (d < 240.0) score += 360.0;
            double localSupport = localSupportBiasAtPoint(ctx, seeker.faction, enemy.x, enemy.y, 680.0);
            score += localSupport * 82.0;
            if (!isCapitalRole(seeker.role) && localSupport < -1.25) score -= 220.0;
            int friendlyNear = countCombatantsNearPoint(ctx, seeker.faction, enemy.x, enemy.y, 420.0, true);
            int hostileNear = countCombatantsNearPoint(ctx, seeker.faction, enemy.x, enemy.y, 420.0, false);
            int overCommit = friendlyNear - hostileNear - 2;
            if (overCommit > 0) score -= overCommit * 44.0;
            score -= killConfirmTargetPenalty(enemy, hullFrac(enemy));
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private static boolean canShipThreatenTarget(GameContext ctx, Ship seeker, Ship target) {
        if (!isAlive(seeker) || !isAlive(target)) return false;
        if (!TargetingSystem.isDetectableToObserver(seeker, target)) return false;
        double rangeMul = (ctx == null) ? 1.0 : CampaignSystem.targetingRangeMul(ctx);
        double d = Math.hypot(target.x - seeker.x, target.y - seeker.y);
        if (d <= 240.0) return true;

        if (seeker.hasWaveMotionGun && d <= 2200.0 * rangeMul) return true;
        for (Turret t : seeker.turrets) {
            if (t == null) continue;
            double maxRange;
            if (seeker.role == ShipRole.BASE || seeker.role == ShipRole.STATIC_TURRET) {
                maxRange = (t.kind == Turret.Kind.MISSILE) ? 1400.0 : 900.0;
            } else {
                maxRange = (t.kind == Turret.Kind.MISSILE) ? 900.0 : 520.0;
            }
            maxRange *= rangeMul;
            if (d <= maxRange * 1.12) return true;
        }
        return false;
    }

    private static boolean shouldCommitToSharedTarget(FleetState state, Ship s, Ship target) {
        if (!isAlive(target)) return false;
        if (state == null || s == null || s.faction == null) return true;
        Double conf = state.sharedTargetConfidence.get(s.faction.teamId());
        if (conf == null) return true;
        double c = Math.max(0.0, Math.min(1.0, conf));
        if (isKillConfirmActive(target)) {
            if (hullFrac(target) < 0.11) return false;
            c *= 0.60;
        }
        if (c >= 0.56) return true;
        if (isSkirmishRole(s.role)) return c >= 0.24;
        if (isSupportRole(s.role)) return c >= 0.42;
        return c >= 0.34;
    }

    private static void pruneClosestRetargetState(List<Ship> ships) {
        if (ships == null || ships.isEmpty()) {
            CLOSEST_RETARGET_TIMERS.clear();
            CLOSEST_RETARGET_TARGET_IDS.clear();
            IMMEDIATE_THREAT_SCAN_TIMERS.clear();
            ENGAGEMENT_SCAN_BACKOFF_TIMERS.clear();
            return;
        }
        java.util.HashSet<Integer> liveIds = new java.util.HashSet<>();
        for (Ship s : ships) {
            if (!isAlive(s)) continue;
            liveIds.add(s.id);
        }
        CLOSEST_RETARGET_TIMERS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
        CLOSEST_RETARGET_TARGET_IDS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
        IMMEDIATE_THREAT_SCAN_TIMERS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
        ENGAGEMENT_SCAN_BACKOFF_TIMERS.entrySet().removeIf(e -> !liveIds.contains(e.getKey()));
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
        for (Ship enemy : ctx.ships) {
            if (!isAlive(enemy) || enemy.faction == null) continue;
            if (seeker.faction.isFriendlyTo(enemy.faction)) continue;
            if (!TargetingSystem.isDetectableToObserver(seeker, enemy)) continue;
            double d2 = dist2(seeker.x, seeker.y, enemy.x, enemy.y);
            if (d2 >= bestD2) continue;
            if (!canShipThreatenTarget(ctx, seeker, enemy)) continue;
            bestD2 = d2;
            best = enemy;
        }
        return best;
    }

    private static boolean playerCanDirectTeamFleet(GameContext ctx, Ship ship, Ship flagship) {
        if (ctx == null || ship == null || flagship == null || ctx.player == null) return false;
        if (!isAlive(flagship) || flagship != ctx.player) return false;
        if (ship.faction == null || ctx.player.faction == null) return false;
        return ship.faction.isFriendlyTo(ctx.player.faction);
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
        if (role == null) return false;
        return switch (role) {
            case MINER, HAULER, TRANSPORT, CARRIER, DRONE_CARRIER -> true;
            default -> false;
        };
    }

    private static boolean isMissileThreatRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case MISSILE_BOAT, BOMBER, STEALTH_SHIP, CRUISER,
                    BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP -> true;
            default -> false;
        };
    }

    private static boolean isCapitalRole(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP -> true;
            default -> false;
        };
    }

    private static double battlelineCoherenceScore(List<Ship> members, Ship flagship, Ship ship, Ship target) {
        if (!isAlive(flagship) || !isAlive(ship)) return 0.0;
        if (members == null || members.isEmpty()) return 0.0;

        double dFlag = Math.hypot(ship.x - flagship.x, ship.y - flagship.y);
        double cohesion = 1.0 - Math.max(0.0, Math.min(1.0, dFlag / Math.max(220.0, preferredRange(ship) * 2.8)));
        double outrunnerPenalty = 0.0;
        double crossfireBonus = 0.0;

        if (isAlive(target)) {
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

    private static double sharedTargetConfidence(FleetState state, Ship ship) {
        if (state == null || ship == null || ship.faction == null) return 1.0;
        Double c = state.sharedTargetConfidence.get(ship.faction.teamId());
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
        double spacing = Math.max(70.0, baseSpacing + radius * 1.2);
        double fx = Math.cos(flagship.angle);
        double fy = Math.sin(flagship.angle);
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
        return new double[]{flagship.x + offX, flagship.y + offY};
    }

    private static void fight(GameContext ctx, Ship s, Ship target, double dt) {
        fight(ctx, s, target, dt, 1.0, SquadObjective.HOLD);
    }

    private static void fight(GameContext ctx, Ship s, Ship target, double dt, double teamConfidence, SquadObjective objective) {
        if (ctx == null || s == null || target == null) return;
        if (objective == null) objective = SquadObjective.HOLD;
        // Determine preferred range by role
        double range = preferredRange(s);
        double aggression = roleAggressionBias(s.role);
        double standoff = roleStandoffBias(s.role);
        double approachMul = roleApproachSpeedMul(s.role);
        double orbitMul = roleOrbitSpeedMul(s.role);
        range *= (1.0 + standoff * 0.18);
        double selfHull = hullFrac(s);
        double selfShield = shieldFrac(s);
        double targetHull = hullFrac(target);
        double pushHullReq = 0.62 - aggression * 0.10;
        double pushShieldReq = 0.36 - aggression * 0.09;
        double targetVulnReq = 0.45 + aggression * 0.08;
        double fallbackHullReq = 0.46 + standoff * 0.08 - aggression * 0.05;
        double fallbackShieldReq = 0.26 + standoff * 0.07 - aggression * 0.04;
        boolean push = (selfHull > pushHullReq && selfShield > pushShieldReq && targetHull < targetVulnReq);
        boolean fallBack = (selfHull < fallbackHullReq || selfShield < fallbackShieldReq);
        double supportBalance = localSupportBalance(ctx, s, target, Math.max(560.0, range * 1.55));
        if (!isCapitalRole(s.role) && supportBalance < -0.95) fallBack = true;
        if (supportBalance > 1.15 && selfHull > 0.52 && selfShield > 0.30) push = true;
        if (push && supportBalance < -0.45) push = false;
        if (objective == SquadObjective.RESERVE && supportBalance < 0.25) fallBack = true;
        if (objective == SquadObjective.INTERCEPT && supportBalance > -0.30 && selfHull > 0.40) fallBack = false;
        if (push) range *= Math.max(0.62, 0.72 - aggression * 0.06);
        if (fallBack) range *= 1.32 + Math.max(0.0, standoff) * 0.10;
        double d2 = dist2(s.x, s.y, target.x, target.y);
        double d = Math.sqrt(d2);

        // Movement:
        // - If far: close in
        // - If close: orbit/strafe to avoid stacking
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
            // orbit at preferred range
            orbit(s, target.x, target.y, range, speed * (fallBack ? 0.98 : 0.95) * orbitMul, dt, orbitDir);
        }

        // Fire control
        int shotsFired = fireIfAble(ctx, s, target, dt, d, teamConfidence, objective);
        updateEngagementMemory(s, target, dt, d, range, shotsFired > 0, objective);
        // CIWS always tries to protect itself
        s.tryCIWS(dt, ctx.projectiles);
    }

    private static void wander(GameContext ctx, Ship s, double dt) {
        // Simple wander: drift toward the player's general area, but loosely.
        double tx = ctx.player.x + (ctx.rng.nextDouble() - 0.5) * 800.0;
        double ty = ctx.player.y + (ctx.rng.nextDouble() - 0.5) * 800.0;
        moveToward(s, tx, ty, Math.max(32.0, MovementModel.speedCeiling(s) * 0.7), dt);

        s.tryCIWS(dt, ctx.projectiles);
    }

    private static int fireIfAble(GameContext ctx, Ship s, Ship target, double dt, double dist) {
        return fireIfAble(ctx, s, target, dt, dist, 1.0, SquadObjective.HOLD);
    }

    private static int fireIfAble(GameContext ctx, Ship s, Ship target, double dt, double dist,
                                   double teamConfidence, SquadObjective objective) {
        if (ctx == null || s == null || target == null || ctx.projectiles == null) return 0;
        if (!TargetingSystem.isDetectableToObserver(s, target)) return 0;
        if (objective == null) objective = SquadObjective.HOLD;
        double rangeMul = CampaignSystem.targetingRangeMul(ctx);
        double sensorConfidence = observerEWConfidence(ctx, s, target, dist);
        double confidence = Math.max(0.0, Math.min(1.0, sensorConfidence * Math.max(0.20, teamConfidence)));
        boolean killConfirm = isKillConfirmActive(target);
        boolean overkillLikely = isOverkillLikely(ctx, s, target);
        double targetHull = hullFrac(target);
        int firedCount = 0;

        if (s.hasWaveMotionGun && (s.isWaveMotionCharging() || s.isWaveMotionBeamActive())) {
            s.trackWaveMotionAim(target.x, target.y);
            if (s.role == ShipRole.SUPERSHIP && s.isWaveMotionCharging()) {
                rotateShipTowardAssist(s, s.getWaveMotionAimAngle(), dt, Math.toRadians(260.0));
            }
        }

        if (s.hasWaveMotionGun) {
            double waveRangeBase = 2200.0;
            if (s.superweaponPattern == Ship.SuperweaponPattern.DIRECT_BEAM) {
                // Match fire gating to actual beam reach so Team C supers don't waste casts at extreme standoff.
                double beamReach = MathUtil.clamp(s.waveMotionSpeed * 0.96, 760.0, 1760.0);
                waveRangeBase = Math.max(640.0, beamReach * 0.92);
            }
            double waveRange = waveRangeBase * rangeMul;
            if (dist <= waveRange) {
                boolean superShip = (s.role == ShipRole.SUPERSHIP);
                boolean allowWave;
                if (superShip) {
                    // Supership ultimates should be visible threats; do not over-throttle them.
                    double confGate = isCapitalRole(target.role) ? 0.38 : 0.46;
                    double hullGate = isCapitalRole(target.role) ? 0.12 : 0.18;
                    allowWave = confidence >= confGate && targetHull > hullGate && !killConfirm;
                } else {
                    allowWave = confidence >= 0.62 && targetHull > 0.30 && !killConfirm && !overkillLikely;
                }
                if (objective == SquadObjective.RESERVE) {
                    allowWave = allowWave && confidence >= (superShip ? 0.58 : 0.78) && dist <= waveRange * (superShip ? 0.82 : 0.70);
                }
                if (objective == SquadObjective.INTERCEPT) {
                    allowWave = allowWave && confidence >= (superShip ? 0.44 : 0.56);
                }
                Projectile shot = allowWave ? s.tryFireWaveMotionGun(target, dt) : null;
                if (shot != null) {
                    ctx.projectiles.add(shot);
                    ScreenShake.kick(3.5);
                    firedCount++;
                }
            }
        }

        for (Turret t : s.turrets) {
            if (t == null) continue;

            // Always track assigned target, even when weapon is cooling down or out of range.
            if (t.kind == Turret.Kind.GUN) {
                if (s.faction == Faction.TEAM_C) {
                    // Directed-energy guns should bias direct tracking, not projectile lead.
                    t.aimAt(dt, s, target);
                } else {
                    t.aimAtLead(dt, s, target, Turret.effectiveGunProjectileSpeed(t));
                }
            } else {
                t.aimAt(dt, s, target);
            }

            // Rough engagement gating by weapon kind
            double gunRange;
            double missileRange;
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) {
                gunRange = 900.0;
                missileRange = 1400.0;
            } else {
                gunRange = 520.0;
                missileRange = 900.0;
            }
            gunRange *= gunRangeRoleMul(s.role);
            missileRange *= missileRangeRoleMul(s.role);
            double maxRange = (t.kind == Turret.Kind.MISSILE) ? missileRange : gunRange;
            maxRange *= rangeMul;
            if (dist > maxRange) continue;

            // Only fire if ready and roughly aligned
            if (!t.canFire()) continue;

            double wx = t.worldX(s);
            double wy = t.worldY(s);
            double desired = Math.atan2(target.y - wy, target.x - wx);
            double delta = Math.abs(MathUtil.normalizeAngle(desired - t.angle));

            // Allow looser alignment for missiles
            double tol = (t.kind == Turret.Kind.MISSILE) ? Math.toRadians(28) : Math.toRadians(14);
            if (delta > tol) continue;

            if (t.kind == Turret.Kind.MISSILE) {
                if (!shouldFireMissileWithDiscipline(ctx, s, target, dist, confidence, objective, killConfirm, overkillLikely)) {
                    continue;
                }
            } else if ((killConfirm || overkillLikely) && objective != SquadObjective.INTERCEPT) {
                // Preserve gun cycles when target is already collapsing unless we're in dedicated intercept duty.
                if (dist > 280.0 && targetHull < 0.18) continue;
            }

            Projectile p = t.fire(s, (t.kind == Turret.Kind.MISSILE ? target : null), dt);
            if (p != null) {
                ctx.projectiles.add(p);
                firedCount++;
            }
        }
        return firedCount;
    }

    private static void updateEngagementMemory(Ship s, Ship target, double dt, double dist, double range,
                                               boolean firedNow, SquadObjective objective) {
        if (s == null) return;
        if (!isAlive(target)) {
            s.aiNoFireTimer = 0.0;
            return;
        }
        s.aiLastEngagementX = target.x;
        s.aiLastEngagementY = target.y;
        if (firedNow) {
            s.aiNoFireTimer = Math.max(0.0, s.aiNoFireTimer - dt * 2.2);
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

    private static double preferredRange(Ship s) {
        if (s == null) return 380;
        // Keep roles feeling different
        return switch (s.role) {
            case FIGHTER, DRONE -> 210;
            case PD_CRAFT -> 260;
            case BOMBER -> 560;
            case PATROL -> 300;
            case PICKET -> 460;
            case FRIGATE -> 370;
            case CIWS_CORVETTE -> 280;
            case MISSILE_BOAT -> 760;
            case LIGHT_CRUISER -> 440;
            case MEDIUM_CRUISER, CRUISER -> 520;
            case BATTLECRUISER -> 460;
            case BATTLESHIP -> 650;
            case DREADNOUGHT -> 730;
            case SUPERSHIP -> 980;
            case CARRIER -> 920;
            case DRONE_CARRIER -> 820;
            case TRANSPORT, HAULER, MINER -> 760;
            case STEALTH_SHIP -> 420;
            default -> 380; // fallback for any roles you add later
        };

    }

    private static double roleAggressionBias(ShipRole role) {
        if (role == null) return 0.0;
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
        return switch (role) {
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
        return switch (role) {
            case PICKET -> 1.38;
            case BATTLESHIP -> 1.32;
            case DREADNOUGHT -> 1.36;
            case SUPERSHIP -> 1.42;
            case BATTLECRUISER -> 1.18;
            case LIGHT_CRUISER -> 1.12;
            case MEDIUM_CRUISER, CRUISER -> 1.16;
            case CARRIER, DRONE_CARRIER -> 1.04;
            case FRIGATE -> 1.00;
            case MISSILE_BOAT -> 0.90;
            case CIWS_CORVETTE -> 0.86;
            case PATROL -> 0.92;
            case STEALTH_SHIP -> 0.94;
            case FIGHTER, DRONE, PD_CRAFT, BOMBER -> 0.82;
            case TRANSPORT, HAULER, MINER -> 0.78;
            default -> 1.0;
        };
    }

    private static double missileRangeRoleMul(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case MISSILE_BOAT -> 1.28;
            case BOMBER -> 1.20;
            case CARRIER -> 1.18;
            case DRONE_CARRIER -> 1.14;
            case DREADNOUGHT -> 1.16;
            case SUPERSHIP -> 1.22;
            case BATTLESHIP -> 1.14;
            case BATTLECRUISER -> 1.08;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 1.05;
            case STEALTH_SHIP -> 1.02;
            case FRIGATE -> 1.00;
            case CIWS_CORVETTE -> 0.92;
            case PICKET, PATROL -> 0.90;
            case FIGHTER, DRONE, PD_CRAFT -> 0.80;
            case TRANSPORT, HAULER, MINER -> 0.86;
            default -> 1.0;
        };
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

        // If we have a nearby flagship, bias retreat back into fleet cohesion.
        Ship flagship = (ctx == null || ctx.fleetCommandShips == null || s.faction == null)
                ? null
                : ctx.fleetCommandShips.get(s.faction);
        if (isAlive(flagship) && flagship != s) {
            double fdx = flagship.x - s.x;
            double fdy = flagship.y - s.y;
            double fl = Math.hypot(fdx, fdy) + 1e-9;
            vx += (fdx / fl) * speedPerSec * 0.26;
            vy += (fdy / fl) * speedPerSec * 0.26;
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
        if (observer == null || target == null) return 0.0;
        double sensor = Math.max(0.20, observer.sensorRangeMultiplier());
        double sensorNorm = Math.max(0.20, Math.min(1.20, sensor));
        double rangeBudget = 1650.0 * sensorNorm;
        double distConf = Math.max(0.08, Math.min(1.0, 1.0 - dist / Math.max(420.0, rangeBudget)));
        double ewFactor = 1.0;
        if (ctx != null && ctx.scienceJamming && ctx.player != null && observer.faction != null && target.faction != null) {
            boolean observerFriendlyToPlayer = observer.faction.isFriendlyTo(ctx.player.faction);
            boolean targetFriendlyToPlayer = target.faction.isFriendlyTo(ctx.player.faction);
            if (observerFriendlyToPlayer) ewFactor *= 0.90; // own-spectrum noise while actively jamming
            if (!observerFriendlyToPlayer && targetFriendlyToPlayer) ewFactor *= 0.58; // player EW degrades enemy lock confidence
        }
        double conf = (sensorNorm * 0.62 + distConf * 0.38) * ewFactor;
        return Math.max(0.05, Math.min(1.0, conf));
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
        if (target.role == ShipRole.DREADNOUGHT || target.role == ShipRole.SUPERSHIP) expectedVolley *= 0.75;
        return expectedVolley >= effectiveHp * 1.15;
    }

    private static double localSupportBalance(GameContext ctx, Ship seeker, Ship target, double radius) {
        if (ctx == null || seeker == null || target == null || seeker.faction == null) return 0.0;
        double mx = (seeker.x + target.x) * 0.5;
        double my = (seeker.y + target.y) * 0.5;
        return localSupportBiasAtPoint(ctx, seeker.faction, mx, my, radius);
    }

    private static double localSupportBiasAtPoint(GameContext ctx, Faction perspective, double x, double y, double radius) {
        if (ctx == null || perspective == null || radius <= 0.0) return 0.0;
        double r2 = radius * radius;
        double friendly = 0.0;
        double hostile = 0.0;
        for (Ship s : ctx.ships) {
            if (!isAlive(s) || s.faction == null) continue;
            if (!isSupportRelevantCombatant(s.role)) continue;
            double d2 = dist2(s.x, s.y, x, y);
            if (d2 > r2) continue;
            double dist = Math.sqrt(Math.max(0.0, d2));
            double falloff = Math.max(0.35, 1.0 - (dist / radius) * 0.45);
            double w = supportCombatWeight(s.role) * falloff;
            if (perspective.isFriendlyTo(s.faction)) friendly += w;
            else hostile += w;
        }
        return friendly - hostile;
    }

    private static int countCombatantsNearPoint(GameContext ctx, Faction perspective, double x, double y, double radius, boolean friendly) {
        if (ctx == null || perspective == null || radius <= 0.0) return 0;
        int count = 0;
        double r2 = radius * radius;
        for (Ship s : ctx.ships) {
            if (!isAlive(s) || s.faction == null) continue;
            if (!isSupportRelevantCombatant(s.role)) continue;
            boolean isFriendly = perspective.isFriendlyTo(s.faction);
            if (friendly != isFriendly) continue;
            if (dist2(s.x, s.y, x, y) <= r2) count++;
        }
        return count;
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

    private static boolean isAlive(Ship s) {
        return s != null && s.alive && !s.dying && s.hp > 0;
    }

    private static double hullFrac(Ship s) {
        if (s == null || s.hpMax <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, s.hp / (double) s.hpMax));
    }

    private static double shieldFrac(Ship s) {
        if (s == null || s.shieldMax <= 0.0 || !s.shieldActive) return 1.0;
        return Math.max(0.0, Math.min(1.0, s.shield / Math.max(1e-9, s.shieldMax)));
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

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return dx*dx + dy*dy;
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
