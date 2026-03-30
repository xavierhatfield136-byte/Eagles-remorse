import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipIdentityRegistryTest {

    @Test
    void everyFactionRolePairHasConfiguredRoleBonus() {
        for (Faction faction : Faction.fourTeamFactions()) {
            for (ShipRole role : ShipRole.values()) {
                ShipIdentityRegistry.RoleBonus bonus = ShipIdentityRegistry.roleBonusFor(faction, role);
                assertNotEquals(ShipIdentityRegistry.IdentityStat.NONE, bonus.stat,
                        faction + " " + role + " should have a role stat bonus");
                assertTrue(bonus.multiplier > 1.0,
                        faction + " " + role + " should have a positive bonus multiplier");
                assertTrue(bonus.name != null && !bonus.name.isBlank(),
                        faction + " " + role + " should expose a role bonus name");
            }
        }
    }

    @Test
    void sameRoleDiffersAcrossFactions() {
        for (ShipRole role : ShipRole.values()) {
            Set<ShipIdentityRegistry.IdentityStat> stats = new HashSet<>();
            for (Faction faction : Faction.fourTeamFactions()) {
                stats.add(ShipIdentityRegistry.roleBonusFor(faction, role).stat);
            }
            assertEquals(4, stats.size(), role + " should use different defining stats across factions");
        }
    }

    @Test
    void playerUsesBlueRoleBonuses() {
        for (ShipRole role : ShipRole.values()) {
            ShipIdentityRegistry.RoleBonus playerBonus = ShipIdentityRegistry.roleBonusFor(Faction.PLAYER, role);
            ShipIdentityRegistry.RoleBonus allyBonus = ShipIdentityRegistry.roleBonusFor(Faction.ALLY, role);
            assertEquals(allyBonus.stat, playerBonus.stat, role + " player bonus stat should match blue fleet");
            assertEquals(allyBonus.multiplier, playerBonus.multiplier, 1e-9, role + " player bonus multiplier should match blue fleet");
        }
    }

    @Test
    void everyFactionHasConfiguredFactionTrait() {
        for (Faction faction : Faction.values()) {
            ShipIdentityRegistry.FactionTrait trait = ShipIdentityRegistry.factionTraitFor(faction);
            assertNotEquals(ShipIdentityRegistry.FactionTraitId.NONE, trait.id, faction + " should have a faction trait");
            assertTrue(trait.name != null && !trait.name.isBlank(), faction + " should expose a faction trait name");
        }
    }

    @Test
    void redMomentumIncreasesWeaponOutputAfterFiring() {
        FleetShip redBattleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ENEMY, 0.0, 0.0);

        double beforeDamage = redBattleship.weaponDamageMultiplier();
        double beforeCycle = redBattleship.weaponCycleRateMultiplier();

        redBattleship.onFiredWeapon();

        assertTrue(redBattleship.weaponDamageMultiplier() > beforeDamage, "red firing momentum should boost weapon damage");
        assertTrue(redBattleship.weaponCycleRateMultiplier() > beforeCycle, "red firing momentum should boost cycle rate");
    }

    @Test
    void sameClassUsesDifferentBonusesInDifferentFleets() {
        FleetShip blueFrigate = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        FleetShip redFrigate = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 0.0, 0.0);
        FleetShip greenFrigate = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        FleetShip yellowFrigate = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_D, 0.0, 0.0);

        Set<ShipIdentityRegistry.IdentityStat> stats = new HashSet<>();
        stats.add(blueFrigate.roleBonusStat());
        stats.add(redFrigate.roleBonusStat());
        stats.add(greenFrigate.roleBonusStat());
        stats.add(yellowFrigate.roleBonusStat());

        assertEquals(4, stats.size(), "frigates should not share the same defining stat across factions");
    }
}
