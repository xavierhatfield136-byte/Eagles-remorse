import app.persistence.CampaignCheckpointStore;
import app.persistence.CampaignSaveContract;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class CampaignIntegritySystem {
    private CampaignIntegritySystem() {}

    static CampaignSystem.CampaignIntegrityReport validateCampaignIntegrity(GameContext ctx,
                                                                            CampaignSystem.CampaignState st) {
        ArrayList<String> validators = new ArrayList<>();
        ArrayList<String> failures = new ArrayList<>();
        if (ctx == null || st == null) {
            return new CampaignSystem.CampaignIntegrityReport(List.of(), List.of("campaign: unavailable"));
        }
        validateOrderOfBattle(st, validators, failures);
        validateFleetProvenance(st, validators, failures);
        validateEconomyConservation(ctx, st, validators, failures);
        validateProductionQueues(st, validators, failures);
        validateTerritoryOwnership(st, validators, failures);
        validateMissionBriefings(st, validators, failures);
        validateContacts(st, validators, failures);
        validateStrikeOrigins(st, validators, failures);
        validateSaveMigration(ctx, st, validators, failures);
        return new CampaignSystem.CampaignIntegrityReport(validators, failures);
    }

    static List<String> campaignIntegrityDiagnosticLines(GameContext ctx, CampaignSystem.CampaignState st) {
        CampaignSystem.CampaignIntegrityReport report = validateCampaignIntegrity(ctx, st);
        ArrayList<String> out = new ArrayList<>();
        out.add("CAMPAIGN INTEGRITY  " + (report.healthy() ? "PASS" : "FAIL")
                + "  validators=" + report.validators.size()
                + "  failures=" + report.failures.size());
        for (String failure : report.failures) {
            out.add(" - " + failure);
            if (out.size() >= 6) break;
        }
        return out;
    }

    static List<String> campaignAuthoritativeOwnershipLines() {
        return List.of(
                "Fleets: CampaignState.campaignForces + campaignShipPool; tactical ships are encounter projections.",
                "Inventory: CampaignState resources, persistentBlueFleet, campaignShipPool, and facility stockpiles.",
                "Economy: CampaignState live stores; economyExpansion stores are forecast/telemetry projections.",
                "Territory: CampaignLocation.ownerFaction/controlState; strategic nodes are theater summaries.",
                "Production: campaignYardOrders and campaignBaseQueues; counters/catalogs are projections.",
                "Missions: live objective fields and mission sections; mission boards are presentation projections."
        );
    }

    static ArrayList<String> orderOfBattleAuditProblems(CampaignSystem.CampaignState st, int unassigned) {
        ArrayList<String> problems = new ArrayList<>();
        HashSet<Integer> ids = new HashSet<>();
        HashSet<String> names = new HashSet<>();
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null) continue;
            if (!ids.add(record.id)) problems.add("duplicate ship ID " + record.id);
            String normalized = CampaignSystem.trimmedOrFallback(record.name, "").toLowerCase(Locale.US);
            if (!normalized.isBlank() && !names.add(normalized)) problems.add("duplicate persistent ship name " + record.name);
            if (record.faction == null) problems.add("ship " + record.id + " has no faction");
            if (record.status == CampaignSystem.CampaignShipPoolStatus.ACTIVE
                    && CampaignSystem.campaignForceById(st, record.forceId) == null) {
                problems.add("active ship " + record.id + " has no owner force");
            }
        }
        for (CampaignSystem.PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null) continue;
            String normalized = CampaignSystem.displayPersistentFleetEntryName(entry).toLowerCase(Locale.US);
            if (!names.add(normalized)) {
                problems.add("duplicate persistent ship name " + CampaignSystem.displayPersistentFleetEntryName(entry));
            }
        }
        if (unassigned > 0) problems.add(unassigned + " inventory records have no force, base, or queue");
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force == null || force.destroyed) continue;
            if (force.faction == null) problems.add("force " + force.id + " has no faction");
            if (force.origin == null || force.origin.isBlank()) problems.add("force " + force.id + " has no origin");
            if (force.mission == null || force.purpose == null || force.purpose.isBlank()) {
                problems.add("force " + force.id + " has no mission");
            }
            if (force.kind != CampaignSystem.CampaignForceKind.PLAYER_FLEET
                    && force.faction != Faction.PLAYER
                    && force.faction != Faction.ALLY
                    && force.strength >= 45.0
                    && !CampaignSystem.campaignForceHasInventoryProvenanceOrClaim(st, force)) {
                problems.add("major force " + force.id + " has no inventory provenance");
            }
        }
        return problems;
    }

    private static void validateOrderOfBattle(CampaignSystem.CampaignState st,
                                              List<String> validators,
                                              List<String> failures) {
        validators.add("order-of-battle");
        int unassigned = 0;
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null || record.status == CampaignSystem.CampaignShipPoolStatus.DESTROYED) continue;
            boolean queueOwned = record.status == CampaignSystem.CampaignShipPoolStatus.UNDER_CONSTRUCTION
                    && st.campaignBaseQueues.stream().anyMatch(queue -> queue != null && queue.shipRecordId == record.id);
            if (record.forceId <= 0 && record.baseId.isBlank() && !queueOwned) unassigned++;
        }
        for (String problem : orderOfBattleAuditProblems(st, unassigned)) {
            failures.add("order-of-battle: " + problem);
        }
    }

    private static void validateFleetProvenance(CampaignSystem.CampaignState st,
                                                List<String> validators,
                                                List<String> failures) {
        validators.add("fleet-provenance");
        HashMap<Integer, Integer> runtimeOwners = new HashMap<>();
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null || record.status == CampaignSystem.CampaignShipPoolStatus.DESTROYED || record.forceId <= 0) continue;
            CampaignSystem.CampaignForce owner = CampaignSystem.campaignForceById(st, record.forceId);
            if (owner == null || owner.destroyed) {
                failures.add("fleet-provenance: ship record " + record.id + " points at missing force " + record.forceId);
            } else if (owner.faction != record.faction) {
                failures.add("fleet-provenance: ship record " + record.id + " faction differs from force " + owner.id);
            }
        }
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force == null || force.destroyed) continue;
            for (Integer shipId : force.shipIds) {
                if (shipId == null || shipId <= 0) continue;
                Integer prior = runtimeOwners.put(shipId, force.id);
                if (prior != null && prior != force.id) {
                    failures.add("fleet-provenance: tactical ship " + shipId + " belongs to forces " + prior + " and " + force.id);
                }
                Integer indexed = st.shipCampaignForceIds.get(shipId);
                if (indexed != null && indexed != force.id) {
                    failures.add("fleet-provenance: tactical ship index " + shipId + " points at " + indexed + " instead of " + force.id);
                }
            }
        }
    }

    private static void validateEconomyConservation(GameContext ctx,
                                                    CampaignSystem.CampaignState st,
                                                    List<String> validators,
                                                    List<String> failures) {
        validators.add("economy-conservation");
        if (st.oreLedger.storedOre < 0) failures.add("economy-conservation: negative campaign ore");
        if (st.campaignFuel < 0) failures.add("economy-conservation: negative fuel");
        if (st.campaignSupplies < 0) failures.add("economy-conservation: negative supplies");
        if (st.campaignAmmo < 0) failures.add("economy-conservation: negative ammunition");
        if (st.campaignSalvage < 0) failures.add("economy-conservation: negative salvage");
        if (ctx.credits < 0) failures.add("economy-conservation: negative credits");
        for (CampaignSystem.CampaignLocation location : CampaignSystem.allCampaignLocations(st)) {
            if (location.oreStockpile < 0 || location.repairSupplyStockpile < 0
                    || location.ammunitionStockpile < 0 || location.fuelStockpile < 0) {
                failures.add("economy-conservation: negative facility stockpile at " + location.id);
            }
        }
    }

    private static void validateProductionQueues(CampaignSystem.CampaignState st,
                                                 List<String> validators,
                                                 List<String> failures) {
        validators.add("production-queue");
        HashSet<Integer> queueIds = new HashSet<>();
        HashSet<Integer> queuedShipIds = new HashSet<>();
        for (CampaignSystem.CampaignBaseQueueEntry queue : st.campaignBaseQueues) {
            if (queue == null) {
                failures.add("production-queue: null base queue entry");
                continue;
            }
            if (!queueIds.add(queue.id)) failures.add("production-queue: duplicate base queue id " + queue.id);
            if (queue.baseId.isBlank() || CampaignSystem.campaignLocationById(st, queue.baseId) == null) {
                failures.add("production-queue: queue " + queue.id + " has invalid base " + queue.baseId);
            }
            if (queue.remainingSec < 0.0 || queue.oreCost < 0 || queue.repairSupplyCost < 0) {
                failures.add("production-queue: queue " + queue.id + " has invalid cost or duration");
            }
            if (queue.shipRecordId > 0 && !queuedShipIds.add(queue.shipRecordId)) {
                failures.add("production-queue: ship record " + queue.shipRecordId + " appears in multiple queues");
            }
        }
        HashSet<Integer> yardIds = new HashSet<>();
        for (CampaignSystem.CampaignYardOrder order : st.campaignYardOrders) {
            if (order == null) {
                failures.add("production-queue: null player yard order");
                continue;
            }
            if (!yardIds.add(order.id)) failures.add("production-queue: duplicate yard order id " + order.id);
            if (order.sourceLocationId.isBlank()
                    || CampaignSystem.campaignLocationById(st, order.sourceLocationId) == null) {
                failures.add("production-queue: yard order " + order.id + " has invalid source");
            }
            if (order.remainingSeconds < 0.0 || order.remainingSeconds > order.totalSeconds + 0.001) {
                failures.add("production-queue: yard order " + order.id + " has invalid remaining time");
            }
        }
    }

    private static void validateTerritoryOwnership(CampaignSystem.CampaignState st,
                                                   List<String> validators,
                                                   List<String> failures) {
        validators.add("territory-ownership");
        HashSet<String> locationIds = new HashSet<>();
        for (CampaignSystem.CampaignLocation location : CampaignSystem.allCampaignLocations(st)) {
            if (!locationIds.add(location.id)) failures.add("territory-ownership: duplicate location id " + location.id);
            if (location.ownerFaction == null) failures.add("territory-ownership: " + location.id + " has no owner faction");
            if (location.controlState == null) failures.add("territory-ownership: " + location.id + " has no control state");
        }
        for (CampaignSystem.StrategicNodeState node : st.strategicNodes) {
            if (node == null || node.locationId == null || node.locationId.isBlank()) {
                failures.add("territory-ownership: strategic node has no location");
            } else if (CampaignSystem.campaignLocationById(st, node.locationId) == null) {
                failures.add("territory-ownership: strategic node references missing location " + node.locationId);
            }
            if (node != null && node.owner == null) failures.add("territory-ownership: strategic node has no owner");
        }
    }

    private static void validateMissionBriefings(CampaignSystem.CampaignState st,
                                                 List<String> validators,
                                                 List<String> failures) {
        validators.add("mission-briefing-completeness");
        if (!CampaignSystem.isStrategicOvermapMode(st) && st.enabled) {
            if (st.objectiveLabel == null || st.objectiveLabel.isBlank()) {
                failures.add("mission-briefing-completeness: active tactical mission has no objective label");
            }
            if (st.missionSections.isEmpty()) {
                failures.add("mission-briefing-completeness: active tactical mission has no mission sections");
            }
        }
        for (CampaignSystem.MissionSection section : st.missionSections) {
            if (section == null || section.label.isBlank() || !Double.isFinite(section.x)
                    || !Double.isFinite(section.y) || section.radius <= 0.0) {
                failures.add("mission-briefing-completeness: invalid mission section");
            }
        }
        if (st.galaxyEncounterActive && (st.activeGalaxyEncounterLocationId == null
                || st.activeGalaxyEncounterLocationId.isBlank())) {
            failures.add("mission-briefing-completeness: encounter has no source location");
        }
    }

    private static void validateContacts(CampaignSystem.CampaignState st,
                                         List<String> validators,
                                         List<String> failures) {
        validators.add("contact-validity");
        HashSet<Integer> ids = new HashSet<>();
        for (CampaignSystem.GalaxySearchGroup group : st.galaxySearchGroups) {
            if (group == null) {
                failures.add("contact-validity: null search group");
                continue;
            }
            if (!ids.add(group.id)) failures.add("contact-validity: duplicate search group id " + group.id);
            if (!Double.isFinite(group.x) || !Double.isFinite(group.y)
                    || !Double.isFinite(group.lastKnownX) || !Double.isFinite(group.lastKnownY)) {
                failures.add("contact-validity: search group " + group.id + " has invalid coordinates");
            }
            if (group.contactFadeSec < 0.0 || group.trackIntegrity < 0.0) {
                failures.add("contact-validity: search group " + group.id + " has invalid certainty");
            }
        }
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force == null || force.destroyed) continue;
            if (!Double.isFinite(force.x) || !Double.isFinite(force.y)
                    || !Double.isFinite(force.lastKnownX) || !Double.isFinite(force.lastKnownY)) {
                failures.add("contact-validity: force " + force.id + " has invalid coordinates");
            }
            if (force.contactConfidence < 0.0 || force.contactConfidence > 1.0001 || force.uncertaintyRadius < 0.0) {
                failures.add("contact-validity: force " + force.id + " has invalid contact confidence");
            }
        }
    }

    private static void validateStrikeOrigins(CampaignSystem.CampaignState st,
                                              List<String> validators,
                                              List<String> failures) {
        validators.add("strike-origin");
        HashSet<Integer> ids = new HashSet<>();
        for (CampaignSystem.StrategicStrikeObject strike : st.strategicStrikeObjects) {
            if (strike == null) {
                failures.add("strike-origin: null strike object");
                continue;
            }
            if (!ids.add(strike.id)) failures.add("strike-origin: duplicate strike id " + strike.id);
            if (strike.owner == null) failures.add("strike-origin: strike " + strike.id + " has no owner");
            if (!Double.isFinite(strike.x) || !Double.isFinite(strike.y)
                    || !Double.isFinite(strike.targetX) || !Double.isFinite(strike.targetY)) {
                failures.add("strike-origin: strike " + strike.id + " has invalid trajectory");
            }
            if (strike.speed <= 0.0 || strike.targetLabel == null || strike.targetLabel.isBlank()) {
                failures.add("strike-origin: strike " + strike.id + " lacks launch metadata");
            }
        }
    }

    private static void validateSaveMigration(GameContext ctx,
                                              CampaignSystem.CampaignState st,
                                              List<String> validators,
                                              List<String> failures) {
        validators.add("save-migration");
        if (CampaignCheckpointStore.currentVersion() <= 0) failures.add("save-migration: invalid current schema version");
        List<CampaignSaveContract.FieldContract> inventory = CampaignSaveContract.inventory();
        if (inventory.isEmpty()) failures.add("save-migration: save field contract is empty");
        HashSet<String> fields = new HashSet<>();
        for (CampaignSaveContract.FieldContract row : inventory) {
            if (!fields.add(row.field())) failures.add("save-migration: duplicate field contract " + row.field());
            if (row.fallback() == null || row.fallback().isBlank()) {
                failures.add("save-migration: field " + row.field() + " lacks fallback");
            }
        }
        CampaignCheckpointStore.Checkpoint checkpoint = CampaignSystem.captureCheckpoint(ctx, st, Math.max(1, st.sector));
        if (checkpoint == null || !checkpoint.isUsable()) {
            failures.add("save-migration: live state cannot produce a usable checkpoint");
        }
    }
}
