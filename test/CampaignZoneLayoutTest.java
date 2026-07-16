import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignZoneLayoutTest {

    @Test
    void campaignMissionWorldScalesWithConfiguredSectorSize() {
        GameConfig small = new GameConfig(GameMode.CAMPAIGN_OPS, 1800, 1400, true, 1L, false);
        GameConfig large = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1L, false);
        assertTrue(CampaignSystem.recommendedWorldWidth(large) > CampaignSystem.recommendedWorldWidth(small));
        assertTrue(CampaignSystem.recommendedWorldHeight(large) > CampaignSystem.recommendedWorldHeight(small));
    }

    @Test
    void missionSubzonesUseBoundedContiguousPocketSpacing() throws Exception {
        Method centerX = CampaignSystem.class.getDeclaredMethod("missionSubzoneCenterX", int.class, int.class);
        Method centerY = CampaignSystem.class.getDeclaredMethod("missionSubzoneCenterY", int.class, int.class);
        Method missionSubzoneIndex = CampaignSystem.class.getDeclaredMethod("missionSubzoneIndex", int.class, int.class);
        centerX.setAccessible(true);
        centerY.setAccessible(true);
        missionSubzoneIndex.setAccessible(true);

        int left = (int) missionSubzoneIndex.invoke(null, 0, 1);
        int right = (int) missionSubzoneIndex.invoke(null, 1, 1);
        int upper = (int) missionSubzoneIndex.invoke(null, 0, 0);

        double leftX = (double) centerX.invoke(null, 1, left);
        double rightX = (double) centerX.invoke(null, 1, right);
        double leftY = (double) centerY.invoke(null, 1, left);
        double upperY = (double) centerY.invoke(null, 1, upper);

        assertEquals(5000.0, rightX - leftX, 0.001);
        assertEquals(5000.0, leftY - upperY, 0.001);
    }

    @Test
    void campaignWorldContainsSeparatedZonesSoSectorTwoSpawnDoesNotClampToMapEdge() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        startSector(ctx, 2);

        assertTrue(ctx.WORLD_W >= CampaignSystem.recommendedWorldWidth(ctx.config));
        assertTrue(ctx.WORLD_H >= CampaignSystem.recommendedWorldHeight(ctx.config));
        assertTrue(ctx.player.x > 2000.0 && ctx.player.x < 3000.0,
                "sector 2 arrival should land inside the physical sector-2 zone, not on the world edge");
        assertTrue(ctx.player.x < ctx.WORLD_W - 1000.0,
                "sector 2 arrival should not be clamped to the far-right map border");

        long overlappingFleetShips = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.faction == Faction.ALLY)
                .filter(ship -> Math.hypot(ship.x - ctx.player.x, ship.y - ctx.player.y)
                        < Math.max(120.0, ship.radius + ctx.player.radius + 20.0))
                .count();
        assertEquals(0, overlappingFleetShips,
                "persistent fleet ships should spawn in formation instead of stacking on the Mothership");

        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "RESOURCE POCKET".equals(l.label)));
        assertTrue(CampaignSystem.landmarks(ctx).stream().anyMatch(l -> "RESERVE STAGING".equals(l.label)));

        double minEnemyX = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.ENEMY)
                .mapToDouble(ship -> ship.x)
                .min()
                .orElse(ctx.player.x);
        double maxEnemyX = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.ENEMY)
                .mapToDouble(ship -> ship.x)
                .max()
                .orElse(ctx.player.x);
        assertTrue(maxEnemyX - minEnemyX > 900.0,
                "campaign sectors should distribute enemy contacts across the zone instead of one local cluster");
    }

    @Test
    void tacticalZoneEntryRebuildsPersistentFleetNearPlayerDespiteStaleSavedOffsets() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 4321L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        replacePersistentFleetWithStaleOffsets(ctx.campaign);

        startSector(ctx, 2);

        int loadedSubzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
        List<Ship> deployed = ctx.ships.stream()
                .filter(ship -> ship != null && ship.name != null && ship.name.startsWith("Stale Deploy "))
                .filter(ship -> ship.alive && !ship.dying && ship.hp > 0)
                .toList();

        assertEquals(10, deployed.size(), "all committed persistent hulls should materialize on tactical entry");
        for (Ship ship : deployed) {
            double dist = Math.hypot(ship.x - ctx.player.x, ship.y - ctx.player.y);
            assertTrue(dist < 1600.0,
                    "stale saved offsets should not scatter " + ship.name + " away from the entry formation");
            assertEquals(loadedSubzone, ship.campaignMissionSubzone,
                    "persistent fleet hulls should be stamped into the loaded tactical pocket");
        }
    }

    @Test
    void campaignFleetMinersHoldNearMothershipUnlessMiningIsOrdered() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9876L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        Ship miner = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.MINER && ship.minerHomeBase == ctx.player)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected persistent campaign miner"));
        int loadedSubzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
        miner.x = ctx.player.x + 1800.0;
        miner.y = ctx.player.y + 1200.0;
        miner.minerState = Ship.MinerState.SEEK_ASTEROID;
        miner.minerTarget = new Asteroid(miner.x + 300.0, miner.y, 40.0, 400);
        ctx.command.alliedFleetCommand = GameContext.FleetCommand.AUTO;

        EconomySystem.update(ctx, 1.0);

        assertEquals(Ship.MinerState.IDLE, miner.minerState,
                "campaign fleet miners should hold escort formation unless the fleet is explicitly ordered to mine");
        assertEquals(loadedSubzone, miner.campaignMissionSubzone,
                "held campaign miners should remain visible in the loaded tactical pocket");
        assertTrue(miner.minerTarget == null, "held campaign miners should drop remote asteroid targets");
    }

    @Test
    void legacyMainPoisKeepStableIdsWhileExposingFacilityMetadata() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        assertEquals(24, CampaignSystem.mainCampaignLocations(ctx).size());
        for (int i = 0; i < 24; i++) {
            CampaignSystem.CampaignLocation location = CampaignSystem.mainCampaignLocations(ctx).get(i);
            String legacyId = String.format(java.util.Locale.US, "poi-%02d", i + 1);
            assertEquals(legacyId, location.id);
            assertEquals(legacyId, location.legacyPoiId);
            assertEquals(String.format(java.util.Locale.US, "facility-%02d", i + 1), location.facilityId);
            assertTrue(location.zoneId != null && !location.zoneId.isBlank(),
                    "main campaign facility should expose a zone id");
            assertTrue(location.facilityType != CampaignSystem.CampaignFacilityType.UNKNOWN,
                    "main campaign facility should expose an explicit facility type");
            assertTrue(location.strategicValue >= 1 && location.strategicValue <= 5,
                    "strategic value should stay in the 1-5 rating range");
            assertEquals(CampaignSystem.CampaignIntelLevel.FULL, location.intelLevel);
        }
    }

    @Test
    void proceduralMinorSitesStartAsHiddenFacilitiesWithUnknownIntel() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        assertTrue(CampaignSystem.campaignAreasOfInterest(ctx).stream()
                .filter(location -> location != null && location.id.startsWith("aoi-proc-"))
                .allMatch(location -> !location.discovered
                        && location.facilityType != CampaignSystem.CampaignFacilityType.UNKNOWN
                        && location.intelLevel == CampaignSystem.CampaignIntelLevel.UNKNOWN));
    }

    @Test
    void strategicOvermapFacilitiesGenerateHomeBasedFleets() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = true;
        ctx.campaign.facilityFleetGenerationActive = true;

        invokeForceSimulation(ctx, 0.25);

        boolean found = false;
        for (Object force : ctx.campaign.campaignForces) {
            if (fieldString(force, "name").equals("Green Anchorage Pelagos Patrol Fleet")
                    && fieldString(force, "homeBaseId").equals("poi-01")
                    && fieldString(force, "sourceLocationId").equals("poi-01")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "strategic overmap should generate a home-based fleet from the Green anchorage facility");
    }

    @Test
    void openingCampaignForcesStayOutsideProtectedStartRadius() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.strategicOvermapMode = true;
        ctx.campaign.facilityFleetGenerationActive = true;
        ctx.campaign.sectorElapsed = 0.0;

        invokeForceSimulation(ctx, 0.25);

        for (Object force : ctx.campaign.campaignForces) {
            if (!"ENEMY".equals(fieldString(force, "faction"))) continue;
            double x = fieldDouble(force, "x");
            double y = fieldDouble(force, "y");
            double dist = Math.hypot(x - ctx.campaign.playerGalaxyX, y - ctx.campaign.playerGalaxyY);
            assertTrue(dist >= 2500.0,
                    "opening Red campaign force should not spawn directly on the player: " + fieldString(force, "name"));
        }
    }

    @Test
    void tacticalCampaignSpawnGracePushesRedShipsAwayFromPlayer() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 99L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);
        ctx.campaign.sectorElapsed = 2.0;

        Ship spawned = invokeSpawnEnemyAtPoint(ctx, ShipRole.PATROL, ctx.player.x + 30.0, ctx.player.y + 20.0);
        double dist = Math.hypot(spawned.x - ctx.player.x, spawned.y - ctx.player.y);
        assertTrue(dist >= 900.0,
                "Red ships spawned during the opening grace period should not pile directly onto the player");
    }

    @Test
    void nonPlayerFriendlyOffsetSpawnsUseSeparateRallyPocket() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 101L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        Ship green = invokeSpawnCampaignFactionAtPlayerOffset(
                ctx, ShipRole.FRIGATE, Faction.TEAM_C, -120.0, 70.0, "Green Test Guard");

        double dist = Math.hypot(green.x - ctx.player.x, green.y - ctx.player.y);
        assertTrue(dist >= 900.0,
                "non-player friendly support should spawn near the player but outside the player formation pocket");
        assertTrue(green.minerHomeBase != ctx.player,
                "local friendly support should not be tagged as part of the persistent player formation");
    }

    @Test
    void joinedGreenCoalitionSupportSpawnsOutsidePlayerFormationAtMissionStart() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 102L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.greenContractFleetJoined = true;
        ctx.campaign.greenContractFavor = Math.max(ctx.campaign.greenContractFavor, 4);

        startSector(ctx, 13);

        List<Ship> greenContractShips = ctx.ships.stream()
                .filter(ship -> ship != null && ship.name != null && ship.name.startsWith("Green Contract"))
                .filter(ship -> ship.role != ShipRole.STATIC_TURRET && ship.role != ShipRole.BASE)
                .toList();
        assertTrue(!greenContractShips.isEmpty(), "expected joined Green support ships to spawn");
        for (Ship ship : greenContractShips) {
            double dist = Math.hypot(ship.x - ctx.player.x, ship.y - ctx.player.y);
            assertTrue(dist >= 900.0,
                    "joined Green support should spawn as its own nearby formation, not inside the player formation: " + ship.name);
        }
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static void replacePersistentFleetWithStaleOffsets(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CampaignSystem.PersistentFleetEntry> entries = (List<CampaignSystem.PersistentFleetEntry>) field.get(st);
        entries.clear();

        ShipRole[] roles = {
                ShipRole.CARRIER_SUPPORT_TITAN,
                ShipRole.BATTLESHIP,
                ShipRole.BATTLECRUISER,
                ShipRole.CRUISER,
                ShipRole.FRIGATE,
                ShipRole.PICKET,
                ShipRole.CIWS_CORVETTE,
                ShipRole.MINER,
                ShipRole.MISSILE_BOAT,
                ShipRole.HAULER
        };
        for (int i = 0; i < roles.length; i++) {
            CampaignSystem.PersistentFleetEntry entry = CampaignSystem.addPersistentFleetEntry(
                    st,
                    roles[i],
                    "Stale Deploy " + (i + 1),
                    CampaignSystem.CAMPAIGN_FLAGSHIP_COMMAND_GROUP,
                    Faction.ALLY
            );
            entry.tacticalCommitmentId = CampaignSystem.FleetCommitment.COMMIT.name();
            entry.relX = 8000.0 + i * 900.0;
            entry.relY = (i % 2 == 0 ? 1.0 : -1.0) * (6400.0 + i * 700.0);
            entry.relAngle = Math.PI;
        }
    }

    private static Ship invokeSpawnEnemyAtPoint(GameContext ctx, ShipRole role, double x, double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "spawnEnemyAtPoint",
                GameContext.class,
                ShipRole.class,
                double.class,
                double.class
        );
        method.setAccessible(true);
        return (Ship) method.invoke(null, ctx, role, x, y);
    }

    private static Ship invokeSpawnCampaignFactionAtPlayerOffset(GameContext ctx, ShipRole role, Faction faction,
                                                                 double ox, double oy, String name) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "spawnCampaignFactionAtPlayerOffset",
                GameContext.class,
                ShipRole.class,
                Faction.class,
                double.class,
                double.class,
                String.class);
        method.setAccessible(true);
        return (Ship) method.invoke(null, ctx, role, faction, ox, oy, name);
    }

    private static void invokeForceSimulation(GameContext ctx, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, dt);
    }

    private static String fieldString(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static double fieldDouble(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
