import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carrier wing control:
 * - Auto-launch/replenish for carriers with auto-launch enabled.
 * - Wing states: ATTACK / RTB with recovery at parent carrier.
 * - DEFEND mode keeps wing near the carrier and intercepting local hostiles.
 */
public final class CarrierSystem {
    private CarrierSystem() {}

    private static final int MAX_GLOBAL_LAUNCHED_CRAFT = 140;
    private static final double ORPHAN_DESPAWN_SECONDS = 18.0;
    private static final double RTB_HP_FRAC = 0.35;
    private static final double RECOVERY_PAD = 10.0;
    private static final double DEFEND_RANGE = 320.0;
    private static final double DEFEND_ORBIT = 170.0;
    private static final double ATTACK_SEARCH_RANGE = 1450.0;
    private static final double ATTACK_LEASH_RANGE = 980.0;
    private static final int BOMBER_SLOT_INTERVAL = 4;

    public static void update(GameContext ctx, double dt) {
        if (ctx == null || ctx.gameOver) return;
        if (ctx.ships == null || ctx.ships.isEmpty()) return;
        if (dt <= 0.0) return;

        Map<Integer, Ship> carriersById = collectAliveCarriers(ctx);
        updateOrphanedCraft(ctx, dt, carriersById.keySet());
        updateWingBehavior(ctx, dt, carriersById);

        int globalCraft = countGlobalLaunchedCraft(ctx);
        List<Ship> carriers = new ArrayList<>(carriersById.values());

        for (Ship carrier : carriers) {
            if (globalCraft >= MAX_GLOBAL_LAUNCHED_CRAFT) break;
            if (!carrier.carrierAutoLaunch) continue;

            // Don't auto-launch from player while in overlays.
            if (carrier == ctx.player && (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen)) continue;

            int activeWing = countActiveWingByCarrier(ctx, carrier.id);
            if (activeWing >= Math.max(0, carrier.maxFighters)) continue;
            if (!carrier.canLaunchFighter()) continue;

            ShipRole launchRole = chooseLaunchRole(carrier, activeWing);
            Ship launched = launchCraft(ctx, carrier, launchRole, dt, activeWing);
            if (launched == null) continue;

            carrier.resetFighterTimer();
            globalCraft++;
        }
    }

    public static boolean tryLaunchOne(GameContext ctx, Ship carrier) {
        if (ctx == null || carrier == null) return false;
        if (!carrier.alive || carrier.dying || carrier.hp <= 0) return false;
        if (!carrier.isCarrier) return false;
        if (!carrier.canLaunchFighter()) return false;

        int activeWing = countActiveWingByCarrier(ctx, carrier.id);
        if (activeWing >= Math.max(0, carrier.maxFighters)) return false;
        if (countGlobalLaunchedCraft(ctx) >= MAX_GLOBAL_LAUNCHED_CRAFT) return false;

        ShipRole launchRole = chooseLaunchRole(carrier, activeWing);
        Ship launched = launchCraft(ctx, carrier, launchRole, GameContext.DT, activeWing);
        if (launched == null) return false;
        carrier.resetFighterTimer();
        return true;
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
        Ship nearbyHostile = findClosestHostileToPoint(ctx, carrier, carrier.x, carrier.y, DEFEND_RANGE);
        if (nearbyHostile != null) {
            steerToward(craft, nearbyHostile.x, nearbyHostile.y, Math.max(130.0, craft.desiredSpeed), dt);
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
        Ship hostile = findClosestHostileToPoint(ctx, craft, craft.x, craft.y, ATTACK_SEARCH_RANGE);
        if (hostile != null) {
            steerToward(craft, hostile.x, hostile.y, Math.max(135.0, craft.desiredSpeed * 1.02), dt);
            return;
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

        // Every Nth launched craft from a carrier is a bomber to add strike capability.
        int ordinal = Math.max(0, activeWing) + 1;
        if (ordinal % BOMBER_SLOT_INTERVAL == 0) return ShipRole.BOMBER;

        return ShipRole.FIGHTER;
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
