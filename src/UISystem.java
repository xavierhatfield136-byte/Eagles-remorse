import java.awt.Rectangle;
import java.awt.event.MouseEvent;

public final class UISystem {
    private UISystem(){}

    public static void closeAllOverlays(GameContext ctx) {
        ctx.shopOpen = false;
        ctx.baseMenuOpen = false;
        ctx.mapOpen = false;
        if (!ctx.gameOver) ctx.state = GameState.RUNNING;
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.shopOpen = !ctx.shopOpen;
        if (ctx.shopOpen) {
            ctx.baseMenuOpen = false;
            ctx.mapOpen = false;
            ctx.state = GameState.SHOP;
        } else {
            ctx.state = GameState.RUNNING;
        }
    }

    public static void toggleMap(GameContext ctx) {
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.mapOpen = !ctx.mapOpen;
        if (ctx.mapOpen) {
            ctx.shopOpen = false;
            ctx.baseMenuOpen = false;
            ctx.state = GameState.MAP;
        } else {
            ctx.state = GameState.RUNNING;
        }
    }

    public static void toggleBaseMenu(GameContext ctx) {
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
            ctx.state = GameState.BASE_MENU;
        } else {
            ctx.state = GameState.RUNNING;
        }
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
        ctx.player.hp += 10;
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
        int cost = 120;
        if (ctx.credits < cost) {
            EventSystem.showBanner(ctx, "NOT ENOUGH CREDITS", 1.4);
            return;
        }
        ctx.credits -= cost;
        ctx.player.upgradeCIWS();
        EventSystem.showBanner(ctx, "CIWS UPGRADED", 1.2);
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
        ctx.credits -= cost;
        ctx.player.applyHull(role, ctx.player.x, ctx.player.y);
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
                base.hp += 40;
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
                if (base.turrets != null) {
                    for (Turret t : base.turrets) {
                        if (t == null) continue;
                        t.damage = Math.max(1, t.damage + 1);
                        t.cooldown = Math.max(0.05, t.cooldown * 0.95);
                    }
                }
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

    public static int getMaxHangarTierForPlayer(GameContext ctx) {
        if (ctx == null || ctx.baseUpgrades == null) return 0;
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
}
