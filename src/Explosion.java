import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Explosion {
    private static final int SUPERWEAPON_RING_COUNT = 3;

    // Hard cap so even if something goes wrong, the game never floods effects.
    private static final int MAX_EFFECTS = 900;

    public static final List<Explosion> active = new ArrayList<>();

    public enum Kind {
        SHIELD_HIT,
        DEATH,
        DESTABILIZER_PULSE,
        SUPERWEAPON_BLAST,
        STASIS_FIELD,
        FINAL_DETONATION
    }

    public final double x, y;
    public final Kind kind;
    public final int sourceShipId;
    public final Faction sourceFaction;
    public final double effectRadius;

    // seconds remaining
    private double t;

    // for drawing convenience (optional)
    public final double maxT;
    private final Set<Integer>[] superweaponRingHits;

    private Explosion(double x, double y, double seconds, Kind kind) {
        this(x, y, seconds, kind, -1, null, 0.0);
    }

    @SuppressWarnings("unchecked")
    private Explosion(double x, double y, double seconds, Kind kind, int sourceShipId, Faction sourceFaction) {
        this(x, y, seconds, kind, sourceShipId, sourceFaction, 0.0);
    }

    @SuppressWarnings("unchecked")
    private Explosion(double x, double y, double seconds, Kind kind, int sourceShipId, Faction sourceFaction, double effectRadius) {
        this.x = x;
        this.y = y;
        this.kind = (kind == null) ? Kind.DEATH : kind;
        this.t = seconds;
        this.maxT = seconds;
        this.sourceShipId = sourceShipId;
        this.sourceFaction = sourceFaction;
        this.effectRadius = Math.max(0.0, effectRadius);
        if (this.kind == Kind.SUPERWEAPON_BLAST) {
            this.superweaponRingHits = (Set<Integer>[]) new Set<?>[SUPERWEAPON_RING_COUNT];
            for (int i = 0; i < SUPERWEAPON_RING_COUNT; i++) {
                this.superweaponRingHits[i] = new HashSet<>();
            }
        } else {
            this.superweaponRingHits = null;
        }
    }

    public static void spawnShieldHit(double x, double y) {
        addCapped(new Explosion(x, y, 0.12, Kind.SHIELD_HIT)); // short impact ripple
    }

    public static void spawnDeath(double x, double y) {
        addCapped(new Explosion(x, y, 0.64, Kind.DEATH)); // staged blast
    }

    public static void spawnDestabilizerPulse(double x, double y, double effectRadius) {
        addCapped(new Explosion(x, y, 0.72, Kind.DESTABILIZER_PULSE, -1, null, effectRadius));
    }

    public static void spawnSuperweaponBlast(double x, double y, int sourceShipId, Faction sourceFaction) {
        addCapped(new Explosion(x, y, 0.92, Kind.SUPERWEAPON_BLAST, sourceShipId, sourceFaction));
    }

    public static void spawnStasisField(double x, double y, double seconds, int sourceShipId, Faction sourceFaction, double effectRadius) {
        addCapped(new Explosion(x, y, Math.max(0.1, seconds), Kind.STASIS_FIELD, sourceShipId, sourceFaction, effectRadius));
    }

    public static void spawnFinalDetonation(double x, double y, double effectRadius) {
        addCapped(new Explosion(x, y, 0.72, Kind.FINAL_DETONATION, -1, null, effectRadius));
    }

    private static void addCapped(Explosion e) {
        active.add(e);
        // drop oldest if over cap
        while (active.size() > MAX_EFFECTS) {
            active.remove(0);
        }
    }

    /** Call once per tick with dt (seconds). */
    public static void updateAll(double dt) {
        if (active.isEmpty()) return;

        for (Iterator<Explosion> it = active.iterator(); it.hasNext(); ) {
            Explosion e = it.next();
            e.t -= dt;
            if (e.t <= 0) it.remove();
        }
    }

    /** Backwards compatible (assumes ~60fps). */
    public static void updateAll() {
        updateAll(1.0 / 60.0);
    }

    /** 0..1 fraction remaining (useful if you want fade-out). */
    public double frac() {
        if (maxT <= 0) return 0;
        return Math.max(0.0, Math.min(1.0, t / maxT));
    }

    /** 0..1 fraction elapsed. */
    public double ageFrac() {
        return 1.0 - frac();
    }

    public int superweaponRingCount() {
        return (kind == Kind.SUPERWEAPON_BLAST) ? SUPERWEAPON_RING_COUNT : 0;
    }

    public double superweaponPlasmaRadius() {
        if (kind != Kind.SUPERWEAPON_BLAST) return 0.0;
        double age = ageFrac();
        return 56.0 + age * 236.0;
    }

    public double superweaponCoreRadius() {
        if (kind != Kind.SUPERWEAPON_BLAST) return 0.0;
        double age = ageFrac();
        return 20.0 + Math.min(1.0, age * 1.8) * 68.0;
    }

    public double superweaponRingRadius(int ringIndex) {
        if (kind != Kind.SUPERWEAPON_BLAST) return 0.0;
        double age = ageFrac();
        return switch (ringIndex) {
            case 0 -> 92.0 + age * 352.0;
            case 1 -> 144.0 + Math.max(0.0, age - 0.06) * 440.0;
            case 2 -> 196.0 + Math.max(0.0, age - 0.12) * 496.0;
            default -> 0.0;
        };
    }

    public double superweaponRingStrokeWidth(int ringIndex) {
        if (kind != Kind.SUPERWEAPON_BLAST) return 0.0;
        return switch (ringIndex) {
            case 0 -> 6.2;
            case 1 -> 4.4;
            case 2 -> 3.0;
            default -> 0.0;
        };
    }

    public double superweaponRingHalfWidth(int ringIndex) {
        if (kind != Kind.SUPERWEAPON_BLAST) return 0.0;
        return Math.max(12.0, superweaponRingStrokeWidth(ringIndex) * 0.5 + 12.0);
    }

    public double superweaponHazeRadius() {
        if (kind != Kind.SUPERWEAPON_BLAST) return 0.0;
        double age = ageFrac();
        return 240.0 + Math.max(0.0, age - 0.08) * 572.0;
    }

    public double stasisFieldRadius() {
        if (kind != Kind.STASIS_FIELD) return 0.0;
        return Math.max(120.0, effectRadius);
    }

    public double stasisFieldCoreRadius() {
        if (kind != Kind.STASIS_FIELD) return 0.0;
        double pulse = 0.88 + 0.12 * Math.sin(ageFrac() * Math.PI * 4.0);
        return stasisFieldRadius() * pulse;
    }

    public double finalDetonationPlasmaRadius() {
        if (kind != Kind.FINAL_DETONATION) return 0.0;
        double age = ageFrac();
        return Math.max(36.0, effectRadius * (0.30 + age * 0.92));
    }

    public double finalDetonationCoreRadius() {
        if (kind != Kind.FINAL_DETONATION) return 0.0;
        double age = ageFrac();
        return Math.max(14.0, effectRadius * (0.10 + Math.min(1.0, age * 1.6) * 0.24));
    }

    public double finalDetonationRingRadius(int ringIndex) {
        if (kind != Kind.FINAL_DETONATION) return 0.0;
        double age = ageFrac();
        return switch (ringIndex) {
            case 0 -> Math.max(44.0, effectRadius * (0.38 + age * 1.08));
            case 1 -> Math.max(60.0, effectRadius * (0.52 + Math.max(0.0, age - 0.05) * 1.12));
            default -> Math.max(80.0, effectRadius * (0.68 + Math.max(0.0, age - 0.10) * 1.08));
        };
    }

    public double finalDetonationRingStrokeWidth(int ringIndex) {
        if (kind != Kind.FINAL_DETONATION) return 0.0;
        double base = Math.max(2.4, effectRadius * 0.016);
        return switch (ringIndex) {
            case 0 -> base * 1.55;
            case 1 -> base * 1.15;
            default -> base * 0.82;
        };
    }

    public double finalDetonationHazeRadius() {
        if (kind != Kind.FINAL_DETONATION) return 0.0;
        double age = ageFrac();
        return Math.max(90.0, effectRadius * (0.90 + Math.max(0.0, age - 0.06) * 1.18));
    }

    public double destabilizerWaveRadius() {
        if (kind != Kind.DESTABILIZER_PULSE) return 0.0;
        double age = ageFrac();
        double span = Math.max(160.0, effectRadius * 1.06);
        return 28.0 + age * span;
    }

    public double destabilizerInnerRingRadius() {
        if (kind != Kind.DESTABILIZER_PULSE) return 0.0;
        double age = ageFrac();
        double span = Math.max(100.0, effectRadius * 0.68);
        return 14.0 + age * span;
    }

    public double destabilizerOuterRingRadius() {
        if (kind != Kind.DESTABILIZER_PULSE) return 0.0;
        double age = ageFrac();
        double span = Math.max(220.0, effectRadius * 1.28);
        return 44.0 + age * span;
    }

    public double destabilizerCoronaRadius() {
        if (kind != Kind.DESTABILIZER_PULSE) return 0.0;
        double age = ageFrac();
        return 18.0 + Math.min(1.0, age * 1.8) * Math.max(54.0, effectRadius * 0.34);
    }

    public double visualRadius() {
        return switch (kind) {
            case SHIELD_HIT -> 24.0;
            case DESTABILIZER_PULSE -> Math.max(220.0, effectRadius * 1.35);
            case SUPERWEAPON_BLAST -> 840.0;
            case STASIS_FIELD -> Math.max(140.0, effectRadius * 1.18);
            case FINAL_DETONATION -> Math.max(120.0, effectRadius * 2.1);
            default -> 72.0;
        };
    }

    public boolean markSuperweaponRingHit(int ringIndex, int shipId) {
        if (kind != Kind.SUPERWEAPON_BLAST) return false;
        if (ringIndex < 0 || ringIndex >= superweaponRingCount()) return false;
        if (shipId <= 0) return false;
        return superweaponRingHits[ringIndex].add(shipId);
    }
}
