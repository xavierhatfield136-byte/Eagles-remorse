import java.util.ArrayList;
import java.util.List;

/**
 * Base ship.
 *
 * IMPORTANT: This project uses a "per-tick delta" pattern:
 * - vx/vy are already scaled by dt (per tick), so integration is x += vx; y += vy.
 */
public abstract class Ship {
    private static int NEXT_ID = 1;
    public final int id = NEXT_ID++;
    /** Backwards-compatible alias used by Turret/GamePanel. */
    public void onFire() {
        onFiredWeapon();
    }

    // ------------------------------
    // Salvage drop hook
    // ------------------------------
    /**
     * GamePanel registers a LootSpawner so ships can spawn real Salvage pickups when they explode.
     * (Keeps Ship decoupled from GamePanel's lists.)
     */
    public interface LootSpawner {
        void spawn(double x, double y, double vx, double vy, int credits, int ore, double lifeSeconds);
    }

    private static LootSpawner lootSpawner = null;

    public static void setLootSpawner(LootSpawner spawner) {
        lootSpawner = spawner;
    }


    public String name = "Ship";
    public Faction faction = Faction.ENEMY;
    public ShipRole role = ShipRole.FRIGATE;

    public double x, y;
    public double vx, vy; // per-tick delta
    public double angle;
    public double radius = 16;

    // Hull
    public int hpMax = 10;
    public int hp = hpMax;
    public boolean alive = true;

    // ------------------------------
    // Death sequence (wreck -> fire -> explosion -> loot)
    // ------------------------------
    /** True while this ship is a drifting wreck waiting to explode. */
    public boolean dying = false;
    /** Seconds since entering dying state. */
    private double dyingTimer = 0.0;
    /** Seconds until the wreck explodes. */
    private double burnDuration = 0.0;
    /** Cached drift velocity used during the death sequence (prevents AI from overriding). */
    private double wreckVx = 0.0, wreckVy = 0.0;
    /** Slow spin while drifting. */
    private double wreckSpin = 0.0;
    /** Ensures we explode once. */
    private boolean deathExploded = false;
    /** Fire particle spawn timer. */
    private double fireSpawnTimer = 0.0;

    // Economy
    public int bountyValue = 0;
    public boolean bountyClaimed = false;

    // ------------------------------
    // Mining / cargo / base stockpile (Economy loop)
    // ------------------------------
    /** Current ore carried by this ship (player + miners). */
    public int cargo = 0;
    /** Maximum ore this ship can carry. */
    public int cargoMax = 120;

    /** Ore per second while mining (before bonuses). */
    public double miningRate = 22.0;
    /** How close you must be to mine / deposit. */
    public double miningRange = 90.0;
    /** Current asteroid being mined (optional; used by some AI/UI). */
    public Asteroid miningTarget = null;

    /** Base-owned ore stockpile (used for upgrades / resource-rush scoring). */
    public int oreStockpile = 0;

    /** Buffer for fractional mining accumulation. */
    private double miningBuffer = 0.0;

    // ------------------------------
    // Miner AI state (NPCs)
    // ------------------------------
    public enum MinerState {
        SEEK_ASTEROID,
        MOVE_TO_ASTEROID,
        MINING,
        RETURN_TO_BASE,
        DEPOSIT,
        IDLE
    }

    public MinerState minerState = MinerState.SEEK_ASTEROID;
    public Asteroid minerTarget = null;
    public Ship minerHomeBase = null;
    public double minerDebugTimer = 0.0;
    public String minerDebugNote = "";

    // Shield
    public double shieldMax = 0;
    public double shield = 0;
    public double shieldRegen = 0.0;
    public boolean shieldActive = false;
    public double shieldRebootDelay = 2.2;
    private double shieldOfflineTimer = 0.0;
    public enum ShieldFacingMode {
        AUTO_TRACK,
        FORWARD,
        MANUAL
    }
    public ShieldFacingMode shieldFacingMode = ShieldFacingMode.AUTO_TRACK;
    public double shieldFacingAngle = Double.NaN;
    public double shieldAutoTrackRate = Math.toRadians(210.0);
    public double shieldDirectionalArc = Math.toRadians(120.0);
    private double recentShieldImpactAngle = Double.NaN;
    private double recentShieldImpactTimer = 0.0;

    // Turrets
    public final List<Turret> turrets = new ArrayList<>();

    // Superweapon (wave-motion gun)
    public boolean hasWaveMotionGun = false;
    public double waveMotionChargeTime = 0.0;
    public double waveMotionCooldown = 24.0;
    private double waveMotionTimer = 0.0;
    private double waveMotionChargeTimer = 0.0;
    private boolean waveMotionCharging = false;
    private WaveMotionShot pendingWaveMotionShot = null;
    private double queuedWaveMotionAim = Double.NaN;
    public int waveMotionDamage = 68;
    public double waveMotionSpeed = 1500.0;
    public int waveMotionLife = 140;
    public double waveMotionRadius = 12.0;
    public int waveMotionMaxHits = 18;
    public double waveMotionBeamDuration = 0.95;
    public double waveMotionBeamTickInterval = 0.12;
    public double waveMotionBeamDamageScale = 0.34;
    private double waveMotionBeamTimer = 0.0;
    private double waveMotionBeamTickTimer = 0.0;
    private double waveMotionBeamAim = Double.NaN;

    // Primary weapon family (Energy Navy only for now)
    public enum PrimaryWeaponFamily {
        ENERGY_BOLT,
        BEAM_BOLT
    }

    public static final double BEAM_BOLT_SPEED = 650.0;
    public static final double BEAM_BOLT_DAMAGE_MULT = 3.0;
    public static final double BEAM_BOLT_FIRE_RATE_MULT = 0.35;
    public static final int BEAM_BOLT_LIFE = 120; // frames (~1200px at 650 px/s)

    public PrimaryWeaponFamily primaryWeaponFamily = PrimaryWeaponFamily.ENERGY_BOLT;

    private static final class GunBaseline {
        final double cooldown;
        final int damage;
        final double bulletSpeed;
        final int bulletLife;

        GunBaseline(Turret t) {
            this.cooldown = t.cooldown;
            this.damage = t.damage;
            this.bulletSpeed = t.bulletSpeed;
            this.bulletLife = t.bulletLife;
        }
    }

    private final java.util.IdentityHashMap<Turret, GunBaseline> gunBaselines = new java.util.IdentityHashMap<>();

    // CIWS (point defense)
    public boolean hasCIWS = false;
    public double ciwsRange = 200;
    public double ciwsCooldown = 0.12;
    private double ciwsTimer = 0;

    /**
     * 0..1 quality: affects spread and pellet count.
     * The CIWS_CORVETTE role will have high quality; most other ships are weak.
     */
    public double ciwsQuality = 0.35;
    public int ciwsPelletsPerBurst = 2;
    public double ciwsPelletSpeed = 920;
    public int ciwsPelletDamage = 1;
    public int ciwsPelletLife = 18;
    public double ciwsPelletRadius = 1.8;

    // Carrier
    public enum WingState {
        ATTACK,
        RTB
    }

    public enum CarrierCommandMode {
        ATTACK,
        DEFEND
    }

    public boolean isCarrier = false;
    public double fighterLaunchCooldown = 4.0;
    private double fighterTimer = 0;
    public int maxFighters = 4;
    /** Carrier command mode used by launched wing craft behavior. */
    public CarrierCommandMode carrierCommandMode = CarrierCommandMode.ATTACK;
    /** If false, this carrier won't auto-launch replacement craft. */
    public boolean carrierAutoLaunch = true;
    /** If this is a launched strike craft, the owning carrier ship id; otherwise -1. */
    public int carrierOwnerId = -1;
    /** Seconds until orphaned craft despawns; -1 while not orphaned. */
    public double carrierOrphanTimer = -1.0;
    /** Active strike-craft behavior state. */
    public WingState wingState = WingState.ATTACK;

    // Base / capture
    public boolean isBase = false;
    public Faction baseOwner = null;
    public double captureProgress = 1.0; // 0..1 (0 = belongs to ENEMY, 1 = belongs to ALLY)
    public double captureRadius = 360;
    public double captureTime = 10.0; // seconds with advantage to flip

    // Base repair/spawn
    public double baseSpawnCooldown = 9.0;
    private double baseSpawnTimer = 0;
    public int maxDefenders = 8;
    public double repairRange = 320;
    public double repairHullPerSec = 1.6;
    public double repairShieldPerSec = 8.0;

    // Movement
    public double desiredSpeed = 110;
    public double desiredSpeedBase = 110;

    // AI engagement memory (used for lane adaptation and anti-stall behavior).
    public double aiBadApproachTimer = 0.0;
    public double aiBadApproachAngle = Double.NaN;
    public double aiNoFireTimer = 0.0;
    public double aiLastEngagementX = Double.NaN;
    public double aiLastEngagementY = Double.NaN;

    // Power management
    public enum PowerPreset {
        BALANCED,
        ATTACK,
        DEFENSE,
        PURSUIT
    }
    public PowerPreset powerPreset = PowerPreset.BALANCED;
    private double powerEngines = 0.25;
    private double powerShields = 0.25;
    private double powerWeapons = 0.25;
    private double powerSystems = 0.25;

    // Crew interactions
    public enum CrewOrder {
        BALANCED,
        GUNNERY,
        ENGINEERING,
        DAMAGE_CONTROL
    }
    public CrewOrder crewOrder = CrewOrder.BALANCED;
    private double crewFatigue = 0.0;      // retained for save/compat, no gameplay effect
    private double crewCasualtyRate = 0.0; // 0..1
    private double crewReadiness = 1.0;    // 0..1
    private double crewCombatStress = 0.0;
    private double crewEngineMul = 1.0;
    private double crewShieldMul = 1.0;
    private double crewWeaponMul = 1.0;
    private double crewSystemMul = 1.0;
    private double hullRegenBuffer = 0;

    // Internal system damage model
    public enum InternalSystem {
        ENGINES,
        SHIELDS,
        REACTOR_CORE,
        SENSORS,
        WEAPONS,
        BRIDGE,
        WARP_ENGINES,
        MAGAZINES
    }
    private final java.util.EnumMap<InternalSystem, Double> systemHp = new java.util.EnumMap<>(InternalSystem.class);
    private final java.util.EnumMap<InternalSystem, Double> systemHpMax = new java.util.EnumMap<>(InternalSystem.class);
    private boolean internalSystemsInitialized = false;

    // Stealth
    /** If true, this ship is harder to target/lock unless revealed or very close. */
    public boolean isStealth = false;
    /** 0..1 (1 = fully visible). Stealth ships usually sit around ~0.35 while cloaked. */
    public double signature = 1.0;
    /** Seconds remaining that this ship is "revealed" (shots/hits make you easier to see). */
    public double revealTimer = 0.0;
    /** Active cloak state for stealth ships. */
    public boolean cloakActive = false;
    /** If false, stealth ships will not engage cloak (debug/gameplay toggle hook). */
    public boolean cloakEnabled = true;
    /** Cloak resource model. */
    public double cloakEnergyMax = 9.0;
    public double cloakEnergy = cloakEnergyMax;
    public double cloakDrainPerSec = 1.15;
    public double cloakRechargePerSec = 0.95;
    public double cloakMinEnergyToEngage = 1.0;
    public double cloakSignature = 0.08;

    public void addTurret(Turret t) {
        if (t != null) {
            turrets.add(t);
            if (t.kind == Turret.Kind.GUN) {
                cacheGunBaseline(t);
                if (primaryWeaponFamily == PrimaryWeaponFamily.BEAM_BOLT) {
                    applyPrimaryWeaponFamily();
                }
            }
        }
    }

    public void update(double dt) {
        if (!alive && !dying) return;

        // Dying ships drift, burn, and then explode into debris.
        if (dying) {
            // Once exploded, stop the death-loop update path entirely.
            if (deathExploded) {
                dying = false;
                vx = 0;
                vy = 0;
                wreckVx = 0;
                wreckVy = 0;
                return;
            }

            // Override any AI/input velocity changes by using wreck velocities.
            vx = wreckVx;
            vy = wreckVy;

            x += vx;
            y += vy;

            // Gentle drag (scaled to dt)
            double drag = Math.pow(0.985, dt * 60.0);
            wreckVx *= drag;
            wreckVy *= drag;

            // Slow tumble
            angle += wreckSpin * dt;

            // Spawn intermittent fire + smoke
            dyingTimer += dt;
            fireSpawnTimer += dt;
            if (fireSpawnTimer >= 0.06) {
                fireSpawnTimer = 0.0;
                double jx = (Math.random() - 0.5) * radius * 0.9;
                double jy = (Math.random() - 0.5) * radius * 0.9;
                double intensity = 0.6 + Math.random() * 0.8;
                VFX.spawnShipFire(x + jx, y + jy, intensity);
            }

            // After a short burn, explode and hand off to VFX/Explosion.
            if (!deathExploded && dyingTimer >= burnDuration) {
                deathExploded = true;

                // Big boom
                Explosion.spawnDeath(x, y);
                ScreenShake.kick(8.0);

                // Debris + "salvage" visuals
                double baseSpdX = wreckVx;
                double baseSpdY = wreckVy;
                VFX.spawnDebrisBurst(x, y, baseSpdX, baseSpdY, (int) Math.max(10, radius * 0.9));
                VFX.spawnSalvageBurst(x, y, baseSpdX, baseSpdY, 3 + (int) (Math.random() * 4));

                // Real collectible salvage pickups
                spawnExplosionSalvage(baseSpdX, baseSpdY);

                // Mark dead so GamePanel awards bounty + removes from list.
                alive = false;
            }

            return;
        }

        x += vx;
        y += vy;

        if (revealTimer > 0) {
            revealTimer -= dt;
            if (revealTimer < 0) revealTimer = 0;
        }
        if (recentShieldImpactTimer > 0.0) {
            recentShieldImpactTimer -= dt;
            if (recentShieldImpactTimer < 0.0) recentShieldImpactTimer = 0.0;
        }
        ensureInternalSystemsInitialized();
        ensurePowerInitialized();
        updateCrewState(dt);
        updateDerivedSystemEffects();
        updateShieldFacing(dt);
        updateStealthCloak(dt);

        if (shieldOfflineTimer > 0.0) {
            shieldOfflineTimer -= dt;
            if (shieldOfflineTimer < 0.0) shieldOfflineTimer = 0.0;
        }
        if (isShieldOnline() && shield < shieldMax) {
            shield = Math.min(shieldMax, shield + shieldRegen * shieldRegenMultiplier() * dt);
        }

        for (Turret t : turrets) t.update(dt);

        ciwsTimer -= dt;
        if (ciwsTimer < 0) ciwsTimer = 0;

        if (waveMotionTimer > 0) {
            waveMotionTimer -= dt;
            if (waveMotionTimer < 0) waveMotionTimer = 0;
        }
        if (waveMotionCharging) {
            waveMotionChargeTimer -= dt;
            if (waveMotionChargeTimer <= 0.0) {
                waveMotionChargeTimer = 0.0;
                waveMotionCharging = false;
                double aim = Double.isFinite(queuedWaveMotionAim) ? queuedWaveMotionAim : angle;
                pendingWaveMotionShot = fireWaveMotionShot(dt, aim);
                queuedWaveMotionAim = Double.NaN;
            }
        }
        if (waveMotionBeamTimer > 0.0) {
            waveMotionBeamTimer -= dt;
            waveMotionBeamTickTimer -= dt;
            if (waveMotionBeamTimer < 0.0) waveMotionBeamTimer = 0.0;

            if (waveMotionBeamTimer > 0.0 && waveMotionBeamTickTimer <= 0.0) {
                double aim = Double.isFinite(waveMotionBeamAim) ? waveMotionBeamAim : angle;
                if (pendingWaveMotionShot == null || !pendingWaveMotionShot.alive) {
                    pendingWaveMotionShot = createWaveMotionPulse(dt, aim, true);
                }
                waveMotionBeamTickTimer = Math.max(0.03, waveMotionBeamTickInterval);
            }

            if (waveMotionBeamTimer <= 0.0) {
                waveMotionBeamTickTimer = 0.0;
                waveMotionBeamAim = Double.NaN;
            }
        }

        if (isCarrier) {
            fighterTimer -= dt;
            if (fighterTimer < 0) fighterTimer = 0;
        }

        if (isBase) {
            baseSpawnTimer -= dt;
            if (baseSpawnTimer < 0) baseSpawnTimer = 0;
        }
    }

    public void applyPrimaryWeaponFamily() {
        for (Turret t : turrets) {
            if (t == null) continue;
            if (t.kind != Turret.Kind.GUN) continue;

            GunBaseline base = gunBaselines.get(t);
            if (base == null) {
                base = cacheGunBaseline(t);
            }

            if (primaryWeaponFamily == PrimaryWeaponFamily.BEAM_BOLT) {
                t.damage = Math.max(1, (int) Math.round(base.damage * BEAM_BOLT_DAMAGE_MULT));
                t.cooldown = base.cooldown / BEAM_BOLT_FIRE_RATE_MULT;
                t.bulletSpeed = BEAM_BOLT_SPEED;
                t.bulletLife = BEAM_BOLT_LIFE;
            } else {
                t.damage = base.damage;
                t.cooldown = base.cooldown;
                t.bulletSpeed = base.bulletSpeed;
                t.bulletLife = base.bulletLife;
            }
        }
    }

    private GunBaseline cacheGunBaseline(Turret t) {
        GunBaseline base = gunBaselines.get(t);
        if (base != null) return base;
        base = new GunBaseline(t);
        gunBaselines.put(t, base);
        return base;
    }

    public void healHull(double amount) {
        if (!alive || amount <= 0) return;
        if (hp >= hpMax) return;

        hullRegenBuffer += amount;
        int add = (int) Math.floor(hullRegenBuffer);
        if (add > 0) {
            hp = Math.min(hpMax, hp + add);
            hullRegenBuffer -= add;
        }
    }

    public void healShield(double amount) {
        if (!alive || !isShieldOnline()) return;
        if (amount <= 0) return;
        shield = Math.min(shieldMax, shield + amount);
    }

    public void takeDamage(int dmg) {
        takeDamage(dmg, Double.NaN, Double.NaN);
    }

    public void takeDamage(int dmg, double hitX, double hitY) {
        if (!alive) return;
        if (dying) return;
        if (dmg <= 0) return;

        // Getting hit briefly reveals stealth ships.
        reveal(2.5);
        crewCombatStress = Math.max(crewCombatStress, 1.4);

        if (isShieldOnline() && shield > 0) {
            double scaledDamage = dmg * directionalShieldDamageScale(hitX, hitY);
            shield -= scaledDamage;
            if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
                recentShieldImpactAngle = Math.atan2(hitY - y, hitX - x);
                recentShieldImpactTimer = 1.2;
            }
            if (shield <= 0) {
                shield = 0;
                shieldOfflineTimer = Math.max(shieldOfflineTimer, shieldRebootDelay);
            }
            Explosion.spawnShieldHit(x, y);
            return;
        }

        hp -= dmg;
        applySystemDamageFromHullHit(dmg, hitX, hitY);
        if (hp <= 0) {
            hp = 0;
            startDeathSequence();
        }
    }

    private void startDeathSequence() {
        if (dying) return;
        dying = true;
        waveMotionCharging = false;
        waveMotionChargeTimer = 0.0;
        pendingWaveMotionShot = null;
        queuedWaveMotionAim = Double.NaN;
        waveMotionBeamTimer = 0.0;
        waveMotionBeamTickTimer = 0.0;
        waveMotionBeamAim = Double.NaN;
        dyingTimer = 0.0;
        fireSpawnTimer = 0.0;
        deathExploded = false;

        // Keep current drift; add a touch of forward momentum.
        wreckVx = vx;
        wreckVy = vy;

        // Random burn time (feels more organic)
        burnDuration = 1.2 + Math.random() * 1.1;

        // Random tumble
        wreckSpin = (Math.random() - 0.5) * 2.4;

        // Initial sparks
        VFX.spawnImpactSparks(x, y, 0.0, 0.0, 3);
    }

    /**
     * Spawn real Salvage pickups (collectible) when the wreck finally explodes.
     * Visual shards are handled by VFX; this is the gameplay pickup.
     */
    private void spawnExplosionSalvage(double baseVx, double baseVy) {
        if (lootSpawner == null) return;

        // Don't drop salvage for the player ship (avoids weird self-loot on death).
        if (this instanceof Player) return;

        // Compute a drop budget based on ship "size".
        double size = Math.max(8.0, radius);
        double toughness = hpMax + shieldMax * 0.35;

        int totalCredits = (int) Math.round(25 + toughness * 10.5 + size * 3.2);
        int totalOre = (int) Math.round(Math.max(0.0, toughness * 0.55 + size * 0.15));

        // Bases would flood the map; tone it down.
        if (isBase) {
            totalCredits = (int) Math.round(totalCredits * 0.35);
            totalOre = (int) Math.round(totalOre * 0.35);
        }

        // Randomize a bit so it feels organic.
        totalCredits = (int) Math.round(totalCredits * (0.75 + Math.random() * 0.55));
        totalOre = (int) Math.round(totalOre * (0.70 + Math.random() * 0.60));

        // How many pickups?
        int count = 2 + (int) Math.floor(size / 14.0);
        if (count < 2) count = 2;
        if (count > 10) count = 10;
        if (isBase && count > 6) count = 6;

        int remainingC = Math.max(0, totalCredits);
        int remainingO = Math.max(0, totalOre);

        final double dtTick = 1.0 / 60.0;

        for (int i = 0; i < count; i++) {
            // Split the remaining pool.
            int c;
            int o;

            if (i == count - 1) {
                c = remainingC;
                o = remainingO;
            } else {
                double fracC = 0.18 + Math.random() * 0.28;
                double fracO = 0.18 + Math.random() * 0.28;
                c = (int) Math.round(remainingC * fracC);
                o = (int) Math.round(remainingO * fracO);
            }

            remainingC -= c;
            remainingO -= o;
            if (remainingC < 0) remainingC = 0;
            if (remainingO < 0) remainingO = 0;

            // Impulse direction/speed.
            double a = Math.random() * Math.PI * 2.0;
            double sp = 90 + Math.random() * 320; // units/sec

            double svx = baseVx + Math.cos(a) * sp * dtTick;
            double svy = baseVy + Math.sin(a) * sp * dtTick;

            double ox = (Math.random() - 0.5) * radius * 0.6;
            double oy = (Math.random() - 0.5) * radius * 0.6;

            double life = 22.0 + Math.random() * 16.0;

            lootSpawner.spawn(x + ox, y + oy, svx, svy, c, o, life);
        }
    }


    // ------------------------------
    // Economy helpers
    // ------------------------------

    /**
     * Try to mine ore from a nearby asteroid.
     *
     * @return amount of ore actually mined (0 if nothing mined).
     */
    public int tryMine(Asteroid a, double dt) {
        if (!alive || dying) return 0;
        if (a == null) return 0;
        if (cargo >= cargoMax) return 0;

        // Must be in range
        double dx = a.x - x;
        double dy = a.y - y;
        double reach = miningRange + radius + a.radius;
        if (dx * dx + dy * dy > reach * reach) return 0;

        // Accumulate fractional mining and take whole units.
        miningBuffer += Math.max(0.0, miningRate) * Math.max(0.0, dt);
        int want = (int) Math.floor(miningBuffer);
        if (want <= 0) return 0;
        miningBuffer -= want;

        int room = cargoMax - cargo;
        if (room <= 0) return 0;
        if (want > room) want = room;

        // Asteroid supports takeOre in the Option 5+ code.
        int got = a.takeOre(want);
        if (got <= 0) return 0;

        cargo += got;
        miningTarget = a;
        return got;
    }

    /**
     * Deposit all carried cargo to a friendly base, increasing its oreStockpile.
     *
     * @return amount deposited.
     */
    public int depositCargoTo(Ship base) {
        if (!alive || dying) return 0;
        if (cargo <= 0) return 0;
        if (base == null || !base.isBase) return 0;
        if (!faction.isFriendlyTo(base.faction)) return 0;

        int moved = cargo;
        cargo = 0;
        base.oreStockpile += moved;
        return moved;
    }

    /** Make the ship easier to see/lock for a short time. */
    public void reveal(double seconds) {
        if (!isStealth) return;
        revealTimer = Math.max(revealTimer, seconds);
        cloakActive = false;
    }

    /** Called when this ship fires a weapon; helps prevent perma-cloaking while shooting. */
    public void onFiredWeapon() {
        reveal(1.4);
        crewCombatStress = Math.max(crewCombatStress, 1.0);
    }

    /** Effective signature (used by targeting). */
    public double effectiveSignature() {
        if (!isStealth) return 1.0;
        if (isCloaked()) {
            return Math.max(0.03, Math.min(0.30, Math.min(signature, cloakSignature)));
        }
        if (revealTimer > 0) return 1.0;
        return Math.max(0.20, Math.min(1.0, signature));
    }

    public boolean isCloaked() {
        if (!isStealth) return false;
        if (!cloakEnabled) return false;
        if (!cloakActive) return false;
        if (revealTimer > 0) return false;
        return cloakEnergy > 0.01;
    }

    public double cloakEnergyFrac() {
        if (!isStealth) return 0.0;
        if (cloakEnergyMax <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(1.0, cloakEnergy / cloakEnergyMax));
    }

    private void updateStealthCloak(double dt) {
        if (!isStealth || dt <= 0.0) return;

        if (cloakEnergyMax <= 0.01) cloakEnergyMax = 0.01;
        cloakEnergy = Math.max(0.0, Math.min(cloakEnergyMax, cloakEnergy));

        if (!cloakEnabled || revealTimer > 0.0) {
            cloakActive = false;
            cloakEnergy = Math.min(cloakEnergyMax, cloakEnergy + cloakRechargePerSec * dt);
            return;
        }

        if (!cloakActive && cloakEnergy >= cloakMinEnergyToEngage) {
            cloakActive = true;
        }

        if (cloakActive) {
            cloakEnergy -= cloakDrainPerSec * dt;
            if (cloakEnergy <= 0.0) {
                cloakEnergy = 0.0;
                cloakActive = false;
                // Briefly expose after cloak burnout.
                revealTimer = Math.max(revealTimer, 1.0);
            }
        } else {
            cloakEnergy = Math.min(cloakEnergyMax, cloakEnergy + cloakRechargePerSec * dt);
        }
    }

    public void resetInternalSystems() {
        internalSystemsInitialized = false;
        systemHp.clear();
        systemHpMax.clear();
    }

    public double systemHealthFraction(InternalSystem system) {
        ensureInternalSystemsInitialized();
        if (system == null) return 1.0;
        Double hpv = systemHp.get(system);
        Double maxv = systemHpMax.get(system);
        if (hpv == null || maxv == null || maxv <= 1e-6) return 1.0;
        return Math.max(0.0, Math.min(1.0, hpv / maxv));
    }

    public boolean isSystemDestroyed(InternalSystem system) {
        return systemHealthFraction(system) <= 0.001;
    }

    public PowerPreset cyclePowerPreset() {
        PowerPreset[] presets = PowerPreset.values();
        int idx = powerPreset.ordinal() + 1;
        if (idx >= presets.length) idx = 0;
        setPowerPreset(presets[idx]);
        return powerPreset;
    }

    public void setPowerPreset(PowerPreset preset) {
        if (preset == null) preset = PowerPreset.BALANCED;
        powerPreset = preset;
        switch (preset) {
            case ATTACK -> setPowerAllocation(0.20, 0.20, 0.44, 0.16);
            case DEFENSE -> setPowerAllocation(0.18, 0.46, 0.20, 0.16);
            case PURSUIT -> setPowerAllocation(0.48, 0.14, 0.22, 0.16);
            default -> setPowerAllocation(0.25, 0.25, 0.25, 0.25);
        }
    }

    public void setPowerAllocation(double engines, double shields, double weapons, double systems) {
        powerEngines = Math.max(0.0, engines);
        powerShields = Math.max(0.0, shields);
        powerWeapons = Math.max(0.0, weapons);
        powerSystems = Math.max(0.0, systems);
        normalizePowerAllocation();
    }

    public double powerEnginesFrac() { return powerEngines; }
    public double powerShieldsFrac() { return powerShields; }
    public double powerWeaponsFrac() { return powerWeapons; }
    public double powerSystemsFrac() { return powerSystems; }

    public CrewOrder cycleCrewOrder() {
        CrewOrder[] orders = CrewOrder.values();
        int idx = crewOrder.ordinal() + 1;
        if (idx >= orders.length) idx = 0;
        crewOrder = orders[idx];
        return crewOrder;
    }

    public double crewReadiness() {
        return Math.max(0.0, Math.min(1.0, crewReadiness));
    }

    public double crewFatigue() {
        return 0.0;
    }

    public ShieldFacingMode cycleShieldFacingMode() {
        ShieldFacingMode[] modes = ShieldFacingMode.values();
        int idx = shieldFacingMode.ordinal() + 1;
        if (idx >= modes.length) idx = 0;
        shieldFacingMode = modes[idx];
        if (shieldFacingMode == ShieldFacingMode.FORWARD) {
            shieldFacingAngle = angle;
        } else if (!Double.isFinite(shieldFacingAngle)) {
            shieldFacingAngle = angle;
        }
        return shieldFacingMode;
    }

    public void rotateShieldFacing(double deltaRad) {
        if (!Double.isFinite(deltaRad) || deltaRad == 0.0) return;
        if (!Double.isFinite(shieldFacingAngle)) shieldFacingAngle = angle;
        shieldFacingAngle = MathUtil.normalizeAngle(shieldFacingAngle + deltaRad);
    }

    public double getShieldFacingAngle() {
        if (!Double.isFinite(shieldFacingAngle)) return angle;
        return shieldFacingAngle;
    }

    public double shieldArcDegrees() {
        return Math.toDegrees(Math.max(0.0, shieldDirectionalArc));
    }

    public double weaponDamageMultiplier() {
        double weapons = systemHealthFraction(InternalSystem.WEAPONS);
        double reactor = systemHealthFraction(InternalSystem.REACTOR_CORE);
        double out = 0.28 + 0.52 * weapons + 0.20 * reactor;
        out *= weaponsPowerMultiplier();
        out *= crewWeaponMul;
        return Math.max(0.12, Math.min(1.50, out));
    }

    public double weaponCycleRateMultiplier() {
        double weapons = systemHealthFraction(InternalSystem.WEAPONS);
        double reactor = systemHealthFraction(InternalSystem.REACTOR_CORE);
        double out = 0.32 + 0.48 * weapons + 0.20 * reactor;
        out *= weaponsPowerMultiplier();
        out *= crewWeaponMul;
        return Math.max(0.12, Math.min(1.45, out));
    }

    public double sensorRangeMultiplier() {
        double sensors = systemHealthFraction(InternalSystem.SENSORS);
        double bridge = systemHealthFraction(InternalSystem.BRIDGE);
        double out = 0.35 + 0.45 * sensors + 0.20 * bridge;
        out *= systemsPowerMultiplier();
        out *= crewSystemMul;
        return Math.max(0.16, Math.min(1.25, out));
    }

    public double shieldSystemMultiplier() {
        return Math.max(0.0, Math.min(1.0, systemHealthFraction(InternalSystem.SHIELDS)));
    }

    public double shieldRegenMultiplier() {
        double regen = shieldSystemMultiplier();
        regen *= shieldsPowerMultiplier();
        regen *= crewShieldMul;
        return Math.max(0.0, Math.min(1.65, regen));
    }

    private void ensureInternalSystemsInitialized() {
        if (internalSystemsInitialized) return;

        if (desiredSpeedBase <= 0.0) desiredSpeedBase = Math.max(0.0, desiredSpeed);
        double base = Math.max(20.0, hpMax + shieldMax * 0.40 + radius * 0.60);

        initSystem(InternalSystem.ENGINES, base * 0.78);
        initSystem(InternalSystem.SHIELDS, shieldActive ? base * 0.74 : base * 0.36);
        initSystem(InternalSystem.REACTOR_CORE, base * 0.88);
        initSystem(InternalSystem.SENSORS, base * 0.58);
        initSystem(InternalSystem.WEAPONS, base * 0.80);
        initSystem(InternalSystem.BRIDGE, base * 0.54);
        initSystem(InternalSystem.WARP_ENGINES, base * 0.62);
        initSystem(InternalSystem.MAGAZINES, base * 0.70);
        internalSystemsInitialized = true;
    }

    private void initSystem(InternalSystem system, double hp) {
        double max = Math.max(6.0, hp);
        systemHpMax.put(system, max);
        systemHp.put(system, max);
    }

    private void ensurePowerInitialized() {
        if (powerEngines == 0.0 && powerShields == 0.0 && powerWeapons == 0.0 && powerSystems == 0.0) {
            setPowerPreset(powerPreset);
            return;
        }
        normalizePowerAllocation();
    }

    private void normalizePowerAllocation() {
        double sum = powerEngines + powerShields + powerWeapons + powerSystems;
        if (sum <= 1e-6) {
            powerEngines = 0.25;
            powerShields = 0.25;
            powerWeapons = 0.25;
            powerSystems = 0.25;
            return;
        }
        powerEngines /= sum;
        powerShields /= sum;
        powerWeapons /= sum;
        powerSystems /= sum;
    }

    private double enginesPowerMultiplier() {
        return Math.max(0.35, Math.min(1.35, 0.58 + 0.82 * powerEngines));
    }

    private double shieldsPowerMultiplier() {
        return Math.max(0.18, Math.min(1.45, 0.52 + 0.98 * powerShields));
    }

    private double weaponsPowerMultiplier() {
        return Math.max(0.20, Math.min(1.50, 0.52 + 0.98 * powerWeapons));
    }

    private double systemsPowerMultiplier() {
        return Math.max(0.28, Math.min(1.30, 0.58 + 0.84 * powerSystems));
    }

    private void updateCrewState(double dt) {
        if (dt <= 0.0) return;

        if (crewCombatStress > 0.0) {
            crewCombatStress -= dt;
            if (crewCombatStress < 0.0) crewCombatStress = 0.0;
        }

        // Crew fatigue gameplay was removed; keep it pinned to zero.
        crewFatigue = 0.0;

        // Casualties are persistent but recover slowly over time through medbay/automation.
        crewCasualtyRate -= dt * 0.0015;
        crewCasualtyRate = MathUtil.clamp(crewCasualtyRate, 0.0, 0.70);

        double readinessBase = 1.0 - 0.45 * crewCasualtyRate;
        crewReadiness = MathUtil.clamp(readinessBase, 0.28, 1.0);

        double casualtyPenalty = 1.0 - 0.42 * crewCasualtyRate;
        double base = MathUtil.clamp(crewReadiness * casualtyPenalty, 0.30, 1.0);

        double engines = 1.0;
        double shields = 1.0;
        double weapons = 1.0;
        double systems = 1.0;
        switch (crewOrder) {
            case GUNNERY -> {
                weapons = 1.20;
                shields = 0.88;
                engines = 0.94;
                systems = 0.94;
            }
            case ENGINEERING -> {
                engines = 1.12;
                shields = 1.16;
                systems = 1.14;
                weapons = 0.86;
            }
            case DAMAGE_CONTROL -> {
                shields = 1.12;
                systems = 1.22;
                engines = 0.86;
                weapons = 0.80;
                ensureInternalSystemsInitialized();
                repairDamagedSystems((0.55 + 0.55 * crewReadiness) * dt);
                healHull((0.16 + 0.22 * crewReadiness) * dt);
            }
            default -> {
                // Balanced
            }
        }

        crewEngineMul = MathUtil.clamp(base * engines, 0.35, 1.30);
        crewShieldMul = MathUtil.clamp(base * shields, 0.30, 1.35);
        crewWeaponMul = MathUtil.clamp(base * weapons, 0.28, 1.35);
        crewSystemMul = MathUtil.clamp(base * systems, 0.30, 1.35);
    }

    private void repairDamagedSystems(double amount) {
        if (amount <= 0.0) return;
        InternalSystem lowest = null;
        double lowestFrac = 1.0;
        for (InternalSystem s : InternalSystem.values()) {
            double f = systemHealthFraction(s);
            if (f < lowestFrac) {
                lowestFrac = f;
                lowest = s;
            }
        }
        if (lowest == null || lowestFrac >= 0.999) return;
        Double hpv = systemHp.get(lowest);
        Double maxv = systemHpMax.get(lowest);
        if (hpv == null || maxv == null || maxv <= 0.0) return;
        hpv = Math.min(maxv, hpv + amount * maxv * 0.16 * systemsPowerMultiplier());
        systemHp.put(lowest, hpv);
    }

    private void updateShieldFacing(double dt) {
        if (!shieldActive || shieldMax <= 0.0) return;
        if (!Double.isFinite(shieldFacingAngle)) shieldFacingAngle = angle;

        if (shieldFacingMode == ShieldFacingMode.FORWARD) {
            shieldFacingAngle = angle;
            return;
        }
        if (shieldFacingMode == ShieldFacingMode.MANUAL) {
            return;
        }

        double target = angle;
        double v2 = vx * vx + vy * vy;
        if (v2 > 1e-8) {
            target = Math.atan2(vy, vx);
        }
        if (recentShieldImpactTimer > 0.0 && Double.isFinite(recentShieldImpactAngle)) {
            target = recentShieldImpactAngle;
        }

        double delta = MathUtil.normalizeAngle(target - shieldFacingAngle);
        double maxStep = Math.max(0.0, shieldAutoTrackRate * dt);
        delta = MathUtil.clamp(delta, -maxStep, maxStep);
        shieldFacingAngle = MathUtil.normalizeAngle(shieldFacingAngle + delta);
    }

    private double directionalShieldDamageScale(double hitX, double hitY) {
        if (!Double.isFinite(hitX) || !Double.isFinite(hitY)) return 1.0;
        if (!Double.isFinite(shieldFacingAngle)) return 1.0;

        double hitAngle = Math.atan2(hitY - y, hitX - x);
        double rel = Math.abs(MathUtil.normalizeAngle(hitAngle - shieldFacingAngle));
        double halfArc = Math.max(Math.toRadians(35.0), shieldDirectionalArc * 0.5);
        double arcT = MathUtil.clamp(rel / halfArc, 0.0, 1.0);

        // In-arc hits are mitigated heavily, out-of-arc hits leak through.
        double arcScale = 0.62 + arcT * 0.72;
        double systems = Math.max(0.30, Math.min(1.30, shieldSystemMultiplier() * systemsPowerMultiplier() * crewShieldMul));
        double powerScale = 1.22 - 0.36 * shieldsPowerMultiplier();
        double total = arcScale * powerScale / Math.max(0.35, systems);
        return Math.max(0.35, Math.min(1.95, total));
    }

    private void updateDerivedSystemEffects() {
        if (desiredSpeedBase <= 0.0) desiredSpeedBase = Math.max(0.0, desiredSpeed);

        double engine = systemHealthFraction(InternalSystem.ENGINES);
        double warp = systemHealthFraction(InternalSystem.WARP_ENGINES);
        double bridge = systemHealthFraction(InternalSystem.BRIDGE);
        double mobility = 0.24 + 0.52 * engine + 0.16 * warp + 0.08 * bridge;
        mobility *= enginesPowerMultiplier();
        mobility *= crewEngineMul;
        mobility = Math.max(0.18, Math.min(1.38, mobility));
        desiredSpeed = desiredSpeedBase * mobility;
    }

    private void applySystemDamageFromHullHit(int hullDamage, double hitX, double hitY) {
        if (hullDamage <= 0) return;
        ensureInternalSystemsInitialized();

        int rolls = (hullDamage >= 9) ? 2 : 1;
        for (int i = 0; i < rolls; i++) {
            InternalSystem system = pickSystemForHit(hitX, hitY);
            if (system == null) continue;

            double dmg = Math.max(1.0, hullDamage * (0.58 + Math.random() * 0.82));
            damageSystem(system, dmg);
        }

        double casualtySpike = (hpMax <= 0) ? 0.0 : (hullDamage / (double) hpMax) * 0.36;
        crewCasualtyRate = MathUtil.clamp(crewCasualtyRate + casualtySpike, 0.0, 0.75);

        if (isSystemDestroyed(InternalSystem.MAGAZINES) && Math.random() < 0.10) {
            int cookoff = Math.max(1, (int) Math.round(hullDamage * 0.45));
            hp -= cookoff;
            Explosion.spawnShieldHit(x, y);
        }
    }

    private InternalSystem pickSystemForHit(double hitX, double hitY) {
        if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
            double hitAngle = Math.atan2(hitY - y, hitX - x);
            double rel = Math.abs(MathUtil.normalizeAngle(hitAngle - angle));
            if (rel < Math.toRadians(45.0) && Math.random() < 0.55) {
                InternalSystem[] front = { InternalSystem.WEAPONS, InternalSystem.SENSORS, InternalSystem.BRIDGE };
                return front[(int) Math.floor(Math.random() * front.length)];
            }
            if (rel > Math.toRadians(135.0) && Math.random() < 0.55) {
                InternalSystem[] rear = { InternalSystem.ENGINES, InternalSystem.WARP_ENGINES, InternalSystem.REACTOR_CORE };
                return rear[(int) Math.floor(Math.random() * rear.length)];
            }
        }

        InternalSystem[] systems = InternalSystem.values();
        int idx = (int) Math.floor(Math.random() * systems.length);
        if (idx < 0 || idx >= systems.length) idx = 0;
        InternalSystem s = systems[idx];
        if (s == InternalSystem.SHIELDS && !shieldActive && Math.random() < 0.7) {
            s = (Math.random() < 0.5) ? InternalSystem.ENGINES : InternalSystem.WEAPONS;
        }
        return s;
    }

    private void damageSystem(InternalSystem system, double damage) {
        if (system == null || damage <= 0.0) return;
        Double hpv = systemHp.get(system);
        if (hpv == null) return;
        hpv = Math.max(0.0, hpv - damage);
        systemHp.put(system, hpv);

        if (system == InternalSystem.SHIELDS && hpv <= 0.0) {
            shield = 0.0;
            shieldOfflineTimer = Math.max(shieldOfflineTimer, shieldRebootDelay * 1.5);
        }
    }

    /**
     * CIWS point-defense.
     *
     * - Finds the closest incoming enemy missile in range.
     * - Fires a burst of visible pellets toward it.
     * - Pellets are handled by CollisionSystem (pellets can kill missiles).
     */
    public void tryCIWS(double dt, List<Projectile> projectiles) {
        if (!alive || !hasCIWS) return;
        if (ciwsTimer > 0) return;
        if (projectiles == null || projectiles.isEmpty()) return;

        Missile closest = null;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (!(p instanceof Missile m)) continue;
            if (faction.isFriendlyTo(m.faction)) continue;

            double dx = m.x - x;
            double dy = m.y - y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= ciwsRange * ciwsRange && d2 < bestD2) {
                bestD2 = d2;
                closest = m;
            }
        }

        if (closest == null) return;

        // Fire!
        ciwsTimer = ciwsCooldown;

        double aim = Math.atan2(closest.y - y, closest.x - x);

        // Spread gets tighter as quality increases.
        double spread = Math.toRadians(22) * (1.15 - ciwsQuality);
        int pellets = Math.max(1, ciwsPelletsPerBurst);

        for (int i = 0; i < pellets; i++) {
            double jitter = (Math.random() - 0.5) * 2.0 * spread;
            double a = MathUtil.normalizeAngle(aim + jitter);

            double sx = x + Math.cos(a) * (radius + 8);
            double sy = y + Math.sin(a) * (radius + 8);

            projectiles.add(new CIWSPellet(
                    sx,
                    sy,
                    a,
                    dt,
                    ciwsPelletSpeed,
                    ciwsPelletDamage,
                    ciwsPelletLife,
                    ciwsPelletRadius,
                    faction
            ));
        }
    }

    public boolean canLaunchFighter() {
        return alive && isCarrier && fighterTimer <= 0;
    }

    public void resetFighterTimer() {
        fighterTimer = fighterLaunchCooldown;
    }

    public boolean canSpawnDefender() {
        return alive && isBase && baseSpawnTimer <= 0;
    }

    public void resetBaseSpawnTimer() {
        baseSpawnTimer = baseSpawnCooldown;
    }

    public double getWaveMotionRemaining() {
        return Math.max(waveMotionTimer, waveMotionCharging ? waveMotionChargeTimer : 0.0);
    }

    public boolean isWaveMotionCharging() {
        return waveMotionCharging;
    }

    public double getWaveMotionChargeProgress() {
        if (!waveMotionCharging) return 0.0;
        if (waveMotionChargeTime <= 1e-9) return 1.0;
        double t = 1.0 - (waveMotionChargeTimer / waveMotionChargeTime);
        return Math.max(0.0, Math.min(1.0, t));
    }

    public boolean isWaveMotionBeamActive() {
        return waveMotionBeamTimer > 0.0;
    }

    public double getWaveMotionAimAngle() {
        if (waveMotionCharging && Double.isFinite(queuedWaveMotionAim)) return queuedWaveMotionAim;
        if (waveMotionBeamTimer > 0.0 && Double.isFinite(waveMotionBeamAim)) return waveMotionBeamAim;
        return angle;
    }

    public WaveMotionShot pollWaveMotionShot() {
        WaveMotionShot shot = pendingWaveMotionShot;
        pendingWaveMotionShot = null;
        return shot;
    }

    public void resetWaveMotionCooldown() {
        waveMotionTimer = 0.0;
        waveMotionChargeTimer = 0.0;
        waveMotionCharging = false;
        pendingWaveMotionShot = null;
        queuedWaveMotionAim = Double.NaN;
        waveMotionBeamTimer = 0.0;
        waveMotionBeamTickTimer = 0.0;
        waveMotionBeamAim = Double.NaN;
    }

    public boolean canFireWaveMotionGun() {
        if (!alive || dying) return false;
        if (!hasWaveMotionGun) return false;
        return waveMotionTimer <= 0.0 && !waveMotionCharging;
    }

    public void trackWaveMotionAim(double targetX, double targetY) {
        if (!hasWaveMotionGun) return;
        double aim = resolveWaveMotionAim(targetX, targetY);
        if (waveMotionCharging) queuedWaveMotionAim = aim;
        if (waveMotionBeamTimer > 0.0) waveMotionBeamAim = aim;
    }

    public WaveMotionShot tryFireWaveMotionGunAt(double targetX, double targetY, double dt) {
        if (!canFireWaveMotionGun()) return null;
        if (dt <= 0.0) return null;
        double aim = resolveWaveMotionAim(targetX, targetY);
        queuedWaveMotionAim = aim;

        if (waveMotionChargeTime > 0.0) {
            waveMotionCharging = true;
            waveMotionChargeTimer = waveMotionChargeTime;
            return null;
        }

        queuedWaveMotionAim = Double.NaN;
        return fireWaveMotionShot(dt, aim);
    }

    public WaveMotionShot tryFireWaveMotionGun(Ship target, double dt) {
        if (target == null) return tryFireWaveMotionGunAt(Double.NaN, Double.NaN, dt);
        return tryFireWaveMotionGunAt(target.x, target.y, dt);
    }

    public boolean isShieldOnline() {
        return shieldActive
                && shieldMax > 0
                && shieldSystemMultiplier() > 0.05
                && powerShields > 0.04
                && shieldOfflineTimer <= 0.0;
    }

    public double getShieldOfflineRemaining() {
        return Math.max(0.0, shieldOfflineTimer);
    }

    public void resetShieldState() {
        shieldOfflineTimer = 0.0;
        recentShieldImpactTimer = 0.0;
        recentShieldImpactAngle = Double.NaN;
        if (shieldFacingMode == ShieldFacingMode.FORWARD) {
            shieldFacingAngle = angle;
        }
    }

    private double resolveWaveMotionAim(double targetX, double targetY) {
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY)) return angle;
        double dx = targetX - x;
        double dy = targetY - y;
        double d2 = dx * dx + dy * dy;
        if (d2 < 1e-8) return angle;
        return Math.atan2(dy, dx);
    }

    private WaveMotionShot fireWaveMotionShot(double dt, double aim) {
        angle = aim;
        waveMotionTimer = Math.max(1.0, waveMotionCooldown);
        waveMotionBeamTimer = Math.max(0.0, waveMotionBeamDuration);
        waveMotionBeamTickTimer = Math.max(0.03, waveMotionBeamTickInterval);
        waveMotionBeamAim = aim;
        onFiredWeapon();

        return createWaveMotionPulse(dt, aim, false);
    }

    private WaveMotionShot createWaveMotionPulse(double dt, double aim, boolean beamTick) {
        double sx = x + Math.cos(aim) * (radius + 10.0);
        double sy = y + Math.sin(aim) * (radius + 10.0);

        int damage = waveMotionDamage;
        double speed = waveMotionSpeed;
        int life = waveMotionLife;
        double radius = waveMotionRadius;
        int maxHits = waveMotionMaxHits;

        if (beamTick) {
            damage = Math.max(1, (int) Math.round(waveMotionDamage * waveMotionBeamDamageScale));
            speed = waveMotionSpeed * 1.12;
            life = Math.max(18, (int) Math.round(waveMotionLife * 0.42));
            radius = waveMotionRadius * 0.92;
            maxHits = Math.max(6, (int) Math.round(waveMotionMaxHits * 0.45));
        }

        return new WaveMotionShot(
                sx,
                sy,
                aim,
                dt,
                speed,
                damage,
                life,
                radius,
                maxHits,
                faction
        );
    }
}
