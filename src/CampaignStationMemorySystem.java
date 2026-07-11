import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CampaignStationMemorySystem {
    private CampaignStationMemorySystem() {}

    static double stationPriceMultiplier(CampaignSystem.CampaignLocation location,
                                         CampaignSystem.HubService service) {
        if (location == null) return 1.0;
        double mul = 1.0;
        if ("damaged".equalsIgnoreCase(location.stationDamageState) || "degraded".equalsIgnoreCase(location.stationServiceState)) mul += 0.14;
        if ("offline".equalsIgnoreCase(location.stationServiceState)) mul += 0.28;
        if (hasMemory(location, "station saved") || hasMemory(location, "defended")) mul -= 0.10;
        if (hasMemory(location, "trade helped") && (service == CampaignSystem.HubService.TRADE || service == CampaignSystem.HubService.FUEL || service == CampaignSystem.HubService.SALVAGE)) mul -= 0.10;
        if (hasMemory(location, "yellow protected") && (service == CampaignSystem.HubService.TRADE || service == CampaignSystem.HubService.FUEL)) mul -= 0.08;
        if (hasMemory(location, "overused repairs") && (service == CampaignSystem.HubService.REPAIR || service == CampaignSystem.HubService.REFIT)) mul += 0.20;
        if (hasMemory(location, "lost supplies") && (service == CampaignSystem.HubService.SUPPLY || service == CampaignSystem.HubService.FUEL || service == CampaignSystem.HubService.STRIKE_REARM)) mul += 0.16;
        if (hasMemory(location, "yellow attacked") && (service == CampaignSystem.HubService.TRADE || service == CampaignSystem.HubService.FUEL || service == CampaignSystem.HubService.SALVAGE)) mul += 0.18;
        return MathUtil.clamp(mul, 0.70, 1.65);
    }

    static double stationSupportMultiplier(CampaignSystem.CampaignLocation location) {
        if (location == null) return 1.0;
        double mul = 1.0;
        if ("damaged".equalsIgnoreCase(location.stationDamageState) || "degraded".equalsIgnoreCase(location.stationServiceState)) mul -= 0.12;
        if ("offline".equalsIgnoreCase(location.stationServiceState)) mul -= 0.25;
        if (hasMemory(location, "station saved") || hasMemory(location, "defended")) mul += 0.10;
        if (hasMemory(location, "lost supplies")) mul -= 0.12;
        if (hasMemory(location, "overused repairs")) mul -= 0.10;
        return MathUtil.clamp(mul, 0.55, 1.35);
    }

    static double stationTradeMultiplier(CampaignSystem.CampaignLocation location) {
        if (location == null) return 1.0;
        double mul = 1.0;
        if (hasMemory(location, "trade helped") || hasMemory(location, "convoy saved")) mul += 0.10;
        if (hasMemory(location, "convoy lost") || hasMemory(location, "yellow attacked")) mul -= 0.14;
        return MathUtil.clamp(mul, 0.65, 1.35);
    }

    static double stationLogisticsMultiplier(CampaignSystem.CampaignLocation location) {
        if (location == null) return 1.0;
        double mul = 1.0;
        if (hasMemory(location, "station saved") || hasMemory(location, "convoy saved")) mul += 0.08;
        if (hasMemory(location, "lost supplies") || hasMemory(location, "convoy lost")) mul -= 0.12;
        return MathUtil.clamp(mul, 0.65, 1.30);
    }

    static List<String> reactionLines(CampaignSystem.CampaignState st,
                                      CampaignSystem.CampaignLocation focus,
                                      int maxLines) {
        ArrayList<String> out = new ArrayList<>();
        int limit = Math.max(1, maxLines);
        if (focus != null && !focus.stationMemoryFlags.isEmpty()) {
            out.add("Consequence: " + focus.name
                    + " price x" + String.format(Locale.US, "%.2f", stationPriceMultiplier(focus, null))
                    + " support x" + String.format(Locale.US, "%.2f", stationSupportMultiplier(focus))
                    + " trade x" + String.format(Locale.US, "%.2f", stationTradeMultiplier(focus))
                    + " logistics x" + String.format(Locale.US, "%.2f", stationLogisticsMultiplier(focus)));
            for (String memory : focus.stationMemoryFlags) {
                if (memory == null || memory.isBlank()) continue;
                out.add("Because " + memory.trim() + ", station services now react through local prices and support.");
                if (out.size() >= limit) return out;
            }
        }
        if (st != null && !st.campaignMemoryFlags.isEmpty()) {
            ArrayList<String> flags = new ArrayList<>(st.campaignMemoryFlags);
            for (int i = flags.size() - 1; i >= 0 && out.size() < limit; i--) {
                String flag = flags.get(i);
                if (flag == null || flag.isBlank()) continue;
                out.add("Because " + flag.trim() + ", the campaign memory ledger can feed station, faction, and production responses.");
            }
        }
        if (out.isEmpty()) out.add("Consequence: no saved campaign memory is currently affecting this location.");
        return out;
    }

    static boolean hasMemory(CampaignSystem.CampaignLocation location, String flag) {
        if (location == null || flag == null || flag.isBlank()) return false;
        String needle = flag.trim().toLowerCase(Locale.US);
        for (String memory : location.stationMemoryFlags) {
            if (memory != null && memory.trim().toLowerCase(Locale.US).contains(needle)) return true;
        }
        return false;
    }

    static void addMemoryFlag(CampaignSystem.CampaignLocation location, String flag) {
        if (location == null || flag == null || flag.isBlank()) return;
        String clean = flag.trim();
        location.stationMemoryFlags.remove(clean);
        location.stationMemoryFlags.add(clean);
        while (location.stationMemoryFlags.size() > 10) {
            String first = location.stationMemoryFlags.iterator().next();
            location.stationMemoryFlags.remove(first);
        }
    }
}
