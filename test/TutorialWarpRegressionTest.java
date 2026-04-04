import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialWarpRegressionTest {

    @Test
    void carrierWarpLessonLocksWaypointToGammaWhenWarpStepUnlocks() throws Exception {
        GameContext ctx = tutorialContext();
        TutorialSystem.init(ctx, Faction.ALLY);
        ctx.player.applyHull(ShipRole.CARRIER, ctx.player.x, ctx.player.y);
        ctx.player.faction = Faction.ALLY;

        Object state = tutorialState(ctx);
        enterLesson(ctx, state, "CARRIER_AND_WARP");
        setBoolean(state, "openedFlightDeck", true);
        setBoolean(state, "launchedWing", true);
        setBoolean(state, "carrierModeChanged", true);
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;

        TutorialSystem.update(ctx, GameContext.DT);

        assertEquals(getDouble(state, "gammaX"), ctx.ui.waypointX, 1e-6);
        assertEquals(getDouble(state, "gammaY"), ctx.ui.waypointY, 1e-6);
        assertTrue(getBoolean(state, "gammaWaypointSet"));
    }

    @Test
    void carrierWarpLessonWarpUsesGammaInsteadOfBaseFallback() throws Exception {
        GameContext ctx = tutorialContext();
        TutorialSystem.init(ctx, Faction.ALLY);
        ctx.player.applyHull(ShipRole.CARRIER, ctx.player.x, ctx.player.y);
        ctx.player.faction = Faction.ALLY;

        Object state = tutorialState(ctx);
        enterLesson(ctx, state, "CARRIER_AND_WARP");
        setBoolean(state, "openedFlightDeck", true);
        setBoolean(state, "launchedWing", true);
        setBoolean(state, "carrierModeChanged", true);
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;

        TutorialSystem.update(ctx, GameContext.DT);
        GameplayActions.tryTeleportToBase(ctx);

        assertTrue(ctx.player.isWarpCharging());
        assertEquals(getDouble(state, "gammaX"), ctx.player.warpExitX(), 1e-6);
        assertEquals(getDouble(state, "gammaY"), ctx.player.warpExitY(), 1e-6);
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

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }
}
