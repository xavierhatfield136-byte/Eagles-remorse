/**
 * A small set of doctrine knobs. For Step 5, we primarily use this to choose
 * the main projectile *style* (EnergyBolt vs Bullet). Later steps can wire
 * missile/PD scaling to missileStrength/pdStrength.
 */
public final class DoctrineProfile {
    public final Doctrine doctrine;

    /** Suggested main projectile speed (units/sec). */
    public final double mainProjectileSpeed;
    /** Suggested main projectile damage per hit (integer-ish). */
    public final int mainDamage;
    /** Suggested rate of fire (shots/sec). */
    public final double mainFireRate;

    /** Multiplier for missile pressure (damage/salvo/reload tuning later). */
    public final double missileStrength;
    /** Multiplier for point defense effectiveness (tuning later). */
    public final double pdStrength;

    public DoctrineProfile(
            Doctrine doctrine,
            double mainProjectileSpeed,
            int mainDamage,
            double mainFireRate,
            double missileStrength,
            double pdStrength
    ) {
        this.doctrine = doctrine;
        this.mainProjectileSpeed = mainProjectileSpeed;
        this.mainDamage = mainDamage;
        this.mainFireRate = mainFireRate;
        this.missileStrength = missileStrength;
        this.pdStrength = pdStrength;
    }
}
