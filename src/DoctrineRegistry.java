/**
 * Maps factions to doctrine profiles.
 *
 * Default mapping:
 *  - PLAYER + ALLY => ENERGY_NAVY
 *  - ENEMY        => KINETIC_CONSORTIUM
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
            1.30,    // stronger missile pressure (future)
            1.00     // medium PD (future)
    );

    public static final DoctrineProfile KINETIC_CONSORTIUM = new DoctrineProfile(
            Doctrine.KINETIC_CONSORTIUM,
            1400.0,  // fast rounds
            25,      // light hit
            6.0,     // shots/sec
            1.00,    // medium missiles (future)
            1.30     // strong PD (future)
    );

    public static DoctrineProfile forFaction(Faction faction) {
        if (faction == null) return KINETIC_CONSORTIUM;
        if (faction.teamId() == Faction.ALLY.teamId()) return ENERGY_NAVY;
        return KINETIC_CONSORTIUM;
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
                }
            }
        }
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
