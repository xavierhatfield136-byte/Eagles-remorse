import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiScalePolicyTest {
    @Test
    void normalBattlesRunEveryAiDecisionFrame() {
        GameContext ctx = contextWithShips(20);
        AiScalePolicy.FramePlan plan = AiScalePolicy.planFor(ctx, 0);

        assertEquals(1, plan.fullDecisionStride);
        assertEquals(1, plan.avoidanceStride);
        assertEquals(48, plan.targetCandidateCap);
        assertEquals(0.04, plan.intentReuseSeconds, 0.0001);
        assertEquals(0.0, plan.movementThinkSeconds, 0.0001);
        assertTrue(plan.shouldRunFullDecisionScan(ctx.ships.getFirst()));
        assertTrue(plan.shouldRunAvoidance(ctx.ships.getFirst()));
    }

    @Test
    void hugeBattlesStaggerExpensiveDecisionScans() {
        GameContext ctx = contextWithShips(320);
        Ship first = ctx.ships.getFirst();
        AiScalePolicy.FramePlan plan = AiScalePolicy.planFor(ctx, 0);

        assertEquals(8, plan.fullDecisionStride);
        assertEquals(4, plan.avoidanceStride);
        assertEquals(12, plan.targetCandidateCap);
        assertEquals(0.38, plan.intentReuseSeconds, 0.0001);
        assertEquals(0.10, plan.movementThinkSeconds, 0.0001);
        assertTrue(plan.isLargeBattle());
        assertEquals(320, plan.liveCombatShips);
        assertFalse(plan.shouldRunFullDecisionScan(first) && plan.shouldRunFullDecisionScan(ctx.ships.get(1)),
                "adjacent ships should not all run full scans on the same huge-battle frame");
    }

    @Test
    void focusTargetsRotateBySquadIndex() {
        Ship first = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 100.0, 100.0);
        Ship second = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 200.0, 100.0);
        Ship third = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 300.0, 100.0);

        assertSame(first, CombatTargeting.focusTargetForSquad(List.of(first, second, third), 0));
        assertSame(second, CombatTargeting.focusTargetForSquad(List.of(first, second, third), 1));
        assertSame(third, CombatTargeting.focusTargetForSquad(List.of(first, second, third), 2));

        second.alive = false;
        assertSame(third, CombatTargeting.focusTargetForSquad(List.of(first, second, third), 1));
    }

    private static GameContext contextWithShips(int count) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 6000, 6000, true, 99L, false));
        ctx.ships.clear();
        for (int i = 0; i < count; i++) {
            Faction faction = (i & 1) == 0 ? Faction.ALLY : Faction.ENEMY;
            ctx.ships.add(new FleetShip(ShipRole.FRIGATE, faction, 100.0 + i * 8.0, 100.0));
        }
        return ctx;
    }
}
