import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommSystemTest {

    @Test
    void hailUsesCursorContactForFriendlyShipAndShowsCaption() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip trader = new FleetShip(ShipRole.TRANSPORT, Faction.TEAM_C, 1200.0, 1000.0);
        trader.name = "Broker Spine";
        trader.alive = true;
        trader.dying = false;
        trader.hpMax = Math.max(1, trader.hpMax);
        trader.hp = trader.hpMax;
        ctx.ships.add(trader);
        ctx.cursorWorldX = trader.x;
        ctx.cursorWorldY = trader.y;

        CommSystem.tryHailCurrentContact(ctx);

        assertFalse(ctx.fleetCommLog.isEmpty(), "hailing should add a visible comm message");
        assertTrue(ctx.fleetCommLog.get(ctx.fleetCommLog.size() - 1).external, "non-fleet hail traffic should be marked visible to the player");
        assertTrue(ctx.ui.voiceCaption.contains("Broker Spine"), "hails should surface through the caption layer");
    }

    @Test
    void hailFallsBackToLockedEnemyWhenCursorIsEmpty() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 5678L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemy = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 1300.0, 1000.0);
        enemy.name = "Red Scout";
        ctx.ships.add(enemy);
        ctx.lockedTarget = enemy;
        ctx.cursorWorldX = 3000.0;
        ctx.cursorWorldY = 3000.0;

        CommSystem.tryHailCurrentContact(ctx);

        String text = ctx.fleetCommLog.get(ctx.fleetCommLog.size() - 1).text;
        assertTrue(text.toLowerCase().contains("scout") || text.toLowerCase().contains("red"),
                "enemy hails should produce hostile/scout-flavored responses");
    }

    @Test
    void cycleIntentWrapsForward() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9001L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;

        assertEquals(UiState.CommIntent.IDENTIFY, ctx.ui.commIntent);
        CommSystem.cycleIntent(ctx, +1);
        assertEquals(UiState.CommIntent.STATE_INTENT, ctx.ui.commIntent);

        for (int i = 0; i < 5; i++) {
            CommSystem.cycleIntent(ctx, +1);
        }
        assertEquals(UiState.CommIntent.IDENTIFY, ctx.ui.commIntent);
    }

    @Test
    void tradeIntentProducesTradeFlavoredReplyForBrokerContact() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 2468L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip trader = new FleetShip(ShipRole.TRANSPORT, Faction.TEAM_C, 1200.0, 1000.0);
        trader.name = "Broker Spine";
        trader.alive = true;
        trader.dying = false;
        trader.hpMax = Math.max(1, trader.hpMax);
        trader.hp = trader.hpMax;
        ctx.ships.add(trader);
        ctx.cursorWorldX = trader.x;
        ctx.cursorWorldY = trader.y;
        ctx.ui.commIntent = UiState.CommIntent.REQUEST_TRADE;

        CommSystem.tryHailCurrentContact(ctx);

        String text = ctx.fleetCommLog.get(ctx.fleetCommLog.size() - 1).text.toLowerCase();
        assertTrue(text.contains("trade")
                        || text.contains("terms")
                        || text.contains("business")
                        || text.contains("intel")
                        || text.contains("vector")
                        || text.contains("route"),
                "trade requests should resolve into either cargo barter or intel-sale channel language");
    }

    @Test
    void surrenderDemandGetsDistinctReplyFromDamagedEnemy() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1357L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemy = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 1300.0, 1000.0);
        enemy.name = "Red Scout";
        enemy.hp = Math.max(1, (int) Math.round(enemy.hpMax * 0.10));
        ctx.ships.add(enemy);
        ctx.lockedTarget = enemy;
        ctx.cursorWorldX = 3000.0;
        ctx.cursorWorldY = 3000.0;
        ctx.ui.commIntent = UiState.CommIntent.DEMAND_SURRENDER;

        CommSystem.tryHailCurrentContact(ctx);

        String text = ctx.fleetCommLog.get(ctx.fleetCommLog.size() - 1).text.toLowerCase();
        assertTrue(text.contains("dead") || text.contains("wreck") || text.contains("failing") || text.contains("negative"),
                "surrender demands should get a distinct damaged-enemy response");
    }

    @Test
    void supportIntentAppliesTemporaryEscortOrderToFriendlyCombatant() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 4242L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1200.0, 1000.0);
        escort.name = "Blue Escort";
        ctx.ships.add(escort);
        ctx.cursorWorldX = escort.x;
        ctx.cursorWorldY = escort.y;
        ctx.ui.commIntent = UiState.CommIntent.REQUEST_SUPPORT;

        CommSystem.tryHailCurrentContact(ctx);

        assertEquals(GameContext.FleetCommand.ESCORT, ctx.command.shipFleetCommandOverrides.get(escort.id));
        assertTrue(ctx.command.shipFleetCommandOverrideTimers.getOrDefault(escort.id, 0.0) > 0.0,
                "support hails should be temporary, not permanent");
    }

    @Test
    void tradeIntentSellsOreAndStartsCooldownForTrader() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 24680L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.cargo = 60;
        ctx.ships.add(ctx.player);

        FleetShip trader = new FleetShip(ShipRole.TRANSPORT, Faction.TEAM_C, 1200.0, 1000.0);
        trader.name = "Broker Spine";
        ctx.ships.add(trader);
        ctx.cursorWorldX = trader.x;
        ctx.cursorWorldY = trader.y;
        ctx.ui.commIntent = UiState.CommIntent.REQUEST_TRADE;
        int creditsBefore = ctx.credits;

        CommSystem.tryHailCurrentContact(ctx);

        assertTrue(ctx.ui.commTradeMenu.active, "trade hail should open a selectable trade menu first");
        assertEquals(trader.id, ctx.ui.commTradeMenu.targetId);
        assertTrue(ctx.ui.commTradeMenu.options.stream().anyMatch(option -> "SELL_ORE".equals(option.id)));
        assertEquals(60, ctx.player.cargo, "opening the trade menu should not auto-transfer cargo");

        assertTrue(CommSystem.chooseTradeMenuOption(ctx, 0));

        assertFalse(ctx.ui.commTradeMenu.active);
        assertEquals(0, ctx.player.cargo, "trade should allow selling the entire ore stockpile");
        assertTrue(ctx.credits > creditsBefore, "trade should pay the player");
        assertTrue(ctx.command.shipCommActionCooldowns.getOrDefault(trader.id, 0.0) > 0.0,
                "trade should create an anti-spam cooldown");
    }

    @Test
    void servicesTabPurchasesOreThroughComms() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 24681L, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.campaign.enabled = true;
        CampaignSystem.grantCampaignOre(ctx, 25);
        ctx.credits = 5000;

        FleetShip trader = new FleetShip(ShipRole.TRANSPORT, Faction.TEAM_C, 1200.0, 1000.0);
        trader.name = "Broker Spine";
        trader.alive = true;
        trader.dying = false;
        trader.hpMax = Math.max(1, trader.hpMax);
        trader.hp = trader.hpMax;
        ctx.ships.add(trader);
        ctx.cursorWorldX = trader.x;
        ctx.cursorWorldY = trader.y;
        ctx.ui.commIntent = UiState.CommIntent.REQUEST_TRADE;

        CommSystem.tryHailCurrentContact(ctx);

        assertTrue(ctx.ui.commTradeMenu.active, "trade hail should open services ledger");
        assertEquals(trader.id, ctx.ui.commTradeMenu.targetId);
        if (!ctx.ships.contains(trader)) ctx.ships.add(trader);
        int oreOptionIndex = -1;
        for (int i = 0; i < ctx.ui.commTradeMenu.options.size(); i++) {
            UiState.CommTradeOption option = ctx.ui.commTradeMenu.options.get(i);
            if (option != null && "BUY_SERVICE_ORE".equals(option.id)) {
                oreOptionIndex = i;
                break;
            }
        }
        assertTrue(oreOptionIndex >= 0, "services tab should offer purchasable ore services");
        assertTrue(ctx.ui.commTradeMenu.options.get(oreOptionIndex).enabled,
                ctx.ui.commTradeMenu.options.get(oreOptionIndex).detail);
        int creditsBefore = ctx.credits;
        int oreBefore = CampaignSystem.currentCampaignOre(ctx);
        trader.alive = true;
        trader.dying = false;
        trader.hpMax = Math.max(1, trader.hpMax);
        trader.hp = trader.hpMax;

        assertTrue(CommSystem.chooseTradeMenuOption(ctx, oreOptionIndex));

        assertFalse(ctx.ui.commTradeMenu.active);
        int oreAfter = CampaignSystem.currentCampaignOre(ctx);
        assertTrue(oreAfter > oreBefore, "ore service should add campaign ore; before=" + oreBefore
                + " after=" + oreAfter + " result=" + ctx.ui.commResultTitle + " " + ctx.ui.commResultBody);
        assertTrue(ctx.credits < creditsBefore, "service purchase should spend credits");
    }

    @Test
    void warnOffIntentPushesNeutralOutOfLane() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 8888L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip civilian = new FleetShip(ShipRole.HAULER, Faction.TEAM_C, 1220.0, 1000.0);
        civilian.name = "Civilian Freighter";
        civilian.aiCommittedTargetId = 99;
        civilian.aiTargetCommitTimer = 5.0;
        ctx.ships.add(civilian);
        ctx.cursorWorldX = civilian.x;
        ctx.cursorWorldY = civilian.y;
        ctx.ui.commIntent = UiState.CommIntent.WARN_OFF;

        CommSystem.tryHailCurrentContact(ctx);

        assertEquals(GameContext.FleetCommand.RETREAT, ctx.command.shipFleetCommandOverrides.get(civilian.id));
        assertEquals(-1, civilian.aiCommittedTargetId);
        assertEquals(0.0, civilian.aiTargetCommitTimer);
        assertNotEquals(Ship.CrewOrder.BALANCED, civilian.crewOrder,
                "warn-off should push the contact into a defensive withdrawal posture");
    }

    @Test
    void stateIntentSharesIntelAndLocksReportedHostile() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9191L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1200.0, 1000.0);
        ally.name = "Blue Scout";
        ctx.ships.add(ally);

        FleetShip enemy = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 1400.0, 1000.0);
        enemy.name = "Red Contact";
        ctx.ships.add(enemy);

        ctx.cursorWorldX = ally.x;
        ctx.cursorWorldY = ally.y;
        ctx.ui.commIntent = UiState.CommIntent.STATE_INTENT;

        CommSystem.tryHailCurrentContact(ctx);

        assertEquals(enemy, ctx.lockedTarget, "state intent should surface a concrete hostile contact when allies have one");
        assertTrue(ctx.fleetCommLog.get(ctx.fleetCommLog.size() - 1).text.contains("Red Contact"),
                "intel-sharing replies should mention the reported hostile");
    }

    @Test
    void surrenderDemandCanForceScoutWithdrawal() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 2222L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemy = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 1260.0, 1000.0);
        enemy.name = "Red Scout";
        enemy.hp = Math.max(1, (int) Math.round(enemy.hpMax * 0.24));
        enemy.aiCommittedTargetId = ctx.player.id;
        enemy.aiTargetCommitTimer = 6.0;
        ctx.ships.add(enemy);
        ctx.cursorWorldX = enemy.x;
        ctx.cursorWorldY = enemy.y;
        ctx.ui.commIntent = UiState.CommIntent.DEMAND_SURRENDER;

        CommSystem.tryHailCurrentContact(ctx);

        assertEquals(GameContext.FleetCommand.RETREAT, ctx.command.shipFleetCommandOverrides.get(enemy.id));
        assertEquals(-1, enemy.aiCommittedTargetId);
        assertEquals(0.0, enemy.aiTargetCommitTimer);
    }

    @Test
    void surrenderDemandCanFlipBrokenSupportHull() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 3333L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        FleetShip enemy = new FleetShip(ShipRole.TRANSPORT, Faction.ENEMY, 1240.0, 1000.0);
        enemy.name = "Red Tender";
        enemy.hp = Math.max(1, (int) Math.round(enemy.hpMax * 0.12));
        ctx.ships.add(enemy);
        ctx.cursorWorldX = enemy.x;
        ctx.cursorWorldY = enemy.y;
        ctx.ui.commIntent = UiState.CommIntent.DEMAND_SURRENDER;

        CommSystem.tryHailCurrentContact(ctx);

        assertEquals(Faction.ALLY, enemy.faction, "broken support hulls should be able to defect");
        assertTrue(enemy.name.startsWith("Defector "), "converted ships should be clearly renamed");
        assertEquals(ctx.player.id, enemy.escortAnchorId, "converted ships should anchor to the player fleet");
    }
}
