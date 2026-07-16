import java.util.EnumMap;

/**
 * Projectile pressure rules for large battles.
 *
 * Normal battles keep exact behavior. When active projectile counts get high,
 * this policy reduces low-value point-defense spam and staggers expensive
 * missile retarget scans while preserving intercept missiles and direct hits.
 */
public final class ProjectileScalePolicy {
    private static final int MEDIUM_PROJECTILES = 380;
    private static final int HIGH_PROJECTILES = 560;
    private static final int EXTREME_PROJECTILES = 700;

    private ProjectileScalePolicy() {}

    public static FramePlan planFor(GameContext ctx, long frameIndex) {
        int active = countActiveProjectiles(ctx);
        if (active >= EXTREME_PROJECTILES) {
            return new FramePlan(frameIndex, active, 5, 5, 3, 2, 22, 6);
        }
        if (active >= HIGH_PROJECTILES) {
            return new FramePlan(frameIndex, active, 4, 4, 2, 3, 30, 8);
        }
        if (active >= MEDIUM_PROJECTILES) {
            return new FramePlan(frameIndex, active, 2, 2, 1, 4, 44, 10);
        }
        return new FramePlan(frameIndex, active, 1, 1, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static int countActiveProjectiles(GameContext ctx) {
        if (ctx == null || ctx.projectiles == null || ctx.projectiles.isEmpty()) return 0;
        int count = 0;
        for (Projectile projectile : ctx.projectiles) {
            if (projectile != null && projectile.alive) count++;
        }
        return count;
    }

    public static final class FramePlan {
        public final long frameIndex;
        public final int activeProjectiles;
        public final int missileRetargetStride;
        public final int ciwsAcquisitionStride;
        public final int ciwsPelletStride;
        public final int ciwsLifeCap;
        public final int ciwsTeamBurstBudget;
        public final int ciwsLocalBurstBudget;
        private final EnumMap<Faction, Integer> ciwsBurstsByFaction = new EnumMap<>(Faction.class);

        private FramePlan(long frameIndex,
                          int activeProjectiles,
                          int missileRetargetStride,
                          int ciwsAcquisitionStride,
                          int ciwsPelletStride,
                          int ciwsLifeCap,
                          int ciwsTeamBurstBudget,
                          int ciwsLocalBurstBudget) {
            this.frameIndex = frameIndex;
            this.activeProjectiles = Math.max(0, activeProjectiles);
            this.missileRetargetStride = Math.max(1, missileRetargetStride);
            this.ciwsAcquisitionStride = Math.max(1, ciwsAcquisitionStride);
            this.ciwsPelletStride = Math.max(1, ciwsPelletStride);
            this.ciwsLifeCap = Math.max(1, ciwsLifeCap);
            this.ciwsTeamBurstBudget = Math.max(1, ciwsTeamBurstBudget);
            this.ciwsLocalBurstBudget = Math.max(1, ciwsLocalBurstBudget);
        }

        public boolean underPressure() {
            return activeProjectiles >= MEDIUM_PROJECTILES;
        }

        public boolean shouldRetargetMissile(Missile missile, boolean urgent) {
            if (urgent || missile == null || missileRetargetStride <= 1) return true;
            if (missile.role == Turret.MissileRole.INTERCEPT) return true;
            return Math.floorMod(missile.sourceShipId + missile.life + (int) frameIndex, missileRetargetStride) == 0;
        }

        public boolean shouldRunCiwsAcquisition(Ship ship) {
            if (ship == null || ciwsAcquisitionStride <= 1) return true;
            return Math.floorMod(ship.id + (int) frameIndex, ciwsAcquisitionStride) == 0;
        }

        public int ciwsPelletsForBurst(int requested) {
            int pellets = Math.max(1, requested);
            if (ciwsPelletStride <= 1) return pellets;
            return Math.max(1, (pellets + ciwsPelletStride - 1) / ciwsPelletStride);
        }

        public int ciwsLifeFor(int requestedLife) {
            int life = Math.max(1, requestedLife);
            if (!underPressure()) return life;
            return Math.min(life, ciwsLifeCap);
        }

        public boolean allowCiwsBurst(Faction faction, double x, double y) {
            if (!underPressure()) return true;
            Faction key = faction == null ? Faction.ENEMY : faction;
            int used = ciwsBurstsByFaction.getOrDefault(key, 0);
            if (used >= ciwsTeamBurstBudget) return false;
            if (used >= ciwsLocalBurstBudget && Math.floorMod((int) Math.round(x * 0.05 + y * 0.07) + used, 3) != 0) {
                return false;
            }
            ciwsBurstsByFaction.put(key, used + 1);
            return true;
        }
    }
}
