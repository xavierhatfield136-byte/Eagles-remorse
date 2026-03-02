public final class DevTools {
    private DevTools() {}

    private static boolean debugOverlay = false;
    private static boolean aiEnabled = true;
    private static boolean fancyVfxEnabled = true;
    private static boolean asteroidHeatmapEnabled = false;

    private static final double[] SCALES = {1.0, 0.5, 0.25, 0.10};
    private static int scaleIndex = 0;

    public static boolean isDebugOverlay() { return debugOverlay; }
    public static boolean isAIEnabled() { return aiEnabled; }
    public static boolean isFancyVfxEnabled() { return fancyVfxEnabled; }
    public static boolean isAsteroidHeatmapEnabled() { return asteroidHeatmapEnabled; }

    public static void toggleDebugOverlay() { debugOverlay = !debugOverlay; }
    public static void toggleAIEnabled() { aiEnabled = !aiEnabled; }
    public static void toggleFancyVfx() { fancyVfxEnabled = !fancyVfxEnabled; }
    public static void toggleAsteroidHeatmap() { asteroidHeatmapEnabled = !asteroidHeatmapEnabled; }

    public static double getTimeScale() { return SCALES[scaleIndex]; }

    public static void cycleTimeScale() {
        scaleIndex = (scaleIndex + 1) % SCALES.length;
    }

    /** Convenience if you ever want to call from a KeyListener path. */
    public static boolean handleKeyPressed(int keyCode) {
        if (keyCode == java.awt.event.KeyEvent.VK_F2) { toggleAsteroidHeatmap(); return true; }
        if (keyCode == java.awt.event.KeyEvent.VK_F3) { toggleDebugOverlay(); return true; }
        if (keyCode == java.awt.event.KeyEvent.VK_F4) { toggleAIEnabled(); return true; }
        if (keyCode == java.awt.event.KeyEvent.VK_F5) { cycleTimeScale(); return true; }
        return false;
    }
}
