/**
 * Centralizes AI cadence changes for very large tactical battles.
 *
 * Normal battles keep a stride of 1, so existing behavior remains unchanged.
 * Huge battles stagger expensive scans across frames while cheap movement and
 * already-committed firing continue every update.
 */
public final class AiScalePolicy {
    private static final int MEDIUM_BATTLE_SHIPS = 140;
    private static final int LARGE_BATTLE_SHIPS = 220;
    private static final int HUGE_BATTLE_SHIPS = 300;

    private AiScalePolicy() {}

    public static FramePlan planFor(GameContext ctx, long frameIndex) {
        int liveCombatShips = countLiveCombatShips(ctx);
        if (liveCombatShips >= HUGE_BATTLE_SHIPS) {
            return new FramePlan(frameIndex, liveCombatShips, 8, 4, 0.38, 0.10, 12,
                    3.50, 3.00, 2.65);
        }
        if (liveCombatShips >= LARGE_BATTLE_SHIPS) {
            return new FramePlan(frameIndex, liveCombatShips, 3, 2, 0.22, 0.06, 16,
                    2.10, 1.75, 1.55);
        }
        if (liveCombatShips >= MEDIUM_BATTLE_SHIPS) {
            return new FramePlan(frameIndex, liveCombatShips, 1, 2, 0.12, 0.03, 24,
                    1.25, 1.18, 1.12);
        }
        return new FramePlan(frameIndex, liveCombatShips, 1, 1, 0.04, 0.0, 48,
                1.0, 1.0, 1.0);
    }

    private static int countLiveCombatShips(GameContext ctx) {
        if (ctx == null || ctx.ships == null || ctx.ships.isEmpty()) return 0;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) continue;
            count++;
        }
        return count;
    }

    public static final class FramePlan {
        public final long frameIndex;
        public final int liveCombatShips;
        public final int fullDecisionStride;
        public final int avoidanceStride;
        public final double intentReuseSeconds;
        public final double movementThinkSeconds;
        public final int targetCandidateCap;
        public final double immediateThreatCadenceMultiplier;
        public final double engagementScanBackoffMultiplier;
        public final double closestRetargetCadenceMultiplier;

        private FramePlan(long frameIndex,
                          int liveCombatShips,
                          int fullDecisionStride,
                          int avoidanceStride,
                          double intentReuseSeconds,
                          double movementThinkSeconds,
                          int targetCandidateCap,
                          double immediateThreatCadenceMultiplier,
                          double engagementScanBackoffMultiplier,
                          double closestRetargetCadenceMultiplier) {
            this.frameIndex = frameIndex;
            this.liveCombatShips = Math.max(0, liveCombatShips);
            this.fullDecisionStride = Math.max(1, fullDecisionStride);
            this.avoidanceStride = Math.max(1, avoidanceStride);
            this.intentReuseSeconds = Math.max(0.0, intentReuseSeconds);
            this.movementThinkSeconds = Math.max(0.0, movementThinkSeconds);
            this.targetCandidateCap = Math.max(4, targetCandidateCap);
            this.immediateThreatCadenceMultiplier = Math.max(1.0, immediateThreatCadenceMultiplier);
            this.engagementScanBackoffMultiplier = Math.max(1.0, engagementScanBackoffMultiplier);
            this.closestRetargetCadenceMultiplier = Math.max(1.0, closestRetargetCadenceMultiplier);
        }

        public boolean isLargeBattle() {
            return liveCombatShips >= MEDIUM_BATTLE_SHIPS;
        }

        public boolean shouldRunFullDecisionScan(Ship ship) {
            if (ship == null || fullDecisionStride <= 1) return true;
            if (ship.aiForcedEngageTimer > 0.0) return true;
            if (ship.aiTargetCommitTimer <= 0.08 && ship.aiCommittedTargetId <= 0) {
                return Math.floorMod(ship.id + (int) frameIndex, fullDecisionStride) == 0;
            }
            return Math.floorMod(ship.id + (int) frameIndex, fullDecisionStride) == 0;
        }

        public boolean shouldRunAvoidance(Ship ship) {
            if (ship == null || avoidanceStride <= 1) return true;
            if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET || ship.role == ShipRole.MINER) return true;
            return Math.floorMod(ship.id + (int) frameIndex, avoidanceStride) == 0;
        }
    }
}
