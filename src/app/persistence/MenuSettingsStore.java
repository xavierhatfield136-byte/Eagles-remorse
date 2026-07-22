package app.persistence;

import app.config.GameMode;
import app.config.MultiplayerMissionChoice;
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
 * Persists main-menu startup settings between sessions.
 */
public final class MenuSettingsStore {
    private MenuSettingsStore() {}

    private static final Path SAVE_DIR = UserDataPaths.saveDir();
    private static final Path SETTINGS_FILE = SAVE_DIR.resolve("menu_settings.properties");
    private static final Object IO_LOCK = new Object();

    public static final class MenuSettings {
        public int version = 1;
        public String modeName = GameMode.CAMPAIGN_OPS.name();
        public int mapIndex = 0;
        public boolean randomEvents = true;
        public String seedText = "0";
        public int playerTeamId = 0;
        public String multiplayerDirectAddress = "127.0.0.1:46717";
        public String multiplayerMissionId = MultiplayerMissionChoice.DEFAULT_MISSION_ID;
        public String multiplayerPlayerName = "Player";
        public boolean voiceCaptionsEnabled = true;
        public double voiceVolumeCaptain = 1.0;
        public double voiceVolumeHelm = 1.0;
        public double voiceVolumeTactical = 1.0;
        public double voiceVolumeEngineering = 1.0;
        public double voiceVolumeScience = 1.0;

        void normalize() {
            version = 1;
            if (modeName == null || modeName.isBlank() || !isKnownMode(modeName)) {
                modeName = GameMode.CAMPAIGN_OPS.name();
            }
            mapIndex = clamp(mapIndex, 0, 2);
            playerTeamId = clamp(playerTeamId, 0, 3);
            if (seedText == null) seedText = "0";
            seedText = seedText.trim();
            if (seedText.isBlank()) seedText = "0";
            if (seedText.length() > 32) seedText = seedText.substring(0, 32);
            if (multiplayerDirectAddress == null) multiplayerDirectAddress = "127.0.0.1:46717";
            multiplayerDirectAddress = multiplayerDirectAddress.trim();
            if (multiplayerDirectAddress.isBlank()) multiplayerDirectAddress = "127.0.0.1:46717";
            if (multiplayerDirectAddress.length() > 128) {
                multiplayerDirectAddress = multiplayerDirectAddress.substring(0, 128);
            }
            multiplayerMissionId = MultiplayerMissionChoice.fromMissionId(multiplayerMissionId).missionId();
            if (multiplayerPlayerName == null) multiplayerPlayerName = "Player";
            multiplayerPlayerName = multiplayerPlayerName.trim();
            if (multiplayerPlayerName.isBlank()) multiplayerPlayerName = "Player";
            if (multiplayerPlayerName.length() > 32) {
                multiplayerPlayerName = multiplayerPlayerName.substring(0, 32);
            }

            voiceVolumeCaptain = clampVoiceVolume(voiceVolumeCaptain);
            voiceVolumeHelm = clampVoiceVolume(voiceVolumeHelm);
            voiceVolumeTactical = clampVoiceVolume(voiceVolumeTactical);
            voiceVolumeEngineering = clampVoiceVolume(voiceVolumeEngineering);
            voiceVolumeScience = clampVoiceVolume(voiceVolumeScience);
        }
    }

    public static MenuSettings load() {
        synchronized (IO_LOCK) {
            MenuSettings s = new MenuSettings();
            if (!Files.exists(SETTINGS_FILE)) return s;

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(SETTINGS_FILE, StandardOpenOption.READ)) {
                props.load(in);
                s.version = parseInt(props, "version", 1);
                s.modeName = props.getProperty("modeName", s.modeName);
                s.mapIndex = parseInt(props, "mapIndex", s.mapIndex);
                s.randomEvents = parseBoolean(props, "randomEvents", s.randomEvents);
                s.randomEvents = true;
                s.seedText = props.getProperty("seedText", s.seedText);
                s.playerTeamId = parseInt(props, "playerTeamId", s.playerTeamId);
                s.multiplayerDirectAddress = props.getProperty(
                        "multiplayerDirectAddress", s.multiplayerDirectAddress);
                s.multiplayerMissionId = props.getProperty("multiplayerMissionId", s.multiplayerMissionId);
                s.multiplayerPlayerName = props.getProperty("multiplayerPlayerName", s.multiplayerPlayerName);
                s.voiceCaptionsEnabled = parseBoolean(props, "voiceCaptionsEnabled", s.voiceCaptionsEnabled);
                s.voiceVolumeCaptain = parseDouble(props, "voiceVolumeCaptain", s.voiceVolumeCaptain);
                s.voiceVolumeHelm = parseDouble(props, "voiceVolumeHelm", s.voiceVolumeHelm);
                s.voiceVolumeTactical = parseDouble(props, "voiceVolumeTactical", s.voiceVolumeTactical);
                s.voiceVolumeEngineering = parseDouble(props, "voiceVolumeEngineering", s.voiceVolumeEngineering);
                s.voiceVolumeScience = parseDouble(props, "voiceVolumeScience", s.voiceVolumeScience);
            } catch (IOException ex) {
                ErrorLog.logException("[settings] load_failed path=" + SETTINGS_FILE, ex);
            }

            s.normalize();
            return s;
        }
    }

    public static void save(MenuSettings s) {
        if (s == null) return;
        synchronized (IO_LOCK) {
            s.normalize();

            try {
                Files.createDirectories(SAVE_DIR);
            } catch (IOException ex) {
                ErrorLog.logException("[settings] save_failed mkdir path=" + SAVE_DIR, ex);
                return;
            }

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(s.version));
            props.setProperty("modeName", s.modeName);
            props.setProperty("mapIndex", String.valueOf(s.mapIndex));
            props.setProperty("randomEvents", String.valueOf(s.randomEvents));
            props.setProperty("seedText", s.seedText);
            props.setProperty("playerTeamId", String.valueOf(s.playerTeamId));
            props.setProperty("multiplayerDirectAddress", s.multiplayerDirectAddress);
            props.setProperty("multiplayerMissionId", s.multiplayerMissionId);
            props.setProperty("multiplayerPlayerName", s.multiplayerPlayerName);
            props.setProperty("voiceCaptionsEnabled", String.valueOf(s.voiceCaptionsEnabled));
            props.setProperty("voiceVolumeCaptain", String.valueOf(s.voiceVolumeCaptain));
            props.setProperty("voiceVolumeHelm", String.valueOf(s.voiceVolumeHelm));
            props.setProperty("voiceVolumeTactical", String.valueOf(s.voiceVolumeTactical));
            props.setProperty("voiceVolumeEngineering", String.valueOf(s.voiceVolumeEngineering));
            props.setProperty("voiceVolumeScience", String.valueOf(s.voiceVolumeScience));

            Path tmp = SETTINGS_FILE.resolveSibling(SETTINGS_FILE.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(
                    tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
                 OutputStream out = Channels.newOutputStream(channel)) {
                props.store(out, "Main menu settings");
                out.flush();
                channel.force(true);
            } catch (IOException ex) {
                ErrorLog.logException("[settings] save_failed write path=" + tmp, ex);
                deleteTempQuietly(tmp);
                return;
            }

            try {
                Files.move(tmp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                try {
                    Files.move(tmp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex2) {
                    ErrorLog.logException("[settings] save_failed move path=" + SETTINGS_FILE, ex2);
                    deleteTempQuietly(tmp);
                }
            } catch (IOException ex) {
                ErrorLog.logException("[settings] save_failed move path=" + SETTINGS_FILE, ex);
                deleteTempQuietly(tmp);
            }
        }
    }

    public static GameMode resolveMode(String modeName) {
        if (modeName != null) {
            for (GameMode gm : GameMode.values()) {
                if (gm.name().equalsIgnoreCase(modeName.trim())) return gm;
            }
        }
        return GameMode.CAMPAIGN_OPS;
    }

    private static boolean isKnownMode(String modeName) {
        for (GameMode gm : GameMode.values()) {
            if (gm.name().equals(modeName)) return true;
        }
        return false;
    }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
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

    private static double clampVoiceVolume(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return clamp(value, 0.0, 2.0);
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
