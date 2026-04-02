import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class TitanAbilitySystem {
    private static final Map<Long, Double> BOARDING_PULSE_COOLDOWNS = new HashMap<>();
    private static final double BOARDING_PULSE_SECONDS = 1.75;

    private TitanAbilitySystem() {}

    public static void update(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        decayCooldowns(BOARDING_PULSE_COOLDOWNS, dt);

        for (Ship ship : ctx.ships) {
            if (ship != null) ship.clearCommandStatMultipliers();
        }

        if (ctx.ships.isEmpty()) return;
        ArrayList<Ship> nearby = new ArrayList<>(48);
        for (Ship source : ctx.ships) {
            if (!isOperational(source)) continue;
            if (!source.role.isTitanOrMothership()) continue;
            applyTitanField(ctx, source, dt, nearby);
        }
    }

    private static void applyTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        switch (source.role) {
            case TRANSPORT_TITAN -> applyTransportTitanField(ctx, source, dt, nearby);
            case BULWARK_TITAN -> applyBulwarkTitanField(ctx, source, dt, nearby);
            case CARRIER_SUPPORT_TITAN -> applyCarrierSupportTitanField(ctx, source, dt, nearby);
            case VANGUARD_TITAN -> applyVanguardTitanField(ctx, source, dt, nearby);
            case INTERDICTION_TITAN -> applyInterdictionTitanField(ctx, source, dt, nearby);
            case COMMAND_INTEL_TITAN -> applyCommandIntelTitanField(ctx, source, dt, nearby);
            case BOARDING_RECOVERY_TITAN -> applyBoardingRecoveryTitanField(ctx, source, dt, nearby);
            case ARTILLERY_TITAN -> applyArtilleryTitanField(ctx, source, dt, nearby);
            case SHIELD_BASTION_TITAN -> applyShieldBastionTitanField(ctx, source, dt, nearby);
            case FLEET_TELEPORTER_TITAN -> applyFleetTeleporterTitanField(ctx, source, dt, nearby);
            case ELITE_SUPERSHIP_COMMAND_TITAN -> applyEliteSupershipCommandField(ctx, source, dt, nearby);
            case MOBILE_STATION_TITAN -> applyMobileStationTitanField(ctx, source, dt, nearby);
            case HYPERWEAPON_TITAN -> applyHyperweaponTitanField(ctx, source, dt, nearby);
            case MOTHERSHIP -> applyMothershipField(ctx, source, dt, nearby);
            default -> {
            }
        }
    }

    private static void applyTransportTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, Math.max(600.0, source.repairRange), nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            applySupportPackage(ship, dt, 6.4, 18.0, 0.028, 0.15);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WARP_CHARGE, 1.14);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SHIELD_REGEN, 1.16);
            if (ship.role == ShipRole.TRANSPORT || ship.role == ShipRole.MINER || ship.role == ShipRole.HAULER) {
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD, 1.18);
            }
        }
    }

    private static void applyBulwarkTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, Math.max(420.0, source.repairRange), nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            ship.healShield(11.5 * dt);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SHIELD_REGEN, 1.16);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.CIWS_RANGE, 1.12);
            if (ship.role.isCapitalCombatant() || ship.role.isCarrierHull() || ship.role.isMothership()) {
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_DAMAGE, 1.08);
            }
            if (ship.role.isMothership()) {
                applySupportPackage(ship, dt, 0.8, 4.0, 0.008, 0.06);
            }
        }
    }

    private static void applyCarrierSupportTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, Math.max(540.0, source.repairRange), nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.STRIKE_CRAFT, 1.35);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD, 1.14);
            if (ship.isCarrier || ship.isSmallCraft()) {
                applySupportPackage(ship, dt, 3.0, 10.0, 0.024, 0.12);
                rearmStrikeCraftIfDocked(source, ship);
            }
            if (ship.isCarrier) {
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_CYCLE, 1.08);
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SHIELD_REGEN, 1.10);
            }
        }
    }

    private static void applyVanguardTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 460.0, nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.MOBILITY, 1.24);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_DAMAGE, 1.18);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_CYCLE, 1.10);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WARP_CHARGE, 1.10);
            if (!ship.isSmallCraft()) {
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.MISSILE_DAMAGE, 1.14);
            }
        }
    }

    private static void applyInterdictionTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        double fieldRange = 760.0;
        collectNearby(ctx, source, fieldRange, nearby);
        for (Ship ship : nearby) {
            if (sameTeam(source, ship)) {
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SENSOR_RANGE, 1.10);
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.MISSILE_DAMAGE, 1.12);
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.CIWS_RANGE, 1.10);
                continue;
            }
            ship.applyDestabilized(0.95);
            ship.drainShieldByAmount(8.0 * dt, source.x, source.y, ship.x - source.x, ship.y - source.y);
            if (ship.isWarpCharging()) {
                ship.applyTemporaryDisable(0.20);
            }
        }
        disruptHostileMissiles(ctx, source, fieldRange, dt);
    }

    private static void applyCommandIntelTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 560.0, nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            ship.healShield(3.5 * dt);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SENSOR_RANGE, 1.20);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_CYCLE, 1.12);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.MISSILE_CYCLE, 1.10);
        }
    }

    private static void applyBoardingRecoveryTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 470.0, nearby);
        for (Ship ship : nearby) {
            if (sameTeam(source, ship)) {
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD, 1.12);
                ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WARP_CHARGE, 1.10);
                if (ship.isSmallCraft()) {
                    ship.healHull(1.4 * dt);
                }
                continue;
            }
            ship.applyDestabilized(0.75);
            if (shouldBoardingPulse(source, ship) && consumeBoardingPulse(source, ship)) {
                int rooms = ship.role.isTitanOrMothership() ? 4 : (ship.role.isCapitalCombatant() ? 3 : 2);
                ship.applyRoomDisruption(source.x, source.y, source.vx, source.vy, rooms);
                ship.addTemporaryDisable(0.18);
            }
        }
    }

    private static void applyArtilleryTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 700.0, nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            if (ship.isSmallCraft()) continue;
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SENSOR_RANGE, 1.18);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_DAMAGE, 1.22);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_CYCLE, 1.12);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPERWEAPON_RECHARGE, 1.10);
        }
    }

    private static void applyShieldBastionTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, Math.max(680.0, source.repairRange), nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            applySupportPackage(ship, dt, 1.2, 22.0, 0.014, 0.10);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SHIELD_REGEN, 1.36);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.CIWS_RANGE, 1.16);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD, 1.12);
        }
    }

    private static void applyFleetTeleporterTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 490.0, nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            ship.healShield(5.5 * dt);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WARP_CHARGE, 1.28);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.MOBILITY, 1.10);
        }
    }

    private static void applyEliteSupershipCommandField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 520.0, nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            if (!isEliteCommandTarget(ship)) continue;
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_DAMAGE, 1.18);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_CYCLE, 1.10);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.MISSILE_CYCLE, 1.12);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SENSOR_RANGE, 1.10);
        }
    }

    private static void applyMobileStationTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, Math.max(580.0, source.repairRange), nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            applySupportPackage(ship, dt, 3.6, 12.0, 0.030, 0.12);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD, 1.20);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.STRIKE_CRAFT, 1.14);
            if (ship.isCarrier || ship.isSmallCraft()) {
                rearmStrikeCraftIfDocked(source, ship);
            }
        }
    }

    private static void applyHyperweaponTitanField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, 360.0, nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship) || ship == source) continue;
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SENSOR_RANGE, 1.08);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.CIWS_RANGE, 1.10);
        }
    }

    private static void applyMothershipField(GameContext ctx, Ship source, double dt, ArrayList<Ship> nearby) {
        collectNearby(ctx, source, Math.max(640.0, source.repairRange + 60.0), nearby);
        for (Ship ship : nearby) {
            if (!sameTeam(source, ship)) continue;
            applySupportPackage(ship, dt, 2.6, 11.0, 0.020, 0.10);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SUPPORT_FIELD, 1.12);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.STRIKE_CRAFT, 1.12);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.SHIELD_REGEN, 1.14);
            ship.applyCommandStatMultiplier(ShipIdentityRegistry.IdentityStat.WEAPON_DAMAGE, 1.08);
            if (ship.isCarrier || ship.isSmallCraft()) {
                rearmStrikeCraftIfDocked(source, ship);
            }
        }
    }

    private static void collectNearby(GameContext ctx, Ship source, double range, ArrayList<Ship> out) {
        out.clear();
        if (ctx == null || source == null) return;
        ctx.entityQuery.collectAliveShipsNear(source.x, source.y, Math.max(0.0, range), out);
    }

    private static boolean isOperational(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0 && ship.faction != null;
    }

    private static boolean sameTeam(Ship a, Ship b) {
        if (a == null || b == null || a.faction == null || b.faction == null) return false;
        return a.faction.teamId() == b.faction.teamId();
    }

    private static void applySupportPackage(Ship ship, double dt, double hullPerSec, double shieldPerSec,
                                            double roomHealFracPerSec, double fireReductionFracPerSec) {
        if (!isOperational(ship)) return;
        if (hullPerSec > 0.0) ship.healHull(hullPerSec * dt);
        if (shieldPerSec > 0.0) ship.healShield(shieldPerSec * dt);
        if (roomHealFracPerSec > 0.0 || fireReductionFracPerSec > 0.0) {
            ship.applySupportField(roomHealFracPerSec, fireReductionFracPerSec, dt);
        }
    }

    private static void rearmStrikeCraftIfDocked(Ship source, Ship ship) {
        if (source == null || ship == null) return;
        if (!ship.isSmallCraft() || !ship.needsStrikeCraftRearm()) return;
        double serviceRange = Math.max(120.0, source.radius + ship.radius + 110.0);
        if (GameMath.dist2(source.x, source.y, ship.x, ship.y) > serviceRange * serviceRange) return;
        ship.reloadStrikeCraftMunitions();
    }

    private static boolean isEliteCommandTarget(Ship ship) {
        if (ship == null || ship.role == null) return false;
        return switch (ship.role) {
            case SUPERSHIP, BATTLECRUISER, BATTLESHIP, DREADNOUGHT, MOTHERSHIP -> true;
            default -> ship.role.isTitan();
        };
    }

    private static boolean shouldBoardingPulse(Ship source, Ship target) {
        if (!isOperational(source) || !isOperational(target)) return false;
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) return false;
        double closeRange = Math.max(300.0, source.radius + target.radius + 170.0);
        if (GameMath.dist2(source.x, source.y, target.x, target.y) > closeRange * closeRange) return false;
        boolean shieldBroken = !target.isShieldOnline() || target.shield <= Math.max(4.0, target.shieldMax * 0.18);
        boolean hullOpen = target.hp <= Math.max(1, (int) Math.round(target.hpMax * 0.68));
        return shieldBroken || hullOpen;
    }

    private static boolean consumeBoardingPulse(Ship source, Ship target) {
        long key = (((long) source.id) << 32) ^ (target.id & 0xffffffffL);
        double cooldown = BOARDING_PULSE_COOLDOWNS.getOrDefault(key, 0.0);
        if (cooldown > 1e-6) return false;
        BOARDING_PULSE_COOLDOWNS.put(key, BOARDING_PULSE_SECONDS);
        return true;
    }

    private static void decayCooldowns(Map<Long, Double> cooldowns, double dt) {
        if (cooldowns.isEmpty() || dt <= 0.0) return;
        Iterator<Map.Entry<Long, Double>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Double> entry = it.next();
            double next = entry.getValue() - dt;
            if (next <= 1e-6) it.remove();
            else entry.setValue(next);
        }
    }

    private static void disruptHostileMissiles(GameContext ctx, Ship source, double range, double dt) {
        if (ctx == null || source == null || source.faction == null || ctx.projectiles == null) return;
        double rr = Math.max(0.0, range);
        double rr2 = rr * rr;
        for (Projectile projectile : ctx.projectiles) {
            if (!(projectile instanceof Missile missile) || !missile.alive) continue;
            if (missile.faction == null || source.faction.isFriendlyTo(missile.faction)) continue;
            if (GameMath.dist2(source.x, source.y, missile.x, missile.y) > rr2) continue;
            missile.speed = Math.max(150.0, missile.speed - 260.0 * dt);
            missile.turnRate = Math.max(Math.toRadians(70.0), missile.turnRate - Math.toRadians(260.0) * dt);
            missile.interceptHp = Math.max(1, Math.min(missile.interceptHp, Missile.BASE_INTERCEPT_HP));
            double scramble = Math.sin(source.id * 0.31 + missile.x * 0.012 + missile.y * 0.017) * 0.05;
            missile.angle = MathUtil.normalizeAngle(missile.angle + scramble);
        }
    }
}
