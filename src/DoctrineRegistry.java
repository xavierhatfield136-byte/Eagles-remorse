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
            750.0,   // medium speed, visible
            75,      // heavy hit (frigate baseline)
            2.0,     // shots/sec
            1.05,    // light missile support
            1.05     // medium PD
    );

    public static final DoctrineProfile KINETIC_CONSORTIUM = new DoctrineProfile(
            Doctrine.KINETIC_CONSORTIUM,
            1400.0,  // fast rounds
            25,      // light hit
            6.0,     // shots/sec
            1.00,    // neutral missile pressure
            1.30     // strong PD
    );

    public static final DoctrineProfile AEGIS_LATTICE = new DoctrineProfile(
            Doctrine.ENERGY_NAVY,
            840.0,   // precision energy emphasis
            72,
            2.3,
            0.82,    // limited missile pressure
            1.08     // moderate PD
    );

    public static final DoctrineProfile VIPER_BARRAGE = new DoctrineProfile(
            Doctrine.MISSILE_BARRAGE,
            980.0,   // backup gun pressure only
            20,
            4.8,
            1.65,    // heavy missile pressure
            0.90     // light-to-moderate PD
    );

    public static DoctrineProfile forFaction(Faction faction) {
        if (faction == null) return KINETIC_CONSORTIUM;
        return switch (faction) {
            case PLAYER, ALLY -> ENERGY_NAVY;
            case ENEMY -> KINETIC_CONSORTIUM;
            case TEAM_C -> AEGIS_LATTICE;
            case TEAM_D -> VIPER_BARRAGE;
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

        if (durabilityFaction == Faction.TEAM_D) {
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
        if (durabilityFaction == Faction.TEAM_D) {
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
            case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP, CARRIER, DRONE_CARRIER -> 0.72;
            case BASE, STATIC_TURRET -> 1.0;
        };
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
