/**
 * Survival mode loop for LAST_STAND.
 *
 * Rules:
 * - Survive for a fixed time limit.
 * - Lose if player flagship or allied base is destroyed.
 * - Enemy waves escalate over time.
 */
public final class LastStandSystem {
    private LastStandSystem() {}

    private static final double DEFAULT_GOAL_SECONDS = 15.0 * 60.0;
    private static final double START_WAVE_DELAY = 6.0;

    public static void init(GameContext ctx) {
        if (ctx == null || ctx.config == null) return;
        if (ctx.config.mode != GameMode.LAST_STAND) return;

        ctx.lastStandElapsed = 0.0;
        ctx.lastStandGoalSec = DEFAULT_GOAL_SECONDS;
        ctx.lastStandWaveTimer = START_WAVE_DELAY;
        ctx.lastStandWaveIndex = 0;

        EventSystem.showBanner(ctx, "LAST STAND: HOLD THE LINE", 2.5);
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null || ctx.config == null) return;
        if (ctx.config.mode != GameMode.LAST_STAND) return;
        if (ctx.gameOver) return;

        if (ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) {
            fail(ctx, "DEFEAT: FLAGSHIP LOST");
            return;
        }

        Ship allyBase = TeamSystem.getBaseForTeam(ctx, Faction.ALLY);
        if (allyBase == null || !allyBase.alive || allyBase.dying || allyBase.hp <= 0) {
            fail(ctx, "DEFEAT: BASE LOST");
            return;
        }

        ctx.lastStandElapsed += Math.max(0.0, dt);
        if (ctx.lastStandElapsed >= ctx.lastStandGoalSec) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;
            ctx.gameOverText = "VICTORY";
            EventSystem.showBanner(ctx, "LAST STAND COMPLETE", 3.0);
            return;
        }

        ctx.lastStandWaveTimer -= Math.max(0.0, dt);
        if (ctx.lastStandWaveTimer <= 0.0) {
            spawnWave(ctx, allyBase);
        }
    }

    public static String hudTitle(GameContext ctx) {
        if (!isActive(ctx)) return "";
        return "LAST STAND";
    }

    public static String hudDetail(GameContext ctx) {
        if (!isActive(ctx)) return "";
        int left = (int) Math.ceil(Math.max(0.0, ctx.lastStandGoalSec - ctx.lastStandElapsed));
        int next = (int) Math.ceil(Math.max(0.0, ctx.lastStandWaveTimer));
        return "Survive " + left + "s   Wave " + Math.max(1, ctx.lastStandWaveIndex + 1) + " incoming   Next wave in " + next + "s";
    }

    public static boolean isActive(GameContext ctx) {
        if (ctx == null || ctx.config == null) return false;
        return ctx.config.mode == GameMode.LAST_STAND;
    }

    private static void spawnWave(GameContext ctx, Ship allyBase) {
        int wave = ++ctx.lastStandWaveIndex;

        int groups = Math.min(7, 1 + wave / 3);
        for (int i = 0; i < groups; i++) {
            double[] p = pickWaveSpawn(ctx, allyBase, 930, 1320);
            SpawnSystem.spawnEnemyGroup(ctx, p[0], p[1]);
        }

        if (wave >= 3 && (wave % 3) == 0) {
            double[] p = pickWaveSpawn(ctx, allyBase, 980, 1380);
            SpawnSystem.spawnEnemy(ctx, heavyRoleForWave(wave), p[0], p[1]);
        }

        if ((wave % 4) == 0) {
            double ax = allyBase.x - 220 + ctx.rng.nextDouble() * 180.0;
            double ay = allyBase.y - 140 + ctx.rng.nextDouble() * 280.0;
            SpawnSystem.spawnAlly(ctx, ShipRole.FRIGATE, ax, ay);
            EventSystem.showBanner(ctx, "FRIENDLY REINFORCEMENTS ARRIVED", 1.8);
        } else {
            EventSystem.showBanner(ctx, "WAVE " + wave + " INBOUND", 1.3);
        }

        ctx.lastStandWaveTimer = nextWaveDelay(wave);
    }

    private static ShipRole heavyRoleForWave(int wave) {
        if (wave >= 18) return ShipRole.SUPERSHIP;
        if (wave >= 14) return ShipRole.DREADNOUGHT;
        if (wave >= 10) return ShipRole.BATTLESHIP;
        if (wave >= 7) return ShipRole.BATTLECRUISER;
        return ShipRole.CRUISER;
    }

    private static double nextWaveDelay(int wave) {
        double base = 13.0 - Math.min(7.0, wave * 0.35);
        return Math.max(5.0, base);
    }

    private static double[] pickWaveSpawn(GameContext ctx, Ship center, double minR, double maxR) {
        double a = ctx.rng.nextDouble() * Math.PI * 2.0;
        double r = minR + ctx.rng.nextDouble() * Math.max(1.0, (maxR - minR));
        double x = center.x + Math.cos(a) * r;
        double y = center.y + Math.sin(a) * r;
        x = GameMath.clamp(x, 40, ctx.WORLD_W - 40);
        y = GameMath.clamp(y, 40, ctx.WORLD_H - 40);
        return new double[]{x, y};
    }

    private static void fail(GameContext ctx, String text) {
        if (ctx == null) return;
        ctx.gameOver = true;
        ctx.state = GameState.GAME_OVER;
        ctx.gameOverText = text;
    }
}
