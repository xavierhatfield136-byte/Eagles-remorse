import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
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
    private static final double PING_MATCH_RADIUS = 220.0;

    private enum StepId {
        MOVE_TO_ALPHA,
        MAP_TO_BETA,
        TRAVEL_TO_BETA,
        PING_WEAPONS_RANGE,
        LOCK_AND_FIRE,
        XRAY_AND_DIAGNOSE,
        MINE_ORE,
        DOCK_AND_UPGRADE,
        SWAP_TO_CARRIER,
        POWER_AND_CREW,
        DAMAGE_CONTROL,
        CARRIER_WING,
        WARP_TRIAL,
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
        double gammaX;
        double gammaY;
        double pingTimer = 0.0;
        int stepIndex = 0;
        int miningStartOre = 0;
        int hangarTierAtUpgradeStep = 0;
        boolean sawShop = false;
        boolean sawBase = false;
        boolean sawPower = false;
        boolean sawCrew = false;
        boolean sawFlightDeck = false;
        boolean launchedWing = false;
        boolean carrierModeChanged = false;
        boolean carrierAutoLaunchChanged = false;
        boolean seededDamageControlFire = false;
        Ship.PowerPreset baselinePowerPreset = Ship.PowerPreset.BALANCED;
        double[] baselinePowerBuses = new double[]{};
        GameContext.CaptainDirective baselineCaptainDirective = GameContext.CaptainDirective.BALANCED;
        GameContext.HelmMode baselineHelmMode = GameContext.HelmMode.INTERCEPT;
        GameContext.TacticalMode baselineTacticalMode = GameContext.TacticalMode.DEFENSIVE;
        GameContext.EngineeringMode baselineEngineeringMode = GameContext.EngineeringMode.BALANCED;
        boolean baselineScienceJamming = false;
        Ship.CarrierCommandMode baselineCarrierMode = Ship.CarrierCommandMode.ATTACK;
        boolean baselineCarrierAutoLaunch = false;
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
        ctx.credits = 15000;
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
        st.gammaX = GameMath.clamp(ctx.WORLD_W * 0.82, 260.0, ctx.WORLD_W - 260.0);
        st.gammaY = GameMath.clamp(ctx.WORLD_H * 0.16, 220.0, ctx.WORLD_H - 220.0);

        Ship homeBase = new FleetShip(ShipRole.BASE, st.playerFaction, baseX, baseY);
        homeBase.name = "Tutorial Base";
        homeBase.oreStockpile = 1800;
        ctx.ships.add(homeBase);
        ctx.teamBases.put(st.playerFaction, homeBase);
        BaseUpgrades tutorialUpgrades = new BaseUpgrades();
        tutorialUpgrades.hullLv = 1;
        tutorialUpgrades.shieldLv = 1;
        tutorialUpgrades.turretLv = 1;
        tutorialUpgrades.miningLv = 1;
        tutorialUpgrades.hangarLv = 2;
        ctx.baseUpgrades.put(homeBase, tutorialUpgrades);
        st.homeBaseId = homeBase.id;
        if (st.playerFaction == Faction.ALLY) ctx.allyBase = homeBase;
        if (st.playerFaction == Faction.ENEMY) ctx.enemyBase = homeBase;

        ctx.player = new Player(ShipRole.FRIGATE, playerX, playerY);
        ctx.player.faction = st.playerFaction;
        ctx.player.name = "Player";
        ctx.player.angle = 0.0;
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.player.setPowerPreset(Ship.PowerPreset.BALANCED);
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
        st.sawFlightDeck |= ctx.flightDeckOpen;

        if (ctx.player.isCarrier) {
            int activeWing = CarrierSystem.countActiveWingByCarrier(ctx, ctx.player);
            if (activeWing > 0) st.launchedWing = true;
            st.carrierModeChanged |= ctx.player.carrierCommandMode != st.baselineCarrierMode;
            st.carrierAutoLaunchChanged |= ctx.player.carrierAutoLaunch != st.baselineCarrierAutoLaunch;
        }

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
                    enterStep(ctx, st, StepId.PING_WEAPONS_RANGE, true);
                }
            }
            case PING_WEAPONS_RANGE -> {
                if (hasPingNear(ctx, st.weaponsX, st.weaponsY, PING_MATCH_RADIUS)) {
                    enterStep(ctx, st, StepId.LOCK_AND_FIRE, true);
                }
            }
            case LOCK_AND_FIRE -> {
                Ship target = shipById(ctx, st.combatTargetId);
                boolean targetDamaged = target == null || target.hp < target.hpMax || !target.alive || target.dying;
                boolean targetLocked = (ctx.lockedTarget != null && ctx.lockedTarget.id == st.combatTargetId);
                if (targetDamaged && (targetLocked || target == null || !target.alive || target.dying)) {
                    enterStep(ctx, st, StepId.XRAY_AND_DIAGNOSE, true);
                }
            }
            case XRAY_AND_DIAGNOSE -> {
                if (ctx.xrayFocusedRoom != null && ctx.xrayFilterMode != GameContext.XrayFilterMode.ALL) {
                    enterStep(ctx, st, StepId.MINE_ORE, true);
                }
            }
            case MINE_ORE -> {
                Ship base = shipById(ctx, st.homeBaseId);
                int totalOre = ((ctx.player == null) ? 0 : ctx.player.cargo) + ((base == null) ? 0 : base.oreStockpile);
                if (totalOre > st.miningStartOre) {
                    enterStep(ctx, st, StepId.DOCK_AND_UPGRADE, true);
                }
            }
            case DOCK_AND_UPGRADE -> {
                Ship docked = EconomySystem.getDockedFriendlyBase(ctx);
                boolean dockedHome = docked != null && docked.id == st.homeBaseId;
                if (dockedHome && st.sawBase && currentHangarLevel(ctx, st.homeBaseId) > st.hangarTierAtUpgradeStep) {
                    enterStep(ctx, st, StepId.SWAP_TO_CARRIER, true);
                }
            }
            case SWAP_TO_CARRIER -> {
                Ship docked = EconomySystem.getDockedFriendlyBase(ctx);
                boolean dockedHome = docked != null && docked.id == st.homeBaseId;
                if (dockedHome && st.sawShop && ctx.player.isCarrier) {
                    enterStep(ctx, st, StepId.POWER_AND_CREW, true);
                }
            }
            case POWER_AND_CREW -> {
                if (st.sawPower && st.sawCrew && powerAdjusted(ctx, st) && crewAdjusted(ctx, st)) {
                    enterStep(ctx, st, StepId.DAMAGE_CONTROL, true);
                }
            }
            case DAMAGE_CONTROL -> {
                if (!st.seededDamageControlFire) {
                    seedDamageControlFire(ctx, st);
                }
                if (ctx.player.totalFireIntensity() <= 0.05) {
                    enterStep(ctx, st, StepId.CARRIER_WING, true);
                }
            }
            case CARRIER_WING -> {
                if (st.sawFlightDeck && st.launchedWing && (st.carrierModeChanged || st.carrierAutoLaunchChanged)) {
                    enterStep(ctx, st, StepId.WARP_TRIAL, true);
                }
            }
            case WARP_TRIAL -> {
                if (nearPoint(ctx.waypointX, ctx.waypointY, st.gammaX, st.gammaY, 150.0) && ctx.playerTeleportCharging) {
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
        if (currentStep(st) == StepId.COMPLETE) return "COMMAND SCHOOL   COMPLETE";
        int totalSteps = StepId.values().length - 1;
        return "COMMAND SCHOOL   STEP " + Math.min(totalSteps, st.stepIndex + 1) + "/" + totalSteps;
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
                    "Fly to NAV BETA. The waypoint ring will guide you across the sector.";
            case PING_WEAPONS_RANGE ->
                    "Move your cursor over WEAPONS RANGE and press P to drop a tactical ping for your crew.";
            case LOCK_AND_FIRE ->
                    "At the weapons range, lock the tutorial drone with L and hit it with SPACE. SHIFT fires your missile secondary if you want extra punch."
                            + combatStatus(target);
            case XRAY_AND_DIAGNOSE ->
                    "Use the always-on x-ray panel: press ` to cycle a non-ALL filter, then click any room on your ship to focus it.";
            case MINE_ORE ->
                    "Move to the mining pocket and hold F near an asteroid until you collect ore. Cargo fills automatically when close enough.";
            case DOCK_AND_UPGRADE ->
                    "Dock at Tutorial Base, open the base menu with B, and buy one more HANGAR level with key 5.";
            case SWAP_TO_CARRIER ->
                    "Open the loadout/shop with TAB and swap your hull to a carrier. This unlocks the flight deck lesson.";
            case POWER_AND_CREW ->
                    "Review bridge systems: open O and change a power state or preset, then open H and change at least one crew directive or station mode.";
            case DAMAGE_CONTROL ->
                    "A scripted fire is active on your ship. Open H, go to Engineering, and suppress the hotspot with key 8 until the hazard is gone.";
            case CARRIER_WING ->
                    "Carrier lesson: open the flight deck with / at least once, launch a wing with C, and toggle wing behavior with V or auto-launch with Z.";
            case WARP_TRIAL ->
                    "Open the map with M, set NAV GAMMA as your waypoint, then press - or BACKSPACE to begin a battlefield warp charge.";
            case COMPLETE ->
                    "Command school complete. You covered navigation, tactical pings, combat, x-ray, mining, docking, upgrades, loadouts, power, crew, hazards, carrier ops, and warp.";
        };
    }

    public static String contextHint(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        return switch (currentStep(st)) {
            case MOVE_TO_ALPHA -> "Throttle with W/S, steer with A/D, and bring the ship into the NAV ALPHA ring.";
            case MAP_TO_BETA -> "Press M to open the full map. Click the NAV BETA marker to place a waypoint, then close the map.";
            case TRAVEL_TO_BETA -> "Use the minimap waypoint marker to cross the sector. Manual flight is enough here.";
            case PING_WEAPONS_RANGE -> "Press P while your cursor is over the WEAPONS RANGE marker to place a command ping.";
            case LOCK_AND_FIRE -> "Press L to lock the drone under your cursor, then fire with SPACE. SHIFT uses your secondary battery.";
            case XRAY_AND_DIAGNOSE -> "The x-ray panel is live at all times. Press ` to cycle filters, then click a room on your ship to focus it.";
            case MINE_ORE -> "Asteroids with ore can be mined by holding F while close. Bring any ore you gather back to base.";
            case DOCK_AND_UPGRADE -> "Dock at Tutorial Base, press B, then press 5 to push the hangar to tier 3.";
            case SWAP_TO_CARRIER -> "With hangar tier 3 unlocked, press TAB and choose a carrier hull from the loadout screen.";
            case POWER_AND_CREW -> "Change one power state with O or Y, then use H to change any crew directive or station mode.";
            case DAMAGE_CONTROL -> "Open H, switch to Engineering, and press 8 to suppress the fire hotspot until it is contained.";
            case CARRIER_WING -> "Open / once to review the deck, then use C to launch a squadron and V or Z to change wing behavior.";
            case WARP_TRIAL -> "Open M, click NAV GAMMA, close the map, then press - or BACKSPACE to start warp charge.";
            case COMPLETE -> "Tutorial complete. Stay in this sandbox or press F10 to return to the menu.";
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
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: NAV ALPHA", 2.2);
            }
            case MAP_TO_BETA -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: SET NAV BETA", 2.6);
            }
            case TRAVEL_TO_BETA -> {
                ctx.waypointX = st.betaX;
                ctx.waypointY = st.betaY;
                if (announce) EventSystem.showBanner(ctx, "WAYPOINT CONFIRMED: NAV BETA", 2.0);
            }
            case PING_WEAPONS_RANGE -> {
                ctx.waypointX = st.weaponsX;
                ctx.waypointY = st.weaponsY;
                if (announce) EventSystem.showBanner(ctx, "TACTICAL PING: WEAPONS RANGE", 2.0);
            }
            case LOCK_AND_FIRE -> {
                ctx.waypointX = st.weaponsX;
                ctx.waypointY = st.weaponsY;
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: WEAPONS RANGE", 2.0);
            }
            case XRAY_AND_DIAGNOSE -> {
                ctx.waypointX = st.weaponsX;
                ctx.waypointY = st.weaponsY;
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: XRAY DIAGNOSTICS", 2.1);
            }
            case MINE_ORE -> {
                Ship base = shipById(ctx, st.homeBaseId);
                st.miningStartOre = ((ctx.player == null) ? 0 : ctx.player.cargo) + ((base == null) ? 0 : base.oreStockpile);
                ctx.waypointX = st.miningX;
                ctx.waypointY = st.miningY;
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: MINING POCKET", 2.0);
            }
            case DOCK_AND_UPGRADE -> {
                st.hangarTierAtUpgradeStep = currentHangarLevel(ctx, st.homeBaseId);
                focusHomeBase(ctx, st);
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: UPGRADE HANGAR", 2.2);
            }
            case SWAP_TO_CARRIER -> {
                focusHomeBase(ctx, st);
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: CARRIER LOADOUT", 2.2);
            }
            case POWER_AND_CREW -> {
                focusHomeBase(ctx, st);
                capturePowerAndCrewBaseline(ctx, st);
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: BRIDGE SYSTEMS", 2.1);
            }
            case DAMAGE_CONTROL -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                st.seededDamageControlFire = false;
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: DAMAGE CONTROL", 2.1);
            }
            case CARRIER_WING -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                captureCarrierBaseline(ctx, st);
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: CARRIER OPS", 2.1);
            }
            case WARP_TRIAL -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                if (announce) EventSystem.showBanner(ctx, "COMMAND SCHOOL: WARP TO NAV GAMMA", 2.4);
            }
            case COMPLETE -> {
                ctx.waypointX = Double.NaN;
                ctx.waypointY = Double.NaN;
                EventSystem.showBanner(ctx, "COMMAND SCHOOL COMPLETE", 3.0);
            }
        }
    }

    private static void focusHomeBase(GameContext ctx, TutorialState st) {
        Ship base = shipById(ctx, st.homeBaseId);
        if (base != null) {
            ctx.waypointX = base.x;
            ctx.waypointY = base.y;
        }
    }

    private static void capturePowerAndCrewBaseline(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        st.baselinePowerPreset = ctx.player.powerPreset;
        st.baselinePowerBuses = ctx.player.powerBusFractions();
        st.baselineCaptainDirective = ctx.captainDirective;
        st.baselineHelmMode = ctx.helmMode;
        st.baselineTacticalMode = ctx.tacticalMode;
        st.baselineEngineeringMode = ctx.engineeringMode;
        st.baselineScienceJamming = ctx.scienceJamming;
    }

    private static void captureCarrierBaseline(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        st.sawFlightDeck = false;
        st.launchedWing = false;
        st.carrierModeChanged = false;
        st.carrierAutoLaunchChanged = false;
        st.baselineCarrierMode = ctx.player.carrierCommandMode;
        st.baselineCarrierAutoLaunch = ctx.player.carrierAutoLaunch;
    }

    private static boolean powerAdjusted(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return false;
        if (ctx.player.isOverloadActive() || ctx.player.isEmergencyThrustActive()) return true;
        if (ctx.player.powerPreset != st.baselinePowerPreset) return true;
        return powerBusDelta(ctx.player.powerBusFractions(), st.baselinePowerBuses) > 0.045;
    }

    private static boolean crewAdjusted(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null) return false;
        return ctx.captainDirective != st.baselineCaptainDirective
                || ctx.helmMode != st.baselineHelmMode
                || ctx.tacticalMode != st.baselineTacticalMode
                || ctx.engineeringMode != st.baselineEngineeringMode
                || ctx.scienceJamming != st.baselineScienceJamming
                || ctx.activeCrewStation != GameContext.CrewStation.CAPTAIN;
    }

    private static double powerBusDelta(double[] now, double[] before) {
        if (now == null || before == null || now.length == 0 || before.length == 0) return 0.0;
        int n = Math.min(now.length, before.length);
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            total += Math.abs(now[i] - before[i]);
        }
        return total;
    }

    private static void seedDamageControlFire(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        ctx.player.seedRoomFire(ShipRoomLayout.RoomId.ENGINES, 1.35);
        ctx.player.seedRoomFire(ShipRoomLayout.RoomId.POWER_CONDUITS, 0.55);
        st.seededDamageControlFire = true;
        EventSystem.showBanner(ctx, "ENGINEERING ALERT: FIRE IN ENGINES", 1.4);
    }

    private static List<Marker> markers(GameContext ctx, TutorialState st) {
        ArrayList<Marker> out = new ArrayList<>();
        Ship base = shipById(ctx, st.homeBaseId);
        if (base != null) out.add(new Marker("HOME BASE", base.x, base.y, 120.0));
        out.add(new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS));
        out.add(new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS));
        out.add(new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS));
        out.add(new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS));
        out.add(new Marker("NAV GAMMA", st.gammaX, st.gammaY, POINT_REACHED_RADIUS));
        return out;
    }

    private static Marker activeMarker(GameContext ctx, TutorialState st) {
        return switch (currentStep(st)) {
            case MOVE_TO_ALPHA -> new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS);
            case MAP_TO_BETA, TRAVEL_TO_BETA -> new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS);
            case PING_WEAPONS_RANGE, LOCK_AND_FIRE -> new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS);
            case MINE_ORE -> new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS);
            case DOCK_AND_UPGRADE, SWAP_TO_CARRIER, POWER_AND_CREW -> {
                Ship base = shipById(ctx, st.homeBaseId);
                yield (base == null) ? null : new Marker("HOME BASE", base.x, base.y, 120.0);
            }
            case WARP_TRIAL -> new Marker("NAV GAMMA", st.gammaX, st.gammaY, POINT_REACHED_RADIUS);
            case XRAY_AND_DIAGNOSE, DAMAGE_CONTROL, CARRIER_WING, COMPLETE -> null;
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
        g2.drawString(marker.label, x - 42, y - r - 10);
    }

    private static String combatStatus(Ship target) {
        if (target == null) return "";
        return "   TARGET: " + target.hp + "/" + target.hpMax + " HULL";
    }

    private static boolean near(Ship ship, double x, double y, double radius) {
        return ship != null && GameMath.dist2(ship.x, ship.y, x, y) <= radius * radius;
    }

    private static boolean nearPoint(double x1, double y1, double x2, double y2, double radius) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1)) return false;
        return GameMath.dist2(x1, y1, x2, y2) <= radius * radius;
    }

    private static boolean hasPingNear(GameContext ctx, double x, double y, double radius) {
        if (ctx == null || ctx.mapPings == null) return false;
        double r2 = radius * radius;
        for (Renderer.MapPing ping : ctx.mapPings) {
            if (ping == null) continue;
            if (GameMath.dist2(ping.x, ping.y, x, y) <= r2) return true;
        }
        return false;
    }

    private static int currentHangarLevel(GameContext ctx, int baseId) {
        Ship base = shipById(ctx, baseId);
        if (ctx == null || base == null) return 0;
        BaseUpgrades up = ctx.baseUpgrades.get(base);
        return (up == null) ? 0 : up.hangarLv;
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
