public final class GameplayActions {
    private GameplayActions() {}

    public static void handleEscape(GameContext ctx, Runnable exitToMenu) {
        if (ctx == null) return;
        if (ctx.state == GameState.GAME_OVER || ctx.gameOver) {
            if (exitToMenu != null) exitToMenu.run();
            return;
        }
        if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen || ctx.powerManagementOpen
                || ctx.crewStationsOpen || ctx.flightDeckOpen) {
            UISystem.closeAllOverlays(ctx);
            return;
        }
        ctx.state = (ctx.state == GameState.PAUSED) ? GameState.RUNNING : GameState.PAUSED;
    }

    public static boolean canIssueCombatAction(GameContext ctx) {
        if (ctx == null || ctx.player == null) return false;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return false;
        if (ctx.state != GameState.RUNNING) return false;
        return !ctx.shopOpen && !ctx.baseMenuOpen && !ctx.mapOpen
                && !ctx.powerManagementOpen && !ctx.crewStationsOpen && !ctx.flightDeckOpen;
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
        ctx.scienceAutomation = false;
        TargetingSystem.lockClosestToMouse(ctx, controls);
    }

    public static void cycleLockedTarget(GameContext ctx, int dir) {
        if (ctx == null) return;
        ctx.scienceAutomation = false;
        TargetingSystem.cycleLockedTarget(ctx, dir);
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
        GameContext.HudDetail[] modes = GameContext.HudDetail.values();
        int next = (ctx.hudDetail.ordinal() + 1) % modes.length;
        ctx.hudDetail = modes[next];
        EventSystem.showBanner(ctx, "HUD: " + ctx.hudDetail.name(), 0.8);
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
        ctx.engineeringAutomation = false;
        EventSystem.showBanner(ctx, "POWER: " + preset.name(), 0.8);
    }

    public static void cycleCrewOrder(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Ship.CrewOrder order = ctx.player.cycleCrewOrder();
        ctx.engineeringAutomation = false;
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
        Ship base = TeamSystem.getBaseForTeam(ctx, player.faction);
        boolean hasWaypoint = Double.isFinite(ctx.waypointX) && Double.isFinite(ctx.waypointY);
        double targetX;
        double targetY;
        String destinationLabel;
        if (hasWaypoint) {
            targetX = ctx.waypointX;
            targetY = ctx.waypointY;
            destinationLabel = "WAYPOINT";
        } else if (base != null && base.alive && !base.dying && base.hp > 0) {
            targetX = base.x;
            targetY = base.y;
            destinationLabel = "BASE";
        } else {
            EventSystem.showBanner(ctx, "WARP UNAVAILABLE: SET WAYPOINT OR FIND BASE", 1.4);
            return;
        }

        if (player.isWarpCharging()) {
            player.cancelBattlefieldWarp();
            ctx.playerTeleportCharging = false;
            ctx.playerTeleportChargeRemaining = 0.0;
            EventSystem.showBanner(ctx, "BATTLEFIELD WARP CANCELLED", 1.0);
            return;
        }

        if (!player.beginBattlefieldWarp(targetX, targetY, 10.0)) {
            EventSystem.showBanner(ctx, "WARP UNAVAILABLE", 1.2);
            return;
        }
        ctx.playerTeleportCharging = true;
        ctx.playerTeleportChargeRemaining = player.warpChargeRemaining();
        EventSystem.showBanner(ctx, "BATTLEFIELD WARP TO " + destinationLabel + " (10.0S)", 1.2);
    }

    public static void rotateShieldFacing(GameContext ctx, int dir) {
        if (!canIssueCombatAction(ctx)) return;
        int step = (dir < 0) ? -1 : 1;
        ctx.player.shieldFacingMode = Ship.ShieldFacingMode.MANUAL;
        ctx.player.rotateShieldFacing(Math.toRadians(12.0 * step));
    }

    public static boolean tryHandleShopHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.shopOpen) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_3 -> UISystem.tryEquipEnergyBolt(ctx);
            case java.awt.event.KeyEvent.VK_4 -> UISystem.tryBuyBeamBolt(ctx);
            case java.awt.event.KeyEvent.VK_5 -> UISystem.tryBuyHullPlating(ctx);
            case java.awt.event.KeyEvent.VK_6 -> UISystem.tryBuyShieldArray(ctx);
            case java.awt.event.KeyEvent.VK_7 -> UISystem.tryAddGunTurret(ctx);
            case java.awt.event.KeyEvent.VK_8 -> UISystem.tryAddMissileRack(ctx);
            case java.awt.event.KeyEvent.VK_9 -> UISystem.tryUpgradeCIWS(ctx);
            case java.awt.event.KeyEvent.VK_F1 -> UISystem.trySwapHull(ctx, ShipRole.PATROL, 0, 0);
            case java.awt.event.KeyEvent.VK_F2 -> UISystem.trySwapHull(ctx, ShipRole.PICKET, 180, 0);
            case java.awt.event.KeyEvent.VK_F3 -> UISystem.trySwapHull(ctx, ShipRole.FRIGATE, 0, 0);
            case java.awt.event.KeyEvent.VK_F4 -> UISystem.trySwapHull(ctx, ShipRole.MISSILE_BOAT, 300, 0);
            case java.awt.event.KeyEvent.VK_F5 -> UISystem.trySwapHull(ctx, ShipRole.CIWS_CORVETTE, 250, 0);
            case java.awt.event.KeyEvent.VK_F6 -> UISystem.trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
            case java.awt.event.KeyEvent.VK_F7 -> UISystem.trySwapHull(ctx, ShipRole.MEDIUM_CRUISER, 950, 1);
            case java.awt.event.KeyEvent.VK_BACK_SLASH -> UISystem.trySwapHull(ctx, ShipRole.CRUISER, 1100, 1);
            case java.awt.event.KeyEvent.VK_F8 -> UISystem.trySwapHull(ctx, ShipRole.BATTLECRUISER, 1600, 2);
            case java.awt.event.KeyEvent.VK_F9 -> UISystem.trySwapHull(ctx, ShipRole.BATTLESHIP, 2200, 2);
            case java.awt.event.KeyEvent.VK_F11 -> UISystem.trySwapHull(ctx, ShipRole.STEALTH_SHIP, 1200, 2);
            case java.awt.event.KeyEvent.VK_F12 -> UISystem.trySwapHull(ctx, ShipRole.DREADNOUGHT, 3200, 3);
            case java.awt.event.KeyEvent.VK_0 -> UISystem.trySwapHull(ctx, ShipRole.CARRIER, 2800, 3);
            case java.awt.event.KeyEvent.VK_MINUS -> UISystem.trySwapHull(ctx, ShipRole.DRONE_CARRIER, 3000, 3);
            case java.awt.event.KeyEvent.VK_EQUALS -> UISystem.trySwapHull(ctx, ShipRole.SUPERSHIP, 5200, 3);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean tryHandlePowerOverlayHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.powerManagementOpen) return false;
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
        if (ctx == null || !ctx.crewStationsOpen) return false;
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

        switch (ctx.activeCrewStation) {
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
                else if (keyCode == java.awt.event.KeyEvent.VK_Q) UISystem.assignNearestFriendlyShipFleetOverride(ctx, GameContext.FleetCommand.ATTACK);
                else if (keyCode == java.awt.event.KeyEvent.VK_W) UISystem.assignNearestFriendlyShipFleetOverride(ctx, GameContext.FleetCommand.DEFEND);
                else if (keyCode == java.awt.event.KeyEvent.VK_E) UISystem.assignNearestFriendlyShipFleetOverride(ctx, GameContext.FleetCommand.REPAIR);
                else if (keyCode == java.awt.event.KeyEvent.VK_R) UISystem.assignNearestFriendlyShipFleetOverride(ctx, GameContext.FleetCommand.RTB);
                else if (keyCode == java.awt.event.KeyEvent.VK_T) UISystem.assignNearestFriendlyShipFleetOverride(ctx, GameContext.FleetCommand.AUTO);
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
                    ctx.scienceAutomation = false;
                    UISystem.scienceLockNearest(ctx);
                } else if (keyCode == java.awt.event.KeyEvent.VK_2) {
                    ctx.scienceAutomation = false;
                    UISystem.scienceClearLock(ctx);
                } else if (keyCode == java.awt.event.KeyEvent.VK_3) {
                    ctx.scienceAutomation = false;
                    UISystem.toggleScienceJamming(ctx);
                }
                else return false;
                return true;
            }
        }
        return true;
    }

    public static boolean tryHandleBaseMenuHotkey(GameContext ctx, int keyCode) {
        if (ctx == null || !ctx.baseMenuOpen) return false;
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
        if (ctx == null || !ctx.flightDeckOpen) return false;
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

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        return s.alive && !s.dying && s.hp > 0;
    }
}
