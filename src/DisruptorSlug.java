import java.util.HashSet;
import java.util.Set;

public class DisruptorSlug extends Bullet {
    public final double angle;
    public final double blastRadius;
    private final Set<Integer> hitShipIds = new HashSet<>();

    public DisruptorSlug(double x,
                         double y,
                         double angle,
                         double dt,
                         double speed,
                         int damage,
                         int life,
                         double radius,
                         double blastRadius,
                         Faction faction) {
        super(x, y, angle, dt, speed, damage, life, radius, faction);
        this.angle = angle;
        this.blastRadius = Math.max(radius * 2.5, blastRadius);
    }

    public boolean canAffect(Ship ship) {
        if (ship == null) return false;
        return !hitShipIds.contains(ship.id);
    }

    public void markAffected(Ship ship) {
        if (ship == null) return;
        hitShipIds.add(ship.id);
    }
}
