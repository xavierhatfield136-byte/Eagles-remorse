import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class CampaignAfterActionSystem {
    private CampaignAfterActionSystem() {}

    static List<String> campaignForceAfterActionLines(GameContext ctx,
                                                      CampaignSystem.CampaignState st,
                                                      int maxLines) {
        if (ctx == null || st == null || maxLines <= 0) return List.of();
        ArrayList<ForceOutcomeLine> candidates = new ArrayList<>();
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force == null || force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) continue;
            if (!force.simulationActive && !force.destroyed && force.strength > 20.0
                    && force.intent != CampaignSystem.CampaignForceIntent.RETREATING
                    && force.intent != CampaignSystem.CampaignForceIntent.REGROUPING
                    && force.intent != CampaignSystem.CampaignForceIntent.REPAIRING) continue;
            String outcome = "";
            if (force.destroyed || force.strength <= 1.0) {
                outcome = "DESTROYED";
            } else if (force.intent == CampaignSystem.CampaignForceIntent.RETREATING) {
                outcome = "ROUTED";
            } else if (force.intent == CampaignSystem.CampaignForceIntent.REGROUPING
                    || force.intent == CampaignSystem.CampaignForceIntent.REPAIRING
                    || force.hullIntegrity < 70.0 || force.readiness < 62.0) {
                outcome = "DAMAGED";
            }
            if (outcome.isBlank()) continue;
            int priority = switch (outcome) {
                case "DESTROYED" -> 0;
                case "ROUTED" -> 1;
                default -> 2;
            };
            candidates.add(new ForceOutcomeLine(priority, force.strength,
                    "FORCE OUTCOME  |  " + outcome + "  |  " + force.name
                    + "  |  strength " + (int) Math.round(force.strength)
                    + "  readiness " + (int) Math.round(force.readiness)));
        }
        candidates.sort((a, b) -> {
            int priority = Integer.compare(a.priority, b.priority);
            if (priority != 0) return priority;
            return Double.compare(a.strength, b.strength);
        });
        ArrayList<String> out = new ArrayList<>();
        for (ForceOutcomeLine candidate : candidates) {
            out.add(candidate.line);
            if (out.size() >= maxLines) break;
        }
        return out;
    }

    private record ForceOutcomeLine(int priority, double strength, String line) {}

    static List<String> campaignAfterActionPlateLines(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null) return List.of();
        String top = CampaignSystem.transitionSummaryTop(ctx);
        String bottom = CampaignSystem.transitionSummaryBottom(ctx);
        if ((top == null || top.isBlank()) && (bottom == null || bottom.isBlank())) return List.of();
        ArrayList<String> out = new ArrayList<>();
        if (top != null && !top.isBlank()) out.add(top);
        if (bottom != null && !bottom.isBlank()) out.add(bottom);
        if (st.transitionRewardLine != null && !st.transitionRewardLine.isBlank()) {
            out.add("REWARD  |  " + st.transitionRewardLine.toUpperCase(Locale.US));
        }
        if (st.transitionRouteImpactLine != null && !st.transitionRouteImpactLine.isBlank()) {
            out.add("ROUTE  |  " + st.transitionRouteImpactLine.toUpperCase(Locale.US));
        }
        out.add("REPUTATION  |  " + CampaignSystem.campaignReputationReadout(ctx).toUpperCase(Locale.US));
        out.add("THEATER  |  " + CampaignSystem.theaterPressureReadout(ctx).toUpperCase(Locale.US));
        out.addAll(campaignForceAfterActionLines(ctx, st, 3));
        return out;
    }

    static List<String> campaignAfterActionReportLines(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        String location = campaignAfterActionLocationLabel(st);
        String objective = CampaignSystem.trimmedOrFallback(st.objectiveLabel, "Command the fleet");
        out.add("Battle Report: " + location + "  |  Objective: " + objective);
        if (st.objectivePhaseLabel != null && !st.objectivePhaseLabel.isBlank()) {
            out.add("Battle State: " + st.objectivePhaseLabel.trim());
        }
        String top = CampaignSystem.transitionSummaryTop(ctx);
        String bottom = CampaignSystem.transitionSummaryBottom(ctx);
        if (top != null && !top.isBlank()) out.add("Outcome: " + top.trim());
        if (bottom != null && !bottom.isBlank()) out.add("Campaign Impact: " + bottom.trim());
        if (st.transitionRewardLine != null && !st.transitionRewardLine.isBlank()) {
            out.add("Rewards: " + st.transitionRewardLine.trim());
        }
        if (st.transitionRouteImpactLine != null && !st.transitionRouteImpactLine.isBlank()) {
            out.add("Route Impact: " + st.transitionRouteImpactLine.trim());
        }
        out.addAll(campaignForceAfterActionLines(ctx, st, 3));
        out.add("Friendly Fleet: " + campaignAfterActionFleetDamageLabel(st));
        out.add("Resources: credits " + Math.max(0, ctx.credits)
                + "  ore " + CampaignSystem.currentCampaignOre(ctx)
                + "  fuel " + CampaignSystem.campaignFuel(ctx)
                + "  supplies " + CampaignSystem.campaignSupplies(ctx)
                + "  ammo " + CampaignSystem.campaignAmmo(ctx)
                + "  salvage " + CampaignSystem.campaignSalvageStock(ctx));
        out.add("Reputation: " + CampaignSystem.campaignReputationReadout(ctx));
        out.add("Intel: " + CampaignSystem.campaignIntelReadout(ctx)
                + "  |  Exposure " + CampaignSystem.campaignExposureReadout(ctx));
        out.add("Theater Pressure: " + CampaignSystem.theaterPressureReadout(ctx));
        if (st.lastTheaterOperationDebrief != null && !st.lastTheaterOperationDebrief.isBlank()) {
            out.add("Follow-On: " + st.lastTheaterOperationDebrief.trim());
        } else if (st.lastStrikeReportTitle != null && !st.lastStrikeReportTitle.isBlank()) {
            out.add("Follow-On: " + st.lastStrikeReportTitle.trim()
                    + (st.lastStrikeReportDetail == null || st.lastStrikeReportDetail.isBlank()
                    ? "" : "  |  " + st.lastStrikeReportDetail.trim()));
        } else {
            out.add("Follow-On: " + CampaignSystem.campaignFleetStrainReadout(ctx));
        }
        out.addAll(campaignAfterActionPersonnelLines(st, 4));
        out.addAll(campaignAfterActionFollowUpActionLines(ctx));
        recordAfterActionSnapshotIfNew(st, location, out);
        return out;
    }

    static List<String> campaignLatestAfterActionReportLines(CampaignSystem.CampaignState st) {
        if (st == null || st.campaignAfterActionReports.isEmpty()) return List.of("No after-action reports recorded.");
        CampaignSystem.AfterActionReport report = st.campaignAfterActionReports.get(st.campaignAfterActionReports.size() - 1);
        return afterActionReportDisplayLines(report);
    }

    static List<String> campaignAfterActionFollowUpActionLines(GameContext ctx) {
        if (ctx == null) return List.of("Report Button: unavailable");
        String[] ids = {
                "COLLECT_SALVAGE",
                "RESCUE_SURVIVORS",
                "OPEN_REPAIRS",
                "INSPECT_FLEET",
                "OPEN_CAPTAINS_LOG",
                "OPEN_WAR_ROOM",
                "RETURN_TO_MAP",
                "CONTACT_SURVIVORS",
                "REQUEST_GREEN_SUPPORT"
        };
        LinkedHashMap<String, CampaignSystem.CampaignAction> byId = new LinkedHashMap<>();
        for (CampaignSystem.CampaignAction action : CampaignSystem.campaignVisibleActions(ctx)) {
            if (action != null) byId.put(action.id, action);
        }
        ArrayList<String> out = new ArrayList<>();
        for (String id : ids) {
            CampaignSystem.CampaignAction action = byId.get(id);
            if (action == null) {
                out.add("Report Button: " + campaignReportButtonFallbackLabel(id)
                        + " - Disabled: unavailable in current context");
            } else {
                String state = action.enabled
                        ? "Ready"
                        : "Disabled: " + CampaignSystem.trimmedOrFallback(action.disabledReason, "unavailable");
                out.add("Report Button: " + action.label + " - " + state);
            }
        }
        if (out.isEmpty()) out.add("Report Button: no follow-up actions available");
        return out;
    }

    static List<String> campaignCaptainLogLines(CampaignSystem.CampaignState st,
                                                int maxLines,
                                                int newestOffset,
                                                String filter) {
        if (st == null) return List.of("Captain's Log unavailable.");
        int limit = Math.max(1, maxLines);
        ArrayList<String> out = new ArrayList<>();
        ArrayList<CampaignSystem.CampaignLogEntry> filtered = new ArrayList<>();
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.US);
        for (CampaignSystem.CampaignLogEntry entry : st.campaignCaptainLog) {
            if (entry == null) continue;
            if (!needle.isBlank()) {
                String haystack = (entry.category + " " + entry.title + " " + entry.detail + " " + entry.consequence)
                        .toLowerCase(Locale.US);
                if (!haystack.contains(needle)) continue;
            }
            filtered.add(entry);
        }
        if (filtered.isEmpty()) {
            out.add("Captain's Log: no major campaign entries yet.");
            if (!st.theaterWarRecentEvents.isEmpty()) {
                out.add("Latest War Event: " + st.theaterWarRecentEvents.get(st.theaterWarRecentEvents.size() - 1));
            }
            return out;
        }
        int emitted = 0;
        int skipped = 0;
        int offset = Math.max(0, newestOffset);
        for (int i = filtered.size() - 1; i >= 0 && emitted < limit; i--) {
            CampaignSystem.CampaignLogEntry entry = filtered.get(i);
            if (entry == null) continue;
            if (skipped++ < offset) continue;
            out.add((entry.major ? "MAJOR " : "") + entry.category.toUpperCase(Locale.US)
                    + " #" + entry.id + "  T+" + entry.theaterTick + ": " + entry.title);
            out.add("  " + entry.detail);
            out.add("  Consequence: " + entry.consequence);
            emitted++;
        }
        return out;
    }

    static List<String> campaignMemoryFlagLines(CampaignSystem.CampaignState st, int maxLines) {
        if (st == null) return List.of("Campaign memory unavailable.");
        if (st.campaignMemoryFlags.isEmpty()) return List.of("Campaign memory: no persistent flags recorded yet.");
        ArrayList<String> flags = new ArrayList<>(st.campaignMemoryFlags);
        int start = Math.max(0, flags.size() - Math.max(1, maxLines));
        ArrayList<String> out = new ArrayList<>();
        for (int i = start; i < flags.size(); i++) {
            out.add("Memory: " + flags.get(i));
        }
        return out;
    }

    static String campaignAfterActionLogDetail(CampaignSystem.CampaignState st,
                                               CampaignSystem.AfterActionReport snapshot) {
        if (snapshot == null) return "After-action report recorded.";
        ArrayList<String> parts = new ArrayList<>();
        parts.add("Region/Location: " + snapshot.location);
        parts.add("Result: " + snapshot.result);
        parts.add("Factions: Blue command / Green support / Yellow traffic / Red pressure as applicable");
        ArrayList<String> personnel = new ArrayList<>(campaignAfterActionPersonnelLines(st, 2));
        for (String line : personnel) {
            if (line == null || line.isBlank()) continue;
            if (line.startsWith("Named Ships: ")) parts.add("Ships: " + line.substring("Named Ships: ".length()));
            else if (line.startsWith("Named Captains: ")) parts.add("Captains: " + line.substring("Named Captains: ".length()));
        }
        if (snapshot.location != null && !snapshot.location.isBlank()) {
            parts.add("Base/Station: " + snapshot.location);
        }
        if (st != null && !st.campaignMemoryFlags.isEmpty()) {
            ArrayList<String> flags = new ArrayList<>(st.campaignMemoryFlags);
            String previous = flags.get(flags.size() - 1);
            if (previous != null && !previous.isBlank()) {
                parts.add("Callback: remembers " + previous.trim());
            }
        }
        return String.join("  |  ", parts);
    }

    private static List<String> campaignAfterActionPersonnelLines(CampaignSystem.CampaignState st, int maxEntries) {
        if (st == null) return List.of();
        ArrayList<CampaignSystem.PersistentFleetEntry> fleet = new ArrayList<>(st.persistentBlueFleet);
        fleet.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            if (a.destroyed != b.destroyed) return Boolean.compare(b.destroyed, a.destroyed);
            int damage = Double.compare(
                    Math.min(a.hullConditionFrac, a.shieldConditionFrac),
                    Math.min(b.hullConditionFrac, b.shieldConditionFrac));
            if (damage != 0) return damage;
            return Integer.compare(a.slotId, b.slotId);
        });
        ArrayList<String> ships = new ArrayList<>();
        ArrayList<String> captains = new ArrayList<>();
        int crewCasualties = 0;
        int crewRescued = 0;
        int limit = Math.max(1, maxEntries);
        for (CampaignSystem.PersistentFleetEntry entry : fleet) {
            if (entry == null) continue;
            if (ships.size() < limit) {
                ships.add(CampaignSystem.displayPersistentFleetEntryName(entry) + " ("
                        + (entry.destroyed ? "lost" : CampaignSystem.repairStateLabel(entry)) + ")");
            }
            if (captains.size() < limit && entry.captainName != null && !entry.captainName.isBlank()) {
                captains.add(entry.captainName.trim());
            }
            int crew = CampaignSystem.shipCrewRequirement(entry.role);
            if (entry.destroyed) crewCasualties += Math.max(1, (int) Math.round(crew * 0.55));
            else if (entry.hullConditionFrac < 0.45) crewCasualties += Math.max(0, (int) Math.round(crew * 0.18));
            else if (entry.hullConditionFrac < 0.75) crewCasualties += Math.max(0, (int) Math.round(crew * 0.07));
            crewRescued += Math.max(0, entry.rescues);
        }
        ArrayList<String> out = new ArrayList<>();
        if (!captains.isEmpty()) out.add("Named Captains: " + String.join(", ", captains));
        if (!ships.isEmpty()) out.add("Named Ships: " + String.join(", ", ships));
        out.add("Crew Casualties: estimated " + crewCasualties);
        out.add("Crew Rescued: " + crewRescued + " recorded rescue actions");
        return out;
    }

    private static String campaignReportButtonFallbackLabel(String id) {
        if (id == null) return "FOLLOW-UP";
        return switch (id) {
            case "COLLECT_SALVAGE" -> "COLLECT SALVAGE";
            case "RESCUE_SURVIVORS" -> "RESCUE SURVIVORS";
            case "OPEN_REPAIRS" -> "OPEN REPAIRS";
            case "INSPECT_FLEET" -> "INSPECT FLEET";
            case "OPEN_CAPTAINS_LOG" -> "CAPTAIN'S LOG";
            case "OPEN_WAR_ROOM" -> "WAR ROOM";
            case "RETURN_TO_MAP" -> "RETURN TO MAP";
            case "CONTACT_SURVIVORS" -> "CONTACT SURVIVORS";
            case "REQUEST_GREEN_SUPPORT" -> "REQUEST GREEN SUPPORT";
            default -> id.replace('_', ' ');
        };
    }

    private static void recordAfterActionSnapshotIfNew(CampaignSystem.CampaignState st,
                                                       String location,
                                                       List<String> reportLines) {
        if (st == null || reportLines == null || reportLines.isEmpty()) return;
        String result = firstLineWithPrefix(reportLines, "Outcome: ");
        if (result.isBlank()) result = firstLineWithPrefix(reportLines, "Battle State: ");
        if (result.isBlank()) result = "Outcome pending";
        String resources = firstLineWithPrefix(reportLines, "Resources: ");
        String consequence = firstLineWithPrefix(reportLines, "Follow-On: ");
        if (consequence.isBlank()) consequence = firstLineWithPrefix(reportLines, "Campaign Impact: ");
        String losses = firstLineWithPrefix(reportLines, "Friendly Fleet: ");
        String title = "Battle Report: " + CampaignSystem.trimmedOrFallback(location, "Unknown Theater");
        String nextAction = campaignAfterActionNextAction(st);
        String why = campaignWhyThisMatters(st, consequence);
        CampaignSystem.AfterActionReport snapshot = new CampaignSystem.AfterActionReport(
                st.nextAfterActionReportId,
                title,
                location,
                result,
                losses,
                resources,
                consequence,
                nextAction,
                why,
                st.theaterWarTickIndex);
        if (!st.campaignAfterActionReports.isEmpty()) {
            CampaignSystem.AfterActionReport last = st.campaignAfterActionReports.get(st.campaignAfterActionReports.size() - 1);
            if (last != null && last.signature().equals(snapshot.signature())) return;
        }
        st.nextAfterActionReportId = Math.max(st.nextAfterActionReportId + 1, snapshot.id + 1);
        st.campaignAfterActionReports.add(snapshot);
        while (st.campaignAfterActionReports.size() > CampaignSystem.CAMPAIGN_AFTER_ACTION_HISTORY_CAP) {
            st.campaignAfterActionReports.remove(0);
        }
        CampaignSystem.addCampaignMemoryFlag(st, why);
        CampaignSystem.addCampaignLogEntry(st, "after-action", snapshot.title,
                campaignAfterActionLogDetail(st, snapshot), snapshot.consequence, true);
    }

    private static List<String> afterActionReportDisplayLines(CampaignSystem.AfterActionReport report) {
        if (report == null) return List.of("No after-action report selected.");
        ArrayList<String> out = new ArrayList<>();
        out.add(report.title + "  |  " + report.location);
        out.add("Result: " + report.result);
        out.add("Losses: " + report.losses);
        out.add("Resources: " + report.resources);
        out.add("Strategic Effect: " + report.consequence);
        out.add("Why This Matters: " + report.whyThisMatters);
        out.add("Next: " + report.nextAction);
        return out;
    }

    private static String firstLineWithPrefix(List<String> lines, String prefix) {
        if (lines == null || prefix == null) return "";
        for (String line : lines) {
            if (line != null && line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String campaignAfterActionNextAction(CampaignSystem.CampaignState st) {
        if (st == null) return "Return to campaign map";
        if (st.campaignSalvage > 0) return "Collect salvage, repair damaged hulls, then return to route planning";
        if (st.awaitingFleetHubChoice) return "Open fleet hub and repair or commission ships before the next route";
        if (st.galaxyEncounterActive || st.galaxyAmbientEncounterActive) return "Withdraw To Strategic Map when ready";
        return "Open War Room and choose the next route";
    }

    private static String campaignWhyThisMatters(CampaignSystem.CampaignState st, String consequence) {
        String detail = CampaignSystem.trimmedOrFallback(consequence, "");
        String lower = detail.toLowerCase(Locale.US);
        if (lower.contains("red") || lower.contains("hostile")) {
            return "Because hostile pressure changed, later intercepts and regional safety may shift.";
        }
        if (lower.contains("green")) {
            return "Because Green forces were affected, repairs, support, and regional control may change.";
        }
        if (lower.contains("yellow") || lower.contains("trade")) {
            return "Because Yellow trade trust changed, prices, routes, and neutral response may shift.";
        }
        if (lower.contains("route") || lower.contains("fuel") || lower.contains("supply")) {
            return "Because route logistics changed, the next travel leg may become safer or more expensive.";
        }
        if (st != null && st.fleetStrain >= 55.0) {
            return "Because fleet strain is high, repairs and resupply now matter before the next fight.";
        }
        return "Because this event is recorded, later reports, station reactions, and War Room advice can reference it.";
    }

    private static String campaignAfterActionLocationLabel(CampaignSystem.CampaignState st) {
        if (st == null) return "Unknown Theater";
        CampaignSystem.CampaignLocation selected = CampaignSystem.campaignLocationById(st, st.activeGalaxyEncounterLocationId);
        if (selected == null) selected = CampaignSystem.campaignLocationById(st, st.selectedGalaxyLocationId);
        if (selected == null) selected = CampaignSystem.campaignLocationById(st, st.dockedGalaxyLocationId);
        if (selected != null) return selected.name;
        String transition = st.transitionLabel == null ? "" : st.transitionLabel.trim();
        if (!transition.isBlank()) return transition;
        return "Sector " + Math.max(1, st.sector);
    }

    static String campaignAfterActionFleetDamageLabel(CampaignSystem.CampaignState st) {
        if (st == null) return "unknown";
        int live = 0;
        int damaged = 0;
        int critical = 0;
        int lost = 0;
        for (CampaignSystem.PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null) continue;
            if (entry.destroyed) {
                lost++;
                continue;
            }
            live++;
            double hull = MathUtil.clamp(entry.hullConditionFrac, 0.0, 1.0);
            double shield = MathUtil.clamp(entry.shieldConditionFrac, 0.0, 1.0);
            if (hull < 0.45 || shield < 0.35) critical++;
            if (hull < 0.72 || shield < 0.58) damaged++;
        }
        return "live " + live + "  damaged " + damaged + "  critical " + critical + "  lost " + lost;
    }
}
