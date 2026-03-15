/**
 * Maps factions to doctrine profiles.
 *
 * Resource Rush / four-team mapping:
 *  - PLAYER + ALLY => ENERGY_NAVY
 *  - ENEMY         => KINETIC_CONSORTIUM
 *  - TEAM_C        => AEGIS_LATTICE (directed energy)
 *  - TEAM_D        => VIPER_BARRAGE (missile-forward)
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
     *  - Energy Navy: slightly more shields, slightly less hull.
     *  - Kinetic Consortium: slightly more hull, slightly less shields.
     *  - Missile Barrage: moderate hull with reduced shields.
     */
    public static void applyToShip(Ship s) {
        if (s == null) return;
        if (APPLIED.containsKey(s)) return;
        APPLIED.put(s, Boolean.TRUE);

        DoctrineProfile p = forFaction(s.faction);

        // --- 5C: Hull/Shield emphasis (small but noticeable) ---
        double hullMult;
        double shieldMult;
        if (p.doctrine == Doctrine.ENERGY_NAVY) {
            hullMult = 0.95;
            shieldMult = 1.10;
        } else if (p.doctrine == Doctrine.MISSILE_BARRAGE) {
            hullMult = 1.06;
            shieldMult = 0.90;
        } else {
            hullMult = 1.10;
            shieldMult = 0.95;
        }

        s.hpMax = Math.max(1, (int) Math.round(s.hpMax * hullMult));
        s.shieldMax = Math.max(0.0, s.shieldMax * shieldMult);

        // Spawn ships "fresh" at their new max values.
        s.hp = s.hpMax;
        s.shield = s.shieldMax;
        if (s.shieldMax > 0) s.shieldActive = true;

        // TEAM_D doctrine: make primary batteries torpedo-centric.
        if (p.doctrine == Doctrine.MISSILE_BARRAGE) {
            convertPrimaryGunsToTorpedoes(s);
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
                        t.cooldown = Math.max(0.35, t.cooldown * 0.76);
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

    private static void convertPrimaryGunsToTorpedoes(Ship s) {
        if (s == null || s.turrets == null || s.turrets.isEmpty()) return;

        int converted = 0;
        for (int i = 0; i < s.turrets.size(); i++) {
            Turret gun = s.turrets.get(i);
            if (gun == null || gun.kind != Turret.Kind.GUN || !gun.primary) continue;

            Turret rack = convertGunToTorpedoRack(gun);
            rack.primary = true;

            s.turrets.set(i, rack);
            converted++;
        }

        // Fallback: if a hull somehow has no primary guns, convert the first gun.
        if (converted > 0) return;
        for (int i = 0; i < s.turrets.size(); i++) {
            Turret gun = s.turrets.get(i);
            if (gun == null || gun.kind != Turret.Kind.GUN) continue;
            Turret rack = convertGunToTorpedoRack(gun);
            rack.primary = true;
            s.turrets.set(i, rack);
            return;
        }
    }

    private static Turret convertGunToTorpedoRack(Turret gun) {
        Turret rack = new Turret(Turret.Kind.MISSILE, gun.localX, gun.localY);
        rack.primary = gun.primary;
        rack.turnRate = gun.turnRate;
        rack.cooldown = Math.max(0.46, gun.cooldown * 1.95);
        rack.damage = Math.max(6, (int) Math.round(gun.damage * 4.2));
        rack.missileSpeed = MathUtil.clamp(gun.bulletSpeed * 0.60, 340.0, 660.0); // fast torpedo profile
        rack.missileTurnRate = 0.0; // unguided torpedoes for all TEAM_D primaries
        rack.missileLife = Math.max(200, (int) Math.round(gun.bulletLife * 2.2));
        rack.radius = Math.max(8.5, gun.radius + 2.2);
        rack.barrelLen = Math.max(9.0, gun.barrelLen * 0.82);
        return rack;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
