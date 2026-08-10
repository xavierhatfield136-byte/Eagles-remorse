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
 * Compact accessibility editor for the launch console.
 */
final class ExperienceSettingsDialog {
    private ExperienceSettingsDialog() {}

    static ExperienceSettings show(JPanel parent, ExperienceSettings source) {
        ExperienceSettings base = (source == null) ? ExperienceSettings.defaults() : source.copy();
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
        JComboBox<ExperienceSettings.VisualDetail> visualDetail = new JComboBox<>(ExperienceSettings.VisualDetail.values());
        JLabel modifierPreview = new JLabel();
        mining.setSelectedItem(base.miningMode);
        firing.setSelectedItem(base.firingMode);
        map.setSelectedItem(base.mapMode);
        visualDetail.setSelectedItem(base.visualDetail);
        modifierPreview.setText(modifierPreview(ExperienceSettings.universalCampaign()));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(modifierPreview);
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
        panel.add(row("Visual detail", visualDetail));

        int result = JOptionPane.showConfirmDialog(parent, panel, "Accessibility",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return source;

        ExperienceSettings out = base;
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
        out.visualDetail = (ExperienceSettings.VisualDetail) visualDetail.getSelectedItem();
        out.normalize();
        return out;
    }

    private static JPanel row(String label, java.awt.Component component) {
        JPanel row = new JPanel();
        row.add(new JLabel(label + ":"));
        row.add(component);
        return row;
    }

    private static JSlider scaleSlider(double value) {
        return new JSlider(80, 180, (int) Math.round(value * 100.0));
    }

    private static double number(JSlider slider) {
        return slider.getValue() / 100.0;
    }

    private static String modifierPreview(ExperienceSettings settings) {
        return "<html><b>Campaign difficulty is fixed</b><br>"
                + String.join("<br>", settings.modifierSummaryLines())
                + "</html>";
    }
}
