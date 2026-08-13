/**
 * Maps factions to doctrine profiles.
 *
 * Resource Rush / four-team mapping:
 *  - BLUE   => ENERGY_NAVY
 *  - RED    => KINETIC_CONSORTIUM
 *  - GREEN  => AEGIS_LATTICE (directed energy)
 *  - YELLOW => VIPER_BARRAGE (missile-forward)
 */
public final class DoctrineRegistry {

    private DoctrineRegistry() {}

    // Prevent double-applying doctrine scaling to the same ship.
    private static final java.util.Map<Ship, Boolean> APPLIED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    // Baseline doctrine targets (tweak during balance).
    public static final DoctrineProfile ENERGY_NAVY = new DoctrineProfile(
            Doctrine.ENERGY_NAVY,
            740.0,   // medium speed, visible, with more time-to-read in tactical view
            84,      // heavy hit (frigate baseline)
            1.6,     // shots/sec
            1.05,    // light missile support
            1.05     // medium PD
    );

    public static final DoctrineProfile KINETIC_CONSORTIUM = new DoctrineProfile(
            Doctrine.KINETIC_CONSORTIUM,
            1200.0,  // still fast, but no longer so quick that impacts read like instant traces
            30,      // light hit
            4.2,     // shots/sec
            1.00,    // neutral missile pressure
            1.30     // strong PD
    );

    public static final DoctrineProfile AEGIS_LATTICE = new DoctrineProfile(
            Doctrine.ENERGY_NAVY,
            900.0,   // precision energy emphasis with more readable travel
            78,
            2.0,
            0.82,    // limited missile pressure
            1.08     // moderate PD
    );

    public static final DoctrineProfile VIPER_BARRAGE = new DoctrineProfile(
            Doctrine.MISSILE_BARRAGE,
            980.0,   // backup gun pressure only, kept readable behind the missile game
            22,
            4.4,
            1.65,    // heavy missile pressure
            0.90     // light-to-moderate PD
    );

    public static DoctrineProfile forFaction(Faction faction) {
        if (faction == null) return KINETIC_CONSORTIUM;
        return switch (faction) {
            case PLAYER, ALLY -> ENERGY_NAVY;
            case ENEMY -> KINETIC_CONSORTIUM;
            case TEAM_C -> AEGIS_LATTICE;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> VIPER_BARRAGE;
            case TEAM_E -> ENERGY_NAVY;
        };
    }

    /**
     * Step 5B + 5C: Apply doctrine multipliers to a ship (once).
     *
     * 5B:
     *  - missileStrength scales missile turrets (damage + a touch of speed/turn).
     *  - pdStrength scales CIWS (cooldown, pellets/burst, range, quality).
     *
 * 5C:
 *  - Blue (PLAYER/ALLY): 50% hull, 50% shields.
 *  - Red (ENEMY): 75% hull, 25% shields.
 *  - Green: 25% hull, 75% shields.
 *  - Yellow: 100% hull, no shields, tougher armor, slower brick-like handling.
     */
    public static void applyToShip(Ship s) {
        if (s == null) return;
        if (APPLIED.containsKey(s)) return;
        APPLIED.put(s, Boolean.TRUE);

        DoctrineProfile p = forFaction(s.faction);
        Faction durabilityFaction = (s.faction == null) ? Faction.ENEMY : s.faction;

        // --- 5C: Faction durability split ---
        double totalDurability = Math.max(1.0, s.hpMax + Math.max(0.0, s.shieldMax));
        double hullShare;
        double armorRoomMult = 1.0;
        double shieldStripMult = 1.0;
        switch (durabilityFaction) {
            case PLAYER, ALLY -> hullShare = 0.50;
            case ENEMY -> hullShare = 0.75;
            case TEAM_C -> {
                hullShare = 0.25;
                shieldStripMult = 0.42;
            }
            case TEAM_D -> {
                hullShare = 1.0;
                armorRoomMult = 1.35;
            }
            default -> hullShare = 0.75;
        }

        int newHull = Math.max(1, (int) Math.round(totalDurability * hullShare));
        if (hullShare < 1.0 && totalDurability >= 2.0) {
            newHull = Math.max(1, Math.min(newHull, (int) Math.floor(totalDurability - 1.0)));
        }
        double newShield = Math.max(0.0, totalDurability - newHull);

        s.hpMax = newHull;
        s.shieldMax = newShield;
        s.armorRoomHpMultiplier = armorRoomMult;
        s.shieldStripRoomHpMultiplier = shieldStripMult;
        s.rebuildDefenseStateForCurrentStats();

        if (durabilityFaction != null && durabilityFaction.isYellowLineage()) {
            s.shieldRegen = 0.0;
            s.shieldActive = false;
            s.shield = 0.0;

            double speedMul = missileDoctrineSpeedMultiplier(s);
            s.desiredSpeed = Math.max(0.0, s.desiredSpeed * speedMul);
            s.desiredSpeedBase = Math.max(0.0, s.desiredSpeed);
            s.repairShieldPerSec = 0.0;
        }

        // Spawn ships "fresh" at their new max values.
        s.hp = s.hpMax;
        s.shield = s.shieldMax;
        if (durabilityFaction != null && durabilityFaction.isYellowLineage()) {
            s.shield = 0.0;
            s.shieldActive = false;
        } else if (s.shieldMax > 0) {
            s.shieldActive = true;
        }

        // TEAM_D doctrine: convert the M1/primary battery into lighter guided missiles,
        // while keeping native missile racks as the heavier salvo threat.
        if (p.doctrine == Doctrine.MISSILE_BARRAGE) {
            convertPrimaryGunsToLightMissiles(s);
        }

        // --- 5B: CIWS/PD scaling ---
        double pd = p.pdStrength;
        if (s.hasCIWS) {
            // More PD => shoots more often, slightly further, with better quality.
            s.ciwsCooldown = Math.max(0.03, s.ciwsCooldown / pd);
            s.ciwsRange = s.ciwsRange * (0.90 + 0.10 * pd);
            s.ciwsQuality = clamp01(s.ciwsQuality * (0.95 + 0.05 * pd));
            s.ciwsPelletsPerBurst = Math.max(1, (int) Math.round(s.ciwsPelletsPerBurst * pd));
        }

        // --- 5B: Missile scaling ---
        double ms = p.missileStrength;
        if (s.turrets != null) {
            for (Turret t : s.turrets) {
                if (t == null) continue;
                if (t.kind == Turret.Kind.MISSILE) {
                    t.damage = Math.max(1, (int) Math.round(t.damage * ms));
                    // Subtle: make stronger missiles a touch more capable.
                    t.missileSpeed = t.missileSpeed * (0.92 + 0.08 * ms);
                    t.missileTurnRate = t.missileTurnRate * (0.92 + 0.08 * ms);
                    if (p.doctrine == Doctrine.MISSILE_BARRAGE) {
                        double cooldownScale = t.primary ? 0.92 : 0.76;
                        double cooldownFloor = t.primary ? 0.60 : 0.35;
                        t.cooldown = Math.max(cooldownFloor, t.cooldown * cooldownScale);
                        t.missileLife = Math.max(1, (int) Math.round(t.missileLife * 1.10));
                    }
                } else if (t.kind == Turret.Kind.GUN && p.doctrine == Doctrine.MISSILE_BARRAGE) {
                    // TEAM_D keeps backup guns, but missiles should be the main pressure source.
                    t.cooldown = t.cooldown * 1.16;
                    t.damage = Math.max(1, (int) Math.round(t.damage * 0.84));
                    t.bulletSpeed = t.bulletSpeed * 0.92;
                }
            }
        }

        applyFactionOffenseEqualization(s);
    }

    private static void applyFactionOffenseEqualization(Ship s) {
        if (s == null || s.role == null || s.faction == null || s.turrets == null || s.turrets.isEmpty()) return;
        double multiplier = factionOffenseEqualizationMultiplier(s.faction, s.role);
        if (!Double.isFinite(multiplier) || multiplier <= 1.005) return;
        s.doctrineOffenseDamageMultiplier = Math.max(s.doctrineOffenseDamageMultiplier, multiplier);
    }

    private static double factionOffenseEqualizationMultiplier(Faction faction, ShipRole role) {
        if (faction == null || role == null) return 1.0;
        return switch (faction) {
            case PLAYER, ALLY -> switch (role) {
                case PICKET -> 1.2505;
                case PATROL -> 1.5229;
                case STEALTH_SHIP -> 1.6301;
                case BOMBER -> 1.2668;
                case PD_CRAFT -> 1.3193;
                case DRONE -> 2.4928;
                case FRIGATE -> 1.9254;
                case ARTILLERY_SHIP -> 1.3200;
                case MISSILE_BOAT -> 2.1232;
                case CIWS_CORVETTE -> 1.3932;
                case LIGHT_CRUISER -> 1.1765;
                case MEDIUM_CRUISER -> 1.3539;
                case CRUISER -> 2.1751;
                case BATTLECRUISER -> 1.1185;
                case BATTLESHIP -> 1.3796;
                case DREADNOUGHT -> 1.0189;
                case SUPERSHIP -> 1.1797;
                case TRANSPORT_TITAN -> 1.3891;
                case BULWARK_TITAN -> 1.2071;
                case CARRIER_SUPPORT_TITAN -> 1.5014;
                case VANGUARD_TITAN -> 1.1512;
                case INTERDICTION_TITAN -> 1.3897;
                case COMMAND_INTEL_TITAN -> 1.1281;
                case BOARDING_RECOVERY_TITAN -> 1.1618;
                case ARTILLERY_TITAN -> 1.3128;
                case SHIELD_BASTION_TITAN -> 1.3555;
                case FLEET_TELEPORTER_TITAN -> 1.2313;
                case MOBILE_STATION_TITAN -> 1.0693;
                case HYPERWEAPON_TITAN -> 1.2000;
                case MOTHERSHIP -> 1.3141;
                case CARRIER -> 2.2065;
                case DRONE_CARRIER -> 1.8545;
                case TRANSPORT -> 2.1060;
                case MINER -> 2.1060;
                case HAULER -> 2.2788;
                default -> 1.0;
            };
            case ENEMY -> switch (role) {
                case PICKET -> 1.1277;
                case STEALTH_SHIP -> 1.6450;
                case FIGHTER -> 1.1417;
                case BOMBER -> 1.3201;
                case PD_CRAFT -> 1.3224;
                case DRONE -> 2.2886;
                case FRIGATE -> 1.4902;
                case ARTILLERY_SHIP -> 1.2446;
                case MISSILE_BOAT -> 2.4872;
                case LIGHT_CRUISER -> 1.3328;
                case MEDIUM_CRUISER -> 1.2882;
                case CRUISER -> 2.4214;
                case BATTLECRUISER -> 1.1300;
                case BATTLESHIP -> 1.5678;
                case DREADNOUGHT -> 1.0816;
                case SUPERSHIP -> 1.2755;
                case TRANSPORT_TITAN -> 1.4477;
                case BULWARK_TITAN -> 1.2746;
                case CARRIER_SUPPORT_TITAN -> 1.7385;
                case VANGUARD_TITAN -> 1.1057;
                case INTERDICTION_TITAN -> 1.5945;
                case COMMAND_INTEL_TITAN -> 1.0194;
                case BOARDING_RECOVERY_TITAN -> 1.0911;
                case ARTILLERY_TITAN -> 1.1924;
                case SHIELD_BASTION_TITAN -> 1.4351;
                case FLEET_TELEPORTER_TITAN -> 1.3985;
                case ELITE_SUPERSHIP_COMMAND_TITAN -> 1.2959;
                case ELITE_REINFORCEMENTS_TITAN -> 1.2931;
                case MOBILE_STATION_TITAN -> 1.1817;
                case HYPERWEAPON_TITAN -> 1.1253;
                case MOTHERSHIP -> 1.3511;
                case CARRIER -> 2.2769;
                case DRONE_CARRIER -> 2.1016;
                case TRANSPORT -> 2.6420;
                case MINER -> 2.2833;
                case HAULER -> 1.9806;
                default -> 1.0;
            };
            case TEAM_C -> switch (role) {
                case PICKET -> 1.0640;
                case PATROL -> 1.2539;
                case STEALTH_SHIP -> 1.7347;
                case FIGHTER -> 1.0056;
                case DRONE -> 2.5580;
                case FRIGATE -> 1.6427;
                case ARTILLERY_SHIP -> 1.0328;
                case MISSILE_BOAT -> 2.7264;
                case CIWS_CORVETTE -> 1.1004;
                case LIGHT_CRUISER -> 1.1685;
                case MEDIUM_CRUISER -> 1.2357;
                case CRUISER -> 2.5229;
                case BATTLECRUISER -> 1.1059;
                case BATTLESHIP -> 1.4583;
                case DREADNOUGHT -> 1.0207;
                case SUPERSHIP -> 1.1626;
                case TRANSPORT_TITAN -> 1.5471;
                case CARRIER_SUPPORT_TITAN -> 1.8146;
                case VANGUARD_TITAN -> 1.2820;
                case INTERDICTION_TITAN -> 1.5162;
                case COMMAND_INTEL_TITAN -> 1.1637;
                case BOARDING_RECOVERY_TITAN -> 1.2071;
                case ARTILLERY_TITAN -> 1.1833;
                case SHIELD_BASTION_TITAN -> 1.3684;
                case FLEET_TELEPORTER_TITAN -> 1.3597;
                case ELITE_SUPERSHIP_COMMAND_TITAN -> 1.1251;
                case ELITE_REINFORCEMENTS_TITAN -> 1.1227;
                case MOBILE_STATION_TITAN -> 1.1320;
                case MOTHERSHIP -> 1.3129;
                case CARRIER -> 1.8700;
                case DRONE_CARRIER -> 1.6066;
                case TRANSPORT -> 1.6602;
                case MINER -> 1.6602;
                case HAULER -> 1.6250;
                default -> 1.0;
            };
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> switch (role) {
                case PICKET -> 2.2564;
                case PATROL -> 2.0247;
                case FIGHTER -> 4.4613;
                case BOMBER -> 1.2459;
                case PD_CRAFT -> 1.5549;
                case STEALTH_SHIP -> 1.0305;
                case ARTILLERY_SHIP -> 1.4966;
                case MISSILE_BOAT -> 1.0178;
                case CIWS_CORVETTE -> 1.2410;
                case FRIGATE -> 1.0267;
                case BATTLECRUISER -> 1.1926;
                case BATTLESHIP -> 1.0524;
                case DREADNOUGHT -> 1.0084;
                case BULWARK_TITAN -> 1.1585;
                case ELITE_SUPERSHIP_COMMAND_TITAN -> 1.0722;
                case ELITE_REINFORCEMENTS_TITAN -> 1.0699;
                case HYPERWEAPON_TITAN -> 1.0500;
                case CARRIER -> 1.0504;
                case DRONE_CARRIER -> 1.0160;
                case TRANSPORT -> 1.0446;
                case MINER -> 1.0446;
                case HAULER -> 1.0256;
                default -> 1.0;
            };
            case TEAM_E -> 1.0;
        };
    }

    private static void convertPrimaryGunsToLightMissiles(Ship s) {
        if (s == null || s.turrets == null || s.turrets.isEmpty()) return;

        int converted = 0;
        for (int i = 0; i < s.turrets.size(); i++) {
            Turret gun = s.turrets.get(i);
            if (gun == null || gun.kind != Turret.Kind.GUN || !gun.primary) continue;

            Turret rack = convertGunToLightMissileRack(gun);
            rack.primary = true;

            s.turrets.set(i, rack);
            converted++;
        }

        // Fallback: if a hull somehow has no primary guns, convert the first gun.
        if (converted > 0) return;
        for (int i = 0; i < s.turrets.size(); i++) {
            Turret gun = s.turrets.get(i);
            if (gun == null || gun.kind != Turret.Kind.GUN) continue;
            Turret rack = convertGunToLightMissileRack(gun);
            rack.primary = true;
            s.turrets.set(i, rack);
            return;
        }
    }

    private static Turret convertGunToLightMissileRack(Turret gun) {
        Turret rack = new Turret(Turret.Kind.MISSILE, gun.localX, gun.localY);
        rack.primary = gun.primary;
        rack.turnRate = gun.turnRate;
        rack.cooldown = Math.max(0.68, gun.cooldown * 1.65);
        rack.damage = Math.max(1, (int) Math.round(Math.max(1.0, gun.damage * 1.95) * 0.25));
        rack.missileSpeed = MathUtil.clamp(gun.bulletSpeed * 0.92, 520.0, 960.0);
        rack.missileTurnRate = Math.toRadians(260);
        rack.missileLife = Math.max(170, (int) Math.round(gun.bulletLife * 1.55));
        rack.missileRole = Turret.MissileRole.ANTI_LIGHT;
        rack.radius = Math.max(6.8, gun.radius + 0.8);
        rack.barrelLen = Math.max(10.0, gun.barrelLen * 0.92);
        return rack;
    }

    private static double missileDoctrineSpeedMultiplier(Ship s) {
        if (s == null || s.role == null) return 0.78;
        return switch (s.role) {
            case FIGHTER, DRONE -> 0.84;
            case BOMBER, STEALTH_SHIP, PATROL, PICKET, PD_CRAFT, FRIGATE, CIWS_CORVETTE -> 0.80;
            case ARTILLERY_SHIP, MISSILE_BOAT, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, MINER, HAULER, TRANSPORT -> 0.76;
            case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP,
                 TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                 INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                   ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                   MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                   MOTHERSHIP,
                 CARRIER, DRONE_CARRIER -> 0.72;
            case BASE, STATIC_TURRET -> 1.0;
        };
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
