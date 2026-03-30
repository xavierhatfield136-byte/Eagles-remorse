import app.persistence.MenuSettingsStore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * UI, overlay, and presentation state that does not belong in the core simulation bucket.
 */
public final class UiState {
    public boolean shopOpen = false;
    public boolean baseMenuOpen = false;
    public boolean mapOpen = false;
    public boolean powerManagementOpen = false;
    public boolean crewStationsOpen = false;
    public boolean flightDeckOpen = false;
    public int powerManagementFocus = 0;
    public int flightDeckFocus = 0;

    public GameContext.HudDetail hudDetail = GameContext.HudDetail.COMPACT;
    public GameContext.XrayFilterMode xrayFilterMode = GameContext.XrayFilterMode.ALL;
    public ShipRoomLayout.RoomId xrayFocusedRoom = null;
    public ShipRoomLayout.RoomId xrayHoveredRoom = null;

    public double waypointX = Double.NaN;
    public double waypointY = Double.NaN;
    public final List<Renderer.MapPing> mapPings = new ArrayList<>();

    public String voiceCaption = "";
    public double voiceCaptionT = 0.0;
    public boolean voiceCaptionsEnabled = true;
    public GameContext.CrewStation voiceMixFocus = GameContext.CrewStation.CAPTAIN;
    public final EnumMap<GameContext.CrewStation, Double> voiceRoleVolumes =
            new EnumMap<>(GameContext.CrewStation.class);
    public final EnumMap<GameContext.CrewStation, Integer> portraitExpressionLevel =
            new EnumMap<>(GameContext.CrewStation.class);
    public final EnumMap<GameContext.CrewStation, Double> portraitExpressionTimerSec =
            new EnumMap<>(GameContext.CrewStation.class);

    public boolean hasBlockingOverlay() {
        return shopOpen || baseMenuOpen || mapOpen || powerManagementOpen || crewStationsOpen || flightDeckOpen;
    }

    public void clearVoiceCaption() {
        voiceCaption = "";
        voiceCaptionT = 0.0;
    }

    public void initAudioPreferences() {
        for (GameContext.CrewStation station : GameContext.CrewStation.values()) {
            voiceRoleVolumes.put(station, 1.0);
            portraitExpressionLevel.put(station, 0);
            portraitExpressionTimerSec.put(station, 0.0);
        }
        MenuSettingsStore.MenuSettings persisted = MenuSettingsStore.load();
        voiceCaptionsEnabled = persisted.voiceCaptionsEnabled;
        voiceRoleVolumes.put(GameContext.CrewStation.CAPTAIN, clampVoiceVol(persisted.voiceVolumeCaptain));
        voiceRoleVolumes.put(GameContext.CrewStation.HELM, clampVoiceVol(persisted.voiceVolumeHelm));
        voiceRoleVolumes.put(GameContext.CrewStation.TACTICAL, clampVoiceVol(persisted.voiceVolumeTactical));
        voiceRoleVolumes.put(GameContext.CrewStation.ENGINEERING, clampVoiceVol(persisted.voiceVolumeEngineering));
        voiceRoleVolumes.put(GameContext.CrewStation.SCIENCE, clampVoiceVol(persisted.voiceVolumeScience));
    }

    public double voiceRoleVolume(GameContext.CrewStation station) {
        if (station == null) return 1.0;
        return clampVoiceVol(voiceRoleVolumes.getOrDefault(station, 1.0));
    }

    public void setVoiceRoleVolume(GameContext.CrewStation station, double value) {
        if (station == null) return;
        voiceRoleVolumes.put(station, clampVoiceVol(value));
    }

    public int portraitExpression(GameContext.CrewStation station) {
        if (station == null) return 0;
        return MathUtil.clamp(portraitExpressionLevel.getOrDefault(station, 0), 0, 3);
    }

    public void setPortraitExpression(GameContext.CrewStation station, int expression, double holdSec) {
        if (station == null) return;
        portraitExpressionLevel.put(station, MathUtil.clamp(expression, 0, 3));
        portraitExpressionTimerSec.put(station, Math.max(0.0, holdSec));
    }

    public void decayPortraitExpressions(double dt) {
        double step = Math.max(0.0, dt);
        if (step <= 0.0) return;
        for (GameContext.CrewStation station : GameContext.CrewStation.values()) {
            double t = Math.max(0.0, portraitExpressionTimerSec.getOrDefault(station, 0.0) - step);
            portraitExpressionTimerSec.put(station, t);
            if (t <= 0.0) {
                portraitExpressionLevel.put(station, 0);
            }
        }
    }

    private static double clampVoiceVol(double v) {
        if (!Double.isFinite(v)) return 1.0;
        return MathUtil.clamp(v, 0.0, 2.0);
    }
}
