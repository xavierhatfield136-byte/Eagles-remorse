import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based battle analysis. This intentionally stays deterministic so AARs
 * can be tested, saved, and compared without AI or network dependencies.
 */
public final class BattleAnalysisService {
    private BattleAnalysisService() {}

    public static AfterActionReport analyze(BattleResult result) {
        if (result == null) {
            return new AfterActionReport(
                    "After-Action Report",
                    "Unknown Theater",
                    "Outcome pending",
                    "No battle result was available.",
                    "No loss data recorded.",
                    "No resource data recorded.",
                    "No strategic effect recorded.",
                    List.of("The recorder did not receive a completed battle result."),
                    List.of(),
                    "Return to command");
        }

        ArrayList<BattleResult.AnalysisInsight> insights = new ArrayList<>();
        ArrayList<String> notable = new ArrayList<>();
        int friendlyStarted = result.friendlyShipsStarted();
        int friendlySurvived = result.friendlyShipsSurvived();
        int friendlyLost = result.friendlyShipsLost();
        int hostileStarted = result.hostileShipsStarted();
        int hostileDestroyed = result.hostileShipsDestroyed();
        int hostileSurvived = Math.max(0, hostileStarted - hostileDestroyed);

        if (result.tacticalResult == BattleResult.TacticalResult.VICTORY && friendlyLost == 0) {
            add(insights, "aar.clean_victory", BattleResult.AnalysisInsight.Category.PRIMARY_FACTOR, 95, 0.94,
                    "Clean victory: no friendly hulls were lost.",
                    "tacticalResult=VICTORY and friendlyLost=0");
        }
        if (result.tacticalResult == BattleResult.TacticalResult.WITHDRAWAL && friendlySurvived > 0) {
            add(insights, "aar.withdrawal_preserved_fleet", BattleResult.AnalysisInsight.Category.PRIMARY_FACTOR, 92, 0.92,
                    "Withdrawal preserved " + friendlySurvived + " friendly ship"
                            + plural(friendlySurvived) + " for the next operation.",
                    "tacticalResult=WITHDRAWAL and friendlySurvived=" + friendlySurvived);
        }
        if (friendlyLost > 0) {
            add(insights, "aar.friendly_losses", BattleResult.AnalysisInsight.Category.WARNING, 90, 0.98,
                    "Friendly losses: " + friendlyLost + " of " + Math.max(1, friendlyStarted)
                            + " starting ship" + plural(friendlyStarted) + " destroyed.",
                    "friendlyLost=" + friendlyLost + " friendlyStarted=" + friendlyStarted);
        }
        double avgHull = result.averageFriendlyHullFraction();
        if (avgHull > 0.0 && avgHull < 0.55) {
            add(insights, "aar.heavy_friendly_damage", BattleResult.AnalysisInsight.Category.WARNING, 82, 0.84,
                    "Surviving friendly hulls averaged " + percent(avgHull)
                            + " integrity; repairs should come before escalation.",
                    "averageFriendlyHullFraction=" + String.format(Locale.US, "%.3f", avgHull));
        }
        if (hostileDestroyed > 0) {
            add(insights, "aar.enemy_attrition", BattleResult.AnalysisInsight.Category.SECONDARY_FACTOR, 70, 0.88,
                    "Enemy attrition: " + hostileDestroyed + " hostile ship"
                            + plural(hostileDestroyed) + " destroyed.",
                    "hostileDestroyed=" + hostileDestroyed);
        }
        if (hostileSurvived > hostileDestroyed && hostileSurvived > 0
                && result.tacticalResult != BattleResult.TacticalResult.VICTORY) {
            add(insights, "aar.hostile_escape_pressure", BattleResult.AnalysisInsight.Category.WARNING, 76, 0.78,
                    "Hostile formation largely survived; expect follow-on pressure.",
                    "hostileSurvived=" + hostileSurvived + " hostileDestroyed=" + hostileDestroyed);
        }
        if (result.customShipsInBattle() > 0) {
            add(insights, "aar.custom_ship_participation", BattleResult.AnalysisInsight.Category.DETAIL, 30, 0.99,
                    "Custom content present: " + result.customShipsInBattle()
                            + " Team E/custom ship" + plural(result.customShipsInBattle()) + " participated.",
                    "customShipsInBattle=" + result.customShipsInBattle());
        }
        if (result.repairCostEstimate > result.missionRewardEarned
                && result.repairCostEstimate > 0
                && result.missionRewardEarned > 0) {
            add(insights, "aar.repair_cost_exceeds_reward", BattleResult.AnalysisInsight.Category.WARNING, 74, 0.8,
                    "Repair estimates exceeded the recorded mission reward.",
                    "repairCostEstimate=" + result.repairCostEstimate
                            + " missionRewardEarned=" + result.missionRewardEarned);
        }
        if (result.tacticalResult == BattleResult.TacticalResult.VICTORY && result.durationSeconds > 0.0
                && result.durationSeconds <= 180.0) {
            add(insights, "aar.fast_objective_completion", BattleResult.AnalysisInsight.Category.SECONDARY_FACTOR, 64, 0.72,
                    "Objective completed quickly before prolonged enemy escalation.",
                    "durationSeconds=" + String.format(Locale.US, "%.1f", result.durationSeconds));
        }
        if (insights.isEmpty()) {
            add(insights, "aar.no_dominant_signal", BattleResult.AnalysisInsight.Category.DETAIL, 10, 0.55,
                    "Battle concluded without a dominant loss, repair, or route-pressure signal.",
                    "No primary threshold fired.");
        }

        for (BattleResult.ShipSnapshot ship : result.mostDamagedFriendlyShips(3)) {
            notable.add(ship.name + " took " + Math.round(ship.damageTaken()) + " combined hull/shield damage.");
        }
        for (BattleResult.ShipSnapshot ship : result.ships) {
            if (ship == null || !ship.isFriendlyTo(result.playerFaction)) continue;
            if (ship.destroyed && ship.name != null && !ship.name.isBlank()) {
                notable.add(ship.name + " was lost and should be replaced before escalation.");
            }
            if (ship.destroyed && ship.role != null && (ship.role.isCapitalCombatant() || ship.role.isTitanOrMothership())) {
                notable.add("Capital loss: " + ship.name + " was destroyed.");
            }
            if (ship.academyTrainingShip && !ship.destroyed) {
                notable.add("Academy ship preserved: " + ship.name + ".");
            }
        }
        if (notable.isEmpty() && hostileDestroyed > 0) {
            notable.add("Hostile losses opened space for the next maneuver.");
        }
        insights.sort((a, b) -> {
            int byPriority = Integer.compare(b.priority, a.priority);
            if (byPriority != 0) return byPriority;
            return Double.compare(b.confidence, a.confidence);
        });
        List<BattleResult.AnalysisInsight> visibleInsights = capInsights(insights, 4);

        return new AfterActionReport(
                titleFor(result),
                result.location,
                result.resultLabel() + "  |  " + result.missionTitle + "  |  " + result.durationLabel(),
                forceSummary(result, friendlyStarted, friendlySurvived, hostileStarted, hostileDestroyed),
                lossesSummary(friendlySurvived, friendlyLost, result),
                resourcesSummary(result),
                strategicEffect(result, hostileSurvived, friendlyLost),
                visibleInsights,
                insightText(visibleInsights),
                cap(notable, 4),
                nextAction(result, friendlyLost, avgHull, hostileSurvived));
    }

    private static String titleFor(BattleResult result) {
        return switch (result.source) {
            case ACADEMY -> "Academy Debrief";
            case CAMPAIGN -> "Campaign After-Action Report";
            case CUSTOM_BATTLE -> "Custom Battle After-Action Report";
            case LAST_STAND -> "Last Stand Debrief";
            case RESOURCE_RUSH -> "Resource Rush Debrief";
            case FOUR_TEAM_DOMINATION -> "Domination Debrief";
            case MULTIPLAYER -> "Multiplayer Debrief";
            default -> "After-Action Report";
        };
    }

    private static String forceSummary(BattleResult result,
                                       int friendlyStarted,
                                       int friendlySurvived,
                                       int hostileStarted,
                                       int hostileDestroyed) {
        String enemies = result.enemyFactionLabels().isEmpty()
                ? "hostiles"
                : String.join("/", result.enemyFactionLabels());
        return "friendly " + friendlySurvived + "/" + Math.max(0, friendlyStarted)
                + " survived  |  " + enemies + " destroyed " + hostileDestroyed
                + "/" + Math.max(0, hostileStarted);
    }

    private static String lossesSummary(int friendlySurvived, int friendlyLost, BattleResult result) {
        return "live " + friendlySurvived
                + "  lost " + friendlyLost
                + "  damage taken " + Math.round(result.friendlyDamageTaken());
    }

    private static String resourcesSummary(BattleResult result) {
        int creditDelta = result.endCredits - result.startCredits;
        int oreDelta = result.endOre - result.startOre;
        return "credits " + result.endCredits + " (" + signed(creditDelta) + ")"
                + "  ore " + result.endOre + " (" + signed(oreDelta) + ")"
                + "  reward " + result.missionRewardEarned
                + "  repair est " + Math.max(result.repairCostEstimate, result.friendlyRepairEstimate())
                + "  replacement est " + Math.max(result.replacementCostEstimate, result.friendlyReplacementEstimate())
                + "  salvage pickups " + result.salvagePickupsRemaining;
    }

    private static String strategicEffect(BattleResult result, int hostileSurvived, int friendlyLost) {
        String suffix = result.campaignStateChanged ? " Campaign state changed." : "";
        return switch (result.tacticalResult) {
            case VICTORY -> hostileSurvived <= 0
                    ? "Enemy local combat power was broken." + suffix
                    : "Enemy objective pressure was reduced, but survivors remain in theater." + suffix;
            case DEFEAT -> "Friendly command lost the field and should expect heavier enemy initiative." + suffix;
            case WITHDRAWAL -> friendlyLost == 0
                    ? "Fleet preserved combat power by leaving the field." + suffix
                    : "Fleet escaped, but losses will constrain the next operation." + suffix;
            case DRAW -> "The field ended inconclusively; both sides may re-engage." + suffix;
            default -> "No completed strategic consequence was recorded.";
        };
    }

    private static String nextAction(BattleResult result, int friendlyLost, double avgHull, int hostileSurvived) {
        if (friendlyLost > 0 || (avgHull > 0.0 && avgHull < 0.65)) {
            return "Repair, replace losses, and review fleet composition before the next launch.";
        }
        if (result.tacticalResult == BattleResult.TacticalResult.DEFEAT
                || (result.tacticalResult == BattleResult.TacticalResult.WITHDRAWAL && hostileSurvived > 0)) {
            return "Avoid high-pressure routes, refit, and choose a lower-threat objective.";
        }
        if (result.tacticalResult == BattleResult.TacticalResult.WITHDRAWAL && hostileSurvived > 0) {
            return "Track escaped enemies on the map before choosing the next engagement.";
        }
        return switch (result.source) {
            case ACADEMY -> "Continue the academy lesson chain or return to the main menu.";
            case CAMPAIGN -> "Return to the map, collect salvage, and choose the next route.";
            case CUSTOM_BATTLE -> "Adjust roster balance or launch another custom engagement.";
            default -> "Return to command and choose the next objective.";
        };
    }

    private static void add(List<BattleResult.AnalysisInsight> insights,
                            String ruleId,
                            BattleResult.AnalysisInsight.Category category,
                            int priority,
                            double confidence,
                            String playerText,
                            String debugExplanation) {
        insights.add(new BattleResult.AnalysisInsight(ruleId, category, priority, confidence, playerText, debugExplanation));
    }

    private static List<String> insightText(List<BattleResult.AnalysisInsight> insights) {
        ArrayList<String> out = new ArrayList<>();
        if (insights != null) {
            for (BattleResult.AnalysisInsight insight : insights) {
                if (insight != null && insight.category != BattleResult.AnalysisInsight.Category.RECOMMENDED_NEXT_ACTION) {
                    out.add(insight.playerText);
                }
            }
        }
        return cap(out, 4);
    }

    private static List<BattleResult.AnalysisInsight> capInsights(List<BattleResult.AnalysisInsight> lines, int max) {
        int limit = Math.max(0, max);
        if (lines == null || lines.size() <= limit) return lines == null ? List.of() : List.copyOf(lines);
        return List.copyOf(lines.subList(0, limit));
    }

    private static List<String> cap(List<String> lines, int max) {
        int limit = Math.max(0, max);
        if (lines == null || lines.size() <= limit) return lines == null ? List.of() : List.copyOf(lines);
        return List.copyOf(lines.subList(0, limit));
    }

    private static String percent(double value) {
        return Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0) + "%";
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static String plural(int count) {
        return Math.abs(count) == 1 ? "" : "s";
    }
}
