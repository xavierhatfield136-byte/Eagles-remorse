import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetClassifierTest {
    @Test
    void minersAndScoutsAreLightGroups() {
        assertEquals(FleetLevel.LEVEL_1_LIGHT,
                FleetClassifier.classifyRoles(ShipRole.MINER, ShipRole.MINER, ShipRole.MINER).level);
        assertEquals(FleetLevel.LEVEL_1_LIGHT,
                FleetClassifier.classifyRoles(ShipRole.PICKET, ShipRole.PATROL, ShipRole.PATROL,
                        ShipRole.STEALTH_SHIP, ShipRole.PICKET).level);
    }

    @Test
    void ordinaryCombatFormationsUpToTwentyAreMediumWithoutCapitals() {
        assertEquals(FleetLevel.LEVEL_2_MEDIUM,
                FleetClassifier.classifyRoles(repeat(ShipRole.FRIGATE, 12)).level);
        assertEquals(FleetLevel.LEVEL_2_MEDIUM,
                FleetClassifier.classifyRoles(repeat(ShipRole.CRUISER, 18)).level);
    }

    @Test
    void capitalsAndLargeConventionalFleetsBecomeLevelThree() {
        ArrayList<ShipRole> tenWithCapital = repeat(ShipRole.FRIGATE, 9);
        tenWithCapital.add(ShipRole.DREADNOUGHT);
        assertEquals(FleetLevel.LEVEL_3_LARGE_CAPITAL,
                FleetClassifier.classifyRoles(tenWithCapital).level);

        ArrayList<ShipRole> twentyWithCapitals = repeat(ShipRole.CRUISER, 17);
        twentyWithCapitals.add(ShipRole.DREADNOUGHT);
        twentyWithCapitals.add(ShipRole.CARRIER);
        twentyWithCapitals.add(ShipRole.SUPERSHIP);
        assertEquals(FleetLevel.LEVEL_3_LARGE_CAPITAL,
                FleetClassifier.classifyRoles(twentyWithCapitals).level);

        assertEquals(FleetLevel.LEVEL_3_LARGE_CAPITAL,
                FleetClassifier.classifyRoles(repeat(ShipRole.FRIGATE, 21)).level,
                "21-29 ordinary ships are explicitly a large conventional fleet, not an undefined gap");
    }

    @Test
    void titanTaskForcesOverrideRawShipCount() {
        ArrayList<ShipRole> thirtyWithTitan = repeat(ShipRole.FRIGATE, 29);
        thirtyWithTitan.add(ShipRole.INTERDICTION_TITAN);
        assertEquals(FleetLevel.LEVEL_4_TITAN_TASK_FORCE,
                FleetClassifier.classifyRoles(thirtyWithTitan).level);

        ArrayList<ShipRole> thirtyFiveWithTitanAndCapitals = repeat(ShipRole.FRIGATE, 31);
        thirtyFiveWithTitanAndCapitals.add(ShipRole.DREADNOUGHT);
        thirtyFiveWithTitanAndCapitals.add(ShipRole.BATTLESHIP);
        thirtyFiveWithTitanAndCapitals.add(ShipRole.SUPERSHIP);
        thirtyFiveWithTitanAndCapitals.add(ShipRole.ARTILLERY_TITAN);
        assertEquals(FleetLevel.LEVEL_4_TITAN_TASK_FORCE,
                FleetClassifier.classifyRoles(thirtyFiveWithTitanAndCapitals).level);

        assertEquals(FleetLevel.LEVEL_4_TITAN_TASK_FORCE,
                FleetClassifier.classifyRoles(ShipRole.FRIGATE, ShipRole.FRIGATE, ShipRole.PICKET,
                        ShipRole.PATROL, ShipRole.CRUISER, ShipRole.BULWARK_TITAN,
                        ShipRole.HYPERWEAPON_TITAN).level,
                "a seven-ship group with two Titans must not be treated as a medium fleet");
    }

    @Test
    void grandFleetRequiresGrandScaleCapitalsAndSeveralTitans() {
        ArrayList<ShipRole> grand = repeat(ShipRole.FRIGATE, 31);
        grand.addAll(List.of(
                ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER, ShipRole.DREADNOUGHT,
                ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.SUPERSHIP,
                ShipRole.INTERDICTION_TITAN, ShipRole.ARTILLERY_TITAN, ShipRole.HYPERWEAPON_TITAN));
        assertEquals(FleetLevel.LEVEL_5_GRAND_FLEET,
                FleetClassifier.classifyRoles(grand).level);

        assertEquals(FleetLevel.LEVEL_3_LARGE_CAPITAL,
                FleetClassifier.classifyRoles(repeat(ShipRole.FRIGATE, 40)).level,
                "40 ships without Titans are large, but not a Grand Fleet");
    }

    @Test
    void civilianAndJoinableRolesAreSeparateFromFleetLevel() {
        FleetClassifier.FleetProfile convoy = FleetClassifier.classifyRoles(
                ShipRole.HAULER, ShipRole.HAULER, ShipRole.TRANSPORT, ShipRole.PICKET,
                ShipRole.FRIGATE, ShipRole.FRIGATE, ShipRole.HAULER, ShipRole.MINER,
                ShipRole.PATROL, ShipRole.PICKET, ShipRole.HAULER, ShipRole.TRANSPORT);

        assertEquals(FleetLevel.LEVEL_2_MEDIUM, convoy.level);
        assertTrue(convoy.civilianCount >= 6);
        assertFalse(FleetClassifier.isMilitaryJoinableByDefault(ShipRole.MINER));
        assertFalse(FleetClassifier.isMilitaryJoinableByDefault(ShipRole.HAULER));
        assertTrue(FleetClassifier.isMilitaryJoinableByDefault(ShipRole.CRUISER));
    }

    private static ArrayList<ShipRole> repeat(ShipRole role, int count) {
        ArrayList<ShipRole> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(role);
        return out;
    }
}
