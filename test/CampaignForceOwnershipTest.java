import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignForceOwnershipTest {

    @Test
    void campaignShipsAreAssignedToNamedForcesAndIntroAttackHasOrigin() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.update(ctx, 0.1);
        assertFalse(CampaignSystem.campaignForceSummaries(ctx).isEmpty());
        assertEquals(liveShipCount(ctx), st.shipCampaignForceIds.size());
        assertEquals(0, liveHostileShipCountNearPlayer(ctx, 1200.0));

        invokeIntroRedDetachment(ctx, st);

        assertTrue(campaignForceFieldContains(st, "name", "Red Knife Advance Detachment"));
        assertTrue(campaignForceFieldContains(st, "origin", "Detected warp signature"));
        assertEquals(liveShipCount(ctx), st.shipCampaignForceIds.size());
    }

    @Test
    void tabOpensPersistentFleetManagementInsideCampaignBattlefield() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.introSequenceActive = false;
        ctx.campaign.awaitingFleetHubChoice = false;
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.galaxyEncounterActive = true;
        ctx.ui.shopOpen = false;
        ctx.ui.mapOpen = false;
        ctx.state = GameState.RUNNING;

        GameplayActions.toggleShop(ctx);

        assertTrue(ctx.ui.shopOpen);
        assertEquals(GameState.SHOP, ctx.state);
        assertFalse(ctx.ui.mapOpen);
    }

    @Test
    void checkpointRoundTripPreservesCampaignForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.update(ctx, 0.1);
        CampaignSystem.update(ctx, 5.0);
        CampaignSystem.update(ctx, 2.2);
        assertFalse(CampaignSystem.campaignForceSummaries(ctx).isEmpty());

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 2);
        assertFalse(checkpoint.campaignForces.isBlank());
        assertFalse(checkpoint.shipCampaignForceIds.isBlank());

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));

        CampaignSystem.CampaignState restoredState = restored.campaign;
        assertTrue(restoredState.nextCampaignForceId >= st.nextCampaignForceId);
        assertEquals(liveShipCount(restored), restoredState.shipCampaignForceIds.size());
        assertFalse(checkpoint.campaignForces.isBlank());
        assertFalse(checkpoint.shipCampaignForceIds.isBlank());
    }

    @Test
    void olderCheckpointWithoutForceRegistryMigratesIntoOwnedLiveShips() throws Exception {
        GameContext ctx = initializedCampaignContext();

        CampaignSystem.update(ctx, 0.1);

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 2);
        checkpoint.campaignForces = "";
        checkpoint.shipCampaignForceIds = "";
        checkpoint.nextCampaignForceId = 1;

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));

        CampaignSystem.CampaignState restoredState = restored.campaign;
        assertFalse(CampaignSystem.campaignForceSummaries(restored).isEmpty());
        assertEquals(liveShipCount(restored), restoredState.shipCampaignForceIds.size());
        assertTrue(restoredState.nextCampaignForceId > 1);
    }

    @Test
    void authoredCampaignSpawnHelperProducesNoForceLessOrFallbackOwnedShips() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.update(ctx, 0.1);
        st.fallbackOwnedShipIds.clear();
        st.campaignForceAuditWarnings.clear();

        Method spawnCampaignPatrolBand = CampaignSystem.class.getDeclaredMethod(
                "spawnCampaignPatrolBand",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class,
                double.class,
                int.class
        );
        spawnCampaignPatrolBand.setAccessible(true);
        spawnCampaignPatrolBand.invoke(null, ctx, st, ctx.player.x + 900.0, ctx.player.y + 120.0, 1);

        assertTrue(CampaignSystem.campaignForceAuditReport(ctx).stream()
                .noneMatch(line -> line.contains("FORCELESS") || line.contains("campaign_patrol_band")));
    }

    @Test
    void auditReportFlagsFallbackOwnedShipsBySpawnCategory() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.update(ctx, 0.1);
        st.fallbackOwnedShipIds.clear();
        st.campaignForceAuditWarnings.clear();

        Ship rogue = SpawnSystem.spawnEnemy(ctx, ShipRole.PATROL, ctx.player.x + 700.0, ctx.player.y + 60.0);
        assertTrue(rogue != null && rogue.id > 0);

        assertTrue(CampaignSystem.campaignForceAuditReport(ctx).stream()
                .anyMatch(line -> line.contains("FALLBACK-OWNED category=unknown_callsite")));
        assertTrue(st.campaignForceAuditWarnings.stream()
                .anyMatch(line -> line.contains("category=unknown_callsite")));
    }

    @Test
    void oldCampaignSectorOpenersDoNotCreateFallbackOwnedShips() throws Exception {
        for (int sector = 2; sector <= 24; sector++) {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;
            st.fallbackOwnedShipIds.clear();
            st.campaignForceAuditWarnings.clear();

            startSector(ctx, sector);

            assertTrue(CampaignSystem.campaignForceAuditReport(ctx).stream()
                            .noneMatch(line -> line.contains("FORCELESS") || line.contains("FALLBACK-OWNED")),
                    "expected sector " + sector + " opener to assign all spawned ships to non-fallback campaign forces");
        }
    }

    @Test
    void campaignEncounterSetupPathsDoNotCreateFallbackOwnedShips() throws Exception {
        GameContext friendlySeed = initializedCampaignContext();
        for (CampaignSystem.CampaignLocation location : friendlyInstallationLocations(friendlySeed)) {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;
            st.fallbackOwnedShipIds.clear();
            st.campaignForceAuditWarnings.clear();
            prepareAmbientEncounter(ctx, st, location);
            assertTrue(CampaignSystem.campaignForceAuditReport(ctx).stream()
                            .noneMatch(line -> line.contains("FORCELESS") || line.contains("FALLBACK-OWNED")),
                    "expected friendly ambient setup to avoid fallback ownership for " + location.name);
        }

        GameContext hostileCtx = initializedCampaignContext();
        CampaignSystem.CampaignState hostileState = hostileCtx.campaign;
        hostileState.fallbackOwnedShipIds.clear();
        hostileState.campaignForceAuditWarnings.clear();
        Object group = firstGalaxySearchGroup(hostileState);
        assertTrue(group != null, "expected a seeded hostile search group");
        prepareSearchGroupEncounter(hostileCtx, hostileState, group);
        assertTrue(CampaignSystem.campaignForceAuditReport(hostileCtx).stream()
                        .noneMatch(line -> line.contains("FORCELESS") || line.contains("FALLBACK-OWNED")),
                "expected hostile search-group setup to avoid fallback ownership");
    }

    @Test
    void friendlyHubAmbientEncounterUsesNamedDefenseTrafficAndMiningForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");

        prepareAmbientEncounter(ctx, st, location);

        assertTrue(campaignForceFieldContains(st, "name", location.name + " Defense Force"));
        assertTrue(campaignForceFieldContains(st, "name", location.name + " Prospecting Group"));
        assertTrue(campaignForceContainsAny(st,
                location.name + " Service Traffic Group",
                location.name + " Trade Traffic Group"));
    }

    @Test
    void friendlyHubAmbientEncounterStaysHostileFreeWithoutTrackedThreat() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");

        prepareAmbientEncounter(ctx, st, location);

        assertEquals(0, liveHostileShipCount(ctx),
                "friendly installation encounters should not inject unexplained hostile ships");
    }

    @Test
    void allFriendlyInstallationsStayHostileFreeInAmbientEncounters() throws Exception {
        GameContext seed = initializedCampaignContext();
        List<CampaignSystem.CampaignLocation> locations = friendlyInstallationLocations(seed);
        assertFalse(locations.isEmpty(), "expected at least one friendly installation location");

        for (CampaignSystem.CampaignLocation location : locations) {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;

            prepareAmbientEncounter(ctx, st, location);

            assertEquals(0, liveHostileShipCount(ctx),
                    "friendly installation encounter should stay hostile-free for " + location.name);
        }
    }

    @Test
    void secureFriendlyHubsDoNotSpawnRandomHostilePatrolBands() throws Exception {
        GameContext seed = initializedCampaignContext();
        for (CampaignSystem.CampaignLocation location : friendlyInstallationLocations(seed)) {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;
            prepareAmbientEncounter(ctx, st, location);

            assertTrue(shipSpawnCategories(st).stream().noneMatch("campaign_patrol_band"::equals),
                    "expected secure hub " + location.name + " to avoid hostile patrol-band spawning");
            assertTrue(CampaignSystem.campaignForceAuditReport(ctx).stream()
                            .noneMatch(line -> line.contains("Red Route Patrol Band")),
                    "expected secure hub " + location.name + " to avoid route-patrol hostile forces");
        }
    }

    @Test
    void friendlyInstallationArrivalUsesTrackedNearbyHostileForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");
        st.selectedGalaxyLocationId = location.id;

        int threatGroupId = stageTrackedThreatNearInstallation(ctx, st, location);
        beginCampaignArrivalEncounterChoice(ctx, st, location);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(threatGroupId, ctx.ui.strategicEncounterPrompt.galaxySearchGroupId);
        assertTrue(ctx.ui.strategicEncounterPrompt.title.contains("INSTALLATION THREAT"));
        assertTrue(ctx.ui.strategicEncounterPrompt.location.contains(location.name));
    }

    @Test
    void selectedHubIdentityLinesExposeTrackedNearbyHostileForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");
        st.selectedGalaxyLocationId = location.id;

        stageTrackedThreatNearInstallation(ctx, st, location);

        List<String> lines = CampaignSystem.selectedHubIdentityLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Threat Alert: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Hostile Provenance: ")));
    }

    @Test
    void scriptedInstallationThreatUsesDedicatedPromptAndNamedForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");

        stageScriptedInstallationThreat(st, location,
                "Customs Halo Saboteurs",
                "Yellow Exchange Ilex",
                "Hidden raiders slipped inside the harbor approach and are striking service traffic.",
                0.58);

        beginCampaignArrivalEncounterChoice(ctx, st, location);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.INSTALLATION_THREAT, ctx.ui.strategicEncounterPrompt.kind);
        assertTrue(ctx.ui.strategicEncounterPrompt.title.contains("INFILTRATION ALERT"));
        assertTrue(ctx.ui.strategicEncounterPrompt.location.contains("Customs Halo Saboteurs"));

        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertTrue(liveHostileShipCount(ctx) > 0, "scripted installation threat should spawn hostile defenders in the local pocket");
        assertTrue(campaignForceFieldContains(st, "name", "Customs Halo Saboteurs"));
    }

    @Test
    void selectedHubIdentityLinesExposeScriptedInstallationThreat() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");
        st.selectedGalaxyLocationId = location.id;

        stageScriptedInstallationThreat(st, location,
                "Anchorage Ghost Cell",
                "Contract Repair Anchorage",
                "A hidden hostile cell has been seen probing the anchorage perimeter.",
                0.32);

        List<String> lines = CampaignSystem.selectedHubIdentityLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.contains("Anchorage Ghost Cell")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Contract Repair Anchorage")));
    }

    @Test
    void searchGroupEncounterShipsStayOwnedBySearchGroupForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.update(ctx, 0.1);
        st.fallbackOwnedShipIds.clear();
        st.campaignForceAuditWarnings.clear();
        Object group = firstGalaxySearchGroup(st);
        assertTrue(group != null, "expected a seeded hostile search group");

        prepareSearchGroupEncounter(ctx, st, group);

        assertTrue(CampaignSystem.campaignForceAuditReport(ctx).stream()
                .noneMatch(line -> line.contains("FORCELESS") || line.contains("FALLBACK-OWNED")),
                "search-group encounter should not create anonymous or fallback-owned ships");
        String label = (String) readField(group, "label");
        String anchorLocationId = (String) readField(group, "anchorLocationId");
        CampaignSystem.CampaignLocation anchor = campaignLocationById(ctx, anchorLocationId);
        String anchoredForceName = (anchor != null) ? anchor.name + " Garrison" : "";
        assertTrue(campaignForceContainsAny(st, label, anchoredForceName));
    }

    @Test
    void searchGroupEncounterUsesDirectionalIngressInBriefingAndSpawnLayout() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.update(ctx, 0.1);
        Object group = firstGalaxySearchGroup(st);
        assertTrue(group != null, "expected a seeded hostile search group");

        setField(group, "anchorLocationId", "");
        setField(group, "x", st.playerGalaxyX + 520.0);
        setField(group, "y", st.playerGalaxyY);
        prepareSearchGroupEncounter(ctx, st, group);

        assertTrue(st.objectivePhaseLabel.contains("Hostiles from east edge"));
        assertTrue(st.threatStateLabel.contains("approaching from east edge"));
        assertTrue(ctx.player.x < st.galaxyAmbientPocketCenterX, "player should spawn on the allied side");
        assertTrue(hostileCenterX(ctx) > st.galaxyAmbientPocketCenterX, "hostiles should enter on the hostile side");
    }

    @Test
    void enemyActivityEncounterPromptUsesGarrisonIdentity() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation hostile = firstAreaOfInterestOfType(ctx, CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY);
        assertTrue(hostile != null, "expected an enemy activity location");

        beginCampaignArrivalEncounterChoice(ctx, st, hostile);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(ctx.ui.strategicEncounterPrompt.location.contains("Garrison"));
        assertTrue(ctx.ui.strategicEncounterPrompt.location.contains(hostile.name));
    }

    @Test
    void ambientResourceAndDistressSitesUseNamedLocalForceOwners() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation resource = firstAreaOfInterestOfType(ctx, CampaignSystem.CampaignLocationType.RESOURCE_ZONE);
        CampaignSystem.CampaignLocation distress = firstAreaOfInterestOfType(ctx, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertTrue(resource != null, "expected a resource zone");
        assertTrue(distress != null, "expected a distress signal");

        prepareAmbientEncounter(ctx, st, resource);
        assertTrue(campaignForceFieldContains(st, "name", resource.name + " Survey Group"));

        GameContext distressCtx = initializedCampaignContext();
        CampaignSystem.CampaignState distressState = distressCtx.campaign;
        prepareAmbientEncounter(distressCtx, distressState, distress);
        assertTrue(campaignForceFieldContains(distressState, "name", distress.name + " Rescue Convoy"));
    }

    @Test
    void ambientHubEncounterUsesArrivalDirectionForBriefingAndStationSideLayout() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly service location in the campaign map");
        CampaignSystem.CampaignLocation origin = firstLocationWestOf(ctx, location);
        assertTrue(origin != null, "expected a western origin location");
        st.galaxyTravel.originId = origin.id;

        prepareAmbientEncounter(ctx, st, location);

        Ship control = findShipByName(ctx, location.name + " Control");
        Ship tender = findShipByName(ctx, "Harbor Tender");
        assertTrue(control != null, "expected the installation control ship");
        assertTrue(tender != null, "expected harbor traffic");
        assertTrue(st.objectivePhaseLabel.contains("Entered from west edge"));
        boolean greenStation = (location.name != null && location.name.toUpperCase().contains("GREEN"))
                || (location.detail != null && location.detail.toUpperCase().contains("GREEN"));
        if (greenStation) {
            double playerStationDistance = Math.hypot(ctx.player.x - control.x, ctx.player.y - control.y);
            assertTrue(playerStationDistance <= 260.0, "Green station sites should start the player near the station");
        } else {
            assertTrue(ctx.player.x < st.galaxyAmbientPocketCenterX, "player should enter from the route side");
        }
        assertTrue(control.x > st.galaxyAmbientPocketCenterX, "installation should sit deeper in the pocket");
        assertTrue(tender.x < control.x, "traffic should remain closer to the approach lane than the station core");
    }

    @Test
    void ambientResourceAndDistressLayoutsPlaceMinersAndConvoysNearTheirStrategicAnchors() throws Exception {
        GameContext resourceCtx = initializedCampaignContext();
        CampaignSystem.CampaignState resourceState = resourceCtx.campaign;
        CampaignSystem.CampaignLocation resource = firstAreaOfInterestOfType(resourceCtx, CampaignSystem.CampaignLocationType.RESOURCE_ZONE);
        assertTrue(resource != null, "expected a resource zone");
        CampaignSystem.CampaignLocation resourceOrigin = firstLocationWestOf(resourceCtx, resource);
        assertTrue(resourceOrigin != null, "expected a western origin for the resource zone");
        resourceState.galaxyTravel.originId = resourceOrigin.id;
        prepareAmbientEncounter(resourceCtx, resourceState, resource);

        Ship miner = findShipByName(resourceCtx, "Survey Prospector");
        assertTrue(miner != null, "expected the survey miner");
        assertTrue(miner.x > resourceState.galaxyAmbientPocketCenterX, "miners should sit closer to the ore objective than the route entry");

        GameContext distressCtx = initializedCampaignContext();
        CampaignSystem.CampaignState distressState = distressCtx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfInterestOfType(distressCtx, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertTrue(distress != null, "expected a distress signal");
        CampaignSystem.CampaignLocation distressOrigin = firstLocationWestOf(distressCtx, distress);
        assertTrue(distressOrigin != null, "expected a western origin for the distress site");
        distressState.galaxyTravel.originId = distressOrigin.id;
        prepareAmbientEncounter(distressCtx, distressState, distress);

        Ship liner = findShipByName(distressCtx, "Lost Liner");
        if (liner == null) {
            liner = findShipByName(distressCtx, "Route Tender");
        }
        assertTrue(liner != null, "expected the convoy core ship");
        assertTrue(liner.x < distressState.galaxyAmbientPocketCenterX, "convoys should remain near their route-side approach lane");
    }

    @Test
    void missionAmbientPatrolsAndReserveNodesUseNamedForceOwners() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);

        assertTrue(campaignForceFieldContains(st, "name", "Red Forward Screen Patrol"));
        assertTrue(campaignForceFieldContains(st, "name", "Red Mid-Lane Interdiction Patrol"));
        assertTrue(campaignForceFieldContains(st, "name", "Red Reserve Staging Garrison"));
    }

    @Test
    void discoveryThreatPocketsUseNamedForceOwners() throws Exception {
        GameContext ambushCtx = initializedCampaignContext();
        CampaignSystem.CampaignState ambushState = ambushCtx.campaign;
        startSector(ambushCtx, 6);
        invokeDiscoveryThreat("spawnDiscoveryAmbush", ambushCtx, ambushState, 2400.0, 2450.0);
        assertTrue(campaignForceFieldContains(ambushState, "name", "Discovery Pocket Ambush Screen"));

        GameContext mineCtx = initializedCampaignContext();
        CampaignSystem.CampaignState mineState = mineCtx.campaign;
        startSector(mineCtx, 10);
        invokeDiscoveryThreat("spawnDiscoveryMinefield", mineCtx, mineState, 2600.0, 2380.0);
        assertTrue(campaignForceFieldContains(mineState, "name", "Discovery Pocket Minefield Guard"));
    }

    @Test
    void authoredMissionOpenersUseNamedSectorForces() throws Exception {
        assertSectorContainsNamedForce(5, "Red Atlas Memory Assault Wing");
        assertSectorContainsNamedForce(6, "Red Recovery Line Hunters");
        assertSectorContainsNamedForce(8, "Red Exodus Interdiction Group");
        assertSectorContainsNamedForce(9, "Red Defection Purge Wing");
        assertSectorContainsNamedForce(10, "Red Waybreaker Blockade Group");
        assertSectorContainsNamedForce(11, "Red Luna Cordon Vanguard");
        assertSectorContainsNamedForce(12, "Red Signatory Intercept Wing");
        assertSectorContainsNamedForce(13, "Red Contract Array Guard");
        assertSectorContainsNamedForce(14, "Red Nysa Breakpoint Screen");
        assertSectorContainsNamedForce(15, "Red Siege Relay Screen");
        assertSectorContainsNamedForce(18, "Red Solward Interdiction Wing");
        assertSectorContainsNamedForce(19, "Red Liberation Trap Screen");
        assertSectorContainsNamedForce(20, "Red Rejoin Pursuit Group");
        assertSectorContainsNamedForce(21, "Red Earthway Siege Group");
        assertSectorContainsNamedForce(22, "Red Terminal Breakthrough Group");
        assertSectorContainsNamedForce(23, "Red Earthrise Suppression Group");
    }

    @Test
    void authoredSectorScriptsUseNamedReliefAndCounterattackForces() throws Exception {
        GameContext sector3Ctx = initializedCampaignContext();
        CampaignSystem.CampaignState sector3 = sector3Ctx.campaign;
        startSector(sector3Ctx, 3);
        sector3.authoredObjectiveHostiles.clear();
        sector3.objectiveStage = 0;
        sector3.authoredWaveCursor = 0;
        invokeSectorScriptUpdate("updateSector3Script", sector3Ctx, sector3);
        assertTrue(campaignForceFieldContains(sector3, "name", "Red Relay Relief Wing Alpha"));

        GameContext sector7Ctx = initializedCampaignContext();
        CampaignSystem.CampaignState sector7 = sector7Ctx.campaign;
        startSector(sector7Ctx, 7);
        sector7.objectiveStage = 1;
        sector7.authoredWaveCursor = 1;
        sector7.objectiveGoal = 8.0;
        sector7.objectiveProgress = 3.0;
        invokeSectorScriptUpdate("updateSector7Script", sector7Ctx, sector7);
        assertTrue(campaignForceFieldContains(sector7, "name", "Red Contract Counterattack Screen"));

        GameContext sector11Ctx = initializedCampaignContext();
        CampaignSystem.CampaignState sector11 = sector11Ctx.campaign;
        startSector(sector11Ctx, 11);
        sector11.objectiveStage = 0;
        sector11.authoredWaveCursor = 0;
        sector11.sectorElapsed = 40.0;
        invokeSectorScriptUpdate("updateSector11Script", sector11Ctx, sector11);
        assertTrue(campaignForceFieldContains(sector11, "name", "Red Luna Cordon Reinforcement Alpha"));

        GameContext sector1Ctx = initializedCampaignContext();
        CampaignSystem.CampaignState sector1 = sector1Ctx.campaign;
        startSector(sector1Ctx, 1);
        sector1.sectorElapsed = 55.0;
        invokeSectorScriptUpdate("updateSector1Script", sector1Ctx, sector1);
        assertTrue(campaignForceFieldContains(sector1, "name", "Red Knife Raider Probe Alpha"));
    }

    @Test
    void reservePressureWavesUseNamedDetachmentForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);

        invokeLaunchPressureStage(ctx, st, "TEST PRESSURE", "phase", "threat",
                new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT});
        assertTrue(campaignForceFieldContains(st, "name", "Reserve Detachment Alpha"));
        assertTrue(st.objectivePhaseLabel.contains("Reserve wave entering from"));
        assertTrue(st.threatStateLabel.contains("Reserve ingress"));

        st.sectorElapsed = 90.0;
        invokeDistributedMapPressure(ctx, st);
        assertTrue(campaignForceFieldContains(st, "name", "Reserve Detachment Beta")
                || campaignForceFieldContains(st, "name", "Reserve Detachment Alpha"));
        assertTrue(st.threatStateLabel.contains("Reserve staging is spilling in from"));
    }

    @Test
    void strategicForceSimulationSeedsIntentStrengthAndFriendlySupportOrders() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        invokeStrategicForceSimulation(ctx, st, 1.0);

        assertTrue(CampaignSystem.campaignForceSummaries(ctx).stream()
                .anyMatch(summary -> summary.name.contains("Blue Command Fleet") && summary.intent != null));
        assertTrue(CampaignSystem.campaignForceSummaries(ctx).stream()
                .anyMatch(summary -> summary.hostile && summary.strength > 0.0));
        assertTrue(CampaignSystem.campaignForceSummaries(ctx).stream()
                .anyMatch(summary -> !summary.hostile && summary.intent == CampaignSystem.CampaignForceIntent.MINING));
    }

    @Test
    void campaignForcePersistenceRoundTripKeepsIntentAndRouteState() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        invokeStrategicForceSimulation(ctx, st, 1.0);
        ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.LOCAL_FORCE,
                Faction.ENEMY,
                "Test Checkpoint Contact Force",
                st.playerGalaxyX + 900.0,
                st.playerGalaxyY + 80.0,
                64.0);
        Object force = campaignForceByName(st, "Test Checkpoint Contact Force");
        assertTrue(force != null, "expected test force to exist before checkpoint");
        setField(force, "contactConfidence", 0.73);
        setField(force, "uncertaintyRadius", 222.0);
        setField(force, "lastKnownX", 1234.0);
        setField(force, "lastKnownY", 2345.0);
        setField(force, "lastKnownAgeSec", 17.0);
        setField(force, "contactState", Enum.valueOf((Class<Enum>) readField(force, "contactState").getClass(), "SUSPECTED"));
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 2);

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        Object restoredForce = campaignForceByName(restored.campaign, "Test Checkpoint Contact Force");

        assertTrue(restoredForce != null, "expected restored test force");
        assertEquals(0.73, (double) readField(restoredForce, "contactConfidence"), 1e-6);
        assertEquals(222.0, (double) readField(restoredForce, "uncertaintyRadius"), 1e-6);
        assertEquals("SUSPECTED", readField(restoredForce, "contactState").toString());
        assertTrue(CampaignSystem.campaignForceSummaries(restored).stream()
                .anyMatch(summary -> summary.intent != null && summary.strength > 0.0));
    }

    @Test
    void strategicMapShowsCampaignForceMarkersWithContactTelemetry() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        invokeStrategicForceSimulation(ctx, st, 1.0);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        assertTrue(markers.stream().anyMatch(marker ->
                        marker.type == CampaignSystem.SupportMarkerType.FORCE_BASE_DEFENSE
                                || marker.type == CampaignSystem.SupportMarkerType.FORCE_PATROL
                                || marker.type == CampaignSystem.SupportMarkerType.FORCE_CONVOY
                                || marker.type == CampaignSystem.SupportMarkerType.FORCE_MINING
                                || marker.type == CampaignSystem.SupportMarkerType.FORCE_SEARCH
                                || marker.type == CampaignSystem.SupportMarkerType.FORCE_STRIKE),
                "expected campaign forces to be visible as strategic support markers");
        assertTrue(markers.stream().anyMatch(marker ->
                        marker.subtitle.contains("force contact")
                                && marker.subtitle.contains("conf ")
                                && marker.subtitle.contains("intent ")),
                "expected force markers to explain confidence, last known state, direction, and intent");
    }

    @Test
    void selectedForceContactAddsDetailPanelLinesAndRouteInterceptWarning() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation destination = firstFriendlyServiceLocation(ctx);
        assertTrue(destination != null, "expected a route destination");
        st.selectedGalaxyLocationId = destination.id;

        ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT,
                Faction.ENEMY,
                "Test Route Interceptor",
                (st.playerGalaxyX + destination.x) * 0.5,
                (st.playerGalaxyY + destination.y) * 0.5,
                82.0);
        Object force = campaignForceByName(st, "Test Route Interceptor");
        assertTrue(force != null, "expected test force");
        setField(force, "intent", CampaignSystem.CampaignForceIntent.INTERCEPTING);
        setField(force, "contactConfidence", 0.86);
        setField(force, "uncertaintyRadius", 180.0);
        setField(force, "targetX", destination.x);
        setField(force, "targetY", destination.y);
        @SuppressWarnings("unchecked")
        List<double[]> routePoints = (List<double[]>) readField(force, "routePoints");
        routePoints.add(new double[]{destination.x, destination.y});

        List<String> warnings = CampaignSystem.selectedRouteForceWarningLines(ctx);
        assertTrue(warnings.stream().anyMatch(line -> line.contains("Test Route Interceptor")),
                "expected plotted route to warn about the intercepting force");

        setField(force, "contactState", CampaignSystem.CampaignForceContactState.STALE);
        warnings = CampaignSystem.selectedRouteForceWarningLines(ctx);
        assertTrue(warnings.stream().noneMatch(line -> line.contains("Test Route Interceptor")),
                "stale force contacts should not keep drawing intercept warnings");
        setField(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);

        CampaignSystem.selectCampaignContactTarget(ctx,
                "Test Route Interceptor",
                "Known force contact  |  strike detachment  |  conf 86%  |  intent intercepting",
                "Known force contact",
                (double) readField(force, "x"),
                (double) readField(force, "y"),
                true,
                true);
        List<String> detail = invokeSelectedContactSidebarLines(ctx);
        assertTrue(detail.stream().anyMatch(line -> line.contains("Force Owner: Test Route Interceptor")));
        assertTrue(detail.stream().anyMatch(line -> line.contains("Force Orders: pursuit / intercept")));
    }

    @Test
    void scoutingBattleContextAndAfterActionLinesNameForceOriginsAndOutcomes() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly hub");
        st.selectedGalaxyLocationId = location.id;

        List<String> safeContext = CampaignSystem.campaignBattleContextLines(ctx);
        assertTrue(safeContext.stream().anyMatch(line -> line.contains("Hub Safety: SAFE")),
                "friendly hubs should read safe before a hostile force is nearby");
        assertTrue(safeContext.size() <= 4, "battle context should stay compact");

        ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.STRIKE_DETACHMENT,
                Faction.ENEMY,
                "Test Hidden Approach Cell",
                location.x + 160.0,
                location.y + 30.0,
                48.0);
        Object force = campaignForceByName(st, "Test Hidden Approach Cell");
        assertTrue(force != null, "expected test force");
        setField(force, "sourceLocationId", firstEnemyActivityLocationId(ctx));
        setField(force, "contactConfidence", 0.41);
        setEnumField(force, "contactState", "SUSPECTED");

        List<String> scout = CampaignSystem.campaignScoutingReportLines(ctx);
        assertTrue(scout.stream().anyMatch(line -> line.contains("Test Hidden Approach Cell") && line.contains("from")),
                "scouting reports should explain force origin");

        List<String> dangerContext = CampaignSystem.campaignBattleContextLines(ctx);
        assertTrue(dangerContext.stream().anyMatch(line -> line.contains("Battle Reason: Test Hidden Approach Cell")),
                "battle context should explain why a battle can happen before entry");
        assertTrue(dangerContext.stream().anyMatch(line -> line.contains("Hub Safety: THREATENED")),
                "hub safety should escalate when a hostile force approaches");
        assertTrue(dangerContext.stream().anyMatch(line -> line.startsWith("Hidden Threat Hint: ")),
                "uncertain hostile forces should leave a subtle hint");

        st.transitionSummaryTop = "Test engagement resolved.";
        st.transitionSummaryBottom = "Command crews are compiling losses.";
        setField(force, "intent", CampaignSystem.CampaignForceIntent.RETREATING);
        setField(force, "strength", 18.0);
        setField(force, "readiness", 44.0);
        assertTrue(CampaignSystem.campaignAfterActionPlateLines(ctx).stream()
                        .anyMatch(line -> line.contains("FORCE OUTCOME") && line.contains("Test Hidden Approach Cell")
                                && (line.contains("ROUTED") || line.contains("DAMAGED"))),
                "after-action plate should name damaged or routed campaign forces");
    }

    @Test
    void strategicStrikeDamageFeedsBackIntoLinkedCampaignForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.update(ctx, 0.1);
        invokeStrategicForceSimulation(ctx, st, 0.5);
        Object group = firstGalaxySearchGroup(st);
        assertTrue(group != null, "expected a seeded hostile search group");
        int groupId = (int) readField(group, "id");
        setField(group, "x", st.playerGalaxyX + 180.0);
        setField(group, "y", st.playerGalaxyY + 20.0);
        setField(group, "lastKnownX", st.playerGalaxyX + 180.0);
        setField(group, "lastKnownY", st.playerGalaxyY + 20.0);
        setField(group, "visible", true);
        double before = campaignForceStrengthForLinkedSearchGroup(st, groupId);

        setField(group, "contactConfidence", Enum.valueOf((Class<Enum>) readField(group, "contactConfidence").getClass(), "CONFIRMED_HOSTILE"));
        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx, (double) readField(group, "x"), (double) readField(group, "y")));
        for (int i = 0; i < 200 && !st.strategicStrikeObjects.isEmpty(); i++) {
            CampaignSystem.update(ctx, 0.2);
        }

        double after = campaignForceStrengthForLinkedSearchGroup(st, groupId);
        assertTrue(after <= before, "linked campaign force should lose strength after a successful strategic strike");
    }

    @Test
    void directFleetClashUsesLinkedHostileCampaignForceManifest() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.update(ctx, 0.1);
        Object group = firstGalaxySearchGroup(st);
        assertTrue(group != null, "expected a hostile search group");
        setField(group, "anchorLocationId", "");
        setField(group, "x", st.playerGalaxyX + 260.0);
        setField(group, "y", st.playerGalaxyY);
        invokeStrategicForceSimulation(ctx, st, 0.2);

        prepareSearchGroupEncounter(ctx, st, group);

        int groupId = (int) readField(group, "id");
        assertTrue(countEnemyShipsForLinkedSearchGroup(st, groupId) > 0, "expected hostile ships to belong to the linked force");
        assertTrue(st.transitionSummaryTop.contains("fleets clashed"), "expected clash summary");
    }

    @Test
    void activeTacticalEncounterPersistsParentForceReferencesWithoutDuplicateRestore() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.update(ctx, 0.1);
        Object group = firstGalaxySearchGroup(st);
        assertTrue(group != null, "expected a hostile search group");

        assertTrue(invokeLaunchGalaxySearchGroupEncounter(ctx, st, group));
        List<Integer> activeForceIds = CampaignSystem.activeGalaxyEncounterForceIds(ctx);
        assertFalse(activeForceIds.isEmpty(), "active tactical encounter should remember participating campaign forces");
        assertTrue(CampaignSystem.activeGalaxyEncounterParentForceId(ctx) > 0,
                "active tactical encounter should retain a parent-force reference");

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 2);
        assertFalse(checkpoint.activeGalaxyEncounterForceIds.isBlank());
        assertTrue(checkpoint.activeGalaxyEncounterParentForceId > 0);

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        int liveShipsAfterFirstRestore = liveShipCount(restored);
        assertTrue(applyCheckpoint(restored, checkpoint));

        assertEquals(liveShipsAfterFirstRestore, liveShipCount(restored),
                "reapplying a force checkpoint should not duplicate live ships");
        assertEquals(liveShipCount(restored), restored.campaign.shipCampaignForceIds.size(),
                "restored live ships should keep one current force membership each");
        assertEquals(activeForceIds, CampaignSystem.activeGalaxyEncounterForceIds(restored));
        assertEquals(checkpoint.activeGalaxyEncounterParentForceId, CampaignSystem.activeGalaxyEncounterParentForceId(restored));
    }

    @Test
    void campaignEncounterSetupExitsWithEveryLiveCampaignShipOwned() throws Exception {
        GameContext friendlyCtx = initializedCampaignContext();
        CampaignSystem.CampaignState friendlyState = friendlyCtx.campaign;
        CampaignSystem.CampaignLocation hub = firstFriendlyServiceLocation(friendlyCtx);
        assertTrue(hub != null, "expected a friendly hub");
        prepareAmbientEncounter(friendlyCtx, friendlyState, hub);
        assertEveryLiveShipHasCampaignForce(friendlyCtx);

        GameContext hostileCtx = initializedCampaignContext();
        CampaignSystem.CampaignState hostileState = hostileCtx.campaign;
        CampaignSystem.update(hostileCtx, 0.1);
        Object group = firstGalaxySearchGroup(hostileState);
        assertTrue(group != null, "expected a seeded hostile search group");
        prepareSearchGroupEncounter(hostileCtx, hostileState, group);
        assertEveryLiveShipHasCampaignForce(hostileCtx);
    }

    @Test
    void tacticalKillsRemoveShipsFromParentForceMembershipAndLowerStrength() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);
        invokeStrategicForceSimulation(ctx, st, 0.2);

        invokeLaunchPressureStage(ctx, st, "TEST PRESSURE", "phase", "threat",
                new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT});
        Object detachment = campaignForceByName(st, "Reserve Detachment Alpha");
        assertTrue(detachment != null, "expected reserve detachment");
        int memberBefore = campaignForceShipIdCount(detachment);
        double strengthBefore = (double) readField(detachment, "strength");
        Ship victim = firstLiveShipForForce(ctx, detachment);
        assertTrue(victim != null, "expected a live detachment ship to kill");

        victim.alive = false;
        victim.dying = true;
        victim.hp = 0;
        invokeStrategicForceSimulation(ctx, st, 0.2);

        assertTrue(campaignForceShipIdCount(detachment) < memberBefore,
                "dead tactical ships should be removed from their campaign force membership");
        assertTrue((double) readField(detachment, "strength") < strengthBefore,
                "tactical casualties should lower the owning force strength");
    }

    @Test
    void ambientEncounterUsesRelevantNamedCampaignForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeStrategicForceSimulation(ctx, st, 1.0);
        CampaignSystem.CampaignLocation location = firstFriendlyServiceLocation(ctx);
        assertTrue(location != null, "expected a friendly hub");

        prepareAmbientEncounter(ctx, st, location);

        assertTrue(CampaignSystem.campaignForceSummaries(ctx).stream()
                .anyMatch(summary -> summary.name.contains(location.name)),
                "expected the encounter to use named local force owners tied to the location");
    }

    @Test
    void reserveDetachmentConsumesStrengthFromReserveParentForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);
        invokeStrategicForceSimulation(ctx, st, 0.2);

        double before = campaignForceStrengthByName(ctx, "Red Reserve Staging Garrison");
        invokeLaunchPressureStage(ctx, st, "TEST PRESSURE", "phase", "threat", new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT});
        double after = campaignForceStrengthByName(ctx, "Red Reserve Staging Garrison");

        assertTrue(after < before, "reserve staging should lose strength when it detaches reinforcements");
        assertTrue(parentForceIdForForceName(st, "Reserve Detachment Alpha") > 0, "detachment should retain its parent force link");
    }

    @Test
    void missionStartBriefingsNameRelevantHostileForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);

        assertTrue(st.transitionSummaryTop.contains("Relevant Forces:"), "expected mission briefing to name the hostile owners");
        assertTrue(st.transitionSummaryTop.contains("Patrol:"), "expected mission briefing to classify patrol-role owners");
    }

    @Test
    void allOldCampaignMissionBriefingsMapToSpecificForceOwners() throws Exception {
        for (int sector = 1; sector <= 24; sector++) {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;
            startSector(ctx, sector);
            assertTrue(st.transitionSummaryTop == null || st.transitionSummaryTop.isBlank()
                            || st.transitionSummaryTop.contains("Relevant Forces:"),
                    "expected sector " + sector + " briefing to expose specific force owners");
        }
    }

    @Test
    void enemyActivityPoiBattlesPullNearbyCampaignForcesIntoTheEncounter() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation hostile = firstAreaOfInterestOfType(ctx, CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY);
        assertTrue(hostile != null, "expected an enemy activity site");
        CampaignSystem.update(ctx, 0.1);

        ensureCampaignForce(st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Test Nearby Hostile Wing", hostile.x + 70.0, hostile.y + 20.0, 64.0);
        ensureCampaignForce(st, CampaignSystem.CampaignForceKind.TRADE_GROUP, Faction.TEAM_D,
                "Test Nearby Relief Convoy", hostile.x - 60.0, hostile.y - 30.0, 58.0);

        Object group = anchoredSearchGroupForLocation(st, hostile.id);
        assertTrue(group != null, "expected an anchored search group");
        prepareSearchGroupEncounter(ctx, st, group);

        assertTrue(forceHasLiveShips(st, "Test Nearby Hostile Wing"));
        assertTrue(forceHasLiveShips(st, "Test Nearby Relief Convoy"));
    }

    @Test
    void convoyAttackEncountersUseConvoyEscortAndAttackingCampaignForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation distress = firstAreaOfInterestOfType(ctx, CampaignSystem.CampaignLocationType.DISTRESS_SIGNAL);
        assertTrue(distress != null, "expected a distress signal");
        CampaignSystem.update(ctx, 0.1);

        ensureCampaignForce(st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Test Raider Intercept", distress.x + 90.0, distress.y + 10.0, 62.0);

        prepareAmbientEncounter(ctx, st, distress);

        assertTrue(forceHasLiveShips(st, distress.name + " Rescue Convoy"));
        assertTrue(forceHasLiveShips(st, distress.name + " Rescue Escort"));
        assertTrue(forceHasLiveShips(st, "Test Raider Intercept"));
        assertTrue(st.threatStateLabel.contains("Test Raider Intercept"));
    }

    @Test
    void stealthContactsRevealNamedForceOwnershipBeforeCombat() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);

        Object stealth = firstStrategicTaskForceOfKind(st, "STEALTH");
        assertTrue(stealth != null, "expected a stealth task force");
        invokeBeginStrategicEncounterChoice(ctx, st, stealth, 4);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(ctx.ui.strategicEncounterPrompt.title.contains("STEALTH CONTACT REVEALED"));
        assertTrue(ctx.ui.strategicEncounterPrompt.body.contains("Ghost trace resolved into"));
    }

    @Test
    void reinforcementLossesDegradeTheParentReserveForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 6);

        invokeLaunchPressureStage(ctx, st, "TEST PRESSURE", "phase", "threat",
                new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT});
        Object detachment = campaignForceByName(st, "Reserve Detachment Alpha");
        assertTrue(detachment != null, "expected reserve detachment");
        double readinessBefore = campaignForceReadinessByName(ctx, "Red Reserve Staging Garrison");

        setField(detachment, "strength", 6.0);
        setField(detachment, "reportedSurvivingStrength", 22.0);
        invokeReconcileDetachedForceParents(st);

        double readinessAfter = campaignForceReadinessByName(ctx, "Red Reserve Staging Garrison");
        assertTrue(readinessAfter < readinessBefore, "parent reserve should lose readiness after detachment casualties");
    }

    @Test
    void authoredStaticDefenseMissionsUseNamedDefenseGrids() throws Exception {
        assertSectorContainsNamedForce(13, "Defense Grid");
        assertSectorContainsNamedForce(15, "Defense Grid");
        assertSectorContainsNamedForce(19, "Defense Grid");
        assertSectorContainsNamedForce(21, "Defense Grid");
    }

    @Test
    void bossPhaseReinforcementsUseNamedForceOwners() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, 7);
        Ship boss = findShipById(ctx, st.bossTargetId);
        assertTrue(boss != null, "expected a sector boss ship");

        invokeBossPhaseTrigger("triggerBossPhaseOne", ctx, st, boss);
        invokeBossPhaseTrigger("triggerBossPhaseTwo", ctx, st, boss);

        assertTrue(campaignForceFieldContains(st, "name", "AI PURSUIT TITAN RED KNIFE Counterstroke Screen"));
        assertTrue(campaignForceFieldContains(st, "name", "AI PURSUIT TITAN RED KNIFE Final Guard"));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method captureCheckpoint = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                int.class
        );
        captureCheckpoint.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) captureCheckpoint.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method applyCheckpoint = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignCheckpointStore.Checkpoint.class
        );
        applyCheckpoint.setAccessible(true);
        return (boolean) applyCheckpoint.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static void invokeIntroRedDetachment(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method spawnIntro = CampaignSystem.class.getDeclaredMethod(
                "spawnIntroRedDetachment",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        spawnIntro.setAccessible(true);
        spawnIntro.invoke(null, ctx, st);
    }

    private static void prepareAmbientEncounter(GameContext ctx, CampaignSystem.CampaignState st,
                                                CampaignSystem.CampaignLocation location) throws Exception {
        Method prepare = CampaignSystem.class.getDeclaredMethod(
                "prepareAmbientCampaignLocationEncounterWorld",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class
        );
        prepare.setAccessible(true);
        prepare.invoke(null, ctx, st, location);
    }

    private static void beginCampaignArrivalEncounterChoice(GameContext ctx, CampaignSystem.CampaignState st,
                                                            CampaignSystem.CampaignLocation location) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "beginCampaignArrivalEncounterChoice",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, location);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int stageTrackedThreatNearInstallation(GameContext ctx, CampaignSystem.CampaignState st,
                                                          CampaignSystem.CampaignLocation location) throws Exception {
        java.lang.reflect.Field groupsField = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        groupsField.setAccessible(true);
        List groups = (List) groupsField.get(st);
        Object threat = groups.get(0);
        groups.clear();
        groups.add(threat);

        setField(threat, "x", location.x + 90.0);
        setField(threat, "y", location.y - 40.0);
        setField(threat, "lastKnownX", location.x + 90.0);
        setField(threat, "lastKnownY", location.y - 40.0);
        setField(threat, "visible", true);
        setField(threat, "identified", false);
        setField(threat, "contactFadeSec", 12.0);
        setField(threat, "anchorLocationId", firstEnemyActivityLocationId(ctx));
        setEnumField(threat, "contactConfidence", "CONFIRMED_HOSTILE");
        return (int) readField(threat, "id");
    }

    private static void stageScriptedInstallationThreat(CampaignSystem.CampaignState st,
                                                        CampaignSystem.CampaignLocation location,
                                                        String forceName,
                                                        String origin,
                                                        String warning,
                                                        double threatLevel) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "registerInstallationThreatCase",
                CampaignSystem.CampaignState.class,
                String.class,
                String.class,
                String.class,
                String.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, st, location.id, forceName, origin, warning, threatLevel);
    }

    @SuppressWarnings("rawtypes")
    private static Object firstGalaxySearchGroup(CampaignSystem.CampaignState st) throws Exception {
        java.lang.reflect.Field groupsField = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        groupsField.setAccessible(true);
        List groups = (List) groupsField.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static void prepareSearchGroupEncounter(GameContext ctx, CampaignSystem.CampaignState st, Object group) throws Exception {
        Class<?> groupClass = group.getClass();
        Method method = CampaignSystem.class.getDeclaredMethod(
                "prepareGalaxySearchGroupEncounterWorld",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                groupClass
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, group);
    }

    private static boolean invokeLaunchGalaxySearchGroupEncounter(GameContext ctx,
                                                                  CampaignSystem.CampaignState st,
                                                                  Object group) throws Exception {
        Class<?> groupClass = group.getClass();
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchGalaxySearchGroupEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                groupClass
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, group);
    }

    private static void invokeSectorScriptUpdate(String methodName, GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                methodName,
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeDiscoveryThreat(String methodName,
                                              GameContext ctx,
                                              CampaignSystem.CampaignState st,
                                              double x,
                                              double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                methodName,
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, x, y);
    }

    private static void invokeBossPhaseTrigger(String methodName,
                                               GameContext ctx,
                                               CampaignSystem.CampaignState st,
                                               Ship boss) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                methodName,
                GameContext.class,
                CampaignSystem.CampaignState.class,
                Ship.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, boss);
    }

    private static void invokeLaunchPressureStage(GameContext ctx,
                                                  CampaignSystem.CampaignState st,
                                                  String banner,
                                                  String phase,
                                                  String threat,
                                                  ShipRole[] roles) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchPressureStage",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                String.class,
                String.class,
                String.class,
                ShipRole[].class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, banner, phase, threat, roles);
    }

    private static void invokeDistributedMapPressure(GameContext ctx,
                                                     CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateDistributedMapPressure",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static void invokeStrategicForceSimulation(GameContext ctx,
                                                       CampaignSystem.CampaignState st,
                                                       double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeReconcileDetachedForceParents(CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "reconcileDetachedCampaignForceParents",
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, st);
    }

    private static void invokeBeginStrategicEncounterChoice(GameContext ctx,
                                                            CampaignSystem.CampaignState st,
                                                            Object taskForce,
                                                            int playerSubzone) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "beginStrategicEncounterChoice",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                taskForce.getClass(),
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, taskForce, playerSubzone);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSelectedContactSidebarLines(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "selectedContactSidebarLines",
                GameContext.class
        );
        method.setAccessible(true);
        return (List<String>) method.invoke(null, ctx);
    }

    private static void ensureCampaignForce(CampaignSystem.CampaignState st,
                                            CampaignSystem.CampaignForceKind kind,
                                            Faction faction,
                                            String name,
                                            double x,
                                            double y,
                                            double strength) throws Exception {
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
        Object force = method.invoke(null, st, kind, faction, name, "Test setup", "Test force", x, y);
        setField(force, "strength", strength);
        setField(force, "readiness", Math.max(56.0, strength));
    }

    private static void assertSectorContainsNamedForce(int sector, String expectedForceName) throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        startSector(ctx, sector);
        assertTrue(campaignForceFieldContains(st, "name", expectedForceName),
                "expected sector " + sector + " to create force " + expectedForceName);
    }

    private static boolean campaignForceFieldContains(CampaignSystem.CampaignState st, String fieldName, String expected) {
        try {
            java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
            field.setAccessible(true);
            java.util.List<?> forces = (java.util.List<?>) field.get(st);
            for (Object force : forces) {
                if (force == null) continue;
                java.lang.reflect.Field forceField = force.getClass().getDeclaredField(fieldName);
                forceField.setAccessible(true);
                Object value = forceField.get(force);
                if (value != null && value.toString().contains(expected)) return true;
            }
            return false;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static boolean campaignForceContainsAny(CampaignSystem.CampaignState st, String... expected) {
        for (String value : expected) {
            if (campaignForceFieldContains(st, "name", value)) return true;
        }
        return false;
    }

    private static List<String> shipSpawnCategories(CampaignSystem.CampaignState st) {
        try {
            java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("shipCampaignSpawnCategories");
            field.setAccessible(true);
            java.util.Map<?, ?> categories = (java.util.Map<?, ?>) field.get(st);
            ArrayList<String> out = new ArrayList<>();
            for (Object value : categories.values()) {
                if (value != null) out.add(value.toString());
            }
            return out;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static CampaignSystem.CampaignLocation firstFriendlyServiceLocation(GameContext ctx) {
        List<CampaignSystem.CampaignLocation> locations = friendlyInstallationLocations(ctx);
        return locations.isEmpty() ? null : locations.get(0);
    }

    private static List<CampaignSystem.CampaignLocation> friendlyInstallationLocations(GameContext ctx) {
        ArrayList<CampaignSystem.CampaignLocation> out = new ArrayList<>();
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (isFriendlyInstallationLocation(location)) out.add(location);
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (isFriendlyInstallationLocation(location)) out.add(location);
        }
        return out;
    }

    private static boolean isFriendlyInstallationLocation(CampaignSystem.CampaignLocation location) {
        if (location == null) return false;
        boolean installation = location.type == CampaignSystem.CampaignLocationType.REPAIR_SITE
                || (location.services != null && !location.services.isEmpty());
        if (!installation) return false;
        String name = location.name == null ? "" : location.name.toUpperCase();
        String detail = location.detail == null ? "" : location.detail.toUpperCase();
        if (location.type == CampaignSystem.CampaignLocationType.REPAIR_SITE) return true;
        return name.contains("GREEN")
                || name.contains("YELLOW")
                || detail.contains("GREEN")
                || detail.contains("YELLOW")
                || detail.contains("BROKER")
                || detail.contains("COALITION")
                || detail.contains("RESISTANCE");
    }

    private static String firstEnemyActivityLocationId(GameContext ctx) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.type == CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY) {
                return location.id;
            }
        }
        return "";
    }

    private static CampaignSystem.CampaignLocation firstAreaOfInterestOfType(GameContext ctx,
                                                                              CampaignSystem.CampaignLocationType type) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.type == type) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstLocationWestOf(GameContext ctx, CampaignSystem.CampaignLocation target) {
        if (ctx == null || target == null) return null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location != target && location.x < target.x - 10.0) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location != target && location.x < target.x - 10.0) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation campaignLocationById(GameContext ctx, String id) {
        if (id == null || id.isBlank()) return null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static Object anchoredSearchGroupForLocation(CampaignSystem.CampaignState st, String locationId) throws Exception {
        java.lang.reflect.Field groupsField = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        groupsField.setAccessible(true);
        List groups = (List) groupsField.get(st);
        for (Object group : groups) {
            if (group != null && locationId.equals(readField(group, "anchorLocationId"))) return group;
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static Object firstStrategicTaskForceOfKind(CampaignSystem.CampaignState st, String kindName) throws Exception {
        java.lang.reflect.Field forcesField = CampaignSystem.CampaignState.class.getDeclaredField("strategicTaskForces");
        forcesField.setAccessible(true);
        List forces = (List) forcesField.get(st);
        for (Object force : forces) {
            if (force != null && kindName.equals(String.valueOf(readField(force, "kind")))) return force;
        }
        return null;
    }

    private static Object campaignForceByName(CampaignSystem.CampaignState st, String nameFragment) throws Exception {
        java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        java.util.List<?> forces = (java.util.List<?>) field.get(st);
        for (Object force : forces) {
            if (force == null) continue;
            Object name = readField(force, "name");
            if (name != null && name.toString().contains(nameFragment)) return force;
        }
        return null;
    }

    private static boolean forceHasLiveShips(CampaignSystem.CampaignState st, String nameFragment) throws Exception {
        Object force = campaignForceByName(st, nameFragment);
        if (force == null) return false;
        Object shipIds = readField(force, "shipIds");
        if (!(shipIds instanceof java.util.Set<?>)) return false;
        return !((java.util.Set<?>) shipIds).isEmpty();
    }

    private static int campaignForceShipIdCount(Object force) throws Exception {
        Object shipIds = readField(force, "shipIds");
        if (!(shipIds instanceof java.util.Set<?>)) return 0;
        return ((java.util.Set<?>) shipIds).size();
    }

    private static Ship firstLiveShipForForce(GameContext ctx, Object force) throws Exception {
        Object shipIds = readField(force, "shipIds");
        if (!(shipIds instanceof java.util.Set<?> ids)) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ids.contains(ship.id) && ship.alive && !ship.dying && ship.hp > 0) return ship;
        }
        return null;
    }

    private static void assertEveryLiveShipHasCampaignForce(GameContext ctx) {
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            assertTrue(ctx.campaign.shipCampaignForceIds.containsKey(ship.id),
                    "expected live campaign ship to have a force owner: " + ship.name + " id=" + ship.id);
        }
    }

    private static Ship findShipById(GameContext ctx, int id) {
        if (ctx == null || ctx.ships == null || id <= 0) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == id) return ship;
        }
        return null;
    }

    private static Ship findShipByName(GameContext ctx, String nameFragment) {
        if (ctx == null || ctx.ships == null || nameFragment == null || nameFragment.isBlank()) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.name != null && ship.name.contains(nameFragment)) return ship;
        }
        return null;
    }

    private static double campaignForceStrengthByName(GameContext ctx, String nameFragment) {
        for (CampaignSystem.CampaignForceSummary summary : CampaignSystem.campaignForceSummaries(ctx)) {
            if (summary != null && summary.name != null && summary.name.contains(nameFragment)) return summary.strength;
        }
        return 0.0;
    }

    private static double campaignForceReadinessByName(GameContext ctx, String nameFragment) {
        for (CampaignSystem.CampaignForceSummary summary : CampaignSystem.campaignForceSummaries(ctx)) {
            if (summary != null && summary.name != null && summary.name.contains(nameFragment)) return summary.readiness;
        }
        return 0.0;
    }

    private static double campaignForceStrengthForLinkedSearchGroup(CampaignSystem.CampaignState st, int groupId) throws Exception {
        java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        java.util.List<?> forces = (java.util.List<?>) field.get(st);
        for (Object force : forces) {
            if (force == null) continue;
            java.lang.reflect.Field linked = force.getClass().getDeclaredField("linkedSearchGroupId");
            linked.setAccessible(true);
            if (((Integer) linked.get(force)) == groupId) {
                java.lang.reflect.Field strength = force.getClass().getDeclaredField("strength");
                strength.setAccessible(true);
                return ((Double) strength.get(force));
            }
        }
        return 0.0;
    }

    private static int countEnemyShipsForLinkedSearchGroup(CampaignSystem.CampaignState st, int groupId) throws Exception {
        java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        java.util.List<?> forces = (java.util.List<?>) field.get(st);
        for (Object force : forces) {
            if (force == null) continue;
            java.lang.reflect.Field linked = force.getClass().getDeclaredField("linkedSearchGroupId");
            linked.setAccessible(true);
            if (((Integer) linked.get(force)) == groupId) {
                java.lang.reflect.Field ships = force.getClass().getDeclaredField("shipIds");
                ships.setAccessible(true);
                java.util.Set<?> ids = (java.util.Set<?>) ships.get(force);
                return ids.size();
            }
        }
        return 0;
    }

    private static int parentForceIdForForceName(CampaignSystem.CampaignState st, String nameFragment) throws Exception {
        java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        java.util.List<?> forces = (java.util.List<?>) field.get(st);
        for (Object force : forces) {
            if (force == null) continue;
            java.lang.reflect.Field name = force.getClass().getDeclaredField("name");
            name.setAccessible(true);
            Object value = name.get(force);
            if (value != null && value.toString().contains(nameFragment)) {
                java.lang.reflect.Field parent = force.getClass().getDeclaredField("parentForceId");
                parent.setAccessible(true);
                return (Integer) parent.get(force);
            }
        }
        return 0;
    }

    private static double hostileCenterX(GameContext ctx) {
        double total = 0.0;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.alive && !ship.dying && ship.hp > 0 && ship.faction == Faction.ENEMY) {
                total += ship.x;
                count++;
            }
        }
        return count <= 0 ? 0.0 : total / count;
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumField(Object target, String fieldName, String enumName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<? extends Enum> enumType = (Class<? extends Enum>) field.getType();
        field.set(target, Enum.valueOf(enumType, enumName));
    }

    private static int liveShipCount(GameContext ctx) {
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.alive && !ship.dying && ship.hp > 0) count++;
        }
        return count;
    }

    private static int liveHostileShipCount(GameContext ctx) {
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.alive && !ship.dying && ship.hp > 0 && ship.faction == Faction.ENEMY) {
                count++;
            }
        }
        return count;
    }

    private static int liveHostileShipCountNearPlayer(GameContext ctx, double radius) {
        int count = 0;
        double r2 = radius * radius;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.alive && !ship.dying && ship.hp > 0 && ship.faction == Faction.ENEMY
                    && ctx.player != null && GameMath.dist2(ship.x, ship.y, ctx.player.x, ctx.player.y) <= r2) {
                count++;
            }
        }
        return count;
    }
}
