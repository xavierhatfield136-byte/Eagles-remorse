import app.config.GameConfig;
import app.config.GameMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4 sanity harness:
 * - validates tactical tradeoffs across attack/defense/pursuit archetypes
 * - validates overload lifecycle
 * - validates manual engineering commands override automation
 */
public final class Phase4SystemsHarness {
    private Phase4SystemsHarness() {}

    public static void main(String[] args) {
        boolean strict = false;
        for (String arg : args) {
            if (arg != null && "--strict".equalsIgnoreCase(arg.trim())) strict = true;
        }

        List<String> failures = new ArrayList<>();
        Player p = new Player(ShipRole.FRIGATE, 0.0, 0.0);

        p.setPowerPreset(Ship.PowerPreset.ATTACK);
        p.update(GameContext.DT);
        double atkTac = p.powerBusEffect(Ship.PowerBus.TACTICAL);
        double atkShield = p.powerBusEffect(Ship.PowerBus.SHIELD);
        double atkProp = p.powerBusEffect(Ship.PowerBus.PROPULSION);

        p.setPowerPreset(Ship.PowerPreset.DEFENSE);
        p.update(GameContext.DT);
        double defTac = p.powerBusEffect(Ship.PowerBus.TACTICAL);
        double defShield = p.powerBusEffect(Ship.PowerBus.SHIELD);
        double defProp = p.powerBusEffect(Ship.PowerBus.PROPULSION);

        p.setPowerPreset(Ship.PowerPreset.PURSUIT);
        p.update(GameContext.DT);
        double purTac = p.powerBusEffect(Ship.PowerBus.TACTICAL);
        double purShield = p.powerBusEffect(Ship.PowerBus.SHIELD);
        double purProp = p.powerBusEffect(Ship.PowerBus.PROPULSION);

        if (!(atkTac > defTac + 0.04)) {
            failures.add("attack tactical bus should exceed defense tactical bus");
        }
        if (!(defShield > atkShield + 0.04)) {
            failures.add("defense shield bus should exceed attack shield bus");
        }
        if (!(purProp > atkProp + 0.05 && purProp > defProp + 0.05)) {
            failures.add("pursuit propulsion bus should exceed attack/defense propulsion");
        }
        if (!(purShield < defShield && atkShield < defShield)) {
            failures.add("defense should retain strongest shield routing");
        }

        p.setPowerPreset(Ship.PowerPreset.ATTACK);
        p.setOverloadBus(Ship.PowerBus.TACTICAL);
        p.setOverloadMode(true);
        boolean collapsed = false;
        for (int i = 0; i < 2400; i++) {
            p.update(GameContext.DT);
            if (!p.isOverloadActive() && p.overloadCooldownRemaining() > 0.0) {
                collapsed = true;
                break;
            }
        }
        if (!collapsed) {
            failures.add("overload should collapse into cooldown under sustained load");
        }

        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 12345L, false));
        ctx.player = p;
        ctx.command.engineeringAutomation = true;
        UISystem.adjustPowerAllocation(ctx, 0, 0.05);
        if (ctx.command.engineeringAutomation) failures.add("manual power allocation must disable engineering automation");

        ctx.command.engineeringAutomation = true;
        UISystem.toggleOverloadMode(ctx);
        if (ctx.command.engineeringAutomation) failures.add("manual overload toggle must disable engineering automation");

        ctx.command.engineeringAutomation = true;
        UISystem.cycleEngineeringPriority(ctx, +1);
        if (ctx.command.engineeringAutomation) failures.add("manual repair-priority change must disable engineering automation");

        System.out.println("[phase4] attack bus effects  P=" + fmt(atkProp) + " SH=" + fmt(atkShield) + " T=" + fmt(atkTac));
        System.out.println("[phase4] defense bus effects P=" + fmt(defProp) + " SH=" + fmt(defShield) + " T=" + fmt(defTac));
        System.out.println("[phase4] pursuit bus effects P=" + fmt(purProp) + " SH=" + fmt(purShield) + " T=" + fmt(purTac));
        System.out.println("[phase4] overload collapse=" + (collapsed ? "PASS" : "FAIL")
                + " cooldown=" + fmt(p.overloadCooldownRemaining()));

        if (failures.isEmpty()) {
            System.out.println("[phase4] checks: PASS");
            return;
        }

        System.out.println("[phase4] checks: FAIL");
        for (String failure : failures) {
            System.out.println(" - " + failure);
        }
        if (strict) System.exit(2);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }
}
