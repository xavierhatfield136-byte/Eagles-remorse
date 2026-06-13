import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPlaytestHarnessTest {
    @Test
    void automatedCampaignPlaytestAgentsReportNoIssues() {
        CampaignPlaytestHarness.PlaytestReport report = CampaignPlaytestHarness.runDefaultSuite();
        assertTrue(report.passed(), report.summary());
        assertTrue(report.notes().size() >= 4, "playtest report should include agent coverage notes");
    }

    @Test
    void playtestReportSummaryIncludesAgentFindings() {
        CampaignPlaytestHarness.PlaytestReport report = CampaignPlaytestHarness.runDefaultSuite();
        String summary = report.summary();
        assertTrue(summary.contains("PLAYTEST PASS"), summary);
        assertTrue(summary.contains("notes"), summary);
    }
}
