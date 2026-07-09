import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignInspectorsMilestoneNineTest {

    @Test
    void fleetInspectorDoesNotLeakMissionFromPlayerSensorContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createForce(ctx, Faction.ENEMY, "Private Mission Fleet");
        int forceId = getInt(force, "id");
        ctx.campaign.campaignIntelTick = 5L;
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                5L, 5L, 0.95, 1200.0, 1300.0, 0.0);

        CampaignSystem.CampaignFleetInspectorView view = CampaignSystem.campaignFleetInspector(ctx, forceId);
        assertNotNull(view);
        assertEquals("Private Mission Fleet", view.displayName);
        assertEquals("Unknown", view.mission);
        assertEquals("Unknown", view.destination);
        assertTrue(view.liveActionsAllowed);
    }

    @Test
    void approximateFleetInspectorDisablesLiveActions() throws Exception {
        GameContext ctx = initializedCampaignContext();
        Object force = createForce(ctx, Faction.ENEMY, "Hidden Approximate Fleet");
        int forceId = getInt(force, "id");
        ctx.campaign.campaignIntelTick = 8L;
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, forceId,
                CampaignSystem.CampaignIntelObservationSource.SITE_RADAR,
                CampaignSystem.CampaignIntelPrecision.APPROXIMATE,
                8L, 12L, 0.6, 1800.0, 1700.0, 240.0);

        CampaignSystem.CampaignFleetInspectorView view = CampaignSystem.campaignFleetInspector(ctx, forceId);
        assertNotNull(view);
        assertEquals("Approximate Contact", view.displayName);
        assertEquals("Unknown", view.faction);
        assertFalse(view.liveActionsAllowed);
    }

    @Test
    void operationInspectorRequiresIntelAndStrategicViewHidesFleetCount() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation origin = ctx.campaign.galaxyMainPois.get(0);
        CampaignSystem.CampaignLocation target = ctx.campaign.galaxyMainPois.get(1);
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                ctx.campaign.factionAttackCommitments,
                new FactionAttackCommitmentSystem.Request(Faction.ENEMY, origin.id, target.id, 0, 0.0, 300.0),
                target.ownerFaction.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(result.accepted());
        assertNull(CampaignSystem.campaignOperationInspector(ctx, result.operationId()));

        ctx.campaign.campaignIntelTick = 9L;
        CampaignSystem.recordCampaignOperationIntelObservation(ctx, result.operationId(),
                CampaignSystem.CampaignIntelObservationSource.OPERATION_INTEL,
                CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY,
                9L, 15L, 0.7, target.x, target.y, 0.0);
        CampaignSystem.CampaignOperationInspectorView view =
                CampaignSystem.campaignOperationInspector(ctx, result.operationId());
        assertNotNull(view);
        assertEquals(-1, view.knownFleetCount);
        assertEquals(-1.0, view.musterProgress, 1e-9);
        assertFalse(CampaignSystem.campaignOperationInspectorLines(ctx).isEmpty());
    }

    private static Object createForce(GameContext ctx, Faction faction, String name) throws Exception {
        Method ensure = CampaignSystem.class.getDeclaredMethod("ensureCampaignForce",
                CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class, Faction.class,
                String.class, String.class, String.class, double.class, double.class);
        ensure.setAccessible(true);
        return ensure.invoke(null, ctx.campaign, CampaignSystem.CampaignForceKind.TASK_FORCE, faction,
                name, "test-base", "inspector regression", 1200.0, 1300.0);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99001L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
