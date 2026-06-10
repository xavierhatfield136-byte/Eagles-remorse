import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TacticalReadabilitySystem {
    private TacticalReadabilitySystem() {}

    public enum CombatLogFilter {
        ALL,
        ORDERS,
        DAMAGE,
        KILLS,
        HAZARDS,
        RETREATS
    }

    public static void update(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        int start = MathUtil.clamp(ctx.ui.tacticalReadabilityDamageCursor, 0, ctx.damageEvents.size());
        for (int i = start; i < ctx.damageEvents.size(); i++) {
            DamageEvent event = ctx.damageEvents.get(i);
            for (String line : subsystemFailureCalloutLines(event)) {
                EventSystem.showWorldCallout(ctx, event.worldX, event.worldY, line, new Color(255, 116, 92, 230), 2.0);
            }
        }
        ctx.ui.tacticalReadabilityDamageCursor = ctx.damageEvents.size();
    }

    public static List<String> filteredCombatLogLines(GameContext ctx, CombatLogFilter filter) {
        if (ctx == null) return List.of();
        CombatLogFilter resolved = (filter == null) ? CombatLogFilter.ALL : filter;
        ArrayList<CombatLogEntry> entries = new ArrayList<>();
        for (DamageEvent event : ctx.damageEvents) {
            if (event == null) continue;
            RoomDamageResult result = event.roomDamageResult;
            if (result != null && result != RoomDamageResult.NONE && result.roomLocalHpLoss > 0.0) {
                entries.add(new CombatLogEntry(event.timestamp, CombatLogFilter.DAMAGE,
                        "DAMAGE  |  ship " + event.targetShipId + " " + roomLabel(result.roomId)
                                + " -" + (int) Math.round(result.roomLocalHpLoss)));
            }
            if (result != null && result.subsystemTransitions != null) {
                for (String transition : result.subsystemTransitions) {
                    if (transition == null || transition.isBlank()) continue;
                    CombatLogFilter category = transition.startsWith("hazard:")
                            ? CombatLogFilter.HAZARDS
                            : CombatLogFilter.DAMAGE;
                    entries.add(new CombatLogEntry(event.timestamp, category,
                            (category == CombatLogFilter.HAZARDS ? "HAZARD  |  " : "DAMAGE  |  ")
                                    + transition.replace(':', ' ').replace('_', ' ')));
                }
            }
        }
        for (GameContext.FleetCommMessage message : ctx.fleetCommLog) {
            if (message == null || message.text == null || message.text.isBlank()) continue;
            CombatLogFilter category = message.text.toLowerCase(Locale.US).contains("retreat")
                    ? CombatLogFilter.RETREATS
                    : CombatLogFilter.ORDERS;
            entries.add(new CombatLogEntry(0L, category,
                    (category == CombatLogFilter.RETREATS ? "RETREAT  |  " : "ORDER  |  ")
                            + message.channel + ": " + message.text));
        }
        for (Ship ship : ctx.ships) {
            if (ship == null || ship.alive || ship.hp > 0) continue;
            entries.add(new CombatLogEntry(0L, CombatLogFilter.KILLS,
                    "KILL  |  ship " + ship.id + " " + safeShipName(ship)));
        }
        entries.sort(Comparator.comparingLong(e -> e.timestamp));
        ArrayList<String> out = new ArrayList<>();
        for (CombatLogEntry entry : entries) {
            if (resolved != CombatLogFilter.ALL && entry.category != resolved) continue;
            out.add(entry.line);
        }
        return out;
    }

    public static List<String> afterBattleTimelineLines(GameContext ctx) {
        if (ctx == null) return List.of("Timeline unavailable.");
        ArrayList<CombatLogEntry> entries = new ArrayList<>();
        for (DamageEvent event : ctx.damageEvents) {
            if (event == null || event.roomDamageResult == null || event.roomDamageResult == RoomDamageResult.NONE) continue;
            String room = roomLabel(event.roomDamageResult.roomId);
            entries.add(new CombatLogEntry(event.timestamp, CombatLogFilter.DAMAGE,
                    timestampLabel(event.timestamp) + "  " + room + " hit on ship " + event.targetShipId));
            for (String callout : subsystemFailureCalloutLines(event)) {
                entries.add(new CombatLogEntry(event.timestamp, CombatLogFilter.DAMAGE,
                        timestampLabel(event.timestamp) + "  " + callout));
            }
        }
        if (entries.isEmpty()) return List.of("Timeline empty.");
        entries.sort(Comparator.comparingLong(e -> e.timestamp));
        ArrayList<String> out = new ArrayList<>();
        for (CombatLogEntry entry : entries) out.add(entry.line);
        return out;
    }

    public static String scrubAfterBattleTimeline(GameContext ctx, double fraction) {
        List<String> lines = afterBattleTimelineLines(ctx);
        if (lines.isEmpty()) return "Timeline empty.";
        if (lines.size() == 1) return lines.get(0);
        int idx = (int) Math.round(MathUtil.clamp(fraction, 0.0, 1.0) * (lines.size() - 1));
        return lines.get(idx);
    }

    public static void setCombatLogFilter(GameContext ctx, CombatLogFilter filter) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.combatLogFilter = (filter == null) ? CombatLogFilter.ALL : filter;
    }

    public static void setOptionalSlowTimeEnabled(boolean enabled) {
        DevTools.setTimeScale(enabled ? 0.5 : 1.0);
    }

    public static boolean optionalSlowTimeEnabled() {
        return DevTools.getTimeScale() < 0.999;
    }

    public static List<String> tacticalClarityLegendLines() {
        return List.of(
                "Projectile Color  |  blue/green friendly fire, red hostile fire, amber explosive ordnance",
                "Shield Impacts  |  shield hits bloom as rings before hull sparks",
                "Missile Trails  |  torpedoes and atomic strikes keep longer hot trails than bullets",
                "Beam Roles  |  lances are continuous lines; beam-bolts are chunky moving capsules",
                "Point Defense  |  CIWS pellets and PD lasers are smaller, brighter intercept fire"
        );
    }

    public static String projectileClarityLine(Projectile projectile) {
        if (projectile == null) return "Projectile  |  unknown contact";
        if (projectile instanceof Missile missile) {
            String role = missile.strikeVisual == Missile.StrikeVisual.ATOMIC ? "atomic strike"
                    : (missile.strikeVisual == Missile.StrikeVisual.TORPEDO ? "torpedo" : "guided missile");
            return "Missile Trails  |  " + role + "  |  amber core, hot trail, explosive shield bloom";
        }
        if (projectile instanceof PhaserBeam) {
            return "Beam Roles  |  continuous lance  |  stable line, terminal impact bloom";
        }
        if (projectile instanceof PointDefenseLaser) {
            return "Point Defense  |  PD laser  |  thin bright intercept line";
        }
        if (projectile instanceof CIWSPellet) {
            return "Point Defense  |  CIWS pellet  |  small kinetic tracer";
        }
        if (projectile instanceof EnergyBolt bolt) {
            return bolt.isBeamBolt()
                    ? "Beam Roles  |  beam-bolt capsule  |  cyan-white moving slug"
                    : "Projectile Color  |  energy bolt  |  bright role-colored core";
        }
        return "Projectile Color  |  kinetic round  |  compact tracer";
    }

    private static List<String> subsystemFailureCalloutLines(DamageEvent event) {
        if (event == null || event.roomDamageResult == null || event.roomDamageResult.subsystemTransitions == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String transition : event.roomDamageResult.subsystemTransitions) {
            if (transition == null || !transition.endsWith(":offline")) continue;
            String system = transition.substring(0, transition.indexOf(':'))
                    .replace('_', ' ')
                    .toUpperCase(Locale.US);
            out.add(system + " OFFLINE");
        }
        return out;
    }

    private static String timestampLabel(long timestamp) {
        if (timestamp <= 0L) return "T+00.0";
        return "T+" + String.format(Locale.US, "%.1f", timestamp / 1000.0);
    }

    private static String roomLabel(String roomId) {
        if (roomId == null || roomId.isBlank()) return "room";
        return roomId.toLowerCase(Locale.US).replace('_', ' ');
    }

    private static String safeShipName(Ship ship) {
        if (ship == null || ship.name == null || ship.name.isBlank()) return "";
        return ship.name;
    }

    private record CombatLogEntry(long timestamp, CombatLogFilter category, String line) {}
}
