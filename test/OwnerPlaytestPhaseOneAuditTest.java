import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestPhaseOneAuditTest {

    @Test
    void movingProjectedStrategicContactsMapToOneCanonicalLiveForceId() {
        GameContext ctx = campaign(62101L);
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;

        Map<Integer, CampaignSystem.CampaignForceSummary> forcesById = new HashMap<>();
        for (CampaignSystem.CampaignForceSummary force : CampaignSystem.campaignForceSummaries(ctx)) {
            assertTrue(force.id > 0, "every physical strategic force should have a stable positive id");
            assertFalse(forcesById.containsKey(force.id), "duplicate physical force id " + force.id);
            forcesById.put(force.id, force);
        }
        observeForce(ctx, firstForce(ctx, Faction.TEAM_C));
        observeForce(ctx, firstYellowForce(ctx));
        observeForce(ctx, firstForce(ctx, Faction.ENEMY));

        Set<Integer> projectedForceIds = new HashSet<>();
        for (CampaignSystem.CampaignSupportMarker marker : CampaignSystem.activeSupportMarkers(ctx)) {
            if (marker == null || marker.sourceForceId <= 0) continue;
            CampaignSystem.CampaignForceSummary force = forcesById.get(marker.sourceForceId);
            assertNotNull(force, "projected marker should map to a live CampaignForce id=" + marker.sourceForceId);
            assertTrue(projectedForceIds.add(marker.sourceForceId),
                    "one physical force should not be projected through multiple live marker authorities: " + marker.sourceForceId);
            String subtitle = marker.subtitle.toLowerCase(java.util.Locale.US);
            assertTrue(subtitle.contains("doing:") || subtitle.contains("next:")
                            || subtitle.contains("known") || subtitle.contains("exact")
                            || subtitle.contains("approx") || subtitle.contains("intent")
                            || subtitle.contains("mission"),
                    "projected fleet marker should explain current report/intent: " + marker.label + " / " + marker.subtitle);
        }

        assertTrue(projectedForceIds.stream().anyMatch(id -> forcesById.get(id).faction == Faction.TEAM_C),
                "normal projection should include at least one Green force report");
        assertTrue(projectedForceIds.stream().anyMatch(id -> isYellow(forcesById.get(id).faction)),
                "normal projection should include at least one Yellow force report");
        assertTrue(projectedForceIds.stream().anyMatch(id -> forcesById.get(id).faction == Faction.ENEMY),
                "normal projection should include at least one Red force report");
    }

    @Test
    void offscreenFactionFleetsKeepRoutesAndMoveWithoutPlayerSensorContact() {
        GameContext ctx = campaign(62102L);
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;
        for (int i = 0; i < 30; i++) {
            advanceStrategicTime(ctx, 1.0);
        }

        Map<Integer, double[]> before = new HashMap<>();
        addRoutedForces(ctx, before, Faction.TEAM_C);
        addRoutedYellowForces(ctx, before);
        addRoutedForces(ctx, before, Faction.ENEMY);
        Map<Integer, double[]> previousPositions = new HashMap<>();
        for (int id : before.keySet()) {
            previousPositions.put(id, physicalPosition(ctx, id));
        }

        ctx.campaign.playerGalaxyX = 0.0;
        ctx.campaign.playerGalaxyY = 0.0;
        for (int i = 0; i < 90; i++) {
            advanceStrategicTime(ctx, 1.0);
            for (int id : before.keySet()) {
                double[] previous = previousPositions.get(id);
                double[] current = physicalPosition(ctx, id);
                double step = Math.hypot(current[0] - previous[0], current[1] - previous[1]);
                double speed = Math.max(1.0, physicalDouble(ctx, id, "speed"));
                assertTrue(step <= Math.max(320.0, speed * 1.0 * 2.5),
                        "offscreen update should not create a one-step teleport id=" + id
                                + " step=" + step + " speed=" + speed);
                previousPositions.put(id, current);
            }
        }

        Set<Integer> movedFactions = new HashSet<>();
        StringBuilder movementDebug = new StringBuilder();
        for (int id : before.keySet()) {
            CampaignSystem.CampaignForceSummary after = forceById(ctx, id);
            double[] start = before.get(id);
            double[] physicalAfter = physicalPosition(ctx, id);
            double moved = Math.hypot(physicalAfter[0] - start[0], physicalAfter[1] - start[1]);
            movementDebug.append(" id=").append(id)
                    .append(" faction=").append(after.faction)
                    .append(" kind=").append(after.kind)
                    .append(" moved=").append(Math.round(moved))
                    .append(" speed=").append(Math.round(physicalDouble(ctx, id, "speed")))
                    .append(" linked=").append(Math.round(physicalDouble(ctx, id, "linkedSearchGroupId")))
                    .append(" dest=").append(after.destinationLocationId)
                    .append(" intent=").append(after.intent)
                    .append(" targetDist=").append(Math.round(Math.hypot(after.targetX - after.x, after.targetY - after.y)));
            if (moved > 0.5) movedFactions.add((int) start[2]);
            assertFalse(after.destinationLocationId.isBlank(), "moving offscreen fleet should retain a route destination id=" + id);
        }
        assertTrue(movedFactions.contains(factionCode(Faction.TEAM_C)),
                "at least one routed Green force should keep moving offscreen: elapsed="
                        + ctx.campaign.sectorElapsed
                        + " ticks=" + ctx.campaign.campaignForceSimTickCount
                        + " gameOver=" + ctx.gameOver
                        + " prompt=" + ctx.ui.strategicEncounterPrompt.active
                        + movementDebug);
        assertTrue(movedFactions.contains(factionCode(Faction.BRIGHT_YELLOW)), "at least one routed Yellow force should keep moving offscreen");
        assertTrue(movedFactions.contains(factionCode(Faction.ENEMY)), "at least one routed Red force should keep moving offscreen");
    }

    @Test
    void factionSitesKeepOriginalIdentitySeparateFromCurrentOccupier() {
        GameContext ctx = campaign(62103L);
        CampaignSystem.CampaignLocation yellow = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location.name.toUpperCase(java.util.Locale.US).contains("YELLOW"))
                .findFirst().orElseThrow();
        CampaignSystem.CampaignLocation red = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location.name.toUpperCase(java.util.Locale.US).contains("RED"))
                .findFirst().orElseThrow();

        assertTrue(isYellow(yellow.ownerFaction), "authored Yellow site should start under Yellow control");
        assertTrue(red.ownerFaction == Faction.ENEMY, "authored Red site should start under Red control");
        assertFalse(yellow.name.toUpperCase(java.util.Locale.US).contains("GREEN"),
                "Yellow-authored sites should not instantiate Green station identity");
        assertFalse(red.name.toUpperCase(java.util.Locale.US).contains("GREEN"),
                "Red-authored sites should not instantiate Green station identity");

        Faction originalYellowOwner = yellow.ownerFaction;
        yellow.ownerFaction = Faction.ENEMY;
        CampaignSystem.CampaignLocationControlView occupied = CampaignSystem.campaignLocationControlView(ctx, yellow);

        assertNotEquals(originalYellowOwner, yellow.ownerFaction);
        assertTrue(occupied.status.toUpperCase(java.util.Locale.US).contains("RED")
                        || occupied.status.toUpperCase(java.util.Locale.US).contains("HOSTILE")
                        || occupied.status.toUpperCase(java.util.Locale.US).contains("ENEMY"),
                "captured/occupied state should show current control without renaming original site identity");
        assertTrue(yellow.name.toUpperCase(java.util.Locale.US).contains("YELLOW"),
                "occupation should not rewrite the authored site name/faction identity");
    }

    @Test
    void knownFleetCleanupReasonsAreStructuredForDestroyedRetreatDockMergeAndInvalidProjection() throws Exception {
        for (String reason : List.of("destroyed_after_attrition", "retreated_to_home", "docked_at_site", "merged_with_escort", "invalid_projection")) {
            GameContext ctx = campaign(62104L);
            CampaignSystem.CampaignForceSummary summary = CampaignSystem.campaignForceSummaries(ctx).stream()
                    .filter(force -> force.faction == Faction.ENEMY)
                    .findFirst().orElseThrow();
            Object physical = physicalForce(ctx, summary.id);
            setObject(physical, "visibleToPlayer", true);
            setObject(physical, "removalReason", reason);
            setObject(physical, "destroyed", true);
            CampaignSystem.update(ctx, 0.1);
            assertTrue(CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                            .anyMatch(line -> line.contains("campaign.fleet.disappeared")
                                    && line.contains("forceId=" + summary.id)
                                    && line.contains("reason=" + reason)),
                    "known fleet cleanup should emit structured disappearance reason " + reason);
        }
    }

    private static void addRoutedForces(GameContext ctx, Map<Integer, double[]> out, Faction faction) {
        CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.faction == faction)
                .filter(force -> force.kind != CampaignSystem.CampaignForceKind.PLAYER_FLEET)
                .filter(force -> isIndependentSimulatedForce(ctx, force.id))
                .filter(force -> !force.destinationLocationId.isBlank())
                .filter(force -> Math.hypot(force.targetX - force.x, force.targetY - force.y) > 25.0)
                .forEach(force -> {
                    double[] physical = physicalPosition(ctx, force.id);
                    out.put(force.id, new double[]{physical[0], physical[1], factionCode(force.faction)});
                });
    }

    private static void addRoutedYellowForces(GameContext ctx, Map<Integer, double[]> out) {
        CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> isYellow(force.faction))
                .filter(force -> isIndependentSimulatedForce(ctx, force.id))
                .filter(force -> !force.destinationLocationId.isBlank())
                .filter(force -> Math.hypot(force.targetX - force.x, force.targetY - force.y) > 25.0)
                .forEach(force -> {
                    double[] physical = physicalPosition(ctx, force.id);
                    out.put(force.id, new double[]{physical[0], physical[1], factionCode(force.faction)});
                });
    }

    private static double[] physicalPosition(GameContext ctx, int forceId) {
        try {
            Object physical = physicalForce(ctx, forceId);
            Field x = physical.getClass().getDeclaredField("x");
            Field y = physical.getClass().getDeclaredField("y");
            x.setAccessible(true);
            y.setAccessible(true);
            return new double[]{x.getDouble(physical), y.getDouble(physical)};
        } catch (Exception ex) {
            throw new AssertionError("could not read physical campaign force position " + forceId, ex);
        }
    }

    private static double physicalDouble(GameContext ctx, int forceId, String fieldName) {
        try {
            Object physical = physicalForce(ctx, forceId);
            Field field = physical.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object raw = field.get(physical);
            if (raw instanceof Number number) return number.doubleValue();
            if (raw instanceof Boolean bool) return bool ? 1.0 : 0.0;
            return Double.NaN;
        } catch (Exception ex) {
            throw new AssertionError("could not read physical campaign force field " + forceId + "." + fieldName, ex);
        }
    }

    private static boolean isIndependentSimulatedForce(GameContext ctx, int forceId) {
        try {
            Object physical = physicalForce(ctx, forceId);
            Field linked = physical.getClass().getDeclaredField("linkedSearchGroupId");
            linked.setAccessible(true);
            Field active = physical.getClass().getDeclaredField("simulationActive");
            active.setAccessible(true);
            return linked.getInt(physical) <= 0 && active.getBoolean(physical);
        } catch (Exception ex) {
            throw new AssertionError("could not inspect physical campaign force " + forceId, ex);
        }
    }

    private static CampaignSystem.CampaignForceSummary firstForce(GameContext ctx, Faction faction) {
        return CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.faction == faction)
                .filter(force -> force.kind != CampaignSystem.CampaignForceKind.PLAYER_FLEET)
                .findFirst().orElseThrow();
    }

    private static CampaignSystem.CampaignForceSummary firstYellowForce(GameContext ctx) {
        return CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> isYellow(force.faction))
                .findFirst().orElseThrow();
    }

    private static void observeForce(GameContext ctx, CampaignSystem.CampaignForceSummary force) {
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, force.id,
                CampaignSystem.CampaignIntelObservationSource.ALLIED_REPORT,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                ctx.campaign.campaignIntelTick,
                ctx.campaign.campaignIntelTick + 8,
                1.0,
                force.x,
                force.y,
                0.0);
    }

    private static CampaignSystem.CampaignForceSummary forceById(GameContext ctx, int id) {
        return CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.id == id)
                .findFirst().orElseThrow();
    }

    private static boolean isYellow(Faction faction) {
        return faction == Faction.BRIGHT_YELLOW || faction == Faction.DARK_YELLOW || faction == Faction.TEAM_D;
    }

    private static int factionCode(Faction faction) {
        if (faction == Faction.TEAM_C) return 1;
        if (isYellow(faction)) return 2;
        if (faction == Faction.ENEMY) return 3;
        return 0;
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.state = GameState.FLEET;
        ctx.campaign.strategicOvermapMode = true;
        advanceStrategicTime(ctx, 0.1);
        return ctx;
    }

    private static void advanceStrategicTime(GameContext ctx, double dt) {
        if (ctx != null && ctx.ui != null) {
            ctx.ui.strategicEncounterPrompt.active = false;
            ctx.ui.campaignHubMenu.active = false;
        }
        CampaignSystem.update(ctx, dt);
        if (ctx != null && ctx.ui != null) {
            ctx.ui.strategicEncounterPrompt.active = false;
            ctx.ui.campaignHubMenu.active = false;
        }
    }

    private static Object physicalForce(GameContext ctx, int forceId) throws Exception {
        for (Object candidate : ctx.campaign.campaignForces) {
            Field id = candidate.getClass().getDeclaredField("id");
            id.setAccessible(true);
            if (id.getInt(candidate) == forceId) return candidate;
        }
        throw new AssertionError("missing physical force " + forceId);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
