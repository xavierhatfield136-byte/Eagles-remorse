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

class CampaignCompatibilityOverhaulTest {

    @AfterEach
    void cleanupCheckpoint() {
        CampaignCheckpointStore.clear();
    }

    @Test
    void escortSideObjectiveProtectsEscortTitanInsteadOfPlayerHull() throws Exception {
        GameContext ctx = initializedCampaignContext();
        startSector(ctx, 5);

        Ship escort = ctx.campaign.escortShip;
        assertNotNull(escort);
        int escortHpStart = escort.hp;

        ctx.player.hp = Math.max(1, ctx.player.hp - 20);
        CampaignSystem.update(ctx, GameContext.DT);
        assertFalse(ctx.campaign.sideObjectiveFailed, "player hull damage should not fail escort integrity side objectives");

        escort.hp = Math.max(1, escortHpStart - 1);
        CampaignSystem.update(ctx, GameContext.DT);
        assertTrue(ctx.campaign.sideObjectiveFailed, "escort hull damage should fail escort integrity side objectives");
    }

    @Test
    void escortObjectiveNeedsFormationPresenceToAdvance() throws Exception {
        GameContext ctx = initializedCampaignContext();
        startSector(ctx, 5);

        ctx.ships.removeIf(ship -> ship != null && ship.faction == Faction.ENEMY);
        Ship escort = ctx.campaign.escortShip;
        assertNotNull(escort);

        ctx.player.x = escort.x + 1800.0;
        ctx.player.y = escort.y + 1800.0;
        runCampaignTicks(ctx, 180);
        assertEquals(0.0, ctx.campaign.objectiveProgress, 0.05,
                "escort progress should stall when the flagship leaves the escort formation");

        ctx.player.x = escort.x - 120.0;
        ctx.player.y = escort.y + 20.0;
        runCampaignTicks(ctx, 180);
        assertTrue(ctx.campaign.objectiveProgress > 1.0,
                "escort progress should resume once the flagship reforms on the protected titan");
        assertTrue(ctx.campaign.escortFormationIntegrity > 0.55,
                "formation integrity should recover when the flagship and support screen stay nearby");
    }

    @Test
    void coalitionTaskGroupsUnlockForLaterSectorsAndSurviveCheckpointRestore() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 7);
        ctx.campaign.sideObjectiveCompleted = true;
        grantStoryFleetReward(ctx);
        startSector(ctx, 8);
        assertTrue(hasNamedShip(ctx, ShipRole.LIGHT_CRUISER, "Green Contract Cruiser"));
        assertTrue(hasNamedShip(ctx, ShipRole.CIWS_CORVETTE, "Green Contract Flak"));
        assertTrue(hasNamedShip(ctx, ShipRole.FRIGATE, "Green Contract Frigate"));
        assertEquals(2, coalitionTier(ctx.campaign, "greenContractTier"));

        startSector(ctx, 10);
        ctx.campaign.sideObjectiveCompleted = true;
        grantStoryFleetReward(ctx);
        startSector(ctx, 11);
        assertTrue(hasNamedShip(ctx, ShipRole.MISSILE_BOAT, "Yellow Liberation Missile Boat"));
        assertTrue(hasNamedShip(ctx, ShipRole.CIWS_CORVETTE, "Yellow Liberation Flak"));
        assertTrue(hasNamedShip(ctx, ShipRole.FRIGATE, "Yellow Liberation Frigate"));
        assertEquals(2, coalitionTier(ctx.campaign, "yellowLiberationTier"));

        CampaignCheckpointStore.Checkpoint cp = captureCheckpoint(ctx, 12);

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, cp));
        startSector(restored, 11);

        assertTrue(restored.campaign.greenContractFleetJoined);
        assertTrue(restored.campaign.yellowLiberationFleetJoined);
        assertTrue(hasNamedShip(restored, ShipRole.LIGHT_CRUISER, "Green Contract Cruiser"));
        assertTrue(hasNamedShip(restored, ShipRole.MISSILE_BOAT, "Yellow Liberation Missile Boat"));
        assertEquals(2, coalitionTier(restored.campaign, "greenContractTier"));
        assertEquals(2, coalitionTier(restored.campaign, "yellowLiberationTier"));
    }

    @Test
    void purchasedTitansAnchorPersistentCommandGroupsForLaterCommissions() {
        GameContext ctx = campaignShopContext(20_000, 4_000);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));

        Ship titan = firstShip(ctx, ShipRole.TRANSPORT_TITAN);
        Ship frigateA = nthShip(ctx, ShipRole.FRIGATE, 0);
        Ship frigateB = nthShip(ctx, ShipRole.FRIGATE, 1);
        assertNotNull(titan);
        assertNotNull(frigateA);
        assertNotNull(frigateB);

        double titanDistA = Math.hypot(frigateA.x - titan.x, frigateA.y - titan.y);
        double titanDistB = Math.hypot(frigateB.x - titan.x, frigateB.y - titan.y);
        double playerDistA = Math.hypot(frigateA.x - ctx.player.x, frigateA.y - ctx.player.y);
        double playerDistB = Math.hypot(frigateB.x - ctx.player.x, frigateB.y - ctx.player.y);
        assertTrue(titanDistA < playerDistA, "command-group frigates should stage off their titan anchor");
        assertTrue(titanDistB < playerDistB, "command-group frigates should stage off their titan anchor");
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

    private static void grantStoryFleetReward(GameContext ctx) throws Exception {
        Method grantStoryFleetReward = CampaignSystem.class.getDeclaredMethod(
                "grantStoryFleetReward",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        grantStoryFleetReward.setAccessible(true);
        grantStoryFleetReward.invoke(null, ctx, ctx.campaign);
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method captureCheckpoint = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                int.class
        );
        captureCheckpoint.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) captureCheckpoint.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method applyCheckpoint = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignCheckpointStore.Checkpoint.class
        );
        applyCheckpoint.setAccessible(true);
        return (boolean) applyCheckpoint.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static int coalitionTier(CampaignSystem.CampaignState state, String methodName) throws Exception {
        Method tier = CampaignSystem.class.getDeclaredMethod(methodName, CampaignSystem.CampaignState.class);
        tier.setAccessible(true);
        return (int) tier.invoke(null, state);
    }

    private static GameContext campaignShopContext(int credits, int ore) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        ctx.campaign = st;

        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.cargo = ore;
        ctx.player.cargoMax = Math.max(ctx.player.cargoMax, ore);
        ctx.ships.add(ctx.player);

        BaseUpgrades upgrades = new BaseUpgrades();
        upgrades.hangarLv = 3;
        ctx.baseUpgrades.put(ctx.player, upgrades);
        ctx.credits = credits;
        return ctx;
    }

    private static void runCampaignTicks(GameContext ctx, int ticks) {
        for (int i = 0; i < ticks; i++) {
            CampaignSystem.update(ctx, GameContext.DT);
        }
    }

    private static boolean hasNamedShip(GameContext ctx, ShipRole role, String name) {
        for (Ship ship : ctx.ships) {
            if (ship == null) continue;
            if (ship.role != role) continue;
            if (name.equals(ship.name)) return true;
        }
        return false;
    }

    private static Ship firstShip(GameContext ctx, ShipRole role) {
        return nthShip(ctx, role, 0);
    }

    private static Ship nthShip(GameContext ctx, ShipRole role, int index) {
        int seen = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || ship.role != role) continue;
            if (seen == index) return ship;
            seen++;
        }
        return null;
    }
}
