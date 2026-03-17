import java.util.Random;

public final class SpawnSystem {
    private SpawnSystem(){}
    public static final int MAX_MINERS_PER_FACTION = 4;
    private static final double SHOOTING_RANGE_RESPAWN_DELAY = 10.0;
    private static final java.util.WeakHashMap<GameContext, java.util.Map<String, ShootingRangeTargetSlot>> SHOOTING_RANGE_TARGET_SLOTS =
            new java.util.WeakHashMap<>();

    private static final class ShootingRangeTargetSlot {
        final ShipRole role;
        final Faction faction;
        final String label;
        final double x;
        final double y;
        final boolean keepShields;
        double respawnTimer = 0.0;

        ShootingRangeTargetSlot(ShipRole role, Faction faction, String label, double x, double y, boolean keepShields) {
            this.role = role;
            this.faction = faction;
            this.label = label;
            this.x = x;
            this.y = y;
            this.keepShields = keepShields;
        }
    }

    private static final class ShootingRangeTargetSpec {
        final ShipRole role;
        final double dx;
        final double dy;
        final String label;
        final boolean keepShields;

        ShootingRangeTargetSpec(ShipRole role, double dx, double dy, String label, boolean keepShields) {
            this.role = role;
            this.dx = dx;
            this.dy = dy;
            this.label = label;
            this.keepShields = keepShields;
        }
    }

    // Performance + economy rebalance: fewer rocks, much richer yields per asteroid.
    private static final double ASTEROID_DENSITY_SCALE = 0.22;
    private static final double ASTEROID_ORE_MULTIPLIER = 10.0;

    public static void initWorld(GameContext ctx) {
        if (ctx.config.mode == GameMode.SHOWCASE) {
            initShowcase(ctx);
            return;
        }

        if (ctx.config.mode == GameMode.TUTORIAL) {
            TutorialSystem.init(ctx, configuredPlayerFaction(ctx));
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

        // Player spawns near selected team base for team-select modes.
        Faction playerFaction = configuredPlayerFaction(ctx);
        Ship playerAnchor = (playerFaction != null && playerFaction.teamId() == Faction.ENEMY.teamId()) ? ctx.enemyBase : ctx.allyBase;
        double[] spawn = inwardSpawnNearBase(ctx, playerAnchor);
        double px = spawn[0];
        double py = spawn[1];
        ctx.player = new Player(ShipRole.FRIGATE, px, py);
        ctx.player.faction = playerFaction;
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
        spawnTeamShip(ctx, ShipRole.FRIGATE, playerFaction, ctx.player.x - 120, ctx.player.y + 90);
        spawnTeamShip(ctx, ShipRole.CIWS_CORVETTE, playerFaction, ctx.player.x - 170, ctx.player.y - 40);

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
        if (ctx == null || role == null || faction == null) return null;

        ShipRole spawnRole = resolveSpawnRoleForFaction(ctx, faction, role);
        if (spawnRole == null) return null;

        if (spawnRole == ShipRole.MINER) {
            if (TeamSystem.countAliveMiners(ctx, faction) >= MAX_MINERS_PER_FACTION) {
                return null;
            }
        }

        double sx = GameMath.clamp(x, 20, ctx.WORLD_W - 20);
        double sy = GameMath.clamp(y, 20, ctx.WORLD_H - 20);
        Ship s = new FleetShip(spawnRole, faction, sx, sy);
        ctx.ships.add(s);
        try { DoctrineRegistry.applyToShip(s); } catch (Throwable ignored) {}
        if (spawnRole == ShipRole.MINER) logMinerSpawn(s);
        return s;
    }

    public static int requiredHangarTierForRole(ShipRole role) {
        if (role == null) return 0;
        return switch (role) {
            case BASE, STATIC_TURRET,
                 MINER, HAULER,
                 PICKET, PATROL,
                 FIGHTER, BOMBER, PD_CRAFT, DRONE,
                 FRIGATE, MISSILE_BOAT, CIWS_CORVETTE -> 0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 1;
            case TRANSPORT -> 1;
            case BATTLECRUISER, BATTLESHIP, STEALTH_SHIP -> 2;
            case DREADNOUGHT, SUPERSHIP, CARRIER, DRONE_CARRIER -> 3;
        };
    }

    public static int maxHangarTierForFaction(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null) return 0;
        int best = 0;
        boolean hasAliveBase = false;

        if (ctx.baseUpgrades != null && !ctx.baseUpgrades.isEmpty()) {
            for (java.util.Map.Entry<Ship, BaseUpgrades> e : ctx.baseUpgrades.entrySet()) {
                Ship base = (e == null) ? null : e.getKey();
                if (base == null) continue;
                if (base.role != ShipRole.BASE) continue;
                if (!base.alive || base.dying || base.hp <= 0) continue;
                if (base.faction == null || base.faction.teamId() != faction.teamId()) continue;
                hasAliveBase = true;
                BaseUpgrades up = e.getValue();
                if (up != null) best = Math.max(best, up.hangarLv);
            }
        }

        if (!hasAliveBase) {
            for (Ship b : ctx.teamBases.values()) {
                if (b == null) continue;
                if (b.role != ShipRole.BASE) continue;
                if (!b.alive || b.dying || b.hp <= 0) continue;
                if (b.faction == null || b.faction.teamId() != faction.teamId()) continue;
                hasAliveBase = true;
                break;
            }
        }
        if (!hasAliveBase && ctx.allyBase != null && ctx.allyBase.alive && !ctx.allyBase.dying
                && ctx.allyBase.hp > 0 && ctx.allyBase.faction != null
                && ctx.allyBase.faction.teamId() == faction.teamId()) hasAliveBase = true;
        if (!hasAliveBase && ctx.enemyBase != null && ctx.enemyBase.alive && !ctx.enemyBase.dying
                && ctx.enemyBase.hp > 0 && ctx.enemyBase.faction != null
                && ctx.enemyBase.faction.teamId() == faction.teamId()) hasAliveBase = true;

        boolean hasCarrierHangar = false;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (!s.isCarrier) continue;
            if (s.faction == null || s.faction.teamId() != faction.teamId()) continue;
            hasCarrierHangar = true;
            break;
        }
        if (hasCarrierHangar) best = Math.max(best, 1);

        if (!hasAliveBase && !hasCarrierHangar) return 0;
        return Math.max(0, Math.min(3, best));
    }

    public static double[] playerRespawnPose(GameContext ctx) {
        if (ctx == null) return new double[]{0.0, 0.0, 0.0};
        GameMode mode = (ctx.config == null) ? GameMode.CAMPAIGN_OPS : ctx.config.mode;

        if (mode == GameMode.SHOWCASE) {
            ShipRole[] roles = ShipRole.values();
            int roleCount = 0;
            for (ShipRole r : roles) {
                if (r == ShipRole.FRIGATE) continue;
                roleCount++;
            }
            int totalShips = roleCount + 1;
            int perSide = Math.max(3, (int) Math.ceil(Math.sqrt(totalShips)));
            double spacing = 230.0;
            double gridW = (perSide - 1) * spacing;
            double gridH = (perSide - 1) * spacing;
            double startX = GameMath.clamp((ctx.WORLD_W - gridW) * 0.5, 90.0, ctx.WORLD_W - 90.0 - gridW);
            double startY = GameMath.clamp((ctx.WORLD_H - gridH - 260.0) * 0.5, 90.0, ctx.WORLD_H - 90.0 - gridH);
            return new double[]{startX, startY, 0.0};
        }

        if (mode == GameMode.SHOOTING_RANGE) {
            double px = GameMath.clamp(Math.max(240.0, ctx.WORLD_W * 0.16), 90.0, ctx.WORLD_W - 90.0);
            double py = GameMath.clamp(ctx.WORLD_H * 0.5, 90.0, ctx.WORLD_H - 90.0);
            return new double[]{px, py, 0.0};
        }

        Faction playerFaction = (ctx.player != null && ctx.player.faction != null)
                ? ctx.player.faction
                : configuredPlayerFaction(ctx);
        Ship anchor = preferredRespawnAnchor(ctx, playerFaction);
        if (anchor != null) {
            double[] spawn = inwardSpawnNearBase(ctx, anchor);
            double angle = Math.atan2(ctx.WORLD_H * 0.5 - spawn[1], ctx.WORLD_W * 0.5 - spawn[0]);
            return new double[]{spawn[0], spawn[1], angle};
        }

        double fallbackX = GameMath.clamp(Math.max(220.0, ctx.WORLD_W * 0.18), 90.0, ctx.WORLD_W - 90.0);
        double fallbackY = GameMath.clamp(ctx.WORLD_H * 0.5, 90.0, ctx.WORLD_H - 90.0);
        return new double[]{fallbackX, fallbackY, 0.0};
    }

    private static ShipRole resolveSpawnRoleForFaction(GameContext ctx, Faction faction, ShipRole requested) {
        if (requested == null) return null;
        ShipRole doctrinal = applyFactionRoleBias(ctx, faction, requested);
        int availableTier = maxHangarTierForFaction(ctx, faction);
        if (requiredHangarTierForRole(doctrinal) <= availableTier) return doctrinal;

        for (ShipRole fallback : fallbackRolesFor(doctrinal)) {
            if (fallback == null) continue;
            if (requiredHangarTierForRole(fallback) <= availableTier) return fallback;
        }
        return null;
    }

    private static ShipRole applyFactionRoleBias(GameContext ctx, Faction faction, ShipRole requested) {
        if (faction == null || requested == null) return requested;
        Random rng = (ctx == null || ctx.rng == null) ? new Random() : ctx.rng;

        if (faction == Faction.TEAM_C) {
            // Aegis Lattice: directed-energy line ships with limited missile reliance.
            return switch (requested) {
                case MISSILE_BOAT -> ShipRole.FRIGATE;
                case CRUISER -> ShipRole.MEDIUM_CRUISER;
                case BOMBER -> ShipRole.FIGHTER;
                case STEALTH_SHIP -> (rng.nextDouble() < 0.60) ? ShipRole.PICKET : requested;
                case FRIGATE -> (rng.nextDouble() < 0.22) ? ShipRole.PICKET : requested;
                default -> requested;
            };
        }

        if (faction == Faction.TEAM_D) {
            // Viper Barrage: salvo-heavy fleet composition with frequent missile boats/cruisers.
            return switch (requested) {
                case PATROL, PICKET, FRIGATE, CIWS_CORVETTE ->
                        (rng.nextDouble() < 0.52) ? ShipRole.MISSILE_BOAT : requested;
                case LIGHT_CRUISER, MEDIUM_CRUISER ->
                        (rng.nextDouble() < 0.48) ? ShipRole.CRUISER : requested;
                case STEALTH_SHIP -> ShipRole.MISSILE_BOAT;
                case FIGHTER -> (rng.nextDouble() < 0.40) ? ShipRole.BOMBER : requested;
                default -> requested;
            };
        }

        return requested;
    }

    private static Ship preferredRespawnAnchor(GameContext ctx, Faction playerFaction) {
        if (ctx == null) return null;
        Faction teamKey = (playerFaction == null) ? Faction.ALLY : Faction.forTeamId(playerFaction.teamId());

        Ship base = TeamSystem.getBaseForTeam(ctx, teamKey);
        if (isUsableRespawnAnchor(base)) return base;
        if (playerFaction != null) {
            Ship exactBase = TeamSystem.getBaseForTeam(ctx, playerFaction);
            if (isUsableRespawnAnchor(exactBase)) return exactBase;
        }

        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship s : ctx.ships) {
            if (!isUsableRespawnAnchor(s)) continue;
            if (s == ctx.player) continue;
            if (playerFaction != null && (s.faction == null || s.faction.teamId() != playerFaction.teamId())) continue;
            double score = s.hpMax + s.shieldMax + s.radius * 2.0;
            if (s.role == ShipRole.BASE) score += 5000.0;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static boolean isUsableRespawnAnchor(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }

    private static ShipRole[] fallbackRolesFor(ShipRole role) {
        if (role == null) return new ShipRole[0];
        return switch (role) {
            case SUPERSHIP -> new ShipRole[]{
                    ShipRole.DREADNOUGHT, ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER,
                    ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case DREADNOUGHT -> new ShipRole[]{
                    ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER, ShipRole.MEDIUM_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case BATTLESHIP -> new ShipRole[]{
                    ShipRole.BATTLECRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case BATTLECRUISER -> new ShipRole[]{
                    ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case MEDIUM_CRUISER, CRUISER -> new ShipRole[]{
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case LIGHT_CRUISER -> new ShipRole[]{ShipRole.FRIGATE};
            case STEALTH_SHIP -> new ShipRole[]{ShipRole.MISSILE_BOAT, ShipRole.PICKET, ShipRole.FRIGATE};
            case CARRIER -> new ShipRole[]{
                    ShipRole.DRONE_CARRIER, ShipRole.BATTLECRUISER, ShipRole.MEDIUM_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case DRONE_CARRIER -> new ShipRole[]{
                    ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.MEDIUM_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case TRANSPORT -> new ShipRole[]{ShipRole.HAULER, ShipRole.FRIGATE};
            default -> new ShipRole[]{role};
        };
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
        spawnTeamGroup(ctx, Faction.ENEMY, x, y);
    }

    public static void spawnAllyGroup(GameContext ctx, double x, double y) {
        spawnTeamGroup(ctx, Faction.ALLY, x, y);
    }

    public static void spawnTeamGroup(GameContext ctx, Faction faction, double x, double y) {
        if (ctx == null || faction == null) return;
        spawnTeamShip(ctx, ShipRole.PATROL, faction, x + 0, y + 0);
        spawnTeamShip(ctx, ShipRole.PICKET, faction, x + 70, y + 50);
        spawnTeamShip(ctx, ShipRole.FRIGATE, faction, x - 90, y + 70);
        if (ctx.rng.nextDouble() < 0.18) spawnTeamShip(ctx, ShipRole.CRUISER, faction, x - 40, y - 120);
        if (ctx.rng.nextDouble() < 0.35) spawnTeamShip(ctx, ShipRole.MISSILE_BOAT, faction, x + 110, y - 80);
        if (ctx.rng.nextDouble() < 0.08) spawnTeamShip(ctx, ShipRole.SUPERSHIP, faction, x + 180, y - 40);
    }

    public static void spawnAsteroidField(GameContext ctx) {
        ctx.asteroids.clear();
        int baseCount = (ctx.WORLD_W <= 6000) ? 120 : (ctx.WORLD_W <= 12000 ? 220 : 380);
        int n = Math.max(18, (int) Math.round(baseCount * ASTEROID_DENSITY_SCALE));
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
            double ore = (200 + rng.nextDouble() * 800) * ASTEROID_ORE_MULTIPLIER;
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

        // Player spawns near selected team base.
        Faction playerFaction = configuredPlayerFaction(ctx);
        Faction teamKey = Faction.forTeamId(playerFaction.teamId());
        Ship pBase = ctx.teamBases.get(teamKey);
        if (pBase == null) pBase = ctx.teamBases.get(Faction.ALLY);
        double[] spawn = inwardSpawnNearBase(ctx, pBase);
        double px = spawn[0];
        double py = spawn[1];
        ctx.player = new Player(ShipRole.FRIGATE, px, py);
        ctx.player.faction = playerFaction;
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

    private static Faction configuredPlayerFaction(GameContext ctx) {
        if (ctx == null || ctx.config == null) return Faction.PLAYER;
        if (ctx.config.mode != GameMode.RESOURCE_RUSH
                && ctx.config.mode != GameMode.SHOOTING_RANGE
                && ctx.config.mode != GameMode.FOUR_TEAM_DOMINATION) {
            return Faction.PLAYER;
        }
        return playerFactionForTeamId(ctx.config.playerTeamId);
    }

    private static Faction playerFactionForTeamId(int teamId) {
        if (teamId == 0) return Faction.PLAYER;
        return Faction.forTeamId(teamId);
    }

    private static double[] inwardSpawnNearBase(GameContext ctx, Ship base) {
        if (ctx == null || base == null) return new double[]{0.0, 0.0};
        double cx = ctx.WORLD_W * 0.5;
        double cy = ctx.WORLD_H * 0.5;
        double dx = cx - base.x;
        double dy = cy - base.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-9) len = 1.0;
        double ux = dx / len;
        double uy = dy / len;
        double forward = Math.max(220.0, ctx.WORLD_W * 0.08);
        double lateral = Math.max(120.0, ctx.WORLD_H * 0.04);
        double px = base.x + ux * forward - uy * lateral * 0.35;
        double py = base.y + uy * forward + ux * lateral * 0.35;
        px = GameMath.clamp(px, 40.0, ctx.WORLD_W - 40.0);
        py = GameMath.clamp(py, 40.0, ctx.WORLD_H - 40.0);
        return new double[]{px, py};
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

        if (ctx.rng.nextDouble() < 0.22) {
            ox = (ctx.rng.nextDouble() - 0.5) * 240.0;
            oy = (ctx.rng.nextDouble() - 0.5) * 240.0;
            spawnTeamShip(ctx, ShipRole.CRUISER, team, base.x + ox, base.y + oy);
        }

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

        ctx.credits = 100;
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
        clearShootingRangeTargetSlots(ctx);

        double px = GameMath.clamp(Math.max(240.0, ctx.WORLD_W * 0.16), 90.0, ctx.WORLD_W - 90.0);
        double py = GameMath.clamp(ctx.WORLD_H * 0.5, 90.0, ctx.WORLD_H - 90.0);
        ctx.player = new Player(ShipRole.FRIGATE, px, py);
        ctx.player.faction = configuredPlayerFaction(ctx);
        ctx.player.name = "Player";
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.player.angle = 0.0;
        ctx.ships.add(ctx.player);
        try { DoctrineRegistry.applyToShip(ctx.player); } catch (Throwable ignored) {}

        Faction targetFaction = ctx.player.faction.isFriendlyTo(Faction.ENEMY) ? Faction.ALLY : Faction.ENEMY;
        populateShootingRangeTargets(ctx, px, py, targetFaction);

        ctx.credits = 10000;
        ctx.enemyWaveTimer = Double.POSITIVE_INFINITY;
        ctx.nextEventTimer = Double.POSITIVE_INFINITY;
        ctx.minerReinforcementTimer = Double.POSITIVE_INFINITY;
        ctx.eventBanner = "SHOOTING RANGE  -  ALL HULL EXHIBITION";
        ctx.eventBannerT = 6.0;
    }

    static void populateShootingRangeTargets(GameContext ctx, double originX, double originY, Faction faction) {
        if (ctx == null || faction == null) return;
        for (ShootingRangeTargetSpec spec : shootingRangeLayout()) {
            spawnRangeTarget(ctx,
                    spec.role,
                    faction,
                    originX + spec.dx,
                    originY + spec.dy,
                    spec.label,
                    spec.keepShields);
        }
    }

    public static void updateShootingRangeRespawns(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        if (ctx.config == null || ctx.config.mode != GameMode.SHOOTING_RANGE) return;
        java.util.Map<String, ShootingRangeTargetSlot> slots = shootingRangeTargetSlotsFor(ctx);
        if (slots.isEmpty()) return;

        for (ShootingRangeTargetSlot slot : slots.values()) {
            if (slot == null) continue;

            boolean active = false;
            for (Ship s : ctx.ships) {
                if (s == null) continue;
                if (s.role != slot.role) continue;
                if (!slot.label.equals(s.name)) continue;
                if (s.faction != slot.faction) continue;
                if (s.alive && !s.dying && s.hp > 0) {
                    active = true;
                    break;
                }
            }

            if (active) {
                slot.respawnTimer = 0.0;
                continue;
            }

            slot.respawnTimer += dt;
            if (slot.respawnTimer < SHOOTING_RANGE_RESPAWN_DELAY) continue;

            slot.respawnTimer = 0.0;
            spawnRangeTarget(ctx, slot.role, slot.faction, slot.x, slot.y, slot.label, slot.keepShields);
        }
    }

    private static java.util.Map<String, ShootingRangeTargetSlot> shootingRangeTargetSlotsFor(GameContext ctx) {
        return SHOOTING_RANGE_TARGET_SLOTS.computeIfAbsent(ctx, k -> new java.util.LinkedHashMap<>());
    }

    private static void clearShootingRangeTargetSlots(GameContext ctx) {
        if (ctx == null) return;
        shootingRangeTargetSlotsFor(ctx).clear();
    }

    private static void registerShootingRangeTarget(GameContext ctx, ShipRole role, Faction faction, double x, double y, String label, boolean keepShields) {
        if (ctx == null || role == null || faction == null || label == null || label.isBlank()) return;
        shootingRangeTargetSlotsFor(ctx).put(label, new ShootingRangeTargetSlot(role, faction, label, x, y, keepShields));
    }

    private static Ship spawnRangeTarget(GameContext ctx, ShipRole role, Faction faction, double x, double y, String label, boolean keepShields) {
        double sx = GameMath.clamp(x, 20, ctx.WORLD_W - 20);
        double sy = GameMath.clamp(y, 20, ctx.WORLD_H - 20);
        Ship s = new FleetShip(role, faction, sx, sy);

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
        registerShootingRangeTarget(ctx, role, faction, sx, sy, label, keepShields);
        return s;
    }

    private static java.util.List<ShootingRangeTargetSpec> shootingRangeLayout() {
        java.util.List<ShootingRangeTargetSpec> out = new java.util.ArrayList<>();

        // Forward screen: tiny craft and raiders.
        out.add(spec(ShipRole.FIGHTER, 300, -320, "STRIKE SCREEN FIGHTER", false));
        out.add(spec(ShipRole.DRONE, 380, -250, "STRIKE SCREEN DRONE", false));
        out.add(spec(ShipRole.PD_CRAFT, 455, -340, "POINT-DEFENSE CRAFT", false));
        out.add(spec(ShipRole.BOMBER, 540, -275, "BOMBER INTERCEPT LANE", false));
        out.add(spec(ShipRole.PATROL, 640, -320, "PATROL PICKET MARK", false));
        out.add(spec(ShipRole.PICKET, 735, -248, "PICKET OUTRIDER", false));
        out.add(spec(ShipRole.STEALTH_SHIP, 845, -330, "STEALTH RAIDER GHOST", false));

        // Escort and skirmish line.
        out.add(spec(ShipRole.FRIGATE, 460, -90, "FRIGATE DUEL HULL", true));
        out.add(spec(ShipRole.MISSILE_BOAT, 635, -130, "MISSILE BOAT SALVO", false));
        out.add(spec(ShipRole.CIWS_CORVETTE, 785, -52, "CIWS CORVETTE SCREEN", false));
        out.add(spec(ShipRole.LIGHT_CRUISER, 980, -120, "LIGHT CRUISER SHIELD", true));
        out.add(spec(ShipRole.MEDIUM_CRUISER, 1160, -48, "MEDIUM CRUISER LINE", true));

        // Centerline bruisers.
        out.add(spec(ShipRole.CRUISER, 1260, 78, "CRUISER GUNLINE", true));
        out.add(spec(ShipRole.BATTLECRUISER, 1485, 8, "BATTLECRUISER BREAKER", true));
        out.add(spec(ShipRole.BATTLESHIP, 1715, -96, "BATTLESHIP ANCHOR", true));
        out.add(spec(ShipRole.DREADNOUGHT, 1980, 12, "DREADNOUGHT TEST WALL", true));
        out.add(spec(ShipRole.SUPERSHIP, 2315, -24, "SUPERSHIP FINAL EXAM", true));

        // Logistics and carriers.
        out.add(spec(ShipRole.MINER, 690, 250, "MINER WORK BARGE", false));
        out.add(spec(ShipRole.HAULER, 860, 305, "HAULER FREIGHT HULL", false));
        out.add(spec(ShipRole.TRANSPORT, 1040, 246, "TRANSPORT SUPPORT FRAME", false));
        out.add(spec(ShipRole.CARRIER, 1315, 292, "CARRIER FLIGHT DECK", true));
        out.add(spec(ShipRole.DRONE_CARRIER, 1560, 352, "DRONE CARRIER NEST", true));

        // Fortress corner: structures at the far end.
        out.add(spec(ShipRole.STATIC_TURRET, 2140, 250, "DEFENSE NODE PORT", false));
        out.add(spec(ShipRole.STATIC_TURRET, 2140, 392, "DEFENSE NODE STARBOARD", false));
        out.add(spec(ShipRole.BASE, 2420, 320, "RANGE FORTRESS CORE", true));
        return out;
    }

    private static ShootingRangeTargetSpec spec(ShipRole role, double dx, double dy, String label, boolean keepShields) {
        return new ShootingRangeTargetSpec(role, dx, dy, label, keepShields);
    }
}
