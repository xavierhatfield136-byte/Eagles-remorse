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
    void redFleetTeleporterTitanArmorTakesDamageAndCanBePenetrated() {
        FleetShip red = doctrinalShip(ShipRole.FLEET_TELEPORTER_TITAN, Faction.ENEMY);
        red.shield = 0.0;
        red.shieldActive = false;

        ShipRoomLayout.RoomId armorRoom = ShipRoomLayout.RoomId.BOW_ARMOR;
        double armorBefore = roomHp(red, armorRoom);
        assertTrue(armorBefore > 0.0, "red teleporter titan should have damageable bow armor");
        assertEquals(0.0, roomHpMax(red, ShipRoomLayout.RoomId.BOW_ARMOR_INNER), 1e-6,
                "red teleporter titan should not get yellow's inner armor layer");
        assertEquals(0, red.armorGateHitCap(),
                "red teleporter titan armor should not use yellow's armor gate");

        red.takeDamage(42, red.x + red.radius, red.y, -1.0, 0.0);

        assertTrue(roomHp(red, armorRoom) < armorBefore,
                "red teleporter titan bow armor should lose HP on a direct hit");

        int hpBefore = red.hp;
        for (int hit = 0; hit < 96 && red.hp == hpBefore; hit++) {
            red.takeDamage(96, red.x + red.radius, red.y, -1.0, 0.0);
        }

        assertEquals(0.0, roomHp(red, armorRoom), 1e-6,
                "red teleporter titan bow armor should be breakable");
        assertTrue(red.hp < hpBefore,
                "red teleporter titan should eventually take hull damage after armor breaks");
    }

    @Test
    void yellowArmorUsesLocalRoomHpInsteadOfGateCounters() {
        FleetShip yellow = doctrinalShip(ShipRole.PATROL, Faction.TEAM_D);
        yellow.shield = 0.0;
        yellow.shieldActive = false;

        ShipRoomLayout.RoomId bowArmor = ShipRoomLayout.RoomId.BOW_ARMOR;
        double armorBefore = roomHp(yellow, bowArmor);
        int beforeEvents = yellow.recentRoomDamageEvents().size();

        assertEquals(0, yellow.armorGateHitCap(), "armor protection should come from armor room HP, not gate counters");

        yellow.takeDamage(1, yellow.x + yellow.radius, yellow.y, -1.0, 0.0);

        assertTrue(roomHp(yellow, bowArmor) < armorBefore, "the local bow armor room should absorb the hit");
        assertEquals(0, nonArmorDamageEventsSince(yellow, beforeEvents),
                "live armor should block normal interior room damage");
    }

    @Test
    void yellowArmorTakesDamageOnEveryArmoredHullFace() {
        for (ShipRole role : ShipRole.values()) {
            if (role == ShipRole.FIGHTER || role == ShipRole.BOMBER || role == ShipRole.DRONE) continue;
            for (int face = 0; face < 4; face++) {
                FleetShip yellow = doctrinalShip(role, Faction.TEAM_D);
                yellow.shield = 0.0;
                yellow.shieldActive = false;

                ShipRoomLayout.RoomId armorRoom = armorRoomForFace(face, false);
                double before = roomHp(yellow, armorRoom);
                double max = roomHpMax(yellow, armorRoom);
                assertTrue(max > 0.0, role + " should have live yellow armor room " + armorRoom);

                yellow.takeDamage(14, hitXForFace(yellow, face), hitYForFace(yellow, face),
                        impactVxForFace(face), impactVyForFace(face));

                double after = roomHp(yellow, armorRoom);
                assertTrue(after < before,
                        role + " " + armorRoom + " should lose armor HP after a direct hit");
            }
        }
    }

    @Test
    void yellowArmorCanBeBrokenAndPenetratedOnEveryArmoredHullFace() {
        for (ShipRole role : ShipRole.values()) {
            if (role == ShipRole.FIGHTER || role == ShipRole.BOMBER || role == ShipRole.DRONE) continue;
            for (int face = 0; face < 4; face++) {
                FleetShip yellow = doctrinalShip(role, Faction.TEAM_D);
                yellow.shield = 0.0;
                yellow.shieldActive = false;

                ShipRoomLayout.RoomId outerArmor = armorRoomForFace(face, false);
                int beforeEvents = yellow.recentRoomDamageEvents().size();

                for (int hit = 0; hit < 96 && nonArmorDamageEventsSince(yellow, beforeEvents) == 0; hit++) {
                    yellow.takeDamage(42, hitXForFace(yellow, face), hitYForFace(yellow, face),
                            impactVxForFace(face), impactVyForFace(face));
                }

                assertEquals(0.0, roomHp(yellow, outerArmor), 1e-6,
                        role + " " + outerArmor + " should be breakable");
                assertTrue(nonArmorDamageEventsSince(yellow, beforeEvents) > 0,
                        role + " should take interior damage through " + outerArmor + " once that armor room is destroyed");
            }
        }
    }

    @Test
    void oneBrokenArmorRoomAllowsPenetrationWithoutBreakingOtherSides() {
        FleetShip yellow = doctrinalShip(ShipRole.CRUISER, Faction.TEAM_D);
        yellow.shield = 0.0;
        yellow.shieldActive = false;

        ShipRoomLayout.RoomId bowArmor = ShipRoomLayout.RoomId.BOW_ARMOR;
        ShipRoomLayout.RoomId aftArmor = ShipRoomLayout.RoomId.AFT_ARMOR;
        int beforeEvents = yellow.recentRoomDamageEvents().size();

        for (int hit = 0; hit < 96 && nonArmorDamageEventsSince(yellow, beforeEvents) == 0; hit++) {
            yellow.takeDamage(42, yellow.x + yellow.radius, yellow.y, -1.0, 0.0);
        }

        assertEquals(0.0, roomHp(yellow, bowArmor), 1e-6,
                "bow armor should be the only required local armor buffer");
        assertTrue(nonArmorDamageEventsSince(yellow, beforeEvents) > 0,
                "hits on the broken bow armor side should reach interior rooms");
        assertTrue(roomHp(yellow, aftArmor) > 0.0,
                "penetrating bow armor should not require the aft armor room to be destroyed");
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

    private static ShipRoomLayout.RoomId armorRoomForFace(int face, boolean inner) {
        return switch (face) {
            case Ship.SHIELD_FACE_REAR -> inner ? ShipRoomLayout.RoomId.AFT_ARMOR_INNER : ShipRoomLayout.RoomId.AFT_ARMOR;
            case Ship.SHIELD_FACE_LEFT -> inner ? ShipRoomLayout.RoomId.DORSAL_ARMOR_INNER : ShipRoomLayout.RoomId.DORSAL_ARMOR;
            case Ship.SHIELD_FACE_RIGHT -> inner ? ShipRoomLayout.RoomId.VENTRAL_ARMOR_INNER : ShipRoomLayout.RoomId.VENTRAL_ARMOR;
            default -> inner ? ShipRoomLayout.RoomId.BOW_ARMOR_INNER : ShipRoomLayout.RoomId.BOW_ARMOR;
        };
    }

    private static double hitXForFace(Ship ship, int face) {
        return switch (face) {
            case Ship.SHIELD_FACE_REAR -> ship.x - ship.radius;
            case Ship.SHIELD_FACE_LEFT, Ship.SHIELD_FACE_RIGHT -> ship.x;
            default -> ship.x + ship.radius;
        };
    }

    private static double hitYForFace(Ship ship, int face) {
        return switch (face) {
            case Ship.SHIELD_FACE_LEFT -> ship.y - ship.radius;
            case Ship.SHIELD_FACE_RIGHT -> ship.y + ship.radius;
            default -> ship.y;
        };
    }

    private static double impactVxForFace(int face) {
        return switch (face) {
            case Ship.SHIELD_FACE_REAR -> 1.0;
            default -> -1.0;
        };
    }

    private static double impactVyForFace(int face) {
        return switch (face) {
            case Ship.SHIELD_FACE_LEFT -> 1.0;
            case Ship.SHIELD_FACE_RIGHT -> -1.0;
            default -> 0.0;
        };
    }

    private static int nonArmorDamageEventsSince(Ship ship, int startIndex) {
        int count = 0;
        for (int i = Math.max(0, startIndex); i < ship.recentRoomDamageEvents().size(); i++) {
            Ship.RoomDamageEvent event = ship.recentRoomDamageEvents().get(i);
            if (event != null && event.roomId != null && event.damage > 0.0
                    && !event.fromHazard && !ShipRoomLayout.isArmorRoom(event.roomId)) {
                count++;
            }
        }
        return count;
    }

    private static double roomHp(Ship ship, ShipRoomLayout.RoomId roomId) {
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.roomId == roomId) return room.hp;
        }
        return 0.0;
    }

    private static double roomHpMax(Ship ship, ShipRoomLayout.RoomId roomId) {
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.roomId == roomId) return room.hpMax;
        }
        return 0.0;
    }
}
