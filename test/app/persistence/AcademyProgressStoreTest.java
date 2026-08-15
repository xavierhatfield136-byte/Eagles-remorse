package app.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademyProgressStoreTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty("game.userDataDir");
        System.clearProperty("game.academyTelemetry");
    }

    @Test
    void progressAndTelemetryStayUnderUserDataDirectory() throws Exception {
        System.setProperty("game.userDataDir", tempDir.toString());

        AcademyProgressStore.Progress progress =
                AcademyProgressStore.markStepCompleted("session-test", "chapter-one", "targeting");
        AcademyProgressStore.recordEvent(progress.sessionId,
                "academy_chapter_completed",
                progress.currentChapter,
                progress.currentStep,
                12.5,
                "success");

        assertTrue(Files.exists(AcademyProgressStore.progressFile()));
        assertTrue(Files.exists(AcademyProgressStore.telemetryFile()));
        assertTrue(AcademyProgressStore.progressFile().startsWith(tempDir));
        assertTrue(AcademyProgressStore.telemetryFile().startsWith(tempDir));
        assertTrue(AcademyProgressStore.load().completedSteps.contains("chapter-one/targeting"));
        String telemetry = Files.readString(AcademyProgressStore.telemetryFile());
        assertTrue(telemetry.contains("\"event\":\"academy_chapter_completed\""));
        assertTrue(telemetry.contains("\"sessionId\":\"session-test\""));
    }

    @Test
    void telemetryCanBeDisabled() {
        System.setProperty("game.userDataDir", tempDir.toString());
        System.setProperty("game.academyTelemetry", "false");

        AcademyProgressStore.recordEvent("session-test", "academy_started", "start", "", 0.0, "");

        assertFalse(Files.exists(AcademyProgressStore.telemetryFile()));
    }

    @Test
    void failureRecoveryHintAndSummaryDataAreLocal() throws Exception {
        System.setProperty("game.userDataDir", tempDir.toString());

        AcademyProgressStore.markStepFailed("session-test", "hold-the-line", "withdraw", "lost escort");
        AcademyProgressStore.markStepRecovered("session-test", "hold-the-line", "withdraw", "skipped");
        AcademyProgressStore.recordHint("session-test", "hold-the-line", "withdraw", false);
        AcademyProgressStore.recordHint("session-test", "hold-the-line", "withdraw", true);
        AcademyProgressStore.recordEvent("session-test", "academy_started", "Academy", "start", 0.0, "started");
        AcademyProgressStore.recordEvent("session-test", "academy_chapter_completed", "hold-the-line", "withdraw", 42.0, "complete");
        AcademyProgressStore.markAbandoned("session-test", "choose-mission", "briefing", 120.0);
        AcademyProgressStore.markCompleted("session-test", "version=1;liveFriendly=4");
        AcademyProgressStore.recordEvent("session-test", "academy_completed", "Graduation", "complete", 0.0, "complete");

        AcademyProgressStore.Progress progress = AcademyProgressStore.load();
        assertTrue(progress.failedSteps.contains("hold-the-line/withdraw"));
        assertTrue(progress.recoveredSteps.contains("hold-the-line/withdraw"));
        assertTrue(progress.hintDisplayCounts.contains("hold-the-line/withdraw=2"));
        assertTrue(progress.hintRepeatCounts.contains("hold-the-line/withdraw=1"));
        assertTrue(progress.graduationSnapshot.contains("liveFriendly=4"));

        AcademyTelemetrySummary.Summary summary =
                AcademyTelemetrySummary.summarize(AcademyProgressStore.telemetryFile());
        assertEquals(1, summary.starts);
        assertEquals(1, summary.completions);
        assertEquals(1, summary.failuresByChapter.get("hold-the-line"));
        assertEquals(1, summary.abandonmentsByChapter.get("choose-mission"));
        assertEquals(1, summary.repeatedHintsByChapter.get("hold-the-line"));
        assertTrue(summary.toLines().stream().anyMatch(line -> line.contains("Average chapter time")));
    }
}
