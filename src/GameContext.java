import app.config.GameConfig;
import app.config.GameMode;
import app.config.ExperienceSettings;
import app.persistence.CampaignUnlockProfile;
import app.state.PerfTelemetry;
import java.util.*;
/**
 * Shared mutable game state container.
 * All systems operate on this instead of owning state in GamePanel.
 */
public class GameContext {
    public static final class FleetCommMessage {
        public final Faction faction;
        public final String channel;
        public final String text;
        public final boolean external;
        public double ttl;

        public FleetCommMessage(Faction faction, String channel, String text, double ttl) {
            this(faction, channel, text, ttl, false);
        }

        public FleetCommMessage(Faction faction, String channel, String text, double ttl, boolean external) {
            this.faction = faction;
            this.channel = (channel == null || channel.isBlank()) ? "FLEET" : channel;
            this.text = (text == null || text.isBlank()) ? "Traffic spike." : text;
            this.external = external;
            this.ttl = Math.max(0.2, ttl);
        }
    }

    // Config / world
    public final GameConfig config;
    public final int WORLD_W;
    public final int WORLD_H;
    public final Random rng;
    public final ExperienceSettings experience;

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
    public final List<FleetCommMessage> fleetCommLog = new ArrayList<>();
    public final EntityQueryIndex entityQuery = new EntityQueryIndex();
    public boolean multiplayerBattle = false;
    public MultiplayerAuthorityMode multiplayerAuthorityMode = MultiplayerAuthorityMode.NONE;
    public int multiplayerLocalSlotId = MultiplayerRulesV1.HOST_SLOT_ID;
    public final Set<Integer> multiplayerPlayerControlledShipIds = new HashSet<>();
    public MultiplayerBattleRuntime multiplayerBattleRuntime = null;
    public MultiplayerInGameDuelSession multiplayerInGameSession = null;
    public MultiplayerBattleThreadGuard multiplayerClientSnapshotThreadGuard = null;
    public int multiplayerLocalNetworkShipId = 0;
    public final Map<Integer, Integer> multiplayerNetworkShipIdToLocalShipId = new HashMap<>();
    public String multiplayerLobbyId = "lobby:local";
    public String multiplayerMatchId = "match:local";
    public String multiplayerSessionNonce = "";
    public long multiplayerLockedConfigRevision = 0L;
    public long multiplayerMatchStartTick = 0L;
    public String multiplayerLocalPlayerId = "";

    // Bases
    public Ship allyBase;
    public Ship enemyBase;
    public final java.util.EnumMap<Faction, Ship> teamBases = new java.util.EnumMap<>(Faction.class);
    public final FogOfWarSystem.State fogOfWar;

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
    public boolean firingPrimaryManualLatched = false;
    public boolean firingSecondaryManual = false;
    public boolean firingSecondaryManualLatched = false;
    // Tactical station automation fire requests. These continue while overlays are open.
    public boolean firingPrimaryAuto = false;
    public boolean firingSecondaryAuto = false;
    public boolean miningKeyDown = false;

    // Targeting
    public Ship lockedTarget = null;
    public boolean autoLockTurrets = true;
    public int lockedIndexHint = 0;
    public Ship playerAutoTargetCache = null;
    public long playerAutoTargetCacheFrame = Long.MIN_VALUE;

    public final UiState ui = new UiState();

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
        ASSAULT,
        SCREEN,
        DEFENSIVE,
        OFFENSIVE
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
    public final CommandState command = new CommandState();

    // Economy
    public int credits = 10000;
    public static final int ORE_PRICE = 4;
    public static final double CREDIT_EARNINGS_MUL = 1.5;
    public static final double CAMPAIGN_CREDIT_REWARD_MUL = 2.0;
    public int resourceGoal = 600;

    public static int scaleCreditEarnings(int baseCredits) {
        if (baseCredits <= 0) return 0;
        return (int) Math.round(baseCredits * CREDIT_EARNINGS_MUL);
    }

    public static int scaleCreditReward(int baseCredits) {
        return (int) Math.round(scaleCreditEarnings(baseCredits) * CAMPAIGN_CREDIT_REWARD_MUL);
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
    public double battleElapsed = 0.0;

    // Campaign progression (CAMPAIGN_OPS)
    public CampaignSystem.CampaignState campaign = null;
    public CampaignUnlockProfile campaignUnlockProfile = null;
    public final BattleResultRecorder battleResultRecorder = new BattleResultRecorder();

    // Last Stand progression
    public double lastStandElapsed = 0.0;
    public double lastStandGoalSec = 15.0 * 60.0;
    public double lastStandWaveTimer = 6.0;
    public int lastStandWaveIndex = 0;

    // Runtime performance telemetry (debug overlay / frame pacing checks).
    public final PerfTelemetry perf = new PerfTelemetry();

    public GameContext(GameConfig config) {
        this.config = (config == null)
                ? new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, System.nanoTime(), false)
                : config;
        this.WORLD_W = resolvedWorldWidth(this.config);
        this.WORLD_H = resolvedWorldHeight(this.config);
        this.rng = new Random(this.config.seed);
        this.experience = this.config.experience.copy();
        if (this.experience.commandComplexity <= 0.80) {
            command.captainAutomation = true;
            command.helmAutomation = true;
            command.tacticalAutomation = true;
            command.engineeringAutomation = true;
        }
        this.fogOfWar = new FogOfWarSystem.State(this.WORLD_W, this.WORLD_H);
        Faction.clearCampaignAlliances();
        ui.initAudioPreferences();
        if (this.config.mode == GameMode.CAMPAIGN_OPS || this.config.mode == GameMode.FLEET) {
            this.campaignUnlockProfile = CampaignUnlockProfile.load();
        }
    }

    private static int resolvedWorldWidth(GameConfig config) {
        if (config != null && (config.mode == GameMode.CAMPAIGN_OPS || config.mode == GameMode.FLEET)) {
            return Math.max(Math.max(1, config.worldW), CampaignSystem.recommendedWorldWidth(config));
        }
        return BattlefieldSectorSystem.recommendedWorldWidth(config);
    }

    private static int resolvedWorldHeight(GameConfig config) {
        if (config != null && (config.mode == GameMode.CAMPAIGN_OPS || config.mode == GameMode.FLEET)) {
            return Math.max(Math.max(1, config.worldH), CampaignSystem.recommendedStrategicTheaterHeight(config));
        }
        return BattlefieldSectorSystem.recommendedWorldHeight(config);
    }

    public double voiceRoleVolume(CrewStation station) {
        return ui.voiceRoleVolume(station);
    }

    public void setVoiceRoleVolume(CrewStation station, double value) {
        ui.setVoiceRoleVolume(station, value);
    }

    public int portraitExpression(CrewStation station) {
        return ui.portraitExpression(station);
    }

    public void setPortraitExpression(CrewStation station, int expression, double holdSec) {
        ui.setPortraitExpression(station, expression, holdSec);
    }

    public void decayPortraitExpressions(double dt) {
        ui.decayPortraitExpressions(dt);
    }
}
