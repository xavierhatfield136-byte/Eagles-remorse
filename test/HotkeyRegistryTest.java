import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyRegistryTest {
    @Test
    void renderedHudHelpUsesCanonicalRegisteredHotkeys() {
        Player player = new Player(ShipRole.CARRIER, 100.0, 100.0);

        assertEquals(
                HotkeyRegistry.hudHelpRows(true, GameContext.HudDetail.FULL),
                Renderer.buildHudControlsRows(player, GameContext.HudDetail.FULL));
        assertTrue(HotkeyRegistry.renderedHelpText(true, GameContext.HudDetail.FULL).contains(
                HotkeyRegistry.label("fullscreen") + " fullscreen"));
    }

    @Test
    void registeredUnqualifiedHotkeysHaveNoDuplicatesWithinScope() {
        assertEquals(java.util.List.of(), HotkeyRegistry.duplicateUnqualifiedBindings());
    }

    @Test
    void registryDefinesEveryInputOwnershipScope() {
        assertEquals(EnumSet.allOf(HotkeyRegistry.Scope.class), HotkeyRegistry.scopes());
    }

    @Test
    void controlCatalogSupportsSearchMouseControllerGlyphsAndContextLegend() {
        assertTrue(HotkeyRegistry.search("warp").stream().anyMatch(binding -> binding.action().equals("battlefieldWarp")));
        assertEquals(1, HotkeyRegistry.mouseButton("primaryDown"));
        assertEquals("RT", HotkeyRegistry.controllerButton("primaryDown"));
        HotkeyRegistry.noteControllerInput();
        assertEquals("RT", HotkeyRegistry.glyph("primaryDown"));

        GameContext ctx = new GameContext(null);
        assertTrue(HotkeyRegistry.currentContextLegend(ctx).stream().anyMatch(row -> row.contains("RT/LMB fire")));
        HotkeyRegistry.noteKeyboardInput();
    }

}
