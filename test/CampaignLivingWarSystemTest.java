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
        assertFalse(getObject(battle, "loserFollowUp").toString().isBlank());
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
                ctx, st, 6.8);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.3);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 10.0);

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
    void factionDirectorScorecardsExposeRequiredRegionalJobs() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokePrivate("updateFactionDirectors",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, ctx.campaign, 12.0);

        List<String> lines = CampaignSystem.campaignFactionDirectorScoreLines(ctx);
        String green = lines.stream().filter(line -> line.startsWith("Green")).findFirst().orElse("");
        String yellow = lines.stream().filter(line -> line.startsWith("Yellow")).findFirst().orElse("");
        String red = lines.stream().filter(line -> line.startsWith("Red")).findFirst().orElse("");

        assertTrue(green.contains("route-defense") && green.contains("convoy-escort")
                && green.contains("base-defense") && green.contains("repair-rescue")
                && green.contains("controlled-assault") && green.contains("player-support"));
        assertTrue(yellow.contains("trade-profit") && yellow.contains("mining-profit")
                && yellow.contains("salvage-opportunity") && yellow.contains("smuggling-route")
                && yellow.contains("mercenary-contract") && yellow.contains("piracy-opportunity"));
        assertTrue(red.contains("scouting") && red.contains("raiding-weak-routes")
                && red.contains("hunting-high-value") && red.contains("blockading-chokepoints")
                && red.contains("staging-siege") && red.contains("defending-red-assets")
                && red.contains("invasion-escalation"));
        assertTrue(ctx.campaign.greenDirectorBrief.contains("selected") && ctx.campaign.greenDirectorBrief.contains("top"));
        assertTrue(ctx.campaign.yellowDirectorBrief.contains("selected") && ctx.campaign.yellowDirectorBrief.contains("top"));
        assertTrue(ctx.campaign.redDirectorBrief.contains("selected") && ctx.campaign.redDirectorBrief.contains("top"));
    }

    @Test
    void factionDirectorsReserveHeavyFleetsAndRespectSupplyAndOperatingRadius() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object southernId = invokePrivate("theaterForPoint",
                new Class[]{CampaignSystem.CampaignState.class, double.class}, st, 4300.0);
        Object earthId = invokePrivate("theaterForPoint",
                new Class[]{CampaignSystem.CampaignState.class, double.class}, st, 500.0);
        Object southern = invokePrivate("campaignTheaterById",
                new Class[]{CampaignSystem.CampaignState.class, findNestedClass("TheaterId")}, st, southernId);
        Object earth = invokePrivate("campaignTheaterById",
                new Class[]{CampaignSystem.CampaignState.class, findNestedClass("TheaterId")}, st, earthId);
        setDouble(southern, "routeRisk", 95.0);
        setDouble(southern, "supplyState", 12.0);
        setDouble(southern, "yellowActivity", 100.0);
        setDouble(southern, "threatPressure", 8.0);
        setDouble(southern, "redPresence", 10.0);
        setDouble(southern, "marketPressure", 0.0);
        setDouble(southern, "danger", 30.0);
        setDouble(earth, "danger", 96.0);
        setDouble(earth, "routeRisk", 96.0);
        setDouble(earth, "threatPressure", 96.0);
        setDouble(earth, "supplyState", 65.0);

        Object heavyRed = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Dreadnought Director Reserve", "Southern depot", "Reserve heavy fleet", 2400.0, 4300.0);
        Object lowSupplyRed = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.ENEMY,
                "Red Low Supply Director Patrol", "Southern depot", "Low supply patrol", 2300.0, 4300.0);
        Object greenReserve = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.TEAM_C,
                "Green Radius Limited Assault Reserve", "Southern yard", "Regional reserve", 2450.0, 4300.0);

        setDouble(heavyRed, "supply", 90.0);
        setDouble(heavyRed, "fuelLevel", 90.0);
        setDouble(heavyRed, "operatingRadius", 1400.0);
        setDouble(lowSupplyRed, "supply", 18.0);
        setDouble(lowSupplyRed, "fuelLevel", 18.0);
        setDouble(greenReserve, "supply", 90.0);
        setDouble(greenReserve, "fuelLevel", 90.0);
        setDouble(greenReserve, "operatingRadius", 60.0);
        setObject(greenReserve, "intent", Enum.valueOf(
                (Class<Enum>) getObject(greenReserve, "intent").getClass(), "HOLDING"));

        invokePrivate("updateFactionDirectors",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 12.0);

        String heavyIntent = getObject(heavyRed, "intent").toString();
        assertFalse("INTERCEPTING".equals(heavyIntent) || "SEARCHING".equals(heavyIntent) || "PATROLLING".equals(heavyIntent),
                "heavy Red fleet should reserve for siege/blockade/invasion/defense work instead of becoming a random raider");
        assertEquals("RETREATING", getObject(lowSupplyRed, "intent").toString(),
                "low supply fleets should route to recovery before accepting scored regional work");
        String greenIntent = getObject(greenReserve, "intent").toString();
        assertFalse("REINFORCING".equals(greenIntent) || "ESCORTING".equals(greenIntent),
                "regional director should not pull a heavy Green reserve outside its operating radius");
    }

    @Test
    void yellowConvoyRetreatsWhenThreatened() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object convoy = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.CONVOY, Faction.BRIGHT_YELLOW,
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
                    st, CampaignSystem.CampaignForceKind.CONVOY, Faction.BRIGHT_YELLOW,
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
            assertFalse(getObject(force, "mission").toString().isBlank(), name + " should have a mission");
            if (name.startsWith("Red ")) {
                assertFalse("strategic-roaming-assignment".equals(getObject(force, "homeBaseId").toString()),
                        name + " should launch from a named Red origin when seeded late-campaign");
            }
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
    void redMajorFleetLaunchesFromNamedSourceAwayFromPlayerWithWarningTelemetry() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 24;
        st.sectorElapsed = 121.0;
        st.playerGalaxyX = 2400.0;
        st.playerGalaxyY = 2400.0;

        invokePrivate("syncCampaignForceSimulationSeeds",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);

        Set<String> majorNames = Set.of(
                "Red Siege Fleet",
                "Red Carrier Strike Group",
                "Red Dreadnought Task Force",
                "Red Pursuit Armada",
                "Red Planetary Suppression Fleet",
                "Red Flagship Fleet",
                "Red Northern Wall");
        boolean foundMajor = false;
        for (Object force : st.campaignForces) {
            String name = getObject(force, "name").toString();
            if (!majorNames.contains(name)) continue;
            foundMajor = true;
            assertFalse(getObject(force, "sourceLocationId").toString().isBlank(), name + " should have a named source");
            assertFalse("strategic-roaming-assignment".equals(getObject(force, "homeBaseId").toString()));
            assertFalse(((List<?>) getObject(force, "routePoints")).isEmpty(), name + " should expose route telemetry");
            assertTrue(Math.hypot(getDouble(force, "x") - st.playerGalaxyX, getDouble(force, "y") - st.playerGalaxyY) > 600.0,
                    name + " should not spawn next to the player");
            assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("MAJOR RED LAUNCH WARNING")
                            && line.contains(name)
                            && line.contains("source")
                            && line.contains("target")
                            && line.contains("telemetry")),
                    name + " should emit source and warning telemetry");
        }
        assertTrue(foundMajor, "late campaign should seed at least one major Red fleet");
    }

    @Test
    void campaignForceSummariesExposeMissionOriginAndDestination() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.sector = 24;
        st.sectorElapsed = 121.0;
        invokePrivate("syncCampaignForceSimulationSeeds",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);

        CampaignSystem.CampaignForceSummary red = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(summary -> summary.name.startsWith("Red "))
                .filter(summary -> !summary.mission.isBlank())
                .filter(summary -> !summary.homeBaseId.isBlank())
                .filter(summary -> !summary.destinationLocationId.isBlank()
                        || summary.targetX != summary.x
                        || summary.targetY != summary.y)
                .findFirst()
                .orElseThrow();

        assertFalse(red.mission.isBlank());
        assertFalse(red.homeBaseId.isBlank());
        assertFalse("strategic-roaming-assignment".equals(red.homeBaseId));
        assertFalse(red.destinationLocationId.isBlank() && red.targetX == red.x && red.targetY == red.y);
    }

    @Test
    void strategicBattleEventsEmitPrioritizedAudioTelemetry() throws Exception {
        AudioSystem.setTelemetryOnly(true);
        try {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;
            Object red = firstForceForFaction(st, Faction.ENEMY);
            Object green = ensureGreenRegressionPatrol(st);
            setDouble(red, "x", 2200.0);
            setDouble(red, "y", 2200.0);
            setDouble(green, "x", 2240.0);
            setDouble(green, "y", 2200.0);
            setDouble(red, "strength", 80.0);
            setDouble(green, "strength", 80.0);
            st.playerGalaxyX = 2200.0;
            st.playerGalaxyY = 2200.0;

            invokePrivate("formCampaignBattle",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                            findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                    ctx, st, red, green);
            invokePrivate("updateCampaignBattles",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    ctx, st, 17.0);

            assertTrue(ctx.audioEvents.stream().anyMatch(event -> event.eventId.contains("warp.charge_start")),
                    "battle start should emit a strategic alert cue");
            assertTrue(ctx.audioEvents.stream().anyMatch(event -> event.eventId.contains("impact.explosion")),
                    "battle end should emit a resolved-battle cue");
        } finally {
            AudioSystem.setTelemetryOnly(false);
        }
    }

    @Test
    void resolvedStrategicBattleCreatesWreckAndYellowSalvageAssignment() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        int wrecksBefore = st.recoverableWreckSites.size();
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(red, "strength", 80.0);
        setDouble(green, "strength", 80.0);
        st.playerGalaxyX = 2200.0;
        st.playerGalaxyY = 2200.0;

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 11.0);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 6.0);

        assertTrue(st.recoverableWreckSites.size() > wrecksBefore,
                "resolved battle should add a recoverable wreck or battle scar");
        Object salvageForce = null;
        for (Object force : st.campaignForces) {
            if (force == null) continue;
            if (getObject(force, "name").toString().contains("Salvage")
                    && "SALVAGE".equals(getObject(force, "cargoKind").toString())) {
                salvageForce = force;
                break;
            }
        }
        assertNotNull(salvageForce, "battle aftermath should assign or create a Yellow salvage-capable force");
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("Battle Scar")
                        || line.contains("salvage")),
                "battle aftermath should be visible in the theater event feed");

        Object wreck = st.recoverableWreckSites.get(st.recoverableWreckSites.size() - 1);
        double wreckX = getDouble(wreck, "x");
        double wreckY = getDouble(wreck, "y");
        CampaignSystem.CampaignLocation returnHub = (CampaignSystem.CampaignLocation) invokePrivate("nearestFriendlySupportHub",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, Faction.class, double.class, double.class},
                ctx, st, Faction.BRIGHT_YELLOW, wreckX, wreckY);
        assertNotNull(returnHub, "battle salvage test needs a Yellow support hub for the return leg");
        setObject(salvageForce, "sourceLocationId", returnHub.id);
        setDouble(salvageForce, "x", wreckX + 10.0);
        setDouble(salvageForce, "y", wreckY + 10.0);
        setDouble(salvageForce, "cargoLoad", 0.0);
        setDouble(salvageForce, "cargoCapacity", 10.0);
        invokePrivate("assignSalvageMission",
                new Class[]{CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"),
                        double.class, double.class, String.class},
                st, salvageForce, wreckX, wreckY, returnHub.id);
        setDouble(salvageForce, "cargoCapacity", 10.0);
        setDouble(salvageForce, "workRemainingSec", 0.0);
        setDouble(salvageForce, "taskDeadlineSec", 0.0);
        invokePrivate("updateCampaignForceLifecycleAfterMovement",
                new Class[]{CampaignSystem.CampaignState.class, findNestedClass("CampaignForce"), double.class},
                st, salvageForce, 2.0);

        assertEquals("DOCKING", getObject(salvageForce, "intent").toString(),
                "full salvage cargo should leave the battle scar and return to a hub");
        assertEquals(returnHub.id, getObject(salvageForce, "destinationLocationId"));
        assertFalse(((List<?>) getObject(salvageForce, "routePoints")).isEmpty());
    }

    @Test
    void greenRedBattleProducesScarControlShiftAndNearbyFleetReaction() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = firstForceForFaction(st, Faction.ENEMY);
        Object green = ensureGreenRegressionPatrol(st);
        Object reserve = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.PATROL_GROUP, Faction.TEAM_C,
                "Green Aftermath Reserve", "Green relay reserve", "React to battle aftermath", 2540.0, 2200.0);
        int wrecksBefore = st.recoverableWreckSites.size();
        Object theaterId = invokePrivate("theaterForPoint",
                new Class[]{CampaignSystem.CampaignState.class, double.class}, st, 2200.0);
        Object theater = invokePrivate("campaignTheaterById",
                new Class[]{CampaignSystem.CampaignState.class, theaterId.getClass()},
                st, theaterId);
        double controlBefore = getDouble(theater, "controlScore");
        for (Object force : st.campaignForces) {
            if (force == null) continue;
            if (force == red || force == green || force == reserve) continue;
            setDouble(force, "x", 4200.0);
            setDouble(force, "y", 4200.0);
        }
        setDouble(red, "x", 2200.0);
        setDouble(red, "y", 2200.0);
        setDouble(green, "x", 2240.0);
        setDouble(green, "y", 2200.0);
        setDouble(reserve, "x", 2540.0);
        setDouble(reserve, "y", 2200.0);
        setDouble(red, "strength", 180.0);
        setDouble(red, "readiness", 100.0);
        setDouble(red, "supply", 100.0);
        setDouble(red, "hullIntegrity", 100.0);
        setDouble(red, "morale", 100.0);
        setDouble(red, "fuelPressure", 0.0);
        setDouble(green, "strength", 35.0);
        setDouble(green, "readiness", 18.0);
        setDouble(green, "supply", 18.0);
        setDouble(green, "hullIntegrity", 18.0);
        setDouble(green, "morale", 18.0);
        setDouble(reserve, "strength", 78.0);

        invokePrivate("formCampaignBattle",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        findNestedClass("CampaignForce"), findNestedClass("CampaignForce")},
                ctx, st, red, green);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 6.8);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.3);
        invokePrivate("updateCampaignBattles",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 10.0);

        assertTrue(st.recoverableWreckSites.size() > wrecksBefore,
                "resolved Green/Red battle should leave a battle scar or wreck marker");
        assertTrue(getDouble(theater, "controlScore") < controlBefore,
                "Red victory should shift regional control toward Red");
        boolean reserveReacted = getObject(reserve, "targetForceId").equals(getObject(green, "id"))
                || getObject(reserve, "targetForceId").equals(getObject(red, "id"));
        assertTrue(reserveReacted || st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("reacting to battle aftermath")
                        || line.contains("reinforcing after nearby battle")),
                "nearby Green force should react to battle aftermath");
        assertTrue("REPAIR".equals(getObject(reserve, "mission").toString())
                        || "REINFORCING".equals(getObject(reserve, "intent").toString())
                        || "GUARDING".equals(getObject(reserve, "intent").toString()),
                "battle-created wrecks should attract at least one valid follow-up behavior");
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
    void newlyCreatedRedForceBackfillsNamedSource() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object red = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Source Backfill Regression Fleet", "unresolved hostile contact",
                "Verify source backfill", 2650.0, 1620.0);

        String sourceId = getObject(red, "sourceLocationId").toString();
        String homeId = getObject(red, "homeBaseId").toString();
        assertFalse(sourceId.isBlank());
        assertFalse(homeId.isBlank());
        assertFalse("strategic-roaming-assignment".equals(sourceId));
        assertFalse("strategic-roaming-assignment".equals(homeId));
        assertNotNull(invokePrivate("campaignLocationById",
                new Class[]{CampaignSystem.CampaignState.class, String.class}, st, sourceId));
    }

    @Test
    void sensorNetPrioritizesRealNearbyContactsAndFiltersGhosts() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object hunter = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Red Sensor Regression Hunter", "red regression yard", "Intercept player", 2580.0, 2500.0);
        setDouble(hunter, "x", 2580.0);
        setDouble(hunter, "y", 2500.0);
        setDouble(hunter, "targetX", 2500.0);
        setDouble(hunter, "targetY", 2500.0);
        setDouble(hunter, "strength", 132.0);
        setDouble(hunter, "readiness", 94.0);
        setDouble(hunter, "contactConfidence", 0.92);
        setObject(hunter, "intent", CampaignSystem.CampaignForceIntent.INTERCEPTING);

        Object destroyed = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Destroyed Sensor Regression Ghost", "red regression yard", "Should be hidden", 2520.0, 2520.0);
        setObject(destroyed, "destroyed", true);

        Object stale = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Stale Sensor Regression Ghost", "red regression yard", "Should be hidden", 2540.0, 2540.0);
        setDouble(stale, "strength", 12.0);
        setDouble(stale, "contactConfidence", 0.05);
        setDouble(stale, "lastKnownAgeSec", 80.0);
        setDouble(stale, "lastKnownX", 2540.0);
        setDouble(stale, "lastKnownY", 2540.0);
        setObject(stale, "contactState", enumConstant(findNestedClass("CampaignForceContactState"), "STALE"));

        List<CampaignSystem.CampaignContactReadout> contacts = CampaignSystem.campaignNearbyContactReadouts(ctx, 8);
        assertTrue(contacts.stream().anyMatch(contact -> contact.title.equals("Red Sensor Regression Hunter")
                && contact.detail.contains("Confirmed")
                && contact.detail.contains("severe threat")));
        assertFalse(contacts.stream().anyMatch(contact -> contact.title.contains("Destroyed Sensor Regression Ghost")));
        assertFalse(contacts.stream().anyMatch(contact -> contact.title.contains("Stale Sensor Regression Ghost")));

        List<GameRenderSystem.SensorNetEntry> entries = GameRenderSystem.sensorNetEntries(ctx, 0, 0);
        assertTrue(entries.stream().anyMatch(entry -> "NEARBY CONTACTS".equals(entry.section)
                && entry.title.equals("Red Sensor Regression Hunter")));
    }

    @Test
    void confirmedInterceptLinesExcludeDestroyedAndStaleContacts() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;

        Object confirmed = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Confirmed Intercept Regression Fleet", "red regression yard", "Intercept player", 2700.0, 2500.0);
        setDouble(confirmed, "x", 2700.0);
        setDouble(confirmed, "y", 2500.0);
        setDouble(confirmed, "targetX", 2500.0);
        setDouble(confirmed, "targetY", 2500.0);
        setDouble(confirmed, "contactConfidence", 0.95);
        setObject(confirmed, "intent", CampaignSystem.CampaignForceIntent.INTERCEPTING);
        setObject(confirmed, "state", enumConstant(findNestedClass("CampaignFleetState"), "PURSUING"));
        setObject(confirmed, "contactState", enumConstant(findNestedClass("CampaignForceContactState"), "KNOWN"));

        Object stale = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Stale Intercept Regression Fleet", "red regression yard", "Fake intercept", 2760.0, 2500.0);
        setDouble(stale, "contactConfidence", 0.9);
        setObject(stale, "intent", CampaignSystem.CampaignForceIntent.INTERCEPTING);
        setObject(stale, "state", enumConstant(findNestedClass("CampaignFleetState"), "PURSUING"));
        setObject(stale, "contactState", enumConstant(findNestedClass("CampaignForceContactState"), "STALE"));

        Object destroyed = invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, CampaignSystem.CampaignForceKind.TASK_FORCE, Faction.ENEMY,
                "Destroyed Intercept Regression Fleet", "red regression yard", "Fake intercept", 2820.0, 2500.0);
        setDouble(destroyed, "contactConfidence", 0.95);
        setObject(destroyed, "intent", CampaignSystem.CampaignForceIntent.INTERCEPTING);
        setObject(destroyed, "state", enumConstant(findNestedClass("CampaignFleetState"), "PURSUING"));
        setObject(destroyed, "contactState", enumConstant(findNestedClass("CampaignForceContactState"), "KNOWN"));
        setObject(destroyed, "destroyed", true);
        st.activeGalaxyEncounterForceIds.add((Integer) getObject(destroyed, "id"));

        List<CampaignSystem.CampaignInterceptLine> lines = CampaignSystem.confirmedPlayerInterceptLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.label.equals("Confirmed Intercept Regression Fleet")));
        assertFalse(lines.stream().anyMatch(line -> line.label.contains("Stale Intercept")));
        assertFalse(lines.stream().anyMatch(line -> line.label.contains("Destroyed Intercept")));
        assertFalse(st.activeGalaxyEncounterForceIds.contains((Integer) getObject(destroyed, "id")),
                "cleanup should remove destroyed active encounter force refs");
    }

    @Test
    void pendingMajorRedLaunchEmitsEarlyMidAndFinalWarnings() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        queuePendingReinforcement(ctx, st);
        Object pending = st.pendingHostileReinforcements.get(0);

        invokePrivate("updatePendingHostileReinforcements",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.1);
        setDouble(pending, "etaSec", 12.0);
        invokePrivate("updatePendingHostileReinforcements",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.1);
        setDouble(pending, "etaSec", 5.0);
        invokePrivate("updatePendingHostileReinforcements",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, st, 0.1);

        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("MAJOR RED LAUNCH EARLY WARNING")
                && line.contains("source") && line.contains("target") && line.contains("ETA")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("MAJOR RED LAUNCH MID-ROUTE CONTACT")
                && line.contains("ETA")));
        assertTrue(st.theaterWarRecentEvents.stream().anyMatch(line -> line.contains("MAJOR RED LAUNCH FINAL ARRIVAL WARNING")
                && line.contains("ETA")));
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

    private static void setObject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
