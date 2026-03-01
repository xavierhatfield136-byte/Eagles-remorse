public final class GameplayActions {
    private GameplayActions() {}

    public static void handleEscape(GameContext ctx, Runnable exitToMenu) {
        if (ctx == null) return;
        if (ctx.state == GameState.GAME_OVER || ctx.gameOver) {
            if (exitToMenu != null) exitToMenu.run();
            return;
        }
        if (ctx.shopOpen || ctx.baseMenuOpen || ctx.mapOpen) {
            UISystem.closeAllOverlays(ctx);
            return;
        }
        ctx.state = (ctx.state == GameState.PAUSED) ? GameState.RUNNING : GameState.PAUSED;
    }

    public static boolean canIssueCombatAction(GameContext ctx) {
        if (ctx == null || ctx.player == null) return false;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return false;
        if (ctx.state != GameState.RUNNING) return false;
        return !ctx.shopOpen && !ctx.baseMenuOpen && !ctx.mapOpen;
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

    public static void lockUnderMouse(GameContext ctx, PlayerControl controls) {
        if (ctx == null || controls == null) return;
        TargetingSystem.lockClosestToMouse(ctx, controls);
    }

    public static void cycleLockedTarget(GameContext ctx, int dir) {
        if (ctx == null) return;
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

    public static void tryShieldOvercharge(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        ctx.player.tryShieldOvercharge();
    }

    public static void tryMissileSalvo(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;

        Ship target;
        if (isAlive(ctx.lockedTarget)
                && TeamSystem.isHostileToPlayer(ctx, ctx.lockedTarget.faction)
                && TargetingSystem.isDetectableToObserver(ctx.player, ctx.lockedTarget)) {
            target = ctx.lockedTarget;
        } else {
            target = TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, ctx.player.x, ctx.player.y, 1100);
        }
        if (target == null) return;
        if (!TeamSystem.isHostileToPlayer(ctx, target.faction)) return;
        ctx.projectiles.addAll(ctx.player.tryMissileSalvo(target, GameContext.DT));
    }

    public static void tryWaveMotionGun(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        if (!ctx.player.hasWaveMotionGun) return;

        WaveMotionShot shot = ctx.player.tryFireWaveMotionGunAt(ctx.cursorWorldX, ctx.cursorWorldY, GameContext.DT);
        if (shot != null) {
            ctx.projectiles.add(shot);
            EventSystem.showBanner(ctx, "WAVE-MOTION GUN FIRED", 1.0);
            ScreenShake.kick(8.0);
        } else if (ctx.player.isWaveMotionCharging()) {
            EventSystem.showBanner(ctx, "WAVE-MOTION GUN CHARGING", 0.8);
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
        EventSystem.showBanner(ctx, "POWER: " + preset.name(), 0.8);
    }

    public static void cycleCrewOrder(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Ship.CrewOrder order = ctx.player.cycleCrewOrder();
        EventSystem.showBanner(ctx, "CREW ORDER: " + order.name(), 0.8);
    }

    public static void cycleShieldFacingMode(GameContext ctx) {
        if (!canIssueCombatAction(ctx)) return;
        Ship.ShieldFacingMode mode = ctx.player.cycleShieldFacingMode();
        EventSystem.showBanner(ctx, "SHIELD MODE: " + mode.name(), 0.8);
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
