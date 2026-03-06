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
            // CIWS is point-defense only: pellets should only interact with missiles.
            if (p instanceof CIWSPellet) continue;
            boolean waveShot = p instanceof WaveMotionShot;
            VFX.ImpactStyle impactStyle = impactStyleFor(p);
            for (Ship s : ships) {
                if (!s.alive) continue;
                if (s.faction.isFriendlyTo(p.faction)) continue;

                double shipHitRadius = HullGeometry.broadPhaseRadius(s);
                if (!circleHit(p.x, p.y, p.radius, s.x, s.y, shipHitRadius)) continue;
                if (!HullGeometry.projectileIntersectsShip(p, s)) continue;
                if (waveShot) {
                    WaveMotionShot ws = (WaveMotionShot) p;
                    if (!ws.canDamage(s)) continue;
                    ws.markDamaged(s);

                    double dirX = p.vx;
                    double dirY = p.vy;
                    double len = Math.sqrt(dirX * dirX + dirY * dirY);
                    if (len > 1e-9) {
                        dirX /= len;
                        dirY /= len;
                    } else {
                        dirX = Math.cos(ws.angle);
                        dirY = Math.sin(ws.angle);
                    }

                    double shieldBefore = s.shield;
                    int hpBefore = s.hp;
                    s.takeDamage(p.damage, p.x, p.y, p.vx, p.vy);
                    boolean shieldHit = s.shield < shieldBefore - 1e-6;
                    boolean hullHit = s.hp < hpBefore;
                    if (shieldHit) {
                        VFX.spawnShieldImpact(p.x, p.y, dirX, dirY, Math.max(2, p.damage), impactStyle);
                    }
                    if (hullHit) {
                        VFX.spawnHullImpact(p.x, p.y, dirX, dirY, Math.max(2, p.damage), impactStyle);
                    }
                    if (!shieldHit && !hullHit) {
                        VFX.spawnImpactSparks(p.x, p.y, dirX, dirY, Math.max(2, p.damage));
                    }

                    ScreenShake.kick(2.2);
                    ws.consumeHit();
                    if (!p.alive) break;
                    continue;
                }

                double dirX = p.vx;
                double dirY = p.vy;
                double len = Math.sqrt(dirX * dirX + dirY * dirY);
                if (len > 1e-9) {
                    dirX /= len;
                    dirY /= len;
                } else {
                    dirX = s.x - p.x;
                    dirY = s.y - p.y;
                    double dlen = Math.sqrt(dirX * dirX + dirY * dirY);
                    if (dlen > 1e-9) {
                        dirX /= dlen;
                        dirY /= dlen;
                    } else {
                        dirX = 1.0;
                        dirY = 0.0;
                    }
                }

                double shieldBefore = s.shield;
                int hpBefore = s.hp;

                // Small screen shake for heavier hits
                if (p instanceof Missile) ScreenShake.kick(3.5);
                else if (p.damage >= 3) ScreenShake.kick(1.8);

                s.takeDamage(p.damage, p.x, p.y, p.vx, p.vy);
                if (p instanceof Missile m) {
                    applyMissileBlast(m, s, ships);
                }

                boolean shieldHit = s.shield < shieldBefore - 1e-6;
                boolean hullHit = s.hp < hpBefore;
                if (shieldHit) {
                    VFX.spawnShieldImpact(p.x, p.y, dirX, dirY, Math.max(1, p.damage), impactStyle);
                }
                if (hullHit) {
                    VFX.spawnHullImpact(p.x, p.y, dirX, dirY, Math.max(1, p.damage), impactStyle);
                }
                if (!shieldHit && !hullHit) {
                    VFX.spawnImpactSparks(p.x, p.y, dirX, dirY, Math.max(1, p.damage));
                }

                p.alive = false;
                break;
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
                        VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, 2, VFX.ImpactStyle.KINETIC);
                        Explosion.spawnShieldHit(m.x, m.y);
                    } else {
                        VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, 1, VFX.ImpactStyle.KINETIC);
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

    /** Projectiles damage asteroids; most shots are consumed on impact. */
    public static void handleProjectilesVsAsteroids(List<Projectile> projectiles, List<Asteroid> asteroids) {
        if (projectiles == null || asteroids == null || asteroids.isEmpty()) return;

        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (p instanceof CIWSPellet) continue;
            WaveMotionShot ws = (p instanceof WaveMotionShot) ? (WaveMotionShot) p : null;
            for (int ai = asteroids.size() - 1; ai >= 0; ai--) {
                Asteroid a = asteroids.get(ai);
                if (a == null) {
                    asteroids.remove(ai);
                    continue;
                }
                if (!circleHit(p.x, p.y, p.radius, a.x, a.y, a.collisionRadius())) continue;

                if (ws != null) {
                    if (!ws.canDamage(a)) continue;
                    ws.markDamaged(a);
                }

                int impactDamage = Math.max(1, p.damage);
                boolean destroyed = a.applyWeaponDamage(impactDamage);

                VFX.spawnHullImpact(
                        p.x, p.y, p.vx, p.vy,
                        Math.max(1, impactDamage),
                        impactStyleFor(p)
                );
                Explosion.spawnShieldHit(p.x, p.y);

                if (destroyed) {
                    Explosion.spawnShieldHit(a.x, a.y);
                    ScreenShake.kick(Math.min(5.0, 1.2 + a.collisionRadius() * 0.06));
                    asteroids.remove(ai);
                }

                if (ws != null) ws.consumeHit();
                else p.alive = false;
                break;
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
            double maxD = rr + HullGeometry.broadPhaseRadius(s);
            if (d > maxD) continue;

            double falloff = 1.0 - (d / Math.max(1.0, maxD));
            int splash = Math.max(1, (int) Math.round(baseSplash * (0.35 + 0.65 * falloff)));
            s.takeDamage(splash, m.x, m.y);
        }

        VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, Math.max(2, m.damage), VFX.ImpactStyle.EXPLOSIVE);
        Explosion.spawnShieldHit(m.x, m.y);
    }

    private static VFX.ImpactStyle impactStyleFor(Projectile p) {
        if (p instanceof Missile) return VFX.ImpactStyle.EXPLOSIVE;
        if (p instanceof WaveMotionShot) return VFX.ImpactStyle.BEAM;
        if (p instanceof EnergyBolt bolt) {
            return bolt.isBeamBolt() ? VFX.ImpactStyle.BEAM : VFX.ImpactStyle.ENERGY;
        }
        if (p instanceof CIWSPellet) return VFX.ImpactStyle.KINETIC;
        return VFX.ImpactStyle.KINETIC;
    }
}
