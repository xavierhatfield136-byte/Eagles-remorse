import java.util.Random;

/**
 * Tiny screen-shake helper.
 *
 * Main calls update(dt) each tick and then reads getOffsetX/Y().
 * Other systems can call kick(magnitude) when something impactful happens.
 */
public final class ScreenShake {

    private ScreenShake() {}

    private static final Random RNG = new Random();

    private static double timeLeft = 0.0;
    private static double mag = 0.0;

    private static double offX = 0.0;
    private static double offY = 0.0;
    private static double scale = 1.0;

    public static void setScale(double value) {
        scale = Math.max(0.0, Math.min(1.0, value));
    }

    public static void kick(double magnitude) {
        // stack a little, cap hard
        mag = Math.min(22.0, mag + magnitude * scale);
        timeLeft = Math.min(0.35, timeLeft + 0.12);
    }

    public static void update(double dt) {
        if (timeLeft <= 0) {
            offX = 0;
            offY = 0;
            mag = 0;
            return;
        }

        timeLeft -= dt;
        if (timeLeft < 0) timeLeft = 0;

        // decay magnitude quickly
        mag *= Math.pow(0.001, dt);

        offX = (RNG.nextDouble() - 0.5) * 2.0 * mag;
        offY = (RNG.nextDouble() - 0.5) * 2.0 * mag;
    }

    public static double getOffsetX() { return offX; }
    public static double getOffsetY() { return offY; }
}
