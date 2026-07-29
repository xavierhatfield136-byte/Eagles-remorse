import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Carrier wing control:
 * - Auto-launch/replenish for carriers with auto-launch enabled.
 * - Wing states: ATTACK / RTB with recovery at parent carrier.
 * - DEFEND mode keeps wing near the carrier and intercepting local hostiles.
 */
public final class CarrierSystem {
    private CarrierSystem() {}

    private static final int MAX_GLOBAL_LAUNCHED_CRAFT = 220;
    private static final int FOUR_TEAM_MAX_GLOBAL_LAUNCHED_CRAFT = 96;
    private static final int FOUR_TEAM_MAX_TEAM_LAUNCHED_CRAFT = 18;
    private static final int FOUR_TEAM_MAX_TEAM_BOMBERS = 6;
    private static final int MAX_GLOBAL_PD_ESCORTS = 40;
    private static final double ORPHAN_DESPAWN_SECONDS = 18.0;
    private static final double RTB_HP_FRAC = 0.35;
    private static final double RECOVERY_PAD = 10.0;
    private static final double DEFEND_RANGE = 640.0;
    private static final double DEFEND_ORBIT = 360.0;
    private static final double ATTACK_SEARCH_RANGE = 2200.0;
    private static final double ATTACK_LEASH_RANGE = 1650.0;
    private static final double PD_ESCORT_RESPAWN_SECONDS = 9.0;
    private static final double PD_ESCORT_ANCHOR_RANGE = 360.0;
    private static final int STRIKE_WING_SIZE = 2;
    private static final int STRIKE_SQUAD_SIZE = 2;
    private static final double STRIKE_FORMATION_SPACING = 120.0;
    private static final double STRIKE_COHESION_RANGE = 420.0;
    private static final double BOMBER_ESCORT_RANGE = 360.0;
    private static final double BOMBER_GUARD_REACTION_RANGE = 640.0;
    private static final int MOTHERSHIP_PICKET_BERTHS = 4;
    private static final int MOBILE_STATION_PICKET_BERTHS = 3;
    private static final WeakHashMap<GameContext, Map<Integer, Double>> PD_ESCORT_COOLDOWNS = new WeakHashMap<>();
    private static final ThreadLocal<ListScratch> SCRATCH = ThreadLocal.withInitial(ListScratch::new);

    private static final class ListScratch {
        final java.util.ArrayDeque<ArrayList<Ship>> shipLists = new java.util.ArrayDeque<>();
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null || ctx.gameOver) return;
        if (ctx.ships == null || ctx.ships.isEmpty()) return;
        if (dt <= 0.0) return;

        Map<Integer, Ship> carriersById = collectAliveCarriers(ctx);
        updateOrphanedCraft(ctx, dt, carriersById.keySet());
        updateWingBehavior(ctx, dt, carriersById);

        List<Ship> carriers = new ArrayList<>(carriersById.values());
        updateCarrierPdEscorts(ctx, dt, carriers);

        int globalCraft = countGlobalLaunchedCraft(ctx);
        int globalCap = globalLaunchedCraftCap(ctx);

        for (Ship carrier : carriers) {
            if (globalCraft >= globalCap) break;
            if (!canUseAutomaticCraft(carrier)) continue;
            if (!carrier.carrierAutoLaunch) continue;
            if (CampaignSystem.coalitionSupportSmallCraftBudgetExceeded(ctx, carrier)) continue;

            // Don't auto-launch from player while in overlays.
            if (carrier == ctx.player && ctx.ui.hasBlockingOverlay()) continue;

            int launched = launchFlight(ctx, carrier, dt);
            if (launched <= 0) continue;
            globalCraft += launched;
        }
    }

    public static int tryLaunchFlight(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return 0;
        if (!carrier.alive || carrier.dying || carrier.hp <= 0) return 0;
        if (!carrier.isCarrier) return 0;
        if (!canUseAutomaticCraft(carrier)) return 0;
        if (CampaignSystem.coalitionSupportSmallCraftBudgetExceeded(ctx, carrier)) return 0;
        if (!carrier.canLaunchFighter()) return 0;
        if (countGlobalLaunchedCraft(ctx) >= globalLaunchedCraftCap(ctx)) return 0;
        return launchFlight(ctx, carrier, GameContext.DT);
    }

    public static int recallWing(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return 0;
        if (!carrier.isCarrier) return 0;
        int recalled = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId != carrier.id) continue;
            if (s.wingState != Ship.WingState.RTB) {
                s.wingState = Ship.WingState.RTB;
            }
            recalled++;
        }
        return recalled;
    }

    public static int countActiveWingByCarrier(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return 0;
        if (!carrier.isCarrier) return 0;
        return countActiveWingByCarrier(ctx, carrier.id);
    }

    private static Map<Integer, Ship> collectAliveCarriers(GameContext ctx) {
        Map<Integer, Ship> out = new HashMap<>();
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (!s.isCarrier) continue;
            out.put(s.id, s);
        }
        return out;
    }

    private static void updateOrphanedCraft(GameContext ctx, double dt, Set<Integer> aliveCarrierIds) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId < 0) continue;

            if (aliveCarrierIds.contains(s.carrierOwnerId)) {
                s.carrierOrphanTimer = -1.0;
                continue;
            }

            if (s.carrierOrphanTimer < 0.0) {
                s.carrierOrphanTimer = ORPHAN_DESPAWN_SECONDS;
            } else {
                s.carrierOrphanTimer -= dt;
            }

            if (s.carrierOrphanTimer > 0.0) continue;

            // Silent cleanup for orphaned strike craft to keep simulation bounded.
            retireCraft(s);
        }
    }

    private static void updateWingBehavior(GameContext ctx, double dt, Map<Integer, Ship> carriersById) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId < 0) continue;

            Ship carrier = carriersById.get(s.carrierOwnerId);
            if (carrier == null) continue;
            if (CampaignSystem.missionSubzoneBoundaryConstraintsEnabled(ctx)
                    && s.isSmallCraft()
                    && CampaignSystem.missionSubzoneForShip(ctx, s) >= 0
                    && CampaignSystem.missionSubzoneForShip(ctx, carrier) >= 0
                    && CampaignSystem.missionSubzoneForShip(ctx, s) != CampaignSystem.missionSubzoneForShip(ctx, carrier)) {
                retireCraft(s);
                continue;
            }

            if (s.needsStrikeCraftRearm()) {
                s.wingState = Ship.WingState.RTB;
            }

            double hpFrac = (s.hpMax <= 0) ? 0.0 : ((double) s.hp / (double) s.hpMax);
            if (hpFrac <= RTB_HP_FRAC) {
                s.wingState = Ship.WingState.RTB;
            }

            if (s.wingState == Ship.WingState.RTB) {
                applyRtb(s, carrier, dt);
                continue;
            }

            if (carrier.carrierCommandMode == Ship.CarrierCommandMode.DEFEND) {
                applyDefend(ctx, s, carrier, dt);
            } else {
                applyAttack(ctx, s, carrier, dt);
            }
        }
    }

    private static void applyRtb(Ship craft, Ship carrier, double dt) {
        steerToward(craft, carrier.x, carrier.y, Math.max(140.0, craft.desiredSpeed * 1.05), dt);

        double rec = carrier.radius + craft.radius + RECOVERY_PAD;
        double d2 = dist2(craft.x, craft.y, carrier.x, carrier.y);
        if (d2 <= rec * rec) {
            retireCraft(craft);
        }
    }

    private static void applyDefend(GameContext ctx, Ship craft, Ship carrier, double dt) {
        if (craft.role == ShipRole.BOMBER) {
            craft.wingState = Ship.WingState.RTB;
            applyRtb(craft, carrier, dt);
            return;
        }
        if (craft.role == ShipRole.FIGHTER) {
            Ship escortedBomber = findEscortBomber(ctx, craft);
            if (isAlive(escortedBomber)) {
                craft.wingState = Ship.WingState.RTB;
                applyRtb(craft, carrier, dt);
                return;
            }
        }
        Ship nearbyHostile = preferredTargetForCraft(ctx, craft, carrier);
        if (!isAlive(nearbyHostile)) {
            nearbyHostile = findClosestHostileToPoint(ctx, carrier, carrier.x, carrier.y, DEFEND_RANGE);
        }
        if (nearbyHostile != null) {
            steerWingTowardTarget(ctx, craft, carrier, nearbyHostile, Math.max(130.0, craft.desiredSpeed), dt);
            return;
        }

        double d = Math.hypot(craft.x - carrier.x, craft.y - carrier.y);
        if (d > DEFEND_RANGE * 1.25) {
            steerToward(craft, carrier.x, carrier.y, Math.max(130.0, craft.desiredSpeed), dt);
            return;
        }

        double orbitDir = ((craft.hashCode() & 1) == 0) ? 1.0 : -1.0;
        orbit(craft, carrier.x, carrier.y, DEFEND_ORBIT, Math.max(115.0, craft.desiredSpeed * 0.9), dt, orbitDir);
    }

    private static void applyAttack(GameContext ctx, Ship craft, Ship carrier, double dt) {
        Ship hostile = preferredTargetForCraft(ctx, craft, carrier);
        if (hostile != null) {
            if (attemptBoardingCapture(ctx, craft, carrier, hostile)) return;
            steerWingTowardTarget(ctx, craft, carrier, hostile, Math.max(135.0, craft.desiredSpeed * 1.02), dt);
            return;
        }

        if (craft.role == ShipRole.FIGHTER) {
            Ship bomber = findEscortBomber(ctx, craft);
            if (isAlive(bomber)) {
                holdEscortScreen(craft, bomber, dt);
                return;
            }
        }

        double d = Math.hypot(craft.x - carrier.x, craft.y - carrier.y);
        if (d > ATTACK_LEASH_RANGE) {
            steerToward(craft, carrier.x, carrier.y, Math.max(130.0, craft.desiredSpeed), dt);
            return;
        }

        // No hostile in range: hold a looser forward screen well ahead of the carrier.
        double fx = Math.cos(carrier.angle);
        double fy = Math.sin(carrier.angle);
        double tx = carrier.x + fx * (carrier.radius + 720.0);
        double ty = carrier.y + fy * (carrier.radius + 720.0);
        double side = ((craft.id & 1) == 0) ? -1.0 : 1.0;
        tx += -fy * side * 360.0;
        ty += fx * side * 360.0;
        steerToward(craft, tx, ty, Math.max(124.0, craft.desiredSpeed * 0.96), dt);
    }

    private static Ship findClosestHostileToPoint(GameContext ctx, Ship carrier, double x, double y, double maxDist) {
        if (carrier == null || carrier.faction == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(carrier.faction, x, y, maxDist, nearby);
            for (int i = 0; i < nearby.size(); i++) {
                Ship s = nearby.get(i);
                if (s == null) continue;
                if (!s.alive || s.dying || s.hp <= 0) continue;
                if (s.role == ShipRole.BASE) continue;
                if (s.faction == null) continue;
                if (carrier.faction.isFriendlyTo(s.faction)) continue;

                double d2 = dist2(s.x, s.y, x, y);
                if (d2 >= bestD2) continue;
                bestD2 = d2;
                best = s;
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return best;
    }

    private static int countActiveWingByCarrier(GameContext ctx, int carrierId) {
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId != carrierId) continue;
            count++;
        }
        return count;
    }

    private static int countGlobalLaunchedCraft(GameContext ctx) {
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId < 0) continue;
            count++;
        }
        return count;
    }

    private static boolean attemptBoardingCapture(GameContext ctx, Ship craft, Ship carrier, Ship hostile) {
        if (!isBoardingBomber(craft, carrier)) return false;
        if (!isBoardingTarget(ctx, hostile, carrier.faction)) return false;
        double captureRange = Math.max(42.0, hostile.radius + craft.radius + 18.0);
        if (dist2(craft.x, craft.y, hostile.x, hostile.y) > captureRange * captureRange) return false;

        convertBoardedShip(ctx, carrier, hostile);
        retireCraft(craft);
        return true;
    }

    private static int launchFlight(GameContext ctx, Ship carrier, double dt) {
        if (ctx == null || carrier == null) return 0;
        ShipRole squadRole = chooseLaunchRole(ctx, carrier);
        int activeWing = countActiveWingByCarrier(ctx, carrier.id);
        int squadSize = launchSquadSize(squadRole);
        int deckCap = launchDeckCap(carrier, squadRole);
        int deckRoom = Math.max(0, deckCap - activeWing);
        int globalRoom = Math.max(0, globalLaunchedCraftCap(ctx) - countGlobalLaunchedCraft(ctx));
        int teamRoom = Math.max(0, teamLaunchedCraftCap(ctx, carrier.faction) - countActiveWingByFaction(ctx, carrier.faction));
        int toLaunch = Math.min(squadSize, Math.min(deckRoom, Math.min(globalRoom, teamRoom)));
        if (toLaunch <= 0) return 0;
        int launched = 0;
        for (int i = 0; i < toLaunch; i++) {
            int slotIndex = activeWing + launched;
            Ship craft = launchCraft(ctx, carrier, squadRole, dt, slotIndex);
            if (craft == null) continue;
            launched++;
        }
        if (launched > 0) {
            carrier.flightDeckLaunchCursor = Math.floorMod(carrier.flightDeckLaunchCursor + 1, Math.max(1, carrier.flightDeckLoadout.length));
            carrier.resetFighterTimer();
            AudioSystem.onFlightLaunch(ctx, carrier);
        }
        return launched;
    }

    private static int launchSquadSize(ShipRole role) {
        return role == ShipRole.PICKET ? 1 : STRIKE_SQUAD_SIZE;
    }

    private static int launchDeckCap(Ship carrier, ShipRole role) {
        if (carrier == null) return 0;
        if (role != ShipRole.PICKET) return Math.max(0, carrier.maxFighters);
        if (carrier.role == ShipRole.MOTHERSHIP) return MOTHERSHIP_PICKET_BERTHS;
        if (carrier.role == ShipRole.MOBILE_STATION_TITAN) return MOBILE_STATION_PICKET_BERTHS;
        return Math.min(2, Math.max(0, carrier.maxFighters));
    }

    private static Ship launchCraft(GameContext ctx, Ship carrier, ShipRole craftRole, double dt, int slotIndex) {
        if (carrier == null || carrier.faction == null) return null;

        double side = ((slotIndex & 1) == 0) ? -1.0 : 1.0;
        double lateral = side * (8.0 + Math.random() * 6.0);
        double forward = carrier.radius + 18.0;

        double ca = Math.cos(carrier.angle);
        double sa = Math.sin(carrier.angle);

        double spawnX = carrier.x + ca * forward - sa * lateral;
        double spawnY = carrier.y + sa * forward + ca * lateral;

        Ship craft = SpawnSystem.spawnTeamShip(ctx, craftRole, carrier.faction, spawnX, spawnY);
        if (craft == null) return null;

        craft.carrierOwnerId = carrier.id;
        craft.minerHomeBase = carrier;
        craft.wingState = Ship.WingState.ATTACK;
        craft.carrierOrphanTimer = -1.0;
        craft.angle = carrier.angle;

        double launchSpeedPerSec = Math.max(120.0, craft.desiredSpeed * (craftRole == ShipRole.PICKET ? 0.44 : 0.75));
        craft.vx = carrier.vx + ca * launchSpeedPerSec * dt;
        craft.vy = carrier.vy + sa * launchSpeedPerSec * dt;
        return craft;
    }

    private static ShipRole chooseLaunchRole(GameContext ctx, Ship carrier) {
        if (carrier == null) return ShipRole.FIGHTER;
        if (!carrier.isCarrier) return ShipRole.FIGHTER;
        int slot = Math.floorMod(carrier.flightDeckLaunchCursor, Math.max(1, carrier.flightDeckLoadout.length));
        ShipRole role = carrier.flightDeckRoleAt(slot);
        if (role == null) return (carrier.role == ShipRole.DRONE_CARRIER) ? ShipRole.DRONE : ShipRole.FIGHTER;
        if (ctx != null
                && ctx.config != null
                && ctx.config.mode == app.config.GameMode.FOUR_TEAM_DOMINATION
                && carrier.faction != null
                && role == ShipRole.BOMBER
                && countWingRoleByFaction(ctx, carrier.faction, ShipRole.BOMBER) >= FOUR_TEAM_MAX_TEAM_BOMBERS) {
            return ShipRole.FIGHTER;
        }
        return role;
    }

    private static int globalLaunchedCraftCap(GameContext ctx) {
        if (ctx != null && ctx.config != null && ctx.config.mode == app.config.GameMode.FOUR_TEAM_DOMINATION) {
            return FOUR_TEAM_MAX_GLOBAL_LAUNCHED_CRAFT;
        }
        return MAX_GLOBAL_LAUNCHED_CRAFT;
    }

    private static int teamLaunchedCraftCap(GameContext ctx, Faction faction) {
        if (ctx != null && ctx.config != null && ctx.config.mode == app.config.GameMode.FOUR_TEAM_DOMINATION) {
            return FOUR_TEAM_MAX_TEAM_LAUNCHED_CRAFT;
        }
        return MAX_GLOBAL_LAUNCHED_CRAFT;
    }

    private static int countActiveWingByFaction(GameContext ctx, Faction faction) {
        int count = 0;
        if (ctx == null || faction == null) return 0;
        for (Ship s : ctx.ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId < 0) continue;
            if (s.faction == null || s.faction.teamId() != faction.teamId()) continue;
            count++;
        }
        return count;
    }

    private static int countWingRoleByFaction(GameContext ctx, Faction faction, ShipRole role) {
        int count = 0;
        if (ctx == null || faction == null || role == null) return 0;
        for (Ship s : ctx.ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s.carrierOwnerId < 0) continue;
            if (s.role != role) continue;
            if (s.faction == null || s.faction.teamId() != faction.teamId()) continue;
            count++;
        }
        return count;
    }

    public static Ship preferredTargetForCraft(GameContext ctx, Ship craft, Ship carrierHint) {
        if (ctx == null || craft == null || craft.faction == null) return null;
        Ship carrier = carrierHint;
        if (!isAlive(carrier) && craft.carrierOwnerId >= 0) {
            carrier = findLiveShipById(ctx.ships, craft.carrierOwnerId);
        }
        Ship designated = designatedTargetForCraft(ctx, craft, carrier);
        if (isAlive(designated)) {
            return designated;
        }
        if (isBoardingBomber(craft, carrier)) {
            return preferredBoardingTarget(ctx, craft, carrier);
        }
        Ship protectedBomber = (craft.role == ShipRole.FIGHTER) ? findEscortBomber(ctx, craft) : null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectHostileShipsNear(craft.faction, craft.x, craft.y, ATTACK_SEARCH_RANGE, nearby);
            for (int i = 0; i < nearby.size(); i++) {
                Ship enemy = nearby.get(i);
                if (!isAlive(enemy) || enemy.faction == null) continue;
                if (craft.faction.isFriendlyTo(enemy.faction)) continue;

                double dCraft = Math.hypot(enemy.x - craft.x, enemy.y - craft.y);
                if (dCraft > ATTACK_SEARCH_RANGE) continue;

                double score = Math.max(0.0, 1600.0 - dCraft) * 0.78;
                if (carrier != null) {
                    double dCarrier = Math.hypot(enemy.x - carrier.x, enemy.y - carrier.y);
                    score += Math.max(0.0, 1400.0 - dCarrier) * 0.12;
                    if (dCarrier > ATTACK_LEASH_RANGE * 1.55) score -= 220.0;
                }

                score += strikeRoleTargetBias(craft.role, enemy.role);
                if (craft.role == ShipRole.BOMBER && enemy.isSmallCraft()) {
                    score -= 240.0;
                }
                if (craft.role == ShipRole.DRONE && enemy.isSmallCraft()) {
                    score -= 120.0;
                }
                if (craft.role == ShipRole.BOMBER && isHighValueStrikeTarget(enemy)) {
                    score += 180.0;
                }
                if (craft.role == ShipRole.DRONE && isHighValueStrikeTarget(enemy)) {
                    score += 120.0;
                }
                if (carrier != null && enemy == designatedTargetForCraft(ctx, craft, carrier)) {
                    score += (craft.role == ShipRole.BOMBER) ? 360.0 : 220.0;
                }
                if (protectedBomber != null) {
                    double dBomber = Math.hypot(enemy.x - protectedBomber.x, enemy.y - protectedBomber.y);
                    if (dBomber <= BOMBER_GUARD_REACTION_RANGE) {
                        score += Math.max(0.0, BOMBER_GUARD_REACTION_RANGE - dBomber) * 0.95;
                    }
                }

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

    private static Ship preferredBoardingTarget(GameContext ctx, Ship craft, Ship carrier) {
        if (ctx == null || craft == null || carrier == null || carrier.faction == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(carrier.faction, craft.x, craft.y, ATTACK_SEARCH_RANGE, nearby);
        for (Ship enemy : nearby) {
            if (!isBoardingTarget(ctx, enemy, carrier.faction)) continue;

            double dCraft = Math.hypot(enemy.x - craft.x, enemy.y - craft.y);
            double dCarrier = Math.hypot(enemy.x - carrier.x, enemy.y - carrier.y);
            double hullFrac = (enemy.hpMax <= 0) ? 0.0 : MathUtil.clamp(enemy.hp / (double) enemy.hpMax, 0.0, 1.0);
            double shieldFrac = (enemy.shieldMax <= 1e-6) ? 0.0 : MathUtil.clamp(enemy.shield / enemy.shieldMax, 0.0, 1.0);
            double score = Math.max(0.0, 1600.0 - dCraft) * 0.72;
            score += Math.max(0.0, 1400.0 - dCarrier) * 0.08;
            score += (1.0 - hullFrac) * 520.0;
            score += (1.0 - shieldFrac) * 260.0;
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private static Ship designatedTargetForCraft(GameContext ctx, Ship craft, Ship carrier) {
        if (ctx == null || craft == null || craft.faction == null) return null;
        Ship committed = findLiveShipById(ctx.ships, craft.aiCommittedTargetId);
        if (isValidCraftTarget(craft, committed, carrier)) return committed;
        if (ctx.command != null && ctx.command.fleetSharedTargets != null) {
            Ship shared = ctx.command.fleetSharedTargets.get(craft.faction);
            if (isValidCraftTarget(craft, shared, carrier)) return shared;
        }
        if (craft.faction == Faction.ALLY && isValidCraftTarget(craft, ctx.lockedTarget, carrier)) {
            return ctx.lockedTarget;
        }
        return null;
    }

    private static boolean isValidCraftTarget(Ship craft, Ship target, Ship carrier) {
        if (!isAlive(target) || craft == null || craft.faction == null || target.faction == null) return false;
        if (craft.faction.isFriendlyTo(target.faction)) return false;
        if (!TargetingSystem.isDetectableToObserver(craft, target)) return false;
        double dCraft = Math.hypot(target.x - craft.x, target.y - craft.y);
        if (dCraft > ATTACK_SEARCH_RANGE * 1.45) return false;
        if (carrier != null) {
            double dCarrier = Math.hypot(target.x - carrier.x, target.y - carrier.y);
            if (dCarrier > ATTACK_LEASH_RANGE * 1.8) return false;
        }
        return true;
    }

    public static int recallDefensiveStrikeCraft(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return 0;
        int recalled = 0;
        for (Ship s : ctx.ships) {
            if (!isAlive(s)) continue;
            if (s.carrierOwnerId != carrier.id) continue;
            if (s.role == ShipRole.BOMBER) {
                if (s.wingState != Ship.WingState.RTB) {
                    s.wingState = Ship.WingState.RTB;
                    recalled++;
                }
                continue;
            }
            if (s.role == ShipRole.FIGHTER) {
                Ship bomber = findEscortBomber(ctx, s);
                if (isAlive(bomber) && s.wingState != Ship.WingState.RTB) {
                    s.wingState = Ship.WingState.RTB;
                    recalled++;
                }
            }
        }
        return recalled;
    }

    private static void updateCarrierPdEscorts(GameContext ctx, double dt, List<Ship> carriers) {
        if (ctx == null || carriers == null || carriers.isEmpty() || dt <= 0.0) return;
        Map<Integer, Double> cooldowns = pdEscortCooldowns(ctx);
        if (!cooldowns.isEmpty()) {
            List<Integer> expired = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : cooldowns.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                double t = Math.max(0.0, e.getValue() - dt);
                if (t <= 0.0) expired.add(e.getKey());
                else e.setValue(t);
            }
            for (Integer id : expired) cooldowns.remove(id);
        }

        int globalEscorts = countGlobalPdEscorts(ctx);
        for (Ship carrier : carriers) {
            if (carrier == null || !carrier.alive || carrier.dying || carrier.hp <= 0) continue;
            if (!carrier.isCarrier || carrier.faction == null) continue;
            if (!canUseAutomaticCraft(carrier)) continue;
            if (CampaignSystem.coalitionSupportSmallCraftBudgetExceeded(ctx, carrier)) continue;

            int desired = (carrier.role == ShipRole.DRONE_CARRIER) ? 2 : 1;
            int active = countPdEscortsForCarrier(ctx, carrier);
            if (active >= desired) continue;
            if (globalEscorts >= MAX_GLOBAL_PD_ESCORTS) break;

            double cd = cooldowns.getOrDefault(carrier.id, 0.0);
            if (cd > 0.0) continue;

            Ship escort = spawnPdEscortForCarrier(ctx, carrier);
            double jitter = (ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
            cooldowns.put(carrier.id, PD_ESCORT_RESPAWN_SECONDS * (0.85 + jitter * 0.35));
            if (escort != null) globalEscorts++;
        }
    }

    private static int countGlobalPdEscorts(GameContext ctx) {
        if (ctx == null || ctx.ships == null) return 0;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.PD_CRAFT) continue;
            if (s.carrierOwnerId >= 0) continue;
            count++;
        }
        return count;
    }

    private static int countPdEscortsForCarrier(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null || carrier.faction == null) return 0;
        int count = 0;
        double near2 = PD_ESCORT_ANCHOR_RANGE * PD_ESCORT_ANCHOR_RANGE;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.PD_CRAFT) continue;
            if (s.carrierOwnerId >= 0) continue;
            if (s.faction == null || s.faction.teamId() != carrier.faction.teamId()) continue;

            if (s.minerHomeBase == carrier || dist2(s.x, s.y, carrier.x, carrier.y) <= near2) count++;
        }
        return count;
    }

    private static Ship spawnPdEscortForCarrier(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null || carrier.faction == null) return null;
        for (int attempt = 0; attempt < 8; attempt++) {
            double side = ((attempt & 1) == 0) ? -1.0 : 1.0;
            double rollL = (ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
            double rollF = (ctx.rng == null) ? Math.random() : ctx.rng.nextDouble();
            double lateral = side * (carrier.radius + 28.0 + rollL * 16.0);
            double forward = carrier.radius + 28.0 + rollF * 12.0;

            double ca = Math.cos(carrier.angle);
            double sa = Math.sin(carrier.angle);
            double sx = carrier.x + ca * forward - sa * lateral;
            double sy = carrier.y + sa * forward + ca * lateral;

            Ship escort = SpawnSystem.spawnTeamShip(ctx, ShipRole.PD_CRAFT, carrier.faction, sx, sy);
            if (escort == null) continue;

            escort.carrierOwnerId = -1;
            escort.minerHomeBase = carrier;
            escort.angle = carrier.angle;
            escort.vx = carrier.vx * 0.9;
            escort.vy = carrier.vy * 0.9;
            return escort;
        }
        return null;
    }

    private static boolean canUseAutomaticCraft(Ship carrier) {
        if (carrier == null || carrier.role == null) return false;
        ShopHullCategory category = ShopHullCategory.forRole(carrier.role);
        return category == ShopHullCategory.CAPITAL || category == ShopHullCategory.TITAN;
    }

    private static Map<Integer, Double> pdEscortCooldowns(GameContext ctx) {
        return PD_ESCORT_COOLDOWNS.computeIfAbsent(ctx, k -> new HashMap<>());
    }

    private static void retireCraft(Ship s) {
        if (s == null) return;
        s.alive = false;
        s.hp = 0;
        s.vx = 0;
        s.vy = 0;
        s.carrierOwnerId = -1;
        s.carrierOrphanTimer = 0.0;
        s.wingState = Ship.WingState.ATTACK;
    }

    private static void setVelPerSec(Ship s, double vxPerSec, double vyPerSec, double dt) {
        if (dt <= 0) {
            s.vx = 0;
            s.vy = 0;
            return;
        }
        s.vx = vxPerSec * dt;
        s.vy = vyPerSec * dt;
        rotateToward(s, Math.atan2(vyPerSec, vxPerSec), dt);
    }

    private static void steerToward(Ship s, double tx, double ty, double speedPerSec, double dt) {
        double dx = tx - s.x;
        double dy = ty - s.y;
        double len = Math.sqrt(dx * dx + dy * dy) + 1e-9;
        setVelPerSec(s, dx / len * speedPerSec, dy / len * speedPerSec, dt);
    }

    private static void orbit(Ship s, double cx, double cy, double desiredRange, double speedPerSec, double dt, double dir) {
        double dx = cx - s.x;
        double dy = cy - s.y;
        double d = Math.sqrt(dx * dx + dy * dy) + 1e-9;

        double ux = dx / d;
        double uy = dy / d;

        double tx = -uy * dir;
        double ty = ux * dir;

        double err = d - desiredRange;
        double radial = Math.max(-1.0, Math.min(1.0, err / Math.max(1.0, desiredRange)));
        double blend = 0.55;

        double vx = (tx * (1.0 - blend) + ux * blend * radial) * speedPerSec;
        double vy = (ty * (1.0 - blend) + uy * blend * radial) * speedPerSec;
        setVelPerSec(s, vx, vy, dt);
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return dx * dx + dy * dy;
    }

    private static void steerWingTowardTarget(GameContext ctx, Ship craft, Ship carrier, Ship target, double speedPerSec, double dt) {
        if (!isAlive(target)) return;
        Ship leader = wingLeader(ctx, craft);
        int slot = wingSlotIndex(ctx, craft, leader);

        double tx = target.x;
        double ty = target.y;
        // Keep strike craft at standoff range; otherwise they collapse into point-blank dogpiles.
        double desiredRange = switch (craft.role) {
            case FIGHTER -> 520.0;
            case BOMBER -> 940.0;
            case DRONE -> 700.0;
            default -> 520.0;
        };
        // Boarding bombers must still close to capture once a target is isolated and weak.
        if (isBoardingBomber(craft, carrier) && carrier != null && carrier.faction != null
                && isBoardingTarget(ctx, target, carrier.faction)) {
            desiredRange = Math.max(42.0, target.radius + craft.radius + 18.0);
        } else if (craft.role == ShipRole.BOMBER && target.isSmallCraft()) {
            desiredRange = 1080.0;
        } else if (craft.role == ShipRole.DRONE && isHighValueStrikeTarget(target)) {
            desiredRange = 620.0;
        }

        double ang = Math.atan2(target.y - craft.y, target.x - craft.x);
        double tangent = ang + ((slot & 1) == 0 ? Math.PI * 0.5 : -Math.PI * 0.5);
        double offsetMag = Math.min(STRIKE_FORMATION_SPACING * 1.35, 90.0 + slot * 110.0);
        tx += Math.cos(ang + Math.PI) * desiredRange + Math.cos(tangent) * offsetMag;
        ty += Math.sin(ang + Math.PI) * desiredRange + Math.sin(tangent) * offsetMag;

        if (craft.role == ShipRole.FIGHTER) {
            Ship bomber = findEscortBomber(ctx, craft);
            if (isAlive(bomber)) {
                double guardX = bomber.x - Math.sin(bomber.angle) * (((craft.id & 1) == 0) ? -BOMBER_ESCORT_RANGE : BOMBER_ESCORT_RANGE);
                double guardY = bomber.y + Math.cos(bomber.angle) * (((craft.id & 1) == 0) ? -BOMBER_ESCORT_RANGE : BOMBER_ESCORT_RANGE);
                tx = tx * 0.58 + guardX * 0.42;
                ty = ty * 0.58 + guardY * 0.42;
            }
        } else if (craft.role == ShipRole.DRONE) {
            double biasX = target.x * 0.72 + craft.x * 0.28;
            double biasY = target.y * 0.72 + craft.y * 0.28;
            tx = tx * 0.7 + biasX * 0.3;
            ty = ty * 0.7 + biasY * 0.3;
        }

        if (leader != null && leader != craft) {
            tx = tx * 0.82 + leader.x * 0.18;
            ty = ty * 0.82 + leader.y * 0.18;
        }

        if (carrier != null) {
            double dCarrier = Math.hypot(tx - carrier.x, ty - carrier.y);
            if (dCarrier > ATTACK_LEASH_RANGE) {
                double ca = Math.atan2(ty - carrier.y, tx - carrier.x);
                tx = carrier.x + Math.cos(ca) * ATTACK_LEASH_RANGE;
                ty = carrier.y + Math.sin(ca) * ATTACK_LEASH_RANGE;
            }
        }

        steerToward(craft, tx, ty, speedPerSec, dt);
    }

    private static boolean isHighValueStrikeTarget(Ship target) {
        if (!isAlive(target) || target.role == null) return false;
        return switch (target.role) {
            case MISSILE_BOAT, ARTILLERY_SHIP, TRANSPORT, HAULER, MINER,
                    LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER,
                    BATTLESHIP, DREADNOUGHT, SUPERSHIP,
                    CARRIER, DRONE_CARRIER,
                    TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                    INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                    MOBILE_STATION_TITAN, HYPERWEAPON_TITAN, MOTHERSHIP, BASE -> true;
            default -> false;
        };
    }

    private static void holdEscortScreen(Ship fighter, Ship bomber, double dt) {
        if (!isAlive(fighter) || !isAlive(bomber)) return;
        double side = ((fighter.id & 1) == 0) ? -1.0 : 1.0;
        double tx = bomber.x - Math.sin(bomber.angle) * (side * BOMBER_ESCORT_RANGE) - Math.cos(bomber.angle) * 24.0;
        double ty = bomber.y + Math.cos(bomber.angle) * (side * BOMBER_ESCORT_RANGE) - Math.sin(bomber.angle) * 24.0;
        steerToward(fighter, tx, ty, Math.max(140.0, fighter.desiredSpeed * 0.98), dt);
    }

    private static Ship findEscortBomber(GameContext ctx, Ship fighter) {
        if (ctx == null || fighter == null) return null;
        if (fighter.role != ShipRole.FIGHTER) return null;
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectAliveShipsNear(fighter.x, fighter.y, BOMBER_GUARD_REACTION_RANGE, nearby);
            for (int i = 0; i < nearby.size(); i++) {
                Ship s = nearby.get(i);
                if (!isAlive(s)) continue;
                if (s.role != ShipRole.BOMBER) continue;
                if (s.carrierOwnerId != fighter.carrierOwnerId) continue;
                double d2 = dist2(fighter.x, fighter.y, s.x, s.y);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = s;
                }
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return best;
    }

    private static Ship wingLeader(GameContext ctx, Ship craft) {
        if (ctx == null || craft == null) return craft;
        Ship best = craft;
        int bestId = craft.id;
        int count = 0;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectAliveShipsNear(craft.x, craft.y, STRIKE_COHESION_RANGE, nearby);
            for (int i = 0; i < nearby.size(); i++) {
                Ship s = nearby.get(i);
                if (!isAlive(s)) continue;
                if (s.carrierOwnerId != craft.carrierOwnerId) continue;
                if (s.role != craft.role) continue;
                if (dist2(s.x, s.y, craft.x, craft.y) > STRIKE_COHESION_RANGE * STRIKE_COHESION_RANGE) continue;
                if (s.id < bestId) {
                    bestId = s.id;
                    best = s;
                }
                count++;
                if (count >= STRIKE_WING_SIZE) break;
            }
        } finally {
            releaseShipScratch(nearby);
        }
        return best;
    }

    private static int wingSlotIndex(GameContext ctx, Ship craft, Ship leader) {
        if (ctx == null || craft == null) return 0;
        ArrayList<Ship> peers = borrowShipScratch();
        double queryX = (leader != null) ? leader.x : craft.x;
        double queryY = (leader != null) ? leader.y : craft.y;
        ArrayList<Ship> nearby = borrowShipScratch();
        try {
            ctx.entityQuery.collectAliveShipsNear(queryX, queryY, STRIKE_COHESION_RANGE, nearby);
            for (int i = 0; i < nearby.size(); i++) {
                Ship s = nearby.get(i);
                if (!isAlive(s)) continue;
                if (s.carrierOwnerId != craft.carrierOwnerId) continue;
                if (s.role != craft.role) continue;
                if (leader != null && dist2(s.x, s.y, leader.x, leader.y) > STRIKE_COHESION_RANGE * STRIKE_COHESION_RANGE) continue;
                peers.add(s);
            }
            peers.sort((a, b) -> Integer.compare(a.id, b.id));
            for (int i = 0; i < peers.size() && i < STRIKE_WING_SIZE; i++) {
                if (peers.get(i) == craft) return i;
            }
        } finally {
            releaseShipScratch(nearby);
            releaseShipScratch(peers);
        }
        return Math.max(0, craft.id % STRIKE_WING_SIZE);
    }

    private static double strikeRoleTargetBias(ShipRole attacker, ShipRole target) {
        if (attacker == null || target == null) return 0.0;
        return switch (attacker) {
            case FIGHTER -> switch (target) {
                case BOMBER -> 340.0;
                case DRONE -> 220.0;
                case FIGHTER, PD_CRAFT -> 120.0;
                case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, MISSILE_BOAT, TRANSPORT, HAULER -> 145.0;
                case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP, BASE -> -70.0;
                default -> 32.0;
            };
            case BOMBER -> switch (target) {
                case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP, BASE -> 360.0;
                case CARRIER, DRONE_CARRIER -> 310.0;
                case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER, TRANSPORT, HAULER -> 190.0;
                case FIGHTER, DRONE, PD_CRAFT, PATROL, PICKET -> -120.0;
                default -> 40.0;
            };
            case DRONE -> switch (target) {
                case BOMBER -> 210.0;
                case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, MISSILE_BOAT, TRANSPORT, HAULER -> 180.0;
                case BATTLECRUISER, BATTLESHIP, CARRIER, DRONE_CARRIER -> 120.0;
                case FIGHTER, DRONE, PD_CRAFT -> 80.0;
                default -> 36.0;
            };
            default -> 0.0;
        };
    }

    private static ArrayList<Ship> borrowShipScratch() {
        ListScratch scratch = SCRATCH.get();
        ArrayList<Ship> list = scratch.shipLists.pollFirst();
        if (list == null) return new ArrayList<>(48);
        list.clear();
        return list;
    }

    private static void releaseShipScratch(ArrayList<Ship> list) {
        if (list == null) return;
        list.clear();
        SCRATCH.get().shipLists.offerFirst(list);
    }

    private static boolean isBoardingBomber(Ship craft, Ship carrier) {
        return craft != null && craft.role == ShipRole.BOMBER && isBoardingCarrier(carrier);
    }

    private static boolean isBoardingCarrier(Ship carrier) {
        return isAlive(carrier) && carrier.role == ShipRole.BOARDING_RECOVERY_TITAN;
    }

    private static boolean isBoardingTarget(GameContext ctx, Ship target, Faction boardingFaction) {
        if (!isAlive(target) || boardingFaction == null || target.faction == null) return false;
        if (boardingFaction.isFriendlyTo(target.faction)) return false;
        if (target.isSmallCraft()) return false;
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) return false;
        if (target.role.isTitanOrMothership()) return false;
        if (target.surrendered) return true;

        double hullFrac = (target.hpMax <= 0) ? 0.0 : MathUtil.clamp(target.hp / (double) target.hpMax, 0.0, 1.0);
        double shieldFrac = (target.shieldMax <= 1e-6) ? 0.0 : MathUtil.clamp(target.shield / target.shieldMax, 0.0, 1.0);
        boolean weakEnough = hullFrac <= 0.60 || (hullFrac + shieldFrac) <= 0.92;
        if (!weakEnough) return false;
        return isIsolatedBoardingTarget(ctx, target);
    }

    private static boolean isIsolatedBoardingTarget(GameContext ctx, Ship target) {
        if (ctx == null || target == null || target.faction == null) return false;
        int nearbyFriends = 0;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectAliveShipsNear(target.x, target.y, 250.0, nearby);
        for (Ship s : nearby) {
            if (!isAlive(s) || s == target) continue;
            if (s.faction == null || !s.faction.isFriendlyTo(target.faction)) continue;
            if (s.isSmallCraft()) continue;
            nearbyFriends++;
            if (nearbyFriends > 1) return false;
        }
        return true;
    }

    private static void convertBoardedShip(GameContext ctx, Ship carrier, Ship target) {
        if (carrier == null || target == null || carrier.faction == null) return;
        Faction convertedFaction = Faction.forTeamId(carrier.faction.teamId());
        target.faction = convertedFaction;
        target.clearSurrenderState();
        target.cancelBattlefieldWarp();
        target.reveal(3.0);
        target.addTemporaryDisable(0.45);
        target.healHull(Math.max(8.0, target.hpMax * 0.18));
        target.healShield(Math.max(12.0, target.shieldMax * 0.30));
        target.playerTaggedForKillCredit = false;
        target.playerKillCreditPaid = false;
        try {
            DoctrineRegistry.applyToShip(target);
        } catch (Throwable ignored) {
        }
        if (target.isCarrier) {
            recallWing(ctx, target);
        }
        if (ctx != null) {
            java.awt.Color tint = new java.awt.Color(132, 255, 214);
            VFX.spawnBoardingCaptureEffect(carrier.x, carrier.y, target.x, target.y, tint);
            EventSystem.showWorldCallout(ctx, target.x, target.y - target.radius - 24.0, "CONVERTED", tint, 1.25);
            if (carrier == ctx.player) {
                EventSystem.showBanner(ctx, "BOARDING ACTION SUCCESSFUL", 1.1);
            }
        }
    }

    private static Ship findLiveShipById(List<Ship> ships, int id) {
        if (ships == null || id <= 0) return null;
        for (Ship s : ships) {
            if (s != null && s.id == id && isAlive(s)) return s;
        }
        return null;
    }

    private static boolean isAlive(Ship s) {
        return s != null && s.alive && !s.dying && s.hp > 0;
    }

    private static void rotateToward(Ship s, double desiredAngle, double dt) {
        if (s == null || dt <= 0.0) return;
        double maxRate = switch (s.role) {
            case FIGHTER, BOMBER, DRONE, PD_CRAFT -> Math.toRadians(190.0);
            case PICKET, PATROL, STEALTH_SHIP -> Math.toRadians(160.0);
            default -> Math.toRadians(120.0);
        };
        double delta = MathUtil.normalizeAngle(desiredAngle - s.angle);
        double maxDelta = maxRate * dt;
        delta = MathUtil.clamp(delta, -maxDelta, maxDelta);
        s.angle = MathUtil.normalizeAngle(s.angle + delta);
    }
}
