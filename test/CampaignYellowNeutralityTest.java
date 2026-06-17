import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignYellowNeutralityTest {

    @Test
    void yellowIsFriendlyInCampaignZoneUntilRedContextAppears() throws Exception {
        try {
            GameContext ctx = campaignZone();
            FleetShip yellow = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_D, 1220.0, 1000.0);
            ctx.ships.add(yellow);

            refreshAlliances(ctx);

            assertTrue(Faction.ALLY.isFriendlyTo(Faction.TEAM_D),
                    "Yellow-only campaign pockets should not make Yellow attack the player");

            ctx.ships.add(new FleetShip(ShipRole.PATROL, Faction.ENEMY, 1500.0, 1000.0));
            refreshAlliances(ctx);

            assertFalse(Faction.ALLY.isFriendlyTo(Faction.TEAM_D),
                    "active Red context should keep unconverted Yellow independent");
        } finally {
            Faction.clearCampaignAlliances();
        }
    }

    @Test
    void stateIntentConvertsYellowOutOfFriendlyTargeting() {
        try {
            GameContext ctx = campaignZone();
            FleetShip yellow = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_D, 1220.0, 1000.0);
            FleetShip red = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 1500.0, 1000.0);
            ctx.ships.add(yellow);
            ctx.ships.add(red);
            ctx.entityQuery.rebuild(ctx);

            CampaignSystem.noteYellowStateIntentNeutrality(ctx, yellow);

            assertTrue(Faction.ALLY.isFriendlyTo(Faction.TEAM_D),
                    "State Intent should convert Yellow contacts to neutral/friendly campaign status");
            assertSame(red, TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, yellow.x, yellow.y, 900.0),
                    "friendly forces should target Red instead of converted Yellow");
        } finally {
            Faction.clearCampaignAlliances();
        }
    }

    private static GameContext campaignZone() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        st.strategicOvermapMode = false;
        st.campaignBlueGreenAlliance = true;
        st.campaignBlueYellowAlliance = false;
        ctx.campaign = st;
        Faction.configureCampaignAlliances(true, false, true);
        return ctx;
    }

    private static void refreshAlliances(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "refreshCampaignAlliances", GameContext.class, CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign);
    }
}
