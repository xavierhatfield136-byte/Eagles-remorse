import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

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

    @Test
    void assaultFormationScreensTowardHostilesEvenWhenFlagshipFacesAway() throws Exception {
        Player mothership = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        mothership.faction = Faction.ALLY;
        mothership.angle = 0.0;
        FleetShip line = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 980.0, 1000.0);
        FleetShip picket = new FleetShip(ShipRole.PICKET, Faction.ALLY, 970.0, 1040.0);
        FleetShip hostile = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 250.0, 1000.0);
        List<Ship> members = List.of(mothership, line, picket);

        Method anchorAt = AISystem.class.getDeclaredMethod(
                "assaultFormationAnchorAt",
                double.class, double.class, double.class,
                List.class, Ship.class, Ship.class, double.class,
                GameContext.FleetCommand.class, Ship.class);
        anchorAt.setAccessible(true);

        double[] lineAnchor = (double[]) anchorAt.invoke(null,
                mothership.x, mothership.y, mothership.angle,
                members, mothership, line, 130.0,
                GameContext.FleetCommand.FORM_UP, hostile);
        double[] picketAnchor = (double[]) anchorAt.invoke(null,
                mothership.x, mothership.y, mothership.angle,
                members, mothership, picket, 130.0,
                GameContext.FleetCommand.FORM_UP, hostile);

        assertTrue(lineAnchor[0] < mothership.x && lineAnchor[0] > hostile.x,
                "line ships in Assault should screen along the hostile vector, not the mothership nose");
        assertTrue(picketAnchor[0] < mothership.x && picketAnchor[0] > hostile.x,
                "picket ships in Assault should also post between the mothership and hostile contacts");
    }

    @Test
    void assaultFormationFormUpSendsLineAndPicketShipsIntoContact() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2600, 1800, true, 31002L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 900.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.angle = 0.0;
        ctx.command.alliedFleetFormation = GameContext.FleetFormation.ASSAULT;
        ctx.command.alliedFleetCommand = GameContext.FleetCommand.FORM_UP;

        FleetShip line = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 700.0, 900.0);
        FleetShip picket = new FleetShip(ShipRole.PICKET, Faction.ALLY, 650.0, 940.0);
        FleetShip hostile = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 360.0, 900.0);

        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        ctx.ships.add(line);
        ctx.ships.add(picket);
        ctx.ships.add(hostile);
        ctx.entityQuery.rebuild(ctx);

        AISystem.update(ctx, 1.0 / 60.0);

        assertTrue(line.vx < 0.0,
                "line ships should move toward the Assault screen/contact instead of stacking ahead of the mothership");
        assertTrue(Math.hypot(picket.vx, picket.vy) > 0.0001,
                "picket ships should actively maneuver toward hostile contact while Assault formation is selected");
        assertTrue(line.aiCommittedTargetId == hostile.id,
                "Assault line ships should commit to the hostile once they can force contact");
        assertTrue(picket.aiCommittedTargetId == hostile.id,
                "Assault picket ships should commit to the hostile once they can force contact");
    }
}
