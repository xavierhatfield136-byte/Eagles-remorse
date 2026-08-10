import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignDifficultyOutcomeSeparationTest {
    @Test
    void legacyDifficultyPresetsNormalizeToOneHardCampaignProfile() throws Exception {
        GameContext relaxed = campaign(65001L, ExperienceSettings.Preset.RELAXED);
        GameContext standard = campaign(65001L, ExperienceSettings.Preset.STANDARD);
        GameContext iron = campaign(65001L, ExperienceSettings.Preset.IRON_COMMAND);
        GameContext tacticalOnly = campaign(65001L, ExperienceSettings.Preset.TACTICAL_ONLY);

        assertTrue(routeInterdictionRiskFloor(relaxed) == routeInterdictionRiskFloor(standard));
        assertTrue(routeInterdictionRiskFloor(iron) == routeInterdictionRiskFloor(standard));
        assertTrue(routeInterdictionRiskFloor(tacticalOnly) == routeInterdictionRiskFloor(standard));

        int relaxedLoss = runLongRouteAndResourceLoss(relaxed);
        int standardLoss = runLongRouteAndResourceLoss(standard);
        int ironLoss = runLongRouteAndResourceLoss(iron);
        int tacticalLoss = runLongRouteAndResourceLoss(tacticalOnly);

        assertTrue(relaxedLoss == standardLoss, "Relaxed should normalize to the same campaign attrition");
        assertTrue(ironLoss == standardLoss, "Iron should normalize to the same campaign attrition");
        assertTrue(tacticalLoss == standardLoss, "Tactical Only should no longer suppress strategic attrition");

        String relaxedRecovery = String.join("\n", CampaignSystem.campaignDifficultyTelemetryLines(relaxed));
        String ironRecovery = String.join("\n", CampaignSystem.campaignDifficultyModifierLines(iron));
        assertTrue(relaxedRecovery.contains("one ordinary mistake remains recoverable"));
        assertTrue(ironRecovery.contains("Travel attrition: x1.32"));
    }

    @Test
    void presetRulesAreVisibleForEveryNonCustomPreset() {
        for (ExperienceSettings.Preset preset : ExperienceSettings.Preset.values()) {
            if (preset == ExperienceSettings.Preset.CUSTOM) continue;
            ExperienceSettings settings = ExperienceSettings.forPreset(preset);
            String summary = String.join("\n", settings.modifierSummaryLines());
            assertTrue(summary.contains("Strategic pressure: x") || settings.tacticalOnly);
            assertTrue(summary.contains("Travel attrition: x") || settings.commandOnly);
            assertTrue(summary.contains("Combat lethality: x") || settings.tacticalOnly);
        }
    }

    private static int runLongRouteAndResourceLoss(GameContext ctx) throws Exception {
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignFuel = 500;
        st.campaignSupplies = 500;
        st.campaignAmmo = 500;
        CampaignSystem.CampaignLocation target = findLocation(ctx, "poi-22");
        st.selectedGalaxyLocationId = target.id;
        int before = st.campaignFuel + st.campaignSupplies + st.campaignAmmo;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        invokeTravelUpdate(ctx, st, st.galaxyTravel.durationSec + 0.01);
        int after = st.campaignFuel + st.campaignSupplies + st.campaignAmmo;
        return Math.max(0, before - after);
    }

    private static GameContext campaign(long seed, ExperienceSettings.Preset preset) {
        ExperienceSettings settings = ExperienceSettings.forPreset(preset);
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.experience.preset = settings.preset;
        ctx.experience.commandComplexity = settings.commandComplexity;
        ctx.experience.combatLethality = settings.combatLethality;
        ctx.experience.strategicPressure = settings.strategicPressure;
        ctx.experience.attrition = settings.attrition;
        ctx.experience.tacticalOnly = settings.tacticalOnly;
        ctx.experience.commandOnly = settings.commandOnly;
        ctx.experience.ironCommand = settings.ironCommand;
        ctx.experience.normalize();
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        throw new AssertionError("missing campaign location " + id);
    }

    private static double routeInterdictionRiskFloor(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("routeInterdictionRiskFloor", GameContext.class);
        method.setAccessible(true);
        return (double) method.invoke(null, ctx);
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }
}
