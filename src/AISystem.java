import java.util.List;

public final class AISystem {
    private AISystem(){}

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;
        if (!DevTools.isAIEnabled()) return;

        // spawn waves (unchanged behavior)
        ctx.enemyWaveTimer -= dt;
        if (ctx.enemyWaveTimer <= 0) {
            ctx.enemyWaveTimer = 14.0 + ctx.rng.nextDouble() * 10.0;
            if (ctx.config.mode != GameMode.SANDBOX && ctx.config.mode != GameMode.FOUR_TEAM_DOMINATION) {
                SpawnSystem.spawnEnemyGroup(
                        ctx,
                        ctx.player.x + 900 + ctx.rng.nextDouble() * 500,
                        ctx.player.y - 600 + ctx.rng.nextDouble() * 400
                );
                SpawnSystem.spawnAllyGroup(
                        ctx,
                        ctx.player.x - 900 - ctx.rng.nextDouble() * 500,
                        ctx.player.y + 600 + ctx.rng.nextDouble() * 400
                );
            }
        }

        // per ship AI
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying) continue;
            if (s == ctx.player) continue;
            if (s.role == ShipRole.BASE) {
                // Bases stay put but can still defend themselves.
                s.vx = 0;
                s.vy = 0;
                Ship target = TargetingSystem.getPreferredEnemyTarget(ctx, s);
                if (target != null && target.alive && !target.dying) {
                    fireIfAble(ctx, s, target, dt, Math.hypot(target.x - s.x, target.y - s.y));
                }
                s.tryCIWS(dt, ctx.projectiles);
                continue;
            }
            if (s.role == ShipRole.MINER) {
                // Mining movement is handled in EconomySystem. Miners only do opportunistic self-defense here.
                Ship target = TargetingSystem.getPreferredEnemyTarget(ctx, s);
                if (target != null && target.alive && !target.dying) {
                    double d = Math.hypot(target.x - s.x, target.y - s.y);
                    if (d <= 280) {
                        fireIfAble(ctx, s, target, dt, d);
                    }
                }
                s.tryCIWS(dt, ctx.projectiles);
                continue;
            }

            Ship target = TargetingSystem.getPreferredEnemyTarget(ctx, s);
            if (target != null && target.alive && !target.dying) {
                fight(ctx, s, target, dt);
            } else {
                wander(ctx, s, dt);
            }
        }

        // keep bounds
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying) continue;
            s.x = GameMath.clamp(s.x, 0, ctx.WORLD_W);
            s.y = GameMath.clamp(s.y, 0, ctx.WORLD_H);
        }
    }

    private static void fight(GameContext ctx, Ship s, Ship target, double dt) {
        // Determine preferred range by role
        double range = preferredRange(s);
        double d2 = dist2(s.x, s.y, target.x, target.y);
        double d = Math.sqrt(d2);

        // Movement:
        // - If far: close in
        // - If close: orbit/strafe to avoid stacking
        double speed = Math.max(55.0, s.desiredSpeed);

        double orbitDir = ((s.hashCode() & 1) == 0) ? 1.0 : -1.0;

        if (d > range * 1.25) {
            moveToward(s, target.x, target.y, speed, dt);
        } else {
            // orbit at preferred range
            orbit(s, target.x, target.y, range, speed * 0.95, dt, orbitDir);
        }

        // Fire control
        fireIfAble(ctx, s, target, dt, d);
        // CIWS always tries to protect itself
        s.tryCIWS(dt, ctx.projectiles);
    }

    private static void wander(GameContext ctx, Ship s, double dt) {
        // Simple wander: drift toward the player's general area, but loosely.
        double tx = ctx.player.x + (ctx.rng.nextDouble() - 0.5) * 800.0;
        double ty = ctx.player.y + (ctx.rng.nextDouble() - 0.5) * 800.0;
        moveToward(s, tx, ty, Math.max(40.0, s.desiredSpeed * 0.7), dt);

        s.tryCIWS(dt, ctx.projectiles);
    }

    private static void fireIfAble(GameContext ctx, Ship s, Ship target, double dt, double dist) {
        if (ctx.projectiles == null) return;

        for (Turret t : s.turrets) {
            if (t == null) continue;

            // Rough engagement gating by weapon kind
            double maxRange;
            if (s.role == ShipRole.BASE) {
                maxRange = (t.kind == Turret.Kind.MISSILE) ? 1400.0 : 900.0;
            } else {
                maxRange = (t.kind == Turret.Kind.MISSILE) ? 900.0 : 520.0;
            }
            if (dist > maxRange) continue;

            // Aim with lead for guns; direct for missiles
            if (t.kind == Turret.Kind.GUN) {
                t.aimAtLead(dt, s, target, t.bulletSpeed);
            } else {
                t.aimAt(dt, s, target);
            }

            // Only fire if ready and roughly aligned
            if (!t.canFire()) continue;

            double wx = t.worldX(s);
            double wy = t.worldY(s);
            double desired = Math.atan2(target.y - wy, target.x - wx);
            double delta = Math.abs(MathUtil.normalizeAngle(desired - t.angle));

            // Allow looser alignment for missiles
            double tol = (t.kind == Turret.Kind.MISSILE) ? Math.toRadians(28) : Math.toRadians(14);
            if (delta > tol) continue;

            Projectile p = t.fire(s, (t.kind == Turret.Kind.MISSILE ? target : null), dt);
            if (p != null) ctx.projectiles.add(p);
        }
    }

    private static double preferredRange(Ship s) {
        if (s == null) return 380;
        // Keep roles feeling different
        return switch (s.role) {
            case FIGHTER, DRONE, PD_CRAFT -> 220;
            case PICKET, PATROL -> 320;
            case FRIGATE, CIWS_CORVETTE -> 360;
            case MISSILE_BOAT -> 620;
            case LIGHT_CRUISER -> 420;
            case MEDIUM_CRUISER, CRUISER -> 460;
            case BATTLECRUISER -> 500;
            case BATTLESHIP -> 560;
            case DREADNOUGHT -> 610;
            case CARRIER, DRONE_CARRIER, TRANSPORT -> 720;
            case STEALTH_SHIP -> 520;
            default -> 380; // fallback for any roles you add later
        };

    }

    // --- helpers (dt-safe) ---

    private static void setVelPerSec(Ship s, double vxPerSec, double vyPerSec, double dt) {
        if (dt <= 0) { s.vx = 0; s.vy = 0; return; }
        s.vx = vxPerSec * dt;
        s.vy = vyPerSec * dt;
    }

    private static void moveToward(Ship s, double tx, double ty, double speedPerSec, double dt) {
        double dx = tx - s.x;
        double dy = ty - s.y;
        double len = Math.sqrt(dx*dx + dy*dy) + 1e-9;
        double vx = (dx/len) * speedPerSec;
        double vy = (dy/len) * speedPerSec;
        setVelPerSec(s, vx, vy, dt);
        s.angle = Math.atan2(vy, vx);
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
        s.angle = Math.atan2(vy, vx);
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return dx*dx + dy*dy;
    }
}
