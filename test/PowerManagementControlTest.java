import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerManagementControlTest {

    @Test
    void manualPowerAllocationBecomesCustomAndSurvivesAutomationTick() {
        GameContext ctx = context();
        ctx.command.captainAutomation = true;
        ctx.command.captainDirective = GameContext.CaptainDirective.ATTACK;
        ctx.command.engineeringAutomation = true;
        ctx.player.setPowerPreset(Ship.PowerPreset.ATTACK);

        UISystem.adjustPowerAllocation(ctx, Ship.PowerBus.PROPULSION.ordinal(), 0.05);
        double[] customBuses = ctx.player.powerBusFractions();

        assertFalse(ctx.command.engineeringAutomation);
        assertTrue(ctx.command.playerPowerManualOverride);
        assertEquals(Ship.PowerPreset.CUSTOM, ctx.player.powerPreset);

        CrewStationsSystem.updatePlayerAutomation(ctx, idleInput(), GameContext.DT);

        assertEquals(Ship.PowerPreset.CUSTOM, ctx.player.powerPreset);
        assertArrayEquals(customBuses, ctx.player.powerBusFractions(), 1.0e-9);
    }

    @Test
    void manualPowerAllocationSurvivesStrayEngineeringAutomationReenable() {
        GameContext ctx = context();
        ctx.command.captainAutomation = true;
        ctx.command.captainDirective = GameContext.CaptainDirective.ATTACK;
        ctx.command.engineeringAutomation = true;
        ctx.command.engineeringMode = GameContext.EngineeringMode.ATTACK;
        ctx.player.setPowerPreset(Ship.PowerPreset.ATTACK);

        UISystem.adjustPowerAllocation(ctx, Ship.PowerBus.PROPULSION.ordinal(), 0.05);
        double[] customBuses = ctx.player.powerBusFractions();

        ctx.command.engineeringAutomation = true;
        CrewStationsSystem.updatePlayerAutomation(ctx, idleInput(), GameContext.DT);

        assertFalse(ctx.command.engineeringAutomation);
        assertEquals(Ship.PowerPreset.CUSTOM, ctx.player.powerPreset);
        assertArrayEquals(customBuses, ctx.player.powerBusFractions(), 1.0e-9);
    }

    @Test
    void manualPowerAllocationSurvivesFlagshipOperationsProjection() {
        GameContext ctx = context();
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        FlagshipOperationsSystem.syncFromShip(state, ctx.player);
        FlagshipOperationsSystem.Compartment propulsion = state.compartments.values().stream()
                .filter(c -> c.systemType == FlagshipOperationsSystem.SystemType.PROPULSION)
                .findFirst()
                .orElse(null);
        if (propulsion == null) {
            propulsion = new FlagshipOperationsSystem.Compartment("PROPULSION", "Propulsion");
            propulsion.systemType = FlagshipOperationsSystem.SystemType.PROPULSION;
            propulsion.powerDemand = 60;
            state.compartments.put(propulsion.id, propulsion);
        }
        state.powerGeneration = 20;
        propulsion.powerPriority = 0;
        FlagshipOperationsSystem.routePower(state);

        UISystem.adjustPowerAllocation(ctx, Ship.PowerBus.PROPULSION.ordinal(), 0.05);
        double[] customBuses = ctx.player.powerBusFractions();

        FlagshipOperationsSystem.applyToShip(state, ctx.player,
                ctx.command.playerPowerManualOverride || !ctx.command.engineeringAutomation);

        assertEquals(Ship.PowerPreset.CUSTOM, ctx.player.powerPreset);
        assertArrayEquals(customBuses, ctx.player.powerBusFractions(), 1.0e-9);
    }

    @Test
    void campaignUpdateDoesNotRevertManualPowerAllocation() {
        GameContext ctx = context();
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.campaign.strategicOvermapMode = false;
        FlagshipOperationsSystem.syncFromShip(ctx.campaign.flagshipOperations, ctx.player);
        FlagshipOperationsSystem.Compartment propulsion = ctx.campaign.flagshipOperations.compartments.values().stream()
                .filter(c -> c.systemType == FlagshipOperationsSystem.SystemType.PROPULSION)
                .findFirst()
                .orElse(null);
        if (propulsion != null) {
            ctx.campaign.flagshipOperations.powerGeneration = 20;
            propulsion.powerPriority = 0;
            FlagshipOperationsSystem.routePower(ctx.campaign.flagshipOperations);
        }

        UISystem.adjustPowerAllocation(ctx, Ship.PowerBus.PROPULSION.ordinal(), 0.05);
        double[] customBuses = ctx.player.powerBusFractions();

        CampaignSystem.update(ctx, GameContext.DT);

        assertEquals(Ship.PowerPreset.CUSTOM, ctx.player.powerPreset);
        assertArrayEquals(customBuses, ctx.player.powerBusFractions(), 1.0e-9);
    }

    @Test
    void explicitEngineeringModeCanTakePowerBackFromCustom() {
        GameContext ctx = context();
        UISystem.adjustPowerAllocation(ctx, Ship.PowerBus.PROPULSION.ordinal(), 0.05);

        UISystem.setEngineeringMode(ctx, GameContext.EngineeringMode.ATTACK);
        CrewStationsSystem.updatePlayerAutomation(ctx, idleInput(), GameContext.DT);

        assertFalse(ctx.command.playerPowerManualOverride);
        assertEquals(Ship.PowerPreset.ATTACK, ctx.player.powerPreset);
    }

    @Test
    void cyclingPresetFromCustomReturnsToPresetRing() {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        player.setCustomPowerBusAllocation(0.28, 0.12, 0.18, 0.14, 0.18, 0.10);

        Ship.PowerPreset next = player.cyclePowerPreset();

        assertEquals(Ship.PowerPreset.BALANCED, next);
    }

    private static GameContext context() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 4242L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        ctx.entityQuery.rebuild(ctx);
        return ctx;
    }

    private static InputSnapshot idleInput() {
        return new InputSnapshot(false, false, false, false, false, 0.0, 0.0);
    }
}
