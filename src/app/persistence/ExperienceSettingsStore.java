package app.persistence;

import app.config.ExperienceSettings;
import app.support.ErrorLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persists difficulty and accessibility defaults independently of save files.
 */
public final class ExperienceSettingsStore {
    private static final Path FILE = Paths.get("save", "experience_settings.properties");

    private ExperienceSettingsStore() {}

    public static ExperienceSettings load() {
        ExperienceSettings out = ExperienceSettings.defaults();
        if (!Files.exists(FILE)) return out;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
            out.preset = enumValue(ExperienceSettings.Preset.class, props.getProperty("preset"), out.preset);
            ExperienceSettings preset = ExperienceSettings.forPreset(out.preset);
            copyDifficulty(preset, out);
            out.commandComplexity = number(props, "commandComplexity", out.commandComplexity);
            out.combatLethality = number(props, "combatLethality", out.combatLethality);
            out.strategicPressure = number(props, "strategicPressure", out.strategicPressure);
            out.attrition = number(props, "attrition", out.attrition);
            out.tacticalOnly = bool(props, "tacticalOnly", out.tacticalOnly);
            out.commandOnly = bool(props, "commandOnly", out.commandOnly);
            out.ironCommand = bool(props, "ironCommand", out.ironCommand);
            out.colorblindPalette = enumValue(ExperienceSettings.ColorblindPalette.class, props.getProperty("colorblindPalette"), out.colorblindPalette);
            out.uiTextScale = number(props, "uiTextScale", out.uiTextScale);
            out.highContrastHud = bool(props, "highContrastHud", out.highContrastHud);
            out.reducedFlash = bool(props, "reducedFlash", out.reducedFlash);
            out.reducedScreenShake = bool(props, "reducedScreenShake", out.reducedScreenShake);
            out.subtitleScale = number(props, "subtitleScale", out.subtitleScale);
            out.subtitleBackground = bool(props, "subtitleBackground", out.subtitleBackground);
            out.subtitleSpeakerLabels = bool(props, "subtitleSpeakerLabels", out.subtitleSpeakerLabels);
            out.pauseOnFocusLoss = bool(props, "pauseOnFocusLoss", out.pauseOnFocusLoss);
            out.miningMode = enumValue(ExperienceSettings.InteractionMode.class, props.getProperty("miningMode"), out.miningMode);
            out.firingMode = enumValue(ExperienceSettings.InteractionMode.class, props.getProperty("firingMode"), out.firingMode);
            out.mapMode = enumValue(ExperienceSettings.InteractionMode.class, props.getProperty("mapMode"), out.mapMode);
        } catch (IOException ex) {
            ErrorLog.logException("[experience] load_failed path=" + FILE, ex);
        }
        out.normalize();
        return out;
    }

    public static void save(ExperienceSettings value) {
        ExperienceSettings settings = (value == null) ? ExperienceSettings.defaults() : value.copy();
        settings.normalize();
        Properties props = new Properties();
        props.setProperty("preset", settings.preset.name());
        props.setProperty("commandComplexity", String.valueOf(settings.commandComplexity));
        props.setProperty("combatLethality", String.valueOf(settings.combatLethality));
        props.setProperty("strategicPressure", String.valueOf(settings.strategicPressure));
        props.setProperty("attrition", String.valueOf(settings.attrition));
        props.setProperty("tacticalOnly", String.valueOf(settings.tacticalOnly));
        props.setProperty("commandOnly", String.valueOf(settings.commandOnly));
        props.setProperty("ironCommand", String.valueOf(settings.ironCommand));
        props.setProperty("colorblindPalette", settings.colorblindPalette.name());
        props.setProperty("uiTextScale", String.valueOf(settings.uiTextScale));
        props.setProperty("highContrastHud", String.valueOf(settings.highContrastHud));
        props.setProperty("reducedFlash", String.valueOf(settings.reducedFlash));
        props.setProperty("reducedScreenShake", String.valueOf(settings.reducedScreenShake));
        props.setProperty("subtitleScale", String.valueOf(settings.subtitleScale));
        props.setProperty("subtitleBackground", String.valueOf(settings.subtitleBackground));
        props.setProperty("subtitleSpeakerLabels", String.valueOf(settings.subtitleSpeakerLabels));
        props.setProperty("pauseOnFocusLoss", String.valueOf(settings.pauseOnFocusLoss));
        props.setProperty("miningMode", settings.miningMode.name());
        props.setProperty("firingMode", settings.firingMode.name());
        props.setProperty("mapMode", settings.mapMode.name());
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "Difficulty and accessibility defaults");
            }
        } catch (IOException ex) {
            ErrorLog.logException("[experience] save_failed path=" + FILE, ex);
        }
    }

    private static void copyDifficulty(ExperienceSettings source, ExperienceSettings target) {
        target.commandComplexity = source.commandComplexity;
        target.combatLethality = source.combatLethality;
        target.strategicPressure = source.strategicPressure;
        target.attrition = source.attrition;
        target.tacticalOnly = source.tacticalOnly;
        target.commandOnly = source.commandOnly;
        target.ironCommand = source.ironCommand;
    }

    private static boolean bool(Properties props, String key, boolean fallback) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(fallback)));
    }

    private static double number(Properties props, String key, double fallback) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
