package app.persistence;

import app.config.GameConfig;
import app.config.GameMode;
import app.support.ErrorLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Persists resumable campaign checkpoints between sector transitions.
 */
public final class CampaignCheckpointStore {
    private static final String DEFAULT_BRANCH_ROUTE = "BALANCED";
    private static final String DEFAULT_PLAYER_FACTION_NAME = "PLAYER";
    private static final String DEFAULT_PLAYER_ROLE_NAME = "FRIGATE";
    private static final String DEFAULT_PRIMARY_WEAPON_FAMILY_NAME = "ENERGY_BOLT";
    private static final String DEFAULT_POWER_PRESET_NAME = "BALANCED";
    private static final String DEFAULT_CREW_ORDER_NAME = "BALANCED";
    private static final String DEFAULT_ENGINEERING_PRIORITY_NAME = "BALANCED";
    private static final String DEFAULT_OVERLOAD_BUS_NAME = "TACTICAL";
    private static final String DEFAULT_POWER_BUSES = "0.18,0.18,0.19,0.15,0.18,0.12";
    private static final String DEFAULT_CARRIER_COMMAND_MODE_NAME = "ATTACK";

    private CampaignCheckpointStore() {}

    private static final int CURRENT_VERSION = 1;
    private static final Path SAVE_DIR = Paths.get("save");
    private static final Path CHECKPOINT_FILE = SAVE_DIR.resolve("campaign_checkpoint.properties");
    private static final Object IO_LOCK = new Object();

    public static final class Checkpoint {
        public int version = CURRENT_VERSION;

        public int worldW = 5000;
        public int worldH = 5000;
        public boolean randomEvents = true;
        public long seed = 0L;

        public int nextSector = 2;
        public int credits = 0;
        public int sectorsCleared = 0;
        public int campaignKills = 0;
        public int branchScore = 0;
        public String branchRoute = DEFAULT_BRANCH_ROUTE;
        public int sideObjectivesCompletedTotal = 0;
        public int sideObjectivesFailedTotal = 0;

        public boolean unlockAuxGunGranted = false;
        public int unlockMissileTierGranted = 0;
        public boolean unlockCiwsGranted = false;
        public boolean unlockHullGranted = false;
        public boolean bossDropAegisArray = false;
        public boolean bossDropMissileCore = false;
        public boolean bossDropFlagCore = false;
        public int bossDropsCollected = 0;

        public String playerFactionName = DEFAULT_PLAYER_FACTION_NAME;
        public String playerRoleName = DEFAULT_PLAYER_ROLE_NAME;
        public String primaryWeaponFamilyName = DEFAULT_PRIMARY_WEAPON_FAMILY_NAME;
        public int hpMax = 10;
        public double shieldMax = 0.0;
        public double shieldRegen = 0.0;
        public boolean shieldActive = false;
        public int cargo = 0;
        public int cargoMax = 120;
        public double miningRate = 10.0;
        public double miningRange = 56.0;
        public boolean hasCIWS = false;
        public double ciwsRange = 200.0;
        public double ciwsCooldown = 0.12;
        public double ciwsQuality = 0.35;
        public int ciwsPelletsPerBurst = 2;
        public double ciwsPelletSpeed = 920.0;
        public int ciwsPelletDamage = 1;
        public int ciwsPelletLife = 18;
        public double ciwsPelletRadius = 1.8;
        public String powerPresetName = DEFAULT_POWER_PRESET_NAME;
        public String crewOrderName = DEFAULT_CREW_ORDER_NAME;
        public String engineeringPriorityName = DEFAULT_ENGINEERING_PRIORITY_NAME;
        public String overloadBusName = DEFAULT_OVERLOAD_BUS_NAME;
        public String powerBuses = DEFAULT_POWER_BUSES;
        public String turretData = "";
        public boolean isCarrier = false;
        public int maxFighters = 4;
        public String carrierCommandModeName = DEFAULT_CARRIER_COMMAND_MODE_NAME;
        public boolean carrierAutoLaunch = true;
        public String flightDeckLoadout = "";

        public int allyOreStockpile = 0;
        public int enemyOreStockpile = 0;
        public int allyHullLv = 0;
        public int allyShieldLv = 0;
        public int allyTurretLv = 0;
        public int allyMiningLv = 0;
        public int allyHangarLv = 0;
        public int enemyHullLv = 0;
        public int enemyShieldLv = 0;
        public int enemyTurretLv = 0;
        public int enemyMiningLv = 0;
        public int enemyHangarLv = 0;

        public void normalize() {
            version = CURRENT_VERSION;
            worldW = Math.max(2000, worldW);
            worldH = Math.max(2000, worldH);
            nextSector = clamp(nextSector, 1, 12);
            credits = Math.max(0, credits);
            sectorsCleared = clamp(sectorsCleared, 0, 12);
            campaignKills = Math.max(0, campaignKills);
            bossDropsCollected = Math.max(0, bossDropsCollected);
            unlockMissileTierGranted = clamp(unlockMissileTierGranted, 0, 2);
            sideObjectivesCompletedTotal = Math.max(0, sideObjectivesCompletedTotal);
            sideObjectivesFailedTotal = Math.max(0, sideObjectivesFailedTotal);
            branchScore = Math.max(-99, Math.min(99, branchScore));
            branchRoute = safeName(branchRoute, DEFAULT_BRANCH_ROUTE);
            playerFactionName = safeName(playerFactionName, DEFAULT_PLAYER_FACTION_NAME);
            playerRoleName = safeName(playerRoleName, DEFAULT_PLAYER_ROLE_NAME);
            primaryWeaponFamilyName = safeName(primaryWeaponFamilyName, DEFAULT_PRIMARY_WEAPON_FAMILY_NAME);
            powerPresetName = safeName(powerPresetName, DEFAULT_POWER_PRESET_NAME);
            crewOrderName = safeName(crewOrderName, DEFAULT_CREW_ORDER_NAME);
            engineeringPriorityName = safeName(engineeringPriorityName, DEFAULT_ENGINEERING_PRIORITY_NAME);
            overloadBusName = safeName(overloadBusName, DEFAULT_OVERLOAD_BUS_NAME);
            carrierCommandModeName = safeName(carrierCommandModeName, DEFAULT_CARRIER_COMMAND_MODE_NAME);
            hpMax = Math.max(1, hpMax);
            shieldMax = finiteOr(shieldMax, 0.0);
            shieldRegen = finiteOr(shieldRegen, 0.0);
            cargo = Math.max(0, cargo);
            cargoMax = Math.max(0, cargoMax);
            miningRate = Math.max(0.0, finiteOr(miningRate, 10.0));
            miningRange = Math.max(0.0, finiteOr(miningRange, 56.0));
            ciwsRange = Math.max(0.0, finiteOr(ciwsRange, 200.0));
            ciwsCooldown = Math.max(0.02, finiteOr(ciwsCooldown, 0.12));
            ciwsQuality = clamp(finiteOr(ciwsQuality, 0.35), 0.0, 1.0);
            ciwsPelletsPerBurst = Math.max(1, ciwsPelletsPerBurst);
            ciwsPelletSpeed = Math.max(0.0, finiteOr(ciwsPelletSpeed, 920.0));
            ciwsPelletDamage = Math.max(1, ciwsPelletDamage);
            ciwsPelletLife = Math.max(1, ciwsPelletLife);
            ciwsPelletRadius = Math.max(0.1, finiteOr(ciwsPelletRadius, 1.8));
            maxFighters = Math.max(0, maxFighters);
            powerBuses = safeName(powerBuses, DEFAULT_POWER_BUSES);
            turretData = (turretData == null) ? "" : turretData.trim();
            flightDeckLoadout = (flightDeckLoadout == null) ? "" : flightDeckLoadout.trim();
            allyOreStockpile = Math.max(0, allyOreStockpile);
            enemyOreStockpile = Math.max(0, enemyOreStockpile);
            allyHullLv = clamp(allyHullLv, 0, 3);
            allyShieldLv = clamp(allyShieldLv, 0, 3);
            allyTurretLv = clamp(allyTurretLv, 0, 3);
            allyMiningLv = clamp(allyMiningLv, 0, 3);
            allyHangarLv = clamp(allyHangarLv, 0, 3);
            enemyHullLv = clamp(enemyHullLv, 0, 3);
            enemyShieldLv = clamp(enemyShieldLv, 0, 3);
            enemyTurretLv = clamp(enemyTurretLv, 0, 3);
            enemyMiningLv = clamp(enemyMiningLv, 0, 3);
            enemyHangarLv = clamp(enemyHangarLv, 0, 3);
        }

        public boolean isUsable() {
            return nextSector >= 1 && nextSector <= 12;
        }

        public String menuSummary() {
            String role = playerRoleName.replace('_', ' ');
            return "Sector " + nextSector + "  |  " + role + "  |  Route " + branchRoute;
        }

        public GameConfig toGameConfig() {
            return new GameConfig(GameMode.CAMPAIGN_OPS, worldW, worldH, randomEvents, seed, false, 0, true);
        }
    }

    public static Checkpoint load() {
        synchronized (IO_LOCK) {
            if (!Files.exists(CHECKPOINT_FILE)) return null;

            Properties props = new Properties();
            Checkpoint cp = new Checkpoint();
            try (InputStream in = Files.newInputStream(CHECKPOINT_FILE, StandardOpenOption.READ)) {
                props.load(in);
                cp.version = parseInt(props, "version", cp.version);
                cp.worldW = parseInt(props, "worldW", cp.worldW);
                cp.worldH = parseInt(props, "worldH", cp.worldH);
                cp.randomEvents = parseBoolean(props, "randomEvents", cp.randomEvents);
                cp.seed = parseLong(props, "seed", cp.seed);
                cp.nextSector = parseInt(props, "nextSector", cp.nextSector);
                cp.credits = parseInt(props, "credits", cp.credits);
                cp.sectorsCleared = parseInt(props, "sectorsCleared", cp.sectorsCleared);
                cp.campaignKills = parseInt(props, "campaignKills", cp.campaignKills);
                cp.branchScore = parseInt(props, "branchScore", cp.branchScore);
                cp.branchRoute = props.getProperty("branchRoute", cp.branchRoute);
                cp.sideObjectivesCompletedTotal = parseInt(props, "sideObjectivesCompletedTotal", cp.sideObjectivesCompletedTotal);
                cp.sideObjectivesFailedTotal = parseInt(props, "sideObjectivesFailedTotal", cp.sideObjectivesFailedTotal);
                cp.unlockAuxGunGranted = parseBoolean(props, "unlockAuxGunGranted", cp.unlockAuxGunGranted);
                cp.unlockMissileTierGranted = parseInt(props, "unlockMissileTierGranted", cp.unlockMissileTierGranted);
                cp.unlockCiwsGranted = parseBoolean(props, "unlockCiwsGranted", cp.unlockCiwsGranted);
                cp.unlockHullGranted = parseBoolean(props, "unlockHullGranted", cp.unlockHullGranted);
                cp.bossDropAegisArray = parseBoolean(props, "bossDropAegisArray", cp.bossDropAegisArray);
                cp.bossDropMissileCore = parseBoolean(props, "bossDropMissileCore", cp.bossDropMissileCore);
                cp.bossDropFlagCore = parseBoolean(props, "bossDropFlagCore", cp.bossDropFlagCore);
                cp.bossDropsCollected = parseInt(props, "bossDropsCollected", cp.bossDropsCollected);
                cp.playerFactionName = props.getProperty("playerFactionName", cp.playerFactionName);
                cp.playerRoleName = props.getProperty("playerRoleName", cp.playerRoleName);
                cp.primaryWeaponFamilyName = props.getProperty("primaryWeaponFamilyName", cp.primaryWeaponFamilyName);
                cp.hpMax = parseInt(props, "hpMax", cp.hpMax);
                cp.shieldMax = parseDouble(props, "shieldMax", cp.shieldMax);
                cp.shieldRegen = parseDouble(props, "shieldRegen", cp.shieldRegen);
                cp.shieldActive = parseBoolean(props, "shieldActive", cp.shieldActive);
                cp.cargo = parseInt(props, "cargo", cp.cargo);
                cp.cargoMax = parseInt(props, "cargoMax", cp.cargoMax);
                cp.miningRate = parseDouble(props, "miningRate", cp.miningRate);
                cp.miningRange = parseDouble(props, "miningRange", cp.miningRange);
                cp.hasCIWS = parseBoolean(props, "hasCIWS", cp.hasCIWS);
                cp.ciwsRange = parseDouble(props, "ciwsRange", cp.ciwsRange);
                cp.ciwsCooldown = parseDouble(props, "ciwsCooldown", cp.ciwsCooldown);
                cp.ciwsQuality = parseDouble(props, "ciwsQuality", cp.ciwsQuality);
                cp.ciwsPelletsPerBurst = parseInt(props, "ciwsPelletsPerBurst", cp.ciwsPelletsPerBurst);
                cp.ciwsPelletSpeed = parseDouble(props, "ciwsPelletSpeed", cp.ciwsPelletSpeed);
                cp.ciwsPelletDamage = parseInt(props, "ciwsPelletDamage", cp.ciwsPelletDamage);
                cp.ciwsPelletLife = parseInt(props, "ciwsPelletLife", cp.ciwsPelletLife);
                cp.ciwsPelletRadius = parseDouble(props, "ciwsPelletRadius", cp.ciwsPelletRadius);
                cp.powerPresetName = props.getProperty("powerPresetName", cp.powerPresetName);
                cp.crewOrderName = props.getProperty("crewOrderName", cp.crewOrderName);
                cp.engineeringPriorityName = props.getProperty("engineeringPriorityName", cp.engineeringPriorityName);
                cp.overloadBusName = props.getProperty("overloadBusName", cp.overloadBusName);
                cp.powerBuses = props.getProperty("powerBuses", cp.powerBuses);
                cp.turretData = props.getProperty("turretData", cp.turretData);
                cp.isCarrier = parseBoolean(props, "isCarrier", cp.isCarrier);
                cp.maxFighters = parseInt(props, "maxFighters", cp.maxFighters);
                cp.carrierCommandModeName = props.getProperty("carrierCommandModeName", cp.carrierCommandModeName);
                cp.carrierAutoLaunch = parseBoolean(props, "carrierAutoLaunch", cp.carrierAutoLaunch);
                cp.flightDeckLoadout = props.getProperty("flightDeckLoadout", cp.flightDeckLoadout);
                cp.allyOreStockpile = parseInt(props, "allyOreStockpile", cp.allyOreStockpile);
                cp.enemyOreStockpile = parseInt(props, "enemyOreStockpile", cp.enemyOreStockpile);
                cp.allyHullLv = parseInt(props, "allyHullLv", cp.allyHullLv);
                cp.allyShieldLv = parseInt(props, "allyShieldLv", cp.allyShieldLv);
                cp.allyTurretLv = parseInt(props, "allyTurretLv", cp.allyTurretLv);
                cp.allyMiningLv = parseInt(props, "allyMiningLv", cp.allyMiningLv);
                cp.allyHangarLv = parseInt(props, "allyHangarLv", cp.allyHangarLv);
                cp.enemyHullLv = parseInt(props, "enemyHullLv", cp.enemyHullLv);
                cp.enemyShieldLv = parseInt(props, "enemyShieldLv", cp.enemyShieldLv);
                cp.enemyTurretLv = parseInt(props, "enemyTurretLv", cp.enemyTurretLv);
                cp.enemyMiningLv = parseInt(props, "enemyMiningLv", cp.enemyMiningLv);
                cp.enemyHangarLv = parseInt(props, "enemyHangarLv", cp.enemyHangarLv);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_load_failed path=" + CHECKPOINT_FILE, ex);
                return null;
            }
            cp.normalize();
            return cp.isUsable() ? cp : null;
        }
    }

    public static void save(Checkpoint cp) {
        if (cp == null) return;
        synchronized (IO_LOCK) {
            cp.normalize();
            try {
                Files.createDirectories(SAVE_DIR);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_save_failed mkdir path=" + SAVE_DIR, ex);
                return;
            }

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(cp.version));
            props.setProperty("worldW", String.valueOf(cp.worldW));
            props.setProperty("worldH", String.valueOf(cp.worldH));
            props.setProperty("randomEvents", String.valueOf(cp.randomEvents));
            props.setProperty("seed", String.valueOf(cp.seed));
            props.setProperty("nextSector", String.valueOf(cp.nextSector));
            props.setProperty("credits", String.valueOf(cp.credits));
            props.setProperty("sectorsCleared", String.valueOf(cp.sectorsCleared));
            props.setProperty("campaignKills", String.valueOf(cp.campaignKills));
            props.setProperty("branchScore", String.valueOf(cp.branchScore));
            props.setProperty("branchRoute", cp.branchRoute);
            props.setProperty("sideObjectivesCompletedTotal", String.valueOf(cp.sideObjectivesCompletedTotal));
            props.setProperty("sideObjectivesFailedTotal", String.valueOf(cp.sideObjectivesFailedTotal));
            props.setProperty("unlockAuxGunGranted", String.valueOf(cp.unlockAuxGunGranted));
            props.setProperty("unlockMissileTierGranted", String.valueOf(cp.unlockMissileTierGranted));
            props.setProperty("unlockCiwsGranted", String.valueOf(cp.unlockCiwsGranted));
            props.setProperty("unlockHullGranted", String.valueOf(cp.unlockHullGranted));
            props.setProperty("bossDropAegisArray", String.valueOf(cp.bossDropAegisArray));
            props.setProperty("bossDropMissileCore", String.valueOf(cp.bossDropMissileCore));
            props.setProperty("bossDropFlagCore", String.valueOf(cp.bossDropFlagCore));
            props.setProperty("bossDropsCollected", String.valueOf(cp.bossDropsCollected));
            props.setProperty("playerFactionName", cp.playerFactionName);
            props.setProperty("playerRoleName", cp.playerRoleName);
            props.setProperty("primaryWeaponFamilyName", cp.primaryWeaponFamilyName);
            props.setProperty("hpMax", String.valueOf(cp.hpMax));
            props.setProperty("shieldMax", String.valueOf(cp.shieldMax));
            props.setProperty("shieldRegen", String.valueOf(cp.shieldRegen));
            props.setProperty("shieldActive", String.valueOf(cp.shieldActive));
            props.setProperty("cargo", String.valueOf(cp.cargo));
            props.setProperty("cargoMax", String.valueOf(cp.cargoMax));
            props.setProperty("miningRate", String.valueOf(cp.miningRate));
            props.setProperty("miningRange", String.valueOf(cp.miningRange));
            props.setProperty("hasCIWS", String.valueOf(cp.hasCIWS));
            props.setProperty("ciwsRange", String.valueOf(cp.ciwsRange));
            props.setProperty("ciwsCooldown", String.valueOf(cp.ciwsCooldown));
            props.setProperty("ciwsQuality", String.valueOf(cp.ciwsQuality));
            props.setProperty("ciwsPelletsPerBurst", String.valueOf(cp.ciwsPelletsPerBurst));
            props.setProperty("ciwsPelletSpeed", String.valueOf(cp.ciwsPelletSpeed));
            props.setProperty("ciwsPelletDamage", String.valueOf(cp.ciwsPelletDamage));
            props.setProperty("ciwsPelletLife", String.valueOf(cp.ciwsPelletLife));
            props.setProperty("ciwsPelletRadius", String.valueOf(cp.ciwsPelletRadius));
            props.setProperty("powerPresetName", cp.powerPresetName);
            props.setProperty("crewOrderName", cp.crewOrderName);
            props.setProperty("engineeringPriorityName", cp.engineeringPriorityName);
            props.setProperty("overloadBusName", cp.overloadBusName);
            props.setProperty("powerBuses", cp.powerBuses);
            props.setProperty("turretData", cp.turretData);
            props.setProperty("isCarrier", String.valueOf(cp.isCarrier));
            props.setProperty("maxFighters", String.valueOf(cp.maxFighters));
            props.setProperty("carrierCommandModeName", cp.carrierCommandModeName);
            props.setProperty("carrierAutoLaunch", String.valueOf(cp.carrierAutoLaunch));
            props.setProperty("flightDeckLoadout", cp.flightDeckLoadout);
            props.setProperty("allyOreStockpile", String.valueOf(cp.allyOreStockpile));
            props.setProperty("enemyOreStockpile", String.valueOf(cp.enemyOreStockpile));
            props.setProperty("allyHullLv", String.valueOf(cp.allyHullLv));
            props.setProperty("allyShieldLv", String.valueOf(cp.allyShieldLv));
            props.setProperty("allyTurretLv", String.valueOf(cp.allyTurretLv));
            props.setProperty("allyMiningLv", String.valueOf(cp.allyMiningLv));
            props.setProperty("allyHangarLv", String.valueOf(cp.allyHangarLv));
            props.setProperty("enemyHullLv", String.valueOf(cp.enemyHullLv));
            props.setProperty("enemyShieldLv", String.valueOf(cp.enemyShieldLv));
            props.setProperty("enemyTurretLv", String.valueOf(cp.enemyTurretLv));
            props.setProperty("enemyMiningLv", String.valueOf(cp.enemyMiningLv));
            props.setProperty("enemyHangarLv", String.valueOf(cp.enemyHangarLv));

            Path tmp = CHECKPOINT_FILE.resolveSibling(CHECKPOINT_FILE.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(
                    tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
                 OutputStream out = Channels.newOutputStream(channel)) {
                props.store(out, "Campaign checkpoint");
                out.flush();
                channel.force(true);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_save_failed write path=" + tmp, ex);
                deleteTempQuietly(tmp);
                return;
            }

            try {
                Files.move(tmp, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                try {
                    Files.move(tmp, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex2) {
                    ErrorLog.logException("[campaign] checkpoint_save_failed move path=" + CHECKPOINT_FILE, ex2);
                    deleteTempQuietly(tmp);
                }
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_save_failed move path=" + CHECKPOINT_FILE, ex);
                deleteTempQuietly(tmp);
            }
        }
    }

    public static void clear() {
        synchronized (IO_LOCK) {
            try {
                Files.deleteIfExists(CHECKPOINT_FILE);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_clear_failed path=" + CHECKPOINT_FILE, ex);
            }
        }
    }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(Properties props, String key, long fallback) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        return Boolean.parseBoolean(raw.trim());
    }

    private static double parseDouble(Properties props, String key, double fallback) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safeName(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void deleteTempQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
