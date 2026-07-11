final class CampaignForceLifecycleSystem {
    private CampaignForceLifecycleSystem() {}

    static CampaignForceLifecycleValidation validate(CampaignSystem.CampaignState st,
                                                     CampaignSystem.CampaignForce force) {
        if (force == null) return invalid("missing force", "force", "skip null force");
        if (force.destroyed || CampaignSystem.isPlayerControlledCampaignForce(force)) return valid();
        if (force.mission == null) return invalid("missing mission", "mission", "assign simple director mission");
        if (force.workState == null) return invalid("missing work state", "workState", "prime lifecycle defaults");
        if (force.missionState == null) return invalid("missing mission state", "missionState", "prime lifecycle defaults");
        if (force.stopReason == null) return invalid("missing stop reason", "stopReason", "prime lifecycle defaults");
        if (force.reassignmentCondition == null) return invalid("missing reassignment condition", "reassignmentCondition", "prime lifecycle defaults");
        boolean hasDestination = force.destinationLocationId != null && !force.destinationLocationId.isBlank();
        boolean hasTargetCoord = Double.isFinite(force.targetX) && Double.isFinite(force.targetY)
                && (GameMath.dist2(force.x, force.y, force.targetX, force.targetY) > 18.0 * 18.0 || !force.routePoints.isEmpty());
        boolean hasRoute = !force.routePoints.isEmpty() || !force.patrolWaypoints.isEmpty();
        boolean hasWorkLocation = hasDestination || force.sourceLocationId != null && !force.sourceLocationId.isBlank();
        boolean hasEscortedTarget = force.targetForceId > 0 && CampaignSystem.campaignForceById(st, force.targetForceId) != null;
        if (force.workState == CampaignSystem.CampaignForceWorkState.TRAVELING && !hasDestination && !hasRoute && !hasTargetCoord) {
            return invalid("traveling without destination or route", "routePoints", "assign route");
        }
        if ((force.intent == CampaignSystem.CampaignForceIntent.RETREATING || force.workState == CampaignSystem.CampaignForceWorkState.RECOVERING)
                && !hasDestination) {
            return invalid("recovering without safe destination", "destinationLocationId", "assign nearest support hub");
        }
        if (!hasDestination && !hasTargetCoord && !hasRoute && !hasWorkLocation && !hasEscortedTarget
                && force.intent != CampaignSystem.CampaignForceIntent.GUARDING && force.intent != CampaignSystem.CampaignForceIntent.HOLDING) {
            return invalid("mission has no destination target route or work location", "destination/target/route", "assign destination or route");
        }
        if (force.workState == CampaignSystem.CampaignForceWorkState.WAITING_WITH_PURPOSE
                && force.stopReason == CampaignSystem.CampaignForceStopReason.NONE) {
            return invalid("waiting without stop reason", "stopReason", "assign valid stop reason");
        }
        if ((force.state == CampaignSystem.CampaignFleetState.IDLE || force.workState == CampaignSystem.CampaignForceWorkState.WAITING_WITH_PURPOSE)
                && force.stopReason == CampaignSystem.CampaignForceStopReason.NONE
                && force.stationaryTimeSec > 2.0) {
            return invalid("stationary without stop reason", "stopReason", "assign work or reassign route");
        }
        if (hasWorkLocation
                && force.routePoints.isEmpty()
                && force.workState != CampaignSystem.CampaignForceWorkState.TRAVELING
                && force.workState != CampaignSystem.CampaignForceWorkState.RECOVERING
                && force.workState != CampaignSystem.CampaignForceWorkState.FIGHTING
                && force.stationaryTimeSec > 12.0
                && !isValidPoiStopReason(force.stopReason)) {
            return invalid("stopped at POI without valid work", "stopReason", "assign guard extract raid repair trade scan hide or stage work");
        }
        if (force.workState == CampaignSystem.CampaignForceWorkState.WORKING
                && force.workRemainingSec <= 0.0
                && force.taskDeadlineSec <= 0.0
                && !missionHasImplicitWorkCompletion(force)) {
            return invalid("working without timer or completion condition", "workRemainingSec", "set work timer or completion condition");
        }
        if (isTimedStopReason(force.stopReason)
                && force.workState != CampaignSystem.CampaignForceWorkState.TRAVELING
                && force.workRemainingSec <= 0.0
                && force.taskDeadlineSec <= 0.0
                && force.missionState != CampaignSystem.CampaignForceMissionState.COMPLETED
                && !missionHasImplicitWorkCompletion(force)) {
            return invalid("stopped work timer expired without completion", "taskDeadlineSec", "complete work or assign new mission");
        }
        if ((force.mission == CampaignSystem.CampaignFleetMission.PATROL || force.mission == CampaignSystem.CampaignFleetMission.RECON)
                && (force.intent == CampaignSystem.CampaignForceIntent.PATROLLING || force.intent == CampaignSystem.CampaignForceIntent.SEARCHING)
                && force.patrolWaypoints.isEmpty()
                && force.routePoints.isEmpty()
                && force.workState != CampaignSystem.CampaignForceWorkState.RECOVERING
                && force.intent != CampaignSystem.CampaignForceIntent.RETREATING) {
            return invalid("patrol mission missing waypoint loop", "patrolWaypoints", "assign patrol waypoints or return to base");
        }
        if (force.intent == CampaignSystem.CampaignForceIntent.ESCORTING && force.targetForceId > 0) {
            CampaignSystem.CampaignForce target = CampaignSystem.campaignForceById(st, force.targetForceId);
            if (target == null || target.destroyed || target.missionState == CampaignSystem.CampaignForceMissionState.COMPLETED) {
                return invalid("escort target missing", "targetForceId", "return to base or choose new escort");
            }
        }
        if (force.mission == CampaignSystem.CampaignFleetMission.ESCORT && force.targetForceId <= 0) {
            return invalid("escort target missing", "targetForceId", "return to base or choose new escort");
        }
        if ((force.kind == CampaignSystem.CampaignForceKind.MINING_GROUP
                || force.kind == CampaignSystem.CampaignForceKind.CONVOY
                || force.kind == CampaignSystem.CampaignForceKind.TRADE_GROUP
                || force.kind == CampaignSystem.CampaignForceKind.INSTALLATION_TRAFFIC)
                && force.cargoKind == CampaignSystem.CampaignForceCargoKind.NONE) {
            return invalid("cargo mission missing cargo kind", "cargoKind", "initialize cargo settings");
        }
        if (force.mission == CampaignSystem.CampaignFleetMission.RAID
                && force.intentTimerSec <= 0.0
                && force.taskDeadlineSec <= 0.0
                && force.targetForceId <= 0
                && force.routePoints.isEmpty()) {
            return invalid("raid timed out without target or fallback", "intentTimerSec", "route to alternate ambush point or return home");
        }
        if ((force.mission == CampaignSystem.CampaignFleetMission.RAID
                || force.mission == CampaignSystem.CampaignFleetMission.CAPTURE
                || force.mission == CampaignSystem.CampaignFleetMission.BLOCKADE)
                && force.intentTimerSec <= 0.0
                && force.taskDeadlineSec <= 0.0
                && force.routePoints.isEmpty()) {
            return invalid("combat mission missing timeout or route", "intentTimerSec", "set route and give-up timer");
        }
        if (force.workState == CampaignSystem.CampaignForceWorkState.FIGHTING
                && force.state != CampaignSystem.CampaignFleetState.ENGAGING
                && force.missionState != CampaignSystem.CampaignForceMissionState.RECOVERING
                && force.missionState != CampaignSystem.CampaignForceMissionState.RETREATING
                && force.missionState != CampaignSystem.CampaignForceMissionState.COMPLETED
                && force.stopReason != CampaignSystem.CampaignForceStopReason.REPAIRING
                && force.stopReason != CampaignSystem.CampaignForceStopReason.RECOVERING
                && force.stopReason != CampaignSystem.CampaignForceStopReason.SALVAGING
                && force.stopReason != CampaignSystem.CampaignForceStopReason.HOLDING_LINE
                && force.stopReason != CampaignSystem.CampaignForceStopReason.AVOIDING_SUPERIOR_THREAT) {
            return invalid("battle participant has no aftermath order", "missionState", "assign repair retreat salvage continue hold or destroy");
        }
        return valid();
    }

    private static CampaignForceLifecycleValidation valid() {
        return new CampaignForceLifecycleValidation(true, "", "", "");
    }

    private static CampaignForceLifecycleValidation invalid(String reason, String field, String fix) {
        return new CampaignForceLifecycleValidation(false, reason, field, fix);
    }

    private static boolean missionHasImplicitWorkCompletion(CampaignSystem.CampaignForce force) {
        if (force == null || force.mission == null) return false;
        return switch (force.mission) {
            case PATROL, RECON -> !force.patrolWaypoints.isEmpty() || !force.routePoints.isEmpty();
            case CONVOY, ESCORT -> force.targetForceId > 0 || !force.routePoints.isEmpty();
            case REPAIR -> force.targetForceId > 0 || force.hullIntegrity < 100.0 || force.readiness < 100.0 || force.repairCapacity < 100.0;
            case RAID, INTERCEPT, CAPTURE, BLOCKADE, REINFORCE, COUNTER_SORTIE -> force.targetForceId > 0 || !force.routePoints.isEmpty();
        };
    }

    private static boolean isTimedStopReason(CampaignSystem.CampaignForceStopReason stopReason) {
        if (stopReason == null) return false;
        return switch (stopReason) {
            case MINING, SALVAGING, REPAIRING, REFUELING, TRADING, LOADING, UNLOADING,
                 SCANNING, HIDING, AMBUSHING, BLOCKADING, STAGING, WAITING_FOR_ESCORT,
                 WAITING_FOR_REINFORCEMENTS, RECOVERING_SURVIVORS -> true;
            case NONE, GUARDING, RECOVERING, HOLDING_LINE, AVOIDING_SUPERIOR_THREAT -> false;
        };
    }

    private static boolean isValidPoiStopReason(CampaignSystem.CampaignForceStopReason stopReason) {
        if (stopReason == null) return false;
        return switch (stopReason) {
            case GUARDING, MINING, SALVAGING, REPAIRING, REFUELING, TRADING, LOADING, UNLOADING,
                 SCANNING, HIDING, AMBUSHING, BLOCKADING, STAGING, RECOVERING_SURVIVORS -> true;
            case NONE, WAITING_FOR_ESCORT, WAITING_FOR_REINFORCEMENTS, RECOVERING,
                 HOLDING_LINE, AVOIDING_SUPERIOR_THREAT -> false;
        };
    }
}

final class CampaignForceLifecycleValidation {
    final boolean valid;
    final String invalidReason;
    final String blockingField;
    final String recommendedFix;

    CampaignForceLifecycleValidation(boolean valid, String invalidReason, String blockingField, String recommendedFix) {
        this.valid = valid;
        this.invalidReason = invalidReason == null ? "" : invalidReason;
        this.blockingField = blockingField == null ? "" : blockingField;
        this.recommendedFix = recommendedFix == null ? "" : recommendedFix;
    }
}
