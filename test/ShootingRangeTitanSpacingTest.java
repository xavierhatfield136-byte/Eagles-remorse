import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShootingRangeTitanSpacingTest {
    @Test
    void defaultShootingRangeLayoutKeepsTitansOutsideAuraStackingDistance() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        List<Ship> titans = new ArrayList<>();
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == ctx.player) continue;
            if (ship.role != null && ship.role.isTitanOrMothership()) titans.add(ship);
        }

        double minDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < titans.size(); i++) {
            for (int j = i + 1; j < titans.size(); j++) {
                Ship a = titans.get(i);
                Ship b = titans.get(j);
                minDist = Math.min(minDist, Math.hypot(a.x - b.x, a.y - b.y));
            }
        }

        assertTrue(minDist >= 680.0,
                "default shooting range titan spacing should avoid overlapping support fields");
    }
}
