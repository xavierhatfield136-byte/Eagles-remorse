import java.util.Collections;
import java.util.List;

/**
 * Canonical room contract used by x-ray UI, telemetry, and replay tooling.
 */
public final class ShipRoom {
    public static final int STATUS_DESTROYED = 1 << 0;
    public static final int STATUS_FIRE_ACTIVE = 1 << 1;
    public static final int STATUS_CRITICAL = 1 << 2;
    public static final int STATUS_DISRUPTED = 1 << 3;

    public final String id;
    public final String roleProfileId;
    public final double[] polygonLocal;
    public final double maxHP;
    public final double hp;
    public final double criticality;
    public final List<String> tags;
    public final int statusFlags;

    public ShipRoom(String id,
                    String roleProfileId,
                    double[] polygonLocal,
                    double maxHP,
                    double hp,
                    double criticality,
                    List<String> tags,
                    int statusFlags) {
        this.id = (id == null) ? "" : id;
        this.roleProfileId = (roleProfileId == null) ? "capital" : roleProfileId;
        this.polygonLocal = (polygonLocal == null) ? new double[0] : polygonLocal.clone();
        this.maxHP = Math.max(0.0, maxHP);
        this.hp = Math.max(0.0, hp);
        this.criticality = MathUtil.clamp(criticality, 0.0, 1.0);
        this.tags = (tags == null) ? List.of() : Collections.unmodifiableList(List.copyOf(tags));
        this.statusFlags = statusFlags;
    }
}
