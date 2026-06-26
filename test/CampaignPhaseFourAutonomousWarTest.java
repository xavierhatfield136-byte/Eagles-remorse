import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseFourAutonomousWarTest {

    @Test
    void ownershipTransferUpdatesTheAuthoritativeLocationAndCampaignSurfaces() throws Exception {
        GameContext ctx = initializedCampaignContext(4101L);
        CampaignSystem.CampaignLocation location = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(candidate -> candidate.ownerFaction == Faction.ENEMY)
                .findFirst().orElseThrow();
        invokeCampaign("applyAuthoritativeLocationOwnership",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class,
                        CampaignSystem.CampaignLocation.class, Faction.class, String.class},
                ctx, ctx.campaign, location, Faction.TEAM_C, "Phase 4 occupation test");

        assertEquals(Faction.TEAM_C, location.ownerFaction);
        assertEquals(CampaignSystem.CampaignControlVisualState.GREEN, location.controlState);
        assertEquals("online", location.stationServiceState);
        assertFalse(location.missionTags.isEmpty());
        assertTrue(location.stationMemoryFlags.stream().anyMatch(flag -> flag.contains("ownership changed")));
        assertTrue(ctx.campaign.theaterWarRecentEvents.stream()
                .anyMatch(line -> line.contains("CAMPAIGN BULLETIN") && line.contains(location.name)));
        assertTrue(CampaignSystem.campaignReleaseTelemetryHistory(ctx).stream()
                .anyMatch(line -> line.contains("event=campaign.ownership.changed")
                        && line.contains("location=" + location.id)));
    }

    @Test
    void majorBattleWarningShowsThirtySecondWindowParticipantsStrengthDistanceAndChoices() throws Exception {
        GameContext ctx = initializedCampaignContext(4102L);
        Object green = createForce(ctx, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Phase Four Battle Group", ctx.campaign.playerGalaxyX + 100.0, ctx.campaign.playerGalaxyY);
        Object red = createForce(ctx, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Phase Four Battle Group", ctx.campaign.playerGalaxyX + 130.0, ctx.campaign.playerGalaxyY);
        Object battle = newBattle(7001, green, red);
        addBattle(ctx.campaign, battle);

        List<String> warning = CampaignSystem.campaignBattleWarningLines(ctx, 4);
        String joined = String.join("\n", warning);
        assertTrue(joined.contains("engagement warning 30s"));
        assertTrue(joined.contains("Green Phase Four Battle Group"));
        assertTrue(joined.contains("Red Phase Four Battle Group"));
        assertTrue(joined.contains("coalition"));
        assertTrue(joined.contains("distance"));
        assertTrue(joined.contains("Follow Fleet / Join Battle / Ignore / Offer Support"));
    }

    @Test
    void followingAlliedFleetTracksDestinationChangesAndEndsOnRetreat() throws Exception {
        GameContext ctx = initializedCampaignContext(4103L);
        Object green = createForce(ctx, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Follow Group", ctx.campaign.playerGalaxyX + 600.0, ctx.campaign.playerGalaxyY - 120.0);
        int id = (int) getField(green, "id");

        assertTrue(CampaignSystem.beginFollowingCampaignForce(ctx, id));
        assertTrue(String.join("\n", CampaignSystem.campaignFollowedFleetLines(ctx)).contains("Green Follow Group"));

        setDouble(green, "x", ctx.campaign.playerGalaxyX + 900.0);
        setDouble(green, "y", ctx.campaign.playerGalaxyY - 300.0);
        invokeCampaign("updateFollowedCampaignForce",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, ctx.campaign);
        assertEquals(getDouble(green, "x"), ctx.campaign.galaxyTravel.targetX, 1e-6);
        assertEquals(getDouble(green, "y"), ctx.campaign.galaxyTravel.targetY, 1e-6);

        setEnum(green, "intent", "RETREATING");
        invokeCampaign("updateFollowedCampaignForce",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, ctx.campaign);
        assertTrue(String.join("\n", CampaignSystem.campaignFollowedFleetLines(ctx)).contains("none"));
    }

    @Test
    void yellowAlignmentMovesFromNeutralToCoercedToFriendlyWithReasons() throws Exception {
        GameContext ctx = initializedCampaignContext(4104L);
        ctx.campaign.campaignBlueYellowAlliance = false;
        ctx.campaign.yellowLiberationFleetJoined = false;
        ctx.campaign.yellowLiberationFavor = 1;
        ctx.campaign.enemyAlertLevel = 10.0;
        setAllTheaterRedInfluence(ctx.campaign, 25.0);
        assertEquals(CampaignSystem.YellowAlignment.TRANSACTIONAL_NEUTRAL,
                CampaignSystem.yellowAlignment(ctx));

        ctx.campaign.yellowLiberationFavor = 0;
        ctx.campaign.enemyAlertLevel = 90.0;
        setAllTheaterRedInfluence(ctx.campaign, 85.0);
        assertEquals(CampaignSystem.YellowAlignment.COERCED_HOSTILE,
                CampaignSystem.yellowAlignment(ctx));
        assertTrue(String.join("\n", CampaignSystem.campaignYellowAlignmentLines(ctx)).contains("Red regional pressure"));

        ctx.campaign.campaignBlueYellowAlliance = true;
        assertEquals(CampaignSystem.YellowAlignment.LIBERATED_FRIENDLY,
                CampaignSystem.yellowAlignment(ctx));
        assertTrue(String.join("\n", CampaignSystem.campaignYellowAlignmentLines(ctx)).contains("formal Blue-Yellow alliance"));
    }

    @Test
    void stalemateInterventionConsumesRealResourcesWithoutMintingFleets() throws Exception {
        GameContext ctx = initializedCampaignContext(4105L);
        ctx.campaign.theaterWarTickIndex = 24;
        Object theater = firstTheater(ctx.campaign);
        setDouble(theater, "controlScore", 0.0);
        setEnum(theater, "controlState", "CONTESTED");
        String theaterId = getField(theater, "id").toString();
        CampaignSystem.grantCampaignOre(ctx, 100);
        int oreBefore = CampaignSystem.currentCampaignOre(ctx);
        int forceCount = ctx.campaign.campaignForces.size();

        assertTrue(String.join("\n", CampaignSystem.campaignStalemateOpportunityLines(ctx))
                .contains("no free fleets generated"));
        assertTrue(CampaignSystem.resolveCampaignStalemateIntervention(ctx, theaterId, "ORE"));

        assertEquals(oreBefore - 40, CampaignSystem.currentCampaignOre(ctx));
        assertEquals(forceCount, ctx.campaign.campaignForces.size());
        assertTrue(getDouble(theater, "controlScore") > 0.0);
        assertFalse(CampaignSystem.resolveCampaignStalemateIntervention(ctx, theaterId, "ORE"),
                "the same bounded intervention must not reward twice");
    }

    @Test
    void identicalSeedsRemainDeterministicWhileDifferentActionsDiverge() throws Exception {
        GameContext a = initializedCampaignContext(4106L);
        GameContext b = initializedCampaignContext(4106L);
        assertEquals(CampaignSystem.campaignDivergenceSignature(a),
                CampaignSystem.campaignDivergenceSignature(b));

        a.campaign.campaignBlueYellowAlliance = true;
        a.campaign.yellowLiberationFavor += 4;
        assertNotEquals(CampaignSystem.campaignDivergenceSignature(a),
                CampaignSystem.campaignDivergenceSignature(b));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(a);
        GameContext restored = initializedCampaignContext(9999L);
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(a.campaign.redDirectorBrief, restored.campaign.redDirectorBrief);
        assertEquals(a.campaign.greenDirectorBrief, restored.campaign.greenDirectorBrief);
        assertEquals(a.campaign.yellowDirectorBrief, restored.campaign.yellowDirectorBrief);
    }

    @Test
    void phaseFourAuthorityReadoutsExposeTerritoryDirectorsAndAutonomousWar() {
        GameContext ctx = initializedCampaignContext(4107L);
        String authority = String.join("\n", CampaignSystem.campaignTerritoryAuthorityLines(ctx, 12));
        assertTrue(authority.contains("owner / occupation / contest / garrison / supply"));
        assertTrue(authority.contains("services"));
        String strategic = String.join("\n", CampaignSystem.campaignStrategicAuthorityLines(ctx));
        assertTrue(strategic.contains("FACTION PLANS"));
        assertTrue(strategic.contains("ONGOING BATTLES"));
    }

    private static GameContext initializedCampaignContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static Object createForce(GameContext ctx,
                                      CampaignSystem.CampaignForceKind kind,
                                      Faction faction,
                                      String name,
                                      double x,
                                      double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForceWithoutDeploymentCost",
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignForceKind.class,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class);
        method.setAccessible(true);
        Object force = method.invoke(null, ctx.campaign, kind, faction, name,
                "Phase Four Test Origin", "Phase Four autonomous objective", x, y);
        setDouble(force, "strength", 88.0);
        setDouble(force, "readiness", 84.0);
        setDouble(force, "supply", 82.0);
        setDouble(force, "ammoLevel", 79.0);
        setDouble(force, "hullIntegrity", 90.0);
        return force;
    }

    private static Object newBattle(int id, Object a, Object b) throws Exception {
        Class<?> battleClass = Class.forName("CampaignSystem$CampaignBattle");
        Constructor<?> constructor = battleClass.getDeclaredConstructor(
                int.class, Class.forName("CampaignSystem$CampaignForce"), Class.forName("CampaignSystem$CampaignForce"));
        constructor.setAccessible(true);
        return constructor.newInstance(id, a, b);
    }

    private static Object firstTheater(CampaignSystem.CampaignState state) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignTheaters");
        field.setAccessible(true);
        return ((List<?>) field.get(state)).get(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addBattle(CampaignSystem.CampaignState state, Object battle) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignBattles");
        field.setAccessible(true);
        ((List) field.get(state)).add(battle);
    }

    private static void setAllTheaterRedInfluence(CampaignSystem.CampaignState state, double value) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignTheaters");
        field.setAccessible(true);
        for (Object theater : (List<?>) field.get(state)) setDouble(theater, "redInfluence", value);
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx) throws Exception {
        return (CampaignCheckpointStore.Checkpoint) invokeCampaign("captureCheckpoint",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, int.class},
                ctx, ctx.campaign, 2);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        return (boolean) invokeCampaign("applyCheckpoint",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class},
                ctx, ctx.campaign, checkpoint);
    }

    private static Object invokeCampaign(String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static double getDouble(Object target, String name) throws Exception {
        return (double) getField(target, name);
    }

    private static void setDouble(Object target, String name, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, Enum.valueOf((Class<? extends Enum>) field.getType(), value));
    }
}
