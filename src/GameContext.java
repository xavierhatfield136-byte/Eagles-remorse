import java.util.*;
/**
 * Shared mutable game state container.
 * All systems operate on this instead of owning state in GamePanel.
 */
public class GameContext {
    // Config / world
    public final GameConfig config;
    public final int WORLD_W;
    public final int WORLD_H;
    public final Random rng;

    // Time step
    public static final double DT = 1.0 / 60.0;

    // Core state
    public GameState state = GameState.RUNNING;
    public boolean gameOver = false;
    public String gameOverText = "";
    public static final double DEFAULT_PLAYER_RESPAWN_DELAY_SECONDS = 4.0;
    public boolean playerRespawnPending = false;
    public double playerRespawnTimer = 0.0;
    public double playerRespawnDelaySeconds = DEFAULT_PLAYER_RESPAWN_DELAY_SECONDS;

    // Entities
    public Player player;
    public final List<Ship> ships = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Asteroid> asteroids = new ArrayList<>();
    public final List<Salvage> salvage = new ArrayList<>();
    public final List<DamageEvent> damageEvents = new ArrayList<>();
    public final List<AudioEvent> audioEvents = new ArrayList<>();

    // Bases
    public Ship allyBase;
    public Ship enemyBase;
    public final java.util.EnumMap<Faction, Ship> teamBases = new java.util.EnumMap<>(Faction.class);

    // Camera
    public double camX = 0;
    public double camY = 0;
    public static final double DEFAULT_ZOOM = 1.0;
    public static final double MIN_ZOOM = 0.02;
    public static final double MAX_ZOOM = 50.0;
    public double zoom = DEFAULT_ZOOM;
    public double cameraOffsetX = 0.0;
    public double cameraOffsetY = 0.0;
    public boolean cameraPanUp = false;
    public boolean cameraPanDown = false;
    public boolean cameraPanLeft = false;
    public boolean cameraPanRight = false;

        // Cursor world position (updated each tick)
    public double cursorWorldX = 0;
    public double cursorWorldY = 0;

// Input-derived actions
    // Manual fire input (mouse / keyboard). These are blocked by overlays.
    public boolean firingPrimaryManual = false;
    public boolean firingSecondaryManual = false;
    // Tactical station automation fire requests. These continue while overlays are open.
    public boolean firingPrimaryAuto = false;
    public boolean firingSecondaryAuto = false;
    public boolean miningKeyDown = false;

    // Targeting
    public Ship lockedTarget = null;
    public boolean autoLockTurrets = true;
    public int lockedIndexHint = 0;

    // UI overlays
    public boolean shopOpen = false;
    public boolean baseMenuOpen = false;
    public boolean mapOpen = false;
    public boolean powerManagementOpen = false;
    public boolean crewStationsOpen = false;
    public boolean flightDeckOpen = false;
    public int powerManagementFocus = 0; // 0=propulsion 1=shield 2=tactical 3=sensor 4=engineering 5=auxiliary
    public int flightDeckFocus = 0;

    public enum CrewStation {
        CAPTAIN,
        HELM,
        TACTICAL,
        ENGINEERING,
        SCIENCE
    }
    public enum HelmMode {
        INTERCEPT,
        ORBIT,
        MAINTAIN_RANGE,
        EVASIVE
    }
    public enum TacticalMode {
        HOLD_FIRE,
        DEFENSIVE,
        AGGRESSIVE
    }
    public enum EngineeringMode {
        BALANCED,
        ATTACK,
        DEFENSE,
        DAMAGE_CONTROL
    }
    public enum CaptainDirective {
        BALANCED,
        ATTACK,
        DEFENSE,
        EMERGENCY,
        MINE,
        ESCORT,
        DEFEND,
        REPAIR,
        RTB
    }
    public enum FleetCommand {
        AUTO,
        FORM_UP,
        ATTACK,
        DEFEND,
        ESCORT,
        REPAIR,
        RTB,
        RETREAT,
        MINE
    }
    public enum FleetFormation {
        WEDGE,
        LINE,
        SCREEN
    }
    public enum HudDetail {
        FULL,
        COMPACT,
        MINIMAL
    }
    public enum XrayFilterMode {
        ALL,
        DAMAGE,
        HAZARD,
        POWER,
        DISABLED
    }
    public CrewStation activeCrewStation = CrewStation.CAPTAIN;
    public HelmMode helmMode = HelmMode.INTERCEPT;
    public TacticalMode tacticalMode = TacticalMode.DEFENSIVE;
    public EngineeringMode engineeringMode = EngineeringMode.BALANCED;
    public CaptainDirective captainDirective = CaptainDirective.BALANCED;
    public HudDetail hudDetail = HudDetail.FULL;
    public XrayFilterMode xrayFilterMode = XrayFilterMode.ALL;
    public ShipRoomLayout.RoomId xrayFocusedRoom = null;
    public ShipRoomLayout.RoomId xrayHoveredRoom = null;
    public boolean captainAutomation = false;
    public boolean helmAutomation = false;
    public boolean tacticalAutomation = false;
    public boolean engineeringAutomation = false;
    public boolean scienceAutomation = true;
    public boolean scienceJamming = false;
    public double helmDesiredRange = 480.0;
    public boolean miningAuto = false;
    public FleetCommand alliedFleetCommand = FleetCommand.AUTO;
    public FleetFormation alliedFleetFormation = FleetFormation.WEDGE;
    public final Map<Integer, FleetCommand> shipFleetCommandOverrides = new HashMap<>();
    public final java.util.EnumMap<Faction, Ship> fleetCommandShips = new java.util.EnumMap<>(Faction.class);
    public final java.util.EnumMap<Faction, Ship> fleetSharedTargets = new java.util.EnumMap<>(Faction.class);
    public boolean playerTeleportCharging = false;
    public double playerTeleportChargeRemaining = 0.0;
    public Faction shootingRangeTargetFaction = Faction.ENEMY;
    public double shootingRangeOriginX = Double.NaN;
    public double shootingRangeOriginY = Double.NaN;

    // Waypoint / pings
    public double waypointX = Double.NaN;
    public double waypointY = Double.NaN;
    public final List<Renderer.MapPing> mapPings = new ArrayList<>();

    // Economy
    public int credits = 10000;
    public static final int ORE_PRICE = 4;
    public static final double CREDIT_EARNINGS_MUL = 1.5;
    public int resourceGoal = 600;

    public static int scaleCreditEarnings(int baseCredits) {
        if (baseCredits <= 0) return 0;
        return (int) Math.round(baseCredits * CREDIT_EARNINGS_MUL);
    }

    // Base upgrades
    public final Map<Ship, BaseUpgrades> baseUpgrades = new HashMap<>();

    // Random events / modifiers
    public double nextEventTimer = 18.0;
    public String eventBanner = "";
    public double eventBannerT = 0.0;
    public double hazardHintCooldown = 0.0;
    public double hazardCriticalCooldown = 0.0;
    public double cursorScreenX = 0.0;
    public double cursorScreenY = 0.0;
    public String voiceCaption = "";
    public double voiceCaptionT = 0.0;
    public boolean voiceCaptionsEnabled = true;
    public CrewStation voiceMixFocus = CrewStation.CAPTAIN;
    public final java.util.EnumMap<CrewStation, Double> voiceRoleVolumes = new java.util.EnumMap<>(CrewStation.class);
    public final java.util.EnumMap<CrewStation, Integer> portraitExpressionLevel = new java.util.EnumMap<>(CrewStation.class);
    public final java.util.EnumMap<CrewStation, Double> portraitExpressionTimerSec = new java.util.EnumMap<>(CrewStation.class);
    public double orePriceMul = 1.0;
    public double orePriceT = 0.0;
    public double miningMul = 1.0;
    public double miningT = 0.0;

    // Permanent base upgrade multipliers (stack with temporary events)
    public double orePriceBaseMul = 1.0;
    public double miningBaseMul = 1.0;

    // Waves
    public double enemyWaveTimer = 2.0;
    public double minerReinforcementTimer = 20.0;

    // Campaign progression (CAMPAIGN_OPS)
    public CampaignSystem.CampaignState campaign = null;
    public CampaignUnlockProfile campaignUnlockProfile = null;

    // Last Stand progression
    public double lastStandElapsed = 0.0;
    public double lastStandGoalSec = 15.0 * 60.0;
    public double lastStandWaveTimer = 6.0;
    public int lastStandWaveIndex = 0;

    // Runtime performance telemetry (debug overlay / frame pacing checks).
    public double perfFps = 0.0;
    public double perfFrameMs = 0.0;
    public double perfFrameJitterMs = 0.0;
    public double perfUpdateMs = 0.0;
    public double perfRenderMs = 0.0;
    public int perfUpdateSteps = 0;
    public int perfDroppedUpdates = 0;

    public GameContext(GameConfig config) {
        this.config = (config == null)
                ? new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, System.nanoTime(), false)
                : config;
        this.WORLD_W = this.config.worldW;
        this.WORLD_H = this.config.worldH;
        this.rng = new Random(this.config.seed);
        initAudioPreferences();
        if (this.config.mode == GameMode.CAMPAIGN_OPS) {
            this.campaignUnlockProfile = CampaignUnlockProfile.load();
        }
    }

    private void initAudioPreferences() {
        for (CrewStation station : CrewStation.values()) {
            voiceRoleVolumes.put(station, 1.0);
            portraitExpressionLevel.put(station, 0);
            portraitExpressionTimerSec.put(station, 0.0);
        }
        MenuSettingsStore.MenuSettings persisted = MenuSettingsStore.load();
        voiceCaptionsEnabled = persisted.voiceCaptionsEnabled;
        voiceRoleVolumes.put(CrewStation.CAPTAIN, clampVoiceVol(persisted.voiceVolumeCaptain));
        voiceRoleVolumes.put(CrewStation.HELM, clampVoiceVol(persisted.voiceVolumeHelm));
        voiceRoleVolumes.put(CrewStation.TACTICAL, clampVoiceVol(persisted.voiceVolumeTactical));
        voiceRoleVolumes.put(CrewStation.ENGINEERING, clampVoiceVol(persisted.voiceVolumeEngineering));
        voiceRoleVolumes.put(CrewStation.SCIENCE, clampVoiceVol(persisted.voiceVolumeScience));
    }

    public double voiceRoleVolume(CrewStation station) {
        if (station == null) return 1.0;
        return clampVoiceVol(voiceRoleVolumes.getOrDefault(station, 1.0));
    }

    public void setVoiceRoleVolume(CrewStation station, double value) {
        if (station == null) return;
        voiceRoleVolumes.put(station, clampVoiceVol(value));
    }

    public int portraitExpression(CrewStation station) {
        if (station == null) return 0;
        return MathUtil.clamp(portraitExpressionLevel.getOrDefault(station, 0), 0, 3);
    }

    public void setPortraitExpression(CrewStation station, int expression, double holdSec) {
        if (station == null) return;
        portraitExpressionLevel.put(station, MathUtil.clamp(expression, 0, 3));
        portraitExpressionTimerSec.put(station, Math.max(0.0, holdSec));
    }

    public void decayPortraitExpressions(double dt) {
        double step = Math.max(0.0, dt);
        if (step <= 0.0) return;
        for (CrewStation station : CrewStation.values()) {
            double t = Math.max(0.0, portraitExpressionTimerSec.getOrDefault(station, 0.0) - step);
            portraitExpressionTimerSec.put(station, t);
            if (t <= 0.0) {
                portraitExpressionLevel.put(station, 0);
            }
        }
    }

    private static double clampVoiceVol(double v) {
        if (!Double.isFinite(v)) return 1.0;
        return MathUtil.clamp(v, 0.0, 2.0);
    }
}


