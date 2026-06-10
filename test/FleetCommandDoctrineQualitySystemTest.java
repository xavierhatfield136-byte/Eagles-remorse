import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetCommandDoctrineQualitySystemTest {

    @Test
    void fleetCommandDoctrineQualityLinesCoverGroupTemplatesPoliciesAndReview() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 77L, false));
        SpawnSystem.initWorld(ctx);
        ctx.ships.add(new FleetShip(ShipRole.CARRIER, Faction.ALLY, 1200.0, 1200.0));
        ctx.ships.add(new FleetShip(ShipRole.TRANSPORT, Faction.ALLY, 1260.0, 1200.0));
        ctx.ships.add(new FleetShip(ShipRole.MISSILE_BOAT, Faction.ALLY, 1320.0, 1200.0));
        ctx.campaign.fleetDoctrineExpansion.fleet.bandwidthUsed = 3;
        ctx.campaign.fleetDoctrineExpansion.fleet.panicPercent = 28;
        ctx.campaign.fleetDoctrineExpansion.fleet.standingOrders.afterActionNotes.add("Screen order arrived late.");

        List<String> all = Stream.of(
                        FleetCommandDoctrineQualitySystem.fleetGroupManagementLines(ctx),
                        FleetCommandDoctrineQualitySystem.savedDoctrineTemplateLines(ctx),
                        FleetCommandDoctrineQualitySystem.doctrineRecommendationLines(ctx),
                        FleetCommandDoctrineQualitySystem.doctrineImpactLines(ctx),
                        FleetCommandDoctrineQualitySystem.commandBandwidthForecastLines(ctx),
                        FleetCommandDoctrineQualitySystem.commandConsequenceLines(ctx),
                        FleetCommandDoctrineQualitySystem.reserveAndSpecialOrderLines(ctx),
                        FleetCommandDoctrineQualitySystem.postBattleDoctrineReviewLines(ctx))
                .flatMap(List::stream)
                .toList();

        assertTrue(all.stream().anyMatch(line -> line.contains("create, rename, assign")));
        assertTrue(all.stream().anyMatch(line -> line.contains("drag ship cards")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Saved Doctrine  |  CONVOY ESCORT")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Doctrine Recommendation  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Doctrine Impact  |  ")
                && line.contains("ammo") && line.contains("retreat") && line.contains("repair burden")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Captain Objection  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Command Bandwidth Forecast  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.contains("relay loss")));
        assertTrue(all.stream().anyMatch(line -> line.contains("flagship loss")));
        assertTrue(all.stream().anyMatch(line -> line.contains("panic")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Field Promotion  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Reserve Rotation  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.contains("screen artillery")));
        assertTrue(all.stream().anyMatch(line -> line.contains("escort carrier")));
        assertTrue(all.stream().anyMatch(line -> line.contains("guard salvage ship")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Rules Of Engagement  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Retreat / Rescue Policy  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Post-Battle Doctrine Review  |  ")));
        assertTrue(all.stream().anyMatch(line -> line.startsWith("Command Training  |  ")));
    }
}
