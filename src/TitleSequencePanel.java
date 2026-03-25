import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class TitleSequencePanel extends JPanel implements ActionListener {
    private static final double FADE_IN_SEC = 0.9;
    private static final double HOLD_SEC = 1.4;
    private static final double FADE_OUT_SEC = 0.9;
    private static final double TOTAL_SEC = FADE_IN_SEC + HOLD_SEC + FADE_OUT_SEC;

    private final Runnable onComplete;
    private final Timer timer;

    private long lastTickNs = 0L;
    private double elapsedSec = 0.0;
    private boolean completed = false;

    TitleSequencePanel(Runnable onComplete) {
        this.onComplete = onComplete;
        setPreferredSize(MenuDisplay.preferredWindowSize());
        setBackground(Color.BLACK);
        setFocusable(true);

        timer = new Timer(16, this);
        timer.setCoalesce(true);

        bindSkip(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        bindSkip(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        bindSkip(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        bindSkip(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                complete();
            }
        });
    }

    void start() {
        elapsedSec = 0.0;
        completed = false;
        lastTickNs = System.nanoTime();
        if (!timer.isRunning()) timer.start();
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        if (lastTickNs <= 0L) lastTickNs = now;
        double dt = (now - lastTickNs) / 1_000_000_000.0;
        lastTickNs = now;
        if (dt < 0.0) dt = 0.0;
        if (dt > 0.25) dt = 0.25;

        elapsedSec += dt;
        if (elapsedSec >= TOTAL_SEC) {
            complete();
            return;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        float alpha = alphaForTime(elapsedSec);
        double scale = MenuDisplay.scaleFor(w, h);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2.setColor(new Color(215, 215, 215));
        g2.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 28, scale));
        drawCentered(g2, "Xavier H. presents:", w / 2, h / 2 - MenuDisplay.scaled(40, scale));

        g2.setColor(Color.WHITE);
        g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 66, scale));
        drawCentered(g2, AppInfo.APP_NAME.toUpperCase(), w / 2, h / 2 + MenuDisplay.scaled(24, scale));

        float hintAlpha = Math.min(0.80f, alpha + 0.15f);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hintAlpha));
        g2.setColor(new Color(180, 180, 180));
        g2.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 16, scale));
        drawCentered(g2, "Press Enter/Space/Esc (or click) to skip", w / 2, h - MenuDisplay.scaled(42, scale));

        g2.dispose();
    }

    private void bindSkip(KeyStroke key) {
        String id = "skip_" + key.toString();
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, id);
        getActionMap().put(id, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                complete();
            }
        });
    }

    private void complete() {
        if (completed) return;
        completed = true;
        if (timer.isRunning()) timer.stop();
        onComplete.run();
    }

    private static float alphaForTime(double t) {
        if (t <= 0.0) return 0.0f;
        if (t < FADE_IN_SEC) return (float) (t / FADE_IN_SEC);
        double holdEnd = FADE_IN_SEC + HOLD_SEC;
        if (t < holdEnd) return 1.0f;
        if (t < TOTAL_SEC) return (float) ((TOTAL_SEC - t) / FADE_OUT_SEC);
        return 0.0f;
    }

    private static void drawCentered(Graphics2D g2, String text, int cx, int cy) {
        FontMetrics fm = g2.getFontMetrics();
        int x = cx - fm.stringWidth(text) / 2;
        int y = cy + fm.getAscent() / 2;
        g2.drawString(text, x, y);
    }
}
