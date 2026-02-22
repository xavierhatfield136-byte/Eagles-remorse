public final class EconomySystem {
    private EconomySystem(){}

    private static final double PERIODIC_MINER_SPAWN_INTERVAL = 20.0;
    private static final int PERIODIC_MINERS_PER_TEAM = 2;

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;

        // Salvage drift
        for (int i = ctx.salvage.size() - 1; i >= 0; i--) {
            Salvage s = ctx.salvage.get(i);
            s.update(dt);
            if (s.life <= 0) ctx.salvage.remove(i);
        }

        // Mining for player (hold F)
        if (ctx.miningKeyDown && ctx.player != null) {
            doMining(ctx, ctx.player, dt);
        }

        // NPC mining & deposits
        handleNpcMiningAndDeposits(ctx, dt);

        // Periodic miner reinforcements for teams still in the match.
        updatePeriodicMinerReinforcements(ctx, dt);

        // Mode win checks
        if (ctx.config.mode == GameMode.RESOURCE_RUSH) checkResourceRushWin(ctx);
        if (ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) checkFourTeamDominationWin(ctx);
    }

    private static void doMining(GameContext ctx, Ship miner, double dt) {
        Asteroid a = findBestAsteroidNear(ctx, miner.x, miner.y, 220);
        if (a == null) return;

        double rate = getMiningRate(miner) * ctx.miningMul * ctx.miningBaseMul;
        rate *= CampaignSystem.miningRateMul(ctx);
        double mined = mineAsteroid(a, rate * dt);
        if (mined > 0) {
            addOreToShip(miner, mined);
            try { VFX.spawnEngineWisp(miner.x, miner.y, miner.vx, miner.vy); } catch (Throwable ignored) {}
        }
    }

    public static Asteroid findBestAsteroidNear(GameContext ctx, double x, double y, double maxDist) {
        Asteroid best = null;
        double bestD2 = maxDist * maxDist;
        for (Asteroid a : ctx.asteroids) {
            double ore = getAsteroidOre(a);
            if (ore <= 0.01) continue;
            double d2 = GameMath.dist2(x, y, a.x, a.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = a;
            }
        }
        return best;
    }

    public static Ship getDockedFriendlyBase(GameContext ctx) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (!TeamSystem.isFriendlyToPlayer(ctx, s.faction)) continue;
            if (GameMath.dist2(ctx.player.x, ctx.player.y, s.x, s.y) < (120 * 120)) return s;
        }
        return null;
    }

    private static void handleNpcMiningAndDeposits(GameContext ctx, double dt) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.MINER) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            updateMinerState(ctx, s, dt);
        }
    }

    private static void updatePeriodicMinerReinforcements(GameContext ctx, double dt) {
        if (ctx == null) return;
        ctx.minerReinforcementTimer -= Math.max(0.0, dt);
        while (ctx.minerReinforcementTimer <= 0.0) {
            ctx.minerReinforcementTimer += PERIODIC_MINER_SPAWN_INTERVAL;
            spawnPeriodicMinersForAliveTeams(ctx);
        }
    }

    private static void spawnPeriodicMinersForAliveTeams(GameContext ctx) {
        java.util.EnumSet<Faction> teams = java.util.EnumSet.noneOf(Faction.class);
        teams.addAll(ctx.teamBases.keySet());
        if (ctx.allyBase != null) teams.add(Faction.ALLY);
        if (ctx.enemyBase != null) teams.add(Faction.ENEMY);

        for (Faction team : teams) {
            if (team == null) continue;
            if (!TeamSystem.isTeamAlive(ctx, team)) continue;

            Ship base = TeamSystem.getBaseForTeam(ctx, team);
            if (base == null) continue;
            if (!base.alive || base.dying || base.hp <= 0) continue;

            spawnMinersAtBase(ctx, team, base, PERIODIC_MINERS_PER_TEAM);
        }
    }

    private static void spawnMinersAtBase(GameContext ctx, Faction team, Ship base, int count) {
        int n = Math.max(0, count);
        for (int i = 0; i < n; i++) {
            double a = ctx.rng.nextDouble() * Math.PI * 2.0;
            double r = base.radius + 80.0 + ctx.rng.nextDouble() * 70.0;
            double sx = base.x + Math.cos(a) * r;
            double sy = base.y + Math.sin(a) * r;

            Ship miner = SpawnSystem.spawnTeamShip(ctx, ShipRole.MINER, team, sx, sy);
            if (miner == null) continue;
            miner.minerHomeBase = base;
            miner.minerState = Ship.MinerState.SEEK_ASTEROID;
        }
    }

    // ------------------------------
    // Miner AI state machine
    // ------------------------------

    private static final double MINER_SEARCH_RADIUS = 2400.0;
    private static final double MINER_DEPOSIT_RANGE = 120.0;
    private static final double MINER_FULL_FRAC = 0.90;

    private static void updateMinerState(GameContext ctx, Ship s, double dt) {
        // Ensure we have a home base
        if (s.minerHomeBase == null || !s.minerHomeBase.alive || s.minerHomeBase.hp <= 0) {
            s.minerHomeBase = findHomeBaseFor(ctx, s);
        }

        boolean hasBase = (s.minerHomeBase != null && s.minerHomeBase.alive && s.minerHomeBase.hp > 0);
        if (!hasBase && s.minerState != Ship.MinerState.IDLE) {
            s.minerState = Ship.MinerState.IDLE;
        }

        // Periodic debug logging (once per second)
        if (DevTools.isDebugOverlay()) {
            s.minerDebugTimer += dt;
            if (s.minerDebugTimer >= 1.0) {
                s.minerDebugTimer = 0.0;
                logMinerStatus(ctx, s);
            }
        }

        double cargo = getShipOre(s);
        double cargoMax = Math.max(1, s.cargoMax);
        boolean cargoFullEnough = cargo >= cargoMax * MINER_FULL_FRAC;

        if (cargoFullEnough && s.minerState != Ship.MinerState.RETURN_TO_BASE && s.minerState != Ship.MinerState.DEPOSIT) {
            s.minerState = Ship.MinerState.RETURN_TO_BASE;
        }

        switch (s.minerState) {
            case SEEK_ASTEROID -> {
                Asteroid a = findBestAsteroidForMiner(ctx, s, MINER_SEARCH_RADIUS);
                if (a == null) {
                    s.minerTarget = null;
                    s.minerDebugNote = findNoAsteroidReason(ctx);
                    aiWander(s, dt, ctx.WORLD_W, ctx.WORLD_H);
                    break;
                }
                s.minerTarget = a;
                s.minerDebugNote = "";
                s.minerState = Ship.MinerState.MOVE_TO_ASTEROID;
            }
            case MOVE_TO_ASTEROID -> {
                Asteroid a = s.minerTarget;
                if (a == null || getAsteroidOre(a) <= 0.01) {
                    s.minerTarget = null;
                    s.minerDebugNote = "target depleted";
                    s.minerState = Ship.MinerState.SEEK_ASTEROID;
                    break;
                }
                s.minerDebugNote = "";
                steerTo(s, a.x, a.y, dt);
                if (inMiningRange(s, a)) {
                    s.minerState = Ship.MinerState.MINING;
                }
            }
            case MINING -> {
                Asteroid a = s.minerTarget;
                if (a == null || getAsteroidOre(a) <= 0.01) {
                    s.minerTarget = null;
                    s.minerDebugNote = "target depleted";
                    s.minerState = Ship.MinerState.SEEK_ASTEROID;
                    break;
                }
                s.minerDebugNote = "";

                if (!inMiningRange(s, a)) {
                    s.minerState = Ship.MinerState.MOVE_TO_ASTEROID;
                    break;
                }

                double dtScaled = dt * ctx.miningMul * ctx.miningBaseMul;
                dtScaled *= CampaignSystem.miningRateMul(ctx);
                int mined = s.tryMine(a, dtScaled);
                if (mined > 0) {
                    try { VFX.spawnEngineWisp(s.x, s.y, s.vx, s.vy); } catch (Throwable ignored) {}
                }
                double newCargo = getShipOre(s);
                if (newCargo >= cargoMax * MINER_FULL_FRAC) {
                    s.minerState = Ship.MinerState.RETURN_TO_BASE;
                }
            }
            case RETURN_TO_BASE -> {
                if (!hasBase) {
                    s.minerState = Ship.MinerState.IDLE;
                    break;
                }
                Ship base = s.minerHomeBase;
                s.minerDebugNote = "";
                steerTo(s, base.x, base.y, dt);
                if (inDepositRange(s, base)) {
                    s.minerState = Ship.MinerState.DEPOSIT;
                }
            }
            case DEPOSIT -> {
                if (!hasBase) {
                    s.minerState = Ship.MinerState.IDLE;
                    break;
                }
                Ship base = s.minerHomeBase;
                s.minerDebugNote = "";
                if (inDepositRange(s, base)) {
                    int moved = s.depositCargoTo(base);
                    if (moved > 0 && TeamSystem.isFriendlyToPlayer(ctx, s.faction)) {
                        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul;
                        priceMul *= CampaignSystem.oreCreditMul(ctx);
                        ctx.credits += (int) Math.round(moved * GameContext.ORE_PRICE * priceMul);
                    }
                    // repair a bit (hp/hpMax are ints in this codebase)
                    int heal = (int) Math.round(18 * dt);
                    if (heal > 0) s.hp = Math.min(s.hpMax, s.hp + heal);
                }
                s.minerState = Ship.MinerState.SEEK_ASTEROID;
            }
            case IDLE -> {
                // If we regain a base, resume work.
                s.minerDebugNote = hasBase ? "" : "no base";
                if (hasBase) s.minerState = Ship.MinerState.SEEK_ASTEROID;
            }
        }
    }

    private static Ship findHomeBaseFor(GameContext ctx, Ship miner) {
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (s.faction != miner.faction) continue;
            if (!s.alive || s.hp <= 0) continue;
            double d2 = GameMath.dist2(miner.x, miner.y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    private static Asteroid findBestAsteroidForMiner(GameContext ctx, Ship miner, double maxDist) {
        Asteroid best = null;
        double bestD2 = maxDist * maxDist;
        if (ctx.asteroids == null || ctx.asteroids.isEmpty()) return null;
        for (Asteroid a : ctx.asteroids) {
            double ore = getAsteroidOre(a);
            if (ore <= 0.01) continue;
            double d2 = GameMath.dist2(miner.x, miner.y, a.x, a.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = a;
            }
        }
        return best;
    }

    private static String findNoAsteroidReason(GameContext ctx) {
        if (ctx.asteroids == null || ctx.asteroids.isEmpty()) return "no asteroids";
        for (Asteroid a : ctx.asteroids) {
            if (getAsteroidOre(a) > 0.01) return "";
        }
        return "all ore depleted";
    }

    private static boolean inMiningRange(Ship s, Asteroid a) {
        double dx = a.x - s.x;
        double dy = a.y - s.y;
        double reach = Math.max(0.0, s.miningRange) + s.radius + a.radius;
        return (dx * dx + dy * dy) <= (reach * reach);
    }

    private static boolean inDepositRange(Ship s, Ship base) {
        double dx = base.x - s.x;
        double dy = base.y - s.y;
        double reach = MINER_DEPOSIT_RANGE + s.radius + base.radius;
        return (dx * dx + dy * dy) <= (reach * reach);
    }

    private static void steerTo(Ship s, double tx, double ty, double dt) {
        double dx = tx - s.x;
        double dy = ty - s.y;
        double len = Math.sqrt(dx * dx + dy * dy) + 1e-9;
        double speed = Math.max(55.0, s.desiredSpeed);
        double vx = (dx / len) * speed;
        double vy = (dy / len) * speed;
        if (dt <= 0) {
            s.vx = 0;
            s.vy = 0;
            return;
        }
        s.vx = vx * dt;
        s.vy = vy * dt;
        s.angle = Math.atan2(vy, vx);
    }

    private static void logMinerStatus(GameContext ctx, Ship s) {
        StringBuilder sb = new StringBuilder();
        sb.append("MINER #").append(s.id).append(" ")
                .append(s.minerState).append(" cargo=").append(s.cargo).append("/").append(s.cargoMax);

        if (s.minerTarget != null) {
            double d = Math.hypot(s.minerTarget.x - s.x, s.minerTarget.y - s.y);
            sb.append(" distA=").append((int) Math.round(d));
        } else {
            sb.append(" distA=?");
        }

        if (s.minerHomeBase != null) {
            double d = Math.hypot(s.minerHomeBase.x - s.x, s.minerHomeBase.y - s.y);
            sb.append(" distB=").append((int) Math.round(d));
        } else {
            sb.append(" distB=?");
        }

        if (s.minerDebugNote != null && !s.minerDebugNote.isBlank()) {
            sb.append(" note=").append(s.minerDebugNote);
        }

        System.out.println(sb);
    }

    private static Ship getBaseForFaction(GameContext ctx, Faction faction) {
        Ship direct = ctx.teamBases.get(faction);
        if (direct == null && faction != null) {
            if (faction == Faction.ALLY) direct = ctx.allyBase;
            else if (faction == Faction.ENEMY) direct = ctx.enemyBase;
        }
        if (direct != null && direct.role == ShipRole.BASE && direct.faction == faction && direct.hp > 0) {
            return direct;
        }
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (s.faction != faction) continue;
            if (s.hp <= 0) continue;
            return s;
        }
        return null;
    }

    public static int getOreTotalForFaction(GameContext ctx, Faction f) {
        int total = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role == ShipRole.BASE && s.faction == f) total += s.oreStockpile;
        }
        return total;
    }

    private static void checkResourceRushWin(GameContext ctx) {
        int allyOre = getOreTotalForFaction(ctx, Faction.ALLY);
        int enemyOre = getOreTotalForFaction(ctx, Faction.ENEMY);

        if (allyOre >= ctx.resourceGoal || enemyOre >= ctx.resourceGoal) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;

            if (allyOre == enemyOre) ctx.gameOverText = "DRAW";
            else if (allyOre > enemyOre) ctx.gameOverText = "VICTORY";
            else ctx.gameOverText = "DEFEAT";
        }
    }

    private static void checkFourTeamDominationWin(GameContext ctx) {
        Faction[] teams = Faction.fourTeamFactions();
        int alive = TeamSystem.countAliveTeams(ctx, teams);
        if (alive <= 1) {
            ctx.gameOver = true;
            ctx.state = GameState.GAME_OVER;

            Faction winner = TeamSystem.getLastAliveTeam(ctx, teams);
            if (winner == null) ctx.gameOverText = "DRAW";
            else ctx.gameOverText = winner.teamName() + " WINS";
        }
    }

    // ---- Compatibility helpers for varying codebases ----

    private static double getMiningRate(Ship s) {
        try { return (double) s.getClass().getMethod("getMiningRate").invoke(s); } catch (Throwable ignored) {}
        try { return (double) s.getClass().getField("miningRate").get(s); } catch (Throwable ignored) {}
        return 60.0;
    }

    private static void aiGoTo(Ship s, double x, double y, double dt) {
        try { s.getClass().getMethod("aiGoTo", double.class, double.class, double.class).invoke(s, x, y, dt); return; } catch (Throwable ignored) {}
        // Fallback: direct velocity set using ship preferred speed.
        double dx = x - s.x, dy = y - s.y;
        double len = Math.sqrt(dx*dx + dy*dy) + 1e-9;
        double speed = Math.max(45.0, s.desiredSpeed);
        s.vx = (dx / len) * speed * dt;
        s.vy = (dy / len) * speed * dt;
    }

    private static void aiWander(Ship s, double dt, int w, int h) {
        try { s.getClass().getMethod("aiWander", double.class, int.class, int.class).invoke(s, dt, w, h); return; } catch (Throwable ignored) {}
        // Fallback wander: slow deterministic drift, with center pull to avoid edge camping.
        double t = System.nanoTime() * 1e-9 + (s.hashCode() * 0.001);
        double tx = s.x + Math.cos(t * 0.7) * 180.0;
        double ty = s.y + Math.sin(t * 0.9) * 180.0;
        tx = GameMath.clamp(tx, 80, w - 80);
        ty = GameMath.clamp(ty, 80, h - 80);
        aiGoTo(s, tx, ty, dt);
    }

    private static double getShipOre(Ship s) {
        try {
            Object v = s.getClass().getField("ore").get(s);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        try {
            Object v = s.getClass().getField("cargo").get(s);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return 0.0;
    }

    private static void setShipOre(Ship s, double v) {
        int iv = (int) Math.max(0, Math.round(v));
        try {
            java.lang.reflect.Field f = s.getClass().getField("ore");
            Class<?> t = f.getType();
            if (t == int.class || t == Integer.class) f.set(s, iv);
            else if (t == double.class || t == Double.class) f.set(s, (double) iv);
            else if (t == float.class || t == Float.class) f.set(s, (float) iv);
            else f.set(s, iv);
            return;
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field f = s.getClass().getField("cargo");
            Class<?> t = f.getType();
            if (t == int.class || t == Integer.class) f.set(s, iv);
            else if (t == double.class || t == Double.class) f.set(s, (double) iv);
            else if (t == float.class || t == Float.class) f.set(s, (float) iv);
            else f.set(s, iv);
        } catch (Throwable ignored) {}
    }

    private static void addOreToShip(Ship s, double add) {
        setShipOre(s, getShipOre(s) + add);
    }

    private static double getAsteroidOre(Asteroid a) {
        try { return (double) a.getClass().getField("ore").get(a); } catch (Throwable ignored) {}
        try { return ((Number) a.getClass().getField("ore").get(a)).doubleValue(); } catch (Throwable ignored) {}
        return 0;
    }

    private static double mineAsteroid(Asteroid a, double amt) {
        try { return ((Number) a.getClass().getMethod("mine", double.class).invoke(a, amt)).doubleValue(); } catch (Throwable ignored) {}
        try { return ((Number) a.getClass().getMethod("takeOre", double.class).invoke(a, amt)).doubleValue(); } catch (Throwable ignored) {}
        // If no method, just reduce field
        double ore = getAsteroidOre(a);
        double mined = Math.min(ore, amt);
        try { a.getClass().getField("ore").set(a, ore - mined); } catch (Throwable ignored) {}
        return mined;
    }
}
