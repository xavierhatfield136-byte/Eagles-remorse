import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

public final class UISystem {
    private UISystem(){}

    public static void closeAllOverlays(GameContext ctx) {
        if (ctx == null) return;
        boolean hadOverlay = ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen || ctx.powerManagementOpen || ctx.crewStationsOpen;
        ctx.shopOpen = false;
        ctx.baseMenuOpen = false;
        ctx.mapOpen = false;
        ctx.powerManagementOpen = false;
        ctx.crewStationsOpen = false;
        clearManualCombatInputs(ctx);
        if (!ctx.gameOver) ctx.state = GameState.RUNNING;
        if (hadOverlay) AudioSystem.onUiClose(ctx);
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.shopOpen = !ctx.shopOpen;
        if (ctx.shopOpen) {
            ctx.baseMenuOpen = false;
            ctx.mapOpen = false;
            ctx.powerManagementOpen = false;
            ctx.crewStationsOpen = false;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.SHOP;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = GameState.RUNNING;
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void toggleMap(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.mapOpen = !ctx.mapOpen;
        if (ctx.mapOpen) {
            ctx.shopOpen = false;
            ctx.baseMenuOpen = false;
            ctx.powerManagementOpen = false;
            ctx.crewStationsOpen = false;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.MAP;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = GameState.RUNNING;
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void toggleBaseMenu(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        Ship dock = EconomySystem.getDockedFriendlyBase(ctx);
        if (dock == null) {
            EventSystem.showBanner(ctx, "DOCK AT A FRIENDLY BASE TO UPGRADE", 2.0);
            return;
        }
        ctx.baseMenuOpen = !ctx.baseMenuOpen;
        if (ctx.baseMenuOpen) {
            ctx.shopOpen = false;
            ctx.mapOpen = false;
            ctx.powerManagementOpen = false;
            ctx.crewStationsOpen = false;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.BASE_MENU;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = GameState.RUNNING;
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void togglePowerManagement(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        if (ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;

        ctx.powerManagementOpen = !ctx.powerManagementOpen;
        if (ctx.powerManagementOpen) {
            ctx.shopOpen = false;
            ctx.baseMenuOpen = false;
            ctx.mapOpen = false;
            ctx.crewStationsOpen = false;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.POWER_MANAGEMENT;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = GameState.RUNNING;
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void toggleCrewStations(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        if (ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;

        ctx.crewStationsOpen = !ctx.crewStationsOpen;
        if (ctx.crewStationsOpen) {
            ctx.shopOpen = false;
            ctx.baseMenuOpen = false;
            ctx.mapOpen = false;
            ctx.powerManagementOpen = false;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.CREW_STATIONS;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = GameState.RUNNING;
            AudioSystem.onUiClose(ctx);
        }
    }
    public static boolean handleCoreMenuClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || e == null) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;

        int idx = Renderer.coreMenuButtonAt(viewportW, viewportH, e.getX(), e.getY());
        if (idx < 0) return false;

        switch (idx) {
            case 0 -> toggleShop(ctx);
            case 1 -> toggleBaseMenu(ctx);
            case 2 -> toggleMap(ctx);
            case 3 -> togglePowerManagement(ctx);
            case 4 -> toggleCrewStations(ctx);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void selectPowerManagementSlot(GameContext ctx, int idx) {
        if (ctx == null) return;
        ctx.powerManagementFocus = Math.max(0, Math.min(3, idx));
    }

    public static void cyclePowerManagementSlot(GameContext ctx, int dir) {
        if (ctx == null) return;
        int step = (dir < 0) ? -1 : 1;
        int next = ctx.powerManagementFocus + step;
        if (next < 0) next = 3;
        if (next > 3) next = 0;
        ctx.powerManagementFocus = next;
    }

    public static void stepPowerAllocation(GameContext ctx, int dir) {
        if (ctx == null || ctx.player == null) return;
        adjustPowerAllocation(ctx, ctx.powerManagementFocus, (dir < 0) ? -0.05 : 0.05);
    }

    public static void adjustPowerAllocation(GameContext ctx, int channel, double delta) {
        if (ctx == null || ctx.player == null) return;
        if (channel < 0 || channel > 3) return;
        if (!Double.isFinite(delta) || Math.abs(delta) < 1e-9) return;

        double[] p = new double[]{
                ctx.player.powerEnginesFrac(),
                ctx.player.powerShieldsFrac(),
                ctx.player.powerWeaponsFrac(),
                ctx.player.powerSystemsFrac()
        };

        double oldVal = p[channel];
        double newVal = Math.max(0.0, Math.min(1.0, oldVal + delta));
        double applied = newVal - oldVal;
        if (Math.abs(applied) < 1e-9) return;
        p[channel] = newVal;

        if (applied > 0.0) {
            double othersTotal = 0.0;
            for (int i = 0; i < 4; i++) if (i != channel) othersTotal += p[i];
            if (othersTotal <= 1e-9) {
                double each = (1.0 - p[channel]) / 3.0;
                for (int i = 0; i < 4; i++) if (i != channel) p[i] = each;
            } else {
                double remove = applied;
                for (int i = 0; i < 4; i++) {
                    if (i == channel) continue;
                    double share = p[i] / othersTotal;
                    p[i] -= remove * share;
                    if (p[i] < 0.0) p[i] = 0.0;
                }
            }
        } else {
            double freed = -applied;
            double avail = 0.0;
            for (int i = 0; i < 4; i++) {
                if (i == channel) continue;
                avail += (1.0 - p[i]);
            }
            if (avail <= 1e-9) {
                double each = (1.0 - p[channel]) / 3.0;
                for (int i = 0; i < 4; i++) if (i != channel) p[i] = each;
            } else {
                for (int i = 0; i < 4; i++) {
                    if (i == channel) continue;
                    double share = (1.0 - p[i]) / avail;
                    p[i] += freed * share;
                }
            }
        }

        normalizePower(p);
        ctx.player.setPowerAllocation(p[0], p[1], p[2], p[3]);
        // Manual engineering input immediately overrides automation.
        ctx.engineeringAutomation = false;
    }

    public static void applyPowerPreset(GameContext ctx, Ship.PowerPreset preset) {
        if (ctx == null || ctx.player == null) return;
        if (preset == null) preset = Ship.PowerPreset.BALANCED;
        ctx.player.setPowerPreset(preset);
        ctx.engineeringAutomation = false;
    }

    private static void normalizePower(double[] p) {
        if (p == null || p.length < 4) return;
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            if (!Double.isFinite(p[i]) || p[i] < 0.0) p[i] = 0.0;
            sum += p[i];
        }
        if (sum <= 1e-9) {
            p[0] = 0.25;
            p[1] = 0.25;
            p[2] = 0.25;
            p[3] = 0.25;
            return;
        }
        for (int i = 0; i < 4; i++) p[i] /= sum;
    }

    public static void selectCrewStation(GameContext ctx, GameContext.CrewStation station) {
        if (ctx == null || station == null) return;
        ctx.activeCrewStation = station;
    }

    public static void cycleCrewStation(GameContext ctx, int dir) {
        if (ctx == null) return;
        GameContext.CrewStation[] values = GameContext.CrewStation.values();
        int step = (dir < 0) ? -1 : 1;
        int idx = ctx.activeCrewStation.ordinal() + step;
        if (idx < 0) idx = values.length - 1;
        if (idx >= values.length) idx = 0;
        ctx.activeCrewStation = values[idx];
    }

    public static boolean stationAutomation(GameContext ctx, GameContext.CrewStation station) {
        if (ctx == null || station == null) return false;
        return switch (station) {
            case CAPTAIN -> ctx.captainAutomation;
            case HELM -> ctx.helmAutomation;
            case TACTICAL -> ctx.tacticalAutomation;
            case ENGINEERING -> ctx.engineeringAutomation;
            case SCIENCE -> ctx.scienceAutomation;
        };
    }

    public static void setStationAutomation(GameContext ctx, GameContext.CrewStation station, boolean enabled) {
        if (ctx == null || station == null) return;
        switch (station) {
            case CAPTAIN -> ctx.captainAutomation = enabled;
            case HELM -> ctx.helmAutomation = enabled;
            case TACTICAL -> ctx.tacticalAutomation = enabled;
            case ENGINEERING -> ctx.engineeringAutomation = enabled;
            case SCIENCE -> ctx.scienceAutomation = enabled;
        }
    }

    public static void toggleActiveStationAutomation(GameContext ctx) {
        if (ctx == null) return;
        GameContext.CrewStation s = ctx.activeCrewStation;
        setStationAutomation(ctx, s, !stationAutomation(ctx, s));
    }

    public static void handleMapClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        Rectangle rect = Renderer.getStrategicMapRect(viewportW, viewportH);
        if (!rect.contains(e.getPoint())) return;

        double nx = (e.getX() - rect.x) / (double) rect.width;
        double ny = (e.getY() - rect.y) / (double) rect.height;

        ctx.waypointX = GameMath.clamp(nx * ctx.WORLD_W, 0, ctx.WORLD_W);
        ctx.waypointY = GameMath.clamp(ny * ctx.WORLD_H, 0, ctx.WORLD_H);

        addPing(ctx, ctx.waypointX, ctx.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.2);
    }

    public static void setWaypointAtCursor(GameContext ctx, PlayerControl controls) {
        double wx = CameraSystem.screenToWorldX(ctx, controls.getMouseX());
        double wy = CameraSystem.screenToWorldY(ctx, controls.getMouseY());
        ctx.waypointX = GameMath.clamp(wx, 0, ctx.WORLD_W);
        ctx.waypointY = GameMath.clamp(wy, 0, ctx.WORLD_H);
        addPing(ctx, ctx.waypointX, ctx.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.0);
    }

    public static void pingAtCursor(GameContext ctx, PlayerControl controls) {
        double wx = CameraSystem.screenToWorldX(ctx, controls.getMouseX());
        double wy = CameraSystem.screenToWorldY(ctx, controls.getMouseY());
        addPing(ctx, wx, wy, 1.8);
    }

    public static void addPing(GameContext ctx, double x, double y, double seconds) {
        int factionCode = 0;
        if (ctx.player != null) {
            factionCode = pingCodeForFaction(ctx.player.faction);
        }
        ctx.mapPings.add(new Renderer.MapPing(x, y, seconds, factionCode));
    }

    public static void updatePings(GameContext ctx, double dt) {
        for (int i = ctx.mapPings.size() - 1; i >= 0; i--) {
            Renderer.MapPing p = ctx.mapPings.get(i);
            p.t -= dt;
            if (p.t <= 0) ctx.mapPings.remove(i);
        }
    }

    public static void tryBuyBeamBolt(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;

        Player player = ctx.player;
        int cost = 220;

        if (player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) {
            EventSystem.showBanner(ctx, "BEAM BOLT ALREADY EQUIPPED", 1.4);
            return;
        }
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }

        ctx.credits -= cost;
        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.BEAM_BOLT;
        player.applyPrimaryWeaponFamily();
        EventSystem.showBanner(ctx, "BEAM BOLT ONLINE", 1.6);
    }

    public static void tryEquipEnergyBolt(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;

        Player player = ctx.player;
        if (player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.ENERGY_BOLT) {
            EventSystem.showBanner(ctx, "ENERGY BOLT ALREADY EQUIPPED", 1.4);
            return;
        }

        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
        player.applyPrimaryWeaponFamily();
        EventSystem.showBanner(ctx, "ENERGY BOLT ONLINE", 1.2);
    }

    public static void tryBuyHullPlating(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;
        int cost = 60;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        ctx.player.hpMax += 10;
        ctx.player.healHull(10);
        EventSystem.showBanner(ctx, "HULL UPGRADED", 1.2);
    }

    public static void tryBuyShieldArray(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;
        Player p = ctx.player;
        if (!p.shieldActive || p.shieldMax <= 0) {
            EventSystem.showBanner(ctx, "NO SHIELD SYSTEM", 1.4);
            return;
        }
        int cost = 70;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        p.shieldMax += 12.0;
        p.shieldRegen += 0.3;
        p.shield += 12.0;
        EventSystem.showBanner(ctx, "SHIELD ARRAY UPGRADED", 1.2);
    }

    public static void tryAddGunTurret(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;
        int cost = 100;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        ctx.player.addGunTurret();
        EventSystem.showBanner(ctx, "GUN TURRET ADDED", 1.2);
    }

    public static void tryAddMissileRack(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;
        int cost = 140;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        ctx.player.addMissileTurret();
        EventSystem.showBanner(ctx, "MISSILE RACK ADDED", 1.2);
    }

    public static void tryUpgradeCIWS(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;
        if (!ctx.player.hasCIWS) {
            EventSystem.showBanner(ctx, "NO CIWS SYSTEM", 1.4);
            return;
        }
        if (ctx.player.isCIWSUpgradeMaxed()) {
            EventSystem.showBanner(ctx, "CIWS AT MAX LEVEL", 1.2);
            return;
        }
        int cost = 120;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        if (ctx.player.upgradeCIWS()) {
            EventSystem.showBanner(ctx, "CIWS UPGRADED", 1.2);
        } else {
            // Safety fallback in case CIWS state changed between checks.
            ctx.credits += cost;
            EventSystem.showBanner(ctx, "CIWS AT MAX LEVEL", 1.2);
        }
    }

    public static void tryCarrierLaunch(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        Player p = ctx.player;

        boolean launched = CarrierSystem.tryLaunchOne(ctx, p);
        if (launched) {
            int active = CarrierSystem.countActiveWingByCarrier(ctx, p);
            EventSystem.showBanner(ctx, "WING LAUNCHED  " + active + "/" + p.maxFighters, 1.1);
            return;
        }

        int active = CarrierSystem.countActiveWingByCarrier(ctx, p);
        if (active >= Math.max(0, p.maxFighters)) {
            EventSystem.showBanner(ctx, "DECK FULL  " + active + "/" + p.maxFighters, 1.2);
            return;
        }
        if (!p.canLaunchFighter()) {
            EventSystem.showBanner(ctx, "DECK CYCLE IN PROGRESS", 1.2);
            return;
        }
        EventSystem.showBanner(ctx, "LAUNCH NOT AVAILABLE", 1.2);
    }

    public static void tryCarrierRecall(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        int recalled = CarrierSystem.recallWing(ctx, ctx.player);
        if (recalled <= 0) {
            EventSystem.showBanner(ctx, "NO WING TO RECALL", 1.1);
            return;
        }
        EventSystem.showBanner(ctx, "RECALL ORDER  " + recalled + " CRAFT", 1.2);
    }

    public static void tryCarrierToggleMode(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        Player p = ctx.player;
        p.carrierCommandMode = (p.carrierCommandMode == Ship.CarrierCommandMode.ATTACK)
                ? Ship.CarrierCommandMode.DEFEND
                : Ship.CarrierCommandMode.ATTACK;
        EventSystem.showBanner(ctx, "WING MODE: " + p.carrierCommandMode.name(), 1.2);
    }

    public static void tryCarrierToggleAutoLaunch(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        Player p = ctx.player;
        p.carrierAutoLaunch = !p.carrierAutoLaunch;
        EventSystem.showBanner(ctx, "AUTO-LAUNCH: " + (p.carrierAutoLaunch ? "ON" : "OFF"), 1.2);
    }

    public static void trySwapHull(GameContext ctx, ShipRole role, int cost, int requiredTier) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.shopOpen) return;
        if (ctx.player.role == role) {
            EventSystem.showBanner(ctx, "HULL ALREADY EQUIPPED", 1.2);
            return;
        }
        int hangarTier = getMaxHangarTierForPlayer(ctx);
        if (hangarTier < requiredTier) {
            EventSystem.showBanner(ctx, "HANGAR TIER TOO LOW", 1.4);
            return;
        }
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        Ship.PrimaryWeaponFamily retainedPrimary = ctx.player.primaryWeaponFamily;
        ctx.credits -= cost;
        ctx.player.applyHull(role, ctx.player.x, ctx.player.y);
        ctx.player.primaryWeaponFamily = retainedPrimary;
        ctx.player.applyPrimaryWeaponFamily();
        EventSystem.showBanner(ctx, "HULL SWAPPED", 1.2);
    }

    public static void tryUpgradeBase(GameContext ctx, int which) {
        if (ctx == null) return;
        if (!ctx.baseMenuOpen) return;
        Ship base = EconomySystem.getDockedFriendlyBase(ctx);
        if (base == null) {
            EventSystem.showBanner(ctx, "DOCK AT A FRIENDLY BASE", 1.4);
            return;
        }
        BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(base, k -> new BaseUpgrades());

        int max = switch (which) {
            case 5 -> 3;
            default -> 5;
        };

        int current = switch (which) {
            case 1 -> up.hullLv;
            case 2 -> up.shieldLv;
            case 3 -> up.turretLv;
            case 4 -> up.miningLv;
            case 5 -> up.hangarLv;
            default -> 0;
        };

        if (current >= max) {
            EventSystem.showBanner(ctx, "UPGRADE MAXED", 1.2);
            return;
        }

        int nextLv = current + 1;
        int cCost = switch (which) {
            case 1 -> 150 + 200 * nextLv;
            case 2 -> 170 + 210 * nextLv;
            case 3 -> 210 + 250 * nextLv;
            case 4 -> 140 + 170 * nextLv;
            case 5 -> 380 + 420 * nextLv;
            default -> 0;
        };
        int oCost = switch (which) {
            case 1 -> 40 + 70 * nextLv;
            case 2 -> 50 + 80 * nextLv;
            case 3 -> 60 + 90 * nextLv;
            case 4 -> 40 + 110 * nextLv;
            case 5 -> 100 + 170 * nextLv;
            default -> 0;
        };

        if (ctx.credits < cCost || base.oreStockpile < oCost) {
            EventSystem.showBanner(ctx, "INSUFFICIENT RESOURCES", 1.4);
            return;
        }

        ctx.credits -= cCost;
        base.oreStockpile -= oCost;

        switch (which) {
            case 1 -> {
                up.hullLv++;
                base.hpMax += 40;
                base.healHull(40);
                EventSystem.showBanner(ctx, "HULL FORTIFIED", 1.2);
            }
            case 2 -> {
                up.shieldLv++;
                base.shieldMax += 30.0;
                base.shieldRegen += 0.8;
                base.shieldActive = true;
                base.shield += 30.0;
                EventSystem.showBanner(ctx, "SHIELD ARRAY UPGRADED", 1.2);
            }
            case 3 -> {
                up.turretLv++;
                applyTurretSystemsUpgrade(base, 1);
                EventSystem.showBanner(ctx, "TURRET SYSTEMS UPGRADED", 1.2);
            }
            case 4 -> {
                up.miningLv++;
                ctx.miningBaseMul = Math.min(2.0, ctx.miningBaseMul + 0.06);
                ctx.orePriceBaseMul = Math.min(2.0, ctx.orePriceBaseMul + 0.05);
                EventSystem.showBanner(ctx, "MINING OPS UPGRADED", 1.2);
            }
            case 5 -> {
                up.hangarLv++;
                EventSystem.showBanner(ctx, "HANGAR EXPANDED", 1.2);
            }
        }
    }

    public static void applyTurretSystemsUpgrade(Ship ship, int levels) {
        if (ship == null || ship.turrets == null) return;
        int n = Math.max(0, levels);
        if (n <= 0) return;
        for (Turret t : ship.turrets) {
            if (t == null) continue;
            for (int i = 0; i < n; i++) {
                t.damage = Math.max(1, t.damage + 1);
                t.cooldown = Math.max(0.05, t.cooldown * 0.95);
            }
        }
    }

    public static int getMaxHangarTierForPlayer(GameContext ctx) {
        if (ctx == null || ctx.baseUpgrades == null) return 0;
        if (ctx.config != null && ctx.config.mode == GameMode.SHOOTING_RANGE) return 3;
        int best = 0;
        for (java.util.Map.Entry<Ship, BaseUpgrades> e : ctx.baseUpgrades.entrySet()) {
            Ship b = e.getKey();
            if (b == null || !b.alive) continue;
            if (!TeamSystem.isFriendlyToPlayer(ctx, b.faction)) continue;
            BaseUpgrades up = e.getValue();
            if (up == null) continue;
            if (up.hangarLv > best) best = up.hangarLv;
        }
        return best;
    }

    public static void setHelmMode(GameContext ctx, GameContext.HelmMode mode) {
        if (ctx == null || mode == null) return;
        ctx.helmMode = mode;
        ctx.helmAutomation = true;
    }

    public static void setTacticalMode(GameContext ctx, GameContext.TacticalMode mode) {
        if (ctx == null || mode == null) return;
        ctx.tacticalMode = mode;
        ctx.tacticalAutomation = true;
    }

    public static void setEngineeringMode(GameContext ctx, GameContext.EngineeringMode mode) {
        if (ctx == null || mode == null) return;
        ctx.engineeringMode = mode;
        ctx.engineeringAutomation = true;
    }

    public static void applyCaptainPreset(GameContext ctx, int index) {
        if (ctx == null || ctx.player == null) return;
        GameContext.CaptainDirective directive = switch (index) {
            case 1 -> GameContext.CaptainDirective.BALANCED;
            case 2 -> GameContext.CaptainDirective.ATTACK;
            case 3 -> GameContext.CaptainDirective.DEFENSE;
            case 4 -> GameContext.CaptainDirective.EMERGENCY;
            default -> null;
        };
        if (directive == null) return;
        applyCaptainDirective(ctx, directive);
    }

    public static void applyCaptainDirective(GameContext ctx, GameContext.CaptainDirective directive) {
        if (ctx == null || ctx.player == null || directive == null) return;
        ctx.captainDirective = directive;
        switch (directive) {
            case ATTACK -> {
                ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                ctx.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.ATTACK;
                ctx.player.setPowerPreset(Ship.PowerPreset.ATTACK);
                ctx.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
            }
            case DEFENSE -> {
                ctx.helmMode = GameContext.HelmMode.MAINTAIN_RANGE;
                ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
            }
            case EMERGENCY -> {
                ctx.helmMode = GameContext.HelmMode.EVASIVE;
                ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.player.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
                ctx.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            }
            case MINE -> {
                ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.player.setPowerPreset(Ship.PowerPreset.PURSUIT);
                ctx.alliedFleetCommand = GameContext.FleetCommand.MINE;
            }
            case ESCORT -> {
                ctx.helmMode = GameContext.HelmMode.ORBIT;
                ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.player.setPowerPreset(Ship.PowerPreset.BALANCED);
                ctx.alliedFleetCommand = GameContext.FleetCommand.ESCORT;
            }
            case DEFEND -> {
                ctx.helmMode = GameContext.HelmMode.ORBIT;
                ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
            }
            case REPAIR -> {
                ctx.helmMode = GameContext.HelmMode.EVASIVE;
                ctx.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                ctx.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.player.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
                ctx.alliedFleetCommand = GameContext.FleetCommand.REPAIR;
            }
            case RTB -> {
                ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                ctx.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                ctx.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.alliedFleetCommand = GameContext.FleetCommand.RTB;
            }
            default -> {
                ctx.helmMode = GameContext.HelmMode.MAINTAIN_RANGE;
                ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.player.setPowerPreset(Ship.PowerPreset.BALANCED);
                ctx.alliedFleetCommand = GameContext.FleetCommand.AUTO;
            }
        }
        ctx.captainAutomation = true;
        ctx.helmAutomation = true;
        ctx.tacticalAutomation = true;
        ctx.engineeringAutomation = true;
        ctx.scienceAutomation = true;
    }

    public static void cycleAlliedFleetFormation(GameContext ctx) {
        if (ctx == null) return;
        GameContext.FleetFormation[] values = GameContext.FleetFormation.values();
        int next = ctx.alliedFleetFormation.ordinal() + 1;
        if (next >= values.length) next = 0;
        ctx.alliedFleetFormation = values[next];
        EventSystem.showBanner(ctx, "FLEET FORMATION: " + ctx.alliedFleetFormation.name(), 1.0);
    }

    public static void assignNearestFriendlyShipFleetOverride(GameContext ctx, GameContext.FleetCommand command) {
        if (ctx == null || ctx.player == null) return;
        if (command == null) command = GameContext.FleetCommand.AUTO;
        Ship target = nearestFriendlyShipForOverride(ctx);
        if (target == null) {
            EventSystem.showBanner(ctx, "NO FRIENDLY SHIP NEARBY", 1.1);
            return;
        }
        if (command == GameContext.FleetCommand.AUTO) {
            ctx.shipFleetCommandOverrides.remove(target.id);
            EventSystem.showBanner(ctx, "SHIP " + target.id + " ORDER CLEARED", 1.1);
            return;
        }
        ctx.shipFleetCommandOverrides.put(target.id, command);
        EventSystem.showBanner(ctx, "SHIP " + target.id + " ORDER: " + command.name(), 1.1);
    }

    private static Ship nearestFriendlyShipForOverride(GameContext ctx) {
        Ship best = null;
        double bestD2 = 850.0 * 850.0;
        for (Ship s : ctx.ships) {
            if (s == null || s == ctx.player) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) continue;
            if (s.faction == null || !s.faction.isFriendlyTo(ctx.player.faction)) continue;
            double d2 = GameMath.dist2(ctx.player.x, ctx.player.y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static void scienceLockNearest(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        double range = 1800.0 * Math.max(0.20, ctx.player.sensorRangeMultiplier());
        Ship target = TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, ctx.player.x, ctx.player.y, range);
        ctx.lockedTarget = target;
    }

    public static void scienceClearLock(GameContext ctx) {
        if (ctx == null) return;
        ctx.lockedTarget = null;
    }

    public static void toggleScienceJamming(GameContext ctx) {
        if (ctx == null) return;
        ctx.scienceJamming = !ctx.scienceJamming;
    }

    private static int pingCodeForFaction(Faction faction) {
        if (faction == null) return 0;
        if (faction == Faction.PLAYER) return 0;
        if (faction == Faction.ALLY) return 1;
        if (faction == Faction.ENEMY) return 2;
        if (faction == Faction.TEAM_C) return 3;
        if (faction == Faction.TEAM_D) return 4;
        return 0;
    }

    private static boolean ensurePlayerCarrier(GameContext ctx) {
        if (ctx == null || ctx.player == null) return false;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return false;
        if (!ctx.player.isCarrier) {
            EventSystem.showBanner(ctx, "CURRENT HULL IS NOT A CARRIER", 1.2);
            return false;
        }
        return true;
    }

    private static void clearManualCombatInputs(GameContext ctx) {
        if (ctx == null) return;
        ctx.firingPrimaryManual = false;
        ctx.firingSecondaryManual = false;
    }
}


