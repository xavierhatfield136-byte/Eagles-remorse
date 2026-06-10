import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class EventSystem {
    private EventSystem(){}

    private static final int EVENT_MARKET_SPIKE = 0;
    private static final int EVENT_MARKET_GLUT = 1;
    private static final int EVENT_MINING_SURGE = 2;
    private static final int EVENT_RICH_VEIN = 3;
    private static final int EVENT_SALVAGE_DRIFT = 4;
    private static final int EVENT_RELIEF_CONVOY = 5;
    private static final int EVENT_DEFENSE_BUOY = 6;
    private static final int EVENT_DISTRESS_RESPONSE = 7;
    private static final int EVENT_SCOUT_CONTACT = 8;
    private static final int EVENT_FRONTLINE_SKIRMISH = 9;
    private static final int EVENT_RAIDERS = 10;

    public static void showBanner(GameContext ctx, String msg, double seconds) {
        ctx.eventBanner = msg;
        ctx.eventBannerT = seconds;
    }

    public static void showWorldCallout(GameContext ctx, double x, double y, String msg, Color color, double seconds) {
        if (ctx == null || ctx.ui == null) return;
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        ctx.ui.addCombatCallout(x, y, msg, color, seconds);
    }

    public static boolean isPlayerNear(GameContext ctx, double x, double y, double radius) {
        if (ctx == null || ctx.player == null) return false;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return false;
        double rr = Math.max(0.0, radius);
        return Math.hypot(ctx.player.x - x, ctx.player.y - y) <= rr;
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;
        if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;
        if (ctx.ui != null) {
            ctx.ui.updateCombatCallouts(dt);
            ctx.ui.updateCommResult(dt);
        }
        TacticalReadabilitySystem.update(ctx);
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
        ctx.nextEventTimer = nextEventDelay(ctx);

        List<Integer> pool = buildRandomEventPool(ctx);
        if (pool.isEmpty()) return;
        int which = pool.get(ctx.rng.nextInt(pool.size()));
        switch (which) {
            case EVENT_MARKET_SPIKE -> triggerMarketShift(ctx);
            case EVENT_MARKET_GLUT -> triggerMarketGlut(ctx);
            case EVENT_MINING_SURGE -> triggerMiningSurge(ctx);
            case EVENT_RICH_VEIN -> triggerRichVein(ctx);
            case EVENT_SALVAGE_DRIFT -> triggerSalvageDrift(ctx);
            case EVENT_RELIEF_CONVOY -> triggerReliefConvoy(ctx);
            case EVENT_DEFENSE_BUOY -> triggerDefenseBuoy(ctx);
            case EVENT_DISTRESS_RESPONSE -> triggerDistressResponse(ctx);
            case EVENT_SCOUT_CONTACT -> triggerScoutContact(ctx);
            case EVENT_FRONTLINE_SKIRMISH -> triggerFrontlineSkirmish(ctx);
            default -> triggerRaiders(ctx);
        }
    }

    private static double nextEventDelay(GameContext ctx) {
        if (CampaignSystem.isCampaignActive(ctx)) {
            return 45.0 + ctx.rng.nextDouble() * 30.0;
        }
        return 18.0 + ctx.rng.nextDouble() * 18.0;
    }

    private static List<Integer> buildRandomEventPool(GameContext ctx) {
        ArrayList<Integer> pool = new ArrayList<>();
        if (CampaignSystem.isCampaignActive(ctx)) {
            addEventWeight(pool, EVENT_MARKET_SPIKE, 2);
            addEventWeight(pool, EVENT_MARKET_GLUT, 1);
            addEventWeight(pool, EVENT_MINING_SURGE, 2);
            addEventWeight(pool, EVENT_RICH_VEIN, 2);
            addEventWeight(pool, EVENT_SALVAGE_DRIFT, 2);
            return pool;
        }

        addEventWeight(pool, EVENT_MARKET_SPIKE, 2);
        addEventWeight(pool, EVENT_MARKET_GLUT, 1);
        addEventWeight(pool, EVENT_MINING_SURGE, 2);
        addEventWeight(pool, EVENT_RICH_VEIN, 2);
        addEventWeight(pool, EVENT_SALVAGE_DRIFT, 2);
        addEventWeight(pool, EVENT_SCOUT_CONTACT, 2);
        addEventWeight(pool, EVENT_RAIDERS, 2);

        if (friendlyFactionToPlayer(ctx) != null) {
            addEventWeight(pool, EVENT_RELIEF_CONVOY, 2);
            addEventWeight(pool, EVENT_DEFENSE_BUOY, 1);
            addEventWeight(pool, EVENT_DISTRESS_RESPONSE, 2);
        }
        if (friendlyFactionCount(ctx) >= 1 && hostileFactionCount(ctx) >= 1) {
            addEventWeight(pool, EVENT_FRONTLINE_SKIRMISH, 2);
        }
        if (hostileFactionToPlayer(ctx) != null) {
            addEventWeight(pool, EVENT_SCOUT_CONTACT, 1);
            addEventWeight(pool, EVENT_RAIDERS, 1);
        }
        return pool;
    }

    private static void addEventWeight(List<Integer> pool, int eventId, int weight) {
        if (pool == null || weight <= 0) return;
        for (int i = 0; i < weight; i++) pool.add(eventId);
    }

    private static void triggerMarketShift(GameContext ctx) {
        ctx.orePriceMul = 1.2 + ctx.rng.nextDouble() * 0.9;
        ctx.orePriceT = 14.0 + ctx.rng.nextDouble() * 10.0;
        showBanner(ctx, "MARKET SHIFT: ORE PRICE x" + String.format("%.2f", ctx.orePriceMul), 3.0);
    }

    private static void triggerMarketGlut(GameContext ctx) {
        ctx.orePriceMul = 0.60 + ctx.rng.nextDouble() * 0.25;
        ctx.orePriceT = 12.0 + ctx.rng.nextDouble() * 8.0;
        showBanner(ctx, "ORE GLUT: PRICE x" + String.format("%.2f", ctx.orePriceMul), 3.0);
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
        showWorldCallout(ctx, cx, cy, "RICH VEIN", new Color(178, 230, 255), 4.0);
        AudioSystem.playContextBanter(ctx, "science", "rich_vein_detected",
                "SCIENCE",
                "Dense ore return on scanners. Marking the vein now.",
                2.2, 9.0, 2);
    }

    private static void triggerSalvageDrift(GameContext ctx) {
        double[] center = randomInteriorPoint(ctx, 340.0);
        int drops = 4 + ctx.rng.nextInt(4);
        for (int i = 0; i < drops; i++) {
            double ox = ctx.rng.nextGaussian() * 95.0;
            double oy = ctx.rng.nextGaussian() * 95.0;
            ctx.salvage.add(new Salvage(
                    GameMath.clamp(center[0] + ox, 40.0, ctx.WORLD_W - 40.0),
                    GameMath.clamp(center[1] + oy, 40.0, ctx.WORLD_H - 40.0),
                    45 + ctx.rng.nextInt(90),
                    18 + ctx.rng.nextInt(45),
                    28.0 + ctx.rng.nextDouble() * 14.0));
        }
        showBanner(ctx, "SALVAGE DRIFT ON SCANNERS", 3.0);
        showWorldCallout(ctx, center[0], center[1], "SALVAGE DRIFT", new Color(255, 218, 124), 4.0);
        AudioSystem.playContextBanter(ctx, "science", "salvage_drift_detected",
                "SCIENCE",
                "Salvage drift on the board. Could be useful if we have room to peel off.",
                2.4, 9.0, 2);
    }

    private static void triggerReliefConvoy(GameContext ctx) {
        Faction faction = friendlyFactionToPlayer(ctx);
        Ship anchor = preferredFriendlyAnchor(ctx, faction);
        if (faction == null || anchor == null) {
            triggerMiningSurge(ctx);
            return;
        }

        double[] point = offsetPoint(ctx, anchor, towardWorldCenter(ctx, anchor), 300.0, 150.0);
        Ship transport = SpawnSystem.spawnTeamShip(ctx, ShipRole.TRANSPORT, faction, point[0], point[1]);
        if (transport == null) {
            transport = SpawnSystem.spawnTeamShip(ctx, ShipRole.HAULER, faction, point[0], point[1]);
        }
        SpawnSystem.spawnTeamShip(ctx, ShipRole.HAULER, faction, point[0] - 70.0, point[1] + 55.0);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.PICKET, faction, point[0] + 80.0, point[1] - 50.0);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.FRIGATE, faction, point[0] - 110.0, point[1] - 30.0);
        showBanner(ctx, faction.teamName().toUpperCase() + " RELIEF CONVOY ARRIVES", 3.0);
        showWorldCallout(ctx, point[0], point[1], "RELIEF CONVOY", new Color(120, 222, 180), 4.0);
    }

    private static void triggerDefenseBuoy(GameContext ctx) {
        Faction faction = friendlyFactionToPlayer(ctx);
        Ship anchor = preferredFriendlyAnchor(ctx, faction);
        if (faction == null || anchor == null) {
            triggerReliefConvoy(ctx);
            return;
        }

        double[] point = offsetPoint(ctx, anchor, towardWorldCenter(ctx, anchor), 380.0, 210.0);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.STATIC_TURRET, faction, point[0], point[1]);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.PATROL, faction, point[0] + 90.0, point[1] + 48.0);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.CIWS_CORVETTE, faction, point[0] - 70.0, point[1] - 42.0);
        showBanner(ctx, faction.teamName().toUpperCase() + " DEFENSE BUOY ONLINE", 3.0);
        showWorldCallout(ctx, point[0], point[1], "DEFENSE BUOY", new Color(142, 235, 208), 4.0);
    }

    private static void triggerDistressResponse(GameContext ctx) {
        Faction faction = mostPressuredFriendlyFaction(ctx);
        if (faction == null) faction = friendlyFactionToPlayer(ctx);
        if (faction == null) {
            triggerReliefConvoy(ctx);
            return;
        }

        double[] point = threatenedSpawnPoint(ctx, faction);
        SpawnSystem.spawnTeamGroup(ctx, faction, point[0], point[1]);
        if (ctx.rng.nextDouble() < 0.55) {
            SpawnSystem.spawnTeamShip(ctx, ShipRole.CIWS_CORVETTE, faction, point[0] + 95.0, point[1] + 65.0);
        }
        showBanner(ctx, faction.teamName().toUpperCase() + " DISTRESS RESPONSE", 3.0);
        showWorldCallout(ctx, point[0], point[1], "RESPONSE WING", new Color(138, 232, 190), 4.0);
        AudioSystem.playContextBanter(ctx, "captain", "distress_response",
                "CAPTAIN",
                faction.teamName() + " response wing is arriving on our side of the lane.",
                2.3, 10.0, 2);
    }

    private static void triggerScoutContact(GameContext ctx) {
        Faction faction = hostileFactionToPlayer(ctx);
        if (faction == null) {
            triggerRaiders(ctx);
            return;
        }

        Ship anchor = (ctx.player != null) ? ctx.player : firstLiveShipForTeam(ctx, friendlyFactionToPlayer(ctx));
        if (anchor == null) {
            triggerRaiders(ctx);
            return;
        }

        double angle = ctx.rng.nextDouble() * Math.PI * 2.0;
        double radius = 760.0 + ctx.rng.nextDouble() * 260.0;
        double x = GameMath.clamp(anchor.x + Math.cos(angle) * radius, 40.0, ctx.WORLD_W - 40.0);
        double y = GameMath.clamp(anchor.y + Math.sin(angle) * radius, 40.0, ctx.WORLD_H - 40.0);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.PICKET, faction, x, y);
        SpawnSystem.spawnTeamShip(ctx, ShipRole.PATROL, faction, x + 60.0, y - 45.0);
        if (ctx.rng.nextDouble() < 0.5) {
            SpawnSystem.spawnTeamShip(ctx, ShipRole.STEALTH_SHIP, faction, x - 80.0, y + 35.0);
        } else {
            SpawnSystem.spawnTeamShip(ctx, ShipRole.MISSILE_BOAT, faction, x - 90.0, y + 50.0);
        }
        showBanner(ctx, "SCOUT CONTACT DETECTED", 2.8);
        showWorldCallout(ctx, x, y, "HOSTILE SCOUT", new Color(255, 150, 132), 4.0);
    }

    private static void triggerFrontlineSkirmish(GameContext ctx) {
        Faction friendly = randomFriendlyFaction(ctx);
        Faction hostile = randomHostileFaction(ctx);
        if (friendly == null || hostile == null) {
            triggerRaiders(ctx);
            return;
        }

        Ship friendlyAnchor = preferredFriendlyAnchor(ctx, friendly);
        Ship hostileAnchor = firstLiveShipForTeam(ctx, hostile);
        if (friendlyAnchor == null || hostileAnchor == null) {
            triggerRaiders(ctx);
            return;
        }

        double midX = GameMath.clamp((friendlyAnchor.x + hostileAnchor.x) * 0.5, 80.0, ctx.WORLD_W - 80.0);
        double midY = GameMath.clamp((friendlyAnchor.y + hostileAnchor.y) * 0.5, 80.0, ctx.WORLD_H - 80.0);
        SpawnSystem.spawnTeamGroup(ctx, friendly, midX - 140.0, midY + 70.0);
        SpawnSystem.spawnTeamGroup(ctx, hostile, midX + 140.0, midY - 70.0);
        showBanner(ctx, "FRONTLINE SKIRMISH IGNITES", 3.0);
        showWorldCallout(ctx, midX, midY, "ACTIVE SKIRMISH", new Color(255, 204, 144), 4.0);
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

    private static Faction friendlyFactionToPlayer(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return Faction.ALLY;
        ArrayList<Faction> friendly = new ArrayList<>();
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null) continue;
            if (!ctx.player.faction.isFriendlyTo(faction)) continue;
            if (!TeamSystem.isTeamAlive(ctx, faction)) continue;
            friendly.add(faction);
        }
        if (friendly.isEmpty()) return null;
        return friendly.get(ctx.rng.nextInt(friendly.size()));
    }

    private static Faction hostileFactionToPlayer(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return Faction.ENEMY;
        ArrayList<Faction> hostile = new ArrayList<>();
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null) continue;
            if (ctx.player.faction.isFriendlyTo(faction)) continue;
            if (!TeamSystem.isTeamAlive(ctx, faction)) continue;
            hostile.add(faction);
        }
        if (hostile.isEmpty()) return null;
        return hostile.get(ctx.rng.nextInt(hostile.size()));
    }

    private static int friendlyFactionCount(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return 0;
        int count = 0;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null) continue;
            if (!ctx.player.faction.isFriendlyTo(faction)) continue;
            if (!TeamSystem.isTeamAlive(ctx, faction)) continue;
            count++;
        }
        return count;
    }

    private static int hostileFactionCount(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return 0;
        int count = 0;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null) continue;
            if (ctx.player.faction.isFriendlyTo(faction)) continue;
            if (!TeamSystem.isTeamAlive(ctx, faction)) continue;
            count++;
        }
        return count;
    }

    private static Faction randomFriendlyFaction(GameContext ctx) {
        return friendlyFactionToPlayer(ctx);
    }

    private static Faction randomHostileFaction(GameContext ctx) {
        return hostileFactionToPlayer(ctx);
    }

    private static Faction mostPressuredFriendlyFaction(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null) return null;
        Faction best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null) continue;
            if (!ctx.player.faction.isFriendlyTo(faction)) continue;
            if (!TeamSystem.isTeamAlive(ctx, faction)) continue;
            int score = TeamSystem.countAliveShips(ctx, faction) + TeamSystem.countAliveMiners(ctx, faction) * 2;
            if (score < bestScore) {
                bestScore = score;
                best = faction;
            }
        }
        return best;
    }

    private static Ship preferredFriendlyAnchor(GameContext ctx, Faction faction) {
        Ship base = TeamSystem.getBaseForTeam(ctx, faction);
        if (base != null && base.alive && !base.dying && base.hp > 0) return base;
        Ship live = firstLiveShipForTeam(ctx, faction);
        if (live != null) return live;
        if (ctx != null && ctx.player != null && ctx.player.alive && !ctx.player.dying && ctx.player.hp > 0) {
            return ctx.player;
        }
        return null;
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

    private static double[] threatenedSpawnPoint(GameContext ctx, Faction faction) {
        Ship anchor = preferredFriendlyAnchor(ctx, faction);
        if (anchor == null) return randomInteriorPoint(ctx, 220.0);

        double[] dir = towardWorldCenter(ctx, anchor);
        return offsetPoint(ctx, anchor, dir, 260.0, 160.0);
    }

    private static double[] towardWorldCenter(GameContext ctx, Ship anchor) {
        if (ctx == null || anchor == null) return new double[]{1.0, 0.0};
        double dx = ctx.WORLD_W * 0.5 - anchor.x;
        double dy = ctx.WORLD_H * 0.5 - anchor.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) return new double[]{1.0, 0.0};
        return new double[]{dx / len, dy / len};
    }

    private static double[] offsetPoint(GameContext ctx, Ship anchor, double[] dir, double forward, double lateral) {
        double ux = (dir == null || dir.length < 2) ? 1.0 : dir[0];
        double uy = (dir == null || dir.length < 2) ? 0.0 : dir[1];
        double side = (ctx.rng.nextDouble() - 0.5) * lateral * 2.0;
        double x = anchor.x + ux * forward - uy * side;
        double y = anchor.y + uy * forward + ux * side;
        x = GameMath.clamp(x, 40.0, ctx.WORLD_W - 40.0);
        y = GameMath.clamp(y, 40.0, ctx.WORLD_H - 40.0);
        return new double[]{x, y};
    }

    private static double[] randomInteriorPoint(GameContext ctx, double margin) {
        double x = margin + ctx.rng.nextDouble() * Math.max(80.0, ctx.WORLD_W - margin * 2.0);
        double y = margin + ctx.rng.nextDouble() * Math.max(80.0, ctx.WORLD_H - margin * 2.0);
        x = GameMath.clamp(x, 40.0, ctx.WORLD_W - 40.0);
        y = GameMath.clamp(y, 40.0, ctx.WORLD_H - 40.0);
        return new double[]{x, y};
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
            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(ctx.player.role, ctx.player.faction, hotspot);
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
