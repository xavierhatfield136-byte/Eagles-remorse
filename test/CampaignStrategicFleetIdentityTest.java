import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicFleetIdentityTest {

    @Test
    void mixedFleetGetsHigherRoleCoverageBonusThanMonoHeavyFleet() throws Exception {
        GameContext mixedCtx = initializedCampaignContext();
        replacePersistentFleet(mixedCtx.campaign,
                ShipRole.CARRIER,
                ShipRole.STEALTH_SHIP,
                ShipRole.BATTLECRUISER,
                ShipRole.HAULER,
                ShipRole.CIWS_CORVETTE);

        GameContext heavyCtx = initializedCampaignContext();
        replacePersistentFleet(heavyCtx.campaign,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLECRUISER);

        double mixedBonus = extractRoleCoverageBonus(CampaignSystem.strategicFleetRoleSummaryLines(mixedCtx));
        double heavyBonus = extractRoleCoverageBonus(CampaignSystem.strategicFleetRoleSummaryLines(heavyCtx));

        assertTrue(mixedBonus > heavyBonus, "mixed fleet should receive a stronger role coverage bonus");
    }

    @Test
    void stealthFleetSoftensContactConfidenceAtTheSameDetectionRange() throws Exception {
        GameContext stealthCtx = initializedCampaignContext();
        replacePersistentFleet(stealthCtx.campaign,
                ShipRole.STEALTH_SHIP,
                ShipRole.STEALTH_SHIP,
                ShipRole.COMMAND_INTEL_TITAN,
                ShipRole.CIWS_CORVETTE,
                ShipRole.PICKET);

        GameContext heavyCtx = initializedCampaignContext();
        replacePersistentFleet(heavyCtx.campaign,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLESHIP,
                ShipRole.DREADNOUGHT,
                ShipRole.BULWARK_TITAN,
                ShipRole.VANGUARD_TITAN);

        Object stealthGroup = firstSearchGroup(stealthCtx.campaign);
        Object heavyGroup = firstSearchGroup(heavyCtx.campaign);
        assertNotNull(stealthGroup);
        assertNotNull(heavyGroup);

        double detection = getDouble(heavyGroup, "detectionRange");
        placeGroupAtDetectionEdge(stealthCtx, stealthGroup, detection * 0.95);
        placeGroupAtDetectionEdge(heavyCtx, heavyGroup, detection * 0.95);

        invokePrivateStatic("updateGalaxySearchGroups",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                stealthCtx, stealthCtx.campaign, 0.1);
        invokePrivateStatic("updateGalaxySearchGroups",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                heavyCtx, heavyCtx.campaign, 0.1);

        int stealthRank = confidenceRank(getEnumName(stealthGroup, "contactConfidence"));
        int heavyRank = confidenceRank(getEnumName(heavyGroup, "contactConfidence"));
        assertTrue(stealthRank < heavyRank, "stealth-heavy fleet should be harder to classify at the same range");
    }

    @Test
    void carrierHeavyFleetGetsMoreSortieCapacityThanLineFleet() throws Exception {
        GameContext carrierCtx = initializedCampaignContext();
        replacePersistentFleet(carrierCtx.campaign,
                ShipRole.CARRIER,
                ShipRole.DRONE_CARRIER,
                ShipRole.CARRIER_SUPPORT_TITAN,
                ShipRole.FRIGATE,
                ShipRole.CIWS_CORVETTE);

        GameContext lineCtx = initializedCampaignContext();
        replacePersistentFleet(lineCtx.campaign,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLECRUISER,
                ShipRole.CRUISER,
                ShipRole.FRIGATE,
                ShipRole.CIWS_CORVETTE);

        int carrierCap = (int) invokePrivateStatic("strategicSortieCapacity",
                new Class<?>[]{GameContext.class},
                carrierCtx);
        int lineCap = (int) invokePrivateStatic("strategicSortieCapacity",
                new Class<?>[]{GameContext.class},
                lineCtx);

        assertTrue(carrierCap > lineCap, "carrier projection should expand strategic sortie capacity");
        assertTrue(carrierCap > 0, "carrier fleet should have strategic sorties available");
    }

    @Test
    void heavyConcentrationRaisesRoutePressureComparedToBalancedFleet() throws Exception {
        GameContext balancedCtx = initializedCampaignContext();
        replacePersistentFleet(balancedCtx.campaign,
                ShipRole.CARRIER,
                ShipRole.STEALTH_SHIP,
                ShipRole.BATTLECRUISER,
                ShipRole.HAULER,
                ShipRole.CIWS_CORVETTE);

        GameContext heavyCtx = initializedCampaignContext();
        replacePersistentFleet(heavyCtx.campaign,
                ShipRole.BATTLECRUISER,
                ShipRole.BATTLESHIP,
                ShipRole.DREADNOUGHT,
                ShipRole.BULWARK_TITAN,
                ShipRole.VANGUARD_TITAN);

        CampaignSystem.CampaignLocation destination = findLocation(heavyCtx, "poi-21");
        assertNotNull(destination);

        Object balancedRoute = invokePrivateStatic("analyzeRoute",
                new Class<?>[]{CampaignSystem.CampaignState.class, GameContext.class, double.class, double.class, CampaignSystem.CampaignLocation.class},
                balancedCtx.campaign, balancedCtx, balancedCtx.campaign.playerGalaxyX, balancedCtx.campaign.playerGalaxyY, destination);
        Object heavyRoute = invokePrivateStatic("analyzeRoute",
                new Class<?>[]{CampaignSystem.CampaignState.class, GameContext.class, double.class, double.class, CampaignSystem.CampaignLocation.class},
                heavyCtx.campaign, heavyCtx, heavyCtx.campaign.playerGalaxyX, heavyCtx.campaign.playerGalaxyY, destination);

        double balancedLogistics = getDouble(balancedRoute, "logisticsPressure");
        double heavyLogistics = getDouble(heavyRoute, "logisticsPressure");
        double balancedRisk = getDouble(balancedRoute, "interceptionRisk");
        double heavyRisk = getDouble(heavyRoute, "interceptionRisk");
        double balancedDuration = getDouble(balancedRoute, "durationSec");
        double heavyDuration = getDouble(heavyRoute, "durationSec");

        assertTrue(heavyLogistics > balancedLogistics, "heavy concentration should increase logistics pressure");
        assertTrue(heavyRisk > balancedRisk, "heavy concentration should increase interception pressure");
        assertTrue(heavyDuration > balancedDuration, "heavy concentration should slow strategic transit");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void replacePersistentFleet(CampaignSystem.CampaignState st, ShipRole... roles) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) field.get(st);
        entries.clear();
        int slotId = 1;
        for (ShipRole role : roles) {
            entries.add(newPersistentEntry(slotId++, role, role.name().replace('_', ' ')));
        }
    }

    private static Object newPersistentEntry(int slotId, ShipRole role, String name) throws Exception {
        Class<?> entryClass = Class.forName("CampaignSystemModels$PersistentFleetEntry");
        Constructor<?> ctor = entryClass.getDeclaredConstructor(int.class, ShipRole.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(slotId, role, name);
    }

    private static double extractRoleCoverageBonus(List<String> lines) {
        for (String line : lines) {
            if (line != null && line.startsWith("Role Coverage Bonus: x")) {
                return Double.parseDouble(line.substring("Role Coverage Bonus: x".length()).trim());
            }
        }
        return 1.0;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static void placeGroupAtDetectionEdge(GameContext ctx, Object group, double offset) throws Exception {
        double x = ctx.campaign.playerGalaxyX + offset;
        double y = ctx.campaign.playerGalaxyY;
        setDouble(group, "x", x);
        setDouble(group, "y", y);
        setDouble(group, "targetX", x);
        setDouble(group, "targetY", y);
        setDouble(group, "stateTimer", 999.0);
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static Object invokePrivateStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static int confidenceRank(String value) {
        return switch (value) {
            case "POSSIBLE_PATROL" -> 1;
            case "LOST_CONTACT" -> 2;
            case "CONFIRMED_HOSTILE" -> 3;
            case "IDENTIFIED_TASK_FORCE" -> 4;
            default -> 0;
        };
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static String getEnumName(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return (value == null) ? "" : value.toString();
    }
}
