import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Consolidated validation harness for remaining objective checklist gates.
 *
 * Covers:
 * - Phase 6 acceptance signals (fire start/spread alive, suppression containment)
 * - Phase 8 acceptance signals (repeated-area room consistency, x-ray/gameplay sync)
 * - Room profile coverage for playable roles
 * - Performance budgets (room resolver, hazard update, x-ray draw, audio dispatch, memory)
 */
public final class ChecklistV2Harness {
    private static final int PERF_SHIP_COUNT = 200;
    private static final double MB = 1024.0 * 1024.0;
    private static volatile Object memorySink;

    private ChecklistV2Harness() {}

    public static void main(String[] args) {
        boolean strict = false;
        long seed = 424242L;
        int audioSeconds = 180;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if ("--strict".equalsIgnoreCase(a)) {
                strict = true;
            } else if (a.startsWith("--seed=")) {
                seed = parseLong(a.substring("--seed=".length()), seed);
            } else if (a.startsWith("--audio-seconds=")) {
                audioSeconds = Math.max(60, parseInt(a.substring("--audio-seconds=".length()), audioSeconds));
            }
        }

        List<String> failures = new ArrayList<>();

        Phase6Result phase6 = validatePhase6Acceptance(seed);
        if (!phase6.pass()) failures.add("phase6 acceptance");

        Phase8Result phase8 = validatePhase8Acceptance(seed + 17L);
        if (!phase8.pass()) failures.add("phase8 acceptance");

        RoomProfileCoverage profiles = validateRoomProfileCoverage();
        if (!profiles.pass()) failures.add("room profile coverage");

        BudgetResult budgets = validatePerformanceBudgets(seed + 99L, audioSeconds);
        if (!budgets.passRoomHit()) failures.add("room-hit budget");
        if (!budgets.passHazard()) failures.add("hazard budget");
        if (!budgets.passXray()) failures.add("x-ray budget");
        if (!budgets.passAudio()) failures.add("audio budget");
        if (!budgets.passMemory()) failures.add("memory budget");
        if (!budgets.passUpdateIncrease()) failures.add("update-time increase budget");

        System.out.println("[checklist-v2] phase6 fireStartedAlive=" + passFail(phase6.fireStartedAlive())
                + " fireSpreadAlive=" + passFail(phase6.fireSpreadAlive())
                + " suppressionContained=" + passFail(phase6.suppressionContained())
                + " prePeak=" + fmt3(phase6.preSuppressionPeak())
                + " postMin=" + fmt3(phase6.postSuppressionMin())
                + " pass=" + passFail(phase6.pass()));
        System.out.println("[checklist-v2] phase8 repeatedAreaRatio=" + fmtPct(phase8.repeatedAreaConsistencyRatio())
                + " resultSyncMismatches=" + phase8.damageResultSyncMismatches()
                + " hazardSyncMismatches=" + phase8.hazardSyncMismatches()
                + " pass=" + passFail(phase8.pass()));
        System.out.println("[checklist-v2] roomProfiles coveredRoles=" + profiles.coveredRoles()
                + "/" + profiles.totalRoles()
                + " missingDefs=" + profiles.missingDefinitions()
                + " pass=" + passFail(profiles.pass()));

        System.out.println("[checklist-v2] budgets roomHitMsPer100=" + fmt3(budgets.roomHitMsPer100())
                + " hazardMsFrame200=" + fmt3(budgets.hazardMsPerFrame())
                + " xrayMsDraw=" + fmt3(budgets.xrayMsPerDraw())
                + " audioMsFrame=" + fmt3(budgets.audioMsPerFrame())
                + " memoryMB=" + fmt3(budgets.memoryOverheadMb())
                + " updateIncreasePct=" + fmt3(budgets.updateIncreasePct()));
        System.out.println("[checklist-v2] budgetPass roomHit=" + passFail(budgets.passRoomHit())
                + " hazard=" + passFail(budgets.passHazard())
                + " xray=" + passFail(budgets.passXray())
                + " audio=" + passFail(budgets.passAudio())
                + " memory=" + passFail(budgets.passMemory())
                + " updateIncrease=" + passFail(budgets.passUpdateIncrease()));

        if (failures.isEmpty()) {
            System.out.println("[checklist-v2] checks: PASS");
            return;
        }

        System.out.println("[checklist-v2] checks: FAIL");
        for (String f : failures) {
            System.out.println(" - " + f);
        }
        if (strict) System.exit(2);
    }

    private static Phase6Result validatePhase6Acceptance(long seed) {
        boolean startedAlive = false;
        boolean spreadAlive = false;
        boolean suppressionContained = false;
        double prePeak = 0.0;
        double postMin = Double.POSITIVE_INFINITY;

        Ship.enableDeterministicRandom(seed);
        try {
            FleetShip ship = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 1000.0, 1000.0);
            ship.shieldActive = false;
            ship.shieldMax = 0.0;
            ship.shield = 0.0;

            ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(ship.role, ShipRoomLayout.RoomId.REACTOR);
            double wx = ship.x;
            double wy = ship.y;
            if (reactor != null) {
                wx = ship.x + avg(reactor.xs) * ship.radius;
                wy = ship.y + avg(reactor.ys) * ship.radius;
            }

            for (int tick = 0; tick < 1800; tick++) {
                if (tick < 360 && tick % 18 == 0) {
                    ship.takeDamage(3, wx, wy, 0.0, 0.0);
                }
                if (tick >= 600) {
                    ship.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
                    if (tick % 15 == 0) ship.suppressHottestFire();
                }

                ship.update(GameContext.DT);
                if (ship.hp < ship.hpMax * 0.22) ship.hp = (int) Math.round(ship.hpMax * 0.55);

                int fireRooms = ship.activeFireRoomCount();
                double fireLoad = ship.totalFireIntensity();
                if (ship.hp > 0 && fireRooms > 0) startedAlive = true;
                if (ship.hp > 0 && fireRooms > 1) spreadAlive = true;

                if (tick < 600) prePeak = Math.max(prePeak, fireLoad);
                if (tick >= 900) postMin = Math.min(postMin, fireLoad);
            }
        } finally {
            Ship.disableDeterministicRandom();
        }

        if (!Double.isFinite(postMin)) postMin = 0.0;
        if (prePeak > 0.70) suppressionContained = postMin <= prePeak * 0.65;
        boolean pass = startedAlive && spreadAlive && suppressionContained;
        return new Phase6Result(pass, startedAlive, spreadAlive, suppressionContained, prePeak, postMin);
    }

    private static Phase8Result validatePhase8Acceptance(long seed) {
        double repeatedRatio = repeatedAreaConsistency(seed);
        int resultSyncMismatches = 0;
        int hazardSyncMismatches = 0;

        Ship.enableDeterministicRandom(seed ^ 0xABCDEF12345L);
        try {
            FleetShip ship = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 1200.0, 1200.0);
            ship.shieldActive = false;
            ship.shieldMax = 0.0;
            ship.shield = 0.0;

            ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(ship.role, ShipRoomLayout.RoomId.REACTOR);
            if (reactor != null) {
                double wx = ship.x + avg(reactor.xs) * ship.radius;
                double wy = ship.y + avg(reactor.ys) * ship.radius;
                for (int i = 0; i < 12; i++) ship.takeDamage(3, wx, wy, 0.0, 0.0);
            }

            for (int tick = 0; tick < 480; tick++) {
                ShipRoomLayout.RoomDef hitRoom = ShipRoomLayout.roomForId(ship.role, ShipRoomLayout.RoomId.MAIN_WEAPON);
                if (hitRoom != null && tick % 30 == 0) {
                    double wx = ship.x + avg(hitRoom.xs) * ship.radius;
                    double wy = ship.y + avg(hitRoom.ys) * ship.radius;
                    ship.takeDamage(2, wx, wy, 0.0, 0.0);

                    RoomDamageResult result = ship.lastRoomDamageResult();
                    if (result != null && result.roomId != null && !result.roomId.isBlank()) {
                        double hp = roomHpById(ship, result.roomId);
                        if (Double.isFinite(hp) && Math.abs(hp - result.hpAfter) > 1e-6) {
                            resultSyncMismatches++;
                        }
                    }
                }

                if (tick % 40 == 0) ship.suppressHottestFire();
                ship.update(GameContext.DT);

                Map<String, Double> hazardIntensityByRoom = new HashMap<>();
                for (HazardState hz : ship.hazardStateSnapshot()) {
                    if (hz == null || hz.roomId == null) continue;
                    hazardIntensityByRoom.put(hz.roomId, Math.max(0.0, hz.intensity));
                }
                for (Ship.RoomStatus rs : ship.roomStatusSnapshot()) {
                    if (rs == null || rs.roomId == null) continue;
                    String id = rs.roomId.name();
                    double a = Math.max(0.0, rs.fireIntensity);
                    double b = hazardIntensityByRoom.getOrDefault(id, 0.0);
                    if (Math.abs(a - b) > 1e-6) hazardSyncMismatches++;
                }
                if (ship.hp < ship.hpMax * 0.24) ship.hp = (int) Math.round(ship.hpMax * 0.52);
            }
        } finally {
            Ship.disableDeterministicRandom();
        }

        boolean pass = repeatedRatio >= 0.95 && resultSyncMismatches == 0 && hazardSyncMismatches == 0;
        return new Phase8Result(pass, repeatedRatio, resultSyncMismatches, hazardSyncMismatches);
    }

    private static RoomProfileCoverage validateRoomProfileCoverage() {
        int totalRoles = 0;
        int coveredRoles = 0;
        int missing = 0;

        EnumSet<ShipRole> excluded = EnumSet.of(ShipRole.STATIC_TURRET);
        for (ShipRole role : ShipRole.values()) {
            if (excluded.contains(role)) continue;
            totalRoles++;
            boolean ok = true;
            for (ShipRoomLayout.RoomId id : ShipRoomLayout.RoomId.values()) {
                if (ShipRoomLayout.roomForId(role, id) == null) {
                    ok = false;
                    missing++;
                }
            }
            if (ok) coveredRoles++;
        }
        return new RoomProfileCoverage(coveredRoles == totalRoles, coveredRoles, totalRoles, missing);
    }

    private static BudgetResult validatePerformanceBudgets(long seed, int audioSeconds) {
        double roomHitMsPer100 = benchmarkRoomHitResolver(seed);
        HazardPerf hazardPerf = benchmarkHazardUpdate(seed + 1);
        double xrayMsPerDraw = benchmarkXrayDraw(seed + 2);
        double audioMsPerFrame = benchmarkAudioDispatch(seed + 3, audioSeconds);
        double memoryOverheadMb = benchmarkMemoryOverhead(seed + 4);

        boolean passRoomHit = roomHitMsPer100 < 0.2;
        boolean passHazard = hazardPerf.stressMsPerFrame() < 1.0;
        boolean passXray = xrayMsPerDraw < 0.7;
        boolean passAudio = audioMsPerFrame < 0.2;
        boolean passMemory = memoryOverheadMb < 40.0;
        double updateIncreasePct = benchmarkUpdateIncreasePct(seed + 5, Math.max(90, audioSeconds));
        boolean passIncrease = updateIncreasePct < 15.0;

        return new BudgetResult(
                roomHitMsPer100,
                hazardPerf.stressMsPerFrame(),
                xrayMsPerDraw,
                audioMsPerFrame,
                memoryOverheadMb,
                updateIncreasePct,
                passRoomHit,
                passHazard,
                passXray,
                passAudio,
                passMemory,
                passIncrease
        );
    }

    private static double benchmarkRoomHitResolver(long seed) {
        ShipRole[] roles = {
                ShipRole.FRIGATE, ShipRole.BATTLECRUISER, ShipRole.CARRIER, ShipRole.BASE
        };
        int checks = 400_000;
        Random rng = new Random(seed);
        long t0 = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < checks; i++) {
            ShipRole role = roles[i % roles.length];
            double x = -1.0 + rng.nextDouble() * 2.0;
            double y = -1.0 + rng.nextDouble() * 2.0;
            ShipRoomLayout.RoomDef room = RoomHitResolver.resolve(role, x, y);
            if (room != null) hits++;
        }
        long elapsed = System.nanoTime() - t0;
        if (hits < 0) System.out.println(); // keep optimizer from stripping loop assumptions
        double msPerCheck = elapsed / (double) checks / 1_000_000.0;
        return msPerCheck * 100.0;
    }

    private static HazardPerf benchmarkHazardUpdate(long seed) {
        List<FleetShip> baseline = createPerfShips(seed, false);
        List<FleetShip> stress = createPerfShips(seed + 1, true);

        double baseMs = measureShipUpdateMsPerFrame(baseline, 900);
        double stressMs = measureShipUpdateMsPerFrame(stress, 900);
        double pct = (baseMs <= 1e-9) ? 0.0 : ((stressMs - baseMs) / baseMs) * 100.0;
        return new HazardPerf(baseMs, stressMs, pct);
    }

    private static List<FleetShip> createPerfShips(long seed, boolean igniteHazards) {
        ShipRole[] roles = {
                ShipRole.FRIGATE, ShipRole.BATTLECRUISER, ShipRole.CARRIER, ShipRole.PATROL, ShipRole.LIGHT_CRUISER
        };
        List<FleetShip> out = new ArrayList<>(PERF_SHIP_COUNT);
        Random rng = new Random(seed);
        for (int i = 0; i < PERF_SHIP_COUNT; i++) {
            ShipRole role = roles[i % roles.length];
            FleetShip ship = new FleetShip(role, ((i & 1) == 0) ? Faction.ALLY : Faction.ENEMY,
                    300.0 + (i % 20) * 80.0,
                    300.0 + (i / 20) * 55.0);
            ship.shieldActive = false;
            ship.shieldMax = 0.0;
            ship.shield = 0.0;
            ship.vx = (rng.nextDouble() - 0.5) * 0.8;
            ship.vy = (rng.nextDouble() - 0.5) * 0.8;

            if (igniteHazards) {
                ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(ship.role, ShipRoomLayout.RoomId.REACTOR);
                if (reactor != null) {
                    double wx = ship.x + avg(reactor.xs) * ship.radius;
                    double wy = ship.y + avg(reactor.ys) * ship.radius;
                    for (int d = 0; d < 6; d++) ship.takeDamage(3, wx, wy, 0.0, 0.0);
                }
            }
            out.add(ship);
        }
        return out;
    }

    private static double measureShipUpdateMsPerFrame(List<FleetShip> ships, int frames) {
        if (ships == null || ships.isEmpty() || frames <= 0) return 0.0;
        long t0 = System.nanoTime();
        for (int f = 0; f < frames; f++) {
            for (FleetShip ship : ships) {
                ship.update(GameContext.DT);
                if (ship.hp < ship.hpMax * 0.20) ship.hp = (int) Math.round(ship.hpMax * 0.55);
            }
        }
        long elapsed = System.nanoTime() - t0;
        return elapsed / (double) frames / 1_000_000.0;
    }

    private static double benchmarkXrayDraw(long seed) {
        try {
            Method m = Renderer.class.getDeclaredMethod("drawLockedTargetXrayHud",
                    Graphics2D.class, GameContext.class, Player.class, Ship.class, boolean.class, int.class, int.class);
            m.setAccessible(true);

            int w = 1920;
            int h = 1080;
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = canvas.createGraphics();
            g2.setClip(0, 0, w, h);
            try {
                GameConfig cfg = new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, seed, false);
                GameContext ctx = new GameContext(cfg);
                Player player = new Player(ShipRole.BATTLECRUISER, 2500.0, 2500.0);
                FleetShip target = new FleetShip(ShipRole.CARRIER, Faction.ENEMY, 2800.0, 2500.0);
                target.name = "Target";
                ctx.player = player;
                ctx.lockedTarget = target;

                // Seed state so x-ray overlays have room/hazard/power visuals.
                ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(player.role, ShipRoomLayout.RoomId.REACTOR);
                if (reactor != null) {
                    double wx = player.x + avg(reactor.xs) * player.radius;
                    double wy = player.y + avg(reactor.ys) * player.radius;
                    for (int i = 0; i < 8; i++) player.takeDamage(2, wx, wy, 0.0, 0.0);
                }
                player.update(GameContext.DT);

                int warmup = 120;
                for (int i = 0; i < warmup; i++) {
                    m.invoke(null, g2, ctx, player, target, false, w, h);
                }

                int iterations = 1200;
                long t0 = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    m.invoke(null, g2, ctx, player, target, false, w, h);
                }
                long elapsed = System.nanoTime() - t0;
                return elapsed / (double) iterations / 1_000_000.0;
            } finally {
                g2.dispose();
            }
        } catch (Throwable ex) {
            // Reflection/layout failures should fail the budget.
            return Double.POSITIVE_INFINITY;
        }
    }

    private static double benchmarkAudioDispatch(long seed, int seconds) {
        GameConfig cfg = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(cfg);
        SpawnSystem.initWorld(ctx);

        if (ctx.player != null) {
            ctx.player.hpMax = Math.max(ctx.player.hpMax, 900);
            ctx.player.hp = ctx.player.hpMax;
            ctx.player.shieldMax = Math.max(ctx.player.shieldMax, 420.0);
            ctx.player.shield = ctx.player.shieldMax;
            ctx.player.shieldActive = true;
            ctx.player.shieldRegen = Math.max(ctx.player.shieldRegen, 8.0);
        }

        AudioSystem.setTelemetryOnly(true);
        try {
            int ticks = seconds * 60;
            long audioNs = 0L;
            for (int tick = 0; tick < ticks; tick++) {
                injectSignals(ctx, tick);
                PhysicsSystem.update(ctx, GameContext.DT);
                AISystem.update(ctx, GameContext.DT);
                CarrierSystem.update(ctx, GameContext.DT);
                EconomySystem.update(ctx, GameContext.DT);
                CampaignSystem.update(ctx, GameContext.DT);
                LastStandSystem.update(ctx, GameContext.DT);
                EventSystem.update(ctx, GameContext.DT);
                long t0 = System.nanoTime();
                AudioSystem.update(ctx, GameContext.DT);
                audioNs += System.nanoTime() - t0;
                UISystem.updatePings(ctx, GameContext.DT);
                keepPlayerAlive(ctx, tick);
            }
            return audioNs / (double) Math.max(1, ticks) / 1_000_000.0;
        } finally {
            AudioSystem.setTelemetryOnly(false);
        }
    }

    private static double benchmarkMemoryOverhead(long seed) {
        forceGc();
        long before = usedMemoryBytes();

        List<FleetShip> ships = createPerfShips(seed, true);
        for (FleetShip ship : ships) {
            ship.roomStatusSnapshot();
            ship.hazardStateSnapshot();
        }
        for (int t = 0; t < 300; t++) {
            for (FleetShip ship : ships) {
                ship.update(GameContext.DT);
            }
        }
        memorySink = ships;

        forceGc();
        long after = usedMemoryBytes();
        long diff = Math.max(0L, after - before);
        return diff / MB;
    }

    private static double benchmarkUpdateIncreasePct(long seed, int seconds) {
        double base = runScenarioUpdateMs(seed, seconds, false);
        double stress = runScenarioUpdateMs(seed, seconds, true);
        if (base <= 1e-9) return 0.0;
        return ((stress - base) / base) * 100.0;
    }

    private static double runScenarioUpdateMs(long seed, int seconds, boolean stress) {
        GameConfig cfg = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(cfg);
        SpawnSystem.initWorld(ctx);
        if (ctx.player != null) {
            ctx.player.hpMax = Math.max(ctx.player.hpMax, 900);
            ctx.player.hp = ctx.player.hpMax;
            ctx.player.shieldMax = Math.max(ctx.player.shieldMax, 420.0);
            ctx.player.shield = ctx.player.shieldMax;
            ctx.player.shieldActive = true;
            ctx.player.shieldRegen = Math.max(ctx.player.shieldRegen, 8.0);
        }

        int ticks = seconds * 60;
        long totalNs = 0L;
        for (int tick = 0; tick < ticks; tick++) {
            injectSignals(ctx, tick);
            if (stress && ctx.player != null && tick % 36 == 0) {
                ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(ctx.player.role, ShipRoomLayout.RoomId.REACTOR);
                if (reactor != null) {
                    double wx = ctx.player.x + avg(reactor.xs) * ctx.player.radius;
                    double wy = ctx.player.y + avg(reactor.ys) * ctx.player.radius;
                    ctx.player.takeDamage(2, wx, wy, 0.0, 0.0);
                }
            }

            long t0 = System.nanoTime();
            PhysicsSystem.update(ctx, GameContext.DT);
            AISystem.update(ctx, GameContext.DT);
            CarrierSystem.update(ctx, GameContext.DT);
            EconomySystem.update(ctx, GameContext.DT);
            CampaignSystem.update(ctx, GameContext.DT);
            LastStandSystem.update(ctx, GameContext.DT);
            EventSystem.update(ctx, GameContext.DT);
            AudioSystem.update(ctx, GameContext.DT);
            UISystem.updatePings(ctx, GameContext.DT);
            totalNs += System.nanoTime() - t0;

            if (stress && tick % 48 == 0 && ctx.player != null) {
                ctx.player.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
                ctx.player.suppressHottestFire();
            }
            keepPlayerAlive(ctx, tick);
        }

        return totalNs / (double) Math.max(1, ticks) / 1_000_000.0;
    }

    private static void injectSignals(GameContext ctx, int tick) {
        if (ctx == null) return;
        if (tick % 420 == 0) {
            GameContext.HelmMode[] modes = {
                    GameContext.HelmMode.INTERCEPT,
                    GameContext.HelmMode.EVASIVE,
                    GameContext.HelmMode.MAINTAIN_RANGE
            };
            UISystem.setHelmMode(ctx, modes[(tick / 420) % modes.length]);
        }
        if (tick % 520 == 0) {
            GameContext.CaptainDirective[] directives = {
                    GameContext.CaptainDirective.ATTACK,
                    GameContext.CaptainDirective.DEFEND,
                    GameContext.CaptainDirective.ESCORT,
                    GameContext.CaptainDirective.REPAIR,
                    GameContext.CaptainDirective.RTB,
                    GameContext.CaptainDirective.BALANCED
            };
            UISystem.applyCaptainDirective(ctx, directives[(tick / 520) % directives.length]);
        }
        if (tick % 360 == 0) ctx.scienceJamming = !ctx.scienceJamming;
        if (tick % 540 == 0) {
            UISystem.setEngineeringMode(ctx, ((tick / 540) & 1) == 0
                    ? GameContext.EngineeringMode.DAMAGE_CONTROL
                    : GameContext.EngineeringMode.BALANCED);
        }
        if (tick % 300 == 0) {
            if (((tick / 300) & 1) == 0) UISystem.scienceLockNearest(ctx);
            else UISystem.scienceClearLock(ctx);
        }
    }

    private static void keepPlayerAlive(GameContext ctx, int tick) {
        if (ctx == null || ctx.player == null) return;
        Ship p = ctx.player;
        if (!p.alive || p.dying || p.hp <= 0) {
            p.alive = true;
            p.dying = false;
            p.hp = Math.max(1, p.hpMax);
        }
        if (p.hp < p.hpMax * 0.38) p.hp = (int) Math.round(p.hpMax * 0.85);
        if (p.shieldMax > 0.0 && p.shield < p.shieldMax * 0.15) p.shield = p.shieldMax * 0.80;
        if (tick % 700 == 360) p.takeDamage(4, p.x + p.radius, p.y);
    }

    private static double repeatedAreaConsistency(long seed) {
        ShipRole[] roles = {
                ShipRole.FRIGATE, ShipRole.BATTLECRUISER, ShipRole.CARRIER, ShipRole.BASE
        };
        int total = 0;
        int consistent = 0;

        Ship.enableDeterministicRandom(seed);
        try {
            for (ShipRole role : roles) {
                FleetShip ship = new FleetShip(role, Faction.ALLY, 900.0, 900.0);
                ship.shieldActive = false;
                ship.shieldMax = 0.0;
                ship.shield = 0.0;

                ShipRoomLayout.RoomId[] targets = {
                        ShipRoomLayout.RoomId.REACTOR,
                        ShipRoomLayout.RoomId.ENGINES,
                        ShipRoomLayout.RoomId.BRIDGE
                };
                for (ShipRoomLayout.RoomId id : targets) {
                    ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(role, id);
                    if (room == null) continue;
                    double nx = avg(room.xs);
                    double ny = avg(room.ys);
                    double wx = ship.x + nx * ship.radius;
                    double wy = ship.y + ny * ship.radius;
                    EnumSet<ShipRoomLayout.RoomId> expectedGroup = EnumSet.of(id);
                    if (room.neighbors != null) {
                        for (ShipRoomLayout.RoomId n : room.neighbors) {
                            if (n != null) expectedGroup.add(n);
                        }
                    }

                    int match = 0;
                    int count = 0;
                    for (int i = 0; i < 24; i++) {
                        ship.takeDamage(1, wx, wy, 0.0, 0.0);
                        RoomDamageResult result = ship.lastRoomDamageResult();
                        if (result == null || result.roomId == null || result.roomId.isBlank()) continue;
                        count++;
                        ShipRoomLayout.RoomId actual = parseRoomId(result.roomId);
                        if (actual != null && expectedGroup.contains(actual)) match++;
                        if (ship.hp < ship.hpMax * 0.22) {
                            ship.hp = (int) Math.round(ship.hpMax * 0.65);
                        }
                    }
                    if (count > 0) {
                        total += count;
                        consistent += match;
                    }
                }
            }
        } finally {
            Ship.disableDeterministicRandom();
        }

        if (total <= 0) return 0.0;
        return consistent / (double) total;
    }

    private static double roomHpById(Ship ship, String roomId) {
        if (ship == null || roomId == null) return Double.NaN;
        for (Ship.RoomStatus rs : ship.roomStatusSnapshot()) {
            if (rs == null || rs.roomId == null) continue;
            if (roomId.equals(rs.roomId.name())) return rs.hp;
        }
        return Double.NaN;
    }

    private static ShipRoomLayout.RoomId parseRoomId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ShipRoomLayout.RoomId.valueOf(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double avg(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static void forceGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static long usedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static String passFail(boolean pass) {
        return pass ? "PASS" : "FAIL";
    }

    private static String fmt3(double v) {
        return String.format(Locale.US, "%.3f", v);
    }

    private static String fmtPct(double v) {
        return String.format(Locale.US, "%.1f%%", v * 100.0);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record Phase6Result(
            boolean pass,
            boolean fireStartedAlive,
            boolean fireSpreadAlive,
            boolean suppressionContained,
            double preSuppressionPeak,
            double postSuppressionMin) {}

    private record Phase8Result(
            boolean pass,
            double repeatedAreaConsistencyRatio,
            int damageResultSyncMismatches,
            int hazardSyncMismatches) {}

    private record RoomProfileCoverage(
            boolean pass,
            int coveredRoles,
            int totalRoles,
            int missingDefinitions) {}

    private record HazardPerf(
            double baseMsPerFrame,
            double stressMsPerFrame,
            double increasePct) {}

    private record BudgetResult(
            double roomHitMsPer100,
            double hazardMsPerFrame,
            double xrayMsPerDraw,
            double audioMsPerFrame,
            double memoryOverheadMb,
            double updateIncreasePct,
            boolean passRoomHit,
            boolean passHazard,
            boolean passXray,
            boolean passAudio,
            boolean passMemory,
            boolean passUpdateIncrease) {}
}
