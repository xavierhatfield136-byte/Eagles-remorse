public final class World {

    public static final double WIDTH = 4000;
    public static final double HEIGHT = 4000;

    private static double camX;
    private static double camY;

    private static final int SCREEN_W = 1280;
    private static final int SCREEN_H = 720;

    private World() {}

    public static void updateCamera(Player player) {
        camX = player.x - SCREEN_W / 2.0;
        camY = player.y - SCREEN_H / 2.0;

        camX = clamp(camX, 0, WIDTH - SCREEN_W);
        camY = clamp(camY, 0, HEIGHT - SCREEN_H);
    }

    public static int sx(double worldX) {
        return (int) Math.round(worldX - camX);
    }

    public static int sy(double worldY) {
        return (int) Math.round(worldY - camY);
    }

    public static void keepInBounds(Ship s) {
        s.x = clamp(s.x, s.radius, WIDTH - s.radius);
        s.y = clamp(s.y, s.radius, HEIGHT - s.radius);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
