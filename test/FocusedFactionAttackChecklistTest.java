import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFactionAttackChecklistTest {
    @Test
    void inertCommitmentModelIsTransactionalAndUsesExplicitFactionSlots() {
        FactionAttackCommitmentSystem.State state = new FactionAttackCommitmentSystem.State();
        assertEquals(FactionAttackCommitmentSystem.Slot.GREEN,
                FactionAttackCommitmentSystem.slotFor(Faction.TEAM_C));
        assertEquals(FactionAttackCommitmentSystem.Slot.GREEN,
                FactionAttackCommitmentSystem.slotFor(Faction.ALLY));
        assertEquals(FactionAttackCommitmentSystem.Slot.YELLOW,
                FactionAttackCommitmentSystem.slotFor(Faction.BRIGHT_YELLOW));
        assertEquals(FactionAttackCommitmentSystem.Slot.DARK_YELLOW,
                FactionAttackCommitmentSystem.slotFor(Faction.DARK_YELLOW));
        assertEquals(FactionAttackCommitmentSystem.Slot.RED,
                FactionAttackCommitmentSystem.slotFor(Faction.ENEMY));

        FactionAttackCommitmentSystem.Request request = new FactionAttackCommitmentSystem.Request(
                Faction.TEAM_C, "green-origin", "yellow-target", 41, 10.0, 180.0);
        FactionAttackCommitmentSystem.Result rejected = FactionAttackCommitmentSystem.request(
                state, request, Faction.BRIGHT_YELLOW.name(), ignored ->
                        FactionAttackCommitmentSystem.Validation.reject("no path"));
        assertFalse(rejected.accepted());
        assertTrue(state.activeCommitments().isEmpty(), "failed validation must leave no partial slot state");
        assertEquals(1, state.nextOperationId(), "failed validation must not consume an operation ID");

        FactionAttackCommitmentSystem.Result accepted = FactionAttackCommitmentSystem.request(
                state, request, Faction.BRIGHT_YELLOW.name(), ignored ->
                        FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(accepted.accepted());
        assertTrue(accepted.created());
        assertEquals("green-origin", accepted.commitment().originLocationId);
        assertEquals("yellow-target", accepted.commitment().targetLocationId);
    }

    @Test
    void oneTargetHoldConflictReleaseAndSaveRoundTripAreSafe() {
        FactionAttackCommitmentSystem.State state = new FactionAttackCommitmentSystem.State();
        FactionAttackCommitmentSystem.Result green = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.TEAM_C, "g-base", "red-site", 7, 0, 120),
                Faction.ENEMY.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(green.accepted());

        FactionAttackCommitmentSystem.Result secondGreen = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.TEAM_C, "g-base", "other-site", 8, 1, 120),
                Faction.ENEMY.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertFalse(secondGreen.accepted());

        FactionAttackCommitmentSystem.Result sameTargetOtherFaction = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.BRIGHT_YELLOW, "y-base", "red-site", 9, 1, 120),
                Faction.ENEMY.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertFalse(sameTargetOtherFaction.accepted());

        assertTrue(FactionAttackCommitmentSystem.setPhase(state, green.operationId(),
                FactionAttackCommitmentSystem.Phase.HOLD));
        assertNotNull(FactionAttackCommitmentSystem.active(state, FactionAttackCommitmentSystem.Slot.GREEN),
                "held commitments still occupy their slot");

        FactionAttackCommitmentSystem.State restored = FactionAttackCommitmentSystem.restore(
                FactionAttackCommitmentSystem.serialize(state));
        FactionAttackCommitmentSystem.Commitment restoredGreen =
                FactionAttackCommitmentSystem.active(restored, FactionAttackCommitmentSystem.Slot.GREEN);
        assertNotNull(restoredGreen);
        assertEquals("g-base", restoredGreen.originLocationId);
        assertEquals("red-site", restoredGreen.targetLocationId);
        assertTrue(restoredGreen.supportingFleetIds.contains(7));

        assertTrue(FactionAttackCommitmentSystem.abort(restored, restoredGreen.operationId, "test abort"));
        assertFalse(FactionAttackCommitmentSystem.abort(restored, restoredGreen.operationId, "double release"));
        assertNull(FactionAttackCommitmentSystem.active(restored, FactionAttackCommitmentSystem.Slot.GREEN));
        assertEquals(1, restored.history().size());
    }

    @Test
    void checkpointStorePreservesStableCommitmentPayload() {
        FactionAttackCommitmentSystem.State state = new FactionAttackCommitmentSystem.State();
        FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(state,
                new FactionAttackCommitmentSystem.Request(Faction.ENEMY,
                        "red-origin-id", "green-target-id", 77, 12, 240),
                Faction.TEAM_C.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
        assertTrue(result.accepted());
        CampaignCheckpointStore.Checkpoint checkpoint = new CampaignCheckpointStore.Checkpoint();
        checkpoint.seed = 61006L;
        checkpoint.factionAttackCommitments = FactionAttackCommitmentSystem.serialize(state);
        try {
            CampaignCheckpointStore.save(checkpoint);
            CampaignCheckpointStore.Checkpoint loaded = CampaignCheckpointStore.load();
            assertNotNull(loaded);
            FactionAttackCommitmentSystem.Commitment restored = FactionAttackCommitmentSystem.active(
                    FactionAttackCommitmentSystem.restore(loaded.factionAttackCommitments),
                    FactionAttackCommitmentSystem.Slot.RED);
            assertNotNull(restored);
            assertEquals("red-origin-id", restored.originLocationId);
            assertEquals("green-target-id", restored.targetLocationId);
        } finally {
            CampaignCheckpointStore.clear();
        }
    }

    @Test
    void gateBEnforcementPreventsUncommittedMinuteOneOwnershipChangesWithoutDeletingRouteData() {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = campaign(61003L);
            ctx.campaign.strategicOvermapMode = true;
            Map<String, String> initial = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
            assertFalse(CampaignSystem.campaignRouteSegments(ctx).isEmpty());
            List<String> yellowLocations = initial.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(Faction.BRIGHT_YELLOW.name()))
                    .map(Map.Entry::getKey).toList();
            assertFalse(yellowLocations.isEmpty());

            for (int elapsed = 0; elapsed < 60; elapsed += 10) CampaignSystem.update(ctx, 10.0);

            Map<String, String> after = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
            for (String id : yellowLocations) {
                assertEquals(Faction.BRIGHT_YELLOW.name(), after.get(id),
                        "uncommitted pressure changed Yellow ownership at " + id);
            }
            assertFalse(CampaignSystem.campaignRouteSegments(ctx).isEmpty(),
                    "focused attacks deleted all underlying route geometry");
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void gateCStableIdRecoveryAbortsAndReleasesExactlyOnce() {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = campaign(61004L);
            FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                    ctx.campaign.factionAttackCommitments,
                    new FactionAttackCommitmentSystem.Request(Faction.ENEMY,
                            "missing-origin", "missing-target", 0, 0, 120),
                    Faction.TEAM_C.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
            assertTrue(result.accepted());

            CampaignSystem.updateFactionAttackCommitmentsForTest(ctx);
            assertNull(FactionAttackCommitmentSystem.active(ctx.campaign.factionAttackCommitments,
                    FactionAttackCommitmentSystem.Slot.RED));
            assertEquals(1, ctx.campaign.factionAttackCommitments.history().size());
            CampaignSystem.updateFactionAttackCommitmentsForTest(ctx);
            assertEquals(1, ctx.campaign.factionAttackCommitments.history().size(),
                    "save recovery attempted to release the slot twice");
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void ownershipBoundaryRequiresResolvingCommitmentAndMinimumDuration() throws Exception {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = campaign(61007L);
            CampaignSystem.CampaignLocation origin = CampaignSystem.mainCampaignLocations(ctx).get(0);
            CampaignSystem.CampaignLocation target = CampaignSystem.mainCampaignLocations(ctx).stream()
                    .filter(location -> location != null && !location.id.equals(origin.id)
                            && location.ownerFaction != Faction.ENEMY)
                    .findFirst().orElseThrow();
            CampaignSystem.CampaignForceSummary attacker = CampaignSystem.campaignForceSummaries(ctx).stream()
                    .filter(force -> force.faction == Faction.ENEMY)
                    .findFirst().orElseThrow();
            assertFalse(CampaignSystem.committedOwnershipAuthorizedForTest(ctx, Faction.ENEMY, target.id));
            FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                    ctx.campaign.factionAttackCommitments,
                    new FactionAttackCommitmentSystem.Request(Faction.ENEMY,
                            origin.id, target.id, attacker.id, 0, 120),
                    target.ownerFaction.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
            assertTrue(result.accepted());
            prepareCaptureForce(ctx, attacker.id, result.operationId(), target);
            assertTrue(FactionAttackCommitmentSystem.setPhase(ctx.campaign.factionAttackCommitments,
                    result.operationId(), FactionAttackCommitmentSystem.Phase.ACTIVE));
            assertTrue(FactionAttackCommitmentSystem.setPhase(ctx.campaign.factionAttackCommitments,
                    result.operationId(), FactionAttackCommitmentSystem.Phase.ASSAULTING));
            assertTrue(FactionAttackCommitmentSystem.setPhase(ctx.campaign.factionAttackCommitments,
                    result.operationId(), FactionAttackCommitmentSystem.Phase.RESOLVING));
            ctx.campaign.sectorElapsed = 19.0;
            assertFalse(CampaignSystem.committedOwnershipAuthorizedForTest(ctx, Faction.ENEMY, target.id));
            ctx.campaign.sectorElapsed = 20.0;
            assertTrue(CampaignSystem.committedOwnershipAuthorizedForTest(ctx, Faction.ENEMY, target.id));
            String evidence = CampaignSystem.committedOwnershipEvidenceForTest(ctx, Faction.ENEMY, target.id);
            assertTrue(evidence.contains(result.operationId()));
            assertTrue(evidence.contains("attacker=" + attacker.id));
            assertTrue(evidence.contains("defender="));
            assertTrue(evidence.contains("arrival=RESOLVING"));
            assertTrue(evidence.contains("outcome"));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void gateDArrowIsCommitmentDerivedLargeHollowAndLeavesMarkerCentersUntouched() {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = campaign(61005L);
            List<CampaignSystem.CampaignLocation> locations = CampaignSystem.mainCampaignLocations(ctx);
            assertTrue(locations.size() >= 2);
            CampaignSystem.CampaignLocation source = locations.get(0);
            CampaignSystem.CampaignLocation target = locations.get(1);
            source.ownerFaction = Faction.ENEMY;
            target.ownerFaction = Faction.TEAM_C;
            FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                    ctx.campaign.factionAttackCommitments,
                    new FactionAttackCommitmentSystem.Request(Faction.ENEMY,
                            source.id, target.id, 0, 0, 120),
                    target.ownerFaction == null ? "NONE" : target.ownerFaction.name(),
                    ignored -> FactionAttackCommitmentSystem.Validation.allow());
            assertTrue(result.accepted());
            CampaignSystem.recordCampaignOperationIntelObservation(ctx, result.operationId(),
                    CampaignSystem.CampaignIntelObservationSource.OPERATION_INTEL,
                    CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY,
                    ctx.campaign.campaignIntelTick, ctx.campaign.campaignIntelTick + 2,
                    0.8, target.x, target.y, 0.0);
            assertEquals(1, CampaignSystem.campaignInvasionArrows(ctx).stream()
                            .filter(arrow -> result.operationId().equals(arrow.forceName))
                            .count(),
                    "commitment arrows must remain identifiable even when live fleet invasion arrows are present");

            Shape outline = Renderer.campaignBubbleArrowOutlineForTest(100, 100, 620, 320);
            assertNotNull(outline);
            assertTrue(outline.getBounds2D().getHeight() >= 40.0);
            BufferedImage image = new BufferedImage(760, 460, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            try {
                Renderer.drawCampaignBubbleArrowForTest(g2, 100, 100, 620, 320, Faction.ENEMY);
            } finally {
                g2.dispose();
            }
            assertEquals(0, alpha(image, 360, 210), "the arrow interior must remain transparent");
            assertEquals(0, alpha(image, 100, 100), "the source marker center must remain untouched");
            assertEquals(0, alpha(image, 620, 320), "the target marker center must remain untouched");
            for (int[] size : new int[][]{{1280, 720}, {1920, 1080}, {2560, 1440}}) {
                BufferedImage scaled = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                Graphics2D scaledGraphics = scaled.createGraphics();
                int sx = size[0] / 5;
                int sy = size[1] / 3;
                int tx = size[0] * 4 / 5;
                int ty = size[1] * 2 / 3;
                try {
                    Renderer.drawCampaignBubbleArrowForTest(scaledGraphics, sx, sy, tx, ty, Faction.BRIGHT_YELLOW);
                } finally {
                    scaledGraphics.dispose();
                }
                assertEquals(0, alpha(scaled, (sx + tx) / 2, (sy + ty) / 2));
                assertEquals(0, alpha(scaled, sx, sy));
                assertEquals(0, alpha(scaled, tx, ty));
            }

            ctx.campaign.strategicOvermapMode = true;
            ctx.ui.mapOpen = true;
            ctx.state = GameState.MAP;
            double midpointX = (source.x + target.x) * 0.5;
            double midpointY = (source.y + target.y) * 0.5;
            ctx.ui.strategicMapFocusX = midpointX;
            ctx.ui.strategicMapFocusY = midpointY;
            UISystem.resetStrategicMapZoom(ctx);
            Rectangle map = Renderer.getStrategicMapInnerRect(1280, 720, true);
            int clickX = map.x + (int) Math.round(((midpointX - UISystem.strategicMapWorldMinX(ctx))
                    / UISystem.strategicMapViewWidth(ctx)) * map.width);
            int clickY = map.y + (int) Math.round(((midpointY - UISystem.strategicMapWorldMinY(ctx))
                    / UISystem.strategicMapViewHeight(ctx)) * map.height);
            MouseEvent click = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, clickX, clickY, 1, false, MouseEvent.BUTTON1);
            UISystem.handleMapClick(ctx, click, 1280, 720);
            assertTrue(CampaignSystem.hasSelectedFreeTravelTarget(ctx)
                            || CampaignSystem.selectedCampaignLocation(ctx) != null
                            || !CampaignSystem.selectedCampaignContactLabel(ctx).isBlank(),
                    "the arrow interior must not intercept map selection");
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void gateABaselineTelemetryIsReadOnlyAndRoutesRemainPresent() {
        GameContext ctx = campaign(61001L);
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;

        Map<String, String> ownersBefore = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
        int routesBefore = CampaignSystem.campaignRouteSegments(ctx).size();
        List<String> telemetryBefore = CampaignSystem.campaignWarBaselineTelemetryLines(ctx);

        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            GameRenderSystem.render(ctx, g2, image.getWidth(), image.getHeight());
        } finally {
            g2.dispose();
        }

        assertFalse(ownersBefore.isEmpty());
        assertTrue(routesBefore > 0);
        assertEquals(ownersBefore, CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx));
        assertEquals(routesBefore, CampaignSystem.campaignRouteSegments(ctx).size());
        assertEquals(telemetryBefore, CampaignSystem.campaignWarBaselineTelemetryLines(ctx));
    }

    @Test
    void gateABaselineTimelineIsDeterministicAcrossIdenticalSeeds() {
        GameContext first = campaign(61002L);
        GameContext second = campaign(61002L);
        first.campaign.strategicOvermapMode = true;
        second.campaign.strategicOvermapMode = true;

        // The routine gate keeps the minute-one regression fast; the dedicated soak harness
        // records the full 0/30/60/180/600 timeline without slowing every test invocation.
        int[] checkpoints = {0, 30, 60};
        int elapsed = 0;
        for (int checkpoint : checkpoints) {
            while (elapsed < checkpoint) {
                int step = Math.min(10, checkpoint - elapsed);
                CampaignSystem.update(first, step);
                CampaignSystem.update(second, step);
                elapsed += step;
            }
            assertEquals(CampaignSystem.campaignWarBaselineTelemetryLines(first),
                    CampaignSystem.campaignWarBaselineTelemetryLines(second),
                    "campaign baseline diverged at " + checkpoint + " seconds");
        }
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private static void prepareCaptureForce(GameContext ctx,
                                            int forceId,
                                            String operationId,
                                            CampaignSystem.CampaignLocation target) throws Exception {
        Field forcesField = ctx.campaign.getClass().getField("campaignForces");
        Object force = null;
        for (Object candidate : (List<?>) forcesField.get(ctx.campaign)) {
            if (intField(candidate, "id") == forceId) {
                force = candidate;
                break;
            }
        }
        if (force == null) throw new AssertionError("missing campaign force " + forceId);
        setField(force, "assignedOperationId", operationId);
        setField(force, "destinationLocationId", target.id);
        setEnum(force, "mission", "CAPTURE");
        setEnum(force, "state", "MOVING");
        setEnum(force, "missionState", "ARRIVED");
        setField(force, "x", target.x);
        setField(force, "y", target.y);
    }

    private static int intField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
