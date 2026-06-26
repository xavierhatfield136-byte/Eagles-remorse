import app.support.AppInfo;
import app.support.UserDataPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseElevenPackagingReleaseTest {
    @Test
    void releaseContractCoversChannelsAndCleanMachineSteps() throws Exception {
        assertEquals(java.util.List.of(), Phase11PackagingReleaseValidation.validateProjectContract(Path.of(".")));
        assertEquals(4, Phase11PackagingReleaseValidation.distributionChannels().size());
        assertEquals(11, Phase11PackagingReleaseValidation.cleanMachineSteps().size());

        Set<String> channels = Phase11PackagingReleaseValidation.distributionChannels().stream()
                .map(Phase11PackagingReleaseValidation.DistributionChannel::id)
                .collect(Collectors.toSet());
        assertEquals(Set.of("itch", "github", "private", "steam"), channels);
        assertTrue(Phase11PackagingReleaseValidation.distributionChannels().stream()
                .filter(channel -> channel.id().equals("steam"))
                .findFirst()
                .orElseThrow()
                .status()
                .equals("investigated"));
    }

    @Test
    void runtimeDataPathsAreUserWritableAndOutsideInstallTree() {
        Path root = UserDataPaths.root();
        assertTrue(UserDataPaths.saveDir().startsWith(root));
        assertTrue(UserDataPaths.logDir().startsWith(root));
        if (System.getProperty("game.userDataDir") == null) {
            assertFalse(root.toAbsolutePath().normalize().startsWith(Path.of(".").toAbsolutePath().normalize()));
        }
        assertTrue(UserDataPaths.isUserWritablePath(UserDataPaths.saveDir().resolve("campaign_checkpoint.properties")));
        assertTrue(UserDataPaths.isUserWritablePath(UserDataPaths.logDir().resolve("error.log")));
    }

    @Test
    void releaseMetadataIsStableForPackaging() {
        assertEquals("Eagles Remorse", AppInfo.APP_NAME);
        assertFalse(AppInfo.VERSION.isBlank());
        assertTrue(Phase11PackagingReleaseValidation.cleanMachineSteps().stream()
                .anyMatch(step -> step.id().equals("uninstall-preserves-saves")));
        assertTrue(Phase11PackagingReleaseValidation.distributionChannels().stream()
                .anyMatch(channel -> channel.id().equals("github") && channel.artifact().contains(".zip")));
    }
}
