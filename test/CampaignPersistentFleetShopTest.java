import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPersistentFleetShopTest {

    @Test
    void serviceRecordReadoutExposesCaptainMoraleRepairsAndEvents() throws Exception {
        GameContext ctx = campaignShopContext(100_000, 20_000, 5, 5);
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));

        Object entry = ctx.campaign.persistentBlueFleet.get(0);
        setField(entry, "captainName", "Captain Mira Vale");
        setField(entry, "crewExperience", 14);
        setField(entry, "morale", 61);
        setField(entry, "refusalRisk", 9);
        setField(entry, "kills", 3);
        setField(entry, "rescues", 2);
        setField(entry, "retreats", 1);
        setField(entry, "scars", 4);
        setField(entry, "hullConditionFrac", 0.52);
        setField(entry, "shieldConditionFrac", 0.41);
        setField(entry, "serviceHistory", "MAJOR HULL DAMAGE / MISSION COMPLETE");

        String record = String.join("\n", CampaignSystem.campaignFleetServiceRecordLines(ctx, 4));

        assertTrue(record.contains("Captain Mira Vale"));
        assertTrue(record.contains("MORALE 61"));
        assertTrue(record.contains("REPAIRS NEEDED"));
        assertTrue(record.contains("KILLS 3 RESCUES 2 RETREATS 1 SCARS 4"));
        assertTrue(record.contains("MAJOR HULL DAMAGE"));
    }

    @Test
    void logisticsHullsCanBeCommissionedWithCampaignOrePricing() {
        GameContext ctx = campaignShopContext(10_000, 2_000, 1, 1);

        assertEquals(14, CampaignSystem.campaignOreCost(ShipRole.MINER, 160, 0));
        assertEquals(34, CampaignSystem.campaignOreCost(ShipRole.HAULER, 260, 1));

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MINER, 180, 0));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HAULER, 300, 1));

        assertEquals(9_520, ctx.credits);
        assertEquals(1_952, ctx.player.cargo);
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
    }

    @Test
    void campaignShipyardMustClimbBeforeTitansCanBeCommissioned() {
        GameContext ctx = campaignShopContext(100_000, 10_000, 1, 1);

        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));

        ctx.baseUpgrades.get(ctx.player).hangarLv = 3;
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertEquals(10_000, ctx.player.cargoMax);
    }

    @Test
    void commandGridLimitsEarlyFleetSnowballUntilTitansExpandIt() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 3, 3);

        for (int i = 0; i < 5; i++) {
            assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.PATROL, 0, 0));
        }
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));
    }

    @Test
    void midAndLateTitansRespectSectorAndInfrastructureRequirements() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 5);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.COMMAND_INTEL_TITAN, TitanArchetype.COMMAND_INTEL.costCredits(), 3));
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3));

        ctx.campaign.sector = 9;
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3));

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3));
    }

    @Test
    void standardSupershipUnlocksWithMidCampaignFlagshipEliteBerth() {
        GameContext earlyCtx = campaignShopContext(250_000, 25_000, 4, 5);
        assertEquals(0, CampaignSystem.campaignEliteCommandCapacity(earlyCtx));
        assertFalse(CampaignSystem.purchasePersistentBlueShip(earlyCtx, ShipRole.SUPERSHIP, 4200, 3));

        GameContext midCtx = campaignShopContext(250_000, 25_000, 4, 6);
        assertEquals(1, CampaignSystem.campaignEliteCommandCapacity(midCtx));
        assertTrue(CampaignSystem.flagshipSupershipBerthOnline(midCtx));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(midCtx, ShipRole.SUPERSHIP, 4200, 3));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(midCtx, ShopHullCategory.CAPITAL));
        assertEquals(1, CampaignSystem.campaignEliteCommandUsed(midCtx));
    }

    @Test
    void eliteReinforcementsTitanAddsHonorGuardTaskGroup() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 10);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.ELITE_REINFORCEMENTS_TITAN, TitanArchetype.ELITE_REINFORCEMENTS.costCredits(), 3));

        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
        assertEquals(21, CampaignSystem.campaignStandardCommandCapacity(ctx));

        long battleships = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.BATTLESHIP && ship.faction == Faction.ALLY)
                .count();
        long battlecruisers = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.BATTLECRUISER && ship.faction == Faction.ALLY)
                .count();
        long frigates = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.FRIGATE && ship.faction == Faction.ALLY)
                .count();
        long screens = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.CIWS_CORVETTE && ship.faction == Faction.ALLY)
                .count();

        assertEquals(1, battleships);
        assertEquals(1, battlecruisers);
        assertEquals(1, frigates);
        assertEquals(1, screens);
    }

    @Test
    void eliteSupershipCommandTitanRemainsSeparatePurchaseWithoutAutoPackage() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 10);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN, TitanArchetype.ELITE_SUPERSHIP_COMMAND.costCredits(), 3));

        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
    }

    @Test
    void persistentFleetMixIsTrackedPerBandOnceCommandGridIsExpanded() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 9);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.BULWARK_TITAN, TitanArchetype.BULWARK.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.PATROL, 0, 0));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HAULER, 300, 1));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.DREADNOUGHT, 3000, 3));

        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
    }

    @Test
    void legacyFleetCapUpgradePurchaseIsDisabled() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 9);

        assertFalse(CampaignSystem.purchasePersistentFleetCapUpgrade(ctx, ShopHullCategory.ESCORT));
        assertEquals(0, CampaignSystem.persistentFleetCapUpgradeStep(ShopHullCategory.ESCORT));
        assertEquals(0, CampaignSystem.persistentFleetCapUpgradeCreditCost(ctx, ShopHullCategory.ESCORT));
        assertEquals(0, CampaignSystem.persistentFleetCapUpgradeOreCost(ctx, ShopHullCategory.ESCORT));
    }

    @Test
    void legacyFleetCapUpgradeLevelsAreClearedThroughCheckpointRestore() throws Exception {
        CampaignCheckpointStore.clear();
        GameContext ctx = campaignShopContext(50_000, 8_000, 5, 11);
        ctx.campaign.escortCapUpgradeLevel = 2;
        ctx.campaign.lineCapUpgradeLevel = 1;
        ctx.campaign.capitalCapUpgradeLevel = 1;

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 12);

        GameContext restored = campaignShopContext(0, 0, 1, 1);
        assertTrue(applyCheckpoint(restored, checkpoint));

        assertEquals(0, restored.campaign.escortCapUpgradeLevel);
        assertEquals(0, restored.campaign.lineCapUpgradeLevel);
        assertEquals(0, restored.campaign.capitalCapUpgradeLevel);
        assertEquals(CampaignSystem.persistentFleetCap(ShopHullCategory.ESCORT),
                CampaignSystem.persistentFleetCap(restored, ShopHullCategory.ESCORT));
        assertEquals(CampaignSystem.persistentFleetCap(ShopHullCategory.LINE),
                CampaignSystem.persistentFleetCap(restored, ShopHullCategory.LINE));
        assertEquals(CampaignSystem.persistentFleetCap(ShopHullCategory.CAPITAL),
                CampaignSystem.persistentFleetCap(restored, ShopHullCategory.CAPITAL));
    }

    @Test
    void fleetHubUpgradeSlotsAreRoleSpecificAndApplyImmediately() {
        GameContext ctx = campaignShopContext(100_000, 20_000, 5, 5);
        ctx.campaign.awaitingEpisodeLaunch = true;
        ctx.state = GameState.FLEET;
        ctx.ui.baseMenuOpen = true;

        FleetShip picket = new FleetShip(ShipRole.PICKET, Faction.ALLY, 2400.0, 2400.0);
        ctx.ships.add(picket);
        BaseUpgrades picketUp = new BaseUpgrades().bindTo(picket);
        ctx.baseUpgrades.put(picket, picketUp);
        ctx.ui.fleetSelectedShipId = picket.id;

        assertFalse(CampaignSystem.campaignShipUpgradeAvailable(picket, 4));
        assertFalse(CampaignSystem.campaignShipUpgradeAvailable(picket, 5));
        int creditsBefore = ctx.credits;
        int oreBefore = ctx.player.cargo;
        UISystem.tryUpgradeBase(ctx, 4);
        UISystem.tryUpgradeBase(ctx, 5);
        assertEquals(0, picketUp.miningLv);
        assertEquals(0, picketUp.hangarLv);
        assertEquals(creditsBefore, ctx.credits);
        assertEquals(oreBefore, ctx.player.cargo);

        FleetShip miner = new FleetShip(ShipRole.MINER, Faction.ALLY, 2450.0, 2450.0);
        ctx.ships.add(miner);
        BaseUpgrades minerUp = new BaseUpgrades().bindTo(miner);
        ctx.baseUpgrades.put(miner, minerUp);
        ctx.ui.fleetSelectedShipId = miner.id;
        double miningBefore = miner.miningRate;
        int cargoBefore = miner.cargoMax;
        creditsBefore = ctx.credits;
        oreBefore = ctx.player.cargo;
        UISystem.tryUpgradeBase(ctx, 4);
        assertTrue(CampaignSystem.campaignShipUpgradeAvailable(miner, 4));
        assertEquals(miningBefore + 1.4, miner.miningRate, 1e-9);
        assertEquals(cargoBefore + 20, miner.cargoMax);
        assertEquals(creditsBefore - 310, ctx.credits);
        assertEquals(oreBefore - 150, ctx.player.cargo);

        FleetShip carrier = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 2500.0, 2500.0);
        ctx.ships.add(carrier);
        BaseUpgrades carrierUp = new BaseUpgrades().bindTo(carrier);
        ctx.baseUpgrades.put(carrier, carrierUp);
        ctx.ui.fleetSelectedShipId = carrier.id;
        int fightersBefore = carrier.maxFighters;
        creditsBefore = ctx.credits;
        oreBefore = ctx.player.cargo;
        UISystem.tryUpgradeBase(ctx, 5);
        assertTrue(CampaignSystem.campaignShipUpgradeAvailable(carrier, 5));
        assertEquals(fightersBefore + 1, carrier.maxFighters);
        assertEquals(creditsBefore - 800, ctx.credits);
        assertEquals(oreBefore - 270, ctx.player.cargo);
    }

    @Test
    void commissionedHullAppearsInFleetHubAndLaunchesIntoNextMission() throws Exception {
        GameContext ctx = campaignShopContext(100_000, 20_000, 5, 5);
        ctx.campaign.pendingEpisodeSector = 6;
        ctx.campaign.routeArrivalSourceSector = 5;

        invokeCampaignPrivate("enterFleetHub",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, ctx.campaign);

        long beforeFleetHubVisible = alliedRoleCount(ctx, ShipRole.FRIGATE);
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));
        long afterFleetHubVisible = alliedRoleCount(ctx, ShipRole.FRIGATE);

        assertTrue(afterFleetHubVisible > beforeFleetHubVisible,
                "commissioned hull should appear immediately in the fleet hub");
        assertTrue(CampaignSystem.launchPendingEpisode(ctx));

        long afterLaunchVisible = alliedRoleCount(ctx, ShipRole.FRIGATE);
        assertTrue(afterLaunchVisible >= 1,
                "commissioned hull should spawn with the player in the next mission");
    }

    private static GameContext campaignShopContext(int credits, int ore, int hangarTier, int sector) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        st.sector = sector;
        ctx.campaign = st;

        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.cargo = ore;
        ctx.player.cargoMax = Math.max(ctx.player.cargoMax, ore);
        ctx.ships.add(ctx.player);

        BaseUpgrades upgrades = new BaseUpgrades();
        upgrades.hangarLv = hangarTier;
        ctx.baseUpgrades.put(ctx.player, upgrades);
        ctx.credits = credits;
        return ctx;
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

    private static Object invokeCampaignPrivate(String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static long alliedRoleCount(GameContext ctx, ShipRole role) {
        return ctx.ships.stream()
                .filter(ship -> ship != null
                        && ship.role == role
                        && ship.faction == Faction.ALLY
                        && ship.alive
                        && !ship.dying
                        && ship.hp > 0)
                .count();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
