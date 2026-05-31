import app.ui.ThemeArt;
import app.config.GameMode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Comparator;
import javax.imageio.ImageIO;

public class Renderer {
    private static final int BEAM_BOLT_TURRET_TETHER_FRAMES =
            Math.max(1, (int) Math.round(0.5 / GameContext.DT));

    private static final File HUD_PANEL_DIR = new File("assets/hud_panels");
    private static final double IMPACT_DECAL_SCALE = 0.25;
    private static final double HULL_DAMAGE_DETAIL_MIN_SCREEN_SPAN = 72.0;
    private static final double HULL_DAMAGE_BREACH_MIN_SCREEN_SPAN = 108.0;
    private static final double HULL_DAMAGE_IMPACT_OVERLAY_MIN_SCREEN_SPAN = 92.0;
    private static final double HULL_DAMAGE_PATCH_MIN_SCREEN_SPAN = 96.0;
    private static final double HULL_DAMAGE_PATCH_MIN_RENDER_RADIUS = 2.2;
    private static final double SHIELD_FX_MIN_SCREEN_SPAN = 56.0;
    private static final double WARP_FX_MIN_SCREEN_SPAN = 64.0;
    private static final double SHIELD_FX_MIN_MARK_FRESHNESS = 0.06;
    private static long frameShieldRenderNs = 0L;

    private static final String[] CORE_MENU_LABELS = {"SHOP", "BASE", "MAP", "POWER", "CREW", "SAFE EXIT"};
    private static final String[] CORE_MENU_HOTKEYS = {"TAB", "B", "M", "O", "H", ""};
    private static final long XRAY_PERCENT_REFRESH_NS = 180_000_000L;
    private static final Font XRAY_TITLE_FONT = new Font("Consolas", Font.BOLD, 13);
    private static final Font XRAY_SUBTITLE_FONT = new Font("Consolas", Font.PLAIN, 11);
    private static final Font XRAY_SYMBOL_FONT = new Font("Consolas", Font.BOLD, 10);
    private static final Font XRAY_HP_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final String[] XRAY_PCT_LABELS = buildXrayPctLabels();
    // Cache rendered x-ray panels for a short window to avoid rebuilding the full panel every draw.
    private static final long XRAY_PANEL_FRAME_CACHE_NS = 36_000_000L;
    private static final java.util.WeakHashMap<Ship, EnumMap<ShipRoomLayout.RoomId, Integer>> XRAY_ROOM_PCT_CACHE =
            new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, Long> XRAY_ROOM_PCT_CACHE_TS =
            new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, XrayPanelFrameCache> XRAY_PANEL_CACHE =
            new java.util.WeakHashMap<>();
    private static final Font XRAY_META_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final Font XRAY_REPAIR_FONT = new Font("Consolas", Font.BOLD, 8);
    private static final Stroke XRAY_HIT_STROKE = new BasicStroke(1.8f);
    private static final Stroke XRAY_DISABLED_STROKE = new BasicStroke(1.5f);
    private static final Stroke XRAY_FOCUS_STROKE = new BasicStroke(2.1f);
    private static final Font HOVER_TOOLTIP_TITLE_FONT = new Font("Consolas", Font.BOLD, 12);
    private static final Font HOVER_TOOLTIP_BODY_FONT = new Font("Consolas", Font.PLAIN, 11);
    private static final Font STRATEGIC_MAP_ZONE_FONT = new Font("Consolas", Font.BOLD, 10);
    private static final Font STRATEGIC_MAP_ZONE_TAG_FONT = new Font("Consolas", Font.BOLD, 9);
    private static final Font STRATEGIC_MAP_OBJECTIVE_FONT = new Font("Consolas", Font.BOLD, 10);
    private static final Stroke STRATEGIC_MAP_ZONE_STROKE = new BasicStroke(1.4f);
    private static final Stroke STRATEGIC_MAP_ZONE_ACTIVE_STROKE = new BasicStroke(2.2f);
    private static final Stroke STRATEGIC_MAP_ZONE_OBJECTIVE_STROKE = new BasicStroke(2.6f);
    private static final Stroke STRATEGIC_MAP_OBJECTIVE_STROKE =
            new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // ------------------------------------------------------------
    // Option 8: Strategic map / waypoints / pings
    // ------------------------------------------------------------
    public static final class MapPing {
        public double x, y;
        public double t; // seconds remaining
        public int faction; // 0=player, 1=team A, 2=team B, 3=team C, 4=team D

        public MapPing(double x, double y, double t, int faction) {
            this.x = x;
            this.y = y;
            this.t = t;
            this.faction = faction;
        }
    }

    public static void beginFramePerfCapture() {
        frameShieldRenderNs = 0L;
    }

    public static double frameShieldRenderMs() {
        return frameShieldRenderNs / 1_000_000.0;
    }

    public static void prewarmAssetCaches() {
        for (ShipRole role : ShipRole.values()) {
            for (Faction faction : Faction.values()) {
                ShipSkinLibrary.getSkinSet(role, faction);
            }
            ShipSkinLibrary.getSkinSet(role, null);
        }

        String[] stationModules = {
                "hull_fortification",
                "shield_array",
                "turret_systems",
                "mining_ops",
                "hangar_expansion"
        };
        for (Faction faction : Faction.values()) {
            for (String key : stationModules) {
                StationModuleLibrary.getModuleSkin(key, faction);
            }
        }
        for (String key : stationModules) {
            StationModuleLibrary.getModuleSkin(key, null);
        }

        ProjectileSkinLibrary.getMissileSkin();
        ProjectileSkinLibrary.getEnergyBoltSkin(false);
        ProjectileSkinLibrary.getEnergyBoltSkin(true);
        ProjectileSkinLibrary.getBeamBoltSingleSkin();
        ProjectileSkinLibrary.getWaveShotSkin();
        ProjectileSkinLibrary.getBulletSkin();
        ProjectileSkinLibrary.getCiwsPelletSkin();
    }

    public static final class ShopClickTarget {
        public enum Kind {
            UPGRADE,
            HULL,
            CATEGORY,
            PAGE
        }

        public final Kind kind;
        public final int upgradeId;
        public final ShipRole role;
        public final ShopHullCategory category;
        public final int pageDelta;

        public ShopClickTarget(Kind kind, int upgradeId, ShipRole role, ShopHullCategory category, int pageDelta) {
            this.kind = kind;
            this.upgradeId = upgradeId;
            this.role = role;
            this.category = category;
            this.pageDelta = pageDelta;
        }
    }

    public static final class FleetOverlayClickTarget {
        public enum Kind {
            MODE_COMMISSION,
            MODE_REFIT,
            SELECT_SHIP,
            SELECT_TURRET,
            SWAP_TO_GUN,
            SWAP_TO_MISSILE,
            SET_MISSILE_ROLE
        }

        public final Kind kind;
        public final int shipId;
        public final int turretIndex;
        public final Turret.MissileRole missileRole;

        public FleetOverlayClickTarget(Kind kind, int shipId, int turretIndex, Turret.MissileRole missileRole) {
            this.kind = kind;
            this.shipId = shipId;
            this.turretIndex = turretIndex;
            this.missileRole = missileRole;
        }
    }

    public static final class HoverTooltip {
        public final String key;
        public final String title;
        public final String body;

        HoverTooltip(String key, String title, String body) {
            this.key = (key == null) ? "" : key;
            this.title = (title == null) ? "" : title;
            this.body = (body == null) ? "" : body;
        }
    }

    public static final class HudPanelClickTarget {
        public enum Kind {
            BEAM_RAPID,
            BEAM_CONCENTRATED,
            MISSILE_HEAVY,
            MISSILE_FAST,
            MISSILE_AAA,
            ECM_PRIMED,
            ECM_ACTIVE,
            CLOAK_CHARGE,
            CLOAK_ACTIVE,
            STRIKE_SELECT_TORPEDO,
            STRIKE_SELECT_AIRWING,
            STRIKE_SELECT_NUCLEAR,
            STRIKE_LAUNCH
        }

        public final Kind kind;

        public HudPanelClickTarget(Kind kind) {
            this.kind = kind;
        }
    }

    public static final class CampaignHubClickTarget {
        public enum Kind {
            SERVICE,
            TAB,
            ACTION,
            FLEET_ROSTER,
            CONFIRM,
            CLOSE
        }

        public final Kind kind;
        public final String serviceId;
        public final String valueId;

        public CampaignHubClickTarget(Kind kind, String serviceId) {
            this(kind, serviceId, serviceId);
        }

        public CampaignHubClickTarget(Kind kind, String serviceId, String valueId) {
            this.kind = kind;
            this.serviceId = (serviceId == null) ? "" : serviceId;
            this.valueId = (valueId == null) ? "" : valueId;
        }
    }

    private static final class CampaignActionSection {
        final CampaignSystem.CampaignActionCategory category;
        final String label;
        final List<CampaignSystem.CampaignAction> actions;

        CampaignActionSection(CampaignSystem.CampaignActionCategory category,
                              String label,
                              List<CampaignSystem.CampaignAction> actions) {
            this.category = category;
            this.label = label;
            this.actions = actions;
        }
    }

    private static final class CampaignActionLayoutEntry {
        final CampaignSystem.CampaignAction action;
        final Rectangle rect;

        CampaignActionLayoutEntry(CampaignSystem.CampaignAction action, Rectangle rect) {
            this.action = action;
            this.rect = rect;
        }
    }

    private enum CombatHudPanelImageKey {
        BEAM_RAPID,
        BEAM_CONCENTRATED,
        MISSILE_HEAVY,
        MISSILE_FAST,
        MISSILE_AAA,
        ECM_PRIMED,
        ECM_ACTIVE,
        CLOAK_CHARGE,
        CLOAK_ACTIVE
    }

    private static final class CombatHudPanelLayout {
        final Rectangle beamRect;
        final Rectangle missileRect;
        final Rectangle ecmRect;
        final Rectangle cloakRect;

        CombatHudPanelLayout(Rectangle beamRect, Rectangle missileRect, Rectangle ecmRect, Rectangle cloakRect) {
            this.beamRect = beamRect;
            this.missileRect = missileRect;
            this.ecmRect = ecmRect;
            this.cloakRect = cloakRect;
        }
    }

    private static final class HudPanelVisual {
        final Rectangle drawRect;
        final BufferedImage image;

        HudPanelVisual(Rectangle drawRect, BufferedImage image) {
            this.drawRect = drawRect;
            this.image = image;
        }
    }

    private static final class HudPanelSkinLibrary {
        private static final Map<CombatHudPanelImageKey, BufferedImage> CACHE = new HashMap<>();

        private static BufferedImage get(CombatHudPanelImageKey key) {
            if (key == null) return null;
            BufferedImage cached = CACHE.get(key);
            if (cached != null) return cached;
            BufferedImage loaded = load(key);
            if (loaded != null) {
                CACHE.put(key, loaded);
                return loaded;
            }
            return null;
        }

        private static BufferedImage load(CombatHudPanelImageKey key) {
            String[] candidates = switch (key) {
                case BEAM_RAPID -> new String[]{
                        "beam_mode_rapid.png",
                        "beam_mode_rapid_fire.png",
                        "beam mode rapid fire.png",
                        "beam_mode_rapidfire.png",
                        "beam mode rapid.png"
                };
                case BEAM_CONCENTRATED -> new String[]{
                        "beam_mode_concentrated.png",
                        "beam mode concentrated.png",
                        "beam_mode_focus.png"
                };
                case MISSILE_HEAVY -> new String[]{
                        "missile_mode_heavy.png",
                        "missile mode heavy.png"
                };
                case MISSILE_FAST -> new String[]{
                        "missile_mode_fast.png",
                        "missile mode fast.png"
                };
                case MISSILE_AAA -> new String[]{
                        "missile_mode_aaa.png",
                        "missile mode aaa.png",
                        "missile_mode_aa.png",
                        "missile mode aa.png"
                };
                case ECM_PRIMED -> new String[]{
                        "ecm_mode_primed.png",
                        "ecm mode primed.png"
                };
                case ECM_ACTIVE -> new String[]{
                        "ecm_mode_active.png",
                        "ecm mode active.png"
                };
                case CLOAK_CHARGE -> new String[]{
                        "cloak_mode_charge.png",
                        "cloak mode charge.png",
                        "cloak_charge.png",
                        "cloak charge.png"
                };
                case CLOAK_ACTIVE -> new String[]{
                        "cloak_mode_active.png",
                        "cloak mode active.png",
                        "cloak_active.png",
                        "cloak active.png"
                };
            };
            for (String candidate : candidates) {
                File file = new File(HUD_PANEL_DIR, candidate);
                if (!file.isFile()) continue;
                try {
                    return ImageIO.read(file);
                } catch (IOException ignored) {
                }
            }
            return null;
        }
    }

    private static final class ShopHullOffer {
        final ShipRole role;
        final int cost;
        final int requiredTier;
        final ShopHullCategory category;
        final String tagLine;
        final String detail;

        ShopHullOffer(ShipRole role, int cost, int requiredTier,
                      ShopHullCategory category, String tagLine, String detail) {
            this.role = role;
            this.cost = cost;
            this.requiredTier = requiredTier;
            this.category = (category == null) ? ShopHullCategory.ESCORT : category;
            this.tagLine = (tagLine == null || tagLine.isBlank()) ? "Combat hull" : tagLine;
            this.detail = (detail == null || detail.isBlank()) ? "Ready for refit." : detail;
        }
    }

    private static final int SHOP_UPGRADE_ENERGY_BOLT = 1;
    private static final int SHOP_UPGRADE_BEAM_BOLT = 2;
    private static final int SHOP_UPGRADE_HULL = 3;
    private static final int SHOP_UPGRADE_SHIELD = 4;
    private static final int SHOP_UPGRADE_GUN = 5;
    private static final int SHOP_UPGRADE_MISSILE = 6;
    private static final int SHOP_UPGRADE_CIWS = 7;
    private static final int SHOP_HULL_PAGE_SIZE = 8;

    private static final ShopHullOffer[] SHOP_HULL_OFFERS = new ShopHullOffer[]{
            new ShopHullOffer(ShipRole.PATROL, 0, 0, ShopHullCategory.ESCORT,
                    "Fast scout and skirmish frame",
                    "Long sensor reach and clean entry-point mobility."),
            new ShopHullOffer(ShipRole.PICKET, 180, 0, ShopHullCategory.ESCORT,
                    "Interceptor picket hull",
                    "Ambush pursuit frame with stronger standoff control."),
            new ShopHullOffer(ShipRole.FRIGATE, 0, 0, ShopHullCategory.ESCORT,
                    "Balanced fleet-standard frigate",
                    "Reliable baseline hull for general combat upgrades."),
            new ShopHullOffer(ShipRole.MINER, 160, 0, ShopHullCategory.ESCORT,
                    "Ore extraction utility hull",
                    "Low-cost mining ship that keeps the fleet stores flowing."),
            new ShopHullOffer(ShipRole.ARTILLERY_SHIP, 320, 0, ShopHullCategory.ESCORT,
                    "Budget long-range gun platform",
                    "Cheap reach for keeping pressure on distant targets."),
            new ShopHullOffer(ShipRole.MISSILE_BOAT, 300, 0, ShopHullCategory.ESCORT,
                    "Compact launcher skirmisher",
                    "Punches above size with burst missile pressure."),
            new ShopHullOffer(ShipRole.CIWS_CORVETTE, 250, 0, ShopHullCategory.ESCORT,
                    "Escort flak and defense net",
                    "Best early frame for anti-missile and anti-craft screens."),

            new ShopHullOffer(ShipRole.LIGHT_CRUISER, 700, 1, ShopHullCategory.LINE,
                    "Entry heavy line hull",
                    "First true line-warship with room for upgrades."),
            new ShopHullOffer(ShipRole.MEDIUM_CRUISER, 950, 1, ShopHullCategory.LINE,
                    "Versatile strike cruiser",
                    "Flexible midline ship with stronger staying power."),
            new ShopHullOffer(ShipRole.CRUISER, 1100, 1, ShopHullCategory.LINE,
                    "Missile cruiser",
                    "Long-range salvo ship with sustained rack pressure."),
            new ShopHullOffer(ShipRole.HAULER, 260, 1, ShopHullCategory.LINE,
                    "Bulk logistics hauler",
                    "Moves ore off miners fast and keeps the command ship topped up."),
            new ShopHullOffer(ShipRole.BATTLECRUISER, 1600, 2, ShopHullCategory.LINE,
                    "Fast capital hunter",
                    "Aggressive heavy hull for breakthrough pushes."),
            new ShopHullOffer(ShipRole.BATTLESHIP, 2200, 2, ShopHullCategory.LINE,
                    "Line-breaking heavy capital",
                    "Dense broadside hull built to win frontal exchanges."),
            new ShopHullOffer(ShipRole.STEALTH_SHIP, 1200, 2, ShopHullCategory.LINE,
                    "Raid and ambush specialist",
                    "High-risk strike hull for flanks and precision kills."),

            new ShopHullOffer(ShipRole.DREADNOUGHT, 3200, 3, ShopHullCategory.CAPITAL,
                    "Siege capital",
                    "Slow, hard-killing warship for attrition fights."),
            new ShopHullOffer(ShipRole.CARRIER, 2800, 3, ShopHullCategory.CAPITAL,
                    "Strike-carrier capital",
                    "Launches and sustains wings while anchoring the line."),
            new ShopHullOffer(ShipRole.DRONE_CARRIER, 3000, 3, ShopHullCategory.CAPITAL,
                    "Drone warfare carrier",
                    "Swarm-focused carrier with strong automated pressure."),
            new ShopHullOffer(ShipRole.SUPERSHIP, 5200, 3, ShopHullCategory.CAPITAL,
                    "Elite super-capital",
                    "Prestige hull with flagship-grade lethality."),

            new ShopHullOffer(ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.TRANSPORT.roleLabel(),
                    TitanArchetype.TRANSPORT.commandBonusSummary()),
            new ShopHullOffer(ShipRole.BULWARK_TITAN, TitanArchetype.BULWARK.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.BULWARK.roleLabel(),
                    TitanArchetype.BULWARK.commandBonusSummary()),
            new ShopHullOffer(ShipRole.CARRIER_SUPPORT_TITAN, TitanArchetype.CARRIER_SUPPORT.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.CARRIER_SUPPORT.roleLabel(),
                    TitanArchetype.CARRIER_SUPPORT.commandBonusSummary()),
            new ShopHullOffer(ShipRole.VANGUARD_TITAN, TitanArchetype.VANGUARD.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.VANGUARD.roleLabel(),
                    TitanArchetype.VANGUARD.commandBonusSummary()),
            new ShopHullOffer(ShipRole.INTERDICTION_TITAN, TitanArchetype.INTERDICTION.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.INTERDICTION.roleLabel(),
                    TitanArchetype.INTERDICTION.commandBonusSummary()),
            new ShopHullOffer(ShipRole.COMMAND_INTEL_TITAN, TitanArchetype.COMMAND_INTEL.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.COMMAND_INTEL.roleLabel(),
                    TitanArchetype.COMMAND_INTEL.commandBonusSummary()),
            new ShopHullOffer(ShipRole.BOARDING_RECOVERY_TITAN, TitanArchetype.BOARDING_RECOVERY.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.BOARDING_RECOVERY.roleLabel(),
                    TitanArchetype.BOARDING_RECOVERY.commandBonusSummary()),
            new ShopHullOffer(ShipRole.ARTILLERY_TITAN, TitanArchetype.ARTILLERY.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.ARTILLERY.roleLabel(),
                    TitanArchetype.ARTILLERY.commandBonusSummary()),
            new ShopHullOffer(ShipRole.SHIELD_BASTION_TITAN, TitanArchetype.SHIELD_BASTION.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.SHIELD_BASTION.roleLabel(),
                    TitanArchetype.SHIELD_BASTION.commandBonusSummary()),
            new ShopHullOffer(ShipRole.FLEET_TELEPORTER_TITAN, TitanArchetype.FLEET_TELEPORTER.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.FLEET_TELEPORTER.roleLabel(),
                    TitanArchetype.FLEET_TELEPORTER.commandBonusSummary()),
            new ShopHullOffer(ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN, TitanArchetype.ELITE_SUPERSHIP_COMMAND.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.ELITE_SUPERSHIP_COMMAND.roleLabel(),
                    TitanArchetype.ELITE_SUPERSHIP_COMMAND.commandBonusSummary()),
            new ShopHullOffer(ShipRole.ELITE_REINFORCEMENTS_TITAN, TitanArchetype.ELITE_REINFORCEMENTS.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.ELITE_REINFORCEMENTS.roleLabel(),
                    TitanArchetype.ELITE_REINFORCEMENTS.commandBonusSummary()),
            new ShopHullOffer(ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.MOBILE_STATION.roleLabel(),
                    TitanArchetype.MOBILE_STATION.commandBonusSummary() + " Picket-launch berths replace small-craft decks."),
            new ShopHullOffer(ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.HYPERWEAPON.roleLabel(),
                    TitanArchetype.HYPERWEAPON.commandBonusSummary()),
            new ShopHullOffer(ShipRole.MOTHERSHIP, 7200, 3, ShopHullCategory.TITAN,
                    "Fleet anchor and command citadel",
                    "Grand carrier, repair harbor, apex flagship, and picket-launch citadel.")
    };

    public static Rectangle getStrategicMapRect(int viewW, int viewH) {
        int padX = Math.max(10, viewW / 90);
        int padY = Math.max(10, viewH / 85);
        return new Rectangle(padX, padY, Math.max(320, viewW - padX * 2), Math.max(240, viewH - padY * 2));
    }

    public static Rectangle getStrategicMapRect(int viewW, int viewH, boolean galaxyMode) {
        if (!galaxyMode) return getStrategicMapRect(viewW, viewH);
        int padX = Math.max(14, viewW / 80);
        int padY = Math.max(12, viewH / 70);
        return new Rectangle(padX, padY, Math.max(320, viewW - padX * 2), Math.max(240, viewH - padY * 2));
    }

    public static Rectangle getStrategicMapInnerRect(int viewW, int viewH) {
        Rectangle r = getStrategicMapRect(viewW, viewH);
        int pad = 18;
        int gutter = 16;
        Rectangle sidebar = getStrategicMapSidebarRect(viewW, viewH);
        int width = Math.max(220, sidebar.x - gutter - (r.x + pad));
        return new Rectangle(r.x + pad, r.y + 44, width, r.height - 60);
    }

    public static Rectangle getStrategicMapInnerRect(int viewW, int viewH, boolean galaxyMode) {
        if (!galaxyMode) return getStrategicMapInnerRect(viewW, viewH);
        Rectangle r = getStrategicMapRect(viewW, viewH, true);
        Rectangle left = getStrategicMapLeftPanelRect(viewW, viewH, true);
        Rectangle sidebar = getStrategicMapSidebarRect(viewW, viewH, true);
        int pad = Math.max(16, r.width / 90);
        int gutter = Math.max(14, r.width / 120);
        int topPad = 42;
        int bottomPad = 34;
        int mapX = left.x + left.width + gutter;
        int width = Math.max(320, sidebar.x - gutter - mapX);
        return new Rectangle(mapX, r.y + topPad, width, Math.max(180, r.height - topPad - bottomPad));
    }

    public static Rectangle getStrategicMapSidebarRect(int viewW, int viewH) {
        Rectangle r = getStrategicMapRect(viewW, viewH);
        int pad = 18;
        int gutter = 16;
        int w = Math.min(420, Math.max(280, r.width / 3));
        int x = r.x + r.width - pad - w;
        int y = r.y + 44;
        int h = r.height - 60;
        return new Rectangle(x, y, w, h);
    }

    public static Rectangle getStrategicMapSidebarRect(int viewW, int viewH, boolean galaxyMode) {
        if (!galaxyMode) return getStrategicMapSidebarRect(viewW, viewH);
        Rectangle r = getStrategicMapRect(viewW, viewH, true);
        int pad = Math.max(14, r.width / 100);
        int gutter = Math.max(14, r.width / 120);
        int w = Math.min(430, Math.max(320, r.width / 4));
        int x = r.x + r.width - pad - w;
        int y = r.y + 42;
        int h = Math.max(180, r.height - 76);
        return new Rectangle(x, y, w, h);
    }

    public static Rectangle getStrategicMapLeftPanelRect(int viewW, int viewH, boolean galaxyMode) {
        Rectangle r = getStrategicMapRect(viewW, viewH, galaxyMode);
        int pad = Math.max(14, r.width / 100);
        int w = Math.min(320, Math.max(250, r.width / 5));
        int x = r.x + pad;
        int y = r.y + 42;
        int h = Math.max(180, r.height - 76);
        return new Rectangle(x, y, w, h);
    }

    public static Rectangle getShopOverlayRect(int viewW, int viewH) {
        int padX = 36;
        int padY = 44;
        int w = Math.min(1180, Math.max(820, viewW - padX * 2));
        int h = Math.min(640, Math.max(560, viewH - padY * 2));
        int x = (viewW - w) / 2;
        int y = Math.max(28, (viewH - h) / 2);
        return new Rectangle(x, y, w, h);
    }

    public static int shopHullPageCount(ShopHullCategory category) {
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        int count = 0;
        for (ShopHullOffer offer : SHOP_HULL_OFFERS) {
            if (offer != null && offer.category == resolved) count++;
        }
        return Math.max(1, (count + SHOP_HULL_PAGE_SIZE - 1) / SHOP_HULL_PAGE_SIZE);
    }

    public static int clampShopHullPage(ShopHullCategory category, int page) {
        return MathUtil.clamp(page, 0, shopHullPageCount(category) - 1);
    }

    public static int shopHullPageForRole(ShipRole role) {
        ShopHullCategory category = ShopHullCategory.forRole(role);
        int slot = 0;
        for (ShopHullOffer offer : SHOP_HULL_OFFERS) {
            if (offer == null || offer.category != category) continue;
            if (offer.role == role) return slot / SHOP_HULL_PAGE_SIZE;
            slot++;
        }
        return 0;
    }

    public static ShopClickTarget shopClickTargetAt(Player player, UiState ui, int credits, int hangarTier,
                                                    int viewW, int viewH, int mouseX, int mouseY) {
        if (player == null) return null;
        Rectangle panel = getShopOverlayRect(viewW, viewH);
        if (!panel.contains(mouseX, mouseY)) return null;
        ShopHullCategory category = (ui == null || ui.shopHullCategory == null)
                ? ShopHullCategory.forRole(player.role)
                : ui.shopHullCategory;
        int page = (ui == null) ? 0 : clampShopHullPage(category, ui.shopHullPage);

        for (int i = 0; i < 7; i++) {
            Rectangle button = getShopCardButtonRect(getShopUpgradeCardRect(panel, i));
            if (button.contains(mouseX, mouseY)) {
                return new ShopClickTarget(ShopClickTarget.Kind.UPGRADE, i + 1, null, null, 0);
            }
        }
        for (ShopHullCategory candidate : ShopHullCategory.values()) {
            Rectangle tab = getShopHullCategoryTabRect(panel, candidate);
            if (tab.contains(mouseX, mouseY)) {
                return new ShopClickTarget(ShopClickTarget.Kind.CATEGORY, 0, null, candidate, 0);
            }
        }
        if (shopHullPageCount(category) > 1) {
            Rectangle prev = getShopHullPageButtonRect(panel, false);
            Rectangle next = getShopHullPageButtonRect(panel, true);
            if (prev.contains(mouseX, mouseY)) {
                return new ShopClickTarget(ShopClickTarget.Kind.PAGE, 0, null, null, -1);
            }
            if (next.contains(mouseX, mouseY)) {
                return new ShopClickTarget(ShopClickTarget.Kind.PAGE, 0, null, null, 1);
            }
        }

        for (int slot = 0; slot < SHOP_HULL_PAGE_SIZE; slot++) {
            ShopHullOffer offer = shopHullOfferAt(category, page, slot);
            if (offer == null) continue;
            Rectangle button = getShopCardButtonRect(getShopHullCardRect(panel, slot));
            if (button.contains(mouseX, mouseY)) {
                return new ShopClickTarget(ShopClickTarget.Kind.HULL, 0, offer.role, null, 0);
            }
        }
        return null;
    }

    private static Rectangle getFleetOverlayModeTabRect(Rectangle panel, boolean refitTab) {
        if (panel == null) return new Rectangle();
        int w = 124;
        int h = 22;
        int gap = 8;
        int y = panel.y + 10;
        int refitX = panel.x + panel.width - 22 - w;
        int commissionX = refitX - gap - w;
        int x = refitTab ? refitX : commissionX;
        return new Rectangle(x, y, w, h);
    }

    private static void drawFleetOverlayModeTabs(Graphics2D g2, Rectangle panel, boolean refitActive) {
        if (g2 == null || panel == null) return;
        Rectangle commission = getFleetOverlayModeTabRect(panel, false);
        Rectangle refit = getFleetOverlayModeTabRect(panel, true);
        drawFleetOverlayModeTab(g2, commission, "COMMISSION", !refitActive, new Color(118, 180, 255));
        drawFleetOverlayModeTab(g2, refit, "REFIT", refitActive, new Color(255, 206, 122));
    }

    private static void drawFleetOverlayModeTab(Graphics2D g2, Rectangle rect, String label, boolean active, Color accent) {
        if (g2 == null || rect == null || label == null) return;
        Color base = (accent == null) ? new Color(118, 180, 255) : accent;
        g2.setColor(active ? new Color(22, 28, 42, 220) : new Color(18, 22, 34, 188));
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), active ? 170 : 78));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(label)) / 2;
        int ty = rect.y + (rect.height + fm.getAscent() - fm.getDescent()) / 2 - 1;
        g2.setColor(active ? new Color(248, 250, 255, 238) : new Color(188, 204, 224, 192));
        g2.drawString(label, tx, ty);
    }

    private static Rectangle getFleetCapUpgradeButtonRect(Rectangle panel, int idx) {
        if (panel == null) return new Rectangle();
        int buttonW = 240;
        int buttonH = 28;
        int gap = 16;
        int x = panel.x + 22 + idx * (buttonW + gap);
        int y = panel.y + 116;
        return new Rectangle(x, y, buttonW, buttonH);
    }

    private static Rectangle getFleetEditorShipListRect(Rectangle panel) {
        if (panel == null) return new Rectangle();
        int x = panel.x + 22;
        int y = panel.y + 72;
        int w = 260;
        int h = panel.height - 120;
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle getFleetEditorEditorRect(Rectangle panel, Rectangle shipList) {
        if (panel == null || shipList == null) return new Rectangle();
        int x = shipList.x + shipList.width + 18;
        int y = shipList.y;
        int w = (panel.x + panel.width - 22) - x;
        int h = shipList.height;
        return new Rectangle(x, y, Math.max(240, w), h);
    }

    private static List<Ship> fleetEditorShips(GameContext ctx) {
        ArrayList<Ship> out = new ArrayList<>();
        if (ctx == null || ctx.ships == null) return out;
        for (Ship s : ctx.ships) {
            if (!CampaignSystem.isFleetSelectionCandidate(ctx, s)) continue;
            out.add(s);
        }
        // Stable ordering: flagship first, then by role + id.
        out.sort((a, b) -> {
            if (a == ctx.player && b != ctx.player) return -1;
            if (b == ctx.player && a != ctx.player) return 1;
            int ra = (a == null || a.role == null) ? 0 : a.role.ordinal();
            int rb = (b == null || b.role == null) ? 0 : b.role.ordinal();
            if (ra != rb) return Integer.compare(ra, rb);
            int ia = (a == null) ? 0 : a.id;
            int ib = (b == null) ? 0 : b.id;
            return Integer.compare(ia, ib);
        });
        return out;
    }

    public static FleetOverlayClickTarget fleetOverlayClickTargetAt(GameContext ctx, UiState ui,
                                                                    int viewW, int viewH, int mouseX, int mouseY) {
        Rectangle panel = getShopOverlayRect(viewW, viewH);
        if (!panel.contains(mouseX, mouseY)) return null;

        Rectangle commissionTab = getFleetOverlayModeTabRect(panel, false);
        Rectangle refitTab = getFleetOverlayModeTabRect(panel, true);
        if (commissionTab.contains(mouseX, mouseY)) {
            return new FleetOverlayClickTarget(FleetOverlayClickTarget.Kind.MODE_COMMISSION, -1, -1, null);
        }
        if (refitTab.contains(mouseX, mouseY)) {
            return new FleetOverlayClickTarget(FleetOverlayClickTarget.Kind.MODE_REFIT, -1, -1, null);
        }

        if (ui != null && !ui.fleetRefitMode) {
        }

        if (ui == null || !ui.fleetRefitMode) return null;
        if (ctx == null || ctx.ships == null) return null;

        Rectangle shipList = getFleetEditorShipListRect(panel);
        Rectangle editor = getFleetEditorEditorRect(panel, shipList);

        // Ship selection list (left)
        List<Ship> ships = fleetEditorShips(ctx);
        int headerH = 22;
        int rowH = 18;
        int startY = shipList.y + headerH + 4;
        int maxRows = Math.max(0, (shipList.height - headerH - 10) / rowH);
        for (int i = 0; i < ships.size() && i < maxRows; i++) {
            int ry = startY + i * rowH;
            Rectangle row = new Rectangle(shipList.x + 6, ry - rowH + 4, shipList.width - 12, rowH);
            if (row.contains(mouseX, mouseY)) {
                Ship s = ships.get(i);
                if (s != null) return new FleetOverlayClickTarget(FleetOverlayClickTarget.Kind.SELECT_SHIP, s.id, -1, null);
            }
        }

        // Selected ship context for turret clicks (right)
        Ship selected = null;
        if (ui.fleetSelectedShipId > 0) {
            for (Ship s : ships) {
                if (s != null && s.id == ui.fleetSelectedShipId) {
                    selected = s;
                    break;
                }
            }
        }
        if (selected == null && !ships.isEmpty()) selected = ships.get(0);
        if (selected == null) return null;

        int editorHeaderH = 72;
        int controlsH = 92;
        int turretRowH = 18;
        Rectangle turretList = new Rectangle(
                editor.x + 6,
                editor.y + editorHeaderH,
                editor.width - 12,
                Math.max(80, editor.height - editorHeaderH - controlsH - 8));
        Rectangle controls = new Rectangle(
                editor.x + 6,
                turretList.y + turretList.height + 8,
                editor.width - 12,
                Math.max(60, editor.y + editor.height - (turretList.y + turretList.height + 8)));

        // Turret row selection
        if (turretList.contains(mouseX, mouseY)) {
            int idx = (mouseY - (turretList.y + 16)) / turretRowH;
            if (idx >= 0 && idx < selected.turrets.size()) {
                return new FleetOverlayClickTarget(FleetOverlayClickTarget.Kind.SELECT_TURRET, selected.id, idx, null);
            }
        }

        // Controls (swap + missile role buttons)
        int ti = ui.fleetSelectedTurretIndex;
        if (ti >= 0 && ti < selected.turrets.size()) {
            Turret t = selected.turrets.get(ti);
            if (t != null && controls.contains(mouseX, mouseY)) {
                Rectangle swap = new Rectangle(controls.x + 8, controls.y + 24, 136, 24);
                if (swap.contains(mouseX, mouseY)) {
                    boolean toMissile = (t.kind == Turret.Kind.GUN);
                    return new FleetOverlayClickTarget(
                            toMissile ? FleetOverlayClickTarget.Kind.SWAP_TO_MISSILE : FleetOverlayClickTarget.Kind.SWAP_TO_GUN,
                            selected.id, ti, null);
                }

                if (t.kind == Turret.Kind.MISSILE) {
                    int bx = controls.x + 158;
                    int by = controls.y + 24;
                    int bw = 110;
                    int bh = 24;
                    int gap = 8;
                    Turret.MissileRole[] roles = new Turret.MissileRole[]{
                            Turret.MissileRole.INTERCEPT,
                            Turret.MissileRole.ANTI_LIGHT,
                            Turret.MissileRole.ANTI_MEDIUM,
                            Turret.MissileRole.ANTI_HEAVY
                    };
                    for (int i = 0; i < roles.length; i++) {
                        Rectangle b = new Rectangle(bx + i * (bw + gap), by, bw, bh);
                        if (b.contains(mouseX, mouseY)) {
                            return new FleetOverlayClickTarget(FleetOverlayClickTarget.Kind.SET_MISSILE_ROLE, selected.id, ti, roles[i]);
                        }
                    }
                }
            }
        }

        return null;
    }

    public static Rectangle getCoreMenuBarRect(int viewW, int viewH) {
        int margin = 10;
        int h = 42;
        int maxW = 740;
        int avail = Math.max(220, viewW - margin * 2);
        int w = Math.min(maxW, avail);
        int x = (viewW - w) / 2;
        int y = viewH - h - margin;
        return new Rectangle(x, y, w, h);
    }

    public static Rectangle getCoreMenuButtonRect(int viewW, int viewH, int index) {
        if (index < 0 || index >= CORE_MENU_LABELS.length) return new Rectangle();
        Rectangle bar = getCoreMenuBarRect(viewW, viewH);
        int pad = 8;
        int gap = 6;
        int innerW = Math.max(1, bar.width - pad * 2);
        int cellW = (innerW - gap * (CORE_MENU_LABELS.length - 1)) / CORE_MENU_LABELS.length;
        int x = bar.x + pad + index * (cellW + gap);
        int y = bar.y + 6;
        int w = Math.max(24, cellW);
        int h = Math.max(18, bar.height - 12);
        return new Rectangle(x, y, w, h);
    }

    public static int coreMenuButtonAt(int viewW, int viewH, int mouseX, int mouseY) {
        for (int i = 0; i < CORE_MENU_LABELS.length; i++) {
            Rectangle r = getCoreMenuButtonRect(viewW, viewH, i);
            if (r.contains(mouseX, mouseY)) return i;
        }
        return -1;
    }

    private static String coreMenuLabel(GameContext ctx, int index) {
        if (index < 0 || index >= CORE_MENU_LABELS.length) return "";
        if (CampaignSystem.isCampaignActive(ctx)) {
            return switch (index) {
                case 0 -> "FLEET";
                case 1 -> "UPGRADE";
                case 4 -> "HELP";
                case 5 -> "SAFE EXIT";
                default -> CORE_MENU_LABELS[index];
            };
        }
        if (index == 4) return "HELP";
        return CORE_MENU_LABELS[index];
    }

    public static HoverTooltip hoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null) return null;
        HoverTooltip tooltip = null;
        if (ctx.ui.shopOpen) tooltip = shopHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.baseMenuOpen) tooltip = baseUpgradeHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.powerManagementOpen) tooltip = powerManagementHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.flightDeckOpen) tooltip = flightDeckHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.crewStationsOpen) tooltip = crewStationsHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (tooltip != null) return tooltip;

        tooltip = campaignMapHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (tooltip != null) return tooltip;

        tooltip = objectiveCardHoverTooltipAt(ctx, mouseX, mouseY);
        if (tooltip != null) return tooltip;

        tooltip = coreMenuHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (tooltip != null) return tooltip;
        if (ctx.ui.hasBlockingOverlay()) return null;
        if (ctx.ui.xrayHoveredRoom != null) return null;
        return shipHoverTooltipAt(ctx);
    }

    private static HoverTooltip campaignMapHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null) return null;
        if (CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            return galaxyMapHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        }
        if (ctx.ui.mapOpen) {
            return tacticalMapHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        }
        return null;
    }

    private static HoverTooltip galaxyMapHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null) return null;
        if (ctx.ui.campaignActionConfirm.active) {
            Rectangle overlay = campaignActionConfirmOverlayRect(viewW, viewH);
            Rectangle confirmRect = new Rectangle(overlay.x + 18, overlay.y + overlay.height - 38, 122, 22);
            Rectangle closeRect = new Rectangle(overlay.x + overlay.width - 92, overlay.y + overlay.height - 38, 78, 22);
            if (confirmRect.contains(mouseX, mouseY)) {
                return new HoverTooltip("campaign:confirm", "Confirm Action",
                        "Commit the selected strategic command and accept its listed cost, risk, and consequences.");
            }
            if (closeRect.contains(mouseX, mouseY)) {
                return new HoverTooltip("campaign:confirm:back", "Back",
                        "Cancel this confirmation prompt and return to the command panel.");
            }
            return null;
        }
        if (ctx.ui.campaignHubMenu.active) {
            Rectangle overlay = campaignHubOverlayRect(viewW, viewH);
            Rectangle confirmRect = new Rectangle(overlay.x + 18, overlay.y + overlay.height - 38, 122, 22);
            Rectangle closeRect = new Rectangle(overlay.x + overlay.width - 92, overlay.y + overlay.height - 38, 78, 22);
            if (confirmRect.contains(mouseX, mouseY)) {
                return new HoverTooltip("campaign:service:confirm", "Confirm Service",
                        "Execute the selected hub service and spend the listed resources immediately.");
            }
            if (closeRect.contains(mouseX, mouseY)) {
                return new HoverTooltip("campaign:service:back", "Back",
                        "Close the service prompt without spending resources.");
            }
            return null;
        }
        Rectangle panelRect = getStrategicMapSidebarRect(viewW, viewH, true);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Rectangle[] tabRects = galaxyCommandTabRects(inner.x, inner.y + 2, inner.width);
        UiState.CampaignCommandTab[] tabs = UiState.CampaignCommandTab.values();
        for (int i = 0; i < tabs.length && i < tabRects.length; i++) {
            if (tabRects[i].contains(mouseX, mouseY)) {
                return new HoverTooltip(
                        "campaign:tab:" + tabs[i].name(),
                        tabs[i].label(),
                        switch (tabs[i]) {
                            case NAV -> "Navigation, destination readouts, route pressure, and movement orders.";
                            case FLEET -> "Fleet posture, detachment control, readiness, and support allocation.";
                            case RESOURCES -> "Fuel, supplies, ammo, salvage, ore, and route sustainability.";
                            case STRIKES -> "Recon quality, strike readiness, target windows, and launch authority.";
                        });
            }
        }
        if (ctx.ui.campaignCommandTab == UiState.CampaignCommandTab.FLEET) {
            CampaignSystem.CampaignFleetRosterEntry rosterEntry = galaxyFleetRosterEntryAt(ctx, panelRect, mouseX, mouseY);
            if (rosterEntry != null) {
                return new HoverTooltip(
                        "campaign:fleet:roster:" + rosterEntry.slotId,
                        rosterEntry.name,
                        rosterEntry.roleLabel + "  |  Ore " + rosterEntry.oreCost
                                + "\n" + rosterEntry.readinessLabel
                                + "\n" + rosterEntry.cargoLabel + "  |  " + rosterEntry.forceLabel
                                + "\n" + rosterEntry.groupLabel + "  |  " + rosterEntry.commitmentLabel
                                + "\nClick to focus. Use command buttons to refit where safe, commit, reserve, hold, or assign groups.");
            }
        }
        for (CampaignSystem.CampaignAction action : galaxyCommandActions(ctx)) {
            if (action == null || action.id == null || action.id.isBlank()) continue;
            Rectangle rect = galaxyActionRect(ctx, panelRect, action.id);
            if (rect != null && rect.contains(mouseX, mouseY)) {
                return campaignActionTooltip("campaign:action:" + action.id, action);
            }
        }
        HoverTooltip mapTooltip = galaxyMapSurfaceHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (mapTooltip != null) return mapTooltip;
        return null;
    }

    private static HoverTooltip galaxyMapSurfaceHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null || !CampaignSystem.isStrategicGalaxyMapMode(ctx)) return null;
        Rectangle map = getStrategicMapInnerRect(viewW, viewH, true);
        if (!map.contains(mouseX, mouseY)) return null;
        double worldMinX = UISystem.strategicMapWorldMinX(ctx);
        double worldMinY = UISystem.strategicMapWorldMinY(ctx);
        double worldW = UISystem.strategicMapViewWidth(ctx);
        double worldH = UISystem.strategicMapViewHeight(ctx);
        if (worldW <= 1e-6 || worldH <= 1e-6) return null;
        double wx = worldMinX + ((mouseX - map.x) / (double) Math.max(1, map.width)) * worldW;
        double wy = worldMinY + ((mouseY - map.y) / (double) Math.max(1, map.height)) * worldH;
        double markerRadius = Math.max(90.0, worldW * 0.025);

        CampaignSystem.CampaignSupportMarker support = CampaignSystem.nearestSupportMarker(ctx, wx, wy, markerRadius);
        if (support != null) {
            String body = defaultIfBlank(support.subtitle, "No detail available")
                    + "\nOwner: " + CampaignSystem.supportMarkerOwnerReadout(support)
                    + "\nRole: " + CampaignSystem.supportMarkerRoleReadout(support)
                    + "\n" + CampaignSystem.supportMarkerStrategicValueReadout(support);
            return new HoverTooltip("campaign:map:support:" + support.type + ":" + support.label, support.label, body);
        }

        CampaignSystem.CampaignObjectiveMarker objective = CampaignSystem.nearestObjectiveMarker(ctx, wx, wy, markerRadius);
        if (objective != null) {
            String body = defaultIfBlank(objective.subtitle, "No objective detail available")
                    + "\nType: " + objective.type.name().replace('_', ' ')
                    + "\nStrategic value: objective pressure and theater control";
            return new HoverTooltip("campaign:map:objective:" + objective.type + ":" + objective.label, objective.label, body);
        }

        CampaignSystem.CampaignLandmark landmark = CampaignSystem.nearestStrategicLandmark(ctx, wx, wy, markerRadius);
        if (landmark != null) {
            String body = defaultIfBlank(landmark.subtitle, "No landmark detail available")
                    + "\nType: " + landmark.type.name().replace('_', ' ')
                    + "\nStrategic value: route and theater orientation";
            return new HoverTooltip("campaign:map:landmark:" + landmark.type + ":" + landmark.label, landmark.label, body);
        }
        return null;
    }

    private static HoverTooltip tacticalMapHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        Rectangle panelRect = getStrategicMapSidebarRect(viewW, viewH, false);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Rectangle[] tabRects = tacticalMapTabRects(inner.x, inner.y + 2, inner.width);
        UiState.TacticalMapTab[] tabs = UiState.TacticalMapTab.values();
        for (int i = 0; i < tabs.length && i < tabRects.length; i++) {
            if (tabRects[i].contains(mouseX, mouseY)) {
                return new HoverTooltip(
                        "tactical:tab:" + tabs[i].name(),
                        tabs[i].label(),
                        switch (tabs[i]) {
                            case MISSION -> "Selected mission data, objectives, threats, and mission commands.";
                            case FLEET -> "Division readiness, hull condition, and local formation control.";
                            case RESOURCES -> "Combat-use resources, readiness stocks, and sustainment pressure.";
                            case CONTACTS -> "Nearby contacts, support leads, and trackable local signals.";
                            case STRIKES -> "Standoff torpedoes, sorties, atomics, and remote strike authority.";
                        });
            }
        }
        for (CampaignActionLayoutEntry entry : tacticalActionEntries(ctx, panelRect)) {
            if (entry != null && entry.rect != null && entry.rect.contains(mouseX, mouseY)) {
                return campaignActionTooltip("tactical:action:" + entry.action.id, entry.action);
            }
        }
        return null;
    }

    private static HoverTooltip campaignActionTooltip(String key, CampaignSystem.CampaignAction action) {
        if (action == null) return null;
        StringBuilder body = new StringBuilder();
        String detail = defaultIfBlank(action.tooltip, action.shortDescription);
        if (!detail.isBlank()) body.append(detail);
        if (!action.enabled && action.disabledReason != null && !action.disabledReason.isBlank()) {
            if (body.length() > 0) body.append('\n');
            body.append("Disabled: ").append(action.disabledReason);
        }
        if (action.shortcut != null && !action.shortcut.isBlank()) {
            if (body.length() > 0) body.append('\n');
            body.append("Shortcut: ").append(action.shortcut);
        }
        return body.isEmpty() ? null : new HoverTooltip(key, action.label, body.toString());
    }

    public static void drawHoverTooltip(Graphics2D g2, UiState ui, int mouseX, int mouseY, int viewW, int viewH) {
        if (g2 == null || ui == null || !ui.hoverTooltipVisible || ui.hoverTooltipBody == null || ui.hoverTooltipBody.isBlank()) {
            return;
        }

        FontMetrics titleFm = g2.getFontMetrics(HOVER_TOOLTIP_TITLE_FONT);
        FontMetrics bodyFm = g2.getFontMetrics(HOVER_TOOLTIP_BODY_FONT);
        java.util.List<String> lines = wrapTooltipLines(bodyFm, ui.hoverTooltipBody, 320);
        int contentW = 0;
        if (ui.hoverTooltipTitle != null && !ui.hoverTooltipTitle.isBlank()) {
            contentW = Math.max(contentW, titleFm.stringWidth(ui.hoverTooltipTitle));
        }
        for (String line : lines) {
            contentW = Math.max(contentW, bodyFm.stringWidth(line));
        }
        int pad = 10;
        int width = Math.min(360, Math.max(150, contentW + pad * 2));
        int height = pad * 2 + lines.size() * bodyFm.getHeight() + 4;
        if (ui.hoverTooltipTitle != null && !ui.hoverTooltipTitle.isBlank()) {
            height += titleFm.getHeight() + 4;
        }

        int x = mouseX + 18;
        int y = mouseY + 18;
        if (x + width > viewW - 12) x = mouseX - width - 18;
        if (x < 12) x = 12;
        if (y + height > viewH - 12) y = mouseY - height - 18;
        if (y < 12) y = 12;

        if (!paintThemedHudFrame(g2, x, y, width, height,
                new Color(190, 222, 255, 170), ThemeArt.HUD_STANDARD_PANEL, 12)) {
            g2.setColor(new Color(4, 8, 16, 228));
            g2.fillRoundRect(x, y, width, height, 12, 12);
            g2.setColor(new Color(190, 222, 255, 170));
            g2.drawRoundRect(x, y, width, height, 12, 12);
        }

        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, width, height);
        pad = Math.max(8, Math.min(14, inner.x - x));

        int ty = inner.y + bodyFm.getAscent();
        if (ui.hoverTooltipTitle != null && !ui.hoverTooltipTitle.isBlank()) {
            g2.setFont(HOVER_TOOLTIP_TITLE_FONT);
            g2.setColor(new Color(246, 250, 255, 240));
            ty = inner.y + titleFm.getAscent();
            g2.drawString(ui.hoverTooltipTitle, inner.x, ty);
            ty += titleFm.getDescent() + 8;
            g2.setColor(new Color(155, 206, 255, 110));
            g2.drawLine(inner.x, ty - 2, inner.x + inner.width, ty - 2);
            ty += bodyFm.getAscent();
        }

        g2.setFont(HOVER_TOOLTIP_BODY_FONT);
        g2.setColor(new Color(224, 235, 248, 228));
        for (String line : lines) {
            g2.drawString(line, inner.x, ty);
            ty += bodyFm.getHeight();
        }
    }

    private static HoverTooltip coreMenuHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        int index = coreMenuButtonAt(viewW, viewH, mouseX, mouseY);
        if (index < 0) return null;
        boolean campaign = CampaignSystem.isCampaignActive(ctx);
        boolean fleetHub = CampaignSystem.isFleetHubSession(ctx);
        String body = switch (index) {
            case 0 -> campaign
                    ? (fleetHub
                        ? "Fleet hub implementation detail for between-sector commissioning and refit. Hotkey: TAB."
                        : "Persistent fleet management. Opens commissioning and refit controls for the active campaign fleet. Hotkey: TAB.")
                    : "Shop and loadout controls. Commission hulls, buy upgrades, and browse fleet bands. Hotkey: TAB.";
            case 1 -> campaign
                    ? (fleetHub
                        ? "Fleet upgrade console. Edit the selected hull, its turrets, and its upgrade track. Hotkey: B."
                        : "Command-ship upgrade console during live sectors. The campaign map remains on M. Hotkey: B.")
                    : "Base upgrade console. Spend credits and ore on fortification, shields, turret systems, mining, and hangar tier. Hotkey: B.";
            case 2 -> "Strategic map. Set waypoints and inspect the wider battlespace. Hotkey: M.";
            case 3 -> "Power routing. Rebalance propulsion, shields, tactical, sensors, engineering, and supercharge buses. Hotkey: O.";
            case 4 -> "Help and operations. Review controls, combat reference, Captain/Helm/Tactical/Engineering/Science automation, and voice mix. Hotkey: H.";
            case 5 -> campaign
                    ? (fleetHub
                        ? "Safe exit is only available during a live mission. In the fleet hub, use the normal menu exit."
                        : "Safely exit mission. Orders the flagship and escorting fleet to spool a warp, then returns to menu after the extraction snapshot saves ore and cargo.")
                    : "Safe campaign extraction is only available during Campaign Ops missions.";
            default -> "";
        };
        return body.isBlank() ? null : new HoverTooltip("core:" + index, coreMenuLabel(ctx, index), body);
    }

    private static HoverTooltip objectiveCardHoverTooltipAt(GameContext ctx, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null || ctx.ui.objectiveHoverRect == null) return null;
        if (!ctx.ui.objectiveHoverRect.contains(mouseX, mouseY)) return null;
        String body = ctx.ui.objectiveHoverBody;
        if (body == null || body.isBlank()) return null;
        String title = (ctx.ui.objectiveHoverTitle == null || ctx.ui.objectiveHoverTitle.isBlank())
                ? "OBJECTIVE"
                : ctx.ui.objectiveHoverTitle;
        return new HoverTooltip("hud:objective", title, body);
    }

    private static HoverTooltip shopHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null) return null;
        Rectangle panel = getShopOverlayRect(viewW, viewH);
        if (!panel.contains(mouseX, mouseY)) return null;
        HoverTooltip fleetTooltip = fleetOverlayHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (fleetTooltip != null) return fleetTooltip;
        Player player = ctx.player;
        UiState ui = ctx.ui;
        ShopHullCategory category = (ui == null || ui.shopHullCategory == null) ? ShopHullCategory.ESCORT : ui.shopHullCategory;
        int page = (ui == null) ? 0 : clampShopHullPage(category, ui.shopHullPage);

        for (int i = 0; i < 7; i++) {
            Rectangle card = getShopUpgradeCardRect(panel, i);
            if (!card.contains(mouseX, mouseY)) continue;
            return switch (i + 1) {
                case 1 -> new HoverTooltip("shop:upgrade:1", "Beam Bolt Primary (Stagger)",
                        "Standard beam-bolt battery with barrels cycling in sequence. Keeps the blue-team beam look while spreading shots across the gun mounts.");
                case 2 -> new HoverTooltip("shop:upgrade:2", "Beam Bolt Primary (Volley)",
                        "Synchronized beam-bolt package. All barrels fire together for heavier alpha strikes and longer-range pressure.");
                case 3 -> new HoverTooltip("shop:upgrade:3", "Hull Plating",
                        "Permanent plating upgrade for the current hull. Raises flagship hull integrity by 10 per level until that chassis reaches its plating cap.");
                case 4 -> new HoverTooltip("shop:upgrade:4", "Shield Array",
                        "Improves shield strength and regeneration on shield-capable hulls. No effect on hulls that do not mount shields.");
                case 5 -> new HoverTooltip("shop:upgrade:5", "Add Gun Turret",
                        "Adds another gun hardpoint if the current hull still has spare gun upgrade capacity. Useful for broadside and anti-light pressure.");
                case 6 -> new HoverTooltip("shop:upgrade:6", "Add Missile Rack",
                        "Adds another missile launcher if the hull can still take one. Best for burst damage, harassment, and long-range pressure.");
                case 7 -> new HoverTooltip("shop:upgrade:7", "Upgrade CIWS",
                        "Improves close-in defense quality, reach, and burst output on CIWS-capable hulls. Helps against missiles and strike craft.");
                default -> null;
            };
        }

        for (ShopHullCategory candidate : ShopHullCategory.values()) {
            Rectangle tab = getShopHullCategoryTabRect(panel, candidate);
            if (!tab.contains(mouseX, mouseY)) continue;
            return new HoverTooltip("shop:tab:" + candidate.name(), candidate.label(),
                    candidate.subtitle() + " Click to swap the hull bay to this band.");
        }

        if (shopHullPageCount(category) > 1) {
            if (getShopHullPageButtonRect(panel, false).contains(mouseX, mouseY)) {
                return new HoverTooltip("shop:page:prev", "Previous Page",
                        "Browse the previous page of " + category.label().toLowerCase(Locale.US) + " hull offers.");
            }
            if (getShopHullPageButtonRect(panel, true).contains(mouseX, mouseY)) {
                return new HoverTooltip("shop:page:next", "Next Page",
                        "Browse the next page of " + category.label().toLowerCase(Locale.US) + " hull offers.");
            }
        }

        for (int slot = 0; slot < SHOP_HULL_PAGE_SIZE; slot++) {
            ShopHullOffer offer = shopHullOfferAt(category, page, slot);
            if (offer == null) continue;
            Rectangle card = getShopHullCardRect(panel, slot);
            if (!card.contains(mouseX, mouseY)) continue;
            boolean campaignShop = CampaignSystem.usesPersistentFleetShop(ctx);
            int displayTier = campaignShop ? CampaignSystem.campaignRequiredTier(offer.role, offer.requiredTier) : offer.requiredTier;
            int oreCost = campaignShop ? CampaignSystem.campaignOreCost(offer.role, offer.cost, displayTier) : 0;
            String body = offer.tagLine + ". " + offer.detail
                    + " Required tier " + displayTier
                    + ", cost $" + offer.cost
                    + (campaignShop ? (" plus " + oreCost + " ore") : "")
                    + "."
                    + (campaignShop ? (" " + CampaignSystem.campaignCommissionRequirementsDetail(ctx, offer.role, offer.requiredTier)) : "");
            return new HoverTooltip("shop:hull:" + offer.role.name(), shopRoleTitle(offer.role), body);
        }
        return null;
    }

    private static HoverTooltip fleetOverlayHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null) return null;
        Rectangle panel = getShopOverlayRect(viewW, viewH);
        Rectangle commissionTab = getFleetOverlayModeTabRect(panel, false);
        Rectangle refitTab = getFleetOverlayModeTabRect(panel, true);
        if (commissionTab.contains(mouseX, mouseY)) {
            return new HoverTooltip("fleet:mode:commission", "Commission",
                    "Open the fleet growth bay. Upgrade the flagship, browse persistent hull offers, and commission new ships into the campaign roster.");
        }
        if (refitTab.contains(mouseX, mouseY)) {
            return new HoverTooltip("fleet:mode:refit", "Refit",
                    "Open the refit bay. Select a ship, swap weapon mounts, and assign missile doctrine by turret.");
        }
        if (!ctx.ui.fleetRefitMode) return null;

        List<Ship> ships = fleetEditorShips(ctx);
        Rectangle shipList = getFleetEditorShipListRect(panel);
        int headerH = 22;
        int rowH = 18;
        int startY = shipList.y + headerH + 4;
        int maxRows = Math.max(0, (shipList.height - headerH - 10) / rowH);
        for (int i = 0; i < ships.size() && i < maxRows; i++) {
            Rectangle row = new Rectangle(shipList.x + 6, startY + i * rowH - rowH + 4, shipList.width - 12, rowH);
            if (!row.contains(mouseX, mouseY)) continue;
            Ship ship = ships.get(i);
            if (ship == null) break;
            String shipName = (ship.name == null || ship.name.isBlank()) ? shopRoleTitle(ship.role) : ship.name;
            return new HoverTooltip("fleet:ship:" + ship.id, shipName,
                    "Select this hull for refit. Current fit: " + ship.turrets.size() + " mounts, hull " + ship.hp + "/" + ship.hpMax + ".");
        }

        Ship selected = null;
        if (ctx.ui.fleetSelectedShipId > 0) {
            for (Ship ship : ships) {
                if (ship != null && ship.id == ctx.ui.fleetSelectedShipId) {
                    selected = ship;
                    break;
                }
            }
        }
        if (selected == null && !ships.isEmpty()) selected = ships.get(0);
        if (selected == null) return null;

        Rectangle editor = getFleetEditorEditorRect(panel, shipList);
        Rectangle turretList = new Rectangle(editor.x + 6, editor.y + 72, editor.width - 12, Math.max(80, editor.height - 72 - 92 - 8));
        if (turretList.contains(mouseX, mouseY)) {
            int idx = (mouseY - (turretList.y + 16)) / 18;
            if (idx >= 0 && idx < selected.turrets.size()) {
                Turret turret = selected.turrets.get(idx);
                if (turret != null) {
                    return new HoverTooltip("fleet:turret:" + idx, "Turret " + (idx + 1),
                            "Selected mount: " + turret.kind.name() + ". Click to edit this turret's mount type and doctrine.");
                }
            }
        }

        int ti = ctx.ui.fleetSelectedTurretIndex;
        if (ti >= 0 && ti < selected.turrets.size()) {
            Turret turret = selected.turrets.get(ti);
            Rectangle controls = new Rectangle(editor.x + 6, turretList.y + turretList.height + 8, editor.width - 12,
                    Math.max(60, editor.y + editor.height - (turretList.y + turretList.height + 8)));
            Rectangle swap = new Rectangle(controls.x + 8, controls.y + 24, 136, 24);
            if (swap.contains(mouseX, mouseY)) {
                boolean toMissile = turret != null && turret.kind == Turret.Kind.GUN;
                return new HoverTooltip("fleet:swap", toMissile ? "Swap To Missile" : "Swap To Gun",
                        toMissile
                                ? "Convert the selected mount into a missile rack so this hull can threaten targets at standoff range."
                                : "Convert the selected missile rack back into a gun mount for direct-fire pressure.");
            }
            if (turret != null && turret.kind == Turret.Kind.MISSILE) {
                Turret.MissileRole[] roles = {
                        Turret.MissileRole.INTERCEPT,
                        Turret.MissileRole.ANTI_LIGHT,
                        Turret.MissileRole.ANTI_MEDIUM,
                        Turret.MissileRole.ANTI_HEAVY
                };
                String[] labels = {"Intercept", "Anti-Light", "Anti-Medium", "Anti-Heavy"};
                String[] bodies = {
                        "Prefer missiles against incoming ordnance and small harassment craft.",
                        "Bias the rack toward fragile hulls, escorts, and fast skirmishers.",
                        "Bias the rack toward line ships and medium combatants.",
                        "Bias the rack toward capitals and the heaviest available targets."
                };
                int bx = controls.x + 158;
                for (int i = 0; i < roles.length; i++) {
                    Rectangle rect = new Rectangle(bx + i * 118, controls.y + 24, 110, 24);
                    if (rect.contains(mouseX, mouseY)) {
                        return new HoverTooltip("fleet:role:" + roles[i].name(), labels[i], bodies[i]);
                    }
                }
            }
        }
        return null;
    }

    private static HoverTooltip baseUpgradeHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        Rectangle panel = getBaseUpgradeOverlayRect(viewW);
        if (!panel.contains(mouseX, mouseY)) return null;
        int lineY = panel.y + 124;
        for (int i = 0; i < 5; i++) {
            Rectangle row = new Rectangle(panel.x + 18, lineY - 12 + i * 26, 480, 22);
            if (!row.contains(mouseX, mouseY)) continue;
            return switch (i) {
                case 0 -> new HoverTooltip("base:1", "Hull Fortification",
                        "Adds base hull durability so the dock can survive longer under direct attack. Purchased with credits and ore.");
                case 1 -> new HoverTooltip("base:2", "Shield Array",
                        "Improves base shield strength and regeneration, making the dock much harder to burst down.");
                case 2 -> new HoverTooltip("base:3", "Turret Systems",
                        "Upgrades the base defense battery, improving local firepower and orbital screen coverage.");
                case 3 -> new HoverTooltip("base:4", "Mining Ops",
                        "Improves mining throughput and ore sale value across the docked economy package.");
                case 4 -> new HoverTooltip("base:5", "Hangar Expansion",
                        "Raises available shipyard tier so stronger hull classes can be fielded. In campaign, tiers four and five unlock strategic capitals and apex titan fabrication.");
                default -> null;
            };
        }
        return null;
    }

    private static HoverTooltip powerManagementHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null) return null;
        int w = Math.min(700, viewW - 110);
        int h = 462;
        int x = (viewW - w) / 2;
        int y = Math.max(54, (viewH - h) / 2);
        if (!new Rectangle(x, y, w, h).contains(mouseX, mouseY)) return null;

        String[] titles = {"Propulsion", "Shield", "Tactical", "Sensor", "Engineering", "Supercharge"};
        String[] bodies = {
                "Controls acceleration, cruise speed, and repositioning headroom.",
                "Controls shield upkeep, recharge effectiveness, and staying power.",
                "Controls weapon cycle pressure and combat lethality.",
                "Controls sensor reach, lock quality, and target acquisition comfort.",
                "Controls repair throughput and ship survivability under load.",
                "Controls superweapon recharge tempo and burst readiness."
        };
        int rowY = y + 96;
        for (int i = 0; i < titles.length; i++) {
            Rectangle row = new Rectangle(x + 18, rowY + i * 28 - 2, w - 36, 22);
            if (!row.contains(mouseX, mouseY)) continue;
            return new HoverTooltip("power:" + i, titles[i],
                    bodies[i] + " Current allocation: " + (int) Math.round(ctx.player.powerBusFractions()[i] * 100.0) + "%.");
        }
        return new HoverTooltip("power:panel", "Power Management",
                "Hover a bus row to read its role. This panel lets you redistribute the flagship power budget and preview the resulting combat tradeoffs.");
    }

    private static HoverTooltip flightDeckHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null || !ctx.player.isCarrier) return null;
        int w = Math.min(820, viewW - 100);
        int h = 356;
        int x = (viewW - w) / 2;
        int y = Math.max(48, (viewH - h) / 2);
        if (!new Rectangle(x, y, w, h).contains(mouseX, mouseY)) return null;

        int slotGap = 12;
        int slotW = (w - 36 - slotGap * 4) / 5;
        int slotH = 132;
        int slotY = y + 108;
        for (int i = 0; i < 5; i++) {
            Rectangle slot = new Rectangle(x + 18 + i * (slotW + slotGap), slotY, slotW, slotH);
            if (!slot.contains(mouseX, mouseY)) continue;
            ShipRole role = ctx.player.flightDeckRoleAt(i);
            return new HoverTooltip("deck:" + i, "Squad " + (i + 1),
                    flightDeckRoleLabel(role) + ". " + flightDeckRoleDescription(role)
                            + ". Each slot launches as a 2-ship pair in deck order.");
        }
        return null;
    }

    private static HoverTooltip crewStationsHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null) return null;
        int w = Math.min(1010, viewW - 56);
        int h = 438;
        int x = (viewW - w) / 2;
        int y = Math.max(34, (viewH - h) / 2);
        if (!new Rectangle(x, y, w, h).contains(mouseX, mouseY)) return null;

        int portraitPaneW = 232;
        int panelX = x + 18 + portraitPaneW + 14;
        int panelW = x + w - panelX - 14;
        int tabX = panelX + 8;
        int tabY = y + 70;
        int tabGap = 8;
        int stationCount = GameContext.CrewStation.values().length;
        int tw = Math.max(104, (panelW - 16 - tabGap * (stationCount - 1)) / stationCount);
        for (GameContext.CrewStation station : GameContext.CrewStation.values()) {
            Rectangle tab = new Rectangle(tabX, tabY, tw, 24);
            if (tab.contains(mouseX, mouseY)) {
                String body = switch (station) {
                    case CAPTAIN -> "High-level battle posture. Captain directives push shipwide stance and allied fleet orders.";
                    case HELM -> "Movement and spacing control. Helm automation handles intercepts, orbiting, and evasive maneuvering.";
                    case TACTICAL -> "Weapons release and lock discipline. Tactical automation decides how aggressively the ship fires.";
                    case ENGINEERING -> "Power and damage-control authority. Engineering automation sets repair bias and overload posture.";
                    case SCIENCE -> "Sensors, locks, and electronic warfare. Science automation handles target acquisition and jamming.";
                };
                return new HoverTooltip("crew:" + station.name(), station.name(),
                        body + " Current mode: " + (UISystem.stationAutomation(ctx, station) ? "AI" : "MAN") + ".");
            }
            tabX += tw + tabGap;
        }
        return null;
    }

    private static HoverTooltip shipHoverTooltipAt(GameContext ctx) {
        if (ctx == null || ctx.player == null) return null;
        Ship best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        double wx = ctx.cursorWorldX;
        double wy = ctx.cursorWorldY;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (CampaignSystem.isFleetHubSession(ctx) && ship == ctx.enemyBase) continue;
            double radius = Math.max(38.0, ship.radius * 1.6 + 12.0);
            double d2 = GameMath.dist2(wx, wy, ship.x, ship.y);
            if (d2 > radius * radius || d2 >= bestDist2) continue;
            best = ship;
            bestDist2 = d2;
        }
        if (best == null) return null;

        String title = (best.name == null || best.name.isBlank()) ? shopRoleTitle(best.role) : best.name;
        String faction = (best.faction == null) ? "Unknown" : best.faction.name().replace('_', ' ');
        String hull = best.hp + "/" + best.hpMax;
        String shield = best.shieldActive
                ? ((int) Math.round(best.shield)) + "/" + ((int) Math.round(best.shieldMax))
                : "offline";
        String body = "Role: " + shopRoleTitle(best.role)
                + ". Faction: " + faction
                + ". Hull " + hull
                + ", shield " + shield
                + ", crew order " + best.crewOrder + ".";
        if (CampaignSystem.isFleetHubSession(ctx)) {
            Ship selected = CampaignSystem.fleetSelectedShip(ctx);
            if (best == selected) {
                body += " Selected for Fleet editing.";
            } else if (best.faction != null && ctx.player.faction != null && best.faction.isFriendlyTo(ctx.player.faction)) {
                body += " Click to select this hull for Fleet editing.";
            }
        }
        return new HoverTooltip("ship:" + best.id, title, body);
    }

    private static Rectangle getBaseUpgradeOverlayRect(int viewW) {
        int w = 520;
        int h = 284;
        int pad = 22;
        int x = viewW - w - pad;
        int y = 240;
        return new Rectangle(x, y, w, h);
    }

    private static java.util.List<String> wrapTooltipLines(FontMetrics metrics, String raw, int maxWidth) {
        java.util.List<String> out = new ArrayList<>();
        if (metrics == null || raw == null || raw.isBlank()) {
            out.add("");
            return out;
        }
        for (String paragraph : raw.split("\\n")) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                if (line.length() == 0) {
                    line.append(word);
                    continue;
                }
                String candidate = line + " " + word;
                if (metrics.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) out.add(line.toString());
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    public static void drawCoreMenuBar(Graphics2D g2, GameContext ctx, int viewW, int viewH) {
        if (g2 == null || ctx == null) return;
        Rectangle bar = getCoreMenuBarRect(viewW, viewH);

        if (!paintThemedHudFrame(g2, bar.x, bar.y, bar.width, bar.height,
                new Color(110, 200, 255, 190), ThemeArt.HUD_STATUS_STRIP, 14)) {
            g2.setColor(new Color(0, 0, 0, 158));
            g2.fillRoundRect(bar.x, bar.y, bar.width, bar.height, 14, 14);
            g2.setColor(new Color(255, 255, 255, 95));
            g2.drawRoundRect(bar.x, bar.y, bar.width, bar.height, 14, 14);
        }

        boolean[] open = {
                ctx.ui.shopOpen,
                ctx.ui.baseMenuOpen,
                ctx.ui.mapOpen,
                ctx.ui.powerManagementOpen,
                ctx.ui.crewStationsOpen,
                false
        };
        boolean campaignActive = CampaignSystem.isCampaignActive(ctx);
        boolean fleetHub = CampaignSystem.isFleetHubSession(ctx);
        boolean baseAvailable = campaignActive ? fleetHub : CampaignSystem.currentBaseUpgradeAnchor(ctx) != null;
        boolean controlsDisabled = ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER;

        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < CORE_MENU_LABELS.length; i++) {
            Rectangle br = getCoreMenuButtonRect(viewW, viewH, i);
            boolean disabled = controlsDisabled
                    || (campaignActive && !fleetHub && (i == 0 || i == 1))
                    || (!campaignActive && i == 1 && !baseAvailable)
                    || (i == 5 && (!campaignActive || fleetHub || ctx.ui.hasBlockingOverlay() || CampaignSystem.isTransitioning(ctx)));
            boolean active = i < open.length && open[i];
            String menuLabel = coreMenuLabel(ctx, i);

            Color fill;
            if (disabled) fill = new Color(60, 60, 65, 160);
            else if (active) fill = new Color(70, 145, 220, 185);
            else fill = new Color(28, 32, 40, 180);
            g2.setColor(fill);
            g2.fillRoundRect(br.x, br.y, br.width, br.height, 10, 10);

            if (disabled) g2.setColor(new Color(160, 160, 170, 130));
            else if (active) g2.setColor(new Color(215, 242, 255, 220));
            else g2.setColor(new Color(200, 220, 255, 180));
            g2.drawRoundRect(br.x, br.y, br.width, br.height, 10, 10);

            String label;
            if (br.width < 64) label = menuLabel.substring(0, Math.min(2, menuLabel.length()));
            else if (br.width < 96) label = menuLabel;
            else if (CORE_MENU_HOTKEYS[i] == null || CORE_MENU_HOTKEYS[i].isBlank()) label = menuLabel;
            else label = menuLabel + " [" + CORE_MENU_HOTKEYS[i] + "]";
            int tx = br.x + (br.width - fm.stringWidth(label)) / 2;
            int ty = br.y + (br.height + fm.getAscent() - fm.getDescent()) / 2;
            if (disabled) g2.setColor(new Color(170, 170, 176, 155));
            else g2.setColor(new Color(240, 245, 255, active ? 240 : 210));
            g2.drawString(label, tx, ty);
        }

        g2.setFont(oldFont);
    }



    private static String fmt1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String signedPct(double mul) {
        double v = (mul - 1.0) * 100.0;
        if (!Double.isFinite(v)) v = 0.0;
        return String.format(Locale.US, "%+.0f%%", v);
    }

    private static Color mixColor(Color a, Color b, double t) {
        if (a == null) a = Color.WHITE;
        if (b == null) b = Color.WHITE;
        double k = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k);
        return new Color(MathUtil.clamp(r, 0, 255), MathUtil.clamp(g, 0, 255), MathUtil.clamp(bl, 0, 255));
    }

    private static Color shieldTeamColor(Ship ship) {
        Faction faction = (ship == null || ship.faction == null) ? Faction.ALLY : ship.faction;
        return switch (faction.teamId()) {
            case 1 -> new Color(255, 132, 132); // Team B / Enemy
            case 2 -> new Color(130, 255, 132); // Team C (Aegis Lattice)
            case 3 -> new Color(255, 212, 132); // Team D (Viper Barrage)
            default -> new Color(128, 206, 255); // Team A / Player / Ally
        };
    }

    private static Color shieldFaceColor(Ship ship, int face, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        Color base = shieldTeamColor(ship);
        Color faceTint = switch (face) {
            case Ship.SHIELD_FACE_FORE -> mixColor(base, Color.WHITE, 0.24);
            case Ship.SHIELD_FACE_LEFT -> base;
            case Ship.SHIELD_FACE_RIGHT -> base;
            case Ship.SHIELD_FACE_REAR -> mixColor(base, new Color(44, 50, 68), 0.20);
            default -> base;
        };
        return new Color(faceTint.getRed(), faceTint.getGreen(), faceTint.getBlue(), a);
    }

    private static String shieldGateReadout(Ship ship) {
        if (ship == null) return "N/A";
        int cap = ship.externalShieldGateHitCap();
        if (cap <= 0) return "N/A";
        return "F" + ship.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_FORE)
                + " L" + ship.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_LEFT)
                + " R" + ship.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_RIGHT)
                + " A" + ship.externalShieldGateHitsRemaining(Ship.SHIELD_FACE_REAR);
    }

    private static String superweaponStatusReadout(Player player) {
        if (player == null || !player.hasSuperweapon) return "N/A";
        if (player.isSuperweaponBeamActive()) return "FIRING";
        if (player.isSuperweaponCharging()) {
            return "CHARGE " + (int) Math.round(player.getSuperweaponChargeProgress() * 100.0) + "%";
        }
        int pct = (int) Math.round(player.getSuperweaponRechargeProgress() * 100.0);
        if (player.canFireSuperweapon()) return "READY 100%";
        return "RECHARGE " + pct + "%";
    }

    private static Rectangle getShopUpgradeArea(Rectangle panel) {
        int x = panel.x + 24;
        int y = panel.y + 136;
        int w = Math.min(396, Math.max(332, (int) Math.round(panel.width * 0.34)));
        int h = panel.height - 168;
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle getShopHullArea(Rectangle panel) {
        Rectangle upgrades = getShopUpgradeArea(panel);
        int x = upgrades.x + upgrades.width + 20;
        int y = upgrades.y;
        int w = panel.x + panel.width - x - 24;
        int h = upgrades.height;
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle getShopUpgradeCardRect(Rectangle panel, int index) {
        Rectangle area = getShopUpgradeArea(panel);
        int cols = 2;
        int gap = 12;
        int cardW = (area.width - gap) / cols;
        int cardH = 96;
        int col = Math.max(0, index % cols);
        int row = Math.max(0, index / cols);
        int x = area.x + col * (cardW + gap);
        int y = area.y + 28 + row * (cardH + gap);
        return new Rectangle(x, y, cardW, cardH);
    }

    private static Rectangle getShopHullCategoryTabRect(Rectangle panel, ShopHullCategory category) {
        Rectangle area = getShopHullArea(panel);
        ShopHullCategory[] categories = ShopHullCategory.values();
        int tabGap = 8;
        int tabY = area.y + 18;
        int tabH = 28;
        int usableW = area.width - 128;
        int tabW = Math.max(100, (usableW - tabGap * (categories.length - 1)) / categories.length);
        int idx = 0;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i] == category) {
                idx = i;
                break;
            }
        }
        int x = area.x + idx * (tabW + tabGap);
        return new Rectangle(x, tabY, tabW, tabH);
    }

    private static Rectangle getShopHullPageButtonRect(Rectangle panel, boolean next) {
        Rectangle area = getShopHullArea(panel);
        int w = 42;
        int h = 28;
        int y = area.y + 18;
        int x = area.x + area.width - (next ? w : (w * 2 + 8));
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle getShopHullCardRect(Rectangle panel, int slot) {
        Rectangle area = getShopHullArea(panel);
        int cols = 4;
        int gap = 10;
        int cardW = (area.width - gap * (cols - 1)) / cols;
        int cardH = 136;
        int col = Math.max(0, slot % cols);
        int row = Math.max(0, slot / cols);
        int x = area.x + col * (cardW + gap);
        int y = area.y + 64 + row * (cardH + gap);
        return new Rectangle(x, y, cardW, cardH);
    }

    private static ShopHullOffer shopHullOfferAt(ShopHullCategory category, int page, int slot) {
        ShopHullCategory resolved = (category == null) ? ShopHullCategory.ESCORT : category;
        int clampedPage = clampShopHullPage(resolved, page);
        int wanted = clampedPage * SHOP_HULL_PAGE_SIZE + Math.max(0, slot);
        int idx = 0;
        for (ShopHullOffer offer : SHOP_HULL_OFFERS) {
            if (offer == null || offer.category != resolved) continue;
            if (idx == wanted) return offer;
            idx++;
        }
        return null;
    }

    private static Rectangle getShopCardButtonRect(Rectangle cardRect) {
        int w = Math.min(94, Math.max(74, cardRect.width - 24));
        int h = 24;
        int x = cardRect.x + cardRect.width - w - 12;
        int y = cardRect.y + cardRect.height - h - 10;
        return new Rectangle(x, y, w, h);
    }

    private static void drawShieldArcSegment(Graphics2D g, double radius, double centerAngle, double span) {
        int steps = 18;
        double start = centerAngle - span * 0.5;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = start + span * t;
            double px = Math.cos(a) * radius;
            double py = Math.sin(a) * radius;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        g.draw(path);
    }

    private static void drawShieldArcBand(Graphics2D g, double innerRadius, double outerRadius, double centerAngle, double span) {
        if (g == null) return;
        if (!Double.isFinite(innerRadius) || !Double.isFinite(outerRadius)) return;
        if (outerRadius <= innerRadius || span <= 0.0) return;

        int steps = 24;
        double start = centerAngle - span * 0.5;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = start + span * t;
            double x = Math.cos(a) * outerRadius;
            double y = Math.sin(a) * outerRadius;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        for (int i = steps; i >= 0; i--) {
            double t = i / (double) steps;
            double a = start + span * t;
            double x = Math.cos(a) * innerRadius;
            double y = Math.sin(a) * innerRadius;
            path.lineTo(x, y);
        }
        path.closePath();
        g.fill(path);
    }

    private static double shieldFaceCenterAngle(Ship ship, int face) {
        double facing = (ship == null) ? 0.0 : ship.getShieldFacingAngle();
        return switch (face) {
            case Ship.SHIELD_FACE_FORE -> facing;
            case Ship.SHIELD_FACE_LEFT -> facing - Math.PI * 0.5;
            case Ship.SHIELD_FACE_RIGHT -> facing + Math.PI * 0.5;
            case Ship.SHIELD_FACE_REAR -> facing + Math.PI;
            default -> facing;
        };
    }

    private static void drawShipShieldFaces(Graphics2D g, Ship ship, Area hullArea) {
        drawShipShieldFaces(g, ship, hullArea, null);
    }

    private static void drawShipShieldFaces(Graphics2D g, Ship ship, Area hullArea, ShipVisual visual) {
        long shieldStart = System.nanoTime();
        try {
        if (g == null || ship == null || hullArea == null) return;
        if (isTinyStrikeCraft(ship.role)) return;
        double effectiveShieldMax = ship.effectiveShieldCapacityMax();
        if (!ship.shieldActive || effectiveShieldMax <= 0.0 || ship.shield <= 0.0) return;

        Rectangle2D hullBounds = hullArea.getBounds2D();
        if (hullBounds.getWidth() <= 0.0 || hullBounds.getHeight() <= 0.0) return;
        if (!shouldRenderShieldFx(ship, hullBounds, g)) return;

        double shieldFrac = MathUtil.clamp(ship.shield / Math.max(1e-9, effectiveShieldMax), 0.0, 1.0);
        double wear = 1.0 - shieldFrac;
        float shellWidth = (float) Math.max(5.0, ship.radius * 0.24);
        float auraWidth = shellWidth * 1.9f;
        Area shellBase = createShieldShell(hullArea, shellWidth, visual);
        Area auraBase = createShieldShell(hullArea, auraWidth, visual);
        Area shell = new Area(shellBase);
        Area aura = new Area(auraBase);
        Area wearMask = createShieldWearMask(ship, shellBase, shellWidth, wear);
        Rectangle2D wearBounds = (wearMask == null) ? null : wearMask.getBounds2D();
        if (wearMask != null && wearBounds != null && wearBounds.getWidth() > 0.0 && wearBounds.getHeight() > 0.0) {
            shell.subtract(new Area(wearMask));
            aura.subtract(new Area(wearMask));
        }

        Rectangle2D auraBounds = aura.getBounds2D();
        if (auraBounds.getWidth() <= 0.0 || auraBounds.getHeight() <= 0.0) return;
        double gradientRadius = Math.max(auraBounds.getWidth(), auraBounds.getHeight()) * 0.72;

        double flicker = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * (4.6 + shieldFrac * 2.2));
        Color base = shieldTeamColor(ship);

        Graphics2D gx = (Graphics2D) g.create();
        Paint oldPaint = gx.getPaint();
        Stroke oldStroke = gx.getStroke();
        gx.setPaint(new RadialGradientPaint(
                new Point2D.Double(0.0, 0.0),
                (float) gradientRadius,
                new float[]{0.0f, 0.56f, 1.0f},
                new Color[]{
                        withAlpha(base, 0),
                        withAlpha(mixColor(base, Color.WHITE, 0.14), (int) Math.round(26 + shieldFrac * 36)),
                        withAlpha(mixColor(base, Color.WHITE, 0.36), (int) Math.round(18 + shieldFrac * 34))
                }));
        gx.fill(aura);
        drawShieldScarPatches(gx, ship, auraBase, shellWidth, base, shieldFrac);

        gx.setPaint(new RadialGradientPaint(
                new Point2D.Double(0.0, 0.0),
                (float) Math.max(10.0, gradientRadius * 0.86),
                new float[]{0.0f, 0.50f, 1.0f},
                new Color[]{
                        withAlpha(base, 0),
                        withAlpha(mixColor(base, Color.WHITE, 0.24), (int) Math.round(52 + shieldFrac * 64 + flicker * 18)),
                        withAlpha(mixColor(base, Color.WHITE, 0.62), (int) Math.round(96 + shieldFrac * 92))
                }));
        gx.fill(shell);

        gx.setStroke(new BasicStroke(Math.max(1.1f, shellWidth * 0.18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gx.setColor(withAlpha(mixColor(base, Color.WHITE, 0.52), (int) Math.round(92 + shieldFrac * 104)));
        gx.draw(shell);

        if (wearMask != null && wearBounds != null && wearBounds.getWidth() > 0.0 && wearBounds.getHeight() > 0.0) {
            gx.setStroke(new BasicStroke(Math.max(0.9f, shellWidth * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gx.setColor(withAlpha(mixColor(base, Color.WHITE, 0.18), (int) Math.round(42 + wear * 126)));
            gx.draw(wearMask);
        }

        if (ship.hasRecentShieldImpactTelemetry() && Double.isFinite(ship.recentShieldImpactAngle())) {
            double localImpactAngle = MathUtil.normalizeAngle(ship.recentShieldImpactAngle() - ship.angle);
            Point2D impactPoint = shieldImpactPoint(hullBounds, shellWidth * 0.85, localImpactAngle);
            double fade = ship.recentShieldImpactTelemetryFraction();
            double flareRadius = Math.max(8.0, ship.radius * (0.16 + 0.20 * fade));
            gx.setPaint(new RadialGradientPaint(
                    new Point2D.Double(impactPoint.getX(), impactPoint.getY()),
                    (float) flareRadius,
                    new float[]{0.0f, 0.42f, 1.0f},
                    new Color[]{
                            withAlpha(Color.WHITE, (int) Math.round(128 + fade * 96)),
                            withAlpha(mixColor(base, Color.WHITE, 0.34), (int) Math.round(86 + fade * 104)),
                            withAlpha(base, 0)
                    }));
            gx.fill(new Ellipse2D.Double(
                    impactPoint.getX() - flareRadius,
                    impactPoint.getY() - flareRadius,
                    flareRadius * 2.0,
                    flareRadius * 2.0));
        }

        gx.setPaint(oldPaint);
        gx.setStroke(oldStroke);
        gx.dispose();
        } finally {
            frameShieldRenderNs += (System.nanoTime() - shieldStart);
        }
    }

    private static boolean shouldRenderShieldFx(Ship ship, Rectangle2D hullBounds, Graphics2D g) {
        if (ship == null || hullBounds == null) return false;
        if (ship.hasRecentShieldImpactTelemetry()) return shieldFxVisibleOnScreen(hullBounds, g);
        List<Ship.ShieldImpactMark> marks = ship.shieldImpactMarks();
        if (marks == null || marks.isEmpty()) return false;
        for (int i = marks.size() - 1; i >= 0; i--) {
            Ship.ShieldImpactMark mark = marks.get(i);
            if (mark != null && mark.freshness() >= SHIELD_FX_MIN_MARK_FRESHNESS) {
                return shieldFxVisibleOnScreen(hullBounds, g);
            }
        }
        return false;
    }

    private static boolean shieldFxVisibleOnScreen(Rectangle2D hullBounds, Graphics2D g) {
        if (hullBounds == null) return false;
        double screenScale = hullDamageDetailScale(g);
        double span = Math.max(hullBounds.getWidth(), hullBounds.getHeight());
        return span * screenScale >= SHIELD_FX_MIN_SCREEN_SPAN;
    }

    private static Area createShieldShell(Area hullArea, float width) {
        return createShieldShell(hullArea, width, null);
    }

    private static Area createShieldShell(Area hullArea, float width, ShipVisual visual) {
        if (hullArea == null || width <= 0.0f) return new Area();
        int key = Math.max(1, Math.round(width * 100.0f));
        if (visual != null && visual.shieldShellCache != null) {
            Area cached = visual.shieldShellCache.get(key);
            if (cached != null) return new Area(cached);
        }
        Area shell = new Area(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(hullArea));
        shell.subtract(new Area(hullArea));
        if (visual != null && visual.shieldShellCache != null) {
            visual.shieldShellCache.put(key, new Area(shell));
        }
        return shell;
    }

    private static void drawShieldScarPatches(Graphics2D g,
                                              Ship ship,
                                              Area shellArea,
                                              float shellWidth,
                                              Color base,
                                              double shieldFrac) {
        if (g == null || ship == null || shellArea == null || base == null) return;
        List<Ship.ShieldImpactMark> marks = ship.shieldImpactMarks();
        if (marks.isEmpty()) return;
        Paint oldPaint = g.getPaint();
        try {
            int start = Math.max(0, marks.size() - 8);
            for (int i = start; i < marks.size(); i++) {
                Ship.ShieldImpactMark mark = marks.get(i);
                if (mark == null) continue;
                Area patch = createShieldScarPatch(mark, shellWidth);
                patch.intersect(new Area(shellArea));
                Rectangle2D bounds = patch.getBounds2D();
                if (bounds.getWidth() <= 0.0 || bounds.getHeight() <= 0.0) continue;
                Point2D center = shieldScarCenter(mark, shellWidth);
                double patchRadius = Math.max(mark.patchRadius(), shellWidth * 1.4);
                int midAlpha = (int) Math.round(30 + mark.severity() * 48 + mark.freshness() * 56 + shieldFrac * 22);
                int edgeAlpha = (int) Math.round(18 + mark.severity() * 32 + shieldFrac * 18);
                g.setPaint(new RadialGradientPaint(
                        new Point2D.Double(center.getX(), center.getY()),
                        (float) Math.max(shellWidth * 1.3, patchRadius * 1.15),
                        new float[]{0.0f, 0.46f, 1.0f},
                        new Color[]{
                                withAlpha(Color.WHITE, MathUtil.clamp(midAlpha + 34, 0, 210)),
                                withAlpha(mixColor(base, Color.WHITE, 0.48), MathUtil.clamp(midAlpha, 0, 190)),
                                withAlpha(base, MathUtil.clamp(edgeAlpha, 0, 140))
                        }));
                g.fill(patch);
            }
        } finally {
            g.setPaint(oldPaint);
        }
    }

    private static Area createShieldWearMask(Ship ship, Area shellArea, float shellWidth, double wear) {
        if (ship == null || shellArea == null || wear <= 0.10) return null;
        List<Ship.ShieldImpactMark> marks = ship.shieldImpactMarks();
        if (marks.isEmpty()) return null;
        Area out = new Area();
        for (int i = 0; i < marks.size(); i++) {
            Ship.ShieldImpactMark mark = marks.get(i);
            if (mark == null) continue;
            double clusterWeight = Math.max(0.0, mark.severity() * (0.58 + wear * 1.85) + mark.freshness() * 0.18 - 0.08);
            if (clusterWeight <= 0.10) continue;
            int holeCount = Math.max(1, (int) Math.round(clusterWeight * 4.2));
            Random rng = new Random(shieldWearSeed(ship, i));
            Point2D center = shieldScarCenter(mark, shellWidth);
            double nx = mark.normalX();
            double ny = mark.normalY();
            double tx = -ny;
            double ty = nx;
            double spreadAlong = Math.max(shellWidth * 0.55, mark.patchRadius() * 0.34);
            double spreadOut = Math.max(shellWidth * 0.22, shellWidth * wear * 1.1);
            for (int j = 0; j < holeCount; j++) {
                double tangentOffset = (rng.nextDouble() - 0.5) * 2.0 * spreadAlong;
                double normalOffset = (rng.nextDouble() - 0.35) * spreadOut;
                double cx = center.getX() + tx * tangentOffset + nx * normalOffset;
                double cy = center.getY() + ty * tangentOffset + ny * normalOffset;
                double baseSize = Math.max(shellWidth * 0.22, ship.radius * (0.026 + clusterWeight * 0.085));
                double holeW = baseSize * (0.70 + rng.nextDouble() * (0.55 + clusterWeight * 0.70));
                double holeH = baseSize * (0.48 + rng.nextDouble() * (0.42 + clusterWeight * 0.55));
                double rotation = Math.atan2(ty, tx) + (rng.nextDouble() - 0.5) * 0.48;
                out.add(createShieldHole(cx, cy, holeW, holeH, rotation));
            }
        }
        out.intersect(new Area(shellArea));
        return out;
    }

    private static long shieldWearSeed(Ship ship, int index) {
        long seed = ship.id * 0x9E3779B97F4A7C15L ^ ((long) (index + 1) * 0xBF58476D1CE4E5B9L);
        if (ship.role != null) seed ^= ((long) ship.role.ordinal() + 1L) * 0x94D049BB133111EBL;
        if (ship.faction != null) seed ^= ((long) ship.faction.ordinal() + 1L) * 0x369DEA0F31A53F85L;
        return seed;
    }

    private static Area createShieldScarPatch(Ship.ShieldImpactMark mark, float shellWidth) {
        Point2D center = shieldScarCenter(mark, shellWidth);
        double radius = Math.max(mark.patchRadius(), shellWidth * 1.3);
        double width = Math.max(shellWidth * 2.0, radius * 1.55);
        double height = Math.max(shellWidth * 1.3, radius * 0.64);
        Ellipse2D.Double ellipse = new Ellipse2D.Double(-width, -height, width * 2.0, height * 2.0);
        double rotation = Math.atan2(mark.normalY(), mark.normalX()) + Math.PI * 0.5;
        AffineTransform tx = new AffineTransform();
        tx.translate(center.getX(), center.getY());
        tx.rotate(rotation);
        return new Area(tx.createTransformedShape(ellipse));
    }

    private static Area createShieldHole(double cx, double cy, double radiusX, double radiusY, double rotation) {
        Ellipse2D.Double ellipse = new Ellipse2D.Double(-radiusX, -radiusY, radiusX * 2.0, radiusY * 2.0);
        AffineTransform tx = new AffineTransform();
        tx.translate(cx, cy);
        tx.rotate(rotation);
        return new Area(tx.createTransformedShape(ellipse));
    }

    private static Point2D shieldScarCenter(Ship.ShieldImpactMark mark, float shellWidth) {
        double offset = shellWidth * 0.62;
        return new Point2D.Double(
                mark.localX() + mark.normalX() * offset,
                mark.localY() + mark.normalY() * offset
        );
    }

    private static Point2D shieldImpactPoint(Rectangle2D hullBounds, double shellInset, double angle) {
        double rx = hullBounds.getWidth() * 0.5 + shellInset + 1.5;
        double ry = hullBounds.getHeight() * 0.5 + shellInset + 1.5;
        return new Point2D.Double(Math.cos(angle) * rx, Math.sin(angle) * ry);
    }

    private static void drawShieldFaceTelemetry(Graphics2D g, Ship ship, int face, double centerAngle,
                                                double radius, double fade, Color accent) {
        if (g == null || ship == null || face < 0) return;
        String text = ship.shieldFaceName(face) + " "
                + (int) Math.round(ship.shieldFaceValue(face)) + "/"
                + (int) Math.round(ship.shieldFaceMax(face));
        Font oldFont = g.getFont();
        g.setFont(new Font("Consolas", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(text);
        int tx = (int) Math.round(Math.cos(centerAngle) * radius) - textW / 2;
        int ty = (int) Math.round(Math.sin(centerAngle) * radius);
        int padX = 4;
        int padY = 2;
        int boxX = tx - padX;
        int boxY = ty - fm.getAscent() + 1 - padY;
        int boxW = textW + padX * 2;
        int boxH = fm.getAscent() + fm.getDescent() + padY * 2;
        int fillAlpha = MathUtil.clamp((int) Math.round(118 * fade), 0, 255);
        int edgeAlpha = MathUtil.clamp((int) Math.round(182 * fade), 0, 255);
        g.setColor(new Color(8, 14, 24, fillAlpha));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        g.setColor(withAlpha(accent, edgeAlpha));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        g.setColor(new Color(240, 248, 255, MathUtil.clamp((int) Math.round(228 * fade), 0, 255)));
        g.drawString(text, tx, ty);
        g.setFont(oldFont);
    }

    private static Shape createShieldFaceWedge(double centerAngle, double radius, double span) {
        int steps = 24;
        double start = centerAngle - span * 0.5;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0.0, 0.0);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = start + span * t;
            path.lineTo(Math.cos(a) * radius, Math.sin(a) * radius);
        }
        path.closePath();
        return path;
    }

    private static double shieldEnvelopeRadius(Ship ship) {
        if (ship == null) return 21.8;
        double base = ship.radius + 5.8;
        ShipRole role = ship.role;
        if (role == null) return base;
        return switch (role) {
            case TRANSPORT, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> base + 2.0;
            case BATTLECRUISER, BATTLESHIP -> base + 3.6;
            case CARRIER, DRONE_CARRIER, DREADNOUGHT, SUPERSHIP, BASE -> base + 5.4;
            default -> base;
        };
    }

    private static Color layerColorForFace(Color base) {
        return mixColor(base, new Color(208, 242, 255), 0.42);
    }

    private static void drawWarpChargeHullFx(Graphics2D g, Ship ship, Area hullArea) {
        drawWarpChargeHullFx(g, ship, hullArea, null);
    }

    private static void drawWarpChargeHullFx(Graphics2D g, Ship ship, Area hullArea, ShipVisual visual) {
        if (g == null || ship == null || hullArea == null || !ship.isWarpCharging()) return;
        Rectangle2D bounds = hullArea.getBounds2D();
        if (bounds.getWidth() <= 0.0 || bounds.getHeight() <= 0.0) return;
        double screenSpan = Math.max(bounds.getWidth(), bounds.getHeight()) * hullDamageDetailScale(g);
        if (screenSpan < WARP_FX_MIN_SCREEN_SPAN) return;

        double charge = ship.warpChargeProgress();
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 4.6 + ship.id * 0.21);
        Color base = mixColor(factionTrimColor(ship.faction), new Color(120, 220, 255), 0.22);
        double anchorX = bounds.getCenterX() - bounds.getWidth() * 0.16;
        double anchorY = bounds.getCenterY();
        double wormholeR = Math.max(4.0, Math.min(bounds.getWidth(), bounds.getHeight()) * 0.11 + charge * 2.4);
        double hazeR = wormholeR * (1.7 + pulse * 0.10);

        Graphics2D gx = (Graphics2D) g.create();
        Paint oldPaint = gx.getPaint();
        Stroke oldStroke = gx.getStroke();
        try {
            gx.setPaint(new RadialGradientPaint(
                    new Point2D.Double(anchorX, anchorY),
                    (float) Math.max(6.0, hazeR),
                    new float[]{0.0f, 0.36f, 1.0f},
                    new Color[]{
                            withAlpha(base, 0),
                            withAlpha(mixColor(base, Color.WHITE, 0.18), (int) Math.round(18 + charge * 22 + pulse * 8)),
                            withAlpha(base, 0)
                    }));
            gx.fill(new Ellipse2D.Double(anchorX - hazeR, anchorY - hazeR, hazeR * 2.0, hazeR * 2.0));

            gx.setStroke(new BasicStroke(Math.max(0.8f, (float) (wormholeR * 0.18)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gx.setColor(withAlpha(mixColor(base, Color.WHITE, 0.42), (int) Math.round(38 + charge * 28 + pulse * 12)));
            gx.draw(new Ellipse2D.Double(anchorX - wormholeR, anchorY - wormholeR, wormholeR * 2.0, wormholeR * 2.0));

            double slitW = wormholeR * (0.44 + charge * 0.10);
            double slitH = Math.max(1.6, wormholeR * 0.26);
            gx.setColor(withAlpha(Color.WHITE, (int) Math.round(18 + charge * 16 + pulse * 6)));
            gx.fill(new Ellipse2D.Double(anchorX - slitW, anchorY - slitH, slitW * 2.0, slitH * 2.0));
        } finally {
            gx.setPaint(oldPaint);
            gx.setStroke(oldStroke);
            gx.dispose();
        }
    }

    private enum BackdropBodyKind {
        PLANET,
        MOON,
        GAS_GIANT,
        STAR
    }

    private static final class CelestialBackdropSpec {
        final BackdropBodyKind kind;
        final double anchorX;
        final double anchorY;
        final double radiusPx;
        final double parallax;
        final Color baseColor;
        final Color highlightColor;
        final Color atmosphereColor;
        final Color glowColor;
        final boolean rings;
        final boolean cityLights;
        final boolean cloudBands;
        final boolean agricultureBands;
        final double infrastructureDensity;
        final double trafficDensity;

        CelestialBackdropSpec(BackdropBodyKind kind,
                              double anchorX,
                              double anchorY,
                              double radiusPx,
                              double parallax,
                              Color baseColor,
                              Color highlightColor,
                              Color atmosphereColor,
                              Color glowColor,
                              boolean rings,
                              boolean cityLights,
                              boolean cloudBands,
                              boolean agricultureBands,
                              double infrastructureDensity,
                              double trafficDensity) {
            this.kind = kind;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.radiusPx = radiusPx;
            this.parallax = parallax;
            this.baseColor = baseColor;
            this.highlightColor = highlightColor;
            this.atmosphereColor = atmosphereColor;
            this.glowColor = glowColor;
            this.rings = rings;
            this.cityLights = cityLights;
            this.cloudBands = cloudBands;
            this.agricultureBands = agricultureBands;
            this.infrastructureDensity = infrastructureDensity;
            this.trafficDensity = trafficDensity;
        }
    }

    private enum BackdropFieldMode {
        SPACE_NEBULA("space_nebula"),
        COLONY_ARCOLOGY("colony_arcology"),
        INDUSTRIAL_YARDS("industrial_yards"),
        LUNAR_INSTALLATION("lunar_installation"),
        HOMEWORLD_CITYLIGHTS("homeworld_citylights");

        final String debugName;

        BackdropFieldMode(String debugName) {
            this.debugName = (debugName == null || debugName.isBlank()) ? "space_nebula" : debugName;
        }
    }

    private static final class CampaignBackdropSpec {
        final String key;
        final double phaseBlend;
        final Color ambientTint;
        final BackdropFieldMode fieldMode;
        final CelestialBackdropSpec primary;
        final CelestialBackdropSpec secondary;

        CampaignBackdropSpec(String key,
                             double phaseBlend,
                             Color ambientTint,
                             BackdropFieldMode fieldMode,
                             CelestialBackdropSpec primary,
                             CelestialBackdropSpec secondary) {
            this.key = (key == null) ? "neutral_space" : key;
            this.phaseBlend = MathUtil.clamp(phaseBlend, 0.0, 1.0);
            this.ambientTint = (ambientTint == null) ? new Color(0, 0, 0, 0) : ambientTint;
            this.fieldMode = (fieldMode == null) ? BackdropFieldMode.SPACE_NEBULA : fieldMode;
            this.primary = primary;
            this.secondary = secondary;
        }

        boolean replacesNebula() {
            return fieldMode != BackdropFieldMode.SPACE_NEBULA;
        }
    }

    // Layered environment backgrounds with procedural fallback.
    public static void drawSpaceBackground(Graphics2D g2, double camX, double camY, int viewW, int viewH, long seed) {
        drawSpaceBackground(g2, null, camX, camY, viewW, viewH, seed);
    }

    public static void drawSpaceBackground(Graphics2D g2, GameContext ctx, double camX, double camY, int viewW, int viewH, long seed) {
        if (ctx != null && ctx.ui != null && ctx.ui.tacticalViewEnabled) {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, viewW, viewH);
            return;
        }
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        BufferedImage campaignImage = EnvironmentSkinLibrary.campaignBackdrop(campaignBackdropImageKey(ctx));
        if (campaignImage == null) {
            campaignImage = EnvironmentSkinLibrary.campaignBackdrop(campaignBackdropBaseImageKey(ctx));
        }
        if (campaignImage != null) {
            drawCampaignBackgroundImage(g2, campaignImage, camX, camY, viewW, viewH, seed);
            return;
        }

        BufferedImage bgBase = EnvironmentSkinLibrary.backgroundBase();
        BufferedImage bgNebula = EnvironmentSkinLibrary.backgroundNebula();
        BufferedImage bgStars = EnvironmentSkinLibrary.backgroundStars();
        BufferedImage bgDust = EnvironmentSkinLibrary.backgroundDust();

        if (bgBase == null && bgNebula == null && bgStars == null && bgDust == null && spec == null) {
            drawSpaceBackgroundFallback(g2, camX, camY, viewW, viewH, seed);
            return;
        }

        drawTiledParallaxLayer(g2, bgBase, camX, camY, viewW, viewH, 0.05, 1.00f);
        if (spec != null && spec.replacesNebula()) {
            drawBackdropField(g2, spec, camX, camY, viewW, viewH, seed ^ 0x5DA41B77L);
        } else {
            drawTiledParallaxLayer(g2, bgNebula, camX, camY, viewW, viewH, 0.10, 0.72f);
        }
        drawTiledParallaxLayer(g2, bgStars, camX, camY, viewW, viewH, 0.16, backdropStarsAlpha(spec));
        drawTiledParallaxLayer(g2, bgDust, camX, camY, viewW, viewH, 0.24, backdropDustAlpha(spec));
        drawCampaignBackdropOverlay(g2, spec, camX, camY, viewW, viewH, seed);
    }

    private static void drawCampaignBackgroundImage(Graphics2D g2, BufferedImage image,
                                                    double camX, double camY,
                                                    int viewW, int viewH, long seed) {
        if (g2 == null || image == null || viewW <= 0 || viewH <= 0) return;
        int iw = Math.max(1, image.getWidth());
        int ih = Math.max(1, image.getHeight());
        double scale = Math.max(viewW / (double) iw, viewH / (double) ih) * 1.08;
        int drawW = Math.max(1, (int) Math.round(iw * scale));
        int drawH = Math.max(1, (int) Math.round(ih * scale));
        int slackX = Math.max(0, drawW - viewW);
        int slackY = Math.max(0, drawH - viewH);
        double driftX = MathUtil.clamp(camX * 0.010, -slackX / 2.0, slackX / 2.0);
        double driftY = MathUtil.clamp(camY * 0.006, -slackY / 2.0, slackY / 2.0);
        driftX += Math.sin((seed & 1023L) * 0.001) * 2.0;
        driftY += Math.cos((seed & 1023L) * 0.0017) * 1.5;
        int x = (viewW - drawW) / 2 - (int) Math.round(driftX);
        int y = (viewH - drawH) / 2 - (int) Math.round(driftY);
        g2.drawImage(image, x, y, drawW, drawH, null);
    }

    public static int drawShips(Graphics2D g2, List<Ship> ships) {
        return drawShips(g2, ships, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, null, null);
    }

    public static int drawShips(Graphics2D g2, List<Ship> ships,
                                double minX, double minY, double maxX, double maxY) {
        return drawShips(g2, ships, minX, minY, maxX, maxY, null, null);
    }

    public static int drawShips(Graphics2D g2, List<Ship> ships,
                                double minX, double minY, double maxX, double maxY,
                                FogOfWarSystem.State fog, Faction perspective) {
        if (ships == null) return 0;
        int drawn = 0;
        for (Ship s : ships) {
            if (s == null || !s.alive) continue;
            boolean visible = FogOfWarSystem.isVisibleToPerspective(fog, perspective, s);
            if (visible) {
                if (!isWorldCircleVisible(s.x, s.y, shipDrawCullRadius(s), minX, minY, maxX, maxY)) continue;
                drawShip(g2, s);
                drawn++;
            } else {
                FogOfWarSystem.ContactGhost ghost = (fog == null) ? null : fog.contactGhost(s.id);
                if (ghost == null || ghost.isExpired()) continue;
                if (!isWorldCircleVisible(ghost.x, ghost.y, ghost.renderCullRadius(), minX, minY, maxX, maxY)) continue;
                ShipRenderer.drawGhostShip(g2, s, ghost);
                drawn++;
            }
        }
        return drawn;
    }

    static int drawTacticalShips(Graphics2D g2, List<Ship> ships,
                                  double minX, double minY, double maxX, double maxY) {
        return drawTacticalShips(g2, ships, minX, minY, maxX, maxY, null, null);
    }

    static int drawTacticalShips(Graphics2D g2, List<Ship> ships,
                                 double minX, double minY, double maxX, double maxY,
                                 FogOfWarSystem.State fog, Faction perspective) {
        if (ships == null) return 0;
        int drawn = 0;
        for (Ship s : ships) {
            if (s == null || !s.alive) continue;
            boolean visible = FogOfWarSystem.isVisibleToPerspective(fog, perspective, s);
            if (visible) {
                if (!isWorldCircleVisible(s.x, s.y, shipDrawCullRadius(s), minX, minY, maxX, maxY)) continue;
                drawTacticalShip(g2, s);
                drawn++;
            } else {
                FogOfWarSystem.ContactGhost ghost = (fog == null) ? null : fog.contactGhost(s.id);
                if (ghost == null || ghost.isExpired()) continue;
                if (!isWorldCircleVisible(ghost.x, ghost.y, ghost.renderCullRadius(), minX, minY, maxX, maxY)) continue;
                ShipRenderer.drawGhostShip(g2, s, ghost);
                drawn++;
            }
        }
        return drawn;
    }

    // ------------------------------
    // Asteroids (obstacles/resources)
    // ------------------------------

    public static int drawAsteroids(Graphics2D g2, List<Asteroid> asteroids) {
        return drawAsteroids(g2, asteroids, null);
    }

    public static int drawAsteroids(Graphics2D g2, List<Asteroid> asteroids, Player player) {
        return drawAsteroids(g2, asteroids, player, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static int drawAsteroids(Graphics2D g2, List<Asteroid> asteroids, Player player,
                                    double minX, double minY, double maxX, double maxY) {
        if (asteroids == null) return 0;
        Asteroid promptAsteroid = findNearbyAsteroidPromptTarget(asteroids, player);
        int drawn = 0;
        for (Asteroid a : asteroids) {
            if (a == null) continue;
            if (!isWorldCircleVisible(a.x, a.y, a.collisionRadius() + 24.0, minX, minY, maxX, maxY)) continue;

            BufferedImage skin = EnvironmentSkinLibrary.pickAsteroidSprite(a);
            if (skin != null) {
                drawAsteroidSprite(g2, a, skin);
                drawn++;
                continue;
            }

            int r = (int) Math.round(a.radius);
            int x = (int) Math.round(a.x);
            int y = (int) Math.round(a.y);

            double frac = (a.oreMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) a.ore / (double) a.oreMax));

            // Main body
            int baseA = 150;
            int shade = (int) Math.round(70 + 80 * (0.35 + 0.65 * frac));
            g2.setColor(new Color(shade, shade, shade, baseA));
            g2.fillOval(x - r, y - r, r * 2, r * 2);

            // Subtle rim
            g2.setColor(new Color(255, 255, 255, 28));
            g2.drawOval(x - r, y - r, r * 2, r * 2);

            // Ore glow
            if (a.ore > 0) {
                int ir = Math.max(6, (int) Math.round(r * 0.55));
                int alpha = (int) Math.round(30 + 120 * frac);
                g2.setColor(new Color(255, 220, 140, MathUtil.clamp(alpha, 0, 200)));
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);

                // A little"twist" highlight
                double ang = a.spin;
                int hx = (int) Math.round(x + Math.cos(ang) * ir * 0.65);
                int hy = (int) Math.round(y + Math.sin(ang) * ir * 0.65);
                g2.setColor(new Color(255, 255, 255, MathUtil.clamp((int) (20 + 80 * frac), 0, 120)));
                g2.fillOval(hx - 3, hy - 3, 6, 6);
            }

            // Rich vein highlight
            if (a.rich) {
                int rr = (int) Math.round(r * 1.25);
                g2.setColor(new Color(255, 220, 120, 34));
                g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
                int rr2 = (int) Math.round(r * 1.45);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.drawOval(x - rr2, y - rr2, rr2 * 2, rr2 * 2);
            }
            drawn++;
        }
        drawAsteroidMinePrompt(g2, promptAsteroid);
        return drawn;
    }

    static int drawTacticalAsteroids(Graphics2D g2, List<Asteroid> asteroids, Player player,
                                     double minX, double minY, double maxX, double maxY) {
        if (asteroids == null) return 0;
        Asteroid promptAsteroid = findNearbyAsteroidPromptTarget(asteroids, player);
        int drawn = 0;
        for (Asteroid a : asteroids) {
            if (a == null) continue;
            if (!isWorldCircleVisible(a.x, a.y, a.collisionRadius() + 24.0, minX, minY, maxX, maxY)) continue;
            drawTacticalAsteroid(g2, a);
            drawn++;
        }
        drawAsteroidMinePrompt(g2, promptAsteroid);
        return drawn;
    }

    public static void drawAsteroidDangerHeatmap(Graphics2D g2, List<Asteroid> asteroids) {
        drawAsteroidDangerHeatmap(g2, asteroids,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static void drawAsteroidDangerHeatmap(Graphics2D g2, List<Asteroid> asteroids,
                                                 double minX, double minY, double maxX, double maxY) {
        if (g2 == null || asteroids == null || asteroids.isEmpty()) return;

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(1.1f));
        for (Asteroid a : asteroids) {
            if (a == null) continue;
            double maxDangerRadius = a.collisionRadius()
                    + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.BATTLESHIP);
            if (!isWorldCircleVisible(a.x, a.y, maxDangerRadius + 8.0, minX, minY, maxX, maxY)) continue;
            int x = (int) Math.round(a.x);
            int y = (int) Math.round(a.y);

            double coll = a.collisionRadius();
            int cr = (int) Math.round(coll);
            g2.setColor(new Color(255, 80, 80, 130));
            g2.drawOval(x - cr, y - cr, cr * 2, cr * 2);

            double light = coll + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.FIGHTER);
            double frig = coll + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.FRIGATE);
            double cap = coll + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.BATTLESHIP);

            int lr = (int) Math.round(light);
            int fr = (int) Math.round(frig);
            int car = (int) Math.round(cap);
            g2.setColor(new Color(255, 190, 80, 90));
            g2.drawOval(x - lr, y - lr, lr * 2, lr * 2);
            g2.setColor(new Color(255, 215, 120, 70));
            g2.drawOval(x - fr, y - fr, fr * 2, fr * 2);
            g2.setColor(new Color(255, 240, 170, 55));
            g2.drawOval(x - car, y - car, car * 2, car * 2);
        }
        g2.setStroke(old);
    }

    private static void drawSpaceBackgroundFallback(Graphics2D g2, double camX, double camY, int viewW, int viewH, long seed) {
        double px = camX * 0.20;
        double py = camY * 0.20;

        int tile = 256;
        int startX = (int) Math.floor(px / tile) - 1;
        int startY = (int) Math.floor(py / tile) - 1;
        int endX = (int) Math.floor((px + viewW) / tile) + 1;
        int endY = (int) Math.floor((py + viewH) / tile) + 1;

        for (int tx = startX; tx <= endX; tx++) {
            for (int ty = startY; ty <= endY; ty++) {
                long mix = seed;
                mix ^= (long) tx * 0x9E3779B97F4A7C15L;
                mix ^= (long) ty * 0xC2B2AE3D27D4EB4FL;
                mix ^= (mix >>> 33);
                mix *= 0xff51afd7ed558ccdL;
                mix ^= (mix >>> 33);

                Random r = new Random(mix);
                int stars = 10 + r.nextInt(10);
                for (int i = 0; i < stars; i++) {
                    int sx = tx * tile + r.nextInt(tile);
                    int sy = ty * tile + r.nextInt(tile);
                    int x = (int) Math.round(sx - px);
                    int y = (int) Math.round(sy - py);
                    int size = 1 + r.nextInt(2);
                    int a = 40 + r.nextInt(90);
                    g2.setColor(new Color(255, 255, 255, a));
                    g2.fillRect(x, y, size, size);
                }
            }
        }
    }

    private static void drawTiledParallaxLayer(Graphics2D g2, BufferedImage tile,
                                               double camX, double camY, int viewW, int viewH,
                                               double parallax, float alpha) {
        if (tile == null || alpha <= 0f) return;

        int tw = Math.max(1, tile.getWidth());
        int th = Math.max(1, tile.getHeight());
        double px = camX * parallax;
        double py = camY * parallax;
        int startX = (int) Math.floor(px / tw) - 1;
        int startY = (int) Math.floor(py / th) - 1;
        int endX = (int) Math.floor((px + viewW) / tw) + 1;
        int endY = (int) Math.floor((py + viewH) / th) + 1;

        Composite old = g2.getComposite();
        if (alpha < 0.999f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        }

        for (int tx = startX; tx <= endX; tx++) {
            for (int ty = startY; ty <= endY; ty++) {
                int x = (int) Math.round(tx * tw - px);
                int y = (int) Math.round(ty * th - py);
                g2.drawImage(tile, x, y, tw, th, null);
            }
        }

        g2.setComposite(old);
    }

    static String campaignBackdropDebugName(GameContext ctx) {
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        return (spec == null) ? "neutral_space" : spec.key;
    }

    static String campaignBackdropBaseImageKey(GameContext ctx) {
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        return (spec == null) ? "" : spec.key;
    }

    static String campaignBackdropImageKey(GameContext ctx) {
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        if (spec == null) return "";
        int stage = Math.max(0, CampaignSystem.objectiveStage(ctx));
        if (stage <= 0) return spec.key;
        return spec.key + "_phase" + stage;
    }

    static boolean campaignBackdropImageAvailable(String key) {
        if (key == null || key.isBlank()) return false;
        return EnvironmentSkinLibrary.campaignBackdrop(key) != null;
    }

    static String campaignBackdropFieldModeDebugName(GameContext ctx) {
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        return (spec == null) ? BackdropFieldMode.SPACE_NEBULA.debugName : spec.fieldMode.debugName;
    }

    static boolean campaignBackdropReplacesNebula(GameContext ctx) {
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        return spec != null && spec.replacesNebula();
    }

    static double campaignBackdropPhaseBlend(GameContext ctx) {
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        return (spec == null) ? 0.0 : spec.phaseBlend;
    }

    private static float backdropStarsAlpha(CampaignBackdropSpec spec) {
        if (spec == null) return 0.95f;
        return switch (spec.fieldMode) {
            case COLONY_ARCOLOGY -> 0.56f;
            case INDUSTRIAL_YARDS -> 0.46f;
            case LUNAR_INSTALLATION -> 0.38f;
            case HOMEWORLD_CITYLIGHTS -> 0.34f;
            default -> 0.95f;
        };
    }

    private static float backdropDustAlpha(CampaignBackdropSpec spec) {
        if (spec == null) return 0.62f;
        return switch (spec.fieldMode) {
            case COLONY_ARCOLOGY -> 0.28f;
            case INDUSTRIAL_YARDS -> 0.22f;
            case LUNAR_INSTALLATION -> 0.16f;
            case HOMEWORLD_CITYLIGHTS -> 0.14f;
            default -> 0.62f;
        };
    }

    private static void drawCampaignBackdropOverlay(Graphics2D g2, CampaignBackdropSpec spec,
                                                    double camX, double camY, int viewW, int viewH,
                                                    long seed) {
        if (g2 == null || spec == null) return;

        Color oldColor = g2.getColor();
        Paint oldPaint = g2.getPaint();
        Composite oldComposite = g2.getComposite();

        if (spec.secondary != null) {
            drawBackdropBody(g2, spec.secondary, spec.phaseBlend, camX, camY, viewW, viewH, seed ^ 0x28F71E4DL, false);
        }
        if (spec.primary != null) {
            drawBackdropBody(g2, spec.primary, spec.phaseBlend, camX, camY, viewW, viewH, seed ^ 0xA53CB92DL, true);
        }

        int tintAlpha = MathUtil.clamp(spec.ambientTint.getAlpha(), 0, 255);
        if (tintAlpha > 0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, tintAlpha / 255f));
            g2.setColor(new Color(spec.ambientTint.getRed(), spec.ambientTint.getGreen(), spec.ambientTint.getBlue()));
            g2.fillRect(0, 0, viewW, viewH);
        }

        g2.setColor(oldColor);
        g2.setPaint(oldPaint);
        g2.setComposite(oldComposite);
    }

    private static void drawBackdropField(Graphics2D g2, CampaignBackdropSpec spec,
                                          double camX, double camY, int viewW, int viewH,
                                          long seed) {
        if (g2 == null || spec == null || !spec.replacesNebula()) return;

        CelestialBackdropSpec basis = (spec.primary != null) ? spec.primary : spec.secondary;
        Color base = (basis != null && basis.baseColor != null) ? basis.baseColor : new Color(52, 74, 98, 255);
        Color highlight = (basis != null && basis.highlightColor != null) ? basis.highlightColor : new Color(202, 224, 236, 255);
        Color atmosphere = (basis != null && basis.atmosphereColor != null) ? basis.atmosphereColor : new Color(164, 214, 248, 118);

        Color oldColor = g2.getColor();
        Paint oldPaint = g2.getPaint();
        Stroke oldStroke = g2.getStroke();
        Composite oldComposite = g2.getComposite();
        Shape oldClip = g2.getClip();

        double ox = -camX * 0.018;
        double oy = -camY * 0.014;
        Color top = darken(base, 0.22);
        Color bottom = darken(base, 0.52);
        if (spec.fieldMode == BackdropFieldMode.LUNAR_INSTALLATION) {
            top = blend(top, new Color(118, 124, 138, 255), 0.34);
            bottom = darken(blend(base, new Color(126, 132, 142, 255), 0.42), 0.52);
        } else if (spec.fieldMode == BackdropFieldMode.HOMEWORLD_CITYLIGHTS) {
            top = blend(top, new Color(18, 32, 54, 255), 0.48);
            bottom = blend(bottom, new Color(12, 22, 38, 255), 0.36);
        }

        g2.setPaint(new GradientPaint(0f, 0f, top, 0f, (float) viewH, bottom));
        g2.fillRect(0, 0, viewW, viewH);

        drawBackdropTerraces(g2, spec, viewW, viewH, ox, oy, seed ^ 0x1E4A6D93L, base, highlight);
        drawBackdropDistrictMesh(g2, spec, viewW, viewH, ox, oy, seed ^ 0x4B1CC45AL, base, highlight);
        drawBackdropArcologySilhouettes(g2, spec, viewW, viewH, ox, oy, seed ^ 0x68B3E217L, base, highlight);
        drawBackdropSurfaceLights(g2, spec, viewW, viewH, ox, oy, seed ^ 0x2246DA91L, highlight, atmosphere);
        drawBackdropSkyways(g2, spec, viewW, viewH, ox, oy, seed ^ 0x7F122C4DL, atmosphere, highlight);
        drawBackdropTethersAndDefense(g2, spec, viewW, viewH, ox, oy, seed ^ 0x31AE9B55L, atmosphere, highlight);

        g2.setColor(oldColor);
        g2.setPaint(oldPaint);
        g2.setStroke(oldStroke);
        g2.setComposite(oldComposite);
        g2.setClip(oldClip);
    }

    private static void drawBackdropTerraces(Graphics2D g2, CampaignBackdropSpec spec, int viewW, int viewH,
                                             double ox, double oy, long seed, Color base, Color highlight) {
        Random random = new Random(seed);
        int bands = switch (spec.fieldMode) {
            case COLONY_ARCOLOGY, HOMEWORLD_CITYLIGHTS -> 7;
            case INDUSTRIAL_YARDS -> 8;
            case LUNAR_INSTALLATION -> 6;
            default -> 0;
        };
        for (int i = 0; i < bands; i++) {
            double frac = (i + 1.0) / (bands + 1.0);
            double y = viewH * (0.10 + frac * 0.78) + oy * (0.4 + frac * 0.5) + random.nextDouble() * 28.0;
            double depth = viewH * (0.07 + random.nextDouble() * 0.08);
            Path2D.Double ribbon = new Path2D.Double();
            ribbon.moveTo(-viewW * 0.18, y);
            ribbon.curveTo(viewW * 0.18, y - depth * (0.84 + random.nextDouble() * 0.34),
                    viewW * 0.52, y + depth * (0.18 + random.nextDouble() * 0.32),
                    viewW * 1.18, y - depth * (0.10 + random.nextDouble() * 0.20));
            ribbon.lineTo(viewW * 1.18, y + depth * 1.24);
            ribbon.curveTo(viewW * 0.76, y + depth * (1.16 + random.nextDouble() * 0.22),
                    viewW * 0.32, y + depth * (0.88 + random.nextDouble() * 0.22),
                    -viewW * 0.18, y + depth * (1.02 + random.nextDouble() * 0.22));
            ribbon.closePath();

            Color fill = switch (spec.fieldMode) {
                case LUNAR_INSTALLATION -> blend(base, new Color(160, 168, 176, 255), 0.24 + frac * 0.28);
                case INDUSTRIAL_YARDS -> blend(base, new Color(174, 176, 186, 255), 0.16 + frac * 0.24);
                case HOMEWORLD_CITYLIGHTS -> blend(base, new Color(78, 116, 156, 255), 0.16 + frac * 0.26);
                default -> blend(base, highlight, 0.14 + frac * 0.28);
            };
            int alpha = switch (spec.fieldMode) {
                case HOMEWORLD_CITYLIGHTS -> 34 + (int) Math.round(frac * 26.0);
                case LUNAR_INSTALLATION -> 40 + (int) Math.round(frac * 18.0);
                default -> 26 + (int) Math.round(frac * 24.0);
            };
            g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), MathUtil.clamp(alpha, 0, 110)));
            g2.fill(ribbon);
            g2.setStroke(new BasicStroke((float) (1.3 + frac * 1.7)));
            g2.setColor(new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(),
                    MathUtil.clamp(18 + (int) Math.round(frac * 26.0), 0, 88)));
            g2.draw(ribbon);
        }
    }

    private static void drawBackdropDistrictMesh(Graphics2D g2, CampaignBackdropSpec spec, int viewW, int viewH,
                                                 double ox, double oy, long seed, Color base, Color highlight) {
        Random random = new Random(seed);
        int slabs = switch (spec.fieldMode) {
            case HOMEWORLD_CITYLIGHTS -> 42;
            case INDUSTRIAL_YARDS -> 36;
            case COLONY_ARCOLOGY -> 30;
            case LUNAR_INSTALLATION -> 26;
            default -> 0;
        };
        for (int i = 0; i < slabs; i++) {
            double x = -viewW * 0.08 + random.nextDouble() * viewW * 1.16 + ox * (0.3 + random.nextDouble() * 0.4);
            double y = viewH * (0.18 + random.nextDouble() * 0.74) + oy * (0.25 + random.nextDouble() * 0.45);
            double w = viewW * (0.08 + random.nextDouble() * 0.18);
            double h = viewH * (0.018 + random.nextDouble() * 0.055);
            double arc = Math.max(8.0, h * 0.8);
            Color fill = switch (spec.fieldMode) {
                case INDUSTRIAL_YARDS -> blend(base, new Color(204, 200, 196, 255), 0.14 + random.nextDouble() * 0.18);
                case LUNAR_INSTALLATION -> blend(base, new Color(188, 190, 196, 255), 0.12 + random.nextDouble() * 0.20);
                case HOMEWORLD_CITYLIGHTS -> blend(base, new Color(88, 132, 178, 255), 0.12 + random.nextDouble() * 0.22);
                default -> blend(base, highlight, 0.12 + random.nextDouble() * 0.18);
            };
            int alpha = switch (spec.fieldMode) {
                case HOMEWORLD_CITYLIGHTS -> 18 + random.nextInt(28);
                case LUNAR_INSTALLATION -> 20 + random.nextInt(24);
                default -> 16 + random.nextInt(22);
            };
            g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), MathUtil.clamp(alpha, 0, 84)));
            g2.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        }
    }

    private static void drawBackdropArcologySilhouettes(Graphics2D g2, CampaignBackdropSpec spec, int viewW, int viewH,
                                                        double ox, double oy, long seed, Color base, Color highlight) {
        Random random = new Random(seed);
        int towers = switch (spec.fieldMode) {
            case INDUSTRIAL_YARDS -> 14;
            case LUNAR_INSTALLATION -> 12;
            case HOMEWORLD_CITYLIGHTS -> 18;
            case COLONY_ARCOLOGY -> 16;
            default -> 0;
        };
        double baseline = switch (spec.fieldMode) {
            case LUNAR_INSTALLATION -> viewH * 0.60;
            case HOMEWORLD_CITYLIGHTS -> viewH * 0.66;
            default -> viewH * 0.64;
        };
        for (int i = 0; i < towers; i++) {
            double x = viewW * (-0.04 + random.nextDouble() * 1.08) + ox * (0.18 + random.nextDouble() * 0.12);
            double width = viewW * (0.028 + random.nextDouble() * 0.048);
            double height = viewH * (0.12 + random.nextDouble() * 0.26);
            double y = baseline + random.nextDouble() * viewH * 0.24 + oy * 0.2;

            Path2D.Double tower = new Path2D.Double();
            tower.moveTo(x - width * 0.50, y);
            tower.lineTo(x - width * 0.20, y - height * (0.72 + random.nextDouble() * 0.16));
            tower.lineTo(x - width * 0.08, y - height);
            tower.lineTo(x + width * 0.08, y - height);
            tower.lineTo(x + width * 0.20, y - height * (0.72 + random.nextDouble() * 0.16));
            tower.lineTo(x + width * 0.50, y);
            tower.closePath();

            Color fill = switch (spec.fieldMode) {
                case INDUSTRIAL_YARDS -> blend(base, new Color(198, 194, 188, 255), 0.18 + random.nextDouble() * 0.18);
                case LUNAR_INSTALLATION -> blend(base, new Color(214, 216, 222, 255), 0.16 + random.nextDouble() * 0.16);
                case HOMEWORLD_CITYLIGHTS -> blend(base, new Color(108, 148, 188, 255), 0.18 + random.nextDouble() * 0.22);
                default -> blend(base, highlight, 0.16 + random.nextDouble() * 0.18);
            };
            int alpha = 26 + random.nextInt(30);
            g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), MathUtil.clamp(alpha, 0, 110)));
            g2.fill(tower);
            g2.setColor(new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(), 26 + random.nextInt(34)));
            g2.draw(tower);
        }
    }

    private static void drawBackdropSurfaceLights(Graphics2D g2, CampaignBackdropSpec spec, int viewW, int viewH,
                                                  double ox, double oy, long seed, Color highlight, Color atmosphere) {
        Random random = new Random(seed);
        int clusters = switch (spec.fieldMode) {
            case HOMEWORLD_CITYLIGHTS -> 72;
            case COLONY_ARCOLOGY -> 48;
            case INDUSTRIAL_YARDS -> 38;
            case LUNAR_INSTALLATION -> 26;
            default -> 0;
        };
        Color node = blend(highlight, atmosphere, 0.45);
        for (int i = 0; i < clusters; i++) {
            double x = random.nextDouble() * viewW + ox * (0.20 + random.nextDouble() * 0.22);
            double y = viewH * (0.20 + random.nextDouble() * 0.74) + oy * (0.16 + random.nextDouble() * 0.24);
            double size = 6.0 + random.nextDouble() * 18.0;
            int blocks = 3 + random.nextInt(4);
            for (int b = 0; b < blocks; b++) {
                double bx = x - size * 0.55 + random.nextDouble() * size;
                double by = y - size * 0.35 + random.nextDouble() * size * 0.70;
                double bw = 2.0 + random.nextDouble() * (size * 0.28);
                double bh = 1.0 + random.nextDouble() * (size * 0.12);
                int alpha = switch (spec.fieldMode) {
                    case HOMEWORLD_CITYLIGHTS -> 34 + random.nextInt(52);
                    case LUNAR_INSTALLATION -> 20 + random.nextInt(34);
                    default -> 26 + random.nextInt(40);
                };
                g2.setColor(new Color(node.getRed(), node.getGreen(), node.getBlue(), MathUtil.clamp(alpha, 0, 138)));
                g2.fill(new Rectangle2D.Double(bx, by, bw, bh));
            }
            g2.setColor(new Color(node.getRed(), node.getGreen(), node.getBlue(), 20 + random.nextInt(28)));
            g2.draw(new java.awt.geom.Line2D.Double(x - size * 0.42, y, x + size * 0.42, y));
        }
    }

    private static void drawBackdropSkyways(Graphics2D g2, CampaignBackdropSpec spec, int viewW, int viewH,
                                            double ox, double oy, long seed, Color atmosphere, Color highlight) {
        Random random = new Random(seed);
        Stroke oldStroke = g2.getStroke();
        int lanes = switch (spec.fieldMode) {
            case HOMEWORLD_CITYLIGHTS -> 8;
            case COLONY_ARCOLOGY, INDUSTRIAL_YARDS -> 6;
            case LUNAR_INSTALLATION -> 4;
            default -> 0;
        };
        for (int i = 0; i < lanes; i++) {
            double y = viewH * (0.14 + random.nextDouble() * 0.68) + oy * (0.12 + random.nextDouble() * 0.16);
            float strokeW = 1.0f + random.nextFloat() * 1.4f;
            float dash = 8.0f + random.nextFloat() * 12.0f;
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash * 1.7f}, random.nextFloat() * dash));
            Color lane = blend(atmosphere, highlight, 0.40 + random.nextDouble() * 0.24);
            int alpha = switch (spec.fieldMode) {
                case LUNAR_INSTALLATION -> 26;
                case HOMEWORLD_CITYLIGHTS -> 42;
                default -> 34;
            };
            g2.setColor(new Color(lane.getRed(), lane.getGreen(), lane.getBlue(), alpha));
            g2.draw(new java.awt.geom.Line2D.Double(-viewW * 0.10,
                    y + random.nextDouble() * viewH * 0.08,
                    viewW * 1.10,
                    y - viewH * (0.04 + random.nextDouble() * 0.12)));
        }
        g2.setStroke(oldStroke);
    }

    private static void drawBackdropTethersAndDefense(Graphics2D g2, CampaignBackdropSpec spec, int viewW, int viewH,
                                                      double ox, double oy, long seed, Color atmosphere, Color highlight) {
        Random random = new Random(seed);
        Stroke oldStroke = g2.getStroke();
        int tethers = switch (spec.fieldMode) {
            case COLONY_ARCOLOGY -> 4;
            case INDUSTRIAL_YARDS -> 6;
            case LUNAR_INSTALLATION -> 6;
            case HOMEWORLD_CITYLIGHTS -> 5;
            default -> 0;
        };
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < tethers; i++) {
            double x = viewW * (0.08 + random.nextDouble() * 0.84) + ox * 0.12;
            double top = viewH * (0.04 + random.nextDouble() * 0.24);
            double bottom = viewH * (0.54 + random.nextDouble() * 0.30) + oy * 0.18;
            Color tether = blend(atmosphere, highlight, 0.42);
            int alpha = (spec.fieldMode == BackdropFieldMode.LUNAR_INSTALLATION) ? 44 : 30;
            g2.setColor(new Color(tether.getRed(), tether.getGreen(), tether.getBlue(), alpha));
            g2.draw(new java.awt.geom.Line2D.Double(x, top, x + random.nextDouble() * 34.0 - 17.0, bottom));
        }

        if (spec.fieldMode == BackdropFieldMode.LUNAR_INSTALLATION || spec.fieldMode == BackdropFieldMode.HOMEWORLD_CITYLIGHTS) {
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{12f, 14f}, 0f));
            Color defense = blend(highlight, atmosphere, 0.34);
            int alpha = (spec.fieldMode == BackdropFieldMode.HOMEWORLD_CITYLIGHTS) ? 58 : 44;
            g2.setColor(new Color(defense.getRed(), defense.getGreen(), defense.getBlue(), alpha));
            double ringW = viewW * (1.10 + random.nextDouble() * 0.16);
            double ringH = viewH * (0.42 + random.nextDouble() * 0.10);
            drawBackdropArcBand(g2, (viewW - ringW) / 2.0 + ox * 0.10, viewH * (0.02 + random.nextDouble() * 0.06) + oy * 0.08,
                    ringW, ringH, 186.0, 126.0);
            drawBackdropArcBand(g2, (viewW - ringW) / 2.0 + ox * 0.08, viewH * (0.08 + random.nextDouble() * 0.06) + oy * 0.06,
                    ringW, ringH, 12.0, 96.0);
        }
        g2.setStroke(oldStroke);
    }

    private static void drawBackdropBody(Graphics2D g2, CelestialBackdropSpec spec, double blend,
                                         double camX, double camY, int viewW, int viewH,
                                         long seed, boolean primary) {
        if (g2 == null || spec == null) return;
        double cx = viewW * spec.anchorX - camX * spec.parallax;
        double cy = viewH * spec.anchorY - camY * spec.parallax;
        double radius = spec.radiusPx;
        if (!primary) {
            radius *= 0.92;
        }
        if (spec.kind == BackdropBodyKind.STAR) {
            radius *= 1.0 + blend * 0.08;
        } else if (spec.cityLights) {
            radius *= 1.0 + blend * 0.03;
        }

        drawBackdropGlow(g2, cx, cy, radius, spec.glowColor);

        Shape oldClip = g2.getClip();
        Paint oldPaint = g2.getPaint();
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        Composite oldComposite = g2.getComposite();

        Ellipse2D.Double body = new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        RadialGradientPaint fill = new RadialGradientPaint(
                new Point2D.Double(cx - radius * 0.34, cy - radius * 0.28),
                (float) (radius * 1.15),
                new float[]{0f, 0.58f, 1f},
                new Color[]{
                        scaleAlpha(spec.highlightColor, 0.98),
                        scaleAlpha(spec.baseColor, 0.96),
                        darken(spec.baseColor, 0.46)
                });
        g2.setPaint(fill);
        g2.fill(body);

        if (spec.kind != BackdropBodyKind.STAR) {
            Stroke atmStroke = new BasicStroke((float) Math.max(2.0, radius * 0.05));
            g2.setStroke(atmStroke);
            g2.setColor(scaleAlpha(spec.atmosphereColor, 0.55 + 0.20 * blend));
            g2.draw(new Ellipse2D.Double(cx - radius * 1.01, cy - radius * 1.01, radius * 2.02, radius * 2.02));
        }

        g2.setClip(body);
        if (spec.kind == BackdropBodyKind.GAS_GIANT || spec.cloudBands) {
            drawBackdropBands(g2, cx, cy, radius, spec, blend, seed);
        }
        if (spec.kind == BackdropBodyKind.MOON) {
            drawBackdropCraters(g2, cx, cy, radius, seed);
        }
        if (spec.kind == BackdropBodyKind.PLANET && spec.agricultureBands) {
            drawBackdropAgriculture(g2, cx, cy, radius, seed, blend);
        }
        if (spec.kind == BackdropBodyKind.PLANET && spec.cityLights) {
            drawBackdropCityLights(g2, cx, cy, radius, spec, blend, seed);
        }
        if (spec.kind == BackdropBodyKind.PLANET || spec.kind == BackdropBodyKind.GAS_GIANT) {
            drawBackdropCloudDeck(g2, cx, cy, radius, spec, blend, seed);
        }
        g2.setClip(oldClip);

        drawBackdropInfrastructure(g2, cx, cy, radius, spec, blend, seed);
        drawBackdropLanes(g2, cx, cy, radius, spec, blend, seed);

        g2.setStroke(oldStroke);
        g2.setPaint(oldPaint);
        g2.setColor(oldColor);
        g2.setComposite(oldComposite);
        g2.setClip(oldClip);
    }

    private static void drawBackdropGlow(Graphics2D g2, double cx, double cy, double radius, Color glow) {
        if (g2 == null || glow == null) return;
        Color oldColor = g2.getColor();
        Composite oldComposite = g2.getComposite();
        for (int i = 3; i >= 1; i--) {
            double scale = 1.18 + i * 0.16;
            float alpha = Math.max(0.02f, Math.min(0.25f, glow.getAlpha() / 255f * (0.14f / i)));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue()));
            g2.fill(new Ellipse2D.Double(cx - radius * scale, cy - radius * scale, radius * 2.0 * scale, radius * 2.0 * scale));
        }
        g2.setComposite(oldComposite);
        g2.setColor(oldColor);
    }

    private static void drawBackdropBands(Graphics2D g2, double cx, double cy, double radius,
                                          CelestialBackdropSpec spec, double blend, long seed) {
        Random random = new Random(seed ^ 0x4F31A17BL);
        int bands = 7;
        for (int i = 0; i < bands; i++) {
            double frac = (i + 0.5) / bands;
            double y = cy - radius * 0.86 + frac * radius * 1.72;
            double height = radius * (0.12 + random.nextDouble() * 0.06);
            double width = radius * 2.25 * (0.72 + random.nextDouble() * 0.22);
            int alpha = 18 + random.nextInt(28);
            if (spec.kind == BackdropBodyKind.GAS_GIANT) {
                alpha += 18;
            }
            Color band = blend(spec.highlightColor, spec.baseColor, 0.22 + frac * 0.48);
            g2.setColor(new Color(band.getRed(), band.getGreen(), band.getBlue(), MathUtil.clamp(alpha, 0, 120)));
            g2.fill(new RoundRectangle2D.Double(cx - width / 2.0, y - height / 2.0, width, height, height, height));
        }
        g2.setColor(new Color(255, 255, 255, MathUtil.clamp((int) Math.round(16 + 20 * blend), 0, 72)));
        g2.fill(new Ellipse2D.Double(cx - radius * 0.82, cy - radius * 0.62, radius * 1.20, radius * 0.26));
    }

    private static void drawBackdropCraters(Graphics2D g2, double cx, double cy, double radius, long seed) {
        Random random = new Random(seed ^ 0x2F17D31BL);
        int craterCount = 14;
        for (int i = 0; i < craterCount; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double dist = radius * random.nextDouble() * 0.72;
            double x = cx + Math.cos(ang) * dist;
            double y = cy + Math.sin(ang) * dist;
            double size = radius * (0.05 + random.nextDouble() * 0.11);
            g2.setColor(new Color(26, 34, 42, 24));
            g2.fill(new Ellipse2D.Double(x - size * 0.58, y - size * 0.42, size * 1.16, size * 0.84));
            g2.setColor(new Color(230, 236, 242, 16));
            g2.draw(new Ellipse2D.Double(x - size * 0.60, y - size * 0.44, size * 1.20, size * 0.88));
        }
    }

    private static void drawBackdropAgriculture(Graphics2D g2, double cx, double cy, double radius, long seed, double blend) {
        Random random = new Random(seed ^ 0x6A1CF42DL);
        for (int i = 0; i < 9; i++) {
            double x = cx - radius * 0.65 + random.nextDouble() * radius * 1.15;
            double y = cy - radius * 0.25 + random.nextDouble() * radius * 0.70;
            double w = radius * (0.16 + random.nextDouble() * 0.14);
            double h = radius * (0.04 + random.nextDouble() * 0.06);
            Color band = new Color(76, 128 + random.nextInt(36), 88 + random.nextInt(24), MathUtil.clamp((int) Math.round(18 + 10 * blend), 0, 72));
            g2.setColor(band);
            g2.fill(new RoundRectangle2D.Double(x - w / 2.0, y - h / 2.0, w, h, h, h));
        }
    }

    private static void drawBackdropCityLights(Graphics2D g2, double cx, double cy, double radius,
                                               CelestialBackdropSpec spec, double blend, long seed) {
        Random random = new Random(seed ^ 0x91B4E12DL);
        int streets = 36;
        for (int i = 0; i < streets; i++) {
            double x1 = cx - radius * 0.40 + random.nextDouble() * radius * 0.86;
            double y1 = cy + radius * 0.04 + random.nextDouble() * radius * 0.46;
            double x2 = x1 + radius * (0.07 + random.nextDouble() * 0.18);
            double y2 = y1 + radius * (-0.02 + random.nextDouble() * 0.05);
            int alpha = MathUtil.clamp((int) Math.round(28 + 58 * (0.45 + blend)), 0, 128);
            g2.setColor(new Color(255, 214, 138, alpha));
            g2.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
        }
        int clusters = 6 + (int) Math.round(spec.infrastructureDensity * 8.0);
        for (int i = 0; i < clusters; i++) {
            double x = cx - radius * 0.42 + random.nextDouble() * radius * 0.86;
            double y = cy + radius * 0.10 + random.nextDouble() * radius * 0.36;
            double size = radius * (0.08 + random.nextDouble() * 0.08);
            drawBackdropCityCluster(g2, x, y, size, blend, random);
        }
        for (int i = 0; i < 12; i++) {
            double x = cx - radius * 0.38 + random.nextDouble() * radius * 0.80;
            double y = cy + radius * 0.06 + random.nextDouble() * radius * 0.42;
            double s = radius * (0.010 + random.nextDouble() * 0.018);
            g2.setColor(new Color(255, 234, 176, MathUtil.clamp((int) Math.round(38 + 66 * blend), 0, 156)));
            g2.fill(new Ellipse2D.Double(x, y, s * 1.8, s));
        }
    }

    private static void drawBackdropCloudDeck(Graphics2D g2, double cx, double cy, double radius,
                                              CelestialBackdropSpec spec, double blend, long seed) {
        Random random = new Random(seed ^ 0x17ABF02DL);
        int clouds = (spec.kind == BackdropBodyKind.GAS_GIANT) ? 10 : 6;
        for (int i = 0; i < clouds; i++) {
            double x = cx - radius * 0.78 + random.nextDouble() * radius * 1.36;
            double y = cy - radius * 0.30 + random.nextDouble() * radius * 0.86;
            double w = radius * (0.24 + random.nextDouble() * 0.34);
            double h = radius * (0.06 + random.nextDouble() * 0.11);
            int alpha = MathUtil.clamp((int) Math.round((spec.kind == BackdropBodyKind.GAS_GIANT ? 18 : 14) + 16 * blend), 0, 72);
            g2.setColor(new Color(240, 246, 255, alpha));
            g2.fill(new Ellipse2D.Double(x, y, w, h));
        }
    }

    private static void drawBackdropInfrastructure(Graphics2D g2, double cx, double cy, double radius,
                                                   CelestialBackdropSpec spec, double blend, long seed) {
        if (spec.infrastructureDensity <= 0.01) return;
        Random random = new Random(seed ^ 0x5B2A73DCL);
        Stroke oldStroke = g2.getStroke();
        int rings = 1 + (int) Math.round(spec.infrastructureDensity * 2.5);
        for (int i = 0; i < rings; i++) {
            double orbit = radius * (1.14 + i * 0.12);
            int alpha = MathUtil.clamp((int) Math.round(18 + 56 * spec.infrastructureDensity + 18 * blend), 0, 120);
            float strokeW = (float) (1.1 + i * 0.35);
            g2.setStroke(new BasicStroke(strokeW));
            g2.setColor(new Color(168, 212, 255, alpha));
            double arcW = orbit * 2.36;
            double arcH = orbit * 0.68;
            drawBackdropArcBand(g2, cx - arcW / 2.0, cy - arcH / 2.0, arcW, arcH, i * 18.0, 112.0);
            drawBackdropArcBand(g2, cx - arcW / 2.0, cy - arcH / 2.0, arcW, arcH, 180.0 + i * 14.0, 92.0);
            if (spec.infrastructureDensity >= 0.55) {
                g2.setColor(new Color(206, 232, 255, MathUtil.clamp(alpha + 16, 0, 148)));
                drawBackdropArcBand(g2, cx - arcW / 2.0, cy - arcH / 2.0, arcW, arcH, 300.0 - i * 10.0, 42.0);
            }
            int nodes = 4 + random.nextInt(4) + (spec.infrastructureDensity >= 0.7 ? 1 : 0);
            for (int n = 0; n < nodes; n++) {
                double ang = (Math.PI * 2.0 * n / nodes) + random.nextDouble() * 0.12;
                double nx = cx + Math.cos(ang) * orbit * 1.02;
                double ny = cy + Math.sin(ang) * orbit * 0.28;
                double size = 5.0 + random.nextDouble() * 5.0;
                drawBackdropStationGlyph(g2, nx, ny, size, MathUtil.clamp(alpha + 28, 0, 180));
                if (spec.infrastructureDensity >= 0.72) {
                    g2.setColor(new Color(136, 200, 255, MathUtil.clamp(alpha / 2, 0, 90)));
                    g2.draw(new java.awt.geom.Line2D.Double(cx, cy, nx, ny));
                }
            }
        }
        if (spec.rings) {
            double ringR = radius * 1.45;
            g2.setStroke(new BasicStroke((float) Math.max(2.0, radius * 0.018)));
            g2.setColor(new Color(215, 232, 255, MathUtil.clamp((int) Math.round(26 + 44 * spec.infrastructureDensity), 0, 120)));
            double ringW = ringR * 2.36;
            double ringH = ringR * 0.60;
            drawBackdropArcBand(g2, cx - ringW / 2.0, cy - ringH / 2.0, ringW, ringH, 18.0, 138.0);
            drawBackdropArcBand(g2, cx - ringW / 2.0, cy - ringH / 2.0, ringW, ringH, 198.0, 122.0);
        }
        if (spec.infrastructureDensity >= 0.65) {
            double haloR = radius * 1.62;
            g2.setStroke(new BasicStroke((float) Math.max(1.4, radius * 0.012), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{10f, 12f}, 0f));
            g2.setColor(new Color(122, 210, 255, MathUtil.clamp((int) Math.round(18 + 54 * spec.infrastructureDensity), 0, 110)));
            drawBackdropArcBand(g2, cx - haloR, cy - haloR, haloR * 2.0, haloR * 2.0, 28.0, 64.0);
            drawBackdropArcBand(g2, cx - haloR, cy - haloR, haloR * 2.0, haloR * 2.0, 214.0, 58.0);
        }
        g2.setStroke(oldStroke);
    }

    private static void drawBackdropLanes(Graphics2D g2, double cx, double cy, double radius,
                                          CelestialBackdropSpec spec, double blend, long seed) {
        if (spec.trafficDensity <= 0.01) return;
        Random random = new Random(seed ^ 0x3D6E18AAL);
        Stroke oldStroke = g2.getStroke();
        for (int i = 0; i < 3; i++) {
            double orbit = radius * (1.34 + i * 0.16);
            float dash = (float) Math.max(8.0, radius * 0.05);
            g2.setStroke(new BasicStroke(1.0f + i * 0.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash * 1.7f}, i * 6f));
            g2.setColor(new Color(124, 214, 255, MathUtil.clamp((int) Math.round(16 + 54 * spec.trafficDensity), 0, 108)));
            g2.draw(new Ellipse2D.Double(cx - orbit * 1.10, cy - orbit * 0.24, orbit * 2.20, orbit * 0.48));
        }
        int freighters = 4 + random.nextInt(4);
        g2.setStroke(oldStroke);
        for (int i = 0; i < freighters; i++) {
            double orbit = radius * (1.35 + random.nextDouble() * 0.32);
            double ang = random.nextDouble() * Math.PI * 2.0 + blend * 0.65;
            double x = cx + Math.cos(ang) * orbit;
            double y = cy + Math.sin(ang) * orbit * 0.24;
            double dx = Math.cos(ang) * 10.0;
            double dy = Math.sin(ang) * 2.4;
            g2.setColor(new Color(255, 244, 188, MathUtil.clamp((int) Math.round(42 + 44 * spec.trafficDensity), 0, 156)));
            g2.draw(new java.awt.geom.Line2D.Double(x - dx, y - dy, x + dx, y + dy));
        }
        if (spec.trafficDensity >= 0.45) {
            g2.setColor(new Color(170, 222, 255, MathUtil.clamp((int) Math.round(18 + 62 * spec.trafficDensity), 0, 128)));
            g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{7f, 14f}, 0f));
            g2.draw(new java.awt.geom.Line2D.Double(cx - radius * 1.45, cy + radius * 0.18, cx + radius * 1.54, cy - radius * 0.62));
            g2.draw(new java.awt.geom.Line2D.Double(cx - radius * 1.30, cy - radius * 0.06, cx + radius * 1.44, cy + radius * 0.46));
        }
    }

    private static void drawBackdropArcBand(Graphics2D g2, double x, double y, double w, double h, double start, double extent) {
        g2.draw(new java.awt.geom.Arc2D.Double(x, y, w, h, start, extent, java.awt.geom.Arc2D.OPEN));
    }

    private static void drawBackdropStationGlyph(Graphics2D g2, double x, double y, double size, int alpha) {
        double w = size;
        double h = size * 0.48;
        g2.setColor(new Color(220, 242, 255, MathUtil.clamp(alpha, 0, 180)));
        g2.fill(new Rectangle2D.Double(x - w / 2.0, y - h / 2.0, w, h));
        g2.setColor(new Color(122, 198, 255, MathUtil.clamp(alpha / 2, 0, 120)));
        g2.draw(new java.awt.geom.Line2D.Double(x - w * 0.72, y, x + w * 0.72, y));
        g2.draw(new java.awt.geom.Line2D.Double(x, y - h * 1.3, x, y + h * 1.3));
    }

    private static void drawBackdropCityCluster(Graphics2D g2, double x, double y, double size, double blend, Random random) {
        int blocks = 3 + random.nextInt(3);
        for (int i = 0; i < blocks; i++) {
            double bx = x - size * 0.5 + random.nextDouble() * size;
            double by = y - size * 0.35 + random.nextDouble() * size * 0.7;
            double bw = size * (0.20 + random.nextDouble() * 0.22);
            double bh = size * (0.08 + random.nextDouble() * 0.14);
            int alpha = MathUtil.clamp((int) Math.round(24 + 58 * (0.35 + blend)), 0, 132);
            g2.setColor(new Color(255, 210, 132, alpha));
            g2.fill(new Rectangle2D.Double(bx, by, bw, bh));
        }
        g2.setColor(new Color(255, 236, 182, MathUtil.clamp((int) Math.round(18 + 34 * blend), 0, 120)));
        g2.draw(new java.awt.geom.Line2D.Double(x - size * 0.6, y, x + size * 0.6, y));
        g2.draw(new java.awt.geom.Line2D.Double(x, y - size * 0.38, x, y + size * 0.38));
    }

    private static CampaignBackdropSpec resolveCampaignBackdropSpec(GameContext ctx) {
        if (ctx == null || !CampaignSystem.isCampaignActive(ctx)) return null;
        int sector = CampaignSystem.activeSector(ctx);
        int stage = CampaignSystem.objectiveStage(ctx);
        double progress = CampaignSystem.objectiveProgressRatio(ctx);
        double elapsed = CampaignSystem.sectorElapsedRatio(ctx);

        return switch (sector) {
            case 1 -> new CampaignBackdropSpec(
                    "trade_hub_colony",
                    elapsed * 0.35,
                    new Color(10, 20, 26, 28),
                    BackdropFieldMode.COLONY_ARCOLOGY,
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 1.05, 0.96, 370.0, 0.018,
                            new Color(58, 106, 122, 255), new Color(170, 202, 208, 255),
                            new Color(156, 224, 232, 168), new Color(72, 160, 196, 74),
                            true, true, true, true, 0.90, 0.75),
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.24, -0.08, 120.0, 0.010,
                            new Color(98, 112, 132, 255), new Color(188, 198, 214, 255),
                            new Color(180, 206, 255, 90), new Color(124, 146, 210, 28),
                            false, false, false, false, 0.15, 0.0));
            case 2, 3 -> new CampaignBackdropSpec(
                    "jump_ring_frontier",
                    elapsed * 0.30,
                    new Color(12, 18, 30, 22),
                    BackdropFieldMode.COLONY_ARCOLOGY,
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 1.12, 0.82, 300.0, 0.022,
                            new Color(72, 78, 98, 255), new Color(182, 190, 222, 255),
                            new Color(154, 188, 236, 148), new Color(92, 118, 182, 54),
                            true, true, true, false, 0.58, 0.48),
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.18, 0.10, 96.0, 0.012,
                            new Color(128, 102, 82, 255), new Color(220, 186, 154, 255),
                            new Color(220, 190, 170, 92), new Color(184, 142, 120, 32),
                            false, false, false, false, 0.0, 0.0));
            case 4, 5 -> new CampaignBackdropSpec(
                    "relay_halo_moon",
                    0.0,
                    new Color(8, 18, 32, 18),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 1.08, 0.20, 240.0, 0.014,
                            new Color(82, 106, 126, 255), new Color(190, 216, 230, 255),
                            new Color(170, 220, 255, 124), new Color(94, 154, 192, 34),
                            true, false, false, false, 0.72, 0.52),
                    null);
            case 6, 7 -> new CampaignBackdropSpec(
                    "burning_debris_wake",
                    elapsed * 0.25,
                    new Color(34, 12, 10, 34),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 1.18, 0.96, 330.0, 0.020,
                            new Color(92, 58, 52, 255), new Color(212, 144, 118, 255),
                            new Color(226, 138, 98, 110), new Color(204, 106, 74, 64),
                            false, false, false, false, 0.14, 0.10),
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, -0.10, -0.14, 150.0, 0.008,
                            new Color(52, 52, 68, 255), new Color(142, 148, 178, 255),
                            new Color(160, 168, 220, 84), new Color(122, 126, 180, 22),
                            false, false, false, false, 0.0, 0.0));
            case 8 -> new CampaignBackdropSpec(
                    "exodus_gas_giant",
                    progress,
                    new Color(12, 18, 22, 20),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.GAS_GIANT, 1.05, 0.82, 430.0, 0.016,
                            new Color(76, 118, 108, 255), new Color(190, 220, 184, 255),
                            new Color(174, 232, 210, 120), new Color(98, 186, 160, 56),
                            true, false, true, false, 0.38, 0.42),
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.24, 0.14, 102.0, 0.010,
                            new Color(128, 146, 154, 255), new Color(220, 232, 232, 255),
                            new Color(210, 228, 255, 88), new Color(132, 168, 186, 22),
                            false, false, false, false, 0.0, 0.0));
            case 9, 10, 11 -> new CampaignBackdropSpec(
                    "trade_spine_industrial_orbit",
                    elapsed * 0.28,
                    new Color(18, 18, 26, 24),
                    BackdropFieldMode.INDUSTRIAL_YARDS,
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 1.10, 0.90, 340.0, 0.019,
                            new Color(80, 92, 104, 255), new Color(198, 206, 220, 255),
                            new Color(186, 210, 244, 124), new Color(124, 148, 196, 46),
                            true, true, true, false, 0.88, 0.68),
                    null);
            case 12, 13, 14 -> {
                double blend = (stage <= 0) ? 0.0 : MathUtil.clamp(0.28 + progress * 0.72, 0.0, 1.0);
                yield new CampaignBackdropSpec(
                        "contract_world_array",
                        blend,
                        new Color(8, 26, 20, 24 + (int) Math.round(18 * blend)),
                        BackdropFieldMode.COLONY_ARCOLOGY,
                        new CelestialBackdropSpec(BackdropBodyKind.PLANET, 1.02 - blend * 0.06, 0.84 - blend * 0.03, 360.0 + blend * 20.0, 0.017,
                                new Color(68, 116, 96, 255), new Color(198, 234, 222, 255),
                                new Color(156, 232, 210, 136), new Color(76, 180, 128, 60),
                                true, true, true, true, 0.62 + blend * 0.34, 0.42 + blend * 0.28),
                        new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.18 + blend * 0.06, 0.08, 112.0, 0.011,
                                new Color(96, 132, 128, 255), new Color(206, 234, 226, 255),
                                new Color(184, 238, 230, 96), new Color(112, 182, 170, 26),
                                false, false, false, false, 0.0, 0.0));
            }
            case 15, 16 -> new CampaignBackdropSpec(
                    "ash_gate_gas_giant",
                    elapsed * 0.22,
                    new Color(26, 14, 10, 32),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.GAS_GIANT, 1.06, 0.84, 410.0, 0.015,
                            new Color(104, 82, 68, 255), new Color(224, 182, 124, 255),
                            new Color(232, 170, 114, 118), new Color(214, 122, 82, 58),
                            true, false, true, false, 0.44, 0.30),
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.22, 0.14, 118.0, 0.010,
                            new Color(122, 92, 76, 255), new Color(216, 166, 126, 255),
                            new Color(220, 176, 130, 82), new Color(196, 134, 84, 22),
                            false, false, false, false, 0.0, 0.0));
            case 17, 18 -> new CampaignBackdropSpec(
                    "outer_sol_starline",
                    progress,
                    new Color(30, 20, 6, 34),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.STAR, 1.24, -0.18, 330.0, 0.006,
                            new Color(255, 206, 120, 255), new Color(255, 246, 214, 255),
                            new Color(255, 206, 122, 120), new Color(255, 168, 82, 120),
                            false, false, false, false, 0.0, 0.0),
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 0.08, 1.12, 210.0, 0.014,
                            new Color(48, 66, 90, 255), new Color(140, 180, 220, 255),
                            new Color(156, 206, 240, 96), new Color(98, 146, 202, 24),
                            false, false, true, false, 0.0, 0.0));
            case 19, 20 -> new CampaignBackdropSpec(
                    "liberation_moon_orbit",
                    progress,
                    new Color(20, 18, 12, 24),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 1.00, 0.90, 320.0, 0.018,
                            new Color(112, 118, 90, 255), new Color(226, 228, 176, 255),
                            new Color(244, 228, 158, 110), new Color(210, 182, 92, 42),
                            true, false, false, false, 0.54, 0.58),
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 0.10, -0.10, 140.0, 0.009,
                            new Color(78, 96, 120, 255), new Color(182, 200, 228, 255),
                            new Color(188, 212, 244, 78), new Color(122, 146, 198, 24),
                            false, false, false, false, 0.0, 0.0));
            case 21, 22 -> {
                double blend = (stage <= 0) ? 0.0 : MathUtil.clamp(0.18 + progress * 0.82, 0.0, 1.0);
                yield new CampaignBackdropSpec(
                        "luna_earthrise_approach",
                        blend,
                        new Color(18, 20, 28, 20 + (int) Math.round(14 * blend)),
                        BackdropFieldMode.LUNAR_INSTALLATION,
                        new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.96 - blend * 0.18, 0.70 - blend * 0.10, 260.0 - blend * 36.0, 0.014,
                                new Color(168, 174, 180, 255), new Color(242, 244, 248, 255),
                                new Color(220, 228, 255, 96), new Color(186, 194, 224, 26),
                                false, false, false, false, 0.46 + blend * 0.12, 0.18),
                        new CelestialBackdropSpec(BackdropBodyKind.PLANET, 1.18 - blend * 0.28, 1.06 - blend * 0.20, 220.0 + blend * 125.0, 0.010,
                                new Color(58, 92, 132, 255), new Color(182, 220, 246, 255),
                                new Color(164, 214, 248, 142), new Color(92, 154, 212, 56),
                                false, true, true, true, 0.68 + blend * 0.20, 0.36 + blend * 0.18));
            }
            case 23, 24 -> new CampaignBackdropSpec(
                    "earth_high_orbit",
                    1.0,
                    new Color(16, 24, 32, 26),
                    BackdropFieldMode.HOMEWORLD_CITYLIGHTS,
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 0.92, 0.86, 420.0, 0.012,
                            new Color(62, 100, 136, 255), new Color(196, 232, 250, 255),
                            new Color(170, 220, 252, 148), new Color(92, 162, 220, 64),
                            true, true, true, true, 0.96, 0.70),
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 0.18, 0.18, 120.0, 0.008,
                            new Color(180, 184, 192, 255), new Color(246, 248, 252, 255),
                            new Color(226, 232, 255, 84), new Color(194, 202, 228, 24),
                            false, false, false, false, 0.08, 0.0));
            default -> null;
        };
    }

    private static Color scaleAlpha(Color color, double factor) {
        if (color == null) return new Color(255, 255, 255, 0);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                MathUtil.clamp((int) Math.round(color.getAlpha() * factor), 0, 255));
    }

    private static Color darken(Color color, double factor) {
        if (color == null) return Color.BLACK;
        return new Color(
                MathUtil.clamp((int) Math.round(color.getRed() * factor), 0, 255),
                MathUtil.clamp((int) Math.round(color.getGreen() * factor), 0, 255),
                MathUtil.clamp((int) Math.round(color.getBlue() * factor), 0, 255),
                color.getAlpha());
    }

    private static Color blend(Color a, Color b, double t) {
        if (a == null) return (b == null) ? Color.WHITE : b;
        if (b == null) return a;
        double clamped = MathUtil.clamp(t, 0.0, 1.0);
        return new Color(
                MathUtil.clamp((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped), 0, 255),
                MathUtil.clamp((int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped), 0, 255),
                MathUtil.clamp((int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped), 0, 255),
                MathUtil.clamp((int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * clamped), 0, 255));
    }

    private static void drawAsteroidSprite(Graphics2D g2, Asteroid a, BufferedImage skin) {
        int x = (int) Math.round(a.x);
        int y = (int) Math.round(a.y);
        int draw = Math.max(14, (int) Math.round(a.radius * 3.0));

        Graphics2D ga = (Graphics2D) g2.create();
        ga.translate(x, y);
        ga.rotate(a.spin * 0.35);
        ga.drawImage(skin, -draw / 2, -draw / 2, draw, draw, null);

        double frac = (a.oreMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) a.ore / (double) a.oreMax));
        if (a.rich && frac > 0.05) {
            int rr = Math.max(8, (int) Math.round(a.radius * 1.28));
            int alpha = MathUtil.clamp((int) Math.round(24 + 72 * frac), 0, 140);
            ga.setColor(new Color(255, 210, 120, alpha));
            ga.drawOval(-rr, -rr, rr * 2, rr * 2);
        }

        ga.dispose();
    }

    private static Asteroid findNearbyAsteroidPromptTarget(List<Asteroid> asteroids, Player player) {
        if (asteroids == null || asteroids.isEmpty() || player == null) return null;
        if (!player.alive || player.dying || player.hp <= 0) return null;
        double range = Math.max(150.0, player.miningRange + 48.0);
        double bestD2 = range * range;
        Asteroid best = null;
        for (Asteroid a : asteroids) {
            if (a == null || a.ore <= 0) continue;
            double d2 = GameMath.dist2(player.x, player.y, a.x, a.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = a;
            }
        }
        return best;
    }

    private static void drawAsteroidMinePrompt(Graphics2D g2, Asteroid asteroid) {
        if (g2 == null || asteroid == null) return;
        String label = "ORE [F]";
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();

        int tx = (int) Math.round(asteroid.x - fm.stringWidth(label) / 2.0);
        int ty = (int) Math.round(asteroid.y - asteroid.radius - 12.0);
        int pad = 6;
        int bw = fm.stringWidth(label) + pad * 2;
        int bh = 16;
        int bx = tx - pad;
        int by = ty - fm.getAscent() + 1;

        g2.setColor(new Color(8, 10, 16, 172));
        g2.fillRoundRect(bx, by, bw, bh, 10, 10);
        g2.setColor(new Color(168, 218, 255, 176));
        g2.drawRoundRect(bx, by, bw, bh, 10, 10);
        g2.setColor(new Color(255, 244, 170, 226));
        g2.drawString(label, tx, ty);

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    // ------------------------------
    // Salvage pickups (random events)
    // ------------------------------

    public static int drawSalvage(Graphics2D g2, List<Salvage> salvage) {
        return drawSalvage(g2, salvage, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static int drawSalvage(Graphics2D g2, List<Salvage> salvage,
                                  double minX, double minY, double maxX, double maxY) {
        if (salvage == null) return 0;
        int drawn = 0;
        for (Salvage s : salvage) {
            if (s == null || !s.alive()) continue;
            if (!isWorldCircleVisible(s.x, s.y, s.radius + 18.0, minX, minY, maxX, maxY)) continue;

            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y);
            int r = (int) Math.round(s.radius);

            // Soft glow
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillOval(x - r * 2, y - r * 2, r * 4, r * 4);

            // Diamond "crate"
            Polygon p = new Polygon();
            p.addPoint(x, y - r);
            p.addPoint(x + r, y);
            p.addPoint(x, y + r);
            p.addPoint(x - r, y);

            int a = (int) Math.round(160 + 80 * Math.max(0.0, Math.min(1.0, s.life / 25.0)));
            g2.setColor(new Color(220, 240, 255, MathUtil.clamp(a, 0, 240)));
            g2.fillPolygon(p);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawPolygon(p);

            // Tiny hint for valuable drops
            if (s.credits >= 500 || s.ore >= 80) {
                g2.setColor(new Color(255, 220, 120, 60));
                g2.drawOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);
            }
            drawn++;
        }
        return drawn;
    }


    public static int drawProjectiles(Graphics2D g2, List<Projectile> projectiles) {
        return drawProjectiles(g2, null, projectiles, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, null, null);
    }

    public static int drawProjectiles(Graphics2D g2, List<Projectile> projectiles,
                                      double minX, double minY, double maxX, double maxY) {
        return drawProjectiles(g2, null, projectiles, minX, minY, maxX, maxY, null, null);
    }

    public static int drawProjectiles(Graphics2D g2, List<Projectile> projectiles,
                                      double minX, double minY, double maxX, double maxY,
                                      FogOfWarSystem.State fog, Faction perspective) {
        return drawProjectiles(g2, null, projectiles, minX, minY, maxX, maxY, fog, perspective);
    }

    public static int drawProjectiles(Graphics2D g2, List<Ship> ships, List<Projectile> projectiles,
                                      double minX, double minY, double maxX, double maxY,
                                      FogOfWarSystem.State fog, Faction perspective) {
        if (projectiles == null) return 0;
        
        // Count CIWS pellets to determine if we should use simplified rendering in heavy combat
        int ciwsPelletCount = 0;
        for (Projectile p : projectiles) {
            if (p instanceof CIWSPellet) ciwsPelletCount++;
        }
        boolean heavyCombatwithCIWS = ciwsPelletCount > 80;
        
        int drawn = 0;
        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (!isProjectileVisible(p, fog, perspective, minX, minY, maxX, maxY)) continue;

            if (p instanceof CIWSPellet pellet) {
                int r = (int) Math.round(Math.max(1.0, pellet.radius));
                int x = (int) Math.round(pellet.x);
                int y = (int) Math.round(pellet.y);
                Color core = mixColor(projectileCoreColor(pellet.faction), Color.WHITE, 0.42);
                
                // In heavy CIWS combat, use simplified rendering (just solid dots)
                if (heavyCombatwithCIWS) {
                    g2.setColor(withAlpha(core, 200));
                    g2.fillOval(x - r, y - r, r * 2, r * 2);
                    drawn++;
                    continue;
                }
                
                Color trail = projectileTrailColor(pellet.faction);
                double speed = Math.hypot(pellet.vx, pellet.vy);
                double trailLen = Math.max(8.0, Math.min(22.0, 8.0 + speed * 0.16));
                double nx = Math.cos(pellet.angle);
                double ny = Math.sin(pellet.angle);

                BufferedImage skin = ProjectileSkinLibrary.getCiwsPelletSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, pellet.x, pellet.y, pellet.angle,
                            Math.max(5.0, r * 2.7), Math.max(2.0, r * 1.3), 0.95f);
                }

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(Math.max(1.2f, r * 0.9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(trail, 145));
                g2.drawLine(x, y,
                        (int) Math.round(pellet.x - nx * trailLen),
                        (int) Math.round(pellet.y - ny * trailLen));

                g2.setStroke(new BasicStroke(Math.max(1.0f, r * 0.55f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 185));
                g2.drawLine(x, y,
                        (int) Math.round(pellet.x - nx * (trailLen * 0.55)),
                        (int) Math.round(pellet.y - ny * (trailLen * 0.55)));
                g2.setStroke(old);

                g2.setColor(withAlpha(core, 228));
                g2.fillOval(x - r, y - r, r * 2, r * 2);
                drawn++;
                continue;
            }

            if (p instanceof PhaserBeam beam) {
                drawPhaserBeam(g2, beam);
                drawn++;
                continue;
            }
            if (p instanceof PointDefenseLaser laser) {
                drawPointDefenseLaser(g2, laser);
                drawn++;
                continue;
            }
            if (p instanceof DisruptorSlug slug) {
                drawDisruptorSlug(g2, slug);
                drawn++;
                continue;
            }
            if (p instanceof DestabilizerPulse pulse) {
                drawDestabilizerPulse(g2, pulse);
                drawn++;
                continue;
            }

            if (p instanceof Missile m) {
                drawMissile(g2, m);
                drawn++;
            } else if (p instanceof SuperweaponShot ws) {
                int x = (int) Math.round(ws.x);
                int y = (int) Math.round(ws.y);
                double nx = Math.cos(ws.angle);
                double ny = Math.sin(ws.angle);
                Color beam = beamColorForFaction(ws.faction);
                Color hot = mixColor(beam, Color.WHITE, 0.76);
                double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 9.5);

                int len = (int) Math.round(Math.max(30.0, ws.radius * 5.6));
                int tail = len / 2;
                int head = len / 2;

                int x1 = (int) Math.round(ws.x - nx * tail);
                int y1 = (int) Math.round(ws.y - ny * tail);
                int x2 = (int) Math.round(ws.x + nx * head);
                int y2 = (int) Math.round(ws.y + ny * head);

                BufferedImage skin = ProjectileSkinLibrary.getWaveShotSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, ws.x, ws.y, ws.angle,
                            Math.max(28.0, ws.radius * 5.8), Math.max(8.0, ws.radius * 2.8), 0.92f);
                }

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke((float) Math.max(6.0, ws.radius * 2.5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(beam, (int) Math.round(115 + pulse * 28)));
                g2.drawLine(x1, y1, x2, y2);

                g2.setStroke(new BasicStroke((float) Math.max(3.2, ws.radius * 1.3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(hot, 236));
                g2.drawLine(x1, y1, x2, y2);

                g2.setStroke(new BasicStroke((float) Math.max(1.4, ws.radius * 0.58), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(Color.WHITE, 190));
                g2.drawLine(x1, y1, x2, y2);

                int glow = (int) Math.round(Math.max(8.0, ws.radius * 1.6));
                g2.setColor(withAlpha(mixColor(beam, Color.WHITE, 0.26), (int) Math.round(150 + pulse * 30)));
                g2.fillOval(x - glow, y - glow, glow * 2, glow * 2);
                g2.setStroke(old);
                drawn++;
            } else if (p instanceof EnergyBolt eb) {
                drawEnergyBolt(g2, eb, false, ships, fog, perspective);
                drawn++;
            } else {
                // Bullet / generic projectile with a small motion trail
                int r = (int) Math.round(Math.max(1.0, p.radius));
                int x = (int) Math.round(p.x);
                int y = (int) Math.round(p.y);
                double speed = Math.hypot(p.vx, p.vy);
                double nx = (speed > 1e-6) ? p.vx / speed : 1.0;
                double ny = (speed > 1e-6) ? p.vy / speed : 0.0;
                double trailLen = Math.max(6.0, Math.min(28.0, 7.0 + speed * 0.15));
                Color trail = projectileTrailColor(p.faction);
                Color core = projectileCoreColor(p.faction);

                BufferedImage skin = ProjectileSkinLibrary.getBulletSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, p.x, p.y, Math.atan2(ny, nx),
                            Math.max(5.0, r * 2.0), Math.max(2.0, r * 1.2), 0.9f);
                }

                int tx = (int) Math.round(p.x - nx * trailLen);
                int ty = (int) Math.round(p.y - ny * trailLen);
                int tx2 = (int) Math.round(p.x - nx * (trailLen * 0.56));
                int ty2 = (int) Math.round(p.y - ny * (trailLen * 0.56));

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(Math.max(1.1f, r * 0.86f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(trail, 132));
                g2.drawLine(tx, ty, x, y);

                g2.setStroke(new BasicStroke(Math.max(1.0f, r * 0.50f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 172));
                g2.drawLine(tx2, ty2, x, y);
                g2.setStroke(old);

                g2.setColor(withAlpha(core, 224));
                g2.fillOval(x - r, y - r, r * 2, r * 2);
                drawn++;
            }
        }
        return drawn;
    }

    static int drawTacticalProjectiles(Graphics2D g2, List<Projectile> projectiles,
                                       double minX, double minY, double maxX, double maxY) {
        return drawTacticalProjectiles(g2, null, projectiles, minX, minY, maxX, maxY, null, null);
    }

    static int drawTacticalProjectiles(Graphics2D g2, List<Projectile> projectiles,
                                       double minX, double minY, double maxX, double maxY,
                                       FogOfWarSystem.State fog, Faction perspective) {
        return drawTacticalProjectiles(g2, null, projectiles, minX, minY, maxX, maxY, fog, perspective);
    }

    static int drawTacticalProjectiles(Graphics2D g2, List<Ship> ships, List<Projectile> projectiles,
                                       double minX, double minY, double maxX, double maxY,
                                       FogOfWarSystem.State fog, Faction perspective) {
        if (projectiles == null) return 0;
        int drawn = 0;
        for (Projectile p : projectiles) {
            if (p == null || !p.alive) continue;
            if (!isProjectileVisible(p, fog, perspective, minX, minY, maxX, maxY)) continue;
            drawTacticalProjectile(g2, p, ships, fog, perspective);
            drawn++;
        }
        return drawn;
    }

    private static void drawPhaserBeam(Graphics2D g2, PhaserBeam beam) {
        if (g2 == null || beam == null || !beam.alive) return;

        double sx = beam.startX();
        double sy = beam.startY();
        double ex = beam.endX();
        double ey = beam.endY();
        boolean hyperLance = beam.isHyperLanceBeam();
        Color base = hyperLance ? new Color(122, 232, 255) : beamColorForFaction(beam.faction);
        Color hot = mixColor(base, Color.WHITE, hyperLance ? 0.82 : 0.72);
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 10.0);
        float width = (float) Math.max(1.6, beam.width * (hyperLance ? (0.92 + 0.18 * pulse) : (0.70 + 0.10 * pulse)));

        Stroke old = g2.getStroke();

        g2.setStroke(new BasicStroke(width * (hyperLance ? 2.10f : 1.55f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, (int) Math.round((hyperLance ? 62 : 38) + pulse * (hyperLance ? 24 : 14))));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(width * (hyperLance ? 1.05f : 0.88f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, hyperLance ? 210 : 186));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(Math.max(0.9f, width * (hyperLance ? 0.44f : 0.34f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(Color.WHITE, hyperLance ? 182 : 144));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int glowR = (int) Math.round(Math.max(hyperLance ? 6.0 : 3.0, beam.width * (hyperLance ? 1.25 : 0.90)));
        g2.setColor(withAlpha(base, hyperLance ? 118 : 92));
        g2.fillOval((int) Math.round(sx) - glowR, (int) Math.round(sy) - glowR, glowR * 2, glowR * 2);
        g2.setColor(withAlpha(hot, hyperLance ? 112 : 86));
        g2.fillOval((int) Math.round(ex) - glowR, (int) Math.round(ey) - glowR, glowR * 2, glowR * 2);
        if (hyperLance) {
            int terminalR = (int) Math.round(Math.max(12.0, beam.width * 1.7));
            g2.setColor(withAlpha(base, 74));
            g2.fillOval((int) Math.round(ex) - terminalR, (int) Math.round(ey) - terminalR, terminalR * 2, terminalR * 2);
            g2.setColor(withAlpha(Color.WHITE, 92));
            g2.drawOval((int) Math.round(ex) - terminalR, (int) Math.round(ey) - terminalR, terminalR * 2, terminalR * 2);
        }

        g2.setStroke(old);
    }

    private static void drawPointDefenseLaser(Graphics2D g2, PointDefenseLaser laser) {
        if (g2 == null || laser == null || !laser.alive) return;

        double sx = laser.startX();
        double sy = laser.startY();
        double ex = laser.endX;
        double ey = laser.endY;

        Color base = mixColor(beamColorForFaction(laser.faction), new Color(130, 245, 210), 0.34);
        Color hot = mixColor(base, Color.WHITE, 0.78);
        float width = (float) Math.max(0.9, laser.width * 0.82);

        Stroke old = g2.getStroke();

        g2.setStroke(new BasicStroke(width * 1.45f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, 72));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(Math.max(0.8f, width * 0.70f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, 184));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int r = (int) Math.round(Math.max(1.0, laser.width * 1.0));
        g2.setColor(withAlpha(hot, 132));
        g2.fillOval((int) Math.round(ex) - r, (int) Math.round(ey) - r, r * 2, r * 2);

        g2.setStroke(old);
    }

    private static void drawDisruptorSlug(Graphics2D g2, DisruptorSlug slug) {
        if (g2 == null || slug == null || !slug.alive) return;

        double time = System.nanoTime() * 1e-9;
        double motionAngle = Math.atan2(slug.vy, slug.vx);
        if (!Double.isFinite(motionAngle)) motionAngle = slug.angle;
        double nx = Math.cos(motionAngle);
        double ny = Math.sin(motionAngle);
        double pulse = 0.5 + 0.5 * Math.sin(time * 11.0 + slug.sourceShipId * 0.21);
        double shellRadius = Math.max(12.0, slug.radius * (0.94 + 0.10 * pulse));
        double auraRadius = shellRadius * 1.58;
        double coreRadius = shellRadius * 0.56;

        Color base = new Color(255, 46, 58);
        Color hot = mixColor(base, Color.WHITE, 0.58);
        Color corona = new Color(255, 98, 118);

        Graphics2D gx = (Graphics2D) g2.create();
        Paint oldPaint = gx.getPaint();
        Stroke oldStroke = gx.getStroke();

        double trailLen = Math.max(16.0, shellRadius * 1.8);
        gx.setStroke(new BasicStroke((float) Math.max(3.4, shellRadius * 0.32), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gx.setColor(withAlpha(corona, (int) Math.round(92 + pulse * 38)));
        gx.drawLine((int) Math.round(slug.x - nx * trailLen),
                (int) Math.round(slug.y - ny * trailLen),
                (int) Math.round(slug.x + nx * (shellRadius * 0.20)),
                (int) Math.round(slug.y + ny * (shellRadius * 0.20)));

        gx.setPaint(new RadialGradientPaint(
                new Point2D.Double(slug.x, slug.y),
                (float) auraRadius,
                new float[]{0.0f, 0.48f, 1.0f},
                new Color[]{
                        withAlpha(base, 0),
                        withAlpha(corona, (int) Math.round(96 + pulse * 34)),
                        withAlpha(base, 0)
                }));
        gx.fill(new Ellipse2D.Double(slug.x - auraRadius, slug.y - auraRadius, auraRadius * 2.0, auraRadius * 2.0));

        gx.setPaint(new RadialGradientPaint(
                new Point2D.Double(slug.x, slug.y),
                (float) shellRadius,
                new float[]{0.0f, 0.45f, 0.82f, 1.0f},
                new Color[]{
                        withAlpha(Color.WHITE, (int) Math.round(188 + pulse * 42)),
                        withAlpha(hot, 236),
                        withAlpha(base, 220),
                        withAlpha(new Color(140, 18, 26), 110)
                }));
        gx.fill(new Ellipse2D.Double(slug.x - shellRadius, slug.y - shellRadius, shellRadius * 2.0, shellRadius * 2.0));

        gx.setColor(withAlpha(Color.WHITE, (int) Math.round(140 + pulse * 48)));
        gx.fill(new Ellipse2D.Double(slug.x - coreRadius, slug.y - coreRadius, coreRadius * 2.0, coreRadius * 2.0));

        gx.setStroke(new BasicStroke((float) Math.max(1.8, shellRadius * 0.16), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gx.setColor(withAlpha(hot, 218));
        gx.draw(new Ellipse2D.Double(slug.x - shellRadius, slug.y - shellRadius, shellRadius * 2.0, shellRadius * 2.0));

        int arcCount = 7;
        for (int i = 0; i < arcCount; i++) {
            double arcAngle = time * (1.8 + i * 0.12) + i * (Math.PI * 2.0 / arcCount) + slug.sourceShipId * 0.07;
            double startRadius = shellRadius * (0.78 + 0.10 * Math.sin(time * 6.5 + i));
            double endRadius = shellRadius + 8.0 + shellRadius * (0.22 + 0.14 * Math.sin(time * 8.8 + i * 1.6));
            Path2D.Double bolt = new Path2D.Double();
            for (int step = 0; step < 5; step++) {
                double t = step / 4.0;
                double rr = startRadius + (endRadius - startRadius) * t;
                double tangent = (step == 0 || step == 4) ? 0.0
                        : Math.sin(time * 18.0 + i * 1.9 + step * 0.8) * shellRadius * 0.24;
                double px = slug.x + Math.cos(arcAngle) * rr - Math.sin(arcAngle) * tangent;
                double py = slug.y + Math.sin(arcAngle) * rr + Math.cos(arcAngle) * tangent;
                if (step == 0) bolt.moveTo(px, py);
                else bolt.lineTo(px, py);
            }

            gx.setStroke(new BasicStroke((float) Math.max(1.6, shellRadius * 0.12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gx.setColor(withAlpha(base, 190));
            gx.draw(bolt);
            gx.setStroke(new BasicStroke((float) Math.max(0.9, shellRadius * 0.06), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gx.setColor(withAlpha(Color.WHITE, 196));
            gx.draw(bolt);
        }

        gx.setPaint(oldPaint);
        gx.setStroke(oldStroke);
        gx.dispose();
    }

    private static void drawDestabilizerPulse(Graphics2D g2, DestabilizerPulse pulse) {
        if (g2 == null || pulse == null || !pulse.alive) return;

        double time = System.nanoTime() * 1e-9;
        double motionAngle = Math.atan2(pulse.vy, pulse.vx);
        if (!Double.isFinite(motionAngle)) motionAngle = pulse.angle;
        double nx = Math.cos(motionAngle);
        double ny = Math.sin(motionAngle);
        double shimmer = 0.5 + 0.5 * Math.sin(time * 8.8 + pulse.sourceShipId * 0.19);

        double auraRadius = Math.max(20.0, pulse.radius * (1.55 + 0.12 * shimmer));
        double shellRadius = auraRadius * 0.68;
        double coreRadius = shellRadius * 0.44;
        Color base = new Color(90, 190, 255);
        Color rim = new Color(188, 236, 255);
        Color hot = mixColor(base, Color.WHITE, 0.78);

        Graphics2D gx = (Graphics2D) g2.create();
        Paint oldPaint = gx.getPaint();
        Stroke oldStroke = gx.getStroke();

        double trailLen = Math.max(16.0, auraRadius * 1.35);
        gx.setStroke(new BasicStroke((float) Math.max(3.0, pulse.radius * 0.34), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gx.setColor(withAlpha(base, (int) Math.round(88 + shimmer * 42)));
        gx.drawLine((int) Math.round(pulse.x - nx * trailLen),
                (int) Math.round(pulse.y - ny * trailLen),
                (int) Math.round(pulse.x + nx * (shellRadius * 0.12)),
                (int) Math.round(pulse.y + ny * (shellRadius * 0.12)));

        gx.setPaint(new RadialGradientPaint(
                new Point2D.Double(pulse.x, pulse.y),
                (float) auraRadius,
                new float[]{0.0f, 0.42f, 1.0f},
                new Color[]{
                        withAlpha(base, 0),
                        withAlpha(base, (int) Math.round(102 + shimmer * 40)),
                        withAlpha(base, 0)
                }));
        gx.fill(new Ellipse2D.Double(pulse.x - auraRadius, pulse.y - auraRadius, auraRadius * 2.0, auraRadius * 2.0));

        gx.setPaint(new RadialGradientPaint(
                new Point2D.Double(pulse.x, pulse.y),
                (float) shellRadius,
                new float[]{0.0f, 0.52f, 0.86f, 1.0f},
                new Color[]{
                        withAlpha(Color.WHITE, (int) Math.round(204 + shimmer * 28)),
                        withAlpha(hot, 238),
                        withAlpha(rim, 172),
                        withAlpha(base, 0)
                }));
        gx.fill(new Ellipse2D.Double(pulse.x - shellRadius, pulse.y - shellRadius, shellRadius * 2.0, shellRadius * 2.0));

        gx.setColor(withAlpha(Color.WHITE, (int) Math.round(188 + shimmer * 32)));
        gx.fill(new Ellipse2D.Double(pulse.x - coreRadius, pulse.y - coreRadius, coreRadius * 2.0, coreRadius * 2.0));

        gx.setStroke(new BasicStroke((float) Math.max(1.6, pulse.radius * 0.18), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int arcCount = 5;
        for (int i = 0; i < arcCount; i++) {
            double theta = time * 1.5 + i * (Math.PI * 2.0 / arcCount);
            double inner = shellRadius * 0.85;
            double outer = auraRadius * 1.08;
            int x1 = (int) Math.round(pulse.x + Math.cos(theta) * inner);
            int y1 = (int) Math.round(pulse.y + Math.sin(theta) * inner);
            int x2 = (int) Math.round(pulse.x + Math.cos(theta + 0.30) * outer);
            int y2 = (int) Math.round(pulse.y + Math.sin(theta + 0.30) * outer);
            gx.setColor(withAlpha(rim, (int) Math.round(118 + shimmer * 48)));
            gx.drawLine(x1, y1, x2, y2);
        }

        gx.setPaint(oldPaint);
        gx.setStroke(oldStroke);
        gx.dispose();
    }

    public static void drawSuperweaponAimCue(Graphics2D g2, Player player, double cursorWorldX, double cursorWorldY) {
        if (g2 == null || player == null) return;
        if (!player.alive || player.dying || player.hp <= 0) return;
        if (!player.hasSuperweapon) return;
        if (!player.isSuperweaponCharging()) return;

        double aim = player.getSuperweaponAimAngle();
        double len = 2200.0;
        double sx = player.x + Math.cos(aim) * (player.radius + 10.0);
        double sy = player.y + Math.sin(aim) * (player.radius + 10.0);
        double ex = sx + Math.cos(aim) * len;
        double ey = sy + Math.sin(aim) * len;

        float chargeFrac = (float) Math.max(0.0, Math.min(1.0, player.getSuperweaponChargeProgress()));
        boolean charging = player.isSuperweaponCharging();
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 8.0);

        Stroke oldStroke = g2.getStroke();
        int warnAlpha = charging ? (int) Math.round(120 + 95 * Math.max(chargeFrac, pulse)) : 72;

        g2.setStroke(new BasicStroke(charging ? (7.2f + chargeFrac * 4.0f) : 5.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 32, 32, MathUtil.clamp(warnAlpha, 40, 230)));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(charging ? 2.8f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 210, 210, charging ? 200 : 130));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        if (Double.isFinite(cursorWorldX) && Double.isFinite(cursorWorldY)) {
            int cx = (int) Math.round(cursorWorldX);
            int cy = (int) Math.round(cursorWorldY);
            int r = charging ? 28 : 22;
            int r2 = charging ? 44 : 34;
            g2.setColor(new Color(255, 64, 64, charging ? 215 : 160));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(new Color(255, 140, 140, charging ? 180 : 120));
            g2.drawOval(cx - r2, cy - r2, r2 * 2, r2 * 2);
            int tick = charging ? 20 : 14;
            g2.drawLine(cx - tick, cy, cx - 5, cy);
            g2.drawLine(cx + 5, cy, cx + tick, cy);
            g2.drawLine(cx, cy - tick, cx, cy - 5);
            g2.drawLine(cx, cy + 5, cx, cy + tick);
        }

        g2.setStroke(oldStroke);
    }

    public static void drawNpcSuperweaponAimCues(Graphics2D g2, List<Ship> ships, Ship player) {
        drawNpcSuperweaponAimCues(g2, ships, player,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                null);
    }

    public static void drawNpcSuperweaponAimCues(Graphics2D g2, List<Ship> ships, Ship player,
                                                 double minX, double minY, double maxX, double maxY) {
        drawNpcSuperweaponAimCues(g2, ships, player, minX, minY, maxX, maxY, null);
    }

    public static void drawNpcSuperweaponAimCues(Graphics2D g2, List<Ship> ships, Ship player,
                                                 double minX, double minY, double maxX, double maxY,
                                                 FogOfWarSystem.State fog) {
        if (g2 == null || ships == null || ships.isEmpty()) return;
        Faction perspective = (player == null) ? null : player.faction;
        for (Ship ship : ships) {
            if (ship == null || ship == player) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (!ship.hasSuperweapon || !ship.isSuperweaponCharging()) continue;
            if (!FogOfWarSystem.isVisibleToPerspective(fog, perspective, ship)) continue;
            double aim = ship.getSuperweaponAimAngle();
            double len = npcSuperweaponCueLength(ship);
            double sx = ship.x + Math.cos(aim) * (ship.radius + 10.0);
            double sy = ship.y + Math.sin(aim) * (ship.radius + 10.0);
            double ex = sx + Math.cos(aim) * len;
            double ey = sy + Math.sin(aim) * len;
            if (!isWorldSegmentVisible(sx, sy, ex, ey, 24.0, minX, minY, maxX, maxY)) continue;
            drawNpcSuperweaponAimCue(g2, ship);
        }
    }

    private static void drawNpcSuperweaponAimCue(Graphics2D g2, Ship ship) {
        if (g2 == null || ship == null) return;
        double aim = ship.getSuperweaponAimAngle();
        double len = npcSuperweaponCueLength(ship);
        double sx = ship.x + Math.cos(aim) * (ship.radius + 10.0);
        double sy = ship.y + Math.sin(aim) * (ship.radius + 10.0);
        double ex = sx + Math.cos(aim) * len;
        double ey = sy + Math.sin(aim) * len;

        float chargeFrac = (float) Math.max(0.0, Math.min(1.0, ship.getSuperweaponChargeProgress()));
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 8.6 + ship.id * 0.17);

        Color base = npcSuperweaponCueColor(ship.faction);
        Color hot = mixColor(base, Color.WHITE, 0.58);

        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(6.2f + chargeFrac * 3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, (int) Math.round(88 + 78 * Math.max(chargeFrac, pulse))));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, 196));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int r = (int) Math.round(12 + 10 * Math.max(chargeFrac, pulse));
        g2.setColor(withAlpha(base, 148));
        g2.drawOval((int) Math.round(ex) - r, (int) Math.round(ey) - r, r * 2, r * 2);

        g2.setStroke(oldStroke);
    }

    private static double npcSuperweaponCueLength(Ship ship) {
        if (ship == null) return 2200.0;
        if (ship.superweaponPattern == Ship.SuperweaponPattern.DIRECT_BEAM
                || ship.superweaponPattern == Ship.SuperweaponPattern.LANCE_CONE) {
            double beamScale = (ship.superweaponPattern == Ship.SuperweaponPattern.LANCE_CONE) ? 0.74 : 0.96;
            return MathUtil.clamp(ship.superweaponSpeed * beamScale, 720.0, 1760.0);
        }
        return 2200.0;
    }

    private static Color npcSuperweaponCueColor(Faction faction) {
        if (faction == null) return new Color(255, 120, 120);
        return switch (faction) {
            case ALLY, PLAYER -> new Color(130, 220, 255);
            case ENEMY -> new Color(255, 96, 96);
            case TEAM_C -> new Color(154, 255, 138);
            case TEAM_D -> new Color(255, 198, 126);
        };
    }

    private static void drawMissile(Graphics2D g2, Missile m) {
        BufferedImage skin = switch ((m == null || m.strikeVisual == null) ? Missile.StrikeVisual.DEFAULT : m.strikeVisual) {
            case TORPEDO -> ProjectileSkinLibrary.getTorpedoStrikeSkin();
            case ATOMIC -> ProjectileSkinLibrary.getAtomicStrikeSkin();
            default -> ProjectileSkinLibrary.getMissileSkin();
        };
        if (skin != null) {
            drawMissileSkin(g2, m, skin);
        } else {
            drawMissileFallback(g2, m);
        }

        double nx = Math.cos(m.angle);
        double ny = Math.sin(m.angle);
        double tailOffset = missileBodyLength(m) * 0.34;
        double trailLen = missileTrailLength(m);

        int x1 = (int) Math.round(m.x - nx * tailOffset);
        int y1 = (int) Math.round(m.y - ny * tailOffset);
        int x2 = (int) Math.round(m.x - nx * (tailOffset + trailLen));
        int y2 = (int) Math.round(m.y - ny * (tailOffset + trailLen));

        Color trail = missileExhaustColor(m);
        if (m.faction == Faction.TEAM_C) {
            // Green missiles should read more like photon torpedoes: brighter glow head + thicker, softer trail.
            Stroke old = g2.getStroke();
            try {
                int glowR = Math.max(6, (int) Math.round(m.radius * 1.85));
                int gx = (int) Math.round(m.x);
                int gy = (int) Math.round(m.y);
                g2.setColor(new Color(trail.getRed(), trail.getGreen(), trail.getBlue(), 72));
                g2.fillOval(gx - glowR, gy - glowR, glowR * 2, glowR * 2);

                g2.setStroke(new BasicStroke((float) Math.max(3.0, missileTrailWidth(m) * 1.15), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(trail.getRed(), trail.getGreen(), trail.getBlue(), 88));
                g2.drawLine(x1, y1, x2, y2);

                g2.setStroke(new BasicStroke((float) Math.max(1.6, missileTrailWidth(m) * 0.68), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color hot = mixColor(trail, Color.WHITE, 0.55);
                g2.setColor(new Color(hot.getRed(), hot.getGreen(), hot.getBlue(), 150));
                g2.drawLine(x1, y1, x2, y2);
            } finally {
                g2.setStroke(old);
            }
        } else {
            Stroke old = g2.getStroke();
            try {
                g2.setStroke(new BasicStroke((float) missileTrailWidth(m), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(trail.getRed(), trail.getGreen(), trail.getBlue(), 120));
                g2.drawLine(x1, y1, x2, y2);
                g2.setStroke(new BasicStroke((float) Math.max(1.1, missileTrailWidth(m) * 0.46), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color hot = mixColor(trail, Color.WHITE, 0.45);
                g2.setColor(new Color(hot.getRed(), hot.getGreen(), hot.getBlue(), 150));
                g2.drawLine(x1, y1, x2, y2);
            } finally {
                g2.setStroke(old);
            }
        }
    }

    private static void drawMissileSkin(Graphics2D g2, Missile m, BufferedImage skin) {
        double len = missileBodyLength(m);
        double width = missileBodyWidth(m);
        int drawW = (int) Math.round(len);
        int drawH = (int) Math.round(width);

        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(m.x, m.y);
        gx.rotate(m.angle);
        gx.drawImage(skin, -drawW / 2, -drawH / 2, drawW, drawH, null);

        Color stripe = missileStripeColor(m);
        int bandW = Math.max(2, (int) Math.round(drawW * 0.12));
        int bandH = Math.max(3, (int) Math.round(drawH * 0.64));
        int bandX = (int) Math.round(-drawW * 0.10);
        gx.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 170));
        gx.fillRoundRect(bandX, -bandH / 2, bandW, bandH, bandW, bandW);

        int flare = Math.max(2, (int) Math.round(drawH * 0.34));
        int flareX = (int) Math.round(drawW * 0.30);
        gx.setColor(new Color(255, 250, 220, 170));
        gx.fillOval(flareX, -flare / 2, flare, flare);
        
        // Phase 5.2: Make green missiles read more like green photon torpedoes
        if (m.faction == Faction.TEAM_C) {
            int glowRadius = Math.max(8, (int) Math.round(Math.max(len, width) * 1.5));
            gx.setColor(new Color(146, 255, 118, 80)); // Green glow
            gx.fillOval(-glowRadius / 2, -glowRadius / 2, glowRadius, glowRadius);
        }
        
        gx.dispose();
    }

    private static void drawMissileFallback(Graphics2D g2, Missile m) {
        double len = missileBodyLength(m);
        double width = missileBodyWidth(m);
        int hw = (int) Math.round(width * 0.5);
        int hl = (int) Math.round(len * 0.5);
        int nose = (int) Math.round(len * 0.22);
        int tail = (int) Math.round(len * 0.20);

        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(m.x, m.y);
        gx.rotate(m.angle);

        Polygon body = new Polygon();
        body.addPoint(-hl + tail, -hw);
        body.addPoint(hl - nose, -hw);
        body.addPoint(hl, 0);
        body.addPoint(hl - nose, hw);
        body.addPoint(-hl + tail, hw);
        body.addPoint(-hl, hw / 2);
        body.addPoint(-hl, -hw / 2);
        gx.setColor(new Color(176, 192, 208, 230));
        gx.fillPolygon(body);
        gx.setColor(new Color(255, 255, 255, 70));
        gx.drawPolygon(body);

        Color stripe = missileStripeColor(m);
        int bandW = Math.max(2, (int) Math.round(len * 0.12));
        int bandH = Math.max(3, (int) Math.round(width * 0.64));
        int bandX = (int) Math.round(-len * 0.08);
        gx.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 170));
        gx.fillRoundRect(bandX, -bandH / 2, bandW, bandH, bandW, bandW);

        if (m.role == Turret.MissileRole.INTERCEPT) {
            gx.setColor(new Color(255, 245, 225, 180));
            int finLen = Math.max(4, (int) Math.round(len * 0.16));
            int finOff = Math.max(2, (int) Math.round(width * 0.42));
            gx.drawLine(-hl + tail, -finOff, -hl - finLen / 2, -hw - 1);
            gx.drawLine(-hl + tail, finOff, -hl - finLen / 2, hw + 1);
        }

        gx.dispose();
    }

    private static double missileBodyLength(Missile missile) {
        if (missile == null) return 18.0;
        double base = switch (missile.role) {
            case ANTI_HEAVY -> Math.max(20.0, missile.radius * 4.6);
            case ANTI_LIGHT -> Math.max(14.0, missile.radius * 3.1);
            case ANTI_MEDIUM -> Math.max(13.0, missile.radius * 3.0);
            case INTERCEPT -> Math.max(11.0, missile.radius * 2.5);
        };
        return base * Math.max(0.1, missile.visualScale);
    }

    private static double missileBodyWidth(Missile missile) {
        if (missile == null) return 8.0;
        double base = switch (missile.role) {
            case ANTI_HEAVY -> Math.max(7.0, missile.radius * 1.7);
            case ANTI_LIGHT -> Math.max(4.8, missile.radius * 1.2);
            case ANTI_MEDIUM -> Math.max(4.8, missile.radius * 1.35);
            case INTERCEPT -> Math.max(3.2, missile.radius * 0.95);
        };
        return base * Math.max(0.1, missile.visualScale);
    }

    private static double missileTrailLength(Missile missile) {
        if (missile == null) return 16.0;
        double base = switch (missile.role) {
            case ANTI_HEAVY -> Math.max(10.0, missile.radius * 2.8);
            case ANTI_LIGHT -> Math.max(18.0, missile.radius * 4.7);
            case ANTI_MEDIUM -> Math.max(10.0, missile.radius * 3.5);
            case INTERCEPT -> Math.max(14.0, missile.radius * 5.2);
        };
        return base * Math.max(0.1, missile.visualScale);
    }

    private static double missileTrailWidth(Missile missile) {
        if (missile == null) return 2.0;
        double base = switch (missile.role) {
            case ANTI_HEAVY -> Math.max(1.8, missile.radius * 0.28);
            case ANTI_LIGHT -> Math.max(1.5, missile.radius * 0.24);
            case ANTI_MEDIUM -> Math.max(1.6, missile.radius * 0.25);
            case INTERCEPT -> Math.max(1.3, missile.radius * 0.20);
        };
        return base * Math.max(0.1, missile.visualScale);
    }

    private static Color missileStripeColor(Missile missile) {
        Faction faction = (missile == null) ? null : missile.faction;
        Color base = (faction == null) ? new Color(110, 220, 255) : switch (faction) {
            case PLAYER, ALLY -> new Color(110, 220, 255);
            case ENEMY -> new Color(255, 122, 94);
            case TEAM_C -> new Color(146, 255, 118);
            case TEAM_D -> new Color(255, 186, 92);
        };
        if (missile == null) return base;
        return switch (missile.role) {
            case ANTI_HEAVY -> mixColor(base, new Color(255, 240, 180), 0.28);
            case ANTI_LIGHT -> mixColor(base, Color.WHITE, 0.12);
            case ANTI_MEDIUM -> base;
            case INTERCEPT -> mixColor(base, new Color(255, 242, 196), 0.40);
        };
    }

    private static Color missileExhaustColor(Missile missile) {
        Faction faction = (missile == null) ? null : missile.faction;
        Color base = (faction == null) ? new Color(255, 186, 120) : switch (faction) {
            case PLAYER, ALLY -> new Color(130, 226, 255);
            case ENEMY -> new Color(255, 170, 112);
            case TEAM_C -> new Color(164, 255, 140);
            case TEAM_D -> new Color(255, 210, 128);
        };
        if (missile == null) return base;
        return switch (missile.role) {
            case ANTI_HEAVY -> mixColor(base, new Color(255, 192, 110), 0.30);
            case ANTI_LIGHT -> mixColor(base, Color.WHITE, 0.18);
            case ANTI_MEDIUM -> base;
            case INTERCEPT -> mixColor(base, new Color(255, 248, 228), 0.52);
        };
    }

    private static Color projectileCoreColor(Faction faction) {
        if (faction == null) return new Color(255, 232, 162);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(180, 232, 255);
            case ENEMY -> new Color(255, 188, 142);
            case TEAM_C -> new Color(190, 255, 172);
            case TEAM_D -> new Color(255, 220, 146);
        };
    }

    private static Color projectileTrailColor(Faction faction) {
        if (faction == null) return new Color(255, 202, 130);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(132, 214, 255);
            case ENEMY -> new Color(255, 150, 110);
            case TEAM_C -> new Color(136, 240, 112);
            case TEAM_D -> new Color(255, 194, 116);
        };
    }

    private static Color beamColorForFaction(Faction faction) {
        if (faction == null) return new Color(125, 226, 255);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(110, 225, 255);
            case ENEMY -> new Color(255, 122, 96);
            case TEAM_C -> new Color(154, 255, 138);
            case TEAM_D -> new Color(255, 206, 118);
        };
    }

    private static void drawOrientedProjectileSkin(Graphics2D g2, BufferedImage skin, double x, double y, double angle,
                                                   double length, double width, float alpha) {
        if (g2 == null || skin == null) return;
        int drawW = (int) Math.round(Math.max(2.0, length));
        int drawH = (int) Math.round(Math.max(2.0, width));
        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(x, y);
        gx.rotate(angle);
        if (alpha < 0.999f) {
            float a = Math.max(0.0f, Math.min(1.0f, alpha));
            gx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }
        gx.drawImage(skin, -drawW / 2, -drawH / 2, drawW, drawH, null);
        gx.dispose();
    }

    public static void drawHUD(Graphics2D g2, Player player, int credits, int hangarTier, boolean dockedAtBase, boolean shopOpen, boolean autoLock, Ship lockedTarget,
                               int playerWingActive, int playerWingCap, int lockedWingActive, int lockedWingCap,
                               boolean resourceRush, int allyOre, int enemyOre, int goal, String gameOverText,
                               String objectiveTitle, String objectiveDetail,
                               String eventBanner, double eventBannerT, double orePriceMul, double orePriceT, double miningMul, double miningT,
                               double camX, double camY, int viewW, int viewH, double zoom, String stationStatus,
                               GameContext ctx, GameContext.HudDetail hudDetail, String contextHint, String overlayStatus) {
        XrayStackLayout xrayLayout = computeXrayStackLayout(player, lockedTarget, shopOpen, viewW, viewH);
        GameContext.HudDetail detail = (hudDetail == null) ? GameContext.HudDetail.COMPACT : hudDetail;

        Rectangle coreMenu = getCoreMenuBarRect(viewW, viewH);
        int leftX = 14;
        int leftW = (xrayLayout != null)
                ? Math.max(250, Math.min(340, xrayLayout.playerX - leftX - 26))
                : Math.max(270, Math.min(340, viewW / 4));
        leftW = Math.max(250, Math.min(leftW, viewW - 28));
        if (ctx != null && ctx.ui != null) {
            ctx.ui.clearObjectiveHover();
        }

        int objectiveH = computeObjectiveCardHeight(objectiveTitle, objectiveDetail, leftW, detail);
        int commandH = computeCommandOverviewCardHeight(player, hangarTier, dockedAtBase, resourceRush,
                allyOre, enemyOre, goal, orePriceMul, orePriceT, miningMul, miningT,
                gameOverText, leftW, detail, ctx);
        int actionH = 0;
        int shipH = computeShipSystemsCardHeight(player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, leftW, detail, ctx);

        int totalH = commandH + 10 + shipH + (actionH > 0 ? actionH + 10 : 0) + (objectiveH > 0 ? objectiveH + 10 : 0);
        int cardY = Math.max(16, coreMenu.y - 12 - totalH);
        if (objectiveH > 0) {
            if (ctx != null && ctx.ui != null) {
                ctx.ui.setObjectiveHover(
                        new Rectangle(leftX, cardY, leftW, objectiveH),
                        "OBJECTIVE",
                        buildObjectiveHoverBody(ctx, objectiveTitle, objectiveDetail));
            }
            cardY += drawObjectiveCard(g2, objectiveTitle, objectiveDetail, leftX, cardY, leftW, detail);
            cardY += 10;
        }
        cardY += drawCommandOverviewCard(g2, player, credits, hangarTier, dockedAtBase,
                resourceRush, allyOre, enemyOre, goal, orePriceMul, orePriceT, miningMul, miningT, gameOverText,
                leftX, cardY, leftW, detail, ctx);
        cardY += 10;
        drawShipSystemsCard(g2, player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, leftX, cardY, leftW, detail, ctx);
        drawCombatHudPanels(g2, ctx, player, viewW, viewH, detail);

        if (!resourceRush && gameOverText != null && !gameOverText.isBlank()) {
            String msg = gameOverText;
            g2.setFont(new Font("Consolas", Font.BOLD, 22));
            g2.setColor(new Color(255, 255, 255, 220));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (g2.getClipBounds().width - fm.stringWidth(msg)) / 2;
            g2.drawString(msg, Math.max(10, tx), 52);
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(255, 255, 255, 220));
        }

        if (lockedTarget != null && lockedTarget.alive) {
            drawOffscreenTargetIndicator(g2, lockedTarget, camX, camY, viewW, viewH, zoom);
        }
        // Top-center event banner (moved to top-right to avoid blocking centered menus)
        if (eventBanner != null && !eventBanner.isBlank() && eventBannerT > 0) {
            int bw = 720;
            int bh = 34;
            int bx = (g2.getClipBounds().width - bw) / 2;
            int by = 60;  // Moved down from y=10 to avoid blocking menus

            int a = (int) Math.round(60 + 140 * Math.max(0.0, Math.min(1.0, eventBannerT / 3.0)));
            if (!paintThemedHudFrame(g2, bx, by, bw, bh,
                    new Color(255, 120, 110, MathUtil.clamp(a, 0, 220)), ThemeArt.HUD_ALERT_PANEL, 14)) {
                g2.setColor(new Color(0, 0, 0, MathUtil.clamp(a, 0, 190)));
                g2.fillRoundRect(bx, by, bw, bh, 14, 14);
            }
            g2.setColor(new Color(255, 255, 255, 210));
            g2.setFont(new Font("Consolas", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            int tx = bx + (bw - fm.stringWidth(eventBanner)) / 2;
            int ty = by + 22;
            g2.drawString(eventBanner, tx, ty);

            // restore
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(255, 255, 255, 220));
        }

        drawLockedTargetXrayHud(g2, ctx, player, lockedTarget, shopOpen, viewW, viewH);
        drawBottomCombatVitals(g2, ctx, player, lockedTarget, xrayLayout, viewW, viewH);
        drawCursorWeaponHints(g2, ctx, player, camX, camY, zoom, viewW, viewH);

        // Performance metrics display for Phase 3.2 (largest map profiling)
        if (DevTools.isDebugOverlay() && ctx != null && ctx.perf != null) {
            drawPerformanceMetrics(g2, ctx, viewW, viewH);
        }

        if (shopOpen) {
            drawShopOverlay(g2, ctx, player, credits, hangarTier, ctx.ui);
        }
    }

    private static CombatHudPanelLayout combatHudPanelLayout(int viewW, int viewH, boolean includeCloak) {
        Rectangle coreMenu = getCoreMenuBarRect(viewW, viewH);
        int beamW = 336;
        int beamH = 150;
        int missileW = 336;
        int missileH = 166;
        int ecmW = 336;
        int ecmH = 124;
        int cloakW = 336;
        int cloakH = 124;
        int gap = 14;
        int x = Math.max(14, viewW - beamW - 18);
        int totalH = beamH + gap + missileH + gap + ecmH + (includeCloak ? gap + cloakH : 0);
        int y = Math.max(18, coreMenu.y - totalH - 22);
        Rectangle beamRect = new Rectangle(x, y, beamW, beamH);
        Rectangle missileRect = new Rectangle(x, beamRect.y + beamRect.height + gap, missileW, missileH);
        Rectangle ecmRect = new Rectangle(x, missileRect.y + missileRect.height + gap, ecmW, ecmH);
        Rectangle cloakRect = includeCloak
                ? new Rectangle(x, ecmRect.y + ecmRect.height + gap, cloakW, cloakH)
                : new Rectangle(x, ecmRect.y + ecmRect.height, 0, 0);
        return new CombatHudPanelLayout(beamRect, missileRect, ecmRect, cloakRect);
    }

    public static HudPanelClickTarget hudPanelClickTargetAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null) return null;
        CombatHudPanelLayout layout = combatHudPanelLayout(viewW, viewH, ctx.player.isStealth);
        if (showCombatStrikePanel(ctx)) {
            Rectangle strikeRect = combatStrikePanelRect(viewW, viewH, layout);
            if (strikeRect != null && strikeRect.width > 0 && strikeRect.height > 0 && strikeRect.contains(mouseX, mouseY)) {
                Rectangle t = combatStrikeTorpedoRect(strikeRect);
                Rectangle a = combatStrikeAirWingRect(strikeRect);
                Rectangle n = combatStrikeNuclearRect(strikeRect);
                Rectangle l = combatStrikeLaunchRect(strikeRect);
                if (t.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.STRIKE_SELECT_TORPEDO);
                if (a.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.STRIKE_SELECT_AIRWING);
                if (n.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.STRIKE_SELECT_NUCLEAR);
                if (l.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.STRIKE_LAUNCH);
            }
        }
        Rectangle beamRapid = beamRapidRect(layout.beamRect);
        if (beamRapid.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.BEAM_RAPID);
        Rectangle beamConcentrated = beamConcentratedRect(layout.beamRect);
        if (beamConcentrated.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.BEAM_CONCENTRATED);
        Rectangle beamToggle = beamToggleRect(layout.beamRect);
        if (beamToggle.contains(mouseX, mouseY)) {
            return new HudPanelClickTarget(
                    ctx.player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT
                            ? HudPanelClickTarget.Kind.BEAM_RAPID
                            : HudPanelClickTarget.Kind.BEAM_CONCENTRATED
            );
        }

        Rectangle missileHeavy = missileRowRect(layout.missileRect, 0);
        if (missileHeavy.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.MISSILE_HEAVY);
        Rectangle missileFast = missileRowRect(layout.missileRect, 1);
        if (missileFast.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.MISSILE_FAST);
        Rectangle missileAaa = missileRowRect(layout.missileRect, 2);
        if (missileAaa.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.MISSILE_AAA);

        Rectangle ecmPrimed = ecmPrimedRect(layout.ecmRect);
        if (ecmPrimed.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.ECM_PRIMED);
        Rectangle ecmActive = ecmActiveRect(layout.ecmRect);
        if (ecmActive.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.ECM_ACTIVE);

        if (ctx.player.isStealth) {
            Rectangle cloakCharge = cloakChargeRect(layout.cloakRect);
            if (cloakCharge.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.CLOAK_CHARGE);
            Rectangle cloakActive = cloakActiveRect(layout.cloakRect);
            if (cloakActive.contains(mouseX, mouseY)) return new HudPanelClickTarget(HudPanelClickTarget.Kind.CLOAK_ACTIVE);
        }
        return null;
    }

    private static Rectangle beamRapidRect(Rectangle panel) {
        return new Rectangle(panel.x + 14, panel.y + 26, panel.width / 2 - 20, panel.height - 34);
    }

    private static Rectangle beamConcentratedRect(Rectangle panel) {
        return new Rectangle(panel.x + panel.width / 2 + 4, panel.y + 26, panel.width / 2 - 18, panel.height - 34);
    }

    private static Rectangle beamToggleRect(Rectangle panel) {
        int cx = panel.x + panel.width / 2;
        int cy = panel.y + panel.height / 2 + 10;
        int r = Math.max(28, Math.min(panel.width, panel.height) / 8);
        return new Rectangle(cx - r, cy - r, r * 2, r * 2);
    }

    private static Rectangle missileRowRect(Rectangle panel, int row) {
        int contentY = panel.y + 28;
        int rowGap = 6;
        int rowH = (panel.height - 40 - rowGap * 2) / 3;
        int y = contentY + row * (rowH + rowGap);
        return new Rectangle(panel.x + 12, y, panel.width - 24, rowH);
    }

    private static Rectangle ecmPrimedRect(Rectangle panel) {
        return new Rectangle(panel.x + panel.width / 2, panel.y + 24, panel.width / 2 - 14, panel.height / 2 - 10);
    }

    private static Rectangle ecmActiveRect(Rectangle panel) {
        return new Rectangle(panel.x + panel.width / 2, panel.y + panel.height / 2 + 2, panel.width / 2 - 14, panel.height / 2 - 14);
    }

    private static Rectangle cloakChargeRect(Rectangle panel) {
        return new Rectangle(panel.x + 8, panel.y + 12, panel.width - 16, panel.height / 2 - 4);
    }

    private static Rectangle cloakActiveRect(Rectangle panel) {
        return new Rectangle(panel.x + 8, panel.y + panel.height / 2 - 4, panel.width - 16, panel.height / 2 + 2);
    }

    private static void drawCombatHudPanels(Graphics2D g2, GameContext ctx, Player player, int viewW, int viewH,
                                            GameContext.HudDetail detail) {
        if (g2 == null || ctx == null || player == null) return;
        if (detail == GameContext.HudDetail.MINIMAL) return;
        if (ctx.ui != null && ctx.ui.hasBlockingOverlay()) return;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;

        CombatHudPanelLayout layout = combatHudPanelLayout(viewW, viewH, player.isStealth);
        drawBeamModePanel(g2, player, layout.beamRect);
        drawMissileModePanel(g2, player, layout.missileRect);
        drawEcmModePanel(g2, ctx, layout.ecmRect);
        if (player.isStealth) {
            drawCloakModePanel(g2, player, layout.cloakRect);
        }
        if (showCombatStrikePanel(ctx)) {
            Rectangle strikeRect = combatStrikePanelRect(viewW, viewH, layout);
            drawCombatStrikeSelectionPanel(g2, ctx, strikeRect);
        }
    }

    private static boolean showCombatStrikePanel(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.campaign == null || !ctx.campaign.enabled) return false;
        if (ctx.player.role != ShipRole.MOTHERSHIP) return false;
        return !CampaignSystem.isStrategicOvermapMode(ctx);
    }

    private static void drawBeamModePanel(Graphics2D g2, Player player, Rectangle rect) {
        if (g2 == null || player == null || rect == null) return;
        CombatHudPanelImageKey key = (player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT)
                ? CombatHudPanelImageKey.BEAM_CONCENTRATED
                : CombatHudPanelImageKey.BEAM_RAPID;
        HudPanelVisual visual = panelVisual(key, rect);
        String active = (player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) ? "CONCENTRATED" : "RAPID FIRE";
        if (visual != null) {
            drawCombatModeImagePanel(g2, rect, "BEAM MODE", active, new Color(255, 182, 92, 220), visual);
            return;
        }
        drawFallbackPanel(g2, rect, "BEAM MODE", active,
                "Click left for rapid fire", "Click right for concentrated", new Color(255, 182, 92, 220));
    }

    private static void drawMissileModePanel(Graphics2D g2, Player player, Rectangle rect) {
        if (g2 == null || player == null || rect == null) return;
        Turret.MissileRole role = currentPlayerMissileRole(player);
        CombatHudPanelImageKey key = switch (role) {
            case ANTI_HEAVY -> CombatHudPanelImageKey.MISSILE_HEAVY;
            case INTERCEPT -> CombatHudPanelImageKey.MISSILE_AAA;
            case ANTI_LIGHT, ANTI_MEDIUM -> CombatHudPanelImageKey.MISSILE_FAST;
        };
        HudPanelVisual visual = panelVisual(key, rect);
        String active = switch (role) {
            case ANTI_HEAVY -> "HEAVY";
            case INTERCEPT -> "AAA";
            case ANTI_LIGHT, ANTI_MEDIUM -> "FAST";
        };
        if (visual != null) {
            drawCombatModeImagePanel(g2, rect, "MISSILE MODE", active, new Color(255, 156, 92, 220), visual);
            return;
        }
        drawFallbackPanel(g2, rect, "MISSILE MODE", active,
                "Top: heavy payload", "Mid: fast / Bottom: AAA", new Color(255, 156, 92, 220));
    }

    private static void drawEcmModePanel(Graphics2D g2, GameContext ctx, Rectangle rect) {
        if (g2 == null || ctx == null || rect == null) return;
        boolean activeNow = ctx.player != null && ctx.player.hasActiveEcm();
        CombatHudPanelImageKey key = activeNow
                ? CombatHudPanelImageKey.ECM_ACTIVE
                : CombatHudPanelImageKey.ECM_PRIMED;
        HudPanelVisual visual = panelVisual(key, rect);
        String active = activeNow ? "ACTIVE"
                : ((ctx.player != null && !ctx.player.ecmReady())
                ? String.format("RECHARGING %.0fs", Math.ceil(ctx.player.ecmCooldownRemaining()))
                : "PRIMED");
        if (visual != null) {
            drawCombatModeImagePanel(g2, rect, "ECM MODE", active, new Color(255, 170, 90, 220), visual);
            return;
        }
        drawFallbackPanel(g2, rect, "ECM MODE", active,
                "Top: primed", "Bottom: active", new Color(255, 170, 90, 220));
    }

    private static void drawCloakModePanel(Graphics2D g2, Player player, Rectangle rect) {
        if (g2 == null || player == null || rect == null) return;
        CombatHudPanelImageKey key = player.cloakWantsActive()
                ? CombatHudPanelImageKey.CLOAK_ACTIVE
                : CombatHudPanelImageKey.CLOAK_CHARGE;
        HudPanelVisual visual = panelVisual(key, rect);
        int pct = (int) Math.round(player.cloakEnergyFrac() * 100.0);
        String active = player.cloakWantsActive()
                ? String.format("ACTIVE %d%%", pct)
                : String.format("CHARGE %d%%", pct);
        if (visual != null) {
            drawCombatModeImagePanel(g2, rect, "CLOAK MODE", active,
                    player.cloakWantsActive() ? new Color(122, 255, 116, 220) : new Color(108, 194, 255, 220), visual);
            return;
        }
        drawFallbackPanel(g2, rect, "CLOAK MODE", active,
                "Top: preserve charge", "Bottom: cloak active", player.cloakWantsActive()
                        ? new Color(122, 255, 116, 220)
                        : new Color(108, 194, 255, 220));
    }

    private static void drawCombatModeImagePanel(Graphics2D g2, Rectangle rect, String title, String active, Color accent, HudPanelVisual visual) {
        if (g2 == null || rect == null || visual == null) return;
        drawHudPanelFrame(g2, rect.x, rect.y, rect.width, rect.height, title, accent);
        drawHudPanelImage(g2, visual);
        if (active != null && !active.isBlank()) {
            Font oldFont = g2.getFont();
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int chipW = fm.stringWidth(active) + 14;
            drawHudStatusChip(g2, active, rect.x + rect.width - chipW - 12, rect.y + rect.height - 28, chipW, 18, accent, true);
            g2.setFont(oldFont);
        }
    }

    private static boolean strikeActionEnabled(GameContext ctx, String id) {
        if (ctx == null || id == null) return false;
        for (CampaignSystem.CampaignAction action : CampaignSystem.tacticalMapVisibleActions(ctx)) {
            if (action == null || action.id == null) continue;
            if (action.id.equalsIgnoreCase(id)) return action.enabled;
        }
        return false;
    }

    private static Rectangle combatStrikePanelRect(int viewW, int viewH, CombatHudPanelLayout layout) {
        if (layout == null) return new Rectangle(0, 0, 0, 0);
        int w = layout.beamRect.width;
        int h = Math.max(110, (int) Math.round(layout.beamRect.height * 0.76));
        int x = Math.max(12, layout.beamRect.x - w - 12);
        int y = layout.beamRect.y;
        if (x + w > viewW - 12) x = Math.max(12, viewW - w - 12);
        if (y + h > viewH - 12) y = Math.max(12, viewH - h - 12);
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle combatStrikeTorpedoRect(Rectangle panel) {
        int buttonY = panel.y + (int) Math.round(panel.height * 0.30);
        int buttonH = (int) Math.round(panel.height * 0.30);
        int x = panel.x + (int) Math.round(panel.width * 0.11);
        int w = (int) Math.round(panel.width * 0.23);
        return new Rectangle(x, buttonY, w, buttonH);
    }

    private static Rectangle combatStrikeAirWingRect(Rectangle panel) {
        int buttonY = panel.y + (int) Math.round(panel.height * 0.30);
        int buttonH = (int) Math.round(panel.height * 0.30);
        int x = panel.x + (int) Math.round(panel.width * 0.39);
        int w = (int) Math.round(panel.width * 0.23);
        return new Rectangle(x, buttonY, w, buttonH);
    }

    private static Rectangle combatStrikeNuclearRect(Rectangle panel) {
        int buttonY = panel.y + (int) Math.round(panel.height * 0.30);
        int buttonH = (int) Math.round(panel.height * 0.30);
        int x = panel.x + (int) Math.round(panel.width * 0.67);
        int w = (int) Math.round(panel.width * 0.23);
        return new Rectangle(x, buttonY, w, buttonH);
    }

    private static Rectangle combatStrikeLaunchRect(Rectangle panel) {
        int y = panel.y + (int) Math.round(panel.height * 0.68);
        int h = (int) Math.round(panel.height * 0.20);
        int x = panel.x + (int) Math.round(panel.width * 0.28);
        int w = (int) Math.round(panel.width * 0.44);
        return new Rectangle(x, y, w, h);
    }

    private static void drawCombatStrikeSelectionPanel(Graphics2D g2, GameContext ctx, Rectangle rect) {
        if (g2 == null || ctx == null || rect == null || rect.width <= 0 || rect.height <= 0) return;
        int mode = (ctx.ui == null) ? 0 : MathUtil.clamp(ctx.ui.combatStrikeSelection, 0, 2);
        BufferedImage art = switch (mode) {
            case 1 -> StrikeButtonSkinLibrary.getAirWingButton();
            case 2 -> StrikeButtonSkinLibrary.getNuclearButton();
            default -> StrikeButtonSkinLibrary.getTorpedoButton();
        };
        boolean enabled = switch (mode) {
            case 1 -> strikeActionEnabled(ctx, "TACTICAL_CARRIER_SORTIE");
            case 2 -> strikeActionEnabled(ctx, "TACTICAL_ATOMIC_STRIKE");
            default -> strikeActionEnabled(ctx, "TACTICAL_TORPEDO_STRIKE");
        };
        if (art != null) {
            g2.drawImage(art, rect.x, rect.y, rect.width, rect.height, null);
            if (!enabled) {
                // Keep the panel readable at full brightness; only annotate disabled state.
                int bx = rect.x + 10;
                int by = rect.y + rect.height - 26;
                int bw = 126;
                int bh = 18;
                g2.setColor(new Color(14, 22, 32, 210));
                g2.fillRoundRect(bx, by, bw, bh, 8, 8);
                g2.setColor(new Color(255, 180, 140, 210));
                g2.drawRoundRect(bx, by, bw, bh, 8, 8);
                Font oldFont = g2.getFont();
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                g2.drawString("STRIKE UNAVAILABLE", bx + 8, by + 12);
                g2.setFont(oldFont);
            }
            g2.setColor(withAlpha(new Color(178, 220, 255, 220), enabled ? 220 : 176));
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
            return;
        }
        drawFallbackPanel(g2, rect, "STRIKE CONTROL", enabled ? "READY" : "UNAVAILABLE",
                "Select strike type", "Launch selected strike", new Color(255, 176, 120, 220));
    }

    private static Turret.MissileRole currentPlayerMissileRole(Player player) {
        if (player == null || player.turrets == null) return Turret.MissileRole.ANTI_LIGHT;
        for (Turret turret : player.turrets) {
            if (turret != null && turret.kind == Turret.Kind.MISSILE) {
                return (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
            }
        }
        return Turret.MissileRole.ANTI_MEDIUM;
    }

    private static void drawFallbackPanel(Graphics2D g2, Rectangle rect, String title, String active,
                                          String hintA, String hintB, Color accent) {
        if (g2 == null || rect == null) return;
        drawHudPanelFrame(g2, rect.x, rect.y, rect.width, rect.height, title, accent);
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(new Color(248, 242, 232, 232));
        g2.drawString(active, rect.x + 14, rect.y + 48);
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(218, 228, 240, 196));
        g2.drawString(hintA, rect.x + 14, rect.y + rect.height - 28);
        g2.drawString(hintB, rect.x + 14, rect.y + rect.height - 12);
    }

    private static HudPanelVisual panelVisual(CombatHudPanelImageKey key, Rectangle slot) {
        BufferedImage image = HudPanelSkinLibrary.get(key);
        if (image == null || slot == null) return null;
        int srcW = Math.max(1, image.getWidth());
        int srcH = Math.max(1, image.getHeight());
        double scale = Math.min(slot.width / (double) srcW, slot.height / (double) srcH);
        scale = Math.max(0.05, scale);
        int drawW = Math.max(1, (int) Math.round(srcW * scale));
        int drawH = Math.max(1, (int) Math.round(srcH * scale));
        int drawX = slot.x + (slot.width - drawW) / 2;
        int drawY = slot.y + (slot.height - drawH) / 2;
        return new HudPanelVisual(new Rectangle(drawX, drawY, drawW, drawH), image);
    }

    private static void drawHudPanelImage(Graphics2D g2, HudPanelVisual visual) {
        if (g2 == null || visual == null || visual.image == null || visual.drawRect == null) return;
        Rectangle r = visual.drawRect;
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
        g2.setColor(Color.BLACK);
        g2.fillRoundRect(r.x + 6, r.y + 8, r.width - 2, r.height - 2, 18, 18);
        g2.setComposite(oldComposite);
        g2.drawImage(visual.image, r.x, r.y, r.width, r.height, null);
    }

    private static int drawObjectiveCard(Graphics2D g2, String objectiveTitle, String objectiveDetail,
                                         int x, int y, int w, GameContext.HudDetail detail) {
        if (g2 == null) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font titleFont = new Font("Consolas", Font.BOLD, 13);
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        FontMetrics titleFm = g2.getFontMetrics(titleFont);
        FontMetrics bodyFm = g2.getFontMetrics(bodyFont);
        int contentW = Math.max(220, themedContentWidth(ThemeArt.HUD_STANDARD_PANEL, w, 120));

        List<String> titleLines = buildObjectiveTitleLines(titleFm, objectiveTitle, contentW, detail);
        List<String> detailLines = buildObjectiveDetailLines(bodyFm, objectiveDetail, contentW, detail);
        if (titleLines.isEmpty() && detailLines.isEmpty()) return 0;

        int h = computeObjectiveCardHeight(objectiveTitle, objectiveDetail, w, detail);
        drawHudPanelFrame(g2, x, y, w, h, "OBJECTIVE", new Color(255, 214, 132, 220));

        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, h);
        int rowY = inner.y + 4;
        g2.setFont(titleFont);
        g2.setColor(new Color(255, 232, 170, 232));
        for (String line : titleLines) {
            g2.drawString(line, inner.x, rowY);
            rowY += 16;
        }

        if (!titleLines.isEmpty() && !detailLines.isEmpty()) {
            g2.setColor(new Color(255, 255, 255, 44));
            g2.drawLine(inner.x, rowY + 1, inner.x + inner.width, rowY + 1);
            rowY += 16;
        }

        g2.setFont(bodyFont);
        g2.setColor(new Color(220, 232, 244, 208));
        for (String line : detailLines) {
            g2.drawString(line, inner.x, rowY);
            rowY += 15;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return h;
    }

    private static int computeObjectiveCardHeight(String objectiveTitle, String objectiveDetail, int w,
                                                  GameContext.HudDetail detail) {
        Canvas metricsCanvas = new Canvas();
        Font titleFont = new Font("Consolas", Font.BOLD, 13);
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        FontMetrics titleFm = metricsCanvas.getFontMetrics(titleFont);
        FontMetrics bodyFm = metricsCanvas.getFontMetrics(bodyFont);
        int contentW = Math.max(220, themedContentWidth(ThemeArt.HUD_STANDARD_PANEL, w, 120));
        List<String> titleLines = buildObjectiveTitleLines(titleFm, objectiveTitle, contentW, detail);
        List<String> detailLines = buildObjectiveDetailLines(bodyFm, objectiveDetail, contentW, detail);
        if (titleLines.isEmpty() && detailLines.isEmpty()) return 0;
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(ThemeArt.HUD_STANDARD_PANEL, w, 120);
        int h = metrics.top() + 4 + titleLines.size() * 16;
        if (!titleLines.isEmpty() && !detailLines.isEmpty()) h += 16;
        h += detailLines.size() * 15 + metrics.bottom();
        return Math.max(metrics.top() + metrics.bottom() + 28, h);
    }

    private static String buildObjectiveHoverBody(GameContext ctx, String objectiveTitle, String objectiveDetail) {
        String expanded = CampaignSystem.hudObjectiveExpandedDetail(ctx);
        if (expanded != null && !expanded.isBlank()) {
            return expanded;
        }
        StringBuilder body = new StringBuilder();
        if (objectiveTitle != null && !objectiveTitle.isBlank()) {
            body.append(objectiveTitle.trim());
        }
        if (objectiveDetail != null && !objectiveDetail.isBlank()) {
            if (body.length() > 0) body.append('\n');
            body.append(objectiveDetail.trim());
        }
        return body.toString();
    }

    private static int drawCommandOverviewCard(Graphics2D g2, Player player, int credits, int hangarTier, boolean dockedAtBase,
                                               boolean resourceRush, int allyOre, int enemyOre, int goal,
                                               double orePriceMul, double orePriceT, double miningMul, double miningT,
                                               String gameOverText, int x, int y, int w,
                                               GameContext.HudDetail detail, GameContext ctx) {
        if (g2 == null || player == null) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 13);
        List<String> statusLines = buildCommandStatusLines(player, hangarTier, dockedAtBase, resourceRush,
                allyOre, enemyOre, goal, orePriceMul, orePriceT, miningMul, miningT, gameOverText, detail, ctx);
        int h = computeCommandOverviewCardHeight(player, hangarTier, dockedAtBase, resourceRush,
                allyOre, enemyOre, goal, orePriceMul, orePriceT, miningMul, miningT,
                gameOverText, w, detail, ctx);

        drawHudPanelFrame(g2, x, y, w, h, "COMMAND", factionHudColor(player.faction, 210));

        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, h);
        int titleY = inner.y;
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(new Color(244, 248, 255, 235));
        String shipLabel = (player.role == null) ? "COMMAND SHIP" : player.role.name().replace('_', ' ');
        g2.drawString(shipLabel, inner.x, titleY);

        boolean infiniteCredits = ctx != null && ctx.config != null && ctx.config.mode == GameMode.SHOOTING_RANGE;
        String creditLabel = infiniteCredits ? "CREDITS INF" : ("CREDITS " + credits);
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        FontMetrics creditFm = g2.getFontMetrics();
        g2.setColor(new Color(150, 214, 255, 225));
        g2.drawString(creditLabel, inner.x + inner.width - creditFm.stringWidth(creditLabel), titleY);

        int rowY = inner.y + 24;
        g2.setColor(new Color(255, 255, 255, 58));
        g2.drawLine(inner.x, rowY, inner.x + inner.width, rowY);
        rowY += 18;

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        for (String line : statusLines) {
            g2.setColor(line.startsWith("Status:")
                    ? new Color(255, 196, 148, 226)
                    : new Color(190, 214, 236, 198));
            g2.drawString(line, inner.x, rowY);
            rowY += 15;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return h;
    }

    private static int computeCommandOverviewCardHeight(Player player, int hangarTier, boolean dockedAtBase,
                                                        boolean resourceRush, int allyOre, int enemyOre, int goal,
                                                        double orePriceMul, double orePriceT,
                                                        double miningMul, double miningT, String gameOverText,
                                                        int w, GameContext.HudDetail detail, GameContext ctx) {
        if (player == null) return 0;
        List<String> statusLines = buildCommandStatusLines(player, hangarTier, dockedAtBase, resourceRush,
                allyOre, enemyOre, goal, orePriceMul, orePriceT, miningMul, miningT, gameOverText, detail, ctx);
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(ThemeArt.HUD_STANDARD_PANEL, w, 120);
        return metrics.top() + 24 + 18 + statusLines.size() * 15 + metrics.bottom();
    }

    private static List<String> buildObjectiveTitleLines(FontMetrics titleFm, String objectiveTitle, int contentW,
                                                         GameContext.HudDetail detail) {
        List<String> lines = wrapHudText(titleFm, objectiveTitle, contentW);
        int maxLines = switch ((detail == null) ? GameContext.HudDetail.COMPACT : detail) {
            case MINIMAL -> 1;
            case COMPACT -> 2;
            case FULL -> 2;
        };
        return limitHudLines(lines, maxLines);
    }

    private static List<String> buildObjectiveDetailLines(FontMetrics bodyFm, String objectiveDetail, int contentW,
                                                          GameContext.HudDetail detail) {
        List<String> lines = wrapHudMultilineText(bodyFm, objectiveDetail, contentW);
        int maxLines = switch ((detail == null) ? GameContext.HudDetail.COMPACT : detail) {
            case MINIMAL -> 1;
            case COMPACT -> 4;
            case FULL -> 5;
        };
        return limitHudLines(lines, maxLines);
    }

    private static List<String> buildCommandStatusLines(Player player, int hangarTier, boolean dockedAtBase,
                                                        boolean resourceRush, int allyOre, int enemyOre, int goal,
                                                        double orePriceMul, double orePriceT, double miningMul, double miningT,
                                                        String gameOverText, GameContext.HudDetail detail, GameContext ctx) {
        ArrayList<String> statusLines = new ArrayList<>();
        String modeName = ((ctx == null || ctx.config == null) ? "Unknown" : ctx.config.mode.toString());
        statusLines.add("Mode: " + modeName + "   Tier: " + hangarTier);
        String sectorLine = BattlefieldSectorSystem.currentSectorLine(ctx);
        if (!sectorLine.isBlank()) {
            statusLines.add(sectorLine);
        }
        if (CampaignSystem.isCampaignActive(ctx)) {
            int escortCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT);
            int lineCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE);
            int capitalCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL);
            int titanHullCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN);
            int standardCommand = CampaignSystem.campaignStandardCommandCapacity(ctx);
            int standardUsed = CampaignSystem.campaignStandardCommandUsed(ctx);
            int eliteCommand = CampaignSystem.campaignEliteCommandCapacity(ctx);
            int eliteUsed = CampaignSystem.campaignEliteCommandUsed(ctx);
            String fleetLine = "Fleet: E " + escortCount
                    + "   L " + lineCount
                    + "   C " + capitalCount
                    + "   T " + titanHullCount + "/" + CampaignSystem.persistentFleetCap(ctx, ShopHullCategory.TITAN);
            statusLines.add(fleetLine);
            String commandLine = "Command: Grid " + titanHullCount + "/" + TitanFleetSystem.mothershipTitanCap()
                    + "   Std " + standardUsed + "/" + standardCommand
                    + "   Elite " + eliteUsed + "/" + eliteCommand;
            statusLines.add(commandLine);
            CampaignSystem.CampaignRouteChoice route = CampaignSystem.selectedRouteChoice(ctx);
            if (route != null) {
                int routeIndex = CampaignSystem.selectedRouteChoiceIndex(ctx) + 1;
                statusLines.add("Route: [" + routeIndex + "] " + route.title + " -> Sector " + route.targetSector);
            }
            if (detail == GameContext.HudDetail.FULL) {
                TitanArchetype nextTitan = TitanFleetSystem.nextLockedArchetype(ctx);
                if (nextTitan != null) {
                    statusLines.add("Next Titan: " + nextTitan.displayName() + "   S" + nextTitan.availability().minSector());
                }
            }
        }
        if (player != null && player.cargoMax > 0) {
            String label = CampaignSystem.isCampaignActive(ctx) ? "Ore: " : "Cargo: ";
            String suffix = CampaignSystem.isCampaignActive(ctx) ? "   Fleet stores" : (dockedAtBase ? "   Docked" : "");
            statusLines.add(label + player.cargo + "/" + player.cargoMax + suffix);
        }
        if (resourceRush) {
            statusLines.add("Race: ally " + allyOre + "   enemy " + enemyOre + "   goal " + goal);
        }
        if (detail == GameContext.HudDetail.FULL) {
            if (Math.abs(orePriceMul - 1.0) > 0.01 && orePriceT > 0.0) {
                statusLines.add("Ore price x" + fmt1(orePriceMul) + "   " + (int) Math.ceil(orePriceT) + "s remaining");
            }
            if (Math.abs(miningMul - 1.0) > 0.01 && miningT > 0.0) {
                statusLines.add("Mining x" + fmt1(miningMul) + "   " + (int) Math.ceil(miningT) + "s remaining");
            }
        }
        if (gameOverText != null && !gameOverText.isBlank() && resourceRush) {
            statusLines.add("Status: " + gameOverText);
        }

        int maxLines = switch ((detail == null) ? GameContext.HudDetail.COMPACT : detail) {
            case MINIMAL -> Math.min(1, statusLines.size());
            case COMPACT -> Math.min(2, statusLines.size());
            case FULL -> statusLines.size();
        };
        return limitHudLines(statusLines, maxLines);
    }

    private static int drawActionStripCard(Graphics2D g2, Player player, GameContext.HudDetail detail, int x, int y, int w) {
        if (g2 == null || player == null) return 0;
        List<String> chips = buildActionStripLabels(player, detail);
        if (chips.isEmpty()) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        g2.setFont(chipFont);
        FontMetrics fm = g2.getFontMetrics();

        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, 88);
        int chipX = inner.x;
        int chipY = inner.y;
        int lineHeight = 28;
        int chipH = 18;
        int maxX = inner.x + inner.width;
        int rows = 1;

        int panelH = 60;
        drawHudPanelFrame(g2, x, y, w, panelH, "ACTION STRIP", new Color(132, 196, 255, 210));
        for (String chip : chips) {
            int chipW = fm.stringWidth(chip) + 14;
            if (chipX + chipW > maxX) {
                chipX = inner.x;
                chipY += lineHeight;
                rows++;
            }
            drawHudStatusChip(g2, chip, chipX, chipY - 12, chipW, chipH, new Color(125, 190, 255, 210), false);
            chipX += chipW + 8;
        }
        if (rows > 1) {
            panelH = 60 + (rows - 1) * 28;
            drawHudPanelFrame(g2, x, y, w, panelH, "ACTION STRIP", new Color(132, 196, 255, 210));
            inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, panelH);
            chipX = inner.x;
            chipY = inner.y;
            maxX = inner.x + inner.width;
            for (String chip : chips) {
                int chipW = fm.stringWidth(chip) + 14;
                if (chipX + chipW > maxX) {
                    chipX = inner.x;
                    chipY += lineHeight;
                }
                drawHudStatusChip(g2, chip, chipX, chipY - 12, chipW, chipH, new Color(125, 190, 255, 210), false);
                chipX += chipW + 8;
            }
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return panelH;
    }

    private static int computeActionStripCardHeight(Player player, GameContext.HudDetail detail, int w) {
        if (player == null) return 0;
        List<String> chips = buildActionStripLabels(player, detail);
        if (chips.isEmpty()) return 0;

        Canvas metricsCanvas = new Canvas();
        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        FontMetrics fm = metricsCanvas.getFontMetrics(chipFont);
        int chipX = 12;
        int maxX = Math.max(12, w - 12);
        int rows = 1;
        for (String chip : chips) {
            int chipW = fm.stringWidth(chip) + 14;
            if (chipX + chipW > maxX) {
                chipX = 12;
                rows++;
            }
            chipX += chipW + 8;
        }
        return 60 + (rows - 1) * 28;
    }

    private static int drawShipSystemsCard(Graphics2D g2, Player player, Ship lockedTarget, boolean autoLock,
                                           int playerWingActive, int playerWingCap, String stationStatus,
                                           String overlayStatus, String contextHint,
                                           int x, int y, int w, GameContext.HudDetail detail, GameContext ctx) {
        if (g2 == null || player == null) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        FontMetrics bodyFm = g2.getFontMetrics(bodyFont);
        int contentW = Math.max(220, themedContentWidth(ThemeArt.HUD_STANDARD_PANEL, w, 180));

        List<String> noteLines = buildShipSystemNoteLines(player, lockedTarget, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, detail, bodyFm, contentW, ctx);
        HudChipSet chips = buildShipSystemChips(player, autoLock, detail);

        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        g2.setFont(chipFont);
        FontMetrics chipFm = g2.getFontMetrics();
        int chipRows = computeHudChipRows(chips.texts, chipFm, w);
        boolean showPowerStrip = detail != GameContext.HudDetail.MINIMAL;
        boolean showPowerLegend = detail == GameContext.HudDetail.FULL;
        int powerBlockH = showPowerStrip ? (showPowerLegend ? 62 : 18) : 0;
        int h = computeShipSystemsCardHeight(player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, w, detail, ctx);

        drawHudPanelFrame(g2, x, y, w, h, "SHIP", factionHudColor(player.faction, 210));
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, x, y, w, h);

        int chipY = inner.y;
        int chipX = inner.x;
        int chipMaxX = inner.x + inner.width;
        for (int i = 0; i < chips.texts.size(); i++) {
            int chipW = chipFm.stringWidth(chips.texts.get(i)) + 14;
            if (chipX + chipW > chipMaxX) {
                chipX = inner.x;
                chipY += 24;
            }
            drawHudStatusChip(g2, chips.texts.get(i), chipX, chipY - 12, chipW, 18,
                    chips.colors.get(i), chips.strong.get(i));
            chipX += chipW + 8;
        }

        int textY;
        if (showPowerStrip) {
            int barY = chipY + 30;
            int powerBlockUsed = drawPowerAllocationStrip(g2, player, inner.x, barY, inner.width, 16, showPowerLegend);
            textY = barY + powerBlockUsed + 10;
        } else {
            textY = chipY + 20;
        }

        g2.setFont(bodyFont);
        for (String line : noteLines) {
            if (line == null || line.isBlank()) continue;
            boolean emphasis = line.startsWith("Hint:") || line.startsWith("Counter:") || line.startsWith("OVERLAY:");
            g2.setColor(emphasis ? new Color(255, 226, 154, 224) : new Color(198, 218, 238, 195));
            g2.drawString(line, inner.x, textY);
            textY += 15;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return h;
    }

    private static int computeShipSystemsCardHeight(Player player, Ship lockedTarget, boolean autoLock,
                                                    int playerWingActive, int playerWingCap, String stationStatus,
                                                    String overlayStatus, String contextHint,
                                                    int w, GameContext.HudDetail detail, GameContext ctx) {
        if (player == null) return 0;
        Canvas metricsCanvas = new Canvas();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        FontMetrics bodyFm = metricsCanvas.getFontMetrics(bodyFont);
        FontMetrics chipFm = metricsCanvas.getFontMetrics(chipFont);
        int contentW = Math.max(220, themedContentWidth(ThemeArt.HUD_STANDARD_PANEL, w, 180));
        List<String> noteLines = buildShipSystemNoteLines(player, lockedTarget, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, detail, bodyFm, contentW, ctx);
        HudChipSet chips = buildShipSystemChips(player, autoLock, detail);
        int chipRows = computeHudChipRows(chips.texts, chipFm, w);
        boolean showPowerStrip = detail != GameContext.HudDetail.MINIMAL;
        int powerBlockH = showPowerStrip ? ((detail == GameContext.HudDetail.FULL) ? 62 : 18) : 0;
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(ThemeArt.HUD_STANDARD_PANEL, w, 180);
        return metrics.top() + chipRows * 24 + 18 + powerBlockH + noteLines.size() * 15 + metrics.bottom();
    }

    private static List<String> buildShipSystemNoteLines(Player player, Ship lockedTarget,
                                                         int playerWingActive, int playerWingCap,
                                                         String stationStatus, String overlayStatus, String contextHint,
                                                         GameContext.HudDetail detail, FontMetrics bodyFm, int contentW,
                                                         GameContext ctx) {
        ArrayList<String> noteLines = new ArrayList<>();
        GameContext.HudDetail mode = (detail == null) ? GameContext.HudDetail.COMPACT : detail;

        if (lockedTarget != null && lockedTarget.alive && !lockedTarget.dying && lockedTarget.hp > 0) {
            int dist = (int) Math.round(Math.hypot(lockedTarget.x - player.x, lockedTarget.y - player.y));
            noteLines.add("Lock: " + lockedTarget.name + "   D " + dist);
            String counter = EnemyArchetypeIntel.counterHint(lockedTarget.role);
            if (mode == GameContext.HudDetail.FULL && counter != null && !counter.isBlank()) {
                noteLines.addAll(wrapHudText(bodyFm, "Counter: " + counter, contentW));
            }
        }

        String systemsLine = compactSystemsLine(player);
        if (!systemsLine.isBlank()) {
            noteLines.addAll(wrapHudText(bodyFm, systemsLine, contentW));
        }

        ShipRoomLayout.RoomId focusRoom = player.integrityFocusRoom();
        if (focusRoom != null && mode != GameContext.HudDetail.MINIMAL) {
            noteLines.add("Field: " + xrayRoomDisplayLabel(focusRoom)
                    + "   " + (int) Math.ceil(player.integrityFocusRemaining()) + "s");
        }

        if (playerWingCap > 0 && mode != GameContext.HudDetail.MINIMAL) {
            noteLines.add("Wing " + playerWingActive + "/" + playerWingCap
                    + "   " + player.carrierCommandMode.name()
                    + "   auto " + (player.carrierAutoLaunch ? "ON" : "OFF"));
        }

        if (overlayStatus != null && !overlayStatus.isBlank() && mode == GameContext.HudDetail.FULL) {
            noteLines.addAll(wrapHudText(bodyFm, overlayStatus, contentW));
        }

        if (mode == GameContext.HudDetail.FULL) {
            noteLines.add("Comms: I cycle intent   K hail target   intent " + CommSystem.currentIntentLabel(ctx));
            if (player.hasSuperweapon) {
                String superCharge = "Superweapon recharge " + signedPct(player.superweaponRechargeRateMultiplier())
                        + "   charge bus " + (int) Math.round(player.powerAuxiliaryFrac() * 100.0) + "%";
                noteLines.addAll(wrapHudText(bodyFm, superCharge, contentW));
            }
            if (stationStatus != null && !stationStatus.isBlank()) {
                noteLines.addAll(wrapHudText(bodyFm, stationStatus, contentW));
            }
            if (contextHint != null && !contextHint.isBlank()) {
                noteLines.addAll(wrapHudText(bodyFm, "Hint: " + contextHint, contentW));
            }
        }

        int maxLines = switch (mode) {
            case MINIMAL -> 2;
            case COMPACT -> 4;
            case FULL -> noteLines.size();
        };
        return limitHudLines(noteLines, maxLines);
    }

    private static String compactSystemsLine(Player player) {
        if (player == null) return "";
        if (player.isOverloadActive()) {
            return "Overload " + player.overloadBus().name() + " " + (int) Math.round(player.overloadHeat() * 100.0) + "%";
        }
        if (player.isEmergencyThrustActive()) {
            return "Emergency thrust active   heat " + (int) Math.round(player.emergencyThrustHeat() * 100.0) + "%";
        }
        ArrayList<String> ready = new ArrayList<>();
        if (player.overloadCooldownRemaining() > 0.05) {
            ready.add("Overload cd " + (int) Math.ceil(player.overloadCooldownRemaining()) + "s");
        } else {
            ready.add("Overload ready");
        }
        if (player.emergencyThrustCooldownRemaining() > 0.05) {
            ready.add("Thrust cd " + (int) Math.ceil(player.emergencyThrustCooldownRemaining()) + "s");
        } else {
            ready.add("Thrust ready");
        }
        if (player.hasSuperweapon) {
            ready.add("Super " + superweaponStatusReadout(player));
        }
        return String.join("   ", ready);
    }

    private static HudChipSet buildShipSystemChips(Player player, boolean autoLock, GameContext.HudDetail detail) {
        HudChipSet chips = new HudChipSet();
        GameContext.HudDetail mode = (detail == null) ? GameContext.HudDetail.COMPACT : detail;
        if (mode != GameContext.HudDetail.MINIMAL) {
            chips.add("AUTO-LOCK " + (autoLock ? "ON" : "OFF"), new Color(124, 208, 255, 210), autoLock);
        }
        chips.add("POWER " + player.powerPreset.name(), new Color(114, 226, 166, 208), true);
        chips.add("CREW " + player.crewOrder.name(), new Color(244, 198, 116, 208), true);
        if (player.integrityFocusRoom() != null) {
            chips.add("FIELD " + xrayRoomDisplayLabel(player.integrityFocusRoom()),
                    new Color(150, 220, 255, 214), true);
        }
        if (player.shieldActive && player.shieldMax > 0.0) {
            chips.add("GATE " + shieldGateReadout(player), new Color(154, 186, 255, 208), true);
        }
        if (player.hasSuperweapon && mode == GameContext.HudDetail.FULL) {
            chips.add("SUPER " + superweaponStatusReadout(player), new Color(156, 214, 255, 214),
                    player.getSuperweaponRemaining() <= 1e-6 && !player.isSuperweaponCharging());
        }
        return chips;
    }

    private static int computeHudChipRows(List<String> chipTexts, FontMetrics chipFm, int w) {
        if (chipTexts == null || chipTexts.isEmpty() || chipFm == null) return 0;
        int chipRows = 1;
        int chipCursorX = 12;
        int chipMaxX = Math.max(12, w - 12);
        for (String chip : chipTexts) {
            int chipW = chipFm.stringWidth(chip) + 14;
            if (chipCursorX + chipW > chipMaxX) {
                chipRows++;
                chipCursorX = 12;
            }
            chipCursorX += chipW + 8;
        }
        return chipRows;
    }

    private static final class HudChipSet {
        private final ArrayList<String> texts = new ArrayList<>();
        private final ArrayList<Color> colors = new ArrayList<>();
        private final ArrayList<Boolean> strong = new ArrayList<>();

        private void add(String text, Color color, boolean isStrong) {
            texts.add(text);
            colors.add(color);
            strong.add(isStrong);
        }
    }

    private static List<String> buildActionStripLabels(Player player, GameContext.HudDetail detail) {
        ArrayList<String> out = new ArrayList<>();
        out.add("SPACE/LMB FIRE");
        out.add("SHIFT/RMB SECONDARY");
        out.add("L/MMB LOCK");
        out.add("J TACTICAL");
        out.add("WASD MOVE / ARROWS PAN");
        out.add("TAB SHOP / ESC PAUSE");
        out.add("M MAP / N HUD / H CREW");
        out.add("F1-F5 STATIONS");
        out.add("O POWER / B BASE");
        out.add("SAFE EXIT BUTTON");
        out.add("P PING / G WAYPOINT");
        out.add("CTRL +/-/0 ZOOM");
        out.add("-/BKSP WARP");
        out.add("T AUTO-LOCK");
        out.add("F MINE / E OVERCHARGE / ; THRUST");
        out.add("Y PRESET / U CREW ORDER");
        out.add("X SUPERWEAPON");
        out.add("` XRAY FILTER / ' CLEAR");
        if (player.isCarrier) {
            out.add("C LAUNCH / R RECALL / V MODE / Z AUTO-LAUNCH");
        }
        return out;
    }

    private static void drawPerformanceMetrics(Graphics2D g2, GameContext ctx, int viewW, int viewH) {
        if (g2 == null || ctx == null || ctx.perf == null) return;
        
        int x = 12;
        int y = viewH - 120;
        int lineHeight = 14;
        
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(100, 200, 100, 200));
        
        // Draw performance header
        g2.drawString("PERFORMANCE (DEBUG)", x, y);
        y += lineHeight;
        
        // Draw key metrics for largest map profiling
        g2.setColor(new Color(180, 220, 180, 200));
        g2.drawString(String.format("Ships: %d / Proj: %d / VFX: %d / Expl: %d",
                ctx.perf.drawnShips, ctx.perf.drawnProjectiles, ctx.perf.drawnVfx, ctx.perf.drawnExplosions),
                x, y);
        y += lineHeight;
        
        g2.drawString(String.format("Total VFX: %d / Asteroids: %d / Salvage: %d",
                ctx.perf.totalVfx, ctx.perf.drawnAsteroids, ctx.perf.drawnSalvage),
                x, y);
        y += lineHeight;
        
        if (ctx.perf.renderMs > 0.1) {
            g2.setColor(ctx.perf.renderMs > 20.0 ? new Color(255, 100, 100, 200) : new Color(180, 220, 180, 200));
            g2.drawString(String.format("Render: %.1f ms | Update: %.1f ms | FPS: %.1f",
                    ctx.perf.renderMs, ctx.perf.updateMs, ctx.perf.fps),
                    x, y);
        }
    }

    private static void drawHudPanelFrame(Graphics2D g2, int x, int y, int w, int h, String title, Color accent) {
        drawHudPanelFrame(g2, x, y, w, h, title, accent, ThemeArt.HUD_STANDARD_PANEL);
    }

    private static void drawHudPanelFrame(Graphics2D g2, int x, int y, int w, int h,
                                          String title, Color accent, String themeSlot) {
        if (g2 == null) return;
        Color base = (accent == null) ? new Color(150, 190, 235, 180) : accent;
        if (!paintThemedHudFrame(g2, x, y, w, h, base, themeSlot, 18)) {
            g2.setColor(new Color(7, 14, 24, 188));
            g2.fillRoundRect(x, y, w, h, 18, 18);
            g2.setColor(withAlpha(base, 110));
            g2.drawRoundRect(x, y, w - 1, h - 1, 18, 18);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 16, 16);
        }
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(themeSlot, w, h);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(withAlpha(base, 220));
        g2.drawString(title, x + metrics.left(), y + metrics.titleBaseline());
        g2.setColor(withAlpha(base, 72));
        g2.drawLine(x + metrics.left(), y + metrics.separatorY(), x + w - metrics.right(), y + metrics.separatorY());
    }

    private static boolean paintThemedHudFrame(Graphics2D g2, int x, int y, int w, int h,
                                               Color accent, String slot, int arc) {
        if (g2 == null || w <= 0 || h <= 0) return false;
        BufferedImage image = ThemeArt.get(slot);
        if (image == null) return false;

        Color base = (accent == null) ? new Color(150, 190, 235, 180) : accent;
        Paint oldPaint = g2.getPaint();
        Composite oldComposite = g2.getComposite();

        g2.setPaint(new GradientPaint(x, y, new Color(6, 12, 22, 224), x, y + h, new Color(4, 8, 16, 212)));
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.setPaint(new GradientPaint(
                x + w * 0.14f, y + h * 0.10f, withAlpha(base, 42),
                x + w * 0.48f, y + h * 0.42f, withAlpha(base, 0)));
        g2.fillRoundRect(x + 6, y + 6, Math.max(8, w - 12), Math.max(8, h - 12),
                Math.max(8, arc - 4), Math.max(8, arc - 4));
        g2.setComposite(AlphaComposite.SrcOver);
        g2.drawImage(image, x, y, w, h, null);

        g2.setComposite(oldComposite);
        g2.setPaint(oldPaint);
        return true;
    }

    private static boolean paintThemedCircularHudFrame(Graphics2D g2, int x, int y, int size,
                                                       Color accent, String slot) {
        if (g2 == null || size <= 0) return false;
        BufferedImage image = ThemeArt.get(slot);
        if (image == null) return false;

        Color base = (accent == null) ? new Color(150, 190, 235, 180) : accent;
        Paint oldPaint = g2.getPaint();
        Composite oldComposite = g2.getComposite();

        g2.setPaint(new GradientPaint(x, y, new Color(6, 12, 22, 224), x, y + size, new Color(4, 8, 16, 212)));
        g2.fillOval(x, y, size, size);
        g2.setPaint(new GradientPaint(
                x + size * 0.22f, y + size * 0.18f, withAlpha(base, 40),
                x + size * 0.55f, y + size * 0.62f, withAlpha(base, 0)));
        g2.fillOval(x + 8, y + 8, Math.max(8, size - 16), Math.max(8, size - 16));
        g2.setComposite(AlphaComposite.SrcOver);
        g2.drawImage(image, x, y, size, size, null);

        g2.setComposite(oldComposite);
        g2.setPaint(oldPaint);
        return true;
    }

    private static Rectangle themedContentRect(String slot, int x, int y, int w, int h) {
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(slot, w, h);
        int innerX = x + metrics.left();
        int innerY = y + metrics.top();
        int innerW = Math.max(1, w - metrics.left() - metrics.right());
        int innerH = Math.max(1, h - metrics.top() - metrics.bottom());
        return new Rectangle(innerX, innerY, innerW, innerH);
    }

    private static int themedContentWidth(String slot, int w, int h) {
        ThemeArt.FrameMetrics metrics = ThemeArt.metrics(slot, w, h);
        return Math.max(1, w - metrics.left() - metrics.right());
    }

    private static int drawHudChipAuto(Graphics2D g2, String text, int x, int y, Color accent, boolean strong) {
        if (g2 == null || text == null || text.isBlank()) return x;
        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int chipW = fm.stringWidth(text) + 14;
        drawHudStatusChip(g2, text, x, y - 12, chipW, 18, accent, strong);
        g2.setFont(oldFont);
        return x + chipW + 8;
    }

    private static void drawHudStatusChip(Graphics2D g2, String text, int x, int y, int w, int h, Color accent, boolean strong) {
        if (g2 == null || text == null) return;
        Color base = (accent == null) ? new Color(180, 205, 235, 220) : accent;
        int fillAlpha = strong ? 82 : 54;
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(withAlpha(base, strong ? 210 : 170));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(245, 250, 255, strong ? 228 : 210));
        g2.drawString(text, x + 7, y + 12);
    }

    private static int drawPowerAllocationStrip(Graphics2D g2, Player player, int x, int y, int w, int h, boolean showLegend) {
        if (g2 == null || player == null) return 0;
        double[] fracs = new double[]{
                player.powerEnginesFrac(),
                player.powerShieldsFrac(),
                player.powerWeaponsFrac(),
                player.powerSensorsFrac(),
                player.powerEngineeringFrac()
        };
        String[] labels = new String[]{"P", "SH", "T", "SN", "EN", "SW"};
        Color[] colors = new Color[]{
                new Color(110, 212, 255),
                new Color(138, 168, 255),
                new Color(255, 132, 132),
                new Color(128, 240, 190),
                new Color(255, 206, 118),
                new Color(188, 160, 255)
        };

        int[] values = new int[6];
        int total = 0;
        for (int i = 0; i < fracs.length; i++) {
            values[i] = MathUtil.clamp((int) Math.round(fracs[i] * 100.0), 0, 100);
            total += values[i];
        }
        values[5] = Math.max(0, 100 - total);

        g2.setColor(new Color(255, 255, 255, 48));
        g2.drawRoundRect(x, y, w, h, 10, 10);
        int innerX = x + 1;
        int innerW = Math.max(1, w - 1);
        for (int i = 0; i < values.length; i++) {
            int segW = (int) Math.round(innerW * (values[i] / 100.0));
            if (i == values.length - 1) {
                segW = Math.max(0, x + w - innerX);
            }
            if (segW <= 0) continue;
            g2.setColor(withAlpha(colors[i], 150));
            g2.fillRect(innerX, y + 1, segW, h - 1);
            innerX += segW;
        }

        if (!showLegend) {
            return h;
        }

        int legendY = y + h + 10;
        int legendW = (w - 16) / 3;
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        for (int i = 0; i < labels.length; i++) {
            int col = i % 3;
            int row = i / 3;
            int lx = x + col * (legendW + 8);
            int ly = legendY + row * 18;
            String text = labels[i] + " " + values[i] + "%";
            int chipW = Math.min(legendW, g2.getFontMetrics().stringWidth(text) + 12);
            drawHudStatusChip(g2, text, lx, ly - 10, chipW, 16, colors[i], false);
        }
        return h + 46;
    }

    private static int drawHudControlsCard(Graphics2D g2, Player player, GameContext.HudDetail detail, int x, int y, int viewW) {
        if (g2 == null || player == null) return y;
        GameContext.HudDetail mode = (detail == null) ? GameContext.HudDetail.FULL : detail;
        java.util.List<String> rows = buildHudControlsRows(player, mode);
        if (rows.isEmpty()) return y;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        Font titleFont = new Font("Consolas", Font.BOLD, 13);
        Font rowFont = new Font("Consolas", Font.PLAIN, 13);
        g2.setFont(rowFont);
        FontMetrics rowFm = g2.getFontMetrics();

        int panelW = Math.max(360, Math.min(780, viewW - x - 18));
        int contentW = Math.max(220, panelW - 24);

        java.util.List<String> wrappedRows = new ArrayList<>();
        for (String row : rows) {
            wrappedRows.addAll(wrapHudText(rowFm, row, contentW));
        }

        int rowH = 15;
        g2.setFont(titleFont);
        g2.setColor(new Color(210, 234, 255, 220));
        g2.drawString("HUD [" + mode.name() + "]  N: cycle detail", x, y + 14);

        int rowY = y + 31;
        for (int i = 0; i < wrappedRows.size(); i++) {
            g2.setColor(new Color(206, 224, 244, 190));
            g2.drawString(wrappedRows.get(i), x, rowY);
            rowY += rowH;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return rowY + 6;
    }

    private static java.util.List<String> buildHudControlsRows(Player player, GameContext.HudDetail detail) {
        java.util.List<String> rows = new ArrayList<>();
        if (detail == GameContext.HudDetail.MINIMAL) {
            rows.add("HELP surface stores combat, navigation, and overlay hotkeys so the live HUD can stay focused.");
            rows.add("META: ESC pause/resume");
            return rows;
        }

        if (detail == GameContext.HudDetail.COMPACT) {
            rows.add("COMBAT: SPACE/LMB fire | SHIFT/RMB secondary | L/MMB lock | J tactical");
            rows.add("NAV: WASD move | arrows pan | TAB fleet management | B command upgrades | M map | N HUD");
            rows.add("SYSTEMS: H crew | O power | B base | P ping | G waypoint");
            rows.add("OBJECTIVES: Click strategic-map markers to set objective waypoints directly.");
            rows.add("COMMS: I cycle intent | K hail target | marker panel lists live mission targets.");
            rows.add("SPECIAL: F mine | E overcharge | ; thrust | Y preset | U crew order | T auto-lock");
            rows.add("EXTRAS: X superweapon | ` xray filter | ' xray clear | -/BKSP warp | Ctrl +/-/0 zoom");
            if (player.isCarrier) rows.add("CARRIER: C launch | R recall | V mode | Z auto-launch");
            rows.add("META: ESC pause/resume | Alt+Enter fullscreen");
            return rows;
        }

        rows.add("COMBAT: SPACE/LMB fire | SHIFT/RMB secondary | L/MMB lock | J tactical");
        rows.add("NAV: WASD move | arrows pan | TAB fleet management | B command upgrades | M map | N HUD | H crew");
        rows.add("SYSTEMS: O power | B base | P ping | G waypoint | F mine | E overcharge | ; thrust");
        rows.add("OBJECTIVES: Strategic map markers can be clicked for direct routeing to kill, escort, protect, and capture targets.");
        rows.add("COMMS: I cycle intent | K hail target | live comms intent is now kept here instead of the ship card.");
        rows.add("TARGETING: T auto-lock | Y preset | U crew order | X superweapon");
        rows.add("X-RAY: ` filter | ' clear focus | click room focus/protect");
        rows.add("WARP: - or BACKSPACE | Ctrl +/-/0 zoom");
        if (player.isCarrier) rows.add("CARRIER: C launch | R recall | V mode | Z auto-launch");
        rows.add("META: ESC pause/resume | Alt+Enter fullscreen");
        return rows;
    }

    private static <T> List<T> limitHudLines(List<T> lines, int maxLines) {
        if (lines == null || lines.isEmpty() || maxLines <= 0) return java.util.Collections.emptyList();
        if (lines.size() <= maxLines) return new ArrayList<>(lines);
        return new ArrayList<>(lines.subList(0, maxLines));
    }

    private static java.util.List<String> wrapHudText(FontMetrics fm, String text, int maxWidth) {
        java.util.List<String> out = new ArrayList<>();
        if (fm == null || text == null || text.isBlank() || maxWidth <= 0) return out;
        String[] words = text.trim().split("\\s+");
        String line = "";
        for (String word : words) {
            if (word == null || word.isBlank()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(candidate) > maxWidth) {
                out.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private static java.util.List<String> wrapHudMultilineText(FontMetrics fm, String text, int maxWidth) {
        java.util.List<String> out = new ArrayList<>();
        if (fm == null || text == null || text.isBlank() || maxWidth <= 0) return out;
        String[] paragraphs = text.replace("\r", "").split("\n");
        for (String paragraph : paragraphs) {
            java.util.List<String> wrapped = wrapHudText(fm, paragraph, maxWidth);
            if (wrapped.isEmpty() && !paragraph.isBlank()) {
                out.add(paragraph.trim());
            } else {
                out.addAll(wrapped);
            }
        }
        return out;
    }

    private static void drawBottomCombatVitals(Graphics2D g2, GameContext ctx, Player player, Ship lockedTarget,
                                               XrayStackLayout layout, int viewW, int viewH) {
        if (g2 == null || player == null) return;

        int cardW = 220;
        int cardH = 116;
        int margin = 12;
        int sideGap = 12;

        int playerX;
        int playerY;
        int targetX;
        int targetY;

        if (layout != null) {
            playerX = Math.max(margin, layout.playerX - cardW - sideGap);
            playerY = layout.playerY + Math.max(0, (layout.playerH - cardH) / 2);
            targetX = Math.min(viewW - cardW - margin, layout.targetX + layout.panelW + sideGap);
            targetY = (layout.targetVisible && layout.targetH > 0)
                    ? layout.targetY + Math.max(0, (layout.targetH - cardH) / 2)
                    : playerY;
        } else {
            Rectangle menu = getCoreMenuBarRect(viewW, viewH);
            int cx = viewW / 2;
            playerX = Math.max(margin, cx - cardW - 18);
            targetX = Math.min(viewW - cardW - margin, cx + 18);
            playerY = Math.max(100, menu.y - cardH - 12);
            targetY = playerY;
        }

        drawShipVitalsCard(
                g2, player, "PLAYER VITALS", playerX, playerY, cardW, cardH,
                factionHudColor(player.faction, 220),
                true
        );

        boolean validTarget = lockedTarget != null && lockedTarget.alive && !lockedTarget.dying && lockedTarget.hp > 0
                && (lockedTarget.faction == null || player.faction == null || !lockedTarget.faction.isFriendlyTo(player.faction));
        if (validTarget) {
            String title = "TARGET VITALS";
            if (lockedTarget.name != null && !lockedTarget.name.isBlank()) title = lockedTarget.name;
            drawShipVitalsCard(
                    g2, lockedTarget, title, targetX, targetY, cardW, cardH,
                    factionHudColor(lockedTarget.faction, 220),
                    false
            );
            drawCommResultCard(g2, ctx, lockedTarget, targetX, targetY + cardH + 8, cardW);
        }
    }

    private static void drawCommResultCard(Graphics2D g2, GameContext ctx, Ship lockedTarget, int x, int y, int w) {
        if (g2 == null || ctx == null || ctx.ui == null || lockedTarget == null) return;
        if (ctx.ui.commResultT <= 0.0) return;
        if (ctx.ui.commResultTargetId != lockedTarget.id) return;

        String title = (ctx.ui.commResultTitle == null || ctx.ui.commResultTitle.isBlank())
                ? "COMM RESULT"
                : ctx.ui.commResultTitle.trim();
        String body = (ctx.ui.commResultBody == null) ? "" : ctx.ui.commResultBody.trim();

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics titleFm = g2.getFontMetrics();
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        FontMetrics bodyFm = g2.getFontMetrics();

        int contentW = Math.max(110, w - 18);
        java.util.List<String> bodyLines = limitHudLines(wrapHudText(bodyFm, body, contentW), 2);
        int lineH = Math.max(13, bodyFm.getHeight());
        int h = 28 + Math.max(1, bodyLines.size()) * lineH + 8;

        Color accent = factionHudColor(lockedTarget.faction, 220);
        int alpha = (int) Math.round(150 + 70 * MathUtil.clamp(ctx.ui.commResultT / 4.5, 0.0, 1.0));

        g2.setColor(new Color(0, 0, 0, 168));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(withAlpha(accent, alpha));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(withAlpha(accent, 228));
        g2.drawString(title, x + 9, y + 15);

        int baseline = y + 31;
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(236, 242, 248, 220));
        if (bodyLines.isEmpty()) {
            g2.drawString("Channel clear.", x + 9, baseline);
        } else {
            for (String line : bodyLines) {
                g2.drawString(line, x + 9, baseline);
                baseline += lineH;
            }
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    public static void drawStrategicEncounterOverlay(Graphics2D g2, GameContext ctx, int viewW, int viewH) {
        if (g2 == null || ctx == null || ctx.ui == null || !ctx.ui.strategicEncounterPrompt.active) return;
        UiState.StrategicEncounterPrompt prompt = ctx.ui.strategicEncounterPrompt;

        g2.setColor(new Color(0, 0, 0, 178));
        g2.fillRect(0, 0, viewW, viewH);

        int w = Math.min(620, Math.max(420, viewW - 180));
        int x = (viewW - w) / 2;
        int y = Math.max(70, viewH / 2 - 130);

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        Font titleFont = new Font("Consolas", Font.BOLD, 20);
        Font infoFont = new Font("Consolas", Font.PLAIN, 13);
        Font bodyFont = new Font("Consolas", Font.PLAIN, 15);
        Font footerFont = new Font("Consolas", Font.PLAIN, 12);
        g2.setFont(titleFont);
        FontMetrics titleFm = g2.getFontMetrics();
        java.util.List<String> titleLines = wrapHudText(titleFm, prompt.title, w - 52);
        g2.setFont(bodyFont);
        FontMetrics bodyFm = g2.getFontMetrics();
        java.util.List<String> bodyLines = wrapHudMultilineText(bodyFm, prompt.body, w - 52);

        String frameTitle = switch (prompt.kind) {
            case CAMPAIGN_LOCATION -> "MISSION ENCOUNTER";
            case GALAXY_SEARCH_GROUP -> "HOSTILE INTERCEPT";
            case INSTALLATION_THREAT -> "INSTALLATION THREAT";
            case CAMPAIGN_FORCE -> "HOSTILE FORCE CONTACT";
            case CAMPAIGN_BATTLE -> "BATTLE INTERVENTION";
            case TASK_FORCE -> "STRATEGIC CONTACT";
        };
        String footer = switch (prompt.kind) {
            case CAMPAIGN_LOCATION ->
                    "Auto-resolve stays on the galaxy map. Taking command opens one large tactical sector.";
            case GALAXY_SEARCH_GROUP ->
                    "Auto-resolve keeps the route moving. Taking command breaks the interception in tactical combat.";
            case INSTALLATION_THREAT ->
                    "Auto-resolve keeps the installation open. Taking command clears the hostile contact inside the harbor approach.";
            case CAMPAIGN_FORCE ->
                    "Auto-resolve avoids tactical deployment. Taking command opens a direct fleet-contact battle.";
            case CAMPAIGN_BATTLE ->
                    "I ignore  |  J join battle  |  S strike support  |  O observe";
            case TASK_FORCE ->
                    "Auto-resolve is faster. Taking command opens a full tactical battle for this contact.";
        };
        g2.setFont(footerFont);
        FontMetrics footerFm = g2.getFontMetrics();
        java.util.List<String> footerLines = wrapHudText(footerFm, footer, w - 52);
        int infoLineCount = 0;
        if (prompt.location != null && !prompt.location.isBlank()) infoLineCount++;
        if (prompt.strengthReadout != null && !prompt.strengthReadout.isBlank()) infoLineCount++;
        int h = 154
                + Math.max(1, titleLines.size()) * 24
                + Math.max(0, infoLineCount) * 18
                + Math.max(1, bodyLines.size()) * 18
                + Math.max(1, footerLines.size()) * 14;
        drawHudPanelFrame(g2, x, y, w, h, frameTitle, new Color(255, 206, 122, 230), ThemeArt.HUD_SPECIAL_FRAME);
        Rectangle inner = themedContentRect(ThemeArt.HUD_SPECIAL_FRAME, x, y, w, h);

        g2.setFont(titleFont);
        g2.setColor(new Color(248, 238, 220, 236));
        int titleY = inner.y + 24;
        for (String line : titleLines) {
            g2.drawString(line, inner.x, titleY);
            titleY += 24;
        }

        g2.setFont(infoFont);
        g2.setColor(new Color(255, 214, 142, 220));
        int infoY = titleY + 2;
        if (prompt.location != null && !prompt.location.isBlank()) {
            String label = switch (prompt.kind) {
                case CAMPAIGN_LOCATION -> "Location: ";
                case GALAXY_SEARCH_GROUP -> "Intercept Contact: ";
                case INSTALLATION_THREAT -> "Threat Axis: ";
                case CAMPAIGN_FORCE -> "Force Axis: ";
                case CAMPAIGN_BATTLE -> "Battle Axis: ";
                case TASK_FORCE -> "Contact Axis: ";
            };
            g2.drawString(label + prompt.location, inner.x, infoY);
            infoY += 18;
        }
        if (prompt.strengthReadout != null && !prompt.strengthReadout.isBlank()) {
            g2.drawString(prompt.strengthReadout, inner.x, infoY);
            infoY += 20;
        }

        g2.setFont(bodyFont);
        g2.setColor(new Color(232, 238, 245, 226));
        int bodyY = infoY + 4;
        for (String line : bodyLines) {
            g2.drawString(line, inner.x, bodyY);
            bodyY += 18;
        }

        int chipY = y + h - 58;
        if (prompt.kind == UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE) {
            drawHudStatusChip(g2, "I IGNORE", inner.x, chipY, 82, 22, new Color(132, 196, 255, 224), true);
            drawHudStatusChip(g2, "J JOIN", inner.x + 92, chipY, 76, 22, new Color(255, 206, 122, 224), true);
            drawHudStatusChip(g2, "S SUPPORT", inner.x + 178, chipY, 92, 22, new Color(190, 226, 152, 224), true);
            drawHudStatusChip(g2, "O OBSERVE", inner.x + 280, chipY, 96, 22, new Color(190, 190, 220, 224), true);
        } else {
            drawHudStatusChip(g2, "A AUTO-RESOLVE", inner.x, chipY, 132, 22, new Color(132, 196, 255, 224), true);
            drawHudStatusChip(g2, "C TAKE COMMAND", inner.x + 146, chipY, 142, 22, new Color(255, 206, 122, 224), true);
            if (!CampaignSystem.hasValidStrategicEncounterResponder(ctx)) {
                drawHudStatusChip(g2, "D DISMISS STALE", inner.x + 302, chipY, 132, 22,
                        new Color(255, 146, 122, 224), true);
            }
        }

        g2.setFont(footerFont);
        g2.setColor(new Color(180, 200, 220, 210));
        int footerY = chipY + 36;
        for (String line : footerLines) {
            g2.drawString(line, inner.x, footerY);
            footerY += 14;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static void drawShipVitalsCard(Graphics2D g2, Ship ship, String title,
                                           int x, int y, int w, int h, Color accent, boolean showOverchargeHint) {
        if (g2 == null || ship == null) return;
        Color frame = (accent == null) ? new Color(175, 210, 255, 150) : withAlpha(accent, 178);

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(frame.getRed(), frame.getGreen(), frame.getBlue(), 220));
        g2.drawString(title, x, y + 12);
        g2.setColor(new Color(frame.getRed(), frame.getGreen(), frame.getBlue(), 120));
        g2.drawLine(x, y + 16, x + w, y + 16);

        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        int meterW = w;
        int meterH = 10;
        int meterX = x;
        int hullY = y + 34;
        int shieldY = y + 62;
        int cloakY = y + 90;

        double hullFrac = (ship.hpMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, ship.hp / (double) ship.hpMax));
        drawVitalsMeter(g2, meterX, hullY, meterW, meterH, "HULL " + ship.hp + "/" + ship.hpMax, hullFrac,
                new Color(92, 246, 124, 218));

        double effectiveShieldMax = ship.effectiveShieldCapacityMax();
        if (ship.shieldActive && effectiveShieldMax > 0.0) {
            double shieldFrac = Math.max(0.0, Math.min(1.0, ship.shield / Math.max(1e-9, effectiveShieldMax)));
            int shieldNow = (int) Math.round(Math.max(0.0, ship.shield));
            int shieldMax = (int) Math.round(Math.max(0.0, effectiveShieldMax));
            drawVitalsMeter(g2, meterX, shieldY, meterW, meterH, "SHIELD " + shieldNow + "/" + shieldMax, shieldFrac,
                    shieldFaceColor(ship, Ship.SHIELD_FACE_FORE, 216));
            if (showOverchargeHint) {
                drawHudHintChip(g2, "E shield overcharge", x + w - 2, shieldY - 2, -1);
            }
            if (!ship.isShieldOnline()) {
                g2.setColor(new Color(255, 185, 136, 210));
                g2.drawString("REBOOT " + fmt1(ship.getShieldOfflineRemaining()) + "s", meterX, y + h - 16);
            }
        } else {
            drawVitalsMeter(g2, meterX, shieldY, meterW, meterH, "SHIELD N/A", 0.0, new Color(135, 160, 190, 160));
        }

        int statusLineY = y + h - 16;
        if (ship.isTemporarilyDisabled()) {
            String disabled = "DISABLED " + fmt1(ship.getTemporaryDisableRemaining()) + "s";
            FontMetrics statusFm = g2.getFontMetrics();
            g2.setColor(new Color(255, 134, 118, 220));
            g2.drawString(disabled, x + w - statusFm.stringWidth(disabled), statusLineY);
            statusLineY -= 14;
        }
        if (ship.activeRoomDisruptionCount() > 0) {
            String destabilized = "ROOM DISRUPTION " + ship.activeRoomDisruptionCount();
            FontMetrics statusFm = g2.getFontMetrics();
            g2.setColor(new Color(150, 220, 255, 220));
            g2.drawString(destabilized, x + w - statusFm.stringWidth(destabilized), statusLineY);
            statusLineY -= 14;
            if (ship.crewOrder == Ship.CrewOrder.DAMAGE_CONTROL && ship.disruptionRepairTargetRoom() != null) {
                String repair = "REPAIR " + xrayRoomDisplayLabel(ship.disruptionRepairTargetRoom())
                        + " " + (int) Math.round(ship.disruptionRepairTargetProgress() * 100.0) + "%";
                statusFm = g2.getFontMetrics();
                g2.setColor(new Color(170, 255, 198, 210));
                g2.drawString(repair, x + w - statusFm.stringWidth(repair), statusLineY);
            }
        }

        if (ship.isStealth) {
            int pct = (int) Math.round(ship.cloakEnergyFrac() * 100.0);
            String state = ship.isCloaked()
                    ? "ACTIVE"
                    : (ship.cloakWantsActive() ? "SPOOL" : "CHARGE");
            drawVitalsMeter(
                    g2,
                    meterX,
                    cloakY,
                    meterW,
                    meterH,
                    "CLOAK " + MathUtil.clamp(pct, 0, 100) + "% " + state,
                    ship.cloakEnergyFrac(),
                    new Color(168, 130, 255, 210)
            );
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static void drawVitalsMeter(Graphics2D g2, int x, int y, int w, int h,
                                        String label, double frac, Color fillColor) {
        if (g2 == null) return;
        double f = Math.max(0.0, Math.min(1.0, frac));
        g2.setColor(new Color(255, 255, 255, 75));
        g2.drawRect(x, y, w, h);
        int fillW = (int) Math.round((w - 1) * f);
        g2.setColor(fillColor == null ? new Color(160, 210, 255, 188) : fillColor);
        if (fillW > 0) g2.fillRect(x + 1, y + 1, fillW, h - 1);
        g2.setColor(new Color(225, 240, 255, 212));
        g2.drawString(label, x, y - 2);
    }

    private static void drawCursorWeaponHints(Graphics2D g2, GameContext ctx, Player player,
                                              double camX, double camY, double zoom, int viewW, int viewH) {
        if (g2 == null || ctx == null || player == null) return;
        if (hudBlockingMenuOpen(ctx)) return;
        if (zoom <= 0.0) return;

        double sx = (ctx.cursorWorldX - camX) * zoom;
        double sy = (ctx.cursorWorldY - camY) * zoom;
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) return;

        int mx = MathUtil.clamp((int) Math.round(sx), 18, Math.max(18, viewW - 18));
        int my = MathUtil.clamp((int) Math.round(sy), 18, Math.max(18, viewH - 18));

        int horizontalGap = 16;
        int verticalGap = 24;
        drawHudHintChip(g2, "LMB guns", mx - horizontalGap, my, -1);
        drawHudHintChip(g2, "RMB missiles", mx + horizontalGap, my, +1);
        if (player.role == ShipRole.SUPERSHIP || player.hasSuperweapon) {
            drawHudHintChip(g2, "X superweapon", mx, my - verticalGap, 0);
        }
    }

    private static boolean hudBlockingMenuOpen(GameContext ctx) {
        if (ctx == null) return false;
        return ctx.ui.shopOpen
                || ctx.ui.baseMenuOpen
                || ctx.ui.mapOpen
                || ctx.ui.powerManagementOpen
                || ctx.ui.crewStationsOpen
                || ctx.ui.flightDeckOpen
                || ctx.state == GameState.PAUSED
                || ctx.state == GameState.GAME_OVER;
    }

    // align: -1 right-align to anchor, +1 left-align to anchor, 0 center on anchor.
    private static void drawHudHintChip(Graphics2D g2, String text, int anchorX, int baselineY, int align) {
        if (g2 == null || text == null || text.isBlank()) return;
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int padX = 2;
        int textW = fm.stringWidth(text);

        int textX;
        if (align < 0) {
            textX = anchorX - textW - padX;
        } else if (align > 0) {
            textX = anchorX + padX;
        } else {
            textX = anchorX - textW / 2;
        }

        g2.setColor(new Color(4, 8, 14, 210));
        g2.drawString(text, textX + 1, baselineY + 1);
        g2.setColor(new Color(236, 244, 255, 228));
        g2.drawString(text, textX, baselineY);

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    public static void drawWorldMarkers(Graphics2D g2, List<Ship> ships, Ship lockedTarget,
                                        java.util.Map<Faction, Ship> commandShips,
                                        java.util.Map<Faction, Ship> sharedTargets) {
        drawWorldMarkers(g2, ships, lockedTarget, commandShips, sharedTargets,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                null, null);
    }

    public static void drawWorldMarkers(Graphics2D g2, List<Ship> ships, Ship lockedTarget,
                                        java.util.Map<Faction, Ship> commandShips,
                                        java.util.Map<Faction, Ship> sharedTargets,
                                        double minX, double minY, double maxX, double maxY) {
        drawWorldMarkers(g2, ships, lockedTarget, commandShips, sharedTargets, minX, minY, maxX, maxY, null, null);
    }

    public static void drawWorldMarkers(Graphics2D g2, List<Ship> ships, Ship lockedTarget,
                                        java.util.Map<Faction, Ship> commandShips,
                                        java.util.Map<Faction, Ship> sharedTargets,
                                        double minX, double minY, double maxX, double maxY,
                                        FogOfWarSystem.State fog, Faction perspective) {
        if (lockedTarget != null && FogOfWarSystem.isVisibleToPerspective(fog, perspective, lockedTarget)
                && isWorldCircleVisible(lockedTarget.x, lockedTarget.y, lockedTarget.radius + 22.0, minX, minY, maxX, maxY)) {
            int x = (int) Math.round(lockedTarget.x);
            int y = (int) Math.round(lockedTarget.y);
            int rr = (int) Math.round(lockedTarget.radius + 18);
            g2.setColor(new Color(255, 255, 255, 170));
            g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
            g2.drawLine(x - rr, y, x - rr + 10, y);
            g2.drawLine(x + rr, y, x + rr - 10, y);
            g2.drawLine(x, y - rr, x, y - rr + 10);
            g2.drawLine(x, y + rr, x, y + rr - 10);
        }

        if (commandShips != null && !commandShips.isEmpty()) {
            for (java.util.Map.Entry<Faction, Ship> e : commandShips.entrySet()) {
                Ship cmd = (e == null) ? null : e.getValue();
                if (cmd == null || !cmd.alive || cmd.dying || cmd.hp <= 0) continue;
                if (!isWorldCircleVisible(cmd.x, cmd.y, cmd.radius + 46.0, minX, minY, maxX, maxY)) continue;
                drawCommandShipBeacon(g2, cmd, e.getKey(), (sharedTargets == null) ? null : sharedTargets.get(e.getKey()),
                        fog, perspective);
            }
        }

        if (ships == null) return;
        for (Ship s : ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (!s.isWarpCharging()) continue;
            if (!Double.isFinite(s.warpExitX()) || !Double.isFinite(s.warpExitY())) continue;
            if (fog != null && perspective != null && s.faction != null && !s.faction.isFriendlyTo(perspective)
                    && !fog.isVisibleAtWorld(s.warpExitX(), s.warpExitY())) {
                continue;
            }
            if (!isWorldCircleVisible(s.warpExitX(), s.warpExitY(), Math.max(56.0, s.radius * 1.8), minX, minY, maxX, maxY)) continue;
            drawWarpArrivalTell(g2, s);
        }
        for (Ship s : ships) {
            if (!s.alive) continue;
            if (s.role != ShipRole.BASE) continue;
            if (!isWorldCircleVisible(s.x, s.y, s.radius + 36.0, minX, minY, maxX, maxY)) continue;

            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y - s.radius - 26);
            int w = 110;
            int h = 8;

            double p = Math.max(0, Math.min(1, s.captureProgress));

            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(x - w / 2, y, w, h, 8, 8);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(x - w / 2, y, w, h, 8, 8);

            g2.setColor(new Color(9, 189, 67, 200));
            g2.fillRoundRect(x - w / 2 + 1, y + 1, (int) Math.round((w - 2) * p), h - 2, 7, 7);

            g2.setColor(new Color(255, 90, 90, 110));
            int start = x - w / 2 + 1 + (int) Math.round((w - 2) * p);
            int rem = (x + w / 2 - 1) - start;
            if (rem > 0) g2.fillRoundRect(start, y + 1, rem, h - 2, 7, 7);
        }
    }

    private static void drawWarpArrivalTell(Graphics2D g2, Ship ship) {
        if (g2 == null || ship == null) return;
        double pulse = 0.45 + 0.55 * Math.sin(System.nanoTime() * 1e-9 * 4.4 + ship.id * 0.19);
        double progress = ship.warpChargeProgress();
        int x = (int) Math.round(ship.warpExitX());
        int y = (int) Math.round(ship.warpExitY());
        int baseR = (int) Math.round(Math.max(6.0, Math.min(12.0, ship.radius * 0.18 + 4.0 + progress * 2.0)));
        int outerR = (int) Math.round(baseR + 3 + pulse * 2.0);
        Color base = factionHudColor(ship.faction, 220);

        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 14 + (int) Math.round(progress * 16.0)));
        g2.fillOval(x - outerR, y - outerR, outerR * 2, outerR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(1.0f));
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 72 + (int) Math.round(progress * 36.0)));
        g2.drawOval(x - outerR, y - outerR, outerR * 2, outerR * 2);
        g2.setColor(new Color(235, 245, 255, 96 + (int) Math.round(progress * 38.0)));
        g2.drawOval(x - baseR, y - baseR, baseR * 2, baseR * 2);
        g2.setStroke(old);
    }

    private static void drawCommandShipBeacon(Graphics2D g2, Ship cmd, Faction faction, Ship sharedTarget,
                                             FogOfWarSystem.State fog, Faction perspective) {
        if (g2 == null || cmd == null) return;
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 3.2 + cmd.id * 0.31);
        int x = (int) Math.round(cmd.x);
        int y = (int) Math.round(cmd.y - cmd.radius - 34);
        int r = (int) Math.round(8 + 5 * pulse);

        Color base = factionHudColor(faction, 220);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 70));
        g2.fillOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);

        Polygon p = new Polygon();
        p.addPoint(x, y - r);
        p.addPoint(x + r, y);
        p.addPoint(x, y + r);
        p.addPoint(x - r, y);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 210));
        g2.fillPolygon(p);
        g2.setColor(new Color(255, 255, 255, 190));
        g2.drawPolygon(p);

        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString("CMD", x - 10, y - r - 4);

        if (FogOfWarSystem.isVisibleToPerspective(fog, perspective, sharedTarget)) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{6f, 6f}, 0f));
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 90));
            g2.drawLine((int) Math.round(cmd.x), (int) Math.round(cmd.y), (int) Math.round(sharedTarget.x), (int) Math.round(sharedTarget.y));
            g2.setStroke(old);
        }
    }

    public static void drawCombatCallouts(Graphics2D g2, List<UiState.CombatCallout> callouts,
                                          double minX, double minY, double maxX, double maxY) {
        drawCombatCallouts(g2, callouts, minX, minY, maxX, maxY, null);
    }

    public static void drawCombatCallouts(Graphics2D g2, List<UiState.CombatCallout> callouts,
                                          double minX, double minY, double maxX, double maxY,
                                          FogOfWarSystem.State fog) {
        if (g2 == null || callouts == null || callouts.isEmpty()) return;
        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        for (UiState.CombatCallout callout : callouts) {
            if (callout == null || callout.text == null || callout.text.isBlank()) continue;
            if (!isWorldCircleVisible(callout.x, callout.y, 90.0, minX, minY, maxX, maxY)) continue;
            if (fog != null && !fog.isVisibleAtWorld(callout.x, callout.y)) continue;
            double fade = MathUtil.clamp(callout.alphaFrac(), 0.0, 1.0);
            int textW = fm.stringWidth(callout.text);
            int x = (int) Math.round(callout.x - textW * 0.5);
            int y = (int) Math.round(callout.y);
            int bgX = x - 6;
            int bgY = y - 11;
            int bgW = textW + 12;
            int bgH = 16;
            g2.setColor(new Color(6, 10, 18, (int) Math.round(42 + fade * 78)));
            g2.fillRoundRect(bgX, bgY, bgW, bgH, 10, 10);
            Color text = callout.color;
            g2.setColor(new Color(text.getRed(), text.getGreen(), text.getBlue(), (int) Math.round(84 + fade * 164)));
            g2.drawRoundRect(bgX, bgY, bgW, bgH, 10, 10);
            g2.setColor(new Color(0, 0, 0, (int) Math.round(44 + fade * 88)));
            g2.drawString(callout.text, x + 1, y + 1);
            g2.setColor(new Color(text.getRed(), text.getGreen(), text.getBlue(), (int) Math.round(120 + fade * 120)));
            g2.drawString(callout.text, x, y);
        }
        g2.setFont(oldFont);
    }


    private static void drawShopOverlay(Graphics2D g2, GameContext ctx, Player player, int credits, int hangarTier, UiState ui) {
        Rectangle clip = g2.getClipBounds();
        int viewW = clip.width;
        int viewH = clip.height;
        Rectangle panel = getShopOverlayRect(viewW, viewH);
        boolean campaignShop = CampaignSystem.usesPersistentFleetShop(ctx);

        if (campaignShop && ui != null && ui.fleetRefitMode) {
            drawFleetEditorOverlay(g2, ctx, ui, viewW, viewH);
            return;
        }

        Graphics2D gx = (Graphics2D) g2.create();
        ShopHullCategory category = (ui == null || ui.shopHullCategory == null)
                ? ShopHullCategory.forRole(player.role)
                : ui.shopHullCategory;
        int page = (ui == null) ? 0 : clampShopHullPage(category, ui.shopHullPage);
        int pageCount = shopHullPageCount(category);

        if (!paintThemedHudFrame(gx, panel.x, panel.y, panel.width, panel.height,
                new Color(136, 196, 255, 190), ThemeArt.HUD_SPECIAL_FRAME, 24)) {
            GradientPaint panelFill = new GradientPaint(
                    panel.x, panel.y, new Color(7, 10, 16, 236),
                    panel.x, panel.y + panel.height, new Color(14, 18, 28, 226));
            gx.setPaint(panelFill);
            gx.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 24, 24);
            gx.setColor(new Color(255, 255, 255, 78));
            gx.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 24, 24);
            gx.setColor(new Color(118, 180, 255, 42));
            gx.drawRoundRect(panel.x + 2, panel.y + 2, panel.width - 4, panel.height - 4, 22, 22);
        }
        Rectangle inner = themedContentRect(ThemeArt.HUD_SPECIAL_FRAME, panel.x, panel.y, panel.width, panel.height);

        if (campaignShop) {
            drawFleetOverlayModeTabs(gx, panel, false);
        }

        gx.setFont(new Font("Consolas", Font.BOLD, 18));
        gx.setColor(new Color(245, 248, 255, 230));
        gx.drawString(campaignShop ? "FLEET COMMISSIONING" : "SHOP / LOADOUT", inner.x, inner.y);
        gx.setFont(new Font("Consolas", Font.PLAIN, 12));
        gx.setColor(new Color(192, 210, 232, 180));
        gx.drawString(campaignShop
                        ? "Flagship upgrades on the left. Persistent fleet commissions on the right. TAB/ESC closes."
                        : "Upgrade the active hull on the left and browse ship classes on the right. TAB/ESC closes.",
                inner.x, inner.y + 20);

        drawShopMetricPill(gx, inner.x, inner.y + 36, 170, "CREDITS", "$" + credits, new Color(120, 214, 170));
        drawShopMetricPill(gx, inner.x + 180, inner.y + 36, 150,
                campaignShop ? "ORE" : "HANGAR",
                campaignShop ? String.valueOf(CampaignSystem.currentCampaignOre(ctx)) : ("TIER " + hangarTier),
                new Color(158, 196, 255));
        drawShopMetricPill(gx, inner.x + 340, inner.y + 36, 250,
                campaignShop ? "FLAGSHIP" : "CURRENT HULL",
                shopRoleTitle(player.role),
                new Color(255, 206, 122));
        drawShopMetricPill(gx, inner.x + 600, inner.y + 36, 170,
                campaignShop ? "FLEET MIX" : "SUPERWEAPON",
                campaignShop ? campaignFleetCount(ctx) : superweaponStatusReadout(player),
                new Color(156, 224, 255));

        if (campaignShop) {
            drawFleetCapUpgradeButtons(gx, ctx, panel);
        }

        Rectangle upgradesArea = getShopUpgradeArea(panel);
        Rectangle hullArea = getShopHullArea(panel);
        drawShopSectionLabel(gx, upgradesArea.x, upgradesArea.y, "UPGRADES", "Flagship fit and survivability");
        drawShopSectionLabel(gx, hullArea.x, hullArea.y, "HULL BAY", "Persistent " + category.label().toLowerCase(Locale.US) + " commissions");
        drawShopHullTabs(gx, panel, category, page, pageCount);

        int gunCount = player.gunTurretCount();
        int missileCount = player.missileRackCount();

        for (int i = 0; i < 7; i++) {
            Rectangle card = getShopUpgradeCardRect(panel, i);
            drawShopUpgradeCard(gx, card, player, credits, gunCount, missileCount, i + 1);
        }
        for (int slot = 0; slot < SHOP_HULL_PAGE_SIZE; slot++) {
            ShopHullOffer offer = shopHullOfferAt(category, page, slot);
            if (offer == null) continue;
            Rectangle card = getShopHullCardRect(panel, slot);
            drawShopHullCard(gx, card, offer, credits, hangarTier, player, ctx);
        }

        gx.setFont(new Font("Consolas", Font.PLAIN, 12));
        gx.setColor(new Color(196, 208, 224, 164));
        gx.drawString(campaignShop
                        ? "Tabs: [1-4] hull bands   [Left/Right] page   Hover cards for full requirements."
                        : "Tabs: [1-4] hull bands   [Left/Right] page   Hover cards for full requirements.",
                panel.x + 22, panel.y + panel.height - 18);
        gx.dispose();
    }

    private static void drawFleetCapUpgradeButtons(Graphics2D g2, GameContext ctx, Rectangle panel) {
        if (g2 == null || panel == null || ctx == null) return;
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(204, 220, 238, 190));
        g2.drawString("FLEET DOCTRINE", panel.x + 22, panel.y + 108);
        Rectangle rect = new Rectangle(panel.x + 22, panel.y + 116, 752, 28);
        g2.setColor(new Color(16, 22, 34, 216));
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2.setColor(new Color(118, 214, 255, 92));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(188, 208, 230, 200));
        String line = "Growth gates: shipyard tier, credits/ore, mission unlocks, and titan berth doctrine.";
        g2.drawString(fitShopText(g2.getFontMetrics(), line, rect.width - 20), rect.x + 10, rect.y + 18);
    }

    private static void drawShopHullTabs(Graphics2D g2, Rectangle panel, ShopHullCategory current, int page, int pageCount) {
        for (ShopHullCategory category : ShopHullCategory.values()) {
            Rectangle tab = getShopHullCategoryTabRect(panel, category);
            boolean active = category == current;
            Color accent = switch (category) {
                case ESCORT -> new Color(118, 214, 255);
                case LINE -> new Color(255, 206, 122);
                case CAPITAL -> new Color(255, 150, 126);
                case TITAN -> new Color(176, 210, 255);
            };
            g2.setColor(active
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 132)
                    : new Color(22, 28, 42, 188));
            g2.fillRoundRect(tab.x, tab.y, tab.width, tab.height, 12, 12);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), active ? 170 : 78));
            g2.drawRoundRect(tab.x, tab.y, tab.width, tab.height, 12, 12);
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            String label = category.label();
            int tx = tab.x + (tab.width - fm.stringWidth(label)) / 2;
            int ty = tab.y + (tab.height + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.setColor(active ? new Color(248, 250, 255, 238) : new Color(198, 212, 230, 192));
            g2.drawString(label, tx, ty);
        }

        if (pageCount > 1) {
            Rectangle prev = getShopHullPageButtonRect(panel, false);
            Rectangle next = getShopHullPageButtonRect(panel, true);
            drawShopPageButton(g2, prev, "<", page > 0);
            drawShopPageButton(g2, next, ">", page < pageCount - 1);
        }

        Rectangle area = getShopHullArea(panel);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(204, 220, 238, 190));
        String pageLabel = "PAGE " + (page + 1) + " / " + pageCount;
        FontMetrics fm = g2.getFontMetrics();
        int px = area.x + area.width - 132 - fm.stringWidth(pageLabel);
        g2.drawString(pageLabel, Math.max(area.x + 8, px), area.y + 13);
    }

    private static void drawShopPageButton(Graphics2D g2, Rectangle rect, String label, boolean enabled) {
        g2.setColor(enabled ? new Color(58, 104, 156, 180) : new Color(48, 54, 68, 170));
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
        g2.setColor(enabled ? new Color(220, 234, 255, 208) : new Color(140, 150, 168, 118));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(label)) / 2;
        int ty = rect.y + (rect.height + fm.getAscent() - fm.getDescent()) / 2 - 1;
        g2.drawString(label, tx, ty);
    }

    private static void drawFleetEditorOverlay(Graphics2D g2, GameContext ctx, UiState ui, int viewW, int viewH) {
        if (ctx == null || ctx.ships == null || ui == null) return;

        Rectangle panel = getShopOverlayRect(viewW, viewH);
        Graphics2D gx = (Graphics2D) g2.create();
        try {
            // Panel background
            if (!paintThemedHudFrame(gx, panel.x, panel.y, panel.width, panel.height,
                    new Color(136, 196, 255, 190), ThemeArt.HUD_SPECIAL_FRAME, 24)) {
                GradientPaint panelFill = new GradientPaint(
                        panel.x, panel.y, new Color(7, 10, 16, 236),
                        panel.x, panel.y + panel.height, new Color(14, 18, 28, 226));
                gx.setPaint(panelFill);
                gx.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 24, 24);
                gx.setColor(new Color(255, 255, 255, 78));
                gx.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 24, 24);
                gx.setColor(new Color(118, 180, 255, 42));
                gx.drawRoundRect(panel.x + 2, panel.y + 2, panel.width - 4, panel.height - 4, 22, 22);
            }
            Rectangle inner = themedContentRect(ThemeArt.HUD_SPECIAL_FRAME, panel.x, panel.y, panel.width, panel.height);

            drawFleetOverlayModeTabs(gx, panel, true);

            // Title
            gx.setFont(new Font("Consolas", Font.BOLD, 18));
            gx.setColor(new Color(245, 248, 255, 230));
            gx.drawString("FLEET REFIT", inner.x, inner.y);
            gx.setFont(new Font("Consolas", Font.PLAIN, 12));
            gx.setColor(new Color(192, 210, 232, 180));
            gx.drawString("Select a hull, then swap a slot between GUN/MSL and set missile roles. TAB/ESC closes.",
                    inner.x, inner.y + 20);

            Rectangle shipList = getFleetEditorShipListRect(panel);
            Rectangle editor = getFleetEditorEditorRect(panel, shipList);

            // Ship list (left)
            gx.setColor(new Color(20, 28, 42, 160));
            gx.fillRect(shipList.x, shipList.y, shipList.width, shipList.height);
            gx.setColor(new Color(118, 180, 255, 78));
            gx.drawRect(shipList.x, shipList.y, shipList.width, shipList.height);
            gx.setFont(new Font("Consolas", Font.BOLD, 11));
            gx.setColor(new Color(192, 210, 232, 200));
            gx.drawString("FLEET HULLS", shipList.x + 8, shipList.y + 14);

            List<Ship> ships = fleetEditorShips(ctx);
            int headerH = 22;
            int rowH = 18;
            int startY = shipList.y + headerH + 4;
            int maxRows = Math.max(0, (shipList.height - headerH - 10) / rowH);
            gx.setFont(new Font("Consolas", Font.PLAIN, 10));
            for (int i = 0; i < ships.size() && i < maxRows; i++) {
                Ship s = ships.get(i);
                if (s == null) continue;
                int ry = startY + i * rowH;
                boolean selected = ui.fleetSelectedShipId == s.id;
                if (selected) {
                    gx.setColor(new Color(118, 180, 255, 64));
                    gx.fillRect(shipList.x + 2, ry - rowH + 4, shipList.width - 4, rowH);
                }
                gx.setColor(selected ? new Color(255, 255, 255, 232) : new Color(180, 200, 220, 208));
                String prefix = (ctx.player != null && s == ctx.player) ? "FLAGSHIP" : "HULL";
                String label = prefix + ": " + ((s.name == null || s.name.isBlank()) ? s.role.name() : s.name)
                        + "  [" + s.role.name() + "]";
                if (label.length() > 44) label = label.substring(0, 44);
                gx.drawString(label, shipList.x + 8, ry);
            }

            // Editor panel (right)
            gx.setColor(new Color(20, 28, 42, 160));
            gx.fillRect(editor.x, editor.y, editor.width, editor.height);
            gx.setColor(new Color(255, 206, 122, 78));
            gx.drawRect(editor.x, editor.y, editor.width, editor.height);

            Ship selectedShip = null;
            if (ui.fleetSelectedShipId > 0) {
                for (Ship s : ships) {
                    if (s != null && s.id == ui.fleetSelectedShipId) {
                        selectedShip = s;
                        break;
                    }
                }
            }
            if (selectedShip == null && !ships.isEmpty()) selectedShip = ships.get(0);

            int pad = 10;
            int hx = editor.x + pad;
            int hy = editor.y + 18;
            gx.setFont(new Font("Consolas", Font.BOLD, 11));
            gx.setColor(new Color(255, 206, 122, 210));
            gx.drawString("LOADOUT", hx, editor.y + 14);

            gx.setFont(new Font("Consolas", Font.PLAIN, 10));
            gx.setColor(new Color(200, 220, 240, 220));
            if (selectedShip == null) {
                gx.drawString("No fleet hull selected.", hx, hy);
                return;
            }

            String shipName = (selectedShip.name == null || selectedShip.name.isBlank()) ? selectedShip.role.name() : selectedShip.name;
            gx.drawString("SHIP: " + shipName, hx, hy);
            hy += 14;
            gx.drawString("HULL: " + selectedShip.role.name() + "    HP " + selectedShip.hp + "/" + selectedShip.hpMax,
                    hx, hy);

            int editorHeaderH = 72;
            int controlsH = 92;
            int turretRowH = 18;
            Rectangle turretList = new Rectangle(
                    editor.x + 6,
                    editor.y + editorHeaderH,
                    editor.width - 12,
                    Math.max(80, editor.height - editorHeaderH - controlsH - 8));
            Rectangle controls = new Rectangle(
                    editor.x + 6,
                    turretList.y + turretList.height + 8,
                    editor.width - 12,
                    Math.max(60, editor.y + editor.height - (turretList.y + turretList.height + 8)));

            // Turret list
            gx.setColor(new Color(12, 16, 26, 160));
            gx.fillRect(turretList.x, turretList.y, turretList.width, turretList.height);
            gx.setColor(new Color(255, 206, 122, 68));
            gx.drawRect(turretList.x, turretList.y, turretList.width, turretList.height);
            gx.setFont(new Font("Consolas", Font.BOLD, 10));
            gx.setColor(new Color(255, 206, 122, 200));
            gx.drawString("SLOTS", turretList.x + 8, turretList.y + 14);

            gx.setFont(new Font("Consolas", Font.PLAIN, 9));
            int listY0 = turretList.y + 28;
            int maxTurrets = Math.max(0, (turretList.height - 34) / turretRowH);
            for (int i = 0; i < selectedShip.turrets.size() && i < maxTurrets; i++) {
                Turret t = selectedShip.turrets.get(i);
                if (t == null) continue;
                int rowY = listY0 + i * turretRowH;
                boolean turretSelected = (ui.fleetSelectedTurretIndex == i);
                if (turretSelected) {
                    gx.setColor(new Color(255, 206, 122, 64));
                    gx.fillRect(turretList.x + 2, rowY - 12, turretList.width - 4, turretRowH);
                }
                gx.setColor(turretSelected ? new Color(255, 240, 200, 235) : new Color(180, 200, 220, 205));
                String kind = (t.kind == Turret.Kind.MISSILE) ? "MSL" : "GUN";
                String role = (t.kind == Turret.Kind.MISSILE && t.missileRole != null) ? (" " + t.missileRole.name()) : "";
                String row = String.format(Locale.US, "[%02d] %s%s   DMG %d   CYC %.2fs",
                        i, kind, role, t.damage, t.cooldown);
                gx.drawString(row, turretList.x + 8, rowY);
            }

            // Controls
            gx.setColor(new Color(12, 16, 26, 160));
            gx.fillRect(controls.x, controls.y, controls.width, controls.height);
            gx.setColor(new Color(118, 180, 255, 58));
            gx.drawRect(controls.x, controls.y, controls.width, controls.height);

            int ti = ui.fleetSelectedTurretIndex;
            Turret selectedTurret = (ti >= 0 && ti < selectedShip.turrets.size()) ? selectedShip.turrets.get(ti) : null;
            gx.setFont(new Font("Consolas", Font.BOLD, 10));
            gx.setColor(new Color(192, 210, 232, 200));
            gx.drawString((selectedTurret == null) ? "Select a slot to edit." : ("EDIT SLOT [" + ti + "]"),
                    controls.x + 8, controls.y + 16);

            if (selectedTurret != null) {
                Rectangle swap = new Rectangle(controls.x + 8, controls.y + 24, 136, 24);
                boolean toMissile = selectedTurret.kind == Turret.Kind.GUN;
                drawFleetOverlayModeTab(gx, swap, toMissile ? "SWAP TO MSL" : "SWAP TO GUN", true, new Color(158, 196, 255));

                if (selectedTurret.kind == Turret.Kind.MISSILE) {
                    int bx = controls.x + 158;
                    int by = controls.y + 24;
                    int bw = 110;
                    int bh = 24;
                    int gap = 8;
                    Turret.MissileRole[] roles = new Turret.MissileRole[]{
                            Turret.MissileRole.INTERCEPT,
                            Turret.MissileRole.ANTI_LIGHT,
                            Turret.MissileRole.ANTI_MEDIUM,
                            Turret.MissileRole.ANTI_HEAVY
                    };
                    for (int i = 0; i < roles.length; i++) {
                        Turret.MissileRole role = roles[i];
                        boolean active = (selectedTurret.missileRole == role);
                        Rectangle b = new Rectangle(bx + i * (bw + gap), by, bw, bh);
                        drawFleetOverlayModeTab(gx, b, role.name(), active, new Color(255, 206, 122));
                    }
                    gx.setFont(new Font("Consolas", Font.PLAIN, 9));
                    gx.setColor(new Color(180, 200, 220, 170));
                    gx.drawString("INTERCEPT = fast, low yield   ANTI_HEAVY = high yield torpedo (blue)",
                            controls.x + 8, controls.y + 62);
                }
            }

            gx.setFont(new Font("Consolas", Font.PLAIN, 10));
            gx.setColor(new Color(180, 200, 220, 160));
            gx.drawString("Click hulls and slots. Changes persist via campaign checkpoint serialization.",
                    panel.x + 22, panel.y + panel.height - 10);
        } finally {
            gx.dispose();
        }
    }

    private static void drawShopMetricPill(Graphics2D g2, int x, int y, int w, String label, String value, Color accent) {
        Color base = (accent == null) ? new Color(150, 205, 255) : accent;
        g2.setColor(new Color(16, 22, 34, 188));
        g2.fillRoundRect(x, y, w, 46, 16, 16);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 84));
        g2.drawRoundRect(x, y, w, 46, 16, 16);
        Font labelFont = new Font("Consolas", Font.BOLD, 11);
        g2.setFont(labelFont);
        g2.setColor(new Color(210, 228, 246, 172));
        FontMetrics labelFm = g2.getFontMetrics();
        g2.drawString(fitShopText(labelFm, label, w - 24), x + 12, y + 15);
        Font valueFont = new Font("Consolas", Font.BOLD, 15);
        FontMetrics valueFm = g2.getFontMetrics(valueFont);
        if (valueFm.stringWidth(value) > w - 24) {
            valueFont = new Font("Consolas", Font.BOLD, 13);
            valueFm = g2.getFontMetrics(valueFont);
        }
        if (valueFm.stringWidth(value) > w - 24) {
            valueFont = new Font("Consolas", Font.BOLD, 12);
            valueFm = g2.getFontMetrics(valueFont);
        }
        g2.setFont(valueFont);
        g2.setColor(new Color(245, 249, 255, 228));
        g2.drawString(fitShopText(valueFm, value, w - 24), x + 12, y + 33);
    }

    private static void drawShopSectionLabel(Graphics2D g2, int x, int y, String title, String subtitle) {
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.setColor(new Color(245, 247, 255, 226));
        g2.drawString(title, x, y + 2);
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(186, 206, 226, 162));
        g2.drawString(subtitle, x + 102, y + 2);
    }

    private static void drawShopUpgradeCard(Graphics2D g2, Rectangle card, Player player, int credits,
                                            int gunCount, int missileCount, int upgradeId) {
        String title;
        String line1;
        String line2;
        String buttonLabel;
        boolean enabled = true;
        boolean accentStrong = false;
        Color accent = new Color(122, 194, 255);

        switch (upgradeId) {
            case SHOP_UPGRADE_ENERGY_BOLT -> {
                boolean active = player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.ENERGY_BOLT;
                title = "Beam Bolt Primary (Stagger)";
                line1 = active ? "Current mount: staggered beam bolt" : "Cycle beam-bolt barrels one at a time";
                line2 = active ? "Default blue-team beam doctrine" : "Click to equip instantly";
                buttonLabel = active ? "ACTIVE" : "EQUIP";
                enabled = !active;
                accentStrong = active;
                accent = new Color(118, 214, 255);
            }
            case SHOP_UPGRADE_BEAM_BOLT -> {
                boolean active = player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT;
                title = "Beam Bolt Primary (Volley)";
                line1 = active ? "Current mount: synchronized beam volley" : "Fire every beam-bolt barrel together";
                line2 = active ? "Already installed" : "Install package for $220";
                buttonLabel = active ? "ACTIVE" : (credits >= 220 ? "BUY $220" : "NEED $220");
                enabled = !active && credits >= 220;
                accentStrong = active;
                accent = new Color(144, 230, 255);
            }
            case SHOP_UPGRADE_HULL -> {
                int level = player.getHullPlatingUpgradeLevel();
                int maxLevel = player.maxHullPlatingUpgrades();
                boolean maxed = level >= maxLevel;
                title = "Hull Plating";
                line1 = maxed
                        ? "Hull integrity " + player.hpMax + " at cap"
                        : "Hull integrity " + player.hpMax + " -> " + (player.hpMax + 10);
                line2 = "Plating level " + level + "/" + maxLevel + " on this hull";
                buttonLabel = maxed ? "MAX " + level + "/" + maxLevel : (credits >= 60 ? "BUY $60" : "NEED $60");
                enabled = !maxed && credits >= 60;
                accentStrong = maxed;
                accent = new Color(255, 194, 126);
            }
            case SHOP_UPGRADE_SHIELD -> {
                boolean available = player.shieldActive && player.shieldMax > 0.0;
                int level = player.getShieldArrayUpgradeLevel();
                int maxLevel = player.maxShieldArrayUpgrades();
                boolean maxed = available && level >= maxLevel;
                title = "Shield Array";
                line1 = !available
                        ? "This hull does not mount shield hardware"
                        : maxed
                        ? "Shield " + (int) Math.round(player.shieldMax) + " at cap"
                        : "Shield " + (int) Math.round(player.shieldMax) + " -> " + (int) Math.round(player.shieldMax + 12.0);
                line2 = !available
                        ? "Switch to a shield-capable hull to use this"
                        : maxed
                        ? "Array level " + level + "/" + maxLevel + "  Regen " + fmt1(player.shieldRegen)
                        : "Array level " + level + "/" + maxLevel + "  Regen " + fmt1(player.shieldRegen) + " -> " + fmt1(player.shieldRegen + 0.3);
                buttonLabel = !available
                        ? "NO SHIELD"
                        : (maxed ? "MAX " + level + "/" + maxLevel : (credits >= 70 ? "BUY $70" : "NEED $70"));
                enabled = available && !maxed && credits >= 70;
                accentStrong = maxed;
                accent = new Color(144, 176, 255);
            }
            case SHOP_UPGRADE_GUN -> {
                int level = player.getGunTurretUpgradeLevel();
                int maxLevel = player.maxExtraGunTurrets();
                boolean maxed = level >= maxLevel;
                title = "Add Gun Turret";
                line1 = maxed
                        ? "Gun mounts " + gunCount + " at cap"
                        : "Gun mounts " + gunCount + " -> " + (gunCount + 1);
                line2 = "Extra hardpoints " + level + "/" + maxLevel + " on this hull";
                buttonLabel = maxed ? "MAX " + level + "/" + maxLevel : (credits >= 100 ? "BUY $100" : "NEED $100");
                enabled = !maxed && credits >= 100;
                accentStrong = maxed;
                accent = new Color(255, 180, 124);
            }
            case SHOP_UPGRADE_MISSILE -> {
                int level = player.getMissileRackUpgradeLevel();
                int maxLevel = player.maxExtraMissileRacks();
                boolean maxed = level >= maxLevel;
                title = "Add Missile Rack";
                line1 = maxed
                        ? "Missile racks " + missileCount + " at cap"
                        : "Missile racks " + missileCount + " -> " + (missileCount + 1);
                line2 = "Extra launchers " + level + "/" + maxLevel + " on this hull";
                buttonLabel = maxed ? "MAX " + level + "/" + maxLevel : (credits >= 140 ? "BUY $140" : "NEED $140");
                enabled = !maxed && credits >= 140;
                accentStrong = maxed;
                accent = new Color(255, 148, 126);
            }
            case SHOP_UPGRADE_CIWS -> {
                boolean hasCiws = player.hasCIWS;
                boolean maxed = hasCiws && player.isCIWSUpgradeMaxed();
                title = "Upgrade CIWS";
                if (!hasCiws) {
                    line1 = "Current hull has no CIWS package";
                    line2 = "Pick a CIWS-capable frame first";
                    buttonLabel = "NO CIWS";
                    enabled = false;
                } else if (maxed) {
                    line1 = "Quality " + fmt1(player.ciwsQuality) + "  Range " + (int) Math.round(player.ciwsRange);
                    line2 = "Burst " + player.ciwsPelletsPerBurst + "  CD " + fmt1(player.ciwsCooldown);
                    buttonLabel = "MAX";
                    enabled = false;
                    accentStrong = true;
                } else {
                    double nextQ = Math.min(1.0, player.ciwsQuality + 0.20);
                    double nextRange = Math.min(380.0, player.ciwsRange + 25.0);
                    line1 = "Quality " + fmt1(player.ciwsQuality) + " -> " + fmt1(nextQ);
                    line2 = "Range " + (int) Math.round(player.ciwsRange) + " -> " + (int) Math.round(nextRange);
                    buttonLabel = credits >= 120 ? "BUY $120" : "NEED $120";
                    enabled = credits >= 120;
                }
                accent = new Color(142, 234, 190);
            }
            default -> {
                title = "Unknown Upgrade";
                line1 = "";
                line2 = "";
                buttonLabel = "N/A";
                enabled = false;
            }
        }

        drawShopCardFrame(g2, card, accent, accentStrong);
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(245, 248, 255, 222));
        FontMetrics titleMetrics = g2.getFontMetrics();
        g2.drawString(fitShopText(titleMetrics, title, card.width - 24), card.x + 12, card.y + 18);
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(204, 216, 230, 182));
        FontMetrics bodyMetrics = g2.getFontMetrics();
        g2.drawString(fitShopText(bodyMetrics, line1, card.width - 24), card.x + 12, card.y + 39);
        g2.drawString(fitShopText(bodyMetrics, line2, card.width - 24), card.x + 12, card.y + 54);
        drawShopActionButton(g2, getShopCardButtonRect(card), buttonLabel, enabled, accent, accentStrong);
    }

    private static void drawShopHullCard(Graphics2D g2, Rectangle card, ShopHullOffer offer,
                                         int credits, int hangarTier, Player player, GameContext ctx) {
        boolean campaignShop = CampaignSystem.usesPersistentFleetShop(ctx);
        int displayTier = campaignShop ? CampaignSystem.campaignRequiredTier(offer.role, offer.requiredTier) : offer.requiredTier;
        int oreCost = campaignShop ? CampaignSystem.campaignOreCost(offer.role, offer.cost, displayTier) : 0;
        ShopHullCategory fleetBand = ShopHullCategory.forRole(offer.role);
        int bandCount = campaignShop ? CampaignSystem.livePersistentFleetCount(ctx, fleetBand) : 0;
        int minSector = campaignShop ? CampaignSystem.campaignMinSectorForRole(offer.role) : 1;
        boolean sectorOk = !campaignShop || CampaignSystem.campaignSectorRequirementMet(ctx, offer.role);
        boolean mobileStationOk = !campaignShop
                || !CampaignSystem.campaignNeedsMobileStation(offer.role)
                || CampaignSystem.hasOperationalMobileStation(ctx);
        int standardCost = campaignShop ? CampaignSystem.campaignStandardCommandCost(offer.role) : 0;
        int eliteCost = campaignShop ? CampaignSystem.campaignEliteCommandCost(offer.role) : 0;
        int standardUsed = campaignShop ? CampaignSystem.campaignStandardCommandUsed(ctx) : 0;
        int standardCapacity = campaignShop ? CampaignSystem.campaignStandardCommandCapacity(ctx) : 0;
        int eliteUsed = campaignShop ? CampaignSystem.campaignEliteCommandUsed(ctx) : 0;
        int eliteCapacity = campaignShop ? CampaignSystem.campaignEliteCommandCapacity(ctx) : 0;
        boolean standardCommandOk = !campaignShop || standardCost <= 0 || (standardUsed + standardCost) <= standardCapacity;
        boolean eliteCommandOk = !campaignShop || eliteCost <= 0 || (eliteCapacity > 0 && (eliteUsed + eliteCost) <= eliteCapacity);
        boolean current = player.role == offer.role;
        boolean tierOk = hangarTier >= displayTier;
        boolean affordable = credits >= offer.cost;
        boolean oreAffordable = !campaignShop || CampaignSystem.currentCampaignOre(ctx) >= oreCost;
        boolean commandOk = standardCommandOk && eliteCommandOk;
        boolean enabled = !current && tierOk && sectorOk && mobileStationOk && commandOk && affordable && oreAffordable;
        Color accent = current ? new Color(255, 214, 126) : new Color(126, 186, 255);

        drawShopCardFrame(g2, card, accent, current);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(244, 248, 255, 220));
        FontMetrics titleMetrics = g2.getFontMetrics();
        String title = fitShopText(titleMetrics, shopRoleTitle(offer.role), card.width - 20);
        g2.drawString(title, card.x + 10, card.y + 18);
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(196, 210, 226, 180));
        FontMetrics bodyMetrics = g2.getFontMetrics();
        g2.drawString(fitShopText(bodyMetrics, offer.tagLine, card.width - 20), card.x + 10, card.y + 35);
        g2.drawString(fitShopText(bodyMetrics, offer.detail, card.width - 20), card.x + 10, card.y + 52);

        String line2 = current
                ? (campaignShop ? "Flagship hull currently commanded" : "Currently equipped")
                : (!tierOk ? "Needs shipyard T" + displayTier
                : (campaignShop && !sectorOk)
                ? ("Unlocks in sector " + minSector)
                : (campaignShop && !mobileStationOk)
                ? "Needs Mobile Station Titan"
                : (campaignShop && eliteCost > 0 && eliteCapacity <= 0)
                ? ((offer.role == ShipRole.SUPERSHIP)
                    ? ("Needs sector " + CampaignSystem.campaignMinSectorForRole(offer.role)
                        + " + T" + CampaignSystem.campaignRequiredTier(offer.role, offer.requiredTier))
                    : "Needs elite command titan")
                : (campaignShop && !eliteCommandOk)
                ? ("Elite grid " + eliteUsed + "/" + eliteCapacity + " committed")
                : (campaignShop && !standardCommandOk)
                ? ("Std grid " + standardUsed + "/" + standardCapacity + " committed")
                : (campaignShop ? ("Ready to commission   Live " + fleetBand.label() + " hulls: " + bandCount) : "Ready for swap"));
        String costLine = campaignShop
                ? ("Tier " + displayTier + "   Cost $" + offer.cost + " + " + oreCost + " ore")
                : ("Tier " + offer.requiredTier + "   Cost $" + offer.cost);
        g2.drawString(fitShopText(bodyMetrics, costLine, card.width - 20), card.x + 10, card.y + 69);
        g2.drawString(fitShopText(bodyMetrics, line2, card.width - 20), card.x + 10, card.y + 86);

        String buttonLabel;
        if (current) buttonLabel = "CURRENT";
        else if (!tierOk) buttonLabel = "LOCK T" + displayTier;
        else if (campaignShop && !sectorOk) buttonLabel = "LOCK S" + minSector;
        else if (campaignShop && !mobileStationOk) buttonLabel = "NEED STATION";
        else if (campaignShop && eliteCost > 0 && eliteCapacity <= 0) buttonLabel = "NEED ELITE";
        else if (campaignShop && !eliteCommandOk) buttonLabel = "ELITE FULL";
        else if (campaignShop && !standardCommandOk) buttonLabel = "GRID FULL";
        else if (!affordable) buttonLabel = "NEED $" + offer.cost;
        else if (!oreAffordable) buttonLabel = "NEED " + oreCost + " ORE";
        else if (campaignShop) buttonLabel = (offer.cost <= 0) ? "BUY FREE" : ("BUY $" + offer.cost);
        else if (offer.cost <= 0) buttonLabel = "SWAP FREE";
        else buttonLabel = "SWAP $" + offer.cost;
        drawShopActionButton(g2, getShopCardButtonRect(card), buttonLabel, enabled, accent, current);
    }

    private static String campaignFleetCount(GameContext ctx) {
        return CampaignSystem.persistentFleetCompactSummary(ctx);
    }

    private static void drawShopCardFrame(Graphics2D g2, Rectangle card, Color accent, boolean strong) {
        Color base = (accent == null) ? new Color(150, 205, 255) : accent;
        int fillAlpha = strong ? 60 : 38;
        g2.setColor(new Color(18, 24, 38, 214));
        g2.fillRoundRect(card.x, card.y, card.width, card.height, 18, 18);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha));
        g2.fillRoundRect(card.x, card.y, card.width, card.height, 18, 18);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), strong ? 124 : 80));
        g2.drawRoundRect(card.x, card.y, card.width, card.height, 18, 18);
    }

    private static void drawShopActionButton(Graphics2D g2, Rectangle button, String label,
                                             boolean enabled, Color accent, boolean strong) {
        Color base = (accent == null) ? new Color(150, 205, 255) : accent;
        Color fill = enabled
                ? new Color(base.getRed(), base.getGreen(), base.getBlue(), strong ? 138 : 112)
                : new Color(56, 62, 72, 180);
        Color border = enabled
                ? new Color(245, 248, 255, 198)
                : new Color(160, 170, 184, 118);
        Color text = enabled
                ? new Color(248, 250, 255, 235)
                : new Color(186, 194, 206, 178);
        g2.setColor(fill);
        g2.fillRoundRect(button.x, button.y, button.width, button.height, 12, 12);
        g2.setColor(border);
        g2.drawRoundRect(button.x, button.y, button.width, button.height, 12, 12);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int tx = button.x + (button.width - fm.stringWidth(label)) / 2;
        int ty = button.y + (button.height + fm.getAscent() - fm.getDescent()) / 2 - 1;
        g2.setColor(text);
        g2.drawString(label, tx, ty);
    }

    private static String fitShopText(FontMetrics metrics, String text, int maxWidth) {
        if (metrics == null || text == null || metrics.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        int end = text.length();
        while (end > 1 && metrics.stringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(1, end)) + ellipsis;
    }

    private static String shopRoleTitle(ShipRole role) {
        if (role == null) return "UNKNOWN";
        TitanArchetype titan = TitanArchetype.fromShipRole(role);
        if (titan != null) return titan.displayName().toUpperCase(Locale.US);
        return switch (role) {
            case ARTILLERY_SHIP -> "ARTILLERY SHIP";
            case CRUISER -> "MISSILE CRUISER";
            case MOTHERSHIP -> "MOTHERSHIP";
            default -> role.name().replace('_', ' ');
        };
    }




    public static void drawPowerManagementOverlay(Graphics2D g2, Player player, int focusSlot) {
        if (g2 == null || player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(700, clip.width - 110);
        int h = 462;
        int x = (clip.width - w) / 2;
        int y = Math.max(54, (clip.height - h) / 2);

        drawHudPanelFrame(g2, x, y, w, h, "POWER MANAGEMENT", new Color(255, 214, 150, 225), ThemeArt.HUD_SPECIAL_FRAME);
        Rectangle inner = themedContentRect(ThemeArt.HUD_SPECIAL_FRAME, x, y, w, h);

        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(new Color(255, 240, 180, 230));
        g2.drawString("POWER MANAGEMENT", inner.x, inner.y);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("O/ESC close   1-6 buses   <-/-> or [/] adjust   F1-F4 presets", inner.x, inner.y + 18);
        g2.drawString("7 overload   8 overload bus   9 repair priority   0 emergency thrust", inner.x, inner.y + 34);

        String[] labels = {"PROPULSION", "SHIELD", "TACTICAL", "SENSOR", "ENGINEERING", "SUPERCHARGE"};
        double[] values = player.powerBusFractions();

        int rowY = inner.y + 66;
        int barW = 330;
        int barH = 16;
        for (int i = 0; i < labels.length; i++) {
            Ship.PowerBus bus = Ship.PowerBus.values()[i];
            int ry = rowY + i * 28;
            boolean focus = (i == Math.max(0, Math.min(5, focusSlot)));
            int pct = (int) Math.round(values[i] * 100.0);
            double eff = player.powerBusEffect(bus);
            double usefulCap = player.powerBusUsefulCapFraction(bus);
            double usefulFill = player.powerBusUsefulFillFraction(bus);
            double nominal = player.powerBusNominalFraction(bus);
            boolean saturated = values[i] >= usefulCap - 1e-6;

            g2.setColor(focus ? new Color(255, 230, 170, 220) : new Color(255, 255, 255, 200));
            g2.setFont(new Font("Consolas", focus ? Font.BOLD : Font.PLAIN, 14));
            g2.drawString((i + 1) + ": " + labels[i], inner.x + 2, ry + 13);

            int bx = inner.x + 132;
            int by = ry;
            g2.setColor(new Color(255, 255, 255, 50));
            g2.fillRoundRect(bx, by, barW, barH, 8, 8);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(bx, by, barW, barH, 8, 8);
            int fill = (int) Math.round((barW - 2) * usefulFill);
            Color c = switch (i) {
                case 0 -> new Color(120, 255, 150, 210);
                case 1 -> new Color(120, 210, 255, 210);
                case 2 -> new Color(255, 170, 120, 210);
                case 3 -> new Color(195, 170, 255, 210);
                case 4 -> new Color(255, 225, 130, 210);
                default -> new Color(175, 220, 190, 210);
            };
            g2.setColor(c);
            g2.fillRoundRect(bx + 1, by + 1, Math.max(0, fill), barH - 2, 7, 7);

            int nominalX = bx + 1 + (int) Math.round((barW - 2) * MathUtil.clamp(nominal / usefulCap, 0.0, 1.0));
            int capX = bx + 1 + (barW - 2);
            g2.setColor(new Color(255, 244, 180, 180));
            g2.drawLine(nominalX, by - 2, nominalX, by + barH + 2);
            g2.setColor(new Color(255, 180, 180, saturated ? 225 : 130));
            g2.drawLine(capX, by - 1, capX, by + barH + 1);

            g2.setColor(new Color(255, 255, 255, 220));
            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.drawString(String.format(Locale.US, "%3d%%", pct), bx + barW + 14, ry + 13);
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(new Color(230, 240, 255, 185));
            g2.drawString("eff " + signedPct(eff), bx + barW + 72, ry + 13);
            g2.setColor(saturated ? new Color(255, 196, 164, 210) : new Color(196, 214, 236, 170));
            g2.drawString((saturated ? "SAT" : "CAP") + " " + (int) Math.round(usefulCap * 100.0) + "%", bx + barW + 140, ry + 13);
        }

        double speedMul = (player.desiredSpeedBase > 0.01) ? (player.desiredSpeed / player.desiredSpeedBase) : 1.0;
        double weaponDmg = player.weaponDamageMultiplier();
        double weaponCycle = player.weaponCycleRateMultiplier();
        double sensor = player.sensorRangeMultiplier();
        double shield = player.shieldRegenMultiplier();
        double superCharge = player.superweaponRechargeRateMultiplier();

        int py = y + 278;
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Combat Effects", x + 20, py);
        py += 20;

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(200, 245, 220, 220));
        g2.drawString("Mobility: " + signedPct(speedMul) + "   Weapon Damage: " + signedPct(weaponDmg), x + 20, py);
        py += 16;
        g2.setColor(new Color(255, 225, 180, 220));
        g2.drawString("Fire Rate: " + signedPct(weaponCycle) + "   Sensor Range: " + signedPct(sensor), x + 20, py);
        py += 16;
        g2.setColor(new Color(180, 225, 255, 220));
        g2.drawString("Shield Effectiveness: " + signedPct(shield) + "   Super Recharge: " + signedPct(superCharge), x + 20, py);
        py += 16;
        g2.drawString("Shield Gate: " + shieldGateReadout(player) + "   Super: "
                + (int) Math.round(player.getSuperweaponRechargeProgress() * 100.0) + "%", x + 20, py);
        py += 20;

        String overload = player.isOverloadActive()
                ? "ACTIVE"
                : (player.isOverloadAvailable() ? "READY" : "COOLDOWN");
        g2.setColor(new Color(255, 214, 150, 225));
        g2.drawString("Overload " + overload + "  Bus " + player.overloadBus().name()
                + "  Heat " + (int) Math.round(player.overloadHeat() * 100.0) + "%  Debt "
                + (int) Math.round(player.overloadStressDebt() * 100.0) + "%  CD "
                + (int) Math.ceil(player.overloadCooldownRemaining()) + "s", x + 20, py);
        py += 18;
        String emergencyStatus = player.isEmergencyThrustActive() ? "ACTIVE" : "STANDBY";
        g2.setColor(new Color(255, 190, 150, 225));
        g2.drawString("Emergency Thrust " + emergencyStatus
                + "  Heat " + (int) Math.round(player.emergencyThrustHeat() * 100.0) + "%  CD "
                + (int) Math.ceil(player.emergencyThrustCooldownRemaining()) + "s  Propulsion "
                + (int) Math.round(player.propulsionRoomIntegrity() * 100.0) + "%", x + 20, py);
        py += 18;
        g2.setColor(new Color(200, 255, 200, 220));
        g2.drawString("Repair Priority: " + player.engineeringPriority().name(), x + 20, py);
        py += 20;

        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(225, 240, 255, 220));
        g2.drawString("Subsystem States", x + 20, py);
        py += 16;
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        StringBuilder stateLine = new StringBuilder();
        ArrayList<String> stateRows = new ArrayList<>();
        for (Ship.InternalSystem system : Ship.InternalSystem.values()) {
            Ship.SubsystemState st = player.subsystemState(system);
            String token = shortSystemName(system) + ":" + st.name();
            if (stateLine.length() > 0 && stateLine.length() + token.length() > 52) {
                stateRows.add(stateLine.toString());
                stateLine.setLength(0);
            }
            if (stateLine.length() > 0) stateLine.append("   ");
            stateLine.append(token);
        }
        if (stateLine.length() > 0) stateRows.add(stateLine.toString());
        g2.setColor(new Color(220, 230, 245, 210));
        for (int i = 0; i < stateRows.size() && i < 2; i++) {
            g2.drawString(stateRows.get(i), x + 20, py);
            py += 14;
        }

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Presets: F1 BALANCED   F2 ATTACK   F3 DEFENSE   F4 PURSUIT", x + 20, y + h - 18);
    }

    public static void drawFlightDeckOverlay(Graphics2D g2, Ship carrier, int focusSlot) {
        if (g2 == null || carrier == null || !carrier.isCarrier) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(820, clip.width - 100);
        int h = 356;
        int x = (clip.width - w) / 2;
        int y = Math.max(48, (clip.height - h) / 2);

        drawHudPanelFrame(g2, x, y, w, h, "FLIGHT DECK CONTROL", new Color(146, 210, 255, 225), ThemeArt.HUD_SPECIAL_FRAME);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(225, 236, 250, 188));
        g2.drawString("/ or ESC close   F1-F5 select slot   [ ] move focus   -/+ cycle role", x + 18, y + 46);
        boolean picketDeck = carrier.supportsPicketFlightDeck();
        g2.drawString("Each slot launches a 2-ship small-craft pair, or 1 picket on Mothership / Mobile Dockyard decks.", x + 18, y + 62);
        g2.drawString((picketDeck ? "5 picket   " : "") + "6 fighter   7 drone   8 bomber   9 all fighters   0 all bombers   Backspace default", x + 18, y + 78);

        int focus = Math.max(0, Math.min(4, focusSlot));
        int slotGap = 12;
        int slotW = (w - 36 - slotGap * 4) / 5;
        int slotH = 132;
        int slotY = y + 108;
        int fighters = 0;
        int bombers = 0;
        int drones = 0;
        int pickets = 0;

        for (int i = 0; i < 5; i++) {
            ShipRole role = carrier.flightDeckRoleAt(i);
            if (role == ShipRole.PICKET) pickets += 1;
            else if (role == ShipRole.BOMBER) bombers += 2;
            else if (role == ShipRole.DRONE) drones += 2;
            else fighters += 2;

            int slotX = x + 18 + i * (slotW + slotGap);
            boolean selected = (i == focus);
            Color accent = flightDeckRoleColor(role);

            g2.setColor(selected ? new Color(26, 42, 64, 224) : new Color(12, 20, 32, 196));
            g2.fillRoundRect(slotX, slotY, slotW, slotH, 16, 16);
            g2.setColor(withAlpha(accent, selected ? 220 : 140));
            g2.drawRoundRect(slotX, slotY, slotW, slotH, 16, 16);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.drawRoundRect(slotX + 1, slotY + 1, slotW - 2, slotH - 2, 14, 14);

            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.setColor(new Color(246, 250, 255, 228));
            g2.drawString("SQUAD " + (i + 1), slotX + 12, slotY + 20);

            int chipW = Math.max(70, Math.min(slotW - 24, g2.getFontMetrics(new Font("Consolas", Font.BOLD, 12)).stringWidth(flightDeckRoleLabel(role)) + 18));
            drawHudStatusChip(g2, flightDeckRoleLabel(role), slotX + 12, slotY + 30, chipW, 20, accent, true);

            g2.setFont(new Font("Consolas", Font.BOLD, 24));
            g2.setColor(withAlpha(accent, 228));
            g2.drawString(flightDeckRoleAbbrev(role), slotX + 12, slotY + 76);

            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(new Color(216, 228, 242, 178));
            g2.drawString(flightDeckRoleDescription(role), slotX + 12, slotY + 98);
            g2.drawString(selected ? (role == ShipRole.PICKET ? "ACTIVE PICKET BERTH" : "ACTIVE 2-SHIP PAIR") : "READY", slotX + 12, slotY + 116);
        }

        int summaryY = slotY + slotH + 34;
        drawHudStatusChip(g2, "PAIR SIZE 2", x + 18, summaryY, 104, 18, new Color(140, 210, 255, 214), true);
        drawHudStatusChip(g2, "PICKET " + pickets, x + 132, summaryY, 102, 18, flightDeckRoleColor(ShipRole.PICKET), pickets > 0);
        drawHudStatusChip(g2, "FIGHTER " + fighters, x + 244, summaryY, 102, 18, flightDeckRoleColor(ShipRole.FIGHTER), fighters > 0);
        drawHudStatusChip(g2, "DRONE " + drones, x + 356, summaryY, 94, 18, flightDeckRoleColor(ShipRole.DRONE), drones > 0);
        drawHudStatusChip(g2, "BOMBER " + bombers, x + 460, summaryY, 104, 18, flightDeckRoleColor(ShipRole.BOMBER), bombers > 0);
        drawHudStatusChip(g2, "MODE " + carrier.carrierCommandMode.name(), x + 574, summaryY, 122, 18,
                new Color(236, 196, 132, 214), carrier.carrierCommandMode == Ship.CarrierCommandMode.DEFEND);
        drawHudStatusChip(g2, "AUTO " + (carrier.carrierAutoLaunch ? "ON" : "OFF"), x + 706, summaryY, 96, 18,
                new Color(148, 228, 182, 214), carrier.carrierAutoLaunch);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(216, 228, 240, 190));
        g2.drawString("Launch rhythm: each launch call emits one 2-ship squad from the next squad slot in sequence.", x + 18, y + h - 38);
        g2.drawString("Picket decks emit one larger escort at a time; defend mode recalls bombers before the next pair leaves.", x + 18, y + h - 20);
    }

    private static String shortSystemName(Ship.InternalSystem system) {
        if (system == null) return "?";
        return switch (system) {
            case ENGINES -> "ENG";
            case SHIELDS -> "SHD";
            case REACTOR_CORE -> "RCT";
            case SENSORS -> "SNS";
            case WEAPONS -> "WPN";
            case BRIDGE -> "BRG";
            case WARP_ENGINES -> "WRP";
            case MAGAZINES -> "MAG";
        };
    }

    private static Color flightDeckRoleColor(ShipRole role) {
        if (role == ShipRole.PICKET) return new Color(255, 214, 132);
        if (role == ShipRole.BOMBER) return new Color(255, 168, 124);
        if (role == ShipRole.DRONE) return new Color(150, 226, 204);
        return new Color(132, 190, 255);
    }

    private static String flightDeckRoleLabel(ShipRole role) {
        if (role == ShipRole.PICKET) return "PICKET ESCORT";
        if (role == ShipRole.BOMBER) return "HEAVY BOMBER";
        if (role == ShipRole.DRONE) return "MULTIROLE DRONE";
        return "ESCORT FIGHTER";
    }

    private static String flightDeckRoleAbbrev(ShipRole role) {
        if (role == ShipRole.PICKET) return "PCK";
        if (role == ShipRole.BOMBER) return "BMB";
        if (role == ShipRole.DRONE) return "DRN";
        return "FGT";
    }

    private static String flightDeckRoleDescription(ShipRole role) {
        if (role == ShipRole.PICKET) return "LARGE ESCORT LAUNCH";
        if (role == ShipRole.BOMBER) return "ANTI-SHIP STRIKE";
        if (role == ShipRole.DRONE) return "FLEX SUPPORT";
        return "BOMBER ESCORT";
    }

    public static void drawCrewStationsOverlay(Graphics2D g2, GameContext ctx) {
        if (g2 == null || ctx == null || ctx.player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(1010, clip.width - 56);
        int h = 560;
        int x = (clip.width - w) / 2;
        int y = Math.max(34, (clip.height - h) / 2);

        drawHudPanelFrame(g2, x, y, w, h, "HELP AND OPERATIONS", new Color(255, 214, 150, 225), ThemeArt.HUD_SPECIAL_FRAME);
        Rectangle inner = themedContentRect(ThemeArt.HUD_SPECIAL_FRAME, x, y, w, h);

        g2.setColor(new Color(255, 240, 180, 230));
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.drawString("HELP AND OPERATIONS", inner.x, inner.y);

        g2.setColor(new Color(255, 255, 255, 170));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("H/ESC close   F1-F5 stations   A toggle AI   <-/-> cycle station   N HUD detail", inner.x, inner.y + 18);

        int portraitPaneX = inner.x;
        int portraitPaneY = inner.y + 36;
        int portraitPaneW = 232;
        int portraitPaneH = inner.height - 36;

        g2.setColor(new Color(255, 255, 255, 28));
        g2.fillRoundRect(portraitPaneX, portraitPaneY, portraitPaneW, portraitPaneH, 12, 12);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(portraitPaneX, portraitPaneY, portraitPaneW, portraitPaneH, 12, 12);

        CrewPortraitSystem.PortraitAsset activePortrait = CrewPortraitSystem.getPortrait(ctx.command.activeCrewStation);
        BufferedImage portraitImage = activePortrait.image();

        int portraitX = portraitPaneX + 10;
        int portraitY = portraitPaneY + 24;
        int portraitW = portraitPaneW - 20;
        int portraitH = portraitPaneH - 62;

        g2.setColor(new Color(0, 0, 0, 145));
        g2.fillRoundRect(portraitX, portraitY, portraitW, portraitH, 10, 10);

        if (portraitImage != null) {
            double sx = portraitW / (double) portraitImage.getWidth();
            double sy = portraitH / (double) portraitImage.getHeight();
            double scale = Math.min(sx, sy);
            int dw = Math.max(1, (int) Math.round(portraitImage.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(portraitImage.getHeight() * scale));
            int dx = portraitX + (portraitW - dw) / 2;
            int dy = portraitY + (portraitH - dh) / 2;
            g2.drawImage(portraitImage, dx, dy, dw, dh, null);
        }

        g2.setColor(new Color(255, 255, 255, 118));
        g2.drawRoundRect(portraitX, portraitY, portraitW, portraitH, 10, 10);

        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 245, 210, 225));
        g2.drawString(ctx.command.activeCrewStation.name(), portraitPaneX + 12, portraitPaneY + 16);

        int panelX = portraitPaneX + portraitPaneW + 14;
        int panelW = inner.x + inner.width - panelX;
        int textRight = inner.x + inner.width;

        int tabX = panelX + 8;
        int tabY = inner.y + 36;
        int tabGap = 8;
        int stationCount = GameContext.CrewStation.values().length;
        int tw = Math.max(104, (panelW - 16 - tabGap * (stationCount - 1)) / stationCount);

        for (GameContext.CrewStation station : GameContext.CrewStation.values()) {
            boolean active = (station == ctx.command.activeCrewStation);
            boolean auto = UISystem.stationAutomation(ctx, station);
            g2.setColor(active ? new Color(255, 220, 140, 180) : new Color(255, 255, 255, 45));
            g2.fillRoundRect(tabX, tabY, tw, 24, 10, 10);
            g2.setColor(active ? new Color(255, 245, 210, 220) : new Color(255, 255, 255, 120));
            g2.drawRoundRect(tabX, tabY, tw, 24, 10, 10);

            CrewPortraitSystem.PortraitAsset iconAsset = CrewPortraitSystem.getPortrait(station);
            BufferedImage icon = iconAsset.image();
            if (icon != null) {
                g2.drawImage(icon, tabX + 6, tabY + 4, 16, 16, null);
            }

            g2.setFont(new Font("Consolas", active ? Font.BOLD : Font.PLAIN, 12));
            g2.setColor(new Color(250, 250, 250, 220));
            g2.drawString(station.name(), tabX + 26, tabY + 16);
            g2.setColor(auto ? new Color(120, 255, 170, 220) : new Color(255, 150, 140, 220));
            g2.drawString(auto ? "AI" : "MAN", tabX + tw - 34, tabY + 16);
            tabX += tw + tabGap;
        }

        int readoutX = panelX + 12;
        int ly = y + 126;

        Shape oldClip = g2.getClip();
        g2.setClip(new Rectangle(readoutX - 4, y + 92, Math.max(20, textRight - readoutX), h - 108));

        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Ship State", readoutX, ly);
        ly += 20;

        int lockDist = -1;
        if (ctx.lockedTarget != null && ctx.lockedTarget.alive) {
            lockDist = (int) Math.round(Math.hypot(ctx.lockedTarget.x - ctx.player.x, ctx.lockedTarget.y - ctx.player.y));
        }
        boolean sensorsOnline = !ctx.player.isSystemDestroyed(Ship.InternalSystem.SENSORS);
        int fireRooms = ctx.player.activeFireRoomCount();
        double fireLoad = ctx.player.totalFireIntensity();
        ShipRoomLayout.RoomId hotspot = ctx.player.hottestFireRoom();
        String hotspotLabel = "NONE";
        if (hotspot != null) {
            ShipRoomLayout.RoomDef hotspotDef = ShipRoomLayout.roomForId(ctx.player.role, ctx.player.faction, hotspot);
            hotspotLabel = (hotspotDef != null && hotspotDef.label != null && !hotspotDef.label.isBlank())
                    ? hotspotDef.label
                    : hotspot.name();
        }
        ShipRoomLayout.RoomId focusRoom = ctx.player.integrityFocusRoom();

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(210, 235, 255, 220));
        g2.drawString("Captain: " + ctx.command.captainDirective
                + "   Fleet: " + ctx.command.alliedFleetCommand
                + " / " + ctx.command.alliedFleetFormation, readoutX, ly);
        ly += 16;
        g2.drawString("Helm: " + ctx.command.helmMode
                + "   Tactical: " + ctx.command.tacticalMode
                + "   Lock: " + ((ctx.lockedTarget == null) ? "NONE" : (ctx.lockedTarget.name + " (" + Math.max(0, lockDist) + "m)")), readoutX, ly);
        ly += 16;
        g2.drawString("Engineering: " + ctx.command.engineeringMode
                + "   Priority: " + ctx.player.engineeringPriority()
                + "   Overload: " + (ctx.player.isOverloadActive() ? ("ACTIVE " + ctx.player.overloadBus().name()) : "STANDBY"), readoutX, ly);
        ly += 16;
        g2.drawString("Crew: " + ctx.player.crewOrder
                + "   Readiness " + (int) Math.round(ctx.player.crewReadiness() * 100.0) + "%"
                + "   Fire " + fireRooms + " / " + String.format("%.1f", fireLoad), readoutX, ly);
        ly += 16;
        g2.drawString("Science: " + (sensorsOnline ? "ONLINE" : "DISABLED")
                + "   EW: " + (ctx.command.scienceJamming ? "JAMMING" : "PASSIVE")
                + "   Hotspot: " + hotspotLabel, readoutX, ly);
        ly += 16;
        String fieldLabel = (focusRoom == null)
                ? "NONE"
                : xrayRoomDisplayLabel(focusRoom) + " " + (int) Math.ceil(ctx.player.integrityFocusRemaining()) + "s";
        String voice = (ctx.ui.voiceCaptionT > 0.0 && ctx.ui.voiceCaption != null && !ctx.ui.voiceCaption.isBlank())
                ? ctx.ui.voiceCaption
                : "IDLE";
        g2.drawString("Field: " + fieldLabel
                + "   Voice: " + voice
                + "   Captions: " + (ctx.ui.voiceCaptionsEnabled ? "ON" : "OFF"), readoutX, ly);

        ly += 28;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Active Station Orders", readoutX, ly);
        ly += 20;
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        switch (ctx.command.activeCrewStation) {
            case CAPTAIN -> {
                g2.setColor(new Color(255, 230, 175, 220));
                g2.drawString("1 BALANCED  2 ATTACK  3 DEFENSE  4 EMERGENCY  5 MINE", readoutX, ly);
                ly += 16;
                g2.drawString("6 ESCORT  7 DEFEND  8 REPAIR  9 RTB  0 FORMATION", readoutX, ly);
                ly += 16;
                g2.drawString("Sets ship posture and allied fleet command.", readoutX, ly);
            }
            case HELM -> {
                g2.setColor(new Color(200, 240, 255, 220));
                g2.drawString("1 INTERCEPT  2 ORBIT  3 RANGE  4 EVASIVE  5 THRUST", readoutX, ly);
                ly += 16;
                g2.drawString("Controls spacing, approach, and thrust bias.", readoutX, ly);
            }
            case TACTICAL -> {
                g2.setColor(new Color(255, 210, 180, 220));
                g2.drawString("1 HOLD FIRE  2 DEFENSIVE  3 AGGRESSIVE", readoutX, ly);
                ly += 16;
                g2.drawString("Controls lock discipline and firing posture.", readoutX, ly);
            }
            case ENGINEERING -> {
                g2.setColor(new Color(200, 255, 200, 220));
                g2.drawString("1 BALANCED  2 ATTACK  3 DEFENSE  4 DAMAGE CONTROL", readoutX, ly);
                ly += 16;
                g2.drawString("5 OVERLOAD  6 BUS  7 PRIORITY  8 SUPPRESS FIRE", readoutX, ly);
                ly += 16;
                g2.drawString("Manual power edits override engineering AI instantly.", readoutX, ly);
            }
            case SCIENCE -> {
                g2.setColor(new Color(220, 210, 255, 220));
                g2.drawString("1 LOCK NEAREST  2 CLEAR LOCK  3 TOGGLE JAM", readoutX, ly);
                ly += 16;
                g2.drawString("Controls target acquisition and jamming posture.", readoutX, ly);
            }
        }

        ly += 24;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Quick Reference", readoutX, ly);
        ly += 20;
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(190, 245, 220, 220));
        g2.drawString("Voice: C captions   Z/X focus   ,/. volume", readoutX, ly);
        ly += 16;
        g2.setColor(new Color(206, 224, 244, 190));
        g2.drawString("Combat: SPACE/LMB fire   SHIFT/RMB secondary   L/MMB lock", readoutX, ly);
        ly += 16;
        g2.drawString("Systems: O power   H crew   M map   TAB fleet   ESC pause", readoutX, ly);
        ly += 16;
        g2.drawString("Automation: manual flight, fire, or power input disables matching AI.", readoutX, ly);

        g2.setClip(oldClip);

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Hover a station tab for the full role brief.", readoutX, y + h - 16);
    }

    private static final class XrayStackLayout {
        final int playerX;
        final int panelW;
        final int targetX;
        final int playerY;
        final int playerH;
        final int targetY;
        final int targetH;
        final boolean targetVisible;

        XrayStackLayout(int playerX, int targetX, int panelW, int playerY, int playerH, int targetY, int targetH, boolean targetVisible) {
            this.playerX = playerX;
            this.targetX = targetX;
            this.panelW = panelW;
            this.playerY = playerY;
            this.playerH = playerH;
            this.targetY = targetY;
            this.targetH = targetH;
            this.targetVisible = targetVisible;
        }
    }

    private static final class XrayPanelFrameCache {
        BufferedImage image;
        int width;
        int height;
        int titleHash;
        int subtitleHash;
        boolean interactive;
        GameContext.XrayFilterMode filterMode;
        ShipRoomLayout.RoomId focusedRoom;
        int cursorX;
        int cursorY;
        ShipRoomLayout.RoomId hoveredRoom;
        long renderedAtNanos;
    }

    private static XrayPanelFrameCache xrayPanelCacheFor(Ship ship) {
        return XRAY_PANEL_CACHE.computeIfAbsent(ship, k -> new XrayPanelFrameCache());
    }

    private static BufferedImage ensureXrayPanelImage(XrayPanelFrameCache cache, int w, int h) {
        if (cache.image == null || cache.width != w || cache.height != h) {
            cache.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            cache.width = w;
            cache.height = h;
        }
        return cache.image;
    }

    private static boolean canReuseXrayPanelCache(XrayPanelFrameCache cache,
                                                  long nowNanos,
                                                  int w, int h,
                                                  String title, String subtitle,
                                                  boolean interactive,
                                                  GameContext.XrayFilterMode filterMode,
                                                  ShipRoomLayout.RoomId focusedRoom,
                                                  int cursorX, int cursorY) {
        if (cache == null || cache.image == null) return false;
        if (cache.width != w || cache.height != h) return false;
        if (cache.interactive != interactive) return false;
        if ((nowNanos - cache.renderedAtNanos) > XRAY_PANEL_FRAME_CACHE_NS) return false;
        if (cache.titleHash != java.util.Objects.hashCode(title)) return false;
        if (cache.subtitleHash != java.util.Objects.hashCode(subtitle)) return false;
        if (!interactive) return true;
        if (cache.filterMode != filterMode) return false;
        if (cache.focusedRoom != focusedRoom) return false;
        return cache.cursorX == cursorX && cache.cursorY == cursorY;
    }

    private static void updateXrayPanelCacheMeta(XrayPanelFrameCache cache,
                                                 long nowNanos,
                                                 String title, String subtitle,
                                                 boolean interactive,
                                                 GameContext.XrayFilterMode filterMode,
                                                 ShipRoomLayout.RoomId focusedRoom,
                                                 int cursorX, int cursorY,
                                                 ShipRoomLayout.RoomId hoveredRoom) {
        cache.renderedAtNanos = nowNanos;
        cache.titleHash = java.util.Objects.hashCode(title);
        cache.subtitleHash = java.util.Objects.hashCode(subtitle);
        cache.interactive = interactive;
        cache.filterMode = filterMode;
        cache.focusedRoom = focusedRoom;
        cache.cursorX = cursorX;
        cache.cursorY = cursorY;
        cache.hoveredRoom = hoveredRoom;
    }

    public static ShipRoomLayout.RoomId playerXrayRoomAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null) return null;
        XrayStackLayout layout = computeXrayStackLayout(ctx.player, ctx.lockedTarget, ctx.ui.shopOpen, viewW, viewH);
        if (layout == null) return null;
        Rectangle mapRect = xrayMapRect(layout.playerX, layout.playerY, layout.panelW, layout.playerH);
        if (!mapRect.contains(mouseX, mouseY)) return null;
        for (ShipRoomLayout.VisualCell cell : ShipRoomLayout.visualCellsFor(ctx.player.role, ctx.player.faction)) {
            if (cell == null || cell.roomId == null) continue;
            Polygon p = xrayRoomPolygon(mapRect.x, mapRect.y, mapRect.width, mapRect.height, cell.xs, cell.ys);
            if (p != null && p.contains(mouseX, mouseY)) return cell.roomId;
        }
        return null;
    }

    private static void drawShipXrayPanel(Graphics2D g2, GameContext ctx, Ship ship, int x, int y, int w, int h,
                                          String title, String subtitle, boolean interactive) {
        if (g2 == null || ship == null) return;
        if (w < 80 || h < 80) return;

        long nowNanos = System.nanoTime();
        GameContext.XrayFilterMode filterMode = (ctx == null || ctx.ui.xrayFilterMode == null)
                ? GameContext.XrayFilterMode.ALL
                : ctx.ui.xrayFilterMode;
        ShipRoomLayout.RoomId focusedRoom = (ctx == null) ? null : ctx.ui.xrayFocusedRoom;
        int cursorX = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenX);
        int cursorY = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenY);

        XrayPanelFrameCache cache = xrayPanelCacheFor(ship);
        if (canReuseXrayPanelCache(cache, nowNanos, w, h, title, subtitle, interactive, filterMode, focusedRoom, cursorX, cursorY)) {
            if (interactive && ctx != null) ctx.ui.xrayHoveredRoom = cache.hoveredRoom;
            g2.drawImage(cache.image, x, y, null);
            return;
        }

        BufferedImage panelImage = ensureXrayPanelImage(cache, w, h);
        Graphics2D cg = panelImage.createGraphics();
        try {
            cg.setComposite(AlphaComposite.Clear);
            cg.fillRect(0, 0, w, h);
            cg.setComposite(AlphaComposite.SrcOver);
            cg.translate(-x, -y);
            drawShipXrayPanelImmediate(cg, ctx, ship, x, y, w, h, title, subtitle, interactive);
        } finally {
            cg.dispose();
        }

        ShipRoomLayout.RoomId hoveredRoom = (interactive && ctx != null) ? ctx.ui.xrayHoveredRoom : null;
        updateXrayPanelCacheMeta(
                cache,
                nowNanos,
                title, subtitle,
                interactive,
                filterMode,
                focusedRoom,
                cursorX, cursorY,
                hoveredRoom
        );
        g2.drawImage(panelImage, x, y, null);
    }

    private static void drawShipXrayPanelImmediate(Graphics2D g2, GameContext ctx, Ship ship, int x, int y, int w, int h,
                                                   String title, String subtitle, boolean interactive) {
        if (g2 == null || ship == null) return;
        if (w < 80 || h < 80) return;
        long nowNanos = System.nanoTime();

        String xrayTitle = (title == null || title.isBlank()) ? "TACTICAL X-RAY" : title;
        drawHudPanelFrame(g2, x, y, w, h, xrayTitle, new Color(150, 205, 255, 214), ThemeArt.HUD_SPECIAL_FRAME);
        g2.setFont(XRAY_SUBTITLE_FONT);
        g2.setColor(new Color(175, 218, 255, 205));
        if (subtitle != null && !subtitle.isBlank()) {
            g2.drawString(subtitle, x + 12, y + 36);
        }

        Rectangle mapRect = xrayMapRect(x, y, w, h);
        int mapX = mapRect.x;
        int mapY = mapRect.y;
        int mapW = mapRect.width;
        int mapH = mapRect.height;
        g2.setColor(new Color(10, 18, 28, 192));
        g2.fillRoundRect(mapX, mapY, mapW, mapH, 10, 10);
        g2.setColor(new Color(168, 206, 246, 76));
        g2.drawRoundRect(mapX, mapY, mapW, mapH, 10, 10);

        drawXrayShipUnderlay(g2, ship, mapRect, nowNanos);

        g2.setColor(new Color(255, 255, 255, 20));
        g2.drawLine(mapX + mapW / 2, mapY + 6, mapX + mapW / 2, mapY + mapH - 6);
        g2.drawLine(mapX + 6, mapY + mapH / 2, mapX + mapW - 6, mapY + mapH / 2);

        double[] hitFlash = new double[ShipRoomLayout.RoomId.values().length];
        List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(ship.role, ship.faction);
        refreshXrayPercentCache(ship, rooms, nowNanos);
        EnumMap<ShipRoomLayout.RoomId, Integer> pctCache = xrayPercentCacheFor(ship);

        List<Ship.RoomDamageEvent> events = ship.recentRoomDamageEvents();
        if (events != null) {
            for (int i = events.size() - 1; i >= 0; i--) {
                Ship.RoomDamageEvent ev = events.get(i);
                if (ev == null || ev.roomId == null) continue;
                if (ev.fromHazard) continue;
                double ageSec = (nowNanos - ev.timestampNanos) / 1_000_000_000.0;
                if (ageSec < 0.0 || ageSec > 2.4) continue;
                double strength = Math.max(0.0, 1.0 - ageSec / 2.4);
                int roomIdx = ev.roomId.ordinal();
                if (roomIdx < 0 || roomIdx >= hitFlash.length) continue;
                if (strength > hitFlash[roomIdx]) hitFlash[roomIdx] = strength;
            }
        }

        GameContext.XrayFilterMode filterMode = (ctx == null || ctx.ui.xrayFilterMode == null)
                ? GameContext.XrayFilterMode.ALL
                : ctx.ui.xrayFilterMode;
        ShipRoomLayout.RoomId focusedRoom = (ctx == null) ? null : ctx.ui.xrayFocusedRoom;
        ShipRoomLayout.RoomId hoveredRoom = null;
        int cursorX = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenX);
        int cursorY = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenY);
        ShipRoomLayout.RoomId repairRoom = xrayRepairTargetRoom(ship);

        String hottestRoomLabel = null;
        double hottestHit = 0.0;

        g2.setFont(XRAY_SYMBOL_FONT);
        FontMetrics symFm = g2.getFontMetrics();
        g2.setFont(XRAY_HP_FONT);
        FontMetrics hpFm = g2.getFontMetrics();

        List<ShipRoomLayout.VisualCell> visualCells = ShipRoomLayout.visualCellsFor(ship.role, ship.faction);
        List<ShipRoomLayout.VisualCell> drawCells = new ArrayList<>();
        List<Polygon> cellPolygons = new ArrayList<>();
        for (ShipRoomLayout.VisualCell cell : visualCells) {
            if (cell == null || cell.roomId == null) continue;
            Polygon p = xrayRoomPolygon(mapX, mapY, mapW, mapH, cell.xs, cell.ys);
            if (p == null || p.npoints < 3) continue;
            drawCells.add(cell);
            cellPolygons.add(p);
            if (interactive && p.contains(cursorX, cursorY)) hoveredRoom = cell.roomId;
        }
        if (interactive && ctx != null) ctx.ui.xrayHoveredRoom = hoveredRoom;

        Stroke oldStroke = g2.getStroke();
        for (int cellIdx = 0; cellIdx < drawCells.size(); cellIdx++) {
            ShipRoomLayout.VisualCell cell = drawCells.get(cellIdx);
            Polygon p = cellPolygons.get(cellIdx);
            ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ship.role, ship.faction, cell.roomId);
            if (room == null || cell.roomId == null || p == null || p.npoints < 3) continue;

            int pctVal = pctCache.getOrDefault(cell.roomId, -1);
            if (pctVal < 0) {
                pctVal = MathUtil.clamp((int) Math.round(ship.roomHealthFraction(cell.roomId) * 100.0), 0, 100);
            }
            double frac = pctVal * 0.01;
            double fireIntensity = ship.roomFireIntensity(cell.roomId);
            boolean disrupted = ship.isRoomDisrupted(cell.roomId);
            double disruptRepair = ship.roomDisruptionRepairProgress(cell.roomId);
            int roomIdx = cell.roomId.ordinal();
            double hitStrength = (roomIdx >= 0 && roomIdx < hitFlash.length) ? hitFlash[roomIdx] : 0.0;
            boolean disabled = pctVal <= 0 || (room.primarySystem != null && ship.isSystemDestroyed(room.primarySystem));
            double powerIntensity = xrayPowerRoutingIntensity(ship, room);
            boolean powerOutOfBand = Math.abs(powerIntensity - xrayNominalPowerTarget(room)) >= 0.035;
            boolean filteredIn = xrayRoomMatchesFilter(filterMode, frac, fireIntensity, disabled, powerOutOfBand);
            boolean focused = interactive && focusedRoom == cell.roomId;
            boolean hovered = interactive && hoveredRoom == cell.roomId;

            Color fill;
            if (!filteredIn) fill = new Color(70, 78, 96, 66);
            else if (disabled) fill = new Color(120, 120, 132, 132);
            else if (disrupted) fill = new Color(118, 132, 255, 126);
            else if (frac > 0.70) fill = new Color(95, 210, 255, 88);
            else if (frac > 0.35) fill = new Color(255, 195, 90, 120);
            else fill = new Color(255, 82, 82, 155);

            g2.setColor(fill);
            g2.fillPolygon(p);
            g2.setColor(disrupted
                    ? new Color(188, 214, 255, filteredIn ? 185 : 90)
                    : new Color(220, 245, 255, filteredIn ? 130 : 65));
            g2.drawPolygon(p);

            if (hitStrength > 0.01) {
                int a = MathUtil.clamp((int) Math.round(130 + hitStrength * 110), 0, 255);
                g2.setStroke(XRAY_HIT_STROKE);
                g2.setColor(new Color(255, 245, 145, a));
                g2.drawPolygon(p);
                g2.setStroke(oldStroke);
                if (hitStrength > hottestHit) {
                    hottestHit = hitStrength;
                    hottestRoomLabel = xrayRoomDisplayLabel(cell.roomId);
                }
            }

            if (disabled) {
                Rectangle b = p.getBounds();
                g2.setColor(new Color(20, 22, 28, 180));
                g2.setStroke(XRAY_DISABLED_STROKE);
                g2.drawLine(b.x + 2, b.y + 2, b.x + b.width - 2, b.y + b.height - 2);
                g2.drawLine(b.x + 2, b.y + b.height - 2, b.x + b.width - 2, b.y + 2);
                g2.setStroke(oldStroke);
            }

            Rectangle b = p.getBounds();
            int cx = (int) Math.round(b.getCenterX());
            int cy = (int) Math.round(b.getCenterY());
            boolean showFireSymbol = fireIntensity > 0.06;
            boolean showRoomSymbol = frac >= 0.999 && !showFireSymbol;

            if (showRoomSymbol && b.width >= 10 && b.height >= 8) {
                String symbol = xrayRoomSymbol(cell.roomId);
                Font labelFont = (b.width >= 20 && b.height >= 14) ? XRAY_SYMBOL_FONT : XRAY_REPAIR_FONT;
                g2.setFont(labelFont);
                FontMetrics labelFm = g2.getFontMetrics();
                int sw = labelFm.stringWidth(symbol);
                int sh = labelFm.getAscent();
                int sx = cx - sw / 2 - 4;
                int sy = cy - (sh + 5) / 2 - 1;
                Color symBg = focused
                        ? new Color(120, 210, 255, 200)
                        : (hovered ? new Color(80, 190, 255, 185)
                        : (hitStrength > 0.01)
                        ? new Color(255, 96, 72, MathUtil.clamp((int) Math.round(140 + 85 * hitStrength), 0, 255))
                        : new Color(18, 28, 44, filteredIn ? 156 : 96));
                g2.setColor(symBg);
                g2.fillRoundRect(sx, sy, sw + 8, sh + 5, 8, 8);
                g2.setColor(new Color(220, 245, 255, 190));
                g2.drawRoundRect(sx, sy, sw + 8, sh + 5, 8, 8);
                g2.setColor(new Color(250, 252, 255, 230));
                g2.drawString(symbol, sx + 4, sy + sh);
            }

            if (showFireSymbol && b.width >= 10 && b.height >= 8) {
                g2.setFont(XRAY_REPAIR_FONT);
                FontMetrics fireFm = g2.getFontMetrics();
                String fireSymbol = "F";
                int fw = fireFm.stringWidth(fireSymbol);
                int fh = fireFm.getAscent();
                int fx = cx - fw / 2 - 3;
                int fy = cy - (fh + 4) / 2;
                int fa = MathUtil.clamp((int) Math.round(170 + Math.min(1.0, fireIntensity) * 60), 0, 255);
                g2.setColor(new Color(255, 118, 54, fa));
                g2.fillRoundRect(fx, fy, fw + 6, fh + 4, 8, 8);
                g2.setColor(new Color(255, 220, 170, Math.min(255, fa + 20)));
                g2.drawRoundRect(fx, fy, fw + 6, fh + 4, 8, 8);
                g2.setColor(new Color(255, 248, 232, 235));
                g2.drawString(fireSymbol, fx + 3, fy + fh + 1);
            }

            if (disrupted && b.width >= 12 && b.height >= 10) {
                g2.setFont(XRAY_REPAIR_FONT);
                FontMetrics disruptFm = g2.getFontMetrics();
                String disruptSymbol = "D";
                int dw = disruptFm.stringWidth(disruptSymbol);
                int dh = disruptFm.getAscent();
                int dx = cx - dw / 2 - 3;
                int dy = cy - (dh + 4) / 2 + (showFireSymbol ? 12 : 0);
                int da = 180 + (int) Math.round((1.0 - disruptRepair) * 55.0);
                g2.setColor(new Color(126, 146, 255, MathUtil.clamp(da, 120, 255)));
                g2.fillRoundRect(dx, dy, dw + 6, dh + 4, 8, 8);
                g2.setColor(new Color(220, 232, 255, 235));
                g2.drawRoundRect(dx, dy, dw + 6, dh + 4, 8, 8);
                g2.drawString(disruptSymbol, dx + 3, dy + dh + 1);
            }

            if (cell.labelAnchor && b.width >= 28 && b.height >= 20) {
                String pct = XRAY_PCT_LABELS[MathUtil.clamp(pctVal, 0, 100)];
                g2.setFont(XRAY_HP_FONT);
                int px = cx - hpFm.stringWidth(pct) / 2;
                int py = Math.min(b.y + b.height - 4, cy + Math.max(8, b.height / 4));
                g2.setColor(new Color(245, 250, 255, filteredIn ? 220 : 120));
                g2.drawString(pct, px, py);
            }

            // Overlay: repair team/task marker
            if (repairRoom == cell.roomId) {
                g2.setColor(new Color(145, 255, 170, 200));
                g2.fillOval(cx - 4, cy + 14, 8, 8);
                g2.setColor(new Color(10, 35, 16, 220));
                g2.setFont(XRAY_REPAIR_FONT);
                g2.drawString("R", cx - 3, cy + 21);
            }
            // Overlay: power routing intensity bar
            int barX = b.x + 2;
            int barY = b.y + b.height - 4;
            int barW = Math.max(6, b.width - 4);
            int barFill = MathUtil.clamp((int) Math.round(barW * MathUtil.clamp(powerIntensity / 0.36, 0.0, 1.0)), 0, barW);
            g2.setColor(new Color(16, 18, 26, 140));
            g2.fillRect(barX, barY, barW, 2);
            g2.setColor(new Color(
                    MathUtil.clamp((int) Math.round(255 - powerIntensity * 420), 70, 255),
                    MathUtil.clamp((int) Math.round(110 + powerIntensity * 320), 80, 255),
                    255,
                    filteredIn ? 205 : 95
            ));
            g2.fillRect(barX, barY, barFill, 2);

            if (focused || hovered) {
                g2.setStroke(XRAY_FOCUS_STROKE);
                g2.setColor(new Color(130, 220, 255, 230));
                g2.drawPolygon(p);
                g2.setStroke(oldStroke);
            }
        }
        g2.setStroke(oldStroke);

        ShipRoomLayout.RoomId detailRoom = (interactive && hoveredRoom != null) ? hoveredRoom : focusedRoom;
        if (detailRoom != null) {
            boolean present = false;
            for (ShipRoomLayout.VisualCell cell : drawCells) {
                if (cell != null && detailRoom == cell.roomId) {
                    present = true;
                    break;
                }
            }
            if (!present) detailRoom = null;
        }
        if (interactive && detailRoom != null) {
            ShipRoomLayout.RoomDef roomDef = ShipRoomLayout.roomForId(ship.role, ship.faction, detailRoom);
            int pct = MathUtil.clamp((int) Math.round(ship.roomHealthFraction(detailRoom) * 100.0), 0, 100);
            double fire = ship.roomFireIntensity(detailRoom);
            boolean disrupted = ship.isRoomDisrupted(detailRoom);
            String roomLabel = xrayRoomDisplayLabel(detailRoom);
            double power = xrayPowerRoutingIntensity(ship, roomDef);
            String disruptText = disrupted
                    ? "  DISRUPTED  REPAIR " + (int) Math.round(ship.roomDisruptionRepairProgress(detailRoom) * 100.0) + "%"
                    : "";
            String fieldText = "";
            ShipRoomLayout.RoomId focusRoom = ship.integrityFocusRoom();
            if (focusRoom != null) {
                fieldText = "  FIELD " + xrayRoomDisplayLabel(focusRoom)
                        + " " + (int) Math.ceil(ship.integrityFocusRemaining()) + "s";
            }
            String line = roomLabel + "  HP " + pct + "%  FIRE " + String.format("%.2f", fire)
                    + "  POWER " + (int) Math.round(power * 100.0) + "%" + disruptText + fieldText;
            g2.setFont(XRAY_META_FONT);
            g2.setColor(new Color(220, 244, 255, 220));
            g2.drawString(line, x + 10, y + h - 10);
            drawXrayTooltip(g2, mapRect, cursorX, cursorY, roomLabel, pct, fire, power, ship, roomDef);
        } else if (hottestRoomLabel != null && hottestHit > 0.01) {
            g2.setFont(XRAY_META_FONT);
            g2.setColor(new Color(255, 228, 164, 230));
            g2.drawString("HIT ROOM: " + hottestRoomLabel, x + 10, y + h - 10);
        } else {
            g2.setFont(XRAY_META_FONT);
            g2.setColor(new Color(170, 210, 240, 180));
            g2.drawString("HIT ROOM: NONE", x + 10, y + h - 10);
        }

        g2.setFont(XRAY_META_FONT);
        g2.setColor(new Color(190, 230, 255, 180));
        g2.drawString("RED<35%  AMBER<70%  BLUE>=70%", x + 10, y + h - 34);
        String filterLabel = (filterMode == null) ? "ALL" : filterMode.name();
        String focusLabel = (focusedRoom == null) ? "NONE" : xrayRoomDisplayLabel(focusedRoom);
        g2.drawString("FILTER[" + filterLabel + "] ` cycle   ' clear   CLICK room = focus/protect   FOCUS: " + focusLabel, x + 10, y + h - 22);
    }

    private static Rectangle xrayMapRect(int panelX, int panelY, int panelW, int panelH) {
        return new Rectangle(
                panelX + 10,
                panelY + 38,
                Math.max(20, panelW - 20),
                Math.max(20, panelH - 76)
        );
    }

    private static boolean xrayRoomMatchesFilter(GameContext.XrayFilterMode mode,
                                                 double hpFrac, double fireIntensity,
                                                 boolean disabled, boolean powerOutOfBand) {
        if (mode == null || mode == GameContext.XrayFilterMode.ALL) return true;
        return switch (mode) {
            case DAMAGE -> hpFrac < 0.99 || disabled;
            case HAZARD -> fireIntensity > 0.05;
            case POWER -> powerOutOfBand;
            case DISABLED -> disabled;
            default -> true;
        };
    }

    private static double xrayPowerRoutingIntensity(Ship ship, ShipRoomLayout.RoomDef room) {
        if (ship == null || room == null) return 0.0;
        Ship.InternalSystem system = room.primarySystem;
        if (system == null) return ship.powerAuxiliaryFrac();
        return switch (system) {
            case ENGINES, WARP_ENGINES -> ship.powerEnginesFrac();
            case SHIELDS -> ship.powerShieldsFrac();
            case WEAPONS, MAGAZINES -> ship.powerWeaponsFrac();
            case SENSORS, BRIDGE -> ship.powerSensorsFrac();
            case REACTOR_CORE -> ship.powerEngineeringFrac();
        };
    }

    private static double xrayNominalPowerTarget(ShipRoomLayout.RoomDef room) {
        if (room == null || room.primarySystem == null) return 0.12;
        return switch (room.primarySystem) {
            case ENGINES, WARP_ENGINES, SHIELDS, WEAPONS, MAGAZINES, REACTOR_CORE -> 0.18;
            case SENSORS, BRIDGE -> 0.15;
        };
    }

    private static void drawXrayShipUnderlay(Graphics2D g2, Ship ship, Rectangle mapRect, long nowNanos) {
        if (g2 == null || ship == null || mapRect == null) return;

        Graphics2D ug = (Graphics2D) g2.create();
        try {
            RoundRectangle2D.Float clipShape = new RoundRectangle2D.Float(
                    mapRect.x, mapRect.y, mapRect.width, mapRect.height, 10, 10
            );
            ug.clip(clipShape);

            Polygon hull = xrayHullPolygon(ship.role, ship.faction, mapRect);
            if (hull != null && hull.npoints >= 3) {
                Rectangle hb = hull.getBounds();
                if (hb.width > 0 && hb.height > 0) {
                    BufferedImage skin = ShipSkinLibrary.getSkin(ship.role, ship.faction);
                    if (skin != null) {
                        double scale = Math.min(hb.width / (double) Math.max(1, skin.getWidth()),
                                hb.height / (double) Math.max(1, skin.getHeight()));
                        int drawW = Math.max(1, (int) Math.round(skin.getWidth() * scale));
                        int drawH = Math.max(1, (int) Math.round(skin.getHeight() * scale));
                        int dx = (int) Math.round(hb.getCenterX() - drawW / 2.0);
                        int dy = (int) Math.round(hb.getCenterY() - drawH / 2.0);
                        ug.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.17f));
                        ug.drawImage(skin, dx, dy, drawW, drawH, null);
                        ug.setComposite(AlphaComposite.SrcOver);
                    }

                    Shape oldClip = ug.getClip();
                    ug.clip(hull);
                    float pulse = (float) (0.5 + 0.5 * Math.sin(nowNanos / 220_000_000.0));
                    ug.setPaint(new GradientPaint(
                            hb.x, hb.y, new Color(78, 134, 180, 34 + (int) Math.round(10 * pulse)),
                            hb.x, hb.y + hb.height, new Color(18, 34, 56, 10)
                    ));
                    ug.fillRect(hb.x, hb.y, hb.width, hb.height);
                    ug.setClip(oldClip);

                    ug.setStroke(new BasicStroke(1.1f));
                    ug.setColor(new Color(165, 218, 255, 72));
                    ug.drawPolygon(hull);
                    ug.setColor(new Color(160, 222, 255, 28));
                    ug.drawLine(hb.x + 6, (int) Math.round(hb.getCenterY()), hb.x + hb.width - 6, (int) Math.round(hb.getCenterY()));

                    for (int i = 1; i <= 4; i++) {
                        int sx = hb.x + (int) Math.round(hb.width * (i / 5.0));
                        ug.drawLine(sx, hb.y + 6, sx, hb.y + hb.height - 6);
                    }
                }
            }

            int scanAlpha = 10 + (int) Math.round(4 * (0.5 + 0.5 * Math.sin(nowNanos / 180_000_000.0)));
            ug.setColor(new Color(170, 228, 255, scanAlpha));
            for (int yy = mapRect.y + 3; yy < mapRect.y + mapRect.height; yy += 6) {
                ug.drawLine(mapRect.x + 4, yy, mapRect.x + mapRect.width - 4, yy);
            }
        } finally {
            ug.dispose();
        }
    }

    private static Polygon xrayHullPolygon(ShipRole role, Rectangle mapRect) {
        return xrayHullPolygon(role, null, mapRect);
    }

    private static Polygon xrayHullPolygon(ShipRole role, Faction faction, Rectangle mapRect) {
        if (mapRect == null) return null;
        Polygon hull = ShipHullSilhouette.hullPolygon(role, 100.0, faction);
        if (hull == null || hull.npoints < 3) return null;

        Rectangle b = hull.getBounds();
        if (b.width <= 0 || b.height <= 0) return null;

        double minX = b.getMinX();
        double maxX = b.getMaxX();
        double minY = b.getMinY();
        double maxY = b.getMaxY();
        double halfW = Math.max(1.0, Math.max(Math.abs(minX), Math.abs(maxX)));
        double halfH = Math.max(1.0, Math.max(Math.abs(minY), Math.abs(maxY)));
        double[] normalizedXs = new double[hull.npoints];
        double[] normalizedYs = new double[hull.npoints];
        for (int i = 0; i < hull.npoints; i++) {
            normalizedXs[i] = MathUtil.clamp((hull.xpoints[i] / halfW) * 0.98, -1.0, 1.0);
            normalizedYs[i] = MathUtil.clamp((hull.ypoints[i] / halfH) * 0.98, -1.0, 1.0);
        }
        return xrayRoomPolygon(mapRect.x, mapRect.y, mapRect.width, mapRect.height, normalizedXs, normalizedYs);
    }

    private static ShipRoomLayout.RoomId xrayRepairTargetRoom(Ship ship) {
        if (ship == null) return null;
        if (ship.crewOrder != Ship.CrewOrder.DAMAGE_CONTROL) return null;
        ShipRoomLayout.RoomId disruption = ship.disruptionRepairTargetRoom();
        if (disruption != null) return disruption;
        ShipRoomLayout.RoomId hotspot = ship.hottestFireRoom();
        if (hotspot != null) return hotspot;
        ShipRoomLayout.RoomId best = null;
        double lowest = 1.0;
        for (Ship.RoomStatus rs : ship.roomStatusSnapshot()) {
            if (rs == null || rs.roomId == null) continue;
            if (rs.hpMax <= 1e-9) continue;
            double frac = MathUtil.clamp(rs.hp / rs.hpMax, 0.0, 1.0);
            if (frac < lowest) {
                lowest = frac;
                best = rs.roomId;
            }
        }
        return (lowest < 0.995) ? best : null;
    }

    private static String xrayRoomDisplayLabel(ShipRoomLayout.RoomId roomId) {
        return ShipRoomLayout.displayLabel(roomId);
    }

    private static void drawXrayTooltip(Graphics2D g2, Rectangle mapRect, int cursorX, int cursorY,
                                        String roomLabel, int hpPct, double fireIntensity, double powerIntensity,
                                        Ship ship, ShipRoomLayout.RoomDef roomDef) {
        if (g2 == null || mapRect == null) return;
        if (!mapRect.contains(cursorX, cursorY)) return;
        String system = (roomDef == null || roomDef.primarySystem == null)
                ? "SUPER"
                : roomDef.primarySystem.name();
        String line1 = roomLabel;
        String line2 = "HP " + hpPct + "%  FIRE " + String.format("%.2f", fireIntensity)
                + "  POWER " + (int) Math.round(powerIntensity * 100.0) + "%";
        String line3 = "SYSTEM " + system + "  " + ((ship != null && roomDef != null && roomDef.primarySystem != null
                && ship.isSystemDestroyed(roomDef.primarySystem)) ? "DISABLED" : "ONLINE");

        Font oldFont = g2.getFont();
        g2.setFont(XRAY_META_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int tw = Math.max(fm.stringWidth(line1), Math.max(fm.stringWidth(line2), fm.stringWidth(line3))) + 14;
        int th = 44;
        int tx = cursorX + 12;
        int ty = cursorY - th - 8;
        if (tx + tw > mapRect.x + mapRect.width) tx = cursorX - tw - 12;
        if (ty < mapRect.y + 2) ty = cursorY + 12;
        tx = MathUtil.clamp(tx, mapRect.x + 2, mapRect.x + mapRect.width - tw - 2);
        ty = MathUtil.clamp(ty, mapRect.y + 2, mapRect.y + mapRect.height - th - 2);

        g2.setColor(new Color(6, 10, 18, 222));
        g2.fillRoundRect(tx, ty, tw, th, 10, 10);
        g2.setColor(new Color(145, 206, 255, 190));
        g2.drawRoundRect(tx, ty, tw, th, 10, 10);
        g2.setColor(new Color(236, 248, 255, 230));
        g2.drawString(line1, tx + 7, ty + 13);
        g2.setColor(new Color(205, 236, 255, 220));
        g2.drawString(line2, tx + 7, ty + 26);
        g2.setColor(new Color(182, 220, 250, 210));
        g2.drawString(line3, tx + 7, ty + 39);
        g2.setFont(oldFont);
    }

    private static Polygon xrayRoomPolygon(int x, int y, int w, int h, double[] normalizedXs, double[] normalizedYs) {
        if (normalizedXs == null || normalizedYs == null) return null;
        int n = Math.min(normalizedXs.length, normalizedYs.length);
        if (n < 3) return null;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            double nx = Math.max(-1.0, Math.min(1.0, normalizedXs[i]));
            double ny = Math.max(-1.0, Math.min(1.0, normalizedYs[i]));
            xs[i] = x + (int) Math.round((nx * 0.5 + 0.5) * w);
            ys[i] = y + (int) Math.round((ny * 0.5 + 0.5) * h);
        }
        return new Polygon(xs, ys, n);
    }

    private static String[] buildXrayPctLabels() {
        String[] labels = new String[101];
        for (int i = 0; i <= 100; i++) {
            labels[i] = i + "%";
        }
        return labels;
    }

    private static EnumMap<ShipRoomLayout.RoomId, Integer> xrayPercentCacheFor(Ship ship) {
        return XRAY_ROOM_PCT_CACHE.computeIfAbsent(
                ship,
                k -> new EnumMap<>(ShipRoomLayout.RoomId.class)
        );
    }

    private static void refreshXrayPercentCache(Ship ship, List<ShipRoomLayout.RoomDef> rooms, long nowNanos) {
        if (ship == null || rooms == null || rooms.isEmpty()) return;
        EnumMap<ShipRoomLayout.RoomId, Integer> cache = xrayPercentCacheFor(ship);
        long last = XRAY_ROOM_PCT_CACHE_TS.getOrDefault(ship, 0L);
        boolean refreshDue = (nowNanos - last) >= XRAY_PERCENT_REFRESH_NS;
        if (!refreshDue && cache.size() >= rooms.size()) return;

        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.id == null) continue;
            int pct = MathUtil.clamp((int) Math.round(ship.roomHealthFraction(room.id) * 100.0), 0, 100);
            cache.put(room.id, pct);
        }
        XRAY_ROOM_PCT_CACHE_TS.put(ship, nowNanos);
    }

    private static XrayStackLayout computeXrayStackLayout(Player player, Ship lockedTarget, boolean shopOpen,
                                                          int viewW, int viewH) {
        if (player == null || shopOpen) return null;
        if (!player.alive || player.dying || player.hp <= 0) return null;

        Rectangle menu = getCoreMenuBarRect(viewW, viewH);
        int availableH = menu.y - 54;
        if (availableH < 130) return null;

        boolean sensorsOnline = !player.isSystemDestroyed(Ship.InternalSystem.SENSORS);
        boolean targetVisible = lockedTarget != null
                && lockedTarget.alive && !lockedTarget.dying && lockedTarget.hp > 0
                && sensorsOnline
                && !(lockedTarget.faction != null && player.faction != null
                && lockedTarget.faction.isFriendlyTo(player.faction));

        int menuTop = menu.y;
        int panelY;
        int playerH;
        int targetH;
        int playerY;
        int targetY;

        if (targetVisible) {
            int reservedVitalsW = 220;
            int outerMargin = 12;
            int sideGap = 12;
            int centerGap = 18;
            int usableX = outerMargin + reservedVitalsW + sideGap;
            int usableW = viewW - (outerMargin + reservedVitalsW + sideGap) * 2;
            if (usableW < 560) return null;

            int panelW = Math.max(270, Math.min(396, (usableW - centerGap) / 2));
            int totalW = panelW * 2 + centerGap;
            int playerX = usableX + Math.max(0, (usableW - totalW) / 2);
            int targetX = playerX + panelW + centerGap;

            playerH = Math.max(166, Math.min(236, availableH - 12));
            targetH = playerH;
            panelY = menuTop - playerH - 8;
            playerY = panelY;
            targetY = panelY;
            if (playerY < 48) return null;

            return new XrayStackLayout(playerX, targetX, panelW, playerY, playerH, targetY, targetH, true);
        }

        int panelW = Math.max(270, Math.min(396, menu.width - 170));
        int playerX = menu.x + (menu.width - panelW) / 2;
        playerH = Math.max(170, Math.min(228, (int) Math.round(availableH * 0.58)));
        playerY = menuTop - playerH - 8;
        if (playerY < 48) return null;

        return new XrayStackLayout(playerX, playerX, panelW, playerY, playerH, playerY, 0, false);
    }

    private static void drawLockedTargetXrayHud(Graphics2D g2, GameContext ctx, Player player, Ship lockedTarget,
                                                boolean shopOpen, int viewW, int viewH) {
        if (g2 == null || player == null) return;
        XrayStackLayout layout = computeXrayStackLayout(player, lockedTarget, shopOpen, viewW, viewH);
        if (layout == null) return;

        drawShipXrayPanel(g2, ctx, player, layout.playerX, layout.playerY, layout.panelW, layout.playerH,
                "SHIP X-RAY", "OWN HULL TELEMETRY", true);

        if (layout.targetH > 0 && layout.targetVisible) {
            String role = (lockedTarget.role == null) ? "UNKNOWN" : lockedTarget.role.name();
            String subtitle = lockedTarget.name + " / " + role;
            drawShipXrayPanel(g2, ctx, lockedTarget, layout.targetX, layout.targetY, layout.panelW, layout.targetH,
                    "TARGET X-RAY", subtitle, false);
        }
    }

    private static String xrayRoomSymbol(ShipRoomLayout.RoomId roomId) {
        return ShipRoomLayout.symbol(roomId);
    }

    public static void drawBaseUpgradeOverlay(Graphics2D g2, Ship selectedShip, String baseName, int credits, int baseOre,
                                              int hullLv, int shieldLv, int turretLv, int miningLv, int hangarLv,
                                              int maxHangarTier, boolean fleetHub) {
        // "B" style: a diegetic sci-fi console panel (glow edges, grid, bars, subtle scanline).
        int w = 520;
        int h = 284;
        int pad = 22;
        int viewW = g2.getClipBounds().width;
        int x = viewW - w - pad;
        int y = 240;

        double t = System.nanoTime() / 1_000_000_000.0;
        int glowA = 55 + (int) Math.round(25 * (0.5 + 0.5 * Math.sin(t * 2.2)));

        // Outer glow
        g2.setColor(new Color(90, 220, 255, MathUtil.clamp(glowA, 30, 90)));
        g2.fillRoundRect(x - 4, y - 4, w + 8, h + 8, 24, 24);

        if (!paintThemedHudFrame(g2, x, y, w, h, new Color(120, 214, 255, 190), ThemeArt.HUD_SPECIAL_FRAME, 20)) {
            g2.setColor(new Color(0, 0, 0, 190));
            g2.fillRoundRect(x, y, w, h, 20, 20);
            g2.setColor(new Color(255, 255, 255, 95));
            g2.drawRoundRect(x, y, w, h, 20, 20);
        }
        Rectangle inner = themedContentRect(ThemeArt.HUD_SPECIAL_FRAME, x, y, w, h);

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 18));
        for (int gx = x + 14; gx < x + w - 14; gx += 28) g2.drawLine(gx, y + 40, gx, y + h - 14);
        for (int gy = y + 40; gy < y + h - 14; gy += 22) g2.drawLine(x + 14, gy, x + w - 14, gy);

        // Header bar
        g2.setColor(new Color(20, 70, 90, 190));
        g2.fillRoundRect(x + 10, y + 10, w - 20, 26, 14, 14);
        g2.setColor(new Color(90, 220, 255, 110));
        g2.drawRoundRect(x + 10, y + 10, w - 20, 26, 14, 14);

        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.setColor(new Color(230, 250, 255, 230));
        g2.drawString(fleetHub ? "FLEET UPGRADE CONSOLE  (ESC)" : "BASE UPGRADE CONSOLE  (ESC)", inner.x, inner.y);

        // Scanline sweep
        int sweepY = y + 42 + (int) Math.round(((Math.sin(t * 0.9) * 0.5 + 0.5)) * (h - 70));
        g2.setColor(new Color(90, 220, 255, 14));
        g2.fillRect(x + 10, sweepY, w - 20, 12);

        // Info
        if (baseName == null || baseName.isBlank()) {
            baseName = fleetHub ? "Selected hull" : "Base";
        }
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        int ty = inner.y + 28;
        g2.setColor(new Color(255, 255, 255, 210));
        if (fleetHub) {
            g2.drawString("Selected hull: " + baseName, inner.x, ty);
            ty += 18;
            if (selectedShip != null) {
                String role = (selectedShip.role == null) ? "UNKNOWN" : shopRoleTitle(selectedShip.role);
                String faction = (selectedShip.faction == null) ? "Unknown" : selectedShip.faction.name().replace('_', ' ');
                g2.drawString("Role: " + role + "   Faction: " + faction, inner.x, ty);
                ty += 18;
            }
        } else {
            g2.drawString("Base: " + baseName, inner.x, ty);
            ty += 18;
        }

        // Resource readouts (with small pills)
        drawPill(g2, inner.x, ty - 12, 150, "CREDITS", String.valueOf(credits));
        drawPill(g2, inner.x + 160, ty - 12, 150, "BASE ORE", String.valueOf(baseOre));
        String focusLabel = "SYSTEMS";
        String focusValue = turretLv + " / 5";
        if (fleetHub && selectedShip != null) {
            ShipRole role = selectedShip.role;
            if (CampaignSystem.campaignShipUpgradeAvailable(selectedShip, 5)) {
                focusLabel = "HANGAR";
                focusValue = hangarLv + " / " + maxHangarTier;
            } else if (CampaignSystem.campaignShipUpgradeAvailable(selectedShip, 4)) {
                if (role == ShipRole.MINER) {
                    focusLabel = "MINING";
                } else if (role == ShipRole.HAULER || role == ShipRole.TRANSPORT || role == ShipRole.TRANSPORT_TITAN) {
                    focusLabel = "CARGO";
                } else {
                    focusLabel = "LOGISTICS";
                }
                focusValue = miningLv + " / 5";
            }
        }
        drawPill(g2, inner.x + 320, ty - 12, 160, focusLabel, focusValue);
        ty += 30;

        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString(fleetHub ? "Press the numbered slots shown below to upgrade the selected hull:" : "Press 1-5 to purchase:", inner.x, ty);
        ty += 18;

        // Costs mirror GamePanel (keep in sync)
        java.util.function.IntBinaryOperator cCost = (which, nextLv) -> switch (which) {
            case 1 -> 150 + 200 * nextLv;
            case 2 -> 170 + 210 * nextLv;
            case 3 -> 210 + 250 * nextLv;
            case 4 -> 140 + 170 * nextLv;
            case 5 -> 380 + 420 * nextLv;
            default -> 0;
        };
        java.util.function.IntBinaryOperator oCost = (which, nextLv) -> switch (which) {
            case 1 -> 40 + 70 * nextLv;
            case 2 -> 50 + 80 * nextLv;
            case 3 -> 60 + 90 * nextLv;
            case 4 -> 40 + 110 * nextLv;
            case 5 -> 100 + 170 * nextLv;
            default -> 0;
        };

        ty = drawUpgradeLineConsole(g2, x + 18, ty, 1, "Hull Fortification", hullLv, 5, new Color(120, 255, 170, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 2, "Shield Array",      shieldLv, 5, new Color(120, 200, 255, 220), cCost, oCost);
        if (!fleetHub || CampaignSystem.campaignShipUpgradeAvailable(selectedShip, 3)) {
            ty = drawUpgradeLineConsole(g2, x + 18, ty, 3, "Turret Systems", turretLv, 5, new Color(255, 210, 130, 220), cCost, oCost);
        }
        if (!fleetHub) {
            ty = drawUpgradeLineConsole(g2, x + 18, ty, 4, "Mining Ops",       miningLv, 5, new Color(255, 230, 120, 220), cCost, oCost);
            ty = drawUpgradeLineConsole(g2, x + 18, ty, 5, "Hangar Expansion", hangarLv, maxHangarTier, new Color(210, 170, 255, 220), cCost, oCost);
        } else {
            String logisticsTitle = CampaignSystem.campaignShipUpgradeTitle(selectedShip, 4);
            if (logisticsTitle != null) {
                ty = drawUpgradeLineConsole(g2, x + 18, ty, 4, logisticsTitle, miningLv, 5, new Color(255, 230, 120, 220), cCost, oCost);
            }
            String hangarTitle = CampaignSystem.campaignShipUpgradeTitle(selectedShip, 5);
            if (hangarTitle != null) {
                ty = drawUpgradeLineConsole(g2, x + 18, ty, 5, hangarTitle, hangarLv, maxHangarTier, new Color(210, 170, 255, 220), cCost, oCost);
            }
        }

        g2.setColor(new Color(255, 255, 255, 130));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString(fleetHub
                ? CampaignSystem.campaignShipUpgradeFooter(selectedShip) + " Launch with Enter when ready."
                : "Mining Ops boosts mining rate + ore sell value.", x + 18, y + h - 16);
    }

    private static void drawPill(Graphics2D g2, int x, int y, int w, String label, String value) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, 20, 12, 12);
        g2.setColor(new Color(90, 220, 255, 70));
        g2.drawRoundRect(x, y, w, 20, 12, 12);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(200, 240, 255, 210));
        g2.drawString(label, x + 8, y + 14);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 220));
        int vw = g2.getFontMetrics().stringWidth(value);
        g2.drawString(value, x + w - 8 - vw, y + 15);
    }

    private static int drawUpgradeLineConsole(Graphics2D g2, int x, int ty,
                                              int key, String name, int lv, int max, Color accent,
                                              java.util.function.IntBinaryOperator cCost,
                                              java.util.function.IntBinaryOperator oCost) {
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        // Key capsule
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, ty - 12, 22, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRoundRect(x, ty - 12, 22, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.drawString(String.valueOf(key), x + 7, ty + 2);

        int textX = x + 30;

        // Name
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 215));
        g2.drawString(name, textX, ty + 2);

        // Level bars
        int barX = x + 250;
        int barY = ty - 10;
        int barW = 10;
        int barH = 16;
        for (int i = 0; i < max; i++) {
            boolean on = i < lv;
            g2.setColor(on ? accent : new Color(255, 255, 255, 40));
            g2.fillRoundRect(barX + i * (barW + 4), barY, barW, barH, 6, 6);
        }

        // Cost / status
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        if (lv >= max) {
            g2.setColor(new Color(120, 255, 170, 210));
            g2.drawString("MAX", x + 250 + max * 14 + 12, ty + 2);
        } else {
            int next = lv + 1;
            int c = cCost.applyAsInt(key, next);
            int o = oCost.applyAsInt(key, next);
            g2.setColor(new Color(255, 255, 255, 190));
            g2.drawString(c + "c + " + o + " ore", x + 250 + max * 14 + 12, ty + 2);
        }

        // Divider line
        g2.setColor(new Color(255, 255, 255, 26));
        g2.drawLine(x, ty + 8, x + 480, ty + 8);
        return ty + 26;
    }

public static void drawMinimap(Graphics2D g2, List<Ship> ships, Player player, int viewW, int viewH, double waypointX, double waypointY, List<MapPing> pings) {
        if (ships == null || ships.isEmpty() || player == null) return;

        int pad = 14;
        int size = 170;
        int x0 = viewW - size - pad;
        int y0 = pad;
        int ringInset = 10;
        int ringX = x0 + ringInset;
        int ringY = y0 + ringInset;
        int ringSize = size - ringInset * 2;

        if (!paintThemedCircularHudFrame(g2, ringX, ringY, ringSize, new Color(120, 210, 255, 190), ThemeArt.HUD_RADAR_RING)) {
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRoundRect(x0, y0, size, size, 16, 16);
            g2.setColor(new Color(255, 255, 255, 80));
            g2.drawRoundRect(x0, y0, size, size, 16, 16);
        }

        Shape oldClip = g2.getClip();
        g2.setClip(new Ellipse2D.Double(ringX + 6, ringY + 6, ringSize - 12, ringSize - 12));

        double view = 1500;
        double left = player.x - view / 2.0;
        double top = player.y - view / 2.0;

        for (Ship s : ships) {
            if (!s.alive) continue;

            double rx = (s.x - left) / view;
            double ry = (s.y - top) / view;
            if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

            int px = x0 + (int) Math.round(rx * size);
            int py = y0 + (int) Math.round(ry * size);

            g2.setColor(factionMapColor(s.faction, (s == player), 220));

            int r = (s.role == ShipRole.BASE) ? 4 : 2;
            g2.fillOval(px - r, py - r, r * 2, r * 2);
        }

        // Waypoint marker (if inside minimap view)
        if (!Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
            double rx = (waypointX - left) / view;
            double ry = (waypointY - top) / view;
            if (rx >= 0 && rx <= 1 && ry >= 0 && ry <= 1) {
                int px = x0 + (int) Math.round(rx * size);
                int py = y0 + (int) Math.round(ry * size);
                g2.setColor(new Color(255, 255, 255, 210));
                g2.drawOval(px - 4, py - 4, 8, 8);
                g2.drawLine(px - 6, py, px - 2, py);
                g2.drawLine(px + 2, py, px + 6, py);
                g2.drawLine(px, py - 6, px, py - 2);
                g2.drawLine(px, py + 2, px, py + 6);
            }
        }

        // Pings (if inside minimap view)
        if (pings != null) {
            for (MapPing ping : pings) {
                if (ping == null || ping.t <= 0) continue;
                double rx = (ping.x - left) / view;
                double ry = (ping.y - top) / view;
                if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

                int px = x0 + (int) Math.round(rx * size);
                int py = y0 + (int) Math.round(ry * size);

                int a = MathUtil.clamp((int) Math.round(60 + 120 * Math.min(1, ping.t)), 0, 220);
                Color c = switch (ping.faction) {
                    case 2 -> new Color(255, 90, 90, a);
                    case 1 -> new Color(140, 180, 255, a);
                    case 3 -> new Color(255, 200, 90, a);
                    case 4 -> new Color(200, 140, 255, a);
                    default -> new Color(90, 255, 140, a);
                };
                g2.setColor(c);
                g2.drawOval(px - 5, py - 5, 10, 10);
            }
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawString("MINIMAP", x0 + 10, y0 + size - 10);
        g2.setClip(oldClip);
    }


    public static void drawStrategicMap(Graphics2D g2,
                                        GameContext ctx,
                                        int viewW, int viewH,
                                        int worldW, int worldH,
                                        double camX, double camY,
                                        double camViewW, double camViewH,
                                        Player player,
                                        List<Ship> ships,
                                        List<Asteroid> asteroids,
                                        List<Salvage> salvage,
                                        double waypointX, double waypointY,
                                        List<MapPing> pings,
                                        FogOfWarSystem.State fog,
                                        String bannerTopLine) {

        Rectangle r = getStrategicMapRect(viewW, viewH);
        boolean sectorized = BattlefieldSectorSystem.isEnabled(ctx);
        boolean galaxyMode = CampaignSystem.isStrategicGalaxyMapMode(ctx);
        List<BattlefieldSectorSystem.SectorSnapshot> sectorSnapshots = sectorized
                ? BattlefieldSectorSystem.snapshots(ctx)
                : List.of();
        BattlefieldSectorSystem.SectorDefinition currentSector = sectorized
                ? BattlefieldSectorSystem.currentSector(ctx)
                : null;
        BattlefieldSectorSystem.SectorDefinition loadedSector = sectorized
                ? BattlefieldSectorSystem.loadedSector(ctx)
                : null;
        BattlefieldSectorSystem.SectorDefinition selectedSector = sectorized
                ? BattlefieldSectorSystem.selectedSector(ctx)
                : null;

        // Backdrop + glow border
        if (galaxyMode) {
            g2.setColor(new Color(6, 12, 22, 76));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setColor(new Color(140, 200, 255, 42));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
        } else if (!paintThemedHudFrame(g2, r.x, r.y, r.width, r.height,
                new Color(140, 200, 255, 188), ThemeArt.HUD_SPECIAL_FRAME, 22)) {
            g2.setColor(new Color(0, 0, 0, 205));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);
            g2.setColor(new Color(140, 200, 255, 55));
            g2.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 24, 24);
            g2.setColor(new Color(255, 255, 255, 95));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        }

        Rectangle m = getStrategicMapInnerRect(viewW, viewH, galaxyMode);

        if (galaxyMode) {
            g2.setColor(new Color(255, 255, 255, 14));
            g2.fillRoundRect(m.x, m.y, m.width, m.height, 14, 14);
            g2.setColor(new Color(180, 224, 255, 46));
            g2.drawRoundRect(m.x, m.y, m.width, m.height, 14, 14);
        } else if (!paintThemedHudFrame(g2, m.x, m.y, m.width, m.height,
                new Color(124, 204, 255, 150), ThemeArt.HUD_STANDARD_PANEL, 16)) {
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillRoundRect(m.x, m.y, m.width, m.height, 16, 16);
            g2.setColor(new Color(255, 255, 255, 55));
            g2.drawRoundRect(m.x, m.y, m.width, m.height, 16, 16);
        }

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 22));
        int step = 80;
        for (int x = m.x + step; x < m.x + m.width; x += step) g2.drawLine(x, m.y, x, m.y + m.height);
        for (int y = m.y + step; y < m.y + m.height; y += step) g2.drawLine(m.x, y, m.x + m.width, y);
        if (galaxyMode) {
            g2.setColor(new Color(210, 230, 255, 10));
            for (int y = m.y + 2; y < m.y + m.height; y += 4) {
                g2.drawLine(m.x, y, m.x + m.width, y);
            }
            g2.setColor(new Color(180, 220, 255, 70));
            g2.drawString("THEATER SCALE 2000  3000  4000  5000KM", m.x + 18, m.y + m.height - 12);
        }

        // Title + help
        g2.setFont(new Font("Consolas", Font.BOLD, galaxyMode ? 18 : 16));
        g2.setColor(new Color(255, 255, 255, 225));
        g2.drawString(galaxyMode ? "GALACTIC ROUTE MAP" : "STRATEGIC MAP", r.x + 18, r.y + 28);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
                g2.drawString(sectorized
                        ? "LMB: route warp   MMB: recenter   RMB: sector ping   wheel/Ctrl+/-: zoom   1/2/3: compact/standard/expanded"
                        : (galaxyMode
                        ? "LMB: select destination or free course   Double-click/T: burn engines   TAB: fleet management   B: upgrades   H: hold   RMB: ping   wheel/Ctrl+/-: zoom"
                        : (CampaignSystem.usesMissionSubzones(ctx)
                        ? "LMB: set local course or select contact   Command Bay: visible actions   RMB: local ping   wheel/Ctrl+/-: zoom   tabs: mission/fleet/resources/contacts/strikes"
                        : "LMB: waypoint   MMB: recenter   RMB: ping   wheel/Ctrl+/-: zoom   Sensor power reveals anomalies")),
                r.x + 18, r.y + r.height - 16);

        String mapHeader = sectorized
                ? buildSectorMapHeader(loadedSector, currentSector, selectedSector,
                (ctx == null || ctx.ui == null) ? null : ctx.ui.tacticalSectorScalePreset)
                : bannerTopLine;
        if (mapHeader != null && !mapHeader.isBlank()) {
            g2.setColor(new Color(140, 200, 255, 200));
            g2.drawString(mapHeader, r.x + 190, r.y + 28);
        }

        if (galaxyMode) {
            drawGalaxyNavigationPanel(g2, ctx, getStrategicMapLeftPanelRect(viewW, viewH, true));
        }
        drawStrategicObjectivePanel(g2, ctx, getStrategicMapSidebarRect(viewW, viewH, galaxyMode));

        double mapZoom = UISystem.strategicMapZoom(ctx);
        double visibleWorldW = UISystem.strategicMapViewWidth(ctx);
        double visibleWorldH = UISystem.strategicMapViewHeight(ctx);
        double worldMinX = UISystem.strategicMapWorldMinX(ctx);
        double worldMinY = UISystem.strategicMapWorldMinY(ctx);
        double worldMaxX = worldMinX + visibleWorldW;
        double worldMaxY = worldMinY + visibleWorldH;

        // Helpers: world -> map
        java.util.function.BiFunction<Double, Double, Point> W2M = (wx, wy) -> {
            int px = m.x + (int) Math.round(((wx - worldMinX) / Math.max(1.0, visibleWorldW)) * m.width);
            int py = m.y + (int) Math.round(((wy - worldMinY) / Math.max(1.0, visibleWorldH)) * m.height);
            return new Point(px, py);
        };

        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(m.x, m.y, m.width, m.height);

        if (sectorized) {
            drawBattlefieldSectorsOnMap(g2, m, ctx, sectorSnapshots, currentSector, selectedSector,
                    worldMinX, worldMinY, visibleWorldW, visibleWorldH);
        }

        if (galaxyMode) {
            drawGalaxyBackdrop(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
            drawCampaignRouteNetwork(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
            drawCampaignTravelPath(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
        } else {
            if (asteroids != null) {
                g2.setColor(new Color(200, 200, 200, 80));
                for (Asteroid a : asteroids) {
                    if (a == null) continue;
                    if (a.x < worldMinX || a.x > worldMaxX || a.y < worldMinY || a.y > worldMaxY) continue;
                    Point p = W2M.apply(a.x, a.y);
                    g2.fillRect(p.x, p.y, 2, 2);
                }
            }
            if (salvage != null) {
                g2.setColor(new Color(255, 255, 255, 120));
                for (Salvage s : salvage) {
                    if (s == null || !s.alive()) continue;
                    if (s.x < worldMinX || s.x > worldMaxX || s.y < worldMinY || s.y > worldMaxY) continue;
                    Point p = W2M.apply(s.x, s.y);
                    g2.fillOval(p.x - 1, p.y - 1, 3, 3);
                }
            }
            if (ships != null) {
                for (Ship s : ships) {
                    if (s == null || !s.alive) continue;
                    if (s.x < worldMinX || s.x > worldMaxX || s.y < worldMinY || s.y > worldMaxY) continue;
                    Point p = W2M.apply(s.x, s.y);
                    Color c = factionMapColor(s.faction, (s == player), 200);
                    int rr = (s.role == ShipRole.BASE) ? 4 : 2;
                    g2.setColor(c);
                    g2.fillOval(p.x - rr, p.y - rr, rr * 2, rr * 2);
                }
            }
            if (fog != null) {
                drawStrategicFogOverlay(g2, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH, fog);
                drawSensorInterestSignals(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
            }
            if (!sectorized && CampaignSystem.usesMissionSubzones(ctx)) {
                drawLocalOperatingAreaOnMap(g2, m, ctx, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
            }
        }
        drawStrategicLandmarkMarkers(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
        drawStrategicSupportMarkers(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
        drawStrategicObjectiveMarkers(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);
        drawCampaignStrikeCinematic(g2, ctx, m, worldMinX, worldMinY, visibleWorldW, visibleWorldH);

        if (!galaxyMode && !Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
            Point wp = W2M.apply(waypointX, waypointY);
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawOval(wp.x - 6, wp.y - 6, 12, 12);
            g2.drawLine(wp.x - 10, wp.y, wp.x - 3, wp.y);
            g2.drawLine(wp.x + 3, wp.y, wp.x + 10, wp.y);
            g2.drawLine(wp.x, wp.y - 10, wp.x, wp.y - 3);
            g2.drawLine(wp.x, wp.y + 3, wp.x, wp.y + 10);
        }

        // Pings
        if (pings != null) {
            for (MapPing ping : pings) {
                if (ping == null || ping.t <= 0) continue;
                Point pp = W2M.apply(ping.x, ping.y);

                int a = MathUtil.clamp((int) Math.round(60 + 120 * Math.min(1, ping.t)), 0, 220);
                Color c = switch (ping.faction) {
                    case 2 -> new Color(255, 90, 90, a);
                    case 1 -> new Color(140, 180, 255, a);
                    case 3 -> new Color(255, 200, 90, a);
                    case 4 -> new Color(200, 140, 255, a);
                    default -> new Color(90, 255, 140, a);
                };

                g2.setColor(c);
                g2.drawOval(pp.x - 8, pp.y - 8, 16, 16);
                g2.drawOval(pp.x - 4, pp.y - 4, 8, 8);
            }
        }

        g2.setClip(oldClip);
        if (galaxyMode) {
            drawCampaignWarLegend(g2, ctx, m);
        }

        if (!galaxyMode) {
            double focusX = (ctx != null && ctx.ui != null && Double.isFinite(ctx.ui.strategicMapFocusX))
                    ? ctx.ui.strategicMapFocusX
                    : (camX + camViewW * 0.5);
            double focusY = (ctx != null && ctx.ui != null && Double.isFinite(ctx.ui.strategicMapFocusY))
                    ? ctx.ui.strategicMapFocusY
                    : (camY + camViewH * 0.5);
            double vx0 = focusX - camViewW * 0.5;
            double vy0 = focusY - camViewH * 0.5;
            double vx1 = focusX + camViewW * 0.5;
            double vy1 = focusY + camViewH * 0.5;

            Point p0 = W2M.apply(vx0, vy0);
            Point p1 = W2M.apply(vx1, vy1);

            int rx = Math.min(p0.x, p1.x);
            int ry = Math.min(p0.y, p1.y);
            int rw = Math.abs(p1.x - p0.x);
            int rh = Math.abs(p1.y - p0.y);

            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawRect(rx, ry, rw, rh);
        }
        g2.setColor(new Color(140, 200, 255, 176));
        g2.drawString(String.format(java.util.Locale.US, "MAP ZOOM %.2fx", mapZoom), r.x + r.width - 178, r.y + 28);
        if (galaxyMode) {
            g2.setColor(new Color(255, 224, 170, 190));
            g2.drawString("EARTH DIRECTION: NORTH", r.x + 240, r.y + 28);
        }
    }

    private static void drawGalaxyBackdrop(Graphics2D g2, GameContext ctx, Rectangle rect,
                                           double worldMinX, double worldMinY, double worldW, double worldH) {
        if (g2 == null || rect == null) return;
        Paint oldPaint = g2.getPaint();
        g2.setPaint(new GradientPaint(
                rect.x, rect.y, new Color(6, 12, 26, 235),
                rect.x + rect.width, rect.y + rect.height, new Color(12, 22, 38, 220)));
        g2.fillRect(rect.x, rect.y, rect.width, rect.height);
        drawGalaxyLatitudeBands(g2, ctx, rect, worldMinY, worldH);
        g2.setPaint(new RadialGradientPaint(
                new Point(rect.x + (int) (rect.width * 0.28), rect.y + (int) (rect.height * 0.38)),
                Math.max(120.0f, rect.width * 0.26f),
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(88, 136, 196, 54), new Color(88, 136, 196, 0)}));
        g2.fillOval(rect.x - rect.width / 8, rect.y - rect.height / 10, rect.width / 2, rect.height / 2);
        g2.setPaint(new RadialGradientPaint(
                new Point(rect.x + (int) (rect.width * 0.72), rect.y + (int) (rect.height * 0.58)),
                Math.max(120.0f, rect.width * 0.22f),
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(214, 132, 92, 48), new Color(214, 132, 92, 0)}));
        g2.fillOval(rect.x + rect.width / 2, rect.y + rect.height / 4, rect.width / 3, rect.height / 3);
        g2.setPaint(oldPaint);
    }

    private static void drawGalaxyLatitudeBands(Graphics2D g2, GameContext ctx, Rectangle rect,
                                                double worldMinY, double worldH) {
        if (g2 == null || rect == null || ctx == null) return;
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.0f));
        java.util.List<CampaignSystem.TheaterBand> bands = CampaignSystem.campaignTheaterBands(ctx);
        if (bands.isEmpty()) {
            g2.setStroke(oldStroke);
            return;
        }
        double mapHeight = Math.max(1.0, ctx.WORLD_H);
        for (CampaignSystem.TheaterBand band : bands) {
            if (band == null) continue;
            double topY = mapHeight * band.minYNorm;
            double bottomY = mapHeight * band.maxYNorm;
            int pyTop = strategicMapPixelY(rect, worldMinY, worldH, topY);
            int pyBottom = strategicMapPixelY(rect, worldMinY, worldH, bottomY);
            int y0 = Math.min(pyTop, pyBottom);
            int y1 = Math.max(pyTop, pyBottom);
            Color tint = theaterBandColor(band.controlToken, 24);
            g2.setColor(tint);
            g2.fillRect(rect.x, MathUtil.clamp(y0, rect.y, rect.y + rect.height), rect.width,
                    Math.max(1, Math.min(rect.y + rect.height, y1) - Math.max(rect.y, y0)));
            g2.setColor(theaterBandColor(band.controlToken, 74));
            g2.drawLine(rect.x, pyTop, rect.x + rect.width, pyTop);
            g2.drawLine(rect.x, pyBottom, rect.x + rect.width, pyBottom);
            int labelY = MathUtil.clamp((y0 + y1) / 2, rect.y + 18, rect.y + rect.height - 14);
            g2.setColor(theaterBandColor(band.controlToken, 188));
            g2.drawString(band.label.toUpperCase(Locale.US), rect.x + 12, labelY);
        }
        int northX = rect.x + rect.width - 28;
        int northY = rect.y + 18;
        g2.setColor(new Color(255, 225, 170, 200));
        g2.drawLine(northX, northY + 20, northX, northY - 6);
        g2.drawLine(northX, northY - 6, northX - 5, northY + 2);
        g2.drawLine(northX, northY - 6, northX + 5, northY + 2);
        g2.drawString("N", northX - 4, northY + 34);
        g2.setStroke(oldStroke);
    }

    private static Color theaterBandColor(String controlToken, int alpha) {
        String token = (controlToken == null) ? "" : controlToken.trim().toUpperCase(Locale.US);
        if (token.startsWith("BG")) return new Color(120, 236, 188, MathUtil.clamp(alpha, 0, 255));
        if (token.startsWith("R")) return new Color(255, 170, 150, MathUtil.clamp(alpha, 0, 255));
        return new Color(196, 210, 236, MathUtil.clamp(alpha, 0, 255));
    }

    private static void drawCampaignTheaterBands(Graphics2D g2, GameContext ctx, Rectangle rect,
                                                 double worldMinY, double worldH) {
        drawGalaxyLatitudeBands(g2, ctx, rect, worldMinY, worldH);
    }

    private static void drawCampaignWarLegend(Graphics2D g2, GameContext ctx, Rectangle mapRect) {
        if (g2 == null || mapRect == null) return;
        int x = mapRect.x + 10;
        int y = mapRect.y + mapRect.height - 56;
        g2.setColor(new Color(8, 12, 20, 168));
        g2.fillRoundRect(x - 6, y - 16, 330, 48, 12, 12);
        g2.setColor(new Color(180, 214, 248, 78));
        g2.drawRoundRect(x - 6, y - 16, 330, 48, 12, 12);
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(228, 236, 244, 220));
        g2.drawString("LEGEND", x, y - 4);
        drawHudStatusChip(g2, "BG CONTROLLED", x, y + 8, 102, 16, new Color(120, 236, 188, 210), false);
        drawHudStatusChip(g2, "CONTESTED", x + 108, y + 8, 82, 16, new Color(210, 220, 238, 210), false);
        drawHudStatusChip(g2, "R/Y CONTROLLED", x + 194, y + 8, 120, 16, new Color(255, 170, 150, 210), false);
        if (CampaignSystem.isCampaignWarMapSimplified(ctx)) {
            g2.setColor(new Color(255, 226, 176, 210));
            g2.drawString("SIMPLIFIED MODE ACTIVE", x + 140, y - 4);
        }
    }

    private static void drawCampaignRouteNetwork(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                                 double worldMinX, double worldMinY, double worldW, double worldH) {
        if (g2 == null || ctx == null || mapRect == null) return;
        List<CampaignSystem.CampaignLocation> pois = CampaignSystem.mainCampaignLocations(ctx);
        if (pois.size() < 2) return;
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(new Color(110, 170, 220, 84));
        for (int i = 1; i < pois.size(); i++) {
            CampaignSystem.CampaignLocation a = pois.get(i - 1);
            CampaignSystem.CampaignLocation b = pois.get(i);
            if (a == null || b == null) continue;
            int ax = strategicMapPixelX(mapRect, worldMinX, worldW, a.x);
            int ay = strategicMapPixelY(mapRect, worldMinY, worldH, a.y);
            int bx = strategicMapPixelX(mapRect, worldMinX, worldW, b.x);
            int by = strategicMapPixelY(mapRect, worldMinY, worldH, b.y);
            g2.drawLine(ax, ay, bx, by);
        }
        g2.setStroke(oldStroke);
    }

    private static void drawCampaignTravelPath(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                               double worldMinX, double worldMinY, double worldW, double worldH) {
        if (g2 == null || ctx == null || mapRect == null) return;
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        double freeTargetX = CampaignSystem.selectedFreeTravelTargetX(ctx);
        double freeTargetY = CampaignSystem.selectedFreeTravelTargetY(ctx);
        double playerX = CampaignSystem.playerGalaxyX(ctx);
        double playerY = CampaignSystem.playerGalaxyY(ctx);
        if (!Double.isFinite(playerX) || !Double.isFinite(playerY)) return;
        int ax = strategicMapPixelX(mapRect, worldMinX, worldW, playerX);
        int ay = strategicMapPixelY(mapRect, worldMinY, worldH, playerY);
        if (selected != null || (Double.isFinite(freeTargetX) && Double.isFinite(freeTargetY))) {
            double targetX = (selected != null) ? selected.x : freeTargetX;
            double targetY = (selected != null) ? selected.y : freeTargetY;
            int bx = strategicMapPixelX(mapRect, worldMinX, worldW, targetX);
            int by = strategicMapPixelY(mapRect, worldMinY, worldH, targetY);
            Stroke oldStroke = g2.getStroke();
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{10.0f, 10.0f}, 0.0f));
            g2.setColor(new Color(255, 214, 132, 170));
            g2.drawLine(ax, ay, bx, by);
            g2.setStroke(oldStroke);
            g2.setColor(new Color(255, 232, 178, 120));
            g2.drawOval(bx - 10, by - 10, 20, 20);
            g2.setColor(new Color(255, 210, 120, 210));
            g2.drawOval(bx - 15, by - 15, 30, 30);
        }
        CampaignSystem.CampaignTravelState travel = CampaignSystem.campaignTravelState(ctx);
        double headingRad = Math.toRadians(CampaignSystem.playerGalaxyHeadingDeg(ctx));
        int noseX = ax + (int) Math.round(Math.cos(headingRad) * 9.0);
        int noseY = ay + (int) Math.round(Math.sin(headingRad) * 9.0);
        g2.setColor(new Color(120, 220, 255, 90));
        g2.fillOval(ax - 12, ay - 12, 24, 24);
        g2.setColor(new Color(255, 244, 210, 220));
        g2.fillOval(ax - 5, ay - 5, 10, 10);
        g2.drawLine(ax, ay, noseX, noseY);
        if (travel != null && travel.traveling) {
            g2.setColor(new Color(255, 214, 132, 170));
            g2.drawOval(ax - 9, ay - 9, 18, 18);
        } else {
            g2.setColor(new Color(120, 236, 188, 160));
            g2.drawOval(ax - 8, ay - 8, 16, 16);
        }
    }

    private static int strategicMapPixelX(Rectangle mapRect, double worldMinX, double worldW, double worldX) {
        return mapRect.x + (int) Math.round(((worldX - worldMinX) / Math.max(1.0, worldW)) * mapRect.width);
    }

    private static int strategicMapPixelY(Rectangle mapRect, double worldMinY, double worldH, double worldY) {
        return mapRect.y + (int) Math.round(((worldY - worldMinY) / Math.max(1.0, worldH)) * mapRect.height);
    }

    private static void drawStrategicObjectivePanel(Graphics2D g2, GameContext ctx, Rectangle panelRect) {
        if (g2 == null || ctx == null || panelRect == null) return;
        if (CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            drawGalaxySidebar(g2, ctx, panelRect);
            return;
        }
        drawTacticalMissionSidebar(g2, ctx, panelRect);
    }

    private static void drawTacticalMissionSidebar(Graphics2D g2, GameContext ctx, Rectangle panelRect) {
        if (g2 == null || ctx == null || panelRect == null) return;
        paintGalaxyConsolePanel(g2, panelRect, new Color(255, 196, 118, 164));
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Rectangle[] tabRects = tacticalMapTabRects(inner.x, inner.y + 2, inner.width);
        UiState.TacticalMapTab activeTab = (ctx.ui == null) ? UiState.TacticalMapTab.MISSION : ctx.ui.tacticalMapTab;
        UiState.TacticalMapTab[] tabs = UiState.TacticalMapTab.values();
        for (int i = 0; i < tabs.length && i < tabRects.length; i++) {
            boolean selected = tabs[i] == activeTab;
            Color accent = selected ? new Color(255, 204, 124, 224) : new Color(178, 214, 238, 172);
            drawHudStatusChip(g2, tabs[i].label().toUpperCase(Locale.US), tabRects[i].x, tabRects[i].y + 14, tabRects[i].width, 20, accent, selected);
        }

        Rectangle contentRect = tacticalMapContentRect(inner);
        Rectangle actionRect = tacticalMapActionBayRect(inner);
        drawGalaxyContentWell(g2, contentRect.x, contentRect.y, contentRect.width, contentRect.height,
                new Color(7, 10, 16, 188), new Color(160, 212, 244, 54));
        drawGalaxyContentWell(g2, actionRect.x, actionRect.y, actionRect.width, actionRect.height,
                new Color(7, 10, 16, 198), new Color(255, 196, 118, 56));

        int y = contentRect.y + 18;
        List<String> titleLines = tacticalSidebarTitleLines(ctx);
        y = drawTacticalTitleBlock(g2, contentRect.x + 10, y, contentRect.width - 20, titleLines,
                new Color(255, 220, 160, 236));
        List<TacticalSectionBlock> blocks = tacticalSidebarBlocks(ctx);
        for (TacticalSectionBlock block : blocks) {
            if (block == null || block.lines.isEmpty() || y >= contentRect.y + contentRect.height - 30) continue;
            y = drawTacticalSidebarSection(g2, contentRect.x + 10, y, contentRect.width - 20,
                    block.header, block.lines, block.accent, contentRect.y + contentRect.height - 18);
        }

        drawTacticalActionBay(g2, ctx, actionRect);
    }

    private static final class TacticalSectionBlock {
        final String header;
        final List<String> lines;
        final Color accent;

        TacticalSectionBlock(String header, List<String> lines, Color accent) {
            this.header = header;
            this.lines = lines;
            this.accent = accent;
        }
    }

    private static Rectangle tacticalMapContentRect(Rectangle inner) {
        int top = inner.y + 40;
        int h = Math.max(180, (int) Math.round(inner.height * 0.52));
        return new Rectangle(inner.x, top, inner.width, Math.min(h, inner.y + inner.height - top - 156));
    }

    private static Rectangle tacticalMapActionBayRect(Rectangle inner) {
        Rectangle content = tacticalMapContentRect(inner);
        int y = content.y + content.height + 10;
        return new Rectangle(inner.x, y, inner.width, inner.y + inner.height - y);
    }

    private static Rectangle[] tacticalMapTabRects(int x, int y, int width) {
        UiState.TacticalMapTab[] tabs = UiState.TacticalMapTab.values();
        Rectangle[] rects = new Rectangle[tabs.length];
        int gap = 8;
        int tabW = Math.max(58, (width - gap * (tabs.length - 1)) / tabs.length);
        int cursor = x;
        for (int i = 0; i < tabs.length; i++) {
            if (i == tabs.length - 1) {
                rects[i] = new Rectangle(cursor, y, Math.max(tabW, x + width - cursor), 26);
            } else {
                rects[i] = new Rectangle(cursor, y, tabW, 26);
            }
            cursor += tabW + gap;
        }
        return rects;
    }

    private static List<String> tacticalSidebarTitleLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null || ctx.ui == null) return out;
        boolean selectedObject = ctx.ui.tacticalMapSelectionKind != UiState.TacticalMapSelectionKind.MISSION
                && ctx.ui.tacticalMapSelectionLabel != null
                && !ctx.ui.tacticalMapSelectionLabel.isBlank();
        if (selectedObject) {
            out.add("SELECTED " + ctx.ui.tacticalMapSelectionKind.name());
            out.add(ctx.ui.tacticalMapSelectionLabel.toUpperCase(Locale.US));
            return out;
        }
        out.add("SELECTED MISSION");
        String title = CampaignSystem.hudObjectiveTitle(ctx);
        if (title == null || title.isBlank()) title = "MISSION CONTACT";
        out.add(title.toUpperCase(Locale.US));
        return out;
    }

    private static List<TacticalSectionBlock> tacticalSidebarBlocks(GameContext ctx) {
        ArrayList<TacticalSectionBlock> out = new ArrayList<>();
        if (ctx == null) return out;
        UiState.TacticalMapTab tab = (ctx.ui == null) ? UiState.TacticalMapTab.MISSION : ctx.ui.tacticalMapTab;
        switch (tab) {
            case MISSION -> {
                out.add(new TacticalSectionBlock("MISSION", tacticalMissionSummaryLines(ctx), new Color(255, 198, 126, 220)));
                out.add(new TacticalSectionBlock("LANDMARKS", sampleLines(buildStrategicLandmarkLines(CampaignSystem.strategicLandmarks(ctx)), 4), new Color(168, 220, 255, 210)));
                out.add(new TacticalSectionBlock("ACTIVE MARKERS", sampleLines(buildStrategicObjectiveMarkerLines(CampaignSystem.activeObjectiveMarkers(ctx)), 5), new Color(255, 214, 132, 220)));
            }
            case FLEET -> {
                out.add(new TacticalSectionBlock("FLAGSHIP STATUS", tacticalFlagshipStatusLines(ctx), new Color(168, 220, 255, 220)));
                out.add(new TacticalSectionBlock("DIVISIONS", sampleLines(CampaignSystem.strategicDivisionSummaryLines(ctx), 5), new Color(188, 228, 255, 220)));
            }
            case RESOURCES -> {
                out.add(new TacticalSectionBlock("RESOURCE BOARD", tacticalResourceBoardLines(ctx), new Color(156, 228, 178, 214)));
                out.add(new TacticalSectionBlock("READINESS", tacticalReadinessLines(ctx), new Color(255, 208, 142, 214)));
            }
            case CONTACTS -> {
                out.add(new TacticalSectionBlock("SELECTED CONTACT", tacticalContactSummaryLines(ctx), tacticalSelectionAccent(ctx)));
                out.add(new TacticalSectionBlock("TASK FORCES", sampleLines(CampaignSystem.strategicTaskForceSummaryLines(ctx), 4), new Color(255, 174, 146, 220)));
                out.add(new TacticalSectionBlock("SUPPORT CONTACTS", sampleLines(buildStrategicSupportLines(buildStrategicSupportEntryList(ctx)), 4), new Color(156, 224, 255, 214)));
            }
            case STRIKES -> {
                out.add(new TacticalSectionBlock("TARGET WINDOW", tacticalStrikeSummaryLines(ctx), new Color(255, 174, 146, 220)));
                out.add(new TacticalSectionBlock("READINESS", tacticalStrikeReadinessLines(ctx), new Color(255, 208, 142, 214)));
            }
        }
        return out;
    }

    private static List<String> tacticalMissionSummaryLines(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        if (ctx.ui.tacticalMapSelectionKind != UiState.TacticalMapSelectionKind.MISSION
                && ctx.ui.tacticalMapSelectionLabel != null
                && !ctx.ui.tacticalMapSelectionLabel.isBlank()) {
            out.add("Type: " + ctx.ui.tacticalMapSelectionKind.name());
            if (ctx.ui.tacticalMapSelectionSubtitle != null && !ctx.ui.tacticalMapSelectionSubtitle.isBlank()) {
                out.add("Role: " + ctx.ui.tacticalMapSelectionSubtitle);
            }
            out.add("Threat: " + (ctx.ui.tacticalMapSelectionHostile ? "Hostile contact" : "Mission contact"));
            if (ctx.ui.tacticalMapSelectionDetail != null && !ctx.ui.tacticalMapSelectionDetail.isBlank()) {
                out.add("Intel: " + ctx.ui.tacticalMapSelectionDetail);
            }
            if (ctx.player != null && Double.isFinite(ctx.ui.tacticalMapSelectionX) && Double.isFinite(ctx.ui.tacticalMapSelectionY)) {
                int distance = (int) Math.round(Math.hypot(ctx.ui.tacticalMapSelectionX - ctx.player.x, ctx.ui.tacticalMapSelectionY - ctx.player.y));
                out.add("Distance: " + distance + "m");
            }
            return out;
        }
        String detail = CampaignSystem.hudObjectiveExpandedDetail(ctx);
        out.add("Type: " + defaultIfBlank(objectiveValue(detail, "Theater:"), "Local campaign operation"));
        out.add("Primary Objective: " + defaultIfBlank(objectiveValue(detail, "Main Objective:"), "Advance the objective"));
        String secondary = objectiveValue(detail, "Optional:");
        if (secondary.isBlank()) secondary = objectiveValue(detail, "Current Task:");
        out.add("Secondary Objective: " + defaultIfBlank(secondary, "Stabilize the district"));
        out.add("Win State: " + defaultIfBlank(objectiveValue(detail, "Win State:"), "Complete the operation"));
        out.add("Failure Risk: " + defaultIfBlank(objectiveValue(detail, "Failure Risk:"), "Do not lose mission-critical assets"));
        String time = objectiveValue(detail, "Time:");
        if (time.isBlank()) time = objectiveValue(detail, "Timer:");
        if (time.isBlank() && ctx.campaign != null) {
            int left = (int) Math.ceil(Math.max(0.0, ctx.campaign.sectorTimeLimit - ctx.campaign.sectorElapsed));
            time = left + " seconds remaining";
        }
        out.add("Time Limit: " + defaultIfBlank(time, "Open engagement"));
        out.add("Threat: " + defaultIfBlank(objectiveValue(detail, "Threat:"), "Hostile task forces active"));
        return out;
    }

    private static List<String> tacticalFlagshipStatusLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null || ctx.player == null) return out;
        out.add("Hull: " + (int) Math.round(ctx.player.hp) + " / " + (int) Math.round(ctx.player.hpMax));
        out.add("Shield: " + (int) Math.round(ctx.player.shield) + " / " + (int) Math.round(ctx.player.shieldMax));
        out.add("Status: " + (ctx.player.getOverShieldRemaining() > 0.0 ? "Shield Overcharge" : "Combat Ready"));
        out.add("Division: " + selectedDivisionReadout(ctx));
        out.add("Fleet Strain: " + CampaignSystem.campaignFleetStrainReadout(ctx));
        out.add("Posture: " + CampaignSystem.campaignFleetPostureReadout(ctx));
        return out;
    }

    private static List<String> tacticalResourceBoardLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null) return out;
        out.add("Credits: " + ctx.credits);
        out.add("Fuel: " + CampaignSystem.campaignFuel(ctx));
        out.add("Supplies: " + CampaignSystem.campaignSupplies(ctx));
        out.add("Ammo: " + CampaignSystem.campaignAmmo(ctx));
        out.add("Salvage: " + CampaignSystem.campaignSalvageStock(ctx));
        out.add("Intel: " + (int) Math.round(CampaignSystem.campaignIntelPercent(ctx) * 100.0) + "%");
        out.add("Exposure: " + (int) Math.round(CampaignSystem.campaignExposurePercent(ctx) * 100.0) + "%");
        return out;
    }

    private static List<String> tacticalReadinessLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null || ctx.campaign == null) return out;
        out.add("Torpedoes: " + Math.max(0, ctx.campaign.strategicTorpedoCharges));
        out.add("Sorties Committed: " + Math.max(0, ctx.campaign.strategicSortiesLaunched));
        out.add("Atomic: " + Math.max(0, ctx.campaign.strategicAtomicCharges));
        out.add("Intel State: " + (int) Math.round(CampaignSystem.campaignIntelPercent(ctx) * 100.0) + "% workable");
        out.add("Exposure: " + (int) Math.round(CampaignSystem.campaignExposurePercent(ctx) * 100.0) + "%");
        return out;
    }

    private static List<String> tacticalContactSummaryLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null || ctx.ui == null) return out;
        if (ctx.ui.tacticalMapSelectionLabel == null || ctx.ui.tacticalMapSelectionLabel.isBlank()) {
            out.add("No map contact selected.");
            out.add("Select a marker, contact, or landmark for a focused readout.");
            return out;
        }
        out.add("Type: " + ctx.ui.tacticalMapSelectionKind.name());
        out.add("Label: " + ctx.ui.tacticalMapSelectionLabel);
        if (ctx.ui.tacticalMapSelectionSubtitle != null && !ctx.ui.tacticalMapSelectionSubtitle.isBlank()) {
            out.add("Role: " + ctx.ui.tacticalMapSelectionSubtitle);
        }
        out.add("Threat: " + (ctx.ui.tacticalMapSelectionHostile ? "Hostile" : "Non-hostile / objective"));
        if (ctx.ui.tacticalMapSelectionDetail != null && !ctx.ui.tacticalMapSelectionDetail.isBlank()) {
            out.add("Intel: " + ctx.ui.tacticalMapSelectionDetail);
        }
        return out;
    }

    private static List<String> tacticalStrikeSummaryLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null) return out;
        if (ctx.ui != null
                && ctx.ui.tacticalMapSelectionHostile
                && ctx.ui.tacticalMapSelectionLabel != null
                && !ctx.ui.tacticalMapSelectionLabel.isBlank()) {
            out.add("Target: " + ctx.ui.tacticalMapSelectionLabel);
            out.add("Intel: " + defaultIfBlank(ctx.ui.tacticalMapSelectionDetail, "Tracked"));
            out.add("Threat: Hostile tactical strike contact");
        } else if (CampaignSystem.hasSelectedCampaignContactTarget(ctx)) {
            out.add("Target: " + CampaignSystem.selectedCampaignContactLabel(ctx));
            out.add("Intel: " + defaultIfBlank(CampaignSystem.selectedCampaignContactIntelLabel(ctx), "Track weak"));
            out.add("Threat: " + (CampaignSystem.selectedCampaignContactHostile(ctx) ? "Hostile strike candidate" : "Contact not strike-eligible"));
        } else {
            out.add("Target: No hostile contact selected.");
            out.add("Select a hostile map contact to open strike windows.");
        }
        return out;
    }

    private static List<String> tacticalStrikeReadinessLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        if (ctx == null || ctx.campaign == null) return out;
        out.add("Torpedo Ready: " + Math.max(0, ctx.campaign.strategicTorpedoCharges));
        out.add("Sorties Committed: " + Math.max(0, ctx.campaign.strategicSortiesLaunched));
        out.add("Atomic Ready: " + Math.max(0, ctx.campaign.strategicAtomicCharges));
        out.add("Ammo/Fuel: " + CampaignSystem.campaignAmmo(ctx) + " / " + CampaignSystem.campaignFuel(ctx));
        out.add("Strike Heat: " + defaultIfBlank(CampaignSystem.lastStrikeReportTitle(ctx), "Cold"));
        return out;
    }

    private static int drawTacticalTitleBlock(Graphics2D g2, int x, int y, int width, List<String> lines, Color accent) {
        if (g2 == null || lines == null || lines.isEmpty()) return y;
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(208, 232, 248, 210));
        g2.drawString(lines.get(0), x, y);
        y += 18;
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(accent == null ? new Color(255, 216, 148, 230) : accent);
        for (int i = 1; i < lines.size(); i++) {
            for (String line : wrapHudText(g2.getFontMetrics(), lines.get(i), width)) {
                g2.drawString(line, x, y);
                y += 18;
            }
        }
        g2.setColor(new Color(176, 216, 242, 120));
        g2.drawLine(x, y + 2, x + width, y + 2);
        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return y + 14;
    }

    private static int drawTacticalSidebarSection(Graphics2D g2, int x, int y, int width, String header,
                                                  List<String> lines, Color accent, int maxY) {
        if (g2 == null || lines == null || lines.isEmpty() || y >= maxY) return y;
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(accent == null ? new Color(180, 220, 255, 214) : accent);
        g2.drawString(header, x, y);
        y += 12;
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(228, 236, 248, 216));
        for (String line : lines) {
            for (String wrapped : wrapHudText(g2.getFontMetrics(), line, width)) {
                if (y >= maxY) break;
                g2.drawString(wrapped, x, y);
                y += 14;
            }
            if (y >= maxY) break;
        }
        g2.setColor(new Color(176, 216, 242, 96));
        g2.drawLine(x, y + 1, x + width, y + 1);
        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return y + 12;
    }

    private static void drawTacticalActionBay(Graphics2D g2, GameContext ctx, Rectangle area) {
        if (g2 == null || ctx == null || area == null) return;
        List<CampaignSystem.CampaignAction> actions = CampaignSystem.tacticalMapVisibleActions(ctx);
        CampaignSystem.CampaignAction primary = tacticalPrimaryAction(actions);
        int x = area.x + 10;
        int y = area.y + 18;
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(255, 214, 132, 220));
        g2.drawString("COMMAND ACTION BAY", x, y);
        y += 10;
        g2.setColor(new Color(176, 216, 242, 96));
        g2.drawLine(x, y, x + area.width - 20, y);
        y += 12;

        if (primary != null) {
            drawTacticalActionButton(g2, primary, new Rectangle(x, y, area.width - 20, 40), true);
            y += 48;
        }
        for (CampaignActionSection section : tacticalActionSections(actions, primary)) {
            if (y >= area.y + area.height - 26) break;
            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            g2.setColor(section.category == CampaignSystem.CampaignActionCategory.STRIKES
                    ? new Color(255, 180, 154, 212)
                    : (section.category == CampaignSystem.CampaignActionCategory.POSTURE
                    ? new Color(188, 228, 255, 212)
                    : new Color(176, 220, 255, 212)));
            g2.drawString(section.label, x, y);
            y += 8;
            for (CampaignActionLayoutEntry entry : tacticalSectionActionLayout(section, x, y, area.width - 20)) {
                drawTacticalActionButton(g2, entry.action, entry.rect, false);
            }
            y += tacticalSectionBlockHeight(section, area.width - 20);
        }
    }

    private static CampaignSystem.CampaignAction tacticalPrimaryAction(List<CampaignSystem.CampaignAction> actions) {
        if (actions == null || actions.isEmpty()) return null;
        for (CampaignSystem.CampaignAction action : actions) {
            if (action != null && action.primary) return action;
        }
        for (CampaignSystem.CampaignAction action : actions) {
            if (action != null && action.enabled) return action;
        }
        return actions.get(0);
    }

    private static List<CampaignActionSection> tacticalActionSections(List<CampaignSystem.CampaignAction> actions,
                                                                      CampaignSystem.CampaignAction primary) {
        ArrayList<CampaignActionSection> sections = new ArrayList<>();
        if (actions == null) return sections;
        Map<CampaignSystem.CampaignActionCategory, List<CampaignSystem.CampaignAction>> grouped = new LinkedHashMap<>();
        for (CampaignSystem.CampaignAction action : actions) {
            if (action == null || action == primary) continue;
            grouped.computeIfAbsent(action.category, ignored -> new ArrayList<>()).add(action);
        }
        for (Map.Entry<CampaignSystem.CampaignActionCategory, List<CampaignSystem.CampaignAction>> entry : grouped.entrySet()) {
            List<CampaignSystem.CampaignAction> values = entry.getValue();
            if (values == null || values.isEmpty()) continue;
            sections.add(new CampaignActionSection(entry.getKey(), tacticalSectionLabel(entry.getKey()), sampleActions(values, 4)));
        }
        return sections;
    }

    private static String tacticalSectionLabel(CampaignSystem.CampaignActionCategory category) {
        if (category == null) return "COMMANDS";
        return switch (category) {
            case NAVIGATION -> "NAVIGATION";
            case POSTURE -> "DIVISION ORDERS";
            case SENSORS -> "MISSION";
            case STRIKES -> "STRIKES";
            case SUPPORT -> "SUPPORT";
            default -> "COMMANDS";
        };
    }

    private static int tacticalSectionBlockHeight(CampaignActionSection section, int width) {
        if (section == null || section.actions.isEmpty()) return 0;
        int rows = (section.actions.size() + 1) / 2;
        return 14 + rows * 38 + 8;
    }

    private static List<CampaignActionLayoutEntry> tacticalSectionActionLayout(CampaignActionSection section, int x, int y, int width) {
        ArrayList<CampaignActionLayoutEntry> entries = new ArrayList<>();
        if (section == null || section.actions.isEmpty()) return entries;
        int gap = 8;
        int buttonW = (width - gap) / 2;
        for (int i = 0; i < section.actions.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            Rectangle rect = new Rectangle(x + col * (buttonW + gap), y + row * 38 + 8, buttonW, 30);
            entries.add(new CampaignActionLayoutEntry(section.actions.get(i), rect));
        }
        return entries;
    }

    private static void drawTacticalActionButton(Graphics2D g2, CampaignSystem.CampaignAction action, Rectangle rect, boolean primary) {
        if (g2 == null || action == null || rect == null) return;
        BufferedImage strikeButton = strikeActionButtonImage(action);
        if (strikeButton != null) {
            Composite oldComposite = g2.getComposite();
            float alpha = action.enabled ? 1.0f : 0.54f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.drawImage(strikeButton, rect.x, rect.y, rect.width, rect.height, null);
            g2.setComposite(oldComposite);
            if (!action.enabled) {
                g2.setColor(new Color(12, 18, 28, 120));
                g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
            }
            g2.setColor(withAlpha(new Color(178, 220, 255, 220), action.enabled ? 218 : 110));
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
            return;
        }
        Color accent = tacticalActionAccent(action);
        int fillAlpha = action.enabled ? (primary ? 86 : 64) : 34;
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fillAlpha));
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        g2.setColor(withAlpha(accent, action.enabled ? 212 : 110));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, primary ? 12 : 11));
        g2.setColor(new Color(244, 248, 255, action.enabled ? 228 : 144));
        g2.drawString(action.label, rect.x + 8, rect.y + 12);
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        String detail = !action.enabled && action.disabledReason != null && !action.disabledReason.isBlank()
                ? "Disabled: " + action.disabledReason
                : defaultIfBlank(action.shortDescription, action.tooltip);
        List<String> lines = wrapHudText(g2.getFontMetrics(), detail, rect.width - 12);
        if (!lines.isEmpty()) {
            g2.setColor(new Color(220, 232, 244, action.enabled ? 210 : 126));
            g2.drawString(lines.get(0), rect.x + 8, rect.y + 24);
        }
        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static Color tacticalActionAccent(CampaignSystem.CampaignAction action) {
        if (action == null) return new Color(180, 220, 255, 210);
        if (!action.enabled) return new Color(142, 152, 168, 188);
        return switch (action.state) {
            case RECOMMENDED -> new Color(120, 236, 188, 214);
            case WARNING -> new Color(255, 188, 132, 220);
            case DISABLED -> new Color(142, 152, 168, 188);
            default -> action.category == CampaignSystem.CampaignActionCategory.STRIKES
                    ? new Color(255, 180, 148, 214)
                    : new Color(168, 224, 255, 214);
        };
    }

    public static CampaignHubClickTarget tacticalMapClickTargetAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null || CampaignSystem.isStrategicGalaxyMapMode(ctx)) return null;
        if (ctx.ui.campaignActionConfirm.active) {
            Rectangle overlay = campaignActionConfirmOverlayRect(viewW, viewH);
            Rectangle closeRect = new Rectangle(overlay.x + overlay.width - 92, overlay.y + overlay.height - 38, 78, 22);
            Rectangle confirmRect = new Rectangle(overlay.x + 18, overlay.y + overlay.height - 38, 122, 22);
            if (confirmRect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.CONFIRM, ctx.ui.campaignActionConfirm.actionId);
            }
            if (closeRect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.CLOSE, "");
            }
            return null;
        }
        Rectangle panelRect = getStrategicMapSidebarRect(viewW, viewH, false);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Rectangle[] tabRects = tacticalMapTabRects(inner.x, inner.y + 2, inner.width);
        UiState.TacticalMapTab[] tabs = UiState.TacticalMapTab.values();
        for (int i = 0; i < tabs.length && i < tabRects.length; i++) {
            if (tabRects[i].contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.TAB, "", tabs[i].name());
            }
        }
        for (CampaignActionLayoutEntry entry : tacticalActionEntries(ctx, panelRect)) {
            if (entry.rect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.ACTION, "", entry.action.id);
            }
        }
        return null;
    }

    private static List<CampaignActionLayoutEntry> tacticalActionEntries(GameContext ctx, Rectangle panelRect) {
        ArrayList<CampaignActionLayoutEntry> entries = new ArrayList<>();
        if (ctx == null || panelRect == null) return entries;
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Rectangle area = tacticalMapActionBayRect(inner);
        List<CampaignSystem.CampaignAction> actions = CampaignSystem.tacticalMapVisibleActions(ctx);
        CampaignSystem.CampaignAction primary = tacticalPrimaryAction(actions);
        int x = area.x + 10;
        int y = area.y + 18 + 10 + 12;
        if (primary != null) {
            entries.add(new CampaignActionLayoutEntry(primary, new Rectangle(x, y, area.width - 20, 40)));
            y += 48;
        }
        for (CampaignActionSection section : tacticalActionSections(actions, primary)) {
            y += 8;
            for (CampaignActionLayoutEntry entry : tacticalSectionActionLayout(section, x, y, area.width - 20)) {
                entries.add(entry);
            }
            y += tacticalSectionBlockHeight(section, area.width - 20);
        }
        return entries;
    }

    private static Rectangle tacticalActionRect(GameContext ctx, Rectangle panelRect, String actionId) {
        if (actionId == null || actionId.isBlank()) return null;
        for (CampaignActionLayoutEntry entry : tacticalActionEntries(ctx, panelRect)) {
            if (entry.action != null && actionId.equals(entry.action.id)) return entry.rect;
        }
        return null;
    }

    private static String objectiveValue(String detail, String prefix) {
        if (detail == null || prefix == null) return "";
        for (String line : detail.split("\\R")) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String selectedDivisionReadout(GameContext ctx) {
        if (ctx == null || ctx.campaign == null || ctx.ui == null) return "Flag Division";
        for (String line : CampaignSystem.strategicDivisionSummaryLines(ctx)) {
            if (line != null && line.endsWith("*")) return line.replace('*', ' ').trim();
        }
        return "Flag Division";
    }

    private static List<String> sampleLines(List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            out.add(line);
            if (out.size() >= max) break;
        }
        return out;
    }

    private static List<CampaignSystem.CampaignAction> sampleActions(List<CampaignSystem.CampaignAction> actions, int max) {
        if (actions == null || actions.isEmpty()) return List.of();
        ArrayList<CampaignSystem.CampaignAction> out = new ArrayList<>();
        for (CampaignSystem.CampaignAction action : actions) {
            if (action == null) continue;
            out.add(action);
            if (out.size() >= max) break;
        }
        return out;
    }

    private static Color tacticalSelectionAccent(GameContext ctx) {
        if (ctx == null || ctx.ui == null || !ctx.ui.tacticalMapSelectionHostile) {
            return new Color(168, 220, 255, 214);
        }
        return new Color(255, 178, 150, 214);
    }

    private static void drawGalaxyNavigationPanel(Graphics2D g2, GameContext ctx, Rectangle panelRect) {
        if (g2 == null || ctx == null || panelRect == null) return;
        paintGalaxyConsolePanel(g2, panelRect, new Color(144, 214, 255, 160));
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        List<String> navLines = CampaignSystem.campaignNavigationStationLines(ctx);
        List<String> receiverLines = CampaignSystem.campaignReceiverBoardLines(ctx);
        List<String> finderLines = CampaignSystem.campaignDirectionFinderLines(ctx);
        List<String> commsLines = CampaignSystem.campaignCommsBoardLines(ctx);

        int rowY = inner.y + 4;
        rowY = drawGalaxySessionClockPanel(g2, inner.x, rowY, inner.width, navLines);
        rowY += 8;
        rowY = drawGalaxyReceiverManualPanel(g2, inner.x, rowY, inner.width, receiverLines, ctx);
        rowY += 8;
        rowY = drawGalaxyDirectionFinderPanel(g2, inner.x, rowY, inner.width, finderLines, ctx);
        rowY += 8;
        drawGalaxyCommsPanel(g2, inner.x, rowY, inner.width, Math.max(116, inner.y + inner.height - rowY - 8), commsLines);
    }

    private static int drawGalaxySessionClockPanel(Graphics2D g2, int x, int y, int width, List<String> navLines) {
        int h = 54;
        drawGalaxyInstrumentPanel(g2, x, y, width, h, "SESSION TIME");
        String time = (navLines != null && navLines.size() > 1) ? navLines.get(1) : "00:00:00";
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(new Color(210, 234, 252, 230));
        g2.drawString(time, x + 12, y + 35);
        g2.setColor(new Color(255, 255, 255, 24));
        for (int i = 0; i < 8; i++) {
            int yy = y + 14 + i * 4;
            g2.drawLine(x + width / 2, yy, x + width - 12, yy);
        }
        return y + h;
    }

    private static int drawGalaxyReceiverManualPanel(Graphics2D g2, int x, int y, int width, List<String> lines, GameContext ctx) {
        int h = 168;
        drawGalaxyInstrumentPanel(g2, x, y, width, h, "RECEIVER MANUAL");
        int dialCx = x + 64;
        int dialCy = y + 82;
        int dialR = 34;
        g2.setColor(new Color(255, 230, 176, 145));
        g2.drawArc(dialCx - dialR, dialCy - dialR, dialR * 2, dialR * 2, 24, 132);
        g2.setColor(new Color(255, 210, 132, 220));
        double needleFrac = navReceiverFraction(ctx);
        double theta = Math.toRadians(204 - 132 * needleFrac);
        int nx = dialCx + (int) Math.round(Math.cos(theta) * (dialR - 4));
        int ny = dialCy - (int) Math.round(Math.sin(theta) * (dialR - 4));
        g2.drawLine(dialCx, dialCy, nx, ny);
        g2.fillOval(dialCx - 3, dialCy - 3, 6, 6);

        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(255, 220, 156, 220));
        g2.drawString("TUNING", x + 36, y + 128);
        g2.drawString("SIGNAL", x + 118, y + 44);

        drawGalaxyMeter(g2, x + 116, y + 56, width - 128, "STRENGTH", navSignalFraction(ctx),
                new Color(255, 198, 132, 220), safeNavLine(lines, 1, "Signal"));
        drawGalaxyMeter(g2, x + 116, y + 80, width - 128, "TRACK", navTrackFraction(ctx),
                new Color(132, 214, 255, 220), safeNavLine(lines, 2, "Track"));
        drawGalaxyMeter(g2, x + 116, y + 104, width - 128, "LOCK", navLockFraction(ctx),
                new Color(116, 224, 186, 220), safeNavLine(lines, 3, "Course"));

        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(210, 228, 242, 205));
        g2.drawString(safeNavLine(lines, 0, "Band"), x + 12, y + 145);
        drawGalaxyBoardLine(g2, x + 12, y + 158, width - 24, safeNavLine(lines, 4, "Best Lead"), new Color(198, 220, 236, 210));
        return y + h;
    }

    private static int drawGalaxyDirectionFinderPanel(Graphics2D g2, int x, int y, int width, List<String> lines, GameContext ctx) {
        int h = 178;
        drawGalaxyInstrumentPanel(g2, x, y, width, h, "DIRECTION FINDER");
        int dialCx = x + 58;
        int dialCy = y + 78;
        int dialR = 28;
        g2.setColor(new Color(160, 220, 255, 145));
        g2.drawOval(dialCx - dialR, dialCy - dialR, dialR * 2, dialR * 2);
        g2.drawLine(dialCx, dialCy - dialR - 4, dialCx, dialCy + dialR + 4);
        g2.drawLine(dialCx - dialR - 4, dialCy, dialCx + dialR + 4, dialCy);
        double theta = Math.toRadians(navBearingAngle(ctx));
        int nx = dialCx + (int) Math.round(Math.sin(theta) * (dialR - 3));
        int ny = dialCy - (int) Math.round(Math.cos(theta) * (dialR - 3));
        g2.setColor(new Color(118, 238, 220, 220));
        g2.drawLine(dialCx, dialCy, nx, ny);
        g2.fillOval(dialCx - 3, dialCy - 3, 6, 6);

        drawGalaxyMeter(g2, x + 102, y + 42, width - 114, "BEARING", navBearingFraction(ctx),
                new Color(132, 214, 255, 220), safeNavLine(lines, 0, "Bearing"));
        drawGalaxyMeter(g2, x + 102, y + 66, width - 114, "EXPOSURE", navExposureFraction(ctx),
                new Color(255, 182, 120, 220), safeNavLine(lines, 1, "Exposure"));
        drawGalaxyMeter(g2, x + 102, y + 90, width - 114, "SWEEP", navSweepFraction(ctx),
                new Color(118, 238, 220, 220), safeNavLine(lines, 3, "Sweep"));
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(210, 228, 242, 205));
        g2.drawString(safeNavLine(lines, 2, "Pressure"), x + 12, y + 126);
        drawGalaxyBoardLine(g2, x + 12, y + 139, width - 24, safeNavLine(lines, 4, "Posture"), new Color(198, 220, 236, 210));
        g2.drawString(safeNavLine(lines, 5, "Enter Site"), x + width - 150, y + 126);
        if (lines.size() > 6) {
            drawGalaxyBoardLine(g2, x + 12, y + 154, width - 24, safeNavLine(lines, 6, "Route Vector"), new Color(198, 220, 236, 210));
        }
        if (lines.size() > 7) {
            drawGalaxyBoardLine(g2, x + 12, y + 168, width - 24, safeNavLine(lines, 7, "Callout"), new Color(198, 220, 236, 210));
        }
        return y + h;
    }

    private static void drawGalaxyCommsPanel(Graphics2D g2, int x, int y, int width, int height, List<String> lines) {
        int h = Math.max(144, Math.min(height, 220));
        drawGalaxyInstrumentPanel(g2, x, y, width, h, "RADIO / COMMS");
        int top = y + 34;
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            Color lamp = (i == 0) ? new Color(132, 236, 170, 220)
                    : (i == 1) ? new Color(255, 206, 132, 220)
                    : (i == 2) ? new Color(132, 214, 255, 220)
                    : new Color(118, 238, 220, 220);
            g2.setColor(lamp);
            g2.fillOval(x + 12, top - 8 + i * 18, 6, 6);
            drawGalaxyBoardLine(g2, x + 24, top + i * 18, width - 36, lines.get(i), new Color(226, 236, 246, 220));
        }
        int infoStart = y + 112;
        for (int i = 4; i < lines.size() && infoStart + (i - 4) * 14 <= y + h - 10; i++) {
            Color accent = (i < 6) ? new Color(198, 220, 236, 210) : new Color(224, 232, 242, 214);
            drawGalaxyBoardLine(g2, x + 12, infoStart + (i - 4) * 14, width - 24, lines.get(i), accent);
        }
    }

    private static void drawGalaxySidebar(Graphics2D g2, GameContext ctx, Rectangle panelRect) {
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        paintGalaxyConsolePanel(g2, panelRect, new Color(170, 214, 255, 155));
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Color oldColor = g2.getColor();
        Font oldFont = g2.getFont();

        drawGalaxyCommandTabs(g2, ctx, inner.x, inner.y + 2, inner.width);
        int rowY = inner.y + 34;
        int actionBlockH = galaxyActionBlockHeight(ctx, panelRect);
        int contentBottom = inner.y + inner.height - actionBlockH - 12;
        rowY = drawGalaxyCommandContent(g2, ctx, selected, inner.x, rowY, inner.width, contentBottom);

        int actionY = inner.y + inner.height - actionBlockH + 6;
        drawGalaxyContentWell(g2, inner.x - 4, actionY - 14, inner.width + 8, actionBlockH + 6,
                new Color(8, 14, 22, 204), new Color(148, 214, 255, 78));
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(196, 232, 255, 225));
        g2.drawString("COMMAND ACTIONS", inner.x, actionY + 10);
        drawGalaxyCommandActions(g2, ctx, panelRect, inner.x, actionY + 18, inner.width, actionY + actionBlockH - 6);

        g2.setColor(oldColor);
        g2.setFont(oldFont);
    }

    private static void paintGalaxyConsolePanel(Graphics2D g2, Rectangle panelRect, Color accent) {
        int x = panelRect.x;
        int y = panelRect.y;
        int w = panelRect.width;
        int h = panelRect.height;
        if (!paintThemedHudFrame(g2, x, y, w, h, accent, ThemeArt.HUD_STANDARD_PANEL, 18)) {
            g2.setColor(new Color(8, 14, 22, 190));
            g2.fillRoundRect(x, y, w, h, 18, 18);
            g2.setColor(new Color(180, 220, 255, 80));
            g2.drawRoundRect(x, y, w, h, 18, 18);
        }
    }

    private static int galaxyActionBlockHeight(GameContext ctx, Rectangle panelRect) {
        if (ctx == null || panelRect == null) return 248;
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        List<CampaignSystem.CampaignAction> actions = galaxyCommandActions(ctx);
        CampaignSystem.CampaignAction primary = CampaignSystem.campaignPrimaryAction(ctx);
        List<CampaignActionSection> sections = galaxyActionSections(actions, primary);
        int height = 50;
        if (primary != null) height += galaxyPrimaryActionHeight();
        for (CampaignActionSection section : sections) {
            if (section == null || section.actions.isEmpty()) continue;
            int columns = galaxyActionColumns(section.actions.size(), inner.width);
            int rows = (int) Math.ceil(section.actions.size() / (double) columns);
            height += galaxySectionHeaderHeight() + rows * galaxySectionRowHeight() + galaxySectionFooterGap();
        }
        return Math.max(206, Math.min(286, height));
    }

    private static int drawGalaxyCommandContent(Graphics2D g2, GameContext ctx, CampaignSystem.CampaignLocation selected,
                                                int x, int y, int width, int maxBottomY) {
        List<String> primaryLines;
        List<String> secondaryLines;
        String primaryHeader;
        String secondaryHeader;
        Color primaryAccent = new Color(184, 228, 255, 220);
        Color secondaryAccent = new Color(255, 196, 164, 220);
        UiState.CampaignCommandTab tab = (ctx == null || ctx.ui == null) ? UiState.CampaignCommandTab.NAV : ctx.ui.campaignCommandTab;
        switch (tab) {
            case FLEET -> {
                primaryHeader = "FLEET MANAGER";
                primaryLines = CampaignSystem.campaignFleetManagerLines(ctx);
                secondaryHeader = "READINESS / DETACHMENTS";
                ArrayList<String> fleetSecondary = new ArrayList<>();
                fleetSecondary.addAll(CampaignSystem.campaignFleetConditionLines(ctx));
                fleetSecondary.addAll(CampaignSystem.campaignFleetDetachmentLines(ctx));
                secondaryLines = fleetSecondary;
            }
            case RESOURCES -> {
                primaryHeader = "RESOURCE BOARD";
                primaryLines = CampaignSystem.campaignResourceManagerLines(ctx);
                secondaryHeader = "LOGISTICS / ROUTE";
                ArrayList<String> resourceSecondary = new ArrayList<>();
                resourceSecondary.addAll(CampaignSystem.campaignResourceTrendLines(ctx));
                resourceSecondary.addAll(CampaignSystem.campaignResourceWarningLines(ctx));
                secondaryLines = resourceSecondary;
            }
            case STRIKES -> {
                primaryHeader = "LONG-RANGE STRIKE CONTROL";
                primaryLines = CampaignSystem.campaignStrikeManagerLines(ctx);
                secondaryHeader = "READINESS / CONSEQUENCES";
                ArrayList<String> strikeSecondary = new ArrayList<>();
                strikeSecondary.addAll(CampaignSystem.campaignStrikeReadinessLines(ctx));
                strikeSecondary.addAll(CampaignSystem.campaignStrikeConsequenceLines(ctx));
                secondaryLines = strikeSecondary;
            }
            default -> {
                primaryHeader = "CAMPAIGN SUMMARY";
                primaryLines = CampaignSystem.campaignSummarySidebarLines(ctx);
                secondaryHeader = (selected == null) ? "SELECTED COURSE" : selected.name.toUpperCase(Locale.US);
                secondaryLines = CampaignSystem.selectedLocationSidebarLines(ctx);
                primaryAccent = new Color(184, 228, 255, 220);
                secondaryAccent = hubAccent(selected, 220);
            }
        }
        int rowY = drawGalaxySidebarSection(g2, x, y, width, primaryHeader, primaryLines, primaryAccent, true);
        if (rowY < maxBottomY) {
            rowY = drawGalaxySidebarSection(g2, x, rowY, width, secondaryHeader, secondaryLines, secondaryAccent, true);
        }
        int remaining = maxBottomY - rowY;
        if (remaining > 54) {
            switch (tab) {
                case FLEET -> drawGalaxyFleetBoard(g2, ctx, x, rowY, width, remaining);
                case RESOURCES -> drawGalaxyResourceBoard(g2, ctx, x, rowY, width, remaining);
                case STRIKES -> drawGalaxyStrikeBoard(g2, ctx, x, rowY, width, remaining);
                default -> drawGalaxyNavigationBoard(g2, ctx, x, rowY, width, remaining);
            }
        }
        return rowY;
    }

    private static void drawGalaxyCommandTabs(Graphics2D g2, GameContext ctx, int x, int y, int width) {
        if (g2 == null || ctx == null) return;
        UiState.CampaignCommandTab active = (ctx.ui == null) ? UiState.CampaignCommandTab.NAV : ctx.ui.campaignCommandTab;
        Rectangle[] rects = galaxyCommandTabRects(x, y, width);
        UiState.CampaignCommandTab[] tabs = UiState.CampaignCommandTab.values();
        for (int i = 0; i < tabs.length && i < rects.length; i++) {
            boolean selected = tabs[i] == active;
            Color accent = selected ? new Color(128, 236, 194, 220) : new Color(160, 190, 214, 180);
            Rectangle rect = rects[i];
            g2.setColor(new Color(10, 16, 24, selected ? 180 : 120));
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
            g2.setColor(new Color(255, 255, 255, selected ? 36 : 18));
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
            drawHudStatusChip(g2, tabs[i].label().toUpperCase(Locale.US), rect.x, rect.y + 14, rect.width, 20, accent, selected);
        }
    }

    private static Rectangle[] galaxyCommandTabRects(int x, int y, int width) {
        int gap = 8;
        int count = UiState.CampaignCommandTab.values().length;
        int tabW = Math.max(66, (width - gap * (count - 1)) / count);
        Rectangle[] rects = new Rectangle[count];
        for (int i = 0; i < count; i++) {
            rects[i] = new Rectangle(x + i * (tabW + gap), y, tabW, 22);
        }
        return rects;
    }

    private static void drawGalaxyCommandActions(Graphics2D g2, GameContext ctx, Rectangle panelRect, int x, int y, int width, int maxBottomY) {
        List<CampaignSystem.CampaignAction> actions = galaxyCommandActions(ctx);
        CampaignSystem.CampaignAction primary = CampaignSystem.campaignPrimaryAction(ctx);
        Rectangle contentRect = new Rectangle(x, y, width, Math.max(1, panelRect.height));
        int cursorY = y;
        Shape oldClip = g2.getClip();
        g2.setClip(x - 4, y - 4, width + 8, Math.max(1, maxBottomY - y + 8));
        if (primary != null) {
            Rectangle primaryRect = galaxyPrimaryActionRect(contentRect, y);
            if (primaryRect.y + primaryRect.height <= maxBottomY) {
                drawGalaxyActionButton(g2, primary, primaryRect.x, primaryRect.y, primaryRect.width, primaryRect.height, true);
                cursorY = primaryRect.y + primaryRect.height + galaxyPrimaryActionGap();
            }
        }
        for (CampaignActionSection section : galaxyActionSections(actions, primary)) {
            if (section == null || section.actions.isEmpty()) continue;
            int sectionHeight = galaxySectionBlockHeight(section, width);
            if (cursorY + sectionHeight > maxBottomY) break;
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            g2.setColor(new Color(172, 214, 244, 208));
            g2.drawString(section.label, x, cursorY + 10);
            int buttonTop = cursorY + galaxySectionHeaderHeight();
            for (CampaignActionLayoutEntry entry : galaxySectionActionLayout(section, x, buttonTop, width)) {
                if (entry.rect.y + entry.rect.height > maxBottomY) continue;
                drawGalaxyActionButton(g2, entry.action, entry.rect.x, entry.rect.y, entry.rect.width, entry.rect.height, false);
            }
            cursorY += sectionHeight;
        }
        g2.setClip(oldClip);
    }

    private static List<CampaignActionSection> galaxyActionSections(List<CampaignSystem.CampaignAction> actions,
                                                                    CampaignSystem.CampaignAction primary) {
        EnumMap<CampaignSystem.CampaignActionCategory, ArrayList<CampaignSystem.CampaignAction>> grouped =
                new EnumMap<>(CampaignSystem.CampaignActionCategory.class);
        for (CampaignSystem.CampaignAction action : actions) {
            if (action == null || action == primary) continue;
            grouped.computeIfAbsent(action.category, ignored -> new ArrayList<>()).add(action);
        }
        ArrayList<CampaignActionSection> out = new ArrayList<>();
        CampaignSystem.CampaignActionCategory[] order = {
                CampaignSystem.CampaignActionCategory.NAVIGATION,
                CampaignSystem.CampaignActionCategory.SERVICES,
                CampaignSystem.CampaignActionCategory.STRIKES,
                CampaignSystem.CampaignActionCategory.SUPPORT,
                CampaignSystem.CampaignActionCategory.POSTURE,
                CampaignSystem.CampaignActionCategory.SITE_RESOLUTION,
                CampaignSystem.CampaignActionCategory.SENSORS
        };
        for (CampaignSystem.CampaignActionCategory category : order) {
            ArrayList<CampaignSystem.CampaignAction> sectionActions = grouped.get(category);
            if (sectionActions == null || sectionActions.isEmpty()) continue;
            out.add(new CampaignActionSection(category, galaxyActionSectionLabel(category), sectionActions));
        }
        return out;
    }

    private static String galaxyActionSectionLabel(CampaignSystem.CampaignActionCategory category) {
        if (category == null) return "COMMANDS";
        return switch (category) {
            case NAVIGATION -> "NAVIGATION";
            case SERVICES -> "LOCAL SERVICES";
            case STRIKES -> "TACTICAL / STRIKE";
            case SUPPORT -> "SUPPORT";
            case POSTURE -> "FLEET POSTURE";
            case SITE_RESOLUTION -> "SITE RESOLUTION";
            case SENSORS -> "SENSORS / COMMS";
        };
    }

    private static int galaxyActionColumns(int actionCount, int width) {
        if (actionCount <= 2) return 2;
        if (width >= 300 && actionCount >= 4) return 3;
        return 2;
    }

    private static int galaxyPrimaryActionHeight() {
        return 34;
    }

    private static int galaxyPrimaryActionGap() {
        return 6;
    }

    private static int galaxySectionHeaderHeight() {
        return 14;
    }

    private static int galaxySectionRowHeight() {
        return 31;
    }

    private static int galaxySectionButtonHeight() {
        return 28;
    }

    private static int galaxySectionFooterGap() {
        return 4;
    }

    private static int galaxySectionColumnGap() {
        return 8;
    }

    private static Rectangle galaxyPrimaryActionRect(Rectangle contentRect, int topY) {
        return new Rectangle(contentRect.x, topY, contentRect.width, galaxyPrimaryActionHeight());
    }

    private static int galaxySectionBlockHeight(CampaignActionSection section, int width) {
        if (section == null || section.actions.isEmpty()) return 0;
        int columns = galaxyActionColumns(section.actions.size(), width);
        int rows = (int) Math.ceil(section.actions.size() / (double) columns);
        return galaxySectionHeaderHeight() + rows * galaxySectionRowHeight() + galaxySectionFooterGap();
    }

    private static List<CampaignActionLayoutEntry> galaxySectionActionLayout(CampaignActionSection section,
                                                                             int x,
                                                                             int topY,
                                                                             int width) {
        ArrayList<CampaignActionLayoutEntry> out = new ArrayList<>();
        if (section == null || section.actions.isEmpty()) return out;
        int columns = galaxyActionColumns(section.actions.size(), width);
        int gap = galaxySectionColumnGap();
        int colW = Math.max(96, (width - gap * (columns - 1)) / columns);
        for (int i = 0; i < section.actions.size(); i++) {
            CampaignSystem.CampaignAction action = section.actions.get(i);
            int col = i % columns;
            int row = i / columns;
            int bx = x + col * (colW + gap);
            int by = topY + row * galaxySectionRowHeight();
            Rectangle rect = new Rectangle(bx, by, colW, galaxySectionButtonHeight());
            out.add(new CampaignActionLayoutEntry(action, rect));
        }
        return out;
    }

    private static void drawGalaxyActionButton(Graphics2D g2, CampaignSystem.CampaignAction action,
                                               int x, int y, int w, int h, boolean primary) {
        if (g2 == null || action == null) return;
        BufferedImage strikeButton = strikeActionButtonImage(action);
        if (strikeButton != null) {
            Composite oldComposite = g2.getComposite();
            float alpha = action.enabled ? 1.0f : 0.54f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.drawImage(strikeButton, x, y, w, h, null);
            g2.setComposite(oldComposite);
            if (!action.enabled) {
                g2.setColor(new Color(12, 18, 28, 120));
                g2.fillRoundRect(x, y, w, h, 10, 10);
            }
            g2.setColor(withAlpha(new Color(178, 220, 255, 220), action.enabled ? 218 : 110));
            g2.drawRoundRect(x, y, w, h, 10, 10);
            return;
        }
        Color accent = switch (action.state) {
            case DISABLED -> new Color(126, 136, 150, 170);
            case WARNING -> new Color(255, 176, 120, 220);
            case RECOMMENDED -> new Color(132, 236, 194, 220);
            default -> new Color(255, 214, 132, 210);
        };
        int fillAlpha = action.enabled ? (primary ? 92 : 74) : 28;
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fillAlpha));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(withAlpha(accent, action.enabled ? 210 : 110));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(10, 16, 24, action.enabled ? 150 : 108));
        g2.fillRoundRect(x + 4, y + 4, Math.max(8, w - 8), Math.max(8, h - 8), 10, 10);
        g2.setColor(withAlpha(accent, action.enabled ? 220 : 90));
        g2.fillOval(x + 10, y + (primary ? 10 : 9), 7, 7);
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, primary ? 13 : 12));
        g2.setColor(new Color(245, 250, 255, action.enabled ? 230 : 144));
        g2.drawString(action.label, x + 22, y + (primary ? 15 : 14));
        g2.setFont(new Font("Consolas", Font.PLAIN, primary ? 11 : 10));
        String detail = action.enabled
                ? (action.shortDescription.isBlank() ? action.tooltip : action.shortDescription)
                : ("Disabled: " + action.disabledReason);
        g2.setColor(new Color(220, 236, 248, action.enabled ? 194 : 124));
        g2.drawString(trimHudLine(detail, w - 28), x + 22, y + (primary ? 29 : 25));
        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static String trimHudLine(String text, int maxWidth) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isBlank()) return "";
        int maxChars = Math.max(8, maxWidth / 6);
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static BufferedImage strikeActionButtonImage(CampaignSystem.CampaignAction action) {
        if (action == null || action.id == null) return null;
        String id = action.id.toUpperCase(Locale.US);
        if (id.contains("TORPEDO_STRIKE")) return StrikeButtonSkinLibrary.getTorpedoButton();
        if (id.contains("CARRIER_SORTIE") || id.contains("AIR_WING")) return StrikeButtonSkinLibrary.getAirWingButton();
        if (id.contains("ATOMIC_STRIKE") || id.contains("NUCLEAR")) return StrikeButtonSkinLibrary.getNuclearButton();
        return null;
    }

    private static void drawGalaxyNavigationBoard(Graphics2D g2, GameContext ctx, int x, int y, int width, int height) {
        if (g2 == null || ctx == null || height < 48) return;
        CampaignSystem.CampaignTravelState travel = CampaignSystem.campaignTravelState(ctx);
        double travelFrac = (travel == null || !travel.traveling) ? 0.0 : travel.progress;
        List<String> afterAction = CampaignSystem.campaignAfterActionPlateLines(ctx);
        List<String> preview = CampaignSystem.campaignActionPreviewLines(ctx);
        int panelH = Math.min(height, afterAction.isEmpty() ? 126 : 176);
        drawGalaxyInstrumentPanel(g2, x, y, width, panelH, afterAction.isEmpty() ? "ROUTE SCHEMATIC" : "ROUTE / AFTER ACTION");
        drawGalaxyMeter(g2, x + 12, y + 34, width - 24, "COURSE PROGRESS", travelFrac, new Color(116, 224, 186, 220),
                (travel == null || !travel.traveling) ? "HOLDING" : ((int) Math.round(travelFrac * 100.0)) + "%");
        drawGalaxyMeter(g2, x + 12, y + 58, width - 24, "EXPOSURE", 0.01 * Math.max(0, parseTrailingNumber(CampaignSystem.campaignExposureReadout(ctx))),
                new Color(255, 182, 120, 220), CampaignSystem.campaignExposureReadout(ctx));
        int infoY = y + 84;
        for (int i = 0; i < preview.size() && infoY + i * 14 <= y + panelH - 34; i++) {
            Color accent = (i == 0) ? new Color(132, 236, 194, 220) : new Color(212, 226, 238, 214);
            drawGalaxyBoardLine(g2, x + 12, infoY + i * 14, width - 24, preview.get(i), accent);
        }
        if (!afterAction.isEmpty()) {
            int plateY = Math.max(y + 118, y + panelH - 44);
            for (int i = 0; i < afterAction.size() && plateY + i * 14 <= y + panelH - 8; i++) {
                Color accent = (i == 0) ? new Color(255, 214, 132, 220) : new Color(212, 226, 238, 214);
                drawGalaxyBoardLine(g2, x + 12, plateY + i * 14, width - 24, afterAction.get(i), accent);
            }
        }
    }

    private static void drawGalaxyFleetBoard(Graphics2D g2, GameContext ctx, int x, int y, int width, int height) {
        if (g2 == null || ctx == null || height < 48) return;
        int panelH = Math.max(170, height);
        drawGalaxyInstrumentPanel(g2, x, y, width, panelH, "FLEET ROSTER / READINESS");
        List<String> summary = CampaignSystem.campaignFleetBoardSummaryLines(ctx);
        int boardY = y + 34;
        for (int i = 0; i < Math.min(3, summary.size()); i++) {
            drawGalaxyBoardLine(g2, x + 12, boardY + i * 18, width - 24, summary.get(i), new Color(226, 236, 246, 220));
        }

        int meterY = y + 92;
        drawGalaxyMeter(g2, x + 12, meterY, width - 24, "READINESS", fleetBoardFraction(summary, 0), new Color(116, 224, 186, 220),
                metricTail(summary, 0, "READY"));
        drawGalaxyMeter(g2, x + 12, meterY + 22, width - 24, "SUPPORT MIX", fleetBoardFraction(summary, 1), new Color(132, 198, 255, 220),
                metricTail(summary, 1, "SUPPORT"));
        drawGalaxyMeter(g2, x + 12, meterY + 44, width - 24, "DETACHMENTS", fleetBoardFraction(summary, 2), new Color(255, 196, 132, 220),
                metricTail(summary, 2, "DETACHED"));

        List<String> condition = CampaignSystem.campaignFleetConditionLines(ctx);
        int conditionY = y + 154;
        for (int i = 0; i < condition.size() && i < 3; i++) {
            Color lamp = switch (i) {
                case 0 -> new Color(112, 230, 172, 220);
                case 1 -> new Color(132, 198, 255, 220);
                default -> new Color(255, 148, 132, 220);
            };
            g2.setColor(lamp);
            g2.fillOval(x + 12, conditionY - 8 + i * 16, 6, 6);
            drawGalaxyBoardLine(g2, x + 24, conditionY + i * 16, width - 36, condition.get(i), new Color(212, 226, 238, 214));
        }

        Rectangle roster = new Rectangle(x + 10, y + 208, width - 20, Math.max(64, y + panelH - (y + 216)));
        drawGalaxyFleetRosterList(g2, ctx, roster);
    }

    private static void drawGalaxyFleetRosterList(Graphics2D g2, GameContext ctx, Rectangle roster) {
        if (g2 == null || ctx == null || roster == null) return;
        List<CampaignSystem.CampaignFleetRosterEntry> entries = CampaignSystem.campaignFleetRosterEntries(ctx);
        g2.setColor(new Color(7, 12, 20, 172));
        g2.fillRoundRect(roster.x, roster.y, roster.width, roster.height, 10, 10);
        g2.setColor(new Color(130, 214, 255, 56));
        g2.drawRoundRect(roster.x, roster.y, roster.width, roster.height, 10, 10);
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(255, 224, 176, 220));
        g2.drawString("SHIP ROSTER  |  CLICK FOCUS  |  DOUBLE-CLICK REFIT WHEN IN HANGAR", roster.x + 8, roster.y + 16);
        if (entries.isEmpty()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(new Color(210, 224, 238, 190));
            g2.drawString("No built ships in the persistent fleet yet.", roster.x + 10, roster.y + 42);
            return;
        }
        int rowH = galaxyFleetRosterRowHeight();
        int maxRows = Math.max(1, (roster.height - 28) / rowH);
        int maxScroll = Math.max(0, entries.size() - maxRows);
        int scroll = (ctx.ui == null) ? 0 : MathUtil.clamp(ctx.ui.campaignFleetRosterScroll, 0, maxScroll);
        if (ctx.ui != null) ctx.ui.campaignFleetRosterScroll = scroll;
        int y0 = roster.y + 28;
        Shape oldClip = g2.getClip();
        g2.setClip(roster.x, roster.y + 24, roster.width, roster.height - 26);
        for (int i = 0; i < maxRows && i + scroll < entries.size(); i++) {
            CampaignSystem.CampaignFleetRosterEntry entry = entries.get(i + scroll);
            Rectangle row = new Rectangle(roster.x + 6, y0 + i * rowH, roster.width - 12, rowH - 5);
            boolean hovered = row.contains((int) Math.round(ctx.cursorScreenX), (int) Math.round(ctx.cursorScreenY));
            drawGalaxyFleetRosterRow(g2, entry, row, hovered);
        }
        g2.setClip(oldClip);
        if (entries.size() > maxRows) {
            int trackX = roster.x + roster.width - 6;
            int trackY = roster.y + 28;
            int trackH = roster.height - 36;
            g2.setColor(new Color(255, 255, 255, 26));
            g2.fillRoundRect(trackX, trackY, 3, trackH, 3, 3);
            int thumbH = Math.max(18, (int) Math.round(trackH * (maxRows / (double) entries.size())));
            int thumbY = trackY + (int) Math.round((trackH - thumbH) * (scroll / (double) Math.max(1, maxScroll)));
            g2.setColor(new Color(132, 236, 194, 150));
            g2.fillRoundRect(trackX - 1, thumbY, 5, thumbH, 4, 4);
        }
    }

    private static void drawGalaxyFleetRosterRow(Graphics2D g2, CampaignSystem.CampaignFleetRosterEntry entry, Rectangle row, boolean hovered) {
        if (g2 == null || entry == null || row == null) return;
        Color accent = entry.selected ? new Color(132, 236, 194, 220)
                : (hovered ? new Color(255, 214, 132, 190) : new Color(132, 198, 255, 150));
        g2.setColor(entry.selected ? new Color(32, 76, 70, 132)
                : (hovered ? new Color(72, 56, 28, 142) : new Color(18, 28, 42, 138)));
        g2.fillRoundRect(row.x, row.y, row.width, row.height, 9, 9);
        g2.setColor(withAlpha(accent, entry.selected ? 190 : (hovered ? 150 : 82)));
        g2.drawRoundRect(row.x, row.y, row.width, row.height, 9, 9);

        int iconCx = row.x + 28;
        int iconCy = row.y + row.height / 2;
        drawRosterHullSilhouette(g2, entry.role, iconCx, iconCy, 18, accent);

        int textX = row.x + 56;
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(242, 248, 255, entry.editable ? 232 : 150));
        g2.drawString(trimHudLine(entry.name, row.width - 68), textX, row.y + 14);
        g2.setFont(new Font("Consolas", Font.PLAIN, 9));
        g2.setColor(new Color(210, 226, 240, 205));
        g2.drawString(trimHudLine(entry.roleLabel + "  |  ORE " + entry.oreCost + "  |  " + entry.readinessLabel, row.width - 68),
                textX, row.y + 28);
        String bottom = entry.cargoLabel + "  |  " + entry.forceLabel + "  |  " + entry.groupLabel + "  |  " + entry.commitmentLabel
                + (entry.unavailableReason.isBlank() ? "" : "  |  " + entry.unavailableReason.toUpperCase(Locale.US));
        g2.setColor(entry.commitmentLabel.contains("HOLD")
                ? new Color(255, 170, 142, 212)
                : (entry.commitmentLabel.contains("COMMIT") ? new Color(132, 236, 194, 214) : new Color(214, 226, 238, 198)));
        g2.drawString(trimHudLine(bottom, row.width - 68), textX, row.y + 42);
    }

    private static void drawRosterHullSilhouette(Graphics2D g2, ShipRole role, int cx, int cy, int radius, Color accent) {
        if (g2 == null) return;
        Polygon base = ShipHullSilhouette.hullPolygon(role, Math.max(8.0, radius), Faction.ALLY);
        Polygon poly = new Polygon();
        for (int i = 0; i < base.npoints; i++) {
            poly.addPoint(cx + base.xpoints[i], cy + base.ypoints[i]);
        }
        g2.setColor(new Color(8, 14, 22, 220));
        g2.fillOval(cx - radius - 7, cy - radius - 7, (radius + 7) * 2, (radius + 7) * 2);
        g2.setColor(withAlpha(accent == null ? new Color(132, 198, 255) : accent, 96));
        g2.fillPolygon(poly);
        g2.setColor(withAlpha(accent == null ? new Color(132, 198, 255) : accent, 230));
        g2.drawPolygon(poly);
    }

    private static void drawGalaxyResourceBoard(Graphics2D g2, GameContext ctx, int x, int y, int width, int height) {
        if (g2 == null || ctx == null || height < 70) return;
        int panelH = Math.min(height, 214);
        drawGalaxyInstrumentPanel(g2, x, y, width, panelH, "RESOURCE BOARD");
        drawGalaxyMeter(g2, x + 12, y + 34, width - 24, "FUEL", Math.min(1.0, CampaignSystem.campaignFuel(ctx) / 180.0), new Color(120, 220, 255, 220), String.valueOf(CampaignSystem.campaignFuel(ctx)));
        drawGalaxyMeter(g2, x + 12, y + 56, width - 24, "SUPPLIES", Math.min(1.0, CampaignSystem.campaignSupplies(ctx) / 150.0), new Color(148, 224, 168, 220), String.valueOf(CampaignSystem.campaignSupplies(ctx)));
        drawGalaxyMeter(g2, x + 12, y + 78, width - 24, "AMMO", Math.min(1.0, CampaignSystem.campaignAmmo(ctx) / 180.0), new Color(255, 206, 132, 220), String.valueOf(CampaignSystem.campaignAmmo(ctx)));
        drawGalaxyMeter(g2, x + 12, y + 100, width - 24, "SALVAGE", Math.min(1.0, CampaignSystem.campaignSalvageStock(ctx) / 60.0), new Color(255, 150, 132, 220), String.valueOf(CampaignSystem.campaignSalvageStock(ctx)));
        drawGalaxyMeter(g2, x + 12, y + 122, width - 24, "ORE", Math.min(1.0, CampaignSystem.currentCampaignOre(ctx) / 80.0), new Color(214, 198, 120, 220), String.valueOf(CampaignSystem.currentCampaignOre(ctx)));

        List<String> trend = CampaignSystem.campaignResourceTrendLines(ctx);
        int trendY = y + 154;
        for (int i = 0; i < trend.size() && i < 4; i++) {
            drawGalaxyBoardLine(g2, x + 12, trendY + i * 15, width - 24, trend.get(i), new Color(214, 226, 238, 214));
        }

        List<String> warnings = CampaignSystem.campaignResourceWarningLines(ctx);
        int warnY = y + 154 + Math.min(4, trend.size()) * 15 + 8;
        for (int i = 0; i < warnings.size() && warnY + i * 15 <= y + panelH - 8; i++) {
            Color lamp = (warnings.get(i).contains("LOW") || warnings.get(i).contains("CRITICAL"))
                    ? new Color(255, 146, 132, 220)
                    : new Color(132, 214, 180, 220);
            g2.setColor(lamp);
            g2.fillOval(x + 12, warnY - 8 + i * 15, 6, 6);
            drawGalaxyBoardLine(g2, x + 24, warnY + i * 15, width - 36, warnings.get(i), new Color(214, 226, 238, 214));
        }
    }

    private static void drawGalaxyStrikeBoard(Graphics2D g2, GameContext ctx, int x, int y, int width, int height) {
        if (g2 == null || ctx == null || height < 64) return;
        int panelH = Math.min(height, 218);
        drawGalaxyInstrumentPanel(g2, x, y, width, panelH, "CONTACT / STRIKE BOARD");
        List<String> readiness = CampaignSystem.campaignStrikeReadinessLines(ctx);
        int top = y + 34;
        for (int i = 0; i < readiness.size() && i < 4; i++) {
            Color lamp = (i < 3) ? new Color(120, 220, 255, 220) : new Color(120, 236, 186, 220);
            g2.setColor(lamp);
            g2.fillOval(x + 12, top - 8 + i * 18, 6, 6);
            drawGalaxyBoardLine(g2, x + 24, top + i * 18, width - 36, readiness.get(i), new Color(226, 236, 246, 220));
        }
        List<String> consequences = CampaignSystem.campaignStrikeConsequenceLines(ctx);
        String strikeHeat = consequences.size() > 1 ? consequences.get(1) : "Strike Heat: COLD (0)";
        drawGalaxyMeter(g2, x + 12, y + 112, width - 24, "INTEL LOCK", CampaignSystem.campaignIntelPercent(ctx),
                new Color(120, 220, 255, 220), CampaignSystem.campaignIntelReadout(ctx));
        drawGalaxyMeter(g2, x + 12, y + 134, width - 24, "EXPOSURE", CampaignSystem.campaignExposurePercent(ctx),
                new Color(255, 182, 120, 220), CampaignSystem.campaignExposureReadout(ctx));
        drawGalaxyMeter(g2, x + 12, y + 156, width - 24, "STRIKE HEAT", MathUtil.clamp(parseTrailingNumber(strikeHeat) / 100.0, 0.0, 1.0),
                new Color(255, 140, 120, 220), strikeHeat);
        int infoY = y + 190;
        for (int i = 0; i < consequences.size() && i < 4 && infoY + i * 14 <= y + panelH - 8; i++) {
            drawGalaxyBoardLine(g2, x + 12, infoY + i * 14, width - 24, consequences.get(i), new Color(214, 226, 238, 214));
        }
    }

    private static void drawGalaxyInstrumentPanel(Graphics2D g2, int x, int y, int width, int height, String title) {
        drawGalaxyContentWell(g2, x, y, width, height, new Color(10, 16, 24, 138), new Color(255, 255, 255, 24));
        g2.setColor(new Color(18, 26, 36, 170));
        g2.fillRoundRect(x + 6, y + 22, width - 12, Math.max(18, height - 28), 10, 10);
        g2.setColor(new Color(255, 255, 255, 18));
        g2.drawRoundRect(x + 6, y + 22, width - 12, Math.max(18, height - 28), 10, 10);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(255, 224, 176, 220));
        g2.drawString(title, x + 10, y + 16);
    }

    private static void drawGalaxyMeter(Graphics2D g2, int x, int y, int width, String label, double frac, Color accent, String value) {
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(220, 230, 240, 210));
        g2.drawString(label, x, y);
        g2.drawString(value, x + width - Math.max(40, g2.getFontMetrics().stringWidth(value)), y);
        int barY = y + 6;
        g2.setColor(new Color(18, 18, 18, 150));
        g2.fillRoundRect(x, barY, width, 10, 8, 8);
        g2.setColor(withAlpha(accent, 170));
        g2.fillRoundRect(x + 1, barY + 1, Math.max(4, (int) Math.round((width - 2) * MathUtil.clamp(frac, 0.0, 1.0))), 8, 8, 8);
    }

    private static void drawGalaxyBoardLine(Graphics2D g2, int x, int y, int width, String text, Color color) {
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(color);
        List<String> wrapped = wrapHudText(g2.getFontMetrics(), text, width);
        if (!wrapped.isEmpty()) {
            g2.drawString(wrapped.get(0), x, y);
        }
    }

    private static int parseTrailingNumber(String text) {
        if (text == null || text.isBlank()) return 0;
        String digits = text.replaceAll("[^0-9]+", " ").trim();
        if (digits.isBlank()) return 0;
        String[] parts = digits.split("\\s+");
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double fleetBoardFraction(List<String> lines, int row) {
        if (lines == null || row < 0 || row >= lines.size()) return 0.0;
        String text = lines.get(row);
        if (text == null || text.isBlank()) return 0.0;
        String[] nums = text.replaceAll("[^0-9]+", " ").trim().split("\\s+");
        if (nums.length < 2) return 0.0;
        try {
            int a = Integer.parseInt(nums[0]);
            int b = Integer.parseInt(nums[1]);
            int total = Math.max(1, a + b);
            return MathUtil.clamp(a / (double) total, 0.0, 1.0);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String metricTail(List<String> lines, int row, String fallback) {
        if (lines == null || row < 0 || row >= lines.size() || lines.get(row) == null || lines.get(row).isBlank()) {
            return fallback;
        }
        return lines.get(row);
    }

    private static String safeNavLine(List<String> lines, int index, String fallback) {
        if (lines == null || index < 0 || index >= lines.size() || lines.get(index) == null || lines.get(index).isBlank()) {
            return fallback;
        }
        return lines.get(index);
    }

    private static double navReceiverFraction(GameContext ctx) {
        return navIntelFraction(ctx);
    }

    private static double navSignalFraction(GameContext ctx) {
        return navAlertFraction(ctx);
    }

    private static double navTrackFraction(GameContext ctx) {
        String hunted = CampaignSystem.campaignHuntStatusReadout(ctx);
        if (hunted == null) return 0.0;
        if (hunted.contains("Hunted")) return 1.0;
        if (hunted.contains("Tracked")) return 0.68;
        return 0.22;
    }

    private static double navLockFraction(GameContext ctx) {
        String label = CampaignSystem.selectedStrategicDestinationLabel(ctx);
        return (label == null || label.equalsIgnoreCase("None selected")) ? 0.12 : 0.88;
    }

    private static double navBearingFraction(GameContext ctx) {
        String bearing = CampaignSystem.campaignDirectionFinderLines(ctx).isEmpty() ? "000 DEG" : CampaignSystem.campaignDirectionFinderLines(ctx).get(0);
        int value = parseTrailingNumber(bearing);
        return MathUtil.clamp(value / 360.0, 0.0, 1.0);
    }

    private static double navExposureFraction(GameContext ctx) {
        return CampaignSystem.campaignExposurePercent(ctx);
    }

    private static double navSweepFraction(GameContext ctx) {
        List<String> lines = CampaignSystem.campaignDirectionFinderLines(ctx);
        String value = safeNavLine(lines, 3, "Sweep");
        if (value.contains("OPEN")) return 0.95;
        if (value.contains("WORKABLE")) return 0.66;
        if (value.contains("NARROW")) return 0.38;
        return 0.12;
    }

    private static double navIntelFraction(GameContext ctx) {
        return CampaignSystem.campaignIntelPercent(ctx);
    }

    private static double navAlertFraction(GameContext ctx) {
        return CampaignSystem.enemyAlertPercent(ctx);
    }

    private static double navBearingAngle(GameContext ctx) {
        List<String> lines = CampaignSystem.campaignDirectionFinderLines(ctx);
        int value = parseTrailingNumber(safeNavLine(lines, 0, "000 DEG"));
        return Math.toRadians(Math.floorMod(value, 360));
    }

    private static List<CampaignSystem.CampaignAction> galaxyCommandActions(GameContext ctx) {
        return CampaignSystem.campaignVisibleActions(ctx);
    }

    private static int drawGalaxySidebarSection(Graphics2D g2, int x, int y, int width, String header,
                                                List<String> lines, Color accent, boolean compact) {
        if (g2 == null) return y;
        List<String> displayLines = galaxySidebarDisplayLines(lines, compact ? 6 : 7);
        int headerGap = compact ? 18 : 20;
        int lineStep = compact ? 15 : 17;
        int panelHeight = compact ? 88 : 110;
        if (displayLines != null) {
            int wrappedCount = 0;
            FontMetrics fm = g2.getFontMetrics(new Font("Consolas", Font.PLAIN, compact ? 12 : 13));
            for (String line : displayLines) {
                if (line == null || line.isBlank()) continue;
                wrappedCount += Math.max(1, wrapHudText(fm, line, width - 24).size());
            }
            panelHeight = Math.max(panelHeight, 42 + wrappedCount * lineStep);
        }
        drawGalaxyContentWell(g2, x - 4, y + 2, width + 8, panelHeight, new Color(8, 14, 22, compact ? 170 : 184),
                new Color(144, 214, 255, 52));
        g2.setColor(new Color(150, 220, 255, 140));
        g2.drawLine(x + 2, y + 14, x + width - 2, y + 14);
        y += headerGap;
        g2.setFont(new Font("Consolas", Font.BOLD, compact ? 13 : 14));
        g2.setColor(accent == null ? new Color(196, 232, 255, 220) : accent);
        g2.drawString((header == null) ? "SECTION" : header, x, y);
        y += compact ? 17 : 19;
        g2.setFont(new Font("Consolas", Font.PLAIN, compact ? 12 : 13));
        g2.setColor(new Color(228, 238, 248, 224));
        for (String line : displayLines) {
            if (line == null || line.isBlank()) continue;
            List<String> wrapped = wrapHudText(g2.getFontMetrics(), line, width);
            for (String wrap : wrapped) {
                g2.drawString(wrap, x, y);
                y += lineStep;
            }
        }
        return y + 8;
    }

    private static List<String> galaxySidebarDisplayLines(List<String> lines, int maxLines) {
        if (lines == null || lines.isEmpty()) return List.of();
        int limit = Math.max(1, maxLines);
        ArrayList<String> out = new ArrayList<>();
        int count = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            if (count >= limit) break;
            out.add(line);
            count++;
        }
        int hidden = 0;
        for (String line : lines) {
            if (line != null && !line.isBlank()) hidden++;
        }
        hidden -= out.size();
        if (hidden > 0 && !out.isEmpty()) {
            out.add("+ " + hidden + " more in command buttons / hover details");
        }
        return out;
    }

    private static void drawGalaxyContentWell(Graphics2D g2, int x, int y, int width, int height, Color fill, Color stroke) {
        if (g2 == null) return;
        g2.setColor(fill == null ? new Color(10, 16, 24, 160) : fill);
        g2.fillRoundRect(x, y, width, height, 14, 14);
        g2.setColor(stroke == null ? new Color(255, 255, 255, 22) : stroke);
        g2.drawRoundRect(x, y, width, height, 14, 14);
    }

    private static String galaxyTravelStatus(GameContext ctx, CampaignSystem.CampaignTravelState travel) {
        if (travel == null || !travel.traveling) {
            return "Holding position";
        }
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        int eta = (int) Math.ceil(Math.max(0.0, (1.0 - travel.progress) * travel.durationSec));
        return "En route to " + ((selected == null) ? "target" : selected.name)
                + "  ETA " + eta + "s  RISK " + (int) Math.round(travel.interceptionRisk) + "%";
    }

    private static String galaxyThreatStatus(GameContext ctx, CampaignSystem.CampaignLocation selected) {
        if (selected == null) return "No destination selected";
        return CampaignSystem.threatReadoutForSidebar(selected.threatLevel);
    }

    private static String galaxyAlertStatus(GameContext ctx) {
        return CampaignSystem.enemyAlertReadout(ctx);
    }

    private static String galaxyLocationFaction(CampaignSystem.CampaignLocation location) {
        if (location == null || location.name == null) return "Neutral";
        String name = location.name.toUpperCase(Locale.US);
        if (name.contains("GREEN")) return "Green Team";
        if (name.contains("YELLOW")) return "Yellow Team";
        if (location.type == CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY) return "Hostile";
        return "Neutral";
    }

    private static List<String> buildCampaignHubInfoLines(GameContext ctx, CampaignSystem.CampaignLocation location) {
        if (ctx == null || location == null || location.services.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        out.add(location.name + "  |  " + location.detail);
        out.add("Resources: Credits " + ctx.credits
                + "  Ore " + CampaignSystem.currentCampaignOre(ctx)
                + "  Fuel " + CampaignSystem.campaignFuel(ctx));
        out.add("Stores: Supplies " + CampaignSystem.campaignSupplies(ctx)
                + "  Ammo " + CampaignSystem.campaignAmmo(ctx)
                + "  Salvage " + CampaignSystem.campaignSalvageStock(ctx));
        return out;
    }

    private static Color hubAccent(CampaignSystem.CampaignLocation location, int alpha) {
        String name = (location == null || location.name == null) ? "" : location.name.toUpperCase(Locale.US);
        if (name.contains("GREEN")) return new Color(120, 236, 188, MathUtil.clamp(alpha, 0, 255));
        if (name.contains("YELLOW")) return new Color(255, 214, 122, MathUtil.clamp(alpha, 0, 255));
        if (name.contains("SHIPYARD") || name.contains("SHIPWORKS") || name.contains("DRYDOCK")) {
            return new Color(148, 198, 255, MathUtil.clamp(alpha, 0, 255));
        }
        if (name.contains("RELAY")) return new Color(188, 180, 255, MathUtil.clamp(alpha, 0, 255));
        return new Color(172, 220, 255, MathUtil.clamp(alpha, 0, 255));
    }

    private static void drawCampaignHubButtons(Graphics2D g2, GameContext ctx, Rectangle panelRect,
                                               int x, int y, int width,
                                               List<CampaignSystem.HubService> services,
                                               CampaignSystem.CampaignLocation location) {
        if (g2 == null || services == null || services.isEmpty()) return;
        Color accent = hubAccent(location, 220);
        int colW = Math.max(120, (width - 10) / 2);
        int index = 0;
        for (CampaignSystem.HubService service : services) {
            if (service == null) continue;
            int col = index % 2;
            int row = index / 2;
            int bx = x + col * (colW + 10);
            int by = y + row * 32;
            drawCampaignHubActionButton(g2, ctx, bx, by, colW, 26, accent, location, service);
            index++;
        }
    }

    private static void drawCampaignHubActionButton(Graphics2D g2, GameContext ctx,
                                                    int x, int y, int w, int h, Color accent,
                                                    CampaignSystem.CampaignLocation location,
                                                    CampaignSystem.HubService service) {
        if (g2 == null || service == null) return;
        boolean docked = CampaignSystem.isDockedAtSelectedLocation(ctx);
        Color base = docked ? accent : new Color(160, 176, 196, 180);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), docked ? 62 : 34));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(withAlpha(base, docked ? 196 : 132));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(245, 250, 255, docked ? 228 : 178));
        g2.drawString(CampaignSystem.hubServiceActionLabel(ctx, location, service), x + 7, y + 11);
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(220, 236, 248, docked ? 210 : 160));
        g2.drawString(CampaignSystem.hubServiceActionDetail(ctx, location, service), x + 7, y + 22);
        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static int campaignHubButtonsStartY(GameContext ctx, Rectangle panelRect) {
        if (ctx == null || panelRect == null) return Integer.MIN_VALUE;
        String title = CampaignSystem.hudObjectiveTitle(ctx);
        String body = CampaignSystem.hudObjectiveExpandedDetail(ctx);
        if ((title == null || title.isBlank()) && (body == null || body.isBlank())) return Integer.MIN_VALUE;

        List<CampaignSystem.CampaignObjectiveMarker> markers = CampaignSystem.activeObjectiveMarkers(ctx);
        List<CampaignSystem.CampaignLandmark> landmarks = CampaignSystem.strategicLandmarks(ctx);
        List<GameRenderSystem.SensorNetEntry> supportEntries = buildStrategicSupportEntryList(ctx);
        List<String> taskForceLines = CampaignSystem.strategicTaskForceSummaryLines(ctx);
        List<String> divisionLines = CampaignSystem.strategicDivisionSummaryLines(ctx);
        String actionSummary = CampaignSystem.strategicMapActionSummary(ctx);
        CampaignSystem.CampaignLocation selectedLocation = CampaignSystem.selectedCampaignLocation(ctx);
        List<String> hubLines = buildCampaignHubInfoLines(ctx, selectedLocation);

        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsG = metricsImage.createGraphics();
        FontMetrics titleFm = metricsG.getFontMetrics(new Font("Consolas", Font.BOLD, 13));
        FontMetrics bodyFm = metricsG.getFontMetrics(new Font("Consolas", Font.PLAIN, 11));
        FontMetrics markerFm = metricsG.getFontMetrics(new Font("Consolas", Font.PLAIN, 10));
        int contentW = Math.max(180, themedContentWidth(ThemeArt.HUD_STANDARD_PANEL, panelRect.width, panelRect.height));

        List<String> titleLines = limitHudLines(wrapHudText(titleFm, title, contentW), 2);
        List<String> bodyLines = limitHudLines(wrapHudMultilineText(bodyFm, body, contentW), 9);
        List<String> wrappedMarkerLines = new ArrayList<>();
        for (String markerLine : buildStrategicObjectiveMarkerLines(markers)) {
            wrappedMarkerLines.addAll(limitHudLines(wrapHudText(markerFm, markerLine, contentW), 1));
        }
        List<String> wrappedLandmarkLines = new ArrayList<>();
        for (String landmarkLine : buildStrategicLandmarkLines(landmarks)) {
            wrappedLandmarkLines.addAll(limitHudLines(wrapHudText(markerFm, landmarkLine, contentW), 1));
        }
        List<String> wrappedSupportLines = new ArrayList<>();
        for (String supportLine : buildStrategicSupportLines(supportEntries)) {
            wrappedSupportLines.addAll(limitHudLines(wrapHudText(markerFm, supportLine, contentW), 1));
        }
        List<String> wrappedTaskForceLines = new ArrayList<>();
        for (String taskForceLine : taskForceLines) {
            wrappedTaskForceLines.addAll(limitHudLines(wrapHudText(markerFm, taskForceLine, contentW), 1));
        }
        List<String> wrappedDivisionLines = new ArrayList<>();
        for (String divisionLine : divisionLines) {
            wrappedDivisionLines.addAll(limitHudLines(wrapHudText(markerFm, divisionLine, contentW), 1));
        }
        List<String> actionLines = (actionSummary == null || actionSummary.isBlank())
                ? List.of()
                : limitHudLines(wrapHudText(markerFm, actionSummary, contentW), 1);

        int landmarkBlockH = wrappedLandmarkLines.isEmpty() ? 0 : 10 + wrappedLandmarkLines.size() * 13;
        int markerBlockH = wrappedMarkerLines.isEmpty() ? 0 : 10 + wrappedMarkerLines.size() * 13;
        int taskForceBlockH = wrappedTaskForceLines.isEmpty() ? 0 : 10 + wrappedTaskForceLines.size() * 13;
        int divisionBlockH = wrappedDivisionLines.isEmpty() ? 0 : 10 + wrappedDivisionLines.size() * 13;
        int actionBlockH = actionLines.isEmpty() ? 0 : 10 + actionLines.size() * 13;
        int supportBlockH = wrappedSupportLines.isEmpty() ? 0 : 10 + wrappedSupportLines.size() * 13;
        int hubBlockH = hubLines.isEmpty() ? 0 : 14 + hubLines.size() * 13;
        int panelH = Math.min(panelRect.height,
                30 + titleLines.size() * 16 + Math.max(1, bodyLines.size()) * 14
                        + landmarkBlockH + markerBlockH + taskForceBlockH + divisionBlockH
                        + actionBlockH + supportBlockH + hubBlockH + 12);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL,
                panelRect.x, panelRect.y, panelRect.width, panelH);
        metricsG.dispose();

        int rowY = inner.y + titleLines.size() * 16 + 4 + Math.max(1, bodyLines.size()) * 14;
        if (!wrappedLandmarkLines.isEmpty()) rowY += 4 + 12 + 13 + wrappedLandmarkLines.size() * 13;
        if (!wrappedMarkerLines.isEmpty()) rowY += 4 + 12 + 13 + wrappedMarkerLines.size() * 13;
        if (!wrappedTaskForceLines.isEmpty()) rowY += 4 + 12 + 13 + wrappedTaskForceLines.size() * 13;
        if (!wrappedDivisionLines.isEmpty()) rowY += 4 + 12 + 13 + wrappedDivisionLines.size() * 13;
        if (!actionLines.isEmpty()) rowY += 4 + 12 + 13 + actionLines.size() * 13;
        if (!wrappedSupportLines.isEmpty()) rowY += 4 + 12 + 13 + wrappedSupportLines.size() * 13;
        if (!hubLines.isEmpty()) rowY += 4 + 12 + 13 + hubLines.size() * 13;
        return rowY + 6 + 14 + 10;
    }

    public static CampaignHubClickTarget campaignHubClickTargetAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null || !CampaignSystem.isStrategicGalaxyMapMode(ctx)) return null;
        if (ctx.ui.campaignActionConfirm.active) {
            Rectangle overlay = campaignActionConfirmOverlayRect(viewW, viewH);
            Rectangle closeRect = new Rectangle(overlay.x + overlay.width - 92, overlay.y + overlay.height - 38, 78, 22);
            Rectangle confirmRect = new Rectangle(overlay.x + 18, overlay.y + overlay.height - 38, 122, 22);
            if (confirmRect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.CONFIRM, ctx.ui.campaignActionConfirm.actionId);
            }
            if (closeRect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.CLOSE, "");
            }
            return null;
        }
        if (ctx.ui.campaignHubMenu.active) {
            Rectangle overlay = campaignHubOverlayRect(viewW, viewH);
            Rectangle closeRect = new Rectangle(overlay.x + overlay.width - 92, overlay.y + overlay.height - 38, 78, 22);
            Rectangle confirmRect = new Rectangle(overlay.x + 18, overlay.y + overlay.height - 38, 122, 22);
            if (confirmRect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.CONFIRM, ctx.ui.campaignHubMenu.serviceId);
            }
            if (closeRect.contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.CLOSE, "");
            }
            return null;
        }
        Rectangle panelRect = getStrategicMapSidebarRect(viewW, viewH, true);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        Rectangle[] tabRects = galaxyCommandTabRects(inner.x, inner.y + 2, inner.width);
        UiState.CampaignCommandTab[] tabs = UiState.CampaignCommandTab.values();
        for (int i = 0; i < tabs.length && i < tabRects.length; i++) {
            if (tabRects[i].contains(mouseX, mouseY)) {
                return new CampaignHubClickTarget(CampaignHubClickTarget.Kind.TAB, "", tabs[i].name());
            }
        }
        if (ctx.ui.campaignCommandTab == UiState.CampaignCommandTab.FLEET) {
            CampaignHubClickTarget fleetTarget = galaxyFleetRosterClickTargetAt(ctx, panelRect, mouseX, mouseY);
            if (fleetTarget != null) return fleetTarget;
        }
        for (CampaignHubClickTarget target : galaxyActionClickTargets(ctx, panelRect)) {
            Rectangle rect = galaxyActionRect(ctx, panelRect, target.valueId);
            if (rect != null && rect.contains(mouseX, mouseY)) return target;
        }
        return null;
    }

    public static boolean campaignFleetRosterContains(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null || !CampaignSystem.isStrategicGalaxyMapMode(ctx)) return false;
        if (ctx.ui.campaignCommandTab != UiState.CampaignCommandTab.FLEET) return false;
        Rectangle panelRect = getStrategicMapSidebarRect(viewW, viewH, true);
        Rectangle roster = galaxyFleetRosterRect(ctx, panelRect);
        return roster.contains(mouseX, mouseY);
    }

    public static int campaignFleetRosterVisibleRows(GameContext ctx, int viewW, int viewH) {
        Rectangle panelRect = getStrategicMapSidebarRect(viewW, viewH, true);
        Rectangle roster = galaxyFleetRosterRect(ctx, panelRect);
        return Math.max(1, (roster.height - 28) / galaxyFleetRosterRowHeight());
    }

    private static CampaignHubClickTarget galaxyFleetRosterClickTargetAt(GameContext ctx, Rectangle panelRect, int mouseX, int mouseY) {
        CampaignSystem.CampaignFleetRosterEntry entry = galaxyFleetRosterEntryAt(ctx, panelRect, mouseX, mouseY);
        return entry == null ? null : new CampaignHubClickTarget(CampaignHubClickTarget.Kind.FLEET_ROSTER, "", String.valueOf(entry.slotId));
    }

    private static CampaignSystem.CampaignFleetRosterEntry galaxyFleetRosterEntryAt(GameContext ctx, Rectangle panelRect, int mouseX, int mouseY) {
        Rectangle roster = galaxyFleetRosterRect(ctx, panelRect);
        if (!roster.contains(mouseX, mouseY)) return null;
        List<CampaignSystem.CampaignFleetRosterEntry> entries = CampaignSystem.campaignFleetRosterEntries(ctx);
        if (entries.isEmpty()) return null;
        int rowH = galaxyFleetRosterRowHeight();
        int scroll = (ctx == null || ctx.ui == null) ? 0 : Math.max(0, ctx.ui.campaignFleetRosterScroll);
        int index = scroll + (mouseY - (roster.y + 28)) / rowH;
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    private static Rectangle galaxyFleetRosterRect(GameContext ctx, Rectangle panelRect) {
        if (panelRect == null) return new Rectangle();
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        int actionBlockH = galaxyActionBlockHeight(ctx, panelRect);
        int contentBottom = inner.y + inner.height - actionBlockH - 12;
        int boardY = inner.y + 34;
        boardY += estimateGalaxySidebarSectionHeight(CampaignSystem.campaignFleetManagerLines(ctx), inner.width, true);
        if (boardY < contentBottom) {
            ArrayList<String> secondary = new ArrayList<>();
            secondary.addAll(CampaignSystem.campaignFleetConditionLines(ctx));
            secondary.addAll(CampaignSystem.campaignFleetDetachmentLines(ctx));
            boardY += estimateGalaxySidebarSectionHeight(secondary, inner.width, false);
        }
        int remaining = contentBottom - boardY;
        if (remaining <= 54) return new Rectangle();
        int panelH = Math.max(170, remaining);
        return new Rectangle(inner.x + 10, boardY + 208, Math.max(80, inner.width - 20), Math.max(54, panelH - 216));
    }

    private static int galaxyFleetRosterRowHeight() {
        return 56;
    }

    private static int estimateGalaxySidebarSectionHeight(List<String> lines, int width, boolean compact) {
        int lineStep = compact ? 15 : 17;
        int panelHeight = compact ? 88 : 110;
        int wrappedCount = 0;
        int chars = Math.max(18, (width - 24) / 7);
        List<String> displayLines = galaxySidebarDisplayLines(lines, compact ? 7 : 9);
        if (displayLines != null) {
            for (String line : displayLines) {
                if (line == null || line.isBlank()) continue;
                wrappedCount += Math.max(1, (line.length() + chars - 1) / chars);
            }
        }
        panelHeight = Math.max(panelHeight, 42 + wrappedCount * lineStep);
        int drawnTextHeight = (compact ? 18 : 20) + (compact ? 17 : 19) + wrappedCount * lineStep;
        return Math.max(panelHeight + 2, drawnTextHeight + 8);
    }

    private static List<CampaignHubClickTarget> galaxyActionClickTargets(GameContext ctx, Rectangle panelRect) {
        List<CampaignSystem.CampaignAction> actions = galaxyCommandActions(ctx);
        ArrayList<CampaignHubClickTarget> out = new ArrayList<>();
        for (CampaignSystem.CampaignAction action : actions) {
            if (action == null || action.id.isBlank()) continue;
            out.add(new CampaignHubClickTarget(CampaignHubClickTarget.Kind.ACTION, "", action.id));
        }
        return out;
    }

    private static Rectangle galaxyActionRect(GameContext ctx, Rectangle panelRect, String actionId) {
        if (panelRect == null || actionId == null || actionId.isBlank()) return null;
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        List<CampaignSystem.CampaignAction> actions = galaxyCommandActions(ctx);
        CampaignSystem.CampaignAction primary = CampaignSystem.campaignPrimaryAction(ctx);
        int actionBlockH = galaxyActionBlockHeight(ctx, panelRect);
        int baseY = inner.y + inner.height - actionBlockH + 24;
        if (primary != null && actionId.equals(primary.id)) {
            return galaxyPrimaryActionRect(new Rectangle(inner.x, baseY, inner.width, actionBlockH), baseY);
        }
        int cursorY = baseY + ((primary == null) ? 0 : (galaxyPrimaryActionHeight() + galaxyPrimaryActionGap()));
        for (CampaignActionSection section : galaxyActionSections(actions, primary)) {
            if (section == null || section.actions.isEmpty()) continue;
            int buttonTop = cursorY + galaxySectionHeaderHeight();
            for (CampaignActionLayoutEntry entry : galaxySectionActionLayout(section, inner.x, buttonTop, inner.width)) {
                if (entry.action != null && actionId.equals(entry.action.id)) return entry.rect;
            }
            cursorY += galaxySectionBlockHeight(section, inner.width);
        }
        return null;
    }

    public static void drawCampaignHubOverlay(Graphics2D g2, GameContext ctx, int viewW, int viewH) {
        if (g2 == null || ctx == null || ctx.ui == null || !ctx.ui.campaignHubMenu.active) return;
        CampaignSystem.CampaignLocation location = CampaignSystem.selectedCampaignLocation(ctx);
        CampaignSystem.HubService service = campaignHubService(ctx);
        if (location == null || service == null) return;
        Rectangle rect = campaignHubOverlayRect(viewW, viewH);
        g2.setColor(new Color(0, 0, 0, 168));
        g2.fillRect(0, 0, viewW, viewH);
        paintThemedHudFrame(g2, rect.x, rect.y, rect.width, rect.height, hubAccent(location, 220), ThemeArt.HUD_STANDARD_PANEL, 18);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, rect.x, rect.y, rect.width, rect.height);
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(hubAccent(location, 230));
        g2.drawString(service.label.toUpperCase(Locale.US), inner.x, inner.y + 20);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(228, 236, 248, 220));
        List<String> lines = campaignHubOverlayLines(ctx, location, service);
        int rowY = inner.y + 44;
        for (String line : lines) {
            g2.drawString(line, inner.x, rowY);
            rowY += 16;
        }
        drawHudStatusChip(g2, "CONFIRM", inner.x, rect.y + rect.height - 38, 122, 22, hubAccent(location, 220), true);
        drawHudStatusChip(g2, "BACK", rect.x + rect.width - 92, rect.y + rect.height - 38, 78, 22, new Color(180, 200, 220, 220), false);
        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    public static void drawCampaignActionConfirmOverlay(Graphics2D g2, GameContext ctx, int viewW, int viewH) {
        if (g2 == null || ctx == null || ctx.ui == null || !ctx.ui.campaignActionConfirm.active) return;
        Rectangle rect = campaignActionConfirmOverlayRect(viewW, viewH);
        g2.setColor(new Color(0, 0, 0, 176));
        g2.fillRect(0, 0, viewW, viewH);
        paintThemedHudFrame(g2, rect.x, rect.y, rect.width, rect.height, new Color(255, 176, 120, 220), ThemeArt.HUD_STANDARD_PANEL, 18);
        Rectangle inner = themedContentRect(ThemeArt.HUD_STANDARD_PANEL, rect.x, rect.y, rect.width, rect.height);
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(new Color(255, 204, 154, 236));
        g2.drawString(ctx.ui.campaignActionConfirm.title, inner.x, inner.y + 20);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(228, 236, 248, 220));
        int rowY = inner.y + 46;
        for (String line : wrapHudText(g2.getFontMetrics(), ctx.ui.campaignActionConfirm.body, inner.width - 20)) {
            g2.drawString(line, inner.x, rowY);
            rowY += 16;
        }
        drawHudStatusChip(g2, "CONFIRM", inner.x, rect.y + rect.height - 38, 122, 22, new Color(255, 176, 120, 220), true);
        drawHudStatusChip(g2, "CANCEL", rect.x + rect.width - 92, rect.y + rect.height - 38, 78, 22, new Color(180, 200, 220, 220), false);
        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static Rectangle campaignHubOverlayRect(int viewW, int viewH) {
        int w = Math.min(520, Math.max(380, viewW / 2));
        int h = Math.min(280, Math.max(220, viewH / 3));
        return new Rectangle((viewW - w) / 2, (viewH - h) / 2, w, h);
    }

    private static Rectangle campaignActionConfirmOverlayRect(int viewW, int viewH) {
        int w = Math.min(560, Math.max(400, viewW / 2));
        int h = Math.min(300, Math.max(220, viewH / 3));
        return new Rectangle((viewW - w) / 2, (viewH - h) / 2, w, h);
    }

    private static CampaignSystem.HubService campaignHubService(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return null;
        try {
            return CampaignSystem.HubService.valueOf(ctx.ui.campaignHubMenu.serviceId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> campaignHubOverlayLines(GameContext ctx,
                                                        CampaignSystem.CampaignLocation location,
                                                        CampaignSystem.HubService service) {
        return CampaignSystem.hubServicePreviewLines(ctx, location, service);
    }

    private static List<String> buildStrategicLandmarkLines(List<CampaignSystem.CampaignLandmark> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CampaignSystem.CampaignLandmark landmark : landmarks) {
            if (landmark == null) continue;
            String label = (landmark.label == null) ? "" : landmark.label.trim();
            if (label.isBlank()) continue;
            String key = landmark.type + "|" + label;
            if (!seen.add(key)) continue;
            String prefix = switch (landmark.type) {
                case PLANET -> "PLANET";
                case STAR -> "STAR";
                case RING -> "RING";
                case COLONY -> "COLONY";
                case RELAY -> "RELAY";
                case FORTRESS -> "FORT";
                case FRONT -> "FRONT";
                case CORRIDOR -> "LANE";
            };
            String detail = (landmark.subtitle == null || landmark.subtitle.isBlank()) ? "" : " - " + landmark.subtitle;
            out.add(prefix + " " + label + detail);
        }
        return out;
    }

    private static List<String> buildStrategicObjectiveMarkerLines(List<CampaignSystem.CampaignObjectiveMarker> markers) {
        if (markers == null || markers.isEmpty()) return List.of();
        ArrayList<CampaignSystem.CampaignObjectiveMarker> ordered = new ArrayList<>(markers);
        ordered.sort(Comparator.comparingInt((CampaignSystem.CampaignObjectiveMarker marker) -> marker.priority).reversed());
        ArrayList<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CampaignSystem.CampaignObjectiveMarker marker : ordered) {
            if (marker == null) continue;
            String key = marker.type + "|" + marker.label;
            if (!seen.add(key)) continue;
            String prefix = strategicMarkerListPrefix(marker.type);
            String detail = (marker.subtitle == null || marker.subtitle.isBlank()) ? "" : " - " + marker.subtitle;
            out.add(prefix + " " + marker.label + detail);
            if (out.size() >= 6) break;
        }
        return out;
    }

    private static List<GameRenderSystem.SensorNetEntry> buildStrategicSupportEntryList(GameContext ctx) {
        List<GameRenderSystem.SensorNetEntry> all = GameRenderSystem.sensorNetEntries(ctx, 4, 2);
        if (all.isEmpty()) return List.of();
        ArrayList<GameRenderSystem.SensorNetEntry> out = new ArrayList<>();
        for (GameRenderSystem.SensorNetEntry entry : all) {
            if (entry == null || "MISSION".equals(entry.section)) continue;
            out.add(entry);
            if (out.size() >= 4) break;
        }
        return out;
    }

    private static List<String> buildStrategicSupportLines(List<GameRenderSystem.SensorNetEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        String currentSection = "";
        for (GameRenderSystem.SensorNetEntry entry : entries) {
            if (entry == null) continue;
            if (!entry.section.equals(currentSection)) {
                currentSection = entry.section;
                out.add("[" + currentSection + "]");
            }
            String title = (entry.title == null || entry.title.isBlank()) ? "Contact" : entry.title;
            String detail = (entry.detail == null) ? "" : entry.detail;
            out.add("- " + title + (detail.isBlank() ? "" : " | " + detail));
        }
        return out;
    }

    private static String strategicMarkerListPrefix(CampaignSystem.ObjectiveMarkerType type) {
        if (type == null) return "[OBJ]";
        return switch (type) {
            case PRIMARY_OBJECTIVE -> "[OBJ]";
            case NEXT_ROUTE -> "[ROUTE]";
            case ESCORT_TARGET -> "[ESCORT]";
            case PROTECTED_ASSET -> "[PROTECT]";
            case DESTROY_TARGET -> "[KILL]";
            case CAPTURE_ZONE -> "[CAPTURE]";
            case BOSS_TARGET -> "[BOSS]";
            case OPTIONAL_OBJECTIVE -> "[OPTIONAL]";
        };
    }

    private static String buildSectorMapHeader(BattlefieldSectorSystem.SectorDefinition loadedSector,
                                               BattlefieldSectorSystem.SectorDefinition currentSector,
                                               BattlefieldSectorSystem.SectorDefinition selectedSector,
                                               UiState.TacticalSectorScalePreset scalePreset) {
        UiState.TacticalSectorScalePreset preset =
                (scalePreset == null) ? UiState.TacticalSectorScalePreset.STANDARD : scalePreset;
        StringBuilder out = new StringBuilder();
        BattlefieldSectorSystem.SectorDefinition focus = (loadedSector != null) ? loadedSector : currentSector;
        if (focus != null) {
            out.append("Loaded: ").append(focus.label);
        }
        if (selectedSector != null && (focus == null || !selectedSector.id.equalsIgnoreCase(focus.id))) {
            if (out.length() > 0) out.append("   ");
            out.append("Target: ").append(selectedSector.label);
        }
        if (out.length() == 0 && currentSector != null) {
            out.append("Current: ").append(currentSector.label);
        }
        if (out.length() > 0) {
            out.append("   ");
        }
        out.append("Scale: ").append(preset.label().toUpperCase(Locale.US));
        return out.toString();
    }

    private static void drawBattlefieldSectorsOnMap(Graphics2D g2,
                                                    Rectangle mapRect,
                                                    GameContext ctx,
                                                    List<BattlefieldSectorSystem.SectorSnapshot> sectorSnapshots,
                                                    BattlefieldSectorSystem.SectorDefinition currentSector,
                                                    BattlefieldSectorSystem.SectorDefinition selectedSector,
                                                    double worldMinX, double worldMinY,
                                                    double worldViewW, double worldViewH) {
        if (g2 == null || mapRect == null || sectorSnapshots == null || sectorSnapshots.isEmpty()) return;

        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        for (BattlefieldSectorSystem.SectorSnapshot snapshot : sectorSnapshots) {
            if (snapshot == null || snapshot.sector == null) continue;
            Rectangle sectorRect = sectorMapRect(mapRect, ctx, snapshot.sector, worldMinX, worldMinY, worldViewW, worldViewH);
            if (sectorRect.width <= 0 || sectorRect.height <= 0) continue;

            Color fill = sectorFillColor(snapshot);
            Color border = sectorBorderColor(snapshot);
            g2.setColor(fill);
            g2.fillRect(sectorRect.x, sectorRect.y, sectorRect.width, sectorRect.height);

            g2.setColor(border);
            g2.drawRect(sectorRect.x, sectorRect.y, sectorRect.width, sectorRect.height);

            if (currentSector != null && currentSector.id.equalsIgnoreCase(snapshot.sector.id)) {
                g2.setStroke(new BasicStroke(2.2f));
                g2.setColor(new Color(240, 248, 255, 190));
                g2.drawRect(sectorRect.x + 1, sectorRect.y + 1,
                        Math.max(0, sectorRect.width - 2), Math.max(0, sectorRect.height - 2));
            }
            if (selectedSector != null && selectedSector.id.equalsIgnoreCase(snapshot.sector.id)) {
                g2.setStroke(new BasicStroke(2.6f));
                g2.setColor(new Color(255, 208, 96, 210));
                g2.drawRect(sectorRect.x + 4, sectorRect.y + 4,
                        Math.max(0, sectorRect.width - 8), Math.max(0, sectorRect.height - 8));
            }
            g2.setStroke(oldStroke);

            int labelX = sectorRect.x + 8;
            int labelY = sectorRect.y + 18;
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            g2.setColor(new Color(255, 255, 255, 225));
            g2.drawString(snapshot.sector.label, labelX, labelY);

            g2.setFont(new Font("Consolas", Font.PLAIN, 10));
            g2.setColor(new Color(220, 230, 240, 190));
            g2.drawString(BattlefieldSectorSystem.absoluteStatusLabel(snapshot), labelX, labelY + 13);
            if (ctx != null && ctx.player != null) {
                String relativeStatus = BattlefieldSectorSystem.relativeStatusLabel(ctx, snapshot);
                if (!relativeStatus.isBlank()) {
                    g2.drawString(relativeStatus, labelX, labelY + 26);
                }
            }
        }
        g2.setFont(oldFont);
        g2.setStroke(oldStroke);
    }

    private static Rectangle sectorMapRect(Rectangle mapRect,
                                           GameContext ctx,
                                           BattlefieldSectorSystem.SectorDefinition sector,
                                           double worldMinX, double worldMinY,
                                           double worldViewW, double worldViewH) {
        if (mapRect == null || ctx == null || sector == null) return new Rectangle();
        double worldW = Math.max(1.0, worldViewW);
        double worldH = Math.max(1.0, worldViewH);
        int x0 = mapRect.x + (int) Math.round(((sector.minWorldX(ctx) - worldMinX) / worldW) * mapRect.width);
        int y0 = mapRect.y + (int) Math.round(((sector.minWorldY(ctx) - worldMinY) / worldH) * mapRect.height);
        int x1 = mapRect.x + (int) Math.round(((sector.maxWorldX(ctx) - worldMinX) / worldW) * mapRect.width);
        int y1 = mapRect.y + (int) Math.round(((sector.maxWorldY(ctx) - worldMinY) / worldH) * mapRect.height);
        return new Rectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static Color sectorFillColor(BattlefieldSectorSystem.SectorSnapshot snapshot) {
        if (snapshot == null) return new Color(255, 255, 255, 18);
        if (snapshot.controlState == BattlefieldSectorSystem.ControlState.EMPTY) {
            return new Color(255, 255, 255, 12);
        }
        if (snapshot.controlState == BattlefieldSectorSystem.ControlState.CONTESTED) {
            return new Color(255, 186, 92, 28);
        }
        if (snapshot.dominantFaction == null) {
            return new Color(160, 200, 255, 24);
        }
        Color tint = factionMapColor(snapshot.dominantFaction, false, 30);
        return new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 28);
    }

    private static Color sectorBorderColor(BattlefieldSectorSystem.SectorSnapshot snapshot) {
        if (snapshot == null) return new Color(255, 255, 255, 60);
        if (snapshot.controlState == BattlefieldSectorSystem.ControlState.CONTESTED) {
            return new Color(255, 206, 120, 110);
        }
        if (snapshot.dominantFaction == null) {
            return new Color(255, 255, 255, 60);
        }
        return factionMapColor(snapshot.dominantFaction, false, 96);
    }

    private static void drawCampaignSectorsOnMap(Graphics2D g2, Rectangle mapRect, GameContext ctx,
                                                 double worldMinX, double worldMinY,
                                                 double worldViewW, double worldViewH) {
        if (g2 == null || mapRect == null || ctx == null || !CampaignSystem.usesMissionSubzones(ctx)) return;
        int cols = CampaignSystem.missionSubzoneColumns();
        int rows = CampaignSystem.missionSubzoneRows();
        int loaded = CampaignSystem.currentLoadedMissionSubzone(ctx);
        Set<Integer> objectiveSubzones = new HashSet<>();
        Set<Integer> primarySubzones = new HashSet<>();
        for (CampaignSystem.CampaignObjectiveMarker marker : CampaignSystem.activeObjectiveMarkers(ctx)) {
            if (marker == null) continue;
            int subzone = CampaignSystem.missionSubzoneForPoint(ctx, ctx.campaign.sector, marker.x, marker.y);
            if (subzone < 0) continue;
            objectiveSubzones.add(subzone);
            if (marker.type == CampaignSystem.ObjectiveMarkerType.PRIMARY_OBJECTIVE
                    || marker.type == CampaignSystem.ObjectiveMarkerType.NEXT_ROUTE
                    || marker.type == CampaignSystem.ObjectiveMarkerType.ESCORT_TARGET
                    || marker.type == CampaignSystem.ObjectiveMarkerType.BOSS_TARGET) {
                primarySubzones.add(subzone);
            }
        }
        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int subzone = CampaignSystem.missionSubzoneIndex(col, row);
                Rectangle sectorRect = campaignSectorMapRect(mapRect, ctx, ctx.campaign.sector, subzone,
                        worldMinX, worldMinY, worldViewW, worldViewH);
                if (sectorRect.width <= 0 || sectorRect.height <= 0) continue;

                boolean active = subzone == loaded;
                boolean objective = objectiveSubzones.contains(subzone);
                boolean primaryObjective = primarySubzones.contains(subzone);
                if (primaryObjective) {
                    g2.setColor(new Color(255, 210, 120, active ? 34 : 24));
                    g2.fillRect(sectorRect.x, sectorRect.y, sectorRect.width, sectorRect.height);
                } else if (objective) {
                    g2.setColor(new Color(168, 225, 255, active ? 28 : 20));
                    g2.fillRect(sectorRect.x, sectorRect.y, sectorRect.width, sectorRect.height);
                } else if (active) {
                    g2.setColor(new Color(180, 235, 255, 18));
                    g2.fillRect(sectorRect.x, sectorRect.y, sectorRect.width, sectorRect.height);
                }

                g2.setStroke(primaryObjective
                        ? STRATEGIC_MAP_ZONE_OBJECTIVE_STROKE
                        : (active ? STRATEGIC_MAP_ZONE_ACTIVE_STROKE : STRATEGIC_MAP_ZONE_STROKE));
                g2.setColor(primaryObjective
                        ? new Color(255, 224, 164, 218)
                        : (objective
                        ? new Color(168, 230, 255, 178)
                        : (active ? new Color(236, 247, 255, 205) : new Color(118, 218, 255, 118))));
                g2.drawRect(sectorRect.x, sectorRect.y, sectorRect.width, sectorRect.height);

                g2.setFont(STRATEGIC_MAP_ZONE_FONT);
                g2.setColor(primaryObjective
                        ? new Color(255, 236, 192, 235)
                        : (objective
                        ? new Color(216, 244, 255, 215)
                        : (active ? new Color(244, 252, 255, 220) : new Color(182, 230, 244, 132))));
                String label = campaignSectorLabel(col, row);
                g2.drawString(label, sectorRect.x + 6, sectorRect.y + 14);
                if (primaryObjective) {
                    g2.setFont(STRATEGIC_MAP_ZONE_TAG_FONT);
                    g2.setColor(new Color(255, 214, 142, 220));
                    g2.drawString("OBJ", sectorRect.x + 6, sectorRect.y + 28);
                } else if (objective) {
                    g2.setFont(STRATEGIC_MAP_ZONE_TAG_FONT);
                    g2.setColor(new Color(176, 228, 255, 205));
                    g2.drawString("TASK", sectorRect.x + 6, sectorRect.y + 28);
                }
            }
        }
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static void drawLocalOperatingAreaOnMap(Graphics2D g2, Rectangle mapRect, GameContext ctx,
                                                    double worldMinX, double worldMinY,
                                                    double worldViewW, double worldViewH) {
        if (g2 == null || mapRect == null || ctx == null) return;
        double centerX = (ctx.player != null) ? ctx.player.x : (worldMinX + worldViewW * 0.5);
        double centerY = (ctx.player != null) ? ctx.player.y : (worldMinY + worldViewH * 0.5);
        int cx = mapRect.x + (int) Math.round(((centerX - worldMinX) / Math.max(1.0, worldViewW)) * mapRect.width);
        int cy = mapRect.y + (int) Math.round(((centerY - worldMinY) / Math.max(1.0, worldViewH)) * mapRect.height);

        double[] radii = {220.0, 420.0, 760.0, 1150.0};
        Color[] colors = {
                new Color(255, 226, 170, 148),
                new Color(172, 232, 255, 126),
                new Color(150, 210, 255, 108),
                new Color(128, 184, 255, 92)
        };
        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.94f));

        for (int i = 0; i < radii.length; i++) {
            double worldRadius = radii[i];
            int pxRadius = (int) Math.max(8, Math.round((worldRadius / Math.max(1.0, worldViewW)) * mapRect.width));
            g2.setStroke((i == 0) ? STRATEGIC_MAP_ZONE_ACTIVE_STROKE : STRATEGIC_MAP_ZONE_STROKE);
            g2.setColor(colors[i]);
            g2.drawOval(cx - pxRadius, cy - pxRadius, pxRadius * 2, pxRadius * 2);
        }

        g2.setColor(new Color(230, 244, 255, 198));
        g2.setStroke(STRATEGIC_MAP_OBJECTIVE_STROKE);
        g2.drawLine(cx - 11, cy, cx + 11, cy);
        g2.drawLine(cx, cy - 11, cx, cy + 11);
        g2.fillOval(cx - 3, cy - 3, 6, 6);

        g2.setFont(STRATEGIC_MAP_ZONE_TAG_FONT);
        g2.setColor(new Color(196, 232, 255, 188));
        g2.drawString("LOCAL OPERATING AREA", MathUtil.clamp(cx + 14, mapRect.x + 6, mapRect.x + mapRect.width - 170), MathUtil.clamp(cy - 12, mapRect.y + 14, mapRect.y + mapRect.height - 8));

        g2.setComposite(oldComposite);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static void drawStrategicObjectiveMarkers(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                                      double worldMinX, double worldMinY,
                                                      double worldW, double worldH) {
        if (g2 == null || ctx == null || mapRect == null) return;
        List<CampaignSystem.CampaignObjectiveMarker> markers = new ArrayList<>(CampaignSystem.activeObjectiveMarkers(ctx));
        if (markers.isEmpty()) return;
        markers.sort(Comparator.comparingInt((CampaignSystem.CampaignObjectiveMarker marker) -> marker.priority).reversed());

        Set<String> occupiedLabels = new HashSet<>();
        for (CampaignSystem.CampaignObjectiveMarker marker : markers) {
            if (marker == null) continue;
            String key = marker.type + "|" + marker.label + "|" + Math.round(marker.x / 25.0) + "|" + Math.round(marker.y / 25.0);
            if (!occupiedLabels.add(key)) continue;
            drawStrategicObjectiveMarker(g2, ctx, mapRect, worldMinX, worldMinY, worldW, worldH, marker);
        }
    }

    private static void drawStrategicLandmarkMarkers(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                                     double worldMinX, double worldMinY,
                                                     double worldW, double worldH) {
        if (g2 == null || ctx == null || mapRect == null) return;
        List<CampaignSystem.CampaignLandmark> markers = new ArrayList<>(CampaignSystem.strategicLandmarks(ctx));
        if (markers.isEmpty()) return;

        Set<String> occupiedLabels = new HashSet<>();
        for (CampaignSystem.CampaignLandmark marker : markers) {
            if (marker == null) continue;
            String key = marker.type + "|" + marker.label + "|" + Math.round(marker.x / 25.0) + "|" + Math.round(marker.y / 25.0);
            if (!occupiedLabels.add(key)) continue;
            drawStrategicLandmarkMarker(g2, ctx, mapRect, worldMinX, worldMinY, worldW, worldH, marker);
        }
    }

    private static void drawStrategicSupportMarkers(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                                    double worldMinX, double worldMinY,
                                                    double worldW, double worldH) {
        if (g2 == null || ctx == null || mapRect == null) return;
        List<CampaignSystem.CampaignSupportMarker> markers = new ArrayList<>(CampaignSystem.activeSupportMarkers(ctx));
        if (markers.isEmpty()) return;
        markers.sort(Comparator.comparingInt((CampaignSystem.CampaignSupportMarker marker) -> marker.priority).reversed());

        Set<String> occupiedLabels = new HashSet<>();
        for (CampaignSystem.CampaignSupportMarker marker : markers) {
            if (marker == null) continue;
            String key = marker.type + "|" + marker.label + "|" + Math.round(marker.x / 25.0) + "|" + Math.round(marker.y / 25.0);
            if (!occupiedLabels.add(key)) continue;
            drawStrategicSupportMarker(g2, ctx, mapRect, worldMinX, worldMinY, worldW, worldH, marker);
        }
    }

    private static void drawStrategicObjectiveMarker(Graphics2D g2,
                                                     GameContext ctx,
                                                     Rectangle mapRect,
                                                     double worldMinX,
                                                     double worldMinY,
                                                     double worldW,
                                                     double worldH,
                                                     CampaignSystem.CampaignObjectiveMarker marker) {
        if (g2 == null || mapRect == null || marker == null) return;
        double wx = marker.x;
        double wy = marker.y;
        int px = mapRect.x + (int) Math.round(((wx - worldMinX) / Math.max(1.0, worldW)) * mapRect.width);
        int py = mapRect.y + (int) Math.round(((wy - worldMinY) / Math.max(1.0, worldH)) * mapRect.height);
        String label = marker.label;
        px = MathUtil.clamp(px, mapRect.x + 8, mapRect.x + mapRect.width - 8);
        py = MathUtil.clamp(py, mapRect.y + 8, mapRect.y + mapRect.height - 8);

        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        Composite oldComposite = g2.getComposite();
        Color accent = strategicMarkerColor(marker);
        Color fill = withAlpha(accent, strategicMarkerFillAlpha(marker.type));
        boolean selected = isSelectedMapMarker(ctx, marker.label, marker.x, marker.y);
        int sizeBoost = selected ? 2 : markerPriorityBoost(marker.priority);
        int outerRadius = strategicMarkerOuterRadius(marker.type) + sizeBoost;
        int innerRadius = strategicMarkerInnerRadius(marker.type) + Math.max(0, sizeBoost - 1);
        int crossRadius = strategicMarkerCrossRadius(marker.type) + sizeBoost;

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        g2.setColor(fill);
        g2.fillOval(px - outerRadius - 3, py - outerRadius - 3, (outerRadius + 3) * 2, (outerRadius + 3) * 2);

        g2.setStroke(STRATEGIC_MAP_OBJECTIVE_STROKE);
        g2.setColor(withAlpha(accent, 232));
        g2.drawOval(px - outerRadius, py - outerRadius, outerRadius * 2, outerRadius * 2);
        g2.drawOval(px - innerRadius, py - innerRadius, innerRadius * 2, innerRadius * 2);
        g2.drawLine(px - crossRadius, py, px - innerRadius - 2, py);
        g2.drawLine(px + innerRadius + 2, py, px + crossRadius, py);
        g2.drawLine(px, py - crossRadius, px, py - innerRadius - 2);
        g2.drawLine(px, py + innerRadius + 2, px, py + crossRadius);
        if (selected) {
            double pulse = 0.55 + 0.45 * Math.sin(System.nanoTime() * 1e-9 * 5.4);
            g2.setColor(withAlpha(accent, (int) Math.round(120 + pulse * 80)));
            g2.drawOval(px - outerRadius - 6, py - outerRadius - 6, (outerRadius + 6) * 2, (outerRadius + 6) * 2);
        }
        drawStrategicMarkerCenterGlyph(g2, marker.type, px, py);

        boolean simplified = CampaignSystem.isCampaignWarMapSimplified(ctx);
        if (label != null && !label.isBlank() && (!simplified || selected)) {
            g2.setFont(STRATEGIC_MAP_OBJECTIVE_FONT);
            FontMetrics fm = g2.getFontMetrics();
            String shortLabel = label.trim().toUpperCase(Locale.US);
            int maxWidth = Math.max(96, mapRect.width / 5);
            while (fm.stringWidth(shortLabel) > maxWidth && shortLabel.length() > 18) {
                shortLabel = shortLabel.substring(0, shortLabel.length() - 1).trim();
            }
            if (fm.stringWidth(shortLabel) > maxWidth && shortLabel.length() > 3) {
                shortLabel = shortLabel.substring(0, Math.max(3, shortLabel.length() - 3)).trim() + "...";
            }
            int tw = fm.stringWidth(shortLabel);
            int tx = Math.max(mapRect.x + 6, Math.min(mapRect.x + mapRect.width - tw - 6, px - tw / 2));
            int ty = Math.max(mapRect.y + 16, py - 16);
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRoundRect(tx - 5, ty - 11, tw + 10, 16, 10, 10);
            g2.setColor(withAlpha(accent, 235));
            g2.drawRoundRect(tx - 5, ty - 11, tw + 10, 16, 10, 10);
            g2.drawString(shortLabel, tx, ty + 1);
        }

        g2.setComposite(oldComposite);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static void drawStrategicSupportMarker(Graphics2D g2,
                                                   GameContext ctx,
                                                   Rectangle mapRect,
                                                   double worldMinX,
                                                   double worldMinY,
                                                   double worldW,
                                                   double worldH,
                                                   CampaignSystem.CampaignSupportMarker marker) {
        if (g2 == null || mapRect == null || marker == null) return;
        int px = mapRect.x + (int) Math.round(((marker.x - worldMinX) / Math.max(1.0, worldW)) * mapRect.width);
        int py = mapRect.y + (int) Math.round(((marker.y - worldMinY) / Math.max(1.0, worldH)) * mapRect.height);
        px = MathUtil.clamp(px, mapRect.x + 8, mapRect.x + mapRect.width - 8);
        py = MathUtil.clamp(py, mapRect.y + 8, mapRect.y + mapRect.height - 8);

        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        Composite oldComposite = g2.getComposite();
        Color accent = strategicSupportMarkerColor(marker);
        boolean selected = isSelectedMapMarker(ctx, marker.label, marker.x, marker.y);
        int radius = strategicSupportMarkerRadius(marker.type) + (selected ? 2 : markerPriorityBoost(marker.priority));

        float markerAlpha = strategicSupportMarkerAlpha(marker);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, markerAlpha * 0.56f));
        g2.setColor(withAlpha(accent, 124));
        g2.fillOval(px - radius - 2, py - radius - 2, (radius + 2) * 2, (radius + 2) * 2);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, markerAlpha * 0.88f));
        g2.setStroke(new BasicStroke(1.3f));
        g2.setColor(withAlpha(accent, 210));
        if (selected) {
            double pulse = 0.55 + 0.45 * Math.sin(System.nanoTime() * 1e-9 * 5.0);
            g2.drawOval(px - radius - 5, py - radius - 5, (radius + 5) * 2, (radius + 5) * 2);
            g2.setColor(withAlpha(accent, (int) Math.round(188 + pulse * 36)));
        }
        drawStrategicSupportMarkerGlyph(g2, marker.type, px, py, radius);

        if (shouldShowSupportMarkerLabel(ctx, marker, selected)
                && (!CampaignSystem.isCampaignWarMapSimplified(ctx) || selected)
                && marker.label != null && !marker.label.isBlank()) {
            g2.setFont(STRATEGIC_MAP_OBJECTIVE_FONT);
            FontMetrics fm = g2.getFontMetrics();
            String shortLabel = strategicSupportShortLabel(marker).trim().toUpperCase(Locale.US);
            int maxWidth = Math.max(88, mapRect.width / 6);
            while (fm.stringWidth(shortLabel) > maxWidth && shortLabel.length() > 14) {
                shortLabel = shortLabel.substring(0, shortLabel.length() - 1).trim();
            }
            if (fm.stringWidth(shortLabel) > maxWidth && shortLabel.length() > 3) {
                shortLabel = shortLabel.substring(0, Math.max(3, shortLabel.length() - 3)).trim() + "...";
            }
            int tw = fm.stringWidth(shortLabel);
            int tx = Math.max(mapRect.x + 6, Math.min(mapRect.x + mapRect.width - tw - 6, px - tw / 2));
            int ty = strategicSupportLabelY(mapRect, marker, py);
            g2.setColor(new Color(0, 0, 0, 138));
            g2.fillRoundRect(tx - 4, ty - 10, tw + 8, 15, 8, 8);
            g2.setColor(withAlpha(accent, 214));
            g2.drawRoundRect(tx - 4, ty - 10, tw + 8, 15, 8, 8);
            g2.drawString(shortLabel, tx, ty + 1);
        }

        g2.setComposite(oldComposite);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static String strategicSupportShortLabel(CampaignSystem.CampaignSupportMarker marker) {
        if (marker == null || marker.label == null) return "";
        String label = marker.label.trim();
        if (label.endsWith("Outer Screen")) return "OUTER SCREEN";
        if (label.endsWith("Docked Strike Wing")) return "STRIKE WING";
        if (label.endsWith("Reserve Picket")) return "RESERVE PICKET";
        if (label.endsWith("Perimeter Screen")) return "PERIMETER SCREEN";
        if (label.endsWith("Response Corvette Line")) return "RESPONSE LINE";
        if (label.endsWith("Defense Lattice")) return "DEFENSE LATTICE";
        return label;
    }

    private static int strategicSupportLabelY(Rectangle mapRect,
                                              CampaignSystem.CampaignSupportMarker marker,
                                              int py) {
        int lane = 0;
        if (marker != null && marker.label != null && !marker.label.isBlank()) {
            lane = Math.floorMod(marker.label.hashCode(), 3);
        }
        int offset = switch (lane) {
            case 1 -> -18;
            case 2 -> 30;
            default -> 18;
        };
        return Math.min(mapRect.y + mapRect.height - 8, Math.max(mapRect.y + 16, py + offset));
    }

    private static void drawStrategicLandmarkMarker(Graphics2D g2,
                                                    GameContext ctx,
                                                    Rectangle mapRect,
                                                    double worldMinX,
                                                    double worldMinY,
                                                    double worldW,
                                                    double worldH,
                                                    CampaignSystem.CampaignLandmark marker) {
        if (g2 == null || mapRect == null || marker == null) return;
        int px = mapRect.x + (int) Math.round(((marker.x - worldMinX) / Math.max(1.0, worldW)) * mapRect.width);
        int py = mapRect.y + (int) Math.round(((marker.y - worldMinY) / Math.max(1.0, worldH)) * mapRect.height);
        px = MathUtil.clamp(px, mapRect.x + 7, mapRect.x + mapRect.width - 7);
        py = MathUtil.clamp(py, mapRect.y + 7, mapRect.y + mapRect.height - 7);

        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        Composite oldComposite = g2.getComposite();
        Color accent = strategicLandmarkColor(marker);
        boolean selected = isSelectedMapMarker(ctx, marker.label, marker.x, marker.y);
        int radius = strategicLandmarkRadius(marker.type) + (selected ? 2 : 0);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.36f));
        g2.setColor(withAlpha(accent, 105));
        g2.fillOval(px - radius - 2, py - radius - 2, (radius + 2) * 2, (radius + 2) * 2);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
        g2.setStroke(new BasicStroke(1.1f));
        g2.setColor(withAlpha(accent, 176));
        g2.drawOval(px - radius, py - radius, radius * 2, radius * 2);
        if (selected) {
            g2.setColor(withAlpha(accent, 210));
            g2.drawOval(px - radius - 4, py - radius - 4, (radius + 4) * 2, (radius + 4) * 2);
        }
        drawStrategicLandmarkGlyph(g2, marker.type, px, py, radius);

        if (marker.label != null && !marker.label.isBlank()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 9));
            FontMetrics fm = g2.getFontMetrics();
            String shortLabel = marker.label.trim().toUpperCase(Locale.US);
            int maxWidth = Math.max(84, mapRect.width / 7);
            while (fm.stringWidth(shortLabel) > maxWidth && shortLabel.length() > 14) {
                shortLabel = shortLabel.substring(0, shortLabel.length() - 1).trim();
            }
            if (fm.stringWidth(shortLabel) > maxWidth && shortLabel.length() > 3) {
                shortLabel = shortLabel.substring(0, Math.max(3, shortLabel.length() - 3)).trim() + "...";
            }
            int tw = fm.stringWidth(shortLabel);
            int tx = Math.max(mapRect.x + 4, Math.min(mapRect.x + mapRect.width - tw - 4, px - tw / 2));
            int ty = Math.max(mapRect.y + 14, py - 14);
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRoundRect(tx - 4, ty - 10, tw + 8, 14, 8, 8);
            g2.setColor(withAlpha(accent, 180));
            g2.drawRoundRect(tx - 4, ty - 10, tw + 8, 14, 8, 8);
            g2.drawString(shortLabel, tx, ty + 1);
        }

        g2.setComposite(oldComposite);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static boolean isSelectedMapMarker(GameContext ctx, String label, double x, double y) {
        if (ctx == null || ctx.ui == null) return false;
        if (CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            if (CampaignSystem.hasSelectedCampaignContactTarget(ctx)
                    && Double.isFinite(ctx.ui.selectedCampaignContactX)
                    && Double.isFinite(ctx.ui.selectedCampaignContactY)
                    && Math.hypot(ctx.ui.selectedCampaignContactX - x, ctx.ui.selectedCampaignContactY - y) <= 180.0) {
                return true;
            }
            if (label != null
                    && !label.isBlank()
                    && ctx.ui.selectedCampaignContactLabel != null
                    && !ctx.ui.selectedCampaignContactLabel.isBlank()
                    && label.equalsIgnoreCase(ctx.ui.selectedCampaignContactLabel)) {
                return true;
            }
            return false;
        }
        if (!Double.isFinite(ctx.ui.tacticalMapSelectionX) || !Double.isFinite(ctx.ui.tacticalMapSelectionY)) return false;
        if (label != null && !label.isBlank() && label.equalsIgnoreCase(ctx.ui.tacticalMapSelectionLabel)) return true;
        return Math.hypot(ctx.ui.tacticalMapSelectionX - x, ctx.ui.tacticalMapSelectionY - y) <= 140.0;
    }

    private static boolean shouldShowSupportMarkerLabel(GameContext ctx,
                                                        CampaignSystem.CampaignSupportMarker marker,
                                                        boolean selected) {
        if (marker == null) return false;
        if (!CampaignSystem.isStrategicGalaxyMapMode(ctx)) return true;
        return selected;
    }

    private static int markerPriorityBoost(int priority) {
        if (priority >= 90) return 2;
        if (priority >= 50) return 1;
        return 0;
    }

    private static Color strategicMarkerColor(CampaignSystem.CampaignObjectiveMarker marker) {
        if (marker != null && marker.faction != null
                && (marker.type == CampaignSystem.ObjectiveMarkerType.PROTECTED_ASSET
                || marker.type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET
                || marker.type == CampaignSystem.ObjectiveMarkerType.ESCORT_TARGET
                || marker.type == CampaignSystem.ObjectiveMarkerType.BOSS_TARGET)) {
            return factionMapColor(marker.faction, false, 220);
        }
        CampaignSystem.ObjectiveMarkerType type = (marker == null) ? null : marker.type;
        if (type == null) return new Color(255, 220, 166);
        return switch (type) {
            case PRIMARY_OBJECTIVE, BOSS_TARGET -> new Color(255, 220, 166);
            case NEXT_ROUTE -> new Color(132, 224, 255);
            case ESCORT_TARGET, PROTECTED_ASSET -> new Color(132, 255, 176);
            case DESTROY_TARGET -> new Color(255, 124, 118);
            case CAPTURE_ZONE -> new Color(205, 170, 255);
            case OPTIONAL_OBJECTIVE -> new Color(255, 210, 120);
        };
    }

    private static Color strategicSupportMarkerColor(CampaignSystem.CampaignSupportMarker marker) {
        if (marker != null && marker.faction != null) {
            return factionMapColor(marker.faction, false, 220);
        }
        CampaignSystem.SupportMarkerType type = (marker == null) ? null : marker.type;
        if (type == null) return new Color(150, 220, 255);
        return switch (type) {
            case ANOMALY -> new Color(167, 118, 255);
            case FACTION_CONTACT -> new Color(138, 226, 194);
            case SALVAGE -> new Color(206, 218, 232);
            case RESOURCE -> new Color(242, 208, 118);
            case HAZARD -> new Color(255, 132, 118);
            case INTEL -> new Color(126, 190, 255);
            case FORCE_BASE_DEFENSE -> new Color(255, 178, 126);
            case FORCE_PATROL -> new Color(255, 146, 132);
            case FORCE_CONVOY -> new Color(146, 218, 194);
            case FORCE_MINING -> new Color(238, 204, 112);
            case FORCE_SEARCH -> new Color(255, 118, 112);
            case FORCE_STRIKE -> new Color(255, 102, 156);
        };
    }

    private static Color strategicLandmarkColor(CampaignSystem.CampaignLandmark landmark) {
        if (landmark == null) return new Color(182, 212, 236);
        if (landmark.edgeColor != null) return withAlpha(landmark.edgeColor, 188);
        return switch (landmark.type) {
            case PLANET, STAR -> new Color(220, 230, 255);
            case RING, RELAY -> new Color(146, 210, 255);
            case FORTRESS, FRONT -> new Color(255, 182, 146);
            case CORRIDOR -> new Color(164, 222, 196);
            case COLONY -> new Color(214, 214, 190);
        };
    }

    private static int strategicSupportMarkerRadius(CampaignSystem.SupportMarkerType type) {
        if (type == CampaignSystem.SupportMarkerType.FORCE_BASE_DEFENSE
                || type == CampaignSystem.SupportMarkerType.FORCE_STRIKE) return 10;
        if (type == CampaignSystem.SupportMarkerType.FORCE_PATROL
                || type == CampaignSystem.SupportMarkerType.FORCE_CONVOY
                || type == CampaignSystem.SupportMarkerType.FORCE_MINING
                || type == CampaignSystem.SupportMarkerType.FORCE_SEARCH) return 9;
        if (type == CampaignSystem.SupportMarkerType.FACTION_CONTACT || type == CampaignSystem.SupportMarkerType.ANOMALY) return 9;
        return 8;
    }

    private static float strategicSupportMarkerAlpha(CampaignSystem.CampaignSupportMarker marker) {
        if (marker == null || marker.subtitle == null) return 1.0f;
        String subtitle = marker.subtitle.toUpperCase(Locale.US);
        if (subtitle.contains("STALE FORCE CONTACT") || subtitle.contains("LOST CONTACT")) return 0.48f;
        if (subtitle.contains("SUSPECTED FORCE CONTACT") || subtitle.contains("UNCERTAIN CONTACT")) return 0.72f;
        return 1.0f;
    }

    private static int strategicLandmarkRadius(CampaignSystem.LandmarkType type) {
        if (type == null) return 7;
        return switch (type) {
            case PLANET, STAR -> 9;
            case FORTRESS -> 8;
            default -> 7;
        };
    }

    private static int strategicMarkerFillAlpha(CampaignSystem.ObjectiveMarkerType type) {
        if (type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET) return 34;
        if (type == CampaignSystem.ObjectiveMarkerType.NEXT_ROUTE) return 26;
        return 28;
    }

    private static int strategicMarkerOuterRadius(CampaignSystem.ObjectiveMarkerType type) {
        if (type == CampaignSystem.ObjectiveMarkerType.PRIMARY_OBJECTIVE
                || type == CampaignSystem.ObjectiveMarkerType.BOSS_TARGET) return 12;
        if (type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET) return 10;
        return 11;
    }

    private static int strategicMarkerInnerRadius(CampaignSystem.ObjectiveMarkerType type) {
        if (type == CampaignSystem.ObjectiveMarkerType.PRIMARY_OBJECTIVE
                || type == CampaignSystem.ObjectiveMarkerType.BOSS_TARGET) return 5;
        return 4;
    }

    private static int strategicMarkerCrossRadius(CampaignSystem.ObjectiveMarkerType type) {
        if (type == CampaignSystem.ObjectiveMarkerType.PRIMARY_OBJECTIVE
                || type == CampaignSystem.ObjectiveMarkerType.BOSS_TARGET) return 18;
        return 15;
    }

    private static void drawStrategicMarkerCenterGlyph(Graphics2D g2,
                                                       CampaignSystem.ObjectiveMarkerType type,
                                                       int px,
                                                       int py) {
        if (g2 == null || type == null) return;
        if (type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET) {
            g2.drawLine(px - 4, py - 4, px + 4, py + 4);
            g2.drawLine(px - 4, py + 4, px + 4, py - 4);
        } else if (type == CampaignSystem.ObjectiveMarkerType.ESCORT_TARGET
                || type == CampaignSystem.ObjectiveMarkerType.PROTECTED_ASSET) {
            g2.drawRect(px - 3, py - 3, 6, 6);
        } else if (type == CampaignSystem.ObjectiveMarkerType.CAPTURE_ZONE) {
            g2.drawOval(px - 3, py - 3, 6, 6);
        } else if (type == CampaignSystem.ObjectiveMarkerType.NEXT_ROUTE) {
            g2.drawLine(px - 3, py, px + 3, py);
            g2.drawLine(px + 1, py - 2, px + 3, py);
            g2.drawLine(px + 1, py + 2, px + 3, py);
        } else {
            g2.fillOval(px - 2, py - 2, 4, 4);
        }
    }

    private static void drawStrategicSupportMarkerGlyph(Graphics2D g2,
                                                        CampaignSystem.SupportMarkerType type,
                                                        int px,
                                                        int py,
                                                        int r) {
        if (g2 == null || type == null) return;
        switch (type) {
            case ANOMALY -> {
                Polygon p = new Polygon(
                        new int[]{px, px + r, px, px - r},
                        new int[]{py - r, py, py + r, py},
                        4);
                g2.drawPolygon(p);
            }
            case FACTION_CONTACT -> {
                g2.drawOval(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px - r - 2, py, px + r + 2, py);
            }
            case SALVAGE -> {
                g2.drawRect(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px - r, py - r, px + r, py + r);
                g2.drawLine(px - r, py + r, px + r, py - r);
            }
            case RESOURCE -> {
                g2.drawOval(px - r, py - r + 1, r * 2, r * 2 - 2);
                g2.drawLine(px, py - r - 1, px, py + r + 1);
            }
            case HAZARD -> {
                g2.drawLine(px - r, py + r, px, py - r);
                g2.drawLine(px, py - r, px + r, py + r);
                g2.drawLine(px - r + 1, py + r, px + r - 1, py + r);
            }
            case INTEL -> {
                g2.drawOval(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px, py - r + 2, px, py + r - 2);
                g2.drawLine(px, py + r, px, py + r);
            }
            case FORCE_BASE_DEFENSE -> {
                g2.drawRect(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px - r, py, px + r, py);
                g2.drawLine(px, py - r, px, py + r);
            }
            case FORCE_PATROL -> {
                g2.drawOval(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px - r, py + r, px + r, py - r);
            }
            case FORCE_CONVOY -> {
                g2.drawRect(px - r, py - r / 2, r * 2, r);
                g2.drawLine(px - r, py - r - 2, px + r, py - r - 2);
                g2.drawLine(px - r, py + r + 2, px + r, py + r + 2);
            }
            case FORCE_MINING -> {
                g2.drawOval(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px - r, py + r, px + r, py - r);
                g2.drawLine(px - r / 2, py - r, px + r, py + r / 2);
            }
            case FORCE_SEARCH -> {
                g2.drawOval(px - r, py - r, r * 2, r * 2);
                g2.drawLine(px, py, px + r + 3, py - r - 3);
                g2.drawLine(px + r - 2, py - r - 3, px + r + 3, py - r - 3);
            }
            case FORCE_STRIKE -> {
                g2.drawLine(px - r, py + r, px, py - r);
                g2.drawLine(px, py - r, px + r, py + r);
                g2.drawLine(px - r, py + r, px + r, py + r);
                g2.drawLine(px, py - r - 3, px, py + r + 3);
            }
        }
    }

    private static void drawStrategicLandmarkGlyph(Graphics2D g2,
                                                   CampaignSystem.LandmarkType type,
                                                   int px,
                                                   int py,
                                                   int radius) {
        if (g2 == null || type == null) return;
        switch (type) {
            case PLANET -> g2.drawOval(px - radius / 2, py - radius / 2, radius, radius);
            case STAR -> {
                g2.drawLine(px - radius, py, px + radius, py);
                g2.drawLine(px, py - radius, px, py + radius);
                g2.drawOval(px - radius / 2, py - radius / 2, radius, radius);
            }
            case RING -> g2.drawOval(px - radius + 1, py - radius + 1, (radius - 1) * 2, (radius - 1) * 2);
            case RELAY -> g2.drawRect(px - radius / 2, py - radius / 2, radius, radius);
            case FORTRESS -> g2.drawPolygon(
                    new int[]{px, px + radius, px, px - radius},
                    new int[]{py - radius, py, py + radius, py},
                    4);
            case FRONT -> {
                g2.drawLine(px - radius, py + radius / 2, px, py - radius / 2);
                g2.drawLine(px, py - radius / 2, px + radius, py + radius / 2);
            }
            case CORRIDOR -> g2.drawLine(px - radius, py, px + radius, py);
            case COLONY -> {
                g2.drawOval(px - radius / 2, py - radius / 2, radius, radius);
                g2.drawLine(px - radius, py, px + radius, py);
            }
        }
    }

    private static Rectangle campaignSectorMapRect(Rectangle mapRect, GameContext ctx, int sector, int subzone,
                                                   double worldMinX, double worldMinY,
                                                   double worldViewW, double worldViewH) {
        if (mapRect == null || ctx == null || subzone < 0) return new Rectangle();
        double worldW = Math.max(1.0, worldViewW);
        double worldH = Math.max(1.0, worldViewH);
        double minX = CampaignSystem.missionSubzoneMinX(ctx, sector, subzone);
        double minY = CampaignSystem.missionSubzoneMinY(ctx, sector, subzone);
        double maxX = minX + CampaignSystem.missionSubzoneWidth(ctx);
        double maxY = minY + CampaignSystem.missionSubzoneHeight(ctx);
        int x0 = mapRect.x + (int) Math.round(((minX - worldMinX) / worldW) * mapRect.width);
        int y0 = mapRect.y + (int) Math.round(((minY - worldMinY) / worldH) * mapRect.height);
        int x1 = mapRect.x + (int) Math.round(((maxX - worldMinX) / worldW) * mapRect.width);
        int y1 = mapRect.y + (int) Math.round(((maxY - worldMinY) / worldH) * mapRect.height);
        return new Rectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static String campaignSectorLabel(int col, int row) {
        char rowTag = (char) ('A' + Math.max(0, row));
        return rowTag + Integer.toString(col + 1);
    }

    private static void drawStrategicFogOverlay(Graphics2D g2, Rectangle mapRect,
                                                double worldMinX, double worldMinY,
                                                double worldW, double worldH,
                                                FogOfWarSystem.State fog) {
        if (g2 == null || mapRect == null || fog == null || fog.totalCells() <= 0) return;

        java.awt.Shape oldClip = g2.getClip();
        Stroke oldStroke = g2.getStroke();
        g2.setClip(mapRect.x, mapRect.y, mapRect.width, mapRect.height);

        Color exploredFog = new Color(20, 38, 54, 84);
        Color unseenFog = new Color(6, 14, 26, 148);
        int cols = fog.cols();
        int rows = fog.rows();
        double viewWorldW = Math.max(1.0, worldW);
        double viewWorldH = Math.max(1.0, worldH);
        double cellW = Math.max(1.0, fog.cellWorldWidth());
        double cellH = Math.max(1.0, fog.cellWorldHeight());
        double worldMaxX = worldMinX + viewWorldW;
        double worldMaxY = worldMinY + viewWorldH;

        int minCol = Math.max(0, (int) Math.floor(worldMinX / cellW));
        int maxCol = Math.min(cols - 1, (int) Math.floor(Math.max(worldMinX, worldMaxX - 1.0) / cellW));
        int minRow = Math.max(0, (int) Math.floor(worldMinY / cellH));
        int maxRow = Math.min(rows - 1, (int) Math.floor(Math.max(worldMinY, worldMaxY - 1.0) / cellH));

        for (int row = minRow; row <= maxRow; row++) {
            double cellMinY = row * cellH;
            double cellMaxY = (row == rows - 1) ? Math.max(cellMinY + 1.0, worldMaxY) : (row + 1) * cellH;
            int y0 = mapRect.y + (int) Math.floor(((cellMinY - worldMinY) / viewWorldH) * mapRect.height);
            int y1 = mapRect.y + (int) Math.ceil(((cellMaxY - worldMinY) / viewWorldH) * mapRect.height);
            int h = Math.max(1, y1 - y0);

            for (int col = minCol; col <= maxCol; col++) {
                if (fog.isVisibleCell(col, row)) continue;
                double cellMinX = col * cellW;
                double cellMaxX = (col == cols - 1) ? Math.max(cellMinX + 1.0, worldMaxX) : (col + 1) * cellW;
                int x0 = mapRect.x + (int) Math.floor(((cellMinX - worldMinX) / viewWorldW) * mapRect.width);
                int x1 = mapRect.x + (int) Math.ceil(((cellMaxX - worldMinX) / viewWorldW) * mapRect.width);
                int w = Math.max(1, x1 - x0);
                boolean explored = fog.isExploredCell(col, row);
                g2.setColor(explored ? exploredFog : unseenFog);
                g2.fillRect(x0, y0, w, h);
            }
        }

        g2.setStroke(oldStroke);
        g2.setClip(oldClip);
    }

    private static void drawSensorInterestSignals(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                                  double worldMinX, double worldMinY,
                                                  double worldW, double worldH) {
        if (g2 == null || ctx == null || mapRect == null) return;
        List<FogOfWarSystem.SensorInterestSignal> signals = FogOfWarSystem.sensorInterestSignals(ctx);
        if (signals.isEmpty()) return;

        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        Composite oldComposite = g2.getComposite();
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(mapRect.x, mapRect.y, mapRect.width, mapRect.height);
        g2.setFont(new Font("Consolas", Font.BOLD, 10));

        for (FogOfWarSystem.SensorInterestSignal signal : signals) {
            if (signal == null) continue;
            if (signal.x < worldMinX || signal.x > worldMinX + worldW
                    || signal.y < worldMinY || signal.y > worldMinY + worldH) {
                continue;
            }
            int px = mapRect.x + (int) Math.round(((signal.x - worldMinX) / Math.max(1.0, worldW)) * mapRect.width);
            int py = mapRect.y + (int) Math.round(((signal.y - worldMinY) / Math.max(1.0, worldH)) * mapRect.height);
            Color color = sensorInterestColor(signal.kind);
            int alpha = MathUtil.clamp((int) Math.round(120 + signal.strength * 105), 0, 235);
            int radius = MathUtil.clamp((int) Math.round(4 + signal.strength * 5), 4, 9);
            int uncertainty = MathUtil.clamp((int) Math.round((signal.uncertaintyRadius / Math.max(1.0, worldW)) * mapRect.width),
                    radius + 7, 34);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f));
            g2.setColor(withAlpha(color, Math.min(160, alpha)));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(px - uncertainty, py - uncertainty, uncertainty * 2, uncertainty * 2);
            g2.drawOval(px - uncertainty / 2, py - uncertainty / 2, uncertainty, uncertainty);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
            g2.setColor(withAlpha(color, alpha));
            drawSensorInterestGlyph(g2, signal.kind, px, py, radius);

            if (signal.strength >= 0.54) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
                g2.setColor(new Color(218, 236, 255, 185));
                String text = sensorInterestShortLabel(signal.kind);
                g2.drawString(text, px + radius + 4, py - radius - 2);
            }
        }

        g2.setComposite(oldComposite);
        g2.setClip(oldClip);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static void drawCampaignStrikeCinematic(Graphics2D g2, GameContext ctx, Rectangle mapRect,
                                                    double worldMinX, double worldMinY,
                                                    double worldW, double worldH) {
        if (g2 == null || ctx == null || ctx.campaign == null || mapRect == null || !ctx.campaign.strikeCinematicActive) return;
        CampaignSystem.CampaignState st = ctx.campaign;
        double total = 3.9;
        double launchEnd = 0.65;
        double transitEnd = 2.45;
        double impactEnd = 3.2;
        double timer = Math.max(0.0, st.strikeCinematicTimer);
        if (!Double.isFinite(st.strikeCinematicSourceX) || !Double.isFinite(st.strikeCinematicSourceY)
                || !Double.isFinite(st.strikeCinematicTargetX) || !Double.isFinite(st.strikeCinematicTargetY)) {
            return;
        }

        java.util.function.BiFunction<Double, Double, Point> w2m = (wx, wy) -> new Point(
                mapRect.x + (int) Math.round(((wx - worldMinX) / Math.max(1.0, worldW)) * mapRect.width),
                mapRect.y + (int) Math.round(((wy - worldMinY) / Math.max(1.0, worldH)) * mapRect.height));
        Point source = w2m.apply(st.strikeCinematicSourceX, st.strikeCinematicSourceY);
        Point target = w2m.apply(st.strikeCinematicTargetX, st.strikeCinematicTargetY);
        Point payload = w2m.apply(
                Double.isFinite(st.strikeCinematicPayloadX) ? st.strikeCinematicPayloadX : st.strikeCinematicSourceX,
                Double.isFinite(st.strikeCinematicPayloadY) ? st.strikeCinematicPayloadY : st.strikeCinematicSourceY);

        Composite oldComposite = g2.getComposite();
        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();
        Color strikeColor = st.strikeCinematicAtomic ? new Color(255, 170, 96)
                : ("SORTIE".equalsIgnoreCase(st.strikeCinematicType) ? new Color(140, 228, 255) : new Color(255, 212, 126));
        float fade = (float) MathUtil.clamp(1.0 - Math.max(0.0, timer - impactEnd) / Math.max(0.2, total - impactEnd), 0.18, 1.0);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));

        if (timer <= launchEnd) {
            int pulse = 16 + (int) Math.round((timer / launchEnd) * 26.0);
            g2.setColor(withAlpha(strikeColor, 210));
            g2.setStroke(new BasicStroke(2.4f));
            g2.drawOval(source.x - pulse, source.y - pulse, pulse * 2, pulse * 2);
            g2.drawOval(source.x - pulse / 2, source.y - pulse / 2, pulse, pulse);
        } else {
            g2.setColor(withAlpha(strikeColor, 170));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(source.x, source.y, payload.x, payload.y);
            g2.setColor(withAlpha(strikeColor, 240));
            g2.fillOval(payload.x - 5, payload.y - 5, 10, 10);
            g2.drawOval(payload.x - 9, payload.y - 9, 18, 18);
        }

        if (timer >= transitEnd - 0.35) {
            double orbit = Math.max(10.0, 24.0 + Math.sin(timer * 3.4) * 5.0);
            g2.setStroke(new BasicStroke(1.8f));
            for (int i = 0; i < 4; i++) {
                double ang = timer * 1.8 + i * (Math.PI / 2.0);
                int sx = target.x + (int) Math.round(Math.cos(ang) * orbit * (i < 2 ? 1.2 : 0.8));
                int sy = target.y + (int) Math.round(Math.sin(ang) * orbit);
                Polygon hull = new Polygon(
                        new int[]{sx, sx + 7, sx, sx - 7},
                        new int[]{sy - 10, sy, sy + 10, sy},
                        4);
                g2.setColor(withAlpha(new Color(220, 236, 255), (timer >= impactEnd) ? 72 : 148));
                g2.drawPolygon(hull);
            }
        }

        if (timer >= transitEnd) {
            double impactT = MathUtil.clamp((timer - transitEnd) / Math.max(1e-6, total - transitEnd), 0.0, 1.0);
            int blast = Math.max(16, (int) Math.round((st.strikeCinematicBlastRadius / Math.max(1.0, worldW)) * mapRect.width));
            blast = (int) Math.round(blast * (0.45 + impactT * (st.strikeCinematicAtomic ? 2.2 : 1.2)));
            g2.setColor(withAlpha(strikeColor, st.strikeCinematicAtomic ? 112 : 88));
            g2.fillOval(target.x - blast, target.y - blast, blast * 2, blast * 2);
            g2.setColor(withAlpha(new Color(255, 246, 214), 236));
            g2.drawOval(target.x - blast, target.y - blast, blast * 2, blast * 2);
            int victimW = (st.strikeCinematicAtomic || st.strikeCinematicDamageScale >= 0.6) ? 18 : 12;
            int victimH = (st.strikeCinematicAtomic || st.strikeCinematicDamageScale >= 0.6) ? 28 : 18;
            g2.setColor(withAlpha(new Color(255, 248, 228), 240));
            g2.fillOval(target.x - 4, target.y - 4, 8, 8);
            g2.drawLine(target.x, target.y, target.x + victimW, target.y - victimH);
            g2.drawLine(target.x, target.y, target.x - victimW / 2, target.y + victimH / 2);
            if (st.strikeCinematicDestroyedTarget) {
                g2.setColor(withAlpha(new Color(255, 136, 124), 228));
                g2.drawLine(target.x - victimW, target.y - victimH / 2, target.x + victimW, target.y + victimH / 2);
                g2.drawLine(target.x - victimW, target.y + victimH / 2, target.x + victimW, target.y - victimH / 2);
            }
        }

        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(withAlpha(new Color(244, 248, 255), 230));
        g2.drawString(st.strikeCinematicType.toUpperCase(java.util.Locale.US) + " LAUNCH", mapRect.x + 16, mapRect.y + 20);
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        String detail = st.strikeCinematicTargetLabel.toUpperCase(java.util.Locale.US)
                + (st.strikeCinematicAtomic ? "  |  BLAST RADIUS WIDE" : "  |  CAPITAL-BREAKER IMPACT");
        g2.drawString(detail, mapRect.x + 16, mapRect.y + 34);

        g2.setComposite(oldComposite);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private static Color sensorInterestColor(FogOfWarSystem.SensorInterestKind kind) {
        if (kind == null) return new Color(128, 218, 255);
        return switch (kind) {
            case ORE_VEIN -> new Color(255, 204, 92);
            case WRECKAGE -> new Color(206, 218, 232);
            case CACHE -> new Color(242, 208, 118);
            case CONTACT -> new Color(138, 226, 194);
            case HAZARD -> new Color(255, 132, 118);
            case INTEL -> new Color(126, 190, 255);
            case FLEET_ASSET -> new Color(198, 244, 154);
            case INSTALLATION -> new Color(255, 126, 106);
            case MASS_SIGNATURE -> new Color(155, 232, 255);
            case ANOMALY -> new Color(167, 118, 255);
        };
    }

    private static String sensorInterestShortLabel(FogOfWarSystem.SensorInterestKind kind) {
        if (kind == null) return "SIG";
        return switch (kind) {
            case ORE_VEIN -> "ORE";
            case WRECKAGE -> "WRK";
            case CACHE -> "CACHE";
            case CONTACT -> "CNT";
            case HAZARD -> "HAZ";
            case INTEL -> "INT";
            case FLEET_ASSET -> "AST";
            case INSTALLATION -> "SITE";
            case MASS_SIGNATURE -> "MASS";
            case ANOMALY -> "ANOM";
        };
    }

    private static void drawSensorInterestGlyph(Graphics2D g2, FogOfWarSystem.SensorInterestKind kind, int x, int y, int r) {
        if (kind == FogOfWarSystem.SensorInterestKind.ORE_VEIN) {
            Polygon p = new Polygon(
                    new int[]{x, x + r, x + Math.max(2, r / 2), x - Math.max(2, r / 2), x - r},
                    new int[]{y - r, y - 2, y + r, y + r, y - 2},
                    5);
            g2.fillPolygon(p);
        } else if (kind == FogOfWarSystem.SensorInterestKind.WRECKAGE) {
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x - r, y - r, x + r, y + r);
            g2.drawLine(x - r, y + r, x + r, y - r);
            g2.fillOval(x - 2, y - 2, 4, 4);
        } else if (kind == FogOfWarSystem.SensorInterestKind.INSTALLATION) {
            g2.fillRect(x - r, y - r, r * 2, r * 2);
            g2.setColor(new Color(0, 0, 0, 130));
            g2.drawLine(x - r, y, x + r, y);
            g2.drawLine(x, y - r, x, y + r);
        } else if (kind == FogOfWarSystem.SensorInterestKind.ANOMALY) {
            Polygon p = new Polygon(
                    new int[]{x, x + r, x, x - r},
                    new int[]{y - r, y, y + r, y},
                    4);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolygon(p);
            g2.drawLine(x - Math.max(2, r / 2), y, x + Math.max(2, r / 2), y);
            g2.drawLine(x, y - Math.max(2, r / 2), x, y + Math.max(2, r / 2));
        } else {
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawOval(x - r, y - r, r * 2, r * 2);
            g2.fillOval(x - 2, y - 2, 4, 4);
        }
    }

    public static void drawCombatFogOverlay(Graphics2D g2, int worldW, int worldH, FogOfWarSystem.State fog,
                                            double minX, double minY, double maxX, double maxY,
                                            boolean tacticalView) {
        if (g2 == null || fog == null || fog.totalCells() <= 0 || worldW <= 0 || worldH <= 0) return;

        Color exploredFog = tacticalView ? new Color(12, 24, 38, 110) : new Color(16, 26, 38, 122);
        Color unseenFog = tacticalView ? new Color(0, 0, 0, 186) : new Color(0, 0, 0, 210);
        int cols = fog.cols();
        int rows = fog.rows();
        double cellW = Math.max(1.0, fog.cellWorldWidth());
        double cellH = Math.max(1.0, fog.cellWorldHeight());

        int minCol = Math.max(0, (int) Math.floor(Math.max(0.0, minX - cellW) / cellW));
        int maxCol = Math.min(cols - 1, (int) Math.floor(Math.min(worldW - 1.0, maxX + cellW) / cellW));
        int minRow = Math.max(0, (int) Math.floor(Math.max(0.0, minY - cellH) / cellH));
        int maxRow = Math.min(rows - 1, (int) Math.floor(Math.min(worldH - 1.0, maxY + cellH) / cellH));

        for (int row = minRow; row <= maxRow; row++) {
            double y0 = row * cellH;
            double y1 = (row == rows - 1) ? worldH : (row + 1) * cellH;
            int drawY = (int) Math.floor(y0);
            int drawH = Math.max(1, (int) Math.ceil(y1 - y0));
            for (int col = minCol; col <= maxCol; col++) {
                if (fog.isVisibleCell(col, row)) continue;
                double x0 = col * cellW;
                double x1 = (col == cols - 1) ? worldW : (col + 1) * cellW;
                int drawX = (int) Math.floor(x0);
                int drawW = Math.max(1, (int) Math.ceil(x1 - x0));
                g2.setColor(fog.isExploredCell(col, row) ? exploredFog : unseenFog);
                g2.fillRect(drawX, drawY, drawW, drawH);
            }
        }
    }


    // IMPORTANT: This is the method that was likely stubbed/empty in your current project.
    public static void drawShip(Graphics2D g2, Ship ship) {
        ShipRenderer.drawShip(g2, ship);
    }

    private static void drawEcmIllusions(Graphics2D g2, Ship ship) {
        // ECM keeps its gameplay effect, but the in-world distortion visuals are retired
        // so fleets stay readable under pressure.
    }

    private static void drawTacticalAsteroid(Graphics2D g2, Asteroid a) {
        if (g2 == null || a == null) return;
        int r = Math.max(4, (int) Math.round(a.radius));
        int x = (int) Math.round(a.x);
        int y = (int) Math.round(a.y);
        double frac = (a.oreMax <= 0) ? 0.0 : MathUtil.clamp((double) a.ore / (double) a.oreMax, 0.0, 1.0);
        int shade = (int) Math.round(72 + 70 * (0.25 + 0.75 * frac));
        g2.setColor(new Color(shade, shade, shade, 175));
        g2.fillOval(x - r, y - r, r * 2, r * 2);
        g2.setColor(new Color(210, 220, 230, 70));
        g2.drawOval(x - r, y - r, r * 2, r * 2);
        if (a.ore > 0) {
            int ir = Math.max(4, (int) Math.round(r * 0.48));
            g2.setColor(new Color(255, 214, 132, MathUtil.clamp((int) Math.round(42 + 110 * frac), 0, 185)));
            g2.drawOval(x - ir, y - ir, ir * 2, ir * 2);
        }
        if (a.rich) {
            int rr = (int) Math.round(r * 1.28);
            g2.setColor(new Color(255, 230, 165, 64));
            g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
        }
    }

    private static Ship findShipById(List<Ship> ships, int shipId) {
        if (ships == null || ships.isEmpty() || shipId <= 0) return null;
        for (Ship ship : ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }

    private static Turret findTurretForEnergyBolt(Ship ship, EnergyBolt eb) {
        if (ship == null || eb == null || ship.turrets == null || ship.turrets.isEmpty()) return null;
        if (!Double.isFinite(eb.sourceTurretLocalX) || !Double.isFinite(eb.sourceTurretLocalY)) return null;
        for (Turret turret : ship.turrets) {
            if (turret == null) continue;
            if (Math.abs(turret.localX - eb.sourceTurretLocalX) > 1e-6) continue;
            if (Math.abs(turret.localY - eb.sourceTurretLocalY) > 1e-6) continue;
            return turret;
        }
        return null;
    }

    private static void drawEnergyBolt(Graphics2D g2, EnergyBolt eb, boolean tactical) {
        drawEnergyBolt(g2, eb, tactical, null, null, null);
    }

    private static void drawEnergyBolt(Graphics2D g2, EnergyBolt eb, boolean tactical,
                                       List<Ship> ships, FogOfWarSystem.State fog, Faction perspective) {
        if (g2 == null || eb == null || !eb.alive) return;

        boolean beamBolt = eb.isBeamBolt();
        if (!beamBolt) {
            BufferedImage skin = ProjectileSkinLibrary.getEnergyBoltSkin(false);
            if (skin != null) {
                // Restore a cleaner "bolt projectile" look for the lighter ENERGY_BOLT family (especially important
                // for fighter-heavy fleet battles where the braided beam visuals become noise).
                double ux = Math.cos(eb.angle);
                double uy = Math.sin(eb.angle);
                double speed = Math.hypot(eb.vx, eb.vy);
                double trailLen = Math.max(6.0, Math.min(14.0, 6.0 + speed * 3.2));
                if (tactical) trailLen *= 0.68;

                Color core = projectileCoreColor(eb.faction);
                Color hot = mixColor(core, Color.WHITE, 0.34);
                int x = (int) Math.round(eb.x);
                int y = (int) Math.round(eb.y);

                Stroke old = g2.getStroke();
                try {
                    g2.setStroke(new BasicStroke((float) Math.max(0.9, eb.radius * 0.42), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(withAlpha(core, tactical ? 102 : 142));
                    g2.drawLine(x, y,
                            (int) Math.round(eb.x - ux * trailLen),
                            (int) Math.round(eb.y - uy * trailLen));

                    int glowR = Math.max(1, (int) Math.round(eb.radius * (tactical ? 0.62 : 0.78)));
                    g2.setColor(withAlpha(hot, tactical ? 140 : 176));
                    g2.fillOval(x - glowR, y - glowR, glowR * 2, glowR * 2);
                } finally {
                    g2.setStroke(old);
                }

                double boltLen = Math.max(10.0, eb.radius * 3.2) * (tactical ? 0.72 : 0.92);
                double boltW = Math.max(3.0, eb.radius * 1.35) * (tactical ? 0.68 : 0.82);
                drawOrientedProjectileSkin(g2, skin, eb.x, eb.y, eb.angle, boltLen, boltW, tactical ? 0.72f : 0.84f);
                return;
            }
        }

        double sx = eb.spawnX;
        double sy = eb.spawnY;
        double originAngle = eb.angle;

        Ship sourceShip = findShipById(ships, eb.sourceShipId);
        if (FogOfWarSystem.isVisibleToPerspective(fog, perspective, sourceShip)) {
            Turret sourceTurret = findTurretForEnergyBolt(sourceShip, eb);
            if (sourceTurret != null) {
                sx = sourceTurret.worldX(sourceShip) + Math.cos(sourceTurret.angle) * (sourceTurret.radius + 4.0);
                sy = sourceTurret.worldY(sourceShip) + Math.sin(sourceTurret.angle) * (sourceTurret.radius + 4.0);
                originAngle = sourceTurret.angle;
            } else {
                sx = sourceShip.x + Math.cos(sourceShip.angle) * Math.max(sourceShip.radius, 1.0);
                sy = sourceShip.y + Math.sin(sourceShip.angle) * Math.max(sourceShip.radius, 1.0);
                originAngle = sourceShip.angle;
            }
        }

        double ex = eb.x;
        double ey = eb.y;
        if (beamBolt && eb.ageFrames() >= BEAM_BOLT_TURRET_TETHER_FRAMES) {
            double trailLen = Math.max(tactical ? 14.0 : 18.0, eb.radius * (tactical ? 2.6 : 3.2));
            sx = ex - Math.cos(eb.angle) * trailLen;
            sy = ey - Math.sin(eb.angle) * trailLen;
            originAngle = eb.angle;
        }
        double dx = ex - sx;
        double dy = ey - sy;
        double len = Math.hypot(dx, dy);
        double pathAngle = (len > 1e-6) ? Math.atan2(dy, dx) : originAngle;
        double dirX = Math.cos(pathAngle);
        double dirY = Math.sin(pathAngle);
        if (len < 2.0) {
            double lead = eb.isBeamBolt()
                    ? (tactical ? 11.0 : 15.0)
                    : (tactical ? 8.0 : 11.0);
            ex = sx + dirX * lead;
            ey = sy + dirY * lead;
            dx = ex - sx;
            dy = ey - sy;
            len = Math.hypot(dx, dy);
            pathAngle = (len > 1e-6) ? Math.atan2(dy, dx) : originAngle;
            dirX = Math.cos(pathAngle);
            dirY = Math.sin(pathAngle);
        }

        double perpX = -dirY;
        double perpY = dirX;

        Color base = mixColor(beamColorForFaction(eb.faction), new Color(168, 242, 255), beamBolt ? 0.58 : 0.42);
        Color hot = mixColor(base, Color.WHITE, beamBolt ? 0.80 : 0.70);
        Color spark = mixColor(hot, Color.WHITE, 0.42);

        double time = System.nanoTime() * 1e-9;
        double pulse = 0.5 + 0.5 * Math.sin(time * (beamBolt ? 8.8 : 7.2) + len * 0.012);
        double phaseBase = time * (beamBolt ? 5.6 : 4.8) + len * 0.007;
        double widthScale = tactical ? 0.72 : 1.0;
        double coreWidth = Math.max(tactical ? 0.9 : 1.2, eb.radius * (beamBolt ? 0.36 : 0.28) * widthScale);
        double gap = Math.max(1.4, coreWidth * (beamBolt ? 0.96 : 0.82));
        double twist = Math.max(0.9, coreWidth * (beamBolt ? 0.74 : 0.52));
        int segments = tactical ? 8 : 16;
        double[] laneSeeds = beamLaneSeeds(eb);
        boolean combinedBeam = eb.usesCombinedBeamVisual();

        Stroke old = g2.getStroke();
        try {
            if (combinedBeam) {
                g2.setStroke(new BasicStroke((float) Math.max(1.4, coreWidth * (beamBolt ? 1.55 : 1.35)),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(base, tactical ? 44 : (beamBolt ? 62 : 54)));
                g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

                g2.setStroke(new BasicStroke((float) Math.max(1.0, coreWidth * 0.82),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(hot, tactical ? 142 : (beamBolt ? 206 : 186)));
                g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

                g2.setStroke(new BasicStroke((float) Math.max(0.8, coreWidth * 0.42),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(Color.WHITE, tactical ? 154 : (beamBolt ? 210 : 186)));
                g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));
            } else {
                double lane = laneSeeds[0];
                double laneOffset = lane * gap;
                double laneSx = sx + perpX * laneOffset;
                double laneSy = sy + perpY * laneOffset;
                double laneEx = ex + perpX * laneOffset;
                double laneEy = ey + perpY * laneOffset;

                g2.setStroke(new BasicStroke((float) Math.max(1.4, coreWidth * 1.28),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(base, tactical ? 78 : 104));
                g2.drawLine((int) Math.round(laneSx), (int) Math.round(laneSy), (int) Math.round(laneEx), (int) Math.round(laneEy));

                g2.setStroke(new BasicStroke((float) Math.max(0.95, coreWidth * 0.66),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(hot, tactical ? 176 : 228));
                g2.drawLine((int) Math.round(laneSx), (int) Math.round(laneSy), (int) Math.round(laneEx), (int) Math.round(laneEy));

                g2.setStroke(new BasicStroke((float) Math.max(0.65, coreWidth * 0.30),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(Color.WHITE, tactical ? 186 : 236));
                g2.drawLine((int) Math.round(laneSx), (int) Math.round(laneSy), (int) Math.round(laneEx), (int) Math.round(laneEy));
            }

            for (int laneIndex = 0; laneIndex < laneSeeds.length; laneIndex++) {
                double lane = laneSeeds[laneIndex];
                double prevX = sx + perpX * lane * gap;
                double prevY = sy + perpY * lane * gap;

                for (int i = 1; i <= segments; i++) {
                    double u = (double) i / segments;
                    double launchEase = MathUtil.clamp(u / 0.20, 0.0, 1.0);
                    double taper = Math.pow(1.0 - u, beamBolt ? 1.20 : 1.38);
                    double swirl = combinedBeam
                            ? Math.sin(u * (beamBolt ? 11.8 : 9.0) + phaseBase + laneIndex * (Math.PI * 2.0 / 3.0))
                            * twist * taper * launchEase
                            : 0.0;
                    double offset = (lane * gap + swirl) * taper;
                    double px = sx + dx * u + perpX * offset;
                    double py = sy + dy * u + perpY * offset;

                    float outerWidth = (float) Math.max(1.0, coreWidth * (0.78 + 0.24 * taper));
                    float innerWidth = (float) Math.max(0.7, coreWidth * 0.40);
                    int outerAlpha = MathUtil.clamp((int) Math.round((beamBolt ? 112 : 90) + taper * (tactical ? 40 : 72)), 0, 255);
                    int innerAlpha = MathUtil.clamp((int) Math.round((beamBolt ? 206 : 176) + taper * (tactical ? 20 : 46)), 0, 255);

                    g2.setStroke(new BasicStroke(outerWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(withAlpha(base, outerAlpha));
                    g2.drawLine((int) Math.round(prevX), (int) Math.round(prevY),
                            (int) Math.round(px), (int) Math.round(py));

                    g2.setStroke(new BasicStroke(innerWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(withAlpha(hot, innerAlpha));
                    g2.drawLine((int) Math.round(prevX), (int) Math.round(prevY),
                            (int) Math.round(px), (int) Math.round(py));

                    prevX = px;
                    prevY = py;
                }
            }

            int muzzleR = Math.max(2, (int) Math.round(coreWidth * (beamBolt ? 0.66 : 0.58)));
            double muzzleGap = gap * 1.12;
            for (double lane : laneSeeds) {
                int mx = (int) Math.round(sx + perpX * lane * muzzleGap);
                int my = (int) Math.round(sy + perpY * lane * muzzleGap);
                g2.setColor(withAlpha(hot, tactical ? 132 : (beamBolt ? 192 : 160)));
                g2.fillOval(mx - muzzleR, my - muzzleR, muzzleR * 2, muzzleR * 2);
            }

            int headR = Math.max(2, (int) Math.round(coreWidth * (beamBolt ? 1.30 : 1.12) * (0.88 + pulse * 0.12)));
            int headX = (int) Math.round(ex);
            int headY = (int) Math.round(ey);
            g2.setColor(withAlpha(hot, tactical ? 162 : (beamBolt ? 236 : 210)));
            g2.fillOval(headX - headR, headY - headR, headR * 2, headR * 2);

            int sparkR = Math.max(1, headR / 2);
            g2.setColor(withAlpha(spark, tactical ? 182 : (beamBolt ? 230 : 210)));
            g2.fillOval(headX - sparkR, headY - sparkR, sparkR * 2, sparkR * 2);

            if (beamBolt) {
                BufferedImage headSkin = combinedBeam
                        ? ProjectileSkinLibrary.getEnergyBoltSkin(true)
                        : ProjectileSkinLibrary.getBeamBoltSingleSkin();
                if (headSkin != null) {
                    double boltLen = Math.max(18.0, eb.radius * 4.1) * (tactical ? 0.76 : 0.92);
                    double boltW = Math.max(combinedBeam ? 7.0 : 5.0,
                            eb.radius * (combinedBeam ? 2.0 : 1.4)) * (tactical ? 0.72 : 0.88);
                    drawOrientedProjectileSkin(g2, headSkin, ex, ey, pathAngle, boltLen, boltW, tactical ? 0.76f : 0.86f);
                }
            }
        } finally {
            g2.setStroke(old);
        }
    }

    private static double[] beamLaneSeeds(EnergyBolt eb) {
        if (eb == null) return new double[]{0.0};
        int laneCount = Math.max(1, eb.beamLaneCount);
        if (eb.usesCombinedBeamVisual()) {
            return centeredLaneSeeds(laneCount);
        }
        int laneIndex = Math.floorMod(eb.beamLaneIndex, laneCount);
        double[] centered = centeredLaneSeeds(laneCount);
        return new double[]{centered[Math.min(centered.length - 1, laneIndex)]};
    }

    private static double[] centeredLaneSeeds(int laneCount) {
        int count = Math.max(1, laneCount);
        if (count == 1) return new double[]{0.0};
        double[] seeds = new double[count];
        double mid = (count - 1) * 0.5;
        for (int i = 0; i < count; i++) {
            seeds[i] = i - mid;
        }
        return seeds;
    }

    private static void drawTacticalProjectile(Graphics2D g2, Projectile p) {
        drawTacticalProjectile(g2, p, null, null, null);
    }

    private static void drawTacticalProjectile(Graphics2D g2, Projectile p,
                                               List<Ship> ships, FogOfWarSystem.State fog, Faction perspective) {
        if (g2 == null || p == null) return;
        if (p instanceof PhaserBeam beam) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke((float) Math.max(1.4, beam.width * 0.42), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(withAlpha(beamColorForFaction(beam.faction), 210));
            g2.drawLine((int) Math.round(beam.startX()), (int) Math.round(beam.startY()),
                    (int) Math.round(beam.endX()), (int) Math.round(beam.endY()));
            g2.setStroke(old);
            return;
        }
        if (p instanceof PointDefenseLaser laser) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke((float) Math.max(1.1, laser.width * 0.45), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(withAlpha(mixColor(beamColorForFaction(laser.faction), Color.WHITE, 0.30), 220));
            g2.drawLine((int) Math.round(laser.startX()), (int) Math.round(laser.startY()),
                    (int) Math.round(laser.endX), (int) Math.round(laser.endY));
            g2.setStroke(old);
            return;
        }
        if (p instanceof EnergyBolt eb) {
            drawEnergyBolt(g2, eb, true, ships, fog, perspective);
            return;
        }

        double ux;
        double uy;
        double len = Math.hypot(p.vx, p.vy);
        if (len > 1e-6) {
            ux = p.vx / len;
            uy = p.vy / len;
        } else if (p instanceof Missile m) {
            ux = Math.cos(m.angle);
            uy = Math.sin(m.angle);
        } else if (p instanceof SuperweaponShot ws) {
            ux = Math.cos(ws.angle);
            uy = Math.sin(ws.angle);
        } else if (p instanceof EnergyBolt eb) {
            ux = Math.cos(eb.angle);
            uy = Math.sin(eb.angle);
        } else if (p instanceof DisruptorSlug slug) {
            ux = Math.cos(slug.angle);
            uy = Math.sin(slug.angle);
        } else if (p instanceof DestabilizerPulse pulse) {
            ux = Math.cos(pulse.angle);
            uy = Math.sin(pulse.angle);
        } else {
            ux = 1.0;
            uy = 0.0;
        }

        Color core = (p instanceof SuperweaponShot)
                ? mixColor(beamColorForFaction(p.faction), Color.WHITE, 0.20)
                : projectileCoreColor(p.faction);
        int x = (int) Math.round(p.x);
        int y = (int) Math.round(p.y);
        int r = Math.max(2, (int) Math.round(p.radius * ((p instanceof SuperweaponShot) ? 0.85 : 0.65)));
        double trailLen = Math.max(8.0, Math.min(30.0, p.radius * ((p instanceof SuperweaponShot) ? 4.8 : 3.0) + 8.0));
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke((float) Math.max(1.1, r * 0.8), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(core, (p instanceof SuperweaponShot) ? 220 : 190));
        g2.drawLine(x, y,
                (int) Math.round(p.x - ux * trailLen),
                (int) Math.round(p.y - uy * trailLen));
        g2.setStroke(old);
        g2.setColor(withAlpha(mixColor(core, Color.WHITE, 0.22), (p instanceof SuperweaponShot) ? 236 : 214));
        g2.fillOval(x - r, y - r, r * 2, r * 2);
    }

    private static void drawTacticalShip(Graphics2D g2, Ship ship) {
        if (g2 == null || ship == null || !ship.alive) return;
        boolean multipartDying = ship.dying && ShipPartLibrary.hasDestroyedParts(ship.role, ship.faction);
        int wx = (int) Math.round(ship.x);
        int wy = (int) Math.round(ship.y);
        if (multipartDying) {
            if (!isTinyStrikeCraft(ship.role)) {
                g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                g2.setColor(new Color(255, 255, 255, 92));
                g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
            }
            return;
        }

        Color hull = mixColor(factionHullColor(ship.faction), Color.BLACK, 0.18);
        Color trim = mixColor(factionTrimColor(ship.faction), Color.WHITE, 0.08);
        Graphics2D g = (Graphics2D) g2.create();
        g.translate(wx, wy);
        g.rotate(ship.angle);
        double roleScale = ShipRenderer.roleVisualScale(ship.role);
        if (Math.abs(roleScale - 1.0) > 1e-6) {
            g.scale(roleScale, roleScale);
        }

        double sig = ship.effectiveSignature();
        if (ship.isCloaked() && sig < 0.99) {
            float a = (float) (0.22 + 0.78 * sig);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }

        ShipVisual visual = ShipRenderer.getVisual(ship);
        for (Polygon poly : visual.hullPolys) {
            g.setColor(withAlpha(hull, 188));
            g.fillPolygon(poly);
        }
        for (Polygon poly : visual.superPolys) {
            g.setColor(withAlpha(trim, 58));
            g.fillPolygon(poly);
        }

        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke((float) Math.max(1.0, Math.min(2.5, ship.radius * 0.04)),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(withAlpha(trim, 228));
        for (Polygon poly : visual.hullPolys) {
            g.drawPolygon(poly);
        }
        g.setColor(withAlpha(trim, 140));
        for (Polygon poly : visual.superPolys) {
            g.drawPolygon(poly);
        }
        for (Polygon poly : visual.fins) {
            g.drawPolygon(poly);
        }
        g.setStroke(old);

        double shieldMax = ship.effectiveShieldCapacityMax();
        if (ship.shieldActive && shieldMax > 0.0 && ship.shield > 0.0) {
            int sr = Math.max(8, (int) Math.round((ship.radius + 12.0) * 1.08));
            int alpha = MathUtil.clamp((int) Math.round(48 + 118 * (ship.shield / shieldMax)), 0, 190);
            g.setColor(new Color(120, 220, 255, alpha));
            g.drawOval(-sr, -sr, sr * 2, sr * 2);
        }
        g.dispose();

        if (!isTinyStrikeCraft(ship.role)) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            g2.setColor(new Color(235, 240, 255, 112));
            g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
        }
    }

    /**
     * Modular ship visual pipeline:
     * - Role-based local-coordinate silhouettes
     * - Deterministic panel/window greebles
     * - Engine cones and hardpoint mounts
     */
    private static final class ShipRenderer {
        private static final Map<String, ShipVisual> CACHE = new HashMap<>();

        static void drawShip(Graphics2D g2, Ship ship) {
            drawShip(g2, ship, true, true, true);
        }

        static void drawShip(Graphics2D g2, Ship ship, boolean fullDamageFx, boolean drawName) {
            drawShip(g2, ship, fullDamageFx, drawName, true);
        }

        static void drawShip(Graphics2D g2, Ship ship, boolean fullDamageFx, boolean drawName, boolean drawEnergyFx) {
            if (!ship.alive) return;
            boolean multipartDying = ship.dying && ShipPartLibrary.hasDestroyedParts(ship.role, ship.faction);
            if (multipartDying) {
                int wx = (int) Math.round(ship.x);
                int wy = (int) Math.round(ship.y);
                if (drawName && !isTinyStrikeCraft(ship.role)) {
                    g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                    g2.setColor(new Color(255, 255, 255, 120));
                    g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
                }
                return;
            }

            Color hull;
            Color trim;
            hull = factionHullColor(ship.faction);
            trim = factionTrimColor(ship.faction);

            int wx = (int) Math.round(ship.x);
            int wy = (int) Math.round(ship.y);

            Graphics2D g = (Graphics2D) g2.create();
            g.translate(wx, wy);
            g.rotate(ship.angle);
            double roleScale = roleVisualScale(ship.role);
            if (Math.abs(roleScale - 1.0) > 1e-6) {
                g.scale(roleScale, roleScale);
            }

            double sig = ship.effectiveSignature();
            if (ship.isCloaked() && sig < 0.99) {
                float a = (float) (0.22 + 0.78 * sig);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            }

            ShipVisual visual = getVisual(ship);
            Area hullArea = visual.hullArea;
            ShipSkinSet skinSet = ShipSkinLibrary.getSkinSet(ship.role, ship.faction);
            boolean hasAlbedoSkin = skinSet != null && skinSet.hasAlbedo();

            if (!hasAlbedoSkin) {
                drawHullShadow(g, visual);
                drawHullAndSuper(g, visual, hull, trim);
            }
            drawHullSkin(g, ship, visual, hullArea, hull, trim, skinSet);
            drawPanelsAndWindows(g, ship, visual, hullArea, hasAlbedoSkin);
            drawEngines(g, ship, visual);
            drawHardpoints(g, ship, visual);

            if (drawEnergyFx) {
                drawShipShieldFaces(g, ship, hullArea, visual);
            }

            if (hullArea != null && fullDamageFx) {
                drawDamageDecals(g, ship, hullArea);
            }
            if (drawEnergyFx && hullArea != null) {
                drawWarpChargeHullFx(g, ship, hullArea, visual);
            }

            if (fullDamageFx && DevTools.isDebugOverlay()) {
                drawRoomDebugOverlay(g, ship);
            }

            if (ship.isCloaked() && sig < 0.99 && !visual.hullPolys.isEmpty()) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                g.setColor(new Color(120, 220, 255, 110));
                g.draw(visual.hullPolys.get(0));
            }

            g.dispose();

            if (drawName && !isTinyStrikeCraft(ship.role)) {
                g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                g2.setColor(new Color(255, 255, 255, 130));
                g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
            }
        }

        static void drawGhostShip(Graphics2D g2, Ship ship, FogOfWarSystem.ContactGhost ghost) {
            if (g2 == null || ship == null || ghost == null || !ship.alive || ghost.isExpired()) return;

            Color hull = mixColor(factionHullColor(ship.faction), new Color(130, 226, 255), 0.45);
            Color trim = mixColor(factionTrimColor(ship.faction), new Color(228, 248, 255), 0.38);
            drawGhostTrail(g2, ghost, hull, trim);

            float fade = (float) Math.max(0.16, Math.min(0.60, 0.20 + ghost.fadeFraction() * 0.36));
            Graphics2D g = (Graphics2D) g2.create();
            g.translate((int) Math.round(ghost.x), (int) Math.round(ghost.y));
            g.rotate(ghost.angle);
            double roleScale = roleVisualScale(ship.role);
            if (Math.abs(roleScale - 1.0) > 1e-6) {
                g.scale(roleScale, roleScale);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));

            ShipVisual visual = getVisual(ship);
            if (visual.station) {
                int ro = (int) Math.round(Math.max(ghost.radius + 8.0, visual.stationOuter));
                int ri = (int) Math.round(Math.max(8.0, Math.min(visual.stationInner, ro - 10.0)));
                g.setColor(withAlpha(hull, 82));
                g.fillOval(-ro, -ro, ro * 2, ro * 2);
                g.setColor(withAlpha(new Color(0, 0, 0), 88));
                g.fillOval(-ri, -ri, ri * 2, ri * 2);
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke((float) Math.max(1.0, Math.min(2.0, ghost.radius * 0.03)),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{6f, 8f}, 0f));
                g.setColor(withAlpha(trim, 176));
                g.drawOval(-ro, -ro, ro * 2, ro * 2);
                g.drawOval(-ri, -ri, ri * 2, ri * 2);
                for (int i = 0; i < visual.stationSpokes; i++) {
                    double a = (Math.PI * 2.0 * i) / Math.max(1, visual.stationSpokes);
                    int x1 = (int) Math.round(Math.cos(a) * (ri + 2));
                    int y1 = (int) Math.round(Math.sin(a) * (ri + 2));
                    int x2 = (int) Math.round(Math.cos(a) * (ro - 2));
                    int y2 = (int) Math.round(Math.sin(a) * (ro - 2));
                    g.drawLine(x1, y1, x2, y2);
                }
                g.setStroke(old);
            } else {
                for (Polygon poly : visual.hullPolys) {
                    g.setColor(withAlpha(hull, 92));
                    g.fillPolygon(poly);
                }
                for (Polygon poly : visual.superPolys) {
                    g.setColor(withAlpha(trim, 34));
                    g.fillPolygon(poly);
                }

                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke((float) Math.max(1.0, Math.min(2.1, ship.radius * 0.035)),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{5f, 7f}, 0f));
                g.setColor(withAlpha(trim, 185));
                for (Polygon poly : visual.hullPolys) {
                    g.drawPolygon(poly);
                }
                g.setColor(withAlpha(trim, 120));
                for (Polygon poly : visual.superPolys) {
                    g.drawPolygon(poly);
                }
                for (Polygon poly : visual.fins) {
                    g.drawPolygon(poly);
                }
                g.setStroke(old);
            }

            int ring = Math.max(6, (int) Math.round(ghost.radius * 0.28));
            g.setColor(withAlpha(new Color(235, 248, 255), 120));
            g.drawOval(-ring, -ring, ring * 2, ring * 2);
            g.dispose();
        }

        private static void drawGhostTrail(Graphics2D g2, FogOfWarSystem.ContactGhost ghost, Color hull, Color trim) {
            if (g2 == null || ghost == null || ghost.trail.size() < 2) return;
            Stroke old = g2.getStroke();
            int sampleCount = ghost.trail.size();
            for (int i = 1; i < sampleCount; i++) {
                FogOfWarSystem.GhostTrailPoint prev = ghost.trail.get(i - 1);
                FogOfWarSystem.GhostTrailPoint curr = ghost.trail.get(i);
                if (prev == null || curr == null) continue;
                double t = i / (double) (sampleCount - 1);
                int alpha = MathUtil.clamp((int) Math.round((26 + 92 * t) * ghost.fadeFraction()), 0, 150);
                float width = (float) Math.max(1.0, ghost.radius * (0.020 + 0.010 * t));
                g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(hull, alpha));
                g2.drawLine((int) Math.round(prev.x), (int) Math.round(prev.y), (int) Math.round(curr.x), (int) Math.round(curr.y));
            }
            FogOfWarSystem.GhostTrailPoint last = ghost.trail.get(sampleCount - 1);
            if (last != null) {
                int dot = Math.max(3, (int) Math.round(Math.max(ghost.radius, 10.0) * 0.12));
                g2.setColor(withAlpha(trim, MathUtil.clamp((int) Math.round(100 * ghost.fadeFraction()), 0, 130)));
                g2.fillOval((int) Math.round(last.x) - dot, (int) Math.round(last.y) - dot, dot * 2, dot * 2);
            }
            g2.setStroke(old);
        }

        private static double roleVisualScale(ShipRole role) {
            if (role == null) return 1.0;
            return switch (role) {
                case FIGHTER -> 0.16;
                case BOMBER -> 0.17;
                case DRONE -> 0.20;
                default -> HullGeometry.roleVisualScale(role);
            };
        }

        private static ShipVisual getVisual(Ship ship) {
            int r = (int) Math.round(Math.max(8.0, ship.radius));
            String key = ship.role + ":" + ship.faction + ":" + r;
            ShipVisual cached = CACHE.get(key);
            if (cached != null) return cached;

            ShipVisual v = buildVisual(ship.role, ship.faction, r);
            CACHE.put(key, v);
            return v;
        }

        private static ShipVisual buildVisual(ShipRole role, int r) {
            return buildVisual(role, null, r);
        }

        private static ShipVisual buildVisual(ShipRole role, Faction faction, int r) {
            ShipVisual v = new ShipVisual();
            if (role == null) role = ShipRole.FRIGATE;

            switch (role) {
                case PICKET -> {
                    v.hullPolys.add(poly(new int[]{r + 9, r - 4, -r + 2, -r, -r + 2, r - 4},
                            new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 4, r / 2, r / 5}, new int[]{-r / 5, 0, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{-r / 3, -r / 2, -r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{r / 3, r / 2, r / 6}));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                }
                case PATROL -> {
                    v.hullPolys.add(poly(new int[]{r + 7, r - 2, -r + 4, -r, -r + 4, r - 2},
                            new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2}));
                    v.superPolys.add(poly(new int[]{0, r / 3, r / 6, -r / 6}, new int[]{-r / 5, 0, r / 5, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                }
                case ARTILLERY_SHIP -> {
                    v.hullPolys.add(poly(new int[]{r + 7, r - 2, -r + 4, -r, -r + 4, r - 2},
                            new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2}));
                    v.superPolys.add(poly(new int[]{0, r / 3, r / 6, -r / 6}, new int[]{-r / 5, 0, r / 5, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                }
                case LIGHT_CRUISER -> {
                    v.hullPolys.add(poly(new int[]{r + 12, r - 7, -r + 2, -r, -r + 8, -r, -r + 2, r - 7},
                            new int[]{0, -r / 2, -r / 2, -r / 6, 0, r / 6, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 5, r / 4, r / 8, -r / 6}, new int[]{-r / 4, -r / 8, r / 4, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 10, r / 3, r / 4, r / 12}, new int[]{-r / 7, -r / 10, r / 7, r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{-r / 2, -r / 3, -r / 6, -r / 4}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{r / 2, r / 3, r / 6, r / 4}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 3));
                    v.engines.add(new EnginePoint(-r + 1, r / 3));
                }
                case MEDIUM_CRUISER, CRUISER -> {
                    v.hullPolys.add(poly(new int[]{r + 14, r - 7, r - 14, -r + 1, -r, -r + 10, -r, -r + 1, r - 14, r - 7},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 6, 0, r / 6, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 3, r / 5, -r / 8}, new int[]{-r / 5, -r / 8, r / 5, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 8, r / 2, r / 3, r / 12}, new int[]{-r / 7, -r / 12, r / 7, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{-r / 2, -r / 3, -r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{r / 2, r / 3, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 3));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 3));
                }
                case BATTLECRUISER -> {
                    v.hullPolys.add(poly(new int[]{r + 16, r - 6, r - 16, -r + 2, -r, -r + 13, -r, -r + 2, r - 16, r - 6},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 4, 0, r / 4, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 5, r / 3, r / 4, -r / 7}, new int[]{-r / 4, -r / 6, r / 4, r / 3}));
                    v.superPolys.add(poly(new int[]{r / 8, r / 2, r / 3, r / 10}, new int[]{-r / 6, -r / 9, r / 8, r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{-r / 2, -r / 3, -r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{r / 2, r / 3, r / 6}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 4));
                    v.engines.add(new EnginePoint(-r + 1, -r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 4));
                }
                case BATTLESHIP -> {
                    v.hullPolys.add(poly(new int[]{r + 18, r - 8, r - 18, -r + 2, -r, -r + 15, -r, -r + 2, r - 18, r - 8},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 3, r / 4, -r / 8}, new int[]{-r / 4, -r / 6, r / 4, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 8, r / 2, r / 3, r / 8}, new int[]{-r / 6, -r / 8, r / 8, r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{-r / 2, -r / 3, -r / 8}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{r / 2, r / 3, r / 8}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 4));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 4));
                }
                case DREADNOUGHT -> {
                    v.hullPolys.add(poly(new int[]{r + 20, r - 11, r - 22, -r + 2, -r, -r + 17, -r, -r + 2, r - 22, r - 11},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 3, r / 4, -r / 10}, new int[]{-r / 4, -r / 7, r / 4, r / 3}));
                    v.superPolys.add(poly(new int[]{r / 12, r / 2, r / 3, r / 8}, new int[]{-r / 5, -r / 8, r / 8, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 4, -r + 2}, new int[]{-r / 2, -r / 3, -r / 7}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 4, -r + 2}, new int[]{r / 2, r / 3, r / 7}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 5));
                    v.engines.add(new EnginePoint(-r + 1, -r / 3));
                    v.engines.add(new EnginePoint(-r + 1, -r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 3));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 5));
                }
                case SUPERSHIP -> {
                    v.hullPolys.add(poly(new int[]{r + 24, r - 8, r - 24, -r + 3, -r, -r + 18, -r, -r + 3, r - 24, r - 8},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 4, r / 4, -r / 10}, new int[]{-r / 4, -r / 8, r / 4, r / 3}));
                    v.superPolys.add(poly(new int[]{r / 6, r / 2, r / 3, r / 7}, new int[]{-r / 6, -r / 10, r / 10, r / 6}));
                    v.superPolys.add(poly(new int[]{r / 3, r / 2, r / 2, r / 3}, new int[]{-r / 7, -r / 11, r / 11, r / 7}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{-r / 2, -r / 4, -r / 8}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{r / 2, r / 4, r / 8}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 6));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 6));
                }
                case MINER -> {
                    // Industrial silhouette: chunkier bow, side pods, mining rig.
                    v.hullPolys.add(poly(new int[]{r + 5, r - 7, -r + 6, -r, -r + 6, r - 7},
                            new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 3, r / 3, r / 4, -r / 3}, new int[]{-r / 4, -r / 4, r / 4, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 4, r / 2, r / 2, r / 4}, new int[]{-r / 5, -r / 6, r / 6, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{-r / 2, -r / 2, -r / 6, -r / 4}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{r / 2, r / 2, r / 6, r / 4}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                }
                case BASE -> {
                    v.station = true;
                    v.stationOuter = r;
                    v.stationInner = Math.max(8, r - 14);
                    v.stationSpokes = 6;
                    Polygon stationHull = ShipHullSilhouette.hullPolygon(role, r, faction);
                    if (stationHull != null && stationHull.npoints >= 3) {
                        v.hullPolys.add(stationHull);
                    }
                }
                default -> {
                    // Generic frigate line
                    v.hullPolys.add(poly(new int[]{r + 8, r - 6, -r, -r + 8, -r, r - 6},
                            new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 4, r / 3, r / 5, -r / 6}, new int[]{-r / 5, 0, r / 5, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                }
            }

            if (!v.station) {
                Polygon canonicalHull = ShipHullSilhouette.hullPolygon(role, r, faction);
                if (canonicalHull != null && canonicalHull.npoints >= 3) {
                    v.hullPolys.clear();
                    v.hullPolys.add(canonicalHull);
                    List<EnginePoint> derivedEngines = enginePointsForHull(canonicalHull, role, r);
                    if (!derivedEngines.isEmpty()) {
                        v.engines.clear();
                        v.engines.addAll(derivedEngines);
                    }
                }
            }

            v.hullArea = buildArea(v.hullPolys);
            v.hullBounds = (v.hullArea == null) ? null : v.hullArea.getBounds2D();

            return v;
        }

        private static void drawHullShadow(Graphics2D g, ShipVisual v) {
            g.setColor(new Color(0, 0, 0, 70));
            g.translate(4, 4);
            if (v.station) {
                int ro = (int) Math.round(v.stationOuter);
                int ri = (int) Math.round(v.stationInner);
                g.fillOval(-ro, -ro, ro * 2, ro * 2);
                g.setColor(new Color(0, 0, 0, 120));
                g.fillOval(-ri, -ri, ri * 2, ri * 2);
            } else {
                for (Polygon p : v.hullPolys) g.fillPolygon(p);
            }
            g.translate(-4, -4);
        }

        private static void drawHullAndSuper(Graphics2D g, ShipVisual v, Color hull, Color trim) {
            if (v.station) {
                int ro = (int) Math.round(v.stationOuter);
                int ri = (int) Math.round(v.stationInner);
                g.setColor(new Color(hull.getRed(), hull.getGreen(), hull.getBlue(), 190));
                g.fillOval(-ro, -ro, ro * 2, ro * 2);
                g.setColor(new Color(0, 0, 0, 160));
                g.fillOval(-ri, -ri, ri * 2, ri * 2);
                g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 170));
                g.drawOval(-ro, -ro, ro * 2, ro * 2);
                g.drawOval(-ri, -ri, ri * 2, ri * 2);
                for (int i = 0; i < v.stationSpokes; i++) {
                    double a = (Math.PI * 2.0 * i) / v.stationSpokes;
                    int x1 = (int) Math.round(Math.cos(a) * (ri + 2));
                    int y1 = (int) Math.round(Math.sin(a) * (ri + 2));
                    int x2 = (int) Math.round(Math.cos(a) * (ro - 2));
                    int y2 = (int) Math.round(Math.sin(a) * (ro - 2));
                    g.drawLine(x1, y1, x2, y2);
                }
                return;
            }

            Rectangle2D bounds = (v.hullBounds == null)
                    ? new Rectangle2D.Double(-1.0, -1.0, 2.0, 2.0)
                    : v.hullBounds;
            int backX = (int) Math.round(bounds.getMinX());
            int frontX = (int) Math.round(bounds.getMaxX());
            Color hullDark = new Color(Math.max(0, hull.getRed() - 35), Math.max(0, hull.getGreen() - 35), Math.max(0, hull.getBlue() - 35));
            Color hullLight = new Color(Math.min(255, hull.getRed() + 25), Math.min(255, hull.getGreen() + 25), Math.min(255, hull.getBlue() + 25));
            GradientPaint gp = new GradientPaint(backX, 0, hullDark, frontX, 0, hullLight);

            g.setPaint(gp);
            for (Polygon p : v.hullPolys) g.fillPolygon(p);
            g.setPaint(null);

            for (Polygon p : v.superPolys) {
                g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 120));
                g.fillPolygon(p);
                g.setColor(new Color(0, 0, 0, 100));
                g.drawPolygon(p);
            }

            for (Polygon p : v.fins) {
                g.setColor(new Color(hullDark.getRed(), hullDark.getGreen(), hullDark.getBlue(), 160));
                g.fillPolygon(p);
            }

            g.setColor(new Color(0, 0, 0, 115));
            for (Polygon p : v.hullPolys) g.drawPolygon(p);
        }

        private static void drawPanelsAndWindows(Graphics2D g, Ship ship, ShipVisual v, Area hullArea, boolean hasAlbedoSkin) {
            if (v.station || hullArea == null) return;
            if (hasAlbedoSkin) return;

            Shape oldClip = g.getClip();
            g.setClip(hullArea);

            int seed = System.identityHashCode(ship) * 31 + (ship.role == null ? 0 : ship.role.ordinal() * 17);
            Random rng = new Random(seed);
            int detail = Math.max(4, (int) Math.round(ship.radius / 4.0));

            g.setColor(new Color(255, 255, 255, 55));
            for (int i = 0; i < detail; i++) {
                int x1 = (int) Math.round(-ship.radius + rng.nextDouble() * ship.radius * 2.0);
                int y1 = (int) Math.round(-ship.radius + rng.nextDouble() * ship.radius * 2.0);
                int x2 = x1 + 4 + rng.nextInt(Math.max(4, (int) ship.radius / 2 + 2));
                int y2 = y1 + rng.nextInt(5) - 2;
                g.drawLine(x1, y1, x2, y2);
            }

            g.setColor(new Color(230, 245, 255, 75));
            int windows = Math.max(3, detail / 2);
            for (int i = 0; i < windows; i++) {
                int x = (int) Math.round(-ship.radius / 2 + rng.nextDouble() * ship.radius);
                int y = (int) Math.round(-ship.radius / 3 + rng.nextDouble() * ship.radius * 0.66);
                g.fillRect(x, y, 2, 2);
            }

            g.setClip(oldClip);
        }

        private static void drawHullSkin(Graphics2D g, Ship ship, ShipVisual v, Area hullArea,
                                         Color hull, Color trim, ShipSkinSet skinSet) {
            if (skinSet == null || !skinSet.hasAnyLayer()) return;

            Rectangle2D bounds = (hullArea == null)
                    ? new Rectangle2D.Double(-ship.radius, -ship.radius, ship.radius * 2.0, ship.radius * 2.0)
                    : hullArea.getBounds2D();

            // Draw the authored sprite on a square canvas around the ship center.
            int baseSpan = Math.max(1, (int) Math.round(ship.radius * 2.0));
            int sw = Math.max(1, (int) Math.round(baseSpan * ShipHullSilhouette.skinRenderScale()));
            int sh = sw;
            int sx = -sw / 2;
            int sy = -sh / 2;

            if (drawMultipartDamageStage(g, ship, sw, sh)) {
                // Multipart hulls can swap staged baked damage art directly.
            } else {
                drawSkinLayer(g, skinSet.albedo, sx, sy, sw, sh, 0.98f);
            }
            drawStationUpgradeModules(g, ship, hullArea);
            boolean hasAuxLayers = skinSet.panel != null || skinSet.ao != null
                    || skinSet.emissive != null || skinSet.damage != null;
            if (!hasAuxLayers) return;

            Shape oldClip = g.getClip();
            if (hullArea != null) {
                if (oldClip == null) {
                    g.setClip(hullArea);
                } else {
                    Area combined = new Area(oldClip);
                    combined.intersect(hullArea);
                    g.setClip(combined);
                }
            }

            drawSkinLayer(g, skinSet.panel, sx, sy, sw, sh, 0.46f);
            drawSkinLayer(g, skinSet.ao, sx, sy, sw, sh, 0.50f);

            if (skinSet.damage != null && ship.hpMax > 0) {
                double damageFrac = Math.max(0.0, Math.min(1.0, 1.0 - ship.hp / (double) ship.hpMax));
                float damageAlpha = (float) Math.min(0.88, 0.18 + damageFrac * 0.72);
                if (damageAlpha > 0.16f) {
                    drawSkinLayer(g, skinSet.damage, sx, sy, sw, sh, damageAlpha);
                    if (damageFrac > 0.50) drawSkinLayer(g, skinSet.damage, sx, sy, sw, sh, damageAlpha * 0.36f);
                }
            }

            if (skinSet.emissive != null) {
                drawSkinLayer(g, skinSet.emissive, sx, sy, sw, sh, 0.50f);
                drawSkinLayer(g, skinSet.emissive, sx, sy, sw, sh, 0.17f);
            }

            applyFactionSkinLighting(g, bounds, ship.faction, hull, trim);
            g.setClip(oldClip);
        }

        private static void drawStationUpgradeModules(Graphics2D g, Ship ship, Area hullArea) {
            if (g == null || ship == null || hullArea == null) return;
            if (ship.role != ShipRole.BASE) return;
            BaseUpgrades upgrades = ship.stationUpgrades;
            if (upgrades == null) return;

            drawStationModuleSeries(g, ship, hullArea, "hull_fortification", upgrades.hullLv,
                    new double[]{Math.toRadians(-45.0), Math.toRadians(45.0), Math.toRadians(135.0), Math.toRadians(225.0), Math.toRadians(180.0)},
                    0.96, 0.08);
            drawStationModuleSeries(g, ship, hullArea, "shield_array", upgrades.shieldLv,
                    new double[]{Math.toRadians(90.0), Math.toRadians(270.0), Math.toRadians(20.0), Math.toRadians(160.0), Math.toRadians(340.0)},
                    0.82, 0.10);
            drawStationModuleSeries(g, ship, hullArea, "turret_systems", upgrades.turretLv,
                    new double[]{Math.toRadians(0.0), Math.toRadians(180.0), Math.toRadians(60.0), Math.toRadians(300.0), Math.toRadians(240.0)},
                    0.80, 0.12);
            drawStationModuleSeries(g, ship, hullArea, "mining_ops", upgrades.miningLv,
                    new double[]{Math.toRadians(150.0), Math.toRadians(210.0), Math.toRadians(30.0), Math.toRadians(330.0), Math.toRadians(270.0)},
                    0.88, 0.10);
            drawStationModuleSeries(g, ship, hullArea, "hangar_expansion", upgrades.hangarLv,
                    new double[]{Math.toRadians(0.0), Math.toRadians(180.0), Math.toRadians(270.0)},
                    1.08, 0.14);
        }

        private static void drawStationModuleSeries(Graphics2D g,
                                                    Ship ship,
                                                    Area hullArea,
                                                    String moduleKey,
                                                    int level,
                                                    double[] angles,
                                                    double sizeFactor,
                                                    double outwardBias) {
            if (level <= 0 || angles == null || angles.length == 0) return;
            BufferedImage module = StationModuleLibrary.getModuleSkin(moduleKey, ship.faction);
            if (module == null) return;

            int count = Math.min(level, angles.length);
            double maxProbeRadius = Math.max(ship.radius * 2.35, ship.radius * ShipHullSilhouette.skinRenderScale());
            int drawW = Math.max(12, (int) Math.round(ship.radius * sizeFactor));
            int drawH = Math.max(12, (int) Math.round(drawW * (module.getHeight() / (double) Math.max(1, module.getWidth()))));
            double majorSpan = Math.max(drawW, drawH);
            double outward = majorSpan * outwardBias;

            for (int i = 0; i < count; i++) {
                double angle = angles[i];
                Point2D.Double anchor = stationHullBoundaryPoint(hullArea, angle, maxProbeRadius);
                if (anchor == null) continue;
                double dx = Math.cos(angle);
                double dy = Math.sin(angle);
                double cx = anchor.x + dx * outward;
                double cy = anchor.y + dy * outward;

                Graphics2D gm = (Graphics2D) g.create();
                gm.translate(cx, cy);
                gm.rotate(angle);
                gm.drawImage(module, -drawW / 2, -drawH / 2, drawW, drawH, null);
                gm.dispose();
            }
        }

        private static Point2D.Double stationHullBoundaryPoint(Area hullArea, double angle, double maxRadius) {
            if (hullArea == null) return null;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            double lastInsideX = 0.0;
            double lastInsideY = 0.0;
            boolean foundInside = false;
            double step = 1.2;
            for (double dist = 0.0; dist <= maxRadius; dist += step) {
                double x = dx * dist;
                double y = dy * dist;
                if (hullArea.contains(x, y)) {
                    lastInsideX = x;
                    lastInsideY = y;
                    foundInside = true;
                } else if (foundInside) {
                    break;
                }
            }
            if (!foundInside) return new Point2D.Double(dx * maxRadius * 0.45, dy * maxRadius * 0.45);
            return new Point2D.Double(lastInsideX, lastInsideY);
        }

        private static boolean drawMultipartDamageStage(Graphics2D g, Ship ship, int sw, int sh) {
            ShipPartLibrary.PartSet normal = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.NORMAL);
            if (!normal.hasParts()) return false;

            double hpFrac = (ship == null || ship.hpMax <= 0)
                    ? 1.0
                    : MathUtil.clamp(ship.hp / (double) ship.hpMax, 0.0, 1.0);

            if (hpFrac > 2.0 / 3.0) {
                drawSkinParts(g, normal.parts, sw, sh, 1.0f);
                return true;
            }

            ShipPartLibrary.PartSet damaged = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.DAMAGED);
            if (hpFrac > 1.0 / 3.0 && damaged.variant == ShipPartLibrary.Variant.DAMAGED) {
                float t = (float) MathUtil.clamp((2.0 / 3.0 - hpFrac) / (1.0 / 3.0), 0.0, 1.0);
                drawSkinParts(g, normal.parts, sw, sh, 1.0f - t);
                drawSkinParts(g, damaged.parts, sw, sh, t);
                return true;
            }

            ShipPartLibrary.PartSet critical = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.CRITICAL);
            if (hpFrac <= 1.0 / 3.0 && critical.variant == ShipPartLibrary.Variant.CRITICAL) {
                drawSkinParts(g, critical.parts, sw, sh, 1.0f);
                return true;
            }

            if (damaged.variant == ShipPartLibrary.Variant.DAMAGED) {
                drawSkinParts(g, damaged.parts, sw, sh, 1.0f);
                return true;
            }

            drawSkinParts(g, normal.parts, sw, sh, 1.0f);
            return true;
        }

        private static void drawSkinLayer(Graphics2D g, BufferedImage layer,
                                          int x, int y, int w, int h, float alpha) {
            if (layer == null || alpha <= 0f) return;
            float a = (float) Math.max(0.0, Math.min(1.0, alpha));
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.drawImage(layer, x, y, w, h, null);
            g.setComposite(old);
        }

        private static void drawSkinParts(Graphics2D g, List<ShipPartLibrary.PartSprite> parts,
                                          int w, int h, float alpha) {
            if (parts == null || parts.isEmpty() || alpha <= 0f) return;
            for (ShipPartLibrary.PartSprite part : parts) {
                if (part == null || part.image == null) continue;
                int drawW = Math.max(1, (int) Math.round(w * part.widthNorm));
                int drawH = Math.max(1, (int) Math.round(h * part.heightNorm));
                int cx = (int) Math.round(part.offsetXNorm * w);
                int cy = (int) Math.round(part.offsetYNorm * h);
                int dx = cx - drawW / 2;
                int dy = cy - drawH / 2;
                drawSkinLayer(g, part.image, dx, dy, drawW, drawH, alpha);
            }
        }

        private static void applyFactionSkinLighting(Graphics2D g, Rectangle2D bounds, Faction faction, Color hull, Color trim) {
            int x = (int) Math.round(bounds.getMinX());
            int y = (int) Math.round(bounds.getMinY());
            int w = Math.max(1, (int) Math.round(bounds.getWidth()));
            int h = Math.max(1, (int) Math.round(bounds.getHeight()));
            HullLightingPreset preset = HullLightingPreset.forFaction(faction, hull, trim);

            Paint oldPaint = g.getPaint();
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setPaint(new GradientPaint(
                    x, y + h / 2f, withAlpha(preset.rimColor, preset.rimAlpha),
                    x + w * 0.40f, y + h / 2f, withAlpha(preset.rimColor, 0)));
            g.fillRect(x, y, w, h);

            g.setPaint(new GradientPaint(
                    x, y + h / 2f, withAlpha(preset.keyColor, 0),
                    x + w, y + h / 2f, withAlpha(preset.keyColor, preset.keyAlpha)));
            g.fillRect(x, y, w, h);

            g.setPaint(new GradientPaint(
                    x, y, withAlpha(Color.WHITE, preset.deckAlpha),
                    x, y + h, withAlpha(Color.BLACK, preset.bellyAlpha)));
            g.fillRect(x, y, w, h);

            g.setPaint(oldPaint);
            g.setComposite(oldComposite);
        }

        private static Color withAlpha(Color c, int alpha) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
        }

        private static void drawEngines(Graphics2D g, Ship ship, ShipVisual v) {
            if (ship == null || v == null || v.station || v.engines.isEmpty()) return;
            drawEngineNozzlePass(g, ship, v.engines, ship.radius);
        }

        private static void drawHardpoints(Graphics2D g, Ship ship, ShipVisual v) {
            drawTurrets(g, ship);
        }

        private static void drawRoomDebugOverlay(Graphics2D g, Ship ship) {
            List<Ship.RoomStatus> rooms = ship.roomStatusSnapshot();
            if (rooms == null || rooms.isEmpty()) return;
            boolean showPolygons = DevTools.isRoomPolygonsEnabled();
            boolean showImpactPoints = DevTools.isRoomImpactPointsEnabled();
            boolean showHpBars = DevTools.isRoomHpBarsEnabled();
            boolean showHazards = DevTools.isRoomHazardsEnabled();
            if (!showPolygons && !showImpactPoints && !showHpBars && !showHazards) return;

            Color stroke = new Color(255, 240, 150, 110);
            Font oldFont = g.getFont();
            g.setFont(new Font("Consolas", Font.PLAIN, 9));

        if (showPolygons || showHazards) {
            EnumMap<ShipRoomLayout.RoomId, Ship.RoomStatus> statusById = new EnumMap<>(ShipRoomLayout.RoomId.class);
            for (Ship.RoomStatus rs : rooms) {
                if (rs != null && rs.roomId != null) statusById.put(rs.roomId, rs);
            }

            boolean drewVisualCells = false;
            for (ShipRoomLayout.VisualCell cell : ShipRoomLayout.visualCellsFor(ship.role, ship.faction)) {
                if (cell == null || cell.roomId == null) continue;
                Ship.RoomStatus rs = statusById.get(cell.roomId);
                if (rs == null) continue;

                Polygon p = roomPolygonShipLocal(ship, cell.xs, cell.ys);
                if (p == null || p.npoints < 3) continue;
                drewVisualCells = true;

                double frac = (rs.hpMax <= 1e-9) ? 1.0 : Math.max(0.0, Math.min(1.0, rs.hp / rs.hpMax));
                int alpha = 28 + (int) Math.round((1.0 - frac) * 135.0);
                boolean fire = rs.fireIntensity > 0.06;

                if (showPolygons) {
                    Color fill = (showHazards && fire)
                            ? new Color(255, 120, 50, Math.min(180, alpha + 35))
                            : new Color(255, 70, 70, Math.min(170, alpha));
                    g.setColor(fill);
                    g.fillPolygon(p);
                    g.setColor(stroke);
                    g.drawPolygon(p);

                    if (cell.labelAnchor) {
                        Rectangle b = p.getBounds();
                        if (b.width >= 18 && b.height >= 12) {
                            int tx = (int) Math.round(b.getCenterX()) - 14;
                            int ty = (int) Math.round(b.getCenterY());
                            g.setColor(new Color(255, 255, 255, 210));
                            g.drawString((int) Math.round(frac * 100.0) + "%", tx, ty);
                        }
                    }
                }

                if (showHazards && fire && cell.labelAnchor) {
                    Point c = roomDebugCentroid(p);
                    int hzR = Math.max(3, (int) Math.round(2.5 + rs.fireIntensity * 2.3));
                    g.setColor(new Color(255, 165, 70, 205));
                    g.drawOval(c.x - hzR, c.y - hzR, hzR * 2, hzR * 2);
                    g.setColor(new Color(255, 220, 150, 225));
                    g.drawLine(c.x - hzR, c.y, c.x + hzR, c.y);
                    g.drawLine(c.x, c.y - hzR, c.x, c.y + hzR);
                }
            }

            if (!drewVisualCells) {
                for (Ship.RoomStatus rs : rooms) {
                    Polygon p = roomPolygonShipLocal(ship, rs.normalizedXs, rs.normalizedYs);
                    if (p == null || p.npoints < 3) continue;

                    double frac = (rs.hpMax <= 1e-9) ? 1.0 : Math.max(0.0, Math.min(1.0, rs.hp / rs.hpMax));
                    int alpha = 28 + (int) Math.round((1.0 - frac) * 135.0);
                    boolean fire = rs.fireIntensity > 0.06;

                    if (showPolygons) {
                        Color fill = (showHazards && fire)
                                ? new Color(255, 120, 50, Math.min(180, alpha + 35))
                                : new Color(255, 70, 70, Math.min(170, alpha));
                        g.setColor(fill);
                        g.fillPolygon(p);
                        g.setColor(stroke);
                        g.drawPolygon(p);

                        Rectangle b = p.getBounds();
                        int tx = (int) Math.round(b.getCenterX()) - 14;
                        int ty = (int) Math.round(b.getCenterY());
                        g.setColor(new Color(255, 255, 255, 210));
                        g.drawString((int) Math.round(frac * 100.0) + "%", tx, ty);
                    }

                    if (showHazards && fire) {
                        Point c = roomDebugCentroid(p);
                        int hzR = Math.max(3, (int) Math.round(2.5 + rs.fireIntensity * 2.3));
                        g.setColor(new Color(255, 165, 70, 205));
                        g.drawOval(c.x - hzR, c.y - hzR, hzR * 2, hzR * 2);
                        g.setColor(new Color(255, 220, 150, 225));
                        g.drawLine(c.x - hzR, c.y, c.x + hzR, c.y);
                        g.drawLine(c.x, c.y - hzR, c.x, c.y + hzR);
                    }
                }
            }
        }

            if (showHpBars) {
                drawRoomDebugHpBars(g, ship, rooms, showHazards);
            }

            if (showImpactPoints) {
                List<Ship.RoomDamageEvent> events = ship.recentRoomDamageEvents();
                if (events != null) {
                    int start = Math.max(0, events.size() - 8);
                    for (int i = start; i < events.size(); i++) {
                        Ship.RoomDamageEvent ev = events.get(i);
                        if (!Double.isFinite(ev.normalizedX) || !Double.isFinite(ev.normalizedY)) continue;
                        Point pt = normalizedShipLocalPoint(ship, ev.normalizedX, ev.normalizedY);
                        int px = pt.x;
                        int py = pt.y;
                        g.setColor(ev.fromHazard ? new Color(255, 130, 70, 200) : new Color(255, 250, 170, 210));
                        g.fillOval(px - 2, py - 2, 4, 4);
                    }
                }
            }

            g.setFont(oldFont);
        }

        private static void drawRoomDebugHpBars(Graphics2D g, Ship ship, List<Ship.RoomStatus> rooms, boolean showHazards) {
            if (g == null || ship == null || rooms == null || rooms.isEmpty()) return;
            int barW = Math.max(14, (int) Math.round(ship.radius * 0.74));
            int barH = 3;
            int gap = 1;
            int count = rooms.size();
            int listH = count * barH + (count - 1) * gap;
            int baseX = -((int) Math.round(ship.radius)) - barW - 9;
            int baseY = -(listH / 2);
            int i = 0;
            for (Ship.RoomStatus rs : rooms) {
                double frac = (rs.hpMax <= 1e-9) ? 1.0 : Math.max(0.0, Math.min(1.0, rs.hp / rs.hpMax));
                int y = baseY + i * (barH + gap);
                int fillW = MathUtil.clamp((int) Math.round(barW * frac), 0, barW);
                Color hpColor = new Color(
                        MathUtil.clamp((int) Math.round((1.0 - frac) * 220.0), 0, 220),
                        MathUtil.clamp((int) Math.round(90.0 + frac * 165.0), 0, 255),
                        80,
                        220
                );
                if (showHazards && rs.fireIntensity > 0.06) {
                    hpColor = new Color(255, 130, 70, 220);
                }
                g.setColor(new Color(18, 18, 22, 170));
                g.fillRect(baseX, y, barW, barH);
                if (fillW > 0) {
                    g.setColor(hpColor);
                    g.fillRect(baseX, y, fillW, barH);
                }
                g.setColor(new Color(255, 255, 220, 130));
                g.drawRect(baseX, y, barW, barH);
                if (rs.critical) {
                    g.setColor(new Color(255, 220, 120, 210));
                    g.fillRect(baseX - 3, y, 2, barH);
                }
                i++;
            }
        }

        private static Point roomDebugCentroid(Polygon p) {
            if (p == null || p.npoints <= 0) return new Point(0, 0);
            int sx = 0;
            int sy = 0;
            for (int i = 0; i < p.npoints; i++) {
                sx += p.xpoints[i];
                sy += p.ypoints[i];
            }
            return new Point(Math.round(sx / (float) p.npoints), Math.round(sy / (float) p.npoints));
        }

        private static Area buildArea(List<Polygon> polys) {
            if (polys == null || polys.isEmpty()) return null;
            Area a = new Area();
            for (Polygon p : polys) a.add(new Area(p));
            return a;
        }

        private static Polygon poly(int[] xs, int[] ys) {
            return new Polygon(xs, ys, Math.min(xs.length, ys.length));
        }
    }

    private static final class ShipVisual {
        final List<Polygon> hullPolys = new ArrayList<>();
        final List<Polygon> superPolys = new ArrayList<>();
        final List<Polygon> fins = new ArrayList<>();
        final List<EnginePoint> engines = new ArrayList<>();
        final Map<Integer, Area> shieldShellCache = new HashMap<>();
        Area hullArea;
        Rectangle2D hullBounds;
        boolean station = false;
        double stationOuter = 0;
        double stationInner = 0;
        int stationSpokes = 0;
    }

    private static final class EnginePoint {
        final int x;
        final int y;

        EnginePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class EngineBand {
        final int y;
        final int left;
        final int right;

        EngineBand(int y, int left, int right) {
            this.y = y;
            this.left = left;
            this.right = right;
        }

        int width() {
            return right - left + 1;
        }
    }

    private static final class ShipSkinSet {
        final BufferedImage albedo;
        final BufferedImage panel;
        final BufferedImage ao;
        final BufferedImage emissive;
        final BufferedImage damage;

        ShipSkinSet(BufferedImage albedo, BufferedImage panel, BufferedImage ao,
                    BufferedImage emissive, BufferedImage damage) {
            this.albedo = albedo;
            this.panel = panel;
            this.ao = ao;
            this.emissive = emissive;
            this.damage = damage;
        }

        boolean hasAlbedo() {
            return albedo != null;
        }

        boolean hasAnyLayer() {
            return albedo != null || panel != null || ao != null || emissive != null || damage != null;
        }
    }

    private static final class HullLightingPreset {
        final Color keyColor;
        final Color rimColor;
        final int keyAlpha;
        final int rimAlpha;
        final int deckAlpha;
        final int bellyAlpha;

        HullLightingPreset(Color keyColor, Color rimColor,
                           int keyAlpha, int rimAlpha, int deckAlpha, int bellyAlpha) {
            this.keyColor = keyColor;
            this.rimColor = rimColor;
            this.keyAlpha = keyAlpha;
            this.rimAlpha = rimAlpha;
            this.deckAlpha = deckAlpha;
            this.bellyAlpha = bellyAlpha;
        }

        static HullLightingPreset forFaction(Faction faction, Color hull, Color trim) {
            Color baseKey = brighten(hull, 46);
            Color baseRim = brighten(trim, 24);
            if (faction == null) {
                return new HullLightingPreset(baseKey, baseRim, 56, 48, 30, 24);
            }
            return switch (faction) {
                case PLAYER, ALLY -> new HullLightingPreset(baseKey, baseRim, 62, 52, 30, 23);
                case ENEMY -> new HullLightingPreset(baseKey, baseRim, 52, 40, 26, 26);
                case TEAM_C -> new HullLightingPreset(baseKey, baseRim, 58, 46, 29, 24);
                case TEAM_D -> new HullLightingPreset(baseKey, baseRim, 57, 48, 28, 24);
            };
        }

        private static Color brighten(Color c, int delta) {
            return new Color(
                    Math.min(255, c.getRed() + delta),
                    Math.min(255, c.getGreen() + delta),
                    Math.min(255, c.getBlue() + delta));
        }
    }

    private static final class ShipSkinLibrary {
        private static final String SKIN_DIR = "assets/ship_skins";
        private static final String SKIN_RESOURCE_DIR = "ship_skins";
        private static final List<File> SKIN_ROOTS = resolveSkinRoots(SKIN_DIR);
        private static final Map<String, ShipSkinSet> CACHE = new HashMap<>();
        private static final Set<String> MISS = new HashSet<>();

        static boolean hasSkin(ShipRole role, Faction faction) {
            ShipSkinSet set = getSkinSet(role, faction);
            return set != null && set.hasAlbedo();
        }

        static BufferedImage getSkin(ShipRole role, Faction faction) {
            ShipSkinSet set = getSkinSet(role, faction);
            return (set == null) ? null : set.albedo;
        }

        static ShipSkinSet getSkinSet(ShipRole role, Faction faction) {
            String roleKey = keyForRole(role);
            String factionKey = keyForFaction(faction);
            String key = roleKey + "|" + factionKey;
            if (CACHE.containsKey(key)) return CACHE.get(key);
            if (MISS.contains(key)) return null;

            BufferedImage albedo = loadLayer(roleKey, factionKey, "albedo", true);
            BufferedImage panel = loadLayer(roleKey, factionKey, "panel", false);
            BufferedImage ao = loadLayer(roleKey, factionKey, "ao", false);
            BufferedImage emissive = loadLayer(roleKey, factionKey, "emissive", false);
            BufferedImage damage = loadLayer(roleKey, factionKey, "damage", false);

            ShipSkinSet set = new ShipSkinSet(albedo, panel, ao, emissive, damage);
            if (set.hasAnyLayer()) {
                CACHE.put(key, set);
                return set;
            }

            MISS.add(key);
            return null;
        }

        private static BufferedImage loadLayer(String roleKey, String factionKey, String layerKey, boolean includeLegacyRoleFallback) {
            String layerSuffix = "_" + layerKey;

            BufferedImage img = loadRoleSkin(factionKey + "/" + roleKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin(roleKey + "_" + factionKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin(roleKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin("default_" + factionKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin("default" + layerSuffix);
            if (img != null) return img;

            if (!includeLegacyRoleFallback) return null;

            img = loadRoleSkin(factionKey, roleKey);
            if (img != null) return img;

            img = loadRoleSkin(roleKey + "_" + factionKey);
            if (img != null) return img;

            img = loadRoleSkin(roleKey);
            if (img != null) return img;

            img = loadRoleSkin("default_" + factionKey);
            if (img != null) return img;

            return loadRoleSkin("default");
        }

        private static BufferedImage loadRoleSkin(String key) {
            BufferedImage resource = loadBundledImage(Renderer.class, SKIN_RESOURCE_DIR, SKIN_DIR, key);
            if (resource != null) return resource;
            for (File root : SKIN_ROOTS) {
                File f = new File(root, key + ".png");
                try {
                    if (f.isFile()) return ImageIO.read(f);
                } catch (IOException ignored) {}
            }
            return null;
        }

        private static BufferedImage loadRoleSkin(String factionKey, String roleKey) {
            return loadRoleSkin(factionKey + "/" + roleKey);
        }

        private static String keyForRole(ShipRole role) {
            if (role == null) return "frigate";
            if (role == ShipRole.ARTILLERY_SHIP) return "patrol";
            // Defense-node structures should reuse the faction station skins until they have bespoke variants.
            if (role == ShipRole.STATIC_TURRET) return "base";
            if (role == ShipRole.ELITE_REINFORCEMENTS_TITAN) return "elite_supership_command_titan";
            return role.name().toLowerCase(Locale.ROOT);
        }

        private static String keyForFaction(Faction faction) {
            if (faction == null) return "ally";
            return switch (faction) {
                case PLAYER, ALLY -> "ally";
                case ENEMY -> "enemy";
                case TEAM_C -> "team_c";
                case TEAM_D -> "team_d";
            };
        }

        private static List<File> resolveSkinRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class TurretSkinLibrary {
        private static final String SKIN_DIR = "assets/turret_skins";
        private static final String SKIN_RESOURCE_DIR = "turret_skins";
        private static final List<File> SKIN_ROOTS = resolveSkinRoots(SKIN_DIR);
        private static final Map<String, BufferedImage> CACHE = new HashMap<>();
        private static final Set<String> MISS = new HashSet<>();

        static BufferedImage getTurretSkin(String styleKey, ShipRole role, Faction faction) {
            String safeStyle = (styleKey == null || styleKey.isBlank()) ? "twin_gun" : styleKey.toLowerCase(Locale.ROOT);
            String roleKey = keyForRole(role);
            String factionKey = keyForFaction(faction);
            String key = roleKey + "|" + factionKey + "|" + safeStyle;
            if (CACHE.containsKey(key)) return CACHE.get(key);
            if (MISS.contains(key)) return null;

            BufferedImage img = loadSkin(factionKey + "/" + roleKey + "_" + safeStyle);
            if (img == null) img = loadSkin(roleKey + "_" + factionKey + "_" + safeStyle);
            if (img == null) img = loadSkin(roleKey + "_" + safeStyle);
            if (img == null) img = loadSkin(factionKey + "/" + safeStyle);
            if (img == null) img = loadSkin(safeStyle + "_" + factionKey);
            if (img == null) img = loadSkin(safeStyle);
            if (img == null) img = loadSkin("default_" + factionKey + "_" + safeStyle);
            if (img == null) img = loadSkin("default_" + safeStyle);
            if (img == null) img = loadSkin("default_" + factionKey);
            if (img == null) img = loadSkin("default");

            if (img != null) {
                CACHE.put(key, img);
                return img;
            }

            MISS.add(key);
            return null;
        }

        private static BufferedImage loadSkin(String key) {
            BufferedImage resource = loadBundledImage(Renderer.class, SKIN_RESOURCE_DIR, SKIN_DIR, key);
            if (resource == null) {
                resource = loadBundledImage(Renderer.class, SKIN_RESOURCE_DIR, SKIN_DIR, key.toUpperCase(Locale.ROOT));
            }
            if (resource != null) return resource;
            for (File root : SKIN_ROOTS) {
                File f = new File(root, key + ".png");
                try {
                    if (f.isFile()) return ImageIO.read(f);
                } catch (IOException ignored) {}
                File upper = new File(root, key.toUpperCase(Locale.ROOT) + ".png");
                try {
                    if (upper.isFile()) return ImageIO.read(upper);
                } catch (IOException ignored) {}
            }
            return null;
        }

        private static String keyForRole(ShipRole role) {
            if (role == null) return "frigate";
            if (role == ShipRole.ELITE_REINFORCEMENTS_TITAN) return "elite_supership_command_titan";
            return role.name().toLowerCase(Locale.ROOT);
        }

        private static String keyForFaction(Faction faction) {
            if (faction == null) return "ally";
            return switch (faction) {
                case PLAYER, ALLY -> "ally";
                case ENEMY -> "enemy";
                case TEAM_C -> "team_c";
                case TEAM_D -> "team_d";
            };
        }

        private static List<File> resolveSkinRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class StationModuleLibrary {
        private static final String MODULE_DIR = "assets/station_modules";
        private static final String MODULE_RESOURCE_DIR = "station_modules";
        private static final List<File> MODULE_ROOTS = resolveSkinRoots(MODULE_DIR);
        private static final Map<String, BufferedImage> CACHE = new HashMap<>();
        private static final Set<String> MISS = new HashSet<>();

        static BufferedImage getModuleSkin(String moduleKey, Faction faction) {
            String safeKey = (moduleKey == null || moduleKey.isBlank()) ? "hull_fortification" : moduleKey.toLowerCase(Locale.ROOT);
            String factionKey = keyForFaction(faction);
            String cacheKey = factionKey + "|" + safeKey;
            if (CACHE.containsKey(cacheKey)) return CACHE.get(cacheKey);
            if (MISS.contains(cacheKey)) return null;

            BufferedImage img = loadModule(factionKey + "/" + safeKey);
            if (img == null) img = loadModule(safeKey + "_" + factionKey);
            if (img == null) img = loadModule("default_" + factionKey + "_" + safeKey);
            if (img == null) img = loadModule(safeKey);
            if (img == null) img = loadModule("default_" + safeKey);

            if (img != null) {
                CACHE.put(cacheKey, img);
                return img;
            }

            MISS.add(cacheKey);
            return null;
        }

        private static BufferedImage loadModule(String key) {
            BufferedImage resource = loadBundledImage(Renderer.class, MODULE_RESOURCE_DIR, MODULE_DIR, key);
            if (resource != null) return resource;
            for (File root : MODULE_ROOTS) {
                File f = new File(root, key + ".png");
                try {
                    if (f.isFile()) return ImageIO.read(f);
                } catch (IOException ignored) {}
            }
            return null;
        }

        private static String keyForFaction(Faction faction) {
            if (faction == null) return "ally";
            return switch (faction) {
                case PLAYER, ALLY -> "ally";
                case ENEMY -> "enemy";
                case TEAM_C -> "team_c";
                case TEAM_D -> "team_d";
            };
        }

        private static List<File> resolveSkinRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class EnvironmentSkinLibrary {
        private static final String BG_DIR = "assets/environment_overhaul_dropzone/background";
        private static final String AST_DIR = "assets/environment_overhaul_dropzone/asteroids";
        private static final String CAMPAIGN_BG_DIR = "assets/environment_overhaul_dropzone/campaign_backgrounds";
        private static final String BG_RESOURCE_DIR = "environment_overhaul_dropzone/background";
        private static final String AST_RESOURCE_DIR = "environment_overhaul_dropzone/asteroids";
        private static final String CAMPAIGN_BG_RESOURCE_DIR = "environment_overhaul_dropzone/campaign_backgrounds";
        private static final List<File> BG_ROOTS = resolveRoots(BG_DIR);
        private static final List<File> AST_ROOTS = resolveRoots(AST_DIR);
        private static final List<File> CAMPAIGN_BG_ROOTS = resolveRoots(CAMPAIGN_BG_DIR);

        private static boolean bgLoaded = false;
        private static BufferedImage bgBase;
        private static BufferedImage bgNebula;
        private static BufferedImage bgStars;
        private static BufferedImage bgDust;
        private static boolean campaignBgLoaded = false;
        private static final Map<String, BufferedImage> CAMPAIGN_BG = new HashMap<>();

        private static boolean astLoaded = false;
        private static final Map<String, List<BufferedImage>> AST_NORMAL = new HashMap<>();
        private static final Map<String, List<BufferedImage>> AST_ORE = new HashMap<>();

        static BufferedImage backgroundBase() {
            ensureBackgroundLoaded();
            return bgBase;
        }

        static BufferedImage backgroundNebula() {
            ensureBackgroundLoaded();
            return bgNebula;
        }

        static BufferedImage backgroundStars() {
            ensureBackgroundLoaded();
            return bgStars;
        }

        static BufferedImage backgroundDust() {
            ensureBackgroundLoaded();
            return bgDust;
        }

        static BufferedImage campaignBackdrop(String key) {
            if (key == null || key.isBlank()) return null;
            ensureCampaignBackgroundsLoaded();
            return CAMPAIGN_BG.get(normalizeCampaignKey(key));
        }

        static BufferedImage pickAsteroidSprite(Asteroid a) {
            if (a == null) return null;
            ensureAsteroidsLoaded();
            if (AST_NORMAL.isEmpty() && AST_ORE.isEmpty()) return null;

            String sizeKey = sizeKeyForRadius(a.radius);
            List<BufferedImage> preferred = a.rich ? AST_ORE.get(sizeKey) : AST_NORMAL.get(sizeKey);
            List<BufferedImage> fallback = a.rich ? AST_NORMAL.get(sizeKey) : AST_ORE.get(sizeKey);
            List<BufferedImage> pool = (preferred != null && !preferred.isEmpty()) ? preferred : fallback;
            if (pool == null || pool.isEmpty()) return null;

            int idx = stableVariantIndex(a, pool.size());
            return pool.get(idx);
        }

        private static void ensureBackgroundLoaded() {
            if (bgLoaded) return;
            bgLoaded = true;

            bgBase = loadFirst(BG_ROOTS, new String[]{
                    "bg_space_base_4096_a",
                    "bg_space_base_tile_4096",
                    "bg_space_base_4096",
                    "bg_space_base_tile",
                    "bg_space_base"
            });
            bgNebula = loadFirst(BG_ROOTS, new String[]{
                    "bg_nebula_overlay_4096_a",
                    "bg_nebula_overlay_tile_4096",
                    "bg_nebula_overlay_4096",
                    "bg_nebula_overlay_tile",
                    "bg_nebula_overlay"
            });
            bgStars = loadFirst(BG_ROOTS, new String[]{
                    "bg_star_overlay_sparse_2048_a",
                    "bg_star_overlay_tile_2048",
                    "bg_star_overlay_sparse_2048",
                    "bg_star_overlay_tile",
                    "bg_star_overlay_sparse"
            });
            bgDust = loadFirst(BG_ROOTS, new String[]{
                    "bg_dust_parallax_2048_a",
                    "bg_dust_overlay_tile_2048",
                    "bg_dust_parallax_2048",
                    "bg_dust_overlay_tile",
                    "bg_dust_overlay"
            });
        }

        private static void ensureAsteroidsLoaded() {
            if (astLoaded) return;
            astLoaded = true;

            AST_NORMAL.clear();
            AST_ORE.clear();
            AST_NORMAL.put("small", new ArrayList<>());
            AST_NORMAL.put("med", new ArrayList<>());
            AST_NORMAL.put("large", new ArrayList<>());
            AST_ORE.put("small", new ArrayList<>());
            AST_ORE.put("med", new ArrayList<>());
            AST_ORE.put("large", new ArrayList<>());

            List<File> files = new ArrayList<>();
            for (File root : AST_ROOTS) {
                File[] pngs = root.listFiles((d, n) -> n != null && n.toLowerCase(Locale.ROOT).endsWith(".png"));
                if (pngs == null) continue;
                for (File f : pngs) files.add(f);
                if (!files.isEmpty()) break;
            }

            files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (!name.startsWith("ast_") || !name.endsWith(".png")) continue;

                String stem = name.substring(0, name.length() - 4);
                boolean ore = stem.endsWith("_ore");
                if (ore) stem = stem.substring(0, stem.length() - 4);

                String[] parts = stem.split("_");
                if (parts.length < 3) continue;
                String size = normalizeAstSize(parts[1]);
                if (size == null) continue;

                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null) continue;
                    (ore ? AST_ORE : AST_NORMAL).get(size).add(img);
                } catch (IOException ex) {
                    System.err.println("[renderer] asteroid_skin_read_failed "
                            + f.getAbsolutePath() + " :: " + ex.getMessage());
                }
            }
        }

        private static void ensureCampaignBackgroundsLoaded() {
            if (campaignBgLoaded) return;
            campaignBgLoaded = true;
            CAMPAIGN_BG.clear();

            for (File root : CAMPAIGN_BG_ROOTS) {
                File[] files = root.listFiles((dir, name) -> {
                    if (name == null) return false;
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                });
                if (files == null || files.length == 0) continue;
                java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File f : files) {
                    String stem = fileStem(f.getName());
                    String key = normalizeCampaignKey(stem);
                    if (key.isBlank() || CAMPAIGN_BG.containsKey(key)) continue;
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            CAMPAIGN_BG.put(key, img);
                        }
                    } catch (IOException ex) {
                        System.err.println("[renderer] campaign_bg_read_failed "
                                + f.getAbsolutePath() + " :: " + ex.getMessage());
                    }
                }
                if (!CAMPAIGN_BG.isEmpty()) break;
            }
        }

        private static String normalizeAstSize(String raw) {
            if (raw == null) return null;
            String s = raw.toLowerCase(Locale.ROOT);
            if (s.equals("small")) return "small";
            if (s.equals("med") || s.equals("medium")) return "med";
            if (s.equals("large")) return "large";
            return null;
        }

        private static String sizeKeyForRadius(double radius) {
            if (radius <= 30.0) return "small";
            if (radius <= 46.0) return "med";
            return "large";
        }

        private static String normalizeCampaignKey(String raw) {
            if (raw == null) return "";
            String s = raw.trim().toLowerCase(Locale.ROOT);
            s = s.replace(' ', '_').replace('-', '_');
            return s;
        }

        private static String fileStem(String name) {
            if (name == null || name.isBlank()) return "";
            int dot = name.lastIndexOf('.');
            return (dot <= 0) ? name : name.substring(0, dot);
        }

        private static int stableVariantIndex(Asteroid a, int modulo) {
            if (modulo <= 1) return 0;
            long h = 1469598103934665603L;
            h ^= Double.doubleToLongBits(a.x);
            h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.y);
            h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.radius);
            h *= 1099511628211L;
            h ^= (long) a.oreMax * 1315423911L;
            int v = (int) (h ^ (h >>> 32));
            return Math.floorMod(v, modulo);
        }

        private static BufferedImage loadFirst(List<File> roots, String[] keys) {
            String resourceDir = (roots == BG_ROOTS) ? BG_RESOURCE_DIR : AST_RESOURCE_DIR;
            String legacyDir = (roots == BG_ROOTS) ? BG_DIR : AST_DIR;
            for (String key : keys) {
                BufferedImage resource = loadBundledImage(Renderer.class, resourceDir, legacyDir, key);
                if (resource != null) return resource;
            }
            for (File root : roots) {
                for (String key : keys) {
                    File f = new File(root, key + ".png");
                    try {
                        if (f.isFile()) return ImageIO.read(f);
                    } catch (IOException ignored) {}
                }
            }
            return null;
        }

        private static List<File> resolveRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class ProjectileSkinLibrary {
        private static final String SKIN_DIR = "assets/projectile_skins";
        private static final String SKIN_RESOURCE_DIR = "projectile_skins";
        private static BufferedImage missileSkin;
        private static BufferedImage torpedoStrikeSkin;
        private static BufferedImage atomicStrikeSkin;
        private static BufferedImage energyBoltSkin;
        private static BufferedImage beamBoltSkin;
        private static BufferedImage beamBoltSingleSkin;
        private static BufferedImage waveShotSkin;
        private static BufferedImage bulletSkin;
        private static BufferedImage ciwsPelletSkin;
        private static boolean missileSkinLoaded = false;
        private static boolean torpedoStrikeSkinLoaded = false;
        private static boolean atomicStrikeSkinLoaded = false;
        private static boolean energyBoltSkinLoaded = false;
        private static boolean beamBoltSkinLoaded = false;
        private static boolean beamBoltSingleSkinLoaded = false;
        private static boolean waveShotSkinLoaded = false;
        private static boolean bulletSkinLoaded = false;
        private static boolean ciwsPelletSkinLoaded = false;

        static BufferedImage getMissileSkin() {
            if (missileSkinLoaded) return missileSkin;
            missileSkinLoaded = true;
            missileSkin = loadSkin("missile");
            return missileSkin;
        }

        static BufferedImage getTorpedoStrikeSkin() {
            if (torpedoStrikeSkinLoaded) return torpedoStrikeSkin;
            torpedoStrikeSkinLoaded = true;
            torpedoStrikeSkin = loadSkin("torpedo_strike");
            return torpedoStrikeSkin;
        }

        static BufferedImage getAtomicStrikeSkin() {
            if (atomicStrikeSkinLoaded) return atomicStrikeSkin;
            atomicStrikeSkinLoaded = true;
            atomicStrikeSkin = loadSkin("atomic_strike");
            return atomicStrikeSkin;
        }

        static BufferedImage getEnergyBoltSkin(boolean beamBoltVariant) {
            if (beamBoltVariant) {
                if (beamBoltSkinLoaded) return beamBoltSkin;
                beamBoltSkinLoaded = true;
                beamBoltSkin = loadSkin("beam_bolt");
                return beamBoltSkin;
            }
            if (energyBoltSkinLoaded) return energyBoltSkin;
            energyBoltSkinLoaded = true;
            energyBoltSkin = loadSkin("energy_bolt");
            return energyBoltSkin;
        }

        static BufferedImage getBeamBoltSingleSkin() {
            if (beamBoltSingleSkinLoaded) return beamBoltSingleSkin;
            beamBoltSingleSkinLoaded = true;
            beamBoltSingleSkin = loadSkin("beam_bolt_single");
            return beamBoltSingleSkin;
        }

        static BufferedImage getWaveShotSkin() {
            if (waveShotSkinLoaded) return waveShotSkin;
            waveShotSkinLoaded = true;
            waveShotSkin = loadSkin("wave_shot");
            return waveShotSkin;
        }

        static BufferedImage getBulletSkin() {
            if (bulletSkinLoaded) return bulletSkin;
            bulletSkinLoaded = true;
            bulletSkin = loadSkin("bullet");
            return bulletSkin;
        }

        static BufferedImage getCiwsPelletSkin() {
            if (ciwsPelletSkinLoaded) return ciwsPelletSkin;
            ciwsPelletSkinLoaded = true;
            ciwsPelletSkin = loadSkin("ciws_pellet");
            return ciwsPelletSkin;
        }

        private static BufferedImage loadSkin(String key) {
            BufferedImage resource = loadBundledImage(Renderer.class, SKIN_RESOURCE_DIR, SKIN_DIR, key);
            if (resource != null) return resource;
            String path = SKIN_DIR + "/" + key + ".png";
            try {
                File f = new File(path);
                if (f.isFile()) return ImageIO.read(f);
            } catch (IOException ignored) {}
            return null;
        }
    }

    private static final class StrikeButtonSkinLibrary {
        private static final String SKIN_DIR = "assets/ui/strike_buttons";
        private static final String SKIN_RESOURCE_DIR = "ui/strike_buttons";
        private static BufferedImage torpedoButton;
        private static BufferedImage airWingButton;
        private static BufferedImage nuclearButton;
        private static boolean torpedoLoaded = false;
        private static boolean airWingLoaded = false;
        private static boolean nuclearLoaded = false;

        static BufferedImage getTorpedoButton() {
            if (torpedoLoaded) return torpedoButton;
            torpedoLoaded = true;
            torpedoButton = loadSkin("torpedo_strike_button");
            return torpedoButton;
        }

        static BufferedImage getAirWingButton() {
            if (airWingLoaded) return airWingButton;
            airWingLoaded = true;
            airWingButton = loadSkin("air_wing_strike_button");
            return airWingButton;
        }

        static BufferedImage getNuclearButton() {
            if (nuclearLoaded) return nuclearButton;
            nuclearLoaded = true;
            nuclearButton = loadSkin("nuclear_strike_button");
            return nuclearButton;
        }

        private static BufferedImage loadSkin(String key) {
            BufferedImage resource = loadBundledImage(Renderer.class, SKIN_RESOURCE_DIR, SKIN_DIR, key);
            if (resource != null) return resource;
            String path = SKIN_DIR + "/" + key + ".png";
            try {
                File f = new File(path);
                if (f.isFile()) return ImageIO.read(f);
            } catch (IOException ignored) {}
            return null;
        }
    }

    private static BufferedImage loadBundledImage(Class<?> anchor, String resourceDir, String legacyDir, String key) {
        if (anchor == null || key == null || key.isBlank()) return null;
        String[] paths = {
                "/" + resourceDir + "/" + key + ".png",
                "/" + legacyDir + "/" + key + ".png"
        };
        for (String path : paths) {
            try (InputStream in = anchor.getResourceAsStream(path)) {
                if (in == null) continue;
                BufferedImage img = ImageIO.read(in);
                if (img != null) return img;
            } catch (IOException ignored) {}
        }
        return null;
    }

    private static void drawShipLegacy(Graphics2D g2, Ship ship) {
        if (!ship.alive) return;

        // Color palette per faction
        Color hull;
        Color trim;
        hull = factionHullColor(ship.faction);
        trim = factionTrimColor(ship.faction);

        int wx = (int) Math.round(ship.x);
        int wy = (int) Math.round(ship.y);

        Graphics2D g = (Graphics2D) g2.create();
        g.translate(wx, wy);
        g.rotate(ship.angle);

        // Stealth rendering: fade when not revealed.
        double sig = ship.effectiveSignature();
        if (ship.isCloaked() && sig < 0.99) {
            float a = (float) (0.22 + 0.78 * sig);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }

        Polygon hullPoly = switch (ship.role) {
            case PATROL -> hullPatrol(ship.radius);
            case PICKET -> hullPicket(ship.radius);
            case ARTILLERY_SHIP -> hullPatrol(ship.radius);
            case STEALTH_SHIP -> hullStealth(ship.radius);
            case FIGHTER -> hullFighter(ship.radius);
            case MISSILE_BOAT -> hullMissileBoat(ship.radius);
            case CIWS_CORVETTE -> hullCIWS(ship.radius);
            case LIGHT_CRUISER -> hullLightCruiser(ship.radius);
            case CRUISER, MEDIUM_CRUISER -> hullMediumCruiser(ship.radius);
            case BATTLECRUISER -> hullBattlecruiser(ship.radius);
            case BATTLESHIP -> hullBattleship(ship.radius);
            case DREADNOUGHT -> hullDreadnought(ship.radius);
            case SUPERSHIP -> hullDreadnought(ship.radius);
            case CARRIER -> hullCarrier(ship.radius);
            case BASE -> hullBase(ship.radius);
            default -> hullFrigate(ship.radius);
        };

        // Shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.translate(4, 4);
        g.fillPolygon(hullPoly);
        g.translate(-4, -4);

        // Main hull (subtle shading gradient)
        int frontX = 0;
        int backX = 0;
        for (int i = 0; i < hullPoly.npoints; i++) {
            int px = hullPoly.xpoints[i];
            if (i == 0) { frontX = backX = px; }
            else {
                if (px > frontX) frontX = px;
                if (px < backX) backX = px;
            }
        }
        Color hullDark = new Color(Math.max(0, hull.getRed() - 35), Math.max(0, hull.getGreen() - 35), Math.max(0, hull.getBlue() - 35));
        Color hullLight = new Color(Math.min(255, hull.getRed() + 25), Math.min(255, hull.getGreen() + 25), Math.min(255, hull.getBlue() + 25));
        GradientPaint gp = new GradientPaint(backX, 0, hullDark, frontX, 0, hullLight);
        g.setPaint(gp);
        g.fillPolygon(hullPoly);
        g.setPaint(null);

        // Outline
        g.setColor(new Color(0, 0, 0, 110));
        g.drawPolygon(hullPoly);

        // Plating + deck details
        drawPlating(g, ship, hull, trim);

        // Engines
        drawEngines(g, ship);

        // Bridge / superstructure
        drawBridge(g, ship);

        // Shield ring/faces
        drawShipShieldFaces(g, ship, new Area(hullPoly));

        // Turrets
        drawTurrets(g, ship);

        // Damage decals / scorch marks
        drawDamageDecals(g, ship, hullPoly);

        // Stealth shimmer outline
        if (ship.isCloaked() && sig < 0.99) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g.setColor(new Color(120, 220, 255, 110));
            g.drawPolygon(hullPoly);
        }

        g.dispose();

        // Name tag
        if (!isTinyStrikeCraft(ship.role)) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            g2.setColor(new Color(255, 255, 255, 130));
            g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
        }
    }

    private static boolean isTinyStrikeCraft(ShipRole role) {
        if (role == null) return false;
        return role == ShipRole.FIGHTER || role == ShipRole.BOMBER || role == ShipRole.DRONE;
    }

    private static void drawPlating(Graphics2D g, Ship ship, Color hull, Color trim) {
        int r = (int) Math.round(ship.radius);

        // Armor belt (inset polygon)
        Polygon base = switch (ship.role) {
            case PATROL -> hullPatrol(ship.radius);
            case PICKET -> hullPicket(ship.radius);
            case ARTILLERY_SHIP -> hullPatrol(ship.radius);
            case STEALTH_SHIP -> hullStealth(ship.radius);
            case FIGHTER -> hullFighter(ship.radius);
            case MISSILE_BOAT -> hullMissileBoat(ship.radius);
            case CIWS_CORVETTE -> hullCIWS(ship.radius);
            case LIGHT_CRUISER -> hullLightCruiser(ship.radius);
            case CRUISER, MEDIUM_CRUISER -> hullMediumCruiser(ship.radius);
            case BATTLECRUISER -> hullBattlecruiser(ship.radius);
            case BATTLESHIP -> hullBattleship(ship.radius);
            case DREADNOUGHT -> hullDreadnought(ship.radius);
            case SUPERSHIP -> hullDreadnought(ship.radius);
            case CARRIER -> hullCarrier(ship.radius);
            case BASE -> hullBase(ship.radius);
            default -> hullFrigate(ship.radius);
        };

        if (ship.role != ShipRole.BASE) {
            Polygon inset = scalePolygon(base, 0.78);
            int dr = clamp255(hull.getRed() - 40);
            int dg = clamp255(hull.getGreen() - 40);
            int db = clamp255(hull.getBlue() - 40);
            g.setColor(new Color(dr, dg, db, 120));
            g.fillPolygon(inset);

            g.setColor(new Color(255, 255, 255, 45));
            g.drawPolygon(inset);
        }

        // Deck stripe / panels
        g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 120));
        drawDeckDetails(g, ship);

        // Simple portholes / windows on larger hulls
        if (ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER || ship.role == ShipRole.CRUISER
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.BATTLESHIP
                || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.SUPERSHIP
                || ship.role == ShipRole.CARRIER || ship.role.isTitanOrMothership()) {
            g.setColor(new Color(255, 255, 255, 65));
            int n = Math.max(4, r / 4);
            for (int i = 0; i < n; i++) {
                int px = -r / 3 + i * (r / 3);
                g.fillRect(px, -r / 4, 2, 2);
                g.fillRect(px, r / 4, 2, 2);
            }
        }
    }

    private static void drawBridge(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);
        if (ship.role == ShipRole.BASE) return;

        // Carriers already have a runway-style deck; give them an offset island.
        if (ship.role == ShipRole.CARRIER) {
            g.setColor(new Color(255, 255, 255, 120));
            g.fillRoundRect(2, -r / 2, r / 3, r / 3, 8, 8);
            g.setColor(new Color(0, 0, 0, 80));
            g.drawRoundRect(2, -r / 2, r / 3, r / 3, 8, 8);
            return;
        }

        // Stealth ships: low-profile bridge
        if (ship.role == ShipRole.STEALTH_SHIP) {
            g.setColor(new Color(255, 255, 255, 70));
            g.fillRoundRect(r / 6, -r / 6, r / 5, r / 3, 10, 10);
            return;
        }

        int bx = r / 6;
        int by = -r / 6;
        int bw = r / 3;
        int bh = r / 3;

        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.SUPERSHIP
                || ship.role.isTitanOrMothership()) {
            bx = r / 10;
            by = -r / 5;
            bw = r / 2;
            bh = r / 2;
        }

        g.setColor(new Color(255, 255, 255, 110));
        g.fillRoundRect(bx, by, bw, bh, 10, 10);
        g.setColor(new Color(0, 0, 0, 90));
        g.drawRoundRect(bx, by, bw, bh, 10, 10);
    }

    private static void drawDeckDetails(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);

        switch (ship.role) {
            case CARRIER -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 8, 0, r + 8, 0);
                g.drawLine(-r + 8, -r / 3, r + 4, -r / 3);
                g.drawLine(-r + 8, r / 3, r + 4, r / 3);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawRect(-r / 2, -r + 6, r / 3, r / 2);

                g.setColor(new Color(255, 255, 255, 90));
                for (int i = 0; i < 5; i++) g.fillRect(-r / 2 + 3 + i * 5, -r + 10, 2, 2);
            }
            case MISSILE_BOAT -> {
                g.setColor(new Color(255, 255, 255, 110));
                g.drawRect(-r / 4, -r / 2, r / 2, r / 3);
                g.drawRect(-r / 4, r / 6, r / 2, r / 3);

                g.setColor(new Color(255, 255, 255, 70));
                g.drawLine(-r + 6, -r / 4, r - 2, -r / 4);
                g.drawLine(-r + 6, r / 4, r - 2, r / 4);
            }
            case ARTILLERY_SHIP -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, -r / 4, r + 6, -r / 6);
                g.drawLine(-r + 6, r / 4, r + 6, r / 6);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawOval(r / 6, -3, 6, 6);
            }
            case CIWS_CORVETTE -> {
                g.setColor(new Color(255, 255, 255, 120));
                g.drawLine(-r / 2, 0, -r / 2, -r / 2);
                g.drawOval(-r / 2 - 4, -r / 2 - 10, 8, 8);

                g.setColor(new Color(255, 255, 255, 90));
                g.drawOval(-2, -2, 4, 4);

                g.setColor(new Color(255, 255, 255, 70));
                g.drawLine(-r + 4, 0, r, 0);
            }
            case BASE -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawOval(-r, -r, r * 2, r * 2);
                g.drawOval(-(r - 10), -(r - 10), (r - 10) * 2, (r - 10) * 2);

                g.setColor(new Color(255, 255, 255, 110));
                g.drawLine(0, -r, 0, r);
                g.drawLine(-r, 0, r, 0);
            }
            case PATROL -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, -r / 4, r + 6, -r / 6);
                g.drawLine(-r + 6, r / 4, r + 6, r / 6);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawOval(r / 6, -3, 6, 6);
            }
            case PICKET -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, 0, r + 8, 0);
                g.drawLine(-r / 2, -r / 3, r / 2, -r / 6);
                g.drawLine(-r / 2, r / 3, r / 2, r / 6);
            }
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(-r + 6, -r / 3, r + 8, -r / 8);
                g.drawLine(-r + 6, r / 3, r + 8, r / 8);
                g.setColor(new Color(255, 255, 255, 115));
                g.drawRect(-r / 4, -r / 5, r / 3, r / 2);
                g.drawRect(r / 10, -r / 7, r / 4, r / 3);
            }
            case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP,
                 TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                 INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                 MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                 MOTHERSHIP -> {
                g.setColor(new Color(255, 255, 255, 75));
                g.drawLine(-r + 6, -r / 2, r + 10, -r / 6);
                g.drawLine(-r + 6, r / 2, r + 10, r / 6);
                g.drawLine(-r + 6, 0, r + 10, 0);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawRect(-r / 5, -r / 4, r / 3, r / 2);
                g.drawRect(r / 8, -r / 6, r / 3, r / 3);
            }
            default -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(-r + 6, -r / 3, r + 4, -r / 6);
                g.drawLine(-r + 6, r / 3, r + 4, r / 6);

                g.setColor(new Color(255, 255, 255, 110));
                g.drawRect(-r / 3, -r / 4, r / 3, r / 2);
            }
        }
    }

    private static Polygon scalePolygon(Polygon p, double s) {
        int n = p.npoints;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(p.xpoints[i] * s);
            ys[i] = (int) Math.round(p.ypoints[i] * s);
        }
        return new Polygon(xs, ys, n);
    }

    private static Polygon roomPolygonShipLocal(Ship ship, double[] normalizedXs, double[] normalizedYs) {
        if (ship == null || normalizedXs == null || normalizedYs == null) return null;
        int n = Math.min(normalizedXs.length, normalizedYs.length);
        if (n < 3) return null;
        HullRoomProjection projection = hullRoomProjection(ship);
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(normalizedXs[i] * projection.roomScaleX);
            ys[i] = (int) Math.round(normalizedYs[i] * projection.roomScaleY);
        }
        return new Polygon(xs, ys, n);
    }

    private static Point normalizedShipLocalPoint(Ship ship, double normalizedX, double normalizedY) {
        HullRoomProjection projection = hullRoomProjection(ship);
        int x = (int) Math.round(normalizedX * projection.localExtentX);
        int y = (int) Math.round(normalizedY * projection.localExtentY);
        return new Point(x, y);
    }

    private static HullRoomProjection hullRoomProjection(Ship ship) {
        double fallback = (ship == null) ? 16.0 : Math.max(8.0, ship.radius);
        if (ship == null) return new HullRoomProjection(fallback, fallback, fallback, fallback);

        Polygon hull = ShipHullSilhouette.hullPolygon(ship.role, ship.radius, ship.faction);
        if (hull == null || hull.npoints < 3) {
            return new HullRoomProjection(fallback, fallback, fallback, fallback);
        }

        double maxAbsX = 1.0;
        double maxAbsY = 1.0;
        for (int i = 0; i < hull.npoints; i++) {
            maxAbsX = Math.max(maxAbsX, Math.abs(hull.xpoints[i]));
            maxAbsY = Math.max(maxAbsY, Math.abs(hull.ypoints[i]));
        }
        return new HullRoomProjection(maxAbsX, maxAbsY, maxAbsX / 0.98, maxAbsY / 0.98);
    }

    private static final class HullRoomProjection {
        final double localExtentX;
        final double localExtentY;
        final double roomScaleX;
        final double roomScaleY;

        HullRoomProjection(double localExtentX, double localExtentY, double roomScaleX, double roomScaleY) {
            this.localExtentX = Math.max(1.0, localExtentX);
            this.localExtentY = Math.max(1.0, localExtentY);
            this.roomScaleX = Math.max(1.0, roomScaleX);
            this.roomScaleY = Math.max(1.0, roomScaleY);
        }
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static List<EnginePoint> enginePointsForHull(Polygon hull, ShipRole role, double radius) {
        if (hull == null || role == null || hull.npoints < 3) return java.util.Collections.emptyList();
        if (role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return java.util.Collections.emptyList();

        int count = engineCountForRole(role);
        if (count <= 0) return java.util.Collections.emptyList();

        int minX = hull.xpoints[0];
        int maxX = hull.xpoints[0];
        int minY = hull.ypoints[0];
        int maxY = hull.ypoints[0];
        for (int i = 1; i < hull.npoints; i++) {
            minX = Math.min(minX, hull.xpoints[i]);
            maxX = Math.max(maxX, hull.xpoints[i]);
            minY = Math.min(minY, hull.ypoints[i]);
            maxY = Math.max(maxY, hull.ypoints[i]);
        }

        double halfSpan = Math.max(3.0, Math.max(Math.abs(minY), Math.abs(maxY)) * engineSpanFraction(role));
        double minWidth = Math.max(3.0, radius * (count >= 5 ? 0.14 : count >= 3 ? 0.11 : 0.09));

        List<EngineBand> rows = collectEngineBands(hull, minX, maxX, minY, maxY, halfSpan, minWidth);
        if (rows.isEmpty()) {
            rows = collectEngineBands(hull, minX, maxX, minY, maxY, Math.max(Math.abs(minY), Math.abs(maxY)), 2.0);
        }
        if (rows.isEmpty()) return java.util.Collections.emptyList();

        boolean[] used = new boolean[rows.size()];
        List<EnginePoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = (count == 1) ? 0.5 : (double) i / (double) (count - 1);
            double targetY = rows.get(0).y + (rows.get(rows.size() - 1).y - rows.get(0).y) * t;
            int best = -1;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int j = 0; j < rows.size(); j++) {
                EngineBand row = rows.get(j);
                double score = Math.abs(row.y - targetY) + Math.abs(row.y) * 0.04 + (used[j] ? 4.0 : 0.0);
                if (score < bestScore) {
                    bestScore = score;
                    best = j;
                }
            }
            if (best < 0) continue;
            used[best] = true;
            EngineBand band = rows.get(best);
            double inset = Math.max(2.0, Math.min(radius * 0.18, band.width() * 0.26));
            int x = (int) Math.round(Math.min(band.right - 1.0, band.left + inset));
            points.add(new EnginePoint(x, band.y));
        }

        points.sort(java.util.Comparator.comparingInt(p -> p.y));
        return points;
    }

    private static List<EngineBand> collectEngineBands(Polygon hull, int minX, int maxX, int minY, int maxY,
                                                       double halfSpan, double minWidth) {
        int y0 = (int) Math.round(Math.max(minY, -halfSpan));
        int y1 = (int) Math.round(Math.min(maxY, halfSpan));
        List<EngineBand> rows = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (int x = minX - 1; x <= maxX + 1; x++) {
                if (!hull.contains(x + 0.5, y + 0.5) && !hull.contains(x, y)) continue;
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            if (right >= left && (right - left + 1) >= minWidth) {
                rows.add(new EngineBand(y, left, right));
            }
        }
        return rows;
    }

    private static int engineCountForRole(ShipRole role) {
        if (role == null) return 0;
        if (role == ShipRole.MOTHERSHIP) return 8;
        if (role.isTitan()) {
            return switch (role) {
                case VANGUARD_TITAN, FLEET_TELEPORTER_TITAN -> 5;
                case MOBILE_STATION_TITAN -> 4;
                default -> 6;
            };
        }
        return switch (role) {
            case DRONE, FIGHTER, BOMBER, STEALTH_SHIP, PD_CRAFT -> 1;
            case PATROL, FRIGATE, PICKET, ARTILLERY_SHIP, LIGHT_CRUISER, MISSILE_BOAT, MINER, TRANSPORT, HAULER, DRONE_CARRIER, CARRIER -> 2;
            case MEDIUM_CRUISER, CRUISER -> 3;
            case BATTLECRUISER, SUPERSHIP -> 4;
            case BATTLESHIP -> 5;
            case DREADNOUGHT -> 6;
            default -> 2;
        };
    }

    private static double engineSpanFraction(ShipRole role) {
        if (role == null) return 0.40;
        if (role == ShipRole.MOTHERSHIP) return 0.68;
        if (role.isTitan()) return 0.60;
        return switch (role) {
            case DRONE, FIGHTER, BOMBER, STEALTH_SHIP, PD_CRAFT -> 0.18;
            case PATROL, PICKET, FRIGATE, MISSILE_BOAT, MINER -> 0.28;
            case ARTILLERY_SHIP -> 0.28;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, TRANSPORT, HAULER -> 0.38;
            case CARRIER, DRONE_CARRIER -> 0.34;
            case BATTLECRUISER, BATTLESHIP -> 0.48;
            case DREADNOUGHT, SUPERSHIP -> 0.56;
            default -> 0.40;
        };
    }

    private static void drawEngineNozzlePass(Graphics2D g, Ship ship, List<EnginePoint> engines, double radius) {
        if (g == null || ship == null || engines == null || engines.isEmpty()) return;
        double output = engineOutputIntensity(ship);
        if (output <= 1e-3) return;

        Graphics2D g2 = (Graphics2D) g.create();
        double shimmer = engineShimmerIntensity(ship);
        double nozzleDia = Math.max(4.0, radius * (engines.size() >= 4 ? 0.095 : 0.13));
        double plumeLen = Math.max(7.0, radius * (0.24 + 0.54 * output));
        double plumeHalf = Math.max(2.5, nozzleDia * (0.55 + 0.55 * output));
        Color plumeBase = engineExhaustColor(ship.faction);
        Color plumeHot = engineExhaustCoreColor(ship.faction);

        for (EnginePoint p : engines) {
            if (p == null) continue;

            int aftX = p.x - 1;
            int tailX = (int) Math.round(aftX - plumeLen);
            int nozzleR = Math.max(2, (int) Math.round(nozzleDia * 0.52));
            int haloR = Math.max(nozzleR + 2, (int) Math.round(nozzleDia * (0.95 + output * 0.32)));
            int flare = Math.max(2, (int) Math.round(plumeHalf));

            Paint oldPaint = g2.getPaint();

            Polygon plume = new Polygon(
                    new int[]{aftX, tailX, aftX},
                    new int[]{(int) Math.round(p.y - flare), p.y, (int) Math.round(p.y + flare)},
                    3
            );
            g2.setPaint(new GradientPaint(
                    aftX, (float) p.y, withAlpha(plumeBase, (int) Math.round(92 + 92 * output)),
                    tailX, (float) p.y, withAlpha(plumeBase, 0)));
            g2.fillPolygon(plume);

            int coreHalf = Math.max(1, (int) Math.round(flare * (0.34 + 0.18 * shimmer)));
            Polygon corePlume = new Polygon(
                    new int[]{aftX, tailX + Math.max(1, nozzleR), aftX},
                    new int[]{(int) Math.round(p.y - coreHalf), p.y, (int) Math.round(p.y + coreHalf)},
                    3
            );
            g2.setPaint(new GradientPaint(
                    aftX, (float) p.y, withAlpha(plumeHot, (int) Math.round(126 + 88 * output)),
                    tailX, (float) p.y, withAlpha(plumeHot, 0)));
            g2.fillPolygon(corePlume);

            g2.setColor(withAlpha(plumeBase, (int) Math.round(88 + 74 * output)));
            g2.fillOval(aftX - haloR, p.y - haloR, haloR * 2, haloR * 2);

            g2.setColor(withAlpha(plumeHot, (int) Math.round(148 + 72 * output)));
            g2.fillOval(aftX - nozzleR, p.y - nozzleR, nozzleR * 2, nozzleR * 2);

            int hotW = Math.max(2, (int) Math.round(nozzleR * 1.1));
            int hotH = Math.max(2, (int) Math.round(nozzleR * 0.82));
            g2.setColor(withAlpha(Color.WHITE, (int) Math.round(110 + 70 * output)));
            g2.fillOval(aftX - hotW / 2, p.y - hotH / 2, hotW, hotH);

            if (shimmer > 0.02) {
                Stroke oldStroke = g2.getStroke();
                g2.setStroke(new BasicStroke(Math.max(1.1f, (float) (0.9 + shimmer * 1.1)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(mixColor(plumeHot, Color.WHITE, 0.28), (int) Math.round(54 + shimmer * 80)));
                g2.drawLine(aftX, p.y, tailX, p.y);
                g2.setStroke(oldStroke);
            }

            g2.setPaint(oldPaint);
        }

        g2.dispose();
    }

    private static double engineOutputIntensity(Ship ship) {
        if (ship == null || ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) return 0.0;
        double dt = Math.max(1e-6, GameContext.DT);
        double speedPerSec = Math.hypot(ship.vx, ship.vy) / dt;
        double ceiling = Math.max(1.0, MovementModel.speedCeiling(ship));
        double speedFrac = MathUtil.clamp(speedPerSec / ceiling, 0.0, 1.0);
        if (ship.isEmergencyThrustActive()) {
            return MathUtil.clamp(0.92 + 0.08 * (1.0 - ship.emergencyThrustHeat()), 0.0, 1.0);
        }
        double motionThreshold = Math.max(6.0, ceiling * 0.035);
        if (speedPerSec <= motionThreshold) return 0.0;
        double motionFrac = MathUtil.clamp((speedPerSec - motionThreshold) / Math.max(10.0, ceiling * 0.22), 0.0, 1.0);
        double output = motionFrac * (0.22 + ship.powerEnginesFrac() * 0.30 + speedFrac * 0.48);
        return MathUtil.clamp(output, 0.0, 1.0);
    }

    private static double engineShimmerIntensity(Ship ship) {
        double output = engineOutputIntensity(ship);
        return MathUtil.clamp((output - 0.44) / 0.48, 0.0, 1.0);
    }

    private static List<EnginePoint> enginePointsForLegacy(Ship ship) {
        if (ship == null || ship.role == null || ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) {
            return java.util.Collections.emptyList();
        }

        int r = Math.max(6, (int) Math.round(ship.radius));
        Polygon hull = ShipHullSilhouette.hullPolygon(ship.role, r, ship.faction);
        List<EnginePoint> derived = enginePointsForHull(hull, ship.role, ship.radius);
        if (!derived.isEmpty()) return derived;

        int count = engineCountForRole(ship.role);
        int x = -r + 1;
        if (count == 1) {
            return java.util.Collections.singletonList(new EnginePoint(x, 0));
        }

        List<EnginePoint> points = new ArrayList<>();
        double span = Math.max(3.0, r * 0.48);
        for (int i = 0; i < count; i++) {
            double t = (count == 1) ? 0.5 : (double) i / (double) (count - 1);
            int y = (int) Math.round(-span + t * span * 2.0);
            points.add(new EnginePoint(x, y));
        }
        return points;
    }

    private static void drawEngines(Graphics2D g, Ship ship) {
        if (ship == null) return;
        drawEngineNozzlePass(g, ship, enginePointsForLegacy(ship), ship.radius);
    }

    private static Color engineExhaustColor(Faction faction) {
        Color hull = factionHullColor(faction);
        Color trim = factionTrimColor(faction);
        return mixColor(hull, trim, 0.58);
    }

    private static Color engineExhaustCoreColor(Faction faction) {
        return mixColor(engineExhaustColor(faction), Color.WHITE, 0.58);
    }

    private static void drawTurrets(Graphics2D g2, Ship ship) {
        if (ship == null || ship.turrets == null) return;
        if (ship.role == ShipRole.FIGHTER || ship.role == ShipRole.BOMBER || ship.role == ShipRole.DRONE) return;

        Color accent = factionTrimColor(ship.faction);
        final double GLOBAL_TURRET_SCALE = 0.44;
        for (Turret t : ship.turrets) {
            if (t == null) continue;

            double rel = MathUtil.normalizeAngle(t.angle - ship.angle);
            double fireFrac = turretFireFraction(t);
            TurretVisualScale scale = turretVisualScale(ship.role, t.kind);
            double bodyScale = scale.bodyScale * GLOBAL_TURRET_SCALE;
            double barrelScale = scale.barrelScale * GLOBAL_TURRET_SCALE;
            String styleKey = turretStyleKey(ship, t);
            BufferedImage turretSkin = TurretSkinLibrary.getTurretSkin(styleKey, ship.role, ship.faction);

            Graphics2D tg = (Graphics2D) g2.create();
            tg.translate(t.localX, t.localY);
            tg.rotate(rel);

            if (turretSkin != null) {
                drawTurretSkinSprite(tg, turretSkin, styleKey, fireFrac, bodyScale);
            } else {
                if (t.kind == Turret.Kind.MISSILE) {
                    drawMissilePodTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else if (isHeavyTurretRole(ship.role)) {
                    drawHeavyTripleTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else if (ship.role == ShipRole.STEALTH_SHIP) {
                    drawStealthFlushTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else if (ship.usesBeamBoltPrimaryVisuals()) {
                    drawBeamEmitterTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else {
                    drawTwinGunTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                }
            }

            tg.dispose();
        }

        if (ship.hasCIWS) {
            double ciwsScale = turretVisualScale(ship.role, Turret.Kind.GUN).ciwsScale * GLOBAL_TURRET_SCALE;
            BufferedImage ciwsSkin = TurretSkinLibrary.getTurretSkin("ciws", ship.role, ship.faction);
            if (ciwsSkin != null) drawCiwsSkinSprite(g2, ciwsSkin, ciwsScale);
            else drawCIWSTurret(g2, ciwsScale);
        }
    }

    private static String turretStyleKey(Ship ship, Turret t) {
        if (t == null) return "twin_gun";
        if (t.kind == Turret.Kind.MISSILE) return "missile_pod";
        if (ship != null && isHeavyTurretRole(ship.role)) return "heavy_triple";
        if (ship != null && ship.role == ShipRole.STEALTH_SHIP) return "stealth_flush";
        if (ship != null && ship.usesBeamBoltPrimaryVisuals()) return "beam_emitter";
        return "twin_gun";
    }

    private static void drawTurretSkinSprite(Graphics2D g, BufferedImage skin, String styleKey,
                                             double fireFrac, double bodyScale) {
        if (skin == null) return;
        double scaleNorm = Math.max(0.55, bodyScale / 0.5);
        double w = 26.0 * scaleNorm;
        double h = 16.0 * scaleNorm;

        if ("heavy_triple".equals(styleKey)) {
            w *= 1.24;
            h *= 1.12;
        } else if ("missile_pod".equals(styleKey)) {
            w *= 1.14;
            h *= 1.08;
        } else if ("stealth_flush".equals(styleKey)) {
            w *= 0.96;
            h *= 0.88;
        } else if ("beam_emitter".equals(styleKey)) {
            w *= 1.05;
            h *= 1.02;
        }

        int drawW = Math.max(8, (int) Math.round(w));
        int drawH = Math.max(6, (int) Math.round(h));
        int recoilPx = (int) Math.round(fireFrac * Math.max(1.0, drawW * 0.07));
        int x = -drawW / 2 - recoilPx;
        int y = -drawH / 2;
        g.drawImage(skin, x, y, drawW, drawH, null);
    }

    private static void drawCiwsSkinSprite(Graphics2D g, BufferedImage skin, double ciwsScale) {
        if (skin == null) return;
        double scaleNorm = Math.max(0.65, ciwsScale / 0.5);
        int draw = Math.max(10, (int) Math.round(20.0 * scaleNorm));
        g.drawImage(skin, -draw / 2, -draw / 2, draw, draw, null);
    }

    private static boolean isHeavyTurretRole(ShipRole role) {
        return role == ShipRole.BATTLECRUISER || role == ShipRole.BATTLESHIP
                || role == ShipRole.DREADNOUGHT || role == ShipRole.SUPERSHIP
                || (role != null && role.isTitanOrMothership());
    }

    private static double turretFireFraction(Turret t) {
        if (t == null || t.cooldown <= 1e-6) return 0.0;
        double frac = t.getCooldownRemaining() / t.cooldown;
        return Math.max(0.0, Math.min(1.0, frac));
    }

    private static Color mix(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() * (1.0 - clamped) + b.getRed() * clamped);
        int g = (int) Math.round(a.getGreen() * (1.0 - clamped) + b.getGreen() * clamped);
        int bl = (int) Math.round(a.getBlue() * (1.0 - clamped) + b.getBlue() * clamped);
        return new Color(clamp255(r), clamp255(g), clamp255(bl));
    }

    private static TurretVisualScale turretVisualScale(ShipRole role, Turret.Kind kind) {
        if (role == null) return new TurretVisualScale(1.0, 1.0, 1.0);
        if (role == ShipRole.MOTHERSHIP) return new TurretVisualScale(1.56, 1.30, 1.28);
        if (role.isTitan()) {
            if (kind == Turret.Kind.MISSILE) return new TurretVisualScale(1.24, 1.12, 1.18);
            return switch (role) {
                case VANGUARD_TITAN, INTERDICTION_TITAN -> new TurretVisualScale(1.34, 1.18, 1.20);
                case ARTILLERY_TITAN, HYPERWEAPON_TITAN -> new TurretVisualScale(1.48, 1.28, 1.22);
                case MOBILE_STATION_TITAN -> new TurretVisualScale(1.28, 1.14, 1.24);
                default -> new TurretVisualScale(1.42, 1.24, 1.20);
            };
        }
        return switch (role) {
            case PATROL, PICKET, FIGHTER -> new TurretVisualScale(0.84, 0.86, 0.88);
            case ARTILLERY_SHIP -> new TurretVisualScale(1.08, 1.04, 1.00);
            case FRIGATE, MISSILE_BOAT, CIWS_CORVETTE, MINER -> new TurretVisualScale(0.95, 0.96, 0.95);
            case LIGHT_CRUISER, CRUISER, MEDIUM_CRUISER, STEALTH_SHIP -> new TurretVisualScale(1.02, 1.01, 0.98);
            case BATTLECRUISER -> new TurretVisualScale(1.14, 1.08, 1.02);
            case BATTLESHIP -> new TurretVisualScale(1.22, 1.12, 1.08);
            case DREADNOUGHT -> new TurretVisualScale(1.30, 1.16, 1.14);
            case SUPERSHIP -> new TurretVisualScale(1.40, 1.22, 1.20);
            case CARRIER -> {
                if (kind == Turret.Kind.MISSILE) yield new TurretVisualScale(1.02, 0.98, 1.00);
                yield new TurretVisualScale(0.90, 0.88, 0.98);
            }
            case BASE -> new TurretVisualScale(1.18, 1.10, 1.18);
            default -> new TurretVisualScale(1.0, 1.0, 1.0);
        };
    }

    private static void drawTwinGunTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int baseW = r + 4;
        int baseH = r + 3;
        int capW = r + 8;
        int capH = r + 5;
        int barrelLen = (int) Math.max(8, Math.round(t.barrelLen * barrelScale));
        int recoil = (int) Math.round(fireFrac * 3.0);

        g.setColor(new Color(36, 40, 48, 210));
        g.fillOval(-baseW / 2, -baseH / 2, baseW, baseH);

        g.setColor(mix(new Color(120, 128, 140), accent, 0.35));
        g.fillRoundRect(-capW / 2, -capH / 2, capW, capH, 4, 4);
        g.setColor(new Color(0, 0, 0, 150));
        g.drawRoundRect(-capW / 2, -capH / 2, capW, capH, 4, 4);

        int yOff = Math.max(2, r / 3);
        int bw = Math.max(2, r / 3);
        g.setColor(new Color(210, 220, 235, 235));
        g.fillRoundRect(0 - recoil, -yOff - bw / 2, barrelLen, bw, 2, 2);
        g.fillRoundRect(0 - recoil, yOff - bw / 2, barrelLen, bw, 2, 2);
        g.setColor(new Color(28, 30, 36, 180));
        g.drawRoundRect(0 - recoil, -yOff - bw / 2, barrelLen, bw, 2, 2);
        g.drawRoundRect(0 - recoil, yOff - bw / 2, barrelLen, bw, 2, 2);

        if (fireFrac > 0.82) {
            int fx = barrelLen - recoil;
            g.setColor(new Color(255, 230, 150, 160));
            g.fillOval(fx - 3, -yOff - 2, 6, 6);
            g.fillOval(fx - 3, yOff - 2, 6, 6);
        }
    }

    private static void drawHeavyTripleTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(5, Math.round((t.radius + 1) * bodyScale));
        int baseW = r + 7;
        int baseH = r + 5;
        int capW = r + 12;
        int capH = r + 7;
        int barrelLen = (int) Math.max(11, Math.round((t.barrelLen + 2) * barrelScale));
        int recoil = (int) Math.round(fireFrac * 4.0);

        g.setColor(new Color(58, 66, 80, 200));
        g.fillRoundRect(-baseW / 2, -baseH / 2, baseW, baseH, 4, 4);

        g.setColor(mix(new Color(110, 118, 134), accent, 0.40));
        g.fillRoundRect(-capW / 2, -capH / 2, capW, capH, 5, 5);
        g.setColor(new Color(0, 0, 0, 160));
        g.drawRoundRect(-capW / 2, -capH / 2, capW, capH, 5, 5);

        int bw = Math.max(2, r / 3);
        int[] ys = new int[]{-Math.max(3, r / 2), 0, Math.max(3, r / 2)};
        g.setColor(new Color(210, 220, 235, 235));
        for (int y : ys) {
            g.fillRoundRect(1 - recoil, y - bw / 2, barrelLen, bw, 2, 2);
            g.setColor(new Color(28, 30, 36, 180));
            g.drawRoundRect(1 - recoil, y - bw / 2, barrelLen, bw, 2, 2);
            g.setColor(new Color(210, 220, 235, 235));
        }

        if (fireFrac > 0.8) {
            int fx = 1 + barrelLen - recoil;
            g.setColor(new Color(255, 225, 135, 170));
            for (int y : ys) g.fillOval(fx - 3, y - 3, 6, 6);
        }
    }

    private static void drawMissilePodTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int w = r + 10;
        int h = r + 8;
        int recoil = (int) Math.round(fireFrac * 2.0);

        g.setColor(new Color(34, 38, 46, 215));
        g.fillRoundRect(-w / 2, -h / 2, w, h, 4, 4);
        g.setColor(mix(new Color(126, 132, 142), accent, 0.30));
        g.fillRoundRect(-w / 2 + 1, -h / 2 + 1, w - 2, h - 2, 4, 4);

        g.setColor(new Color(20, 24, 30, 190));
        int cell = Math.max(2, r / 3);
        for (int yy = -1; yy <= 1; yy += 2) {
            for (int xx = 0; xx < 3; xx++) {
                int cx = -w / 4 + xx * (cell + 2);
                int cy = yy * (cell + 1) - recoil;
                g.fillRect(cx, cy, cell, cell);
            }
        }

        int hatchLen = (int) Math.max(8, Math.round(t.barrelLen * 0.55 * barrelScale));
        g.setColor(new Color(190, 205, 225, 220));
        g.fillRoundRect(0 - recoil, -1, hatchLen, 2, 2, 2);
        if (fireFrac > 0.85) {
            int fx = hatchLen - recoil;
            g.setColor(new Color(255, 180, 110, 170));
            g.fillOval(fx - 3, -3, 6, 6);
        }
    }

    private static void drawBeamEmitterTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(5, Math.round((t.radius + 1) * bodyScale));
        int w = r + 11;
        int h = r + 7;
        int barrelLen = (int) Math.max(11, Math.round((t.barrelLen + 2) * barrelScale));
        int recoil = (int) Math.round(fireFrac * 3.0);

        g.setColor(new Color(28, 36, 48, 215));
        g.fillRoundRect(-w / 2, -h / 2, w, h, 5, 5);

        g.setColor(mix(new Color(102, 128, 152), accent, 0.26));
        g.fillRoundRect(-w / 2 + 1, -h / 2 + 1, w - 2, h - 2, 5, 5);
        g.setColor(new Color(10, 16, 24, 190));
        g.drawRoundRect(-w / 2, -h / 2, w, h, 5, 5);

        int[] ys = new int[]{-Math.max(3, r / 2), 0, Math.max(3, r / 2)};
        int bw = Math.max(2, r / 4);
        Color barrelFill = new Color(180, 232, 255, 232);
        Color barrelEdge = new Color(24, 28, 36, 180);
        for (int y : ys) {
            g.setColor(barrelFill);
            g.fillRoundRect(1 - recoil, y - bw / 2, barrelLen, bw, 2, 2);
            g.setColor(barrelEdge);
            g.drawRoundRect(1 - recoil, y - bw / 2, barrelLen, bw, 2, 2);
        }

        g.setColor(new Color(146, 226, 255, 108));
        g.drawLine(0 - recoil, 0, barrelLen - recoil, 0);

        if (fireFrac > 0.56) {
            int fx = 1 + barrelLen - recoil;
            for (int y : ys) {
                g.setColor(new Color(142, 236, 255, 178));
                g.fillOval(fx - 4, y - 4, 8, 8);
                g.setColor(new Color(255, 255, 255, 184));
                g.fillOval(fx - 2, y - 2, 4, 4);
            }
        }
    }

    private static void drawStealthFlushTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int len = (int) Math.max(9, Math.round(t.barrelLen * 0.8 * barrelScale));
        int recoil = (int) Math.round(fireFrac * 2.0);

        Polygon p = new Polygon(
                new int[]{-r, 0, r + 2, 0},
                new int[]{0, -r / 2, 0, r / 2}, 4);
        g.setColor(mix(new Color(68, 86, 108), accent, 0.18));
        g.fillPolygon(p);
        g.setColor(new Color(150, 210, 245, 130));
        g.drawLine(-1, 0, len - recoil, 0);

        if (fireFrac > 0.8) {
            int fx = len - recoil;
            g.setColor(new Color(170, 235, 255, 130));
            g.fillOval(fx - 2, -2, 4, 4);
        }
    }

    private static void drawCIWSTurret(Graphics2D g2, double ciwsScale) {
        Graphics2D g = (Graphics2D) g2.create();
        int rr = Math.max(3, (int) Math.round(4 * ciwsScale));
        int barrelLen = Math.max(7, (int) Math.round(9 * ciwsScale));
        g.setColor(new Color(80, 90, 105, 200));
        g.fillOval(-rr, -rr, rr * 2, rr * 2);
        g.setColor(new Color(205, 220, 240, 200));
        g.drawOval(-rr, -rr, rr * 2, rr * 2);

        long t = System.nanoTime();
        double a = (t % 2_000_000_000L) / 2_000_000_000.0 * Math.PI * 2.0;
        for (int i = 0; i < 3; i++) {
            double aa = a + i * (Math.PI * 2.0 / 3.0);
            int x2 = (int) Math.round(Math.cos(aa) * barrelLen);
            int y2 = (int) Math.round(Math.sin(aa) * barrelLen);
            g.setColor(new Color(190, 210, 235, 170));
            g.drawLine(0, 0, x2, y2);
        }
        g.dispose();
    }

    private static final class TurretVisualScale {
        final double bodyScale;
        final double barrelScale;
        final double ciwsScale;

        TurretVisualScale(double bodyScale, double barrelScale, double ciwsScale) {
            this.bodyScale = bodyScale;
            this.barrelScale = barrelScale;
            this.ciwsScale = ciwsScale;
        }
    }


    private static void drawDamageDecals(Graphics2D g, Ship ship, Shape hullShape) {
        if (ship == null || hullShape == null) return;
        if (ship.hpMax <= 0) return;

        double hpFrac = Math.max(0.0, Math.min(1.0, ship.hp / (double) ship.hpMax));
        double dmg = 1.0 - hpFrac; // 0..1
        if (dmg < 0.12) return;

        Rectangle bounds = hullShape.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return;
        int span = Math.max(bounds.width, bounds.height);
        double screenScale = hullDamageDetailScale(g);
        double screenSpan = span * screenScale;
        if (screenSpan < HULL_DAMAGE_DETAIL_MIN_SCREEN_SPAN) return;
        List<Ship.HullImpactMark> marks = ship.hullImpactMarks();

        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();
        g.setClip(hullShape);

        try {
            if (!marks.isEmpty()) {
                int mCount = marks.size();
                int start = Math.max(0, mCount - 18);
                for (int i = start; i < mCount; i++) {
                    Ship.HullImpactMark mark = marks.get(i);
                    int px = (int) Math.round(mark.localX);
                    int py = (int) Math.round(mark.localY);
                    double sev = MathUtil.clamp(mark.severity, 0.04, 1.0);

                    int scorchSz = (int) Math.round(Math.max(1.0, (2.0 + sev * 10.0 + dmg * 5.0) * IMPACT_DECAL_SCALE));
                    int scorchA = (int) MathUtil.clamp(54 + sev * 108 + dmg * 42, 0, 200);
                    g.setColor(new Color(0, 0, 0, scorchA));
                    g.fillOval(px - scorchSz, py - scorchSz, scorchSz * 2, scorchSz * 2);
                    Color traceTint = roomTraceTint(mark.roomId,
                            (int) MathUtil.clamp(14 + sev * 60 + dmg * 24, 0, 132));

                    // Deformation: a displaced dent shadow + warm rim at the impact point.
                    int dent = Math.max(1, (int) Math.round((2 + sev * 6) * IMPACT_DECAL_SCALE));
                    g.setColor(new Color(5, 5, 6, (int) MathUtil.clamp(26 + sev * 80, 0, 145)));
                    g.fillOval(px - dent + 1, py - dent + 1, dent * 2, dent * 2);
                    g.setColor(traceTint);
                    g.drawOval(px - scorchSz, py - scorchSz, scorchSz * 2, scorchSz * 2);

                    double seedA = Math.abs(mark.localX * 0.027 + mark.localY * 0.019 + i * 0.171);
                    double dir = (seedA - Math.floor(seedA)) * Math.PI * 2.0;
                    int len = (int) Math.round(Math.max(1.0, (4 + sev * 18 + dmg * span * 0.10) * IMPACT_DECAL_SCALE));
                    int x2 = px + (int) Math.round(Math.cos(dir) * len);
                    int y2 = py + (int) Math.round(Math.sin(dir) * len);
                    float width = (float) Math.max(0.45, (0.9 + sev * 2.2) * IMPACT_DECAL_SCALE);
                    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(12, 12, 14, (int) MathUtil.clamp(52 + sev * 95, 0, 180)));
                    g.drawLine(px, py, x2, y2);
                    g.setStroke(new BasicStroke(Math.max(0.35f, width * 0.42f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(roomTraceTint(mark.roomId, (int) MathUtil.clamp(12 + sev * 52, 0, 116)));
                    g.drawLine(px, py, x2, y2);

                }

                if (dmg > 0.55) {
                    int smoke = Math.min(8, Math.max(2, (int) Math.round(2 + dmg * 6)));
                    for (int i = 0; i < smoke; i++) {
                        Ship.HullImpactMark mark = marks.get(Math.max(0, mCount - 1 - i % Math.max(1, mCount)));
                        int px = (int) Math.round(mark.localX);
                        int py = (int) Math.round(mark.localY);
                        int sz = (int) Math.max(2, Math.round((6 + (0.4 + mark.severity) * 8) * IMPACT_DECAL_SCALE));
                        int a = (int) MathUtil.clamp(20 + (dmg - 0.55) * 140, 0, 110);
                        g.setColor(new Color(30, 30, 30, a));
                        g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
                    }
                }
            } else {
                // Fallback for legacy/non-positional damage events.
                int n = (int) Math.round(4 + dmg * 12);
                long seed = (long) System.identityHashCode(ship) * 0x9E3779B97F4A7C15L;
                Random rng = new Random(seed);
                for (int i = 0; i < n; i++) {
                    Point hit = randomPointInShape(rng, bounds, hullShape, 18);
                    int px = hit.x;
                    int py = hit.y;
                    int sz = (int) Math.max(1, Math.round((2 + rng.nextDouble() * (4 + dmg * 10)) * IMPACT_DECAL_SCALE));
                    int a = (int) MathUtil.clamp(48 + dmg * 140, 0, 175);
                    g.setColor(new Color(0, 0, 0, a));
                    g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
                }
            }
            boolean patchesAvailable = ShipDamagePatchLibrary.hasAnyPatch();
            if (patchesAvailable && screenSpan >= HULL_DAMAGE_PATCH_MIN_SCREEN_SPAN) {
                drawImpactMachineryPatches(g, ship, hullShape, marks, dmg, span);
            }
            if (screenSpan >= HULL_DAMAGE_BREACH_MIN_SCREEN_SPAN) {
                drawDestroyedHullBreaches(g, ship, hullShape, marks, span);
            }
            if (screenSpan >= HULL_DAMAGE_IMPACT_OVERLAY_MIN_SCREEN_SPAN) {
                drawImpactHoleOverlays(g, marks, patchesAvailable);
            }
        } finally {
            g.setStroke(oldStroke);
            g.setClip(oldClip);
        }
    }

    private static void drawImpactMachineryPatches(Graphics2D g,
                                                   Ship ship,
                                                   Shape hullShape,
                                                   List<Ship.HullImpactMark> marks,
                                                   double damageFraction,
                                                   int span) {
        if (g == null || ship == null || hullShape == null || marks == null || marks.isEmpty()) return;

        Shape hullClip = g.getClip();
        int start = Math.max(0, marks.size() - 18);
        for (int i = start; i < marks.size(); i++) {
            Ship.HullImpactMark mark = marks.get(i);
            if (!shouldDrawMachineryPatch(mark)) continue;

            int px = (int) Math.round(mark.localX);
            int py = (int) Math.round(mark.localY);
            double sev = MathUtil.clamp(mark.severity, 0.04, 1.0);
            double radius = machineryPatchRenderRadius(mark, sev, span);
            if (radius < HULL_DAMAGE_PATCH_MIN_RENDER_RADIUS) continue;

            ShipDamagePatchLibrary.Selection selection =
                    ShipDamagePatchLibrary.select(ship.faction, mark.localX, mark.localY, sev, i);
            if (selection == null || selection.image == null) continue;

            double wobble = seedUnit(mark.localX, mark.localY, i, 0.417);
            double rx = radius * (0.92 + wobble * 0.24);
            double ry = radius * (0.78 + seedUnit(mark.localY, mark.localX, i, 0.793) * 0.30);
            Shape holeShape = createBreachBlob(px, py, rx, ry, breachSeed(ship, mark.roomId, i + 211));
            Area holeArea = new Area(holeShape);
            holeArea.intersect(new Area(hullShape));
            if (holeArea.isEmpty()) continue;

            Graphics2D gp = (Graphics2D) g.create();
            try {
                Area patchClip = new Area(hullClip == null ? hullShape : hullClip);
                patchClip.intersect(holeArea);
                gp.setClip(patchClip);
                gp.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gp.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                float alpha = (float) MathUtil.clamp(0.26 + sev * 0.34 + damageFraction * 0.28, 0.24, 0.78);
                gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                BufferedImage image = selection.image;
                double drawSize = Math.max(rx, ry) * 2.75;
                AffineTransform tx = new AffineTransform();
                tx.translate(px, py);
                tx.rotate(selection.quarterTurns * Math.PI * 0.5);
                if (selection.flipX) tx.scale(-1.0, 1.0);
                tx.scale(drawSize / Math.max(1.0, image.getWidth()), drawSize / Math.max(1.0, image.getHeight()));
                tx.translate(-image.getWidth() * 0.5, -image.getHeight() * 0.5);
                gp.drawImage(image, tx, null);

                gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        (float) MathUtil.clamp(0.22 + damageFraction * 0.18, 0.20, 0.42)));
                gp.setColor(new Color(2, 3, 5, 118));
                gp.fill(holeArea);
            } finally {
                gp.dispose();
            }

            Stroke oldStroke = g.getStroke();
            float rim = (float) Math.max(0.75, Math.min(2.2, radius * 0.24));
            g.setStroke(new BasicStroke(rim, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(3, 3, 5, (int) MathUtil.clamp(118 + sev * 72, 0, 210)));
            g.draw(holeArea);
            g.setStroke(new BasicStroke(Math.max(0.45f, rim * 0.45f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(roomTraceTint(mark.roomId, (int) MathUtil.clamp(26 + sev * 52, 0, 118)));
            g.draw(holeArea);
            g.setStroke(oldStroke);
        }
    }

    private static void drawImpactHoleOverlays(Graphics2D g, List<Ship.HullImpactMark> marks, boolean patchesAvailable) {
        if (g == null || marks == null || marks.isEmpty()) return;
        int start = Math.max(0, marks.size() - 18);
        for (int i = start; i < marks.size(); i++) {
            Ship.HullImpactMark mark = marks.get(i);
            if (mark == null || mark.breachRadius <= 0.01) continue;

            int px = (int) Math.round(mark.localX);
            int py = (int) Math.round(mark.localY);
            double sev = MathUtil.clamp(mark.severity, 0.04, 1.0);
            int br = (int) Math.round(Math.max(1.0, mark.breachRadius * IMPACT_DECAL_SCALE));
            boolean machineryBreach = patchesAvailable && shouldDrawMachineryPatch(mark);

            int coreAlpha = machineryBreach
                    ? (int) MathUtil.clamp(26 + sev * 42, 0, 84)
                    : (int) MathUtil.clamp(95 + sev * 110, 0, 220);
            g.setColor(new Color(8, 8, 10, coreAlpha));
            g.fillOval(px - br, py - br, br * 2, br * 2);
            if (machineryBreach) {
                Stroke oldStroke = g.getStroke();
                g.setStroke(new BasicStroke(Math.max(0.75f, br * 0.38f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0, 0, 0, (int) MathUtil.clamp(72 + sev * 72, 0, 170)));
                g.drawOval(px - br, py - br, br * 2, br * 2);
                g.setStroke(oldStroke);
            }
            g.setColor(roomTraceTint(mark.roomId, (int) MathUtil.clamp(26 + sev * 58, 0, 140)));
            g.drawOval(px - br, py - br, br * 2, br * 2);
        }
    }

    private static boolean shouldDrawMachineryPatch(Ship.HullImpactMark mark) {
        if (mark == null || mark.breachRadius <= 0.01) return false;
        if (mark.breachRadius * IMPACT_DECAL_SCALE >= HULL_DAMAGE_PATCH_MIN_RENDER_RADIUS) return true;
        return mark.severity >= 0.16 && mark.breachRadius * IMPACT_DECAL_SCALE >= HULL_DAMAGE_PATCH_MIN_RENDER_RADIUS * 0.62;
    }

    private static double machineryPatchRenderRadius(Ship.HullImpactMark mark, double severity, int span) {
        if (mark == null) return 0.0;
        double structuralLift = Math.max(0.0, span) * (0.012 + severity * 0.018);
        return Math.max(1.0, (mark.breachRadius * 1.92 + structuralLift) * IMPACT_DECAL_SCALE);
    }

    private static double seedUnit(double x, double y, int sequence, double salt) {
        double value = Math.sin(x * 12.9898 + y * 78.233 + sequence * 37.719 + salt * 43758.5453) * 43758.5453;
        return value - Math.floor(value);
    }

    private static void drawDestroyedHullBreaches(Graphics2D g,
                                                  Ship ship,
                                                  Shape hullShape,
                                                  List<Ship.HullImpactMark> marks,
                                                  int span) {
        if (g == null || ship == null || hullShape == null) return;

        EnumMap<ShipRoomLayout.RoomId, Area> shellAreas = destroyedHullShellAreas(ship, hullShape);
        if (shellAreas.isEmpty()) return;

        Stroke oldStroke = g.getStroke();
        for (Map.Entry<ShipRoomLayout.RoomId, Area> entry : shellAreas.entrySet()) {
            ShipRoomLayout.RoomId roomId = entry.getKey();
            Area shellArea = entry.getValue();
            if (shellArea == null || shellArea.isEmpty()) continue;
            ShipRoomLayout.RoomId facingRoom = breachFacingRoomId(ship, roomId, shellArea.getBounds());

            Area breachArea = buildDestroyedRoomBreachArea(ship, roomId, shellArea, marks, span);
            if (breachArea == null || breachArea.isEmpty()) continue;

            g.setColor(new Color(5, 6, 9, 232));
            g.fill(breachArea);

            float rim = Math.max(1.1f, (float) (1.0 + span * 0.0035));
            g.setStroke(new BasicStroke(rim, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(198, 208, 220, 82));
            g.draw(breachArea);

            drawHullBreachInterior(g, breachArea, roomId, facingRoom, span, ship);
        }
        g.setStroke(oldStroke);
    }

    private static EnumMap<ShipRoomLayout.RoomId, Area> destroyedHullShellAreas(Ship ship, Shape hullShape) {
        EnumMap<ShipRoomLayout.RoomId, Area> out = new EnumMap<>(ShipRoomLayout.RoomId.class);
        if (ship == null || hullShape == null) return out;

        Area hullArea = new Area(hullShape);
        Shape hullEdgeShape = new BasicStroke((float) Math.max(6.0, ship.radius * 0.22),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(hullShape);
        Area hullEdgeBand = new Area(hullEdgeShape);
        for (ShipRoomLayout.VisualCell cell : ShipRoomLayout.visualCellsFor(ship.role, ship.faction)) {
            if (cell == null || cell.roomId == null) continue;
            if (ship.roomHealthFraction(cell.roomId) > 1e-3) continue;

            Polygon poly = roomPolygonShipLocal(ship, cell.xs, cell.ys);
            if (poly == null || poly.npoints < 3) continue;

            Area cellArea = new Area(poly);
            cellArea.intersect(new Area(hullArea));
            if (cellArea.isEmpty()) continue;
            boolean hullFacing = isHullFacingCell(cellArea, hullEdgeBand);
            if (!ShipRoomLayout.isArmorRoom(cell.roomId) && !hullFacing) {
                cellArea = new Area(poly);
                cellArea.intersect(new Area(hullArea));
                if (cellArea.isEmpty()) continue;
            }

            out.computeIfAbsent(cell.roomId, key -> new Area()).add(cellArea);
        }
        return out;
    }

    private static boolean isHullFacingCell(Area cellArea, Area hullEdgeBand) {
        if (cellArea == null || hullEdgeBand == null || cellArea.isEmpty() || hullEdgeBand.isEmpty()) return false;
        Area overlap = new Area(cellArea);
        overlap.intersect(new Area(hullEdgeBand));
        return !overlap.isEmpty() && overlap.getBounds().width > 0 && overlap.getBounds().height > 0;
    }

    private static ShipRoomLayout.RoomId breachFacingRoomId(Ship ship, ShipRoomLayout.RoomId roomId, Rectangle bounds) {
        if (ShipRoomLayout.isArmorRoom(roomId)) return roomId;
        Faction faction = (ship == null) ? null : ship.faction;
        if (bounds == null) {
            ShipRoomLayout.RoomId fallback = ShipRoomLayout.RoomId.DORSAL_ARMOR;
            return (faction == Faction.TEAM_C) ? ShipRoomLayout.shieldStripRoomFor(fallback) : fallback;
        }

        HullRoomProjection projection = hullRoomProjection(ship);
        double nx = bounds.getCenterX() / Math.max(1.0, projection.localExtentX);
        double ny = bounds.getCenterY() / Math.max(1.0, projection.localExtentY);
        ShipRoomLayout.RoomId facing;
        if (Math.abs(nx) > Math.abs(ny) * 1.15) {
            facing = (nx >= 0.0) ? ShipRoomLayout.RoomId.BOW_ARMOR : ShipRoomLayout.RoomId.AFT_ARMOR;
        } else {
            facing = (ny <= 0.0) ? ShipRoomLayout.RoomId.DORSAL_ARMOR : ShipRoomLayout.RoomId.VENTRAL_ARMOR;
        }
        return (faction == Faction.TEAM_C) ? ShipRoomLayout.shieldStripRoomFor(facing) : facing;
    }

    private static Area buildDestroyedRoomBreachArea(Ship ship,
                                                     ShipRoomLayout.RoomId roomId,
                                                     Area shellArea,
                                                     List<Ship.HullImpactMark> marks,
                                                     int span) {
        if (ship == null || roomId == null || shellArea == null || shellArea.isEmpty()) return null;

        Rectangle bounds = shellArea.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return null;

        Area breach = new Area();
        int placed = 0;
        if (marks != null && !marks.isEmpty()) {
            for (int i = Math.max(0, marks.size() - 8); i < marks.size(); i++) {
                Ship.HullImpactMark mark = marks.get(i);
                if (mark == null || mark.roomId != roomId) continue;

                double base = Math.max(5.0, Math.min(bounds.width, bounds.height) * 0.22);
                double radius = Math.max(base, mark.breachRadius * 2.8 + mark.severity * span * 0.050);
                Area shard = new Area(createBreachBlob(mark.localX, mark.localY, radius,
                        radius * (0.90 + mark.severity * 0.45),
                        breachSeed(ship, roomId, i)));
                shard.intersect(new Area(shellArea));
                if (!shard.isEmpty()) {
                    breach.add(shard);
                    placed++;
                }
            }
        }

        if (placed == 0) {
            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY();
            double radius = Math.max(7.0, Math.min(bounds.width, bounds.height) * 0.34);
            Area fallback = new Area(createBreachBlob(cx, cy, radius, radius * 0.92,
                    breachSeed(ship, roomId, 0)));
            fallback.intersect(new Area(shellArea));
            breach.add(fallback);
        }

        Rectangle breachBounds = breach.getBounds();
        double shellScale = Math.max(8.0, Math.min(bounds.width, bounds.height));
        if (breachBounds.width < shellScale * 0.22 && breachBounds.height < shellScale * 0.22) {
            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY();
            double radius = Math.max(5.0, Math.min(bounds.width, bounds.height) * 0.18);
            Area supplement = new Area(createBreachBlob(cx, cy, radius, radius * 0.78,
                    breachSeed(ship, roomId, 17)));
            supplement.intersect(new Area(shellArea));
            breach.add(supplement);
        }

        return breach;
    }

    private static double hullDamageDetailScale(Graphics2D g) {
        if (g == null) return 1.0;
        java.awt.geom.AffineTransform tx = g.getTransform();
        double sx = Math.hypot(tx.getScaleX(), tx.getShearX());
        double sy = Math.hypot(tx.getScaleY(), tx.getShearY());
        double scale = Math.max(Math.abs(sx), Math.abs(sy));
        if (!Double.isFinite(scale) || scale <= 1e-6) return 1.0;
        return scale;
    }

    private static Shape createBreachBlob(double cx, double cy, double rx, double ry, long seed) {
        Random rng = new Random(seed);
        int points = 10;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < points; i++) {
            double t = (Math.PI * 2.0 * i) / points;
            double jitter = 0.68 + rng.nextDouble() * 0.52;
            double px = cx + Math.cos(t) * rx * jitter;
            double py = cy + Math.sin(t) * ry * jitter;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.closePath();
        return path;
    }

    private static void drawHullBreachInterior(Graphics2D g,
                                               Shape breachShape,
                                               ShipRoomLayout.RoomId roomId,
                                               ShipRoomLayout.RoomId facingRoom,
                                               int span,
                                               Ship ship) {
        if (g == null || breachShape == null) return;
        Rectangle b = breachShape.getBounds();
        if (b.width <= 0 || b.height <= 0) return;

        Graphics2D gi = (Graphics2D) g.create();
        gi.setClip(breachShape);

        drawClassSpecificBreachBackdrop(gi, ship, b, facingRoom);
        drawExposedInteriorRooms(gi, ship, breachShape, roomId, facingRoom);

        GradientPaint depth = new GradientPaint(
                b.x, b.y,
                new Color(18, 20, 24, 120),
                b.x + b.width, b.y + b.height,
                new Color(2, 3, 5, 0)
        );
        gi.setPaint(depth);
        gi.fillRect(b.x, b.y, b.width, b.height);

        long seed = breachSeed(ship, roomId, 91);
        Random rng = new Random(seed);
        gi.setStroke(new BasicStroke(Math.max(1.0f, (float) (span * 0.0018)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gi.setColor(new Color(76, 82, 92, 92));

        int verticals = Math.max(2, Math.min(6, b.width / 8));
        for (int i = 0; i < verticals; i++) {
            int x = b.x + (int) Math.round((i + 1.0) * b.width / (verticals + 1.0)) + rng.nextInt(3) - 1;
            gi.drawLine(x, b.y, x, b.y + b.height);
        }

        int horizontals = Math.max(1, Math.min(4, b.height / 10));
        for (int i = 0; i < horizontals; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (horizontals + 1.0)) + rng.nextInt(3) - 1;
            gi.drawLine(b.x, y, b.x + b.width, y);
        }

        gi.dispose();
    }

    private static void drawClassSpecificBreachBackdrop(Graphics2D g,
                                                        Ship ship,
                                                        Rectangle breachBounds,
                                                        ShipRoomLayout.RoomId breachedRoom) {
        if (g == null || ship == null || breachBounds == null) return;
        if (breachBounds.width <= 0 || breachBounds.height <= 0) return;

        String profile = ShipRoomLayout.profileIdForRole(ship.role);
        if (ship.role == ShipRole.CARRIER || ship.role == ShipRole.DRONE_CARRIER
                || "carrier".equals(profile)) {
            drawCarrierBreachBackdrop(g, breachBounds, breachedRoom, ship);
            return;
        }
        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.SUPERSHIP
                || ship.role.isTitanOrMothership()) {
            drawHeavyCapitalBreachBackdrop(g, breachBounds, breachedRoom, ship);
            return;
        }
        if (ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER
                || ship.role == ShipRole.CRUISER || ship.role == ShipRole.BATTLECRUISER) {
            drawCruiserBreachBackdrop(g, breachBounds, breachedRoom, ship);
            return;
        }
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET || "station".equals(profile)) {
            drawStationBreachBackdrop(g, breachBounds, ship);
            return;
        }
        drawLightHullBreachBackdrop(g, breachBounds, breachedRoom, ship);
    }

    private static void drawCarrierBreachBackdrop(Graphics2D g,
                                                  Rectangle b,
                                                  ShipRoomLayout.RoomId breachedRoom,
                                                  Ship ship) {
        g.setColor(new Color(20, 24, 30, 160));
        g.fillRect(b.x, b.y, b.width, b.height);

        int laneCount = Math.max(2, Math.min(5, b.height / 9));
        for (int i = 0; i < laneCount; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (laneCount + 1.0));
            g.setColor(new Color(62, 72, 84, 110));
            g.drawLine(b.x, y, b.x + b.width, y);
        }

        int bayCount = Math.max(2, Math.min(6, b.width / 11));
        int bayW = Math.max(4, b.width / Math.max(3, bayCount + 1));
        int bayH = Math.max(4, b.height / Math.max(3, laneCount + 1));
        for (int i = 0; i < bayCount; i++) {
            int x = b.x + 2 + i * Math.max(5, bayW - 1);
            int y = b.y + ((i % 2 == 0) ? 2 : Math.max(2, b.height - bayH - 2));
            g.setColor(new Color(38, 46, 56, 132));
            g.fillRoundRect(x, y, bayW, bayH, 4, 4);
            g.setColor(new Color(108, 128, 146, 92));
            g.drawRoundRect(x, y, bayW, bayH, 4, 4);
        }

        if (breachedRoom == ShipRoomLayout.RoomId.BOW_ARMOR || breachedRoom == ShipRoomLayout.RoomId.AFT_ARMOR) {
            int catwalkY = b.y + b.height / 2;
            g.setColor(new Color(146, 164, 180, 86));
            g.drawLine(b.x, catwalkY, b.x + b.width, catwalkY);
        }
    }

    private static void drawCruiserBreachBackdrop(Graphics2D g,
                                                  Rectangle b,
                                                  ShipRoomLayout.RoomId breachedRoom,
                                                  Ship ship) {
        g.setColor(new Color(18, 22, 28, 152));
        g.fillRect(b.x, b.y, b.width, b.height);

        int ribs = Math.max(3, Math.min(8, b.width / 9));
        for (int i = 0; i < ribs; i++) {
            int x = b.x + (int) Math.round((i + 1.0) * b.width / (ribs + 1.0));
            g.setColor(new Color(74, 86, 98, 118));
            g.drawLine(x, b.y, x, b.y + b.height);
        }

        int decks = Math.max(1, Math.min(4, b.height / 12));
        for (int i = 0; i < decks; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (decks + 1.0));
            g.setColor(new Color(52, 60, 70, 92));
            g.drawLine(b.x, y, b.x + b.width, y);
        }

        if (breachedRoom == ShipRoomLayout.RoomId.DORSAL_ARMOR || breachedRoom == ShipRoomLayout.RoomId.VENTRAL_ARMOR) {
            int conduitX = b.x + b.width / 2;
            g.setColor(new Color(128, 142, 158, 84));
            g.drawLine(conduitX, b.y, conduitX, b.y + b.height);
        }
    }

    private static void drawHeavyCapitalBreachBackdrop(Graphics2D g,
                                                       Rectangle b,
                                                       ShipRoomLayout.RoomId breachedRoom,
                                                       Ship ship) {
        g.setColor(new Color(16, 18, 24, 168));
        g.fillRect(b.x, b.y, b.width, b.height);

        int bulkheads = Math.max(2, Math.min(5, b.width / 15));
        int bandW = Math.max(3, b.width / Math.max(3, bulkheads * 2));
        for (int i = 0; i < bulkheads; i++) {
            int x = b.x + 2 + i * Math.max(6, bandW + 3);
            g.setColor(new Color(44, 50, 58, 150));
            g.fillRect(x, b.y, Math.max(2, bandW / 2), b.height);
            g.setColor(new Color(118, 128, 138, 96));
            g.drawLine(x + Math.max(1, bandW / 4), b.y, x + Math.max(1, bandW / 4), b.y + b.height);
        }

        int armorBelts = Math.max(2, Math.min(4, b.height / 10));
        for (int i = 0; i < armorBelts; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (armorBelts + 1.0));
            g.setColor(new Color(70, 78, 88, 108));
            g.fillRect(b.x, y - 1, b.width, 2);
        }

        int spineX = (breachedRoom == ShipRoomLayout.RoomId.AFT_ARMOR) ? b.x + b.width / 3 : b.x + (b.width * 2) / 3;
        g.setColor(new Color(154, 166, 176, 74));
        g.drawLine(spineX, b.y, spineX, b.y + b.height);
    }

    private static void drawStationBreachBackdrop(Graphics2D g, Rectangle b, Ship ship) {
        g.setColor(new Color(18, 22, 30, 158));
        g.fillRect(b.x, b.y, b.width, b.height);

        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        int spokes = Math.max(4, Math.min(8, Math.max(b.width, b.height) / 8));
        g.setColor(new Color(88, 102, 120, 112));
        for (int i = 0; i < spokes; i++) {
            double a = (Math.PI * 2.0 * i) / spokes;
            int x2 = cx + (int) Math.round(Math.cos(a) * b.width * 0.55);
            int y2 = cy + (int) Math.round(Math.sin(a) * b.height * 0.55);
            g.drawLine(cx, cy, x2, y2);
        }
        g.setColor(new Color(62, 72, 86, 104));
        g.drawOval(b.x + b.width / 5, b.y + b.height / 5, Math.max(6, b.width * 3 / 5), Math.max(6, b.height * 3 / 5));
    }

    private static void drawLightHullBreachBackdrop(Graphics2D g,
                                                    Rectangle b,
                                                    ShipRoomLayout.RoomId breachedRoom,
                                                    Ship ship) {
        g.setColor(new Color(18, 22, 28, 144));
        g.fillRect(b.x, b.y, b.width, b.height);

        int frames = Math.max(2, Math.min(4, Math.max(b.width, b.height) / 10));
        for (int i = 0; i < frames; i++) {
            int inset = 1 + i * 3;
            int w = Math.max(4, b.width - inset * 2);
            int h = Math.max(4, b.height - inset * 2);
            if (w <= 4 || h <= 4) break;
            g.setColor(new Color(80, 94, 108, Math.max(38, 104 - i * 20)));
            g.drawRoundRect(b.x + inset, b.y + inset, w, h, 4, 4);
        }
    }

    private static void drawExposedInteriorRooms(Graphics2D g,
                                                 Ship ship,
                                                 Shape breachShape,
                                                 ShipRoomLayout.RoomId breachedRoom,
                                                 ShipRoomLayout.RoomId facingRoom) {
        if (g == null || ship == null || breachShape == null || breachedRoom == null || facingRoom == null) return;

        LinkedHashSet<ShipRoomLayout.RoomId> exposedRooms = exposedInteriorRoomIds(ship.role, ship.faction, breachedRoom);
        if (exposedRooms.isEmpty()) return;

        Rectangle breachBounds = breachShape.getBounds();
        double offsetStrength = breachOffsetStrength(ship, breachBounds);
        double offset = Math.max(0.0, Math.min(breachBounds.width, breachBounds.height) * 0.24 * offsetStrength);
        int shiftX = 0;
        int shiftY = 0;
        switch (facingRoom) {
            case DORSAL_ARMOR -> shiftY = (int) Math.round(-offset);
            case VENTRAL_ARMOR -> shiftY = (int) Math.round(offset);
            case BOW_ARMOR -> shiftX = (int) Math.round(offset);
            case AFT_ARMOR -> shiftX = (int) Math.round(-offset);
            default -> {
                return;
            }
        }

        g.translate(shiftX, shiftY);
        for (ShipRoomLayout.VisualCell cell : ShipRoomLayout.visualCellsFor(ship.role, ship.faction)) {
            if (cell == null || cell.roomId == null) continue;
            if (!exposedRooms.contains(cell.roomId) || ShipRoomLayout.isArmorRoom(cell.roomId)) continue;

            Polygon poly = roomPolygonShipLocal(ship, cell.xs, cell.ys);
            if (poly == null || poly.npoints < 3) continue;

            double frac = ship.roomHealthFraction(cell.roomId);
            Color tint = roomTraceTint(cell.roomId, 150);
            Color fill = new Color(
                    MathUtil.clamp((tint.getRed() + 16) / 2, 0, 255),
                    MathUtil.clamp((tint.getGreen() + 18) / 2, 0, 255),
                    MathUtil.clamp((tint.getBlue() + 22) / 2, 0, 255),
                    MathUtil.clamp((int) Math.round(96 + (1.0 - frac) * 54), 0, 170)
            );
            g.setColor(fill);
            g.fillPolygon(poly);
            g.setColor(roomTraceTint(cell.roomId, 112));
            g.drawPolygon(poly);
            drawSpecialRoomInteriorGraphic(g, poly, cell.roomId, frac);

            if (cell.labelAnchor) {
                Rectangle pb = poly.getBounds();
                if (pb.width >= 16 && pb.height >= 11) {
                    String symbol = xrayRoomSymbol(cell.roomId);
                    Font font = (pb.width >= 24 && pb.height >= 14) ? XRAY_REPAIR_FONT : new Font("Consolas", Font.BOLD, 7);
                    g.setFont(font);
                    FontMetrics fm = g.getFontMetrics();
                    int tx = (int) Math.round(pb.getCenterX()) - fm.stringWidth(symbol) / 2;
                    int ty = (int) Math.round(pb.getCenterY()) + Math.max(4, fm.getAscent() / 2 - 1);
                    g.setColor(new Color(238, 244, 248, 210));
                    g.drawString(symbol, tx, ty);
                }
            }
        }
        g.translate(-shiftX, -shiftY);
    }

    private static double breachOffsetStrength(Ship ship, Rectangle breachBounds) {
        if (ship == null || breachBounds == null) return 1.0;
        HullRoomProjection projection = hullRoomProjection(ship);
        double nx = Math.abs(breachBounds.getCenterX()) / Math.max(1.0, projection.localExtentX);
        double ny = Math.abs(breachBounds.getCenterY()) / Math.max(1.0, projection.localExtentY);
        double edge = Math.max(nx, ny);
        return MathUtil.clamp((edge - 0.38) / 0.40, 0.0, 1.0);
    }

    private static void drawSpecialRoomInteriorGraphic(Graphics2D g,
                                                       Polygon poly,
                                                       ShipRoomLayout.RoomId roomId,
                                                       double hpFrac) {
        if (g == null || poly == null || roomId == null) return;
        Rectangle b = poly.getBounds();
        if (b.width < 10 || b.height < 8) return;

        Graphics2D gi = (Graphics2D) g.create();
        gi.clip(poly);

        Color accent = roomTraceTint(roomId, MathUtil.clamp((int) Math.round(98 + (1.0 - hpFrac) * 42), 0, 150));
        Color fill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;

        if (roomId == ShipRoomLayout.RoomId.REACTOR) {
            int r = Math.max(3, Math.min(b.width, b.height) / 4);
            gi.setColor(fill);
            gi.fillOval(cx - r, cy - r, r * 2, r * 2);
            gi.setColor(accent);
            gi.drawOval(cx - r - 2, cy - r - 2, (r + 2) * 2, (r + 2) * 2);
            gi.drawLine(cx, b.y + 2, cx, b.y + b.height - 2);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
        } else if (roomId == ShipRoomLayout.RoomId.BRIDGE || roomId == ShipRoomLayout.RoomId.BOW) {
            gi.setColor(accent);
            gi.drawArc(b.x + 2, b.y + 2, Math.max(6, b.width - 4), Math.max(6, b.height - 4), 200, 140);
            gi.drawLine(b.x + 3, b.y + b.height - 4, b.x + b.width - 3, b.y + b.height - 4);
            gi.drawLine(cx, b.y + b.height / 2, cx, b.y + b.height - 4);
        } else if (ShipRoomLayout.isMagazineRoom(roomId)) {
            int cols = Math.max(2, Math.min(4, b.width / 10));
            int shellW = Math.max(3, b.width / Math.max(3, cols + 1));
            int shellH = Math.max(4, b.height / 3);
            gi.setColor(fill);
            for (int i = 0; i < cols; i++) {
                int x = b.x + 2 + i * (shellW + 2);
                int y = cy - shellH / 2;
                gi.fillRoundRect(x, y, shellW, shellH, 3, 3);
            }
            gi.setColor(accent);
            gi.drawLine(b.x + 2, b.y + b.height - 3, b.x + b.width - 2, b.y + b.height - 3);
        } else if (ShipRoomLayout.isShieldStripRoom(roomId)) {
            int r = Math.max(4, Math.min(b.width, b.height) / 3);
            gi.setColor(accent);
            gi.drawOval(cx - r, cy - r, r * 2, r * 2);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
            gi.drawLine(cx, b.y + 2, cx, b.y + b.height - 2);
        } else if (ShipRoomLayout.isShieldRoom(roomId)) {
            int r = Math.max(4, Math.min(b.width, b.height) / 3);
            gi.setColor(accent);
            gi.drawOval(cx - r, cy - r, r * 2, r * 2);
            gi.drawOval(cx - r / 2, cy - r / 2, r, r);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
        } else if (ShipRoomLayout.isWarpRoom(roomId)) {
            int r = Math.max(4, Math.min(b.width, b.height) / 3);
            gi.setColor(accent);
            gi.drawOval(cx - r, cy - r / 2, r * 2, r);
            gi.drawOval(cx - r + 3, cy - r / 2 + 2, Math.max(4, r * 2 - 6), Math.max(4, r - 4));
            gi.drawLine(b.x + 3, cy, b.x + b.width - 3, cy);
        } else if (ShipRoomLayout.isPowerRoom(roomId)) {
            gi.setColor(accent);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
            gi.drawLine(cx, b.y + 2, cx, b.y + b.height - 2);
            gi.fillOval(cx - 2, cy - 2, 4, 4);
        } else if (ShipRoomLayout.isEngineRoom(roomId) || roomId == ShipRoomLayout.RoomId.AFT_SPINE) {
            gi.setColor(accent);
            int lanes = Math.max(2, Math.min(4, b.height / 5));
            for (int i = 0; i < lanes; i++) {
                int y = b.y + 2 + i * Math.max(3, (b.height - 4) / Math.max(1, lanes));
                gi.drawLine(b.x + 2, y, b.x + b.width - 2, y);
            }
            gi.drawLine(b.x + b.width - 4, b.y + 2, b.x + b.width - 4, b.y + b.height - 2);
        } else if (roomId == ShipRoomLayout.RoomId.SENSORS) {
            gi.setColor(accent);
            gi.drawArc(b.x + 2, b.y + 2, Math.max(6, b.width - 4), Math.max(6, b.height - 4), 220, 100);
            gi.drawArc(b.x + 4, b.y + 4, Math.max(4, b.width - 8), Math.max(4, b.height - 8), 220, 100);
            gi.drawLine(cx, cy, b.x + b.width - 3, b.y + 3);
        } else if (ShipRoomLayout.isWeaponRoom(roomId)) {
            gi.setColor(accent);
            gi.drawLine(b.x + 2, cy - 2, b.x + b.width - 4, cy - 2);
            gi.drawLine(b.x + 2, cy + 2, b.x + b.width - 4, cy + 2);
            gi.drawLine(b.x + b.width - 5, cy - 4, b.x + b.width - 2, cy);
            gi.drawLine(b.x + b.width - 5, cy + 4, b.x + b.width - 2, cy);
        } else if (roomId == ShipRoomLayout.RoomId.CARGO_BAY) {
            gi.setColor(fill);
            int crate = Math.max(4, Math.min(b.width, b.height) / 4);
            gi.fillRect(b.x + 2, b.y + 2, crate, crate);
            gi.fillRect(cx - crate / 2, cy - crate / 2, crate, crate);
            gi.fillRect(b.x + b.width - crate - 2, b.y + b.height - crate - 2, crate, crate);
            gi.setColor(accent);
            gi.drawLine(b.x + 2, b.y + 2, b.x + b.width - 2, b.y + b.height - 2);
        } else if (roomId == ShipRoomLayout.RoomId.CREW_QUARTERS || roomId == ShipRoomLayout.RoomId.SERVICE_BAY) {
            gi.setColor(accent);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
            gi.drawLine(b.x + 2, b.y + 3, b.x + b.width - 2, b.y + 3);
            gi.drawLine(b.x + 2, b.y + b.height - 3, b.x + b.width - 2, b.y + b.height - 3);
        }

        gi.dispose();
    }

    private static LinkedHashSet<ShipRoomLayout.RoomId> exposedInteriorRoomIds(ShipRole role,
                                                                                Faction faction,
                                                                                ShipRoomLayout.RoomId breachedRoom) {
        LinkedHashSet<ShipRoomLayout.RoomId> out = new LinkedHashSet<>();
        HashSet<ShipRoomLayout.RoomId> visited = new HashSet<>();
        ShipRoomLayout.RoomDef root = ShipRoomLayout.roomForId(role, faction, breachedRoom);
        if (root == null) return out;

        if (!ShipRoomLayout.isArmorRoom(breachedRoom)) {
            out.add(breachedRoom);
        }
        for (ShipRoomLayout.RoomId neighbor : root.neighbors) {
            collectExposedInteriorRoomIds(role, faction, neighbor, 0, 2, visited, out);
        }
        return out;
    }

    private static void collectExposedInteriorRoomIds(ShipRole role,
                                                      Faction faction,
                                                      ShipRoomLayout.RoomId roomId,
                                                      int depth,
                                                      int maxDepth,
                                                      Set<ShipRoomLayout.RoomId> visited,
                                                      LinkedHashSet<ShipRoomLayout.RoomId> out) {
        if (roomId == null || depth > maxDepth || visited.contains(roomId)) return;
        visited.add(roomId);

        ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, faction, roomId);
        if (def == null) return;
        if (!ShipRoomLayout.isArmorRoom(roomId)) {
            out.add(roomId);
        }
        if (depth == maxDepth) return;
        for (ShipRoomLayout.RoomId next : def.neighbors) {
            if (next == null || ShipRoomLayout.isArmorRoom(next)) continue;
            collectExposedInteriorRoomIds(role, faction, next, depth + 1, maxDepth, visited, out);
        }
    }

    private static long breachSeed(Ship ship, ShipRoomLayout.RoomId roomId, int salt) {
        long base = (ship == null) ? 0L : (long) System.identityHashCode(ship);
        long room = (roomId == null) ? 0L : (roomId.ordinal() + 1L) * 0x9E3779B97F4A7C15L;
        return base * 1103515245L + room + salt * 2654435761L;
    }

    private static Color roomTraceTint(ShipRoomLayout.RoomId roomId, int alpha) {
        int a = MathUtil.clamp(alpha, 0, 255);
        if (roomId == null) return new Color(255, 178, 105, a);
        if (ShipRoomLayout.isShieldStripRoom(roomId)) return new Color(124, 214, 255, a);
        if (ShipRoomLayout.isArmorRoom(roomId)) return new Color(210, 224, 236, a);
        if (ShipRoomLayout.isPowerRoom(roomId)) return new Color(255, 198, 112, a);
        if (ShipRoomLayout.isWeaponRoom(roomId)) return new Color(255, 164, 94, a);
        if (ShipRoomLayout.isMagazineRoom(roomId)) return new Color(255, 96, 86, a);
        if (ShipRoomLayout.isShieldRoom(roomId)) return new Color(178, 166, 255, a);
        if (ShipRoomLayout.isEngineRoom(roomId) || ShipRoomLayout.isWarpRoom(roomId) || roomId == ShipRoomLayout.RoomId.AFT_SPINE) {
            return new Color((roomId == ShipRoomLayout.RoomId.WARP_DRIVE) ? 144 : 130,
                    (roomId == ShipRoomLayout.RoomId.WARP_DRIVE) ? 186 : 208,
                    255,
                    a);
        }
        if (roomId == ShipRoomLayout.RoomId.SENSORS) return new Color(132, 238, 226, a);
        if (roomId == ShipRoomLayout.RoomId.BRIDGE || roomId == ShipRoomLayout.RoomId.BOW) return new Color(255, 214, 138, a);
        return new Color(200, 214, 230, a);
    }

    private static Point randomPointInShape(Random rng, Rectangle bounds, Shape shape, int tries) {
        int px = (int) Math.round(bounds.getCenterX());
        int py = (int) Math.round(bounds.getCenterY());
        int maxTries = Math.max(6, tries);
        for (int t = 0; t < maxTries; t++) {
            px = bounds.x + rng.nextInt(Math.max(1, bounds.width));
            py = bounds.y + rng.nextInt(Math.max(1, bounds.height));
            if (shape.contains(px, py)) return new Point(px, py);
        }
        return new Point(px, py);
    }

    private static Polygon hullFighter(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(-r + 1, -r / 2);
        p.addPoint(-r, 0);
        p.addPoint(-r + 1, r / 2);
        return p;
    }

    private static Polygon hullFrigate(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 8, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 8, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullArtilleryShip(double radius) {
        return hullPatrol(radius);
    }

    private static Polygon hullMissileBoat(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(r - 8, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 8, r / 2);
        return p;
    }

    private static Polygon hullCarrier(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 8, 0);
        p.addPoint(r - 8, -r);
        p.addPoint(-r, -r);
        p.addPoint(-r + 14, 0);
        p.addPoint(-r, r);
        p.addPoint(r - 8, r);
        return p;
    }

    private static Polygon hullCIWS(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullBase(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        // diamond-ish station
        p.addPoint(0, -r);
        p.addPoint(r, 0);
        p.addPoint(0, r);
        p.addPoint(-r, 0);
        return p;
    }

    // ------------------------------
    // New hull silhouettes (art pass)
    // ------------------------------

    private static Polygon hullPatrol(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 7, 0);
        p.addPoint(r - 2, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(r - 2, r / 2);
        return p;
    }

    private static Polygon hullPicket(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 9, 0);
        p.addPoint(r - 4, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 4, r / 2);
        return p;
    }

    private static Polygon hullStealth(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        // sleek diamond/knife
        p.addPoint(r + 10, 0);
        p.addPoint(r - 4, -r / 3);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, 0);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 4, r / 3);
        return p;
    }

    private static Polygon hullLightCruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 10, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r + 4, -r / 2);
        p.addPoint(-r, -r / 5);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 5);
        p.addPoint(-r + 4, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullMediumCruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 12, 0);
        p.addPoint(r - 7, -r / 2);
        p.addPoint(r - 12, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 6);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 6);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 12, r / 2);
        p.addPoint(r - 7, r / 2);
        return p;
    }

    private static Polygon hullBattlecruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 14, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(r - 14, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 4);
        p.addPoint(-r + 12, 0);
        p.addPoint(-r, r / 4);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 14, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullBattleship(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 16, 0);
        p.addPoint(r - 8, -r / 2);
        p.addPoint(r - 18, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 14, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 18, r / 2);
        p.addPoint(r - 8, r / 2);
        return p;
    }

    private static Polygon hullDreadnought(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 18, 0);
        p.addPoint(r - 10, -r / 2);
        p.addPoint(r - 22, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 2 + r / 6);
        p.addPoint(-r + 16, 0);
        p.addPoint(-r, r / 2 - r / 6);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 22, r / 2);
        p.addPoint(r - 10, r / 2);
        return p;
    }

    /**
     * If the locked target is offscreen, draw a small arrow at the edge of the screen pointing toward it.
     * Coordinates are in screen space (camX/camY are the world-space camera origin).
     */
    static void drawOffscreenTargetIndicator(Graphics2D g2, Ship target, double camX, double camY, int viewW, int viewH, double zoom) {
        if (target == null || !target.alive) return;

        double z = Math.max(1e-6, zoom);
        // Target in screen coords
        double sx = (target.x - camX) * z;
        double sy = (target.y - camY) * z;

        if (sx >= 0 && sx <= viewW && sy >= 0 && sy <= viewH) return; // on screen

        double cx = viewW / 2.0;
        double cy = viewH / 2.0;

        double vx = sx - cx;
        double vy = sy - cy;
        double len = Math.hypot(vx, vy);
        if (len < 1e-6) return;

        vx /= len;
        vy /= len;

        double margin = 22.0;

        // Ray from screen center: find earliest intersection with inset rectangle.
        double t = Double.POSITIVE_INFINITY;
        if (vx >  1e-6) t = Math.min(t, (viewW - margin - cx) / vx);
        if (vx < -1e-6) t = Math.min(t, (margin - cx) / vx);
        if (vy >  1e-6) t = Math.min(t, (viewH - margin - cy) / vy);
        if (vy < -1e-6) t = Math.min(t, (margin - cy) / vy);

        if (!Double.isFinite(t)) return;

        double px = cx + vx * t;
        double py = cy + vy * t;

        double size = 13.0;
        double perpX = -vy;
        double perpY =  vx;

        int x0 = (int) Math.round(px);
        int y0 = (int) Math.round(py);

        int x1 = (int) Math.round(px - vx * size + perpX * size * 0.55);
        int y1 = (int) Math.round(py - vy * size + perpY * size * 0.55);

        int x2 = (int) Math.round(px - vx * size - perpX * size * 0.55);
        int y2 = (int) Math.round(py - vy * size - perpY * size * 0.55);

        int[] xs = {x0, x1, x2};
        int[] ys = {y0, y1, y2};

        Color fill = factionHudColor(target.faction, 220);

        g2.setColor(fill);
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawPolygon(xs, ys, 3);
    }

    private static Color factionHullColor(Faction f) {
        if (f == Faction.ENEMY) return new Color(220, 80, 80);
        if (f == Faction.PLAYER) return new Color(70, 220, 120);
        if (f == Faction.TEAM_C) return new Color(86, 196, 102);
        if (f == Faction.TEAM_D) return new Color(230, 166, 88);
        return new Color(120, 160, 245);
    }

    private static Color factionTrimColor(Faction f) {
        if (f == Faction.ENEMY) return new Color(255, 170, 170);
        if (f == Faction.PLAYER) return new Color(200, 255, 220);
        if (f == Faction.TEAM_C) return new Color(188, 255, 186);
        if (f == Faction.TEAM_D) return new Color(255, 218, 160);
        return new Color(220, 230, 255);
    }

    private static Color factionHudColor(Faction f, int alpha) {
        Color base;
        if (f == Faction.ENEMY) base = new Color(255, 170, 170);
        else if (f == Faction.PLAYER) base = new Color(180, 255, 220);
        else if (f == Faction.TEAM_C) base = new Color(188, 255, 186);
        else if (f == Faction.TEAM_D) base = new Color(255, 218, 160);
        else base = new Color(170, 220, 255);
        return withAlpha(base, alpha);
    }

    private static Color factionMapColor(Faction f, boolean isPlayer, int alpha) {
        Color base;
        if (isPlayer || f == Faction.PLAYER) base = new Color(90, 255, 140);
        else if (f == Faction.ENEMY) base = new Color(255, 90, 90);
        else if (f == Faction.TEAM_C) base = new Color(114, 230, 116);
        else if (f == Faction.TEAM_D) base = new Color(255, 188, 108);
        else base = new Color(140, 180, 255);
        return withAlpha(base, alpha);
    }

    private static Color withAlpha(Color c, int alpha) {
        if (c == null) c = Color.WHITE;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathUtil.clamp(alpha, 0, 255));
    }

    private static boolean isProjectileVisible(Projectile projectile, double minX, double minY, double maxX, double maxY) {
        return isProjectileVisible(projectile, null, null, minX, minY, maxX, maxY);
    }

    private static boolean isProjectileVisible(Projectile projectile, FogOfWarSystem.State fog, Faction perspective,
                                               double minX, double minY, double maxX, double maxY) {
        if (projectile == null) return false;
        boolean friendly = perspective == null
                || projectile.faction == null
                || projectile.faction.isFriendlyTo(perspective);
        if (projectile instanceof PhaserBeam beam) {
            if (!isWorldSegmentVisible(beam.startX(), beam.startY(), beam.endX(), beam.endY(),
                    Math.max(beam.width, 16.0), minX, minY, maxX, maxY)) return false;
            return friendly || fog == null || perspective == null
                    || isFogSegmentVisible(fog, beam.startX(), beam.startY(), beam.endX(), beam.endY());
        }
        if (projectile instanceof PointDefenseLaser laser) {
            if (!isWorldSegmentVisible(laser.startX(), laser.startY(), laser.endX, laser.endY,
                    Math.max(laser.width, 10.0), minX, minY, maxX, maxY)) return false;
            return friendly || fog == null || perspective == null
                    || isFogSegmentVisible(fog, laser.startX(), laser.startY(), laser.endX, laser.endY);
        }
        double radius = Math.max(8.0, projectile.radius + 12.0);
        if (projectile instanceof CIWSPellet) radius = Math.max(radius, 24.0);
        else if (projectile instanceof Missile) radius = Math.max(radius, 18.0);
        if (!isWorldCircleVisible(projectile.x, projectile.y, radius, minX, minY, maxX, maxY)) return false;
        return friendly || fog == null || perspective == null || fog.isVisibleAtWorld(projectile.x, projectile.y);
    }

    private static double shipDrawCullRadius(Ship ship) {
        if (ship == null) return 24.0;
        double scale = switch (ship.role) {
            case FIGHTER -> 0.16;
            case BOMBER -> 0.17;
            case DRONE -> 0.20;
            default -> HullGeometry.roleVisualScale(ship.role);
        };
        return Math.max(ship.radius + 28.0, ship.radius * Math.max(0.1, scale) + 28.0);
    }

    private static boolean isFogSegmentVisible(FogOfWarSystem.State fog, double x1, double y1, double x2, double y2) {
        if (fog == null) return true;
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0;
            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            if (fog.isVisibleAtWorld(x, y)) return true;
        }
        return false;
    }

    private static boolean isWorldCircleVisible(double x, double y, double radius,
                                                double minX, double minY, double maxX, double maxY) {
        double r = Math.max(0.0, radius);
        return x + r >= minX && x - r <= maxX && y + r >= minY && y - r <= maxY;
    }

    private static boolean isWorldSegmentVisible(double x1, double y1, double x2, double y2, double pad,
                                                 double minX, double minY, double maxX, double maxY) {
        double p = Math.max(0.0, pad);
        double segMinX = Math.min(x1, x2) - p;
        double segMaxX = Math.max(x1, x2) + p;
        double segMinY = Math.min(y1, y2) - p;
        double segMaxY = Math.max(y1, y2) + p;
        return segMaxX >= minX && segMinX <= maxX && segMaxY >= minY && segMinY <= maxY;
    }

}
