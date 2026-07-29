import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CampaignForceRosterSystem {
    private CampaignForceRosterSystem() {}

    enum ForceRosterState {
        CONCRETE,
        TEMPORARILY_TRANSITIONING,
        INTEL_ONLY,
        SCRIPTED_NON_PHYSICAL,
        DEPLETED,
        INVALID
    }

    static final class ConcreteForceRoster {
        final int forceId;
        final int snapshotTick;
        final ForceRosterState state;
        final Set<String> concreteShipKeys = new LinkedHashSet<>();
        final Set<Integer> liveTacticalShipIds = new LinkedHashSet<>();
        final Set<Integer> viablePoolRecordIds = new LinkedHashSet<>();
        final Set<String> linkedSearchGroupShipKeys = new LinkedHashSet<>();
        final Set<String> duplicateAssignments = new LinkedHashSet<>();
        final Set<String> unresolvedMembership = new LinkedHashSet<>();

        ConcreteForceRoster(int forceId, int snapshotTick, ForceRosterState state) {
            this.forceId = Math.max(0, forceId);
            this.snapshotTick = Math.max(0, snapshotTick);
            this.state = state == null ? ForceRosterState.DEPLETED : state;
        }

        int concreteShipCount() {
            return concreteShipKeys.size();
        }

        boolean hasConcreteShips() {
            return !concreteShipKeys.isEmpty();
        }

        boolean hasDuplicateAssignments() {
            return !duplicateAssignments.isEmpty();
        }

        boolean hasUnresolvedMembership() {
            return !unresolvedMembership.isEmpty();
        }
    }

    static List<Integer> activeEncounterForceIds(CampaignSystem.CampaignState st) {
        if (st == null || st.activeGalaxyEncounterForceIds.isEmpty()) return List.of();
        return List.copyOf(st.activeGalaxyEncounterForceIds);
    }

    static int activeEncounterParentForceId(CampaignSystem.CampaignState st) {
        return st == null ? 0 : Math.max(0, st.activeGalaxyEncounterParentForceId);
    }

    static void clearActiveEncounterRefs(CampaignSystem.CampaignState st) {
        if (st == null) return;
        st.activeGalaxyEncounterForceIds.clear();
        st.activeGalaxyEncounterParentForceId = 0;
    }

    static void setActiveEncounterRefs(CampaignSystem.CampaignState st,
                                       CampaignSystem.CampaignForce... forces) {
        clearActiveEncounterRefs(st);
        if (st == null || forces == null) return;
        for (CampaignSystem.CampaignForce force : forces) {
            addActiveEncounterRef(st, force);
        }
    }

    static void captureActiveEncounterRefsFromLiveShips(GameContext ctx, CampaignSystem.CampaignState st) {
        clearActiveEncounterRefs(st);
        if (ctx == null || st == null || ctx.ships == null) return;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            Integer forceId = st.shipCampaignForceIds.get(ship.id);
            CampaignSystem.CampaignForce force = findForceById(st, forceId == null ? 0 : forceId);
            if (force == null || force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) continue;
            addActiveEncounterRef(st, force);
        }
    }

    private static void addActiveEncounterRef(CampaignSystem.CampaignState st,
                                              CampaignSystem.CampaignForce force) {
        if (st == null || force == null || force.id <= 0) return;
        st.activeGalaxyEncounterForceIds.add(force.id);
        if (force.parentForceId > 0) {
            st.activeGalaxyEncounterParentForceId = force.parentForceId;
            st.activeGalaxyEncounterForceIds.add(force.parentForceId);
        } else if (st.activeGalaxyEncounterParentForceId <= 0) {
            st.activeGalaxyEncounterParentForceId = force.id;
        }
    }

    static boolean hasRecoverablePoolMembers(CampaignSystem.CampaignState st,
                                             CampaignSystem.CampaignForce force) {
        if (st == null || force == null || st.campaignShipPool.isEmpty()) return false;
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null || record.forceId != force.id) continue;
            if (record.status == CampaignSystem.CampaignShipPoolStatus.DESTROYED
                    || record.status == CampaignSystem.CampaignShipPoolStatus.UNDER_CONSTRUCTION) continue;
            return true;
        }
        return false;
    }

    static ConcreteForceRoster resolveConcreteRoster(GameContext ctx,
                                                     CampaignSystem.CampaignState st,
                                                     CampaignSystem.CampaignForce force) {
        int tick = st == null ? 0 : st.campaignForceSimTickCount;
        if (force == null) return new ConcreteForceRoster(0, tick, ForceRosterState.INVALID);
        ConcreteForceRoster roster = new ConcreteForceRoster(force.id, tick, ForceRosterState.DEPLETED);
        if (force.destroyed) return roster;

        if (ctx != null && ctx.ships != null && force.shipIds != null && !force.shipIds.isEmpty()) {
            for (Integer shipId : force.shipIds) {
                if (shipId == null || shipId <= 0) continue;
                Ship ship = CampaignSystem.findShipById(ctx, shipId);
                if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) {
                    roster.unresolvedMembership.add("dead-tactical:" + shipId);
                    continue;
                }
                if (st != null) {
                    Integer mappedForce = st.shipCampaignForceIds.get(shipId);
                    if (mappedForce != null && mappedForce != force.id) {
                        roster.unresolvedMembership.add("ship-force-mismatch:" + shipId + "->" + mappedForce);
                    }
                }
                String key = stableCampaignShipKey(st, shipId);
                addConcreteKey(roster, key, "tactical:" + shipId);
                roster.liveTacticalShipIds.add(shipId);
            }
        }

        if (st != null && !st.campaignShipPool.isEmpty()) {
            for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
                if (record == null || record.forceId != force.id) continue;
                if (record.status == CampaignSystem.CampaignShipPoolStatus.DESTROYED
                        || record.status == CampaignSystem.CampaignShipPoolStatus.UNDER_CONSTRUCTION) continue;
                String key = "pool:" + record.id;
                addConcreteKey(roster, key, "pool:" + record.id);
                roster.viablePoolRecordIds.add(record.id);
            }
        }

        if (force.linkedSearchGroupId > 0 && st != null) {
            CampaignSystem.GalaxySearchGroup group = findSearchGroupById(st, force.linkedSearchGroupId);
            if (group == null) {
                roster.unresolvedMembership.add("missing-search-group:" + force.linkedSearchGroupId);
            } else if (group.behavior != CampaignSystem.GalaxySearchBehavior.RETURNING
                    && group.contactConfidence != CampaignSystem.GalaxyContactConfidence.LOST_CONTACT) {
                // Search groups project movement and intel. They do not add separate physical ships
                // unless the parent force has concrete tactical or pool membership.
                roster.linkedSearchGroupShipKeys.add("search-group:" + group.id);
            }
        }

        ForceRosterState state = resolveRosterState(st, force, roster);
        ConcreteForceRoster resolved = new ConcreteForceRoster(force.id, tick, state);
        resolved.concreteShipKeys.addAll(roster.concreteShipKeys);
        resolved.liveTacticalShipIds.addAll(roster.liveTacticalShipIds);
        resolved.viablePoolRecordIds.addAll(roster.viablePoolRecordIds);
        resolved.linkedSearchGroupShipKeys.addAll(roster.linkedSearchGroupShipKeys);
        resolved.duplicateAssignments.addAll(roster.duplicateAssignments);
        resolved.unresolvedMembership.addAll(roster.unresolvedMembership);
        return resolved;
    }

    static ForceRosterState resolveRosterState(GameContext ctx,
                                               CampaignSystem.CampaignState st,
                                               CampaignSystem.CampaignForce force) {
        return resolveConcreteRoster(ctx, st, force).state;
    }

    static boolean hasConcreteFleetRoster(GameContext ctx,
                                          CampaignSystem.CampaignState st,
                                          CampaignSystem.CampaignForce force) {
        return resolveConcreteRoster(ctx, st, force).hasConcreteShips();
    }

    static boolean shouldRenderAsPhysicalFleet(GameContext ctx,
                                               CampaignSystem.CampaignState st,
                                               CampaignSystem.CampaignForce force) {
        if (force == null || force.destroyed) return false;
        if (force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) return true;
        return resolveRosterState(ctx, st, force) == ForceRosterState.CONCRETE;
    }

    static boolean isProtectedFromAutomaticCleanup(GameContext ctx,
                                                   CampaignSystem.CampaignState st,
                                                   CampaignSystem.CampaignForce force) {
        ForceRosterState state = resolveRosterState(ctx, st, force);
        return state == ForceRosterState.CONCRETE
                || state == ForceRosterState.TEMPORARILY_TRANSITIONING
                || state == ForceRosterState.INTEL_ONLY
                || state == ForceRosterState.SCRIPTED_NON_PHYSICAL;
    }

    static boolean shouldRetainAsNonPhysicalRecord(GameContext ctx,
                                                   CampaignSystem.CampaignState st,
                                                   CampaignSystem.CampaignForce force) {
        ForceRosterState state = resolveRosterState(ctx, st, force);
        return state == ForceRosterState.INTEL_ONLY || state == ForceRosterState.SCRIPTED_NON_PHYSICAL;
    }

    static int concreteShipCount(GameContext ctx,
                                 CampaignSystem.CampaignState st,
                                 CampaignSystem.CampaignForce force) {
        return resolveConcreteRoster(ctx, st, force).concreteShipCount();
    }

    static void markRosterTransition(CampaignSystem.CampaignForce force, String reason, double seconds) {
        if (force == null) return;
        force.rosterTransitionGraceSec = Math.max(force.rosterTransitionGraceSec, Math.max(0.0, seconds));
        force.rosterTransitionReason = reason == null ? "" : reason.trim();
    }

    static void updateRosterTransitions(CampaignSystem.CampaignState st, double dt) {
        if (st == null || st.campaignForces.isEmpty() || dt <= 0.0) return;
        double elapsed = Math.max(0.0, dt);
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force == null || force.rosterTransitionGraceSec <= 0.0) continue;
            force.rosterTransitionGraceSec = Math.max(0.0, force.rosterTransitionGraceSec - elapsed);
            if (force.rosterTransitionGraceSec <= 0.0) force.rosterTransitionReason = "";
        }
    }

    static boolean hasDepletedConcreteRoster(CampaignSystem.CampaignState st,
                                             CampaignSystem.CampaignForce force) {
        return force != null
                && force.kind != CampaignSystem.CampaignForceKind.PLAYER_FLEET
                && force.hadTacticalMembers
                && force.shipIds.isEmpty()
                && force.rosterTransitionGraceSec <= 0.0
                && !hasRecoverablePoolMembers(st, force);
    }

    static boolean hasViableEncounterRoster(GameContext ctx,
                                            CampaignSystem.CampaignState st,
                                            CampaignSystem.CampaignForce force) {
        if (force == null || force.destroyed) return false;
        if (force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) return true;
        return resolveRosterState(ctx, st, force) == ForceRosterState.CONCRETE;
    }

    static void resolveActiveTacticalRosters(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null || st.activeGalaxyEncounterForceIds.isEmpty()) return;
        for (Integer forceId : new ArrayList<>(st.activeGalaxyEncounterForceIds)) {
            CampaignSystem.CampaignForce force = findForceById(st, forceId == null ? 0 : forceId);
            if (force == null || force.destroyed
                    || force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) continue;
            if (!force.hadTacticalMembers && force.shipIds.isEmpty()) continue;

            ArrayList<Integer> tacticalIds = new ArrayList<>(force.shipIds);
            int live = 0;
            double hpFrac = 0.0;
            double avgX = 0.0;
            double avgY = 0.0;
            for (Integer shipId : tacticalIds) {
                Ship ship = CampaignSystem.findShipById(ctx, shipId == null ? 0 : shipId);
                if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
                live++;
                hpFrac += MathUtil.clamp(ship.hp / (double) Math.max(1, ship.hpMax), 0.0, 1.0);
                avgX += ship.x;
                avgY += ship.y;
            }

            clearTacticalMembership(st, force, tacticalIds, live <= 0);
            if (live <= 0) {
                force.strength = 0.0;
                force.readiness = 0.0;
                force.hullIntegrity = 0.0;
                CampaignSystem.markCampaignForceDefeated(st, force, "lost_all_tactical_members");
                continue;
            }

            if (live > 0) {
                double avgHp = hpFrac / live;
                force.x = avgX / live;
                force.y = avgY / live;
                force.hullIntegrity = MathUtil.clamp(avgHp * 100.0, 0.0, 100.0);
                force.strength = MathUtil.clamp(live * 18.0 * avgHp, 1.0, 100.0);
                force.readiness = MathUtil.clamp((avgHp * 78.0) + Math.min(22.0, live * 4.0), 0.0, 100.0);
                force.hadTacticalMembers = false;
                markRosterTransition(force, "tactical_survivors_reconciled", 6.0);
                noteKnownPosition(force, force.x, force.y, force.contactConfidence, force.uncertaintyRadius);
            }
        }
    }

    private static ForceRosterState resolveRosterState(CampaignSystem.CampaignState st,
                                                       CampaignSystem.CampaignForce force,
                                                       ConcreteForceRoster roster) {
        if (force == null || force.destroyed) return ForceRosterState.DEPLETED;
        if (force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) return ForceRosterState.CONCRETE;
        if (roster != null && roster.hasDuplicateAssignments()) return ForceRosterState.INVALID;
        if (roster != null && roster.hasConcreteShips()) return ForceRosterState.CONCRETE;
        if (force.rosterTransitionGraceSec > 0.0
                || (st != null && st.activeGalaxyEncounterForceIds.contains(force.id))) {
            return ForceRosterState.TEMPORARILY_TRANSITIONING;
        }
        if (force.linkedSearchGroupId > 0) {
            CampaignSystem.GalaxySearchGroup group = findSearchGroupById(st, force.linkedSearchGroupId);
            if (group != null
                    && group.behavior != CampaignSystem.GalaxySearchBehavior.RETURNING
                    && group.contactConfidence != CampaignSystem.GalaxyContactConfidence.LOST_CONTACT) {
                return ForceRosterState.INTEL_ONLY;
            }
        }
        if (force.assignedOperationId != null && !force.assignedOperationId.isBlank()) {
            return ForceRosterState.SCRIPTED_NON_PHYSICAL;
        }
        if (force.contactState == CampaignSystem.CampaignForceContactState.STALE
                && force.contactConfidence > 0.0
                && force.lastKnownAgeSec < 90.0) {
            return ForceRosterState.INTEL_ONLY;
        }
        if (roster != null && roster.hasUnresolvedMembership()) return ForceRosterState.INVALID;
        return ForceRosterState.DEPLETED;
    }

    private static CampaignSystem.CampaignForce findForceById(CampaignSystem.CampaignState st, int forceId) {
        if (st == null || forceId <= 0) return null;
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force != null && force.id == forceId) return force;
        }
        return null;
    }

    private static CampaignSystem.GalaxySearchGroup findSearchGroupById(CampaignSystem.CampaignState st, int groupId) {
        if (st == null || groupId <= 0) return null;
        for (CampaignSystem.GalaxySearchGroup group : st.galaxySearchGroups) {
            if (group != null && group.id == groupId) return group;
        }
        return null;
    }

    private static String stableCampaignShipKey(CampaignSystem.CampaignState st, int tacticalShipId) {
        if (st != null) {
            Integer recordId = st.tacticalShipPoolRecordIds.get(tacticalShipId);
            if (recordId != null && recordId > 0) return "pool:" + recordId;
        }
        return "ship:" + tacticalShipId;
    }

    private static void addConcreteKey(ConcreteForceRoster roster, String key, String source) {
        if (roster == null || key == null || key.isBlank()) return;
        if (!roster.concreteShipKeys.add(key)) {
            // A tactical ship and its pool record are expected to collapse to one identity.
            // Different tactical IDs claiming the same key are recorded for debug.
            if (source != null && source.startsWith("tactical:")) {
                roster.duplicateAssignments.add(key);
            }
        }
    }

    private static void clearTacticalMembership(CampaignSystem.CampaignState st,
                                                CampaignSystem.CampaignForce force,
                                                List<Integer> tacticalIds,
                                                boolean markRecordsDestroyed) {
        if (st == null || force == null) return;
        if (tacticalIds != null) {
            for (Integer shipId : tacticalIds) {
                if (shipId == null) continue;
                Integer mappedForce = st.shipCampaignForceIds.get(shipId);
                if (mappedForce != null && mappedForce == force.id) {
                    st.shipCampaignForceIds.remove(shipId);
                }
                Integer recordId = st.tacticalShipPoolRecordIds.remove(shipId);
                CampaignSystem.CampaignShipPoolRecord record = recordId == null ? null : st.campaignShipPool.get(recordId);
                if (record != null && markRecordsDestroyed) {
                    record.forceId = 0;
                    record.status = CampaignSystem.CampaignShipPoolStatus.DESTROYED;
                    record.condition = 0.0;
                }
            }
        }
        force.shipIds.clear();
    }

    private static void noteKnownPosition(CampaignSystem.CampaignForce force,
                                          double x,
                                          double y,
                                          double confidence,
                                          double uncertaintyRadius) {
        if (force == null) return;
        force.lastKnownX = x;
        force.lastKnownY = y;
        force.lastKnownAgeSec = 0.0;
        force.contactConfidence = MathUtil.clamp(confidence, 0.0, 1.0);
        force.uncertaintyRadius = Math.max(40.0, uncertaintyRadius);
    }
}
