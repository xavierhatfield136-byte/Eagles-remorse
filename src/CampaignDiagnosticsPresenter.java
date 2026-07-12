import java.util.List;

final class CampaignDiagnosticsPresenter {
    private CampaignDiagnosticsPresenter() {}

    static List<String> productionReadinessLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        return ProductionReadinessLongevitySystem.commandBoardLines((st == null) ? null : st.productionReadiness);
    }

    static List<String> structuredTelemetryLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.productionReadiness == null) return List.of("Campaign telemetry unavailable.");
        List<String> log = st.productionReadiness.longevity.campaignEventLog;
        if (log.isEmpty()) return List.of("Campaign telemetry empty.");
        int start = Math.max(0, log.size() - 6);
        return List.copyOf(log.subList(start, log.size()));
    }

    static List<String> releaseTelemetryHistory(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.productionReadiness == null) return List.of();
        List<String> log = st.productionReadiness.longevity.campaignEventLog;
        int start = Math.max(0, log.size() - 64);
        return List.copyOf(log.subList(start, log.size()));
    }

    static List<String> releaseTelemetryContractLines() {
        return List.of(
                "campaign.fleet.created  |  forceId, kind, faction, origin, reason",
                "campaign.fleet.destroyed  |  forceId, kind, faction, reason",
                "campaign.fleet.disappeared  |  forceId, reason",
                "campaign.ownership.changed  |  location, from, to, reason",
                "campaign.production.start/stop  |  orderId, kind, role, source, reason",
                "campaign.mining.departure/return  |  forceId, source/destination, cargo, reason",
                "campaign.mission.success / campaign.failure  |  sector, objective, reason",
                "campaign.strike.denied  |  action, target label, reason",
                "campaign.save_recovery  |  sourceVersion, targetVersion, verified, repairs",
                "Privacy  |  no credentials, user names, email addresses, filesystem paths, or machine identifiers"
        );
    }

    static List<String> fleetDoctrineExpansionLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        return StretchGoalsFleetDoctrineSystem.commandBoardLines((st == null) ? null : st.fleetDoctrineExpansion);
    }

    static List<String> deepSimulationLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        return DeepCampaignSimulationSystem.commandBoardLines((st == null) ? null : st.deepCampaignExpansion);
    }

    static List<String> communityContentLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        return CommunityContentSystem.commandBoardLines((st == null) ? null : st.communityContent);
    }

    static List<String> difficultyTelemetryLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null) return List.of("Difficulty telemetry unavailable.");
        int losses = 0;
        int retreats = 0;
        int battles = st.campaignAfterActionReports.size();
        for (CampaignSystem.AfterActionReport report : st.campaignAfterActionReports) {
            if (report == null) continue;
            String text = (report.result + " " + report.losses + " " + report.resources).toLowerCase(java.util.Locale.US);
            if (text.contains("defeat") || text.contains("failed") || text.contains("destroyed")) losses++;
            if (text.contains("retreat") || text.contains("withdraw")) retreats++;
        }
        int emergencies = 0;
        if (CampaignSystem.campaignFuel(ctx) < 16) emergencies++;
        if (CampaignSystem.campaignSupplies(ctx) < 12) emergencies++;
        if (CampaignSystem.campaignAmmo(ctx) < 14) emergencies++;
        return CampaignDifficultySystem.telemetryLines(ctx.experience, battles, losses, retreats, emergencies);
    }

    static List<String> doctrineAuditLines(GameContext ctx) {
        GameContext.FleetFormation selected = ctx == null || ctx.command == null || ctx.command.alliedFleetFormation == null
                ? GameContext.FleetFormation.WEDGE : ctx.command.alliedFleetFormation;
        return List.of(
                "WEDGE: fast concentration and attack lead; vulnerable to enveloping fire.",
                "LINE: broad lateral spacing, overlapping fields of fire, and shallow reinforcement rows; slower to turn.",
                "SCREEN: circular protection for capitals, carriers, and transports; weaker forward concentration.",
                "ASSAULT: role-layered spearhead with escorts forward and support aft; highest commitment risk.",
                "Selected: " + selected + "  |  no formation changes raw hull health or creates free ships.",
                "Workload: one formation choice applies to the fleet; individual micromanagement remains optional."
        );
    }

    static List<String> difficultyModifierLines(GameContext ctx) {
        return CampaignDifficultySystem.modifierLines(ctx == null ? null : ctx.experience);
    }

    static List<String> difficultyRuntimeConsumerAuditLines() {
        return CampaignDifficultySystem.runtimeConsumerAuditLines();
    }
}
