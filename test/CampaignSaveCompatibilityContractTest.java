import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import app.support.UserDataPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

class CampaignSaveCompatibilityContractTest {
    private static final Path SAVE = UserDataPaths.saveDir().resolve("campaign_checkpoint.properties");
    private static final Path FIXTURE = Path.of("test", "fixtures", "campaign_schema_v1.properties");
    private byte[] original;
    private boolean hadOriginal;

    @BeforeEach
    void preserveUserCheckpoint() throws Exception {
        hadOriginal = Files.exists(SAVE);
        original = hadOriginal ? Files.readAllBytes(SAVE) : null;
        Files.createDirectories(SAVE.getParent());
        Files.copy(FIXTURE, SAVE, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterEach
    void restoreUserCheckpoint() throws Exception {
        Files.deleteIfExists(Path.of(SAVE + ".pre-migration-v1.bak"));
        if (hadOriginal && original != null) {
            Files.write(SAVE, original);
        } else {
            Files.deleteIfExists(SAVE);
        }
    }

    @Test
    void schemaV1MigratesThroughTravelCombatShipQueueAndSecondReload() throws Exception {
        byte[] sourceBytes = Files.readAllBytes(SAVE);
        CampaignCheckpointStore.Checkpoint migrated = CampaignCheckpointStore.load();

        assertNotNull(migrated);
        assertTrue(migrated.migrationApplied);
        assertEquals(1, migrated.sourceVersion);
        assertEquals(CampaignCheckpointStore.currentVersion(), migrated.version);
        assertTrue(migrated.campaignFuel > 0);
        assertTrue(migrated.campaignSupplies > 0);
        assertTrue(migrated.campaignAmmo > 0);
        assertEquals("PLAYER", migrated.playerFactionName);
        assertEquals("BALANCED", migrated.branchRoute);
        assertArrayEquals(sourceBytes, Files.readAllBytes(SAVE),
                "loading and migration analysis must not alter the source save");

        GameContext ctx = new GameContext(migrated.toGameConfig(GameMode.CAMPAIGN_OPS));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        assertTrue(CampaignSystem.isStrategicOvermapMode(ctx));
        assertTrue(ctx.campaign.saveRecoveryMessage.contains("SAVE RECOVERED"));
        assertTrue(CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                .anyMatch(line -> line.contains("event=campaign.save_recovery")
                        && line.contains("verified=true")));
        assertTrue(Files.exists(Path.of(SAVE + ".pre-migration-v1.bak")),
                "source save must remain as a backup until migrated checkpoint verification succeeds");

        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1400.0, 3400.0));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(ctx.campaign.galaxyTravel.traveling);

        startSector(ctx, 5);
        assertFalse(CampaignSystem.isStrategicOvermapMode(ctx));
        assertNotNull(ctx.player);
        assertTrue(ctx.player.alive);

        activateStrategicOvermap(ctx);
        ctx.credits = 100_000;
        ctx.campaign.campaignSalvage = 100;
        CampaignSystem.grantCampaignOre(ctx, 500);
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 100, 1));
        CampaignSystem.CampaignLocation yard = new CampaignSystem.CampaignLocation(
                "migration-yard", "Migration Yard", 1000.0, 1000.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT, 0.0f, false, 0,
                "Compatibility fixture yard", CampaignSystem.HubService.SHIPYARD);
        queueConstruction(ctx, yard);
        assertFalse(CampaignSystem.campaignYardOrders(ctx).isEmpty());

        CampaignCheckpointStore.Checkpoint resaved = captureCheckpoint(ctx, 6);
        CampaignCheckpointStore.save(resaved);
        CampaignCheckpointStore.Checkpoint firstReload = CampaignCheckpointStore.load();
        assertNotNull(firstReload);
        assertEquals(CampaignCheckpointStore.currentVersion(), firstReload.version);
        assertFalse(firstReload.campaignYardOrders.isBlank());

        CampaignCheckpointStore.save(firstReload);
        CampaignCheckpointStore.Checkpoint secondReload = CampaignCheckpointStore.load();
        assertNotNull(secondReload);
        assertEquals(firstReload.nextSector, secondReload.nextSector);
        assertEquals(firstReload.campaignYardOrders, secondReload.campaignYardOrders);
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        method.setAccessible(true);
        method.invoke(null, ctx, sector);
    }

    private static void activateStrategicOvermap(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "activateStrategicOvermapLayer",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                String.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, "MIGRATION TEST RETURN");
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static void queueConstruction(GameContext ctx, CampaignSystem.CampaignLocation yard) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "queueCampaignConstructionOrder",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class,
                ShipRole.class,
                int.class,
                int.class,
                int.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(null, ctx, ctx.campaign, yard, ShipRole.FRIGATE, 100, 20, 5));
    }
}
