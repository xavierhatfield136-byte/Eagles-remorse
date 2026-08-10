import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseFiveDifficultyAttritionTest {

    @Test
    void ironCommandAppliesEnemyOnlyArmorAndShieldRebootBonuses() {
        GameContext standard = initializedCampaignContext(5101L, ExperienceSettings.Preset.STANDARD);
        GameContext iron = initializedCampaignContext(5102L, ExperienceSettings.Preset.IRON_COMMAND);

        Ship standardEnemy = CampaignSystem.spawnSectionFourHullEncounter(
                standard, ShipRole.FRIGATE, Faction.ENEMY, 2200.0, 2200.0);
        Ship ironEnemy = CampaignSystem.spawnSectionFourHullEncounter(
                iron, ShipRole.FRIGATE, Faction.ENEMY, 2200.0, 2200.0);
        Ship ironAlly = CampaignSystem.spawnSectionFourHullEncounter(
                iron, ShipRole.FRIGATE, Faction.ALLY, 1800.0, 1800.0);

        assertEquals(standardEnemy.armorRoomHpMultiplier * 1.18,
                ironEnemy.armorRoomHpMultiplier, 0.0001);
        assertEquals(standardEnemy.shieldRebootDelay * 0.62,
                ironEnemy.shieldRebootDelay, 0.0001);
        assertEquals(standardEnemy.armorRoomHpMultiplier,
                ironAlly.armorRoomHpMultiplier, 0.0001);
        assertEquals(standardEnemy.shieldRebootDelay,
                ironAlly.shieldRebootDelay, 0.0001);
        assertEquals(standardEnemy.hpMax, ironEnemy.hpMax,
                "Iron Command must not turn into generic hull-health inflation");
    }

    @Test
    void difficultyRulesAreVisibleAndStandardKeepsRecoveryTarget() {
        GameContext standard = initializedCampaignContext(5103L, ExperienceSettings.Preset.STANDARD);
        GameContext iron = initializedCampaignContext(5104L, ExperienceSettings.Preset.IRON_COMMAND);

        String standardLines = String.join("\n", CampaignSystem.campaignDifficultyTelemetryLines(standard));
        assertTrue(standardLines.contains("one major loss"));
        assertTrue(standardLines.contains("hub recovery"));
        assertTrue(standardLines.contains("one ordinary mistake remains recoverable"));
        assertTrue(standardLines.contains("No faction-specific durability bonuses"));

        String ironLines = String.join("\n", CampaignSystem.campaignDifficultyModifierLines(iron));
        assertTrue(ironLines.contains("Enemy armor systems: +18%"));
        assertTrue(ironLines.contains("Enemy shield reboot delay: -38%"));
        assertTrue(ironLines.contains("sector transitions only"));
    }

    @Test
    void travelRepairStrikeAndTradePressureAreExplainedBeforeCommitment() {
        GameContext ctx = initializedCampaignContext(5105L, ExperienceSettings.Preset.STANDARD);
        String resources = String.join("\n", CampaignSystem.campaignResourceTrendLines(ctx));
        String strikes = String.join("\n", CampaignSystem.campaignStrikeAvailability(ctx).stream()
                .map(brief -> brief.strikeName + " " + brief.resourceCost + " " + brief.replenishment)
                .toList());
        String manager = String.join("\n", CampaignSystem.campaignResourceManagerLines(ctx));

        assertTrue(resources.contains("2-JUMP FORECAST"));
        assertTrue(resources.contains("enemy pressure"));
        assertTrue(resources.contains("fleet strain"));
        assertTrue(resources.contains("full armor/systems require a repair hub"));
        assertTrue(strikes.contains("ammo"));
        assertTrue(strikes.contains("per-battle cooldowns"));
        assertTrue(manager.contains("Credits:"));
        assertTrue(manager.contains("Ore"));
    }

    @Test
    void doctrineAndRetreatContractsStateDistinctPurposeAndRealConsequences() {
        GameContext ctx = initializedCampaignContext(5106L, ExperienceSettings.Preset.STANDARD);
        String doctrine = String.join("\n", CampaignSystem.campaignDoctrineAuditLines(ctx));
        String retreat = String.join("\n", CampaignSystem.campaignRetreatContractLines(ctx));

        assertTrue(doctrine.contains("LINE: broad lateral spacing"));
        assertTrue(doctrine.contains("overlapping fields of fire"));
        assertTrue(doctrine.contains("no formation changes raw hull health"));
        assertTrue(retreat.contains("7.5 seconds"));
        assertTrue(retreat.contains("interrupts it"));
        assertTrue(retreat.contains("distant"));
        assertTrue(retreat.contains("pursuit raises Red alert/exposure"));
        assertFalse(retreat.toLowerCase().contains("surrender"));
    }

    @Test
    void finalReadinessPenalizesAndAcknowledgesMajorRedRemnants() throws Exception {
        GameContext ctx = initializedCampaignContext(5107L, ExperienceSettings.Preset.STANDARD);
        ctx.campaign.campaignForces.clear();
        ctx.campaign.greenContractFavor = 5;
        ctx.campaign.yellowLiberationFavor = 5;
        CampaignSystem.CampaignFinalBattleReadiness before = CampaignSystem.campaignFinalBattleReadiness(ctx);

        Object remnant = createForce(ctx, "Red Surviving Capital Group");
        setDouble(remnant, "strength", 90.0);
        CampaignSystem.CampaignFinalBattleReadiness after = CampaignSystem.campaignFinalBattleReadiness(ctx);
        String late = String.join("\n", CampaignSystem.campaignLateCampaignReadinessLines(ctx));

        assertTrue(after.readinessScore <= before.readinessScore,
                "surviving Red capital groups must never improve final readiness");
        assertTrue(String.join("\n", after.lines).contains("major mobile remnants 1"));
        assertTrue(late.contains("1 major Red mobile remnants survive"));
        assertTrue(late.contains("victory text acknowledge"));
    }

    @Test
    void lateCampaignReadoutIncludesGreenOperationsAndYellowConsequences() {
        GameContext ctx = initializedCampaignContext(5108L, ExperienceSettings.Preset.STANDARD);
        ctx.campaign.sector = 21;
        List<String> lines = CampaignSystem.campaignLateCampaignReadinessLines(ctx);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("quality and capital concentration rise"));
        assertTrue(joined.contains("Green Operations"));
        assertTrue(joined.contains("Yellow Consequence"));
        assertTrue(joined.contains("reserves"));
    }

    private static GameContext initializedCampaignContext(long seed, ExperienceSettings.Preset preset) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ExperienceSettings selected = ExperienceSettings.forPreset(preset);
        ctx.experience.preset = selected.preset;
        ctx.experience.commandComplexity = selected.commandComplexity;
        ctx.experience.combatLethality = selected.combatLethality;
        ctx.experience.strategicPressure = selected.strategicPressure;
        ctx.experience.attrition = selected.attrition;
        ctx.experience.ironCommand = selected.ironCommand;
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static Object createForce(GameContext ctx, String name) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForceWithoutDeploymentCost",
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignForceKind.class,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class);
        method.setAccessible(true);
        return method.invoke(null, ctx.campaign, CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.ENEMY, name, "Earthward remnant lane", "Survive Red collapse",
                ctx.campaign.playerGalaxyX + 500.0, ctx.campaign.playerGalaxyY);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }
}
