import app.config.ExperienceSettings;

import java.util.ArrayList;
import java.util.List;

/** Read-only difficulty presentation and runtime-consumer audit helpers for Campaign Ops. */
public final class CampaignDifficultySystem {
    private CampaignDifficultySystem() {}

    public record RuntimeConsumer(String field, String consumer, String effect) {
        String line() {
            return field + " -> " + consumer + "  |  " + effect;
        }
    }

    public static List<String> telemetryLines(ExperienceSettings experience,
                                              int battles,
                                              int losses,
                                              int retreats,
                                              int resourceEmergencies) {
        String preset = experience == null || experience.preset == null
                ? ExperienceSettings.Preset.STANDARD.name()
                : experience.preset.name();
        ArrayList<String> out = new ArrayList<>();
        out.add("Difficulty " + preset + "  |  battles/reports " + Math.max(0, battles)
                + "  major losses " + Math.max(0, losses)
                + "  retreats " + Math.max(0, retreats)
                + "  resource emergencies " + Math.max(0, resourceEmergencies));
        out.add("Standard target: roughly one major loss, failure, or forced withdrawal per five battles, with hub recovery.");
        out.add("Collapse rule: one ordinary mistake remains recoverable; repeated losses plus unresolved shortages can end the campaign.");
        if (experience != null) out.addAll(experience.modifierSummaryLines());
        return out;
    }

    public static List<String> modifierLines(ExperienceSettings experience) {
        if (experience == null) return List.of("Difficulty modifiers unavailable.");
        return experience.modifierSummaryLines();
    }

    public static List<RuntimeConsumer> runtimeConsumers() {
        return List.of(
                new RuntimeConsumer("commandComplexity",
                        "GameContext constructor",
                        "low-complexity presets enable captain/helm/tactical/engineering automation by default"),
                new RuntimeConsumer("combatLethality",
                        "CollisionSystem damage resolution",
                        "scales resolved combat damage so presets change tactical lethality"),
                new RuntimeConsumer("strategicPressure",
                        "CampaignSystem encounter density and unresolved pressure aging",
                        "changes route interception thresholds and unresolved threat growth"),
                new RuntimeConsumer("attrition",
                        "CampaignSystem travel forecast and resource deduction",
                        "scales fuel, supplies, and ammo losses during strategic travel"),
                new RuntimeConsumer("tacticalOnly",
                        "MainMenuPanel and CampaignSystem route pressure",
                        "changes Campaign Ops structure by suppressing strategic pressure/attrition"),
                new RuntimeConsumer("commandOnly",
                        "ExperienceRuntime and GameplayActions",
                        "auto-resolves non-battle strategic prompts and blocks direct tactical deployment actions"),
                new RuntimeConsumer("ironCommand",
                        "CampaignSystem Iron Command tactical/campaign modifiers",
                        "applies enemy-only armor/reboot pressure, harsher attrition, and checkpoint-save rules")
        );
    }

    public static List<String> runtimeConsumerAuditLines() {
        ArrayList<String> out = new ArrayList<>();
        for (RuntimeConsumer consumer : runtimeConsumers()) {
            out.add(consumer.line());
        }
        return out;
    }
}
