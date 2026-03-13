import java.util.List;

public class CollisionSystem {
    private static final double DAMAGE_VFX_MAX_DIST_FROM_PLAYER = 1150.0;
    private static final int MAX_DAMAGE_EVENT_LOG = 2048;

    private CollisionSystem() {}

    public static boolean circleHit(double ax, double ay, double ar, double bx, double by, double br) {
        double dx = ax - bx;
        double dy = ay - by;
        double r = ar + br;
        return (dx * dx + dy * dy) <= (r * r);
    }

    /** Projectiles hit ships of the opposing faction. */
    public static void handleProjectilesVsShips(GameContext ctx, List<Projectile> projectiles, List<Ship> ships) {
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
                    markPlayerHitContribution(ctx, p, s);

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
                    logDamageEvent(ctx, "projectile:" + System.identityHashCode(p), p.damage, impactStyle, s, p.x, p.y);
                    boolean shieldHit = s.shield < shieldBefore - 1e-6;
                    boolean hullHit = s.hp < hpBefore;
                    boolean showImpactVfx = shouldRenderDamageVfx(ctx, s, p.x, p.y);
                    if (shieldHit) {
                        if (showImpactVfx) {
                            VFX.spawnShieldImpact(p.x, p.y, dirX, dirY, Math.max(2, p.damage), impactStyle);
                        }
                        AudioSystem.onShieldImpact(ctx, impactStyle, p.x, p.y);
                    }
                    if (hullHit) {
                        if (showImpactVfx) {
                            VFX.spawnHullImpact(p.x, p.y, dirX, dirY, Math.max(2, p.damage), impactStyle);
                        }
                        AudioSystem.onHullImpact(ctx, impactStyle, p.x, p.y);
                    }
                    if (!shieldHit && !hullHit) {
                        if (showImpactVfx) {
                            VFX.spawnImpactSparks(p.x, p.y, dirX, dirY, Math.max(2, p.damage));
                        }
                        // Preserve audible feedback for glancing/mitigated hull contacts.
                        AudioSystem.onHullImpact(ctx, impactStyle, p.x, p.y);
                    }

                    if (showImpactVfx) ScreenShake.kick(2.2);
                    ws.consumeHit();
                    if (!p.alive) break;
                    continue;
                }

                markPlayerHitContribution(ctx, p, s);
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
                boolean showImpactVfx = shouldRenderDamageVfx(ctx, s, p.x, p.y);

                // Small screen shake for heavier hits
                if (showImpactVfx) {
                    if (p instanceof Missile) ScreenShake.kick(3.5);
                    else if (p.damage >= 3) ScreenShake.kick(1.8);
                }

                s.takeDamage(p.damage, p.x, p.y, p.vx, p.vy);
                logDamageEvent(ctx, "projectile:" + System.identityHashCode(p), p.damage, impactStyle, s, p.x, p.y);
                if (p instanceof Missile m) {
                    applyMissileBlast(ctx, m, s, ships);
                }

                boolean shieldHit = s.shield < shieldBefore - 1e-6;
                boolean hullHit = s.hp < hpBefore;
                if (shieldHit) {
                    if (showImpactVfx) {
                        VFX.spawnShieldImpact(p.x, p.y, dirX, dirY, Math.max(1, p.damage), impactStyle);
                    }
                    AudioSystem.onShieldImpact(ctx, impactStyle, p.x, p.y);
                }
                if (hullHit) {
                    if (showImpactVfx) {
                        VFX.spawnHullImpact(p.x, p.y, dirX, dirY, Math.max(1, p.damage), impactStyle);
                    }
                    AudioSystem.onHullImpact(ctx, impactStyle, p.x, p.y);
                }
                if (!shieldHit && !hullHit) {
                    if (showImpactVfx) {
                        VFX.spawnImpactSparks(p.x, p.y, dirX, dirY, Math.max(1, p.damage));
                    }
                    // Preserve audible feedback for glancing/mitigated hull contacts.
                    AudioSystem.onHullImpact(ctx, impactStyle, p.x, p.y);
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
    public static void handleProjectilesVsProjectiles(GameContext ctx, List<Projectile> projectiles) {
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
                        if (shouldRenderDamageVfx(ctx, null, m.x, m.y)) {
                            VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, 2, VFX.ImpactStyle.KINETIC);
                            Explosion.spawnShieldHit(m.x, m.y);
                        }
                        AudioSystem.onExplosion(ctx, m.x, m.y);
                    } else {
                        if (shouldRenderDamageVfx(ctx, null, m.x, m.y)) {
                            VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, 1, VFX.ImpactStyle.KINETIC);
                        }
                        AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.KINETIC, m.x, m.y);
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
    public static void handleProjectilesVsAsteroids(GameContext ctx, List<Projectile> projectiles, List<Asteroid> asteroids) {
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
                boolean showImpactVfx = shouldRenderDamageVfx(ctx, null, p.x, p.y);

                if (showImpactVfx) {
                    VFX.spawnHullImpact(
                            p.x, p.y, p.vx, p.vy,
                            Math.max(1, impactDamage),
                            impactStyleFor(p)
                    );
                    Explosion.spawnShieldHit(p.x, p.y);
                }
                AudioSystem.onHullImpact(ctx, impactStyleFor(p), p.x, p.y);

                if (destroyed) {
                    if (showImpactVfx) {
                        Explosion.spawnShieldHit(a.x, a.y);
                        ScreenShake.kick(Math.min(5.0, 1.2 + a.collisionRadius() * 0.06));
                    }
                    AudioSystem.onExplosion(ctx, a.x, a.y);
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

    private static void applyMissileBlast(GameContext ctx, Missile m, Ship directHit, List<Ship> ships) {
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
            markPlayerHitContribution(ctx, m, s);
            s.takeDamage(splash, m.x, m.y);
            logDamageEvent(ctx, "missile_splash:" + System.identityHashCode(m), splash, VFX.ImpactStyle.EXPLOSIVE, s, m.x, m.y);
        }

        if (shouldRenderDamageVfx(ctx, directHit, m.x, m.y)) {
            VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, Math.max(2, m.damage), VFX.ImpactStyle.EXPLOSIVE);
            Explosion.spawnShieldHit(m.x, m.y);
        }
        AudioSystem.onExplosion(ctx, m.x, m.y);
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

    private static boolean shouldRenderDamageVfx(GameContext ctx, Ship victim, double x, double y) {
        if (ctx == null || ctx.player == null) return true;
        if (victim == ctx.player) return true;
        if (victim != null && victim.faction != null && ctx.player.faction != null
                && victim.faction.isFriendlyTo(ctx.player.faction)) {
            return true;
        }
        return GameMath.dist2(x, y, ctx.player.x, ctx.player.y)
                <= DAMAGE_VFX_MAX_DIST_FROM_PLAYER * DAMAGE_VFX_MAX_DIST_FROM_PLAYER;
    }

    private static void markPlayerHitContribution(GameContext ctx, Projectile projectile, Ship target) {
        if (ctx == null || ctx.player == null) return;
        if (projectile == null || target == null) return;
        if (!target.alive || target.dying) return;
        if (!TeamSystem.isHostileToPlayer(ctx, target.faction)) return;
        if (!isProjectileFromPlayer(ctx, projectile)) return;
        target.playerTaggedForKillCredit = true;
    }

    private static boolean isProjectileFromPlayer(GameContext ctx, Projectile projectile) {
        if (ctx == null || ctx.player == null || projectile == null) return false;
        return projectile.sourceShipId == ctx.player.id;
    }

    private static void logDamageEvent(GameContext ctx,
                                       String sourceId,
                                       double energy,
                                       VFX.ImpactStyle style,
                                       Ship target,
                                       double worldX,
                                       double worldY) {
        if (ctx == null || target == null) return;
        HullGeometry.ImpactSample impact = HullGeometry.sampleImpact(target, worldX, worldY, true);
        double localX = (impact == null) ? Double.NaN : impact.localX;
        double localY = (impact == null) ? Double.NaN : impact.localY;
        String damageType = (style == null) ? "kinetic" : style.name().toLowerCase();
        RoomDamageResult result = target.lastRoomDamageResult();
        DamageEvent event = new DamageEvent(
                sourceId,
                target.id,
                worldX,
                worldY,
                localX,
                localY,
                damageType,
                energy,
                System.nanoTime(),
                (result == null) ? RoomDamageResult.NONE : result
        );
        if (ctx.damageEvents.size() >= MAX_DAMAGE_EVENT_LOG) {
            ctx.damageEvents.remove(0);
        }
        ctx.damageEvents.add(event);
    }
}
