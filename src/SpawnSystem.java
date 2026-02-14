import java.util.Random;

public final class SpawnSystem {
    private SpawnSystem(){}

    public static void initWorld(GameContext ctx) {
        if (ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) {
            initFourTeamDomination(ctx);
            return;
        }

        ctx.teamBases.clear();

        // Player
        ctx.player = new Player(ShipRole.FRIGATE, ctx.WORLD_W / 2.0, ctx.WORLD_H / 2.0);
        ctx.ships.add(ctx.player);

        // Bases
        ctx.allyBase = new FleetShip(ShipRole.BASE, Faction.ALLY, ctx.player.x - 700, ctx.player.y + 380);
        ctx.enemyBase = new FleetShip(ShipRole.BASE, Faction.ENEMY, ctx.player.x + 1400, ctx.player.y - 900);
        ctx.ships.add(ctx.allyBase);
        ctx.ships.add(ctx.enemyBase);
        ctx.teamBases.put(Faction.ALLY, ctx.allyBase);
        ctx.teamBases.put(Faction.ENEMY, ctx.enemyBase);

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
            for (Ship base : ctx.teamBases.values()) DoctrineRegistry.applyToShip(base);
            for (Ship s : ctx.ships) DoctrineRegistry.applyToShip(s);
        } catch (Throwable ignored) {
            // If DoctrineRegistry isn't in project, ignore.
        }
    }

    public static Ship spawnAlly(GameContext ctx, ShipRole role, double x, double y) {
        Ship s = spawnTeamShip(ctx, role, Faction.ALLY, x, y);
        return s;
    }

    public static Ship spawnEnemy(GameContext ctx, ShipRole role, double x, double y) {
        Ship s = spawnTeamShip(ctx, role, Faction.ENEMY, x, y);
        return s;
    }

    public static Ship spawnTeamShip(GameContext ctx, ShipRole role, Faction faction, double x, double y) {
        double sx = GameMath.clamp(x, 20, ctx.WORLD_W - 20);
        double sy = GameMath.clamp(y, 20, ctx.WORLD_H - 20);
        Ship s = new FleetShip(role, faction, sx, sy);
        ctx.ships.add(s);
        try { DoctrineRegistry.applyToShip(s); } catch (Throwable ignored) {}
        if (role == ShipRole.MINER) logMinerSpawn(s);
        return s;
    }

    private static void logMinerSpawn(Ship s) {
        if (s == null) return;
        System.out.println("MINER SPAWN #" + s.id +
                " role=" + s.role +
                " faction=" + s.faction +
                " pos=(" + (int) Math.round(s.x) + "," + (int) Math.round(s.y) + ")" +
                " speed=" + s.desiredSpeed +
                " miningRate=" + s.miningRate +
                " miningRange=" + s.miningRange +
                " cargoMax=" + s.cargoMax);
    }

    public static void spawnEnemyGroup(GameContext ctx, double x, double y) {
        spawnEnemy(ctx, ShipRole.PATROL, x + 0, y + 0);
        spawnEnemy(ctx, ShipRole.PICKET, x + 70, y + 50);
        spawnEnemy(ctx, ShipRole.FRIGATE, x - 90, y + 70);
        if (ctx.rng.nextDouble() < 0.35) spawnEnemy(ctx, ShipRole.MISSILE_BOAT, x + 110, y - 80);
    }

    public static void spawnAllyGroup(GameContext ctx, double x, double y) {
        spawnAlly(ctx, ShipRole.PATROL, x + 0, y + 0);
        spawnAlly(ctx, ShipRole.PICKET, x + 70, y + 50);
        spawnAlly(ctx, ShipRole.FRIGATE, x - 90, y + 70);
        if (ctx.rng.nextDouble() < 0.35) spawnAlly(ctx, ShipRole.MISSILE_BOAT, x + 110, y - 80);
    }

    public static void spawnAsteroidField(GameContext ctx) {
        ctx.asteroids.clear();
        int n = (ctx.WORLD_W <= 6000) ? 120 : (ctx.WORLD_W <= 12000 ? 220 : 380);
        Random rng = ctx.rng;
        for (int i = 0; i < n; i++) {
            double x = 0;
            double y = 0;
            boolean ok = false;
            for (int tries = 0; tries < 25; tries++) {
                x = 200 + rng.nextDouble() * (ctx.WORLD_W - 400);
                y = 200 + rng.nextDouble() * (ctx.WORLD_H - 400);
                if (isClearOfBases(ctx, x, y, 220)) { ok = true; break; }
            }
            if (!ok) continue;
            double ore = 200 + rng.nextDouble() * 800;
            double r = 18 + rng.nextDouble() * 45;
            // Asteroid constructor varies across your versions; keep as int ore to match common signature.
            ctx.asteroids.add(new Asteroid(x, y, r, (int)Math.round(ore)));
        }
    }

    private static boolean isClearOfBases(GameContext ctx, double x, double y, double minDist) {
        double minD2 = minDist * minDist;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < minD2) return false;
        }
        return true;
    }

    private static void initFourTeamDomination(GameContext ctx) {
        ctx.teamBases.clear();

        double margin = Math.max(200.0, Math.min(ctx.WORLD_W, ctx.WORLD_H) * 0.05);

        double[][] corners = new double[][]{
                {margin, margin},
                {ctx.WORLD_W - margin, margin},
                {margin, ctx.WORLD_H - margin},
                {ctx.WORLD_W - margin, ctx.WORLD_H - margin}
        };

        Faction[] teams = Faction.fourTeamFactions();
        for (int i = 0; i < teams.length; i++) {
            Faction team = teams[i];
            double bx = corners[i][0];
            double by = corners[i][1];

            Ship base = new FleetShip(ShipRole.BASE, team, bx, by);
            clampBaseToBounds(ctx, base);

            ctx.ships.add(base);
            ctx.teamBases.put(team, base);
            ctx.baseUpgrades.put(base, new BaseUpgrades());
        }

        ctx.allyBase = ctx.teamBases.get(Faction.ALLY);
        ctx.enemyBase = ctx.teamBases.get(Faction.ENEMY);

        // Player spawns near Team A base (slight offset toward center).
        Ship aBase = ctx.teamBases.get(Faction.ALLY);
        double px = aBase.x + (ctx.WORLD_W * 0.05);
        double py = aBase.y + (ctx.WORLD_H * 0.05);
        px = GameMath.clamp(px, 40, ctx.WORLD_W - 40);
        py = GameMath.clamp(py, 40, ctx.WORLD_H - 40);
        ctx.player = new Player(ShipRole.FRIGATE, px, py);
        ctx.ships.add(ctx.player);

        // Resource field
        spawnAsteroidField(ctx);

        // Starting ships near each base
        for (Faction team : teams) {
            Ship base = ctx.teamBases.get(team);
            if (base == null) continue;
            spawnTeamStart(ctx, team, base);
        }

        logBaseSpawns(ctx, teams);

        // Apply doctrine tuning (Step 5B/5C) if present
        tryApplyDoctrine(ctx);
    }

    private static void clampBaseToBounds(GameContext ctx, Ship base) {
        if (base == null) return;
        double r = Math.max(1.0, base.radius);
        double pad = 12.0;
        base.x = GameMath.clamp(base.x, r + pad, ctx.WORLD_W - r - pad);
        base.y = GameMath.clamp(base.y, r + pad, ctx.WORLD_H - r - pad);
    }

    private static void spawnTeamStart(GameContext ctx, Faction team, Ship base) {
        double ox = (ctx.rng.nextDouble() - 0.5) * 180.0;
        double oy = (ctx.rng.nextDouble() - 0.5) * 180.0;
        spawnTeamShip(ctx, ShipRole.PATROL, team, base.x + ox, base.y + oy);

        ox = (ctx.rng.nextDouble() - 0.5) * 200.0;
        oy = (ctx.rng.nextDouble() - 0.5) * 200.0;
        spawnTeamShip(ctx, ShipRole.PICKET, team, base.x + ox, base.y + oy);

        ox = (ctx.rng.nextDouble() - 0.5) * 220.0;
        oy = (ctx.rng.nextDouble() - 0.5) * 220.0;
        spawnTeamShip(ctx, ShipRole.FRIGATE, team, base.x + ox, base.y + oy);
    }

    private static void logBaseSpawns(GameContext ctx, Faction[] teams) {
        for (Faction team : teams) {
            Ship base = ctx.teamBases.get(team);
            if (base == null) continue;
            System.out.println("BASE SPAWN team=" + team.teamName() +
                    " teamId=" + team.teamId() +
                    " faction=" + team.name() +
                    " id=" + base.id +
                    " pos=(" + (int) Math.round(base.x) + "," + (int) Math.round(base.y) + ")");
        }
    }
}
