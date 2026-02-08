public final class EconomySystem {
    private EconomySystem(){}

    public static void update(GameContext ctx, double dt) {
        if (ctx.gameOver) return;

        // Salvage drift
        for (int i = ctx.salvage.size() - 1; i >= 0; i--) {
            Salvage s = ctx.salvage.get(i);
            s.update(dt);
            if (s.life <= 0) ctx.salvage.remove(i);
        }

        // Mining for player (hold E)
        if (ctx.miningKeyDown && ctx.player != null) {
            doMining(ctx, ctx.player, dt);
        }

        // NPC mining & deposits
        handleNpcMiningAndDeposits(ctx, dt);

        // Mode win checks
        if (ctx.config.mode == GameMode.RESOURCE_RUSH) checkResourceRushWin(ctx);
    }

    private static void doMining(GameContext ctx, Ship miner, double dt) {
        Asteroid a = findBestAsteroidNear(ctx, miner.x, miner.y, 220);
        if (a == null) return;

        double rate = getMiningRate(miner) * ctx.miningMul;
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
            if (s.faction != Faction.ALLY) continue;
            if (GameMath.dist2(ctx.player.x, ctx.player.y, s.x, s.y) < (120 * 120)) return s;
        }
        return null;
    }

    private static void handleNpcMiningAndDeposits(GameContext ctx, double dt) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.MINER) continue;
            if (s.hp <= 0) continue;

            Ship base = (s.faction == Faction.ALLY) ? ctx.allyBase : ctx.enemyBase;
            if (base == null || base.hp <= 0) continue;

            if (GameMath.dist2(s.x, s.y, base.x, base.y) < (130 * 130)) {
                double ore = getShipOre(s);
                if (ore > 0) {
                    base.oreStockpile += (int) Math.round(ore);
                    if (s.faction == Faction.ALLY) {
                        ctx.credits += (int) Math.round(ore * GameContext.ORE_PRICE * ctx.orePriceMul);
                    }
                    setShipOre(s, 0);
                }
                // repair a bit (hp/hpMax are ints in this codebase)
                int heal = (int) Math.round(18 * dt);
                if (heal > 0) s.hp = Math.min(s.hpMax, s.hp + heal);
                continue;
            }

            if (getShipOre(s) >= 180) {
                aiGoTo(s, base.x, base.y, dt);
                continue;
            }

            Asteroid a = findBestAsteroidNear(ctx, s.x, s.y, 320);
            if (a != null) {
                aiGoTo(s, a.x, a.y, dt);
                if (GameMath.dist2(s.x, s.y, a.x, a.y) < (90 * 90)) {
                    double mined = mineAsteroid(a, getMiningRate(s) * ctx.miningMul * dt);
                    if (mined > 0) addOreToShip(s, mined);
                }
            } else {
                aiWander(s, dt, ctx.WORLD_W, ctx.WORLD_H);
            }
        }
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

    // ---- Compatibility helpers for varying codebases ----

    private static double getMiningRate(Ship s) {
        try { return (double) s.getClass().getMethod("getMiningRate").invoke(s); } catch (Throwable ignored) {}
        try { return (double) s.getClass().getField("miningRate").get(s); } catch (Throwable ignored) {}
        return 60.0;
    }

    private static void aiGoTo(Ship s, double x, double y, double dt) {
        try { s.getClass().getMethod("aiGoTo", double.class, double.class, double.class).invoke(s, x, y, dt); return; } catch (Throwable ignored) {}
        // Fallback: naive steer
        double dx = x - s.x, dy = y - s.y;
        double len = Math.sqrt(dx*dx + dy*dy) + 1e-9;
        s.vx += (dx/len) * 40 * dt;
        s.vy += (dy/len) * 40 * dt;
    }

    private static void aiWander(Ship s, double dt, int w, int h) {
        try { s.getClass().getMethod("aiWander", double.class, int.class, int.class).invoke(s, dt, w, h); } catch (Throwable ignored) {}
    }

    private static double getShipOre(Ship s) {
        try { return (double) s.getClass().getField("ore").get(s); } catch (Throwable ignored) {}
        try { return (double) s.getClass().getField("cargo").get(s); } catch (Throwable ignored) {}
        return 0.0;
    }

    private static void setShipOre(Ship s, double v) {
        try { s.getClass().getField("ore").set(s, v); return; } catch (Throwable ignored) {}
        try { s.getClass().getField("cargo").set(s, v); } catch (Throwable ignored) {}
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
