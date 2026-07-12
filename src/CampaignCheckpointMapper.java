import app.persistence.CampaignCheckpointStore;

final class CampaignCheckpointMapper {
    private CampaignCheckpointMapper() {}

    static CampaignCheckpointStore.Checkpoint capture(
            GameContext ctx,
            CampaignSystem.CampaignState state,
            int nextSector) {
        return CampaignSystem.legacyCaptureCheckpoint(ctx, state, nextSector);
    }

    static boolean apply(
            GameContext ctx,
            CampaignSystem.CampaignState state,
            CampaignCheckpointStore.Checkpoint checkpoint) {
        return CampaignSystem.legacyApplyCheckpoint(ctx, state, checkpoint);
    }
}
