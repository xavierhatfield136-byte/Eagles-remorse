import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignFleetEscalationTest {
    @Test
    void grandFleetProfileDrivesPunishmentTierResponse() {
        ArrayList<ShipRole> roles = repeat(ShipRole.FRIGATE, 31);
        roles.addAll(List.of(
                ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER, ShipRole.DREADNOUGHT,
                ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.SUPERSHIP,
                ShipRole.INTERDICTION_TITAN, ShipRole.ARTILLERY_TITAN, ShipRole.HYPERWEAPON_TITAN));

        FleetClassifier.FleetProfile profile = FleetClassifier.classifyRoles(roles);

        assertEquals(FleetLevel.LEVEL_5_GRAND_FLEET, profile.level);
        assertEquals(8, CampaignSystem.strategicResponseTierForPlayerProfile(profile, 42.0));
    }

    @Test
    void grandFleetRedTaskForceManifestCommitsCapitalAndTitanResponse() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object force = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.ENEMY,
                "Red Grand Fleet Response",
                "Regional command",
                "Concentrate capital groups against Blue Grand Fleet",
                st.playerGalaxyX + 900.0,
                st.playerGalaxyY);
        setField(force, "strength", 100.0);
        setField(force, "y", 2500.0);
        setField(force, "intent", CampaignSystem.CampaignForceIntent.INTERCEPTING);

        Object manifest = invokePrivate("encounterManifestForForce",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, force.getClass(), int.class},
                ctx, st, force, 10);
        List<ShipRole> roles = manifestRoles(manifest);

        assertTrue(roles.contains(ShipRole.INTERDICTION_TITAN));
        assertTrue(roles.contains(ShipRole.ARTILLERY_TITAN));
        assertTrue(roles.contains(ShipRole.HYPERWEAPON_TITAN));
        assertTrue(roles.contains(ShipRole.DREADNOUGHT) || roles.contains(ShipRole.BATTLESHIP));
        assertTrue(roles.size() >= 9, "Grand Fleet response should not drip-feed a tiny patrol");
    }

    @Test
    void titanHeavyRedContactGetsEscortsInsteadOfFiveShipManifest() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object force = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.ENEMY,
                "Red Five Ship Titan Contact",
                "Earthward command",
                "Titan-heavy force should bring a screen",
                st.playerGalaxyX + 900.0,
                2400.0);
        setField(force, "strength", 100.0);
        addPoolShips(st, (int) readField(force, "id"),
                ShipRole.INTERDICTION_TITAN, ShipRole.ARTILLERY_TITAN,
                ShipRole.DREADNOUGHT, ShipRole.FRIGATE, ShipRole.MISSILE_BOAT);

        Object manifest = invokePrivate("encounterManifestForForce",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, force.getClass(), int.class},
                ctx, st, force, 24);
        List<ShipRole> roles = manifestRoles(manifest);

        assertTrue(roles.size() >= 18,
                "four-star Titan contacts should deploy with line ships and escorts, not just their raw pool trickle: " + roles);
        assertTrue(roles.contains(ShipRole.HYPERWEAPON_TITAN)
                        || roles.contains(ShipRole.BULWARK_TITAN)
                        || roles.contains(ShipRole.SUPERSHIP),
                "Red top-up should expose late-catalog heavy hulls");
        assertTrue(roles.stream().filter(role -> role == ShipRole.FRIGATE
                        || role == ShipRole.PICKET
                        || role == ShipRole.CIWS_CORVETTE
                        || role == ShipRole.MISSILE_BOAT).count() >= 4,
                "Titan groups need a proper screen");
    }

    @Test
    void tacticalEncounterManifestOffsetsDoNotStackRepeatedRoles() throws Exception {
        double[] first = (double[]) invokePrivate("tacticalFormationOffset",
                new Class[]{CampaignSystem.FleetFormationRole.class, int.class, boolean.class},
                CampaignSystem.FleetFormationRole.VANGUARD, 0, false);
        double[] repeated = (double[]) invokePrivate("tacticalFormationOffset",
                new Class[]{CampaignSystem.FleetFormationRole.class, int.class, boolean.class},
                CampaignSystem.FleetFormationRole.VANGUARD, 6, false);

        assertTrue(Math.hypot(first[0] - repeated[0], first[1] - repeated[1]) >= 0.45,
                "repeated manifest roles need row/depth separation instead of stacked spawn points");
    }

    @Test
    void persistentFleetDeploymentHonorsSelectedScreenFormation() throws Exception {
        double[] line = (double[]) invokePrivate("persistentFleetDeploymentPoint",
                new Class[]{double.class, double.class, double.class, int.class, int.class, double.class, boolean.class,
                        GameContext.FleetFormation.class, GameContext.FleetCommand.class},
                1000.0, 1000.0, 0.0, 1, 8, 30.0, false,
                GameContext.FleetFormation.LINE, GameContext.FleetCommand.AUTO);
        double[] screen = (double[]) invokePrivate("persistentFleetDeploymentPoint",
                new Class[]{double.class, double.class, double.class, int.class, int.class, double.class, boolean.class,
                        GameContext.FleetFormation.class, GameContext.FleetCommand.class},
                1000.0, 1000.0, 0.0, 1, 8, 30.0, false,
                GameContext.FleetFormation.SCREEN, GameContext.FleetCommand.AUTO);

        assertTrue(Math.hypot(screen[0] - 1000.0, screen[1] - 1000.0) > 130.0);
        assertTrue(Math.abs(screen[0] - line[0]) > 80.0 || Math.abs(screen[1] - line[1]) > 80.0,
                "campaign deployment should vary with the selected formation instead of always trailing in line");
    }

    @Test
    void earthwardRedWarPresenceIsSeededAndVisible() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        for (String name : List.of("Red Earthward Grand Fleet", "Red Luna Titan Reserve",
                "Red Typhon Dreadnought Sortie", "Red High Orbit Terminal Guard")) {
            Object force = campaignForceByName(st, name);
            assertTrue(force != null, "expected seeded Earthward Red force: " + name);
            assertEquals(Faction.ENEMY, readField(force, "faction"));
            assertEquals(true, readField(force, "visibleToPlayer"));
            assertTrue((double) readField(force, "strength") >= 94.0);
        }
    }

    @Test
    void grandFleetResponseRalliesRedForcesAndMergesOnlyAfterArrival() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object patrol = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.ENEMY,
                "Red Rally Candidate",
                "Nearby patrol",
                "Available patrol should rally before engaging",
                st.playerGalaxyX + 720.0,
                st.playerGalaxyY + 40.0);
        setField(patrol, "strength", 34.0);

        CampaignSystem.GalaxySearchGroup response = new CampaignSystem.GalaxySearchGroup(
                777,
                "Red Punishment Fleet",
                st.playerGalaxyX + 240.0,
                st.playerGalaxyY,
                150.0,
                430.0,
                300.0,
                1.0f,
                CampaignSystem.CampaignLocationType.ENEMY_ACTIVITY,
                8);
        response.doctrine = CampaignSystem.GalaxySearchDoctrine.PUNISHMENT_FLEET;
        response.behavior = CampaignSystem.GalaxySearchBehavior.INTERCEPTING;
        response.targetX = st.playerGalaxyX;
        response.targetY = st.playerGalaxyY;
        st.galaxySearchGroups.add(response);

        int rallied = (int) invokePrivate("rallyNearbyRedForcesAgainstPlayerThreat",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class,
                        CampaignSystem.GalaxySearchGroup.class, FleetClassifier.FleetProfile.class},
                ctx, st, response, grandFleetProfile());

        assertEquals(1, rallied);
        assertEquals("REGROUPING", String.valueOf(readField(patrol, "intent")));
        assertTrue((int) readField(patrol, "parentForceId") > 0);
        assertFalse((boolean) readField(patrol, "destroyed"),
                "rallying should not teleport-merge before the force reaches the rally point");

        setField(patrol, "x", response.x);
        setField(patrol, "y", response.y);
        invokePrivate("mergeCampaignForceIfNeeded",
                new Class[]{CampaignSystem.CampaignState.class, patrol.getClass()},
                st, patrol);

        assertTrue((boolean) readField(patrol, "destroyed"));
        assertTrue(String.valueOf(readField(patrol, "removalReason")).contains("merged_into_parent"));
    }

    @Test
    void blueRejoinRecoveryDoesNotAbsorbGreenYellowOrCivilianTraffic() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        int before = st.persistentBlueFleet.size();
        double x = ctx.player.x + 40.0;
        double y = ctx.player.y + 30.0;

        FleetShip green = new FleetShip(ShipRole.CRUISER, Faction.TEAM_C, x, y);
        green.name = "Green Nearby Cruiser";
        FleetShip yellow = new FleetShip(ShipRole.FRIGATE, Faction.BRIGHT_YELLOW, x + 20.0, y);
        yellow.name = "Bright Yellow Escort";
        FleetShip civilian = new FleetShip(ShipRole.HAULER, Faction.ALLY, x + 40.0, y);
        civilian.name = "Blue Civilian Hauler";
        FleetShip blue = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, x + 60.0, y);
        blue.name = "Blue Rejoin Frigate";

        ctx.ships.add(green);
        ctx.ships.add(yellow);
        ctx.ships.add(civilian);
        ctx.ships.add(blue);
        st.shipCampaignProvenance.put(blue.id, CampaignShipProvenance.PLAYER_ROSTER);

        invokePrivate("recoverBlueRejoinsNearFlagship",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class},
                ctx, st);

        assertEquals(before + 1, st.persistentBlueFleet.size());
        assertTrue(st.persistentBlueFleet.stream().anyMatch(entry -> "Blue Rejoin Frigate".equals(entry.name)));
        assertFalse(st.persistentBlueFleet.stream().anyMatch(entry -> "Green Nearby Cruiser".equals(entry.name)));
        assertFalse(st.persistentBlueFleet.stream().anyMatch(entry -> "Bright Yellow Escort".equals(entry.name)));
        assertFalse(st.persistentBlueFleet.stream().anyMatch(entry -> "Blue Civilian Hauler".equals(entry.name)));
    }

    @Test
    void campaignForceMarkersExposeFleetLevelAsStarsInsteadOfLevelNames() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object force = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.TEAM_C,
                "Green Star Marker Test",
                "Coalition command",
                "Visible fleet classification marker",
                st.playerGalaxyX + 500.0,
                st.playerGalaxyY + 120.0);
        setField(force, "visibleToPlayer", true);
        setField(force, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        setField(force, "contactConfidence", 1.0);
        setField(force, "simulationActive", true);
        java.util.Set<Integer> shipIds = (java.util.Set<Integer>) readField(force, "shipIds");
        int forceId = (int) readField(force, "id");
        for (int i = 0; i < 36; i++) {
            FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, st.playerGalaxyX + 500.0 + i, st.playerGalaxyY);
            ctx.ships.add(ship);
            shipIds.add(ship.id);
            st.shipCampaignForceIds.put(ship.id, forceId);
        }
        for (ShipRole role : List.of(ShipRole.DREADNOUGHT, ShipRole.CARRIER, ShipRole.DRONE_CARRIER,
                ShipRole.SUPERSHIP, ShipRole.DREADNOUGHT, ShipRole.CARRIER, ShipRole.DRONE_CARRIER,
                ShipRole.INTERDICTION_TITAN, ShipRole.ARTILLERY_TITAN, ShipRole.HYPERWEAPON_TITAN)) {
            FleetShip ship = new FleetShip(role, Faction.TEAM_C, st.playerGalaxyX + 530.0, st.playerGalaxyY);
            ctx.ships.add(ship);
            shipIds.add(ship.id);
            st.shipCampaignForceIds.put(ship.id, forceId);
        }

        CampaignSystem.CampaignSupportMarker marker = (CampaignSystem.CampaignSupportMarker) invokePrivate(
                "supportMarkerForCampaignForce",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, force.getClass()},
                ctx, st, force);

        assertEquals(5, Renderer.strategicFleetMarkerStarCountForTest(marker),
                marker == null ? "null marker" : marker.subtitle);
        assertFalse(marker.subtitle.contains("Grand Fleet"));
        assertFalse(marker.subtitle.contains("Level 5"));

        BufferedImage scratch = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scratch.createGraphics();
        List<Renderer.StrategicSupportLabelLayout> layouts;
        try {
            layouts = Renderer.strategicSupportMarkerLabelLayoutsForTest(g2, ctx,
                    new Rectangle(80, 64, 740, 472), List.of(marker),
                    st.playerGalaxyX - 400.0, st.playerGalaxyY - 300.0, 900.0, 650.0);
        } finally {
            g2.dispose();
        }
        assertEquals(1, layouts.size());
        assertEquals(5, layouts.get(0).starCount(),
                "fleet power stars should live in the label box above the fleet title");
        assertTrue(layouts.get(0).textY() > layouts.get(0).bounds().y + 12,
                "fleet label text should sit under the star row, not under the map ring");
    }

    @Test
    void oversizedBlueFleetCapsCoalitionJoinersByFactionAndPrefersHeavyForces() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        ctx.ships.removeIf(ship -> ship != ctx.player);
        st.campaignForces.clear();
        st.campaignShipPool.clear();
        st.shipCampaignForceIds.clear();
        st.shipCampaignSpawnCategories.clear();
        st.tacticalShipPoolRecordIds.clear();
        replacePersistentFleet(st, repeat(ShipRole.FRIGATE, 31));

        Object smallGreen = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.PATROL_GROUP,
                Faction.TEAM_C,
                "Green Small Screen",
                "Nearby screen",
                "Small support should wait behind heavier groups",
                st.playerGalaxyX + 120.0,
                st.playerGalaxyY);
        setField(smallGreen, "visibleToPlayer", true);
        setField(smallGreen, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        setField(smallGreen, "intent", CampaignSystem.CampaignForceIntent.ESCORTING);
        addPoolShips(st, (int) readField(smallGreen, "id"), ShipRole.PICKET, ShipRole.PATROL, ShipRole.CIWS_CORVETTE);

        Object heavyGreen = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.TEAM_C,
                "Green Heavy Response",
                "Nearby task force",
                "Heavy local support should answer first",
                st.playerGalaxyX + 360.0,
                st.playerGalaxyY);
        setField(heavyGreen, "visibleToPlayer", true);
        setField(heavyGreen, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        setField(heavyGreen, "intent", CampaignSystem.CampaignForceIntent.REINFORCING);
        addPoolShips(st, (int) readField(heavyGreen, "id"),
                ShipRole.DREADNOUGHT, ShipRole.CARRIER, ShipRole.BATTLESHIP, ShipRole.FRIGATE);

        Object yellow = ensureCampaignForce(st,
                CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.BRIGHT_YELLOW,
                "Yellow Heavy Response",
                "Nearby task force",
                "Yellow support budget should be separate",
                st.playerGalaxyX + 420.0,
                st.playerGalaxyY + 60.0);
        setField(yellow, "visibleToPlayer", true);
        setField(yellow, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        setField(yellow, "intent", CampaignSystem.CampaignForceIntent.REINFORCING);
        addPoolShips(st, (int) readField(yellow, "id"),
                ShipRole.BATTLECRUISER, ShipRole.CRUISER, ShipRole.FRIGATE, ShipRole.PICKET);

        int spawned = (int) invokePrivate("spawnSensorBubbleCampaignForceParticipants",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, heavyGreen.getClass(),
                        CampaignSystem.TacticalApproachDirection.class, String.class},
                ctx, st, null, CampaignSystem.TacticalApproachDirection.EAST, "test_joiners");

        long greenSupport = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.TEAM_C
                        && "test_joiners".equals(st.shipCampaignSpawnCategories.get(ship.id))
                        && st.shipCampaignForceIds.getOrDefault(ship.id, 0) > 0)
                .count();
        long yellowSupport = ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == Faction.BRIGHT_YELLOW
                        && "test_joiners".equals(st.shipCampaignSpawnCategories.get(ship.id))
                        && st.shipCampaignForceIds.getOrDefault(ship.id, 0) > 0)
                .count();

        assertTrue(spawned >= 6, "enemy forces may also join, but coalition support should still be budgeted");
        assertEquals(3, greenSupport);
        assertEquals(3, yellowSupport);
        assertTrue(ctx.ships.stream().anyMatch(ship -> ship != null
                        && (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.CARRIER)
                        && "Green Heavy Response".equals(readForceNameForShip(st, ship))
                        && "test_joiners".equals(st.shipCampaignSpawnCategories.get(ship.id))),
                "capped Green support should spend its slots on the largest available local hulls: "
                        + spawnedJoinerSummary(ctx, st));
        assertFalse(ctx.ships.stream().anyMatch(ship -> ship != null && "Green Small Screen".equals(
                readForceNameForShip(st, ship)) && "test_joiners".equals(st.shipCampaignSpawnCategories.get(ship.id))),
                "small Green force should not consume the oversized-fleet support budget before heavier forces");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static Object ensureCampaignForce(CampaignSystem.CampaignState st,
                                              CampaignSystem.CampaignForceKind kind,
                                              Faction faction,
                                              String name,
                                              String origin,
                                              String purpose,
                                              double x,
                                              double y) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("ensureCampaignForce",
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignForceKind.class,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class);
        method.setAccessible(true);
        return method.invoke(null, st, kind, faction, name, origin, purpose, x, y);
    }

    private static Object invokePrivate(String methodName, Class<?>[] signature, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(methodName, signature);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static FleetClassifier.FleetProfile grandFleetProfile() {
        ArrayList<ShipRole> roles = repeat(ShipRole.FRIGATE, 31);
        roles.addAll(List.of(
                ShipRole.BATTLESHIP, ShipRole.BATTLECRUISER, ShipRole.DREADNOUGHT,
                ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.SUPERSHIP,
                ShipRole.INTERDICTION_TITAN, ShipRole.ARTILLERY_TITAN, ShipRole.HYPERWEAPON_TITAN));
        return FleetClassifier.classifyRoles(roles);
    }

    private static List<ShipRole> manifestRoles(Object manifest) throws Exception {
        ArrayList<ShipRole> roles = new ArrayList<>();
        Object value = readField(manifest, "ships");
        assertTrue(value instanceof List<?>);
        for (Object ship : (List<?>) value) {
            Object role = readField(ship, "role");
            assertTrue(role instanceof ShipRole);
            roles.add((ShipRole) role);
        }
        return roles;
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

    private static ArrayList<ShipRole> repeat(ShipRole role, int count) {
        ArrayList<ShipRole> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(role);
        return out;
    }

    private static void replacePersistentFleet(CampaignSystem.CampaignState st, List<ShipRole> roles) {
        st.persistentBlueFleet.clear();
        st.nextPersistentFleetSlotId = 1;
        for (ShipRole role : roles) {
            CampaignSystem.addPersistentFleetEntry(st, role, "Blue " + role.name(), 0, Faction.ALLY);
        }
    }

    private static void addPoolShips(CampaignSystem.CampaignState st, int forceId, ShipRole... roles) {
        Faction faction = Faction.ALLY;
        for (Object force : st.campaignForces) {
            try {
                if ((int) readField(force, "id") == forceId) {
                    faction = (Faction) readField(force, "faction");
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        for (ShipRole role : roles) {
            CampaignSystem.CampaignShipPoolRecord record = new CampaignSystem.CampaignShipPoolRecord(
                    st.nextCampaignShipRecordId++,
                    faction,
                    role,
                    CampaignSystem.CampaignShipPoolStatus.RESERVE,
                    "",
                    forceId,
                    100.0,
                    role.name());
            st.campaignShipPool.put(record.id, record);
        }
    }

    private static String readForceNameForShip(CampaignSystem.CampaignState st, Ship ship) {
        int forceId = st.shipCampaignForceIds.getOrDefault(ship.id, 0);
        for (Object force : st.campaignForces) {
            try {
                if ((int) readField(force, "id") == forceId) return String.valueOf(readField(force, "name"));
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static Object campaignForceByName(CampaignSystem.CampaignState st, String name) throws Exception {
        for (Object force : st.campaignForces) {
            if (name.equals(readField(force, "name"))) return force;
        }
        return null;
    }

    private static String spawnedJoinerSummary(GameContext ctx, CampaignSystem.CampaignState st) {
        ArrayList<String> parts = new ArrayList<>();
        for (Ship ship : ctx.ships) {
            if (ship == null || !"test_joiners".equals(st.shipCampaignSpawnCategories.get(ship.id))) continue;
            parts.add(ship.faction + ":" + ship.role + ":" + readForceNameForShip(st, ship));
        }
        return String.join(", ", parts);
    }
}
