/**
 * Short-lived point-defense laser pulse used by Team C CIWS.
 *
 * It visually links a firing ship to an incoming missile and applies a small
 * amount of intercept damage on contact resolution.
 */
public class PointDefenseLaser extends Projectile {
    private final Missile target;

    public double endX;
    public double endY;
    public double width;

    public PointDefenseLaser(
            double startX,
            double startY,
            Missile target,
            int damage,
            int life,
            double width,
            Faction faction
    ) {
        super(
                startX,
                startY,
                0.0,
                0.0,
                Math.max(0.8, width),
                Math.max(1, damage),
                Math.max(1, life),
                faction
        );
        this.target = target;
        this.width = Math.max(0.8, width);
        syncEndpoint();
    }

    @Override
    public void update(double dt) {
        syncEndpoint();
        life--;
        if (life <= 0) alive = false;
        if (target == null || !target.alive) alive = false;
    }

    public Missile target() {
        return target;
    }

    public double startX() {
        return x;
    }

    public double startY() {
        return y;
    }

    private void syncEndpoint() {
        if (target == null) {
            endX = x;
            endY = y;
            return;
        }
        endX = target.x;
        endY = target.y;
    }
}

