import java.util.ArrayList;
import java.util.List;

/**
 * Player-controlled ship.
 *
 * The player ship uses the same turret/system model as NPC ships.
 */
public class Player extends Ship {

    // Abilities
    private double shieldOverchargeCooldown = 2.0;
    private double shieldOverchargeTimer = 0;
    private double overShieldTimer = 0;
    private double overShieldAdded = 0;

    private double missileSalvoCooldown = 2.4;
    private double missileSalvoTimer = 0;

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
        this.faction = Faction.PLAYER;
        this.x = x;
        this.y = y;
        this.role = role;

        // Use an ALLY template so the numbers match other friendly ships.
        FleetShip template = new FleetShip(role, Faction.ALLY, x, y);
        copyFrom(template);

        // Give the player a baseline mining module on any combat hull.
        // (Dedicated MINER/HAULER roles are still better.)
        if (this.cargoMax <= 0) this.cargoMax = 120;
        if (this.miningRate <= 0) this.miningRate = 10.0;
        if (this.miningRange <= 0) this.miningRange = 56.0;

        // Ensure player faction
        this.faction = Faction.PLAYER;
    }

    @Override
    public void update(double dt) {
        super.update(dt);

        if (shieldOverchargeTimer > 0) {
            shieldOverchargeTimer -= dt;
            if (shieldOverchargeTimer < 0) shieldOverchargeTimer = 0;
        }

        if (missileSalvoTimer > 0) {
            missileSalvoTimer -= dt;
            if (missileSalvoTimer < 0) missileSalvoTimer = 0;
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

    /** Ability: fires a 4-missile salvo in a quick spread (if you have a missile turret ready). */
    public List<Projectile> tryMissileSalvo(Ship target, double dt) {
        List<Projectile> out = new ArrayList<>();
        if (!alive || target == null || !target.alive) return out;
        if (missileSalvoTimer > 0) return out;

        Turret launcher = null;
        for (Turret t : turrets) {
            if (t.primary) continue;
            if (t.kind != Turret.Kind.MISSILE) continue;
            if (t.canFire()) {
                launcher = t;
                break;
            }
        }
        if (launcher == null) return out;

        missileSalvoTimer = missileSalvoCooldown;

        launcher.aimAt(dt, this, target);
        Projectile first = launcher.fire(this, target, dt);
        if (first != null) out.add(first);

        double baseAng = launcher.angle;
        double mx = launcher.worldX(this) + Math.cos(baseAng) * (launcher.radius + 4);
        double my = launcher.worldY(this) + Math.sin(baseAng) * (launcher.radius + 4);

        double spread = 0.16;
        double ms = Turret.MISSILE_SPEED_MULT;
        double mt = Turret.MISSILE_TURN_MULT;
        double md = Turret.MISSILE_DAMAGE_MULT;
        double ml = Turret.MISSILE_LIFE_MULT;
        int salvoDamage = Math.max(1, (int) Math.round(launcher.damage * md));
        int salvoLife = Math.max(1, (int) Math.round(launcher.missileLife * ml));
        out.add(new Missile(mx, my, MathUtil.normalizeAngle(baseAng - spread), target, dt,
                launcher.missileSpeed * ms, launcher.missileTurnRate * mt, salvoDamage, salvoLife, 6.0, faction));
        out.add(new Missile(mx, my, MathUtil.normalizeAngle(baseAng + spread), target, dt,
                launcher.missileSpeed * ms, launcher.missileTurnRate * mt, salvoDamage, salvoLife, 6.0, faction));
        out.add(new Missile(mx, my, baseAng, target, dt,
                launcher.missileSpeed * ms * 1.10, launcher.missileTurnRate * mt * 1.10,
                Math.max(1, (int) Math.round(salvoDamage * 1.15)), salvoLife, 6.5, faction));

        return out;
    }

    public double getShieldOverchargeRemaining() { return shieldOverchargeTimer; }
    public double getMissileSalvoRemaining() { return missileSalvoTimer; }
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
        this.setPowerAllocation(t.powerEnginesFrac(), t.powerShieldsFrac(), t.powerWeaponsFrac(), t.powerSystemsFrac());
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

        this.hasWaveMotionGun = t.hasWaveMotionGun;
        this.waveMotionChargeTime = t.waveMotionChargeTime;
        this.waveMotionCooldown = t.waveMotionCooldown;
        this.waveMotionDamage = t.waveMotionDamage;
        this.waveMotionSpeed = t.waveMotionSpeed;
        this.waveMotionLife = t.waveMotionLife;
        this.waveMotionRadius = t.waveMotionRadius;
        this.waveMotionMaxHits = t.waveMotionMaxHits;
        this.waveMotionBeamDuration = t.waveMotionBeamDuration;
        this.waveMotionBeamTickInterval = t.waveMotionBeamTickInterval;
        this.waveMotionBeamDamageScale = t.waveMotionBeamDamageScale;
        this.resetWaveMotionCooldown();

        this.isCarrier = t.isCarrier;
        this.fighterLaunchCooldown = t.fighterLaunchCooldown;
        this.maxFighters = t.maxFighters;
        this.carrierCommandMode = t.carrierCommandMode;
        this.carrierAutoLaunch = t.carrierAutoLaunch;
        this.wingState = Ship.WingState.ATTACK;
        this.carrierOwnerId = -1;
        this.carrierOrphanTimer = -1.0;

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
        for (Turret t : turrets) {
            if (!t.primary) continue;
            if (t.kind != Turret.Kind.GUN) continue;
            t.aimAt(dt, this, targetX, targetY);
            Projectile p = t.fire(this, null, dt);
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
        for (Turret t : turrets) {
            if (!t.primary) continue;
            if (t.kind != Turret.Kind.GUN) continue;
            t.aimAtLead(dt, this, target, Turret.effectiveGunProjectileSpeed(t));
            Projectile p = t.fire(this, null, dt);
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
        for (Turret t : turrets) {
            if (t.primary) continue;
            if (t.kind != Turret.Kind.MISSILE) continue;
            if (target == null) continue;
            t.aimAt(dt, this, target);
            Projectile p = t.fire(this, target, dt);
            if (p != null) {
                out.add(p);
                fired = true;
            }
        }
        if (fired) onFiredWeapon();
        return out;
    }
}
