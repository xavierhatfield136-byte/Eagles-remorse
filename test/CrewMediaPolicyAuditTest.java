import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewMediaPolicyAuditTest {
    @Test
    void frozenAlphaMediaAndAbstractExpansionPersonnelPassPolicyAudit() {
        CrewMediaPolicyAudit.AuditResult result = CrewMediaPolicyAudit.audit();
        assertTrue(result.passed(), () -> String.join("\n", result.errors()));
        assertTrue(result.frozenAssetsChecked() > 0, "the audit must inspect the frozen alpha media baseline");
    }
}

