import java.util.ArrayList;
import java.util.List;

/** Focused Phase 5 tactical/control/UI presentation contracts and small actions. */
public final class PhaseFiveTacticalCleanupSystem {
    private PhaseFiveTacticalCleanupSystem() {}

    public record RoleBalanceMeasurement(String role, double burstDamage, double survivalPool,
                                         double escapeValue, String counterplay, String conclusion) {}

    public record ReserveControlView(String composition, String arrivalRule, String eta,
                                     String spawnEdge, String blockedReason, boolean deployAvailable,
                                     boolean recallAvailable) {}

    public static List<String> fighterDeadlockRegressionLines() {
        return List.of(
                "Scenario: two opposing fighters start 280 units apart on a clean custom-battle board.",
                "Old failure: both craft could orbit inside practical pellet range without firing or resetting approach.",
                "Acceptance: within 8 seconds they must exchange damage, disengage, or deliberately reposition by at least 48 units.",
                "Rule: small-craft dogfights clamp practical gun range and rotate nose-on while bad approaches flip orbit direction."
        );
    }

    public static List<String> escortToleranceLines() {
        return List.of(
                "Escort arrival tolerance: no correction warp when escort is inside 720 units or anchor radius + 320.",
                "Escort slot tolerance: reserved alternating side slots keep exits outside the anchor center and separated from each other.",
                "Safety: heavily damaged escorts, reserve-role ships, and ships near active threats do not formation-warp."
        );
    }

    public static RoleBalanceMeasurement stealthRevealWindowMeasurement() {
        Ship stealth = new FleetShip(ShipRole.STEALTH_SHIP, Faction.ALLY, 0.0, 0.0);
        double burst = weaponBurstDamage(stealth, 4.0) * 1.18;
        double survival = Math.max(1.0, stealth.hp + stealth.shield);
        double escape = MovementModel.speedCeiling(stealth) * 1.12;
        return new RoleBalanceMeasurement(
                "STEALTH_SHIP",
                burst,
                survival,
                escape,
                "Detection counterplay remains: stealth can be hit/revealed by damage, sensors, and proximity instead of becoming permanently untargetable.",
                "Reveal window value comes from burst/disruption/escape, not raw durability."
        );
    }

    public static RoleBalanceMeasurement ciwsSecondaryRoleMeasurement() {
        Ship ciws = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ALLY, 0.0, 0.0);
        double burst = weaponBurstDamage(ciws, 4.0);
        double survival = Math.max(1.0, ciws.hp + ciws.shield);
        return new RoleBalanceMeasurement(
                "CIWS_CORVETTE",
                burst,
                survival,
                ciws.hasCIWS ? 1.0 : 0.0,
                "Primary identity preserved: CIWS remains anti-fighter/anti-missile point defense first.",
                "Secondary ship-to-ship value is modest but non-zero through standard gun mounts."
        );
    }

    public static List<String> combinedArmsLoopLines() {
        return List.of(
                "Carrier: projects bombers/fighters and creates pressure windows.",
                "Picket: screens carrier/capital flanks and catches hostile small craft.",
                "Capital: anchors the line and punishes targets exposed by carrier and picket pressure.",
                "Loop: carrier pressure creates openings; pickets keep strike craft alive; capitals convert openings into kills."
        );
    }

    public static ReserveControlView reserveControlView(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null) {
            return new ReserveControlView("No campaign reserve", "Unavailable", "n/a", "n/a",
                    "campaign offline", false, false);
        }
        int fleet = Math.max(0, st.persistentBlueFleet.size());
        int reserve = (int) Math.round(Math.max(0.0, st.blueInterventionReserve));
        boolean deploy = reserve >= 20 && fleet > 0 && !st.galaxyAmbientSupportRequested;
        boolean recall = st.galaxyAmbientSupportRequested;
        String blocker = deploy ? "" : (recall ? "reserve already requested"
                : fleet <= 0 ? "no reserve composition available"
                : "reserve below 20%");
        return new ReserveControlView(
                "Blue reserve " + reserve + "%  |  persistent hulls " + fleet,
                "Deploy when reserve is >=20%, then arrive from safest friendly map edge at next tactical contact.",
                deploy ? "next tactical contact" : (recall ? "pending" : "blocked"),
                "safest friendly edge opposite the selected hostile or objective",
                blocker,
                deploy,
                recall
        );
    }

    public static String deployReserve(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        ReserveControlView view = reserveControlView(ctx);
        if (st == null || !view.deployAvailable) return "RESERVE DEPLOY BLOCKED  |  " + view.blockedReason;
        st.galaxyAmbientSupportRequested = true;
        st.blueInterventionReserve = Math.max(0.0, st.blueInterventionReserve - 20.0);
        st.campaignMemoryFlags.add("RESERVE_TUTORIAL_SEEN");
        return "RESERVE DEPLOY CONFIRMED  |  arrival " + view.arrivalRule;
    }

    public static String recallReserve(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null || !st.galaxyAmbientSupportRequested) return "RESERVE RECALL BLOCKED  |  no pending reserve";
        st.galaxyAmbientSupportRequested = false;
        return "RESERVE RECALL CONFIRMED  |  pending reserve stood down";
    }

    public static String reserveTutorialPrompt(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null || st.campaignMemoryFlags.contains("RESERVE_TUTORIAL_SEEN")) return "";
        ReserveControlView view = reserveControlView(ctx);
        if (!view.deployAvailable && !view.recallAvailable) return "";
        return "Reserve Prompt: review composition, ETA, spawn edge, blocker, then Deploy or Recall.";
    }

    public static boolean shouldCollapseTopHints(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return false;
        return ctx.ui.campaignTopHintsCollapsed
                || ctx.ui.shopOpen
                || ctx.ui.mapOpen
                || ctx.ui.powerManagementOpen
                || ctx.ui.crewStationsOpen
                || ctx.ui.commsOpen
                || ctx.ui.campaignHubMenu.active
                || ctx.ui.campaignActionConfirm.active;
    }

    public static String toggleTopHintsPreference(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return "TOP HINTS UNAVAILABLE";
        ctx.ui.campaignTopHintsCollapsed = !ctx.ui.campaignTopHintsCollapsed;
        ctx.ui.campaignTopHintsPreferenceRemembered = true;
        return ctx.ui.campaignTopHintsCollapsed ? "TOP HINTS COLLAPSED" : "TOP HINTS EXPANDED";
    }

    public static List<String> crewAutomationExplanationLines(GameContext ctx) {
        if (ctx == null || ctx.command == null) return List.of("Crew automation unavailable.");
        ArrayList<String> out = new ArrayList<>();
        out.add("Crew Automation: Captain " + onOff(ctx.command.captainAutomation)
                + "  Helm " + onOff(ctx.command.helmAutomation)
                + "  Tactical " + onOff(ctx.command.tacticalAutomation)
                + "  Engineering " + onOff(ctx.command.engineeringAutomation));
        out.add("Captain presets coordinate helm, tactical, engineering, and power posture.");
        out.add("Manual station input turns that station's automation off so the player can override it immediately.");
        out.add("Use crew station controls to toggle automation before hunting through deeper menus.");
        return out;
    }

    public static List<String> strategicTopFoldLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        List<String> objective = CampaignArcSummarySystem.mainObjectiveGuidanceLines(ctx);
        out.add(objective.isEmpty() ? "Main Objective unavailable." : objective.get(0));
        out.add(objective.size() > 1 ? objective.get(1) : "Immediate Step unavailable.");
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        String selected = (ctx != null && CampaignSystem.selectedCampaignContactLabel(ctx) != null
                && !CampaignSystem.selectedCampaignContactLabel(ctx).isBlank())
                ? CampaignSystem.selectedCampaignContactLabel(ctx)
                : (st == null || st.selectedGalaxyLocationId == null || st.selectedGalaxyLocationId.isBlank()
                ? "none" : st.selectedGalaxyLocationId);
        out.add("Selected Target: " + selected);
        List<String> route = ctx == null ? List.of() : CampaignSystem.selectedRouteAssessmentLines(ctx);
        out.add(route.isEmpty() ? "Route Risk: no route selected" : "Route Risk: " + route.get(0));
        out.add("Primary Actions: scout, plot route, travel, resupply, strike/rearm when target quality allows.");
        return out;
    }

    public static List<String> artBaselineLines() {
        return List.of(
                "Turret Baseline: hull-local hardpoints must sample on-hull after sprite origin, rotation, and scale are applied.",
                "Blue Hyperweapon Titan: mounts must span port/starboard flanks and at least two longitudinal bands without center bias.",
                "Shield Baseline: shield faces are composited as a separate aura/shell layer after hull paint, so shield color cannot tint hull sprites.",
                "Screenshot Baselines: representative Blue hyperweapon titan, shielded titan, Green capital, Yellow civilian hull, and Red fortress hull."
        );
    }

    private static double weaponBurstDamage(Ship ship, double seconds) {
        if (ship == null || ship.turrets == null) return 0.0;
        double total = 0.0;
        for (Turret turret : ship.turrets) {
            if (turret == null) continue;
            double cooldown = Math.max(0.10, turret.cooldown);
            int shots = Math.max(1, (int) Math.floor(Math.max(0.1, seconds) / cooldown));
            total += Math.max(1, turret.damage) * shots;
        }
        return total;
    }

    private static String onOff(boolean value) {
        return value ? "AUTO" : "MANUAL";
    }
}
