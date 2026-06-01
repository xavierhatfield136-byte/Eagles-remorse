package app.config;

/**
 * Player-facing first-hour, difficulty, and accessibility defaults.
 */
public final class ExperienceSettings {
    public enum Preset {
        STANDARD("Standard Command"),
        RELAXED("Relaxed Campaign"),
        TACTICAL_ONLY("Tactical Only"),
        COMMAND_ONLY("Command Only"),
        IRON_COMMAND("Iron Command"),
        CUSTOM("Custom");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ColorblindPalette {
        STANDARD("Standard"),
        DEUTERANOPIA("Deuteranopia"),
        PROTANOPIA("Protanopia"),
        TRITANOPIA("Tritanopia");

        private final String label;

        ColorblindPalette(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum InteractionMode {
        HOLD("Hold"),
        TOGGLE("Toggle");

        private final String label;

        InteractionMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public Preset preset = Preset.STANDARD;
    public double commandComplexity = 1.0;
    public double combatLethality = 1.0;
    public double strategicPressure = 1.0;
    public double attrition = 1.0;
    public boolean tacticalOnly = false;
    public boolean commandOnly = false;
    public boolean ironCommand = false;
    public ColorblindPalette colorblindPalette = ColorblindPalette.STANDARD;
    public double uiTextScale = 1.0;
    public boolean highContrastHud = false;
    public boolean reducedFlash = false;
    public boolean reducedScreenShake = false;
    public double subtitleScale = 1.0;
    public boolean subtitleBackground = true;
    public boolean subtitleSpeakerLabels = true;
    public boolean pauseOnFocusLoss = true;
    public InteractionMode miningMode = InteractionMode.HOLD;
    public InteractionMode firingMode = InteractionMode.HOLD;
    public InteractionMode mapMode = InteractionMode.TOGGLE;

    public static ExperienceSettings defaults() {
        return forPreset(Preset.STANDARD);
    }

    public static ExperienceSettings forPreset(Preset preset) {
        ExperienceSettings out = new ExperienceSettings();
        out.preset = (preset == null) ? Preset.STANDARD : preset;
        switch (out.preset) {
            case RELAXED -> {
                out.commandComplexity = 0.72;
                out.combatLethality = 0.78;
                out.strategicPressure = 0.58;
                out.attrition = 0.56;
            }
            case TACTICAL_ONLY -> {
                out.tacticalOnly = true;
                out.commandComplexity = 0.65;
                out.strategicPressure = 0.0;
                out.attrition = 0.0;
            }
            case COMMAND_ONLY -> {
                out.commandOnly = true;
                out.commandComplexity = 1.15;
                out.combatLethality = 0.9;
            }
            case IRON_COMMAND -> {
                out.ironCommand = true;
                out.combatLethality = 1.3;
                out.strategicPressure = 1.35;
                out.attrition = 1.45;
            }
            default -> {
            }
        }
        return out;
    }

    public ExperienceSettings copy() {
        ExperienceSettings out = new ExperienceSettings();
        out.preset = preset;
        out.commandComplexity = commandComplexity;
        out.combatLethality = combatLethality;
        out.strategicPressure = strategicPressure;
        out.attrition = attrition;
        out.tacticalOnly = tacticalOnly;
        out.commandOnly = commandOnly;
        out.ironCommand = ironCommand;
        out.colorblindPalette = colorblindPalette;
        out.uiTextScale = uiTextScale;
        out.highContrastHud = highContrastHud;
        out.reducedFlash = reducedFlash;
        out.reducedScreenShake = reducedScreenShake;
        out.subtitleScale = subtitleScale;
        out.subtitleBackground = subtitleBackground;
        out.subtitleSpeakerLabels = subtitleSpeakerLabels;
        out.pauseOnFocusLoss = pauseOnFocusLoss;
        out.miningMode = miningMode;
        out.firingMode = firingMode;
        out.mapMode = mapMode;
        return out;
    }

    public void normalize() {
        if (preset == null) preset = Preset.STANDARD;
        if (colorblindPalette == null) colorblindPalette = ColorblindPalette.STANDARD;
        if (miningMode == null) miningMode = InteractionMode.HOLD;
        if (firingMode == null) firingMode = InteractionMode.HOLD;
        if (mapMode == null) mapMode = InteractionMode.TOGGLE;
        commandComplexity = clamp(commandComplexity, 0.4, 1.8);
        combatLethality = clamp(combatLethality, 0.4, 1.8);
        strategicPressure = clamp(strategicPressure, 0.0, 1.8);
        attrition = clamp(attrition, 0.0, 1.8);
        uiTextScale = clamp(uiTextScale, 0.8, 1.6);
        subtitleScale = clamp(subtitleScale, 0.8, 1.8);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(min, Math.min(max, value));
    }
}
