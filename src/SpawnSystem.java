import java.util.Random;

public final class SpawnSystem {
    private SpawnSystem(){}

    public static void initWorld(GameContext ctx) {
        // Player
        ctx.player = new Player(ShipRole.FRIGATE, ctx.WORLD_W / 2.0, ctx.WORLD_H / 2.0);
        ctx.ships.add(ctx.player);

        // Bases
        ctx.allyBase = new FleetShip(ShipRole.BASE, Faction.ALLY, ctx.player.x - 700, ctx.player.y + 380);
        ctx.enemyBase = new FleetShip(ShipRole.BASE, Faction.ENEMY, ctx.player.x + 1400, ctx.player.y - 900);
        ctx.ships.add(ctx.allyBase);
        ctx.ships.add(ctx.enemyBase);

        ctx.baseUpgrades.put(ctx.allyBase, new BaseUpgrades());
        ctx.baseUpgrades.put(ctx.enemyBase, new BaseUpgrades());

        // Resource field
        spawnAsteroidField(ctx);

        // Resource Rush gets miners early
        if (ctx.config.mode == GameMode.RESOURCE_RUSH) {
            spawnAlly(ctx, ShipRole.MINER, ctx.allyBase.x - 140, ctx.allyBase.y + 120);
            spawnAlly(ctx, ShipRole.MINER, ctx.allyBase.x - 190, ctx.allyBase.y - 40);
            spawnEnemy(ctx, ShipRole.MINER, ctx.enemyBase.x + 140, ctx.enemyBase.y - 120);
            spawnEnemy(ctx, ShipRole.MINER, ctx.enemyBase.x + 190, ctx.enemyBase.y + 40);
            ctx.resourceGoal = 10000;
        }

        // Starting escort + enemies
        spawnAlly(ctx, ShipRole.FRIGATE, ctx.player.x - 120, ctx.player.y + 90);
        spawnAlly(ctx, ShipRole.CIWS_CORVETTE, ctx.player.x - 170, ctx.player.y - 40);
        spawnEnemyGroup(ctx, ctx.player.x + 600, ctx.player.y - 450);

        // Apply doctrine tuning (Step 5B/5C) if present
        tryApplyDoctrine(ctx);
    }

    private static void tryApplyDoctrine(GameContext ctx) {
        try {
            DoctrineRegistry.applyToShip(ctx.player);
            DoctrineRegistry.applyToShip(ctx.allyBase);
            DoctrineRegistry.applyToShip(ctx.enemyBase);
            for (Ship s : ctx.ships) DoctrineRegistry.applyToShip(s);
        } catch (Throwable ignored) {
            // If DoctrineRegistry isn't in project, ignore.
        }
    }

    public static Ship spawnAlly(GameContext ctx, ShipRole role, double x, double y) {
        Ship s = new FleetShip(role, Faction.ALLY, x, y);
        ctx.ships.add(s);
        try { DoctrineRegistry.applyToShip(s); } catch (Throwable ignored) {}
        return s;
    }

    public static Ship spawnEnemy(GameContext ctx, ShipRole role, double x, double y) {
        Ship s = new FleetShip(role, Faction.ENEMY, x, y);
        ctx.ships.add(s);
        try { DoctrineRegistry.applyToShip(s); } catch (Throwable ignored) {}
        return s;
    }

    public static void spawnEnemyGroup(GameContext ctx, double x, double y) {
        spawnEnemy(ctx, ShipRole.PATROL, x + 0, y + 0);
        spawnEnemy(ctx, ShipRole.PICKET, x + 70, y + 50);
        spawnEnemy(ctx, ShipRole.FRIGATE, x - 90, y + 70);
        if (ctx.rng.nextDouble() < 0.35) spawnEnemy(ctx, ShipRole.MISSILE_BOAT, x + 110, y - 80);
    }

    public static void spawnAsteroidField(GameContext ctx) {
        ctx.asteroids.clear();
        int n = (ctx.WORLD_W <= 6000) ? 120 : (ctx.WORLD_W <= 12000 ? 220 : 380);
        Random rng = ctx.rng;
        for (int i = 0; i < n; i++) {
            double x = 200 + rng.nextDouble() * (ctx.WORLD_W - 400);
            double y = 200 + rng.nextDouble() * (ctx.WORLD_H - 400);
            double ore = 200 + rng.nextDouble() * 800;
            double r = 18 + rng.nextDouble() * 45;
            // Asteroid constructor varies across your versions; keep as int ore to match common signature.
            ctx.asteroids.add(new Asteroid(x, y, r, (int)Math.round(ore)));
        }
    }
}
