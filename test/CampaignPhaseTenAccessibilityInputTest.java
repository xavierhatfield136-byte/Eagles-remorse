import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseTenAccessibilityInputTest {
    @Test
    void phaseTenAcceptanceContractCoversEveryChecklistCategory() {
        assertEquals(14, Phase10AccessibilityAcceptance.keyboardFlows().size());
        assertEquals(8, Phase10AccessibilityAcceptance.remappingChecks().size());
        assertEquals(12, Phase10AccessibilityAcceptance.visualChecks().size());
        assertEquals(7, Phase10AccessibilityAcceptance.audioChecks().size());
        assertEquals(9, Phase10AccessibilityAcceptance.windowFocusChecks().size());
        assertEquals(java.util.List.of(), Phase10AccessibilityAcceptance.validationErrors());

        Set<String> keyboardItems = Phase10AccessibilityAcceptance.keyboardFlows().stream()
                .map(Phase10AccessibilityAcceptance.KeyboardFlow::checklistItem)
                .collect(Collectors.toSet());
        assertTrue(keyboardItems.contains("Navigate the main menu without a mouse"));
        assertTrue(keyboardItems.contains("Enter tactical combat"));
        assertTrue(keyboardItems.contains("Show visible keyboard focus"));
    }

    @Test
    void remapValidationRejectsSameScopeConflictsWithPlayerFacingReasons() {
        HotkeyRegistry.RemapResult keyboard = HotkeyRegistry.remapKeyboardDetailed(
                "toggleShop",
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        HotkeyRegistry.RemapResult mouse = HotkeyRegistry.remapMouseDetailed("primaryDown", 3);
        HotkeyRegistry.RemapResult controller = HotkeyRegistry.remapControllerDetailed("primaryDown", "LT");

        assertFalse(keyboard.accepted());
        assertTrue(keyboard.message().contains("escape") || keyboard.message().contains("ESC"));
        assertTrue(keyboard.message().contains("escape"));
        assertFalse(mouse.accepted());
        assertTrue(mouse.message().contains("secondaryDown"));
        assertFalse(controller.accepted());
        assertTrue(controller.message().contains("secondaryDown"));
    }

    @Test
    void requiredActionsStayRegisteredAndRemappableCatalogIsComplete() {
        assertEquals(
                HotkeyRegistry.bindings().stream().map(HotkeyRegistry.Binding::action).collect(Collectors.toList()),
                HotkeyRegistry.requiredActionIds());
        for (String action : HotkeyRegistry.requiredActionIds()) {
            assertNotNull(HotkeyRegistry.stroke(action));
            assertFalse(HotkeyRegistry.label(action).isBlank(), action);
        }
        assertEquals(java.util.List.of(), HotkeyRegistry.conflictWarnings());
    }

    @Test
    void focusLossReleaseClearsHeldGameplayInputsWithoutUnlatchingToggles() {
        ExperienceSettings settings = ExperienceSettings.defaults();
        settings.miningMode = ExperienceSettings.InteractionMode.TOGGLE;
        settings.firingMode = ExperienceSettings.InteractionMode.TOGGLE;
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1010L, false)
                .withExperience(settings));

        ctx.miningKeyDown = true;
        ctx.firingPrimaryManual = true;
        ctx.firingSecondaryManual = true;
        ctx.firingPrimaryManualLatched = true;
        ctx.cameraPanLeft = true;
        ctx.cameraPanRight = true;
        ctx.cameraPanUp = true;
        ctx.cameraPanDown = true;

        ExperienceRuntime.releaseHeldInputs(ctx);

        assertFalse(ctx.miningKeyDown);
        assertFalse(ctx.firingPrimaryManual);
        assertFalse(ctx.firingSecondaryManual);
        assertFalse(ctx.cameraPanLeft);
        assertFalse(ctx.cameraPanRight);
        assertFalse(ctx.cameraPanUp);
        assertFalse(ctx.cameraPanDown);
        assertTrue(ctx.firingPrimaryManualLatched, "toggle-mode intent should survive a focus transition");
    }

    @Test
    void visualAndAudioAccessibilitySettingsNormalizeAndExposeControls() {
        ExperienceSettings settings = ExperienceSettings.defaults();
        settings.uiTextScale = 99.0;
        settings.subtitleScale = -99.0;
        settings.normalize();
        assertEquals(1.6, settings.uiTextScale, 0.001);
        assertEquals(0.8, settings.subtitleScale, 0.001);

        assertEquals("Alt+Enter", HotkeyRegistry.label("fullscreen"));
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), HotkeyRegistry.stroke("fullscreen"));
        assertTrue(Phase10AccessibilityAcceptance.audioChecks().stream()
                .anyMatch(check -> check.checklistItem().equals("Verify volume controls")));
        assertTrue(Phase10AccessibilityAcceptance.visualChecks().stream()
                .anyMatch(check -> check.checklistItem().equals("Verify high contrast")));
    }
}
