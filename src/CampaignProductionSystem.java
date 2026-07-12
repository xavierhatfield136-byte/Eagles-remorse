import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class CampaignProductionSystem {
    private CampaignProductionSystem() {}

    static List<CampaignSystem.CampaignYardOrder> yardOrders(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        return (st == null || st.campaignYardOrders.isEmpty()) ? List.of() : List.copyOf(st.campaignYardOrders);
    }

    static boolean queueConstructionOrder(GameContext ctx,
                                          CampaignSystem.CampaignState st,
                                          CampaignSystem.CampaignLocation location,
                                          ShipRole role,
                                          int creditCost,
                                          int oreCost,
                                          int salvageCost) {
        if (ctx == null || st == null || location == null || role == null) return false;
        CampaignSystem.CampaignYardOrder order = new CampaignSystem.CampaignYardOrder(
                st.nextCampaignYardOrderId++,
                CampaignSystem.CampaignYardOrderKind.CONSTRUCTION,
                role,
                location.ownerFaction,
                0,
                location.id,
                location.name,
                "New Hull",
                creditCost,
                oreCost,
                salvageCost,
                CampaignSystem.campaignLaneBaseSeconds(role));
        st.campaignYardOrders.add(order);
        CampaignSystem.recordStructuredCampaignEvent(st, "campaign.production.start",
                "orderId=" + order.id
                        + " kind=" + order.kind
                        + " role=" + order.role
                        + " source=" + CampaignSystem.safeTelemetryValue(order.sourceLocationId)
                        + " reason=player_shipyard_purchase");
        return true;
    }

    static boolean queueRefitOrder(GameContext ctx,
                                   CampaignSystem.CampaignState st,
                                   CampaignSystem.CampaignLocation location,
                                   CampaignSystem.HubProfile profile,
                                   int creditCost,
                                   int salvageCost) {
        if (ctx == null || st == null || location == null || profile == null) return false;
        CampaignSystem.PersistentFleetEntry focused = CampaignSystem.campaignFleetFocusEntry(ctx);
        ShipRole role = (focused == null || focused.role == null) ? ShipRole.FRIGATE : focused.role;
        FleetBuildingSystem.RefitTemplate template = FleetBuildingSystem.standardLoadouts().get(
                Math.floorMod((focused == null) ? 0 : focused.slotId - 1, FleetBuildingSystem.standardLoadouts().size()));
        FleetBuildingSystem.RefitAssessment assessment =
                FleetBuildingSystem.assessRefit(role, template, false, "Blue", CampaignSystem.selectedHubAlignmentLabelForProfile(profile));
        CampaignSystem.CampaignYardOrder order = new CampaignSystem.CampaignYardOrder(
                st.nextCampaignYardOrderId++,
                CampaignSystem.CampaignYardOrderKind.REFIT,
                role,
                location.ownerFaction,
                (focused == null) ? 0 : focused.slotId,
                location.id,
                location.name,
                template.name,
                creditCost,
                0,
                salvageCost,
                12.0 + assessment.refitDays * 4.0);
        st.campaignYardOrders.add(order);
        CampaignSystem.recordStructuredCampaignEvent(st, "campaign.production.start",
                "orderId=" + order.id
                        + " kind=" + order.kind
                        + " role=" + order.role
                        + " source=" + CampaignSystem.safeTelemetryValue(order.sourceLocationId)
                        + " reason=player_refit_order");
        return true;
    }

    static void advanceYardOrders(GameContext ctx, CampaignSystem.CampaignState st, double dt) {
        if (ctx == null || st == null || dt <= 0.0 || st.campaignYardOrders.isEmpty()) return;
        ArrayList<CampaignSystem.CampaignYardOrder> completed = new ArrayList<>();
        ArrayList<CampaignSystem.CampaignYardOrder> canceled = new ArrayList<>();
        HashSet<String> activeLanes = new HashSet<>();
        for (CampaignSystem.CampaignYardOrder order : st.campaignYardOrders) {
            if (order == null) continue;
            CampaignSystem.CampaignLocation yard = CampaignSystem.campaignLocationById(st, order.sourceLocationId);
            if (yard == null || yard.destroyed || "destroyed".equalsIgnoreCase(yard.stationDamageState)) {
                CampaignSystem.refundCanceledCampaignYardOrder(ctx, st, order, 0.50, "producing shipyard destroyed");
                canceled.add(order);
                continue;
            }
            if (yard.ownerFaction != null && yard.ownerFaction != order.producingFaction) {
                continue;
            }
            if ("offline".equalsIgnoreCase(yard.stationServiceState)
                    || CampaignStationMemorySystem.hasMemory(yard, "blockaded")
                    || CampaignStationMemorySystem.hasMemory(yard, "under blockade")) {
                continue;
            }
            String laneKey = order.sourceLocationId + "|" + order.lane;
            if (!activeLanes.add(laneKey)) continue;
            double throughput = "damaged".equalsIgnoreCase(yard.stationDamageState)
                    || "degraded".equalsIgnoreCase(yard.stationServiceState) ? 0.50 : 1.0;
            throughput *= yard.strategicConstructionMultiplier;
            order.remainingSeconds = Math.max(0.0, order.remainingSeconds - dt * throughput);
            if (order.remainingSeconds <= 0.0) completed.add(order);
        }
        for (CampaignSystem.CampaignYardOrder order : completed) {
            CampaignSystem.completeCampaignYardOrder(ctx, st, order);
        }
        st.campaignYardOrders.removeAll(completed);
        st.campaignYardOrders.removeAll(canceled);
    }

    static void refundCanceledYardOrder(GameContext ctx,
                                        CampaignSystem.CampaignState st,
                                        CampaignSystem.CampaignYardOrder order,
                                        double fraction,
                                        String reason) {
        if (ctx == null || st == null || order == null) return;
        double refund = MathUtil.clamp(fraction, 0.0, 1.0);
        ctx.credits += (int) Math.round(order.creditCost * refund);
        CampaignSystem.setCampaignOre(ctx, st, CampaignSystem.currentCampaignOre(ctx) + (int) Math.round(order.oreCost * refund));
        st.campaignSalvage += (int) Math.round(order.salvageCost * refund);
        CampaignSystem.recordStructuredCampaignEvent(st, "campaign.production.stop",
                "orderId=" + order.id
                        + " kind=" + order.kind
                        + " role=" + order.role
                        + " reason=" + CampaignSystem.safeTelemetryValue(reason)
                        + " refund=" + Math.round(refund * 100.0));
    }

    static void completeYardOrder(GameContext ctx,
                                  CampaignSystem.CampaignState st,
                                  CampaignSystem.CampaignYardOrder order) {
        if (ctx == null || st == null || order == null) return;
        if (order.kind == CampaignSystem.CampaignYardOrderKind.CONSTRUCTION) {
            CampaignSystem.PersistentFleetEntry predecessor = CampaignSystem.latestDestroyedPersistentFleetEntry(st, order.role);
            String buildName = (predecessor == null)
                    ? order.sourceLabel + " Yard Build"
                    : CampaignSystem.basePersistentFleetName(predecessor.name) + " II";
            CampaignSystem.CampaignLocation producingYard = CampaignSystem.campaignLocationById(st, order.sourceLocationId);
            Faction producingFaction = producingYard == null || producingYard.ownerFaction == null
                    || producingYard.ownerFaction == Faction.ENEMY
                    ? Faction.ALLY
                    : producingYard.ownerFaction;
            CampaignSystem.PersistentFleetEntry built = CampaignSystem.addPersistentFleetEntry(st, order.role, buildName,
                    CampaignSystem.CAMPAIGN_FLAGSHIP_COMMAND_GROUP, producingFaction);
            if (built != null) {
                built.hullConditionFrac = 1.0;
                built.shieldConditionFrac = 1.0;
                CampaignSystem.appendPersistentServiceHistory(built, "COMMISSIONED AT " + order.sourceLabel);
                if (predecessor != null) {
                    built.crewExperience = predecessor.crewExperience / 4;
                    CampaignSystem.appendPersistentServiceHistory(built, "SUCCESSOR TO SLOT " + predecessor.slotId);
                    CampaignSystem.appendPersistentServiceHistory(predecessor, "SUCCESSOR COMMISSIONED AS SLOT " + built.slotId);
                }
                CampaignSystem.markPlayerPurchasedEntryCommitted(built);
                CampaignSystem.spawnPurchasedPersistentBlueShip(ctx, st, built);
            }
            EventSystem.showBanner(ctx, ("YARD DELIVERY: " + order.role).toUpperCase(Locale.US), 1.6);
        } else {
            CampaignSystem.PersistentFleetEntry target = CampaignSystem.persistentFleetEntryBySlotId(st, order.fleetSlotId);
            if (target != null && !target.destroyed) {
                target.refitTemplateName = order.templateName;
                target.hullConditionFrac = Math.min(1.0, target.hullConditionFrac + 0.22);
                target.shieldConditionFrac = Math.min(1.0, target.shieldConditionFrac + 0.26);
                CampaignSystem.appendPersistentServiceHistory(target, "REFIT " + order.templateName + " COMPLETED");
            } else {
                CampaignSystem.refitPersistentFleet(ctx, st,
                        CampaignSystem.hubProfile(ctx, CampaignSystem.campaignLocationById(st, order.sourceLocationId)));
            }
            CampaignSystem.syncPersistentFleetEntrySnapshots(ctx, st);
            EventSystem.showBanner(ctx, ("REFIT COMPLETE: " + order.templateName).toUpperCase(Locale.US), 1.5);
        }
        CampaignSystem.recordStructuredCampaignEvent(st, "campaign.production.stop",
                "orderId=" + order.id
                        + " kind=" + order.kind
                        + " role=" + order.role
                        + " reason=completed");
        CampaignSystem.rebalancePersistentCommandGroups(st);
        CampaignSystem.applyCampaignFleetBonuses(ctx, st);
    }
}
