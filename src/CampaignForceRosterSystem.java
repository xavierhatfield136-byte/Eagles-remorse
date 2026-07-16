import java.util.ArrayList;
import java.util.List;

final class CampaignForceRosterSystem {
    private CampaignForceRosterSystem() {}

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

    static boolean hasDepletedConcreteRoster(CampaignSystem.CampaignState st,
                                             CampaignSystem.CampaignForce force) {
        return force != null
                && force.kind != CampaignSystem.CampaignForceKind.PLAYER_FLEET
                && force.hadTacticalMembers
                && force.shipIds.isEmpty()
                && !hasRecoverablePoolMembers(st, force);
    }

    static boolean hasViableEncounterRoster(GameContext ctx,
                                            CampaignSystem.CampaignState st,
                                            CampaignSystem.CampaignForce force) {
        if (force == null || force.destroyed) return false;
        if (hasDepletedConcreteRoster(st, force)) return false;
        return force.linkedSearchGroupId > 0
                || !force.shipIds.isEmpty()
                || hasRecoverablePoolMembers(st, force)
                || force.strength > 1.0
                || force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET;
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
                noteKnownPosition(force, force.x, force.y, force.contactConfidence, force.uncertaintyRadius);
            }
        }
    }

    private static CampaignSystem.CampaignForce findForceById(CampaignSystem.CampaignState st, int forceId) {
        if (st == null || forceId <= 0) return null;
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force != null && force.id == forceId) return force;
        }
        return null;
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
