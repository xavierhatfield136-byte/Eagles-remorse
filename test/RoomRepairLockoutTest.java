import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomRepairLockoutTest {

    @Test
    void armorAndArmoryRoomsCannotRepairForTenSecondsAfterDamage() throws Exception {
        FleetShip ship = doctrinalShip(ShipRole.BULWARK_TITAN, Faction.TEAM_D);

        assertRepairLockedAfterDamage(ship, ShipRoomLayout.RoomId.DORSAL_ARMOR);
        assertRepairLockedAfterDamage(ship, ShipRoomLayout.RoomId.MAGAZINES);
    }

    @Test
    void blueArmorCannotBeRepairedByHullHealingDuringLockout() throws Exception {
        FleetShip ship = doctrinalShip(ShipRole.BULWARK_TITAN, Faction.ALLY);
        ShipRoomLayout.RoomId armor = ShipRoomLayout.RoomId.DORSAL_ARMOR;

        damageRoomDirectly(ship, armor, 40.0);
        double damaged = ship.roomHealthFraction(armor);
        assertTrue(damaged < 1.0, "blue armor should take test damage");

        ship.healHull(10_000.0);

        assertEquals(damaged, ship.roomHealthFraction(armor), 1e-6,
                "blue armor should ignore hull healing while the armor repair lockout is active");

        ship.update(Ship.ARMORY_ARMOR_ROOM_REPAIR_LOCKOUT_SECONDS + 0.05);
        ship.healHull(10_000.0);

        assertTrue(ship.roomHealthFraction(armor) > damaged,
                "blue armor should accept hull healing after the lockout expires");
    }

    @Test
    void armorRepairLockoutResetsFromTheLastHit() throws Exception {
        FleetShip ship = doctrinalShip(ShipRole.BULWARK_TITAN, Faction.ALLY);
        ShipRoomLayout.RoomId armor = ShipRoomLayout.RoomId.DORSAL_ARMOR;

        damageRoomDirectly(ship, armor, 20.0);
        ship.update(Ship.ARMORY_ARMOR_ROOM_REPAIR_LOCKOUT_SECONDS * 0.5);
        damageRoomDirectly(ship, armor, 20.0);

        assertEquals(Ship.ARMORY_ARMOR_ROOM_REPAIR_LOCKOUT_SECONDS,
                ship.roomRepairLockoutSecondsForTests(armor), 1e-6,
                "each fresh armor hit should restart the full repair lockout");
    }

    @Test
    void nonGreenLineShipsUseSingleOuterArmorLayer() {
        FleetShip redTitan = doctrinalShip(ShipRole.BULWARK_TITAN, Faction.ENEMY);
        FleetShip blueTitan = doctrinalShip(ShipRole.BULWARK_TITAN, Faction.ALLY);
        FleetShip yellowTitan = doctrinalShip(ShipRole.BULWARK_TITAN, Faction.TEAM_D);

        assertTrue(roomMax(redTitan, ShipRoomLayout.RoomId.DORSAL_ARMOR) > 0.0,
                "red ships keep a damageable outer armor belt");
        assertEquals(0.0, roomMax(redTitan, ShipRoomLayout.RoomId.DORSAL_ARMOR_INNER), 1e-6,
                "red ships should not get an inner armor layer");
        assertTrue(roomMax(blueTitan, ShipRoomLayout.RoomId.DORSAL_ARMOR) > 0.0,
                "blue ships keep a damageable outer armor belt");
        assertEquals(0.0, roomMax(blueTitan, ShipRoomLayout.RoomId.DORSAL_ARMOR_INNER), 1e-6,
                "blue ships should not get an inner armor layer");
        assertTrue(roomMax(yellowTitan, ShipRoomLayout.RoomId.DORSAL_ARMOR) > 0.0,
                "yellow ships keep the outer armor layer");
        assertEquals(0.0, roomMax(yellowTitan, ShipRoomLayout.RoomId.DORSAL_ARMOR_INNER), 1e-6,
                "yellow ships should not get a second inner armor layer");
    }

    private static void assertRepairLockedAfterDamage(FleetShip ship, ShipRoomLayout.RoomId roomId) throws Exception {
        damageRoomDirectly(ship, roomId, 40.0);
        double damaged = ship.roomHealthFraction(roomId);
        assertTrue(damaged < 1.0, roomId + " should take test damage");
        assertEquals(Ship.ARMORY_ARMOR_ROOM_REPAIR_LOCKOUT_SECONDS,
                ship.roomRepairLockoutSecondsForTests(roomId), 1e-6);

        ship.applySupportField(1.0, 0.0, 1.0);
        assertEquals(damaged, ship.roomHealthFraction(roomId), 1e-6,
                roomId + " should ignore repair while the lockout is active");

        ship.update(Ship.ARMORY_ARMOR_ROOM_REPAIR_LOCKOUT_SECONDS + 0.05);
        ship.applySupportField(1.0, 0.0, 1.0);
        assertTrue(ship.roomHealthFraction(roomId) > damaged,
                roomId + " should accept repair after the lockout expires");
    }

    private static void damageRoomDirectly(FleetShip ship, ShipRoomLayout.RoomId roomId, double damage) throws Exception {
        ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ship.role, ship.faction, roomId);
        Method damageRoom = Ship.class.getDeclaredMethod("damageRoom",
                ShipRoomLayout.RoomDef.class,
                double.class,
                double.class,
                double.class,
                boolean.class,
                boolean.class);
        damageRoom.setAccessible(true);
        damageRoom.invoke(ship, room, damage, 0.0, 0.0, false, false);
    }

    private static double roomMax(Ship ship, ShipRoomLayout.RoomId roomId) {
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.roomId == roomId) return room.hpMax;
        }
        return 0.0;
    }

    private static FleetShip doctrinalShip(ShipRole role, Faction faction) {
        FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
        DoctrineRegistry.applyToShip(ship);
        ship.resetShieldState();
        ship.shield = 0.0;
        ship.shieldActive = false;
        return ship;
    }
}
