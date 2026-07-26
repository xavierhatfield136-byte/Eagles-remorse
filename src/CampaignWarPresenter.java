import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

final class CampaignWarPresenter {
    private CampaignWarPresenter() {}

    static List<String> baselineTelemetryLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        out.add("TIME " + (int) Math.floor(Math.max(0.0, st.sectorElapsed)));
        for (Map.Entry<String, String> entry : CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx).entrySet()) {
            out.add("OWNER " + entry.getKey() + " " + entry.getValue());
        }
        st.campaignForces.stream()
                .filter(Objects::nonNull)
                .filter(force -> !force.destroyed && force.destinationLocationId != null
                        && !force.destinationLocationId.isBlank())
                .sorted(Comparator.comparingInt(force -> force.id))
                .forEach(force -> out.add("FORCE " + force.id + " "
                        + (force.faction == null ? "NONE" : force.faction.name()) + " "
                        + force.mission.name() + " " + force.destinationLocationId));
        for (StrategicCampaignExpansionSystem.StrategicOperation operation : st.strategicExpansion.operations) {
            if (operation == null) continue;
            out.add("OP " + operation.id + " " + operation.faction + " " + operation.type + " "
                    + operation.status + " " + operation.originTerritoryId + " " + operation.targetTerritoryId);
        }
        out.add("ROUTES " + CampaignSystem.campaignRouteSegments(ctx).size());
        return List.copyOf(out);
    }

    static List<String> battleWarningLines(GameContext ctx, int maxLines) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null) return List.of("BATTLE WARNING  |  unavailable");
        ArrayList<String> out = new ArrayList<>();
        for (CampaignSystem.CampaignBattle battle : st.campaignBattles) {
            if (battle == null || battle.resolved) continue;
            double distance = Math.hypot(battle.x - st.playerGalaxyX, battle.y - st.playerGalaxyY);
            double eta = distance / Math.max(1.0, st.galaxyTravel.speed > 0.0 ? st.galaxyTravel.speed : 120.0);
            double friendly = 0.0;
            double hostile = 0.0;
            for (Integer forceId : battle.participantForceIds) {
                CampaignSystem.CampaignForce force = CampaignSystem.campaignForceById(st, forceId == null ? 0 : forceId);
                if (force == null) continue;
                if (force.faction == Faction.ENEMY) hostile += CampaignSystem.campaignFleetCombatPower(force);
                else friendly += CampaignSystem.campaignFleetCombatPower(force);
            }
            out.add("BATTLE #" + battle.id
                    + "  |  " + CampaignSystem.theaterForPoint(st, battle.y).label
                    + "  |  engagement warning " + (int) Math.ceil(CampaignSystem.CAMPAIGN_BATTLE_WARNING_LEAD_SEC) + "s"
                    + "  |  coalition " + (int) Math.round(friendly)
                    + " vs hostile " + (int) Math.round(hostile)
                    + "  |  distance " + (int) Math.round(distance)
                    + " ETA " + (int) Math.ceil(eta) + "s"
                    + "  |  Follow Fleet / Join Battle / Ignore / Offer Support"
                    + "  |  participants " + battle.participantManifest);
            if (out.size() >= Math.max(1, maxLines)) break;
        }
        if (out.isEmpty()) out.add("BATTLE WARNING  |  no active major battle");
        return out;
    }
}
