import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CustomShipGenerator {
    public static final int GENERATOR_VERSION = 1;

    private CustomShipGenerator() {}

    public static CustomShipDefinition generate(CustomShipGenerationRequest request) {
        CustomShipGenerationRequest req = request == null
                ? new CustomShipGenerationRequest("Custom Ship", "Frigate", CustomHullClass.FRIGATE,
                CustomCombatClassification.LINE, CustomWeaponDoctrine.BALANCED, CustomDefenseBias.BALANCED, 2)
                : request;
        ShipRole template = templateRole(req.hullClass(), req.combatClassification(), req.weaponDoctrine());
        RoleStats.Stats base = RoleStats.get(template);
        double classMul = classDurabilityMultiplier(req.hullClass());
        double classificationMul = classificationDurabilityMultiplier(req.combatClassification());
        double shieldBias = shieldBias(req.defenseBias());
        double armorBias = armorBias(req.defenseBias());

        int hp = Math.max(1, (int) Math.round(base.hpMax * classMul * classificationMul * armorBias));
        double shields = Math.max(0.0, base.shieldMax * classMul * classificationMul * shieldBias);
        double regen = Math.max(0.0, base.shieldRegen * Math.sqrt(Math.max(0.2, shieldBias)));
        double speed = Math.max(20.0, base.desiredSpeed * speedMultiplier(req.hullClass(), req.combatClassification()));
        double radius = Math.max(8.0, base.radius * radiusMultiplier(req.hullClass()));

        return new CustomShipDefinition(
                UUID.randomUUID(),
                req.displayName(),
                req.declaredShipClass(),
                CustomShipDefinition.CURRENT_SCHEMA_VERSION,
                GENERATOR_VERSION,
                "hull.png",
                "thumbnail.png",
                req.hullClass(),
                req.combatClassification(),
                req.weaponDoctrine(),
                req.defenseBias(),
                template,
                radius,
                hp,
                shields,
                regen,
                speed,
                generateWeapons(req, radius),
                roomPreset(req.hullClass(), req.combatClassification())
        );
    }

    private static List<CustomWeaponMount> generateWeapons(CustomShipGenerationRequest req, double radius) {
        int maxSlots = maxWeaponSlots(req.hullClass(), req.combatClassification());
        int slots = MathUtil.clamp(req.weaponCount(), 1, maxSlots);
        double offensiveBudget = offensiveBudget(req.hullClass(), req.combatClassification());
        double budgetPerSlot = Math.max(2.0, offensiveBudget / Math.max(1, slots));
        ArrayList<CustomWeaponMount> weapons = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            Turret.Kind kind = weaponKind(req.weaponDoctrine(), i, slots);
            double[] pos = mountPosition(i, slots);
            double cooldown = cooldownFor(req.weaponDoctrine(), kind, budgetPerSlot);
            int damage = damageFor(req.weaponDoctrine(), kind, budgetPerSlot);
            double speed = projectileSpeedFor(req.weaponDoctrine(), kind, radius);
            double range = rangeFor(req.combatClassification(), kind);
            int life = Math.max(30, (int) Math.ceil(range / Math.max(1.0, speed) / GameContext.DT));
            weapons.add(new CustomWeaponMount(
                    "mount-" + (i + 1),
                    pos[0],
                    pos[1],
                    kind,
                    cooldown,
                    damage,
                    speed,
                    range,
                    life,
                    req.weaponDefinitionId() == null ? null : WeaponDefinitionRef.custom(req.weaponDefinitionId())
            ));
        }
        return weapons;
    }

    private static ShipRole templateRole(CustomHullClass hullClass,
                                         CustomCombatClassification classification,
                                         CustomWeaponDoctrine doctrine) {
        if (classification == CustomCombatClassification.TITAN || hullClass == CustomHullClass.TITAN) {
            return switch (doctrine) {
                case MISSILE -> ShipRole.INTERDICTION_TITAN;
                case POINT_DEFENSE -> ShipRole.SHIELD_BASTION_TITAN;
                case ENERGY -> ShipRole.HYPERWEAPON_TITAN;
                default -> ShipRole.VANGUARD_TITAN;
            };
        }
        if (classification == CustomCombatClassification.CAPITAL || hullClass == CustomHullClass.CAPITAL) {
            return doctrine == CustomWeaponDoctrine.MISSILE ? ShipRole.BATTLESHIP : ShipRole.DREADNOUGHT;
        }
        return switch (hullClass) {
            case SMALL_CRAFT -> ShipRole.FIGHTER;
            case ESCORT -> doctrine == CustomWeaponDoctrine.POINT_DEFENSE ? ShipRole.CIWS_CORVETTE : ShipRole.PICKET;
            case CRUISER -> doctrine == CustomWeaponDoctrine.MISSILE ? ShipRole.MISSILE_BOAT : ShipRole.CRUISER;
            default -> doctrine == CustomWeaponDoctrine.MISSILE ? ShipRole.MISSILE_BOAT : ShipRole.FRIGATE;
        };
    }

    private static Turret.Kind weaponKind(CustomWeaponDoctrine doctrine, int index, int slots) {
        return switch (doctrine) {
            case MISSILE -> (slots > 1 && index == slots - 1) ? Turret.Kind.GUN : Turret.Kind.MISSILE;
            case POINT_DEFENSE, ENERGY, GUNSHIP -> Turret.Kind.GUN;
            case BALANCED -> index % 3 == 2 ? Turret.Kind.MISSILE : Turret.Kind.GUN;
        };
    }

    private static double[] mountPosition(int index, int count) {
        if (count <= 1) return new double[]{0.62, 0.50};
        int row = index / 2;
        boolean top = index % 2 == 0;
        double columns = Math.max(1.0, Math.ceil(count / 2.0));
        double x = 0.34 + (row / Math.max(1.0, columns - 1.0)) * 0.40;
        double yOffset = Math.min(0.30, 0.16 + row * 0.025);
        return new double[]{MathUtil.clamp(x, 0.18, 0.82), MathUtil.clamp(0.50 + (top ? -yOffset : yOffset), 0.16, 0.84)};
    }

    private static int maxWeaponSlots(CustomHullClass hullClass, CustomCombatClassification classification) {
        int classSlots = switch (hullClass) {
            case SMALL_CRAFT -> 2;
            case ESCORT -> 4;
            case FRIGATE -> 6;
            case CRUISER -> 10;
            case CAPITAL -> 14;
            case TITAN -> 20;
        };
        int classificationCap = switch (classification) {
            case PICKET -> 5;
            case LINE -> 10;
            case CAPITAL -> 16;
            case TITAN -> 24;
        };
        return Math.min(classSlots, classificationCap);
    }

    private static double offensiveBudget(CustomHullClass hullClass, CustomCombatClassification classification) {
        double classBudget = switch (hullClass) {
            case SMALL_CRAFT -> 3.0;
            case ESCORT -> 7.0;
            case FRIGATE -> 11.0;
            case CRUISER -> 20.0;
            case CAPITAL -> 36.0;
            case TITAN -> 58.0;
        };
        double classificationMul = switch (classification) {
            case PICKET -> 0.75;
            case LINE -> 1.0;
            case CAPITAL -> 1.28;
            case TITAN -> 1.55;
        };
        return classBudget * classificationMul;
    }

    private static double cooldownFor(CustomWeaponDoctrine doctrine, Turret.Kind kind, double budgetPerSlot) {
        double base = kind == Turret.Kind.MISSILE ? 1.25 : 0.72;
        double doctrineMul = switch (doctrine) {
            case GUNSHIP, ENERGY -> 0.82;
            case POINT_DEFENSE -> 0.62;
            case MISSILE -> kind == Turret.Kind.MISSILE ? 0.92 : 1.05;
            default -> 1.0;
        };
        return Math.max(kind == Turret.Kind.MISSILE ? 0.58 : 0.16, base * doctrineMul / Math.sqrt(Math.max(1.0, budgetPerSlot) / 4.0));
    }

    private static int damageFor(CustomWeaponDoctrine doctrine, Turret.Kind kind, double budgetPerSlot) {
        double base = kind == Turret.Kind.MISSILE ? budgetPerSlot * 1.35 : budgetPerSlot * 0.72;
        if (doctrine == CustomWeaponDoctrine.POINT_DEFENSE) base *= 0.58;
        if (doctrine == CustomWeaponDoctrine.GUNSHIP || doctrine == CustomWeaponDoctrine.ENERGY) base *= 1.12;
        return MathUtil.clamp((int) Math.round(base), 1, kind == Turret.Kind.MISSILE ? 28 : 18);
    }

    private static double projectileSpeedFor(CustomWeaponDoctrine doctrine, Turret.Kind kind, double radius) {
        if (kind == Turret.Kind.MISSILE) return 700.0;
        double base = doctrine == CustomWeaponDoctrine.ENERGY ? 980.0 : 820.0;
        return Math.min(1200.0, base + radius * 2.0);
    }

    private static double rangeFor(CustomCombatClassification classification, Turret.Kind kind) {
        double base = switch (classification) {
            case PICKET -> 900.0;
            case LINE -> 1300.0;
            case CAPITAL -> 1750.0;
            case TITAN -> 2200.0;
        };
        return kind == Turret.Kind.MISSILE ? base * 1.28 : base;
    }

    private static double classDurabilityMultiplier(CustomHullClass hullClass) {
        return switch (hullClass) {
            case SMALL_CRAFT -> 0.85;
            case ESCORT -> 0.95;
            case FRIGATE -> 1.0;
            case CRUISER -> 1.1;
            case CAPITAL -> 1.18;
            case TITAN -> 1.28;
        };
    }

    private static double classificationDurabilityMultiplier(CustomCombatClassification classification) {
        return switch (classification) {
            case PICKET -> 0.86;
            case LINE -> 1.0;
            case CAPITAL -> 1.22;
            case TITAN -> 1.42;
        };
    }

    private static double radiusMultiplier(CustomHullClass hullClass) {
        return switch (hullClass) {
            case SMALL_CRAFT -> 0.9;
            case ESCORT -> 1.0;
            case FRIGATE -> 1.0;
            case CRUISER -> 1.06;
            case CAPITAL -> 1.12;
            case TITAN -> 1.18;
        };
    }

    private static double speedMultiplier(CustomHullClass hullClass, CustomCombatClassification classification) {
        double classMul = switch (hullClass) {
            case SMALL_CRAFT -> 1.10;
            case ESCORT -> 1.06;
            case FRIGATE -> 1.0;
            case CRUISER -> 0.94;
            case CAPITAL -> 0.86;
            case TITAN -> 0.78;
        };
        double classificationMul = classification == CustomCombatClassification.PICKET ? 1.08
                : (classification == CustomCombatClassification.TITAN ? 0.86 : 1.0);
        return classMul * classificationMul;
    }

    private static double shieldBias(CustomDefenseBias bias) {
        return switch (bias) {
            case ARMOR_HEAVY -> 0.62;
            case BALANCED -> 1.0;
            case SHIELD_HEAVY -> 1.34;
        };
    }

    private static double armorBias(CustomDefenseBias bias) {
        return switch (bias) {
            case ARMOR_HEAVY -> 1.34;
            case BALANCED -> 1.0;
            case SHIELD_HEAVY -> 0.78;
        };
    }

    private static String roomPreset(CustomHullClass hullClass, CustomCombatClassification classification) {
        if (classification == CustomCombatClassification.TITAN || hullClass == CustomHullClass.TITAN) return "titan";
        if (classification == CustomCombatClassification.CAPITAL || hullClass == CustomHullClass.CAPITAL) return "capital";
        if (hullClass == CustomHullClass.SMALL_CRAFT || classification == CustomCombatClassification.PICKET) return "small";
        return "standard";
    }
}
