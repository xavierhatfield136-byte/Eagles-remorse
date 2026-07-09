import app.config.ExperienceSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Player-facing campaign arc, objective, difficulty, and ending summaries. */
public final class CampaignArcSummarySystem {
    public static final int STANDARD_TARGET_MIN_HOURS = 3;
    public static final int STANDARD_TARGET_MAX_HOURS = 5;

    private CampaignArcSummarySystem() {}

    public static List<String> campaignArcIdentityLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        ArrayList<String> out = new ArrayList<>();
        CampaignPhase phase = phase(st);
        out.add("Campaign Phase: " + phase.label + "  |  verbs: " + phase.verbs
                + "  |  threats: " + phase.threats
                + "  |  economy: " + phase.economy);
        out.add("Green identity  |  holds repair, patrol, and convoy infrastructure; behavior: escorts, counter-patrols, relief routes.");
        out.add("Bright Yellow identity  |  civilian trade, rebel cells, refugees, and liberation logistics; behavior: convoys, sabotage, evacuation support.");
        out.add("Dark Yellow identity  |  coercive breakaway cells, black-market yards, and opportunistic raiders; behavior: raids, traps, contested aid.");
        out.add("Red identity  |  fortress depots, listening bastions, fuel depots, and Earthward siege groups; behavior: blockade, pursuit, suppression, counterattack.");
        out.add("Strategic Problems: early survival and route learning; middle contested diplomacy, recon, and logistics; late Earth approach, Red reserves, and readiness locks.");
        out.add("Optional Choices Matter: allied favor, completed sites, rescued ships, fleet growth, and abandoned territory feed Earth readiness and ending text.");
        out.add("Standard Target Length: " + STANDARD_TARGET_MIN_HOURS + "-" + STANDARD_TARGET_MAX_HOURS
                + " hours for a successful first Standard Command campaign.");
        return out;
    }

    public static List<String> mainObjectiveGuidanceLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null) return List.of("Main Objective unavailable.");
        CampaignPhase phase = phase(st);
        CampaignSystem.CampaignFinalBattleReadiness readiness = CampaignSystem.campaignFinalBattleReadiness(ctx);
        ArrayList<String> out = new ArrayList<>();
        out.add("Main Objective: Open the route to Earth and defeat the Earthfall command core.");
        out.add("Immediate Step: " + immediateStep(ctx, st, phase));
        out.add("Reason: " + immediateReason(st, readiness));
        out.add("Route Guidance: move north through selected route markers, but detour for hubs, allies, recon, and repairs when risk or shortages rise.");
        out.add("Earth Readiness: " + readiness.readinessScore + "/100  |  " + readiness.summary);
        out.add("Earth Lock Conditions: stabilize theaters and improve readiness through Green/Yellow favor, captured shipyards/relays, rescued ships, destroyed Red fortresses, and fleet repair.");
        out.add("Exploration Rule: optional sites are allowed detours because they change support, readiness, and ending inputs.");
        return out;
    }

    public static List<String> difficultyPresetRuleLines(ExperienceSettings.Preset preset) {
        ExperienceSettings settings = ExperienceSettings.forPreset(preset);
        ArrayList<String> out = new ArrayList<>();
        out.add("Preset: " + settings.preset);
        if (settings.preset == ExperienceSettings.Preset.STANDARD) {
            out.add("Default: Standard Command is the default campaign ruleset.");
        }
        if (settings.tacticalOnly) {
            out.add("Structure: Tactical Only suppresses strategic pressure and route attrition so play centers on battles.");
        } else if (settings.commandOnly) {
            out.add("Structure: Command Only keeps campaign command pressure while reducing direct tactical deployment emphasis.");
        } else if (settings.ironCommand) {
            out.add("Structure: Iron Command limits checkpoint recovery and raises tactical/campaign punishment.");
        } else if (settings.preset == ExperienceSettings.Preset.RELAXED) {
            out.add("Structure: Relaxed lowers first-30-minute strategic pressure and resource loss to leave recovery room.");
        } else {
            out.add("Structure: Standard keeps tactical combat, strategic pressure, attrition, and recovery fully active.");
        }
        out.addAll(settings.modifierSummaryLines());
        return out;
    }

    public static List<String> campaignEndingSummaryLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null) return List.of("Ending summary unavailable.");
        CampaignSystem.CampaignFinalBattleReadiness readiness = CampaignSystem.campaignFinalBattleReadiness(ctx);
        int completedSites = completedSiteCount(ctx);
        int abandoned = CampaignSystem.campaignLedgerEntryValue(st, "territory_abandoned");
        int alliedBattles = CampaignSystem.campaignLedgerEntryValue(st, "allied_battles_joined");
        int rescued = Math.max(0, st.bossDropsCollected) + Math.max(0, st.persistentBlueFleet.size());
        String family = endingFamily(readiness.readinessScore, st.greenContractFavor, st.yellowLiberationFavor, abandoned);
        ArrayList<String> out = new ArrayList<>();
        out.add("Ending Family: " + family);
        out.add("Allies: Green favor " + st.greenContractFavor + "  |  Yellow favor " + st.yellowLiberationFavor
                + "  |  allied battles joined " + alliedBattles);
        out.add("Territory/Sites: completed sites " + completedSites + "  |  abandoned territory " + abandoned
                + "  |  Earth operation stage " + st.earthOperationStage);
        out.add("Fleet Record: surviving Blue fleet slots " + st.persistentBlueFleet.size()
                + "  |  rescued/retained ships " + rescued
                + "  |  campaign kills " + st.campaignKills
                + "  |  objective losses " + st.objectiveAssetLosses);
        out.add("Operations: completed board missions " + st.completedCampaignBoardMissionIds.size()
                + "  |  expired missions " + st.expiredCampaignBoardMissionIds.size());
        out.add("Earth Readiness: " + readiness.readinessScore + "/100  |  " + readiness.summary);
        out.add("Persistence: unlocks aux=" + st.unlockAuxGunGranted
                + " missileTier=" + st.unlockMissileTierGranted
                + " ciws=" + st.unlockCiwsGranted
                + " hull=" + st.unlockHullGranted
                + " survive return-to-menu checkpoint restore.");
        return out;
    }

    private static CampaignPhase phase(CampaignSystem.CampaignState st) {
        double progress = st == null ? 0.0 : Math.max(st.earthProgress, Math.max(0, st.completedMainMissions) / 24.0);
        if (st != null && (st.earthOperationStage >= 2 || progress >= 0.68)) {
            return new CampaignPhase("LATE",
                    "breach, commit, counterstrike, preserve readiness",
                    "Earth cordon, Red reserves, fortresses, occupation core",
                    "fuel/ammo burn, fleet strain, final repair windows");
        }
        if (st != null && (st.earthOperationStage >= 1 || progress >= 0.34 || st.completedMainMissions >= 8)) {
            return new CampaignPhase("MIDDLE",
                    "recon, choose allies, break blockades, stabilize routes",
                    "mixed Yellow crisis, Red hunt groups, contested hubs",
                    "supplies, repair materials, favor, shipyard throughput");
        }
        return new CampaignPhase("EARLY",
                "scout, mine, repair, learn routes, make first allies",
                "patrols, convoy danger, weak intel, local shortages",
                "ore, fuel, supplies, first upgrades");
    }

    private static String immediateStep(GameContext ctx, CampaignSystem.CampaignState st, CampaignPhase phase) {
        if (st.campaignFuel < 24 || st.campaignSupplies < 18) return "resupply before the next northbound move";
        CampaignSystem.CampaignLocation selected = selectedLocation(ctx);
        if (selected != null && selected.primaryMission && !selected.completed) {
            return "commit or prepare for " + selected.name;
        }
        if (phase.label.equals("LATE")) return "raise Earth readiness, cut Red core defenses, then enter the Earth chain";
        if (phase.label.equals("MIDDLE")) return "stabilize a route, resolve allied commitments, and break the next blockade";
        return "select the next northern route marker, scout it, and use safe hubs before overextending";
    }

    private static String immediateReason(CampaignSystem.CampaignState st,
                                          CampaignSystem.CampaignFinalBattleReadiness readiness) {
        if (st.campaignFuel < 24 || st.campaignSupplies < 18) return "current stores are below safe route thresholds.";
        if (readiness.readinessScore < 32) return "Earth assault is currently underprepared.";
        if (readiness.readinessScore < 55) return "Earth assault is possible, but support and route stability are thin.";
        return "readiness is building; route risk and optional support now shape final losses and ending text.";
    }

    private static CampaignSystem.CampaignLocation selectedLocation(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null || st.selectedGalaxyLocationId == null || st.selectedGalaxyLocationId.isBlank()) return null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && st.selectedGalaxyLocationId.equals(location.id)) return location;
        }
        return null;
    }

    private static int completedSiteCount(GameContext ctx) {
        if (ctx == null) return 0;
        int count = 0;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.completed) count++;
        }
        return count;
    }

    private static String endingFamily(int readiness, int greenFavor, int yellowFavor, int abandoned) {
        if (greenFavor >= 8 && yellowFavor >= 8 && abandoned <= 1) return "Coalition Restoration";
        if (readiness >= 78 && greenFavor >= 5 && yellowFavor >= 5) return "Coalition Restoration";
        if (readiness >= 55 && abandoned <= 2) return "Costly Liberation";
        if (readiness >= 32) return "Under-Supported Breakthrough";
        return "Desperate Earthfall";
    }

    private record CampaignPhase(String label, String verbs, String threats, String economy) {}
}
