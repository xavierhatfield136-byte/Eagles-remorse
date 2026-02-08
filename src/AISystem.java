import java.util.Map;
import java.util.WeakHashMap;

/**
 * AI system (split-architecture friendly).
 *
 * IMPORTANT: Ship.vx/vy are per-tick deltas (already scaled by dt),
 * so this system must set vx/vy using (speed * dt), NOT per-second.
 */
public final class AISystem {
    private AISystem() {}

    private static final class WanderState {
        double timer = 0;
        double angle = Math.random() * Math.PI * 2.0;
    }

    private static final Map<Ship, WanderState> WANDER = new WeakHashMap<>();

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;

        // Spawn waves (keep your current behavior)
        ctx.enemyWaveTimer -= dt;
        if (ctx.enemyWaveTimer <= 0) {
            ctx.enemyWaveTimer = 14.0 + ctx.rng.nextDouble() * 10.0;
            if (ctx.config.mode != GameMode.SANDBOX) {
                SpawnSystem.spawnEnemyGroup(
                        ctx,
                        ctx.player.x + 900 + ctx.rng.nextDouble() * 500,
                        ctx.player.y - 600 + ctx.rng.nextDouble() * 400
                );
            }
        }

        // Per-ship AI
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive) continue;
            if (s.dying) continue;
            if (s == ctx.player) continue;

            Ship target = TargetingSystem.getPreferredEnemyTarget(ctx, s);

            if (target != null && target.alive && !target.dying) {
                fight(ctx, s, target, dt);
                fireAt(ctx, s, target, dt);
            } else {
                wander(ctx, s, dt);
            }

            keepInBounds(ctx, s);
        }
    }

    // -----------------------------
    // Movement
    // -----------------------------

    private static void fight(GameContext ctx, Ship s, Ship target, double dt) {
        double dx = target.x - s.x;
        double dy = target.y - s.y;
        double d2 = dx * dx + dy * dy;
        double d = Math.sqrt(d2) + 1e-9;

        // Role-based preferred range (units)
        double preferred = preferredRange(s);
        double band = Math.max(60.0, preferred * 0.18);

        // Normalize direction to target
        double ux = dx / d;
        double uy = dy / d;

        // Basic approach/retreat
        double toward = 0.0;
        if (d > preferred + band) toward = 1.0;
        else if (d < preferred - band) toward = -1.0;

        // Add a gentle orbit component so ships don't stack into a single blob.
        // Orbit direction depends on faction to create swirl variety.
        double orbitSign = (s.faction == Faction.ENEMY) ? 1.0 : -1.0;
        double ox = -uy * orbitSign;
        double oy =  ux * orbitSign;

        // Orbit strength: stronger when near preferred band.
        double near = 1.0 - MathUtil.clamp(Math.abs(d - preferred) / (preferred + 1e-9), 0.0, 1.0);
        double orbitStrength = 0.35 + 0.55 * near;

        // Combine vectors
        double vx = ux * toward + ox * orbitStrength;
        double vy = uy * toward + oy * orbitStrength;

        // If we are far, reduce orbit and go more direct.
        if (d > preferred * 1.8) {
            vx = ux;
            vy = uy;
        }

        // Set per-tick velocity (NOT accumulation)
        double sp = moveSpeed(s, d, preferred);
        s.vx = vx * sp * dt;
        s.vy = vy * sp * dt;

        // Point the hull roughly toward the target (visual)
        double desiredAngle = Math.atan2(dy, dx);
        s.angle = MathUtil.normalizeAngle(lerpAngle(s.angle, desiredAngle, MathUtil.clamp(dt * 6.0, 0.0, 1.0)));
    }

    private static void wander(GameContext ctx, Ship s, double dt) {
        WanderState st = WANDER.computeIfAbsent(s, k -> new WanderState());
        st.timer -= dt;
        if (st.timer <= 0) {
            st.timer = 1.2 + ctx.rng.nextDouble() * 2.4;
            // Small random turn
            st.angle += (ctx.rng.nextDouble() - 0.5) * 1.4;
        }

        double sp = Math.max(40.0, s.desiredSpeed * 0.55);
        s.vx = Math.cos(st.angle) * sp * dt;
        s.vy = Math.sin(st.angle) * sp * dt;

        // Visual hull facing
        s.angle = MathUtil.normalizeAngle(lerpAngle(s.angle, st.angle, MathUtil.clamp(dt * 3.0, 0.0, 1.0)));
    }

    private static double preferredRange(Ship s) {
        // Conservative defaults if role not set
        ShipRole r = s.role;

        if (r == ShipRole.MISSILE_BOAT) return 720;
        if (r == ShipRole.CIWS_CORVETTE) return 360;
        if (r == ShipRole.PICKET) return 520;
        if (r == ShipRole.PATROL) return 520;
        if (r == ShipRole.FRIGATE) return 540;
        if (r == ShipRole.LIGHT_CRUISER) return 620;
        if (r == ShipRole.MEDIUM_CRUISER) return 680;
        if (r == ShipRole.CRUISER) return 720;
        if (r == ShipRole.BATTLECRUISER) return 780;
        if (r == ShipRole.BATTLESHIP) return 820;
        if (r == ShipRole.DREADNOUGHT) return 900;
        if (r == ShipRole.CARRIER || r == ShipRole.DRONE_CARRIER) return 980;
        if (r == ShipRole.TRANSPORT) return 980;

        return 600;
    }

    private static double moveSpeed(Ship s, double d, double preferred) {
        // Slightly speed up when far away to close distance faster.
        double base = Math.max(60.0, s.desiredSpeed);
        if (d > preferred * 1.8) return base * 1.15;
        return base;
    }

    private static void keepInBounds(GameContext ctx, Ship s) {
        s.x = GameMath.clamp(s.x, 0, ctx.WORLD_W);
        s.y = GameMath.clamp(s.y, 0, ctx.WORLD_H);
    }

    // -----------------------------
    // Firing / fire control
    // -----------------------------

    private static void fireAt(GameContext ctx, Ship s, Ship target, double dt) {
        if (s.turrets == null || s.turrets.isEmpty()) return;

        double d2 = MathUtil.dist2(s.x, s.y, target.x, target.y);

        // Fire primary guns when in range.
        for (Turret t : s.turrets) {
            if (t == null) continue;

            if (t.kind == Turret.Kind.GUN) {
                double maxRange = t.bulletSpeed * dt * t.bulletLife * 0.92;
                if (d2 > maxRange * maxRange) continue;

                // Lead aim for guns
                t.aimAtLead(dt, s, target, t.bulletSpeed);

                // Only shoot if roughly aligned
                if (!t.canFire()) continue;
                if (angleOff(t.angle, Math.atan2(target.y - t.worldY(s), target.x - t.worldX(s))) > Math.toRadians(18)) continue;

                Projectile p = t.fire(s, null, dt);
                if (p != null) ctx.projectiles.add(p);
            } else { // MISSILE
                double maxRange = t.missileSpeed * dt * t.missileLife * 0.92;
                if (d2 > maxRange * maxRange) continue;

                t.aimAt(dt, s, target);

                if (!t.canFire()) continue;
                if (angleOff(t.angle, Math.atan2(target.y - t.worldY(s), target.x - t.worldX(s))) > Math.toRadians(26)) continue;

                Projectile p = t.fire(s, target, dt);
                if (p != null) ctx.projectiles.add(p);
            }
        }
    }

    private static double angleOff(double a, double b) {
        return Math.abs(MathUtil.normalizeAngle(b - a));
    }

    private static double lerpAngle(double a, double b, double t) {
        double d = MathUtil.normalizeAngle(b - a);
        return a + d * t;
    }
}
