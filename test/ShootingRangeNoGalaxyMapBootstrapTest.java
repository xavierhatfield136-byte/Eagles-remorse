import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShootingRangeNoGalaxyMapBootstrapTest {
    @Test
    void shootingRangeTacticalStrikeBootstrapDoesNotEnableGalaxyMapMode() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 42L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.campaign, "shooting range should still have tactical strike state");
        assertTrue(ctx.campaign.enabled);
        assertFalse(CampaignSystem.isStrategicGalaxyMapMode(ctx),
                "shooting range must never auto-enter campaign galaxy map mode");
    }

    @Test
    void shootingRangeMothershipHasInfiniteStrikeStores() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 420L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.campaign);
        assertNotNull(ctx.player);

        ctx.player = new Player(ShipRole.MOTHERSHIP, 1200.0, 1200.0);
        Ship hostile = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ENEMY, 1500.0, 1200.0);
        ctx.ships.add(ctx.player);
        ctx.ships.add(hostile);
        ctx.lockedTarget = hostile;
        ctx.entityQuery.rebuild(ctx);

        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        ctx.campaign.strategicTorpedoCharges = 0;
        ctx.campaign.strategicAtomicCharges = 0;
        ctx.campaign.strategicSortiesLaunched = 999;
        ctx.campaign.campaignAmmo = 0;
        ctx.campaign.campaignFuel = 0;
        ctx.campaign.campaignSupplies = 0;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.tacticalMapVisibleActions(ctx);
        CampaignSystem.CampaignAction torpedo = actions.stream().filter(a -> a != null && "TACTICAL_TORPEDO_STRIKE".equals(a.id)).findFirst().orElse(null);
        CampaignSystem.CampaignAction sortie = actions.stream().filter(a -> a != null && "TACTICAL_CARRIER_SORTIE".equals(a.id)).findFirst().orElse(null);
        CampaignSystem.CampaignAction atomic = actions.stream().filter(a -> a != null && "TACTICAL_ATOMIC_STRIKE".equals(a.id)).findFirst().orElse(null);
        assertNotNull(torpedo);
        assertNotNull(sortie);
        assertNotNull(atomic);
        assertTrue(torpedo.enabled, "shooting range mothership should have torpedo strike access without stores");
        assertTrue(sortie.enabled, "shooting range mothership should have sortie strike access without stores");
        assertTrue(atomic.enabled, "shooting range mothership should have atomic strike access without stores");

        int beforeProjectiles = ctx.projectiles.size();
        assertTrue(CampaignSystem.executeTacticalMapAction(ctx, "TACTICAL_TORPEDO_STRIKE"));
        assertTrue(CampaignSystem.executeTacticalMapAction(ctx, "TACTICAL_TORPEDO_STRIKE"));
        assertTrue(ctx.projectiles.size() > beforeProjectiles, "repeated torpedo strikes should spawn inbound objects");
        assertEquals(0, ctx.campaign.strategicTorpedoCharges, "infinite strike mode should not consume torpedo charges");
        assertEquals(0, ctx.campaign.campaignAmmo, "infinite strike mode should not consume ammo");
        assertEquals(0, ctx.campaign.campaignFuel, "infinite strike mode should not consume fuel");
    }
}
