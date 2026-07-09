import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemEscortFormationTest {

    @Test
    void escortWarpUsesReservedScreenSlotsInsteadOfAnchorCenter() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5200, 2600, true, 31001L, false));
        FleetShip anchor = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 1100.0, 1300.0);
        FleetShip escortOne = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 4400.0, 1050.0);
        FleetShip escortTwo = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 4400.0, 1550.0);
        anchor.angle = 0.0;
        escortOne.escortAnchorId = anchor.id;
        escortTwo.escortAnchorId = anchor.id;

        ctx.ships.clear();
        ctx.ships.add(anchor);
        ctx.ships.add(escortOne);
        ctx.ships.add(escortTwo);
        ctx.entityQuery.rebuild(ctx);
        for (int i = 0; i < 11 * 60; i++) {
            escortOne.update(1.0 / 60.0);
            escortTwo.update(1.0 / 60.0);
        }

        Method warp = AISystem.class.getDeclaredMethod(
                "maybeStartEscortAnchorWarp", GameContext.class, Ship.class, Ship.class);
        warp.setAccessible(true);

        assertTrue((Boolean) warp.invoke(null, ctx, escortOne, anchor));
        assertTrue((Boolean) warp.invoke(null, ctx, escortTwo, anchor));

        assertTrue(escortOne.isWarpCharging());
        assertTrue(escortTwo.isWarpCharging());
        assertTrue(Math.hypot(escortOne.warpExitX() - anchor.x, escortOne.warpExitY() - anchor.y) > anchor.radius + 120.0,
                "escort one should warp to a screen point, not the anchor center");
        assertTrue(Math.hypot(escortTwo.warpExitX() - anchor.x, escortTwo.warpExitY() - anchor.y) > anchor.radius + 120.0,
                "escort two should warp to a screen point, not the anchor center");
        assertTrue(Math.hypot(escortOne.warpExitX() - escortTwo.warpExitX(),
                        escortOne.warpExitY() - escortTwo.warpExitY()) > escortOne.radius + escortTwo.radius + 80.0,
                "escorts sharing an anchor should reserve visibly separated warp exits");
        assertNotEquals(Math.signum(escortOne.warpExitY() - anchor.y), Math.signum(escortTwo.warpExitY() - anchor.y),
                "default escort slots should alternate sides around the escorted ship");
    }
}
