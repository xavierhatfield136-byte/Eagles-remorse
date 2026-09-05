package app.ui;

import app.support.AppInfo;
import app.support.MenuDisplay;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class TitleSequencePanel extends JPanel implements ActionListener {
    private static final double FADE_IN_SEC = 0.8;
    private static final double HOLD_SEC = 2.2;
    private static final double FADE_OUT_SEC = 0.8;
    private static final double TOTAL_SEC = FADE_IN_SEC + HOLD_SEC + FADE_OUT_SEC;

    private final Runnable onComplete;
    private final Timer timer;

    private long lastTickNs = 0L;
    private double elapsedSec = 0.0;
    private boolean completed = false;

    public TitleSequencePanel(Runnable onComplete) {
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

    public void start() {
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

        Paint oldPaint = g2.getPaint();
        g2.setPaint(new GradientPaint(0, 0, new Color(5, 14, 28), 0, h, new Color(1, 3, 8)));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(oldPaint);

        int starCount = Math.max(34, w / 22);
        for (int i = 0; i < starCount; i++) {
            int sx = Math.floorMod(i * 97 + 31, Math.max(1, w));
            int sy = Math.floorMod(i * 53 + 17, Math.max(1, h));
            int a = 70 + Math.floorMod(i * 23, 112);
            g2.setColor(new Color(168, 204, 232, a));
            g2.fillRect(sx, sy, 1 + i % 2, 1 + (i + 1) % 2);
        }

        int centerY = h / 2;
        int horizonW = MenuDisplay.scaled(520, scale);
        int horizonY = centerY + MenuDisplay.scaled(82, scale);
        g2.setColor(new Color(255, 204, 112, 92));
        g2.drawLine((w - horizonW) / 2, horizonY, (w + horizonW) / 2, horizonY);
        g2.setColor(new Color(118, 204, 255, 80));
        g2.drawLine((w - horizonW) / 2 + MenuDisplay.scaled(42, scale), horizonY + MenuDisplay.scaled(12, scale),
                (w + horizonW) / 2 - MenuDisplay.scaled(42, scale), horizonY + MenuDisplay.scaled(12, scale));

        g2.setColor(new Color(142, 216, 255, 220));
        g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 18, scale));
        drawCentered(g2, "FLEET COMMAND INITIALIZING", w / 2, centerY - MenuDisplay.scaled(72, scale));

        g2.setColor(Color.WHITE);
        g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 64, scale));
        drawCentered(g2, AppInfo.APP_NAME.toUpperCase(), w / 2, centerY - MenuDisplay.scaled(8, scale));

        g2.setColor(new Color(216, 226, 236, 206));
        g2.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 18, scale));
        drawCentered(g2, "Campaign grid online. Tactical systems ready.", w / 2, centerY + MenuDisplay.scaled(44, scale));

        g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, scale));
        drawStatusChip(g2, "TACTICAL", w / 2 - MenuDisplay.scaled(156, scale), horizonY + MenuDisplay.scaled(26, scale), scale);
        drawStatusChip(g2, "CAMPAIGN", w / 2 - MenuDisplay.scaled(42, scale), horizonY + MenuDisplay.scaled(26, scale), scale);
        drawStatusChip(g2, "FLEET", w / 2 + MenuDisplay.scaled(78, scale), horizonY + MenuDisplay.scaled(26, scale), scale);

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

    private static void drawStatusChip(Graphics2D g2, String text, int x, int y, double scale) {
        FontMetrics fm = g2.getFontMetrics();
        int padX = MenuDisplay.scaled(10, scale);
        int chipW = fm.stringWidth(text) + padX * 2;
        int chipH = MenuDisplay.scaled(22, scale);
        g2.setColor(new Color(5, 10, 18, 178));
        g2.fillRoundRect(x, y, chipW, chipH, 8, 8);
        g2.setColor(new Color(134, 204, 238, 170));
        g2.drawRoundRect(x, y, chipW, chipH, 8, 8);
        g2.setColor(new Color(226, 238, 248, 218));
        g2.drawString(text, x + padX, y + (chipH + fm.getAscent()) / 2 - 2);
    }
}
