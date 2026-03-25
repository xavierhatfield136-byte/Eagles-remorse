import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class Main {
    private static final String CARD_TITLE = "title";
    private static final String CARD_MENU = "menu";
    private static final String CARD_GAME = "game";
    private static final String CARD_CREDITS = "credits";

    private final JFrame frame;
    private final CardLayout cards;
    private final JPanel root;

    private final TitleSequencePanel titlePanel;
    private final MainMenuPanel menuPanel;
    private final CreditsPanel creditsPanel;
    private GamePanel gamePanel;
    private String activeCard = CARD_MENU;

    // Fullscreen management (Swing)
    private final GraphicsDevice device;
    private boolean fullscreen = false;
    private Rectangle windowedBounds = null;

    public Main() {
        device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        frame = new JFrame(AppInfo.windowTitle());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cards = new CardLayout();
        root = new JPanel(cards);

        titlePanel = new TitleSequencePanel(this::showMenu);
        menuPanel = new MainMenuPanel(this::startGame, this::showCredits, () -> System.exit(0));
        creditsPanel = new CreditsPanel(this::showMenu);

        root.add(titlePanel, CARD_TITLE);
        root.add(menuPanel, CARD_MENU);
        root.add(creditsPanel, CARD_CREDITS);

        frame.setContentPane(root);
        frame.pack();
        frame.setResizable(true);
        frame.setLocationRelativeTo(null);
    }

    private void startGame(GameConfig config) {
        if (gamePanel != null) {
            gamePanel.shutdown();
            root.remove(gamePanel);
            gamePanel = null;
        }

        // Apply any requested display mode before showing the game card.
        setFullscreen(config.fullscreen);

        gamePanel = new GamePanel(config, this::showMenu, this::toggleFullscreen);
        root.add(gamePanel, CARD_GAME);

        showCard(CARD_GAME);
    }

    private void showMenu() {
        if (gamePanel != null) {
            gamePanel.shutdown();
            root.remove(gamePanel);
            gamePanel = null;
        }
        menuPanel.refreshCampaignCheckpointState();
        showCard(CARD_MENU);
    }

    private void showCredits() {
        showCard(CARD_CREDITS);
    }

    private void showCard(String cardName) {
        cards.show(root, cardName);
        activeCard = cardName;
        root.revalidate();
        root.repaint();
        SwingUtilities.invokeLater(() -> {
            switch (activeCard) {
                case CARD_GAME -> {
                    if (gamePanel != null) gamePanel.requestFocusInWindow();
                }
                case CARD_CREDITS -> creditsPanel.requestFocusInWindow();
                case CARD_TITLE -> titlePanel.requestFocusInWindow();
                default -> menuPanel.requestFocusInWindow();
            }
        });
    }

    private void toggleFullscreen() {
        setFullscreen(!fullscreen);
    }

    private void setFullscreen(boolean on) {
        if (on == fullscreen) return;

        if (on) {
            // Remember windowed bounds so we can restore them.
            windowedBounds = frame.getBounds();

            // Fullscreen requires undecorated; must dispose to change it.
            frame.dispose();
            frame.setUndecorated(true);
            frame.setVisible(true);

            device.setFullScreenWindow(frame);
            fullscreen = true;

        } else {
            // Exit fullscreen first.
            device.setFullScreenWindow(null);

            frame.dispose();
            frame.setUndecorated(false);
            frame.setVisible(true);

            if (windowedBounds != null) {
                frame.setBounds(windowedBounds);
            } else {
                frame.pack();
                frame.setLocationRelativeTo(null);
            }
            fullscreen = false;
        }

        // Re-validate layout after mode switch.
        root.revalidate();
        root.repaint();

        // Restore focus to the current panel.
        SwingUtilities.invokeLater(() -> {
            switch (activeCard) {
                case CARD_GAME -> {
                    if (gamePanel != null && root.isAncestorOf(gamePanel)) gamePanel.requestFocusInWindow();
                }
                case CARD_CREDITS -> creditsPanel.requestFocusInWindow();
                case CARD_TITLE -> titlePanel.requestFocusInWindow();
                default -> menuPanel.requestFocusInWindow();
            }
        });
    }

    public void showWindow() {
        frame.setVisible(true);
        if (AppInfo.SKIP_TITLE_SEQUENCE) {
            showMenu();
            return;
        }
        showCard(CARD_TITLE);
        titlePanel.start();
    }

    public static void main(String[] args) {
        SourceTreeHygiene.purgeDefaultSourceTreeArtifacts();
        ErrorLog.installGlobalHandler();
        SwingUtilities.invokeLater(() -> new Main().showWindow());
    }
}

/**
 * Game startup options selected in the main menu.
 * (Package-private so other files in the default package can use it.)
 */
class GameConfig {
    public final GameMode mode;
    public final int worldW;
    public final int worldH;
    public final boolean randomEvents;
    public final long seed;
    public final boolean fullscreen;
    public final int playerTeamId;
    public final boolean resumeCampaign;

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, 0, false);
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen, int playerTeamId) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, false);
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen, int playerTeamId, boolean resumeCampaign) {
        this.mode = mode;
        this.worldW = worldW;
        this.worldH = worldH;
        this.randomEvents = randomEvents;
        this.seed = seed;
        this.fullscreen = fullscreen;
        this.playerTeamId = Math.max(0, Math.min(3, playerTeamId));
        this.resumeCampaign = resumeCampaign;
    }
}

/**
 * High-level game modes.
 */
enum GameMode {
    TUTORIAL("Tutorial"),
    CAMPAIGN_OPS("Campaign Ops"),
    LAST_STAND("Last Stand"),
    RESOURCE_RUSH("Resource Rush"),
    FOUR_TEAM_DOMINATION("4 Team Domination"),
    SHOOTING_RANGE("Shooting Range"),
    SHOWCASE("Showcase");

    private final String label;

    GameMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

enum PlayerTeamChoice {
    TEAM_A("Blue Team", 0),
    TEAM_B("Red Team", 1),
    TEAM_C("Green Team", 2),
    TEAM_D("Yellow Team", 3);

    private final String label;
    private final int teamId;

    PlayerTeamChoice(String label, int teamId) {
        this.label = label;
        this.teamId = teamId;
    }

    public int teamId() {
        return teamId;
    }

    @Override
    public String toString() {
        return label;
    }

    public static PlayerTeamChoice forTeamId(int teamId) {
        for (PlayerTeamChoice c : values()) {
            if (c.teamId == teamId) return c;
        }
        return TEAM_A;
    }
}

/**
 * Simple main menu. Package-private to keep file count down.
 */
class MainMenuPanel extends JPanel {
    private static final long MENU_BG_SEED = 0x5A17C0DEL;
    private static final String CARD_ROOT = "root";
    private static final String CARD_TUTORIAL = "tutorial";
    private static final String CARD_SINGLE_PLAYER = "singlePlayer";
    private static final String CARD_MISSION_SETUP = "missionSetup";
    private final long backgroundStartNs = System.nanoTime();
    private final Timer backgroundTimer;
    private final JButton continueCampaignButton;
    private final JLabel continueCampaignLabel;
    private String activeMenuCard = CARD_ROOT;

    public MainMenuPanel(Consumer<GameConfig> onStart, Runnable onCredits, Runnable onQuit) {
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
        title.setFont(MenuDisplay.font("Consolas", Font.BOLD, 48, uiScale));

        JLabel subtitle = new JLabel("Bridge command. Fleet pressure. Tactical survival.");
        subtitle.setForeground(new Color(196, 220, 242, 210));
        subtitle.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 16, uiScale));

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
        events.setOpaque(false);
        events.setForeground(Color.WHITE);
        events.setSelected(true);

        JTextField seedField = new JTextField("0", 12);
        styleField(seedField);
        scaleField(seedField, uiScale);

        JButton start = new JButton("Start");
        JButton credits = new JButton("Credits");
        JButton quit = new JButton("Quit");
        styleButton(start, new Color(70, 122, 170));
        styleButton(credits, new Color(58, 72, 95));
        styleButton(quit, new Color(82, 54, 62));
        scaleButton(start, uiScale);
        scaleButton(credits, uiScale);
        scaleButton(quit, uiScale);
        JButton tutorialMenu = createMenuButton("Tutorial", new Color(70, 122, 170), uiScale);
        JButton singlePlayerMenu = createMenuButton("Single Player", new Color(58, 72, 95), uiScale);
        continueCampaignButton = createMenuButton("Continue Campaign", new Color(96, 132, 84), uiScale);
        JButton campaignOps = createMenuButton("Campaign Ops", new Color(70, 122, 170), uiScale);
        JButton missionSetup = createMenuButton("Mission Setup", new Color(58, 72, 95), uiScale);
        JButton tutorialStart = createMenuButton("Start Command School", new Color(70, 122, 170), uiScale);
        JButton backToRoot = createMenuButton("Back", new Color(82, 54, 62), uiScale);
        JButton backToSinglePlayer = createMenuButton("Back", new Color(82, 54, 62), uiScale);
        JLabel versionLabel = new JLabel("Version " + AppInfo.VERSION);
        versionLabel.setForeground(new Color(180, 180, 180));
        versionLabel.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 14, uiScale));
        continueCampaignLabel = sectionBody("", uiScale);
        continueCampaignLabel.setForeground(new Color(188, 214, 184, 220));

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
            onStart.accept(new GameConfig(mode, w, h, events.isSelected(), seed, false, playerTeamId));
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

        CardLayout menuCards = new CardLayout();
        JPanel cardHost = new JPanel(menuCards);
        cardHost.setOpaque(false);

        Runnable showRoot = () -> {
            menuCards.show(cardHost, CARD_ROOT);
            activeMenuCard = CARD_ROOT;
            requestFocusInWindow();
        };
        java.util.function.Consumer<String> showMenuCard = (cardName) -> {
            menuCards.show(cardHost, cardName);
            activeMenuCard = cardName;
            requestFocusInWindow();
        };

        tutorialMenu.addActionListener(e -> showMenuCard.accept(CARD_TUTORIAL));
        singlePlayerMenu.addActionListener(e -> showMenuCard.accept(CARD_SINGLE_PLAYER));
        tutorialStart.addActionListener(e -> startWithMode.accept(GameMode.TUTORIAL));
        continueCampaignButton.addActionListener(e -> {
            CampaignCheckpointStore.Checkpoint cp = CampaignCheckpointStore.load();
            if (cp == null) {
                refreshCampaignCheckpointState();
                return;
            }
            persistSettings.accept(GameMode.CAMPAIGN_OPS);
            onStart.accept(cp.toGameConfig());
        });
        campaignOps.addActionListener(e -> startWithMode.accept(GameMode.CAMPAIGN_OPS));
        missionSetup.addActionListener(e -> showMenuCard.accept(CARD_MISSION_SETUP));
        backToRoot.addActionListener(e -> showRoot.run());
        backToSinglePlayer.addActionListener(e -> showMenuCard.accept(CARD_SINGLE_PLAYER));

        JPanel rootContent = createMenuContent(uiScale);
        rootContent.add(title);
        rootContent.add(subtitle);
        rootContent.add(Box.createVerticalStrut(MenuDisplay.scaled(16, uiScale)));
        rootContent.add(tutorialMenu);
        rootContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        rootContent.add(singlePlayerMenu);
        rootContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        rootContent.add(credits);
        rootContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        rootContent.add(quit);
        rootContent.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));
        rootContent.add(versionLabel);

        JPanel tutorialContent = createMenuContent(uiScale);
        tutorialContent.add(sectionTitle("Tutorial", uiScale));
        tutorialContent.add(sectionBody(
                "<html><div style='text-align:center;'>"
                        + "The new command school is a guided bridge-operations run.<br>"
                        + "It teaches navigation, pings, combat, x-ray, mining, docking,<br>"
                        + "upgrades, carrier loadouts, bridge stations, hazards, and warp."
                        + "</div></html>", uiScale));
        tutorialContent.add(Box.createVerticalStrut(MenuDisplay.scaled(16, uiScale)));
        tutorialContent.add(tutorialStart);
        tutorialContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        tutorialContent.add(backToRoot);

        JPanel singlePlayerContent = createMenuContent(uiScale);
        singlePlayerContent.add(sectionTitle("Single Player", uiScale));
        singlePlayerContent.add(sectionBody(
                "<html><div style='text-align:center;'>"
                        + "Campaign Ops now supports sector checkpoints and resume.<br>"
                        + "Other modes still live behind a separate setup screen."
                        + "</div></html>", uiScale));
        singlePlayerContent.add(Box.createVerticalStrut(MenuDisplay.scaled(16, uiScale)));
        singlePlayerContent.add(continueCampaignButton);
        singlePlayerContent.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        singlePlayerContent.add(continueCampaignLabel);
        singlePlayerContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerContent.add(campaignOps);
        singlePlayerContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerContent.add(missionSetup);
        singlePlayerContent.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        singlePlayerContent.add(backToRoot);

        JPanel missionContent = createMenuContent(uiScale);
        missionContent.add(sectionTitle("Mission Setup", uiScale));
        missionContent.add(sectionBody(
                "<html><div style='text-align:center;'>"
                        + "Configure the non-campaign modes here.<br>"
                        + "This is the first step toward deeper per-mode menus."
                        + "</div></html>", uiScale));
        missionContent.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));

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
        missionForm.add(label("Mode:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        missionForm.add(modeBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        missionForm.add(label("Map Size:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        missionForm.add(mapBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        missionForm.add(label("Player Team:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        missionForm.add(teamBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        missionForm.add(label("Seed:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        missionForm.add(seedField, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        missionForm.add(events, c);

        c.gridy++;
        c.gridwidth = 1;
        c.gridx = 0;
        missionForm.add(start, c);
        c.gridx = 1;
        missionForm.add(backToSinglePlayer, c);

        missionContent.add(missionForm);

        cardHost.add(wrapMenuCard(rootContent, uiScale), CARD_ROOT);
        cardHost.add(wrapMenuCard(tutorialContent, uiScale), CARD_TUTORIAL);
        cardHost.add(wrapMenuCard(singlePlayerContent, uiScale), CARD_SINGLE_PLAYER);
        cardHost.add(wrapMenuCard(missionContent, uiScale), CARD_MISSION_SETUP);

        setLayout(new GridBagLayout());
        add(cardHost);

        // Convenience: Alt+Enter toggles fullscreen in-game, but in menu we can at least
        // show that this is the toggle key later (Step 3+).
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK), "noop");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "menu_back");
        getActionMap().put("menu_back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (CARD_MISSION_SETUP.equals(activeMenuCard)) {
                    showMenuCard.accept(CARD_SINGLE_PLAYER);
                    return;
                }
                if (!CARD_ROOT.equals(activeMenuCard)) {
                    showRoot.run();
                }
            }
        });

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
        Renderer.drawSpaceBackground(g2, camX, camY, w, h, MENU_BG_SEED);
        drawMenuAtmosphere(g2, w, h, t);
        g2.dispose();
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(255, 255, 255, 210));
        l.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 18, MenuDisplay.scaleFor(getPreferredSize())));
        return l;
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(new Color(20, 28, 43));
        combo.setForeground(new Color(236, 242, 248));
    }

    private static void styleField(JTextField field) {
        field.setBackground(new Color(20, 28, 43));
        field.setForeground(new Color(236, 242, 248));
        field.setCaretColor(Color.WHITE);
        field.setSelectionColor(new Color(90, 150, 205));
    }

    private static void styleButton(JButton button, Color fill) {
        button.setBackground(fill);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
    }

    private static JButton createMenuButton(String text, Color fill, double scale) {
        JButton button = new JButton(text);
        styleButton(button, fill);
        scaleButton(button, scale);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        return button;
    }

    private static JPanel createMenuContent(double scale) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(6, scale),
                MenuDisplay.scaled(6, scale),
                MenuDisplay.scaled(6, scale),
                MenuDisplay.scaled(6, scale)));
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
                g2.setColor(new Color(7, 16, 28, 190));
                g2.fillRoundRect(0, 0, w, h, 30, 30);
                g2.setColor(new Color(130, 190, 235, 70));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 30, 30);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 28, 28);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(28, scale),
                MenuDisplay.scaled(34, scale),
                MenuDisplay.scaled(26, scale),
                MenuDisplay.scaled(34, scale)));
        card.add(content);
        return card;
    }

    private static JLabel sectionTitle(String text, double scale) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 32, scale));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private static JLabel sectionBody(String html, double scale) {
        JLabel label = new JLabel(html);
        label.setForeground(new Color(214, 228, 242, 210));
        label.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
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
        CampaignCheckpointStore.Checkpoint cp = CampaignCheckpointStore.load();
        boolean hasCheckpoint = cp != null;
        continueCampaignButton.setEnabled(hasCheckpoint);
        continueCampaignLabel.setText(hasCheckpoint
                ? "<html><div style='text-align:center;'>" + cp.menuSummary() + "</div></html>"
                : "<html><div style='text-align:center;'>No checkpoint saved yet. Clear a sector in Campaign Ops to unlock resume.</div></html>");
    }

    private static void scaleCombo(JComboBox<?> combo, double scale) {
        combo.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
    }

    private static void scaleField(JTextField field, double scale) {
        field.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(116, 154, 190)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(5, scale),
                        MenuDisplay.scaled(8, scale),
                        MenuDisplay.scaled(5, scale),
                        MenuDisplay.scaled(8, scale))
        ));
        field.setColumns(Math.max(8, MenuDisplay.scaled(12, scale)));
    }

    private static void scaleButton(JButton button, double scale) {
        Color fill = button.getBackground();
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fill.brighter()),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(7, scale),
                        MenuDisplay.scaled(16, scale),
                        MenuDisplay.scaled(7, scale),
                        MenuDisplay.scaled(16, scale))
        ));
        button.setFont(MenuDisplay.font("Consolas", Font.BOLD, 16, scale));
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
        g2.setPaint(oldPaint);
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
}
