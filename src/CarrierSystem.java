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

    private static final int MAX_GLOBAL_LAUNCHED_CRAFT = 700;
    private static final int MAX_GLOBAL_PD_ESCORTS = 40;
    private static final double ORPHAN_DESPAWN_SECONDS = 18.0;
    private static final double RTB_HP_FRAC = 0.35;
    private static final double RECOVERY_PAD = 10.0;
    private static final double DEFEND_RANGE = 320.0;
    private static final double DEFEND_ORBIT = 170.0;
    private static final double ATTACK_SEARCH_RANGE = 1450.0;
    private static final double ATTACK_LEASH_RANGE = 980.0;
    private static final int BOMBER_SLOT_INTERVAL = 5;
    private static final double PD_ESCORT_RESPAWN_SECONDS = 9.0;
    private static final double PD_ESCORT_ANCHOR_RANGE = 360.0;
    private static final int STRIKE_WING_SIZE = 5;
    private static final double STRIKE_FORMATION_SPACING = 54.0;
    private static final double STRIKE_COHESION_RANGE = 240.0;
    private static final double BOMBER_ESCORT_RANGE = 220.0;
    private static final double BOMBER_GUARD_REACTION_RANGE = 420.0;
    private static final WeakHashMap<GameContext, Map<Integer, Double>> PD_ESCORT_COOLDOWNS = new WeakHashMap<>();

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

        for (Ship carrier : carriers) {
            if (globalCraft >= MAX_GLOBAL_LAUNCHED_CRAFT) break;
            if (!carrier.carrierAutoLaunch) continue;

            // Don't auto-launch from player while in overlays.
            if (carrier == ctx.player && (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen
                    || ctx.powerManagementOpen || ctx.crewStationsOpen || ctx.flightDeckOpen)) continue;

            int launched = launchFlight(ctx, carrier, dt);
            if (launched <= 0) continue;
            globalCraft += launched;
        }
    }

    public static int tryLaunchFlight(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return 0;
        if (!carrier.alive || carrier.dying || carrier.hp <= 0) return 0;
        if (!carrier.isCarrier) return 0;
        if (!carrier.canLaunchFighter()) return 0;
        if (countGlobalLaunchedCraft(ctx) >= MAX_GLOBAL_LAUNCHED_CRAFT) return 0;
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

        // No hostile in range: hold a loose forward screen near the carrier.
        double fx = Math.cos(carrier.angle);
        double fy = Math.sin(carrier.angle);
        double tx = carrier.x + fx * (carrier.radius + 220.0);
        double ty = carrier.y + fy * (carrier.radius + 220.0);
        double side = ((craft.id & 1) == 0) ? -1.0 : 1.0;
        tx += -fy * side * 140.0;
        ty += fx * side * 140.0;
        steerToward(craft, tx, ty, Math.max(120.0, craft.desiredSpeed * 0.92), dt);
    }

    private static Ship findClosestHostileToPoint(GameContext ctx, Ship carrier, double x, double y, double maxDist) {
        if (carrier == null || carrier.faction == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        for (Ship s : ctx.ships) {
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

    private static int launchFlight(GameContext ctx, Ship carrier, double dt) {
        if (ctx == null || carrier == null) return 0;
        int activeWing = countActiveWingByCarrier(ctx, carrier.id);
        int deckRoom = Math.max(0, carrier.maxFighters - activeWing);
        int globalRoom = Math.max(0, MAX_GLOBAL_LAUNCHED_CRAFT - countGlobalLaunchedCraft(ctx));
        int toLaunch = Math.min(5, Math.min(deckRoom, globalRoom));
        if (toLaunch <= 0) return 0;
        int launched = 0;
        for (int i = 0; i < toLaunch; i++) {
            int slotIndex = activeWing + launched;
            ShipRole launchRole = chooseLaunchRole(carrier, slotIndex);
            Ship craft = launchCraft(ctx, carrier, launchRole, dt, slotIndex);
            if (craft == null) continue;
            launched++;
        }
        if (launched > 0) carrier.resetFighterTimer();
        return launched;
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
        craft.wingState = Ship.WingState.ATTACK;
        craft.carrierOrphanTimer = -1.0;
        craft.angle = carrier.angle;

        double launchSpeedPerSec = Math.max(120.0, craft.desiredSpeed * 0.75);
        craft.vx = carrier.vx + ca * launchSpeedPerSec * dt;
        craft.vy = carrier.vy + sa * launchSpeedPerSec * dt;
        return craft;
    }

    private static ShipRole chooseLaunchRole(Ship carrier, int activeWing) {
        if (carrier == null) return ShipRole.FIGHTER;
        if (!carrier.isCarrier) return ShipRole.FIGHTER;
        int slot = Math.floorMod(carrier.flightDeckLaunchCursor, Math.max(1, carrier.flightDeckLoadout.length));
        ShipRole role = carrier.flightDeckRoleAt(slot);
        carrier.flightDeckLaunchCursor = (slot + 1) % Math.max(1, carrier.flightDeckLoadout.length);
        if (role == null) return (carrier.role == ShipRole.DRONE_CARRIER) ? ShipRole.DRONE : ShipRole.FIGHTER;
        return role;
    }

    public static Ship preferredTargetForCraft(GameContext ctx, Ship craft, Ship carrierHint) {
        if (ctx == null || craft == null || craft.faction == null) return null;
        Ship carrier = carrierHint;
        if (!isAlive(carrier) && craft.carrierOwnerId >= 0) {
            carrier = findLiveShipById(ctx.ships, craft.carrierOwnerId);
        }
        Ship protectedBomber = (craft.role == ShipRole.FIGHTER) ? findEscortBomber(ctx, craft) : null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship enemy : ctx.ships) {
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
        return best;
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
        double desiredRange = switch (craft.role) {
            case FIGHTER -> Math.max(22.0, target.radius + 18.0);
            case BOMBER -> Math.max(72.0, target.radius + 54.0);
            case DRONE -> Math.max(44.0, target.radius + 36.0);
            default -> Math.max(26.0, target.radius + 22.0);
        };

        double ang = Math.atan2(target.y - craft.y, target.x - craft.x);
        double tangent = ang + ((slot & 1) == 0 ? Math.PI * 0.5 : -Math.PI * 0.5);
        double offsetMag = Math.min(STRIKE_FORMATION_SPACING * 1.15, 16.0 + slot * 12.0);
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
        for (Ship s : ctx.ships) {
            if (!isAlive(s)) continue;
            if (s.role != ShipRole.BOMBER) continue;
            if (s.carrierOwnerId != fighter.carrierOwnerId) continue;
            double d2 = dist2(fighter.x, fighter.y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    private static Ship wingLeader(GameContext ctx, Ship craft) {
        if (ctx == null || craft == null) return craft;
        Ship best = craft;
        int bestId = craft.id;
        int count = 0;
        for (Ship s : ctx.ships) {
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
        return best;
    }

    private static int wingSlotIndex(GameContext ctx, Ship craft, Ship leader) {
        if (ctx == null || craft == null) return 0;
        ArrayList<Ship> peers = new ArrayList<>();
        for (Ship s : ctx.ships) {
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
