package app.support;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

public final class MenuDisplay {
    public static final int BASE_W = 1280;
    public static final int BASE_H = 720;

    private static final double MAX_SCREEN_W_FRACTION = 0.88;
    private static final double MAX_SCREEN_H_FRACTION = 0.84;
    private static final int MIN_LAYOUT_W = 640;
    private static final int MIN_LAYOUT_H = 360;

    private MenuDisplay() {}

    public static Dimension preferredWindowSize() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return new Dimension(BASE_W, BASE_H);
        }

        int maxW = safeAxis(bounds.width, BASE_W, MAX_SCREEN_W_FRACTION, MIN_LAYOUT_W);
        int maxH = safeAxis(bounds.height, BASE_H, MAX_SCREEN_H_FRACTION, MIN_LAYOUT_H);
        return fitToAspect(maxW, maxH);
    }

    public static double scaleFor(Dimension size) {
        if (size == null) return 1.0;
        return scaleFor(size.width, size.height);
    }

    public static double scaleFor(int width, int height) {
        if (width <= 0 || height <= 0) return 1.0;
        double sx = width / (double) BASE_W;
        double sy = height / (double) BASE_H;
        return Math.max(0.50, Math.min(1.0, Math.min(sx, sy)));
    }

    public static int scaled(int value, double scale) {
        return Math.max(1, (int) Math.round(value * scale));
    }

    public static Font font(String family, int style, int size, double scale) {
        return new Font(family, style, scaled(size, scale));
    }

    private static int safeAxis(int screen, int preferred, double fraction, int minLayout) {
        int fractionSize = (int) Math.floor(screen * fraction);
        int floor = Math.min(screen, minLayout);
        return Math.min(preferred, Math.max(floor, fractionSize));
    }

    private static Dimension fitToAspect(int maxW, int maxH) {
        if (maxW <= 0 || maxH <= 0) {
            return new Dimension(BASE_W, BASE_H);
        }

        double aspect = BASE_W / (double) BASE_H;
        int width = Math.min(maxW, (int) Math.floor(maxH * aspect));
        int height = (int) Math.round(width / aspect);
        if (height > maxH) {
            height = maxH;
            width = (int) Math.round(height * aspect);
        }

        width = Math.max(1, width);
        height = Math.max(1, height);
        return new Dimension(width, height);
    }
}
