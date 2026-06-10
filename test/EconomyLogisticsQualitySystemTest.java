import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyLogisticsQualitySystemTest {

    @Test
    void economyQualityLinesCoverResourcesMarketsContractsMaintenanceAndBlockades() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 88L, false));
        SpawnSystem.initWorld(ctx);
        ctx.campaign.oreLedger.storedOre = 120;
        ctx.campaign.campaignSalvage = 40;
        ctx.campaign.campaignFuel = 70;
        ctx.campaign.campaignSupplies = 55;
        ctx.campaign.campaignAmmo = 80;
        ctx.campaign.greenContractFavor = 2;
        ctx.campaign.yellowLiberationFavor = 1;

        List<String> lines = EconomyLogisticsQualitySystem.allEconomyQualityLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Split Resources  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Cargo Allocation  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Market Screen  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Contract Board  |  ")
                && line.contains("escort") && line.contains("rescue") && line.contains("salvage")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Contract Stakes  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Rival Bidders  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Hull Insurance  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Maintenance Debt  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Spare Parts  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Field Repairs  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Shipyard Region  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Construction Queue  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Refit Template  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Black Market  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Convoy Dependency  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Blockade Starvation  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Rare Materials  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Salvage Processing  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("AI Resource Reserve  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Trade Substitute  |  ")));
    }
}
