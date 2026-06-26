import app.persistence.CampaignCheckpointStore;
import app.persistence.CampaignSaveContract;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignSaveFieldContractTest {
    @Test
    void everyCheckpointFieldHasAnExplicitStatusDefaultAndFallback() {
        List<CampaignSaveContract.FieldContract> inventory = CampaignSaveContract.inventory();
        long publicFields = java.util.Arrays.stream(CampaignCheckpointStore.Checkpoint.class.getFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .count();

        assertEquals(publicFields, inventory.size());
        assertTrue(inventory.stream().allMatch(row -> row.status() != null));
        assertTrue(inventory.stream().allMatch(row -> row.defaultValue() != null));
        assertTrue(inventory.stream().allMatch(row -> row.fallback() != null && !row.fallback().isBlank()));
        assertEquals(CampaignSaveContract.Status.AUTHORITATIVE_LIVE,
                CampaignSaveContract.field("campaignFuel").status());
        assertEquals(CampaignSaveContract.Status.FUTURE_MODEL_ONLY,
                CampaignSaveContract.field("communityContentState").status());
        assertEquals(CampaignSaveContract.Status.DEBUG_READOUT_ONLY,
                CampaignSaveContract.field("redDirectorBrief").status());
    }
}
