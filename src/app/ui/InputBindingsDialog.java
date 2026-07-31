package app.ui;

import app.support.MenuDisplay;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Main-menu controls editor. The game input registry currently lives in the
 * default package, so this packaged Swing class talks to it through reflection.
 */
final class InputBindingsDialog {
    static final String CONTROLS_BUTTON_NAME = "controlsSettingsButton";

    private InputBindingsDialog() {}

    static void show(Component parent, double uiScale) {
        try {
            ControlBridge bridge = ControlBridge.load();
            BindingTableModel model = new BindingTableModel(bridge.loadRows());
            JTable table = createTable(model, uiScale);
            JLabel status = statusLabel("Select a row, then rebind keyboard or mouse.", uiScale);

            JButton keyboardButton = button("Rebind Keyboard", uiScale);
            JButton mouseButton = button("Rebind Mouse", uiScale);
            JButton resetButton = button("Reset Defaults", uiScale);
            JButton saveButton = button("Save", uiScale);
            JButton cancelButton = button("Cancel", uiScale);

            keyboardButton.addActionListener(e -> {
                BindingRow row = selectedRow(table, model);
                if (row == null) {
                    status.setText("Select a control row first.");
                    return;
                }
                KeyStroke stroke = captureKey(parent, uiScale, row.action);
                if (stroke == null) return;
                row.keyboardSerialized = stroke.toString();
                row.keyboardLabel = keyLabel(stroke);
                model.fireTableDataChanged();
                selectAction(table, model, row.action);
                status.setText(displayAction(row.action) + " keyboard set to " + row.keyboardLabel + ".");
            });

            mouseButton.addActionListener(e -> {
                BindingRow row = selectedRow(table, model);
                if (row == null) {
                    status.setText("Select a control row first.");
                    return;
                }
                if (!row.mouseEditable) {
                    status.setText(displayAction(row.action) + " does not use a mouse binding.");
                    return;
                }
                Integer button = captureMouse(parent, uiScale, row.action);
                if (button == null) return;
                row.mouseButton = button;
                model.fireTableDataChanged();
                selectAction(table, model, row.action);
                status.setText(displayAction(row.action) + " mouse set to " + mouseLabel(button) + ".");
            });

            resetButton.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(parent,
                        "Reset every keyboard and mouse binding to defaults?",
                        "Reset Controls",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.OK_OPTION) return;
                try {
                    model.replaceRows(bridge.loadDefaultRows());
                    status.setText("Defaults restored. Press Save to keep them.");
                } catch (Exception ex) {
                    status.setText("Could not restore defaults: " + ex.getMessage());
                }
            });

            final JDialog dialog = modalDialog(parent, "Controls", uiScale);
            saveButton.addActionListener(e -> {
                try {
                    ControlBridge.Result result = bridge.save(model.rows);
                    status.setText(result.message);
                    if (result.accepted) dialog.dispose();
                } catch (Exception ex) {
                    status.setText("Could not save controls: " + ex.getMessage());
                }
            });
            cancelButton.addActionListener(e -> dialog.dispose());

            JPanel content = new JPanel(new BorderLayout(0, MenuDisplay.scaled(10, uiScale)));
            content.setBorder(BorderFactory.createEmptyBorder(
                    MenuDisplay.scaled(12, uiScale),
                    MenuDisplay.scaled(12, uiScale),
                    MenuDisplay.scaled(12, uiScale),
                    MenuDisplay.scaled(12, uiScale)));
            content.setBackground(new Color(8, 15, 25));

            JLabel title = new JLabel("Controls");
            title.setForeground(Color.WHITE);
            title.setFont(MenuDisplay.font("Consolas", Font.BOLD, 22, uiScale));
            JLabel hint = new JLabel("Keyboard bindings are available for every listed action. Mouse bindings are editable where supported.");
            hint.setForeground(new Color(188, 210, 226));
            hint.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, uiScale));

            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(title);
            top.add(Box.createVerticalStrut(MenuDisplay.scaled(4, uiScale)));
            top.add(hint);

            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(MenuDisplay.scaled(920, uiScale), MenuDisplay.scaled(460, uiScale)));
            scroll.getViewport().setBackground(new Color(7, 13, 22));
            scroll.setBorder(BorderFactory.createLineBorder(new Color(78, 132, 158)));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, MenuDisplay.scaled(8, uiScale), 0));
            actions.setOpaque(false);
            actions.add(keyboardButton);
            actions.add(mouseButton);
            actions.add(resetButton);
            actions.add(saveButton);
            actions.add(cancelButton);

            JPanel bottom = new JPanel(new BorderLayout(MenuDisplay.scaled(10, uiScale), 0));
            bottom.setOpaque(false);
            bottom.add(status, BorderLayout.CENTER);
            bottom.add(actions, BorderLayout.EAST);

            content.add(top, BorderLayout.NORTH);
            content.add(scroll, BorderLayout.CENTER);
            content.add(bottom, BorderLayout.SOUTH);
            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Controls editor is unavailable: " + ex.getMessage(),
                    "Controls",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JTable createTable(BindingTableModel model, double uiScale) {
        JTable table = new JTable(model);
        table.setName("controlsBindingsTable");
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(MenuDisplay.scaled(28, uiScale));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, uiScale));
        table.getTableHeader().setFont(MenuDisplay.font("Consolas", Font.BOLD, 13, uiScale));
        table.getTableHeader().setBackground(new Color(17, 37, 54));
        table.getTableHeader().setForeground(new Color(235, 244, 250));
        table.setBackground(new Color(7, 13, 22));
        table.setForeground(new Color(221, 234, 242));
        table.setGridColor(new Color(48, 81, 102));
        table.setShowGrid(true);

        table.getColumnModel().getColumn(0).setPreferredWidth(MenuDisplay.scaled(170, uiScale));
        table.getColumnModel().getColumn(1).setPreferredWidth(MenuDisplay.scaled(80, uiScale));
        table.getColumnModel().getColumn(2).setPreferredWidth(MenuDisplay.scaled(120, uiScale));
        table.getColumnModel().getColumn(3).setPreferredWidth(MenuDisplay.scaled(100, uiScale));
        table.getColumnModel().getColumn(4).setPreferredWidth(MenuDisplay.scaled(450, uiScale));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setOpaque(true);
        renderer.setVerticalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, renderer);
        return table;
    }

    private static BindingRow selectedRow(JTable table, BindingTableModel model) {
        if (table == null || model == null) return null;
        int selected = table.getSelectedRow();
        if (selected < 0) return null;
        int row = table.convertRowIndexToModel(selected);
        if (row < 0 || row >= model.rows.size()) return null;
        return model.rows.get(row);
    }

    private static void selectAction(JTable table, BindingTableModel model, String action) {
        if (table == null || model == null || action == null) return;
        for (int i = 0; i < model.rows.size(); i++) {
            if (!action.equals(model.rows.get(i).action)) continue;
            int view = table.convertRowIndexToView(i);
            if (view >= 0) table.getSelectionModel().setSelectionInterval(view, view);
            return;
        }
    }

    private static KeyStroke captureKey(Component parent, double uiScale, String action) {
        final KeyStroke[] out = new KeyStroke[1];
        JDialog dialog = modalDialog(parent, "Press Key", uiScale);
        JPanel panel = capturePanel("Press the new key for " + displayAction(action), uiScale);
        dialog.setContentPane(panel);
        KeyAdapter capture = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                out[0] = KeyStroke.getKeyStrokeForEvent(e);
                dialog.dispose();
            }
        };
        dialog.addKeyListener(capture);
        panel.addKeyListener(capture);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                panel.requestFocusInWindow();
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return out[0];
    }

    private static Integer captureMouse(Component parent, double uiScale, String action) {
        final Integer[] out = new Integer[1];
        JDialog dialog = modalDialog(parent, "Click Mouse Button", uiScale);
        JPanel panel = capturePanel("Click the new mouse button for " + displayAction(action), uiScale);
        panel.setFocusable(true);
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getButton() > 0) {
                    out[0] = e.getButton();
                    dialog.dispose();
                }
            }
        });
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return out[0];
    }

    private static JPanel capturePanel(String text, double uiScale) {
        JPanel panel = new JPanel(new GridLayout(1, 1));
        panel.setFocusable(true);
        panel.setPreferredSize(new Dimension(MenuDisplay.scaled(420, uiScale), MenuDisplay.scaled(120, uiScale)));
        panel.setBackground(new Color(8, 18, 30));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(83, 154, 185)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(16, uiScale),
                        MenuDisplay.scaled(16, uiScale),
                        MenuDisplay.scaled(16, uiScale),
                        MenuDisplay.scaled(16, uiScale))));
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(new Color(232, 242, 248));
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 15, uiScale));
        panel.add(label);
        return panel;
    }

    private static JDialog modalDialog(Component parent, String title, double uiScale) {
        Window owner = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame frame
                ? new JDialog(frame, title, true)
                : new JDialog((Frame) null, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, uiScale));
        return dialog;
    }

    private static JLabel statusLabel(String text, double uiScale) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(188, 220, 232));
        label.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, uiScale));
        return label;
    }

    private static JButton button(String text, double uiScale) {
        JButton button = new JButton(text);
        button.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, uiScale));
        button.setFocusable(true);
        return button;
    }

    private static String keyLabel(KeyStroke stroke) {
        if (stroke == null) return "";
        String modifiers = KeyEvent.getKeyModifiersText(stroke.getModifiers());
        String key = KeyEvent.getKeyText(stroke.getKeyCode());
        if (modifiers == null || modifiers.isBlank()) return key.toUpperCase(Locale.US);
        return (modifiers + "+" + key).toUpperCase(Locale.US);
    }

    private static String mouseLabel(Integer button) {
        if (button == null || button <= 0) return "";
        return switch (button) {
            case 1 -> "LMB";
            case 2 -> "MMB";
            case 3 -> "RMB";
            default -> "Mouse " + button;
        };
    }

    private static String displayAction(String action) {
        if (action == null || action.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        char[] chars = action.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (i > 0 && Character.isUpperCase(ch)) out.append(' ');
            out.append(i == 0 ? Character.toUpperCase(ch) : ch);
        }
        return out.toString();
    }

    private static final class BindingRow {
        final String action;
        final String scope;
        final String description;
        String keyboardSerialized;
        String keyboardLabel;
        Integer mouseButton;
        final boolean mouseEditable;

        BindingRow(String action, String scope, String keyboardSerialized, String keyboardLabel,
                   Integer mouseButton, boolean mouseEditable, String description) {
            this.action = action;
            this.scope = scope;
            this.keyboardSerialized = keyboardSerialized;
            this.keyboardLabel = keyboardLabel;
            this.mouseButton = mouseButton;
            this.mouseEditable = mouseEditable;
            this.description = description;
        }
    }

    private static final class BindingTableModel extends AbstractTableModel {
        private final String[] columns = {"Action", "Scope", "Keyboard", "Mouse", "What It Does"};
        private List<BindingRow> rows;

        BindingTableModel(List<BindingRow> rows) {
            this.rows = new ArrayList<>(rows);
        }

        void replaceRows(List<BindingRow> next) {
            rows = new ArrayList<>(next);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BindingRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> displayAction(row.action);
                case 1 -> row.scope;
                case 2 -> row.keyboardLabel;
                case 3 -> row.mouseEditable ? mouseLabel(row.mouseButton) : "";
                case 4 -> row.description;
                default -> "";
            };
        }
    }

    private static final class ControlBridge {
        private final Class<?> registry;
        private final Method bindings;
        private final Method defaultBindings;
        private final Method mouseActionIds;
        private final Method mouseButton;
        private final Method defaultMouseButton;
        private final Method actionDescription;
        private final Method replaceAllSerialized;

        private ControlBridge(Class<?> registry) throws NoSuchMethodException {
            this.registry = registry;
            bindings = registry.getMethod("bindings");
            defaultBindings = registry.getMethod("defaultBindings");
            mouseActionIds = registry.getMethod("mouseActionIds");
            mouseButton = registry.getMethod("mouseButton", String.class);
            defaultMouseButton = registry.getMethod("defaultMouseButton", String.class);
            actionDescription = registry.getMethod("actionDescription", String.class);
            replaceAllSerialized = registry.getMethod("replaceAllSerialized", Map.class, Map.class);
        }

        static ControlBridge load() throws Exception {
            return new ControlBridge(Class.forName("HotkeyRegistry"));
        }

        @SuppressWarnings("unchecked")
        List<BindingRow> loadRows() throws Exception {
            return loadRowsFrom((List<Object>) bindings.invoke(null), false);
        }

        @SuppressWarnings("unchecked")
        List<BindingRow> loadDefaultRows() throws Exception {
            return loadRowsFrom((List<Object>) defaultBindings.invoke(null), true);
        }

        @SuppressWarnings("unchecked")
        private List<BindingRow> loadRowsFrom(List<Object> bindingValues, boolean defaults) throws Exception {
            Set<String> mouseIds = new LinkedHashSet<>((List<String>) mouseActionIds.invoke(null));
            List<BindingRow> out = new ArrayList<>();
            for (Object binding : bindingValues) {
                Method actionMethod = accessible(binding.getClass().getMethod("action"));
                Method scopeMethod = accessible(binding.getClass().getMethod("scope"));
                Method strokeMethod = accessible(binding.getClass().getMethod("stroke"));
                Method labelMethod = accessible(binding.getClass().getMethod("label"));
                String action = String.valueOf(actionMethod.invoke(binding));
                Object stroke = strokeMethod.invoke(binding);
                Integer mouse = mouseIds.contains(action)
                        ? (Integer) (defaults ? defaultMouseButton : mouseButton).invoke(null, action)
                        : null;
                out.add(new BindingRow(
                        action,
                        String.valueOf(scopeMethod.invoke(binding)),
                        stroke == null ? "" : stroke.toString(),
                        String.valueOf(labelMethod.invoke(binding)),
                        mouse,
                        mouseIds.contains(action),
                        String.valueOf(actionDescription.invoke(null, action))));
            }
            return out;
        }

        Result save(List<BindingRow> rows) throws Exception {
            Map<String, String> keyboard = new LinkedHashMap<>();
            Map<String, Integer> mouse = new LinkedHashMap<>();
            for (BindingRow row : rows) {
                keyboard.put(row.action, row.keyboardSerialized);
                if (row.mouseEditable) mouse.put(row.action, row.mouseButton);
            }
            Object result = replaceAllSerialized.invoke(null, keyboard, mouse);
            Method acceptedMethod = accessible(result.getClass().getMethod("accepted"));
            Method messageMethod = accessible(result.getClass().getMethod("message"));
            boolean accepted = (Boolean) acceptedMethod.invoke(result);
            String message = String.valueOf(messageMethod.invoke(result));
            return new Result(accepted, message);
        }

        private static Method accessible(Method method) {
            method.setAccessible(true);
            return method;
        }

        private record Result(boolean accepted, String message) {}
    }
}
