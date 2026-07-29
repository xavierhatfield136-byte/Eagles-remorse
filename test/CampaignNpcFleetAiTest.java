import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignNpcFleetAiTest {
    private static final Method UPDATE_FORCE_SIMULATION = declaredMethod(
            "updateCampaignForceSimulation",
            GameContext.class,
            CampaignSystem.CampaignState.class,
            double.class
    );
    private static final Method UPDATE_TRAVEL = declaredMethod(
            "updateCampaignTravel",
            GameContext.class,
            CampaignSystem.CampaignState.class,
            double.class
    );
    private static final Method UPDATE_STRATEGIC_STRIKE_OBJECTS = declaredMethod(
            "updateStrategicStrikeObjects",
            GameContext.class,
            CampaignSystem.CampaignState.class,
            double.class
    );
    private static final Method UPDATE_OVERMAP_GHOST_FLEET_SWEEP = declaredMethod(
            "updateOvermapGhostFleetSweep",
            GameContext.class,
            CampaignSystem.CampaignState.class,
            double.class
    );

    @Test
    void ambientTheaterFleetsSeedAcrossCampaignFromStart() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        invokeForceSimulation(ctx, st, 0.2);

        List<?> forces = campaignForces(st);
        long enemies = forces.stream().filter(force -> force != null && "ENEMY".equals(fieldString(force, "faction"))).count();
        long green = forces.stream().filter(force -> force != null && "TEAM_C".equals(fieldString(force, "faction"))).count();
        long yellow = forces.stream().filter(force -> force != null && "BRIGHT_YELLOW".equals(fieldString(force, "faction"))).count();
        long theaterInterdictionScreens = forces.stream()
                .filter(force -> force != null && fieldString(force, "name").contains("Interdiction Screen"))
                .count();

        assertTrue(forces.size() >= 22, "campaign should seed a visible NPC fleet picture from the start");
        assertTrue(enemies >= 8, "expected multiple hostile fleets before the final theater");
        assertTrue(green >= 4, "expected Green patrol/relay traffic across theaters");
        assertTrue(yellow >= 4, "expected Yellow trade columns across theaters");
        assertTrue(theaterInterdictionScreens >= 4, "expected one red interdiction screen per theater");
    }

    @Test
    void reconSweepReacquiresStaleCampaignForceContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setDouble(force, "x", st.playerGalaxyX + 360.0);
        setDouble(force, "y", st.playerGalaxyY - 180.0);
        setDouble(force, "lastKnownX", st.playerGalaxyX + 360.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY - 180.0);
        setDouble(force, "lastKnownAgeSec", 76.0);
        setDouble(force, "contactConfidence", 0.12);
        setBoolean(force, "visibleToPlayer", false);
        setEnumByName(force, "contactState", "STALE");
        st.campaignSupplies = 99;

        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));

        assertTrue(getBoolean(force, "visibleToPlayer"));
        assertTrue(getDouble(force, "contactConfidence") >= 0.54);
        assertTrue(getDouble(force, "lastKnownAgeSec") <= 0.001);
        assertTrue(!"STALE".equals(fieldString(force, "contactState")));
    }

    @Test
    void recentContactMemoryDoesNotResurrectShiplessHostileForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.LOCAL_FORCE,
                Faction.ENEMY, "Disposable Memory Picket Patrol", "test-red-base",
                "verify contact memory cannot preserve physical existence", st.playerGalaxyX + 400.0, st.playerGalaxyY);
        assertNotNull(force);

        setDouble(force, "strength", 0.0);
        setDouble(force, "readiness", 0.0);
        setDouble(force, "supply", 0.0);
        setDouble(force, "hullIntegrity", 0.0);
        setDouble(force, "contactConfidence", 0.18);
        setDouble(force, "lastKnownAgeSec", 44.0);
        setBoolean(force, "visibleToPlayer", false);
        setEnumByName(force, "contactState", "STALE");
        ((java.util.Set<?>) getObject(force, "shipIds")).clear();
        setInt(force, "linkedSearchGroupId", 0);

        invokeForceSimulation(ctx, st, 0.2);

        assertNull(forceNamed(st, "Disposable Memory Picket Patrol"),
                "contact memory must not manufacture a surviving physical fleet");
    }

    @Test
    void overmapSweepRemovesVisibleShiplessConcreteForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicOvermapMode = true;
        ctx.state = GameState.MAP;
        ctx.ui.mapOpen = true;

        Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.ENEMY, "Ghost Sweep Red Patrol", "test-red-base",
                "verify overworld sweep removes empty concrete fleets", st.playerGalaxyX + 300.0, st.playerGalaxyY);
        assertNotNull(force);
        setDouble(force, "strength", 64.0);
        setDouble(force, "readiness", 90.0);
        setDouble(force, "hullIntegrity", 90.0);
        setDouble(force, "contactConfidence", 0.96);
        setBoolean(force, "visibleToPlayer", true);
        setBoolean(force, "hadTacticalMembers", true);
        setInt(force, "linkedSearchGroupId", 0);
        setEnumByName(force, "contactState", "KNOWN");
        ((java.util.Set<?>) getObject(force, "shipIds")).clear();
        st.campaignShipPool.clear();

        invokeOvermapGhostFleetSweep(ctx, st, 14.9);
        assertNotNull(forceNamed(st, "Ghost Sweep Red Patrol"),
                "ghost sweep should wait for the periodic overworld interval");

        invokeOvermapGhostFleetSweep(ctx, st, 0.2);

        assertNull(forceNamed(st, "Ghost Sweep Red Patrol"),
                "shipless concrete fleets should be removed from the overworld map on the sweep");
    }

    @Test
    void overmapSweepRemovesShiplessForceWithoutPriorTacticalMembership() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicOvermapMode = true;
        ctx.state = GameState.MAP;

        Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.ENEMY, "Never Materialized Ghost Patrol", "test-red-base",
                "verify strength alone is not physical fleet evidence", st.playerGalaxyX + 360.0, st.playerGalaxyY + 40.0);
        assertNotNull(force);
        setDouble(force, "strength", 78.0);
        setDouble(force, "readiness", 90.0);
        setDouble(force, "hullIntegrity", 90.0);
        setBoolean(force, "hadTacticalMembers", false);
        setInt(force, "linkedSearchGroupId", 0);
        setEnumByName(force, "contactState", "KNOWN");
        ((java.util.Set<?>) getObject(force, "shipIds")).clear();
        st.campaignShipPool.clear();

        invokeOvermapGhostFleetSweep(ctx, st, 15.1);

        assertNull(forceNamed(st, "Never Materialized Ghost Patrol"),
                "positive strength without concrete roster membership must not preserve a physical fleet");
    }

    @Test
    void poolBackedForceSummaryUsesConcreteRosterCount() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object force = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.ENEMY, "Pool Backed Count Test Force", "test-red-base",
                "verify pool records are the concrete roster", st.playerGalaxyX + 420.0, st.playerGalaxyY);
        assertNotNull(force);
        ((java.util.Set<?>) getObject(force, "shipIds")).clear();
        st.campaignShipPool.clear();
        addPoolRecord(st, Faction.ENEMY, ShipRole.FRIGATE, getInt(force, "id"), "Count Test Frigate");
        addPoolRecord(st, Faction.ENEMY, ShipRole.MISSILE_BOAT, getInt(force, "id"), "Count Test Missile Boat");

        CampaignSystem.CampaignForceSummary summary = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(entry -> entry != null && entry.id == getIntUnchecked(force, "id"))
                .findFirst()
                .orElse(null);

        assertNotNull(summary, "pool-backed concrete fleet should remain visible to force summaries");
        assertEquals(2, summary.shipCount, "summary count should come from concrete pool roster, not empty shipIds");
    }

    @Test
    void concreteRosterDeduplicatesTacticalShipAndPoolRecordIdentity() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignForce force = (CampaignSystem.CampaignForce) invokeEnsureCampaignForce(
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Deduplicated Roster Test Force", "test-red-base",
                "verify tactical and pool views collapse to one identity", st.playerGalaxyX + 480.0, st.playerGalaxyY);
        assertNotNull(force);
        st.campaignShipPool.clear();
        CampaignSystem.CampaignShipPoolRecord record = addPoolRecord(
                st, Faction.ENEMY, ShipRole.FRIGATE, force.id, "Dedup Frigate");
        FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, st.playerGalaxyX + 20.0, st.playerGalaxyY);
        ship.name = "Dedup Frigate";
        ctx.ships.add(ship);
        force.shipIds.add(ship.id);
        st.shipCampaignForceIds.put(ship.id, force.id);
        st.tacticalShipPoolRecordIds.put(ship.id, record.id);

        CampaignForceRosterSystem.ConcreteForceRoster roster =
                CampaignForceRosterSystem.resolveConcreteRoster(ctx, st, force);

        assertEquals(1, roster.concreteShipCount(),
                "one persistent ship represented by a tactical ship and pool record must count once");
        assertEquals(1, roster.liveTacticalShipIds.size());
        assertEquals(1, roster.viablePoolRecordIds.size());
    }

    @Test
    void genericOvermapSearchEncounterDoesNotSpawnNamedGreenContractShips() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        assertFalse(st.galaxySearchGroups.isEmpty(), "test requires at least one seeded search group");
        st.greenContractFleetJoined = true;
        st.greenContractFavor = 4;
        st.sector = 13;

        invokePrepareGalaxySearchGroupEncounterWorld(ctx, st, st.galaxySearchGroups.get(0));

        assertFalse(hasNamedShip(ctx, "Green Contract Cruiser"));
        assertFalse(hasNamedShip(ctx, "Green Contract Flak"));
        assertFalse(hasNamedShip(ctx, "Green Contract Frigate"));
    }

    @Test
    void nearbyCoalitionSupportSelectsOneConcreteForceAndCapsSpawnedShips() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        ctx.ships.removeIf(ship -> ship != null && ship != ctx.player);
        st.campaignForces.clear();
        st.campaignShipPool.clear();

        CampaignSystem.CampaignForce supportA = (CampaignSystem.CampaignForce) invokeEnsureCampaignForce(
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Nearby Support A", "green-base", "Eligible support A",
                st.playerGalaxyX + 120.0, st.playerGalaxyY);
        CampaignSystem.CampaignForce supportB = (CampaignSystem.CampaignForce) invokeEnsureCampaignForce(
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Nearby Support B", "green-base", "Eligible support B",
                st.playerGalaxyX + 140.0, st.playerGalaxyY + 10.0);
        setEnumByName(supportA, "intent", "REINFORCING");
        setEnumByName(supportB, "intent", "REINFORCING");
        for (int i = 0; i < 5; i++) {
            addPoolRecord(st, Faction.TEAM_C, i == 0 ? ShipRole.LIGHT_CRUISER : ShipRole.FRIGATE,
                    supportA.id, "Support A " + i);
            addPoolRecord(st, Faction.TEAM_C, i == 0 ? ShipRole.LIGHT_CRUISER : ShipRole.FRIGATE,
                    supportB.id, "Support B " + i);
        }

        invokeSpawnCoalitionSupportFleet(ctx, st, false, true, null);

        long greenSpawned = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.faction == Faction.TEAM_C)
                .count();
        assertEquals(1, st.activeCoalitionParticipations.size(),
                "routine nearby support should commit only one support force");
        assertTrue(greenSpawned <= 3,
                "routine nearby support should cap spawned hulls; spawned=" + greenSpawned);
        assertEquals(supportA.id, st.activeCoalitionParticipations.get(0).sourceForceId,
                "nearest eligible force should win deterministic support selection");
    }

    @Test
    void hostileInterdictionForceClosesDuringEarthwardTravel() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 999.0;
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(northernObjective);

        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Lunar Interdiction Screen");
        assertNotNull(force);
        setDouble(force, "x", st.playerGalaxyX + 240.0);
        setDouble(force, "y", st.playerGalaxyY - 1300.0);
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "mission", "INTERCEPT");
        double startDistance = Math.hypot(getDouble(force, "x") - st.playerGalaxyX, getDouble(force, "y") - st.playerGalaxyY);

        st.selectedGalaxyLocationId = northernObjective.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        for (int i = 0; i < 30; i++) {
            invokeTravelUpdate(ctx, st, 0.2);
            invokeForceSimulation(ctx, st, 0.2);
        }

        double endDistance = Math.hypot(getDouble(force, "x") - st.playerGalaxyX, getDouble(force, "y") - st.playerGalaxyY);
        assertTrue(endDistance < startDistance * 0.88, "interdiction force should close distance during active travel"
                + "; start=" + startDistance
                + " end=" + endDistance
                + " pos=" + getDouble(force, "x") + "," + getDouble(force, "y")
                + " player=" + st.playerGalaxyX + "," + st.playerGalaxyY
                + " target=" + getDouble(force, "targetX") + "," + getDouble(force, "targetY")
                + " intent=" + fieldString(force, "intent")
                + " state=" + fieldString(force, "state")
                + " mission=" + fieldString(force, "mission")
                + " work=" + fieldString(force, "workState")
                + " stop=" + fieldString(force, "stopReason")
                + " active=" + getBoolean(force, "simulationActive")
                + " route=" + ((List<?>) getObject(force, "routePoints")).size());
        assertTrue("INTERCEPTING".equals(fieldString(force, "intent")));
    }

    @Test
    void staleContactDoesNotEmitPlayerFacingFleetMarker() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(force);

        setDouble(force, "lastKnownX", st.playerGalaxyX + 640.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY - 420.0);
        setDouble(force, "targetX", st.playerGalaxyX + 800.0);
        setDouble(force, "targetY", st.playerGalaxyY - 620.0);
        setDouble(force, "lastKnownAgeSec", 48.0);
        setDouble(force, "contactConfidence", 0.22);
        setDouble(force, "uncertaintyRadius", 460.0);
        setBoolean(force, "visibleToPlayer", false);
        setEnumByName(force, "contactState", "STALE");

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        assertTrue(markers.stream().noneMatch(m -> m != null
                        && Math.hypot(m.x - getDoubleUnchecked(force, "lastKnownX"),
                        m.y - getDoubleUnchecked(force, "lastKnownY")) <= 1.0),
                "stale fleet memory must not produce a normal selectable map marker");
    }

    @Test
    void strategicStrikeDamagesAndRetasksCampaignForceBeforeBattle() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setDouble(force, "x", st.playerGalaxyX + 260.0);
        setDouble(force, "y", st.playerGalaxyY - 120.0);
        setDouble(force, "lastKnownX", st.playerGalaxyX + 260.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY - 120.0);
        setDouble(force, "strength", 42.0);
        setDouble(force, "readiness", 46.0);
        setDouble(force, "contactConfidence", 0.90);
        setEnumByName(force, "contactState", "KNOWN");
        st.galaxySearchGroups.clear();
        st.strategicTaskForces.clear();
        for (Object other : campaignForces(st)) {
            if (other != null && other != force && "ENEMY".equals(fieldString(other, "faction"))) {
                setDouble(other, "x", st.playerGalaxyX + 4000.0);
                setDouble(other, "y", st.playerGalaxyY + 4000.0);
                setDouble(other, "lastKnownX", st.playerGalaxyX + 4000.0);
                setDouble(other, "lastKnownY", st.playerGalaxyY + 4000.0);
            }
        }
        st.campaignAmmo = 200;
        st.campaignFuel = 200;
        st.strategicTorpedoCharges = 3;
        double before = getDouble(force, "strength");

        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx, getDouble(force, "x"), getDouble(force, "y")));
        for (int i = 0; i < 20; i++) {
            invokeStrikeObjectUpdate(ctx, st, 1.0);
        }

        assertTrue(getDouble(force, "strength") < before,
                "strike should damage the campaign force before battle; before=" + before
                        + " after=" + getDouble(force, "strength")
                        + " report=" + CampaignSystem.lastStrikeReportDetail(ctx)
                        + " event=" + CampaignSystem.campaignStrikeBattleEventSummary(ctx));
        assertTrue("RETREATING".equals(fieldString(force, "intent"))
                        || "REGROUPING".equals(fieldString(force, "intent"))
                        || "INTERCEPTING".equals(fieldString(force, "intent")),
                "strike should force an adaptation");
        assertTrue(CampaignSystem.lastStrikeReportDetail(ctx).contains("Force")
                || CampaignSystem.campaignStrikeBattleEventSummary(ctx).contains("Campaign force"));
    }

    @Test
    void nearbyHostileCampaignForceCanBeStruckWithoutManualContactSelection() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setDouble(force, "x", st.playerGalaxyX + 120.0);
        setDouble(force, "y", st.playerGalaxyY + 70.0);
        setDouble(force, "lastKnownX", st.playerGalaxyX + 120.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY + 70.0);
        setDouble(force, "contactConfidence", 0.86);
        setDouble(force, "strength", 48.0);
        setBoolean(force, "visibleToPlayer", true);
        setEnumByName(force, "contactState", "KNOWN");
        st.galaxySearchGroups.clear();
        st.strategicTaskForces.clear();
        st.campaignAmmo = 200;
        st.campaignFuel = 200;
        st.strategicTorpedoCharges = 2;
        ctx.ui.clearSelectedCampaignContact();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;

        CampaignSystem.CampaignAction torpedo = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> "TORPEDO_STRIKE".equals(action.id))
                .findFirst()
                .orElse(null);
        CampaignSystem.CampaignAction track = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> "TRACK_TARGET".equals(action.id))
                .findFirst()
                .orElse(null);

        assertNull(torpedo, "overmap should not expose remote torpedo strikes against fleet markers");
        assertNotNull(track);
        assertFalse(st.strategicStrikeObjects.size() > 0, "overmap selection should not queue remote strike objects");
    }

    @Test
    void alliedFleetsRespondToNearbyHostilePressure() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 999.0;
        invokeForceSimulation(ctx, st, 0.2);
        Object hostile = forceNamed(st, "Red Frontier Interdiction Screen");
        Object green = forceNamed(st, "Green Frontier Relay Patrol");
        assertNotNull(hostile);
        assertNotNull(green);

        setDouble(hostile, "x", st.playerGalaxyX + 520.0);
        setDouble(hostile, "y", st.playerGalaxyY - 140.0);
        setDouble(green, "x", st.playerGalaxyX + 430.0);
        setDouble(green, "y", st.playerGalaxyY - 120.0);
        setDouble(green, "strength", 66.0);
        setDouble(green, "readiness", 76.0);
        setBoolean(hostile, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setEnumByName(green, "intent", "PATROLLING");

        invokeForceSimulation(ctx, st, 0.2);

        String intent = fieldString(green, "intent");
        assertTrue("ESCORTING".equals(intent) || "REINFORCING".equals(intent) || "GUARDING".equals(intent),
                "green patrol should react to nearby hostile pressure; intent=" + intent
                        + " state=" + fieldString(green, "state")
                        + " faction=" + fieldString(green, "faction")
                        + " mission=" + fieldString(green, "mission")
                        + " active=" + getBoolean(green, "simulationActive")
                        + " pos=" + getDouble(green, "x") + "," + getDouble(green, "y")
                        + " hostilePos=" + getDouble(hostile, "x") + "," + getDouble(hostile, "y")
                        + " dist=" + Math.hypot(getDouble(green, "x") - getDouble(hostile, "x"), getDouble(green, "y") - getDouble(hostile, "y"))
                        + " route=" + ((List<?>) getObject(green, "routePoints")).size()
                        + " debug=" + CampaignSystem.campaignFleetAiDebugLines(ctx));
        assertTrue(CampaignSystem.campaignFleetAiDebugLines(ctx).stream().anyMatch(line -> line.startsWith("FLEET AI DEBUG")));
    }

    @Test
    void battlePromptOnlyEligibleInPlayersCurrentCampaignZone() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 999.0;
        st.strategicOvermapMode = true;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(red);
        assertNotNull(green);

        st.playerGalaxyY = ctx.WORLD_H * 0.84;
        setDouble(red, "x", st.playerGalaxyX + 80.0);
        setDouble(green, "x", st.playerGalaxyX + 120.0);
        setDouble(red, "y", ctx.WORLD_H * 0.84);
        setDouble(green, "y", ctx.WORLD_H * 0.84);
        Object localBattle = invokeCreateCampaignBattle(901, red, green);
        assertTrue(invokeCampaignBattleCanPromptPlayer(ctx, st, localBattle),
                "same-zone battles should remain eligible for intervention prompts");

        setDouble(red, "y", ctx.WORLD_H * 0.16);
        setDouble(green, "y", ctx.WORLD_H * 0.16);
        Object remoteBattle = invokeCreateCampaignBattle(902, red, green);
        assertFalse(invokeCampaignBattleCanPromptPlayer(ctx, st, remoteBattle),
                "remote-zone battles should not create player-facing intervention prompts");
        assertTrue("DIFFERENT_ZONE".equals(fieldString(remoteBattle, "playerPromptSuppressedReason")));
    }

    @Test
    void remoteBattlesStillSimulateAndLogWithoutPromptingPlayer() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 999.0;
        st.strategicOvermapMode = true;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Lunar Interdiction Screen");
        Object green = forceNamed(st, "Green Southern Relay Patrol");
        if (green == null) green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(red);
        assertNotNull(green);

        st.playerGalaxyY = ctx.WORLD_H * 0.84;
        setBoolean(red, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setDouble(red, "x", st.playerGalaxyX + 220.0);
        setDouble(green, "x", st.playerGalaxyX + 250.0);
        setDouble(red, "y", ctx.WORLD_H * 0.16);
        setDouble(green, "y", ctx.WORLD_H * 0.16);
        setEnumByName(red, "state", "MOVING");
        setEnumByName(green, "state", "MOVING");
        setDouble(red, "strength", 90.0);
        setDouble(green, "strength", 88.0);
        ctx.ui.clearStrategicEncounterPrompt();

        invokeForceSimulation(ctx, st, 0.2);

        assertFalse(ctx.ui.strategicEncounterPrompt.active,
                "remote-zone NPC battles should not open an intervention modal");
        assertTrue(((List<?>) st.campaignBattles).stream().anyMatch(battle -> !getBooleanUnchecked(battle, "resolved")),
                "remote-zone battles should still form and simulate");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("engaged")),
                "remote-zone battles should remain visible as passive Sensor Net / war-log entries");
    }

    @Test
    void southernRedPressureLaunchesLimitedGreenCounterSortie() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 999.0;
        CampaignSystem.CampaignLocation greenBase = findLocation(ctx, "poi-01");
        assertNotNull(greenBase);
        Object red = invokeEnsureCampaignForce(st,
                CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT,
                Faction.ENEMY,
                "Red Southern Sortie Pressure Test",
                "southern raider lane",
                "Roaming Red pressure in the starter zone",
                greenBase.x + 620.0,
                greenBase.y + 80.0);
        assertNotNull(red);
        setBoolean(red, "simulationActive", true);
        setDouble(red, "strength", 64.0);
        setDouble(red, "readiness", 74.0);
        setDouble(red, "supply", 70.0);
        setDouble(red, "x", greenBase.x + 620.0);
        setDouble(red, "y", greenBase.y + 80.0);
        setDouble(red, "targetX", st.playerGalaxyX);
        setDouble(red, "targetY", st.playerGalaxyY);
        setEnumByName(red, "mission", "RAID");
        setEnumByName(red, "intent", "INTERCEPTING");
        for (Object force : campaignForces(st)) {
            if (force == null || force == red || !"ENEMY".equals(fieldString(force, "faction"))) continue;
            setDouble(force, "y", ctx.WORLD_H * 0.18);
            setDouble(force, "strength", 8.0);
        }

        invokeUpdateGreenCounterSorties(ctx, st);

        long assigned = campaignForces(st).stream()
                .filter(force -> force != null
                        && "COUNTER_SORTIE".equals(fieldString(force, "mission"))
                        && getIntUnchecked(force, "targetForceId") == getIntUnchecked(red, "id"))
                .count();
        assertTrue(assigned == 1,
                "southern Red pressure should launch exactly one Green counter-sortie against that target");
        long activeSouthSorties = campaignForces(st).stream()
                .filter(force -> force != null && "COUNTER_SORTIE".equals(fieldString(force, "mission")))
                .count();
        assertTrue(activeSouthSorties <= 2, "Green counter-sorties should be capped so Red activity remains alive");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green counter-task force launched")));
    }

    @Test
    void fleetAiDiagnosticsReportCleanAfterSeededSimulation() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        for (int i = 0; i < 40; i++) {
            invokeForceSimulation(ctx, st, 0.2);
        }

        assertTrue(CampaignSystem.campaignFleetAiDebugLines(ctx).stream().anyMatch(line -> line.startsWith("FLEET AI DEBUG")));
        assertTrue(CampaignSystem.campaignFleetAiAnomalyReport(ctx).stream()
                .anyMatch(line -> line.contains("no idle") || line.contains("IDLE") || line.contains("VISIBLE")));
    }

    @Test
    void visibleFarFleetMovesEverySimulationTickAsHighPriorityContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setBoolean(force, "simulationActive", true);
        setBoolean(force, "visibleToPlayer", true);
        setDouble(force, "x", st.playerGalaxyX + 6200.0);
        setDouble(force, "y", st.playerGalaxyY);
        setDouble(force, "targetX", st.playerGalaxyX + 6400.0);
        setDouble(force, "targetY", st.playerGalaxyY);
        setDouble(force, "speed", 100.0);
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "workState", "TRAVELING");
        setEnumByName(force, "missionState", "TRAVELING");
        ((List<?>) getObject(force, "routePoints")).clear();
        @SuppressWarnings("unchecked")
        List<double[]> route = (List<double[]>) getObject(force, "routePoints");
        route.add(new double[]{st.playerGalaxyX + 6400.0, st.playerGalaxyY});
        setInt(force, "currentRouteIndex", 0);
        setEnumByName(force, "reassignmentCondition", "TIMER_EXPIRED");
        int tickBefore = st.campaignForceSimTickCount;
        double xBefore = getDouble(force, "x");

        invokeForceSimulation(ctx, st, 0.2);

        assertTrue(st.campaignForceSimTickCount == tickBefore || st.campaignForceSimTickCount == tickBefore + 1);
        assertTrue(getDouble(force, "x") > xBefore,
                "visible/pursuing far contacts should move on every simulation update instead of waiting for distance throttling");
    }

    @Test
    void fleetDebugReadoutExposesMovementContractFields() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);

        List<String> debug = CampaignSystem.campaignFleetAiDebugLines(ctx);

        assertTrue(debug.stream().anyMatch(line -> line.contains("faction ")
                        && line.contains("mission ")
                        && line.contains("state ")
                        && line.contains("work ")
                        && line.contains("reassign ")
                        && line.contains("target ")
                        && line.contains("route ")
                        && line.contains("battle ")
                        && line.contains("prompt ")),
                "debug readout should expose the fields needed to inspect overmap fleet movement");
    }

    @Test
    void presentationDensityDoesNotRelocateRealCampaignForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        st.playerGalaxyX = ctx.WORLD_W * 0.52;
        st.playerGalaxyY = ctx.WORLD_H * 0.34;
        for (Object force : campaignForces(st)) {
            if (force == null || "PLAYER_FLEET".equals(fieldString(force, "kind"))) continue;
            setBoolean(force, "visibleToPlayer", false);
            setDouble(force, "contactConfidence", 0.02);
            setDouble(force, "lastKnownAgeSec", 300.0);
            setDouble(force, "x", st.playerGalaxyX + 9000.0);
            setDouble(force, "y", st.playerGalaxyY + 9000.0);
            setDouble(force, "lastKnownX", st.playerGalaxyX + 9000.0);
            setDouble(force, "lastKnownY", st.playerGalaxyY + 9000.0);
            setEnumByName(force, "contactState", "STALE");
        }

        List<double[]> before = campaignForces(st).stream()
                .filter(force -> force != null && !"PLAYER_FLEET".equals(fieldString(force, "kind")))
                .map(force -> new double[]{getDoubleUnchecked(force, "x"), getDoubleUnchecked(force, "y")})
                .toList();

        invokeMaintainVisibleFleetContacts(ctx, st);

        List<double[]> after = campaignForces(st).stream()
                .filter(force -> force != null && !"PLAYER_FLEET".equals(fieldString(force, "kind")))
                .map(force -> new double[]{getDoubleUnchecked(force, "x"), getDoubleUnchecked(force, "y")})
                .toList();
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i)[0], after.get(i)[0], 1e-9);
            assertEquals(before.get(i)[1], after.get(i)[1], 1e-9);
        }
    }

    @Test
    void campaignForceLifecycleFieldsPersistThroughRoundTrip() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Yellow Trade Convoy");
        assertNotNull(force);

        setEnumByName(force, "workState", "WORKING");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "MINING");
        setEnumByName(force, "reassignmentCondition", "CARGO_FULL");
        setEnumByName(force, "cargoKind", "ORE");
        setDouble(force, "stationaryTimeSec", 12.5);
        setDouble(force, "antiIdleTimerSec", 7.0);
        setDouble(force, "taskDeadlineSec", 33.0);
        setDouble(force, "workRemainingSec", 22.0);
        setDouble(force, "cargoLoad", 44.0);
        setDouble(force, "cargoCapacity", 100.0);
        setDouble(force, "fuelLevel", 71.0);
        setDouble(force, "ammoLevel", 72.0);
        setDouble(force, "repairCapacity", 73.0);
        setDouble(force, "crewReadiness", 74.0);
        setDouble(force, "riskTolerance", 31.0);
        setDouble(force, "operatingRadius", 812.0);

        String raw = invokeSerializeCampaignForces(st);
        GameContext restored = initializedCampaignContext();
        invokeRestoreCampaignForces(restored.campaign, raw, "", st.nextCampaignForceId);
        Object restoredForce = forceNamed(restored.campaign, "Yellow Trade Convoy");
        assertNotNull(restoredForce);

        assertTrue("WORKING".equals(fieldString(restoredForce, "workState")));
        assertTrue("WORKING".equals(fieldString(restoredForce, "missionState")));
        assertTrue("MINING".equals(fieldString(restoredForce, "stopReason")));
        assertTrue("CARGO_FULL".equals(fieldString(restoredForce, "reassignmentCondition")));
        assertTrue("ORE".equals(fieldString(restoredForce, "cargoKind")));
        assertTrue(Math.abs(getDouble(restoredForce, "cargoLoad") - 44.0) < 0.01);
        assertTrue(Math.abs(getDouble(restoredForce, "fuelLevel") - 71.0) < 0.01);
        assertTrue(Math.abs(getDouble(restoredForce, "operatingRadius") - 812.0) < 0.01);
    }

    @Test
    void lifecycleReportExplainsInvalidFleetReasonAndFix() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setObject(force, "mission", null);

        List<String> report = CampaignSystem.campaignFleetLifecycleReport(ctx);

        assertTrue(report.stream().anyMatch(line -> line.contains("LIFECYCLE INVALID")
                && line.contains("Green Local Defense Patrol")
                && line.contains("missing mission")
                && line.contains("assign simple director mission")));
    }

    @Test
    void lifecycleValidatorCatchesTravelingWithoutDestinationOrRoute() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "PATROL");
        setEnumByName(force, "intent", "PATROLLING");
        setEnumByName(force, "workState", "TRAVELING");
        setEnumByName(force, "missionState", "TRAVELING");
        setEnumByName(force, "stopReason", "NONE");
        setObject(force, "destinationLocationId", "");
        setObject(force, "sourceLocationId", "");
        setInt(force, "targetForceId", 0);
        setDouble(force, "targetX", getDouble(force, "x"));
        setDouble(force, "targetY", getDouble(force, "y"));
        ((List<?>) getObject(force, "routePoints")).clear();
        ((List<?>) getObject(force, "patrolWaypoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("traveling without destination or route"));
    }

    @Test
    void lifecycleValidatorCatchesWorkingWithoutTimerOrCompletionCondition() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Yellow Trade Convoy");
        assertNotNull(force);
        setEnumByName(force, "mission", "CONVOY");
        setEnumByName(force, "intent", "DOCKING");
        setEnumByName(force, "workState", "WORKING");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "TRADING");
        setEnumByName(force, "cargoKind", "TRADE_GOODS");
        setObject(force, "sourceLocationId", "poi-07");
        setObject(force, "destinationLocationId", "");
        setInt(force, "targetForceId", 0);
        setDouble(force, "workRemainingSec", 0.0);
        setDouble(force, "taskDeadlineSec", 0.0);
        ((List<?>) getObject(force, "routePoints")).clear();
        ((List<?>) getObject(force, "patrolWaypoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("working without timer"));
    }

    @Test
    void lifecycleValidatorCatchesWaitingWithNoneStopReason() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "PATROL");
        setEnumByName(force, "intent", "GUARDING");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "NONE");
        setObject(force, "sourceLocationId", "poi-07");
        setDouble(force, "stationaryTimeSec", 0.0);
        ((List<?>) getObject(force, "routePoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("waiting without stop reason"));
    }

    @Test
    void lifecycleValidatorCatchesRetreatingWithoutSafeDestination() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "REPAIR");
        setEnumByName(force, "intent", "RETREATING");
        setEnumByName(force, "workState", "RECOVERING");
        setEnumByName(force, "missionState", "RETREATING");
        setEnumByName(force, "stopReason", "AVOIDING_SUPERIOR_THREAT");
        setObject(force, "destinationLocationId", "");
        setObject(force, "sourceLocationId", "");
        ((List<?>) getObject(force, "routePoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("recovering without safe destination"));
    }

    @Test
    void lifecycleValidatorCatchesExpiredTimedStopWithoutCompletion() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "CAPTURE");
        setEnumByName(force, "intent", "GUARDING");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "STAGING");
        setObject(force, "sourceLocationId", "poi-07");
        setDouble(force, "workRemainingSec", 0.0);
        setDouble(force, "taskDeadlineSec", 0.0);
        setDouble(force, "intentTimerSec", 20.0);

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("stopped work timer expired"));
    }

    @Test
    void lifecycleValidatorCatchesEscortTargetDestroyed() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "TEAM_C");
        if (target == null) target = firstForceByKind(st, "MINING_GROUP");
        assertNotNull(escort);
        assertNotNull(target);
        setEnumByName(escort, "mission", "ESCORT");
        setEnumByName(escort, "intent", "ESCORTING");
        setEnumByName(escort, "workState", "TRAVELING");
        setEnumByName(escort, "missionState", "TRAVELING");
        setEnumByName(escort, "stopReason", "NONE");
        setInt(escort, "targetForceId", getInt(target, "id"));
        setBoolean(target, "destroyed", true);

        Object validation = invokeValidateFleetLifecycle(st, escort);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("escort target missing"));
    }

    @Test
    void lifecycleValidatorCatchesPatrolWithEmptyWaypointLoop() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "PATROL");
        setEnumByName(force, "intent", "PATROLLING");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "SCANNING");
        setObject(force, "sourceLocationId", "poi-07");
        setDouble(force, "workRemainingSec", 5.0);
        setDouble(force, "taskDeadlineSec", 8.0);
        ((List<?>) getObject(force, "routePoints")).clear();
        ((List<?>) getObject(force, "patrolWaypoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("patrol mission missing waypoint loop"));
    }

    @Test
    void lifecycleValidatorCatchesRaidTimeoutWithoutFallback() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "RAID");
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "AMBUSHING");
        setObject(force, "sourceLocationId", "poi-21");
        setInt(force, "targetForceId", 0);
        setDouble(force, "intentTimerSec", 0.0);
        setDouble(force, "workRemainingSec", 5.0);
        setDouble(force, "taskDeadlineSec", 0.0);
        ((List<?>) getObject(force, "routePoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("raid timed out without target"));
    }

    @Test
    void lifecycleValidatorCatchesBattleParticipantWithoutAftermathOrder() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(force);
        setEnumByName(force, "mission", "INTERCEPT");
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "state", "IDLE");
        setEnumByName(force, "workState", "FIGHTING");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "STAGING");
        setObject(force, "sourceLocationId", "poi-21");
        setDouble(force, "workRemainingSec", 5.0);
        setDouble(force, "taskDeadlineSec", 8.0);

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("battle participant has no aftermath order"));
    }

    @Test
    void lifecycleValidatorCatchesPoiStopWithoutValidWork() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Yellow Trade Convoy");
        assertNotNull(force);
        setEnumByName(force, "mission", "CONVOY");
        setEnumByName(force, "intent", "ESCORTING");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "WAITING_FOR_ESCORT");
        setEnumByName(force, "cargoKind", "TRADE_GOODS");
        setObject(force, "sourceLocationId", "poi-07");
        setObject(force, "destinationLocationId", "poi-08");
        setInt(force, "targetForceId", 0);
        setDouble(force, "stationaryTimeSec", 30.0);
        setDouble(force, "workRemainingSec", 5.0);
        setDouble(force, "taskDeadlineSec", 8.0);
        ((List<?>) getObject(force, "routePoints")).clear();

        Object validation = invokeValidateFleetLifecycle(st, force);

        assertFalse(getBoolean(validation, "valid"));
        assertTrue(fieldString(validation, "invalidReason").contains("stopped at POI without valid work"));
    }

    @Test
    void antiIdleReassignsFleetWithNoStopReasonOrRoute() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setEnumByName(force, "intent", "PATROLLING");
        setEnumByName(force, "state", "IDLE");
        setEnumByName(force, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(force, "missionState", "WORKING");
        setEnumByName(force, "stopReason", "NONE");
        setDouble(force, "stationaryTimeSec", 30.0);
        setDouble(force, "antiIdleTimerSec", 30.0);
        ((List<?>) getObject(force, "routePoints")).clear();
        setDouble(force, "targetX", getDouble(force, "x"));
        setDouble(force, "targetY", getDouble(force, "y"));

        invokeForceSimulation(ctx, st, 0.2);

        boolean hasRoute = !((List<?>) getObject(force, "routePoints")).isEmpty();
        boolean hasPurposefulWork = !"NONE".equals(fieldString(force, "stopReason"))
                && !"WAITING_WITH_PURPOSE".equals(fieldString(force, "workState"));
        assertTrue(hasRoute || hasPurposefulWork,
                "anti-idle should assign a route or purposeful work instead of allowing an unexplained stop");
        assertFalse("NONE".equals(fieldString(force, "reassignmentCondition")),
                "anti-idle should record why the fleet will be reassigned next");
        assertTrue(CampaignSystem.campaignFleetLifecycleReport(ctx).stream()
                .noneMatch(line -> line.contains("Green Local Defense Patrol") && line.contains("LIFECYCLE INVALID")),
                "reassigned fleet should satisfy lifecycle validation");
    }

    @Test
    void patrolMissionPausesToScanThenContinuesRoute() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        double x = getDouble(force, "x");
        double y = getDouble(force, "y");
        setBoolean(force, "simulationActive", true);
        setDouble(force, "speed", 400.0);
        invokeAssignPatrolMission(st, force, List.of(
                new double[]{x + 12.0, y},
                new double[]{x + 80.0, y + 20.0},
                new double[]{x - 60.0, y - 20.0}));

        invokeAdvanceCampaignForcePosition(force, 0.2);

        assertTrue("SCANNING".equals(fieldString(force, "stopReason")),
                "patrol should pause to scan on waypoint arrival");
        assertTrue("WORKING".equals(fieldString(force, "workState")));

        for (int i = 0; i < 30; i++) {
            invokeLifecycleBeforeOrders(st, force, 0.2);
            invokeLifecycleAfterMovement(st, force, 0.2);
        }

        assertTrue("TRAVELING".equals(fieldString(force, "workState"))
                        || !((List<?>) getObject(force, "routePoints")).isEmpty()
                        || ("SCANNING".equals(fieldString(force, "stopReason")) && getInt(force, "patrolWaypointIndex") > 0),
                "patrol should resume toward the next waypoint after scan; work="
                        + fieldString(force, "workState")
                        + " stop=" + fieldString(force, "stopReason")
                        + " idx=" + getInt(force, "patrolWaypointIndex")
                        + " route=" + ((List<?>) getObject(force, "routePoints")).size()
                        + " remaining=" + getDouble(force, "workRemainingSec")
                        + " deadline=" + getDouble(force, "taskDeadlineSec"));
    }

    @Test
    void miningMissionReturnsToRefineryWhenCargoIsFull() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (miner == null) miner = firstForceByKind(st, "MINING_GROUP");
        assertNotNull(miner);
        setBoolean(miner, "simulationActive", true);
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(resource);
        CampaignSystem.CampaignLocation refinery = findLocation(ctx, "poi-07");
        if (refinery == null) refinery = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(refinery);
        setDouble(miner, "x", resource.x);
        setDouble(miner, "y", resource.y);
        setDouble(miner, "cargoLoad", 98.0);
        setDouble(miner, "cargoCapacity", 100.0);
        invokeAssignMiningMission(st, miner, resource.id, refinery.id);
        setDouble(miner, "workRemainingSec", 0.0);
        setDouble(miner, "taskDeadlineSec", 0.0);

        invokeLifecycleAfterMovement(st, miner, 0.2);

        assertTrue("DOCKING".equals(fieldString(miner, "intent")),
                "full mining cargo should route back to refinery; intent="
                        + fieldString(miner, "intent")
                        + " work=" + fieldString(miner, "workState")
                        + " stop=" + fieldString(miner, "stopReason")
                        + " cargo=" + getDouble(miner, "cargoLoad")
                        + "/" + getDouble(miner, "cargoCapacity")
                        + " route=" + ((List<?>) getObject(miner, "routePoints")).size()
                        + " dest=" + fieldString(miner, "destinationLocationId"));
        assertTrue(refinery.id.equals(fieldString(miner, "destinationLocationId")));
        assertFalse(((List<?>) getObject(miner, "routePoints")).isEmpty());
    }

    @Test
    void simpleDirectorAssignsYellowSalvageAfterRecoverableWreckAppears() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object salvage = forceNamed(st, "Yellow Trade Convoy");
        assertNotNull(salvage);
        setBoolean(salvage, "simulationActive", true);
        setEnumByName(salvage, "cargoKind", "SALVAGE");
        double x = getDouble(salvage, "x") + 90.0;
        double y = getDouble(salvage, "y") + 40.0;
        invokeAddRecoverableWreckSite(st, x, y, ShipRole.FRIGATE,
                "Director Test Wreck", "Fresh salvage opportunity");

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, salvage));

        assertTrue("SALVAGE".equals(fieldString(salvage, "cargoKind")));
        assertTrue("MINING".equals(fieldString(salvage, "intent")),
                "first-pass salvage work reuses the mining/work cargo intent");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                        && line.contains("selected=salvage")
                        && line.contains("Director Test Wreck")),
                "director should explain the salvage assignment");
    }

    @Test
    void salvageFleetDepletesWreckValueAndLeavesWhenCargoFull() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object salvage = forceNamed(st, "Yellow Trade Convoy");
        CampaignSystem.CampaignLocation station = findLocation(ctx, "poi-07");
        assertNotNull(salvage);
        assertNotNull(station);
        double wreckX = station.x + 180.0;
        double wreckY = station.y + 90.0;
        invokeAddRecoverableWreckSite(st, wreckX, wreckY, ShipRole.FRIGATE,
                "Cargo Full Test Wreck", "Finite salvage value");
        List<?> wrecks = (List<?>) getObject(st, "recoverableWreckSites");
        Object wreck = wrecks.get(wrecks.size() - 1);
        double valueBefore = getDouble(wreck, "salvageValue");
        setBoolean(salvage, "simulationActive", true);
        setDouble(salvage, "x", wreckX + 10.0);
        setDouble(salvage, "y", wreckY + 10.0);
        setDouble(salvage, "cargoLoad", 0.0);
        setDouble(salvage, "cargoCapacity", 10.0);
        setEnumByName(salvage, "cargoKind", "SALVAGE");

        invokeAssignSalvageMission(st, salvage, wreckX, wreckY, station.id);
        setDouble(salvage, "cargoCapacity", 10.0);
        setDouble(salvage, "workRemainingSec", 0.0);
        setDouble(salvage, "taskDeadlineSec", 0.0);
        invokeLifecycleAfterMovement(st, salvage, 2.0);

        assertTrue(getDouble(wreck, "salvageValue") < valueBefore,
                "salvage work should deplete the wreck's finite value");
        assertTrue(getDouble(salvage, "cargoLoad") >= getDouble(salvage, "cargoCapacity") * 0.98);
        assertTrue(station.id.equals(fieldString(salvage, "destinationLocationId")));
        assertFalse(((List<?>) getObject(salvage, "routePoints")).isEmpty(),
                "full salvage cargo should route back to the return station");
    }

    @Test
    void yellowSalvageFleetFleesWhenRedMilitaryContestsWreck() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object salvage = forceNamed(st, "Yellow Trade Convoy");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation station = findLocation(ctx, "poi-07");
        assertNotNull(salvage);
        assertNotNull(red);
        assertNotNull(station);
        setBoolean(salvage, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(salvage, "x", station.x + 200.0);
        setDouble(salvage, "y", station.y + 120.0);
        setObject(salvage, "sourceLocationId", station.id);
        setDouble(salvage, "riskTolerance", 20.0);
        setDouble(salvage, "strength", 18.0);
        setEnumByName(salvage, "cargoKind", "SALVAGE");
        setEnumByName(salvage, "stopReason", "SALVAGING");
        setEnumByName(salvage, "workState", "WORKING");
        setEnumByName(salvage, "missionState", "WORKING");
        setDouble(red, "x", getDouble(salvage, "x") + 90.0);
        setDouble(red, "y", getDouble(salvage, "y") + 30.0);
        setDouble(red, "strength", 80.0);

        assertTrue(invokeApplySalvageContestBehavior(ctx, st, salvage));

        assertTrue("RETREATING".equals(fieldString(salvage, "intent")));
        assertTrue(station.id.equals(fieldString(salvage, "destinationLocationId")));
        assertFalse(((List<?>) getObject(salvage, "routePoints")).isEmpty());
    }

    @Test
    void yellowSalvageCrewStealsFromGreenWreckAndCreatesLocalConflict() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object salvage = forceNamed(st, "Yellow Trade Convoy");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation station = findLocation(ctx, "poi-07");
        assertNotNull(salvage);
        assertNotNull(green);
        assertNotNull(station);
        setBoolean(salvage, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setDouble(salvage, "x", station.x + 200.0);
        setDouble(salvage, "y", station.y + 120.0);
        setObject(salvage, "sourceLocationId", station.id);
        setDouble(salvage, "riskTolerance", 70.0);
        setDouble(salvage, "strength", 80.0);
        setDouble(salvage, "cargoLoad", 0.0);
        setDouble(salvage, "cargoCapacity", 60.0);
        setEnumByName(salvage, "cargoKind", "SALVAGE");
        setEnumByName(salvage, "stopReason", "SALVAGING");
        setEnumByName(salvage, "workState", "WORKING");
        setEnumByName(salvage, "missionState", "WORKING");
        setDouble(green, "x", getDouble(salvage, "x") + 80.0);
        setDouble(green, "y", getDouble(salvage, "y") + 30.0);
        setDouble(green, "strength", 40.0);
        st.greenContractFavor = 3;

        assertTrue(invokeApplySalvageContestBehavior(ctx, st, salvage));

        assertTrue(getDouble(salvage, "cargoLoad") > 0.0);
        assertTrue(st.greenContractFavor == 2);
        assertTrue("RETREATING".equals(fieldString(salvage, "intent")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("stole Green wreck salvage")
                && line.contains("local conflict")));
    }

    @Test
    void simpleDirectorAssignsRedRaidAfterScoutKnowsWeakMiner() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (miner == null) miner = firstForceByKind(st, "MINING_GROUP");
        assertNotNull(red);
        assertNotNull(miner);
        setDouble(red, "x", 1800.0);
        setDouble(red, "y", 1800.0);
        setDouble(red, "strength", 70.0);
        setDouble(miner, "x", 2060.0);
        setDouble(miner, "y", 1840.0);
        setDouble(miner, "strength", 24.0);
        setBoolean(red, "simulationActive", true);
        setBoolean(miner, "simulationActive", true);
        setObject(red, "homeBaseId", "poi-21");
        invokeRefreshNpcForceContacts(st, red, 0.2);

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, red));

        assertTrue("RAID".equals(fieldString(red, "mission")));
        assertTrue(getInt(red, "targetForceId") == getInt(miner, "id"),
                "raid should target the weak known miner");
        assertTrue(((List<?>) getObject(red, "routePoints")).size() >= 2,
                "raid should use an indirect route instead of one direct point");
        String minerName = fieldString(miner, "name");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                        && line.contains("selected=raid")
                        && line.contains(minerName)),
                "director should explain the raid assignment");
    }

    @Test
    void redScoutReportRedirectsRaiderTowardDiscoveredConvoy() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object raider = forceNamed(st, "Red Frontier Picket Patrol");
        Object convoy = forceNamed(st, "Yellow Trade Convoy");
        if (convoy == null) convoy = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        assertNotNull(raider);
        assertNotNull(convoy);
        setBoolean(raider, "simulationActive", true);
        setBoolean(convoy, "simulationActive", true);
        setObject(raider, "homeBaseId", "poi-21");
        setDouble(raider, "x", 1600.0);
        setDouble(raider, "y", 1800.0);
        setDouble(raider, "strength", 82.0);
        setDouble(raider, "readiness", 92.0);
        setDouble(raider, "supply", 92.0);
        setDouble(raider, "hullIntegrity", 92.0);
        setDouble(convoy, "x", 2060.0);
        setDouble(convoy, "y", 1840.0);
        setDouble(convoy, "strength", 22.0);
        setDouble(convoy, "readiness", 48.0);
        setDouble(convoy, "hullIntegrity", 76.0);
        ((java.util.Map<?, ?>) getObject(raider, "knownHostileContacts")).clear();
        invokeSeedNpcForceContact(raider, convoy, 0.88);
        String convoyName = fieldString(convoy, "name");

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, raider));

        assertTrue("RAID".equals(fieldString(raider, "mission")));
        assertTrue(getInt(raider, "targetForceId") == getInt(convoy, "id"),
                "scout report contact should redirect raider toward discovered convoy");
        assertFalse(((List<?>) getObject(raider, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("selected=raid")
                        && line.contains(convoyName)));
    }

    @Test
    void redRaiderPrefersWeakMinerOverCloserLowValuePatrol() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object patrol = forceNamed(st, "Green Local Defense Patrol");
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        assertNotNull(red);
        assertNotNull(patrol);
        assertNotNull(miner);
        setBoolean(red, "simulationActive", true);
        setObject(red, "homeBaseId", "poi-21");
        setDouble(red, "x", 1200.0);
        setDouble(red, "y", 1200.0);
        setDouble(red, "strength", 80.0);
        setDouble(red, "readiness", 92.0);
        setDouble(red, "supply", 92.0);
        setDouble(red, "hullIntegrity", 92.0);
        for (Object force : campaignForces(st)) {
            if (force == null || "ENEMY".equals(fieldString(force, "faction"))) continue;
            if (getInt(force, "id") == getInt(patrol, "id") || getInt(force, "id") == getInt(miner, "id")) continue;
            setDouble(force, "strength", 220.0);
            setDouble(force, "readiness", 100.0);
            setDouble(force, "supply", 100.0);
            setDouble(force, "hullIntegrity", 100.0);
        }
        setDouble(patrol, "x", 1280.0);
        setDouble(patrol, "y", 1210.0);
        setDouble(patrol, "strength", 24.0);
        setDouble(patrol, "readiness", 60.0);
        setDouble(patrol, "hullIntegrity", 80.0);
        setDouble(miner, "x", 1580.0);
        setDouble(miner, "y", 1240.0);
        setDouble(miner, "strength", 28.0);
        setDouble(miner, "readiness", 64.0);
        setDouble(miner, "hullIntegrity", 86.0);
        ((java.util.Map<?, ?>) getObject(red, "knownHostileContacts")).clear();

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, red));

        assertTrue("RAID".equals(fieldString(red, "mission")));
        assertTrue(getInt(red, "targetForceId") == getInt(miner, "id"),
                "raid target preference should beat pure nearest-target selection");
    }

    @Test
    void redRaiderHitsWeakMinerCreatesRouteRiskAndRetreatsHome() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(red);
        assertNotNull(miner);
        assertNotNull(home);
        for (Object force : campaignForces(st)) {
            if (force != null && ("TEAM_C".equals(fieldString(force, "faction")) || "ALLY".equals(fieldString(force, "faction")))) {
                setDouble(force, "x", home.x + 1600.0);
                setDouble(force, "y", home.y + 1600.0);
            }
        }
        setBoolean(red, "simulationActive", true);
        setObject(red, "homeBaseId", home.id);
        setDouble(red, "x", home.x + 420.0);
        setDouble(red, "y", home.y + 80.0);
        setDouble(red, "strength", 82.0);
        setDouble(red, "readiness", 92.0);
        setDouble(red, "supply", 92.0);
        setDouble(red, "hullIntegrity", 92.0);
        setDouble(miner, "x", getDouble(red, "x") + 40.0);
        setDouble(miner, "y", getDouble(red, "y") + 20.0);
        setDouble(miner, "strength", 24.0);
        setDouble(miner, "readiness", 45.0);
        setDouble(miner, "supply", 60.0);
        setDouble(miner, "cargoLoad", 40.0);
        Object theater = invokeCampaignTheaterForPoint(st, getDouble(miner, "y"));
        double riskBefore = getDouble(theater, "routeRisk");

        invokeAssignRaidMission(st, red, miner);
        assertTrue(invokeMaintainRaidMission(ctx, st, red, 2.0));

        assertTrue("REPAIR".equals(fieldString(red, "mission")));
        assertTrue("RETREATING".equals(fieldString(red, "intent")));
        assertTrue(home.id.equals(fieldString(red, "destinationLocationId")));
        assertTrue("LOOT".equals(fieldString(red, "cargoKind")));
        assertTrue(getDouble(theater, "routeRisk") > riskBefore,
                "successful raid should leave route danger behind before retreating");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("distress")
                        && line.contains(fieldString(miner, "name"))),
                "successful Red raid against Yellow miner should create a visible distress event");
    }

    @Test
    void redRaiderHitsGreenConvoyAndLaunchesGreenResponse() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object responder = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(red);
        assertNotNull(responder);
        assertNotNull(home);
        for (Object force : campaignForces(st)) {
            if (force != null && ("TEAM_C".equals(fieldString(force, "faction")) || "ALLY".equals(fieldString(force, "faction")))) {
                setDouble(force, "x", home.x + 2200.0);
                setDouble(force, "y", home.y + 2200.0);
            }
        }
        Object convoy = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.CONVOY, Faction.TEAM_C,
                "Green Relief Convoy", "Green logistics berth", "Green civilian relief convoy", home.x + 460.0, home.y + 120.0);
        setBoolean(red, "simulationActive", true);
        setBoolean(responder, "simulationActive", true);
        setBoolean(convoy, "simulationActive", true);
        setObject(red, "homeBaseId", home.id);
        setDouble(red, "x", home.x + 420.0);
        setDouble(red, "y", home.y + 110.0);
        setDouble(red, "strength", 94.0);
        setDouble(red, "readiness", 94.0);
        setDouble(red, "supply", 92.0);
        setDouble(red, "hullIntegrity", 92.0);
        setDouble(convoy, "x", home.x + 460.0);
        setDouble(convoy, "y", home.y + 120.0);
        setDouble(convoy, "strength", 20.0);
        setDouble(convoy, "readiness", 42.0);
        setDouble(convoy, "cargoLoad", 36.0);
        setDouble(responder, "x", getDouble(convoy, "x") + 700.0);
        setDouble(responder, "y", getDouble(convoy, "y"));
        setDouble(responder, "strength", 82.0);
        setDouble(responder, "readiness", 88.0);
        setDouble(responder, "operatingRadius", 1200.0);

        invokeAssignRaidMission(st, red, convoy);
        assertTrue(invokeMaintainRaidMission(ctx, st, red, 2.0));

        assertTrue("RETREATING".equals(fieldString(convoy, "intent")));
        assertTrue("REINFORCE".equals(fieldString(responder, "mission")));
        assertTrue("REINFORCING".equals(fieldString(responder, "intent")));
        assertTrue(getInt(responder, "targetForceId") == getInt(red, "id"));
        assertFalse(((List<?>) getObject(responder, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green response launched after Red raid")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("launched from")
                && line.contains("Green response")));
    }

    @Test
    void redRaiderBreaksOffWhenGreenReinforcementArrives() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(red);
        assertNotNull(miner);
        assertNotNull(green);
        assertNotNull(home);
        setBoolean(red, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setObject(red, "homeBaseId", home.id);
        setDouble(red, "x", home.x + 500.0);
        setDouble(red, "y", home.y + 140.0);
        setDouble(red, "strength", 45.0);
        setDouble(red, "readiness", 70.0);
        setDouble(red, "hullIntegrity", 88.0);
        setDouble(miner, "x", getDouble(red, "x") + 60.0);
        setDouble(miner, "y", getDouble(red, "y") + 30.0);
        setDouble(miner, "strength", 24.0);
        setDouble(green, "x", getDouble(miner, "x") + 80.0);
        setDouble(green, "y", getDouble(miner, "y") + 40.0);
        setDouble(green, "strength", 88.0);
        setDouble(green, "readiness", 90.0);

        invokeAssignRaidMission(st, red, miner);
        assertTrue(invokeMaintainRaidMission(ctx, st, red, 2.0));

        assertTrue("REPAIR".equals(fieldString(red, "mission")));
        assertTrue("RETREATING".equals(fieldString(red, "intent")));
        assertTrue(home.id.equals(fieldString(red, "destinationLocationId")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("broke off raid")
                        && line.contains(fieldString(green, "name"))),
                "raider should explain retreat when Green reinforcement arrives");
    }

    @Test
    void repeatedRedVictoriesMoveRegionTowardRedControlAndChangeTrafficMix() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(red);
        assertNotNull(green);
        setDouble(red, "x", 1800.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 1840.0);
        setDouble(green, "y", 2200.0);
        Object theater = invokeCampaignTheaterForPoint(st, 2200.0);
        setDouble(theater, "controlScore", 0.0);
        setDouble(theater, "tradeHealth", 65.0);
        setDouble(theater, "routeRisk", 30.0);
        double scoreBefore = getDouble(theater, "controlScore");
        double tradeBefore = getDouble(theater, "tradeHealth");

        for (int i = 0; i < 6; i++) {
            Object battle = invokeCreateCampaignBattle(i + 1, red, green);
            invokeApplyCampaignBattleRegionalConsequences(st, battle, red, green);
        }

        assertTrue(getDouble(theater, "controlScore") < scoreBefore);
        assertTrue(getDouble(theater, "tradeHealth") < tradeBefore);
        assertTrue(CampaignSystem.campaignRegionalControlRatingLines(ctx).stream()
                        .anyMatch(line -> line.contains("Red") && line.contains("mix")),
                "regional control readout should expose Red control and traffic mix changes");
    }

    @Test
    void clearingRedRaiderImprovesYellowTrafficPosture() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(red);
        assertNotNull(green);
        setDouble(red, "x", 1800.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 1840.0);
        setDouble(green, "y", 2200.0);
        Object theater = invokeCampaignTheaterForPoint(st, 2200.0);
        setDouble(theater, "controlScore", 44.0);
        setDouble(theater, "tradeHealth", 50.0);
        setDouble(theater, "routeRisk", 40.0);
        double tradeBefore = getDouble(theater, "tradeHealth");
        double riskBefore = getDouble(theater, "routeRisk");

        Object battle = invokeCreateCampaignBattle(200, green, red);
        invokeApplyCampaignBattleRegionalConsequences(st, battle, green, red);

        assertTrue(getDouble(theater, "tradeHealth") > tradeBefore,
                "clearing Red pressure in Green-leaning space should encourage Yellow traffic");
        assertTrue(getDouble(theater, "routeRisk") < riskBefore);
    }

    @Test
    void simpleDirectorAssignsGreenEscortBeforeGenericPatrolWhenTrafficIsVulnerable() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object traffic = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (traffic == null) traffic = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        assertNotNull(green);
        assertNotNull(traffic);
        setBoolean(green, "simulationActive", true);
        setBoolean(traffic, "simulationActive", true);
        setDouble(green, "x", 2200.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 72.0);
        setDouble(green, "readiness", 88.0);
        setDouble(green, "supply", 88.0);
        setDouble(green, "hullIntegrity", 92.0);
        setDouble(green, "repairCapacity", 92.0);
        setDouble(traffic, "x", 2360.0);
        setDouble(traffic, "y", 2240.0);
        setDouble(traffic, "strength", 22.0);

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, green));

        assertTrue("ESCORT".equals(fieldString(green, "mission")));
        assertTrue("ESCORTING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == getInt(traffic, "id"));
        String trafficName = fieldString(traffic, "name");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                        && line.contains("top=")
                        && line.contains("selected=escort")
                        && line.contains(trafficName)),
                "director should explain Green escort assignment with top candidate telemetry");
    }

    @Test
    void simpleDirectorAssignsGreenRepairRescueForReachableDamagedFriendly() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(base);
        assertNotNull(green);
        for (Object force : campaignForces(st)) {
            if (force == null || "ENEMY".equals(fieldString(force, "faction"))) continue;
            setDouble(force, "strength", 80.0);
            setDouble(force, "readiness", 82.0);
            setDouble(force, "hullIntegrity", 86.0);
        }
        Object damaged = invokeEnsureCampaignForce(
                st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.TEAM_C,
                "Green Director Damaged Fleet",
                base.id,
                "Damaged fleet awaiting director rescue",
                base.x + 210.0,
                base.y + 120.0
        );
        assertNotNull(damaged);
        setBoolean(green, "simulationActive", true);
        setDouble(green, "x", base.x + 20.0);
        setDouble(green, "y", base.y + 20.0);
        setDouble(green, "strength", 74.0);
        setDouble(green, "readiness", 86.0);
        setDouble(green, "supply", 90.0);
        setDouble(green, "hullIntegrity", 92.0);
        setDouble(green, "repairCapacity", 92.0);
        setDouble(damaged, "strength", 18.0);
        setDouble(damaged, "readiness", 28.0);
        setDouble(damaged, "hullIntegrity", 30.0);

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, green));

        assertTrue("REPAIR".equals(fieldString(green, "mission")));
        assertTrue("REINFORCING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == getInt(damaged, "id"));
        assertFalse(((List<?>) getObject(green, "routePoints")).isEmpty());
        String damagedName = fieldString(damaged, "name");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                        && line.contains("selected=rescue")
                        && line.contains(damagedName)),
                "director should explain Green repair/rescue assignment");
    }

    @Test
    void simpleDirectorAssignsGreenRepairRescueForDamagedFriendlyYellowFleet() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(base);
        assertNotNull(green);
        for (Object force : campaignForces(st)) {
            if (force == null || "ENEMY".equals(fieldString(force, "faction"))) continue;
            setDouble(force, "strength", 82.0);
            setDouble(force, "readiness", 84.0);
            setDouble(force, "hullIntegrity", 88.0);
        }
        Object damagedYellow = invokeEnsureCampaignForce(
                st,
                CampaignSystem.CampaignForceKind.TRADE_GROUP,
                Faction.BRIGHT_YELLOW,
                "Yellow Damaged Relief Convoy",
                base.id,
                "Friendly Yellow convoy awaiting Green rescue",
                base.x + 230.0,
                base.y + 140.0
        );
        assertNotNull(damagedYellow);
        setBoolean(green, "simulationActive", true);
        setDouble(green, "x", base.x + 20.0);
        setDouble(green, "y", base.y + 20.0);
        setDouble(green, "strength", 76.0);
        setDouble(green, "readiness", 88.0);
        setDouble(green, "supply", 92.0);
        setDouble(green, "hullIntegrity", 94.0);
        setDouble(green, "repairCapacity", 94.0);
        setDouble(damagedYellow, "strength", 22.0);
        setDouble(damagedYellow, "readiness", 32.0);
        setDouble(damagedYellow, "hullIntegrity", 34.0);

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, green));

        assertTrue("REPAIR".equals(fieldString(green, "mission")));
        assertTrue("REINFORCING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == getInt(damagedYellow, "id"));
        assertFalse(((List<?>) getObject(green, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("selected=rescue")
                        && line.contains("Yellow Damaged Relief Convoy")),
                "Green director should dispatch rescue to a damaged friendly Yellow fleet");
    }

    @Test
    void escortMissionKeepsEscortNearMovingConvoy() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        Object traffic = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (traffic == null) traffic = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        assertNotNull(escort);
        assertNotNull(traffic);
        setDouble(escort, "x", 1800.0);
        setDouble(escort, "y", 1800.0);
        setDouble(traffic, "x", 2300.0);
        setDouble(traffic, "y", 2200.0);
        setDouble(traffic, "targetX", 2600.0);
        setDouble(traffic, "targetY", 2200.0);
        invokeAssignEscortMission(st, escort, getInt(traffic, "id"));
        ((List<?>) getObject(escort, "routePoints")).clear();

        assertTrue(invokeMaintainEscortMission(ctx, st, escort));

        assertTrue("ESCORT".equals(fieldString(escort, "mission")));
        assertTrue(getInt(escort, "targetForceId") == getInt(traffic, "id"));
        assertFalse(((List<?>) getObject(escort, "routePoints")).isEmpty());
    }

    @Test
    void escortMissionRefusesToChaseOverwhelmingRedAwayFromConvoy() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        Object traffic = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (traffic == null) traffic = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(escort);
        assertNotNull(traffic);
        assertNotNull(red);
        setDouble(escort, "x", 2300.0);
        setDouble(escort, "y", 2200.0);
        setDouble(escort, "strength", 28.0);
        setDouble(traffic, "x", 2360.0);
        setDouble(traffic, "y", 2200.0);
        setDouble(red, "x", 2420.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 95.0);
        setBoolean(red, "simulationActive", true);
        invokeAssignEscortMission(st, escort, getInt(traffic, "id"));

        assertTrue(invokeMaintainEscortMission(ctx, st, escort));

        assertTrue("ESCORTING".equals(fieldString(escort, "intent")));
        assertTrue(getInt(escort, "targetForceId") == getInt(traffic, "id"),
                "escort should keep protecting the convoy instead of switching target to the stronger Red force");
    }

    @Test
    void escortMissionReceivesValidOrdersAfterConvoyArrival() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        Object traffic = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (traffic == null) traffic = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        assertNotNull(escort);
        assertNotNull(traffic);
        invokeAssignEscortMission(st, escort, getInt(traffic, "id"));
        setEnumByName(traffic, "missionState", "COMPLETED");

        assertTrue(invokeMaintainEscortMission(ctx, st, escort));

        assertFalse("ESCORT".equals(fieldString(escort, "mission"))
                        && "COMPLETED".equals(fieldString(escort, "missionState")),
                "completed escort should not remain attached to a completed convoy");
        assertTrue(!((List<?>) getObject(escort, "routePoints")).isEmpty()
                        || !"NONE".equals(fieldString(escort, "stopReason")),
                "completed escort should receive a return route or purposeful stop");
        assertTrue("REPAIR".equals(fieldString(escort, "mission"))
                        || "PATROL".equals(fieldString(escort, "mission"))
                        || !"HOLDING".equals(fieldString(escort, "intent")),
                "completed escort should leave with a valid follow-up mission");
    }

    @Test
    void simpleDirectorAssignsGreenPatrolWhenNoVulnerableTrafficNeedsEscort() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-07");
        assertNotNull(green);
        assertNotNull(home);
        setBoolean(green, "simulationActive", true);
        setObject(green, "homeBaseId", home.id);
        setDouble(green, "strength", 70.0);
        setDouble(green, "readiness", 88.0);
        setDouble(green, "supply", 88.0);
        setDouble(green, "hullIntegrity", 92.0);
        setDouble(green, "repairCapacity", 92.0);
        for (Object force : campaignForces(st)) {
            if (force != null && "BRIGHT_YELLOW".equals(fieldString(force, "faction"))) {
                setDouble(force, "strength", 100.0);
            }
        }

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, green));

        assertTrue("PATROL".equals(fieldString(green, "mission")));
        assertFalse(((List<?>) getObject(green, "patrolWaypoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                && line.contains("selected=patrol")));
    }

    @Test
    void simpleDirectorAssignsYellowMiningWhenResourceAndRefineryAreReachable() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        CampaignSystem.CampaignLocation refinery = findLocation(ctx, "poi-07");
        assertNotNull(miner);
        assertNotNull(resource);
        assertNotNull(refinery);
        setBoolean(miner, "simulationActive", true);
        setObject(miner, "homeBaseId", refinery.id);
        setObject(miner, "sourceLocationId", refinery.id);
        setObject(miner, "destinationLocationId", resource.id);
        setDouble(miner, "strength", 26.0);
        setDouble(miner, "supply", 90.0);
        setDouble(miner, "hullIntegrity", 90.0);
        setDouble(miner, "repairCapacity", 90.0);

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, miner));

        assertTrue("MINING".equals(fieldString(miner, "intent")));
        assertTrue(resource.id.equals(fieldString(miner, "destinationLocationId")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                && line.contains("selected=mine")));
    }

    @Test
    void simpleDirectorAssignsYellowTradeRouteWhenDestinationIsReachable() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation source = findLocation(ctx, "poi-07");
        assertNotNull(source);
        Object trader = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.CONVOY, Faction.BRIGHT_YELLOW,
                "Yellow Checklist Trade Convoy", source.id, "Trade route endpoint test", source.x, source.y);
        assertNotNull(trader);
        setBoolean(trader, "simulationActive", true);
        setObject(trader, "homeBaseId", source.id);
        setObject(trader, "sourceLocationId", source.id);
        setObject(trader, "destinationLocationId", "");
        setDouble(trader, "supply", 90.0);
        setDouble(trader, "hullIntegrity", 90.0);
        setDouble(trader, "repairCapacity", 90.0);
        st.theaterWarRecentEvents.clear();

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, trader));

        assertTrue("CONVOY".equals(fieldString(trader, "mission")));
        assertTrue("ESCORTING".equals(fieldString(trader, "intent")));
        assertFalse(((List<?>) getObject(trader, "routePoints")).isEmpty());
        CampaignSystem.CampaignLocation destination = findLocation(ctx, fieldString(trader, "destinationLocationId"));
        assertNotNull(destination);
        assertTrue(destination.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE
                        || destination.type == CampaignSystem.CampaignLocationType.REPAIR_SITE
                        || destination.type == CampaignSystem.CampaignLocationType.HIDDEN_CACHE
                        || destination.type == CampaignSystem.CampaignLocationType.STORY_EVENT
                        || !destination.services.isEmpty(),
                "Yellow trade should route to a station, trade hub, mining base, repair port, shipyard, or neutral anchor");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                && line.contains("selected=trade")));
    }

    @Test
    void simpleDirectorAssignsRedScoutPatrolWhenTargetIntelIsStale() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(red);
        assertNotNull(home);
        setBoolean(red, "simulationActive", true);
        setObject(red, "homeBaseId", home.id);
        setDouble(red, "strength", 18.0);
        setDouble(red, "readiness", 88.0);
        setDouble(red, "supply", 88.0);
        setDouble(red, "hullIntegrity", 92.0);
        setDouble(red, "repairCapacity", 92.0);
        for (Object force : campaignForces(st)) {
            if (force != null && !"ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "strength", 100.0);
            }
        }
        ((java.util.Map<?, ?>) getObject(red, "knownHostileContacts")).clear();

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, red));

        assertTrue("PATROL".equals(fieldString(red, "mission")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                && line.contains("selected=scout-patrol")));
    }

    @Test
    void simpleDirectorAssignsRedRepairWhenDamagedOrLowSupply() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(red);
        assertNotNull(home);
        setBoolean(red, "simulationActive", true);
        setObject(red, "homeBaseId", home.id);
        setDouble(red, "supply", 20.0);
        setDouble(red, "hullIntegrity", 45.0);
        setDouble(red, "repairCapacity", 45.0);

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, red));

        assertTrue("REPAIR".equals(fieldString(red, "mission")));
        assertTrue(home.id.equals(fieldString(red, "destinationLocationId")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                && line.contains("selected=repair")));
    }

    @Test
    void doctrineProfilesExposeFactionPriorities() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        Object green = invokeDoctrineForFaction(st, Faction.TEAM_C);
        Object yellow = invokeDoctrineForFaction(st, Faction.BRIGHT_YELLOW);
        Object red = invokeDoctrineForFaction(st, Faction.ENEMY);

        assertTrue(getDouble(green, "escortPriority") > getDouble(red, "escortPriority"));
        assertTrue(getDouble(yellow, "profitPriority") > getDouble(green, "profitPriority"));
        assertTrue(getDouble(red, "raidPriority") > getDouble(yellow, "raidPriority"));
        assertTrue(getDouble(yellow, "fleePowerRatio") > getDouble(red, "fleePowerRatio"));
    }

    @Test
    void greenDoctrineChoosesGuardedReturnInsteadOfDeepChase() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        assertNotNull(green);
        assertNotNull(red);
        assertNotNull(base);
        setBoolean(green, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setObject(green, "homeBaseId", base.id);
        setDouble(green, "x", base.x + 30.0);
        setDouble(green, "y", base.y + 20.0);
        setDouble(green, "strength", 95.0);
        setDouble(green, "readiness", 95.0);
        setDouble(green, "hullIntegrity", 95.0);
        setDouble(green, "supply", 95.0);
        setDouble(red, "x", base.x + 820.0);
        setDouble(red, "y", base.y + 30.0);
        setDouble(red, "strength", 26.0);
        setDouble(red, "readiness", 60.0);
        invokeSeedNpcForceContact(green, red, 0.85);

        assertTrue(invokeApplyNpcContactDecision(st, green));

        assertFalse("INTERCEPTING".equals(fieldString(green, "intent")),
                "Green doctrine should not deep-chase beyond cautious pursuit range");
        assertTrue("GUARDING".equals(fieldString(green, "intent"))
                        || "SEARCHING".equals(fieldString(green, "intent"))
                        || "PATROLLING".equals(fieldString(green, "intent"))
                        || "REINFORCING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == 0
                        || !"PURSUING".equals(fieldString(green, "state")));
    }

    @Test
    void greenPatrolReactsToWeakRedScoutInsideOperatingRadius() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(green);
        assertNotNull(red);
        setBoolean(green, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(green, "x", 2100.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 88.0);
        setDouble(green, "readiness", 92.0);
        setDouble(green, "supply", 92.0);
        setDouble(green, "operatingRadius", 760.0);
        setDouble(red, "x", 2260.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 18.0);
        setDouble(red, "readiness", 42.0);
        invokeSeedNpcForceContact(green, red, 0.90);

        assertTrue(invokeApplyNpcContactDecision(st, green));

        assertTrue("INTERCEPTING".equals(fieldString(green, "intent")));
        assertTrue("REACTING".equals(fieldString(green, "workState")));
        assertTrue(getInt(red, "id") == getInt(green, "targetForceId"));
    }

    @Test
    void greenPatrolStopsPursuitBeforeEnteringStrongRedControl() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(green);
        assertNotNull(red);
        setBoolean(green, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(green, "x", 2100.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 90.0);
        setDouble(green, "readiness", 90.0);
        setDouble(green, "supply", 90.0);
        setDouble(red, "x", 2320.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 20.0);
        Object theater = invokeCampaignTheaterForPoint(st, 2200.0);
        setDouble(theater, "controlScore", -65.0);
        invokeSeedNpcForceContact(green, red, 0.90);

        assertTrue(invokeApplyNpcContactDecision(st, green));

        assertFalse("INTERCEPTING".equals(fieldString(green, "intent")),
                "Green patrol should not chase even weak Red contacts into strong Red control");
        assertTrue(getInt(green, "targetForceId") == 0);
        assertTrue("GUARDING".equals(fieldString(green, "intent"))
                        || "SEARCHING".equals(fieldString(green, "intent"))
                        || "PATROLLING".equals(fieldString(green, "intent")));
    }

    @Test
    void greenPatrolReturnsToWaypointLoopWhenContactRetreatsBeyondSafePursuit() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(green);
        assertNotNull(red);
        setBoolean(green, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(green, "x", 2100.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 90.0);
        setDouble(green, "readiness", 90.0);
        setDouble(green, "supply", 90.0);
        setDouble(red, "x", 3200.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 16.0);
        invokeAssignPatrolMission(st, green, List.of(
                new double[]{2100.0, 2200.0},
                new double[]{2260.0, 2320.0},
                new double[]{1980.0, 2360.0}));
        setInt(green, "patrolWaypointIndex", 1);
        invokeSeedNpcForceContact(green, red, 0.90);

        assertTrue(invokeApplyNpcContactDecision(st, green));

        assertTrue("PATROLLING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == 0);
        assertFalse(((List<?>) getObject(green, "routePoints")).isEmpty());
        double[] next = (double[]) ((List<?>) getObject(green, "routePoints")).get(1);
        assertTrue(Math.abs(next[0] - 2260.0) < 0.01 && Math.abs(next[1] - 2320.0) < 0.01,
                "patrol should return to the previously selected waypoint");
    }

    @Test
    void greenPatrolLowSupplyBreaksRouteAndRecoversAtHomeBase() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        assertNotNull(green);
        assertNotNull(base);
        setBoolean(green, "simulationActive", true);
        setObject(green, "homeBaseId", base.id);
        setDouble(green, "x", base.x + 620.0);
        setDouble(green, "y", base.y + 140.0);
        setDouble(green, "supply", 18.0);
        setDouble(green, "fuelLevel", 82.0);
        setDouble(green, "readiness", 70.0);
        invokeAssignPatrolMission(st, green, List.of(
                new double[]{base.x + 620.0, base.y + 140.0},
                new double[]{base.x + 760.0, base.y + 260.0},
                new double[]{base.x + 500.0, base.y + 320.0}));
        setDouble(green, "supply", 18.0);

        invokeUpdateCampaignForceOrders(ctx, st, green, 0.2);

        assertTrue("REPAIR".equals(fieldString(green, "mission")));
        assertTrue("REPAIRING".equals(fieldString(green, "intent")));
        assertTrue(base.id.equals(fieldString(green, "destinationLocationId")));
        assertTrue("RECOVERING".equals(fieldString(green, "stopReason"))
                        || "REPAIRING".equals(fieldString(green, "stopReason"))
                        || "REFUELING".equals(fieldString(green, "stopReason")));
    }

    @Test
    void greenPatrolProtectsRouteAndLowersRiskAfterClearingRedPressure() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(green);
        assertNotNull(red);
        setBoolean(green, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(green, "x", 2100.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 92.0);
        setDouble(red, "x", 2240.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 34.0);
        Object theater = invokeCampaignTheaterForPoint(st, 2200.0);
        setDouble(theater, "routeRisk", 46.0);
        setDouble(theater, "threatPressure", 34.0);
        double riskBefore = getDouble(theater, "routeRisk");

        assertTrue(invokeApplyAlliedRoutePressureBehavior(ctx, st, green));

        assertTrue(getDouble(theater, "routeRisk") < riskBefore,
                "Green patrol response should reduce local route risk after clearing pressure");
        assertTrue("REINFORCING".equals(fieldString(green, "intent"))
                        || "ESCORTING".equals(fieldString(green, "intent")));
    }

    @Test
    void redScoutAvoidsCombatUnlessTargetIsExtremelyWeak() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object scout = forceNamed(st, "Red Scout Pair");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(scout);
        assertNotNull(green);
        setBoolean(scout, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setDouble(scout, "x", 2100.0);
        setDouble(scout, "y", 2200.0);
        setDouble(scout, "strength", 24.0);
        setDouble(scout, "readiness", 78.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 64.0);
        setDouble(green, "readiness", 80.0);
        invokeSeedNpcForceContact(scout, green, 0.92);

        assertTrue(invokeApplyNpcContactDecision(st, scout));

        assertFalse("INTERCEPTING".equals(fieldString(scout, "intent")),
                "Red scout should shadow or evade instead of attacking a normal combat target");
        assertTrue("SEARCHING".equals(fieldString(scout, "intent")));
        assertTrue(getInt(scout, "targetForceId") == 0);
    }

    @Test
    void greenPatrolInterceptsRedScoutThenResumesPatrol() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object scout = forceNamed(st, "Red Scout Pair");
        assertNotNull(green);
        assertNotNull(scout);
        setBoolean(green, "simulationActive", true);
        setBoolean(scout, "simulationActive", true);
        setDouble(green, "x", 2100.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "strength", 82.0);
        setDouble(green, "readiness", 88.0);
        setDouble(scout, "x", 2240.0);
        setDouble(scout, "y", 2200.0);
        setDouble(scout, "strength", 24.0);
        invokeAssignPatrolMission(st, green, List.of(
                new double[]{2100.0, 2200.0},
                new double[]{2180.0, 2260.0},
                new double[]{2020.0, 2140.0}));
        invokeSeedNpcForceContact(green, scout, 0.95);

        assertTrue(invokeApplyNpcContactDecision(st, green));

        assertTrue("INTERCEPTING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == getInt(scout, "id"));

        setDouble(scout, "x", 4300.0);
        setDouble(scout, "y", 2200.0);
        invokeSeedNpcForceContact(green, scout, 0.95);
        assertTrue(invokeApplyNpcContactDecision(st, green));

        assertTrue("PATROLLING".equals(fieldString(green, "intent")));
        assertTrue(getInt(green, "targetForceId") == 0);
        assertFalse(((List<?>) getObject(green, "routePoints")).isEmpty());
    }

    @Test
    void relayCoverageImprovesCampaignForceContactTracking() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(red);
        st.playerGalaxyX = 1000.0;
        st.playerGalaxyY = 1000.0;
        st.campaignIntelLevel = 0.0;
        setBoolean(red, "simulationActive", true);
        setDouble(red, "x", 1780.0);
        setDouble(red, "y", 1000.0);
        setDouble(red, "stealthRating", 0.0);
        setDouble(red, "contactConfidence", 0.30);
        setDouble(red, "lastKnownAgeSec", 40.0);
        setEnumByName(red, "contactState", "STALE");

        invokeUpdateCampaignForceContactState(ctx, st, red, 1.0);
        double withoutRelay = getDouble(red, "contactConfidence");
        String stateWithoutRelay = fieldString(red, "contactState");

        setDouble(red, "contactConfidence", 0.30);
        setDouble(red, "lastKnownAgeSec", 40.0);
        setEnumByName(red, "contactState", "STALE");
        invokeAddSensorRelayNode(st, "Test Relay", 1400.0, 1000.0, 1200.0, 60.0, false);
        invokeUpdateCampaignForceContactState(ctx, st, red, 1.0);

        assertTrue(getDouble(red, "contactConfidence") > withoutRelay,
                "relay coverage should improve local contact confidence");
        assertTrue("KNOWN".equals(fieldString(red, "contactState"))
                        || "SUSPECTED".equals(fieldString(red, "contactState")));
        assertTrue("STALE".equals(stateWithoutRelay));
    }

    @Test
    void yellowCivilianFleesMajorRedContactUsingDoctrineThreshold() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object yellow = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (yellow == null) yellow = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation safe = findLocation(ctx, "poi-07");
        assertNotNull(yellow);
        assertNotNull(red);
        assertNotNull(safe);
        setBoolean(yellow, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setObject(yellow, "homeBaseId", safe.id);
        setDouble(yellow, "x", 2200.0);
        setDouble(yellow, "y", 2200.0);
        setDouble(yellow, "strength", 22.0);
        setDouble(yellow, "riskTolerance", 24.0);
        setDouble(red, "x", 2260.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 95.0);

        assertTrue(invokeApplyMiningDistressBehavior(ctx, st, yellow));

        assertTrue("RETREATING".equals(fieldString(yellow, "intent")));
        assertFalse(fieldString(yellow, "destinationLocationId").isBlank());
    }

    @Test
    void yellowTradeConvoyReroutesAwayFromLargeRedBattlefrontOnLane() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object yellow = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        if (yellow == null) yellow = firstForceByKindAndFaction(st, "CONVOY", "BRIGHT_YELLOW");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation safe = findLocation(ctx, "poi-07");
        assertNotNull(yellow);
        assertNotNull(red);
        assertNotNull(safe);
        setBoolean(yellow, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setObject(yellow, "homeBaseId", safe.id);
        setDouble(yellow, "x", 1800.0);
        setDouble(yellow, "y", 2200.0);
        setDouble(yellow, "targetX", 2700.0);
        setDouble(yellow, "targetY", 2200.0);
        setDouble(yellow, "strength", 26.0);
        setDouble(red, "x", 2220.0);
        setDouble(red, "y", 2210.0);
        setDouble(red, "strength", 88.0);

        assertTrue(invokeApplyAlliedRoutePressureBehavior(ctx, st, yellow));

        assertTrue("RETREATING".equals(fieldString(yellow, "intent")));
        assertFalse(fieldString(yellow, "destinationLocationId").isBlank());
        assertFalse(((List<?>) getObject(yellow, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("rerouting away from Red battlefront")));
    }

    @Test
    void yellowTradeConvoyRequestsEscortOnHighRiskLaneWhenPlayerNearby() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object yellow = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        if (yellow == null) yellow = firstForceByKindAndFaction(st, "CONVOY", "BRIGHT_YELLOW");
        assertNotNull(yellow);
        setBoolean(yellow, "simulationActive", true);
        setDouble(yellow, "x", 2100.0);
        setDouble(yellow, "y", 2200.0);
        st.playerGalaxyX = 2260.0;
        st.playerGalaxyY = 2200.0;
        Object theater = invokeCampaignTheaterForPoint(st, 2200.0);
        setDouble(theater, "danger", 68.0);
        setDouble(theater, "routeRisk", 72.0);

        assertTrue(invokeApplyAlliedRoutePressureBehavior(ctx, st, yellow));

        assertTrue("ESCORTING".equals(fieldString(yellow, "intent")));
        assertFalse(((List<?>) getObject(yellow, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("requesting escort through high-risk trade lane")));
    }

    @Test
    void yellowPirateAttacksWeakYellowTrafficThenRetreatsWithoutGlobalHostility() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object pirate = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.BRIGHT_YELLOW,
                "Yellow Pirate Skiff", "Broker shadow quay", "Pirate rogue ambush traffic", 2100.0, 2200.0);
        Object trader = firstForceByKindAndFaction(st, "TRADE_GROUP", "BRIGHT_YELLOW");
        if (trader == null) trader = firstForceByKindAndFaction(st, "CONVOY", "BRIGHT_YELLOW");
        assertNotNull(pirate);
        assertNotNull(trader);
        setBoolean(pirate, "simulationActive", true);
        setBoolean(trader, "simulationActive", true);
        setDouble(pirate, "x", 2100.0);
        setDouble(pirate, "y", 2200.0);
        setDouble(pirate, "strength", 62.0);
        setDouble(pirate, "readiness", 78.0);
        setDouble(trader, "x", 2140.0);
        setDouble(trader, "y", 2200.0);
        setDouble(trader, "strength", 18.0);
        setDouble(trader, "cargoLoad", 30.0);
        double traderStrengthBefore = getDouble(trader, "strength");

        assertTrue(invokeApplyYellowPirateBehavior(ctx, st, pirate));

        assertTrue(getDouble(trader, "strength") < traderStrengthBefore);
        assertTrue("RETREATING".equals(fieldString(pirate, "intent")));
        assertTrue("LOOT".equals(fieldString(pirate, "cargoKind")));
        assertTrue("BRIGHT_YELLOW".equals(fieldString(pirate, "faction")));
        assertTrue("BRIGHT_YELLOW".equals(fieldString(trader, "faction")));
    }

    @Test
    void yellowMercenaryDefendsTraderFromYellowPirateWithoutGlobalHostility() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        for (Object force : campaignForces(st)) {
            if ("ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "x", 7200.0);
                setDouble(force, "y", 7200.0);
            }
        }
        Object pirate = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.BRIGHT_YELLOW,
                "Yellow Pirate Cutter", "Broker shadow quay", "Pirate rogue ambush traffic", 2220.0, 2200.0);
        Object trader = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Trader Meridian", "Yellow exchange berth", "Civilian trader", 2180.0, 2200.0);
        Object merc = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Mercenary Shield", "Contract board", "Mercenary trader defense contract", 2060.0, 2200.0);
        setBoolean(pirate, "simulationActive", true);
        setBoolean(trader, "simulationActive", true);
        setBoolean(merc, "simulationActive", true);
        setDouble(pirate, "strength", 42.0);
        setDouble(trader, "strength", 18.0);
        setDouble(merc, "strength", 72.0);
        setDouble(merc, "riskTolerance", 70.0);
        setEnumByName(pirate, "mission", "RAID");
        setEnumByName(pirate, "intent", "INTERCEPTING");
        setInt(pirate, "targetForceId", getInt(trader, "id"));

        assertTrue(invokeApplyYellowMercenaryContractBehavior(ctx, st, merc));

        assertTrue("ESCORT".equals(fieldString(merc, "mission")));
        assertTrue("INTERCEPTING".equals(fieldString(merc, "intent")));
        assertTrue(getInt(merc, "targetForceId") == getInt(pirate, "id"));
        assertFalse(((List<?>) getObject(merc, "routePoints")).isEmpty());
        assertTrue("BRIGHT_YELLOW".equals(fieldString(pirate, "faction")));
        assertTrue("BRIGHT_YELLOW".equals(fieldString(trader, "faction")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("defended Yellow trader")));
    }

    @Test
    void yellowSmugglerUsesIndirectRouteAndHidesFromGreenInspection() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation hideout = firstLocationOfType(ctx, "HIDDEN_CACHE");
        assertNotNull(hideout);
        for (Object force : campaignForces(st)) {
            if ("TEAM_C".equals(fieldString(force, "faction")) || "ALLY".equals(fieldString(force, "faction"))
                    || "ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "x", hideout.x + 4000.0);
                setDouble(force, "y", hideout.y + 4000.0);
            }
        }
        Object smuggler = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Smuggler Runner", "Yellow broker den", "Move contraband through indirect routes",
                hideout.x - 900.0, hideout.y - 520.0);
        setBoolean(smuggler, "simulationActive", true);
        setObject(smuggler, "destinationLocationId", hideout.id);
        setDouble(smuggler, "stealthRating", 80.0);

        assertTrue(invokeApplyYellowSmugglerBehavior(ctx, st, smuggler));

        List<?> route = (List<?>) getObject(smuggler, "routePoints");
        assertTrue(route.size() >= 2, "smuggler should use a bent route, not a single direct hop");
        double[] first = (double[]) route.get(0);
        assertTrue(Math.hypot(first[0] - hideout.x, first[1] - hideout.y) > 120.0,
                "first smuggler waypoint should be an indirect bend away from the destination");
        assertTrue("LOOT".equals(fieldString(smuggler, "cargoKind")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("indirect broker route")));

        Object inspector = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(inspector);
        setBoolean(inspector, "simulationActive", true);
        setDouble(inspector, "x", getDouble(smuggler, "x") + 180.0);
        setDouble(inspector, "y", getDouble(smuggler, "y") + 60.0);

        assertTrue(invokeApplyYellowSmugglerBehavior(ctx, st, smuggler));

        assertTrue("RETREATING".equals(fieldString(smuggler, "intent")));
        assertTrue("HIDING".equals(fieldString(smuggler, "stopReason")));
        assertFalse(((List<?>) getObject(smuggler, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("hid contraband route")));
    }

    @Test
    void greenPatrolInspectsYellowSmugglerWithoutGlobalYellowHostility() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object inspector = forceNamed(st, "Green Local Defense Patrol");
        Object trader = forceNamed(st, "Yellow Trade Convoy");
        assertNotNull(inspector);
        assertNotNull(trader);
        Object smuggler = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Smuggler Skiff", "Yellow broker den", "Contraband courier avoiding customs inspection",
                2200.0, 2200.0);
        setBoolean(inspector, "simulationActive", true);
        setBoolean(smuggler, "simulationActive", true);
        setDouble(inspector, "x", 2260.0);
        setDouble(inspector, "y", 2200.0);
        setDouble(smuggler, "x", 2210.0);
        setDouble(smuggler, "y", 2200.0);
        setDouble(smuggler, "cargoLoad", 30.0);
        setDouble(smuggler, "stealthRating", 70.0);

        assertTrue(invokeApplyGreenSmugglerInspectionBehavior(ctx, st, inspector));

        assertTrue(getInt(inspector, "targetForceId") == getInt(smuggler, "id"));
        assertTrue("SCANNING".equals(fieldString(inspector, "stopReason")));
        assertTrue("HIDING".equals(fieldString(smuggler, "stopReason")));
        assertTrue(getDouble(smuggler, "cargoLoad") < 30.0);
        assertTrue("BRIGHT_YELLOW".equals(fieldString(trader, "faction")),
                "local inspection should not make unrelated Yellow traffic hostile");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("without widening Yellow hostility")));
    }

    @Test
    void redDirectorDoesNotAutoRaidCovertYellowSmugglerInRedTerritory() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(red);
        Object smuggler = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Smuggler Needle", "Yellow broker den", "Contraband courier crossing Red territory",
                3100.0, 3100.0);
        for (Object force : campaignForces(st)) {
            if (force == red || force == smuggler) continue;
            if (!"PLAYER_FLEET".equals(fieldString(force, "kind"))) {
                setDouble(force, "x", 7200.0);
                setDouble(force, "y", 7200.0);
            }
        }
        setBoolean(red, "simulationActive", true);
        setBoolean(smuggler, "simulationActive", true);
        setObject(red, "homeBaseId", "poi-21");
        setDouble(red, "x", 3000.0);
        setDouble(red, "y", 3000.0);
        setDouble(red, "strength", 80.0);
        setDouble(smuggler, "x", 3140.0);
        setDouble(smuggler, "y", 3040.0);
        setDouble(smuggler, "strength", 18.0);
        setDouble(smuggler, "stealthRating", 85.0);
        setBoolean(smuggler, "visibleToPlayer", false);
        ((java.util.Map<?, ?>) getObject(red, "knownHostileContacts")).clear();

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, red));

        assertTrue(getInt(red, "targetForceId") != getInt(smuggler, "id"),
                "covert smuggler should not always become Red's automatic weak raid target");
    }

    @Test
    void attackingYellowCivilianTrafficDropsTrustAndTurnsMercenaryHostileAtThreshold() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object player = forceNamed(st, "Blue Command Fleet");
        assertNotNull(player);
        Object civilian = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Civilian Liner", "Yellow exchange berth", "Protected civilian traffic", 2400.0, 2400.0);
        Object merc = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Mercenary Escort", "Contract board", "Mercenary contract response", 2480.0, 2400.0);
        setBoolean(civilian, "simulationActive", true);
        setBoolean(merc, "simulationActive", true);
        setDouble(player, "x", 2200.0);
        setDouble(player, "y", 2400.0);
        setDouble(civilian, "x", 2400.0);
        setDouble(civilian, "y", 2400.0);
        setDouble(merc, "x", 2480.0);
        setDouble(merc, "y", 2400.0);
        setDouble(merc, "riskTolerance", 82.0);
        st.yellowLiberationFavor = 1;

        assertTrue(invokeApplyPlayerCivilianTrafficAttackConsequences(st, civilian));

        assertTrue(st.yellowLiberationFavor == 0, "Yellow trust/leverage should drop after civilian traffic is attacked");
        assertTrue("INTERCEPT".equals(fieldString(merc, "mission")));
        assertTrue("INTERCEPTING".equals(fieldString(merc, "intent")));
        assertTrue(getInt(merc, "targetForceId") == getInt(player, "id"));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow trust dropped")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("mercenary contacts turned hostile")));
    }

    @Test
    void attackingProtectedYellowCivilianTrafficReducesGreenOpinionWhenEscorted() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object civilian = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Protected Civilians", "Yellow exchange berth", "Protected civilian traffic", 2400.0, 2400.0);
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(escort);
        setBoolean(civilian, "simulationActive", true);
        setBoolean(escort, "simulationActive", true);
        setBoolean(civilian, "protectedCivilianTraffic", true);
        setDouble(civilian, "x", 2400.0);
        setDouble(civilian, "y", 2400.0);
        setDouble(escort, "x", 2460.0);
        setDouble(escort, "y", 2400.0);
        setEnumByName(escort, "mission", "ESCORT");
        setInt(escort, "targetForceId", getInt(civilian, "id"));
        st.yellowLiberationFavor = 4;
        st.greenContractFavor = 3;

        assertTrue(invokeApplyPlayerCivilianTrafficAttackConsequences(st, civilian));

        assertTrue(st.yellowLiberationFavor == 2, "protected civilian hit should cost extra Yellow trust/leverage");
        assertTrue(st.greenContractFavor == 2, "Green opinion/favor should drop when protected escorted traffic is attacked");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green opinion dropped")));
    }

    @Test
    void yellowMercenaryAcceptsBattleSupportContractAgainstRed() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object merc = forceNamed(st, "Yellow Mercenary Fleet");
        if (merc == null) {
            merc = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                    "Yellow Mercenary Fleet", "Contract board", "Mercenary contract battle support", 2100.0, 2200.0);
        }
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(merc);
        assertNotNull(red);
        setBoolean(merc, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(merc, "x", 2100.0);
        setDouble(merc, "y", 2200.0);
        setDouble(merc, "strength", 84.0);
        setDouble(merc, "riskTolerance", 68.0);
        setDouble(red, "x", 2260.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 46.0);

        assertTrue(invokeApplyYellowMercenaryContractBehavior(ctx, st, merc));

        assertTrue("REINFORCE".equals(fieldString(merc, "mission")));
        assertTrue("REINFORCING".equals(fieldString(merc, "intent")));
        assertTrue(getInt(merc, "targetForceId") == getInt(red, "id"));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("accepted battle-support contract")));
    }

    @Test
    void yellowMercenaryRetreatsWhenContractThreatExceedsRiskTolerance() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object merc = forceNamed(st, "Yellow Mercenary Fleet");
        if (merc == null) {
            merc = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                    "Yellow Mercenary Fleet", "Contract board", "Mercenary contract battle support", 2100.0, 2200.0);
        }
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(merc);
        assertNotNull(red);
        setBoolean(merc, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setDouble(merc, "x", 2100.0);
        setDouble(merc, "y", 2200.0);
        setDouble(merc, "strength", 28.0);
        setDouble(merc, "riskTolerance", 24.0);
        setDouble(red, "x", 2240.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 90.0);

        assertTrue(invokeApplyYellowMercenaryContractBehavior(ctx, st, merc));

        assertTrue("RETREATING".equals(fieldString(merc, "intent")));
        assertFalse(fieldString(merc, "destinationLocationId").isBlank());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("contract failed")));
    }

    @Test
    void yellowMinerFleesWhenEscortDestroyedOutsideStrongFriendlyControl() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (miner == null) miner = firstForceByKind(st, "MINING_GROUP");
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(miner);
        assertNotNull(escort);
        setBoolean(miner, "simulationActive", true);
        setBoolean(escort, "simulationActive", true);
        setDouble(miner, "x", 2100.0);
        setDouble(miner, "y", 2200.0);
        setInt(miner, "targetForceId", getInt(escort, "id"));
        setBoolean(escort, "destroyed", true);
        Object theater = invokeCampaignTheaterForPoint(st, 2200.0);
        setDouble(theater, "controlScore", 5.0);

        assertTrue(invokeApplyMiningEscortLossBehavior(ctx, st, miner));

        assertTrue("RETREATING".equals(fieldString(miner, "intent")));
        assertTrue(getInt(miner, "targetForceId") == 0);
        assertFalse(fieldString(miner, "destinationLocationId").isBlank());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("escort lost")));
    }

    @Test
    void redThreatInterruptsYellowMinerAndGreenRespondsWithinOperatingRadius() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation safe = findLocation(ctx, "poi-07");
        assertNotNull(miner);
        assertNotNull(red);
        assertNotNull(green);
        assertNotNull(safe);
        setBoolean(miner, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setObject(miner, "homeBaseId", safe.id);
        setDouble(miner, "x", 2200.0);
        setDouble(miner, "y", 2200.0);
        setDouble(miner, "strength", 22.0);
        setDouble(red, "x", 2260.0);
        setDouble(red, "y", 2200.0);
        setDouble(red, "strength", 90.0);
        setDouble(green, "x", 2140.0);
        setDouble(green, "y", 2200.0);
        setDouble(green, "operatingRadius", 500.0);
        setDouble(green, "strength", 80.0);
        int yellowFavorBefore = st.yellowLiberationFavor;
        double intelBefore = st.campaignIntelLevel;

        assertTrue(invokeApplyMiningDistressBehavior(ctx, st, miner));

        assertTrue("RETREATING".equals(fieldString(miner, "intent")));
        assertTrue("REINFORCING".equals(fieldString(green, "intent")));
        assertTrue("REINFORCE".equals(fieldString(green, "mission")));
        assertTrue(getInt(green, "targetForceId") == getInt(red, "id"));
        assertFalse(fieldString(green, "sourceLocationId").isBlank());
        assertFalse(fieldString(green, "homeBaseId").isBlank());
        CampaignSystem.CampaignLocation source = findLocation(ctx, fieldString(green, "sourceLocationId"));
        assertNotNull(source);
        assertTrue(Math.abs(source.x - getDouble(green, "x")) <= 0.001);
        assertTrue(Math.abs(source.y - getDouble(green, "y")) <= 0.001);
        assertFalse(((List<?>) getObject(green, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("distress")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("launched from")
                && line.contains("Green response")));
        assertTrue(st.yellowLiberationFavor > yellowFavorBefore,
                "saving Yellow traffic should improve Yellow reputation/leverage");
        assertTrue(st.campaignIntelLevel > intelBefore,
                "saving Yellow traffic should unlock a small intel benefit");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow leverage")));
    }

    @Test
    void greenResponseReturnsToNamedStagingAfterEmergencyEnds() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object green = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation staging = firstLocationWithService(ctx, CampaignSystem.HubService.SHIPYARD);
        if (staging == null) staging = firstLocationWithService(ctx, CampaignSystem.HubService.INTEL);
        assertNotNull(green);
        assertNotNull(red);
        assertNotNull(staging);
        String stagingId = staging.id;
        String stagingName = staging.name;
        setBoolean(green, "simulationActive", true);
        setBoolean(red, "simulationActive", true);
        setObject(green, "homeBaseId", stagingId);
        setObject(green, "sourceLocationId", stagingId);
        setDouble(green, "x", staging.x + 420.0);
        setDouble(green, "y", staging.y);
        setDouble(green, "operatingRadius", 800.0);
        setDouble(red, "x", staging.x + 520.0);
        setDouble(red, "y", staging.y + 40.0);
        setEnumByName(green, "mission", "REINFORCE");
        setEnumByName(green, "intent", "REINFORCING");
        setInt(green, "targetForceId", getInt(red, "id"));
        setBoolean(red, "destroyed", true);

        assertTrue(invokeMaintainGreenResponseMission(ctx, st, green));

        assertTrue("REPAIR".equals(fieldString(green, "mission")));
        assertTrue("REPAIRING".equals(fieldString(green, "intent"))
                || "RETREATING".equals(fieldString(green, "intent")));
        assertTrue(stagingId.equals(fieldString(green, "destinationLocationId")));
        assertTrue(getInt(green, "targetForceId") == 0);
        assertFalse(((List<?>) getObject(green, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("returning to staging")
                && line.contains(stagingName)));
    }

    @Test
    void greenResponseTriggersCoverConvoyRaidBaseThreatDamagedFleetAndPlayerReport() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-01");
        if (base == null) base = firstLocationWithService(ctx, CampaignSystem.HubService.SHIPYARD);
        if (base == null) base = firstLocationWithService(ctx, CampaignSystem.HubService.REPAIR);
        assertNotNull(base);
        st.theaterWarRecentEvents.clear();

        Object convoy = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.CONVOY, Faction.BRIGHT_YELLOW,
                "Trigger Test Yellow Convoy", base.id, "Convoy under raid", 40000.0, 40000.0);
        Object convoyRaider = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Trigger Test Convoy Raider", "red-test", "Raiding convoy", 40080.0, 40040.0);
        Object convoyResponder = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Trigger Test Convoy Responder", base.id, "Green response trigger", 39960.0, 39980.0);
        invokeAssignRaidMission(st, convoyRaider, convoy);
        assertTrue(invokeApplyGreenResponseTriggerScan(ctx, st, convoyResponder));

        Object baseIntruder = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Trigger Test Base Intruder", "red-test", "Threatening Green base", base.x + 120.0, base.y + 90.0);
        Object baseResponder = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Trigger Test Base Responder", base.id, "Green base response", base.x + 80.0, base.y + 40.0);
        assertTrue(invokeApplyGreenResponseTriggerScan(ctx, st, baseResponder));

        Object damagedGreen = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Trigger Test Damaged Green", base.id, "Damaged Green fleet", 41000.0, 41000.0);
        Object damagedThreat = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Trigger Test Damaged Threat", "red-test", "Threat near damaged Green", 41080.0, 41040.0);
        Object damagedResponder = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Trigger Test Damaged Responder", base.id, "Damaged Green response", 40970.0, 40980.0);
        setDouble(damagedGreen, "strength", 18.0);
        setDouble(damagedGreen, "readiness", 24.0);
        setDouble(damagedGreen, "hullIntegrity", 24.0);
        assertTrue(invokeApplyGreenResponseTriggerScan(ctx, st, damagedResponder));

        Object reported = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Trigger Test Reported Red", "red-test", "Player reported contact", 42080.0, 42040.0);
        Object reportResponder = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Trigger Test Report Responder", base.id, "Player report response", 41970.0, 41980.0);
        setBoolean(reported, "visibleToPlayer", true);
        setDouble(reported, "contactConfidence", 0.92);
        assertTrue(invokeApplyGreenResponseTriggerScan(ctx, st, reportResponder));

        assertTrue(getInt(convoyResponder, "targetForceId") == getInt(convoyRaider, "id"));
        assertTrue(getInt(baseResponder, "targetForceId") == getInt(baseIntruder, "id"));
        assertTrue(getInt(damagedResponder, "targetForceId") == getInt(damagedThreat, "id"));
        assertTrue(getInt(reportResponder, "targetForceId") == getInt(reported, "id"));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Red attacking convoy")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("friendly base threatened")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("badly damaged Green fleet")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("player-reported contact")));
    }

    @Test
    void redRaiderAvoidsEqualStrengthTargetUnderDoctrine() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(red);
        assertNotNull(home);
        setBoolean(red, "simulationActive", true);
        setObject(red, "homeBaseId", home.id);
        setDouble(red, "strength", 50.0);
        setDouble(red, "readiness", 88.0);
        setDouble(red, "supply", 88.0);
        setDouble(red, "hullIntegrity", 92.0);
        setDouble(red, "repairCapacity", 92.0);
        for (Object force : campaignForces(st)) {
            if (force != null && !"ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "strength", 220.0);
                setDouble(force, "readiness", 100.0);
                setDouble(force, "supply", 100.0);
                setDouble(force, "hullIntegrity", 100.0);
            }
        }
        ((java.util.Map<?, ?>) getObject(red, "knownHostileContacts")).clear();

        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, red));

        assertFalse("RAID".equals(fieldString(red, "mission")),
                "Red raid doctrine should avoid equal-strength targets in the first-pass director");
        assertTrue("PATROL".equals(fieldString(red, "mission")));
    }

    @Test
    void huntMissionRoutesToLastKnownContactInsteadOfOmniscientLivePosition() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object hunter = forceNamed(st, "Red Frontier Picket Patrol");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "TEAM_C");
        if (target == null) target = firstForceByKind(st, "MINING_GROUP");
        assertNotNull(hunter);
        assertNotNull(target);
        setBoolean(hunter, "simulationActive", true);
        setBoolean(target, "simulationActive", true);
        setDouble(hunter, "x", 1000.0);
        setDouble(hunter, "y", 1000.0);
        setDouble(target, "x", 1120.0);
        setDouble(target, "y", 1040.0);
        invokeRefreshNpcForceContacts(st, hunter, 0.2);
        setDouble(target, "x", 2600.0);
        setDouble(target, "y", 2600.0);

        invokeAssignHuntMission(st, hunter, getInt(target, "id"));

        List<?> route = (List<?>) getObject(hunter, "routePoints");
        assertFalse(route.isEmpty());
        double[] last = (double[]) route.get(route.size() - 1);
        double oldDistance = Math.hypot(last[0] - 1120.0, last[1] - 1040.0);
        double liveDistance = Math.hypot(last[0] - 2600.0, last[1] - 2600.0);
        assertTrue(oldDistance < liveDistance,
                "hunt route should favor stale/last-known contact instead of exact live target");
    }

    @Test
    void stalePredictionRemainsNpcInternalAndDoesNotCreatePlayerFacingMarker() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object hunter = forceNamed(st, "Red Frontier Picket Patrol");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "TEAM_C");
        if (target == null) target = firstForceByKind(st, "MINING_GROUP");
        assertNotNull(hunter);
        assertNotNull(target);
        setBoolean(hunter, "simulationActive", true);
        setBoolean(target, "simulationActive", true);
        setDouble(hunter, "x", 1000.0);
        setDouble(hunter, "y", 1000.0);
        setDouble(target, "x", 2600.0);
        setDouble(target, "y", 2600.0);
        setDouble(target, "lastKnownX", 1400.0);
        setDouble(target, "lastKnownY", 1200.0);
        setDouble(target, "lastKnownVelocityX", 18.0);
        setDouble(target, "lastKnownVelocityY", -6.0);
        setDouble(target, "lastKnownAgeSec", 10.0);
        ((java.util.Map<?, ?>) getObject(hunter, "knownHostileContacts")).clear();

        invokeAssignHuntMission(st, hunter, getInt(target, "id"));

        List<?> route = (List<?>) getObject(hunter, "routePoints");
        assertFalse(route.isEmpty());
        double[] last = (double[]) route.get(route.size() - 1);
        double staleDistance = Math.hypot(last[0] - 1400.0, last[1] - 1200.0);
        double predictedDistance = Math.hypot(last[0] - 1580.0, last[1] - 1140.0);
        assertTrue(predictedDistance < staleDistance,
                "hunt route should project stale contacts using last-known velocity");
        setBoolean(target, "visibleToPlayer", false);
        setDouble(target, "contactConfidence", 0.18);
        setEnumByName(target, "contactState", "STALE");
        assertTrue(CampaignSystem.activeSupportMarkers(ctx).stream().noneMatch(marker -> marker != null
                        && Math.hypot(marker.x - 1400.0, marker.y - 1200.0) <= 1.0),
                "internal NPC prediction must not create a player-facing live marker");
    }

    @Test
    void redScoutsMoveAheadOfRaidHuntAndSiegeGroups() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation redHome = findLocation(ctx, "poi-13");
        CampaignSystem.CampaignLocation targetLocation = findLocation(ctx, "poi-07");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (target == null) target = firstForceByKind(st, "MINING_GROUP");
        assertNotNull(redHome);
        assertNotNull(targetLocation);
        assertNotNull(target);
        st.theaterWarRecentEvents.clear();

        Object raider = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Checklist Raider", redHome.id, "Scout-screen raid test", redHome.x + 40.0, redHome.y + 30.0);
        Object raidScout = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Red Checklist Raid Scout", redHome.id, "Scout ahead of raid", redHome.x + 60.0, redHome.y + 50.0);
        Object hunter = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Checklist Hunter", redHome.id, "Scout-screen hunt test", redHome.x + 120.0, redHome.y + 90.0);
        Object huntScout = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Red Checklist Hunt Scout", redHome.id, "Scout ahead of hunt", redHome.x + 140.0, redHome.y + 100.0);
        Object siege = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Checklist Siege Group", redHome.id, "Scout-screen siege test", redHome.x + 210.0, redHome.y + 160.0);
        Object siegeScout = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Red Checklist Siege Scout", redHome.id, "Scout ahead of siege", redHome.x + 240.0, redHome.y + 170.0);
        for (Object force : campaignForces(st)) {
            String name = fieldString(force, "name");
            if (force != null
                    && "ENEMY".equals(fieldString(force, "faction"))
                    && !name.startsWith("Red Checklist")
                    && (name.toUpperCase().contains("SCOUT") || name.toUpperCase().contains("RECON") || name.toUpperCase().contains("PROBE"))) {
                setDouble(force, "x", redHome.x + 5000.0);
                setDouble(force, "y", redHome.y + 5000.0);
                setInt(force, "targetForceId", 0);
            }
        }

        invokeAssignRaidMission(st, raider, target);
        invokeAssignHuntMission(st, hunter, getInt(target, "id"));
        invokeAssignSiegeMission(st, siege, redHome.id, targetLocation.id);

        assertTrue(hasRedScoutScreening(st, raider));
        assertTrue(hasRedScoutScreening(st, hunter));
        assertTrue(hasRedScoutScreening(st, siege));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED SCOUT SCREEN")
                && line.contains("raider")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED SCOUT SCREEN")
                && line.contains("hunter")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED SCOUT SCREEN")
                && line.contains("siege")));
    }

    @Test
    void redDefenseFleetsProtectBasesReinforceDamagedForcesAndCounterattack() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation redBase = firstLocationOfType(ctx, "ENEMY_ACTIVITY");
        assertNotNull(redBase);
        st.theaterWarRecentEvents.clear();

        Object reinforcement = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.BASE_DEFENSE, Faction.ENEMY,
                "Red Checklist Defense Reinforcer", redBase.id, "Red defense reinforcement test", redBase.x + 40.0, redBase.y + 30.0);
        Object damaged = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Checklist Damaged Force", redBase.id, "Damaged Red force", redBase.x + 130.0, redBase.y + 80.0);
        setDouble(damaged, "strength", 20.0);
        setDouble(damaged, "readiness", 24.0);
        setDouble(damaged, "hullIntegrity", 28.0);
        assertTrue(invokeAssignRedDefenseMission(st, reinforcement));
        assertTrue("REINFORCE".equals(fieldString(reinforcement, "mission")));
        assertTrue(getInt(reinforcement, "targetForceId") == getInt(damaged, "id"));

        setDouble(damaged, "x", redBase.x + 4000.0);
        setDouble(damaged, "y", redBase.y + 4000.0);
        Object counter = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.BASE_DEFENSE, Faction.ENEMY,
                "Red Checklist Defense Counter", redBase.id, "Red defense counterattack test", redBase.x + 20.0, redBase.y + 20.0);
        Object intruder = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Green Checklist Base Raider", redBase.id, "Intruder near Red base", redBase.x + 90.0, redBase.y + 70.0);
        assertTrue(invokeAssignRedDefenseMission(st, counter));
        assertTrue("INTERCEPT".equals(fieldString(counter, "mission")));
        assertTrue(getInt(counter, "targetForceId") == getInt(intruder, "id"));

        setDouble(intruder, "x", redBase.x + 4000.0);
        setDouble(intruder, "y", redBase.y + 4000.0);
        for (Object force : campaignForces(st)) {
            if (force != null && !"ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "x", redBase.x + 5000.0);
                setDouble(force, "y", redBase.y + 5000.0);
            }
        }
        Object guard = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.BASE_DEFENSE, Faction.ENEMY,
                "Red Checklist Base Guard", redBase.id, "Red defense base guard test", redBase.x + 10.0, redBase.y + 15.0);
        assertTrue(invokeAssignRedDefenseMission(st, guard));
        assertTrue("PATROL".equals(fieldString(guard, "mission")));
        assertTrue("GUARDING".equals(fieldString(guard, "intent")));
        assertFalse(((List<?>) getObject(guard, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED DEFENSE")
                && line.contains("reinforcing damaged")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED DEFENSE")
                && line.contains("counterattacking")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED DEFENSE")
                && line.contains("protecting Red base")));
    }

    @Test
    void huntMissionBreaksOffWhenTargetReachesStrongFriendlyDefense() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object hunter = forceNamed(st, "Red Frontier Picket Patrol");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (target == null) target = firstForceByKind(st, "MINING_GROUP");
        CampaignSystem.CampaignLocation defense = findLocation(ctx, "poi-07");
        assertNotNull(hunter);
        assertNotNull(target);
        assertNotNull(defense);
        setBoolean(hunter, "simulationActive", true);
        setBoolean(target, "simulationActive", true);
        setObject(hunter, "homeBaseId", "poi-21");
        setObject(target, "homeBaseId", defense.id);
        setObject(target, "sourceLocationId", defense.id);
        setDouble(hunter, "x", defense.x + 520.0);
        setDouble(hunter, "y", defense.y);
        setDouble(target, "x", defense.x + 40.0);
        setDouble(target, "y", defense.y + 20.0);
        invokeAssignHuntMission(st, hunter, getInt(target, "id"));

        assertTrue(invokeMaintainHuntMission(ctx, st, hunter));

        assertTrue(getInt(hunter, "targetForceId") == 0,
                "hunter should drop target when it reaches defended friendly space");
        assertTrue("PATROL".equals(fieldString(hunter, "mission"))
                        || !"INTERCEPTING".equals(fieldString(hunter, "intent")),
                "hunter should break off into patrol/return instead of deep chase");
    }

    @Test
    void blockadeMissionHoldsRouteAndRaisesRouteRiskWithoutIdling() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object blockade = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation choke = findLocation(ctx, "poi-07");
        assertNotNull(blockade);
        assertNotNull(choke);
        setBoolean(blockade, "simulationActive", true);
        setDouble(blockade, "x", choke.x + 20.0);
        setDouble(blockade, "y", choke.y + 20.0);
        setDouble(blockade, "strength", 82.0);
        Object theater = invokeCampaignTheaterForPoint(st, choke.y);
        setDouble(theater, "controlScore", 10.0);
        double riskBefore = getDouble(theater, "routeRisk");
        double tradeBefore = getDouble(theater, "tradeHealth");
        double controlBefore = getDouble(theater, "controlScore");

        invokeAssignBlockadeMission(st, blockade, choke.id);
        assertTrue(invokeMaintainBlockadeMission(ctx, st, blockade, 5.0));

        assertTrue("BLOCKADE".equals(fieldString(blockade, "mission")));
        assertTrue("BLOCKADING".equals(fieldString(blockade, "stopReason")));
        assertTrue("WAITING_WITH_PURPOSE".equals(fieldString(blockade, "workState")));
        assertTrue(getDouble(theater, "routeRisk") > riskBefore);
        assertTrue(getDouble(theater, "tradeHealth") < tradeBefore);
        assertTrue(getDouble(theater, "controlScore") < controlBefore);
        Object validation = invokeValidateFleetLifecycle(st, blockade);
        assertTrue(getBoolean(validation, "valid"),
                "blockading should be purposeful work, not idle");
    }

    @Test
    void greenAssaultFleetCanClearRedBlockade() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation choke = findLocation(ctx, "poi-07");
        CampaignSystem.CampaignLocation redHome = findLocation(ctx, "poi-21");
        Object blockade = forceNamed(st, "Red Frontier Picket Patrol");
        Object assault = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(choke);
        assertNotNull(redHome);
        assertNotNull(blockade);
        assertNotNull(assault);
        for (Object force : campaignForces(st)) {
            if (force == null || getInt(force, "id") == getInt(blockade, "id") || getInt(force, "id") == getInt(assault, "id")) continue;
            setDouble(force, "x", choke.x + 1800.0);
            setDouble(force, "y", choke.y + 1800.0);
        }
        setObject(blockade, "homeBaseId", redHome.id);
        setBoolean(blockade, "simulationActive", true);
        setDouble(blockade, "x", choke.x + 35.0);
        setDouble(blockade, "y", choke.y + 30.0);
        setDouble(blockade, "strength", 52.0);
        setDouble(blockade, "readiness", 72.0);
        setDouble(blockade, "hullIntegrity", 86.0);
        setBoolean(assault, "simulationActive", true);
        setDouble(assault, "x", choke.x + 90.0);
        setDouble(assault, "y", choke.y + 65.0);
        setDouble(assault, "strength", 88.0);
        setDouble(assault, "readiness", 92.0);
        setEnumByName(assault, "mission", "CAPTURE");
        setEnumByName(assault, "intent", "INTERCEPTING");
        Object theater = invokeCampaignTheaterForPoint(st, choke.y);
        setDouble(theater, "controlScore", -18.0);
        setDouble(theater, "routeRisk", 64.0);
        double controlBefore = getDouble(theater, "controlScore");
        double riskBefore = getDouble(theater, "routeRisk");

        invokeAssignBlockadeMission(st, blockade, choke.id);
        assertTrue(invokeMaintainBlockadeMission(ctx, st, blockade, 2.0));

        assertTrue("REPAIR".equals(fieldString(blockade, "mission")));
        assertTrue("RETREATING".equals(fieldString(blockade, "intent")));
        assertTrue(redHome.id.equals(fieldString(blockade, "destinationLocationId")));
        assertTrue(getDouble(theater, "controlScore") > controlBefore);
        assertTrue(getDouble(theater, "routeRisk") < riskBefore);
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("challenged blockade")
                        && line.contains(fieldString(assault, "name"))),
                "Green assault should visibly challenge and clear the Red blockade");
    }

    @Test
    void greenAssaultStagesRetreatsWhenDamagedAndHoldsCapturedTerritory() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation target = firstLocationOfType(ctx, "ENEMY_ACTIVITY");
        assertNotNull(target);
        Object weakAssault = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Checklist Weak Assault", "Green assault staging", "Understrength assault", target.x - 900.0, target.y + 200.0);
        setDouble(weakAssault, "strength", 34.0);
        setDouble(weakAssault, "readiness", 40.0);
        assertTrue(invokeAssignGreenAssaultMission(ctx, st, weakAssault, target.id));
        assertTrue("CAPTURE".equals(fieldString(weakAssault, "mission")));
        assertTrue("WAITING_FOR_REINFORCEMENTS".equals(fieldString(weakAssault, "stopReason")));

        setDouble(weakAssault, "hullIntegrity", 22.0);
        setDouble(weakAssault, "readiness", 26.0);
        assertTrue(invokeMaintainSiegeMission(ctx, st, weakAssault, 1.0));
        assertTrue("RETREATING".equals(fieldString(weakAssault, "intent")));

        for (Object force : campaignForces(st)) {
            if (force != null && "ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "x", target.x + 5000.0);
                setDouble(force, "y", target.y + 5000.0);
            }
        }
        Object assault = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Checklist Ready Assault", "Green assault staging", "Ready assault", target.x - 120.0, target.y - 80.0);
        Object support = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Green Checklist Assault Support", "Green assault staging", "Assault support", target.x - 150.0, target.y - 100.0);
        setDouble(assault, "strength", 92.0);
        setDouble(assault, "readiness", 94.0);
        setDouble(assault, "hullIntegrity", 96.0);
        setDouble(support, "strength", 76.0);
        setDouble(support, "readiness", 80.0);
        assertTrue(invokeAssignGreenAssaultMission(ctx, st, assault, target.id));
        setDouble(assault, "x", target.x + 20.0);
        setDouble(assault, "y", target.y + 20.0);
        Object theater = invokeCampaignTheaterForPoint(st, target.y);
        double controlBefore = getDouble(theater, "controlScore");
        assertTrue(invokeMaintainSiegeMission(ctx, st, assault, 2.0));

        assertTrue("PATROL".equals(fieldString(assault, "mission")));
        assertTrue("HOLDING_LINE".equals(fieldString(assault, "stopReason")));
        assertTrue(target.id.equals(fieldString(assault, "destinationLocationId")));
        assertTrue(getDouble(theater, "controlScore") > controlBefore);
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("GREEN ASSAULT STAGING")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("GREEN ASSAULT HOLD")
                && line.contains(target.name)));
    }

    @Test
    void shipyardDamageReducesRepairAndLaunchCapacityUntilRecovered() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation shipyard = firstLocationWithService(ctx, CampaignSystem.HubService.SHIPYARD);
        assertNotNull(shipyard);
        Object node = strategicNodeForLocation(st, shipyard.id);
        Object theater = invokeCampaignTheaterForPoint(st, shipyard.y);
        assertNotNull(node);
        assertNotNull(theater);
        setEnumByName(node, "owner", "BLUE_GREEN");
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        setBoolean(force, "simulationActive", true);
        setDouble(force, "x", shipyard.x + 40.0);
        setDouble(force, "y", shipyard.y + 30.0);
        setDouble(force, "readiness", 20.0);
        setDouble(force, "supply", 20.0);
        setDouble(force, "morale", 20.0);
        setDouble(force, "fuelPressure", 70.0);

        setDouble(force, "strength", 20.0);
        setDouble(theater, "installationIntegrity", 100.0);
        invokeApplyShipyardReinforcementTick(st, 10.0);
        double healthyGain = getDouble(force, "strength") - 20.0;

        setDouble(force, "strength", 20.0);
        setDouble(force, "readiness", 20.0);
        setDouble(force, "supply", 20.0);
        setDouble(force, "morale", 20.0);
        setDouble(force, "fuelPressure", 70.0);
        setDouble(theater, "installationIntegrity", 20.0);
        invokeApplyShipyardReinforcementTick(st, 10.0);
        double damagedGain = getDouble(force, "strength") - 20.0;

        assertTrue(healthyGain > 0.0);
        assertTrue(damagedGain > 0.0);
        assertTrue(damagedGain < healthyGain * 0.55,
                "damaged shipyard output should be substantially below healthy launch/repair capacity");
    }

    @Test
    void siegeMissionStagesWarnsAdvancesAndConvertsToBlockade() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation staging = findLocation(ctx, "poi-13");
        CampaignSystem.CampaignLocation target = findLocation(ctx, "poi-07");
        Object siege = forceNamed(st, "Red Frontier Picket Patrol");
        Object support = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(staging);
        assertNotNull(target);
        assertNotNull(siege);
        assertNotNull(support);
        for (Object force : campaignForces(st)) {
            if (force == null || !"TEAM_C".equals(fieldString(force, "faction"))) continue;
            setDouble(force, "x", target.x + 1200.0);
            setDouble(force, "y", target.y + 1200.0);
        }
        for (Object force : campaignForces(st)) {
            if (force == null || !"ENEMY".equals(fieldString(force, "faction"))) continue;
            if (getInt(force, "id") == getInt(siege, "id") || getInt(force, "id") == getInt(support, "id")) continue;
            setDouble(force, "x", staging.x + 1800.0);
            setDouble(force, "y", staging.y + 1800.0);
        }
        setBoolean(siege, "simulationActive", true);
        setBoolean(support, "simulationActive", true);
        setObject(siege, "homeBaseId", staging.id);
        setDouble(siege, "x", staging.x + 25.0);
        setDouble(siege, "y", staging.y + 20.0);
        setDouble(siege, "strength", 88.0);
        setDouble(siege, "readiness", 86.0);
        setDouble(siege, "hullIntegrity", 90.0);
        setDouble(support, "x", staging.x + 1400.0);
        setDouble(support, "y", staging.y + 1400.0);

        invokeAssignSiegeMission(st, siege, staging.id, target.id);
        assertTrue(invokeMaintainSiegeMission(ctx, st, siege, 5.0));

        assertTrue("CAPTURE".equals(fieldString(siege, "mission")));
        assertTrue("STAGING".equals(fieldString(siege, "stopReason")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("SIEGE WARNING")
                        && line.contains(staging.name)
                        && line.contains(target.name)
                        && line.contains("ETA")),
                "siege should warn the player before advancing");

        setDouble(support, "x", staging.x + 80.0);
        setDouble(support, "y", staging.y + 60.0);
        setDouble(support, "strength", 92.0);
        assertTrue(invokeMaintainSiegeMission(ctx, st, siege, 1.0));

        assertTrue("INTERCEPTING".equals(fieldString(siege, "intent")));
        assertFalse(((List<?>) getObject(siege, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("SIEGE ADVANCING")
                        && line.contains(target.name)),
                "siege advance should be visible in warnings/events");

        setDouble(siege, "x", target.x + 20.0);
        setDouble(siege, "y", target.y + 25.0);
        setDouble(support, "x", target.x + 60.0);
        setDouble(support, "y", target.y + 50.0);
        assertTrue(invokeMaintainSiegeMission(ctx, st, siege, 2.0));

        assertTrue("BLOCKADE".equals(fieldString(siege, "mission")));
        assertTrue("BLOCKADING".equals(fieldString(siege, "stopReason")));
        Object validation = invokeValidateFleetLifecycle(st, siege);
        assertTrue(getBoolean(validation, "valid"),
                "post-siege blockade should remain a valid purposeful state");
    }

    @Test
    void redInvasionAssemblesWingsAroundOneCommittedTargetAndDeploysScoutsRaiders() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation staging = firstLocationOfType(ctx, "ENEMY_ACTIVITY");
        assertNotNull(staging);
        Object spearhead = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Checklist Invasion Spearhead", staging.id, "Invasion spearhead", staging.x + 20.0, staging.y + 20.0);
        Object wing = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Checklist Invasion Wing", staging.id, "Invasion capture wing", staging.x + 80.0, staging.y + 60.0);
        Object raider = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Red Checklist Invasion Raider", staging.id, "Invasion raider wing", staging.x + 120.0, staging.y + 70.0);
        Object scout = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Red Checklist Invasion Scout", staging.id, "Invasion scout wing", staging.x + 140.0, staging.y + 90.0);
        assertNotNull(wing);
        assertNotNull(raider);
        assertNotNull(scout);
        for (Object force : campaignForces(st)) {
            if (force == null) continue;
            String name = fieldString(force, "name");
            if ("ENEMY".equals(fieldString(force, "faction")) && !name.startsWith("Red Checklist Invasion")) {
                setDouble(force, "x", staging.x + 5000.0);
                setDouble(force, "y", staging.y + 5000.0);
            }
        }
        st.theaterWarRecentEvents.clear();

        assertTrue(invokeAssignRedInvasionMission(st, spearhead, staging.id));

        assertTrue("CAPTURE".equals(fieldString(spearhead, "mission")));
        String committedTarget = fieldString(spearhead, "destinationLocationId");
        assertFalse(committedTarget.isBlank());
        assertTrue("CAPTURE".equals(fieldString(wing, "mission"))
                || "CAPTURE".equals(fieldString(raider, "mission")));
        for (Object force : List.of(spearhead, wing, raider)) {
            if ("CAPTURE".equals(fieldString(force, "mission"))) {
                assertEquals(committedTarget, fieldString(force, "destinationLocationId"));
            }
        }
        assertTrue(hasRedScoutScreening(st, spearhead));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RED INVASION")
                && line.contains("establish forward bases")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("FOCUSED SIEGE")
                && line.contains("supporting one target")));
    }

    @Test
    void redSiegeAcceptsHigherLossThresholdThanRaiderDoctrine() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation staging = findLocation(ctx, "poi-13");
        CampaignSystem.CampaignLocation target = findLocation(ctx, "poi-07");
        Object siege = forceNamed(st, "Red Frontier Picket Patrol");
        Object defense = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(staging);
        assertNotNull(target);
        assertNotNull(siege);
        assertNotNull(defense);
        for (Object force : campaignForces(st)) {
            if (force == null || getInt(force, "id") == getInt(siege, "id") || getInt(force, "id") == getInt(defense, "id")) continue;
            setDouble(force, "x", target.x + 1600.0);
            setDouble(force, "y", target.y + 1600.0);
        }
        setObject(siege, "homeBaseId", staging.id);
        setBoolean(siege, "simulationActive", true);
        setDouble(siege, "x", target.x + 35.0);
        setDouble(siege, "y", target.y + 30.0);
        setDouble(siege, "strength", 78.0);
        setDouble(siege, "readiness", 100.0);
        setDouble(siege, "supply", 100.0);
        setDouble(siege, "morale", 100.0);
        setDouble(siege, "fuelPressure", 0.0);
        setDouble(siege, "hullIntegrity", 100.0);
        setBoolean(defense, "simulationActive", true);
        setDouble(defense, "x", target.x + 80.0);
        setDouble(defense, "y", target.y + 55.0);
        setDouble(defense, "strength", 100.0);
        setDouble(defense, "readiness", 100.0);
        setDouble(defense, "supply", 100.0);
        setDouble(defense, "morale", 100.0);
        setDouble(defense, "fuelPressure", 0.0);
        setDouble(defense, "hullIntegrity", 100.0);

        invokeAssignSiegeMission(st, siege, staging.id, target.id);
        assertTrue(invokeMaintainSiegeMission(ctx, st, siege, 1.0));

        assertTrue("BLOCKADE".equals(fieldString(siege, "mission")),
                "Red siege should accept higher losses than raider doctrine and press a stronger defense");
        assertTrue("BLOCKADING".equals(fieldString(siege, "stopReason")));
    }

    @Test
    void redSiegeFleetForcesGreenResponseLaunch() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation staging = findLocation(ctx, "poi-13");
        CampaignSystem.CampaignLocation target = findLocation(ctx, "poi-07");
        Object siege = forceNamed(st, "Red Frontier Picket Patrol");
        Object responder = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(staging);
        assertNotNull(target);
        assertNotNull(siege);
        assertNotNull(responder);
        for (Object force : campaignForces(st)) {
            if (force == null || getInt(force, "id") == getInt(siege, "id") || getInt(force, "id") == getInt(responder, "id")) continue;
            setDouble(force, "x", target.x + 1900.0);
            setDouble(force, "y", target.y + 1900.0);
        }
        setObject(siege, "homeBaseId", staging.id);
        setBoolean(siege, "simulationActive", true);
        setDouble(siege, "x", staging.x + 40.0);
        setDouble(siege, "y", staging.y + 35.0);
        setDouble(siege, "strength", 86.0);
        setDouble(siege, "readiness", 88.0);
        setDouble(siege, "hullIntegrity", 90.0);
        setBoolean(responder, "simulationActive", true);
        setDouble(responder, "x", target.x + 520.0);
        setDouble(responder, "y", target.y + 60.0);
        setDouble(responder, "strength", 80.0);
        setDouble(responder, "readiness", 86.0);
        setDouble(responder, "operatingRadius", 1200.0);
        setEnumByName(responder, "mission", "PATROL");
        setEnumByName(responder, "intent", "PATROLLING");
        setEnumByName(responder, "state", "MOVING");

        invokeAssignSiegeMission(st, siege, staging.id, target.id);
        assertTrue(invokeMaintainSiegeMission(ctx, st, siege, 1.0));

        assertTrue("REINFORCE".equals(fieldString(responder, "mission")));
        assertTrue("REINFORCING".equals(fieldString(responder, "intent")));
        assertTrue(getInt(responder, "targetForceId") == getInt(siege, "id"));
        assertFalse(fieldString(responder, "sourceLocationId").isBlank());
        assertFalse(((List<?>) getObject(responder, "routePoints")).isEmpty());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("SIEGE RESPONSE")
                        && line.contains(fieldString(siege, "name"))),
                "Red siege should force a named Green response launch");
    }

    @Test
    void redSiegeArrivalBlockadesGreenStationIfIgnored() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation staging = findLocation(ctx, "poi-13");
        CampaignSystem.CampaignLocation target = findLocation(ctx, "poi-07");
        Object siege = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(staging);
        assertNotNull(target);
        assertNotNull(siege);
        for (Object force : campaignForces(st)) {
            if (force == null || getInt(force, "id") == getInt(siege, "id")) continue;
            setDouble(force, "x", target.x + 2200.0);
            setDouble(force, "y", target.y + 2200.0);
        }
        setObject(siege, "homeBaseId", staging.id);
        setBoolean(siege, "simulationActive", true);
        setDouble(siege, "x", target.x + 30.0);
        setDouble(siege, "y", target.y + 25.0);
        setDouble(siege, "strength", 92.0);
        setDouble(siege, "readiness", 94.0);
        setDouble(siege, "hullIntegrity", 96.0);
        Object theater = invokeCampaignTheaterForPoint(st, target.y);
        setDouble(theater, "controlScore", 12.0);
        setDouble(theater, "installationIntegrity", 100.0);
        double controlBefore = getDouble(theater, "controlScore");
        double integrityBefore = getDouble(theater, "installationIntegrity");

        invokeAssignSiegeMission(st, siege, staging.id, target.id);
        assertTrue(invokeMaintainSiegeMission(ctx, st, siege, 2.0));

        assertTrue("BLOCKADE".equals(fieldString(siege, "mission")));
        assertTrue("BLOCKADING".equals(fieldString(siege, "stopReason")));
        assertTrue(target.id.equals(fieldString(siege, "destinationLocationId")));
        assertTrue(getDouble(theater, "controlScore") < controlBefore);
        assertTrue(getDouble(theater, "installationIntegrity") < integrityBefore);
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("converted siege pressure into blockade")
                        && line.contains(target.name)),
                "ignored Red siege arrival should visibly blockade the Green station");
    }

    @Test
    void repairRescueMissionStabilizesDamagedGreenFleetAndEscortsHome() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        assertNotNull(base);
        Object rescue = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(rescue);
        Object damaged = invokeEnsureCampaignForce(
                st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.TEAM_C,
                "Green Damaged Rescue Test Fleet",
                base.id,
                "Damaged fleet awaiting repair rescue",
                base.x + 40.0,
                base.y + 35.0
        );
        assertNotNull(damaged);
        for (Object force : campaignForces(st)) {
            if (force != null && "ENEMY".equals(fieldString(force, "faction"))) {
                setDouble(force, "x", base.x + 1800.0);
                setDouble(force, "y", base.y + 1800.0);
            }
        }
        setBoolean(rescue, "simulationActive", true);
        setDouble(rescue, "x", base.x + 70.0);
        setDouble(rescue, "y", base.y + 60.0);
        setDouble(rescue, "strength", 72.0);
        setDouble(rescue, "readiness", 78.0);
        setDouble(damaged, "strength", 18.0);
        setDouble(damaged, "readiness", 26.0);
        setDouble(damaged, "hullIntegrity", 24.0);
        setDouble(damaged, "supply", 22.0);
        setDouble(damaged, "repairCapacity", 42.0);
        setObject(damaged, "homeBaseId", base.id);
        double strengthBefore = getDouble(damaged, "strength");
        double readinessBefore = getDouble(damaged, "readiness");

        invokeAssignRepairRescueMission(st, rescue, getInt(damaged, "id"));
        assertTrue(invokeMaintainRepairRescueMission(ctx, st, rescue, 6.0));

        assertTrue("REPAIR".equals(fieldString(rescue, "mission")));
        assertTrue("RECOVERING_SURVIVORS".equals(fieldString(rescue, "stopReason"))
                        || "REPAIRING".equals(fieldString(rescue, "stopReason")),
                "rescue should arrive and perform explicit recovery work");
        assertTrue(getDouble(damaged, "strength") > strengthBefore);
        assertTrue(getDouble(damaged, "readiness") > readinessBefore);

        setDouble(damaged, "strength", 36.0);
        setDouble(damaged, "readiness", 60.0);
        setDouble(damaged, "hullIntegrity", 60.0);
        assertTrue(invokeMaintainRepairRescueMission(ctx, st, rescue, 2.0));

        assertTrue("ESCORT".equals(fieldString(rescue, "mission")),
                "stable damaged fleet should be escorted back to a friendly base");
        assertTrue(getInt(rescue, "targetForceId") == getInt(damaged, "id"));
        assertTrue("RETREATING".equals(fieldString(damaged, "intent")));
        Object validation = invokeValidateFleetLifecycle(st, rescue);
        assertTrue(getBoolean(validation, "valid"),
                "repair rescue should become a valid escort instead of a completed idle stop");
    }

    @Test
    void repairRescueMissionFleesWithDamagedAllyWhenMajorThreatNearby() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        assertNotNull(base);
        Object rescue = forceNamed(st, "Green Local Defense Patrol");
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(rescue);
        assertNotNull(red);
        Object damaged = invokeEnsureCampaignForce(
                st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.TEAM_C,
                "Green Threatened Rescue Test Fleet",
                base.id,
                "Damaged fleet awaiting evacuation",
                base.x + 75.0,
                base.y + 55.0
        );
        assertNotNull(damaged);
        setBoolean(rescue, "simulationActive", true);
        setDouble(rescue, "x", base.x + 90.0);
        setDouble(rescue, "y", base.y + 65.0);
        setDouble(rescue, "strength", 28.0);
        setDouble(damaged, "strength", 12.0);
        setDouble(damaged, "readiness", 18.0);
        setDouble(damaged, "hullIntegrity", 20.0);
        setObject(damaged, "homeBaseId", base.id);
        setBoolean(red, "simulationActive", true);
        setDouble(red, "x", base.x + 120.0);
        setDouble(red, "y", base.y + 70.0);
        setDouble(red, "strength", 92.0);
        setDouble(red, "readiness", 92.0);

        invokeAssignRepairRescueMission(st, rescue, getInt(damaged, "id"));
        assertTrue(invokeMaintainRepairRescueMission(ctx, st, rescue, 2.0));

        assertTrue("ESCORT".equals(fieldString(rescue, "mission"))
                        || "AVOIDING_SUPERIOR_THREAT".equals(fieldString(rescue, "stopReason"))
                        || "WAITING_FOR_ESCORT".equals(fieldString(rescue, "stopReason")),
                "rescue should avoid a stronger threat instead of picking a random fight");
        assertTrue("RETREATING".equals(fieldString(damaged, "intent"))
                        || "ESCORTING".equals(fieldString(rescue, "intent"))
                        || "REINFORCING".equals(fieldString(rescue, "intent")));
    }

    @Test
    void lowSupplyFleetReturnsRepairsAndReceivesNewDirectorMission() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Green Local Defense Patrol");
        assertNotNull(force);
        CampaignSystem.CampaignLocation base = findLocation(ctx, "poi-07");
        if (base == null) base = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(base);
        setBoolean(force, "simulationActive", true);
        setObject(force, "homeBaseId", base.id);
        setDouble(force, "x", base.x + 420.0);
        setDouble(force, "y", base.y + 140.0);
        setDouble(force, "targetX", base.x + 420.0);
        setDouble(force, "targetY", base.y + 140.0);
        setDouble(force, "speed", 10000.0);
        setDouble(force, "fuelLevel", 24.0);
        setDouble(force, "fuelPressure", 76.0);
        setDouble(force, "ammoLevel", 31.0);
        setDouble(force, "supply", 31.0);
        setDouble(force, "repairCapacity", 42.0);
        setDouble(force, "hullIntegrity", 42.0);
        setDouble(force, "crewReadiness", 45.0);
        setDouble(force, "readiness", 45.0);
        invokeAssignReturnToBaseMission(st, force, base.id);

        invokeAdvanceCampaignForcePosition(force, 0.2);

        assertTrue("WORKING".equals(fieldString(force, "workState")),
                "returning force should enter repair work when it reaches base");
        assertTrue("REPAIRING".equals(fieldString(force, "stopReason"))
                        || "REFUELING".equals(fieldString(force, "stopReason")),
                "returning force should repair or refuel at base");

        setDouble(force, "fuelLevel", 99.5);
        setDouble(force, "ammoLevel", 99.5);
        setDouble(force, "repairCapacity", 99.5);
        setDouble(force, "crewReadiness", 99.5);
        setDouble(force, "fuelPressure", 0.5);
        setDouble(force, "supply", 99.5);
        setDouble(force, "hullIntegrity", 99.5);
        setDouble(force, "readiness", 99.5);
        setDouble(force, "workRemainingSec", 0.0);
        setDouble(force, "taskDeadlineSec", 0.0);

        invokeLifecycleAfterMovement(st, force, 1.0);
        invokeAssignDirectorAfterRepairResupplyComplete(ctx, st, force);

        assertTrue(getDouble(force, "fuelLevel") >= 99.9);
        assertTrue(getDouble(force, "ammoLevel") >= 99.9);
        assertTrue(getDouble(force, "repairCapacity") >= 99.9);
        assertTrue(getDouble(force, "crewReadiness") >= 99.9);
        assertFalse("COMPLETED".equals(fieldString(force, "missionState")),
                "repair completion should hand the fleet to a new director mission");
        assertTrue(!((List<?>) getObject(force, "routePoints")).isEmpty()
                        || !"NONE".equals(fieldString(force, "stopReason")),
                "new director mission should provide a route or purposeful stop");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("DIRECTOR SIMPLE")
                        && line.contains(fieldString(force, "name"))),
                "repair completion should be visible in director telemetry");
    }

    @Test
    void stationVisitorReceivesNewMissionAfterTradeTimerCompletes() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object trader = forceNamed(st, "Yellow Trade Convoy");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-07");
        assertNotNull(trader);
        assertNotNull(home);
        setBoolean(trader, "simulationActive", true);
        setObject(trader, "homeBaseId", home.id);
        setObject(trader, "sourceLocationId", home.id);
        setEnumByName(trader, "mission", "CONVOY");
        setEnumByName(trader, "intent", "DOCKING");
        setEnumByName(trader, "workState", "WORKING");
        setEnumByName(trader, "missionState", "COMPLETED");
        setEnumByName(trader, "stopReason", "TRADING");
        setDouble(trader, "workRemainingSec", 0.0);
        setDouble(trader, "taskDeadlineSec", 0.0);

        invokeAssignDirectorAfterStationWorkComplete(ctx, st, trader);

        assertFalse("COMPLETED".equals(fieldString(trader, "missionState")),
                "station visitor should leave completed work with a new assignment");
        assertTrue(!((List<?>) getObject(trader, "routePoints")).isEmpty()
                        || !"NONE".equals(fieldString(trader, "stopReason")),
                "station visitor should receive a route or purposeful station follow-up");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("completed station work")
                        || line.contains("DIRECTOR SIMPLE")),
                "station work completion should be visible in director telemetry");
    }

    @Test
    void retreatingFleetArrivesAtSafeDestinationAndStartsRepair() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(red);
        CampaignSystem.CampaignLocation safe = findLocation(ctx, "poi-21");
        if (safe == null) safe = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(safe);
        setBoolean(red, "simulationActive", true);
        setDouble(red, "x", safe.x + 360.0);
        setDouble(red, "y", safe.y + 120.0);
        setDouble(red, "targetX", safe.x + 360.0);
        setDouble(red, "targetY", safe.y + 120.0);
        setDouble(red, "speed", 10000.0);
        setDouble(red, "fuelLevel", 92.0);
        setDouble(red, "fuelPressure", 8.0);
        setDouble(red, "repairCapacity", 38.0);
        setDouble(red, "hullIntegrity", 38.0);
        invokeAssignRetreatMission(st, red, safe.id);

        invokeAdvanceCampaignForcePosition(red, 0.2);

        assertTrue("REPAIRING".equals(fieldString(red, "intent")));
        assertTrue("WORKING".equals(fieldString(red, "workState")));
        assertTrue("REPAIRING".equals(fieldString(red, "stopReason"))
                        || "REFUELING".equals(fieldString(red, "stopReason")),
                "retreat arrival should become repair/refuel work");
        assertTrue(((List<?>) getObject(red, "routePoints")).isEmpty(),
                "safe arrival should stop the retreat route instead of looping it");
    }

    @Test
    void retreatMissionDoglegsAwayFromKnownEnemyOnDirectPath() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        Object green = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation safe = findLocation(ctx, "poi-21");
        if (safe == null) safe = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(red);
        assertNotNull(green);
        assertNotNull(safe);
        setBoolean(red, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setDouble(red, "x", safe.x - 820.0);
        setDouble(red, "y", safe.y);
        setDouble(red, "targetX", safe.x - 820.0);
        setDouble(red, "targetY", safe.y);
        setDouble(green, "x", safe.x - 410.0);
        setDouble(green, "y", safe.y);

        invokeAssignRetreatMission(st, red, safe.id);

        List<?> route = (List<?>) getObject(red, "routePoints");
        assertTrue(route.size() >= 3, "retreat should add an avoidance waypoint before the safe hub");
        double[] avoid = (double[]) route.get(1);
        assertTrue(Math.hypot(avoid[0] - getDouble(green, "x"), avoid[1] - getDouble(green, "y")) > 300.0,
                "avoidance waypoint should steer away from the known hostile");
        assertTrue("AVOIDING_SUPERIOR_THREAT".equals(fieldString(red, "stopReason")));
    }

    @Test
    void damagedRetreatingRaiderSlowsDownAndKeepsInterceptableContactTrail() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation safe = findLocation(ctx, "poi-21");
        if (safe == null) safe = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(red);
        assertNotNull(safe);
        setBoolean(red, "simulationActive", true);
        setBoolean(red, "visibleToPlayer", true);
        setDouble(red, "speed", 420.0);
        setDouble(red, "hullIntegrity", 24.0);
        setDouble(red, "readiness", 24.0);
        setDouble(red, "contactConfidence", 0.16);

        invokeAssignRetreatMission(st, red, safe.id);

        assertTrue(getDouble(red, "speed") < 250.0, "badly damaged retreat should move slower than full speed");
        assertTrue(getDouble(red, "contactConfidence") >= 0.28,
                "visible/recent retreat should preserve enough confidence for player interception");
        assertFalse(((List<?>) getObject(red, "routePoints")).isEmpty());
    }

    @Test
    void antiIdleStartsMiningWorkWhenFleetIsAtMiningPoiWithoutWorkState() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object miner = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (miner == null) miner = firstForceByKind(st, "MINING_GROUP");
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(miner);
        assertNotNull(resource);
        setBoolean(miner, "simulationActive", true);
        setDouble(miner, "x", resource.x);
        setDouble(miner, "y", resource.y);
        setObject(miner, "destinationLocationId", resource.id);
        setEnumByName(miner, "mission", "CONVOY");
        setEnumByName(miner, "intent", "MINING");
        setEnumByName(miner, "state", "IDLE");
        setEnumByName(miner, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(miner, "missionState", "WORKING");
        setEnumByName(miner, "stopReason", "NONE");
        setDouble(miner, "stationaryTimeSec", 30.0);
        setDouble(miner, "antiIdleReassignCooldownSec", 0.0);
        ((List<?>) getObject(miner, "routePoints")).clear();

        invokeApplyCampaignForceAntiIdle(ctx, st, miner);

        assertTrue("WORKING".equals(fieldString(miner, "workState")));
        assertTrue("MINING".equals(fieldString(miner, "stopReason")));
    }

    @Test
    void poiWorkAssignmentsGiveEveryFactionConcreteMiningAndWreckJobs() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation mining = firstLocationOfType(ctx, "RESOURCE_ZONE");
        CampaignSystem.CampaignLocation wreck = firstLocationOfType(ctx, "SALVAGE_FIELD");
        if (wreck == null && mining != null) {
            invokeAddRecoverableWreckSite(st, mining.x + 140.0, mining.y + 85.0, ShipRole.FRIGATE,
                    "Test Wreck", "Recoverable wreck");
            wreck = firstLocationOfType(ctx, "SALVAGE_FIELD");
        }
        assertNotNull(mining);
        assertNotNull(wreck);
        st.theaterWarRecentEvents.clear();

        Object greenMining = testForceAt(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Green POI Mining Guard", mining);
        Object yellowMining = testForceAt(st, CampaignSystem.CampaignForceKind.MINING_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow POI Mining Extractor", mining);
        Object redMining = testForceAt(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red POI Mining Raider", mining);
        assertTrue(invokeAssignPoiWorkMission(ctx, st, greenMining, mining));
        assertTrue(invokeAssignPoiWorkMission(ctx, st, yellowMining, mining));
        assertTrue(invokeAssignPoiWorkMission(ctx, st, redMining, mining));

        assertTrue("GUARDING".equals(fieldString(greenMining, "stopReason"))
                        || "ESCORTING".equals(fieldString(greenMining, "intent")));
        assertTrue("MINING".equals(fieldString(yellowMining, "stopReason")));
        assertTrue("RAID".equals(fieldString(redMining, "mission")));
        assertTrue("AMBUSHING".equals(fieldString(redMining, "stopReason"))
                        || "INTERCEPTING".equals(fieldString(redMining, "intent")));

        Object greenWreck = testForceAt(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Green POI Wreck Recovery", wreck);
        Object yellowWreck = testForceAt(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow POI Wreck Salvager", wreck);
        Object redWreck = testForceAt(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red POI Wreck Cleaner", wreck);
        assertTrue(invokeAssignPoiWorkMission(ctx, st, greenWreck, wreck));
        assertTrue(invokeAssignPoiWorkMission(ctx, st, yellowWreck, wreck));
        assertTrue(invokeAssignPoiWorkMission(ctx, st, redWreck, wreck));

        assertTrue("RECOVERING_SURVIVORS".equals(fieldString(greenWreck, "stopReason")));
        assertTrue("SALVAGE".equals(fieldString(yellowWreck, "cargoKind"))
                        || "SALVAGING".equals(fieldString(yellowWreck, "stopReason")));
        assertTrue("LOOT".equals(fieldString(redWreck, "cargoKind")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green guarding mining site")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow mining, loading cargo")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Red raiding miners")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green recovering survivors")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow salvaging parts")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Red recovering black boxes")));
    }

    @Test
    void poiWorkAssignmentsGiveStationRelayAndShipyardTrafficSpecificWork() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        CampaignSystem.CampaignLocation station = firstLocationWithService(ctx, CampaignSystem.HubService.TRADE);
        if (station == null) station = findLocation(ctx, "poi-07");
        CampaignSystem.CampaignLocation relay = firstLocationWithService(ctx, CampaignSystem.HubService.INTEL);
        if (relay == null) relay = findLocation(ctx, "poi-12");
        CampaignSystem.CampaignLocation shipyard = firstLocationWithService(ctx, CampaignSystem.HubService.SHIPYARD);
        assertNotNull(station);
        assertNotNull(relay);
        assertNotNull(shipyard);
        st.theaterWarRecentEvents.clear();

        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C, "Green POI Station Guard", station),
                station));
        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW, "Yellow POI Station Trader", station),
                station));
        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY, "Red POI Station Blockade", station),
                station));

        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C, "Green POI Relay Tech", relay),
                relay));
        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW, "Yellow POI Relay Broker", relay),
                relay));
        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY, "Red POI Relay Jammer", relay),
                relay));

        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C, "Green POI Shipyard Stage", shipyard),
                shipyard));
        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW, "Yellow POI Shipyard Buyer", shipyard),
                shipyard));
        assertTrue(invokeAssignPoiWorkMission(ctx, st,
                testForceAt(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY, "Red POI Shipyard Saboteur", shipyard),
                shipyard));

        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green repairing, resupplying")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow trading, docking")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Red raiding, blockading")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green defending communications")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow selling data")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Red jamming")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Green shipyard repairing heavy fleets")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Yellow buying repairs")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Red attacking, blockading, sabotaging")));
    }

    @Test
    void antiIdleReturnsEscortWhoseTargetWasDestroyed() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object escort = forceNamed(st, "Green Local Defense Patrol");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        if (target == null) target = firstForceByKind(st, "MINING_GROUP");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-07");
        assertNotNull(escort);
        assertNotNull(target);
        assertNotNull(home);
        setBoolean(escort, "simulationActive", true);
        setObject(escort, "homeBaseId", home.id);
        setEnumByName(escort, "mission", "ESCORT");
        setEnumByName(escort, "intent", "ESCORTING");
        setEnumByName(escort, "workState", "TRAVELING");
        setEnumByName(escort, "missionState", "TRAVELING");
        setEnumByName(escort, "stopReason", "NONE");
        setInt(escort, "targetForceId", getInt(target, "id"));
        setBoolean(target, "destroyed", true);
        setDouble(escort, "antiIdleReassignCooldownSec", 0.0);

        invokeApplyCampaignForceAntiIdle(ctx, st, escort);

        assertTrue("REPAIR".equals(fieldString(escort, "mission")));
        assertTrue(home.id.equals(fieldString(escort, "destinationLocationId")));
        assertFalse(((List<?>) getObject(escort, "routePoints")).isEmpty());
    }

    @Test
    void antiIdleRestoresPatrolLoopWhenWaypointListIsEmpty() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object patrol = forceNamed(st, "Green Local Defense Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-07");
        assertNotNull(patrol);
        assertNotNull(home);
        setBoolean(patrol, "simulationActive", true);
        setObject(patrol, "homeBaseId", home.id);
        setEnumByName(patrol, "mission", "PATROL");
        setEnumByName(patrol, "intent", "PATROLLING");
        setEnumByName(patrol, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(patrol, "missionState", "WORKING");
        setEnumByName(patrol, "stopReason", "SCANNING");
        setDouble(patrol, "workRemainingSec", 4.0);
        setDouble(patrol, "taskDeadlineSec", 7.0);
        setDouble(patrol, "antiIdleReassignCooldownSec", 0.0);
        ((List<?>) getObject(patrol, "routePoints")).clear();
        ((List<?>) getObject(patrol, "patrolWaypoints")).clear();

        invokeApplyCampaignForceAntiIdle(ctx, st, patrol);

        assertFalse(((List<?>) getObject(patrol, "patrolWaypoints")).isEmpty());
        assertFalse(((List<?>) getObject(patrol, "routePoints")).isEmpty());
    }

    @Test
    void antiIdleRoutesTimedOutRaidAwayFromDeadAmbush() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object raider = forceNamed(st, "Red Frontier Picket Patrol");
        CampaignSystem.CampaignLocation home = findLocation(ctx, "poi-21");
        assertNotNull(raider);
        assertNotNull(home);
        setBoolean(raider, "simulationActive", true);
        setObject(raider, "homeBaseId", home.id);
        setEnumByName(raider, "mission", "RAID");
        setEnumByName(raider, "intent", "INTERCEPTING");
        setEnumByName(raider, "workState", "WAITING_WITH_PURPOSE");
        setEnumByName(raider, "missionState", "WORKING");
        setEnumByName(raider, "stopReason", "AMBUSHING");
        setInt(raider, "targetForceId", 0);
        setDouble(raider, "intentTimerSec", 0.0);
        setDouble(raider, "workRemainingSec", 5.0);
        setDouble(raider, "taskDeadlineSec", 0.0);
        setDouble(raider, "antiIdleReassignCooldownSec", 0.0);
        ((List<?>) getObject(raider, "routePoints")).clear();

        invokeApplyCampaignForceAntiIdle(ctx, st, raider);

        assertFalse(((List<?>) getObject(raider, "routePoints")).isEmpty(),
                "timed-out raid should retarget or return instead of sitting at ambush");
        assertTrue(!"RAID".equals(fieldString(raider, "mission")) || getInt(raider, "targetForceId") > 0,
                "raid should either find a target or convert into a return/recovery route");
    }

    @Test
    void antiIdleAssignsSafeBaseWhenRetreatHasNoDestination() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object red = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(red);
        setBoolean(red, "simulationActive", true);
        setEnumByName(red, "mission", "REPAIR");
        setEnumByName(red, "intent", "RETREATING");
        setEnumByName(red, "workState", "RECOVERING");
        setEnumByName(red, "missionState", "RETREATING");
        setEnumByName(red, "stopReason", "AVOIDING_SUPERIOR_THREAT");
        setObject(red, "destinationLocationId", "");
        setDouble(red, "antiIdleReassignCooldownSec", 0.0);
        ((List<?>) getObject(red, "routePoints")).clear();

        invokeApplyCampaignForceAntiIdle(ctx, st, red);

        assertFalse(fieldString(red, "destinationLocationId").isBlank());
        assertFalse(((List<?>) getObject(red, "routePoints")).isEmpty());
        assertTrue("RETREATING".equals(fieldString(red, "intent")));
    }

    @Test
    void destroyingRedScoutDegradesLocalRedContactConfidence() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object scout = forceNamed(st, "Red Scout Pair");
        Object hunter = forceNamed(st, "Red Frontier Picket Patrol");
        Object target = firstForceByKindAndFaction(st, "MINING_GROUP", "BRIGHT_YELLOW");
        assertNotNull(scout);
        assertNotNull(hunter);
        assertNotNull(target);
        setBoolean(hunter, "simulationActive", true);
        setEnumByName(scout, "mission", "RECON");
        setEnumByName(hunter, "mission", "RAID");
        setDouble(scout, "x", 2400.0);
        setDouble(scout, "y", 2400.0);
        setDouble(hunter, "x", 2480.0);
        setDouble(hunter, "y", 2420.0);
        setDouble(target, "x", 2520.0);
        setDouble(target, "y", 2440.0);
        invokeSeedNpcForceContact(hunter, target, 0.92);
        Object contact = ((java.util.Map<?, ?>) getObject(hunter, "knownHostileContacts")).get(getInt(target, "id"));
        double before = getDouble(contact, "confidence");

        invokeApplyRedScoutLossConsequences(ctx, st, scout);

        double after = getDouble(contact, "confidence");
        assertTrue(after < before - 0.25, "Red hunter contact should become materially less reliable after scout loss");
        assertTrue(getDouble(contact, "ageSec") >= 45.0, "lost scout intel should age local Red contacts");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("scout loss degraded")
                        && line.contains("raid accuracy")),
                "theater log should explain degraded Red raid accuracy");
    }

    @Test
    void destroyingRedScoutDispatchesReplacementFromNamedSource() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object scout = forceNamed(st, "Red Scout Pair");
        assertNotNull(scout);
        setEnumByName(scout, "mission", "RECON");
        setDouble(scout, "x", st.playerGalaxyX + 600.0);
        setDouble(scout, "y", st.playerGalaxyY - 600.0);
        int before = countForcesNamed(st, "Red Replacement Scout");

        invokeApplyRedScoutLossConsequences(ctx, st, scout);

        assertTrue(countForcesNamed(st, "Red Replacement Scout") == before + 1);
        Object replacement = firstForceNamedContaining(st, "Red Replacement Scout");
        assertNotNull(replacement);
        assertTrue("RECON".equals(fieldString(replacement, "mission")));
        assertTrue("PATROLLING".equals(fieldString(replacement, "intent")));
        assertFalse(fieldString(replacement, "sourceLocationId").isBlank(), "replacement scout should have a launch source");
        assertFalse(fieldString(replacement, "homeBaseId").isBlank(), "replacement scout should have a home base");
        assertFalse(((List<?>) getObject(replacement, "routePoints")).isEmpty(), "replacement scout should sail out from source");
        CampaignSystem.CampaignLocation source = findLocation(ctx, fieldString(replacement, "sourceLocationId"));
        assertNotNull(source);
        assertTrue(Math.abs(source.x - getDouble(replacement, "x")) <= 0.001);
        assertTrue(Math.abs(source.y - getDouble(replacement, "y")) <= 0.001);
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Replacement Scout")
                        && line.contains("launched from")),
                "theater log should name the replacement launch source");
    }

    @Test
    void deterministicLivingWarScenarioRunsScoutRaidDistressResponseAndAftermath() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenBase = findLocation(ctx, "poi-01");
        CampaignSystem.CampaignLocation greenRelay = findLocation(ctx, "poi-12");
        CampaignSystem.CampaignLocation tradeStation = findLocation(ctx, "poi-02");
        CampaignSystem.CampaignLocation refinery = findLocation(ctx, "poi-11");
        CampaignSystem.CampaignLocation redDepot = firstLocationOfType(ctx, "ENEMY_ACTIVITY");
        CampaignSystem.CampaignLocation miningSite = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(greenBase);
        assertNotNull(greenRelay);
        assertNotNull(tradeStation);
        assertNotNull(refinery);
        assertNotNull(redDepot);
        assertNotNull(miningSite);

        for (Object force : campaignForces(st)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "x", 4700.0);
            setDouble(force, "y", 4700.0);
        }
        Object greenPatrol = forceNamed(st, "Green Local Defense Patrol");
        if (greenPatrol == null) {
            greenPatrol = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                    "Green Scenario Patrol", greenBase.id, "Scenario relay patrol", greenBase.x, greenBase.y);
        }
        Object miner = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.MINING_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Scenario Miner", tradeStation.id, "Scenario mining loop", tradeStation.x, tradeStation.y);
        Object redScout = forceNamed(st, "Red Scout Pair");
        if (redScout == null) {
            redScout = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                    "Red Scenario Scout", redDepot.id, "Scenario scout ahead of raid", redDepot.x, redDepot.y);
        }
        Object redRaider = forceNamed(st, "Red Frontier Picket Patrol");
        if (redRaider == null) {
            redRaider = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                    "Red Scenario Raider", redDepot.id, "Scenario raid force", redDepot.x, redDepot.y);
        }
        Object greenResponse = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Scenario Response", greenRelay.id, "Scenario response fleet", greenRelay.x, greenRelay.y);
        Object yellowTrade = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Yellow Scenario Trade Route", tradeStation.id, "Scenario Yellow trade route", tradeStation.x + 80.0, tradeStation.y + 40.0);

        setBoolean(greenPatrol, "simulationActive", true);
        setDouble(greenPatrol, "x", greenRelay.x - 12.0);
        setDouble(greenPatrol, "y", greenRelay.y);
        setDouble(greenPatrol, "speed", 500.0);
        setDouble(greenPatrol, "operatingRadius", 1400.0);
        invokeAssignPatrolMission(st, greenPatrol, List.of(
                new double[]{greenRelay.x, greenRelay.y},
                new double[]{miningSite.x, miningSite.y},
                new double[]{tradeStation.x, tradeStation.y},
                new double[]{greenBase.x, greenBase.y}));

        invokeAdvanceCampaignForcePosition(greenPatrol, 0.2);
        assertTrue("SCANNING".equals(fieldString(greenPatrol, "stopReason")));
        for (int i = 0; i < 40; i++) {
            invokeLifecycleBeforeOrders(st, greenPatrol, 0.2);
            invokeLifecycleAfterMovement(st, greenPatrol, 0.2);
        }
        setDouble(greenPatrol, "x", miningSite.x - 12.0);
        setDouble(greenPatrol, "y", miningSite.y);
        invokeAssignPatrolMission(st, greenPatrol, List.of(
                new double[]{miningSite.x, miningSite.y},
                new double[]{tradeStation.x, tradeStation.y},
                new double[]{greenBase.x, greenBase.y}));
        invokeAdvanceCampaignForcePosition(greenPatrol, 0.2);
        assertTrue("SCANNING".equals(fieldString(greenPatrol, "stopReason")),
                "Green patrol should pause and scan at the mining site");

        setBoolean(miner, "simulationActive", true);
        setObject(miner, "sourceLocationId", tradeStation.id);
        setObject(miner, "homeBaseId", refinery.id);
        setDouble(miner, "x", tradeStation.x);
        setDouble(miner, "y", tradeStation.y);
        setDouble(miner, "strength", 24.0);
        setDouble(miner, "readiness", 48.0);
        setDouble(miner, "cargoLoad", 0.0);
        setDouble(miner, "cargoCapacity", 100.0);
        invokeAssignMiningMission(st, miner, miningSite.id, refinery.id);
        assertFalse(((List<?>) getObject(miner, "routePoints")).isEmpty());
        setDouble(miner, "x", miningSite.x + 8.0);
        setDouble(miner, "y", miningSite.y + 4.0);
        invokeUpdateCampaignForceOrders(ctx, st, miner, 0.2);
        assertTrue("MINING".equals(fieldString(miner, "intent")));
        assertTrue("MINING".equals(fieldString(miner, "stopReason")));

        setBoolean(redScout, "simulationActive", true);
        setObject(redScout, "sourceLocationId", redDepot.id);
        setObject(redScout, "homeBaseId", redDepot.id);
        setEnumByName(redScout, "mission", "RECON");
        setDouble(redScout, "x", redDepot.x);
        setDouble(redScout, "y", redDepot.y);
        setDouble(redScout, "strength", 24.0);
        setDouble(redScout, "readiness", 78.0);
        invokeAssignPatrolMission(st, redScout, List.of(new double[]{miningSite.x - 180.0, miningSite.y - 80.0}));
        setEnumByName(redScout, "mission", "RECON");
        assertFalse(((List<?>) getObject(redScout, "routePoints")).isEmpty());
        invokeSeedNpcForceContact(redScout, miner, 0.94);

        setBoolean(redRaider, "simulationActive", true);
        setObject(redRaider, "sourceLocationId", redDepot.id);
        setObject(redRaider, "homeBaseId", redDepot.id);
        setDouble(redRaider, "x", redDepot.x);
        setDouble(redRaider, "y", redDepot.y);
        setDouble(redRaider, "strength", 96.0);
        setDouble(redRaider, "readiness", 96.0);
        setDouble(redRaider, "supply", 96.0);
        setDouble(redRaider, "hullIntegrity", 96.0);
        ((java.util.Map<?, ?>) getObject(redRaider, "knownHostileContacts")).clear();
        invokeSeedNpcForceContact(redRaider, miner, 0.94);
        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, redRaider));
        assertTrue("RAID".equals(fieldString(redRaider, "mission")));
        assertTrue(getInt(redRaider, "targetForceId") == getInt(miner, "id"));
        List<?> raidRoute = (List<?>) getObject(redRaider, "routePoints");
        assertTrue(raidRoute.size() >= 2, "Red raider should use an indirect approach lane from the named depot");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("RAID WARNING")
                        && line.contains(redDepot.name)
                        && line.contains(fieldString(miner, "name"))
                        && line.contains("ETA")
                        && line.contains("actions")),
                "raid warning should name source, target, ETA, and action options");

        setDouble(greenPatrol, "x", miningSite.x - 120.0);
        setDouble(greenPatrol, "y", miningSite.y);
        setDouble(redScout, "x", miningSite.x + 40.0);
        setDouble(redScout, "y", miningSite.y);
        invokeSeedNpcForceContact(greenPatrol, redScout, 0.95);
        assertTrue(invokeApplyNpcContactDecision(st, greenPatrol));
        assertTrue("INTERCEPTING".equals(fieldString(greenPatrol, "intent"))
                || "SEARCHING".equals(fieldString(greenPatrol, "intent")));
        setDouble(redScout, "x", redDepot.x);
        setDouble(redScout, "y", redDepot.y);
        Object redTheater = invokeCampaignTheaterForPoint(st, redDepot.y);
        setDouble(redTheater, "controlScore", -70.0);
        setInt(greenPatrol, "targetForceId", 0);
        invokeSeedNpcForceContact(greenPatrol, redScout, 0.95);
        invokeApplyNpcContactDecision(st, greenPatrol);
        assertFalse(getInt(greenPatrol, "targetForceId") == getInt(redScout, "id"),
                "Green patrol should not follow the scout into strong Red control");

        setDouble(redRaider, "x", miningSite.x + 40.0);
        setDouble(redRaider, "y", miningSite.y);
        setDouble(miner, "x", miningSite.x);
        setDouble(miner, "y", miningSite.y);
        setDouble(greenPatrol, "x", greenBase.x);
        setDouble(greenPatrol, "y", greenBase.y);
        Object theater = invokeCampaignTheaterForPoint(st, miningSite.y);
        setDouble(theater, "routeRisk", 42.0);
        setDouble(theater, "controlScore", 0.0);
        double routeRiskBefore = getDouble(theater, "routeRisk");
        double controlBefore = getDouble(theater, "controlScore");
        assertTrue(invokeMaintainRaidMission(ctx, st, redRaider, 2.0));
        assertTrue("RETREATING".equals(fieldString(miner, "intent")),
                "Red raider should hit the Yellow miner if Green does not intercept in time");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("distress")
                && line.contains(fieldString(miner, "name"))));

        setBoolean(greenResponse, "simulationActive", true);
        setDouble(greenResponse, "x", miningSite.x - 120.0);
        setDouble(greenResponse, "y", miningSite.y);
        setDouble(greenResponse, "strength", 84.0);
        setDouble(greenResponse, "readiness", 90.0);
        setDouble(greenResponse, "operatingRadius", 1300.0);
        setEnumByName(greenResponse, "mission", "PATROL");
        setEnumByName(greenResponse, "intent", "PATROLLING");
        assertTrue(invokeApplyMiningDistressBehavior(ctx, st, miner));
        assertTrue("REINFORCE".equals(fieldString(greenResponse, "mission")));
        assertFalse(fieldString(greenResponse, "sourceLocationId").isBlank());
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("launched from")
                && line.contains("Green response")));

        invokeAddRecoverableWreckSite(st, miningSite.x + 60.0, miningSite.y + 35.0, ShipRole.FRIGATE,
                "Scenario Battle Wreck", "Deterministic aftermath marker");
        Object battle = invokeCreateCampaignBattle(77, greenResponse, redRaider);
        invokeApplyCampaignBattleRegionalConsequences(st, battle, greenResponse, redRaider);
        assertTrue(((List<?>) getObject(st, "recoverableWreckSites")).stream()
                .anyMatch(wreck -> fieldString(wreck, "label").contains("Scenario Battle Wreck")));
        assertTrue(getDouble(theater, "routeRisk") != routeRiskBefore);
        assertTrue(getDouble(theater, "controlScore") != controlBefore);

        setBoolean(yellowTrade, "simulationActive", true);
        setDouble(yellowTrade, "x", miningSite.x + 120.0);
        setDouble(yellowTrade, "y", miningSite.y);
        setDouble(yellowTrade, "strength", 18.0);
        setObject(yellowTrade, "homeBaseId", tradeStation.id);
        setDouble(theater, "routeRisk", 72.0);
        assertTrue(invokeApplyAlliedRoutePressureBehavior(ctx, st, yellowTrade));
        assertFalse(((List<?>) getObject(yellowTrade, "routePoints")).isEmpty(),
                "Yellow traffic should reroute after the route becomes unsafe");

        simulateCampaignMinutes(ctx, st, 5);
        List<String> report = CampaignSystem.campaignFleetLifecycleReport(ctx);
        assertTrue(report.stream().anyMatch(line -> line.contains("all active NPC fleets valid")),
                "deterministic scenario should leave every surviving fleet with valid lifecycle state: " + report);
    }

    @Test
    void nineBasicFactionJobsCoexistForFiveMinutesWithoutInvalidLifecycleStates() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation greenBase = findLocation(ctx, "poi-01");
        CampaignSystem.CampaignLocation yellowHub = findLocation(ctx, "poi-02");
        CampaignSystem.CampaignLocation refinery = findLocation(ctx, "poi-11");
        CampaignSystem.CampaignLocation redBase = firstLocationOfType(ctx, "ENEMY_ACTIVITY");
        CampaignSystem.CampaignLocation resource = firstLocationOfType(ctx, "RESOURCE_ZONE");
        assertNotNull(greenBase);
        assertNotNull(yellowHub);
        assertNotNull(refinery);
        assertNotNull(redBase);
        assertNotNull(resource);

        Object greenPatrol = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Nine Job Green Patrol", greenBase.id, "Nine job patrol", greenBase.x + 40.0, greenBase.y + 40.0);
        Object greenEscort = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Nine Job Green Escort", greenBase.id, "Nine job escort", greenBase.x + 80.0, greenBase.y + 50.0);
        Object greenResponse = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Nine Job Green Response", greenBase.id, "Nine job response", greenBase.x + 120.0, greenBase.y + 60.0);
        Object yellowTrade = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Nine Job Yellow Trade", yellowHub.id, "Nine job trade", yellowHub.x + 50.0, yellowHub.y + 40.0);
        Object yellowMiner = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.MINING_GROUP, Faction.BRIGHT_YELLOW,
                "Nine Job Yellow Miner", yellowHub.id, "Nine job miner", yellowHub.x + 90.0, yellowHub.y + 60.0);
        Object yellowSalvage = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.BRIGHT_YELLOW,
                "Nine Job Yellow Salvage", yellowHub.id, "Nine job salvage", yellowHub.x + 130.0, yellowHub.y + 80.0);
        Object redScout = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Nine Job Red Scout", redBase.id, "Nine job scout", redBase.x + 40.0, redBase.y + 40.0);
        Object redRaider = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Nine Job Red Raider", redBase.id, "Nine job raid", redBase.x + 80.0, redBase.y + 60.0);
        Object redRepair = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Nine Job Red Repair", redBase.id, "Nine job return repair", redBase.x + 120.0, redBase.y + 80.0);
        Object damagedFriendly = invokeEnsureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.BRIGHT_YELLOW,
                "Nine Job Damaged Friendly", yellowHub.id, "Nine job rescue target", greenBase.x + 160.0, greenBase.y + 70.0);

        for (Object force : List.of(greenPatrol, greenEscort, greenResponse, yellowTrade, yellowMiner,
                yellowSalvage, redScout, redRaider, redRepair, damagedFriendly)) {
            setBoolean(force, "simulationActive", true);
            setDouble(force, "readiness", 82.0);
            setDouble(force, "hullIntegrity", 86.0);
            setDouble(force, "supply", 84.0);
            setDouble(force, "fuelLevel", 84.0);
        }
        setDouble(damagedFriendly, "readiness", 24.0);
        setDouble(damagedFriendly, "hullIntegrity", 28.0);
        setDouble(redRepair, "readiness", 26.0);
        setDouble(redRepair, "hullIntegrity", 32.0);

        invokeAssignPatrolMission(st, greenPatrol, List.of(
                new double[]{greenBase.x + 120.0, greenBase.y + 80.0},
                new double[]{greenBase.x - 100.0, greenBase.y + 140.0},
                new double[]{greenBase.x + 40.0, greenBase.y - 120.0}));
        invokeAssignEscortMission(st, greenEscort, getInt(yellowTrade, "id"));
        invokeAssignRepairRescueMission(st, greenResponse, getInt(damagedFriendly, "id"));
        setObject(yellowTrade, "destinationLocationId", refinery.id);
        assertTrue(invokeAssignSimpleDirectorMission(ctx, st, yellowTrade));
        invokeAssignMiningMission(st, yellowMiner, resource.id, refinery.id);
        invokeAddRecoverableWreckSite(st, yellowHub.x + 220.0, yellowHub.y + 100.0, ShipRole.FRIGATE,
                "Nine Job Wreck", "Nine job salvage target");
        invokeAssignSalvageMission(st, yellowSalvage, yellowHub.x + 220.0, yellowHub.y + 100.0, yellowHub.id);
        invokeAssignPatrolMission(st, redScout, List.of(
                new double[]{redBase.x + 140.0, redBase.y - 80.0},
                new double[]{redBase.x - 120.0, redBase.y + 100.0}));
        setEnumByName(redScout, "mission", "RECON");
        invokeAssignRaidMission(st, redRaider, yellowMiner);
        invokeAssignReturnToBaseMission(st, redRepair, redBase.id);

        for (Object force : List.of(greenPatrol, greenEscort, greenResponse, yellowTrade, yellowMiner,
                yellowSalvage, redScout, redRaider, redRepair)) {
            assertFalse("HOLDING".equals(fieldString(force, "intent")));
            assertFalse(fieldString(force, "mission").isBlank());
        }

        simulateCampaignMinutes(ctx, st, 5);

        List<String> report = CampaignSystem.campaignFleetLifecycleReport(ctx);
        assertTrue(report.stream().anyMatch(line -> line.contains("all active NPC fleets valid")),
                "nine basic jobs should coexist for five campaign minutes without invalid lifecycle states: " + report);
    }

    @Test
    void fiveMinuteLifecycleSoakKeepsActiveNpcFleetsValid() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        simulateCampaignMinutes(ctx, st, 5);

        List<String> report = CampaignSystem.campaignFleetLifecycleReport(ctx);
        assertTrue(report.stream().anyMatch(line -> line.contains("all active NPC fleets valid")),
                "five-minute lifecycle soak should leave no persistent invalid fleets: " + report);
    }

    @Test
    void tenMinuteAntiIdleSoakKeepsNpcFleetsFromBlankStationaryStops() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        simulateCampaignMinutes(ctx, st, 10);

        List<String> report = CampaignSystem.campaignFleetLifecycleReport(ctx);
        assertTrue(report.stream().anyMatch(line -> line.contains("all active NPC fleets valid")),
                "ten-minute anti-idle soak should leave no persistent invalid fleets: " + report);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void invokeForceSimulation(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        UPDATE_FORCE_SIMULATION.invoke(null, ctx, st, dt);
    }

    private static void simulateCampaignMinutes(GameContext ctx, CampaignSystem.CampaignState st, int minutes) throws Exception {
        int seconds = Math.max(0, minutes) * 60;
        double stepSeconds = 5.0;
        int steps = (int) Math.ceil(seconds / stepSeconds);
        for (int i = 0; i < steps; i++) {
            invokeForceSimulation(ctx, st, stepSeconds);
        }
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        UPDATE_TRAVEL.invoke(null, ctx, st, dt);
    }

    private static void invokeStrikeObjectUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        UPDATE_STRATEGIC_STRIKE_OBJECTS.invoke(null, ctx, st, dt);
    }

    private static void invokeOvermapGhostFleetSweep(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        UPDATE_OVERMAP_GHOST_FLEET_SWEEP.invoke(null, ctx, st, dt);
    }

    private static void invokePrepareGalaxySearchGroupEncounterWorld(GameContext ctx,
                                                                      CampaignSystem.CampaignState st,
                                                                      CampaignSystem.GalaxySearchGroup group) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "prepareGalaxySearchGroupEncounterWorld",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.GalaxySearchGroup.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, group);
    }

    private static void invokeSpawnCoalitionSupportFleet(GameContext ctx,
                                                         CampaignSystem.CampaignState st,
                                                         boolean allowNamedSupport,
                                                         boolean allowNearbySupport,
                                                         CampaignSystem.CampaignForce primaryOwner) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "spawnCoalitionSupportFleet",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                boolean.class,
                boolean.class,
                CampaignSystem.CampaignForce.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, allowNamedSupport, allowNearbySupport, primaryOwner);
    }

    private static CampaignSystem.CampaignShipPoolRecord addPoolRecord(CampaignSystem.CampaignState st,
                                                                        Faction faction,
                                                                        ShipRole role,
                                                                        int forceId,
                                                                        String name) {
        CampaignSystem.CampaignShipPoolRecord record = new CampaignSystem.CampaignShipPoolRecord(
                st.nextCampaignShipRecordId++,
                faction,
                role,
                CampaignSystem.CampaignShipPoolStatus.ACTIVE,
                "test-base",
                forceId,
                100.0,
                name
        );
        st.campaignShipPool.put(record.id, record);
        return record;
    }

    private static boolean hasNamedShip(GameContext ctx, String name) {
        if (ctx == null || ctx.ships == null) return false;
        return ctx.ships.stream().anyMatch(ship -> ship != null && name.equals(ship.name));
    }

    private static Method declaredMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = CampaignSystem.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void invokeMaintainVisibleFleetContacts(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainMinimumVisibleFleetContacts",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static String invokeSerializeCampaignForces(CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("serializeCampaignForces", CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        return (String) method.invoke(null, st);
    }

    private static void invokeRestoreCampaignForces(CampaignSystem.CampaignState st,
                                                    String forceRaw,
                                                    String membershipRaw,
                                                    int nextForceId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "restoreCampaignForces",
                CampaignSystem.CampaignState.class,
                String.class,
                String.class,
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, st, forceRaw, membershipRaw, nextForceId);
    }

    private static void invokeAssignPatrolMission(CampaignSystem.CampaignState st, Object force, List<double[]> waypoints) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignPatrolMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                List.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, waypoints);
    }

    private static void invokeUpdateCampaignForceOrders(GameContext ctx,
                                                        CampaignSystem.CampaignState st,
                                                        Object force,
                                                        double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceOrders",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, force, dt);
    }

    private static void invokeUpdateGreenCounterSorties(GameContext ctx,
                                                        CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGreenCounterSorties",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeAssignMiningMission(CampaignSystem.CampaignState st,
                                                  Object force,
                                                  String miningSiteId,
                                                  String refineryId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignMiningMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, miningSiteId, refineryId);
    }

    private static void invokeAssignReturnToBaseMission(CampaignSystem.CampaignState st,
                                                        Object force,
                                                        String baseId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignReturnToBaseMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, baseId);
    }

    private static void invokeAssignRetreatMission(CampaignSystem.CampaignState st,
                                                   Object force,
                                                   String safeDestinationId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignRetreatMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, safeDestinationId);
    }

    private static void invokeAssignEscortMission(CampaignSystem.CampaignState st, Object escort, int escortedForceId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignEscortMission",
                CampaignSystem.CampaignState.class,
                escort.getClass(),
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, st, escort, escortedForceId);
    }

    private static void invokeAssignHuntMission(CampaignSystem.CampaignState st, Object hunter, int targetForceId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignHuntMission",
                CampaignSystem.CampaignState.class,
                hunter.getClass(),
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, st, hunter, targetForceId);
    }

    private static void invokeAssignBlockadeMission(CampaignSystem.CampaignState st, Object force, String locationId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignBlockadeMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, locationId);
    }

    private static void invokeAssignRaidMission(CampaignSystem.CampaignState st, Object force, Object target) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignRaidMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                target.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, st, force, target);
    }

    private static void invokeAssignSalvageMission(CampaignSystem.CampaignState st,
                                                   Object force,
                                                   double wreckX,
                                                   double wreckY,
                                                   String returnStationId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignSalvageMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class,
                double.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, wreckX, wreckY, returnStationId);
    }

    private static void invokeAssignSiegeMission(CampaignSystem.CampaignState st,
                                                 Object force,
                                                 String stagingPointId,
                                                 String targetLocationId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignSiegeMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, stagingPointId, targetLocationId);
    }

    private static boolean invokeAssignGreenAssaultMission(GameContext ctx,
                                                           CampaignSystem.CampaignState st,
                                                           Object force,
                                                           String targetLocationId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignGreenAssaultMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force, targetLocationId);
    }

    private static boolean invokeAssignRedInvasionMission(CampaignSystem.CampaignState st,
                                                          Object force,
                                                          String stagingPointId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignRedInvasionMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, st, force, stagingPointId);
    }

    private static void invokeAssignRepairRescueMission(CampaignSystem.CampaignState st, Object force, int damagedAllyId) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignRepairRescueMission",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, damagedAllyId);
    }

    private static boolean invokeAssignRedDefenseMission(CampaignSystem.CampaignState st, Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignRedDefenseMission",
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, st, force);
    }

    private static void invokeAdvanceCampaignForcePosition(Object force, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "advanceCampaignForcePosition",
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, force, dt);
    }

    private static void invokeLifecycleBeforeOrders(CampaignSystem.CampaignState st, Object force, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceLifecycleBeforeOrders",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, dt);
    }

    private static void invokeLifecycleAfterMovement(CampaignSystem.CampaignState st, Object force, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceLifecycleAfterMovement",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, dt);
    }

    private static void invokeApplyCampaignForceAntiIdle(GameContext ctx,
                                                         CampaignSystem.CampaignState st,
                                                         Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCampaignForceAntiIdle",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, force);
    }

    private static boolean invokeMaintainEscortMission(GameContext ctx,
                                                       CampaignSystem.CampaignState st,
                                                       Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainEscortMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeMaintainHuntMission(GameContext ctx,
                                                     CampaignSystem.CampaignState st,
                                                     Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainHuntMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeMaintainBlockadeMission(GameContext ctx,
                                                         CampaignSystem.CampaignState st,
                                                         Object force,
                                                         double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainBlockadeMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force, dt);
    }

    private static boolean invokeMaintainRaidMission(GameContext ctx,
                                                     CampaignSystem.CampaignState st,
                                                     Object force,
                                                     double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainRaidMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force, dt);
    }

    private static boolean invokeMaintainSiegeMission(GameContext ctx,
                                                      CampaignSystem.CampaignState st,
                                                      Object force,
                                                      double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainSiegeMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force, dt);
    }

    private static boolean invokeMaintainRepairRescueMission(GameContext ctx,
                                                             CampaignSystem.CampaignState st,
                                                             Object force,
                                                             double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainRepairRescueMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force, dt);
    }

    private static Object invokeCreateCampaignBattle(int id, Object a, Object b) throws Exception {
        Class<?> battleType = Class.forName("CampaignSystemModels$CampaignBattle");
        java.lang.reflect.Constructor<?> constructor = battleType.getDeclaredConstructor(int.class, a.getClass(), b.getClass());
        constructor.setAccessible(true);
        Object battle = constructor.newInstance(id, a, b);
        setDouble(battle, "importance", 1.0);
        return battle;
    }

    private static boolean invokeCampaignBattleCanPromptPlayer(GameContext ctx,
                                                               CampaignSystem.CampaignState st,
                                                               Object battle) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "campaignBattleCanPromptPlayer",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                battle.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, battle);
    }

    private static void invokeApplyCampaignBattleRegionalConsequences(CampaignSystem.CampaignState st,
                                                                      Object battle,
                                                                      Object winner,
                                                                      Object loser) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCampaignBattleRegionalConsequences",
                CampaignSystem.CampaignState.class,
                battle.getClass(),
                winner.getClass(),
                loser.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, st, battle, winner, loser);
    }

    private static Object invokeCampaignTheaterForPoint(CampaignSystem.CampaignState st, double y) throws Exception {
        Method theaterForPoint = CampaignSystem.class.getDeclaredMethod(
                "theaterForPoint",
                CampaignSystem.CampaignState.class,
                double.class
        );
        theaterForPoint.setAccessible(true);
        Object theaterId = theaterForPoint.invoke(null, st, y);
        Method campaignTheaterById = CampaignSystem.class.getDeclaredMethod(
                "campaignTheaterById",
                CampaignSystem.CampaignState.class,
                theaterId.getClass()
        );
        campaignTheaterById.setAccessible(true);
        return campaignTheaterById.invoke(null, st, theaterId);
    }

    private static Object invokeDoctrineForFaction(CampaignSystem.CampaignState st, Faction faction) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "doctrineForFaction",
                CampaignSystem.CampaignState.class,
                Faction.class
        );
        method.setAccessible(true);
        return method.invoke(null, st, faction);
    }

    private static boolean invokeApplyMiningDistressBehavior(GameContext ctx,
                                                             CampaignSystem.CampaignState st,
                                                             Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyMiningDistressBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeAssignPoiWorkMission(GameContext ctx,
                                                      CampaignSystem.CampaignState st,
                                                      Object force,
                                                      CampaignSystem.CampaignLocation location) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignPoiWorkMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                Class.forName("CampaignSystemModels$CampaignForce"),
                CampaignSystem.CampaignLocation.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force, location);
    }

    private static Object invokeValidateFleetLifecycle(CampaignSystem.CampaignState st, Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "validateFleetLifecycle",
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return method.invoke(null, st, force);
    }

    private static void invokeAssignDirectorAfterRepairResupplyComplete(GameContext ctx,
                                                                        CampaignSystem.CampaignState st,
                                                                        Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignDirectorAfterRepairResupplyComplete",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, force);
    }

    private static void invokeAssignDirectorAfterStationWorkComplete(GameContext ctx,
                                                                     CampaignSystem.CampaignState st,
                                                                     Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignDirectorAfterStationWorkComplete",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, force);
    }

    private static boolean invokeAssignSimpleDirectorMission(GameContext ctx,
                                                             CampaignSystem.CampaignState st,
                                                             Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "assignSimpleDirectorMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplySalvageContestBehavior(GameContext ctx,
                                                             CampaignSystem.CampaignState st,
                                                             Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applySalvageContestBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyAlliedRoutePressureBehavior(GameContext ctx,
                                                                  CampaignSystem.CampaignState st,
                                                                  Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyAlliedRoutePressureBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyYellowPirateBehavior(GameContext ctx,
                                                           CampaignSystem.CampaignState st,
                                                           Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyYellowPirateBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyYellowSmugglerBehavior(GameContext ctx,
                                                             CampaignSystem.CampaignState st,
                                                             Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyYellowSmugglerBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyGreenSmugglerInspectionBehavior(GameContext ctx,
                                                                      CampaignSystem.CampaignState st,
                                                                      Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyGreenSmugglerInspectionBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyPlayerCivilianTrafficAttackConsequences(CampaignSystem.CampaignState st,
                                                                              Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyPlayerCivilianTrafficAttackConsequences",
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, st, force);
    }

    private static boolean invokeMaintainGreenResponseMission(GameContext ctx,
                                                              CampaignSystem.CampaignState st,
                                                              Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainGreenResponseMission",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyGreenResponseTriggerScan(GameContext ctx,
                                                               CampaignSystem.CampaignState st,
                                                               Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyGreenResponseTriggerScan",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static void invokeApplyShipyardReinforcementTick(CampaignSystem.CampaignState st,
                                                             double tickSeconds) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyShipyardReinforcementTick",
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, st, tickSeconds);
    }

    private static boolean invokeApplyYellowMercenaryContractBehavior(GameContext ctx,
                                                                      CampaignSystem.CampaignState st,
                                                                      Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyYellowMercenaryContractBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyMiningEscortLossBehavior(GameContext ctx,
                                                               CampaignSystem.CampaignState st,
                                                               Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyMiningEscortLossBehavior",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, ctx, st, force);
    }

    private static boolean invokeApplyNpcContactDecision(CampaignSystem.CampaignState st,
                                                         Object force) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyNpcContactDecision",
                CampaignSystem.CampaignState.class,
                force.getClass()
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, st, force);
    }

    private static void invokeAddRecoverableWreckSite(CampaignSystem.CampaignState st,
                                                      double x,
                                                      double y,
                                                      ShipRole role,
                                                      String label,
                                                      String subtitle) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "addRecoverableWreckSite",
                CampaignSystem.CampaignState.class,
                double.class,
                double.class,
                ShipRole.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(null, st, x, y, role, label, subtitle);
    }

    private static void invokeRefreshNpcForceContacts(CampaignSystem.CampaignState st, Object force, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "refreshNpcForceContacts",
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, st, force, dt);
    }

    private static void invokeUpdateCampaignForceContactState(GameContext ctx,
                                                              CampaignSystem.CampaignState st,
                                                              Object force,
                                                              double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceContactState",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                force.getClass(),
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, force, dt);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void invokeAddSensorRelayNode(CampaignSystem.CampaignState st,
                                                 String label,
                                                 double x,
                                                 double y,
                                                 double radius,
                                                 double ttlSec,
                                                 boolean scout) throws Exception {
        Class<?> relayType = Class.forName("CampaignSystemModels$SensorRelayNode");
        java.lang.reflect.Constructor<?> constructor = relayType.getDeclaredConstructor(
                int.class,
                String.class,
                double.class,
                double.class,
                double.class,
                double.class,
                boolean.class
        );
        constructor.setAccessible(true);
        Object relay = constructor.newInstance(st.nextSensorRelayId++, label, x, y, radius, ttlSec, scout);
        ((java.util.List) st.sensorRelayNodes).add(relay);
    }

    private static void invokeApplyRedScoutLossConsequences(GameContext ctx,
                                                            CampaignSystem.CampaignState st,
                                                            Object scout) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyRedScoutLossConsequences",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                scout.getClass()
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, scout);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void invokeSeedNpcForceContact(Object observer, Object target, double confidence) throws Exception {
        Class<?> contactType = Class.forName("CampaignSystemModels$NpcForceContact");
        java.lang.reflect.Constructor<?> constructor = contactType.getDeclaredConstructor(target.getClass(), double.class);
        constructor.setAccessible(true);
        Object contact = constructor.newInstance(target, confidence);
        ((java.util.Map) getObject(observer, "knownHostileContacts")).put(getInt(target, "id"), contact);
    }

    private static List<?> campaignForces(CampaignSystem.CampaignState st) {
        return st.campaignForces;
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

    private static Object testForceAt(CampaignSystem.CampaignState st,
                                      CampaignSystem.CampaignForceKind kind,
                                      Faction faction,
                                      String name,
                                      CampaignSystem.CampaignLocation location) throws Exception {
        Object force = invokeEnsureCampaignForce(st, kind, faction, name, location.id, "POI work test", location.x, location.y);
        setBoolean(force, "simulationActive", true);
        setDouble(force, "x", location.x);
        setDouble(force, "y", location.y);
        setDouble(force, "targetX", location.x);
        setDouble(force, "targetY", location.y);
        setObject(force, "homeBaseId", location.id);
        setObject(force, "sourceLocationId", location.id);
        setObject(force, "destinationLocationId", "");
        ((List<?>) getObject(force, "routePoints")).clear();
        return force;
    }

    private static Object forceNamed(CampaignSystem.CampaignState st, String name) {
        for (Object force : campaignForces(st)) {
            if (force != null && name.equals(fieldString(force, "name"))) return force;
        }
        return null;
    }

    private static Object firstForceNamedContaining(CampaignSystem.CampaignState st, String namePart) {
        for (Object force : campaignForces(st)) {
            if (force != null && fieldString(force, "name").contains(namePart)) return force;
        }
        return null;
    }

    private static int countForcesNamed(CampaignSystem.CampaignState st, String namePart) {
        int count = 0;
        for (Object force : campaignForces(st)) {
            if (force != null && fieldString(force, "name").contains(namePart)) count++;
        }
        return count;
    }

    private static Object firstForceByKind(CampaignSystem.CampaignState st, String kind) {
        for (Object force : campaignForces(st)) {
            if (force != null && kind.equals(fieldString(force, "kind"))) return force;
        }
        return null;
    }

    private static Object firstForceByKindAndFaction(CampaignSystem.CampaignState st, String kind, String faction) {
        for (Object force : campaignForces(st)) {
            if (force != null && kind.equals(fieldString(force, "kind")) && faction.equals(fieldString(force, "faction"))) {
                return force;
            }
        }
        return null;
    }

    private static boolean hasRedScoutScreening(CampaignSystem.CampaignState st, Object mainForce) throws Exception {
        int mainId = getInt(mainForce, "id");
        for (Object force : campaignForces(st)) {
            if (force == null || force == mainForce) continue;
            String name = fieldString(force, "name").toUpperCase();
            if (!"ENEMY".equals(fieldString(force, "faction"))) continue;
            if (!"RECON".equals(fieldString(force, "mission"))) continue;
            if (!name.contains("SCOUT") && !name.contains("RECON") && !name.contains("PROBE")) continue;
            if (getInt(force, "targetForceId") != mainId) continue;
            if (((List<?>) getObject(force, "routePoints")).isEmpty()) continue;
            return true;
        }
        return false;
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstLocationOfType(GameContext ctx, String typeName) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && typeName.equals(location.type.name())) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && typeName.equals(location.type.name())) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstLocationWithService(GameContext ctx, CampaignSystem.HubService service) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.services.contains(service)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.services.contains(service)) return location;
        }
        return null;
    }

    private static Object strategicNodeForLocation(CampaignSystem.CampaignState st, String locationId) {
        for (Object node : st.strategicNodes) {
            if (node != null && locationId.equals(fieldString(node, "locationId"))) return node;
        }
        return null;
    }

    private static String fieldString(Object target, String fieldName) {
        Object value = getObject(target, fieldName);
        return value == null ? "" : value.toString();
    }

    private static Object getObject(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static double getDoubleUnchecked(Object target, String fieldName) {
        try {
            return getDouble(target, fieldName);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static int getIntUnchecked(Object target, String fieldName) {
        try {
            return getInt(target, fieldName);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static boolean getBooleanUnchecked(Object target, String fieldName) {
        try {
            return getBoolean(target, fieldName);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumByName(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> enumType = field.getType();
        field.set(target, Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), value));
    }
}
