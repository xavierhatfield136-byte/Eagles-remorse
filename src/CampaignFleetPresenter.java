import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CampaignFleetPresenter {
    private CampaignFleetPresenter() {}

    static List<String> boardSummaryLines(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null) return List.of("No fleet data.");
        int ready = 0;
        int strained = 0;
        int support = 0;
        int escorts = 0;
        int capitals = 0;
        int detached = 0;
        int recovered = 0;
        for (CampaignSystem.PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            double hull = MathUtil.clamp(entry.hullConditionFrac, 0.0, 1.0);
            double shield = MathUtil.clamp(entry.shieldConditionFrac, 0.0, 1.0);
            if (hull >= 0.86 && shield >= 0.80) ready++;
            if (hull < 0.70 || shield < 0.62) strained++;
            if (entry.commandGroupId != CampaignSystem.CAMPAIGN_FLAGSHIP_COMMAND_GROUP) detached++;
            if (entry.role == ShipRole.HAULER || entry.role == ShipRole.MINER || entry.role == ShipRole.TRANSPORT
                    || entry.role == ShipRole.TRANSPORT_TITAN || entry.role == ShipRole.MOBILE_STATION_TITAN) {
                support++;
            } else if (entry.role == ShipRole.BATTLESHIP || entry.role == ShipRole.DREADNOUGHT
                    || entry.role == ShipRole.SUPERSHIP || entry.role == ShipRole.BATTLECRUISER
                    || entry.role == ShipRole.BULWARK_TITAN || entry.role == ShipRole.ARTILLERY_TITAN) {
                capitals++;
            } else {
                escorts++;
            }
            String name = (entry.name == null) ? "" : entry.name.toUpperCase(Locale.US);
            if (name.contains("RECOVERED") || name.contains("RELAY") || name.contains("DISTRESS") || name.contains("CRADLE")) {
                recovered++;
            }
        }
        return List.of(
                "READY " + ready + "  |  STRAINED " + strained,
                "ESCORTS " + escorts + "  |  SUPPORT " + support + "  |  CAPITALS " + capitals,
                "DETACHED " + detached + "  |  RECOVERED " + recovered
        );
    }

    static List<String> detachmentLines(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null) return List.of("No detachment data.");
        LinkedHashMap<Integer, Integer> groups = new LinkedHashMap<>();
        for (CampaignSystem.PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            groups.merge(entry.commandGroupId, 1, Integer::sum);
        }
        ArrayList<String> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : groups.entrySet()) {
            int groupId = entry.getKey();
            int count = entry.getValue();
            String label = (groupId == CampaignSystem.CAMPAIGN_FLAGSHIP_COMMAND_GROUP) ? "FLAG GROUP" : ("DETACHMENT " + groupId);
            out.add(label + "  |  " + count + " HULLS");
            if (out.size() >= 4) break;
        }
        return out;
    }

    static List<String> flagshipSchematicLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null) return List.of("FLAGSHIP SCHEMATIC  |  unavailable");
        if (ctx.player != null) FlagshipOperationsSystem.syncFromShip(st.flagshipOperations, ctx.player);
        ArrayList<String> out = new ArrayList<>();
        out.add("SCHEMATIC  |  zoom " + String.format(Locale.US, "%.2f", st.flagshipOperations.schematicZoom)
                + "  selected " + (st.flagshipOperations.selectedCompartmentId.isBlank()
                ? "none" : st.flagshipOperations.selectedCompartmentId)
                + "  automation " + st.flagshipOperations.automation);
        out.addAll(FlagshipOperationsSystem.schematicLines(st.flagshipOperations));
        for (FlagshipOperationsSystem.CriticalWarning warning
                : FlagshipOperationsSystem.criticalWarnings(st.flagshipOperations)) {
            out.add("WARNING P" + warning.priority() + "  |  " + warning.icon() + " / " + warning.pattern()
                    + "  |  " + warning.text() + "  |  audio " + warning.optionalAudioCue());
        }
        return List.copyOf(out);
    }
}
