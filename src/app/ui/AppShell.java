package app.ui;

import app.config.GameConfig;
import app.support.AppInfo;

import javax.swing.*;
import java.awt.*;

/**
 * Packaged Swing shell for the title, menu, credits, and active game view.
 */
public final class AppShell {
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
    private final GameViewFactory gameViewFactory;
    private GameView gameView;
    private String activeCard = CARD_MENU;

    private final GraphicsDevice device;
    private boolean fullscreen = false;
    private Rectangle windowedBounds = null;

    public AppShell(GameViewFactory gameViewFactory,
                    MainMenuPanel.ResumeCampaignProvider resumeCampaignProvider,
                    MainMenuPanel.SpaceBackgroundPainter spaceBackgroundPainter,
                    Runnable quitAction) {
        this.gameViewFactory = gameViewFactory;
        this.device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        frame = new JFrame(AppInfo.windowTitle());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cards = new CardLayout();
        root = new JPanel(cards);

        titlePanel = new TitleSequencePanel(this::showMenu);
        menuPanel = new MainMenuPanel(this::startGame, this::showCredits, quitAction,
                resumeCampaignProvider, spaceBackgroundPainter);
        creditsPanel = new CreditsPanel(this::showMenu);

        root.add(titlePanel, CARD_TITLE);
        root.add(menuPanel, CARD_MENU);
        root.add(creditsPanel, CARD_CREDITS);

        frame.setContentPane(root);
        frame.pack();
        frame.setResizable(true);
        frame.setLocationRelativeTo(null);
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

    private void startGame(GameConfig config) {
        removeActiveGameView();
        setFullscreen(config.fullscreen);

        gameView = gameViewFactory.create(config, this::showMenu, this::toggleFullscreen);
        root.add(gameView.component(), CARD_GAME);
        showCard(CARD_GAME);
    }

    private void showMenu() {
        removeActiveGameView();
        menuPanel.refreshCampaignCheckpointState();
        showCard(CARD_MENU);
    }

    private void showCredits() {
        showCard(CARD_CREDITS);
    }

    private void removeActiveGameView() {
        if (gameView == null) return;
        gameView.shutdown();
        root.remove(gameView.component());
        gameView = null;
    }

    private void showCard(String cardName) {
        cards.show(root, cardName);
        activeCard = cardName;
        root.revalidate();
        root.repaint();
        SwingUtilities.invokeLater(this::restoreFocusForActiveCard);
    }

    private void toggleFullscreen() {
        setFullscreen(!fullscreen);
    }

    private void setFullscreen(boolean on) {
        if (on == fullscreen) return;

        if (on) {
            windowedBounds = frame.getBounds();
            frame.dispose();
            frame.setUndecorated(true);
            frame.setVisible(true);
            device.setFullScreenWindow(frame);
            fullscreen = true;
        } else {
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

        root.revalidate();
        root.repaint();
        SwingUtilities.invokeLater(this::restoreFocusForActiveCard);
    }

    private void restoreFocusForActiveCard() {
        switch (activeCard) {
            case CARD_GAME -> {
                if (gameView != null && root.isAncestorOf(gameView.component())) {
                    gameView.requestFocusInWindow();
                }
            }
            case CARD_CREDITS -> creditsPanel.requestFocusInWindow();
            case CARD_TITLE -> titlePanel.requestFocusInWindow();
            default -> menuPanel.requestFocusInWindow();
        }
    }

    public interface GameViewFactory {
        GameView create(GameConfig config, Runnable showMenu, Runnable toggleFullscreen);
    }

    public interface GameView {
        JComponent component();

        void shutdown();

        void requestFocusInWindow();
    }
}
