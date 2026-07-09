import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignFleetAuthorityMilestoneTwoTest {

    @Test
    void canonicalForceIdAndOperationAssignmentSurviveCheckpointRoundTrip() throws Exception {
        GameContext source = initializedCampaignContext(22001L);
        CampaignSystem.CampaignState state = source.campaign;
        CampaignSystem.CampaignLocation origin = locationOwnedBy(state, Faction.ENEMY);
        CampaignSystem.CampaignLocation target = locationNotOwnedBy(state, Faction.ENEMY);
        assertNotNull(origin);
        assertNotNull(target);

        Object force = createCaptureForce(state, origin, target, "Milestone Two Stable-ID Fleet");
        int forceId = getInt(force, "id");
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                state.factionAttackCommitments,
                new FactionAttackCommitmentSystem.Request(Faction.ENEMY, origin.id, target.id,
                        forceId, state.sectorElapsed, 300.0),
                target.ownerFaction.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(result.accepted());
        invokeAssignment(state, result.commitment(), force);
        assertEquals(result.operationId(), getString(force, "assignedOperationId"));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 2);
        GameContext restored = initializedCampaignContext(22002L);
        assertTrue(applyCheckpoint(restored, checkpoint));

        Object restoredForce = forceById(restored.campaign, forceId);
        assertNotNull(restoredForce);
        assertEquals(forceId, getInt(restoredForce, "id"));
        assertEquals(result.operationId(), getString(restoredForce, "assignedOperationId"));
        FactionAttackCommitmentSystem.Commitment restoredCommitment =
                FactionAttackCommitmentSystem.active(restored.campaign.factionAttackCommitments,
                        FactionAttackCommitmentSystem.Slot.RED);
        assertNotNull(restoredCommitment);
        assertTrue(restoredCommitment.supportingFleetIds.contains(forceId));
    }

    @Test
    void deterministicRepairRemovesMissingAndOneSidedOperationAssignments() throws Exception {
        GameContext ctx = initializedCampaignContext(22003L);
        CampaignSystem.CampaignState state = ctx.campaign;
        CampaignSystem.CampaignLocation origin = locationOwnedBy(state, Faction.ENEMY);
        CampaignSystem.CampaignLocation target = locationNotOwnedBy(state, Faction.ENEMY);
        assertNotNull(origin);
        assertNotNull(target);

        Object force = createCaptureForce(state, origin, target, "Milestone Two Repair Fleet");
        int forceId = getInt(force, "id");
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                state.factionAttackCommitments,
                new FactionAttackCommitmentSystem.Request(Faction.ENEMY, origin.id, target.id,
                        forceId, state.sectorElapsed, 300.0),
                target.ownerFaction.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(result.accepted());
        invokeAssignment(state, result.commitment(), force);

        result.commitment().supportingFleetIds.remove(forceId);
        result.commitment().supportingFleetIds.add(Integer.MAX_VALUE);
        invokeRepair(state);

        assertEquals("", getString(force, "assignedOperationId"),
                "the authoritative commitment membership should clear a one-sided fleet mirror");
        assertFalse(result.commitment().supportingFleetIds.contains(Integer.MAX_VALUE));
        assertFalse(result.commitment().supportingFleetIds.contains(forceId));
    }

    private static Object createCaptureForce(CampaignSystem.CampaignState state,
                                             CampaignSystem.CampaignLocation origin,
                                             CampaignSystem.CampaignLocation target,
                                             String name) throws Exception {
        Method ensure = CampaignSystem.class.getDeclaredMethod("ensureCampaignForce",
                CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class, Faction.class,
                String.class, String.class, String.class, double.class, double.class);
        ensure.setAccessible(true);
        Object force = ensure.invoke(null, state, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                name, origin.id, "Milestone 2 authority regression", origin.x, origin.y);
        setObject(force, "sourceLocationId", origin.id);
        setObject(force, "homeBaseId", origin.id);
        setObject(force, "destinationLocationId", target.id);
        setDouble(force, "targetX", target.x);
        setDouble(force, "targetY", target.y);
        setEnum(force, "mission", "CAPTURE");
        setEnum(force, "state", "MOVING");
        return force;
    }

    private static CampaignSystem.CampaignLocation locationOwnedBy(CampaignSystem.CampaignState state,
                                                                    Faction faction) {
        return allLocations(state).stream()
                .filter(location -> location != null && location.ownerFaction == faction && !location.destroyed)
                .findFirst().orElse(null);
    }

    private static CampaignSystem.CampaignLocation locationNotOwnedBy(CampaignSystem.CampaignState state,
                                                                       Faction faction) {
        return allLocations(state).stream()
                .filter(location -> location != null && location.ownerFaction != faction && !location.destroyed)
                .findFirst().orElse(null);
    }

    private static List<CampaignSystem.CampaignLocation> allLocations(CampaignSystem.CampaignState state) {
        java.util.ArrayList<CampaignSystem.CampaignLocation> result = new java.util.ArrayList<>();
        result.addAll(state.galaxyMainPois);
        result.addAll(state.galaxyAreasOfInterest);
        return result;
    }

    private static void invokeAssignment(CampaignSystem.CampaignState state,
                                         FactionAttackCommitmentSystem.Commitment commitment,
                                         Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("assignCampaignForceToAttackCommitment",
                CampaignSystem.CampaignState.class, FactionAttackCommitmentSystem.Commitment.class,
                force.getClass());
        method.setAccessible(true);
        method.invoke(null, state, commitment, force);
    }

    private static void invokeRepair(CampaignSystem.CampaignState state) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("repairCampaignForceOperationAssignments",
                CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, state);
    }

    private static Object forceById(CampaignSystem.CampaignState state, int id) throws Exception {
        for (Object force : state.campaignForces) if (getInt(force, "id") == id) return force;
        return null;
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

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String getString(Object target, String fieldName) throws Exception {
        Object value = getObject(target, fieldName);
        return value == null ? "" : value.toString();
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

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnum(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<? extends Enum> type = (Class<? extends Enum>) field.getType();
        field.set(target, Enum.valueOf(type, value));
    }
}
