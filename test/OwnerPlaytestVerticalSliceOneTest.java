import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestVerticalSliceOneTest {
    @Test
    void greenYellowAndRedPhysicalFleetsHaveExplainableMapProjection() {
        GameContext ctx = campaign(62001L);
        ctx.campaign.strategicOvermapMode = true;

        CampaignSystem.CampaignForceSummary green = forceFor(ctx, Faction.TEAM_C);
        CampaignSystem.CampaignForceSummary yellow = forceFor(ctx, Faction.BRIGHT_YELLOW);
        CampaignSystem.CampaignForceSummary red = forceFor(ctx, Faction.ENEMY);

        assertProjectedWhenObserved(ctx, green);
        assertProjectedWhenObserved(ctx, yellow);
        assertProjectedWhenObserved(ctx, red);

        assertTrue(CampaignSystem.campaignFleetProjectionParityLines(ctx).stream()
                .noneMatch(line -> line.contains("PROJECTION_MISMATCH")));
    }

    @Test
    void normalOverviewShowsAPlainlyLabeledMovingGreenFleet() {
        GameContext ctx = campaign(62005L);
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = 1.85;

        CampaignSystem.CampaignSupportMarker green = CampaignSystem.activeSupportMarkers(ctx).stream()
                .filter(marker -> marker.sourceForceId > 0)
                .filter(marker -> marker.faction == Faction.TEAM_C)
                .filter(marker -> marker.type != CampaignSystem.SupportMarkerType.FORCE_BASE_DEFENSE)
                .filter(marker -> Renderer.shouldDrawSupportMarkerAtZoom(ctx, marker))
                .findFirst().orElse(null);

        assertNotNull(green, "normal overview should include at least one moving Green fleet report");
        assertTrue(green.priority >= 54, "moving Green fleet should survive overview clutter filtering");
    }

    @Test
    void routineTerritoryStateDoesNotMasqueradeAsAnUnlabeledFleetCircle() {
        CampaignSystem.CampaignTerritoryOverlayView routine = territoryView(
                StrategicCampaignExpansionSystem.TerritoryControlState.SECURE,
                StrategicCampaignExpansionSystem.SupplyState.SUPPLIED,
                false, false, false, false);
        CampaignSystem.CampaignTerritoryOverlayView operation = territoryView(
                StrategicCampaignExpansionSystem.TerritoryControlState.CONTESTED,
                StrategicCampaignExpansionSystem.SupplyState.STRAINED,
                false, false, true, false);
        CampaignSystem.CampaignTerritoryOverlayView frontOnly = territoryView(
                StrategicCampaignExpansionSystem.TerritoryControlState.SECURE,
                StrategicCampaignExpansionSystem.SupplyState.SUPPLIED,
                false, true, false, false);

        assertFalse(Renderer.shouldDrawCampaignTerritoryOverlayView(routine));
        assertFalse(Renderer.shouldDrawCampaignTerritoryOverlayView(frontOnly));
        assertTrue(Renderer.shouldDrawCampaignTerritoryOverlayView(operation));
        assertEquals("OPERATION", Renderer.campaignTerritoryOverlayTag(operation));
    }

    @Test
    void approachingARedFleetDuringOpeningGraceCannotSlingshotItAway() {
        GameContext ctx = campaign(62006L);
        CampaignSystem.CampaignForceSummary red = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.faction == Faction.ENEMY)
                .findFirst().orElseThrow();
        ctx.campaign.sectorElapsed = 30.0;
        ctx.campaign.playerGalaxyX = red.x;
        ctx.campaign.playerGalaxyY = red.y;

        CampaignSystem.syncCampaignForceSimulationSeedsForTest(ctx);

        CampaignSystem.CampaignForceSummary after = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.id == red.id)
                .findFirst().orElseThrow();
        assertTrue(Math.hypot(after.x - red.x, after.y - red.y) < 1.0,
                "seed reconciliation must never teleport an existing Red fleet away from the player");
    }

    @Test
    void ambientFactionFleetsRequireAHomeInTheirOwnTerritory() throws Exception {
        GameContext ctx = campaign(62007L);
        for (CampaignSystem.CampaignForceSummary force : CampaignSystem.campaignForceSummaries(ctx)) {
            boolean ambientGreen = force.name.startsWith("Green ") && force.name.endsWith(" Relay Patrol");
            boolean ambientYellow = force.name.startsWith("Yellow ") && force.name.endsWith(" Trade Column");
            if (!ambientGreen && !ambientYellow) continue;
            CampaignSystem.CampaignLocation home = campaignLocationById(ctx, force.homeBaseId);
            assertNotNull(home, force.name + " should have a real same-faction home base");
            if (ambientGreen) assertEquals(Faction.TEAM_C, home.ownerFaction, force.name);
            if (ambientYellow) assertTrue(home.ownerFaction == Faction.BRIGHT_YELLOW
                    || home.ownerFaction == Faction.TEAM_D, force.name);
        }
    }

    @Test
    void removedKnownFleetEmitsOneNamedDisappearanceReason() throws Exception {
        GameContext ctx = campaign(62002L);
        CampaignSystem.CampaignForceSummary force = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(summary -> summary.faction == Faction.ENEMY)
                .findFirst().orElseThrow();
        Object physical = physicalForce(ctx, force.id);
        setField(physical, "visibleToPlayer", true);
        setField(physical, "removalReason", "owner_slice_test_retreat");
        setField(physical, "destroyed", true);

        CampaignSystem.update(ctx, 0.1);

        List<String> events = CampaignSystem.campaignReleaseTelemetryHistory(ctx);
        assertTrue(events.stream().anyMatch(line -> line.contains("campaign.fleet.disappeared")
                && line.contains("forceId=" + force.id)
                && line.contains("owner_slice_test_retreat")));
        assertEquals(1, events.stream().filter(line -> line.contains("campaign.fleet.disappeared")
                && line.contains("forceId=" + force.id)).count());
    }

    @Test
    void lawfulCaptureNamesOperationAttackerDefenderArrivalAndOutcome() throws Exception {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = campaign(62003L);
            CampaignSystem.CampaignLocation origin = CampaignSystem.mainCampaignLocations(ctx).stream()
                    .filter(location -> location.ownerFaction == Faction.ENEMY)
                    .findFirst().orElseThrow();
            CampaignSystem.CampaignLocation target = CampaignSystem.mainCampaignLocations(ctx).stream()
                    .filter(location -> location.ownerFaction != Faction.ENEMY)
                    .max(Comparator.comparingDouble(location -> Math.hypot(
                            location.x - ctx.campaign.playerGalaxyX,
                            location.y - ctx.campaign.playerGalaxyY)))
                    .orElseThrow();
            CampaignSystem.CampaignForceSummary attacker = forceFor(ctx, Faction.ENEMY);
            Faction originalOwner = target.ownerFaction;

            FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                    ctx.campaign.factionAttackCommitments,
                    new FactionAttackCommitmentSystem.Request(Faction.ENEMY, origin.id, target.id,
                            attacker.id, 0.0, 300.0),
                    originalOwner.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
            assertTrue(result.accepted());
            prepareCaptureForce(ctx, attacker.id, result.operationId(), target);
            isolateCapturePressure(ctx, attacker.id);
            assertTrue(FactionAttackCommitmentSystem.setPhase(ctx.campaign.factionAttackCommitments,
                    result.operationId(), FactionAttackCommitmentSystem.Phase.ACTIVE));
            assertTrue(FactionAttackCommitmentSystem.setPhase(ctx.campaign.factionAttackCommitments,
                    result.operationId(), FactionAttackCommitmentSystem.Phase.ASSAULTING));
            assertTrue(FactionAttackCommitmentSystem.setPhase(ctx.campaign.factionAttackCommitments,
                    result.operationId(), FactionAttackCommitmentSystem.Phase.RESOLVING));
            ctx.campaign.sectorElapsed = 30.0;
            setNodeContestProgress(ctx, target.id, -120.0);
            assertTrue(CampaignSystem.committedOwnershipAuthorizedForTest(ctx, Faction.ENEMY, target.id),
                    CampaignSystem.committedOwnershipEvidenceForTest(ctx, Faction.ENEMY, target.id));

            invokeTheaterWarTick(ctx, 4.0);

            assertNotEquals(originalOwner, target.ownerFaction);
            assertEquals(Faction.ENEMY, target.ownerFaction);
            String event = CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                    .filter(line -> line.contains("campaign.ownership.changed") && line.contains("location=" + target.id))
                    .reduce((first, second) -> second).orElseThrow();
            assertTrue(event.contains(result.operationId()));
            assertTrue(event.contains("attacker=" + attacker.id) || event.contains("attacker_" + attacker.id));
            assertTrue(event.contains("defender="));
            assertTrue(event.contains("arrival=RESOLVING") || event.contains("arrival_RESOLVING"));
            assertTrue(event.contains("outcome"));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void captureWithoutARealArrivedFleetRemainsPaused() {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = campaign(62004L);
            CampaignSystem.CampaignLocation target = CampaignSystem.mainCampaignLocations(ctx).stream()
                    .filter(location -> location.ownerFaction != Faction.ENEMY)
                    .findFirst().orElseThrow();
            assertFalse(CampaignSystem.committedOwnershipAuthorizedForTest(ctx, Faction.ENEMY, target.id));
            assertTrue(CampaignSystem.committedOwnershipEvidenceForTest(ctx, Faction.ENEMY, target.id)
                    .contains("no authoritative operation ID"));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private static void assertProjectedWhenObserved(GameContext ctx, CampaignSystem.CampaignForceSummary force) {
        ctx.campaign.playerGalaxyX = force.x;
        ctx.campaign.playerGalaxyY = force.y;
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, force.id,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                ctx.campaign.campaignIntelTick,
                ctx.campaign.campaignIntelTick + 4,
                1.0, force.x, force.y, 0.0);
        String prefix = "FLEET " + force.id + " " + force.faction.name() + " ";
        String line = CampaignSystem.campaignFleetProjectionParityLines(ctx).stream()
                .filter(candidate -> candidate.startsWith(prefix))
                .findFirst().orElseThrow();
        assertTrue(line.contains("PROJECTED"), line);
    }

    private static CampaignSystem.CampaignForceSummary forceFor(GameContext ctx, Faction faction) {
        CampaignSystem.CampaignForceSummary force = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(summary -> summary.faction == faction)
                .findFirst().orElse(null);
        assertNotNull(force, "missing physical " + faction + " fleet");
        return force;
    }

    private static CampaignSystem.CampaignTerritoryOverlayView territoryView(
            StrategicCampaignExpansionSystem.TerritoryControlState control,
            StrategicCampaignExpansionSystem.SupplyState supply,
            boolean source,
            boolean front,
            boolean operation,
            boolean beachhead) {
        return new CampaignSystem.CampaignTerritoryOverlayView(
                "test", "Test Territory", 1000.0, 1000.0, Faction.TEAM_C,
                "green", "solid", control, supply, 0, source, front, operation, beachhead, "test");
    }

    private static CampaignSystem.CampaignLocation campaignLocationById(GameContext ctx, String id) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "campaignLocationById", CampaignSystem.CampaignState.class, String.class);
        method.setAccessible(true);
        return (CampaignSystem.CampaignLocation) method.invoke(null, ctx.campaign, id);
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        SpawnSystem.initWorld(ctx);
        invokeStrategicBootstrap(ctx);
        ctx.campaign.strategicOvermapMode = true;
        CampaignSystem.update(ctx, 0.1);
        return ctx;
    }

    private static void invokeStrategicBootstrap(GameContext ctx) {
        try {
            Method method = CampaignSystem.class.getDeclaredMethod("activateStrategicOvermapLayer",
                    GameContext.class, CampaignSystem.CampaignState.class, String.class);
            method.setAccessible(true);
            method.invoke(null, ctx, ctx.campaign, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object physicalForce(GameContext ctx, int forceId) throws Exception {
        Field forcesField = ctx.campaign.getClass().getField("campaignForces");
        for (Object candidate : (List<?>) forcesField.get(ctx.campaign)) {
            if (intField(candidate, "id") == forceId) return candidate;
        }
        throw new AssertionError("missing physical force " + forceId);
    }

    private static void prepareCaptureForce(GameContext ctx,
                                            int forceId,
                                            String operationId,
                                            CampaignSystem.CampaignLocation target) throws Exception {
        Object force = physicalForce(ctx, forceId);
        setField(force, "assignedOperationId", operationId);
        setField(force, "destinationLocationId", target.id);
        setEnum(force, "mission", "CAPTURE");
        setEnum(force, "state", "MOVING");
        setEnum(force, "missionState", "ARRIVED");
        setField(force, "strength", 100.0);
        setField(force, "readiness", 100.0);
        setField(force, "supply", 100.0);
        setField(force, "x", target.x);
        setField(force, "y", target.y);
    }

    private static void isolateCapturePressure(GameContext ctx, int attackerId) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        for (Object force : (List<?>) field.get(ctx.campaign)) {
            int id = intField(force, "id");
            if (id == attackerId) {
                setField(force, "strength", 100.0);
            } else {
                setField(force, "x", 0.0);
                setField(force, "y", 0.0);
            }
        }
    }

    private static void setNodeContestProgress(GameContext ctx, String locationId, double progress) throws Exception {
        Field nodesField = ctx.campaign.getClass().getField("strategicNodes");
        for (Object node : (List<?>) nodesField.get(ctx.campaign)) {
            Field id = node.getClass().getDeclaredField("locationId");
            id.setAccessible(true);
            if (!locationId.equals(id.get(node))) continue;
            setField(node, "contestProgress", progress);
            return;
        }
        throw new AssertionError("missing strategic node " + locationId);
    }

    private static void invokeTheaterWarTick(GameContext ctx, double seconds) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("runCampaignTheaterWarTick",
                GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, seconds);
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnum(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, Enum.valueOf((Class<? extends Enum>) field.getType(), value));
    }
}
