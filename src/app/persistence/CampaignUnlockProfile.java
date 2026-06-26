package app.persistence;

import app.support.ErrorLog;
import app.support.UserDataPaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Persisted campaign meta progression between runs.
 */
public final class CampaignUnlockProfile {
    private static final int CURRENT_VERSION = 1;
    private static final Path SAVE_DIR = UserDataPaths.saveDir();
    private static final Path PROFILE_FILE = SAVE_DIR.resolve("campaign_unlock_profile.properties");
    private static final Object IO_LOCK = new Object();

    public int version = CURRENT_VERSION;
    public int runsStarted = 0;
    public int runsWon = 0;
    public int bestSectorCleared = 0;

    public int gunTier = 0;         // 0..1
    public int missileTier = 0;     // 0..2
    public boolean ciwsUnlocked = false;
    public boolean reinforcedHullUnlocked = false;

    public static CampaignUnlockProfile load() {
        synchronized (IO_LOCK) {
            CampaignUnlockProfile p = new CampaignUnlockProfile();
            if (!Files.exists(PROFILE_FILE)) return p;

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(PROFILE_FILE, StandardOpenOption.READ)) {
                props.load(in);
                p.version = parseInt(props, "version", CURRENT_VERSION);
                p.runsStarted = parseInt(props, "runsStarted", 0);
                p.runsWon = parseInt(props, "runsWon", 0);
                p.bestSectorCleared = parseInt(props, "bestSectorCleared", 0);
                p.gunTier = parseInt(props, "gunTier", 0);
                p.missileTier = parseInt(props, "missileTier", 0);
                p.ciwsUnlocked = Boolean.parseBoolean(props.getProperty("ciwsUnlocked", "false"));
                p.reinforcedHullUnlocked = Boolean.parseBoolean(props.getProperty("reinforcedHullUnlocked", "false"));
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] profile_load_failed path=" + PROFILE_FILE, ex);
            }
            p.normalize();
            return p;
        }
    }

    public static void save(CampaignUnlockProfile p) {
        if (p == null) return;
        synchronized (IO_LOCK) {
            p.normalize();
            try {
                Files.createDirectories(SAVE_DIR);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] profile_save_failed mkdir path=" + SAVE_DIR, ex);
                return;
            }

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(p.version));
            props.setProperty("runsStarted", String.valueOf(p.runsStarted));
            props.setProperty("runsWon", String.valueOf(p.runsWon));
            props.setProperty("bestSectorCleared", String.valueOf(p.bestSectorCleared));
            props.setProperty("gunTier", String.valueOf(p.gunTier));
            props.setProperty("missileTier", String.valueOf(p.missileTier));
            props.setProperty("ciwsUnlocked", String.valueOf(p.ciwsUnlocked));
            props.setProperty("reinforcedHullUnlocked", String.valueOf(p.reinforcedHullUnlocked));

            Path tmp = PROFILE_FILE.resolveSibling(PROFILE_FILE.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(
                    tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
                 OutputStream out = Channels.newOutputStream(channel)) {
                props.store(out, "Campaign unlock profile");
                out.flush();
                channel.force(true);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] profile_save_failed write path=" + tmp, ex);
                deleteTempQuietly(tmp);
                return;
            }

            try {
                Files.move(tmp, PROFILE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                try {
                    Files.move(tmp, PROFILE_FILE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex2) {
                    ErrorLog.logException("[campaign] profile_save_failed move path=" + PROFILE_FILE, ex2);
                    deleteTempQuietly(tmp);
                }
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] profile_save_failed move path=" + PROFILE_FILE, ex);
                deleteTempQuietly(tmp);
            }
        }
    }

    public void markRunStarted() {
        runsStarted++;
        normalize();
    }

    public void markRunWon() {
        runsWon++;
        normalize();
    }

    public boolean recordSectorClear(int sector) {
        boolean changed = false;
        int s = clamp(sector, 0, 12);
        if (s > bestSectorCleared) {
            bestSectorCleared = s;
            changed = true;
        }
        changed |= unlockForSector(s);
        normalize();
        return changed;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (gunTier > 0) sb.append("GUN");
        if (missileTier > 0) {
            if (sb.length() > 0) sb.append(",");
            sb.append("MISSILE").append(missileTier >= 2 ? "x2" : "x1");
        }
        if (ciwsUnlocked) {
            if (sb.length() > 0) sb.append(",");
            sb.append("CIWS");
        }
        if (reinforcedHullUnlocked) {
            if (sb.length() > 0) sb.append(",");
            sb.append("HULL");
        }
        return (sb.length() == 0) ? "none" : sb.toString();
    }

    private boolean unlockForSector(int sector) {
        return switch (sector) {
            case 2 -> setGunTier(1);
            case 4 -> setMissileTier(1);
            case 6 -> setCiwsUnlocked(true);
            case 8 -> setReinforcedHullUnlocked(true);
            case 10 -> setMissileTier(2);
            default -> false;
        };
    }

    private boolean setGunTier(int tier) {
        int t = clamp(tier, 0, 1);
        if (gunTier >= t) return false;
        gunTier = t;
        return true;
    }

    private boolean setMissileTier(int tier) {
        int t = clamp(tier, 0, 2);
        if (missileTier >= t) return false;
        missileTier = t;
        return true;
    }

    private boolean setCiwsUnlocked(boolean enabled) {
        if (ciwsUnlocked == enabled) return false;
        ciwsUnlocked = enabled;
        return true;
    }

    private boolean setReinforcedHullUnlocked(boolean enabled) {
        if (reinforcedHullUnlocked == enabled) return false;
        reinforcedHullUnlocked = enabled;
        return true;
    }

    private void normalize() {
        version = CURRENT_VERSION;
        runsStarted = Math.max(0, runsStarted);
        runsWon = Math.max(0, Math.min(runsWon, runsStarted));
        bestSectorCleared = clamp(bestSectorCleared, 0, 12);
        gunTier = clamp(gunTier, 0, 1);
        missileTier = clamp(missileTier, 0, 2);
    }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
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
