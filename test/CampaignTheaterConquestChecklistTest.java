import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignTheaterConquestChecklistTest {
    private static final Method UPDATE_STRATEGIC_OVERMAP_CAMPAIGN = declaredMethod(
            "updateStrategicOvermapCampaign",
            GameContext.class,
            CampaignSystem.CampaignState.class,
            double.class
    );

    @Test
    void theaterControlStateThresholdsMatchDesign() throws Exception {
        Object blue = invokePrivate(
                "theaterControlStateForScore",
                new Class[]{double.class},
                32.0
        );
        Object contested = invokePrivate(
                "theaterControlStateForScore",
                new Class[]{double.class},
                0.0
        );
        Object red = invokePrivate(
                "theaterControlStateForScore",
                new Class[]{double.class},
                -32.0
        );

        assertEquals("BLUE_GREEN_CONTROLLED", blue.toString());
        assertEquals("CONTESTED", contested.toString());
        assertEquals("RED_CONTROLLED", red.toString());
    }

    @Test
    void theaterBandsExposeRegionalInfluenceAtAGlance() {
        GameContext ctx = initCampaign();
        List<CampaignSystem.TheaterBand> bands = CampaignSystem.campaignTheaterBands(ctx);

        assertEquals(4, bands.size());
        assertInfluenceDominance(bands.get(0), "GREEN");
        assertInfluenceDominance(bands.get(1), "GREEN");
        assertTrue(bands.get(1).yellowInfluence > bands.get(1).redInfluence,
                "the split Yellow frontier should remain visibly Yellow-influenced while coalition and Red blocs contest it");
        assertInfluenceDominance(bands.get(2), "RED");
        assertInfluenceDominance(bands.get(3), "RED");
    }

    @Test
    void startingZoneInfluenceValuesMatchCampaignSpecification() throws Exception {
        Class<?> theaterIdClass = Class.forName("CampaignSystemModels$TheaterId");
        Object southern = Enum.valueOf((Class<Enum>) theaterIdClass.asSubclass(Enum.class), "SOUTHERN");
        Object frontier = Enum.valueOf((Class<Enum>) theaterIdClass.asSubclass(Enum.class), "FRONTIER");
        Object lunar = Enum.valueOf((Class<Enum>) theaterIdClass.asSubclass(Enum.class), "LUNAR");
        Object earth = Enum.valueOf((Class<Enum>) theaterIdClass.asSubclass(Enum.class), "EARTH");

        assertInfluenceSeed(southern, 85.0, 10.0, 5.0);
        assertInfluenceSeed(frontier, 25.0, 55.0, 20.0);
        assertInfluenceSeed(lunar, 5.0, 15.0, 80.0);
        assertInfluenceSeed(earth, 0.0, 5.0, 95.0);
    }

    @Test
    void influenceBandsMapToReadableControlLanguage() throws Exception {
        assertEquals("firm control", influenceBand(95.0));
        assertEquals("occupied/held", influenceBand(60.0));
        assertEquals("contested", influenceBand(40.0));
        assertEquals("losing control", influenceBand(20.0));
        assertEquals("nearly liberated/collapsed", influenceBand(5.0));
    }

    @Test
    void shipyardOwnershipAffectsTheaterSupplyState() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        Object shipyardNode = firstNodeByType(st, "SHIPYARD");
        assertNotNull(shipyardNode, "expected at least one shipyard strategic node");
        Object theaterId = getField(shipyardNode, "theaterId");

        setField(shipyardNode, "owner", enumConstant(fieldType(shipyardNode, "owner"), "RED"));
        invokePrivate("recomputeCampaignTheaterStates", new Class[]{CampaignSystem.CampaignState.class}, st);
        double redSupply = theaterSupplyState(st, theaterId);

        setField(shipyardNode, "owner", enumConstant(fieldType(shipyardNode, "owner"), "BLUE_GREEN"));
        invokePrivate("recomputeCampaignTheaterStates", new Class[]{CampaignSystem.CampaignState.class}, st);
        double blueSupply = theaterSupplyState(st, theaterId);

        assertTrue(blueSupply > redSupply, "friendly shipyard control should improve theater supply state");
    }

    @Test
    void lowIntegrityTaskForceTransitionsIntoRecoveryBehavior() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        Object force = firstNonPlayerCampaignForce(st);
        assertNotNull(force, "expected at least one autonomous campaign force");

        setDoubleField(force, "hullIntegrity", 20.0);
        setDoubleField(force, "readiness", 18.0);
        setDoubleField(force, "supply", 16.0);

        invokePrivate(
                "updateCampaignForceOrders",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, force.getClass(), double.class},
                ctx, st, force, 1.0
        );
        Object intent = getField(force, "intent");
        assertNotNull(intent);
        String name = intent.toString();
        assertTrue(name.equals("REPAIRING") || name.equals("REGROUPING") || name.equals("RETREATING"));
    }

    @Test
    void strategicOperationsModifyControlAndConsumeBlueReserve() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation current = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(current);
        st.selectedGalaxyLocationId = current.id;
        st.dockedGalaxyLocationId = current.id;
        st.playerGalaxyX = current.x;
        st.playerGalaxyY = current.y;

        Object node = strategicNodeForLocation(st, current.id);
        assertNotNull(node);
        double beforeProgress = getDoubleField(node, "contestProgress");
        double beforeReserve = st.blueInterventionReserve;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "OP_COMMAND_STRIKE"));
        double afterProgress = getDoubleField(node, "contestProgress");
        double afterReserve = st.blueInterventionReserve;

        assertTrue(afterProgress > beforeProgress, "command strike should push control toward Blue/Green");
        assertTrue(afterReserve < beforeReserve, "blue reserve should be consumed by strategic operations");
    }

    @Test
    void earthGateUnlockDependsOnStabilizedTheaters() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation earthGateMission = earthMission(ctx);
        assertNotNull(earthGateMission, "expected an Earth-phase mission node");
        st.selectedGalaxyLocationId = earthGateMission.id;
        st.playerGalaxyX = earthGateMission.x;
        st.playerGalaxyY = earthGateMission.y;
        st.dockedGalaxyLocationId = earthGateMission.id;

        boolean locked = (boolean) invokePrivate(
                "earthPhaseUnlocked",
                new Class[]{CampaignSystem.CampaignState.class},
                st
        );
        assertFalse(locked, "earth phase should stay locked by default");

        forceBlueControlOnFirstTheaters(st, 2);
        boolean unlocked = (boolean) invokePrivate(
                "earthPhaseUnlocked",
                new Class[]{CampaignSystem.CampaignState.class},
                st
        );
        assertTrue(unlocked, "earth phase should unlock after two stabilized theaters");
    }

    @Test
    void earthBossStaysInNorthernRedCoreAndCompatibilityPath() {
        GameContext ctx = initCampaign();
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation earthGateMission = earthMission(ctx);

        assertNotNull(earthGateMission, "expected an Earth-phase mission node");
        assertEquals("poi-24", earthGateMission.id, "legacy final POI should remain as the compatibility path");
        assertEquals("poi-24", earthGateMission.legacyPoiId);
        assertEquals("EARTH", earthGateMission.zoneId);
        assertEquals(CampaignSystem.CampaignFacilityType.BOSS_STAGING_AREA, earthGateMission.facilityType);
        assertEquals(Faction.ENEMY, earthGateMission.ownerFaction);
        assertEquals(CampaignSystem.CampaignControlVisualState.RED, earthGateMission.controlState);
        assertEquals(5, earthGateMission.strategicValue);
    }

    @Test
    void factionMissionBoardsGenerateFromFacilitiesAndRewardWarProgress() {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        List<CampaignSystem.CampaignMissionBoardEntry> green = CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C);
        List<CampaignSystem.CampaignMissionBoardEntry> yellow = CampaignSystem.campaignFactionMissionBoard(ctx, Faction.BRIGHT_YELLOW);

        assertTrue(green.size() >= 3, "Green board should produce multiple facility/fleet-driven missions");
        assertTrue(yellow.size() >= 3, "Yellow board should produce multiple facility/fleet-driven missions");
        assertTrue(green.stream().anyMatch(entry -> entry.reward.contains("Green Reputation")));
        assertTrue(yellow.stream().anyMatch(entry -> entry.reward.contains("Yellow Reputation")));

        CampaignSystem.CampaignMissionBoardEntry entry = green.stream()
                .filter(candidate -> candidate.family.contains("attack") || candidate.family.contains("capture"))
                .findFirst()
                .orElse(green.get(0));
        CampaignSystem.CampaignLocation target = findLocationById(ctx, entry.targetLocationId);
        assertNotNull(target);
        Faction beforeOwner = target.ownerFaction;
        int favorBefore = st.greenContractFavor;
        int supportBefore = supportForceCount(st, Faction.TEAM_C);

        assertTrue(CampaignSystem.completeCampaignBoardMission(ctx, entry.id));

        assertTrue(st.greenContractFavor > favorBefore, "Green mission should award reputation");
        assertTrue(CampaignSystem.campaignRedHostility(ctx) > 0, "anti-Red mission should increase hostility");
        if (beforeOwner == Faction.ENEMY) {
            assertEquals(Faction.TEAM_C, target.ownerFaction, "facility assault should flip ownership");
        }
        assertTrue(supportForceCount(st, Faction.TEAM_C) > supportBefore,
                "first support threshold should visibly commit an allied force");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("OWNERSHIP SHIFT")
                        || line.contains("SUPPORT COMMITTED")),
                "board outcome should leave a visible theater event");
    }

    @Test
    void facilityIntelControlsMissionBoardVisibility() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation redFacility = findLocationById(ctx, "poi-19");
        assertNotNull(redFacility);

        redFacility.intelLevel = CampaignSystem.CampaignIntelLevel.UNKNOWN;
        setField(redFacility, "intelQuality", enumConstant(fieldType(redFacility, "intelQuality"), "UNKNOWN"));
        List<CampaignSystem.CampaignMissionBoardEntry> hiddenBoard = CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C);
        assertFalse(hiddenBoard.stream().anyMatch(entry -> redFacility.id.equals(entry.targetLocationId)),
                "unknown facilities should not generate precise board strikes");

        st.playerGalaxyX = redFacility.x;
        st.playerGalaxyY = redFacility.y;
        st.campaignSupplies = 20;
        st.campaignIntelLevel = 75.0;
        assertTrue(CampaignSystem.requestCampaignTrafficAudit(ctx));

        List<CampaignSystem.CampaignMissionBoardEntry> revealedBoard = CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C);
        assertTrue(revealedBoard.stream().anyMatch(entry -> redFacility.id.equals(entry.targetLocationId)),
                "improved intel should unlock missions against the facility");
        assertTrue(redFacility.intelLevel.ordinal() >= CampaignSystem.CampaignIntelLevel.GOOD.ordinal());
    }

    @Test
    void intelSourceReadoutCoversAllDiscoveryChannels() {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        st.greenContractFavor = 6;
        st.yellowLiberationFavor = 6;

        List<String> sources = CampaignSystem.campaignIntelSourceLines(ctx);

        assertIntelSource(sources, "Recon flights");
        assertIntelSource(sources, "Friendly relays");
        assertIntelSource(sources, "Sensor towers");
        assertIntelSource(sources, "Allied reputation");
        assertIntelSource(sources, "Patrol fleets");
        assertIntelSource(sources, "Listening posts");
        assertIntelSource(sources, "Captured facilities");
        assertIntelSource(sources, "Rescued civilians or prisoners");
        assertIntelSource(sources, "Yellow rebel reports");
        assertIntelSource(sources, "Green command briefings");
        assertTrue(CampaignSystem.campaignWarSupportReadoutLines(ctx).stream()
                .anyMatch(line -> line.contains("Intel sources")));
    }

    @Test
    void facilityOperationalFieldsAndSidebarRespectIntelLevels() {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation facility = findLocationById(ctx, "poi-20");
        assertNotNull(facility);

        assertTrue(facility.defenseStrength > 0.0);
        assertTrue(facility.resourceValue > 0.0);
        assertFalse(facility.missionTags.isEmpty());
        assertFalse(facility.displayLabel.isBlank());
        assertFalse(facility.longDetail.isBlank());
        assertEquals(CampaignSystem.CampaignControlVisualState.RED, facility.controlState);

        st.selectedGalaxyLocationId = facility.id;
        facility.intelLevel = CampaignSystem.CampaignIntelLevel.UNKNOWN;
        List<String> unknownLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(unknownLines.stream().anyMatch(line -> line.contains("Unknown contact")));
        assertTrue(unknownLines.stream().anyMatch(line -> line.contains("Defense: unknown")));

        facility.intelLevel = CampaignSystem.CampaignIntelLevel.PARTIAL;
        List<String> partialLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(partialLines.stream().anyMatch(line -> line.contains("Probable")));
        assertTrue(partialLines.stream().anyMatch(line -> line.contains("Hooks hidden")));

        facility.intelLevel = CampaignSystem.CampaignIntelLevel.FULL;
        List<String> fullLines = CampaignSystem.selectedLocationSidebarLines(ctx);
        assertTrue(fullLines.stream().anyMatch(line -> line.contains("Garrison:")));
        assertTrue(fullLines.stream().anyMatch(line -> line.contains("Strategic 5/5") || line.contains("Strategic 4/5")));
    }

    @Test
    void missionBoardStateTracksCompletedAndIgnoredUrgentMissions() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        CampaignSystem.CampaignMissionBoardEntry complete = CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C).get(0);
        assertTrue(CampaignSystem.completeCampaignBoardMission(ctx, complete.id));
        assertTrue(st.completedCampaignBoardMissionIds.contains(complete.id));
        assertFalse(CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C).stream()
                .anyMatch(entry -> entry.id.equals(complete.id)));

        for (Object theater : st.campaignTheaters) {
            setDoubleField(theater, "redInfluence", 70.0);
            setDoubleField(theater, "greenInfluence", 20.0);
            setDoubleField(theater, "yellowInfluence", 10.0);
        }

        CampaignSystem.CampaignMissionBoardEntry urgent = CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C).stream()
                .filter(entry -> "urgent".equalsIgnoreCase(entry.timePressure))
                .findFirst()
                .orElseThrow();
        CampaignSystem.CampaignLocation urgentTarget = findLocationById(ctx, urgent.targetLocationId);
        assertNotNull(urgentTarget);
        urgentTarget.escalationStage = 2;
        double alertBefore = st.enemyAlertLevel;
        st.theaterWarTickIndex = 3;
        invokePrivate("updateCampaignBoardUrgencies",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);

        assertTrue(st.expiredCampaignBoardMissionIds.contains(urgent.id));
        assertTrue(st.enemyAlertLevel > alertBefore);
        assertFalse(CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_C).stream()
                .anyMatch(entry -> entry.id.equals(urgent.id)));
    }

    @Test
    void redEscalationAndFinalReadinessReactToCampaignProgress() {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        CampaignSystem.CampaignFinalBattleReadiness before = CampaignSystem.campaignFinalBattleReadiness(ctx);
        st.greenContractFavor = 8;
        st.yellowLiberationFavor = 6;
        st.enemyAlertLevel = 72.0;
        CampaignSystem.CampaignLocation redShipyard = findLocationById(ctx, "poi-20");
        CampaignSystem.CampaignLocation redRelay = findLocationById(ctx, "poi-21");
        CampaignSystem.CampaignLocation redFortress = findLocationById(ctx, "poi-24");
        CampaignSystem.CampaignLocation yellowHub = findLocationById(ctx, "poi-09");
        assertNotNull(redShipyard);
        assertNotNull(redRelay);
        assertNotNull(redFortress);
        assertNotNull(yellowHub);
        redShipyard.ownerFaction = Faction.TEAM_C;
        redRelay.ownerFaction = Faction.TEAM_C;
        redFortress.destroyed = true;
        yellowHub.ownerFaction = Faction.BRIGHT_YELLOW;

        CampaignSystem.CampaignFinalBattleReadiness after = CampaignSystem.campaignFinalBattleReadiness(ctx);

        assertTrue(after.readinessScore >= before.readinessScore,
                "captured shipyards, relays, and support should not reduce Earth readiness");
        assertTrue(after.greenSupport > before.greenSupport);
        assertTrue(after.yellowSupport > before.yellowSupport);
        assertTrue(after.capturedShipyards > before.capturedShipyards);
        assertTrue(after.capturedRelays > before.capturedRelays);
        assertTrue(after.lines.stream().anyMatch(line -> line.contains("Shipyards")));
        assertTrue(after.lines.stream().anyMatch(line -> line.contains("Green capital support")));
        assertTrue(after.lines.stream().anyMatch(line -> line.contains("Yellow rebel sabotage")));
        assertTrue(after.lines.stream().anyMatch(line -> line.contains("Player fleet readiness")));
        assertTrue(after.lines.stream().anyMatch(line -> line.contains("coalition mass")
                && line.contains("immovable Earthfall core")));
        assertTrue(after.lines.stream().anyMatch(line -> line.contains("Red core defenses remain scary")));
        assertTrue(CampaignSystem.campaignWarSupportReadoutLines(ctx).stream()
                .anyMatch(line -> line.contains("capital support")
                        || line.contains("sabotage cells")
                        || line.contains("final battle coalition commitment")));
        assertTrue(CampaignSystem.campaignEnemyEscalationLines(ctx).stream()
                        .anyMatch(line -> line.contains("hunter")
                                || line.contains("counter")
                                || line.contains("reinforcement")
                                || line.contains("assassin")
                                || line.contains("fortress")),
                "high hostility should expose Red escalation behavior");
    }

    @Test
    void finalEarthBattleStagesMassiveAlliedFleetAgainstRedCore() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        int shipsBefore = ctx.ships.size();

        invokePrivate("spawnSector24", new Class[]{GameContext.class, CampaignSystem.CampaignState.class}, ctx, st);

        int alliedCapitalShips = 0;
        int alliedTotal = 0;
        int earthfallCore = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive) continue;
            boolean spawnedNow = ctx.ships.indexOf(ship) >= shipsBefore;
            if (!spawnedNow) continue;
            String name = ship.name == null ? "" : ship.name;
            if (ship.faction == Faction.TEAM_C || ship.faction == Faction.ALLY || ship.faction == Faction.BRIGHT_YELLOW) {
                alliedTotal++;
                if (name.contains("Titan") || name.contains("Battlecruiser") || name.contains("Cruiser")) {
                    alliedCapitalShips++;
                }
            }
            if (ship.faction == Faction.ENEMY && (name.contains("Earthfall") || name.contains("MOTHERSHIP"))) {
                earthfallCore++;
            }
        }

        assertTrue(st.bossTargetId > 0, "final battle should assign the Earthfall boss target");
        assertTrue(alliedTotal >= 7, "late-game player side should stage a visibly large allied fleet");
        assertTrue(alliedCapitalShips >= 5, "final push should include capital reinforcements, not only escorts");
        assertTrue(earthfallCore >= 3, "Earthfall core should stage as an immovable Red command group");
    }

    @Test
    void routeSanitizerRemovesInvalidForceRoutePointsAfterLoad() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        Object force = firstNonPlayerCampaignForce(st);
        assertNotNull(force);

        @SuppressWarnings("unchecked")
        List<double[]> route = (List<double[]>) getField(force, "routePoints");
        route.clear();
        route.add(new double[]{Double.NaN, 10.0});
        route.add(new double[]{ctx.WORLD_W + 5000.0, ctx.WORLD_H + 7000.0});

        invokePrivate("sanitizeCampaignForceRoutesAfterLoad",
                new Class[]{CampaignSystem.CampaignState.class, GameContext.class},
                st, ctx);

        @SuppressWarnings("unchecked")
        List<double[]> sanitized = (List<double[]>) getField(force, "routePoints");
        assertFalse(sanitized.isEmpty());
        for (double[] point : sanitized) {
            assertNotNull(point);
            assertEquals(2, point.length);
            assertTrue(Double.isFinite(point[0]) && Double.isFinite(point[1]));
            assertTrue(point[0] >= 0.0 && point[0] <= ctx.WORLD_W);
            assertTrue(point[1] >= 0.0 && point[1] <= ctx.WORLD_H);
        }
    }

    @Test
    void campaignRouteSegmentsReplaceOldLinearPoiSpine() {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation selected = findLocationById(ctx, "poi-12");
        assertNotNull(selected);
        st.selectedGalaxyLocationId = selected.id;

        List<CampaignSystem.CampaignRouteSegment> segments = CampaignSystem.campaignRouteSegments(ctx);
        assertSame(segments, CampaignSystem.campaignRouteSegments(ctx),
                "route segment layout should be cached while route-relevant state is unchanged");
        EnumSet<CampaignSystem.CampaignRouteSegmentKind> kinds = EnumSet.noneOf(CampaignSystem.CampaignRouteSegmentKind.class);
        int oldSpineLinks = 0;
        for (CampaignSystem.CampaignRouteSegment segment : segments) {
            assertNotNull(segment);
            assertTrue(Double.isFinite(segment.fromX) && Double.isFinite(segment.fromY));
            assertTrue(Double.isFinite(segment.toX) && Double.isFinite(segment.toY));
            assertTrue(segment.danger >= 0.0 && segment.danger <= 100.0);
            kinds.add(segment.kind);
            if (isOldSequentialPoiLink(segment)) oldSpineLinks++;
        }

        assertTrue(kinds.contains(CampaignSystem.CampaignRouteSegmentKind.LOCAL_ZONE));
        assertTrue(kinds.contains(CampaignSystem.CampaignRouteSegmentKind.SUPPLY_LINE));
        assertTrue(kinds.contains(CampaignSystem.CampaignRouteSegmentKind.CONTESTED_LANE));
        assertTrue(kinds.contains(CampaignSystem.CampaignRouteSegmentKind.BLOCKADE_LINE));
        assertTrue(kinds.contains(CampaignSystem.CampaignRouteSegmentKind.PLAYER_PLOTTED));
        assertTrue(oldSpineLinks < 10, "route graph should no longer be the old poi-01 through poi-24 spine");

        st.selectedGalaxyLocationId = "poi-18";
        assertNotSame(segments, CampaignSystem.campaignRouteSegments(ctx),
                "changing the selected route target should invalidate route segment cache");
    }

    @Test
    void theaterWarLongRunStaysStableAndBounded() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        for (int i = 0; i < 420; i++) {
            invokePrivate("updateCampaignTheaterWar",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    ctx, st, 1.0);
        }

        assertTrue(st.theaterWarRecentEvents.size() <= 16);
        for (Object theater : st.campaignTheaters) {
            assertTrue(getDoubleField(theater, "controlScore") >= -100.0);
            assertTrue(getDoubleField(theater, "controlScore") <= 100.0);
            assertTrue(getDoubleField(theater, "supplyState") >= 0.0);
            assertTrue(getDoubleField(theater, "supplyState") <= 100.0);
            assertTrue(getDoubleField(theater, "threatPressure") >= 0.0);
            assertTrue(getDoubleField(theater, "threatPressure") <= 100.0);
            assertTrue(getDoubleField(theater, "greenInfluence") >= 0.0);
            assertTrue(getDoubleField(theater, "greenInfluence") <= 100.0);
            assertTrue(getDoubleField(theater, "yellowInfluence") >= 0.0);
            assertTrue(getDoubleField(theater, "yellowInfluence") <= 100.0);
            assertTrue(getDoubleField(theater, "redInfluence") >= 0.0);
            assertTrue(getDoubleField(theater, "redInfluence") <= 100.0);
        }
    }

    @Test
    void theaterWarReplayIsDeterministicForFixedSeed() throws Exception {
        GameContext a = initCampaign();
        GameContext b = initCampaign();
        bootOvermap(a);
        bootOvermap(b);

        for (int i = 0; i < 240; i++) {
            invokePrivate("updateCampaignTheaterWar",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    a, a.campaign, 1.0);
            invokePrivate("updateCampaignTheaterWar",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    b, b.campaign, 1.0);
        }

        assertEquals(theaterSnapshot(a.campaign), theaterSnapshot(b.campaign));
        assertEquals(nodeSnapshot(a.campaign), nodeSnapshot(b.campaign));
    }

    @Test
    void highContactStrategicUpdateRemainsWithinReasonableBudget() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        long t0 = System.nanoTime();
        for (int i = 0; i < 12; i++) {
            UPDATE_STRATEGIC_OVERMAP_CAMPAIGN.invoke(null, ctx, st, 0.5);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(elapsedMs < 5000, "six-second strategic overmap smoke regressed: " + elapsedMs + "ms");
    }

    private static String theaterSnapshot(CampaignSystem.CampaignState st) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Object theater : st.campaignTheaters) {
            if (theater == null) continue;
            sb.append(getField(theater, "id")).append('|')
                    .append((int) Math.round(getDoubleField(theater, "controlScore"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "supplyState"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "threatPressure"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "greenInfluence"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "yellowInfluence"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "redInfluence"))).append('|')
                    .append(getField(theater, "controlState")).append(';');
        }
        return sb.toString();
    }

    private static void assertInfluenceSeed(Object theaterId,
                                            double green,
                                            double yellow,
                                            double red) throws Exception {
        assertEquals(green, invokePrivate("startingGreenInfluence", new Class[]{theaterId.getClass()}, theaterId));
        assertEquals(yellow, invokePrivate("startingYellowInfluence", new Class[]{theaterId.getClass()}, theaterId));
        assertEquals(red, invokePrivate("startingRedInfluence", new Class[]{theaterId.getClass()}, theaterId));
    }

    private static String influenceBand(double value) {
        return CampaignSystem.campaignInfluenceBandLabel(value);
    }

    private static void assertInfluenceDominance(CampaignSystem.TheaterBand band, String dominant) {
        assertNotNull(band);
        assertTrue(band.greenInfluence >= 0.0 && band.greenInfluence <= 100.0);
        assertTrue(band.yellowInfluence >= 0.0 && band.yellowInfluence <= 100.0);
        assertTrue(band.redInfluence >= 0.0 && band.redInfluence <= 100.0);
        double total = band.greenInfluence + band.yellowInfluence + band.redInfluence;
        String values = "green=" + band.greenInfluence + " yellow=" + band.yellowInfluence + " red=" + band.redInfluence;
        assertEquals(100.0, total, 0.75);
        switch (dominant) {
            case "GREEN" -> assertTrue(band.greenInfluence > band.yellowInfluence && band.greenInfluence > band.redInfluence, values);
            case "YELLOW" -> assertTrue(band.yellowInfluence > band.greenInfluence && band.yellowInfluence > band.redInfluence, values);
            case "RED" -> assertTrue(band.redInfluence > band.greenInfluence && band.redInfluence > band.yellowInfluence, values);
            default -> throw new AssertionError("unknown dominant influence " + dominant);
        }
    }

    private static void assertIntelSource(List<String> lines, String label) {
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(label + "  |")),
                "missing intel source label: " + label);
    }

    private static String nodeSnapshot(CampaignSystem.CampaignState st) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Object node : st.strategicNodes) {
            if (node == null) continue;
            sb.append(getField(node, "locationId")).append('|')
                    .append(getField(node, "owner")).append('|')
                    .append((int) Math.round(getDoubleField(node, "contestProgress"))).append(';');
        }
        return sb.toString();
    }

    private static CampaignSystem.CampaignLocation firstFacilityOwnedBy(CampaignSystem.CampaignState st, Faction faction) {
        return firstFacilityOwnedBy(st, faction, "");
    }

    private static CampaignSystem.CampaignLocation firstFacilityOwnedBy(CampaignSystem.CampaignState st,
                                                                         Faction faction,
                                                                         String excludedLocationId) {
        for (CampaignSystem.CampaignLocation location : st.galaxyMainPois) {
            if (location != null && location.ownerFaction == faction && !location.destroyed
                    && !location.id.equals(excludedLocationId)) return location;
        }
        for (CampaignSystem.CampaignLocation location : st.galaxyAreasOfInterest) {
            if (location != null && location.ownerFaction == faction && !location.destroyed
                    && !location.id.equals(excludedLocationId)) return location;
        }
        return null;
    }

    private static Object theaterForLocation(GameContext ctx,
                                             CampaignSystem.CampaignState st,
                                             CampaignSystem.CampaignLocation location) throws Exception {
        for (Object theater : st.campaignTheaters) {
            double min = getDoubleField(theater, "minYNorm");
            double max = getDoubleField(theater, "maxYNorm");
            double yNorm = location.y / Math.max(1.0, ctx.WORLD_H);
            if (yNorm >= min && yNorm <= max) return theater;
        }
        return null;
    }

    private static boolean isOldSequentialPoiLink(CampaignSystem.CampaignRouteSegment segment) {
        if (segment == null) return false;
        int a = poiNumber(segment.fromLocationId);
        int b = poiNumber(segment.toLocationId);
        return a > 0 && b > 0 && Math.abs(a - b) == 1;
    }

    private static int poiNumber(String id) {
        if (id == null || !id.startsWith("poi-")) return -1;
        try {
            return Integer.parseInt(id.substring(4));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static GameContext initCampaign() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9876L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void bootOvermap(GameContext ctx) {
        CampaignSystem.campaignSummarySidebarLines(ctx);
        assertNotNull(ctx.campaign);
    }

    private static CampaignSystem.CampaignLocation earthMission(GameContext ctx) {
        CampaignSystem.CampaignLocation best = null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || !location.primaryMission) continue;
            if (location.missionIndex >= 23) {
                if (best == null || location.missionIndex > best.missionIndex) {
                    best = location;
                }
            }
        }
        return best;
    }

    private static CampaignSystem.CampaignLocation findLocationById(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.id.equals(id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.id.equals(id)) return location;
        }
        return null;
    }

    private static int supportForceCount(CampaignSystem.CampaignState st, Faction faction) {
        int count = 0;
        for (Object force : st.campaignForces) {
            try {
                if (force != null
                        && faction.toString().equals(String.valueOf(getField(force, "faction")))
                        && String.valueOf(getField(force, "name")).contains("Support")) {
                    count++;
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return count;
    }

    private static void forceBlueControlOnFirstTheaters(CampaignSystem.CampaignState st, int count) throws Exception {
        int done = 0;
        for (Object theater : st.campaignTheaters) {
            if (theater == null) continue;
            setField(theater, "controlState", enumConstant(fieldType(theater, "controlState"), "BLUE_GREEN_CONTROLLED"));
            setDoubleField(theater, "controlScore", 58.0);
            setDoubleField(theater, "greenInfluence", 70.0);
            setDoubleField(theater, "yellowInfluence", 20.0);
            setDoubleField(theater, "redInfluence", 10.0);
            done++;
            if (done >= count) break;
        }
    }

    private static Object firstNodeByType(CampaignSystem.CampaignState st, String typeName) throws Exception {
        for (Object node : st.strategicNodes) {
            if (node == null) continue;
            Object type = getField(node, "type");
            if (type != null && typeName.equals(type.toString())) return node;
        }
        return null;
    }

    private static Object strategicNodeForLocation(CampaignSystem.CampaignState st, String locationId) throws Exception {
        for (Object node : st.strategicNodes) {
            if (node == null) continue;
            Object id = getField(node, "locationId");
            if (id != null && id.toString().equalsIgnoreCase(locationId)) return node;
        }
        return null;
    }

    private static double theaterSupplyState(CampaignSystem.CampaignState st, Object theaterId) throws Exception {
        for (Object theater : st.campaignTheaters) {
            if (theater == null) continue;
            Object id = getField(theater, "id");
            if (id != null && id.equals(theaterId)) {
                return getDoubleField(theater, "supplyState");
            }
        }
        return 0.0;
    }

    private static Object firstNonPlayerCampaignForce(CampaignSystem.CampaignState st) throws Exception {
        for (Object force : st.campaignForces) {
            if (force == null) continue;
            Object kind = getField(force, "kind");
            if (kind != null && !"PLAYER_FLEET".equals(kind.toString())) return force;
        }
        return null;
    }

    private static Object invokePrivate(String methodName, Class<?>[] sig, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(methodName, sig);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Method declaredMethod(String methodName, Class<?>... signature) {
        try {
            Method method = CampaignSystem.class.getDeclaredMethod(methodName, signature);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Class<?> fieldType(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getType();
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static double getDoubleField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static void setDoubleField(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }
}
