import java.util.HashSet;
import java.util.Set;

/**
 * Heavy projectile used by the superweapon.
 * It pierces multiple targets and is intended to feel like a line-breaker shot.
 */
public class SuperweaponShot extends Projectile {
    public final double angle;
    private int remainingHits;
    private final Set<Integer> hitShipIds = new HashSet<>();
    private final Set<Integer> hitAsteroidIds = new HashSet<>();

    public SuperweaponShot(
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

    public boolean canDamage(Asteroid a) {
        if (a == null) return false;
        return !hitAsteroidIds.contains(a.id);
    }

    public void markDamaged(Asteroid a) {
        if (a == null) return;
        hitAsteroidIds.add(a.id);
    }

    public void consumeHit() {
        remainingHits--;
        if (remainingHits <= 0) {
            alive = false;
        }
    }
}
