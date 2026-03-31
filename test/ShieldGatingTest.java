import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldGatingTest {

    @Test
    void blueShieldGateAbsorbsFiveHitsBeforeCoreShieldDrops() {
        FleetShip blue = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);

        assertEquals(5, blue.externalShieldGateHitCap(), "blue ships should get a five-hit outer shield screen");

        double shieldBefore = blue.shield;
        int hpBefore = blue.hp;
        for (int hit = 1; hit <= blue.externalShieldGateHitCap(); hit++) {
            blue.takeDamage(1, blue.x + blue.radius, blue.y, 0.0, 0.0);
            assertEquals(shieldBefore, blue.shield, 1e-6, "outer shield should absorb blue hit " + hit);
            assertEquals(hpBefore, blue.hp, "blue outer shield should stop hull damage on hit " + hit);
            assertEquals(blue.externalShieldGateHitCap() - hit, blue.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_FORE),
                    "blue fore shield gate should count down per hit");
            assertEquals(blue.externalShieldGateHitCap(), blue.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_REAR),
                    "blue rear shield gate should stay fresh while the bow is being hit");
        }

        blue.takeDamage(1, blue.x + blue.radius, blue.y, 0.0, 0.0);
        assertTrue(blue.shield < shieldBefore, "once blue's outer screen is gone, the core shield should start taking damage");

        double shieldAfterForeDrop = blue.shield;
        blue.takeDamage(1, blue.x - blue.radius, blue.y, 0.0, 0.0);
        assertEquals(shieldAfterForeDrop, blue.shield, 1e-6,
                "hitting blue from the rear should still be absorbed by the untouched rear gate");
    }

    @Test
    void shieldGateDoesNotInstantlyRefillWhileCoreShieldRemainsFull() {
        FleetShip blue = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);

        blue.takeDamage(1, blue.x + blue.radius, blue.y, 0.0, 0.0);
        assertEquals(4, blue.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_FORE),
                "fore gate should spend one charge on the first hit");

        blue.update(0.1);

        assertEquals(4, blue.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_FORE),
                "outer gate should not instantly refill just because the inner shield stayed full");
    }

    @Test
    void greenShieldGateAbsorbsTenHitsBeforeCoreShieldDrops() {
        FleetShip green = doctrinalShip(ShipRole.FRIGATE, Faction.TEAM_C);

        assertEquals(10, green.externalShieldGateHitCap(), "green ships should get a ten-hit outer shield screen");

        double shieldBefore = green.shield;
        for (int hit = 1; hit <= green.externalShieldGateHitCap(); hit++) {
            green.takeDamage(1, green.x + green.radius, green.y, 0.0, 0.0);
            assertEquals(shieldBefore, green.shield, 1e-6, "outer shield should absorb green hit " + hit);
            assertEquals(green.externalShieldGateHitCap() - hit, green.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_FORE),
                    "green fore shield gate should count down per hit");
            assertEquals(green.externalShieldGateHitCap(), green.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_LEFT),
                    "green side shield gates should stay fresh while the bow is being hit");
        }

        green.takeDamage(1, green.x + green.radius, green.y, 0.0, 0.0);
        assertTrue(green.shield < shieldBefore, "once green's outer screen is gone, the core shield should start taking damage");
    }

    @Test
    void redShieldGateAbsorbsOneHitBeforeCoreShieldDrops() {
        FleetShip red = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);

        assertEquals(1, red.externalShieldGateHitCap(), "red ships should only get a one-hit outer shield screen");

        double shieldBefore = red.shield;
        red.takeDamage(1, red.x + red.radius, red.y, 0.0, 0.0);
        assertEquals(shieldBefore, red.shield, 1e-6, "red outer shield should absorb the first hit");
        assertEquals(0, red.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_FORE),
                "red fore shield gate should be spent after the first hit");
        assertEquals(1, red.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_REAR),
                "red rear shield gate should still be ready");

        red.takeDamage(1, red.x + red.radius, red.y, 0.0, 0.0);
        assertTrue(red.shield < shieldBefore, "red core shield should start taking damage on the second hit");

        double shieldAfterForeDrop = red.shield;
        red.takeDamage(1, red.x - red.radius, red.y, 0.0, 0.0);
        assertEquals(shieldAfterForeDrop, red.shield, 1e-6,
                "red rear shield gate should still absorb its first hit even after the bow gate is gone");
    }

    @Test
    void yellowArmorGateBlocksInteriorPenetrationForFirstFiveHits() {
        FleetShip yellow = doctrinalShip(ShipRole.PATROL, Faction.TEAM_D);

        assertEquals(5, yellow.armorGateHitCap(), "yellow armor should gate five hits before penetration");

        for (int hit = 1; hit <= yellow.armorGateHitCap(); hit++) {
            yellow.takeDamage(12, yellow.x + yellow.radius, yellow.y, 0.0, 0.0);
            assertEquals(yellow.armorGateHitCap() - hit, yellow.armorGateHitsRemaining(Ship.SHIELD_FACE_FORE),
                    "yellow fore armor gate should count down per hit");
            assertEquals(yellow.armorGateHitCap(), yellow.armorGateHitsRemaining(Ship.SHIELD_FACE_REAR),
                    "yellow rear armor gate should stay fresh while the bow is being hit");
        }

        yellow.takeDamage(12, yellow.x - yellow.radius, yellow.y, 0.0, 0.0);
        assertEquals(yellow.armorGateHitCap() - 1, yellow.armorGateHitsRemaining(Ship.SHIELD_FACE_REAR),
                "yellow rear armor gate should start counting down independently once the stern is hit");
    }

    private static FleetShip doctrinalShip(ShipRole role, Faction faction) {
        FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
        DoctrineRegistry.applyToShip(ship);
        ship.resetShieldState();
        if (ship.shieldActive && ship.shieldMax > 0.0) {
            ship.shield = ship.shieldMax;
        } else {
            ship.shield = 0.0;
        }
        return ship;
    }
}
