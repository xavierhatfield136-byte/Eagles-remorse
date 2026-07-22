package app.persistence;

import app.config.MultiplayerMissionChoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuSettingsStoreTest {

    @Test
    void multiplayerSetupSettingsNormalizeToSafePersistableValues() {
        MenuSettingsStore.MenuSettings settings = new MenuSettingsStore.MenuSettings();
        settings.multiplayerDirectAddress = " 192.168.1.20:46718 ";
        settings.multiplayerMissionId = MultiplayerMissionChoice.HEAVY_DUEL.missionId();
        settings.multiplayerPlayerName = " Ada ";

        settings.normalize();

        assertEquals("192.168.1.20:46718", settings.multiplayerDirectAddress);
        assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(), settings.multiplayerMissionId);
        assertEquals("Ada", settings.multiplayerPlayerName);
    }

    @Test
    void unknownPersistedMultiplayerMissionFallsBackToDefault() {
        MenuSettingsStore.MenuSettings settings = new MenuSettingsStore.MenuSettings();
        settings.multiplayerDirectAddress = "";
        settings.multiplayerMissionId = "missing:mission";
        settings.multiplayerPlayerName = "";

        settings.normalize();

        assertEquals("127.0.0.1:46717", settings.multiplayerDirectAddress);
        assertEquals(MultiplayerMissionChoice.DEFAULT_MISSION_ID, settings.multiplayerMissionId);
        assertEquals("Player", settings.multiplayerPlayerName);
    }
}
