import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionValidationHarnessTest {
    @Test
    void productionValidationAuditsWorkspaceDataWithoutErrors() throws Exception {
        assertTrue(ProductionValidationHarness.validate(Path.of(".")).isEmpty());
    }
}
