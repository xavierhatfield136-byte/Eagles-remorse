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
        FLEET("Fleet Management", "Open fleet management with TAB. Review commitment, reserve, refit, and commissioning."),
        CONTACT("First Contact", "A contact can be auto-resolved for speed or fought manually by taking command."),
        STRIKE("First Strike", "Before launching a strike, review cost, target quality, and the campaign consequences."),
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
        boolean archiveOpen;
        boolean contactSeen;
        boolean strikeSeen;
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
        g2.drawString("FIRST-HOUR COMMAND BRIEFING  " + (beat.ordinal() + 1) + "/9", x + 14, y + 22);
        g2.setFont(new Font("Consolas", Font.BOLD, 17));
        g2.setColor(Color.WHITE);
        g2.drawString(beat.title, x + 14, y + 45);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(214, 228, 244));
        g2.drawString(trim(beat.detail, 82), x + 14, y + 65);
        g2.setColor(shouldShowReminder(ctx) ? ExperienceRuntime.warningColor() : new Color(158, 180, 204));
        g2.drawString((shouldShowReminder(ctx) ? "Reminder: " : "") + "Ctrl+F1 skip beat   Ctrl+F2 archive", x + 14, y + 84);
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
        int cy = y + 62;
        for (Beat beat : Beat.values()) {
            if (beat == Beat.COMPLETE) continue;
            boolean done = state.complete.contains(beat);
            boolean skipped = state.skipped.contains(beat);
            g2.setColor(done ? new Color(116, 232, 168) : new Color(180, 198, 220));
            g2.drawString((done ? "[x] " : "[ ] ") + beat.title + (skipped ? "  (skipped)" : ""), x + 20, cy);
            g2.setColor(new Color(168, 188, 212));
            g2.drawString(trim(beat.detail, 94), x + 42, cy + 16);
            cy += 43;
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
        if (ctx.ui.mapOpen) state.complete.add(Beat.MAP);
        if (ctx.ui.shopOpen || ctx.state == GameState.FLEET || ctx.ui.campaignHubMenu.active) state.complete.add(Beat.FLEET);
        if (CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) state.contactSeen = true;
        if (state.contactSeen && !CampaignSystem.hasPendingStrategicEncounterChoice(ctx)) state.complete.add(Beat.CONTACT);
        if (!CampaignSystem.lastStrikeReportTitle(ctx).isBlank()) {
            state.strikeSeen = true;
            state.complete.add(Beat.STRIKE);
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
}
