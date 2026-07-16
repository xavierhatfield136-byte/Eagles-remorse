import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenRangeBalanceTest {

    @Test
    void greenGunAuthorityDoesNotUseLongCommandRange() {
        FleetShip green = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        FleetShip hostile = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1400.0, 0.0);

        double authorized = AISystem.gunFireAuthorityRange(null, green, green.turrets.getFirst(), hostile, 1600.0);

        assertTrue(authorized <= 760.0, "green frigates should not inherit long-range command fire authority");
        assertTrue(authorized < 1400.0, "green frigates should not be allowed to snipe distant targets");
    }

    @Test
    void greenBeamLengthIsCappedToLocalEngagementRange() {
        FleetShip green = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        FleetShip hostile = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1400.0, 0.0);

        double length = Turret.greenBeamLength(green, hostile, 2400.0);

        assertTrue(length <= 760.0, "green beam collider should not keep the old 2400-unit sniper reach");
        assertTrue(length < 1400.0, "green beams should not physically hit distant targets after firing nearby");
    }
}
