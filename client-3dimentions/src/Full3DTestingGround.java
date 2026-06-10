import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * LWJGL/OpenGL proving ground for the future full-3D combat client.
 * This is intentionally separate from the production renderer and the Swing pseudo-3D sandbox.
 */
public final class Full3DTestingGround {
    private static final Path DEFAULT_MODEL_DIR = Path.of("C:\\Users\\xhatf\\OneDrive\\Desktop\\3d models dropoff");
    private static final double ARENA_RADIUS = 820.0;
    private static final int SMALL_MODEL_TRIANGLE_BUDGET = 36_000;
    private static final int CAPITAL_MODEL_TRIANGLE_BUDGET = 78_000;
    private static final int MOTHERSHIP_MODEL_TRIANGLE_BUDGET = 180_000;

    private final Random rng = new Random(90420317L);
    private final List<Path> modelFiles = new ArrayList<>();
    private final List<Ship3D> ships = new ArrayList<>();
    private final List<Projectile3D> projectiles = new ArrayList<>();
    private final List<DemoProp> props = new ArrayList<>();
    private final Map<String, GlbModel> modelCache = new HashMap<>();

    private long window;
    private int width = 1440;
    private int height = 900;
    private boolean paused;
    private boolean followCamera = true;
    private boolean cinematicCamera;
    private boolean wireframeOverlay;
    private int selectedShipIndex;
    private int playerShipIndex;
    private String currentScenario = "mothership";
    private double enemyWaveTimer = 6.0;
    private int enemyWaveNumber;
    private double cameraYaw = 38.0;
    private double cameraPitch = 56.0;
    private double cameraDistance = 1350.0;
    private double cameraTargetX;
    private double cameraTargetZ;
    private double lastTitleUpdate;

    public static void main(String[] args) {
        new Full3DTestingGround().run(args == null ? new String[0] : args);
    }

    private void run(String[] args) {
        String scenario = parseScenario(args);
        Path modelDir = resolveModelDir(args);
        discoverModels(modelDir);
        initWindow();
        spawnScenario(scenario);
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Could not initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        window = glfwCreateWindow(width, height, "Eagles Remorse - Full 3D Testing Ground", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Could not create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = Math.max(1, w);
            height = Math.max(1, h);
        });
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) glfwSetWindowShouldClose(window, true);
            if (key == GLFW_KEY_P) paused = !paused;
            if (key == GLFW_KEY_R) spawnScenario(currentScenario);
            if (key == GLFW_KEY_1) spawnScenario("mothership");
            if (key == GLFW_KEY_2) spawnScenario("skirmish");
            if (key == GLFW_KEY_3) spawnScenario("capital");
            if (key == GLFW_KEY_4) spawnScenario("swarm");
            if (key == GLFW_KEY_5) spawnScenario("fourteam");
            if (key == GLFW_KEY_6) spawnScenario("gallery");
            if (key == GLFW_KEY_TAB) selectNextPlayerShip();
            if (key == GLFW_KEY_C) cinematicCamera = !cinematicCamera;
            if (key == GLFW_KEY_F) followCamera = !followCamera;
            if (key == GLFW_KEY_X) wireframeOverlay = !wireframeOverlay;
        });

        glClearColor(0.018f, 0.024f, 0.040f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DITHER);
        glEnable(GL_LINE_SMOOTH);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
        glDisable(GL_CULL_FACE);
        glPointSize(4.0f);
        glLineWidth(1.15f);
    }

    private void loop() {
        double previous = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            double dt = Math.min(0.05, Math.max(0.0, now - previous));
            previous = now;

            handleCameraControls(dt);
            if (!paused) updateBattle(dt);
            updateCameraFollow(dt, now);
            render(now);
            updateTitle(now);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void handleCameraControls(double dt) {
        double move = 520.0 * dt;
        if (!followCamera) {
            if (glfwGetKey(window, GLFW_KEY_I) == GLFW_PRESS) cameraTargetZ -= move;
            if (glfwGetKey(window, GLFW_KEY_K) == GLFW_PRESS) cameraTargetZ += move;
            if (glfwGetKey(window, GLFW_KEY_J) == GLFW_PRESS) cameraTargetX -= move;
            if (glfwGetKey(window, GLFW_KEY_L) == GLFW_PRESS) cameraTargetX += move;
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) cameraYaw -= 80.0 * dt;
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) cameraYaw += 80.0 * dt;
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) cameraPitch = clamp(cameraPitch - 55.0 * dt, 18.0, 78.0);
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) cameraPitch = clamp(cameraPitch + 55.0 * dt, 18.0, 78.0);
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) cameraDistance = clamp(cameraDistance + 720.0 * dt, 420.0, 2600.0);
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) cameraDistance = clamp(cameraDistance - 720.0 * dt, 420.0, 2600.0);
    }

    private void updateCameraFollow(double dt, double now) {
        if (cinematicCamera) {
            cameraYaw += 8.0 * dt;
            cameraPitch = 54.0 + Math.sin(now * 0.22) * 5.0;
            cameraDistance = 1420.0 + Math.sin(now * 0.18) * 180.0;
        }
        if (!followCamera) return;
        Ship3D selected = playerShip();
        if (selected == null) return;
        double t = clamp(dt * 3.4, 0.0, 1.0);
        cameraTargetX = lerp(cameraTargetX, selected.x, t);
        cameraTargetZ = lerp(cameraTargetZ, selected.z, t);
        if (currentScenario.equals("mothership") && !cinematicCamera) {
            double desiredYaw = 90.0 - Math.toDegrees(selected.heading);
            cameraYaw = lerpAngleDegrees(cameraYaw, desiredYaw, clamp(dt * 2.3, 0.0, 1.0));
            cameraPitch = lerp(cameraPitch, 62.0, clamp(dt * 2.0, 0.0, 1.0));
            cameraDistance = lerp(cameraDistance, 980.0, clamp(dt * 1.7, 0.0, 1.0));
        }
    }

    private void updateBattle(double dt) {
        for (Ship3D ship : ships) {
            if (!ship.alive()) continue;
            if (ship.playerControlled) {
                updatePlayerShip(ship, dt);
                continue;
            }
            if (currentScenario.equals("mothership") && ship.team == 0) {
                updateFriendlyEscort(ship, dt);
                continue;
            }
            Ship3D target = nearestHostile(ship);
            if (target == null) {
                orbitCenter(ship, dt);
                continue;
            }

            double dx = target.x - ship.x;
            double dz = target.z - ship.z;
            double dist = Math.max(1.0, Math.hypot(dx, dz));
            double desiredHeading = Math.atan2(dz, dx);
            ship.heading = turnToward(ship.heading, desiredHeading, ship.turnRate * dt);
            double preferred = ship.preferredRange;
            double throttle = dist > preferred ? 1.0 : (dist < preferred * 0.62 ? -0.36 : 0.0);
            ship.x += Math.cos(ship.heading) * ship.speed * throttle * dt;
            ship.z += Math.sin(ship.heading) * ship.speed * throttle * dt;
            ship.x = clamp(ship.x, -ARENA_RADIUS, ARENA_RADIUS);
            ship.z = clamp(ship.z, -ARENA_RADIUS, ARENA_RADIUS);

            ship.cooldown -= dt;
            if (dist < ship.weaponRange && ship.cooldown <= 0.0) {
                fire(ship, target);
                ship.cooldown = ship.fireDelay * (0.72 + rng.nextDouble() * 0.42);
            }
        }
        updateEnemySpawner(dt);

        for (Projectile3D p : projectiles) {
            if (!p.alive) continue;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.ttl -= dt;
            if (p.ttl <= 0.0) p.alive = false;
            for (Ship3D ship : ships) {
                if (!p.alive || !ship.alive() || ship.team == p.team) continue;
                double hit = ship.collisionRadius;
                if (Math.hypot(ship.x - p.x, ship.z - p.z) < hit && Math.abs(ship.y - p.y) < hit * 0.75) {
                    ship.hp -= p.damage;
                    p.alive = false;
                }
            }
        }
        projectiles.removeIf(p -> !p.alive);
    }

    private void updatePlayerShip(Ship3D ship, double dt) {
        double turnInput = 0.0;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) turnInput -= 1.0;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) turnInput += 1.0;
        ship.heading = normalizeAngle(ship.heading + turnInput * ship.turnRate * 1.35 * dt);

        double throttle = 0.0;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) throttle += 1.0;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) throttle -= 0.55;
        double boost = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS ? 1.55 : 1.0;
        ship.x += Math.cos(ship.heading) * ship.speed * throttle * boost * dt;
        ship.z += Math.sin(ship.heading) * ship.speed * throttle * boost * dt;
        ship.x = clamp(ship.x, -ARENA_RADIUS, ARENA_RADIUS);
        ship.z = clamp(ship.z, -ARENA_RADIUS, ARENA_RADIUS);

        ship.cooldown -= dt;
        boolean firing = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS
                || glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        if (firing && ship.cooldown <= 0.0) {
            Ship3D target = bestPlayerTarget(ship);
            if (target != null) fire(ship, target);
            else fireForward(ship);
            ship.cooldown = ship.fireDelay * 0.62;
        }
    }

    private void updateFriendlyEscort(Ship3D ship, double dt) {
        Ship3D player = playerShip();
        Ship3D target = nearestHostile(ship);
        if (target != null) {
            double dx = target.x - ship.x;
            double dz = target.z - ship.z;
            double dist = Math.max(1.0, Math.hypot(dx, dz));
            if (dist < ship.weaponRange * 1.35) {
                double desiredHeading = Math.atan2(dz, dx);
                ship.heading = turnToward(ship.heading, desiredHeading, ship.turnRate * dt);
                ship.cooldown -= dt;
                if (dist < ship.weaponRange && ship.cooldown <= 0.0) {
                    fire(ship, target);
                    ship.cooldown = ship.fireDelay * (0.78 + rng.nextDouble() * 0.38);
                }
                if (dist > ship.preferredRange * 0.72) {
                    ship.x += Math.cos(ship.heading) * ship.speed * 0.34 * dt;
                    ship.z += Math.sin(ship.heading) * ship.speed * 0.34 * dt;
                }
                return;
            }
        }

        if (player == null || player == ship) return;
        double slotX = Math.cos(player.heading) * ship.formationForward
                - Math.sin(player.heading) * ship.formationSide;
        double slotZ = Math.sin(player.heading) * ship.formationForward
                + Math.cos(player.heading) * ship.formationSide;
        double goalX = player.x + slotX;
        double goalZ = player.z + slotZ;
        double dx = goalX - ship.x;
        double dz = goalZ - ship.z;
        double dist = Math.max(1.0, Math.hypot(dx, dz));
        double desiredHeading = dist > 18.0 ? Math.atan2(dz, dx) : player.heading;
        ship.heading = turnToward(ship.heading, desiredHeading, ship.turnRate * 1.15 * dt);
        double speedMul = clamp(dist / 260.0, 0.10, 1.18);
        ship.x += Math.cos(ship.heading) * ship.speed * speedMul * dt;
        ship.z += Math.sin(ship.heading) * ship.speed * speedMul * dt;
        ship.x = clamp(ship.x, -ARENA_RADIUS, ARENA_RADIUS);
        ship.z = clamp(ship.z, -ARENA_RADIUS, ARENA_RADIUS);
    }

    private void updateEnemySpawner(double dt) {
        if (!currentScenario.equals("mothership")) return;
        long aliveEnemies = ships.stream().filter(s -> s.alive() && s.team != 0).count();
        if (aliveEnemies > 16) return;
        enemyWaveTimer -= dt;
        if (enemyWaveTimer > 0.0) return;
        spawnEnemyWave();
        enemyWaveNumber++;
        enemyWaveTimer = clamp(12.0 - enemyWaveNumber * 0.65, 5.5, 12.0) + rng.nextDouble() * 4.0;
    }

    private void orbitCenter(Ship3D ship, double dt) {
        double desiredHeading = Math.atan2(-ship.z, -ship.x) + 0.42;
        ship.heading = turnToward(ship.heading, desiredHeading, ship.turnRate * dt);
        ship.x += Math.cos(ship.heading) * ship.speed * 0.35 * dt;
        ship.z += Math.sin(ship.heading) * ship.speed * 0.35 * dt;
    }

    private void fire(Ship3D ship, Ship3D target) {
        double dx = target.x - ship.x;
        double dz = target.z - ship.z;
        double len = Math.max(1.0, Math.hypot(dx, dz));
        double speed = ship.projectileSpeed;
        projectiles.add(new Projectile3D(
                ship.team,
                ship.x + dx / len * ship.collisionRadius,
                ship.y + 12.0,
                ship.z + dz / len * ship.collisionRadius,
                dx / len * speed,
                0.0,
                dz / len * speed,
                ship.damage,
                2.8));
    }

    private void fireForward(Ship3D ship) {
        double dx = Math.cos(ship.heading);
        double dz = Math.sin(ship.heading);
        double speed = ship.projectileSpeed;
        projectiles.add(new Projectile3D(
                ship.team,
                ship.x + dx * ship.collisionRadius,
                ship.y + 12.0,
                ship.z + dz * ship.collisionRadius,
                dx * speed,
                0.0,
                dz * speed,
                ship.damage,
                2.4));
    }

    private Ship3D bestPlayerTarget(Ship3D source) {
        Ship3D best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Ship3D other : ships) {
            if (other == source || !other.alive() || other.team == source.team) continue;
            double dx = other.x - source.x;
            double dz = other.z - source.z;
            double dist = Math.max(1.0, Math.hypot(dx, dz));
            if (dist > source.weaponRange * 1.18) continue;
            double angle = Math.abs(normalizeAngle(Math.atan2(dz, dx) - source.heading));
            if (angle > Math.toRadians(42.0)) continue;
            double score = dist + angle * 420.0;
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }

    private Ship3D nearestHostile(Ship3D source) {
        Ship3D best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Ship3D other : ships) {
            if (other == source || !other.alive() || other.team == source.team) continue;
            double d = Math.hypot(other.x - source.x, other.z - source.z);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private void render(double now) {
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        setupCamera();

        renderBackdropStars();
        renderGrid();
        renderDemoProps();
        renderArenaRing();
        renderProjectiles();
        ships.stream()
                .filter(Ship3D::alive)
                .sorted(Comparator.comparingDouble(s -> s.z))
                .forEach(this::renderShip);
        renderPlayerCues();
    }

    private void setupCamera() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        perspective(54.0, width / (double) Math.max(1, height), 1.0, 6000.0);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslated(0.0, 0.0, -cameraDistance);
        glRotated(cameraPitch, 1.0, 0.0, 0.0);
        glRotated(cameraYaw, 0.0, 1.0, 0.0);
        glTranslated(-cameraTargetX, -80.0, -cameraTargetZ);
    }

    private void renderGrid() {
        glBegin(GL_LINES);
        for (int i = -12; i <= 12; i++) {
            float alpha = i == 0 ? 0.38f : 0.10f;
            glColor4f(0.34f, 0.52f, 0.68f, alpha);
            glVertex3d(i * 100.0, 0.0, -1200.0);
            glVertex3d(i * 100.0, 0.0, 1200.0);
            glVertex3d(-1200.0, 0.0, i * 100.0);
            glVertex3d(1200.0, 0.0, i * 100.0);
        }
        glEnd();
    }

    private void renderBackdropStars() {
        glDisable(GL_DEPTH_TEST);
        glBegin(GL_POINTS);
        for (int i = 0; i < 220; i++) {
            double x = ((i * 197L) % 2400L) - 1200.0;
            double z = ((i * 353L) % 2400L) - 1200.0;
            double y = 220.0 + ((i * 89L) % 520L);
            float a = (i % 13 == 0) ? 0.72f : 0.34f;
            glColor4f(0.74f, 0.84f, 1.0f, a);
            glVertex3d(x, y, z);
        }
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    private void renderDemoProps() {
        for (DemoProp prop : props) {
            glPushMatrix();
            glTranslated(prop.x, prop.y, prop.z);
            glRotated(prop.rotation, 0.3, 1.0, 0.2);
            glScaled(prop.scale, prop.scale, prop.scale);
            Tone tone = prop.tone;
            if (prop.kind == PropKind.BEACON) {
                renderBeacon(tone);
            } else {
                renderAsteroid(tone);
            }
            glPopMatrix();
        }
    }

    private void renderAsteroid(Tone tone) {
        glBegin(GL_TRIANGLES);
        glColor4f(tone.r * 0.45f, tone.g * 0.45f, tone.b * 0.45f, 0.95f);
        glVertex3d(0.0, 0.9, 0.0);
        glVertex3d(-1.0, -0.4, -0.7);
        glVertex3d(0.8, -0.2, -0.8);
        glVertex3d(0.0, 0.9, 0.0);
        glVertex3d(0.8, -0.2, -0.8);
        glVertex3d(0.9, -0.5, 0.7);
        glColor4f(tone.r * 0.62f, tone.g * 0.62f, tone.b * 0.62f, 0.95f);
        glVertex3d(0.0, 0.9, 0.0);
        glVertex3d(0.9, -0.5, 0.7);
        glVertex3d(-0.8, -0.3, 0.8);
        glVertex3d(0.0, 0.9, 0.0);
        glVertex3d(-0.8, -0.3, 0.8);
        glVertex3d(-1.0, -0.4, -0.7);
        glEnd();
    }

    private void renderBeacon(Tone tone) {
        glBegin(GL_LINES);
        glColor4f(tone.r, tone.g, tone.b, 0.85f);
        glVertex3d(0.0, -1.0, 0.0);
        glVertex3d(0.0, 1.4, 0.0);
        glVertex3d(-0.8, 0.0, 0.0);
        glVertex3d(0.8, 0.0, 0.0);
        glVertex3d(0.0, 0.0, -0.8);
        glVertex3d(0.0, 0.0, 0.8);
        glEnd();
    }

    private void renderArenaRing() {
        glColor4f(0.80f, 0.70f, 0.38f, 0.44f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 96; i++) {
            double a = i / 96.0 * Math.PI * 2.0;
            glVertex3d(Math.cos(a) * ARENA_RADIUS, 1.0, Math.sin(a) * ARENA_RADIUS);
        }
        glEnd();
    }

    private void renderProjectiles() {
        glBegin(GL_LINES);
        for (Projectile3D p : projectiles) {
            Tone tone = Tone.forTeam(p.team);
            glColor4f(tone.r, tone.g, tone.b, 0.95f);
            glVertex3d(p.x, p.y, p.z);
            glVertex3d(p.x - p.vx * 0.035, p.y - p.vy * 0.035, p.z - p.vz * 0.035);
        }
        glEnd();
    }

    private void renderShip(Ship3D ship) {
        glPushMatrix();
        glTranslated(ship.x, ship.y, ship.z);
        glRotated(-Math.toDegrees(ship.heading), 0.0, 1.0, 0.0);
        glScaled(ship.scale, ship.scale, ship.scale);

        if (ship.model != null && ship.model.isRenderable()) {
            renderGlbModel(ship);
        } else {
            renderFallbackHull(ship);
        }

        glPopMatrix();
        if (ship.playerControlled) {
            renderSelectionRing(ship, new Tone(0.52f, 0.88f, 1.0f), 1.25);
        }
        renderHealthStem(ship);
    }

    private void renderSelectionRing(Ship3D ship, Tone tone, double radiusMul) {
        double radius = ship.collisionRadius * radiusMul;
        glBegin(GL_LINE_LOOP);
        glColor4f(tone.r, tone.g, tone.b, 0.82f);
        for (int i = 0; i < 56; i++) {
            double a = i / 56.0 * Math.PI * 2.0;
            glVertex3d(ship.x + Math.cos(a) * radius, 4.0, ship.z + Math.sin(a) * radius);
        }
        glEnd();
    }

    private void renderPlayerCues() {
        Ship3D player = playerShip();
        if (player == null) return;
        Ship3D target = bestPlayerTarget(player);
        double noseX = player.x + Math.cos(player.heading) * player.collisionRadius * 1.55;
        double noseZ = player.z + Math.sin(player.heading) * player.collisionRadius * 1.55;
        double aimX = player.x + Math.cos(player.heading) * player.weaponRange * 0.72;
        double aimZ = player.z + Math.sin(player.heading) * player.weaponRange * 0.72;

        glBegin(GL_LINES);
        glColor4f(0.55f, 0.90f, 1.0f, 0.54f);
        glVertex3d(noseX, player.y + 10.0, noseZ);
        glVertex3d(aimX, player.y + 10.0, aimZ);
        glEnd();

        if (target != null) {
            renderSelectionRing(target, new Tone(1.0f, 0.42f, 0.32f), 1.42);
            glBegin(GL_LINES);
            glColor4f(1.0f, 0.42f, 0.32f, 0.54f);
            glVertex3d(player.x, player.y + 24.0, player.z);
            glVertex3d(target.x, target.y + 24.0, target.z);
            glEnd();
        }
    }

    private void renderGlbModel(Ship3D ship) {
        Tone tone = Tone.forTeam(ship.team);
        glBegin(GL_TRIANGLES);
        for (GlbModel.Triangle tri : ship.model.triangles) {
            double shade = clamp(0.66 + tri.avgZ * 0.26, 0.42, 1.0);
            glColor4f((float) (tone.r * shade), (float) (tone.g * shade), (float) (tone.b * shade), 1.0f);
            vertex(tri.a);
            vertex(tri.b);
            vertex(tri.c);
        }
        glEnd();

        if (!wireframeOverlay) return;
        glColor4f(0.88f, 0.95f, 1.0f, 0.22f);
        glBegin(GL_LINES);
        int stride = Math.max(1, ship.model.triangles.size() / 120);
        for (int i = 0; i < ship.model.triangles.size(); i += stride) {
            GlbModel.Triangle tri = ship.model.triangles.get(i);
            vertex(tri.a);
            vertex(tri.b);
            vertex(tri.b);
            vertex(tri.c);
            vertex(tri.c);
            vertex(tri.a);
        }
        glEnd();
    }

    private void renderFallbackHull(Ship3D ship) {
        Tone tone = Tone.forTeam(ship.team);
        glBegin(GL_TRIANGLES);
        glColor4f(tone.r, tone.g, tone.b, 0.95f);
        glVertex3d(1.4, 0.0, 0.0);
        glVertex3d(-0.9, 0.22, -0.62);
        glVertex3d(-0.9, 0.22, 0.62);
        glColor4f(tone.r * 0.62f, tone.g * 0.62f, tone.b * 0.62f, 0.95f);
        glVertex3d(1.4, 0.0, 0.0);
        glVertex3d(-0.9, -0.20, 0.62);
        glVertex3d(-0.9, -0.20, -0.62);
        glEnd();
    }

    private void renderHealthStem(Ship3D ship) {
        double hp = clamp(ship.hp / ship.maxHp, 0.0, 1.0);
        Tone tone = Tone.forTeam(ship.team);
        glBegin(GL_LINES);
        glColor4f(0.04f, 0.08f, 0.10f, 0.9f);
        glVertex3d(ship.x, ship.y + ship.scale * 1.4, ship.z);
        glVertex3d(ship.x, ship.y + ship.scale * 1.4 + 42.0, ship.z);
        glColor4f((float) (tone.r * hp), (float) (tone.g * hp), (float) (tone.b * hp), 1.0f);
        glVertex3d(ship.x + 5.0, ship.y + ship.scale * 1.4, ship.z);
        glVertex3d(ship.x + 5.0, ship.y + ship.scale * 1.4 + 42.0 * hp, ship.z);
        glEnd();
    }

    private static void vertex(double[] v) {
        glVertex3d(v[0], v[2] * 0.48, v[1] * 0.58);
    }

    private void updateTitle(double now) {
        if (now - lastTitleUpdate < 0.35) return;
        lastTitleUpdate = now;
        long aliveBlue = ships.stream().filter(s -> s.alive() && s.team == 0).count();
        long aliveRed = ships.stream().filter(s -> s.alive() && s.team == 1).count();
        long aliveHostile = ships.stream().filter(s -> s.alive() && s.team != 0).count();
        Ship3D selected = playerShip();
        glfwSetWindowTitle(window,
                "Eagles Remorse - Demo Level Tool"
                        + " | level " + currentScenario
                        + " | blue " + aliveBlue
                        + " red " + aliveRed
                        + " hostile " + aliveHostile
                        + " wave " + enemyWaveNumber
                        + " | projectiles " + projectiles.size()
                        + " | models " + modelFiles.size()
                        + (selected == null ? "" : " | player " + selected.name)
                        + " | WASD fly, SHIFT boost, SPACE/LMB fire, P pause");
    }

    private void spawnScenario(String scenario) {
        currentScenario = normalizeScenario(scenario);
        ships.clear();
        projectiles.clear();
        props.clear();
        selectedShipIndex = 0;
        playerShipIndex = 0;
        paused = currentScenario.equals("gallery");

        GlbModel blueMothership = loadBest("blue", "mothership");
        GlbModel blueFighter = loadBest("blue", "fighter");
        GlbModel blueBomber = loadBest("blue", "bomber");
        GlbModel blueDrone = loadBest("blue", "drone");
        GlbModel blueFrigate = loadBestAny(new String[]{"blue", "frigate"}, new String[]{"frigate"});
        GlbModel blueCiwsFrigate = loadBest("blue", "ciws", "frigate");
        GlbModel blueCiwsCorvette = loadBest("blue", "ciws", "corvette");
        GlbModel blueCruiser = loadBest("blue", "cruiser");
        GlbModel redFrigate = loadBest("red", "frigate");
        GlbModel redCruiser = loadBest("red", "medium cruiser");
        GlbModel redMissile = loadBest("red", "missile");
        GlbModel redPicket = loadBest("red", "picket");
        GlbModel greenFrigate = loadBest("green", "frigate");
        GlbModel yellowFrigate = loadBest("yellow", "frigate");
        GlbModel blueBattlecruiser = loadBest("blue", "battlecruiser");
        GlbModel blueBattleship = loadBest("blue", "battleship");
        GlbModel blueDreadnought = loadBest("blue", "dreadnaught");
        GlbModel blueSupership = loadBest("blue", "supership");
        GlbModel blueCarrier = loadBest("blue", "carrier");
        GlbModel blueDroneCarrier = loadBest("blue", "drone", "carrier");
        GlbModel blueTransport = loadBest("blue", "transport");
        GlbModel blueHauler = loadBest("blue", "hauler");
        GlbModel blueMiner = loadBest("blue", "miner");
        GlbModel blueMissileBoat = loadBest("blue", "missile", "boat");
        GlbModel bluePatrol = loadBest("blue", "patrol");
        GlbModel bluePicket = loadBest("blue", "picket");
        GlbModel blueStealth = loadBest("blue", "stealth");
        GlbModel blueBase = loadBest("blue", "base");
        GlbModel blueTransportTitan = loadBest("blue", "transport", "titan");
        GlbModel blueCarrierTitan = loadBest("blue", "carrier", "titan");
        GlbModel blueCommandTitan = loadBest("blue", "command", "intel", "titan");
        GlbModel blueBulwarkTitan = loadBestAny(new String[]{"blue", "bulwark"}, new String[]{"bulwark"});
        GlbModel redHauler = loadBest("red", "hauler");
        GlbModel greenCruiser = loadBest("green", "cruiser");
        GlbModel yellowHauler = loadBest("yellow", "hauler");

        buildDemoProps();
        switch (currentScenario) {
            case "mothership" -> spawnMothershipSandbox(
                    blueMothership,
                    blueFighter, blueBomber, blueDrone,
                    bluePicket, bluePatrol, blueStealth,
                    blueFrigate, blueCiwsFrigate, blueCiwsCorvette,
                    blueMissileBoat, blueCruiser, blueBattlecruiser,
                    blueBattleship, blueDreadnought, blueSupership,
                    blueCarrier, blueDroneCarrier, blueTransport,
                    blueHauler, blueMiner, blueBase,
                    blueTransportTitan, blueCarrierTitan, blueCommandTitan, blueBulwarkTitan);
            case "capital" -> {
                addShip("Blue Battleship", 0, blueBattleship, -520, -120, 68, 980, 76, 520);
                addShip("Blue Battlecruiser", 0, blueBattlecruiser, -620, 160, 58, 760, 92, 470);
                addShip("Blue Carrier", 0, blueCarrier, -760, -260, 54, 620, 84, 510);
                addShip("Red Cruiser", 1, redCruiser, 500, -180, 50, 620, 94, 440);
                addShip("Red Missile Line", 1, redMissile, 650, 90, 34, 340, 120, 590);
                addShip("Red Hauler Decoy", 1, redHauler, 770, 250, 30, 260, 105, 300);
            }
            case "swarm" -> {
                addShip("Blue Carrier", 0, blueCarrier, -430, 0, 58, 700, 82, 520);
                for (int i = 0; i < 9; i++) {
                    addShip("Blue Fighter " + (i + 1), 0, blueFighter, -640 - (i % 3) * 58, -220 + (i / 3) * 160,
                            15, 95, 285, 220);
                    addShip("Red Picket " + (i + 1), 1, redPicket, 580 + (i % 3) * 60, -240 + (i / 3) * 160,
                            17, 110, 250, 240);
                }
                addShip("Red Missile Anchor", 1, redMissile, 760, 0, 32, 320, 120, 560);
            }
            case "fourteam" -> {
                addShip("Blue Cruiser", 0, blueCruiser, -570, -130, 44, 420, 120, 380);
                addShip("Red Cruiser", 1, redCruiser, 570, 130, 44, 420, 120, 380);
                addShip("Green Cruiser", 2, greenCruiser, -150, 650, 42, 390, 118, 380);
                addShip("Yellow Hauler Guard", 3, yellowHauler, 150, -650, 32, 280, 120, 300);
                addShip("Blue Frigate", 0, blueFrigate, -690, 100, 30, 260, 155, 320);
                addShip("Red Frigate", 1, redFrigate, 690, -100, 30, 260, 155, 320);
                addShip("Green Frigate", 2, greenFrigate, -340, 720, 30, 260, 150, 330);
                addShip("Yellow Frigate", 3, yellowFrigate, 340, -720, 30, 260, 150, 330);
            }
            case "gallery" -> {
                addShip("Blue Fighter", 0, blueFighter, -620, -260, 18, 120, 0, 280);
                addShip("Blue Frigate", 0, blueFrigate, -360, -220, 30, 260, 0, 320);
                addShip("Blue Cruiser", 0, blueCruiser, -80, -160, 44, 420, 0, 380);
                addShip("Blue Carrier", 0, blueCarrier, 240, -100, 56, 620, 0, 460);
                addShip("Blue Battleship", 0, blueBattleship, 580, -40, 68, 980, 0, 520);
                addShip("Red Frigate", 1, redFrigate, -360, 250, 30, 260, 0, 320);
                addShip("Green Frigate", 2, greenFrigate, -80, 270, 30, 260, 0, 320);
                addShip("Yellow Frigate", 3, yellowFrigate, 200, 290, 30, 260, 0, 320);
            }
            default -> {
                addShip("Blue Carrier", 0, blueCarrier, -520, -120, 56, 620, 95, 460);
                addShip("Blue Cruiser", 0, blueCruiser, -430, 120, 44, 420, 120, 380);
                addShip("Blue Frigate A", 0, blueFrigate, -600, 170, 30, 260, 155, 320);
                addShip("Blue Frigate B", 0, blueFrigate, -610, -250, 30, 260, 155, 320);
                addShip("Blue Fighter 1", 0, blueFighter, -720, -60, 16, 115, 260, 230);
                addShip("Blue Fighter 2", 0, blueFighter, -760, 65, 16, 115, 260, 230);
                addShip("Red Cruiser", 1, redCruiser, 480, -110, 44, 420, 120, 380);
                addShip("Red Missile", 1, redMissile, 560, 155, 28, 230, 145, 520);
                addShip("Red Frigate A", 1, redFrigate, 650, -240, 30, 260, 155, 320);
                addShip("Red Frigate B", 1, redFrigate, 610, 280, 30, 260, 155, 320);
                addShip("Red Picket 1", 1, redPicket, 760, -40, 18, 130, 235, 250);
                addShip("Red Picket 2", 1, redPicket, 735, 80, 18, 130, 235, 250);
            }
        }

        for (Ship3D ship : ships) {
            ship.heading = ship.team == 0 ? 0.0 : Math.PI;
            ship.cooldown = rng.nextDouble() * ship.fireDelay;
        }
        assignPlayerShip();
    }

    private void spawnMothershipSandbox(GlbModel mothership,
                                        GlbModel fighter,
                                        GlbModel bomber,
                                        GlbModel drone,
                                        GlbModel picket,
                                        GlbModel patrol,
                                        GlbModel stealth,
                                        GlbModel frigate,
                                        GlbModel ciwsFrigate,
                                        GlbModel ciwsCorvette,
                                        GlbModel missileBoat,
                                        GlbModel cruiser,
                                        GlbModel battlecruiser,
                                        GlbModel battleship,
                                        GlbModel dreadnought,
                                        GlbModel supership,
                                        GlbModel carrier,
                                        GlbModel droneCarrier,
                                        GlbModel transport,
                                        GlbModel hauler,
                                        GlbModel miner,
                                        GlbModel base,
                                        GlbModel transportTitan,
                                        GlbModel carrierTitan,
                                        GlbModel commandTitan,
                                        GlbModel bulwarkTitan) {
        Ship3D player = addShip("Blue Mothership", 0, mothership, 0, 0, 118, 3200, 54, 640);
        player.heading = 0.0;
        player.fireDelay = 0.18;
        player.damage = 18.0;

        addEscort("Blue Battlecruiser", battlecruiser, -170, -155, 58, 760, 92, 470);
        addEscort("Blue Battleship", battleship, -230, 185, 70, 980, 76, 520);
        addEscort("Blue Dreadnought", dreadnought, -360, 0, 72, 1100, 70, 560);
        addEscort("Blue Supership", supership, -500, 260, 64, 940, 76, 520);
        addEscort("Blue Carrier", carrier, -460, -280, 58, 680, 82, 520);
        addEscort("Blue Drone Carrier", droneCarrier, -650, -80, 54, 620, 88, 480);
        addEscort("Blue Transport Titan", transportTitan, -740, 250, 82, 1400, 58, 520);
        addEscort("Blue Carrier Titan", carrierTitan, -780, -310, 84, 1450, 58, 520);
        addEscort("Blue Command Titan", commandTitan, -980, 0, 92, 1700, 52, 620);
        addEscort("Blue Bulwark Titan", bulwarkTitan, -1040, 340, 88, 1850, 46, 540);

        addEscort("Blue Cruiser", cruiser, -120, 330, 44, 420, 120, 380);
        addEscort("Blue Missile Boat", missileBoat, -80, -350, 30, 230, 145, 540);
        addEscort("Blue Frigate", frigate, -260, -410, 30, 260, 155, 320);
        addEscort("Blue CIWS Frigate", ciwsFrigate, -300, 420, 32, 280, 150, 320);
        addEscort("Blue CIWS Corvette", ciwsCorvette, -420, -470, 24, 180, 170, 260);
        addEscort("Blue Picket", picket, 120, -430, 18, 130, 235, 250);
        addEscort("Blue Patrol", patrol, 150, 410, 18, 130, 235, 250);
        addEscort("Blue Stealth Ship", stealth, 280, -360, 22, 150, 230, 310);
        addEscort("Blue Transport", transport, 320, 340, 28, 210, 135, 280);
        addEscort("Blue Hauler", hauler, 460, 190, 24, 180, 130, 260);
        addEscort("Blue Miner", miner, 460, -190, 22, 160, 130, 240);
        addEscort("Blue Base Tug", base, 620, 0, 44, 620, 70, 340);

        for (int i = 0; i < 5; i++) {
            addEscort("Blue Fighter " + (i + 1), fighter, 230 + i * 42, -210 + i * 90,
                    15, 95, 285, 220);
        }
        for (int i = 0; i < 3; i++) {
            addEscort("Blue Bomber " + (i + 1), bomber, 410 + i * 50, -320 + i * 310,
                    18, 130, 240, 360);
        }
        for (int i = 0; i < 4; i++) {
            addEscort("Blue Drone " + (i + 1), drone, 600 + i * 40, -250 + i * 170,
                    12, 70, 310, 190);
        }

        enemyWaveTimer = 3.5;
        enemyWaveNumber = 0;
        cameraYaw = 90.0;
        cameraPitch = 62.0;
        cameraDistance = 980.0;
        followCamera = true;
        cinematicCamera = false;
    }

    private void addEscort(String name, GlbModel model, double forward, double side, double scale,
                           double hp, double speed, double range) {
        Ship3D ship = addShip(name, 0, model, forward, side, scale, hp, speed, range);
        ship.formationForward = forward;
        ship.formationSide = side;
    }

    private void spawnEnemyWave() {
        Ship3D player = playerShip();
        if (player == null) return;
        int count = 3 + Math.min(5, enemyWaveNumber / 2) + rng.nextInt(3);
        double baseAngle = player.heading + Math.toRadians(-28.0 + rng.nextDouble() * 56.0);
        double distance = 980.0 + rng.nextDouble() * 440.0;
        double cx = player.x + Math.cos(baseAngle) * distance;
        double cz = player.z + Math.sin(baseAngle) * distance;
        for (int i = 0; i < count; i++) {
            GlbModel model = enemyWaveModel(i);
            double spread = (i - (count - 1) * 0.5) * 92.0;
            double sideAngle = baseAngle + Math.PI * 0.5;
            double x = cx + Math.cos(sideAngle) * spread + (rng.nextDouble() - 0.5) * 80.0;
            double z = cz + Math.sin(sideAngle) * spread + (rng.nextDouble() - 0.5) * 80.0;
            int team = (enemyWaveNumber % 5 == 4) ? 3 : 1;
            Ship3D enemy = addShip("Incoming Raider " + (enemyWaveNumber + 1) + "." + (i + 1),
                    team, model, x, z, enemyScaleFor(model), 210 + enemyWaveNumber * 16,
                    125 + rng.nextDouble() * 80.0, 350 + rng.nextDouble() * 160.0);
            enemy.heading = Math.atan2(player.z - z, player.x - x);
            enemy.cooldown = rng.nextDouble() * enemy.fireDelay;
        }
    }

    private GlbModel enemyWaveModel(int index) {
        return switch ((enemyWaveNumber + index) % 8) {
            case 0 -> loadBestAny(new String[]{"red", "picket"}, new String[]{"yellow", "picket"}, new String[]{"green", "picket"});
            case 1 -> loadBestAny(new String[]{"red", "patrol"}, new String[]{"yellow", "patrol"}, new String[]{"green", "patrol"});
            case 2 -> loadBestAny(new String[]{"red", "stealth"}, new String[]{"yellow", "stealth"}, new String[]{"green", "stealth"});
            case 3 -> loadBestAny(new String[]{"red", "missile"}, new String[]{"yellow", "missile"}, new String[]{"green", "missile"});
            case 4 -> loadBestAny(new String[]{"red", "medium", "cruiser"}, new String[]{"yellow", "medium", "cruiser"}, new String[]{"green", "cruiser"});
            case 5 -> loadBestAny(new String[]{"red", "hauler"}, new String[]{"yellow", "hauler"}, new String[]{"green", "hauler"});
            case 6 -> loadBestAny(new String[]{"red", "miner"}, new String[]{"yellow", "mining"}, new String[]{"green", "miner"});
            default -> loadBestAny(new String[]{"red", "transport"}, new String[]{"yellow", "transport"}, new String[]{"green", "transport"});
        };
    }

    private double enemyScaleFor(GlbModel model) {
        if (model == null || model.name == null) return 28.0;
        String n = model.name.toLowerCase(Locale.US);
        if (n.contains("cruiser") || n.contains("supership")) return 42.0;
        if (n.contains("hauler") || n.contains("transport")) return 30.0;
        if (n.contains("missile")) return 28.0;
        return 22.0;
    }

    private void buildDemoProps() {
        for (int i = 0; i < 18; i++) {
            double a = i / 18.0 * Math.PI * 2.0;
            double r = 280.0 + (i % 5) * 95.0;
            props.add(new DemoProp(
                    PropKind.ASTEROID,
                    Math.cos(a) * r,
                    20.0 + (i % 3) * 12.0,
                    Math.sin(a) * r,
                    24.0 + (i % 4) * 9.0,
                    i * 31.0,
                    new Tone(0.58f, 0.62f, 0.66f)));
        }
        props.add(new DemoProp(PropKind.BEACON, 0, 52, 0, 52, 0, new Tone(0.34f, 0.86f, 1.0f)));
        props.add(new DemoProp(PropKind.BEACON, -760, 38, -520, 34, 0, Tone.forTeam(0)));
        props.add(new DemoProp(PropKind.BEACON, 760, 38, 520, 34, 0, Tone.forTeam(1)));
    }

    private Ship3D addShip(String name, int team, GlbModel model, double x, double z, double scale,
                           double hp, double speed, double range) {
        Ship3D ship = new Ship3D(name, team, model, x, z, scale, hp, speed, range);
        ships.add(ship);
        return ship;
    }

    private void discoverModels(Path modelDir) {
        modelFiles.clear();
        if (modelDir == null || !Files.isDirectory(modelDir)) return;
        try (Stream<Path> stream = Files.list(modelDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.US).endsWith(".glb"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.US)))
                    .forEach(modelFiles::add);
        } catch (Exception ex) {
            System.err.println("Could not scan model dir " + modelDir + ": " + ex.getMessage());
        }
    }

    private GlbModel loadBest(String... terms) {
        int budget = budgetForTerms(terms);
        return loadBestWithBudget(budget, terms);
    }

    private GlbModel loadBestWithBudget(int budget, String... terms) {
        String key = budget + "\u0002" + String.join("\u0001", terms).toLowerCase(Locale.US);
        GlbModel cached = modelCache.get(key);
        if (cached != null) return cached;
        Path path = findBest(terms);
        if (path == null) {
            System.err.println("No GLB found for " + String.join(" ", terms));
            return null;
        }
        GlbModel model = GlbModel.load(path, budget);
        if (!model.isRenderable()) {
            System.err.println("GLB not renderable " + path.getFileName() + ": " + model.issue);
        }
        modelCache.put(key, model);
        return model;
    }

    private GlbModel loadBestAny(String[]... choices) {
        if (choices == null) return null;
        for (String[] choice : choices) {
            GlbModel model = loadBest(choice);
            if (model != null && model.isRenderable()) return model;
        }
        return null;
    }

    private static int budgetForTerms(String... terms) {
        if (terms == null) return SMALL_MODEL_TRIANGLE_BUDGET;
        String joined = String.join(" ", terms).toLowerCase(Locale.US);
        if (joined.contains("mothership")) return MOTHERSHIP_MODEL_TRIANGLE_BUDGET;
        if (joined.contains("titan")
                || joined.contains("bulwark")
                || joined.contains("command")
                || joined.contains("battlecruiser")
                || joined.contains("battleship")
                || joined.contains("dreadn")
                || joined.contains("supership")
                || joined.contains("carrier")) {
            return CAPITAL_MODEL_TRIANGLE_BUDGET;
        }
        return SMALL_MODEL_TRIANGLE_BUDGET;
    }

    private Path findBest(String... terms) {
        Path best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Path file : modelFiles) {
            String name = file.getFileName().toString().toLowerCase(Locale.US).replace('+', ' ');
            boolean matches = true;
            for (String term : terms) {
                if (!name.contains(term.toLowerCase(Locale.US))) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            int score = 0;
            if (name.contains("modern")) score += 10;
            if (name.contains("copy")) score -= 2;
            if (name.contains("(1)")) score -= 1;
            score -= name.length() / 10;
            if (score > bestScore) {
                bestScore = score;
                best = file;
            }
        }
        return best;
    }

    private static void perspective(double fovY, double aspect, double near, double far) {
        double top = Math.tan(Math.toRadians(fovY) * 0.5) * near;
        double bottom = -top;
        double right = top * aspect;
        double left = -right;
        glFrustum(left, right, bottom, top, near, far);
    }

    private static double turnToward(double current, double target, double maxStep) {
        double delta = normalizeAngle(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return normalizeAngle(current + Math.copySign(maxStep, delta));
    }

    private static double normalizeAngle(double a) {
        while (a <= -Math.PI) a += Math.PI * 2.0;
        while (a > Math.PI) a -= Math.PI * 2.0;
        return a;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String parseScenario(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.startsWith("--scenario=")) return arg.substring("--scenario=".length()).trim();
        }
        return "mothership";
    }

    private static String normalizeScenario(String scenario) {
        if (scenario == null || scenario.isBlank()) return "skirmish";
        String s = scenario.trim().toLowerCase(Locale.US);
        return switch (s) {
            case "1", "mothership", "sandbox", "fleet-sandbox", "fleet_sandbox" -> "mothership";
            case "2", "skirmish" -> "skirmish";
            case "3", "capital", "capital-duel" -> "capital";
            case "4", "swarm", "fighter-swarm" -> "swarm";
            case "5", "fourteam", "four-team", "four_team" -> "fourteam";
            case "6", "gallery", "showcase" -> "gallery";
            default -> "mothership";
        };
    }

    private void assignPlayerShip() {
        for (Ship3D ship : ships) ship.playerControlled = false;
        for (int i = 0; i < ships.size(); i++) {
            Ship3D ship = ships.get(i);
            if (ship.team == 0 && ship.alive()) {
                playerShipIndex = i;
                selectedShipIndex = i;
                ship.playerControlled = true;
                return;
            }
        }
    }

    private void selectNextPlayerShip() {
        if (ships.isEmpty()) return;
        int start = Math.floorMod(playerShipIndex + 1, ships.size());
        int index = start;
        for (int i = 0; i < ships.size(); i++) {
            Ship3D ship = ships.get(index);
            if (ship.team == 0 && ship.alive()) {
                for (Ship3D s : ships) s.playerControlled = false;
                playerShipIndex = index;
                selectedShipIndex = index;
                ship.playerControlled = true;
                return;
            }
            index = (index + 1) % ships.size();
        }
    }

    private Ship3D playerShip() {
        if (ships.isEmpty()) return null;
        if (playerShipIndex < 0 || playerShipIndex >= ships.size()) playerShipIndex = 0;
        Ship3D ship = ships.get(playerShipIndex);
        if (ship.alive()) return ship;
        selectNextPlayerShip();
        if (ships.isEmpty()) return null;
        ship = ships.get(playerShipIndex);
        return ship.alive() ? ship : null;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double lerpAngleDegrees(double a, double b, double t) {
        double delta = b - a;
        while (delta <= -180.0) delta += 360.0;
        while (delta > 180.0) delta -= 360.0;
        return a + delta * t;
    }

    private static Path resolveModelDir(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.startsWith("--model-dir=")) return Path.of(arg.substring("--model-dir=".length()).trim());
        }
        String configured = System.getProperty("eagles.modelDir");
        if (configured == null || configured.isBlank()) configured = System.getenv("EAGLES_3D_MODEL_DIR");
        if (configured == null || configured.isBlank()) return DEFAULT_MODEL_DIR;
        return Path.of(configured.trim());
    }

    private static final class Ship3D {
        final String name;
        final int team;
        final GlbModel model;
        final double scale;
        final double maxHp;
        final double speed;
        final double preferredRange;
        final double weaponRange;
        final double projectileSpeed;
        double damage;
        double fireDelay;
        final double turnRate;
        final double collisionRadius;
        double x;
        double y = 28.0;
        double z;
        double hp;
        double heading;
        double cooldown;
        double formationForward;
        double formationSide;
        boolean playerControlled;

        Ship3D(String name, int team, GlbModel model, double x, double z, double scale,
               double hp, double speed, double preferredRange) {
            this.name = name;
            this.team = team;
            this.model = model;
            this.x = x;
            this.z = z;
            this.scale = scale;
            this.maxHp = hp;
            this.hp = hp;
            this.speed = speed;
            this.preferredRange = preferredRange;
            this.weaponRange = preferredRange * 1.32;
            this.projectileSpeed = 520.0;
            this.damage = Math.max(7.0, scale * 0.38);
            this.fireDelay = Math.max(0.20, 1.15 - scale / 110.0);
            this.turnRate = Math.toRadians(Math.max(28.0, 92.0 - scale * 0.62));
            this.collisionRadius = Math.max(14.0, scale * 0.70);
        }

        boolean alive() {
            return hp > 0.0;
        }
    }

    private static final class Projectile3D {
        final int team;
        final double vx;
        final double vy;
        final double vz;
        final double damage;
        double x;
        double y;
        double z;
        double ttl;
        boolean alive = true;

        Projectile3D(int team, double x, double y, double z, double vx, double vy, double vz, double damage, double ttl) {
            this.team = team;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.damage = damage;
            this.ttl = ttl;
        }
    }

    private enum PropKind {
        ASTEROID,
        BEACON
    }

    private static final class DemoProp {
        final PropKind kind;
        final double x;
        final double y;
        final double z;
        final double scale;
        final double rotation;
        final Tone tone;

        DemoProp(PropKind kind, double x, double y, double z, double scale, double rotation, Tone tone) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.rotation = rotation;
            this.tone = tone;
        }
    }

    private record Tone(float r, float g, float b) {
        static Tone forTeam(int team) {
            return switch (team) {
                case 0 -> new Tone(0.28f, 0.62f, 1.00f);
                case 1 -> new Tone(1.00f, 0.30f, 0.26f);
                case 2 -> new Tone(0.24f, 0.88f, 0.50f);
                case 3 -> new Tone(1.00f, 0.78f, 0.28f);
                default -> new Tone(0.76f, 0.82f, 0.90f);
            };
        }
    }
}
