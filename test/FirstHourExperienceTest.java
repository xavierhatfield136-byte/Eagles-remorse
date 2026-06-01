import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FirstHourExperienceTest {
    @Test
    void presetsSeparatePressureLethalityAndModes() {
        ExperienceSettings relaxed = ExperienceSettings.forPreset(ExperienceSettings.Preset.RELAXED);
        ExperienceSettings tactical = ExperienceSettings.forPreset(ExperienceSettings.Preset.TACTICAL_ONLY);
        ExperienceSettings command = ExperienceSettings.forPreset(ExperienceSettings.Preset.COMMAND_ONLY);
        ExperienceSettings iron = ExperienceSettings.forPreset(ExperienceSettings.Preset.IRON_COMMAND);

        assertTrue(relaxed.attrition < 1.0);
        assertTrue(relaxed.combatLethality < 1.0);
        assertTrue(relaxed.strategicPressure < 1.0);
        assertTrue(tactical.tacticalOnly);
        assertTrue(command.commandOnly);
        assertTrue(iron.ironCommand);
        assertTrue(iron.attrition > 1.0);
        assertTrue(context(relaxed).command.helmAutomation);
    }

    @Test
    void configCopiesExperienceSettings() {
        ExperienceSettings relaxed = ExperienceSettings.forPreset(ExperienceSettings.Preset.RELAXED);
        GameConfig config = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 7L, false)
                .withExperience(relaxed);
        relaxed.attrition = 1.8;
        assertEquals(0.56, config.experience.attrition, 0.001);
    }

    @Test
    void toggleInteractionsLatchAndReleaseAsConfigured() {
        ExperienceSettings toggle = ExperienceSettings.defaults();
        toggle.miningMode = ExperienceSettings.InteractionMode.TOGGLE;
        toggle.firingMode = ExperienceSettings.InteractionMode.TOGGLE;
        GameContext ctx = context(toggle);

        ExperienceRuntime.miningPressed(ctx);
        ExperienceRuntime.miningReleased(ctx);
        ExperienceRuntime.firingPressed(ctx, false);
        ExperienceRuntime.firingReleased(ctx, false);
        assertTrue(ctx.miningKeyDown);
        assertTrue(ctx.firingPrimaryManualLatched);

        ExperienceRuntime.miningPressed(ctx);
        ExperienceRuntime.firingPressed(ctx, false);
        assertFalse(ctx.miningKeyDown);
        assertFalse(ctx.firingPrimaryManualLatched);
    }

    @Test
    void briefingSupportsIndependentSkipAndArchive() {
        GameContext ctx = context(ExperienceSettings.defaults());
        ctx.player = new Player(ShipRole.FRIGATE, 100.0, 100.0);
        FirstHourOnboardingSystem.init(ctx);
        assertEquals(FirstHourOnboardingSystem.Beat.MOVEMENT, FirstHourOnboardingSystem.currentBeat(ctx));

        FirstHourOnboardingSystem.skipCurrent(ctx);
        assertEquals(FirstHourOnboardingSystem.Beat.MINING, FirstHourOnboardingSystem.currentBeat(ctx));
        FirstHourOnboardingSystem.toggleArchive(ctx);
        assertTrue(FirstHourOnboardingSystem.isArchiveOpen(ctx));
    }

    @Test
    void accessibilityPaletteChangesFactionAndStatusColors() {
        ExperienceSettings settings = ExperienceSettings.defaults();
        settings.colorblindPalette = ExperienceSettings.ColorblindPalette.TRITANOPIA;
        ExperienceRuntime.activate(settings);
        Color enemy = ExperienceRuntime.factionColor(Faction.ENEMY, true);
        assertNotNull(enemy);
        assertNotEquals(new Color(255, 90, 90), enemy);
        assertNotNull(ExperienceRuntime.roomDamageColor());
        assertNotNull(ExperienceRuntime.shieldStateColor());
        ExperienceRuntime.activate(ExperienceSettings.defaults());
    }

    @Test
    void combatLethalityScalesResolvedDamage() {
        ExperienceSettings relaxed = ExperienceSettings.forPreset(ExperienceSettings.Preset.RELAXED);
        ExperienceSettings iron = ExperienceSettings.forPreset(ExperienceSettings.Preset.IRON_COMMAND);
        assertEquals(8, CollisionSystem.scaleDamage(context(relaxed), 10));
        assertEquals(13, CollisionSystem.scaleDamage(context(iron), 10));
    }

    @Test
    void onboardingHotkeysAreCanonical() {
        assertEquals("Ctrl+F1", HotkeyRegistry.label("skipOnboardingBeat"));
        assertEquals("Ctrl+F2", HotkeyRegistry.label("toggleTutorialArchive"));
    }

    private static GameContext context(ExperienceSettings settings) {
        return new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 7L, false)
                .withExperience(settings));
    }
}
