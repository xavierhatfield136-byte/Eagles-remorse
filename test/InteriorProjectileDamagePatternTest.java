import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteriorProjectileDamagePatternTest {

    @Test
    void bluePierceDamagesLineWithFlatFollowThroughDamage() {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);

        ship.takePenetratingInternalDamage(30, ship.x + ship.radius, ship.y,
                -1.0, 0.0, Ship.InteriorHitProfile.BLUE_PIERCE);

        List<Ship.RoomDamageEvent> hits = nonHazardEventsSince(ship, 0);
        assertTrue(uniqueRoomCount(hits) >= 3, "blue piercing bolts should carry through a full room line");
        assertEquals(hits.get(0).damage * 0.75, hits.get(1).damage, 1e-6,
                "second room in a blue shock-cannon line should take 75% of the first room's damage");
        assertEquals(hits.get(0).damage * 0.75, hits.get(2).damage, 1e-6,
                "later rooms in a blue shock-cannon line should keep the same 75% follow-through damage");
    }

    @Test
    void bluePierceCarriesDestroyedRoomDamageForward() throws Exception {
        FleetShip scout = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);
        scout.takePenetratingInternalDamage(30, scout.x + scout.radius, scout.y,
                -1.0, 0.0, Ship.InteriorHitProfile.BLUE_PIERCE);
        ShipRoomLayout.RoomId firstLineRoom = nonHazardEventsSince(scout, 0).get(0).roomId;

        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);
        damageRoomDirectly(ship, firstLineRoom, 10_000.0, false);
        int beforeShotEvents = ship.recentRoomDamageEvents().size();

        ship.takePenetratingInternalDamage(30, ship.x + ship.radius, ship.y,
                -1.0, 0.0, Ship.InteriorHitProfile.BLUE_PIERCE);

        List<Ship.RoomDamageEvent> hits = nonHazardEventsSince(ship, beforeShotEvents);
        assertTrue(hits.size() >= 1, "destroyed first room should not stop the blue piercing line");
        assertTrue(hits.stream().noneMatch(hit -> hit.roomId == firstLineRoom),
                "destroyed rooms should be skipped instead of logged as munition hits");
        assertTrue(hits.get(0).damage > 30.0 * 0.66 * 0.75,
                "damage assigned to a destroyed room should carry into the next live room in line");
    }

    @Test
    void redExplosivePassesThroughDestroyedCarrierSideArmor() throws Exception {
        FleetShip carrier = doctrinalShip(ShipRole.CARRIER, Faction.ALLY);
        ShipRoomLayout.RoomId armorRoom = ShipRoomLayout.RoomId.DORSAL_ARMOR;
        damageRoomDirectly(carrier, armorRoom, 10_000.0, false);
        assertEquals(0.0, roomHp(carrier, armorRoom), 1e-6,
                "test setup should destroy the carrier side armor");

        int hpBefore = carrier.hp;
        int beforeEvents = carrier.recentRoomDamageEvents().size();

        carrier.takeDamage(64, carrier.x, carrier.y - carrier.radius,
                0.0, 1.0, Ship.InteriorHitProfile.RED_EXPLOSIVE);

        List<Ship.RoomDamageEvent> hits = nonHazardEventsSince(carrier, beforeEvents);
        assertTrue(hits.stream().noneMatch(hit -> hit.roomId == armorRoom),
                "destroyed carrier armor should not catch or detonate red APHE rounds");
        assertTrue(hits.stream().anyMatch(hit -> !ShipRoomLayout.isArmorRoom(hit.roomId)),
                "red APHE should pass destroyed armor and damage live interior rooms");
        assertTrue(carrier.hp < hpBefore,
                "carrier hull should continue taking damage after its side armor is destroyed");
    }

    @Test
    void lasersDamageSequentialRoomsAlongNarrowBeamPath() {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.TEAM_C);

        ship.takePenetratingInternalDamage(6, ship.x + ship.radius, ship.y,
                -1.0, 0.0, Ship.InteriorHitProfile.LASER_LINE);

        assertTrue(damagedRoomCount(ship) >= 2, "laser hits should score multiple rooms along the beam path");
        assertTrue(burningRoomCount(ship) >= 2, "laser hits should leave a burning line through the ship");
    }

    @Test
    void missilesDamageMoreRoomsThanRedExplosiveRounds() {
        FleetShip redShip = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);
        FleetShip missileShip = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);

        redShip.takePenetratingInternalDamage(7, redShip.x + redShip.radius, redShip.y,
                -1.0, 0.0, Ship.InteriorHitProfile.RED_EXPLOSIVE);
        missileShip.takePenetratingInternalDamage(7, missileShip.x + missileShip.radius, missileShip.y,
                -1.0, 0.0, Ship.InteriorHitProfile.MISSILE_BLAST);

        assertTrue(damagedRoomCount(redShip) >= 2, "red explosive rounds should damage nearby rooms");
        assertTrue(damagedRoomCount(missileShip) > damagedRoomCount(redShip),
                "missiles should damage a wider set of rooms than red explosive rounds");
    }

    @Test
    void redExplosiveRoundsStayNearDetonationRoom() {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);

        ship.takePenetratingInternalDamage(18, ship.x + ship.radius, ship.y,
                -1.0, 0.0, Ship.InteriorHitProfile.RED_EXPLOSIVE);

        List<Ship.RoomDamageEvent> hits = nonHazardEventsSince(ship, 0);
        assertTrue(hits.size() >= 2, "red APHE rounds should damage the detonation room and neighbors");
        ShipRoomLayout.RoomDef detonation = ShipRoomLayout.roomForId(ship.role, ship.faction, hits.get(0).roomId);
        Set<ShipRoomLayout.RoomId> allowed = new HashSet<>();
        allowed.add(detonation.id);
        for (ShipRoomLayout.RoomId neighbor : detonation.neighbors) allowed.add(neighbor);
        for (Ship.RoomDamageEvent hit : hits) {
            assertTrue(allowed.contains(hit.roomId),
                    "red APHE splash should stay in rooms adjacent to the detonation room");
        }
    }

    @Test
    void deadRoomRedirectsDamageToHealthiestNearbyRoom() throws Exception {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);
        ShipRoomLayout.RoomId deadRoom = ShipRoomLayout.RoomId.CARGO_BAY;
        damageRoomDirectly(ship, deadRoom, 10_000.0, false);

        ShipRoomLayout.RoomId expected = healthiestLiveNeighbor(ship, deadRoom);
        assertTrue(expected != null, "test room should have at least one live neighbor");
        double expectedBefore = ship.roomHealthFraction(expected);

        damageRoomDirectly(ship, deadRoom, 24.0, true);

        assertTrue(ship.roomHealthFraction(expected) < expectedBefore,
                "dead-room hits should redirect into the healthiest nearby live room");
    }

    @Test
    void redExplosiveRoundDamagesLiveRoomWhenDetonationRoomIsDead() throws Exception {
        FleetShip scout = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);
        scout.takePenetratingInternalDamage(18, scout.x + scout.radius, scout.y,
                -1.0, 0.0, Ship.InteriorHitProfile.RED_EXPLOSIVE);
        ShipRoomLayout.RoomId detonationRoom = nonHazardEventsSince(scout, 0).get(0).roomId;

        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);
        damageRoomDirectly(ship, detonationRoom, 10_000.0, false);
        int beforeShotEvents = ship.recentRoomDamageEvents().size();

        ship.takePenetratingInternalDamage(18, ship.x + ship.radius, ship.y,
                -1.0, 0.0, Ship.InteriorHitProfile.RED_EXPLOSIVE);

        List<Ship.RoomDamageEvent> hits = nonHazardEventsSince(ship, beforeShotEvents);
        assertTrue(hits.stream().noneMatch(hit -> hit.roomId == detonationRoom),
                "dead detonation rooms should be skipped instead of catching red APHE");
        assertTrue(hits.stream().anyMatch(hit -> hit.roomId != null && !ShipRoomLayout.isArmorRoom(hit.roomId)),
                "red APHE should continue into live rooms when the original detonation room is dead");
    }

    @Test
    void redExplosiveHullHitsAlwaysResolveToACompartment() {
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0 * i) / 16.0;
            double nx = Math.cos(angle);
            double ny = Math.sin(angle);
            FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);

            ship.takePenetratingInternalDamage(18,
                    ship.x + nx * ship.radius,
                    ship.y + ny * ship.radius,
                    -nx,
                    -ny,
                    Ship.InteriorHitProfile.RED_EXPLOSIVE);

            assertTrue(nonHazardEventsSince(ship, 0).size() > 0,
                    "red APHE hit at hull angle index " + i + " should damage a room");
        }
    }

    private static FleetShip doctrinalShip(ShipRole role, Faction faction) {
        FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
        DoctrineRegistry.applyToShip(ship);
        ship.resetShieldState();
        ship.shield = 0.0;
        ship.shieldActive = false;
        return ship;
    }

    private static int damagedRoomCount(Ship ship) {
        Set<ShipRoomLayout.RoomId> damaged = new HashSet<>();
        for (Ship.RoomDamageEvent event : ship.recentRoomDamageEvents()) {
            if (event != null && event.roomId != null && event.damage > 0.0) damaged.add(event.roomId);
        }
        return damaged.size();
    }

    private static int uniqueRoomCount(List<Ship.RoomDamageEvent> events) {
        Set<ShipRoomLayout.RoomId> damaged = new HashSet<>();
        for (Ship.RoomDamageEvent event : events) {
            if (event != null && event.roomId != null && event.damage > 0.0) damaged.add(event.roomId);
        }
        return damaged.size();
    }

    private static List<Ship.RoomDamageEvent> nonHazardEventsSince(Ship ship, int startIndex) {
        List<Ship.RoomDamageEvent> out = new ArrayList<>();
        List<Ship.RoomDamageEvent> events = ship.recentRoomDamageEvents();
        for (int i = Math.max(0, startIndex); i < events.size(); i++) {
            Ship.RoomDamageEvent event = events.get(i);
            if (event != null && event.roomId != null && event.damage > 0.0 && !event.fromHazard) out.add(event);
        }
        return out;
    }

    private static int burningRoomCount(Ship ship) {
        int count = 0;
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.fireIntensity > 0.05) count++;
        }
        return count;
    }

    private static void damageRoomDirectly(FleetShip ship,
                                           ShipRoomLayout.RoomId roomId,
                                           double damage,
                                           boolean allowSaturation) throws Exception {
        ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ship.role, ship.faction, roomId);
        Method damageRoom = Ship.class.getDeclaredMethod("damageRoom",
                ShipRoomLayout.RoomDef.class,
                double.class,
                double.class,
                double.class,
                boolean.class,
                boolean.class);
        damageRoom.setAccessible(true);
        damageRoom.invoke(ship, room, damage, 0.0, 0.0, false, allowSaturation);
    }

    private static ShipRoomLayout.RoomId healthiestLiveNeighbor(Ship ship, ShipRoomLayout.RoomId roomId) {
        ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ship.role, ship.faction, roomId);
        if (room == null || room.neighbors == null) return null;
        ShipRoomLayout.RoomId best = null;
        double bestHp = Double.NEGATIVE_INFINITY;
        for (ShipRoomLayout.RoomId neighborId : room.neighbors) {
            double hp = roomHp(ship, neighborId);
            if (hp <= 1e-6) continue;
            int order = neighborId == null ? Integer.MAX_VALUE : neighborId.ordinal();
            int bestOrder = best == null ? Integer.MAX_VALUE : best.ordinal();
            if (hp > bestHp || (Math.abs(hp - bestHp) <= 1e-9 && order < bestOrder)) {
                best = neighborId;
                bestHp = hp;
            }
        }
        return best;
    }

    private static double roomHp(Ship ship, ShipRoomLayout.RoomId roomId) {
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.roomId == roomId) return room.hp;
        }
        return 0.0;
    }
}
