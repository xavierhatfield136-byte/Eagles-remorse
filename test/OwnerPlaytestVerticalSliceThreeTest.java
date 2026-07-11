import app.persistence.CampaignCheckpointStore;
import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestVerticalSliceThreeTest {
    @Test
    void authoritativeEconomyLedgerShowsCurrentCapacityUseAndRecoverySources() {
        GameContext ctx = campaign(64001L);
        CampaignSystem.CampaignState st = ctx.campaign;
        ctx.credits = 3210;
        st.campaignFuel = 77;
        st.campaignSupplies = 66;
        st.campaignAmmo = 55;
        st.campaignSalvage = 12;
        CampaignSystem.grantCampaignOre(ctx, 123);
        st.selectedGalaxyLocationId = farthestLocation(ctx).id;

        List<String> lines = CampaignSystem.campaignAuthoritativeEconomyLedgerLines(ctx);
        String joined = String.join("\n", lines);

        assertTrue(lines.get(0).contains("AUTHORITATIVE ECONOMY LEDGER"));
        assertTrue(joined.contains("Credits: 3210/uncapped  |  expected use "));
        assertTrue(joined.contains("Fleet Ore: 123/"));
        assertTrue(joined.contains("Yard Ore: "));
        assertTrue(joined.contains("Fuel: 77/"));
        assertTrue(joined.contains("Supplies: 66/"));
        assertTrue(joined.contains("Ammo: 55/"));
        assertTrue(joined.contains("Repair Materials: "));
        assertTrue(joined.contains("replenishment mining"));
        assertTrue(joined.contains("Ledger Rule  |  travel, repair, refit, strategic strikes, and commissions spend the displayed stores"));
        assertTrue(joined.contains("Recovery Choices  |  buy supplies/fuel"));
        assertTrue(String.join("\n", CampaignSystem.campaignResourceManagerLines(ctx))
                .contains("AUTHORITATIVE ECONOMY LEDGER"));
    }

    @Test
    void travelAndRepairSpendTheSameDisplayedStores() throws Exception {
        GameContext ctx = campaign(64002L);
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignFuel = 500;
        st.campaignSupplies = 500;
        st.campaignAmmo = 500;
        st.campaignSalvage = 30;

        st.selectedGalaxyLocationId = farthestLocation(ctx).id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        int fuelBeforeTravel = st.campaignFuel;
        int suppliesBeforeTravel = st.campaignSupplies;
        int ammoBeforeTravel = st.campaignAmmo;
        invokeTravelUpdate(ctx, st, st.galaxyTravel.durationSec + 0.01);

        assertTrue(st.campaignFuel < fuelBeforeTravel);
        assertTrue(st.campaignSupplies <= suppliesBeforeTravel);
        assertTrue(st.campaignAmmo <= ammoBeforeTravel);
        assertTrue(CampaignSystem.campaignAuthoritativeEconomyLedgerLines(ctx).stream()
                .anyMatch(line -> line.startsWith("Fuel: " + st.campaignFuel + "/")));

        GameContext repairCtx = simpleCampaign(64022L);
        CampaignSystem.CampaignState repairState = repairCtx.campaign;
        CampaignSystem.CampaignLocation repairHub = findLocation(repairCtx, "poi-05");
        repairState.selectedGalaxyLocationId = repairHub.id;
        repairState.currentGalaxyLocationId = repairHub.id;
        repairState.dockedGalaxyLocationId = repairHub.id;
        repairState.playerGalaxyX = repairHub.x;
        repairState.playerGalaxyY = repairHub.y;
        repairState.campaignSupplies = 200;
        repairState.campaignSalvage = 80;
        repairCtx.credits = 50_000;
        Object entry = firstPersistentEntry(repairState);
        setDouble(entry, "hullConditionFrac", 0.42);
        setDouble(entry, "shieldConditionFrac", 0.20);
        repairCtx.player.hp = Math.max(1, (int) Math.round(repairCtx.player.hpMax * 0.55));

        int suppliesBeforeRepair = repairState.campaignSupplies;
        int salvageBeforeRepair = repairState.campaignSalvage;
        assertTrue(CampaignSystem.openSelectedHubService(repairCtx, CampaignSystem.HubService.REPAIR));
        assertTrue(CampaignSystem.confirmSelectedHubService(repairCtx));

        assertTrue(repairState.campaignSupplies < suppliesBeforeRepair);
        assertTrue(repairState.campaignSalvage < salvageBeforeRepair);
        double hullAfter = getDouble(entry, "hullConditionFrac");
        assertTrue(hullAfter > 0.42);
        assertTrue(hullAfter < 1.0, "one ordinary repair visit should improve condition without erasing all attrition");
        assertTrue(CampaignSystem.campaignAuthoritativeEconomyLedgerLines(repairCtx).stream()
                .anyMatch(line -> line.startsWith("Supplies: " + repairState.campaignSupplies + "/")));
    }

    @Test
    void shortagesExposeRecoveryChoicesAndTransportRepairConsumesSupplies() {
        GameContext ctx = campaign(64003L);
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignFuel = 10;
        st.campaignSupplies = 4;
        st.campaignAmmo = 9;
        st.campaignSalvage = 1;
        st.selectedGalaxyLocationId = farthestLocation(ctx).id;

        String warnings = String.join("\n", CampaignSystem.campaignResourceWarningLines(ctx));
        String ledger = String.join("\n", CampaignSystem.campaignAuthoritativeEconomyLedgerLines(ctx));
        assertTrue(warnings.contains("FUEL LOW"));
        assertTrue(warnings.contains("SUPPLIES LOW"));
        assertTrue(warnings.contains("CORRECTIVE ACTION"));
        assertTrue(ledger.contains("Recovery Choices  |  buy supplies/fuel"));

        CampaignSystem.reportTransportRepairSupport(ctx, 2);
        assertTrue(CampaignSystem.campaignTransportRepairSupportLines(ctx).get(0).contains("supply drain 0.20/sec per transport"));
        int before = st.campaignSupplies;
        assertTrue(CampaignSystem.consumeTransportRepairSupport(ctx, 0.20 * 2, 6.0));
        assertTrue(st.campaignSupplies < before);
        assertFalse(CampaignSystem.consumeTransportRepairSupport(ctx, 10.0, 10.0));
        assertFalse(st.transportRepairSupportActive);
    }

    @Test
    void ledgerReconcilesAcrossCombatRearmDockingAndCheckpointRestore() throws Exception {
        GameContext ctx = campaign(64004L);
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignFuel = 300;
        st.campaignSupplies = 300;
        st.campaignAmmo = 300;
        st.campaignSalvage = 40;
        st.strategicTorpedoCharges = 2;
        CampaignSystem.grantCampaignOre(ctx, 1_200);
        ctx.credits = 60_000;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 160.0);
        setDouble(group, "y", st.playerGalaxyY + 100.0);
        setBoolean(group, "visible", true);
        setObject(group, "contactConfidence", enumConstant(fieldType(group, "contactConfidence"), "IDENTIFIED_TASK_FORCE"));
        setObject(group, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TRACKED"));
        setBoolean(group, "identified", true);
        CampaignSystem.CampaignSupportMarker marker =
                CampaignSystem.nearestSupportMarker(ctx, st.playerGalaxyX + 160.0, st.playerGalaxyY + 100.0, 220.0);
        assertNotNull(marker);
        String intel = marker.subtitle.substring(0, marker.subtitle.indexOf('|')).trim();
        CampaignSystem.selectCampaignContactTarget(ctx, marker.label, marker.subtitle, intel, marker.x, marker.y, true, true);

        int ammoBeforeStrike = st.campaignAmmo;
        int fuelBeforeStrike = st.campaignFuel;
        assertTrue(CampaignSystem.launchSelectedCampaignTorpedoStrike(ctx));
        assertTrue(st.campaignAmmo < ammoBeforeStrike);
        assertTrue(st.campaignFuel < fuelBeforeStrike);
        assertLedgerMatchesLiveStores(ctx);

        CampaignSystem.CampaignLocation rearmHub = firstLocationWithService(ctx, CampaignSystem.HubService.STRIKE_REARM);
        st.selectedGalaxyLocationId = rearmHub.id;
        st.currentGalaxyLocationId = rearmHub.id;
        st.dockedGalaxyLocationId = rearmHub.id;
        st.playerGalaxyX = rearmHub.x;
        st.playerGalaxyY = rearmHub.y;
        st.strategicTorpedoCharges = 0;
        st.strategicSortiesLaunched = 3;
        st.strategicAtomicCharges = 0;
        int oreBeforeRearm = CampaignSystem.currentCampaignOre(ctx);
        int suppliesBeforeRearm = st.campaignSupplies;
        assertTrue(CampaignSystem.openSelectedHubService(ctx, CampaignSystem.HubService.STRIKE_REARM));
        assertTrue(CampaignSystem.confirmSelectedHubService(ctx));
        assertTrue(CampaignSystem.currentCampaignOre(ctx) < oreBeforeRearm);
        assertTrue(st.campaignSupplies < suppliesBeforeRearm);
        assertTrue(st.strategicTorpedoCharges > 0);
        assertLedgerMatchesLiveStores(ctx);

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 4);
        GameContext restored = simpleCampaign(64024L);
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertLedgerMatchesLiveStores(restored);
        String restoredLedger = String.join("\n", CampaignSystem.campaignAuthoritativeEconomyLedgerLines(restored));
        assertTrue(restoredLedger.contains("Fuel: " + restored.campaign.campaignFuel + "/"));
        assertTrue(restoredLedger.contains("Supplies: " + restored.campaign.campaignSupplies + "/"));
        assertTrue(restoredLedger.contains("Ammo: " + restored.campaign.campaignAmmo + "/"));
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
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

    private static GameContext simpleCampaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation farthestLocation(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx.campaign;
        return CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> !location.id.equals(st.currentGalaxyLocationId))
                .max(Comparator.comparingDouble(location -> Math.hypot(
                        location.x - st.playerGalaxyX, location.y - st.playerGalaxyY)))
                .orElseThrow();
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        return CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static CampaignSystem.CampaignLocation firstLocationWithService(GameContext ctx, CampaignSystem.HubService service) {
        return CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location.services.contains(service))
                .findFirst()
                .orElseThrow();
    }

    private static Object firstPersistentEntry(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        List<?> entries = (List<?>) field.get(st);
        if (entries.isEmpty()) throw new AssertionError("expected persistent Blue fleet entry");
        return entries.get(0);
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        if (groups.isEmpty()) throw new AssertionError("expected seeded galaxy search group");
        return groups.get(0);
    }

    private static Class<?> fieldType(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getType();
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        throw new AssertionError("missing enum constant " + name);
    }

    private static Class<?> findNestedEnum(String simpleName) {
        for (Class<?> owner = CampaignSystem.class; owner != null; owner = owner.getSuperclass()) {
            for (Class<?> nested : owner.getDeclaredClasses()) {
                if (nested.getSimpleName().equals(simpleName)) return nested;
            }
        }
        throw new AssertionError("missing nested enum " + simpleName);
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static void assertLedgerMatchesLiveStores(GameContext ctx) {
        String ledger = String.join("\n", CampaignSystem.campaignAuthoritativeEconomyLedgerLines(ctx));
        assertTrue(ledger.contains("Fuel: " + ctx.campaign.campaignFuel + "/"));
        assertTrue(ledger.contains("Supplies: " + ctx.campaign.campaignSupplies + "/"));
        assertTrue(ledger.contains("Ammo: " + ctx.campaign.campaignAmmo + "/"));
        assertTrue(ledger.contains("Fleet Ore: " + CampaignSystem.currentCampaignOre(ctx) + "/"));
    }
}
