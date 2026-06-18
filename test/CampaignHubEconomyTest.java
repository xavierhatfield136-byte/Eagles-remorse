import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignHubEconomyTest {

    @Test
    void greenRepairConsumesResourcesAndRestoresPersistentFleetCondition() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenHub = findLocation(ctx, "poi-05");
        assertNotNull(greenHub);

        st.selectedGalaxyLocationId = greenHub.id;
        st.dockedGalaxyLocationId = greenHub.id;
        st.currentGalaxyLocationId = greenHub.id;
        st.campaignSupplies = 40;
        st.campaignSalvage = 20;
        ctx.credits = 5000;

        Object entry = firstPersistentEntry(st);
        assertNotNull(entry);
        setDouble(entry, "hullConditionFrac", 0.42);
        setDouble(entry, "shieldConditionFrac", 0.18);
        ctx.player.hp = Math.max(1, (int) Math.round(ctx.player.hpMax * 0.55));

        assertTrue(openHubService(ctx, "REPAIR"));
        assertTrue(confirmHubService(ctx));

        assertTrue(getDouble(entry, "hullConditionFrac") > 0.42);
        assertTrue(getDouble(entry, "shieldConditionFrac") > 0.18);
        assertTrue(ctx.player.hp > Math.round(ctx.player.hpMax * 0.55));
        assertTrue(st.campaignSupplies < 40);
        assertTrue(st.campaignSalvage < 20);
    }

    @Test
    void yellowTradeSellsSelectedOreForCreditsAndFuel() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation yellowHub = findLocation(ctx, "poi-02");
        assertNotNull(yellowHub);

        st.selectedGalaxyLocationId = yellowHub.id;
        st.dockedGalaxyLocationId = yellowHub.id;
        st.currentGalaxyLocationId = yellowHub.id;
        st.campaignSalvage = 18;
        st.campaignFuel = 10;
        st.campaignSupplies = 10;
        CampaignSystem.grantCampaignOre(ctx, 200);
        CampaignSystem.setCampaignOreSaleFraction(ctx, 0.5);
        int startingOre = CampaignSystem.currentCampaignOre(ctx);
        int selectedOre = CampaignSystem.campaignOreSaleAmount(ctx);
        int startingCredits = ctx.credits;
        int fleetBefore = st.persistentBlueFleet.size();

        assertTrue(openHubService(ctx, "TRADE"));
        assertTrue(confirmHubService(ctx));

        assertTrue(ctx.credits > startingCredits);
        assertTrue(st.campaignFuel > 10);
        assertTrue(st.campaignSupplies > 10);
        assertEquals(18, st.campaignSalvage);
        assertEquals(startingOre - selectedOre, CampaignSystem.currentCampaignOre(ctx));
        assertTrue(st.persistentBlueFleet.size() > fleetBefore, "trade desk should hire help when credits and command room allow it");
        Object hired = st.persistentBlueFleet.get(st.persistentBlueFleet.size() - 1);
        assertTrue(getString(hired, "name").contains("Escort"));
        assertTrue(getString(hired, "serviceHistory").contains("HIRED AT"));
    }

    @Test
    void friendlyInstallationsRebuildLongRangeStrikeStoresForOreAndCredits() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenHub = findLocation(ctx, "poi-05");
        assertNotNull(greenHub);

        st.selectedGalaxyLocationId = greenHub.id;
        st.dockedGalaxyLocationId = greenHub.id;
        st.currentGalaxyLocationId = greenHub.id;
        st.strategicTorpedoCharges = 0;
        st.strategicSortiesLaunched = 3;
        ctx.credits = 5000;
        CampaignSystem.grantCampaignOre(ctx, 200);

        assertTrue(greenHub.services.contains(CampaignSystem.HubService.STRIKE_REARM));
        assertTrue(openHubService(ctx, "STRIKE_REARM"));
        assertTrue(confirmHubService(ctx));

        assertTrue(st.strategicTorpedoCharges > 0);
        assertTrue(st.strategicSortiesLaunched < 3);
        assertTrue(ctx.credits < 5000);
        assertTrue(CampaignSystem.currentCampaignOre(ctx) < 200);
    }

    @Test
    void earlyShipyardCommissioningConsumesOreAndSalvageEnoughToPreventChainBuying() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation yard = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location != null && location.services.contains(CampaignSystem.HubService.SHIPYARD))
                .findFirst()
                .orElse(null);
        assertNotNull(yard);

        st.selectedGalaxyLocationId = yard.id;
        st.dockedGalaxyLocationId = yard.id;
        st.currentGalaxyLocationId = yard.id;
        st.campaignSalvage = 35;
        ctx.credits = 100_000;
        CampaignSystem.grantCampaignOre(ctx, 200);

        assertTrue(openHubService(ctx, "SHIPYARD"));
        assertTrue(confirmHubService(ctx));
        assertEquals(1, CampaignSystem.campaignYardOrders(ctx).size());
        assertTrue(CampaignSystem.currentCampaignOre(ctx) < 200);
        assertTrue(st.campaignSalvage < 35);

        assertTrue(openHubService(ctx, "SHIPYARD"));
        assertFalse(confirmHubService(ctx),
                "one early ore stockpile should not chain-buy a second hull without more salvage/ore pressure");
        assertEquals(1, CampaignSystem.campaignYardOrders(ctx).size());
    }

    @Test
    void greenFavorBuysStoresIntelRouteStabilityAndCombatSupport() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenHub = findLocation(ctx, "poi-05");
        assertNotNull(greenHub);

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.RESOURCES;
        st.selectedGalaxyLocationId = greenHub.id;
        st.playerGalaxyX = greenHub.x;
        st.playerGalaxyY = greenHub.y;
        st.greenContractFavor = 2;
        st.campaignSupplies = 8;
        st.campaignAmmo = 9;
        st.campaignIntelLevel = 20.0;
        st.enemyAlertLevel = 30.0;
        st.blueInterventionReserve = 40.0;
        int forceCountBefore = st.campaignForces.size();

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ALLY_GREEN"));

        assertEquals(1, st.greenContractFavor);
        assertTrue(st.campaignSupplies > 8);
        assertTrue(st.campaignAmmo > 9);
        assertTrue(st.campaignIntelLevel > 20.0);
        assertTrue(st.enemyAlertLevel < 30.0);
        assertTrue(st.blueInterventionReserve > 40.0);
        assertTrue(greenHub.supportRouteStabilized);
        assertTrue(st.campaignForces.size() > forceCountBefore);
    }

    @Test
    void yellowLeverageBuysFuelSalvageTradeRouteAndPressureRelief() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation yellowHub = findLocation(ctx, "poi-02");
        assertNotNull(yellowHub);

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.RESOURCES;
        st.selectedGalaxyLocationId = yellowHub.id;
        st.playerGalaxyX = yellowHub.x;
        st.playerGalaxyY = yellowHub.y;
        st.yellowLiberationFavor = 2;
        st.campaignFuel = 10;
        st.campaignSalvage = 4;
        st.recentStrikePressure = 22.0;
        st.enemyAlertLevel = 28.0;
        int creditsBefore = ctx.credits;
        int forceCountBefore = st.campaignForces.size();

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ALLY_YELLOW"));

        assertEquals(1, st.yellowLiberationFavor);
        assertTrue(ctx.credits > creditsBefore);
        assertTrue(st.campaignFuel > 10);
        assertTrue(st.campaignSalvage > 4);
        assertTrue(st.recentStrikePressure < 22.0);
        assertTrue(st.enemyAlertLevel < 28.0);
        assertTrue(yellowHub.supportRouteStabilized);
        assertTrue(st.campaignForces.size() > forceCountBefore);
    }

    @Test
    void openFriendlyHubHasNearbyOreAndHiredEscortsJoinPersistentFleet() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenHub = findLocation(ctx, "poi-01");
        assertNotNull(greenHub);

        st.selectedGalaxyLocationId = greenHub.id;
        st.playerGalaxyX = greenHub.x;
        st.playerGalaxyY = greenHub.y;
        int fleetBefore = st.persistentBlueFleet.size();

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(ctx.asteroids.size() >= 8, "friendly installations should spawn mineable ore patches nearby");

        Ship hired = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.alive && ship.faction != null
                        && ship.faction.isFriendlyTo(ctx.player.faction)
                        && ship.role == ShipRole.FRIGATE
                        && ship.name != null
                        && ship.name.contains("Green Watch"))
                .findFirst()
                .orElse(null);
        assertNotNull(hired);

        CampaignSystem.noteAmbientSupportRequest(ctx, hired);
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(st.persistentBlueFleet.size() > fleetBefore);
        assertTrue(persistentFleetContainsName(st, hired.name));
        Object entry = persistentFleetEntryByName(st, hired.name);
        assertNotNull(entry);
        assertEquals("COMMIT", getString(entry, "tacticalCommitmentId"));
        assertFalse(getBoolean(entry, "destroyed"));
        assertTrue(getString(entry, "serviceHistory").contains("HIRED AT"));
    }

    @Test
    void hiredCrossFactionShipKeepsFactionHullWhenPlayerFactionChanges() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenHub = findLocation(ctx, "poi-01");
        assertNotNull(greenHub);

        st.selectedGalaxyLocationId = greenHub.id;
        st.playerGalaxyX = greenHub.x;
        st.playerGalaxyY = greenHub.y;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));

        Ship hired = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.alive && ship.faction == Faction.TEAM_C
                        && ship.role == ShipRole.FRIGATE
                        && ship.name != null
                        && ship.name.contains("Green Watch"))
                .findFirst()
                .orElse(null);
        assertNotNull(hired);

        CampaignSystem.noteAmbientSupportRequest(ctx, hired);
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        Object entry = persistentFleetEntryByName(st, hired.name);
        assertNotNull(entry);
        assertEquals(Faction.TEAM_C.name(), getString(entry, "factionName"));

        ctx.player.faction = Faction.TEAM_D;
        invokeEnterFleetHub(ctx, st);

        Ship respawned = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && hired.name.equals(ship.name))
                .findFirst()
                .orElse(null);
        assertNotNull(respawned);
        assertEquals(Faction.TEAM_C, respawned.faction);
    }

    @Test
    void hubDockingIsStillRequiredForServices() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenHub = findLocation(ctx, "poi-05");
        assertNotNull(greenHub);

        st.selectedGalaxyLocationId = greenHub.id;
        st.dockedGalaxyLocationId = "";

        assertFalse(CampaignSystem.openSelectedHubService(ctx, CampaignSystem.HubService.REPAIR));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static Object firstPersistentEntry(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        List<?> entries = (List<?>) field.get(st);
        return entries.isEmpty() ? null : entries.get(0);
    }

    private static boolean persistentFleetContainsName(CampaignSystem.CampaignState st, String name) {
        if (st == null || name == null) return false;
        try {
            Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
            field.setAccessible(true);
            List<?> entries = (List<?>) field.get(st);
            for (Object entry : entries) {
                if (entry == null) continue;
                Field nameField = entry.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object value = nameField.get(entry);
                if (name.equals(value)) return true;
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        return false;
    }

    private static Object persistentFleetEntryByName(CampaignSystem.CampaignState st, String name) {
        if (st == null || name == null) return null;
        try {
            Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
            field.setAccessible(true);
            List<?> entries = (List<?>) field.get(st);
            for (Object entry : entries) {
                if (entry == null) continue;
                Field nameField = entry.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object value = nameField.get(entry);
                if (name.equals(value)) return entry;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static String getString(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return (value == null) ? "" : value.toString();
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void invokeEnterFleetHub(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("enterFleetHub", GameContext.class, CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st);
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

    private static boolean openHubService(GameContext ctx, String serviceName) throws Exception {
        Method method = CampaignSystem.class.getMethod("openSelectedHubService", GameContext.class, CampaignSystem.HubService.class);
        CampaignSystem.HubService service = CampaignSystem.HubService.valueOf(serviceName);
        return (boolean) method.invoke(null, ctx, service);
    }

    private static boolean confirmHubService(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getMethod("confirmSelectedHubService", GameContext.class);
        return (boolean) method.invoke(null, ctx);
    }
}
