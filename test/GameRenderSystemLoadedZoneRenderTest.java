import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRenderSystemLoadedZoneRenderTest {

    @Test
    void unifiedCampaignRenderingIncludesObjectsAcrossFormerMissionSubzones() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 10);

        int loadedSubzone = CampaignSystem.missionSubzoneIndex(0, 0);
        int remoteSubzone = CampaignSystem.missionSubzoneIndex(5, 2);
        double[] localPoint = subzonePoint(ctx, loadedSubzone);
        double[] remotePoint = subzonePoint(ctx, remoteSubzone);

        ctx.player.x = localPoint[0];
        ctx.player.y = localPoint[1];
        CampaignSystem.setLoadedMissionSubzone(ctx, loadedSubzone);

        Ship localEscort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, localPoint[0] + 80.0, localPoint[1] + 40.0);
        localEscort.campaignMissionSubzone = loadedSubzone;
        Ship remoteEscort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, remotePoint[0], remotePoint[1]);
        remoteEscort.campaignMissionSubzone = remoteSubzone;

        Projectile localShot = testProjectile(localPoint[0] + 20.0, localPoint[1] + 20.0);
        Projectile remoteShot = testProjectile(remotePoint[0] + 20.0, remotePoint[1] + 20.0);
        Asteroid localAsteroid = new Asteroid(localPoint[0] + 30.0, localPoint[1] + 30.0, 24.0, 100);
        Asteroid remoteAsteroid = new Asteroid(remotePoint[0] + 30.0, remotePoint[1] + 30.0, 24.0, 100);
        Salvage localSalvage = new Salvage(localPoint[0] + 40.0, localPoint[1] + 40.0, 10, 5, 5.0);
        Salvage remoteSalvage = new Salvage(remotePoint[0] + 40.0, remotePoint[1] + 40.0, 10, 5, 5.0);

        List<Ship> scopedShips = GameRenderSystem.renderScopedShips(ctx, List.of(ctx.player, localEscort, remoteEscort));
        List<Projectile> scopedProjectiles = GameRenderSystem.renderScopedProjectiles(ctx, List.of(localShot, remoteShot));
        List<Asteroid> scopedAsteroids = GameRenderSystem.renderScopedAsteroids(ctx, List.of(localAsteroid, remoteAsteroid));
        List<Salvage> scopedSalvage = GameRenderSystem.renderScopedSalvage(ctx, List.of(localSalvage, remoteSalvage));

        assertTrue(scopedShips.contains(ctx.player));
        assertTrue(scopedShips.contains(localEscort));
        assertTrue(scopedShips.contains(remoteEscort));
        assertTrue(scopedProjectiles.contains(localShot));
        assertTrue(scopedProjectiles.contains(remoteShot));
        assertTrue(scopedAsteroids.contains(localAsteroid));
        assertTrue(scopedAsteroids.contains(remoteAsteroid));
        assertTrue(scopedSalvage.contains(localSalvage));
        assertTrue(scopedSalvage.contains(remoteSalvage));
    }

    @Test
    void sectorizedRenderingScopesWorldObjectsToLoadedBattlefieldSector() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 600, 3000);
        ctx.player.faction = Faction.ALLY;
        BattlefieldSectorSystem.setLoadedSector(ctx, "blue-home");

        Ship localEscort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1200, 3000);
        Ship remoteEscort = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 27000, 3000);
        Projectile localShot = testProjectile(1400, 3000);
        Projectile remoteShot = testProjectile(27050, 3000);
        Asteroid localAsteroid = new Asteroid(1500, 3000, 24.0, 100);
        Asteroid remoteAsteroid = new Asteroid(27100, 3000, 24.0, 100);
        Salvage localSalvage = new Salvage(1600, 3000, 10, 5, 5.0);
        Salvage remoteSalvage = new Salvage(27200, 3000, 10, 5, 5.0);

        List<Ship> scopedShips = GameRenderSystem.renderScopedShips(ctx, List.of(ctx.player, localEscort, remoteEscort));
        List<Projectile> scopedProjectiles = GameRenderSystem.renderScopedProjectiles(ctx, List.of(localShot, remoteShot));
        List<Asteroid> scopedAsteroids = GameRenderSystem.renderScopedAsteroids(ctx, List.of(localAsteroid, remoteAsteroid));
        List<Salvage> scopedSalvage = GameRenderSystem.renderScopedSalvage(ctx, List.of(localSalvage, remoteSalvage));

        assertTrue(scopedShips.contains(ctx.player));
        assertTrue(scopedShips.contains(localEscort));
        assertFalse(scopedShips.contains(remoteEscort));
        assertTrue(scopedProjectiles.contains(localShot));
        assertFalse(scopedProjectiles.contains(remoteShot));
        assertTrue(scopedAsteroids.contains(localAsteroid));
        assertFalse(scopedAsteroids.contains(remoteAsteroid));
        assertTrue(scopedSalvage.contains(localSalvage));
        assertFalse(scopedSalvage.contains(remoteSalvage));
    }

    @Test
    void sectorizedRenderingKeepsDoubledLongRangeFriendlyContactsVisible() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 600, 3000);
        ctx.player.faction = Faction.ALLY;
        BattlefieldSectorSystem.setLoadedSector(ctx, "blue-home");

        Ship distantFriendly = new FleetShip(ShipRole.FRIGATE, Faction.ALLY,
                ctx.player.x + GameRenderSystem.LONG_RANGE_CONTACT_RENDER_METERS - 1200.0,
                ctx.player.y);
        Ship tooDistantFriendly = new FleetShip(ShipRole.FRIGATE, Faction.ALLY,
                ctx.player.x + GameRenderSystem.LONG_RANGE_CONTACT_RENDER_METERS + 1200.0,
                ctx.player.y);

        List<Ship> scopedShips = GameRenderSystem.renderScopedShips(ctx, List.of(distantFriendly, tooDistantFriendly));

        assertTrue(scopedShips.contains(distantFriendly));
        assertFalse(scopedShips.contains(tooDistantFriendly));
    }

    @Test
    void particleDrawFilterSuppressesRemoteSectorEffects() {
        for (int i = 0; i < 240; i++) {
            VFX.updateAll(1.0 / 60.0);
        }

        VFX.spawnImpactSparks(120.0, 120.0, 1.0, 0.0, 4);
        VFX.spawnImpactSparks(31200.0, 3000.0, 1.0, 0.0, 4);

        BufferedImage canvas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            int unfiltered = VFX.drawAll(g2, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            int filtered = VFX.drawAll(g2, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    (x, y) -> x < 1000.0);
            assertTrue(unfiltered > filtered, "remote particles should be excluded by the world filter");
            assertTrue(filtered > 0, "local particles should still render through the filter");
        } finally {
            g2.dispose();
        }

        for (int i = 0; i < 240; i++) {
            VFX.updateAll(1.0 / 60.0);
        }
        BufferedImage clearCanvas = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D clearG2 = clearCanvas.createGraphics();
        try {
            assertEquals(0, VFX.drawAll(clearG2, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        } finally {
            clearG2.dispose();
        }
    }

    private static Projectile testProjectile(double x, double y) {
        return new Projectile(x, y, 0.0, 0.0, 2.0, 1, 60, Faction.ALLY) { };
    }

    private static double[] subzonePoint(GameContext ctx, int subzone) {
        return new double[] {
                CampaignSystem.missionSubzoneMinX(ctx, ctx.campaign.sector, subzone) + 120.0,
                CampaignSystem.missionSubzoneMinY(ctx, ctx.campaign.sector, subzone) + 120.0
        };
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }
}
