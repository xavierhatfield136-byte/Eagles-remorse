public final class DevTools {
    private DevTools() {}

    private static boolean debugOverlay = false;
    private static boolean aiEnabled = true;
    private static boolean fancyVfxEnabled = true;
    private static boolean asteroidHeatmapEnabled = false;
    private static boolean roomPolygonsEnabled = false;
    private static boolean roomImpactPointsEnabled = false;
    private static boolean roomHpBarsEnabled = false;
    private static boolean roomHazardsEnabled = false;

    private static final double[] SCALES = {1.0, 0.5, 0.25, 0.10};
    private static int scaleIndex = 0;

    public static boolean isDebugOverlay() { return debugOverlay; }
    public static boolean isAIEnabled() { return aiEnabled; }
    public static boolean isFancyVfxEnabled() { return fancyVfxEnabled; }
    public static boolean isAsteroidHeatmapEnabled() { return asteroidHeatmapEnabled; }
    public static boolean isRoomPolygonsEnabled() { return roomPolygonsEnabled; }
    public static boolean isRoomImpactPointsEnabled() { return roomImpactPointsEnabled; }
    public static boolean isRoomHpBarsEnabled() { return roomHpBarsEnabled; }
    public static boolean isRoomHazardsEnabled() { return roomHazardsEnabled; }

    public static void toggleDebugOverlay() { debugOverlay = !debugOverlay; }
    public static void toggleAIEnabled() { aiEnabled = !aiEnabled; }
    public static void toggleFancyVfx() { fancyVfxEnabled = !fancyVfxEnabled; }
    public static void toggleAsteroidHeatmap() { asteroidHeatmapEnabled = !asteroidHeatmapEnabled; }
    public static void toggleRoomPolygons() { roomPolygonsEnabled = !roomPolygonsEnabled; }
    public static void toggleRoomImpactPoints() { roomImpactPointsEnabled = !roomImpactPointsEnabled; }
    public static void toggleRoomHpBars() { roomHpBarsEnabled = !roomHpBarsEnabled; }
    public static void toggleRoomHazards() { roomHazardsEnabled = !roomHazardsEnabled; }

    public static double getTimeScale() { return SCALES[scaleIndex]; }

    public static void cycleTimeScale() {
        scaleIndex = (scaleIndex + 1) % SCALES.length;
    }

    /** Convenience if you ever want to call from a KeyListener path. */
    public static boolean handleKeyPressed(java.awt.event.KeyEvent e) {
        if (e == null) return false;
        int keyCode = e.getKeyCode();
        int mods = e.getModifiersEx();
        boolean ctrl = (mods & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0;
        if (ctrl) {
            if (keyCode == java.awt.event.KeyEvent.VK_F2) { toggleRoomPolygons(); return true; }
            if (keyCode == java.awt.event.KeyEvent.VK_F3) { toggleRoomImpactPoints(); return true; }
            if (keyCode == java.awt.event.KeyEvent.VK_F4) { toggleRoomHpBars(); return true; }
            if (keyCode == java.awt.event.KeyEvent.VK_F5) { toggleRoomHazards(); return true; }
        }
        if (keyCode == java.awt.event.KeyEvent.VK_F2) { toggleAsteroidHeatmap(); return true; }
        if (keyCode == java.awt.event.KeyEvent.VK_F3) { toggleDebugOverlay(); return true; }
        if (keyCode == java.awt.event.KeyEvent.VK_F4) { toggleAIEnabled(); return true; }
        if (keyCode == java.awt.event.KeyEvent.VK_F5) { cycleTimeScale(); return true; }
        return false;
    }
}
