import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuBattlePanelTest {
    @Test
    void menuSandboxSuppressesAudioAndUsesNaturalFourFactionShipPool() throws Exception {
        MainMenuBattlePanel panel = new MainMenuBattlePanel(1.0);
        GameContext ctx = context(panel);

        assertTrue(ctx.suppressAudio);
        AudioSystem.onExplosion(ctx, ctx.WORLD_W * 0.5, ctx.WORLD_H * 0.5);
        assertTrue(ctx.audioEvents.isEmpty(), "menu attract-mode combat should not emit SFX events");

        Set<Faction> factions = Set.of(menuFactions());
        assertEquals(Set.of(Faction.ALLY, Faction.ENEMY, Faction.TEAM_C, Faction.TEAM_D), factions);
        assertFalse(factions.contains(Faction.PLAYER), "main menu should not spawn player-team blue separately from ally blue");
        assertFalse(factions.contains(Faction.TEAM_E), "custom mission team should stay out of main-menu attract mode");
        assertFalse(factions.contains(Faction.BRIGHT_YELLOW), "campaign-only yellow split factions should stay out of main-menu attract mode");
        assertFalse(factions.contains(Faction.DARK_YELLOW), "campaign-only yellow split factions should stay out of main-menu attract mode");

        Set<ShipRole> roles = Arrays.stream(menuRoles()).collect(Collectors.toSet());
        assertFalse(roles.contains(ShipRole.BASE));
        assertFalse(roles.contains(ShipRole.STATIC_TURRET));
        assertTrue(roles.contains(ShipRole.PICKET));
        assertTrue(roles.contains(ShipRole.CRUISER));
        assertTrue(roles.contains(ShipRole.BATTLESHIP));
        assertTrue(roles.contains(ShipRole.HYPERWEAPON_TITAN));
        assertTrue(roles.contains(ShipRole.MOTHERSHIP));

        assertTrue(ctx.ships.stream().allMatch(ship -> ship != null && ship.attractModeStaggerPrimaryFire),
                "main-menu ships should use single/staggered primary shots instead of synchronized salvos");
    }

    private static GameContext context(MainMenuBattlePanel panel) throws Exception {
        Field field = MainMenuBattlePanel.class.getDeclaredField("ctx");
        field.setAccessible(true);
        return (GameContext) field.get(panel);
    }

    private static Faction[] menuFactions() throws Exception {
        Field field = MainMenuBattlePanel.class.getDeclaredField("MENU_FACTIONS");
        field.setAccessible(true);
        return (Faction[]) field.get(null);
    }

    private static ShipRole[] menuRoles() throws Exception {
        Field field = MainMenuBattlePanel.class.getDeclaredField("MENU_ROLES");
        field.setAccessible(true);
        return (ShipRole[]) field.get(null);
    }
}
