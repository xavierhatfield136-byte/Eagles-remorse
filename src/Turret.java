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
    public static final double GLOBAL_MISSILE_LAUNCH_RATE_MULT = 0.50;
    public static final double GLOBAL_MISSILE_DAMAGE_OUTPUT_MULT = 2.00;
    // Baseline launch multiplier before faction doctrine is applied in Missile.
    public static final double MISSILE_SPEED_MULT = 0.90;
    public static final double BLUE_FAST_MISSILE_SPEED_MULT = 0.32;
    public static final double BLUE_FAST_MISSILE_MAX_PRE_FACTION_SPEED = 78.0;
    public static final double MISSILE_TURN_MULT = 1.32;
    public static final double MISSILE_LIFE_MULT = 1.22;
    // Tactical alignment: non-beam ordnance should cross the screen visibly instead of feeling nearly hitscan.
    public static final double GUN_PROJECTILE_SPEED_MULT = 0.84;

    public enum Kind {
        GUN,
        MISSILE
    }

    public enum MissileRole {
        INTERCEPT,      // Anti-missile, light interceptor role
        ANTI_LIGHT,     // Against fighters and small craft
        ANTI_MEDIUM,    // Against frigates and corvettes
        ANTI_HEAVY      // Against cruisers and capital ships
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
    
    // Phase 5.5: Targeting persistence - track last fired projectile for blue guns
    public int lastFiredProjectileId = -1;
    public Projectile lastFiredProjectile = null;  // For waiting logic
    // Phase 5.6: Fire timing - store world position we're currently targeting (for persistent aim after firing)
    public double persistentTargetX = Double.NaN;
    public double persistentTargetY = Double.NaN;
    private transient Projectile pendingBlueProjectile = null;
    private transient Ship pendingBlueTarget = null;
    // Phase 5.8: Barrel stagger timing
    public double barrelStaggerTimer = 0.0;
    public int barrelStaggerIndex = 0;
    private transient int beamVisualLaneCursor = 0;

    // Weapon stats
    public Kind kind;
    public int damage = 1;
    public double bulletSpeed = 750;
    public int bulletLife = 120;

    public double missileSpeed = 220;
    public double missileTurnRate = Math.toRadians(180);
    public int missileLife = 180;
    public MissileRole missileRole = MissileRole.ANTI_MEDIUM;  // Default missile role for loadout editing
    // Phase 5.7: Damage growth support flag
    public boolean enablesDamageGrowth = false;

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
        if (pendingBlueProjectile != null && !pendingBlueProjectile.alive) {
            pendingBlueProjectile = null;
            pendingBlueTarget = null;
        }
        if (pendingBlueTarget != null && (!pendingBlueTarget.alive || pendingBlueTarget.dying || pendingBlueTarget.hp <= 0)) {
            pendingBlueTarget = null;
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

    /**
     * Clears transient fire-lock state so a carried-over turret can start a new mission cleanly.
     */
    public void resetFireState() {
        coolLeft = 0;
        lastFiredProjectileId = -1;
        lastFiredProjectile = null;
        persistentTargetX = Double.NaN;
        persistentTargetY = Double.NaN;
        pendingBlueProjectile = null;
        pendingBlueTarget = null;
        barrelStaggerTimer = 0.0;
        barrelStaggerIndex = 0;
        beamVisualLaneCursor = 0;
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
        Ship effective = resolvePersistentTarget(target);
        if (effective == null) return;
        aimAt(dt, host, effective.x, effective.y);
    }

    /**
     * Aim the turret using predictive leading for a moving target.
     *
     * NOTE: This game stores vx/vy as per-tick deltas (already scaled by dt).
     * We convert back to per-second velocity by dividing by dt.
     */
    public void aimAtLead(double dt, Ship host, Ship target, double projectileSpeed) {
        Ship effective = resolvePersistentTarget(target);
        if (effective == null) return;
        if (dt <= 0) {
            aimAt(dt, host, effective);
            return;
        }

        double wx = worldX(host);
        double wy = worldY(host);

        double tvx = effective.vx / dt;
        double tvy = effective.vy / dt;

        double[] ip = MathUtil.interceptPoint(wx, wy, effective.x, effective.y, tvx, tvy, projectileSpeed);
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
        if (pendingBlueProjectile != null) {
            if (!pendingBlueProjectile.alive) {
                pendingBlueProjectile = null;
                pendingBlueTarget = null;
            } else {
                return false;
            }
        }
        return coolLeft <= 0;
    }

    private Ship resolvePersistentTarget(Ship requested) {
        if (pendingBlueProjectile == null || !pendingBlueProjectile.alive) return requested;
        if (pendingBlueTarget != null && pendingBlueTarget.alive && !pendingBlueTarget.dying && pendingBlueTarget.hp > 0) {
            return pendingBlueTarget;
        }
        return requested;
    }
    
    /**
     * Phase 5.6: Check if this turret should wait for its last projectile to resolve.
     * Blue non-missile turrets wait for their projectile to hit/despawn before firing again.
     */
    public boolean shouldWaitForLastProjectile(DoctrineProfile prof) {
        if (kind != Kind.GUN) return false;  // Missiles don't have this behavior
        if (prof == null || prof.doctrine != Doctrine.ENERGY_NAVY) return false;
        return true;
    }

    public Projectile fire(Ship host, Ship missileTarget, double dt) {
        return fire(host, missileTarget, null, dt);
    }

    public Projectile fire(Ship host, Ship missileTarget, Projectile projectileTarget, double dt) {
        if (!canFire()) return null;
        if (host == null) return null;
        if (!host.canUseCombatSystems()) return null;
        if (!TacticalCombatDepthSystem.canFireWeapon(host, this)) return null;
        if (!isWithinHullWeaponArc(host)) return null;
        if (!host.hasStrikeCraftMunitionsFor(this)) return null;

        DoctrineProfile prof = DoctrineRegistry.forFaction(host.faction);
        
        // Phase 5.6: Blue non-missile turrets wait for their prior projectile to resolve
        if (shouldWaitForLastProjectile(prof) && lastFiredProjectile != null && lastFiredProjectile.alive) {
            return null;
        }
        double cycleMul = host.weaponCycleRateMultiplier();
        double damageMul = host.weaponDamageMultiplier();
        if (kind == Kind.MISSILE) {
            cycleMul *= host.missileCycleRateMultiplier() * GLOBAL_MISSILE_LAUNCH_RATE_MULT;
            damageMul *= host.missileDamageMultiplier() * GLOBAL_MISSILE_DAMAGE_OUTPUT_MULT;
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
        damageMul *= Math.max(1.0, host.doctrineOffenseDamageMultiplier);
        cycleMul = Math.max(0.20, cycleMul);
        damageMul = Math.max(0.20, damageMul);
        host.consumeStrikeCraftMunition(this);

        host.onFire();
        TacticalCombatDepthSystem.onWeaponFired(host, this);

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
            double effectiveReloadSeconds = baseReloadSeconds;
            boolean blueMainBattery = prof.doctrine == Doctrine.ENERGY_NAVY
                    && !usesCiwsPelletsAgainst(host, this, missileTarget);
            if (blueMainBattery) {
                double flooredReload = Math.max(baseReloadSeconds, Ship.BLUE_MAIN_BATTERY_MIN_RELOAD_SECONDS);
                if (baseReloadSeconds > 1e-6) {
                    damageMul *= flooredReload / baseReloadSeconds;
                }
                effectiveReloadSeconds = flooredReload;
            } else {
                effectiveReloadSeconds = baseReloadSeconds;
            }
            coolLeft = effectiveReloadSeconds;
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
                projectileSpeed *= 1.03;
            }
            int gunDamage = host.resolveStrikeCraftWeaponDamage(this, damage * damageMul);
            if (host.faction == Faction.TEAM_C) {
                // Team C uses a persistent, tracking cutting beam.
                double shotInterval = Math.max(GameContext.DT, effectiveReloadSeconds);
                int beamLife = Math.max(2, (int) Math.round(shotInterval / GameContext.DT));
                double baseDps = gunDamage / shotInterval;
                double beamDps = baseDps * 1.08;
                double beamLength = greenBeamLength(host, missileTarget, projectileSpeed);
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
                boolean beamBoltVisual = host.usesBeamBoltPrimaryVisuals();
                int beamLaneCount = visualBeamLaneCount(host, this);
                int beamLaneIndex = (beamBoltVisual && host.usesStaggeredPrimaryFire() && beamLaneCount > 1)
                        ? nextBeamVisualLane(beamLaneCount)
                        : -1;
                Projectile p = new EnergyBolt(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, 4.5,
                        beamBoltVisual, beamLaneIndex, beamLaneCount, localX, localY, host.faction);
                p.sourceShipId = host.id;
                // Phase 5.7: Blue non-missile projectiles gain damage with flight distance
                // Growth is 0.5% per 100 units traveled, allowing slower cadence to still deal meaningful damage
                p.damageGrowthPerUnit = 0.005 / 100.0;
                enablesDamageGrowth = true;
                pendingBlueProjectile = p;
                pendingBlueTarget = missileTarget;
                return p;
            }
            double bulletRadius = 2.2;
            if (prof.doctrine == Doctrine.KINETIC_CONSORTIUM) {
                bulletRadius = Math.max(3.2, Math.min(5.0, radius * 0.48));
            }
            Projectile p = new Bullet(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, bulletRadius, host.faction);
            p.sourceShipId = host.id;
            // Phase 5.7: Blue bullets also get damage growth
            if (prof.doctrine == Doctrine.ENERGY_NAVY || host.faction == Faction.PLAYER || host.faction == Faction.ALLY) {
                p.damageGrowthPerUnit = 0.003 / 100.0;
                enablesDamageGrowth = true;
            }
            lastFiredProjectile = p;
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
            if (missileRole == MissileRole.INTERCEPT) {
                flooredReload = Math.max(Ship.MISSILE_MIN_RELOAD_SECONDS / 3.0, flooredReload / 3.0);
            }
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
            double missileRadius = Math.max(4.8, radius * 0.82);
            double missileSpd_final = missileSpd;
            double missileTurn_final = missileTurn;
            int missileDamage_final = missileDamage;
            int missileLifetime_final = missileLifetime;
            double missileVisualScale = 1.0;
            
            // Phase 5.3: Blue torpedo sidegrade
            // Anti-heavy torpedoes have higher yield but lower guidance time (less agile)
            // Best against slower targets like cruisers and capital ships
            if (host.faction == Faction.PLAYER || host.faction == Faction.ALLY) {
                if (missileRole == MissileRole.ANTI_HEAVY) {
                    // High-yield torpedo: +35% damage, but -40% turn rate (less responsive guidance)
                    missileDamage_final = (int) Math.round(missileDamage * 1.35);
                    missileTurn_final = missileTurn * 0.60;  // Slower, less agile turn
                    missileSpd_final = missileSpd * 0.92;  // Slightly slower for better hit chance
                } else if (missileRole == MissileRole.ANTI_LIGHT) {
                    missileSpd_final = missileSpd * BLUE_FAST_MISSILE_SPEED_MULT;
                    missileSpd_final = Math.min(missileSpd_final, BLUE_FAST_MISSILE_MAX_PRE_FACTION_SPEED);
                } else if (missileRole == MissileRole.INTERCEPT) {
                    // Interceptor variant: lighter, faster, turns harder
                    missileDamage_final = (int) Math.round(missileDamage * 0.75);
                    missileTurn_final = missileTurn * 1.25;
                    missileSpd_final = missileSpd * 1.18;
                }
            }

            if (missileRole == MissileRole.ANTI_LIGHT) {
                missileLifetime_final = Math.max(missileLifetime_final, (int) Math.round(missileLifetime * 3.0));
            } else if (missileRole == MissileRole.ANTI_HEAVY) {
                missileLifetime_final = Math.max(missileLifetime_final, (int) Math.round(missileLifetime * 2.0));
                missileVisualScale = 0.5;
            } else if (missileRole == MissileRole.INTERCEPT) {
                missileLifetime_final = Math.max(missileLifetime_final, (int) Math.round(missileLifetime * 1.5));
                missileVisualScale = 0.5;
            }

            Projectile p = new Missile(mx, my, angle, missileTarget, dt, missileSpd_final, missileTurn_final, missileDamage_final, missileLifetime_final, missileRadius, host.faction);
            p.sourceShipId = host.id;
            if (p instanceof Missile missile) {
                missile.role = (missileRole == null) ? MissileRole.ANTI_MEDIUM : missileRole;
                missile.applyRoleSpeedCap(missile.role, dt);
                missile.projectileTarget = projectileTarget;
                missile.visualScale = missileVisualScale;
                if (missile.role == MissileRole.ANTI_LIGHT) {
                    missile.canRetarget = true;
                    missile.retargetRange = Math.max(missile.retargetRange, 2200.0);
                } else if (missile.role == MissileRole.INTERCEPT) {
                    missile.canRetarget = true;
                    missile.preferSmallCraft = true;
                    missile.retargetRange = Math.max(missile.retargetRange, 1400.0);
                }
            }
            return p;
        }
    }

    static double greenBeamLength(Ship host, Ship target, double projectileSpeed) {
        double floor = (host == null || target == null) ? 300.0 : host.radius + target.radius + 190.0;
        return Math.max(floor, AISystem.STANDARD_PROSECUTION_RANGE);
    }

    private boolean isWithinHullWeaponArc(Ship host) {
        if (host == null) return false;
        if (host instanceof Player && host.role == ShipRole.ARTILLERY_SHIP) {
            return true;
        }
        double relative = Math.abs(MathUtil.normalizeAngle(angle - host.angle));
        if (host.role == ShipRole.ARTILLERY_SHIP || host.role == ShipRole.ARTILLERY_TITAN) {
            return relative <= Math.toRadians(18.0);
        }
        if (isBroadsideHull(host.role) && Math.abs(localY) > Math.abs(localX) * 0.72) {
            double side = localY < 0.0 ? -Math.PI / 2.0 : Math.PI / 2.0;
            return Math.abs(MathUtil.normalizeAngle(angle - host.angle - side)) <= Math.toRadians(72.0);
        }
        return true;
    }

    private static boolean isBroadsideHull(ShipRole role) {
        return role == ShipRole.BULWARK_TITAN
                || role == ShipRole.SHIELD_BASTION_TITAN;
    }

    private int nextBeamVisualLane(int laneCount) {
        int safeCount = Math.max(1, laneCount);
        int lane = Math.floorMod(beamVisualLaneCursor, safeCount);
        beamVisualLaneCursor = lane + 1;
        return lane;
    }

    private static int visualBeamLaneCount(Ship host, Turret turret) {
        if (host == null || turret == null) return 1;
        if (turret.kind != Kind.GUN) return 1;
        if (!host.usesBeamBoltPrimaryVisuals()) return 1;
        if (host.role == ShipRole.STEALTH_SHIP) return 1;
        return 3;
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
