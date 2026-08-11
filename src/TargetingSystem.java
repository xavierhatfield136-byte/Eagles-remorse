import java.util.ArrayList;
import java.util.List;

public final class TargetingSystem {
    public static final double PLAYER_CURSOR_LOCK_RADIUS = 560.0;
    public static final double PLAYER_TARGET_LOCK_RANGE = 3600.0;
    private static final double NPC_TARGET_SEARCH_RANGE = 3000.0;

    private TargetingSystem(){}

    public static boolean isCiwsOnlyTarget(Ship target) {
        if (target == null || target.role == null) return false;
        return target.role == ShipRole.FIGHTER || target.role == ShipRole.BOMBER;
    }

    public static boolean isMainBatteryScreenTarget(Ship observer, Ship target) {
        if (observer == null || target == null) return false;
        if (!target.isSmallCraft()) return false;
        if (observer.isSmallCraft()) return false;
        if (observer.role == null) return true;
        return switch (observer.role) {
            case PD_CRAFT, CIWS_CORVETTE, STATIC_TURRET -> false;
            default -> true;
        };
    }

    public static void lockClosestToMouse(GameContext ctx, PlayerControl controls) {
        if (ctx == null || controls == null) return;
        double mx = CameraSystem.screenToWorldX(ctx, controls.getMouseX());
        double my = CameraSystem.screenToWorldY(ctx, controls.getMouseY());
        Ship observer = ctx.player;
        Ship s = findClosestEnemyToPoint(ctx, observer, mx, my, PLAYER_CURSOR_LOCK_RADIUS);
        if (s == null) {
            ctx.eventBanner = "NO ENEMY NEAR CURSOR";
            ctx.eventBannerT = 1.2;
            return;
        }
        ctx.lockedTarget = s;
        ctx.eventBanner = "LOCKED: " + s.name;
        ctx.eventBannerT = 1.0;
    }

    public static void cycleLockedTarget(GameContext ctx, int dir) {
        List<Ship> enemies = new ArrayList<>();
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (isCiwsOnlyTarget(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (ctx.player != null && GameMath.dist2(ctx.player.x, ctx.player.y, s.x, s.y)
                    > PLAYER_TARGET_LOCK_RANGE * PLAYER_TARGET_LOCK_RANGE) continue;
            if (!canDirectlyEngage(ctx, ctx.player, s)) continue;
            if (!isDetectableToObserver(ctx, ctx.player, s)) continue;
            enemies.add(s);
        }
        if (enemies.isEmpty()) {
            ctx.lockedTarget = null;
            return;
        }

        int idx = -1;
        for (int i = 0; i < enemies.size(); i++) {
            if (enemies.get(i) == ctx.lockedTarget) { idx = i; break; }
        }
        if (idx < 0) idx = (ctx.lockedIndexHint < enemies.size()) ? ctx.lockedIndexHint : 0;

        idx = (idx + dir) % enemies.size();
        if (idx < 0) idx += enemies.size();

        ctx.lockedIndexHint = idx;
        ctx.lockedTarget = enemies.get(idx);
        ctx.eventBanner = "LOCKED: " + ctx.lockedTarget.name;
        ctx.eventBannerT = 0.9;
    }

    public static Ship getPreferredEnemyTarget(GameContext ctx, Ship seeker) {
        if (ctx.lockedTarget != null && isAlive(ctx.lockedTarget)
                && seeker.faction != null
                && ctx.player != null
                && seeker.faction.isFriendlyTo(ctx.player.faction)
                && !seeker.faction.isFriendlyTo(ctx.lockedTarget.faction)
                && !isCiwsOnlyTarget(ctx.lockedTarget)
                && canDirectlyEngage(ctx, seeker, ctx.lockedTarget)
                && isDetectableToObserver(ctx, seeker, ctx.lockedTarget)) {
            return ctx.lockedTarget;
        }

        Ship best = null;
        double bestD2 = Double.MAX_VALUE;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(seeker.faction, seeker.x, seeker.y, NPC_TARGET_SEARCH_RANGE, nearby);

        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (isCiwsOnlyTarget(s)) continue;

            if (seeker.faction != null && s.faction != null && !seeker.faction.isFriendlyTo(s.faction)) {
                if (!canDirectlyEngage(ctx, seeker, s)) continue;
                if (!isDetectableToObserver(ctx, seeker, s)) continue;
                double d2 = GameMath.dist2(seeker.x, seeker.y, s.x, s.y);
                if (d2 < bestD2) { bestD2 = d2; best = s; }
            }
        }
        return best;
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship observer = ctx.player;
        return findClosestEnemyToPoint(ctx, observer, x, y, maxDist);
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        Faction perspective = (observer == null) ? ((ctx.player == null) ? null : ctx.player.faction) : observer.faction;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(perspective, x, y, maxDist, nearby);
        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (isCiwsOnlyTarget(s)) continue;
            if (isPlayerObserver(ctx, observer) && GameMath.dist2(observer.x, observer.y, s.x, s.y)
                    > PLAYER_TARGET_LOCK_RANGE * PLAYER_TARGET_LOCK_RANGE) continue;
            if (!canDirectlyEngage(ctx, observer, s)) continue;
            if (!isDetectableToObserver(ctx, observer, s)) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static Ship findClosestHostileSmallCraft(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        Faction perspective = (observer == null) ? ((ctx.player == null) ? null : ctx.player.faction) : observer.faction;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(perspective, x, y, maxDist, nearby);
        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!s.isSmallCraft()) continue;
            if (perspective != null && s.faction != null && perspective.isFriendlyTo(s.faction)) continue;
            if (!canDirectlyEngage(ctx, observer, s)) continue;
            if (!isDetectableToObserver(ctx, observer, s)) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static Ship findClosestVisibleHostileInSector(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        if (ctx == null) return null;
        BattlefieldSectorSystem.SectorDefinition sourceSector = BattlefieldSectorSystem.sectorAt(ctx, x, y);
        if (sourceSector == null) return findClosestEnemyToPoint(ctx, observer, x, y, maxDist);

        Ship best = null;
        double bestD2 = maxDist * maxDist;
        Faction perspective = (observer == null) ? ((ctx.player == null) ? null : ctx.player.faction) : observer.faction;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(perspective, x, y, maxDist, nearby);
        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (perspective != null && s.faction != null && perspective.isFriendlyTo(s.faction)) continue;
            if (!sourceSector.containsWorld(ctx, s.x, s.y)) continue;
            if (!canDirectlyEngage(ctx, observer, s)) continue;
            if (!isDetectableToObserver(ctx, observer, s)) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static Missile findClosestHostileMissile(GameContext ctx, Faction perspective, double x, double y, double maxDist) {
        if (ctx == null || perspective == null) return null;
        Missile best = null;
        double bestD2 = maxDist * maxDist;
        List<Missile> nearby = new ArrayList<>();
        ctx.entityQuery.collectMissilesNear(x, y, maxDist, nearby);
        for (Missile missile : nearby) {
            if (missile == null || !missile.alive) continue;
            if (missile.faction == null || perspective.isFriendlyTo(missile.faction)) continue;
            double d2 = GameMath.dist2(x, y, missile.x, missile.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = missile;
            }
        }
        return best;
    }

    public static Ship findClosestEngagementTarget(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        Faction perspective = (observer == null) ? ((ctx.player == null) ? null : ctx.player.faction) : observer.faction;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(perspective, x, y, maxDist, nearby);
        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (isCiwsOnlyTarget(s)) continue;
            if (isMainBatteryScreenTarget(observer, s)) continue;
            if (!isDetectableToObserver(ctx, observer, s)) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static boolean isDetectableToObserver(Ship observer, Ship target) {
        return isDetectableToObserver(null, observer, target);
    }

    public static boolean isDetectableToObserver(GameContext ctx, Ship observer, Ship target) {
        return isDetectableToObserver(ctx, observer, target, Double.NaN, Double.NaN);
    }

    static boolean isDetectableToObserver(GameContext ctx, Ship observer, Ship target,
                                          double observerSensorMul, double targetSignatureMul) {
        if (target == null) return false;
        if (observer == target) return true;
        if (observer == null) {
            if (!target.isStealth) return true;
            if (!target.isCloaked()) return true;
            return target.revealTimer > 0.0;
        }
        if (!isAlive(observer)) return false;
        if (sharesWeaponsHotContact(ctx, observer, target)) return true;
        if (formationRelayDetectsTarget(ctx, observer, target)) return true;
        if (ctx != null && inSameDetectionZone(ctx, observer, target) && !target.isStealth) return true;
        double range = detectionRangeForObserver(observer, target, observerSensorMul, targetSignatureMul);
        double dx = target.x - observer.x;
        double dy = target.y - observer.y;
        if (dx * dx + dy * dy > range * range) return false;
        if (!target.isStealth) return true;
        if (!target.isCloaked()) return true;
        if (target.revealTimer > 0.0) return true;
        return false;
    }

    private static boolean sharesWeaponsHotContact(GameContext ctx, Ship observer, Ship target) {
        if (ctx == null || observer == null || target == null) return false;
        if (target.weaponsHotTimer <= 1e-6) return false;
        boolean cloakCoverActive = target.isStealth
                && target.cloakEnabled
                && target.cloakControlMode == Ship.CloakControlMode.ACTIVE
                && target.cloakEnergy > 0.01;
        if (cloakCoverActive) return false;
        if (observer.faction == null || target.faction == null || observer.faction.isFriendlyTo(target.faction)) return false;
        return canShareContactTelemetry(ctx, observer, target);
    }

    private static boolean formationRelayDetectsTarget(GameContext ctx, Ship observer, Ship target) {
        if (ctx == null || observer == null || target == null) return false;
        if (observer.role != ShipRole.MOTHERSHIP) return false;
        if (observer.faction == null || target.faction == null || observer.faction.isFriendlyTo(target.faction)) return false;
        if (!canShareContactTelemetry(ctx, observer, target)) return false;
        for (Ship ally : ctx.ships) {
            if (ally == null || ally == observer || ally == target) continue;
            if (!isAlive(ally)) continue;
            if (ally.faction == null || !ally.faction.isFriendlyTo(observer.faction)) continue;
            if (!canShareContactTelemetry(ctx, observer, ally) || !canShareContactTelemetry(ctx, ally, target)) continue;
            if (isDetectableToObserverLocal(ctx, ally, target)) return true;
        }
        return false;
    }

    private static boolean isDetectableToObserverLocal(GameContext ctx, Ship observer, Ship target) {
        if (target == null) return false;
        if (observer == target) return true;
        if (observer == null) return !target.isStealth || !target.isCloaked() || target.revealTimer > 0.0;
        if (!isAlive(observer)) return false;
        if (sharesWeaponsHotContact(ctx, observer, target)) return true;
        double range = detectionRangeForObserver(observer, target);
        double dx = target.x - observer.x;
        double dy = target.y - observer.y;
        if (dx * dx + dy * dy > range * range) return false;
        if (!target.isStealth) return true;
        if (!target.isCloaked()) return true;
        return target.revealTimer > 0.0;
    }

    private static boolean inSameDetectionZone(GameContext ctx, Ship a, Ship b) {
        if (ctx == null || a == null || b == null) return false;
        if (CampaignSystem.usesMissionSubzones(ctx)) {
            int aZone = CampaignSystem.ensureShipMissionSubzone(ctx, a);
            int bZone = CampaignSystem.ensureShipMissionSubzone(ctx, b);
            if (aZone >= 0 && bZone >= 0) return aZone == bZone;
        }
        BattlefieldSectorSystem.SectorDefinition aSector = BattlefieldSectorSystem.sectorAt(ctx, a.x, a.y);
        BattlefieldSectorSystem.SectorDefinition bSector = BattlefieldSectorSystem.sectorAt(ctx, b.x, b.y);
        if (aSector != null && bSector != null) {
            return aSector.id != null && aSector.id.equalsIgnoreCase(bSector.id);
        }
        return true;
    }

    private static boolean canDirectlyEngage(GameContext ctx, Ship observer, Ship target) {
        if (ctx == null || observer == null || target == null) return true;
        return CampaignSystem.missionSubzonesAllowDirectFire(ctx, observer, target);
    }

    private static boolean canShareContactTelemetry(GameContext ctx, Ship a, Ship b) {
        if (ctx == null || a == null || b == null) return false;
        if (inSameDetectionZone(ctx, a, b)) return true;
        double relayRange = sensorRelayRange(a) + sensorRelayRange(b);
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return dx * dx + dy * dy <= relayRange * relayRange;
    }

    private static double sensorRelayRange(Ship ship) {
        if (ship == null) return 0.0;
        double sensorMul = Math.max(0.35, Math.min(1.75, ship.sensorRangeMultiplier()));
        double roleBase = baseDetectionRange(ship.role);
        double relayBias = switch (ship.role) {
            case PICKET, PATROL, STEALTH_SHIP, COMMAND_INTEL_TITAN, MOTHERSHIP -> 1.18;
            case FIGHTER, BOMBER, PD_CRAFT, DRONE -> 0.72;
            default -> 0.92;
        };
        return Math.max(540.0, roleBase * sensorMul * relayBias);
    }

    public static double detectionRangeForObserver(Ship observer, Ship target) {
        return detectionRangeForObserver(observer, target, Double.NaN, Double.NaN);
    }

    static double detectionRangeForObserver(Ship observer, Ship target, double observerSensorMul,
                                            double targetSignatureMul) {
        if (observer == null) return Double.POSITIVE_INFINITY;
        double baseRange = baseDetectionRange(observer.role);
        double sensorMul = Double.isFinite(observerSensorMul)
                ? Math.max(0.35, Math.min(1.75, observerSensorMul))
                : Math.max(0.35, Math.min(1.75, observer.sensorRangeMultiplier()));
        double targetMul = Double.isFinite(targetSignatureMul)
                ? Math.max(0.55, Math.min(1.45, targetSignatureMul))
                : targetSignatureMultiplier(target);
        double range = baseRange * sensorMul * targetMul;
        if (observer instanceof Player) {
            range *= 2.0;
        }
        return Math.max(260.0, range);
    }

    private static boolean isPlayerObserver(GameContext ctx, Ship observer) {
        return observer != null && (observer instanceof Player || ctx != null && observer == ctx.player);
    }

    private static double baseDetectionRange(ShipRole role) {
        if (role == null) return 1650.0;
        return switch (role) {
            case PICKET, PATROL, STEALTH_SHIP, FIGHTER, DRONE -> 2550.0;
            case PD_CRAFT, BOMBER -> 2320.0;
            case FRIGATE, MISSILE_BOAT, ARTILLERY_SHIP, CIWS_CORVETTE, LIGHT_CRUISER,
                    MEDIUM_CRUISER, TRANSPORT, MINER, HAULER -> 1840.0;
            case CRUISER, BATTLECRUISER, CARRIER, DRONE_CARRIER, TRANSPORT_TITAN,
                    CARRIER_SUPPORT_TITAN,
                    COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN, SHIELD_BASTION_TITAN,
                    MOBILE_STATION_TITAN, BASE, STATIC_TURRET -> 1520.0;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP, BULWARK_TITAN, VANGUARD_TITAN,
                    INTERDICTION_TITAN, ARTILLERY_TITAN, FLEET_TELEPORTER_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                    HYPERWEAPON_TITAN, MOTHERSHIP -> 1220.0;
        };
    }

    static double targetSignatureMultiplier(Ship target) {
        if (target == null) return 1.0;
        double multiplier = target.isSmallCraft() ? 0.74 : 1.0;
        if (target.role != null) {
            multiplier *= switch (target.role) {
                case PICKET, PATROL, STEALTH_SHIP, FIGHTER, BOMBER, PD_CRAFT, DRONE -> 0.92;
                case BASE, MOTHERSHIP -> 1.34;
                default -> target.role.isTitanOrMothership() ? 1.28 : (target.role.isCapitalCombatant() ? 1.14 : 1.0);
            };
        }
        if (target.revealTimer > 0.0) multiplier *= 1.12;
        return Math.max(0.55, Math.min(1.45, multiplier));
    }

    public static void enforceCloakLockRules(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.lockedTarget != null && (isHardCloaked(ctx.lockedTarget) || isCiwsOnlyTarget(ctx.lockedTarget))) {
            ctx.lockedTarget = null;
            ctx.eventBanner = "TARGET LOCK BROKEN";
            ctx.eventBannerT = Math.max(ctx.eventBannerT, 0.9);
        }
        if (ctx.command.fleetSharedTargets != null && !ctx.command.fleetSharedTargets.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<Faction, Ship>> it = ctx.command.fleetSharedTargets.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Faction, Ship> e = it.next();
                Ship target = (e == null) ? null : e.getValue();
                if (isHardCloaked(target) || isCiwsOnlyTarget(target)) it.remove();
            }
        }
    }

    private static boolean isHardCloaked(Ship target) {
        if (target == null) return false;
        return target.alive && !target.dying && target.hp > 0
                && target.isStealth
                && target.isCloaked()
                && target.revealTimer <= 0.0;
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        return s.alive && !s.dying && s.hp > 0;
    }
}
