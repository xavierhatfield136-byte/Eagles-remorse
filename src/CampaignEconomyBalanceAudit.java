import app.config.GameConfig;
import app.config.GameMode;

import java.util.ArrayList;
import java.util.List;

/** Objective economy measurements used before owner feel/balance tuning. */
public final class CampaignEconomyBalanceAudit {
    public static final int UNATTENDED_MINING_SECONDS = 600;
    public static final int UNATTENDED_MINING_MAX_ORE = 520;
    public static final int STARTER_MAJOR_UPGRADE_TARGET_MINUTES = 18;
    public static final int STARTER_MAJOR_UPGRADE_TARGET_ORE = 3_200;
    public static final int SALVAGE_PELLET_ORE_REWARD_FOR_AUDIT = 65;

    private CampaignEconomyBalanceAudit() {}

    public static List<MiningRateMeasurement> miningRateMeasurements() {
        return List.of(
                measureMining("starter-miner", ShipRole.MINER, 60, 1.0, 1.0, 2_000),
                measureMining("midgame-miner", ShipRole.MINER, 60, 1.35, 1.0, 2_000),
                measureMining("transport-titan-support", ShipRole.TRANSPORT_TITAN, 60, 1.0, 1.15, 2_000)
        );
    }

    public static EconomyLoopMeasurement oneLoopMeasurement(long seed, int loops) {
        GameContext ctx = campaign(seed);
        CampaignSystem.CampaignState st = ctx.campaign;
        int boundedLoops = Math.max(1, loops);
        int startingOre = CampaignSystem.currentCampaignOre(ctx);
        int startingFleet = st.persistentBlueFleet.size();
        int startingPower = persistentFleetPower(st);
        for (int i = 0; i < boundedLoops; i++) {
            int oreGain = Math.min(UNATTENDED_MINING_MAX_ORE, measureMining("loop-miner", ShipRole.MINER, UNATTENDED_MINING_SECONDS, 1.0, 1.0, 4_000).oreMined());
            CampaignSystem.grantCampaignOre(ctx, oreGain);
            st.campaignSalvage += Math.max(6, oreGain / 70);
            ctx.credits += GameContext.scaleCreditReward(Math.max(120, oreGain / 2));
            attemptOneShipyardCommission(ctx);
        }
        return new EconomyLoopMeasurement(seed, boundedLoops,
                startingOre, CampaignSystem.currentCampaignOre(ctx),
                startingFleet, st.persistentBlueFleet.size(),
                startingPower, persistentFleetPower(st));
    }

    public static List<String> balanceAuditLines() {
        ArrayList<String> out = new ArrayList<>();
        out.add("ECONOMY BALANCE AUDIT  |  objective measurements before owner feel tuning");
        for (MiningRateMeasurement measurement : miningRateMeasurements()) {
            out.add(measurement.label() + "  |  ore/min " + measurement.orePerMinute()
                    + "  |  cargo " + measurement.cargoCapacity()
                    + "  |  depletion " + measurement.depletedOre());
        }
        EconomyLoopMeasurement one = oneLoopMeasurement(64070L, 1);
        EconomyLoopMeasurement three = oneLoopMeasurement(64071L, 3);
        out.add("one-loop power delta  |  fleet +" + (one.endFleetCount() - one.startFleetCount())
                + "  power +" + (one.endFleetPower() - one.startFleetPower())
                + "  ore " + one.startOre() + "->" + one.endOre());
        out.add("three-loop power delta  |  fleet +" + (three.endFleetCount() - three.startFleetCount())
                + "  power +" + (three.endFleetPower() - three.startFleetPower())
                + "  ore " + three.startOre() + "->" + three.endOre());
        out.add("starter target  |  first major upgrade target " + STARTER_MAJOR_UPGRADE_TARGET_ORE
                + " ore in ~" + STARTER_MAJOR_UPGRADE_TARGET_MINUTES + " minutes, pending owner feel review");
        out.add("unattended mining ceiling  |  ten-minute single-patch ceiling " + UNATTENDED_MINING_MAX_ORE + " ore");
        return out;
    }

    public static SalvageMiningComparison salvageMiningComparison() {
        int oneMinuteStarterOre = measureMining("starter-miner", ShipRole.MINER, 60, 1.0, 1.0, 2_000).oreMined();
        int carefulSalvageEquivalentOre = SALVAGE_PELLET_ORE_REWARD_FOR_AUDIT * 4;
        int salvageSupplies = 6;
        int salvageMaterials = 4;
        return new SalvageMiningComparison(oneMinuteStarterOre, carefulSalvageEquivalentOre, salvageSupplies, salvageMaterials);
    }

    public static MiningRateMeasurement measureMining(String label,
                                                      ShipRole role,
                                                      int seconds,
                                                      double miningMul,
                                                      double baseMul,
                                                      int asteroidOre) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 64100L, false));
        ctx.miningMul = Math.max(0.0, miningMul);
        ctx.miningBaseMul = Math.max(0.0, baseMul);
        Ship miner = role == ShipRole.MOTHERSHIP
                ? new Player(role, 2500.0, 2500.0)
                : new FleetShip(role, Faction.ALLY, 2500.0, 2500.0);
        miner.faction = Faction.ALLY;
        miner.miningRange = Math.max(miner.miningRange, 80.0);
        Asteroid asteroid = new Asteroid(2500.0 + Math.max(20.0, miner.radius), 2500.0, 60.0, Math.max(0, asteroidOre));
        int mined = 0;
        for (int i = 0; i < Math.max(0, seconds) * 5; i++) {
            mined += miner.tryMine(asteroid, 0.2 * ctx.miningMul * ctx.miningBaseMul);
        }
        int depleted = Math.max(0, asteroidOre - Math.max(0, asteroid.ore));
        return new MiningRateMeasurement(label, role, Math.max(0, seconds), mined, Math.max(0, miner.cargoMax), depleted);
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void attemptOneShipyardCommission(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null) return;
        CampaignSystem.CampaignLocation yard = null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.services.contains(CampaignSystem.HubService.SHIPYARD)) {
                yard = location;
                break;
            }
        }
        if (yard == null) return;
        st.selectedGalaxyLocationId = yard.id;
        st.currentGalaxyLocationId = yard.id;
        st.dockedGalaxyLocationId = yard.id;
        st.playerGalaxyX = yard.x;
        st.playerGalaxyY = yard.y;
        CampaignSystem.openSelectedHubService(ctx, CampaignSystem.HubService.SHIPYARD);
        CampaignSystem.confirmSelectedHubService(ctx);
    }

    private static int persistentFleetPower(CampaignSystem.CampaignState st) {
        if (st == null) return 0;
        int power = 0;
        for (Object ignored : st.persistentBlueFleet) power += 10;
        return power;
    }

    public record MiningRateMeasurement(String label,
                                        ShipRole role,
                                        int seconds,
                                        int oreMined,
                                        int cargoCapacity,
                                        int depletedOre) {
        public int orePerMinute() {
            if (seconds <= 0) return 0;
            return (int) Math.round(oreMined * 60.0 / seconds);
        }
    }

    public record EconomyLoopMeasurement(long seed,
                                         int loops,
                                         int startOre,
                                         int endOre,
                                         int startFleetCount,
                                         int endFleetCount,
                                         int startFleetPower,
                                         int endFleetPower) {}

    public record SalvageMiningComparison(int oneMinuteStarterMiningOre,
                                          int carefulSalvageEquivalentOre,
                                          int salvageSupplies,
                                          int salvageRepairMaterials) {
        public boolean salvageIsSituationallyCompetitive() {
            return carefulSalvageEquivalentOre + salvageSupplies * 8 + salvageRepairMaterials * 10
                    >= Math.max(1, oneMinuteStarterMiningOre) / 2;
        }
    }
}
