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

    // Entities
    public Player player;
    public final List<Ship> ships = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Asteroid> asteroids = new ArrayList<>();
    public final List<Salvage> salvage = new ArrayList<>();

    // Bases
    public Ship allyBase;
    public Ship enemyBase;
    public final java.util.EnumMap<Faction, Ship> teamBases = new java.util.EnumMap<>(Faction.class);

    // Camera
    public double camX = 0;
    public double camY = 0;
    public static final double DEFAULT_ZOOM = 1.0;
    public static final double MIN_ZOOM = 0.50;
    public static final double MAX_ZOOM = 3.00;
    public double zoom = DEFAULT_ZOOM;

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
    public int powerManagementFocus = 0; // 0=engines 1=shields 2=weapons 3=systems

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
    public CrewStation activeCrewStation = CrewStation.CAPTAIN;
    public HelmMode helmMode = HelmMode.INTERCEPT;
    public TacticalMode tacticalMode = TacticalMode.DEFENSIVE;
    public EngineeringMode engineeringMode = EngineeringMode.BALANCED;
    public CaptainDirective captainDirective = CaptainDirective.BALANCED;
    public HudDetail hudDetail = HudDetail.FULL;
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

    // Waypoint / pings
    public double waypointX = Double.NaN;
    public double waypointY = Double.NaN;
    public final List<Renderer.MapPing> mapPings = new ArrayList<>();

    // Economy
    public int credits = 10000;
    public static final int ORE_PRICE = 4;
    public int resourceGoal = 600;

    // Base upgrades
    public final Map<Ship, BaseUpgrades> baseUpgrades = new HashMap<>();

    // Random events / modifiers
    public double nextEventTimer = 18.0;
    public String eventBanner = "";
    public double eventBannerT = 0.0;

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
        if (this.config.mode == GameMode.CAMPAIGN_OPS) {
            this.campaignUnlockProfile = CampaignUnlockProfile.load();
        }
    }
}
