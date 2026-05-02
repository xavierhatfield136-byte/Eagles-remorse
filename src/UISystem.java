import app.config.GameMode;
import app.config.PlayerTeamChoice;
import app.persistence.MenuSettingsStore;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.SwingUtilities;

public final class UISystem {
    private UISystem(){}

    private static boolean fleetHubEditingLocked(GameContext ctx) {
        return CampaignSystem.isCampaignActive(ctx) && !CampaignSystem.isFleetHubSession(ctx);
    }

    private static GameState stateAfterOverlayClose(GameContext ctx) {
        return CampaignSystem.isFleetHubSession(ctx) ? GameState.FLEET : GameState.RUNNING;
    }

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
        if (!ctx.gameOver) ctx.state = stateAfterOverlayClose(ctx);
        if (hadOverlay) AudioSystem.onUiClose(ctx);
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        // If awaiting fleet hub choice after sector complete, TAB opens it immediately
        if (CampaignSystem.tryEnterFleetHubImmediately(ctx)) {
            return;
        }
        if (fleetHubEditingLocked(ctx)) {
            EventSystem.showBanner(ctx, "FLEET HANGAR OPENS BETWEEN SECTORS", 1.8);
            return;
        }
        ctx.ui.shopOpen = !ctx.ui.shopOpen;
        if (ctx.ui.shopOpen) {
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            clearManualCombatInputs(ctx);
            focusShopHullRole(ctx, (ctx.player == null) ? ShipRole.FRIGATE : ctx.player.role);
            ctx.state = GameState.SHOP;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = stateAfterOverlayClose(ctx);
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
            BattlefieldSectorSystem.ensureSelection(ctx);
            BattlefieldSectorSystem.ensureLoadedSector(ctx);
            ctx.state = GameState.MAP;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.ui.strategicMapFocusX = Double.NaN;
            ctx.ui.strategicMapFocusY = Double.NaN;
            ctx.state = stateAfterOverlayClose(ctx);
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void toggleTacticalView(GameContext ctx) {
        if (ctx == null) return;
        ctx.ui.tacticalViewEnabled = !ctx.ui.tacticalViewEnabled;
        EventSystem.showBanner(ctx, "TACTICAL FPS VIEW: " + (ctx.ui.tacticalViewEnabled ? "ON" : "OFF"), 1.0);
    }

    public static void toggleBaseMenu(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        if (fleetHubEditingLocked(ctx)) {
            EventSystem.showBanner(ctx, "FLEET UPGRADES OPEN BETWEEN SECTORS", 1.8);
            return;
        }
        Ship dock = CampaignSystem.currentBaseUpgradeAnchor(ctx);
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
            ctx.state = stateAfterOverlayClose(ctx);
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
            ctx.state = stateAfterOverlayClose(ctx);
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
            ctx.state = stateAfterOverlayClose(ctx);
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
            ctx.state = stateAfterOverlayClose(ctx);
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
            case 5 -> GameplayActions.trySafeMissionExit(ctx);
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

        boolean campaignShop = CampaignSystem.usesPersistentFleetShop(ctx);
        boolean fleetHub = CampaignSystem.isFleetHubSession(ctx);
        if (campaignShop && fleetHub) {
            Renderer.FleetOverlayClickTarget fleetTarget = Renderer.fleetOverlayClickTargetAt(
                    ctx, ctx.ui, viewportW, viewportH, e.getX(), e.getY());
            if (fleetTarget != null) {
                switch (fleetTarget.kind) {
                    case MODE_COMMISSION -> {
                        ctx.ui.fleetRefitMode = false;
                        return true;
                    }
                    case MODE_REFIT -> {
                        ctx.ui.fleetRefitMode = true;
                        return true;
                    }
                    case SELECT_SHIP -> {
                        if (fleetTarget.shipId > 0) selectFleetShip(ctx, fleetTarget.shipId);
                        return true;
                    }
                    case SELECT_TURRET -> {
                        if (fleetTarget.shipId > 0) selectFleetShip(ctx, fleetTarget.shipId);
                        if (fleetTarget.turretIndex >= 0) selectFleetTurret(ctx, fleetTarget.turretIndex);
                        return true;
                    }
                    case SWAP_TO_GUN -> {
                        swapFleetTurretKind(ctx, fleetTarget.shipId, fleetTarget.turretIndex, Turret.Kind.GUN);
                        return true;
                    }
                    case SWAP_TO_MISSILE -> {
                        swapFleetTurretKind(ctx, fleetTarget.shipId, fleetTarget.turretIndex, Turret.Kind.MISSILE);
                        return true;
                    }
                    case SET_MISSILE_ROLE -> {
                        if (fleetTarget.shipId > 0) selectFleetShip(ctx, fleetTarget.shipId);
                        if (fleetTarget.turretIndex >= 0) selectFleetTurret(ctx, fleetTarget.turretIndex);
                        if (fleetTarget.missileRole != null) setMissileRoleForSelectedTurret(ctx, fleetTarget.missileRole);
                        return true;
                    }
                }
            }
        }

        Renderer.ShopClickTarget target = Renderer.shopClickTargetAt(
                ctx.player, ctx.ui, ctx.credits, getMaxHangarTierForPlayer(ctx),
                viewportW, viewportH, e.getX(), e.getY());
        if (target == null) return false;

        if (target.kind == Renderer.ShopClickTarget.Kind.UPGRADE) {
            performShopUpgradeById(ctx, target.upgradeId);
            return true;
        }
        if (target.kind == Renderer.ShopClickTarget.Kind.CATEGORY && target.category != null) {
            selectShopHullCategory(ctx, target.category);
            return true;
        }
        if (target.kind == Renderer.ShopClickTarget.Kind.PAGE && target.pageDelta != 0) {
            stepShopHullPage(ctx, target.pageDelta);
            return true;
        }
        if (target.kind == Renderer.ShopClickTarget.Kind.HULL && target.role != null) {
            performHullSwapByRole(ctx, target.role);
            return true;
        }
        return false;
    }

    public static void selectFleetShip(GameContext ctx, int shipId) {
        if (ctx == null || !CampaignSystem.isFleetHubSession(ctx)) return;
        ctx.ui.fleetSelectedShipId = shipId;
        ctx.ui.fleetSelectedTurretIndex = -1;  // Reset turret selection when changing ships
    }

    public static void selectFleetTurret(GameContext ctx, int turretIndex) {
        if (ctx == null || !CampaignSystem.isFleetHubSession(ctx)) return;
        if (ctx.ui.fleetSelectedShipId < 0) return;  // Must have a ship selected first
        Ship selected = findShipInFleet(ctx, ctx.ui.fleetSelectedShipId);
        if (selected == null || turretIndex < 0 || turretIndex >= selected.turrets.size()) {
            ctx.ui.fleetSelectedTurretIndex = -1;
            return;
        }
        ctx.ui.fleetSelectedTurretIndex = turretIndex;
    }

    public static void setMissileRoleForSelectedTurret(GameContext ctx, Turret.MissileRole role) {
        if (ctx == null || !CampaignSystem.isFleetHubSession(ctx)) return;
        if (ctx.ui.fleetSelectedShipId < 0 || ctx.ui.fleetSelectedTurretIndex < 0) return;
        Ship selected = findShipInFleet(ctx, ctx.ui.fleetSelectedShipId);
        if (selected == null || selected.turrets.size() <= ctx.ui.fleetSelectedTurretIndex) return;
        Turret turret = selected.turrets.get(ctx.ui.fleetSelectedTurretIndex);
        if (turret != null && turret.kind == Turret.Kind.MISSILE) {
            turret.missileRole = role;
        }
    }

    private static void swapFleetTurretKind(GameContext ctx, int shipId, int turretIndex, Turret.Kind desired) {
        if (ctx == null || desired == null || !CampaignSystem.isFleetHubSession(ctx)) return;
        if (shipId <= 0 || turretIndex < 0) return;
        Ship ship = findShipInFleet(ctx, shipId);
        if (ship == null || ship.turrets == null || turretIndex >= ship.turrets.size()) return;
        Turret old = ship.turrets.get(turretIndex);
        if (old == null || old.kind == desired) return;

        Turret reference = null;
        for (Turret t : ship.turrets) {
            if (t == null) continue;
            if (t.kind == desired) {
                reference = t;
                break;
            }
        }

        Turret nt = new Turret(desired, old.localX, old.localY);
        nt.angle = old.angle;
        nt.primary = old.primary; // preserve fire group intent

        if (reference != null) {
            nt.turnRate = reference.turnRate;
            nt.cooldown = reference.cooldown;
            nt.damage = reference.damage;
            nt.bulletSpeed = reference.bulletSpeed;
            nt.bulletLife = reference.bulletLife;
            nt.missileSpeed = reference.missileSpeed;
            nt.missileTurnRate = reference.missileTurnRate;
            nt.missileLife = reference.missileLife;
            nt.radius = reference.radius;
            nt.barrelLen = reference.barrelLen;
            nt.missileRole = reference.missileRole;
            nt.enablesDamageGrowth = reference.enablesDamageGrowth;
        } else {
            // Keep old tuning where possible, but nudge toward sane baselines for the new weapon kind.
            nt.turnRate = old.turnRate;
            nt.cooldown = old.cooldown;
            nt.damage = old.damage;
            nt.bulletSpeed = old.bulletSpeed;
            nt.bulletLife = old.bulletLife;
            nt.missileSpeed = old.missileSpeed;
            nt.missileTurnRate = old.missileTurnRate;
            nt.missileLife = old.missileLife;
            nt.radius = old.radius;
            nt.barrelLen = old.barrelLen;
            nt.missileRole = (old.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : old.missileRole;

            if (desired == Turret.Kind.MISSILE) {
                nt.cooldown = Math.max(nt.cooldown, Ship.MISSILE_MIN_RELOAD_SECONDS);
                nt.damage = Math.max(2, nt.damage);
                nt.radius = Math.max(nt.radius, 7.0);
                nt.barrelLen = Math.max(nt.barrelLen, 10.0);
            } else {
                nt.cooldown = Math.min(nt.cooldown, 0.30);
                nt.damage = Math.max(1, nt.damage);
                nt.bulletSpeed = Math.max(nt.bulletSpeed, 780.0);
                nt.bulletLife = Math.max(nt.bulletLife, 120);
                nt.radius = Math.max(nt.radius, 6.0);
                nt.barrelLen = Math.max(nt.barrelLen, 14.0);
            }
        }

        ship.turrets.set(turretIndex, nt);
    }

    private static Ship findShipInFleet(GameContext ctx, int shipId) {
        if (ctx == null || ctx.ships == null) return null;
        for (Ship s : ctx.ships) {
            if (s.id == shipId && CampaignSystem.isFleetSelectionCandidate(s)) {
                return s;
            }
        }
        return null;
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
        if (ctx.player != null) {
            ctx.player.clearIntegrityFocus();
        }
        EventSystem.showBanner(ctx, "X-RAY FOCUS CLEARED", 0.8);
    }

    private static Ship.EngineeringPriority engineeringPriorityForRoom(ShipRoomLayout.RoomDef room) {
        if (room == null || room.primarySystem == null) return Ship.EngineeringPriority.BALANCED;
        return switch (room.primarySystem) {
            case ENGINES, WARP_ENGINES -> Ship.EngineeringPriority.PROPULSION;
            case SHIELDS -> Ship.EngineeringPriority.SHIELDS;
            case WEAPONS, MAGAZINES -> Ship.EngineeringPriority.WEAPONS;
            case SENSORS, BRIDGE -> Ship.EngineeringPriority.SENSORS;
            case REACTOR_CORE -> Ship.EngineeringPriority.REACTOR;
        };
    }

    public static boolean handleXrayClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.player == null || e == null) return false;
        if (ctx.ui.hasBlockingOverlay()) return false;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return false;

        ShipRoomLayout.RoomId roomId = Renderer.playerXrayRoomAt(ctx, viewportW, viewportH, e.getX(), e.getY());
        if (roomId == null) return false;

        if (SwingUtilities.isRightMouseButton(e)) {
            clearXrayRoomFocus(ctx);
            return true;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return true;

        ctx.ui.xrayFocusedRoom = roomId;
        ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ctx.player.role, ctx.player.faction, roomId);
        Ship.EngineeringPriority focus = engineeringPriorityForRoom(room);
        ctx.player.setIntegrityFocus(roomId, 8.0);
        ctx.command.engineeringAutomation = false;
        if (focus != null) {
            ctx.player.setEngineeringPriority(focus);
        }

        boolean damaged = ctx.player.roomHealthFraction(roomId) < 0.999
                || ctx.player.isRoomDisrupted(roomId)
                || ctx.player.roomFireIntensity(roomId) > 0.01;
        if (damaged) {
            ctx.player.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
            ctx.player.suppressFireInRoom(roomId);
        }

        StringBuilder banner = new StringBuilder("X-RAY FOCUS: ").append(xrayRoomLabel(roomId));
        banner.append("   FIELD ").append(focus.name());
        if (damaged) banner.append("   DAMAGE CONTROL");
        EventSystem.showBanner(ctx, banner.toString(), 0.9);
        return true;
    }

    public static boolean handleHudPanelClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.player == null || e == null) return false;
        if (ctx.ui.hasBlockingOverlay()) return false;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;

        Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewportW, viewportH, e.getX(), e.getY());
        if (target == null) return false;

        switch (target.kind) {
            case BEAM_RAPID -> setPlayerBeamMode(ctx, Ship.PrimaryWeaponFamily.ENERGY_BOLT);
            case BEAM_CONCENTRATED -> setPlayerBeamMode(ctx, Ship.PrimaryWeaponFamily.BEAM_BOLT);
            case MISSILE_HEAVY -> setPlayerMissileRole(ctx, Turret.MissileRole.ANTI_HEAVY, "MISSILE MODE: HEAVY");
            case MISSILE_FAST -> setPlayerMissileRole(ctx, Turret.MissileRole.ANTI_LIGHT, "MISSILE MODE: FAST");
            case MISSILE_AAA -> setPlayerMissileRole(ctx, Turret.MissileRole.INTERCEPT, "MISSILE MODE: AAA");
            case ECM_PRIMED -> setScienceJamming(ctx, false);
            case ECM_ACTIVE -> setScienceJamming(ctx, true);
            case CLOAK_CHARGE -> setPlayerCloakMode(ctx, Ship.CloakControlMode.CHARGE);
            case CLOAK_ACTIVE -> setPlayerCloakMode(ctx, Ship.CloakControlMode.ACTIVE);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean handleFleetNetClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.player == null || e == null) return false;
        if (ctx.ui.hasBlockingOverlay()) return false;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return false;
        if (ctx.ui.mapOpen) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;

        Rectangle panel = fleetNetPanelRect(viewportW, viewportH);
        if (!panel.contains(e.getPoint())) return false;

        java.util.List<GameRenderSystem.SensorNetEntry> entries = GameRenderSystem.sensorNetEntries(ctx, 4, 2);
        if (entries.isEmpty()) return false;

        FontMetricsLike fm = new FontMetricsLike(12);
        java.util.List<String> sensorLines = FogOfWarSystem.isCombatFogEnabled(ctx)
                ? wrapUiLines(FogOfWarSystem.coverageSummary(ctx), panel.width - 24, fm.charWidth)
                : java.util.List.of();
        int rowY = panel.y + 22 + 16 + sensorLines.size() * 15;
        if (!sensorLines.isEmpty()) rowY += 14;
        rowY += 14; // TRACKS header row

        String currentSection = "";
        for (GameRenderSystem.SensorNetEntry entry : entries) {
            if (entry == null) continue;
            if (!entry.section.equals(currentSection)) {
                currentSection = entry.section;
                rowY += 14;
            }
            Rectangle rowRect = new Rectangle(panel.x + 10, rowY - 11, panel.width - 20, 16);
            if (rowRect.contains(e.getPoint())) {
                ctx.ui.waypointX = GameMath.clamp(entry.x, 0, ctx.WORLD_W);
                ctx.ui.waypointY = GameMath.clamp(entry.y, 0, ctx.WORLD_H);
                openStrategicMapFocusedAt(ctx, entry.x, entry.y);
                addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
                EventSystem.showBanner(ctx, entry.banner, 1.3);
                return true;
            }
            rowY += 18;
        }
        return false;
    }

    public static void handleMapClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        Rectangle rect = Renderer.getStrategicMapInnerRect(viewportW, viewportH);
        if (!rect.contains(e.getPoint())) return;

        double nx = (e.getX() - rect.x) / (double) rect.width;
        double ny = (e.getY() - rect.y) / (double) rect.height;
        double worldX = GameMath.clamp(nx * ctx.WORLD_W, 0, ctx.WORLD_W);
        double worldY = GameMath.clamp(ny * ctx.WORLD_H, 0, ctx.WORLD_H);
        CampaignSystem.CampaignObjectiveMarker clickedMarker =
                CampaignSystem.nearestObjectiveMarker(ctx, worldX, worldY, 280.0);
        if (clickedMarker != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedMarker.x, clickedMarker.y, 2.2);
                EventSystem.showBanner(ctx, "OBJECTIVE PING: " + clickedMarker.label.toUpperCase(), 1.2);
                return;
            }
            ctx.ui.waypointX = GameMath.clamp(clickedMarker.x, 0, ctx.WORLD_W);
            ctx.ui.waypointY = GameMath.clamp(clickedMarker.y, 0, ctx.WORLD_H);
            addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
            String subtitle = (clickedMarker.subtitle == null || clickedMarker.subtitle.isBlank())
                    ? ""
                    : "  " + clickedMarker.subtitle.toUpperCase();
            EventSystem.showBanner(ctx,
                    "OBJECTIVE SET: " + clickedMarker.label.toUpperCase() + subtitle,
                    1.4);
            return;
        }

        CampaignSystem.CampaignSupportMarker clickedSupport =
                CampaignSystem.nearestSupportMarker(ctx, worldX, worldY, 240.0);
        if (clickedSupport != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedSupport.x, clickedSupport.y, 2.2);
                EventSystem.showBanner(ctx, "SUPPORT PING: " + clickedSupport.label.toUpperCase(), 1.2);
                return;
            }
            ctx.ui.waypointX = GameMath.clamp(clickedSupport.x, 0, ctx.WORLD_W);
            ctx.ui.waypointY = GameMath.clamp(clickedSupport.y, 0, ctx.WORLD_H);
            addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
            String subtitle = (clickedSupport.subtitle == null || clickedSupport.subtitle.isBlank())
                    ? ""
                    : "  " + clickedSupport.subtitle.toUpperCase();
            EventSystem.showBanner(ctx,
                    "SUPPORT TRACK SET: " + clickedSupport.label.toUpperCase() + subtitle,
                    1.4);
            return;
        }

        CampaignSystem.CampaignLandmark clickedLandmark =
                CampaignSystem.nearestStrategicLandmark(ctx, worldX, worldY, 220.0);
        if (clickedLandmark != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedLandmark.x, clickedLandmark.y, 2.2);
                EventSystem.showBanner(ctx, "LANDMARK PING: " + clickedLandmark.label.toUpperCase(), 1.2);
                return;
            }
            ctx.ui.waypointX = GameMath.clamp(clickedLandmark.x, 0, ctx.WORLD_W);
            ctx.ui.waypointY = GameMath.clamp(clickedLandmark.y, 0, ctx.WORLD_H);
            addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
            String subtitle = (clickedLandmark.subtitle == null || clickedLandmark.subtitle.isBlank())
                    ? ""
                    : "  " + clickedLandmark.subtitle.toUpperCase();
            EventSystem.showBanner(ctx,
                    "LANDMARK SET: " + clickedLandmark.label.toUpperCase() + subtitle,
                    1.4);
            return;
        }

        if (BattlefieldSectorSystem.isEnabled(ctx)) {
            BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.sectorAtNormalized(ctx, nx, ny);
            if (sector != null) {
                BattlefieldSectorSystem.selectSector(ctx, sector.id);
                BattlefieldSectorSystem.ensureLoadedSector(ctx);
                BattlefieldSectorSystem.SectorDefinition loaded = BattlefieldSectorSystem.loadedSector(ctx);
                double clickedWorldX = worldX;
                double clickedWorldY = worldY;
                BattlefieldSectorSystem.SectorDefinition hop =
                        BattlefieldSectorSystem.nextWarpHop(ctx, loaded, sector);
                BattlefieldSectorSystem.SectorDefinition waypointSector = (hop == null) ? sector : hop;
                boolean sameSectorSelection = loaded != null && loaded.id.equalsIgnoreCase(sector.id);
                double[] arrival = sameSectorSelection ? null : BattlefieldSectorSystem.warpArrivalPoint(
                        ctx, loaded, waypointSector, ctx.ui.tacticalSectorScalePreset);
                double targetX = sameSectorSelection
                        ? clickedWorldX
                        : (arrival == null ? sector.centerX(ctx) : arrival[0]);
                double targetY = sameSectorSelection
                        ? clickedWorldY
                        : (arrival == null ? sector.centerY(ctx) : arrival[1]);
                if (SwingUtilities.isRightMouseButton(e)) {
                    addPing(ctx, targetX, targetY, 2.2);
                    EventSystem.showBanner(ctx, "SECTOR PING: " + sector.label, 1.2);
                    return;
                }
                ctx.ui.waypointX = GameMath.clamp(targetX, 0, ctx.WORLD_W);
                ctx.ui.waypointY = GameMath.clamp(targetY, 0, ctx.WORLD_H);
                addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
                BattlefieldSectorSystem.SectorSnapshot snapshot =
                        BattlefieldSectorSystem.snapshotForSector(ctx, sector.id);
                String status = BattlefieldSectorSystem.relativeStatusLabel(ctx, snapshot);
                String route = (waypointSector != null && sector != null
                        && !waypointSector.id.equalsIgnoreCase(sector.id))
                        ? "  VIA " + waypointSector.label
                        : "";
                EventSystem.showBanner(ctx,
                        "COURSE SET: " + sector.label + route
                                + "  " + ctx.ui.tacticalSectorScalePreset.label().toUpperCase()
                                + (status.isBlank() ? "" : "  " + status.toUpperCase()),
                        1.2);
                return;
            }
        }

        if (CampaignSystem.usesMissionSubzones(ctx)) {
            int targetSubzone = CampaignSystem.campaignMapSubzoneAtPoint(ctx, worldX, worldY);
            if (targetSubzone >= 0) {
                int loadedSubzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
                if (loadedSubzone < 0) loadedSubzone = CampaignSystem.syncLoadedMissionSubzoneFromPlayer(ctx);
                int hopSubzone = CampaignSystem.nextCampaignWarpHop(loadedSubzone, targetSubzone);
                boolean sameSubzoneSelection = hopSubzone == targetSubzone && targetSubzone == loadedSubzone;
                double[] arrival = sameSubzoneSelection ? null : CampaignSystem.campaignWarpArrivalPoint(ctx, hopSubzone);
                double targetX = sameSubzoneSelection ? worldX : ((arrival == null) ? worldX : arrival[0]);
                double targetY = sameSubzoneSelection ? worldY : ((arrival == null) ? worldY : arrival[1]);
                String sectorLabel = CampaignSystem.missionSubzoneLabel(targetSubzone);
                String route = (hopSubzone >= 0 && hopSubzone != targetSubzone)
                        ? "  VIA " + CampaignSystem.missionSubzoneLabel(hopSubzone)
                        : "";
                if (SwingUtilities.isRightMouseButton(e)) {
                    addPing(ctx, targetX, targetY, 2.2);
                    EventSystem.showBanner(ctx, "SECTOR PING: " + sectorLabel, 1.2);
                    return;
                }
                ctx.ui.waypointX = GameMath.clamp(targetX, 0, ctx.WORLD_W);
                ctx.ui.waypointY = GameMath.clamp(targetY, 0, ctx.WORLD_H);
                addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
                EventSystem.showBanner(ctx, "COURSE SET: " + sectorLabel + route, 1.2);
                return;
            }
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            addPing(ctx, worldX, worldY, 2.2);
            EventSystem.showBanner(ctx, "PING MARKED", 1.0);
            return;
        }

        ctx.ui.waypointX = worldX;
        ctx.ui.waypointY = worldY;
        addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.2);
    }

    private static Rectangle fleetNetPanelRect(int viewportW, int viewportH) {
        int w = Math.min(300, Math.max(240, viewportW / 4));
        return new Rectangle(viewportW - w - 16, 16, w, Math.max(120, viewportH / 5));
    }

    private static void openStrategicMapFocusedAt(GameContext ctx, double x, double y) {
        if (ctx == null || ctx.ui == null) return;
        if (!ctx.ui.mapOpen) {
            ctx.ui.mapOpen = true;
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            clearManualCombatInputs(ctx);
            BattlefieldSectorSystem.ensureSelection(ctx);
            BattlefieldSectorSystem.ensureLoadedSector(ctx);
            ctx.state = GameState.MAP;
            AudioSystem.onUiOpen(ctx);
        }
        ctx.ui.strategicMapFocusX = GameMath.clamp(x, 0, ctx.WORLD_W);
        ctx.ui.strategicMapFocusY = GameMath.clamp(y, 0, ctx.WORLD_H);
    }

    private static java.util.List<String> wrapUiLines(String text, int maxWidth, int charWidth) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0) return out;
        int width = Math.max(6, maxWidth / Math.max(1, charWidth));
        String[] words = text.trim().split("\\s+");
        String line = "";
        for (String word : words) {
            if (word == null || word.isBlank()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && candidate.length() > width) {
                out.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private static final class FontMetricsLike {
        final int charWidth;

        FontMetricsLike(int charWidth) {
            this.charWidth = Math.max(6, charWidth);
        }
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

    public static void setTacticalSectorScale(GameContext ctx, UiState.TacticalSectorScalePreset preset) {
        if (ctx == null || ctx.ui == null || preset == null) return;
        ctx.ui.tacticalSectorScalePreset = preset;
        EventSystem.showBanner(ctx, "TACTICAL SCALE: " + preset.label().toUpperCase(), 1.0);
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
        if (fleetHubEditingLocked(ctx)) return;
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

    public static void selectShopHullCategory(GameContext ctx, ShopHullCategory category) {
        if (ctx == null || category == null) return;
        ctx.ui.shopHullCategory = category;
        ctx.ui.shopHullPage = Renderer.clampShopHullPage(category, 0);
    }

    public static void stepShopHullPage(GameContext ctx, int dir) {
        if (ctx == null) return;
        ShopHullCategory category = (ctx.ui.shopHullCategory == null) ? ShopHullCategory.ESCORT : ctx.ui.shopHullCategory;
        int step = (dir < 0) ? -1 : 1;
        int pages = Renderer.shopHullPageCount(category);
        if (pages <= 1) {
            ctx.ui.shopHullPage = 0;
            return;
        }
        int next = ctx.ui.shopHullPage + step;
        if (next < 0) next = pages - 1;
        if (next >= pages) next = 0;
        ctx.ui.shopHullPage = Renderer.clampShopHullPage(category, next);
    }

    public static void focusShopHullRole(GameContext ctx, ShipRole role) {
        if (ctx == null) return;
        ShopHullCategory category = ShopHullCategory.forRole(role);
        ctx.ui.shopHullCategory = category;
        ctx.ui.shopHullPage = Renderer.shopHullPageForRole(role);
    }

    public static void performHullSwapByRole(GameContext ctx, ShipRole role) {
        if (ctx == null || role == null || !ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;
        focusShopHullRole(ctx, role);
        if (CampaignSystem.usesPersistentFleetShop(ctx)) {
            switch (role) {
                case PATROL -> tryBuyCampaignHull(ctx, ShipRole.PATROL, 0, 0);
                case PICKET -> tryBuyCampaignHull(ctx, ShipRole.PICKET, 180, 0);
                case FRIGATE -> tryBuyCampaignHull(ctx, ShipRole.FRIGATE, 0, 0);
                case MINER -> tryBuyCampaignHull(ctx, ShipRole.MINER, 160, 0);
                case ARTILLERY_SHIP -> tryBuyCampaignHull(ctx, ShipRole.ARTILLERY_SHIP, 320, 0);
                case MISSILE_BOAT -> tryBuyCampaignHull(ctx, ShipRole.MISSILE_BOAT, 300, 0);
                case CIWS_CORVETTE -> tryBuyCampaignHull(ctx, ShipRole.CIWS_CORVETTE, 250, 0);
                case LIGHT_CRUISER -> tryBuyCampaignHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
                case MEDIUM_CRUISER -> tryBuyCampaignHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
                case CRUISER -> tryBuyCampaignHull(ctx, ShipRole.CRUISER, 1100, 1);
                case HAULER -> tryBuyCampaignHull(ctx, ShipRole.HAULER, 260, 1);
                case BATTLECRUISER -> tryBuyCampaignHull(ctx, ShipRole.BATTLECRUISER, 1600, 2);
                case BATTLESHIP -> tryBuyCampaignHull(ctx, ShipRole.BATTLESHIP, 2200, 2);
                case STEALTH_SHIP -> tryBuyCampaignHull(ctx, ShipRole.STEALTH_SHIP, 1200, 2);
                case DREADNOUGHT -> tryBuyCampaignHull(ctx, ShipRole.DREADNOUGHT, 3200, 3);
                case CARRIER -> tryBuyCampaignHull(ctx, ShipRole.CARRIER, 2800, 3);
                case DRONE_CARRIER -> tryBuyCampaignHull(ctx, ShipRole.DRONE_CARRIER, 3000, 3);
                case SUPERSHIP -> tryBuyCampaignHull(ctx, ShipRole.SUPERSHIP, 5200, 3);
                case TRANSPORT_TITAN -> tryBuyCampaignHull(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3);
                case BULWARK_TITAN -> tryBuyCampaignHull(ctx, ShipRole.BULWARK_TITAN, TitanArchetype.BULWARK.costCredits(), 3);
                case CARRIER_SUPPORT_TITAN -> tryBuyCampaignHull(ctx, ShipRole.CARRIER_SUPPORT_TITAN, TitanArchetype.CARRIER_SUPPORT.costCredits(), 3);
                case VANGUARD_TITAN -> tryBuyCampaignHull(ctx, ShipRole.VANGUARD_TITAN, TitanArchetype.VANGUARD.costCredits(), 3);
                case INTERDICTION_TITAN -> tryBuyCampaignHull(ctx, ShipRole.INTERDICTION_TITAN, TitanArchetype.INTERDICTION.costCredits(), 3);
                case COMMAND_INTEL_TITAN -> tryBuyCampaignHull(ctx, ShipRole.COMMAND_INTEL_TITAN, TitanArchetype.COMMAND_INTEL.costCredits(), 3);
                case BOARDING_RECOVERY_TITAN -> tryBuyCampaignHull(ctx, ShipRole.BOARDING_RECOVERY_TITAN, TitanArchetype.BOARDING_RECOVERY.costCredits(), 3);
                case ARTILLERY_TITAN -> tryBuyCampaignHull(ctx, ShipRole.ARTILLERY_TITAN, TitanArchetype.ARTILLERY.costCredits(), 3);
                case SHIELD_BASTION_TITAN -> tryBuyCampaignHull(ctx, ShipRole.SHIELD_BASTION_TITAN, TitanArchetype.SHIELD_BASTION.costCredits(), 3);
                case FLEET_TELEPORTER_TITAN -> tryBuyCampaignHull(ctx, ShipRole.FLEET_TELEPORTER_TITAN, TitanArchetype.FLEET_TELEPORTER.costCredits(), 3);
                case ELITE_SUPERSHIP_COMMAND_TITAN -> tryBuyCampaignHull(ctx, ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN, TitanArchetype.ELITE_SUPERSHIP_COMMAND.costCredits(), 3);
                case ELITE_REINFORCEMENTS_TITAN -> tryBuyCampaignHull(ctx, ShipRole.ELITE_REINFORCEMENTS_TITAN, TitanArchetype.ELITE_REINFORCEMENTS.costCredits(), 3);
                case MOBILE_STATION_TITAN -> tryBuyCampaignHull(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3);
                case HYPERWEAPON_TITAN -> tryBuyCampaignHull(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3);
                case MOTHERSHIP -> EventSystem.showBanner(ctx, "MOTHERSHIP ALREADY UNDER COMMAND", 1.4);
                default -> {
                }
            }
            return;
        }
        switch (role) {
            case PATROL -> trySwapHull(ctx, ShipRole.PATROL, 0, 0);
            case PICKET -> trySwapHull(ctx, ShipRole.PICKET, 180, 0);
            case FRIGATE -> trySwapHull(ctx, ShipRole.FRIGATE, 0, 0);
            case MINER -> trySwapHull(ctx, ShipRole.MINER, 160, 0);
            case ARTILLERY_SHIP -> trySwapHull(ctx, ShipRole.ARTILLERY_SHIP, 320, 0);
            case MISSILE_BOAT -> trySwapHull(ctx, ShipRole.MISSILE_BOAT, 300, 0);
            case CIWS_CORVETTE -> trySwapHull(ctx, ShipRole.CIWS_CORVETTE, 250, 0);
            case LIGHT_CRUISER -> trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
            case MEDIUM_CRUISER -> trySwapHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
            case CRUISER -> trySwapHull(ctx, ShipRole.CRUISER, 1100, 1);
            case HAULER -> trySwapHull(ctx, ShipRole.HAULER, 260, 1);
            case BATTLECRUISER -> trySwapHull(ctx, ShipRole.BATTLECRUISER, 1600, 2);
            case BATTLESHIP -> trySwapHull(ctx, ShipRole.BATTLESHIP, 2200, 2);
            case STEALTH_SHIP -> trySwapHull(ctx, ShipRole.STEALTH_SHIP, 1200, 2);
            case DREADNOUGHT -> trySwapHull(ctx, ShipRole.DREADNOUGHT, 3200, 3);
            case CARRIER -> trySwapHull(ctx, ShipRole.CARRIER, 2800, 3);
            case DRONE_CARRIER -> trySwapHull(ctx, ShipRole.DRONE_CARRIER, 3000, 3);
            case SUPERSHIP -> trySwapHull(ctx, ShipRole.SUPERSHIP, 5200, 3);
            case TRANSPORT_TITAN -> trySwapHull(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3);
            case BULWARK_TITAN -> trySwapHull(ctx, ShipRole.BULWARK_TITAN, TitanArchetype.BULWARK.costCredits(), 3);
            case CARRIER_SUPPORT_TITAN -> trySwapHull(ctx, ShipRole.CARRIER_SUPPORT_TITAN, TitanArchetype.CARRIER_SUPPORT.costCredits(), 3);
            case VANGUARD_TITAN -> trySwapHull(ctx, ShipRole.VANGUARD_TITAN, TitanArchetype.VANGUARD.costCredits(), 3);
            case INTERDICTION_TITAN -> trySwapHull(ctx, ShipRole.INTERDICTION_TITAN, TitanArchetype.INTERDICTION.costCredits(), 3);
            case COMMAND_INTEL_TITAN -> trySwapHull(ctx, ShipRole.COMMAND_INTEL_TITAN, TitanArchetype.COMMAND_INTEL.costCredits(), 3);
            case BOARDING_RECOVERY_TITAN -> trySwapHull(ctx, ShipRole.BOARDING_RECOVERY_TITAN, TitanArchetype.BOARDING_RECOVERY.costCredits(), 3);
            case ARTILLERY_TITAN -> trySwapHull(ctx, ShipRole.ARTILLERY_TITAN, TitanArchetype.ARTILLERY.costCredits(), 3);
            case SHIELD_BASTION_TITAN -> trySwapHull(ctx, ShipRole.SHIELD_BASTION_TITAN, TitanArchetype.SHIELD_BASTION.costCredits(), 3);
            case FLEET_TELEPORTER_TITAN -> trySwapHull(ctx, ShipRole.FLEET_TELEPORTER_TITAN, TitanArchetype.FLEET_TELEPORTER.costCredits(), 3);
            case ELITE_SUPERSHIP_COMMAND_TITAN -> trySwapHull(ctx, ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN, TitanArchetype.ELITE_SUPERSHIP_COMMAND.costCredits(), 3);
            case ELITE_REINFORCEMENTS_TITAN -> trySwapHull(ctx, ShipRole.ELITE_REINFORCEMENTS_TITAN, TitanArchetype.ELITE_REINFORCEMENTS.costCredits(), 3);
            case MOBILE_STATION_TITAN -> trySwapHull(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3);
            case HYPERWEAPON_TITAN -> trySwapHull(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3);
            case MOTHERSHIP -> trySwapHull(ctx, ShipRole.MOTHERSHIP, 7200, 3);
            default -> {
            }
        }
    }

    public static void tryBuyBeamBolt(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;

        Player player = ctx.player;
        int cost = 220;

        if (player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) {
            EventSystem.showBanner(ctx, "BEAM BOLT VOLLEY ALREADY ONLINE", 1.4);
            return;
        }
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }

        spendCredits(ctx, cost);
        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.BEAM_BOLT;
        player.applyPrimaryWeaponFamily();
        EventSystem.showBanner(ctx, "BEAM BOLT VOLLEY ONLINE", 1.6);
    }

    public static void tryEquipEnergyBolt(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;

        Player player = ctx.player;
        if (player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.ENERGY_BOLT) {
            EventSystem.showBanner(ctx, "BEAM BOLT STAGGER ALREADY ONLINE", 1.4);
            return;
        }

        player.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
        player.applyPrimaryWeaponFamily();
        EventSystem.showBanner(ctx, "BEAM BOLT STAGGER ONLINE", 1.2);
    }

    public static void tryBuyHullPlating(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;
        if (!ctx.player.canBuyHullPlatingUpgrade()) {
            EventSystem.showBanner(ctx, "HULL PLATING AT CAP", 1.2);
            return;
        }
        int cost = 60;
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        spendCredits(ctx, cost);
        if (ctx.player.buyHullPlatingUpgrade()) {
            EventSystem.showBanner(ctx, "HULL UPGRADED", 1.2);
        } else {
            refundCredits(ctx, cost);
            EventSystem.showBanner(ctx, "HULL PLATING AT CAP", 1.2);
        }
    }

    public static void tryBuyShieldArray(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;
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
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        spendCredits(ctx, cost);
        if (p.buyShieldArrayUpgrade()) {
            EventSystem.showBanner(ctx, "SHIELD ARRAY UPGRADED", 1.2);
        } else {
            refundCredits(ctx, cost);
            EventSystem.showBanner(ctx, "SHIELD ARRAY AT CAP", 1.2);
        }
    }

    public static void tryAddGunTurret(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;
        if (!ctx.player.canAddGunTurretUpgrade()) {
            EventSystem.showBanner(ctx, "GUN HARDPOINTS FULL", 1.2);
            return;
        }
        int cost = 100;
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        spendCredits(ctx, cost);
        if (ctx.player.addGunTurretUpgrade()) {
            EventSystem.showBanner(ctx, "GUN TURRET ADDED", 1.2);
        } else {
            refundCredits(ctx, cost);
            EventSystem.showBanner(ctx, "GUN HARDPOINTS FULL", 1.2);
        }
    }

    public static void tryAddMissileRack(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;
        if (!ctx.player.canAddMissileRackUpgrade()) {
            EventSystem.showBanner(ctx, "MISSILE HARDPOINTS FULL", 1.2);
            return;
        }
        int cost = 140;
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        spendCredits(ctx, cost);
        if (ctx.player.addMissileRackUpgrade()) {
            EventSystem.showBanner(ctx, "MISSILE RACK ADDED", 1.2);
        } else {
            refundCredits(ctx, cost);
            EventSystem.showBanner(ctx, "MISSILE HARDPOINTS FULL", 1.2);
        }
    }

    public static void tryUpgradeCIWS(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.ui.shopOpen) return;
        if (fleetHubEditingLocked(ctx)) return;
        if (!ctx.player.hasCIWS) {
            EventSystem.showBanner(ctx, "NO CIWS SYSTEM", 1.4);
            return;
        }
        if (ctx.player.isCIWSUpgradeMaxed()) {
            EventSystem.showBanner(ctx, "CIWS AT MAX LEVEL", 1.2);
            return;
        }
        int cost = 120;
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        spendCredits(ctx, cost);
        if (ctx.player.upgradeCIWS()) {
            EventSystem.showBanner(ctx, "CIWS UPGRADED", 1.2);
        } else {
            // Safety fallback in case CIWS state changed between checks.
            refundCredits(ctx, cost);
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
        if (fleetHubEditingLocked(ctx)) return;
        if (ctx.player.role == role) {
            EventSystem.showBanner(ctx, "HULL ALREADY EQUIPPED", 1.2);
            return;
        }
        int hangarTier = getMaxHangarTierForPlayer(ctx);
        if (hangarTier < requiredTier) {
            EventSystem.showBanner(ctx, "HANGAR TIER TOO LOW", 1.4);
            return;
        }
        if (!canAffordCredits(ctx, cost)) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        Ship.PrimaryWeaponFamily retainedPrimary = ctx.player.primaryWeaponFamily;
        spendCredits(ctx, cost);
        ctx.player.applyHull(role, ctx.player.x, ctx.player.y);
        ctx.player.primaryWeaponFamily = retainedPrimary;
        ctx.player.applyPrimaryWeaponFamily();
        EventSystem.showBanner(ctx, "HULL SWAPPED", 1.2);
    }

    public static void tryUpgradeBase(GameContext ctx, int which) {
        if (ctx == null) return;
        if (!ctx.ui.baseMenuOpen) return;
        if (fleetHubEditingLocked(ctx)) {
            EventSystem.showBanner(ctx, "FLEET UPGRADES OPEN BETWEEN SECTORS", 1.8);
            return;
        }
        if (which < 1 || which > 5) return;
        Ship base = CampaignSystem.currentBaseUpgradeAnchor(ctx);
        if (base == null) {
            EventSystem.showBanner(ctx, "DOCK AT A FRIENDLY BASE", 1.4);
            return;
        }
        if (!CampaignSystem.campaignShipUpgradeAvailable(base, which)) {
            EventSystem.showBanner(ctx, CampaignSystem.campaignShipUpgradeUnavailableReason(base, which), 1.4);
            return;
        }
        BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(base, k -> new BaseUpgrades().bindTo(base));
        boolean fleetHub = CampaignSystem.isFleetHubSession(ctx);

        int max = switch (which) {
            case 5 -> CampaignSystem.isCampaignActive(ctx) ? CampaignSystem.campaignMaxHangarTier(ctx) : 3;
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

        int oreAvailable = CampaignSystem.isCampaignActive(ctx)
                ? CampaignSystem.currentCampaignOre(ctx)
                : base.oreStockpile;
        if (!canAffordCredits(ctx, cCost) || oreAvailable < oCost) {
            EventSystem.showBanner(ctx, "INSUFFICIENT RESOURCES", 1.4);
            return;
        }

        spendCredits(ctx, cCost);
        if (CampaignSystem.isCampaignActive(ctx) && ctx.player != null) {
            CampaignSystem.spendCampaignOre(ctx, oCost);
        } else {
            base.oreStockpile -= oCost;
        }

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
                if (fleetHub) {
                    CampaignSystem.applyCampaignShipUpgradeDelta(ctx, base, 4, 1);
                } else {
                    ctx.miningBaseMul = Math.min(2.0, ctx.miningBaseMul + 0.06);
                    ctx.orePriceBaseMul = Math.min(2.0, ctx.orePriceBaseMul + 0.05);
                }
                String label = fleetHub ? CampaignSystem.campaignShipUpgradeTitle(base, 4) : "MINING OPS";
                EventSystem.showBanner(ctx, ((label == null) ? "LOGISTICS OPS" : label.toUpperCase()) + " UPGRADED", 1.2);
            }
            case 5 -> {
                up.hangarLv++;
                if (fleetHub) {
                    CampaignSystem.applyCampaignShipUpgradeDelta(ctx, base, 5, 1);
                }
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

    private static boolean isFreePurchaseMode(GameContext ctx) {
        return ctx != null && ctx.config != null && ctx.config.mode == GameMode.SHOOTING_RANGE;
    }

    private static boolean canAffordCredits(GameContext ctx, int cost) {
        if (ctx == null) return false;
        if (cost <= 0) return true;
        return isFreePurchaseMode(ctx) || ctx.credits >= cost;
    }

    private static boolean spendCredits(GameContext ctx, int cost) {
        if (ctx == null) return false;
        if (cost <= 0) return true;
        if (isFreePurchaseMode(ctx)) {
            ctx.credits = Math.max(ctx.credits, 999_999);
            return true;
        }
        if (ctx.credits < cost) return false;
        ctx.credits -= cost;
        return true;
    }

    private static void refundCredits(GameContext ctx, int cost) {
        if (ctx == null || cost <= 0) return;
        if (isFreePurchaseMode(ctx)) return;
        ctx.credits += cost;
    }

    public static int getMaxHangarTierForPlayer(GameContext ctx) {
        if (ctx == null || ctx.baseUpgrades == null) return 0;
        if (ctx.config != null && ctx.config.mode == GameMode.SHOOTING_RANGE) return 3;
        if (CampaignSystem.isCampaignActive(ctx) && ctx.player != null) {
            BaseUpgrades playerUpgrades = ctx.baseUpgrades.get(ctx.player);
            if (playerUpgrades != null) return Math.max(0, playerUpgrades.hangarLv);
        }
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

    private static void tryBuyCampaignHull(GameContext ctx, ShipRole role, int cost, int requiredTier) {
        if (ctx == null || role == null) return;
        CampaignSystem.purchasePersistentBlueShip(ctx, role, cost, requiredTier);
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
        activatePlayerEcm(ctx);
    }

    private static void setPlayerBeamMode(GameContext ctx, Ship.PrimaryWeaponFamily family) {
        if (ctx == null || ctx.player == null || family == null) return;
        if (ctx.player.primaryWeaponFamily == family) return;
        ctx.player.primaryWeaponFamily = family;
        ctx.player.applyPrimaryWeaponFamily();
        String label = (family == Ship.PrimaryWeaponFamily.BEAM_BOLT)
                ? "BEAM MODE: CONCENTRATED"
                : "BEAM MODE: RAPID FIRE";
        EventSystem.showBanner(ctx, label, 0.9);
    }

    private static void setPlayerMissileRole(GameContext ctx, Turret.MissileRole role, String banner) {
        if (ctx == null || ctx.player == null || role == null) return;
        boolean dynamicAaaAllowed = playerSupportsDynamicAaaMode(ctx.player);
        if (role == Turret.MissileRole.INTERCEPT && !dynamicAaaAllowed) {
            EventSystem.showBanner(ctx, "AAA LOADOUT VIA FLEET HUB", 1.0);
            return;
        }
        boolean changed = false;
        boolean foundRack = false;
        for (Turret turret : ctx.player.turrets) {
            if (turret == null || turret.kind != Turret.Kind.MISSILE) continue;
            foundRack = true;
            if (!dynamicAaaAllowed && turret.missileRole == Turret.MissileRole.INTERCEPT) {
                // Preserve campaign-installed AAA launchers on general hulls.
                continue;
            }
            if (turret.missileRole != role) {
                turret.missileRole = role;
                changed = true;
            }
        }
        if (!foundRack) {
            EventSystem.showBanner(ctx, "NO MISSILE RACKS INSTALLED", 1.0);
            return;
        }
        if (changed) {
            EventSystem.showBanner(ctx, banner, 0.9);
        }
    }

    private static boolean playerSupportsDynamicAaaMode(Ship ship) {
        if (ship == null || ship.role == null) return false;
        return switch (ship.role) {
            case CIWS_CORVETTE, PD_CRAFT, STATIC_TURRET -> true;
            default -> false;
        };
    }

    private static void setScienceJamming(GameContext ctx, boolean active) {
        if (ctx == null) return;
        activatePlayerEcm(ctx);
    }

    private static void setPlayerCloakMode(GameContext ctx, Ship.CloakControlMode mode) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.isStealth) {
            EventSystem.showBanner(ctx, "NO CLOAK SYSTEM INSTALLED", 1.0);
            return;
        }
        if (ctx.player.cloakControlMode == mode) return;
        ctx.player.setCloakControlMode(mode);
        String label = (mode == Ship.CloakControlMode.ACTIVE)
                ? "CLOAK MODE: ACTIVE"
                : "CLOAK MODE: CHARGE";
        EventSystem.showBanner(ctx, label, 0.9);
    }

    private static void activatePlayerEcm(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (ctx.player.tryActivateEcm()) {
            ctx.command.scienceJamming = true;
            EventSystem.showBanner(ctx, "ECM MODE: ACTIVE", 0.9);
            return;
        }
        if (ctx.player.hasActiveEcm()) {
            EventSystem.showBanner(ctx, "ECM ALREADY ACTIVE", 0.9);
            return;
        }
        EventSystem.showBanner(ctx, String.format("ECM RECHARGING: %.1fS", ctx.player.ecmCooldownRemaining()), 1.0);
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
        ctx.firingPrimaryManualLatched = false;
        ctx.firingSecondaryManual = false;
        ctx.firingSecondaryManualLatched = false;
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
