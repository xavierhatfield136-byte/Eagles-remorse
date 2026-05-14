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
    public enum CommIntent {
        IDENTIFY("Identify"),
        STATE_INTENT("State Intent"),
        REQUEST_SUPPORT("Request Support"),
        REQUEST_TRADE("Request Trade"),
        WARN_OFF("Warn Off"),
        DEMAND_SURRENDER("Demand Surrender");

        private final String label;

        CommIntent(String label) {
            this.label = (label == null || label.isBlank()) ? name() : label;
        }

        public String label() {
            return label;
        }

        public CommIntent step(int dir) {
            CommIntent[] values = values();
            int delta = (dir < 0) ? -1 : 1;
            int next = Math.floorMod(ordinal() + delta, values.length);
            return values[next];
        }
    }

    public enum TacticalSectorScalePreset {
        COMPACT("Compact", 1.28),
        STANDARD("Standard", 1.0),
        EXPANDED("Expanded", 0.82);

        private final String label;
        private final double zoomMultiplier;

        TacticalSectorScalePreset(String label, double zoomMultiplier) {
            this.label = (label == null || label.isBlank()) ? name() : label;
            this.zoomMultiplier = Math.max(0.35, zoomMultiplier);
        }

        public String label() {
            return label;
        }

        public double zoomMultiplier() {
            return zoomMultiplier;
        }
    }

    public enum CampaignCommandTab {
        NAV("Navigation"),
        FLEET("Fleet"),
        RESOURCES("Resources"),
        STRIKES("Strikes");

        private final String label;

        CampaignCommandTab(String label) {
            this.label = (label == null || label.isBlank()) ? name() : label;
        }

        public String label() {
            return label;
        }
    }

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

    public static final class StrategicEncounterPrompt {
        public enum Kind {
            TASK_FORCE,
            CAMPAIGN_LOCATION,
            GALAXY_SEARCH_GROUP
        }

        public boolean active = false;
        public Kind kind = Kind.TASK_FORCE;
        public int taskForceId = -1;
        public int galaxySearchGroupId = -1;
        public String campaignLocationId = "";
        public String title = "";
        public String body = "";
        public String location = "";
        public String strengthReadout = "";
    }

    public static final class CampaignHubMenu {
        public boolean active = false;
        public String locationId = "";
        public String serviceId = "";
    }

    public static final class CampaignActionConfirm {
        public boolean active = false;
        public String actionId = "";
        public String title = "";
        public String body = "";
    }

    public boolean shopOpen = false;
    public ShopHullCategory shopHullCategory = ShopHullCategory.ESCORT;
    public int shopHullPage = 0;
    public boolean baseMenuOpen = false;
    public boolean mapOpen = false;
    public boolean powerManagementOpen = false;
    public boolean crewStationsOpen = false;
    public boolean flightDeckOpen = false;
    public final StrategicEncounterPrompt strategicEncounterPrompt = new StrategicEncounterPrompt();
    public final CampaignHubMenu campaignHubMenu = new CampaignHubMenu();
    public final CampaignActionConfirm campaignActionConfirm = new CampaignActionConfirm();
    public int fleetSelectedShipId = -1;
    public int fleetSelectedTurretIndex = -1;
    // Fleet hub: when the campaign shop is open (TAB), toggle between "commission" (buy hulls) and "refit"
    // (ship-by-ship loadout editing) views.
    public boolean fleetRefitMode = true;
    public int powerManagementFocus = 0;
    public int flightDeckFocus = 0;
    public int selectedStrategicDivisionGroupId = 0;
    public CampaignCommandTab campaignCommandTab = CampaignCommandTab.NAV;

    public GameContext.HudDetail hudDetail = GameContext.HudDetail.COMPACT;
    public boolean tacticalViewEnabled = false;
    public GameContext.XrayFilterMode xrayFilterMode = GameContext.XrayFilterMode.ALL;
    public ShipRoomLayout.RoomId xrayFocusedRoom = null;
    public ShipRoomLayout.RoomId xrayHoveredRoom = null;

    public double waypointX = Double.NaN;
    public double waypointY = Double.NaN;
    public double strategicMapFocusX = Double.NaN;
    public double strategicMapFocusY = Double.NaN;
    public double strategicMapZoom = 1.0;
    public String selectedSectorId = "";
    public String loadedSectorId = "";
    public CommIntent commIntent = CommIntent.IDENTIFY;
    public TacticalSectorScalePreset tacticalSectorScalePreset = TacticalSectorScalePreset.STANDARD;
    public final List<Renderer.MapPing> mapPings = new ArrayList<>();
    public String selectedCampaignContactLabel = "";
    public String selectedCampaignContactSubtitle = "";
    public String selectedCampaignContactIntel = "";
    public double selectedCampaignContactX = Double.NaN;
    public double selectedCampaignContactY = Double.NaN;
    public boolean selectedCampaignContactHostile = false;
    public boolean selectedCampaignContactTrackable = false;

    public String voiceCaption = "";
    public double voiceCaptionT = 0.0;
    public boolean voiceCaptionsEnabled = true;
    public String commResultTitle = "";
    public String commResultBody = "";
    public int commResultTargetId = -1;
    public double commResultT = 0.0;
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
        return shopOpen || baseMenuOpen || mapOpen || powerManagementOpen || crewStationsOpen || flightDeckOpen
                || strategicEncounterPrompt.active || campaignHubMenu.active || campaignActionConfirm.active;
    }

    public void showStrategicEncounterPrompt(int taskForceId, String title, String body,
                                             String location, String strengthReadout) {
        strategicEncounterPrompt.active = true;
        strategicEncounterPrompt.kind = StrategicEncounterPrompt.Kind.TASK_FORCE;
        strategicEncounterPrompt.taskForceId = taskForceId;
        strategicEncounterPrompt.galaxySearchGroupId = -1;
        strategicEncounterPrompt.campaignLocationId = "";
        strategicEncounterPrompt.title = (title == null || title.isBlank()) ? "STRATEGIC CONTACT" : title.trim();
        strategicEncounterPrompt.body = (body == null) ? "" : body.trim();
        strategicEncounterPrompt.location = (location == null) ? "" : location.trim();
        strategicEncounterPrompt.strengthReadout = (strengthReadout == null) ? "" : strengthReadout.trim();
    }

    public void showCampaignLocationEncounterPrompt(String campaignLocationId, String title, String body,
                                                    String location, String strengthReadout) {
        strategicEncounterPrompt.active = true;
        strategicEncounterPrompt.kind = StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION;
        strategicEncounterPrompt.taskForceId = -1;
        strategicEncounterPrompt.galaxySearchGroupId = -1;
        strategicEncounterPrompt.campaignLocationId =
                (campaignLocationId == null) ? "" : campaignLocationId.trim();
        strategicEncounterPrompt.title = (title == null || title.isBlank()) ? "MISSION ENCOUNTER" : title.trim();
        strategicEncounterPrompt.body = (body == null) ? "" : body.trim();
        strategicEncounterPrompt.location = (location == null) ? "" : location.trim();
        strategicEncounterPrompt.strengthReadout = (strengthReadout == null) ? "" : strengthReadout.trim();
    }

    public void showGalaxySearchGroupEncounterPrompt(int galaxySearchGroupId, String title, String body,
                                                     String location, String strengthReadout) {
        strategicEncounterPrompt.active = true;
        strategicEncounterPrompt.kind = StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP;
        strategicEncounterPrompt.taskForceId = -1;
        strategicEncounterPrompt.galaxySearchGroupId = galaxySearchGroupId;
        strategicEncounterPrompt.campaignLocationId = "";
        strategicEncounterPrompt.title = (title == null || title.isBlank()) ? "HOSTILE INTERCEPT" : title.trim();
        strategicEncounterPrompt.body = (body == null) ? "" : body.trim();
        strategicEncounterPrompt.location = (location == null) ? "" : location.trim();
        strategicEncounterPrompt.strengthReadout = (strengthReadout == null) ? "" : strengthReadout.trim();
    }

    public void clearStrategicEncounterPrompt() {
        strategicEncounterPrompt.active = false;
        strategicEncounterPrompt.kind = StrategicEncounterPrompt.Kind.TASK_FORCE;
        strategicEncounterPrompt.taskForceId = -1;
        strategicEncounterPrompt.galaxySearchGroupId = -1;
        strategicEncounterPrompt.campaignLocationId = "";
        strategicEncounterPrompt.title = "";
        strategicEncounterPrompt.body = "";
        strategicEncounterPrompt.location = "";
        strategicEncounterPrompt.strengthReadout = "";
    }

    public void showCampaignHubMenu(String locationId, String serviceId) {
        campaignHubMenu.active = true;
        campaignHubMenu.locationId = (locationId == null) ? "" : locationId.trim();
        campaignHubMenu.serviceId = (serviceId == null) ? "" : serviceId.trim();
    }

    public void clearCampaignHubMenu() {
        campaignHubMenu.active = false;
        campaignHubMenu.locationId = "";
        campaignHubMenu.serviceId = "";
    }

    public void showCampaignActionConfirm(String actionId, String title, String body) {
        campaignActionConfirm.active = true;
        campaignActionConfirm.actionId = (actionId == null) ? "" : actionId.trim();
        campaignActionConfirm.title = (title == null || title.isBlank()) ? "CONFIRM ACTION" : title.trim();
        campaignActionConfirm.body = (body == null) ? "" : body.trim();
    }

    public void clearCampaignActionConfirm() {
        campaignActionConfirm.active = false;
        campaignActionConfirm.actionId = "";
        campaignActionConfirm.title = "";
        campaignActionConfirm.body = "";
    }

    public void clearSelectedCampaignContact() {
        selectedCampaignContactLabel = "";
        selectedCampaignContactSubtitle = "";
        selectedCampaignContactIntel = "";
        selectedCampaignContactX = Double.NaN;
        selectedCampaignContactY = Double.NaN;
        selectedCampaignContactHostile = false;
        selectedCampaignContactTrackable = false;
    }

    public void clearVoiceCaption() {
        voiceCaption = "";
        voiceCaptionT = 0.0;
    }

    public void showCommResult(String title, String body, int targetId, double ttl) {
        commResultTitle = (title == null || title.isBlank()) ? "COMM RESULT" : title.trim();
        commResultBody = (body == null) ? "" : body.trim();
        commResultTargetId = targetId;
        commResultT = Math.max(0.0, ttl);
    }

    public void clearCommResult() {
        commResultTitle = "";
        commResultBody = "";
        commResultTargetId = -1;
        commResultT = 0.0;
    }

    public void updateCommResult(double dt) {
        if (commResultT <= 0.0) return;
        commResultT = Math.max(0.0, commResultT - Math.max(0.0, dt));
        if (commResultT <= 0.0) {
            clearCommResult();
        }
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
