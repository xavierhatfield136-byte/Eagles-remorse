import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 5-minute (default) voice bus soak to validate cooldown spam control and priority dominance.
 */
public final class VoiceSoakHarness {
    private VoiceSoakHarness() {}

    public static void main(String[] args) {
        int seconds = 300;
        long seed = 1337L;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            if (arg.startsWith("--seconds=")) {
                seconds = Math.max(30, parseInt(arg.substring("--seconds=".length()), 300));
            } else if (arg.startsWith("--seed=")) {
                seed = parseLong(arg.substring("--seed=".length()), seed);
            }
        }

        GameConfig cfg = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(cfg);
        SpawnSystem.initWorld(ctx);

        if (ctx.player != null) {
            ctx.player.hpMax = Math.max(ctx.player.hpMax, 900);
            ctx.player.hp = ctx.player.hpMax;
            ctx.player.shieldMax = Math.max(ctx.player.shieldMax, 400.0);
            ctx.player.shield = ctx.player.shieldMax;
            ctx.player.shieldActive = true;
            ctx.player.shieldRegen = Math.max(ctx.player.shieldRegen, 8.0);
        }

        AudioSystem.setTelemetryOnly(true);
        try {
            runSimulation(ctx, seconds);
        } finally {
            AudioSystem.setTelemetryOnly(false);
        }

        List<AudioEvent> voice = new ArrayList<>();
        for (AudioEvent ev : ctx.audioEvents) {
            if (ev == null) continue;
            if (!"voice".equals(ev.duckingClass)) continue;
            voice.add(ev);
        }
        voice.sort(Comparator.comparingLong(a -> a.timestamp));

        int cooldownViolations = countCooldownViolations(voice);
        int priorityViolations = countPriorityDominanceViolations(voice);

        System.out.println("[voice-soak] seconds=" + seconds);
        System.out.println("[voice-soak] voiceEvents=" + voice.size());
        System.out.println("[voice-soak] cooldownViolations=" + cooldownViolations);
        System.out.println("[voice-soak] priorityViolations=" + priorityViolations);

        if (voice.isEmpty() || cooldownViolations > 0 || priorityViolations > 0) {
            System.exit(2);
        }
    }

    private static void runSimulation(GameContext ctx, int seconds) {
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

        if (tick % 510 == 0) {
            GameContext.CaptainDirective[] directives = {
                    GameContext.CaptainDirective.ATTACK,
                    GameContext.CaptainDirective.DEFEND,
                    GameContext.CaptainDirective.ESCORT,
                    GameContext.CaptainDirective.REPAIR,
                    GameContext.CaptainDirective.RTB,
                    GameContext.CaptainDirective.BALANCED
            };
            UISystem.applyCaptainDirective(ctx, directives[(tick / 510) % directives.length]);
        }

        if (tick % 360 == 0) {
            ctx.scienceJamming = !ctx.scienceJamming;
        }

        if (tick % 540 == 0) {
            if (((tick / 540) & 1) == 0) {
                UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.DAMAGE_CONTROL);
            } else {
                UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.BALANCED);
            }
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
        if (p.hp < p.hpMax * 0.4) {
            p.hp = (int) Math.round(p.hpMax * 0.85);
        }
        if (p.shieldMax > 0.0 && p.shield < p.shieldMax * 0.15) {
            p.shield = p.shieldMax * 0.78;
        }

        // Drive some shield/reactor distress events periodically.
        if (tick % 600 == 250 && p.shieldMax > 0.0) {
            p.shield = Math.min(p.shield, p.shieldMax * 0.18);
        }
        if (tick % 720 == 360) {
            p.takeDamage(4, p.x + p.radius, p.y);
        }
    }

    private static int countCooldownViolations(List<AudioEvent> voiceEvents) {
        int violations = 0;
        Map<String, Long> lastByKey = new HashMap<>();
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

    private static int countPriorityDominanceViolations(List<AudioEvent> voiceEvents) {
        int violations = 0;
        for (int i = 0; i < voiceEvents.size(); i++) {
            AudioEvent critical = voiceEvents.get(i);
            if (critical == null || critical.priority < 3) continue;
            long end = critical.timestamp + 900_000_000L;
            for (int j = i + 1; j < voiceEvents.size(); j++) {
                AudioEvent later = voiceEvents.get(j);
                if (later.timestamp > end) break;
                if (later.priority < 3) violations++;
            }
        }
        return violations;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
