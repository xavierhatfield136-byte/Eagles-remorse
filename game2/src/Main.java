import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.util.*;
import java.util.List;

public class Main extends JPanel implements ActionListener, KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    // ---------------- Screen / Loop ----------------
    private static final int W = 1280, H = 720;
    private static final int FPS_MS = 16;
    private static final double DT = FPS_MS / 1000.0;

    // ---------------- World ----------------
    private static final double WORLD_W = 6000;
    private static final double WORLD_H = 6000;

    // ---------------- Input ----------------
    private boolean up, down, left, right;
    private boolean shooting;
    private boolean missileHeld;
    private boolean commsOpen = false;

    private int mouseX = W / 2, mouseY = H / 2;
    private boolean autoAim = false;

    // Abilities
    private boolean abilityShieldOverchargeHeld = false; // SPACE
    private boolean abilityAfterburnerHeld = false;      // SHIFT
    private boolean summonDronePressed = false;          // F

    // ---------------- RNG ----------------
    private final Random rng = new Random();

    // ---------------- Camera zoom + shake ----------------
    private double zoom = 1.0;              // 0.6..1.6
    private double shakeTime = 0.0;
    private double shakeStrength = 0.0;
    private double shakeX = 0.0, shakeY = 0.0;

    private void addShake(double strength, double seconds) {
        shakeStrength = Math.max(shakeStrength, strength);
        shakeTime = Math.max(shakeTime, seconds);
    }

    // ---------------- Sector system ----------------
    private int sectorId = 1;

    // ---------------- Friendly command system ----------------
    enum FriendlyCommand { DEFAULT, ESCORT_PLAYER, FOCUS_FIRE, RETURN_TO_BASE }
    private FriendlyCommand friendlyCommand = FriendlyCommand.DEFAULT;
    private double focusX = 0, focusY = 0;

    // ---------------- Save file ----------------
    private static final String SAVE_FILE = "savegame.properties";

    // ---------------- Entities ----------------
    private final Player player = new Player(WORLD_W / 2.0, WORLD_H / 2.0);

    private final List<Ship> friendlies = new ArrayList<>();
    private final List<EnemyShip> enemies = new ArrayList<>();
    private final List<CapitalShip> capitals = new ArrayList<>();

    private final List<Drone> drones = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<Tracer> tracers = new ArrayList<>();

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<POI> pois = new ArrayList<>();

    // Bases
    private final Base friendlyBase = new Base(700, 700, Team.FRIENDLY, "Friendly Base");
    private final Base enemyBase = new Base(WORLD_W - 700, WORLD_H - 700, Team.ENEMY, "Enemy Base");

    // Spawning
    private long lastEnemySpawnMs = 0;
    private long enemySpawnIntervalMs = 1200;

    private long lastFriendlySpawnMs = 0;
    private long friendlySpawnIntervalMs = 1800;

    private long lastCapitalSpawnMs = 0;
    private long capitalSpawnIntervalMs = 12000;

    private static final int MAX_ENEMIES = 14;
    private static final int MAX_FRIENDLIES = 10;
    private static final int MAX_CAPITALS_PER_SIDE = 1;

    // Queues (avoid ConcurrentModification issues)
    private final List<Bullet> bulletSpawnQueue = new ArrayList<>();
    private final List<Missile> missileSpawnQueue = new ArrayList<>();
    private final List<Explosion> explosionSpawnQueue = new ArrayList<>();
    private final List<Tracer> tracerSpawnQueue = new ArrayList<>();
    private final List<Drone> droneSpawnQueue = new ArrayList<>();

    // Score / State
    private int score = 0;
    private boolean gameOver = false;

    // Comms
    private final MessageLog log = new MessageLog(10);

    // Missions
    enum MissionType { CLEAR_POI, DESTROY_ENEMY_BASE, DEFEND_FRIENDLY_BASE }
    private MissionType activeMission = null;
    private POI missionPOI = null;
    private int missionProgress = 0;

    // Loop timer
    private final javax.swing.Timer timer = new javax.swing.Timer(FPS_MS, this);

    public Main() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        addMouseWheelListener(this);

        generateWorldForSector(sectorId);
        camXVal = clamp(player.x - W / 2.0, 0, WORLD_W - W);
        camYVal = clamp(player.y - H / 2.0, 0, WORLD_H - H);

        log.push("System: Open-world space combat online.");
        log.push("System: C comms | E auto-aim | Hold Q missiles | F drone | SPACE shield | SHIFT boost");
        log.push("System: J sector jump | F5 save | F9 load | Mouse wheel zoom");
        log.push("System: In comms: 5 missions + friend commands");

        timer.start();
    }
    private void updateCamera() {
        double leadDist = 120; // try 80–180
        double leadX = Math.cos(player.aimAngle) * leadDist;
        double leadY = Math.sin(player.aimAngle) * leadDist;

        double targetX = clamp(player.x - W / 2.0 + leadX, 0, WORLD_W - W);
        double targetY = clamp(player.y - H / 2.0 + leadY, 0, WORLD_H - H);

        double k = 0.12; // smoothing (0.08 slower, 0.18 snappier)
        camXVal += (targetX - camXVal) * k;
        camYVal += (targetY - camYVal) * k;
    }
    // --- Lock-on targeting ---
    private Ship lockTarget = null;
    private boolean lockPressed = false;   // TAB edge detect

    // ---------------- Sector worldgen ----------------
    private void generateWorldForSector(int id) {
        obstacles.clear();
        pois.clear();

        rng.setSeed(1337L + id * 99991L);

        // obstacle belt
        int obstacleCount = 55 + Math.min(40, id * 2);
        for (int i = 0; i < obstacleCount; i++) {
            double x = WORLD_W * 0.20 + rng.nextDouble() * WORLD_W * 0.60;
            double y = WORLD_H * 0.20 + rng.nextDouble() * WORLD_H * 0.60;
            double r = 35 + rng.nextDouble() * (110 + id * 1.2);
            obstacles.add(new Obstacle(x, y, r));
        }

        // POIs
        int poiCount = 10 + Math.min(6, id / 2);
        for (int i = 0; i < poiCount; i++) {
            double x = 400 + rng.nextDouble() * (WORLD_W - 800);
            double y = 400 + rng.nextDouble() * (WORLD_H - 800);
            POIType t = (rng.nextDouble() < 0.5) ? POIType.WRECK : POIType.NEUTRAL_STATION;
            pois.add(new POI(x, y, t));
        }

        // cover near bases
        for (int i = 0; i < 14; i++) {
            obstacles.add(new Obstacle(friendlyBase.x + rng.nextGaussian() * 260, friendlyBase.y + rng.nextGaussian() * 260,
                    25 + rng.nextDouble() * 70));
            obstacles.add(new Obstacle(enemyBase.x + rng.nextGaussian() * 260, enemyBase.y + rng.nextGaussian() * 260,
                    25 + rng.nextDouble() * 70));
        }
    }

    private void jumpSector() {
        sectorId++;
        log.push("System: Jumping to Sector " + sectorId + "...");
        generateWorldForSector(sectorId);

        // Clear transient combat (keep player ship state)
        enemies.clear();
        friendlies.clear();
        capitals.clear();
        drones.clear();
        bullets.clear();
        missiles.clear();
        explosions.clear();
        tracers.clear();

        bulletSpawnQueue.clear();
        missileSpawnQueue.clear();
        explosionSpawnQueue.clear();
        tracerSpawnQueue.clear();
        droneSpawnQueue.clear();

        // Reposition bases for this sector (simple: fixed corners)
        friendlyBase.x = 700;
        friendlyBase.y = 700;
        enemyBase.x = WORLD_W - 700;
        enemyBase.y = WORLD_H - 700;

        friendlyBase.reset();
        enemyBase.reset();

        // Bring player near friendly base
        player.x = friendlyBase.x + 300;
        player.y = friendlyBase.y + 240;

        activeMission = null;
        missionPOI = null;
        missionProgress = 0;

        addShake(10.0, 0.25);
    }

    // ---------------- Save / Load ----------------
    private void quickSave() {
        try {
            Properties p = new Properties();
            p.setProperty("sectorId", String.valueOf(sectorId));
            p.setProperty("score", String.valueOf(score));

            p.setProperty("player.x", String.valueOf(player.x));
            p.setProperty("player.y", String.valueOf(player.y));
            p.setProperty("player.hp", String.valueOf(player.hp));
            p.setProperty("player.shield", String.valueOf(player.shield));
            p.setProperty("player.missiles", String.valueOf(player.missiles));

            p.setProperty("friendlyBase.alive", String.valueOf(friendlyBase.alive));
            p.setProperty("enemyBase.alive", String.valueOf(enemyBase.alive));
            p.setProperty("friendlyBase.hp", String.valueOf(friendlyBase.hp));
            p.setProperty("enemyBase.hp", String.valueOf(enemyBase.hp));
            p.setProperty("friendlyBase.shield", String.valueOf(friendlyBase.shield));
            p.setProperty("enemyBase.shield", String.valueOf(enemyBase.shield));

            p.setProperty("mission.active", String.valueOf(activeMission));
            if (missionPOI != null) {
                p.setProperty("mission.poi.x", String.valueOf(missionPOI.x));
                p.setProperty("mission.poi.y", String.valueOf(missionPOI.y));
                p.setProperty("mission.poi.type", String.valueOf(missionPOI.type));
            }

            p.setProperty("friendlyCommand", String.valueOf(friendlyCommand));

            try (FileOutputStream out = new FileOutputStream(SAVE_FILE)) {
                p.store(out, "Space Shooter Save");
            }

            log.push("System: Saved (" + SAVE_FILE + ")");
        } catch (Exception ex) {
            log.push("System: Save failed: " + ex.getMessage());
        }
    }

    private void quickLoad() {
        try {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(SAVE_FILE)) {
                p.load(in);
            }

            sectorId = Integer.parseInt(p.getProperty("sectorId", "1"));
            score = Integer.parseInt(p.getProperty("score", "0"));

            player.x = Double.parseDouble(p.getProperty("player.x", String.valueOf(WORLD_W / 2.0)));
            player.y = Double.parseDouble(p.getProperty("player.y", String.valueOf(WORLD_H / 2.0)));
            player.hp = Double.parseDouble(p.getProperty("player.hp", String.valueOf(player.hpMax)));
            player.shield = Double.parseDouble(p.getProperty("player.shield", String.valueOf(player.shieldMax)));
            player.missiles = Integer.parseInt(p.getProperty("player.missiles", String.valueOf(player.missilesMax)));

            friendlyBase.alive = Boolean.parseBoolean(p.getProperty("friendlyBase.alive", "true"));
            enemyBase.alive = Boolean.parseBoolean(p.getProperty("enemyBase.alive", "true"));
            friendlyBase.hp = Double.parseDouble(p.getProperty("friendlyBase.hp", String.valueOf(friendlyBase.hpMax)));
            enemyBase.hp = Double.parseDouble(p.getProperty("enemyBase.hp", String.valueOf(enemyBase.hpMax)));
            friendlyBase.shield = Double.parseDouble(p.getProperty("friendlyBase.shield", String.valueOf(friendlyBase.shieldMax)));
            enemyBase.shield = Double.parseDouble(p.getProperty("enemyBase.shield", String.valueOf(enemyBase.shieldMax)));

            String fc = p.getProperty("friendlyCommand", "DEFAULT");
            try {
                friendlyCommand = FriendlyCommand.valueOf(fc);
            } catch (Exception ignore) {
                friendlyCommand = FriendlyCommand.DEFAULT;
            }

            // rebuild sector world
            generateWorldForSector(sectorId);

            // clear combat entities on load (simple & robust)
            enemies.clear();
            friendlies.clear();
            capitals.clear();
            drones.clear();
            bullets.clear();
            missiles.clear();
            explosions.clear();
            tracers.clear();
            bulletSpawnQueue.clear();
            missileSpawnQueue.clear();
            explosionSpawnQueue.clear();
            tracerSpawnQueue.clear();
            droneSpawnQueue.clear();

            // mission restore
            String m = p.getProperty("mission.active", "null");
            activeMission = "null".equals(m) ? null : MissionType.valueOf(m);

            if (p.containsKey("mission.poi.x")) {
                double px = Double.parseDouble(p.getProperty("mission.poi.x"));
                double py = Double.parseDouble(p.getProperty("mission.poi.y"));
                POIType pt = POIType.valueOf(p.getProperty("mission.poi.type"));
                missionPOI = new POI(px, py, pt);
            } else {
                missionPOI = null;
            }
            missionProgress = 0;

            log.push("System: Loaded (" + SAVE_FILE + ")");
            addShake(8.0, 0.2);
        } catch (Exception ex) {
            log.push("System: Load failed: " + ex.getMessage());
        }
    }

    // ---------------- Loop ----------------
    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();

        if (!gameOver) {
            updatePlayer(now);
            updateCamera();
            spawnFriendlies(now);
            spawnEnemies(now);
            spawnCapitals(now);

            updateFriendlies(now);
            updateEnemies(now);
            updateCapitals(now);

            updateBases(now);
            updateDrones(now);

            updateMissiles();
            updateBullets();
            updateExplosions();
            updateTracers();

            handleCollisions();
            updateMissionState();

            // Apply queued spawns
            if (!bulletSpawnQueue.isEmpty()) { bullets.addAll(bulletSpawnQueue); bulletSpawnQueue.clear(); }
            if (!missileSpawnQueue.isEmpty()) { missiles.addAll(missileSpawnQueue); missileSpawnQueue.clear(); }
            if (!explosionSpawnQueue.isEmpty()) { explosions.addAll(explosionSpawnQueue); explosionSpawnQueue.clear(); }
            if (!tracerSpawnQueue.isEmpty()) { tracers.addAll(tracerSpawnQueue); tracerSpawnQueue.clear(); }
            if (!droneSpawnQueue.isEmpty()) { drones.addAll(droneSpawnQueue); droneSpawnQueue.clear(); }

        } else {
            updateExplosions();
            updateTracers();
        }

        // Shake decay/update
        if (shakeTime > 0) {
            shakeTime -= DT;
            double s = shakeStrength;
            shakeX = (rng.nextDouble() * 2 - 1) * s;
            shakeY = (rng.nextDouble() * 2 - 1) * s;
            shakeStrength *= 0.90;
        } else {
            shakeTime = 0;
            shakeStrength = 0;
            shakeX = shakeY = 0;
        }

        repaint();
    }

    // ---------------- Mission logic ----------------
    private void startMission(MissionType t) {
        activeMission = t;
        missionProgress = 0;
        missionPOI = null;

        if (t == MissionType.CLEAR_POI) {
            if (!pois.isEmpty()) missionPOI = pois.get(rng.nextInt(pois.size()));
            log.push("Mission: Clear hostiles near a POI (yellow marker).");
        } else if (t == MissionType.DESTROY_ENEMY_BASE) {
            log.push("Mission: Destroy the enemy base.");
        } else if (t == MissionType.DEFEND_FRIENDLY_BASE) {
            log.push("Mission: Defend friendly base. Keep it alive for 30 seconds.");
        }
    }

    private void completeMission(String msg, int reward) {
        log.push("Mission complete: " + msg + " (+" + reward + ")");
        score += reward;
        activeMission = null;
        missionPOI = null;
        missionProgress = 0;
    }

    private void updateMissionState() {
        if (activeMission == null) return;

        if (activeMission == MissionType.CLEAR_POI) {
            if (missionPOI == null) {
                completeMission("POI resolved", 250);
                return;
            }
            // count enemies near POI
            double R = 350;
            boolean anyNear = false;
            for (EnemyShip en : enemies) {
                if (!en.alive) continue;
                if (dist2(en.x, en.y, missionPOI.x, missionPOI.y) < R * R) { anyNear = true; break; }
            }
            for (CapitalShip cap : capitals) {
                if (!cap.alive || cap.team != Team.ENEMY) continue;
                if (dist2(cap.x, cap.y, missionPOI.x, missionPOI.y) < (R + 200) * (R + 200)) { anyNear = true; break; }
            }
            if (!anyNear) {
                missionProgress++;
                if (missionProgress > 120) { // ~2 seconds of "clear"
                    completeMission("Area secured", 500);
                }
            } else {
                missionProgress = 0;
            }
        }

        if (activeMission == MissionType.DESTROY_ENEMY_BASE) {
            if (!enemyBase.alive) completeMission("Enemy base destroyed", 900);
        }

        if (activeMission == MissionType.DEFEND_FRIENDLY_BASE) {
            if (!friendlyBase.alive) {
                log.push("Mission failed: Friendly base destroyed.");
                activeMission = null;
                return;
            }
            missionProgress++;
            if (missionProgress > (int)(30 / DT)) { // 30 seconds
                completeMission("Friendly base defended", 700);
            }
        }
    }

    // ---------------- Camera ----------------
    private double camX() { return camXVal; }
    private double camY() { return camYVal; }

    private int sx(double worldX) { return (int)Math.round((worldX - camX()) * zoom); }
    private int sy(double worldY) { return (int)Math.round((worldY - camY()) * zoom); }

    // ---------------- Player ----------------
    private void updatePlayer(long now) {
        double vx = 0, vy = 0;
        if (!commsOpen) {
            if (up) vy -= player.speed;
            if (down) vy += player.speed;
            if (left) vx -= player.speed;
            if (right) vx += player.speed;
            if (player.hullHitFlashFrames > 0) player.hullHitFlashFrames--;
            if (player.shieldHitFlashFrames > 0) player.shieldHitFlashFrames--;

        }

        if (vx != 0 && vy != 0) {
            double inv = 1.0 / Math.sqrt(2);
            vx *= inv; vy *= inv;
        }

        // Afterburner
        if (abilityAfterburnerHeld && player.modules.isModuleOnline(ModuleType.ENGINE) && player.modules.isModuleOnline(ModuleType.THRUSTER)) {
            vx *= 1.8;
            vy *= 1.8;
            player.afterburnerHeat = Math.min(1.0, player.afterburnerHeat + 0.03);
        } else {
            player.afterburnerHeat = Math.max(0.0, player.afterburnerHeat - 0.02);
        }
        if (player.afterburnerHeat >= 1.0) {
            player.modules.damageModule(ModuleType.THRUSTER, 999);
            log.push("System: Afterburner overheated! Thrusters disabled.");
            player.afterburnerHeat = 0.75;
        }

        player.vx = vx; player.vy = vy;

        double newX = clamp(player.x + player.vx, player.radius, WORLD_W - player.radius);
        double newY = clamp(player.y + player.vy, player.radius, WORLD_H - player.radius);

        double[] pushed = pushOutOfObstacles(newX, newY, player.radius);
        player.x = pushed[0]; player.y = pushed[1];

        if (player.vx != 0 || player.vy != 0) player.hullAngle = Math.atan2(player.vy, player.vx);

        // Aim (mouse or auto-aim)
        double worldMouseX = camX() + mouseX / zoom;
        double worldMouseY = camY() + mouseY / zoom;

        Ship aimTarget = autoAim ? findClosestEnemyOrCapital(player.x, player.y) : null;
        double tx = (aimTarget != null) ? aimTarget.x : worldMouseX;
        double ty = (aimTarget != null) ? aimTarget.y : worldMouseY;

        // store for "focus fire" command
        focusX = tx;
        focusY = ty;

        player.aimAngle = Math.atan2(ty - player.y, tx - player.x);

        // Repair modules slowly
        player.modules.repairTick(0.25 * DT);

        // Shield regen
        if (player.modules.isModuleOnline(ModuleType.SHIELD_GEN)) {
            double regenMult = abilityShieldOverchargeHeld ? 2.4 : 1.0;
            player.shield = Math.min(player.shieldMax, player.shield + player.shieldRegenPerSec * regenMult * DT);
        }

        // Base aura boosts
        if (friendlyBase.alive && friendlyBase.isInAura(player.x, player.y)) {
            player.hp = Math.min(player.hpMax, player.hp + friendlyBase.repairPerSec * DT);
            if (player.modules.isModuleOnline(ModuleType.SHIELD_GEN)) {
                player.shield = Math.min(player.shieldMax, player.shield + friendlyBase.shieldPerSec * DT);
            }
            player.modules.repairTick(2.2 * DT);

            // resupply missiles over time
            player.missiles = Math.min(player.missilesMax, player.missiles + (int)Math.round(friendlyBase.missilesPerSec * DT));
        }

        // Player CIWS (prioritize missiles; if bot too close, target it anyway; nerfed vs ships)
        player.tryCIWS(now, enemies, capitals, missiles, bulletSpawnQueue, tracerSpawnQueue);

        // Main gun
        if (shooting && !commsOpen && player.modules.isModuleOnline(ModuleType.WEAPON)) {
            player.tryShootTriple(now, tx, ty, bulletSpawnQueue);
        }

        // Missiles (no cooldown, limited ammo)
        if (missileHeld && !commsOpen && player.modules.isModuleOnline(ModuleType.WEAPON)) {
            player.tryLaunchMissileNoCooldown(aimTarget, missileSpawnQueue);
        }

        // Drone spawn
        if (summonDronePressed && !commsOpen) {
            summonDrone();
            summonDronePressed = false;
        }

        if (player.hp <= 0) {
            gameOver = true;
            log.push("System: You were destroyed.");
        }
    }

    private void summonDrone() {
        if (drones.size() >= player.maxDrones) {
            log.push("Friendly: Drone limit reached.");
            return;
        }
        Drone d = new Drone(player.x + rng.nextGaussian() * 20, player.y + rng.nextGaussian() * 20);
        droneSpawnQueue.add(d);
        log.push("Friendly: Drone deployed (temporary escort).");
    }

    // ---------------- Friendly command behaviors ----------------
    private void updateEscortBehavior(Ship s, long now) {
        // Escort point: orbit around player with deterministic offset per ship
        int h = System.identityHashCode(s);
        double a = (h % 360) * Math.PI / 180.0 + (now * 0.00025);
        double dist = 120 + ((h >>> 4) % 40);
        double tx = player.x + Math.cos(a) * dist;
        double ty = player.y + Math.sin(a) * dist;

        double moveSpeed = s.modules.isModuleOnline(ModuleType.ENGINE) ? s.speed : s.speed * 0.15;

        double dx = tx - s.x, dy = ty - s.y;
        double len = Math.hypot(dx, dy);
        if (len > 0.001) { dx /= len; dy /= len; }

        double perpX = -dy;
        double perpY = dx;
        double strafe = Math.sin((now * 0.001) + (s.x + s.y) * 0.0005) * 0.65;

        double mvx = dx * moveSpeed + perpX * strafe;
        double mvy = dy * moveSpeed + perpY * strafe;

        if (!s.modules.isModuleOnline(ModuleType.THRUSTER)) {
            mvx *= 0.65;
            mvy *= 0.65;
        }

        s.x += mvx;
        s.y += mvy;
        s.facing = Math.atan2(mvy, mvx);

        s.x = clamp(s.x, s.radius, WORLD_W - s.radius);
        s.y = clamp(s.y, s.radius, WORLD_H - s.radius);

        double[] pushed = pushOutOfObstacles(s.x, s.y, s.radius);
        s.x = pushed[0]; s.y = pushed[1];
    }

    // ---------------- Spawning ----------------
    private void spawnFriendlies(long now) {
        if (!friendlyBase.alive) return;
        if (friendlies.size() >= MAX_FRIENDLIES) return;
        if (now - lastFriendlySpawnMs < friendlySpawnIntervalMs) return;

        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 120 + rng.nextDouble() * 120;
        double fx = friendlyBase.x + Math.cos(angle) * dist;
        double fy = friendlyBase.y + Math.sin(angle) * dist;

        Ship s;
        double r = rng.nextDouble();
        if (r < 0.22) s = new FriendlyInterceptor(fx, fy);
        else if (r < 0.32) s = new FriendlyShieldTank(fx, fy);
        else s = new FriendlyShip(fx, fy);

        friendlies.add(s);

        lastFriendlySpawnMs = now;
        friendlySpawnIntervalMs = 1400 + rng.nextInt(900);
    }

    private void spawnEnemies(long now) {
        if (!enemyBase.alive) return;
        if (enemies.size() >= MAX_ENEMIES) return;
        if (now - lastEnemySpawnMs < enemySpawnIntervalMs) return;

        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 140 + rng.nextDouble() * 140;
        double ex = enemyBase.x + Math.cos(angle) * dist;
        double ey = enemyBase.y + Math.sin(angle) * dist;

        EnemyShip en;
        double r = rng.nextDouble();
        if (r < 0.20) en = new MissileBoat(ex, ey);
        else if (r < 0.40) en = new Interceptor(ex, ey);
        else if (r < 0.62) en = new ShieldTank(ex, ey);
        else if (r < 0.74) en = new CIWSDestroyer(ex, ey); // ONLY CIWS NPC
        else en = new EnemyShip(ex, ey);

        enemies.add(en);

        lastEnemySpawnMs = now;
        enemySpawnIntervalMs = 1100 + rng.nextInt(900);
    }

    private void spawnCapitals(long now) {
        if (now - lastCapitalSpawnMs < capitalSpawnIntervalMs) return;

        long friendlyCap = capitals.stream().filter(c -> c.team == Team.FRIENDLY && c.alive).count();
        long enemyCap = capitals.stream().filter(c -> c.team == Team.ENEMY && c.alive).count();

        if (friendlyBase.alive && friendlyCap < MAX_CAPITALS_PER_SIDE) {
            capitals.add(CapitalShip.spawnNear(friendlyBase.x, friendlyBase.y, Team.FRIENDLY));
            log.push("Friendly: Capital ship arrived.");
        }
        if (enemyBase.alive && enemyCap < MAX_CAPITALS_PER_SIDE) {
            capitals.add(CapitalShip.spawnNear(enemyBase.x, enemyBase.y, Team.ENEMY));
            log.push("Enemy: Capital ship arrived.");
        }

        lastCapitalSpawnMs = now;
        capitalSpawnIntervalMs = 11000 + rng.nextInt(7000);
    }

    // ---------------- Update Friendlies/Enemies/Capitals ----------------
    private void updateFriendlies(long now) {
        Iterator<Ship> it = friendlies.iterator();
        while (it.hasNext()) {
            Ship s = it.next();
            if (!s.alive) { it.remove(); continue; }

            s.modules.repairTick(0.18 * DT);
            if (friendlyBase.alive && friendlyBase.isInAura(s.x, s.y)) {
                s.hp = Math.min(s.hpMax, s.hp + 0.6 * DT);
                if (s.modules.isModuleOnline(ModuleType.SHIELD_GEN))
                    s.shield = Math.min(s.shieldMax, s.shield + 1.0 * DT);
                s.modules.repairTick(1.6 * DT);
            }

            s.regenShield(DT);

            // Shoot target selection
            Ship shootTarget;
            if (friendlyCommand == FriendlyCommand.FOCUS_FIRE) {
                shootTarget = findClosestEnemyOrCapital(focusX, focusY);
            } else {
                shootTarget = findClosestEnemyOrCapital(s.x, s.y);
            }

            // Movement behavior selection
            if (friendlyCommand == FriendlyCommand.ESCORT_PLAYER) {
                updateEscortBehavior(s, now);
            } else {
                if (friendlyCommand == FriendlyCommand.RETURN_TO_BASE) {
                    s.fleeing = true;
                    s.kamikaze = false;
                }
                s.updateCommonAI(now, shootTarget, player, friendlyBase, enemyBase, obstacles);
                double[] pushed = pushOutOfObstacles(s.x, s.y, s.radius);
                s.x = pushed[0]; s.y = pushed[1];
            }

            if (shootTarget != null && s.modules.isModuleOnline(ModuleType.WEAPON)) {
                s.tryShoot(now, shootTarget.x, shootTarget.y, bulletSpawnQueue);
            }

            s.tryUseAbility(now, shootTarget);

            if (s instanceof FriendlyShip fs && fs.missiles > 0 && shootTarget != null && rng.nextDouble() < 0.02) {
                fs.launchMissileAt(shootTarget, missileSpawnQueue);
            }
        }
    }

    private void updateEnemies(long now) {
        Iterator<EnemyShip> it = enemies.iterator();
        while (it.hasNext()) {
            EnemyShip en = it.next();
            if (!en.alive) { it.remove(); continue; }

            en.modules.repairTick(0.14 * DT);

            if (enemyBase.alive && enemyBase.isInAura(en.x, en.y)) {
                en.hp = Math.min(en.hpMax, en.hp + 0.55 * DT);
                if (en.modules.isModuleOnline(ModuleType.SHIELD_GEN))
                    en.shield = Math.min(en.shieldMax, en.shield + 0.95 * DT);
                en.modules.repairTick(1.5 * DT);
            }

            en.regenShield(DT);

            Ship target = findClosestFriendlyOrPlayer(en.x, en.y);
            en.updateCommonAI(now, target, player, enemyBase, friendlyBase, obstacles);

            if (target != null && en.modules.isModuleOnline(ModuleType.WEAPON)) {
                en.tryShoot(now, target.x, target.y, bulletSpawnQueue);
            }

            if (en instanceof CIWSDestroyer ciws) {
                ciws.tryCIWS(now, missiles, bulletSpawnQueue, tracerSpawnQueue);
            }

            if (en instanceof MissileBoat mb) {
                mb.tryFireMissile(now, player.x, player.y, missileSpawnQueue);
            }

            en.tryUseAbility(now, target);

            double[] pushed = pushOutOfObstacles(en.x, en.y, en.radius);
            en.x = pushed[0]; en.y = pushed[1];
        }
    }

    private void updateCapitals(long now) {
        Iterator<CapitalShip> it = capitals.iterator();
        while (it.hasNext()) {
            CapitalShip cap = it.next();
            if (!cap.alive) { it.remove(); continue; }

            cap.modules.repairTick(0.10 * DT);
            cap.regenShield(DT);

            Base myBase = (cap.team == Team.FRIENDLY) ? friendlyBase : enemyBase;
            Base theirBase = (cap.team == Team.FRIENDLY) ? enemyBase : friendlyBase;

            cap.updateCapitalAI(now, myBase, theirBase, obstacles);

            Ship tgt = (cap.team == Team.FRIENDLY) ? findClosestEnemyOrCapital(cap.x, cap.y) : findClosestFriendlyOrPlayer(cap.x, cap.y);
            if (tgt != null) cap.aimTurretsAt(tgt.x, tgt.y);

            if (tgt != null && cap.modules.isModuleOnline(ModuleType.WEAPON)) {
                cap.tryFireTurrets(now, tgt.x, tgt.y, bulletSpawnQueue);
                cap.tryUseAbility(now, tgt);
            }

            double[] pushed = pushOutOfObstacles(cap.x, cap.y, cap.radius);
            cap.x = pushed[0]; cap.y = pushed[1];
        }
    }

    // ---------------- Bases ----------------
    private void updateBases(long now) {
        if (friendlyBase.alive) {
            friendlyBase.modules.repairTick(0.10 * DT);
            friendlyBase.regenShield(DT);

            Ship tgt = findClosestEnemyOrCapital(friendlyBase.x, friendlyBase.y);
            if (tgt != null && friendlyBase.modules.isModuleOnline(ModuleType.WEAPON)) {
                friendlyBase.tryShootPoint(now, tgt.x, tgt.y, bulletSpawnQueue, tracerSpawnQueue);
            }
        }

        if (enemyBase.alive) {
            enemyBase.modules.repairTick(0.10 * DT);
            enemyBase.regenShield(DT);

            Ship tgt = findClosestFriendlyOrPlayer(enemyBase.x, enemyBase.y);
            if (tgt != null && enemyBase.modules.isModuleOnline(ModuleType.WEAPON)) {
                enemyBase.tryShootPoint(now, tgt.x, tgt.y, bulletSpawnQueue, tracerSpawnQueue);
            }
        }
    }

    // ---------------- Drones ----------------
    private void updateDrones(long now) {
        Iterator<Drone> it = drones.iterator();
        while (it.hasNext()) {
            Drone d = it.next();
            d.lifeFrames--;
            if (d.lifeFrames <= 0) { it.remove(); continue; }

            double dx = player.x - d.x;
            double dy = player.y - d.y;
            double dist = Math.hypot(dx, dy);
            if (dist > 0.001) {
                dx /= dist; dy /= dist;
                double err = dist - d.followDistance;
                d.vx += dx * err * 0.05;
                d.vy += dy * err * 0.05;
            }
            d.vx *= 0.90;
            d.vy *= 0.90;

            double sp = Math.hypot(d.vx, d.vy);
            if (sp > d.speed) { d.vx = d.vx / sp * d.speed; d.vy = d.vy / sp * d.speed; }

            d.x = clamp(d.x + d.vx, d.radius, WORLD_W - d.radius);
            d.y = clamp(d.y + d.vy, d.radius, WORLD_H - d.radius);

            double[] pushed = pushOutOfObstacles(d.x, d.y, d.radius);
            d.x = pushed[0]; d.y = pushed[1];

            Ship tgt = findClosestEnemyOrCapital(d.x, d.y);
            if (tgt != null) {
                d.aimAngle = Math.atan2(tgt.y - d.y, tgt.x - d.x);
                d.tryShoot(now, tgt.x, tgt.y, bulletSpawnQueue);
            }
        }
    }

    // ---------------- Missiles/Bullets/Effects ----------------
    private void updateMissiles() {
        Iterator<Missile> it = missiles.iterator();
        while (it.hasNext()) {
            Missile m = it.next();

            // Homing acquire
            if (m.team == Team.FRIENDLY) {
                if (m.lockTarget == null || !m.lockTarget.alive) m.lockTarget = findClosestEnemyOrCapital(m.x, m.y);
            } else {
                if (m.lockTarget == null || !m.lockTarget.alive) m.lockTarget = findClosestFriendlyOrPlayer(m.x, m.y);
            }

            double tx, ty;
            if (m.lockTarget != null) { tx = m.lockTarget.x; ty = m.lockTarget.y; }
            else { tx = m.x + Math.cos(m.angle) * 1000; ty = m.y + Math.sin(m.angle) * 1000; }

            double desired = Math.atan2(ty - m.y, tx - m.x);
            double delta = normalizeAngle(desired - m.angle);
            double maxTurn = m.turnRateRadPerSec * DT;
            delta = clamp(delta, -maxTurn, maxTurn);
            m.angle = normalizeAngle(m.angle + delta);

            m.vx = Math.cos(m.angle) * m.speed;
            m.vy = Math.sin(m.angle) * m.speed;

            double nx = m.x + m.vx;
            double ny = m.y + m.vy;

            // Obstacle hit
            if (circleHitsObstacle(nx, ny, m.hitRadius)) {
                explosionSpawnQueue.add(new Explosion(m.x, m.y, 22));
                addShake(3.5, 0.12);
                it.remove();
                continue;
            }

            m.x = nx; m.y = ny;
            m.life--;

            // Proximity fuse
            if (m.lockTarget != null && dist2(m.x, m.y, m.lockTarget.x, m.lockTarget.y) < sq(m.proxFuseRadius)) {
                explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                addShake(6.0, 0.18);
                m.lockTarget.takeDamage(m.damage, explosionSpawnQueue);
                it.remove();
                continue;
            }

            if (m.life <= 0 || outOfWorld(m.x, m.y, 250)) it.remove();
        }
    }

    private void updateBullets() {
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();

            double nx = b.x + b.vx;
            double ny = b.y + b.vy;

            if (circleHitsObstacle(nx, ny, b.radius)) {
                it.remove();
                continue;
            }

            b.x = nx; b.y = ny;
            b.life--;
            if (b.life <= 0 || outOfWorld(b.x, b.y, 250)) it.remove();
        }
    }

    private void updateExplosions() {
        Iterator<Explosion> it = explosions.iterator();
        while (it.hasNext()) {
            Explosion ex = it.next();
            ex.age++;
            if (ex.age > ex.maxAge) it.remove();
        }
    }

    private void updateTracers() {
        Iterator<Tracer> it = tracers.iterator();
        while (it.hasNext()) {
            Tracer t = it.next();
            t.life--;
            if (t.life <= 0) it.remove();
        }
    }

    // ---------------- Collisions ----------------
    private void handleCollisions() {
        // CIWS bullets hit missiles; if missile destroyed -> pellet burst
        for (Bullet b : bullets) {
            if (b.type != BulletType.CIWS) continue;

            for (Missile m : missiles) {
                if (b.team == m.team) continue;
                if (dist2(b.x, b.y, m.x, m.y) < sq(m.hitRadius + b.radius)) {
                    b.life = 0;
                    m.hp -= 1;
                    if (m.hp <= 0) {
                        explosionSpawnQueue.add(new Explosion(m.x, m.y, 14));
                        addShake(2.4, 0.10);
                        spawnMissilePellets(m.x, m.y, m.team, 10 + rng.nextInt(10));
                        m.life = 0;
                    }
                    break;
                }
            }
        }

        // Normal bullets also can kill missiles
        for (Bullet b : bullets) {
            if (b.life <= 0) continue;
            for (Missile m : missiles) {
                if (b.team == m.team) continue;
                if (dist2(b.x, b.y, m.x, m.y) < sq(m.hitRadius + b.radius)) {
                    b.life = 0;
                    m.hp -= 1;
                    if (m.hp <= 0) {
                        explosionSpawnQueue.add(new Explosion(m.x, m.y, 14));
                        addShake(2.4, 0.10);
                        spawnMissilePellets(m.x, m.y, m.team, 10 + rng.nextInt(10));
                        m.life = 0;
                    }
                    break;
                }
            }
        }

        // Bullets hit ships/bases/capitals/drones -> shield-hit explosion if shield absorbed
        Iterator<Bullet> bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            if (b.life <= 0) { bit.remove(); continue; }

            if (b.team == Team.FRIENDLY) {
                // enemy base
                if (enemyBase.alive && dist2(b.x, b.y, enemyBase.x, enemyBase.y) < sq(enemyBase.radius + b.radius)) {
                    boolean shieldHit = enemyBase.applyBulletDamage(b.damage, explosionSpawnQueue);
                    if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.4, 0.06); }
                    bit.remove();
                    continue;
                }

                // enemy capitals: turrets first, then hull segments
                boolean removed = false;
                for (CapitalShip cap : capitals) {
                    if (!cap.alive || cap.team != Team.ENEMY) continue;

                    int ti = cap.hitTurretIndex(b.x, b.y, b.radius);
                    if (ti != -1) {
                        cap.turrets.get(ti).hp -= b.damage;
                        explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y));
                        addShake(1.6, 0.06);
                        bit.remove();
                        removed = true;
                        break;
                    }

                    if (cap.hitByCircle(b.x, b.y, b.radius)) {
                        boolean shieldHit = cap.applyBulletDamage(b.damage, explosionSpawnQueue);
                        if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.6, 0.06); }
                        bit.remove();
                        removed = true;
                        break;
                    }
                }
                if (removed) continue;

                // enemies
                EnemyShip hit = null;
                for (EnemyShip en : enemies) {
                    if (!en.alive) continue;
                    if (dist2(b.x, b.y, en.x, en.y) < sq(en.radius + b.radius)) { hit = en; break; }
                }
                if (hit != null) {
                    boolean shieldHit = hit.applyBulletDamage(b.damage, explosionSpawnQueue);
                    if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.2, 0.05); }
                    bit.remove();
                }
            }

            if (b.team == Team.ENEMY) {
                // friendly base
                if (friendlyBase.alive && dist2(b.x, b.y, friendlyBase.x, friendlyBase.y) < sq(friendlyBase.radius + b.radius)) {
                    boolean shieldHit = friendlyBase.applyBulletDamage(b.damage, explosionSpawnQueue);
                    if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.4, 0.06); }
                    bit.remove();
                    continue;
                }

                // friendly capitals: turrets first, then hull segments
                boolean removed = false;
                for (CapitalShip cap : capitals) {
                    if (!cap.alive || cap.team != Team.FRIENDLY) continue;

                    int ti = cap.hitTurretIndex(b.x, b.y, b.radius);
                    if (ti != -1) {
                        cap.turrets.get(ti).hp -= b.damage;
                        explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y));
                        addShake(1.6, 0.06);
                        bit.remove();
                        removed = true;
                        break;
                    }

                    if (cap.hitByCircle(b.x, b.y, b.radius)) {
                        boolean shieldHit = cap.applyBulletDamage(b.damage, explosionSpawnQueue);
                        if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.6, 0.06); }
                        bit.remove();
                        removed = true;
                        break;
                    }
                }
                if (removed) continue;

                // player
                if (dist2(b.x, b.y, player.x, player.y) < sq(player.radius + b.radius)) {
                    boolean shieldHit = player.applyBulletDamage(b.damage, explosionSpawnQueue);
                    if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.2, 0.05); }
                    bit.remove();
                    continue;
                }

                // friendlies
                for (Ship f : friendlies) {
                    if (!f.alive) continue;
                    if (dist2(b.x, b.y, f.x, f.y) < sq(f.radius + b.radius)) {
                        boolean shieldHit = f.applyBulletDamage(b.damage, explosionSpawnQueue);
                        if (shieldHit) { explosionSpawnQueue.add(Explosion.smallShieldHit(b.x, b.y)); addShake(1.2, 0.05); }
                        bit.remove();
                        break;
                    }
                }

                // drones
                if (b.life > 0) {
                    Iterator<Drone> dit = drones.iterator();
                    while (dit.hasNext()) {
                        Drone d = dit.next();
                        if (dist2(b.x, b.y, d.x, d.y) < sq(d.radius + b.radius)) {
                            dit.remove();
                            explosionSpawnQueue.add(new Explosion(d.x, d.y, 18));
                            addShake(2.8, 0.10);
                            bit.remove();
                            break;
                        }
                    }
                }
            }
        }

        // Missiles hit ships/bases/capitals (direct impact)
        Iterator<Missile> mit = missiles.iterator();
        while (mit.hasNext()) {
            Missile m = mit.next();
            if (m.life <= 0 || m.hp <= 0) { mit.remove(); continue; }

            if (m.team == Team.FRIENDLY) {
                if (enemyBase.alive && dist2(m.x, m.y, enemyBase.x, enemyBase.y) < sq(enemyBase.radius + m.hitRadius)) {
                    explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                    addShake(7.0, 0.20);
                    enemyBase.takeDamage(m.damage, explosionSpawnQueue);
                    mit.remove();
                    continue;
                }

                boolean removed = false;
                for (CapitalShip cap : capitals) {
                    if (!cap.alive || cap.team != Team.ENEMY) continue;
                    if (cap.hitByCircle(m.x, m.y, m.hitRadius)) {
                        explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                        addShake(7.0, 0.20);
                        cap.takeDamage(m.damage, explosionSpawnQueue);
                        mit.remove();
                        removed = true;
                        break;
                    }
                }
                if (removed) continue;

                for (EnemyShip en : enemies) {
                    if (!en.alive) continue;
                    if (dist2(m.x, m.y, en.x, en.y) < sq(en.radius + m.hitRadius)) {
                        explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                        addShake(6.0, 0.18);
                        en.takeDamage(m.damage, explosionSpawnQueue);
                        mit.remove();
                        break;
                    }
                }

            } else {
                if (friendlyBase.alive && dist2(m.x, m.y, friendlyBase.x, friendlyBase.y) < sq(friendlyBase.radius + m.hitRadius)) {
                    explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                    addShake(7.0, 0.20);
                    friendlyBase.takeDamage(m.damage, explosionSpawnQueue);
                    mit.remove();
                    continue;
                }

                boolean removed = false;
                for (CapitalShip cap : capitals) {
                    if (!cap.alive || cap.team != Team.FRIENDLY) continue;
                    if (cap.hitByCircle(m.x, m.y, m.hitRadius)) {
                        explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                        addShake(7.0, 0.20);
                        cap.takeDamage(m.damage, explosionSpawnQueue);
                        mit.remove();
                        removed = true;
                        break;
                    }
                }
                if (removed) continue;

                if (dist2(m.x, m.y, player.x, player.y) < sq(player.radius + m.hitRadius)) {
                    explosionSpawnQueue.add(new Explosion(m.x, m.y, 30));
                    addShake(6.5, 0.20);
                    player.takeDamage(m.damage, explosionSpawnQueue);
                    mit.remove();
                }
            }
        }

        missiles.removeIf(m -> m.life <= 0 || m.hp <= 0);

        if (friendlyBase.alive && friendlyBase.hp <= 0) {
            friendlyBase.alive = false;
            explosionSpawnQueue.add(new Explosion(friendlyBase.x, friendlyBase.y, 80));
            addShake(10.0, 0.30);
            log.push("System: Friendly Base destroyed!");
        }
        if (enemyBase.alive && enemyBase.hp <= 0) {
            enemyBase.alive = false;
            explosionSpawnQueue.add(new Explosion(enemyBase.x, enemyBase.y, 80));
            addShake(10.0, 0.30);
            log.push("System: Enemy Base destroyed!");
        }

        enemies.removeIf(s -> !s.alive);
        friendlies.removeIf(s -> !s.alive);
        capitals.removeIf(s -> !s.alive);

        if (!explosionSpawnQueue.isEmpty()) {
            explosions.addAll(explosionSpawnQueue);
            explosionSpawnQueue.clear();
        }

        if (player.hp <= 0) gameOver = true;
    }

    private void spawnMissilePellets(double x, double y, Team missileTeam, int count) {
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double sp = 6 + rng.nextDouble() * 5;
            bulletSpawnQueue.add(new Bullet(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                    missileTeam, BulletType.NORMAL, 38, 3.0, 1));
        }
    }

    // ---------------- Rendering ----------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(8, 8, 14));
        g2.fillRect(0, 0, W, H);

        drawGrid(g2);

        for (POI p : pois) drawPOI(g2, p);
        for (Obstacle o : obstacles) drawObstacle(g2, o);

        // mission marker
        drawMissionMarker(g2);

        drawBase(g2, friendlyBase);
        drawBase(g2, enemyBase);

        for (Explosion ex : explosions) drawExplosion(g2, ex);
        for (Tracer t : tracers) drawTracer(g2, t);
        for (Missile m : missiles) drawMissile(g2, m);
        for (Bullet b : bullets) drawBullet(g2, b);

        for (Drone d : drones) drawDrone(g2, d);
        for (Ship s : friendlies) drawShip(g2, s, new Color(120, 240, 160));
        for (EnemyShip s : enemies) drawShip(g2, s, s.getColor());
        for (CapitalShip c : capitals) drawCapital(g2, c);

        drawPlayer(g2, player);

        drawHUD(g2);
        drawMinimap(g2);
        drawComms(g2);

        if (gameOver) drawGameOver(g2);

        g2.dispose();
    }

    private void drawMissionMarker(Graphics2D g2) {
        if (activeMission == null) return;

        if (activeMission == MissionType.CLEAR_POI && missionPOI != null) {
            g2.setColor(new Color(255, 235, 120, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(sx(missionPOI.x) - 28, sy(missionPOI.y) - 28, 56, 56);
            g2.setStroke(new BasicStroke(1f));
        }

        if (activeMission == MissionType.DESTROY_ENEMY_BASE && enemyBase.alive) {
            g2.setColor(new Color(255, 235, 120, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(sx(enemyBase.x) - 40, sy(enemyBase.y) - 40, 80, 80);
            g2.setStroke(new BasicStroke(1f));
        }

        if (activeMission == MissionType.DEFEND_FRIENDLY_BASE && friendlyBase.alive) {
            g2.setColor(new Color(255, 235, 120, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(sx(friendlyBase.x) - 40, sy(friendlyBase.y) - 40, 80, 80);
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private void drawGrid(Graphics2D g2) {
        double cx = camX(), cy = camY();
        int grid = 250;
        g2.setColor(new Color(255, 255, 255, 10));

        int startX = (int)(Math.floor(cx / grid) * grid);
        int startY = (int)(Math.floor(cy / grid) * grid);

        double viewW = W / zoom;
        double viewH = H / zoom;

        for (int x = startX; x < cx + viewW + grid; x += grid) g2.drawLine(sx(x), 0, sx(x), H);
        for (int y = startY; y < cy + viewH + grid; y += grid) g2.drawLine(0, sy(y), W, sy(y));
    }

    private void drawObstacle(Graphics2D g2, Obstacle o) {
        int r = (int)o.r;
        g2.setColor(new Color(70, 70, 85));
        g2.fillOval(sx(o.x) - r, sy(o.y) - r, r * 2, r * 2);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.drawOval(sx(o.x) - r, sy(o.y) - r, r * 2, r * 2);
    }

    private void drawPOI(Graphics2D g2, POI p) {
        int x = sx(p.x), y = sy(p.y);
        if (p.type == POIType.WRECK) {
            g2.setColor(new Color(140, 140, 140, 120));
            g2.fillRect(x - 10, y - 6, 20, 12);
            g2.setColor(new Color(255, 255, 255, 50));
            g2.drawRect(x - 10, y - 6, 20, 12);
        } else {
            g2.setColor(new Color(120, 120, 255, 80));
            g2.fillOval(x - 10, y - 10, 20, 20);
            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawOval(x - 10, y - 10, 20, 20);
        }
    }

    private void drawBase(Graphics2D g2, Base b) {
        if (!b.alive) return;
        int cx = sx(b.x), cy = sy(b.y);

        g2.setColor(new Color(120, 190, 255, b.team == Team.FRIENDLY ? 35 : 22));
        int ar = (int)b.auraRadius;
        g2.drawOval(cx - ar, cy - ar, ar * 2, ar * 2);

        g2.setColor(b.team == Team.FRIENDLY ? new Color(60, 180, 255) : new Color(255, 90, 90));
        g2.fillRoundRect(cx - 24, cy - 24, 48, 48, 12, 12);

        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(cx - 28, cy + 30, 56, 6);
        int hpw = (int)(56 * clamp(b.hp / (double)b.hpMax, 0, 1));
        g2.setColor(new Color(120, 240, 160));
        g2.fillRect(cx - 28, cy + 30, hpw, 6);

        float sf = (float)(b.shield / b.shieldMax);
        sf = clamp(sf, 0f, 1f);
        int sr = (int)b.shieldRadius;
        g2.setStroke(new BasicStroke(3f));
        g2.setColor(new Color(120, 190, 255, 120));
        g2.drawArc(cx - sr, cy - sr, sr * 2, sr * 2, 90, (int)(-360 * sf));
        g2.setStroke(new BasicStroke(1f));

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(Color.WHITE);
        g2.drawString(b.name, cx - 40, cy - 34);
    }

    private void drawPlayer(Graphics2D g2, Player p) {

        // ---------- Shield ring ----------
        if (p.shield > 0 && p.modules.isModuleOnline(ModuleType.SHIELD_GEN)) {
            float frac = (float)(p.shield / p.shieldMax);
            frac = clamp(frac, 0f, 1f);

            int r = (int)p.shieldRadius;
            int sx0 = this.sx(p.x) - r;
            int sy0 = this.sy(p.y) - r;

            // subtle "hit flash" on shield
            int flashA = (p.shieldHitFlashFrames > 0) ? 220 : 160;

            g2.setStroke(new BasicStroke(4f));
            g2.setColor(new Color(120, 190, 255, flashA));
            g2.drawArc(sx0, sy0, r * 2, r * 2, 90, (int)(-360 * frac));

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(120, 190, 255, 70));
            g2.drawOval(sx0, sy0, r * 2, r * 2);
        }

        // ---------- Hull (base) ----------
        Color baseHull = new Color(70, 200, 120);

        // if hull took damage recently, tint it warmer
        if (p.hullHitFlashFrames > 0) {
            baseHull = new Color(220, 120, 80);
        }

        drawTriangle(
                g2,
                p.x,
                p.y,
                p.hullAngle,
                baseHull,
                18,
                12
        );

        // ---------- Overlay flashes ----------
        // shield flash: blue overlay on the hull
        if (p.shieldHitFlashFrames > 0) {
            drawTriangleOverlay(g2, p.x, p.y, p.hullAngle, new Color(120, 190, 255, 110), 18, 12);
        }
        // hull flash: red overlay on the hull
        if (p.hullHitFlashFrames > 0) {
            drawTriangleOverlay(g2, p.x, p.y, p.hullAngle, new Color(255, 90, 90, 95), 18, 12);
        }

        // ---------- Aim line (helps readability) ----------
        // short line showing where you're aiming (mouse / auto-aim)
        g2.setColor(new Color(255, 255, 255, 55));
        double ax = p.x + Math.cos(p.aimAngle) * 46;
        double ay = p.y + Math.sin(p.aimAngle) * 46;
        g2.drawLine(this.sx(p.x), this.sy(p.y), this.sx(ax), this.sy(ay));

        // ---------- Main cannons (purple squares) ----------
        Point2D nose  = localToWorld(p.x, p.y, p.hullAngle, 18, 0);
        Point2D left  = localToWorld(p.x, p.y, p.hullAngle, -14, -10);
        Point2D right = localToWorld(p.x, p.y, p.hullAngle, -14, 10);

        g2.setColor(new Color(170, 90, 255));
        drawSquare(g2, nose.x, nose.y, 6);
        drawSquare(g2, left.x, left.y, 6);
        drawSquare(g2, right.x, right.y, 6);

        // ---------- CIWS core ----------
        g2.setColor(new Color(255, 160, 60));
        drawSquare(g2, p.x, p.y, 7);

        // ---------- Engine glow (rear) ----------
        Point2D rear = localToWorld(p.x, p.y, p.hullAngle, -18, 0);
        g2.setColor(new Color(120, 190, 255, 90));
        g2.fillOval(this.sx(rear.x) - 6, this.sy(rear.y) - 4, 12, 8);

        // Afterburner visual
        if (abilityAfterburnerHeld && p.modules.isModuleOnline(ModuleType.ENGINE) && p.modules.isModuleOnline(ModuleType.THRUSTER)) {
            g2.setColor(new Color(255, 190, 90, 110));
            g2.fillOval(this.sx(rear.x) - 8, this.sy(rear.y) - 5, 16, 10);
        }
    }




    private void drawShip(Graphics2D g2, Ship s, Color body) {
        if (!s.alive) return;

        float sf = (float)(s.shield / s.shieldMax);
        sf = clamp(sf, 0f, 1f);
        int sr = (int)s.shieldRadius;

        g2.setStroke(new BasicStroke(3f));
        g2.setColor(new Color(255, 180, 90, 120));
        g2.drawArc(sx(s.x) - sr, sy(s.y) - sr, sr * 2, sr * 2, 90, (int)(-360 * sf));
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 180, 90, 50));
        g2.drawOval(sx(s.x) - sr, sy(s.y) - sr, sr * 2, sr * 2);

        drawTriangle(g2, s.x, s.y, s.facing, body, 18, 12);

        if (s.modules.anyDisabled()) {
            g2.setColor(new Color(255, 80, 80));
            g2.fillRect(sx(s.x) - 2, sy(s.y) - 18, 4, 4);
        }
    }

    private void drawCapital(Graphics2D g2, CapitalShip c) {
        if (!c.alive) return;

        int r = (int)c.radius;
        g2.setColor(c.team == Team.FRIENDLY ? new Color(80, 160, 240) : new Color(240, 90, 90));
        g2.fillRoundRect(sx(c.x) - r, sy(c.y) - r, r * 2, r * 2, 22, 22);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.drawRoundRect(sx(c.x) - r, sy(c.y) - r, r * 2, r * 2, 22, 22);

        float sf = (float)(c.shield / c.shieldMax);
        sf = clamp(sf, 0f, 1f);
        int sr = (int)c.shieldRadius;
        g2.setStroke(new BasicStroke(4f));
        g2.setColor(new Color(120, 190, 255, 110));
        g2.drawArc(sx(c.x) - sr, sy(c.y) - sr, sr * 2, sr * 2, 90, (int)(-360 * sf));
        g2.setStroke(new BasicStroke(1f));

        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(sx(c.x) - 50, sy(c.y) + r + 10, 100, 8);
        int hpw = (int)(100 * clamp(c.hp / (double)c.hpMax, 0, 1));
        g2.setColor(new Color(120, 240, 160));
        g2.fillRect(sx(c.x) - 50, sy(c.y) + r + 10, hpw, 8);

        // Draw turrets (dead turrets are darker/red)
        for (Turret t : c.turrets) {
            Point2D tp = localToWorld(c.x, c.y, c.facing, t.localX, t.localY);

            g2.setColor(t.alive() ? new Color(30, 30, 30) : new Color(80, 10, 10));
            g2.fillOval(sx(tp.x) - 5, sy(tp.y) - 5, 10, 10);

            g2.setColor(new Color(255, 255, 255, 110));
            g2.drawOval(sx(tp.x) - 5, sy(tp.y) - 5, 10, 10);

            if (t.alive()) {
                g2.setColor(new Color(40, 40, 40));
                g2.drawLine(
                        sx(tp.x), sy(tp.y),
                        sx(tp.x + Math.cos(t.angle) * 18),
                        sy(tp.y + Math.sin(t.angle) * 18)
                );
            }
        }

        // show hull segments faintly (helps you see collision)
        g2.setColor(new Color(255, 255, 255, 25));
        for (Point2D lp : c.hullPoints) {
            Point2D wp = localToWorld(c.x, c.y, c.facing, lp.x, lp.y);
            int rr = (int)c.hullSegmentRadius;
            g2.drawOval(sx(wp.x) - rr, sy(wp.y) - rr, rr * 2, rr * 2);
        }
    }

    private void drawDrone(Graphics2D g2, Drone d) {
        drawTriangle(g2, d.x, d.y, d.aimAngle, new Color(120, 240, 160), 12, 8);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawOval(sx(d.x) - (int)d.radius, sy(d.y) - (int)d.radius, (int)d.radius * 2, (int)d.radius * 2);
    }

    // Textured triangle hull (more shapes = "texture")
    private void drawTriangle(Graphics2D g2, double wx, double wy, double angle, Color fill, int forward, int halfWidth) {
        AffineTransform old = g2.getTransform();
        g2.translate(sx(wx), sy(wy));
        g2.rotate(angle);

        Polygon tri = new Polygon();
        tri.addPoint(forward, 0);
        tri.addPoint(-forward, -halfWidth);
        tri.addPoint(-forward, halfWidth);

        g2.setColor(fill);
        g2.fillPolygon(tri);

        // Outline
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawPolygon(tri);

        // Panel lines
        g2.setColor(new Color(0, 0, 0, 70));
        g2.drawLine(-forward + 4, -halfWidth + 2, forward - 6, 0);
        g2.drawLine(-forward + 4, halfWidth - 2, forward - 6, 0);
        g2.drawLine(-forward + 8, -halfWidth + 3, -forward + 8, halfWidth - 3);

        // Center spine plate
        g2.setColor(new Color(255, 255, 255, 35));
        g2.fillRect(-forward + 6, -2, forward + 2, 4);

        // Engine glow (rear)
        g2.setColor(new Color(120, 190, 255, 80));
        g2.fillOval(-forward - 6, -4, 10, 8);

        g2.setTransform(old);
    }

    private void drawSquare(Graphics2D g2, double wx, double wy, int size) {
        int half = size / 2;
        g2.fillRect(sx(wx) - half, sy(wy) - half, size, size);
    }

    private void drawMissile(Graphics2D g2, Missile m) {
        Color c = (m.team == Team.FRIENDLY) ? new Color(160, 210, 255) : new Color(255, 120, 120);
        drawTriangle(g2, m.x, m.y, m.angle, c, 10, 6);
    }

    private void drawBullet(Graphics2D g2, Bullet b) {
        if (b.type == BulletType.CIWS) g2.setColor(new Color(220, 220, 220));
        else g2.setColor(b.team == Team.FRIENDLY ? new Color(250, 240, 120) : new Color(255, 160, 160));

        int d = (int)(b.radius * 2);
        g2.fillOval(sx(b.x) - (int)b.radius, sy(b.y) - (int)b.radius, d, d);
    }

    private void drawTracer(Graphics2D g2, Tracer t) {
        g2.setColor(new Color(255, 170, 70, 140));
        g2.drawLine(sx(t.x1), sy(t.y1), sx(t.x2), sy(t.y2));
    }

    private void drawExplosion(Graphics2D g2, Explosion ex) {
        float t = ex.age / (float)ex.maxAge;
        int radius = (int)(6 + ex.size * t);
        int alpha = (int)(220 * (1.0f - t));
        alpha = clamp(alpha, 0, 255);

        g2.setColor(new Color(255, 130, 80, alpha));
        g2.drawOval(sx(ex.x) - radius, sy(ex.y) - radius, radius * 2, radius * 2);

        g2.setColor(new Color(255, 220, 160, Math.max(0, alpha - 90)));
        g2.drawOval(sx(ex.x) - (int)(radius * 0.55), sy(ex.y) - (int)(radius * 0.55),
                (int)(radius * 1.10), (int)(radius * 1.10));
    }

    private void drawHUD(Graphics2D g2) {
        g2.setFont(new Font("Consolas", Font.PLAIN, 16));
        g2.setColor(Color.WHITE);

        g2.drawString("HP: " + (int)Math.ceil(player.hp) + "/" + player.hpMax, 12, 22);
        g2.drawString(String.format("Shield: %.0f/%.0f", player.shield, player.shieldMax), 12, 44);
        g2.drawString("Missiles: " + player.missiles + "/" + player.missilesMax, 12, 66);
        g2.drawString("Drones: " + drones.size() + "/" + player.maxDrones, 12, 88);
        g2.drawString("Score: " + score, 12, 110);
        g2.drawString("Auto-aim: " + (autoAim ? "ON" : "OFF") + " (E)", 12, 132);
        g2.drawString(String.format("Zoom: %.2fx", zoom), 12, 154);

        if (activeMission != null) {
            g2.setColor(new Color(255, 235, 120));
            g2.drawString("Mission: " + activeMission + (activeMission == MissionType.DEFEND_FRIENDLY_BASE ? (" (" + (int)(missionProgress * DT) + "s/30s)") : ""), 12, 176);
            g2.setColor(Color.WHITE);
        } else {
            g2.drawString("Mission: none (open Comms C -> 5)", 12, 176);
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString("Modules: " + player.modules.shortStatus(), 12, 198);

        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("Friend Command: " + friendlyCommand, 12, 220);

        int boxY = H - 160;
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(10, boxY, 680, 140, 14, 14);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        int yy = boxY + 20;
        for (String s : log.lines()) {
            g2.drawString(s, 18, yy);
            yy += 14;
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawString("WASD move | LMB gun | Hold Q missiles | F drone | SPACE shield | SHIFT boost | C comms | J sector | F5 save | F9 load | R restart",
                12, H - 10);
    }

    private void drawMinimap(Graphics2D g2) {
        int mmW = 220, mmH = 220;
        int x0 = 12, y0 = H - mmH - 180;
        if (y0 < 10) y0 = 10;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(x0, y0, mmW, mmH, 14, 14);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.drawRoundRect(x0, y0, mmW, mmH, 14, 14);

        double sxm = mmW / WORLD_W;
        double sym = mmH / WORLD_H;

        g2.setColor(new Color(160, 160, 160, 40));
        for (Obstacle o : obstacles) {
            int px = x0 + (int)(o.x * sxm);
            int py = y0 + (int)(o.y * sym);
            int rr = (int)Math.max(1, o.r * sxm * 0.25);
            g2.fillOval(px - rr, py - rr, rr * 2, rr * 2);
        }

        for (POI p : pois) {
            int px = x0 + (int)(p.x * sxm);
            int py = y0 + (int)(p.y * sym);
            g2.setColor(p.type == POIType.WRECK ? new Color(150, 150, 150, 140) : new Color(120, 120, 255, 140));
            g2.fillRect(px - 2, py - 2, 4, 4);
        }

        if (friendlyBase.alive) {
            int bx = x0 + (int)(friendlyBase.x * sxm);
            int by = y0 + (int)(friendlyBase.y * sym);
            g2.setColor(new Color(60, 180, 255));
            g2.fillRect(bx - 4, by - 4, 8, 8);
        }
        if (enemyBase.alive) {
            int bx = x0 + (int)(enemyBase.x * sxm);
            int by = y0 + (int)(enemyBase.y * sym);
            g2.setColor(new Color(255, 90, 90));
            g2.fillRect(bx - 4, by - 4, 8, 8);
        }

        for (CapitalShip c : capitals) {
            int px = x0 + (int)(c.x * sxm);
            int py = y0 + (int)(c.y * sym);
            g2.setColor(c.team == Team.FRIENDLY ? new Color(80, 160, 240) : new Color(240, 90, 90));
            g2.fillOval(px - 4, py - 4, 8, 8);
        }

        g2.setColor(new Color(120, 240, 160));
        for (Ship f : friendlies) {
            int px = x0 + (int)(f.x * sxm);
            int py = y0 + (int)(f.y * sym);
            g2.fillRect(px - 2, py - 2, 4, 4);
        }

        g2.setColor(new Color(255, 120, 120));
        for (EnemyShip en : enemies) {
            int px = x0 + (int)(en.x * sxm);
            int py = y0 + (int)(en.y * sym);
            g2.fillRect(px - 2, py - 2, 4, 4);
        }

        // mission marker on minimap
        if (activeMission != null) {
            g2.setColor(new Color(255, 235, 120));
            if (activeMission == MissionType.CLEAR_POI && missionPOI != null) {
                int mx = x0 + (int)(missionPOI.x * sxm);
                int my = y0 + (int)(missionPOI.y * sym);
                g2.drawOval(mx - 5, my - 5, 10, 10);
            } else if (activeMission == MissionType.DESTROY_ENEMY_BASE && enemyBase.alive) {
                int mx = x0 + (int)(enemyBase.x * sxm);
                int my = y0 + (int)(enemyBase.y * sym);
                g2.drawOval(mx - 5, my - 5, 10, 10);
            } else if (activeMission == MissionType.DEFEND_FRIENDLY_BASE && friendlyBase.alive) {
                int mx = x0 + (int)(friendlyBase.x * sxm);
                int my = y0 + (int)(friendlyBase.y * sym);
                g2.drawOval(mx - 5, my - 5, 10, 10);
            }
        }

        int ppx = x0 + (int)(player.x * sxm);
        int ppy = y0 + (int)(player.y * sym);
        g2.setColor(Color.WHITE);
        g2.fillOval(ppx - 3, ppy - 3, 6, 6);

        double cx = camX(), cy = camY();
        int vx = x0 + (int)(cx * sxm);
        int vy = y0 + (int)(cy * sym);
        int vw = (int)((W / zoom) * sxm);
        int vh = (int)((H / zoom) * sym);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRect(vx, vy, vw, vh);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawString("MINIMAP (Sector " + sectorId + ")", x0 + 8, y0 + 16);
    }

    private void drawComms(Graphics2D g2) {
        if (!commsOpen) return;

        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(340, 90, 600, 420, 18, 18);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.drawString("COMMS MENU", 370, 130);

        g2.setFont(new Font("Consolas", Font.PLAIN, 16));
        g2.drawString("1) Request resupply/repairs (near Friendly Base)", 370, 175);
        g2.drawString("2) Call escort drone", 370, 205);
        g2.drawString("3) Taunt enemy faction", 370, 235);
        g2.drawString("4) Request ceasefire", 370, 265);
        g2.drawString("5) Mission board (toggle missions)", 370, 295);

        g2.drawString("6) Command: Escort me", 370, 335);
        g2.drawString("7) Command: Focus fire my target", 370, 365);
        g2.drawString("8) Command: Return to base / resupply", 370, 395);
        g2.drawString("9) Request missiles from nearby friendly ship", 370, 425);

        g2.drawString("C) Close comms", 370, 465);

        g2.setFont(new Font("Consolas", Font.ITALIC, 13));
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawString("(Movement/shooting paused while comms open)", 370, 495);
    }

    private void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRect(0, 0, W, H);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 52));
        String msg = "GAME OVER";
        int ww = g2.getFontMetrics().stringWidth(msg);
        g2.drawString(msg, (W - ww) / 2, H / 2 - 10);

        g2.setFont(new Font("Arial", Font.PLAIN, 22));
        String msg2 = "Press R to restart";
        int w2 = g2.getFontMetrics().stringWidth(msg2);
        g2.drawString(msg2, (W - w2) / 2, H / 2 + 28);
    }

    // ---------------- Targeting helpers ----------------
    private Ship findClosestEnemyOrCapital(double x, double y) {
        Ship best = null;
        double bestD = Double.POSITIVE_INFINITY;

        for (EnemyShip en : enemies) {
            if (!en.alive) continue;
            double d = dist2(x, y, en.x, en.y);
            if (d < bestD) { bestD = d; best = en; }
        }
        for (CapitalShip cap : capitals) {
            if (!cap.alive) continue;
            if (cap.team != Team.ENEMY) continue;
            double d = dist2(x, y, cap.x, cap.y);
            if (d < bestD) { bestD = d; best = cap; }
        }
        if (enemyBase.alive) {
            double d = dist2(x, y, enemyBase.x, enemyBase.y);
            if (d < bestD) { bestD = d; best = enemyBase; }
        }
        return best;
    }

    private Ship findClosestFriendlyOrPlayer(double x, double y) {
        Ship best = player;
        double bestD = dist2(x, y, player.x, player.y);

        for (Ship f : friendlies) {
            if (!f.alive) continue;
            double d = dist2(x, y, f.x, f.y);
            if (d < bestD) { bestD = d; best = f; }
        }
        for (CapitalShip cap : capitals) {
            if (!cap.alive) continue;
            if (cap.team != Team.FRIENDLY) continue;
            double d = dist2(x, y, cap.x, cap.y);
            if (d < bestD) { bestD = d; best = cap; }
        }
        if (friendlyBase.alive) {
            double d = dist2(x, y, friendlyBase.x, friendlyBase.y);
            if (d < bestD) { bestD = d; best = friendlyBase; }
        }
        return best;
    }

    // ---------------- Obstacles ----------------
    private double[] pushOutOfObstacles(double x, double y, double radius) {
        double px = x, py = y;
        for (int iter = 0; iter < 4; iter++) {
            boolean pushedAny = false;
            for (Obstacle o : obstacles) {
                double dx = px - o.x;
                double dy = py - o.y;
                double dist = Math.hypot(dx, dy);
                double minDist = radius + o.r;
                if (dist < minDist && dist > 0.0001) {
                    double push = (minDist - dist);
                    dx /= dist; dy /= dist;
                    px += dx * push;
                    py += dy * push;
                    pushedAny = true;
                } else if (dist <= 0.0001) {
                    px += 1.0;
                    pushedAny = true;
                }
            }
            if (!pushedAny) break;
        }
        px = clamp(px, radius, WORLD_W - radius);
        py = clamp(py, radius, WORLD_H - radius);
        return new double[]{px, py};
    }

    private boolean circleHitsObstacle(double x, double y, double r) {
        for (Obstacle o : obstacles) {
            if (dist2(x, y, o.x, o.y) < sq(r + o.r)) return true;
        }
        return false;
    }

    private boolean outOfWorld(double x, double y, double pad) {
        return x < -pad || x > WORLD_W + pad || y < -pad || y > WORLD_H + pad;
    }

    // ---------------- Input ----------------
    @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }

    @Override public void mousePressed(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) shooting = true; }
    @Override public void mouseReleased(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) shooting = false; }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double prev = zoom;
        zoom *= (e.getWheelRotation() < 0) ? 1.08 : 0.92;
        zoom = clamp(zoom, 0.6, 1.6);

        // keep mouse world point stable-ish
        double mxWorldBefore = camX() + mouseX / prev;
        double myWorldBefore = camY() + mouseY / prev;

        double mxWorldAfter = camX() + mouseX / zoom;
        double myWorldAfter = camY() + mouseY / zoom;

        player.x += (mxWorldBefore - mxWorldAfter) * 0.15;
        player.y += (myWorldBefore - myWorldAfter) * 0.15;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> up = true;
            case KeyEvent.VK_S -> down = true;
            case KeyEvent.VK_A -> left = true;
            case KeyEvent.VK_D -> right = true;

            case KeyEvent.VK_E -> autoAim = !autoAim;

            case KeyEvent.VK_Q -> missileHeld = true;

            case KeyEvent.VK_F -> summonDronePressed = true;

            case KeyEvent.VK_SPACE -> abilityShieldOverchargeHeld = true;
            case KeyEvent.VK_SHIFT -> abilityAfterburnerHeld = true;

            case KeyEvent.VK_C -> {
                if (!gameOver) commsOpen = !commsOpen;
                log.push(commsOpen ? "System: Comms opened." : "System: Comms closed.");
            }

            case KeyEvent.VK_1 -> commsAction(1);
            case KeyEvent.VK_2 -> commsAction(2);
            case KeyEvent.VK_3 -> commsAction(3);
            case KeyEvent.VK_4 -> commsAction(4);
            case KeyEvent.VK_5 -> commsAction(5);
            case KeyEvent.VK_6 -> commsAction(6);
            case KeyEvent.VK_7 -> commsAction(7);
            case KeyEvent.VK_8 -> commsAction(8);
            case KeyEvent.VK_9 -> commsAction(9);

            case KeyEvent.VK_J -> { if (!gameOver && !commsOpen) jumpSector(); }
            case KeyEvent.VK_F5 -> { if (!gameOver) quickSave(); }
            case KeyEvent.VK_F9 -> { if (!gameOver) quickLoad(); }

            case KeyEvent.VK_R -> { if (gameOver) resetGame(); }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> up = false;
            case KeyEvent.VK_S -> down = false;
            case KeyEvent.VK_A -> left = false;
            case KeyEvent.VK_D -> right = false;

            case KeyEvent.VK_Q -> missileHeld = false;

            case KeyEvent.VK_SPACE -> abilityShieldOverchargeHeld = false;
            case KeyEvent.VK_SHIFT -> abilityAfterburnerHeld = false;
        }
    }
    @Override public void keyTyped(KeyEvent e) {}

    private void commsAction(int option) {
        if (!commsOpen) return;

        switch (option) {
            case 1 -> {
                if (friendlyBase.alive && friendlyBase.isInAura(player.x, player.y)) {
                    player.missiles = player.missilesMax;
                    player.shield = player.shieldMax;
                    player.hp = player.hpMax;
                    player.modules.fullRepair();
                    log.push("Friendly Base: Full resupply and repairs complete.");
                } else {
                    log.push("Friendly Base: Out of range. Return to base.");
                }
            }
            case 2 -> summonDrone();
            case 3 -> log.push("Enemy: \"Your transmissions are adorable.\"");
            case 4 -> log.push("Enemy: \"Ceasefire denied.\"");
            case 5 -> {
                // rotate missions
                if (activeMission == null) startMission(MissionType.CLEAR_POI);
                else if (activeMission == MissionType.CLEAR_POI) startMission(MissionType.DESTROY_ENEMY_BASE);
                else if (activeMission == MissionType.DESTROY_ENEMY_BASE) startMission(MissionType.DEFEND_FRIENDLY_BASE);
                else { activeMission = null; missionPOI = null; missionProgress = 0; log.push("Mission: cleared."); }
            }
            case 6 -> {
                friendlyCommand = FriendlyCommand.ESCORT_PLAYER;
                log.push("Friendly: Escort acknowledged.");
            }
            case 7 -> {
                friendlyCommand = FriendlyCommand.FOCUS_FIRE;
                log.push("Friendly: Focus fire acknowledged.");
            }
            case 8 -> {
                friendlyCommand = FriendlyCommand.RETURN_TO_BASE;
                log.push("Friendly: Returning to base.");
            }
            case 9 -> {
                // request missiles from nearest friendly ship within range
                Ship donor = null;
                double bestD2 = Double.POSITIVE_INFINITY;
                for (Ship s : friendlies) {
                    if (!s.alive) continue;
                    double d2 = dist2(player.x, player.y, s.x, s.y);
                    if (d2 < bestD2) { bestD2 = d2; donor = s; }
                }
                if (donor instanceof FriendlyShip fs && bestD2 < sq(220) && fs.missiles > 0) {
                    int transfer = Math.min(6, fs.missiles);
                    int space = player.missilesMax - player.missiles;
                    transfer = Math.min(transfer, space);
                    if (transfer > 0) {
                        fs.missiles -= transfer;
                        player.missiles += transfer;
                        log.push("Friendly: Transferred " + transfer + " missiles.");
                    } else {
                        log.push("Friendly: You're already full on missiles.");
                    }
                } else {
                    log.push("Friendly: No missile-capable ship nearby (within ~220).");
                }
            }
        }
    }

    private void resetGame() {
        gameOver = false;
        score = 0;
        commsOpen = false;
        autoAim = false;

        activeMission = null;
        missionPOI = null;
        missionProgress = 0;

        friendlyCommand = FriendlyCommand.DEFAULT;

        sectorId = 1;
        generateWorldForSector(sectorId);

        player.x = WORLD_W / 2.0;
        player.y = WORLD_H / 2.0;
        player.hp = player.hpMax;
        player.shield = player.shieldMax;
        player.missiles = player.missilesMax;
        player.modules.fullRepair();
        player.afterburnerHeat = 0;

        friendlies.clear();
        enemies.clear();
        capitals.clear();
        drones.clear();
        bullets.clear();
        missiles.clear();
        explosions.clear();
        tracers.clear();

        bulletSpawnQueue.clear();
        missileSpawnQueue.clear();
        explosionSpawnQueue.clear();
        tracerSpawnQueue.clear();
        droneSpawnQueue.clear();

        friendlyBase.reset();
        enemyBase.reset();

        lastEnemySpawnMs = 0;
        lastFriendlySpawnMs = 0;
        lastCapitalSpawnMs = 0;

        log.clear();
        log.push("System: Restarted.");
    }

    // ---------------- Types ----------------
    enum Team { FRIENDLY, ENEMY }
    enum BulletType { NORMAL, CIWS }
    enum POIType { WRECK, NEUTRAL_STATION }
    enum ModuleType { ENGINE, THRUSTER, SHIELD_GEN, WEAPON }

    static class Obstacle {
        double x, y, r;
        Obstacle(double x, double y, double r) { this.x = x; this.y = y; this.r = r; }
    }

    static class POI {
        double x, y;
        POIType type;
        POI(double x, double y, POIType type) { this.x = x; this.y = y; this.type = type; }
    }

    static class Bullet {
        double x, y, vx, vy;
        int life;
        double radius;
        int damage;
        Team team;
        BulletType type;

        Bullet(double x, double y, double vx, double vy, Team team, BulletType type, int life, double radius, int damage) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.team = team; this.type = type;
            this.life = life;
            this.radius = radius;
            this.damage = damage;
        }
    }

    static class Tracer {
        double x1, y1, x2, y2;
        int life;
        Tracer(double x1, double y1, double x2, double y2, int life) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.life = life;
        }
    }

    static class Explosion {
        double x, y;
        int age = 0;
        int maxAge = 18;
        int size;

        Explosion(double x, double y, int size) { this.x = x; this.y = y; this.size = size; }

        static Explosion smallShieldHit(double x, double y) {
            Explosion e = new Explosion(x, y, 10);
            e.maxAge = 10;
            return e;
        }
    }

    static class Point2D {
        double x, y;
        Point2D(double x, double y) { this.x = x; this.y = y; }
    }

    static Point2D localToWorld(double cx, double cy, double angle, double lx, double ly) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Point2D(cx + lx * cos - ly * sin, cy + lx * sin + ly * cos);
    }

    static class Modules {
        private final EnumMap<ModuleType, Double> hp = new EnumMap<>(ModuleType.class);
        private final EnumMap<ModuleType, Double> max = new EnumMap<>(ModuleType.class);

        Modules(double base) {
            for (ModuleType t : ModuleType.values()) {
                max.put(t, base);
                hp.put(t, base);
            }
        }

        boolean isModuleOnline(ModuleType t) { return hp.get(t) > 0.01; }

        boolean anyDisabled() {
            for (ModuleType t : ModuleType.values()) if (!isModuleOnline(t)) return true;
            return false;
        }

        void damageRandomModule(double amount, Random rng) {
            ModuleType[] all = ModuleType.values();
            ModuleType t = all[rng.nextInt(all.length)];
            damageModule(t, amount);
        }

        void damageModule(ModuleType t, double amount) {
            double v = hp.get(t) - amount;
            hp.put(t, Math.max(0.0, v));
        }

        void repairTick(double amount) {
            for (ModuleType t : ModuleType.values()) {
                double v = hp.get(t);
                double m = max.get(t);
                if (v < m) hp.put(t, Math.min(m, v + amount));
            }
        }

        void fullRepair() {
            for (ModuleType t : ModuleType.values()) hp.put(t, max.get(t));
        }

        String shortStatus() {
            return String.format("ENG:%s THR:%s SHD:%s WPN:%s",
                    isModuleOnline(ModuleType.ENGINE) ? "OK" : "OFF",
                    isModuleOnline(ModuleType.THRUSTER) ? "OK" : "OFF",
                    isModuleOnline(ModuleType.SHIELD_GEN) ? "OK" : "OFF",
                    isModuleOnline(ModuleType.WEAPON) ? "OK" : "OFF");
        }
    }

    static abstract class Ship {
        double x, y;
        double vx, vy;
        double facing = 0;

        double speed = 1.6;
        double radius = 16;

        int hpMax = 6;
        double hp = hpMax;

        double shieldMax = 8;
        double shield = shieldMax;
        double shieldRegenPerSec = 0.45;
        double shieldRadius = 26;

        boolean alive = true;

        long lastShotMs = 0;
        long shotCooldownMs = 700;
        double bulletSpeed = 8.0;

        long lastAbilityMs = 0;

        Modules modules = new Modules(10);

        boolean fleeing = false;
        boolean kamikaze = false;

        Ship(double x, double y) { this.x = x; this.y = y; }

        void regenShield(double dt) {
            if (modules.isModuleOnline(ModuleType.SHIELD_GEN) && shield < shieldMax) {
                shield = Math.min(shieldMax, shield + shieldRegenPerSec * dt);
            }
        }

        boolean applyBulletDamage(int dmg, List<Explosion> fx) {
            double preHp = hp;

            if (!alive) return false;

            boolean shieldHit = false;
            if (shield > 0 && modules.isModuleOnline(ModuleType.SHIELD_GEN)) {
                shieldHit = true;
                shield -= dmg;
                if (shield < 0) {
                    hp += shield;
                    shield = 0;
                }
            } else {
                hp -= dmg;
            }

            if (dmg > 0 && Math.random() < 0.18) {
                modules.damageRandomModule(4.0, new Random());
            }
            if (this instanceof Player p) {
                if (shieldHit) p.shieldHitFlashFrames = 8;
                if (hp < preHp) p.hullHitFlashFrames = 10;
            }
            if (hp <= 0) {
                alive = false;
                fx.add(new Explosion(x, y, 28));
            }
            return shieldHit;
        }

        void takeDamage(int dmg, List<Explosion> fx) { applyBulletDamage(dmg, fx); }

        void tryShoot(long now, double tx, double ty, List<Bullet> out) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastShotMs < shotCooldownMs) return;

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) return;
            dx /= len; dy /= len;

            out.add(new Bullet(x + dx * 18, y + dy * 18, dx * bulletSpeed, dy * bulletSpeed,
                    getTeam(), BulletType.NORMAL, 140, 4.0, 1));
            lastShotMs = now;
        }

        abstract Team getTeam();

        void updateCommonAI(long now, Ship target, Player player, Base myBase, Base enemyBase, List<Obstacle> obstacles) {
            if (!alive) return;

            double hpFrac = hp / hpMax;
            if (!fleeing && !kamikaze && hpFrac < 0.30) {
                if (Math.random() < 0.70) fleeing = true;
                else kamikaze = true;
            }

            double moveSpeed = modules.isModuleOnline(ModuleType.ENGINE) ? speed : speed * 0.15;

            double tx, ty;

            if (kamikaze) {
                if (target != null) { tx = target.x; ty = target.y; }
                else { tx = player.x; ty = player.y; }
                moveSpeed *= 1.6;
            } else if (fleeing && myBase != null && myBase.alive) {
                tx = myBase.x; ty = myBase.y;
                moveSpeed *= 1.4;
            } else if (target != null) {
                tx = target.x; ty = target.y;
            } else {
                tx = enemyBase != null ? enemyBase.x : player.x;
                ty = enemyBase != null ? enemyBase.y : player.y;
            }

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len > 0.001) { dx /= len; dy /= len; }

            // dodge/strafe
            double perpX = -dy;
            double perpY = dx;
            double strafe = Math.sin((now * 0.001) + (x + y) * 0.0005) * 0.9;

            double mvx = dx * moveSpeed + perpX * strafe;
            double mvy = dy * moveSpeed + perpY * strafe;

            if (!modules.isModuleOnline(ModuleType.THRUSTER)) {
                mvx *= 0.65;
                mvy *= 0.65;
            }

            x += mvx;
            y += mvy;

            facing = Math.atan2(mvy, mvx);

            x = clamp(x, radius, WORLD_W - radius);
            y = clamp(y, radius, WORLD_H - radius);

            if (kamikaze && target != null && dist2(x, y, target.x, target.y) < sq(radius + target.radius + 6)) {
                alive = false;
            }
        }

        void tryUseAbility(long now, Ship target) {}
    }

    static class Player extends Ship {
        double hullAngle = 0;
        double aimAngle = 0;

        int missilesMax = 9999999;
        int missiles = missilesMax;
        int maxDrones = 9999999;

        long lastCIWSMs = 0;
        long ciwsCooldownMs = 55;
        double ciwsRange = 220;
        double ciwsEnemyOverrideRange = 95;
        double ciwsBulletSpeed = 15.0;

        double afterburnerHeat = 0;

        // --- Visual hit feedback ---
        int hullHitFlashFrames = 0;     // flashes when HP is damaged
        int shieldHitFlashFrames = 0;   // flashes when shield absorbs damage

        Player(double x, double y) {
            super(x, y);
            speed = 4.2;
            hpMax = 14; hp = hpMax;
            shieldMax = 12; shield = shieldMax;
            shieldRegenPerSec = 0.75;
            radius = 16;
            shotCooldownMs = 500;
            bulletSpeed = 10.5;
            shieldRadius = 30;

            modules = new Modules(12);
        }

        @Override Team getTeam() { return Team.FRIENDLY; }

        void tryShootTriple(long now, double tx, double ty, List<Bullet> out) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastShotMs < shotCooldownMs) return;

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) return;
            dx /= len; dy /= len;

            Point2D nose = localToWorld(x, y, hullAngle, 18, 0);
            Point2D left = localToWorld(x, y, hullAngle, -18, -12);
            Point2D right = localToWorld(x, y, hullAngle, -18, 12);

            out.add(new Bullet(nose.x, nose.y, dx * bulletSpeed, dy * bulletSpeed, Team.FRIENDLY, BulletType.NORMAL, 120, 4.0, 1));
            out.add(new Bullet(left.x, left.y, dx * bulletSpeed, dy * bulletSpeed, Team.FRIENDLY, BulletType.NORMAL, 120, 4.0, 1));
            out.add(new Bullet(right.x, right.y, dx * bulletSpeed, dy * bulletSpeed, Team.FRIENDLY, BulletType.NORMAL, 120, 4.0, 1));

            lastShotMs = now;
        }

        void tryLaunchMissileNoCooldown(Ship target, List<Missile> out) {
            if (missiles <= 0) return;

            double angle = (target != null) ? Math.atan2(target.y - y, target.x - x) : aimAngle;
            out.add(new Missile(x + Math.cos(angle) * 18, y + Math.sin(angle) * 18, angle, Team.FRIENDLY, target));
            missiles--;
        }

        // CIWS: prioritize missiles; if enemy very close, target it anyway (even really close).
        // nerf vs ships: does 0 damage to ships; still shoots missiles effectively.
        void tryCIWS(long now, List<EnemyShip> enemies, List<CapitalShip> caps, List<Missile> missiles,
                     List<Bullet> outBullets, List<Tracer> outTracers) {

            if (now - lastCIWSMs < ciwsCooldownMs) return;
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;

            Ship closeEnemy = null;
            double bestEnemyD2 = Double.POSITIVE_INFINITY;

            for (EnemyShip en : enemies) {
                if (!en.alive) continue;
                double d2 = dist2(x, y, en.x, en.y);
                if (d2 < ciwsEnemyOverrideRange * ciwsEnemyOverrideRange && d2 < bestEnemyD2) {
                    bestEnemyD2 = d2;
                    closeEnemy = en;
                }
            }
            for (CapitalShip c : caps) {
                if (!c.alive || c.team != Team.ENEMY) continue;
                double d2 = dist2(x, y, c.x, c.y);
                if (d2 < ciwsEnemyOverrideRange * ciwsEnemyOverrideRange && d2 < bestEnemyD2) {
                    bestEnemyD2 = d2;
                    closeEnemy = c;
                }
            }

            double tx, ty;
            int dmg;
            if (closeEnemy != null) {
                tx = closeEnemy.x; ty = closeEnemy.y;
                dmg = 0; // nerfed vs ships
            } else {
                Missile threat = null;
                double best = Double.POSITIVE_INFINITY;
                for (Missile m : missiles) {
                    if (m.team == Team.FRIENDLY) continue;
                    double d2 = dist2(x, y, m.x, m.y);
                    if (d2 < ciwsRange * ciwsRange && d2 < best) { best = d2; threat = m; }
                }
                if (threat == null) return;
                tx = threat.x; ty = threat.y;
                dmg = 1;
            }

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) return;
            dx /= len; dy /= len;

            outBullets.add(new Bullet(x, y, dx * ciwsBulletSpeed, dy * ciwsBulletSpeed,
                    Team.FRIENDLY, BulletType.CIWS, 28, 2.2, dmg));

            outTracers.add(new Tracer(x, y, x + dx * 24, y + dy * 24, 6));
            lastCIWSMs = now;
        }
    }

    static class EnemyShip extends Ship {
        EnemyShip(double x, double y) {
            super(x, y);
            speed = 1.55;
            hpMax = 6; hp = hpMax;
            shieldMax = 8; shield = shieldMax;
            shieldRegenPerSec = 0.38;
            shotCooldownMs = 900;
            bulletSpeed = 7.4;
            radius = 16;
            shieldRadius = 26;
            modules = new Modules(10);
        }

        @Override Team getTeam() { return Team.ENEMY; }
        Color getColor() { return new Color(220, 70, 70); }

        @Override
        void tryUseAbility(long now, Ship target) {
            if (now - lastAbilityMs < 4200) return;
            if (target == null) return;
            if (Math.random() < 0.35 && modules.isModuleOnline(ModuleType.THRUSTER)) {
                speed *= 1.25;
                lastAbilityMs = now;
            }
        }
    }

    static class Interceptor extends EnemyShip {
        Interceptor(double x, double y) {
            super(x, y);
            speed = 2.25;
            hpMax = 4; hp = hpMax;
            shieldMax = 5; shield = shieldMax;
            shotCooldownMs = 780;
        }
        @Override Color getColor() { return new Color(80, 220, 220); }

        @Override
        void tryUseAbility(long now, Ship target) {
            if (now - lastAbilityMs < 3200) return;
            if (modules.isModuleOnline(ModuleType.THRUSTER) && Math.random() < 0.55) {
                speed *= 1.35;
                lastAbilityMs = now;
            }
        }
    }

    static class ShieldTank extends EnemyShip {
        ShieldTank(double x, double y) {
            super(x, y);
            speed = 1.05;
            hpMax = 10; hp = hpMax;
            shieldMax = 16; shield = shieldMax;
            shieldRadius = 34;
            radius = 18;
            shotCooldownMs = 1150;
            shieldRegenPerSec = 0.55;
            modules = new Modules(12);
        }
        @Override Color getColor() { return new Color(230, 200, 80); }

        @Override
        void tryUseAbility(long now, Ship target) {
            if (now - lastAbilityMs < 6000) return;
            if (modules.isModuleOnline(ModuleType.SHIELD_GEN) && Math.random() < 0.45) {
                shield = Math.min(shieldMax, shield + 5);
                lastAbilityMs = now;
            }
        }
    }

    static class MissileBoat extends EnemyShip {
        long lastMissileMs = 0;
        long missileCooldownMs = 2400;

        MissileBoat(double x, double y) {
            super(x, y);
            hpMax = 7; hp = hpMax;
            shieldMax = 10; shield = shieldMax;
            shotCooldownMs = 1100;
        }
        @Override Color getColor() { return new Color(210, 90, 250); }

        void tryFireMissile(long now, double px, double py, List<Missile> out) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastMissileMs < missileCooldownMs) return;
            if (Math.random() < 0.55) {
                double angle = Math.atan2(py - y, px - x);
                out.add(new Missile(x + Math.cos(angle) * 18, y + Math.sin(angle) * 18, angle, Team.ENEMY, null));
            }
            lastMissileMs = now;
        }

        @Override
        void tryUseAbility(long now, Ship target) {
            if (now - lastAbilityMs < 6500) return;
            if (Math.random() < 0.40) {
                missileCooldownMs = Math.max(1200, missileCooldownMs - 400);
                lastAbilityMs = now;
            }
        }
    }

    // ONLY NPC CIWS ship type
    static class CIWSDestroyer extends EnemyShip {
        long lastCIWSMs = 0;
        long ciwsCooldownMs = 55;
        double ciwsRange = 220;
        double ciwsBulletSpeed = 14.0;

        CIWSDestroyer(double x, double y) {
            super(x, y);
            hpMax = 7; hp = hpMax;
            shieldMax = 10; shield = shieldMax;
            shotCooldownMs = 1250;
        }
        @Override Color getColor() { return new Color(200, 200, 200); }

        void tryCIWS(long now, List<Missile> missiles, List<Bullet> outBullets, List<Tracer> outTracers) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastCIWSMs < ciwsCooldownMs) return;

            Missile threat = null;
            double best = Double.POSITIVE_INFINITY;
            for (Missile m : missiles) {
                if (m.team == Team.ENEMY) continue;
                double d2 = dist2(x, y, m.x, m.y);
                if (d2 < ciwsRange * ciwsRange && d2 < best) { best = d2; threat = m; }
            }
            if (threat == null) return;

            double dx = threat.x - x, dy = threat.y - y;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) return;
            dx /= len; dy /= len;

            outBullets.add(new Bullet(x, y, dx * ciwsBulletSpeed, dy * ciwsBulletSpeed,
                    Team.ENEMY, BulletType.CIWS, 28, 2.2, 1));
            outTracers.add(new Tracer(x, y, x + dx * 24, y + dy * 24, 6));

            lastCIWSMs = now;
        }
    }

    static class FriendlyShip extends Ship {
        int missiles = 8;

        FriendlyShip(double x, double y) {
            super(x, y);
            speed = 1.65;
            hpMax = 6; hp = hpMax;
            shieldMax = 8; shield = shieldMax;
            shotCooldownMs = 850;
            bulletSpeed = 8.2;
            modules = new Modules(10);
        }
        @Override Team getTeam() { return Team.FRIENDLY; }

        void launchMissileAt(Ship target, List<Missile> out) {
            if (missiles <= 0 || target == null) return;
            double angle = Math.atan2(target.y - y, target.x - x);
            out.add(new Missile(x + Math.cos(angle) * 18, y + Math.sin(angle) * 18, angle, Team.FRIENDLY, target));
            missiles--;
        }

        @Override
        void tryUseAbility(long now, Ship target) {
            if (now - lastAbilityMs < 5200) return;
            if (modules.isModuleOnline(ModuleType.SHIELD_GEN) && Math.random() < 0.35) {
                shield = Math.min(shieldMax, shield + 3);
                lastAbilityMs = now;
            }
        }
    }

    static class FriendlyInterceptor extends FriendlyShip {
        FriendlyInterceptor(double x, double y) {
            super(x, y);
            speed = 2.1;
            hpMax = 4; hp = hpMax;
            shieldMax = 5; shield = shieldMax;
            shotCooldownMs = 760;
        }

        @Override
        void tryUseAbility(long now, Ship target) {
            if (now - lastAbilityMs < 3200) return;
            if (modules.isModuleOnline(ModuleType.THRUSTER) && Math.random() < 0.55) {
                speed *= 1.30;
                lastAbilityMs = now;
            }
        }
    }

    static class FriendlyShieldTank extends FriendlyShip {
        FriendlyShieldTank(double x, double y) {
            super(x, y);
            speed = 1.1;
            hpMax = 10; hp = hpMax;
            shieldMax = 16; shield = shieldMax;
            shieldRadius = 34;
            radius = 18;
            shotCooldownMs = 1100;
            shieldRegenPerSec = 0.55;
            modules = new Modules(12);
        }
    }

    static class Drone {
        double x, y, vx, vy;
        double radius = 10;
        double speed = 5.2;

        double aimAngle = 0;
        int lifeFrames = 60 * 11;

        double followDistance = 95;

        long lastShotMs = 0;
        long cooldownMs = 220;
        double bulletSpeed = 9.6;

        Drone(double x, double y) { this.x = x; this.y = y; }

        void tryShoot(long now, double tx, double ty, List<Bullet> out) {
            if (now - lastShotMs < cooldownMs) return;

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) return;
            dx /= len; dy /= len;

            out.add(new Bullet(x + dx * 14, y + dy * 14, dx * bulletSpeed, dy * bulletSpeed,
                    Team.FRIENDLY, BulletType.NORMAL, 95, 3.5, 1));
            lastShotMs = now;
        }
    }

    static class Missile {
        double x, y, vx, vy;
        double angle;
        double speed;
        double turnRateRadPerSec;
        int life;
        int hp;
        int damage;
        double hitRadius;
        double proxFuseRadius;
        Team team;

        Ship lockTarget;

        Missile(double x, double y, double angle, Team team, Ship lockTarget) {
            this.x = x; this.y = y;
            this.angle = angle;
            this.team = team;
            this.lockTarget = lockTarget;

            this.speed = (team == Team.FRIENDLY) ? 7.6 : 6.6;
            this.turnRateRadPerSec = (team == Team.FRIENDLY) ? Math.toRadians(250) : Math.toRadians(175);
            this.life = 260;
            this.hp = 2;
            this.damage = 3;
            this.hitRadius = 10;
            this.proxFuseRadius = 18;
        }
    }

    static class Base extends Ship {
        String name;
        Team team;

        double auraRadius = 190;
        double repairPerSec = 0.9;
        double shieldPerSec = 1.3;
        double missilesPerSec = 1.7;

        long cooldownMs = 190;

        Base(double x, double y, Team team, String name) {
            super(x, y);
            this.team = team;
            this.name = name;

            radius = 26;
            hpMax = 60; hp = hpMax;

            shieldMax = 50; shield = shieldMax;
            shieldRadius = 55;
            shieldRegenPerSec = 0.60;

            speed = 0;
            shotCooldownMs = cooldownMs;
            bulletSpeed = 12.8;

            modules = new Modules(18);
        }

        void reset() {
            alive = true;
            hp = hpMax;
            shield = shieldMax;
            modules.fullRepair();
        }

        boolean isInAura(double px, double py) {
            return alive && dist2(px, py, x, y) < auraRadius * auraRadius;
        }

        void tryShootPoint(long now, double tx, double ty, List<Bullet> outBullets, List<Tracer> outTracers) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastShotMs < cooldownMs) return;

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len < 0.001) return;
            dx /= len; dy /= len;

            outBullets.add(new Bullet(x + dx * 28, y + dy * 28, dx * bulletSpeed, dy * bulletSpeed,
                    team, BulletType.NORMAL, 110, 3.6, 1));
            outTracers.add(new Tracer(x, y, x + dx * 34, y + dy * 34, 6));

            lastShotMs = now;
        }

        @Override Team getTeam() { return team; }
        @Override void updateCommonAI(long now, Ship target, Player player, Base myBase, Base enemyBase, List<Obstacle> obstacles) {}
    }

    static class Turret {
        double localX, localY;
        double angle = 0;
        int hp = 6;

        Turret(double lx, double ly) {
            this.localX = lx;
            this.localY = ly;
        }

        boolean alive() { return hp > 0; }
    }

    // -------- Capital Ship (overhauled + turrets have HP) --------
    static class CapitalShip extends Ship {
        Team team;

        // turret system
        List<Turret> turrets = new ArrayList<>();
        long lastTurretMs = 0;
        long turretCooldownMs = 180;

        // multi-segment hull collision points (local-space)
        List<Point2D> hullPoints = new ArrayList<>();
        double hullSegmentRadius = 24;

        CapitalShip(double x, double y, Team team) {
            super(x, y);
            this.team = team;

            radius = 44;
            hpMax = 120; hp = hpMax;

            shieldMax = 80; shield = shieldMax;
            shieldRadius = 80;
            shieldRegenPerSec = 0.45;

            speed = 0.65;
            shotCooldownMs = 999999;
            bulletSpeed = 0;

            modules = new Modules(22);

            // turrets
            turrets.add(new Turret(-30, -18));
            turrets.add(new Turret(0, -20));
            turrets.add(new Turret(30, -18));
            turrets.add(new Turret(0, 18));

            // hull segments (like connected circles)
            hullPoints.add(new Point2D(-28, 0));
            hullPoints.add(new Point2D(0, 0));
            hullPoints.add(new Point2D(28, 0));
        }

        static CapitalShip spawnNear(double bx, double by, Team team) {
            double angle = Math.random() * Math.PI * 2;
            double dist = 250 + Math.random() * 180;
            return new CapitalShip(bx + Math.cos(angle) * dist, by + Math.sin(angle) * dist, team);
        }

        @Override Team getTeam() { return team; }

        void updateCapitalAI(long now, Base myBase, Base theirBase, List<Obstacle> obstacles) {
            if (!alive) return;

            double tx = (theirBase != null) ? theirBase.x : x;
            double ty = (theirBase != null) ? theirBase.y : y;

            double dx = tx - x, dy = ty - y;
            double len = Math.hypot(dx, dy);
            if (len > 0.001) { dx /= len; dy /= len; }

            double moveSpeed = modules.isModuleOnline(ModuleType.ENGINE) ? speed : speed * 0.2;

            if (hp / hpMax < 0.25 && myBase != null && myBase.alive) {
                dx = myBase.x - x;
                dy = myBase.y - y;
                len = Math.hypot(dx, dy);
                if (len > 0.001) { dx /= len; dy /= len; }
                moveSpeed *= 1.2;
            }

            vx = dx * moveSpeed;
            vy = dy * moveSpeed;

            x += vx;
            y += vy;

            x = clamp(x, radius, WORLD_W - radius);
            y = clamp(y, radius, WORLD_H - radius);

            if (Math.hypot(vx, vy) > 0.01) facing = Math.atan2(vy, vx);
        }

        void aimTurretsAt(double tx, double ty) {
            double ang = Math.atan2(ty - y, tx - x);
            for (Turret t : turrets) t.angle = ang;
        }

        void tryFireTurrets(long now, double tx, double ty, List<Bullet> out) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastTurretMs < turretCooldownMs) return;

            aimTurretsAt(tx, ty);

            for (Turret t : turrets) {
                if (!t.alive()) continue;

                Point2D wp = localToWorld(x, y, facing, t.localX, t.localY);

                double dx = tx - wp.x, dy = ty - wp.y;
                double len = Math.hypot(dx, dy);
                if (len < 0.001) continue;
                dx /= len; dy /= len;

                double spread = (Math.random() - 0.5) * 0.06;
                double a = Math.atan2(dy, dx) + spread;

                double sp = 11.0;

                out.add(new Bullet(
                        wp.x + Math.cos(a) * 10,
                        wp.y + Math.sin(a) * 10,
                        Math.cos(a) * sp,
                        Math.sin(a) * sp,
                        team,
                        BulletType.NORMAL,
                        150,
                        4.0,
                        1
                ));
            }

            lastTurretMs = now;
        }

        // Broadside: temporary faster turret cadence
        @Override
        void tryUseAbility(long now, Ship target) {
            if (!modules.isModuleOnline(ModuleType.WEAPON)) return;
            if (now - lastAbilityMs < 9000) return;
            if (target == null) return;

            if (Math.random() < 0.45) {
                turretCooldownMs = Math.max(90, turretCooldownMs - 40);
                lastAbilityMs = now;
            }
        }

        // multi-segment collision test
        boolean hitByCircle(double wx, double wy, double r) {
            for (Point2D lp : hullPoints) {
                Point2D wp = localToWorld(x, y, facing, lp.x, lp.y);
                if (dist2(wx, wy, wp.x, wp.y) < sq(hullSegmentRadius + r)) return true;
            }
            return false;
        }

        int hitTurretIndex(double wx, double wy, double r) {
            for (int i = 0; i < turrets.size(); i++) {
                Turret t = turrets.get(i);
                if (!t.alive()) continue;
                Point2D tp = localToWorld(x, y, facing, t.localX, t.localY);
                if (dist2(wx, wy, tp.x, tp.y) < sq(8 + r)) return i;
            }
            return -1;
        }
    }

    static class MessageLog {
        private final int max;
        private final ArrayDeque<String> q = new ArrayDeque<>();
        MessageLog(int max) { this.max = max; }
        void push(String s) { q.addLast(s); while (q.size() > max) q.removeFirst(); }
        List<String> lines() { return new ArrayList<>(q); }
        void clear() { q.clear(); }
    }

    // ---------------- Math helpers ----------------
    private static double sq(double v) { return v * v; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static double dist2(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    private static double normalizeAngle(double a) {
        while (a <= -Math.PI) a += Math.PI * 2;
        while (a > Math.PI) a -= Math.PI * 2;
        return a;
    }

    // ---------------- Main ----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Open World Space Shooter (Expanded)");
            Main panel = new Main();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.requestFocusInWindow();
        });
    }

    private void drawTriangleOverlay(
            Graphics2D g2,
            double wx,
            double wy,
            double angle,
            Color color,
            int forward,
            int halfWidth
    ) {
        AffineTransform old = g2.getTransform();

        g2.translate(sx(wx), sy(wy));
        g2.rotate(angle);

        Polygon tri = new Polygon();
        tri.addPoint(forward, 0);
        tri.addPoint(-forward, -halfWidth);
        tri.addPoint(-forward, halfWidth);

        g2.setColor(color);
        g2.fillPolygon(tri);

        g2.setTransform(old);
    }
    private double camXVal = 0;
    private double camYVal = 0;


}
