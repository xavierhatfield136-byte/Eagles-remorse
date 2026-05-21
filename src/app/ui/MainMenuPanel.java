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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class MainMenuPanel extends JPanel {
    private static final long MENU_BG_SEED = 0x5A17C0DEL;
    private static final String NO_CHECKPOINT_MESSAGE =
            "No checkpoint saved yet. Clear a sector in Campaign Ops to unlock resume.";
    private static final String[] CUSTOM_BATTLE_ROLE_IDS = {
            "PICKET", "PATROL", "STEALTH_SHIP", "FIGHTER", "BOMBER", "PD_CRAFT", "DRONE",
            "FRIGATE", "ARTILLERY_SHIP", "MISSILE_BOAT", "CIWS_CORVETTE", "LIGHT_CRUISER",
            "MEDIUM_CRUISER", "CRUISER", "BATTLECRUISER", "BATTLESHIP", "DREADNOUGHT",
            "SUPERSHIP", "TRANSPORT_TITAN", "BULWARK_TITAN", "CARRIER_SUPPORT_TITAN",
            "VANGUARD_TITAN", "INTERDICTION_TITAN", "COMMAND_INTEL_TITAN",
            "BOARDING_RECOVERY_TITAN", "ARTILLERY_TITAN", "SHIELD_BASTION_TITAN",
            "FLEET_TELEPORTER_TITAN", "ELITE_SUPERSHIP_COMMAND_TITAN",
            "ELITE_REINFORCEMENTS_TITAN", "MOBILE_STATION_TITAN", "HYPERWEAPON_TITAN",
            "MOTHERSHIP", "CARRIER", "DRONE_CARRIER", "TRANSPORT", "MINER", "HAULER",
            "BASE", "STATIC_TURRET"
    };
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
                GameMode.CUSTOM_BATTLES,
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

        JTextField seedField = new JTextField("0", 12);
        styleField(seedField);
        scaleField(seedField, uiScale);

        JButton start = createMenuButton("Launch Mission", new Color(60, 123, 189), uiScale);
        JButton credits = createMenuButton("Credits", new Color(65, 77, 102), uiScale);
        JButton quit = createMenuButton("Quit", new Color(100, 63, 73), uiScale);
        continueCampaignButton = createMenuButton("Continue Campaign", new Color(87, 134, 91), uiScale);
        JButton campaignOps = createMenuButton("Campaign Ops", new Color(46, 97, 155), uiScale);
        JButton galaxyMapTest = createMenuButton("Galaxy Map Test", new Color(83, 121, 188), uiScale);
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
        seedField.setText(persisted.seedText);

        java.util.function.Consumer<GameMode> persistSettings = (selectedMode) -> {
            MenuSettingsStore.MenuSettings save = new MenuSettingsStore.MenuSettings();
            GameMode currentMode = (selectedMode != null) ? selectedMode : (GameMode) modeBox.getSelectedItem();
            save.modeName = (currentMode == null) ? GameMode.CAMPAIGN_OPS.name() : currentMode.name();
            save.mapIndex = mapBox.getSelectedIndex();
            save.randomEvents = true;
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
            if (mode == GameMode.CUSTOM_BATTLES) {
                GameConfig customConfig = buildCustomBattleConfig(this, uiScale, w, h, seed, playerTeamId);
                if (customConfig == null) return;
                onStart.accept(customConfig);
                return;
            }
            onStart.accept(new GameConfig(mode, w, h, true, seed, false, playerTeamId, resumeCampaign));
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
        seedField.addActionListener(e -> persistSettings.accept((GameMode) modeBox.getSelectedItem()));
        seedField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistSettings.accept((GameMode) modeBox.getSelectedItem());
            }
        });

        tutorialStart.addActionListener(e -> startWithMode.accept(GameMode.TUTORIAL));
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
        galaxyMapTest.addActionListener(e -> {
            persistSettings.accept(GameMode.CAMPAIGN_OPS);
            int w = 20000;
            int h = 20000;
            long seed;
            try {
                seed = Long.parseLong(seedField.getText().trim());
            } catch (Exception ex) {
                seed = System.nanoTime();
            }
            if (seed == 0) seed = System.nanoTime();
            PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
            int playerTeamId = (choice == null) ? 0 : choice.teamId();
            onStart.accept(new GameConfig(
                    GameMode.CAMPAIGN_OPS,
                    w, h, true, seed, false,
                    playerTeamId, false,
                    1, "", "",
                    "galaxy_map_test"));
        });

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
        singlePlayerCard.add(continueCampaignButton);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        JPanel checkpointPanel = createInsetPanel(new Color(38, 70, 56, 120), new Color(94, 136, 101, 100), uiScale);
        checkpointPanel.add(continueCampaignLabel);
        singlePlayerCard.add(checkpointPanel);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(12, uiScale)));
        singlePlayerCard.add(campaignOps);
        singlePlayerCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerCard.add(galaxyMapTest);

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

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "dev_galaxy_map_test");
        getActionMap().put("dev_galaxy_map_test", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                galaxyMapTest.doClick();
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

    private static JLabel label(String text, double scale) {
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
                if (!paintThemedFrame(g2, ThemeArt.MENU_MAIN_SHELL, w, h,
                        new Color(8, 18, 32, 226), new Color(5, 12, 24, 214), 30)) {
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
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(ThemeArt.MENU_MAIN_SHELL, 1400, 940);
        card.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(metrics.top(), scale),
                MenuDisplay.scaled(metrics.left(), scale),
                MenuDisplay.scaled(metrics.bottom(), scale),
                MenuDisplay.scaled(metrics.right(), scale)));
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
                || mode == GameMode.CUSTOM_BATTLES
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

    private static GameConfig toFleetResumeConfig(GameConfig config) {
        if (config == null) return null;
        return new GameConfig(
                GameMode.FLEET,
                config.worldW,
                config.worldH,
                config.randomEvents,
                config.seed,
                config.fullscreen,
                config.playerTeamId,
                true);
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
                if (!paintThemedFrame(g2, ThemeArt.MENU_SECTION_PANEL, w, h,
                        new Color(8, 18, 31, 196), new Color(6, 14, 24, 182), 24)) {
                    g2.setColor(new Color(8, 18, 31, 196));
                    g2.fillRoundRect(0, 0, w, h, 24, 24);
                    g2.setColor(accent);
                    g2.fillRoundRect(0, 0, MenuDisplay.scaled(6, scale), h, 24, 24);
                    g2.setColor(new Color(255, 255, 255, 20));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 24, 24);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(ThemeArt.MENU_SECTION_PANEL, 1400, 940);
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(metrics.top(), scale),
                MenuDisplay.scaled(metrics.left(), scale),
                MenuDisplay.scaled(metrics.bottom(), scale),
                MenuDisplay.scaled(metrics.right(), scale)));
        return panel;
    }

    private static JPanel createInsetPanel(Color fill, Color border, double scale) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!paintThemedFrame(g2, ThemeArt.MENU_INSET_PANEL, getWidth(), getHeight(),
                        fill, darker(fill, 0.78f), 18)) {
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(ThemeArt.MENU_INSET_PANEL, 1240, 700);
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(metrics.top(), scale),
                MenuDisplay.scaled(metrics.left(), scale),
                MenuDisplay.scaled(metrics.bottom(), scale),
                MenuDisplay.scaled(metrics.right(), scale)));
        return panel;
    }

    private static boolean paintThemedFrame(Graphics2D g2, String slot, int w, int h,
                                            Color topFill, Color bottomFill, int arc) {
        if (g2 == null || w <= 0 || h <= 0) return false;
        Image image = ThemeArt.get(slot);
        if (image == null) return false;
        g2.setPaint(new GradientPaint(0, 0, topFill, 0, h, bottomFill));
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.drawImage(image, 0, 0, w, h, null);
        return true;
    }

    private static Color darker(Color color, float factor) {
        if (color == null) return new Color(8, 16, 24, 180);
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        return new Color(
                Math.max(0, Math.round(color.getRed() * factor)),
                Math.max(0, Math.round(color.getGreen() * factor)),
                Math.max(0, Math.round(color.getBlue() * factor)),
                color.getAlpha());
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
            case RESOURCE_RUSH, SHOOTING_RANGE, CUSTOM_BATTLES -> new PlayerTeamChoice[]{
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

    private static GameConfig buildCustomBattleConfig(Component parent,
                                                      double uiScale,
                                                      int worldW,
                                                      int worldH,
                                                      long seed,
                                                      int playerTeamId) {
        PlayerTeamChoice playerTeam = PlayerTeamChoice.forTeamId(playerTeamId);
        PlayerTeamChoice defaultEnemyTeam = defaultCustomBattleEnemyTeam(playerTeam);
        JComboBox<PlayerTeamChoice> enemyTeamBox = new JComboBox<>(PlayerTeamChoice.values());
        styleCombo(enemyTeamBox);
        scaleCombo(enemyTeamBox, uiScale);
        enemyTeamBox.setSelectedItem(defaultEnemyTeam);

        LinkedHashMap<String, JSpinner> friendlySpinners = createCustomBattleSpinners(uiScale);
        LinkedHashMap<String, JSpinner> enemySpinners = createCustomBattleSpinners(uiScale);
        applyCustomBattleDraft(CustomBattleDraft.createDefault(playerTeam.teamId(), defaultEnemyTeam.teamId()),
                friendlySpinners, enemySpinners, enemyTeamBox);

        JPanel form = new JPanel();
        form.setOpaque(true);
        form.setBackground(new Color(9, 17, 29));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(76, 113, 154)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(18, uiScale),
                        MenuDisplay.scaled(18, uiScale),
                        MenuDisplay.scaled(16, uiScale),
                        MenuDisplay.scaled(18, uiScale))));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(bodyLabel("<html><div style='width:620px;'>"
                + "Launch a battle sandbox with the player inside a mothership. Add exact hull counts to both sides and mix factions however you want."
                + "</div></html>", uiScale));
        form.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, MenuDisplay.scaled(12, uiScale), 0));
        topRow.setOpaque(true);
        topRow.setBackground(new Color(12, 23, 38));
        topRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(62, 94, 128)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(10, uiScale),
                        MenuDisplay.scaled(10, uiScale),
                        MenuDisplay.scaled(10, uiScale),
                        MenuDisplay.scaled(10, uiScale))));
        JLabel friendlyTeamLabel = label("Player Team: " + playerTeam, uiScale);
        friendlyTeamLabel.setForeground(new Color(214, 228, 242, 210));
        topRow.add(friendlyTeamLabel);
        topRow.add(label("Enemy Team:", uiScale));
        topRow.add(enemyTeamBox);
        form.add(topRow);
        form.add(Box.createVerticalStrut(MenuDisplay.scaled(12, uiScale)));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);
        tabs.setFont(MenuDisplay.font("Consolas", Font.BOLD, 13, uiScale));
        tabs.setBackground(new Color(10, 18, 30));
        tabs.setForeground(new Color(232, 239, 247));
        tabs.addTab("Friendly Fleet", wrapCustomBattleRosterPanel(buildCustomBattleRosterPanel(friendlySpinners, uiScale), uiScale));
        tabs.addTab("Enemy Fleet", wrapCustomBattleRosterPanel(buildCustomBattleRosterPanel(enemySpinners, uiScale), uiScale));
        form.add(tabs);
        form.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));

        JPanel toolsRow = transparentPanel();
        toolsRow.setLayout(new FlowLayout(FlowLayout.LEFT, MenuDisplay.scaled(10, uiScale), 0));
        JComboBox<CustomBattlePreset> presetBox = new JComboBox<>(CustomBattlePreset.values());
        styleCombo(presetBox);
        scaleCombo(presetBox, uiScale);
        presetBox.setSelectedItem(CustomBattlePreset.FULL_FLEET_BATTLE);
        JButton presetButton = createMenuButton("Apply Preset", new Color(80, 114, 170), uiScale);
        JButton clearButton = createMenuButton("Clear Counts", new Color(93, 72, 86), uiScale);
        presetButton.addActionListener(e -> applyCustomBattleDraft(
                ((CustomBattlePreset) presetBox.getSelectedItem()).createDraft(
                        playerTeam.teamId(),
                        ((PlayerTeamChoice) enemyTeamBox.getSelectedItem()).teamId()),
                friendlySpinners,
                enemySpinners,
                enemyTeamBox));
        presetBox.addActionListener(e -> applyCustomBattleDraft(
                ((CustomBattlePreset) presetBox.getSelectedItem()).createDraft(
                        playerTeam.teamId(),
                        ((PlayerTeamChoice) enemyTeamBox.getSelectedItem()).teamId()),
                friendlySpinners,
                enemySpinners,
                enemyTeamBox));
        clearButton.addActionListener(e -> clearCustomBattleSpinners(friendlySpinners, enemySpinners));
        toolsRow.add(presetBox);
        toolsRow.add(presetButton);
        toolsRow.add(clearButton);
        form.add(toolsRow);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    parent,
                    form,
                    "Custom Battles Setup",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            PlayerTeamChoice enemyTeam = (PlayerTeamChoice) enemyTeamBox.getSelectedItem();
            if (enemyTeam == null) enemyTeam = defaultEnemyTeam;
            if (enemyTeam.teamId() == playerTeam.teamId()) {
                JOptionPane.showMessageDialog(parent,
                        "Enemy team must be different from the player team.",
                        "Custom Battles",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            String friendlyRoster = serializeCustomBattleRoster(friendlySpinners);
            String enemyRoster = serializeCustomBattleRoster(enemySpinners);
            if (enemyRoster.isBlank()) {
                JOptionPane.showMessageDialog(parent,
                        "Add at least one enemy ship before launching.",
                        "Custom Battles",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            return new GameConfig(
                    GameMode.CUSTOM_BATTLES,
                    worldW,
                    worldH,
                    true,
                    seed,
                    false,
                    playerTeam.teamId(),
                    false,
                    enemyTeam.teamId(),
                    friendlyRoster,
                    enemyRoster);
        }
    }

    private static LinkedHashMap<String, JSpinner> createCustomBattleSpinners(double uiScale) {
        LinkedHashMap<String, JSpinner> spinners = new LinkedHashMap<>();
        for (String roleId : CUSTOM_BATTLE_ROLE_IDS) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 64, 1));
            styleSpinner(spinner, uiScale);
            spinners.put(roleId, spinner);
        }
        return spinners;
    }

    private static JPanel buildCustomBattleRosterPanel(LinkedHashMap<String, JSpinner> spinners, double uiScale) {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(12, 21, 35));
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(10, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(10, uiScale)));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(
                MenuDisplay.scaled(5, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(5, uiScale),
                MenuDisplay.scaled(8, uiScale));
        c.gridy = 0;

        int idx = 0;
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            int column = idx % 2;
            if (column == 0 && idx > 0) c.gridy++;
            c.gridx = column * 2;
            c.anchor = GridBagConstraints.LINE_END;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0.0;
            panel.add(label(formatCustomBattleRoleLabel(entry.getKey()) + ":", uiScale), c);

            c.gridx = column * 2 + 1;
            c.anchor = GridBagConstraints.LINE_START;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            panel.add(entry.getValue(), c);
            idx++;
        }
        return panel;
    }

    private static JScrollPane wrapCustomBattleRosterPanel(JPanel panel, double uiScale) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setOpaque(true);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(88, 124, 159)));
        scrollPane.getViewport().setBackground(new Color(12, 21, 35));
        scrollPane.getViewport().setOpaque(true);
        scrollPane.setBackground(new Color(12, 21, 35));
        scrollPane.getVerticalScrollBar().setUnitIncrement(MenuDisplay.scaled(16, uiScale));
        scrollPane.setPreferredSize(new Dimension(MenuDisplay.scaled(680, uiScale), MenuDisplay.scaled(360, uiScale)));
        return scrollPane;
    }

    private static void styleSpinner(JSpinner spinner, double uiScale) {
        spinner.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 14, uiScale));
        spinner.setPreferredSize(new Dimension(MenuDisplay.scaled(88, uiScale), MenuDisplay.scaled(30, uiScale)));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            styleField(field);
            field.setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    private static void applyCustomBattleDraft(CustomBattleDraft draft,
                                               LinkedHashMap<String, JSpinner> friendlySpinners,
                                               LinkedHashMap<String, JSpinner> enemySpinners,
                                               JComboBox<PlayerTeamChoice> enemyTeamBox) {
        if (draft == null) return;
        if (enemyTeamBox != null) {
            enemyTeamBox.setSelectedItem(PlayerTeamChoice.forTeamId(draft.enemyTeamId));
        }
        applyCustomBattleCounts(friendlySpinners, draft.friendlyCounts);
        applyCustomBattleCounts(enemySpinners, draft.enemyCounts);
    }

    private static void applyCustomBattleCounts(LinkedHashMap<String, JSpinner> spinners, LinkedHashMap<String, Integer> counts) {
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            int amount = 0;
            if (counts != null && counts.containsKey(entry.getKey())) {
                amount = Math.max(0, counts.get(entry.getKey()));
            }
            entry.getValue().setValue(amount);
        }
    }

    private static void clearCustomBattleSpinners(LinkedHashMap<String, JSpinner> friendlySpinners,
                                                  LinkedHashMap<String, JSpinner> enemySpinners) {
        applyCustomBattleCounts(friendlySpinners, new LinkedHashMap<>());
        applyCustomBattleCounts(enemySpinners, new LinkedHashMap<>());
    }

    private static String serializeCustomBattleRoster(LinkedHashMap<String, JSpinner> spinners) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            Object value = entry.getValue().getValue();
            int count = (value instanceof Number number) ? number.intValue() : 0;
            if (count <= 0) continue;
            if (out.length() > 0) out.append(';');
            out.append(entry.getKey()).append('=').append(count);
        }
        return out.toString();
    }

    private static PlayerTeamChoice defaultCustomBattleEnemyTeam(PlayerTeamChoice playerTeam) {
        if (playerTeam == null || playerTeam == PlayerTeamChoice.TEAM_A) return PlayerTeamChoice.TEAM_B;
        return PlayerTeamChoice.TEAM_A;
    }

    private static String formatCustomBattleRoleLabel(String roleId) {
        if (roleId == null || roleId.isBlank()) return "Ship";
        String[] parts = roleId.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private static final class CustomBattleDraft {
        final int friendlyTeamId;
        final int enemyTeamId;
        final LinkedHashMap<String, Integer> friendlyCounts;
        final LinkedHashMap<String, Integer> enemyCounts;

        private CustomBattleDraft(int friendlyTeamId,
                                  int enemyTeamId,
                                  LinkedHashMap<String, Integer> friendlyCounts,
                                  LinkedHashMap<String, Integer> enemyCounts) {
            this.friendlyTeamId = friendlyTeamId;
            this.enemyTeamId = enemyTeamId;
            this.friendlyCounts = friendlyCounts;
            this.enemyCounts = enemyCounts;
        }

        static CustomBattleDraft createDefault(int friendlyTeamId, int enemyTeamId) {
            LinkedHashMap<String, Integer> friendly = new LinkedHashMap<>();
            friendly.put("FRIGATE", 4);
            friendly.put("CIWS_CORVETTE", 2);
            friendly.put("LIGHT_CRUISER", 2);
            friendly.put("BATTLECRUISER", 1);
            friendly.put("CARRIER", 1);
            friendly.put("SUPERSHIP", 1);

            LinkedHashMap<String, Integer> enemy = new LinkedHashMap<>();
            enemy.put("FRIGATE", 6);
            enemy.put("MISSILE_BOAT", 3);
            enemy.put("LIGHT_CRUISER", 2);
            enemy.put("BATTLESHIP", 1);
            enemy.put("INTERDICTION_TITAN", 1);
            enemy.put("MOTHERSHIP", 1);

            return new CustomBattleDraft(friendlyTeamId, enemyTeamId, friendly, enemy);
        }
    }

    private enum CustomBattlePreset {
        LIGHT_WEIGHT("Light Weight"),
        MEDIUM("Medium"),
        FULL_FLEET_BATTLE("Full Fleet Battle"),
        THE_LAGGENING("The Laggening"),
        WHY("Why");

        private final String label;

        CustomBattlePreset(String label) {
            this.label = label;
        }

        CustomBattleDraft createDraft(int friendlyTeamId, int enemyTeamId) {
            return switch (this) {
                case LIGHT_WEIGHT -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FRIGATE", 3),
                                entry("CIWS_CORVETTE", 1),
                                entry("LIGHT_CRUISER", 1)
                        ),
                        mapOf(
                                entry("FRIGATE", 4),
                                entry("MISSILE_BOAT", 1),
                                entry("LIGHT_CRUISER", 1)
                        ));
                case MEDIUM -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FRIGATE", 6),
                                entry("CIWS_CORVETTE", 2),
                                entry("LIGHT_CRUISER", 2),
                                entry("BATTLECRUISER", 1),
                                entry("CARRIER", 1)
                        ),
                        mapOf(
                                entry("FRIGATE", 8),
                                entry("MISSILE_BOAT", 3),
                                entry("LIGHT_CRUISER", 2),
                                entry("BATTLECRUISER", 1),
                                entry("BATTLESHIP", 1)
                        ));
                case FULL_FLEET_BATTLE -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FRIGATE", 10),
                                entry("CIWS_CORVETTE", 4),
                                entry("MISSILE_BOAT", 3),
                                entry("LIGHT_CRUISER", 3),
                                entry("CRUISER", 2),
                                entry("BATTLECRUISER", 2),
                                entry("BATTLESHIP", 1),
                                entry("CARRIER", 1),
                                entry("SUPERSHIP", 1)
                        ),
                        mapOf(
                                entry("FRIGATE", 12),
                                entry("CIWS_CORVETTE", 4),
                                entry("MISSILE_BOAT", 4),
                                entry("LIGHT_CRUISER", 3),
                                entry("CRUISER", 2),
                                entry("BATTLECRUISER", 2),
                                entry("BATTLESHIP", 1),
                                entry("INTERDICTION_TITAN", 1),
                                entry("MOTHERSHIP", 1)
                        ));
                case THE_LAGGENING -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FIGHTER", 16),
                                entry("BOMBER", 10),
                                entry("DRONE", 12),
                                entry("FRIGATE", 16),
                                entry("CIWS_CORVETTE", 8),
                                entry("MISSILE_BOAT", 8),
                                entry("LIGHT_CRUISER", 6),
                                entry("CRUISER", 4),
                                entry("BATTLECRUISER", 3),
                                entry("BATTLESHIP", 2),
                                entry("CARRIER", 2),
                                entry("SUPERSHIP", 1),
                                entry("MOTHERSHIP", 1)
                        ),
                        mapOf(
                                entry("FIGHTER", 18),
                                entry("BOMBER", 12),
                                entry("DRONE", 14),
                                entry("FRIGATE", 18),
                                entry("CIWS_CORVETTE", 8),
                                entry("MISSILE_BOAT", 10),
                                entry("LIGHT_CRUISER", 6),
                                entry("CRUISER", 4),
                                entry("BATTLECRUISER", 3),
                                entry("BATTLESHIP", 2),
                                entry("DRONE_CARRIER", 2),
                                entry("INTERDICTION_TITAN", 1),
                                entry("HYPERWEAPON_TITAN", 1)
                        ));
                case WHY -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FIGHTER", 32),
                                entry("BOMBER", 20),
                                entry("PD_CRAFT", 16),
                                entry("DRONE", 24),
                                entry("FRIGATE", 26),
                                entry("CIWS_CORVETTE", 14),
                                entry("MISSILE_BOAT", 14),
                                entry("LIGHT_CRUISER", 10),
                                entry("CRUISER", 8),
                                entry("BATTLECRUISER", 5),
                                entry("BATTLESHIP", 4),
                                entry("DREADNOUGHT", 2),
                                entry("CARRIER", 3),
                                entry("DRONE_CARRIER", 2),
                                entry("SUPERSHIP", 2),
                                entry("MOTHERSHIP", 1),
                                entry("FLEET_TELEPORTER_TITAN", 1),
                                entry("ELITE_SUPERSHIP_COMMAND_TITAN", 1)
                        ),
                        mapOf(
                                entry("FIGHTER", 36),
                                entry("BOMBER", 24),
                                entry("PD_CRAFT", 18),
                                entry("DRONE", 28),
                                entry("FRIGATE", 30),
                                entry("CIWS_CORVETTE", 16),
                                entry("MISSILE_BOAT", 16),
                                entry("LIGHT_CRUISER", 12),
                                entry("CRUISER", 8),
                                entry("BATTLECRUISER", 6),
                                entry("BATTLESHIP", 4),
                                entry("DREADNOUGHT", 2),
                                entry("CARRIER", 2),
                                entry("DRONE_CARRIER", 3),
                                entry("SUPERSHIP", 2),
                                entry("MOTHERSHIP", 1),
                                entry("INTERDICTION_TITAN", 1),
                                entry("HYPERWEAPON_TITAN", 1),
                                entry("ELITE_REINFORCEMENTS_TITAN", 1)
                        ));
            };
        }

        @Override
        public String toString() {
            return label;
        }

        private static CustomBattleDraft draft(int friendlyTeamId,
                                               int enemyTeamId,
                                               LinkedHashMap<String, Integer> friendly,
                                               LinkedHashMap<String, Integer> enemy) {
            return new CustomBattleDraft(friendlyTeamId, enemyTeamId, friendly, enemy);
        }

        @SafeVarargs
        private static LinkedHashMap<String, Integer> mapOf(Map.Entry<String, Integer>... entries) {
            LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
            if (entries == null) return map;
            for (Map.Entry<String, Integer> entry : entries) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) continue;
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }

        private static Map.Entry<String, Integer> entry(String roleId, int count) {
            return Map.entry(roleId, count);
        }
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
