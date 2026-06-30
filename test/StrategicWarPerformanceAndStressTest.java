import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicWarPerformanceAndStressTest {
    @Test
    void worstCaseMultiFrontAllFactionPlanningStaysInsideBudget() {
        StrategicCampaignExpansionSystem.State state = largeWar(144);
        long start = System.nanoTime();
        int decisions = 0;
        for (int pass = 0; pass < 100; pass++) {
            for (String faction : List.of("PLAYER", "ENEMY", "TEAM_C", "BRIGHT_YELLOW", "DARK_YELLOW")) {
                StrategicCampaignExpansionSystem.recalculateSupply(state, faction);
                for (StrategicCampaignExpansionSystem.Territory territory : state.territories) {
                    if (faction.equals(territory.controller)) {
                        StrategicCampaignExpansionSystem.scoreTerritoryDecision(state, faction, territory.id.value());
                        decisions++;
                    }
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(decisions > 10_000);
        assertTrue(elapsedMs < 5000, "multi-front planning took " + elapsedMs + "ms");
        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(state).isEmpty());
    }

    @Test
    void rapidOwnershipChangesAndHundredsOfConcurrentOperationsRemainRecoverable() {
        StrategicCampaignExpansionSystem.State state = largeWar(80);
        StrategicCampaignExpansionSystem.Territory a = state.territories.get(0);
        StrategicCampaignExpansionSystem.Territory b = state.territories.get(1);
        b.controller = "ENEMY";
        for (int i = 0; i < 300; i++) {
            StrategicCampaignExpansionSystem.StrategicOperation raid =
                    StrategicCampaignExpansionSystem.startOperation(state,
                            StrategicCampaignExpansionSystem.OperationType.RAID,
                            a.controller, a.id.value(), b.id.value());
            if (raid != null) StrategicCampaignExpansionSystem.configureRaidTarget(raid,
                    StrategicCampaignExpansionSystem.RaidTarget.values()[i
                            % StrategicCampaignExpansionSystem.RaidTarget.values().length]);
        }
        assertEquals(128, state.operations.size(), "stress should reach, but never exceed, the supported active-operation cap");
        String[] factions = {"PLAYER", "ENEMY", "TEAM_C", "BRIGHT_YELLOW", "DARK_YELLOW"};
        for (int i = 0; i < 2_000; i++) {
            StrategicCampaignExpansionSystem.Territory territory = state.territories.get(i % state.territories.size());
            territory.owner = factions[i % factions.length];
            territory.controller = territory.owner;
            StrategicCampaignExpansionSystem.recalculateSupply(state, territory.controller);
        }
        StrategicCampaignExpansionSystem.State restored = StrategicCampaignExpansionSystem.restore(
                StrategicCampaignExpansionSystem.serialize(state), 55L);
        assertEquals(state.territories.size(), restored.territories.size());
        assertEquals(state.operations.size(), restored.operations.size());
        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(restored).isEmpty());
    }

    @Test
    void liveOverlayQueriesRemainResponsiveWithCachedProjection() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9971L, false));
        SpawnSystem.initWorld(ctx);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        int builds = ctx.campaign.strategicProjectionBuildCount;
        long start = System.nanoTime();
        for (int i = 0; i < 5_000; i++) {
            CampaignSystem.campaignTerritoryOverlayViews(ctx);
            CampaignSystem.campaignSelectedTerritoryEdges(ctx);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(builds, ctx.campaign.strategicProjectionBuildCount);
        assertTrue(elapsedMs < 5000, "cached overlays took " + elapsedMs + "ms");
    }

    private static StrategicCampaignExpansionSystem.State largeWar(int count) {
        StrategicCampaignExpansionSystem.State state = new StrategicCampaignExpansionSystem.State();
        String[] factions = {"PLAYER", "ENEMY", "TEAM_C", "BRIGHT_YELLOW", "DARK_YELLOW"};
        for (int i = 0; i < count; i++) {
            String faction = factions[i % factions.length];
            StrategicCampaignExpansionSystem.Territory territory =
                    new StrategicCampaignExpansionSystem.Territory("stress-" + i, "Stress " + i, faction, faction);
            territory.centerX = (i % 12) * 200.0;
            territory.centerY = (i / 12) * 160.0;
            territory.supplySource = i % 5 == 0;
            territory.supportsInvasionStaging = true;
            territory.friendlyFleetStrength = 100;
            territory.fleetReadiness = 90;
            territory.ammunition = 100;
            territory.locationIds.add(territory.id.value());
            state.territories.add(territory);
            state.systems.add(new StrategicCampaignExpansionSystem.StarSystem(
                    territory.id.value(), territory.name,
                    StrategicCampaignExpansionSystem.RegionRule.CONTESTED_BELT, true));
            if (i > 0) state.lanes.add(new StrategicCampaignExpansionSystem.TravelLane(
                    "stress-" + (i - 1), territory.id.value(),
                    StrategicCampaignExpansionSystem.LaneType.TRAVEL_LANE, true));
        }
        return state;
    }
}
