import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignLivingWarSystemTest {

    @Test
    void npcBattleAndPendingReinforcementPersistAcrossCheckpointRestore() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = firstForceForFaction(st, Faction.TEAM_C);
        if (green == null) {
            green = invokePrivate("ensureCampaignForce",
                    new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                            Faction.class, String.class, String.class, String.class, double.class, double.class},
                    st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                    "Green Regression Patrol", "Green test yard", "Defend local route", 2240.0, 2200.0);
        }
        assertTrue(red != null && green != null, "expected seeded Red and Green campaign forces");

        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(red, "npcEngagementCooldownSec", 0.0);
        setDouble(green, "npcEngagementCooldownSec", 0.0);

        invokePrivate("resolveNpcFactionFleetBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.2);
        assertEquals(1, st.campaignBattles.size());
        Object battle = st.campaignBattles.get(0);
        assertEquals("FORMING", getObject(battle, "stage").toString());

        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 6.8);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.3);
        assertEquals("DECISIVE", getObject(battle, "stage").toString());
        assertTrue(getBoolean(battle, "attritionApplied"));
        assertFalse(getObject(battle, "outcomeReport").toString().isBlank());
        assertFalse(getObject(battle, "winnerFollowUp").toString().isBlank());
        assertFalse(st.theaterWarRecentEvents.isEmpty());

        CampaignSystem.CampaignLocation location = st.galaxyAreasOfInterest.get(0);
        Class<?> doctrineType = findNestedClass("GalaxySearchDoctrine");
        invokePrivate("spawnIgnoredContactResponseGroup",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class, doctrineType},
                ctx, st, location, enumConstant(doctrineType, "INTERDICTION_GROUP"));
        assertFalse(st.pendingHostileReinforcements.isEmpty());

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 5);
        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(1, restored.campaign.campaignBattles.size());
        assertEquals(1, restored.campaign.pendingHostileReinforcements.size());
        assertEquals("DECISIVE", getObject(restored.campaign.campaignBattles.get(0), "stage").toString());
    }

    @Test
    void hostileReinforcementWarnsBeforeItEntersTheater() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        queuePendingReinforcement(ctx, st);
        Object pending = st.pendingHostileReinforcements.get(0);
        int groupsBefore = st.galaxySearchGroups.size();

        invokePrivate("updatePendingHostileReinforcements",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.1);
        assertTrue(getBoolean(pending, "warned"));
        assertEquals(groupsBefore, st.galaxySearchGroups.size(), "warning should precede theater entry");

        invokePrivate("updatePendingHostileReinforcements",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 999.0);
        assertTrue(st.pendingHostileReinforcements.isEmpty());
    }

    @Test
    void npcContactMemoryDecaysAfterHostileLeavesDetectionRange() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2280.0);
        setDouble(green, "y", 2200.0);

        invokePrivate("refreshNpcForceContacts",
                new Class[]{CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"), double.class},
                st, red, 0.2);
        assertFalse(((Map<?, ?>) getObject(red, "knownHostileContacts")).isEmpty());

        setDouble(green, "x", 4800.0);
        invokePrivate("refreshNpcForceContacts",
                new Class[]{CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"), double.class},
                st, red, 30.0);
        assertTrue(((Map<?, ?>) getObject(red, "knownHostileContacts")).isEmpty());
    }

    @Test
    void importantNpcBattleOffersSupportIntervention() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(red, "strength", 90.0);
        setDouble(green, "strength", 90.0);
        st.playerGalaxyX = 2100.0;
        st.playerGalaxyY = 2200.0;

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals("CAMPAIGN_BATTLE", ctx.ui.strategicEncounterPrompt.kind.toString());
        assertTrue(CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "SUPPORT"));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals("SUPPORT", getObject(st.campaignBattles.get(0), "playerIntervention"));
    }

    @Test
    void importantNpcBattleJoinLaunchesTacticalForceEncounter() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(red, "strength", 90.0);
        setDouble(green, "strength", 90.0);
        st.playerGalaxyX = 2100.0;
        st.playerGalaxyY = 2200.0;

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals("CAMPAIGN_BATTLE", ctx.ui.strategicEncounterPrompt.kind.toString());

        assertTrue(CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "JOIN"));

        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals("JOIN", getObject(st.campaignBattles.get(0), "playerIntervention"));
        assertFalse(st.strategicOvermapMode, "joining a nearby battle should enter tactical command");
        assertTrue(st.galaxyEncounterActive, "joining should launch the hostile force encounter");
    }

    @Test
    void importantNpcBattleSupportWorksWithLowReserve() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(red, "strength", 90.0);
        setDouble(green, "strength", 90.0);
        st.playerGalaxyX = 2100.0;
        st.playerGalaxyY = 2200.0;
        st.blueInterventionReserve = 4.0;
        double redBefore = getDouble(red, "strength");

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);

        assertTrue(CampaignSystem.resolvePendingCampaignBattleIntervention(ctx, "SUPPORT"));

        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals("SUPPORT", getObject(st.campaignBattles.get(0), "playerIntervention"));
        assertTrue(getDouble(red, "strength") < redBefore, "low-reserve support should still disrupt the Red participant");
        assertTrue(CampaignSystem.campaignStrikeBattleEventSummary(ctx).contains("SUPPORT IMPACT EVENT"));
    }

    @Test
    void remoteNpcBattleAppearsAsObservedFromAfarIntel() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        setDouble(red, "x", 4900.0);
        setDouble(red, "y", 4900.0);
        setDouble(green, "x", 4860.0);
        setDouble(green, "y", 4900.0);
        setDouble(red, "strength", 30.0);
        setDouble(green, "strength", 30.0);
        st.playerGalaxyX = 0.0;
        st.playerGalaxyY = 0.0;

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);

        List<String> authority = CampaignSystem.campaignStrategicAuthorityLines(ctx);
        assertTrue(authority.stream().anyMatch(line -> line.contains("OBSERVED FROM AFAR")
                && line.contains("delayed intel")
                && line.contains("Fleet Clash #")));
    }

    @Test
    void resolvedNpcBattleLeavesStrategicBattleScarMarker() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(red, "strength", 40.0);
        setDouble(green, "strength", 40.0);
        st.playerGalaxyX = 2200.0;
        st.playerGalaxyY = 2200.0;
        st.strategicOvermapMode = true;

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 17.0);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        assertTrue(markers.stream().anyMatch(marker -> marker.label.startsWith("Battle Scar #")
                && marker.subtitle.contains("BATTLE SCAR")
                && marker.type == CampaignSystem.SupportMarkerType.SALVAGE));
    }

    @Test
    void factionDirectorBriefsPersistAcrossCheckpointRestore() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokePrivate("updateFactionDirectors",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, ctx.campaign, 12.0);
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 5);
        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertTrue(restored.campaign.redDirectorBrief.startsWith("Red:"));
        assertTrue(restored.campaign.greenDirectorBrief.startsWith("Green:"));
        assertTrue(restored.campaign.yellowDirectorBrief.startsWith("Yellow:"));
    }

    @Test
    void yellowConvoyRetreatsWhenThreatened() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object convoy = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.CONVOY, Faction.TEAM_D,
                "Yellow Regression Convoy", "Yellow test lane", "Run trade route", 2400.0, 2400.0);
        Object red = firstForceForFaction(st, Faction.ENEMY);
        assertTrue(convoy != null && red != null, "expected seeded Yellow convoy and Red force");
        setDouble(convoy, "x", 2400.0);
        setDouble(convoy, "y", 2400.0);
        setDouble(red, "x", 2480.0);
        setDouble(red, "y", 2400.0);

        invokePrivate("applySprintOneFleetBehavior",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"), double.class},
                ctx, st, convoy, 0.2);
        assertEquals("RETREATING", getObject(convoy, "intent").toString());
    }

    @Test
    void coalitionTrafficDoesNotTriggerFriendlyFleetThreatResponse() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Faction.configureCampaignAlliances(true, true);
        try {
            for (Object force : st.campaignForces) {
                setDouble(force, "x", 4800.0);
                setDouble(force, "y", 4800.0);
            }
            Object convoy = invokePrivate("ensureCampaignForce",
                    new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                            Faction.class, String.class, String.class, String.class, double.class, double.class},
                    st, CampaignSystem.CampaignForceKind.CONVOY, Faction.TEAM_D,
                    "Yellow Trade Convoy", "Yellow test lane", "Run trade route", 1000.0, 1000.0);
            Object green = invokePrivate("ensureCampaignForce",
                    new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                            Faction.class, String.class, String.class, String.class, double.class, double.class},
                    st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                    "Green Local Defense Patrol", "Green test lane", "Guard trade route", 1060.0, 1000.0);
            assertNotNull(convoy);
            assertNotNull(green);

            Object threat = invokePrivate("nearestEnemyForce",
                    new Class[]{CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"), double.class},
                    st, convoy, 560.0);
            assertEquals(null, threat, "coalition traffic should not be classified as a hostile fleet");

            invokePrivate("applySprintOneFleetBehavior",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"), double.class},
                    ctx, st, convoy, 0.2);
            assertFalse("RETREATING".equals(getObject(convoy, "intent").toString()));
        } finally {
            Faction.clearCampaignAlliances();
        }
    }

    @Test
    void lateCampaignSeedsEveryChecklistFleetWithOperationalMetadata() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 24;
        st.sectorElapsed = 121.0;
        invokePrivate("syncCampaignForceSimulationSeeds",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);

        Set<String> expected = Set.of(
                "Red Scout Pair", "Red Patrol Group", "Red Interceptor Squadron", "Yellow Trade Convoy",
                "Green Local Defense Patrol", "Mining Fleet", "Pirate Wolfpack", "Red Hunter-Killer Group",
                "Red Missile Artillery Fleet", "Red Carrier Strike Group", "Red Siege Fleet", "Sensor Net Fleet",
                "Electronic Warfare Fleet", "Green Counterattack Group", "Green Escort Squadron", "Repair Tender Fleet",
                "Green Relief Convoy", "Yellow Mercenary Fleet", "Yellow Smuggler Convoy", "Distress Call Fleet",
                "Defector Fleet", "Red Line Fleet", "Red Dreadnought Task Force", "Red Blockade Fleet",
                "Red Pursuit Armada", "Red Planetary Suppression Fleet", "Red Flagship Fleet", "Green Home Guard Fleet",
                "Late-Game Carrier Support Group (Allied)", "Late-Game Titan Escort Screen", "Damaged Supercapital",
                "Prisoner Transport", "VIP Diplomatic Fleet", "Deep Recon Probe Group", "Red Picket Screen",
                "Stealth Ambush Pack", "Ghost Contact", "Experimental Cloak Fleet", "Decoy Dreadnought Contact",
                "Red Supply Convoy", "Fuel Tanker Group", "Ammo Tender Group", "Salvage Fleet",
                "Yellow Merchant Caravan", "Yellow Repair Caravan", "Pirate Salvage Gang", "Pirate Decoy Fleet",
                "Rogue Military Remnant", "Mutineer Fleet", "Black Market Escort Fleet", "Nebula Patrol",
                "Asteroid Belt Mining Guard", "Deep Space Fuel Convoy", "Relay Maintenance Fleet", "Mine-Layer Fleet",
                "Mine-Sweeper Fleet", "Red Search Operation", "Red Base Assault Operation", "Green Evacuation Operation",
                "Yellow Trade Summit Operation", "Red Northern Wall", "Silent Fleet", "Automated Drone Swarm",
                "Rogue AI Fleet", "False Convoy", "Last Stand Fleet"
        );
        Set<String> found = new HashSet<>();
        for (Object force : st.campaignForces) {
            String name = getObject(force, "name").toString();
            if (!expected.contains(name)) continue;
            found.add(name);
            assertFalse(getObject(force, "homeBaseId").toString().isBlank(), name + " should declare a home base");
            assertFalse(((List<?>) getObject(force, "routePoints")).isEmpty(), name + " should have a route");
            assertFalse("HOLDING".equals(getObject(force, "intent").toString()), name + " should have an active intent");
        }
        assertEquals(expected, found);

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 24);
        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        boolean restoredNorthernWall = false;
        for (Object force : restored.campaign.campaignForces) {
            if ("Red Northern Wall".equals(getObject(force, "name"))) {
                restoredNorthernWall = true;
                break;
            }
        }
        assertTrue(restoredNorthernWall);
    }

    @Test
    void lateCampaignReadoutsDoNotRecreateTrimmedChecklistCatalog() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 24;
        st.sectorElapsed = 121.0;
        invokePrivate("syncCampaignForceSimulationSeeds",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);
        invokePrivate("updateCampaignForceSimulation",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.2);
        int catalogCount = st.campaignForces.size();
        int activeCount = simulationActiveNpcCount(st);
        int nextId = st.nextCampaignForceId;

        long start = System.nanoTime();
        for (int i = 0; i < 120; i++) {
            CampaignSystem.activeSupportMarkers(ctx);
            CampaignSystem.selectedLocationSidebarLines(ctx);
            CampaignSystem.campaignForceSummaries(ctx);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("lateCampaignReadouts forces=" + st.campaignForces.size()
                + " active=" + activeCount
                + " allocated=" + (st.nextCampaignForceId - nextId)
                + " elapsedMs=" + elapsedMs);

        assertTrue(activeCount <= 96, "simulation should enforce the bounded NPC roster");
        assertEquals(catalogCount, st.campaignForces.size(), "read-only map paths must not recreate checklist fleets");
        assertEquals(nextId, st.nextCampaignForceId, "read-only map paths must not allocate campaign forces");

        for (int i = 0; i < 20; i++) {
            invokePrivate("updateCampaignForceSimulation",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    ctx, st, 0.2);
        }
        assertTrue(simulationActiveNpcCount(st) <= 96, "simulation ticks should retain the bounded NPC roster");
        assertEquals(catalogCount, st.campaignForces.size(), "simulation ticks should retain stable catalog entries");
        assertEquals(nextId, st.nextCampaignForceId, "simulation ticks must not rebuild checklist catalog entries");
    }

    @Test
    void unattendedTheaterContinuesAdvancing() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        int tickBefore = st.campaignForceSimTickCount;
        int theaterTickBefore = st.theaterWarTickIndex;
        invokePrivate("updateStrategicOvermapCampaign",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 301.0);
        assertTrue(st.campaignForceSimTickCount > tickBefore);
        assertTrue(st.theaterWarTickIndex > theaterTickBefore);
        assertTrue(st.factionDirectorAccumulatorSec >= 0.0);
    }

    @Test
    void hostileResponseUsesPendingQueueInsteadOfDirectSpawn() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        int searchGroupsBefore = st.galaxySearchGroups.size();
        queuePendingReinforcement(ctx, st);
        assertEquals(searchGroupsBefore, st.galaxySearchGroups.size());
        assertEquals(1, st.pendingHostileReinforcements.size());
    }

    @Test
    void openingRedWaveKeepsAuthoredRoutes() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokePrivate("syncCampaignForceSimulationSeeds",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);
        int checked = 0;
        for (Object force : st.campaignForces) {
            String name = getObject(force, "name").toString().toUpperCase();
            if (!name.equals("RED SCOUT PAIR") && !name.equals("RED PATROL GROUP") && !name.equals("RED INTERCEPTOR SQUADRON")) continue;
            assertFalse(((List<?>) getObject(force, "routePoints")).isEmpty(), name + " should keep an authored lane");
            checked++;
        }
        assertTrue(checked > 0, "expected at least one opening Red wave force");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 2468L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        CampaignSystem.campaignSummarySidebarLines(ctx);
        return ctx;
    }

    private static void queuePendingReinforcement(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        CampaignSystem.CampaignLocation location = st.galaxyAreasOfInterest.get(0);
        Class<?> doctrineType = findNestedClass("GalaxySearchDoctrine");
        invokePrivate("spawnIgnoredContactResponseGroup",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class, doctrineType},
                ctx, st, location, enumConstant(doctrineType, "INTERDICTION_GROUP"));
    }

    private static Object firstForceForFaction(CampaignSystem.CampaignState st, Faction faction) throws Exception {
        for (Object force : st.campaignForces) {
            if (getObject(force, "faction") == faction && !"PLAYER_FLEET".equals(getObject(force, "kind").toString())) {
                return force;
            }
        }
        return null;
    }

    private static Object ensureGreenRegressionPatrol(CampaignSystem.CampaignState st) throws Exception {
        Object green = firstForceForFaction(st, Faction.TEAM_C);
        if (green != null) return green;
        return invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Green Regression Patrol", "Green test yard", "Defend local route", 2240.0, 2200.0);
    }

    private static int simulationActiveNpcCount(CampaignSystem.CampaignState st) throws Exception {
        int count = 0;
        for (Object force : st.campaignForces) {
            if (!getBoolean(force, "destroyed")
                    && getBoolean(force, "simulationActive")
                    && !"PLAYER_FLEET".equals(getObject(force, "kind").toString())) {
                count++;
            }
        }
        return count;
    }

    private static Object firstForceForFactionAndKind(CampaignSystem.CampaignState st, Faction faction, String kind) throws Exception {
        for (Object force : st.campaignForces) {
            if (getObject(force, "faction") == faction && kind.equals(getObject(force, "kind").toString())) return force;
        }
        return null;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static Object invokePrivate(String methodName, Class<?>[] signature, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(methodName, signature);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Class<?> findNestedClass(String simpleName) {
        for (Class<?> nested : CampaignSystem.class.getDeclaredClasses()) {
            if (simpleName.equals(nested.getSimpleName())) return nested;
        }
        throw new IllegalArgumentException(simpleName);
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static Object getObject(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static double getDouble(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setDouble(Object target, String name, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(target, value);
    }
}
