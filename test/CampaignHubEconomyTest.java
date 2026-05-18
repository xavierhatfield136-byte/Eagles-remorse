import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void yellowTradeConvertsSalvageIntoCreditsAndFuel() throws Exception {
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
        int startingCredits = ctx.credits;

        assertTrue(openHubService(ctx, "TRADE"));
        assertTrue(confirmHubService(ctx));

        assertTrue(ctx.credits > startingCredits);
        assertTrue(st.campaignFuel > 10);
        assertTrue(st.campaignSupplies > 10);
        assertTrue(st.campaignSalvage < 18);
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
    void openFriendlyHubHasNearbyOreAndHiredEscortsJoinPersistentFleet() {
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
