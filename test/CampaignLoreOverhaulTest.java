import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignLoreOverhaulTest {

    @AfterEach
    void cleanupCheckpoint() {
        CampaignCheckpointStore.clear();
    }

    @Test
    void campaignStartLoadsFirstLocalSiteAndStarterBlueFleet() {
        GameContext ctx = initializedPlayerFacingCampaignContext();

        assertNotNull(ctx.campaign);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);
        assertFalse(CampaignSystem.isStrategicOvermapMode(ctx), "fresh campaign should start inside the first local site");
        assertFalse(ctx.ui.mapOpen, "fresh campaign should not open on the overworld map");
        assertTrue(ctx.campaign.galaxyEncounterActive);
        assertTrue(ctx.campaign.galaxyAmbientEncounterActive);
        assertEquals("poi-01", ctx.campaign.currentGalaxyLocationId);
        assertEquals("poi-01", ctx.campaign.selectedGalaxyLocationId);
        assertEquals("poi-01", ctx.campaign.activeGalaxyEncounterLocationId);
        assertTrue(ctx.asteroids.size() >= 8, "opening site should immediately expose mineable ore");
        assertTrue(ctx.ships.stream().anyMatch(ship -> ship != null && ship.role == ShipRole.MINER),
                "opening site should include local mining or starter mining activity");
        assertFalse(ctx.campaign.introSequenceActive);
        assertEquals(4, ctx.campaign.persistentBlueFleet.size());
        assertTrue(hasNamedShip(ctx, ShipRole.MINER, "Blue Prospector One"));
        assertTrue(hasNamedShip(ctx, ShipRole.BASE, "Green Anchorage Pelagos Control"));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Green Anchorage Pelagos".equals(l.label)));
    }

    @Test
    void campaignStartDoesNotPlayLegacyEarthfallDialogue() {
        GameContext ctx = initializedCampaignContext();

        CampaignSystem.update(ctx, 0.1);

        assertFalse(ctx.ui.voiceCaption.contains("Earth has fallen"));
        assertFalse(ctx.ui.voiceCaption.contains("return home immediately"));
        assertFalse(ctx.eventBanner.contains("URGENT SOL TRAFFIC"));
    }

    @Test
    void escortSectorsUseTitanFlagships() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 8);
        assertNotNull(ctx.campaign.escortShip);
        assertEquals(ShipRole.TRANSPORT_TITAN, ctx.campaign.escortShip.role);
        assertEquals("Green Exodus Transport Titan", ctx.campaign.escortShip.name);
        assertTrue(ctx.campaign.objectiveGoal <= 100.0, "escort sectors should now resolve inside the 100-second window");

        startSector(ctx, 20);
        assertNotNull(ctx.campaign.escortShip);
        assertEquals(ShipRole.BOARDING_RECOVERY_TITAN, ctx.campaign.escortShip.role);
        assertEquals("Yellow Recovery Titan Renewal", ctx.campaign.escortShip.name);
        assertTrue(ctx.campaign.objectiveGoal <= 100.0, "escort sectors should now resolve inside the 100-second window");
    }

    @Test
    void bossSectorsEscalateToTitanAndMothershipFlagships() throws Exception {
        GameContext ctx = initializedCampaignContext();

        assertBossRole(ctx, 7, ShipRole.INTERDICTION_TITAN, "AI PURSUIT TITAN RED KNIFE");
        assertBossRole(ctx, 16, ShipRole.ARTILLERY_TITAN, "ASH GATE ARTILLERY TITAN");
        assertBossRole(ctx, 24, ShipRole.MOTHERSHIP, "AI MOTHERSHIP EARTHFALL");
    }

    @Test
    void lateCampaignSectorsExposePlanetaryLandmarksInHud() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 21);
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Luna".equals(l.label)));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Earthrise".equals(l.label)));
        assertTrue(CampaignSystem.hudObjectiveExpandedDetail(ctx).contains("Luna Perimeter"));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> l.subtitle.contains("foundry")));

        startSector(ctx, 24);
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "Earth".equals(l.label)));
        assertTrue(CampaignSystem.hudObjectiveExpandedDetail(ctx).contains("Earth High Orbit"));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> l.subtitle.contains("city")));
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

        startSector(ctx, 8);
        assertEquals("exodus_gas_giant", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("exodus_gas_giant", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("space_nebula", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(!Renderer.campaignBackdropReplacesNebula(ctx));

        startSector(ctx, 13);
        assertEquals("contract_world_array", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("contract_world_array", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("colony_arcology", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(Renderer.campaignBackdropReplacesNebula(ctx));
        assertEquals(0.0, Renderer.campaignBackdropPhaseBlend(ctx), 0.0001);

        ctx.campaign.objectiveStage = 1;
        ctx.campaign.captureArmed = true;
        ctx.campaign.objectiveProgress = 2.0;
        ctx.campaign.objectiveGoal = 3.0;
        assertEquals("contract_world_array_phase1", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropPhaseBlend(ctx) > 0.5);

        startSector(ctx, 21);
        assertEquals("luna_earthrise_approach", Renderer.campaignBackdropDebugName(ctx));
        assertEquals("luna_earthrise_approach", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropImageKey(ctx)));
        assertEquals("lunar_installation", Renderer.campaignBackdropFieldModeDebugName(ctx));
        assertTrue(Renderer.campaignBackdropReplacesNebula(ctx));
        assertEquals(0.0, Renderer.campaignBackdropPhaseBlend(ctx), 0.0001);

        ctx.campaign.objectiveStage = 1;
        ctx.campaign.objectiveProgress = 2.0;
        ctx.campaign.objectiveGoal = 3.0;
        assertEquals("luna_earthrise_approach_phase1", Renderer.campaignBackdropImageKey(ctx));
        assertTrue(Renderer.campaignBackdropPhaseBlend(ctx) > 0.5);

        startSector(ctx, 24);
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
                "jump_ring_frontier",
                "relay_halo_moon",
                "relay_halo_moon",
                "burning_debris_wake",
                "burning_debris_wake",
                "exodus_gas_giant",
                "trade_spine_industrial_orbit",
                "trade_spine_industrial_orbit",
                "trade_spine_industrial_orbit",
                "contract_world_array",
                "contract_world_array",
                "contract_world_array",
                "ash_gate_gas_giant",
                "ash_gate_gas_giant",
                "outer_sol_starline",
                "outer_sol_starline",
                "liberation_moon_orbit",
                "liberation_moon_orbit",
                "luna_earthrise_approach",
                "luna_earthrise_approach",
                "earth_high_orbit",
                "earth_high_orbit"
        };
        for (int sector = 1; sector <= 24; sector++) {
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

    private static GameContext initializedPlayerFacingCampaignContext() {
        CampaignCheckpointStore.clear();
        GameConfig config = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false)
                .withAutoLaunchCampaignStartSite(true);
        GameContext ctx = new GameContext(config);
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
