import java.util.ArrayList;
import java.util.List;

/**
 * Display-ready report produced from a normalized BattleResult.
 */
public final class AfterActionReport {
    public final String title;
    public final String location;
    public final String resultLine;
    public final String forceSummary;
    public final String lossesSummary;
    public final String resourcesSummary;
    public final String strategicEffect;
    public final List<BattleResult.AnalysisInsight> analysisInsights;
    public final List<String> keyBattleFactors;
    public final List<String> notableActions;
    public final String nextAction;

    public AfterActionReport(String title,
                             String location,
                             String resultLine,
                             String forceSummary,
                             String lossesSummary,
                             String resourcesSummary,
                             String strategicEffect,
                             List<String> keyBattleFactors,
                             List<String> notableActions,
                             String nextAction) {
        this(title,
                location,
                resultLine,
                forceSummary,
                lossesSummary,
                resourcesSummary,
                strategicEffect,
                List.of(),
                keyBattleFactors,
                notableActions,
                nextAction);
    }

    public AfterActionReport(String title,
                             String location,
                             String resultLine,
                             String forceSummary,
                             String lossesSummary,
                             String resourcesSummary,
                             String strategicEffect,
                             List<BattleResult.AnalysisInsight> analysisInsights,
                             List<String> keyBattleFactors,
                             List<String> notableActions,
                             String nextAction) {
        this.title = BattleResult.trimmedOrFallback(title, "After-Action Report");
        this.location = BattleResult.trimmedOrFallback(location, "Unknown Theater");
        this.resultLine = BattleResult.trimmedOrFallback(resultLine, "Outcome pending");
        this.forceSummary = BattleResult.trimmedOrFallback(forceSummary, "Forces unavailable");
        this.lossesSummary = BattleResult.trimmedOrFallback(lossesSummary, "Losses unavailable");
        this.resourcesSummary = BattleResult.trimmedOrFallback(resourcesSummary, "Resources unavailable");
        this.strategicEffect = BattleResult.trimmedOrFallback(strategicEffect, "No strategic effect recorded");
        this.analysisInsights = List.copyOf(analysisInsights == null ? List.of() : analysisInsights);
        this.keyBattleFactors = List.copyOf(keyBattleFactors == null ? List.of() : keyBattleFactors);
        this.notableActions = List.copyOf(notableActions == null ? List.of() : notableActions);
        this.nextAction = BattleResult.trimmedOrFallback(nextAction, "Return to command");
    }

    public List<String> toDisplayLines() {
        ArrayList<String> out = new ArrayList<>();
        out.add(title + "  |  " + location);
        out.add("Outcome: " + resultLine);
        out.add("Forces: " + forceSummary);
        out.add("Friendly Fleet: " + lossesSummary);
        out.add("Resources: " + resourcesSummary);
        out.add("Strategic Effect: " + strategicEffect);
        for (String factor : keyBattleFactors) {
            if (factor != null && !factor.isBlank()) {
                out.add("Key Battle Factor: " + factor.trim());
            }
        }
        for (String action : notableActions) {
            if (action != null && !action.isBlank()) {
                out.add("Notable Action: " + action.trim());
            }
        }
        out.add("Next: " + nextAction);
        return out;
    }

    public String compactKeyFactors() {
        if (keyBattleFactors.isEmpty()) return "No key battle factors recorded.";
        return String.join("  |  ", keyBattleFactors);
    }
}
