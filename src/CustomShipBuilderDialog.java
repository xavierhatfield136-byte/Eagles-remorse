import app.support.MenuDisplay;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CustomShipBuilderDialog {
    private static final Color BACKGROUND = new Color(8, 15, 24);
    private static final Color PANEL = new Color(14, 27, 43);
    private static final Color PANEL_ALT = new Color(19, 36, 56);
    private static final Color BORDER = new Color(72, 124, 154);
    private static final Color TEXT = new Color(235, 242, 248);
    private static final Color MUTED = new Color(178, 199, 214);

    private CustomShipBuilderDialog() {}

    public static void show(Component parent) {
        CustomShipRegistry registry = new CustomShipRegistry();
        CustomShipCreationService service = new CustomShipCreationService(registry, new CustomShipImageProcessor());
        CustomWeaponRegistry weaponRegistry = new CustomWeaponRegistry();
        CustomWeaponCreationService weaponService = new CustomWeaponCreationService(weaponRegistry, new CustomWeaponAssetProcessor());
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Shipyard", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(buildContent(dialog, service, registry, weaponService));
        dialog.pack();
        dialog.setMinimumSize(new Dimension(820, 540));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static JPanel buildContent(JDialog dialog,
                                       CustomShipCreationService service,
                                       CustomShipRegistry registry,
                                       CustomWeaponCreationService weaponService) {
        JTextField nameField = new JTextField("Custom Team E Ship", 22);
        JTextField classField = new JTextField("Line Frigate", 22);
        JComboBox<CustomHullClass> hullBox = combo(CustomHullClass.values());
        JComboBox<CustomCombatClassification> classificationBox = combo(CustomCombatClassification.values());
        JComboBox<CustomWeaponDoctrine> doctrineBox = combo(CustomWeaponDoctrine.values());
        JComboBox<CustomDefenseBias> defenseBox = combo(CustomDefenseBias.values());
        JSpinner weaponCount = new JSpinner(new SpinnerNumberModel(4, 1, 24, 1));
        DefaultComboBoxModel<WeaponListItem> weaponModel = new DefaultComboBoxModel<>();
        JComboBox<WeaponListItem> weaponBox = new JComboBox<>(weaponModel);
        styleComboBox(weaponBox);
        styleField(nameField);
        styleField(classField);
        styleSpinner(weaponCount);

        DefaultListModel<ShipListItem> shipModel = new DefaultListModel<>();
        JList<ShipListItem> shipList = new JList<>(shipModel);
        shipList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shipList.setBackground(new Color(6, 13, 21));
        shipList.setForeground(TEXT);
        shipList.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, 1.0));
        shipList.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JLabel preview = new JLabel("No custom ship selected", SwingConstants.CENTER);
        preview.setOpaque(true);
        preview.setBackground(new Color(5, 10, 17));
        preview.setForeground(MUTED);
        preview.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, 1.0));
        preview.setPreferredSize(new Dimension(300, 220));
        preview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        shipList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreview(preview, registry, shipList.getSelectedValue());
        });

        JButton importButton = button("Import PNG And Create Team E Ship", new Color(49, 122, 153));
        JButton weaponLabButton = button("Weapon Lab", new Color(62, 100, 143));
        JButton deleteButton = button("Delete Selected", new Color(106, 65, 78));
        JButton closeButton = button("Close", new Color(58, 76, 100));

        importButton.addActionListener(e -> importShip(dialog, service, shipModel, shipList,
                nameField, classField, hullBox, classificationBox, doctrineBox, defenseBox, weaponCount, weaponBox));
        weaponLabButton.addActionListener(e -> {
            openWeaponLab(dialog, weaponService);
            refreshWeaponCombo(weaponService, weaponModel, null);
        });
        deleteButton.addActionListener(e -> deleteSelected(dialog, service, shipModel, shipList));
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel form = panel();
        form.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int row = 0;
        addRow(form, c, row++, "Name:", nameField);
        addRow(form, c, row++, "Class Name:", classField);
        addRow(form, c, row++, "Hull Size:", hullBox);
        addRow(form, c, row++, "Combat Class:", classificationBox);
        addRow(form, c, row++, "Weapons:", doctrineBox);
        addRow(form, c, row++, "Armor / Shields:", defenseBox);
        addRow(form, c, row++, "Weapon Count:", weaponCount);
        addRow(form, c, row, "Weapon System:", weaponBox);

        JLabel storage = new JLabel("Local storage: " + registry.root());
        storage.setForeground(MUTED);
        storage.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 12, 1.0));

        JPanel left = panel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(title("Create Team E Ship"));
        left.add(Box.createVerticalStrut(8));
        left.add(form);
        left.add(Box.createVerticalStrut(10));
        left.add(storage);
        left.add(Box.createVerticalStrut(14));
        JPanel importRow = transparentRow();
        importRow.add(importButton);
        importRow.add(weaponLabButton);
        left.add(importRow);

        JPanel right = panel();
        right.setLayout(new BorderLayout(10, 10));
        right.add(title("Saved Team E Ships"), BorderLayout.NORTH);
        right.add(new JScrollPane(shipList), BorderLayout.CENTER);
        JPanel previewPanel = panel();
        previewPanel.setLayout(new BorderLayout(0, 8));
        previewPanel.add(preview, BorderLayout.CENTER);
        JPanel actions = transparentRow();
        actions.add(deleteButton);
        actions.add(closeButton);
        previewPanel.add(actions, BorderLayout.SOUTH);
        right.add(previewPanel, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setOpaque(true);
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.add(left, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);

        refreshWeaponCombo(weaponService, weaponModel, null);
        refreshShipList(service, shipModel, shipList, null);
        return root;
    }

    private static void importShip(JDialog dialog,
                                   CustomShipCreationService service,
                                   DefaultListModel<ShipListItem> shipModel,
                                   JList<ShipListItem> shipList,
                                   JTextField nameField,
                                   JTextField classField,
                                   JComboBox<CustomHullClass> hullBox,
                                   JComboBox<CustomCombatClassification> classificationBox,
                                   JComboBox<CustomWeaponDoctrine> doctrineBox,
                                   JComboBox<CustomDefenseBias> defenseBox,
                                   JSpinner weaponCount,
                                   JComboBox<WeaponListItem> weaponBox) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG ship hulls", "png"));
        int result = chooser.showOpenDialog(dialog);
        if (result != JFileChooser.APPROVE_OPTION) return;

        try {
            CustomShipCreationService.CreationResult creation = service.createFromPng(
                    chooser.getSelectedFile().toPath(),
                    new CustomShipGenerationRequest(
                            nameField.getText(),
                            classField.getText(),
                            selected(hullBox, CustomHullClass.class, CustomHullClass.FRIGATE),
                            selected(classificationBox, CustomCombatClassification.class, CustomCombatClassification.LINE),
                            selected(doctrineBox, CustomWeaponDoctrine.class, CustomWeaponDoctrine.BALANCED),
                            selected(defenseBox, CustomDefenseBias.class, CustomDefenseBias.BALANCED),
                            ((Number) weaponCount.getValue()).intValue(),
                            selectedWeaponId(weaponBox)));
            refreshShipList(service, shipModel, shipList, creation.definition().id);
            JOptionPane.showMessageDialog(dialog,
                    creation.definition().displayName + " was added to Team E.",
                    "Shipyard",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException | IOException ex) {
            JOptionPane.showMessageDialog(dialog,
                    "Ship import failed: " + ex.getMessage(),
                    "Shipyard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void openWeaponLab(JDialog parent, CustomWeaponCreationService weaponService) {
        JTextField nameField = new JTextField("Custom Cannon", 20);
        JComboBox<CustomWeaponFamily> familyBox = combo(CustomWeaponFamily.values());
        JComboBox<CustomDamageProfile> damageProfileBox = combo(CustomDamageProfile.values());
        JComboBox<CustomTargetProfile> targetProfileBox = combo(CustomTargetProfile.values());
        JSpinner cooldown = new JSpinner(new SpinnerNumberModel(0.8, 0.08, 12.0, 0.05));
        JSpinner damage = new JSpinner(new SpinnerNumberModel(6, 1, 80, 1));
        JSpinner speed = new JSpinner(new SpinnerNumberModel(760.0, 80.0, 1200.0, 20.0));
        JSpinner range = new JSpinner(new SpinnerNumberModel(1200.0, 120.0, 3000.0, 50.0));
        styleField(nameField);
        styleSpinner(cooldown);
        styleSpinner(damage);
        styleSpinner(speed);
        styleSpinner(range);

        final Path[] turretPath = new Path[1];
        final Path[] projectilePath = new Path[1];
        JLabel turretLabel = new JLabel("No turret PNG selected");
        JLabel projectileLabel = new JLabel("No projectile PNG selected");
        turretLabel.setForeground(MUTED);
        projectileLabel.setForeground(MUTED);
        JButton turretButton = button("Turret PNG", new Color(58, 94, 128));
        JButton projectileButton = button("Projectile PNG", new Color(58, 94, 128));
        turretButton.addActionListener(e -> {
            Path path = choosePng(parent, "Choose turret sprite PNG");
            if (path != null) {
                turretPath[0] = path;
                turretLabel.setText(path.getFileName().toString());
            }
        });
        projectileButton.addActionListener(e -> {
            Path path = choosePng(parent, "Choose projectile sprite PNG");
            if (path != null) {
                projectilePath[0] = path;
                projectileLabel.setText(path.getFileName().toString());
            }
        });

        JPanel form = panel();
        form.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int row = 0;
        addRow(form, c, row++, "Name:", nameField);
        addRow(form, c, row++, "Family:", familyBox);
        addRow(form, c, row++, "Damage Profile:", damageProfileBox);
        addRow(form, c, row++, "Target Profile:", targetProfileBox);
        addRow(form, c, row++, "Cooldown Sec:", cooldown);
        addRow(form, c, row++, "Damage:", damage);
        addRow(form, c, row++, "Speed:", speed);
        addRow(form, c, row++, "Range:", range);
        addRow(form, c, row++, "Turret:", rowWith(turretButton, turretLabel));
        addRow(form, c, row, "Projectile:", rowWith(projectileButton, projectileLabel));

        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, form, "Weapon Lab - Direct Cannon",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            if (turretPath[0] == null || projectilePath[0] == null) {
                JOptionPane.showMessageDialog(parent,
                        "Choose both a turret PNG and projectile PNG.",
                        "Weapon Lab",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            try {
                CustomWeaponCreationService.CreationResult creation = weaponService.createFromPngs(
                        turretPath[0],
                        projectilePath[0],
                        new CustomWeaponGenerationRequest(
                                nameField.getText(),
                                selected(familyBox, CustomWeaponFamily.class, CustomWeaponFamily.KINETIC_CANNON),
                                CustomWeaponRuntimeBehavior.DIRECT_PROJECTILE,
                                selected(damageProfileBox, CustomDamageProfile.class, CustomDamageProfile.BALANCED),
                                selected(targetProfileBox, CustomTargetProfile.class, CustomTargetProfile.GENERAL_PURPOSE),
                                ((Number) cooldown.getValue()).doubleValue(),
                                ((Number) damage.getValue()).intValue(),
                                ((Number) speed.getValue()).doubleValue(),
                                ((Number) range.getValue()).doubleValue(),
                                1,
                                0.0,
                                1.0,
                                1.0));
                JOptionPane.showMessageDialog(parent,
                        creation.definition().displayName + " was added to the Weapon Lab.",
                        "Weapon Lab",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            } catch (RuntimeException | IOException ex) {
                JOptionPane.showMessageDialog(parent,
                        "Weapon import failed: " + ex.getMessage(),
                        "Weapon Lab",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void deleteSelected(JDialog dialog,
                                       CustomShipCreationService service,
                                       DefaultListModel<ShipListItem> shipModel,
                                       JList<ShipListItem> shipList) {
        ShipListItem selected = shipList.getSelectedValue();
        if (selected == null) return;
        int confirm = JOptionPane.showConfirmDialog(dialog,
                "Delete " + selected.definition.displayName + " from local Team E ships?",
                "Shipyard",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        try {
            service.delete(selected.definition.id);
            refreshShipList(service, shipModel, shipList, null);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(dialog,
                    "Delete failed: " + ex.getMessage(),
                    "Shipyard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void refreshShipList(CustomShipCreationService service,
                                        DefaultListModel<ShipListItem> model,
                                        JList<ShipListItem> list,
                                        UUID preferredSelection) {
        try {
            List<CustomShipDefinition> ships = service.savedShips();
            model.clear();
            int selectedIndex = -1;
            for (CustomShipDefinition definition : ships) {
                if (preferredSelection != null && preferredSelection.equals(definition.id)) {
                    selectedIndex = model.size();
                }
                model.addElement(new ShipListItem(definition));
            }
            if (model.isEmpty()) {
                list.clearSelection();
            } else {
                list.setSelectedIndex(selectedIndex >= 0 ? selectedIndex : 0);
            }
        } catch (IOException ex) {
            model.clear();
            JOptionPane.showMessageDialog(list,
                    "Could not load Team E ships: " + ex.getMessage(),
                    "Shipyard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void refreshWeaponCombo(CustomWeaponCreationService service,
                                           DefaultComboBoxModel<WeaponListItem> model,
                                           UUID preferredSelection) {
        WeaponListItem generated = WeaponListItem.generated();
        try {
            model.removeAllElements();
            model.addElement(generated);
            int selectedIndex = 0;
            List<CustomWeaponDefinition> weapons = service.savedWeapons();
            for (CustomWeaponDefinition definition : weapons) {
                if (!definition.validationFailures().isEmpty()) continue;
                WeaponListItem item = new WeaponListItem(definition);
                if (preferredSelection != null && preferredSelection.equals(definition.id)) {
                    selectedIndex = model.getSize();
                }
                model.addElement(item);
            }
            model.setSelectedItem(model.getElementAt(selectedIndex));
        } catch (IOException ex) {
            model.removeAllElements();
            model.addElement(generated);
        }
    }

    private static UUID selectedWeaponId(JComboBox<WeaponListItem> weaponBox) {
        Object selected = weaponBox == null ? null : weaponBox.getSelectedItem();
        if (selected instanceof WeaponListItem item && item.definition != null) {
            return item.definition.id;
        }
        return null;
    }

    private static Path choosePng(Component parent, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("PNG files", "png"));
        int result = chooser.showOpenDialog(parent);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private static void updatePreview(JLabel preview,
                                      CustomShipRegistry registry,
                                      ShipListItem item) {
        if (item == null) {
            preview.setIcon(null);
            preview.setText("No custom ship selected");
            return;
        }
        try {
            Path thumbnail = registry.resolveContentPath(item.definition, item.definition.thumbnailImagePath);
            BufferedImage image = ImageIO.read(thumbnail.toFile());
            if (image == null) throw new IOException("Thumbnail could not be decoded");
            int maxW = 280;
            int maxH = 180;
            double scale = Math.min(1.0, Math.min(maxW / (double) image.getWidth(), maxH / (double) image.getHeight()));
            Image scaled = image.getScaledInstance(
                    Math.max(1, (int) Math.round(image.getWidth() * scale)),
                    Math.max(1, (int) Math.round(image.getHeight() * scale)),
                    Image.SCALE_SMOOTH);
            preview.setIcon(new ImageIcon(scaled));
            preview.setText(item.definition.displayName);
            preview.setHorizontalTextPosition(SwingConstants.CENTER);
            preview.setVerticalTextPosition(SwingConstants.BOTTOM);
        } catch (RuntimeException | IOException ex) {
            preview.setIcon(null);
            preview.setText(item.definition.displayName + " preview unavailable");
        }
    }

    private static <E extends Enum<E>> JComboBox<E> combo(E[] values) {
        JComboBox<E> combo = new JComboBox<>(new DefaultComboBoxModel<>(values));
        styleComboBox(combo);
        return combo;
    }

    private static void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(new Color(6, 13, 21));
        combo.setForeground(TEXT);
        combo.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, 1.0));
    }

    private static <E> E selected(JComboBox<E> combo, Class<E> type, E fallback) {
        Object value = combo.getSelectedItem();
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String text, Component field) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0.0;
        c.anchor = GridBagConstraints.LINE_END;
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 13, 1.0));
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.LINE_START;
        panel.add(field, c);
    }

    private static JPanel panel() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        return panel;
    }

    private static JPanel transparentRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        return row;
    }

    private static JPanel rowWith(Component first, Component second) {
        JPanel row = transparentRow();
        row.add(first);
        row.add(second);
        return row;
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 18, 1.0));
        return label;
    }

    private static JButton button(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setFont(MenuDisplay.font("Consolas", Font.BOLD, 13, 1.0));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_ALT),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return button;
    }

    private static void styleField(JTextField field) {
        field.setBackground(new Color(6, 13, 21));
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, 1.0));
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, 1.0));
    }

    private static String pretty(Enum<?> value) {
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static final class ShipListItem {
        final CustomShipDefinition definition;

        ShipListItem(CustomShipDefinition definition) {
            this.definition = definition;
        }

        @Override
        public String toString() {
            return definition.displayName + " - " + pretty(definition.hullClass)
                    + " / " + pretty(definition.combatClassification)
                    + " / " + pretty(definition.weaponDoctrine);
        }
    }

    private static final class WeaponListItem {
        final CustomWeaponDefinition definition;

        WeaponListItem(CustomWeaponDefinition definition) {
            this.definition = definition;
        }

        static WeaponListItem generated() {
            return new WeaponListItem(null);
        }

        @Override
        public String toString() {
            if (definition == null) return "Generated Defaults";
            String shortId = definition.id == null ? "missing" : definition.id.toString().substring(0, 8);
            return definition.displayName + " - " + pretty(definition.family) + " / " + shortId;
        }
    }
}
