import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleResultAnalysisServiceTest {
    @Test
    void cleanVictoryProducesKeyBattleFactorAndResourceSummary() {
        BattleResult result = new BattleResult(
                "battle-test",
                "test",
                1000L,
                61_000L,
                60.0,
                BattleResult.BattleSource.CUSTOM_BATTLE,
                "Custom Battles",
                "Fleet Balance Test",
                "Open Space",
                Faction.ALLY,
                Faction.ALLY.teamId(),
                42L,
                BattleResult.TacticalResult.VICTORY,
                BattleResult.MissionResult.SUCCESS,
                "VICTORY",
                1000,
                1250,
                20,
                40,
                2,
                0,
                List.of(
                        ship(1, "Blue Frigate", Faction.ALLY, ShipRole.FRIGATE, 100, 25, 85, 20, false),
                        ship(2, "Red Frigate", Faction.ENEMY, ShipRole.FRIGATE, 100, 0, 0, 0, true)));

        AfterActionReport report = BattleAnalysisService.analyze(result);

        assertEquals("Custom Battle After-Action Report", report.title);
        assertTrue(report.resultLine.contains("Victory"));
        assertTrue(report.resourcesSummary.contains("credits 1250 (+250)"));
        assertTrue(report.resourcesSummary.contains("repair est"));
        assertTrue(report.compactKeyFactors().contains("Clean victory"));
        assertFalse(report.analysisInsights.isEmpty());
        assertEquals("aar.clean_victory", report.analysisInsights.get(0).ruleId);
        assertTrue(report.analysisInsights.get(0).confidence > 0.9);
    }

    @Test
    void withdrawalProducesPreservationInsightAndDebuggablePersistentRecord() {
        BattleResult result = new BattleResult(
                "battle-withdraw",
                "test",
                1000L,
                121_000L,
                120.0,
                BattleResult.BattleSource.ACADEMY,
                "Tutorial",
                "Withdrawal Drill",
                "Training Range",
                Faction.ALLY,
                Faction.ALLY.teamId(),
                7L,
                BattleResult.TacticalResult.WITHDRAWAL,
                BattleResult.MissionResult.PARTIAL_SUCCESS,
                "WITHDRAWAL COMPLETE objective secure",
                1000,
                1000,
                20,
                20,
                0,
                0,
                List.of(
                        new BattleResult.ShipSnapshot(11, "campaign-slot-3", 3, "Training Screen",
                                Faction.ALLY, ShipRole.CIWS_CORVETTE, false, true,
                                90, 15, 62, 5, 90, 15, false, true, 2, 56),
                        ship(12, "Red Picket", Faction.ENEMY, ShipRole.PICKET, 70, 0, 70, 0, false)));

        AfterActionReport report = BattleAnalysisService.analyze(result);
        PersistentBattleRecord record = PersistentBattleRecord.from(result, report);

        assertTrue(report.compactKeyFactors().contains("Withdrawal preserved"));
        assertTrue(report.nextAction.toLowerCase().contains("lower-threat")
                || report.nextAction.toLowerCase().contains("academy"));
        assertTrue(record.shipHistorySummary.contains("campaign-slot-3"));
        assertTrue(record.toDebugJson().contains("\"battleId\":\"battle-withdraw\""));
        assertTrue(record.toDebugText().contains("Ship History"));
    }

    @Test
    void recorderCapturesGameOverBattleOnce() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 99L, false));
        Player player = new Player(500.0, 500.0);
        player.faction = Faction.ALLY;
        player.name = "Player Frigate";
        player.role = ShipRole.FRIGATE;
        player.hpMax = 100;
        player.hp = 100;
        ctx.player = player;
        ctx.ships.add(player);

        Ship enemy = new EnemyShip(900.0, 500.0);
        enemy.name = "Red Frigate";
        enemy.faction = Faction.ENEMY;
        enemy.role = ShipRole.FRIGATE;
        enemy.hpMax = 100;
        enemy.hp = 100;
        ctx.ships.add(enemy);

        ctx.battleResultRecorder.update(ctx);
        assertTrue(ctx.battleResultRecorder.active());

        enemy.hp = 0;
        enemy.alive = false;
        ctx.battleElapsed = 3.0;
        ctx.gameOver = true;
        ctx.gameOverText = "VICTORY";
        ctx.battleResultRecorder.update(ctx);

        BattleResult result = ctx.battleResultRecorder.latestResult();
        assertNotNull(result);
        assertEquals(BattleResult.TacticalResult.VICTORY, result.tacticalResult);
        assertEquals(1, result.hostileShipsDestroyed());
        assertEquals(BattleResult.BattleSource.CUSTOM_BATTLE, result.source);
        assertTrue(result.saveSchemaVersion >= 0);
        assertEquals("tactical-ship-" + player.id, result.ships.get(0).stableShipId);
        assertNotNull(ctx.battleResultRecorder.latestReport());

        ctx.battleResultRecorder.update(ctx);
        assertEquals(result, ctx.battleResultRecorder.latestResult());
    }

    @Test
    void campaignVictoryReportUsesNormalizedBattleResultLines() {
        BattleResult result = new BattleResult(
                BattleResult.BATTLE_RESULT_SCHEMA_VERSION,
                "campaign-victory",
                "test",
                1,
                1000L,
                91_000L,
                90.0,
                BattleResult.BattleSource.CAMPAIGN,
                "Open World Campaign",
                "relay-01",
                "Break Red Relay Screen",
                3,
                "Forward Relay",
                "Luna Perimeter",
                "Normal",
                "lethality x1.00, pressure x1.00, attrition x1.00",
                Faction.ALLY,
                Faction.ALLY.teamId(),
                123L,
                BattleResult.TacticalResult.VICTORY,
                BattleResult.MissionResult.SUCCESS,
                "VICTORY objective secured",
                1000,
                1420,
                30,
                65,
                35,
                420,
                34,
                0,
                true,
                1,
                0,
                List.of(
                        ship(1, "Blue Screen", Faction.ALLY, ShipRole.FRIGATE, 100, 25, 83, 18, false),
                        ship(2, "Red Relay Guard", Faction.ENEMY, ShipRole.FRIGATE, 100, 0, 0, 0, true)));

        AfterActionReport report = BattleAnalysisService.analyze(result);

        assertEquals("Campaign After-Action Report", report.title);
        assertTrue(report.resultLine.contains("Victory"));
        assertTrue(report.resultLine.contains("Break Red Relay Screen"));
        assertTrue(report.resourcesSummary.contains("reward 420"));
        assertTrue(report.strategicEffect.contains("Campaign state changed"));
        assertTrue(report.compactKeyFactors().contains("Clean victory"));
    }

    @Test
    void campaignDefeatReportWarnsAboutLostFieldAndFollowOnPressure() {
        BattleResult result = new BattleResult(
                BattleResult.BATTLE_RESULT_SCHEMA_VERSION,
                "campaign-defeat",
                "test",
                1,
                1000L,
                181_000L,
                180.0,
                BattleResult.BattleSource.CAMPAIGN,
                "Open World Campaign",
                "convoy-defense",
                "Hold Convoy Lane",
                2,
                "Convoy Lane",
                "Outer Supply Corridor",
                "Normal",
                "",
                Faction.ALLY,
                Faction.ALLY.teamId(),
                456L,
                BattleResult.TacticalResult.DEFEAT,
                BattleResult.MissionResult.FAILURE,
                "DEFEAT: CONVOYS BELOW SAFE COUNT",
                900,
                900,
                10,
                10,
                0,
                0,
                260,
                800,
                true,
                0,
                2,
                List.of(
                        ship(1, "Blue Frigate", Faction.ALLY, ShipRole.FRIGATE, 100, 20, 0, 0, true),
                        ship(2, "Red Hunter", Faction.ENEMY, ShipRole.FRIGATE, 100, 0, 74, 0, false),
                        ship(3, "Red Picket", Faction.ENEMY, ShipRole.PICKET, 70, 0, 62, 0, false)));

        AfterActionReport report = BattleAnalysisService.analyze(result);

        assertEquals("Campaign After-Action Report", report.title);
        assertTrue(report.resultLine.contains("Defeat"));
        assertTrue(report.strategicEffect.contains("lost the field"));
        assertTrue(report.compactKeyFactors().contains("Friendly losses"));
        assertTrue(report.compactKeyFactors().contains("Hostile formation largely survived"));
        assertTrue(report.nextAction.toLowerCase().contains("repair")
                || report.nextAction.toLowerCase().contains("lower-threat"));
    }

    @Test
    void campaignWithdrawalReportDistinguishesPartialSuccessFromCleanVictory() {
        BattleResult result = new BattleResult(
                BattleResult.BATTLE_RESULT_SCHEMA_VERSION,
                "campaign-withdrawal",
                "test",
                1,
                1000L,
                151_000L,
                150.0,
                BattleResult.BattleSource.CAMPAIGN,
                "Open World Campaign",
                "salvage-site",
                "Extract Salvage Team",
                4,
                "Debris Pocket",
                "Breakchain Debris Run",
                "Normal",
                "",
                Faction.ALLY,
                Faction.ALLY.teamId(),
                789L,
                BattleResult.TacticalResult.WITHDRAWAL,
                BattleResult.MissionResult.PARTIAL_SUCCESS,
                "WITHDRAWAL COMPLETE objective secure",
                1200,
                1280,
                20,
                45,
                25,
                80,
                50,
                0,
                true,
                3,
                0,
                List.of(
                        ship(1, "Blue Escort", Faction.ALLY, ShipRole.CIWS_CORVETTE, 90, 15, 72, 8, false),
                        ship(2, "Red Raider", Faction.ENEMY, ShipRole.FRIGATE, 100, 0, 40, 0, false)));

        AfterActionReport report = BattleAnalysisService.analyze(result);

        assertTrue(report.resultLine.contains("Withdrawal"));
        assertEquals(BattleResult.MissionResult.PARTIAL_SUCCESS, result.missionResult);
        assertTrue(report.compactKeyFactors().contains("Withdrawal preserved"));
        assertFalse(report.compactKeyFactors().contains("Clean victory"));
        assertTrue(report.strategicEffect.contains("preserved combat power"));
    }

    @Test
    void campaignRecorderEmitsDefeatBattleResult() {
        GameContext ctx = tinyCampaignBattleContext(606L);

        ctx.battleResultRecorder.update(ctx);
        ctx.player.hp = 0;
        ctx.player.alive = false;
        ctx.battleElapsed = 6.0;
        ctx.gameOver = true;
        ctx.gameOverText = "DEFEAT: convoy lost";
        ctx.battleResultRecorder.update(ctx);

        BattleResult result = ctx.battleResultRecorder.latestResult();

        assertNotNull(result);
        assertEquals(BattleResult.BattleSource.CAMPAIGN, result.source);
        assertEquals(BattleResult.TacticalResult.DEFEAT, result.tacticalResult);
        assertEquals(BattleResult.MissionResult.FAILURE, result.missionResult);
        assertEquals(1, result.friendlyShipsLost());
        assertNotNull(ctx.battleResultRecorder.latestReport());
    }

    @Test
    void campaignRecorderEmitsWithdrawalBattleResult() {
        GameContext ctx = tinyCampaignBattleContext(707L);

        ctx.battleResultRecorder.update(ctx);
        ctx.battleElapsed = 8.0;
        ctx.gameOver = true;
        ctx.gameOverText = "WITHDRAWAL COMPLETE objective secure";
        ctx.battleResultRecorder.update(ctx);

        BattleResult result = ctx.battleResultRecorder.latestResult();

        assertNotNull(result);
        assertEquals(BattleResult.BattleSource.CAMPAIGN, result.source);
        assertEquals(BattleResult.TacticalResult.WITHDRAWAL, result.tacticalResult);
        assertEquals(BattleResult.MissionResult.PARTIAL_SUCCESS, result.missionResult);
        assertTrue(ctx.battleResultRecorder.latestReport().compactKeyFactors().contains("Withdrawal preserved"));
    }

    @Test
    void academyRecorderEmitsAcademySourceAndTrainingShipDebrief() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.TUTORIAL, 5000, 5000, true, 101L, false));
        Player player = new Player(500.0, 500.0);
        player.faction = Faction.ALLY;
        player.name = "Training Frigate";
        player.role = ShipRole.FRIGATE;
        player.hpMax = 100;
        player.hp = 100;
        ctx.player = player;
        ctx.ships.add(player);

        Ship drone = new EnemyShip(900.0, 500.0);
        drone.name = "Tutorial Drone";
        drone.faction = Faction.ENEMY;
        drone.role = ShipRole.PICKET;
        drone.hpMax = 70;
        drone.hp = 70;
        ctx.ships.add(drone);

        ctx.battleResultRecorder.update(ctx);
        drone.hp = 0;
        drone.alive = false;
        ctx.battleElapsed = 4.0;
        ctx.gameOver = true;
        ctx.gameOverText = "VICTORY: training complete";
        ctx.battleResultRecorder.update(ctx);

        BattleResult result = ctx.battleResultRecorder.latestResult();
        AfterActionReport report = ctx.battleResultRecorder.latestReport();

        assertNotNull(result);
        assertNotNull(report);
        assertEquals(BattleResult.BattleSource.ACADEMY, result.source);
        assertEquals("Academy Debrief", report.title);
        assertTrue(result.ships.stream().anyMatch(ship -> ship.academyTrainingShip));
        assertTrue(report.notableActions.stream().anyMatch(line -> line.contains("Academy ship preserved")));
    }

    @Test
    void campaignAfterActionLinesPreferCurrentNormalizedBattleReport() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 202L, false)
                .withAutoLaunchCampaignStartSite(true));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        assertNotNull(ctx.campaign);

        Ship enemy = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction != null && ship.faction.isHostileTo(ctx.player.faction))
                .findFirst()
                .orElse(null);
        if (enemy == null) {
            enemy = new EnemyShip(ctx.player.x + 420.0, ctx.player.y);
            enemy.name = "Red Report Contact";
            enemy.faction = Faction.ENEMY;
            enemy.role = ShipRole.FRIGATE;
            enemy.hpMax = 100;
            enemy.hp = 100;
            ctx.ships.add(enemy);
        }

        ctx.battleResultRecorder.update(ctx);
        enemy.hp = 0;
        enemy.alive = false;
        ctx.battleElapsed = 5.0;
        ctx.gameOver = true;
        ctx.gameOverText = "VICTORY: objective secured";
        BattleResult result = ctx.battleResultRecorder.finish(ctx, ctx.gameOverText);
        assertNotNull(result);
        assertEquals(BattleResult.BattleSource.CAMPAIGN, result.source);

        ctx.campaign.transitionSummaryTop = "Objective secure. Route pressure reduced.";
        ctx.campaign.transitionSummaryBottom = "Campaign map updated after the battle.";
        List<String> lines = CampaignSystem.campaignAfterActionReportLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.contains("Campaign After-Action Report")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Key Battle Factor: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Objective: ")));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Battle Report: ")),
                "current normalized reports should replace the legacy report header");
    }

    private static GameContext tinyCampaignBattleContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.campaign.objectiveLabel = "Test Campaign Engagement";
        ctx.campaign.transitionLabel = "Test Sector";
        ctx.credits = 900;

        Player player = new Player(500.0, 500.0);
        player.faction = Faction.ALLY;
        player.name = "Blue Campaign Frigate";
        player.role = ShipRole.FRIGATE;
        player.hpMax = 100;
        player.hp = 100;
        ctx.player = player;
        ctx.ships.add(player);

        Ship enemy = new EnemyShip(900.0, 500.0);
        enemy.name = "Red Campaign Frigate";
        enemy.faction = Faction.ENEMY;
        enemy.role = ShipRole.FRIGATE;
        enemy.hpMax = 100;
        enemy.hp = 100;
        ctx.ships.add(enemy);
        return ctx;
    }

    private static BattleResult.ShipSnapshot ship(int id,
                                                  String name,
                                                  Faction faction,
                                                  ShipRole role,
                                                  int startHull,
                                                  double startShield,
                                                  int endHull,
                                                  double endShield,
                                                  boolean destroyed) {
        return new BattleResult.ShipSnapshot(
                id,
                name,
                faction,
                role,
                false,
                startHull,
                startShield,
                endHull,
                endShield,
                Math.max(1, startHull),
                Math.max(startShield, endShield),
                destroyed,
                false);
    }
}
