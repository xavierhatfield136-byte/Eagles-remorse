import app.config.GameMode;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Non-blocking campaign coach. It teaches one command decision at a time and
 * retains a replayable archive without interrupting normal play.
 */
public final class FirstHourOnboardingSystem {
    public enum Beat {
        MOVEMENT("Movement", "Use WASD to move the flagship. Your formation follows your command ship."),
        MINING("Mining", "Move beside an asteroid and use F to mine. Ore and supplies keep the fleet moving."),
        DOCKING("Docking", "Return to a friendly installation. Docking opens refit, repair, and recovery options."),
        MAP("Map Use", "Open the map with M. Set waypoints before committing the fleet to a route."),
        MAP_MARKERS("Map Markers", "Select locations, contacts, and free-space points; pings and overlays keep the picture readable."),
        ROUTE("Route Orders", "Select a location, contact, or free-space point, then Plot Course and Engage Course."),
        ROUTE_CONTROL("Route Control", "Cancel a bad course, hold position, cycle theaters, and use overlays before pressure closes in."),
        SCANNING("Scanning", "Use Recon Sweep, Focused Track, Traffic Audit, Scout Surge, and relays to improve the picture."),
        INTEL("Intelligence", "Intel improves from Unknown to Detected, Identified, Tracked, and Target-Quality."),
        CONTACT_CONFIDENCE("Contact Confidence", "Contacts can fade, move, or mislead you; confidence tells you how much to trust the track."),
        LOCAL_SITES("Local Sites", "Approach sites before entering; some offer site plans, services, salvage, rescues, or resource rewards."),
        ALLIES("Allies", "Spend Green support or Yellow leverage when the fleet needs stores, intel, or route relief."),
        REPUTATION("Reputation", "Rescue, trade, force, and atomic choices change how factions respond to your command."),
        FLEET("Fleet Management", "Open fleet management with TAB. Review commitment, reserve, refit, and commissioning."),
        FLEET_ROSTER("Fleet Roster", "Select persistent hulls, inspect condition, and choose which ships join, reserve, or hold back."),
        FLEET_ORGANIZATION("Fleet Organization", "Assign hulls to commit, reserve, hold back, flag group, or detachments before contact."),
        UPGRADES("Upgrades And Refit", "Use docked services or the fleet tab to improve hulls, turrets, CIWS, and readiness."),
        REFIT_DETAILS("Refit Details", "Select ship and turret slots, swap weapon kinds, set missile roles, and review upgrade categories."),
        COMMISSIONING("Commissioning", "Shipyards turn credits, ore, and salvage into new persistent fleet hulls."),
        ECONOMY("Economy", "Track credits, ore, fuel, supplies, ammo, and salvage separately before each route."),
        RESOURCE_RECOVERY("Resource Recovery", "Ore, salvage, caches, resource zones, and trade services fund repairs, upgrades, and commissions."),
        CONTACT("First Contact", "A contact can be auto-resolved for speed or fought manually by taking command."),
        TACTICAL_ENGAGEMENT("Tactical Engagement", "Close contact opens a tactical sector; damage, strain, and losses carry back to the overmap."),
        TACTICAL_WEAPONS("Tactical Weapons", "Primary, secondary, missile, point-defense, and room-damage tools all solve different threats."),
        CARRIER_TACTICS("Carrier Tactics", "Carrier wings can launch, recall, defend, auto-launch, or shift behavior as the battle changes."),
        TACTICAL_STRIKES("Tactical Strikes", "Torpedo, sortie, and atomic strikes are taught in tactical contact, not launched from the overworld."),
        SALVAGE_EXTRACTION("Salvage And Extraction", "Disengage, recover salvage, and return to the overworld with persistent consequences."),
        SHORTAGE("Resource Shortage", "Supplies are low. Dock, trade, mine, salvage, or reduce sensor tempo to recover."),
        SAVE("Checkpoint", "Campaign checkpoints are visible after saves. Use F10 to save before leaving the session."),
        COMPLETE("Archive Ready", "The paced command briefing is complete. Reopen this archive with Ctrl+F2.");

        final String title;
        final String detail;

        Beat(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }

    private static final class State {
        final EnumSet<Beat> complete = EnumSet.noneOf(Beat.class);
        final EnumSet<Beat> skipped = EnumSet.noneOf(Beat.class);
        double startX;
        double startY;
        int startCargo;
        int startCredits;
        int startOre;
        int startFuel;
        int startSupplies;
        int startAmmo;
        int startSalvage;
        int startFleetSize;
        double startIntel;
        int startGreenFavor;
        int startYellowLeverage;
        String startFleetPostureId = "";
        boolean archiveOpen;
        boolean contactSeen;
        boolean tacticalEngagementSeen;
        boolean tacticalStrikeSeen;
        boolean shortageSeen;
        boolean checkpointSeen;
        double idleSeconds;
        Beat current = Beat.MOVEMENT;
    }

    private static final WeakHashMap<GameContext, State> STATES = new WeakHashMap<>();
    private static final double STUCK_REMINDER_SECONDS = 42.0;

    private FirstHourOnboardingSystem() {}

    public static void init(GameContext ctx) {
        if (!supports(ctx)) return;
        State state = new State();
        if (ctx.player != null) {
            state.startX = ctx.player.x;
            state.startY = ctx.player.y;
            state.startCargo = ctx.player.cargo;
        }
        if (ctx.campaign != null) {
            state.startCredits = ctx.credits;
            state.startOre = CampaignSystem.currentCampaignOre(ctx);
            state.startFuel = CampaignSystem.campaignFuel(ctx);
            state.startSupplies = CampaignSystem.campaignSupplies(ctx);
            state.startAmmo = CampaignSystem.campaignAmmo(ctx);
            state.startSalvage = CampaignSystem.campaignSalvageStock(ctx);
            state.startIntel = ctx.campaign.campaignIntelLevel;
            state.startGreenFavor = ctx.campaign.greenContractFavor;
            state.startYellowLeverage = ctx.campaign.yellowLiberationFavor;
            state.startFleetSize = CampaignSystem.campaignFleetRosterEntries(ctx).size();
            state.startFleetPostureId = ctx.campaign.selectedFleetPostureId == null ? "" : ctx.campaign.selectedFleetPostureId;
        }
        STATES.put(ctx, state);
    }

    public static void update(GameContext ctx, double dt) {
        State state = state(ctx);
        if (state == null) return;
        Beat before = state.current;
        observe(ctx, state);
        advance(state);
        if (before != state.current) {
            state.idleSeconds = 0.0;
            EventSystem.showBanner(ctx, "COMMAND BRIEFING: " + state.current.title.toUpperCase(), 1.8);
        } else {
            state.idleSeconds += Math.max(0.0, dt);
        }
    }

    public static void skipCurrent(GameContext ctx) {
        State state = state(ctx);
        if (state == null || state.current == Beat.COMPLETE) return;
        state.skipped.add(state.current);
        state.complete.add(state.current);
        advance(state);
        state.idleSeconds = 0.0;
        EventSystem.showBanner(ctx, "BRIEFING BEAT SKIPPED", 1.0);
    }

    public static void toggleArchive(GameContext ctx) {
        State state = state(ctx);
        if (state == null) return;
        state.archiveOpen = !state.archiveOpen;
    }

    public static void noteCheckpointSaved(GameContext ctx) {
        State state = state(ctx);
        if (state == null) return;
        state.checkpointSeen = true;
        EventSystem.showBanner(ctx, "CHECKPOINT SAVED - CAMPAIGN RESUME UPDATED", 2.2);
    }

    public static boolean isArchiveOpen(GameContext ctx) {
        State state = state(ctx);
        return state != null && state.archiveOpen;
    }

    public static Beat currentBeat(GameContext ctx) {
        State state = state(ctx);
        return (state == null) ? Beat.COMPLETE : state.current;
    }

    public static boolean shouldShowReminder(GameContext ctx) {
        State state = state(ctx);
        return state != null && state.current != Beat.COMPLETE && state.idleSeconds >= STUCK_REMINDER_SECONDS;
    }

    public static void draw(GameContext ctx, Graphics2D g2, int viewW, int viewH) {
        State state = state(ctx);
        if (state == null || g2 == null) return;
        if (state.archiveOpen) {
            drawArchive(state, g2, viewW, viewH);
            return;
        }
        Beat beat = state.current;
        if (beat == Beat.COMPLETE) return;
        int w = Math.min(560, Math.max(340, viewW - 40));
        int x = (viewW - w) / 2;
        int y = 18;
        g2.setColor(new Color(5, 12, 24, 226));
        g2.fillRoundRect(x, y, w, 94, 18, 18);
        g2.setColor(new Color(112, 190, 255, 210));
        g2.drawRoundRect(x, y, w, 94, 18, 18);
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(140, 206, 255));
        int beatCount = Math.max(1, Beat.values().length - 1);
        g2.drawString("FIRST-HOUR COMMAND BRIEFING  " + (beat.ordinal() + 1) + "/" + beatCount, x + 14, y + 22);
        g2.setFont(new Font("Consolas", Font.BOLD, 17));
        g2.setColor(Color.WHITE);
        g2.drawString(beat.title, x + 14, y + 45);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(214, 228, 244));
        g2.drawString(trim(beat.detail, 82), x + 14, y + 65);
        g2.setColor(shouldShowReminder(ctx) ? ExperienceRuntime.warningColor() : new Color(158, 180, 204));
        g2.drawString((shouldShowReminder(ctx) ? "Reminder: " : "") + reminderFor(beat), x + 14, y + 84);
    }

    private static void drawArchive(State state, Graphics2D g2, int viewW, int viewH) {
        int w = Math.min(720, viewW - 48);
        int h = Math.min(520, viewH - 48);
        int x = (viewW - w) / 2;
        int y = (viewH - h) / 2;
        g2.setColor(new Color(3, 8, 18, 244));
        g2.fillRoundRect(x, y, w, h, 22, 22);
        g2.setColor(new Color(118, 198, 255));
        g2.drawRoundRect(x, y, w, h, 22, 22);
        g2.setFont(new Font("Consolas", Font.BOLD, 20));
        g2.setColor(Color.WHITE);
        g2.drawString("COMMAND BRIEFING ARCHIVE", x + 20, y + 34);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        Beat[] beats = activeBeats();
        int columns = beats.length > 10 ? 2 : 1;
        int colW = (w - 40) / columns;
        int rowH = columns == 2 ? 35 : 43;
        int topY = y + 62;
        for (int i = 0; i < beats.length; i++) {
            Beat beat = beats[i];
            if (beat == Beat.COMPLETE) continue;
            int col = columns == 1 ? 0 : i % 2;
            int row = columns == 1 ? i : i / 2;
            int tx = x + 20 + col * colW;
            int cy = topY + row * rowH;
            if (cy > y + h - 54) break;
            boolean done = state.complete.contains(beat);
            boolean skipped = state.skipped.contains(beat);
            g2.setColor(done ? new Color(116, 232, 168) : new Color(180, 198, 220));
            g2.drawString((done ? "[x] " : "[ ] ") + beat.title + (skipped ? "  (skipped)" : ""), tx, cy);
            g2.setColor(new Color(168, 188, 212));
            g2.drawString(trim(beat.detail, columns == 2 ? 42 : 94), tx + 22, cy + 15);
        }
        g2.setColor(new Color(158, 180, 204));
        g2.drawString("Ctrl+F2 closes archive. Completed and skipped beats remain available for replay reference.", x + 20, y + h - 18);
    }

    private static void observe(GameContext ctx, State state) {
        if (ctx.player != null) {
            if (GameMath.dist2(ctx.player.x, ctx.player.y, state.startX, state.startY) > 160.0 * 160.0) state.complete.add(Beat.MOVEMENT);
            if (ctx.player.cargo > state.startCargo) state.complete.add(Beat.MINING);
        }
        if (CampaignSystem.currentBaseUpgradeAnchor(ctx) != null && state.complete.contains(Beat.MINING)) state.complete.add(Beat.DOCKING);
        if (ctx.ui.mapOpen) {
            state.complete.add(Beat.MAP);
            if (!ctx.ui.mapPings.isEmpty()
                    || CampaignSystem.hasSelectedFreeTravelTarget(ctx)
                    || ctx.campaign != null && ctx.campaign.selectedGalaxyLocationId != null && !ctx.campaign.selectedGalaxyLocationId.isBlank()
                    || CampaignSystem.hasSelectedCampaignContactTarget(ctx)) {
                state.complete.add(Beat.MAP_MARKERS);
            }
        }
        if (CampaignSystem.hasSelectedFreeTravelTarget(ctx)
                || ctx.campaign != null && ctx.campaign.selectedGalaxyLocationId != null && !ctx.campaign.selectedGalaxyLocationId.isBlank()
                || ctx.campaign != null && ctx.campaign.galaxyTravel != null && ctx.campaign.galaxyTravel.traveling) {
            state.complete.add(Beat.ROUTE);
        }
        if (ctx.campaign != null && ctx.campaign.galaxyTravel != null
                && (ctx.campaign.galaxyTravel.traveling || !CampaignSystem.hasSelectedFreeTravelTarget(ctx))) {
            state.complete.add(Beat.ROUTE_CONTROL);
        }
        if (ctx.campaign != null) {
            if (ctx.campaign.campaignIntelLevel > state.startIntel + 0.5
                    || ctx.campaign.sensorRelayNodes.size() > 0
                    || !CampaignSystem.selectedCampaignContactIntelLabel(ctx).isBlank()) {
                state.complete.add(Beat.SCANNING);
            }
            String selectedIntel = CampaignSystem.selectedCampaignContactIntelLabel(ctx);
            if (!selectedIntel.isBlank()
                    || ctx.campaign.campaignIntelLevel >= Math.max(18.0, state.startIntel + 8.0)) {
                state.complete.add(Beat.INTEL);
            }
            if (CampaignSystem.hasSelectedCampaignContactTarget(ctx)
                    || !ctx.campaign.galaxySearchGroups.isEmpty()) {
                state.complete.add(Beat.CONTACT_CONFIDENCE);
            }
            if (CampaignSystem.selectedCampaignLocation(ctx) != null
                    || CampaignSystem.canEnterSelectedLocalEncounter(ctx)
                    || ctx.campaign.galaxyAmbientEncounterActive) {
                state.complete.add(Beat.LOCAL_SITES);
            }
            if (ctx.campaign.greenContractFavor != state.startGreenFavor
                    || ctx.campaign.yellowLiberationFavor != state.startYellowLeverage) {
                state.complete.add(Beat.ALLIES);
            }
            if (!CampaignSystem.campaignReputationReadout(ctx).isBlank()
                    && (ctx.campaign.greenContractFavor != state.startGreenFavor
                    || ctx.campaign.yellowLiberationFavor != state.startYellowLeverage
                    || ctx.campaign.recentStrikePressure > 0.0
                    || ctx.campaign.fleetStrain > 0.0)) {
                state.complete.add(Beat.REPUTATION);
            }
            if (ctx.ui.shopOpen || ctx.state == GameState.FLEET || ctx.ui.campaignHubMenu.active
                    || ctx.ui.campaignCommandTab == UiState.CampaignCommandTab.FLEET) {
                state.complete.add(Beat.FLEET);
            }
            if (ctx.ui.campaignFleetFocusSlotId > 0 || ctx.ui.fleetSelectedShipId > 0) {
                state.complete.add(Beat.FLEET_ROSTER);
            }
            boolean postureChanged = !state.startFleetPostureId.equalsIgnoreCase(
                    ctx.campaign.selectedFleetPostureId == null ? "" : ctx.campaign.selectedFleetPostureId);
            if (postureChanged || ctx.ui.campaignFleetFocusSlotId > 0
                    || ctx.ui.selectedStrategicDivisionGroupId > 0) {
                state.complete.add(Beat.FLEET_ORGANIZATION);
            }
            if (ctx.ui.shopOpen || ctx.ui.campaignHubMenu.active || ctx.state == GameState.FLEET && ctx.ui.fleetSelectedShipId > 0) {
                state.complete.add(Beat.UPGRADES);
            }
            if (ctx.ui.fleetSelectedShipId > 0 || ctx.ui.fleetSelectedTurretIndex >= 0
                    || ctx.ui.shopOpen && ctx.ui.fleetRefitMode) {
                state.complete.add(Beat.REFIT_DETAILS);
            }
            if (CampaignSystem.campaignFleetRosterEntries(ctx).size() > state.startFleetSize
                    || !ctx.campaign.campaignYardOrders.isEmpty()) {
                state.complete.add(Beat.COMMISSIONING);
            }
            if (ctx.credits != state.startCredits
                    || CampaignSystem.currentCampaignOre(ctx) != state.startOre
                    || CampaignSystem.campaignFuel(ctx) != state.startFuel
                    || CampaignSystem.campaignSupplies(ctx) != state.startSupplies
                    || CampaignSystem.campaignAmmo(ctx) != state.startAmmo
                    || CampaignSystem.campaignSalvageStock(ctx) != state.startSalvage) {
                state.complete.add(Beat.ECONOMY);
                state.complete.add(Beat.RESOURCE_RECOVERY);
            }
        }
        if (CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) state.contactSeen = true;
        if (state.contactSeen && !CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) state.complete.add(Beat.CONTACT);
        if (ctx.campaign != null && (ctx.campaign.galaxyEncounterActive || !CampaignSystem.isStrategicOvermapMode(ctx))) {
            state.tacticalEngagementSeen = true;
            state.complete.add(Beat.TACTICAL_ENGAGEMENT);
        }
        if (state.tacticalEngagementSeen && ctx.player != null) {
            state.complete.add(Beat.TACTICAL_WEAPONS);
            if (ctx.player.isCarrier || ctx.ui.flightDeckOpen) {
                state.complete.add(Beat.CARRIER_TACTICS);
            }
        }
        if (state.tacticalEngagementSeen && !CampaignSystem.lastStrikeReportTitle(ctx).isBlank()) {
            state.tacticalStrikeSeen = true;
            state.complete.add(Beat.TACTICAL_STRIKES);
        }
        if (state.tacticalEngagementSeen && ctx.campaign != null && CampaignSystem.isStrategicOvermapMode(ctx)) {
            state.complete.add(Beat.SALVAGE_EXTRACTION);
        }
        if (CampaignSystem.campaignSupplies(ctx) < 26) {
            state.shortageSeen = true;
            state.complete.add(Beat.SHORTAGE);
        }
        if (state.checkpointSeen) state.complete.add(Beat.SAVE);
    }

    private static void advance(State state) {
        while (state.current != Beat.COMPLETE && state.complete.contains(state.current)) {
            state.current = Beat.values()[state.current.ordinal() + 1];
        }
    }

    private static State state(GameContext ctx) {
        return (ctx == null) ? null : STATES.get(ctx);
    }

    private static boolean supports(GameContext ctx) {
        return ctx != null && ctx.config != null
                && ctx.config.mode == GameMode.CAMPAIGN_OPS
                && !ctx.config.resumeCampaign;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static Beat[] activeBeats() {
        ArrayList<Beat> beats = new ArrayList<>();
        for (Beat beat : Beat.values()) {
            if (beat != Beat.COMPLETE) beats.add(beat);
        }
        return beats.toArray(new Beat[0]);
    }

    private static String reminderFor(Beat beat) {
        String control = "Ctrl+F1 skip beat   Ctrl+F2 archive";
        if (beat == null) return control;
        return switch (beat) {
            case ROUTE -> "Select a map target, Plot Course, then Engage Course.   " + control;
            case ROUTE_CONTROL -> "Engage the plotted route, or cancel/hold if the route is bad.   " + control;
            case SCANNING -> "Open Strikes/Navigation and run Recon Sweep or Focused Track.   " + control;
            case INTEL -> "Select a contact and improve its track until the intel label changes.   " + control;
            case LOCAL_SITES -> "Approach a local site or hub before entering or using services.   " + control;
            case ALLIES -> "Use Green support or Yellow leverage once favor is available.   " + control;
            case FLEET_ORGANIZATION -> "Open Fleet, select a hull, then change commitment or command group.   " + control;
            case REFIT_DETAILS -> "Select a ship or turret slot before changing weapons or missile roles.   " + control;
            case COMMISSIONING -> "Dock at a shipyard and queue a hull when credits, ore, and salvage allow.   " + control;
            case RESOURCE_RECOVERY -> "Mine, salvage, trade, or clear resource sites to rebuild stores.   " + control;
            case TACTICAL_STRIKES -> "Use strike weapons only after a tactical contact is active.   " + control;
            case SALVAGE_EXTRACTION -> "Recover what you can, disengage if needed, then return to the overmap.   " + control;
            case SHORTAGE -> "Dock, trade, mine, salvage, or reduce sensor spending.   " + control;
            default -> control;
        };
    }
}
