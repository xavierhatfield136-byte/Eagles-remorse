import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenRangeBalanceTest {

    @Test
    void greenGunAuthorityUsesStandardProsecutionRange() {
        FleetShip green = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        FleetShip hostile = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1400.0, 0.0);

        double authorized = AISystem.gunFireAuthorityRange(null, green, green.turrets.getFirst(), hostile, 1600.0);

        assertTrue(authorized >= 3000.0, "green frigates should use the standard prosecution envelope");
    }

    @Test
    void greenBeamLengthMatchesStandardProsecutionRange() {
        FleetShip green = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        FleetShip hostile = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1400.0, 0.0);

        double length = Turret.greenBeamLength(green, hostile, 2400.0);

        assertTrue(length >= 3000.0, "green beam collider should reach the standard prosecution envelope");
    }
}
