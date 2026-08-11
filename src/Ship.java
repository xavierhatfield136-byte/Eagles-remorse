import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * Base ship.
 *
 * IMPORTANT: This project uses a "per-tick delta" pattern:
 * - vx/vy are already scaled by dt (per tick), so integration is x += vx; y += vy.
 */
public abstract class Ship {
    public static final double UNIVERSAL_SPECIAL_WEAPON_RANGE = 3_000.0;
    private static int NEXT_ID = 1;

    static int beginDeterministicIdScope() {
        int previous = NEXT_ID;
        NEXT_ID = 1;
        return previous;
    }

    static void endDeterministicIdScope(int previousNextId) {
        NEXT_ID = Math.max(previousNextId, NEXT_ID);
    }
    private static final Object SHIP_RNG_LOCK = new Object();
    private static final InternalSystem[] INTERNAL_SYSTEM_VALUES = InternalSystem.values();
    private static final ShipRoomLayout.RoomId[] PROPULSION_ENGINE_ROOMS = {
            ShipRoomLayout.RoomId.ENGINES,
            ShipRoomLayout.RoomId.PORT_ENGINES,
            ShipRoomLayout.RoomId.STARBOARD_ENGINES
    };
    private static Random deterministicRandom = null;

    public static void enableDeterministicRandom(long seed) {
        synchronized (SHIP_RNG_LOCK) {
            deterministicRandom = new Random(seed);
        }
    }

    public static void disableDeterministicRandom() {
        synchronized (SHIP_RNG_LOCK) {
            deterministicRandom = null;
        }
    }

    static double randomUnit() {
        synchronized (SHIP_RNG_LOCK) {
            if (deterministicRandom != null) return deterministicRandom.nextDouble();
        }
        return Math.random();
    }

    private static double[] initCommandStatMultipliers() {
        double[] out = new double[ShipIdentityRegistry.IdentityStat.values().length];
        Arrays.fill(out, 1.0);
        return out;
    }

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
    public double armorRoomHpMultiplier = 1.0;
    public double shieldStripRoomHpMultiplier = 1.0;
    public double doctrineOffenseDamageMultiplier = 1.0;
    private final double[] commandStatMultipliers = initCommandStatMultipliers();

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
    /** Tracks which pre-detonation failure cue has already fired. */
    private int deathCriticalCueStage = 0;

    // Economy
    public int bountyValue = 0;
    public boolean bountyClaimed = false;
    /** True once the player has landed at least one damaging contact on this ship. */
    public boolean playerTaggedForKillCredit = false;
    /** One-shot guard so kill-assist credits are only paid once per ship. */
    public boolean playerKillCreditPaid = false;

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
    private final int[] shieldGateHitsRemaining = new int[SHIELD_FACE_COUNT];
    private final int[] shieldGateHitsMax = new int[SHIELD_FACE_COUNT];
    private final double[] shieldGateRechargeTimer = new double[SHIELD_FACE_COUNT];
    private final int[] armorGateHitsRemaining = new int[SHIELD_FACE_COUNT];
    private final int[] armorGateHitsMax = new int[SHIELD_FACE_COUNT];
    private static final double SHIELD_PASSTHROUGH_MIN_CHANCE = 0.01;
    private static final double SHIELD_PASSTHROUGH_MAX_CHANCE = 0.50;
    private static final double SHIELD_PASSTHROUGH_FULL_FRAC = 1.0;
    private static final double SHIELD_PASSTHROUGH_CRITICAL_FRAC = 0.01;
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
    private int recentShieldImpactFace = -1;

    // Turrets
    public final List<Turret> turrets = new ArrayList<>();

    // Superweapon (superweapon)
    public enum SuperweaponPattern {
        DESTABILIZER_PULSE, // Team A: balanced pulse blast with mixed damage and disruption
        PULSE_BARRAGE,   // Legacy rapid-fire wave pulses
        KINETIC_SLUG,    // Red team: single heavy kinetic shell
        KINETIC_SHOTGUN, // Red hyperweapon: rapid cone of high-velocity kinetic fire
        DIRECT_BEAM,     // Green team: strong direct-energy beam
        MISSILE_BARRAGE, // Missile team: repeated heavy missile salvos
        LANCE_CONE       // Hyperweapon: piercing beam followed by a delayed cone burst
    }

    public boolean hasSuperweapon = false;
    public SuperweaponPattern superweaponPattern = SuperweaponPattern.DESTABILIZER_PULSE;
    public double superweaponChargeTime = 0.0;
    public double superweaponCooldown = 24.0;
    private double superweaponTimer = 0.0;
    private double superweaponChargeTimer = 0.0;
    private boolean superweaponCharging = false;
    private final List<Projectile> pendingSuperweaponShots = new ArrayList<>();
    private double queuedSuperweaponAim = Double.NaN;
    private Ship queuedSuperweaponTarget = null;
    private final List<Ship> queuedSuperweaponSpreadTargets = new ArrayList<>();
    public int superweaponDamage = 68;
    public double superweaponSpeed = 1500.0;
    public int superweaponLife = 140;
    public double superweaponRadius = 12.0;
    public int superweaponMaxHits = 18;
    public double superweaponBeamDuration = 0.95;
    public double superweaponBeamTickInterval = 0.12;
    public double superweaponBeamDamageScale = 0.34;
    public static final double SUPERWEAPON_CHARGE_SFX_SECONDS = 10.0;
    private static final double SUPERWEAPON_PROJECTILE_RATE_MULT = 3.0;
    private static final double SUPERWEAPON_RECHARGE_MIN_MULT = 0.25;
    private static final double SUPERWEAPON_RECHARGE_MAX_MULT = 2.05;
    private double superweaponBeamTimer = 0.0;
    private double superweaponBeamTickTimer = 0.0;
    private double superweaponBeamAim = Double.NaN;
    private Ship superweaponBeamTarget = null;
    private final List<Ship> superweaponBeamSpreadTargets = new ArrayList<>();
    private double temporaryDisableTimer = 0.0;
    private double stasisFieldTimer = 0.0;
    private double destabilizedTimer = 0.0;
    private double kineticMomentumTimer = 0.0;
    private ShipRoomLayout.RoomId integrityFocusRoom = null;
    private double integrityFocusTimer = 0.0;
    private static final double DESTABILIZED_SYSTEM_MULTIPLIER = 0.80;
    private static final double KINETIC_MOMENTUM_WINDOW_SECONDS = 2.4;
    private static final double ROOM_DISRUPTION_FULL_SHIP_MULTIPLIER = 0.42;
    private static final double ROOM_DISRUPTION_CURVE_EXPONENT = 0.92;
    private static final double ROOM_DISRUPTION_REPAIR_SECONDS = 1.0;

    // Primary weapon family (Energy Navy only for now)
    public enum PrimaryWeaponFamily {
        ENERGY_BOLT,
        BEAM_BOLT
    }

    public static final double BLUE_MAIN_BATTERY_MIN_RELOAD_SECONDS = 1.0;
    public static final double MISSILE_MIN_RELOAD_SECONDS = 1.0;
    // Tactical alignment: beam-bolt primaries stay punchy, but move slow enough to sell anticipation and lane identity.
    public static final double BEAM_BOLT_SPEED = 700.0;
    public static final double BEAM_BOLT_RELOAD_SECONDS = 1.0;
    public static final double BEAM_BOLT_DAMAGE_RELOAD_FACTOR = 1.12;
    public static final int BEAM_BOLT_LIFE = 150; // frames (~1950px at 780 px/s)

    public PrimaryWeaponFamily primaryWeaponFamily = PrimaryWeaponFamily.ENERGY_BOLT;
    public static final double ENERGY_BOLT_BARREL_STAGGER_INTERVAL_SECONDS = 0.25;
    public double primaryGunStaggerTimer = 0.0;
    public int primaryGunStaggerCursor = 0;
    public int primaryGunStaggerBurstRemaining = 0;

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
     * 0..1 quality: 1.0 means perfect lead with no spread.
     */
    public double ciwsQuality = 0.35;
    public int ciwsPelletsPerBurst = 2;
    public double ciwsPelletSpeed = 920;
    public int ciwsPelletDamage = 1;
    public int ciwsPelletLife = 18;
    public double ciwsPelletRadius = 1.8;
    private final List<Missile> ciwsMissileScratch = new ArrayList<>();
    private final List<Ship> ciwsShipScratch = new ArrayList<>();

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
    /** Mission subzone anchor used by campaign missions with separated void gaps. */
    public int campaignMissionSubzone = -1;
    /** Source mission subzone held while the ship charges a warp jump in campaign. */
    public int campaignWarpSourceSubzone = -1;
    /** Seconds until orphaned craft despawns; -1 while not orphaned. */
    public double carrierOrphanTimer = -1.0;
    /** Active strike-craft behavior state. */
    public WingState wingState = WingState.ATTACK;
    /** If this is a non-carrier escort fighter, the protected ship id; otherwise -1. */
    public int escortAnchorId = -1;
    /** Stable escort side/slot preference around the protected ship. */
    public int escortSlotIndex = 0;
    /** 5-slot launch pattern used when a carrier launches a full flight. */
    public final ShipRole[] flightDeckLoadout = new ShipRole[5];
    /** Next slot to launch from the configured flight deck pattern. */
    public int flightDeckLaunchCursor = 0;
    /** Limited sortie ammo pool for fighter/bomber primaries. */
    public int strikePrimaryMunitionsMax = 0;
    public int strikePrimaryMunitions = 0;
    /** Limited sortie ammo pool for fighter/bomber secondary ordnance. */
    public int strikeSecondaryMunitionsMax = 0;
    public int strikeSecondaryMunitions = 0;
    private double strikePrimaryDamageCarry = 0.0;
    private double strikeSecondaryDamageCarry = 0.0;

    // Base / capture
    public boolean isBase = false;
    public Faction baseOwner = null;
    public BaseUpgrades stationUpgrades = null;
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

    // Battlefield warp
    private boolean warpCharging = false;
    private double warpChargeRemaining = 0.0;
    private double warpChargeDuration = 0.0;
    private double warpExitX = Double.NaN;
    private double warpExitY = Double.NaN;
    private int warpFormationLeaderId = -1;
    private String warpSourceSectorId = "";

    // Movement
    public double desiredSpeed = 110;
    public double desiredSpeedBase = 110;

    // AI engagement memory (used for lane adaptation and anti-stall behavior).
    public double aiBadApproachTimer = 0.0;
    public double aiBadApproachAngle = Double.NaN;
    public double aiNoFireTimer = 0.0;
    public double aiForcedEngageTimer = 0.0;
    public double aiArrivalFireDelayTimer = 0.0;
    public double aiLastEngagementX = Double.NaN;
    public double aiLastEngagementY = Double.NaN;
    public double aiMissileStandoffTimer = 0.0;
    public int aiMissileStandoffTargetId = -1;
    public int aiCommittedTargetId = -1;
    public double aiTargetCommitTimer = 0.0;
    public int aiIntentTypeOrdinal = -1;
    public int aiIntentTargetId = -1;
    public double aiIntentRetargetTimer = 0.0;
    public double aiMovementThinkTimer = 0.0;
    public double aiCachedDesiredRange = Double.NaN;
    public int aiCachedMovementMode = 0;
    public boolean surrendered = false;
    public double surrenderLockTimer = 0.0;
    public double surrenderSelfDestructTimer = 0.0;

    // Power management
    public enum PowerPreset {
        BALANCED,
        ATTACK,
        DEFENSE,
        PURSUIT,
        CUSTOM
    }
    public enum PowerBus {
        PROPULSION,
        SHIELD,
        TACTICAL,
        SENSOR,
        ENGINEERING,
        AUXILIARY
    }
    private static final double POWER_BUS_UI_SOFT_CAP_RATIO = 1.70;
    private static final double SURRENDER_SELF_DESTRUCT_SECONDS = 20.0;
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

    public enum CrewPriority {
        AUTO_REPAIR,
        FIRE_SUPPRESSION,
        REACTOR,
        ENGINES,
        WEAPONS,
        SHIELDS,
        SENSORS,
        BATTLE_STATIONS,
        MANUAL_ROOM
    }

    public enum CrewTeamRole {
        DAMAGE_CONTROL,
        ENGINEERING,
        FIRE_SUPPRESSION
    }

    public enum CrewTeamTask {
        IDLE,
        MOVING,
        REPAIRING,
        FIREFIGHTING,
        RESTORING_SYSTEM,
        OPERATING
    }

    public static final class CrewTeamSnapshot {
        public final int id;
        public final CrewTeamRole role;
        public final CrewTeamTask task;
        public final CrewPriority priority;
        public final ShipRoomLayout.RoomId currentRoom;
        public final ShipRoomLayout.RoomId targetRoom;
        public final ShipRoomLayout.RoomId nextRoom;
        public final double moveProgress;

        private CrewTeamSnapshot(CrewTeam team, CrewPriority priority) {
            this.id = team.id;
            this.role = team.role;
            this.task = team.task;
            this.priority = priority;
            this.currentRoom = team.currentRoom;
            this.targetRoom = team.targetRoom;
            this.nextRoom = team.nextRoom();
            this.moveProgress = MathUtil.clamp(team.moveProgress, 0.0, 1.0);
        }
    }

    private static final class CrewTeam {
        final int id;
        final CrewTeamRole role;
        ShipRoomLayout.RoomId currentRoom;
        ShipRoomLayout.RoomId targetRoom;
        CrewTeamTask task = CrewTeamTask.IDLE;
        final ArrayList<ShipRoomLayout.RoomId> path = new ArrayList<>();
        int pathIndex = 0;
        double moveProgress = 0.0;

        CrewTeam(int id, CrewTeamRole role, ShipRoomLayout.RoomId currentRoom) {
            this.id = id;
            this.role = role;
            this.currentRoom = currentRoom;
            this.targetRoom = currentRoom;
        }

        ShipRoomLayout.RoomId nextRoom() {
            int nextIdx = pathIndex + 1;
            if (nextIdx < 0 || nextIdx >= path.size()) return null;
            return path.get(nextIdx);
        }
    }

    public CrewOrder crewOrder = CrewOrder.BALANCED;
    private final ArrayList<CrewTeam> crewTeams = new ArrayList<>();
    private CrewPriority crewPriority = CrewPriority.AUTO_REPAIR;
    private ShipRoomLayout.RoomId crewManualPriorityRoom = null;
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
    public enum InteriorHitProfile {
        DEFAULT,
        BLUE_PIERCE,
        LASER_LINE,
        RED_EXPLOSIVE,
        MISSILE_BLAST
    }

    private final java.util.EnumMap<ShipRoomLayout.RoomId, Double> roomHp =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumMap<ShipRoomLayout.RoomId, Double> roomHpMax =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumMap<ShipRoomLayout.RoomId, RoomHazardState> roomHazards =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumMap<ShipRoomLayout.RoomId, Double> roomDisruptionRepairProgress =
            new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
    private final java.util.EnumSet<InternalSystem> roomDisabledSystems =
            java.util.EnumSet.noneOf(InternalSystem.class);
    private boolean roomSystemsInitialized = false;
    private static final int MAX_ROOM_DAMAGE_EVENTS = 64;
    private static final double BASE_ROOM_CONDEMNED_THRESHOLD = 0.30;
    private static final double ROOM_OPERATIONAL_THRESHOLD = 0.30;
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

    public static final class ShieldImpactMark {
        private double localX;
        private double localY;
        private double normalX;
        private double normalY;
        private double severity;
        private double freshness;
        private double patchRadius;

        private ShieldImpactMark(double localX, double localY, double normalX, double normalY,
                                 double severity, double freshness, double patchRadius) {
            this.localX = localX;
            this.localY = localY;
            this.normalX = normalX;
            this.normalY = normalY;
            this.severity = severity;
            this.freshness = freshness;
            this.patchRadius = patchRadius;
        }

        public double localX() { return localX; }
        public double localY() { return localY; }
        public double normalX() { return normalX; }
        public double normalY() { return normalY; }
        public double severity() { return severity; }
        public double freshness() { return freshness; }
        public double patchRadius() { return patchRadius; }
    }

    private static final int MAX_HULL_IMPACT_MARKS = 64;
    private static final double HULL_IMPACT_DECAY_IDLE_SECONDS = 10.0;
    // Projectile damage threshold below which shield hit effects are suppressed for performance
    private static final int MINIMUM_DAMAGE_FOR_EFFECT_SPAWN = 3;
    private final List<HullImpactMark> hullImpactMarks = new ArrayList<>();
    private final List<HullImpactMark> hullImpactMarksView = Collections.unmodifiableList(hullImpactMarks);
    private double hullImpactNoDamageTimer = HULL_IMPACT_DECAY_IDLE_SECONDS;
    private static final int MAX_SHIELD_IMPACT_MARKS = 18;
    private final List<ShieldImpactMark> shieldImpactMarks = new ArrayList<>();
    private final List<ShieldImpactMark> shieldImpactMarksView = Collections.unmodifiableList(shieldImpactMarks);
    private static final double CATASTROPHIC_CHAIN_GRACE_SECONDS = 4.0;
    private static final double BASE_CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC = 0.22;
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
        public final boolean disrupted;
        public final double disruptionRepairProgress;
        public final InternalSystem primarySystem;

        private RoomStatus(ShipRoomLayout.RoomId roomId, String label, double[] normalizedXs, double[] normalizedYs,
                           double hp, double hpMax, boolean critical, double fireIntensity,
                           boolean disrupted, double disruptionRepairProgress,
                           InternalSystem primarySystem) {
            this.roomId = roomId;
            this.label = label;
            this.normalizedXs = normalizedXs;
            this.normalizedYs = normalizedYs;
            this.hp = hp;
            this.hpMax = hpMax;
            this.critical = critical;
            this.fireIntensity = fireIntensity;
            this.disrupted = disrupted;
            this.disruptionRepairProgress = MathUtil.clamp(disruptionRepairProgress, 0.0, 1.0);
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
    public enum CloakControlMode {
        CHARGE,
        ACTIVE
    }

    /** If true, this ship is harder to target/lock unless revealed or very close. */
    public boolean isStealth = false;
    /** 0..1 (1 = fully visible). Stealth ships usually sit around ~0.35 while cloaked. */
    public double signature = 1.0;
    /** Seconds remaining that this ship is "revealed" (shots/hits make you easier to see). */
    public double revealTimer = 0.0;
    /** Short-lived weapons bloom used for zone-wide hostile contact sharing. */
    public double weaponsHotTimer = 0.0;
    /** Active cloak state for stealth ships. */
    public boolean cloakActive = false;
    /** Desired cloak state for stealth ships. */
    public CloakControlMode cloakControlMode = CloakControlMode.CHARGE;
    /** If false, stealth ships will not engage cloak (debug/gameplay toggle hook). */
    public boolean cloakEnabled = true;
    /** Cloak resource model. */
    public double cloakEnergyMax = 9.0;
    public double cloakEnergy = cloakEnergyMax;
    public double cloakDrainPerSec = 1.15;
    public double cloakRechargePerSec = 0.95;
    public double cloakMinEnergyToEngage = 1.0;
    public double cloakSignature = 0.08;
    public double cloakThreatTimer = 0.0;

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

            // Escalating pre-detonation failures: venting bursts, hull flashes, then breakup.
            dyingTimer += dt;
            fireSpawnTimer += dt;
            double deathProgress = (burnDuration <= 1e-6) ? 1.0 : MathUtil.clamp(dyingTimer / burnDuration, 0.0, 1.0);
            double ventInterval = Math.max(0.08, 0.22 - deathProgress * 0.12);
            if (fireSpawnTimer >= ventInterval) {
                fireSpawnTimer = 0.0;
                emitDeathVentingFx(false, deathProgress, deathCriticalCueStage);
            }

            while (deathCriticalCueStage < 3 && deathProgress >= deathCueThreshold(deathCriticalCueStage)) {
                triggerDeathCriticalCue(deathCriticalCueStage, deathProgress);
                deathCriticalCueStage++;
            }

            if (!deathExploded && dyingTimer >= burnDuration) {
                explodeIntoFireball(wreckVx, wreckVy);
            }
            return;
        }

        if (temporaryDisableTimer > 0.0) {
            temporaryDisableTimer -= dt;
            if (temporaryDisableTimer < 0.0) temporaryDisableTimer = 0.0;
            vx = 0.0;
            vy = 0.0;
        }
        if (stasisFieldTimer > 0.0) {
            stasisFieldTimer -= dt;
            if (stasisFieldTimer < 0.0) stasisFieldTimer = 0.0;
        }
        if (destabilizedTimer > 0.0) {
            destabilizedTimer -= dt;
            if (destabilizedTimer < 0.0) destabilizedTimer = 0.0;
        }

        x += vx;
        y += vy;
        if (dt > 0.0) noDamageTimerSeconds += dt;
        if (integrityFocusTimer > 0.0) {
            integrityFocusTimer -= dt;
            if (integrityFocusTimer <= 0.0) {
                clearIntegrityFocus();
            }
        }
        if (kineticMomentumTimer > 0.0) {
            kineticMomentumTimer -= dt;
            if (kineticMomentumTimer < 0.0) kineticMomentumTimer = 0.0;
        }
        if (surrenderLockTimer > 0.0) {
            surrenderLockTimer -= dt;
            if (surrenderLockTimer < 0.0) surrenderLockTimer = 0.0;
        }
        if (surrendered) {
            preserveSurrenderedHullState();
            if (surrenderSelfDestructTimer > 0.0) {
                surrenderSelfDestructTimer -= dt;
                if (surrenderSelfDestructTimer <= 0.0) {
                    surrenderSelfDestructTimer = 0.0;
                    triggerSurrenderSelfDestruct();
                    return;
                }
            }
        }
        if (catastrophicChainGraceTimer > 0.0) {
            catastrophicChainGraceTimer -= dt;
            if (catastrophicChainGraceTimer < 0.0) catastrophicChainGraceTimer = 0.0;
        }

        if (revealTimer > 0) {
            revealTimer -= dt;
            if (revealTimer < 0) revealTimer = 0;
        }
        if (weaponsHotTimer > 0.0) {
            weaponsHotTimer -= dt;
            if (weaponsHotTimer < 0.0) weaponsHotTimer = 0.0;
        }
        if (cloakThreatTimer > 0.0) {
            cloakThreatTimer -= dt;
            if (cloakThreatTimer < 0.0) cloakThreatTimer = 0.0;
        }
        if (recentShieldImpactTimer > 0.0) {
            recentShieldImpactTimer -= dt;
            if (recentShieldImpactTimer < 0.0) {
                recentShieldImpactTimer = 0.0;
                recentShieldImpactFace = -1;
            }
        }
        updateShieldImpactDecay(dt);
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
        if (isShieldOnline() && shield < effectiveShieldCapacityMax()) {
            distributeShieldRegen(shieldRegen * shieldRegenMultiplier() * dt);
        }
        syncDefenseGateState(false);
        updateShieldGateRecharge(dt);

        if (primaryGunStaggerTimer > 0.0) {
            primaryGunStaggerTimer -= dt;
            if (primaryGunStaggerTimer < 0.0) primaryGunStaggerTimer = 0.0;
        }

        for (Turret t : turrets) t.update(dt);

        ciwsTimer -= dt;
        if (ciwsTimer < 0) ciwsTimer = 0;

        if (superweaponTimer > 0) {
            superweaponTimer -= dt * superweaponRechargeRateMultiplier();
            if (superweaponTimer < 0) superweaponTimer = 0;
        }
        if (superweaponCharging) {
            superweaponChargeTimer -= dt;
            if (superweaponChargeTimer <= 0.0) {
                superweaponChargeTimer = 0.0;
                superweaponCharging = false;
                double aim = Double.isFinite(queuedSuperweaponAim) ? queuedSuperweaponAim : angle;
                Projectile fired = fireSuperweaponShot(dt, aim, queuedSuperweaponTarget);
                enqueuePendingSuperweaponShot(fired);
                queuedSuperweaponAim = Double.NaN;
                queuedSuperweaponTarget = null;
                queuedSuperweaponSpreadTargets.clear();
            }
        }
        if (superweaponBeamTimer > 0.0) {
            superweaponBeamTimer -= dt;
            superweaponBeamTickTimer -= dt;
            if (superweaponBeamTimer < 0.0) superweaponBeamTimer = 0.0;

            if (superweaponBeamTimer > 0.0 && superweaponBeamTickTimer <= 0.0) {
                double aim = Double.isFinite(superweaponBeamAim) ? superweaponBeamAim : angle;
                enqueuePendingSuperweaponShots(createSuperweaponVolley(dt, aim, superweaponBeamTarget, true));
                superweaponBeamTickTimer = superweaponTickSpacing();
            }

            if (superweaponBeamTimer <= 0.0) {
                superweaponBeamTickTimer = 0.0;
                superweaponBeamAim = Double.NaN;
                superweaponBeamTarget = null;
                superweaponBeamSpreadTargets.clear();
            }
        }

        if (isCarrier) {
            fighterTimer -= dt;
            if (fighterTimer < 0) fighterTimer = 0;
        }

        if (isBase || isCarrier) {
            baseSpawnTimer -= dt;
            if (baseSpawnTimer < 0) baseSpawnTimer = 0;
        }

        syncHullFromRoomIntegrity();
        evaluateCondemnedStateFromRooms();
    }

    public void applyPrimaryWeaponFamily() {
        if (!(this instanceof Player) && faction == Faction.TEAM_C) {
            primaryWeaponFamily = PrimaryWeaponFamily.BEAM_BOLT;
        }

        for (Turret t : turrets) {
            if (t == null) continue;
            if (t.kind != Turret.Kind.GUN) continue;

            GunBaseline base = gunBaselines.get(t);
            if (base == null) {
                base = cacheGunBaseline(t);
            }

            if (primaryWeaponFamily == PrimaryWeaponFamily.BEAM_BOLT) {
                double reloadCompensation = BEAM_BOLT_DAMAGE_RELOAD_FACTOR / Math.max(0.10, base.cooldown);
                t.damage = Math.max(1, (int) Math.round(base.damage * reloadCompensation));
                t.cooldown = BEAM_BOLT_RELOAD_SECONDS;
                // Store the pre-multiplied speed so Turret.fire() lands on the actual beam-bolt speed.
                t.bulletSpeed = BEAM_BOLT_SPEED / Turret.GUN_PROJECTILE_SPEED_MULT;
                t.bulletLife = BEAM_BOLT_LIFE;
            } else {
                t.damage = base.damage;
                t.cooldown = base.cooldown;
                t.bulletSpeed = base.bulletSpeed;
                t.bulletLife = base.bulletLife;
            }
        }
    }

    public boolean usesBeamBoltPrimaryVisuals() {
        try {
            DoctrineProfile profile = DoctrineRegistry.forFaction(faction);
            return profile != null && profile.doctrine == Doctrine.ENERGY_NAVY;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean usesStaggeredPrimaryFire() {
        return primaryWeaponFamily == PrimaryWeaponFamily.ENERGY_BOLT;
    }

    public boolean usesVolleyPrimaryFire() {
        return primaryWeaponFamily == PrimaryWeaponFamily.BEAM_BOLT;
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
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
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
            for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
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
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
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
        clearIntegrityFocus();
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
        clearShieldImpactMarks();

        instantRepairConsumed = true;
        return true;
    }

    public void respawnAt(double spawnX, double spawnY, double heading) {
        x = spawnX;
        y = spawnY;
        if (Double.isFinite(heading)) angle = heading;
        vx = 0.0;
        vy = 0.0;
        alive = true;
        dying = false;
        dyingTimer = 0.0;
        burnDuration = 0.0;
        wreckVx = 0.0;
        wreckVy = 0.0;
        wreckSpin = 0.0;
        deathExploded = false;
        fireSpawnTimer = 0.0;
        deathCriticalCueStage = 0;
        hp = Math.max(1, hpMax);
        temporaryDisableTimer = 0.0;
        stasisFieldTimer = 0.0;
        destabilizedTimer = 0.0;
        kineticMomentumTimer = 0.0;
        ciwsTimer = 0.0;
        fighterTimer = 0.0;
        baseSpawnTimer = 0.0;
        miningTarget = null;
        minerTarget = null;
        cargo = Math.max(0, cargo);
        revealTimer = 0.0;
        cloakActive = false;
        cloakControlMode = CloakControlMode.CHARGE;
        cloakEnergy = cloakEnergyMax;
        cloakThreatTimer = 0.0;
        primaryGunStaggerTimer = 0.0;
        primaryGunStaggerCursor = 0;
        primaryGunStaggerBurstRemaining = 0;
        bountyClaimed = false;
        playerTaggedForKillCredit = false;
        playerKillCreditPaid = false;
        carrierOwnerId = -1;
        campaignMissionSubzone = -1;
        campaignWarpSourceSubzone = -1;
        carrierOrphanTimer = -1.0;
        wingState = WingState.ATTACK;
        strikePrimaryMunitions = strikePrimaryMunitionsMax;
        strikeSecondaryMunitions = strikeSecondaryMunitionsMax;
        strikePrimaryDamageCarry = 0.0;
        strikeSecondaryDamageCarry = 0.0;
        crewCombatStress = 0.0;
        cancelBattlefieldWarp();
        setOverloadMode(false);
        resetInternalSystems();
        fullyRepairHull();
        resetShieldState();
        if (shieldActive && shieldMax > 0.0) {
            shield = shieldMax;
        } else {
            shield = 0.0;
        }
        clearHullImpactMarks();
        clearShieldImpactMarks();
        resetSuperweaponCooldown();
    }

    public void resetWeaponCycleState() {
        primaryGunStaggerTimer = 0.0;
        primaryGunStaggerCursor = 0;
        primaryGunStaggerBurstRemaining = 0;
        ciwsTimer = 0.0;
        for (Turret turret : turrets) {
            if (turret != null) turret.resetFireState();
        }
    }

    public void healShield(double amount) {
        if (!alive || !isShieldOnline()) return;
        if (amount <= 0) return;
        distributeShieldRegen(amount);
    }

    public void takeDamage(int dmg) {
        takeDamage(dmg, Double.NaN, Double.NaN);
    }

    double dyingTimerSeconds() {
        return dyingTimer;
    }

    public void takeDamage(int dmg, double hitX, double hitY) {
        takeDamage(dmg, hitX, hitY, Double.NaN, Double.NaN);
    }

    public void takeDamage(int dmg, double hitX, double hitY, double impactVx, double impactVy) {
        takeDamage(dmg, hitX, hitY, impactVx, impactVy, InteriorHitProfile.DEFAULT);
    }

    public void takeDamage(int dmg, double hitX, double hitY,
                           double impactVx, double impactVy,
                           InteriorHitProfile interiorProfile) {
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
        syncDefenseGateState(false);
        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        ShipRoomLayout.RoomDef primaryRoom = resolvePrimaryRoomForHullHit(impact, hitX, hitY, impactVx, impactVy);
        int hullDamage = dmg;

        double threatFacingAngle = resolveShieldThreatFacingAngle(hitX, hitY, impactVx, impactVy);
        int shieldGateFace = shieldFaceForImpactAngle(threatFacingAngle, getShieldFacingAngle());
        if (isShieldOnline() && shield > 0) {
            double shieldBefore = shield;
            if (Double.isFinite(threatFacingAngle)) {
                recentShieldImpactAngle = threatFacingAngle;
                recentShieldImpactTimer = 1.2;
                recentShieldImpactFace = shieldGateFace;
            }
            if (consumeShieldGateHit(shieldGateFace)) {
                double absorbedDamage = Math.min(shieldBefore, Math.max(0.0, dmg));
                if (absorbedDamage > 1e-6) {
                    registerShieldImpact(absorbedDamage, impact);
                }
                double fx = Double.isFinite(hitX) ? hitX : x;
                double fy = Double.isFinite(hitY) ? hitY : y;
                if (dmg >= MINIMUM_DAMAGE_FOR_EFFECT_SPAWN) {
                    Explosion.spawnShieldHit(fx, fy);
                }
                return;
            }
            double absorbedDamage = Math.min(shieldBefore, Math.max(0.0, dmg));
            if (absorbedDamage > 1e-6) {
                registerShieldImpact(absorbedDamage, impact);
            }
            applyShieldDamage(dmg);
            if (shield <= 0) {
                forceShieldOffline(shieldRebootDelay);
            }
            double fx = Double.isFinite(hitX) ? hitX : x;
            double fy = Double.isFinite(hitY) ? hitY : y;
            if (dmg >= MINIMUM_DAMAGE_FOR_EFFECT_SPAWN) {
                Explosion.spawnShieldHit(fx, fy);
            }

            double overflow = Math.max(0.0, dmg - shieldBefore);
            if (overflow <= 1e-6) {
                return;
            }
            hullDamage = Math.max(1, (int) Math.round(overflow));
        }

        ShipRoomLayout.RoomDef armorRoom = resolveArmorRoomForImpact(impact, primaryRoom);
        ShipRoomLayout.RoomDef interiorRoom = resolveInteriorRoomForImpact(impact, primaryRoom);
        int armorGateFace = armorGateFaceForImpact(impact, primaryRoom);
        boolean armorGateAbsorbed = hullDamage > 0 && consumeArmorGateHit(armorGateFace);
        if (armorRoom != null && hullDamage > 0) {
            double armorBefore = roomHp.getOrDefault(armorRoom.id, roomHpMax.getOrDefault(armorRoom.id, 0.0));
            double nx = (impact == null) ? Double.NaN : impact.normalizedX;
            double ny = (impact == null) ? Double.NaN : impact.normalizedY;
            damageRoom(armorRoom, hullDamage, nx, ny, false, false);
            double armorAfter = roomHp.getOrDefault(armorRoom.id, armorBefore);
            double absorbed = Math.max(0.0, armorBefore - armorAfter);
            hullDamage = Math.max(0, (int) Math.round(Math.max(0.0, hullDamage - absorbed)));
            if (armorGateAbsorbed || hullDamage <= 0) {
                registerHullImpact(Math.max(1, (int) Math.round(dmg * 0.22)), impact, armorRoom);
                applyPatternedInteriorDamageThroughArmor(dmg, impact, interiorRoom, interiorProfile, impactVx, impactVy);
                syncHullFromRoomIntegrity();
                evaluateCondemnedStateFromRooms();
                return;
            }
        } else {
            if (armorGateAbsorbed) {
                registerHullImpact(Math.max(1, (int) Math.round(dmg * 0.22)), impact, primaryRoom);
                applyPatternedInteriorDamageThroughArmor(dmg, impact, interiorRoom, interiorProfile, impactVx, impactVy);
                syncHullFromRoomIntegrity();
                evaluateCondemnedStateFromRooms();
                return;
            }
            interiorRoom = primaryRoom;
        }

        registerHullImpact(hullDamage, impact, (armorRoom != null) ? armorRoom : interiorRoom);
        int hullBefore = hp;
        RoomDamageResult split = applySystemDamageFromHullHit(
                hullDamage, impact, interiorRoom, hullBefore, interiorProfile, impactVx, impactVy);
        if (split != null) lastRoomDamageResult = split;
        syncHullFromRoomIntegrity();
        preserveSurrenderedHullState();
        evaluateCondemnedStateFromRooms();
    }

    private void applyPatternedInteriorDamageThroughArmor(int damage,
                                                           HullGeometry.ImpactSample impact,
                                                           ShipRoomLayout.RoomDef interiorRoom,
                                                           InteriorHitProfile interiorProfile,
                                                           double impactVx,
                                                           double impactVy) {
        if (interiorProfile == null || interiorProfile == InteriorHitProfile.DEFAULT) return;
        int hullBefore = hp;
        RoomDamageResult split = applySystemDamageFromHullHit(
                Math.max(1, (int) Math.round(damage * 0.55)),
                impact, interiorRoom, hullBefore, interiorProfile, impactVx, impactVy);
        if (split != null) lastRoomDamageResult = split;
    }

    public void takePenetratingInternalDamage(int dmg, double hitX, double hitY, double impactVx, double impactVy) {
        takePenetratingInternalDamage(dmg, hitX, hitY, impactVx, impactVy, InteriorHitProfile.DEFAULT);
    }

    public void takePenetratingInternalDamage(int dmg, double hitX, double hitY,
                                              double impactVx, double impactVy,
                                              InteriorHitProfile interiorProfile) {
        if (!alive || dying || dmg <= 0) return;
        lastRoomDamageResult = RoomDamageResult.NONE;
        hullImpactNoDamageTimer = 0.0;
        noDamageTimerSeconds = 0.0;
        instantRepairConsumed = false;

        reveal(2.5);
        crewCombatStress = Math.max(crewCombatStress, 1.4);
        ensureRoomSystemsInitialized();

        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        ShipRoomLayout.RoomDef primaryRoom = resolvePrimaryRoomForHullHit(impact, hitX, hitY, impactVx, impactVy);
        ShipRoomLayout.RoomDef interiorRoom = resolveInteriorRoomForImpact(impact, primaryRoom);
        int penetratingDamage = Math.max(1, (int) Math.round(dmg * 1.15));

        registerHullImpact(penetratingDamage, impact, interiorRoom);
        int hullBefore = hp;
        RoomDamageResult split = applySystemDamageFromHullHit(
                penetratingDamage, impact, interiorRoom, hullBefore, interiorProfile, impactVx, impactVy);
        if (split != null) lastRoomDamageResult = split;
        syncHullFromRoomIntegrity();
        ensureVisibleHullDamageFromInternalHit(hullBefore, interiorRoom);
        preserveSurrenderedHullState();
        evaluateCondemnedStateFromRooms();
    }

    private void ensureVisibleHullDamageFromInternalHit(int hullBefore, ShipRoomLayout.RoomDef preferredRoom) {
        if (hp < hullBefore || hullBefore <= 1 || hpMax <= 0) return;
        double total = 0.0;
        double maxTotal = 0.0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || ShipRoomLayout.isArmorRoom(def.id)) continue;
            double max = roomHpMax.getOrDefault(def.id, 0.0);
            if (max <= 0.0) continue;
            maxTotal += max;
            total += Math.max(0.0, roomHp.getOrDefault(def.id, max));
        }
        if (maxTotal <= 1e-9) return;

        double targetTotal = ((hullBefore - 1.0) + 0.49) * maxTotal / hpMax;
        double needed = Math.max(0.0, total - targetTotal);
        if (needed <= 1e-9) return;

        ArrayList<ShipRoomLayout.RoomDef> candidates = new ArrayList<>();
        if (preferredRoom != null && !ShipRoomLayout.isArmorRoom(preferredRoom.id)) candidates.add(preferredRoom);
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || ShipRoomLayout.isArmorRoom(def.id) || candidates.contains(def)) continue;
            candidates.add(def);
        }
        for (ShipRoomLayout.RoomDef def : candidates) {
            double current = roomHp.getOrDefault(def.id, 0.0);
            double grant = Math.min(current, needed);
            if (grant <= 1e-9) continue;
            roomHp.put(def.id, current - grant);
            needed -= grant;
            if (needed <= 1e-9) break;
        }
        syncHullFromRoomIntegrity();
    }

    public void scaleCurrentHullIntegrity(double factor) {
        if (!alive || dying || hp <= 0) return;
        ensureRoomSystemsInitialized();
        double clamped = MathUtil.clamp(factor, 0.0, 1.0);
        boolean changed = false;
        for (ShipRoomLayout.RoomId roomId : roomHpMax.keySet()) {
            double max = roomHpMax.getOrDefault(roomId, 0.0);
            if (max <= 1e-6) continue;
            double current = roomHp.getOrDefault(roomId, max);
            double next = Math.max(0.0, current * clamped);
            if (next + 1e-6 < current) {
                roomHp.put(roomId, next);
                changed = true;
            }
        }
        if (!changed) return;
        syncHullFromRoomIntegrity();
        preserveSurrenderedHullState();
        evaluateCondemnedStateFromRooms();
    }

    private void startDeathSequence() {
        if (dying) return;
        dying = true;
        cancelSuperweaponSequence();
        dyingTimer = 0.0;
        fireSpawnTimer = 0.0;
        deathExploded = false;
        deathCriticalCueStage = 0;

        // Preserve final motion for drift.
        wreckVx = vx;
        wreckVy = vy;

        if (isSmallCraft()) {
            explodeIntoFireball(wreckVx, wreckVy);
            return;
        }

        // Burn duration is role-scaled so small craft split quickly while heavy hulls
        // spend more time in the breach / breakup phase before the final blast.
        burnDuration = destructionBurnDurationForRole(role);

        // Random tumble while drifting out of control.
        wreckSpin = destructionSpinForRole(role);

        WreckChunk.spawnForShip(this, burnDuration);

        // Initial sparks on kill impact.
        VFX.spawnImpactSparks(x, y, 0.0, 0.0, 3);
    }

    private void explodeIntoFireball(double baseVx, double baseVy) {
        if (deathExploded) return;
        deathExploded = true;
        WreckChunk.releaseForShip(this, baseVx, baseVy);
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

    private double destructionBurnDurationForRole(ShipRole shipRole) {
        double r = randomUnit();
        if (shipRole == null) return 1.5 + r * 0.8;
        if (shipRole.isTitan()) return 2.15 + r * 1.20;
        if (shipRole.isMothership()) return 2.80 + r * 1.40;
        return switch (shipRole) {
            case FIGHTER, BOMBER, DRONE, PATROL, PICKET, MISSILE_BOAT, CIWS_CORVETTE,
                    PD_CRAFT, MINER, HAULER, TRANSPORT, STATIC_TURRET -> 0.45 + r * 0.35;
            case CARRIER, DRONE_CARRIER, BATTLESHIP, DREADNOUGHT, SUPERSHIP, STEALTH_SHIP -> 1.85 + r * 1.10;
            case BASE -> 2.25 + r * 1.35;
            default -> 1.05 + r * 0.65;
        };
    }

    private double destructionSpinForRole(ShipRole shipRole) {
        boolean multipart = ShipPartLibrary.hasDestroyedParts(shipRole, faction);
        if (multipart) {
            if (shipRole != null && shipRole.isTitanOrMothership()) {
                return (randomUnit() - 0.5) * (shipRole.isMothership() ? 0.10 : 0.13);
            }
            double base = switch (shipRole) {
                case MISSILE_BOAT, FIGHTER, BOMBER, DRONE, PATROL, PICKET, CIWS_CORVETTE,
                        PD_CRAFT, MINER, HAULER, TRANSPORT, STATIC_TURRET -> 0.55;
                case BATTLECRUISER -> 0.28;
                case SUPERSHIP, CARRIER, DRONE_CARRIER, BATTLESHIP, DREADNOUGHT, STEALTH_SHIP, BASE -> 0.16;
                default -> 0.24;
            };
            return (randomUnit() - 0.5) * base;
        }
        if (shipRole != null && shipRole.isTitanOrMothership()) {
            return (randomUnit() - 0.5) * (shipRole.isMothership() ? 0.55 : 1.10);
        }
        double base = switch (shipRole) {
            case FIGHTER, BOMBER, DRONE, PATROL, PICKET, MISSILE_BOAT, CIWS_CORVETTE,
                    PD_CRAFT, MINER, HAULER, TRANSPORT, STATIC_TURRET -> 3.1;
            case CARRIER, DRONE_CARRIER, BATTLESHIP, DREADNOUGHT, SUPERSHIP, STEALTH_SHIP -> 1.5;
            case BASE -> 0.8;
            default -> 2.2;
        };
        return (randomUnit() - 0.5) * base;
    }

    private double deathCueThreshold(int stage) {
        return switch (stage) {
            case 0 -> 0.26;
            case 1 -> 0.56;
            default -> 0.82;
        };
    }

    private void triggerDeathCriticalCue(int stage, double deathProgress) {
        emitDeathVentingFx(true, deathProgress, stage);
        double shake = switch (stage) {
            case 0 -> 1.6;
            case 1 -> 2.6;
            default -> 3.8;
        };
        ScreenShake.kick(shake);
        wreckSpin += (randomUnit() - 0.5) * (0.03 + stage * 0.015);

        double[] cue = deathCuePoint(stage, deathProgress);
        double lx = cue[0];
        double ly = cue[1];
        double outwardLocalX = cue[2];
        double outwardLocalY = cue[3];
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dirX = outwardLocalX * cos - outwardLocalY * sin;
        double dirY = outwardLocalX * sin + outwardLocalY * cos;
        double impulse = (0.05 + stage * 0.025) * GameContext.DT;
        wreckVx -= dirX * impulse;
        wreckVy -= dirY * impulse;
        if (stage >= 2) {
            VFX.spawnHullImpact(x, y, dirX, dirY, 4 + stage, VFX.ImpactStyle.EXPLOSIVE);
        }
    }

    private void emitDeathVentingFx(boolean major, double deathProgress, int stageBias) {
        int bursts = major ? 2 + Math.max(0, stageBias) : 1;
        for (int i = 0; i < bursts; i++) {
            double[] cue = deathCuePoint(i + stageBias * 3, deathProgress);
            double lx = cue[0];
            double ly = cue[1];
            double outwardLocalX = cue[2];
            double outwardLocalY = cue[3];
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double wx = x + lx * cos - ly * sin;
            double wy = y + lx * sin + ly * cos;
            double dirX = outwardLocalX * cos - outwardLocalY * sin;
            double dirY = outwardLocalX * sin + outwardLocalY * cos;
            int strength = major ? (3 + Math.max(0, stageBias)) : Math.max(1, 1 + (int) Math.round(deathProgress * 2.0));
            VFX.spawnHullImpact(wx, wy, dirX, dirY, strength, VFX.ImpactStyle.EXPLOSIVE);
            VFX.spawnImpactSparks(wx, wy, dirX, dirY, strength + (major ? 1 : 0));
        }
    }

    private double[] deathCuePoint(int cueIndex, double deathProgress) {
        int count = hullImpactMarks.size();
        if (count > 0) {
            int start = Math.max(0, count - 10);
            int idx = start + Math.floorMod(cueIndex, Math.max(1, count - start));
            HullImpactMark mark = hullImpactMarks.get(idx);
            if (mark != null) {
                double spread = Math.max(2.0, Math.min(radius * 0.16, 2.0 + mark.breachRadius * (0.16 + deathProgress * 0.20)));
                double lx = mark.localX + (randomUnit() - 0.5) * spread;
                double ly = mark.localY + (randomUnit() - 0.5) * spread;
                double len = Math.hypot(lx, ly);
                if (len > 1e-6) {
                    return new double[]{lx, ly, lx / len, ly / len};
                }
                return new double[]{lx, ly, Math.cos(angle), Math.sin(angle)};
            }
        }

        double theta = randomUnit() * Math.PI * 2.0;
        double radial = radius * (0.24 + deathProgress * 0.28 + randomUnit() * 0.16);
        double lx = Math.cos(theta) * radial;
        double ly = Math.sin(theta) * radial * 0.78;
        double len = Math.hypot(lx, ly);
        if (len <= 1e-6) {
            return new double[]{0.0, 0.0, Math.cos(angle), Math.sin(angle)};
        }
        return new double[]{lx, ly, lx / len, ly / len};
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
        totalCredits = (int) Math.round(totalCredits * (0.75 + randomUnit() * 0.55));
        totalOre = (int) Math.round(totalOre * (0.70 + randomUnit() * 0.60));

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
                double fracC = 0.18 + randomUnit() * 0.28;
                double fracO = 0.18 + randomUnit() * 0.28;
                c = (int) Math.round(remainingC * fracC);
                o = (int) Math.round(remainingO * fracO);
            }

            remainingC -= c;
            remainingO -= o;
            if (remainingC < 0) remainingC = 0;
            if (remainingO < 0) remainingO = 0;

            // Impulse direction/speed.
            double a = randomUnit() * Math.PI * 2.0;
            double sp = 90 + randomUnit() * 320; // units/sec

            double svx = baseVx + Math.cos(a) * sp * dtTick;
            double svy = baseVy + Math.sin(a) * sp * dtTick;

            double ox = (randomUnit() - 0.5) * radius * 0.6;
            double oy = (randomUnit() - 0.5) * radius * 0.6;

            double life = 22.0 + randomUnit() * 16.0;

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
        miningBuffer += Math.max(0.0, miningRate) * miningYieldMultiplier() * Math.max(0.0, dt);
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

    public boolean canReceiveOreDeposits() {
        return isBase || (role != null && role.isMothership());
    }

    /**
     * Deposit carried ore to a friendly receiver.
     * Bases accumulate ore in their stockpile; the Mothership stores it in cargo.
     *
     * @return amount deposited.
     */
    public int depositCargoTo(Ship base) {
        if (!alive || dying) return 0;
        if (cargo <= 0) return 0;
        if (base == null || !base.canReceiveOreDeposits()) return 0;
        if (!faction.isFriendlyTo(base.faction)) return 0;

        int moved;
        if (base.isBase) {
            moved = cargo;
            cargo = 0;
            base.oreStockpile += moved;
        } else {
            int room = Math.max(0, base.cargoMax - base.cargo);
            moved = Math.min(cargo, room);
            if (moved <= 0) return 0;
            cargo -= moved;
            base.cargo = Math.min(base.cargoMax, base.cargo + moved);
        }
        return moved;
    }

    /** Make the ship easier to see/lock for a short time. */
    public void reveal(double seconds) {
        if (!isStealth) return;
        revealTimer = Math.max(revealTimer, seconds);
        cloakActive = false;
        cloakThreatTimer = Math.max(cloakThreatTimer, Math.max(1.2, seconds + 0.75));
    }

    /** Called when this ship fires a weapon; helps prevent perma-cloaking while shooting. */
    public void onFiredWeapon() {
        weaponsHotTimer = Math.max(weaponsHotTimer, 1.6);
        reveal(1.4);
        crewCombatStress = Math.max(crewCombatStress, 1.0);
        if (factionTrait().id == ShipIdentityRegistry.FactionTraitId.KINETIC_MOMENTUM) {
            kineticMomentumTimer = KINETIC_MOMENTUM_WINDOW_SECONDS;
        }
    }

    public boolean usesLimitedStrikeCraftMunitions() {
        return switch (role) {
            case FIGHTER, BOMBER, PD_CRAFT, DRONE -> true;
            default -> false;
        };
    }

    public boolean isSmallCraft() {
        return switch (role) {
            case FIGHTER, BOMBER, PD_CRAFT, DRONE -> true;
            default -> false;
        };
    }

    public boolean canUseBattlefieldWarp() {
        if (!alive || dying || hp <= 0) return false;
        if (isTemporarilyDisabled()) return false;
        if (isStasisFieldTrapped()) return false;
        if (isSmallCraft()) return false;
        if (role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return false;
        return propulsionRoomIntegrity() > 0.20
                && systemHealthFraction(InternalSystem.WARP_ENGINES) > 0.20
                && systemHealthFraction(InternalSystem.BRIDGE) > 0.15;
    }

    public boolean isWarpCharging() {
        return warpCharging;
    }

    public double warpChargeSpeedMultiplier() {
        return warpCharging ? 0.5 : 1.0;
    }

    public ShipIdentityRegistry.FactionTrait factionTrait() {
        return ShipIdentityRegistry.factionTraitFor(faction);
    }

    public String factionTraitName() {
        return factionTrait().name;
    }

    public String factionTraitDescription() {
        return factionTrait().description;
    }

    public ShipIdentityRegistry.RoleBonus roleBonusProfile() {
        return ShipIdentityRegistry.roleBonusFor(faction, role);
    }

    public ShipIdentityRegistry.IdentityStat roleBonusStat() {
        return roleBonusProfile().stat;
    }

    public double roleBonusMultiplier() {
        return roleBonusProfile().multiplier;
    }

    public String roleBonusName() {
        return roleBonusProfile().name;
    }

    public String roleBonusDescription() {
        return roleBonusProfile().description;
    }

    public double warpChargeRemaining() {
        return Math.max(0.0, warpChargeRemaining);
    }

    public double warpChargeDuration() {
        return Math.max(0.0, warpChargeDuration);
    }

    public double warpChargeProgress() {
        if (!warpCharging || warpChargeDuration <= 1e-6) return 0.0;
        return MathUtil.clamp(1.0 - (warpChargeRemaining / warpChargeDuration), 0.0, 1.0);
    }

    public double warpExitX() {
        return warpExitX;
    }

    public double warpExitY() {
        return warpExitY;
    }

    public int warpFormationLeaderId() {
        return warpFormationLeaderId;
    }

    public String warpSourceSectorId() {
        return warpSourceSectorId;
    }

    public void setWarpSourceSectorId(String sectorId) {
        warpSourceSectorId = (sectorId == null) ? "" : sectorId.trim();
    }

    public boolean beginBattlefieldWarp(double targetX, double targetY, double spoolSeconds) {
        warpFormationLeaderId = -1;
        return beginBattlefieldWarpInternal(targetX, targetY, spoolSeconds);
    }

    public boolean beginBattlefieldWarpFollowing(double targetX, double targetY, double spoolSeconds, int leaderShipId) {
        warpFormationLeaderId = Math.max(0, leaderShipId);
        return beginBattlefieldWarpInternal(targetX, targetY, spoolSeconds);
    }

    private boolean beginBattlefieldWarpInternal(double targetX, double targetY, double spoolSeconds) {
        if (!canUseBattlefieldWarp()) return false;
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY)) return false;
        warpCharging = true;
        warpChargeDuration = Math.max(0.1, spoolSeconds / Math.max(0.10, warpChargeRateMultiplier()));
        warpChargeRemaining = warpChargeDuration;
        warpExitX = targetX;
        warpExitY = targetY;
        return true;
    }

    public void cancelBattlefieldWarp() {
        warpCharging = false;
        warpChargeRemaining = 0.0;
        warpChargeDuration = 0.0;
        warpExitX = Double.NaN;
        warpExitY = Double.NaN;
        warpFormationLeaderId = -1;
        warpSourceSectorId = "";
    }

    public void tickBattlefieldWarp(double dt) {
        if (!warpCharging || dt <= 0.0) return;
        warpChargeRemaining = Math.max(0.0, warpChargeRemaining - dt);
    }

    public boolean isBattlefieldWarpReady() {
        return warpCharging && warpChargeRemaining <= 1e-6
                && Double.isFinite(warpExitX) && Double.isFinite(warpExitY);
    }

    public void configureStrikeCraftMunitions() {
        if (!usesLimitedStrikeCraftMunitions()) {
            strikePrimaryMunitionsMax = 0;
            strikePrimaryMunitions = 0;
            strikeSecondaryMunitionsMax = 0;
            strikeSecondaryMunitions = 0;
            strikePrimaryDamageCarry = 0.0;
            strikeSecondaryDamageCarry = 0.0;
            return;
        }

        switch (role) {
            case FIGHTER -> {
                strikePrimaryMunitionsMax = 36;
                strikeSecondaryMunitionsMax = 0;
            }
            case BOMBER -> {
                strikePrimaryMunitionsMax = 14;
                strikeSecondaryMunitionsMax = 6;
            }
            case PD_CRAFT -> {
                strikePrimaryMunitionsMax = 28;
                strikeSecondaryMunitionsMax = 0;
            }
            case DRONE -> {
                strikePrimaryMunitionsMax = 10;
                strikeSecondaryMunitionsMax = 4;
            }
            default -> {
                strikePrimaryMunitionsMax = 0;
                strikeSecondaryMunitionsMax = 0;
            }
        }
        reloadStrikeCraftMunitions();
    }

    public void reloadStrikeCraftMunitions() {
        strikePrimaryMunitions = Math.max(0, strikePrimaryMunitionsMax);
        strikeSecondaryMunitions = Math.max(0, strikeSecondaryMunitionsMax);
        strikePrimaryDamageCarry = 0.0;
        strikeSecondaryDamageCarry = 0.0;
    }

    public boolean hasStrikeCraftMunitionsFor(Turret turret) {
        if (!usesLimitedStrikeCraftMunitions() || turret == null) return true;
        return turret.primary ? strikePrimaryMunitions > 0 : strikeSecondaryMunitions > 0;
    }

    public boolean consumeStrikeCraftMunition(Turret turret) {
        if (!usesLimitedStrikeCraftMunitions() || turret == null) return true;
        if (turret.primary) {
            if (strikePrimaryMunitions <= 0) return false;
            strikePrimaryMunitions--;
            return true;
        }
        if (strikeSecondaryMunitions <= 0) return false;
        strikeSecondaryMunitions--;
        return true;
    }

    public boolean needsStrikeCraftRearm() {
        if (!usesLimitedStrikeCraftMunitions()) return false;
        boolean hasPrimary = false;
        boolean hasSecondary = false;
        for (Turret turret : turrets) {
            if (turret == null) continue;
            if (turret.primary) hasPrimary = true;
            else hasSecondary = true;
        }
        boolean primaryDry = !hasPrimary || strikePrimaryMunitions <= 0;
        boolean secondaryDry = !hasSecondary || strikeSecondaryMunitions <= 0;
        return primaryDry && secondaryDry;
    }

    public int resolveStrikeCraftWeaponDamage(Turret turret, double baseDamage) {
        if (!usesLimitedStrikeCraftMunitions() || turret == null) {
            return (int) Math.round(Math.max(0.0, baseDamage));
        }
        double exact = Math.max(0.0, baseDamage) * 0.5;
        if (turret.primary) {
            exact += strikePrimaryDamageCarry;
            int out = (int) Math.floor(exact + 1e-9);
            strikePrimaryDamageCarry = Math.max(0.0, exact - out);
            return out;
        }
        exact += strikeSecondaryDamageCarry;
        int out = (int) Math.floor(exact + 1e-9);
        strikeSecondaryDamageCarry = Math.max(0.0, exact - out);
        return out;
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

    public boolean cloakWantsActive() {
        return cloakControlMode == CloakControlMode.ACTIVE;
    }

    public void setCloakControlMode(CloakControlMode mode) {
        cloakControlMode = (mode == null) ? CloakControlMode.CHARGE : mode;
        if (cloakControlMode != CloakControlMode.ACTIVE) {
            cloakActive = false;
        }
    }

    public void noteCloakThreat(double seconds) {
        if (!isStealth) return;
        cloakThreatTimer = Math.max(cloakThreatTimer, Math.max(0.0, seconds));
    }

    private double cloakEngageThreshold() {
        return Math.max(cloakMinEnergyToEngage, cloakEnergyMax * 0.55);
    }

    private double cloakReserveThreshold() {
        return Math.max(cloakMinEnergyToEngage * 0.45, cloakEnergyMax * 0.16);
    }

    private void updateStealthCloak(double dt) {
        if (!isStealth || dt <= 0.0) return;

        if (cloakEnergyMax <= 0.01) cloakEnergyMax = 0.01;
        cloakEnergy = Math.max(0.0, Math.min(cloakEnergyMax, cloakEnergy));

        if (!cloakEnabled) {
            cloakActive = false;
            cloakControlMode = CloakControlMode.CHARGE;
            cloakEnergy = Math.min(cloakEnergyMax, cloakEnergy + cloakRechargePerSec * dt);
            return;
        }

        if (revealTimer > 0.0 || cloakControlMode != CloakControlMode.ACTIVE) {
            cloakActive = false;
            cloakEnergy = Math.min(cloakEnergyMax, cloakEnergy + cloakRechargePerSec * dt);
            return;
        }

        if (!cloakActive && cloakEnergy >= cloakEngageThreshold()) {
            cloakActive = true;
        }

        if (cloakActive) {
            cloakEnergy -= cloakDrainPerSec * dt;
            if (cloakEnergy <= cloakReserveThreshold()) {
                cloakEnergy = Math.max(0.0, cloakEnergy);
                cloakActive = false;
                cloakControlMode = CloakControlMode.CHARGE;
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
        roomDisruptionRepairProgress.clear();
        roomDisabledSystems.clear();
        roomDamageEvents.clear();
        lastRoomDamageResult = RoomDamageResult.NONE;
        clearIntegrityFocus();
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

    public List<ShieldImpactMark> shieldImpactMarks() {
        return shieldImpactMarksView;
    }

    public void clearHullImpactMarks() {
        hullImpactMarks.clear();
        hullImpactNoDamageTimer = HULL_IMPACT_DECAY_IDLE_SECONDS;
    }

    public void clearShieldImpactMarks() {
        shieldImpactMarks.clear();
    }

    public List<RoomStatus> roomStatusSnapshot() {
        ensureRoomSystemsInitialized();
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role, faction);
        List<RoomStatus> out = new ArrayList<>(defs.size());
        for (ShipRoomLayout.RoomDef d : defs) {
            double hpv = roomHp.getOrDefault(d.id, 0.0);
            double maxv = roomHpMax.getOrDefault(d.id, 1.0);
            double fire = roomHazards.containsKey(d.id) ? roomHazards.get(d.id).fireIntensity : 0.0;
            boolean disrupted = roomDisruptionRepairProgress.containsKey(d.id);
            double repairProgress = roomDisruptionRepairProgress.getOrDefault(d.id, 0.0);
            out.add(new RoomStatus(
                    d.id, d.label, d.xs, d.ys, hpv, maxv, d.critical, fire, disrupted, repairProgress, d.primarySystem
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
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role, faction);
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
            if (roomDisruptionRepairProgress.containsKey(d.id)) statusFlags |= ShipRoom.STATUS_DISRUPTED;

            List<String> tags = new ArrayList<>(3);
            if (d.primarySystem != null) tags.add(d.primarySystem.name().toLowerCase());
            if (d.critical) tags.add("critical");
            if (fire > 1e-4) tags.add("hazard_fire");
            if (roomDisruptionRepairProgress.containsKey(d.id)) tags.add("hazard_disruption");

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
        Double hpValue = roomHp.get(roomId);
        Double maxValue = roomHpMax.get(roomId);
        if (maxValue == null || maxValue <= 1e-9) return 1.0;
        double maxv = maxValue;
        double hpv = (hpValue == null) ? maxv : hpValue;
        return Math.max(0.0, Math.min(1.0, hpv / maxv));
    }

    private double roomClusterAverageFraction(ShipRoomLayout.RoomId... roomIds) {
        ensureRoomSystemsInitialized();
        if (roomIds == null || roomIds.length <= 0) return 1.0;
        double total = 0.0;
        int count = 0;
        for (ShipRoomLayout.RoomId roomId : roomIds) {
            if (roomId == null || !roomHpMax.containsKey(roomId)) continue;
            total += roomHealthFraction(roomId);
            count++;
        }
        if (count <= 0) return 1.0;
        return total / count;
    }

    public double roomFireIntensity(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return 0.0;
        RoomHazardState hz = roomHazards.get(roomId);
        if (hz == null) return 0.0;
        return Math.max(0.0, hz.fireIntensity);
    }

    public boolean isRoomDisrupted(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return false;
        return roomDisruptionRepairProgress.containsKey(roomId);
    }

    public double roomDisruptionRepairProgress(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return 0.0;
        return MathUtil.clamp(roomDisruptionRepairProgress.getOrDefault(roomId, 0.0), 0.0, 1.0);
    }

    public int activeRoomDisruptionCount() {
        ensureRoomSystemsInitialized();
        return roomDisruptionRepairProgress.size();
    }

    public double roomDisruptionFraction() {
        ensureRoomSystemsInitialized();
        int total = 0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            Double hpMaxValue = roomHpMax.get(def.id);
            if (hpMaxValue == null || hpMaxValue <= 1e-6) continue;
            total++;
        }
        if (total <= 0) return 0.0;
        return MathUtil.clamp(activeRoomDisruptionCount() / (double) total, 0.0, 1.0);
    }

    public ShipRoomLayout.RoomId disruptionRepairTargetRoom() {
        return selectRoomDisruptionRepairTarget();
    }

    public double disruptionRepairTargetProgress() {
        ShipRoomLayout.RoomId target = selectRoomDisruptionRepairTarget();
        if (target == null) return 0.0;
        return roomDisruptionRepairProgress(target);
    }

    public int activeFireRoomCount() {
        ensureRoomSystemsInitialized();
        int count = 0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            RoomHazardState hz = roomHazards.get(def.id);
            if (hz != null && hz.fireIntensity > 0.05) count++;
        }
        return count;
    }

    public double totalFireIntensity() {
        ensureRoomSystemsInitialized();
        double total = 0.0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            RoomHazardState hz = roomHazards.get(def.id);
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
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            RoomHazardState hz = roomHazards.get(def.id);
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

    public ShipRoomLayout.RoomId integrityFocusRoom() {
        return (integrityFocusTimer > 1e-6) ? integrityFocusRoom : null;
    }

    public double integrityFocusRemaining() {
        return Math.max(0.0, integrityFocusTimer);
    }

    public boolean setIntegrityFocus(ShipRoomLayout.RoomId roomId, double seconds) {
        if (!alive || dying) return false;
        if (roomId == null || seconds <= 0.0) {
            clearIntegrityFocus();
            return true;
        }
        if (!isIntegrityContainmentEligibleRoom(roomId)) {
            clearIntegrityFocus();
            return false;
        }
        integrityFocusRoom = roomId;
        integrityFocusTimer = Math.max(0.0, seconds);
        return true;
    }

    public void clearIntegrityFocus() {
        integrityFocusRoom = null;
        integrityFocusTimer = 0.0;
    }

    private boolean hasManualIntegrityFocus() {
        return integrityFocusTimer > 1e-6 && integrityFocusRoom != null;
    }

    private ShipRoomLayout.RoomId activeIntegrityProtectionRoom(ShipRoomLayout.RoomId candidateRoom) {
        if (hasManualIntegrityFocus()) {
            return isIntegrityContainmentEligibleRoom(integrityFocusRoom) ? integrityFocusRoom : null;
        }
        if (!integrityContainmentAvailable()) return null;
        return bestAutomaticIntegrityProtectionRoom(candidateRoom);
    }

    private boolean integrityContainmentAvailable() {
        if (!alive || dying) return false;
        if (reactorBlackoutActive()) return false;
        return isRoomOperational(ShipRoomLayout.RoomId.INTEGRITY_FIELD)
                && roomHealthFraction(ShipRoomLayout.RoomId.INTEGRITY_FIELD) >= 0.30;
    }

    private boolean roomNeedsIntegrityContainment(ShipRoomLayout.RoomId roomId, boolean includeCandidate) {
        if (!isIntegrityContainmentEligibleRoom(roomId)) return false;
        if (includeCandidate) return true;
        if (roomHealthFraction(roomId) < 0.999) return true;
        if (roomFireIntensity(roomId) > 0.05) return true;
        return isRoomDisrupted(roomId);
    }

    private ShipRoomLayout.RoomId bestAutomaticIntegrityProtectionRoom(ShipRoomLayout.RoomId candidateRoom) {
        ShipRoomLayout.RoomId bestRoom = null;
        double bestScore = 0.08;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            if (ShipRoomLayout.isArmorRoom(def.id)) continue;
            boolean includeCandidate = def.id == candidateRoom;
            if (!roomNeedsIntegrityContainment(def.id, includeCandidate)) continue;

            double score = integrityContainmentPriorityScore(def, includeCandidate);
            if (score > bestScore) {
                bestScore = score;
                bestRoom = def.id;
            }
        }
        return bestRoom;
    }

    private double integrityContainmentPriorityScore(ShipRoomLayout.RoomDef def, boolean includeCandidate) {
        if (def == null || def.id == null) return 0.0;
        double hpFrac = roomHealthFraction(def.id);
        double fire = roomFireIntensity(def.id);
        boolean disrupted = isRoomDisrupted(def.id);

        double score = includeCandidate ? 5.2 : 0.0;
        score += (1.0 - hpFrac) * 4.8;
        score += Math.min(3.0, fire * 2.2);
        if (disrupted) score += 1.8;
        if (def.critical) score += 1.0;
        if (def.primarySystem != null) score += 0.5;
        if (def.primarySystem != null && engineeringPriorityMatches(def.primarySystem)) score += 0.8;
        if (crewOrder == CrewOrder.DAMAGE_CONTROL) score += 0.7;
        if (crewOrder == CrewOrder.ENGINEERING) score += 0.35;
        return score;
    }

    private boolean isIntegrityContainmentEligibleRoom(ShipRoomLayout.RoomId roomId) {
        if (roomId == null || ShipRoomLayout.isArmorRoom(roomId)) return false;
        ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, faction, roomId);
        if (def == null) return false;
        return roomId != ShipRoomLayout.RoomId.INTEGRITY_FIELD;
    }

    private boolean engineeringPriorityMatches(InternalSystem system) {
        if (system == null) return false;
        return switch (engineeringPriority()) {
            case PROPULSION -> system == InternalSystem.ENGINES || system == InternalSystem.WARP_ENGINES;
            case SHIELDS -> system == InternalSystem.SHIELDS;
            case WEAPONS -> system == InternalSystem.WEAPONS || system == InternalSystem.MAGAZINES;
            case SENSORS -> system == InternalSystem.SENSORS || system == InternalSystem.BRIDGE;
            case REACTOR -> system == InternalSystem.REACTOR_CORE;
            case BALANCED -> false;
        };
    }

    /**
     * Scripted scenario hook used by tutorials and future authored missions.
     */
    public void seedRoomFire(ShipRoomLayout.RoomId roomId, double intensity) {
        igniteRoomFire(roomId, intensity);
    }

    /**
     * Area-support effect used by support transports.
     * - Heals every room by a fraction of that room's max HP per second.
     * - Reduces active fire lifetime by applying proportional fire intensity decay.
     */
    public void applySupportField(double roomHealFracPerSec, double fireReductionFracPerSec, double dt) {
        if (!alive || dying || dt <= 0.0) return;
        ensureRoomSystemsInitialized();

        double roomHealFrac = Math.max(0.0, roomHealFracPerSec) * dt;
        if (roomHealFrac > 0.0) {
            for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
                if (def == null || def.id == null) continue;
                Double maxValue = roomHpMax.get(def.id);
                if (maxValue == null) continue;
                double maxv = maxValue;
                if (maxv <= 1e-9) continue;
                Double currentValue = roomHp.get(def.id);
                double cur = (currentValue == null) ? maxv : currentValue;
                if (cur >= maxv - 1e-9) continue;
                double add = maxv * roomHealFrac;
                if (add <= 1e-9) continue;
                roomHp.put(def.id, Math.min(maxv, cur + add));
            }
        }

        double fireDecayFrac = MathUtil.clamp(Math.max(0.0, fireReductionFracPerSec) * dt, 0.0, 0.95);
        if (fireDecayFrac > 0.0) {
            for (RoomHazardState hz : roomHazards.values()) {
                if (hz == null || hz.fireIntensity <= 1e-4) continue;
                hz.fireIntensity = hz.fireIntensity * (1.0 - fireDecayFrac);
                hz.suppressionBoost = Math.min(2.2, hz.suppressionBoost + fireDecayFrac * 0.45);
                if (hz.fireIntensity <= 0.02) {
                    hz.fireIntensity = 0.0;
                    hz.damageTickTimer = 0.0;
                    hz.spreadTimer = 0.0;
                    hz.instabilityTimer = 0.0;
                    hz.vfxTimer = 0.0;
                }
            }
        }

        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
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
        PowerPreset[] presets = {
                PowerPreset.BALANCED,
                PowerPreset.ATTACK,
                PowerPreset.DEFENSE,
                PowerPreset.PURSUIT
        };
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == powerPreset) {
                idx = i + 1;
                break;
            }
        }
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
            case CUSTOM -> normalizePowerAllocation();
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

    public void setCustomPowerBusAllocation(double propulsion,
                                            double shield,
                                            double tactical,
                                            double sensor,
                                            double engineering,
                                            double auxiliary) {
        setPowerBusAllocation(propulsion, shield, tactical, sensor, engineering, auxiliary);
        powerPreset = PowerPreset.CUSTOM;
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

    public double powerBusNominalFraction(PowerBus bus) {
        return powerBusNominalTarget((bus == null) ? PowerBus.ENGINEERING : bus);
    }

    public double powerBusUsefulCapFraction(PowerBus bus) {
        PowerBus b = (bus == null) ? PowerBus.ENGINEERING : bus;
        return MathUtil.clamp(powerBusNominalTarget(b) * POWER_BUS_UI_SOFT_CAP_RATIO, 0.08, 1.0);
    }

    public double powerBusUsefulFillFraction(PowerBus bus) {
        PowerBus b = (bus == null) ? PowerBus.ENGINEERING : bus;
        double cap = powerBusUsefulCapFraction(b);
        if (cap <= 1e-9) return 0.0;
        return MathUtil.clamp(powerBusFraction(b) / cap, 0.0, 1.0);
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

    public CrewPriority crewPriority() {
        return (crewPriority == null) ? CrewPriority.AUTO_REPAIR : crewPriority;
    }

    public void setCrewPriority(CrewPriority priority) {
        crewPriority = (priority == null) ? CrewPriority.AUTO_REPAIR : priority;
        if (crewPriority != CrewPriority.MANUAL_ROOM) crewManualPriorityRoom = null;
        retaskCrewTeams();
    }

    public void setCrewManualPriorityRoom(ShipRoomLayout.RoomId roomId) {
        if (roomId == null || ShipRoomLayout.roomForId(role, faction, roomId) == null) {
            crewManualPriorityRoom = null;
            crewPriority = CrewPriority.AUTO_REPAIR;
        } else {
            crewManualPriorityRoom = roomId;
            crewPriority = CrewPriority.MANUAL_ROOM;
        }
        retaskCrewTeams();
    }

    public ShipRoomLayout.RoomId crewManualPriorityRoom() {
        return crewManualPriorityRoom;
    }

    public List<CrewTeamSnapshot> crewTeamSnapshots() {
        ensureCrewTeamsInitialized();
        ArrayList<CrewTeamSnapshot> out = new ArrayList<>(crewTeams.size());
        CrewPriority priority = crewPriority();
        for (CrewTeam team : crewTeams) {
            if (team == null) continue;
            out.add(new CrewTeamSnapshot(team, priority));
        }
        return Collections.unmodifiableList(out);
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
        double engine = roomClusterAverageFraction(PROPULSION_ENGINE_ROOMS);
        double warp = roomHealthFraction(ShipRoomLayout.RoomId.WARP_DRIVE);
        return MathUtil.clamp(engine * 0.72 + warp * 0.28, 0.0, 1.0);
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

    void applyPersistentCombatState(double armorFraction, double readinessFraction) {
        crewReadiness = MathUtil.clamp(readinessFraction, 0.05, 1.0);
        syncDefenseGateState(false);
        double armor = MathUtil.clamp(armorFraction, 0.0, 1.0);
        for (int face = 0; face < SHIELD_FACE_COUNT; face++) {
            int cap = Math.max(0, armorGateHitsMax[face]);
            armorGateHitsRemaining[face] = Math.max(0, Math.min(cap, (int) Math.round(cap * armor)));
        }
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
        return shieldFaceCapacity(face);
    }

    public double effectiveShieldCapacityMax() {
        ensureShieldFacesSynced();
        return Math.max(0.0, Math.min(Math.max(0.0, shieldMax), totalShieldFaceCapacity()));
    }

    public double shieldPassthroughChance() {
        if (!shieldActive || shield <= 1e-9) return 1.0;
        double effectiveMax = effectiveShieldCapacityMax();
        if (effectiveMax <= 1e-9) return 1.0;
        return 0.0;
    }

    public int externalShieldGateHitCap() {
        syncDefenseGateState(false);
        return maxGateValue(shieldGateHitsMax);
    }

    public int externalShieldGateHitsRemaining() {
        syncDefenseGateState(false);
        return minGateValue(shieldGateHitsRemaining);
    }

    public int externalShieldGateHitCap(int face) {
        syncDefenseGateState(false);
        return gateValueForFace(shieldGateHitsMax, face);
    }

    public int externalShieldGateHitsRemaining(int face) {
        syncDefenseGateState(false);
        return gateValueForFace(shieldGateHitsRemaining, face);
    }

    public int armorGateHitCap() {
        syncDefenseGateState(false);
        return maxGateValue(armorGateHitsMax);
    }

    public int armorGateHitsRemaining() {
        syncDefenseGateState(false);
        return minGateValue(armorGateHitsRemaining);
    }

    public int armorGateHitCap(int face) {
        syncDefenseGateState(false);
        return gateValueForFace(armorGateHitsMax, face);
    }

    public int armorGateHitsRemaining(int face) {
        syncDefenseGateState(false);
        return gateValueForFace(armorGateHitsRemaining, face);
    }

    public double collapseShield(double offlineSeconds, double hitX, double hitY, double impactVx, double impactVy) {
        ensureShieldFacesSynced();
        if (!shieldActive) return 0.0;
        double effectiveMax = effectiveShieldCapacityMax();
        if (effectiveMax <= 1e-9) return 0.0;

        double stripped = Math.max(0.0, shield);
        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        double threatFacingAngle = resolveShieldThreatFacingAngle(hitX, hitY, impactVx, impactVy);
        int impactFace = shieldFaceForImpactAngle(threatFacingAngle, getShieldFacingAngle());
        if (Double.isFinite(threatFacingAngle)) {
            recentShieldImpactAngle = threatFacingAngle;
            recentShieldImpactTimer = 1.2;
            recentShieldImpactFace = impactFace;
        }
        if (stripped > 1e-6) {
            registerShieldImpact(stripped, impact);
        }
        forceShieldOffline(Math.max(0.1, offlineSeconds));
        return stripped;
    }

    public double drainShieldByMaxFraction(double fraction,
                                           double hitX,
                                           double hitY,
                                           double impactVx,
                                           double impactVy) {
        ensureShieldFacesSynced();
        if (!shieldActive) return 0.0;
        double effectiveMax = effectiveShieldCapacityMax();
        if (effectiveMax <= 1e-9 || shield <= 1e-9) return 0.0;

        double frac = MathUtil.clamp(fraction, 0.0, 1.0);
        if (frac <= 1e-9) return 0.0;

        double drained = Math.min(shield, effectiveMax * frac);
        if (drained <= 1e-9) return 0.0;

        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        double threatFacingAngle = resolveShieldThreatFacingAngle(hitX, hitY, impactVx, impactVy);
        int impactFace = shieldFaceForImpactAngle(threatFacingAngle, getShieldFacingAngle());
        if (Double.isFinite(threatFacingAngle)) {
            recentShieldImpactAngle = threatFacingAngle;
            recentShieldImpactTimer = 1.2;
            recentShieldImpactFace = impactFace;
        }
        registerShieldImpact(drained, impact);
        applyShieldDamage(drained);
        if (shield <= 1e-6) {
            forceShieldOffline(Math.max(0.5, shieldRebootDelay * 0.75));
        }
        return drained;
    }

    public double drainShieldByAmount(double amount,
                                      double hitX,
                                      double hitY,
                                      double impactVx,
                                      double impactVy) {
        ensureShieldFacesSynced();
        if (!shieldActive) return 0.0;
        double effectiveMax = effectiveShieldCapacityMax();
        if (effectiveMax <= 1e-9 || shield <= 1e-9) return 0.0;

        double drained = Math.min(shield, Math.max(0.0, amount));
        if (drained <= 1e-9) return 0.0;

        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        double threatFacingAngle = resolveShieldThreatFacingAngle(hitX, hitY, impactVx, impactVy);
        int impactFace = shieldFaceForImpactAngle(threatFacingAngle, getShieldFacingAngle());
        if (Double.isFinite(threatFacingAngle)) {
            recentShieldImpactAngle = threatFacingAngle;
            recentShieldImpactTimer = 1.2;
            recentShieldImpactFace = impactFace;
        }
        registerShieldImpact(drained, impact);
        applyShieldDamage(drained);
        if (shield <= 1e-6) {
            forceShieldOffline(Math.max(0.35, shieldRebootDelay * 0.55));
        }
        return drained;
    }

    public double shieldFaceFraction(int face) {
        double max = shieldFaceMax(face);
        if (max <= 1e-9) return 0.0;
        return Math.max(0.0, Math.min(1.0, shieldFaceValue(face) / max));
    }

    public boolean hasRecentShieldImpactTelemetry() {
        return recentShieldImpactTimer > 1e-6 && recentShieldImpactFace >= 0 && recentShieldImpactFace < SHIELD_FACE_COUNT;
    }

    public int recentShieldImpactFace() {
        return hasRecentShieldImpactTelemetry() ? recentShieldImpactFace : -1;
    }

    public double recentShieldImpactAngle() {
        return hasRecentShieldImpactTelemetry() ? recentShieldImpactAngle : Double.NaN;
    }

    public double recentShieldImpactTelemetryFraction() {
        return Math.max(0.0, Math.min(1.0, recentShieldImpactTimer / 1.2));
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
        out *= roomDisruptionSystemMultiplier();
        out *= identityStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_DAMAGE);
        return Math.max(0.12, Math.min(1.50, out));
    }

    public double weaponCycleRateMultiplier() {
        double weapons = systemHealthFraction(InternalSystem.WEAPONS);
        double reactor = systemHealthFraction(InternalSystem.REACTOR_CORE);
        double out = 0.32 + 0.48 * weapons + 0.20 * reactor;
        out *= weaponsPowerMultiplier();
        out *= crewWeaponMul;
        out *= roomDisruptionSystemMultiplier();
        out *= identityStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_CYCLE);
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
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.PORT_POWER) * 0.10;
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.STARBOARD_POWER) * 0.10;
        localFirePenalty += roomFireIntensity(ShipRoomLayout.RoomId.INTEGRITY_FIELD) * 0.12;
        localFirePenalty += totalFireIntensity() * 0.05;
        out *= (1.0 - MathUtil.clamp(localFirePenalty, 0.0, 0.72));
        out *= roomDisruptionSystemMultiplier();
        out *= identityStatMultiplier(ShipIdentityRegistry.IdentityStat.SENSOR_RANGE);
        return Math.max(0.16, Math.min(1.25, out));
    }

    public double shieldSystemMultiplier() {
        return Math.max(0.0, Math.min(1.0, systemHealthFraction(InternalSystem.SHIELDS)));
    }

    public double shieldRegenMultiplier() {
        if (reactorBlackoutActive()) return 0.0;
        double regen = shieldSystemMultiplier();
        regen *= shieldsPowerMultiplier();
        regen *= crewShieldMul;
        regen *= teamCShieldStripRegenMultiplier();
        regen *= roomDisruptionSystemMultiplier();
        regen *= identityStatMultiplier(ShipIdentityRegistry.IdentityStat.SHIELD_REGEN);
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

    public void rebuildDefenseStateForCurrentStats() {
        internalSystemsInitialized = false;
        roomSystemsInitialized = false;
        systemHp.clear();
        systemHpMax.clear();
        roomHp.clear();
        roomHpMax.clear();
        roomHazards.clear();
        roomDisruptionRepairProgress.clear();
        roomDisabledSystems.clear();
        shieldFacesInitialized = false;
        shieldFacesSyncedMax = Double.NaN;
        Arrays.fill(shieldGateHitsRemaining, 0);
        Arrays.fill(shieldGateHitsMax, -1);
        Arrays.fill(shieldGateRechargeTimer, 0.0);
        Arrays.fill(armorGateHitsRemaining, 0);
        Arrays.fill(armorGateHitsMax, -1);
    }

    private void ensureRoomSystemsInitialized() {
        if (roomSystemsInitialized) return;
        ensureInternalSystemsInitialized();

        roomHp.clear();
        roomHpMax.clear();
        roomHazards.clear();
        roomDisruptionRepairProgress.clear();

        double base = Math.max(24.0, hpMax + shieldMax * 0.35 + radius * 0.90);
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            double max = Math.max(8.0, base * Math.max(0.25, def.hpWeight));
            if (ShipRoomLayout.isShieldStripRoom(def.id)) {
                max *= Math.max(0.10, shieldStripRoomHpMultiplier);
            } else if (ShipRoomLayout.isArmorRoom(def.id) && !hasArmorLayer()) {
                max = 0.0;
            } else if (ShipRoomLayout.isArmorRoom(def.id)) {
                max *= Math.max(0.25, armorRoomHpMultiplier);
            }
            roomHpMax.put(def.id, max);
            roomHp.put(def.id, max);
            roomHazards.put(def.id, new RoomHazardState(def.id));
        }

        roomDisabledSystems.clear();
        roomSystemsInitialized = true;
        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
    }

    private boolean hasArmorLayer() {
        if (faction != null && faction.isYellowLineage()) return true;
        return switch (role) {
            case FIGHTER, BOMBER, DRONE -> false;
            default -> true;
        };
    }

    private double totalRoomIntegrityFraction() {
        ensureRoomSystemsInitialized();
        double total = 0.0;
        double maxTotal = 0.0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || ShipRoomLayout.isArmorRoom(def.id)) continue;
            Double maxValue = roomHpMax.get(def.id);
            double max = (maxValue == null) ? 0.0 : maxValue;
            if (max <= 0.0) continue;
            maxTotal += max;
            Double hpValue = roomHp.get(def.id);
            total += Math.max(0.0, (hpValue == null) ? max : hpValue);
        }
        if (maxTotal <= 1e-9) return 1.0;
        return Math.max(0.0, Math.min(1.0, total / maxTotal));
    }

    private double roomIntegrityDamageBudgetMultiplier() {
        if (role == null) return 1.0;
        if (role.isMothership()) return 0.60;
        if (role.isTitan()) return 0.68;
        return switch (role) {
            case SUPERSHIP -> 0.72;
            case DREADNOUGHT -> 0.78;
            case BATTLESHIP, CARRIER, DRONE_CARRIER -> 0.84;
            case BATTLECRUISER -> 0.90;
            default -> 1.0;
        };
    }

    private double roomCondemnedThreshold() {
        if (role == null) return BASE_ROOM_CONDEMNED_THRESHOLD;
        if (role.isMothership()) return 0.18;
        if (role.isTitan()) return 0.21;
        return switch (role) {
            case SUPERSHIP -> 0.20;
            case DREADNOUGHT -> 0.22;
            case BATTLESHIP, CARRIER, DRONE_CARRIER -> 0.24;
            case BATTLECRUISER -> 0.27;
            default -> BASE_ROOM_CONDEMNED_THRESHOLD;
        };
    }

    private double catastrophicChainDamageCapFraction() {
        if (role == null) return BASE_CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC;
        if (role.isMothership()) return 0.10;
        if (role.isTitan()) return 0.11;
        return switch (role) {
            case SUPERSHIP -> 0.12;
            case DREADNOUGHT -> 0.14;
            case BATTLESHIP, CARRIER, DRONE_CARRIER -> 0.16;
            case BATTLECRUISER -> 0.18;
            default -> BASE_CATASTROPHIC_CHAIN_DAMAGE_CAP_FRAC;
        };
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
        if (surrendered) {
            preserveSurrenderedHullState();
            return;
        }
        if (totalRoomIntegrityFraction() > roomCondemnedThreshold()) return;
        hp = 0;
        startDeathSequence();
    }

    private boolean allPowerRoomsDestroyed() {
        ensureRoomSystemsInitialized();
        boolean hasOperationalPowerRoom = false;
        for (ShipRoomLayout.RoomId roomId : new ShipRoomLayout.RoomId[]{
                ShipRoomLayout.RoomId.REACTOR,
                ShipRoomLayout.RoomId.POWER_CONDUITS,
                ShipRoomLayout.RoomId.PORT_POWER,
                ShipRoomLayout.RoomId.STARBOARD_POWER
        }) {
            double max = roomHpMax.getOrDefault(roomId, 0.0);
            if (max <= 1e-6) continue;
            hasOperationalPowerRoom = true;
            if (isRoomOperational(roomId)) {
                return false;
            }
        }
        return hasOperationalPowerRoom;
    }

    public boolean reactorBlackoutActive() {
        if (!alive || dying || hp <= 0) return false;
        ensureRoomSystemsInitialized();
        return isSystemDestroyed(InternalSystem.REACTOR_CORE)
                || !isRoomOperational(ShipRoomLayout.RoomId.REACTOR)
                || allPowerRoomsDestroyed();
    }

    public boolean canUseCombatSystems() {
        if (!alive || dying || hp <= 0) return false;
        if (isTemporarilyDisabled()) return false;
        if (isStasisFieldTrapped()) return false;
        return !reactorBlackoutActive();
    }

    private void enforceRoomSystemAvailability() {
        ensureInternalSystemsInitialized();
        ensureRoomSystemsInitialized();
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role, faction);
        for (InternalSystem system : INTERNAL_SYSTEM_VALUES) {
            boolean hasMappedRoom = false;
            boolean anyOperationalRoom = false;
            boolean criticalMappedRoomFailed = false;
            for (ShipRoomLayout.RoomDef def : defs) {
                if (def.primarySystem != system) continue;
                hasMappedRoom = true;
                boolean operational = isRoomOperational(def.id);
                if (operational) {
                    anyOperationalRoom = true;
                }
                if (def.critical && !operational) criticalMappedRoomFailed = true;
            }
            if (!hasMappedRoom) continue;

            if (criticalMappedRoomFailed || !anyOperationalRoom) {
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
        updateDerivedSystemEffects();
    }

    private boolean isRoomOperational(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return false;
        Double maxValue = roomHpMax.get(roomId);
        if (maxValue == null) return false;
        double max = maxValue;
        if (max <= 1e-6) return false;
        Double hpValue = roomHp.get(roomId);
        double hpv = (hpValue == null) ? max : hpValue;
        double frac = MathUtil.clamp(hpv / max, 0.0, 1.0);
        return frac >= ROOM_OPERATIONAL_THRESHOLD;
    }

    private ShipRoomLayout.RoomDef resolveRoomForImpact(HullGeometry.ImpactSample impact, double hitX, double hitY) {
        if (impact != null && impact.onHull) {
            return ShipRoomLayout.roomForHit(role, faction, impact.normalizedX, impact.normalizedY);
        }
        if (Double.isFinite(hitX) && Double.isFinite(hitY)) {
            HullGeometry.ImpactSample snap = HullGeometry.sampleImpact(this, hitX, hitY, true);
            if (snap != null && snap.onHull) {
                return ShipRoomLayout.roomForHit(role, faction, snap.normalizedX, snap.normalizedY);
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
        ShipRoomLayout.RoomDef directional = resolveDirectionalRoomForImpact(impact, true);
        if (directional != null) return directional;

        ShipRoomLayout.RoomDef room = resolveRoomForImpact(impact, hitX, hitY);
        if (room != null) return room;

        if (impact != null && Double.isFinite(impact.normalizedX) && Double.isFinite(impact.normalizedY)) {
            room = RoomHitResolver.resolve(role, faction, impact.normalizedX, impact.normalizedY);
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
                room = RoomHitResolver.resolve(role, faction, lx, ly);
                if (room != null) return room;
            }
        }

        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(role, faction);
        if (defs != null && !defs.isEmpty()) return defs.get(0);
        return null;
    }

    private ShipRoomLayout.RoomDef resolveDirectionalRoomForImpact(HullGeometry.ImpactSample impact,
                                                                   boolean includeArmor) {
        if (impact == null || !impact.onHull) return null;
        double dirX = impact.normalizedX;
        double dirY = impact.normalizedY;
        double len = Math.hypot(dirX, dirY);
        if (len <= 1e-6) return null;
        dirX /= len;
        dirY /= len;

        ShipRoomLayout.RoomDef best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ShipRoomLayout.RoomDef room : ShipRoomLayout.profileFor(role, faction)) {
            if (room == null || room.id == null) continue;
            if (!includeArmor && ShipRoomLayout.isArmorRoom(room.id)) continue;

            double cx = polygonCentroid(room.xs);
            double cy = polygonCentroid(room.ys);
            double dx = cx - impact.normalizedX;
            double dy = cy - impact.normalizedY;
            double along = cx * dirX + cy * dirY;
            double lateral = Math.abs(-dirY * dx + dirX * dy);
            double distanceSq = dx * dx + dy * dy;
            double score = along * 3.2 - lateral * 2.0 - distanceSq * 0.65;

            if (score > bestScore) {
                bestScore = score;
                best = room;
            }
        }
        return best;
    }

    private double polygonCentroid(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private ShipRoomLayout.RoomDef resolveArmorRoomForImpact(HullGeometry.ImpactSample impact,
                                                             ShipRoomLayout.RoomDef primaryRoom) {
        ensureRoomSystemsInitialized();
        ShipRoomLayout.RoomDef sideArmor = preferredArmorRoomForImpact(impact);
        if (sideArmor != null) {
            return sideArmor;
        }
        if (primaryRoom != null && ShipRoomLayout.isArmorRoom(primaryRoom.id)) {
            ShipRoomLayout.RoomDef primaryDefense = resolveDamageableDefenseRoom(primaryRoom.id);
            if (primaryDefense != null) return primaryDefense;
        }
        ShipRoomLayout.RoomDef directional = resolveDirectionalRoomForImpact(impact, true);
        if (directional != null && ShipRoomLayout.isArmorRoom(directional.id)) {
            ShipRoomLayout.RoomDef directionalDefense = resolveDamageableDefenseRoom(directional.id);
            if (directionalDefense != null) return directionalDefense;
        }
        if (impact == null || !impact.onHull) return null;
        for (double scale : new double[]{1.00, 0.96, 0.92}) {
            ShipRoomLayout.RoomDef candidate = RoomHitResolver.resolve(role, faction,
                    impact.normalizedX * scale,
                    impact.normalizedY * scale);
            if (candidate != null && ShipRoomLayout.isArmorRoom(candidate.id)) {
                ShipRoomLayout.RoomDef candidateDefense = resolveDamageableDefenseRoom(candidate.id);
                if (candidateDefense != null) return candidateDefense;
            }
        }
        return null;
    }

    private ShipRoomLayout.RoomDef resolveDamageableDefenseRoom(ShipRoomLayout.RoomId requestedId) {
        if (requestedId == null) return null;

        if (faction == Faction.TEAM_C) {
            ShipRoomLayout.RoomId stripId = ShipRoomLayout.shieldStripRoomFor(requestedId);
            ShipRoomLayout.RoomDef strip = ShipRoomLayout.roomForId(role, faction, stripId);
            if (strip != null && roomHp.getOrDefault(strip.id, 0.0) > 1e-6) return strip;
            return null;
        }

        ShipRoomLayout.RoomId outerId = ShipRoomLayout.outerArmorRoomFor(requestedId);
        ShipRoomLayout.RoomDef outer = ShipRoomLayout.roomForId(role, faction, outerId);
        if (outer != null && roomHp.getOrDefault(outer.id, 0.0) > 1e-6) return outer;

        if (faction != null && faction.isYellowLineage()) {
            ShipRoomLayout.RoomId innerId = ShipRoomLayout.innerArmorRoomFor(requestedId);
            ShipRoomLayout.RoomDef inner = ShipRoomLayout.roomForId(role, faction, innerId);
            if (inner != null && roomHp.getOrDefault(inner.id, 0.0) > 1e-6) return inner;
        }
        return null;
    }

    private ShipRoomLayout.RoomDef preferredArmorRoomForImpact(HullGeometry.ImpactSample impact) {
        if (impact == null || !impact.onHull) return null;
        ShipRoomLayout.RoomId defenseId;
        double ax = Math.abs(impact.normalizedX);
        double ay = Math.abs(impact.normalizedY);
        if (ax >= ay) {
            defenseId = (impact.normalizedX >= 0.0)
                    ? ShipRoomLayout.RoomId.BOW_ARMOR
                    : ShipRoomLayout.RoomId.AFT_ARMOR;
        } else {
            defenseId = (impact.normalizedY <= 0.0)
                    ? ShipRoomLayout.RoomId.DORSAL_ARMOR
                    : ShipRoomLayout.RoomId.VENTRAL_ARMOR;
        }
        return resolveDamageableDefenseRoom(defenseId);
    }

    private ShipRoomLayout.RoomDef resolveInteriorRoomForImpact(HullGeometry.ImpactSample impact,
                                                                ShipRoomLayout.RoomDef primaryRoom) {
        if (primaryRoom != null && !ShipRoomLayout.isArmorRoom(primaryRoom.id)) return primaryRoom;
        ShipRoomLayout.RoomDef directional = resolveDirectionalRoomForImpact(impact, false);
        if (directional != null && !ShipRoomLayout.isArmorRoom(directional.id)) return directional;
        if (impact != null && impact.onHull) {
            for (double scale : new double[]{0.92, 0.84, 0.76, 0.68, 0.58}) {
                ShipRoomLayout.RoomDef candidate = RoomHitResolver.resolve(role, faction,
                        impact.normalizedX * scale,
                        impact.normalizedY * scale);
                if (candidate != null && !ShipRoomLayout.isArmorRoom(candidate.id)) {
                    return candidate;
                }
            }
        }
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def != null && !ShipRoomLayout.isArmorRoom(def.id)) return def;
        }
        return primaryRoom;
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
        if (hz.damageTickTimer <= 0.0) hz.damageTickTimer = 0.18 + randomUnit() * 0.16;
        if (hz.spreadTimer <= 0.0) hz.spreadTimer = 0.50 + randomUnit() * 0.45;
        if (hz.instabilityTimer <= 0.0) hz.instabilityTimer = 0.40 + randomUnit() * 0.35;
        if (hz.vfxTimer <= 0.0) hz.vfxTimer = 0.06 + randomUnit() * 0.08;
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
        if (activeIntegrityProtectionRoom(room.id) == room.id) {
            // Manual focus, or single-room auto-containment, soaks most of the incoming room damage.
            damage *= 0.15;
        }
        double before = roomHp.getOrDefault(room.id, max);
        int hullBefore = hp;
        int hazardRolls = 0;
        List<String> subsystemTransitions = new ArrayList<>(4);
        noDamageTimerSeconds = 0.0;
        instantRepairConsumed = false;
        boolean armorRoom = ShipRoomLayout.isArmorRoom(room.id);

        // Damage saturation: if this room is already destroyed, spread hit damage
        // evenly across nearby operational rooms.
        if (allowSaturation && !fromHazard && before <= 1e-6) {
            List<ShipRoomLayout.RoomDef> recipients = nearestOperationalRooms(room);
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
            } else {
                logRoomDamage(room.id, normalizedX, normalizedY, damage, false);
                syncHullFromRoomIntegrity();
                evaluateCondemnedStateFromRooms();
                lastRoomDamageResult = new RoomDamageResult(
                        room.id.name(),
                        before,
                        before,
                        0,
                        List.of(),
                        hp - hullBefore,
                        0.0
                );
            }
            return;
        }

        double after = Math.max(0.0, before - damage);
        roomHp.put(room.id, after);
        logRoomDamage(room.id, normalizedX, normalizedY, damage, fromHazard);
        if (ShipRoomLayout.isShieldStripRoom(room.id)) {
            int face = shieldFaceForShieldStrip(room.id);
            if (face >= 0 && before > 1e-6 && after <= 1e-6) {
                shieldFaceRegenLock[face] = Math.max(shieldFaceRegenLock[face], SHIELD_FACE_REGEN_LOCK_SECONDS);
            }
            clampShieldFacesToStructuralCapacity();
        }

        if (!armorRoom && room.primarySystem != null) {
            double sysBefore = systemHealthFraction(room.primarySystem);
            double sysScale = fromHazard ? 0.68 : 0.70;
            damageSystem(room.primarySystem, damage * sysScale);
            double sysAfter = systemHealthFraction(room.primarySystem);
            if (sysBefore > 1e-6 && sysAfter <= 1e-6) {
                subsystemTransitions.add(room.primarySystem.name() + ":offline");
            }
        }

        if ((room.id == ShipRoomLayout.RoomId.POWER_CONDUITS
                || room.id == ShipRoomLayout.RoomId.PORT_POWER
                || room.id == ShipRoomLayout.RoomId.STARBOARD_POWER)
                && !fromHazard) {
            hazardRolls++;
            if (randomUnit() < 0.20) {
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
            if (randomUnit() < 0.08) {
                forceShieldOffline(Math.max(0.8, shieldRebootDelay * 0.55));
                subsystemTransitions.add(InternalSystem.SHIELDS.name() + ":offline");
            }
        }

        if (!armorRoom && !fromHazard) {
            hazardRolls++;
            double fracLost = (before - after) / Math.max(1e-6, max);
            double ignitionChance = 0.10 + fracLost * 0.50;
            if (room.critical) ignitionChance += 0.12;
            if (randomUnit() < MathUtil.clamp(ignitionChance, 0.0, 0.88)) {
                igniteRoomFire(room.id, 0.40 + fracLost * 1.45);
                subsystemTransitions.add("hazard:fire_ignited");
            }
        }

        if (before > 1e-6 && after <= 1e-6) {
            handleDestroyedRoom(room, normalizedX, normalizedY, fromHazard, subsystemTransitions);
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

    private void handleDestroyedRoom(ShipRoomLayout.RoomDef room,
                                     double normalizedX,
                                     double normalizedY,
                                     boolean fromHazard,
                                     List<String> subsystemTransitions) {
        if (room == null || room.id == null) return;
        if (!ShipRoomLayout.isArmorRoom(room.id)) {
            spawnDestroyedRoomExplosion(room, normalizedX, normalizedY,
                    room.critical ? 5 : 4);
        }

        if (ShipRoomLayout.isShieldRoom(room.id) || room.primarySystem == InternalSystem.SHIELDS) {
            forceShieldOffline(Math.max(1.5, shieldRebootDelay * 1.65));
            addSubsystemTransition(subsystemTransitions, InternalSystem.SHIELDS.name() + ":offline");
        }

        if (ShipRoomLayout.isPowerRoom(room.id) && allPowerRoomsDestroyed()) {
            applyReactorBlackoutConsequences(subsystemTransitions);
        }

        if (room.id == ShipRoomLayout.RoomId.MAGAZINES
                || room.id == ShipRoomLayout.RoomId.MISSILE_LAUNCHERS) {
            detonateDestroyedMagazineRoom(room, normalizedX, normalizedY, fromHazard, subsystemTransitions);
        }
    }

    private void applyReactorBlackoutConsequences(List<String> subsystemTransitions) {
        forceShieldOffline(Math.max(2.6, shieldRebootDelay * 2.2));
        emergencyThrustActive = false;
        vx = 0.0;
        vy = 0.0;
        cancelBattlefieldWarp();
        cancelSuperweaponSequence();
        addSubsystemTransition(subsystemTransitions, "reactor:blackout");
        addSubsystemTransition(subsystemTransitions, InternalSystem.SHIELDS.name() + ":offline");
    }

    private void detonateDestroyedMagazineRoom(ShipRoomLayout.RoomDef room,
                                               double normalizedX,
                                               double normalizedY,
                                               boolean fromHazard,
                                               List<String> subsystemTransitions) {
        if (room == null) return;
        addSubsystemTransition(subsystemTransitions, "magazines:detonated");
        spawnDestroyedRoomExplosion(room, normalizedX, normalizedY, 6);

        if (room.neighbors == null || room.neighbors.length == 0) return;
        for (ShipRoomLayout.RoomId neighborId : room.neighbors) {
            if (neighborId == null) continue;
            ShipRoomLayout.RoomDef neighbor = ShipRoomLayout.roomForId(role, faction, neighborId);
            if (neighbor == null || neighbor.id == null) continue;
            double nMax = roomHpMax.getOrDefault(neighbor.id, 0.0);
            if (nMax <= 1e-6) continue;
            double nBefore = roomHp.getOrDefault(neighbor.id, nMax);
            if (nBefore <= 1e-6) continue;

            double blastDamage = Math.max(nMax * 1.35, hpMax * 0.12);
            damageRoom(neighbor, blastDamage, normalizedX, normalizedY, fromHazard, false);
            absorbRoomDamageTransitions(lastRoomDamageResult, subsystemTransitions);
            igniteRoomFire(neighbor.id, 0.85 + randomUnit() * 0.70);
        }
    }

    private void absorbRoomDamageTransitions(RoomDamageResult result, List<String> subsystemTransitions) {
        if (result == null || result == RoomDamageResult.NONE || subsystemTransitions == null) return;
        for (String transition : result.subsystemTransitions) {
            addSubsystemTransition(subsystemTransitions, transition);
        }
    }

    private void addSubsystemTransition(List<String> subsystemTransitions, String transition) {
        if (subsystemTransitions == null || transition == null || transition.isBlank()) return;
        if (!subsystemTransitions.contains(transition)) {
            subsystemTransitions.add(transition);
        }
    }

    private void spawnDestroyedRoomExplosion(ShipRoomLayout.RoomDef room,
                                             double normalizedX,
                                             double normalizedY,
                                             int strength) {
        if (room == null) return;
        double[] localPoint = destroyedRoomExplosionLocalPoint(room, normalizedX, normalizedY);
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        double wx = x + localPoint[0] * ca - localPoint[1] * sa;
        double wy = y + localPoint[0] * sa + localPoint[1] * ca;

        double dirLocalX = localPoint[0];
        double dirLocalY = localPoint[1];
        double dirLen = Math.hypot(dirLocalX, dirLocalY);
        double dirWorldX;
        double dirWorldY;
        if (dirLen <= 1e-6) {
            dirWorldX = Math.cos(angle);
            dirWorldY = Math.sin(angle);
        } else {
            dirLocalX /= dirLen;
            dirLocalY /= dirLen;
            dirWorldX = dirLocalX * ca - dirLocalY * sa;
            dirWorldY = dirLocalX * sa + dirLocalY * ca;
        }

        VFX.spawnHullImpact(wx, wy, dirWorldX, dirWorldY,
                Math.max(2, strength), VFX.ImpactStyle.EXPLOSIVE);
    }

    private double[] destroyedRoomExplosionLocalPoint(ShipRoomLayout.RoomDef room,
                                                      double normalizedX,
                                                      double normalizedY) {
        if (room == null || room.xs == null || room.ys == null
                || room.xs.length == 0 || room.ys.length == 0) {
            double fallbackX = Double.isFinite(normalizedX) ? normalizedX : 0.55;
            double fallbackY = Double.isFinite(normalizedY) ? normalizedY : 0.0;
            return new double[]{fallbackX * radius * 0.78, fallbackY * radius * 0.78};
        }

        double cx = polygonCentroid(room.xs);
        double cy = polygonCentroid(room.ys);
        double dirX = Double.isFinite(normalizedX) ? normalizedX : cx;
        double dirY = Double.isFinite(normalizedY) ? normalizedY : cy;
        double dirLen = Math.hypot(dirX, dirY);
        if (dirLen <= 1e-6) {
            dirX = 1.0;
            dirY = 0.0;
        } else {
            dirX /= dirLen;
            dirY /= dirLen;
        }

        double edgeX = cx;
        double edgeY = cy;
        double bestDot = Double.NEGATIVE_INFINITY;
        int points = Math.min(room.xs.length, room.ys.length);
        for (int i = 0; i < points; i++) {
            double px = room.xs[i];
            double py = room.ys[i];
            double dot = px * dirX + py * dirY;
            if (dot > bestDot) {
                bestDot = dot;
                edgeX = px;
                edgeY = py;
            }
        }

        double blend = ShipRoomLayout.isArmorRoom(room.id) ? 0.82 : 0.66;
        double localNormX = cx * (1.0 - blend) + edgeX * blend;
        double localNormY = cy * (1.0 - blend) + edgeY * blend;
        if (!ShipRoomLayout.isArmorRoom(room.id)) {
            localNormX = localNormX * 0.62 + dirX * 0.38;
            localNormY = localNormY * 0.62 + dirY * 0.38;
        }
        localNormX = MathUtil.clamp(localNormX, -1.0, 1.0);
        localNormY = MathUtil.clamp(localNormY, -1.0, 1.0);
        return new double[]{localNormX * radius, localNormY * radius};
    }

    private List<ShipRoomLayout.RoomDef> nearestOperationalRooms(ShipRoomLayout.RoomDef origin) {
        List<ShipRoomLayout.RoomDef> out = new ArrayList<>();
        if (origin == null || origin.id == null) return out;

        java.util.ArrayDeque<ShipRoomLayout.RoomId> queue = new java.util.ArrayDeque<>();
        java.util.EnumSet<ShipRoomLayout.RoomId> visited = java.util.EnumSet.noneOf(ShipRoomLayout.RoomId.class);
        queue.add(origin.id);
        visited.add(origin.id);

        while (!queue.isEmpty()) {
            int levelCount = queue.size();
            List<ShipRoomLayout.RoomDef> levelRecipients = new ArrayList<>();
            for (int i = 0; i < levelCount; i++) {
                ShipRoomLayout.RoomId currentId = queue.removeFirst();
                ShipRoomLayout.RoomDef current = ShipRoomLayout.roomForId(role, faction, currentId);
                if (current == null || current.neighbors == null) continue;
                for (ShipRoomLayout.RoomId neighborId : current.neighbors) {
                    if (neighborId == null || visited.contains(neighborId)) continue;
                    visited.add(neighborId);
                    ShipRoomLayout.RoomDef neighbor = ShipRoomLayout.roomForId(role, faction, neighborId);
                    if (neighbor == null) continue;
                    if (roomHp.getOrDefault(neighbor.id, 0.0) > 1e-6) {
                        levelRecipients.add(neighbor);
                    } else {
                        queue.addLast(neighborId);
                    }
                }
            }
            if (!levelRecipients.isEmpty()) {
                out.addAll(levelRecipients);
                break;
            }
        }
        return out;
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

            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, faction, hz.roomId);
            boolean integrityContained = activeIntegrityProtectionRoom(hz.roomId) == hz.roomId;
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
            if (integrityContained) {
                hz.suppressionBoost = Math.max(hz.suppressionBoost, 0.65 + engineeringPowerMultiplier() * 0.25);
                hz.spreadTimer = Math.max(hz.spreadTimer, 0.95);
            }

            if (!integrityContained && def != null && def.neighbors.length > 0) {
                for (ShipRoomLayout.RoomId nid : def.neighbors) {
                    if (nid == null) continue;
                    if (ShipRoomLayout.isArmorRoom(nid)) continue;
                    ShipRoomLayout.RoomDef neighbor = ShipRoomLayout.roomForId(role, faction, nid);
                    if (neighbor == null) continue;
                    double nMax = roomHpMax.getOrDefault(nid, 0.0);
                    if (nMax <= 1e-9) continue;
                    double nCur = roomHp.getOrDefault(nid, nMax);
                    if (nCur <= 1e-6) continue;
                    damageRoom(neighbor, nMax * 0.01 * dt, Double.NaN, Double.NaN, true);
                }
            }

            hz.damageTickTimer -= dt;
            if (hz.damageTickTimer <= 0.0) {
                if (def != null) {
                    double roomDmg = Math.max(0.8, hz.fireIntensity * (1.6 + randomUnit() * 1.8));
                    if (def.critical) roomDmg *= 1.24;
                    roomDmg *= (1.0 - Math.min(0.45, hz.suppressionBoost * 0.20));
                    damageRoom(def, roomDmg, Double.NaN, Double.NaN, true);
                    applyHazardSubsystemInstability(def, hz);
                }
                hz.damageTickTimer = 0.34 + randomUnit() * 0.36 + Math.min(0.24, hz.suppressionBoost * 0.12);
            }

            hz.instabilityTimer -= dt;
            if (hz.instabilityTimer <= 0.0 && hz.fireIntensity > 0.55) {
                if (def != null) applyHazardSubsystemInstability(def, hz);
                hz.instabilityTimer = 0.48 + randomUnit() * 0.52;
            }

            hz.spreadTimer -= dt;
            if (hz.spreadTimer <= 0.0) {
                if (!integrityContained) {
                    double spreadChance = MathUtil.clamp(
                            0.10 + hz.fireIntensity * 0.26 + roomFuelFactor(hz.roomId) * 0.14 - hz.suppressionBoost * 0.30,
                            0.0,
                            0.90
                    );
                    if (def != null && def.neighbors.length > 0 && randomUnit() < spreadChance) {
                        java.util.ArrayList<ShipRoomLayout.RoomId> eligible = new java.util.ArrayList<>();
                        for (ShipRoomLayout.RoomId nid : def.neighbors) {
                            if (nid == null) continue;
                            if (ShipRoomLayout.isArmorRoom(nid)) continue;
                            double nMax = roomHpMax.getOrDefault(nid, 0.0);
                            if (nMax <= 1e-9) continue;
                            double nCur = roomHp.getOrDefault(nid, nMax);
                            double frac = nCur / nMax;
                            RoomHazardState nHz = roomHazards.get(nid);
                            if (nHz != null && nHz.fireIntensity > 1.10) continue;
                            if (frac <= 0.85) {
                                eligible.add(nid);
                            }
                        }
                        if (!eligible.isEmpty()) {
                            int idx = (int) Math.floor(randomUnit() * eligible.size());
                            if (idx < 0 || idx >= eligible.size()) idx = 0;
                            double spreadIntensity = MathUtil.clamp(
                                    0.30 + hz.fireIntensity * (0.42 + randomUnit() * 0.30),
                                    0.20,
                                    2.2
                            );
                            igniteRoomFire(eligible.get(idx), spreadIntensity);
                        }
                    }
                }
                hz.spreadTimer = 0.56 + randomUnit() * 0.62 + Math.min(0.20, hz.suppressionBoost * 0.10);
            }

            hz.vfxTimer -= dt;
            if (hz.vfxTimer <= 0.0) {
                spawnRoomFireVfx(def, hz.fireIntensity);
                hz.vfxTimer = Math.max(0.04, 0.20 + randomUnit() * 0.16 - hz.fireIntensity * 0.05);
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
        if (randomUnit() < 0.32) {
            Double warpMax = systemHpMax.get(InternalSystem.WARP_ENGINES);
            if (warpMax != null && warpMax > 1e-6) {
                damageSystem(InternalSystem.WARP_ENGINES, warpMax * 0.05);
            }
        }
        if (randomUnit() < 0.18) {
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

    public double superweaponRechargeRateMultiplier() {
        double alloc = powerBusFraction(PowerBus.AUXILIARY);
        double target = Math.max(0.04, powerBusNominalTarget(PowerBus.AUXILIARY));
        double ratio = alloc / target;
        double scaled;
        if (ratio >= 1.0) {
            scaled = 1.0 + 1.45 * Math.tanh((ratio - 1.0) * 1.65);
        } else {
            scaled = SUPERWEAPON_RECHARGE_MIN_MULT
                    + (1.0 - SUPERWEAPON_RECHARGE_MIN_MULT) * Math.pow(Math.max(0.0, ratio), 1.15);
        }
        double infrastructure = MathUtil.clamp(powerBusEffectiveMultiplier(PowerBus.AUXILIARY) / Math.max(0.12, ratio), 0.72, 1.22);
        double identity = identityStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPERWEAPON_RECHARGE);
        return MathUtil.clamp(scaled * infrastructure * identity, SUPERWEAPON_RECHARGE_MIN_MULT, SUPERWEAPON_RECHARGE_MAX_MULT);
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
                repairRoomDisruptions(dt);
                ensureInternalSystemsInitialized();
                repairDamagedSystems((0.40 + 0.42 * crewReadiness) * dt);
                healHull((0.08 + 0.12 * crewReadiness) * dt);
            }
            default -> {
                // Balanced
            }
        }

        updateCrewTeams(dt);

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

    private void ensureCrewTeamsInitialized() {
        ensureRoomSystemsInitialized();
        if (!crewTeams.isEmpty()) return;
        ShipRoomLayout.RoomId start = ShipRoomLayout.roomForId(role, faction, ShipRoomLayout.RoomId.CREW_QUARTERS) != null
                ? ShipRoomLayout.RoomId.CREW_QUARTERS
                : (ShipRoomLayout.roomForId(role, faction, ShipRoomLayout.RoomId.BRIDGE) != null
                ? ShipRoomLayout.RoomId.BRIDGE
                : firstCrewAccessibleRoom());
        if (start == null) return;
        crewTeams.add(new CrewTeam(1, CrewTeamRole.DAMAGE_CONTROL, start));
        crewTeams.add(new CrewTeam(2, CrewTeamRole.ENGINEERING, start));
        crewTeams.add(new CrewTeam(3, CrewTeamRole.FIRE_SUPPRESSION, start));
    }

    private ShipRoomLayout.RoomId firstCrewAccessibleRoom() {
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def != null && def.id != null && !ShipRoomLayout.isArmorRoom(def.id)) return def.id;
        }
        return null;
    }

    private void updateCrewTeams(double dt) {
        if (dt <= 0.0 || !alive || dying) return;
        ensureCrewTeamsInitialized();
        if (crewTeams.isEmpty()) return;

        java.util.EnumSet<ShipRoomLayout.RoomId> claimed = java.util.EnumSet.noneOf(ShipRoomLayout.RoomId.class);
        for (CrewTeam team : crewTeams) {
            if (team == null) continue;
            ShipRoomLayout.RoomId target = selectCrewTeamTarget(team, claimed);
            assignCrewTeamTarget(team, target);
            if (target != null) claimed.add(target);
            advanceCrewTeam(team, dt);
            performCrewTeamWork(team, dt);
        }
    }

    private void retaskCrewTeams() {
        for (CrewTeam team : crewTeams) {
            if (team == null) continue;
            team.targetRoom = null;
            team.path.clear();
            team.pathIndex = 0;
            team.moveProgress = 0.0;
        }
    }

    private ShipRoomLayout.RoomId selectCrewTeamTarget(CrewTeam team, java.util.EnumSet<ShipRoomLayout.RoomId> claimed) {
        CrewPriority priority = crewPriority();
        if (priority == CrewPriority.MANUAL_ROOM && crewManualPriorityRoom != null
                && crewRoomNeedsWork(crewManualPriorityRoom)) return crewManualPriorityRoom;

        if (priority == CrewPriority.FIRE_SUPPRESSION || team.role == CrewTeamRole.FIRE_SUPPRESSION) {
            ShipRoomLayout.RoomId fire = bestFireCrewTarget(claimed);
            if (fire != null) return fire;
        }

        ShipRoomLayout.RoomId systemTarget = switch (priority) {
            case REACTOR -> bestCrewTargetForRooms(claimed, ShipRoomLayout.RoomId.REACTOR, ShipRoomLayout.RoomId.POWER_CONDUITS);
            case ENGINES -> bestCrewTargetForRooms(claimed, ShipRoomLayout.RoomId.ENGINES, ShipRoomLayout.RoomId.PORT_ENGINES,
                    ShipRoomLayout.RoomId.STARBOARD_ENGINES, ShipRoomLayout.RoomId.WARP_DRIVE);
            case WEAPONS -> bestCrewTargetForRooms(claimed, ShipRoomLayout.RoomId.MAIN_WEAPON, ShipRoomLayout.RoomId.PORT_BATTERY,
                    ShipRoomLayout.RoomId.STARBOARD_BATTERY, ShipRoomLayout.RoomId.MISSILE_LAUNCHERS, ShipRoomLayout.RoomId.MAGAZINES);
            case SHIELDS -> bestCrewTargetForRooms(claimed, ShipRoomLayout.RoomId.INTEGRITY_FIELD,
                    ShipRoomLayout.RoomId.BOW_SHIELD_STRIP, ShipRoomLayout.RoomId.DORSAL_SHIELD_STRIP, ShipRoomLayout.RoomId.VENTRAL_SHIELD_STRIP,
                    ShipRoomLayout.RoomId.AFT_SHIELD_STRIP);
            case SENSORS -> bestCrewTargetForRooms(claimed, ShipRoomLayout.RoomId.SENSORS, ShipRoomLayout.RoomId.BRIDGE);
            case BATTLE_STATIONS -> bestCrewTargetForRooms(claimed, ShipRoomLayout.RoomId.BRIDGE,
                    ShipRoomLayout.RoomId.MAIN_WEAPON, ShipRoomLayout.RoomId.PORT_BATTERY, ShipRoomLayout.RoomId.STARBOARD_BATTERY);
            default -> null;
        };
        if (systemTarget != null) return systemTarget;

        ShipRoomLayout.RoomId disrupted = bestDisruptedCrewTarget(claimed);
        if (disrupted != null) return disrupted;
        ShipRoomLayout.RoomId damaged = bestDamagedCrewTarget(claimed);
        if (damaged != null) return damaged;
        return bestOperatingStationForTeam(team);
    }

    private boolean crewRoomNeedsWork(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return false;
        return roomFireIntensity(roomId) > 0.03
                || isRoomDisrupted(roomId)
                || roomHealthFraction(roomId) < 0.995;
    }

    private ShipRoomLayout.RoomId bestFireCrewTarget(java.util.EnumSet<ShipRoomLayout.RoomId> claimed) {
        ShipRoomLayout.RoomId best = null;
        double bestScore = 0.05;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null || claimed.contains(def.id)) continue;
            double fire = roomFireIntensity(def.id);
            if (fire > bestScore) {
                bestScore = fire;
                best = def.id;
            }
        }
        return best;
    }

    private ShipRoomLayout.RoomId bestDisruptedCrewTarget(java.util.EnumSet<ShipRoomLayout.RoomId> claimed) {
        ShipRoomLayout.RoomId best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null || claimed.contains(def.id) || !isRoomDisrupted(def.id)) continue;
            double score = (def.critical ? 4.0 : 0.0) + (def.primarySystem != null ? 2.0 : 0.0)
                    + roomDisruptionRepairProgress(def.id) + (1.0 - roomHealthFraction(def.id));
            if (score > bestScore) {
                bestScore = score;
                best = def.id;
            }
        }
        return best;
    }

    private ShipRoomLayout.RoomId bestDamagedCrewTarget(java.util.EnumSet<ShipRoomLayout.RoomId> claimed) {
        ShipRoomLayout.RoomId best = null;
        double bestScore = 0.006;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null || claimed.contains(def.id)) continue;
            if (ShipRoomLayout.isArmorRoom(def.id)) continue;
            double missing = 1.0 - roomHealthFraction(def.id);
            double score = missing + (def.critical ? 0.20 : 0.0) + (def.primarySystem != null ? 0.10 : 0.0);
            if (missing > 0.005 && score > bestScore) {
                bestScore = score;
                best = def.id;
            }
        }
        return best;
    }

    private ShipRoomLayout.RoomId bestCrewTargetForRooms(java.util.EnumSet<ShipRoomLayout.RoomId> claimed,
                                                         ShipRoomLayout.RoomId... rooms) {
        ShipRoomLayout.RoomId best = null;
        double bestScore = 0.0;
        if (rooms == null) return null;
        for (ShipRoomLayout.RoomId roomId : rooms) {
            if (roomId == null || claimed.contains(roomId)) continue;
            if (ShipRoomLayout.roomForId(role, faction, roomId) == null) continue;
            double score = (1.0 - roomHealthFraction(roomId)) * 2.0
                    + roomFireIntensity(roomId) * 2.5
                    + (isRoomDisrupted(roomId) ? 1.25 + roomDisruptionRepairProgress(roomId) : 0.0);
            if (score > bestScore + 1e-6) {
                bestScore = score;
                best = roomId;
            }
        }
        return best;
    }

    private ShipRoomLayout.RoomId bestOperatingStationForTeam(CrewTeam team) {
        if (team == null) return firstCrewAccessibleRoom();
        ShipRoomLayout.RoomId preferred = switch (team.role) {
            case ENGINEERING -> ShipRoomLayout.RoomId.REACTOR;
            case FIRE_SUPPRESSION -> ShipRoomLayout.RoomId.SERVICE_BAY;
            case DAMAGE_CONTROL -> ShipRoomLayout.RoomId.CREW_QUARTERS;
        };
        if (ShipRoomLayout.roomForId(role, faction, preferred) != null) return preferred;
        return firstCrewAccessibleRoom();
    }

    private void assignCrewTeamTarget(CrewTeam team, ShipRoomLayout.RoomId target) {
        if (team == null) return;
        if (target == null) target = team.currentRoom;
        if (target == team.targetRoom && !team.path.isEmpty()) return;
        team.targetRoom = target;
        team.path.clear();
        team.path.addAll(crewPath(team.currentRoom, target));
        team.pathIndex = 0;
        team.moveProgress = 0.0;
        team.task = (team.currentRoom == target) ? CrewTeamTask.IDLE : CrewTeamTask.MOVING;
    }

    private List<ShipRoomLayout.RoomId> crewPath(ShipRoomLayout.RoomId from, ShipRoomLayout.RoomId to) {
        if (from == null || to == null) return List.of();
        if (from == to) return List.of(from);
        java.util.ArrayDeque<ShipRoomLayout.RoomId> queue = new java.util.ArrayDeque<>();
        java.util.EnumMap<ShipRoomLayout.RoomId, ShipRoomLayout.RoomId> prev =
                new java.util.EnumMap<>(ShipRoomLayout.RoomId.class);
        queue.add(from);
        prev.put(from, from);
        while (!queue.isEmpty()) {
            ShipRoomLayout.RoomId current = queue.removeFirst();
            if (current == to) break;
            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, faction, current);
            if (def == null || def.neighbors == null) continue;
            for (ShipRoomLayout.RoomId neighbor : def.neighbors) {
                if (neighbor == null || prev.containsKey(neighbor)) continue;
                if (ShipRoomLayout.roomForId(role, faction, neighbor) == null) continue;
                prev.put(neighbor, current);
                queue.addLast(neighbor);
            }
        }
        if (!prev.containsKey(to)) return List.of(from, to);
        ArrayList<ShipRoomLayout.RoomId> out = new ArrayList<>();
        ShipRoomLayout.RoomId step = to;
        while (step != null && step != from) {
            out.add(0, step);
            step = prev.get(step);
        }
        out.add(0, from);
        return out;
    }

    private void advanceCrewTeam(CrewTeam team, double dt) {
        if (team == null || team.currentRoom == null || team.targetRoom == null) return;
        if (team.currentRoom == team.targetRoom) return;
        if (team.path.isEmpty() || team.pathIndex >= team.path.size() - 1) {
            team.path.clear();
            team.path.addAll(crewPath(team.currentRoom, team.targetRoom));
            team.pathIndex = 0;
        }
        ShipRoomLayout.RoomId next = team.nextRoom();
        if (next == null) return;
        team.task = CrewTeamTask.MOVING;
        double roomsPerSecond = 0.72 + 0.28 * crewReadiness();
        team.moveProgress += dt * roomsPerSecond;
        while (team.moveProgress >= 1.0 && next != null) {
            team.moveProgress -= 1.0;
            team.currentRoom = next;
            team.pathIndex++;
            if (team.currentRoom == team.targetRoom) {
                team.moveProgress = 0.0;
                break;
            }
            next = team.nextRoom();
        }
    }

    private void performCrewTeamWork(CrewTeam team, double dt) {
        if (team == null || team.currentRoom == null || team.currentRoom != team.targetRoom) return;
        ShipRoomLayout.RoomId roomId = team.currentRoom;
        double readiness = 0.72 + 0.56 * crewReadiness();
        if (roomFireIntensity(roomId) > 0.03) {
            team.task = CrewTeamTask.FIREFIGHTING;
            applyFireSuppression(roomId, dt * (0.17 + (team.role == CrewTeamRole.FIRE_SUPPRESSION ? 0.13 : 0.06)) * readiness, true);
            return;
        }
        if (isRoomDisrupted(roomId)) {
            team.task = CrewTeamTask.RESTORING_SYSTEM;
            repairRoomDisruption(roomId, dt * (team.role == CrewTeamRole.ENGINEERING ? 1.35 : 1.0) * readiness);
            return;
        }
        if (roomHealthFraction(roomId) < 0.995) {
            team.task = CrewTeamTask.REPAIRING;
            repairRoomIntegrity(roomId, dt * (team.role == CrewTeamRole.DAMAGE_CONTROL ? 0.034 : 0.023) * readiness);
            return;
        }
        team.task = CrewTeamTask.OPERATING;
    }

    private void repairRoomDisruption(ShipRoomLayout.RoomId roomId, double workSeconds) {
        if (roomId == null || workSeconds <= 0.0) return;
        ensureRoomSystemsInitialized();
        Double progress = roomDisruptionRepairProgress.get(roomId);
        if (progress == null) return;
        double next = progress + workSeconds / ROOM_DISRUPTION_REPAIR_SECONDS;
        if (next >= 1.0 - 1e-6) roomDisruptionRepairProgress.remove(roomId);
        else roomDisruptionRepairProgress.put(roomId, next);
    }

    private void repairRoomIntegrity(ShipRoomLayout.RoomId roomId, double healFrac) {
        if (roomId == null || healFrac <= 0.0) return;
        ensureRoomSystemsInitialized();
        Double maxValue = roomHpMax.get(roomId);
        if (maxValue == null || maxValue <= 1e-9) return;
        double cur = roomHp.getOrDefault(roomId, maxValue);
        if (cur >= maxValue - 1e-9) return;
        roomHp.put(roomId, Math.min(maxValue, cur + maxValue * healFrac));
        enforceRoomSystemAvailability();
        syncHullFromRoomIntegrity();
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

    private void repairRoomDisruptions(double dt) {
        if (dt <= 0.0) return;
        ensureRoomSystemsInitialized();
        ShipRoomLayout.RoomId target = selectRoomDisruptionRepairTarget();
        if (target == null) return;
        double progress = roomDisruptionRepairProgress.getOrDefault(target, 0.0) + dt / ROOM_DISRUPTION_REPAIR_SECONDS;
        if (progress >= 1.0 - 1e-6) {
            roomDisruptionRepairProgress.remove(target);
        } else {
            roomDisruptionRepairProgress.put(target, progress);
        }
    }

    private ShipRoomLayout.RoomId selectRoomDisruptionRepairTarget() {
        ensureRoomSystemsInitialized();
        if (roomDisruptionRepairProgress.isEmpty()) return null;

        ShipRoomLayout.RoomId target = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            Double progress = roomDisruptionRepairProgress.get(def.id);
            if (progress == null) continue;
            double score = 0.0;
            if (def.critical) score += 4.0;
            if (def.primarySystem != null) score += 2.0;
            score += progress * 1.5;
            score += (1.0 - roomHealthFraction(def.id)) * 0.8;
            if (score > bestScore) {
                bestScore = score;
                target = def.id;
            }
        }
        return target;
    }

    private double roomDisruptionSystemMultiplier() {
        double frac = roomDisruptionFraction();
        if (frac <= 1e-6) return 1.0;
        double severity = Math.pow(frac, ROOM_DISRUPTION_CURVE_EXPONENT);
        double mul = 1.0 - (1.0 - ROOM_DISRUPTION_FULL_SHIP_MULTIPLIER) * severity;
        return MathUtil.clamp(mul, ROOM_DISRUPTION_FULL_SHIP_MULTIPLIER, 1.0);
    }

    public int applyShipwideRoomDisruption() {
        ensureRoomSystemsInitialized();
        int applied = 0;
        for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
            if (def == null || def.id == null) continue;
            if (roomHpMax.getOrDefault(def.id, 0.0) <= 1e-6) continue;
            roomDisruptionRepairProgress.put(def.id, 0.0);
            applied++;
        }
        return applied;
    }

    public int applyRoomDisruption(double hitX, double hitY, double impactVx, double impactVy, int roomCount) {
        if (roomCount <= 0) return 0;
        ensureRoomSystemsInitialized();

        HullGeometry.ImpactSample impact = resolveHullImpactSample(hitX, hitY, impactVx, impactVy);
        ShipRoomLayout.RoomDef origin = resolvePrimaryRoomForHullHit(impact, hitX, hitY, impactVx, impactVy);
        if (origin == null) {
            for (ShipRoomLayout.RoomDef def : ShipRoomLayout.profileFor(role, faction)) {
                if (def == null || def.id == null) continue;
                if (def.primarySystem != null || def.critical) {
                    origin = def;
                    break;
                }
            }
        }
        if (origin == null || origin.id == null) return 0;

        java.util.LinkedHashSet<ShipRoomLayout.RoomId> targets = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<ShipRoomLayout.RoomId> queue = new java.util.ArrayDeque<>();
        queue.add(origin.id);
        while (!queue.isEmpty() && targets.size() < roomCount) {
            ShipRoomLayout.RoomId current = queue.removeFirst();
            if (current == null || targets.contains(current)) continue;
            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, faction, current);
            if (def == null) continue;
            boolean useful = def.primarySystem != null || def.critical;
            if (useful && roomHpMax.getOrDefault(current, 0.0) > 1e-6) {
                targets.add(current);
            }
            for (ShipRoomLayout.RoomId neighbor : def.neighbors) {
                if (neighbor != null && !targets.contains(neighbor)) queue.addLast(neighbor);
            }
        }

        int applied = 0;
        for (ShipRoomLayout.RoomId roomId : targets) {
            roomDisruptionRepairProgress.put(roomId, 0.0);
            applied++;
        }
        return applied;
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
        if (randomUnit() > chance) return;

        InternalSystem primary = (room.primarySystem != null) ? room.primarySystem : secondaryInstabilitySystem(room.id);
        if (primary != null) {
            double max = systemHpMax.getOrDefault(primary, 0.0);
            if (max > 1e-6) {
                damageSystem(primary, max * (0.007 + hz.fireIntensity * 0.012));
            }
        }

        InternalSystem secondary = secondaryInstabilitySystem(room.id);
        if (secondary != null && secondary != primary && randomUnit() < 0.45) {
            double max = systemHpMax.getOrDefault(secondary, 0.0);
            if (max > 1e-6) {
                damageSystem(secondary, max * (0.004 + hz.fireIntensity * 0.008));
            }
        }

        if ((room.id == ShipRoomLayout.RoomId.POWER_CONDUITS
                || room.id == ShipRoomLayout.RoomId.PORT_POWER
                || room.id == ShipRoomLayout.RoomId.STARBOARD_POWER)
                && hz.fireIntensity > 1.35
                && randomUnit() < 0.16) {
            forceShieldOffline(Math.max(0.5, shieldRebootDelay * 0.35));
        }
        if ((room.id == ShipRoomLayout.RoomId.MAGAZINES
                || room.id == ShipRoomLayout.RoomId.MISSILE_LAUNCHERS)
                && hz.fireIntensity > 1.10
                && randomUnit() < 0.12) {
            double weaponMax = systemHpMax.getOrDefault(InternalSystem.WEAPONS, 0.0);
            if (weaponMax > 1e-6) {
                damageSystem(InternalSystem.WEAPONS, weaponMax * 0.015);
            }
        }
    }

    private InternalSystem secondaryInstabilitySystem(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return null;
        return switch (roomId) {
            case REACTOR, POWER_CONDUITS, PORT_POWER, STARBOARD_POWER -> InternalSystem.SHIELDS;
            case INTEGRITY_FIELD -> InternalSystem.REACTOR_CORE;
            case SENSORS, BRIDGE, BOW, CREW_QUARTERS -> InternalSystem.BRIDGE;
            case PORT_BATTERY, STARBOARD_BATTERY, MAIN_WEAPON, MISSILE_LAUNCHERS, MAGAZINES -> InternalSystem.WEAPONS;
            case SERVICE_BAY -> InternalSystem.REACTOR_CORE;
            case CARGO_BAY -> InternalSystem.MAGAZINES;
            case ENGINES, PORT_ENGINES, STARBOARD_ENGINES, WARP_DRIVE, AFT_SPINE -> InternalSystem.WARP_ENGINES;
            case BOW_ARMOR, DORSAL_ARMOR, VENTRAL_ARMOR, AFT_ARMOR,
                 BOW_ARMOR_INNER, DORSAL_ARMOR_INNER, VENTRAL_ARMOR_INNER, AFT_ARMOR_INNER,
                 BOW_SHIELD_STRIP, DORSAL_SHIELD_STRIP, VENTRAL_SHIELD_STRIP, AFT_SHIELD_STRIP -> null;
        };
    }

    private void spawnRoomFireVfx(ShipRoomLayout.RoomDef room, double intensity) {
        if (intensity <= 0.05) return;
        double localX = (randomUnit() - 0.5) * radius * 0.24;
        double localY = (randomUnit() - 0.5) * radius * 0.24;

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
                localX = (cx + (randomUnit() - 0.5) * jitter) * radius;
                localY = (cy + (randomUnit() - 0.5) * jitter) * radius;
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
        for (InternalSystem s : INTERNAL_SYSTEM_VALUES) {
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
        if (!Double.isFinite(shieldFacingAngle)) shieldFacingAngle = angle;
        if (!shieldActive || shieldMax <= 0.0) return;
        shieldFacingAngle = angle;
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

    private ShipRoomLayout.RoomId shieldStripRoomForFace(int face) {
        return switch (face) {
            case SHIELD_FACE_FORE -> ShipRoomLayout.RoomId.BOW_SHIELD_STRIP;
            case SHIELD_FACE_REAR -> ShipRoomLayout.RoomId.AFT_SHIELD_STRIP;
            case SHIELD_FACE_LEFT -> ShipRoomLayout.RoomId.DORSAL_SHIELD_STRIP;
            case SHIELD_FACE_RIGHT -> ShipRoomLayout.RoomId.VENTRAL_SHIELD_STRIP;
            default -> null;
        };
    }

    private int shieldFaceForShieldStrip(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return -1;
        return switch (roomId) {
            case BOW_SHIELD_STRIP -> SHIELD_FACE_FORE;
            case AFT_SHIELD_STRIP -> SHIELD_FACE_REAR;
            case DORSAL_SHIELD_STRIP -> SHIELD_FACE_LEFT;
            case VENTRAL_SHIELD_STRIP -> SHIELD_FACE_RIGHT;
            default -> -1;
        };
    }

    private double shieldFaceCapacity(int face) {
        double baseCapacity = perFaceShieldMax();
        if (baseCapacity <= 1e-9) return 0.0;
        if (faction != Faction.TEAM_C) return baseCapacity;
        ensureRoomSystemsInitialized();
        ShipRoomLayout.RoomId stripRoom = shieldStripRoomForFace(face);
        if (stripRoom == null) return baseCapacity;
        double max = roomHpMax.getOrDefault(stripRoom, 0.0);
        if (max <= 1e-9) return 0.0;
        double hpv = roomHp.getOrDefault(stripRoom, max);
        double frac = MathUtil.clamp(hpv / max, 0.0, 1.0);
        return baseCapacity * frac;
    }

    private double totalShieldFaceCapacity() {
        double total = 0.0;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) total += shieldFaceCapacity(i);
        return total;
    }

    private int activeTeamCShieldStripCount() {
        if (faction != Faction.TEAM_C) return SHIELD_FACE_COUNT;
        ensureRoomSystemsInitialized();
        int active = 0;
        for (ShipRoomLayout.RoomId roomId : new ShipRoomLayout.RoomId[]{
                ShipRoomLayout.RoomId.BOW_SHIELD_STRIP,
                ShipRoomLayout.RoomId.DORSAL_SHIELD_STRIP,
                ShipRoomLayout.RoomId.VENTRAL_SHIELD_STRIP,
                ShipRoomLayout.RoomId.AFT_SHIELD_STRIP
        }) {
            double max = roomHpMax.getOrDefault(roomId, 0.0);
            if (max <= 1e-9) continue;
            if (roomHp.getOrDefault(roomId, max) > 1e-6) active++;
        }
        return active;
    }

    private double teamCShieldStripRegenMultiplier() {
        if (faction != Faction.TEAM_C) return 1.0;
        return MathUtil.clamp(activeTeamCShieldStripCount() / (double) SHIELD_FACE_COUNT, 0.0, 1.0);
    }

    private void clampShieldFacesToStructuralCapacity() {
        if (!shieldFacesInitialized) return;
        boolean changed = false;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
            double cap = shieldFaceCapacity(i);
            double clamped = MathUtil.clamp(shieldFaces[i], 0.0, cap);
            if (Math.abs(clamped - shieldFaces[i]) > 1e-6) {
                shieldFaces[i] = clamped;
                changed = true;
            }
        }
        if (changed) syncAggregateShieldFromFaces();
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

        double effectiveCap = Math.min(max, totalShieldFaceCapacity());
        shield = MathUtil.clamp(shield, 0.0, effectiveCap);
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) shieldFaces[i] = 0.0;
        double rem = shield;
        for (int pass = 0; pass < 4 && rem > 1e-6; pass++) {
            int open = 0;
            for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
                if (shieldFaceCapacity(i) > shieldFaces[i] + 1e-9) open++;
            }
            if (open <= 0) break;
            double share = rem / open;
            double consumed = 0.0;
            for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
                double cap = shieldFaceCapacity(i);
                double room = cap - shieldFaces[i];
                if (room <= 1e-9) continue;
                double add = Math.min(room, share);
                shieldFaces[i] += add;
                consumed += add;
            }
            rem -= consumed;
            if (consumed <= 1e-9) break;
        }
        shieldFacesSyncedMax = max;
        shieldFacesInitialized = true;
    }

    private void syncAggregateShieldFromFaces() {
        double max = Math.max(0.0, shieldMax);
        double effectiveCap = Math.min(max, totalShieldFaceCapacity());
        double total = 0.0;
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) {
            shieldFaceRegenLock[i] = Math.max(0.0, shieldFaceRegenLock[i]);
            shieldFaces[i] = MathUtil.clamp(shieldFaces[i], 0.0, shieldFaceCapacity(i));
            total += shieldFaces[i];
        }
        shield = MathUtil.clamp(total, 0.0, effectiveCap);
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
        double effectiveCap = effectiveShieldCapacityMax();
        if (effectiveCap <= 1e-9) {
            shield = 0.0;
            syncShieldFacesFromAggregate();
            return;
        }
        shield = MathUtil.clamp(shield + amount, 0.0, effectiveCap);
        syncShieldFacesFromAggregate();
        healShieldImpactMarks(amount);
    }

    private void applyShieldDamage(double amount) {
        if (amount <= 0.0) return;
        double effectiveCap = effectiveShieldCapacityMax();
        shield = MathUtil.clamp(shield - amount, 0.0, Math.max(0.0, effectiveCap));
        syncShieldFacesFromAggregate();
    }

    private double computeShieldPassthroughChance(double shieldFraction) {
        double frac = MathUtil.clamp(shieldFraction, 0.0, 1.0);
        double denom = Math.max(1e-9, SHIELD_PASSTHROUGH_FULL_FRAC - SHIELD_PASSTHROUGH_CRITICAL_FRAC);
        double normalized = MathUtil.clamp((frac - SHIELD_PASSTHROUGH_CRITICAL_FRAC) / denom, 0.0, 1.0);
        double wear = 1.0 - normalized;
        return SHIELD_PASSTHROUGH_MIN_CHANCE
                + (SHIELD_PASSTHROUGH_MAX_CHANCE - SHIELD_PASSTHROUGH_MIN_CHANCE) * wear;
    }

    private void registerShieldImpact(double absorbedDamage, HullGeometry.ImpactSample impact) {
        if (absorbedDamage <= 1e-6 || impact == null || !impact.onHull) return;
        double effectiveCap = Math.max(1e-9, effectiveShieldCapacityMax());
        double severity = MathUtil.clamp(absorbedDamage / effectiveCap * 3.8, 0.10, 0.70);
        double patchRadius = Math.max(radius * (0.18 + severity * 0.34), 8.0);
        double nx = impact.normalizedX;
        double ny = impact.normalizedY;
        double nLen = Math.hypot(nx, ny);
        if (nLen <= 1e-6) {
            nx = 1.0;
            ny = 0.0;
        } else {
            nx /= nLen;
            ny /= nLen;
        }

        ShieldImpactMark best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        double mergeRadius = Math.max(10.0, patchRadius * 0.75);
        double mergeRadius2 = mergeRadius * mergeRadius;
        for (ShieldImpactMark mark : shieldImpactMarks) {
            double dx = mark.localX - impact.localX;
            double dy = mark.localY - impact.localY;
            double d2 = dx * dx + dy * dy;
            if (d2 <= mergeRadius2 && d2 < bestDist2) {
                best = mark;
                bestDist2 = d2;
            }
        }

        if (best != null) {
            double blend = MathUtil.clamp(0.28 + severity * 0.42, 0.20, 0.72);
            best.localX = best.localX * (1.0 - blend) + impact.localX * blend;
            best.localY = best.localY * (1.0 - blend) + impact.localY * blend;
            best.normalX = best.normalX * (1.0 - blend) + nx * blend;
            best.normalY = best.normalY * (1.0 - blend) + ny * blend;
            double len = Math.hypot(best.normalX, best.normalY);
            if (len > 1e-6) {
                best.normalX /= len;
                best.normalY /= len;
            }
            best.severity = MathUtil.clamp(best.severity + severity * 0.72, 0.08, 1.8);
            best.patchRadius = Math.max(best.patchRadius, patchRadius);
            best.freshness = 1.0;
            return;
        }

        if (shieldImpactMarks.size() >= MAX_SHIELD_IMPACT_MARKS) {
            int weakest = 0;
            double weakestScore = Double.POSITIVE_INFINITY;
            for (int i = 0; i < shieldImpactMarks.size(); i++) {
                ShieldImpactMark mark = shieldImpactMarks.get(i);
                double score = mark.severity * 0.7 + mark.freshness * 0.3;
                if (score < weakestScore) {
                    weakestScore = score;
                    weakest = i;
                }
            }
            shieldImpactMarks.remove(weakest);
        }
        shieldImpactMarks.add(new ShieldImpactMark(impact.localX, impact.localY, nx, ny, severity, 1.0, patchRadius));
    }

    private void healShieldImpactMarks(double amount) {
        if (amount <= 1e-6 || shieldImpactMarks.isEmpty()) return;
        double effectiveCap = Math.max(1e-9, effectiveShieldCapacityMax());
        double recover = amount / effectiveCap;
        if (recover <= 1e-6) return;
        for (int i = shieldImpactMarks.size() - 1; i >= 0; i--) {
            ShieldImpactMark mark = shieldImpactMarks.get(i);
            mark.severity = Math.max(0.0, mark.severity - recover * 1.05);
            mark.patchRadius = Math.max(radius * 0.12, mark.patchRadius - radius * recover * 0.45);
            mark.freshness = Math.max(0.0, mark.freshness - recover * 1.6);
            if (mark.severity <= 0.035 && mark.freshness <= 0.04) {
                shieldImpactMarks.remove(i);
            }
        }
    }

    private void updateShieldImpactDecay(double dt) {
        if (dt <= 0.0 || shieldImpactMarks.isEmpty()) return;
        for (int i = shieldImpactMarks.size() - 1; i >= 0; i--) {
            ShieldImpactMark mark = shieldImpactMarks.get(i);
            mark.freshness = Math.max(0.0, mark.freshness - dt * 0.34);
            if (mark.severity <= 0.035 && mark.freshness <= 0.04) {
                shieldImpactMarks.remove(i);
            }
        }
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

    private int armorGateFaceForImpact(HullGeometry.ImpactSample impact,
                                       ShipRoomLayout.RoomDef primaryRoom) {
        ShipRoomLayout.RoomDef preferredArmor = preferredArmorRoomForImpact(impact);
        if (preferredArmor != null) return faceForArmorRoom(preferredArmor.id);
        if (primaryRoom != null) return faceForArmorRoom(primaryRoom.id);
        return SHIELD_FACE_FORE;
    }

    private int faceForArmorRoom(ShipRoomLayout.RoomId roomId) {
        if (roomId == null) return SHIELD_FACE_FORE;
        ShipRoomLayout.RoomId outer = ShipRoomLayout.outerArmorRoomFor(roomId);
        if (outer == null) outer = roomId;
        return switch (outer) {
            case BOW_ARMOR -> SHIELD_FACE_FORE;
            case AFT_ARMOR -> SHIELD_FACE_REAR;
            case DORSAL_ARMOR -> SHIELD_FACE_LEFT;
            case VENTRAL_ARMOR -> SHIELD_FACE_RIGHT;
            default -> SHIELD_FACE_FORE;
        };
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
        Arrays.fill(shieldGateHitsRemaining, 0);
        Arrays.fill(shieldGateRechargeTimer, shieldGateRechargeDelaySeconds());
        shieldOfflineTimer = Math.max(shieldOfflineTimer, duration);
        shieldFacesSyncedMax = Math.max(0.0, shieldMax);
        shieldFacesInitialized = true;
    }

    private void updateDerivedSystemEffects() {
        if (desiredSpeedBase <= 0.0) desiredSpeedBase = Math.max(0.0, desiredSpeed);
        if (reactorBlackoutActive()) {
            desiredSpeed = 0.0;
            emergencyThrustActive = false;
            return;
        }

        double engine = systemHealthFraction(InternalSystem.ENGINES);
        double warp = systemHealthFraction(InternalSystem.WARP_ENGINES);
        double bridge = systemHealthFraction(InternalSystem.BRIDGE);
        double mobility = 0.24 + 0.52 * engine + 0.16 * warp + 0.08 * bridge;
        mobility *= enginesPowerMultiplier();
        mobility *= propulsionMobilityMultiplier();
        mobility *= emergencyThrustSpeedMultiplier();
        mobility *= crewEngineMul;
        mobility *= roomDisruptionSystemMultiplier();
        mobility *= identityStatMultiplier(ShipIdentityRegistry.IdentityStat.MOBILITY);
        mobility = Math.max(0.18, Math.min(1.38, mobility));
        desiredSpeed = desiredSpeedBase * mobility;
    }

    private RoomDamageResult applySystemDamageFromHullHit(int hullDamage,
                                                          HullGeometry.ImpactSample impact,
                                                          ShipRoomLayout.RoomDef primaryRoom,
                                                          int hullBefore,
                                                          InteriorHitProfile interiorProfile,
                                                          double impactVx,
                                                          double impactVy) {
        if (hullDamage <= 0) return RoomDamageResult.NONE;
        ensureInternalSystemsInitialized();
        ensureRoomSystemsInitialized();
        if (primaryRoom == null) {
            primaryRoom = resolvePrimaryRoomForHullHit(impact, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        if (isSmallCraft()) {
            return applySmallCraftDistributedHullDamage(hullDamage, impact, primaryRoom, hullBefore);
        }

        if (interiorProfile != null && interiorProfile != InteriorHitProfile.DEFAULT) {
            return applyPatternedInteriorDamageFromHullHit(
                    hullDamage, impact, primaryRoom, hullBefore, interiorProfile, impactVx, impactVy);
        }
        if (primaryRoom != null && activeIntegrityProtectionRoom(primaryRoom.id) == primaryRoom.id) {
            // Containment also suppresses breach and catastrophic follow-on pressure from the protected hit.
            hullDamage = Math.max(1, (int) Math.round(hullDamage * 0.45));
        }

        double primaryBefore = 0.0;
        if (primaryRoom != null) {
            double max = roomHpMax.getOrDefault(primaryRoom.id, 0.0);
            primaryBefore = roomHp.getOrDefault(primaryRoom.id, max);
        }
        HullDamageSplit split = new HullDamageSplit(primaryRoom, primaryBefore);

        int rolls = (hullDamage >= 9) ? 2 : 1;
        double roomDamageBudget = Math.max(1.0, hullDamage * roomIntegrityDamageBudgetMultiplier());
        double[] weights = new double[rolls];
        double weightTotal = 0.0;
        for (int i = 0; i < rolls; i++) {
            weights[i] = 0.86 + randomUnit() * 0.28;
            weightTotal += weights[i];
        }
        if (weightTotal <= 1e-6) weightTotal = 1.0;
        for (int i = 0; i < rolls; i++) {
            double dmg = Math.max(1.0, roomDamageBudget * (weights[i] / weightTotal));
            ShipRoomLayout.RoomDef room = primaryRoom;
            if (room != null && i > 0 && room.neighbors.length > 0 && randomUnit() < 0.38) {
                int idx = (int) Math.floor(randomUnit() * room.neighbors.length);
                if (idx < 0 || idx >= room.neighbors.length) idx = 0;
                ShipRoomLayout.RoomDef neighbor = ShipRoomLayout.roomForId(role, faction, room.neighbors[idx]);
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

    private RoomDamageResult applySmallCraftDistributedHullDamage(int hullDamage,
                                                                  HullGeometry.ImpactSample impact,
                                                                  ShipRoomLayout.RoomDef primaryRoom,
                                                                  int hullBefore) {
        double primaryBefore = 0.0;
        if (primaryRoom != null) {
            double max = roomHpMax.getOrDefault(primaryRoom.id, 0.0);
            primaryBefore = roomHp.getOrDefault(primaryRoom.id, max);
        }
        HullDamageSplit split = new HullDamageSplit(primaryRoom, primaryBefore);
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;

        for (ShipRoomLayout.RoomDef room : ShipRoomLayout.profileFor(role, faction)) {
            if (room == null || room.id == null) continue;
            damageRoom(room, hullDamage, nx, ny, false, false);
            split.absorb(lastRoomDamageResult);
        }

        double casualtySpike = (hpMax <= 0) ? 0.0 : (hullDamage / (double) hpMax) * 0.36;
        crewCasualtyRate = MathUtil.clamp(crewCasualtyRate + casualtySpike, 0.0, 0.75);
        return split.finish(this, hullBefore);
    }

    private RoomDamageResult applyPatternedInteriorDamageFromHullHit(int hullDamage,
                                                                     HullGeometry.ImpactSample impact,
                                                                     ShipRoomLayout.RoomDef primaryRoom,
                                                                     int hullBefore,
                                                                     InteriorHitProfile interiorProfile,
                                                                     double impactVx,
                                                                     double impactVy) {
        double primaryBefore = 0.0;
        if (primaryRoom != null) {
            double max = roomHpMax.getOrDefault(primaryRoom.id, 0.0);
            primaryBefore = roomHp.getOrDefault(primaryRoom.id, max);
        }
        HullDamageSplit split = new HullDamageSplit(primaryRoom, primaryBefore);
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;
        List<ShipRoomLayout.RoomDef> targets = resolveInteriorPatternTargets(interiorProfile, impact, primaryRoom, impactVx, impactVy);
        if (targets.isEmpty() && primaryRoom != null) targets = List.of(primaryRoom);

        double[] weights = interiorPatternWeights(interiorProfile, targets.size());
        for (int i = 0; i < targets.size(); i++) {
            ShipRoomLayout.RoomDef room = targets.get(i);
            if (room == null) continue;
            double dmg = Math.max(1.0, hullDamage * roomIntegrityDamageBudgetMultiplier() * weights[i]);
            damageRoom(room, dmg, nx, ny, false, false);
            split.absorb(lastRoomDamageResult);
            if (interiorProfile == InteriorHitProfile.LASER_LINE) {
                igniteRoomFire(room.id, 0.48 + 0.30 * weights[i]);
            } else if (interiorProfile == InteriorHitProfile.RED_EXPLOSIVE) {
                igniteRoomFire(room.id, 0.20 + 0.22 * weights[i]);
            } else if (interiorProfile == InteriorHitProfile.MISSILE_BLAST) {
                igniteRoomFire(room.id, 0.34 + 0.32 * weights[i]);
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

    private List<ShipRoomLayout.RoomDef> resolveInteriorPatternTargets(InteriorHitProfile interiorProfile,
                                                                       HullGeometry.ImpactSample impact,
                                                                       ShipRoomLayout.RoomDef primaryRoom,
                                                                       double impactVx,
                                                                       double impactVy) {
        if (interiorProfile == null || interiorProfile == InteriorHitProfile.DEFAULT) {
            return (primaryRoom == null) ? List.of() : List.of(primaryRoom);
        }
        List<ShipRoomLayout.RoomDef> resolved = switch (interiorProfile) {
            case BLUE_PIERCE -> resolveInteriorLineRooms(impact, primaryRoom, impactVx, impactVy, 0.05);
            case LASER_LINE -> resolveInteriorLineRooms(impact, primaryRoom, impactVx, impactVy, 0.09);
            case RED_EXPLOSIVE -> resolveInteriorBlastRooms(impact, primaryRoom, 0.26);
            case MISSILE_BLAST -> resolveInteriorBlastRooms(impact, primaryRoom, 0.42);
            case DEFAULT -> (primaryRoom == null) ? List.of() : List.of(primaryRoom);
        };
        int minimumRooms = switch (interiorProfile) {
            case BLUE_PIERCE, LASER_LINE, RED_EXPLOSIVE -> 2;
            case MISSILE_BLAST -> 3;
            case DEFAULT -> 1;
        };
        return expandInteriorPatternTargets(resolved, primaryRoom, minimumRooms);
    }

    private List<ShipRoomLayout.RoomDef> expandInteriorPatternTargets(List<ShipRoomLayout.RoomDef> targets,
                                                                      ShipRoomLayout.RoomDef primaryRoom,
                                                                      int minimumRooms) {
        java.util.LinkedHashSet<ShipRoomLayout.RoomDef> expanded = new java.util.LinkedHashSet<>();
        if (targets != null) expanded.addAll(targets);
        java.util.ArrayDeque<ShipRoomLayout.RoomDef> queue = new java.util.ArrayDeque<>(expanded);
        if (queue.isEmpty() && primaryRoom != null) {
            expanded.add(primaryRoom);
            queue.add(primaryRoom);
        }
        while (!queue.isEmpty() && expanded.size() < minimumRooms) {
            ShipRoomLayout.RoomDef room = queue.removeFirst();
            for (ShipRoomLayout.RoomId neighborId : room.neighbors) {
                ShipRoomLayout.RoomDef neighbor = ShipRoomLayout.roomForId(role, faction, neighborId);
                if (neighbor == null || ShipRoomLayout.isArmorRoom(neighbor.id) || !expanded.add(neighbor)) continue;
                queue.addLast(neighbor);
                if (expanded.size() >= minimumRooms) break;
            }
        }
        if (expanded.size() < minimumRooms) {
            for (ShipRoomLayout.RoomDef room : ShipRoomLayout.profileFor(role, faction)) {
                if (room == null || ShipRoomLayout.isArmorRoom(room.id)) continue;
                expanded.add(room);
                if (expanded.size() >= minimumRooms) break;
            }
        }
        return new ArrayList<>(expanded);
    }

    private List<ShipRoomLayout.RoomDef> resolveInteriorLineRooms(HullGeometry.ImpactSample impact,
                                                                  ShipRoomLayout.RoomDef primaryRoom,
                                                                  double impactVx,
                                                                  double impactVy,
                                                                  double halfWidth) {
        if (impact == null || !impact.onHull) {
            return (primaryRoom == null) ? List.of() : List.of(primaryRoom);
        }
        double[] dir = resolveInteriorDirection(impact, impactVx, impactVy);
        java.util.LinkedHashSet<ShipRoomLayout.RoomDef> rooms = new java.util.LinkedHashSet<>();
        int samples = 18;
        double laneSpread = Math.max(0.0, halfWidth);
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double distance = 0.04 + t * 1.75;
            double px = MathUtil.clamp(impact.normalizedX + dir[0] * distance, -1.0, 1.0);
            double py = MathUtil.clamp(impact.normalizedY + dir[1] * distance, -1.0, 1.0);
            collectInteriorLineSample(rooms, px, py);
            if (laneSpread > 1e-6) {
                double ox = -dir[1] * laneSpread;
                double oy = dir[0] * laneSpread;
                collectInteriorLineSample(rooms, px + ox, py + oy);
                collectInteriorLineSample(rooms, px - ox, py - oy);
            }
        }
        if (!rooms.isEmpty()) return new ArrayList<>(rooms);
        return (primaryRoom == null) ? List.of() : List.of(primaryRoom);
    }

    private List<ShipRoomLayout.RoomDef> resolveInteriorBlastRooms(HullGeometry.ImpactSample impact,
                                                                   ShipRoomLayout.RoomDef primaryRoom,
                                                                   double radius) {
        if (primaryRoom == null || primaryRoom.id == null) return List.of();
        int depth = (radius >= 0.38) ? 2 : 1;
        java.util.LinkedHashSet<ShipRoomLayout.RoomDef> out = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<ShipRoomLayout.RoomId> queue = new java.util.ArrayDeque<>();
        java.util.HashMap<ShipRoomLayout.RoomId, Integer> depthByRoom = new java.util.HashMap<>();
        queue.add(primaryRoom.id);
        depthByRoom.put(primaryRoom.id, 0);
        while (!queue.isEmpty()) {
            ShipRoomLayout.RoomId roomId = queue.removeFirst();
            int currentDepth = depthByRoom.getOrDefault(roomId, depth + 1);
            if (currentDepth > depth) continue;
            ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(role, faction, roomId);
            if (room == null || ShipRoomLayout.isArmorRoom(room.id)) continue;
            out.add(room);
            for (ShipRoomLayout.RoomId neighborId : room.neighbors) {
                if (neighborId == null || depthByRoom.containsKey(neighborId)) continue;
                depthByRoom.put(neighborId, currentDepth + 1);
                queue.addLast(neighborId);
            }
        }
        return out.isEmpty() ? List.of(primaryRoom) : new ArrayList<>(out);
    }

    private double[] resolveInteriorDirection(HullGeometry.ImpactSample impact, double impactVx, double impactVy) {
        double lx = 0.0;
        double ly = 0.0;
        if (Double.isFinite(impactVx) && Double.isFinite(impactVy)) {
            double len = Math.hypot(impactVx, impactVy);
            if (len > 1e-9) {
                double wx = impactVx / len;
                double wy = impactVy / len;
                double c = Math.cos(angle);
                double s = Math.sin(angle);
                lx = wx * c + wy * s;
                ly = -wx * s + wy * c;
            }
        }
        double dirLen = Math.hypot(lx, ly);
        if (dirLen <= 1e-6 && impact != null) {
            lx = -impact.normalizedX;
            ly = -impact.normalizedY;
            dirLen = Math.hypot(lx, ly);
        }
        if (dirLen <= 1e-6) return new double[]{1.0, 0.0};
        return new double[]{lx / dirLen, ly / dirLen};
    }

    private double[] interiorPatternWeights(InteriorHitProfile interiorProfile, int count) {
        int n = Math.max(1, count);
        double[] weights = new double[n];
        if (n == 1) {
            weights[0] = 1.0;
            return weights;
        }
        switch (interiorProfile) {
            case BLUE_PIERCE -> {
                double total = 0.0;
                for (int i = 0; i < n; i++) {
                    weights[i] = Math.pow(0.72, i);
                    total += weights[i];
                }
                normalizeWeights(weights, total);
            }
            case LASER_LINE -> {
                double total = 0.0;
                for (int i = 0; i < n; i++) {
                    weights[i] = 1.0;
                    total += 1.0;
                }
                normalizeWeights(weights, total);
            }
            case RED_EXPLOSIVE, MISSILE_BLAST -> {
                double total = 0.0;
                for (int i = 0; i < n; i++) {
                    weights[i] = (i == 0) ? 1.0 : Math.pow(0.62, i);
                    total += weights[i];
                }
                normalizeWeights(weights, total);
            }
            case DEFAULT -> weights[0] = 1.0;
        }
        return weights;
    }

    private void normalizeWeights(double[] weights, double total) {
        double safeTotal = (total <= 1e-9) ? 1.0 : total;
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= safeTotal;
        }
    }

    private void collectInteriorLineSample(java.util.LinkedHashSet<ShipRoomLayout.RoomDef> rooms,
                                           double normalizedX,
                                           double normalizedY) {
        ShipRoomLayout.RoomDef room = RoomHitResolver.resolve(
                role,
                faction,
                MathUtil.clamp(normalizedX, -1.0, 1.0),
                MathUtil.clamp(normalizedY, -1.0, 1.0)
        );
        if (room == null || room.id == null || ShipRoomLayout.isArmorRoom(room.id)) return;
        rooms.add(room);
    }

    private void applyCatastrophicFailureRules(ShipRoomLayout.RoomDef primaryRoom,
                                               HullGeometry.ImpactSample impact,
                                               int hullDamage) {
        if (primaryRoom == null || hullDamage <= 0 || hpMax <= 0) return;
        if (primaryRoom.id == null) return;

        double graceScale = (catastrophicChainGraceTimer > 0.0) ? 0.34 : 1.0;
        double severity = MathUtil.clamp(hullDamage / (double) hpMax, 0.02, 1.0);
        boolean triggered = false;

        if (ShipRoomLayout.isPowerRoom(primaryRoom.id)) {
            double reactorFrac = roomHealthFraction(ShipRoomLayout.RoomId.REACTOR);
            double chance = (0.08 + (1.0 - reactorFrac) * 0.45 + severity * 0.18) * graceScale;
            if (randomUnit() < MathUtil.clamp(chance, 0.0, 0.78)) {
                triggered = triggerReactorCriticalChain(impact, hullDamage, severity);
            }
        }

        if (!triggered && (primaryRoom.id == ShipRoomLayout.RoomId.MAGAZINES
                || primaryRoom.id == ShipRoomLayout.RoomId.MISSILE_LAUNCHERS
                || isSystemDestroyed(InternalSystem.MAGAZINES))) {
            double magsFrac = roomHealthFraction(ShipRoomLayout.RoomId.MAGAZINES);
            double chance = (0.10 + (1.0 - magsFrac) * 0.42 + severity * 0.20) * graceScale;
            if (randomUnit() < MathUtil.clamp(chance, 0.0, 0.82)) {
                triggered = triggerMagazineDetonationRisk(impact, hullDamage, severity);
            }
        }

        if (!triggered && (primaryRoom.id == ShipRoomLayout.RoomId.INTEGRITY_FIELD
                || roomHealthFraction(ShipRoomLayout.RoomId.INTEGRITY_FIELD) < 0.28)) {
            double integFrac = roomHealthFraction(ShipRoomLayout.RoomId.INTEGRITY_FIELD);
            double chance = (0.11 + (1.0 - integFrac) * 0.48 + severity * 0.12) * graceScale;
            if (randomUnit() < MathUtil.clamp(chance, 0.0, 0.80)) {
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
        ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(role, faction, ShipRoomLayout.RoomId.REACTOR);
        ShipRoomLayout.RoomDef conduits = ShipRoomLayout.roomForId(role, faction, ShipRoomLayout.RoomId.POWER_CONDUITS);
        if (reactor == null) return false;

        double reactorBefore = roomHp.getOrDefault(reactor.id, roomHpMax.getOrDefault(reactor.id, 0.0));
        double chainBudget = Math.max(2.0, hpMax * catastrophicChainDamageCapFraction() * (0.65 + 0.35 * severity));
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
        ShipRoomLayout.RoomDef mags = ShipRoomLayout.roomForId(role, faction, ShipRoomLayout.RoomId.MAGAZINES);
        if (mags == null) return false;

        double magsBefore = roomHp.getOrDefault(mags.id, roomHpMax.getOrDefault(mags.id, 0.0));
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;
        double chainBudget = Math.max(2.0, hpMax * catastrophicChainDamageCapFraction() * (0.55 + 0.30 * severity));
        double magsDamage = Math.max(2.0, Math.min(chainBudget, hullDamage * (0.52 + 0.90 * severity)));
        damageRoom(mags, magsDamage, nx, ny, false);

        RoomDamageResult base = lastRoomDamageResult;
        List<String> transitions = new ArrayList<>();
        if (base != null && base != RoomDamageResult.NONE) transitions.addAll(base.subsystemTransitions);
        transitions.add("magazines:detonation_risk");
        spawnDestroyedRoomExplosion(mags, nx, ny, 5);
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
        ShipRoomLayout.RoomDef integrity = ShipRoomLayout.roomForId(role, faction, ShipRoomLayout.RoomId.INTEGRITY_FIELD);
        if (integrity == null) return false;

        double integrityBefore = roomHp.getOrDefault(integrity.id, roomHpMax.getOrDefault(integrity.id, 0.0));
        double nx = (impact == null) ? Double.NaN : impact.normalizedX;
        double ny = (impact == null) ? Double.NaN : impact.normalizedY;
        double collapseBudget = Math.max(1.0, hpMax * (catastrophicChainDamageCapFraction() * 0.80));
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
            if (rel < Math.toRadians(45.0) && randomUnit() < 0.55) {
                return pickSystem(InternalSystem.WEAPONS, InternalSystem.SENSORS, InternalSystem.BRIDGE);
            }
            if (rel > Math.toRadians(135.0) && randomUnit() < 0.55) {
                return pickSystem(InternalSystem.ENGINES, InternalSystem.WARP_ENGINES, InternalSystem.REACTOR_CORE);
            }
        }

        return pickSystem(InternalSystem.values());
    }

    private InternalSystem pickSystem(InternalSystem... systems) {
        if (systems == null || systems.length == 0) return null;
        int idx = (int) Math.floor(randomUnit() * systems.length);
        if (idx < 0 || idx >= systems.length) idx = 0;
        InternalSystem out = systems[idx];
        if (out == InternalSystem.SHIELDS && !shieldActive && randomUnit() < 0.7) {
            out = (randomUnit() < 0.5) ? InternalSystem.ENGINES : InternalSystem.WEAPONS;
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
        if (breachScore < 0.60 && randomUnit() > breachScore * 0.70) return;

        ShipRoomLayout.RoomDef breachedRoom = resolveRoomForImpact(impact, Double.NaN, Double.NaN);
        if (breachedRoom == null) return;

        double graceScale = (catastrophicChainGraceTimer > 0.0) ? 0.36 : 1.0;
        double catastrophicChance = (breachScore > 1.05) ? 0.72 : 0.0;
        if (hullDamage >= Math.max(10, hpMax / 8)) catastrophicChance = Math.max(catastrophicChance, 0.60);
        catastrophicChance *= graceScale;
        boolean catastrophic = randomUnit() < MathUtil.clamp(catastrophicChance, 0.0, 0.90);
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
        if (hullDamage >= 18) breachChance += 0.10;
        if (hullDamage >= 30) breachChance += 0.12;
        if (Math.abs(impact.normalizedX) < 0.24 && Math.abs(impact.normalizedY) < 0.24) breachChance += 0.08;
        breachChance = MathUtil.clamp(breachChance, 0.0, 0.95);

        double breachRadius = 0.0;
        if (randomUnit() < breachChance) {
            double impactClass = MathUtil.clamp(Math.sqrt(Math.max(0.0, hullDamage) / 6.0), 0.0, 2.8);
            double base = Math.max(2.8, radius * (0.05 + impactClass * 0.015));
            double bonus = radius * (0.07 + severity * 0.24 + impactClass * 0.08 + severity * severity * 0.12);
            breachRadius = MathUtil.clamp(base + bonus, 2.8, Math.max(5.0, radius * 0.68));
        }

        if (hullImpactMarks.size() >= MAX_HULL_IMPACT_MARKS) {
            hullImpactMarks.remove(0);
        }
        ShipRoomLayout.RoomId roomId = (room == null) ? null : room.id;
        hullImpactMarks.add(new HullImpactMark(impact.localX, impact.localY, severity, breachRadius, roomId));
        hullImpactNoDamageTimer = 0.0;
        if (breachRadius > 1e-3
                && room != null
                && room.id != null
                && !ShipRoomLayout.isArmorRoom(room.id)
                && roomHealthFraction(room.id) <= 1e-3) {
            spawnDestroyedRoomExplosion(room, impact.normalizedX, impact.normalizedY,
                    Math.max(2, (int) Math.round(2.0 + severity * 4.0)));
        }
    }

    private void updateHullImpactDecay(double dt) {
        if (dt <= 0.0) return;
        if (hullImpactMarks.isEmpty()) {
            hullImpactNoDamageTimer = HULL_IMPACT_DECAY_IDLE_SECONDS;
            return;
        }
        // Persistent hull impact marks are now culled by renderer detail distance rather than a timer.
        hullImpactNoDamageTimer = 0.0;
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

    public void tryCIWS(double dt, List<Projectile> projectiles) {
        tryCIWS(dt, projectiles, null);
    }

    public void tryCIWS(double dt, GameContext ctx) {
        tryCIWS(dt, ctx, ctx == null ? null : ProjectileScalePolicy.planFor(ctx, 0L));
    }

    public void tryCIWS(double dt, GameContext ctx, ProjectileScalePolicy.FramePlan projectilePlan) {
        if (ctx == null) {
            tryCIWS(dt, null, null, null);
            return;
        }
        tryCIWS(dt, ctx.projectiles, ctx.ships, ctx.entityQuery, projectilePlan);
    }

    /**
     * CIWS point-defense.
     *
     * - Intercepts nearby hostile missiles.
     * - Engages nearby hostile small craft when present.
     * - Team C keeps laser PD against missiles; all other CIWS fire visible pellets.
     */
    public void tryCIWS(double dt, List<Projectile> projectiles, List<Ship> ships) {
        tryCIWS(dt, projectiles, ships, null);
    }

    private void tryCIWS(double dt, List<Projectile> projectiles, List<Ship> ships, EntityQueryIndex query) {
        tryCIWS(dt, projectiles, ships, query, null);
    }

    private void tryCIWS(double dt, List<Projectile> projectiles, List<Ship> ships, EntityQueryIndex query,
                         ProjectileScalePolicy.FramePlan projectilePlan) {
        if (!alive || !hasCIWS || !canUseCombatSystems()) return;
        if (ciwsTimer > 0) return;
        if ((projectiles == null || projectiles.isEmpty()) && (ships == null || ships.isEmpty())) return;

        Missile closestMissile = findClosestCiwsMissile(projectiles, query);
        Ship closestSmallCraft = findClosestCiwsSmallCraft(ships, query);
        if (closestMissile == null && closestSmallCraft == null) return;

        boolean targetMissile = closestMissile != null;
        if (closestMissile != null && closestSmallCraft != null) {
            double missileD2 = GameMath.dist2(x, y, closestMissile.x, closestMissile.y);
            double craftD2 = GameMath.dist2(x, y, closestSmallCraft.x, closestSmallCraft.y);
            TacticalCombatDepthSystem.PointDefensePriority priority = TacticalCombatDepthSystem.pointDefensePriority(this);
            targetMissile = switch (priority) {
                case MISSILES_FIRST -> missileD2 * 0.55 <= craftD2;
                case STRIKE_CRAFT_FIRST -> missileD2 * 1.65 <= craftD2;
                case BALANCED -> missileD2 * 0.92 <= craftD2;
            };
        }

        // Fire!
        ciwsTimer = ciwsCooldown;
        if (projectilePlan != null && !projectilePlan.allowCiwsBurst(faction, x, y)) return;

        if (targetMissile) {
            double aim = computeCiwsLeadAim(dt, closestMissile.x, closestMissile.y, closestMissile.vx, closestMissile.vy);
            if (faction == Faction.TEAM_C) {
                firePointDefenseLaser(dt, projectiles, closestMissile, aim, projectilePlan);
            } else {
                fireCiwsPellets(dt, projectiles, aim, projectilePlan);
            }
            return;
        }

        double aim = computeCiwsLeadAim(dt, closestSmallCraft.x, closestSmallCraft.y, closestSmallCraft.vx, closestSmallCraft.vy);
        fireCiwsPellets(dt, projectiles, aim, projectilePlan);
    }

    private Missile findClosestCiwsMissile(List<Projectile> projectiles, EntityQueryIndex query) {
        if ((projectiles == null || projectiles.isEmpty()) && query == null) return null;
        Missile closest = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        Iterable<? extends Projectile> source = projectiles;
        double range = effectiveCiwsRange();

        if (query != null) {
            query.collectMissilesNear(x, y, range, ciwsMissileScratch);
            source = ciwsMissileScratch;
        }

        for (Projectile p : source) {
            if (!p.alive) continue;
            if (!(p instanceof Missile m)) continue;
            if (faction != null && faction.isFriendlyTo(m.faction)) continue;

            double d2 = GameMath.dist2(x, y, m.x, m.y);
            if (d2 <= range * range && d2 < bestD2) {
                bestD2 = d2;
                closest = m;
            }
        }
        return closest;
    }

    private Ship findClosestCiwsSmallCraft(List<Ship> ships, EntityQueryIndex query) {
        if ((ships == null || ships.isEmpty()) && query == null) return null;
        Ship closest = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        Iterable<Ship> source = ships;
        double range = effectiveCiwsRange();

        if (query != null && faction != null) {
            query.collectHostileShipsNear(faction, x, y, range, ciwsShipScratch);
            source = ciwsShipScratch;
        }

        for (Ship s : source) {
            if (s == null || s == this) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (!s.isSmallCraft()) continue;
            if (faction != null && faction.isFriendlyTo(s.faction)) continue;

            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 <= range * range && d2 < bestD2) {
                bestD2 = d2;
                closest = s;
            }
        }
        return closest;
    }

    private double computeCiwsLeadAim(double dt, double targetX, double targetY, double targetVx, double targetVy) {
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY)) return angle;
        if (dt <= 1e-9 || ciwsPelletSpeed <= 1e-9) return Math.atan2(targetY - y, targetX - x);

        double targetVelX = targetVx / dt;
        double targetVelY = targetVy / dt;
        double[] intercept = MathUtil.interceptPoint(x, y, targetX, targetY, targetVelX, targetVelY, ciwsPelletSpeed);
        return Math.atan2(intercept[1] - y, intercept[0] - x);
    }

    private void fireCiwsPellets(double dt, List<Projectile> projectiles, double aim) {
        fireCiwsPellets(dt, projectiles, aim, null);
    }

    private void fireCiwsPellets(double dt, List<Projectile> projectiles, double aim,
                                 ProjectileScalePolicy.FramePlan projectilePlan) {
        if (projectiles == null) return;
        int pellets = Math.max(1, ciwsPelletsPerBurst);
        if (projectilePlan != null) pellets = projectilePlan.ciwsPelletsForBurst(pellets);
        double muzzleForward = radius + 8.0;
        double maxLateral = Math.max(0.0, Math.min(radius * 0.55, 14.0));
        double lateralStep = (pellets <= 1) ? 0.0 : (maxLateral * 2.0) / (pellets - 1);
        double rightX = -Math.sin(aim);
        double rightY = Math.cos(aim);

        for (int i = 0; i < pellets; i++) {
            double lateral = (i - (pellets - 1) * 0.5) * lateralStep;
            double sx = x + Math.cos(aim) * muzzleForward + rightX * lateral;
            double sy = y + Math.sin(aim) * muzzleForward + rightY * lateral;

            Projectile pellet = new CIWSPellet(
                    sx,
                    sy,
                    aim,
                    dt,
                    ciwsPelletSpeed,
                    ciwsPelletDamage,
                    projectilePlan == null ? ciwsPelletLife : projectilePlan.ciwsLifeFor(ciwsPelletLife),
                    ciwsPelletRadius,
                    faction
            );
            pellet.sourceShipId = id;
            projectiles.add(pellet);
        }
    }

    private void firePointDefenseLaser(double dt, List<Projectile> projectiles, Missile target, double aim) {
        firePointDefenseLaser(dt, projectiles, target, aim, null);
    }

    private void firePointDefenseLaser(double dt, List<Projectile> projectiles, Missile target, double aim,
                                       ProjectileScalePolicy.FramePlan projectilePlan) {
        if (projectiles == null || target == null || !target.alive) return;

        int pulses = Math.max(1, ciwsPelletsPerBurst);
        if (projectilePlan != null) pulses = projectilePlan.ciwsPelletsForBurst(pulses);
        int pulseDamage = Math.max(1, Math.min(2, (int) Math.round(ciwsPelletDamage * 0.75)));
        int pulseLife = projectilePlan == null ? 2 : projectilePlan.ciwsLifeFor(2);
        double beamWidth = Math.max(1.0, ciwsPelletRadius * 0.85);
        double muzzleForward = radius + 8.0;
        double maxLateral = Math.max(0.0, Math.min(radius * 0.55, 14.0));
        double lateralStep = (pulses <= 1) ? 0.0 : (maxLateral * 2.0) / (pulses - 1);
        double rightX = -Math.sin(aim);
        double rightY = Math.cos(aim);

        for (int i = 0; i < pulses; i++) {
            double lateral = (i - (pulses - 1) * 0.5) * lateralStep;
            double sx = x + Math.cos(aim) * muzzleForward + rightX * lateral;
            double sy = y + Math.sin(aim) * muzzleForward + rightY * lateral;

            PointDefenseLaser laser = new PointDefenseLaser(
                    sx,
                    sy,
                    target,
                    pulseDamage,
                    pulseLife,
                    beamWidth,
                    faction
            );
            laser.sourceShipId = id;
            projectiles.add(laser);
        }
    }

    public boolean canLaunchFighter() {
        return alive && isCarrier && fighterTimer <= 0 && !reactorBlackoutActive();
    }

    public void resetFighterTimer() {
        fighterTimer = fighterLaunchCooldown / Math.max(0.10, strikeCraftTempoMultiplier());
    }

    public void resetFlightDeckLoadout() {
        ShipRole[] defaults;
        if (supportsPicketFlightDeck()) {
            defaults = new ShipRole[]{ShipRole.PICKET, ShipRole.PICKET, ShipRole.PICKET, ShipRole.PICKET, ShipRole.PICKET};
        } else if (role == ShipRole.DRONE_CARRIER) {
            defaults = new ShipRole[]{ShipRole.DRONE, ShipRole.DRONE, ShipRole.DRONE, ShipRole.FIGHTER, ShipRole.BOMBER};
        } else {
            defaults = new ShipRole[]{ShipRole.FIGHTER, ShipRole.FIGHTER, ShipRole.FIGHTER, ShipRole.FIGHTER, ShipRole.BOMBER};
        }
        for (int i = 0; i < flightDeckLoadout.length; i++) {
            flightDeckLoadout[i] = defaults[Math.min(i, defaults.length - 1)];
        }
        flightDeckLaunchCursor = 0;
    }

    public ShipRole flightDeckRoleAt(int slot) {
        if (slot < 0 || slot >= flightDeckLoadout.length) return ShipRole.FIGHTER;
        ShipRole role = flightDeckLoadout[slot];
        return (role == null) ? ShipRole.FIGHTER : role;
    }

    public void setFlightDeckRole(int slot, ShipRole craftRole) {
        if (slot < 0 || slot >= flightDeckLoadout.length) return;
        if (craftRole != ShipRole.FIGHTER && craftRole != ShipRole.BOMBER && craftRole != ShipRole.DRONE
                && !(craftRole == ShipRole.PICKET && supportsPicketFlightDeck())) {
            return;
        }
        flightDeckLoadout[slot] = craftRole;
    }

    public ShipRole cycleFlightDeckRole(int slot, int dir) {
        ShipRole[] options = supportsPicketFlightDeck()
                ? new ShipRole[]{ShipRole.PICKET, ShipRole.FIGHTER, ShipRole.DRONE, ShipRole.BOMBER}
                : new ShipRole[]{ShipRole.FIGHTER, ShipRole.DRONE, ShipRole.BOMBER};
        int idx = 0;
        ShipRole current = flightDeckRoleAt(slot);
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) {
                idx = i;
                break;
            }
        }
        idx = Math.floorMod(idx + ((dir < 0) ? -1 : 1), options.length);
        setFlightDeckRole(slot, options[idx]);
        return options[idx];
    }

    public boolean supportsPicketFlightDeck() {
        return role == ShipRole.MOTHERSHIP || role == ShipRole.MOBILE_STATION_TITAN;
    }

    public boolean canSpawnDefender() {
        return alive && isBase && baseSpawnTimer <= 0 && !reactorBlackoutActive();
    }

    public boolean canSpawnMobileReinforcement() {
        return alive && isCarrier && baseSpawnTimer <= 0 && !reactorBlackoutActive();
    }

    public void resetBaseSpawnTimer() {
        double tempo = isCarrier ? strikeCraftTempoMultiplier() : 1.0;
        baseSpawnTimer = baseSpawnCooldown / Math.max(0.10, tempo);
    }

    public double getSuperweaponRemaining() {
        return Math.max(superweaponTimer, superweaponCharging ? superweaponChargeTimer : 0.0);
    }

    public double getSuperweaponRechargeProgress() {
        if (!hasSuperweapon) return 0.0;
        if (superweaponCooldown <= 1e-9) return 1.0;
        double remaining = MathUtil.clamp(superweaponTimer, 0.0, superweaponCooldown);
        return MathUtil.clamp(1.0 - (remaining / superweaponCooldown), 0.0, 1.0);
    }

    public boolean isSuperweaponCharging() {
        return superweaponCharging;
    }

    public double getSuperweaponChargeProgress() {
        if (!superweaponCharging) return 0.0;
        if (superweaponChargeTime <= 1e-9) return 1.0;
        double t = 1.0 - (superweaponChargeTimer / superweaponChargeTime);
        return Math.max(0.0, Math.min(1.0, t));
    }

    public boolean isSuperweaponBeamActive() {
        return superweaponBeamTimer > 0.0;
    }

    public double getSuperweaponAimAngle() {
        if (superweaponCharging && Double.isFinite(queuedSuperweaponAim)) return queuedSuperweaponAim;
        if (superweaponBeamTimer > 0.0 && Double.isFinite(superweaponBeamAim)) return superweaponBeamAim;
        return angle;
    }

    public Projectile pollSuperweaponShot() {
        if (pendingSuperweaponShots.isEmpty()) return null;
        Projectile shot = pendingSuperweaponShots.get(0);
        pendingSuperweaponShots.remove(0);
        return shot;
    }

    public void resetSuperweaponCooldown() {
        superweaponTimer = 0.0;
        superweaponChargeTimer = 0.0;
        superweaponCharging = false;
        stasisFieldTimer = 0.0;
        pendingSuperweaponShots.clear();
        queuedSuperweaponAim = Double.NaN;
        queuedSuperweaponTarget = null;
        superweaponBeamTimer = 0.0;
        superweaponBeamTickTimer = 0.0;
        superweaponBeamAim = Double.NaN;
        superweaponBeamTarget = null;
        superweaponBeamSpreadTargets.clear();
    }

    public boolean canFireSuperweapon() {
        if (!alive || dying) return false;
        if (!canUseCombatSystems()) return false;
        if (!hasSuperweapon) return false;
        return superweaponTimer <= 0.0 && !superweaponCharging;
    }

    public void trackSuperweaponAim(double targetX, double targetY) {
        if (!hasSuperweapon) return;
        double aim = resolveSuperweaponAim(targetX, targetY);
        if (superweaponCharging) queuedSuperweaponAim = aim;
        if (superweaponBeamTimer > 0.0) superweaponBeamAim = aim;
    }

    public Projectile tryFireSuperweaponAt(double targetX, double targetY, double dt) {
        return tryFireSuperweaponAt(null, targetX, targetY, dt);
    }

    public Projectile tryFireSuperweaponAt(GameContext ctx, double targetX, double targetY, double dt) {
        if (!canFireSuperweapon()) return null;
        if (dt <= 0.0) return null;
        double aim = resolveSuperweaponAim(targetX, targetY);
        return tryFireSuperweaponResolved(ctx, aim, null, dt);
    }

    public Projectile tryFireSuperweapon(Ship target, double dt) {
        return tryFireSuperweapon(null, target, dt);
    }

    public Projectile tryFireSuperweapon(GameContext ctx, Ship target, double dt) {
        if (target == null) return tryFireSuperweaponAt(ctx, Double.NaN, Double.NaN, dt);
        if (!canFireSuperweapon()) return null;
        if (dt <= 0.0) return null;
        double aim = resolveSuperweaponAim(target.x, target.y);
        return tryFireSuperweaponResolved(ctx, aim, target, dt);
    }

    public boolean isShieldOnline() {
        ensureShieldFacesSynced();
        return shieldActive
                && effectiveShieldCapacityMax() > 0.0
                && !reactorBlackoutActive()
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
        recentShieldImpactFace = -1;
        clearShieldImpactMarks();
        for (int i = 0; i < SHIELD_FACE_COUNT; i++) shieldFaceRegenLock[i] = 0.0;
        Arrays.fill(shieldGateHitsRemaining, 0);
        Arrays.fill(shieldGateHitsMax, -1);
        Arrays.fill(shieldGateRechargeTimer, 0.0);
        Arrays.fill(armorGateHitsRemaining, 0);
        Arrays.fill(armorGateHitsMax, -1);
        syncDefenseGateState(false);
        if (shieldFacingMode == ShieldFacingMode.FORWARD) {
            shieldFacingAngle = angle;
        }
    }

    private void syncDefenseGateState(boolean allowRestore) {
        int shieldCap = configuredShieldGateHitCap();
        for (int face = 0; face < SHIELD_FACE_COUNT; face++) {
            boolean initShield = shieldGateHitsMax[face] != shieldCap;
            shieldGateHitsMax[face] = shieldCap;
            if (shieldCap <= 0) {
                shieldGateHitsRemaining[face] = 0;
                shieldGateRechargeTimer[face] = 0.0;
                continue;
            }
            if (initShield) {
                shieldGateHitsRemaining[face] = shieldCap;
                shieldGateRechargeTimer[face] = 0.0;
            }
            shieldGateHitsRemaining[face] = Math.max(0, Math.min(shieldGateHitsRemaining[face], shieldCap));
            shieldGateRechargeTimer[face] = Math.max(0.0, shieldGateRechargeTimer[face]);
        }

        int armorCap = configuredArmorGateHitCap();
        for (int face = 0; face < SHIELD_FACE_COUNT; face++) {
            boolean initArmor = armorGateHitsMax[face] != armorCap;
            armorGateHitsMax[face] = armorCap;
            if (armorCap <= 0) {
                armorGateHitsRemaining[face] = 0;
                continue;
            }
            if (initArmor) armorGateHitsRemaining[face] = armorCap;
            armorGateHitsRemaining[face] = Math.max(0, Math.min(armorGateHitsRemaining[face], armorCap));
            if (allowRestore && armorDefenseLayerFullyRestored(face)) {
                armorGateHitsRemaining[face] = armorCap;
            }
        }
    }

    private int configuredShieldGateHitCap() {
        if (!shieldActive || shieldMax <= 1e-9) return 0;
        Faction durabilityFaction = (faction == null) ? Faction.ENEMY : faction;
        return switch (durabilityFaction) {
            case PLAYER, ALLY -> 5;
            case TEAM_C -> 10;
            case ENEMY -> 1;
            default -> 0;
        };
    }

    private int configuredArmorGateHitCap() {
        return (faction != null && faction.isYellowLineage() && hasArmorLayer()) ? 5 : 0;
    }

    private boolean consumeShieldGateHit(int face) {
        int normalizedFace = normalizeDefenseFace(face);
        if (shieldGateHitsRemaining[normalizedFace] <= 0) return false;
        shieldGateHitsRemaining[normalizedFace]--;
        if (shieldGateHitsRemaining[normalizedFace] < shieldGateHitsMax[normalizedFace]) {
            shieldGateRechargeTimer[normalizedFace] = shieldGateRechargeDelaySeconds();
        }
        return true;
    }

    private boolean consumeArmorGateHit(int face) {
        int normalizedFace = normalizeDefenseFace(face);
        if (armorGateHitsRemaining[normalizedFace] <= 0) return false;
        armorGateHitsRemaining[normalizedFace]--;
        return true;
    }

    private boolean armorDefenseLayerFullyRestored(int face) {
        if (configuredArmorGateHitCap() <= 0) return false;
        ensureRoomSystemsInitialized();
        ShipRoomLayout.RoomId outerRoom = armorRoomForFace(face, false);
        ShipRoomLayout.RoomId innerRoom = armorRoomForFace(face, true);
        boolean found = false;
        for (ShipRoomLayout.RoomId roomId : new ShipRoomLayout.RoomId[]{outerRoom, innerRoom}) {
            if (roomId == null) continue;
            double max = roomHpMax.getOrDefault(roomId, 0.0);
            if (max <= 1e-6) continue;
            found = true;
            double hpv = roomHp.getOrDefault(roomId, max);
            if (hpv < max - 1e-6) return false;
        }
        return found;
    }

    private void updateShieldGateRecharge(double dt) {
        if (dt <= 0.0) return;
        if (!shieldActive || shieldMax <= 1e-9) return;
        if (!isShieldOnline() || shield <= 1e-6) return;
        double interval = shieldGateRechargeIntervalSeconds();
        for (int face = 0; face < SHIELD_FACE_COUNT; face++) {
            int maxHits = shieldGateHitsMax[face];
            if (maxHits <= 0) continue;
            if (shieldGateHitsRemaining[face] >= maxHits) {
                shieldGateRechargeTimer[face] = 0.0;
                continue;
            }
            shieldGateRechargeTimer[face] = Math.max(0.0, shieldGateRechargeTimer[face] - dt);
            while (shieldGateRechargeTimer[face] <= 1e-9 && shieldGateHitsRemaining[face] < maxHits) {
                shieldGateHitsRemaining[face]++;
                if (shieldGateHitsRemaining[face] < maxHits) {
                    shieldGateRechargeTimer[face] += interval;
                } else {
                    shieldGateRechargeTimer[face] = 0.0;
                }
            }
        }
    }

    private int gateValueForFace(int[] values, int face) {
        if (values == null || values.length <= 0) return 0;
        return values[normalizeDefenseFace(face)];
    }

    private int minGateValue(int[] values) {
        if (values == null || values.length <= 0) return 0;
        int min = Integer.MAX_VALUE;
        for (int value : values) min = Math.min(min, value);
        return (min == Integer.MAX_VALUE) ? 0 : Math.max(0, min);
    }

    private int maxGateValue(int[] values) {
        if (values == null || values.length <= 0) return 0;
        int max = 0;
        for (int value : values) max = Math.max(max, value);
        return Math.max(0, max);
    }

    private int normalizeDefenseFace(int face) {
        if (face < 0 || face >= SHIELD_FACE_COUNT) return SHIELD_FACE_FORE;
        return face;
    }

    private ShipRoomLayout.RoomId armorRoomForFace(int face, boolean inner) {
        return switch (normalizeDefenseFace(face)) {
            case SHIELD_FACE_FORE -> inner ? ShipRoomLayout.RoomId.BOW_ARMOR_INNER : ShipRoomLayout.RoomId.BOW_ARMOR;
            case SHIELD_FACE_REAR -> inner ? ShipRoomLayout.RoomId.AFT_ARMOR_INNER : ShipRoomLayout.RoomId.AFT_ARMOR;
            case SHIELD_FACE_LEFT -> inner ? ShipRoomLayout.RoomId.DORSAL_ARMOR_INNER : ShipRoomLayout.RoomId.DORSAL_ARMOR;
            case SHIELD_FACE_RIGHT -> inner ? ShipRoomLayout.RoomId.VENTRAL_ARMOR_INNER : ShipRoomLayout.RoomId.VENTRAL_ARMOR;
            default -> inner ? ShipRoomLayout.RoomId.BOW_ARMOR_INNER : ShipRoomLayout.RoomId.BOW_ARMOR;
        };
    }

    private double shieldGateRechargeDelaySeconds() {
        return Math.max(2.0, shieldRebootDelay * 1.5);
    }

    private double shieldGateRechargeIntervalSeconds() {
        return Math.max(1.0, shieldRebootDelay * 0.75);
    }

    private double resolveSuperweaponAim(double targetX, double targetY) {
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY)) return angle;
        double dx = targetX - x;
        double dy = targetY - y;
        double d2 = dx * dx + dy * dy;
        if (d2 < 1e-8) return angle;
        return Math.atan2(dy, dx);
    }

    private Projectile tryFireSuperweaponResolved(GameContext ctx, double aim, Ship target, double dt) {
        queuedSuperweaponAim = aim;
        queuedSuperweaponTarget = isValidSuperweaponTarget(target) ? target : null;
        queuedSuperweaponSpreadTargets.clear();
        queuedSuperweaponSpreadTargets.addAll(selectKineticShotgunSpreadTargets(ctx, queuedSuperweaponTarget));

        double effectiveChargeTime = superweaponChargeTime;

        if (effectiveChargeTime > 0.0) {
            superweaponCharging = true;
            superweaponChargeTimer = effectiveChargeTime;
            return null;
        }

        queuedSuperweaponAim = Double.NaN;
        Ship immediateTarget = queuedSuperweaponTarget;
        queuedSuperweaponTarget = null;
        List<Ship> immediateSpreadTargets = new ArrayList<>(queuedSuperweaponSpreadTargets);
        queuedSuperweaponSpreadTargets.clear();
        return fireSuperweaponShot(dt, aim, immediateTarget, immediateSpreadTargets);
    }

    private Projectile fireSuperweaponShot(double dt, double aim, Ship target) {
        return fireSuperweaponShot(dt, aim, target, queuedSuperweaponSpreadTargets);
    }

    private Projectile fireSuperweaponShot(double dt, double aim, Ship target, List<Ship> spreadTargets) {
        angle = aim;
        superweaponTimer = Math.max(1.0, superweaponCooldown);
        onFiredWeapon();

        superweaponBeamAim = aim;
        superweaponBeamTarget = isValidSuperweaponTarget(target) ? target : null;
        superweaponBeamSpreadTargets.clear();
        if (spreadTargets != null) {
            for (Ship spreadTarget : spreadTargets) {
                if (isValidSuperweaponTarget(spreadTarget)) superweaponBeamSpreadTargets.add(spreadTarget);
            }
        }
        if (isRedHyperweaponKineticShotgun() && superweaponBeamSpreadTargets.isEmpty() && superweaponBeamTarget != null) {
            superweaponBeamSpreadTargets.add(superweaponBeamTarget);
        }
        switch (superweaponPattern) {
            case DESTABILIZER_PULSE -> {
                superweaponBeamTimer = 0.0;
                superweaponBeamTickTimer = 0.0;
                superweaponBeamTarget = null;
            }
            case PULSE_BARRAGE -> {
                superweaponBeamTimer = Math.max(0.0, superweaponBeamDuration);
                superweaponBeamTickTimer = superweaponTickSpacing();
            }
            case KINETIC_SHOTGUN -> {
                superweaponBeamTimer = Math.max(0.55, superweaponBeamDuration);
                superweaponBeamTickTimer = superweaponTickSpacing();
            }
            case MISSILE_BARRAGE -> {
                if (isYellowHyperweaponTitan()) {
                    superweaponBeamTimer = 0.0;
                    superweaponBeamTickTimer = 0.0;
                    superweaponBeamTarget = null;
                } else {
                    superweaponBeamTimer = Math.max(0.45, superweaponBeamDuration * 1.7);
                    superweaponBeamTickTimer = superweaponTickSpacing();
                }
            }
            case LANCE_CONE -> {
                superweaponBeamTimer = Math.max(0.22, superweaponBeamDuration);
                superweaponBeamTickTimer = superweaponTickSpacing();
            }
            default -> {
                superweaponBeamTimer = 0.0;
                superweaponBeamTickTimer = 0.0;
                superweaponBeamTarget = null;
            }
        }

        List<Projectile> volley = createSuperweaponVolley(dt, aim, target, false);
        if (volley.isEmpty()) return null;
        Projectile first = volley.get(0);
        if (volley.size() > 1) {
            for (int i = 1; i < volley.size(); i++) enqueuePendingSuperweaponShot(volley.get(i));
        }
        return first;
    }

    private double superweaponTickSpacing() {
        return switch (superweaponPattern) {
            case MISSILE_BARRAGE -> Math.max(0.14, superweaponBeamTickInterval * 1.8);
            case KINETIC_SHOTGUN -> Math.max(0.035, superweaponBeamTickInterval);
            case PULSE_BARRAGE -> Math.max(0.03, superweaponBeamTickInterval / SUPERWEAPON_PROJECTILE_RATE_MULT);
            case LANCE_CONE -> Math.max(0.20, superweaponBeamTickInterval);
            default -> Math.max(0.06, superweaponBeamTickInterval);
        };
    }

    private List<Projectile> createSuperweaponVolley(double dt, double aim, Ship target, boolean beamTick) {
        List<Projectile> out = new ArrayList<>();
        switch (superweaponPattern) {
            case DESTABILIZER_PULSE -> addSuperweaponProjectile(out, createDestabilizerPulse(dt, aim));
            case KINETIC_SLUG -> addSuperweaponProjectile(out, createKineticSuperSlug(dt, aim));
            case KINETIC_SHOTGUN -> out.addAll(createKineticShotgunVolley(dt, aim, beamTick));
            case DIRECT_BEAM -> addSuperweaponProjectile(out, createDirectBeamSuperweapon(aim));
            case MISSILE_BARRAGE -> out.addAll(createMissileBarrageVolley(dt, aim, target, beamTick));
            case PULSE_BARRAGE -> addSuperweaponProjectile(out, createSuperweaponPulse(dt, aim, beamTick));
            case LANCE_CONE -> {
                if (beamTick) out.addAll(createLanceConeShardVolley(dt, aim));
                else addSuperweaponProjectile(out, createLanceConePrimaryBeam(aim));
            }
        }
        return out;
    }

    private void addSuperweaponProjectile(List<Projectile> out, Projectile p) {
        if (out == null || p == null) return;
        out.add(p);
    }

    private void enqueuePendingSuperweaponShot(Projectile p) {
        if (p == null) return;
        pendingSuperweaponShots.add(p);
    }

    private void enqueuePendingSuperweaponShots(List<Projectile> shots) {
        if (shots == null || shots.isEmpty()) return;
        for (Projectile p : shots) {
            if (p != null) pendingSuperweaponShots.add(p);
        }
    }

    private Projectile createKineticSuperSlug(double dt, double aim) {
        double sx = x + Math.cos(aim) * (radius + 12.0);
        double sy = y + Math.sin(aim) * (radius + 12.0);
        int damage = Math.max(22, (int) Math.round(superweaponDamage * 0.92));
        double speed = Math.max(980.0, superweaponSpeed * 1.18);
        int life = Math.max(52, (int) Math.round(superweaponLife * 1.22));
        double shotRadius = Math.max(10.0, superweaponRadius * 1.18);
        double blastRadius = Math.max(220.0, superweaponRadius * 12.0);
        Projectile shot = new DisruptorSlug(
                sx,
                sy,
                aim,
                dt,
                speed,
                damage,
                life,
                shotRadius,
                blastRadius,
                faction
        );
        shot.sourceShipId = id;
        return shot;
    }

    private List<Projectile> createKineticShotgunVolley(double dt, double aim, boolean beamTick) {
        if (isRedHyperweaponKineticShotgun() && !superweaponBeamSpreadTargets.isEmpty()) {
            return createTargetedKineticShotgunVolley(dt, aim, beamTick);
        }
        return createUntargetedKineticShotgunVolley(dt, aim, beamTick);
    }

    private List<Projectile> createUntargetedKineticShotgunVolley(double dt, double aim, boolean beamTick) {
        int pelletCount = beamTick ? 9 : 17;
        double spread = Math.toRadians(beamTick ? 34.0 : 48.0);
        int baseDamage = Math.max(8, (int) Math.round(superweaponDamage * (beamTick ? 0.68 : 0.88)));
        double baseSpeed = Math.max(2800.0, superweaponSpeed * (beamTick ? 1.06 : 1.12));
        int life = Math.max(22, (int) Math.round(superweaponLife * (beamTick ? 0.72 : 0.86)));
        double pelletRadius = Math.max(4.8, superweaponRadius * (beamTick ? 0.78 : 0.90));
        int maxHits = Math.max(1, Math.min(3, superweaponMaxHits));
        double muzzle = radius + 18.0;
        double laneSpacing = Math.max(10.0, pelletRadius * 2.8);
        List<Projectile> out = new ArrayList<>(pelletCount);

        for (int i = 0; i < pelletCount; i++) {
            double t = (pelletCount <= 1) ? 0.0 : (i / (double) (pelletCount - 1) - 0.5);
            double abs = Math.abs(t) * 2.0;
            double shotAim = MathUtil.normalizeAngle(aim + t * spread);
            double sx = x + Math.cos(aim) * muzzle + (-Math.sin(aim)) * t * laneSpacing * pelletCount * 0.34;
            double sy = y + Math.sin(aim) * muzzle + (Math.cos(aim)) * t * laneSpacing * pelletCount * 0.34;
            int damage = Math.max(4, (int) Math.round(baseDamage * (1.0 - abs * 0.22)));
            double speed = baseSpeed * (1.0 - abs * 0.08);
            SuperweaponShot shot = new SuperweaponShot(
                    sx,
                    sy,
                    shotAim,
                    dt,
                    speed,
                    damage,
                    life,
                    pelletRadius,
                    maxHits,
                    faction
            );
            shot.sourceShipId = id;
            out.add(shot);
        }
        return out;
    }

    private List<Projectile> createTargetedKineticShotgunVolley(double dt, double fallbackAim, boolean beamTick) {
        int maxTargets = beamTick ? 4 : 6;
        int pelletsPerLane = beamTick ? 2 : 3;
        int baseDamage = Math.max(7, (int) Math.round(superweaponDamage * (beamTick ? 0.50 : 0.62)));
        double baseSpeed = Math.max(3000.0, superweaponSpeed * (beamTick ? 1.08 : 1.15));
        int life = Math.max(24, (int) Math.round(superweaponLife * (beamTick ? 0.78 : 0.92)));
        double pelletRadius = Math.max(4.4, superweaponRadius * (beamTick ? 0.70 : 0.82));
        int maxHits = 1;
        double muzzle = radius + 18.0;
        double trailSpacing = Math.max(8.0, pelletRadius * 2.4);
        List<Projectile> out = new ArrayList<>(maxTargets * pelletsPerLane);

        int lanes = 0;
        for (Ship target : superweaponBeamSpreadTargets) {
            if (lanes >= maxTargets) break;
            if (!isValidSuperweaponTarget(target)) continue;
            double aim = kineticShotgunAimForTarget(target, baseSpeed, dt, fallbackAim);
            double sxBase = x + Math.cos(aim) * muzzle;
            double syBase = y + Math.sin(aim) * muzzle;
            for (int pellet = 0; pellet < pelletsPerLane; pellet++) {
                double trail = pellet * trailSpacing;
                double sx = sxBase - Math.cos(aim) * trail;
                double sy = syBase - Math.sin(aim) * trail;
                int damage = Math.max(4, (int) Math.round(baseDamage * (1.0 - pellet * 0.08)));
                double speed = baseSpeed * (1.0 + pellet * 0.018);
                SuperweaponShot shot = new SuperweaponShot(
                        sx,
                        sy,
                        aim,
                        dt,
                        speed,
                        damage,
                        life,
                        pelletRadius,
                        maxHits,
                        faction
                );
                shot.sourceShipId = id;
                out.add(shot);
            }
            lanes++;
        }

        return out.isEmpty() ? createUntargetedKineticShotgunVolley(dt, fallbackAim, beamTick) : out;
    }

    private double kineticShotgunAimForTarget(Ship target, double projectileSpeed, double dt, double fallbackAim) {
        if (!isValidSuperweaponTarget(target)) return fallbackAim;
        double sx = x + Math.cos(fallbackAim) * (radius + 18.0);
        double sy = y + Math.sin(fallbackAim) * (radius + 18.0);
        double tx = target.x;
        double ty = target.y;
        if (dt > 1e-6) {
            double[] lead = MathUtil.interceptPoint(sx, sy, target.x, target.y,
                    target.vx / dt, target.vy / dt, projectileSpeed);
            tx = lead[0];
            ty = lead[1];
        }
        if (!Double.isFinite(tx) || !Double.isFinite(ty)) return fallbackAim;
        return Math.atan2(ty - sy, tx - sx);
    }

    private List<Ship> selectKineticShotgunSpreadTargets(GameContext ctx, Ship primaryTarget) {
        List<Ship> targets = new ArrayList<>();
        if (!isRedHyperweaponKineticShotgun()) return targets;
        double range = Math.max(UNIVERSAL_SPECIAL_WEAPON_RANGE, superweaponSpeed * Math.max(GameContext.DT, 1e-4) * superweaponLife);
        double range2 = range * range;
        if (isValidSuperweaponTarget(primaryTarget) && MathUtil.dist2(x, y, primaryTarget.x, primaryTarget.y) <= range2) {
            targets.add(primaryTarget);
        }
        if (ctx != null && ctx.ships != null) {
            for (Ship candidate : ctx.ships) {
                if (candidate == this || !isValidSuperweaponTarget(candidate)) continue;
                if (MathUtil.dist2(x, y, candidate.x, candidate.y) > range2) continue;
                boolean alreadyAdded = false;
                for (Ship existing : targets) {
                    if (existing == candidate) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) targets.add(candidate);
            }
        }
        targets.sort((a, b) -> {
            int tierCmp = Integer.compare(kineticShotgunTargetTier(b), kineticShotgunTargetTier(a));
            if (tierCmp != 0) return tierCmp;
            return Double.compare(MathUtil.dist2(x, y, a.x, a.y), MathUtil.dist2(x, y, b.x, b.y));
        });
        int maxTargets = 6;
        if (targets.size() > maxTargets) {
            return new ArrayList<>(targets.subList(0, maxTargets));
        }
        return targets;
    }

    private int kineticShotgunTargetTier(Ship target) {
        if (target == null || target.role == null) return 0;
        ShipRole targetRole = target.role;
        if (targetRole.isMothership()) return 100;
        if (targetRole.isTitan()) return 95;
        return switch (targetRole) {
            case SUPERSHIP -> 90;
            case DREADNOUGHT -> 86;
            case BATTLESHIP -> 82;
            case BATTLECRUISER -> 78;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER -> 68;
            case FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE, CARRIER, DRONE_CARRIER, TRANSPORT -> 52;
            case PICKET, PATROL, STEALTH_SHIP, MINER, HAULER -> 34;
            case FIGHTER, BOMBER, PD_CRAFT, DRONE -> 12;
            case BASE, STATIC_TURRET -> 8;
            default -> 60;
        };
    }

    private boolean isRedHyperweaponKineticShotgun() {
        return role == ShipRole.HYPERWEAPON_TITAN
                && faction == Faction.ENEMY
                && superweaponPattern == SuperweaponPattern.KINETIC_SHOTGUN;
    }

    public boolean isTemporarilyDisabled() {
        return temporaryDisableTimer > 1e-6;
    }

    public double getTemporaryDisableRemaining() {
        return Math.max(0.0, temporaryDisableTimer);
    }

    public boolean isStasisFieldTrapped() {
        return stasisFieldTimer > 1e-6;
    }

    public double getStasisFieldRemaining() {
        return Math.max(0.0, stasisFieldTimer);
    }

    public boolean isDestabilized() {
        return destabilizedTimer > 1e-6;
    }

    public double getDestabilizedRemaining() {
        return Math.max(0.0, destabilizedTimer);
    }

    public void applyDestabilized(double seconds) {
        if (!alive || dying || hp <= 0) return;
        double duration = Math.max(0.0, seconds);
        if (duration <= 0.0) return;
        destabilizedTimer = Math.max(destabilizedTimer, duration);
    }

    public void applyTemporaryDisable(double seconds) {
        if (!alive || dying || hp <= 0) return;
        double adjusted = adjustedDisableDuration(seconds);
        if (adjusted <= 0.0) return;
        temporaryDisableTimer = Math.min(disableDurationCap(), Math.max(temporaryDisableTimer, adjusted));
        vx = 0.0;
        vy = 0.0;
        cancelBattlefieldWarp();
        cancelSuperweaponSequence();
    }

    public void addTemporaryDisable(double seconds) {
        if (!alive || dying || hp <= 0) return;
        double adjusted = adjustedDisableDuration(seconds);
        if (adjusted <= 0.0) return;
        temporaryDisableTimer = Math.min(disableDurationCap(), temporaryDisableTimer + adjusted);
        vx = 0.0;
        vy = 0.0;
        cancelBattlefieldWarp();
        cancelSuperweaponSequence();
    }

    public void enterSurrenderState(double seconds) {
        if (!alive || dying || hp <= 0) return;
        surrendered = true;
        surrenderLockTimer = Math.max(surrenderLockTimer, Math.max(8.0, seconds));
        surrenderSelfDestructTimer = Math.max(surrenderSelfDestructTimer, SURRENDER_SELF_DESTRUCT_SECONDS);
        preserveSurrenderedHullState();
        crewOrder = CrewOrder.DAMAGE_CONTROL;
        aiCommittedTargetId = -1;
        aiTargetCommitTimer = 0.0;
        aiMissileStandoffTimer = 0.0;
        aiMissileStandoffTargetId = -1;
        aiForcedEngageTimer = 0.0;
        aiArrivalFireDelayTimer = Math.max(aiArrivalFireDelayTimer, 1.5);
        applyTemporaryDisable(Math.min(4.0, Math.max(1.5, seconds * 0.2)));
    }

    public void clearSurrenderState() {
        surrendered = false;
        surrenderLockTimer = 0.0;
        surrenderSelfDestructTimer = 0.0;
    }

    private void preserveSurrenderedHullState() {
        if (!surrendered || !alive || dying) return;
        if (hp <= 0) hp = 1;
    }

    private void triggerSurrenderSelfDestruct() {
        if (!alive || dying) return;
        surrendered = false;
        surrenderLockTimer = 0.0;
        hp = 0;
        startDeathSequence();
    }

    public void applyStasisField(double seconds) {
        if (!alive || dying || hp <= 0) return;
        double duration = Math.max(0.0, seconds);
        if (duration <= 0.0) return;
        stasisFieldTimer = Math.max(stasisFieldTimer, duration);
        cancelBattlefieldWarp();
        cancelSuperweaponSequence();
    }

    private double adjustedDisableDuration(double seconds) {
        if (seconds <= 0.0) return 0.0;
        double mul = (faction != null && faction.isYellowLineage()) ? 0.5 : 1.0;
        return seconds * mul;
    }

    private double disableDurationCap() {
        if (faction != null && faction.isYellowLineage()) return 10.0;
        if (faction == Faction.TEAM_C) return 20.0;
        return 15.0;
    }

    private double destabilizedSystemMultiplier() {
        return isDestabilized() ? DESTABILIZED_SYSTEM_MULTIPLIER : 1.0;
    }

    public double missileDamageMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.MISSILE_DAMAGE);
    }

    public double missileCycleRateMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.MISSILE_CYCLE);
    }

    public double ciwsRangeMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.CIWS_RANGE);
    }

    public double effectiveCiwsRange() {
        return Math.max(0.0, ciwsRange * ciwsRangeMultiplier());
    }

    public double strikeCraftTempoMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.STRIKE_CRAFT);
    }

    public double supportFieldMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD);
    }

    public double miningYieldMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.MINING_YIELD);
    }

    public double warpChargeRateMultiplier() {
        return identityStatMultiplier(ShipIdentityRegistry.IdentityStat.WARP_CHARGE);
    }

    public void clearCommandStatMultipliers() {
        Arrays.fill(commandStatMultipliers, 1.0);
    }

    public void applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat stat, double multiplier) {
        if (stat == null || stat == ShipIdentityRegistry.IdentityStat.NONE) return;
        if (!Double.isFinite(multiplier)) return;
        int idx = stat.ordinal();
        if (idx < 0 || idx >= commandStatMultipliers.length) return;
        commandStatMultipliers[idx] = Math.max(commandStatMultipliers[idx], Math.max(1.0, multiplier));
    }

    public boolean hasMissileBattery() {
        if (turrets == null || turrets.isEmpty()) return false;
        for (Turret t : turrets) {
            if (t != null && t.kind == Turret.Kind.MISSILE) return true;
        }
        return false;
    }

    private double identityStatMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        if (stat == null || stat == ShipIdentityRegistry.IdentityStat.NONE) return 1.0;
        double roleMul = roleBonusMultiplierFor(stat);
        double factionMul = factionTraitMultiplier(stat);
        double commandMul = commandStatMultiplier(stat);
        return Math.max(0.10, roleMul * factionMul * commandMul);
    }

    private double commandStatMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        if (stat == null || stat == ShipIdentityRegistry.IdentityStat.NONE) return 1.0;
        int idx = stat.ordinal();
        if (idx < 0 || idx >= commandStatMultipliers.length) return 1.0;
        return Math.max(1.0, commandStatMultipliers[idx]);
    }

    private double roleBonusMultiplierFor(ShipIdentityRegistry.IdentityStat stat) {
        ShipIdentityRegistry.RoleBonus bonus = roleBonusProfile();
        if (bonus.stat != stat) return 1.0;
        return Math.max(1.0, bonus.multiplier);
    }

    private double factionTraitMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        ShipIdentityRegistry.FactionTraitId trait = factionTrait().id;
        if (trait == ShipIdentityRegistry.FactionTraitId.NONE) return 1.0;
        return switch (trait) {
            case COMMAND_NET -> commandNetMultiplier(stat);
            case KINETIC_MOMENTUM -> kineticMomentumMultiplier(stat);
            case AEGIS_LATTICE -> aegisLatticeMultiplier(stat);
            case VIPER_ASSAULT -> viperAssaultMultiplier(stat);
            case NONE -> 1.0;
        };
    }

    private double commandNetMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        double strength = commandNetStrength();
        return switch (stat) {
            case SENSOR_RANGE -> 1.0 + 0.12 * strength;
            case SHIELD_REGEN -> 1.0 + 0.10 * strength;
            case SUPPORT_FIELD -> 1.0 + 0.12 * strength;
            case SUPERWEAPON_RECHARGE -> 1.0 + 0.08 * strength;
            case WARP_CHARGE -> 1.0 + 0.06 * strength;
            default -> 1.0;
        };
    }

    private double kineticMomentumMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        double strength = MathUtil.clamp(kineticMomentumTimer / KINETIC_MOMENTUM_WINDOW_SECONDS, 0.0, 1.0);
        return switch (stat) {
            case WEAPON_DAMAGE -> 1.0 + 0.10 * strength;
            case WEAPON_CYCLE -> 1.0 + 0.12 * strength;
            case MISSILE_DAMAGE -> 1.0 + 0.06 * strength;
            case MISSILE_CYCLE -> 1.0 + 0.08 * strength;
            case MOBILITY -> 1.0 + 0.06 * strength;
            default -> 1.0;
        };
    }

    private double aegisLatticeMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        double strength = aegisLatticeStrength();
        return switch (stat) {
            case SHIELD_REGEN -> 1.0 + 0.16 * strength;
            case SENSOR_RANGE -> 1.0 + 0.08 * strength;
            case CIWS_RANGE -> 1.0 + 0.10 * strength;
            case WEAPON_CYCLE -> 1.0 + 0.06 * strength;
            default -> 1.0;
        };
    }

    private double viperAssaultMultiplier(ShipIdentityRegistry.IdentityStat stat) {
        double strength = viperAssaultStrength();
        boolean missileShip = hasMissileBattery();
        return switch (stat) {
            case MISSILE_DAMAGE -> 1.0 + 0.10 * strength;
            case MISSILE_CYCLE -> 1.0 + 0.12 * strength;
            case WARP_CHARGE -> 1.0 + 0.08 * strength;
            case WEAPON_CYCLE -> missileShip ? 1.0 : (1.0 + 0.06 * strength);
            case MOBILITY -> 1.0 + 0.05 * strength;
            default -> 1.0;
        };
    }

    private double commandNetStrength() {
        double bridge = systemHealthFraction(InternalSystem.BRIDGE);
        double sensors = systemHealthFraction(InternalSystem.SENSORS);
        double readiness = MathUtil.clamp(crewReadiness, 0.0, 1.0);
        double disruptionPenalty = MathUtil.clamp(roomDisruptionFraction() * 0.80, 0.0, 0.75);
        double firePenalty = MathUtil.clamp(totalFireIntensity() * 0.10, 0.0, 0.60);
        double calm = MathUtil.clamp(1.0 - disruptionPenalty - firePenalty, 0.0, 1.0);
        return MathUtil.clamp((bridge * 0.42 + sensors * 0.33 + readiness * 0.25) * calm, 0.0, 1.0);
    }

    public double commandLinkQuality() {
        return commandNetStrength();
    }

    private double aegisLatticeStrength() {
        double stripIntegrity = MathUtil.clamp(activeTeamCShieldStripCount() / (double) SHIELD_FACE_COUNT, 0.0, 1.0);
        double effectiveCap = effectiveShieldCapacityMax();
        double shieldFrac = (effectiveCap <= 1e-6) ? 0.0 : MathUtil.clamp(shield / effectiveCap, 0.0, 1.0);
        double shieldSystems = systemHealthFraction(InternalSystem.SHIELDS);
        return MathUtil.clamp(stripIntegrity * 0.45 + shieldFrac * 0.40 + shieldSystems * 0.15, 0.0, 1.0);
    }

    private double viperAssaultStrength() {
        double hullFrac = (hpMax <= 0) ? 0.0 : MathUtil.clamp(hp / (double) hpMax, 0.0, 1.0);
        double weapons = systemHealthFraction(InternalSystem.WEAPONS);
        double recentPressure = 1.0 - MathUtil.clamp(secondsSinceDamage() / 5.0, 0.0, 1.0);
        return MathUtil.clamp(hullFrac * 0.50 + weapons * 0.30 + recentPressure * 0.20, 0.0, 1.0);
    }

    private void cancelSuperweaponSequence() {
        superweaponCharging = false;
        superweaponChargeTimer = 0.0;
        pendingSuperweaponShots.clear();
        queuedSuperweaponAim = Double.NaN;
        queuedSuperweaponTarget = null;
        superweaponBeamTimer = 0.0;
        superweaponBeamTickTimer = 0.0;
        superweaponBeamAim = Double.NaN;
        superweaponBeamTarget = null;
    }

    private Projectile createDirectBeamSuperweapon(double aim) {
        angle = aim;
        boolean greenHyperweaponTitan = isGreenHyperweaponTitan();
        double beamDurationSec = greenHyperweaponTitan
                ? Math.max(1.10, superweaponBeamDuration * 2.35)
                : Math.max(0.65, superweaponBeamDuration * 2.0);
        int beamLife = Math.max(8, (int) Math.round(beamDurationSec / Math.max(GameContext.DT, 1e-4)));
        double totalDamage = resolveDirectBeamSuperweaponTotalDamage();
        double beamDps = totalDamage / Math.max(GameContext.DT, beamLife * GameContext.DT);
        double beamLength = UNIVERSAL_SPECIAL_WEAPON_RANGE;
        double beamWidth = greenHyperweaponTitan
                ? Math.max(22.0, superweaponRadius * 2.65)
                : Math.max(10.0, superweaponRadius * 1.9);
        double muzzleOffset = radius + 12.0;
        Projectile beam = new PhaserBeam(this, aim, beamLength, beamWidth, beamDps, beamLife, muzzleOffset, faction);
        beam.sourceShipId = id;
        return beam;
    }

    private Projectile createLanceConePrimaryBeam(double aim) {
        angle = aim;
        double beamDurationSec = Math.max(0.42, superweaponBeamDuration * 1.65);
        int beamLife = Math.max(6, (int) Math.round(beamDurationSec / Math.max(GameContext.DT, 1e-4)));
        double totalDamage = Math.max(1.0, superweaponDamage * 2.1);
        double beamDps = totalDamage / Math.max(GameContext.DT, beamLife * GameContext.DT);
        double beamLength = hyperLanceBeamLength();
        double beamWidth = Math.max(16.0, superweaponRadius * 2.5);
        double muzzleOffset = radius + 14.0;
        Projectile beam = new PhaserBeam(this, aim, beamLength, beamWidth, beamDps, beamLife, muzzleOffset, faction);
        beam.sourceShipId = id;
        return beam;
    }

    private Projectile createDestabilizerPulse(double dt, double aim) {
        double sx = x + Math.cos(aim) * (radius + 12.0);
        double sy = y + Math.sin(aim) * (radius + 12.0);
        int hullDamage = Math.max(36, (int) Math.round(superweaponDamage * 0.82));
        double shieldDamage = Math.max(18.0, superweaponDamage * 0.46);
        double pulseSpeed = Math.max(620.0, superweaponSpeed * 0.84);
        int pulseLife = Math.max(48, (int) Math.round(superweaponLife * 1.10));
        double pulseRadius = Math.max(18.0, superweaponRadius * 1.65);
        double blastRadius = Math.max(360.0, pulseRadius * 12.4);
        double destabilizeSeconds = 8.5;
        Projectile pulse = new DestabilizerPulse(
                sx,
                sy,
                aim,
                dt,
                pulseSpeed,
                hullDamage,
                pulseLife,
                pulseRadius,
                blastRadius,
                shieldDamage,
                destabilizeSeconds,
                faction
        );
        pulse.sourceShipId = id;
        return pulse;
    }

    private List<Projectile> createLanceConeShardVolley(double dt, double aim) {
        int shardCount = 7;
        double spread = Math.toRadians(52.0);
        int damage = Math.max(12, (int) Math.round(superweaponDamage * Math.max(0.28, superweaponBeamDamageScale * 1.55)));
        double speed = Math.max(560.0, superweaponSpeed * 0.56);
        int life = Math.max(32, (int) Math.round(superweaponLife * 0.44));
        double shotRadius = Math.max(8.0, superweaponRadius * 0.88);
        int maxHits = Math.max(2, (int) Math.round(superweaponMaxHits * 0.16));
        double beamLength = hyperLanceBeamLength();
        double muzzle = radius + 14.0;
        double anchor = muzzle + Math.max(120.0, beamLength * 0.74);
        double anchorX = x + Math.cos(aim) * anchor;
        double anchorY = y + Math.sin(aim) * anchor;
        List<Projectile> out = new ArrayList<>(shardCount);

        for (int i = 0; i < shardCount; i++) {
            double t = (shardCount <= 1) ? 0.0 : (i / (double) (shardCount - 1) - 0.5);
            double shotAim = MathUtil.normalizeAngle(aim + t * spread);
            double lateral = t * Math.max(18.0, superweaponRadius * 2.4);
            double sx = anchorX + (-Math.sin(aim)) * lateral;
            double sy = anchorY + (Math.cos(aim)) * lateral;
            SuperweaponShot shot = new SuperweaponShot(
                    sx,
                    sy,
                    shotAim,
                    dt,
                    speed,
                    damage,
                    life,
                    shotRadius,
                    maxHits,
                    faction
            );
            shot.sourceShipId = id;
            out.add(shot);
        }
        return out;
    }

    private List<Projectile> createMissileBarrageVolley(double dt, double aim, Ship target, boolean beamTick) {
        if (isYellowHyperweaponTitan()) {
            if (beamTick) return java.util.Collections.emptyList();
            return java.util.Collections.singletonList(createNuclearWarhead(dt, aim, target));
        }
        int missileCount = beamTick ? 4 : 9;
        double spread = beamTick ? Math.toRadians(28.0) : Math.toRadians(46.0);
        int damage = Math.max(6, (int) Math.round(superweaponDamage * (beamTick ? 0.42 : 0.56)));
        double speed = Math.max(260.0, superweaponSpeed * (beamTick ? 0.34 : 0.39));
        double turnRate = Math.toRadians(beamTick ? 218.0 : 246.0);
        int life = Math.max(120, (int) Math.round(superweaponLife * (beamTick ? 1.35 : 1.75)));
        double missileRadius = Math.max(9.0, superweaponRadius * (beamTick ? 0.95 : 1.10));
        double muzzle = radius + 12.0;
        List<Projectile> out = new ArrayList<>(missileCount);

        Ship lock = isValidSuperweaponTarget(target) ? target : null;
        for (int i = 0; i < missileCount; i++) {
            double t = (missileCount <= 1) ? 0.0 : (i / (double) (missileCount - 1) - 0.5);
            double shotAim = MathUtil.normalizeAngle(aim + t * spread);
            double lateral = t * Math.max(8.0, radius * 0.8);
            double sx = x + Math.cos(aim) * muzzle + (-Math.sin(aim)) * lateral;
            double sy = y + Math.sin(aim) * muzzle + (Math.cos(aim)) * lateral;
            Missile missile = new Missile(sx, sy, shotAim, lock, dt, speed, turnRate, damage, life, missileRadius, faction);
            missile.sourceShipId = id;
            out.add(missile);
        }
        return out;
    }

    private Missile createNuclearWarhead(double dt, double aim, Ship target) {
        double muzzle = radius + 14.0;
        double sx = x + Math.cos(aim) * muzzle;
        double sy = y + Math.sin(aim) * muzzle;
        Ship lock = isValidSuperweaponTarget(target) ? target : null;
        int damage = Math.max(18, (int) Math.round(superweaponDamage * 0.28));
        double speed = Math.max(320.0, superweaponSpeed);
        double turnRate = Math.toRadians(96.0);
        int life = Math.max(180, superweaponLife);
        double missileRadius = Math.max(16.0, superweaponRadius * 1.18);
        Missile missile = new Missile(sx, sy, aim, lock, dt, speed, turnRate, damage, life, missileRadius, faction);
        missile.interceptHp = Missile.HEAVY_INTERCEPT_HP + 3;
        missile.blastRadius = Math.max(260.0, missileRadius * 14.0);
        missile.splashDamageMul = 0.0;
        missile.sourceShipId = id;
        return missile;
    }

    private boolean isValidSuperweaponTarget(Ship target) {
        if (target == null) return false;
        return target.alive
                && !target.dying
                && target.hp > 0
                && !TargetingSystem.isCiwsOnlyTarget(target)
                && (target.faction == null || !target.faction.isFriendlyTo(faction));
    }

    private SuperweaponShot createSuperweaponPulse(double dt, double aim, boolean beamTick) {
        double sx = x + Math.cos(aim) * (radius + 10.0);
        double sy = y + Math.sin(aim) * (radius + 10.0);

        int damage = superweaponDamage;
        double speed = superweaponSpeed;
        int life = superweaponLife;
        double radius = superweaponRadius;
        int maxHits = superweaponMaxHits;

        if (beamTick) {
            damage = Math.max(1, (int) Math.round(superweaponDamage * superweaponBeamDamageScale));
            speed = superweaponSpeed * 1.12;
            life = Math.max(18, (int) Math.round(superweaponLife * 0.42));
            radius = superweaponRadius * 0.92;
            maxHits = Math.max(6, (int) Math.round(superweaponMaxHits * 0.45));
        }

        SuperweaponShot shot = new SuperweaponShot(
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
        shot.sourceShipId = id;
        return shot;
    }

    private double resolveDirectBeamSuperweaponTotalDamage() {
        if (isGreenHyperweaponTitan()) {
            return Math.max(referencePulseBarrageFullHitDamage() * 2.8, superweaponDamage * 6.0);
        }
        if (faction == Faction.TEAM_C) {
            return referencePulseBarrageFullHitDamage();
        }
        return Math.max(1.0, superweaponDamage * 3.6);
    }

    private boolean isGreenHyperweaponTitan() {
        return role == ShipRole.HYPERWEAPON_TITAN && faction == Faction.TEAM_C;
    }

    private boolean isYellowHyperweaponTitan() {
        return role == ShipRole.HYPERWEAPON_TITAN && faction != null && faction.isYellowLineage();
    }

    private double hyperLanceBeamLength() {
        return UNIVERSAL_SPECIAL_WEAPON_RANGE;
    }

    private static double referencePulseBarrageFullHitDamage() {
        final double pulseDamage = 96.0;
        final double pulseDuration = 1.15;
        final double pulseBeamScale = 0.36;
        final double pulseTickSpacing = Math.max(0.03, 0.11 / SUPERWEAPON_PROJECTILE_RATE_MULT);
        int followUpShots = estimateSuperweaponFollowUpShots(pulseDuration, pulseTickSpacing);
        int followUpDamage = Math.max(1, (int) Math.round(pulseDamage * pulseBeamScale));
        return pulseDamage + followUpShots * followUpDamage;
    }

    private static int estimateSuperweaponFollowUpShots(double durationSeconds, double tickSpacingSeconds) {
        double beamTimer = Math.max(0.0, durationSeconds);
        double tickTimer = Math.max(GameContext.DT, tickSpacingSeconds);
        int shots = 0;
        int guard = 0;
        while (beamTimer > 0.0 && guard++ < 10000) {
            beamTimer -= GameContext.DT;
            tickTimer -= GameContext.DT;
            if (beamTimer < 0.0) beamTimer = 0.0;
            if (beamTimer > 0.0 && tickTimer <= 0.0) {
                shots++;
                tickTimer = Math.max(GameContext.DT, tickSpacingSeconds);
            }
        }
        return shots;
    }
}
