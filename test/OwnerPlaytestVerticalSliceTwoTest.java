import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestVerticalSliceTwoTest {
    @Test
    void reconSweepProducesVisibleBeforeAfterResultAndCooldown() throws Exception {
        GameContext ctx = campaign(63001L);
        CampaignSystem.CampaignState st = ctx.campaign;
        Object hostile = nonLinkedEnemyForce(st);
        setField(hostile, "x", st.playerGalaxyX + 420.0);
        setField(hostile, "y", st.playerGalaxyY + 120.0);
        setField(hostile, "lastKnownX", st.playerGalaxyX + 420.0);
        setField(hostile, "lastKnownY", st.playerGalaxyY + 120.0);
        setField(hostile, "visibleToPlayer", false);
        setField(hostile, "contactConfidence", 0.20);
        setEnum(hostile, "contactState", "STALE");
        st.campaignIntelLevel = 55.0;
        st.campaignSupplies = 40;

        List<String> preview = CampaignSystem.campaignSensorSweepPreviewLines(ctx);
        assertTrue(preview.stream().anyMatch(line -> line.startsWith("Recon Sweep  |  cost ")));
        assertTrue(preview.stream().anyMatch(line -> line.contains("uncertain/stale contacts")));
        assertTrue(preview.contains("Ready"));
        int sweepCost = Integer.parseInt(preview.get(0).replaceAll(".*cost (\\d+) supplies.*", "$1"));

        int suppliesBefore = st.campaignSupplies;
        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));
        assertEquals(suppliesBefore - sweepCost, st.campaignSupplies);
        assertEquals("KNOWN", field(hostile, "contactState").toString());
        assertTrue(st.lastContactScanSummary.startsWith("Recon Sweep Result"));
        assertTrue(st.lastContactScanSummary.contains("new tracks") || st.lastContactScanSummary.contains("IDs improved"));
        assertTrue(CampaignSystem.campaignRumorBoardLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Recon Sweep Result")));
        assertTrue(CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                .anyMatch(line -> line.contains("campaign.recon.completed")));

        int afterFirstSweep = st.campaignSupplies;
        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));
        assertEquals(afterFirstSweep, st.campaignSupplies, "cooldown must block repeat spending");
        assertTrue(CampaignSystem.campaignSensorSweepPreviewLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Blocked  |  cooldown ")));
    }

    @Test
    void everyPosturePublishesQuantifiedCostsAndRisk() {
        GameContext ctx = campaign(63002L);
        List<String> silent = CampaignSystem.campaignFleetPostureForecastLines(ctx, "SILENT_RUNNING");
        List<String> patrol = CampaignSystem.campaignFleetPostureForecastLines(ctx, "COMBAT_PATROL");

        assertTrue(silent.stream().anyMatch(line -> line.contains("Intercept risk -10%")));
        assertTrue(silent.stream().anyMatch(line -> line.startsWith("10s burn  Fuel ")));
        assertTrue(silent.stream().anyMatch(line -> line.startsWith("Sweep radius ")));
        assertTrue(silent.stream().anyMatch(line -> line.startsWith("Exposure/sweep ")));
        assertTrue(silent.stream().anyMatch(line -> line.startsWith("Detection drift ") && line.contains("Contact-event bias")));
        assertNotEquals(silent, patrol);

        assertTrue(CampaignSystem.setSelectedFleetPosture(ctx, "COMBAT_PATROL"));
        assertTrue(ctx.campaign.theaterWarRecentEvents.stream()
                .anyMatch(line -> line.contains("POSTURE CHANGED: Combat Patrol")));
        assertTrue(CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                .anyMatch(line -> line.contains("campaign.posture.changed") && line.contains("COMBAT_PATROL")));
    }

    @Test
    void routeForecastAndRealizedResourceUseAgreeWithinRounding() throws Exception {
        GameContext ctx = campaign(63003L);
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignFuel = 500;
        st.campaignSupplies = 500;
        st.campaignAmmo = 500;
        CampaignSystem.setSelectedFleetPosture(ctx, "LOGISTICS_CONSERVATION");
        CampaignSystem.CampaignLocation target = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> !location.id.equals(st.currentGalaxyLocationId))
                .max(Comparator.comparingDouble(location -> Math.hypot(
                        location.x - st.playerGalaxyX, location.y - st.playerGalaxyY)))
                .orElseThrow();
        st.selectedGalaxyLocationId = target.id;
        String forecast = CampaignSystem.selectedRouteAssessmentLines(ctx).stream()
                .filter(line -> line.startsWith("Route Forecast:"))
                .findFirst().orElseThrow();
        int[] expected = forecastResources(forecast);

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        int fuelBefore = st.campaignFuel;
        int suppliesBefore = st.campaignSupplies;
        int ammoBefore = st.campaignAmmo;
        double duration = st.galaxyTravel.durationSec;
        invokeTravelUpdate(ctx, st, duration + 0.01);

        assertTrue(Math.abs((fuelBefore - st.campaignFuel) - expected[0]) <= 1);
        assertTrue(Math.abs((suppliesBefore - st.campaignSupplies) - expected[1]) <= 1);
        assertTrue(Math.abs((ammoBefore - st.campaignAmmo) - expected[2]) <= 1);
        assertFalse(st.galaxyTravel.traveling);
    }

    private static int[] forecastResources(String line) {
        Matcher matcher = Pattern.compile("Fuel (\\d+)  Supplies (\\d+)  Ammo (\\d+)").matcher(line);
        assertTrue(matcher.find(), line);
        return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
    }

    private static Object nonLinkedEnemyForce(CampaignSystem.CampaignState st) throws Exception {
        Field forces = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        forces.setAccessible(true);
        for (Object force : (List<?>) forces.get(st)) {
            if (field(force, "faction") == Faction.ENEMY && (int) field(force, "linkedSearchGroupId") == 0) return force;
        }
        throw new AssertionError("expected a non-linked Red campaign force");
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        SpawnSystem.initWorld(ctx);
        try {
            Method activate = CampaignSystem.class.getDeclaredMethod("activateStrategicOvermapLayer",
                    GameContext.class, CampaignSystem.CampaignState.class, String.class);
            activate.setAccessible(true);
            activate.invoke(null, ctx, ctx.campaign, null);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        ctx.campaign.strategicOvermapMode = true;
        CampaignSystem.update(ctx, 0.1);
        return ctx;
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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
