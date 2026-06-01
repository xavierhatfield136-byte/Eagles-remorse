import java.util.List;

public class CollisionSystem {
    private static final double DAMAGE_VFX_MAX_DIST_FROM_PLAYER = 1150.0;
    private static final int MAX_DAMAGE_EVENT_LOG = 2048;
    private static final double SHIELD_SHELL_OFFSET_SCALE = 0.62;
    private static final double DESTABILIZER_PULSE_EDGE_FALLOFF = 0.42;
    private static final double DESTABILIZER_PULSE_CORE_RADIUS_MIN = 84.0;
    private static final double DESTABILIZER_PULSE_DIRECT_HIT_BONUS = 0.55;
    private static final double DESTABILIZER_PULSE_SHIELD_EDGE_SCALE = 0.58;
    private static final double DESTABILIZER_PULSE_SHIELD_CORE_BONUS = 0.75;
    private static final double DESTABILIZER_PULSE_DISABLE_FLOOR_SECONDS = 0.55;
    private static final double DESTABILIZER_PULSE_DISABLE_CORE_SECONDS = 2.4;
    private static final double SUPERWEAPON_RING_DISABLE_SECONDS = 5.0;
    private static final double SUPERWEAPON_RING_SHIELD_DRAIN_FRACTION = 0.25;
    private static final double RED_STASIS_FIELD_SECONDS = 30.0;
    private static final double RED_STASIS_FIELD_REFRESH_SECONDS = 0.35;
    private static final double RED_STASIS_FIELD_SHIELD_LOCK_SECONDS = 0.85;

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
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            // Team C point-defense lasers remain projectile-only; CIWS pellets can also hit small craft.
            if (p instanceof PointDefenseLaser) continue;
            if (p instanceof PhaserBeam beam) {
                handlePhaserBeamVsShips(ctx, beam, ships);
                continue;
            }
            boolean superweaponShot = p instanceof SuperweaponShot;
            boolean disruptorSlug = p instanceof DisruptorSlug;
            boolean destabilizerPulse = p instanceof DestabilizerPulse;
            VFX.ImpactStyle impactStyle = impactStyleFor(p);
            Ship shooter = resolveSourceShip(ctx, ships, p);
            Iterable<Ship> candidates = ships;
            if (ctx != null) {
                double queryRadius = p.radius + ctx.entityQuery.maxShipBroadPhaseRadius();
                ctx.entityQuery.collectAliveShipsNear(p.x, p.y, queryRadius, nearbyShips);
                candidates = nearbyShips;
            }
            for (Ship s : candidates) {
                if (!s.alive) continue;
                if (s.faction.isFriendlyTo(p.faction)) continue;
                if (!canProjectileDamageShip(ctx, shooter, p, s)) continue;

                Missile interceptorMissile = (p instanceof Missile missile
                        && missile.role == Turret.MissileRole.INTERCEPT)
                        ? missile
                        : null;
                boolean interceptorFuse = interceptorMissile != null
                        && s.isSmallCraft();
                double shipHitRadius = HullGeometry.broadPhaseRadius(s);
                if (interceptorFuse) {
                    shipHitRadius += Math.max(18.0, interceptorMissile.blastRadius * 0.32);
                }
                if (!circleHit(p.x, p.y, p.radius, s.x, s.y, shipHitRadius)) continue;
                // CIWS pellets use simple circle collision (performance optimization for high-volume fire)
                // Only apply expensive hull geometry check for larger/more important projectiles
                if (!(p instanceof CIWSPellet) && !interceptorFuse && !HullGeometry.projectileIntersectsShip(p, s)) continue;
                if (disruptorSlug) {
                    DisruptorSlug slug = (DisruptorSlug) p;
                    if (!slug.canAffect(s)) continue;
                }
                if (destabilizerPulse) {
                    applyDestabilizerBlast(ctx, (DestabilizerPulse) p, p.x, p.y, ships);
                    p.alive = false;
                    break;
                }
                if (superweaponShot) {
                    SuperweaponShot ws = (SuperweaponShot) p;
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

                    ImpactVisualPoints impactPoints = resolveImpactVisualPoints(s, p.x, p.y, p.vx, p.vy);
                    double shieldBefore = s.shield;
                    int hpBefore = s.hp;
                    int effectiveDamage = scaleDamage(ctx, p.getEffectiveDamage());
                    boolean redRailgunShot = isRedRailgunSuperweapon(shooter, p);
                    if (redRailgunShot) {
                        int shieldCrack = Math.max(0, (int) Math.round(effectiveDamage * 0.35));
                        if (shieldCrack > 0) {
                            s.drainShieldByAmount(shieldCrack, p.x, p.y, p.vx, p.vy);
                        }
                    }
                    s.takeDamage(
                            effectiveDamage,
                            p.x,
                            p.y,
                            p.vx,
                            p.vy,
                            interiorHitProfileForProjectile(shooter, p)
                    );
                    boolean shieldHit = s.shield < shieldBefore - 1e-6;
                    boolean hullHit = s.hp < hpBefore;
                    if (redRailgunShot && hullHit && s.alive && !s.dying && s.hp > 0) {
                        int penetratingDamage = Math.max(10, (int) Math.round(effectiveDamage * 0.72));
                        s.takePenetratingInternalDamage(penetratingDamage, p.x, p.y, p.vx, p.vy);
                    }
                    logDamageEvent(ctx, "projectile:" + System.identityHashCode(p), effectiveDamage, impactStyle, s, p.x, p.y);
                    double shieldX = impactPoints.shieldX();
                    double shieldY = impactPoints.shieldY();
                    double hullX = impactPoints.hullX();
                    double hullY = impactPoints.hullY();
                    boolean showImpactVfx = shouldRenderDamageVfx(ctx, s,
                            shieldHit && !hullHit ? shieldX : hullX,
                            shieldHit && !hullHit ? shieldY : hullY);
                    if (shieldHit) {
                        if (showImpactVfx) {
                            VFX.spawnShieldImpact(shieldX, shieldY, dirX, dirY, Math.max(2, p.damage), impactStyle);
                        }
                        AudioSystem.onShieldImpact(ctx, impactStyle, shieldX, shieldY);
                    }
                    if (hullHit) {
                        if (showImpactVfx) {
                            VFX.spawnHullImpact(hullX, hullY, dirX, dirY, Math.max(2, p.damage), impactStyle);
                        }
                        AudioSystem.onHullImpact(ctx, impactStyle, hullX, hullY);
                    }
                    if (!shieldHit && !hullHit) {
                        if (showImpactVfx) {
                            VFX.spawnImpactSparks(hullX, hullY, dirX, dirY, Math.max(2, p.damage));
                        }
                        // Preserve audible feedback for glancing/mitigated hull contacts.
                        AudioSystem.onHullImpact(ctx, impactStyle, hullX, hullY);
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

                ImpactVisualPoints impactPoints = resolveImpactVisualPoints(s, p.x, p.y, p.vx, p.vy);
                double shieldBefore = s.shield;
                int hpBefore = s.hp;
                double shieldX = impactPoints.shieldX();
                double shieldY = impactPoints.shieldY();
                double hullX = impactPoints.hullX();
                double hullY = impactPoints.hullY();
                boolean showImpactVfx = shouldRenderDamageVfx(ctx, s,
                        shieldBefore > 0.0 ? shieldX : hullX,
                        shieldBefore > 0.0 ? shieldY : hullY);

                if (disruptorSlug) {
                    DisruptorSlug slug = (DisruptorSlug) p;
                    slug.markAffected(s);
                    applyDisruptorBlast(ctx, slug, p.x, p.y, ships, s);
                    if (redKineticSlugDetonatesOnFirstShipImpact(shooter) || disruptorSlugDetonatesOn(s)) {
                        p.alive = false;
                        break;
                    }
                    continue;
                }

                // Small screen shake for heavier hits
                if (showImpactVfx) {
                    if (p instanceof Missile) ScreenShake.kick(3.5);
                    else if (p.damage >= 3) ScreenShake.kick(1.8);
                }

                if (p instanceof Missile missile && isArmorBypassingTorpedo(missile)) {
                    applyArmorBypassingTorpedoImpact(missile, s);
                } else {
                    s.takeDamage(
                            scaleDamage(ctx, p.damage),
                            p.x,
                            p.y,
                            p.vx,
                            p.vy,
                            interiorHitProfileForProjectile(shooter, p)
                    );
                }
                logDamageEvent(ctx, "projectile:" + System.identityHashCode(p), p.damage, impactStyle, s, p.x, p.y);
                if (p instanceof Missile m) {
                    applyMissileBlast(ctx, m, s, ships);
                }

                boolean shieldHit = s.shield < shieldBefore - 1e-6;
                boolean hullHit = s.hp < hpBefore;
                // CIWS pellets skip VFX (performance optimization for high-volume point-defense fire)
                boolean isCiwsPellet = p instanceof CIWSPellet;
                boolean showCiwsVfx = showImpactVfx && !isCiwsPellet;
                if (shieldHit) {
                    if (showCiwsVfx) {
                        VFX.spawnShieldImpact(shieldX, shieldY, dirX, dirY, Math.max(1, p.damage), impactStyle);
                    }
                    AudioSystem.onShieldImpact(ctx, impactStyle, shieldX, shieldY);
                }
                if (hullHit) {
                    if (showCiwsVfx) {
                        VFX.spawnHullImpact(hullX, hullY, dirX, dirY, Math.max(1, p.damage), impactStyle);
                    }
                    AudioSystem.onHullImpact(ctx, impactStyle, hullX, hullY);
                }
                if (!shieldHit && !hullHit) {
                    if (showCiwsVfx) {
                        VFX.spawnImpactSparks(hullX, hullY, dirX, dirY, Math.max(1, p.damage));
                    }
                    // Preserve audible feedback for glancing/mitigated hull contacts.
                    AudioSystem.onHullImpact(ctx, impactStyle, hullX, hullY);
                }

                p.alive = false;
                break;
            }
        }
    }

    /**
     * Point-defense projectiles can hit missiles:
     * - CIWS pellets (kinetic)
     * - Team C point-defense laser pulses (beam)
     */
    public static void handleProjectilesVsProjectiles(GameContext ctx, List<Projectile> projectiles) {
        if (projectiles == null || projectiles.isEmpty()) return;
        List<Missile> nearbyMissiles = new java.util.ArrayList<>();

        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (p instanceof PointDefenseLaser laser) {
                Missile m = laser.target();
                if (m == null || !m.alive || laser.faction.isFriendlyTo(m.faction)) {
                    laser.alive = false;
                    continue;
                }

                double halfWidth = Math.max(0.8, laser.width * 0.5);
                double hitR = m.radius + halfWidth;
                if (segmentPointDistanceSq(laser.startX(), laser.startY(), laser.endX, laser.endY, m.x, m.y) <= hitR * hitR) {
                    laser.alive = false;
                    boolean killed = m.applyInterceptHit(Math.max(1, laser.damage));
                    if (killed) {
                        AudioSystem.onExplosion(ctx, m.x, m.y);
                    } else {
                        AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.BEAM, m.x, m.y);
                    }
                }
                continue;
            }
            if (!(p instanceof CIWSPellet pellet)) continue;

            Iterable<? extends Projectile> candidates = projectiles;
            if (ctx != null) {
                ctx.entityQuery.collectMissilesNear(pellet.x, pellet.y, 28.0, nearbyMissiles);
                candidates = nearbyMissiles;
            }
            for (Projectile q : candidates) {
                if (!q.alive) continue;
                if (!(q instanceof Missile m)) continue;
                if (pellet.faction.isFriendlyTo(m.faction)) continue;

                if (circleHit(pellet.x, pellet.y, pellet.radius, m.x, m.y, m.radius)) {
                    pellet.alive = false;
                    boolean killed = m.applyInterceptHit(1);
                    if (killed) {
                        AudioSystem.onExplosion(ctx, m.x, m.y);
                    } else {
                        AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.KINETIC, m.x, m.y);
                    }
                    break;
                }
            }
        }

        for (Projectile p : projectiles) {
            if (!(p instanceof Missile interceptor) || !interceptor.alive) continue;
            if (interceptor.role != Turret.MissileRole.INTERCEPT) continue;

            Iterable<? extends Projectile> candidates = projectiles;
            if (ctx != null) {
                double queryRadius = Math.max(48.0, interceptor.blastRadius * 0.38);
                ctx.entityQuery.collectMissilesNear(interceptor.x, interceptor.y, queryRadius, nearbyMissiles);
                candidates = nearbyMissiles;
            }

            for (Projectile q : candidates) {
                if (q == interceptor || !q.alive) continue;
                if (!(q instanceof Missile hostileMissile)) continue;
                if (interceptor.faction != null && hostileMissile.faction != null
                        && interceptor.faction.isFriendlyTo(hostileMissile.faction)) {
                    continue;
                }

                double hitRadius = hostileMissile.radius + Math.max(interceptor.radius, interceptor.blastRadius * 0.18);
                if (!circleHit(interceptor.x, interceptor.y, interceptor.radius, hostileMissile.x, hostileMissile.y, hitRadius)) {
                    continue;
                }

                interceptor.alive = false;
                boolean killed = hostileMissile.applyInterceptHit(Math.max(1, interceptor.damage));
                if (killed) {
                    AudioSystem.onExplosion(ctx, hostileMissile.x, hostileMissile.y);
                } else {
                    AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.KINETIC, hostileMissile.x, hostileMissile.y);
                }
                break;
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
            if (p instanceof PointDefenseLaser) continue;
            if (p instanceof PhaserBeam) continue;
            SuperweaponShot ws = (p instanceof SuperweaponShot) ? (SuperweaponShot) p : null;
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
                    TacticalCombatDepthSystem.detonateVolatileOre(ctx, a);
                    if (showImpactVfx) {
                        Explosion.spawnShieldHit(a.x, a.y);
                        ScreenShake.kick(Math.min(5.0, 1.2 + a.collisionRadius() * 0.06));
                    }
                    AudioSystem.onExplosion(ctx, a.x, a.y);
                    asteroids.remove(ai);
                }

                if (p instanceof DisruptorSlug slug) {
                    applyDisruptorBlast(ctx, slug, p.x, p.y, ctx == null ? null : ctx.ships, null);
                }
                if (p instanceof DestabilizerPulse pulse) {
                    applyDestabilizerBlast(ctx, pulse, p.x, p.y, ctx == null ? null : ctx.ships);
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

    public static void handleSuperweaponBlastRings(GameContext ctx) {
        if (ctx == null || ctx.ships == null || ctx.ships.isEmpty() || Explosion.active.isEmpty()) return;
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        for (Explosion explosion : Explosion.active) {
            if (explosion == null || explosion.kind != Explosion.Kind.SUPERWEAPON_BLAST) continue;
            Ship shooter = resolveSourceShip(ctx, ctx.ships, explosion.sourceShipId);
            for (int ringIndex = 0; ringIndex < explosion.superweaponRingCount(); ringIndex++) {
                double ringRadius = explosion.superweaponRingRadius(ringIndex);
                double ringHalfWidth = explosion.superweaponRingHalfWidth(ringIndex);
                if (ringRadius <= 1e-6 || ringHalfWidth <= 1e-6) continue;

                nearbyShips.clear();
                ctx.entityQuery.collectAliveShipsNear(
                        explosion.x,
                        explosion.y,
                        ringRadius + ringHalfWidth + ctx.entityQuery.maxShipBroadPhaseRadius(),
                        nearbyShips);

                for (Ship ship : nearbyShips) {
                    if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
                    if (ship.id == explosion.sourceShipId) continue;
                    if (!canDisruptorFieldAffectShip(shooter, explosion.sourceFaction, ship)) continue;

                    double dx = ship.x - explosion.x;
                    double dy = ship.y - explosion.y;
                    double dist = Math.hypot(dx, dy);
                    double shipRadius = HullGeometry.broadPhaseRadius(ship);
                    if (Math.abs(dist - ringRadius) > shipRadius + ringHalfWidth) continue;
                    if (!explosion.markSuperweaponRingHit(ringIndex, ship.id)) continue;

                    double dirX;
                    double dirY;
                    if (dist > 1e-6) {
                        dirX = dx / dist;
                        dirY = dy / dist;
                    } else {
                        dirX = 1.0;
                        dirY = 0.0;
                    }
                    double contactX = explosion.x + dirX * ringRadius;
                    double contactY = explosion.y + dirY * ringRadius;
                    ImpactVisualPoints impactPoints = resolveImpactVisualPoints(ship, contactX, contactY, dx, dy);
                    double shieldBefore = ship.shield;
                    double stripped = ship.drainShieldByMaxFraction(
                            SUPERWEAPON_RING_SHIELD_DRAIN_FRACTION,
                            contactX,
                            contactY,
                            dx,
                            dy);
                    ship.addTemporaryDisable(SUPERWEAPON_RING_DISABLE_SECONDS);

                    boolean showImpactVfx = shouldRenderDamageVfx(ctx, ship, impactPoints.shieldX(), impactPoints.shieldY());
                    if (showImpactVfx) {
                        int strength = Math.max(2, (int) Math.round(3.0 + stripped * 0.06));
                        VFX.spawnShieldImpact(impactPoints.shieldX(), impactPoints.shieldY(), dirX, dirY, strength, VFX.ImpactStyle.BEAM);
                        Explosion.spawnShieldHit(impactPoints.shieldX(), impactPoints.shieldY());
                    }
                    if (shieldBefore > 1e-6) {
                        AudioSystem.onShieldImpact(ctx, VFX.ImpactStyle.BEAM, impactPoints.shieldX(), impactPoints.shieldY());
                    }
                }
            }
        }
    }

    public static void handleStasisFields(GameContext ctx) {
        if (ctx == null || ctx.ships == null || ctx.ships.isEmpty() || Explosion.active.isEmpty()) return;
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        for (Explosion explosion : Explosion.active) {
            if (explosion == null || explosion.kind != Explosion.Kind.STASIS_FIELD) continue;
            double fieldRadius = explosion.stasisFieldRadius();
            if (fieldRadius <= 1e-6) continue;

            nearbyShips.clear();
            ctx.entityQuery.collectAliveShipsNear(
                    explosion.x,
                    explosion.y,
                    fieldRadius + ctx.entityQuery.maxShipBroadPhaseRadius(),
                    nearbyShips);

            for (Ship ship : nearbyShips) {
                if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
                if (ship.id == explosion.sourceShipId) continue;
                if (explosion.sourceFaction != null && ship.faction != null && explosion.sourceFaction.isFriendlyTo(ship.faction)) {
                    continue;
                }

                double maxD = fieldRadius + HullGeometry.broadPhaseRadius(ship);
                if (GameMath.dist2(ship.x, ship.y, explosion.x, explosion.y) > maxD * maxD) continue;

                ship.applyStasisField(RED_STASIS_FIELD_REFRESH_SECONDS);
                ship.collapseShield(
                        RED_STASIS_FIELD_SHIELD_LOCK_SECONDS,
                        explosion.x,
                        explosion.y,
                        ship.x - explosion.x,
                        ship.y - explosion.y);
            }
        }
    }

    private static void applyMissileBlast(GameContext ctx, Missile m, Ship directHit, List<Ship> ships) {
        if (m == null || ships == null || ships.isEmpty()) return;
        double rr = Math.max(20.0, m.blastRadius);
        double baseSplash = Math.max(0.0, m.damage * m.splashDamageMul);
        Ship shooter = resolveSourceShip(ctx, ships, m);

        if (m.strikeVisual == Missile.StrikeVisual.ATOMIC || isYellowHyperweaponWarhead(shooter, m)) {
            applyNuclearBlast(ctx, m, directHit, ships, shooter);
            return;
        }

        if (baseSplash > 1e-6) {
            Iterable<Ship> candidates = ships;
            List<Ship> nearbyShips = new java.util.ArrayList<>();
            if (ctx != null) {
                ctx.entityQuery.collectAliveShipsNear(m.x, m.y, rr + ctx.entityQuery.maxShipBroadPhaseRadius(), nearbyShips);
                candidates = nearbyShips;
            }
            for (Ship s : candidates) {
                if (s == null || !s.alive) continue;
                if (s == directHit) continue;
                if (s.faction.isFriendlyTo(m.faction)) continue;
                if (!canProjectileDamageShip(ctx, shooter, m, s)) continue;

                double d = Math.hypot(s.x - m.x, s.y - m.y);
                double maxD = rr + HullGeometry.broadPhaseRadius(s);
                if (d > maxD) continue;

                double falloff = 1.0 - (d / Math.max(1.0, maxD));
                int splash = (int) Math.round(baseSplash * (0.35 + 0.65 * falloff));
                if (splash <= 0) continue;
                markPlayerHitContribution(ctx, m, s);
                s.takeDamage(scaleDamage(ctx, splash), m.x, m.y);
                logDamageEvent(ctx, "missile_splash:" + System.identityHashCode(m), splash, VFX.ImpactStyle.EXPLOSIVE, s, m.x, m.y);
            }
        }

        if (shouldRenderDamageVfx(ctx, directHit, m.x, m.y)) {
            VFX.spawnHullImpact(m.x, m.y, 0.0, 0.0, Math.max(2, m.damage), VFX.ImpactStyle.EXPLOSIVE);
            Explosion.spawnShieldHit(m.x, m.y);
        }
        AudioSystem.onExplosion(ctx, m.x, m.y);
    }

    private static boolean isArmorBypassingTorpedo(Missile missile) {
        return missile != null && missile.strikeVisual == Missile.StrikeVisual.TORPEDO;
    }

    private static void applyArmorBypassingTorpedoImpact(Missile missile, Ship target) {
        if (missile == null || target == null) return;
        double durability = Math.max(1.0, target.hp + Math.max(0.0, target.shield));
        int trueHullDamage = Math.max(1, missile.damage);
        if (isBattleshipOrSmaller(target.role)) {
            trueHullDamage = Math.max(trueHullDamage, (int) Math.ceil(durability * 1.35));
        } else {
            trueHullDamage = Math.max(trueHullDamage, (int) Math.ceil(durability * 0.70));
        }
        target.shield = 0.0;
        target.shieldActive = false;
        target.hp = Math.max(0, target.hp - trueHullDamage);
        if (target.hp <= 0) {
            target.hp = 0;
            target.alive = false;
            target.dying = true;
        }
    }

    private static boolean isBattleshipOrSmaller(ShipRole role) {
        if (role == null) return true;
        if (role.isTitanOrMothership()) return false;
        return switch (role) {
            case DREADNOUGHT, SUPERSHIP -> false;
            default -> true;
        };
    }

    private static void applyNuclearBlast(GameContext ctx, Missile missile, Ship directHit, List<Ship> ships, Ship shooter) {
        if (missile == null || ships == null || ships.isEmpty()) return;
        double rr = Math.max(240.0, missile.blastRadius);
        boolean affected = false;

        Iterable<Ship> candidates = ships;
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        if (ctx != null) {
            ctx.entityQuery.collectAliveShipsNear(missile.x, missile.y, rr + ctx.entityQuery.maxShipBroadPhaseRadius(), nearbyShips);
            candidates = nearbyShips;
        }

        for (Ship ship : candidates) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction != null && missile.faction != null && ship.faction.isFriendlyTo(missile.faction)) continue;
            if (!canProjectileDamageShip(ctx, shooter, missile, ship)) continue;

            double dx = ship.x - missile.x;
            double dy = ship.y - missile.y;
            double maxD = rr + HullGeometry.broadPhaseRadius(ship);
            double dist = Math.hypot(dx, dy);
            if (dist > maxD) continue;

            double falloff = 1.0 - MathUtil.clamp(dist / Math.max(1.0, maxD), 0.0, 1.0);
            double impactVx = (dist > 1e-6) ? dx : missile.vx;
            double impactVy = (dist > 1e-6) ? dy : missile.vy;
            ImpactVisualPoints impactPoints = resolveImpactVisualPoints(ship, missile.x, missile.y, impactVx, impactVy);
            double shieldBefore = ship.shield;
            int hpBefore = ship.hp;

            double stripped = ship.drainShieldByAmount(
                    Math.max(18.0, missile.damage * (0.70 + 0.40 * falloff)),
                    missile.x,
                    missile.y,
                    impactVx,
                    impactVy);

            boolean unshieldedHull = shieldBefore <= 1e-6;
            boolean titanOrMothership = ship.role != null && ship.role.isTitanOrMothership();
            boolean strikeAtomic = missile.strikeVisual == Missile.StrikeVisual.ATOMIC;
            boolean nuclearHardTarget = titanOrMothership
                    || ship.role == ShipRole.DREADNOUGHT
                    || ship.role == ShipRole.SUPERSHIP;
            if (unshieldedHull) {
                if (strikeAtomic && !nuclearHardTarget) {
                    ship.scaleCurrentHullIntegrity(0.0);
                } else if (!titanOrMothership && ship.role != null && SpawnSystem.requiredHangarTierForRole(ship.role) <= 2) {
                    ship.scaleCurrentHullIntegrity(0.0);
                } else if (titanOrMothership) {
                    ship.scaleCurrentHullIntegrity(Math.max(0.28, 0.62 - 0.22 * falloff));
                } else {
                    ship.scaleCurrentHullIntegrity(Math.max(0.10, 0.42 - 0.26 * falloff));
                }
            } else if (ship == directHit && ship.shield <= 1e-6) {
                if (strikeAtomic && !nuclearHardTarget) {
                    ship.scaleCurrentHullIntegrity(0.0);
                } else {
                    ship.scaleCurrentHullIntegrity(0.84);
                }
            }

            int hullDamage = Math.max(0, hpBefore - ship.hp);
            if (stripped > 1e-6 || hullDamage > 0) {
                markPlayerHitContribution(ctx, missile, ship);
                if (hullDamage > 0) {
                    logDamageEvent(ctx, "nuclear_blast:" + System.identityHashCode(missile), hullDamage, VFX.ImpactStyle.EXPLOSIVE, ship, missile.x, missile.y);
                }

                boolean shieldHit = stripped > 1e-6 || ship.shield < shieldBefore - 1e-6;
                boolean hullHit = ship.hp < hpBefore;
                boolean showShipVfx = shouldRenderDamageVfx(ctx, ship,
                        shieldHit && !hullHit ? impactPoints.shieldX() : impactPoints.hullX(),
                        shieldHit && !hullHit ? impactPoints.shieldY() : impactPoints.hullY());
                if (showShipVfx) {
                    double dirLen = Math.hypot(impactVx, impactVy);
                    double dirX = (dirLen > 1e-6) ? (impactVx / dirLen) : 1.0;
                    double dirY = (dirLen > 1e-6) ? (impactVy / dirLen) : 0.0;
                    if (shieldHit) {
                        VFX.spawnShieldImpact(impactPoints.shieldX(), impactPoints.shieldY(), dirX, dirY,
                                Math.max(3, (int) Math.round(3.0 + stripped * 0.05)), VFX.ImpactStyle.EXPLOSIVE);
                        Explosion.spawnShieldHit(impactPoints.shieldX(), impactPoints.shieldY());
                    }
                    if (hullHit) {
                        VFX.spawnHullImpact(impactPoints.hullX(), impactPoints.hullY(), dirX, dirY,
                                Math.max(4, hullDamage), VFX.ImpactStyle.EXPLOSIVE);
                    }
                }
                affected = true;
            }
        }

        boolean showImpactVfx = shouldRenderDamageVfx(ctx, directHit, missile.x, missile.y);
        if (showImpactVfx) {
            VFX.spawnHullImpact(missile.x, missile.y, 0.0, 0.0, Math.max(5, missile.damage), VFX.ImpactStyle.EXPLOSIVE);
            Explosion.spawnFinalDetonation(missile.x, missile.y, rr);
            ScreenShake.kick(Math.min(8.5, 4.0 + rr * 0.010));
        }
        if (affected) {
            AudioSystem.onExplosion(ctx, missile.x, missile.y);
        }
    }

    private static void applyDestabilizerBlast(GameContext ctx, DestabilizerPulse pulse, double x, double y, List<Ship> ships) {
        if (pulse == null) return;
        Ship shooter = resolveSourceShip(ctx, ships, pulse);
        boolean artilleryTitanPulse = shooter != null && shooter.role == ShipRole.ARTILLERY_TITAN;
        boolean blueHyperweaponPulse = isBlueHyperweaponPulse(shooter);
        double rr = Math.max(120.0, pulse.blastRadius);
        if (artilleryTitanPulse) rr = Math.max(rr, pulse.blastRadius * 1.22);
        boolean affected = false;

        Iterable<Ship> candidates = ships;
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        if (ctx != null && ships != null && !ships.isEmpty()) {
            ctx.entityQuery.collectAliveShipsNear(x, y, rr + ctx.entityQuery.maxShipBroadPhaseRadius(), nearbyShips);
            candidates = nearbyShips;
        }

        if (candidates != null) {
            for (Ship s : candidates) {
                if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
                if (s.id == pulse.sourceShipId) continue;
                if (!canProjectileDamageShip(ctx, shooter, pulse, s)) continue;

                double dx = s.x - x;
                double dy = s.y - y;
                double centerDist = Math.hypot(dx, dy);
                double maxD = rr + HullGeometry.broadPhaseRadius(s);
                if (centerDist > maxD) continue;

                double falloff = 1.0 - MathUtil.clamp(centerDist / Math.max(1.0, maxD), 0.0, 1.0);
                double intensity = DESTABILIZER_PULSE_EDGE_FALLOFF + (1.0 - DESTABILIZER_PULSE_EDGE_FALLOFF) * falloff;
                double coreRadius = Math.max(DESTABILIZER_PULSE_CORE_RADIUS_MIN,
                        HullGeometry.broadPhaseRadius(s) + pulse.radius * 3.2);
                double coreFrac = 1.0 - MathUtil.clamp(centerDist / Math.max(1.0, coreRadius), 0.0, 1.0);
                double hullBurst = 1.0 + DESTABILIZER_PULSE_DIRECT_HIT_BONUS * coreFrac;
                double impactVx = (centerDist > 1e-6) ? dx : pulse.vx;
                double impactVy = (centerDist > 1e-6) ? dy : pulse.vy;
                ImpactVisualPoints impactPoints = resolveImpactVisualPoints(s, x, y, impactVx, impactVy);
                double shieldBefore = s.shield;
                int hpBefore = s.hp;
                if (blueHyperweaponPulse) {
                    markPlayerHitContribution(ctx, pulse, s);
                    double stripped = s.collapseShield(
                            Math.max(1.0, s.shieldRebootDelay * 1.45),
                            x,
                            y,
                            impactVx,
                            impactVy);
                    if (s.role != null && s.role.isTitanOrMothership()) {
                        s.scaleCurrentHullIntegrity(0.50);
                    } else {
                        s.scaleCurrentHullIntegrity(0.0);
                    }
                    int hullDamage = Math.max(0, hpBefore - s.hp);
                    if (hullDamage > 0) {
                        logDamageEvent(ctx, "hyper_pulse:" + System.identityHashCode(pulse), hullDamage, VFX.ImpactStyle.ENERGY, s, x, y);
                    }

                    boolean shieldHit = stripped > 1e-6 || s.shield < shieldBefore - 1e-6;
                    boolean hullHit = s.hp < hpBefore;
                    boolean showShipVfx = shouldRenderDamageVfx(ctx, s, impactPoints.hullX(), impactPoints.hullY());
                    if (showShipVfx) {
                        double dirLen = Math.hypot(impactVx, impactVy);
                        double dirX = (dirLen > 1e-6) ? (impactVx / dirLen) : 1.0;
                        double dirY = (dirLen > 1e-6) ? (impactVy / dirLen) : 0.0;
                        if (shieldHit) {
                            VFX.spawnShieldImpact(impactPoints.shieldX(), impactPoints.shieldY(), dirX, dirY,
                                    Math.max(4, (int) Math.round(4.0 + stripped * 0.07)), VFX.ImpactStyle.ENERGY);
                            Explosion.spawnShieldHit(impactPoints.shieldX(), impactPoints.shieldY());
                        }
                        if (hullHit) {
                            VFX.spawnHullImpact(impactPoints.hullX(), impactPoints.hullY(), dirX, dirY,
                                    Math.max(6, hullDamage), VFX.ImpactStyle.ENERGY);
                        }
                    }
                    if (shieldHit) {
                        AudioSystem.onShieldImpact(ctx, VFX.ImpactStyle.ENERGY, impactPoints.shieldX(), impactPoints.shieldY());
                    }
                    if (hullHit) {
                        AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.ENERGY, impactPoints.hullX(), impactPoints.hullY());
                    }
                    affected = true;
                    continue;
                }
                double shieldScale = DESTABILIZER_PULSE_SHIELD_EDGE_SCALE
                        + (1.0 - DESTABILIZER_PULSE_SHIELD_EDGE_SCALE) * falloff;
                double shieldDamage = Math.max(0.0, pulse.shieldDamage
                        * shieldScale
                        * (1.0 + DESTABILIZER_PULSE_SHIELD_CORE_BONUS * coreFrac));
                if (artilleryTitanPulse) {
                    shieldDamage *= 1.75;
                }
                double stripped = s.drainShieldByAmount(shieldDamage, x, y, impactVx, impactVy);

                int hullDamage = Math.max(8, (int) Math.round(pulse.damage * intensity * hullBurst));
                if (artilleryTitanPulse) {
                    hullDamage = Math.max(hullDamage, (int) Math.round(pulse.damage * (1.18 + intensity + coreFrac)));
                }
                markPlayerHitContribution(ctx, pulse, s);
                boolean artilleryExecute = artilleryTitanPulse && s.hpMax < RoleStats.get(ShipRole.BATTLESHIP).hpMax;
                if (artilleryExecute) {
                    int executionDamage = Math.max(720, s.hpMax * 20);
                    s.drainShieldByAmount(s.shieldMax + shieldDamage * 2.0, x, y, impactVx, impactVy);
                    s.takeDamage(scaleDamage(ctx, executionDamage), impactPoints.hullX(), impactPoints.hullY(), impactVx, impactVy);
                    if (s.alive && !s.dying && s.hp > 0) {
                        s.takePenetratingInternalDamage(executionDamage, impactPoints.hullX(), impactPoints.hullY(), impactVx, impactVy);
                    }
                    if (s.alive && !s.dying && s.hp > 0) {
                        s.scaleCurrentHullIntegrity(0.0);
                    }
                    logDamageEvent(ctx, "destabilizer_execution:" + System.identityHashCode(pulse), executionDamage, VFX.ImpactStyle.ENERGY, s, x, y);
                    hullDamage = executionDamage;
                    s.applyShipwideRoomDisruption();
                    s.applyDestabilized(Math.max(10.0, pulse.destabilizeSeconds * 1.6));
                    s.addTemporaryDisable(3.0);
                    if (ctx != null) {
                        VFX.spawnArtilleryExecutionEffect(impactPoints.hullX(), impactPoints.hullY(),
                                Math.max(s.radius * 1.35, pulse.radius * 3.8));
                        EventSystem.showWorldCallout(ctx, s.x, s.y - s.radius - 26.0, "EXECUTED",
                                new java.awt.Color(132, 242, 255), 1.2);
                        if (shooter == ctx.player) {
                            EventSystem.showBanner(ctx, "TARGET ANNIHILATED", 1.0);
                        }
                    }
                } else {
                    s.takePenetratingInternalDamage(hullDamage, impactPoints.hullX(), impactPoints.hullY(), impactVx, impactVy);
                    logDamageEvent(ctx, "destabilizer:" + System.identityHashCode(pulse), hullDamage, VFX.ImpactStyle.ENERGY, s, x, y);
                    s.applyShipwideRoomDisruption();
                    s.applyDestabilized(pulse.destabilizeSeconds * (0.58 + 0.42 * intensity) * (1.0 + 0.35 * coreFrac));
                    if (coreFrac > 1e-6) {
                        double disableSeconds = (DESTABILIZER_PULSE_DISABLE_FLOOR_SECONDS
                                + DESTABILIZER_PULSE_DISABLE_CORE_SECONDS * coreFrac) * intensity;
                        if (artilleryTitanPulse) disableSeconds *= 1.45;
                        s.addTemporaryDisable(disableSeconds);
                    }
                }

                boolean shieldHit = stripped > 1e-6 || s.shield < shieldBefore - 1e-6;
                boolean hullHit = s.hp < hpBefore;
                boolean showShipVfx = shouldRenderDamageVfx(ctx, s, impactPoints.hullX(), impactPoints.hullY());
                if (showShipVfx) {
                    double dirLen = Math.hypot(impactVx, impactVy);
                    double dirX = (dirLen > 1e-6) ? (impactVx / dirLen) : 1.0;
                    double dirY = (dirLen > 1e-6) ? (impactVy / dirLen) : 0.0;
                    if (shieldHit) {
                        int shieldStrength = Math.max(2, (int) Math.round(2.0 + stripped * 0.06));
                        VFX.spawnShieldImpact(impactPoints.shieldX(), impactPoints.shieldY(), dirX, dirY, shieldStrength, VFX.ImpactStyle.ENERGY);
                        Explosion.spawnShieldHit(impactPoints.shieldX(), impactPoints.shieldY());
                    }
                    if (hullHit) {
                        VFX.spawnHullImpact(impactPoints.hullX(), impactPoints.hullY(), dirX, dirY, Math.max(1, hullDamage), VFX.ImpactStyle.ENERGY);
                    }
                }
                if (shieldHit) {
                    AudioSystem.onShieldImpact(ctx, VFX.ImpactStyle.ENERGY, impactPoints.shieldX(), impactPoints.shieldY());
                }
                if (hullHit) {
                    AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.ENERGY, impactPoints.hullX(), impactPoints.hullY());
                } else if (!shieldHit) {
                    AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.ENERGY, impactPoints.hullX(), impactPoints.hullY());
                }
                affected = true;
            }
        }

        boolean showImpactVfx = shouldRenderDamageVfx(ctx, null, x, y);
        if (showImpactVfx) {
            VFX.spawnHullImpact(x, y, 0.0, 0.0, Math.max(3, pulse.damage), VFX.ImpactStyle.ENERGY);
            Explosion.spawnDestabilizerPulse(x, y, rr);
            ScreenShake.kick(3.8);
        }
        if (affected) {
            AudioSystem.onExplosion(ctx, x, y);
        } else {
            AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.ENERGY, x, y);
        }
    }

    private static void applyDisruptorBlast(GameContext ctx,
                                            DisruptorSlug slug,
                                            double x,
                                            double y,
                                            List<Ship> ships,
                                            Ship directHit) {
        if (slug == null || ships == null || ships.isEmpty()) return;
        double rr = Math.max(64.0, slug.blastRadius);
        boolean affected = false;
        Ship shooter = resolveSourceShip(ctx, ships, slug);

        if (isRedHyperweaponStasisWeapon(shooter)) {
            Explosion.spawnStasisField(x, y, RED_STASIS_FIELD_SECONDS, slug.sourceShipId, slug.faction, rr);
            boolean showImpactVfx = shouldRenderDamageVfx(ctx, null, x, y);
            if (showImpactVfx) {
                VFX.spawnHullImpact(x, y, 0.0, 0.0, Math.max(4, slug.damage), VFX.ImpactStyle.EXPLOSIVE);
                Explosion.spawnShieldHit(x, y);
                ScreenShake.kick(Math.min(8.0, 4.2 + rr * 0.010));
            }
            AudioSystem.onExplosion(ctx, x, y);
            return;
        }

        Iterable<Ship> candidates = ships;
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        if (ctx != null) {
            ctx.entityQuery.collectAliveShipsNear(x, y, rr + ctx.entityQuery.maxShipBroadPhaseRadius(), nearbyShips);
            candidates = nearbyShips;
        }
        for (Ship s : candidates) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s.id == slug.sourceShipId) continue;
            if (!canProjectileDamageShip(ctx, shooter, slug, s)) continue;
            if (isRedSupershipTitanSplashImmune(shooter, s, directHit)) continue;

            double maxD = rr + HullGeometry.broadPhaseRadius(s);
            if (GameMath.dist2(s.x, s.y, x, y) > maxD * maxD) continue;

            double dx = s.x - x;
            double dy = s.y - y;
            double centerDist = Math.hypot(dx, dy);
            double falloff = 1.0 - MathUtil.clamp(centerDist / Math.max(1.0, maxD), 0.0, 1.0);
            double impactVx = (centerDist > 1e-6) ? dx : slug.vx;
            double impactVy = (centerDist > 1e-6) ? dy : slug.vy;
            ImpactVisualPoints impactPoints = resolveImpactVisualPoints(s, x, y, impactVx, impactVy);
            double shieldBefore = s.shield;
            int hpBefore = s.hp;
            double shieldOfflineSeconds = resolveDisruptorShieldOfflineSeconds(s);
            double stripped = s.collapseShield(shieldOfflineSeconds, x, y, impactVx, impactVy);
            int hullDamage = resolveDisruptorHullDamage(slug, s, falloff, s == directHit);
            if (hullDamage > 0) {
                markPlayerHitContribution(ctx, slug, s);
                s.takeDamage(scaleDamage(ctx, hullDamage), x, y, impactVx, impactVy);
                logDamageEvent(ctx, "disruptor_blast:" + System.identityHashCode(slug), hullDamage, VFX.ImpactStyle.BEAM, s, x, y);
            }
            s.applyTemporaryDisable(disruptorDisableSeconds(s));
            slug.markAffected(s);

            boolean shieldHit = stripped > 1e-6 || s.shield < shieldBefore - 1e-6;
            boolean hullHit = s.hp < hpBefore;
            boolean showShipVfx = shouldRenderDamageVfx(ctx, s,
                    shieldHit && !hullHit ? impactPoints.shieldX() : impactPoints.hullX(),
                    shieldHit && !hullHit ? impactPoints.shieldY() : impactPoints.hullY());
            if (showShipVfx) {
                double dirLen = Math.hypot(impactVx, impactVy);
                double dirX = (dirLen > 1e-6) ? (impactVx / dirLen) : 1.0;
                double dirY = (dirLen > 1e-6) ? (impactVy / dirLen) : 0.0;
                if (shieldHit) {
                    VFX.spawnShieldImpact(impactPoints.shieldX(), impactPoints.shieldY(), dirX, dirY,
                            Math.max(2, (int) Math.round(2.0 + stripped * 0.05)), VFX.ImpactStyle.BEAM);
                    Explosion.spawnShieldHit(impactPoints.shieldX(), impactPoints.shieldY());
                }
                if (hullHit) {
                    VFX.spawnHullImpact(impactPoints.hullX(), impactPoints.hullY(), dirX, dirY,
                            Math.max(1, hullDamage), VFX.ImpactStyle.BEAM);
                }
            }
            affected = true;
        }

        boolean showImpactVfx = shouldRenderDamageVfx(ctx, null, x, y);
        if (showImpactVfx) {
            VFX.spawnHullImpact(x, y, 0.0, 0.0, Math.max(3, slug.damage), VFX.ImpactStyle.EXPLOSIVE);
            Explosion.spawnShieldHit(x, y);
            Explosion.spawnSuperweaponBlast(x, y, slug.sourceShipId, slug.faction);
            ScreenShake.kick(Math.min(7.0, 3.6 + rr * 0.012));
        }
        if (affected) {
            AudioSystem.onExplosion(ctx, x, y);
        }
    }

    private static double disruptorDisableSeconds(Ship ship) {
        if (ship == null) return 10.0;
        double size = HullGeometry.broadPhaseRadius(ship);
        double t = MathUtil.clamp((size - 12.0) / 42.0, 0.0, 1.0);
        return 10.0 + t * 10.0;
    }

    private static double resolveDisruptorShieldOfflineSeconds(Ship ship) {
        if (ship == null) return 3.0;
        return Math.max(3.0, ship.shieldRebootDelay * 1.2);
    }

    private static int resolveDisruptorHullDamage(DisruptorSlug slug,
                                                  Ship ship,
                                                  double falloff,
                                                  boolean directImpact) {
        if (slug == null || ship == null) return 0;
        double base = Math.max(1.0, slug.damage);
        double scaled = base * (directImpact ? 0.65 : 0.34);
        double damage = scaled * (0.60 + 0.40 * MathUtil.clamp(falloff, 0.0, 1.0));
        return Math.max(1, (int) Math.round(damage));
    }

    private static boolean disruptorSlugDetonatesOn(Ship ship) {
        if (ship == null || ship.role == null) return true;
        return SpawnSystem.requiredHangarTierForRole(ship.role) > 2;
    }

    private static boolean redKineticSlugDetonatesOnFirstShipImpact(Ship shooter) {
        return shooter != null
                && shooter.faction == Faction.ENEMY
                && (shooter.role == ShipRole.SUPERSHIP || shooter.role == ShipRole.HYPERWEAPON_TITAN);
    }

    private static boolean isRedRailgunSuperweapon(Ship shooter, Projectile projectile) {
        return shooter != null
                && projectile instanceof SuperweaponShot
                && shooter.faction == Faction.ENEMY
                && shooter.superweaponPattern == Ship.SuperweaponPattern.KINETIC_SLUG
                && (shooter.role == ShipRole.SUPERSHIP || shooter.role == ShipRole.HYPERWEAPON_TITAN);
    }

    private static boolean isRedSupershipTitanSplashImmune(Ship shooter, Ship target, Ship directHit) {
        return shooter != null
                && shooter.role == ShipRole.SUPERSHIP
                && shooter.faction == Faction.ENEMY
                && target != null
                && target.role != null
                && target.role.isTitanOrMothership()
                && target != directHit;
    }

    private static boolean isRedHyperweaponStasisWeapon(Ship shooter) {
        return shooter != null
                && shooter.role == ShipRole.HYPERWEAPON_TITAN
                && shooter.faction == Faction.ENEMY;
    }

    private static boolean isBlueHyperweaponPulse(Ship shooter) {
        return shooter != null
                && shooter.role == ShipRole.HYPERWEAPON_TITAN
                && shooter.faction != Faction.ENEMY
                && shooter.faction != Faction.TEAM_C
                && shooter.faction != Faction.TEAM_D;
    }

    private static boolean isYellowHyperweaponWarhead(Ship shooter, Missile missile) {
        return shooter != null
                && missile != null
                && shooter.role == ShipRole.HYPERWEAPON_TITAN
                && shooter.faction == Faction.TEAM_D;
    }

    private static boolean canDisruptorFieldAffectShip(Ship shooter, Faction sourceFaction, Ship target) {
        if (target == null) return false;
        if (sourceFaction != null && target.faction != null && sourceFaction.isFriendlyTo(target.faction)) return false;
        if (TargetingSystem.isCiwsOnlyTarget(target)) return false;
        if (isRedSupershipTitanSplashImmune(shooter, target, null)) return false;
        if (shooter == null || shooter.role == null) return true;
        if (isCapitalShip(shooter.role) && target.isSmallCraft()) return false;
        return true;
    }

    private static VFX.ImpactStyle impactStyleFor(Projectile p) {
        if (p instanceof Missile) return VFX.ImpactStyle.EXPLOSIVE;
        if (p instanceof DisruptorSlug) return VFX.ImpactStyle.EXPLOSIVE;
        if (p instanceof DestabilizerPulse) return VFX.ImpactStyle.ENERGY;
        if (p instanceof SuperweaponShot) return VFX.ImpactStyle.BEAM;
        if (p instanceof PhaserBeam) return VFX.ImpactStyle.BEAM;
        if (p instanceof PointDefenseLaser) return VFX.ImpactStyle.BEAM;
        if (p instanceof EnergyBolt bolt) {
            return bolt.isBeamBolt() ? VFX.ImpactStyle.BEAM : VFX.ImpactStyle.ENERGY;
        }
        if (p instanceof CIWSPellet) return VFX.ImpactStyle.KINETIC;
        return VFX.ImpactStyle.KINETIC;
    }

    private static void handlePhaserBeamVsShips(GameContext ctx, PhaserBeam beam, List<Ship> ships) {
        if (beam == null || !beam.alive || ships == null || ships.isEmpty()) return;

        double sx = beam.startX();
        double sy = beam.startY();
        double ex = beam.endX();
        double ey = beam.endY();
        double halfWidth = Math.max(1.0, beam.width * 0.5);
        Ship shooter = resolveSourceShip(ctx, ships, beam);

        List<BeamHitCandidate> hits = new java.util.ArrayList<>();
        Iterable<Ship> candidates = ships;
        List<Ship> nearbyShips = new java.util.ArrayList<>();
        if (ctx != null) {
            double mx = (sx + ex) * 0.5;
            double my = (sy + ey) * 0.5;
            double queryRadius = Math.hypot(ex - sx, ey - sy) * 0.5 + halfWidth + ctx.entityQuery.maxShipBroadPhaseRadius();
            ctx.entityQuery.collectAliveShipsNear(mx, my, queryRadius, nearbyShips);
            candidates = nearbyShips;
        }

        for (Ship s : candidates) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.isFriendlyTo(beam.faction)) continue;
            if (!canProjectileDamageShip(ctx, shooter, beam, s)) continue;

            double shipBroad = HullGeometry.broadPhaseRadius(s) + halfWidth;
            if (segmentPointDistanceSq(sx, sy, ex, ey, s.x, s.y) > shipBroad * shipBroad) continue;
            if (!HullGeometry.segmentIntersectsShip(sx, sy, ex, ey, halfWidth, s)) continue;

            double t = segmentParamForPoint(sx, sy, ex, ey, s.x, s.y);
            hits.add(new BeamHitCandidate(s, t));
        }

        if (hits.isEmpty()) {
            beam.clampImpactFraction(1.0);
            return;
        }
        hits.sort(java.util.Comparator.comparingDouble(BeamHitCandidate::t));

        double dx = ex - sx;
        double dy = ey - sy;
        double len = Math.hypot(dx, dy);
        double dirX = (len > 1e-9) ? (dx / len) : Math.cos(beam.angle);
        double dirY = (len > 1e-9) ? (dy / len) : Math.sin(beam.angle);
        int damage = beam.rollFrameDamage(ctx == null ? null : ctx.rng, GameContext.DT);
        if (!beam.penetratesTargets()) {
            BeamHitCandidate first = hits.get(0);
            BeamImpactResult impact = resolveBeamImpactResult(first.ship(), sx, sy, ex, ey, first.t(), dirX, dirY);
            beam.clampImpactFraction(impact.impactFraction());
            int scaledDamage = scaleBeamDamage(beam, damage, impact.impactFraction());
            if (scaledDamage <= 0) return;
            applyBeamHit(ctx, beam, first.ship(), scaledDamage, dirX, dirY, impact.hitX(), impact.hitY());
            return;
        }

        beam.clampImpactFraction(1.0);
        if (damage <= 0) return;
        for (BeamHitCandidate hit : hits) {
            BeamImpactResult impact = resolveBeamImpactResult(hit.ship(), sx, sy, ex, ey, hit.t(), dirX, dirY);
            int scaledDamage = scaleBeamDamage(beam, damage, impact.impactFraction());
            if (scaledDamage <= 0) continue;
            applyBeamHit(ctx, beam, hit.ship(), scaledDamage, dirX, dirY, impact.hitX(), impact.hitY());
        }
    }

    private static void applyBeamHit(GameContext ctx,
                                     PhaserBeam beam,
                                     Ship target,
                                     int damage,
                                     double dirX,
                                     double dirY,
                                     double hitX,
                                     double hitY) {
        if (beam == null || target == null || damage <= 0) return;
        ImpactVisualPoints impactPoints = resolveImpactVisualPoints(target, hitX, hitY, dirX, dirY);
        markPlayerHitContribution(ctx, beam, target);
        double shieldBefore = target.shield;
        int hpBefore = target.hp;
        target.takeDamage(scaleDamage(ctx, damage), hitX, hitY, dirX, dirY, Ship.InteriorHitProfile.LASER_LINE);
        logDamageEvent(ctx, "phaser_beam:" + System.identityHashCode(beam), damage, VFX.ImpactStyle.BEAM, target, hitX, hitY);

        boolean shieldHit = target.shield < shieldBefore - 1e-6;
        boolean hullHit = target.hp < hpBefore;
        double shieldX = impactPoints.shieldX();
        double shieldY = impactPoints.shieldY();
        double hullX = impactPoints.hullX();
        double hullY = impactPoints.hullY();
        boolean showImpactVfx = shouldRenderDamageVfx(ctx, target,
                shieldHit && !hullHit ? shieldX : hullX,
                shieldHit && !hullHit ? shieldY : hullY);
        if (shieldHit) {
            if (showImpactVfx) VFX.spawnShieldImpact(shieldX, shieldY, dirX, dirY, Math.max(1, damage), VFX.ImpactStyle.BEAM);
            AudioSystem.onShieldImpact(ctx, VFX.ImpactStyle.BEAM, shieldX, shieldY);
        }
        if (hullHit) {
            if (showImpactVfx) VFX.spawnHullImpact(hullX, hullY, dirX, dirY, Math.max(1, damage), VFX.ImpactStyle.BEAM);
            AudioSystem.onHullImpact(ctx, VFX.ImpactStyle.BEAM, hullX, hullY);
        }
    }

    private static BeamImpactResult resolveBeamImpactResult(Ship ship,
                                                            double sx,
                                                            double sy,
                                                            double ex,
                                                            double ey,
                                                            double fallbackT,
                                                            double dirX,
                                                            double dirY) {
        if (ship == null) {
            double hitX = sx + (ex - sx) * fallbackT;
            double hitY = sy + (ey - sy) * fallbackT;
            return new BeamImpactResult(hitX, hitY, fallbackT);
        }
        HullGeometry.ImpactSample impact = firstBeamHullImpact(ship, sx, sy, ex, ey, fallbackT);
        double hitX = sx + (ex - sx) * fallbackT;
        double hitY = sy + (ey - sy) * fallbackT;
        double impactFraction = fallbackT;
        if (impact != null && impact.onHull) {
            double c = Math.cos(ship.angle);
            double s = Math.sin(ship.angle);
            hitX = ship.x + impact.localX * c - impact.localY * s;
            hitY = ship.y + impact.localX * s + impact.localY * c;
            impactFraction = segmentParamForPoint(sx, sy, ex, ey, hitX, hitY);
        }
        return new BeamImpactResult(hitX, hitY, impactFraction);
    }

    private static int scaleBeamDamage(PhaserBeam beam, int damage, double impactFraction) {
        if (beam == null || damage <= 0) return 0;

        double frac = MathUtil.clamp(impactFraction, 0.0, 1.0);
        double falloff;
        if (beam.penetratesTargets()) {
            // Long-range phaser fire still reaches across the field, but the impact weakens
            // the farther the target is from the emitter.
            falloff = 1.0 - (0.58 * Math.pow(frac, 1.12));
        } else {
            // Team C regular beams should feel like true artillery lines: powerful up close,
            // but less oppressive at the edge of their enormous reach.
            falloff = 1.0 - (0.68 * Math.pow(frac, 1.20));
        }
        falloff = MathUtil.clamp(falloff, 0.34, 1.0);
        return Math.max(1, (int) Math.round(damage * falloff));
    }

    private static ImpactVisualPoints resolveImpactVisualPoints(Ship ship,
                                                                double hitX,
                                                                double hitY,
                                                                double impactVx,
                                                                double impactVy) {
        if (ship == null) return new ImpactVisualPoints(hitX, hitY, hitX, hitY);
        HullGeometry.ImpactSample impact = resolveHullImpactSample(ship, hitX, hitY, impactVx, impactVy);
        if (impact == null || !impact.onHull) {
            return new ImpactVisualPoints(hitX, hitY, hitX, hitY);
        }
        double[] hullWorld = HullGeometry.localToWorld(ship, impact.localX, impact.localY);
        double nx = impact.normalizedX;
        double ny = impact.normalizedY;
        double len = Math.hypot(nx, ny);
        if (len <= 1e-6) {
            double[] fallback = fallbackImpactNormal(ship, impactVx, impactVy, hitX, hitY);
            nx = fallback[0];
            ny = fallback[1];
        } else {
            nx /= len;
            ny /= len;
        }
        double c = Math.cos(ship.angle);
        double s = Math.sin(ship.angle);
        double worldNx = nx * c - ny * s;
        double worldNy = nx * s + ny * c;
        double shieldOffset = Math.max(5.0, ship.radius * 0.24) * SHIELD_SHELL_OFFSET_SCALE;
        double shieldX = hullWorld[0] + worldNx * shieldOffset;
        double shieldY = hullWorld[1] + worldNy * shieldOffset;
        return new ImpactVisualPoints(hullWorld[0], hullWorld[1], shieldX, shieldY);
    }

    private static HullGeometry.ImpactSample resolveHullImpactSample(Ship ship,
                                                                     double hitX,
                                                                     double hitY,
                                                                     double impactVx,
                                                                     double impactVy) {
        HullGeometry.ImpactSample impact = HullGeometry.sampleImpact(ship, hitX, hitY, true);
        if (impact != null && impact.onHull) return impact;
        if (Double.isFinite(impactVx) && Double.isFinite(impactVy)) {
            double vLen = Math.hypot(impactVx, impactVy);
            if (vLen > 1e-9) {
                double nx = -impactVx / vLen;
                double ny = -impactVy / vLen;
                double probe = Math.max(8.0, ship.radius + 8.0);
                return HullGeometry.sampleImpact(ship, ship.x + nx * probe, ship.y + ny * probe, true);
            }
        }
        return impact;
    }

    private static double[] fallbackImpactNormal(Ship ship,
                                                 double impactVx,
                                                 double impactVy,
                                                 double hitX,
                                                 double hitY) {
        if (Double.isFinite(impactVx) && Double.isFinite(impactVy)) {
            double len = Math.hypot(impactVx, impactVy);
            if (len > 1e-9) {
                return new double[]{-impactVx / len, -impactVy / len};
            }
        }
        double dx = hitX - ship.x;
        double dy = hitY - ship.y;
        double len = Math.hypot(dx, dy);
        if (len > 1e-9) return new double[]{dx / len, dy / len};
        return new double[]{1.0, 0.0};
    }

    private record BeamHitCandidate(Ship ship, double t) {}

    private record BeamImpactResult(double hitX, double hitY, double impactFraction) {}

    private record ImpactVisualPoints(double hullX, double hullY, double shieldX, double shieldY) {}

    static int scaleDamage(GameContext ctx, int damage) {
        if (damage <= 0) return 0;
        double multiplier = (ctx == null || ctx.experience == null) ? 1.0 : ctx.experience.combatLethality;
        return Math.max(1, (int) Math.round(damage * multiplier));
    }

    private static double segmentParamForPoint(double ax, double ay, double bx, double by, double px, double py) {
        double dx = bx - ax;
        double dy = by - ay;
        double denom = dx * dx + dy * dy;
        if (denom <= 1e-9) return 0.0;
        double t = ((px - ax) * dx + (py - ay) * dy) / denom;
        return Math.max(0.0, Math.min(1.0, t));
    }

    private static double segmentPointDistanceSq(double ax, double ay, double bx, double by, double px, double py) {
        double t = segmentParamForPoint(ax, ay, bx, by, px, py);
        double cx = ax + (bx - ax) * t;
        double cy = ay + (by - ay) * t;
        double dx = px - cx;
        double dy = py - cy;
        return dx * dx + dy * dy;
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

    private static boolean canProjectileDamageShip(GameContext ctx, Ship shooter, Projectile projectile, Ship target) {
        if (projectile == null || target == null) return false;
        Faction sourceFaction = (shooter != null && shooter.faction != null) ? shooter.faction : projectile.faction;
        if (sourceFaction != null && target.faction != null && sourceFaction.isFriendlyTo(target.faction)) return false;
        if (shooter != null && !CampaignSystem.missionSubzonesAllowDirectFire(ctx, shooter, target)) {
            return false;
        }
        boolean interceptorMissile = projectile instanceof Missile missile
                && missile.role == Turret.MissileRole.INTERCEPT;
        if (projectile instanceof CIWSPellet || projectile instanceof PointDefenseLaser) {
            return target.isSmallCraft();
        }
        if (TargetingSystem.isCiwsOnlyTarget(target) && !interceptorMissile) return false;

        if (shooter == null || shooter.role == null) return true;
        if (isCapitalShip(shooter.role) && target.isSmallCraft() && !interceptorMissile) return false;
        return true;
    }

    private static HullGeometry.ImpactSample firstBeamHullImpact(Ship ship,
                                                                 double sx,
                                                                 double sy,
                                                                 double ex,
                                                                 double ey,
                                                                 double fallbackT) {
        if (ship == null) return null;

        double prevT = 0.0;
        boolean prevInside = false;
        int steps = 72;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double wx = sx + (ex - sx) * t;
            double wy = sy + (ey - sy) * t;
            HullGeometry.ImpactSample sample = HullGeometry.sampleImpact(ship, wx, wy, false);
            boolean inside = sample != null && sample.onHull;
            if (inside && !prevInside) {
                double lo = prevT;
                double hi = t;
                for (int j = 0; j < 18; j++) {
                    double mid = 0.5 * (lo + hi);
                    double mx = sx + (ex - sx) * mid;
                    double my = sy + (ey - sy) * mid;
                    HullGeometry.ImpactSample midSample = HullGeometry.sampleImpact(ship, mx, my, false);
                    if (midSample != null && midSample.onHull) {
                        hi = mid;
                    } else {
                        lo = mid;
                    }
                }
                double fx = sx + (ex - sx) * hi;
                double fy = sy + (ey - sy) * hi;
                return HullGeometry.sampleImpact(ship, fx, fy, true);
            }
            prevInside = inside;
            prevT = t;
        }

        double wx = sx + (ex - sx) * MathUtil.clamp(fallbackT, 0.0, 1.0);
        double wy = sy + (ey - sy) * MathUtil.clamp(fallbackT, 0.0, 1.0);
        return HullGeometry.sampleImpact(ship, wx, wy, true);
    }

    private static Ship.InteriorHitProfile interiorHitProfileForProjectile(Ship shooter, Projectile projectile) {
        if (projectile == null) return Ship.InteriorHitProfile.DEFAULT;
        if (projectile instanceof EnergyBolt) return Ship.InteriorHitProfile.BLUE_PIERCE;
        if (projectile instanceof Missile) return Ship.InteriorHitProfile.MISSILE_BLAST;
        if (projectile instanceof PhaserBeam) return Ship.InteriorHitProfile.LASER_LINE;
        if (projectile instanceof Bullet && shooter != null) {
            DoctrineProfile profile = DoctrineRegistry.forFaction(shooter.faction);
            if (profile != null && profile.doctrine == Doctrine.KINETIC_CONSORTIUM) {
                return Ship.InteriorHitProfile.RED_EXPLOSIVE;
            }
        }
        return Ship.InteriorHitProfile.DEFAULT;
    }

    private static Ship resolveSourceShip(GameContext ctx, List<Ship> ships, Projectile projectile) {
        if (projectile == null) return null;
        return resolveSourceShip(ctx, ships, projectile.sourceShipId);
    }

    private static Ship resolveSourceShip(GameContext ctx, List<Ship> ships, int sourceShipId) {
        if (ctx != null) {
            Ship shooter = ctx.entityQuery.findShipById(sourceShipId);
            if (shooter != null) return shooter;
        }
        if (ships == null || sourceShipId <= 0) return null;
        for (Ship s : ships) {
            if (s != null && s.id == sourceShipId) return s;
        }
        return null;
    }

    private static boolean isCapitalShip(ShipRole role) {
        if (role == null) return false;
        return role.isCapitalCombatant() || role == ShipRole.CARRIER || role == ShipRole.DRONE_CARRIER
                || role == ShipRole.TRANSPORT;
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
