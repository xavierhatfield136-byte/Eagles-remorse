/**
 * Yamato 2199-style heavy energy bolt.
 *
 * This is NOT a hitscan beam: it's a visible projectile with medium speed
 * and a chunky impact. Visual styling is handled in Renderer.drawProjectiles
 * (via instanceof EnergyBolt). The spawn point is retained so the renderer
 * can render either a combined volley braid or a single firing lane from one
 * beam barrel, and the source turret offsets let the bolt reattach to the
 * firing mount if the ship moves.
 */
public class EnergyBolt extends Projectile {

    /** Angle for rendering the bolt "capsule" shape. */
    public final double angle;
    /** True when the renderer should use the beam-bolt visual package. */
    public final boolean beamBolt;
    /** Launch point used for the triple-lance spiral effect. */
    public final double spawnX;
    public final double spawnY;
    /** Local turret mount used to re-anchor the beam while the ship moves. */
    public final double sourceTurretLocalX;
    public final double sourceTurretLocalY;
    /** When non-negative, this projectile should render as a single firing lane. */
    public final int beamLaneIndex;
    /** Number of visible beam barrels represented by this projectile. */
    public final int beamLaneCount;

    public EnergyBolt(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            Faction faction
    ) {
        this(x, y, angle, dt, speed, damage, life, radius, isBeamBoltSpeed(speed),
                -1, 1, Double.NaN, Double.NaN, faction);
    }

    public EnergyBolt(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            boolean beamBolt,
            Faction faction
    ) {
        this(x, y, angle, dt, speed, damage, life, radius, beamBolt,
                -1, 1, Double.NaN, Double.NaN, faction);
    }

    public EnergyBolt(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            boolean beamBolt,
            int beamLaneIndex,
            int beamLaneCount,
            Faction faction
    ) {
        this(x, y, angle, dt, speed, damage, life, radius, beamBolt,
                beamLaneIndex, beamLaneCount, Double.NaN, Double.NaN, faction);
    }

    public EnergyBolt(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            boolean beamBolt,
            int beamLaneIndex,
            int beamLaneCount,
            double sourceTurretLocalX,
            double sourceTurretLocalY,
            Faction faction
    ) {
        super(
                x,
                y,
                Math.cos(angle) * speed * dt,
                Math.sin(angle) * speed * dt,
                computeRadius(beamBolt, speed, radius),
                damage,
                life,
                faction
        );
        this.angle = angle;
        this.beamBolt = beamBolt;
        this.spawnX = x;
        this.spawnY = y;
        this.sourceTurretLocalX = sourceTurretLocalX;
        this.sourceTurretLocalY = sourceTurretLocalY;
        this.beamLaneIndex = beamLaneIndex;
        this.beamLaneCount = Math.max(1, beamLaneCount);
    }

    public EnergyBolt(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            double sourceTurretLocalX,
            double sourceTurretLocalY,
            Faction faction
    ) {
        this(x, y, angle, dt, speed, damage, life, radius, isBeamBoltSpeed(speed),
                -1, 1, sourceTurretLocalX, sourceTurretLocalY, faction);
    }

    public EnergyBolt(double x, double y, double angle, double dt, Faction faction) {
        this(x, y, angle, dt,
                DoctrineRegistry.ENERGY_NAVY.mainProjectileSpeed,
                DoctrineRegistry.ENERGY_NAVY.mainDamage,
                120,
                4.5,
                faction);
    }

    public boolean isBeamBolt() {
        return beamBolt;
    }

    public boolean usesCombinedBeamVisual() {
        return beamBolt && beamLaneIndex < 0;
    }

    private static boolean isBeamBoltSpeed(double speed) {
        return speed <= Ship.BEAM_BOLT_SPEED + 1e-6;
    }

    private static double computeRadius(boolean beamBolt, double speed, double radius) {
        if (beamBolt || isBeamBoltSpeed(speed)) {
            return Math.max(radius, 7.0);
        }
        return radius;
    }
}
