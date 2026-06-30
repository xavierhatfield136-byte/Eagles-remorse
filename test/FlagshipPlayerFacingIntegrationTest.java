import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagshipPlayerFacingIntegrationTest {
    private static GameContext campaign() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9961L, false));
        SpawnSystem.initWorld(ctx);
        ctx.state = GameState.MAP;
        ctx.ui.mapOpen = true;
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
        return ctx;
    }

    @Test
    void compartmentPowerChangesLiveCombatBusesWithoutReplacingShipRoomAuthority() {
        GameContext ctx = campaign();
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        FlagshipOperationsSystem.syncFromShip(state, ctx.player);
        FlagshipOperationsSystem.Compartment propulsion = state.compartments.values().stream()
                .filter(c -> c.systemType == FlagshipOperationsSystem.SystemType.PROPULSION)
                .findFirst().orElseGet(() -> {
                    FlagshipOperationsSystem.Compartment c = new FlagshipOperationsSystem.Compartment("PROPULSION", "Propulsion");
                    c.systemType = FlagshipOperationsSystem.SystemType.PROPULSION; c.powerDemand = 60;
                    state.compartments.put(c.id, c); return c;
                });
        state.powerGeneration = 20;
        propulsion.powerPriority = 0;
        FlagshipOperationsSystem.routePower(state);
        FlagshipOperationsSystem.CombatEffects effects = FlagshipOperationsSystem.applyToShip(state, ctx.player);
        assertTrue(effects.propulsion() < 1.0);
        assertTrue(ctx.player.powerEnginesFrac() < ctx.player.powerWeaponsFrac()
                || ctx.player.powerEnginesFrac() < ctx.player.powerShieldsFrac());
        assertEquals(ctx.player.roomStatusSnapshot().size(), state.compartments.values().stream()
                .filter(c -> !c.id.equals("PROPULSION")).count(), 1,
                "ship room snapshot remains the source; only the test fallback room may be extra");
    }

    @Test
    void casualtiesExceedingMedicalCapacityReduceStationPerformance() {
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        FlagshipOperationsSystem.Compartment medical = new FlagshipOperationsSystem.Compartment("MEDICAL", "Medical");
        medical.systemType = FlagshipOperationsSystem.SystemType.MEDICAL;
        medical.injuries = 18;
        state.compartments.put(medical.id, medical);
        state.medicalCapacity = 2;
        int readiness = state.teams.get("dc-1").readiness;
        FlagshipOperationsSystem.update(state, 1.0);
        assertTrue(state.casualties >= 18);
        assertTrue(state.teams.get("dc-1").readiness < readiness);
        assertTrue(FlagshipOperationsSystem.combatEffects(state).medicalPerformance() < 1.0);
    }

    @Test
    void schematicNavigationWarningsSlowTimeAndPersistenceShareOneInterface() {
        GameContext ctx = campaign();
        FlagshipOperationsSystem.syncFromShip(ctx.campaign.flagshipOperations, ctx.player);
        assertTrue(CampaignSystem.toggleFlagshipSchematic(ctx));
        assertTrue(CampaignSystem.cycleFlagshipCompartment(ctx, 1));
        String selected = ctx.campaign.flagshipOperations.selectedCompartmentId;
        assertFalse(selected.isBlank());
        assertTrue(CampaignSystem.zoomFlagshipSchematic(ctx, 0.5));
        assertTrue(CampaignSystem.toggleFlagshipSlowTime(ctx));
        assertEquals(0.25, CampaignSystem.campaignTimeScale(ctx), 0.0001);
        FlagshipOperationsSystem.setEmergency(ctx.campaign.flagshipOperations, selected, 1.0, true, true);
        assertTrue(FlagshipOperationsSystem.criticalWarnings(ctx.campaign.flagshipOperations).stream()
                .allMatch(w -> !w.text().isBlank() && !w.icon().isBlank() && !w.pattern().isBlank()
                        && !w.optionalAudioCue().isBlank()));
        FlagshipOperationsSystem.State restored = FlagshipOperationsSystem.restore(
                FlagshipOperationsSystem.serialize(ctx.campaign.flagshipOperations));
        assertEquals(selected, restored.selectedCompartmentId);
        assertEquals(ctx.campaign.flagshipOperations.schematicZoom, restored.schematicZoom, 0.0001);
        assertTrue(restored.schematicVisible && restored.slowTimeRequested);
    }

    @Test
    void zoomableSchematicRendersAtAllSupportedDesktopScales() {
        for (int[] size : new int[][]{{1280, 720}, {1920, 1080}, {2560, 1440}}) {
            GameContext ctx = campaign();
            FlagshipOperationsSystem.syncFromShip(ctx.campaign.flagshipOperations, ctx.player);
            ctx.campaign.flagshipOperations.schematicVisible = true;
            FlagshipOperationsSystem.selectCompartment(ctx.campaign.flagshipOperations, 1);
            BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            GameRenderSystem.render(ctx, g2, size[0], size[1]);
            g2.dispose();
            assertNotEquals(0, image.getRGB(size[0] / 2, size[1] / 2));
        }
    }

    @Test
    void simultaneousEmergencyAcceptanceScenarioCompletes() {
        GameContext ctx = campaign();
        FlagshipEmergencyAcceptanceHarness.Report report = FlagshipEmergencyAcceptanceHarness.run(ctx.player);
        assertTrue(report.passed(), report.summary());
    }
}
