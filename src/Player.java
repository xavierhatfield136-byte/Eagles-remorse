import java.util.ArrayList;
import java.util.List;

/**
 * Player-controlled ship.
 *
 * The player ship uses the same turret/system model as NPC ships.
 */
public class Player extends Ship {
    private static final int ALT_TEAM_SECONDARY_BURST_COUNT = 3;
    private static final double ALT_TEAM_SECONDARY_BURST_SPREAD = 0.055;
    private static final double ALT_TEAM_SECONDARY_BURST_TRAIL_SPACING = 11.0;

    // Abilities
    private double shieldOverchargeCooldown = 2.0;
    private double shieldOverchargeTimer = 0;
    private double overShieldTimer = 0;
    private double overShieldAdded = 0;

    public Player(double x, double y) {
        this(ShipRole.FRIGATE, x, y);
    }

    public Player(ShipRole role, double x, double y) {
        applyHull(role, x, y);
        name = "Player";
        faction = Faction.PLAYER;
    }

    /** Rebuild the player stats/turrets from a role template (used by loadouts). */
    public void applyHull(ShipRole role, double x, double y) {
        Faction preservedFaction = (this.faction == null) ? Faction.PLAYER : this.faction;
        this.x = x;
        this.y = y;
        this.role = role;

        // Build from the active team doctrine so hull-swaps keep team identity.
        Faction templateFaction = (preservedFaction == Faction.PLAYER) ? Faction.ALLY : preservedFaction;
        FleetShip template = new FleetShip(role, templateFaction, x, y);
        try { DoctrineRegistry.applyToShip(template); } catch (Throwable ignored) {}
        copyFrom(template);

        // Give the player a baseline mining module on any combat hull.
        // (Dedicated MINER/HAULER roles are still better.)
        if (this.cargoMax <= 0) this.cargoMax = 120;
        if (this.miningRate <= 0) this.miningRate = 10.0;
        if (this.miningRange <= 0) this.miningRange = 56.0;

        // New hull means old impact/breach marks are no longer valid.
        clearHullImpactMarks();

        // Keep whichever team the player is currently assigned to.
        this.faction = preservedFaction;
    }

    @Override
    public void update(double dt) {
        super.update(dt);

        if (shieldOverchargeTimer > 0) {
            shieldOverchargeTimer -= dt;
            if (shieldOverchargeTimer < 0) shieldOverchargeTimer = 0;
        }

        if (overShieldTimer > 0) {
            overShieldTimer -= dt;
            if (overShieldTimer <= 0) {
                // Remove temporary overshield
                if (overShieldAdded > 0) {
                    shieldMax = Math.max(0, shieldMax - overShieldAdded);
                    shield = Math.min(shield, shieldMax);
                    overShieldAdded = 0;
                }
                overShieldTimer = 0;
            }
        }
    }

    /** Ability: instantly boosts shields and grants a short overshield. */
    public boolean tryShieldOvercharge() {
        if (!alive) return false;
        if (!isShieldOnline()) return false;
        if (shieldOverchargeTimer > 0) return false;

        shieldOverchargeTimer = shieldOverchargeCooldown;

        // Grant an overshield once (don't stack)
        if (overShieldTimer <= 0) {
            overShieldAdded = Math.max(14, shieldMax * 0.30);
            shieldMax += overShieldAdded;
            shield += overShieldAdded;
            overShieldTimer = 4.0;
        }

        // Also instantly refill some shield
        shield = Math.min(shieldMax, shield + Math.max(18, shieldMax * 0.25));
        return true;
    }

    public double getShieldOverchargeRemaining() { return shieldOverchargeTimer; }
    public double getOverShieldRemaining() { return overShieldTimer; }

    /**
     * Loadout helper: add a gun turret to the hull at a reasonable next slot.
     */
    public void addGunTurret() {
        int idx = (int) turrets.stream().filter(t -> t.kind == Turret.Kind.GUN).count();
        double sx = Math.max(6, radius * 0.55);
        double sy = switch (idx % 3) {
            case 0 -> -radius * 0.35;
            case 1 -> 0;
            default -> radius * 0.35;
        };

        Turret gun = new Turret(Turret.Kind.GUN, sx, sy);
        gun.primary = true;
        gun.cooldown = 0.16;
        gun.damage = 1;
        gun.bulletSpeed = 780;
        gun.bulletLife = 120;
        addTurret(gun);
    }

    /**
     * Loadout helper: add a missile launcher turret.
     */
    public void addMissileTurret() {
        int idx = (int) turrets.stream().filter(t -> t.kind == Turret.Kind.MISSILE).count();
        double sx = radius * 0.35;
        double sy = (idx % 2 == 0) ? radius * 0.30 : -radius * 0.30;

        Turret rack = new Turret(Turret.Kind.MISSILE, sx, sy);
        rack.primary = false;
        rack.cooldown = 1.0;
        rack.damage = 3;
        rack.missileSpeed = 240;
        rack.missileTurnRate = Math.toRadians(220);
        rack.missileLife = 210;
        rack.radius = 7;
        rack.barrelLen = 10;
        addTurret(rack);
    }

    public boolean isCIWSUpgradeMaxed() {
        if (!hasCIWS) return false;
        return ciwsQuality >= (1.0 - 1e-9)
                && ciwsRange >= (380.0 - 1e-9)
                && ciwsPelletsPerBurst >= 8
                && ciwsCooldown <= (0.04 + 1e-9);
    }

    public boolean upgradeCIWS() {
        if (!hasCIWS) return false;
        if (isCIWSUpgradeMaxed()) return false;
        ciwsQuality = Math.min(1.0, ciwsQuality + 0.20);
        ciwsRange = Math.min(380, ciwsRange + 25);
        ciwsPelletsPerBurst = Math.min(8, ciwsPelletsPerBurst + 1);
        ciwsCooldown = Math.max(0.04, ciwsCooldown - 0.01);
        return true;
    }

    private void copyFrom(FleetShip t) {
        this.role = t.role;
        this.radius = t.radius;
        this.hpMax = t.hpMax;
        this.hp = t.hp;
        this.shieldMax = t.shieldMax;
        this.shield = t.shield;
        this.shieldRegen = t.shieldRegen;
        this.shieldActive = t.shieldActive;
        this.shieldRebootDelay = t.shieldRebootDelay;
        this.shieldFacingMode = t.shieldFacingMode;
        this.shieldFacingAngle = t.shieldFacingAngle;
        this.shieldAutoTrackRate = t.shieldAutoTrackRate;
        this.shieldDirectionalArc = t.shieldDirectionalArc;
        this.resetShieldState();
        this.desiredSpeed = t.desiredSpeed;
        this.desiredSpeedBase = (t.desiredSpeedBase > 0.0) ? t.desiredSpeedBase : t.desiredSpeed;
        this.powerPreset = t.powerPreset;
        double[] buses = t.powerBusFractions();
        this.setPowerBusAllocation(buses[0], buses[1], buses[2], buses[3], buses[4], buses[5]);
        this.setEngineeringPriority(t.engineeringPriority());
        this.setOverloadBus(t.overloadBus());
        this.setOverloadMode(false);
        this.crewOrder = t.crewOrder;

        this.cargo = t.cargo;
        this.cargoMax = t.cargoMax;
        this.miningRate = t.miningRate;
        this.miningRange = t.miningRange;
        this.isStealth = t.isStealth;
        this.signature = t.signature;
        this.revealTimer = t.revealTimer;
        this.cloakEnabled = t.cloakEnabled;
        this.cloakActive = t.cloakActive;
        this.cloakEnergyMax = t.cloakEnergyMax;
        this.cloakEnergy = t.cloakEnergy;
        this.cloakDrainPerSec = t.cloakDrainPerSec;
        this.cloakRechargePerSec = t.cloakRechargePerSec;
        this.cloakMinEnergyToEngage = t.cloakMinEnergyToEngage;
        this.cloakSignature = t.cloakSignature;
        this.resetInternalSystems();

        this.hasCIWS = t.hasCIWS;
        this.ciwsRange = t.ciwsRange;
        this.ciwsCooldown = t.ciwsCooldown;
        this.ciwsQuality = t.ciwsQuality;
        this.ciwsPelletsPerBurst = t.ciwsPelletsPerBurst;
        this.ciwsPelletSpeed = t.ciwsPelletSpeed;
        this.ciwsPelletDamage = t.ciwsPelletDamage;
        this.ciwsPelletLife = t.ciwsPelletLife;
        this.ciwsPelletRadius = t.ciwsPelletRadius;

        this.hasSuperweapon = t.hasSuperweapon;
        this.superweaponPattern = t.superweaponPattern;
        this.superweaponChargeTime = t.superweaponChargeTime;
        this.superweaponCooldown = t.superweaponCooldown;
        this.superweaponDamage = t.superweaponDamage;
        this.superweaponSpeed = t.superweaponSpeed;
        this.superweaponLife = t.superweaponLife;
        this.superweaponRadius = t.superweaponRadius;
        this.superweaponMaxHits = t.superweaponMaxHits;
        this.superweaponBeamDuration = t.superweaponBeamDuration;
        this.superweaponBeamTickInterval = t.superweaponBeamTickInterval;
        this.superweaponBeamDamageScale = t.superweaponBeamDamageScale;
        this.resetSuperweaponCooldown();

        this.isCarrier = t.isCarrier;
        this.fighterLaunchCooldown = t.fighterLaunchCooldown;
        this.maxFighters = t.maxFighters;
        this.carrierCommandMode = t.carrierCommandMode;
        this.carrierAutoLaunch = t.carrierAutoLaunch;
        this.wingState = Ship.WingState.ATTACK;
        this.carrierOwnerId = -1;
        this.carrierOrphanTimer = -1.0;
        this.flightDeckLaunchCursor = t.flightDeckLaunchCursor;
        for (int i = 0; i < this.flightDeckLoadout.length; i++) {
            this.flightDeckLoadout[i] = t.flightDeckRoleAt(i);
        }
        this.strikePrimaryMunitionsMax = t.strikePrimaryMunitionsMax;
        this.strikePrimaryMunitions = t.strikePrimaryMunitions;
        this.strikeSecondaryMunitionsMax = t.strikeSecondaryMunitionsMax;
        this.strikeSecondaryMunitions = t.strikeSecondaryMunitions;

        this.isBase = false;

        this.turrets.clear();
        for (Turret turret : t.turrets) {
            Turret nt = new Turret(turret.kind, turret.localX, turret.localY);
            nt.turnRate = turret.turnRate;
            nt.cooldown = turret.cooldown;
            nt.damage = turret.damage;
            nt.bulletSpeed = turret.bulletSpeed;
            nt.bulletLife = turret.bulletLife;
            nt.missileSpeed = turret.missileSpeed;
            nt.missileTurnRate = turret.missileTurnRate;
            nt.missileLife = turret.missileLife;
            nt.radius = turret.radius;
            nt.barrelLen = turret.barrelLen;
            nt.primary = turret.primary;
            this.turrets.add(nt);
        }
    }

    public List<Projectile> firePrimary(double targetX, double targetY, double dt) {
        List<Projectile> out = new ArrayList<>();
        boolean fired = false;
        aimPrimaryTurretsAt(targetX, targetY, dt);
        for (Turret t : turrets) {
            if (!t.primary) continue;
            Projectile p;
            if (t.kind == Turret.Kind.GUN) {
                p = t.fire(this, null, dt);
            } else if (t.kind == Turret.Kind.MISSILE) {
                // Manual primary missile fire uses turret/cursor aim and launches unguided when no lock is provided.
                p = t.fire(this, null, dt);
            } else {
                continue;
            }
            if (p != null) {
                out.add(p);
                fired = true;
            }
        }
        if (fired) onFiredWeapon();
        return out;
    }

    /**
     * Primary fire using predictive leading on a moving target.
     * This is used for player auto-aim / target lock.
     */
    public List<Projectile> firePrimary(Ship target, double dt) {
        if (target == null) return new ArrayList<>();
        List<Projectile> out = new ArrayList<>();
        boolean fired = false;
        aimPrimaryTurretsAtTarget(target, dt);
        for (Turret t : turrets) {
            if (!t.primary) continue;
            Projectile p;
            if (t.kind == Turret.Kind.GUN) {
                p = t.fire(this, target, dt);
            } else if (t.kind == Turret.Kind.MISSILE) {
                p = t.fire(this, target, dt);
            } else {
                continue;
            }
            if (p != null) {
                out.add(p);
                fired = true;
            }
        }
        if (fired) onFiredWeapon();
        return out;
    }

    public List<Projectile> fireSecondary(Ship target, double dt) {
        List<Projectile> out = new ArrayList<>();
        boolean fired = false;
        aimMissileTurretsAtTarget(target, dt);
        for (Turret t : turrets) {
            if (t.primary) continue;
            if (t.kind != Turret.Kind.MISSILE) continue;
            if (target == null) continue;
            Projectile p = t.fire(this, target, dt);
            if (p != null) {
                out.add(p);
                if (p instanceof Missile missile) {
                    spawnSecondaryBurstFollowers(out, missile, target, dt);
                }
                fired = true;
            }
        }
        if (fired) onFiredWeapon();
        return out;
    }

    public void aimPrimaryTurretsAt(double targetX, double targetY, double dt) {
        for (Turret t : turrets) {
            if (t == null) continue;
            if (!t.primary) continue;
            if (t.kind == Turret.Kind.GUN || t.kind == Turret.Kind.MISSILE) {
                t.aimAt(dt, this, targetX, targetY);
            }
        }
    }

    public void aimGunTurretsAtTarget(Ship target, double dt) {
        if (target == null) return;
        for (Turret t : turrets) {
            if (t == null) continue;
            if (t.kind != Turret.Kind.GUN) continue;
            if (Turret.usesCiwsPelletsAgainst(this, t, target)) {
                t.aimAtLead(dt, this, target, Turret.effectiveInterceptorProjectileSpeed(this, t));
            } else if (faction == Faction.TEAM_C) {
                t.aimAt(dt, this, target);
            } else {
                t.aimAtLead(dt, this, target, Turret.effectiveGunProjectileSpeed(t));
            }
        }
    }

    public void aimMissileTurretsAtTarget(Ship target, double dt) {
        if (target == null) return;
        for (Turret t : turrets) {
            if (t == null) continue;
            if (t.kind != Turret.Kind.MISSILE) continue;
            t.aimAt(dt, this, target);
        }
    }

    public void aimAllTurretsAtTarget(Ship target, double dt) {
        if (target == null) return;
        aimPrimaryTurretsAtTarget(target, dt);
        aimMissileTurretsAtTarget(target, dt);
    }

    private void aimPrimaryTurretsAtTarget(Ship target, double dt) {
        if (target == null) return;
        for (Turret t : turrets) {
            if (t == null || !t.primary) continue;
            if (t.kind == Turret.Kind.GUN) {
                if (Turret.usesCiwsPelletsAgainst(this, t, target)) {
                    t.aimAtLead(dt, this, target, Turret.effectiveInterceptorProjectileSpeed(this, t));
                } else if (faction == Faction.TEAM_C) {
                    t.aimAt(dt, this, target);
                } else {
                    t.aimAtLead(dt, this, target, Turret.effectiveGunProjectileSpeed(t));
                }
            } else if (t.kind == Turret.Kind.MISSILE) {
                t.aimAt(dt, this, target);
            }
        }
    }

    private void spawnSecondaryBurstFollowers(List<Projectile> out, Missile leader, Ship target, double dt) {
        if (out == null || leader == null || target == null) return;
        for (int i = 1; i < ALT_TEAM_SECONDARY_BURST_COUNT; i++) {
            double side = (i % 2 == 1) ? -1.0 : 1.0;
            double angleOffset = side * ALT_TEAM_SECONDARY_BURST_SPREAD;
            double ang = MathUtil.normalizeAngle(leader.angle + angleOffset);
            double trail = ALT_TEAM_SECONDARY_BURST_TRAIL_SPACING * i;
            double mx = leader.x - Math.cos(leader.angle) * trail;
            double my = leader.y - Math.sin(leader.angle) * trail;
            Missile burst = new Missile(
                    mx,
                    my,
                    ang,
                    target,
                    dt,
                    leader.speed,
                    leader.turnRate,
                    leader.damage,
                    leader.life,
                    leader.radius,
                    faction
            );
            burst.sourceShipId = id;
            out.add(burst);
        }
    }

}
