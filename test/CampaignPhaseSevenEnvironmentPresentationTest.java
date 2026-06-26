import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseSevenEnvironmentPresentationTest {

    @Test
    void environmentProfilesSeparateIdentityArtRulesAndCounterplay() throws Exception {
        GameContext ctx = initializedCampaignContext(7101L);
        setModifiers(ctx.campaign, "NEBULA", "DEBRIS_FIELD", "GRAVITY_SHEAR");

        List<CampaignSystem.TacticalEnvironmentRule> rules = CampaignSystem.tacticalEnvironmentRules(ctx);
        String briefing = String.join("\n", CampaignSystem.tacticalEnvironmentBriefingLines(ctx));

        assertEquals(3, rules.size());
        assertTrue(briefing.contains("Sensor Shadow"));
        assertTrue(briefing.contains("Dense Debris Field"));
        assertTrue(briefing.contains("Gravity Shear"));
        assertTrue(briefing.contains("Sensors x"));
        assertTrue(briefing.contains("Movement x"));
        assertTrue(briefing.contains("Weapon range x"));
        assertTrue(briefing.contains("Hazard:"));
        assertTrue(briefing.contains("AI:"));
        assertTrue(briefing.contains("Counterplay:"));
    }

    @Test
    void sensorShadowReducesDetectionSymmetricallyForEverySensorSource() throws Exception {
        GameContext clear = initializedCampaignContext(7102L);
        GameContext shadow = initializedCampaignContext(7103L);
        setModifiers(clear.campaign, "NONE");
        setModifiers(shadow.campaign, "NEBULA");

        assertEquals(1.0, CampaignSystem.tacticalSensorMultiplier(clear), 0.0001);
        assertEquals(0.62, CampaignSystem.tacticalSensorMultiplier(shadow), 0.0001);
        assertTrue(CampaignSystem.targetingRangeMul(shadow) <= 1.0);

        clear.ships.clear();
        shadow.ships.clear();
        clear.player.x = shadow.player.x = 2500.0;
        clear.player.y = shadow.player.y = 2500.0;
        clear.ships.add(clear.player);
        shadow.ships.add(shadow.player);
        FleetShip source = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 2800.0, 2500.0);
        FleetShip wing = new FleetShip(ShipRole.PICKET, Faction.ALLY, 2200.0, 2500.0);
        clear.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 2800.0, 2500.0));
        clear.ships.add(new FleetShip(ShipRole.PICKET, Faction.ALLY, 2200.0, 2500.0));
        shadow.ships.add(source);
        shadow.ships.add(wing);
        FogOfWarSystem.reset(clear);
        FogOfWarSystem.reset(shadow);
        FogOfWarSystem.update(clear);
        FogOfWarSystem.update(shadow);
        assertTrue(shadow.fogOfWar.visibleCount() < clear.fogOfWar.visibleCount());
    }

    @Test
    void ionPulseAndMovementEffectsApplyToAllFactions() throws Exception {
        GameContext ctx = initializedCampaignContext(7104L);
        setModifiers(ctx.campaign, "EMP_ZONE", "GRAVITY_SHEAR");
        FleetShip ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 2200.0, 2400.0);
        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 2800.0, 2400.0);
        ally.vx = enemy.vx = 100.0;
        ctx.ships.add(ally);
        ctx.ships.add(enemy);
        ctx.campaign.sectorElapsed = 12.1;

        invokeLiveEnvironment(ctx, 0.2);

        assertTrue(ally.getTemporaryDisableRemaining() > 0.0);
        assertTrue(enemy.getTemporaryDisableRemaining() > 0.0);
        assertTrue(ally.vx < 100.0);
        assertTrue(enemy.vx < 100.0);
        assertEquals(ally.vx, enemy.vx, 0.0001);
    }

    @Test
    void quarantineExplainsRestrictionsConsequencesAndRecoveryWithoutBlockingProgress() throws Exception {
        GameContext ctx = initializedCampaignContext(7105L);
        setModifiers(ctx.campaign, "RESOURCE_DROUGHT");
        String restricted = String.join("\n", CampaignSystem.quarantineWarningLines(ctx));
        assertTrue(restricted.contains("QUARANTINED"));
        assertTrue(restricted.contains("repair"));
        assertTrue(restricted.contains("trade"));
        assertTrue(restricted.contains("rescue"));
        assertTrue(restricted.contains("no progression lock"));
        assertTrue(restricted.contains("friendly hub"));

        ctx.campaign.greenContractFavor = 4;
        String relieved = String.join("\n", CampaignSystem.quarantineWarningLines(ctx));
        assertTrue(relieved.contains("opened escorted supply access"));
    }

    @Test
    void asteroidCoverSurvivesIncidentalFireButCanBeIntentionallyDestroyed() {
        Asteroid asteroid = new Asteroid(1000.0, 1000.0, 60.0, 500);
        int initial = asteroid.hp;
        assertTrue(initial >= 220);
        assertFalse(asteroid.applyWeaponDamage(Math.max(1, initial / 4)));
        assertTrue(asteroid.hp > 0);
        assertTrue(asteroid.applyWeaponDamage(initial));

        String rules = String.join("\n", CampaignSystem.asteroidCoverRulesLines());
        assertTrue(rules.contains("blocks movement and direct fire"));
        assertTrue(rules.contains("sustained focus"));
        assertTrue(rules.contains("heavy weapons"));
        assertTrue(rules.contains("removed immediately"));
    }

    @Test
    void activeEnvironmentAndHazardScheduleSurviveCheckpointRestore() throws Exception {
        GameContext source = initializedCampaignContext(7106L);
        setModifiers(source.campaign, "SOLAR_STORM", "GRAVITY_SHEAR");
        source.campaign.environmentHazardPulseIndex = 4;
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source);
        assertTrue(checkpoint.activeMapModifiers.contains("SOLAR_STORM"));
        assertEquals(4, checkpoint.environmentHazardPulseIndex);

        GameContext restored = initializedCampaignContext(9999L);
        assertTrue(applyCheckpoint(restored, checkpoint));
        String labels = String.join(",", CampaignSystem.activeModifierLabels(restored));
        assertTrue(labels.contains("Solar Storm"));
        assertTrue(labels.contains("Gravity Shear"));
        assertEquals(4, restored.campaign.environmentHazardPulseIndex);
    }

    @Test
    void backgroundAudioAndVisualPresentationContractsRemainExplicit() {
        GameContext ctx = initializedCampaignContext(7107L);
        String presentation = String.join("\n", CampaignSystem.presentationDirectionLines(ctx));
        assertTrue(presentation.contains("ordinary open space uses empty deep space"));
        assertTrue(presentation.contains("industrial naval science fiction"));
        assertTrue(presentation.contains("approved hull"));
        assertTrue(presentation.contains("allied / neutral / hostile / hub / operational"));
        assertTrue(presentation.contains("ambient silence by default"));
        assertTrue(presentation.contains("detection, pursuit, major battle"));
        assertTrue(presentation.contains("Warp Audio"));
        assertTrue(presentation.contains("Missile Audio"));
        assertTrue(presentation.contains("quiet mode"));
        assertFalse(Renderer.campaignBackdropDebugName(ctx).isBlank());
    }

    @Test
    void majorStationSitesUsePlanetBackdropsButOreSitesStayInDeepSpace() {
        GameContext station = initializedCampaignContext(7108L);
        station.campaign.galaxyEncounterActive = true;
        station.campaign.galaxyAmbientEncounterActive = true;
        station.campaign.activeGalaxyEncounterLocationId = "poi-01";

        assertFalse(CampaignSystem.isOrdinaryOpenSpaceEncounter(station));
        assertNotEquals("deep_space_encounter", Renderer.campaignBackdropDebugName(station));
        assertTrue(Renderer.campaignBackdropImageAvailable(Renderer.campaignBackdropBaseImageKey(station)));

        GameContext ore = initializedCampaignContext(7109L);
        ore.campaign.galaxyEncounterActive = true;
        ore.campaign.galaxyAmbientEncounterActive = true;
        ore.campaign.activeGalaxyEncounterLocationId = "aoi-resource-1";

        assertTrue(CampaignSystem.isOrdinaryOpenSpaceEncounter(ore));
        assertEquals("deep_space_encounter", Renderer.campaignBackdropDebugName(ore));
    }

    @Test
    void tacticalRenderingIgnoresRetiredCombatFogCulling() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 7110L, false));
        ctx.state = GameState.RUNNING;
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 2700.0, 2500.0));
        ctx.camX = 1900.0;
        ctx.camY = 2140.0;
        FogOfWarSystem.reset(ctx);

        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = canvas.createGraphics();
        try {
            GameRenderSystem.render(ctx, g2, 1280, 720);
        } finally {
            g2.dispose();
        }

        assertTrue(ctx.perf.drawnShips >= 2,
                "retired fog-of-war should not hide combat ships in tactical/site rendering");
    }

    @Test
    void conqueredStationControlColorFollowsOwnerNotOriginalName() {
        GameContext ctx = initializedCampaignContext(7111L);
        ctx.campaign.strategicOvermapMode = true;
        CampaignSystem.CampaignLocation redNamed = locationContaining(ctx, "RED CORRIDOR BREAKPOINT");
        assertNotNull(redNamed);

        redNamed.ownerFaction = Faction.TEAM_D;

        CampaignSystem.CampaignLocationControlView view = CampaignSystem.campaignLocationControlView(ctx, redNamed);
        assertEquals(CampaignSystem.CampaignControlVisualState.YELLOW, view.control,
                "a conquered Red-named station should visibly change to the conquering faction color");
    }

    @Test
    void temporaryTheaterNodePressureDoesNotRepaintStableSiteOwner() throws Exception {
        GameContext ctx = initializedCampaignContext(7114L);
        ctx.campaign.strategicOvermapMode = true;
        CampaignSystem.CampaignLocation green = locationContaining(ctx, "GREEN ANCHORAGE");
        CampaignSystem.CampaignLocation yellow = locationContaining(ctx, "YELLOW COMMERCE SPINE");
        assertNotNull(green);
        assertNotNull(yellow);

        green.ownerFaction = Faction.ALLY;
        yellow.ownerFaction = Faction.TEAM_D;
        forceStrategicNodeControl(ctx, green.id, "RED", -108.0);
        forceStrategicNodeControl(ctx, yellow.id, "RED", -108.0);

        assertEquals(CampaignSystem.CampaignControlVisualState.GREEN,
                CampaignSystem.campaignLocationControlView(ctx, green).control,
                "temporary Red pressure on a theater node should not repaint a stable Green-owned site");
        assertEquals(CampaignSystem.CampaignControlVisualState.YELLOW,
                CampaignSystem.campaignLocationControlView(ctx, yellow).control,
                "temporary Red pressure on a theater node should not repaint a stable Yellow-owned site");
    }

    @Test
    void redOwnedMajorStationEncounterSpawnsRedStationAndDefenders() throws Exception {
        GameContext ctx = initializedCampaignContext(7112L);
        CampaignSystem.CampaignLocation redStation = locationContaining(ctx, "RED FORWARD SHIPYARD");
        assertNotNull(redStation);
        assertEquals(Faction.ENEMY, redStation.ownerFaction);

        assertTrue(invokeLaunchAmbientEncounter(ctx, redStation));

        assertTrue(ctx.ships.stream().anyMatch(ship -> ship != null
                        && ship.role == ShipRole.BASE
                        && ship.faction == Faction.ENEMY
                        && ship.name.toUpperCase(java.util.Locale.US).contains("RED FORWARD SHIPYARD")),
                "entering a Red-owned major station should spawn a Red station, not a Green one");
        assertTrue(ctx.ships.stream().filter(ship -> ship != null && ship.faction == Faction.ENEMY).count() >= 2,
                "Red-owned major station pockets should include Red local defense/traffic ships");
    }

    @Test
    void movingInvasionFleetProducesFactionColoredMapArrow() throws Exception {
        GameContext ctx = initializedCampaignContext(7113L);
        CampaignSystem.CampaignState st = ctx.campaign;
        ctx.campaign.strategicOvermapMode = true;
        CampaignSystem.CampaignLocation source = locationContaining(ctx, "RED FORTRESS LUNA GATE");
        CampaignSystem.CampaignLocation target = locationContaining(ctx, "GREEN ANCHORAGE");
        assertNotNull(source);
        assertNotNull(target);

        Object force = invokeEnsureCampaignForce(st,
                CampaignSystem.CampaignForceKind.TASK_FORCE,
                Faction.ENEMY,
                "Red Regression Invasion Spearhead",
                source.name,
                "Invade a key Green anchorage",
                source.x,
                source.y);
        setObject(force, "sourceLocationId", source.id);
        setObject(force, "homeBaseId", source.id);
        setObject(force, "destinationLocationId", target.id);
        setEnumByName(force, "mission", "CAPTURE");
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "state", "MOVING");
        setEnumByName(force, "workState", "TRAVELING");
        setDouble(force, "strength", 84.0);
        setDouble(force, "speed", 160.0);

        List<CampaignSystem.CampaignInvasionArrow> arrows = CampaignSystem.campaignInvasionArrows(ctx);
        assertTrue(arrows.stream().anyMatch(arrow -> arrow.faction == Faction.ENEMY
                        && arrow.targetLocationId.equals(target.id)
                        && arrow.label.toUpperCase(java.util.Locale.US).contains("INVASION")),
                "moving capture forces between key sites should be exposed as invasion arrows on the map");
    }

    private static GameContext initializedCampaignContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void setModifiers(CampaignSystem.CampaignState state, String... names) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("activeModifiers");
        field.setAccessible(true);
        Class<?> component = field.getType().getComponentType();
        Object array = Array.newInstance(component, names.length);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<? extends Enum> enumType = (Class<? extends Enum>) component;
        for (int i = 0; i < names.length; i++) {
            Array.set(array, i, Enum.valueOf(enumType, names[i]));
        }
        field.set(state, array);
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, 2);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static void invokeLiveEnvironment(GameContext ctx, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyLiveTacticalEnvironment", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, dt);
    }

    private static CampaignSystem.CampaignLocation locationContaining(GameContext ctx, String text) {
        String needle = text.toUpperCase(java.util.Locale.US);
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.name.toUpperCase(java.util.Locale.US).contains(needle)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && location.name.toUpperCase(java.util.Locale.US).contains(needle)) return location;
        }
        return null;
    }

    private static boolean invokeLaunchAmbientEncounter(GameContext ctx, CampaignSystem.CampaignLocation location) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchAmbientCampaignLocationEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, location);
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
                double.class);
        method.setAccessible(true);
        return method.invoke(null, st, kind, faction, name, origin, purpose, x, y);
    }

    private static void forceStrategicNodeControl(GameContext ctx, String locationId, String owner, double contestProgress) throws Exception {
        for (Object node : ctx.campaign.strategicNodes) {
            Field locationField = node.getClass().getDeclaredField("locationId");
            locationField.setAccessible(true);
            if (!locationId.equals(locationField.get(node))) continue;
            setEnumByName(node, "owner", owner);
            setDouble(node, "contestProgress", contestProgress);
            return;
        }
        fail("Expected a strategic node for location " + locationId);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumByName(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<? extends Enum> enumType = (Class<? extends Enum>) field.getType();
        field.set(target, Enum.valueOf(enumType, value));
    }
}
