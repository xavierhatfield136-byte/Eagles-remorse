import java.util.HashSet;
import java.util.Set;

/**
 * Heavy superweapon projectile used by the wave-motion gun.
 * It pierces multiple targets and is intended to feel like a line-breaker shot.
 */
public class WaveMotionShot extends Projectile {
    public final double angle;
    private int remainingHits;
    private final Set<Integer> hitShipIds = new HashSet<>();

    public WaveMotionShot(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            int maxHits,
            Faction faction
    ) {
        super(
                x,
                y,
                Math.cos(angle) * speed * dt,
                Math.sin(angle) * speed * dt,
                Math.max(8.0, radius),
                Math.max(1, damage),
                Math.max(1, life),
                faction
        );
        this.angle = angle;
        this.remainingHits = Math.max(1, maxHits);
    }

    public boolean canDamage(Ship s) {
        if (s == null) return false;
        return !hitShipIds.contains(s.id);
    }

    public void markDamaged(Ship s) {
        if (s == null) return;
        hitShipIds.add(s.id);
    }

    public void consumeHit() {
        remainingHits--;
        if (remainingHits <= 0) {
            alive = false;
        }
    }
}
