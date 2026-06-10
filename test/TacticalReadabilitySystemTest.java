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
}
