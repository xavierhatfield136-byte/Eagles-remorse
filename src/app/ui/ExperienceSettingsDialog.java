package app.ui;

import app.config.ExperienceSettings;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Compact custom difficulty and accessibility editor for the launch console.
 */
final class ExperienceSettingsDialog {
    private ExperienceSettingsDialog() {}

    static ExperienceSettings show(JPanel parent, ExperienceSettings source) {
        ExperienceSettings base = (source == null) ? ExperienceSettings.defaults() : source.copy();
        JComboBox<ExperienceSettings.Preset> preset = new JComboBox<>(ExperienceSettings.Preset.values());
        preset.setSelectedItem(base.preset);
        JSlider command = slider(base.commandComplexity);
        JSlider lethality = slider(base.combatLethality);
        JSlider pressure = slider(base.strategicPressure);
        JSlider attrition = slider(base.attrition);
        JComboBox<ExperienceSettings.ColorblindPalette> palette = new JComboBox<>(ExperienceSettings.ColorblindPalette.values());
        palette.setSelectedItem(base.colorblindPalette);
        JSlider textScale = scaleSlider(base.uiTextScale);
        JSlider subtitleScale = scaleSlider(base.subtitleScale);
        JCheckBox highContrast = new JCheckBox("High-contrast HUD", base.highContrastHud);
        JCheckBox reducedFlash = new JCheckBox("Reduced flash", base.reducedFlash);
        JCheckBox reducedShake = new JCheckBox("Reduced screen shake", base.reducedScreenShake);
        JCheckBox subtitleBackground = new JCheckBox("Subtitle background", base.subtitleBackground);
        JCheckBox speakerLabels = new JCheckBox("Subtitle speaker labels", base.subtitleSpeakerLabels);
        JCheckBox pauseFocus = new JCheckBox("Pause on focus loss", base.pauseOnFocusLoss);
        JComboBox<ExperienceSettings.InteractionMode> mining = new JComboBox<>(ExperienceSettings.InteractionMode.values());
        JComboBox<ExperienceSettings.InteractionMode> firing = new JComboBox<>(ExperienceSettings.InteractionMode.values());
        JComboBox<ExperienceSettings.InteractionMode> map = new JComboBox<>(ExperienceSettings.InteractionMode.values());
        mining.setSelectedItem(base.miningMode);
        firing.setSelectedItem(base.firingMode);
        map.setSelectedItem(base.mapMode);
        preset.addActionListener(e -> {
            ExperienceSettings.Preset selected = (ExperienceSettings.Preset) preset.getSelectedItem();
            if (selected == null || selected == ExperienceSettings.Preset.CUSTOM) return;
            ExperienceSettings values = ExperienceSettings.forPreset(selected);
            command.setValue((int) Math.round(values.commandComplexity * 100.0));
            lethality.setValue((int) Math.round(values.combatLethality * 100.0));
            pressure.setValue((int) Math.round(values.strategicPressure * 100.0));
            attrition.setValue((int) Math.round(values.attrition * 100.0));
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(row("Preset", preset));
        panel.add(row("Command complexity", command));
        panel.add(row("Combat lethality", lethality));
        panel.add(row("Strategic pressure", pressure));
        panel.add(row("Attrition", attrition));
        panel.add(Box.createVerticalStrut(8));
        panel.add(row("Colorblind palette", palette));
        panel.add(row("UI text scale", textScale));
        panel.add(row("Subtitle size", subtitleScale));
        panel.add(highContrast);
        panel.add(reducedFlash);
        panel.add(reducedShake);
        panel.add(subtitleBackground);
        panel.add(speakerLabels);
        panel.add(pauseFocus);
        panel.add(row("Mining input", mining));
        panel.add(row("Firing input", firing));
        panel.add(row("Map input", map));

        int result = JOptionPane.showConfirmDialog(parent, panel, "Difficulty And Accessibility",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return source;

        ExperienceSettings out = base;
        out.preset = (ExperienceSettings.Preset) preset.getSelectedItem();
        out.commandComplexity = number(command);
        out.combatLethality = number(lethality);
        out.strategicPressure = number(pressure);
        out.attrition = number(attrition);
        out.tacticalOnly = out.preset == ExperienceSettings.Preset.TACTICAL_ONLY;
        out.commandOnly = out.preset == ExperienceSettings.Preset.COMMAND_ONLY;
        out.ironCommand = out.preset == ExperienceSettings.Preset.IRON_COMMAND;
        out.colorblindPalette = (ExperienceSettings.ColorblindPalette) palette.getSelectedItem();
        out.uiTextScale = number(textScale);
        out.subtitleScale = number(subtitleScale);
        out.highContrastHud = highContrast.isSelected();
        out.reducedFlash = reducedFlash.isSelected();
        out.reducedScreenShake = reducedShake.isSelected();
        out.subtitleBackground = subtitleBackground.isSelected();
        out.subtitleSpeakerLabels = speakerLabels.isSelected();
        out.pauseOnFocusLoss = pauseFocus.isSelected();
        out.miningMode = (ExperienceSettings.InteractionMode) mining.getSelectedItem();
        out.firingMode = (ExperienceSettings.InteractionMode) firing.getSelectedItem();
        out.mapMode = (ExperienceSettings.InteractionMode) map.getSelectedItem();
        out.normalize();
        return out;
    }

    private static JPanel row(String label, java.awt.Component component) {
        JPanel row = new JPanel();
        row.add(new JLabel(label + ":"));
        row.add(component);
        return row;
    }

    private static JSlider slider(double value) {
        return new JSlider(40, 180, (int) Math.round(value * 100.0));
    }

    private static JSlider scaleSlider(double value) {
        return new JSlider(80, 180, (int) Math.round(value * 100.0));
    }

    private static double number(JSlider slider) {
        return slider.getValue() / 100.0;
    }
}
