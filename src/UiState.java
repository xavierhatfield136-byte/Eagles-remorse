import app.persistence.MenuSettingsStore;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    public enum TacticalMapTab {
        MISSION("Mission"),
        FLEET("Fleet"),
        RESOURCES("Resources"),
        CONTACTS("Contacts"),
        STRIKES("Strikes");

        private final String label;

        TacticalMapTab(String label) {
            this.label = (label == null || label.isBlank()) ? name() : label;
        }

        public String label() {
            return label;
        }
    }

    public enum TacticalMapSelectionKind {
        MISSION,
        OBJECTIVE,
        CONTACT,
        LANDMARK,
        SPACE
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
            GALAXY_SEARCH_GROUP,
            INSTALLATION_THREAT,
            CAMPAIGN_FORCE,
            CAMPAIGN_BATTLE
        }

        public boolean active = false;
        public Kind kind = Kind.TASK_FORCE;
        public int taskForceId = -1;
        public int galaxySearchGroupId = -1;
        public int installationThreatId = -1;
        public int campaignForceId = -1;
        public int campaignBattleId = -1;
        public String campaignLocationId = "";
        public String title = "";
        public String body = "";
        public String location = "";
        public String strengthReadout = "";
    }

    public enum BlockingModalOwner {
        NONE(0),
        HUB_ACTION(10),
        CONFIRMATION(20),
        STORY_SCENE(30),
        TACTICAL_ENTRY(50),
        INTERVENTION(60);

        private final int priority;

        BlockingModalOwner(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
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

    public static final class CommTradeOption {
        public String id = "";
        public String label = "";
        public String detail = "";
        public boolean enabled = true;
    }

    public static final class CommTradeMenu {
        public boolean active = false;
        public int targetId = -1;
        public String title = "";
        public String body = "";
        public int selectedIndex = 0;
        public final List<CommTradeOption> options = new ArrayList<>();
    }

    public boolean shopOpen = false;
    public ShopHullCategory shopHullCategory = ShopHullCategory.ESCORT;
    public int shopHullPage = 0;
    public boolean baseMenuOpen = false;
    public boolean mapOpen = false;
    public boolean powerManagementOpen = false;
    public boolean crewStationsOpen = false;
    public boolean flightDeckOpen = false;
    public boolean controlsScreenOpen = false;
    public String controlsSearchQuery = "";
    public int controlsSelectedIndex = 0;
    public String controlsCaptureAction = "";
    public int overlayInvariantRepairCount = 0;
    public String overlayInvariantLastRepair = "";
    public boolean modalPauseOwned = false;
    public GameState lastObservedGameState = null;
    public final List<String> stateTransitionHistory = new ArrayList<>();
    public final StrategicEncounterPrompt strategicEncounterPrompt = new StrategicEncounterPrompt();
    private final Deque<StrategicEncounterPrompt> queuedStrategicEncounterPrompts = new ArrayDeque<>();
    public final CampaignHubMenu campaignHubMenu = new CampaignHubMenu();
    public final CampaignActionConfirm campaignActionConfirm = new CampaignActionConfirm();
    public final CommTradeMenu commTradeMenu = new CommTradeMenu();
    public int fleetSelectedShipId = -1;
    public int fleetSelectedTurretIndex = -1;
    public int campaignFleetFocusSlotId = -1;
    public int campaignFleetRosterScroll = 0;
    // Fleet hub: when the campaign shop is open (TAB), toggle between "commission" (buy hulls) and "refit"
    // (ship-by-ship loadout editing) views.
    public boolean fleetRefitMode = true;
    public int powerManagementFocus = 0;
    public int flightDeckFocus = 0;
    public int selectedStrategicDivisionGroupId = 0;
    public CampaignCommandTab campaignCommandTab = CampaignCommandTab.NAV;
    public TacticalMapTab tacticalMapTab = TacticalMapTab.MISSION;
    public boolean campaignWarMapSimplified = false;

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
    public int selectedCampaignContactGalaxySearchGroupId = 0;
    public TacticalMapSelectionKind tacticalMapSelectionKind = TacticalMapSelectionKind.MISSION;
    public String tacticalMapSelectionLabel = "";
    public String tacticalMapSelectionSubtitle = "";
    public String tacticalMapSelectionDetail = "";
    public double tacticalMapSelectionX = Double.NaN;
    public double tacticalMapSelectionY = Double.NaN;
    public boolean tacticalMapSelectionHostile = false;
    // 0 torpedo, 1 air wing, 2 nuclear
    public int combatStrikeSelection = 0;

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
    public int tacticalReadabilityDamageCursor = 0;
    public TacticalReadabilitySystem.CombatLogFilter combatLogFilter = TacticalReadabilitySystem.CombatLogFilter.ALL;
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
        return shopOpen || baseMenuOpen || mapOpen || powerManagementOpen || crewStationsOpen || flightDeckOpen || controlsScreenOpen
                || strategicEncounterPrompt.active || campaignHubMenu.active || campaignActionConfirm.active || commTradeMenu.active;
    }

    public void showStrategicEncounterPrompt(int taskForceId, String title, String body,
                                             String location, String strengthReadout) {
        StrategicEncounterPrompt next = newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind.TASK_FORCE,
                title, body, location, strengthReadout, "STRATEGIC CONTACT");
        next.taskForceId = taskForceId;
        submitStrategicEncounterPrompt(next);
    }

    public void showCampaignLocationEncounterPrompt(String campaignLocationId, String title, String body,
                                                    String location, String strengthReadout) {
        StrategicEncounterPrompt next = newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION,
                title, body, location, strengthReadout, "MISSION ENCOUNTER");
        next.campaignLocationId = (campaignLocationId == null) ? "" : campaignLocationId.trim();
        submitStrategicEncounterPrompt(next);
    }

    public void showGalaxySearchGroupEncounterPrompt(int galaxySearchGroupId, String title, String body,
                                                     String location, String strengthReadout) {
        StrategicEncounterPrompt next = newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP,
                title, body, location, strengthReadout, "HOSTILE INTERCEPT");
        next.galaxySearchGroupId = galaxySearchGroupId;
        submitStrategicEncounterPrompt(next);
    }

    public void showInstallationThreatEncounterPrompt(int installationThreatId, String campaignLocationId, String title, String body,
                                                      String location, String strengthReadout) {
        StrategicEncounterPrompt next = newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind.INSTALLATION_THREAT,
                title, body, location, strengthReadout, "INSTALLATION THREAT");
        next.installationThreatId = installationThreatId;
        next.campaignLocationId = (campaignLocationId == null) ? "" : campaignLocationId.trim();
        submitStrategicEncounterPrompt(next);
    }

    public void showCampaignForceEncounterPrompt(int campaignForceId, String title, String body,
                                                 String location, String strengthReadout) {
        StrategicEncounterPrompt next = newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE,
                title, body, location, strengthReadout, "HOSTILE FORCE CONTACT");
        next.campaignForceId = campaignForceId;
        submitStrategicEncounterPrompt(next);
    }

    public void showCampaignBattleInterventionPrompt(int campaignBattleId, String title, String body,
                                                     String location, String strengthReadout) {
        StrategicEncounterPrompt next = newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE,
                title, body, location, strengthReadout, "BATTLE INTERVENTION");
        next.campaignBattleId = campaignBattleId;
        submitStrategicEncounterPrompt(next);
    }

    public void clearStrategicEncounterPrompt() {
        clearActiveStrategicEncounterPrompt();
        StrategicEncounterPrompt next = queuedStrategicEncounterPrompts.pollFirst();
        if (next != null) copyStrategicEncounterPrompt(next, strategicEncounterPrompt);
    }

    public void clearAllStrategicEncounterPrompts() {
        queuedStrategicEncounterPrompts.clear();
        clearActiveStrategicEncounterPrompt();
    }

    public int queuedStrategicEncounterPromptCount() {
        return queuedStrategicEncounterPrompts.size();
    }

    public BlockingModalOwner blockingModalOwner() {
        if (strategicEncounterPrompt.active) return modalOwner(strategicEncounterPrompt.kind);
        if (campaignActionConfirm.active) return BlockingModalOwner.CONFIRMATION;
        if (campaignHubMenu.active) return BlockingModalOwner.HUB_ACTION;
        return BlockingModalOwner.NONE;
    }

    private void clearActiveStrategicEncounterPrompt() {
        strategicEncounterPrompt.active = false;
        strategicEncounterPrompt.kind = StrategicEncounterPrompt.Kind.TASK_FORCE;
        clearStrategicEncounterPromptReferences();
        strategicEncounterPrompt.title = "";
        strategicEncounterPrompt.body = "";
        strategicEncounterPrompt.location = "";
        strategicEncounterPrompt.strengthReadout = "";
    }

    private StrategicEncounterPrompt newStrategicEncounterPrompt(StrategicEncounterPrompt.Kind kind,
                                                                  String title, String body, String location,
                                                                  String strengthReadout, String fallbackTitle) {
        StrategicEncounterPrompt next = new StrategicEncounterPrompt();
        next.active = true;
        next.kind = (kind == null) ? StrategicEncounterPrompt.Kind.TASK_FORCE : kind;
        next.title = (title == null || title.isBlank()) ? fallbackTitle : title.trim();
        next.body = (body == null) ? "" : body.trim();
        next.location = (location == null) ? "" : location.trim();
        next.strengthReadout = (strengthReadout == null) ? "" : strengthReadout.trim();
        return next;
    }

    private void submitStrategicEncounterPrompt(StrategicEncounterPrompt next) {
        if (next == null) return;
        if (!strategicEncounterPrompt.active) {
            copyStrategicEncounterPrompt(next, strategicEncounterPrompt);
            return;
        }
        BlockingModalOwner currentOwner = modalOwner(strategicEncounterPrompt.kind);
        BlockingModalOwner nextOwner = modalOwner(next.kind);
        if (nextOwner.priority() >= currentOwner.priority()) {
            queuedStrategicEncounterPrompts.addLast(copyOfStrategicEncounterPrompt(strategicEncounterPrompt));
            copyStrategicEncounterPrompt(next, strategicEncounterPrompt);
        } else {
            queuedStrategicEncounterPrompts.addLast(copyOfStrategicEncounterPrompt(next));
        }
    }

    private static BlockingModalOwner modalOwner(StrategicEncounterPrompt.Kind kind) {
        return kind == StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE
                ? BlockingModalOwner.INTERVENTION
                : BlockingModalOwner.TACTICAL_ENTRY;
    }

    private static StrategicEncounterPrompt copyOfStrategicEncounterPrompt(StrategicEncounterPrompt source) {
        StrategicEncounterPrompt copy = new StrategicEncounterPrompt();
        copyStrategicEncounterPrompt(source, copy);
        return copy;
    }

    private static void copyStrategicEncounterPrompt(StrategicEncounterPrompt source,
                                                     StrategicEncounterPrompt target) {
        target.active = source.active;
        target.kind = source.kind;
        target.taskForceId = source.taskForceId;
        target.galaxySearchGroupId = source.galaxySearchGroupId;
        target.installationThreatId = source.installationThreatId;
        target.campaignForceId = source.campaignForceId;
        target.campaignBattleId = source.campaignBattleId;
        target.campaignLocationId = source.campaignLocationId;
        target.title = source.title;
        target.body = source.body;
        target.location = source.location;
        target.strengthReadout = source.strengthReadout;
    }

    private void clearStrategicEncounterPromptReferences() {
        strategicEncounterPrompt.taskForceId = -1;
        strategicEncounterPrompt.galaxySearchGroupId = -1;
        strategicEncounterPrompt.installationThreatId = -1;
        strategicEncounterPrompt.campaignForceId = -1;
        strategicEncounterPrompt.campaignBattleId = -1;
        strategicEncounterPrompt.campaignLocationId = "";
    }

    public void showCampaignHubMenu(String locationId, String serviceId) {
        if (blockingModalOwner().priority() > BlockingModalOwner.HUB_ACTION.priority()) return;
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
        if (blockingModalOwner().priority() > BlockingModalOwner.CONFIRMATION.priority()) return;
        clearCampaignHubMenu();
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

    public void showCommTradeMenu(int targetId, String title, String body, List<CommTradeOption> options) {
        commTradeMenu.active = true;
        commTradeMenu.targetId = targetId;
        commTradeMenu.title = (title == null || title.isBlank()) ? "TRADE CHANNEL" : title.trim();
        commTradeMenu.body = (body == null) ? "" : body.trim();
        commTradeMenu.options.clear();
        if (options != null) {
            for (CommTradeOption option : options) {
                if (option == null) continue;
                CommTradeOption copy = new CommTradeOption();
                copy.id = (option.id == null) ? "" : option.id.trim();
                copy.label = (option.label == null || option.label.isBlank()) ? copy.id : option.label.trim();
                copy.detail = (option.detail == null) ? "" : option.detail.trim();
                copy.enabled = option.enabled;
                commTradeMenu.options.add(copy);
            }
        }
        commTradeMenu.selectedIndex = firstEnabledCommTradeOptionIndex();
    }

    public void clearCommTradeMenu() {
        commTradeMenu.active = false;
        commTradeMenu.targetId = -1;
        commTradeMenu.title = "";
        commTradeMenu.body = "";
        commTradeMenu.selectedIndex = 0;
        commTradeMenu.options.clear();
    }

    public int firstEnabledCommTradeOptionIndex() {
        for (int i = 0; i < commTradeMenu.options.size(); i++) {
            CommTradeOption option = commTradeMenu.options.get(i);
            if (option != null && option.enabled) return i;
        }
        return 0;
    }

    public void clearSelectedCampaignContact() {
        selectedCampaignContactLabel = "";
        selectedCampaignContactSubtitle = "";
        selectedCampaignContactIntel = "";
        selectedCampaignContactX = Double.NaN;
        selectedCampaignContactY = Double.NaN;
        selectedCampaignContactHostile = false;
        selectedCampaignContactTrackable = false;
        selectedCampaignContactGalaxySearchGroupId = 0;
    }

    public void clearTacticalMapSelection() {
        tacticalMapSelectionKind = TacticalMapSelectionKind.MISSION;
        tacticalMapSelectionLabel = "";
        tacticalMapSelectionSubtitle = "";
        tacticalMapSelectionDetail = "";
        tacticalMapSelectionX = Double.NaN;
        tacticalMapSelectionY = Double.NaN;
        tacticalMapSelectionHostile = false;
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
