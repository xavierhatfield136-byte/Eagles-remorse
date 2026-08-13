import java.nio.file.Path;
import java.util.UUID;

public final class CustomProjectile extends Projectile {
    public final double angle;
    public final Path projectileAssetPath;
    public final double visualScale;
    public final UUID customWeaponId;

    public CustomProjectile(double x,
                            double y,
                            double angle,
                            double dt,
                            WeaponRuntimeProfile profile,
                            int damage,
                            Faction faction) {
        super(
                x,
                y,
                Math.cos(angle) * profile.projectileSpeedUnitsPerSecond() * dt,
                Math.sin(angle) * profile.projectileSpeedUnitsPerSecond() * dt,
                Math.max(2.0, 3.2 * profile.projectileVisualScale()),
                damage,
                profile.projectileLifetimeFrames(),
                faction
        );
        this.angle = angle;
        this.projectileAssetPath = profile.projectileAssetPath();
        this.visualScale = profile.projectileVisualScale();
        this.customWeaponId = profile.id();
    }
}
