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
    public int waveMotionDamage = 52;
    public double waveMotionSpeed = 1500.0;
    public int waveMotionLife = 140;
    public double waveMotionRadius = 12.0;
    public int waveMotionMaxHits = 18;

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

    // Stealth
    /** If true, this ship is harder to target/lock unless revealed or very close. */
    public boolean isStealth = false;
    /** 0..1 (1 = fully visible). Stealth ships usually sit around ~0.35 while cloaked. */
    public double signature = 1.0;
    /** Seconds remaining that this ship is "revealed" (shots/hits make you easier to see). */
    public double revealTimer = 0.0;

    // For fractional healing (bases)
    private double hullRegenBuffer = 0;

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

        if (shieldActive && shield < shieldMax) {
            shield = Math.min(shieldMax, shield + shieldRegen * dt);
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
                pendingWaveMotionShot = spawnWaveMotionShotForward(dt);
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
        if (!alive || !shieldActive || shieldMax <= 0) return;
        if (amount <= 0) return;
        shield = Math.min(shieldMax, shield + amount);
    }

    public void takeDamage(int dmg) {
        if (!alive) return;
        if (dying) return;

        // Getting hit briefly reveals stealth ships.
        reveal(2.5);

        if (shieldActive && shield > 0) {
            shield -= dmg;
            if (shield < 0) shield = 0;
            Explosion.spawnShieldHit(x, y);
            return;
        }

        hp -= dmg;
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
    }

    /** Called when this ship fires a weapon; helps prevent perma-cloaking while shooting. */
    public void onFiredWeapon() {
        reveal(1.4);
    }

    /** Effective signature (used by targeting). */
    public double effectiveSignature() {
        if (!isStealth) return 1.0;
        if (revealTimer > 0) return 1.0;
        return Math.max(0.15, Math.min(1.0, signature));
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
    }

    public boolean canFireWaveMotionGun() {
        if (!alive || dying) return false;
        if (!hasWaveMotionGun) return false;
        return waveMotionTimer <= 0.0 && !waveMotionCharging;
    }

    public WaveMotionShot tryFireWaveMotionGunAt(double targetX, double targetY, double dt) {
        if (!canFireWaveMotionGun()) return null;
        if (dt <= 0.0) return null;

        if (waveMotionChargeTime > 0.0) {
            waveMotionCharging = true;
            waveMotionChargeTimer = waveMotionChargeTime;
            return null;
        }

        return spawnWaveMotionShotForward(dt);
    }

    public WaveMotionShot tryFireWaveMotionGun(Ship target, double dt) {
        return tryFireWaveMotionGunAt(0.0, 0.0, dt);
    }

    private WaveMotionShot spawnWaveMotionShotForward(double dt) {
        double aim = angle;
        double sx = x + Math.cos(aim) * (radius + 10.0);
        double sy = y + Math.sin(aim) * (radius + 10.0);

        waveMotionTimer = Math.max(1.0, waveMotionCooldown);
        onFiredWeapon();
        return new WaveMotionShot(
                sx,
                sy,
                aim,
                dt,
                waveMotionSpeed,
                waveMotionDamage,
                waveMotionLife,
                waveMotionRadius,
                waveMotionMaxHits,
                faction
        );
    }
}
