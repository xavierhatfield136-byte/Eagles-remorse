import app.config.GameConfig;
import app.config.GameMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Regression harness for ship/base upgrade behavior.
 * Validates costs, stat changes, unlock gating, and station-turret inheritance.
 */
public final class UpgradeRegressionHarness {
    private UpgradeRegressionHarness() {}

    private static final int[] DEFAULT_SEEDS = {51001, 51002, 51003};

    private static final class Result {
        int seed;
        boolean pass = true;
        String failReason = "";
        int checks = 0;
        int failedChecks = 0;
        int shipChecks = 0;
        int baseChecks = 0;
        int ciwsPurchases = 0;
        int turretLevel = 0;
        boolean inheritedStationTurretBuff = false;
    }

    public static void main(String[] args) throws Exception {
        HarnessArgs cfg = HarnessArgs.parse(args);
        List<Result> results = new ArrayList<>();
        for (int seed : cfg.seeds) {
            results.add(runSeed(seed));
        }

        String json = toJson(results);
        if (cfg.outputPath == null || cfg.outputPath.isBlank()) {
            System.out.println(json);
            return;
        }

        Path out = Paths.get(cfg.outputPath);
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, json, StandardCharsets.UTF_8);
        System.out.println("[upgrade-harness] wrote " + out.toAbsolutePath());
    }

    private static Result runSeed(int seed) {
        Result r = new Result();
        r.seed = seed;

        GameConfig config = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(config);
        SpawnSystem.initWorld(ctx);

        if (ctx.player == null || ctx.allyBase == null) {
            fail(r, "missing_player_or_base");
            return r;
        }

        Ship base = ctx.allyBase;
        BaseUpgrades up = ctx.baseUpgrades.computeIfAbsent(base, k -> new BaseUpgrades());

        ctx.player.x = base.x;
        ctx.player.y = base.y;
        ctx.credits = 1_000_000;
        base.oreStockpile = 1_000_000;

        // ---- Ship upgrades ----
        ctx.ui.shopOpen = true;
        ctx.ui.baseMenuOpen = false;

        UISystem.tryEquipEnergyBolt(ctx);
        checkShip(r, ctx.player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.ENERGY_BOLT, "equip_energy_bolt");

        int creditsBeforeBeam = ctx.credits;
        UISystem.tryBuyBeamBolt(ctx);
        checkShip(r, ctx.player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT, "buy_beam_bolt_family");
        checkShip(r, ctx.credits == creditsBeforeBeam - 220, "buy_beam_bolt_cost");

        int hpBefore = ctx.player.hpMax;
        int creditsBeforeHull = ctx.credits;
        UISystem.tryBuyHullPlating(ctx);
        checkShip(r, ctx.player.hpMax == hpBefore + 10, "hull_plating_hp");
        checkShip(r, ctx.credits == creditsBeforeHull - 60, "hull_plating_cost");

        if (ctx.player.shieldActive && ctx.player.shieldMax > 0.0) {
            double shieldMaxBefore = ctx.player.shieldMax;
            double shieldRegenBefore = ctx.player.shieldRegen;
            int creditsBeforeShield = ctx.credits;
            UISystem.tryBuyShieldArray(ctx);
            checkShip(r, ctx.player.shieldMax > shieldMaxBefore, "shield_array_max");
            checkShip(r, ctx.player.shieldRegen > shieldRegenBefore, "shield_array_regen");
            checkShip(r, ctx.credits == creditsBeforeShield - 70, "shield_array_cost");
        }

        int gunBefore = countTurrets(ctx.player, Turret.Kind.GUN);
        int creditsBeforeGun = ctx.credits;
        UISystem.tryAddGunTurret(ctx);
        checkShip(r, countTurrets(ctx.player, Turret.Kind.GUN) == gunBefore + 1, "add_gun_turret_count");
        checkShip(r, ctx.credits == creditsBeforeGun - 100, "add_gun_turret_cost");

        int missileBefore = countTurrets(ctx.player, Turret.Kind.MISSILE);
        int creditsBeforeRack = ctx.credits;
        UISystem.tryAddMissileRack(ctx);
        checkShip(r, countTurrets(ctx.player, Turret.Kind.MISSILE) == missileBefore + 1, "add_missile_rack_count");
        checkShip(r, ctx.credits == creditsBeforeRack - 140, "add_missile_rack_cost");

        if (ctx.player.hasCIWS) {
            int guard = 0;
            while (!ctx.player.isCIWSUpgradeMaxed() && guard < 24) {
                int creditsBeforeCiws = ctx.credits;
                UISystem.tryUpgradeCIWS(ctx);
                checkShip(r, ctx.credits == creditsBeforeCiws - 120, "ciws_upgrade_cost_step");
                guard++;
                r.ciwsPurchases++;
            }
            checkShip(r, ctx.player.isCIWSUpgradeMaxed(), "ciws_reaches_max");
            int creditsAtMax = ctx.credits;
            UISystem.tryUpgradeCIWS(ctx);
            checkShip(r, ctx.credits == creditsAtMax, "ciws_max_no_cost");
        }

        ShipRole roleBeforeLockedSwap = ctx.player.role;
        int creditsBeforeLockedSwap = ctx.credits;
        UISystem.trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
        checkShip(r, ctx.player.role == roleBeforeLockedSwap, "hangar_lock_prevents_swap");
        checkShip(r, ctx.credits == creditsBeforeLockedSwap, "hangar_lock_no_cost");

        // ---- Base upgrades ----
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = true;

        int baseHpBefore = base.hpMax;
        int creditsBeforeBaseHull = ctx.credits;
        int oreBeforeBaseHull = base.oreStockpile;
        UISystem.tryUpgradeBase(ctx, 1);
        checkBase(r, up.hullLv >= 1, "base_hull_level");
        checkBase(r, base.hpMax > baseHpBefore, "base_hull_hp");
        checkBase(r, ctx.credits < creditsBeforeBaseHull && base.oreStockpile < oreBeforeBaseHull, "base_hull_cost");

        double baseShieldBefore = base.shieldMax;
        double baseRegenBefore = base.shieldRegen;
        UISystem.tryUpgradeBase(ctx, 2);
        checkBase(r, up.shieldLv >= 1, "base_shield_level");
        checkBase(r, base.shieldMax > baseShieldBefore, "base_shield_max");
        checkBase(r, base.shieldRegen > baseRegenBefore, "base_shield_regen");

        Turret baseGunBefore = firstTurretByKind(base, Turret.Kind.GUN);
        int baseGunDmgBefore = (baseGunBefore == null) ? -1 : baseGunBefore.damage;
        double baseGunCdBefore = (baseGunBefore == null) ? -1.0 : baseGunBefore.cooldown;
        UISystem.tryUpgradeBase(ctx, 3);
        r.turretLevel = up.turretLv;
        checkBase(r, up.turretLv >= 1, "base_turret_level");
        Turret baseGunAfter = firstTurretByKind(base, Turret.Kind.GUN);
        if (baseGunBefore != null && baseGunAfter != null) {
            checkBase(r, baseGunAfter.damage > baseGunDmgBefore, "base_turret_damage");
            checkBase(r, baseGunAfter.cooldown < baseGunCdBefore, "base_turret_cooldown");
        }

        double miningMulBefore = ctx.miningBaseMul;
        double orePriceMulBefore = ctx.orePriceBaseMul;
        UISystem.tryUpgradeBase(ctx, 4);
        checkBase(r, up.miningLv >= 1, "base_mining_level");
        checkBase(r, ctx.miningBaseMul > miningMulBefore, "base_mining_mul");
        checkBase(r, ctx.orePriceBaseMul > orePriceMulBefore, "base_ore_price_mul");

        int hangarBefore = up.hangarLv;
        UISystem.tryUpgradeBase(ctx, 5);
        checkBase(r, up.hangarLv == hangarBefore + 1, "base_hangar_level");

        // With hangar tier >= 1, LIGHT_CRUISER unlock should succeed.
        ctx.ui.baseMenuOpen = false;
        ctx.ui.shopOpen = true;
        int creditsBeforeUnlockedSwap = ctx.credits;
        UISystem.trySwapHull(ctx, ShipRole.LIGHT_CRUISER, 700, 1);
        checkShip(r, ctx.player.role == ShipRole.LIGHT_CRUISER, "hangar_unlock_swap");
        checkShip(r, ctx.credits == creditsBeforeUnlockedSwap - 700, "hangar_unlock_cost");

        // Spawned station turret should inherit turret-system upgrade levels.
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = true;
        if (up.turretLv < 2) {
            UISystem.tryUpgradeBase(ctx, 3);
            r.turretLevel = up.turretLv;
        }
        ctx.ships.removeIf(s -> s != null && s.role == ShipRole.STATIC_TURRET && s.faction == base.faction);
        base.baseSpawnCooldown = 0.0;
        base.resetBaseSpawnTimer();

        Ship spawnedTurret = null;
        for (int i = 0; i < 8 && spawnedTurret == null; i++) {
            EconomySystem.update(ctx, GameContext.DT);
            spawnedTurret = findOwnedStationTurret(ctx, base);
            if (spawnedTurret == null) {
                base.baseSpawnCooldown = 0.0;
                base.resetBaseSpawnTimer();
            }
        }

        FleetShip baselineTurret = new FleetShip(ShipRole.STATIC_TURRET, base.faction, 0, 0);
        try { DoctrineRegistry.applyToShip(baselineTurret); } catch (Throwable ignored) {}

        Turret spawnedGun = firstTurretByKind(spawnedTurret, Turret.Kind.GUN);
        Turret baselineGun = firstTurretByKind(baselineTurret, Turret.Kind.GUN);
        if (spawnedGun != null && baselineGun != null) {
            r.inheritedStationTurretBuff = (spawnedGun.damage > baselineGun.damage)
                    || (spawnedGun.cooldown < baselineGun.cooldown);
        }
        checkBase(r, spawnedTurret != null, "station_turret_spawned");
        checkBase(r, r.inheritedStationTurretBuff, "station_turret_inherits_upgrade");

        if (r.failedChecks > 0) {
            r.pass = false;
        }
        return r;
    }

    private static Ship findOwnedStationTurret(GameContext ctx, Ship base) {
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.STATIC_TURRET) continue;
            if (s.minerHomeBase == base) return s;
        }
        return null;
    }

    private static int countTurrets(Ship ship, Turret.Kind kind) {
        if (ship == null || ship.turrets == null || kind == null) return 0;
        int out = 0;
        for (Turret t : ship.turrets) {
            if (t == null) continue;
            if (t.kind == kind) out++;
        }
        return out;
    }

    private static Turret firstTurretByKind(Ship ship, Turret.Kind kind) {
        if (ship == null || ship.turrets == null || kind == null) return null;
        for (Turret t : ship.turrets) {
            if (t == null) continue;
            if (t.kind == kind) return t;
        }
        return null;
    }

    private static void checkShip(Result r, boolean ok, String tag) {
        r.shipChecks++;
        check(r, ok, tag);
    }

    private static void checkBase(Result r, boolean ok, String tag) {
        r.baseChecks++;
        check(r, ok, tag);
    }

    private static void check(Result r, boolean ok, String tag) {
        r.checks++;
        if (ok) return;
        r.failedChecks++;
        fail(r, tag);
    }

    private static void fail(Result r, String reason) {
        if (reason == null || reason.isBlank()) return;
        if (r.failReason == null || r.failReason.isBlank()) {
            r.failReason = reason;
        } else {
            r.failReason = r.failReason + "|" + reason;
        }
    }

    private static String toJson(List<Result> results) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"scenario\": \"upgrade_regression\",\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            if (i > 0) sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"seed\": ").append(r.seed).append(",\n");
            sb.append("      \"pass\": ").append(r.pass).append(",\n");
            sb.append("      \"failReason\": ").append(q(r.failReason)).append(",\n");
            sb.append("      \"checks\": ").append(r.checks).append(",\n");
            sb.append("      \"failedChecks\": ").append(r.failedChecks).append(",\n");
            sb.append("      \"shipChecks\": ").append(r.shipChecks).append(",\n");
            sb.append("      \"baseChecks\": ").append(r.baseChecks).append(",\n");
            sb.append("      \"ciwsPurchases\": ").append(r.ciwsPurchases).append(",\n");
            sb.append("      \"turretLevel\": ").append(r.turretLevel).append(",\n");
            sb.append("      \"stationTurretInheritedBuff\": ").append(r.inheritedStationTurretBuff).append("\n");
            sb.append("    }");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class HarnessArgs {
        final int[] seeds;
        final String outputPath;

        private HarnessArgs(int[] seeds, String outputPath) {
            this.seeds = seeds;
            this.outputPath = outputPath;
        }

        static HarnessArgs parse(String[] args) {
            int[] seeds = Arrays.copyOf(DEFAULT_SEEDS, DEFAULT_SEEDS.length);
            String output = "";

            if (args != null) {
                for (String arg : args) {
                    if (arg == null) continue;
                    if (arg.startsWith("--seeds=")) {
                        String raw = arg.substring("--seeds=".length()).trim();
                        if (!raw.isBlank()) {
                            String[] parts = raw.split(",");
                            int[] parsed = new int[parts.length];
                            int n = 0;
                            for (String p : parts) {
                                try { parsed[n++] = Integer.parseInt(p.trim()); } catch (Throwable ignored) {}
                            }
                            if (n > 0) seeds = Arrays.copyOf(parsed, n);
                        }
                    } else if (arg.startsWith("--output=")) {
                        output = arg.substring("--output=".length()).trim();
                    }
                }
            }
            return new HarnessArgs(seeds, output);
        }
    }
}
