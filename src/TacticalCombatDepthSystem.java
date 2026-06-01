import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tactical depth features layered over the established combat simulation.
 */
public final class TacticalCombatDepthSystem {
    public enum Order {
        ESCORT, SCREEN, FLANK, HOLD, PURSUE, RETREAT, REGROUP, FOCUS_FIRE, PROTECT, CAPTURE_ZONE, SALVAGE_UNDER_FIRE
    }

    public enum Doctrine {
        BALANCED, AGGRESSIVE, CAUTIOUS, POINT_DEFENSE, SUPPORT, AVOID_COLLATERAL
    }

    public enum SupportMode {
        NONE, TRACTOR_TOW, REPAIR_DRONES, SHIELD_TRANSFER, MINE_LAYER, MINE_CLEARER, ECM_BURST
    }

    public enum PointDefensePriority {
        MISSILES_FIRST, STRIKE_CRAFT_FIRST, BALANCED
    }

    public enum Hazard {
        FIRE, DECOMPRESSION, COOLANT_LEAK, ELECTRICAL_ARC, AMMO_COOKOFF, REACTOR_INSTABILITY
    }

    public record TimelineMarker(double seconds, String text) {}

    private static final class ShipState {
        final EnumMap<Hazard, Double> hazards = new EnumMap<>(Hazard.class);
        Doctrine doctrine = Doctrine.BALANCED;
        SupportMode supportMode = SupportMode.NONE;
        double weaponHeat;
        int ballisticAmmo = 120;
        int missileAmmo = 36;
        int mineAmmo = 6;
        boolean bulkheadsSealed;
        boolean evacuated;
        boolean orientationHold;
        double heldAngle;
        int towTargetId = -1;
        double supportTimer;
        double orderDelay;
        Order pendingOrder;
        PointDefensePriority pointDefensePriority = PointDefensePriority.MISSILES_FIRST;
        int persistentScars;
    }

    private static final class Mine {
        final Faction faction;
        final double x;
        final double y;
        double ttl = 75.0;

        Mine(Faction faction, double x, double y) {
            this.faction = faction;
            this.x = x;
            this.y = y;
        }
    }

    private static final class State {
        final Map<Integer, ShipState> ships = new HashMap<>();
        final Map<Integer, LinkedHashSet<Integer>> groups = new HashMap<>();
        final List<Mine> mines = new ArrayList<>();
        final List<TimelineMarker> timeline = new ArrayList<>();
        int selectedGroup = 1;
        Order selectedOrder = Order.ESCORT;
        double orderX = Double.NaN;
        double orderY = Double.NaN;
        int protectedTargetId = -1;
        boolean tacticalPause;
        boolean overlayOpen;
    }

    private static final WeakHashMap<GameContext, State> STATES = new WeakHashMap<>();

    private TacticalCombatDepthSystem() {}

    public static void init(GameContext ctx) {
        if (ctx == null) return;
        STATES.put(ctx, new State());
    }

    public static void update(GameContext ctx, double dt) {
        State state = state(ctx);
        if (state == null || dt <= 0.0) return;
        updateMines(ctx, state, dt);
        for (Ship ship : new ArrayList<>(ctx.ships)) {
            if (!alive(ship)) continue;
            ShipState tactical = shipState(state, ship);
            updateWeaponHeat(tactical, dt);
            updateHazards(ctx, state, ship, tactical, dt);
            updateSupport(ctx, state, ship, tactical, dt);
            updateDockingAssist(ctx, ship, dt);
            if (tactical.orientationHold) ship.angle = tactical.heldAngle;
            if (tactical.orderDelay > 0.0) {
                tactical.orderDelay = Math.max(0.0, tactical.orderDelay - dt);
                if (tactical.orderDelay <= 0.0 && tactical.pendingOrder != null) {
                    applyFleetCommand(ctx, ship, tactical.pendingOrder);
                    marker(ctx, acknowledgmentText(ctx, ship, tactical.pendingOrder));
                    tactical.pendingOrder = null;
                }
            }
        }
    }

    public static boolean isTacticalPause(GameContext ctx) {
        State state = state(ctx);
        return state != null && state.tacticalPause;
    }

    public static void togglePause(GameContext ctx) {
        State state = state(ctx);
        if (state == null) return;
        state.tacticalPause = !state.tacticalPause;
        marker(ctx, state.tacticalPause ? "TACTICAL PAUSE ENGAGED" : "TACTICAL PAUSE RELEASED");
    }

    public static void toggleOverlay(GameContext ctx) {
        State state = state(ctx);
        if (state != null) state.overlayOpen = !state.overlayOpen;
    }

    public static void cycleOrder(GameContext ctx) {
        State state = state(ctx);
        if (state == null) return;
        Order[] values = Order.values();
        state.selectedOrder = values[(state.selectedOrder.ordinal() + 1) % values.length];
        EventSystem.showBanner(ctx, "TACTICAL ORDER: " + state.selectedOrder.name(), 0.9);
    }

    public static void issueSelectedOrder(GameContext ctx, double x, double y) {
        State state = state(ctx);
        if (state == null) return;
        state.orderX = x;
        state.orderY = y;
        LinkedHashSet<Integer> group = state.groups.getOrDefault(state.selectedGroup, new LinkedHashSet<>());
        for (Integer id : group) {
            Ship ship = findShip(ctx, id);
            if (!alive(ship)) continue;
            ShipState tactical = shipState(state, ship);
            tactical.orderDelay = commandDelaySeconds(ctx.player, ship);
            tactical.pendingOrder = state.selectedOrder;
        }
        marker(ctx, state.selectedOrder.name() + " ORDER TO GROUP " + state.selectedGroup);
    }

    public static void selectNearestFriendlyIntoGroup(GameContext ctx, int groupNumber) {
        State state = state(ctx);
        if (state == null || ctx.player == null) return;
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship ship : ctx.ships) {
            if (!alive(ship) || ship == ctx.player || ship.faction == null || ctx.player.faction == null) continue;
            if (!ship.faction.isFriendlyTo(ctx.player.faction)) continue;
            double d2 = GameMath.dist2(ship.x, ship.y, ctx.cursorWorldX, ctx.cursorWorldY);
            if (d2 < bestD2) {
                best = ship;
                bestD2 = d2;
            }
        }
        if (best == null) return;
        int group = Math.max(1, Math.min(4, groupNumber));
        state.selectedGroup = group;
        state.groups.computeIfAbsent(group, ignored -> new LinkedHashSet<>()).add(best.id);
        marker(ctx, best.name + " ASSIGNED TO GROUP " + group);
    }

    public static void cycleGroup(GameContext ctx) {
        State state = state(ctx);
        if (state == null) return;
        state.selectedGroup = state.selectedGroup >= 4 ? 1 : state.selectedGroup + 1;
        EventSystem.showBanner(ctx, "TACTICAL GROUP " + state.selectedGroup, 0.8);
    }

    public static void selectNearestFriendlyIntoActiveGroup(GameContext ctx) {
        State state = state(ctx);
        if (state != null) selectNearestFriendlyIntoGroup(ctx, state.selectedGroup);
    }

    public static void cycleDoctrine(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        State state = state(ctx);
        ShipState tactical = shipState(state, ctx.player);
        Doctrine[] values = Doctrine.values();
        tactical.doctrine = values[(tactical.doctrine.ordinal() + 1) % values.length];
        for (Integer id : state.groups.getOrDefault(state.selectedGroup, new LinkedHashSet<>())) {
            Ship ship = findShip(ctx, id);
            if (alive(ship)) shipState(state, ship).doctrine = tactical.doctrine;
        }
        EventSystem.showBanner(ctx, "TACTICAL DOCTRINE: " + tactical.doctrine.name(), 0.9);
    }

    public static void cycleSupportMode(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        State state = state(ctx);
        ShipState tactical = shipState(state, ctx.player);
        SupportMode[] values = SupportMode.values();
        tactical.supportMode = values[(tactical.supportMode.ordinal() + 1) % values.length];
        EventSystem.showBanner(ctx, "SUPPORT MODE: " + tactical.supportMode.name(), 0.9);
    }

    public static void activateSupportAtCursor(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        State state = state(ctx);
        ShipState tactical = shipState(state, ctx.player);
        switch (tactical.supportMode) {
            case TRACTOR_TOW -> {
                Ship target = nearestDisabledOrSurrendered(ctx, ctx.cursorWorldX, ctx.cursorWorldY, 520.0);
                if (target != null) {
                    tactical.towTargetId = target.id;
                    marker(ctx, "TRACTOR LOCK: " + target.name);
                }
            }
            case MINE_LAYER -> {
                if (tactical.doctrine == Doctrine.AVOID_COLLATERAL
                        && collateralRiskNear(ctx, ctx.cursorWorldX, ctx.cursorWorldY, 190.0)) {
                    marker(ctx, "MINE DEPLOYMENT BLOCKED: COLLATERAL RISK");
                } else if (tactical.mineAmmo > 0) {
                    tactical.mineAmmo--;
                    state.mines.add(new Mine(ctx.player.faction, ctx.cursorWorldX, ctx.cursorWorldY));
                    marker(ctx, "MINEFIELD DEPLOYED");
                }
            }
            case ECM_BURST -> {
                for (Projectile projectile : ctx.projectiles) {
                    if (projectile instanceof Missile missile && missile.target == ctx.player) {
                        missile.guidanceTicksRemaining = Math.min(missile.guidanceTicksRemaining, 1);
                    }
                }
                disruptTractors(ctx, ctx.player.x, ctx.player.y, 440.0);
                marker(ctx, "ECM / DECOY / CHAFF BURST: HOSTILE GUIDANCE DEGRADED");
            }
            default -> marker(ctx, tactical.supportMode.name() + " ACTIVE");
        }
    }

    public static void toggleOrientationHold(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        ShipState tactical = shipState(state(ctx), ctx.player);
        tactical.orientationHold = !tactical.orientationHold;
        tactical.heldAngle = ctx.player.angle;
        EventSystem.showBanner(ctx, "ORIENTATION HOLD: " + (tactical.orientationHold ? "ON" : "OFF"), 0.9);
    }

    public static void toggleBulkheads(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        ShipState tactical = shipState(state(ctx), ctx.player);
        tactical.bulkheadsSealed = !tactical.bulkheadsSealed;
        tactical.evacuated = tactical.bulkheadsSealed;
        marker(ctx, tactical.bulkheadsSealed ? "BULKHEADS SEALED: DAMAGED ROOMS EVACUATED" : "BULKHEADS OPEN");
    }

    public static void overdriveWeapons(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        ShipState tactical = shipState(state(ctx), ctx.player);
        if (tactical.weaponHeat >= 0.88) {
            EventSystem.showBanner(ctx, "WEAPON OVERDRIVE LOCKED: HEAT HIGH", 1.0);
            return;
        }
        tactical.weaponHeat = Math.min(1.0, tactical.weaponHeat + 0.30);
        for (Turret turret : ctx.player.turrets) if (turret != null) turret.reduceCooldown(0.5);
        marker(ctx, "WEAPON OVERDRIVE");
    }

    public static void cyclePointDefensePriority(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        ShipState tactical = shipState(state(ctx), ctx.player);
        PointDefensePriority[] values = PointDefensePriority.values();
        tactical.pointDefensePriority = values[(tactical.pointDefensePriority.ordinal() + 1) % values.length];
        EventSystem.showBanner(ctx, "POINT DEFENSE: " + tactical.pointDefensePriority.name(), 0.9);
    }

    public static PointDefensePriority pointDefensePriority(Ship ship) {
        if (ship == null) return PointDefensePriority.BALANCED;
        for (State state : STATES.values()) {
            ShipState tactical = state.ships.get(ship.id);
            if (tactical != null) return tactical.pointDefensePriority;
        }
        return PointDefensePriority.BALANCED;
    }

    public static boolean canFireWeapon(Ship ship, Turret turret) {
        if (ship == null || turret == null) return false;
        for (State state : STATES.values()) {
            ShipState tactical = state.ships.get(ship.id);
            if (tactical == null) continue;
            if (tactical.weaponHeat >= 0.98) return false;
            return turret.kind == Turret.Kind.MISSILE ? tactical.missileAmmo > 0 : tactical.ballisticAmmo > 0;
        }
        return true;
    }

    public static void onWeaponFired(Ship ship, Turret turret) {
        if (ship == null || turret == null) return;
        for (State state : STATES.values()) {
            ShipState tactical = state.ships.get(ship.id);
            if (tactical == null) continue;
            tactical.weaponHeat = Math.min(1.0, tactical.weaponHeat + (turret.kind == Turret.Kind.MISSILE ? 0.035 : 0.018));
            if (turret.kind == Turret.Kind.MISSILE) tactical.missileAmmo = Math.max(0, tactical.missileAmmo - 1);
            else tactical.ballisticAmmo = Math.max(0, tactical.ballisticAmmo - 1);
        }
    }

    public static void detonateVolatileOre(GameContext ctx, Asteroid asteroid) {
        if (ctx == null || asteroid == null || !asteroid.rich) return;
        for (Ship ship : ctx.ships) {
            if (!alive(ship)) continue;
            double distance = Math.hypot(ship.x - asteroid.x, ship.y - asteroid.y);
            if (distance > 260.0 + ship.radius) continue;
            int damage = Math.max(1, (int) Math.round((1.0 - Math.min(1.0, distance / 300.0)) * 18.0));
            ship.takeDamage(damage, asteroid.x, asteroid.y, ship.x - asteroid.x, ship.y - asteroid.y,
                    Ship.InteriorHitProfile.RED_EXPLOSIVE);
        }
        Explosion.spawnDeath(asteroid.x, asteroid.y);
        marker(ctx, "VOLATILE ORE DETONATION");
    }

    public static String weaponRoleTooltip(Ship ship, Turret turret) {
        if (turret == null) return "";
        if (turret.kind == Turret.Kind.MISSILE) return "MISSILE / " + turret.missileRole + " / guided rack";
        if (ship != null && (ship.role == ShipRole.ARTILLERY_SHIP || ship.role == ShipRole.ARTILLERY_TITAN)) {
            return "SPINAL / armor penetration / alignment required";
        }
        if (isBroadsideHull(ship) && Math.abs(turret.localY) > Math.abs(turret.localX) * 0.72) {
            return "BROADSIDE / staggered battery / shield pressure";
        }
        return "BATTERY / subsystem disruption / sustained fire";
    }

    public static void scuttleNearestDisabled(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        Ship target = nearestDisabledOrSurrendered(ctx, ctx.cursorWorldX, ctx.cursorWorldY, 420.0);
        if (target == null || target.faction == null || !target.faction.isFriendlyTo(ctx.player.faction)) return;
        target.takePenetratingInternalDamage(Math.max(1, target.hpMax * 3), target.x, target.y, 0.0, 0.0);
        marker(ctx, "SCUTTLE ORDER: " + target.name);
    }

    public static void seedHazard(Ship ship, Hazard hazard, double intensity) {
        if (ship == null || hazard == null) return;
        for (State state : STATES.values()) {
            ShipState tactical = state.ships.get(ship.id);
            if (tactical != null) tactical.hazards.put(hazard, MathUtil.clamp(intensity, 0.0, 2.0));
        }
    }

    public static double hazardIntensity(GameContext ctx, Ship ship, Hazard hazard) {
        State state = state(ctx);
        if (state == null || ship == null || hazard == null) return 0.0;
        return shipState(state, ship).hazards.getOrDefault(hazard, 0.0);
    }

    public static void seedHazard(GameContext ctx, Ship ship, Hazard hazard, double intensity) {
        State state = state(ctx);
        if (state == null || ship == null || hazard == null) return;
        shipState(state, ship).hazards.put(hazard, MathUtil.clamp(intensity, 0.0, 2.0));
    }

    public static int persistentScarCount(GameContext ctx, Ship ship) {
        State state = state(ctx);
        if (state == null || ship == null) return 0;
        return shipState(state, ship).persistentScars;
    }

    public static int mineCount(GameContext ctx) {
        State state = state(ctx);
        return state == null ? 0 : state.mines.size();
    }

    public static List<TimelineMarker> timeline(GameContext ctx) {
        State state = state(ctx);
        return state == null ? List.of() : List.copyOf(state.timeline);
    }

    public static void drawOverlay(GameContext ctx, Graphics2D g2, int viewW, int viewH) {
        State state = state(ctx);
        if (state == null || !state.overlayOpen || g2 == null) return;
        int x = Math.max(18, viewW - 430);
        int y = 110;
        int w = 410;
        int h = 294;
        g2.setColor(new Color(5, 12, 23, 232));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(120, 208, 255, 220));
        g2.drawRoundRect(x, y, w, h, 18, 18);
        g2.setFont(new Font("Consolas", Font.BOLD, 17));
        g2.setColor(Color.WHITE);
        g2.drawString("TACTICAL COMMAND", x + 16, y + 26);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(205, 225, 245));
        g2.drawString("Group " + state.selectedGroup + "  Order " + state.selectedOrder, x + 16, y + 52);
        g2.drawString("Ctrl+F3 close   Q cycle order   Shift+RMB issue   Ctrl+G group", x + 16, y + 74);
        g2.drawString("Ctrl+P tactical pause   Ctrl+T support   Ctrl+R activate", x + 16, y + 92);
        g2.drawString("Ctrl+O orientation   Ctrl+B bulkheads   Ctrl+X overdrive", x + 16, y + 110);
        g2.drawString("Ctrl+D point-defense   Ctrl+J doctrine   Ctrl+K group   Ctrl+S scuttle", x + 16, y + 128);
        drawFormationPreview(g2, x + 326, y + 42);
        if (ctx.player != null) {
            ShipState tactical = shipState(state, ctx.player);
            g2.drawString("Doctrine " + tactical.doctrine + "   PD " + tactical.pointDefensePriority, x + 16, y + 146);
            g2.drawString("Ammo " + tactical.ballisticAmmo + "/" + tactical.missileAmmo + "/" + tactical.mineAmmo
                    + "   Heat " + (int) Math.round(tactical.weaponHeat * 100.0) + "%   Scars " + tactical.persistentScars,
                    x + 16, y + 164);
            if (!ctx.player.turrets.isEmpty()) {
                g2.drawString(weaponRoleTooltip(ctx.player, ctx.player.turrets.get(0)), x + 16, y + 182);
            }
        }
        g2.setColor(new Color(255, 214, 132));
        g2.drawString("TIMELINE", x + 16, y + 202);
        int cy = y + 220;
        List<TimelineMarker> markers = state.timeline;
        for (int i = Math.max(0, markers.size() - 5); i < markers.size(); i++) {
            TimelineMarker marker = markers.get(i);
            g2.setColor(new Color(182, 204, 228));
            g2.drawString(String.format("%6.1fs  %s", marker.seconds(), marker.text()), x + 16, cy);
            cy += 18;
        }
    }

    public static void handleRamming(GameContext ctx) {
        if (ctx == null) return;
        List<Ship> ships = ctx.ships;
        for (int i = 0; i < ships.size(); i++) {
            Ship a = ships.get(i);
            if (!alive(a)) continue;
            for (int j = i + 1; j < ships.size(); j++) {
                Ship b = ships.get(j);
                if (!alive(b)) continue;
                double rr = a.radius + b.radius;
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double d2 = dx * dx + dy * dy;
                if (d2 >= rr * rr) continue;
                double d = Math.sqrt(Math.max(1e-6, d2));
                double nx = dx / d;
                double ny = dy / d;
                double overlap = rr - d;
                a.x -= nx * overlap * 0.5;
                a.y -= ny * overlap * 0.5;
                b.x += nx * overlap * 0.5;
                b.y += ny * overlap * 0.5;
                double relative = Math.hypot((a.vx - b.vx) / GameContext.DT, (a.vy - b.vy) / GameContext.DT);
                if (relative < 95.0 || (a.faction != null && a.faction.isFriendlyTo(b.faction))) continue;
                int damage = Math.max(1, (int) Math.round(relative / 88.0));
                a.takeDamage(Math.max(1, (int) Math.round(damage * ramResistance(b.role))), a.x, a.y, -nx, -ny);
                b.takeDamage(Math.max(1, (int) Math.round(damage * ramResistance(a.role))), b.x, b.y, nx, ny);
                shipState(state(ctx), a).persistentScars++;
                shipState(state(ctx), b).persistentScars++;
                marker(ctx, "RAM IMPACT: " + a.name + " / " + b.name);
            }
        }
    }

    private static void updateHazards(GameContext ctx, State state, Ship ship, ShipState tactical, double dt) {
        if (ship.totalFireIntensity() > 0.7) tactical.hazards.merge(Hazard.FIRE, ship.totalFireIntensity(), Math::max);
        double fire = tactical.hazards.getOrDefault(Hazard.FIRE, 0.0);
        if (fire > 0.8) tactical.hazards.merge(Hazard.DECOMPRESSION, fire * 0.22, Math::max);
        if (ship.propulsionRoomIntegrity() < 0.45) tactical.hazards.merge(Hazard.COOLANT_LEAK, 0.45, Math::max);
        if (ship.sensorRangeMultiplier() < 0.62) tactical.hazards.merge(Hazard.ELECTRICAL_ARC, 0.38, Math::max);
        if (fire > 1.15) tactical.hazards.merge(Hazard.AMMO_COOKOFF, 0.32, Math::max);
        if (ship.reactorBlackoutActive()) tactical.hazards.merge(Hazard.REACTOR_INSTABILITY, 0.72, Math::max);
        double sealMul = tactical.bulkheadsSealed ? 0.46 : 1.0;
        for (Map.Entry<Hazard, Double> entry : new ArrayList<>(tactical.hazards.entrySet())) {
            double intensity = Math.max(0.0, entry.getValue() - dt * 0.035 / sealMul);
            tactical.hazards.put(entry.getKey(), intensity);
            if (intensity <= 0.02) tactical.hazards.remove(entry.getKey());
        }
        double danger = tactical.hazards.values().stream().mapToDouble(Double::doubleValue).sum();
        if (danger > 2.8 && ctx.rng.nextDouble() < dt * 0.20) {
            ship.takePenetratingInternalDamage(Math.max(1, (int) Math.round(danger)), ship.x, ship.y, 0.0, 0.0);
            tactical.persistentScars++;
            marker(ctx, ship.name + " CASCADE FAILURE");
        }
    }

    private static void updateSupport(GameContext ctx, State state, Ship ship, ShipState tactical, double dt) {
        tactical.supportTimer = Math.max(0.0, tactical.supportTimer - dt);
        if (tactical.towTargetId > 0) {
            Ship target = findShip(ctx, tactical.towTargetId);
            if (!alive(target) || GameMath.dist2(ship.x, ship.y, target.x, target.y) > 620.0 * 620.0) tactical.towTargetId = -1;
            else {
                target.x += (ship.x - target.x) * dt * 0.48;
                target.y += (ship.y - target.y) * dt * 0.48;
                target.vx = ship.vx * 0.75;
                target.vy = ship.vy * 0.75;
            }
        }
        if (tactical.supportTimer > 0.0) return;
        tactical.supportTimer = 0.8;
        if (tactical.supportMode == SupportMode.REPAIR_DRONES || tactical.supportMode == SupportMode.SHIELD_TRANSFER) {
            Ship target = nearestFriendlyDamaged(ctx, ship, 420.0);
            if (target == null) return;
            if (tactical.supportMode == SupportMode.REPAIR_DRONES) target.healHull(1);
            else target.healShield(8.0);
        } else if (tactical.supportMode == SupportMode.MINE_CLEARER) {
            state.mines.removeIf(mine -> GameMath.dist2(ship.x, ship.y, mine.x, mine.y) <= 250.0 * 250.0);
        }
    }

    private static void updateDockingAssist(GameContext ctx, Ship ship, double dt) {
        if (ctx == null || ship == null || ship != ctx.player) return;
        Ship base = EconomySystem.getDockedFriendlyBase(ctx);
        if (base != null) return;
        Ship nearest = TeamSystem.getBaseForTeam(ctx, ship.faction);
        if (!alive(nearest)) return;
        double dx = nearest.x - ship.x;
        double dy = nearest.y - ship.y;
        double distance = Math.hypot(dx, dy);
        if (distance > nearest.radius + ship.radius + 150.0 || distance < 1e-6) return;
        double speed = Math.hypot(ship.vx, ship.vy) / Math.max(GameContext.DT, 1e-6);
        if (speed > 145.0) return;
        ship.vx += dx / distance * dt * 12.0;
        ship.vy += dy / distance * dt * 12.0;
    }

    private static void updateMines(GameContext ctx, State state, double dt) {
        for (int i = state.mines.size() - 1; i >= 0; i--) {
            Mine mine = state.mines.get(i);
            mine.ttl -= dt;
            if (mine.ttl <= 0.0) {
                state.mines.remove(i);
                continue;
            }
            for (Ship ship : ctx.ships) {
                if (!alive(ship) || ship.faction == null || mine.faction == null || ship.faction.isFriendlyTo(mine.faction)) continue;
                if (GameMath.dist2(ship.x, ship.y, mine.x, mine.y) > 92.0 * 92.0) continue;
                ship.takeDamage(14, mine.x, mine.y, 0.0, 0.0, Ship.InteriorHitProfile.MISSILE_BLAST);
                state.mines.remove(i);
                marker(ctx, "MINE DETONATION: " + ship.name);
                break;
            }
        }
    }

    private static void disruptTractors(GameContext ctx, double x, double y, double range) {
        State state = state(ctx);
        if (state == null) return;
        for (Map.Entry<Integer, ShipState> entry : state.ships.entrySet()) {
            Ship ship = findShip(ctx, entry.getKey());
            if (ship != null && GameMath.dist2(ship.x, ship.y, x, y) <= range * range) entry.getValue().towTargetId = -1;
        }
    }

    private static boolean collateralRiskNear(GameContext ctx, double x, double y, double range) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return false;
        for (Ship ship : ctx.ships) {
            if (!alive(ship) || ship.faction == null) continue;
            if (!ship.faction.isFriendlyTo(ctx.player.faction) && !ship.surrendered) continue;
            if (GameMath.dist2(ship.x, ship.y, x, y) <= range * range) return true;
        }
        return false;
    }

    private static void drawFormationPreview(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(108, 188, 238));
        for (int i = 0; i < 3; i++) {
            int px = x + i * 18;
            g2.drawLine(px, y + i * 10, px + 10, y + i * 10);
            g2.fillOval(px + 3, y + i * 10 - 3, 6, 6);
        }
    }

    private static void updateWeaponHeat(ShipState tactical, double dt) {
        tactical.weaponHeat = Math.max(0.0, tactical.weaponHeat - dt * 0.16);
    }

    private static void applyFleetCommand(GameContext ctx, Ship ship, Order order) {
        GameContext.FleetCommand command = switch (order) {
            case ESCORT, PROTECT -> GameContext.FleetCommand.ESCORT;
            case SCREEN, HOLD, CAPTURE_ZONE -> GameContext.FleetCommand.DEFEND;
            case FLANK, PURSUE, FOCUS_FIRE -> GameContext.FleetCommand.ATTACK;
            case RETREAT -> GameContext.FleetCommand.RETREAT;
            case REGROUP -> GameContext.FleetCommand.FORM_UP;
            case SALVAGE_UNDER_FIRE -> GameContext.FleetCommand.REPAIR;
        };
        ctx.command.shipFleetCommandOverrides.put(ship.id, command);
        ctx.command.shipFleetCommandOverrideTimers.put(ship.id, 12.0);
    }

    private static double commandDelaySeconds(Ship from, Ship to) {
        if (from == null || to == null) return 0.0;
        double distanceDelay = Math.hypot(from.x - to.x, from.y - to.y) / 1400.0;
        double commsDamage = 1.0 - from.commandLinkQuality();
        return MathUtil.clamp(0.15 + distanceDelay + commsDamage * 1.8, 0.1, 4.5);
    }

    private static String acknowledgmentText(GameContext ctx, Ship ship, Order order) {
        boolean jammed = ctx != null && ctx.command != null && ctx.command.scienceJamming;
        if (jammed && ctx.rng.nextDouble() < 0.45) return ship.name + " ACK GARBLED: ... " + order.name();
        return ship.name + " ACK: " + order.name();
    }

    private static double ramResistance(ShipRole role) {
        if (role == null) return 1.0;
        if (role == ShipRole.BULWARK_TITAN || role == ShipRole.SHIELD_BASTION_TITAN || role == ShipRole.MOTHERSHIP) return 0.42;
        if (role.isTitanOrMothership()) return 0.62;
        if (role.isCapitalCombatant()) return 0.78;
        return 1.0;
    }

    private static boolean isBroadsideHull(Ship ship) {
        if (ship == null) return false;
        return ship.role == ShipRole.BULWARK_TITAN
                || ship.role == ShipRole.SHIELD_BASTION_TITAN;
    }

    private static Ship nearestDisabledOrSurrendered(GameContext ctx, double x, double y, double range) {
        Ship best = null;
        double bestD2 = range * range;
        for (Ship ship : ctx.ships) {
            if (!alive(ship) || (!ship.surrendered && !ship.isTemporarilyDisabled())) continue;
            double d2 = GameMath.dist2(x, y, ship.x, ship.y);
            if (d2 < bestD2) {
                best = ship;
                bestD2 = d2;
            }
        }
        return best;
    }

    private static Ship nearestFriendlyDamaged(GameContext ctx, Ship source, double range) {
        Ship best = null;
        double bestD2 = range * range;
        for (Ship ship : ctx.ships) {
            if (!alive(ship) || ship == source || ship.faction == null || source.faction == null || !ship.faction.isFriendlyTo(source.faction)) continue;
            if (ship.hp >= ship.hpMax && ship.shield >= ship.shieldMax) continue;
            double d2 = GameMath.dist2(source.x, source.y, ship.x, ship.y);
            if (d2 < bestD2) {
                best = ship;
                bestD2 = d2;
            }
        }
        return best;
    }

    private static Ship findShip(GameContext ctx, int id) {
        if (ctx == null) return null;
        for (Ship ship : ctx.ships) if (ship != null && ship.id == id) return ship;
        return null;
    }

    private static boolean alive(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }

    private static ShipState shipState(State state, Ship ship) {
        return state.ships.computeIfAbsent(ship.id, ignored -> new ShipState());
    }

    private static State state(GameContext ctx) {
        if (ctx == null) return null;
        return STATES.computeIfAbsent(ctx, ignored -> new State());
    }

    private static void marker(GameContext ctx, String text) {
        State state = state(ctx);
        if (state == null) return;
        state.timeline.add(new TimelineMarker(ctx.battleElapsed, text));
        while (state.timeline.size() > 80) state.timeline.remove(0);
        EventSystem.showBanner(ctx, text, 1.1);
    }
}
