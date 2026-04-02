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
        assertTrue(CampaignSystem.hudObjectiveDetail(ctx).contains("Hold the trade-hub evacuation lanes"));
        assertTrue(ctx.campaign.introSequenceActive);
        assertEquals(3, ctx.campaign.persistentBlueFleet.size());
        assertTrue(hasNamedShip(ctx, ShipRole.MOBILE_STATION_TITAN, "Green Harbor Forge"));
        assertTrue(hasNamedShip(ctx, ShipRole.TRANSPORT_TITAN, "Green Ledger Titan"));
        assertTrue(hasNamedShip(ctx, ShipRole.BASE, "Green Exchange Spire"));
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
