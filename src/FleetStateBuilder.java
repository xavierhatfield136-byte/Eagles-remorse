/**
 * Small fleet-state construction helpers used by AISystem.
 */
public final class FleetStateBuilder {
    private FleetStateBuilder() {}

    public static final class StrengthSummary {
        public final double combatFriendly;
        public final double combatHostile;
        public final double supportFriendly;
        public final double supportHostile;
        public final int countFriendly;
        public final int countHostile;

        public StrengthSummary(double combatFriendly,
                               double combatHostile,
                               double supportFriendly,
                               double supportHostile,
                               int countFriendly,
                               int countHostile) {
            this.combatFriendly = combatFriendly;
            this.combatHostile = combatHostile;
            this.supportFriendly = supportFriendly;
            this.supportHostile = supportHostile;
            this.countFriendly = Math.max(0, countFriendly);
            this.countHostile = Math.max(0, countHostile);
        }
    }

    public static long strengthCacheKey(Faction perspective, double x, double y, double radius) {
        int faction = (perspective == null) ? -1 : perspective.ordinal();
        long qx = Math.round(x / 96.0);
        long qy = Math.round(y / 96.0);
        long qr = Math.round(Math.max(0.0, radius) / 48.0);
        long key = ((long) faction & 0xffL) << 56;
        key ^= (qx & 0xfffffL) << 36;
        key ^= (qy & 0xfffffL) << 16;
        key ^= qr & 0xffffL;
        return key;
    }

    public static boolean isFleetMemberCandidate(Ship ship) {
        if (ship == null) return false;
        if (!ship.alive || ship.dying || ship.hp <= 0) return false;
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) return false;
        return ship.faction != null;
    }
}
