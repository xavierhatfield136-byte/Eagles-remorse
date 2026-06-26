import app.config.ExperienceSettings;
import java.awt.Color;

/**
 * Runtime application of player-facing accessibility and interaction settings.
 */
public final class ExperienceRuntime {
    private static ExperienceSettings active = ExperienceSettings.defaults();

    private ExperienceRuntime() {}

    public static void activate(ExperienceSettings settings) {
        active = (settings == null) ? ExperienceSettings.defaults() : settings.copy();
        active.normalize();
        ScreenShake.setScale(active.reducedScreenShake ? 0.22 : 1.0);
    }

    public static ExperienceSettings active() {
        return active;
    }

    public static void update(GameContext ctx) {
        if (ctx == null || !ctx.experience.commandOnly) return;
        if (!CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) return;
        if (ctx.ui.strategicEncounterPrompt.kind == UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE) return;
        CampaignSystem.autoResolvePendingStrategicEncounter(ctx);
    }

    public static void miningPressed(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.experience.miningMode == ExperienceSettings.InteractionMode.TOGGLE) {
            ctx.miningKeyDown = !ctx.miningKeyDown;
        } else {
            ctx.miningKeyDown = true;
        }
    }

    public static void miningReleased(GameContext ctx) {
        if (ctx != null && ctx.experience.miningMode == ExperienceSettings.InteractionMode.HOLD) ctx.miningKeyDown = false;
    }

    public static void firingPressed(GameContext ctx, boolean secondary) {
        if (ctx == null) return;
        if (ctx.experience.firingMode == ExperienceSettings.InteractionMode.TOGGLE) {
            if (secondary) ctx.firingSecondaryManualLatched = !ctx.firingSecondaryManualLatched;
            else ctx.firingPrimaryManualLatched = !ctx.firingPrimaryManualLatched;
        } else if (secondary) {
            ctx.firingSecondaryManual = true;
        } else {
            ctx.firingPrimaryManual = true;
        }
    }

    public static void firingReleased(GameContext ctx, boolean secondary) {
        if (ctx == null || ctx.experience.firingMode != ExperienceSettings.InteractionMode.HOLD) return;
        if (secondary) ctx.firingSecondaryManual = false;
        else ctx.firingPrimaryManual = false;
    }

    public static void mapPressed(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.experience.mapMode == ExperienceSettings.InteractionMode.HOLD) {
            if (!ctx.ui.mapOpen) UISystem.toggleMap(ctx);
        } else {
            GameplayActions.toggleMap(ctx);
        }
    }

    public static void mapReleased(GameContext ctx) {
        if (ctx != null && ctx.experience.mapMode == ExperienceSettings.InteractionMode.HOLD && ctx.ui.mapOpen) {
            UISystem.toggleMap(ctx);
        }
    }

    public static void releaseHeldInputs(GameContext ctx) {
        if (ctx == null) return;
        ctx.miningKeyDown = false;
        ctx.firingPrimaryManual = false;
        ctx.firingSecondaryManual = false;
        ctx.cameraPanLeft = false;
        ctx.cameraPanRight = false;
        ctx.cameraPanUp = false;
        ctx.cameraPanDown = false;
    }

    public static Color factionColor(Faction faction, boolean bright) {
        ExperienceSettings.ColorblindPalette palette = active.colorblindPalette;
        if (palette == ExperienceSettings.ColorblindPalette.STANDARD) return null;
        boolean hostile = faction == Faction.ENEMY;
        boolean teamC = faction == Faction.TEAM_C;
        boolean teamD = faction == Faction.TEAM_D;
        return switch (palette) {
            case DEUTERANOPIA -> hostile ? new Color(245, 126, 48) : teamC ? new Color(85, 174, 255) : teamD ? new Color(245, 214, 88) : new Color(88, 198, 255);
            case PROTANOPIA -> hostile ? new Color(244, 154, 48) : teamC ? new Color(78, 188, 255) : teamD ? new Color(238, 220, 98) : new Color(102, 194, 255);
            case TRITANOPIA -> hostile ? new Color(255, 96, 142) : teamC ? new Color(90, 220, 156) : teamD ? new Color(240, 154, 226) : new Color(108, 214, 164);
            default -> null;
        };
    }

    public static Color warningColor() {
        return active.colorblindPalette == ExperienceSettings.ColorblindPalette.STANDARD
                ? new Color(255, 188, 92)
                : new Color(255, 214, 92);
    }

    public static Color roomDamageColor() {
        return active.colorblindPalette == ExperienceSettings.ColorblindPalette.TRITANOPIA
                ? new Color(255, 108, 178)
                : new Color(255, 112, 92);
    }

    public static Color shieldStateColor() {
        return active.colorblindPalette == ExperienceSettings.ColorblindPalette.STANDARD
                ? new Color(120, 220, 255)
                : new Color(116, 238, 208);
    }
}
