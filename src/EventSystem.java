public final class EventSystem {
    private EventSystem(){}

    public static void showBanner(GameContext ctx, String msg, double seconds) {
        ctx.eventBanner = msg;
        ctx.eventBannerT = seconds;
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;
        if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;
        if (ctx.hazardHintCooldown > 0.0) ctx.hazardHintCooldown -= dt;
        if (ctx.hazardCriticalCooldown > 0.0) ctx.hazardCriticalCooldown -= dt;
        updateHazardWarnings(ctx);

        // decay modifiers
        if (ctx.orePriceT > 0) {
            ctx.orePriceT -= dt;
            if (ctx.orePriceT <= 0) ctx.orePriceMul = 1.0;
        }
        if (ctx.miningT > 0) {
            ctx.miningT -= dt;
            if (ctx.miningT <= 0) ctx.miningMul = 1.0;
        }

        if (ctx.config == null || !ctx.config.randomEvents) return;
        if (CampaignSystem.suppressRandomEvents(ctx)) return;

        ctx.nextEventTimer -= dt;
        if (ctx.nextEventTimer > 0) return;
        ctx.nextEventTimer = 18.0 + ctx.rng.nextDouble() * 18.0;

        int which = ctx.rng.nextInt(4);
        switch (which) {
            case 0 -> triggerMarketShift(ctx);
            case 1 -> triggerMiningSurge(ctx);
            case 2 -> triggerRichVein(ctx);
            default -> triggerRaiders(ctx);
        }
    }

    private static void triggerMarketShift(GameContext ctx) {
        ctx.orePriceMul = 1.2 + ctx.rng.nextDouble() * 0.9;
        ctx.orePriceT = 14.0 + ctx.rng.nextDouble() * 10.0;
        showBanner(ctx, "MARKET SHIFT: ORE PRICE x" + String.format("%.2f", ctx.orePriceMul), 3.0);
    }

    private static void triggerMiningSurge(GameContext ctx) {
        ctx.miningMul = 1.2 + ctx.rng.nextDouble();
        ctx.miningT = 12.0 + ctx.rng.nextDouble() * 10.0;
        showBanner(ctx, "MINING SURGE: MINING x" + String.format("%.2f", ctx.miningMul), 3.0);
    }

    private static void triggerRichVein(GameContext ctx) {
        double cx = 600 + ctx.rng.nextDouble() * (ctx.WORLD_W - 1200);
        double cy = 600 + ctx.rng.nextDouble() * (ctx.WORLD_H - 1200);
        for (int i = 0; i < 12; i++) {
            double x = cx + ctx.rng.nextGaussian() * 220;
            double y = cy + ctx.rng.nextGaussian() * 220;
            x = GameMath.clamp(x, 200, ctx.WORLD_W - 200);
            y = GameMath.clamp(y, 200, ctx.WORLD_H - 200);
            ctx.asteroids.add(new Asteroid(x, y, 26 + ctx.rng.nextDouble() * 36, (int)Math.round(800 + ctx.rng.nextDouble() * 1600)));
        }
        showBanner(ctx, "RICH VEIN DETECTED", 3.0);
    }

    private static void triggerRaiders(GameContext ctx) {
        java.util.List<Faction> teams = activeRaidTeams(ctx);
        if (teams.isEmpty()) {
            SpawnSystem.spawnEnemyGroup(ctx, ctx.player.x + 900 + ctx.rng.nextDouble() * 400, ctx.player.y + 600 + ctx.rng.nextDouble() * 400);
            SpawnSystem.spawnAllyGroup(ctx, ctx.player.x - 900 - ctx.rng.nextDouble() * 400, ctx.player.y - 600 - ctx.rng.nextDouble() * 400);
            showBanner(ctx, "RAIDERS INBOUND", 2.5);
            return;
        }

        for (int i = 0; i < teams.size(); i++) {
            Faction targetTeam = teams.get(i);
            Faction raiderFaction = raidSourceForTeam(teams, i);
            if (raiderFaction == null) continue;
            double[] spawn = raidSpawnPoint(ctx, targetTeam);
            SpawnSystem.spawnTeamGroup(ctx, raiderFaction, spawn[0], spawn[1]);
        }
        showBanner(ctx, teams.size() > 1 ? "RAIDERS STRIKE EVERY FRONT" : "RAIDERS INBOUND", 2.5);
    }

    private static java.util.List<Faction> activeRaidTeams(GameContext ctx) {
        java.util.ArrayList<Faction> teams = new java.util.ArrayList<>();
        for (Faction team : Faction.fourTeamFactions()) {
            if (TeamSystem.isTeamAlive(ctx, team)) teams.add(team);
        }
        return teams;
    }

    private static Faction raidSourceForTeam(java.util.List<Faction> teams, int targetIndex) {
        if (teams == null || teams.isEmpty() || targetIndex < 0 || targetIndex >= teams.size()) return null;
        Faction targetTeam = teams.get(targetIndex);
        for (int offset = 1; offset < teams.size(); offset++) {
            Faction candidate = teams.get((targetIndex + offset) % teams.size());
            if (candidate != null && targetTeam != null && candidate.teamId() != targetTeam.teamId()) {
                return candidate;
            }
        }
        if (targetTeam == null) return null;
        return (targetTeam.teamId() == Faction.ALLY.teamId()) ? Faction.ENEMY : Faction.ALLY;
    }

    private static double[] raidSpawnPoint(GameContext ctx, Faction targetTeam) {
        Ship anchor = TeamSystem.getBaseForTeam(ctx, targetTeam);
        if (anchor == null) anchor = firstLiveShipForTeam(ctx, targetTeam);
        if (anchor == null) {
            return new double[]{ctx.WORLD_W * 0.5, ctx.WORLD_H * 0.5};
        }

        double cx = ctx.WORLD_W * 0.5;
        double cy = ctx.WORLD_H * 0.5;
        double dx = cx - anchor.x;
        double dy = cy - anchor.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) len = 1.0;
        double ux = dx / len;
        double uy = dy / len;
        double forward = 420.0 + ctx.rng.nextDouble() * 180.0;
        double lateral = (ctx.rng.nextDouble() - 0.5) * 220.0;
        double x = anchor.x + ux * forward - uy * lateral;
        double y = anchor.y + uy * forward + ux * lateral;
        x = GameMath.clamp(x, 40.0, ctx.WORLD_W - 40.0);
        y = GameMath.clamp(y, 40.0, ctx.WORLD_H - 40.0);
        return new double[]{x, y};
    }

    private static Ship firstLiveShipForTeam(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return null;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.teamId() != team.teamId()) continue;
            return s;
        }
        return null;
    }

    private static void updateHazardWarnings(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (ctx.eventBannerT > 0.20) return;

        int fireRooms = ctx.player.activeFireRoomCount();
        if (fireRooms <= 0) return;
        double fireLoad = ctx.player.totalFireIntensity();

        ShipRoomLayout.RoomId hotspot = ctx.player.hottestFireRoom();
        String hotspotLabel = (hotspot == null) ? "UNKNOWN" : hotspot.name();
        if (hotspot != null) {
            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(ctx.player.role, hotspot);
            if (def != null && def.label != null && !def.label.isBlank()) {
                hotspotLabel = def.label;
            }
        }

        if ((fireRooms >= 3 || fireLoad >= 3.0) && ctx.hazardCriticalCooldown <= 0.0) {
            showBanner(ctx, "CRITICAL FIRE ALERT: " + hotspotLabel + " - PRIORITIZE DAMAGE CONTROL", 1.2);
            ctx.hazardCriticalCooldown = 4.0;
            ctx.hazardHintCooldown = Math.max(ctx.hazardHintCooldown, 2.0);
            return;
        }
        if (ctx.hazardHintCooldown <= 0.0) {
            showBanner(ctx, "FIRE DETECTED: " + hotspotLabel + " - ENGINEERING CAN SUPPRESS (KEY 8)", 1.1);
            ctx.hazardHintCooldown = 6.0;
        }
    }
}
