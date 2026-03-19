import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public final class TutorialSystem {
    private TutorialSystem() {}

    private static final WeakHashMap<GameContext, TutorialState> STATES = new WeakHashMap<>();
    private static final double ACTIVE_PING_PERIOD = 1.0;
    private static final double POINT_REACHED_RADIUS = 110.0;
    private static final double MINING_RADIUS = 180.0;
    private static final double WEAPON_RANGE_RADIUS = 170.0;

    private enum StepId {
        MOVE_TO_ALPHA,
        MAP_TO_BETA,
        TRAVEL_TO_BETA,
        LOCK_AND_FIRE,
        MINE_ORE,
        DOCK_AND_REVIEW,
        COMPLETE
    }

    private static final class Marker {
        final String label;
        final double x;
        final double y;
        final double radius;

        Marker(String label, double x, double y, double radius) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static final class TutorialState {
        Faction playerFaction;
        Faction hostileFaction;
        int homeBaseId = -1;
        int combatTargetId = -1;
        double alphaX;
        double alphaY;
        double betaX;
        double betaY;
        double weaponsX;
        double weaponsY;
        double miningX;
        double miningY;
        double pingTimer = 0.0;
        int stepIndex = 0;
        int miningStartOre = 0;
        boolean sawShop = false;
        boolean sawBase = false;
        boolean sawPower = false;
        boolean sawCrew = false;
    }

    public static void init(GameContext ctx, Faction playerFaction) {
        if (ctx == null) return;

        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.damageEvents.clear();
        ctx.audioEvents.clear();
        ctx.teamBases.clear();
        ctx.baseUpgrades.clear();
        ctx.lockedTarget = null;
        ctx.allyBase = null;
        ctx.enemyBase = null;
        ctx.waypointX = Double.NaN;
        ctx.waypointY = Double.NaN;
        ctx.mapPings.clear();
        ctx.campaign = null;
        ctx.credits = 2400;
        ctx.enemyWaveTimer = Double.POSITIVE_INFINITY;
        ctx.nextEventTimer = Double.POSITIVE_INFINITY;
        ctx.minerReinforcementTimer = Double.POSITIVE_INFINITY;
        ctx.orePriceMul = 1.0;
        ctx.orePriceT = 0.0;
        ctx.miningMul = 1.0;
        ctx.miningT = 0.0;

        TutorialState st = new TutorialState();
        st.playerFaction = (playerFaction == null) ? Faction.ALLY : playerFaction;
        st.hostileFaction = st.playerFaction.isFriendlyTo(Faction.ENEMY) ? Faction.ENEMY : Faction.ALLY;

        double baseX = GameMath.clamp(ctx.WORLD_W * 0.15, 220.0, ctx.WORLD_W - 260.0);
        double baseY = GameMath.clamp(ctx.WORLD_H * 0.58, 220.0, ctx.WORLD_H - 220.0);
        double playerX = GameMath.clamp(baseX + 180.0, 90.0, ctx.WORLD_W - 90.0);
        double playerY = baseY;

        st.alphaX = GameMath.clamp(ctx.WORLD_W * 0.28, 260.0, ctx.WORLD_W - 260.0);
        st.alphaY = GameMath.clamp(ctx.WORLD_H * 0.28, 220.0, ctx.WORLD_H - 220.0);
        st.betaX = GameMath.clamp(ctx.WORLD_W * 0.48, 260.0, ctx.WORLD_W - 260.0);
        st.betaY = GameMath.clamp(ctx.WORLD_H * 0.70, 220.0, ctx.WORLD_H - 220.0);
        st.weaponsX = GameMath.clamp(ctx.WORLD_W * 0.72, 260.0, ctx.WORLD_W - 260.0);
        st.weaponsY = GameMath.clamp(ctx.WORLD_H * 0.48, 220.0, ctx.WORLD_H - 220.0);
        st.miningX = GameMath.clamp(ctx.WORLD_W * 0.38, 260.0, ctx.WORLD_W - 260.0);
        st.miningY = GameMath.clamp(ctx.WORLD_H * 0.18, 220.0, ctx.WORLD_H - 220.0);

        Ship homeBase = new FleetShip(ShipRole.BASE, st.playerFaction, baseX, baseY);
        homeBase.name = "Tutorial Base";
        ctx.ships.add(homeBase);
        ctx.teamBases.put(st.playerFaction, homeBase);
        ctx.baseUpgrades.put(homeBase, new BaseUpgrades());
        st.homeBaseId = homeBase.id;
        if (st.playerFaction == Faction.ALLY) ctx.allyBase = homeBase;
        if (st.playerFaction == Faction.ENEMY) ctx.enemyBase = homeBase;

        ctx.player = new Player(ShipRole.FRIGATE, playerX, playerY);
        ctx.player.faction = st.playerFaction;
        ctx.player.name = "Player";
        ctx.player.angle = 0.0;
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.ships.add(ctx.player);

        Ship target = new FleetShip(ShipRole.PATROL, st.hostileFaction, st.weaponsX + 70.0, st.weaponsY);
        configureTutorialTarget(target, "Tutorial Drone");
        ctx.ships.add(target);
        st.combatTargetId = target.id;

        addAsteroid(ctx, st.miningX - 80.0, st.miningY + 24.0, 34.0, 620);
        addAsteroid(ctx, st.miningX + 30.0, st.miningY - 36.0, 28.0, 520);
        addAsteroid(ctx, st.miningX + 110.0, st.miningY + 42.0, 30.0, 560);

        try {
            DoctrineRegistry.applyToShip(homeBase);
            DoctrineRegistry.applyToShip(ctx.player);
            DoctrineRegistry.applyToShip(target);
        } catch (Throwable ignored) {}

        STATES.put(ctx, st);
        enterStep(ctx, st, StepId.MOVE_TO_ALPHA, true);
    }

    public static boolean isActive(GameContext ctx) {
        return state(ctx) != null;
    }

    public static void update(GameContext ctx, double dt) {
        TutorialState st = state(ctx);
        if (st == null || ctx == null || ctx.player == null) return;

        st.sawShop |= ctx.shopOpen;
        st.sawBase |= ctx.baseMenuOpen;
        st.sawPower |= ctx.powerManagementOpen;
        st.sawCrew |= ctx.crewStationsOpen;

        st.pingTimer -= Math.max(0.0, dt);
        if (st.pingTimer <= 0.0) {
            Marker marker = activeMarker(ctx, st);
            if (marker != null) {
                UISystem.addPing(ctx, marker.x, marker.y, 1.6);
            }
            st.pingTimer = ACTIVE_PING_PERIOD;
        }

        switch (currentStep(st)) {
            case MOVE_TO_ALPHA -> {
                if (near(ctx.player, st.alphaX, st.alphaY, POINT_REACHED_RADIUS)) {
                    enterStep(ctx, st, StepId.MAP_TO_BETA, true);
                }
            }
            case MAP_TO_BETA -> {
                if (ctx.mapOpen && nearPoint(ctx.waypointX, ctx.waypointY, st.betaX, st.betaY, 150.0)) {
                    enterStep(ctx, st, StepId.TRAVEL_TO_BETA, true);
                }
            }
            case TRAVEL_TO_BETA -> {
                if (near(ctx.player, st.betaX, st.betaY, POINT_REACHED_RADIUS)) {
                    enterStep(ctx, st, StepId.LOCK_AND_FIRE, true);
                }
            }
            case LOCK_AND_FIRE -> {
                Ship target = shipById(ctx, st.combatTargetId);
                boolean targetDamaged = target == null || target.hp < target.hpMax || !target.alive || target.dying;
                boolean targetLocked = (ctx.lockedTarget != null && ctx.lockedTarget.id == st.combatTargetId);
                if (targetDamaged && (targetLocked || target == null || !target.alive || target.dying)) {
                    enterStep(ctx, st, StepId.MINE_ORE, true);
                }
            }
            case MINE_ORE -> {
                Ship base = shipById(ctx, st.homeBaseId);
                int totalOre = ((ctx.player == null) ? 0 : ctx.player.cargo) + ((base == null) ? 0 : base.oreStockpile);
                if (totalOre > st.miningStartOre) {
                    enterStep(ctx, st, StepId.DOCK_AND_REVIEW, true);
                }
            }
            case DOCK_AND_REVIEW -> {
                Ship docked = EconomySystem.getDockedFriendlyBase(ctx);
                boolean dockedHome = docked != null && docked.id == st.homeBaseId;
                if (dockedHome && st.sawShop && st.sawBase && st.sawPower && st.sawCrew) {
                    enterStep(ctx, st, StepId.COMPLETE, true);
                }
            }
            case COMPLETE -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
            }
        }
    }

    public static String hudTitle(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        StepId step = currentStep(st);
        if (step == StepId.COMPLETE) return "TUTORIAL   COMPLETE";
        return "TUTORIAL   STEP " + Math.min(6, st.stepIndex + 1) + "/6";
    }

    public static String hudDetail(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        Ship target = shipById(ctx, st.combatTargetId);
        return switch (currentStep(st)) {
            case MOVE_TO_ALPHA ->
                    "Reach NAV ALPHA with WASD thrust and steering. Follow the waypoint marker on the minimap.";
            case MAP_TO_BETA ->
                    "Open the strategic map with M, then click NAV BETA to set a waypoint there.";
            case TRAVEL_TO_BETA ->
                    "Fly to NAV BETA. The waypoint ring will guide you across the map.";
            case LOCK_AND_FIRE ->
                    "At the weapons range, lock the tutorial drone with L and hit it with SPACE. SHIFT fires your missile secondary if you want extra punch."
                            + combatStatus(target);
            case MINE_ORE ->
                    "Move to the mining pocket and hold F near an asteroid until you collect ore. Cargo fills automatically when close enough.";
            case DOCK_AND_REVIEW ->
                    "Dock at Tutorial Base, then review your interfaces: "
                            + checklist(st.sawShop, "TAB")
                            + " "
                            + checklist(st.sawBase, "B")
                            + " "
                            + checklist(st.sawPower, "O")
                            + " "
                            + checklist(st.sawCrew, "H");
            case COMPLETE ->
                    "Core systems covered: navigation, map waypoints, combat, mining, docking, and overlays. You can stay in this sandbox or return to menu with F10.";
        };
    }

    public static String contextHint(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        return switch (currentStep(st)) {
            case MOVE_TO_ALPHA -> "Throttle with W/S, steer with A/D, and bring the ship into the NAV ALPHA ring.";
            case MAP_TO_BETA -> "Press M to open the full map. Click the NAV BETA marker to place a waypoint, then close the map.";
            case TRAVEL_TO_BETA -> "Use the minimap waypoint marker to cross the sector. Manual flight is enough here.";
            case LOCK_AND_FIRE -> "Press L to lock the drone under your cursor, then fire with SPACE. SHIFT uses your secondary battery.";
            case MINE_ORE -> "Asteroids with ore can be mined by holding F while close. Bring any ore you gather back to base later.";
            case DOCK_AND_REVIEW -> "At the base, open TAB, B, O, and H at least once. This introduces your main command overlays.";
            case COMPLETE -> "Tutorial complete. Keep testing systems here, or press F10 to head back to the menu.";
        };
    }

    public static void drawWorldMarkers(GameContext ctx, Graphics2D g2) {
        TutorialState st = state(ctx);
        if (st == null || g2 == null) return;

        List<Marker> markers = markers(ctx, st);
        Marker active = activeMarker(ctx, st);
        for (Marker marker : markers) {
            if (marker == null) continue;
            boolean isActive = active != null && marker.label.equals(active.label);
            drawMarker(g2, marker, isActive);
        }
    }

    private static TutorialState state(GameContext ctx) {
        if (ctx == null || ctx.config == null || ctx.config.mode != GameMode.TUTORIAL) return null;
        return STATES.get(ctx);
    }

    private static StepId currentStep(TutorialState st) {
        StepId[] steps = StepId.values();
        int idx = Math.max(0, Math.min(steps.length - 1, st.stepIndex));
        return steps[idx];
    }

    private static void enterStep(GameContext ctx, TutorialState st, StepId step, boolean announce) {
        if (ctx == null || st == null || step == null) return;
        st.stepIndex = step.ordinal();
        st.pingTimer = 0.0;

        switch (step) {
            case MOVE_TO_ALPHA -> {
                ctx.waypointX = st.alphaX;
                ctx.waypointY = st.alphaY;
                if (announce) EventSystem.showBanner(ctx, "TUTORIAL: NAV ALPHA", 2.2);
            }
            case MAP_TO_BETA -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                if (announce) EventSystem.showBanner(ctx, "TUTORIAL: OPEN MAP AND SET NAV BETA", 2.6);
            }
            case TRAVEL_TO_BETA -> {
                ctx.waypointX = st.betaX;
                ctx.waypointY = st.betaY;
                if (announce) EventSystem.showBanner(ctx, "WAYPOINT CONFIRMED: NAV BETA", 2.0);
            }
            case LOCK_AND_FIRE -> {
                ctx.waypointX = st.weaponsX;
                ctx.waypointY = st.weaponsY;
                if (announce) EventSystem.showBanner(ctx, "TUTORIAL: WEAPONS RANGE", 2.0);
            }
            case MINE_ORE -> {
                Ship base = shipById(ctx, st.homeBaseId);
                st.miningStartOre = ((ctx.player == null) ? 0 : ctx.player.cargo) + ((base == null) ? 0 : base.oreStockpile);
                ctx.waypointX = st.miningX;
                ctx.waypointY = st.miningY;
                if (announce) EventSystem.showBanner(ctx, "TUTORIAL: MINING POCKET", 2.0);
            }
            case DOCK_AND_REVIEW -> {
                Ship base = shipById(ctx, st.homeBaseId);
                if (base != null) {
                    ctx.waypointX = base.x;
                    ctx.waypointY = base.y;
                }
                if (announce) EventSystem.showBanner(ctx, "TUTORIAL: RETURN TO BASE", 2.2);
            }
            case COMPLETE -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                EventSystem.showBanner(ctx, "TUTORIAL COMPLETE", 3.0);
            }
        }
    }

    private static List<Marker> markers(GameContext ctx, TutorialState st) {
        ArrayList<Marker> out = new ArrayList<>();
        Ship base = shipById(ctx, st.homeBaseId);
        if (base != null) out.add(new Marker("HOME BASE", base.x, base.y, 120.0));
        out.add(new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS));
        out.add(new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS));
        out.add(new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS));
        out.add(new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS));
        return out;
    }

    private static Marker activeMarker(GameContext ctx, TutorialState st) {
        return switch (currentStep(st)) {
            case MOVE_TO_ALPHA -> new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS);
            case MAP_TO_BETA, TRAVEL_TO_BETA -> new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS);
            case LOCK_AND_FIRE -> new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS);
            case MINE_ORE -> new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS);
            case DOCK_AND_REVIEW -> {
                Ship base = shipById(ctx, st.homeBaseId);
                yield (base == null) ? null : new Marker("HOME BASE", base.x, base.y, 120.0);
            }
            case COMPLETE -> null;
        };
    }

    private static void drawMarker(Graphics2D g2, Marker marker, boolean active) {
        int x = (int) Math.round(marker.x);
        int y = (int) Math.round(marker.y);
        int r = (int) Math.round(marker.radius);
        Color ring = active ? new Color(255, 235, 150, 210) : new Color(150, 205, 255, 110);
        Color fill = active ? new Color(255, 215, 120, 28) : new Color(120, 170, 255, 14);
        g2.setColor(fill);
        g2.fillOval(x - r, y - r, r * 2, r * 2);
        g2.setColor(ring);
        g2.drawOval(x - r, y - r, r * 2, r * 2);
        g2.drawLine(x - 10, y, x + 10, y);
        g2.drawLine(x, y - 10, x, y + 10);
        g2.setFont(new Font("Consolas", active ? Font.BOLD : Font.PLAIN, active ? 14 : 12));
        g2.drawString(marker.label, x - 36, y - r - 10);
    }

    private static String combatStatus(Ship target) {
        if (target == null) return "";
        return "   TARGET: " + target.hp + "/" + target.hpMax + " HULL";
    }

    private static String checklist(boolean done, String key) {
        return done ? "[" + key + " OK]" : "[" + key + " --]";
    }

    private static boolean near(Ship ship, double x, double y, double radius) {
        return ship != null && GameMath.dist2(ship.x, ship.y, x, y) <= radius * radius;
    }

    private static boolean nearPoint(double x1, double y1, double x2, double y2, double radius) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1)) return false;
        return GameMath.dist2(x1, y1, x2, y2) <= radius * radius;
    }

    private static Ship shipById(GameContext ctx, int id) {
        if (ctx == null || id <= 0) return null;
        for (Ship s : ctx.ships) {
            if (s != null && s.id == id) return s;
        }
        return null;
    }

    private static void configureTutorialTarget(Ship s, String label) {
        if (s == null) return;
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
        s.shieldMax = 0.0;
        s.shield = 0.0;
        s.shieldRegen = 0.0;
        s.shieldActive = false;
    }

    private static void addAsteroid(GameContext ctx, double x, double y, double radius, int ore) {
        if (ctx == null) return;
        Asteroid a = new Asteroid(x, y, radius, ore);
        a.rich = true;
        a.richness = 2.0;
        ctx.asteroids.add(a);
    }
}
