import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiwsBalanceRegressionTest {

    @Test
    void generalistCapitalsNoLongerGetHighTierFreeCiwsScreens() {
        FleetShip supership = new FleetShip(ShipRole.SUPERSHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0.0, 0.0);

        assertTrue(supership.hasCIWS, "supership should retain some last-ditch CIWS");
        assertEquals(1, supership.ciwsPelletsPerBurst, "supership should not get multi-pellet PD saturation");
        assertTrue(supership.ciwsRange <= 170.0 + 1e-6, "supership CIWS range should be heavily constrained");
        assertTrue(supership.ciwsCooldown >= 0.24 - 1e-6, "supership CIWS should cycle much slower");

        assertEquals(1, mothership.ciwsPelletsPerBurst, "mothership should not blanket the map with free CIWS fire");
        assertTrue(mothership.ciwsRange <= 221.0 + 1e-6, "mothership CIWS should stay short-ranged even after flagship bonuses");
        assertTrue(mothership.ciwsCooldown >= 0.24 - 1e-6, "mothership CIWS should remain a weak self-defense layer");
    }

    @Test
    void dedicatedPointDefenseHullKeepsMeaningfulCiwsAdvantage() {
        FleetShip pdCraft = new FleetShip(ShipRole.PD_CRAFT, Faction.ALLY, 0.0, 0.0);
        FleetShip corvette = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ALLY, 0.0, 0.0);
        FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);

        assertTrue(pdCraft.ciwsPelletsPerBurst >= 6, "PD craft should keep its dense interception burst");
        assertTrue(corvette.ciwsPelletsPerBurst >= 7, "CIWS corvette should remain the premier AA hull");
        assertEquals(1, frigate.ciwsPelletsPerBurst, "general-purpose frigates should only keep token CIWS");
        assertTrue(corvette.ciwsRange > frigate.ciwsRange, "dedicated AA hulls should outrange generalists");
        assertTrue(corvette.ciwsCooldown < frigate.ciwsCooldown, "dedicated AA hulls should cycle faster than generalists");
    }
}
