import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignStrategicCommandHudTest {

    @Test
    void freeTravelCanBeSelectedAndStartedWithoutPoi() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(ctx, 900.0, 4200.0));
        assertTrue(CampaignSystem.hasSelectedFreeTravelTarget(ctx));
        assertNull(CampaignSystem.selectedCampaignLocation(ctx));

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(st.galaxyTravel.traveling);
        assertTrue(st.galaxyTravel.freeTravel);
        assertEquals("", st.galaxyTravel.destinationId);
        assertEquals("Free Course", st.galaxyTravel.destinationLabel);
    }

    @Test
    void transitTravelCanSurfaceDiscoveryContacts() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        int before = CampaignSystem.campaignAreasOfInterest(ctx).size();

        CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1400.0, 3200.0);
        CampaignSystem.startTravelToSelectedLocation(ctx);
        invokeTransitDiscovery(ctx, st);

        assertTrue(CampaignSystem.campaignAreasOfInterest(ctx).size() > before);
        assertNotNull(CampaignSystem.selectedCampaignLocation(ctx));
        assertTrue(CampaignSystem.selectedCampaignLocation(ctx).id.startsWith("transit-"));
    }

    @Test
    void commandHudSummariesExposeFleetResourcesAndStrikeControl() {
        GameContext ctx = initializedCampaignContext();
        List<String> fleet = CampaignSystem.campaignFleetManagerLines(ctx);
        List<String> resources = CampaignSystem.campaignResourceManagerLines(ctx);
        List<String> strikes = CampaignSystem.campaignStrikeManagerLines(ctx);
        List<String> navigation = CampaignSystem.campaignNavigationStationLines(ctx);

        assertTrue(fleet.stream().anyMatch(line -> line.startsWith("Command Hulls: ")));
        assertTrue(resources.stream().anyMatch(line -> line.startsWith("Credits: ")));
        assertTrue(strikes.stream().anyMatch(line -> line.startsWith("Torpedo Strikes Ready: ")));
        assertTrue(navigation.stream().anyMatch(line -> line.contains("Free travel")));
    }

    @Test
    void localSiteEncounterLaunchesAndReturnsWithAreaOutcome() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(resource);

        st.playerGalaxyX = resource.x;
        st.playerGalaxyY = resource.y;
        st.selectedGalaxyLocationId = resource.id;
        int oreBefore = CampaignSystem.currentCampaignOre(ctx);

        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        assertTrue(st.galaxyEncounterActive);
        assertTrue(getBooleanField(st, "galaxyAmbientEncounterActive"));
        assertTrue(st.objectiveSecured);

        assertTrue(CampaignSystem.completeMissionExtraction(ctx));
        assertFalse(st.galaxyEncounterActive);
        assertFalse(getBooleanField(st, "galaxyAmbientEncounterActive"));
        assertTrue(resource.consumed);
        assertTrue(CampaignSystem.currentCampaignOre(ctx) > oreBefore);
    }

    @Test
    void ambientLocalSitesExposeTacticalMarkersForImportantContacts() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(resource);

        st.playerGalaxyX = resource.x;
        st.playerGalaxyY = resource.y;
        st.selectedGalaxyLocationId = resource.id;

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        int expectedSubzone = CampaignSystem.missionSubzoneIndex(
                CampaignSystem.missionSubzoneColumns() / 2,
                CampaignSystem.missionSubzoneRows() / 2
        );

        List<CampaignSystem.CampaignObjectiveMarker> objectives = CampaignSystem.activeObjectiveMarkers(ctx);
        List<CampaignSystem.CampaignSupportMarker> supports = CampaignSystem.activeSupportMarkers(ctx);

        assertEquals(expectedSubzone, CampaignSystem.currentLoadedMissionSubzone(ctx));
        assertTrue(objectives.stream().anyMatch(marker -> marker.label.contains(resource.name)));
        assertTrue(supports.stream().anyMatch(marker -> marker.type == CampaignSystem.SupportMarkerType.RESOURCE));
        assertTrue(supports.stream().anyMatch(marker -> marker.type == CampaignSystem.SupportMarkerType.FACTION_CONTACT));
    }

    @Test
    void distressSiteRecoveredShipsPersistAfterExtraction() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstLocationOfType(ctx, "DISTRESS_SIGNAL");
        assertNotNull(distress);

        st.playerGalaxyX = distress.x;
        st.playerGalaxyY = distress.y;
        st.selectedGalaxyLocationId = distress.id;
        int fleetBefore = st.persistentBlueFleet.size();

        assertTrue(CampaignSystem.launchSelectedLocalEncounter(ctx));
        Ship support = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.name != null
                        && (ship.name.contains("Distress") || ship.name.contains("Relief") || ship.name.contains("Lost")))
                .findFirst()
                .orElse(null);
        assertNotNull(support);
        CampaignSystem.noteAmbientSupportRequest(ctx, support);
        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertTrue(st.persistentBlueFleet.size() > fleetBefore);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeTransitDiscovery(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "spawnTransitDiscovery",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static CampaignSystem.CampaignLocation firstLocationOfType(GameContext ctx, String typeName) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.type.name().equals(typeName)) return location;
        }
        return null;
    }

    private static boolean getBooleanField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
