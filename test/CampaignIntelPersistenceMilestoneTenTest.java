import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignIntelPersistenceMilestoneTenTest {

    @Test
    void checkpointPersistsDurableIntelButRebuildsLiveDetection() throws Exception {
        GameContext source = initializedCampaignContext(101001L);
        CampaignSystem.CampaignLocation origin = source.campaign.galaxyMainPois.stream()
                .filter(location -> location.ownerFaction == Faction.ENEMY).findFirst().orElseThrow();
        CampaignSystem.CampaignLocation target = source.campaign.galaxyMainPois.stream()
                .filter(location -> location.ownerFaction != Faction.ENEMY).findFirst().orElseThrow();
        Object force = createCaptureForce(source, origin, target);
        int forceId = getInt(force, "id");
        source.campaign.playerGalaxyX = 300.0;
        source.campaign.playerGalaxyY = 300.0;
        source.campaign.campaignIntelTick = 10L;
        CampaignSystem.recordCampaignFleetIntelObservation(source, forceId,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                10L, 100L, 1.0, 4200.0, 4200.0, 0.0);
        CampaignSystem.recordCampaignFleetIntelObservation(source, forceId,
                CampaignSystem.CampaignIntelObservationSource.MISSION_INTEL,
                CampaignSystem.CampaignIntelPrecision.APPROXIMATE,
                10L, 100L, 0.7, 4000.0, 4050.0, 320.0);
        FactionAttackCommitmentSystem.Result operation = FactionAttackCommitmentSystem.request(
                source.campaign.factionAttackCommitments,
                new FactionAttackCommitmentSystem.Request(Faction.ENEMY, origin.id, target.id,
                        forceId, 0.0, 300.0), target.ownerFaction.name(),
                ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(operation.accepted());
        CampaignSystem.recordCampaignOperationIntelObservation(source, operation.operationId(),
                CampaignSystem.CampaignIntelObservationSource.OPERATION_INTEL,
                CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY,
                10L, 100L, 0.75, target.x, target.y, 0.0);

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 2);
        assertTrue(checkpoint.campaignFleetIntel.contains("MISSION_INTEL"));
        assertFalse(checkpoint.campaignFleetIntel.contains("PLAYER_SENSOR"));
        assertFalse(checkpoint.campaignOperationIntel.isBlank());

        GameContext restored = initializedCampaignContext(101002L);
        assertTrue(applyCheckpoint(restored, checkpoint));
        CampaignSystem.CampaignIntelResolution fleetIntel = CampaignSystem.campaignFleetIntelResolution(
                restored, forceId, restored.campaign.campaignIntelTick);
        assertNotNull(fleetIntel);
        assertFalse(fleetIntel.exactPosition(), "distant live sensor truth must not be restored blindly");
        assertNotNull(CampaignSystem.campaignOperationIntelResolution(
                restored, operation.operationId(), restored.campaign.campaignIntelTick));
    }

    private static Object createCaptureForce(GameContext ctx,
                                             CampaignSystem.CampaignLocation origin,
                                             CampaignSystem.CampaignLocation target) throws Exception {
        Method ensure = CampaignSystem.class.getDeclaredMethod("ensureCampaignForce",
                CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class, Faction.class,
                String.class, String.class, String.class, double.class, double.class);
        ensure.setAccessible(true);
        Object force = ensure.invoke(null, ctx.campaign, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Persistent Intel Fleet", origin.id, "persistence regression", 4200.0, 4200.0);
        setObject(force, "sourceLocationId", origin.id);
        setObject(force, "homeBaseId", origin.id);
        setObject(force, "destinationLocationId", target.id);
        setEnum(force, "mission", "CAPTURE");
        setEnum(force, "state", "MOVING");
        return force;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("captureCheckpoint", GameContext.class,
                CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("applyCheckpoint", GameContext.class,
                CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static GameContext initializedCampaignContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnum(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<? extends Enum> type = (Class<? extends Enum>) field.getType();
        field.set(target, Enum.valueOf(type, value));
    }
}
