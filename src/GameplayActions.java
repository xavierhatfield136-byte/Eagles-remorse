public final class GameplayActions {
    private GameplayActions() {}

    public static void handleEscape(GameContext ctx, Runnable exitToMenu) {
        if (ctx == null) return;
        if (ctx.state == GameState.GAME_OVER || ctx.gameOver) {
            if (exitToMenu != null) exitToMenu.run();
            return;
        }
        if (ctx.ui.hasBlockingOverlay()) {
            UISystem.closeAllOverlays(ctx);
            return;
        }
        ctx.state = (ctx.state == GameState.PAUSED) ? GameState.RUNNING : GameState.PAUSED;
    }

    public static boolean canIssueCombatAction(GameContext ctx) {
        if (ctx == null || ctx.player == null) return false;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return false;
        if (ctx.state != GameState.RUNNING) return false;
        if (CampaignSystem.isPlayerControlLocked(ctx)) return false;
        return !ctx.ui.hasBlockingOverlay();
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx == null) return;
        UISystem.toggleShop(ctx);
    }

    public static void toggleMap(GameContext ctx) {
        if (ctx == null) return;
        UISystem.toggleMap(ctx);
    }

    public static void toggleBaseMenu(GameContext ctx) {
        if (ctx == null) return;
        UISystem.toggleBaseMenu(ctx);
    }

    public static void togglePowerManagement(GameContext ctx) {
        if (ctx == null) return;
        UISystem.togglePowerManagement(ctx);
    }

    public static void toggleCrewStations(GameContext ctx) {
        if (ctx == null) return;
        UISystem.toggleCrewStations(ctx);
    }

    public static void toggleFlightDeck(GameContext ctx) {
        if (ctx == null) return;
        UISystem.toggleFlightDeck(ctx);
    }

    public static void lockUnderMouse(GameContext ctx, PlayerControl controls) {
        if (ctx == null || controls == null) return;
        ctx.command.scienceAutomation = false;
        TargetingSystem.lockClosestToMouse(ctx, controls);
    }

    public static void cycleLockedTarget(GameContext ctx, int dir) {
        if (ctx == null) return;
        ctx.command.scienceAutomation = false;
        TargetingSystem.cycleLockedTarget(ctx, dir);
    }

    public static void hailCurrentContact(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        CommSystem.tryHailCurrentContact(ctx);
    }

    public static void cycleCommIntent(GameContext ctx, int dir) {
        if (!canIssueCombatAction(ctx)) return;
        CommSystem.cycleIntent(ctx, dir);
    }

    public static void pingAtCursor(GameContext ctx, PlayerControl controls) {
        if (ctx == null || controls == null) return;
        UISystem.pingAtCursor(ctx, controls);
    }

    public static void setWaypointAtCursor(GameContext ctx, PlayerControl controls) {
        if (ctx == null || controls == null) return;
        UISystem.setWaypointAtCursor(ctx, controls);
    }

    public static void toggleTurretAutoLock(GameContext ctx) {
        if (ctx == null) return;
        ctx.autoLockTurrets = !ctx.autoLockTurrets;
    }

    public static void cycleHudDetail(GameContext ctx) {
        if (ctx == null) return;
        GameContext.HudDetail current = (ctx.ui.hudDetail == null) ? GameContext.HudDetail.COMPACT : ctx.ui.hudDetail;
        ctx.ui.hudDetail = switch (current) {
            case FULL -> GameContext.HudDetail.COMPACT;
            case COMPACT -> GameContext.HudDetail.MINIMAL;
            case MINIMAL -> GameContext.HudDetail.FULL;
        };
        EventSystem.showBanner(ctx, "HUD: " + ctx.ui.hudDetail.name(), 0.8);
    }

    public static void toggleTacticalView(GameContext ctx) {
        if (ctx == null) return;
        UISystem.toggleTacticalView(ctx);
    }

    public static void cycleXrayFilter(GameContext ctx, int dir) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.cycleXrayFilterMode(ctx, dir);
    }

    public static void clearXrayFocus(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.clearXrayRoomFocus(ctx);
    }

    public static void tryShieldOvercharge(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        ctx.player.tryShieldOvercharge();
    }

    public static void trySuperweapon(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        if (!ctx.player.hasSuperweapon) return;

        Projectile shot = ctx.player.tryFireSuperweaponAt(ctx.cursorWorldX, ctx.cursorWorldY, GameContext.DT);
        if (shot != null) {
            ctx.projectiles.add(shot);
            EventSystem.showBanner(ctx, "SUPERWEAPON FIRED", 1.0);
            ScreenShake.kick(8.0);
        } else if (ctx.player.isSuperweaponCharging()) {
            EventSystem.showBanner(ctx, "SUPERWEAPON CHARGING", 0.8);
        }
    }

    public static void tryCarrierLaunch(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.tryCarrierLaunch(ctx);
    }

    public static void tryCarrierRecall(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.tryCarrierRecall(ctx);
    }

    public static void tryCarrierToggleMode(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.tryCarrierToggleMode(ctx);
    }

    public static void tryCarrierToggleAutoLaunch(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.tryCarrierToggleAutoLaunch(ctx);
    }

    public static void cyclePowerPreset(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Ship.PowerPreset preset = ctx.player.cyclePowerPreset();
        ctx.command.engineeringAutomation = false;
        EventSystem.showBanner(ctx, "POWER: " + preset.name(), 0.8);
    }

    public static void cycleCrewOrder(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Ship.CrewOrder order = ctx.player.cycleCrewOrder();
        ctx.command.engineeringAutomation = false;
        EventSystem.showBanner(ctx, "CREW ORDER: " + order.name(), 0.8);
    }

    public static void cycleShieldFacingMode(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Ship.ShieldFacingMode mode = ctx.player.cycleShieldFacingMode();
        EventSystem.showBanner(ctx, "SHIELD MODE: " + mode.name(), 0.8);
    }

    public static void toggleEmergencyThrust(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        UISystem.toggleEmergencyThrustMode(ctx);
    }

    public static void tryTeleportToBase(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Player player = ctx.player;
        if (player == null) return;
        if (!player.canUseBattlefieldWarp()) {
            EventSystem.showBanner(ctx, "WARP UNAVAILABLE", 1.2);
            return;
        }
        BattlefieldSectorSystem.ensureLoadedSector(ctx);
        Ship base = TeamSystem.getBaseForTeam(ctx, player.faction);
        boolean hasWaypoint = Double.isFinite(ctx.ui.waypointX) && Double.isFinite(ctx.ui.waypointY);
        double targetX;
        double targetY;
        String destinationLabel;
        if (CampaignSystem.usesMissionSubzones(ctx)) {
            int loadedSubzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
            if (loadedSubzone < 0) loadedSubzone = CampaignSystem.syncLoadedMissionSubzoneFromPlayer(ctx);
            int targetSubzone = -1;
            if (hasWaypoint) {
                targetSubzone = CampaignSystem.campaignMapSubzoneAtPoint(ctx, ctx.ui.waypointX, ctx.ui.waypointY);
            } else if (base != null && base.alive && !base.dying && base.hp > 0) {
                targetSubzone = CampaignSystem.campaignMapSubzoneAtPoint(ctx, base.x, base.y);
            }
            if (targetSubzone >= 0) {
                int hopSubzone = CampaignSystem.nextCampaignWarpHop(loadedSubzone, targetSubzone);
                double[] arrival = CampaignSystem.campaignWarpArrivalPoint(ctx, hopSubzone);
                if (arrival == null) {
                    EventSystem.showBanner(ctx, "WARP ROUTE UNAVAILABLE", 1.4);
                    return;
                }
                targetX = arrival[0];
                targetY = arrival[1];
                destinationLabel = CampaignSystem.missionSubzoneLabel(hopSubzone);
            } else if (hasWaypoint) {
                EventSystem.showBanner(ctx, "WARP UNAVAILABLE: INVALID SECTOR", 1.4);
                return;
            } else if (base != null && base.alive && !base.dying && base.hp > 0) {
                targetX = base.x;
                targetY = base.y;
                destinationLabel = "BASE";
            } else {
                EventSystem.showBanner(ctx, "WARP UNAVAILABLE: SET WAYPOINT OR FIND BASE", 1.4);
                return;
            }
        } else {
        BattlefieldSectorSystem.SectorDefinition loadedSector = BattlefieldSectorSystem.loadedSector(ctx);
        BattlefieldSectorSystem.SectorDefinition selectedSector = BattlefieldSectorSystem.selectedSector(ctx);
        if (BattlefieldSectorSystem.isEnabled(ctx)
                && loadedSector != null
                && selectedSector != null
                && !selectedSector.id.equalsIgnoreCase(loadedSector.id)) {
            BattlefieldSectorSystem.SectorDefinition hop =
                    BattlefieldSectorSystem.nextWarpHop(ctx, loadedSector, selectedSector);
            double[] arrival = BattlefieldSectorSystem.warpArrivalPoint(
                    ctx, loadedSector, hop, ctx.ui.tacticalSectorScalePreset);
            if (hop == null || arrival == null) {
                EventSystem.showBanner(ctx, "WARP ROUTE UNAVAILABLE", 1.4);
                return;
            }
            targetX = arrival[0];
            targetY = arrival[1];
            destinationLabel = hop.label;
        } else if (hasWaypoint) {
            targetX = ctx.ui.waypointX;
            targetY = ctx.ui.waypointY;
            destinationLabel = "WAYPOINT";
        } else if (base != null && base.alive && !base.dying && base.hp > 0) {
            targetX = base.x;
            targetY = base.y;
            destinationLabel = "BASE";
        } else {
            EventSystem.showBanner(ctx, "WARP UNAVAILABLE: SET WAYPOINT OR FIND BASE", 1.4);
            return;
        }
        }

        if (player.isWarpCharging()) {
            player.cancelBattlefieldWarp();
            ctx.command.playerTeleportCharging = false;
            ctx.command.playerTeleportChargeRemaining = 0.0;
            EventSystem.showBanner(ctx, "BATTLEFIELD WARP CANCELLED", 1.0);
            return;
        }

        if (!player.beginBattlefieldWarp(targetX, targetY, 10.0)) {
            EventSystem.showBanner(ctx, "WARP UNAVAILABLE", 1.2);
            return;
        }
        if (CampaignSystem.usesMissionSubzones(ctx)) {
            int loadedSubzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
            if (loadedSubzone < 0) loadedSubzone = CampaignSystem.syncLoadedMissionSubzoneFromPlayer(ctx);
            player.campaignWarpSourceSubzone = loadedSubzone;
        }
        ctx.command.playerTeleportCharging = true;
        ctx.command.playerTeleportChargeRemaining = player.warpChargeRemaining();
        EventSystem.showBanner(ctx,
                "BATTLEFIELD WARP TO " + destinationLabel + "  "
                        + ctx.ui.tacticalSectorScalePreset.label().toUpperCase()
                        + " (10.0S)",
                1.2);
    }

    public static void rotateShieldFacing(GameContext ctx, int dir) {
        if (!canIssueCombatAction(ctx)) return;
        int step = (dir < 0) ? -1 : 1;
        ctx.player.shieldFacingMode = Ship.ShieldFacingMode.MANUAL;
        ctx.player.rotateShieldFacing(Math.toRadians(12.0 * step));
    }

    public static boolean tryHandleShopHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.ui.shopOpen) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> UISystem.selectShopHullCategory(ctx, ShopHullCategory.ESCORT);
            case java.awt.event.KeyEvent.VK_2 -> UISystem.selectShopHullCategory(ctx, ShopHullCategory.LINE);
            case java.awt.event.KeyEvent.VK_3 -> UISystem.selectShopHullCategory(ctx, ShopHullCategory.CAPITAL);
            case java.awt.event.KeyEvent.VK_4 -> UISystem.selectShopHullCategory(ctx, ShopHullCategory.TITAN);
            case java.awt.event.KeyEvent.VK_LEFT,
                    java.awt.event.KeyEvent.VK_OPEN_BRACKET,
                    java.awt.event.KeyEvent.VK_MINUS,
                    java.awt.event.KeyEvent.VK_SUBTRACT -> UISystem.stepShopHullPage(ctx, -1);
            case java.awt.event.KeyEvent.VK_RIGHT,
                    java.awt.event.KeyEvent.VK_CLOSE_BRACKET,
                    java.awt.event.KeyEvent.VK_EQUALS,
                    java.awt.event.KeyEvent.VK_ADD -> UISystem.stepShopHullPage(ctx, +1);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean tryHandleMapHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.ui.mapOpen) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 ->
                    UISystem.setTacticalSectorScale(ctx, UiState.TacticalSectorScalePreset.COMPACT);
            case java.awt.event.KeyEvent.VK_2 ->
                    UISystem.setTacticalSectorScale(ctx, UiState.TacticalSectorScalePreset.STANDARD);
            case java.awt.event.KeyEvent.VK_3 ->
                    UISystem.setTacticalSectorScale(ctx, UiState.TacticalSectorScalePreset.EXPANDED);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean tryHandleCampaignEpisodeHotkey(GameContext ctx, java.awt.event.KeyEvent e) {
        if (ctx == null || e == null) return false;
        int keyCode = e.getKeyCode();
        if (keyCode == java.awt.event.KeyEvent.VK_ENTER || keyCode == java.awt.event.KeyEvent.VK_SPACE) {
            return CampaignSystem.launchPendingEpisode(ctx);
        }
        if (ctx.ui != null && ctx.ui.hasBlockingOverlay()) return false;
        int routeIndex = switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> 0;
            case java.awt.event.KeyEvent.VK_2 -> 1;
            case java.awt.event.KeyEvent.VK_3 -> 2;
            default -> -1;
        };
        return routeIndex >= 0 && CampaignSystem.selectRouteChoice(ctx, routeIndex);
    }

    public static boolean tryHandlePowerOverlayHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.ui.powerManagementOpen) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> UISystem.selectPowerManagementSlot(ctx, 0);
            case java.awt.event.KeyEvent.VK_2 -> UISystem.selectPowerManagementSlot(ctx, 1);
            case java.awt.event.KeyEvent.VK_3 -> UISystem.selectPowerManagementSlot(ctx, 2);
            case java.awt.event.KeyEvent.VK_4 -> UISystem.selectPowerManagementSlot(ctx, 3);
            case java.awt.event.KeyEvent.VK_5 -> UISystem.selectPowerManagementSlot(ctx, 4);
            case java.awt.event.KeyEvent.VK_6 -> UISystem.selectPowerManagementSlot(ctx, 5);
            case java.awt.event.KeyEvent.VK_7 -> UISystem.toggleOverloadMode(ctx);
            case java.awt.event.KeyEvent.VK_8 -> UISystem.cycleOverloadBus(ctx, +1);
            case java.awt.event.KeyEvent.VK_9 -> UISystem.cycleEngineeringPriority(ctx, +1);
            case java.awt.event.KeyEvent.VK_0 -> UISystem.toggleEmergencyThrustMode(ctx);
            case java.awt.event.KeyEvent.VK_UP -> UISystem.cyclePowerManagementSlot(ctx, -1);
            case java.awt.event.KeyEvent.VK_DOWN -> UISystem.cyclePowerManagementSlot(ctx, +1);
            case java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_OPEN_BRACKET, java.awt.event.KeyEvent.VK_MINUS, java.awt.event.KeyEvent.VK_SUBTRACT -> UISystem.stepPowerAllocation(ctx, -1);
            case java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_CLOSE_BRACKET, java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.KeyEvent.VK_ADD -> UISystem.stepPowerAllocation(ctx, +1);
            case java.awt.event.KeyEvent.VK_F1 -> UISystem.applyPowerPreset(ctx, Ship.PowerPreset.BALANCED);
            case java.awt.event.KeyEvent.VK_F2 -> UISystem.applyPowerPreset(ctx, Ship.PowerPreset.ATTACK);
            case java.awt.event.KeyEvent.VK_F3 -> UISystem.applyPowerPreset(ctx, Ship.PowerPreset.DEFENSE);
            case java.awt.event.KeyEvent.VK_F4 -> UISystem.applyPowerPreset(ctx, Ship.PowerPreset.PURSUIT);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean tryHandleCrewStationsHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.ui.crewStationsOpen) return false;
        boolean handled = false;

        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_F1 -> {
                UISystem.selectCrewStation(ctx, GameContext.CrewStation.CAPTAIN);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_F2 -> {
                UISystem.selectCrewStation(ctx, GameContext.CrewStation.HELM);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_F3 -> {
                UISystem.selectCrewStation(ctx, GameContext.CrewStation.TACTICAL);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_F4 -> {
                UISystem.selectCrewStation(ctx, GameContext.CrewStation.ENGINEERING);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_F5 -> {
                UISystem.selectCrewStation(ctx, GameContext.CrewStation.SCIENCE);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_LEFT -> {
                UISystem.cycleCrewStation(ctx, -1);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_RIGHT -> {
                UISystem.cycleCrewStation(ctx, +1);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_A -> {
                UISystem.toggleActiveStationAutomation(ctx);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_C -> {
                UISystem.toggleVoiceCaptions(ctx);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_Z -> {
                UISystem.cycleVoiceMixFocus(ctx, -1);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_X -> {
                UISystem.cycleVoiceMixFocus(ctx, +1);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_COMMA -> {
                UISystem.stepVoiceMixVolume(ctx, -1);
                handled = true;
            }
            case java.awt.event.KeyEvent.VK_PERIOD -> {
                UISystem.stepVoiceMixVolume(ctx, +1);
                handled = true;
            }
            default -> {
                // handled below by station-specific bindings
            }
        }
        if (handled) return true;

        switch (ctx.command.activeCrewStation) {
            case CAPTAIN -> {
                if (keyCode == java.awt.event.KeyEvent.VK_1) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.BALANCED);
                else if (keyCode == java.awt.event.KeyEvent.VK_2) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.ATTACK);
                else if (keyCode == java.awt.event.KeyEvent.VK_3) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.DEFENSE);
                else if (keyCode == java.awt.event.KeyEvent.VK_4) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.EMERGENCY);
                else if (keyCode == java.awt.event.KeyEvent.VK_5) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.MINE);
                else if (keyCode == java.awt.event.KeyEvent.VK_6) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.ESCORT);
                else if (keyCode == java.awt.event.KeyEvent.VK_7) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.DEFEND);
                else if (keyCode == java.awt.event.KeyEvent.VK_8) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.REPAIR);
                else if (keyCode == java.awt.event.KeyEvent.VK_9) UISystem.applyCaptainDirective(ctx, GameContext.CaptainDirective.RTB);
                else if (keyCode == java.awt.event.KeyEvent.VK_0) UISystem.cycleAlliedFleetFormation(ctx);
                else if (keyCode == java.awt.event.KeyEvent.VK_MINUS || keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    tryTeleportToBase(ctx);
                }
                else return false;
                return true;
            }
            case HELM -> {
                if (keyCode == java.awt.event.KeyEvent.VK_1) UISystem.setHelmMode(ctx, GameContext.HelmMode.INTERCEPT);
                else if (keyCode == java.awt.event.KeyEvent.VK_2) UISystem.setHelmMode(ctx, GameContext.HelmMode.ORBIT);
                else if (keyCode == java.awt.event.KeyEvent.VK_3) UISystem.setHelmMode(ctx, GameContext.HelmMode.MAINTAIN_RANGE);
                else if (keyCode == java.awt.event.KeyEvent.VK_4) UISystem.setHelmMode(ctx, GameContext.HelmMode.EVASIVE);
                else if (keyCode == java.awt.event.KeyEvent.VK_5) UISystem.toggleEmergencyThrustMode(ctx);
                else return false;
                return true;
            }
            case TACTICAL -> {
                if (keyCode == java.awt.event.KeyEvent.VK_1) UISystem.setTacticalMode(ctx, GameContext.TacticalMode.HOLD_FIRE);
                else if (keyCode == java.awt.event.KeyEvent.VK_2) UISystem.setTacticalMode(ctx, GameContext.TacticalMode.DEFENSIVE);
                else if (keyCode == java.awt.event.KeyEvent.VK_3) UISystem.setTacticalMode(ctx, GameContext.TacticalMode.AGGRESSIVE);
                else return false;
                return true;
            }
            case ENGINEERING -> {
                if (keyCode == java.awt.event.KeyEvent.VK_1) UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.BALANCED);
                else if (keyCode == java.awt.event.KeyEvent.VK_2) UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.ATTACK);
                else if (keyCode == java.awt.event.KeyEvent.VK_3) UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.DEFENSE);
                else if (keyCode == java.awt.event.KeyEvent.VK_4) UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.DAMAGE_CONTROL);
                else if (keyCode == java.awt.event.KeyEvent.VK_5) UISystem.toggleOverloadMode(ctx);
                else if (keyCode == java.awt.event.KeyEvent.VK_6) UISystem.cycleOverloadBus(ctx, +1);
                else if (keyCode == java.awt.event.KeyEvent.VK_7) UISystem.cycleEngineeringPriority(ctx, +1);
                else if (keyCode == java.awt.event.KeyEvent.VK_8) UISystem.suppressHottestFire(ctx);
                else return false;
                return true;
            }
            case SCIENCE -> {
                if (keyCode == java.awt.event.KeyEvent.VK_1) {
                    ctx.command.scienceAutomation = false;
                    UISystem.scienceLockNearest(ctx);
                } else if (keyCode == java.awt.event.KeyEvent.VK_2) {
                    ctx.command.scienceAutomation = false;
                    UISystem.scienceClearLock(ctx);
                } else if (keyCode == java.awt.event.KeyEvent.VK_3) {
                    ctx.command.scienceAutomation = false;
                    UISystem.toggleScienceJamming(ctx);
                }
                else return false;
                return true;
            }
        }
        return true;
    }

    public static boolean tryHandleBaseMenuHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.ui.baseMenuOpen) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> UISystem.tryUpgradeBase(ctx, 1);
            case java.awt.event.KeyEvent.VK_2 -> UISystem.tryUpgradeBase(ctx, 2);
            case java.awt.event.KeyEvent.VK_3 -> UISystem.tryUpgradeBase(ctx, 3);
            case java.awt.event.KeyEvent.VK_4 -> UISystem.tryUpgradeBase(ctx, 4);
            case java.awt.event.KeyEvent.VK_5 -> UISystem.tryUpgradeBase(ctx, 5);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean tryHandleFlightDeckHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.ui.flightDeckOpen) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_F1 -> UISystem.selectFlightDeckSlot(ctx, 0);
            case java.awt.event.KeyEvent.VK_F2 -> UISystem.selectFlightDeckSlot(ctx, 1);
            case java.awt.event.KeyEvent.VK_F3 -> UISystem.selectFlightDeckSlot(ctx, 2);
            case java.awt.event.KeyEvent.VK_F4 -> UISystem.selectFlightDeckSlot(ctx, 3);
            case java.awt.event.KeyEvent.VK_F5 -> UISystem.selectFlightDeckSlot(ctx, 4);
            case java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_OPEN_BRACKET -> UISystem.cycleFlightDeckSlot(ctx, -1);
            case java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_CLOSE_BRACKET -> UISystem.cycleFlightDeckSlot(ctx, +1);
            case java.awt.event.KeyEvent.VK_MINUS, java.awt.event.KeyEvent.VK_SUBTRACT -> UISystem.cycleFocusedFlightDeckRole(ctx, -1);
            case java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.KeyEvent.VK_ADD -> UISystem.cycleFocusedFlightDeckRole(ctx, +1);
            case java.awt.event.KeyEvent.VK_6 -> UISystem.setFocusedFlightDeckRole(ctx, ShipRole.FIGHTER);
            case java.awt.event.KeyEvent.VK_7 -> UISystem.setFocusedFlightDeckRole(ctx, ShipRole.DRONE);
            case java.awt.event.KeyEvent.VK_8 -> UISystem.setFocusedFlightDeckRole(ctx, ShipRole.BOMBER);
            case java.awt.event.KeyEvent.VK_9 -> UISystem.fillFlightDeck(ctx, ShipRole.FIGHTER);
            case java.awt.event.KeyEvent.VK_0 -> UISystem.fillFlightDeck(ctx, ShipRole.BOMBER);
            case java.awt.event.KeyEvent.VK_BACK_SPACE -> UISystem.resetFlightDeckLoadout(ctx);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean tryHandleAllySpawnHotkey(GameContext ctx, int keyCode) {
        if (!canIssueCombatAction(ctx)) return false;

        ShipRole role = switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> ShipRole.PICKET;
            case java.awt.event.KeyEvent.VK_2 -> ShipRole.PATROL;
            case java.awt.event.KeyEvent.VK_3 -> ShipRole.FRIGATE;
            case java.awt.event.KeyEvent.VK_4 -> ShipRole.MISSILE_BOAT;
            case java.awt.event.KeyEvent.VK_5 -> ShipRole.CIWS_CORVETTE;
            case java.awt.event.KeyEvent.VK_6 -> ShipRole.LIGHT_CRUISER;
            case java.awt.event.KeyEvent.VK_7 -> ShipRole.BATTLECRUISER;
            case java.awt.event.KeyEvent.VK_8 -> ShipRole.BATTLESHIP;
            case java.awt.event.KeyEvent.VK_9 -> ShipRole.DREADNOUGHT;
            case java.awt.event.KeyEvent.VK_0 -> ShipRole.SUPERSHIP;
            default -> null;
        };

        if (role == null) return false;
        SpawnSystem.spawnAlly(ctx, role, ctx.player.x, ctx.player.y);
        return true;
    }

    public static boolean tryHandleShootingRangeHotkey(GameContext ctx, java.awt.event.KeyEvent e) {
        if (!canIssueCombatAction(ctx)) return false;
        if (e == null) return false;
        if (!SpawnSystem.hasShootingRangeTargets(ctx)) return false;
        int keyCode = e.getKeyCode();

        if (e.isControlDown() && e.isShiftDown()) {
            if (keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                ctx.player.applyHull(ShipRole.FRIGATE, ctx.player.x, ctx.player.y);
                EventSystem.showBanner(ctx, "PLAYER HULL: FRIGATE", 1.0);
                return true;
            }
            if (keyCode == java.awt.event.KeyEvent.VK_M) {
                ctx.player.applyHull(ShipRole.MOTHERSHIP, ctx.player.x, ctx.player.y);
                EventSystem.showBanner(ctx, "PLAYER HULL: MOTHERSHIP", 1.1);
                return true;
            }

            TitanArchetype playerArchetype = switch (keyCode) {
                case java.awt.event.KeyEvent.VK_1 -> TitanArchetype.TRANSPORT;
                case java.awt.event.KeyEvent.VK_2 -> TitanArchetype.BULWARK;
                case java.awt.event.KeyEvent.VK_3 -> TitanArchetype.CARRIER_SUPPORT;
                case java.awt.event.KeyEvent.VK_4 -> TitanArchetype.VANGUARD;
                case java.awt.event.KeyEvent.VK_5 -> TitanArchetype.INTERDICTION;
                case java.awt.event.KeyEvent.VK_6 -> TitanArchetype.COMMAND_INTEL;
                case java.awt.event.KeyEvent.VK_7 -> TitanArchetype.BOARDING_RECOVERY;
                case java.awt.event.KeyEvent.VK_8 -> TitanArchetype.ARTILLERY;
                case java.awt.event.KeyEvent.VK_9 -> TitanArchetype.SHIELD_BASTION;
                case java.awt.event.KeyEvent.VK_0 -> TitanArchetype.FLEET_TELEPORTER;
                case java.awt.event.KeyEvent.VK_Q -> TitanArchetype.ELITE_SUPERSHIP_COMMAND;
                case java.awt.event.KeyEvent.VK_T -> TitanArchetype.ELITE_REINFORCEMENTS;
                case java.awt.event.KeyEvent.VK_E -> TitanArchetype.MOBILE_STATION;
                case java.awt.event.KeyEvent.VK_R -> TitanArchetype.HYPERWEAPON;
                default -> null;
            };
            if (playerArchetype == null) return false;

            ShipRole hullRole = playerArchetype.shipRole();
            ctx.player.applyHull(hullRole, ctx.player.x, ctx.player.y);
            EventSystem.showBanner(ctx, "PLAYER HULL: " + playerArchetype.displayName().toUpperCase(), 1.1);
            return true;
        }

        if (e.isShiftDown()) {
            if (keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                return SpawnSystem.clearShootingRangeTitanLayout(ctx);
            }

            TitanArchetype archetype = switch (keyCode) {
                case java.awt.event.KeyEvent.VK_1 -> TitanArchetype.TRANSPORT;
                case java.awt.event.KeyEvent.VK_2 -> TitanArchetype.BULWARK;
                case java.awt.event.KeyEvent.VK_3 -> TitanArchetype.CARRIER_SUPPORT;
                case java.awt.event.KeyEvent.VK_4 -> TitanArchetype.VANGUARD;
                case java.awt.event.KeyEvent.VK_5 -> TitanArchetype.INTERDICTION;
                case java.awt.event.KeyEvent.VK_6 -> TitanArchetype.COMMAND_INTEL;
                case java.awt.event.KeyEvent.VK_7 -> TitanArchetype.BOARDING_RECOVERY;
                case java.awt.event.KeyEvent.VK_8 -> TitanArchetype.ARTILLERY;
                case java.awt.event.KeyEvent.VK_9 -> TitanArchetype.SHIELD_BASTION;
                case java.awt.event.KeyEvent.VK_0 -> TitanArchetype.FLEET_TELEPORTER;
                case java.awt.event.KeyEvent.VK_Q -> TitanArchetype.ELITE_SUPERSHIP_COMMAND;
                case java.awt.event.KeyEvent.VK_T -> TitanArchetype.ELITE_REINFORCEMENTS;
                case java.awt.event.KeyEvent.VK_E -> TitanArchetype.MOBILE_STATION;
                case java.awt.event.KeyEvent.VK_R -> TitanArchetype.HYPERWEAPON;
                default -> null;
            };
            if (archetype == null) return false;
            return SpawnSystem.setShootingRangeTitanLayout(ctx, archetype);
        }

        Faction targetFaction = switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> Faction.ALLY;
            case java.awt.event.KeyEvent.VK_2 -> Faction.ENEMY;
            case java.awt.event.KeyEvent.VK_3 -> Faction.TEAM_C;
            case java.awt.event.KeyEvent.VK_4 -> Faction.TEAM_D;
            default -> null;
        };

        if (targetFaction == null) return false;
        if (ctx.player != null && ctx.player.faction != null && ctx.player.faction.isFriendlyTo(targetFaction)) {
            EventSystem.showBanner(ctx, "SHOOTING RANGE TARGETS MUST BE HOSTILE", 1.2);
            return true;
        }
        return SpawnSystem.setShootingRangeTargetFaction(ctx, targetFaction);
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        return s.alive && !s.dying && s.hp > 0;
    }
}
