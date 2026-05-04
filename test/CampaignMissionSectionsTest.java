import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMissionSectionsTest {

    @Test
    void campaignSectorsSeedMissionSectionsAndDiscoveries() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        assertTrue(ctx.campaign.missionSections.size() >= 3, "campaign sectors should expose multiple mission sections");
        assertEquals(5, ctx.campaign.discoverySites.size(), "sector 10 should keep a curated discovery budget after trimming");
    }

    @Test
    void campaignDiscoveriesSeedExpandedEncounterPool() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        Set<String> kinds = new HashSet<>();
        for (Object site : ctx.campaign.discoverySites) {
            Object kind = getField(site, "kind");
            kinds.add(String.valueOf(kind));
        }

        Set<String> highPriorityNarrativeSites = Set.of("REINFORCEMENT", "DATA_RELAY", "ANOMALY", "FLEET_ASSET");
        long narrativeCount = kinds.stream()
                .filter(highPriorityNarrativeSites::contains)
                .count();
        assertTrue(narrativeCount >= 3,
                "sector 10 should keep several high-priority narrative/scanner contacts after discovery trimming");
    }

    @Test
    void campaignDiscoveryZonesSeedAmbientWorldContent() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        long supportOrTraderCount = ctx.ships.stream()
                .filter(ship -> ship != null)
                .filter(ship -> ship.role == ShipRole.TRANSPORT
                        || ship.role == ShipRole.HAULER
                        || ship.role == ShipRole.MINER)
                .count();
        long turretCount = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.STATIC_TURRET)
                .count();

        assertTrue(ctx.asteroids.size() >= 20, "campaign pockets should place ambient ore/anomaly fields across the mission");
        assertTrue(ctx.salvage.size() >= 12, "campaign pockets should place ambient salvage/wreckage across the mission");
        assertTrue(supportOrTraderCount >= 1, "campaign pockets should still include at least one support, trader, or logistics ship before discovery");
        assertTrue(turretCount >= 1, "campaign pockets should still include seeded defenses or mine anchors in side zones");
    }

    @Test
    void missionProgressCapsUntilFleetReachesNextSection() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        ctx.campaign.objectiveProgress = 0.34;
        invokeUpdateMissionSectionFlow(ctx);

        assertEquals(1, ctx.campaign.activeMissionSection);
        assertTrue(ctx.campaign.missionSectionTravelLocked, "progress should pause until the fleet reaches the next section");

        Object activeSection = ctx.campaign.missionSections.get(ctx.campaign.activeMissionSection);
        ctx.player.x = getDoubleField(activeSection, "x");
        ctx.player.y = getDoubleField(activeSection, "y");

        invokeUpdateMissionSectionFlow(ctx);

        assertFalse(ctx.campaign.missionSectionTravelLocked, "arriving at the new section should unlock progress again");
    }

    @Test
    void sectorTwoHudExplainsWinStateAndImmediateTask() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 2);

        String detail = CampaignSystem.hudObjectiveDetail(ctx);
        assertTrue(detail.contains("Win State: Reach T-0 with at least 2 convoys alive"));
        assertTrue(detail.contains("Timer State: T-")
                        && detail.contains("Extraction at T-0 is a win if convoy quota holds"),
                "sector 2 should explicitly say that the timer ends in extraction success when the convoy quota survives");
        assertTrue(detail.contains("Current Task: Clear FORWARD SCREEN")
                        || detail.contains("Current Task: Hold the convoy lane"),
                "sector 2 should tell the player both the extraction win state and the immediate pocket task");
    }

    @Test
    void missionThreeInitialBlockersAreMarkedAndReliefRouteIsInitialized() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 3);

        assertEquals(6, CampaignSystem.activeObjectiveMarkers(ctx).stream()
                        .filter(marker -> marker.type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET)
                        .count(),
                "mission 3 should mark the initial jump-ring blockers");
        assertNotEquals(0.0, ctx.campaign.captureX, 0.01, "mission 3 relief-wing anchor should be initialized");
        assertNotEquals(0.0, ctx.campaign.captureY, 0.01, "mission 3 relief-wing anchor should be initialized");
    }

    @Test
    void destroyObjectiveTextOnlyPromisesMarkedTargetsWhenMarkersExist() throws Exception {
        for (int sector = 1; sector <= 24; sector++) {
            GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L + sector, false));
            ctx.campaignUnlockProfile = null;
            SpawnSystem.initWorld(ctx);
            startSector(ctx, sector);

            if (ctx.campaign.objectiveType != CampaignSystem.ObjectiveType.DESTROY) continue;
            String detail = CampaignSystem.hudObjectiveDetail(ctx);
            boolean mentionsMarked = detail.toLowerCase().contains("marked");
            boolean hasMarkers = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                    .anyMatch(marker -> marker.type == CampaignSystem.ObjectiveMarkerType.DESTROY_TARGET);
            assertFalse(mentionsMarked && !hasMarkers,
                    "sector " + sector + " should never promise marked destroy targets unless the game actually exposes them");
        }
    }

    @Test
    void lockedMissionSectionHudSaysToFlyFlagshipToNextPocket() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);
        ctx.campaign.activeMissionSection = 1;
        ctx.campaign.missionSectionTravelLocked = true;

        String detail = CampaignSystem.hudObjectiveDetail(ctx);
        assertTrue(detail.contains("Current Task: Pocket clear at FORWARD SCREEN; fly the flagship to RESERVE STAGING to resume mission progress"),
                "locked staged missions should say which pocket is cleared and which pocket the flagship must enter next");
        assertTrue(detail.contains("Next Move: Pocket clear; route the flagship to RESERVE STAGING because kills are paused until arrival"),
                "locked staged missions should explain that combat progress is paused until the flagship reaches the next pocket");
    }

    @Test
    void enteringDiscoveryPocketMarksItFound() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        List<?> discoveries = ctx.campaign.discoverySites;
        Object first = discoveries.get(0);
        ctx.player.x = getDoubleField(first, "x");
        ctx.player.y = getDoubleField(first, "y");

        invokeUpdatePocketDiscoveries(ctx);

        assertTrue(ctx.campaign.discoveriesFound >= 1);
        assertTrue(getBooleanField(first, "discovered"));
        assertTrue(ctx.ui.voiceCaption != null && !ctx.ui.voiceCaption.isBlank(),
                "discovering a scanner contact should now trigger contextual crew banter");
    }

    @Test
    void sensorNetEntriesMixMissionAndSupportContacts() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        List<GameRenderSystem.SensorNetEntry> entries = GameRenderSystem.sensorNetEntries(ctx, 4, 2);
        assertFalse(entries.isEmpty(), "sensor net should surface actionable contacts during campaign sectors");
        assertTrue(entries.stream().anyMatch(entry -> "MISSION".equals(entry.section)),
                "sensor net should include mission-critical routing and objective contacts");
        assertTrue(entries.stream().anyMatch(entry -> !"MISSION".equals(entry.section)),
                "sensor net should also surface non-mission support contacts like anomaly, salvage, resource, or contact tracks");
    }

    @Test
    void strategicSupportMarkersExposeOptionalContactsOnTheMap() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        assertFalse(markers.isEmpty(), "campaign map should expose support contacts as live strategic markers");
        assertTrue(markers.stream().anyMatch(marker ->
                        marker.type == CampaignSystem.SupportMarkerType.ANOMALY
                                || marker.type == CampaignSystem.SupportMarkerType.FACTION_CONTACT
                                || marker.type == CampaignSystem.SupportMarkerType.INTEL),
                "support markers should include narrative contacts like anomalies, faction contacts, or intel sources");
    }

    @Test
    void nearestSupportMarkerFindsAuthoredDiscoveryTrack() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        CampaignSystem.CampaignSupportMarker expected = CampaignSystem.activeSupportMarkers(ctx).get(0);
        CampaignSystem.CampaignSupportMarker resolved =
                CampaignSystem.nearestSupportMarker(ctx, expected.x, expected.y, 180.0);
        assertTrue(resolved != null && resolved.label.equals(expected.label),
                "map interactions should be able to route directly to a support contact marker");
    }

    @Test
    void strategicLandmarksExposeAuthoredSectorIdentityWithoutScannerDuplicates() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        List<CampaignSystem.CampaignLandmark> markers = CampaignSystem.strategicLandmarks(ctx);
        assertFalse(markers.isEmpty(), "campaign map should expose authored landmarks as a strategic marker layer");
        assertTrue(markers.stream().noneMatch(marker -> marker.discoveryDerived),
                "scanner-derived contacts should stay on the support layer instead of duplicating landmark markers");
        assertTrue(markers.stream().anyMatch(marker ->
                        "FORWARD SCREEN".equals(marker.label) || "RESERVE STAGING".equals(marker.label)),
                "staged sectors should surface named authored pockets as navigable landmarks");
    }

    @Test
    void nearestStrategicLandmarkFindsNamedPocketMarker() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        CampaignSystem.CampaignLandmark expected = CampaignSystem.strategicLandmarks(ctx).get(0);
        CampaignSystem.CampaignLandmark resolved =
                CampaignSystem.nearestStrategicLandmark(ctx, expected.x, expected.y, 180.0);
        assertTrue(resolved != null && resolved.label.equals(expected.label),
                "map interactions should be able to route directly to a named authored landmark");
    }

    @Test
    void supportMarkersUseClearFactionFacingNames() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        assertTrue(markers.stream().anyMatch(marker ->
                        marker.type == CampaignSystem.SupportMarkerType.FACTION_CONTACT
                                && (marker.label.startsWith("Broker Contact - ")
                                || marker.label.startsWith("Coalition Contact - ")
                                || marker.label.startsWith("Detention Contact - ")
                                || marker.label.startsWith("Support Contact - "))),
                "faction-facing support contacts should read like actual actor tracks instead of anonymous categories");
    }

    @Test
    void missionSectionsAndDiscoveriesLandInsidePlayableSubzones() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        Method missionSubzoneForPoint = CampaignSystem.class.getDeclaredMethod(
                "missionSubzoneForPoint", int.class, double.class, double.class);
        missionSubzoneForPoint.setAccessible(true);

        for (Object section : ctx.campaign.missionSections) {
            int subzone = (int) missionSubzoneForPoint.invoke(
                    null, ctx.campaign.sector, getDoubleField(section, "x"), getDoubleField(section, "y"));
            assertTrue(subzone >= 0, "mission section should be placed inside a playable subzone");
        }

        for (Object site : ctx.campaign.discoverySites) {
            int subzone = (int) missionSubzoneForPoint.invoke(
                    null, ctx.campaign.sector, getDoubleField(site, "x"), getDoubleField(site, "y"));
            assertTrue(subzone >= 0, "discovery site should be placed inside a playable subzone");
        }
    }

    @Test
    void randomizedMissionPocketsRespectArrivalSafetyBand() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 10);

        Method missionSubzoneForPoint = CampaignSystem.class.getDeclaredMethod(
                "missionSubzoneForPoint", int.class, double.class, double.class);
        missionSubzoneForPoint.setAccessible(true);

        int entryCol = CampaignSystem.missionSubzoneColumn(ctx.campaign.loadedMissionSubzone);
        int safeDepth = (entryCol <= 0 || entryCol >= CampaignSystem.missionSubzoneColumns() - 1) ? 2 : 1;

        for (Object section : ctx.campaign.missionSections) {
            String label = String.valueOf(getField(section, "label"));
            if (!"FORWARD SCREEN".equals(label) && !"RESERVE STAGING".equals(label)) continue;
            int subzone = (int) missionSubzoneForPoint.invoke(
                    null, ctx.campaign.sector, getDoubleField(section, "x"), getDoubleField(section, "y"));
            int col = CampaignSystem.missionSubzoneColumn(subzone);
            assertFalse(Math.abs(col - entryCol) < safeDepth,
                    "hostile mission pockets should stay out of the player's warp-in safety band");
        }

        for (Object site : ctx.campaign.discoverySites) {
            String kind = String.valueOf(getField(site, "kind"));
            if (!"AMBUSH".equals(kind) && !"MINEFIELD".equals(kind) && !"WRECK_FIELD".equals(kind)) continue;
            int subzone = (int) missionSubzoneForPoint.invoke(
                    null, ctx.campaign.sector, getDoubleField(site, "x"), getDoubleField(site, "y"));
            int col = CampaignSystem.missionSubzoneColumn(subzone);
            assertFalse(Math.abs(col - entryCol) < safeDepth,
                    "hostile discovery pockets should stay out of the player's warp-in safety band");
        }
    }

    @Test
    void campaignWarpRoutingOnlyAdvancesOneSectorPerJump() {
        int source = CampaignSystem.missionSubzoneIndex(0, 1);
        int target = CampaignSystem.missionSubzoneIndex(5, 1);
        int hop = CampaignSystem.nextCampaignWarpHop(source, target);
        assertEquals(CampaignSystem.missionSubzoneIndex(1, 1), hop);

        int diagonalTarget = CampaignSystem.missionSubzoneIndex(5, 2);
        int diagonalHop = CampaignSystem.nextCampaignWarpHop(source, diagonalTarget);
        assertEquals(CampaignSystem.missionSubzoneIndex(1, 2), diagonalHop);
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static void invokeUpdateMissionSectionFlow(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateMissionSectionFlow",
                GameContext.class,
                CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign);
    }

    private static void invokeUpdatePocketDiscoveries(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updatePocketDiscoveries",
                GameContext.class,
                CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign);
    }

    private static double getDoubleField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
