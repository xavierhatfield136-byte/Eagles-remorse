import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicStrikeCounterplayTest {

    @Test
    void torpedoStrikeConsumesResourcesAndTriggersCounterplay() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        CampaignSystem.CampaignState st = ctx.campaign;
        Object taskForce = firstHostileTaskForce(st);
        assertNotNull(taskForce);

        int startingCharges = st.strategicTorpedoCharges;
        int startingAmmo = st.campaignAmmo;
        int startingFuel = st.campaignFuel;
        double startingAlert = st.enemyAlertLevel;
        double startingExposure = st.strategicExposureLevel;

        double x = taskForceCenterX(ctx, st, taskForce);
        double y = taskForceCenterY(ctx, st, taskForce);
        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx, x, y));

        assertTrue(st.strategicTorpedoCharges < startingCharges, "torpedo charge should be spent");
        assertTrue(st.campaignAmmo < startingAmmo, "torpedo strike should spend ammo");
        assertTrue(st.campaignFuel < startingFuel, "torpedo strike should spend fuel");
        assertTrue(st.enemyAlertLevel > startingAlert, "torpedo strike should raise alert");
        assertTrue(st.strategicExposureLevel > startingExposure, "torpedo strike should raise exposure");

        Object searchGroup = firstSearchGroup(st);
        assertNotNull(searchGroup);
        assertTrue(getBoolean(searchGroup, "visible"), "long-range strike should sharpen the search picture");
    }

    @Test
    void higherIntelImprovesSortieStrikeEffectiveness() throws Exception {
        GameContext lowIntelCtx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.CARRIER_SUPPORT_TITAN, ShipRole.CIWS_CORVETTE}
        );
        lowIntelCtx.campaign.campaignIntelLevel = 8.0;
        Object lowTarget = firstHostileTaskForce(lowIntelCtx.campaign);
        assertNotNull(lowTarget);
        double lowBefore = getDouble(lowTarget, "currentStrength");
        assertTrue(CampaignSystem.launchStrategicSortie(lowIntelCtx,
                taskForceCenterX(lowIntelCtx, lowIntelCtx.campaign, lowTarget),
                taskForceCenterY(lowIntelCtx, lowIntelCtx.campaign, lowTarget)));
        double lowDamage = lowBefore - getDouble(lowTarget, "currentStrength");

        GameContext highIntelCtx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.CARRIER_SUPPORT_TITAN, ShipRole.CIWS_CORVETTE}
        );
        highIntelCtx.campaign.campaignIntelLevel = 86.0;
        Object highTarget = firstHostileTaskForce(highIntelCtx.campaign);
        assertNotNull(highTarget);
        double highBefore = getDouble(highTarget, "currentStrength");
        assertTrue(CampaignSystem.launchStrategicSortie(highIntelCtx,
                taskForceCenterX(highIntelCtx, highIntelCtx.campaign, highTarget),
                taskForceCenterY(highIntelCtx, highIntelCtx.campaign, highTarget)));
        double highDamage = highBefore - getDouble(highTarget, "currentStrength");

        assertTrue(highDamage > lowDamage, "better intel should improve sortie damage after counterplay");
        assertTrue(highIntelCtx.campaign.campaignIntelLevel >= lowIntelCtx.campaign.campaignIntelLevel,
                "sorties should reinforce the threat picture rather than collapse it");
    }

    @Test
    void routeAssessmentImprovesWithBetterIntel() throws Exception {
        GameContext lowIntelCtx = initializedCampaignContext();
        GameContext highIntelCtx = initializedCampaignContext();
        lowIntelCtx.campaign.campaignIntelLevel = 8.0;
        highIntelCtx.campaign.campaignIntelLevel = 84.0;

        CampaignSystem.CampaignLocation destination = findLocation(lowIntelCtx, "poi-22");
        assertNotNull(destination);

        Object lowRoute = invokePrivateStatic("analyzeRoute",
                new Class<?>[]{CampaignSystem.CampaignState.class, GameContext.class, double.class, double.class, CampaignSystem.CampaignLocation.class},
                lowIntelCtx.campaign, lowIntelCtx, lowIntelCtx.campaign.playerGalaxyX, lowIntelCtx.campaign.playerGalaxyY, destination);
        Object highRoute = invokePrivateStatic("analyzeRoute",
                new Class<?>[]{CampaignSystem.CampaignState.class, GameContext.class, double.class, double.class, CampaignSystem.CampaignLocation.class},
                highIntelCtx.campaign, highIntelCtx, highIntelCtx.campaign.playerGalaxyX, highIntelCtx.campaign.playerGalaxyY, destination);

        assertTrue(getDouble(highRoute, "interceptionRisk") < getDouble(lowRoute, "interceptionRisk"),
                "better intel should reduce route danger estimates and practical interception risk");
        assertTrue(getDouble(highRoute, "logisticsPressure") < getDouble(lowRoute, "logisticsPressure"),
                "better intel should make route planning less punishing");
    }

    @Test
    void atomicStrikeCarriesPoliticalAndOperationalCost() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.CIWS_CORVETTE}
        );
        CampaignSystem.CampaignState st = ctx.campaign;
        st.greenContractFavor = 4;
        st.yellowLiberationFavor = 4;
        st.enemyAlertLevel = 20.0;
        st.strategicExposureLevel = 10.0;
        Object taskForce = firstHostileTaskForce(st);
        assertNotNull(taskForce);

        double startAlert = st.enemyAlertLevel;
        double startExposure = st.strategicExposureLevel;
        int startGreen = st.greenContractFavor;
        int startYellow = st.yellowLiberationFavor;

        assertTrue(CampaignSystem.launchStrategicAtomicStrike(ctx,
                taskForceCenterX(ctx, st, taskForce),
                taskForceCenterY(ctx, st, taskForce)));

        assertTrue(st.enemyAlertLevel > startAlert + 10.0, "atomic strike should sharply raise alert");
        assertTrue(st.strategicExposureLevel > startExposure + 10.0, "atomic strike should sharply raise exposure");
        assertTrue(st.greenContractFavor < startGreen, "atomic strike should damage Green standing");
        assertTrue(st.yellowLiberationFavor < startYellow, "atomic strike should damage Yellow standing");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static GameContext tacticalStrikeContext(int sector, ShipRole[] roles) throws Exception {
        GameContext ctx = initializedCampaignContext();
        replacePersistentFleet(ctx.campaign, roles);
        ctx.campaign.strategicOvermapMode = false;
        invokePrivateStatic("startSector", new Class<?>[]{GameContext.class, int.class}, ctx, sector);
        ctx.campaign.campaignAmmo = 160;
        ctx.campaign.campaignFuel = 140;
        ctx.campaign.campaignSupplies = 110;
        ctx.campaign.strategicTorpedoCharges = 3;
        ctx.campaign.strategicAtomicCharges = 1;
        ctx.campaign.strategicSortiesLaunched = 0;
        ctx.campaign.campaignIntelLevel = 44.0;
        ctx.campaign.strategicExposureLevel = 10.0;
        ctx.campaign.recentStrikePressure = 0.0;
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
        Class<?> entryClass = Class.forName("CampaignSystem$PersistentFleetEntry");
        Constructor<?> ctor = entryClass.getDeclaredConstructor(int.class, ShipRole.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(slotId, role, name);
    }

    private static Object firstHostileTaskForce(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicTaskForces");
        field.setAccessible(true);
        List<?> taskForces = (List<?>) field.get(st);
        for (Object taskForce : taskForces) {
            if (taskForce != null && getBoolean(taskForce, "hostile")) return taskForce;
        }
        return null;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static double taskForceCenterX(GameContext ctx, CampaignSystem.CampaignState st, Object taskForce) throws Exception {
        return (double) invokePrivateStatic("missionSubzoneCenterX",
                new Class<?>[]{GameContext.class, int.class, int.class},
                ctx, st.sector, getInt(taskForce, "currentSubzone"));
    }

    private static double taskForceCenterY(GameContext ctx, CampaignSystem.CampaignState st, Object taskForce) throws Exception {
        return (double) invokePrivateStatic("missionSubzoneCenterY",
                new Class<?>[]{GameContext.class, int.class, int.class},
                ctx, st.sector, getInt(taskForce, "currentSubzone"));
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

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }
}
