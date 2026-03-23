public class DestabilizerPulse extends Bullet {
    public final double angle;
    public final double blastRadius;
    public final double shieldDamage;
    public final double destabilizeSeconds;

    public DestabilizerPulse(double x,
                             double y,
                             double angle,
                             double dt,
                             double speed,
                             int hullDamage,
                             int life,
                             double radius,
                             double blastRadius,
                             double shieldDamage,
                             double destabilizeSeconds,
                             Faction faction) {
        super(x, y, angle, dt, speed, hullDamage, life, radius, faction);
        this.angle = angle;
        this.blastRadius = Math.max(radius * 4.0, blastRadius);
        this.shieldDamage = Math.max(0.0, shieldDamage);
        this.destabilizeSeconds = Math.max(0.0, destabilizeSeconds);
    }
}
