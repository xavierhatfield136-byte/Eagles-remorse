import app.config.GameConfig;
import app.config.GameMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Phase 9 deterministic/regression harness for:
 * - room mapping correctness and boundary edge handling
 * - damage-to-room determinism
 * - hazard progression determinism
 * - voice/SFX cooldown behavior checks
 */
public final class Phase9DeterminismHarness {
    private static final double SFX_COOLDOWN_TOLERANCE_SEC = 0.03;
    private static final ShipRole[] ROOM_TEST_ROLES = {
            ShipRole.FRIGATE, ShipRole.BATTLECRUISER, ShipRole.CARRIER, ShipRole.BASE
    };
    private static final ShipRole[] DAMAGE_TEST_ROLES = {
            ShipRole.FRIGATE, ShipRole.BATTLECRUISER, ShipRole.CARRIER
    };

    private Phase9DeterminismHarness() {}

    public static void main(String[] args) {
        boolean strict = false;
        long seed = 90210L;
        int seconds = 180;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if ("--strict".equalsIgnoreCase(a)) {
                strict = true;
            } else if (a.startsWith("--seed=")) {
                seed = parseLong(a.substring("--seed=".length()), seed);
            } else if (a.startsWith("--seconds=")) {
                seconds = Math.max(60, parseInt(a.substring("--seconds=".length()), seconds));
            }
        }

        List<String> failures = new ArrayList<>();

        RoomMappingResult roomMap = verifyRoomMappingCorrectness(seed);
        if (!roomMap.pass) failures.add("room mapping correctness below 95%");

        BoundaryResult boundary = verifyBoundaryEdgeDeterminism(seed);
        if (!boundary.pass) failures.add("room boundary edge determinism failed");

        DeterminismResult damageDet = verifyDamageToRoomDeterminism(seed);
        if (!damageDet.pass) failures.add("damage-to-room determinism mismatch");

        DeterminismResult hazardDet = verifyHazardProgressionDeterminism(seed + 77L);
        if (!hazardDet.pass) failures.add("hazard progression determinism mismatch");

        CooldownResult cooldown = verifyVoiceSfxCooldownBehavior(seed + 131L, seconds);
        if (!cooldown.pass) failures.add("voice/SFX cooldown behavior regression");

        System.out.println("[phase9-det] roomMapping " + roomMap.expectedOk + "/" + roomMap.expectedTotal
                + " (" + fmtPct(roomMap.accuracyPct) + ") pass=" + passFail(roomMap.pass));
        System.out.println("[phase9-det] boundary checks=" + boundary.checks
                + " mismatches=" + boundary.mismatches + " pass=" + passFail(boundary.pass));
        System.out.println("[phase9-det] damageDet traceA=" + damageDet.traceLenA
                + " traceB=" + damageDet.traceLenB + " pass=" + passFail(damageDet.pass));
        System.out.println("[phase9-det] hazardDet traceA=" + hazardDet.traceLenA
                + " traceB=" + hazardDet.traceLenB + " pass=" + passFail(hazardDet.pass));
        System.out.println("[phase9-det] cooldown voiceEvents=" + cooldown.voiceEvents
                + " sfxEvents=" + cooldown.sfxEvents
                + " voiceCooldownViolations=" + cooldown.voiceCooldownViolations
                + " sfxCooldownViolations=" + cooldown.sfxCooldownViolations
                + " missingSfx=" + cooldown.missingSfx
                + " voiceDispatched=" + cooldown.voiceDispatchCount
                + " voiceDrops=" + cooldown.voiceDropCount
                + " pass=" + passFail(cooldown.pass));

        if (failures.isEmpty()) {
            System.out.println("[phase9-det] checks: PASS");
            return;
        }

        System.out.println("[phase9-det] checks: FAIL");
        for (String f : failures) {
            System.out.println(" - " + f);
        }
        if (strict) System.exit(2);
    }

    private static RoomMappingResult verifyRoomMappingCorrectness(long seed) {
        int expectedTotal = 0;
        int expectedOk = 0;
        for (ShipRole role : ROOM_TEST_ROLES) {
            Random rng = new Random(seed + role.ordinal() * 1009L);
            for (ShipRoomLayout.RoomDef room : ShipRoomLayout.profileFor(role)) {
                if (room == null || room.id == null) continue;
                double[] p = pickInteriorPoint(room, rng);
                if (p == null) continue;
                expectedTotal++;
                ShipRoomLayout.RoomDef resolved = RoomHitResolver.resolve(role, p[0], p[1]);
                if (resolved != null && resolved.id == room.id) expectedOk++;
            }
        }
        double pct = (expectedTotal <= 0) ? 100.0 : (100.0 * expectedOk / expectedTotal);
        return new RoomMappingResult(pct >= 95.0, expectedTotal, expectedOk, pct);
    }

    private static BoundaryResult verifyBoundaryEdgeDeterminism(long seed) {
        int checks = 0;
        int mismatches = 0;
        for (ShipRole role : ROOM_TEST_ROLES) {
            for (ShipRoomLayout.RoomDef room : ShipRoomLayout.profileFor(role)) {
                if (room == null || room.xs == null || room.ys == null) continue;
                int n = Math.min(room.xs.length, room.ys.length);
                if (n < 2) continue;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double mx = (room.xs[i] + room.xs[j]) * 0.5;
                    double my = (room.ys[i] + room.ys[j]) * 0.5;
                    checks++;
                    if (!isDeterministicBoundary(role, mx, my)) mismatches++;
                }
                for (int i = 0; i < n; i++) {
                    checks++;
                    if (!isDeterministicBoundary(role, room.xs[i], room.ys[i])) mismatches++;
                }
            }
        }
        return new BoundaryResult(mismatches <= 0, checks, mismatches);
    }

    private static boolean isDeterministicBoundary(ShipRole role, double x, double y) {
        ShipRoomLayout.RoomDef baseline = RoomHitResolver.resolve(role, x, y);
        ShipRoomLayout.RoomId expected = (baseline == null) ? null : baseline.id;
        for (int i = 0; i < 14; i++) {
            ShipRoomLayout.RoomDef rerun = RoomHitResolver.resolve(role, x, y);
            ShipRoomLayout.RoomId actual = (rerun == null) ? null : rerun.id;
            if (actual != expected) return false;
        }
        return true;
    }

    private static DeterminismResult verifyDamageToRoomDeterminism(long seed) {
        int totalA = 0;
        int totalB = 0;
        for (ShipRole role : DAMAGE_TEST_ROLES) {
            List<String> a = runDamageTrace(role, seed + role.ordinal() * 211L);
            List<String> b = runDamageTrace(role, seed + role.ordinal() * 211L);
            totalA += a.size();
            totalB += b.size();
            if (a.isEmpty() || b.isEmpty()) return new DeterminismResult(false, totalA, totalB);
            if (!a.equals(b)) return new DeterminismResult(false, totalA, totalB);
        }
        return new DeterminismResult(totalA > 0 && totalA == totalB, totalA, totalB);
    }

    private static List<String> runDamageTrace(ShipRole role, long seed) {
        List<String> trace = new ArrayList<>();
        Ship.enableDeterministicRandom(seed);
        try {
            FleetShip ship = new FleetShip(role, Faction.ALLY, 1200.0, 1200.0);
            ship.shieldActive = false;
            ship.shield = 0.0;
            ship.shieldMax = 0.0;

            List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(role);
            if (rooms.isEmpty()) return trace;
            Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);

            for (int i = 0; i < 64; i++) {
                ShipRoomLayout.RoomDef target = rooms.get(i % rooms.size());
                double[] p = pickInteriorPoint(target, rng);
                if (p == null) continue;
                double wx = ship.x + p[0] * ship.radius;
                double wy = ship.y + p[1] * ship.radius;
                int dmg = 2 + (i % 3);
                ship.takeDamage(dmg, wx, wy, 0.0, 0.0);

                RoomDamageResult r = ship.lastRoomDamageResult();
                trace.add(signature(r));

                if (ship.hp < ship.hpMax * 0.20) {
                    ship.resetInternalSystems();
                    ship.fullyRepairHull();
                    ship.shield = 0.0;
                }
                if (!ship.alive) break;
            }
            return trace;
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    private static DeterminismResult verifyHazardProgressionDeterminism(long seed) {
        List<String> a = runHazardTrace(seed);
        List<String> b = runHazardTrace(seed);
        return new DeterminismResult(a.equals(b), a.size(), b.size());
    }

    private static List<String> runHazardTrace(long seed) {
        List<String> trace = new ArrayList<>();
        Ship.enableDeterministicRandom(seed);
        try {
            FleetShip ship = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 1000.0, 1000.0);
            ship.shieldActive = false;
            ship.shield = 0.0;
            ship.shieldMax = 0.0;
            ship.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;

            ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(ship.role, ShipRoomLayout.RoomId.REACTOR);
            if (reactor != null) {
                double cx = avg(reactor.xs);
                double cy = avg(reactor.ys);
                double wx = ship.x + cx * ship.radius;
                double wy = ship.y + cy * ship.radius;
                for (int i = 0; i < 7; i++) {
                    ship.takeDamage(3, wx, wy, 0.0, 0.0);
                }
            }

            int ticks = 45 * 60;
            for (int tick = 0; tick < ticks; tick++) {
                if (tick > 0 && tick % 300 == 0) {
                    ship.suppressFireInRoom(ShipRoomLayout.RoomId.REACTOR);
                }
                ship.update(GameContext.DT);
                if (tick % 20 == 0) {
                    trace.add(hazardSignature(ship));
                }
            }
            return trace;
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    private static CooldownResult verifyVoiceSfxCooldownBehavior(long seed, int seconds) {
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
            for (int tick = 0; tick < ticks; tick++) {
                injectSignals(ctx, tick);
                PhysicsSystem.update(ctx, GameContext.DT);
                AISystem.update(ctx, GameContext.DT);
                CarrierSystem.update(ctx, GameContext.DT);
                EconomySystem.update(ctx, GameContext.DT);
                CampaignSystem.update(ctx, GameContext.DT);
                LastStandSystem.update(ctx, GameContext.DT);
                EventSystem.update(ctx, GameContext.DT);
                AudioSystem.update(ctx, GameContext.DT);
                UISystem.updatePings(ctx, GameContext.DT);
                keepPlayerAlive(ctx, tick);
            }
        } finally {
            AudioSystem.setTelemetryOnly(false);
        }

        List<AudioEvent> voice = new ArrayList<>();
        List<AudioEvent> sfx = new ArrayList<>();
        int missingSfx = 0;
        for (AudioEvent ev : ctx.audioEvents) {
            if (ev == null || ev.eventId == null) continue;
            if ("voice".equals(ev.duckingClass)) voice.add(ev);
            if (ev.eventId.startsWith("sfx.")) {
                sfx.add(ev);
                if ("sfx_missing".equals(ev.duckingClass)) missingSfx++;
            }
        }
        voice.sort(Comparator.comparingLong(a -> a.timestamp));
        sfx.sort(Comparator.comparingLong(a -> a.timestamp));

        int voiceCooldownViol = countVoiceCooldownViolations(voice);
        int sfxCooldownViol = countSfxCooldownViolations(sfx);
        AudioSystem.VoiceTelemetrySnapshot voiceTelemetry = AudioSystem.voiceTelemetry(ctx);

        boolean voiceExpected = !AudioSystem.voiceEventMatrix().isEmpty();
        boolean pass = (!voiceExpected || voice.isEmpty())
                && !sfx.isEmpty()
                && voiceCooldownViol == 0
                && sfxCooldownViol == 0
                && missingSfx == 0;

        return new CooldownResult(
                pass,
                voice.size(),
                sfx.size(),
                voiceCooldownViol,
                sfxCooldownViol,
                missingSfx,
                voiceTelemetry.dispatchCount(),
                voiceTelemetry.dropCount()
        );
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
        if (p.hp < p.hpMax * 0.38) {
            p.hp = (int) Math.round(p.hpMax * 0.85);
        }
        if (p.shieldMax > 0.0 && p.shield < p.shieldMax * 0.15) {
            p.shield = p.shieldMax * 0.80;
        }
        if (tick % 540 == 250 && p.shieldMax > 0.0) {
            p.shield = Math.min(p.shield, p.shieldMax * 0.16);
        }
        if (tick % 700 == 360) {
            p.takeDamage(4, p.x + p.radius, p.y);
        }
    }

    private static int countVoiceCooldownViolations(List<AudioEvent> voiceEvents) {
        int violations = 0;
        java.util.Map<String, Long> lastByKey = new java.util.HashMap<>();
        for (AudioEvent ev : voiceEvents) {
            if (ev == null || ev.cooldownKey == null) continue;
            Long last = lastByKey.get(ev.cooldownKey);
            if (last != null) {
                double elapsed = (ev.timestamp - last) / 1_000_000_000.0;
                double required = AudioSystem.voiceCooldownSeconds(ev.cooldownKey);
                if (required > 1e-6 && elapsed + 1e-3 < required) {
                    violations++;
                }
            }
            lastByKey.put(ev.cooldownKey, ev.timestamp);
        }
        return violations;
    }

    private static int countSfxCooldownViolations(List<AudioEvent> sfxEvents) {
        int violations = 0;
        java.util.Map<String, Long> lastByEvent = new java.util.HashMap<>();
        for (AudioEvent ev : sfxEvents) {
            if (ev == null || ev.eventId == null) continue;
            if (!ev.eventId.startsWith("sfx.")) continue;
            String id = ev.eventId.substring(4);
            SfxManifest.EventSpec spec = SfxManifest.byId(id);
            if (spec == null) continue;

            Long last = lastByEvent.get(id);
            if (last != null) {
                double elapsed = (ev.timestamp - last) / 1_000_000_000.0;
                double required = Math.max(0.0, spec.cooldownSec());
                // Cooldown gating uses per-update wall-clock snapshots, while event timestamps are captured
                // at log time. Allow a small tolerance to avoid false positives near boundary edges.
                if (required > 1e-6 && elapsed + SFX_COOLDOWN_TOLERANCE_SEC < required) {
                    violations++;
                }
            }
            lastByEvent.put(id, ev.timestamp);
        }
        return violations;
    }

    private static String hazardSignature(Ship ship) {
        List<Ship.RoomStatus> rooms = ship.roomStatusSnapshot();
        int fireRooms = 0;
        double fireLoad = 0.0;
        double hpFracAcc = 0.0;
        String hotspot = "NONE";
        double hottest = 0.0;
        for (Ship.RoomStatus rs : rooms) {
            if (rs == null) continue;
            if (rs.hpMax > 1e-9) hpFracAcc += MathUtil.clamp(rs.hp / rs.hpMax, 0.0, 1.0);
            if (rs.fireIntensity > 0.05) {
                fireRooms++;
                fireLoad += rs.fireIntensity;
            }
            if (rs.fireIntensity > hottest) {
                hottest = rs.fireIntensity;
                hotspot = (rs.roomId == null) ? "NONE" : rs.roomId.name();
            }
        }
        return fireRooms + "|" + fmt3(fireLoad) + "|" + fmt3(hpFracAcc) + "|" + hotspot;
    }

    private static String signature(RoomDamageResult r) {
        if (r == null) return "NONE";
        String trans = (r.subsystemTransitions == null || r.subsystemTransitions.isEmpty())
                ? "-"
                : String.join(",", r.subsystemTransitions);
        return r.roomId + "|" + fmt3(r.hpBefore) + "|" + fmt3(r.hpAfter)
                + "|h" + r.hazardRolls
                + "|d" + r.shipStructuralDelta
                + "|rl" + fmt3(r.roomLocalHpLoss)
                + "|" + trans;
    }

    private static double[] pickInteriorPoint(ShipRoomLayout.RoomDef room, Random rng) {
        if (room == null || room.xs == null || room.ys == null) return null;
        int n = Math.min(room.xs.length, room.ys.length);
        if (n < 3) return null;

        double cx = avg(room.xs);
        double cy = avg(room.ys);
        if (room.contains(cx, cy)) return new double[]{cx, cy};

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, room.xs[i]);
            maxX = Math.max(maxX, room.xs[i]);
            minY = Math.min(minY, room.ys[i]);
            maxY = Math.max(maxY, room.ys[i]);
        }
        for (int i = 0; i < 80; i++) {
            double x = minX + rng.nextDouble() * Math.max(1e-9, maxX - minX);
            double y = minY + rng.nextDouble() * Math.max(1e-9, maxY - minY);
            if (room.contains(x, y)) return new double[]{x, y};
        }
        return null;
    }

    private static double avg(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static String fmt3(double v) {
        return String.format(Locale.US, "%.3f", v);
    }

    private static String fmtPct(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    private static String passFail(boolean pass) {
        return pass ? "PASS" : "FAIL";
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

    private record RoomMappingResult(boolean pass, int expectedTotal, int expectedOk, double accuracyPct) {}
    private record BoundaryResult(boolean pass, int checks, int mismatches) {}
    private record DeterminismResult(boolean pass, int traceLenA, int traceLenB) {}
    private record CooldownResult(
            boolean pass,
            int voiceEvents,
            int sfxEvents,
            int voiceCooldownViolations,
            int sfxCooldownViolations,
            int missingSfx,
            int voiceDispatchCount,
            int voiceDropCount) {}
}
