import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandSchoolOverworldExpansionTest {

    @Test
    void tutorialModeStartsWithSafeSampleOverworld() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.campaign);
        assertTrue(ctx.campaign.commandSchoolTraining);
        assertTrue(ctx.campaign.strategicOvermapMode);
        assertEquals(GameState.MAP, ctx.state);

        CampaignSystem.CampaignLocation anchorage = location(ctx, CampaignSystem.COMMAND_SCHOOL_ANCHORAGE_ID);
        CampaignSystem.CampaignLocation hub = location(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        CampaignSystem.CampaignLocation ore = location(ctx, CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID);
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);

        assertNotNull(anchorage);
        assertNotNull(hub);
        assertNotNull(ore);
        assertNotNull(red);
        assertEquals(Faction.ALLY, anchorage.ownerFaction);
        assertEquals(Faction.BRIGHT_YELLOW, hub.ownerFaction);
        assertEquals(Faction.ENEMY, red.ownerFaction);
        assertEquals(CampaignSystem.CampaignLocationType.RESOURCE_ZONE, ore.type);
        assertFalse(CampaignSystem.persistCheckpointForMenuExit(ctx),
                "Command School must not save over or create a normal campaign checkpoint");
    }

    @Test
    void sampleOverworldUsesRealSelectionTravelAndArrivalState() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        CampaignSystem.CampaignLocation hub = location(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
        assertNotNull(hub);

        assertTrue(CampaignSystem.selectCampaignLocation(ctx, hub.x, hub.y));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "PLOT_COURSE"));
        assertEquals("PLOT_COURSE", ctx.campaign.commandSchoolLastActionId);
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_COURSE"));
        assertTrue(ctx.campaign.galaxyTravel.traveling);

        for (int i = 0; i < 30 && ctx.campaign.galaxyTravel.traveling; i++) {
            CampaignSystem.update(ctx, 1.0);
        }

        assertEquals(CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID, ctx.campaign.currentGalaxyLocationId);
        assertFalse(ctx.campaign.galaxyTravel.traveling);
    }

    @Test
    void commandSchoolEnterSiteTransitionsIntoTacticalSchoolWithoutOverworldEject() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "OVERWORLD_TO_MISSION");
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        assertNotNull(red);
        ctx.campaign.playerGalaxyX = red.x;
        ctx.campaign.playerGalaxyY = red.y;
        ctx.campaign.currentGalaxyLocationId = red.id;
        ctx.campaign.dockedGalaxyLocationId = red.id;
        ctx.campaign.selectedGalaxyLocationId = red.id;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENTER_SITE"));
        assertTrue(ctx.campaign.galaxyAmbientEncounterActive);
        assertFalse(ctx.campaign.strategicOvermapMode);

        for (int i = 0; i < 8; i++) {
            TutorialSystem.update(ctx, GameContext.DT);
            CampaignSystem.update(ctx, GameContext.DT);
            assertFalse(ctx.campaign.strategicOvermapMode, "training mission ejected on frame " + i);
            assertTrue(ctx.campaign.galaxyAmbientEncounterActive, "training site ended on frame " + i);
        }
        assertTrue(TutorialSystem.hudTitle(ctx).contains("COMMAND SCHOOL"));
        assertTrue(TutorialSystem.hudDetail(ctx).contains("Tactical Command School"));
    }

    @Test
    void tutorialMissionMapUsesSubzoneFramingAndArrowPan() throws Exception {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        Object tutorialState = tutorialState(ctx);
        setLesson(ctx, tutorialState, "OVERWORLD_TO_MISSION");
        CampaignSystem.CampaignLocation red = location(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        assertNotNull(red);
        ctx.campaign.playerGalaxyX = red.x;
        ctx.campaign.playerGalaxyY = red.y;
        ctx.campaign.currentGalaxyLocationId = red.id;
        ctx.campaign.dockedGalaxyLocationId = red.id;
        ctx.campaign.selectedGalaxyLocationId = red.id;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENTER_SITE"));
        if (!ctx.ui.mapOpen) UISystem.toggleMap(ctx);
        double before = UISystem.strategicMapFocusX(ctx);
        boolean panLeft = before > ctx.WORLD_W * 0.5;
        ctx.cameraPanLeft = panLeft;
        ctx.cameraPanRight = !panLeft;

        UISystem.updateStrategicMapCameraPan(ctx, 1.0);

        assertTrue(CampaignSystem.usesMissionSubzones(ctx), "tutorial missions should use campaign mission map framing");
        assertTrue(UISystem.strategicMapViewWidth(ctx) < ctx.WORLD_W,
                "tutorial mission map should frame the active training sector instead of the full world");
        double after = UISystem.strategicMapFocusX(ctx);
        assertTrue(panLeft ? after < before : after > before,
                "arrow keys should pan the open tutorial mission map");
    }

    @Test
    void commandSchoolLessonsCanBeSkippedAndArchived() {
        GameContext ctx = tutorialContext();
        SpawnSystem.initWorld(ctx);
        String before = TutorialSystem.hudTitle(ctx);

        TutorialSystem.skipCurrent(ctx);
        String after = TutorialSystem.hudTitle(ctx);
        assertNotEquals(before, after);

        TutorialSystem.toggleArchive(ctx);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        try {
            TutorialSystem.drawOverlay(ctx, g2, 1280, 720);
        } finally {
            g2.dispose();
        }
    }

    private static GameContext tutorialContext() {
        return new GameContext(new GameConfig(GameMode.TUTORIAL, 5000, 5000, true, 8181L, false));
    }

    private static CampaignSystem.CampaignLocation location(GameContext ctx, String id) {
        List<CampaignSystem.CampaignLocation> main = CampaignSystem.mainCampaignLocations(ctx);
        for (CampaignSystem.CampaignLocation location : main) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static Object tutorialState(GameContext ctx) throws Exception {
        Field statesField = TutorialSystem.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<GameContext, Object> states = (Map<GameContext, Object>) statesField.get(null);
        return states.get(ctx);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setLesson(GameContext ctx, Object state, String lessonName) throws Exception {
        Class<?> stateClass = state.getClass();
        Class<? extends Enum> lessonClass = (Class<? extends Enum>) Class.forName("TutorialSystem$LessonId");
        Enum lesson = Enum.valueOf((Class) lessonClass, lessonName);
        java.lang.reflect.Method enterLesson = TutorialSystem.class.getDeclaredMethod(
                "enterLesson", GameContext.class, stateClass, lessonClass, boolean.class);
        enterLesson.setAccessible(true);
        enterLesson.invoke(null, ctx, state, lesson, false);
    }
}
