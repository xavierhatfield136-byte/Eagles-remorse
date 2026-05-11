import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMapDisciplineTest {

    @Test
    void campaignMapDisciplineClearsCombatArtifactsAndTacticalOverlays() {
        GameContext ctx = strategicMapContext();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3000.0, 2500.0));
        ctx.projectiles.add(new Bullet(2600.0, 2500.0, 0.0, GameContext.DT, Faction.ENEMY));
        ctx.asteroids.add(new Asteroid(2800.0, 2400.0, 80.0));
        ctx.salvage.add(new Salvage(2700.0, 2600.0, 25, 5, 20.0));
        ctx.ui.shopOpen = true;
        ctx.ui.baseMenuOpen = true;
        ctx.ui.powerManagementOpen = true;
        ctx.ui.crewStationsOpen = true;
        ctx.ui.flightDeckOpen = true;
        ctx.ui.tacticalViewEnabled = true;
        ctx.firingPrimaryManual = true;
        ctx.firingPrimaryManualLatched = true;
        ctx.firingSecondaryManual = true;
        ctx.firingSecondaryManualLatched = true;
        ctx.firingPrimaryAuto = true;
        ctx.firingSecondaryAuto = true;
        ctx.miningKeyDown = true;
        ctx.command.playerTeleportCharging = true;
        ctx.command.playerTeleportChargeRemaining = 4.5;
        ctx.command.safeMissionExitPending = true;
        ctx.command.safeMissionExitReady = true;
        ctx.lockedTarget = ctx.ships.get(1);

        CampaignSystem.enforceCampaignMapDiscipline(ctx);

        assertEquals(GameState.MAP, ctx.state);
        assertTrue(ctx.ui.mapOpen);
        assertFalse(ctx.ui.shopOpen);
        assertFalse(ctx.ui.baseMenuOpen);
        assertFalse(ctx.ui.powerManagementOpen);
        assertFalse(ctx.ui.crewStationsOpen);
        assertFalse(ctx.ui.flightDeckOpen);
        assertFalse(ctx.ui.tacticalViewEnabled);
        assertEquals(1, ctx.ships.size(), "campaign map should not keep non-player tactical ships alive");
        assertTrue(ctx.projectiles.isEmpty());
        assertTrue(ctx.asteroids.isEmpty());
        assertTrue(ctx.salvage.isEmpty());
        assertFalse(ctx.firingPrimaryManual);
        assertFalse(ctx.firingPrimaryManualLatched);
        assertFalse(ctx.firingSecondaryManual);
        assertFalse(ctx.firingSecondaryManualLatched);
        assertFalse(ctx.firingPrimaryAuto);
        assertFalse(ctx.firingSecondaryAuto);
        assertFalse(ctx.miningKeyDown);
        assertFalse(ctx.command.playerTeleportCharging);
        assertEquals(0.0, ctx.command.playerTeleportChargeRemaining, 1e-9);
        assertFalse(ctx.command.safeMissionExitPending);
        assertFalse(ctx.command.safeMissionExitReady);
        assertNull(ctx.lockedTarget);
    }

    @Test
    void runtimeCampaignMapTickAppliesDisciplineBeforeCampaignUpdate() throws Exception {
        GameContext ctx = strategicMapContext();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3000.0, 2500.0));
        ctx.projectiles.add(new Bullet(2600.0, 2500.0, 0.0, GameContext.DT, Faction.ENEMY));
        ctx.ui.shopOpen = true;
        ctx.firingPrimaryAuto = true;

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        Method tick = GameSimulationRuntime.class.getDeclaredMethod(
                "tick", double.class, InputSnapshot.class, int.class, int.class);
        tick.setAccessible(true);
        tick.invoke(runtime, GameContext.DT, new InputSnapshot(false, false, false, false, false, 0.0, 0.0), 1280, 720);

        assertEquals(1, ctx.ships.size(), "campaign-map runtime tick should strip tactical ships before overmap update");
        assertTrue(ctx.projectiles.isEmpty(), "campaign-map runtime tick should strip projectiles before overmap update");
        assertFalse(ctx.ui.shopOpen, "campaign-map runtime tick should close tactical-only overlays");
        assertFalse(ctx.firingPrimaryAuto, "campaign-map runtime tick should stop combat firing state");
        assertEquals(GameState.MAP, ctx.state);
    }

    private static GameContext strategicMapContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        st.strategicOvermapMode = true;
        ctx.campaign = st;
        ctx.ui.mapOpen = true;
        ctx.state = GameState.MAP;
        return ctx;
    }
}
