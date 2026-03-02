import java.util.List;

public class CollisionSystem {

    private CollisionSystem() {}

    public static boolean circleHit(double ax, double ay, double ar, double bx, double by, double br) {
        double dx = ax - bx;
        double dy = ay - by;
        double r = ar + br;
        return (dx * dx + dy * dy) <= (r * r);
    }

    /** Projectiles hit ships of the opposing faction. */
    public static void handleProjectilesVsShips(List<Projectile> projectiles, List<Ship> ships) {
        if (projectiles == null || ships == null) return;
        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            boolean waveShot = p instanceof WaveMotionShot;
            for (Ship s : ships) {
                if (!s.alive) continue;
                if (s.faction.isFriendlyTo(p.faction)) continue;

                if (circleHit(p.x, p.y, p.radius, s.x, s.y, s.radius)) {
                    if (waveShot) {
                        WaveMotionShot ws = (WaveMotionShot) p;
                        if (!ws.canDamage(s)) continue;
                        ws.markDamaged(s);
                        s.takeDamage(p.damage, p.x, p.y);
                        VFX.spawnImpactSparks(p.x, p.y, p.vx, p.vy, Math.max(3, p.damage));
                        ScreenShake.kick(2.2);
                        ws.consumeHit();
                        if (!p.alive) break;
                        continue;
                    }

                    // Cosmetic impact effects (no gameplay impact)
                    double dirX = p.vx;
                    double dirY = p.vy;
                    double len = Math.sqrt(dirX * dirX + dirY * dirY);
                    if (len > 1e-9) { dirX /= len; dirY /= len; }
                    VFX.spawnImpactSparks(p.x, p.y, dirX, dirY, Math.max(1, p.damage));

                    // Small screen shake for heavier hits
                    if (p instanceof Missile) ScreenShake.kick(3.5);
                    else if (p.damage >= 3) ScreenShake.kick(1.8);

                    s.takeDamage(p.damage, p.x, p.y);
                    if (p instanceof Missile m) {
                        applyMissileBlast(m, s, ships);
                    }
                    p.alive = false;
                    break;
                }
            }
        }
    }

    /**
     * Projectiles can also hit other projectiles (currently: CIWS pellets can hit missiles).
     *
     * This is kept lightweight by only checking pellet-vs-missile pairs.
     */
    public static void handleProjectilesVsProjectiles(List<Projectile> projectiles) {
        if (projectiles == null || projectiles.isEmpty()) return;

        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (!(p instanceof CIWSPellet pellet)) continue;

            for (Projectile q : projectiles) {
                if (!q.alive) continue;
                if (!(q instanceof Missile m)) continue;
                if (pellet.faction.isFriendlyTo(m.faction)) continue;

                if (circleHit(pellet.x, pellet.y, pellet.radius, m.x, m.y, m.radius)) {
                    pellet.alive = false;
                    boolean killed = m.applyInterceptHit(1);
                    if (killed) {
                        VFX.spawnImpactSparks(m.x, m.y, 0.0, 0.0, 2);
                        Explosion.spawnShieldHit(m.x, m.y);
                    } else {
                        VFX.spawnImpactSparks(m.x, m.y, 0.0, 0.0, 1);
                    }
                    break;
                }
            }
        }
    }

    /** Solid asteroids push ships out (no damage). */
    public static void handleShipsVsAsteroids(List<Ship> ships, List<Asteroid> asteroids) {
        if (ships == null || asteroids == null || asteroids.isEmpty()) return;

        for (Ship s : ships) {
            if (s == null || !s.alive) continue;
            for (Asteroid a : asteroids) {
                double dx = s.x - a.x;
                double dy = s.y - a.y;
                double rr = s.radius + a.collisionRadius();
                double d2 = dx * dx + dy * dy;
                if (d2 >= rr * rr) continue;

                double d = Math.sqrt(Math.max(1e-9, d2));
                double push = rr - d;

                double nx = dx / d;
                double ny = dy / d;

                s.x += nx * push;
                s.y += ny * push;

                // damp motion so they don't jitter through
                s.vx *= 0.65;
                s.vy *= 0.65;
            }
        }
    }

    /** Projectiles die on asteroids. */
    public static void handleProjectilesVsAsteroids(List<Projectile> projectiles, List<Asteroid> asteroids) {
        if (projectiles == null || asteroids == null || asteroids.isEmpty()) return;

        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (p instanceof WaveMotionShot) continue;
            for (Asteroid a : asteroids) {
                if (circleHit(p.x, p.y, p.radius, a.x, a.y, a.collisionRadius())) {
                    p.alive = false;
                    VFX.spawnImpactSparks(p.x, p.y, p.vx, p.vy, 1);
                    Explosion.spawnShieldHit(p.x, p.y);
                    break;
                }
            }
        }
    }

    public static void cleanupProjectiles(List<Projectile> projectiles) {
        if (projectiles == null) return;
        projectiles.removeIf(p -> !p.alive);
    }

    private static void applyMissileBlast(Missile m, Ship directHit, List<Ship> ships) {
        if (m == null || ships == null || ships.isEmpty()) return;
        double rr = Math.max(20.0, m.blastRadius);
        double baseSplash = Math.max(1.0, m.damage * m.splashDamageMul);

        for (Ship s : ships) {
            if (s == null || !s.alive) continue;
            if (s == directHit) continue;
            if (s.faction.isFriendlyTo(m.faction)) continue;

            double d = Math.hypot(s.x - m.x, s.y - m.y);
            double maxD = rr + s.radius;
            if (d > maxD) continue;

            double falloff = 1.0 - (d / Math.max(1.0, maxD));
            int splash = Math.max(1, (int) Math.round(baseSplash * (0.35 + 0.65 * falloff)));
            s.takeDamage(splash, m.x, m.y);
        }

        VFX.spawnImpactSparks(m.x, m.y, 0.0, 0.0, Math.max(2, m.damage));
        Explosion.spawnShieldHit(m.x, m.y);
    }
}
