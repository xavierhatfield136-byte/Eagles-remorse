import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    public static final int SHIELD_FACE_COUNT = 4;
    public static final int SHIELD_FACE_FORE = 0;
    public static final int SHIELD_FACE_LEFT = 1;
    public static final int SHIELD_FACE_RIGHT = 2;
    public static final int SHIELD_FACE_REAR = 3;
    private static final double SHIELD_FACE_REGEN_LOCK_SECONDS = 10.0;
    private static final double SHIELD_FACE_SWAP_TRIGGER_FRAC = 0.20;
    private static final double SHIELD_FACE_SWAP_ADVANTAGE_FRAC = 0.10;
    private static final String[] SHIELD_FACE_NAMES = {"FORE", "LEFT", "RIGHT", "REAR"};
    private final double[] shieldFaces = new double[SHIELD_FACE_COUNT];
    private final double[] shieldFaceRegenLock = new double[SHIELD_FACE_COUNT];
    private double shieldFacesSyncedMax = Double.NaN;
    private boolean shieldFacesInitialized = false;
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
    private static final double WAVE_MOTION_PROJECTILE_RATE_MULT = 3.0;
    private double waveMotionBeamTimer = 0.0;
    private double waveMotionBeamTickTimer = 0.0;
    private double waveMotionBeamAim = Double.NaN;

    // Primary weapon family (Energy Navy only for now)
    public enum PrimaryWeaponFamily {
        ENERGY_BOLT,
        BEAM_BOLT
    }

    public static final double BEAM_BOLT_SPEED = 650.0;
    public static final double BEAM_BOLT_FIRE_RATE_MULT = 0.30;
    public static final double BEAM_BOLT_DPS_MULT = 1.20;
    // 4.0 * 0.30 = 1.20x DPS target versus baseline guns.
    public static final double BEAM_BOLT_DAMAGE_MULT = 4.0;
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
    public enum PowerBus {
        PROPULSION,
        SHIELD,
        TACTICAL,
        SENSOR,
        ENGINEERING,
        AUXILIARY
    }
    public enum SubsystemState {
        NOMINAL,
        STRESSED,
        DAMAGED,
        OFFLINE,
        DESTROYED
    }
    public enum EngineeringPriority {
        BALANCED,
        REACTOR,
        PROPULSION,
        SHIELDS,
        WEAPONS,
        SENSORS
    }
    public PowerPreset powerPreset = PowerPreset.BALANCED;
    // Legacy 4-way bus fields retained for compatibility with existing APIs/callers.
    private double powerEngines = 0.25;
    private double powerShields = 0.25;
    private double powerWeapons = 0.25;
    private double powerSystems = 0.25;
    // New Phase 4 subsystem buses.
    private double powerSensors = 0.125;
    private double powerEngineering = 0.125;
    private double powerAuxiliary = 0.125;
    private EngineeringPriority engineeringPriority = EngineeringPriority.BALANCED;
    private PowerBus overloadBus = PowerBus.TACTICAL;
    private boolean overloadActive = false;
    private double overloadHeat = 0.0;
    private double overloadStressDebt = 0.0;
    private double overloadCooldownTimer = 0.0;
    private boolean emergencyThrustActive = false;
    private double emergencyThrustHeat = 0.0;
    private double emergencyThrustCooldown = 0.0;

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

    // Room-localized damage model (x-ray map source of truth).
    private final java.util.EnumMap<ShipRoomLayout.RoomId, Double> roomHp =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumMap<ShipRoomLayout.RoomId, Double> roomHpMax =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumMap<ShipRoomLayout.RoomId, RoomHazardState> roomHazards =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumSet<InternalSystem> roomDisabledSystems =
            java.util.EnumSet.noneOf(InternalSystem.class);
    private boolean roomSystemsInitialized = false;
    private static final int MAX_ROOM_DAMAGE_EVENTS = 64;
    private static final double ROOM_CONDEMNED_THRESHOLD = 0.30;
    private final List<RoomDamageEvent> roomDamageEvents = new ArrayList<>();
    private final List<RoomDamageEvent> roomDamageEventsView = Collections.unmodifiableList(roomDamageEvents);
    private RoomDamageResult lastRoomDamageResult = RoomDamageResult.NONE;

    // Hull impact/breach data in ship-local coordinates (used for exact decal placement).
    public static final class HullImpactMark {
        public final double localX;
        public final double localY;
        public final double severity;
        public final double breachRadius;
        public final ShipRoomLayout.RoomId roomId;

        private HullImpactMark(double localX, double localY, double severity, double breachRadius,
                               ShipRoomLayout.RoomId roomId) {
            this.localX = localX;
            this.localY = localY;
            this.severity = severity;
            this.breachRadius = breachRadius;
            this.roomId = roomId;
        }
    }

    private static final int MAX_HULL_IMPACT_MARKS = 64;
    private static final double HULL_IMPACT_DECAY_IDLE_SECONDS = 10.0;
    private final List<HullImpactMark> hullImpactMarks = new ArrayList<>();
    private final List<HullImpactMark> hullImpactMarksView = Collections.unmodifiableList(hullImpactMarks);
    private double hullImpactNoDamageTimer = HULL_IMPACT_DECAY_IDLE_SECONDS;
    private static final double CATASTROPHIC_CHAIN_GRACE_SECONDS = 4.0;
    private static final double CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC = 0.22;
    private double catastrophicChainGraceTimer = 0.0;
    private double noDamageTimerSeconds = 0.0;
    private boolean instantRepairConsumed = false;

    private static final class HullDamageSplit {
        private final ShipRoomLayout.RoomDef primaryRoom;
        private final double primaryRoomHpBefore;
        private double roomLocalHpLoss = 0.0;
        private int hazardRolls = 0;
        private final LinkedHashSet<String> subsystemTransitions = new LinkedHashSet<>();

        private HullDamageSplit(ShipRoomLayout.RoomDef primaryRoom, double primaryRoomHpBefore) {
            this.primaryRoom = primaryRoom;
            this.primaryRoomHpBefore = Math.max(0.0, primaryRoomHpBefore);
        }

        private void absorb(RoomDamageResult result) {
            if (result == null || result == RoomDamageResult.NONE) return;
            roomLocalHpLoss += Math.max(0.0, result.roomLocalHpLoss);
            hazardRolls += Math.max(0, result.hazardRolls);
            if (result.subsystemTransitions != null) subsystemTransitions.addAll(result.subsystemTransitions);
        }

        private RoomDamageResult finish(Ship ship, int hullBefore) {
            double before = primaryRoomHpBefore;
            double after = primaryRoomHpBefore;
            String roomId = "";
            if (primaryRoom != null) {
                roomId = primaryRoom.id.name();
                double cur = ship.roomHp.getOrDefault(primaryRoom.id, before);
                after = Math.max(0.0, cur);
            }
            return new RoomDamageResult(
                    roomId,
                    before,
                    after,
                    hazardRolls,
                    new ArrayList<>(subsystemTransitions),
                    ship.hp - hullBefore,
                    roomLocalHpLoss
            );
        }
    }

    public static final class RoomDamageEvent {
        public final ShipRoomLayout.RoomId roomId;
        public final double normalizedX;
        public final double normalizedY;
        public final double damage;
        public final boolean fromHazard;
        public final long timestampNanos;

        private RoomDamageEvent(ShipRoomLayout.RoomId roomId, double normalizedX, double normalizedY,
                                double damage, boolean fromHazard) {
            this.roomId = roomId;
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.damage = damage;
            this.fromHazard = fromHazard;
            this.timestampNanos = System.nanoTime();
        }
    }

    public static final class RoomStatus {
        public final ShipRoomLayout.RoomId roomId;
        public final String label;
        public final double[] normalizedXs;
        public final double[] normalizedYs;
        public final double hp;
        public final double hpMax;
        public final boolean critical;
        public final double fireIntensity;
        public final InternalSystem primarySystem;

        private RoomStatus(ShipRoomLayout.RoomId roomId, String label, double[] normalizedXs, double[] normalizedYs,
                           double hp, double hpMax, boolean critical, double fireIntensity,
                           InternalSystem primarySystem) {
            this.roomId = roomId;
            this.label = label;
            this.normalizedXs = normalizedXs;
            this.normalizedYs = normalizedYs;
            this.hp = hp;
            this.hpMax = hpMax;
            this.critical = critical;
            this.fireIntensity = fireIntensity;
            this.primarySystem = primarySystem;
        }
    }

    private static final class RoomHazardState {
        final ShipRoomLayout.RoomId roomId;
        double fireIntensity = 0.0; // 0..2
        double damageTickTimer = 0.0;
        double spreadTimer = 0.0;
        double suppressionBoost = 0.0;
        double instabilityTimer = 0.0;
        double vfxTimer = 0.0;

        RoomHazardState(ShipRoomLayout.RoomId roomId) {
            this.roomId = roomId;
        }

        boolean active() {
            return fireIntensity > 1e-4;
        }
    }

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

        // Dying ships drift, burn, then detonate as a single fireball (no debris burst effects).
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

            // Spawn intermittent fire + smoke while spiraling out.
            dyingTimer += dt;
            fireSpawnTimer += dt;
            if (fireSpawnTimer >= 0.06) {
                fireSpawnTimer = 0.0;
                double jx = (Math.random() - 0.5) * radius * 0.9;
                double jy = (Math.random() - 0.5) * radius * 0.9;
                double intensity = 0.6 + Math.random() * 0.8;
                VFX.spawnShipFire(x + jx, y + jy, intensity);
            }

            if (!deathExploded && dyingTimer >= burnDuration) {
                explodeIntoFireball(wreckVx, wreckVy);
            }
            return;
        }

        x += vx;
        y += vy;
        if (dt > 0.0) noDamageTimerSeconds += dt;
        if (catastrophicChainGraceTimer > 0.0) {
            catastrophicChainGraceTimer -= dt;
            if (catastrophicChainGraceTimer < 0.0) catastrophicChainGraceTimer = 0.0;
        }

        if (revealTimer > 0) {
            revealTimer -= dt;
            if (revealTimer < 0) revealTimer = 0;
        }
        if (recentShieldImpactTimer > 0.0) {
            recentShieldImpactTimer -= dt;
            if (recentShieldImpactTimer < 0.0) recentShieldImpactTimer = 0.0;
        }
        updateHullImpactDecay(dt);
        ensureInternalSystemsInitialized();
        ensureRoomSystemsInitialized();
        ensurePowerInitialized();
        updatePowerBusState(dt);
        updateEmergencyThrustState(dt);
        updateCrewState(dt);
        updateDerivedSystemEffects();
        updateRoomHazards(dt);
        updateShieldFacing(dt);
        updateStealthCloak(dt);
        ensureShieldFacesSynced();

        if (shieldOfflineTimer > 0.0) {
            shieldOfflineTimer -= dt;
            if (shieldOfflineTimer < 0.0) shieldOfflineTimer = 0.0;
        }
        updateShieldFaceRegenLocks(dt);
        if (isShieldOnline() && shield < shieldMax) {
            distributeShieldRegen(shieldRegen * shieldRegenMultiplier() * dt);
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
                waveMotionBeamTickTimer = waveMotionTickSpacing();
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

        syncHullFromRoomIntegrity();
        evaluateCondemnedStateFromRooms();
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
        if (!alive || dying || amount <= 0.0) return;
        ensureRoomSystemsInitialized();

        double roomMaxTotal = 0.0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role)) {
            roomMaxTotal += Math.max(0.0, roomHpMax.getOrDefault(def.id, 0.0));
        }
        if (roomMaxTotal <= 1e-9) return;

        // Convert legacy hull-heal units into room integrity units so existing systems
        // (base aura, events, damage-control) remain compatible.
        double scaled = amount;
        if (hpMax > 0) scaled = amount * (roomMaxTotal / hpMax);
        hullRegenBuffer += Math.max(0.0, scaled);

        int guard = 0;
        while (hullRegenBuffer > 1e-6 && guard++ < 32) {
            ShipRoomLayout.RoomId target = null;
            double lowestFrac = 1.0;
            for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role)) {
                double maxv = roomHpMax.getOrDefault(def.id, 0.0);
                if (maxv <= 1e-9) continue;
                double cur = roomHp.getOrDefault(def.id, maxv);
                double frac = MathUtil.clamp(cur / maxv, 0.0, 1.0);
                if (frac < lowestFrac - 1e-6) {
                    lowestFrac = frac;
                    target = def.id;
                }
            }
            if (target == null) {
                hullRegenBuffer = 0.0;
                break;
            }

            double maxv = roomHpMax.getOrDefault(target, 0.0);
            if (maxv <= 1e-9) {
                hullRegenBuffer = 0.0;
                break;
            }
            double cur = roomHp.getOrDefault(target, maxv);
            double missing = Math.max(0.0, maxv - cur);
            if (missing <= 1e-6) {
                hullRegenBuffer = 0.0;
                break;
            }

            double grant = Math.min(missing, hullRegenBuffer);
            roomHp.put(target, cur + grant);
            hullRegenBuffer -= grant;

            RoomHazardState hz = roomHazards.get(target);
            if (hz != null && hz.fireIntensity > 0.0) {
                hz.fireIntensity = Math.max(0.0, hz.fireIntensity - grant * 0.03);
            }
        }

        // Guard against runaway buffers if incoming heals exceed repair demand.
        hullRegenBuffer = Math.max(0.0, Math.min(hullRegenBuffer, roomMaxTotal));
        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
    }

    public void fullyRepairHull() {
        if (!alive || dying) return;
        ensureRoomSystemsInitialized();
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role)) {
            double maxv = roomHpMax.getOrDefault(def.id, 0.0);
            roomHp.put(def.id, maxv);
            RoomHazardState hz = roomHazards.get(def.id);
            if (hz != null) {
                hz.fireIntensity = 0.0;
                hz.damageTickTimer = 0.0;
                hz.spreadTimer = 0.0;
                hz.suppressionBoost = 0.0;
                hz.instabilityTimer = 0.0;
                hz.vfxTimer = 0.0;
            }
        }
        hullRegenBuffer = 0.0;
        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
    }

    public double secondsSinceDamage() {
        return Math.max(0.0, noDamageTimerSeconds);
    }

    public boolean tryInstantRepairFromOrder(double requiredNoDamageSeconds) {
        if (!alive || dying) return false;
        if (instantRepairConsumed) return false;
        double req = Math.max(0.0, requiredNoDamageSeconds);
        if (noDamageTimerSeconds + 1e-9 < req) return false;

        // Full refit: restore internal systems/rooms/shield state in one step.
        resetInternalSystems();
        fullyRepairHull();
        resetShieldState();
        if (shieldActive && shieldMax > 0.0) shield = shieldMax;
        clearHullImpactMarks();

        instantRepairConsumed = true;
        return true;
    }

    public void healShield(double amount) {
        if (!alive || !isShieldOnline()) return;
        if (amount <= 0) return;
        ensureShieldFacesSynced();
        distributeShieldRegen(amount);
    }

    public void takeDamage(int dmg) {
        takeDamage(dmg, Double.NaN, Double.NaN);
    }

    public void takeDamage(int dmg, double hitX, double hitY) {
        takeDamage(dmg, hitX, hitY, Double.NaN, Double.NaN);
    }

    public void takeDamage(int dmg, double hitX, double hitY, double impactVx, double impactVy) {
        if (!alive) return;
        if (dying) return;
        if (dmg <= 0) return;
        lastRoomDamageResult = RoomDamageResult.NONE;
        hullImpactNoDamageTimer = 0.0;
        noDamageTimerSeconds = 0.0;
        instantRepairConsumed = false;

        // Getting hit briefly reveals stealth ships.
        reveal(2.5);
        crewCombatStress = Math.max(crewCombatStress, 1.4);
        ensureRoomSystemsInitialized();
        ensureShieldFacesSynced();
        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        ShipRoomLayout.RoomDef primaryRoom = resolvePrimaryRoomForHullHit(impact, hitX, hitY, impactVx, impactVy);
        int hullDamage = dmg;

        double impactAngle = resolveShieldImpactAngle(hitX, hitY, impactVx, impactVy);
        double threatFacingAngle = resolveShieldThreatFacingAngle(hitX, hitY, impactVx, impactVy);
        double shieldFacingForHit = getShieldFacingAngle();
        if (shieldFacingMode == ShieldFacingMode.AUTO_TRACK && Double.isFinite(threatFacingAngle)) {
            shieldFacingForHit = autoFacingTargetForThreat(threatFacingAngle);
            if (Double.isFinite(shieldFacingForHit)) {
                // Keep visual facing and damage-facing in sync during incoming fire.
                shieldFacingAngle = shieldFacingForHit;
            }
        }

        if (isShieldOnline() && shield > 0) {
            int face = shieldFaceForImpactAngle(impactAngle, shieldFacingForHit);
            double faceHpBefore = shieldFaceValue(face);
            if (Double.isFinite(threatFacingAngle)) {
                recentShieldImpactAngle = threatFacingAngle;
                recentShieldImpactTimer = 1.2;
            }
            if (faceHpBefore > 1e-6) {
                double shieldScale = directionalShieldDamageScaleFromAngle(impactAngle, shieldFacingForHit);
                double scaledDamage = dmg * shieldScale;
                double overflow = Math.max(0.0, scaledDamage - faceHpBefore);
                applyShieldDamageToFace(face, scaledDamage);
                if (shield <= 0) {
                    forceShieldOffline(shieldRebootDelay);
                }
                double fx = Double.isFinite(hitX) ? hitX : x;
                double fy = Double.isFinite(hitY) ? hitY : y;
                Explosion.spawnShieldHit(fx, fy);
                if (overflow <= 1e-6) {
                    return;
                }

                // Residual bleed-through still gets attenuated by shield geometry.
                double bleedThroughScale = MathUtil.clamp(0.42 + 0.36 * shieldScale, 0.24, 0.88);
                hullDamage = Math.max(1, (int) Math.round(overflow * bleedThroughScale));
            }
        }

        registerHullImpact(hullDamage, impact, primaryRoom);
        int hullBefore = hp;
        RoomDamageResult split = applySystemDamageFromHullHit(hullDamage, impact, primaryRoom, hullBefore);
        if (split != null) lastRoomDamageResult = split;
        syncHullFromRoomIntegrity();
        evaluateCondemnedStateFromRooms();
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

        // Preserve final motion for drift.
        wreckVx = vx;
        wreckVy = vy;

        // Burn for a short random time before detonation.
        burnDuration = 1.2 + Math.random() * 1.1;

        // Random tumble while drifting out of control.
        wreckSpin = (Math.random() - 0.5) * 2.4;

        // Initial sparks on kill impact.
        VFX.spawnImpactSparks(x, y, 0.0, 0.0, 3);
    }

    private void explodeIntoFireball(double baseVx, double baseVy) {
        if (deathExploded) return;
        deathExploded = true;
        Explosion.spawnDeath(x, y);
        ScreenShake.kick(8.0);
        spawnExplosionSalvage(baseVx, baseVy);
        alive = false;
        dying = false;
        vx = 0.0;
        vy = 0.0;
        wreckVx = 0.0;
        wreckVy = 0.0;
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
        roomSystemsInitialized = false;
        roomHp.clear();
        roomHpMax.clear();
        roomHazards.clear();
        roomDisabledSystems.clear();
        roomDamageEvents.clear();
        lastRoomDamageResult = RoomDamageResult.NONE;
        hullRegenBuffer = 0.0;
        noDamageTimerSeconds = 0.0;
        instantRepairConsumed = false;
        catastrophicChainGraceTimer = 0.0;
        emergencyThrustActive = false;
        emergencyThrustHeat = 0.0;
        emergencyThrustCooldown = 0.0;
    }

    public List<HullImpactMark> hullImpactMarks() {
        return hullImpactMarksView;
    }

    public void clearHullImpactMarks() {
        hullImpactMarks.clear();
        hullImpactNoDamageTimer = HULL_IMPACT_DECAY_IDLE_SECONDS;
    }

    public List<RoomStatus> roomStatusSnapshot() {
        ensureRoomSystemsInitialized();
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role);
        List<RoomStatus> out = new ArrayList<>(defs.size());
        for (ShipRoomLayout.RoomDef d : defs) {
            double hpv = roomHp.getOrDefault(d.id, 0.0);
            double maxv = roomHpMax.getOrDefault(d.id, 1.0);
            double fire = roomHazards.containsKey(d.id) ? roomHazards.get(d.id).fireIntensity : 0.0;
            out.add(new RoomStatus(
                    d.id, d.label, d.xs, d.ys, hpv, maxv, d.critical, fire, d.primarySystem
            ));
        }
        return out;
    }

    public List<RoomDamageEvent> recentRoomDamageEvents() {
        return roomDamageEventsView;
    }

    public RoomDamageResult lastRoomDamageResult() {
        return lastRoomDamageResult;
    }

    public List<ShipRoom> shipRoomSnapshot() {
        ensureRoomSystemsInitialized();
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role);
        List<ShipRoom> out = new ArrayList<>(defs.size());
        String profileId = ShipRoomLayout.profileIdForRole(role);
        for (ShipRoomLayout.RoomDef d : defs) {
            if (d == null || d.id == null) continue;
            double maxv = roomHpMax.getOrDefault(d.id, 0.0);
            double hpv = roomHp.getOrDefault(d.id, maxv);
            RoomHazardState hz = roomHazards.get(d.id);
            double fire = (hz == null) ? 0.0 : hz.fireIntensity;

            int statusFlags = 0;
            if (hpv <= 1e-6) statusFlags |= ShipRoom.STATUS_DESTROYED;
            if (fire > 1e-4) statusFlags |= ShipRoom.STATUS_FIRE_ACTIVE;
            if (d.critical) statusFlags |= ShipRoom.STATUS_CRITICAL;

            List<String> tags = new ArrayList<>(3);
            if (d.primarySystem != null) tags.add(d.primarySystem.name().toLowerCase());
            if (d.critical) tags.add("critical");
            if (fire > 1e-4) tags.add("hazard_fire");

            out.add(new ShipRoom(
                    d.id.name(),
                    profileId,
                    flattenPolygon(d.xs, d.ys),
                    maxv,
                    hpv,
                    d.critical ? 1.0 : 0.45,
                    tags,
                    statusFlags
            ));
        }
        return out;
    }

    public List<HazardState> hazardStateSnapshot() {
        ensureRoomSystemsInitialized();
        if (roomHazards.isEmpty()) return List.of();
        List<HazardState> out = new ArrayList<>(roomHazards.size());
        for (RoomHazardState hz : roomHazards.values()) {
            if (hz == null || hz.roomId == null) continue;
            out.add(new HazardState(
                    "fire:" + hz.roomId.name().toLowerCase(),
                    hz.roomId.name(),
                    "fire",
                    hz.fireIntensity,
                    hz.spreadTimer,
                    hazardSuppressionState(hz)
            ));
        }
        return out;
    }

    public double roomHealthFraction(ShipRoomLayout.RoomId roomId) {
        ensureRoomSystemsInitialized();
        if (roomId == null) return 1.0;
        double hpv = roomHp.getOrDefault(roomId, 1.0);
        double maxv = roomHpMax.getOrDefault(roomId, 1.0);
        if (maxv <= 1e-9) return 1.0;
        return Math.max(0.0, Math.min(1.0, hpv / maxv));
    }

    public double roomFireIntensity(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return 0.0;
        RoomHazardState hz = roomHazards.get(roomId);
        if (hz == null) return 0.0;
        return Math.max(0.0, hz.fireIntensity);
    }

    public int activeFireRoomCount() {
        ensureRoomSystemsInitialized();
        int count = 0;
        for (RoomHazardState hz : roomHazards.values()) {
            if (hz != null && hz.fireIntensity > 0.05) count++;
        }
        return count;
    }

    public double totalFireIntensity() {
        ensureRoomSystemsInitialized();
        double total = 0.0;
        for (RoomHazardState hz : roomHazards.values()) {
            if (hz == null) continue;
            total += Math.max(0.0, hz.fireIntensity);
        }
        return total;
    }

    public boolean hasActiveFireHazards() {
        return activeFireRoomCount() > 0;
    }

    public ShipRoomLayout.RoomId hottestFireRoom() {
        ensureRoomSystemsInitialized();
        ShipRoomLayout.RoomId hottest = null;
        double hottestIntensity = 0.05;
        for (RoomHazardState hz : roomHazards.values()) {
            if (hz == null || hz.roomId == null) continue;
            if (hz.fireIntensity > hottestIntensity) {
                hottestIntensity = hz.fireIntensity;
                hottest = hz.roomId;
            }
        }
        return hottest;
    }

    public boolean suppressHottestFire() {
        if (!alive || dying) return false;
        ShipRoomLayout.RoomId target = hottestFireRoom();
        if (target == null) return false;
        double effort = 0.18 + 0.24 * engineeringPowerMultiplier() + 0.16 * crewReadiness();
        return applyFireSuppression(target, effort, true) > 1e-4;
    }

    public boolean suppressFireInRoom(ShipRoomLayout.RoomId roomId) {
        if (!alive || dying || roomId == null) return false;
        double effort = 0.16 + 0.20 * engineeringPowerMultiplier() + 0.12 * crewReadiness();
        return applyFireSuppression(roomId, effort, true) > 1e-4;
    }

    private static double[] flattenPolygon(double[] xs, double[] ys) {
        if (xs == null || ys == null) return new double[0];
        int n = Math.min(xs.length, ys.length);
        if (n <= 0) return new double[0];
        double[] out = new double[n * 2];
        for (int i = 0; i < n; i++) {
            out[i * 2] = xs[i];
            out[i * 2 + 1] = ys[i];
        }
        return out;
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
            case ATTACK -> setPowerBusAllocation(0.16, 0.13, 0.31, 0.12, 0.16, 0.12);
            case DEFENSE -> setPowerBusAllocation(0.13, 0.30, 0.14, 0.14, 0.19, 0.10);
            case PURSUIT -> setPowerBusAllocation(0.33, 0.12, 0.18, 0.12, 0.14, 0.11);
            default -> setPowerBusAllocation(0.18, 0.18, 0.19, 0.15, 0.18, 0.12);
        }
    }

    // Legacy 4-way API: systems share is split across sensor/engineering/aux buses.
    public void setPowerAllocation(double engines, double shields, double weapons, double systems) {
        double support = Math.max(0.0, systems);
        setPowerBusAllocation(
                Math.max(0.0, engines),
                Math.max(0.0, shields),
                Math.max(0.0, weapons),
                support * 0.34,
                support * 0.42,
                support * 0.24
        );
    }

    public void setPowerBusAllocation(double propulsion,
                                      double shield,
                                      double tactical,
                                      double sensor,
                                      double engineering,
                                      double auxiliary) {
        powerEngines = Math.max(0.0, propulsion);
        powerShields = Math.max(0.0, shield);
        powerWeapons = Math.max(0.0, tactical);
        powerSensors = Math.max(0.0, sensor);
        powerEngineering = Math.max(0.0, engineering);
        powerAuxiliary = Math.max(0.0, auxiliary);
        normalizePowerAllocation();
    }

    public double powerBusFraction(PowerBus bus) {
        PowerBus b = (bus == null) ? PowerBus.ENGINEERING : bus;
        return switch (b) {
            case PROPULSION -> powerEngines;
            case SHIELD -> powerShields;
            case TACTICAL -> powerWeapons;
            case SENSOR -> powerSensors;
            case ENGINEERING -> powerEngineering;
            case AUXILIARY -> powerAuxiliary;
        };
    }

    public double[] powerBusFractions() {
        return new double[]{
                powerEngines,
                powerShields,
                powerWeapons,
                powerSensors,
                powerEngineering,
                powerAuxiliary
        };
    }

    public double powerBusEffect(PowerBus bus) {
        return powerBusEffectiveMultiplier(bus);
    }

    public double powerEnginesFrac() { return powerEngines; }
    public double powerShieldsFrac() { return powerShields; }
    public double powerWeaponsFrac() { return powerWeapons; }
    public double powerSystemsFrac() { return powerSensors + powerEngineering + powerAuxiliary; }
    public double powerSensorsFrac() { return powerSensors; }
    public double powerEngineeringFrac() { return powerEngineering; }
    public double powerAuxiliaryFrac() { return powerAuxiliary; }

    public EngineeringPriority engineeringPriority() {
        return engineeringPriority;
    }

    public EngineeringPriority cycleEngineeringPriority() {
        EngineeringPriority[] values = EngineeringPriority.values();
        int idx = engineeringPriority.ordinal() + 1;
        if (idx >= values.length) idx = 0;
        engineeringPriority = values[idx];
        return engineeringPriority;
    }

    public void setEngineeringPriority(EngineeringPriority priority) {
        engineeringPriority = (priority == null) ? EngineeringPriority.BALANCED : priority;
    }

    public PowerBus overloadBus() {
        return overloadBus;
    }

    public void setOverloadBus(PowerBus bus) {
        overloadBus = (bus == null) ? PowerBus.TACTICAL : bus;
    }

    public PowerBus cycleOverloadBus(int dir) {
        PowerBus[] values = PowerBus.values();
        int step = (dir < 0) ? -1 : 1;
        int idx = overloadBus.ordinal() + step;
        if (idx < 0) idx = values.length - 1;
        if (idx >= values.length) idx = 0;
        overloadBus = values[idx];
        return overloadBus;
    }

    public boolean isOverloadActive() {
        return overloadActive;
    }

    public boolean isOverloadAvailable() {
        return overloadCooldownTimer <= 1e-6;
    }

    public double overloadHeat() {
        return Math.max(0.0, Math.min(1.0, overloadHeat));
    }

    public double overloadStressDebt() {
        return Math.max(0.0, overloadStressDebt);
    }

    public double overloadCooldownRemaining() {
        return Math.max(0.0, overloadCooldownTimer);
    }

    public boolean isEmergencyThrustActive() {
        return emergencyThrustActive;
    }

    public double emergencyThrustHeat() {
        return MathUtil.clamp(emergencyThrustHeat, 0.0, 1.0);
    }

    public double emergencyThrustCooldownRemaining() {
        return Math.max(0.0, emergencyThrustCooldown);
    }

    public double emergencyThrustSpeedMultiplier() {
        if (!emergencyThrustActive) return 1.0;
        double heatPenalty = 1.0 - Math.min(0.22, emergencyThrustHeat * 0.20);
        double propulsion = 0.82 + 0.18 * propulsionRoomIntegrity();
        return MathUtil.clamp(1.30 * heatPenalty * propulsion, 1.04, 1.34);
    }

    public boolean setEmergencyThrustMode(boolean enabled) {
        if (!enabled) {
            emergencyThrustActive = false;
            return true;
        }
        if (emergencyThrustCooldown > 1e-6) return false;
        if (propulsionRoomIntegrity() < 0.18) return false;
        if (overloadActive && overloadBus == PowerBus.PROPULSION) return false;
        emergencyThrustActive = true;
        return true;
    }

    public boolean toggleEmergencyThrustMode() {
        return setEmergencyThrustMode(!emergencyThrustActive);
    }

    public double propulsionRoomIntegrity() {
        ensureRoomSystemsInitialized();
        double engine = roomHealthFraction(ShipRoomLayout.RoomId.ENGINES);
        double warp = roomHealthFraction(ShipRoomLayout.RoomId.WARP_DRIVE);
        return MathUtil.clamp(engine * 0.65 + warp * 0.35, 0.0, 1.0);
    }

    public double propulsionMobilityMultiplier() {
        double r = propulsionRoomIntegrity();
        return MathUtil.clamp(0.24 + 0.76 * Math.pow(r, 0.90), 0.20, 1.0);
    }

    public double propulsionHandlingMultiplier() {
        double r = propulsionRoomIntegrity();
        return MathUtil.clamp(0.30 + 0.70 * Math.pow(r, 0.82), 0.24, 1.0);
    }

    public boolean setOverloadMode(boolean enabled) {
        if (!enabled) {
            overloadActive = false;
            return true;
        }
        if (overloadCooldownTimer > 1e-6) return false;
        if (emergencyThrustActive && overloadBus == PowerBus.PROPULSION) return false;
        overloadActive = true;
        return true;
    }

    public boolean toggleOverloadMode() {
        return setOverloadMode(!overloadActive);
    }

    public SubsystemState subsystemState(InternalSystem system) {
        if (system == null) return SubsystemState.NOMINAL;
        double health = systemHealthFraction(system);
        if (health <= 0.001) return SubsystemState.DESTROYED;

        PowerBus bus = powerBusForSystem(system);
        double busMul = powerBusEffectiveMultiplier(bus);
        if (health < 0.15 || busMul < 0.20) return SubsystemState.OFFLINE;

        double integrated = health * (0.58 + 0.42 * Math.min(1.0, busMul));
        if (integrated < 0.38) return SubsystemState.DAMAGED;

        if (health < 0.62
                || busMul < 0.86
                || overloadHeat >= 0.82
                || overloadStressDebt >= 0.85) {
            return SubsystemState.STRESSED;
        }
        return SubsystemState.NOMINAL;
    }

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

    public int shieldFaceCount() {
        return SHIELD_FACE_COUNT;
    }

    public String shieldFaceName(int face) {
        if (face < 0 || face >= SHIELD_FACE_COUNT) return "?";
        return SHIELD_FACE_NAMES[face];
    }

    public double shieldFaceValue(int face) {
        ensureShieldFacesSynced();
        if (face < 0 || face >= SHIELD_FACE_COUNT) return 0.0;
        return shieldFaces[face];
    }

    public double shieldFaceMax(int face) {
        if (face < 0 || face >= SHIELD_FACE_COUNT) return 0.0;
        return perFaceShieldMax();
    }

    public double shieldFaceFraction(int face) {
        double max = shieldFaceMax(face);
        if (max <= 1e-9) return 0.0;
        return Math.max(0.0, Math.min(1.0, shieldFaceValue(face) / max));
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
        out *= sensorPowerMultiplier();
        out *= crewSystemMul;
        double localFirePenalty = 0.0;
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.SENSORS) * 0.36;
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.BRIDGE) * 0.28;
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.POWER_CONDUITS) * 0.18;
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.INTEGRITY_FIELD) * 0.12;
        localFirePenalty += totalFireIntensity() * 0.05;
        out *= (1.0 - MathUtil.clamp(localFirePenalty, 0.0, 0.72));
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

    private void ensureRoomSystemsInitialized() {
        if (roomSystemsInitialized) return;
        ensureInternalSystemsInitialized();

        roomHp.clear();
        roomHpMax.clear();
        roomHazards.clear();

        double base = Math.max(24.0, hpMax + shieldMax * 0.35 + radius * 0.90);
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role)) {
            double max = Math.max(8.0, base * Math.max(0.25, def.hpWeight));
            roomHpMax.put(def.id, max);
            roomHp.put(def.id, max);
            roomHazards.put(def.id, new RoomHazardState(def.id));
        }

        roomDisabledSystems.clear();
        roomSystemsInitialized = true;
        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
    }

    private double totalRoomIntegrityFraction() {
        ensureRoomSystemsInitialized();
        double total = 0.0;
        double maxTotal = 0.0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role)) {
            double max = roomHpMax.getOrDefault(def.id, 0.0);
            if (max <= 0.0) continue;
            maxTotal += max;
            total += Math.max(0.0, roomHp.getOrDefault(def.id, max));
        }
        if (maxTotal <= 1e-9) return 1.0;
        return Math.max(0.0, Math.min(1.0, total / maxTotal));
    }

    private void syncHullFromRoomIntegrity() {
        if (hpMax <= 0) return;
        double frac = totalRoomIntegrityFraction();
        int derived = (int) Math.round(hpMax * frac);
        int min = (alive && !dying) ? 1 : 0;
        hp = Math.max(min, Math.min(hpMax, derived));
    }

    private void evaluateCondemnedStateFromRooms() {
        if (!alive || dying) return;
        if (totalRoomIntegrityFraction() > ROOM_CONDEMNED_THRESHOLD) return;
        hp = 0;
        startDeathSequence();
    }

    private void enforceRoomSystemAvailability() {
        ensureInternalSystemsInitialized();
        ensureRoomSystemsInitialized();
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role);
        for (InternalSystem system : InternalSystem.values()) {
            boolean hasMappedRoom = false;
            boolean anyOperationalRoom = false;
            for (ShipRoomLayout.RoomDef def : defs) {
                if (def.primarySystem != system) continue;
                hasMappedRoom = true;
                if (roomHp.getOrDefault(def.id, 0.0) > 1e-6) {
                    anyOperationalRoom = true;
                    break;
                }
            }
            if (!hasMappedRoom) continue;

            if (!anyOperationalRoom) {
                roomDisabledSystems.add(system);
                systemHp.put(system, 0.0);
                if (system == InternalSystem.SHIELDS) {
                    forceShieldOffline(Math.max(0.6, shieldRebootDelay * 0.65));
                }
                continue;
            }

            if (roomDisabledSystems.remove(system)) {
                Double max = systemHpMax.get(system);
                if (max != null && max > 0.0) {
                    double floor = max * 0.18;
                    double cur = systemHp.getOrDefault(system, 0.0);
                    if (cur < floor) systemHp.put(system, floor);
                }
            }
        }
    }

    private ShipRoomLayout.RoomDef resolveRoomForImpact(HullGeometry.ImpactSample impact, double hitX, double hitY) {
        if (impact != null && impact.onHull) {
            return ShipRoomLayout.roomForHit(role, impact.normalizedX, impact.normalizedY);
        }
        if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
            HullGeometry.ImpactSample snap = HullGeometry.sampleImpact(this, hitX, hitY, true);
            if (snap != null && snap.onHull) {
                return ShipRoomLayout.roomForHit(role, snap.normalizedX, snap.normalizedY);
            }
        }
        return null;
    }

    private HullGeometry.ImpactSample resolveHullImpactSample(double hitX, double hitY,
                                                              double impactVx, double impactVy) {
        HullGeometry.ImpactSample impact = HullGeometry.sampleImpact(this, hitX, hitY, true);
        if (impact != null && impact.onHull) return impact;

        if (Double.isFinite(impactVx) && Double.isFinite(impactVy)) {
            double vLen = Math.hypot(impactVx, impactVy);
            if (vLen > 1e-9) {
                double nx = -impactVx / vLen;
                double ny = -impactVy / vLen;
                double probe = Math.max(8.0, radius + 8.0);
                double wx = x + nx * probe;
                double wy = y + ny * probe;
                HullGeometry.ImpactSample fromVelocity = HullGeometry.sampleImpact(this, wx, wy, true);
                if (fromVelocity != null && fromVelocity.onHull) return fromVelocity;
            }
        }

        return impact;
    }

    private ShipRoomLayout.RoomDef resolvePrimaryRoomForHullHit(HullGeometry.ImpactSample impact,
                                                                 double hitX,
                                                                 double hitY,
                                                                 double impactVx,
                                                                 double impactVy) {
        ShipRoomLayout.RoomDef room = resolveRoomForImpact(impact, hitX, hitY);
        if (room != null) return room;

        if (impact != null && Double.isFinite(impact.normalizedX) && Double.isFinite(impact.normalizedY)) {
            room = RoomHitResolver.resolve(role, impact.normalizedX, impact.normalizedY);
            if (room != null) return room;
        }

        if (Double.isFinite(impactVx) && Double.isFinite(impactVy)) {
            double len = Math.hypot(impactVx, impactVy);
            if (len > 1e-9) {
                double wx = -impactVx / len;
                double wy = -impactVy / len;
                double c = Math.cos(angle);
                double s = Math.sin(angle);
                double lx = wx * c + wy * s;
                double ly = -wx * s + wy * c;
                room = RoomHitResolver.resolve(role, lx, ly);
                if (room != null) return room;
            }
        }

        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role);
        if (defs != null && !defs.isEmpty()) return defs.get(0);
        return null;
    }

    private void logRoomDamage(ShipRoomLayout.RoomId roomId, double normalizedX, double normalizedY,
                               double damage, boolean fromHazard) {
        if (roomId == null || damage <= 0.0) return;
        if (roomDamageEvents.size() >= MAX_ROOM_DAMAGE_EVENTS) roomDamageEvents.remove(0);
        roomDamageEvents.add(new RoomDamageEvent(roomId, normalizedX, normalizedY, damage, fromHazard));
    }

    private void igniteRoomFire(ShipRoomLayout.RoomId roomId, double intensity) {
        if (roomId == null || intensity <= 0.0) return;
        ensureRoomSystemsInitialized();
        RoomHazardState hz = roomHazards.get(roomId);
        if (hz == null) {
            hz = new RoomHazardState(roomId);
            roomHazards.put(roomId, hz);
        }
        hz.fireIntensity = Math.max(hz.fireIntensity, MathUtil.clamp(intensity, 0.0, 2.6));
        if (hz.damageTickTimer <= 0.0) hz.damageTickTimer = 0.18 + Math.random() * 0.16;
        if (hz.spreadTimer <= 0.0) hz.spreadTimer = 0.50 + Math.random() * 0.45;
        if (hz.instabilityTimer <= 0.0) hz.instabilityTimer = 0.40 + Math.random() * 0.35;
        if (hz.vfxTimer <= 0.0) hz.vfxTimer = 0.06 + Math.random() * 0.08;
        hz.suppressionBoost = Math.max(0.0, hz.suppressionBoost - intensity * 0.08);
    }

    private void damageRoom(ShipRoomLayout.RoomDef room, double damage,
                            double normalizedX, double normalizedY, boolean fromHazard) {
        damageRoom(room, damage, normalizedX, normalizedY, fromHazard, true);
    }

    private void damageRoom(ShipRoomLayout.RoomDef room, double damage,
                            double normalizedX, double normalizedY, boolean fromHazard,
                            boolean allowSaturation) {
        if (room == null || damage <= 0.0) return;
        ensureRoomSystemsInitialized();

        double max = roomHpMax.getOrDefault(room.id, 0.0);
        if (max <= 0.0) return;
        double before = roomHp.getOrDefault(room.id, max);
        int hullBefore = hp;
        int hazardRolls = 0;
        List<String> subsystemTransitions = new ArrayList<>(4);
        noDamageTimerSeconds = 0.0;
        instantRepairConsumed = false;

        // Damage saturation: if this room is already destroyed, spread hit damage
        // evenly across nearby operational rooms.
        if (allowSaturation && !fromHazard && before <= 1e-6) {
            List<ShipRoomLayout.RoomDef> recipients = new ArrayList<>();
            for (ShipRoomLayout.RoomId rid : room.neighbors) {
                ShipRoomLayout.RoomDef n = ShipRoomLayout.roomForId(role, rid);
                if (n == null) continue;
                if (roomHp.getOrDefault(n.id, 0.0) > 1e-6) recipients.add(n);
            }
            if (recipients.isEmpty()) {
                for (ShipRoomLayout.RoomDef any : ShipRoomLayout.profileFor(role)) {
                    if (any.id == room.id) continue;
                    if (roomHp.getOrDefault(any.id, 0.0) > 1e-6) recipients.add(any);
                }
            }
            if (!recipients.isEmpty()) {
                double split = damage / recipients.size();
                double redirectedRoomLoss = 0.0;
                int redirectedHazardRolls = 0;
                for (ShipRoomLayout.RoomDef recipient : recipients) {
                    damageRoom(recipient, split, normalizedX, normalizedY, false, false);
                    RoomDamageResult redirected = lastRoomDamageResult;
                    if (redirected != null && redirected != RoomDamageResult.NONE) {
                        redirectedRoomLoss += redirected.roomLocalHpLoss;
                        redirectedHazardRolls += redirected.hazardRolls;
                        subsystemTransitions.addAll(redirected.subsystemTransitions);
                    }
                }
                logRoomDamage(room.id, normalizedX, normalizedY, damage, false);
                syncHullFromRoomIntegrity();
                evaluateCondemnedStateFromRooms();
                double afterSpread = roomHp.getOrDefault(room.id, before);
                lastRoomDamageResult = new RoomDamageResult(
                        room.id.name(),
                        before,
                        afterSpread,
                        redirectedHazardRolls,
                        subsystemTransitions,
                        hp - hullBefore,
                        redirectedRoomLoss
                );
            }
            return;
        }

        double after = Math.max(0.0, before - damage);
        roomHp.put(room.id, after);
        logRoomDamage(room.id, normalizedX, normalizedY, damage, fromHazard);

        if (room.primarySystem != null) {
            double sysBefore = systemHealthFraction(room.primarySystem);
            double sysScale = fromHazard ? 0.68 : 0.70;
            damageSystem(room.primarySystem, damage * sysScale);
            double sysAfter = systemHealthFraction(room.primarySystem);
            if (sysBefore > 1e-6 && sysAfter <= 1e-6) {
                subsystemTransitions.add(room.primarySystem.name() + ":offline");
            }
        }

        if (room.id == ShipRoomLayout.RoomId.POWER_CONDUITS && !fromHazard) {
            hazardRolls++;
            if (Math.random() < 0.20) {
                InternalSystem redirected = pickSystem(InternalSystem.SHIELDS, InternalSystem.WEAPONS, InternalSystem.SENSORS);
                if (redirected != null) {
                    double sysBefore = systemHealthFraction(redirected);
                    damageSystem(redirected, damage * 0.22);
                    double sysAfter = systemHealthFraction(redirected);
                    if (sysBefore > 1e-6 && sysAfter <= 1e-6) {
                        subsystemTransitions.add(redirected.name() + ":offline");
                    }
                }
            }
        }

        if (room.id == ShipRoomLayout.RoomId.INTEGRITY_FIELD && after <= max * 0.20) {
            hazardRolls++;
            if (Math.random() < 0.08) {
                forceShieldOffline(Math.max(0.8, shieldRebootDelay * 0.55));
                subsystemTransitions.add(InternalSystem.SHIELDS.name() + ":offline");
            }
        }

        if (!fromHazard) {
            hazardRolls++;
            double fracLost = (before - after) / Math.max(1e-6, max);
            double ignitionChance = 0.10 + fracLost * 0.50;
            if (room.critical) ignitionChance += 0.12;
            if (Math.random() < MathUtil.clamp(ignitionChance, 0.0, 0.88)) {
                igniteRoomFire(room.id, 0.40 + fracLost * 1.45);
                subsystemTransitions.add("hazard:fire_ignited");
            }
        }

        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
        evaluateCondemnedStateFromRooms();
        lastRoomDamageResult = new RoomDamageResult(
                room.id.name(),
                before,
                after,
                hazardRolls,
                subsystemTransitions,
                hp - hullBefore,
                Math.max(0.0, before - after)
        );
    }

    private void updateRoomHazards(double dt) {
        if (dt <= 0.0) return;
        ensureRoomSystemsInitialized();
        if (roomHazards.isEmpty()) return;

        for (RoomHazardState hz : roomHazards.values()) {
            if (hz == null || hz.roomId == null) continue;
            if (!hz.active()) {
                hz.fireIntensity = 0.0;
                hz.damageTickTimer = 0.0;
                hz.spreadTimer = 0.0;
                hz.instabilityTimer = 0.0;
                hz.vfxTimer = 0.0;
                hz.suppressionBoost = Math.max(0.0, hz.suppressionBoost - dt * 0.90);
                continue;
            }

            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, hz.roomId);
            double roomFrac = roomHealthFraction(hz.roomId);
            double fuel = 0.20 + (1.0 - roomFrac) * 0.95 + ((def != null && def.critical) ? 0.18 : 0.0);
            double growthRate = (hz.fireIntensity < 1.25)
                    ? (0.045 + fuel * 0.070)
                    : (0.015 + fuel * 0.024);
            double passiveSuppression = (crewOrder == CrewOrder.DAMAGE_CONTROL)
                    ? (0.05 + 0.12 * crewReadiness * engineeringPowerMultiplier())
                    : 0.0;
            double suppressionRate = 0.018 + passiveSuppression + hz.suppressionBoost * 0.22;
            double burnoutRate = (roomFrac < 0.24) ? (0.010 + (0.24 - roomFrac) * 0.22) : 0.0;
            hz.fireIntensity = MathUtil.clamp(
                    hz.fireIntensity + (growthRate - suppressionRate - burnoutRate) * dt,
                    0.0,
                    2.8
            );
            hz.suppressionBoost = Math.max(0.0, hz.suppressionBoost - dt * (0.42 + hz.fireIntensity * 0.12));

            hz.damageTickTimer -= dt;
            if (hz.damageTickTimer <= 0.0) {
                if (def != null) {
                    double roomDmg = Math.max(0.8, hz.fireIntensity * (1.6 + Math.random() * 1.8));
                    if (def.critical) roomDmg *= 1.24;
                    roomDmg *= (1.0 - Math.min(0.45, hz.suppressionBoost * 0.20));
                    damageRoom(def, roomDmg, Double.NaN, Double.NaN, true);
                    applyHazardSubsystemInstability(def, hz);
                }
                hz.damageTickTimer = 0.34 + Math.random() * 0.36 + Math.min(0.24, hz.suppressionBoost * 0.12);
            }

            hz.instabilityTimer -= dt;
            if (hz.instabilityTimer <= 0.0 && hz.fireIntensity > 0.55) {
                if (def != null) applyHazardSubsystemInstability(def, hz);
                hz.instabilityTimer = 0.48 + Math.random() * 0.52;
            }

            hz.spreadTimer -= dt;
            if (hz.spreadTimer <= 0.0) {
                double spreadChance = MathUtil.clamp(
                        0.10 + hz.fireIntensity * 0.26 + roomFuelFactor(hz.roomId) * 0.14 - hz.suppressionBoost * 0.30,
                        0.0,
                        0.90
                );
                if (def != null && def.neighbors.length > 0 && Math.random() < spreadChance) {
                    java.util.ArrayList<ShipRoomLayout.RoomId> eligible = new java.util.ArrayList<>();
                    for (ShipRoomLayout.RoomId nid : def.neighbors) {
                        if (nid == null) continue;
                        double nMax = roomHpMax.getOrDefault(nid, 0.0);
                        if (nMax <= 1e-9) continue;
                        double nCur = roomHp.getOrDefault(nid, nMax);
                        double frac = nCur / nMax;
                        RoomHazardState nHz = roomHazards.get(nid);
                        if (nHz != null && nHz.fireIntensity > 1.10) continue;
                        if (frac < 0.98 || Math.random() < 0.28) {
                            eligible.add(nid);
                        }
                    }
                    if (!eligible.isEmpty()) {
                        int idx = (int) Math.floor(Math.random() * eligible.size());
                        if (idx < 0 || idx >= eligible.size()) idx = 0;
                        double spreadIntensity = MathUtil.clamp(
                                0.30 + hz.fireIntensity * (0.42 + Math.random() * 0.30),
                                0.20,
                                2.2
                        );
                        igniteRoomFire(eligible.get(idx), spreadIntensity);
                    }
                }
                hz.spreadTimer = 0.56 + Math.random() * 0.62 + Math.min(0.20, hz.suppressionBoost * 0.10);
            }

            hz.vfxTimer -= dt;
            if (hz.vfxTimer <= 0.0) {
                spawnRoomFireVfx(def, hz.fireIntensity);
                hz.vfxTimer = Math.max(0.04, 0.20 + Math.random() * 0.16 - hz.fireIntensity * 0.05);
            }

            if (hz.fireIntensity <= 0.02) {
                hz.fireIntensity = 0.0;
                hz.damageTickTimer = 0.0;
                hz.spreadTimer = 0.0;
                hz.instabilityTimer = 0.0;
                hz.vfxTimer = 0.0;
            }
        }
    }

    private void ensurePowerInitialized() {
        if (powerEngines == 0.0
                && powerShields == 0.0
                && powerWeapons == 0.0
                && powerSensors == 0.0
                && powerEngineering == 0.0
                && powerAuxiliary == 0.0) {
            setPowerPreset(powerPreset);
            return;
        }
        normalizePowerAllocation();
    }

    private void updatePowerBusState(double dt) {
        if (dt <= 0.0) return;
        overloadCooldownTimer = Math.max(0.0, overloadCooldownTimer - dt);
        overloadStressDebt = Math.max(0.0, overloadStressDebt - dt * 0.008);

        if (overloadActive) {
            if (overloadCooldownTimer > 1e-6) {
                overloadActive = false;
            } else {
                double busLoad = powerBusFraction(overloadBus);
                double heatGain = dt * (0.18 + busLoad * 0.42 + overloadStressDebt * 0.11);
                overloadHeat += heatGain;
                overloadStressDebt += dt * (0.035 + busLoad * 0.09);
                if (overloadHeat >= 1.0) {
                    overloadActive = false;
                    overloadHeat = 1.0;
                    overloadCooldownTimer = Math.max(overloadCooldownTimer, 2.8 + overloadStressDebt * 4.2);
                    applyOverloadCollapsePenalty();
                }
            }
        } else {
            double coolRate = dt * (0.12 + powerEngineering * 0.16 + crewSystemMul * 0.05);
            overloadHeat = Math.max(0.0, overloadHeat - coolRate);
            if (overloadCooldownTimer <= 0.0 && overloadHeat < 0.22) {
                overloadStressDebt = Math.max(0.0, overloadStressDebt - dt * 0.040);
            }
        }
        overloadStressDebt = MathUtil.clamp(overloadStressDebt, 0.0, 2.2);
    }

    private void updateEmergencyThrustState(double dt) {
        if (dt <= 0.0) return;
        emergencyThrustCooldown = Math.max(0.0, emergencyThrustCooldown - dt);
        if (emergencyThrustActive) {
            double propulsion = propulsionRoomIntegrity();
            emergencyThrustHeat += dt * (0.16 + (1.0 - propulsion) * 0.32 + overloadHeat * 0.10);
            if (emergencyThrustHeat >= 1.0 || propulsion < 0.14) {
                emergencyThrustActive = false;
                emergencyThrustHeat = 1.0;
                emergencyThrustCooldown = Math.max(emergencyThrustCooldown, 2.2 + (1.0 - propulsion) * 2.8);
                applyEmergencyThrustFailure();
            }
        } else {
            double cool = dt * (0.14 + engineeringPowerMultiplier() * 0.12 + crewEngineMul * 0.05);
            emergencyThrustHeat = Math.max(0.0, emergencyThrustHeat - cool);
        }
    }

    private void applyEmergencyThrustFailure() {
        Double engMax = systemHpMax.get(InternalSystem.ENGINES);
        if (engMax != null && engMax > 1e-6) {
            damageSystem(InternalSystem.ENGINES, engMax * 0.05);
        }
        if (Math.random() < 0.32) {
            Double warpMax = systemHpMax.get(InternalSystem.WARP_ENGINES);
            if (warpMax != null && warpMax > 1e-6) {
                damageSystem(InternalSystem.WARP_ENGINES, warpMax * 0.05);
            }
        }
        if (Math.random() < 0.18) {
            Double reactorMax = systemHpMax.get(InternalSystem.REACTOR_CORE);
            if (reactorMax != null && reactorMax > 1e-6) {
                damageSystem(InternalSystem.REACTOR_CORE, reactorMax * 0.03);
            }
        }
        crewCombatStress = Math.max(crewCombatStress, 2.8);
    }

    private void applyOverloadCollapsePenalty() {
        InternalSystem focus = switch (overloadBus) {
            case PROPULSION -> InternalSystem.ENGINES;
            case SHIELD -> InternalSystem.SHIELDS;
            case TACTICAL -> InternalSystem.WEAPONS;
            case SENSOR -> InternalSystem.SENSORS;
            case ENGINEERING -> InternalSystem.REACTOR_CORE;
            case AUXILIARY -> InternalSystem.BRIDGE;
        };
        double max = systemHpMax.getOrDefault(focus, 0.0);
        if (max > 1e-6) {
            damageSystem(focus, max * 0.06);
        }
        if (overloadBus == PowerBus.SHIELD) {
            forceShieldOffline(Math.max(0.9, shieldRebootDelay * 0.80));
        }
    }

    private void normalizePowerAllocation() {
        double sum = powerEngines
                + powerShields
                + powerWeapons
                + powerSensors
                + powerEngineering
                + powerAuxiliary;
        if (sum <= 1e-6) {
            powerEngines = 0.18;
            powerShields = 0.18;
            powerWeapons = 0.19;
            powerSensors = 0.15;
            powerEngineering = 0.18;
            powerAuxiliary = 0.12;
            return;
        }
        powerEngines /= sum;
        powerShields /= sum;
        powerWeapons /= sum;
        powerSensors /= sum;
        powerEngineering /= sum;
        powerAuxiliary /= sum;
        powerSystems = powerSensors + powerEngineering + powerAuxiliary;
    }

    private PowerBus powerBusForSystem(InternalSystem system) {
        if (system == null) return PowerBus.ENGINEERING;
        return switch (system) {
            case ENGINES, WARP_ENGINES -> PowerBus.PROPULSION;
            case SHIELDS -> PowerBus.SHIELD;
            case WEAPONS, MAGAZINES -> PowerBus.TACTICAL;
            case SENSORS, BRIDGE -> PowerBus.SENSOR;
            case REACTOR_CORE -> PowerBus.ENGINEERING;
        };
    }

    private double powerBusNominalTarget(PowerBus bus) {
        if (bus == null) return 0.16;
        return switch (bus) {
            case PROPULSION, SHIELD, TACTICAL, ENGINEERING -> 0.18;
            case SENSOR -> 0.15;
            case AUXILIARY -> 0.12;
        };
    }

    private double powerBusEffectiveMultiplier(PowerBus bus) {
        PowerBus b = (bus == null) ? PowerBus.ENGINEERING : bus;
        double alloc = powerBusFraction(b);
        double target = Math.max(0.05, powerBusNominalTarget(b));
        double ratio = alloc / target;
        double mul;
        if (ratio >= 1.0) {
            mul = 1.0 + 0.30 * Math.tanh((ratio - 1.0) * 1.9);
        } else {
            mul = Math.pow(Math.max(0.02, ratio), 1.65);
        }

        if (overloadActive && b == overloadBus) {
            mul *= 1.24;
        }
        if (overloadCooldownTimer > 0.0) {
            double coolPenalty = 0.08 + Math.min(0.26, overloadStressDebt * 0.11 + overloadHeat * 0.16);
            mul *= (1.0 - coolPenalty);
        }
        if (overloadStressDebt > 0.0) {
            mul *= (1.0 - Math.min(0.20, overloadStressDebt * 0.07));
        }
        return MathUtil.clamp(mul, 0.08, 1.55);
    }

    private double enginesPowerMultiplier() {
        return powerBusEffectiveMultiplier(PowerBus.PROPULSION);
    }

    private double shieldsPowerMultiplier() {
        return powerBusEffectiveMultiplier(PowerBus.SHIELD);
    }

    private double weaponsPowerMultiplier() {
        return powerBusEffectiveMultiplier(PowerBus.TACTICAL);
    }

    private double systemsPowerMultiplier() {
        double engineering = powerBusEffectiveMultiplier(PowerBus.ENGINEERING);
        double sensor = powerBusEffectiveMultiplier(PowerBus.SENSOR);
        double auxiliary = powerBusEffectiveMultiplier(PowerBus.AUXILIARY);
        return MathUtil.clamp(engineering * 0.45 + sensor * 0.35 + auxiliary * 0.20, 0.10, 1.45);
    }

    private double engineeringPowerMultiplier() {
        return powerBusEffectiveMultiplier(PowerBus.ENGINEERING);
    }

    private double sensorPowerMultiplier() {
        return powerBusEffectiveMultiplier(PowerBus.SENSOR);
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

        double fireLoad = totalFireIntensity();
        int fireRooms = activeFireRoomCount();
        if (fireRooms > 0) {
            double diversion = MathUtil.clamp(fireRooms * 0.07 + fireLoad * 0.05, 0.0, 0.48);
            if (crewOrder != CrewOrder.DAMAGE_CONTROL) {
                weapons *= (1.0 - diversion * 0.70);
                engines *= (1.0 - diversion * 0.38);
                shields *= (1.0 - diversion * 0.44);
                systems *= (1.0 - diversion * 0.32);
            } else {
                systems *= (1.0 + Math.min(0.22, fireLoad * 0.09));
            }
            base *= (1.0 - diversion * 0.30);
            crewCombatStress = Math.max(crewCombatStress, 0.8 + fireLoad * 0.55);
        }

        crewEngineMul = MathUtil.clamp(base * engines, 0.35, 1.30);
        crewShieldMul = MathUtil.clamp(base * shields, 0.30, 1.35);
        crewWeaponMul = MathUtil.clamp(base * weapons, 0.28, 1.35);
        crewSystemMul = MathUtil.clamp(base * systems, 0.30, 1.35);
    }

    private void repairDamagedSystems(double amount) {
        if (amount <= 0.0) return;
        InternalSystem lowest = repairPrioritySystemTarget();
        double lowestFrac = (lowest == null) ? 1.0 : systemHealthFraction(lowest);
        if (lowest == null || lowestFrac >= 0.999) return;
        Double hpv = systemHp.get(lowest);
        Double maxv = systemHpMax.get(lowest);
        if (hpv == null || maxv == null || maxv <= 0.0) return;
        hpv = Math.min(maxv, hpv + amount * maxv * 0.16 * engineeringPowerMultiplier());
        systemHp.put(lowest, hpv);

        ensureRoomSystemsInitialized();
        ShipRoomLayout.RoomId lowestRoom = null;
        double roomFrac = 1.0;
        for (ShipRoomLayout.RoomId rid : roomHp.keySet()) {
            double maxRoom = roomHpMax.getOrDefault(rid, 0.0);
            if (maxRoom <= 1e-9) continue;
            double frac = roomHp.getOrDefault(rid, maxRoom) / maxRoom;
            if (frac < roomFrac) {
                roomFrac = frac;
                lowestRoom = rid;
            }
        }
        if (lowestRoom != null) {
            double maxRoom = roomHpMax.getOrDefault(lowestRoom, 0.0);
            if (maxRoom > 0.0) {
                double curRoom = roomHp.getOrDefault(lowestRoom, maxRoom);
                curRoom = Math.min(maxRoom, curRoom + amount * maxRoom * 0.10 * engineeringPowerMultiplier());
                roomHp.put(lowestRoom, curRoom);
            }
            applyFireSuppression(lowestRoom, amount * 0.22, false);
        }
        ShipRoomLayout.RoomId hottestFire = hottestFireRoom();
        if (hottestFire != null && hottestFire != lowestRoom) {
            applyFireSuppression(hottestFire, amount * 0.24, false);
        }

        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
    }

    private double applyFireSuppression(ShipRoomLayout.RoomId roomId, double effort, boolean manual) {
        if (roomId == null || effort <= 0.0) return 0.0;
        ensureRoomSystemsInitialized();
        RoomHazardState hz = roomHazards.get(roomId);
        if (hz == null || hz.fireIntensity <= 1e-4) return 0.0;

        double before = hz.fireIntensity;
        double scale = 0.70 + 0.65 * engineeringPowerMultiplier() + 0.35 * crewReadiness;
        if (manual) scale *= 1.22;
        if (crewOrder == CrewOrder.DAMAGE_CONTROL) scale *= 1.12;
        double reduced = effort * scale;
        hz.fireIntensity = Math.max(0.0, hz.fireIntensity - reduced);
        hz.suppressionBoost = Math.min(2.2, hz.suppressionBoost + effort * (manual ? 1.8 : 0.9));
        hz.damageTickTimer = Math.max(0.16, hz.damageTickTimer + effort * 0.10);
        hz.spreadTimer = Math.max(hz.spreadTimer, 0.40 + effort * 0.60);
        if (hz.fireIntensity <= 0.02) {
            hz.fireIntensity = 0.0;
            hz.damageTickTimer = 0.0;
            hz.spreadTimer = 0.0;
            hz.instabilityTimer = 0.0;
            hz.vfxTimer = 0.0;
        }
        return before - hz.fireIntensity;
    }

    private String hazardSuppressionState(RoomHazardState hz) {
        if (hz == null || hz.fireIntensity <= 0.02) return "contained";
        if (hz.suppressionBoost > 0.35) return "active";
        if (crewOrder == CrewOrder.DAMAGE_CONTROL) return "passive";
        if (hz.fireIntensity > 1.35) return "overwhelmed";
        return "passive";
    }

    private double roomFuelFactor(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return 0.0;
        double max = roomHpMax.getOrDefault(roomId, 0.0);
        if (max <= 1e-9) return 0.0;
        double cur = roomHp.getOrDefault(roomId, max);
        return MathUtil.clamp(1.0 - (cur / max), 0.0, 1.0);
    }

    private void applyHazardSubsystemInstability(ShipRoomLayout.RoomDef room, RoomHazardState hz) {
        if (room == null || hz == null) return;
        double chance = MathUtil.clamp(0.06 + hz.fireIntensity * 0.18 + (room.critical ? 0.08 : 0.0), 0.0, 0.72);
        if (Math.random() > chance) return;

        InternalSystem primary = (room.primarySystem != null) ? room.primarySystem : secondaryInstabilitySystem(room.id);
        if (primary != null) {
            double max = systemHpMax.getOrDefault(primary, 0.0);
            if (max > 1e-6) {
                damageSystem(primary, max * (0.007 + hz.fireIntensity * 0.012));
            }
        }

        InternalSystem secondary = secondaryInstabilitySystem(room.id);
        if (secondary != null && secondary != primary && Math.random() < 0.45) {
            double max = systemHpMax.getOrDefault(secondary, 0.0);
            if (max > 1e-6) {
                damageSystem(secondary, max * (0.004 + hz.fireIntensity * 0.008));
            }
        }

        if (room.id == ShipRoomLayout.RoomId.POWER_CONDUITS
                && hz.fireIntensity > 1.35
                && Math.random() < 0.16) {
            forceShieldOffline(Math.max(0.5, shieldRebootDelay * 0.35));
        }
        if (room.id == ShipRoomLayout.RoomId.MAGAZINES
                && hz.fireIntensity > 1.10
                && Math.random() < 0.12) {
            double weaponMax = systemHpMax.getOrDefault(InternalSystem.WEAPONS, 0.0);
            if (weaponMax > 1e-6) {
                damageSystem(InternalSystem.WEAPONS, weaponMax * 0.015);
            }
        }
    }

    private InternalSystem secondaryInstabilitySystem(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return null;
        return switch (roomId) {
            case REACTOR, POWER_CONDUITS -> InternalSystem.SHIELDS;
            case INTEGRITY_FIELD -> InternalSystem.REACTOR_CORE;
            case SENSORS, BRIDGE -> InternalSystem.BRIDGE;
            case MAIN_WEAPON, MISSILE_LAUNCHERS, MAGAZINES -> InternalSystem.WEAPONS;
            case ENGINES, WARP_DRIVE -> InternalSystem.WARP_ENGINES;
        };
    }

    private void spawnRoomFireVfx(ShipRoomLayout.RoomDef room, double intensity) {
        if (intensity <= 0.05) return;
        double localX = (Math.random() - 0.5) * radius * 0.24;
        double localY = (Math.random() - 0.5) * radius * 0.24;

        if (room != null && room.xs != null && room.ys != null) {
            int n = Math.min(room.xs.length, room.ys.length);
            if (n > 0) {
                double cx = 0.0;
                double cy = 0.0;
                for (int i = 0; i < n; i++) {
                    cx += room.xs[i];
                    cy += room.ys[i];
                }
                cx /= n;
                cy /= n;
                double jitter = 0.10 + Math.min(0.22, intensity * 0.06);
                localX = (cx + (Math.random() - 0.5) * jitter) * radius;
                localY = (cy + (Math.random() - 0.5) * jitter) * radius;
            }
        }

        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        double wx = x + localX * ca - localY * sa;
        double wy = y + localX * sa + localY * ca;
        VFX.spawnShipFire(wx, wy, Math.min(2.0, 0.45 + intensity * 0.65));
    }

    private InternalSystem repairPrioritySystemTarget() {
        InternalSystem preferred = switch (engineeringPriority) {
            case REACTOR -> InternalSystem.REACTOR_CORE;
            case PROPULSION -> pickLowerHealth(InternalSystem.ENGINES, InternalSystem.WARP_ENGINES);
            case SHIELDS -> InternalSystem.SHIELDS;
            case WEAPONS -> pickLowerHealth(InternalSystem.WEAPONS, InternalSystem.MAGAZINES);
            case SENSORS -> pickLowerHealth(InternalSystem.SENSORS, InternalSystem.BRIDGE);
            default -> null;
        };
        if (preferred != null && systemHealthFraction(preferred) < 0.999) return preferred;

        InternalSystem lowest = null;
        double lowestFrac = 1.0;
        for (InternalSystem s : InternalSystem.values()) {
            double f = systemHealthFraction(s);
            if (f < lowestFrac) {
                lowestFrac = f;
                lowest = s;
            }
        }
        return lowest;
    }

    private InternalSystem pickLowerHealth(InternalSystem a, InternalSystem b) {
        if (a == null) return b;
        if (b == null) return a;
        double af = systemHealthFraction(a);
        double bf = systemHealthFraction(b);
        return (af <= bf) ? a : b;
    }

    private void updateShieldFacing(double dt) {
        if (!shieldActive || shieldMax <= 0.0) return;
        if (!Double.isFinite(shieldFacingAngle)) shieldFacingAngle = angle;
        ensureShieldFacesSynced();

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
            target = autoFacingTargetForThreat(recentShieldImpactAngle);
        }

        double delta = MathUtil.normalizeAngle(target - shieldFacingAngle);
        double maxStep = Math.max(0.0, shieldAutoTrackRate * dt);
        delta = MathUtil.clamp(delta, -maxStep, maxStep);
        shieldFacingAngle = MathUtil.normalizeAngle(shieldFacingAngle + delta);
    }

    private int bestShieldFaceForSwap(int activeFace) {
        int out = activeFace;
        double activeFrac = shieldFaceFraction(activeFace);
        double bestFrac = activeFrac;
        int opposite = oppositeShieldFace(activeFace);
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
            if (i == activeFace) continue;
            if (i == opposite) continue;
            double frac = shieldFaceFraction(i);
            if (frac <= 0.01) continue;
            if (frac > bestFrac + SHIELD_FACE_SWAP_ADVANTAGE_FRAC) {
                bestFrac = frac;
                out = i;
            }
        }
        return out;
    }

    private int oppositeShieldFace(int face) {
        return switch (face) {
            case SHIELD_FACE_FORE -> SHIELD_FACE_REAR;
            case SHIELD_FACE_REAR -> SHIELD_FACE_FORE;
            case SHIELD_FACE_LEFT -> SHIELD_FACE_RIGHT;
            case SHIELD_FACE_RIGHT -> SHIELD_FACE_LEFT;
            default -> -1;
        };
    }

    private double targetFacingAngleForFaceAtThreat(double threatAngle, int face) {
        if (!Double.isFinite(threatAngle)) return getShieldFacingAngle();
        return switch (face) {
            case SHIELD_FACE_FORE -> threatAngle;
            case SHIELD_FACE_LEFT -> MathUtil.normalizeAngle(threatAngle + Math.PI * 0.5);
            case SHIELD_FACE_RIGHT -> MathUtil.normalizeAngle(threatAngle - Math.PI * 0.5);
            case SHIELD_FACE_REAR -> MathUtil.normalizeAngle(threatAngle - Math.PI);
            default -> threatAngle;
        };
    }

    private double autoFacingTargetForThreat(double threatAngle) {
        if (!Double.isFinite(threatAngle)) return getShieldFacingAngle();
        double target = threatAngle;

        // Determine which face is currently absorbing this threat and swap if it's near collapse.
        int activeFace = shieldFaceForImpactAngle(threatAngle, getShieldFacingAngle());
        double activeFrac = shieldFaceFraction(activeFace);
        if (activeFrac <= SHIELD_FACE_SWAP_TRIGGER_FRAC) {
            int replacement = bestShieldFaceForSwap(activeFace);
            if (replacement != activeFace) {
                target = targetFacingAngleForFaceAtThreat(threatAngle, replacement);
            }
        }
        return target;
    }

    private double directionalShieldDamageScaleFromAngle(double hitAngle, double facingAngle) {
        if (!Double.isFinite(hitAngle)) return 1.0;
        if (!Double.isFinite(facingAngle)) return 1.0;

        double rel = Math.abs(MathUtil.normalizeAngle(hitAngle - facingAngle));
        double halfArc = Math.max(Math.toRadians(35.0), shieldDirectionalArc * 0.5);
        double arcT = MathUtil.clamp(rel / halfArc, 0.0, 1.0);

        // In-arc hits are mitigated heavily, out-of-arc hits leak through.
        double arcScale = 0.62 + arcT * 0.72;
        double systems = Math.max(0.30, Math.min(1.30, shieldSystemMultiplier() * systemsPowerMultiplier() * crewShieldMul));
        double powerScale = 1.22 - 0.36 * shieldsPowerMultiplier();
        double total = arcScale * powerScale / Math.max(0.35, systems);
        return Math.max(0.35, Math.min(1.95, total));
    }

    private double perFaceShieldMax() {
        return (shieldMax <= 0.0) ? 0.0 : (shieldMax / SHIELD_FACE_COUNT);
    }

    private void ensureShieldFacesSynced() {
        if (!shieldFacesInitialized) {
            syncShieldFacesFromAggregate();
            return;
        }

        double max = Math.max(0.0, shieldMax);
        double clampedShield = MathUtil.clamp(shield, 0.0, max);
        double faceSum = 0.0;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) faceSum += Math.max(0.0, shieldFaces[i]);

        boolean shieldEditedExternally = Math.abs(faceSum - clampedShield) > 1e-3;
        boolean maxChanged = Math.abs(max - shieldFacesSyncedMax) > 1e-6;
        if (shieldEditedExternally || maxChanged) {
            syncShieldFacesFromAggregate();
            return;
        }

        shield = clampedShield;
    }

    private void syncShieldFacesFromAggregate() {
        double max = Math.max(0.0, shieldMax);
        if (!shieldActive || max <= 0.0) {
            for (int i = 0; i < SHIELD_FACE_COUNT; i++) shieldFaces[i] = 0.0;
            shield = 0.0;
            shieldFacesSyncedMax = max;
            shieldFacesInitialized = true;
            return;
        }

        shield = MathUtil.clamp(shield, 0.0, max);
        double perFace = max / SHIELD_FACE_COUNT;
        double each = shield / SHIELD_FACE_COUNT;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
            shieldFaces[i] = Math.min(perFace, each);
        }
        shieldFacesSyncedMax = max;
        shieldFacesInitialized = true;
    }

    private void syncAggregateShieldFromFaces() {
        double max = Math.max(0.0, shieldMax);
        double perFace = perFaceShieldMax();
        double total = 0.0;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
            shieldFaceRegenLock[i] = Math.max(0.0, shieldFaceRegenLock[i]);
            shieldFaces[i] = MathUtil.clamp(shieldFaces[i], 0.0, perFace);
            total += shieldFaces[i];
        }
        shield = MathUtil.clamp(total, 0.0, max);
        shieldFacesSyncedMax = max;
        shieldFacesInitialized = true;
    }

    private void updateShieldFaceRegenLocks(double dt) {
        if (dt <= 0.0) return;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
            if (shieldFaceRegenLock[i] <= 0.0) continue;
            shieldFaceRegenLock[i] -= dt;
            if (shieldFaceRegenLock[i] < 0.0) shieldFaceRegenLock[i] = 0.0;
        }
    }

    private void distributeShieldRegen(double amount) {
        if (amount <= 0.0 || shieldMax <= 0.0 || !shieldActive) return;
        ensureShieldFacesSynced();

        double rem = amount;
        double perFace = perFaceShieldMax();
        for (int pass = 0; pass < 4 && rem > 1e-6; pass++) {
            int open = 0;
            for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
                if (shieldFaceRegenLock[i] > 0.0) continue;
                if (shieldFaces[i] + 1e-9 < perFace) open++;
            }
            if (open <= 0) break;

            double share = rem / open;
            double consumed = 0.0;
            for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
                if (shieldFaceRegenLock[i] > 0.0) continue;
                double room = perFace - shieldFaces[i];
                if (room <= 1e-9) continue;
                double add = Math.min(room, share);
                shieldFaces[i] += add;
                consumed += add;
            }
            rem -= consumed;
            if (consumed <= 1e-9) break;
        }

        syncAggregateShieldFromFaces();
    }

    private double resolveShieldImpactAngle(double hitX, double hitY, double impactVx, double impactVy) {
        if (Double.isFinite(impactVx) && Double.isFinite(impactVy)) {
            double v2 = impactVx * impactVx + impactVy * impactVy;
            if (v2 > 1e-8) {
                // Prefer the projectile's previous position to identify which side it approached from.
                if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
                    double prevX = hitX - impactVx;
                    double prevY = hitY - impactVy;
                    double dx = prevX - x;
                    double dy = prevY - y;
                    if (dx * dx + dy * dy > 1e-8) {
                        return Math.atan2(dy, dx);
                    }
                }

                // Fallback: source direction is opposite travel vector.
                return MathUtil.normalizeAngle(Math.atan2(impactVy, impactVx) + Math.PI);
            }
        }
        if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
            return Math.atan2(hitY - y, hitX - x);
        }
        return Double.NaN;
    }

    private double resolveShieldThreatFacingAngle(double hitX, double hitY, double impactVx, double impactVy) {
        return resolveShieldImpactAngle(hitX, hitY, impactVx, impactVy);
    }

    private int shieldFaceForImpactAngle(double hitAngle) {
        return shieldFaceForImpactAngle(hitAngle, getShieldFacingAngle());
    }

    private int shieldFaceForImpactAngle(double hitAngle, double facingAngle) {
        if (!Double.isFinite(hitAngle)) return SHIELD_FACE_FORE;
        if (!Double.isFinite(facingAngle)) facingAngle = getShieldFacingAngle();
        double rel = MathUtil.normalizeAngle(hitAngle - facingAngle);
        double abs = Math.abs(rel);
        if (abs <= Math.toRadians(45.0)) return SHIELD_FACE_FORE;
        if (abs >= Math.toRadians(135.0)) return SHIELD_FACE_REAR;
        return (rel > 0.0) ? SHIELD_FACE_RIGHT : SHIELD_FACE_LEFT;
    }

    private void applyShieldDamageToFace(int face, double amount) {
        if (amount <= 0.0) return;
        ensureShieldFacesSynced();

        int idx = Math.max(0, Math.min(SHIELD_FACE_COUNT - 1, face));
        double before = shieldFaces[idx];
        shieldFaces[idx] = Math.max(0.0, shieldFaces[idx] - amount);
        if (before > 1e-6 && shieldFaces[idx] <= 1e-6) {
            shieldFaceRegenLock[idx] = Math.max(shieldFaceRegenLock[idx], SHIELD_FACE_REGEN_LOCK_SECONDS);
        }
        syncAggregateShieldFromFaces();
    }

    private void forceShieldOffline(double duration) {
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) shieldFaces[i] = 0.0;
        shield = 0.0;
        shieldOfflineTimer = Math.max(shieldOfflineTimer, duration);
        shieldFacesSyncedMax = Math.max(0.0, shieldMax);
        shieldFacesInitialized = true;
    }

    private void updateDerivedSystemEffects() {
        if (desiredSpeedBase <= 0.0) desiredSpeedBase = Math.max(0.0, desiredSpeed);

        double engine = systemHealthFraction(InternalSystem.ENGINES);
        double warp = systemHealthFraction(InternalSystem.WARP_ENGINES);
        double bridge = systemHealthFraction(InternalSystem.BRIDGE);
        double mobility = 0.24 + 0.52 * engine + 0.16 * warp + 0.08 * bridge;
        mobility *= enginesPowerMultiplier();
        mobility *= propulsionMobilityMultiplier();
        mobility *= emergencyThrustSpeedMultiplier();
        mobility *= crewEngineMul;
        mobility = Math.max(0.18, Math.min(1.38, mobility));
        desiredSpeed = desiredSpeedBase * mobility;
    }

    private RoomDamageResult applySystemDamageFromHullHit(int hullDamage,
                                                          HullGeometry.ImpactSample impact,
                                                          ShipRoomLayout.RoomDef primaryRoom,
                                                          int hullBefore) {
        if (hullDamage <= 0) return RoomDamageResult.NONE;
        ensureInternalSystemsInitialized();
        ensureRoomSystemsInitialized();
        if (primaryRoom == null) {
            primaryRoom = resolvePrimaryRoomForHullHit(impact, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        double primaryBefore = 0.0;
        if (primaryRoom != null) {
            double max = roomHpMax.getOrDefault(primaryRoom.id, 0.0);
            primaryBefore = roomHp.getOrDefault(primaryRoom.id, max);
        }
        HullDamageSplit split = new HullDamageSplit(primaryRoom, primaryBefore);

        int rolls = (hullDamage >= 9) ? 2 : 1;
        for (int i = 0; i < rolls; i++) {
            double dmg = Math.max(1.0, hullDamage * (0.58 + Math.random() * 0.82));
            ShipRoomLayout.RoomDef room = primaryRoom;
            if (room != null && i > 0 && room.neighbors.length > 0 && Math.random() < 0.38) {
                int idx = (int) Math.floor(Math.random() * room.neighbors.length);
                if (idx < 0 || idx >= room.neighbors.length) idx = 0;
                ShipRoomLayout.RoomDef neighbor = ShipRoomLayout.roomForId(role, room.neighbors[idx]);
                if (neighbor != null) room = neighbor;
            }
            if (room != null) {
                double nx = (impact == null) ? Double.NaN : impact.normalizedX;
                double ny = (impact == null) ? Double.NaN : impact.normalizedY;
                damageRoom(room, dmg, nx, ny, false);
                split.absorb(lastRoomDamageResult);
            } else {
                InternalSystem system = pickSystemForHit(impact, Double.NaN, Double.NaN);
                if (system != null) damageSystem(system, dmg);
            }
        }

        RoomDamageResult previous = lastRoomDamageResult;
        applyBreachSystemEffects(impact, hullDamage);
        if (lastRoomDamageResult != previous) {
            split.absorb(lastRoomDamageResult);
        }

        previous = lastRoomDamageResult;
        applyCatastrophicFailureRules(primaryRoom, impact, hullDamage);
        if (lastRoomDamageResult != previous) {
            split.absorb(lastRoomDamageResult);
        }

        double casualtySpike = (hpMax <= 0) ? 0.0 : (hullDamage / (double) hpMax) * 0.36;
        crewCasualtyRate = MathUtil.clamp(crewCasualtyRate + casualtySpike, 0.0, 0.75);
        return split.finish(this, hullBefore);
    }

    private void applyCatastrophicFailureRules(ShipRoomLayout.RoomDef primaryRoom,
                                               HullGeometry.ImpactSample impact,
                                               int hullDamage) {
        if (primaryRoom == null || hullDamage <= 0 || hpMax <= 0) return;
        if (primaryRoom.id == null) return;

        double graceScale = (catastrophicChainGraceTimer > 0.0) ? 0.34 : 1.0;
        double severity = MathUtil.clamp(hullDamage / (double) hpMax, 0.02, 1.0);
        boolean triggered = false;

        if (primaryRoom.id == ShipRoomLayout.RoomId.REACTOR
                || primaryRoom.id == ShipRoomLayout.RoomId.POWER_CONDUITS) {
            double reactorFrac = roomHealthFraction(ShipRoomLayout.RoomId.REACTOR);
            double chance = (0.08 + (1.0 - reactorFrac) * 0.45 + severity * 0.18) * graceScale;
            if (Math.random() < MathUtil.clamp(chance, 0.0, 0.78)) {
                triggered = triggerReactorCriticalChain(impact, hullDamage, severity);
            }
        }

        if (!triggered && (primaryRoom.id == ShipRoomLayout.RoomId.MAGAZINES
                || primaryRoom.id == ShipRoomLayout.RoomId.MISSILE_LAUNCHERS
                || isSystemDestroyed(InternalSystem.MAGAZINES))) {
            double magsFrac = roomHealthFraction(ShipRoomLayout.RoomId.MAGAZINES);
            double chance = (0.10 + (1.0 - magsFrac) * 0.42 + severity * 0.20) * graceScale;
            if (Math.random() < MathUtil.clamp(chance, 0.0, 0.82)) {
                triggered = triggerMagazineDetonationRisk(impact, hullDamage, severity);
            }
        }

        if (!triggered && (primaryRoom.id == ShipRoomLayout.RoomId.INTEGRITY_FIELD
                || roomHealthFraction(ShipRoomLayout.RoomId.INTEGRITY_FIELD) < 0.28)) {
            double integFrac = roomHealthFraction(ShipRoomLayout.RoomId.INTEGRITY_FIELD);
            double chance = (0.11 + (1.0 - integFrac) * 0.48 + severity * 0.12) * graceScale;
            if (Math.random() < MathUtil.clamp(chance, 0.0, 0.80)) {
                triggered = triggerIntegrityFieldCollapse(impact, hullDamage, severity);
            }
        }

        if (triggered) {
            catastrophicChainGraceTimer = CATASTROPHIC_CHAIN_GRACE_SECONDS;
        }
    }

    private boolean triggerReactorCriticalChain(HullGeometry.ImpactSample impact,
                                                int hullDamage,
                                                double severity) {
        ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(role, ShipRoomLayout.RoomId.REACTOR);
        ShipRoomLayout.RoomDef conduits = ShipRoomLayout.roomForId(role, ShipRoomLayout.RoomId.POWER_CONDUITS);
        if (reactor == null) return false;

        double reactorBefore = roomHp.getOrDefault(reactor.id, roomHpMax.getOrDefault(reactor.id, 0.0));
        double chainBudget = Math.max(2.0, hpMax * CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC * (0.65 + 0.35 * severity));
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;
        double reactorDamage = Math.max(2.0, Math.min(chainBudget * 0.72, hullDamage * (0.55 + 0.85 * severity)));
        damageRoom(reactor, reactorDamage, nx, ny, false);

        RoomDamageResult base = lastRoomDamageResult;
        List<String> transitions = new ArrayList<>();
        if (base != null && base != RoomDamageResult.NONE) transitions.addAll(base.subsystemTransitions);
        transitions.add("reactor:critical_chain");

        if (conduits != null && chainBudget > reactorDamage + 0.5) {
            double spill = Math.max(1.0, chainBudget - reactorDamage);
            damageRoom(conduits, spill, nx, ny, false);
            RoomDamageResult spillResult = lastRoomDamageResult;
            if (spillResult != null && spillResult != RoomDamageResult.NONE) {
                transitions.addAll(spillResult.subsystemTransitions);
            }
        }
        igniteRoomFire(ShipRoomLayout.RoomId.REACTOR, 1.15 + severity * 0.80);
        transitions.add("hazard:fire_ignited");
        syncHullFromRoomIntegrity();
        evaluateCondemnedStateFromRooms();

        double before = (base == null || base == RoomDamageResult.NONE) ? reactorBefore : base.hpBefore;
        double after = roomHp.getOrDefault(reactor.id, 0.0);
        lastRoomDamageResult = new RoomDamageResult(
                reactor.id.name(),
                before,
                after,
                2,
                transitions,
                0,
                Math.max(0.0, before - after)
        );
        return true;
    }

    private boolean triggerMagazineDetonationRisk(HullGeometry.ImpactSample impact,
                                                  int hullDamage,
                                                  double severity) {
        ShipRoomLayout.RoomDef mags = ShipRoomLayout.roomForId(role, ShipRoomLayout.RoomId.MAGAZINES);
        if (mags == null) return false;

        double magsBefore = roomHp.getOrDefault(mags.id, roomHpMax.getOrDefault(mags.id, 0.0));
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;
        double chainBudget = Math.max(2.0, hpMax * CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC * (0.55 + 0.30 * severity));
        double magsDamage = Math.max(2.0, Math.min(chainBudget, hullDamage * (0.52 + 0.90 * severity)));
        damageRoom(mags, magsDamage, nx, ny, false);

        RoomDamageResult base = lastRoomDamageResult;
        List<String> transitions = new ArrayList<>();
        if (base != null && base != RoomDamageResult.NONE) transitions.addAll(base.subsystemTransitions);
        transitions.add("magazines:detonation_risk");
        Explosion.spawnShieldHit(x, y);
        lastRoomDamageResult = new RoomDamageResult(
                mags.id.name(),
                (base == null || base == RoomDamageResult.NONE) ? magsBefore : base.hpBefore,
                roomHp.getOrDefault(mags.id, 0.0),
                (base == null || base == RoomDamageResult.NONE) ? 1 : Math.max(1, base.hazardRolls),
                transitions,
                (base == null || base == RoomDamageResult.NONE) ? 0 : base.shipStructuralDelta,
                (base == null || base == RoomDamageResult.NONE)
                        ? Math.max(0.0, magsBefore - roomHp.getOrDefault(mags.id, 0.0))
                        : Math.max(base.roomLocalHpLoss, base.hpBefore - roomHp.getOrDefault(mags.id, 0.0))
        );
        return true;
    }

    private boolean triggerIntegrityFieldCollapse(HullGeometry.ImpactSample impact,
                                                  int hullDamage,
                                                  double severity) {
        ShipRoomLayout.RoomDef integrity = ShipRoomLayout.roomForId(role, ShipRoomLayout.RoomId.INTEGRITY_FIELD);
        if (integrity == null) return false;

        double integrityBefore = roomHp.getOrDefault(integrity.id, roomHpMax.getOrDefault(integrity.id, 0.0));
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;
        double collapseBudget = Math.max(1.0, hpMax * (CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC * 0.80));
        double dmg = Math.max(1.0, Math.min(collapseBudget, hullDamage * (0.42 + 0.62 * severity)));
        damageRoom(integrity, dmg, nx, ny, false);
        RoomDamageResult base = lastRoomDamageResult;

        forceShieldOffline(Math.max(1.25, shieldRebootDelay * (1.6 + 0.75 * severity)));
        damageSystem(InternalSystem.SHIELDS, hullDamage * (0.38 + 0.26 * severity));
        List<String> transitions = new ArrayList<>();
        if (base != null && base != RoomDamageResult.NONE) transitions.addAll(base.subsystemTransitions);
        transitions.add("integrity:field_collapse");
        transitions.add(InternalSystem.SHIELDS.name() + ":offline");

        lastRoomDamageResult = new RoomDamageResult(
                integrity.id.name(),
                (base == null || base == RoomDamageResult.NONE) ? integrityBefore : base.hpBefore,
                roomHp.getOrDefault(integrity.id, 0.0),
                (base == null || base == RoomDamageResult.NONE) ? 1 : Math.max(1, base.hazardRolls),
                transitions,
                (base == null || base == RoomDamageResult.NONE) ? 0 : base.shipStructuralDelta,
                (base == null || base == RoomDamageResult.NONE)
                        ? Math.max(0.0, integrityBefore - roomHp.getOrDefault(integrity.id, 0.0))
                        : Math.max(base.roomLocalHpLoss, base.hpBefore - roomHp.getOrDefault(integrity.id, 0.0))
        );
        return true;
    }

    private InternalSystem pickSystemForHit(HullGeometry.ImpactSample impact, double hitX, double hitY) {
        if (impact != null && impact.onHull) {
            double nx = impact.normalizedX;
            double ny = impact.normalizedY;
            double ay = Math.abs(ny);

            if (nx > 0.48) {
                if (ay < 0.22) return pickSystem(InternalSystem.BRIDGE, InternalSystem.SENSORS, InternalSystem.WEAPONS);
                return pickSystem(InternalSystem.WEAPONS, InternalSystem.SENSORS, InternalSystem.SHIELDS);
            }
            if (nx < -0.44) {
                if (ay < 0.28) return pickSystem(InternalSystem.ENGINES, InternalSystem.REACTOR_CORE, InternalSystem.WARP_ENGINES);
                return pickSystem(InternalSystem.ENGINES, InternalSystem.WARP_ENGINES, InternalSystem.SHIELDS);
            }
            if (ay > 0.58) {
                return pickSystem(InternalSystem.SHIELDS, InternalSystem.MAGAZINES, InternalSystem.WEAPONS);
            }
            if (Math.abs(nx) < 0.20 && ay < 0.25) {
                return pickSystem(InternalSystem.REACTOR_CORE, InternalSystem.MAGAZINES, InternalSystem.WEAPONS);
            }
            if (nx >= 0.0) {
                return pickSystem(InternalSystem.WEAPONS, InternalSystem.SENSORS, InternalSystem.BRIDGE, InternalSystem.REACTOR_CORE);
            }
            return pickSystem(InternalSystem.ENGINES, InternalSystem.WARP_ENGINES, InternalSystem.REACTOR_CORE, InternalSystem.MAGAZINES);
        }

        if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
            double hitAngle = Math.atan2(hitY - y, hitX - x);
            double rel = Math.abs(MathUtil.normalizeAngle(hitAngle - angle));
            if (rel < Math.toRadians(45.0) && Math.random() < 0.55) {
                return pickSystem(InternalSystem.WEAPONS, InternalSystem.SENSORS, InternalSystem.BRIDGE);
            }
            if (rel > Math.toRadians(135.0) && Math.random() < 0.55) {
                return pickSystem(InternalSystem.ENGINES, InternalSystem.WARP_ENGINES, InternalSystem.REACTOR_CORE);
            }
        }

        return pickSystem(InternalSystem.values());
    }

    private InternalSystem pickSystem(InternalSystem... systems) {
        if (systems == null || systems.length == 0) return null;
        int idx = (int) Math.floor(Math.random() * systems.length);
        if (idx < 0 || idx >= systems.length) idx = 0;
        InternalSystem out = systems[idx];
        if (out == InternalSystem.SHIELDS && !shieldActive && Math.random() < 0.7) {
            out = (Math.random() < 0.5) ? InternalSystem.ENGINES : InternalSystem.WEAPONS;
        }
        return out;
    }

    private void applyBreachSystemEffects(HullGeometry.ImpactSample impact, int hullDamage) {
        if (impact == null || !impact.onHull) return;
        if (hullDamage <= 0 || hpMax <= 0) return;
        ensureRoomSystemsInitialized();

        double hpFrac = totalRoomIntegrityFraction();
        double severity = MathUtil.clamp(hullDamage / (double) hpMax, 0.02, 1.0);
        double breachScore = severity + Math.max(0.0, 0.52 - hpFrac) * 1.35;
        if (hullDamage >= 10) breachScore += 0.20;
        if (Math.abs(impact.normalizedX) < 0.22 && Math.abs(impact.normalizedY) < 0.22) breachScore += 0.10;
        if (breachScore < 0.60 && Math.random() > breachScore * 0.70) return;

        ShipRoomLayout.RoomDef breachedRoom = resolveRoomForImpact(impact, Double.NaN, Double.NaN);
        if (breachedRoom == null) return;

        double graceScale = (catastrophicChainGraceTimer > 0.0) ? 0.36 : 1.0;
        double catastrophicChance = (breachScore > 1.05) ? 0.72 : 0.0;
        if (hullDamage >= Math.max(10, hpMax / 8)) catastrophicChance = Math.max(catastrophicChance, 0.60);
        catastrophicChance *= graceScale;
        boolean catastrophic = Math.random() < MathUtil.clamp(catastrophicChance, 0.0, 0.90);
        if (catastrophic) {
            int hullBefore = hp;
            List<String> transitions = new ArrayList<>(3);
            double before = roomHp.getOrDefault(breachedRoom.id, 0.0);
            if (breachedRoom.primarySystem != null) destroySystem(breachedRoom.primarySystem);
            if (breachedRoom.primarySystem != null) {
                transitions.add(breachedRoom.primarySystem.name() + ":destroyed");
            }
            Double maxRoom = roomHpMax.get(breachedRoom.id);
            if (maxRoom != null && maxRoom > 0.0) {
                double capFrac = (catastrophicChainGraceTimer > 0.0) ? 0.34 : 0.68;
                double loss = Math.max(maxRoom * 0.24, hullDamage * (1.10 + severity * 1.45));
                loss = Math.min(loss, maxRoom * capFrac);
                double after = Math.max(0.0, before - loss);
                roomHp.put(breachedRoom.id, after);
                logRoomDamage(breachedRoom.id, impact.normalizedX, impact.normalizedY, Math.max(0.0, before - after), false);
            }
            igniteRoomFire(breachedRoom.id, 0.95 + severity * 0.65);
            transitions.add("hazard:fire_ignited");
            enforceRoomSystemAvailability();
            syncHullFromRoomIntegrity();
            evaluateCondemnedStateFromRooms();
            catastrophicChainGraceTimer = CATASTROPHIC_CHAIN_GRACE_SECONDS;
            lastRoomDamageResult = new RoomDamageResult(
                    breachedRoom.id.name(),
                    before,
                    roomHp.getOrDefault(breachedRoom.id, 0.0),
                    1,
                    transitions,
                    hp - hullBefore,
                    Math.max(0.0, before - roomHp.getOrDefault(breachedRoom.id, 0.0))
            );
            return;
        }

        double breachDamage = Math.max(6.0, hullDamage * (1.25 + severity * 1.8));
        breachDamage = Math.min(breachDamage, Math.max(6.0, hpMax * (0.18 + (catastrophicChainGraceTimer > 0.0 ? 0.05 : 0.12))));
        damageRoom(breachedRoom, breachDamage, impact.normalizedX, impact.normalizedY, false);
    }

    private void registerHullImpact(int hullDamage,
                                    HullGeometry.ImpactSample impact,
                                    ShipRoomLayout.RoomDef room) {
        if (hullDamage <= 0) return;
        if (impact == null || !impact.onHull) return;

        double hpDenom = Math.max(1.0, (double) hpMax);
        double severity = MathUtil.clamp(hullDamage / hpDenom, 0.06, 1.0);
        double hpFrac = Math.max(0.0, Math.min(1.0, hp / hpDenom));

        double breachChance = 0.08 + severity * 0.36 + Math.max(0.0, 0.58 - hpFrac) * 0.78;
        if (hullDamage >= 9) breachChance += 0.14;
        if (Math.abs(impact.normalizedX) < 0.24 && Math.abs(impact.normalizedY) < 0.24) breachChance += 0.08;
        breachChance = MathUtil.clamp(breachChance, 0.0, 0.95);

        double breachRadius = 0.0;
        if (Math.random() < breachChance) {
            double base = Math.max(2.8, radius * 0.06);
            double bonus = radius * (0.06 + severity * 0.17);
            breachRadius = MathUtil.clamp(base + bonus, 2.8, Math.max(4.0, radius * 0.42));
        }

        if (hullImpactMarks.size() >= MAX_HULL_IMPACT_MARKS) {
            hullImpactMarks.remove(0);
        }
        ShipRoomLayout.RoomId roomId = (room == null) ? null : room.id;
        hullImpactMarks.add(new HullImpactMark(impact.localX, impact.localY, severity, breachRadius, roomId));
        hullImpactNoDamageTimer = 0.0;
    }

    private void updateHullImpactDecay(double dt) {
        if (dt <= 0.0) return;
        if (hullImpactMarks.isEmpty()) {
            hullImpactNoDamageTimer = HULL_IMPACT_DECAY_IDLE_SECONDS;
            return;
        }
        hullImpactNoDamageTimer += dt;
        if (hullImpactNoDamageTimer >= HULL_IMPACT_DECAY_IDLE_SECONDS) {
            clearHullImpactMarks();
        }
    }

    private void damageSystem(InternalSystem system, double damage) {
        if (system == null || damage <= 0.0) return;
        Double hpv = systemHp.get(system);
        if (hpv == null) return;
        hpv = Math.max(0.0, hpv - damage);
        systemHp.put(system, hpv);

        if (system == InternalSystem.SHIELDS && hpv <= 0.0) {
            forceShieldOffline(shieldRebootDelay * 1.5);
        }
    }

    private void destroySystem(InternalSystem system) {
        if (system == null) return;
        if (!systemHp.containsKey(system)) return;
        systemHp.put(system, 0.0);
        if (system == InternalSystem.SHIELDS) {
            forceShieldOffline(shieldRebootDelay * 1.5);
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
        ensureShieldFacesSynced();
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
        ensureShieldFacesSynced();
        shieldOfflineTimer = 0.0;
        recentShieldImpactTimer = 0.0;
        recentShieldImpactAngle = Double.NaN;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) shieldFaceRegenLock[i] = 0.0;
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
        waveMotionBeamTickTimer = waveMotionTickSpacing();
        waveMotionBeamAim = aim;
        onFiredWeapon();

        return createWaveMotionPulse(dt, aim, false);
    }

    private double waveMotionTickSpacing() {
        return Math.max(0.03, waveMotionBeamTickInterval / WAVE_MOTION_PROJECTILE_RATE_MULT);
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
