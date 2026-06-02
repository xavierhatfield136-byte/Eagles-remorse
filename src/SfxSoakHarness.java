import app.config.GameConfig;
import app.config.GameMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs combat simulation and asserts SFX events resolve to real assets.
 */
public final class SfxSoakHarness {
    private SfxSoakHarness() {}

    public static void main(String[] args) {
        int seconds = 180;
        long seed = 90909L;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            if (arg.startsWith("--seconds=")) {
                seconds = Math.max(30, parseInt(arg.substring("--seconds=".length()), 180));
            } else if (arg.startsWith("--seed=")) {
                seed = parseLong(arg.substring("--seed=".length()), seed);
            }
        }

        GameConfig cfg = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(cfg);
        SpawnSystem.initWorld(ctx);
        AudioSystem.setTelemetryOnly(true);
        try {
            int ticks = seconds * 60;
            for (int tick = 0; tick < ticks; tick++) {
                PhysicsSystem.update(ctx, GameContext.DT);
                AISystem.update(ctx, GameContext.DT);
                CarrierSystem.update(ctx, GameContext.DT);
                EconomySystem.update(ctx, GameContext.DT);
                CampaignSystem.update(ctx, GameContext.DT);
                LastStandSystem.update(ctx, GameContext.DT);
                EventSystem.update(ctx, GameContext.DT);
                AudioSystem.update(ctx, GameContext.DT);
                UISystem.updatePings(ctx, GameContext.DT);
                nudgeCommands(ctx, tick);
            }
        } finally {
            AudioSystem.setTelemetryOnly(false);
        }

        List<AudioEvent> sfx = new ArrayList<>();
        List<AudioEvent> missing = new ArrayList<>();
        for (AudioEvent ev : ctx.audioEvents) {
            if (ev == null || ev.eventId == null) continue;
            if (!ev.eventId.startsWith("sfx.")) continue;
            sfx.add(ev);
            if ("sfx_missing".equals(ev.duckingClass)) missing.add(ev);
        }
        sfx.sort(Comparator.comparingLong(a -> a.timestamp));

        System.out.println("[sfx-soak] seconds=" + seconds + " sfxEvents=" + sfx.size() + " missing=" + missing.size());
        if (!missing.isEmpty()) {
            System.out.println("[sfx-soak] missing event samples:");
            int limit = Math.min(10, missing.size());
            for (int i = 0; i < limit; i++) {
                AudioEvent ev = missing.get(i);
                System.out.println(" - " + ev.eventId + " key=" + ev.cooldownKey);
            }
            System.exit(2);
        }
    }

    private static void nudgeCommands(GameContext ctx, int tick) {
        if (ctx == null) return;
        if (tick % 620 == 0) {
            GameContext.CaptainDirective[] d = {
                    GameContext.CaptainDirective.ATTACK,
                    GameContext.CaptainDirective.DEFEND,
                    GameContext.CaptainDirective.ESCORT,
                    GameContext.CaptainDirective.REPAIR
            };
            UISystem.applyCaptainDirective(ctx, d[(tick / 620) % d.length]);
        }
        if (tick % 710 == 0) {
            UISystem.setHelmMode(ctx, ((tick / 710) % 2 == 0)
                    ? GameContext.HelmMode.INTERCEPT
                    : GameContext.HelmMode.EVASIVE);
        }
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
