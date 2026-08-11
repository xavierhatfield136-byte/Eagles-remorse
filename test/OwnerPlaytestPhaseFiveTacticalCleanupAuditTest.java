import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestPhaseFiveTacticalCleanupAuditTest {
    @Test
    void fighterDeadlockAndEscortToleranceContractsAreExplicit() {
        String fighter = String.join("\n", PhaseFiveTacticalCleanupSystem.fighterDeadlockRegressionLines());
        String escort = String.join("\n", PhaseFiveTacticalCleanupSystem.escortToleranceLines());

        assertTrue(fighter.contains("two opposing fighters"));
        assertTrue(fighter.contains("8 seconds"));
        assertTrue(fighter.contains("48 units"));
        assertTrue(fighter.contains("bad approaches flip orbit direction"));
        assertTrue(escort.contains("720 units"));
        assertTrue(escort.contains("reserved alternating side slots"));
        assertTrue(escort.contains("reserve-role ships"));
    }

    @Test
    void roleBalanceMeasurementsCoverStealthCiwsAndCombinedArms() {
        PhaseFiveTacticalCleanupSystem.RoleBalanceMeasurement stealth =
                PhaseFiveTacticalCleanupSystem.stealthRevealWindowMeasurement();
        PhaseFiveTacticalCleanupSystem.RoleBalanceMeasurement ciws =
                PhaseFiveTacticalCleanupSystem.ciwsSecondaryRoleMeasurement();
        String combinedArms = String.join("\n", PhaseFiveTacticalCleanupSystem.combinedArmsLoopLines());

        assertTrue(stealth.burstDamage() > 0.0);
        assertTrue(stealth.survivalPool() > 0.0);
        assertTrue(stealth.escapeValue() > 0.0);
        assertTrue(stealth.counterplay().contains("Detection counterplay remains"));
        assertTrue(ciws.burstDamage() > 0.0);
        assertTrue(ciws.counterplay().contains("anti-fighter"));
        assertTrue(combinedArms.contains("Carrier:"));
        assertTrue(combinedArms.contains("Picket:"));
        assertTrue(combinedArms.contains("Capital:"));
    }

    @Test
    void reserveControlShowsCompositionRulesBlockersAndDeployRecallConfirmation() {
        GameContext ctx = campaign(68001L);

        PhaseFiveTacticalCleanupSystem.ReserveControlView view =
                PhaseFiveTacticalCleanupSystem.reserveControlView(ctx);
        assertTrue(view.composition().contains("Blue reserve"));
        assertTrue(view.arrivalRule().contains("Deploy when reserve is >=20%"));
        assertTrue(view.spawnEdge().contains("friendly edge"));
        assertFalse(PhaseFiveTacticalCleanupSystem.reserveTutorialPrompt(ctx).isBlank());

        String deploy = PhaseFiveTacticalCleanupSystem.deployReserve(ctx);
        assertTrue(deploy.contains("RESERVE DEPLOY CONFIRMED"));
        assertTrue(ctx.campaign.galaxyAmbientSupportRequested);
        assertTrue(PhaseFiveTacticalCleanupSystem.reserveTutorialPrompt(ctx).isBlank());

        PhaseFiveTacticalCleanupSystem.ReserveControlView pending =
                PhaseFiveTacticalCleanupSystem.reserveControlView(ctx);
        assertTrue(pending.recallAvailable());
        String recall = PhaseFiveTacticalCleanupSystem.recallReserve(ctx);
        assertTrue(recall.contains("RESERVE RECALL CONFIRMED"));
        assertFalse(ctx.campaign.galaxyAmbientSupportRequested);
    }

    @Test
    void uiHintsCrewAutomationAndStrategicTopFoldArePlayerFacingAndCompact() {
        GameContext ctx = campaign(68002L);
        ctx.ui.showCampaignHubMenu("poi-05", "REPAIR");
        assertTrue(PhaseFiveTacticalCleanupSystem.shouldCollapseTopHints(ctx));

        String toggle = PhaseFiveTacticalCleanupSystem.toggleTopHintsPreference(ctx);
        assertTrue(toggle.contains("TOP HINTS"));
        assertTrue(ctx.ui.campaignTopHintsPreferenceRemembered);

        String crew = String.join("\n", PhaseFiveTacticalCleanupSystem.crewAutomationExplanationLines(ctx));
        assertTrue(crew.contains("Crew Automation:"));
        assertTrue(crew.contains("Manual station input"));
        assertTrue(crew.contains("toggle automation"));

        String topFold = String.join("\n", PhaseFiveTacticalCleanupSystem.strategicTopFoldLines(ctx));
        assertTrue(topFold.contains("Main Objective:"));
        assertTrue(topFold.contains("Immediate Step:"));
        assertTrue(topFold.contains("Selected Target:"));
        assertTrue(topFold.contains("Route Risk:"));
        assertTrue(topFold.contains("Primary Actions:"));
        assertTrue(PhaseFiveTacticalCleanupSystem.strategicTopFoldLines(ctx).size() <= 5);
    }

    @Test
    void blueHyperweaponTurretsAndShieldBaselineAreCoveredByArtAudit() {
        Ship.enableDeterministicRandom(68003L);
        try {
            FleetShip hyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ALLY, 0.0, 0.0);
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            ShipHullSilhouette.VisualBounds visualBounds = ShipHullSilhouette.visualBounds(
                    hyperweapon.role, hyperweapon.radius, hyperweapon.faction);
            double visualCenterY = visualBounds == null ? 0.0 : 0.5 * (visualBounds.minY + visualBounds.maxY);
            for (Turret turret : hyperweapon.turrets) {
                assertTrue(ShipHullSilhouette.visualHullContains(
                                hyperweapon.role, hyperweapon.radius, hyperweapon.faction,
                                turret.localX, turret.localY),
                        "Blue hyperweapon turret center drifted off visible hull");
                minY = Math.min(minY, turret.localY);
                maxY = Math.max(maxY, turret.localY);
                minX = Math.min(minX, turret.localX);
                maxX = Math.max(maxX, turret.localX);
            }

            assertTrue(maxY > visualCenterY + 4.0 && minY < visualCenterY - 4.0,
                    "Blue hyperweapon titan must mount both flanks around the visible sprite midline");
            assertTrue(maxX - minX >= hyperweapon.radius * 0.60, "Blue hyperweapon titan needs longitudinal spread");
        } finally {
            Ship.disableDeterministicRandom();
        }

        String art = String.join("\n", PhaseFiveTacticalCleanupSystem.artBaselineLines());
        assertTrue(art.contains("Blue Hyperweapon Titan"));
        assertTrue(art.contains("Shield Baseline"));
        assertTrue(art.contains("shield color cannot tint hull sprites"));
        assertTrue(art.contains("Screenshot Baselines"));
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }
}
