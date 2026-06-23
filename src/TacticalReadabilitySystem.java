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

    public static List<String> tacticalCrisisWarningLines(GameContext ctx) {
        if (ctx == null) return List.of("CRISIS  |  tactical warning net unavailable");
        java.util.LinkedHashSet<String> warnings = new java.util.LinkedHashSet<>();
        Ship flagship = ctx.player;
        if (isAlive(flagship)) {
            double hull = hullFrac(flagship);
            double shield = shieldFrac(flagship);
            if (hull <= 0.38 || (hull + shield) <= 0.62) {
                warnings.add("CRITICAL  |  flagship in danger  |  form screen, retreat, or repair");
            }
            if (hull <= 0.24) {
                warnings.add("CRITICAL  |  mothership hull critical  |  break contact immediately");
            }
            if (isIsolatedCapital(ctx, flagship)) {
                warnings.add("WARNING  |  player capital ship isolated  |  order escorts to form up");
            }
            if (flagship.isTemporarilyDisabled() || flagship.isStasisFieldTrapped() || flagship.isDestabilized()) {
                warnings.add("CRITICAL  |  command link failing  |  flagship cannot maneuver cleanly");
            }
            if (flagship.hasActiveFireHazards()) {
                warnings.add("WARNING  |  ship burning  |  assign damage control or disengage");
            }
        }
        CampaignSystem.CampaignState campaign = ctx.campaign;
        if (campaign != null) {
            if (campaign.campaignAmmo <= 16) {
                warnings.add("WARNING  |  missile ammo low  |  conserve strikes and seek resupply");
            }
            if (campaign.campaignAmmo <= 8) {
                warnings.add("CRITICAL  |  point defense ammo collapse  |  avoid missile-heavy contacts");
            }
            if (campaign.fleetStrain >= 82.0) {
                warnings.add("CRITICAL  |  repair capacity exhausted  |  open repairs before next battle");
            }
            if (campaign.retreatCorridorObjectiveActive) {
                int progress = (int) Math.round(MathUtil.clamp(campaign.retreatCorridorProgressSec
                        / Math.max(0.1, campaign.retreatCorridorHoldSec), 0.0, 1.0) * 100.0);
                warnings.add(progress >= 60
                        ? "READY  |  retreat corridor open  |  hold formation and extract"
                        : "WARNING  |  retreat corridor closing  |  reach the extraction lane");
            }
        }
        for (Projectile projectile : ctx.projectiles) {
            if (!(projectile instanceof Missile missile) || !projectile.alive) continue;
            if (missile.faction != null && flagship != null && flagship.faction != null
                    && missile.faction.isFriendlyTo(flagship.faction)) continue;
            if (missile.strikeVisual == Missile.StrikeVisual.ATOMIC) {
                warnings.add("CRITICAL  |  nuclear strike incoming  |  scatter from impact vector");
            } else if (missile.strikeVisual == Missile.StrikeVisual.TORPEDO || missile.damage >= 8) {
                warnings.add("WARNING  |  torpedo strike incoming  |  point defense and evasive burn");
            }
        }
        for (Ship ship : ctx.ships) {
            if (!isAlive(ship) || ship == flagship) continue;
            boolean friendly = flagship != null && flagship.faction != null && ship.faction != null
                    && ship.faction.isFriendlyTo(flagship.faction);
            boolean hostile = flagship != null && flagship.faction != null && ship.faction != null
                    && !ship.faction.isFriendlyTo(flagship.faction);
            if (hostile && isStrikeCraft(ship)) {
                warnings.add("WARNING  |  enemy strike craft inbound  |  tighten point defense screen");
            }
            if (friendly && isCivilianShip(ship) && hostileNear(ctx, ship, 760.0)) {
                warnings.add("WARNING  |  civilian ships under immediate threat  |  intercept attackers");
            }
            if (friendly && !isCivilianShip(ship) && hullFrac(ship) <= 0.36 && hostileNear(ctx, ship, 680.0)) {
                warnings.add("WARNING  |  allied ships under immediate threat  |  cover or order retreat");
            }
            if (friendly && ship.hasActiveFireHazards()) {
                warnings.add("WARNING  |  ship burning  |  " + safeShipName(ship) + " needs damage control");
            }
            if (friendly && (ship.isTemporarilyDisabled() || ship.isStasisFieldTrapped())) {
                warnings.add("WARNING  |  ship disabled  |  " + safeShipName(ship) + " needs cover or extraction");
            }
            if (friendly && ship.isDestabilized()) {
                warnings.add("WARNING  |  ship cannot maneuver  |  " + safeShipName(ship) + " is destabilized");
            }
        }
        if (warnings.isEmpty()) return List.of("STABLE  |  no immediate tactical crisis warnings");
        ArrayList<String> out = new ArrayList<>();
        for (String warning : warnings) {
            out.add(decorateCrisisWarning(warning));
            if (out.size() >= 10) break;
        }
        return out;
    }

    private static String decorateCrisisWarning(String warning) {
        if (warning == null || warning.isBlank()) return "WARNING  |  tactical warning  |  icon alert-triangle  |  color amber";
        String lower = warning.toLowerCase(Locale.US);
        if (lower.contains("flagship") || lower.contains("mothership") || lower.contains("command link")) {
            return warning + "  |  icon shield-alert  |  color red";
        }
        if (lower.contains("nuclear") || lower.contains("torpedo") || lower.contains("strike incoming")
                || lower.contains("strike craft")) {
            return warning + "  |  icon incoming-strike  |  color magenta";
        }
        if (lower.contains("ammo")) {
            return warning + "  |  icon ammo-empty  |  color orange";
        }
        if (lower.contains("retreat corridor")) {
            return warning + (warning.startsWith("READY")
                    ? "  |  icon exit-open  |  color green"
                    : "  |  icon exit-closing  |  color amber");
        }
        if (lower.contains("civilian") || lower.contains("allied ships under immediate threat")) {
            return warning + "  |  icon distress-beacon  |  color cyan";
        }
        if (warning.startsWith("CRITICAL")) return warning + "  |  icon alert-octagon  |  color red";
        if (warning.startsWith("READY")) return warning + "  |  icon check-circle  |  color green";
        return warning + "  |  icon alert-triangle  |  color amber";
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

    private static boolean isAlive(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }

    private static double hullFrac(Ship ship) {
        return ship == null ? 0.0 : MathUtil.clamp(ship.hp / (double) Math.max(1, ship.hpMax), 0.0, 1.0);
    }

    private static double shieldFrac(Ship ship) {
        return ship == null ? 0.0 : MathUtil.clamp(ship.shield / Math.max(1.0, ship.shieldMax), 0.0, 1.0);
    }

    private static boolean isIsolatedCapital(GameContext ctx, Ship ship) {
        if (ctx == null || ship == null || !isCapitalShip(ship)) return false;
        int friendlyNearby = 0;
        int hostileNearby = 0;
        for (Ship other : ctx.ships) {
            if (!isAlive(other) || other == ship || other.faction == null || ship.faction == null) continue;
            double d = Math.hypot(other.x - ship.x, other.y - ship.y);
            if (other.faction.isFriendlyTo(ship.faction) && d <= 620.0) friendlyNearby++;
            if (!other.faction.isFriendlyTo(ship.faction) && d <= 820.0) hostileNearby++;
        }
        return hostileNearby >= 2 && friendlyNearby <= 1;
    }

    private static boolean hostileNear(GameContext ctx, Ship target, double range) {
        if (ctx == null || target == null || target.faction == null) return false;
        for (Ship other : ctx.ships) {
            if (!isAlive(other) || other == target || other.faction == null) continue;
            if (other.faction.isFriendlyTo(target.faction)) continue;
            if (Math.hypot(other.x - target.x, other.y - target.y) <= range) return true;
        }
        return false;
    }

    private static boolean isCapitalShip(Ship ship) {
        if (ship == null || ship.role == null) return false;
        String role = ship.role.name();
        return role.contains("CRUISER") || role.contains("BATTLE") || role.contains("CARRIER") || role.contains("TITAN");
    }

    private static boolean isStrikeCraft(Ship ship) {
        if (ship == null || ship.role == null) return false;
        String role = ship.role.name();
        return role.contains("FIGHTER") || role.contains("BOMBER") || role.contains("STRIKE");
    }

    private static boolean isCivilianShip(Ship ship) {
        if (ship == null || ship.role == null) return false;
        String role = ship.role.name();
        return role.contains("TRANSPORT") || role.contains("HAULER") || role.contains("MINER");
    }

    private record CombatLogEntry(long timestamp, CombatLogFilter category, String line) {}
}
