import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPhaseEightArchitectureTest {
    @Test
    void ownershipContractNamesEveryAuthoritativeCampaignDomain() {
        List<String> lines = CampaignSystem.campaignAuthoritativeOwnershipLines();

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Fleets:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Inventory:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Economy:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Territory:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Production:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Missions:")));
    }

    @Test
    void liveCampaignPassesAllPhaseEightValidatorsWithoutMutation() {
        GameContext ctx = campaignContext();
        CampaignSnapshot before = snapshot(ctx);

        CampaignSystem.CampaignIntegrityReport report = CampaignSystem.validateCampaignIntegrity(ctx);

        assertEquals(List.of(
                "order-of-battle",
                "fleet-provenance",
                "economy-conservation",
                "production-queue",
                "territory-ownership",
                "mission-briefing-completeness",
                "contact-validity",
                "strike-origin",
                "save-migration"), report.validators);
        assertTrue(report.healthy(), () -> "unexpected failures: " + report.failures);
        assertEquals(before, snapshot(ctx), "validators must be read-only");
    }

    @Test
    void validatorSurfacesConservationFailureInDeveloperDiagnostics() {
        GameContext ctx = campaignContext();
        ctx.campaign.campaignFuel = -1;

        CampaignSystem.CampaignIntegrityReport report = CampaignSystem.validateCampaignIntegrity(ctx);

        assertFalse(report.healthy());
        assertTrue(report.failures.stream().anyMatch(line -> line.contains("economy-conservation")
                && line.contains("negative fuel")));
        assertTrue(CampaignSystem.campaignIntegrityDiagnosticLines(ctx).get(0).contains("FAIL"));
    }

    @Test
    void duplicateResourceTransactionCannotChargeTwice() {
        GameContext ctx = campaignContext();
        ctx.credits = 1_000;
        CampaignSystem.grantCampaignOre(ctx, 100);
        int creditsBefore = ctx.credits;
        int oreBefore = CampaignSystem.currentCampaignOre(ctx);

        assertTrue(CampaignSystem.spendCampaignResourcesOnce(
                ctx, "test:single-charge", 120, 10, 2, 3, 4, 5));
        assertFalse(CampaignSystem.spendCampaignResourcesOnce(
                ctx, "test:single-charge", 120, 10, 2, 3, 4, 5));

        assertEquals(creditsBefore - 120, ctx.credits);
        assertEquals(oreBefore - 10, CampaignSystem.currentCampaignOre(ctx));
        assertEquals(118, ctx.campaign.campaignFuel);
        assertEquals(87, ctx.campaign.campaignSupplies);
        assertEquals(106, ctx.campaign.campaignAmmo);
        assertEquals(30, ctx.campaign.campaignSalvage);
    }

    @Test
    void finiteFleetInventoryIsNotBlockedByProjectionEconomyReserve() throws Exception {
        GameContext ctx = campaignContext();
        ctx.campaign.economyExpansion.aiDeploymentReserve = 0;
        Class<?> kindType = Class.forName("CampaignSystemModels$CampaignForceKind");
        Object patrolKind = Enum.valueOf(kindType.asSubclass(Enum.class), "PATROL_GROUP");
        Method ensure = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForce",
                CampaignSystem.CampaignState.class,
                kindType,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class);
        ensure.setAccessible(true);

        Object force = ensure.invoke(null, ctx.campaign, patrolKind, Faction.ENEMY,
                "Phase Eight Provenance Patrol", "Red finite inventory", "Validate authority", 900.0, 900.0);

        assertNotNull(force, "projection-only AI reserve must not veto authoritative finite fleet creation");
        assertEquals(0, ctx.campaign.economyExpansion.aiDeploymentReserve);
    }

    @Test
    void strategicMapRenderingDoesNotMutateCampaignState() {
        GameContext ctx = campaignContext();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.RESOURCES;
        ctx.ui.campaignFleetRosterScroll = Integer.MAX_VALUE;
        CampaignSnapshot before = snapshot(ctx);
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Renderer.drawStrategicMap(graphics, ctx, 1280, 720, ctx.WORLD_W, ctx.WORLD_H,
                    0.0, 0.0, 1280.0, 720.0, ctx.player,
                    List.of(), List.of(), List.of(), Double.NaN, Double.NaN,
                    ctx.ui.mapPings, null, ctx.eventBanner);
        } finally {
            graphics.dispose();
        }

        assertEquals(before, snapshot(ctx), "campaign render paths must remain read-only");
        assertEquals(Integer.MAX_VALUE, ctx.ui.campaignFleetRosterScroll,
                "renderer must not normalize interaction state while drawing");
    }

    private static GameContext campaignContext() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 808L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSnapshot snapshot(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx.campaign;
        return new CampaignSnapshot(
                st.sector,
                st.objectiveProgress,
                st.oreLedger.storedOre,
                st.campaignFuel,
                st.campaignSupplies,
                st.campaignAmmo,
                st.campaignSalvage,
                st.campaignForces.size(),
                st.campaignShipPool.size(),
                st.campaignBaseQueues.size(),
                st.campaignYardOrders.size(),
                st.galaxyMainPois.size(),
                st.galaxyAreasOfInterest.size(),
                st.strategicStrikeObjects.size(),
                st.processedResourceTransactionIds.size());
    }

    private record CampaignSnapshot(
            int sector,
            double objectiveProgress,
            int ore,
            int fuel,
            int supplies,
            int ammo,
            int salvage,
            int forces,
            int shipPool,
            int baseQueues,
            int yardOrders,
            int mainLocations,
            int areas,
            int strikes,
            int resourceTransactions) {}
}
