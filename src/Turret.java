/**
 * A simple turret/hardpoint attached to a ship.
 *
 * Turrets are modeled visually and can fire either bullets or missiles.
 * For bullets, they fire toward a target point.
 * For missiles, they fire at a target ship.
 */
public class Turret {

    // Universal missile buff (applies to all factions/ships).
    public static final double MISSILE_DAMAGE_MULT = 1.55;
    public static final double MISSILE_SPEED_MULT = 1.40;
    public static final double MISSILE_TURN_MULT = 1.32;
    public static final double MISSILE_LIFE_MULT = 1.22;
    public static final double GUN_PROJECTILE_SPEED_MULT = 1.18;

    public enum Kind {
        GUN,
        MISSILE
    }

    // Local offset from ship center (in ship-local coordinates)
    public final double localX;
    public final double localY;

    // Current turret angle (world radians)
    public double angle;

    // How fast the turret can rotate (rad/sec)
    public double turnRate = Math.toRadians(240);

    // Fire control
    public double cooldown = 0.15;      // seconds between shots
    private double coolLeft = 0;

    // Weapon stats
    public Kind kind;
    public int damage = 1;
    public double bulletSpeed = 750;
    public int bulletLife = 120;

    public double missileSpeed = 220;
    public double missileTurnRate = Math.toRadians(180);
    public int missileLife = 180;

    // Render
    public double radius = 6;
    public double barrelLen = 14;

    public boolean primary = true; // primary fire if true, secondary if false

    public Turret(Kind kind, double localX, double localY) {
        this.kind = kind;
        this.localX = localX;
        this.localY = localY;
    }

    public void update(double dt) {
        if (coolLeft > 0) {
            coolLeft -= dt;
            if (coolLeft < 0) coolLeft = 0;
        }
    }

    /** Useful for transports/resupply: reduce current cooldown timer. */
    public void reduceCooldown(double seconds) {
        if (seconds <= 0) return;
        coolLeft -= seconds;
        if (coolLeft < 0) coolLeft = 0;
    }

    public void setReady() {
        coolLeft = 0;
    }

    public double getCooldownRemaining() {
        return coolLeft;
    }

    /** Aim the turret toward a target point in world-space. */
    public void aimAt(double dt, Ship host, double targetX, double targetY) {
        double wx = worldX(host);
        double wy = worldY(host);
        double desired = Math.atan2(targetY - wy, targetX - wx);
        rotateToward(dt, desired);
    }

    /** Aim the turret toward a target ship. */
    public void aimAt(double dt, Ship host, Ship target) {
        if (target == null) return;
        aimAt(dt, host, target.x, target.y);
    }

    /**
     * Aim the turret using predictive leading for a moving target.
     *
     * NOTE: This game stores vx/vy as per-tick deltas (already scaled by dt).
     * We convert back to per-second velocity by dividing by dt.
     */
    public void aimAtLead(double dt, Ship host, Ship target, double projectileSpeed) {
        if (target == null) return;
        if (dt <= 0) {
            aimAt(dt, host, target);
            return;
        }

        double wx = worldX(host);
        double wy = worldY(host);

        double tvx = target.vx / dt;
        double tvy = target.vy / dt;

        double[] ip = MathUtil.interceptPoint(wx, wy, target.x, target.y, tvx, tvy, projectileSpeed);
        aimAt(dt, host, ip[0], ip[1]);
    }

    public static double effectiveGunProjectileSpeed(Turret t) {
        if (t == null) return 0.0;
        return t.bulletSpeed * GUN_PROJECTILE_SPEED_MULT;
    }

    public static boolean usesCiwsPelletsAgainst(Ship host, Turret turret, Ship target) {
        if (host == null || turret == null || target == null) return false;
        if (turret.kind != Kind.GUN) return false;
        if (host.role != ShipRole.FIGHTER && host.role != ShipRole.DRONE) return false;
        return target.isSmallCraft();
    }

    public static double effectiveInterceptorProjectileSpeed(Ship host, Turret turret) {
        if (host == null || turret == null) return 0.0;
        double gunSpeed = effectiveGunProjectileSpeed(turret);
        double ciwsSpeed = Math.max(1.0, host.ciwsPelletSpeed);
        return Math.max(ciwsSpeed, gunSpeed * 0.92);
    }

    private void rotateToward(double dt, double desired) {
        double delta = MathUtil.normalizeAngle(desired - angle);
        double max = turnRate * dt;
        delta = MathUtil.clamp(delta, -max, max);
        angle = MathUtil.normalizeAngle(angle + delta);
    }

    public boolean canFire() {
        return coolLeft <= 0;
    }

    public Projectile fire(Ship host, Ship missileTarget, double dt) {
        if (!canFire()) return null;
        if (host == null) return null;
        if (!host.canUseCombatSystems()) return null;
        if (!host.hasStrikeCraftMunitionsFor(this)) return null;

        DoctrineProfile prof = DoctrineRegistry.forFaction(host.faction);
        double cycleMul = host.weaponCycleRateMultiplier();
        double damageMul = host.weaponDamageMultiplier();
        if (kind == Kind.MISSILE) {
            cycleMul *= host.missileCycleRateMultiplier();
            damageMul *= host.missileDamageMultiplier();
        }
        if (kind == Kind.GUN && prof.doctrine == Doctrine.KINETIC_CONSORTIUM) {
            // Red favors fewer, heavier shells instead of gun spam.
            cycleMul *= 0.78;
            damageMul *= 1.28;
        } else if (kind == Kind.MISSILE && prof.doctrine == Doctrine.MISSILE_BARRAGE) {
            // Team D should feel like a true long-range barrage faction.
            cycleMul *= 0.90;
            damageMul *= 1.18;
        }
        cycleMul = Math.max(0.20, cycleMul);
        damageMul = Math.max(0.20, damageMul);
        host.consumeStrikeCraftMunition(this);

        host.onFire();

        // Spawn at turret muzzle
        double mx = worldX(host) + Math.cos(angle) * (radius + 4);
        double my = worldY(host) + Math.sin(angle) * (radius + 4);

        // Cosmetic muzzle flash (skip for CIWS pellet spam - performance optimization)
        boolean isCiwsFiring = kind == Kind.GUN && usesCiwsPelletsAgainst(host, this, missileTarget);
        if (!isCiwsFiring) {
            VFX.spawnMuzzleFlash(mx, my, angle, kind == Kind.MISSILE);
        }

        if (kind == Kind.GUN) {
            double baseReloadSeconds = cooldown / cycleMul;
            boolean blueMainBattery = prof.doctrine == Doctrine.ENERGY_NAVY
                    && !usesCiwsPelletsAgainst(host, this, missileTarget);
            if (blueMainBattery) {
                double flooredReload = Math.max(baseReloadSeconds, Ship.BLUE_MAIN_BATTERY_MIN_RELOAD_SECONDS);
                if (baseReloadSeconds > 1e-6) {
                    damageMul *= flooredReload / baseReloadSeconds;
                }
                coolLeft = flooredReload;
            } else {
                coolLeft = baseReloadSeconds;
            }
            if (usesCiwsPelletsAgainst(host, this, missileTarget)) {
                double pelletSpeed = effectiveInterceptorProjectileSpeed(host, this);
                int pelletDamage = Math.max(1, host.resolveStrikeCraftWeaponDamage(this, damage * damageMul));
                int pelletLife = Math.max(8, Math.min(bulletLife, host.ciwsPelletLife > 0 ? host.ciwsPelletLife : bulletLife));
                double pelletRadius = Math.max(1.8, Math.min(2.6, radius * 0.42));
                Projectile pellet = new CIWSPellet(mx, my, angle, dt, pelletSpeed, pelletDamage, pelletLife, pelletRadius, host.faction);
                pellet.sourceShipId = host.id;
                return pellet;
            }
            // Doctrine-based main projectile style.
            // ENERGY_NAVY uses a Yamato 2199-style heavy energy bolt (visible, medium speed).
            // KINETIC_CONSORTIUM uses the existing fast conventional rounds.
            double projectileSpeed = bulletSpeed * GUN_PROJECTILE_SPEED_MULT;
            if (prof.doctrine == Doctrine.KINETIC_CONSORTIUM) {
                projectileSpeed *= 1.06;
            }
            int gunDamage = host.resolveStrikeCraftWeaponDamage(this, damage * damageMul);
            if (host.faction == Faction.TEAM_C) {
                // Team C uses a persistent, tracking cutting beam.
                double shotInterval = Math.max(GameContext.DT, cooldown / cycleMul);
                int beamLife = Math.max(2, (int) Math.round(shotInterval / GameContext.DT));
                double baseDps = gunDamage / shotInterval;
                double beamDps = baseDps * 1.08;
                double beamLength = Math.max(2400.0, projectileSpeed * 2.75);
                double beamWidth = Math.max(4.5, radius * 0.95);
                PhaserBeam p = new PhaserBeam(
                        host,
                        this,
                        angle,
                        beamLength,
                        beamWidth,
                        beamDps,
                        beamLife,
                        host.faction
                );
                p.sourceShipId = host.id;
                return p;
            }
            if (prof.doctrine == Doctrine.ENERGY_NAVY) {
                Projectile p = new EnergyBolt(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, 4.5,
                        localX, localY, host.faction);
                p.sourceShipId = host.id;
                return p;
            }
            double bulletRadius = 3.0;
            if (prof.doctrine == Doctrine.KINETIC_CONSORTIUM) {
                bulletRadius = Math.max(4.2, Math.min(6.6, radius * 0.62));
            }
            Projectile p = new Bullet(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, bulletRadius, host.faction);
            p.sourceShipId = host.id;
            return p;
        } else {
            double missileBaseDamage = damage * damageMul;
            if (!host.usesLimitedStrikeCraftMunitions()) {
                missileBaseDamage *= MISSILE_DAMAGE_MULT;
            }
            if (prof.doctrine == Doctrine.MISSILE_BARRAGE) {
                missileBaseDamage *= 1.18;
            }
            double baseReloadSeconds = cooldown / cycleMul;
            double flooredReload = Math.max(baseReloadSeconds, Ship.MISSILE_MIN_RELOAD_SECONDS);
            if (baseReloadSeconds > 1e-6) {
                damageMul *= flooredReload / baseReloadSeconds;
            }
            coolLeft = flooredReload;
            int missileDamage = host.resolveStrikeCraftWeaponDamage(this, missileBaseDamage);
            double missileSpd = missileSpeed * MISSILE_SPEED_MULT;
            double missileTurn = missileTurnRate * MISSILE_TURN_MULT;
            int missileLifetime = Math.max(1, (int) Math.round(missileLife * MISSILE_LIFE_MULT));
            if (prof.doctrine == Doctrine.MISSILE_BARRAGE) {
                missileSpd *= 1.18;
                missileTurn *= 1.08;
                missileLifetime = Math.max(missileLifetime, (int) Math.round(missileLife * 1.40));
            }
            double missileRadius = Math.max(6.0, radius);
            Projectile p = new Missile(mx, my, angle, missileTarget, dt, missileSpd, missileTurn, missileDamage, missileLifetime, missileRadius, host.faction);
            p.sourceShipId = host.id;
            return p;
        }
    }

    public double worldX(Ship host) {
        double ca = Math.cos(host.angle);
        double sa = Math.sin(host.angle);
        return host.x + localX * ca - localY * sa;
    }

    public double worldY(Ship host) {
        double ca = Math.cos(host.angle);
        double sa = Math.sin(host.angle);
        return host.y + localX * sa + localY * ca;
    }
}
