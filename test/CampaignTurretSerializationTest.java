import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignTurretSerializationTest {

    @Test
    void turretSerializationPersistsMissileRole() throws Exception {
        Player original = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        Turret missile = firstMissileTurret(original);
        assertNotNull(missile, "expected a missile turret on the battleship");
        missile.missileRole = Turret.MissileRole.ANTI_HEAVY;

        String data = serializeTurrets(original);

        Player restored = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        restoreTurrets(restored, data);

        Turret restoredMissile = firstMissileTurret(restored);
        assertNotNull(restoredMissile, "expected a missile turret after restore");
        assertEquals(Turret.MissileRole.ANTI_HEAVY, restoredMissile.missileRole,
                "expected missile role to survive turret serialization/restore");
    }

    @Test
    void turretRestoreDefaultsMissileRoleWhenLoadingLegacyData() throws Exception {
        Player original = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        Turret missile = firstMissileTurret(original);
        assertNotNull(missile, "expected a missile turret on the battleship");
        missile.missileRole = Turret.MissileRole.ANTI_HEAVY;

        String data = serializeTurrets(original);

        // Emulate legacy checkpoint format by removing the final field (missile role) from each turret entry.
        String[] entries = data.split(";");
        StringBuilder legacy = new StringBuilder();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            int last = entry.lastIndexOf('|');
            String trimmed = (last >= 0) ? entry.substring(0, last) : entry;
            if (legacy.length() > 0) legacy.append(';');
            legacy.append(trimmed);
        }

        Player restored = new Player(ShipRole.BATTLESHIP, 0.0, 0.0);
        restoreTurrets(restored, legacy.toString());

        Turret restoredMissile = firstMissileTurret(restored);
        assertNotNull(restoredMissile, "expected a missile turret after legacy restore");
        assertEquals(Turret.MissileRole.ANTI_MEDIUM, restoredMissile.missileRole,
                "legacy turret data should default missile role to ANTI_MEDIUM");
    }

    private static String serializeTurrets(Ship ship) throws Exception {
        Method m = CampaignSystem.class.getDeclaredMethod("serializeTurrets", Ship.class);
        m.setAccessible(true);
        return (String) m.invoke(null, ship);
    }

    private static void restoreTurrets(Ship ship, String data) throws Exception {
        Method m = CampaignSystem.class.getDeclaredMethod("restoreTurrets", Ship.class, String.class);
        m.setAccessible(true);
        m.invoke(null, ship, data);
    }

    private static Turret firstMissileTurret(Ship ship) {
        if (ship == null || ship.turrets == null) return null;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.kind == Turret.Kind.MISSILE) return turret;
        }
        return null;
    }
}

