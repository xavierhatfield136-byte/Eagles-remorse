import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignReleaseTelemetryContractTest {
    @Test
    void releaseTelemetryContractCoversRequiredReasonFamiliesAndRejectsPersonalData() throws Exception {
        List<String> contract = CampaignSystem.campaignReleaseTelemetryContractLines();
        String joined = String.join("\n", contract);
        assertTrue(joined.contains("campaign.fleet.created"));
        assertTrue(joined.contains("campaign.fleet.destroyed"));
        assertTrue(joined.contains("campaign.fleet.disappeared"));
        assertTrue(joined.contains("campaign.ownership.changed"));
        assertTrue(joined.contains("campaign.production.start/stop"));
        assertTrue(joined.contains("campaign.mining.departure/return"));
        assertTrue(joined.contains("campaign.mission.success"));
        assertTrue(joined.contains("campaign.failure"));
        assertTrue(joined.contains("campaign.strike.denied"));
        assertTrue(joined.contains("campaign.save_recovery"));
        assertTrue(joined.contains("no credentials"));

        GameContext ctx = initializedCampaignContext();
        CampaignSystem.launchSelectedCampaignTorpedoStrike(ctx);
        assertTelemetryContains(ctx, "event=campaign.strike.denied");
        assertTelemetryContains(ctx, "reason=no_tracked_hostile_contact_selected");

        Object force = createTelemetryForce(ctx);
        assertNotNull(force);
        assertTelemetryContains(ctx, "event=campaign.fleet.created");
        assertTelemetryContains(ctx, "reason=release_telemetry_fixture");
        assertFalse(String.join("\n", CampaignSystem.campaignReleaseTelemetryHistory(ctx))
                .contains(System.getProperty("user.home", "impossible-personal-path")));

        setBoolean(force, "destroyed", true);
        invokeForceSimulation(ctx, 0.25);
        assertTelemetryContains(ctx, "event=campaign.fleet.destroyed");
        assertTelemetryContains(ctx, "event=campaign.fleet.disappeared");

        CampaignSystem.CampaignLocation yard = new CampaignSystem.CampaignLocation(
                "telemetry-yard", "Telemetry Yard", 1000.0, 1000.0,
                CampaignSystem.CampaignLocationType.STORY_EVENT, 0.0f, false, 0,
                "Telemetry yard", CampaignSystem.HubService.SHIPYARD);
        queueConstruction(ctx, yard);
        assertTelemetryContains(ctx, "event=campaign.production.start");
        advanceYardOrders(ctx, 1000.0);
        assertTelemetryContains(ctx, "event=campaign.production.stop");

        Object miner = createMiningForce(ctx);
        assignMiningMission(ctx, miner);
        assertTelemetryContains(ctx, "event=campaign.mining.departure");
        setString(miner, "destinationLocationId", "poi-05");
        setEnum(miner, "stopReason", "UNLOADING");
        setEnum(miner, "workState", "WORKING");
        setDouble(miner, "cargoLoad", 40.0);
        setDouble(miner, "workRemainingSec", 0.0);
        setDouble(miner, "taskDeadlineSec", 0.0);
        invokeLifecycleAfterMovement(ctx, miner);
        assertTelemetryContains(ctx, "event=campaign.mining.return");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 8181L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static Object createTelemetryForce(GameContext ctx) throws Exception {
        return createForce(ctx, "PATROL_GROUP", "Telemetry Patrol", "release telemetry fixture");
    }

    private static Object createMiningForce(GameContext ctx) throws Exception {
        return createForce(ctx, "MINING_GROUP", "Telemetry Miners", "ore assignment fixture");
    }

    private static Object createForce(GameContext ctx, String kindName, String name, String purpose) throws Exception {
        Class<?> kindClass = Class.forName("CampaignSystem$CampaignForceKind");
        Method method = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForceWithoutDeploymentCost",
                CampaignSystem.CampaignState.class,
                kindClass,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class);
        method.setAccessible(true);
        Object kind = Enum.valueOf(kindClass.asSubclass(Enum.class), kindName);
        return method.invoke(null, ctx.campaign, kind, Faction.TEAM_C,
                name, "poi-05", purpose,
                ctx.campaign.playerGalaxyX + 300.0, ctx.campaign.playerGalaxyY + 200.0);
    }

    private static void queueConstruction(GameContext ctx, CampaignSystem.CampaignLocation yard) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "queueCampaignConstructionOrder",
                GameContext.class, CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class,
                ShipRole.class, int.class, int.class, int.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(null, ctx, ctx.campaign, yard, ShipRole.FRIGATE, 100, 20, 5));
    }

    private static void advanceYardOrders(GameContext ctx, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "advanceCampaignYardOrders", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, dt);
    }

    private static void assignMiningMission(GameContext ctx, Object force) throws Exception {
        CampaignSystem.CampaignLocation site = CampaignSystem.campaignAreasOfInterest(ctx).stream()
                .filter(location -> location.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE)
                .findFirst()
                .orElseThrow();
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignMiningMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class,
                String.class);
        method.setAccessible(true);
        method.invoke(null, ctx.campaign, force, site.id, "poi-05");
    }

    private static void invokeLifecycleAfterMovement(GameContext ctx, Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceLifecycleAfterMovement",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class);
        method.setAccessible(true);
        method.invoke(null, ctx.campaign, force, 0.25);
    }

    private static void invokeForceSimulation(GameContext ctx, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulationTicked",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, dt);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setDouble(Object target, String name, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setString(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setEnum(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object enumValue = Enum.valueOf((Class<? extends Enum>) field.getType().asSubclass(Enum.class), value);
        field.set(target, enumValue);
    }

    private static void assertTelemetryContains(GameContext ctx, String expected) {
        assertTrue(CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                        .anyMatch(line -> line.contains(expected)),
                () -> "Missing telemetry '" + expected + "' in "
                        + CampaignSystem.campaignReleaseTelemetryHistory(ctx));
    }
}
