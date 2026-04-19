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

    // Phase 5.5 + 5.6: Blue main-battery guns lock their target and wait for the prior shot to resolve.
    // Runtime-only state (do not persist to campaign saves).
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
        // Clear target lock once the last shot has resolved (hit or despawned).
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
        double ox = effective.ecmObservedX(worldX(host), worldY(host));
        double oy = effective.ecmObservedY(worldX(host), worldY(host));
        aimAt(dt, host, ox, oy);
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

        double ex = effective.ecmObservedX(wx, wy);
        double ey = effective.ecmObservedY(wx, wy);
        double tvx = effective.vx / dt;
        double tvy = effective.vy / dt;

        double[] ip = MathUtil.interceptPoint(wx, wy, ex, ey, tvx, tvy, projectileSpeed);
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
            boolean blueMainBattery = (host.faction == Faction.PLAYER || host.faction == Faction.ALLY)
                    && prof.doctrine == Doctrine.ENERGY_NAVY
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
            // ENERGY_NAVY always uses the beam-bolt visual identity, with fire doctrine handled separately.
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
                if (host.faction == Faction.PLAYER || host.faction == Faction.ALLY) {
                    p.damageGrowthPerUnit = 0.005 / 100.0;
                    enablesDamageGrowth = true;
                }
                if (blueMainBattery) {
                    pendingBlueProjectile = p;
                    pendingBlueTarget = missileTarget;
                }
                return p;
            }
            double bulletRadius = 3.0;
            if (prof.doctrine == Doctrine.KINETIC_CONSORTIUM) {
                bulletRadius = Math.max(4.2, Math.min(6.6, radius * 0.62));
            }
            Projectile p = new Bullet(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, bulletRadius, host.faction);
            p.sourceShipId = host.id;
            // Phase 5.7: Blue bullets also get damage growth
            if (host.faction == Faction.PLAYER || host.faction == Faction.ALLY) {
                p.damageGrowthPerUnit = 0.003 / 100.0;
                enablesDamageGrowth = true;
            }
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
            double missileSpd_final = missileSpd;
            double missileTurn_final = missileTurn;
            int missileDamage_final = missileDamage;
            int missileLifetime_final = missileLifetime;

            // Phase 5: Missile subtypes (per turret slot).
            // These roles are tuned so missiles have a real place in medium/long-range standoff fights:
            // - INTERCEPT: fast, agile, low yield
            // - ANTI_LIGHT: fast-ish, good turn
            // - ANTI_MEDIUM: baseline
            // - ANTI_HEAVY: photon/torpedo feel, high yield, lower guidance agility
            MissileRole role = (missileRole == null) ? MissileRole.ANTI_MEDIUM : missileRole;
            double spdMul = 1.0;
            double turnMul = 1.0;
            double dmgMul = 1.0;
            double lifeMul = 1.0;
            double radMul = 1.0;
            double blastMul = 1.0;
            double splashMul = 0.60;
            int guidanceTicks = Missile.INFINITE_GUIDANCE_TICKS;
            boolean canRetarget = false;
            boolean preferSmallCraft = false;
            double retargetRange = 900.0;
            int interceptHpBonus = 0;

            switch (role) {
                case INTERCEPT -> {
                    dmgMul *= 0.72;
                    turnMul *= 2.35;
                    spdMul *= 1.52;
                    radMul *= 0.66;
                    blastMul *= 1.05;
                    splashMul = 0.28;
                    guidanceTicks = Math.max(1, (int) Math.round(7.0 / GameContext.DT));
                    canRetarget = true;
                    preferSmallCraft = true;
                    retargetRange = 980.0;
                }
                case ANTI_LIGHT -> {
                    dmgMul *= 0.74;
                    turnMul *= 1.18;
                    spdMul *= 1.42;
                    lifeMul *= 2.80;
                    radMul *= 0.92;
                    blastMul *= 0.84;
                    splashMul = 0.38;
                    canRetarget = true;
                    retargetRange = 12000.0;
                }
                case ANTI_MEDIUM -> {
                }
                case ANTI_HEAVY -> {
                    dmgMul *= 4.30;
                    turnMul *= 0.58;
                    spdMul *= 0.68;
                    lifeMul *= 0.92;
                    radMul *= 1.68;
                    blastMul *= 1.95;
                    splashMul = 0.95;
                    guidanceTicks = Math.max(1, (int) Math.round(2.8 / GameContext.DT));
                    interceptHpBonus = 2;
                }
            }

            if (host.faction == Faction.TEAM_C) {
                // Green missiles should feel like photon torpedoes: a little heavier, larger, and less twitchy.
                spdMul *= 0.97;
                turnMul *= 0.95;
                lifeMul *= 1.08;
                radMul *= 1.15;
                dmgMul *= 1.05;
            }

            missileSpd_final = missileSpd * spdMul;
            missileTurn_final = missileTurn * turnMul;
            missileDamage_final = (int) Math.round(missileDamage * dmgMul);
            missileDamage_final = Math.max(1, missileDamage_final);
            missileLifetime_final = Math.max(1, (int) Math.round(missileLifetime * lifeMul));
            if (role == MissileRole.INTERCEPT) {
                missileLifetime_final = Math.max(1, (int) Math.round(7.0 / GameContext.DT));
            }
            missileRadius = Math.max(6.0, missileRadius * radMul);
            
            Missile p = new Missile(mx, my, angle, missileTarget, dt, missileSpd_final, missileTurn_final, missileDamage_final, missileLifetime_final, missileRadius, host.faction);
            p.role = role;
            p.canRetarget = canRetarget;
            p.preferSmallCraft = preferSmallCraft;
            p.retargetRange = retargetRange;
            p.guidanceTicksRemaining = Math.min(guidanceTicks, missileLifetime_final);
            p.interceptHp += interceptHpBonus;
            p.blastRadius = Math.max(32.0, p.blastRadius * blastMul);
            p.splashDamageMul = Math.max(0.0, splashMul);
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
}
