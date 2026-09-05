import app.config.GameConfig;
import app.config.GameMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 9 telemetry harness.
 *
 * Captures:
 * - room hit distribution
 * - time-to-subsystem-failure
 * - hazard ignition/suppression rates
 * - voice trigger counts and drops
 * - frame/update cost by system
 */
public final class Phase9TelemetryHarness {
    private Phase9TelemetryHarness() {}

    public static void main(String[] args) {
        long seed = 91513L;
        int seconds = 240;
        String output = "build/reports/phase9_telemetry.json";
        boolean strict = false;

        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if (a.startsWith("--seed=")) {
                seed = parseLong(a.substring("--seed=".length()), seed);
            } else if (a.startsWith("--seconds=")) {
                seconds = Math.max(60, parseInt(a.substring("--seconds=".length()), seconds));
            } else if (a.startsWith("--output=")) {
                output = a.substring("--output=".length()).trim();
            } else if ("--strict".equalsIgnoreCase(a)) {
                strict = true;
            }
        }

        TelemetryResult result = runTelemetry(seed, seconds);
        String json = toJson(result);

        Path out = Paths.get(output);
        try {
            Path parent = out.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            System.out.println("[phase9-telemetry] wrote " + out.toAbsolutePath());
        } catch (IOException ex) {
            System.err.println("[phase9-telemetry] write_failed " + ex.getMessage());
            System.out.println(json);
            if (strict) System.exit(2);
            return;
        }

        System.out.println("[phase9-telemetry] roomHitEvents=" + result.roomHitEvents
                + " uniqueRooms=" + result.roomHitDistribution.size());
        System.out.println("[phase9-telemetry] hazard ignitions=" + result.hazardIgnitions
                + " suppressions=" + result.hazardSuppressions);
        System.out.println("[phase9-telemetry] voice dispatched=" + result.voiceDispatchCount
                + " drops=" + result.voiceDropCount);
        System.out.println("[phase9-telemetry] avg frame ms=" + fmt3(result.avgFrameMs)
                + " avg update ms=" + fmt3(result.avgUpdateMs));
        System.out.println("[phase9-telemetry] ai phases ms maint=" + fmt3(result.aiPhaseCostMs.getOrDefault("maintenance", 0.0))
                + " fleet=" + fmt3(result.aiPhaseCostMs.getOrDefault("fleetState", 0.0))
                + " util=" + fmt3(result.aiPhaseCostMs.getOrDefault("shipUtility", 0.0))
                + " combat=" + fmt3(result.aiPhaseCostMs.getOrDefault("shipCombat", 0.0))
                + " avoid=" + fmt3(result.aiPhaseCostMs.getOrDefault("avoidance", 0.0))
                + " sync=" + fmt3(result.aiPhaseCostMs.getOrDefault("formationSync", 0.0))
                + " bounds=" + fmt3(result.aiPhaseCostMs.getOrDefault("bounds", 0.0)));
        System.out.println("[phase9-telemetry] combat detail ms target=" + fmt3(result.aiPhaseCostMs.getOrDefault("shipCombatTarget", 0.0))
                + " fight=" + fmt3(result.aiPhaseCostMs.getOrDefault("shipCombatFight", 0.0))
                + " fire=" + fmt3(result.aiPhaseCostMs.getOrDefault("shipCombatFire", 0.0)));

        boolean pass = result.roomHitEvents > 0
                && !result.roomHitDistribution.isEmpty()
                && (AudioSystem.voiceEventMatrix().isEmpty() || result.voiceDispatchCount > 0)
                && result.hazardIgnitions > 0;
        if (!pass && strict) {
            System.out.println("[phase9-telemetry] checks: FAIL");
            System.exit(2);
        }
        System.out.println("[phase9-telemetry] checks: " + (pass ? "PASS" : "WARN"));
    }

    private static TelemetryResult runTelemetry(long seed, int seconds) {
        GameConfig cfg = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(cfg);
        SpawnSystem.initWorld(ctx);

        if (ctx.player != null) {
            ctx.player.hpMax = Math.max(520, ctx.player.hpMax);
            ctx.player.hp = ctx.player.hpMax;
            ctx.player.shieldMax = Math.max(260.0, ctx.player.shieldMax);
            ctx.player.shield = ctx.player.shieldMax;
            ctx.player.shieldActive = true;
            ctx.player.shieldRegen = Math.max(6.0, ctx.player.shieldRegen);
        }

        int ticks = seconds * 60;
        long startNs = System.nanoTime();

        int damageEventCursor = 0;
        Map<String, Integer> roomHitDistribution = new LinkedHashMap<>();
        EnumMap<Ship.InternalSystem, Double> subsystemFailureSec = new EnumMap<>(Ship.InternalSystem.class);
        EnumMap<ShipRoomLayout.RoomId, Double> lastFire = new EnumMap<>(ShipRoomLayout.RoomId.class);
        int hazardIgnitions = 0;
        int hazardSuppressions = 0;
        int roomHitEvents = 0;

        long physicsNs = 0L;
        long aiNs = 0L;
        long carrierNs = 0L;
        long economyNs = 0L;
        long campaignNs = 0L;
        long lastStandNs = 0L;
        long eventNs = 0L;
        long audioNs = 0L;
        long uiNs = 0L;
        long tickNs = 0L;
        long aiMaintenanceNs = 0L;
        long aiFleetStateNs = 0L;
        long aiShipUtilityNs = 0L;
        long aiShipCombatNs = 0L;
        long aiShipCombatTargetNs = 0L;
        long aiShipCombatFightNs = 0L;
        long aiShipCombatFireNs = 0L;
        long aiAvoidanceNs = 0L;
        long aiFormationSyncNs = 0L;
        long aiBoundsNs = 0L;

        for (int tick = 0; tick < ticks; tick++) {
            long tickStart = System.nanoTime();
            stimulate(ctx, tick);

            long t0 = System.nanoTime();
            PhysicsSystem.update(ctx, GameContext.DT);
            physicsNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            AISystem.update(ctx, GameContext.DT);
            aiNs += System.nanoTime() - t0;
            aiMaintenanceNs += (long) Math.round(ctx.perf.aiMaintenanceMs * 1_000_000.0);
            aiFleetStateNs += (long) Math.round(ctx.perf.aiFleetStateMs * 1_000_000.0);
            aiShipUtilityNs += (long) Math.round(ctx.perf.aiShipUtilityMs * 1_000_000.0);
            aiShipCombatNs += (long) Math.round(ctx.perf.aiShipCombatMs * 1_000_000.0);
            aiShipCombatTargetNs += (long) Math.round(ctx.perf.aiShipCombatTargetMs * 1_000_000.0);
            aiShipCombatFightNs += (long) Math.round(ctx.perf.aiShipCombatFightMs * 1_000_000.0);
            aiShipCombatFireNs += (long) Math.round(ctx.perf.aiShipCombatFireMs * 1_000_000.0);
            aiAvoidanceNs += (long) Math.round(ctx.perf.aiAvoidanceMs * 1_000_000.0);
            aiFormationSyncNs += (long) Math.round(ctx.perf.aiFormationSyncMs * 1_000_000.0);
            aiBoundsNs += (long) Math.round(ctx.perf.aiBoundsMs * 1_000_000.0);

            t0 = System.nanoTime();
            CarrierSystem.update(ctx, GameContext.DT);
            carrierNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            EconomySystem.update(ctx, GameContext.DT);
            economyNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            CampaignSystem.update(ctx, GameContext.DT);
            campaignNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            LastStandSystem.update(ctx, GameContext.DT);
            lastStandNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            EventSystem.update(ctx, GameContext.DT);
            eventNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            AudioSystem.update(ctx, GameContext.DT);
            audioNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            UISystem.updatePings(ctx, GameContext.DT);
            uiNs += System.nanoTime() - t0;

            tickNs += System.nanoTime() - tickStart;

            keepPlayerAlive(ctx, tick);

            if (ctx.player != null) {
                double elapsedSec = tick / 60.0;
                for (Ship.InternalSystem system : Ship.InternalSystem.values()) {
                    if (subsystemFailureSec.containsKey(system)) continue;
                    if (ctx.player.isSystemDestroyed(system)) {
                        subsystemFailureSec.put(system, elapsedSec);
                    }
                }

                List<Ship.RoomStatus> rooms = ctx.player.roomStatusSnapshot();
                for (Ship.RoomStatus rs : rooms) {
                    if (rs == null || rs.roomId == null) continue;
                    double prev = lastFire.getOrDefault(rs.roomId, 0.0);
                    double curr = Math.max(0.0, rs.fireIntensity);
                    if (prev <= 0.05 && curr > 0.05) hazardIgnitions++;
                    if (prev > 0.25 && curr < prev - 0.20) hazardSuppressions++;
                    lastFire.put(rs.roomId, curr);
                }
            }

            while (damageEventCursor < ctx.damageEvents.size()) {
                DamageEvent ev = ctx.damageEvents.get(damageEventCursor++);
                if (ev == null || ev.roomDamageResult == null) continue;
                String room = ev.roomDamageResult.roomId;
                if (room == null || room.isBlank()) continue;
                roomHitEvents++;
                roomHitDistribution.put(room, roomHitDistribution.getOrDefault(room, 0) + 1);
            }
        }

        long elapsedNs = Math.max(1L, System.nanoTime() - startNs);
        AudioSystem.VoiceTelemetrySnapshot voiceT = AudioSystem.voiceTelemetry(ctx);

        TelemetryResult out = new TelemetryResult();
        out.seed = seed;
        out.seconds = seconds;
        out.roomHitEvents = roomHitEvents;
        out.roomHitDistribution.putAll(roomHitDistribution);
        out.hazardIgnitions = hazardIgnitions;
        out.hazardSuppressions = hazardSuppressions;
        out.voiceDispatchCount = voiceT.dispatchCount();
        out.voiceDropCount = voiceT.dropCount();
        out.voiceDispatchByEvent.putAll(voiceT.dispatchByEvent());
        out.voiceDropsByReason.putAll(voiceT.dropsByReason());
        for (Map.Entry<Ship.InternalSystem, Double> e : subsystemFailureSec.entrySet()) {
            out.timeToSubsystemFailureSec.put(e.getKey().name(), e.getValue());
        }

        out.avgFrameMs = tickNs / (double) ticks / 1_000_000.0;
        out.avgUpdateMs = (physicsNs + aiNs + carrierNs + economyNs + campaignNs + lastStandNs + eventNs + audioNs + uiNs)
                / (double) ticks / 1_000_000.0;

        out.systemCostMs.put("physics", physicsNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("ai", aiNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("carrier", carrierNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("economy", economyNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("campaign", campaignNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("lastStand", lastStandNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("event", eventNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("audio", audioNs / (double) ticks / 1_000_000.0);
        out.systemCostMs.put("ui", uiNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("maintenance", aiMaintenanceNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("fleetState", aiFleetStateNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("shipUtility", aiShipUtilityNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("shipCombat", aiShipCombatNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("shipCombatTarget", aiShipCombatTargetNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("shipCombatFight", aiShipCombatFightNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("shipCombatFire", aiShipCombatFireNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("avoidance", aiAvoidanceNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("formationSync", aiFormationSyncNs / (double) ticks / 1_000_000.0);
        out.aiPhaseCostMs.put("bounds", aiBoundsNs / (double) ticks / 1_000_000.0);
        out.runtimeSec = elapsedNs / 1_000_000_000.0;
        return out;
    }

    private static void stimulate(GameContext ctx, int tick) {
        if (ctx == null) return;
        if (tick % 420 == 0) {
            UISystem.setHelmMode(ctx, ((tick / 420) & 1) == 0
                    ? GameContext.HelmMode.INTERCEPT
                    : GameContext.HelmMode.EVASIVE);
        }
        if (tick % 540 == 0) {
            UISystem.applyCaptainDirective(ctx, ((tick / 540) & 1) == 0
                    ? GameContext.CaptainDirective.ATTACK
                    : GameContext.CaptainDirective.DEFEND);
        }
        if (tick % 360 == 0) {
            UISystem.setEngineeringMode(ctx, ((tick / 360) & 1) == 0
                    ? GameContext.EngineeringMode.DAMAGE_CONTROL
                    : GameContext.EngineeringMode.BALANCED);
        }
        if (tick % 900 == 0 && ctx.player != null) {
            SpawnSystem.spawnEnemyGroup(ctx, ctx.player.x + 560.0, ctx.player.y + 240.0);
            SpawnSystem.spawnAllyGroup(ctx, ctx.player.x - 560.0, ctx.player.y - 240.0);
        }
        stimulateHazardTelemetry(ctx, tick);
    }

    private static void stimulateHazardTelemetry(GameContext ctx, int tick) {
        if (ctx == null || ctx.player == null) return;
        Ship player = ctx.player;
        if (tick >= 360 || tick % 18 != 0) return;

        ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(player.role, ShipRoomLayout.RoomId.REACTOR);
        if (reactor == null) return;

        double wx = player.x + avg(reactor.xs) * player.radius;
        double wy = player.y + avg(reactor.ys) * player.radius;
        player.shield = 0.0;
        player.shieldActive = false;
        player.crewOrder = Ship.CrewOrder.BALANCED;
        player.takeDamage(4, wx, wy, 0.0, 0.0);
    }

    private static void keepPlayerAlive(GameContext ctx, int tick) {
        if (ctx == null || ctx.player == null) return;
        Ship p = ctx.player;
        if (!p.alive || p.dying || p.hp <= 0) {
            p.alive = true;
            p.dying = false;
            p.hp = Math.max(1, p.hpMax);
        }
        if (p.hp < p.hpMax * 0.34) {
            p.hp = (int) Math.round(p.hpMax * 0.82);
        }
        if (p.shieldMax > 0.0 && p.shield < p.shieldMax * 0.12) {
            p.shield = p.shieldMax * 0.74;
        }
        if (tick % 500 == 180) {
            p.takeDamage(5, p.x + p.radius, p.y, 0.0, 0.0);
        }
        if (tick % 480 == 200) {
            ShipRoomLayout.RoomId hotspot = p.hottestFireRoom();
            if (hotspot != null) {
                p.suppressFireInRoom(hotspot);
            }
        }
    }

    private static String toJson(TelemetryResult r) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\n");
        sb.append("  \"seed\": ").append(r.seed).append(",\n");
        sb.append("  \"seconds\": ").append(r.seconds).append(",\n");
        sb.append("  \"runtimeSec\": ").append(fmt6(r.runtimeSec)).append(",\n");
        sb.append("  \"roomHitEvents\": ").append(r.roomHitEvents).append(",\n");
        sb.append("  \"roomHitDistribution\": ").append(mapToJson(r.roomHitDistribution)).append(",\n");
        sb.append("  \"timeToSubsystemFailureSec\": ").append(mapToJsonDouble(r.timeToSubsystemFailureSec)).append(",\n");
        sb.append("  \"hazard\": {\n");
        sb.append("    \"ignitions\": ").append(r.hazardIgnitions).append(",\n");
        sb.append("    \"suppressions\": ").append(r.hazardSuppressions).append("\n");
        sb.append("  },\n");
        sb.append("  \"voice\": {\n");
        sb.append("    \"dispatchCount\": ").append(r.voiceDispatchCount).append(",\n");
        sb.append("    \"dropCount\": ").append(r.voiceDropCount).append(",\n");
        sb.append("    \"dispatchByEvent\": ").append(mapToJson(r.voiceDispatchByEvent)).append(",\n");
        sb.append("    \"dropsByReason\": ").append(mapToJson(r.voiceDropsByReason)).append("\n");
        sb.append("  },\n");
        sb.append("  \"performance\": {\n");
        sb.append("    \"avgFrameMs\": ").append(fmt6(r.avgFrameMs)).append(",\n");
        sb.append("    \"avgUpdateMs\": ").append(fmt6(r.avgUpdateMs)).append(",\n");
        sb.append("    \"systemCostMs\": ").append(mapToJsonDouble(r.systemCostMs)).append(",\n");
        sb.append("    \"aiPhaseCostMs\": ").append(mapToJsonDouble(r.aiPhaseCostMs)).append("\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String mapToJson(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(q(e.getKey())).append(": ").append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private static String mapToJsonDouble(Map<String, Double> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(q(e.getKey())).append(": ").append(fmt6(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String q(String raw) {
        if (raw == null) return "\"\"";
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String fmt3(double v) {
        return String.format(Locale.US, "%.3f", v);
    }

    private static String fmt6(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static double avg(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
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

    private static final class TelemetryResult {
        long seed;
        int seconds;
        double runtimeSec;
        int roomHitEvents;
        int hazardIgnitions;
        int hazardSuppressions;
        int voiceDispatchCount;
        int voiceDropCount;
        double avgFrameMs;
        double avgUpdateMs;
        final Map<String, Integer> roomHitDistribution = new LinkedHashMap<>();
        final Map<String, Double> timeToSubsystemFailureSec = new LinkedHashMap<>();
        final Map<String, Integer> voiceDispatchByEvent = new LinkedHashMap<>();
        final Map<String, Integer> voiceDropsByReason = new LinkedHashMap<>();
        final Map<String, Double> systemCostMs = new LinkedHashMap<>();
        final Map<String, Double> aiPhaseCostMs = new LinkedHashMap<>();
    }
}
