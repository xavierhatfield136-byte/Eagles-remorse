import app.persistence.MenuSettingsStore;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;

/**
 * UI, overlay, and presentation state that does not belong in the core simulation bucket.
 */
public final class UiState {
    public static final class CombatCallout {
        public double x;
        public double y;
        public final String text;
        public final Color color;
        public double ttl;
        public final double maxTtl;
        public final double risePerSecond;

        CombatCallout(double x, double y, String text, Color color, double ttl) {
            this.x = x;
            this.y = y;
            this.text = (text == null || text.isBlank()) ? "ALERT" : text;
            this.color = (color == null) ? new Color(160, 220, 255) : color;
            this.ttl = Math.max(0.25, ttl);
            this.maxTtl = this.ttl;
            this.risePerSecond = 16.0;
        }

        public double alphaFrac() {
            if (maxTtl <= 1e-6) return 0.0;
            return MathUtil.clamp(ttl / maxTtl, 0.0, 1.0);
        }
    }

    public boolean shopOpen = false;
    public ShopHullCategory shopHullCategory = ShopHullCategory.ESCORT;
    public int shopHullPage = 0;
    public boolean baseMenuOpen = false;
    public boolean mapOpen = false;
    public boolean powerManagementOpen = false;
    public boolean crewStationsOpen = false;
    public boolean flightDeckOpen = false;
    public int fleetSelectedShipId = -1;
    public int fleetSelectedTurretIndex = -1;
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
    public final List<CombatCallout> combatCallouts = new ArrayList<>();
    public String hoverTooltipKey = "";
    public String hoverTooltipTitle = "";
    public String hoverTooltipBody = "";
    public long hoverTooltipSinceNanos = 0L;
    public boolean hoverTooltipVisible = false;
    public int hoverTooltipAnchorX = Integer.MIN_VALUE;
    public int hoverTooltipAnchorY = Integer.MIN_VALUE;
    public Rectangle objectiveHoverRect = null;
    public String objectiveHoverTitle = "";
    public String objectiveHoverBody = "";

    public boolean hasBlockingOverlay() {
        return shopOpen || baseMenuOpen || mapOpen || powerManagementOpen || crewStationsOpen || flightDeckOpen;
    }

    public void clearVoiceCaption() {
        voiceCaption = "";
        voiceCaptionT = 0.0;
    }

    public void updateHoverTooltip(String key, String title, String body,
                                   int mouseX, int mouseY, long nowNanos, long revealDelayNanos) {
        if (key == null || key.isBlank() || body == null || body.isBlank()) {
            clearHoverTooltip();
            return;
        }

        boolean movedFar = hoverTooltipAnchorX != Integer.MIN_VALUE
                && Math.hypot(mouseX - hoverTooltipAnchorX, mouseY - hoverTooltipAnchorY) > 18.0;
        if (!key.equals(hoverTooltipKey) || movedFar) {
            hoverTooltipKey = key;
            hoverTooltipTitle = (title == null) ? "" : title;
            hoverTooltipBody = body;
            hoverTooltipSinceNanos = nowNanos;
            hoverTooltipVisible = false;
            hoverTooltipAnchorX = mouseX;
            hoverTooltipAnchorY = mouseY;
            return;
        }

        hoverTooltipTitle = (title == null) ? "" : title;
        hoverTooltipBody = body;
        hoverTooltipVisible = (nowNanos - hoverTooltipSinceNanos) >= Math.max(0L, revealDelayNanos);
    }

    public void clearHoverTooltip() {
        hoverTooltipKey = "";
        hoverTooltipTitle = "";
        hoverTooltipBody = "";
        hoverTooltipSinceNanos = 0L;
        hoverTooltipVisible = false;
        hoverTooltipAnchorX = Integer.MIN_VALUE;
        hoverTooltipAnchorY = Integer.MIN_VALUE;
    }

    public void setObjectiveHover(Rectangle rect, String title, String body) {
        if (rect == null || body == null || body.isBlank()) {
            clearObjectiveHover();
            return;
        }
        objectiveHoverRect = new Rectangle(rect);
        objectiveHoverTitle = (title == null) ? "" : title;
        objectiveHoverBody = body;
    }

    public void clearObjectiveHover() {
        objectiveHoverRect = null;
        objectiveHoverTitle = "";
        objectiveHoverBody = "";
    }

    public void addCombatCallout(double x, double y, String text, Color color, double ttl) {
        combatCallouts.add(new CombatCallout(x, y, text, color, ttl));
        while (combatCallouts.size() > 24) {
            combatCallouts.remove(0);
        }
    }

    public void updateCombatCallouts(double dt) {
        if (combatCallouts.isEmpty()) return;
        double step = Math.max(0.0, dt);
        for (Iterator<CombatCallout> it = combatCallouts.iterator(); it.hasNext(); ) {
            CombatCallout callout = it.next();
            callout.ttl -= step;
            callout.y -= callout.risePerSecond * step;
            if (callout.ttl <= 0.0) {
                it.remove();
            }
        }
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
