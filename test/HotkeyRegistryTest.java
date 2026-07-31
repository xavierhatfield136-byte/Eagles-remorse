import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

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
        assertTrue(HotkeyRegistry.currentContextLegend(ctx).stream().anyMatch(row -> row.contains("RT/LMB fire all")));
        HotkeyRegistry.noteKeyboardInput();
        assertTrue(HotkeyRegistry.currentContextLegend(ctx).stream().anyMatch(row -> row.contains("H crew")));
    }

    @Test
    void replaceAllSerializedAllowsAtomicMovementKeySwaps() {
        Properties previous = ControlSettingsStore.load();
        try {
            HotkeyRegistry.restoreAllDefaults();
            Map<String, String> keyboard = currentKeyboardMap();
            keyboard.put("moveForward", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false).toString());
            keyboard.put("moveBackward", KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false).toString());

            HotkeyRegistry.RemapResult result = HotkeyRegistry.replaceAllSerialized(keyboard, currentMouseMap());

            assertTrue(result.accepted(), result.message());
            assertEquals("S", HotkeyRegistry.label("moveForward"));
            assertEquals("W", HotkeyRegistry.label("moveBackward"));
        } finally {
            HotkeyRegistry.restoreAllDefaults();
            ControlSettingsStore.save(previous);
        }
    }

    @Test
    void playerMovementUsesRemappedHotkeys() {
        Properties previous = ControlSettingsStore.load();
        try {
            HotkeyRegistry.restoreAllDefaults();
            Map<String, String> keyboard = currentKeyboardMap();
            keyboard.put("moveForward", KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD8, 0, false).toString());

            HotkeyRegistry.RemapResult result = HotkeyRegistry.replaceAllSerialized(keyboard, currentMouseMap());
            assertTrue(result.accepted(), result.message());

            PlayerControl control = new PlayerControl(new Player(ShipRole.FRIGATE, 100.0, 100.0));
            control.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_NUMPAD8));

            assertTrue(control.snapshot().up);
            control.keyReleased(keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_NUMPAD8));
            assertTrue(!control.snapshot().up);
        } finally {
            HotkeyRegistry.restoreAllDefaults();
            ControlSettingsStore.save(previous);
        }
    }

    @Test
    void hudHelpKeepsCrewOrdersOutOfReferenceRows() {
        String help = HotkeyRegistry.renderedHelpText(true, GameContext.HudDetail.FULL).toLowerCase(java.util.Locale.US);

        assertTrue(help.contains("combat"));
        assertTrue(help.contains("nav"));
        assertTrue(!help.contains("crew order"));
    }

    private static Map<String, String> currentKeyboardMap() {
        Map<String, String> keyboard = new LinkedHashMap<>();
        for (HotkeyRegistry.Binding binding : HotkeyRegistry.bindings()) {
            keyboard.put(binding.action(), binding.stroke().toString());
        }
        return keyboard;
    }

    private static Map<String, Integer> currentMouseMap() {
        Map<String, Integer> mouse = new LinkedHashMap<>();
        for (String action : HotkeyRegistry.mouseActionIds()) {
            mouse.put(action, HotkeyRegistry.mouseButton(action));
        }
        return mouse;
    }

    private static KeyEvent keyEvent(int id, int keyCode) {
        return new KeyEvent(new Canvas(), id, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

}
