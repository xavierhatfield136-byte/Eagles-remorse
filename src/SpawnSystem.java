import app.config.GameMode;
import java.util.Locale;
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
    private static final double ASTEROID_ORE_MULTIPLIER = 6.5;

    public static void initWorld(GameContext ctx) {
        Faction.clearCampaignAlliances();
        FogOfWarSystem.reset(ctx);

        if (ctx.config.mode == GameMode.SHOWCASE) {
            initShowcase(ctx);
            CampaignSystem.initTacticalStrikeState(ctx);
            return;
        }

        if (ctx.config.mode == GameMode.TUTORIAL) {
            TutorialSystem.init(ctx, configuredPlayerFaction(ctx));
            return;
        }

        if (ctx.config.mode == GameMode.SHOOTING_RANGE) {
            initShootingRange(ctx);
            CampaignSystem.initTacticalStrikeState(ctx);
            return;
        }

        if (ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) {
            initFourTeamDomination(ctx);
            CampaignSystem.initTacticalStrikeState(ctx);
            return;
        }

        if (ctx.config.mode == GameMode.CUSTOM_BATTLES) {
            initCustomBattles(ctx);
            CampaignSystem.initTacticalStrikeState(ctx);
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

        ctx.baseUpgrades.put(ctx.allyBase, new BaseUpgrades().bindTo(ctx.allyBase));
        ctx.baseUpgrades.put(ctx.enemyBase, new BaseUpgrades().bindTo(ctx.enemyBase));

        // Player spawns near selected team base for team-select modes.
        Faction playerFaction = configuredPlayerFaction(ctx);
        Ship playerAnchor = (playerFaction != null && playerFaction.teamId() == Faction.ENEMY.teamId()) ? ctx.enemyBase : ctx.allyBase;
        double[] spawn = inwardSpawnNearBase(ctx, playerAnchor);
        double px = spawn[0];
        double py = spawn[1];
        ShipRole playerRole = (ctx.config.mode == GameMode.CAMPAIGN_OPS || ctx.config.mode == GameMode.FLEET)
                ? ShipRole.MOTHERSHIP
                : ShipRole.FRIGATE;
        ctx.player = new Player(playerRole, px, py);
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
                 FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE -> 0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 1;
            case TRANSPORT -> 1;
            case BATTLECRUISER, BATTLESHIP, STEALTH_SHIP -> 2;
            case DREADNOUGHT, SUPERSHIP,
                 TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                 INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                 MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                 MOTHERSHIP,
                 CARRIER, DRONE_CARRIER -> 3;
        };
    }

    public static int maxHangarTierForFaction(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null) return 0;
        int best = 0;
        boolean hasAliveBase = false;

        if (CampaignSystem.isCampaignActive(ctx)
                && faction.teamId() == Faction.ALLY.teamId()
                && ctx.player != null
                && ctx.player.alive
                && !ctx.player.dying
                && ctx.player.hp > 0) {
            BaseUpgrades playerUpgrades = ctx.baseUpgrades.get(ctx.player);
            if (playerUpgrades != null) {
                best = Math.max(best, playerUpgrades.hangarLv);
                hasAliveBase = true;
            }
        }

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
            // Green team: directed-energy line ships with limited missile reliance.
            return switch (requested) {
                case MISSILE_BOAT -> ShipRole.FRIGATE;
                case CRUISER -> ShipRole.MEDIUM_CRUISER;
                case BOMBER -> ShipRole.FIGHTER;
                case STEALTH_SHIP -> (rng.nextDouble() < 0.60) ? ShipRole.PICKET : requested;
                case FRIGATE -> (rng.nextDouble() < 0.22) ? ShipRole.PICKET : requested;
                default -> requested;
            };
        }

        if (faction.isYellowLineage()) {
            // Yellow team: salvo-heavy fleet composition with frequent missile boats/cruisers.
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
            case TRANSPORT_TITAN -> new ShipRole[]{
                    ShipRole.TRANSPORT, ShipRole.HAULER, ShipRole.DREADNOUGHT, ShipRole.BATTLESHIP,
                    ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case BULWARK_TITAN -> new ShipRole[]{
                    ShipRole.DREADNOUGHT, ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER,
                    ShipRole.CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case CARRIER_SUPPORT_TITAN -> new ShipRole[]{
                    ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.BATTLECRUISER,
                    ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case VANGUARD_TITAN, INTERDICTION_TITAN -> new ShipRole[]{
                    ShipRole.BATTLECRUISER, ShipRole.CRUISER, ShipRole.MEDIUM_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN, ARTILLERY_TITAN,
                 SHIELD_BASTION_TITAN, ELITE_SUPERSHIP_COMMAND_TITAN -> new ShipRole[]{
                    ShipRole.DREADNOUGHT, ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER,
                    ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case ELITE_REINFORCEMENTS_TITAN -> new ShipRole[]{
                    ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE, ShipRole.CARRIER
            };
            case FLEET_TELEPORTER_TITAN -> new ShipRole[]{
                    ShipRole.SUPERSHIP, ShipRole.DREADNOUGHT, ShipRole.BATTLESHIP,
                    ShipRole.BATTLECRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case MOBILE_STATION_TITAN -> new ShipRole[]{
                    ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.TRANSPORT,
                    ShipRole.HAULER, ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case HYPERWEAPON_TITAN -> new ShipRole[]{
                    ShipRole.SUPERSHIP, ShipRole.DREADNOUGHT, ShipRole.BATTLESHIP,
                    ShipRole.BATTLECRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE
            };
            case MOTHERSHIP -> new ShipRole[]{
                    ShipRole.MOBILE_STATION_TITAN, ShipRole.CARRIER_SUPPORT_TITAN, ShipRole.SUPERSHIP,
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
            case ARTILLERY_SHIP -> new ShipRole[]{ShipRole.FRIGATE, ShipRole.PICKET};
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
        OffSectorSimulationSystem.ReinforcementDirective directive =
                OffSectorSimulationSystem.reinforcementDirective(ctx, faction);
        OffSectorSimulationSystem.ReinforcementProfile profile =
                (directive == null) ? OffSectorSimulationSystem.ReinforcementProfile.BALANCED : directive.profile;
        switch (profile) {
            case DEFENSE -> spawnDefenseGroup(ctx, faction, x, y);
            case SPEARHEAD -> spawnSpearheadGroup(ctx, faction, x, y);
            case FIRE_SUPPORT -> spawnFireSupportGroup(ctx, faction, x, y);
            default -> spawnBalancedGroup(ctx, faction, x, y);
        }
    }

    private static void spawnBalancedGroup(GameContext ctx, Faction faction, double x, double y) {
        spawnTeamShip(ctx, ShipRole.PATROL, faction, x + 0, y + 0);
        spawnTeamShip(ctx, ShipRole.PICKET, faction, x + 70, y + 50);
        spawnTeamShip(ctx, ShipRole.FRIGATE, faction, x - 90, y + 70);
        if (ctx.rng.nextDouble() < 0.22) spawnTeamShip(ctx, ShipRole.ARTILLERY_SHIP, faction, x + 120, y + 120);
        if (ctx.rng.nextDouble() < 0.18) spawnTeamShip(ctx, ShipRole.CRUISER, faction, x - 40, y - 120);
        if (ctx.rng.nextDouble() < 0.35) spawnTeamShip(ctx, ShipRole.MISSILE_BOAT, faction, x + 110, y - 80);
        if (ctx.rng.nextDouble() < 0.08) spawnTeamShip(ctx, ShipRole.SUPERSHIP, faction, x + 180, y - 40);
    }

    private static void spawnDefenseGroup(GameContext ctx, Faction faction, double x, double y) {
        spawnTeamShip(ctx, ShipRole.PICKET, faction, x + 0, y + 0);
        spawnTeamShip(ctx, ShipRole.CIWS_CORVETTE, faction, x + 70, y + 45);
        spawnTeamShip(ctx, ShipRole.FRIGATE, faction, x - 80, y + 60);
        spawnTeamShip(ctx, ShipRole.PATROL, faction, x + 90, y - 70);
        if (ctx.rng.nextDouble() < 0.42) spawnTeamShip(ctx, ShipRole.MISSILE_BOAT, faction, x - 130, y - 40);
        if (ctx.rng.nextDouble() < 0.24) spawnTeamShip(ctx, ShipRole.LIGHT_CRUISER, faction, x + 140, y + 120);
        if (ctx.rng.nextDouble() < 0.08) spawnTeamShip(ctx, ShipRole.BATTLECRUISER, faction, x - 170, y + 20);
    }

    private static void spawnSpearheadGroup(GameContext ctx, Faction faction, double x, double y) {
        spawnTeamShip(ctx, ShipRole.FRIGATE, faction, x + 0, y + 0);
        spawnTeamShip(ctx, ShipRole.MISSILE_BOAT, faction, x + 80, y - 50);
        spawnTeamShip(ctx, ShipRole.ARTILLERY_SHIP, faction, x - 90, y + 70);
        spawnTeamShip(ctx, ShipRole.PICKET, faction, x + 90, y + 65);
        if (ctx.rng.nextDouble() < 0.38) spawnTeamShip(ctx, ShipRole.CRUISER, faction, x - 150, y - 90);
        if (ctx.rng.nextDouble() < 0.20) spawnTeamShip(ctx, ShipRole.BATTLECRUISER, faction, x + 150, y + 110);
        if (ctx.rng.nextDouble() < 0.10) spawnTeamShip(ctx, ShipRole.SUPERSHIP, faction, x + 210, y + 10);
    }

    private static void spawnFireSupportGroup(GameContext ctx, Faction faction, double x, double y) {
        spawnTeamShip(ctx, ShipRole.PICKET, faction, x + 0, y + 0);
        spawnTeamShip(ctx, ShipRole.FRIGATE, faction, x - 80, y + 55);
        spawnTeamShip(ctx, ShipRole.MISSILE_BOAT, faction, x + 90, y - 70);
        spawnTeamShip(ctx, ShipRole.ARTILLERY_SHIP, faction, x + 130, y + 95);
        if (ctx.rng.nextDouble() < 0.34) spawnTeamShip(ctx, ShipRole.CRUISER, faction, x - 130, y - 105);
        if (ctx.rng.nextDouble() < 0.20) spawnTeamShip(ctx, ShipRole.CIWS_CORVETTE, faction, x + 55, y + 130);
        if (ctx.rng.nextDouble() < 0.06) spawnTeamShip(ctx, ShipRole.BATTLESHIP, faction, x - 200, y + 20);
    }

    public static void spawnAsteroidField(GameContext ctx) {
        ctx.asteroids.clear();
        int baseCount = (ctx.WORLD_W <= 6000) ? 120 : (ctx.WORLD_W <= 12000 ? 220 : 380);
        int n = Math.max(18, (int) Math.round(baseCount * ASTEROID_DENSITY_SCALE));
        Random rng = ctx.rng;
        java.util.List<BattlefieldSectorSystem.SectorDefinition> sectors = BattlefieldSectorSystem.definitions(ctx);
        boolean sectorized = !sectors.isEmpty();
        for (int i = 0; i < n; i++) {
            double x = 0;
            double y = 0;
            boolean ok = false;
            for (int tries = 0; tries < 25; tries++) {
                if (sectorized) {
                    BattlefieldSectorSystem.SectorDefinition sector = sectors.get(rng.nextInt(sectors.size()));
                    double marginX = Math.min(200.0, Math.max(40.0, sector.widthWorld(ctx) * 0.08));
                    double marginY = Math.min(200.0, Math.max(40.0, sector.heightWorld(ctx) * 0.08));
                    x = sector.minWorldX(ctx) + marginX + rng.nextDouble() * Math.max(1.0, sector.widthWorld(ctx) - marginX * 2.0);
                    y = sector.minWorldY(ctx) + marginY + rng.nextDouble() * Math.max(1.0, sector.heightWorld(ctx) - marginY * 2.0);
                } else {
                    x = 200 + rng.nextDouble() * (ctx.WORLD_W - 400);
                    y = 200 + rng.nextDouble() * (ctx.WORLD_H - 400);
                }
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

        Faction[] teams = Faction.fourTeamFactions();
        for (int i = 0; i < teams.length; i++) {
            Faction team = teams[i];
            BattlefieldSectorSystem.SectorDefinition homeSector = BattlefieldSectorSystem.homeSector(ctx, team);
            double[] basePoint = fourTeamHomeBaseAnchor(ctx, homeSector);
            double bx = basePoint[0];
            double by = basePoint[1];

            Ship base = new FleetShip(ShipRole.BASE, team, bx, by);
            clampBaseToBounds(ctx, base);

            ctx.ships.add(base);
            ctx.teamBases.put(team, base);
            ctx.baseUpgrades.put(base, new BaseUpgrades().bindTo(base));
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
        seedFourTeamFrontlines(ctx, teams);

        logBaseSpawns(ctx, teams);

        // Apply doctrine tuning (Step 5B/5C) if present
        tryApplyDoctrine(ctx);
    }

    private static void initCustomBattles(GameContext ctx) {
        ctx.teamBases.clear();

        int playerTeamId = (ctx.config == null) ? 0 : ctx.config.playerTeamId;
        int enemyTeamId = (ctx.config == null) ? 1 : ctx.config.customBattleEnemyTeamId;
        if (enemyTeamId == playerTeamId) {
            enemyTeamId = (playerTeamId == 0) ? 1 : 0;
        }

        Faction playerFaction = playerFactionForTeamId(playerTeamId);
        Faction playerTeamFaction = Faction.forTeamId(playerTeamId);
        Faction enemyFaction = Faction.forTeamId(enemyTeamId);

        double[] friendlyBasePos = edgeBasePosition(ctx, true);
        double[] enemyBasePos = edgeBasePosition(ctx, false);

        Ship friendlyBase = new FleetShip(ShipRole.BASE, playerTeamFaction, friendlyBasePos[0], friendlyBasePos[1]);
        Ship hostileBase = new FleetShip(ShipRole.BASE, enemyFaction, enemyBasePos[0], enemyBasePos[1]);
        clampBaseToBounds(ctx, friendlyBase);
        clampBaseToBounds(ctx, hostileBase);

        ctx.ships.add(friendlyBase);
        ctx.ships.add(hostileBase);
        ctx.teamBases.put(playerTeamFaction, friendlyBase);
        ctx.teamBases.put(enemyFaction, hostileBase);
        ctx.allyBase = friendlyBase;
        ctx.enemyBase = hostileBase;

        BaseUpgrades friendlyUpgrades = new BaseUpgrades().bindTo(friendlyBase);
        friendlyUpgrades.hangarLv = 3;
        BaseUpgrades enemyUpgrades = new BaseUpgrades().bindTo(hostileBase);
        enemyUpgrades.hangarLv = 3;
        ctx.baseUpgrades.put(friendlyBase, friendlyUpgrades);
        ctx.baseUpgrades.put(hostileBase, enemyUpgrades);

        double[] spawn = inwardSpawnNearBase(ctx, friendlyBase);
        ctx.player = new Player(ShipRole.MOTHERSHIP, spawn[0], spawn[1]);
        ctx.player.faction = playerFaction;
        ctx.player.name = "Player";
        ctx.ships.add(ctx.player);

        java.util.LinkedHashMap<ShipRole, Integer> friendlyRoster =
                parseCustomBattleRoster(ctx.config == null ? "" : ctx.config.customBattleFriendlyRoster);
        java.util.LinkedHashMap<ShipRole, Integer> enemyRoster =
                parseCustomBattleRoster(ctx.config == null ? "" : ctx.config.customBattleEnemyRoster);
        if (friendlyRoster.isEmpty()) friendlyRoster = defaultCustomBattleRoster(true);
        if (enemyRoster.isEmpty()) enemyRoster = defaultCustomBattleRoster(false);

        spawnCustomBattleRoster(ctx, playerTeamFaction, friendlyBase, friendlyRoster, true);
        spawnCustomBattleRoster(ctx, enemyFaction, hostileBase, enemyRoster, false);

        tryApplyDoctrine(ctx);
    }

    private static double[] fourTeamHomeBaseAnchor(GameContext ctx, BattlefieldSectorSystem.SectorDefinition homeSector) {
        if (ctx == null || homeSector == null) return new double[]{0.0, 0.0};
        double sectorCx = homeSector.centerX(ctx);
        double sectorCy = homeSector.centerY(ctx);
        double dirX = Math.signum(sectorCx - ctx.WORLD_W * 0.5);
        double dirY = Math.signum(sectorCy - ctx.WORLD_H * 0.5);
        if (Math.abs(dirX) < 1e-6) dirX = 1.0;
        if (Math.abs(dirY) < 1e-6) dirY = 1.0;
        double offsetX = homeSector.widthWorld(ctx) * 0.22 * dirX;
        double offsetY = homeSector.heightWorld(ctx) * 0.22 * dirY;
        double marginX = Math.max(260.0, homeSector.widthWorld(ctx) * 0.14);
        double marginY = Math.max(260.0, homeSector.heightWorld(ctx) * 0.14);
        double bx = GameMath.clamp(sectorCx + offsetX,
                homeSector.minWorldX(ctx) + marginX, homeSector.maxWorldX(ctx) - marginX);
        double by = GameMath.clamp(sectorCy + offsetY,
                homeSector.minWorldY(ctx) + marginY, homeSector.maxWorldY(ctx) - marginY);
        return new double[]{bx, by};
    }

    private static void seedFourTeamFrontlines(GameContext ctx, Faction[] teams) {
        if (ctx == null || teams == null) return;
        BattlefieldSectorSystem.SectorDefinition center = BattlefieldSectorSystem.findSector(ctx, "central-warzone");
        for (Faction team : teams) {
            if (team == null) continue;
            BattlefieldSectorSystem.SectorDefinition home = BattlefieldSectorSystem.homeSector(ctx, team);
            if (home == null) continue;

            for (BattlefieldSectorSystem.SectorDefinition adjacent : BattlefieldSectorSystem.adjacentSectors(ctx, home)) {
                if (adjacent == null || adjacent.anchorFaction != null) continue;
                double[] point = fourTeamFrontlinePoint(ctx, adjacent, home, team.teamId());
                spawnTeamShip(ctx, ShipRole.PATROL, team, point[0], point[1]);
                spawnTeamShip(ctx, ShipRole.PICKET, team, point[0] + 52.0, point[1] - 34.0);
                if (ctx.rng.nextDouble() < 0.85) {
                    spawnTeamShip(ctx, ShipRole.FRIGATE, team, point[0] - 68.0, point[1] + 42.0);
                }
            }

            if (center != null) {
                double[] point = fourTeamFrontlinePoint(ctx, center, home, team.teamId());
                spawnTeamShip(ctx, ShipRole.PICKET, team, point[0], point[1]);
                if (ctx.rng.nextDouble() < 0.55) {
                    spawnTeamShip(ctx, ShipRole.PATROL, team, point[0] + 44.0, point[1] + 36.0);
                }
            }
        }
    }

    private static double[] fourTeamFrontlinePoint(GameContext ctx,
                                                   BattlefieldSectorSystem.SectorDefinition sector,
                                                   BattlefieldSectorSystem.SectorDefinition home,
                                                   int ordinal) {
        if (ctx == null || sector == null) return new double[]{0.0, 0.0};
        double x = sector.centerX(ctx);
        double y = sector.centerY(ctx);
        if (home != null) {
            double dx = home.centerX(ctx) - sector.centerX(ctx);
            double dy = home.centerY(ctx) - sector.centerY(ctx);
            double len = Math.hypot(dx, dy);
            if (len > 1e-6) {
                double nx = dx / len;
                double ny = dy / len;
                x += nx * sector.widthWorld(ctx) * 0.18;
                y += ny * sector.heightWorld(ctx) * 0.18;
            }
        }
        double jitter = 70.0 + (ordinal % 3) * 18.0;
        x += (ctx.rng.nextDouble() - 0.5) * jitter;
        y += (ctx.rng.nextDouble() - 0.5) * jitter;
        x = GameMath.clamp(x, sector.minWorldX(ctx) + 120.0, sector.maxWorldX(ctx) - 120.0);
        y = GameMath.clamp(y, sector.minWorldY(ctx) + 120.0, sector.maxWorldY(ctx) - 120.0);
        return new double[]{x, y};
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
                && ctx.config.mode != GameMode.FOUR_TEAM_DOMINATION
                && ctx.config.mode != GameMode.CUSTOM_BATTLES) {
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

    private static java.util.LinkedHashMap<ShipRole, Integer> parseCustomBattleRoster(String encoded) {
        java.util.LinkedHashMap<ShipRole, Integer> roster = new java.util.LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return roster;
        String[] entries = encoded.split("[;,\\n\\r]+");
        for (String rawEntry : entries) {
            if (rawEntry == null) continue;
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("[:=]", 2);
            if (parts.length != 2) continue;
            String roleId = parts[0].trim().toUpperCase(Locale.US);
            String countText = parts[1].trim();
            if (roleId.isEmpty() || countText.isEmpty()) continue;
            try {
                ShipRole role = ShipRole.valueOf(roleId);
                int count = Integer.parseInt(countText);
                if (count <= 0) continue;
                roster.merge(role, count, Integer::sum);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return roster;
    }

    private static java.util.LinkedHashMap<ShipRole, Integer> defaultCustomBattleRoster(boolean friendly) {
        java.util.LinkedHashMap<ShipRole, Integer> roster = new java.util.LinkedHashMap<>();
        if (friendly) {
            roster.put(ShipRole.FRIGATE, 4);
            roster.put(ShipRole.CIWS_CORVETTE, 2);
            roster.put(ShipRole.LIGHT_CRUISER, 2);
            roster.put(ShipRole.BATTLECRUISER, 1);
            roster.put(ShipRole.CARRIER, 1);
            roster.put(ShipRole.SUPERSHIP, 1);
        } else {
            roster.put(ShipRole.FRIGATE, 6);
            roster.put(ShipRole.MISSILE_BOAT, 3);
            roster.put(ShipRole.LIGHT_CRUISER, 2);
            roster.put(ShipRole.BATTLESHIP, 1);
            roster.put(ShipRole.INTERDICTION_TITAN, 1);
            roster.put(ShipRole.MOTHERSHIP, 1);
        }
        return roster;
    }

    private static void spawnCustomBattleRoster(GameContext ctx,
                                                Faction faction,
                                                Ship base,
                                                java.util.LinkedHashMap<ShipRole, Integer> roster,
                                                boolean playerSide) {
        if (ctx == null || faction == null || base == null || roster == null || roster.isEmpty()) return;

        java.util.ArrayList<ShipRole> roles = new java.util.ArrayList<>();
        for (java.util.Map.Entry<ShipRole, Integer> entry : roster.entrySet()) {
            ShipRole role = entry.getKey();
            int count = (entry.getValue() == null) ? 0 : entry.getValue();
            for (int i = 0; i < count; i++) roles.add(role);
        }
        roles.sort((a, b) -> Integer.compare(customBattleSpawnWeight(b), customBattleSpawnWeight(a)));
        if (roles.isEmpty()) return;

        double centerX = ctx.WORLD_W * 0.5;
        double centerY = ctx.WORLD_H * 0.5;
        double dx = centerX - base.x;
        double dy = centerY - base.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) len = 1.0;
        double nx = dx / len;
        double ny = dy / len;
        double tx = -ny;
        double ty = nx;

        int columns = Math.max(4, (int) Math.ceil(Math.sqrt(roles.size())));
        for (int i = 0; i < roles.size(); i++) {
            ShipRole role = roles.get(i);
            int row = i / columns;
            int col = i % columns;
            double lane = col - (columns - 1) * 0.5;
            double size = customBattleSpacingScale(role);
            double forward = 340.0 + row * (175.0 * size);
            double lateral = lane * (150.0 * size);
            double jitterX = (ctx.rng.nextDouble() - 0.5) * 26.0;
            double jitterY = (ctx.rng.nextDouble() - 0.5) * 26.0;
            double x = base.x + nx * forward + tx * lateral + jitterX;
            double y = base.y + ny * forward + ty * lateral + jitterY;
            Ship ship = spawnExactTeamShip(ctx, role, faction, x, y);
            if (ship == null) continue;
            if (playerSide) {
                ship.angle = Math.atan2(centerY - ship.y, centerX - ship.x);
            } else {
                ship.angle = Math.atan2(base.y - ship.y, base.x - ship.x);
            }
        }
    }

    private static int customBattleSpawnWeight(ShipRole role) {
        if (role == null) return 0;
        return requiredHangarTierForRole(role) * 100 + Math.max(0, roleMaxCountBias(role));
    }

    private static int roleMaxCountBias(ShipRole role) {
        if (role == null) return 0;
        return switch (role) {
            case MOTHERSHIP, BASE, MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, FLEET_TELEPORTER_TITAN,
                    SHIELD_BASTION_TITAN, ARTILLERY_TITAN, INTERDICTION_TITAN,
                    VANGUARD_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    CARRIER_SUPPORT_TITAN, BULWARK_TITAN, TRANSPORT_TITAN -> 12;
            case SUPERSHIP, DREADNOUGHT, BATTLESHIP, BATTLECRUISER -> 8;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER, CARRIER, DRONE_CARRIER -> 5;
            default -> 1;
        };
    }

    private static double customBattleSpacingScale(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case BASE, MOTHERSHIP, MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, FLEET_TELEPORTER_TITAN,
                    SHIELD_BASTION_TITAN, ARTILLERY_TITAN, INTERDICTION_TITAN,
                    VANGUARD_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    CARRIER_SUPPORT_TITAN, BULWARK_TITAN, TRANSPORT_TITAN -> 1.55;
            case SUPERSHIP, DREADNOUGHT, BATTLESHIP, BATTLECRUISER -> 1.25;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER, CARRIER, DRONE_CARRIER -> 1.1;
            default -> 1.0;
        };
    }

    private static Ship spawnExactTeamShip(GameContext ctx, ShipRole role, Faction faction, double x, double y) {
        if (ctx == null || role == null || faction == null) return null;
        if (role == ShipRole.MINER && TeamSystem.countAliveMiners(ctx, faction) >= MAX_MINERS_PER_FACTION) {
            return null;
        }
        double sx = GameMath.clamp(x, 20, ctx.WORLD_W - 20);
        double sy = GameMath.clamp(y, 20, ctx.WORLD_H - 20);
        Ship ship = new FleetShip(role, faction, sx, sy);
        ctx.ships.add(ship);
        try { DoctrineRegistry.applyToShip(ship); } catch (Throwable ignored) {}
        if (role == ShipRole.MINER) logMinerSpawn(ship);
        return ship;
    }

    /** Exact catalog spawn used by campaign encounters, migration validation, and faction editors. */
    public static Ship spawnCatalogShip(GameContext ctx, ShipRole role, Faction faction, double x, double y) {
        return spawnExactTeamShip(ctx, role, faction, x, y);
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

        if (ctx.rng.nextDouble() < 0.26) {
            ox = (ctx.rng.nextDouble() - 0.5) * 230.0;
            oy = (ctx.rng.nextDouble() - 0.5) * 230.0;
            spawnTeamShip(ctx, ShipRole.ARTILLERY_SHIP, team, base.x + ox, base.y + oy);
        }

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
        loadShowcaseTeam(ctx, Faction.forTeamId(ctx.config.playerTeamId));
    }

    public static void loadShowcaseTeam(GameContext ctx, Faction faction) {
        if (ctx == null) return;
        Faction showcaseFaction = normalizeShowcaseFaction(faction);
        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.teamBases.clear();
        ctx.baseUpgrades.clear();
        ctx.allyBase = null;
        ctx.enemyBase = null;

        ShipRole[] roles = ShipRole.values();
        int rolesPerFaction = roles.length;
        int blockCols = Math.max(4, (int) Math.ceil(Math.sqrt(rolesPerFaction)));
        int blockRows = (int) Math.ceil(rolesPerFaction / (double) blockCols);
        double spacing = 210.0;
        double blockW = (blockCols - 1) * spacing;
        double blockH = (blockRows - 1) * spacing;
        double galleryW = blockW;
        double galleryH = blockH;
        double startX = GameMath.clamp((ctx.WORLD_W - galleryW) * 0.5, 120.0, ctx.WORLD_W - 120.0 - galleryW);
        double startY = GameMath.clamp((ctx.WORLD_H - galleryH - 320.0) * 0.5, 180.0, ctx.WORLD_H - 180.0 - galleryH);

        double galleryCenterX = startX + galleryW * 0.5;
        double galleryCenterY = startY + galleryH * 0.5;
        double playerX = GameMath.clamp(galleryCenterX, 120.0, ctx.WORLD_W - 120.0);
        double playerY = GameMath.clamp(startY - 220.0, 120.0, ctx.WORLD_H - 120.0);
        ctx.player = new Player(ShipRole.FRIGATE, playerX, playerY);
        ctx.player.name = "Showcase Camera";
        ctx.player.vx = 0;
        ctx.player.vy = 0;
        ctx.player.angle = 0.0; // face right
        ctx.zoom = 0.28;
        ctx.cameraOffsetX = galleryCenterX - playerX;
        ctx.cameraOffsetY = galleryCenterY - playerY;
        ctx.ships.add(ctx.player);

        double maxShowcaseY = playerY;

        for (int roleIndex = 0; roleIndex < roles.length; roleIndex++) {
            ShipRole role = roles[roleIndex];
            int row = roleIndex / blockCols;
            int col = roleIndex % blockCols;
            double sx = GameMath.clamp(startX + col * spacing, 80.0, ctx.WORLD_W - 80.0);
            double sy = GameMath.clamp(startY + row * spacing, 80.0, ctx.WORLD_H - 180.0);

            Ship s = new FleetShip(role, showcaseFaction, sx, sy);
            s.name = showcaseShipName(showcaseFaction, role);
            s.vx = 0;
            s.vy = 0;
            s.angle = 0.0; // face right

            ctx.ships.add(s);
            if (sy > maxShowcaseY) maxShowcaseY = sy;
        }

        double projectileY = Math.min(ctx.WORLD_H - 140.0, maxShowcaseY + 120.0);
        double projectileStartX = GameMath.clamp(startX + galleryW * 0.5 - 2.0 * 220.0, 120.0, ctx.WORLD_W - 120.0);
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
        ctx.command.showcaseFaction = showcaseFaction;
        ctx.eventBanner = "SHOWCASE MODE  -  " + showcaseFaction.teamName().toUpperCase(Locale.US)
                + " TEAM  -  AI OFF";
        ctx.eventBannerT = 9999.0;
    }

    private static Faction normalizeShowcaseFaction(Faction faction) {
        if (faction == Faction.PLAYER) return Faction.ALLY;
        if (faction == Faction.ENEMY || faction == Faction.TEAM_C || faction.isYellowLineage()) return faction;
        return Faction.ALLY;
    }

    private static String showcaseShipName(Faction faction, ShipRole role) {
        String team = (faction == null) ? "Unknown" : faction.teamName();
        return team + " " + showcaseRoleTitle(role);
    }

    private static String showcaseRoleTitle(ShipRole role) {
        if (role == null) return "Ship";
        TitanArchetype titan = TitanArchetype.fromShipRole(role);
        if (titan != null) return titan.displayName();
        if (role == ShipRole.MOTHERSHIP) return "Mothership";
        String raw = role.name().toLowerCase(Locale.US).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
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
        ctx.command.shootingRangeTitanArchetype = null;

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

        Faction targetFaction = defaultShootingRangeTargetFaction(ctx.player.faction);
        activateShootingRange(ctx, px, py, targetFaction);

        ctx.credits = 999_999;
        ctx.enemyWaveTimer = Double.POSITIVE_INFINITY;
        ctx.nextEventTimer = Double.POSITIVE_INFINITY;
        ctx.minerReinforcementTimer = Double.POSITIVE_INFINITY;
        ctx.eventBannerT = Math.max(ctx.eventBannerT, 6.0);
    }

    static void populateShootingRangeTargets(GameContext ctx, double originX, double originY, Faction faction) {
        if (ctx == null || faction == null) return;
        for (ShootingRangeTargetSpec spec : shootingRangeLayout(faction, currentShootingRangeTitanLayout(ctx))) {
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

    public static void activateShootingRange(GameContext ctx, double originX, double originY, Faction faction) {
        if (ctx == null || faction == null) return;
        ctx.command.shootingRangeOriginX = originX;
        ctx.command.shootingRangeOriginY = originY;
        replaceShootingRangeTargets(ctx, faction);
    }

    public static boolean hasShootingRangeTargets(GameContext ctx) {
        if (ctx == null) return false;
        return !shootingRangeTargetSlotsFor(ctx).isEmpty();
    }

    public static boolean setShootingRangeTargetFaction(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null || ctx.player == null) return false;
        if (ctx.player.faction != null && ctx.player.faction.isFriendlyTo(faction)) return false;
        if (!Double.isFinite(ctx.command.shootingRangeOriginX) || !Double.isFinite(ctx.command.shootingRangeOriginY)) return false;
        replaceShootingRangeTargets(ctx, faction);
        return true;
    }

    public static boolean setShootingRangeTitanLayout(GameContext ctx, TitanArchetype archetype) {
        if (!canSwitchShootingRangeLayout(ctx) || archetype == null) return false;
        ctx.command.shootingRangeTitanArchetype = archetype;
        replaceShootingRangeTargets(ctx, ctx.command.shootingRangeTargetFaction);
        return true;
    }

    public static boolean clearShootingRangeTitanLayout(GameContext ctx) {
        if (!canSwitchShootingRangeLayout(ctx)) return false;
        ctx.command.shootingRangeTitanArchetype = null;
        replaceShootingRangeTargets(ctx, ctx.command.shootingRangeTargetFaction);
        return true;
    }

    private static java.util.Map<String, ShootingRangeTargetSlot> shootingRangeTargetSlotsFor(GameContext ctx) {
        return SHOOTING_RANGE_TARGET_SLOTS.computeIfAbsent(ctx, k -> new java.util.LinkedHashMap<>());
    }

    private static void clearShootingRangeTargetSlots(GameContext ctx) {
        if (ctx == null) return;
        shootingRangeTargetSlotsFor(ctx).clear();
    }

    private static void replaceShootingRangeTargets(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null) return;
        removeShootingRangeTargets(ctx);
        clearShootingRangeTargetSlots(ctx);
        ctx.command.shootingRangeTargetFaction = faction;
        populateShootingRangeTargets(ctx, ctx.command.shootingRangeOriginX, ctx.command.shootingRangeOriginY, faction);
        ctx.eventBanner = shootingRangeBanner(faction, currentShootingRangeTitanLayout(ctx));
        ctx.eventBannerT = 2.4;
    }

    private static void removeShootingRangeTargets(GameContext ctx) {
        if (ctx == null || ctx.ships == null) return;
        java.util.Set<String> labels = new java.util.HashSet<>(shootingRangeTargetSlotsFor(ctx).keySet());
        if (labels.isEmpty()) return;
        ctx.ships.removeIf(s -> s != null && s != ctx.player && labels.contains(s.name));
    }

    private static Faction defaultShootingRangeTargetFaction(Faction playerFaction) {
        if (playerFaction != null && playerFaction.isFriendlyTo(Faction.ENEMY)) return Faction.ALLY;
        return Faction.ENEMY;
    }

    private static boolean canSwitchShootingRangeLayout(GameContext ctx) {
        if (ctx == null || ctx.command == null) return false;
        if (ctx.config == null || ctx.config.mode != GameMode.SHOOTING_RANGE) return false;
        return Double.isFinite(ctx.command.shootingRangeOriginX) && Double.isFinite(ctx.command.shootingRangeOriginY);
    }

    private static TitanArchetype currentShootingRangeTitanLayout(GameContext ctx) {
        if (ctx == null || ctx.command == null) return null;
        return ctx.command.shootingRangeTitanArchetype;
    }

    private static String shootingRangeBanner(Faction faction, TitanArchetype titanArchetype) {
        String label = (faction == null) ? "UNKNOWN" : faction.teamName().toUpperCase();
        if (titanArchetype == null) {
            return "SHOOTING RANGE  -  TARGETS: " + label
                    + "  (1-4 FACTION / SHIFT+1-0,Q,E,R TITANS / CTRL+SHIFT+1-0,Q,E,R,M PLAYER HULL)";
        }
        return "SHOOTING RANGE  -  " + label + " / " + titanArchetype.displayName()
                + "  (SHIFT+BACKSPACE RESET / CTRL+SHIFT+M MOTHERSHIP)";
    }

    private static void registerShootingRangeTarget(GameContext ctx, ShipRole role, Faction faction, double x, double y, String label, boolean keepShields) {
        if (ctx == null || role == null || faction == null || label == null || label.isBlank()) return;
        shootingRangeTargetSlotsFor(ctx).put(label, new ShootingRangeTargetSlot(role, faction, label, x, y, keepShields));
    }

    private static Ship spawnRangeTarget(GameContext ctx, ShipRole role, Faction faction, double x, double y, String label, boolean keepShields) {
        double sx = GameMath.clamp(x, 20, ctx.WORLD_W - 20);
        double sy = GameMath.clamp(y, 20, ctx.WORLD_H - 20);
        Ship s = new FleetShip(role, faction, sx, sy);
        try { DoctrineRegistry.applyToShip(s); } catch (Throwable ignored) {}

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
        s.hasSuperweapon = false;
        s.shieldActive = s.shieldMax > 0.0;
        s.shield = s.shieldActive ? s.shieldMax : 0.0;

        ctx.ships.add(s);
        registerShootingRangeTarget(ctx, role, faction, sx, sy, label, keepShields);
        return s;
    }

    private static java.util.List<ShootingRangeTargetSpec> shootingRangeLayout(Faction faction, TitanArchetype titanArchetype) {
        if (titanArchetype != null) {
            return titanShootingRangeLayout(titanArchetype);
        }
        return defaultShootingRangeLayout(faction);
    }

    private static java.util.List<ShootingRangeTargetSpec> defaultShootingRangeLayout(Faction faction) {
        java.util.List<ShootingRangeTargetSpec> out = new java.util.ArrayList<>();
        ShipRole[] roster = {
                ShipRole.FIGHTER, ShipRole.DRONE, ShipRole.PD_CRAFT, ShipRole.BOMBER,
                ShipRole.PATROL, ShipRole.PICKET, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE,
                ShipRole.ARTILLERY_SHIP, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE, ShipRole.MINER,
                ShipRole.LIGHT_CRUISER, ShipRole.MEDIUM_CRUISER, ShipRole.CRUISER, ShipRole.HAULER,
                ShipRole.BATTLECRUISER, ShipRole.BATTLESHIP, ShipRole.DREADNOUGHT, ShipRole.SUPERSHIP,
                ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.TRANSPORT,
                ShipRole.TRANSPORT_TITAN, ShipRole.BULWARK_TITAN, ShipRole.CARRIER_SUPPORT_TITAN,
                ShipRole.VANGUARD_TITAN, ShipRole.INTERDICTION_TITAN, ShipRole.COMMAND_INTEL_TITAN,
                ShipRole.BOARDING_RECOVERY_TITAN, ShipRole.ARTILLERY_TITAN, ShipRole.SHIELD_BASTION_TITAN,
                ShipRole.FLEET_TELEPORTER_TITAN, ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN,
                ShipRole.ELITE_REINFORCEMENTS_TITAN, ShipRole.MOBILE_STATION_TITAN,
                ShipRole.HYPERWEAPON_TITAN, ShipRole.STATIC_TURRET, ShipRole.STATIC_TURRET, ShipRole.BASE, ShipRole.MOTHERSHIP
        };
        final int columns = 5;
        final double startX = 420.0;
        final double startY = -520.0;
        final double colStep = 520.0;
        final double rowStep = 220.0;
        final int titanColumns = 3;
        final double titanStartX = 2100.0;
        final double titanStartY = -1400.0;
        final double titanColStep = 860.0;
        final double titanRowStep = 700.0;
        int structureIndex = 1;
        int titanIndex = 0;
        for (int i = 0; i < roster.length; i++) {
            ShipRole role = roster[i];
            if (role == null) continue;
            double dx;
            double dy;
            if (role.isTitanOrMothership()) {
                int col = titanIndex % titanColumns;
                int row = titanIndex / titanColumns;
                dx = titanStartX + col * titanColStep;
                dy = titanStartY + row * titanRowStep;
                titanIndex++;
            } else {
                int col = i % columns;
                int row = i / columns;
                dx = startX + col * colStep;
                dy = startY + row * rowStep;
            }
            String label;
            if (role == ShipRole.STATIC_TURRET) {
                label = shootingRangeRoleLabel(faction, role, structureIndex++);
            } else {
                label = shootingRangeRoleLabel(faction, role, 0);
            }
            out.add(spec(role, dx, dy, label, shootingRangeKeepShields(role)));
        }
        return out;
    }

    private static java.util.List<ShootingRangeTargetSpec> titanShootingRangeLayout(TitanArchetype archetype) {
        if (archetype == null) return defaultShootingRangeLayout(Faction.ENEMY);
        return switch (archetype) {
            case TRANSPORT -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.LIGHT_CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.FRIGATE, ShipRole.FRIGATE,
                    ShipRole.CIWS_CORVETTE, ShipRole.CIWS_CORVETTE,
                    ShipRole.TRANSPORT, ShipRole.HAULER,
                    ShipRole.MINER, ShipRole.DRONE_CARRIER);
            case BULWARK -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER,
                    ShipRole.CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE,
                    ShipRole.CIWS_CORVETTE, ShipRole.CARRIER);
            case CARRIER_SUPPORT -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.DRONE_CARRIER, ShipRole.LIGHT_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.FRIGATE, ShipRole.FIGHTER,
                    ShipRole.BOMBER, ShipRole.DRONE,
                    ShipRole.PD_CRAFT, ShipRole.TRANSPORT);
            case VANGUARD -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLECRUISER, ShipRole.CRUISER,
                    ShipRole.CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.FRIGATE, ShipRole.MISSILE_BOAT,
                    ShipRole.PATROL, ShipRole.STEALTH_SHIP);
            case INTERDICTION -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.CRUISER, ShipRole.CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.FRIGATE, ShipRole.MISSILE_BOAT,
                    ShipRole.CIWS_CORVETTE, ShipRole.STEALTH_SHIP,
                    ShipRole.PATROL, ShipRole.DRONE_CARRIER);
            case COMMAND_INTEL -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.ARTILLERY_SHIP, ShipRole.ARTILLERY_SHIP,
                    ShipRole.CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.FRIGATE, ShipRole.FRIGATE,
                    ShipRole.PICKET, ShipRole.PATROL,
                    ShipRole.CIWS_CORVETTE, ShipRole.DRONE_CARRIER);
            case BOARDING_RECOVERY -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE,
                    ShipRole.MISSILE_BOAT, ShipRole.TRANSPORT,
                    ShipRole.HAULER, ShipRole.DRONE_CARRIER);
            case ARTILLERY -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLECRUISER, ShipRole.CRUISER,
                    ShipRole.ARTILLERY_SHIP, ShipRole.ARTILLERY_SHIP,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.CIWS_CORVETTE, ShipRole.CIWS_CORVETTE,
                    ShipRole.PICKET, ShipRole.DREADNOUGHT);
            case SHIELD_BASTION -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLESHIP, ShipRole.CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.FRIGATE, ShipRole.FRIGATE,
                    ShipRole.CIWS_CORVETTE, ShipRole.CIWS_CORVETTE,
                    ShipRole.DRONE_CARRIER, ShipRole.TRANSPORT);
            case FLEET_TELEPORTER -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLECRUISER, ShipRole.CRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.FRIGATE, ShipRole.FRIGATE,
                    ShipRole.PICKET, ShipRole.CIWS_CORVETTE,
                    ShipRole.TRANSPORT, ShipRole.DRONE_CARRIER);
            case ELITE_SUPERSHIP_COMMAND -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.SUPERSHIP, ShipRole.SUPERSHIP,
                    ShipRole.SUPERSHIP, ShipRole.SUPERSHIP,
                    ShipRole.SUPERSHIP);
            case ELITE_REINFORCEMENTS -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.CIWS_CORVETTE);
            case MOBILE_STATION -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.CARRIER, ShipRole.DRONE_CARRIER,
                    ShipRole.TRANSPORT, ShipRole.HAULER,
                    ShipRole.LIGHT_CRUISER, ShipRole.LIGHT_CRUISER,
                    ShipRole.FRIGATE, ShipRole.FRIGATE,
                    ShipRole.CIWS_CORVETTE, ShipRole.STATIC_TURRET);
            case HYPERWEAPON -> titanFormation(archetype, archetype.shipRole(),
                    ShipRole.BATTLESHIP, ShipRole.CRUISER,
                    ShipRole.ARTILLERY_SHIP, ShipRole.ARTILLERY_SHIP,
                    ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE,
                    ShipRole.CIWS_CORVETTE, ShipRole.CIWS_CORVETTE,
                    ShipRole.PICKET, ShipRole.DREADNOUGHT);
        };
    }

    private static java.util.List<ShootingRangeTargetSpec> titanFormation(TitanArchetype archetype,
                                                                          ShipRole titanRole,
                                                                          ShipRole... deploymentRoles) {
        java.util.ArrayList<ShootingRangeTargetSpec> out = new java.util.ArrayList<>();
        out.add(spec(titanRole, 2180, 0, titanCoreLabel(archetype), true));

        double[][] slots = titanFormationSlots(deploymentRoles == null ? 0 : deploymentRoles.length);
        if (deploymentRoles == null) return out;
        for (int i = 0; i < deploymentRoles.length && i < slots.length; i++) {
            ShipRole role = deploymentRoles[i];
            if (role == null) continue;
            out.add(spec(role,
                    slots[i][0],
                    slots[i][1],
                    titanDeployLabel(archetype, role, i + 1),
                    titanKeepShields(role)));
        }
        return out;
    }

    private static double[][] titanFormationSlots(int count) {
        if (count <= 5) {
            return new double[][]{
                    {1400, -280},
                    {1625, -120},
                    {1845, 0},
                    {1625, 120},
                    {1400, 280}
            };
        }
        return new double[][]{
                {1280, -360},
                {1455, -220},
                {1620, -90},
                {1790, -280},
                {1965, -140},
                {1280, 360},
                {1455, 220},
                {1620, 90},
                {1790, 280},
                {1965, 140}
        };
    }

    private static String titanCoreLabel(TitanArchetype archetype) {
        if (archetype == null) return "TITAN CORE";
        return archetype.displayName() + " Core";
    }

    private static String titanDeployLabel(TitanArchetype archetype, ShipRole role, int index) {
        String titanLabel = (archetype == null) ? "Titan" : archetype.displayName();
        String roleLabel = (role == null) ? "Ship" : role.name().replace('_', ' ');
        String slot = (index < 10) ? "0" + index : Integer.toString(index);
        return titanLabel + " Deploy " + slot + " " + roleLabel;
    }

    private static boolean titanKeepShields(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case FRIGATE, ARTILLERY_SHIP, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER,
                    BATTLESHIP, DREADNOUGHT, SUPERSHIP, CARRIER, DRONE_CARRIER, BASE -> true;
            default -> false;
        };
    }

    private static boolean shootingRangeKeepShields(ShipRole role) {
        if (role == null) return false;
        return switch (role) {
            case FRIGATE, ARTILLERY_SHIP, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER,
                    BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP,
                    CARRIER, DRONE_CARRIER, TRANSPORT,
                    TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                    INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                    MOBILE_STATION_TITAN, HYPERWEAPON_TITAN, BASE, MOTHERSHIP -> true;
            default -> false;
        };
    }

    private static String shootingRangeRoleLabel(Faction faction, ShipRole role, int ordinal) {
        String factionLabel = (faction == null) ? "Unknown" : faction.teamName();
        String roleLabel = (role == null) ? "Target" : role.name().replace('_', ' ');
        if (ordinal > 0) {
            return factionLabel + " " + roleLabel + " " + ordinal;
        }
        return factionLabel + " " + roleLabel;
    }

    private static ShootingRangeTargetSpec spec(ShipRole role, double dx, double dy, String label, boolean keepShields) {
        return new ShootingRangeTargetSpec(role, dx, dy, label, keepShields);
    }
}
