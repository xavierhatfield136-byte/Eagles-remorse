/**
 * Backwards-compatible enemy ship type.
 *
 * The new system uses FleetShip for multiple ship roles; this class is kept so older
 * code still compiles and so you can spawn a basic enemy frigate quickly.
 */
public class EnemyShip extends FleetShip {

    public EnemyShip(double x, double y) {
        super(ShipRole.FRIGATE, Faction.ENEMY, x, y);
    }

    public void updateAI(Player player) {
        updateAI(1.0 / 60.0, player);
    }

    public void updateAI(double dt, Player player) {
        if (player == null) return;

        double dx = player.x - x;
        double dy = player.y - y;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 0.0001) {
            vx = 0;
            vy = 0;
            return;
        }

        double speed = desiredSpeed;
        vx = (dx / d) * speed * dt;
        vy = (dy / d) * speed * dt;
        angle = Math.atan2(dy, dx);
    }
}
