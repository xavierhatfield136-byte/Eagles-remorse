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

        // Apply fullscreen choice before showing the game card.
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

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen) {
        this.mode = mode;
        this.worldW = worldW;
        this.worldH = worldH;
        this.randomEvents = randomEvents;
        this.seed = seed;
        this.fullscreen = fullscreen;
    }
}

/**
 * High-level game modes.
 */
enum GameMode {
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

/**
 * Simple main menu. Package-private to keep file count down.
 */
class MainMenuPanel extends JPanel {

    public MainMenuPanel(Consumer<GameConfig> onStart, Runnable onCredits, Runnable onQuit) {
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Color.BLACK);
        setFocusable(true);

        JLabel title = new JLabel(AppInfo.APP_NAME.toUpperCase());
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Consolas", Font.BOLD, 48));

        JComboBox<GameMode> modeBox = new JComboBox<>(GameMode.values());

        JComboBox<String> mapBox = new JComboBox<>(new String[]{
                "Small (5000 x 5000)",
                "Medium (10000 x 10000)",
                "Large (20000 x 20000)"
        });

        JCheckBox events = new JCheckBox("Enable Random Events");
        events.setOpaque(false);
        events.setForeground(Color.WHITE);
        events.setSelected(true);

        JCheckBox fullscreen = new JCheckBox("Start Fullscreen");
        fullscreen.setOpaque(false);
        fullscreen.setForeground(Color.WHITE);
        fullscreen.setSelected(false);

        JTextField seedField = new JTextField("0", 12);

        JButton start = new JButton("Start");
        JButton credits = new JButton("Credits");
        JButton quit = new JButton("Quit");
        JLabel versionLabel = new JLabel("Version " + AppInfo.VERSION);
        versionLabel.setForeground(new Color(180, 180, 180));
        versionLabel.setFont(new Font("Consolas", Font.PLAIN, 14));

        MenuSettingsStore.MenuSettings persisted = MenuSettingsStore.load();
        modeBox.setSelectedItem(MenuSettingsStore.resolveMode(persisted.modeName));
        mapBox.setSelectedIndex(Math.max(0, Math.min(mapBox.getItemCount() - 1, persisted.mapIndex)));
        events.setSelected(persisted.randomEvents);
        fullscreen.setSelected(persisted.fullscreen);
        seedField.setText(persisted.seedText);

        Runnable persistSettings = () -> {
            MenuSettingsStore.MenuSettings save = new MenuSettingsStore.MenuSettings();
            GameMode currentMode = (GameMode) modeBox.getSelectedItem();
            save.modeName = (currentMode == null) ? GameMode.CAMPAIGN_OPS.name() : currentMode.name();
            save.mapIndex = mapBox.getSelectedIndex();
            save.randomEvents = events.isSelected();
            save.fullscreen = fullscreen.isSelected();
            save.seedText = seedField.getText();
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

            persistSettings.run();
            onStart.accept(new GameConfig(mode, w, h, events.isSelected(), seed, fullscreen.isSelected()));
        };

        start.addActionListener(e -> startWithMode.accept(null));

        credits.addActionListener(e -> {
            persistSettings.run();
            onCredits.run();
        });

        quit.addActionListener(e -> {
            persistSettings.run();
            onQuit.run();
        });

        modeBox.addActionListener(e -> persistSettings.run());
        mapBox.addActionListener(e -> persistSettings.run());
        events.addActionListener(e -> persistSettings.run());
        fullscreen.addActionListener(e -> persistSettings.run());
        seedField.addActionListener(e -> persistSettings.run());
        seedField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistSettings.run();
            }
        });

        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        card.add(title, c);

        c.gridwidth = 1;
        c.gridy++;
        c.gridx = 0;
        card.add(label("Mode:"), c);
        c.gridx = 1;
        card.add(modeBox, c);

        c.gridy++;
        c.gridx = 0;
        card.add(label("Map Size:"), c);
        c.gridx = 1;
        card.add(mapBox, c);

        c.gridy++;
        c.gridx = 0;
        card.add(label("Seed:"), c);
        c.gridx = 1;
        card.add(seedField, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        card.add(events, c);

        c.gridy++;
        card.add(fullscreen, c);

        c.gridy++;
        c.gridwidth = 1;
        c.gridx = 0;
        card.add(start, c);
        c.gridx = 1;
        card.add(credits, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        card.add(quit, c);

        c.gridy++;
        card.add(versionLabel, c);

        setLayout(new GridBagLayout());
        add(card);

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
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(255, 255, 255, 210));
        l.setFont(new Font("Consolas", Font.PLAIN, 18));
        return l;
    }
}
