import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTacticalAlignmentTest {

    @Test
    void missionEncounterPromptPreservesSingleLargeSectorRuleAndManualPriorityGuidance() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-08";
        CampaignSystem.CampaignLocation mission = CampaignSystem.selectedCampaignLocation(ctx);
        assertNotNull(mission);
        st.playerGalaxyX = mission.x;
        st.playerGalaxyY = mission.y;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.contains("single large tactical sector"));
        assertTrue(ctx.ui.strategicEncounterPrompt.strengthReadout.contains("MANUAL PRIORITY"));
    }

    @Test
    void greenAndYellowCampaignHubsStayOpenInsteadOfForcingCombat() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-01";

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.selectedLocationSidebarLines(ctx).stream()
                .anyMatch(line -> line.contains("Open campaign hub") || line.contains("Dock / trade / explore")));

        st.selectedGalaxyLocationId = "poi-02";
        st.playerGalaxyX = CampaignSystem.selectedCampaignLocation(ctx).x;
        st.playerGalaxyY = CampaignSystem.selectedCampaignLocation(ctx).y;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
    }

    @Test
    void hostileInterceptPromptUsesSingleLargeSectorLanguageAndManualPriorityGuidance() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        st.sectorElapsed = 999.0;
        setDouble(group, "x", st.playerGalaxyX);
        setDouble(group, "y", st.playerGalaxyY);
        setString(group, "anchorLocationId", "");

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.contains("one large tactical sector"));
        assertTrue(ctx.ui.strategicEncounterPrompt.strengthReadout.contains("MANUAL PRIORITY")
                || ctx.ui.strategicEncounterPrompt.strengthReadout.contains("AUTO-RESOLVE: VIABLE"));
    }

    @Test
    void tacticalAudioEventsCoverLaunchWarpAndMajorShipDeath() {
        AudioSystem.setTelemetryOnly(true);
        GameContext ctx = initializedSkirmishContext();

        AudioSystem.onFlightLaunch(ctx, ctx.player);
        AudioSystem.onWarpChargeStart(ctx, ctx.player);
        AudioSystem.onWarpExit(ctx, ctx.player);

        AudioSystem.update(ctx, GameContext.DT);
        Explosion.spawnFinalDetonation(ctx.player.x + 40.0, ctx.player.y + 20.0, 180.0);
        AudioSystem.update(ctx, GameContext.DT);

        assertTrue(ctx.audioEvents.stream().anyMatch(ev -> "sfx.flight.launch".equals(ev.eventId)));
        assertTrue(ctx.audioEvents.stream().anyMatch(ev -> "sfx.warp.charge_start".equals(ev.eventId)));
        assertTrue(ctx.audioEvents.stream().anyMatch(ev -> "sfx.warp.exit".equals(ev.eventId)));
        assertTrue(ctx.audioEvents.stream().anyMatch(ev -> "sfx.impact.ship_death_major".equals(ev.eventId)));
    }

    @Test
    void soundManifestIncludesPhaseSevenCombatIdentityEvents() {
        assertNotNull(SfxManifest.byId("flight.launch"));
        assertNotNull(SfxManifest.byId("warp.charge_start"));
        assertNotNull(SfxManifest.byId("warp.exit"));
        assertNotNull(SfxManifest.byId("impact.ship_death_major"));
    }

    @Test
    void tacticalProjectileSpeedBudgetKeepsGunsReadableAndMakesMissilesFast() {
        double energyNavyProjectileSpeed = DoctrineRegistry.ENERGY_NAVY.mainProjectileSpeed * Turret.GUN_PROJECTILE_SPEED_MULT;
        double kineticProjectileSpeed = DoctrineRegistry.KINETIC_CONSORTIUM.mainProjectileSpeed * Turret.GUN_PROJECTILE_SPEED_MULT;
        double aegisProjectileSpeed = DoctrineRegistry.AEGIS_LATTICE.mainProjectileSpeed * Turret.GUN_PROJECTILE_SPEED_MULT;
        double viperProjectileSpeed = DoctrineRegistry.VIPER_BARRAGE.mainProjectileSpeed * Turret.GUN_PROJECTILE_SPEED_MULT;
        double baselineMissileSpeed = Math.min(
                220.0 * Turret.MISSILE_SPEED_MULT * Missile.GLOBAL_SPEED_MULT * 2.35,
                Missile.MAX_RUNTIME_SPEED_M_PER_SEC);
        double interceptorMissileSpeed = Math.min(
                baselineMissileSpeed * 1.18,
                Missile.MAX_RUNTIME_SPEED_M_PER_SEC);

        assertTrue(energyNavyProjectileSpeed <= 640.0,
                "energy-navy bolts should stay readable, got " + energyNavyProjectileSpeed);
        assertTrue(kineticProjectileSpeed <= 1020.0,
                "kinetic rounds should stay below near-hitscan feel, got " + kineticProjectileSpeed);
        assertTrue(aegisProjectileSpeed <= 780.0,
                "aegis bolts should remain anticipatable, got " + aegisProjectileSpeed);
        assertTrue(viperProjectileSpeed <= 840.0,
                "backup barrage guns should remain readable, got " + viperProjectileSpeed);
        assertTrue(interceptorMissileSpeed <= Missile.MAX_RUNTIME_SPEED_M_PER_SEC,
                "missiles should stay inside the maximum runtime speed budget, got " + interceptorMissileSpeed);
        assertEquals(2.0, Missile.GLOBAL_SPEED_MULT, 0.0);
        assertTrue(Ship.BEAM_BOLT_SPEED <= 700.0);
        assertTrue(Turret.GUN_PROJECTILE_SPEED_MULT <= 0.84);
        assertTrue(Turret.MISSILE_SPEED_MULT <= 0.90);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static GameContext initializedSkirmishContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 5678L, false));
        SpawnSystem.initWorld(ctx);
        assertNotNull(ctx.player);
        return ctx;
    }

    private static void invokeDetectionUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxyDetectionAndInterception",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        java.util.List<?> groups = (java.util.List<?>) field.get(st);
        assertTrue(!groups.isEmpty());
        return groups.get(0);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setString(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

}
