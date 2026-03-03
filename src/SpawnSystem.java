import java.util.Random;

public final class SpawnSystem {
    private SpawnSystem(){}
    public static final int MAX_MINERS_PER_FACTION = 4;

    public static void initWorld(GameContext ctx) {
        if (ctx.config.mode == GameMode.SHOWCASE) {
            initShowcase(ctx);
            return;
        }

        if (ctx.config.mode == GameMode.SHOOTING_RANGE) {
            initShootingRange(ctx);
            return;
        }

        if (ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) {
            initFourTeamDomination(ctx);
            return;
        }

        ctx.teamBases.clear();

        // Bases anchor to edge lanes that scale with map size.
        double[] allyBasePos = edgeBasePosition(ctx, true);
        double[] enemyBasePos = edgeBasePosition(ctx, false);
        ctx.allyBase = new FleetShip(ShipRole.BASE, Faction.ALLY, allyBasePos[0], allyBasePos[1]);
        ctx.enemyBase = new FleetShip(ShipRole.BASE, Faction.ENEMY, enemyBasePos[0], enemyBasePos[1]);
        clampBaseToBounds(ctx, ctx.allyBase);
        clampBaseToBounds(ctx, ctx.enemyBase);
        ctx.ships.add(ctx.allyBase);
        ctx.ships.add(ctx.enemyBase);
        ctx.teamBases.put(Faction.ALLY, ctx.allyBase);
        ctx.teamBases.put(Faction.ENEMY, ctx.enemyBase);

        ctx.baseUpgrades.put(ctx.allyBase, new BaseUpgrades());
        ctx.baseUpgrades.put(ctx.enemyBase, new BaseUpgrades());

        // Player spawns near ally base with an inward offset.
        double px = GameMath.clamp(ctx.allyBase.x + Math.max(220.0, ctx.WORLD_W * 0.08), 40.0, ctx.WORLD_W - 40.0);
        double py = GameMath.clamp(ctx.allyBase.y - Math.max(120.0, ctx.WORLD_H * 0.04), 40.0, ctx.WORLD_H - 40.0);
        ctx.player = new Player(ShipRole.FRIGATE, px, py);
        ctx.ships.add(ctx.player);

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

        if (ctx.config.mode == GameMode.LAST_STAND) {
            // Last Stand starts with stronger allied defense and staged incoming waves.
            spawnAlly(ctx, ShipRole.PICKET, ctx.allyBase.x - 180, ctx.allyBase.y - 80);
            spawnAlly(ctx, ShipRole.FRIGATE, ctx.allyBase.x - 210, ctx.allyBase.y + 110);
            spawnAlly(ctx, ShipRole.MISSILE_BOAT, ctx.allyBase.x - 250, ctx.allyBase.y + 10);
            LastStandSystem.init(ctx);
        } else if (ctx.config.mode == GameMode.RESOURCE_RUSH) {
            // Keep opening pressure symmetric in Resource Rush.
            spawnEnemyGroup(ctx, ctx.enemyBase.x - 420, ctx.enemyBase.y + 280);
            spawnAllyGroup(ctx, ctx.allyBase.x + 420, ctx.allyBase.y - 280);
        } else {
            spawnEnemyGroup(ctx, ctx.enemyBase.x - 520, ctx.enemyBase.y + 320);
        }

        // Apply doctrine tuning (Step 5B/5C) if present
        tryApplyDoctrine(ctx);

        // Campaign scaffolding
        CampaignSystem.init(ctx);
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
        if (role == ShipRole.MINER) {
            if (TeamSystem.countAliveMiners(ctx, faction) >= MAX_MINERS_PER_FACTION) {
                return null;
            }
        }

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
        if (ctx.rng.nextDouble() < 0.08) spawnEnemy(ctx, ShipRole.SUPERSHIP, x + 180, y - 40);
    }

    public static void spawnAllyGroup(GameContext ctx, double x, double y) {
        spawnAlly(ctx, ShipRole.PATROL, x + 0, y + 0);
        spawnAlly(ctx, ShipRole.PICKET, x + 70, y + 50);
        spawnAlly(ctx, ShipRole.FRIGATE, x - 90, y + 70);
        if (ctx.rng.nextDouble() < 0.35) spawnAlly(ctx, ShipRole.MISSILE_BOAT, x + 110, y - 80);
        if (ctx.rng.nextDouble() < 0.08) spawnAlly(ctx, ShipRole.SUPERSHIP, x - 180, y + 40);
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

    private static double[] edgeBasePosition(GameContext ctx, boolean ally) {
        if (ctx == null) return new double[]{0.0, 0.0};
        double minDim = Math.min(ctx.WORLD_W, ctx.WORLD_H);
        double margin = Math.max(140.0, Math.min(minDim * 0.085, 560.0));
        double laneInset = Math.max(170.0, Math.min(ctx.WORLD_H * 0.22, 640.0));

        double x = ally ? margin : (ctx.WORLD_W - margin);
        // Diagonal lanes reduce immediate straight-line base pressure.
        double y = ally ? (ctx.WORLD_H - laneInset) : laneInset;
        return new double[]{x, y};
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

        if (ctx.rng.nextDouble() < 0.20) {
            ox = (ctx.rng.nextDouble() - 0.5) * 260.0;
            oy = (ctx.rng.nextDouble() - 0.5) * 260.0;
            spawnTeamShip(ctx, ShipRole.SUPERSHIP, team, base.x + ox, base.y + oy);
        }
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

    private static void initShowcase(GameContext ctx) {
        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.teamBases.clear();
        ctx.baseUpgrades.clear();

        // Arrange all showcase ships in a square grid so hulls do not overlap.
        ShipRole[] roles = ShipRole.values();
        int roleCount = 0;
        for (ShipRole r : roles) {
            if (r == ShipRole.FRIGATE) continue;
            roleCount++;
        }
        int totalShips = roleCount + 1; // +player
        int perSide = Math.max(3, (int) Math.ceil(Math.sqrt(totalShips)));
        double spacing = 230.0;
        double gridW = (perSide - 1) * spacing;
        double gridH = (perSide - 1) * spacing;
        double startX = GameMath.clamp((ctx.WORLD_W - gridW) * 0.5, 90.0, ctx.WORLD_W - 90.0 - gridW);
        double startY = GameMath.clamp((ctx.WORLD_H - gridH - 260.0) * 0.5, 90.0, ctx.WORLD_H - 90.0 - gridH);

        double playerX = startX;
        double playerY = startY;
        ctx.player = new Player(ShipRole.FRIGATE, playerX, playerY);
        ctx.player.name = "Showcase Camera";
        ctx.player.vx = 0;
        ctx.player.vy = 0;
        ctx.player.angle = 0.0; // face right
        ctx.ships.add(ctx.player);

        int gridSlot = 1; // slot 0 is the player
        int factionIndex = 0;
        Faction[] factions = new Faction[]{Faction.ALLY, Faction.ENEMY, Faction.TEAM_C, Faction.TEAM_D};
        double maxShowcaseY = playerY;

        for (ShipRole role : roles) {
            if (role == ShipRole.FRIGATE) continue;

            int row = gridSlot / perSide;
            int col = gridSlot % perSide;
            double sx = GameMath.clamp(startX + col * spacing, 80.0, ctx.WORLD_W - 80.0);
            double sy = GameMath.clamp(startY + row * spacing, 80.0, ctx.WORLD_H - 180.0);

            Faction faction = factions[factionIndex % factions.length];
            factionIndex++;
            Ship s = new FleetShip(role, faction, sx, sy);
            s.vx = 0;
            s.vy = 0;
            s.angle = 0.0; // face right

            ctx.ships.add(s);
            if (sy > maxShowcaseY) maxShowcaseY = sy;
            if (role == ShipRole.BASE) {
                if (faction == Faction.ENEMY) ctx.enemyBase = s;
                else if (faction == Faction.ALLY) ctx.allyBase = s;
                ctx.teamBases.put(faction, s);
                ctx.baseUpgrades.put(s, new BaseUpgrades());
            }
            gridSlot++;
        }

        double projectileY = Math.min(ctx.WORLD_H - 140.0, maxShowcaseY + 120.0);
        double projectileStartX = GameMath.clamp(startX, 120.0, ctx.WORLD_W - 120.0);
        double projectileStep = 220.0;

        // Static display set: one sample of each projectile class/style.
        ctx.projectiles.add(new Bullet(projectileStartX + projectileStep * 0, projectileY, 0.0, 0.0,
                760.0, 1, 1_000_000, 3.0, Faction.ALLY));
        ctx.projectiles.add(new EnergyBolt(projectileStartX + projectileStep * 1, projectileY, 0.0, 0.0,
                860.0, 2, 1_000_000, 4.5, Faction.ENEMY));
        ctx.projectiles.add(new EnergyBolt(projectileStartX + projectileStep * 2, projectileY, 0.0, 0.0,
                Ship.BEAM_BOLT_SPEED, 4, 1_000_000, 7.0, Faction.TEAM_C));
        ctx.projectiles.add(new Missile(projectileStartX + projectileStep * 3, projectileY, 0.0, null, GameContext.DT,
                0.0, 0.0, 5, 1_000_000, 7.0, Faction.ALLY));
        ctx.projectiles.add(new CIWSPellet(projectileStartX + projectileStep * 4, projectileY, 0.0, 0.0,
                950.0, 1, 1_000_000, 2.0, Faction.TEAM_D));

        tryApplyDoctrine(ctx);

        ctx.credits = 0;
        ctx.enemyWaveTimer = Double.POSITIVE_INFINITY;
        ctx.nextEventTimer = Double.POSITIVE_INFINITY;
        ctx.eventBanner = "SHOWCASE MODE  -  AI OFF";
        ctx.eventBannerT = 9999.0;
    }

    private static void initShootingRange(GameContext ctx) {
        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.teamBases.clear();
        ctx.baseUpgrades.clear();
        ctx.allyBase = null;
        ctx.enemyBase = null;

        double px = GameMath.clamp(Math.max(240.0, ctx.WORLD_W * 0.16), 90.0, ctx.WORLD_W - 90.0);
        double py = GameMath.clamp(ctx.WORLD_H * 0.5, 90.0, ctx.WORLD_H - 90.0);
        ctx.player = new Player(ShipRole.FRIGATE, px, py);
        ctx.player.name = "Player";
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.player.angle = 0.0;
        ctx.ships.add(ctx.player);

        spawnRangeTarget(ctx, ShipRole.PATROL, px + 420, py - 180, "RANGE TARGET LIGHT (HULL)", false);
        spawnRangeTarget(ctx, ShipRole.FRIGATE, px + 620, py - 60, "RANGE TARGET MEDIUM (SHIELD)", true);
        spawnRangeTarget(ctx, ShipRole.CIWS_CORVETTE, px + 760, py + 120, "RANGE TARGET CIWS (HULL)", false);
        spawnRangeTarget(ctx, ShipRole.LIGHT_CRUISER, px + 980, py - 140, "RANGE TARGET CRUISER (SHIELD)", true);
        spawnRangeTarget(ctx, ShipRole.BATTLECRUISER, px + 1220, py + 40, "RANGE TARGET HEAVY (SHIELD)", true);

        ctx.credits = 10000;
        ctx.enemyWaveTimer = Double.POSITIVE_INFINITY;
        ctx.nextEventTimer = Double.POSITIVE_INFINITY;
        ctx.minerReinforcementTimer = Double.POSITIVE_INFINITY;
        ctx.eventBanner = "SHOOTING RANGE  -  STATIONARY TARGETS";
        ctx.eventBannerT = 6.0;
    }

    private static Ship spawnRangeTarget(GameContext ctx, ShipRole role, double x, double y, String label, boolean keepShields) {
        double sx = GameMath.clamp(x, 20, ctx.WORLD_W - 20);
        double sy = GameMath.clamp(y, 20, ctx.WORLD_H - 20);
        Ship s = new FleetShip(role, Faction.ENEMY, sx, sy);

        s.name = label;
        s.angle = Math.PI;
        s.vx = 0.0;
        s.vy = 0.0;
        s.desiredSpeed = 0.0;
        s.desiredSpeedBase = 0.0;
        s.bountyValue = 0;
        s.turrets.clear();
        s.hasCIWS = false;
        s.isCarrier = false;
        s.carrierAutoLaunch = false;
        s.hasWaveMotionGun = false;

        if (!keepShields) {
            s.shieldMax = 0.0;
            s.shield = 0.0;
            s.shieldRegen = 0.0;
            s.shieldActive = false;
        } else {
            s.shieldActive = s.shieldMax > 0.0;
            s.shield = Math.min(s.shieldMax, Math.max(0.0, s.shield));
        }

        ctx.ships.add(s);
        return s;
    }
}
