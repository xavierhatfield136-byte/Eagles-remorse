import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignLoreOverhaulTest {

    @AfterEach
    void cleanupCheckpoint() {
        CampaignCheckpointStore.clear();
    }

    @Test
    void campaignStartUsesTradeHubLoreAndStarterBlueFleet() {
        GameContext ctx = initializedCampaignContext();

        assertNotNull(ctx.campaign);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);
        assertTrue(CampaignSystem.hudObjectiveTitle(ctx).contains("TRADE HUB COLLAPSE"));
        assertTrue(CampaignSystem.hudObjectiveTitle(ctx).contains("ANCHORAGE FIRESTORM"));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("Far Trade Anchorage"));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("AO: Far Trade Anchorage"));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("arcology crowns"));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("Hold the trade-hub evacuation lanes"));
        assertTrue(ctx.campaign.introSequenceActive);
        assertEquals(3, ctx.campaign.persistentBlueFleet.size());
        assertTrue(hasNamedShip(ctx, ShipRole.MOBILE_STATION_TITAN, "Green Harbor Forge"));
        assertTrue(hasNamedShip(ctx, ShipRole.TRANSPORT_TITAN, "Green Ledger Titan"));
        assertTrue(hasNamedShip(ctx, ShipRole.BASE, "Green Exchange Spire"));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Far Trade Anchorage".equals(l.label)));
    }

    @Test
    void escortSectorsUseTitanFlagships() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 5);
        assertNotNull(ctx.campaign.escortShip);
        assertEquals(ShipRole.TRANSPORT_TITAN, ctx.campaign.escortShip.role);
        assertEquals("Green Exodus Transport Titan", ctx.campaign.escortShip.name);

        startSector(ctx, 10);
        assertNotNull(ctx.campaign.escortShip);
        assertEquals(ShipRole.BOARDING_RECOVERY_TITAN, ctx.campaign.escortShip.role);
        assertEquals("Liberated Yellow Recovery Titan", ctx.campaign.escortShip.name);
    }

    @Test
    void bossSectorsEscalateToTitanAndMothershipFlagships() throws Exception {
        GameContext ctx = initializedCampaignContext();

        assertBossRole(ctx, 4, ShipRole.INTERDICTION_TITAN, "AI PURSUIT TITAN RED KNIFE");
        assertBossRole(ctx, 8, ShipRole.ARTILLERY_TITAN, "ASH GATE ARTILLERY TITAN");
        assertBossRole(ctx, 12, ShipRole.MOTHERSHIP, "AI MOTHERSHIP EARTHFALL");
    }

    @Test
    void lateCampaignSectorsExposePlanetaryLandmarksInHud() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 11);
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Luna".equals(l.label)));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Earthrise".equals(l.label)));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("AO: Luna / Earthrise"));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("foundry belts"));

        startSector(ctx, 12);
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Earth".equals(l.label)));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("AO: Earth / Earth High Orbit"));
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("city webs"));
    }

    @Test
    void campaignBackdropPresetsShiftBySectorAndPhase() throws Exception {
        GameContext ctx = initializedCampaignContext();

        assertEquals("trade_hub_colony", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("trade_hub_colony", Renderer.campaignBackdropBaseImageKey(ctx));
        assertEquals("trade_hub_colony", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("colony_arcology", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(Renderer.campaignBackdropReplacesNebula(ctx));
        assertEquals(0.0, Renderer.campaignBackdropPhaseBlend(ctx), 0.0001);

        startSector(ctx, 5);
        assertEquals("exodus_gas_giant", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("exodus_gas_giant", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("space_nebula", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(!Renderer.campaignBackdropReplacesNebula(ctx));

        startSector(ctx, 7);
        assertEquals("contract_world_array", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("contract_world_array", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("colony_arcology", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(Renderer.campaignBackdropReplacesNebula(ctx));
        assertEquals(0.0, Renderer.campaignBackdropPhaseBlend(ctx), 0.0001);

        ctx.campaign.objectiveStage = 1;
        ctx.campaign.captureArmed = true;
        ctx.campaign.objectiveProgress = 60.0;
        ctx.campaign.objectiveGoal = 120.0;
        assertEquals("contract_world_array_phase1", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropPhaseBlend(ctx) > 0.5);

        startSector(ctx, 11);
        assertEquals("luna_earthrise_approach", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("luna_earthrise_approach", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("lunar_installation", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(Renderer.campaignBackdropReplacesNebula(ctx));
        assertEquals(0.0, Renderer.campaignBackdropPhaseBlend(ctx), 0.0001);

        ctx.campaign.objectiveStage = 1;
        ctx.campaign.objectiveProgress = 5.0;
        ctx.campaign.objectiveGoal = 10.0;
        assertEquals("luna_earthrise_approach_phase1", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropPhaseBlend(ctx) > 0.5);

        startSector(ctx, 12);
        assertEquals("earth_high_orbit", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("earth_high_orbit", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("homeworld_citylights", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(Renderer.campaignBackdropReplacesNebula(ctx));
        assertEquals(1.0, Renderer.campaignBackdropPhaseBlend(ctx), 0.0001);

        ctx.campaign.objectiveStage = 1;
        assertEquals("earth_high_orbit_phase1", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
    }

    @Test
    void campaignBackgroundAssetsExistForAllCurrentSectors() throws Exception {
        GameContext ctx = initializedCampaignContext();
        String[] keys = {
                "trade_hub_colony",
                "jump_ring_frontier",
                "relay_halo_moon",
                "burning_debris_wake",
                "exodus_gas_giant",
                "trade_spine_industrial_orbit",
                "contract_world_array",
                "ash_gate_gas_giant",
                "outer_sol_starline",
                "liberation_moon_orbit",
                "luna_earthrise_approach",
                "earth_high_orbit"
        };
        for (int sector = 1; sector <= 12; sector++) {
            startSector(ctx, sector);
            assertEquals(keys[sector - 1], Renderer.campaignBackdropBaseImageKey(ctx));
            assertTrue(Renderer.campaignBackdropImageAvailable(keys[sector - 1]),
                    "expected campaign background asset for " + keys[sector - 1]);
        }
    }

    private static GameContext initializedCampaignContext() {
        CampaignCheckpointStore.clear();
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static void assertBossRole(GameContext ctx, int sector, ShipRole role, String expectedName) throws Exception {
        startSector(ctx, sector);
        Ship boss = findShipById(ctx, ctx.campaign.bossTargetId);
        assertNotNull(boss, "expected boss spawn for sector " + sector);
        assertEquals(role, boss.role);
        assertEquals(expectedName, boss.name);
    }

    private static Ship findShipById(GameContext ctx, int id) {
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == id) {
                return ship;
            }
        }
        return null;
    }

    private static boolean hasNamedShip(GameContext ctx, ShipRole role, String name) {
        for (Ship ship : ctx.ships) {
            if (ship == null) continue;
            if (ship.role != role) continue;
            if (name.equals(ship.name)) return true;
        }
        return false;
    }
}
