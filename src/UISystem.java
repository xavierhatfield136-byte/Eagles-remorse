import app.config.GameMode;
import app.config.PlayerTeamChoice;
import app.persistence.MenuSettingsStore;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.SwingUtilities;

public final class UISystem {
    private static final double STRATEGIC_MAP_MIN_ZOOM = 1.0;
    private static final double STRATEGIC_GALAXY_MAP_MIN_ZOOM = 1.85;
    private static final double STRATEGIC_MAP_MAX_ZOOM = 18.0;
    private static final double STRATEGIC_MAP_ZOOM_STEP = 1.22;
    private static final double MISSION_MAP_MIN_ZOOM = 1.25;

    private enum PrimaryOverlay {
        NONE,
        SHOP,
        BASE_MENU,
        MAP,
        COMMS,
        POWER_MANAGEMENT,
        CREW_STATIONS,
        FLIGHT_DECK
    }

    private UISystem(){}

    private static boolean fleetHubEditingLocked(GameContext ctx) {
        if (ctx != null && ctx.config != null && ctx.config.mode == GameMode.TUTORIAL) return false;
        return CampaignSystem.isCampaignActive(ctx) && !CampaignSystem.usesPersistentFleetShop(ctx);
    }

    private static boolean campaignFleetOverlayAvailable(GameContext ctx) {
        return CampaignSystem.usesPersistentFleetShop(ctx);
    }

    private static GameState stateAfterOverlayClose(GameContext ctx) {
        return CampaignSystem.isFleetHubSession(ctx) ? GameState.FLEET : GameState.RUNNING;
    }

    public static boolean auditAndRecoverOverlayState(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return false;

        observeStateTransition(ctx, "runtime audit");
        ArrayList<String> repairs = new ArrayList<>();
        if (ctx.state == GameState.PAUSED && ctx.ui.strategicEncounterPrompt.active) {
            ctx.ui.modalPauseOwned = true;
        }
        while (ctx.ui.strategicEncounterPrompt.active
                && !CampaignSystem.hasValidStrategicEncounterResponder(ctx)) {
            ctx.ui.clearStrategicEncounterPrompt();
            repairs.add("cleared stale encounter prompt");
            EventSystem.showBanner(ctx, "CONTACT WINDOW EXPIRED", 1.2);
        }

        if (ctx.ui.strategicEncounterPrompt.active) {
            if (ctx.state != GameState.PAUSED && ctx.state != GameState.GAME_OVER && !ctx.gameOver) {
                ctx.state = GameState.PAUSED;
                repairs.add("paused for queued encounter prompt");
            }
            ctx.ui.modalPauseOwned = true;
            if (ctx.ui.campaignActionConfirm.active) {
                ctx.ui.clearCampaignActionConfirm();
                repairs.add("encounter prompt replaced action confirmation");
            }
            if (ctx.ui.campaignHubMenu.active) {
                ctx.ui.clearCampaignHubMenu();
                repairs.add("encounter prompt replaced hub menu");
            }
        } else if (ctx.ui.campaignActionConfirm.active && ctx.ui.campaignHubMenu.active) {
            ctx.ui.clearCampaignHubMenu();
            repairs.add("action confirmation replaced hub menu");
        }

        int primaryOverlayCount = countPrimaryOverlays(ctx);
        if (primaryOverlayCount > 1) {
            PrimaryOverlay keep = primaryOverlayForState(ctx.state);
            if (keep == PrimaryOverlay.NONE || !isPrimaryOverlayOpen(ctx, keep)) {
                keep = firstOpenPrimaryOverlay(ctx);
            }
            clearPrimaryOverlaysExcept(ctx, keep);
            repairs.add("collapsed " + primaryOverlayCount + " primary overlays to " + keep.name());
        }

        if (ctx.state == GameState.PAUSED && ctx.ui.modalPauseOwned
                && !ctx.ui.strategicEncounterPrompt.active) {
            if (!ctx.gameOver) ctx.state = GameState.RUNNING;
            ctx.ui.modalPauseOwned = false;
            repairs.add("released orphaned modal pause");
        }

        if (!repairs.isEmpty()) {
            ctx.ui.overlayInvariantRepairCount++;
            ctx.ui.overlayInvariantLastRepair = String.join("; ", repairs);
            observeStateTransition(ctx, "overlay recovery");
            return true;
        }
        return false;
    }

    public static String overlayInvariantReadout(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return "unavailable";
        if (ctx.ui.overlayInvariantRepairCount <= 0) return "clean";
        return ctx.ui.overlayInvariantRepairCount + " repair(s): " + ctx.ui.overlayInvariantLastRepair;
    }

    public static String overlayOwnerReadout(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return "unavailable";
        return ctx.ui.blockingModalOwner().name()
                + "  queued=" + ctx.ui.queuedStrategicEncounterPromptCount()
                + "  modalPause=" + ctx.ui.modalPauseOwned;
    }

    public static String stateTransitionHistoryReadout(GameContext ctx) {
        if (ctx == null || ctx.ui == null || ctx.ui.stateTransitionHistory.isEmpty()) return "no transitions";
        return String.join(" | ", ctx.ui.stateTransitionHistory);
    }

    public static void observeStateTransition(GameContext ctx, String reason) {
        if (ctx == null || ctx.ui == null || ctx.state == null) return;
        if (ctx.ui.lastObservedGameState == ctx.state) return;
        GameState previous = ctx.ui.lastObservedGameState;
        ctx.ui.lastObservedGameState = ctx.state;
        if (!isLoggedOverlayState(previous) && !isLoggedOverlayState(ctx.state)) return;
        String detail = ((previous == null) ? "INIT" : previous.name()) + " -> " + ctx.state.name()
                + " (" + ((reason == null || reason.isBlank()) ? "unspecified" : reason.trim()) + ")";
        ctx.ui.stateTransitionHistory.add(detail);
        while (ctx.ui.stateTransitionHistory.size() > 10) ctx.ui.stateTransitionHistory.remove(0);
        System.out.println("[ui-state] " + detail);
    }

    public static boolean dismissStaleStrategicEncounterPrompt(GameContext ctx) {
        if (ctx == null || ctx.ui == null || !ctx.ui.strategicEncounterPrompt.active
                || CampaignSystem.hasValidStrategicEncounterResponder(ctx)) return false;
        ctx.ui.clearStrategicEncounterPrompt();
        if (!ctx.ui.strategicEncounterPrompt.active && ctx.state == GameState.PAUSED && !ctx.gameOver) {
            ctx.state = GameState.RUNNING;
            ctx.ui.modalPauseOwned = false;
        }
        EventSystem.showBanner(ctx, "STALE CONTACT DISMISSED", 1.2);
        observeStateTransition(ctx, "dismiss stale prompt");
        return true;
    }

    public static String printOverlayDiagnostics(GameContext ctx) {
        observeStateTransition(ctx, "developer diagnostics");
        String report = "owner=" + overlayOwnerReadout(ctx)
                + " state=" + ((ctx == null || ctx.state == null) ? "unavailable" : ctx.state.name())
                + " history=" + stateTransitionHistoryReadout(ctx);
        System.out.println("[ui-diagnostics] " + report);
        if (ctx != null) EventSystem.showBanner(ctx, "OVERLAY DIAGNOSTICS PRINTED", 1.0);
        return report;
    }

    private static boolean isLoggedOverlayState(GameState state) {
        return state == GameState.PAUSED || state == GameState.MAP || state == GameState.SHOP
                || state == GameState.FLEET || state == GameState.BASE_MENU;
    }

    private static int countPrimaryOverlays(GameContext ctx) {
        int count = 0;
        for (PrimaryOverlay overlay : PrimaryOverlay.values()) {
            if (isPrimaryOverlayOpen(ctx, overlay)) count++;
        }
        return count;
    }

    private static PrimaryOverlay primaryOverlayForState(GameState state) {
        if (state == null) return PrimaryOverlay.NONE;
        return switch (state) {
            case SHOP -> PrimaryOverlay.SHOP;
            case BASE_MENU -> PrimaryOverlay.BASE_MENU;
            case MAP -> PrimaryOverlay.MAP;
            case POWER_MANAGEMENT -> PrimaryOverlay.POWER_MANAGEMENT;
            case CREW_STATIONS -> PrimaryOverlay.CREW_STATIONS;
            case FLIGHT_DECK -> PrimaryOverlay.FLIGHT_DECK;
            default -> PrimaryOverlay.NONE;
        };
    }

    private static PrimaryOverlay firstOpenPrimaryOverlay(GameContext ctx) {
        for (PrimaryOverlay overlay : PrimaryOverlay.values()) {
            if (isPrimaryOverlayOpen(ctx, overlay)) return overlay;
        }
        return PrimaryOverlay.NONE;
    }

    private static boolean isPrimaryOverlayOpen(GameContext ctx, PrimaryOverlay overlay) {
        if (ctx == null || ctx.ui == null || overlay == null) return false;
        return switch (overlay) {
            case SHOP -> ctx.ui.shopOpen;
            case BASE_MENU -> ctx.ui.baseMenuOpen;
            case MAP -> ctx.ui.mapOpen;
            case COMMS -> ctx.ui.commsOpen;
            case POWER_MANAGEMENT -> ctx.ui.powerManagementOpen;
            case CREW_STATIONS -> ctx.ui.crewStationsOpen;
            case FLIGHT_DECK -> ctx.ui.flightDeckOpen;
            case NONE -> false;
        };
    }

    private static void clearPrimaryOverlaysExcept(GameContext ctx, PrimaryOverlay keep) {
        ctx.ui.shopOpen = keep == PrimaryOverlay.SHOP;
        ctx.ui.baseMenuOpen = keep == PrimaryOverlay.BASE_MENU;
        ctx.ui.mapOpen = keep == PrimaryOverlay.MAP;
        ctx.ui.commsOpen = keep == PrimaryOverlay.COMMS;
        ctx.ui.powerManagementOpen = keep == PrimaryOverlay.POWER_MANAGEMENT;
        ctx.ui.crewStationsOpen = keep == PrimaryOverlay.CREW_STATIONS;
        ctx.ui.flightDeckOpen = keep == PrimaryOverlay.FLIGHT_DECK;
    }

    public static void closeAllOverlays(GameContext ctx) {
        if (ctx == null) return;
        boolean hadOverlay = ctx.ui.hasBlockingOverlay();
        ctx.ui.clearCommTradeMenu();
        ctx.ui.clearCommsContextMenu();
        ctx.ui.commsOpen = false;
        ctx.ui.formationMenuOpen = false;
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = false;
        ctx.ui.mapOpen = false;
        ctx.ui.powerManagementOpen = false;
        ctx.ui.crewStationsOpen = false;
        ctx.ui.flightDeckOpen = false;
        ctx.ui.controlsScreenOpen = false;
        clearManualCombatInputs(ctx);
        if (!ctx.gameOver) ctx.state = stateAfterOverlayClose(ctx);
        if (hadOverlay) AudioSystem.onUiClose(ctx);
    }

    public static boolean handleCommTradeMenuClick(GameContext ctx, MouseEvent e, int viewW, int viewH) {
        if (ctx == null || ctx.ui == null || e == null || !ctx.ui.commTradeMenu.active) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return true;
        Rectangle minus = Renderer.commTradeQuantityMinusRect(viewW, viewH);
        Rectangle plus = Renderer.commTradeQuantityPlusRect(viewW, viewH);
        Rectangle slider = Renderer.commTradeQuantitySliderRect(viewW, viewH);
        String[] tabs = CommSystem.tradeTabs();
        for (int i = 0; i < tabs.length; i++) {
            Rectangle tab = Renderer.commTradeMenuTabRect(viewW, viewH, i);
            if (tab.contains(e.getX(), e.getY())) {
                CommSystem.setTradeTab(ctx, tabs[i]);
                return true;
            }
        }
        if (minus.contains(e.getX(), e.getY())) {
            CommSystem.adjustTradeQuantity(ctx, -1);
            return true;
        }
        if (plus.contains(e.getX(), e.getY())) {
            CommSystem.adjustTradeQuantity(ctx, 1);
            return true;
        }
        if (slider.contains(e.getX(), e.getY())) {
            double f = (e.getX() - slider.x) / (double) Math.max(1, slider.width);
            CommSystem.setTradeQuantityFraction(ctx, f);
            return true;
        }
        for (int i = 0; i < CommSystem.visibleTradeOptions(ctx).size(); i++) {
            Rectangle rect = Renderer.commTradeMenuOptionRect(viewW, viewH, i);
            if (rect.contains(e.getX(), e.getY())) {
                int optionIndex = CommSystem.optionIndexForVisibleTradeRow(ctx, i);
                ctx.ui.commTradeMenu.selectedIndex = optionIndex;
                CommSystem.chooseTradeMenuOption(ctx, optionIndex);
                return true;
            }
        }
        Rectangle close = Renderer.commTradeMenuCloseRect(viewW, viewH);
        if (close.contains(e.getX(), e.getY())) {
            ctx.ui.clearCommTradeMenu();
            EventSystem.showBanner(ctx, "TRADE CHANNEL CLOSED", 0.8);
        }
        return true;
    }

    public static boolean handleCommsContextMenuClick(GameContext ctx, MouseEvent e, int viewW, int viewH) {
        if (ctx == null || ctx.ui == null || e == null || !ctx.ui.commsContextMenu.active) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) {
            if (SwingUtilities.isRightMouseButton(e)) ctx.ui.clearCommsContextMenu();
            return true;
        }
        Rectangle close = Renderer.commsContextCloseRect(ctx, viewW, viewH);
        if (close.contains(e.getX(), e.getY())) {
            ctx.ui.clearCommsContextMenu();
            return true;
        }
        java.util.List<CommSystem.CommsActionView> actions =
                CommSystem.actionsFor(ctx, ctx.ui.commsContextMenu.targetId);
        for (int i = 0; i < actions.size(); i++) {
            Rectangle rect = Renderer.commsContextActionRect(ctx, viewW, viewH, i);
            if (!rect.contains(e.getX(), e.getY())) continue;
            CommSystem.CommsActionView action = actions.get(i);
            if (action != null && action.enabled) {
                int targetId = ctx.ui.commsContextMenu.targetId;
                ctx.ui.commsSelectedContactId = targetId;
                ctx.ui.clearCommsContextMenu();
                CommSystem.performVisibleAction(ctx, targetId, action.id);
            } else {
                String reason = (action == null || action.disabledReason == null || action.disabledReason.isBlank())
                        ? "COMMS ACTION UNAVAILABLE"
                        : action.disabledReason.toUpperCase(Locale.US);
                EventSystem.showBanner(ctx, reason, 1.2);
            }
            return true;
        }
        ctx.ui.clearCommsContextMenu();
        return true;
    }

    public static boolean tryOpenCommsContextAtWorld(GameContext ctx, double worldX, double worldY, int screenX, int screenY) {
        if (ctx == null || ctx.ui == null) return false;
        Ship target = CommSystem.nearestCommsContactAt(ctx, worldX, worldY, 260.0);
        if (target == null) return false;
        ctx.ui.commsSelectedContactId = target.id;
        ctx.ui.showCommsContextMenu(target.id, screenX, screenY);
        EventSystem.showBanner(ctx, "COMMS CONTEXT: " + target.name.toUpperCase(Locale.US), 0.8);
        return true;
    }

    public static boolean handleCommsPanelClick(GameContext ctx, MouseEvent e, int viewW, int viewH) {
        if (ctx == null || ctx.ui == null || e == null || !ctx.ui.commsOpen) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return true;
        Rectangle close = Renderer.commsPanelCloseRect(viewW, viewH);
        if (close.contains(e.getX(), e.getY())) {
            toggleCommsPanel(ctx);
            return true;
        }
        UiState.CommsFilter[] filters = UiState.CommsFilter.values();
        for (int i = 0; i < filters.length; i++) {
            if (Renderer.commsFilterTabRect(viewW, viewH, i).contains(e.getX(), e.getY())) {
                ctx.ui.commsFilter = filters[i];
                ctx.ui.commsSelectedContactId = -1;
                CommSystem.selectedContactView(ctx);
                return true;
            }
        }
        java.util.List<CommSystem.CommsContactView> contacts = CommSystem.contactViews(ctx, ctx.ui.commsFilter);
        int rows = Math.min(Renderer.commsVisibleContactRows(viewW, viewH), contacts.size());
        for (int i = 0; i < rows; i++) {
            if (Renderer.commsContactRowRect(viewW, viewH, i).contains(e.getX(), e.getY())) {
                ctx.ui.commsSelectedContactId = contacts.get(i).shipId;
                return true;
            }
        }
        java.util.List<CommSystem.CommsActionView> actions = CommSystem.actionsFor(ctx, ctx.ui.commsSelectedContactId);
        for (int i = 0; i < actions.size(); i++) {
            if (Renderer.commsActionButtonRect(viewW, viewH, i).contains(e.getX(), e.getY())) {
                CommSystem.CommsActionView action = actions.get(i);
                if (action != null && action.enabled) {
                    CommSystem.performVisibleAction(ctx, ctx.ui.commsSelectedContactId, action.id);
                } else {
                    String reason = (action == null || action.disabledReason == null || action.disabledReason.isBlank())
                            ? "COMMS ACTION UNAVAILABLE"
                            : action.disabledReason.toUpperCase(Locale.US);
                    EventSystem.showBanner(ctx, reason, 1.2);
                }
                return true;
            }
        }
        return true;
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        // If awaiting fleet hub choice after sector complete, TAB opens it immediately.
        if (CampaignSystem.tryEnterFleetHubImmediately(ctx)) {
            return;
        }
        if (CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
            ctx.ui.mapOpen = true;
            ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
            clearManualCombatInputs(ctx);
            ctx.state = GameState.MAP;
            AudioSystem.onUiOpen(ctx);
            return;
        }
        ctx.ui.shopOpen = !ctx.ui.shopOpen;
        if (ctx.ui.shopOpen) {
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
            clearManualCombatInputs(ctx);
            if (CampaignSystem.usesPersistentFleetShop(ctx) && ctx.player != null && ctx.ui.fleetSelectedShipId <= 0) {
                ctx.ui.fleetSelectedShipId = ctx.player.id;
            }
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
        if (CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            ctx.ui.mapOpen = true;
            ctx.state = GameState.MAP;
            EventSystem.showBanner(ctx, "CAMPAIGN MAP ACTIVE", 1.0);
            return;
        }
        ctx.ui.mapOpen = !ctx.ui.mapOpen;
        if (ctx.ui.mapOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
            clearManualCombatInputs(ctx);
            BattlefieldSectorSystem.ensureSelection(ctx);
            BattlefieldSectorSystem.ensureLoadedSector(ctx);
            focusTacticalMapOnCurrentMission(ctx);
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
        if (CampaignSystem.isCampaignActive(ctx) && !CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            ctx.ui.baseMenuOpen = !ctx.ui.baseMenuOpen;
            if (ctx.ui.baseMenuOpen) {
                ctx.ui.shopOpen = false;
                ctx.ui.mapOpen = false;
                ctx.ui.powerManagementOpen = false;
                ctx.ui.crewStationsOpen = false;
                ctx.ui.flightDeckOpen = false;
                ctx.ui.commsOpen = false;
                ctx.ui.formationMenuOpen = false;
                clearManualCombatInputs(ctx);
                ctx.state = GameState.BASE_MENU;
                AudioSystem.onUiOpen(ctx);
                EventSystem.showBanner(ctx, "COMMAND SHIP UPGRADE CONSOLE OPEN", 1.1);
            } else {
                ctx.state = stateAfterOverlayClose(ctx);
                AudioSystem.onUiClose(ctx);
            }
            return;
        }
        if (fleetHubEditingLocked(ctx)) {
            ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
            ctx.ui.mapOpen = true;
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.state = GameState.MAP;
            EventSystem.showBanner(ctx, "FLEET MANAGEMENT IS AVAILABLE IN-WORLD", 1.2);
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
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
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
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
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
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
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
            ctx.ui.commsOpen = false;
            ctx.ui.formationMenuOpen = false;
            ctx.ui.flightDeckFocus = Math.max(0, Math.min(4, ctx.ui.flightDeckFocus));
            clearManualCombatInputs(ctx);
            ctx.state = GameState.FLIGHT_DECK;
            AudioSystem.onUiOpen(ctx);
        } else {
            ctx.state = stateAfterOverlayClose(ctx);
            AudioSystem.onUiClose(ctx);
        }
    }

    public static void toggleCommsPanel(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;

        ctx.ui.commsOpen = !ctx.ui.commsOpen;
        ctx.ui.clearCommsContextMenu();
        if (ctx.ui.commsOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            ctx.ui.formationMenuOpen = false;
            if (ctx.ui.commsSelectedContactId <= 0 && ctx.lockedTarget != null) {
                ctx.ui.commsSelectedContactId = ctx.lockedTarget.id;
            }
            CommSystem.selectedContactView(ctx);
            clearManualCombatInputs(ctx);
            if (!ctx.gameOver) ctx.state = stateAfterOverlayClose(ctx);
            AudioSystem.onUiOpen(ctx);
            EventSystem.showBanner(ctx, "COMMS PANEL OPEN", 0.8);
        } else {
            if (!ctx.gameOver) ctx.state = stateAfterOverlayClose(ctx);
            AudioSystem.onUiClose(ctx);
        }
    }

    public static boolean handleCoreMenuClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || e == null) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;

        int idx = Renderer.coreMenuButtonAt(ctx, viewportW, viewportH, e.getX(), e.getY());
        if (idx < 0) return false;

        if (ctx.config != null && ctx.config.mode == GameMode.SHOWCASE) {
            switch (idx) {
                case 0 -> SpawnSystem.loadShowcaseTeam(ctx, Faction.ALLY);
                case 1 -> SpawnSystem.loadShowcaseTeam(ctx, Faction.ENEMY);
                case 2 -> SpawnSystem.loadShowcaseTeam(ctx, Faction.TEAM_C);
                case 3 -> SpawnSystem.loadShowcaseTeam(ctx, Faction.TEAM_D);
                case 4 -> toggleCrewStations(ctx);
                case 5 -> GameplayActions.trySafeMissionExit(ctx);
                case 6 -> toggleFormationMenu(ctx);
                case 7 -> toggleCommsPanel(ctx);
                default -> {
                    return false;
                }
            }
            return true;
        }

        switch (idx) {
            case 0 -> toggleShop(ctx);
            case 1 -> toggleBaseMenu(ctx);
            case 2 -> toggleMap(ctx);
            case 3 -> togglePowerManagement(ctx);
            case 4 -> toggleCrewStations(ctx);
            case 5 -> GameplayActions.trySafeMissionExit(ctx);
            case 6 -> toggleFormationMenu(ctx);
            case 7 -> toggleCommsPanel(ctx);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void toggleFormationMenu(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.ui.formationMenuOpen = !ctx.ui.formationMenuOpen;
        if (ctx.ui.formationMenuOpen) {
            ctx.ui.shopOpen = false;
            ctx.ui.baseMenuOpen = false;
            ctx.ui.mapOpen = false;
            ctx.ui.powerManagementOpen = false;
            ctx.ui.crewStationsOpen = false;
            ctx.ui.flightDeckOpen = false;
            ctx.ui.commsOpen = false;
            clearManualCombatInputs(ctx);
            EventSystem.showBanner(ctx, "FORMATION CONTROL OPEN", 0.8);
        } else {
            EventSystem.showBanner(ctx, "FORMATION CONTROL CLOSED", 0.6);
        }
    }

    public static boolean handleFormationMenuClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.ui == null || e == null || !ctx.ui.formationMenuOpen) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return true;
        if (Renderer.formationMenuCloseRect(viewportW, viewportH).contains(e.getPoint())) {
            ctx.ui.formationMenuOpen = false;
            return true;
        }
        for (GameContext.FleetFormation formation : GameContext.FleetFormation.values()) {
            Rectangle rect = Renderer.formationMenuOptionRect(viewportW, viewportH, formation);
            if (!rect.contains(e.getPoint())) continue;
            setAlliedFleetFormation(ctx, formation);
            return true;
        }
        Rectangle panel = Renderer.formationMenuRect(viewportW, viewportH);
        if (!panel.contains(e.getPoint())) ctx.ui.formationMenuOpen = false;
        return true;
    }

    public static boolean handleShopClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.player == null || e == null) return false;
        if (!ctx.ui.shopOpen) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;

        boolean campaignShop = CampaignSystem.usesPersistentFleetShop(ctx);
        if (campaignShop) {
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
                    case SET_REFIT_FILTER -> {
                        selectFleetRefitFilter(ctx, fleetTarget.refitFilter);
                        return true;
                    }
                    case SELECT_LOADOUT_GROUP -> {
                        selectFleetLoadoutGroup(ctx, fleetTarget.loadoutGroup);
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

    public static boolean handleShopWheel(GameContext ctx, MouseWheelEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.ui == null || e == null || !ctx.ui.shopOpen) return false;
        if (!CampaignSystem.usesPersistentFleetShop(ctx) || !ctx.ui.fleetRefitMode) return false;
        int rotation = e.getWheelRotation();
        if (rotation == 0) return true;
        if (Renderer.fleetRefitShipListContains(viewportW, viewportH, e.getX(), e.getY())) {
            int max = Renderer.fleetRefitShipMaxScroll(ctx, ctx.ui, viewportW, viewportH);
            ctx.ui.fleetRefitShipScroll = MathUtil.clamp(ctx.ui.fleetRefitShipScroll + rotation, 0, max);
            return true;
        }
        if (Renderer.fleetRefitSlotListContains(viewportW, viewportH, e.getX(), e.getY())) {
            int max = Renderer.fleetRefitSlotMaxScroll(ctx, ctx.ui, viewportW, viewportH);
            ctx.ui.fleetRefitSlotScroll = MathUtil.clamp(ctx.ui.fleetRefitSlotScroll + rotation, 0, max);
            return true;
        }
        return false;
    }

    public static void selectFleetShip(GameContext ctx, int shipId) {
        if (ctx == null || !campaignFleetOverlayAvailable(ctx)) return;
        ctx.ui.fleetSelectedShipId = shipId;
        ctx.ui.fleetSelectedTurretIndex = -1;  // Reset turret selection when changing ships
        ctx.ui.fleetRefitSlotScroll = 0;
    }

    public static void selectFleetTurret(GameContext ctx, int turretIndex) {
        if (ctx == null || !campaignFleetOverlayAvailable(ctx)) return;
        if (ctx.ui.fleetSelectedShipId < 0) return;  // Must have a ship selected first
        Ship selected = findShipInFleet(ctx, ctx.ui.fleetSelectedShipId);
        if (selected == null || turretIndex < 0 || turretIndex >= selected.turrets.size()) {
            ctx.ui.fleetSelectedTurretIndex = -1;
            return;
        }
        ctx.ui.fleetSelectedTurretIndex = turretIndex;
    }

    public static void selectFleetRefitFilter(GameContext ctx, RefitHullFilter filter) {
        if (ctx == null || ctx.ui == null || filter == null || !campaignFleetOverlayAvailable(ctx)) return;
        ctx.ui.fleetRefitFilter = filter;
        ctx.ui.fleetRefitShipScroll = 0;
        ctx.ui.fleetSelectedTurretIndex = -1;
        Ship selected = findShipInFleet(ctx, ctx.ui.fleetSelectedShipId);
        if (selected == null || !filter.matches(selected.role)) {
            ctx.ui.fleetSelectedShipId = -1;
        }
    }

    public static void selectFleetLoadoutGroup(GameContext ctx, int group) {
        if (ctx == null || ctx.ui == null || !campaignFleetOverlayAvailable(ctx)) return;
        ctx.ui.fleetRefitLoadoutGroup = MathUtil.clamp(group, 0, 2);
        ctx.ui.fleetRefitSlotScroll = 0;
        ctx.ui.fleetSelectedTurretIndex = -1;
    }

    public static void setMissileRoleForSelectedTurret(GameContext ctx, Turret.MissileRole role) {
        if (ctx == null || !campaignFleetOverlayAvailable(ctx)) return;
        if (ctx.ui.fleetSelectedShipId < 0 || ctx.ui.fleetSelectedTurretIndex < 0) return;
        Ship selected = findShipInFleet(ctx, ctx.ui.fleetSelectedShipId);
        if (selected == null || selected.turrets.size() <= ctx.ui.fleetSelectedTurretIndex) return;
        Turret turret = selected.turrets.get(ctx.ui.fleetSelectedTurretIndex);
        if (turret != null && turret.kind == Turret.Kind.MISSILE) {
            turret.missileRole = role;
            CampaignSystem.syncPersistentFleetEntrySnapshotForShip(ctx, selected);
        }
    }

    private static void swapFleetTurretKind(GameContext ctx, int shipId, int turretIndex, Turret.Kind desired) {
        if (ctx == null || desired == null || !campaignFleetOverlayAvailable(ctx)) return;
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
        CampaignSystem.syncPersistentFleetEntrySnapshotForShip(ctx, ship);
    }

    private static Ship findShipInFleet(GameContext ctx, int shipId) {
        if (ctx == null || ctx.ships == null) return null;
        for (Ship s : ctx.ships) {
            if (s.id == shipId && CampaignSystem.isFleetRefitEditableCandidate(ctx, s)) {
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
        ctx.player.setCustomPowerBusAllocation(p[0], p[1], p[2], p[3], p[4], p[5]);
        // Manual engineering input immediately overrides automation.
        ctx.command.engineeringAutomation = false;
        ctx.command.playerPowerManualOverride = true;
    }

    public static void applyPowerPreset(GameContext ctx, Ship.PowerPreset preset) {
        if (ctx == null || ctx.player == null) return;
        if (preset == null) preset = Ship.PowerPreset.BALANCED;
        ctx.player.setPowerPreset(preset);
        ctx.command.engineeringAutomation = false;
        ctx.command.playerPowerManualOverride = true;
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
            case ENGINEERING -> {
                ctx.command.engineeringAutomation = enabled;
                ctx.command.playerPowerManualOverride = !enabled;
            }
            case SCIENCE -> ctx.command.scienceAutomation = enabled;
        }
    }

    public static void toggleActiveStationAutomation(GameContext ctx) {
        if (ctx == null) return;
        GameContext.CrewStation s = ctx.command.activeCrewStation;
        setStationAutomation(ctx, s, !stationAutomation(ctx, s));
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
            ctx.player.setCrewManualPriorityRoom(roomId);
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

    public static boolean handleCampaignMapUiClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.ui == null || e == null) return false;
        if (!SwingUtilities.isLeftMouseButton(e)) return false;
        boolean galaxyMode = CampaignSystem.isStrategicGalaxyMapMode(ctx);
        if (galaxyMode
                && ctx.ui.strategicEncounterPrompt.active
                && !ctx.ui.campaignHubMenu.active
                && !ctx.ui.campaignActionConfirm.active
                && !CampaignSystem.hasValidStrategicEncounterResponder(ctx)) {
            dismissStaleStrategicEncounterPrompt(ctx);
            return false;
        }
        boolean encounterPromptBlocking = galaxyMode
                && CampaignSystem.hasValidStrategicEncounterResponder(ctx)
                && !ctx.ui.campaignHubMenu.active
                && !ctx.ui.campaignActionConfirm.active;

        Renderer.CampaignHubClickTarget target = galaxyMode
                ? Renderer.campaignHubClickTargetAt(ctx, viewportW, viewportH, e.getX(), e.getY())
                : Renderer.tacticalMapClickTargetAt(ctx, viewportW, viewportH, e.getX(), e.getY());
        if (target == null) return encounterPromptBlocking;

        switch (target.kind) {
            case STRATEGIC_ENCOUNTER -> {
                return handleStrategicEncounterClick(ctx, target.valueId);
            }
            case SERVICE -> {
                try {
                    CampaignSystem.HubService service = CampaignSystem.HubService.valueOf(target.serviceId);
                    return CampaignSystem.executeSelectedHubService(ctx, service.name());
                } catch (Exception ignored) {
                    return false;
                }
            }
            case TAB -> {
                try {
                    if (galaxyMode) {
                        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.valueOf(target.valueId);
                    } else {
                        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.valueOf(target.valueId);
                    }
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
            case OVERLAY -> {
                return CampaignSystem.setStrategicMapOverlay(ctx, target.valueId);
            }
            case ACTION -> {
                return galaxyMode
                        ? handleCampaignCommandAction(ctx, target.valueId)
                        : CampaignSystem.executeTacticalMapAction(ctx, target.valueId);
            }
            case FLEET_ROSTER -> {
                try {
                    int slotId = Integer.parseInt(target.valueId);
                    boolean selected = CampaignSystem.selectCampaignFleetRosterSlot(ctx, slotId);
                    if (selected && e.getClickCount() >= 2) {
                        return CampaignSystem.openFocusedCampaignFleetEditor(ctx);
                    }
                    return selected;
                } catch (Exception ignored) {
                    return false;
                }
            }
            case ORE_SALE_AMOUNT -> {
                try {
                    CampaignSystem.setCampaignOreSaleFraction(ctx, Double.parseDouble(target.valueId));
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
            case CONFIRM -> {
                if (ctx.ui.campaignActionConfirm.active) {
                    return CampaignSystem.confirmCampaignAction(ctx);
                }
                return CampaignSystem.confirmSelectedHubService(ctx);
            }
            case CLOSE -> {
                if (ctx.ui.campaignActionConfirm.active) {
                    CampaignSystem.cancelCampaignActionConfirm(ctx);
                    return true;
                }
                CampaignSystem.closeHubServiceMenu(ctx);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean handleStrategicEncounterClick(GameContext ctx, String actionId) {
        if (ctx == null || actionId == null || actionId.isBlank()) return false;
        return switch (actionId) {
            case "TAKE_COMMAND" -> CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx);
            case "AUTO_RESOLVE" -> CampaignSystem.autoResolvePendingStrategicEncounter(ctx);
            case "DISMISS_STALE" -> dismissStaleStrategicEncounterPrompt(ctx);
            case "INSERT_CLOSE" -> CampaignSystem.setPendingEncounterInsertionRange(ctx, "CLOSE");
            case "INSERT_MODERATE" -> CampaignSystem.setPendingEncounterInsertionRange(ctx, "MODERATE");
            case "INSERT_FAR" -> CampaignSystem.setPendingEncounterInsertionRange(ctx, "FAR");
            default -> {
                if (actionId.startsWith("DEPLOY:")) {
                    String[] parts = actionId.split(":");
                    if (parts.length != 3) yield false;
                    try {
                        yield CampaignSystem.setPendingEncounterDeploymentPoint(ctx,
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]));
                    } catch (Exception ignored) {
                        yield false;
                    }
                }
                yield false;
            }
            case "FOLLOW" -> CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "FOLLOW");
            case "JOIN" -> CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "JOIN");
            case "IGNORE" -> CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "IGNORE");
            case "SUPPORT" -> CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "SUPPORT");
            case "OBSERVE" -> CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "OBSERVE");
        };
    }

    public static boolean handleCampaignMapWheel(GameContext ctx, MouseWheelEvent e, int viewportW, int viewportH) {
        if (ctx == null || ctx.ui == null || e == null || !ctx.ui.mapOpen) return false;
        if (!CampaignSystem.isStrategicGalaxyMapMode(ctx)) return false;
        if (CampaignSystem.hasPendingStrategicEncounterChoice(ctx)
                && ctx.ui.strategicEncounterPrompt.kind == UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE) {
            int rotation = e.getWheelRotation();
            if (rotation == 0) return true;
            int visibleRows = Renderer.strategicEncounterAssetVisibleRows(viewportH);
            if (Renderer.strategicEncounterFriendlyAssetPaneContains(ctx, viewportW, viewportH, e.getX(), e.getY())) {
                int total = ctx.ui.strategicEncounterPrompt.friendlyAssets.isEmpty()
                        ? ctx.ui.strategicEncounterPrompt.friendlyAssetLines.size()
                        : ctx.ui.strategicEncounterPrompt.friendlyAssets.size();
                int max = Math.max(0, total - visibleRows);
                ctx.ui.strategicEncounterPrompt.friendlyAssetScroll =
                        MathUtil.clamp(ctx.ui.strategicEncounterPrompt.friendlyAssetScroll + rotation, 0, max);
                return true;
            }
            if (Renderer.strategicEncounterEnemyAssetPaneContains(ctx, viewportW, viewportH, e.getX(), e.getY())) {
                int total = ctx.ui.strategicEncounterPrompt.enemyAssets.isEmpty()
                        ? ctx.ui.strategicEncounterPrompt.enemyAssetLines.size()
                        : ctx.ui.strategicEncounterPrompt.enemyAssets.size();
                int max = Math.max(0, total - visibleRows);
                ctx.ui.strategicEncounterPrompt.enemyAssetScroll =
                        MathUtil.clamp(ctx.ui.strategicEncounterPrompt.enemyAssetScroll + rotation, 0, max);
                return true;
            }
            return true;
        }
        if (ctx.ui.campaignHubMenu.active) {
            try {
                CampaignSystem.HubService service = CampaignSystem.HubService.valueOf(ctx.ui.campaignHubMenu.serviceId);
                if (service == CampaignSystem.HubService.TRADE) {
                    return CampaignSystem.adjustCampaignOreSaleAmount(ctx, -e.getWheelRotation());
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        if (ctx.ui.campaignCommandTab != UiState.CampaignCommandTab.FLEET) return false;
        if (!Renderer.campaignFleetRosterContains(ctx, viewportW, viewportH, e.getX(), e.getY())) return false;
        int visibleRows = Renderer.campaignFleetRosterVisibleRows(ctx, viewportW, viewportH);
        return CampaignSystem.scrollCampaignFleetRoster(ctx, e.getWheelRotation(), visibleRows);
    }

    public static void handleMapClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        Rectangle rect = Renderer.getStrategicMapInnerRect(
                viewportW, viewportH, CampaignSystem.isStrategicGalaxyMapMode(ctx));
        if (!rect.contains(e.getPoint())) return;

        double nx = (e.getX() - rect.x) / (double) rect.width;
        double ny = (e.getY() - rect.y) / (double) rect.height;
        double worldX = strategicMapWorldXAt(ctx, nx);
        double worldY = strategicMapWorldYAt(ctx, ny);
        if (!CampaignSystem.isStrategicGalaxyMapMode(ctx)
                && SwingUtilities.isRightMouseButton(e)
                && tryOpenCommsContextAtWorld(ctx, worldX, worldY, e.getX(), e.getY())) {
            return;
        }
        if (SwingUtilities.isMiddleMouseButton(e)) {
            focusStrategicMapAt(ctx, worldX, worldY);
            EventSystem.showBanner(ctx, "MAP FOCUS SHIFTED", 0.9);
            return;
        }
        if (CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            CampaignSystem.CampaignLocation clickedLocation =
                    campaignLocationAtMapClick(ctx, worldX, worldY, rect);
            CampaignSystem.CampaignSupportMarker clickedSupport =
                    CampaignSystem.nearestSupportMarker(ctx, worldX, worldY, 110.0);
            double siteHitRadius = campaignSiteHitRadiusWorld(ctx, rect);
            if (shouldPreferCampaignSupportClick(clickedLocation, clickedSupport, worldX, worldY, siteHitRadius)) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    addPing(ctx, clickedSupport.x, clickedSupport.y, 2.2);
                    EventSystem.showBanner(ctx, "CONTACT PING: " + clickedSupport.label.toUpperCase(), 1.2);
                    return;
                }
                String intel = "";
                if (clickedSupport.subtitle != null && clickedSupport.subtitle.contains("|")) {
                    intel = clickedSupport.subtitle.substring(0, clickedSupport.subtitle.indexOf('|')).trim();
                }
                boolean hostile = isHostileCampaignSupportMarker(ctx, clickedSupport);
                CampaignSystem.selectCampaignContactTarget(ctx,
                        clickedSupport.label,
                        clickedSupport.subtitle,
                        intel,
                        clickedSupport.x,
                        clickedSupport.y,
                        hostile,
                        true);
                ctx.ui.waypointX = GameMath.clamp(clickedSupport.x, 0, ctx.WORLD_W);
                ctx.ui.waypointY = GameMath.clamp(clickedSupport.y, 0, ctx.WORLD_H);
                addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
                EventSystem.showBanner(ctx, "CONTACT SELECTED: " + clickedSupport.label.toUpperCase(), 1.2);
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    CampaignSystem.startTravelToSelectedLocation(ctx);
                }
                return;
            }
            if (clickedLocation != null) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    addPing(ctx, clickedLocation.x, clickedLocation.y, 2.2);
                    EventSystem.showBanner(ctx, "LOCATION PING: " + clickedLocation.name.toUpperCase(), 1.2);
                    return;
                }
                CampaignSystem.clearSelectedCampaignContact(ctx);
                CampaignSystem.selectCampaignLocationById(ctx, clickedLocation.id);
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    CampaignSystem.startTravelToSelectedLocation(ctx);
                }
                return;
            }
            if (clickedSupport != null) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    addPing(ctx, clickedSupport.x, clickedSupport.y, 2.2);
                    EventSystem.showBanner(ctx, "CONTACT PING: " + clickedSupport.label.toUpperCase(), 1.2);
                    return;
                }
                String intel = "";
                if (clickedSupport.subtitle != null && clickedSupport.subtitle.contains("|")) {
                    intel = clickedSupport.subtitle.substring(0, clickedSupport.subtitle.indexOf('|')).trim();
                }
                boolean hostile = isHostileCampaignSupportMarker(ctx, clickedSupport);
                CampaignSystem.selectCampaignContactTarget(ctx,
                        clickedSupport.label,
                        clickedSupport.subtitle,
                        intel,
                        clickedSupport.x,
                        clickedSupport.y,
                        hostile,
                        true);
                ctx.ui.waypointX = GameMath.clamp(clickedSupport.x, 0, ctx.WORLD_W);
                ctx.ui.waypointY = GameMath.clamp(clickedSupport.y, 0, ctx.WORLD_H);
                addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
                EventSystem.showBanner(ctx, "CONTACT SELECTED: " + clickedSupport.label.toUpperCase(), 1.2);
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    CampaignSystem.startTravelToSelectedLocation(ctx);
                }
                return;
            }
            if (SwingUtilities.isLeftMouseButton(e)) {
                CampaignSystem.clearSelectedCampaignContact(ctx);
                CampaignSystem.selectCampaignFreeTravelTarget(ctx, worldX, worldY);
                if (e.getClickCount() >= 2) {
                    CampaignSystem.startTravelToSelectedLocation(ctx);
                }
                return;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, worldX, worldY, 2.2);
                EventSystem.showBanner(ctx, "PING MARKED", 1.0);
            }
            return;
        }
        if (!CampaignSystem.isStrategicGalaxyMapMode(ctx)
                && TutorialSystem.handleStrategicMapClick(ctx, worldX, worldY, SwingUtilities.isRightMouseButton(e))) {
            return;
        }
        boolean strikeTargetingMode = false;
        if (e.isAltDown() && SwingUtilities.isLeftMouseButton(e)) {
            CampaignSystem.issueStrategicDivisionOrder(ctx, worldX, worldY);
            return;
        }
        CampaignSystem.CampaignSupportMarker clickedSupport =
                CampaignSystem.nearestStrategicTaskForceMarker(ctx, worldX, worldY, strikeTargetingMode ? 220.0 : 130.0);
        if (strikeTargetingMode && clickedSupport != null && isHostileCampaignSupportMarker(ctx, clickedSupport)) {
            if (e.isShiftDown() && e.isControlDown() && SwingUtilities.isLeftMouseButton(e)) {
                CampaignSystem.launchStrategicAtomicStrike(ctx, worldX, worldY);
                return;
            }
            if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e)) {
                CampaignSystem.launchStrategicTorpedoStrike(ctx, worldX, worldY);
                return;
            }
            if (e.isShiftDown() && SwingUtilities.isRightMouseButton(e)) {
                CampaignSystem.launchStrategicSortie(ctx, worldX, worldY);
                return;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedSupport.x, clickedSupport.y, 2.2);
                EventSystem.showBanner(ctx, "CONTACT PING: " + clickedSupport.label.toUpperCase(), 1.2);
                return;
            }
            setTacticalMapSelection(ctx,
                    UiState.TacticalMapSelectionKind.CONTACT,
                    clickedSupport.label,
                    clickedSupport.subtitle,
                    clickedSupport.type.name().replace('_', ' '),
                    clickedSupport.x,
                    clickedSupport.y,
                    isHostileCampaignSupportMarker(ctx, clickedSupport));
            CampaignSystem.selectCampaignContactTarget(ctx,
                    clickedSupport.label,
                    clickedSupport.subtitle,
                    CampaignSystem.usesMissionSubzones(ctx) ? "Tracked" : clickedSupport.type.name().replace('_', ' '),
                    clickedSupport.x,
                    clickedSupport.y,
                    isHostileCampaignSupportMarker(ctx, clickedSupport),
                    true);
            ctx.ui.waypointX = GameMath.clamp(clickedSupport.x, 0, ctx.WORLD_W);
            ctx.ui.waypointY = GameMath.clamp(clickedSupport.y, 0, ctx.WORLD_H);
            addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
            EventSystem.showBanner(ctx, "STRIKE TARGET LOCKED: " + clickedSupport.label.toUpperCase(), 1.3);
            return;
        }
        CampaignSystem.CampaignObjectiveMarker clickedMarker =
                CampaignSystem.nearestObjectiveMarker(ctx, worldX, worldY, strikeTargetingMode ? 110.0 : 280.0);
        if (clickedMarker != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedMarker.x, clickedMarker.y, 2.2);
                EventSystem.showBanner(ctx, "OBJECTIVE PING: " + clickedMarker.label.toUpperCase(), 1.2);
                return;
            }
            CampaignSystem.clearSelectedCampaignContact(ctx);
            ctx.ui.waypointX = GameMath.clamp(clickedMarker.x, 0, ctx.WORLD_W);
            ctx.ui.waypointY = GameMath.clamp(clickedMarker.y, 0, ctx.WORLD_H);
            setTacticalMapSelection(ctx,
                    UiState.TacticalMapSelectionKind.OBJECTIVE,
                    clickedMarker.label,
                    clickedMarker.subtitle,
                    clickedMarker.type.name().replace('_', ' '),
                    clickedMarker.x,
                    clickedMarker.y,
                    isHostileTacticalObjective(ctx, clickedMarker));
            if (isHostileTacticalObjective(ctx, clickedMarker)) {
                CampaignSystem.selectCampaignContactTarget(ctx,
                        clickedMarker.label,
                        clickedMarker.subtitle,
                        "Tracked",
                        clickedMarker.x,
                        clickedMarker.y,
                        true,
                        true);
            }
            addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
            String subtitle = (clickedMarker.subtitle == null || clickedMarker.subtitle.isBlank())
                    ? ""
                    : "  " + clickedMarker.subtitle.toUpperCase();
            EventSystem.showBanner(ctx,
                    "OBJECTIVE SET: " + clickedMarker.label.toUpperCase() + subtitle,
                    1.4);
            return;
        }

        clickedSupport =
                CampaignSystem.nearestStrategicTaskForceMarker(ctx, worldX, worldY, strikeTargetingMode ? 220.0 : 130.0);
        if (clickedSupport != null) {
            if (e.isShiftDown() && e.isControlDown() && SwingUtilities.isLeftMouseButton(e)) {
                CampaignSystem.launchStrategicAtomicStrike(ctx, worldX, worldY);
                return;
            }
            if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e)) {
                CampaignSystem.launchStrategicTorpedoStrike(ctx, worldX, worldY);
                return;
            }
            if (e.isShiftDown() && SwingUtilities.isRightMouseButton(e)) {
                CampaignSystem.launchStrategicSortie(ctx, worldX, worldY);
                return;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedSupport.x, clickedSupport.y, 2.2);
                EventSystem.showBanner(ctx, "CONTACT PING: " + clickedSupport.label.toUpperCase(), 1.2);
                return;
            }
            setTacticalMapSelection(ctx,
                    UiState.TacticalMapSelectionKind.CONTACT,
                    clickedSupport.label,
                    clickedSupport.subtitle,
                    clickedSupport.type.name().replace('_', ' '),
                    clickedSupport.x,
                    clickedSupport.y,
                    true);
            CampaignSystem.selectCampaignContactTarget(ctx,
                    clickedSupport.label,
                    clickedSupport.subtitle,
                    CampaignSystem.usesMissionSubzones(ctx) ? "Tracked" : clickedSupport.type.name().replace('_', ' '),
                    clickedSupport.x,
                    clickedSupport.y,
                    true,
                    true);
            ctx.ui.waypointX = GameMath.clamp(clickedSupport.x, 0, ctx.WORLD_W);
            ctx.ui.waypointY = GameMath.clamp(clickedSupport.y, 0, ctx.WORLD_H);
            addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
            String subtitle = (clickedSupport.subtitle == null || clickedSupport.subtitle.isBlank())
                    ? ""
                    : "  " + clickedSupport.subtitle.toUpperCase();
            EventSystem.showBanner(ctx,
                    "CONTACT TRACK SET: " + clickedSupport.label.toUpperCase() + subtitle,
                    1.4);
            return;
        }

        clickedSupport =
                CampaignSystem.nearestSupportMarker(ctx, worldX, worldY, 240.0);
        if (clickedSupport != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                addPing(ctx, clickedSupport.x, clickedSupport.y, 2.2);
                EventSystem.showBanner(ctx, "SUPPORT PING: " + clickedSupport.label.toUpperCase(), 1.2);
                return;
            }
            CampaignSystem.clearSelectedCampaignContact(ctx);
            setTacticalMapSelection(ctx,
                    UiState.TacticalMapSelectionKind.CONTACT,
                    clickedSupport.label,
                    clickedSupport.subtitle,
                    clickedSupport.type.name().replace('_', ' '),
                    clickedSupport.x,
                    clickedSupport.y,
                    false);
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
            CampaignSystem.clearSelectedCampaignContact(ctx);
            setTacticalMapSelection(ctx,
                    UiState.TacticalMapSelectionKind.LANDMARK,
                    clickedLandmark.label,
                    clickedLandmark.subtitle,
                    clickedLandmark.type.name().replace('_', ' '),
                    clickedLandmark.x,
                    clickedLandmark.y,
                    false);
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
                BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.sectorAt(ctx, worldX, worldY);
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
                double targetX = worldX;
                double targetY = worldY;
                String contact = CampaignSystem.localRangeBearingReadout(ctx, targetX, targetY);
                if (SwingUtilities.isRightMouseButton(e)) {
                    addPing(ctx, targetX, targetY, 2.2);
                    EventSystem.showBanner(ctx, "LOCAL PING: " + contact, 1.2);
                    return;
                }
                ctx.ui.waypointX = GameMath.clamp(targetX, 0, ctx.WORLD_W);
                ctx.ui.waypointY = GameMath.clamp(targetY, 0, ctx.WORLD_H);
                addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
                EventSystem.showBanner(ctx, "COURSE SET: " + contact, 1.2);
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
        CampaignSystem.clearSelectedCampaignContact(ctx);
        setTacticalMapSelection(ctx,
                UiState.TacticalMapSelectionKind.SPACE,
                "Free Course",
                "Open battlespace",
                "Waypoint only",
                worldX,
                worldY,
                false);
        addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.2);
    }

    static double campaignSiteHitRadiusWorld(GameContext ctx, Rectangle mapRect) {
        if (ctx == null || mapRect == null || mapRect.width <= 0 || mapRect.height <= 0) return 0.0;
        double worldPerPixelX = strategicMapViewWidth(ctx) / mapRect.width;
        double worldPerPixelY = strategicMapViewHeight(ctx) / mapRect.height;
        double markerRadius = Math.max(worldPerPixelX, worldPerPixelY) * 22.0;
        return Math.max(42.0, Math.min(190.0, markerRadius));
    }

    static CampaignSystem.CampaignLocation campaignLocationAtMapClick(GameContext ctx,
                                                                       double worldX,
                                                                       double worldY,
                                                                       Rectangle mapRect) {
        double radius = campaignSiteHitRadiusWorld(ctx, mapRect);
        if (radius <= 0.0) return null;
        CampaignSystem.CampaignLocation nearest = CampaignSystem.nearestCampaignLocation(ctx, worldX, worldY, radius);
        CampaignSystem.CampaignLocation localSite = nearestLocalSiteAtMapClick(ctx, worldX, worldY, radius);
        if (localSite == null) return nearest;
        if (nearest == null) return localSite;
        double siteDist = Math.hypot(worldX - localSite.x, worldY - localSite.y);
        double nearestDist = Math.hypot(worldX - nearest.x, worldY - nearest.y);
        double localSiteRadius = Math.max(radius, 120.0);
        if (siteDist <= localSiteRadius * 0.72 && nearestDist > radius * 0.22) return localSite;
        return siteDist <= nearestDist + radius * 0.45 ? localSite : nearest;
    }

    private static CampaignSystem.CampaignLocation nearestLocalSiteAtMapClick(GameContext ctx,
                                                                              double worldX,
                                                                              double worldY,
                                                                              double radius) {
        CampaignSystem.CampaignLocation best = null;
        double localSiteRadius = Math.max(1.0, Math.max(radius, 120.0));
        double bestD2 = localSiteRadius * localSiteRadius;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location == null || !location.discovered || location.destroyed) continue;
            if (!isLocalCampaignSite(location)) continue;
            double d2 = GameMath.dist2(worldX, worldY, location.x, location.y);
            if (d2 > bestD2) continue;
            best = location;
            bestD2 = d2;
        }
        return best;
    }

    private static boolean isLocalCampaignSite(CampaignSystem.CampaignLocation location) {
        if (location == null) return false;
        return location.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE
                || location.type == CampaignSystem.CampaignLocationType.SALVAGE_FIELD
                || location.type == CampaignSystem.CampaignLocationType.HIDDEN_CACHE
                || location.type == CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL
                || location.facilityType == CampaignSystem.CampaignFacilityType.MINING_OPERATION
                || location.facilityType == CampaignSystem.CampaignFacilityType.DERELICT_BATTLEFIELD;
    }

    private static boolean shouldPreferCampaignSupportClick(CampaignSystem.CampaignLocation location,
                                                            CampaignSystem.CampaignSupportMarker support,
                                                            double worldX,
                                                            double worldY,
                                                            double siteHitRadius) {
        if (support == null) return false;
        if (location == null) return true;
        double supportDist2 = GameMath.dist2(worldX, worldY, support.x, support.y);
        double locationDist2 = GameMath.dist2(worldX, worldY, location.x, location.y);
        double directSupportRadius = isFleetLikeCampaignSupportMarker(support)
                ? Math.max(34.0, Math.min(96.0, support.radius * 0.42))
                : Math.max(26.0, Math.min(72.0, support.radius * 0.38));
        if (supportDist2 <= directSupportRadius * directSupportRadius
                && supportDist2 < Math.max(1.0, locationDist2 * 0.42)) {
            return true;
        }
        double protectedSiteRadius = Math.max(42.0, Math.min(180.0, siteHitRadius));
        if (locationDist2 <= protectedSiteRadius * protectedSiteRadius) return false;
        if (isFleetLikeCampaignSupportMarker(support)) {
            return supportDist2 <= directSupportRadius * directSupportRadius
                    && supportDist2 < locationDist2 * 0.55;
        }
        return supportDist2 <= locationDist2;
    }

    private static boolean isFleetLikeCampaignSupportMarker(CampaignSystem.CampaignSupportMarker support) {
        if (support == null || support.type == null) return false;
        return switch (support.type) {
            case FORCE_PATROL, FORCE_SEARCH, FORCE_STRIKE, FORCE_BASE_DEFENSE, FORCE_CONVOY, FORCE_MINING -> true;
            default -> false;
        };
    }

    private static void setTacticalMapSelection(GameContext ctx,
                                                UiState.TacticalMapSelectionKind kind,
                                                String label,
                                                String subtitle,
                                                String detail,
                                                double x,
                                                double y,
                                                boolean hostile) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.tacticalMapSelectionKind = (kind == null) ? UiState.TacticalMapSelectionKind.MISSION : kind;
        ctx.ui.tacticalMapSelectionLabel = (label == null) ? "" : label.trim();
        ctx.ui.tacticalMapSelectionSubtitle = (subtitle == null) ? "" : subtitle.trim();
        ctx.ui.tacticalMapSelectionDetail = (detail == null) ? "" : detail.trim();
        ctx.ui.tacticalMapSelectionX = x;
        ctx.ui.tacticalMapSelectionY = y;
        ctx.ui.tacticalMapSelectionHostile = hostile;
    }

    private static boolean isHostileTacticalObjective(GameContext ctx, CampaignSystem.CampaignObjectiveMarker marker) {
        if (marker == null) return false;
        if (marker.faction != null && ctx != null && ctx.player != null && ctx.player.faction != null) {
            return !marker.faction.isFriendlyTo(ctx.player.faction);
        }
        return marker.type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET
                || marker.type == CampaignSystem.ObjectiveMarkerType.BOSS_TARGET;
    }

    private static boolean isHostileCampaignSupportMarker(GameContext ctx, CampaignSystem.CampaignSupportMarker marker) {
        if (marker == null) return false;
        if (marker.type == CampaignSystem.SupportMarkerType.HAZARD) return true;
        if (marker.faction != null && ctx != null && ctx.player != null && ctx.player.faction != null) {
            return !marker.faction.isFriendlyTo(ctx.player.faction);
        }
        return marker.type == CampaignSystem.SupportMarkerType.FORCE_PATROL
                || marker.type == CampaignSystem.SupportMarkerType.FORCE_SEARCH
                || marker.type == CampaignSystem.SupportMarkerType.FORCE_STRIKE
                || marker.type == CampaignSystem.SupportMarkerType.FORCE_BASE_DEFENSE;
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
        focusStrategicMapAt(ctx, x, y);
    }

    public static void stepStrategicMapZoom(GameContext ctx, int dir, int mouseX, int mouseY, int viewportW, int viewportH) {
        if (ctx == null || ctx.ui == null || !ctx.ui.mapOpen || dir == 0) return;
        Rectangle rect = Renderer.getStrategicMapInnerRect(
                viewportW, viewportH, CampaignSystem.isStrategicGalaxyMapMode(ctx));
        double nx = 0.5;
        double ny = 0.5;
        if (rect.width > 0 && rect.height > 0 && rect.contains(mouseX, mouseY)) {
            nx = MathUtil.clamp((mouseX - rect.x) / (double) rect.width, 0.0, 1.0);
            ny = MathUtil.clamp((mouseY - rect.y) / (double) rect.height, 0.0, 1.0);
        }
        double anchoredWorldX = strategicMapWorldXAt(ctx, nx);
        double anchoredWorldY = strategicMapWorldYAt(ctx, ny);
        double currentZoom = strategicMapZoom(ctx);
        double factor = (dir > 0) ? STRATEGIC_MAP_ZOOM_STEP : (1.0 / STRATEGIC_MAP_ZOOM_STEP);
        double nextZoom = MathUtil.clamp(currentZoom * factor, strategicMapMinZoom(ctx), STRATEGIC_MAP_MAX_ZOOM);
        applyStrategicMapZoom(ctx, nextZoom);
        setStrategicMapFocusKeepingAnchor(ctx, anchoredWorldX, anchoredWorldY, nx, ny);
    }

    public static void resetStrategicMapZoom(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        if (CampaignSystem.isStrategicOvermapMode(ctx)) {
            applyStrategicMapZoom(ctx, 2.2);
            focusStrategicMapAt(ctx, campaignMapFocusAnchorX(ctx), campaignMapFocusAnchorY(ctx));
            return;
        }
        if (focusTacticalMapOnCurrentMission(ctx)) return;
        applyStrategicMapZoom(ctx, 1.0);
    }

    private static void applyStrategicMapZoom(GameContext ctx, double zoom) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.strategicMapZoom = MathUtil.clamp(zoom, strategicMapMinZoom(ctx), STRATEGIC_MAP_MAX_ZOOM);
        focusStrategicMapAt(ctx, strategicMapFocusX(ctx), strategicMapFocusY(ctx));
    }

    static double strategicMapZoom(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return 1.0;
        return MathUtil.clamp(ctx.ui.strategicMapZoom, strategicMapMinZoom(ctx), STRATEGIC_MAP_MAX_ZOOM);
    }

    private static double strategicMapMinZoom(GameContext ctx) {
        if (CampaignSystem.usesMissionSubzones(ctx) && !CampaignSystem.isStrategicOvermapMode(ctx)) {
            return MISSION_MAP_MIN_ZOOM;
        }
        return CampaignSystem.isStrategicOvermapMode(ctx) ? STRATEGIC_GALAXY_MAP_MIN_ZOOM : STRATEGIC_MAP_MIN_ZOOM;
    }

    private static double strategicMapMaxX(GameContext ctx) {
        if (ctx == null) return 0.0;
        return CampaignSystem.isStrategicOvermapMode(ctx)
                ? CampaignSystem.strategicGalaxyMapWidth(ctx)
                : ctx.WORLD_W;
    }

    private static double strategicMapMaxY(GameContext ctx) {
        if (ctx == null) return 0.0;
        return CampaignSystem.isStrategicOvermapMode(ctx)
                ? CampaignSystem.strategicGalaxyMapHeight(ctx)
                : ctx.WORLD_H;
    }

    static double strategicMapViewWidth(GameContext ctx) {
        if (ctx == null) return 0.0;
        if (CampaignSystem.usesMissionSubzones(ctx) && !CampaignSystem.isStrategicOvermapMode(ctx)) {
            double baseZoom = Math.max(1.0, STRATEGIC_MAP_MIN_ZOOM);
            double zoom = Math.max(MISSION_MAP_MIN_ZOOM, strategicMapZoom(ctx));
            return Math.max(1.0, CampaignSystem.missionSubzoneWidth(ctx) * 1.18 * baseZoom / zoom);
        }
        return Math.max(1.0, strategicMapMaxX(ctx) / strategicMapZoom(ctx));
    }

    static double strategicMapViewHeight(GameContext ctx) {
        if (ctx == null) return 0.0;
        if (CampaignSystem.usesMissionSubzones(ctx) && !CampaignSystem.isStrategicOvermapMode(ctx)) {
            double baseZoom = Math.max(1.0, STRATEGIC_MAP_MIN_ZOOM);
            double zoom = Math.max(MISSION_MAP_MIN_ZOOM, strategicMapZoom(ctx));
            return Math.max(1.0, CampaignSystem.missionSubzoneHeight(ctx) * 1.12 * baseZoom / zoom);
        }
        return Math.max(1.0, strategicMapMaxY(ctx) / strategicMapZoom(ctx));
    }

    static double strategicMapFocusX(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return 0.0;
        double viewWidth = strategicMapViewWidth(ctx);
        double half = viewWidth * 0.5;
        double fallback = CampaignSystem.isStrategicOvermapMode(ctx)
                ? campaignMapFocusAnchorX(ctx)
                : ((CampaignSystem.usesMissionSubzones(ctx) && ctx.player != null) ? ctx.player.x : half);
        double focus = Double.isFinite(ctx.ui.strategicMapFocusX) ? ctx.ui.strategicMapFocusX : fallback;
        double maxX = strategicMapMaxX(ctx);
        return GameMath.clamp(focus, half, Math.max(half, maxX - half));
    }

    static double strategicMapFocusY(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return 0.0;
        double viewHeight = strategicMapViewHeight(ctx);
        double half = viewHeight * 0.5;
        double fallback = CampaignSystem.isStrategicOvermapMode(ctx)
                ? campaignMapFocusAnchorY(ctx)
                : ((CampaignSystem.usesMissionSubzones(ctx) && ctx.player != null) ? ctx.player.y : half);
        double focus = Double.isFinite(ctx.ui.strategicMapFocusY) ? ctx.ui.strategicMapFocusY : fallback;
        double maxY = strategicMapMaxY(ctx);
        return GameMath.clamp(focus, half, Math.max(half, maxY - half));
    }

    static double strategicMapWorldMinX(GameContext ctx) {
        return strategicMapFocusX(ctx) - strategicMapViewWidth(ctx) * 0.5;
    }

    static double strategicMapWorldMinY(GameContext ctx) {
        return strategicMapFocusY(ctx) - strategicMapViewHeight(ctx) * 0.5;
    }

    static double strategicMapWorldXAt(GameContext ctx, double normalizedX) {
        return GameMath.clamp(strategicMapWorldMinX(ctx) + strategicMapViewWidth(ctx) * MathUtil.clamp(normalizedX, 0.0, 1.0),
                0.0, strategicMapMaxX(ctx));
    }

    static double strategicMapWorldYAt(GameContext ctx, double normalizedY) {
        return GameMath.clamp(strategicMapWorldMinY(ctx) + strategicMapViewHeight(ctx) * MathUtil.clamp(normalizedY, 0.0, 1.0),
                0.0, strategicMapMaxY(ctx));
    }

    private static void focusStrategicMapAt(GameContext ctx, double x, double y) {
        if (ctx == null || ctx.ui == null) return;
        double halfW = strategicMapViewWidth(ctx) * 0.5;
        double halfH = strategicMapViewHeight(ctx) * 0.5;
        double maxX = strategicMapMaxX(ctx);
        double maxY = strategicMapMaxY(ctx);
        ctx.ui.strategicMapFocusX = GameMath.clamp(x, halfW, Math.max(halfW, maxX - halfW));
        ctx.ui.strategicMapFocusY = GameMath.clamp(y, halfH, Math.max(halfH, maxY - halfH));
    }

    private static double campaignMapFocusAnchorX(GameContext ctx) {
        double playerX = CampaignSystem.playerGalaxyX(ctx);
        CampaignSystem.CampaignTravelState travel = CampaignSystem.campaignTravelState(ctx);
        if (travel != null && travel.traveling && Double.isFinite(travel.targetX) && Double.isFinite(playerX)) {
            return (playerX * 0.68) + (travel.targetX * 0.32);
        }
        return Double.isFinite(playerX) ? playerX : (ctx == null ? 0.0 : ctx.WORLD_W * 0.5);
    }

    private static double campaignMapFocusAnchorY(GameContext ctx) {
        double playerY = CampaignSystem.playerGalaxyY(ctx);
        CampaignSystem.CampaignTravelState travel = CampaignSystem.campaignTravelState(ctx);
        if (travel != null && travel.traveling && Double.isFinite(travel.targetY) && Double.isFinite(playerY)) {
            return (playerY * 0.68) + (travel.targetY * 0.32);
        }
        return Double.isFinite(playerY) ? playerY : (ctx == null ? 0.0 : ctx.WORLD_H * 0.5);
    }

    public static boolean focusTacticalMapOnCurrentMission(GameContext ctx) {
        if (ctx == null || ctx.ui == null || !CampaignSystem.usesMissionSubzones(ctx)) {
            return false;
        }
        int subzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
        if (subzone < 0 && ctx.player != null) {
            subzone = CampaignSystem.missionSubzoneForPoint(ctx, ctx.campaign == null ? 1 : ctx.campaign.sector,
                    ctx.player.x, ctx.player.y);
        }
        if (subzone < 0) return false;
        double zoomForWidth = ctx.WORLD_W / Math.max(1.0, CampaignSystem.missionSubzoneWidth(ctx) * 1.75);
        double zoomForHeight = ctx.WORLD_H / Math.max(1.0, CampaignSystem.missionSubzoneHeight(ctx) * 1.55);
        ctx.ui.strategicMapZoom = MathUtil.clamp(Math.min(zoomForWidth, zoomForHeight), MISSION_MAP_MIN_ZOOM, 9.0);
        double focusX = CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign == null ? 1 : ctx.campaign.sector, subzone);
        double focusY = CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign == null ? 1 : ctx.campaign.sector, subzone);
        if (ctx.player != null && Double.isFinite(ctx.player.x) && Double.isFinite(ctx.player.y)) {
            focusX = ctx.player.x;
            focusY = ctx.player.y;
        }
        focusStrategicMapAt(ctx, focusX, focusY);
        return true;
    }

    private static void setStrategicMapFocusKeepingAnchor(GameContext ctx, double worldX, double worldY,
                                                          double normalizedX, double normalizedY) {
        if (ctx == null || ctx.ui == null) return;
        double focusX = worldX - (normalizedX - 0.5) * strategicMapViewWidth(ctx);
        double focusY = worldY - (normalizedY - 0.5) * strategicMapViewHeight(ctx);
        focusStrategicMapAt(ctx, focusX, focusY);
    }

    public static void updateStrategicMapCameraPan(GameContext ctx, double dt) {
        if (ctx == null || ctx.ui == null || !ctx.ui.mapOpen || dt <= 0.0) return;
        double panX = 0.0;
        double panY = 0.0;
        if (ctx.cameraPanLeft) panX -= 1.0;
        if (ctx.cameraPanRight) panX += 1.0;
        if (ctx.cameraPanUp) panY -= 1.0;
        if (ctx.cameraPanDown) panY += 1.0;
        if (Math.abs(panX) <= 1e-9 && Math.abs(panY) <= 1e-9) return;
        double len = Math.hypot(panX, panY);
        if (len > 1e-9) {
            panX /= len;
            panY /= len;
        }
        double panSpeed = Math.max(540.0, Math.max(strategicMapViewWidth(ctx), strategicMapViewHeight(ctx)) * 0.34);
        focusStrategicMapAt(ctx,
                strategicMapFocusX(ctx) + panX * panSpeed * dt,
                strategicMapFocusY(ctx) + panY * panSpeed * dt);
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

    private static boolean handleCampaignCommandAction(GameContext ctx, String actionId) {
        return CampaignSystem.executeCampaignAction(ctx, actionId);
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
                case PATROL -> tryBuyCampaignHull(ctx, ShipRole.PATROL, 120, 0);
                case PICKET -> tryBuyCampaignHull(ctx, ShipRole.PICKET, 180, 0);
                case FRIGATE -> tryBuyCampaignHull(ctx, ShipRole.FRIGATE, 220, 0);
                case MINER -> tryBuyCampaignHull(ctx, ShipRole.MINER, 180, 0);
                case ARTILLERY_SHIP -> tryBuyCampaignHull(ctx, ShipRole.ARTILLERY_SHIP, 320, 0);
                case MISSILE_BOAT -> tryBuyCampaignHull(ctx, ShipRole.MISSILE_BOAT, 340, 0);
                case CIWS_CORVETTE -> tryBuyCampaignHull(ctx, ShipRole.CIWS_CORVETTE, 300, 0);
                case LIGHT_CRUISER -> tryBuyCampaignHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
                case MEDIUM_CRUISER -> tryBuyCampaignHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
                case CRUISER -> tryBuyCampaignHull(ctx, ShipRole.CRUISER, 1100, 1);
                case HAULER -> tryBuyCampaignHull(ctx, ShipRole.HAULER, 300, 1);
                case TRANSPORT -> tryBuyCampaignHull(ctx, ShipRole.TRANSPORT, 460, 1);
                case BATTLECRUISER -> tryBuyCampaignHull(ctx, ShipRole.BATTLECRUISER, 1700, 2);
                case BATTLESHIP -> tryBuyCampaignHull(ctx, ShipRole.BATTLESHIP, 2300, 2);
                case STEALTH_SHIP -> tryBuyCampaignHull(ctx, ShipRole.STEALTH_SHIP, 1300, 2);
                case DREADNOUGHT -> tryBuyCampaignHull(ctx, ShipRole.DREADNOUGHT, 3000, 3);
                case CARRIER -> tryBuyCampaignHull(ctx, ShipRole.CARRIER, 2600, 3);
                case DRONE_CARRIER -> tryBuyCampaignHull(ctx, ShipRole.DRONE_CARRIER, 2700, 3);
                case SUPERSHIP -> tryBuyCampaignHull(ctx, ShipRole.SUPERSHIP, 4200, 3);
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
            case PATROL -> trySwapHull(ctx, ShipRole.PATROL, 120, 0);
            case PICKET -> trySwapHull(ctx, ShipRole.PICKET, 180, 0);
            case FRIGATE -> trySwapHull(ctx, ShipRole.FRIGATE, 220, 0);
            case MINER -> trySwapHull(ctx, ShipRole.MINER, 180, 0);
            case ARTILLERY_SHIP -> trySwapHull(ctx, ShipRole.ARTILLERY_SHIP, 320, 0);
            case MISSILE_BOAT -> trySwapHull(ctx, ShipRole.MISSILE_BOAT, 340, 0);
            case CIWS_CORVETTE -> trySwapHull(ctx, ShipRole.CIWS_CORVETTE, 300, 0);
            case LIGHT_CRUISER -> trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
            case MEDIUM_CRUISER -> trySwapHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
            case CRUISER -> trySwapHull(ctx, ShipRole.CRUISER, 1100, 1);
            case HAULER -> trySwapHull(ctx, ShipRole.HAULER, 300, 1);
            case TRANSPORT -> trySwapHull(ctx, ShipRole.TRANSPORT, 460, 1);
            case BATTLECRUISER -> trySwapHull(ctx, ShipRole.BATTLECRUISER, 1700, 2);
            case BATTLESHIP -> trySwapHull(ctx, ShipRole.BATTLESHIP, 2300, 2);
            case STEALTH_SHIP -> trySwapHull(ctx, ShipRole.STEALTH_SHIP, 1300, 2);
            case DREADNOUGHT -> trySwapHull(ctx, ShipRole.DREADNOUGHT, 3000, 3);
            case CARRIER -> trySwapHull(ctx, ShipRole.CARRIER, 2600, 3);
            case DRONE_CARRIER -> trySwapHull(ctx, ShipRole.DRONE_CARRIER, 2700, 3);
            case SUPERSHIP -> trySwapHull(ctx, ShipRole.SUPERSHIP, 4200, 3);
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
            case MOTHERSHIP -> trySwapHull(ctx, ShipRole.MOTHERSHIP, 12000, 3);
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
        String count = role == ShipRole.PICKET ? "x5 BERTHS" : "x10";
        EventSystem.showBanner(ctx, "SQUAD LOADOUT: " + role.name() + " " + count, 1.0);
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
        int cCost = CampaignSystem.baseUpgradeCreditCost(ctx, base, which, nextLv);
        int oCost = CampaignSystem.baseUpgradeOreCost(ctx, base, which, nextLv);
        if (cCost == Integer.MAX_VALUE || oCost == Integer.MAX_VALUE) return;

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
        CampaignSystem.syncPersistentFleetEntrySnapshotForShip(ctx, base);
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
        ctx.command.playerPowerManualOverride = false;
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
        ctx.command.playerPowerManualOverride = false;
        ctx.command.scienceAutomation = true;
    }

    public static void cycleAlliedFleetFormation(GameContext ctx) {
        if (ctx == null) return;
        GameContext.FleetFormation[] values = GameContext.FleetFormation.values();
        int next = ctx.command.alliedFleetFormation.ordinal() + 1;
        if (next >= values.length) next = 0;
        setAlliedFleetFormation(ctx, values[next]);
    }

    public static void setAlliedFleetFormation(GameContext ctx, GameContext.FleetFormation formation) {
        if (ctx == null || ctx.command == null || formation == null) return;
        ctx.command.alliedFleetFormation = formation;
        AudioSystem.onCommandShipFormationOrder(ctx, ctx.player, ctx.command.alliedFleetFormation);
        EventSystem.showBanner(ctx, "FLEET FORMATION: " + ctx.command.alliedFleetFormation.name(), 1.0);
    }

    public static void scienceLockNearest(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        double range = TargetingSystem.PLAYER_TARGET_LOCK_RANGE * Math.max(1.0, ctx.player.sensorRangeMultiplier());
        Ship target = TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, ctx.player.x, ctx.player.y, range);
        ctx.lockedTarget = target;
    }

    public static void scienceClearLock(GameContext ctx) {
        if (ctx == null) return;
        ctx.lockedTarget = null;
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
