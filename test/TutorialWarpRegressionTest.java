import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialWarpRegressionTest {

    @Test
    void carrierWithdrawLessonUsesSafeExitToOpenRouteMap() throws Exception {
        GameContext ctx = tutorialContext();
        TutorialSystem.init(ctx, Faction.ALLY);
        ctx.player.applyHull(ShipRole.CARRIER, ctx.player.x, ctx.player.y);
        ctx.player.faction = Faction.ALLY;

        Object state = tutorialState(ctx);
        enterLesson(ctx, state, "CARRIER_AND_WARP");
        setBoolean(state, "openedFlightDeck", true);
        setBoolean(state, "launchedWing", true);
        setBoolean(state, "carrierModeChanged", true);

        assertTrue(GameplayActions.trySafeMissionExit(ctx));
        assertTrue(ctx.command.safeMissionExitPending);
        assertFalse(ctx.campaign.strategicOvermapMode);

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        Method completeSafeMissionExit = GameSimulationRuntime.class.getDeclaredMethod("completeSafeMissionExit", Ship.class);
        completeSafeMissionExit.setAccessible(true);
        completeSafeMissionExit.invoke(runtime, ctx.player);
        TutorialSystem.update(ctx, 1.0);

        assertTrue(ctx.campaign.strategicOvermapMode);
        assertFalse(ctx.campaign.galaxyEncounterActive);
        assertTrue(ctx.ui.mapOpen);
        assertTrue(TutorialSystem.hudTitle(ctx).contains("TUTORIAL"));
    }

    @Test
    void carrierWithdrawLessonDoesNotForceOldGammaWaypoint() throws Exception {
        GameContext ctx = tutorialContext();
        TutorialSystem.init(ctx, Faction.ALLY);
        ctx.player.applyHull(ShipRole.CARRIER, ctx.player.x, ctx.player.y);
        ctx.player.faction = Faction.ALLY;

        Object state = tutorialState(ctx);
        enterLesson(ctx, state, "CARRIER_AND_WARP");
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;

        TutorialSystem.update(ctx, GameContext.DT);

        assertFalse(Double.isFinite(ctx.ui.waypointX));
        assertFalse(Double.isFinite(ctx.ui.waypointY));
        assertFalse(ctx.command.playerTeleportCharging);
    }

    private static GameContext tutorialContext() {
        return new GameContext(new GameConfig(GameMode.TUTORIAL, 5000, 5000, true, 1234L, false));
    }

    private static Object tutorialState(GameContext ctx) throws Exception {
        Field statesField = TutorialSystem.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<GameContext, Object> states = (Map<GameContext, Object>) statesField.get(null);
        return states.get(ctx);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void enterLesson(GameContext ctx, Object state, String lessonName) throws Exception {
        Class<?> stateClass = state.getClass();
        Class<? extends Enum> lessonClass = (Class<? extends Enum>) Class.forName("TutorialSystem$LessonId");
        Enum lesson = Enum.valueOf((Class) lessonClass, lessonName);
        Method enterLesson = TutorialSystem.class.getDeclaredMethod("enterLesson", GameContext.class, stateClass, lessonClass, boolean.class);
        enterLesson.setAccessible(true);
        enterLesson.invoke(null, ctx, state, lesson, false);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }
}
