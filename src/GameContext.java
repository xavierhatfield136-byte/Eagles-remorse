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

    // Camera
    public double camX = 0;
    public double camY = 0;

        // Cursor world position (updated each tick)
    public double cursorWorldX = 0;
    public double cursorWorldY = 0;

// Input-derived actions
    public boolean firingPrimary = false;
    public boolean firingSecondary = false;
    public boolean miningKeyDown = false;

    // Targeting
    public Ship lockedTarget = null;
    public boolean autoLockTurrets = true;
    public int lockedIndexHint = 0;

    // UI overlays
    public boolean shopOpen = false;
    public boolean baseMenuOpen = false;
    public boolean mapOpen = false;

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

    // Waves
    public double enemyWaveTimer = 2.0;

    public GameContext(GameConfig config) {
        this.config = (config == null)
                ? new GameConfig(GameMode.SKIRMISH, 5000, 5000, true, System.nanoTime(), false)
                : config;
        this.WORLD_W = this.config.worldW;
        this.WORLD_H = this.config.worldH;
        this.rng = new Random(this.config.seed);
    }
}
