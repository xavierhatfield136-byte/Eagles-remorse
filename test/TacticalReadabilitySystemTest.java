import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalReadabilitySystemTest {

    @Test
    void subsystemFailuresCreateCalloutsFiltersTimelineAndSlowTime() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 44L, false));
        RoomDamageResult failure = new RoomDamageResult(
                "REACTOR",
                40.0,
                0.0,
                1,
                List.of("ENGINES:offline", "hazard:fire_ignited"),
                -8,
                40.0);
        ctx.damageEvents.add(new DamageEvent(
                "test",
                7,
                1200.0,
                1300.0,
                0.0,
                0.0,
                "explosive",
                12.0,
                1500L,
                failure));
        ctx.fleetCommLog.add(new GameContext.FleetCommMessage(Faction.ALLY, "CAPTAIN", "Attack order acknowledged.", 3.0));
        ctx.fleetCommLog.add(new GameContext.FleetCommMessage(Faction.ALLY, "CAPTAIN", "Retreat threshold reached.", 3.0));
        FleetShip wreck = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 2000.0, 2000.0);
        wreck.alive = false;
        wreck.hp = 0;
        ctx.ships.add(wreck);

        EventSystem.update(ctx, GameContext.DT);
        assertTrue(ctx.ui.combatCallouts.stream().anyMatch(callout -> callout.text.contains("ENGINES OFFLINE")));

        assertTrue(TacticalReadabilitySystem.filteredCombatLogLines(ctx, TacticalReadabilitySystem.CombatLogFilter.DAMAGE)
                .stream().anyMatch(line -> line.startsWith("DAMAGE  |  ")));
        assertTrue(TacticalReadabilitySystem.filteredCombatLogLines(ctx, TacticalReadabilitySystem.CombatLogFilter.HAZARDS)
                .stream().anyMatch(line -> line.startsWith("HAZARD  |  ")));
        assertTrue(TacticalReadabilitySystem.filteredCombatLogLines(ctx, TacticalReadabilitySystem.CombatLogFilter.ORDERS)
                .stream().anyMatch(line -> line.startsWith("ORDER  |  ")));
        assertTrue(TacticalReadabilitySystem.filteredCombatLogLines(ctx, TacticalReadabilitySystem.CombatLogFilter.RETREATS)
                .stream().anyMatch(line -> line.startsWith("RETREAT  |  ")));
        assertTrue(TacticalReadabilitySystem.filteredCombatLogLines(ctx, TacticalReadabilitySystem.CombatLogFilter.KILLS)
                .stream().anyMatch(line -> line.startsWith("KILL  |  ")));

        List<String> timeline = TacticalReadabilitySystem.afterBattleTimelineLines(ctx);
        assertFalse(timeline.isEmpty());
        assertTrue(TacticalReadabilitySystem.scrubAfterBattleTimeline(ctx, 1.0).contains("OFFLINE"));

        TacticalReadabilitySystem.setOptionalSlowTimeEnabled(true);
        assertTrue(TacticalReadabilitySystem.optionalSlowTimeEnabled());
        TacticalReadabilitySystem.setOptionalSlowTimeEnabled(false);
        assertFalse(TacticalReadabilitySystem.optionalSlowTimeEnabled());
    }

    @Test
    void tacticalClarityLegendExplainsProjectileImpactTrailBeamAndPointDefenseRoles() {
        List<String> legend = TacticalReadabilitySystem.tacticalClarityLegendLines();
        assertTrue(legend.stream().anyMatch(line -> line.startsWith("Projectile Color  |  ")));
        assertTrue(legend.stream().anyMatch(line -> line.startsWith("Shield Impacts  |  ")));
        assertTrue(legend.stream().anyMatch(line -> line.startsWith("Missile Trails  |  ")));
        assertTrue(legend.stream().anyMatch(line -> line.startsWith("Beam Roles  |  ")));
        assertTrue(legend.stream().anyMatch(line -> line.startsWith("Point Defense  |  ")));

        Missile torpedo = new Missile(0.0, 0.0, 0.0, null, GameContext.DT);
        torpedo.strikeVisual = Missile.StrikeVisual.TORPEDO;
        assertTrue(TacticalReadabilitySystem.projectileClarityLine(torpedo).contains("torpedo"));

        EnergyBolt beamBolt = new EnergyBolt(0.0, 0.0, 0.0, GameContext.DT,
                900.0, 4, 60, 5.0, true, Faction.PLAYER);
        assertTrue(TacticalReadabilitySystem.projectileClarityLine(beamBolt).contains("beam-bolt"));

        CIWSPellet pellet = new CIWSPellet(0.0, 0.0, 0.0, GameContext.DT,
                900.0, 1, 30, 1.2, Faction.ALLY);
        assertTrue(TacticalReadabilitySystem.projectileClarityLine(pellet).contains("CIWS"));
    }

    @Test
    void tacticalCrisisWarningsSurfaceActionableDangerStates() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 45L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.player.hp = Math.max(1, ctx.player.hpMax / 5);
        ctx.player.shield = 0.0;
        ctx.campaign.campaignAmmo = 4;
        ctx.campaign.fleetStrain = 90.0;
        ctx.campaign.retreatCorridorObjectiveActive = true;
        ctx.campaign.retreatCorridorProgressSec = 0.5;
        ctx.campaign.retreatCorridorHoldSec = 4.0;

        FleetShip disabledAlly = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, ctx.player.x + 120.0, ctx.player.y);
        disabledAlly.name = "Disabled Ally";
        disabledAlly.applyTemporaryDisable(8.0);
        ctx.ships.add(disabledAlly);

        FleetShip civilian = new FleetShip(ShipRole.TRANSPORT, Faction.ALLY, ctx.player.x + 180.0, ctx.player.y);
        civilian.name = "Civilian Transport";
        ctx.ships.add(civilian);

        FleetShip bomber = new FleetShip(ShipRole.BOMBER, Faction.ENEMY, ctx.player.x + 260.0, ctx.player.y);
        bomber.name = "Enemy Bomber";
        ctx.ships.add(bomber);

        Missile atomic = new Missile(ctx.player.x + 700.0, ctx.player.y, 0.0, ctx.player, GameContext.DT, Faction.ENEMY);
        atomic.strikeVisual = Missile.StrikeVisual.ATOMIC;
        ctx.projectiles.add(atomic);

        String warnings = String.join("\n", TacticalReadabilitySystem.tacticalCrisisWarningLines(ctx));

        assertTrue(warnings.contains("flagship in danger"));
        assertTrue(warnings.contains("mothership hull critical"));
        assertTrue(warnings.contains("point defense ammo collapse"));
        assertTrue(warnings.contains("repair capacity exhausted"));
        assertTrue(warnings.contains("nuclear strike incoming"));
        assertTrue(warnings.contains("enemy strike craft inbound"));
        assertTrue(warnings.contains("civilian ships under immediate threat"));
        assertTrue(warnings.contains("ship disabled"));
        assertTrue(warnings.contains("retreat corridor closing"));
        assertTrue(warnings.contains("icon shield-alert"));
        assertTrue(warnings.contains("icon incoming-strike"));
        assertTrue(warnings.contains("icon ammo-empty"));
        assertTrue(warnings.contains("icon exit-closing"));
        assertTrue(warnings.contains("icon distress-beacon"));
        assertTrue(warnings.contains("color red"));
        assertTrue(warnings.contains("color amber"));
    }
}
