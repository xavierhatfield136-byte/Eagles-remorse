package app.ui;

import app.config.GameConfig;
import app.config.GameMode;
import app.config.PlayerTeamChoice;
import app.persistence.MenuSettingsStore;
import app.support.AppInfo;
import app.support.MenuDisplay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public final class MainMenuPanel extends JPanel {
    private static final long MENU_BG_SEED = 0x5A17C0DEL;
    private static final String NO_CHECKPOINT_MESSAGE =
            "No checkpoint saved yet. Clear a sector in Campaign Ops to unlock resume.";
    private final long backgroundStartNs = System.nanoTime();
    private final Timer backgroundTimer;
    private final JButton continueCampaignButton;
    private final JLabel continueCampaignLabel;
    private final ResumeCampaignProvider resumeCampaignProvider;
    private final SpaceBackgroundPainter spaceBackgroundPainter;

    public MainMenuPanel(Consumer<GameConfig> onStart,
                         Runnable onCredits,
                         Runnable onQuit,
                         ResumeCampaignProvider resumeCampaignProvider,
                         SpaceBackgroundPainter spaceBackgroundPainter) {
        this.resumeCampaignProvider = resumeCampaignProvider;
        this.spaceBackgroundPainter = spaceBackgroundPainter;
        Dimension preferredSize = MenuDisplay.preferredWindowSize();
        double uiScale = MenuDisplay.scaleFor(preferredSize);

        setPreferredSize(preferredSize);
        setBackground(Color.BLACK);
        setFocusable(true);
        backgroundTimer = new Timer(33, e -> repaint());
        backgroundTimer.setCoalesce(true);
        backgroundTimer.start();

        JLabel title = new JLabel(AppInfo.APP_NAME.toUpperCase());
        title.setForeground(Color.WHITE);
        title.setFont(MenuDisplay.font("Consolas", Font.BOLD, 54, uiScale));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Bridge command. Fleet pressure. Tactical survival.");
        subtitle.setForeground(new Color(196, 220, 242, 210));
        subtitle.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 16, uiScale));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        GameMode[] missionModes = new GameMode[]{
                GameMode.LAST_STAND,
                GameMode.RESOURCE_RUSH,
                GameMode.FOUR_TEAM_DOMINATION,
                GameMode.SHOOTING_RANGE,
                GameMode.SHOWCASE
        };
        JComboBox<GameMode> modeBox = new JComboBox<>(missionModes);
        styleCombo(modeBox);
        scaleCombo(modeBox, uiScale);

        JComboBox<String> mapBox = new JComboBox<>(new String[]{
                "Small (5000 x 5000)",
                "Medium (10000 x 10000)",
                "Large (20000 x 20000)"
        });
        styleCombo(mapBox);
        scaleCombo(mapBox, uiScale);
        JComboBox<PlayerTeamChoice> teamBox = new JComboBox<>();
        styleCombo(teamBox);
        scaleCombo(teamBox, uiScale);

        JCheckBox events = new JCheckBox("Enable Random Events");
        styleCheckBox(events, uiScale);

        JTextField seedField = new JTextField("0", 12);
        styleField(seedField);
        scaleField(seedField, uiScale);

        JButton start = createMenuButton("Launch Mission", new Color(60, 123, 189), uiScale);
        JButton credits = createMenuButton("Credits", new Color(65, 77, 102), uiScale);
        JButton quit = createMenuButton("Quit", new Color(100, 63, 73), uiScale);
        continueCampaignButton = createMenuButton("Continue Campaign", new Color(87, 134, 91), uiScale);
        JButton fleet = createMenuButton("Fleet", new Color(74, 122, 168), uiScale);
        JButton campaignOps = createMenuButton("Campaign Ops", new Color(46, 97, 155), uiScale);
        JButton tutorialStart = createMenuButton("Start Command School", new Color(76, 132, 196), uiScale);
        JLabel versionLabel = new JLabel("Version " + AppInfo.VERSION);
        versionLabel.setForeground(new Color(188, 201, 216));
        versionLabel.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 14, uiScale));
        continueCampaignLabel = bodyLabel("", uiScale);
        continueCampaignLabel.setForeground(new Color(196, 219, 192, 220));

        JLabel fullscreenHint = metaLabel("Alt+Enter toggles fullscreen during battle", uiScale);
        JLabel singlePlayerLead = bodyLabel(
                "<html><div style='width:300px;'>"
                        + "Jump straight into command school, resume your latest checkpoint, or spin up Campaign Ops."
                        + "</div></html>", uiScale);
        JLabel missionLead = bodyLabel(
                "<html><div style='width:340px;'>"
                        + "Set the encounter type, map scale, player team, and seed from one clean launch console."
                        + "</div></html>", uiScale);

        MenuSettingsStore.MenuSettings persisted = MenuSettingsStore.load();
        GameMode persistedMode = MenuSettingsStore.resolveMode(persisted.modeName);
        modeBox.setSelectedItem(isMissionSetupMode(persistedMode) ? persistedMode : GameMode.LAST_STAND);
        mapBox.setSelectedIndex(Math.max(0, Math.min(mapBox.getItemCount() - 1, persisted.mapIndex)));
        syncTeamOptionsForMode((GameMode) modeBox.getSelectedItem(), teamBox, persisted.playerTeamId);
        events.setSelected(persisted.randomEvents);
        seedField.setText(persisted.seedText);

        java.util.function.Consumer<GameMode> persistSettings = (selectedMode) -> {
            MenuSettingsStore.MenuSettings save = new MenuSettingsStore.MenuSettings();
            GameMode currentMode = (selectedMode != null) ? selectedMode : (GameMode) modeBox.getSelectedItem();
            save.modeName = (currentMode == null) ? GameMode.CAMPAIGN_OPS.name() : currentMode.name();
            save.mapIndex = mapBox.getSelectedIndex();
            save.randomEvents = events.isSelected();
            save.seedText = seedField.getText();
            PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
            save.playerTeamId = (choice == null) ? 0 : choice.teamId();
            MenuSettingsStore.save(save);
        };

        java.util.function.Consumer<GameMode> startWithMode = (overrideMode) -> {
            GameMode mode = (overrideMode != null) ? overrideMode : (GameMode) modeBox.getSelectedItem();
            if (mode == null) mode = GameMode.CAMPAIGN_OPS;

            int w = 5000, h = 5000;
            if (mapBox.getSelectedIndex() == 1) { w = 10000; h = 10000; }
            if (mapBox.getSelectedIndex() == 2) { w = 20000; h = 20000; }

            long seed;
            try {
                seed = Long.parseLong(seedField.getText().trim());
            } catch (Exception ex) {
                seed = System.nanoTime();
            }
            if (seed == 0) seed = System.nanoTime();

            PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
            int playerTeamId = (choice == null) ? 0 : choice.teamId();
            persistSettings.accept(mode);
            boolean resumeCampaign = mode == GameMode.FLEET;
            onStart.accept(new GameConfig(mode, w, h, events.isSelected(), seed, false, playerTeamId, resumeCampaign));
        };

        start.addActionListener(e -> startWithMode.accept(null));

        credits.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            onCredits.run();
        });

        quit.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            onQuit.run();
        });

        modeBox.addActionListener(e -> {
            PlayerTeamChoice selected = (PlayerTeamChoice) teamBox.getSelectedItem();
            int preferredTeamId = (selected == null) ? 0 : selected.teamId();
            syncTeamOptionsForMode((GameMode) modeBox.getSelectedItem(), teamBox, preferredTeamId);
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
        });
        mapBox.addActionListener(e -> persistSettings.accept((GameMode) modeBox.getSelectedItem()));
        events.addActionListener(e -> persistSettings.accept((GameMode) modeBox.getSelectedItem()));
        seedField.addActionListener(e -> persistSettings.accept((GameMode) modeBox.getSelectedItem()));
        seedField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistSettings.accept((GameMode) modeBox.getSelectedItem());
            }
        });

        tutorialStart.addActionListener(e -> startWithMode.accept(GameMode.TUTORIAL));
        fleet.addActionListener(e -> {
            ResumeCampaignState checkpoint = loadResumeCampaignState();
            persistSettings.accept(GameMode.FLEET);
            if (checkpoint.available() && checkpoint.config() != null) {
                onStart.accept(checkpoint.config());
                return;
            }
            startWithMode.accept(GameMode.FLEET);
        });
        continueCampaignButton.addActionListener(e -> {
            ResumeCampaignState checkpoint = loadResumeCampaignState();
            if (!checkpoint.available() || checkpoint.config() == null) {
                refreshCampaignCheckpointState();
                return;
            }
            persistSettings.accept(GameMode.CAMPAIGN_OPS);
            onStart.accept(checkpoint.config());
        });
        campaignOps.addActionListener(e -> startWithMode.accept(GameMode.CAMPAIGN_OPS));

        JPanel headerPanel = transparentPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(eyebrowLabel("Fleet Command Interface", uiScale, new Color(129, 184, 237)));
        headerPanel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        headerPanel.add(title);
        headerPanel.add(Box.createVerticalStrut(MenuDisplay.scaled(6, uiScale)));
        headerPanel.add(subtitle);
        headerPanel.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));

        JPanel headerBadges = new FlowPanel(FlowLayout.LEFT, MenuDisplay.scaled(10, uiScale), 0);
        headerBadges.add(createBadge("Version " + AppInfo.VERSION, new Color(21, 41, 66, 220),
                new Color(101, 151, 211, 160), uiScale));
        headerBadges.add(createBadge("Single-Screen Command Deck", new Color(19, 34, 54, 210),
                new Color(80, 118, 170, 150), uiScale));
        headerPanel.add(headerBadges);

        JPanel missionForm = new JPanel(new GridBagLayout());
        missionForm.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(8, uiScale));
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.weightx = 0;
        missionForm.add(label("Mode:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(modeBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(label("Map Size:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(mapBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(label("Player Team:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(teamBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(label("Seed:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(seedField, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(events, c);

        JPanel missionFormShell = createSectionPanel(new Color(52, 88, 128, 118), uiScale);
        missionFormShell.add(missionForm);

        JPanel singlePlayerCard = createSectionPanel(new Color(70, 122, 182, 115), uiScale);
        singlePlayerCard.add(eyebrowLabel("Single Player", uiScale, new Color(129, 184, 237)));
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(6, uiScale)));
        singlePlayerCard.add(sectionTitle("Command Your Sector", uiScale));
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerCard.add(singlePlayerLead);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(18, uiScale)));
        singlePlayerCard.add(tutorialStart);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerCard.add(fleet);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerCard.add(continueCampaignButton);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        JPanel checkpointPanel = createInsetPanel(new Color(38, 70, 56, 120), new Color(94, 136, 101, 100), uiScale);
        checkpointPanel.add(continueCampaignLabel);
        singlePlayerCard.add(checkpointPanel);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(12, uiScale)));
        singlePlayerCard.add(campaignOps);

        JPanel missionCard = createSectionPanel(new Color(136, 92, 60, 118), uiScale);
        missionCard.add(eyebrowLabel("Mission Setup", uiScale, new Color(233, 173, 126)));
        missionCard.add(Box.createVerticalStrut(MenuDisplay.scaled(6, uiScale)));
        missionCard.add(sectionTitle("Build A Custom Engagement", uiScale));
        missionCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        missionCard.add(missionLead);
        missionCard.add(Box.createVerticalStrut(MenuDisplay.scaled(18, uiScale)));
        missionCard.add(missionFormShell);
        missionCard.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));

        JPanel missionActions = new JPanel(new GridLayout(1, 2, MenuDisplay.scaled(12, uiScale), 0));
        missionActions.setOpaque(false);
        missionActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        missionActions.add(start);
        missionActions.add(credits);
        missionCard.add(missionActions);

        JPanel mainColumns = new JPanel(new GridLayout(1, 2, MenuDisplay.scaled(18, uiScale), 0));
        mainColumns.setOpaque(false);
        mainColumns.add(singlePlayerCard);
        mainColumns.add(missionCard);

        JPanel footerPanel = transparentPanel();
        footerPanel.setLayout(new BorderLayout(MenuDisplay.scaled(12, uiScale), 0));
        footerPanel.add(quit, BorderLayout.WEST);
        JPanel footerInfo = transparentPanel();
        footerInfo.setLayout(new BoxLayout(footerInfo, BoxLayout.Y_AXIS));
        versionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        fullscreenHint.setAlignmentX(Component.RIGHT_ALIGNMENT);
        footerInfo.add(versionLabel);
        footerInfo.add(Box.createVerticalStrut(MenuDisplay.scaled(4, uiScale)));
        footerInfo.add(fullscreenHint);
        footerPanel.add(footerInfo, BorderLayout.EAST);

        JPanel rootContent = createMenuContent(uiScale);
        rootContent.add(headerPanel, BorderLayout.NORTH);
        rootContent.add(mainColumns, BorderLayout.CENTER);
        rootContent.add(footerPanel, BorderLayout.SOUTH);

        setLayout(new GridBagLayout());
        add(wrapMenuCard(rootContent, uiScale));

        // Convenience: Alt+Enter toggles fullscreen in-game, but in menu we can at least
        // show that this is the toggle key later (Step 3+).
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK), "noop");

        // Dev hotkey: quick-start Four Team Domination (uses current map size/seed settings).
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "dev_four_team");
        getActionMap().put("dev_four_team", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                startWithMode.accept(GameMode.FOUR_TEAM_DOMINATION);
            }
        });

        refreshCampaignCheckpointState();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();
        double t = (System.nanoTime() - backgroundStartNs) / 1_000_000_000.0;
        double camX = 900.0 + t * 16.0;
        double camY = 520.0 + Math.sin(t * 0.16) * 180.0;
        if (spaceBackgroundPainter != null) {
            spaceBackgroundPainter.paint(g2, camX, camY, w, h, MENU_BG_SEED);
        }
        drawMenuAtmosphere(g2, w, h, t);
        g2.dispose();
    }

    private JLabel label(String text, double scale) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(255, 255, 255, 210));
        l.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 17, scale));
        return l;
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setOpaque(true);
        combo.setFocusable(false);
        combo.setBackground(new Color(17, 27, 41));
        combo.setForeground(new Color(236, 242, 248));
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(88, 124, 159)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                label.setBackground(isSelected ? new Color(53, 98, 150) : new Color(17, 27, 41));
                label.setForeground(new Color(236, 242, 248));
                return label;
            }
        });
    }

    private static void styleField(JTextField field) {
        field.setBackground(new Color(17, 27, 41));
        field.setForeground(new Color(236, 242, 248));
        field.setCaretColor(Color.WHITE);
        field.setSelectionColor(new Color(90, 150, 205));
    }

    private static void styleCheckBox(JCheckBox checkBox, double scale) {
        checkBox.setOpaque(false);
        checkBox.setForeground(new Color(223, 232, 243));
        checkBox.setFont(MenuDisplay.font("Consolas", Font.BOLD, 14, scale));
        checkBox.setFocusPainted(false);
        checkBox.setSelected(true);
    }

    private static JButton createMenuButton(String text, Color fill, double scale) {
        return new MenuButton(text, fill, scale);
    }

    private static JPanel createMenuContent(double scale) {
        JPanel panel = new JPanel(new BorderLayout(0, MenuDisplay.scaled(22, scale)));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(
                0,
                0,
                0,
                0));
        return panel;
    }

    private static JPanel wrapMenuCard(JPanel content, double scale) {
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(8, 18, 32, 226), 0, h,
                        new Color(5, 12, 24, 214)));
                g2.fillRoundRect(0, 0, w, h, 30, 30);
                g2.setPaint(new GradientPaint(0, 0, new Color(76, 136, 204, 78), w, h,
                        new Color(230, 129, 86, 42)));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 30, 30);
                g2.setColor(new Color(255, 255, 255, 24));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 28, 28);
                g2.setPaint(new GradientPaint((float) (w * 0.08), (float) (h * 0.06), new Color(88, 164, 226, 58),
                        (float) (w * 0.42), (float) (h * 0.26), new Color(88, 164, 226, 0)));
                g2.fillRoundRect(MenuDisplay.scaled(24, scale), MenuDisplay.scaled(22, scale),
                        (int) Math.round(w * 0.36), MenuDisplay.scaled(110, scale), 28, 28);
                g2.setColor(new Color(255, 255, 255, 18));
                int separatorY = MenuDisplay.scaled(136, scale);
                g2.drawLine(MenuDisplay.scaled(34, scale), separatorY, w - MenuDisplay.scaled(34, scale), separatorY);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(34, scale),
                MenuDisplay.scaled(38, scale),
                MenuDisplay.scaled(28, scale),
                MenuDisplay.scaled(38, scale)));
        card.add(content);
        return card;
    }

    private static JLabel sectionTitle(String text, double scale) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 26, scale));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel bodyLabel(String html, double scale) {
        JLabel label = new JLabel(html);
        label.setForeground(new Color(214, 228, 242, 210));
        label.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static boolean isMissionSetupMode(GameMode mode) {
        return mode == GameMode.LAST_STAND
                || mode == GameMode.RESOURCE_RUSH
                || mode == GameMode.FOUR_TEAM_DOMINATION
                || mode == GameMode.SHOOTING_RANGE
                || mode == GameMode.SHOWCASE;
    }

    public void refreshCampaignCheckpointState() {
        ResumeCampaignState checkpoint = loadResumeCampaignState();
        continueCampaignButton.setEnabled(checkpoint.available() && checkpoint.config() != null);
        continueCampaignLabel.setText("<html><div style='width:300px;'>" + checkpoint.summaryText() + "</div></html>");
    }

    private ResumeCampaignState loadResumeCampaignState() {
        if (resumeCampaignProvider == null) {
            return ResumeCampaignState.unavailable(NO_CHECKPOINT_MESSAGE);
        }
        ResumeCampaignState checkpoint = resumeCampaignProvider.load();
        if (checkpoint == null) {
            return ResumeCampaignState.unavailable(NO_CHECKPOINT_MESSAGE);
        }
        return checkpoint;
    }

    private static void scaleCombo(JComboBox<?> combo, double scale) {
        combo.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        combo.setPreferredSize(new Dimension(MenuDisplay.scaled(240, scale), MenuDisplay.scaled(34, scale)));
    }

    private static void scaleField(JTextField field, double scale) {
        field.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(88, 124, 159)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(5, scale),
                        MenuDisplay.scaled(8, scale),
                        MenuDisplay.scaled(5, scale),
                        MenuDisplay.scaled(8, scale))
        ));
        field.setPreferredSize(new Dimension(MenuDisplay.scaled(240, scale), MenuDisplay.scaled(34, scale)));
        field.setColumns(Math.max(8, MenuDisplay.scaled(12, scale)));
    }

    private static void drawMenuAtmosphere(Graphics2D g2, int w, int h, double t) {
        g2.setColor(new Color(10, 18, 30, 92));
        g2.fillRect(0, 0, w, h);

        int glowW = (int) Math.round(Math.max(320.0, w * 0.28));
        int glowH = (int) Math.round(Math.max(240.0, h * 0.24));
        int leftGlowX = (int) Math.round(w * 0.10 + Math.sin(t * 0.22) * 16.0);
        int leftGlowY = (int) Math.round(h * 0.18 + Math.cos(t * 0.18) * 12.0);
        g2.setPaint(new GradientPaint(leftGlowX, leftGlowY, new Color(70, 150, 210, 96),
                leftGlowX + glowW, leftGlowY + glowH, new Color(70, 150, 210, 0)));
        g2.fillOval(leftGlowX - glowW / 2, leftGlowY - glowH / 2, glowW, glowH);

        int rightGlowX = (int) Math.round(w * 0.84 + Math.cos(t * 0.19) * 18.0);
        int rightGlowY = (int) Math.round(h * 0.74 + Math.sin(t * 0.21) * 10.0);
        g2.setPaint(new GradientPaint(rightGlowX, rightGlowY, new Color(255, 122, 82, 72),
                rightGlowX - glowW, rightGlowY - glowH, new Color(255, 122, 82, 0)));
        g2.fillOval(rightGlowX - glowW / 2, rightGlowY - glowH / 2, glowW, glowH);

        g2.setColor(new Color(255, 255, 255, 16));
        int lineY = (int) Math.round(h * 0.86);
        g2.drawLine((int) Math.round(w * 0.12), lineY, (int) Math.round(w * 0.88), lineY);

        Paint oldPaint = g2.getPaint();
        g2.setPaint(new GradientPaint(0, 0, new Color(4, 8, 16, 180), 0, h / 2f, new Color(4, 8, 16, 28)));
        g2.fillRect(0, 0, w, h / 2);
        g2.setPaint(new GradientPaint(0, h, new Color(3, 6, 12, 220), 0, h / 2f, new Color(3, 6, 12, 0)));
        g2.fillRect(0, h / 2, w, h / 2);
        g2.setPaint(new GradientPaint(0, 0, new Color(3, 8, 18, 150), w / 4f, 0, new Color(3, 8, 18, 0)));
        g2.fillRect(0, 0, w / 3, h);
        g2.setPaint(new GradientPaint(w, 0, new Color(18, 6, 7, 138), w * 0.72f, 0, new Color(18, 6, 7, 0)));
        g2.fillRect((int) Math.round(w * 0.67), 0, (int) Math.round(w * 0.33), h);
        g2.setPaint(new GradientPaint((float) (w * 0.12), (float) (h * 0.10), new Color(76, 134, 204, 42),
                (float) (w * 0.52), (float) (h * 0.26), new Color(76, 134, 204, 0)));
        g2.fillRoundRect((int) Math.round(w * 0.10), (int) Math.round(h * 0.08),
                (int) Math.round(w * 0.44), (int) Math.round(h * 0.23), 46, 46);
        g2.setPaint(oldPaint);
    }

    private static JPanel transparentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel eyebrowLabel(String text, double scale, Color color) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setForeground(color);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, scale));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel metaLabel(String text, double scale) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(167, 181, 198, 210));
        label.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, scale));
        return label;
    }

    private static JLabel createBadge(String text, Color fill, Color border, double scale) {
        JLabel badge = new JLabel(text);
        badge.setOpaque(true);
        badge.setBackground(fill);
        badge.setForeground(new Color(233, 240, 247));
        badge.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, scale));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(6, scale),
                        MenuDisplay.scaled(10, scale),
                        MenuDisplay.scaled(6, scale),
                        MenuDisplay.scaled(10, scale))));
        return badge;
    }

    private static JPanel createSectionPanel(Color accent, double scale) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(8, 18, 31, 196));
                g2.fillRoundRect(0, 0, w, h, 24, 24);
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, MenuDisplay.scaled(6, scale), h, 24, 24);
                g2.setColor(new Color(255, 255, 255, 20));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(20, scale),
                MenuDisplay.scaled(22, scale),
                MenuDisplay.scaled(20, scale),
                MenuDisplay.scaled(22, scale)));
        return panel;
    }

    private static JPanel createInsetPanel(Color fill, Color border, double scale) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(12, scale),
                MenuDisplay.scaled(14, scale),
                MenuDisplay.scaled(12, scale),
                MenuDisplay.scaled(14, scale)));
        return panel;
    }

    private static void syncTeamOptionsForMode(GameMode mode, JComboBox<PlayerTeamChoice> teamBox, int preferredTeamId) {
        if (teamBox == null) return;
        PlayerTeamChoice[] allowed = allowedTeamsForMode(mode);
        teamBox.removeAllItems();
        for (PlayerTeamChoice t : allowed) teamBox.addItem(t);

        PlayerTeamChoice preferred = PlayerTeamChoice.forTeamId(preferredTeamId);
        boolean found = false;
        for (PlayerTeamChoice t : allowed) {
            if (t.teamId() == preferred.teamId()) {
                found = true;
                break;
            }
        }
        teamBox.setSelectedItem(found ? preferred : allowed[0]);
        teamBox.setEnabled(allowed.length > 1);
    }

    private static PlayerTeamChoice[] allowedTeamsForMode(GameMode mode) {
        if (mode == null) return new PlayerTeamChoice[]{PlayerTeamChoice.TEAM_A};
        return switch (mode) {
            case RESOURCE_RUSH, SHOOTING_RANGE -> new PlayerTeamChoice[]{
                    PlayerTeamChoice.TEAM_A,
                    PlayerTeamChoice.TEAM_B,
                    PlayerTeamChoice.TEAM_C,
                    PlayerTeamChoice.TEAM_D
            };
            case FOUR_TEAM_DOMINATION -> new PlayerTeamChoice[]{
                    PlayerTeamChoice.TEAM_A,
                    PlayerTeamChoice.TEAM_B,
                    PlayerTeamChoice.TEAM_C,
                    PlayerTeamChoice.TEAM_D
            };
            default -> new PlayerTeamChoice[]{PlayerTeamChoice.TEAM_A};
        };
    }

    private static final class FlowPanel extends JPanel {
        FlowPanel(int align, int hgap, int vgap) {
            super(new FlowLayout(align, hgap, vgap));
            setOpaque(false);
        }
    }

    private static final class MenuButton extends JButton {
        private final Color baseFill;

        MenuButton(String text, Color fill, double scale) {
            super(text);
            this.baseFill = fill;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(MenuDisplay.font("Consolas", Font.BOLD, 16, scale));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(BorderFactory.createEmptyBorder(
                    MenuDisplay.scaled(11, scale),
                    MenuDisplay.scaled(18, scale),
                    MenuDisplay.scaled(11, scale),
                    MenuDisplay.scaled(18, scale)));
            setMargin(new Insets(0, 0, 0, 0));
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            Color fill = !isEnabled()
                    ? blend(baseFill, new Color(30, 38, 47), 0.55)
                    : model.isPressed()
                    ? blend(baseFill, Color.BLACK, 0.24)
                    : model.isRollover()
                    ? blend(baseFill, Color.WHITE, 0.14)
                    : baseFill;

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, 18, 18);
            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 48), 0, h / 2f, new Color(255, 255, 255, 0)));
            g2.fillRoundRect(0, 0, w, Math.max(1, h / 2), 18, 18);
            g2.setColor(new Color(255, 255, 255, isEnabled() ? 70 : 24));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    private static Color blend(Color base, Color mix, double mixAmount) {
        double clamped = Math.max(0.0, Math.min(1.0, mixAmount));
        double keep = 1.0 - clamped;
        int red = (int) Math.round(base.getRed() * keep + mix.getRed() * clamped);
        int green = (int) Math.round(base.getGreen() * keep + mix.getGreen() * clamped);
        int blue = (int) Math.round(base.getBlue() * keep + mix.getBlue() * clamped);
        int alpha = (int) Math.round(base.getAlpha() * keep + mix.getAlpha() * clamped);
        return new Color(red, green, blue, alpha);
    }

    @FunctionalInterface
    public interface ResumeCampaignProvider {
        ResumeCampaignState load();
    }

    @FunctionalInterface
    public interface SpaceBackgroundPainter {
        void paint(Graphics2D g2, double camX, double camY, int width, int height, long seed);
    }

    public record ResumeCampaignState(boolean available, String summaryText, GameConfig config) {
        public static ResumeCampaignState unavailable(String summaryText) {
            return new ResumeCampaignState(false, summaryText, null);
        }

        public static ResumeCampaignState available(String summaryText, GameConfig config) {
            return new ResumeCampaignState(true, summaryText, config);
        }
    }
}
