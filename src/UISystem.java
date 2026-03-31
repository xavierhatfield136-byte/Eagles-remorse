import app.config.GameMode;
import app.config.PlayerTeamChoice;
import app.persistence.MenuSettingsStore;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

public final class UISystem {
    private UISystem(){}

    public static void closeAllOverlays(GameContext ctx) {
        if (ctx == null) return;
        boolean hadOverlay = ctx.ui.hasBlockingOverlay();
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = false;
        ctx.ui.mapOpen = false;
        ctx.ui.powerManagementOpen = false;
        ctx.ui.crewStationsOpen = false;
        ctx.ui.flightDeckOpen = false;
        clearManualCombatInputs(ctx);
        if (!ctx.gameOver) ctx.state = GameState.RUNNING;
        if (hadOverlay) AudioSystem.onUiClose(ctx);
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.ui.shopOpen = !ctx.ui.shopOpen;
        if (ctx.ui.shopOpen) {
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
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
        ctx.ui.mapOpen = !ctx.ui.mapOpen;
        if (ctx.ui.mapOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
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
        ctx.ui.baseMenuOpen = !ctx.ui.baseMenuOpen;
        if (ctx.ui.baseMenuOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
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

        ctx.ui.powerManagementOpen = !ctx.ui.powerManagementOpen;
        if (ctx.ui.powerManagementOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
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

        ctx.ui.crewStationsOpen = !ctx.ui.crewStationsOpen;
        if (ctx.ui.crewStationsOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.flightDeckOpen = false;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.CREW_STATIONS;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = GameState.RUNNING;
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void toggleFlightDeck(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;

        ctx.ui.flightDeckOpen = !ctx.ui.flightDeckOpen;
        if (ctx.ui.flightDeckOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckFocus = Math.max(0, Math.min(4, ctx.ui.flightDeckFocus));
            clearManualCombatInputs(ctx);
            ctx.state = GameState.FLIGHT_DECK;
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

    public static boolean handleShopClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.player == null || e == null) return false;
        if (!ctx.ui.shopOpen) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;

        Renderer.ShopClickTarget target = Renderer.shopClickTargetAt(
                ctx.player, ctx.credits, getMaxHangarTierForPlayer(ctx),
                viewportW, viewportH, e.getX(), e.getY());
        if (target == null) return false;

        if (target.kind == Renderer.ShopClickTarget.Kind.UPGRADE) {
            performShopUpgradeById(ctx, target.upgradeId);
            return true;
        }
        if (target.kind == Renderer.ShopClickTarget.Kind.HULL && target.role != null) {
            performHullSwapByRole(ctx, target.role);
            return true;
        }
        return false;
    }

    public static void selectPowerManagementSlot(GameContext ctx, int idx) {
        if (ctx == null) return;
        ctx.ui.powerManagementFocus = Math.max(0, Math.min(5, idx));
    }

    public static void cyclePowerManagementSlot(GameContext ctx, int dir) {
        if (ctx == null) return;
        int step = (dir < 0) ? -1 : 1;
        int next = ctx.ui.powerManagementFocus + step;
        if (next < 0) next = 5;
        if (next > 5) next = 0;
        ctx.ui.powerManagementFocus = next;
    }

    public static void stepPowerAllocation(GameContext ctx, int dir) {
        if (ctx == null || ctx.player == null) return;
        adjustPowerAllocation(ctx, ctx.ui.powerManagementFocus, (dir < 0) ? -0.05 : 0.05);
    }

    public static void adjustPowerAllocation(GameContext ctx, int channel, double delta) {
        if (ctx == null || ctx.player == null) return;
        if (channel < 0 || channel > 5) return;
        if (!Double.isFinite(delta) || Math.abs(delta) < 1e-9) return;

        double[] p = ctx.player.powerBusFractions();

        double oldVal = p[channel];
        double newVal = Math.max(0.0, Math.min(1.0, oldVal + delta));
        double applied = newVal - oldVal;
        if (Math.abs(applied) < 1e-9) return;
        p[channel] = newVal;

        if (applied > 0.0) {
            double othersTotal = 0.0;
            for (int i = 0; i < p.length; i++) if (i != channel) othersTotal += p[i];
            if (othersTotal <= 1e-9) {
                double each = (1.0 - p[channel]) / Math.max(1.0, p.length - 1.0);
                for (int i = 0; i < p.length; i++) if (i != channel) p[i] = each;
            } else {
                double remove = applied;
                for (int i = 0; i < p.length; i++) {
                    if (i == channel) continue;
                    double share = p[i] / othersTotal;
                    p[i] -= remove * share;
                    if (p[i] < 0.0) p[i] = 0.0;
                }
            }
        } else {
            double freed = -applied;
            double avail = 0.0;
            for (int i = 0; i < p.length; i++) {
                if (i == channel) continue;
                avail += (1.0 - p[i]);
            }
            if (avail <= 1e-9) {
                double each = (1.0 - p[channel]) / Math.max(1.0, p.length - 1.0);
                for (int i = 0; i < p.length; i++) if (i != channel) p[i] = each;
            } else {
                for (int i = 0; i < p.length; i++) {
                    if (i == channel) continue;
                    double share = (1.0 - p[i]) / avail;
                    p[i] += freed * share;
                }
            }
        }

        normalizePower(p);
        ctx.player.setPowerBusAllocation(p[0], p[1], p[2], p[3], p[4], p[5]);
        // Manual engineering input immediately overrides automation.
        ctx.command.engineeringAutomation = false;
    }

    public static void applyPowerPreset(GameContext ctx, Ship.PowerPreset preset) {
        if (ctx == null || ctx.player == null) return;
        if (preset == null) preset = Ship.PowerPreset.BALANCED;
        ctx.player.setPowerPreset(preset);
        ctx.command.engineeringAutomation = false;
    }

    public static void toggleOverloadMode(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        ctx.command.engineeringAutomation = false;
        boolean before = ctx.player.isOverloadActive();
        boolean changed = ctx.player.toggleOverloadMode();
        if (!changed && !before && !ctx.player.isOverloadAvailable()) {
            EventSystem.showBanner(ctx, "OVERLOAD COOLING DOWN", 1.0);
            return;
        }
        EventSystem.showBanner(ctx, "OVERLOAD: " + (ctx.player.isOverloadActive() ? "ENGAGED" : "OFF"), 0.9);
    }

    public static void cycleOverloadBus(GameContext ctx, int dir) {
        if (ctx == null || ctx.player == null) return;
        Ship.PowerBus bus = ctx.player.cycleOverloadBus(dir);
        ctx.command.engineeringAutomation = false;
        EventSystem.showBanner(ctx, "OVERLOAD BUS: " + bus.name(), 0.9);
    }

    public static void cycleEngineeringPriority(GameContext ctx, int dir) {
        if (ctx == null || ctx.player == null) return;
        Ship.EngineeringPriority next;
        if (dir >= 0) next = ctx.player.cycleEngineeringPriority();
        else {
            Ship.EngineeringPriority[] vals = Ship.EngineeringPriority.values();
            int idx = ctx.player.engineeringPriority().ordinal() - 1;
            if (idx < 0) idx = vals.length - 1;
            next = vals[idx];
            ctx.player.setEngineeringPriority(next);
        }
        ctx.command.engineeringAutomation = false;
        EventSystem.showBanner(ctx, "ENG PRIORITY: " + next.name(), 0.9);
    }

    public static void suppressHottestFire(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        Player p = ctx.player;
        if (!p.alive || p.dying || p.hp <= 0) return;

        ShipRoomLayout.RoomId target = p.hottestFireRoom();
        if (target == null || !p.hasActiveFireHazards()) {
            EventSystem.showBanner(ctx, "NO ACTIVE FIRE HAZARDS", 1.0);
            return;
        }

        boolean suppressed = p.suppressHottestFire();
        ctx.command.engineeringAutomation = false;
        ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
        if (!suppressed) {
            EventSystem.showBanner(ctx, "SUPPRESSION BURST INEFFECTIVE", 1.0);
            return;
        }

        String label = target.name();
        ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(p.role, p.faction, target);
        if (def != null && def.label != null && !def.label.isBlank()) {
            label = def.label;
        }
        int active = p.activeFireRoomCount();
        EventSystem.showBanner(ctx, "SUPPRESSING " + label + "  (" + active + " FIRE ROOM" + (active == 1 ? "" : "S") + ")", 1.0);
    }

    public static void toggleEmergencyThrustMode(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        Player p = ctx.player;
        if (!p.alive || p.dying || p.hp <= 0) return;

        if (p.isEmergencyThrustActive()) {
            p.setEmergencyThrustMode(false);
            EventSystem.showBanner(ctx, "EMERGENCY THRUST: OFF", 0.9);
            return;
        }

        if (p.emergencyThrustCooldownRemaining() > 1e-6) {
            EventSystem.showBanner(ctx, "EMERGENCY THRUST COOLING DOWN", 1.0);
            return;
        }
        if (p.isOverloadActive() && p.overloadBus() == Ship.PowerBus.PROPULSION) {
            EventSystem.showBanner(ctx, "DISABLE PROPULSION OVERLOAD FIRST", 1.1);
            return;
        }
        if (p.propulsionRoomIntegrity() < 0.18) {
            EventSystem.showBanner(ctx, "PROPULSION TOO DAMAGED", 1.1);
            return;
        }
        if (!p.setEmergencyThrustMode(true)) {
            EventSystem.showBanner(ctx, "EMERGENCY THRUST UNAVAILABLE", 1.0);
            return;
        }
        ctx.command.helmAutomation = false;
        EventSystem.showBanner(ctx, "EMERGENCY THRUST: ENGAGED", 1.0);
    }

    private static void normalizePower(double[] p) {
        if (p == null || p.length == 0) return;
        double sum = 0.0;
        for (int i = 0; i < p.length; i++) {
            if (!Double.isFinite(p[i]) || p[i] < 0.0) p[i] = 0.0;
            sum += p[i];
        }
        if (sum <= 1e-9) {
            double each = 1.0 / p.length;
            for (int i = 0; i < p.length; i++) p[i] = each;
            return;
        }
        for (int i = 0; i < p.length; i++) p[i] /= sum;
    }

    public static void selectCrewStation(GameContext ctx, GameContext.CrewStation station) {
        if (ctx == null || station == null) return;
        ctx.command.activeCrewStation = station;
    }

    public static void cycleCrewStation(GameContext ctx, int dir) {
        if (ctx == null) return;
        GameContext.CrewStation[] values = GameContext.CrewStation.values();
        int step = (dir < 0) ? -1 : 1;
        int idx = ctx.command.activeCrewStation.ordinal() + step;
        if (idx < 0) idx = values.length - 1;
        if (idx >= values.length) idx = 0;
        ctx.command.activeCrewStation = values[idx];
    }

    public static boolean stationAutomation(GameContext ctx, GameContext.CrewStation station) {
        if (ctx == null || station == null) return false;
        return switch (station) {
            case CAPTAIN -> ctx.command.captainAutomation;
            case HELM -> ctx.command.helmAutomation;
            case TACTICAL -> ctx.command.tacticalAutomation;
            case ENGINEERING -> ctx.command.engineeringAutomation;
            case SCIENCE -> ctx.command.scienceAutomation;
        };
    }

    public static void setStationAutomation(GameContext ctx, GameContext.CrewStation station, boolean enabled) {
        if (ctx == null || station == null) return;
        switch (station) {
            case CAPTAIN -> ctx.command.captainAutomation = enabled;
            case HELM -> ctx.command.helmAutomation = enabled;
            case TACTICAL -> ctx.command.tacticalAutomation = enabled;
            case ENGINEERING -> ctx.command.engineeringAutomation = enabled;
            case SCIENCE -> ctx.command.scienceAutomation = enabled;
        }
    }

    public static void toggleActiveStationAutomation(GameContext ctx) {
        if (ctx == null) return;
        GameContext.CrewStation s = ctx.command.activeCrewStation;
        setStationAutomation(ctx, s, !stationAutomation(ctx, s));
    }

    public static void cycleVoiceMixFocus(GameContext ctx, int dir) {
        if (ctx == null) return;
        GameContext.CrewStation[] values = GameContext.CrewStation.values();
        int step = (dir < 0) ? -1 : 1;
        int idx = ctx.ui.voiceMixFocus.ordinal() + step;
        if (idx < 0) idx = values.length - 1;
        if (idx >= values.length) idx = 0;
        ctx.ui.voiceMixFocus = values[idx];
        EventSystem.showBanner(ctx, "VOICE MIX FOCUS: " + ctx.ui.voiceMixFocus.name(), 0.9);
    }

    public static void stepVoiceMixVolume(GameContext ctx, int dir) {
        if (ctx == null) return;
        double current = ctx.voiceRoleVolume(ctx.ui.voiceMixFocus);
        double next = current + ((dir < 0) ? -0.05 : 0.05);
        next = MathUtil.clamp(next, 0.0, 2.0);
        ctx.setVoiceRoleVolume(ctx.ui.voiceMixFocus, next);
        persistVoicePreferences(ctx);
        int pct = (int) Math.round(next * 100.0);
        EventSystem.showBanner(ctx, "VOICE " + ctx.ui.voiceMixFocus.name() + ": " + pct + "%", 0.8);
    }

    public static void toggleVoiceCaptions(GameContext ctx) {
        if (ctx == null) return;
        ctx.ui.voiceCaptionsEnabled = !ctx.ui.voiceCaptionsEnabled;
        if (!ctx.ui.voiceCaptionsEnabled) {
            ctx.ui.clearVoiceCaption();
        }
        persistVoicePreferences(ctx);
        EventSystem.showBanner(ctx, "VOICE CAPTIONS: " + (ctx.ui.voiceCaptionsEnabled ? "ON" : "OFF"), 1.0);
    }

    public static void cycleXrayFilterMode(GameContext ctx, int dir) {
        if (ctx == null) return;
        GameContext.XrayFilterMode[] modes = GameContext.XrayFilterMode.values();
        int step = (dir < 0) ? -1 : 1;
        int idx = ctx.ui.xrayFilterMode.ordinal() + step;
        if (idx < 0) idx = modes.length - 1;
        if (idx >= modes.length) idx = 0;
        ctx.ui.xrayFilterMode = modes[idx];
        EventSystem.showBanner(ctx, "X-RAY FILTER: " + ctx.ui.xrayFilterMode.name(), 0.9);
    }

    public static void clearXrayRoomFocus(GameContext ctx) {
        if (ctx == null) return;
        ctx.ui.xrayFocusedRoom = null;
        EventSystem.showBanner(ctx, "X-RAY FOCUS CLEARED", 0.8);
    }

    public static boolean handleXrayClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.player == null || e == null) return false;
        if (ctx.ui.hasBlockingOverlay()) return false;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return false;

        ShipRoomLayout.RoomId roomId = Renderer.playerXrayRoomAt(ctx, viewportW, viewportH, e.getX(), e.getY());
        if (roomId == null) return false;

        if (SwingUtilities.isRightMouseButton(e)) {
            ctx.ui.xrayFocusedRoom = null;
            EventSystem.showBanner(ctx, "X-RAY FOCUS CLEARED", 0.8);
            return true;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return true;

        if (ctx.ui.xrayFocusedRoom == roomId) {
            ctx.ui.xrayFocusedRoom = null;
            EventSystem.showBanner(ctx, "X-RAY FOCUS CLEARED", 0.8);
            return true;
        }
        ctx.ui.xrayFocusedRoom = roomId;
        EventSystem.showBanner(ctx, "X-RAY FOCUS: " + xrayRoomLabel(roomId), 0.9);
        return true;
    }

    public static void handleMapClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        Rectangle rect = Renderer.getStrategicMapRect(viewportW, viewportH);
        if (!rect.contains(e.getPoint())) return;

        double nx = (e.getX() - rect.x) / (double) rect.width;
        double ny = (e.getY() - rect.y) / (double) rect.height;

        ctx.ui.waypointX = GameMath.clamp(nx * ctx.WORLD_W, 0, ctx.WORLD_W);
        ctx.ui.waypointY = GameMath.clamp(ny * ctx.WORLD_H, 0, ctx.WORLD_H);

        addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.2);
    }

    public static void setWaypointAtCursor(GameContext ctx, PlayerControl controls) {
        double wx = CameraSystem.screenToWorldX(ctx, controls.getMouseX());
        double wy = CameraSystem.screenToWorldY(ctx, controls.getMouseY());
        ctx.ui.waypointX = GameMath.clamp(wx, 0, ctx.WORLD_W);
        ctx.ui.waypointY = GameMath.clamp(wy, 0, ctx.WORLD_H);
        addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
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
        ctx.ui.mapPings.add(new Renderer.MapPing(x, y, seconds, factionCode));
    }

    public static void updatePings(GameContext ctx, double dt) {
        for (int i = ctx.ui.mapPings.size() - 1; i >= 0; i--) {
            Renderer.MapPing p = ctx.ui.mapPings.get(i);
            p.t -= dt;
            if (p.t <= 0) ctx.ui.mapPings.remove(i);
        }
    }

    public static void performShopUpgradeById(GameContext ctx, int upgradeId) {
        if (ctx == null || !ctx.ui.shopOpen) return;
        switch (upgradeId) {
            case 1 -> tryEquipEnergyBolt(ctx);
            case 2 -> tryBuyBeamBolt(ctx);
            case 3 -> tryBuyHullPlating(ctx);
            case 4 -> tryBuyShieldArray(ctx);
            case 5 -> tryAddGunTurret(ctx);
            case 6 -> tryAddMissileRack(ctx);
            case 7 -> tryUpgradeCIWS(ctx);
            default -> {
            }
        }
    }

    public static void performHullSwapByRole(GameContext ctx, ShipRole role) {
        if (ctx == null || role == null || !ctx.ui.shopOpen) return;
        switch (role) {
            case PATROL -> trySwapHull(ctx, ShipRole.PATROL, 0, 0);
            case PICKET -> trySwapHull(ctx, ShipRole.PICKET, 180, 0);
            case FRIGATE -> trySwapHull(ctx, ShipRole.FRIGATE, 0, 0);
            case ARTILLERY_SHIP -> trySwapHull(ctx, ShipRole.ARTILLERY_SHIP, 320, 0);
            case MISSILE_BOAT -> trySwapHull(ctx, ShipRole.MISSILE_BOAT, 300, 0);
            case CIWS_CORVETTE -> trySwapHull(ctx, ShipRole.CIWS_CORVETTE, 250, 0);
            case LIGHT_CRUISER -> trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
            case MEDIUM_CRUISER -> trySwapHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
            case CRUISER -> trySwapHull(ctx, ShipRole.CRUISER, 1100, 1);
            case BATTLECRUISER -> trySwapHull(ctx, ShipRole.BATTLECRUISER, 1600, 2);
            case BATTLESHIP -> trySwapHull(ctx, ShipRole.BATTLESHIP, 2200, 2);
            case STEALTH_SHIP -> trySwapHull(ctx, ShipRole.STEALTH_SHIP, 1200, 2);
            case DREADNOUGHT -> trySwapHull(ctx, ShipRole.DREADNOUGHT, 3200, 3);
            case CARRIER -> trySwapHull(ctx, ShipRole.CARRIER, 2800, 3);
            case DRONE_CARRIER -> trySwapHull(ctx, ShipRole.DRONE_CARRIER, 3000, 3);
            case SUPERSHIP -> trySwapHull(ctx, ShipRole.SUPERSHIP, 5200, 3);
            default -> {
            }
        }
    }

    public static void tryBuyBeamBolt(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;

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
        if (!ctx.ui.shopOpen) return;

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
        if (!ctx.ui.shopOpen) return;
        if (!ctx.player.canBuyHullPlatingUpgrade()) {
            EventSystem.showBanner(ctx, "HULL PLATING AT CAP", 1.2);
            return;
        }
        int cost = 60;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        if (ctx.player.buyHullPlatingUpgrade()) {
            EventSystem.showBanner(ctx, "HULL UPGRADED", 1.2);
        } else {
            ctx.credits += cost;
            EventSystem.showBanner(ctx, "HULL PLATING AT CAP", 1.2);
        }
    }

    public static void tryBuyShieldArray(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        Player p = ctx.player;
        if (!p.shieldActive || p.shieldMax <= 0) {
            EventSystem.showBanner(ctx, "NO SHIELD SYSTEM", 1.4);
            return;
        }
        if (!p.canBuyShieldArrayUpgrade()) {
            EventSystem.showBanner(ctx, "SHIELD ARRAY AT CAP", 1.2);
            return;
        }
        int cost = 70;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        if (p.buyShieldArrayUpgrade()) {
            EventSystem.showBanner(ctx, "SHIELD ARRAY UPGRADED", 1.2);
        } else {
            ctx.credits += cost;
            EventSystem.showBanner(ctx, "SHIELD ARRAY AT CAP", 1.2);
        }
    }

    public static void tryAddGunTurret(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (!ctx.player.canAddGunTurretUpgrade()) {
            EventSystem.showBanner(ctx, "GUN HARDPOINTS FULL", 1.2);
            return;
        }
        int cost = 100;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        if (ctx.player.addGunTurretUpgrade()) {
            EventSystem.showBanner(ctx, "GUN TURRET ADDED", 1.2);
        } else {
            ctx.credits += cost;
            EventSystem.showBanner(ctx, "GUN HARDPOINTS FULL", 1.2);
        }
    }

    public static void tryAddMissileRack(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (!ctx.player.canAddMissileRackUpgrade()) {
            EventSystem.showBanner(ctx, "MISSILE HARDPOINTS FULL", 1.2);
            return;
        }
        int cost = 140;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        if (ctx.player.addMissileRackUpgrade()) {
            EventSystem.showBanner(ctx, "MISSILE RACK ADDED", 1.2);
        } else {
            ctx.credits += cost;
            EventSystem.showBanner(ctx, "MISSILE HARDPOINTS FULL", 1.2);
        }
    }

    public static void tryUpgradeCIWS(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
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

        int launched = CarrierSystem.tryLaunchFlight(ctx, p);
        if (launched > 0) {
            int active = CarrierSystem.countActiveWingByCarrier(ctx, p);
            EventSystem.showBanner(ctx, "SQUADRON LAUNCHED  " + launched + " CRAFT  " + active + "/" + p.maxFighters, 1.1);
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
        int recalled = 0;
        if (p.carrierCommandMode == Ship.CarrierCommandMode.DEFEND) {
            recalled = CarrierSystem.recallDefensiveStrikeCraft(ctx, p);
        }
        String banner = "WING MODE: " + p.carrierCommandMode.name();
        if (recalled > 0) banner += "  RTB " + recalled;
        EventSystem.showBanner(ctx, banner, 1.2);
    }

    public static void tryCarrierToggleAutoLaunch(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        Player p = ctx.player;
        p.carrierAutoLaunch = !p.carrierAutoLaunch;
        EventSystem.showBanner(ctx, "AUTO-LAUNCH: " + (p.carrierAutoLaunch ? "ON" : "OFF"), 1.2);
    }

    public static void selectFlightDeckSlot(GameContext ctx, int idx) {
        if (ctx == null) return;
        ctx.ui.flightDeckFocus = Math.max(0, Math.min(4, idx));
    }

    public static void cycleFlightDeckSlot(GameContext ctx, int dir) {
        if (!ensurePlayerCarrier(ctx)) return;
        int step = (dir < 0) ? -1 : 1;
        int next = ctx.ui.flightDeckFocus + step;
        if (next < 0) next = 4;
        if (next > 4) next = 0;
        ctx.ui.flightDeckFocus = next;
    }

    public static void cycleFocusedFlightDeckRole(GameContext ctx, int dir) {
        if (!ensurePlayerCarrier(ctx)) return;
        ctx.player.cycleFlightDeckRole(ctx.ui.flightDeckFocus, dir);
        EventSystem.showBanner(ctx, "SQUAD SLOT " + (ctx.ui.flightDeckFocus + 1) + ": "
                + ctx.player.flightDeckRoleAt(ctx.ui.flightDeckFocus).name(), 0.9);
    }

    public static void setFocusedFlightDeckRole(GameContext ctx, ShipRole role) {
        if (!ensurePlayerCarrier(ctx) || role == null) return;
        ctx.player.setFlightDeckRole(ctx.ui.flightDeckFocus, role);
        EventSystem.showBanner(ctx, "SQUAD SLOT " + (ctx.ui.flightDeckFocus + 1) + ": "
                + ctx.player.flightDeckRoleAt(ctx.ui.flightDeckFocus).name(), 0.9);
    }

    public static void fillFlightDeck(GameContext ctx, ShipRole role) {
        if (!ensurePlayerCarrier(ctx) || role == null) return;
        for (int i = 0; i < ctx.player.flightDeckLoadout.length; i++) {
            ctx.player.setFlightDeckRole(i, role);
        }
        EventSystem.showBanner(ctx, "SQUAD LOADOUT: " + role.name() + " x10", 1.0);
    }

    public static void resetFlightDeckLoadout(GameContext ctx) {
        if (!ensurePlayerCarrier(ctx)) return;
        ctx.player.resetFlightDeckLoadout();
        EventSystem.showBanner(ctx, "SQUAD LOADOUT RESET", 1.0);
    }

    public static void trySwapHull(GameContext ctx, ShipRole role, int cost, int requiredTier) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
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
        if (!ctx.ui.baseMenuOpen) return;
        if (which < 1 || which > 5) return;
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
            default -> {
                return;
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
        ctx.command.helmMode = mode;
        ctx.command.helmAutomation = true;
    }

    public static void setTacticalMode(GameContext ctx, GameContext.TacticalMode mode) {
        if (ctx == null || mode == null) return;
        ctx.command.tacticalMode = mode;
        ctx.command.tacticalAutomation = true;
    }

    public static void setEngineeringMode(GameContext ctx, GameContext.EngineeringMode mode) {
        if (ctx == null || mode == null) return;
        ctx.command.engineeringMode = mode;
        ctx.command.engineeringAutomation = true;
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
        ctx.command.captainDirective = directive;
        switch (directive) {
            case ATTACK -> {
                ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                ctx.command.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.ATTACK;
                ctx.player.setPowerPreset(Ship.PowerPreset.ATTACK);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
            }
            case DEFENSE -> {
                ctx.command.helmMode = GameContext.HelmMode.MAINTAIN_RANGE;
                ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
            }
            case EMERGENCY -> {
                ctx.command.helmMode = GameContext.HelmMode.EVASIVE;
                ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.player.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            }
            case MINE -> {
                ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.player.setPowerPreset(Ship.PowerPreset.PURSUIT);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.MINE;
            }
            case ESCORT -> {
                ctx.command.helmMode = GameContext.HelmMode.ORBIT;
                ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.player.setPowerPreset(Ship.PowerPreset.BALANCED);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.ESCORT;
            }
            case DEFEND -> {
                ctx.command.helmMode = GameContext.HelmMode.ORBIT;
                ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
            }
            case REPAIR -> {
                ctx.command.helmMode = GameContext.HelmMode.EVASIVE;
                ctx.command.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.player.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.REPAIR;
            }
            case RTB -> {
                ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                ctx.command.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.player.setPowerPreset(Ship.PowerPreset.DEFENSE);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.RTB;
            }
            default -> {
                ctx.command.helmMode = GameContext.HelmMode.MAINTAIN_RANGE;
                ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                ctx.command.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.player.setPowerPreset(Ship.PowerPreset.BALANCED);
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.AUTO;
            }
        }
        ctx.command.captainAutomation = true;
        ctx.command.helmAutomation = true;
        ctx.command.tacticalAutomation = true;
        ctx.command.engineeringAutomation = true;
        ctx.command.scienceAutomation = true;
    }

    public static void cycleAlliedFleetFormation(GameContext ctx) {
        if (ctx == null) return;
        GameContext.FleetFormation[] values = GameContext.FleetFormation.values();
        int next = ctx.command.alliedFleetFormation.ordinal() + 1;
        if (next >= values.length) next = 0;
        ctx.command.alliedFleetFormation = values[next];
        AudioSystem.onCommandShipFormationOrder(ctx, ctx.player, ctx.command.alliedFleetFormation);
        EventSystem.showBanner(ctx, "FLEET FORMATION: " + ctx.command.alliedFleetFormation.name(), 1.0);
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
            ctx.command.shipFleetCommandOverrides.remove(target.id);
            AudioSystem.onCommandShipShipOrder(ctx, ctx.player, GameContext.FleetCommand.AUTO, target);
            EventSystem.showBanner(ctx, "SHIP " + target.id + " ORDER CLEARED", 1.1);
            return;
        }
        ctx.command.shipFleetCommandOverrides.put(target.id, command);
        AudioSystem.onCommandShipShipOrder(ctx, ctx.player, command, target);
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
        ctx.command.scienceJamming = !ctx.command.scienceJamming;
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

    private static String xrayRoomLabel(ShipRoomLayout.RoomId roomId) {
        return ShipRoomLayout.displayLabel(roomId);
    }

    private static void clearManualCombatInputs(GameContext ctx) {
        if (ctx == null) return;
        ctx.firingPrimaryManual = false;
        ctx.firingSecondaryManual = false;
    }

    private static void persistVoicePreferences(GameContext ctx) {
        if (ctx == null) return;
        MenuSettingsStore.MenuSettings settings = MenuSettingsStore.load();
        settings.voiceCaptionsEnabled = ctx.ui.voiceCaptionsEnabled;
        settings.voiceVolumeCaptain = ctx.voiceRoleVolume(GameContext.CrewStation.CAPTAIN);
        settings.voiceVolumeHelm = ctx.voiceRoleVolume(GameContext.CrewStation.HELM);
        settings.voiceVolumeTactical = ctx.voiceRoleVolume(GameContext.CrewStation.TACTICAL);
        settings.voiceVolumeEngineering = ctx.voiceRoleVolume(GameContext.CrewStation.ENGINEERING);
        settings.voiceVolumeScience = ctx.voiceRoleVolume(GameContext.CrewStation.SCIENCE);
        MenuSettingsStore.save(settings);
    }
}
