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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Comparator;
import javax.imageio.ImageIO;

public class Renderer {
    private static final double IMPACT_DECAL_SCALE = 0.25;
    private static final double HULL_DAMAGE_DETAIL_MIN_SCREEN_SPAN = 72.0;
    private static final double SHIELD_FX_MIN_SCREEN_SPAN = 56.0;
    private static final double SHIELD_FX_MIN_MARK_FRESHNESS = 0.06;

    private static final String[] CORE_MENU_LABELS = {"SHOP", "BASE", "MAP", "POWER", "CREW"};
    private static final String[] CORE_MENU_HOTKEYS = {"TAB", "B", "M", "O", "H"};
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
                    TitanArchetype.MOBILE_STATION.commandBonusSummary()),
            new ShopHullOffer(ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3, ShopHullCategory.TITAN,
                    TitanArchetype.HYPERWEAPON.roleLabel(),
                    TitanArchetype.HYPERWEAPON.commandBonusSummary()),
            new ShopHullOffer(ShipRole.MOTHERSHIP, 7200, 3, ShopHullCategory.TITAN,
                    "Fleet anchor and command citadel",
                    "Grand carrier, repair harbor, and apex fleet flagship.")
    };

    public static Rectangle getStrategicMapRect(int viewW, int viewH) {
        int pad = 52;
        int w = Math.min(860, viewW - pad * 2);
        int h = Math.min(560, viewH - pad * 2);
        int x = (viewW - w) / 2;
        int y = (viewH - h) / 2;
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

    public static HoverTooltip hoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.ui == null) return null;
        HoverTooltip tooltip = null;
        if (ctx.ui.shopOpen) tooltip = shopHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.baseMenuOpen) tooltip = baseUpgradeHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.powerManagementOpen) tooltip = powerManagementHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.flightDeckOpen) tooltip = flightDeckHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        else if (ctx.ui.crewStationsOpen) tooltip = crewStationsHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (tooltip != null) return tooltip;

        tooltip = objectiveCardHoverTooltipAt(ctx, mouseX, mouseY);
        if (tooltip != null) return tooltip;

        tooltip = coreMenuHoverTooltipAt(ctx, viewW, viewH, mouseX, mouseY);
        if (tooltip != null) return tooltip;
        if (ctx.ui.hasBlockingOverlay()) return null;
        if (ctx.ui.xrayHoveredRoom != null) return null;
        return shipHoverTooltipAt(ctx);
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

        g2.setColor(new Color(4, 8, 16, 228));
        g2.fillRoundRect(x, y, width, height, 12, 12);
        g2.setColor(new Color(190, 222, 255, 170));
        g2.drawRoundRect(x, y, width, height, 12, 12);

        int ty = y + pad + bodyFm.getAscent();
        if (ui.hoverTooltipTitle != null && !ui.hoverTooltipTitle.isBlank()) {
            g2.setFont(HOVER_TOOLTIP_TITLE_FONT);
            g2.setColor(new Color(246, 250, 255, 240));
            ty = y + pad + titleFm.getAscent();
            g2.drawString(ui.hoverTooltipTitle, x + pad, ty);
            ty += titleFm.getDescent() + 8;
            g2.setColor(new Color(155, 206, 255, 110));
            g2.drawLine(x + pad, ty - 2, x + width - pad, ty - 2);
            ty += bodyFm.getAscent();
        }

        g2.setFont(HOVER_TOOLTIP_BODY_FONT);
        g2.setColor(new Color(224, 235, 248, 228));
        for (String line : lines) {
            g2.drawString(line, x + pad, ty);
            ty += bodyFm.getHeight();
        }
    }

    private static HoverTooltip coreMenuHoverTooltipAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        int index = coreMenuButtonAt(viewW, viewH, mouseX, mouseY);
        if (index < 0) return null;
        String body = switch (index) {
            case 0 -> "Shop and loadout controls. Commission hulls, buy upgrades, and browse fleet bands. Hotkey: TAB.";
            case 1 -> "Base upgrade console. Spend credits and ore on fortification, shields, turret systems, mining, and hangar tier. Hotkey: B.";
            case 2 -> "Strategic map. Set waypoints and inspect the wider battlespace. Hotkey: M.";
            case 3 -> "Power routing. Rebalance propulsion, shields, tactical, sensors, engineering, and supercharge buses. Hotkey: O.";
            case 4 -> "Crew stations. Review Captain, Helm, Tactical, Engineering, and Science automation plus voice mix. Hotkey: H.";
            default -> "";
        };
        return body.isBlank() ? null : new HoverTooltip("core:" + index, CORE_MENU_LABELS[index], body);
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
        Player player = ctx.player;
        UiState ui = ctx.ui;
        ShopHullCategory category = (ui == null || ui.shopHullCategory == null) ? ShopHullCategory.ESCORT : ui.shopHullCategory;
        int page = (ui == null) ? 0 : clampShopHullPage(category, ui.shopHullPage);

        for (int i = 0; i < 7; i++) {
            Rectangle card = getShopUpgradeCardRect(panel, i);
            if (!card.contains(mouseX, mouseY)) continue;
            return switch (i + 1) {
                case 1 -> new HoverTooltip("shop:upgrade:1", "Energy Bolt Primary",
                        "Standard bolt primary. Balanced cadence, no credit cost, and the safe baseline if you want to reset the flagship weapon package.");
                case 2 -> new HoverTooltip("shop:upgrade:2", "Beam Bolt Primary",
                        "Heavy direct-energy bolt package. Slower cadence, stronger punch, and a clean way to push the flagship toward longer-range alpha damage.");
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
        int rowY = y + 110;
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

        g2.setColor(new Color(0, 0, 0, 158));
        g2.fillRoundRect(bar.x, bar.y, bar.width, bar.height, 14, 14);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(bar.x, bar.y, bar.width, bar.height, 14, 14);

        boolean[] open = {
                ctx.ui.shopOpen,
                ctx.ui.baseMenuOpen,
                ctx.ui.mapOpen,
                ctx.ui.powerManagementOpen,
                ctx.ui.crewStationsOpen
        };
        boolean baseAvailable = CampaignSystem.currentBaseUpgradeAnchor(ctx) != null;
        boolean controlsDisabled = ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER;

        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < CORE_MENU_LABELS.length; i++) {
            Rectangle br = getCoreMenuButtonRect(viewW, viewH, i);
            boolean disabled = controlsDisabled || (i == 1 && !baseAvailable);
            boolean active = open[i];

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
            if (br.width < 64) label = CORE_MENU_LABELS[i].substring(0, Math.min(2, CORE_MENU_LABELS[i].length()));
            else if (br.width < 96) label = CORE_MENU_LABELS[i];
            else label = CORE_MENU_LABELS[i] + " [" + CORE_MENU_HOTKEYS[i] + "]";
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
        Area shellBase = createShieldShell(hullArea, shellWidth);
        Area auraBase = createShieldShell(hullArea, auraWidth);
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
        if (hullArea == null || width <= 0.0f) return new Area();
        Area shell = new Area(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(hullArea));
        shell.subtract(new Area(hullArea));
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
        if (g == null || ship == null || hullArea == null || !ship.isWarpCharging()) return;
        Rectangle2D bounds = hullArea.getBounds2D();
        if (bounds.getWidth() <= 0.0 || bounds.getHeight() <= 0.0) return;

        double charge = ship.warpChargeProgress();
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 7.2 + ship.id * 0.29);
        Color base = mixColor(factionTrimColor(ship.faction), new Color(120, 220, 255), 0.38);
        float shellWidth = (float) Math.max(3.0, ship.radius * (0.12 + charge * 0.05));
        float auraWidth = shellWidth * 2.4f;
        Area shell = createShieldShell(hullArea, shellWidth);
        Area aura = createShieldShell(hullArea, auraWidth);
        Rectangle2D auraBounds = aura.getBounds2D();
        if (auraBounds.getWidth() <= 0.0 || auraBounds.getHeight() <= 0.0) return;

        Graphics2D gx = (Graphics2D) g.create();
        Paint oldPaint = gx.getPaint();
        Stroke oldStroke = gx.getStroke();
        try {
            double gradientRadius = Math.max(auraBounds.getWidth(), auraBounds.getHeight()) * 0.72;
            gx.setPaint(new RadialGradientPaint(
                    new Point2D.Double(0.0, 0.0),
                    (float) Math.max(12.0, gradientRadius),
                    new float[]{0.0f, 0.52f, 1.0f},
                    new Color[]{
                            withAlpha(base, 0),
                            withAlpha(mixColor(base, Color.WHITE, 0.24), (int) Math.round(26 + charge * 30 + pulse * 16)),
                            withAlpha(base, 0)
                    }));
            gx.fill(aura);

            gx.setStroke(new BasicStroke(Math.max(1.2f, shellWidth * 0.46f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gx.setColor(withAlpha(mixColor(base, Color.WHITE, 0.58), (int) Math.round(96 + charge * 62 + pulse * 26)));
            gx.draw(shell);

            gx.setStroke(new BasicStroke(Math.max(0.8f, shellWidth * 0.18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gx.setColor(withAlpha(Color.WHITE, (int) Math.round(72 + charge * 54 + pulse * 28)));
            gx.draw(shell);
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
        CampaignBackdropSpec spec = resolveCampaignBackdropSpec(ctx);
        BufferedImage campaignImage = EnvironmentSkinLibrary.campaignBackdrop(campaignBackdropImageKey(ctx));
        if (campaignImage == null) {
            campaignImage = EnvironmentSkinLibrary.campaignBackdrop(campaignBackdropBaseImageKey(ctx));
        }
        if (campaignImage != null) {
            drawCampaignBackgroundImage(g2, campaignImage, viewW, viewH);
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

    private static void drawCampaignBackgroundImage(Graphics2D g2, BufferedImage image, int viewW, int viewH) {
        if (g2 == null || image == null || viewW <= 0 || viewH <= 0) return;
        int iw = Math.max(1, image.getWidth());
        int ih = Math.max(1, image.getHeight());
        double scale = Math.max(viewW / (double) iw, viewH / (double) ih);
        int drawW = Math.max(1, (int) Math.round(iw * scale));
        int drawH = Math.max(1, (int) Math.round(ih * scale));
        int x = (viewW - drawW) / 2;
        int y = (viewH - drawH) / 2;
        g2.drawImage(image, x, y, drawW, drawH, null);
    }

    public static int drawShips(Graphics2D g2, List<Ship> ships) {
        return drawShips(g2, ships, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static int drawShips(Graphics2D g2, List<Ship> ships,
                                double minX, double minY, double maxX, double maxY) {
        if (ships == null) return 0;
        int drawn = 0;
        for (Ship s : ships) {
            if (s.alive && isWorldCircleVisible(s.x, s.y, shipDrawCullRadius(s), minX, minY, maxX, maxY)) {
                drawShip(g2, s);
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
            case 2 -> new CampaignBackdropSpec(
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
            case 3 -> new CampaignBackdropSpec(
                    "relay_halo_moon",
                    0.0,
                    new Color(8, 18, 32, 18),
                    BackdropFieldMode.SPACE_NEBULA,
                    new CelestialBackdropSpec(BackdropBodyKind.MOON, 1.08, 0.20, 240.0, 0.014,
                            new Color(82, 106, 126, 255), new Color(190, 216, 230, 255),
                            new Color(170, 220, 255, 124), new Color(94, 154, 192, 34),
                            true, false, false, false, 0.72, 0.52),
                    null);
            case 4 -> new CampaignBackdropSpec(
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
            case 5 -> new CampaignBackdropSpec(
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
            case 6 -> new CampaignBackdropSpec(
                    "trade_spine_industrial_orbit",
                    elapsed * 0.28,
                    new Color(18, 18, 26, 24),
                    BackdropFieldMode.INDUSTRIAL_YARDS,
                    new CelestialBackdropSpec(BackdropBodyKind.PLANET, 1.10, 0.90, 340.0, 0.019,
                            new Color(80, 92, 104, 255), new Color(198, 206, 220, 255),
                            new Color(186, 210, 244, 124), new Color(124, 148, 196, 46),
                            true, true, true, false, 0.88, 0.68),
                    null);
            case 7 -> {
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
            case 8 -> new CampaignBackdropSpec(
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
            case 9 -> new CampaignBackdropSpec(
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
            case 10 -> new CampaignBackdropSpec(
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
            case 11 -> {
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
            case 12 -> new CampaignBackdropSpec(
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
        return drawProjectiles(g2, projectiles, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static int drawProjectiles(Graphics2D g2, List<Projectile> projectiles,
                                      double minX, double minY, double maxX, double maxY) {
        if (projectiles == null) return 0;
        int drawn = 0;
        for (Projectile p : projectiles) {
            if (!p.alive) continue;
            if (!isProjectileVisible(p, minX, minY, maxX, maxY)) continue;

            if (p instanceof CIWSPellet pellet) {
                int r = (int) Math.round(Math.max(1.0, pellet.radius));
                int x = (int) Math.round(pellet.x);
                int y = (int) Math.round(pellet.y);
                Color core = mixColor(projectileCoreColor(pellet.faction), Color.WHITE, 0.42);
                Color trail = projectileTrailColor(pellet.faction);
                double speed = Math.hypot(pellet.vx, pellet.vy);
                double trailLen = Math.max(8.0, Math.min(22.0, 8.0 + speed * 0.16));
                double nx = Math.cos(pellet.angle);
                double ny = Math.sin(pellet.angle);

                BufferedImage skin = ProjectileSkinLibrary.getCiwsPelletSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, pellet.x, pellet.y, pellet.angle,
                            Math.max(7.0, r * 3.6), Math.max(3.0, r * 1.8), 0.95f);
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
                // Yamato 2199-style energy bolts (standard + BEAM_BOLT variant)
                int x = (int) Math.round(eb.x);
                int y = (int) Math.round(eb.y);

                double vx = eb.vx;
                double vy = eb.vy;
                double vlen = Math.hypot(vx, vy);
                double nx = (vlen > 1e-6) ? (vx / vlen) : Math.cos(eb.angle);
                double ny = (vlen > 1e-6) ? (vy / vlen) : Math.sin(eb.angle);
                Color base = eb.isBeamBolt()
                        ? mixColor(beamColorForFaction(eb.faction), new Color(135, 230, 255), 0.36)
                        : projectileCoreColor(eb.faction);
                Color glow = mixColor(base, Color.WHITE, eb.isBeamBolt() ? 0.42 : 0.30);

                int r = (int) Math.round(Math.max(2.0, eb.radius));
                if (eb.isBeamBolt()) r = (int) Math.round(Math.max(r, 4.0));

                BufferedImage skin = ProjectileSkinLibrary.getEnergyBoltSkin(eb.isBeamBolt());
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, eb.x, eb.y, Math.atan2(ny, nx),
                            Math.max(14.0, r * (eb.isBeamBolt() ? 4.4 : 3.6)),
                            Math.max(6.0, r * 1.8), 0.90f);
                }

                Stroke old = g2.getStroke();

                // soft outer glow line
                g2.setStroke(new BasicStroke(r * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(glow, eb.isBeamBolt() ? 90 : 74));
                int gx1 = (int) Math.round(eb.x - nx * (r * 2.6));
                int gy1 = (int) Math.round(eb.y - ny * (r * 2.6));
                int gx2 = (int) Math.round(eb.x + nx * (r * 1.4));
                int gy2 = (int) Math.round(eb.y + ny * (r * 1.4));
                g2.drawLine(gx1, gy1, gx2, gy2);

                // bright core line
                g2.setStroke(new BasicStroke(r * 0.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(mixColor(base, Color.WHITE, 0.72), eb.isBeamBolt() ? 238 : 224));
                g2.drawLine(gx1, gy1, gx2, gy2);

                // end-cap flare
                int fx = (int) Math.round(eb.x + nx * (r * 2.0));
                int fy = (int) Math.round(eb.y + ny * (r * 2.0));
                g2.setColor(withAlpha(glow, eb.isBeamBolt() ? 210 : 182));
                g2.fillOval(fx - r, fy - r, r * 2, r * 2);

                // subtle trailing segments (motion blur)
                g2.setStroke(new BasicStroke(r * 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(glow, 82));
                for (int i = 1; i <= 2; i++) {
                    double t = r * (3.0 + i * 2.0);
                    int tx1 = (int) Math.round(eb.x - nx * (r * 1.6 + t));
                    int ty1 = (int) Math.round(eb.y - ny * (r * 1.6 + t));
                    int tx2 = (int) Math.round(eb.x - nx * (r * 0.4 + t));
                    int ty2 = (int) Math.round(eb.y - ny * (r * 0.4 + t));
                    g2.drawLine(tx1, ty1, tx2, ty2);
                }

                g2.setStroke(old);
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
                            Math.max(7.0, r * 2.7), Math.max(3.0, r * 1.8), 0.9f);
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
        float width = (float) Math.max(2.2, beam.width * (hyperLance ? (1.02 + 0.24 * pulse) : (0.90 + 0.16 * pulse)));

        Stroke old = g2.getStroke();

        g2.setStroke(new BasicStroke(width * (hyperLance ? 2.75f : 2.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, (int) Math.round((hyperLance ? 86 : 56) + pulse * (hyperLance ? 44 : 28))));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(width * (hyperLance ? 1.35f : 1.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, hyperLance ? 228 : 210));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(Math.max(1.1f, width * (hyperLance ? 0.54f : 0.40f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(Color.WHITE, hyperLance ? 205 : 165));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int glowR = (int) Math.round(Math.max(hyperLance ? 8.0 : 5.0, beam.width * (hyperLance ? 1.8 : 1.3)));
        g2.setColor(withAlpha(base, 150));
        g2.fillOval((int) Math.round(sx) - glowR, (int) Math.round(sy) - glowR, glowR * 2, glowR * 2);
        g2.setColor(withAlpha(hot, 126));
        g2.fillOval((int) Math.round(ex) - glowR, (int) Math.round(ey) - glowR, glowR * 2, glowR * 2);
        if (hyperLance) {
            int terminalR = (int) Math.round(Math.max(16.0, beam.width * 2.2));
            g2.setColor(withAlpha(base, 96));
            g2.fillOval((int) Math.round(ex) - terminalR, (int) Math.round(ey) - terminalR, terminalR * 2, terminalR * 2);
            g2.setColor(withAlpha(Color.WHITE, 118));
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
        float width = (float) Math.max(1.1, laser.width);

        Stroke old = g2.getStroke();

        g2.setStroke(new BasicStroke(width * 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, 102));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(Math.max(1.0f, width * 0.85f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, 212));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int r = (int) Math.round(Math.max(2.0, laser.width * 1.5));
        g2.setColor(withAlpha(hot, 178));
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
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static void drawNpcSuperweaponAimCues(Graphics2D g2, List<Ship> ships, Ship player,
                                                 double minX, double minY, double maxX, double maxY) {
        if (g2 == null || ships == null || ships.isEmpty()) return;
        for (Ship ship : ships) {
            if (ship == null || ship == player) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (!ship.hasSuperweapon || !ship.isSuperweaponCharging()) continue;
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
        BufferedImage skin = ProjectileSkinLibrary.getMissileSkin();
        if (skin != null) {
            drawMissileSkin(g2, m, skin);
        } else {
            drawMissileFallback(g2, m);
        }

        double nx = Math.cos(m.angle);
        double ny = Math.sin(m.angle);
        double tailOffset = m.radius * 1.1;
        double trailLen = Math.max(12.0, m.radius * 4.8);

        int x1 = (int) Math.round(m.x - nx * tailOffset);
        int y1 = (int) Math.round(m.y - ny * tailOffset);
        int x2 = (int) Math.round(m.x - nx * (tailOffset + trailLen));
        int y2 = (int) Math.round(m.y - ny * (tailOffset + trailLen));

        Color trail = missileExhaustColor(m.faction);
        g2.setColor(new Color(trail.getRed(), trail.getGreen(), trail.getBlue(), 120));
        g2.drawLine(x1, y1, x2, y2);
    }

    private static void drawMissileSkin(Graphics2D g2, Missile m, BufferedImage skin) {
        double len = Math.max(16.0, m.radius * 3.8);
        double width = Math.max(6.0, m.radius * 1.8);
        int drawW = (int) Math.round(len);
        int drawH = (int) Math.round(width);

        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(m.x, m.y);
        gx.rotate(m.angle);
        gx.drawImage(skin, -drawW / 2, -drawH / 2, drawW, drawH, null);

        Color stripe = missileStripeColor(m.faction);
        int bandW = Math.max(2, (int) Math.round(drawW * 0.12));
        int bandH = Math.max(3, (int) Math.round(drawH * 0.64));
        int bandX = (int) Math.round(-drawW * 0.10);
        gx.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 170));
        gx.fillRoundRect(bandX, -bandH / 2, bandW, bandH, bandW, bandW);

        int flare = Math.max(2, (int) Math.round(drawH * 0.34));
        int flareX = (int) Math.round(drawW * 0.30);
        gx.setColor(new Color(255, 250, 220, 170));
        gx.fillOval(flareX, -flare / 2, flare, flare);
        gx.dispose();
    }

    private static void drawMissileFallback(Graphics2D g2, Missile m) {
        double len = Math.max(16.0, m.radius * 3.8);
        double width = Math.max(6.0, m.radius * 1.8);
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

        Color stripe = missileStripeColor(m.faction);
        int bandW = Math.max(2, (int) Math.round(len * 0.12));
        int bandH = Math.max(3, (int) Math.round(width * 0.64));
        int bandX = (int) Math.round(-len * 0.08);
        gx.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 170));
        gx.fillRoundRect(bandX, -bandH / 2, bandW, bandH, bandW, bandW);

        gx.dispose();
    }

    private static Color missileStripeColor(Faction faction) {
        if (faction == null) return new Color(110, 220, 255);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(110, 220, 255);
            case ENEMY -> new Color(255, 122, 94);
            case TEAM_C -> new Color(146, 255, 118);
            case TEAM_D -> new Color(255, 186, 92);
        };
    }

    private static Color missileExhaustColor(Faction faction) {
        if (faction == null) return new Color(255, 186, 120);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(130, 226, 255);
            case ENEMY -> new Color(255, 170, 112);
            case TEAM_C -> new Color(164, 255, 140);
            case TEAM_D -> new Color(255, 210, 128);
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
        int actionH = (detail == GameContext.HudDetail.FULL) ? computeActionStripCardHeight(player, detail, leftW) : 0;
        int shipH = computeShipSystemsCardHeight(player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, leftW, detail);

        int totalH = commandH + 10 + shipH + (actionH > 0 ? actionH + 10 : 0) + (objectiveH > 0 ? objectiveH + 10 : 0);
        int cardY = Math.max(16, coreMenu.y - 12 - totalH);
        if (objectiveH > 0) {
            if (ctx != null && ctx.ui != null) {
                ctx.ui.setObjectiveHover(
                        new Rectangle(leftX, cardY, leftW, objectiveH),
                        "OBJECTIVE",
                        buildObjectiveHoverBody(objectiveTitle, objectiveDetail));
            }
            cardY += drawObjectiveCard(g2, objectiveTitle, objectiveDetail, leftX, cardY, leftW, detail);
            cardY += 10;
        }
        cardY += drawCommandOverviewCard(g2, player, credits, hangarTier, dockedAtBase,
                resourceRush, allyOre, enemyOre, goal, orePriceMul, orePriceT, miningMul, miningT, gameOverText,
                leftX, cardY, leftW, detail, ctx);
        if (actionH > 0) {
            cardY += 10;
            cardY += drawActionStripCard(g2, player, detail, leftX, cardY, leftW);
        }
        cardY += 10;
        drawShipSystemsCard(g2, player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, leftX, cardY, leftW, detail);

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
        // Top-center event banner
        if (eventBanner != null && !eventBanner.isBlank() && eventBannerT > 0) {
            int bw = 720;
            int bh = 34;
            int bx = (g2.getClipBounds().width - bw) / 2;
            int by = 10;

            int a = (int) Math.round(60 + 140 * Math.max(0.0, Math.min(1.0, eventBannerT / 3.0)));
            g2.setColor(new Color(0, 0, 0, MathUtil.clamp(a, 0, 190)));
            g2.fillRoundRect(bx, by, bw, bh, 14, 14);
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
        drawBottomCombatVitals(g2, player, lockedTarget, xrayLayout, viewW, viewH);
        drawCursorWeaponHints(g2, ctx, player, camX, camY, zoom, viewW, viewH);



        if (shopOpen) {
            drawShopOverlay(g2, ctx, player, credits, hangarTier, ctx.ui);
        }
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
        int contentW = Math.max(220, w - 24);

        List<String> titleLines = buildObjectiveTitleLines(titleFm, objectiveTitle, contentW, detail);
        List<String> detailLines = buildObjectiveDetailLines(bodyFm, objectiveDetail, contentW, detail);
        if (titleLines.isEmpty() && detailLines.isEmpty()) return 0;

        int h = computeObjectiveCardHeight(objectiveTitle, objectiveDetail, w, detail);
        drawHudPanelFrame(g2, x, y, w, h, "OBJECTIVE", new Color(255, 214, 132, 220));

        int rowY = y + 38;
        g2.setFont(titleFont);
        g2.setColor(new Color(255, 232, 170, 232));
        for (String line : titleLines) {
            g2.drawString(line, x + 12, rowY);
            rowY += 16;
        }

        if (!titleLines.isEmpty() && !detailLines.isEmpty()) {
            g2.setColor(new Color(255, 255, 255, 44));
            g2.drawLine(x + 12, rowY + 1, x + w - 12, rowY + 1);
            rowY += 16;
        }

        g2.setFont(bodyFont);
        g2.setColor(new Color(220, 232, 244, 208));
        for (String line : detailLines) {
            g2.drawString(line, x + 12, rowY);
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
        int contentW = Math.max(220, w - 24);
        List<String> titleLines = buildObjectiveTitleLines(titleFm, objectiveTitle, contentW, detail);
        List<String> detailLines = buildObjectiveDetailLines(bodyFm, objectiveDetail, contentW, detail);
        if (titleLines.isEmpty() && detailLines.isEmpty()) return 0;
        int h = 34 + titleLines.size() * 16;
        if (!titleLines.isEmpty() && !detailLines.isEmpty()) h += 16;
        h += detailLines.size() * 15 + 12;
        return Math.max(66, h);
    }

    private static String buildObjectiveHoverBody(String objectiveTitle, String objectiveDetail) {
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

        int titleY = y + 34;
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(new Color(244, 248, 255, 235));
        String shipLabel = (player.role == null) ? "COMMAND SHIP" : player.role.name().replace('_', ' ');
        g2.drawString(shipLabel, x + 12, titleY);

        boolean infiniteCredits = ctx != null && ctx.config != null && ctx.config.mode == GameMode.SHOOTING_RANGE;
        String creditLabel = infiniteCredits ? "CREDITS INF" : ("CREDITS " + credits);
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        FontMetrics creditFm = g2.getFontMetrics();
        g2.setColor(new Color(150, 214, 255, 225));
        g2.drawString(creditLabel, x + w - 12 - creditFm.stringWidth(creditLabel), titleY);

        int rowY = y + 58;
        g2.setColor(new Color(255, 255, 255, 58));
        g2.drawLine(x + 12, rowY, x + w - 12, rowY);
        rowY += 18;

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        for (String line : statusLines) {
            g2.setColor(line.startsWith("Status:")
                    ? new Color(255, 196, 148, 226)
                    : new Color(190, 214, 236, 198));
            g2.drawString(line, x + 12, rowY);
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
        return 76 + statusLines.size() * 15;
    }

    private static List<String> buildObjectiveTitleLines(FontMetrics titleFm, String objectiveTitle, int contentW,
                                                         GameContext.HudDetail detail) {
        List<String> lines = wrapHudText(titleFm, objectiveTitle, contentW);
        int maxLines = switch ((detail == null) ? GameContext.HudDetail.COMPACT : detail) {
            case MINIMAL -> 1;
            case COMPACT -> 2;
            case FULL -> 3;
        };
        return limitHudLines(lines, maxLines);
    }

    private static List<String> buildObjectiveDetailLines(FontMetrics bodyFm, String objectiveDetail, int contentW,
                                                          GameContext.HudDetail detail) {
        List<String> lines = wrapHudText(bodyFm, objectiveDetail, contentW);
        int maxLines = switch ((detail == null) ? GameContext.HudDetail.COMPACT : detail) {
            case MINIMAL -> 1;
            case COMPACT -> 3;
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
        if (CampaignSystem.isCampaignActive(ctx)) {
            int escortCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT);
            int lineCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE);
            int capitalCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL);
            int titanHullCount = CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN);
            int standardCommand = CampaignSystem.campaignStandardCommandCapacity(ctx);
            int standardUsed = CampaignSystem.campaignStandardCommandUsed(ctx);
            int eliteCommand = CampaignSystem.campaignEliteCommandCapacity(ctx);
            int eliteUsed = CampaignSystem.campaignEliteCommandUsed(ctx);
            String fleetLine = "Fleet: E " + escortCount + "/" + CampaignSystem.persistentFleetCap(ShopHullCategory.ESCORT)
                    + "   L " + lineCount + "/" + CampaignSystem.persistentFleetCap(ShopHullCategory.LINE)
                    + "   C " + capitalCount + "/" + CampaignSystem.persistentFleetCap(ShopHullCategory.CAPITAL)
                    + "   T " + titanHullCount + "/" + CampaignSystem.persistentFleetCap(ShopHullCategory.TITAN);
            statusLines.add(fleetLine);
            String commandLine = "Command: Grid " + titanHullCount + "/" + TitanFleetSystem.mothershipTitanCap()
                    + "   Std " + standardUsed + "/" + standardCommand
                    + "   Elite " + eliteUsed + "/" + eliteCommand;
            statusLines.add(commandLine);
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

        int chipX = x + 12;
        int chipY = y + 34;
        int lineHeight = 28;
        int chipH = 18;
        int maxX = x + w - 12;
        int rows = 1;

        int panelH = 60;
        drawHudPanelFrame(g2, x, y, w, panelH, "ACTION STRIP", new Color(132, 196, 255, 210));
        for (String chip : chips) {
            int chipW = fm.stringWidth(chip) + 14;
            if (chipX + chipW > maxX) {
                chipX = x + 12;
                chipY += lineHeight;
                rows++;
            }
            drawHudStatusChip(g2, chip, chipX, chipY - 12, chipW, chipH, new Color(125, 190, 255, 210), false);
            chipX += chipW + 8;
        }
        if (rows > 1) {
            panelH = 60 + (rows - 1) * 28;
            drawHudPanelFrame(g2, x, y, w, panelH, "ACTION STRIP", new Color(132, 196, 255, 210));
            chipX = x + 12;
            chipY = y + 34;
            for (String chip : chips) {
                int chipW = fm.stringWidth(chip) + 14;
                if (chipX + chipW > maxX) {
                    chipX = x + 12;
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
                                           int x, int y, int w, GameContext.HudDetail detail) {
        if (g2 == null || player == null) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        FontMetrics bodyFm = g2.getFontMetrics(bodyFont);
        int contentW = Math.max(220, w - 24);

        List<String> noteLines = buildShipSystemNoteLines(player, lockedTarget, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, detail, bodyFm, contentW);
        HudChipSet chips = buildShipSystemChips(player, autoLock, detail);

        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        g2.setFont(chipFont);
        FontMetrics chipFm = g2.getFontMetrics();
        int chipRows = computeHudChipRows(chips.texts, chipFm, w);
        boolean showPowerStrip = detail != GameContext.HudDetail.MINIMAL;
        boolean showPowerLegend = detail == GameContext.HudDetail.FULL;
        int powerBlockH = showPowerStrip ? (showPowerLegend ? 62 : 18) : 0;
        int h = computeShipSystemsCardHeight(player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, w, detail);

        drawHudPanelFrame(g2, x, y, w, h, "SHIP", factionHudColor(player.faction, 210));

        int chipY = y + 34;
        int chipX = x + 12;
        int chipMaxX = x + w - 12;
        for (int i = 0; i < chips.texts.size(); i++) {
            int chipW = chipFm.stringWidth(chips.texts.get(i)) + 14;
            if (chipX + chipW > chipMaxX) {
                chipX = x + 12;
                chipY += 24;
            }
            drawHudStatusChip(g2, chips.texts.get(i), chipX, chipY - 12, chipW, 18,
                    chips.colors.get(i), chips.strong.get(i));
            chipX += chipW + 8;
        }

        int textY;
        if (showPowerStrip) {
            int barY = chipY + 30;
            int powerBlockUsed = drawPowerAllocationStrip(g2, player, x + 12, barY, w - 24, 16, showPowerLegend);
            textY = barY + powerBlockUsed + 10;
        } else {
            textY = chipY + 20;
        }

        g2.setFont(bodyFont);
        for (String line : noteLines) {
            if (line == null || line.isBlank()) continue;
            boolean emphasis = line.startsWith("Hint:") || line.startsWith("Counter:") || line.startsWith("OVERLAY:");
            g2.setColor(emphasis ? new Color(255, 226, 154, 224) : new Color(198, 218, 238, 195));
            g2.drawString(line, x + 12, textY);
            textY += 15;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return h;
    }

    private static int computeShipSystemsCardHeight(Player player, Ship lockedTarget, boolean autoLock,
                                                    int playerWingActive, int playerWingCap, String stationStatus,
                                                    String overlayStatus, String contextHint,
                                                    int w, GameContext.HudDetail detail) {
        if (player == null) return 0;
        Canvas metricsCanvas = new Canvas();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        FontMetrics bodyFm = metricsCanvas.getFontMetrics(bodyFont);
        FontMetrics chipFm = metricsCanvas.getFontMetrics(chipFont);
        int contentW = Math.max(220, w - 24);
        List<String> noteLines = buildShipSystemNoteLines(player, lockedTarget, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, detail, bodyFm, contentW);
        HudChipSet chips = buildShipSystemChips(player, autoLock, detail);
        int chipRows = computeHudChipRows(chips.texts, chipFm, w);
        boolean showPowerStrip = detail != GameContext.HudDetail.MINIMAL;
        int powerBlockH = showPowerStrip ? ((detail == GameContext.HudDetail.FULL) ? 62 : 18) : 0;
        return 52 + chipRows * 24 + powerBlockH + noteLines.size() * 15;
    }

    private static List<String> buildShipSystemNoteLines(Player player, Ship lockedTarget,
                                                         int playerWingActive, int playerWingCap,
                                                         String stationStatus, String overlayStatus, String contextHint,
                                                         GameContext.HudDetail detail, FontMetrics bodyFm, int contentW) {
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

        if (playerWingCap > 0 && mode != GameContext.HudDetail.MINIMAL) {
            noteLines.add("Wing " + playerWingActive + "/" + playerWingCap
                    + "   " + player.carrierCommandMode.name()
                    + "   auto " + (player.carrierAutoLaunch ? "ON" : "OFF"));
        }

        if (overlayStatus != null && !overlayStatus.isBlank()) {
            noteLines.addAll(wrapHudText(bodyFm, overlayStatus, contentW));
        }

        if (mode == GameContext.HudDetail.FULL) {
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
        out.add("LMB PRIMARY");
        out.add("RMB SECONDARY");
        out.add("L LOCK");
        out.add("TAB SHOP");
        if (detail != GameContext.HudDetail.MINIMAL) {
            out.add("M MAP");
            out.add("H CREW");
            out.add("O POWER");
            out.add("B BASE");
        }
        if (detail == GameContext.HudDetail.FULL) {
            out.add("F MINE");
            out.add("E OVERCHARGE");
            out.add("Y PRESET");
            out.add("; THRUST");
        }
        if (player.hasSuperweapon) out.add("X SUPERWEAPON");
        if (player.isCarrier) {
            out.add("/ FLIGHT");
            out.add("C LAUNCH");
        }
        return out;
    }

    private static void drawHudPanelFrame(Graphics2D g2, int x, int y, int w, int h, String title, Color accent) {
        if (g2 == null) return;
        Color base = (accent == null) ? new Color(150, 190, 235, 180) : accent;
        g2.setColor(new Color(7, 14, 24, 188));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(withAlpha(base, 110));
        g2.drawRoundRect(x, y, w - 1, h - 1, 18, 18);
        g2.setColor(new Color(255, 255, 255, 22));
        g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 16, 16);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(withAlpha(base, 220));
        g2.drawString(title, x + 12, y + 16);
        g2.setColor(withAlpha(base, 72));
        g2.drawLine(x + 12, y + 22, x + w - 12, y + 22);
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
            rows.add("QUICK: L lock target | TAB/B/M/O/H overlays | bottom bar access");
            rows.add("META: ESC pause/resume");
            return rows;
        }

        if (detail == GameContext.HudDetail.COMPACT) {
            rows.add("CURSOR COMBAT: LMB guns | RMB missiles" + (player.hasSuperweapon ? " | X superweapon" : ""));
            rows.add("TARGETING: L lock | [ ] cycle | T auto-lock");
            rows.add("SYSTEM: Y preset | U crew | watch the per-side GATE chip for the outer shield screen");
            rows.add("OVERLAYS: TAB shop | B base | M map | O power | H crew | bottom bar");
            rows.add("X-RAY: ` filter | ' clear focus | click room focus");
            rows.add("META: ESC pause/resume");
            return rows;
        }

        rows.add("CURSOR COMBAT: LMB guns | RMB missiles" + (player.hasSuperweapon ? " | X superweapon" : ""));
        rows.add("UTILITY: F mine | ; emergency thrust | E shield overcharge");
        rows.add("TARGETING: L lock under mouse | [ ] cycle targets | T auto-lock");
        rows.add("SYSTEMS: O power mgmt | H crew stations | Y power preset | U crew order");
        rows.add("SHIELDS: each side has its own gate; burn one arc down and the core shield behind that side starts taking damage");
        rows.add("X-RAY: ` cycle filter | ' clear focus | click room to focus | RMB clears focus");
        rows.add("OVERLAYS: TAB shop/loadout | B base upgrades | bottom bar quick access");
        rows.add("WARP: - or BACKSPACE charge 10s warp to waypoint or friendly base");
        if (player.isStealth) rows.add("STEALTH: cloak auto-engages while not firing or taking hits");
        if (player.isCarrier) rows.add("CARRIER: C launch wing | R recall | V attack/defend | Z auto-launch");
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

    private static void drawBottomCombatVitals(Graphics2D g2, Player player, Ship lockedTarget,
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
        }
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
            String state = ship.isCloaked() ? "ACTIVE" : "RECHARGE";
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
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static void drawWorldMarkers(Graphics2D g2, List<Ship> ships, Ship lockedTarget,
                                        java.util.Map<Faction, Ship> commandShips,
                                        java.util.Map<Faction, Ship> sharedTargets,
                                        double minX, double minY, double maxX, double maxY) {
        if (lockedTarget != null && lockedTarget.alive
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
                drawCommandShipBeacon(g2, cmd, e.getKey(), (sharedTargets == null) ? null : sharedTargets.get(e.getKey()));
            }
        }

        if (ships == null) return;
        for (Ship s : ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (!s.isWarpCharging()) continue;
            if (!Double.isFinite(s.warpExitX()) || !Double.isFinite(s.warpExitY())) continue;
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
        int baseR = (int) Math.round(Math.max(34.0, ship.radius * 1.4));
        int outerR = (int) Math.round(baseR + 18 + pulse * 18 + progress * 12);
        Color base = factionHudColor(ship.faction, 220);

        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 32 + (int) Math.round(progress * 42.0)));
        g2.fillOval(x - outerR, y - outerR, outerR * 2, outerR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 170));
        g2.drawOval(x - outerR, y - outerR, outerR * 2, outerR * 2);
        g2.setColor(new Color(235, 245, 255, 215));
        g2.drawOval(x - baseR, y - baseR, baseR * 2, baseR * 2);
        g2.drawLine(x - outerR - 8, y, x - baseR + 2, y);
        g2.drawLine(x + baseR - 2, y, x + outerR + 8, y);
        g2.drawLine(x, y - outerR - 8, x, y - baseR + 2);
        g2.drawLine(x, y + baseR - 2, x, y + outerR + 8);
        g2.setStroke(old);

        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(240, 247, 255, 220));
        g2.drawString("WARP IN", x - 19, y - outerR - 8);
    }

    private static void drawCommandShipBeacon(Graphics2D g2, Ship cmd, Faction faction, Ship sharedTarget) {
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

        if (sharedTarget != null && sharedTarget.alive && !sharedTarget.dying && sharedTarget.hp > 0) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{6f, 6f}, 0f));
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 90));
            g2.drawLine((int) Math.round(cmd.x), (int) Math.round(cmd.y), (int) Math.round(sharedTarget.x), (int) Math.round(sharedTarget.y));
            g2.setStroke(old);
        }
    }

    public static void drawCombatCallouts(Graphics2D g2, List<UiState.CombatCallout> callouts,
                                          double minX, double minY, double maxX, double maxY) {
        if (g2 == null || callouts == null || callouts.isEmpty()) return;
        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        for (UiState.CombatCallout callout : callouts) {
            if (callout == null || callout.text == null || callout.text.isBlank()) continue;
            if (!isWorldCircleVisible(callout.x, callout.y, 90.0, minX, minY, maxX, maxY)) continue;
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
        Graphics2D gx = (Graphics2D) g2.create();
        ShopHullCategory category = (ui == null || ui.shopHullCategory == null)
                ? ShopHullCategory.forRole(player.role)
                : ui.shopHullCategory;
        int page = (ui == null) ? 0 : clampShopHullPage(category, ui.shopHullPage);
        int pageCount = shopHullPageCount(category);
        boolean campaignShop = CampaignSystem.usesPersistentFleetShop(ctx);

        GradientPaint panelFill = new GradientPaint(
                panel.x, panel.y, new Color(7, 10, 16, 236),
                panel.x, panel.y + panel.height, new Color(14, 18, 28, 226));
        gx.setPaint(panelFill);
        gx.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 24, 24);
        gx.setColor(new Color(255, 255, 255, 78));
        gx.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 24, 24);
        gx.setColor(new Color(118, 180, 255, 42));
        gx.drawRoundRect(panel.x + 2, panel.y + 2, panel.width - 4, panel.height - 4, 22, 22);

        gx.setFont(new Font("Consolas", Font.BOLD, 18));
        gx.setColor(new Color(245, 248, 255, 230));
        gx.drawString(campaignShop ? "FLEET COMMISSIONING" : "SHOP / LOADOUT", panel.x + 22, panel.y + 28);
        gx.setFont(new Font("Consolas", Font.PLAIN, 12));
        gx.setColor(new Color(192, 210, 232, 180));
        gx.drawString(campaignShop
                        ? "Upgrade the Mothership on the left and commission persistent blue hulls on the right. TAB/ESC closes."
                        : "Buy capped upgrades on the left and browse hull classes on the right. TAB/ESC closes.",
                panel.x + 22, panel.y + 48);

        drawShopMetricPill(gx, panel.x + 22, panel.y + 64, 170, "CREDITS", "$" + credits, new Color(120, 214, 170));
        drawShopMetricPill(gx, panel.x + 202, panel.y + 64, 150,
                campaignShop ? "ORE" : "HANGAR",
                campaignShop ? ((ctx == null || ctx.player == null) ? "0" : String.valueOf(ctx.player.cargo)) : ("TIER " + hangarTier),
                new Color(158, 196, 255));
        drawShopMetricPill(gx, panel.x + 362, panel.y + 64, 250,
                campaignShop ? "FLAGSHIP" : "CURRENT HULL",
                shopRoleTitle(player.role),
                new Color(255, 206, 122));
        drawShopMetricPill(gx, panel.x + 622, panel.y + 64, 170,
                campaignShop ? "FLEET CAPS" : "SUPERWEAPON",
                campaignShop ? campaignFleetCount(ctx) : superweaponStatusReadout(player),
                new Color(156, 224, 255));

        Rectangle upgradesArea = getShopUpgradeArea(panel);
        Rectangle hullArea = getShopHullArea(panel);
        drawShopSectionLabel(gx, upgradesArea.x, upgradesArea.y, "UPGRADES", "Weapons, defenses, and capped frame tuning");
        drawShopSectionLabel(gx, hullArea.x, hullArea.y, "HULL BAY", category.subtitle());
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
                        ? "Tabs: [1] Escort  [2] Line  [3] Capital  [4] Titan   Page: [Left/Right] or [ / ]. Campaign hulls obey shipyard tiers, command-grid limits, sector unlocks, and late titan infrastructure."
                        : "Hull tabs: [1] Escort  [2] Line  [3] Capital  [4] Titan   Page: [Left/Right] or [ / ]. Tier-locked hulls need a stronger base hangar.",
                panel.x + 22, panel.y + panel.height - 18);
        gx.dispose();
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
                title = "Energy Bolt Primary";
                line1 = active ? "Current mount: ENERGY_BOLT" : "Swap the primary weapon back to the standard bolt";
                line2 = active ? "Balanced fire profile with no credit cost" : "Click to equip instantly";
                buttonLabel = active ? "ACTIVE" : "EQUIP";
                enabled = !active;
                accentStrong = active;
                accent = new Color(118, 214, 255);
            }
            case SHOP_UPGRADE_BEAM_BOLT -> {
                boolean active = player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT;
                title = "Beam Bolt Primary";
                line1 = active ? "Current mount: BEAM_BOLT" : "Heavy direct-energy bolt with slower cadence";
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
        int bandCap = campaignShop ? CampaignSystem.persistentFleetCap(fleetBand) : 0;
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
        boolean oreAffordable = !campaignShop || (ctx != null && ctx.player != null && ctx.player.cargo >= oreCost);
        boolean capOk = !campaignShop || bandCount < bandCap;
        boolean commandOk = standardCommandOk && eliteCommandOk;
        boolean enabled = !current && tierOk && sectorOk && mobileStationOk && commandOk && affordable && oreAffordable && capOk;
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
                : (campaignShop && !capOk)
                ? (fleetBand.label() + " cap " + bandCount + "/" + bandCap + " full")
                : (campaignShop ? "Ready to commission" : "Ready for swap"));
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
        else if (!capOk) buttonLabel = "CAP FULL";
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

        g2.setColor(new Color(0, 0, 0, 205));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(new Color(255, 240, 180, 230));
        g2.drawString("POWER MANAGEMENT", x + 18, y + 30);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("O/ESC close   1-6 select bus   <-/-> or [/] adjust   F1-F4 presets", x + 18, y + 48);
        g2.drawString("7 overload on/off   8 cycle overload bus   9 cycle repair priority   0 emergency thrust", x + 18, y + 64);
        g2.drawString("Bars fill to useful cap. Gold tick = nominal power, red tick = saturation point.", x + 18, y + 80);

        String[] labels = {"PROPULSION", "SHIELD", "TACTICAL", "SENSOR", "ENGINEERING", "SUPERCHARGE"};
        double[] values = player.powerBusFractions();

        int rowY = y + 110;
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
            g2.drawString((i + 1) + ": " + labels[i], x + 20, ry + 13);

            int bx = x + 150;
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

        int py = y + 292;
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Effects Preview", x + 20, py);
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
        g2.drawString("Shield Gate: " + shieldGateReadout(player) + "   Super Charge: "
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
        for (Ship.InternalSystem system : Ship.InternalSystem.values()) {
            Ship.SubsystemState st = player.subsystemState(system);
            if (stateLine.length() > 0) stateLine.append("   ");
            stateLine.append(shortSystemName(system)).append(":").append(st.name());
        }
        g2.setColor(new Color(220, 230, 245, 210));
        g2.drawString(stateLine.toString(), x + 20, py);

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

        drawHudPanelFrame(g2, x, y, w, h, "FLIGHT DECK CONTROL", new Color(146, 210, 255, 225));

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(225, 236, 250, 188));
        g2.drawString("/ or ESC close   F1-F5 select slot   [ ] move focus   -/+ cycle role", x + 18, y + 46);
        g2.drawString("Each slot is a 2-ship squad pair. 6 fighter   7 drone   8 bomber   9 all fighters   0 all bombers", x + 18, y + 62);
        g2.drawString("Backspace default mix   Total deck: 5 squads / 10 craft", x + 18, y + 78);

        int focus = Math.max(0, Math.min(4, focusSlot));
        int slotGap = 12;
        int slotW = (w - 36 - slotGap * 4) / 5;
        int slotH = 132;
        int slotY = y + 108;
        int fighters = 0;
        int bombers = 0;
        int drones = 0;

        for (int i = 0; i < 5; i++) {
            ShipRole role = carrier.flightDeckRoleAt(i);
            if (role == ShipRole.BOMBER) bombers += 2;
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
            g2.drawString(selected ? "ACTIVE 2-SHIP PAIR" : "READY", slotX + 12, slotY + 116);
        }

        int summaryY = slotY + slotH + 34;
        drawHudStatusChip(g2, "PAIR SIZE 2", x + 18, summaryY, 104, 18, new Color(140, 210, 255, 214), true);
        drawHudStatusChip(g2, "FIGHTER " + fighters, x + 132, summaryY, 102, 18, flightDeckRoleColor(ShipRole.FIGHTER), fighters > 0);
        drawHudStatusChip(g2, "DRONE " + drones, x + 244, summaryY, 94, 18, flightDeckRoleColor(ShipRole.DRONE), drones > 0);
        drawHudStatusChip(g2, "BOMBER " + bombers, x + 348, summaryY, 104, 18, flightDeckRoleColor(ShipRole.BOMBER), bombers > 0);
        drawHudStatusChip(g2, "MODE " + carrier.carrierCommandMode.name(), x + 462, summaryY, 122, 18,
                new Color(236, 196, 132, 214), carrier.carrierCommandMode == Ship.CarrierCommandMode.DEFEND);
        drawHudStatusChip(g2, "AUTO " + (carrier.carrierAutoLaunch ? "ON" : "OFF"), x + 594, summaryY, 96, 18,
                new Color(148, 228, 182, 214), carrier.carrierAutoLaunch);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(216, 228, 240, 190));
        g2.drawString("Launch rhythm: each launch call emits one 2-ship squad from the next squad slot in sequence.", x + 18, y + h - 38);
        g2.drawString("Defend mode recalls bomber squads and fighter escorts before the next pair leaves the deck.", x + 18, y + h - 20);
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
        if (role == ShipRole.BOMBER) return new Color(255, 168, 124);
        if (role == ShipRole.DRONE) return new Color(150, 226, 204);
        return new Color(132, 190, 255);
    }

    private static String flightDeckRoleLabel(ShipRole role) {
        if (role == ShipRole.BOMBER) return "HEAVY BOMBER";
        if (role == ShipRole.DRONE) return "MULTIROLE DRONE";
        return "ESCORT FIGHTER";
    }

    private static String flightDeckRoleAbbrev(ShipRole role) {
        if (role == ShipRole.BOMBER) return "BMB";
        if (role == ShipRole.DRONE) return "DRN";
        return "FGT";
    }

    private static String flightDeckRoleDescription(ShipRole role) {
        if (role == ShipRole.BOMBER) return "ANTI-SHIP STRIKE";
        if (role == ShipRole.DRONE) return "FLEX SUPPORT";
        return "BOMBER ESCORT";
    }

    public static void drawCrewStationsOverlay(Graphics2D g2, GameContext ctx) {
        if (g2 == null || ctx == null || ctx.player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(1010, clip.width - 56);
        int h = 438;
        int x = (clip.width - w) / 2;
        int y = Math.max(34, (clip.height - h) / 2);

        g2.setColor(new Color(0, 0, 0, 214));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        g2.setColor(new Color(255, 240, 180, 230));
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.drawString("CREW STATIONS", x + 18, y + 30);

        g2.setColor(new Color(255, 255, 255, 170));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("H/ESC close   F1-F5 stations   A toggle station AI   <-/-> cycle station", x + 18, y + 48);

        int portraitPaneX = x + 18;
        int portraitPaneY = y + 70;
        int portraitPaneW = 232;
        int portraitPaneH = h - 88;

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
        int panelW = x + w - panelX - 14;
        int textRight = x + w - 18;

        int tabX = panelX + 8;
        int tabY = y + 70;
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
        g2.drawString("Current Readouts", readoutX, ly);
        ly += 20;

        int lockDist = -1;
        if (ctx.lockedTarget != null && ctx.lockedTarget.alive) {
            lockDist = (int) Math.round(Math.hypot(ctx.lockedTarget.x - ctx.player.x, ctx.lockedTarget.y - ctx.player.y));
        }
        boolean sensorsOnline = !ctx.player.isSystemDestroyed(Ship.InternalSystem.SENSORS);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(210, 235, 255, 220));
        g2.drawString("Captain: " + ctx.command.captainDirective + "   Helm: " + ctx.command.helmMode + "   Tactical: " + ctx.command.tacticalMode, readoutX, ly);
        ly += 16;
        g2.drawString("Engineering: " + ctx.command.engineeringMode + "   Fleet: " + ctx.command.alliedFleetCommand + " / " + ctx.command.alliedFleetFormation, readoutX, ly);
        ly += 16;
        g2.drawString("Engineering Priority: " + ctx.player.engineeringPriority()
                + "   Overload: " + (ctx.player.isOverloadActive() ? ("ACTIVE " + ctx.player.overloadBus().name()) : "STANDBY")
                + "   Heat " + (int) Math.round(ctx.player.overloadHeat() * 100.0) + "%", readoutX, ly);
        ly += 16;
        g2.drawString("Emergency Thrust: " + (ctx.player.isEmergencyThrustActive() ? "ACTIVE" : "STANDBY")
                + "   Heat " + (int) Math.round(ctx.player.emergencyThrustHeat() * 100.0) + "%"
                + "   Cooldown " + (int) Math.ceil(ctx.player.emergencyThrustCooldownRemaining()) + "s"
                + "   Propulsion " + (int) Math.round(ctx.player.propulsionRoomIntegrity() * 100.0) + "%", readoutX, ly);
        ly += 16;
        g2.drawString("Lock: " + ((ctx.lockedTarget == null) ? "NONE" : (ctx.lockedTarget.name + " (" + Math.max(0, lockDist) + "m)"))
                + "   Science EW: " + (ctx.command.scienceJamming ? "JAMMING" : "PASSIVE"), readoutX, ly);
        ly += 16;
        g2.drawString("Sensors: " + (sensorsOnline ? "ONLINE" : "DISABLED"), readoutX, ly);
        ly += 16;
        g2.drawString("Crew: " + ctx.player.crewOrder + "  Readiness " + (int) Math.round(ctx.player.crewReadiness() * 100.0) + "%", readoutX, ly);
        ly += 16;
        int fireRooms = ctx.player.activeFireRoomCount();
        double fireLoad = ctx.player.totalFireIntensity();
        ShipRoomLayout.RoomId hotspot = ctx.player.hottestFireRoom();
        String hotspotLabel = "NONE";
        if (hotspot != null) {
            ShipRoomLayout.RoomDef hotspotDef = ShipRoomLayout.roomForId(ctx.player.role, ctx.player.faction, hotspot);
            if (hotspotDef != null && hotspotDef.label != null && !hotspotDef.label.isBlank()) {
                hotspotLabel = hotspotDef.label;
            } else {
                hotspotLabel = hotspot.name();
            }
        }
        g2.drawString("Hazards: FIRE " + fireRooms + " room" + (fireRooms == 1 ? "" : "s")
                + "  Load " + String.format("%.1f", fireLoad)
                + "  Hotspot " + hotspotLabel, readoutX, ly);
        ly += 16;
        String voice = (ctx.ui.voiceCaptionT > 0.0 && ctx.ui.voiceCaption != null && !ctx.ui.voiceCaption.isBlank()) ? ctx.ui.voiceCaption : "IDLE";
        g2.drawString("Voice: " + voice, readoutX, ly);
        ly += 16;
        g2.drawString("Captions: " + (ctx.ui.voiceCaptionsEnabled ? "ON" : "OFF")
                + "   Mix Focus: " + ctx.ui.voiceMixFocus.name()
                + " (" + (int) Math.round(ctx.voiceRoleVolume(ctx.ui.voiceMixFocus) * 100.0) + "%)", readoutX, ly);
        ly += 16;
        g2.drawString("Role Volumes C/H/T/E/S: "
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.CAPTAIN) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.HELM) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.TACTICAL) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.ENGINEERING) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.SCIENCE) * 100.0), readoutX, ly);

        ly += 28;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Station Controls", readoutX, ly);
        ly += 20;
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        switch (ctx.command.activeCrewStation) {
            case CAPTAIN -> {
                g2.setColor(new Color(255, 230, 175, 220));
                g2.drawString("1 BALANCED  2 ATTACK  3 DEFENSE  4 EMERGENCY  5 MINE", readoutX, ly);
                ly += 16;
                g2.drawString("6 ESCORT  7 DEFEND  8 REPAIR  9 RTB  0 CYCLE FLEET FORMATION", readoutX, ly);
                ly += 16;
                g2.drawString("Q/W/E/R assign nearest friendly ATTACK/DEFEND/REPAIR/RTB, T clears override.", readoutX, ly);
                ly += 16;
                g2.drawString("- or BACKSPACE: charge 10s battlefield warp to waypoint/base (damage disrupts).", readoutX, ly);
                ly += 16;
                g2.drawString("Captain directives set ship posture and allied fleet command behavior.", readoutX, ly);
            }
            case HELM -> {
                g2.setColor(new Color(200, 240, 255, 220));
                g2.drawString("1 INTERCEPT  2 ORBIT  3 MAINTAIN RANGE  4 EVASIVE  5 E-THRUST", readoutX, ly);
                ly += 16;
                g2.drawString("Emergency thrust adds burst speed but can overheat propulsion into cooldown.", readoutX, ly);
                ly += 16;
                g2.drawString("Helm automation sets heading/throttle for target pursuit and maneuvering.", readoutX, ly);
            }
            case TACTICAL -> {
                g2.setColor(new Color(255, 210, 180, 220));
                g2.drawString("1 HOLD FIRE  2 DEFENSIVE FIRE  3 AGGRESSIVE FIRE", readoutX, ly);
                ly += 16;
                g2.drawString("Tactical automation drives primary/secondary firing states and lock usage.", readoutX, ly);
            }
            case ENGINEERING -> {
                g2.setColor(new Color(200, 255, 200, 220));
                g2.drawString("1 BALANCED  2 ATTACK BIAS  3 DEFENSE BIAS  4 DAMAGE CONTROL", readoutX, ly);
                ly += 16;
                g2.drawString("5 OVERLOAD ON/OFF  6 CYCLE OVERLOAD BUS  7 CYCLE REPAIR PRIORITY  8 SUPPRESS FIRE", readoutX, ly);
                ly += 16;
                g2.drawString("Engineering automation enforces policy table; manual edits override AI immediately.", readoutX, ly);
            }
            case SCIENCE -> {
                g2.setColor(new Color(220, 210, 255, 220));
                g2.drawString("1 LOCK NEAREST  2 CLEAR LOCK  3 TOGGLE EW/JAMMING", readoutX, ly);
                ly += 16;
                g2.drawString("Science automation manages target acquisition using current sensor capability.", readoutX, ly);
            }
        }

        ly += 24;
        g2.setColor(new Color(190, 245, 220, 220));
        g2.drawString("Voice: C captions on/off  Z/X role focus  ,/. volume -/+  (persisted)", readoutX, ly);

        g2.setClip(oldClip);

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Manual flight/fire/power input immediately disables corresponding station AI.", readoutX, y + h - 16);
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

        g2.setColor(new Color(16, 20, 28, 206));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(170, 210, 255, 115));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        g2.setColor(new Color(205, 235, 255, 220));
        g2.setFont(XRAY_TITLE_FONT);
        g2.drawString((title == null || title.isBlank()) ? "TACTICAL X-RAY" : title, x + 10, y + 18);
        g2.setFont(XRAY_SUBTITLE_FONT);
        g2.setColor(new Color(175, 218, 255, 205));
        if (subtitle != null && !subtitle.isBlank()) {
            g2.drawString(subtitle, x + 10, y + 32);
        }

        Rectangle mapRect = xrayMapRect(x, y, w, h);
        int mapX = mapRect.x;
        int mapY = mapRect.y;
        int mapW = mapRect.width;
        int mapH = mapRect.height;
        g2.setColor(new Color(255, 255, 255, 20));
        g2.fillRoundRect(mapX, mapY, mapW, mapH, 10, 10);
        g2.setColor(new Color(255, 255, 255, 60));
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
            String line = roomLabel + "  HP " + pct + "%  FIRE " + String.format("%.2f", fire)
                    + "  POWER " + (int) Math.round(power * 100.0) + "%" + disruptText;
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
        g2.drawString("FILTER[" + filterLabel + "] ` cycle   ' clear focus   FOCUS: " + focusLabel, x + 10, y + h - 22);
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

    public static void drawBaseUpgradeOverlay(Graphics2D g2, String baseName, int credits, int baseOre,
                                              int hullLv, int shieldLv, int turretLv, int miningLv, int hangarLv,
                                              int maxHangarTier) {
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

        // Panel body
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(x, y, w, h, 20, 20);

        // Inner border
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(x, y, w, h, 20, 20);

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
        g2.drawString("BASE UPGRADE CONSOLE  (ESC)", x + 18, y + 28);

        // Scanline sweep
        int sweepY = y + 42 + (int) Math.round(((Math.sin(t * 0.9) * 0.5 + 0.5)) * (h - 70));
        g2.setColor(new Color(90, 220, 255, 14));
        g2.fillRect(x + 10, sweepY, w - 20, 12);

        // Info
        if (baseName == null) baseName = "Base";
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        int ty = y + 58;
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Base: " + baseName, x + 18, ty);
        ty += 18;

        // Resource readouts (with small pills)
        drawPill(g2, x + 18, ty - 12, 150, "CREDITS", String.valueOf(credits));
        drawPill(g2, x + 178, ty - 12, 150, "BASE ORE", String.valueOf(baseOre));
        drawPill(g2, x + 338, ty - 12, 160, "HANGAR", hangarLv + " / " + maxHangarTier);
        ty += 30;

        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString("Press 1-5 to purchase:", x + 18, ty);
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
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 3, "Turret Systems",    turretLv, 5, new Color(255, 210, 130, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 4, "Mining Ops",        miningLv, 5, new Color(255, 230, 120, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 5, "Hangar Expansion",  hangarLv, maxHangarTier, new Color(210, 170, 255, 220), cCost, oCost);

        g2.setColor(new Color(255, 255, 255, 130));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("Mining Ops boosts mining rate + ore sell value.", x + 18, y + h - 16);
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

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x0, y0, size, size, 16, 16);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.drawRoundRect(x0, y0, size, size, 16, 16);

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
    }


    public static void drawStrategicMap(Graphics2D g2,
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
                                        String bannerTopLine) {

        Rectangle r = getStrategicMapRect(viewW, viewH);

        // Backdrop + glow border (Style B)
        g2.setColor(new Color(0, 0, 0, 205));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        g2.setColor(new Color(140, 200, 255, 55));
        g2.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 24, 24);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        // Inner map area
        int pad = 18;
        Rectangle m = new Rectangle(r.x + pad, r.y + 44, r.width - pad * 2, r.height - 60);

        g2.setColor(new Color(255, 255, 255, 22));
        g2.fillRoundRect(m.x, m.y, m.width, m.height, 16, 16);
        g2.setColor(new Color(255, 255, 255, 55));
        g2.drawRoundRect(m.x, m.y, m.width, m.height, 16, 16);

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 22));
        int step = 80;
        for (int x = m.x + step; x < m.x + m.width; x += step) g2.drawLine(x, m.y, x, m.y + m.height);
        for (int y = m.y + step; y < m.y + m.height; y += step) g2.drawLine(m.x, y, m.x + m.width, y);

        // Title + help
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(new Color(255, 255, 255, 225));
        g2.drawString("STRATEGIC MAP", r.x + 18, r.y + 28);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("LMB: waypoint   RMB: ping   M/ESC: close", r.x + 18, r.y + r.height - 16);

        if (bannerTopLine != null && !bannerTopLine.isBlank()) {
            g2.setColor(new Color(140, 200, 255, 200));
            g2.drawString(bannerTopLine, r.x + 190, r.y + 28);
        }

        // Helpers: world -> map
        java.util.function.BiFunction<Double, Double, Point> W2M = (wx, wy) -> {
            int px = m.x + (int) Math.round((wx / Math.max(1.0, worldW)) * m.width);
            int py = m.y + (int) Math.round((wy / Math.max(1.0, worldH)) * m.height);
            return new Point(px, py);
        };

        // Asteroids
        if (asteroids != null) {
            g2.setColor(new Color(200, 200, 200, 80));
            for (Asteroid a : asteroids) {
                if (a == null) continue;
                Point p = W2M.apply(a.x, a.y);
                g2.fillRect(p.x, p.y, 2, 2);
            }
        }

        // Salvage
        if (salvage != null) {
            g2.setColor(new Color(255, 255, 255, 120));
            for (Salvage s : salvage) {
                if (s == null || !s.alive()) continue;
                Point p = W2M.apply(s.x, s.y);
                g2.fillOval(p.x - 1, p.y - 1, 3, 3);
            }
        }

        // Ships + bases
        if (ships != null) {
            for (Ship s : ships) {
                if (s == null || !s.alive) continue;
                Point p = W2M.apply(s.x, s.y);

                Color c = factionMapColor(s.faction, (s == player), 200);

                int rr = (s.role == ShipRole.BASE) ? 4 : 2;
                g2.setColor(c);
                g2.fillOval(p.x - rr, p.y - rr, rr * 2, rr * 2);
            }
        }

        // Waypoint
        if (!Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
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

        // Camera viewport rectangle
        double vx0 = camX;
        double vy0 = camY;
        double vx1 = camX + camViewW;
        double vy1 = camY + camViewH;

        Point p0 = W2M.apply(vx0, vy0);
        Point p1 = W2M.apply(vx1, vy1);

        int rx = Math.min(p0.x, p1.x);
        int ry = Math.min(p0.y, p1.y);
        int rw = Math.abs(p1.x - p0.x);
        int rh = Math.abs(p1.y - p0.y);

        g2.setColor(new Color(255, 255, 255, 120));
        g2.drawRect(rx, ry, rw, rh);
    }


    // IMPORTANT: This is the method that was likely stubbed/empty in your current project.
    public static void drawShip(Graphics2D g2, Ship ship) {
        ShipRenderer.drawShip(g2, ship);
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
            if (!ship.alive) return;
            boolean multipartDying = ship.dying && ShipPartLibrary.hasParts(ship.role, ship.faction);
            if (multipartDying) {
                int wx = (int) Math.round(ship.x);
                int wy = (int) Math.round(ship.y);
                if (!isTinyStrikeCraft(ship.role)) {
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
            if (ship.isStealth && sig < 0.99) {
                float a = (float) (0.22 + 0.78 * sig);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            }

            ShipVisual visual = getVisual(ship);
            Area hullArea = buildArea(visual.hullPolys);
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

            drawShipShieldFaces(g, ship, hullArea);

            if (hullArea != null) {
                drawDamageDecals(g, ship, hullArea);
                drawWarpChargeHullFx(g, ship, hullArea);
            }

            if (DevTools.isDebugOverlay()) {
                drawRoomDebugOverlay(g, ship);
            }

            if (ship.isStealth && sig < 0.99 && !visual.hullPolys.isEmpty()) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                g.setColor(new Color(120, 220, 255, 110));
                g.draw(visual.hullPolys.get(0));
            }

            g.dispose();

            if (!isTinyStrikeCraft(ship.role)) {
                g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                g2.setColor(new Color(255, 255, 255, 130));
                g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
            }
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

            Rectangle2D bounds = buildArea(v.hullPolys).getBounds2D();
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

        private static boolean drawMultipartDamageStage(Graphics2D g, Ship ship, int sw, int sh) {
            ShipPartLibrary.PartSet normal = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.NORMAL);
            if (!normal.hasParts()) return false;

            double hpFrac = (ship == null || ship.hpMax <= 0)
                    ? 1.0
                    : MathUtil.clamp(ship.hp / (double) ship.hpMax, 0.0, 1.0);
            ShipPartLibrary.PartSet damaged = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.DAMAGED);
            ShipPartLibrary.PartSet critical = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.CRITICAL);

            if (hpFrac > 2.0 / 3.0) {
                drawSkinParts(g, normal.parts, sw, sh, 1.0f);
                return true;
            }

            if (hpFrac > 1.0 / 3.0 && damaged.variant == ShipPartLibrary.Variant.DAMAGED) {
                float t = (float) MathUtil.clamp((2.0 / 3.0 - hpFrac) / (1.0 / 3.0), 0.0, 1.0);
                drawSkinParts(g, normal.parts, sw, sh, 1.0f - t);
                drawSkinParts(g, damaged.parts, sw, sh, t);
                return true;
            }

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
        private static BufferedImage energyBoltSkin;
        private static BufferedImage beamBoltSkin;
        private static BufferedImage waveShotSkin;
        private static BufferedImage bulletSkin;
        private static BufferedImage ciwsPelletSkin;
        private static boolean missileSkinLoaded = false;
        private static boolean energyBoltSkinLoaded = false;
        private static boolean beamBoltSkinLoaded = false;
        private static boolean waveShotSkinLoaded = false;
        private static boolean bulletSkinLoaded = false;
        private static boolean ciwsPelletSkinLoaded = false;

        static BufferedImage getMissileSkin() {
            if (missileSkinLoaded) return missileSkin;
            missileSkinLoaded = true;
            missileSkin = loadSkin("missile");
            return missileSkin;
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
        if (ship.isStealth && sig < 0.99) {
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
        if (ship.isStealth && sig < 0.99) {
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
                } else if (ship.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) {
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
        if (ship != null && ship.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) return "beam_emitter";
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
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int w = r + 8;
        int h = r + 5;
        int barrelLen = (int) Math.max(8, Math.round(t.barrelLen * 0.85 * barrelScale));
        int recoil = (int) Math.round(fireFrac * 2.0);

        g.setColor(new Color(34, 40, 50, 210));
        g.fillOval(-w / 2, -h / 2, w, h);
        g.setColor(mix(new Color(118, 130, 146), accent, 0.25));
        g.fillRoundRect(-w / 2, -h / 2, w, h, 5, 5);

        g.setColor(new Color(150, 210, 255, 160));
        g.fillOval(-2, -2, 4, 4);
        g.setColor(new Color(210, 240, 255, 215));
        g.drawRoundRect(0 - recoil, -1, barrelLen, 2, 2, 2);

        if (fireFrac > 0.62) {
            int glow = (int) Math.round(70 + fireFrac * 120);
            g.setColor(new Color(120, 220, 255, Math.max(0, Math.min(220, glow))));
            g.fillOval(barrelLen - recoil - 4, -4, 8, 8);
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
        if (span * screenScale < HULL_DAMAGE_DETAIL_MIN_SCREEN_SPAN) return;
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
            drawDestroyedHullBreaches(g, ship, hullShape, marks, span);
            drawImpactHoleOverlays(g, marks);
        } finally {
            g.setStroke(oldStroke);
            g.setClip(oldClip);
        }
    }

    private static void drawImpactHoleOverlays(Graphics2D g, List<Ship.HullImpactMark> marks) {
        if (g == null || marks == null || marks.isEmpty()) return;
        int start = Math.max(0, marks.size() - 18);
        for (int i = start; i < marks.size(); i++) {
            Ship.HullImpactMark mark = marks.get(i);
            if (mark == null || mark.breachRadius <= 0.01) continue;

            int px = (int) Math.round(mark.localX);
            int py = (int) Math.round(mark.localY);
            double sev = MathUtil.clamp(mark.severity, 0.04, 1.0);
            int br = (int) Math.round(Math.max(1.0, mark.breachRadius * IMPACT_DECAL_SCALE));

            g.setColor(new Color(8, 8, 10, (int) MathUtil.clamp(95 + sev * 110, 0, 220)));
            g.fillOval(px - br, py - br, br * 2, br * 2);
            g.setColor(roomTraceTint(mark.roomId, (int) MathUtil.clamp(26 + sev * 58, 0, 140)));
            g.drawOval(px - br, py - br, br * 2, br * 2);
        }
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
        if (projectile == null) return false;
        if (projectile instanceof PhaserBeam beam) {
            return isWorldSegmentVisible(beam.startX(), beam.startY(), beam.endX(), beam.endY(),
                    Math.max(beam.width, 16.0), minX, minY, maxX, maxY);
        }
        if (projectile instanceof PointDefenseLaser laser) {
            return isWorldSegmentVisible(laser.startX(), laser.startY(), laser.endX, laser.endY,
                    Math.max(laser.width, 10.0), minX, minY, maxX, maxY);
        }
        double radius = Math.max(8.0, projectile.radius + 12.0);
        if (projectile instanceof CIWSPellet) radius = Math.max(radius, 24.0);
        else if (projectile instanceof Missile) radius = Math.max(radius, 18.0);
        return isWorldCircleVisible(projectile.x, projectile.y, radius, minX, minY, maxX, maxY);
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
