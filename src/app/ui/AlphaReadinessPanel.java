package app.ui;

import app.support.AppInfo;
import app.support.MenuDisplay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public final class AlphaReadinessPanel extends JPanel {
    private static final String[] READY_ITEMS = {
            "Mission variety and information warfare are implemented through live campaign encounters.",
            "Travel, mining, salvage, resupply, refit, and fleet operations are reachable in normal play.",
            "Station damage, service loss, revisits, scars, and checkpoint persistence are wired.",
            "Keyboard-only controls, high contrast, captions, quiet mode, and 1280x720/1920x1080 readability passed owner review.",
            "Screenshot, audio, voice, save/load, HUD layout, and performance guardrail harnesses exist for release evidence."
    };

    private static final String[] BLOCKERS = {
            "Verify the new strike, ore, ammo, repair, and salvage pressure tuning in a longer campaign route.",
            "Record owner playtest notes for flagship loss, relay loss, retreat, and recovery.",
            "Replace or approve remaining disruptive damage visuals, props, portals, and map icons.",
            "Stabilize x-ray draw cost, room-hit timing, and Phase 9 hazard telemetry acceptance."
    };

    private static final String[] NEXT_ACTIONS = {
            "Run a campaign route with the new ore, ammo, repair, support, and strike costs and record feel notes.",
            "Use docs/ALPHA_PLAYTHROUGH_EVIDENCE.md and manual scripts to update any later playtest notes.",
            "Use the alpha asset report to approve, replace, or remove the remaining visual placeholders.",
            "Use RendererHudLayoutTest and screenshot captures if any new crowded HUD/menu states appear.",
            "Treat docs/2D_GAME_OPPORTUNITY_BACKLOG.md as the long-form checklist for follow-up work."
    };

    public AlphaReadinessPanel(Runnable onBack) {
        Dimension preferredSize = MenuDisplay.preferredWindowSize();
        double scale = MenuDisplay.scaleFor(preferredSize);

        setPreferredSize(preferredSize);
        setBackground(new Color(8, 12, 20));
        setFocusable(true);
        setLayout(new GridBagLayout());

        JPanel content = new JPanel(new BorderLayout(0, MenuDisplay.scaled(18, scale)));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(34, scale),
                MenuDisplay.scaled(46, scale),
                MenuDisplay.scaled(32, scale),
                MenuDisplay.scaled(46, scale)));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(label("Alpha Readiness", Font.BOLD, 46, Color.WHITE, scale));
        header.add(Box.createVerticalStrut(MenuDisplay.scaled(8, scale)));
        header.add(label(AppInfo.APP_NAME + " v" + AppInfo.VERSION, Font.PLAIN, 16,
                new Color(188, 207, 224), scale));

        JPanel columns = new JPanel(new GridLayout(1, 3, MenuDisplay.scaled(16, scale), 0));
        columns.setOpaque(false);
        columns.add(section("Verified", READY_ITEMS, new Color(54, 139, 95), scale));
        columns.add(section("Open Blockers", BLOCKERS, new Color(173, 91, 82), scale));
        columns.add(section("Next Actions", NEXT_ACTIONS, new Color(68, 118, 173), scale));

        JButton back = new JButton("Back");
        back.setFont(MenuDisplay.font("Consolas", Font.BOLD, 16, scale));
        back.setFocusPainted(false);
        back.setForeground(Color.WHITE);
        back.setBackground(new Color(36, 55, 76));
        back.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 156, 190)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(7, scale),
                        MenuDisplay.scaled(22, scale),
                        MenuDisplay.scaled(7, scale),
                        MenuDisplay.scaled(22, scale))));
        back.addActionListener(e -> onBack.run());

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(label("Esc returns to the main menu.", Font.PLAIN, 14,
                new Color(188, 201, 216), scale), BorderLayout.WEST);
        footer.add(back, BorderLayout.EAST);

        content.add(header, BorderLayout.NORTH);
        content.add(columns, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        add(content, constraints);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onBack.run();
            }
        });
    }

    private static JPanel section(String title, String[] items, Color accent, double scale) {
        JPanel panel = new JPanel(new BorderLayout(0, MenuDisplay.scaled(10, scale))) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(9, 19, 31, 222));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 190));
                g2.fillRect(0, 0, w, MenuDisplay.scaled(6, scale));
                g2.setColor(new Color(255, 255, 255, 44));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(18, scale),
                MenuDisplay.scaled(18, scale),
                MenuDisplay.scaled(18, scale),
                MenuDisplay.scaled(18, scale)));

        panel.add(label(title, Font.BOLD, 22, Color.WHITE, scale), BorderLayout.NORTH);

        JTextArea body = new JTextArea(formatItems(items));
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setForeground(new Color(219, 231, 242));
        body.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 14, scale));
        body.setMargin(new Insets(0, 0, 0, 0));
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel label(String text, int style, int size, Color color, double scale) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(MenuDisplay.font("Consolas", style, size, scale));
        return label;
    }

    private static String formatItems(String[] items) {
        StringBuilder out = new StringBuilder();
        for (String item : items) {
            if (out.length() > 0) out.append("\n\n");
            out.append("- ").append(item);
        }
        return out.toString();
    }
}
