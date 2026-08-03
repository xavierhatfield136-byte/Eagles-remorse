import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Immutable campaign-map read models prepared outside Renderer. */
public final class CampaignMapPresentationModel {
    public record SidebarContent(
            UiState.CampaignCommandTab tab,
            String primaryHeader,
            List<String> primaryLines,
            String secondaryHeader,
            List<String> secondaryLines) {
        public SidebarContent {
            primaryLines = List.copyOf(primaryLines == null ? List.of() : primaryLines);
            secondaryLines = List.copyOf(secondaryLines == null ? List.of() : secondaryLines);
        }
    }

    public record ResourceBoard(
            int fuel,
            int supplies,
            int ammo,
            int salvage,
            int ore,
            List<String> trendLines,
            List<String> warningLines) {
        public ResourceBoard {
            trendLines = List.copyOf(trendLines == null ? List.of() : trendLines);
            warningLines = List.copyOf(warningLines == null ? List.of() : warningLines);
        }
    }

    private CampaignMapPresentationModel() {}

    public static SidebarContent sidebar(GameContext ctx) {
        UiState.CampaignCommandTab tab = visibleTab(ctx);
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        return switch (tab) {
            case FLEET -> new SidebarContent(
                    tab, "FLEET", concise(CampaignSystem.campaignFleetManagerLines(ctx), 4),
                    "READY",
                    concise(joined(CampaignSystem.campaignFleetConditionLines(ctx),
                            CampaignSystem.campaignFleetDetachmentLines(ctx)), 4));
            case RESOURCES -> new SidebarContent(
                    tab, "SUPPLIES", concise(CampaignSystem.campaignResourceManagerLines(ctx), 4),
                    "RISKS",
                    concise(joined(CampaignSystem.campaignResourceWarningLines(ctx),
                            CampaignSystem.campaignResourceTrendLines(ctx)), 4));
            default -> new SidebarContent(
                    tab, "ROUTE", concise(CampaignSystem.campaignWarRoomLines(ctx), 4),
                    selected == null ? "SELECTED" : shortHeader(selected.name),
                    concise(CampaignSystem.selectedLocationSidebarLines(ctx), 5));
        };
    }

    public static ResourceBoard resources(GameContext ctx) {
        return new ResourceBoard(
                CampaignSystem.campaignFuel(ctx),
                CampaignSystem.campaignSupplies(ctx),
                CampaignSystem.campaignAmmo(ctx),
                CampaignSystem.campaignSalvageStock(ctx),
                CampaignSystem.currentCampaignOre(ctx),
                CampaignSystem.campaignResourceTrendLines(ctx),
                CampaignSystem.campaignResourceWarningLines(ctx));
    }

    private static UiState.CampaignCommandTab visibleTab(GameContext ctx) {
        UiState.CampaignCommandTab tab = ctx == null || ctx.ui == null
                ? UiState.CampaignCommandTab.NAV
                : ctx.ui.campaignCommandTab;
        return (tab == UiState.CampaignCommandTab.STRIKES || tab == UiState.CampaignCommandTab.RESOURCES)
                ? UiState.CampaignCommandTab.NAV
                : tab;
    }

    private static List<String> joined(List<String> first, List<String> second) {
        ArrayList<String> out = new ArrayList<>();
        if (first != null) out.addAll(first);
        if (second != null) out.addAll(second);
        return List.copyOf(out);
    }

    private static List<String> concise(List<String> lines, int max) {
        if (lines == null || lines.isEmpty() || max <= 0) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String line : lines) {
            String simplified = simplifyLine(line);
            if (simplified.isBlank()) continue;
            if (!out.contains(simplified)) out.add(simplified);
            if (out.size() >= max) break;
        }
        return List.copyOf(out);
    }

    private static String simplifyLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        if (s.isBlank()) return "";
        if (s.startsWith("Fleet orders here affect")) return "";
        if (s.startsWith("Overmap Berths:")) return "";
        if (s.startsWith("Flagship Capability:")) return "";
        if (s.startsWith("Expansion Ledger:")) return "";
        if (s.startsWith("Operational State:")) return "";
        if (s.startsWith("Recovery Resources:")) return "";
        if (s.startsWith("Primary Recommendation:")) return "Recommended: " + valueAfterColon(s);
        if (s.startsWith("Facility:")) return compactFacility(s);
        if (s.startsWith("Defense:")) return compactDefense(s);
        if (s.startsWith("Services:")) return compactServices(s);
        if (s.startsWith("Alignment:")) return compactLabel(s, "Alignment");
        if (s.startsWith("Threat:")) return compactLabel(s, "Threat");
        if (s.startsWith("Docking:")) return compactLabel(s, "Dock");
        if (s.startsWith("Travel Result:")) return compactLabel(s, "Travel");
        if (s.startsWith("Region Note:")) return compactLabel(s, "Region");
        if (s.startsWith("Selected")) return s.replace("  |  ", " | ");
        if (s.startsWith("Credits:")) return s.replace("  |  ", " | ");
        if (s.startsWith("Strike Cooldowns:")) return s.replace("Strike Cooldowns:", "Strikes:");
        if (s.startsWith("Pressure:")) return s.replace("  |  ", " | ");
        if (s.startsWith("Command Hulls:")) return s.replace("Command Hulls:", "Ships:").replace("  |  ", " | ");
        if (s.startsWith("Next Tactical Entry:")) return s.replace("Next Tactical Entry:", "Next fight:");
        if (s.startsWith("Force Mix:")) return s.replace("Force Mix:", "Mix:").replace("  |  ", " | ");
        if (s.startsWith("Commitments:")) return s.replace("  |  ", " | ");
        if (s.startsWith("Average Hull Condition:")) return s.replace("Average Hull Condition:", "Avg hull:").replace("  |  ", " | ");
        return trimSentence(s.replace("  |  ", " | "), 82);
    }

    private static String compactFacility(String s) {
        String facility = valueAfterColon(s);
        int owner = facility.indexOf("Owner ");
        int value = facility.indexOf("Value ");
        if (owner >= 0 && value > owner) {
            return "Site: " + facility.substring(0, owner).replace("|", "").trim()
                    + " | " + facility.substring(owner, value).replace("|", "").trim();
        }
        return "Site: " + trimSentence(facility, 64);
    }

    private static String compactDefense(String s) {
        String v = valueAfterColon(s);
        int resources = v.indexOf("Resources ");
        int alert = v.indexOf("Alert ");
        String defense = resources >= 0 ? v.substring(0, resources).replace("|", "").trim() : v;
        String alertText = alert >= 0 ? v.substring(alert).replace("|", "").trim() : "";
        return trimSentence("Defense: " + defense + (alertText.isBlank() ? "" : " | " + alertText), 74);
    }

    private static String compactServices(String s) {
        String v = valueAfterColon(s);
        int hooks = v.indexOf("Hooks ");
        String services = hooks >= 0 ? v.substring(0, hooks).replace("|", "").trim() : v;
        return trimSentence("Services: " + services, 74);
    }

    private static String compactLabel(String s, String label) {
        return trimSentence(label + ": " + valueAfterColon(s).replace("  |  ", " | "), 78);
    }

    private static String valueAfterColon(String s) {
        int idx = s.indexOf(':');
        return idx >= 0 ? s.substring(idx + 1).trim() : s.trim();
    }

    private static String trimSentence(String s, int maxChars) {
        if (s == null) return "";
        String clean = s.trim();
        if (clean.length() <= maxChars) return clean;
        return clean.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private static String shortHeader(String name) {
        if (name == null || name.isBlank()) return "SELECTED";
        return trimSentence(name.trim().toUpperCase(Locale.US), 28);
    }
}
