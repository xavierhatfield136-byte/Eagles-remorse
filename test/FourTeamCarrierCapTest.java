import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FourTeamCarrierCapTest {

    @Test
    void fourTeamDominationLimitsBomberSwarmsPerTeam() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 321L, false));
        Ship carrier = new FleetShip(ShipRole.CARRIER, Faction.TEAM_C, 4000.0, 4000.0);
        carrier.maxFighters = 24;
        carrier.carrierAutoLaunch = true;
        for (int i = 0; i < carrier.flightDeckLoadout.length; i++) {
            carrier.setFlightDeckRole(i, ShipRole.BOMBER);
        }
        ctx.ships.add(carrier);

        for (int i = 0; i < 80; i++) {
            CarrierSystem.update(ctx, 1.0);
        }

        long bombers = ctx.ships.stream()
                .filter(s -> s != null && s.alive && !s.dying && s.carrierOwnerId >= 0)
                .filter(s -> s.faction == Faction.TEAM_C && s.role == ShipRole.BOMBER)
                .count();
        long totalCraft = ctx.ships.stream()
                .filter(s -> s != null && s.alive && !s.dying && s.carrierOwnerId >= 0)
                .filter(s -> s.faction == Faction.TEAM_C)
                .count();

        assertTrue(bombers <= 6, "four-team domination should cap bomber density per team");
        assertTrue(totalCraft <= 18, "four-team domination should cap total launched craft per team");
    }
}
