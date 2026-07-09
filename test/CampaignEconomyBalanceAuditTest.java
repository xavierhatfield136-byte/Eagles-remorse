import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignEconomyBalanceAuditTest {
    @Test
    void miningRatesAreMeasuredForStarterMidgameAndTransportTitanConfigurations() {
        List<CampaignEconomyBalanceAudit.MiningRateMeasurement> measurements =
                CampaignEconomyBalanceAudit.miningRateMeasurements();

        assertTrue(measurements.stream().anyMatch(m -> m.label().equals("starter-miner") && m.orePerMinute() > 0));
        assertTrue(measurements.stream().anyMatch(m -> m.label().equals("midgame-miner") && m.orePerMinute() > 0));
        assertTrue(measurements.stream().anyMatch(m -> m.label().equals("transport-titan-support") && m.cargoCapacity() >= 1000));
        assertFalse(CampaignEconomyBalanceAudit.balanceAuditLines().isEmpty());
    }

    @Test
    void unattendedSinglePatchMiningStaysBelowRunawayCeiling() {
        CampaignEconomyBalanceAudit.MiningRateMeasurement tenMinutePatch =
                CampaignEconomyBalanceAudit.measureMining("ten-minute-single-patch",
                        ShipRole.MINER,
                        CampaignEconomyBalanceAudit.UNATTENDED_MINING_SECONDS,
                        1.0,
                        1.0,
                        4_000);

        assertTrue(tenMinutePatch.oreMined() <= CampaignEconomyBalanceAudit.UNATTENDED_MINING_MAX_ORE,
                "single unattended patch mined " + tenMinutePatch.oreMined());
    }

    @Test
    void salvageRegistersAsSituationalRecoveryAlternative() {
        CampaignEconomyBalanceAudit.SalvageMiningComparison comparison =
                CampaignEconomyBalanceAudit.salvageMiningComparison();

        assertTrue(comparison.oneMinuteStarterMiningOre() > 0);
        assertTrue(comparison.carefulSalvageEquivalentOre() > 0);
        assertTrue(comparison.salvageIsSituationallyCompetitive());
    }

    @Test
    void oneAndThreeEconomyLoopsMeasureFleetPowerWithoutRunawayGrowth() {
        CampaignEconomyBalanceAudit.EconomyLoopMeasurement one =
                CampaignEconomyBalanceAudit.oneLoopMeasurement(64080L, 1);
        CampaignEconomyBalanceAudit.EconomyLoopMeasurement three =
                CampaignEconomyBalanceAudit.oneLoopMeasurement(64081L, 3);

        assertTrue(one.endFleetPower() >= one.startFleetPower());
        assertTrue(three.endFleetPower() >= three.startFleetPower());
        assertTrue(one.endFleetCount() - one.startFleetCount() <= 1,
                "one economy loop should not chain-buy multiple hulls");
        assertTrue(three.endFleetCount() - three.startFleetCount() <= 3,
                "three economy loops should remain bounded pending owner feel tuning");
    }

    @Test
    void starterMiningSupportsFirstMajorUpgradeTargetWithoutRunawayUnattendedIncome() {
        CampaignEconomyBalanceAudit.MiningRateMeasurement starter =
                CampaignEconomyBalanceAudit.measureMining("starter-target-window",
                        ShipRole.MINER,
                        60,
                        1.0,
                        1.0,
                        2_000);
        int targetOre = CampaignEconomyBalanceAudit.STARTER_MAJOR_UPGRADE_TARGET_ORE;
        int targetMinutes = CampaignEconomyBalanceAudit.STARTER_MAJOR_UPGRADE_TARGET_MINUTES;
        int projectedOreAtTarget = starter.orePerMinute() * targetMinutes;

        assertTrue(projectedOreAtTarget >= targetOre,
                "starter mining should be able to reach the first major upgrade target near the stated target window");
        assertTrue(projectedOreAtTarget <= targetOre * 2,
                "starter mining should not trivialize the first major upgrade before owner feel tuning");
    }
}
