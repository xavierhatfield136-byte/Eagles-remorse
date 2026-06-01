import app.config.GameMode;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mode-specific visual asset manifests avoid decoding every optional hull family at startup.
 */
public final class AssetPrewarmManifest {
    private AssetPrewarmManifest() {}

    public static Set<ShipRole> rolesFor(GameMode mode) {
        LinkedHashSet<ShipRole> roles = new LinkedHashSet<>();
        roles.add(ShipRole.FRIGATE);
        roles.add(ShipRole.CIWS_CORVETTE);
        roles.add(ShipRole.MISSILE_BOAT);
        roles.add(ShipRole.LIGHT_CRUISER);
        roles.add(ShipRole.BASE);
        if (mode == null) return roles;
        switch (mode) {
            case CAMPAIGN_OPS, FLEET -> {
                roles.add(ShipRole.MOTHERSHIP);
                roles.add(ShipRole.MINER);
                roles.add(ShipRole.CARRIER);
                roles.add(ShipRole.BATTLECRUISER);
                roles.add(ShipRole.BATTLESHIP);
            }
            case FOUR_TEAM_DOMINATION, CUSTOM_BATTLES -> {
                roles.addAll(java.util.Arrays.asList(ShipRole.values()));
            }
            case SHOOTING_RANGE -> roles.addAll(java.util.Arrays.asList(ShipRole.values()));
            default -> {
                roles.add(ShipRole.PICKET);
                roles.add(ShipRole.CRUISER);
            }
        }
        return Set.copyOf(roles);
    }
}
