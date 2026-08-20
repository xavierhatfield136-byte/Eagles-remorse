import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignOvermapEncounterFlowTest {

    @Test
    void campaignStartsDirectlyInStrategicOvermapLayer() {
        GameContext ctx = initializedCampaignContext();

        assertTrue(CampaignSystem.isStrategicGalaxyMapMode(ctx));
        assertTrue(ctx.ui.mapOpen);
        assertEquals(GameState.MAP, ctx.state);
        assertEquals(1, ctx.ships.size());
        assertTrue(ctx.campaign.strategicOvermapMode);
    }

    @Test
    void campaignClockSlowsForLocationMenusAndStopsForEncounterDecisions() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.introSequenceActive = false;
        st.sectorElapsed = 0.0;

        double start = st.sectorElapsed;
        CampaignSystem.update(ctx, 10.0);
        assertEquals(start + 10.0, st.sectorElapsed, 0.001);
        assertEquals(1.0, CampaignSystem.campaignTimeScale(ctx), 0.001,
                () -> "unexpected prompt: " + ctx.ui.strategicEncounterPrompt.kind
                        + " title=" + ctx.ui.strategicEncounterPrompt.title
                        + " location=" + ctx.ui.strategicEncounterPrompt.location);

        ctx.ui.showCampaignHubMenu("test-hub", "REPAIR");
        CampaignSystem.update(ctx, 10.0);
        assertEquals(start + 11.0, st.sectorElapsed, 0.001);
        assertEquals(0.10, CampaignSystem.campaignTimeScale(ctx), 0.001);

        ctx.ui.clearCampaignHubMenu();
        ctx.ui.showGalaxySearchGroupEncounterPrompt(1, "CONTACT", "", "", "");
        CampaignSystem.update(ctx, 10.0);
        assertEquals(start + 11.0, st.sectorElapsed, 0.001);
        assertEquals(0.0, CampaignSystem.campaignTimeScale(ctx), 0.001);
    }

    @Test
    void manualEncounterCommitLatchesUntilStrategicOvermapReturns() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX);
        setDouble(group, "y", st.playerGalaxyY);
        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertTrue(st.manualEncounterCommitInProgress);
        assertFalse(st.strategicOvermapMode);
        assertFalse(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
    }

    @Test
    void staleSecondPromptDuringManualEncounterDismissesInsteadOfLockingInput() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.manualEncounterCommitInProgress = true;
        st.strategicOvermapMode = false;
        ctx.ui.showStrategicEncounterPrompt(99, "CONTACT: RED PATROL GROUP", "", "", "");
        ctx.state = GameState.PAUSED;

        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(GameState.RUNNING, ctx.state);
        assertTrue(st.manualEncounterCommitInProgress);
    }

    @Test
    void tacticalManualEntryAutoJoinsSecondStrategicTaskForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 2;
        Object taskForce = firstStrategicTaskForce(ctx, st);
        assertNotNull(taskForce);
        st.manualEncounterCommitInProgress = true;
        st.strategicOvermapMode = false;
        setInt(taskForce, "currentSubzone", 0);

        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateStrategicTaskForceEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                taskForce.getClass(),
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, taskForce, 0);

        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(getBoolean(taskForce, "encounterSpawned"));
        assertTrue(ctx.eventBanner.contains("NEW ENEMY TASK FORCE HAS ARRIVED"));
    }

    @Test
    void defeatedStrategicTaskForceIsRemovedFromOvermapAfterManualBattle() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 2;
        Object taskForce = firstStrategicTaskForce(ctx, st);
        assertNotNull(taskForce);
        int taskForceId = getInt(taskForce, "id");
        String label = getObject(taskForce, "label").toString();

        ctx.ui.showStrategicEncounterPrompt(taskForceId, "CONTACT: " + label, "", "", "");
        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertTrue(getBoolean(taskForce, "encounterSpawned"));

        @SuppressWarnings("unchecked")
        java.util.Set<Integer> spawnedShipIds = (java.util.Set<Integer>) getObject(taskForce, "spawnedShipIds");
        assertFalse(spawnedShipIds.isEmpty(), "manual task-force command should spawn tracked enemy ships");
        for (Ship ship : ctx.ships) {
            if (ship == null || !spawnedShipIds.contains(ship.id)) continue;
            ship.hp = 0;
            ship.alive = false;
            ship.dying = true;
        }

        invokeFinishGalaxyEncounterAndReturn(ctx, st);

        assertTrue(getBoolean(taskForce, "encounterResolved"),
                "defeated strategic task forces should be resolved before the overworld is shown again");
        assertFalse(getBoolean(taskForce, "encounterSpawned"));
        assertTrue(((java.util.Set<?>) getObject(taskForce, "spawnedShipIds")).isEmpty());
        assertTrue(CampaignSystem.strategicTaskForceMarkers(ctx).stream()
                        .noneMatch(marker -> marker != null && label.equals(marker.label)),
                "resolved Red patrol/strike task forces should not keep overworld markers");

        invokeInitializeStrategicTaskForces(ctx, st);

        assertNull(strategicTaskForceByLabel(st, label),
                "defeated strategic task forces should not be recreated when the strategic layer is rebuilt");
        assertTrue(CampaignSystem.strategicTaskForceMarkers(ctx).stream()
                        .noneMatch(marker -> marker != null && label.equals(marker.label)),
                "rebuilt strategic task-force markers should still exclude defeated contacts");
    }

    @Test
    void manualStrategicTaskForceCommandDoesNotSpawnZeroShipEncounter() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 2;
        Object taskForce = firstStrategicTaskForce(ctx, st);
        assertNotNull(taskForce);
        int taskForceId = getInt(taskForce, "id");
        String label = getObject(taskForce, "label").toString();

        setDouble(taskForce, "maxStrength", 100.0);
        setDouble(taskForce, "currentStrength", 10.0);
        ctx.ui.showStrategicEncounterPrompt(taskForceId, "CONTACT: " + label, "", "", "");

        assertFalse(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx),
                "task forces below all spawn thresholds should be resolved instead of launching an empty battle");
        assertTrue(getBoolean(taskForce, "encounterResolved"));
        assertFalse(getBoolean(taskForce, "encounterSpawned"));
        assertTrue(((java.util.Set<?>) getObject(taskForce, "spawnedShipIds")).isEmpty());
        assertFalse(st.galaxyEncounterActive);
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
    }

    @Test
    void changingEncounterPromptKindsClearsPriorIdentifiers() {
        GameContext ctx = initializedCampaignContext();
        UiState ui = ctx.ui;
        ui.showStrategicEncounterPrompt(7, "TASK FORCE", "", "", "");
        ui.showCampaignForceEncounterPrompt(11, "CAMPAIGN FORCE", "", "", "");

        assertEquals(-1, ui.strategicEncounterPrompt.taskForceId);
        assertEquals(11, ui.strategicEncounterPrompt.campaignForceId);
        assertTrue(CampaignSystem.hasPendingStrategicEncounterChoice(ctx));

        ui.showCampaignBattleInterventionPrompt(13, "BATTLE", "", "", "");
        assertEquals(-1, ui.strategicEncounterPrompt.campaignForceId);
        assertEquals(13, ui.strategicEncounterPrompt.campaignBattleId);
    }

    @Test
    void hostileSearchGroupInterceptionUsesDedicatedPromptAndAutoResolveReturnsToOvermap() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX);
        setDouble(group, "y", st.playerGalaxyY);

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(getInt(group, "id"), ctx.ui.strategicEncounterPrompt.galaxySearchGroupId);

        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));

        assertTrue(st.strategicOvermapMode);
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(ctx.ui.mapOpen);
        assertEquals(0, st.activeGalaxyEncounterSearchGroupId);
        assertFalse(st.galaxyEncounterActive);
        assertEquals("RETURNING", getObject(group, "behavior").toString());
    }

    @Test
    void hostileSearchGroupInterceptionInOpenSpaceStillCreatesEncounterPrompt() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        setDouble(group, "x", 2500.0);
        setDouble(group, "y", 2500.0);

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals("Open-space intercept", ctx.ui.strategicEncounterPrompt.location);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.toLowerCase().contains("route intercept"));
        assertTrue(ctx.ui.strategicEncounterPrompt.body.toLowerCase().contains("compact three-zone"));
    }

    @Test
    void visibleHostileCampaignForceAtPlayerMarkerCreatesDirectCombatPrompt() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object playerForce = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PLAYER_FLEET, Faction.ALLY,
                "Blue Command Fleet", "Blue command flagship", "Lead the campaign fleet", 120.0, 120.0);
        setDouble(playerForce, "x", 120.0);
        setDouble(playerForce, "y", 120.0);
        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Shadow", "Red route contact", "close with Blue command", 2500.0, 2500.0);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 60.0);
        setDouble(hostile, "contactConfidence", 0.42);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.SUSPECTED);
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(hostile, "id"), "Shadow Patrol");

        invokeForceEncounterUpdate(ctx, st);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(getInt(hostile, "id"), ctx.ui.strategicEncounterPrompt.campaignForceId);
        assertEquals(2500.0, getDouble(playerForce, "x"), 0.001,
                "direct contact checks should sync the Blue force to the live overmap player marker");
    }

    @Test
    void hostileFleetOverlapStartsCombatEvenDuringOpeningGrace() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 30.0;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT, Faction.ENEMY,
                "Regression Red Opening Interceptor", "Red close intercept", "force contact with Blue command",
                st.playerGalaxyX + 40.0, st.playerGalaxyY);
        setBoolean(hostile, "simulationActive", true);
        setEnumByName(hostile, "mission", "INTERCEPT");
        setEnumByName(hostile, "intent", "INTERCEPTING");
        setEnumByName(hostile, "state", "MOVING");
        setDouble(hostile, "strength", 62.0);
        setDouble(hostile, "contactConfidence", 0.82);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.MISSILE_BOAT, getInt(hostile, "id"), "Opening Interceptor");

        invokeForceEncounterUpdate(ctx, st);

        assertTrue(ctx.ui.strategicEncounterPrompt.active,
                "opening grace should not let a confirmed hostile overlap the player indefinitely");
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(getInt(hostile, "id"), ctx.ui.strategicEncounterPrompt.campaignForceId);
    }

    @Test
    void campaignForceEncounterPullsVisibleFleetsInsideHalfPlayerSensorRadiusOnly() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        double sensorRange = CampaignSystem.playerCampaignSensorRange(ctx);
        double hardJoinRange = sensorRange * 0.5;

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Primary Contact", "Red base lane", "primary intercept",
                st.playerGalaxyX + 40.0, st.playerGalaxyY);
        Object nearby = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT, Faction.ENEMY,
                "Regression Red Nearby Reinforcement", "Red base lane", "join sensor battle",
                st.playerGalaxyX + Math.min(900.0, hardJoinRange - 120.0), st.playerGalaxyY + 80.0);
        Object distant = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT, Faction.ENEMY,
                "Regression Red Distant Reinforcement", "Red base lane", "too far to join",
                st.playerGalaxyX + hardJoinRange + 160.0, st.playerGalaxyY);
        for (Object force : List.of(primary, nearby, distant)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 70.0);
            setDouble(force, "readiness", 80.0);
            setDouble(force, "contactConfidence", 0.92);
            setDouble(force, "lastKnownAgeSec", 0.0);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        }
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(primary, "id"), "Primary Sensor Patrol");
        addPoolRecord(st, Faction.ENEMY, ShipRole.MISSILE_BOAT, getInt(nearby, "id"), "Nearby Sensor Spear");
        addPoolRecord(st, Faction.ENEMY, ShipRole.MISSILE_BOAT, getInt(distant, "id"), "Distant Sensor Spear");

        assertTrue(launchCampaignForceEncounter(ctx, st, primary));

        assertTrue(hasTacticalShipForCampaignForce(st, getInt(primary, "id")),
                "the primary force should spawn into the tactical battle");
        assertTrue(hasTacticalShipForCampaignForce(st, getInt(nearby, "id")),
                "visible fleets inside half the player sensor circle should join the same battle");
        assertFalse(hasTacticalShipForCampaignForce(st, getInt(distant, "id")),
                "fleets outside half the player sensor circle must not be pulled into combat");
    }

    @Test
    void sensorBubbleBattleJoinersAreCappedEvenWhenManyFleetsAreNearby() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Capped Primary", "Red base lane", "primary intercept",
                st.playerGalaxyX + 40.0, st.playerGalaxyY);
        setBoolean(primary, "simulationActive", true);
        setDouble(primary, "strength", 70.0);
        setDouble(primary, "readiness", 80.0);
        setDouble(primary, "contactConfidence", 0.92);
        setBoolean(primary, "visibleToPlayer", true);
        setObject(primary, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        ArrayList<Object> nearbyForces = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                    "Regression Red Sensor Crowd " + i, "Red base lane", "crowded intercept",
                    st.playerGalaxyX + 520.0 + i * 35.0, st.playerGalaxyY + (i % 3 - 1) * 80.0);
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 55.0);
            setDouble(force, "readiness", 75.0);
            setDouble(force, "contactConfidence", 0.92);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
            nearbyForces.add(force);
        }

        assertTrue(launchCampaignForceEncounter(ctx, st, primary));

        int joined = 0;
        for (Object force : nearbyForces) {
            if (hasTacticalShipForCampaignForce(st, getInt(force, "id"))) joined++;
        }
        assertTrue(joined <= 2, "sensor bubble battles should cap hostile reinforcements");
        assertTrue(joined < nearbyForces.size(), "the cap should prevent every nearby fleet from being dragged in");
    }

    @Test
    void friendlySensorBubbleJoinersUseHalfPlayerSensorRadius() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        double hardJoinRange = CampaignSystem.playerCampaignSensorRange(ctx) * 0.5;

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Primary For Friendly Pull", "Red base lane", "primary intercept",
                st.playerGalaxyX + 40.0, st.playerGalaxyY);
        Object nearbyFriendly = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Regression Green Nearby Support Pull", "Green support lane", "should join nearby contact",
                st.playerGalaxyX + Math.min(900.0, hardJoinRange - 120.0), st.playerGalaxyY + 20.0);
        Object distantFriendly = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Regression Green Distant Support Pull", "Green support lane", "should not teleport into contact",
                st.playerGalaxyX + hardJoinRange + 160.0, st.playerGalaxyY + 20.0);
        for (Object force : List.of(primary, nearbyFriendly, distantFriendly)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 70.0);
            setDouble(force, "readiness", 80.0);
            setDouble(force, "contactConfidence", 0.95);
            setDouble(force, "lastKnownAgeSec", 0.0);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        }
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(primary, "id"), "Primary Patrol");
        addPoolRecord(st, Faction.TEAM_C, ShipRole.FRIGATE, getInt(nearbyFriendly, "id"), "Nearby Green Frigate");
        addPoolRecord(st, Faction.TEAM_C, ShipRole.FRIGATE, getInt(distantFriendly, "id"), "Distant Green Frigate");

        assertTrue(launchCampaignForceEncounter(ctx, st, primary));

        assertTrue(hasTacticalShipForCampaignForce(st, getInt(primary, "id")),
                "the primary hostile force should still spawn into the tactical battle");
        assertTrue(hasTacticalShipForCampaignForce(st, getInt(nearbyFriendly, "id")),
                "friendly green forces inside half the player's detection radius should join the battle");
        assertFalse(hasTacticalShipForCampaignForce(st, getInt(distantFriendly, "id")),
                "friendly forces outside half the player's detection radius must not be pulled into combat");
    }

    @Test
    void friendlyBaseDefenseInsideHalfSensorRadiusJoinsForceEncounter() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Contact Beside Green Station", "Red base lane", "primary intercept",
                st.playerGalaxyX + 40.0, st.playerGalaxyY);
        Object stationDefense = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.BASE_DEFENSE, Faction.TEAM_C,
                "Regression Green Station Defense", "Green station base", "standing friendly station support",
                st.playerGalaxyX + 80.0, st.playerGalaxyY);
        for (Object force : List.of(primary, stationDefense)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 82.0);
            setDouble(force, "readiness", 85.0);
            setDouble(force, "contactConfidence", 0.95);
            setDouble(force, "lastKnownAgeSec", 0.0);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        }
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(primary, "id"), "Primary Station Raider");
        addPoolRecord(st, Faction.TEAM_C, ShipRole.BASE, getInt(stationDefense, "id"), "Green Station Control");
        addPoolRecord(st, Faction.TEAM_C, ShipRole.STATIC_TURRET, getInt(stationDefense, "id"), "Green Station Turret");

        assertTrue(launchCampaignForceEncounter(ctx, st, primary));

        assertTrue(hasTacticalShipForCampaignForce(st, getInt(primary, "id")),
                "the primary hostile force should still spawn into the tactical battle");
        assertTrue(hasTacticalShipForCampaignForce(st, getInt(stationDefense, "id")),
                "friendly station/base defenses inside half the player's detection radius should join the battle");
    }

    @Test
    void greenStationEmergencySupportSupplementsBlueAgainstDoomstack() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        double hardJoinRange = CampaignSystem.playerCampaignSensorRange(ctx) * 0.5;

        CampaignSystem.CampaignLocation station = new CampaignSystem.CampaignLocation(
                "regression-green-emergency-station",
                "Green Emergency Station",
                st.playerGalaxyX + 120.0,
                st.playerGalaxyY,
                CampaignSystem.CampaignLocationType.REPAIR_SITE,
                0.0f,
                false,
                0,
                "Green coalition support radius",
                CampaignSystem.HubService.REPAIR);
        station.ownerFaction = Faction.TEAM_C;
        station.facilityType = CampaignSystem.CampaignFacilityType.REPAIR_YARD;
        station.strategicValue = 5;
        station.defenseStrength = 90.0;
        station.stationServiceState = "online";
        station.stationDamageState = "intact";
        st.galaxyMainPois.add(station);

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Misrated Doomstack", "Red base lane", "misrated level three doomstack",
                st.playerGalaxyX + 40.0, st.playerGalaxyY);
        Object stationReserve = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.BASE_DEFENSE, Faction.TEAM_C,
                "Regression Green Emergency Reserve", "Green station reserve", "supplement blue against red mass",
                st.playerGalaxyX + hardJoinRange + 260.0, st.playerGalaxyY + 80.0);
        for (Object force : List.of(primary, stationReserve)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 92.0);
            setDouble(force, "readiness", 88.0);
            setDouble(force, "contactConfidence", 0.95);
            setDouble(force, "lastKnownAgeSec", 0.0);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        }
        for (int i = 0; i < 14; i++) {
            addPoolRecord(st, Faction.ENEMY,
                    i % 4 == 0 ? ShipRole.FRIGATE : ShipRole.MISSILE_BOAT,
                    getInt(primary, "id"),
                    "Misrated Doomstack Red " + i);
        }
        addPoolRecord(st, Faction.TEAM_C, ShipRole.BASE, getInt(stationReserve, "id"), "Emergency Station Control");
        addPoolRecord(st, Faction.TEAM_C, ShipRole.FRIGATE, getInt(stationReserve, "id"), "Emergency Green Frigate");
        addPoolRecord(st, Faction.TEAM_C, ShipRole.CIWS_CORVETTE, getInt(stationReserve, "id"), "Emergency Green Screen");

        assertTrue(launchCampaignForceEncounter(ctx, st, primary));

        assertTrue(hasTacticalShipForCampaignForce(st, getInt(primary, "id")),
                "the misrated hostile doomstack should spawn into the tactical battle");
        assertTrue(hasTacticalShipForCampaignForce(st, getInt(stationReserve, "id")),
                "green station emergency reserves outside the half-sensor join radius should still supplement blue");
    }

    @Test
    void generatedForceEncounterIgnoresHistoricalKillsWhenCheckingCompletion() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        st.kills = 40;
        st.lastDetectedKillCount = 40;
        st.campaignKills = 40;

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Historical Kill Contact", "Red base lane", "must not instantly end",
                st.playerGalaxyX + 500.0, st.playerGalaxyY);
        setBoolean(primary, "simulationActive", true);
        setDouble(primary, "strength", 82.0);
        setDouble(primary, "readiness", 85.0);
        setDouble(primary, "contactConfidence", 0.95);
        setDouble(primary, "lastKnownAgeSec", 0.0);
        setBoolean(primary, "visibleToPlayer", true);
        setObject(primary, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.FRIGATE, getInt(primary, "id"), "Historical Kill Red Frigate");
        addPoolRecord(st, Faction.ENEMY, ShipRole.MISSILE_BOAT, getInt(primary, "id"), "Historical Kill Red Boat");

        assertTrue(launchCampaignForceEncounter(ctx, st, primary));
        CampaignSystem.update(ctx, 0.25);

        assertTrue(st.galaxyEncounterActive,
                "generated force encounters should not auto-complete from kills earned before the fight");
        assertFalse(st.objectiveSecured,
                "fresh generated encounters should wait for current hostile losses before extraction opens");
        assertEquals(0.0, st.objectiveProgress, 0.001);
    }

    @Test
    void alliedGreenAndRedFleetOverlapFormsNpcBattle() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 30.0;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object red = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Stack Contact", "Red local intercept", "test hostile stacking",
                2600.0, 2500.0);
        Object green = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ALLY,
                "Regression Green Stack Response", "Green response patrol", "test allied response",
                2640.0, 2500.0);
        setBoolean(red, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setDouble(red, "strength", 70.0);
        setDouble(green, "strength", 70.0);
        setEnumByName(red, "intent", "INTERCEPTING");
        setEnumByName(green, "intent", "INTERCEPTING");
        setEnumByName(red, "state", "IDLE");
        setEnumByName(green, "state", "IDLE");
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(red, "id"), "Stack Red Patrol");
        addPoolRecord(st, Faction.ALLY, ShipRole.PATROL, getInt(green, "id"), "Stack Green Patrol");

        invokeNpcBattleResolution(ctx, st, 0.2);

        assertFalse(st.campaignBattles.isEmpty(), "overlapping allied and Red fleets should resolve into a battle");
        assertEquals("ENGAGING", getObject(red, "state").toString());
        assertEquals("ENGAGING", getObject(green, "state").toString());
    }

    @Test
    void adjacentSensorBattlePromptOnlyAcceptsJoinOrIgnore() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 240.0;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object red = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Adjacent Battle", "Red local intercept", "test adjacent battle",
                2600.0, 2500.0);
        Object green = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ALLY,
                "Regression Green Adjacent Battle", "Green response patrol", "test adjacent battle",
                2640.0, 2500.0);
        for (Object force : List.of(red, green)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 70.0);
            setEnumByName(force, "intent", "INTERCEPTING");
            setEnumByName(force, "state", "IDLE");
        }
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(red, "id"), "Adjacent Red Patrol");
        addPoolRecord(st, Faction.ALLY, ShipRole.PATROL, getInt(green, "id"), "Adjacent Green Patrol");

        invokeNpcBattleResolution(ctx, st, 0.2);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE, ctx.ui.strategicEncounterPrompt.kind);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.contains("Join the battle or ignore it"));
        assertFalse(CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "SUPPORT"),
                "remote battle prompts should reject support/observe/follow choices");
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "IGNORE"));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
    }

    @Test
    void distantNpcBattleDoesNotPromptJoin() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 240.0;
        st.playerGalaxyX = 600.0;
        st.playerGalaxyY = 600.0;

        Object red = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Distant Battle", "Red distant front", "test distant battle",
                3300.0, 3300.0);
        Object green = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ALLY,
                "Regression Green Distant Battle", "Green distant front", "test distant battle",
                3340.0, 3300.0);
        for (Object force : List.of(red, green)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 95.0);
            setEnumByName(force, "intent", "INTERCEPTING");
            setEnumByName(force, "state", "IDLE");
        }
        addPoolRecord(st, Faction.ENEMY, ShipRole.CRUISER, getInt(red, "id"), "Distant Red Cruiser");
        addPoolRecord(st, Faction.ALLY, ShipRole.CRUISER, getInt(green, "id"), "Distant Green Cruiser");

        invokeNpcBattleResolution(ctx, st, 0.2);

        assertFalse(st.campaignBattles.isEmpty(), "distant fleets should still form NPC battles");
        assertFalse(ctx.ui.strategicEncounterPrompt.active,
                "distant battles should not offer the player a join prompt");
    }

    @Test
    void focusedAttackModeStillShowsPhysicalFleetInvasionArrows() throws Exception {
        String previous = System.getProperty("game.feature.focused_faction_attacks");
        System.setProperty("game.feature.focused_faction_attacks", "true");
        try {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;
            CampaignSystem.CampaignLocation source = firstLocationOwnedBy(ctx, Faction.ENEMY);
            CampaignSystem.CampaignLocation target = firstMainLocationOwnedBy(ctx, Faction.TEAM_C);
            if (target == null) target = firstMainLocationOwnedBy(ctx, Faction.ALLY);
            if (target == null) target = firstMainLocationNotOwnedBy(ctx, Faction.ENEMY);
            assertNotNull(source);
            assertNotNull(target);

            Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                    "Regression Red Invasion Fleet", source.name, "raid a green zone",
                    source.x, source.y);
            setBoolean(force, "simulationActive", true);
            setEnumByName(force, "mission", "RAID");
            setEnumByName(force, "intent", "INTERCEPTING");
            setEnumByName(force, "state", "MOVING");
            setObject(force, "sourceLocationId", source.id);
            setObject(force, "homeBaseId", source.id);
            setObject(force, "destinationLocationId", target.id);
            setDouble(force, "targetX", target.x);
            setDouble(force, "targetY", target.y);
            setDouble(force, "strength", 72.0);
            setDouble(force, "contactConfidence", 0.92);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

            int forceId = getInt(force, "id");
            String targetId = target.id;
            assertTrue(CampaignSystem.campaignInvasionArrows(ctx).stream()
                            .anyMatch(arrow -> arrow.forceId == forceId
                                    && arrow.faction == Faction.ENEMY
                                    && targetId.equals(arrow.targetLocationId)),
                    "focused attack visuals should still expose live fleets moving to invade a zone");
        } finally {
            if (previous == null) System.clearProperty("game.feature.focused_faction_attacks");
            else System.setProperty("game.feature.focused_faction_attacks", previous);
        }
    }

    @Test
    void largeInvasionArrowsIgnoreSameFactionInternalMovement() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation source = firstMainLocationNotOwnedBy(ctx, Faction.ENEMY);
        CampaignSystem.CampaignLocation target = anotherMainLocation(ctx, source);
        assertNotNull(source);
        assertNotNull(target);
        source.ownerFaction = Faction.TEAM_C;
        target.ownerFaction = Faction.TEAM_C;

        Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, source.ownerFaction,
                "Regression Internal Movement Fleet", source.name, "move inside friendly territory",
                source.x, source.y);
        setBoolean(force, "simulationActive", true);
        setEnumByName(force, "mission", "RAID");
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "state", "MOVING");
        setObject(force, "sourceLocationId", source.id);
        setObject(force, "homeBaseId", source.id);
        setObject(force, "destinationLocationId", target.id);
        setDouble(force, "targetX", target.x);
        setDouble(force, "targetY", target.y);
        setDouble(force, "strength", 64.0);
        setDouble(force, "contactConfidence", 1.0);
        setBoolean(force, "visibleToPlayer", true);
        setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        int forceId = getInt(force, "id");
        assertFalse(CampaignSystem.campaignInvasionArrows(ctx).stream()
                        .anyMatch(arrow -> arrow.forceId == forceId),
                "same-faction movement should keep fleet movement arrows but not create large invasion arrows");
    }

    @Test
    void largeInvasionArrowsRequireOffensiveCrossFactionMovement() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation source = firstLocationOwnedBy(ctx, Faction.ENEMY);
        CampaignSystem.CampaignLocation target = firstMainLocationNotOwnedBy(ctx, Faction.ENEMY);
        assertNotNull(source);
        assertNotNull(target);

        Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Logistics Fleet", source.name, "cross-faction reposition without invasion",
                source.x, source.y);
        setBoolean(force, "simulationActive", true);
        setEnumByName(force, "mission", "REINFORCE");
        setEnumByName(force, "intent", "REINFORCING");
        setEnumByName(force, "state", "MOVING");
        setObject(force, "sourceLocationId", source.id);
        setObject(force, "homeBaseId", source.id);
        setObject(force, "destinationLocationId", target.id);
        setDouble(force, "targetX", target.x);
        setDouble(force, "targetY", target.y);
        setDouble(force, "strength", 68.0);
        setDouble(force, "contactConfidence", 0.9);
        setBoolean(force, "visibleToPlayer", true);
        setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        int forceId = getInt(force, "id");
        assertFalse(CampaignSystem.campaignInvasionArrows(ctx).stream()
                        .anyMatch(arrow -> arrow.forceId == forceId),
                "cross-faction logistics and reinforcement should not be promoted to strategic invasion arrows");

        setEnumByName(force, "mission", "RAID");
        assertTrue(CampaignSystem.campaignInvasionArrows(ctx).stream()
                        .anyMatch(arrow -> arrow.forceId == forceId
                                && arrow.faction == Faction.ENEMY
                                && target.id.equals(arrow.targetLocationId)),
                "an actual offensive cross-faction fleet should produce a large invasion arrow");
    }

    @Test
    void counterSortiesAndEnemyActivityAnchorsDoNotCreateLargeInvasionArrows() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation source = firstMainLocationOwnedBy(ctx, Faction.TEAM_C);
        CampaignSystem.CampaignLocation target = firstMainLocationOwnedBy(ctx, Faction.ENEMY);
        CampaignSystem.CampaignLocation enemyActivity = findAreaOfInterest(ctx, "aoi-threat-1");
        assertNotNull(source);
        assertNotNull(target);
        assertNotNull(enemyActivity);
        enemyActivity.discovered = true;

        Object sortie = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Regression Green Counter Sortie", source.name, "counter pressure",
                source.x + 40.0, source.y);
        setBoolean(sortie, "simulationActive", true);
        setEnumByName(sortie, "mission", "COUNTER_SORTIE");
        setEnumByName(sortie, "state", "MOVING");
        setObject(sortie, "sourceLocationId", source.id);
        setObject(sortie, "homeBaseId", source.id);
        setObject(sortie, "destinationLocationId", target.id);
        setDouble(sortie, "targetX", target.x);
        setDouble(sortie, "targetY", target.y);
        setDouble(sortie, "strength", 74.0);
        setDouble(sortie, "contactConfidence", 0.95);
        setBoolean(sortie, "visibleToPlayer", true);
        setObject(sortie, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        int sortieId = getInt(sortie, "id");
        assertFalse(CampaignSystem.campaignInvasionArrows(ctx).stream()
                        .anyMatch(arrow -> arrow.forceId == sortieId),
                "counter-task fleets should keep normal movement arrows, not the large invasion arrow layer");

        setEnumByName(sortie, "mission", "RAID");
        setObject(sortie, "destinationLocationId", enemyActivity.id);
        setDouble(sortie, "targetX", enemyActivity.x);
        setDouble(sortie, "targetY", enemyActivity.y);

        assertFalse(CampaignSystem.campaignInvasionArrows(ctx).stream()
                        .anyMatch(arrow -> arrow.forceId == sortieId),
                "enemy activity markers are not territorial invasion endpoints");
    }

    @Test
    void randomSiteClickWinsOverNearbyFleetMarkerAndCanBeEntered() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation site = firstRandomSite(ctx);
        assertNotNull(site);
        site.discovered = true;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Site-Side Patrol", "nearby patrol", "test click priority",
                site.x + 58.0, site.y);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 42.0);
        setDouble(hostile, "contactConfidence", 0.95);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        int hostileId = getInt(hostile, "id");
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, hostileId,
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                st.campaignIntelTick,
                st.campaignIntelTick + 20,
                0.95,
                site.x + 58.0,
                site.y,
                86.0);

        clickGalaxyMap(ctx, site.x, site.y, 1);

        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        assertNotNull(selected);
        assertEquals(site.id, selected.id,
                () -> "clicked " + site.id + " " + site.name + " but selected " + selected.id + " " + selected.name);
        assertFalse(CampaignSystem.hasSelectedCampaignContactTarget(ctx));

        st.playerGalaxyX = site.x;
        st.playerGalaxyY = site.y;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertEquals(site.id, st.dockedGalaxyLocationId);
    }

    @Test
    void nearbySiteActionsRemainAvailableWithoutExplicitSelection() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation site = firstRandomSite(ctx);
        assertNotNull(site);
        site.discovered = true;
        st.selectedGalaxyLocationId = "";
        st.selectedFreeGalaxyTargetX = Double.NaN;
        st.selectedFreeGalaxyTargetY = Double.NaN;
        st.playerGalaxyX = site.x;
        st.playerGalaxyY = site.y;

        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        assertNotNull(selected);
        assertEquals(site.id, selected.id);
        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        assertTrue(CampaignSystem.campaignVisibleActions(ctx).stream()
                .anyMatch(action -> "ENTER_SITE".equals(action.id) && action.enabled));
    }

    @Test
    void nearbySiteEntryRemainsPrimaryWhenStaleContactWasSelected() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation site = firstRandomSite(ctx);
        assertNotNull(site);
        site.discovered = true;
        st.selectedGalaxyLocationId = "";
        st.selectedFreeGalaxyTargetX = Double.NaN;
        st.selectedFreeGalaxyTargetY = Double.NaN;
        st.playerGalaxyX = site.x;
        st.playerGalaxyY = site.y;

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Stale Regression Contact",
                "old ping",
                "Stale",
                site.x + 20.0,
                site.y,
                true,
                false);

        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        assertNotNull(selected);
        assertEquals(site.id, selected.id);
        assertTrue(CampaignSystem.canEnterSelectedLocalEncounter(ctx));
        CampaignSystem.CampaignAction primary = CampaignSystem.campaignPrimaryAction(ctx);
        assertNotNull(primary);
        assertEquals("ENTER_SITE", primary.id);
    }

    @Test
    void selectedFleetContactCanBeUsedAsNavigationCourse() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Fleet Course", "direct contact", "test fleet course",
                st.playerGalaxyX + 500.0, st.playerGalaxyY + 40.0);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 48.0);
        setDouble(hostile, "contactConfidence", 0.95);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Fleet Course",
                "Tracked contact",
                "Tracked",
                st.playerGalaxyX + 500.0,
                st.playerGalaxyY + 40.0,
                true,
                true);

        assertTrue(CampaignSystem.campaignVisibleActions(ctx).stream()
                .anyMatch(action -> "ENGAGE_COURSE".equals(action.id) && action.enabled));
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(st.galaxyTravel.traveling);
        assertTrue(st.galaxyTravel.freeTravel);
        assertEquals(st.playerGalaxyX + 500.0, st.galaxyTravel.targetX, 1e-9);
    }

    @Test
    void selectedLiveEnemyFleetCanBeDirectlyEngagedButStaleGhostCannot() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2600.0;
        st.playerGalaxyY = 2600.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Direct Engage", "direct contact", "test direct fleet engagement",
                st.playerGalaxyX + 80.0, st.playerGalaxyY);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 55.0);
        setDouble(hostile, "contactConfidence", 0.9);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(hostile, "id"), "Direct Engage Patrol");

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Live Hostile Contact",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 80.0,
                st.playerGalaxyY,
                true,
                true);

        assertTrue(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(getInt(hostile, "id"), ctx.ui.strategicEncounterPrompt.campaignForceId);

        ctx.ui.clearStrategicEncounterPrompt();
        CampaignSystem.clearSelectedCampaignContact(ctx);
        Object stale = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Ghost Contact", "stale contact", "test stale contact rejection",
                4800.0, 4800.0);
        setBoolean(stale, "simulationActive", true);
        setDouble(stale, "strength", 55.0);
        setDouble(stale, "contactConfidence", 0.1);
        setDouble(stale, "lastKnownAgeSec", 120.0);
        setBoolean(stale, "visibleToPlayer", false);
        setObject(stale, "contactState", CampaignSystem.CampaignForceContactState.STALE);

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Ghost Contact",
                "STALE CONTACT",
                "Last Known",
                4800.0,
                4800.0,
                true,
                true);

        assertFalse(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
    }

    @Test
    void engageContactActionForLiveEnemyFleetOpensBriefingPromptBeforeBattle() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2600.0;
        st.playerGalaxyY = 2600.0;
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Action Briefing", "direct contact", "test action opens briefing",
                st.playerGalaxyX + 80.0, st.playerGalaxyY);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 55.0);
        setDouble(hostile, "contactConfidence", 0.9);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(hostile, "id"), "Action Briefing Patrol");

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Action Briefing",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 80.0,
                st.playerGalaxyY,
                true,
                true);

        CampaignSystem.CampaignAction engage = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> "ENGAGE_CONTACT".equals(action.id))
                .findFirst()
                .orElse(null);
        assertNotNull(engage);
        assertTrue(engage.enabled, "live hostile fleets should expose the pre-battle briefing action");

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "ENGAGE_CONTACT"));

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_FORCE, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(getInt(hostile, "id"), ctx.ui.strategicEncounterPrompt.campaignForceId);
        assertFalse(ctx.ui.campaignEncounterLoading.active,
                "engage contact should stop at the briefing menu instead of starting tactical loading");
        assertTrue(st.strategicOvermapMode);
    }

    @Test
    void engagingShiplessForceRunsImmediateGhostSweepBeforePrompt() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2600.0;
        st.playerGalaxyY = 2600.0;
        st.strategicOvermapMode = true;
        ctx.state = GameState.MAP;

        Object ghost = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Immediate Ghost Sweep", "direct contact", "test immediate ghost cleanup",
                st.playerGalaxyX + 70.0, st.playerGalaxyY);
        setBoolean(ghost, "simulationActive", true);
        setDouble(ghost, "strength", 62.0);
        setDouble(ghost, "readiness", 90.0);
        setDouble(ghost, "hullIntegrity", 90.0);
        setDouble(ghost, "contactConfidence", 0.95);
        setDouble(ghost, "lastKnownAgeSec", 0.0);
        setBoolean(ghost, "visibleToPlayer", true);
        setBoolean(ghost, "hadTacticalMembers", false);
        setInt(ghost, "linkedSearchGroupId", 0);
        setObject(ghost, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        ((java.util.Set<?>) getObject(ghost, "shipIds")).clear();
        st.campaignShipPool.clear();

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Immediate Ghost Sweep",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 70.0,
                st.playerGalaxyY,
                true,
                true);

        assertFalse(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertNull(forceNamed(st, "Regression Red Immediate Ghost Sweep"),
                "engage should sweep and delete shipless ghost fleets before opening the auto-battle prompt");
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
    }

    @Test
    void shiplessNewFleetIsRemovedDuringSpawnReconciliation() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        for (CampaignSystem.CampaignLocation location : allKnownCampaignLocations(ctx)) {
            if (location != null && location.ownerFaction == Faction.ENEMY) {
                location.ownerFaction = Faction.ALLY;
            }
        }

        Object shipless = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Shipless Spawn Guard", "spawn guard", "should not survive without hulls",
                2600.0, 2600.0);
        st.campaignShipPool.clear();
        setBoolean(shipless, "simulationActive", true);
        setDouble(shipless, "strength", 64.0);
        setDouble(shipless, "readiness", 90.0);
        setDouble(shipless, "hullIntegrity", 95.0);
        setBoolean(shipless, "visibleToPlayer", true);
        setObject(shipless, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        invokeRemoveShiplessPhysicalCampaignForces(ctx, st);

        assertNull(forceNamed(st, "Regression Red Shipless Spawn Guard"),
                "new physical fleets should not remain on the campaign map when no ship can be assigned");
    }

    @Test
    void openingNamedRedFleetsAreNotSeededWhenPlayerMoves() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.syncCampaignForceSimulationSeedsForTest(ctx);
        assertNull(forceNamed(st, "Red Scout Pair"));
        assertNull(forceNamed(st, "Red Patrol Group"));
        assertNull(forceNamed(st, "Red Interceptor Squadron"));

        st.galaxyTravel.traveling = true;
        st.playerGalaxyX += 420.0;
        st.playerGalaxyY -= 180.0;
        CampaignSystem.syncCampaignForceSimulationSeedsForTest(ctx);

        assertNull(forceNamed(st, "Red Scout Pair"));
        assertNull(forceNamed(st, "Red Patrol Group"),
                "movement-triggered seed sync should not create opening Red overmap fleets");
        assertNull(forceNamed(st, "Red Interceptor Squadron"));
    }

    @Test
    void destroyedConcreteFleetIsRemovedBeforeItCanBecomeOvermapGhost() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2600.0;
        st.playerGalaxyY = 2600.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Defeated Concrete Fleet", "direct contact", "test defeated concrete force",
                st.playerGalaxyX + 80.0, st.playerGalaxyY);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 55.0);
        setDouble(hostile, "contactConfidence", 0.95);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        Ship redShip = new FleetShip(ShipRole.PATROL, Faction.ENEMY, st.playerGalaxyX + 80.0, st.playerGalaxyY);
        ctx.ships.add(redShip);
        invokeRegisterShipWithCampaignForce(st, redShip, hostile);
        st.galaxyEncounterActive = true;
        st.activeGalaxyEncounterForceIds.add(getInt(hostile, "id"));
        redShip.alive = false;
        redShip.hp = 0;

        invokeCampaignForceSimulation(ctx, st, 0.2);

        assertTrue(getBoolean(hostile, "destroyed"), "force should be removed once its last real tactical member is gone");
        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Defeated Concrete Fleet",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 80.0,
                st.playerGalaxyY,
                true,
                true);

        assertFalse(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
    }

    @Test
    void missionCompletionRemovesDefeatedLinkedFleetBeforeReturningToOvermap() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        int groupId = getInt(group, "id");

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Linked Mission Fleet", "mission contact", "test linked mission cleanup",
                2600.0, 2600.0);
        int forceId = getInt(hostile, "id");
        setInt(hostile, "linkedSearchGroupId", groupId);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 58.0);
        setDouble(hostile, "readiness", 90.0);
        setDouble(hostile, "hullIntegrity", 100.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        Ship redShip = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 2600.0, 2600.0);
        ctx.ships.add(redShip);
        invokeRegisterShipWithCampaignForce(st, redShip, hostile);
        invokeTrackActiveTacticalForce(st, hostile);
        redShip.hp = 0;
        redShip.alive = false;
        redShip.dying = true;

        st.strategicOvermapMode = false;
        st.galaxyEncounterActive = false;
        st.objectiveSecured = true;

        assertTrue(CampaignSystem.completeMissionExtraction(ctx));

        assertNull(forceById(st, forceId),
                "mission completion should delete a linked force after its last tactical member is destroyed");
        assertFalse(containsSearchGroup(st, groupId),
                "the linked search group should be retired with the defeated mission fleet");
    }

    @Test
    void takeCommandOfDirectFleetContactLaunchesTacticalBattleInsteadOfAutoResolve() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2700.0;
        st.playerGalaxyY = 2700.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Regression Red Manual Fight", "direct contact", "test manual fleet fight",
                st.playerGalaxyX + 70.0, st.playerGalaxyY);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 62.0);
        setDouble(hostile, "contactConfidence", 0.95);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.PATROL, getInt(hostile, "id"), "Manual Fight Patrol");

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Manual Fight",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 70.0,
                st.playerGalaxyY,
                true,
                true);

        assertTrue(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertTrue(ctx.ui.campaignEncounterLoading.active);
        assertTrue(st.strategicOvermapMode);
        assertEquals(GameState.MAP, ctx.state);

        CampaignSystem.update(ctx, 1.2);

        assertFalse(st.strategicOvermapMode);
        assertTrue(st.galaxyEncounterActive);
        assertFalse(getBoolean(hostile, "destroyed"));
    }

    @Test
    void directFleetContactBriefingListsAssetsAndHonorsFarInsertionChoice() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2700.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Briefing Fight", "direct contact", "test briefing fleet fight",
                st.playerGalaxyX + 900.0, st.playerGalaxyY + 40.0);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 94.0);
        setDouble(hostile, "contactConfidence", 0.95);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.INTERDICTION_TITAN, getInt(hostile, "id"), "Briefing Titan");
        addPoolRecord(st, Faction.ENEMY, ShipRole.FRIGATE, getInt(hostile, "id"), "Briefing Escort");

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Briefing Fight",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 900.0,
                st.playerGalaxyY + 40.0,
                true,
                true);

        assertTrue(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.friendlyAssetLines.isEmpty());
        assertTrue(ctx.ui.strategicEncounterPrompt.enemyAssetLines.stream()
                .anyMatch(line -> line.contains("Regression Red Briefing Fight")));
        assertFalse(ctx.ui.strategicEncounterPrompt.friendlyAssets.isEmpty(),
                "briefing should expose individual friendly hull rows, not only text summaries");
        assertTrue(ctx.ui.strategicEncounterPrompt.enemyAssets.stream()
                        .anyMatch(asset -> asset.role == ShipRole.INTERDICTION_TITAN
                                && asset.name.contains("Briefing Titan")
                                && asset.formationRole == CampaignSystem.FleetFormationRole.FLAGSHIP),
                "briefing should expose each identified enemy hull by role and name");
        assertTrue(CampaignSystem.setPendingEncounterInsertionRange(ctx, "FAR"));
        assertEquals("FAR", ctx.ui.strategicEncounterPrompt.insertionRange);

        KeyEvent cPress = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, KeyEvent.VK_C, 'C');
        assertFalse(GameplayActions.tryHandleStrategicEncounterHotkey(ctx, cPress),
                "C should not bypass the pre-battle briefing and insertion range choice");
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertFalse(ctx.ui.campaignEncounterLoading.active);
        assertEquals("FAR", ctx.ui.strategicEncounterPrompt.insertionRange);

        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertEquals("FAR", st.pendingCampaignEncounterInsertionRange);
        assertTrue(ctx.ui.campaignEncounterLoading.active);

        CampaignSystem.update(ctx, 1.2);

        assertFalse(ctx.ui.campaignEncounterLoading.active);
        assertFalse(st.strategicOvermapMode);
        assertTrue(st.galaxyEncounterActive);
    }

    @Test
    void farInsertionKeepsHostileSensorBubbleJoinersAtLongStandoff() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        double sensorRange = CampaignSystem.playerCampaignSensorRange(ctx);
        double hardJoinRange = sensorRange * 0.5;

        Object primary = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Long Range Primary", "Red base lane", "primary long insertion",
                st.playerGalaxyX + 600.0, st.playerGalaxyY);
        Object nearby = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT, Faction.ENEMY,
                "Regression Red Long Range Joiner", "Red base lane", "join long insertion",
                st.playerGalaxyX + Math.min(900.0, hardJoinRange - 160.0), st.playerGalaxyY + 80.0);
        for (Object force : List.of(primary, nearby)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "strength", 88.0);
            setDouble(force, "readiness", 88.0);
            setDouble(force, "contactConfidence", 0.95);
            setDouble(force, "lastKnownAgeSec", 0.0);
            setBoolean(force, "visibleToPlayer", true);
            setObject(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        }
        addPoolRecord(st, Faction.ENEMY, ShipRole.BATTLECRUISER, getInt(primary, "id"), "Long Primary Battlecruiser");
        addPoolRecord(st, Faction.ENEMY, ShipRole.MISSILE_BOAT, getInt(nearby, "id"), "Long Joiner Missile Boat");

        assertTrue(launchCampaignForceEncounter(ctx, st, primary, "FAR"));
        assertTrue(hasTacticalShipForCampaignForce(st, getInt(nearby, "id")),
                "nearby hostile force should still join the same sensor-bubble battle");
        double nearestEnemy = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.ENEMY && ship.alive && !ship.dying)
                .mapToDouble(ship -> Math.hypot(ship.x - ctx.player.x, ship.y - ctx.player.y))
                .min()
                .orElse(0.0);
        assertTrue(nearestEnemy >= 7600.0,
                "far insertion should not allow hostile joiners to spawn at the old 1-2km standoff; nearest=" + nearestEnemy);
    }

    @Test
    void selectedDeploymentPointCreatesSeparatedFleetBattleStandoff() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2700.0;

        Object hostile = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Regression Red Deployment Map Fight", "direct contact", "test deployment map fleet fight",
                st.playerGalaxyX + 820.0, st.playerGalaxyY + 40.0);
        setBoolean(hostile, "simulationActive", true);
        setDouble(hostile, "strength", 96.0);
        setDouble(hostile, "contactConfidence", 0.95);
        setDouble(hostile, "lastKnownAgeSec", 0.0);
        setBoolean(hostile, "visibleToPlayer", true);
        setObject(hostile, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        addPoolRecord(st, Faction.ENEMY, ShipRole.CARRIER, getInt(hostile, "id"), "Deployment Carrier");
        addPoolRecord(st, Faction.ENEMY, ShipRole.FRIGATE, getInt(hostile, "id"), "Deployment Escort");

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Regression Red Deployment Map Fight",
                "EXACT LIVE CONTACT",
                "Tracked",
                st.playerGalaxyX + 820.0,
                st.playerGalaxyY + 40.0,
                true,
                true);

        assertTrue(CampaignSystem.engageSelectedCampaignContact(ctx));
        assertTrue(CampaignSystem.setPendingEncounterDeploymentPoint(ctx, 0.38, 0.16));
        assertEquals("MODERATE", ctx.ui.strategicEncounterPrompt.insertionRange);
        assertEquals(0.38, ctx.ui.strategicEncounterPrompt.deploymentX, 0.001);
        assertEquals(0.16, ctx.ui.strategicEncounterPrompt.deploymentY, 0.001);

        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertEquals(0.38, st.pendingCampaignEncounterDeploymentX, 0.001);
        assertEquals(0.16, st.pendingCampaignEncounterDeploymentY, 0.001);

        CampaignSystem.update(ctx, 1.2);

        assertFalse(ctx.ui.campaignEncounterLoading.active);
        assertFalse(st.strategicOvermapMode);
        assertTrue(st.galaxyEncounterActive);
        List<Ship> enemies = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.ENEMY && ship.alive && !ship.dying)
                .toList();
        assertFalse(enemies.isEmpty());
        double nearestEnemy = enemies.stream()
                .mapToDouble(ship -> Math.hypot(ship.x - ctx.player.x, ship.y - ctx.player.y))
                .min()
                .orElse(0.0);
        double enemyCenterX = enemies.stream().mapToDouble(ship -> ship.x).average().orElse(ctx.player.x);
        assertTrue(ctx.player.x < enemyCenterX, "player deployment should remain on the friendly side of hostile contacts");
        assertTrue(nearestEnemy >= 1100.0, "enemy fleets should start outside immediate brawl range");
        assertNoInitialShipOverlaps(ctx);
    }

    @Test
    void campaignSeedsActiveLivingWarFleetsForRedGreenAndYellow() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeCampaignForceSeedSync(ctx, ctx.campaign);

        List<CampaignSystem.CampaignForceSummary> summaries = CampaignSystem.campaignForceSummaries(ctx);
        long red = summaries.stream().filter(force -> force.faction == Faction.ENEMY).count();
        long green = summaries.stream().filter(force -> force.faction == Faction.TEAM_C || force.faction == Faction.ALLY).count();
        long yellow = summaries.stream().filter(force -> force.faction == Faction.BRIGHT_YELLOW || force.faction == Faction.TEAM_D).count();

        assertTrue(red >= 6, "Red should maintain multiple active theater fleets");
        assertTrue(green >= 6, "Green should maintain visible patrol, escort, and counter-task fleets");
        assertTrue(yellow >= 6, "Yellow should maintain trade, security, and miner traffic");
        assertTrue(summaries.stream().anyMatch(force -> force.name.contains("Counter Task Force")
                        && (force.faction == Faction.TEAM_C || force.faction == Faction.ALLY)
                        && force.destinationLocationId != null
                        && !force.destinationLocationId.isBlank()),
                "Green should project active counter-task forces with named destinations");
        assertTrue(summaries.stream().anyMatch(force -> force.name.contains("Civilian Security")
                        && (force.faction == Faction.BRIGHT_YELLOW || force.faction == Faction.TEAM_D)),
                "Yellow should contribute visible civilian security traffic");
    }

    @Test
    void pointOfInterestDefenseContactFoldsIntoMissionPromptInsteadOfOpenSpaceClash() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation mission = firstCombatMission(ctx);
        assertNotNull(mission);
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        st.playerGalaxyX = mission.x;
        st.playerGalaxyY = mission.y;
        st.selectedGalaxyLocationId = mission.id;
        setDouble(group, "x", mission.x);
        setDouble(group, "y", mission.y);
        setObject(group, "anchorLocationId", mission.id);

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(mission.id, ctx.ui.strategicEncounterPrompt.campaignLocationId);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.toLowerCase().contains("site assault"));
    }

    @Test
    void openSpaceFleetClashExposesThreeOwnedTacticalZones() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        assertTrue(launchGalaxySearchGroupEncounter(ctx, st, group));

        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .anyMatch(marker -> marker.label.equals("Allied Spawn Zone")
                        && marker.faction == Faction.ALLY
                        && marker.subtitle.toLowerCase().contains("left zone")));
        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .anyMatch(marker -> marker.label.equals("Neutral Transit Zone")
                        && marker.faction == null
                        && marker.subtitle.toLowerCase().contains("middle zone")));
        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .anyMatch(marker -> marker.label.equals("Hostile Contact Zone")
                        && marker.faction == Faction.ENEMY
                        && marker.subtitle.toLowerCase().contains("right zone")));
        CampaignSystem.CampaignObjectiveMarker allied = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .filter(marker -> marker.label.equals("Allied Spawn Zone"))
                .findFirst()
                .orElse(null);
        CampaignSystem.CampaignObjectiveMarker neutral = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .filter(marker -> marker.label.equals("Neutral Transit Zone"))
                .findFirst()
                .orElse(null);
        CampaignSystem.CampaignObjectiveMarker hostile = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .filter(marker -> marker.label.equals("Hostile Contact Zone"))
                .findFirst()
                .orElse(null);
        assertNotNull(allied);
        assertNotNull(neutral);
        assertNotNull(hostile);
        assertTrue(neutral.x - allied.x < 1200.0, "open-space allied and neutral lanes should not span the whole tactical map");
        assertTrue(hostile.x - neutral.x < 1200.0, "open-space neutral and hostile lanes should stay in the same battle pocket");
        assertTrue(st.objectivePhaseLabel.toLowerCase().contains("left allied"));
        assertTrue(st.threatStateLabel.toLowerCase().contains("no authored mission blockers"));
    }

    @Test
    void defeatedSearchGroupFleetIsRemovedFromOvermapAfterManualBattle() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        int groupId = getInt(group, "id");

        assertTrue(launchGalaxySearchGroupEncounter(ctx, st, group));
        Object linkedForce = linkedForceForSearchGroup(st, groupId);
        assertNotNull(linkedForce);
        int forceId = getInt(linkedForce, "id");

        int defeatedShips = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || st.shipCampaignForceIds.getOrDefault(ship.id, 0) != forceId) continue;
            ship.hp = 0;
            ship.alive = false;
            ship.dying = true;
            defeatedShips++;
        }
        assertTrue(defeatedShips > 0, "search group encounter should spawn ships owned by the linked campaign force");

        invokeFinishGalaxyEncounterAndReturn(ctx, st);

        assertFalse(st.campaignForces.contains(linkedForce),
                "defeated linked forces should be removed before the overworld is shown again");
        assertFalse(containsSearchGroup(st, groupId),
                "the backing search group should be retired so its red map marker cannot be rebuilt");
        assertNull(linkedForceForSearchGroup(st, groupId),
                "no remaining campaign force should still point at the defeated search group");

        CampaignSystem.syncCampaignForceSimulationSeedsForTest(ctx);
        assertNull(linkedForceForSearchGroup(st, groupId),
                "search-group force sync should not recreate a defeated patrol after return to overmap");
    }

    @Test
    void enemyActivityArrivalUsesTheSameStrategicEncounterPipeline() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation threat = findAreaOfInterest(ctx, "aoi-threat-1");

        assertNotNull(threat);
        st.selectedGalaxyLocationId = threat.id;
        st.playerGalaxyX = threat.x;
        st.playerGalaxyY = threat.y;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);

        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
        assertTrue(st.strategicOvermapMode);
        assertFalse(st.galaxyEncounterActive);
        assertEquals(0, st.activeGalaxyEncounterSearchGroupId);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.sectorElapsed = 240.0;
        return ctx;
    }

    private static void invokeDetectionUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxyDetectionAndInterception",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeForceEncounterUpdate(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceEncounterTrigger",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeCampaignForceSeedSync(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "syncCampaignForceSimulationSeeds",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeCampaignForceSimulation(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeRemoveDestroyedCampaignForces(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "removeDestroyedCampaignForces",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeRemoveShiplessPhysicalCampaignForces(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "removeShiplessPhysicalCampaignForces",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, "test_shipless_spawn_guard");
    }

    private static void invokeNpcBattleResolution(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "resolveNpcFactionFleetBattles",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static Object invokeEnsureCampaignForce(CampaignSystem.CampaignState st,
                                                    CampaignSystem.CampaignForceKind kind,
                                                    Faction faction,
                                                    String name,
                                                    String origin,
                                                    String purpose,
                                                    double x,
                                                    double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForce",
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignForceKind.class,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class
        );
        method.setAccessible(true);
        return method.invoke(null, st, kind, faction, name, origin, purpose, x, y);
    }

    private static void invokeRegisterShipWithCampaignForce(CampaignSystem.CampaignState st, Ship ship, Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "registerShipWithCampaignForce",
                CampaignSystem.CampaignState.class,
                Ship.class,
                force.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, st, ship, force);
    }

    private static void addPoolRecord(CampaignSystem.CampaignState st,
                                      Faction faction,
                                      ShipRole role,
                                      int forceId,
                                      String name) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "addCampaignShipPoolRecord",
                CampaignSystem.CampaignState.class,
                Faction.class,
                ShipRole.class,
                CampaignSystem.CampaignShipPoolStatus.class,
                String.class,
                int.class,
                double.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, faction, role, CampaignSystem.CampaignShipPoolStatus.ACTIVE,
                "", forceId, 100.0, name);
    }

    private static boolean launchGalaxySearchGroupEncounter(GameContext ctx, CampaignSystem.CampaignState st, Object group) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchGalaxySearchGroupEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                group.getClass()
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, group);
    }

    private static boolean launchCampaignForceEncounter(GameContext ctx, CampaignSystem.CampaignState st, Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchCampaignForceEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean launchCampaignForceEncounter(GameContext ctx,
                                                        CampaignSystem.CampaignState st,
                                                        Object force,
                                                        String insertionRange) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchCampaignForceEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, force, insertionRange);
    }

    private static boolean hasTacticalShipForCampaignForce(CampaignSystem.CampaignState st, int forceId) {
        for (Integer mappedForceId : st.shipCampaignForceIds.values()) {
            if (mappedForceId != null && mappedForceId == forceId) return true;
        }
        return false;
    }

    private static void assertNoInitialShipOverlaps(GameContext ctx) {
        for (int i = 0; i < ctx.ships.size(); i++) {
            Ship a = ctx.ships.get(i);
            if (a == null || !a.alive || a.dying) continue;
            for (int j = i + 1; j < ctx.ships.size(); j++) {
                Ship b = ctx.ships.get(j);
                if (b == null || !b.alive || b.dying) continue;
                double min = Math.max(60.0, a.radius + b.radius + 20.0);
                double dist = Math.hypot(a.x - b.x, a.y - b.y);
                assertTrue(dist >= min,
                        "ships should not begin overlapped: " + a.name + " / " + b.name + " distance=" + dist + " min=" + min);
            }
        }
    }

    private static void invokeTrackActiveTacticalForce(CampaignSystem.CampaignState st, Object force) throws Exception {
        Method method = CampaignForceRosterSystem.class.getDeclaredMethod(
                "trackActiveTacticalForce",
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, st, force);
    }

    private static void invokeFinishGalaxyEncounterAndReturn(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "finishGalaxyEncounterAndReturn",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                int.class,
                int.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, 0, 0, "", "", "");
    }

    private static Object linkedForceForSearchGroup(CampaignSystem.CampaignState st, int groupId) throws Exception {
        for (Object force : st.campaignForces) {
            if (force != null && getInt(force, "linkedSearchGroupId") == groupId) return force;
        }
        return null;
    }

    private static Object forceById(CampaignSystem.CampaignState st, int forceId) throws Exception {
        for (Object force : st.campaignForces) {
            if (force != null && getInt(force, "id") == forceId) return force;
        }
        return null;
    }

    private static Object forceNamed(CampaignSystem.CampaignState st, String name) throws Exception {
        for (Object force : st.campaignForces) {
            if (force != null && name.equals(getObject(force, "name"))) return force;
        }
        return null;
    }

    private static boolean containsSearchGroup(CampaignSystem.CampaignState st, int groupId) throws Exception {
        for (Object group : st.galaxySearchGroups) {
            if (group != null && getInt(group, "id") == groupId) return true;
        }
        return false;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        for (Object group : groups) {
            if (group != null && getBoolean(group, "hostile")) return group;
        }
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static Object firstStrategicTaskForce(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        invokeInitializeStrategicTaskForces(ctx, st);
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicTaskForces");
        field.setAccessible(true);
        List<?> taskForces = (List<?>) field.get(st);
        return taskForces.isEmpty() ? null : taskForces.get(0);
    }

    private static void invokeInitializeStrategicTaskForces(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "initializeStrategicTaskForces",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static Object strategicTaskForceByLabel(CampaignSystem.CampaignState st, String label) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicTaskForces");
        field.setAccessible(true);
        List<?> taskForces = (List<?>) field.get(st);
        for (Object taskForce : taskForces) {
            if (taskForce != null && label.equals(getObject(taskForce, "label"))) return taskForce;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation findAreaOfInterest(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation findMainLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstCombatMission(GameContext ctx) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.primaryMission && "poi-08".equals(location.id)) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstMainLocationOwnedBy(GameContext ctx, Faction faction) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && !location.destroyed && location.ownerFaction == faction) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstMainLocationNotOwnedBy(GameContext ctx, Faction faction) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && !location.destroyed && location.ownerFaction != null && location.ownerFaction != faction) {
                return location;
            }
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation anotherMainLocation(GameContext ctx,
                                                                       CampaignSystem.CampaignLocation excluded) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && !location.destroyed && location != excluded) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstLocationOwnedBy(GameContext ctx, Faction faction) {
        for (CampaignSystem.CampaignLocation location : allKnownCampaignLocations(ctx)) {
            if (location != null && !location.destroyed && location.ownerFaction == faction) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstRandomSite(GameContext ctx) {
        CampaignSystem.CampaignLocation fallback = null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location == null || location.destroyed) continue;
            if (location.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE
                    || location.type == CampaignSystem.CampaignLocationType.SALVAGE_FIELD
                    || location.facilityType == CampaignSystem.CampaignFacilityType.DERELICT_BATTLEFIELD
                    || location.facilityType == CampaignSystem.CampaignFacilityType.MINING_OPERATION) {
                if (fallback == null) fallback = location;
                CampaignSystem.CampaignLocation offsetHit =
                        CampaignSystem.nearestCampaignLocation(ctx, location.x + 40.0, location.y, 120.0);
                if (offsetHit == location) return location;
            }
        }
        return fallback;
    }

    private static List<CampaignSystem.CampaignLocation> allKnownCampaignLocations(GameContext ctx) {
        ArrayList<CampaignSystem.CampaignLocation> out = new ArrayList<>();
        out.addAll(CampaignSystem.mainCampaignLocations(ctx));
        out.addAll(CampaignSystem.campaignAreasOfInterest(ctx));
        return out;
    }

    private static void clickGalaxyMap(GameContext ctx, double worldX, double worldY, int clickCount) {
        int viewportW = 1000;
        int viewportH = 800;
        ctx.ui.mapOpen = true;
        ctx.ui.strategicMapZoom = 2.2;
        ctx.ui.strategicMapFocusX = worldX;
        ctx.ui.strategicMapFocusY = worldY;
        Rectangle rect = Renderer.getStrategicMapInnerRect(viewportW, viewportH, true);
        double nx = (worldX - UISystem.strategicMapWorldMinX(ctx)) / UISystem.strategicMapViewWidth(ctx);
        double ny = (worldY - UISystem.strategicMapWorldMinY(ctx)) / UISystem.strategicMapViewHeight(ctx);
        int sx = rect.x + (int) Math.round(nx * rect.width);
        int sy = rect.y + (int) Math.round(ny * rect.height);
        MouseEvent event = new MouseEvent(new Canvas(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, sx, sy, clickCount, false, MouseEvent.BUTTON1);
        UISystem.handleMapClick(ctx, event, viewportW, viewportH);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setEnumByName(Object target, String fieldName, String enumName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Enum<?> value = Enum.valueOf((Class<? extends Enum>) field.getType(), enumName);
        field.set(target, value);
    }
}
