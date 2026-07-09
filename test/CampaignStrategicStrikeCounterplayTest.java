import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignStrategicStrikeCounterplayTest {

    @Test
    void strikeAvailabilityContractShowsInventoryCostsIntelRiskAndRecovery() {
        GameContext ctx = initializedCampaignContext();
        List<CampaignSystem.StrikeAvailabilityBrief> briefs = CampaignSystem.campaignStrikeAvailability(ctx);

        assertEquals(3, briefs.size());
        for (CampaignSystem.StrikeAvailabilityBrief brief : briefs) {
            assertFalse(brief.inventory.isBlank());
            assertTrue(brief.resourceCost.contains("Ammo"));
            assertTrue(brief.resourceCost.contains("Fuel"));
            assertTrue(brief.resourceCost.contains("Supplies"));
            assertFalse(brief.capacityCost.isBlank());
            assertFalse(brief.requiredIntel.isBlank());
            assertFalse(brief.estimatedEffect.isBlank());
            assertFalse(brief.retaliationRisk.isBlank());
            assertFalse(brief.unavailableReason.isBlank());
            assertTrue(brief.replenishment.contains("Strike Rearm")
                            || brief.replenishment.contains("Rearm at"),
                    "strike recovery must be discoverable before launch");
        }
        assertTrue(briefs.stream()
                .filter(brief -> "Carrier Sortie".equals(brief.strikeName))
                .allMatch(brief -> brief.inventory.contains("reusable")));
        assertTrue(briefs.stream()
                .filter(brief -> !"Carrier Sortie".equals(brief.strikeName))
                .allMatch(brief -> brief.inventory.contains("expendable")));
    }

    @Test
    void failedStrikeValidationConsumesNothing() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicTorpedoCharges = 2;
        st.campaignAmmo = 80;
        st.campaignFuel = 70;
        st.campaignSupplies = 60;

        assertFalse(CampaignSystem.launchStrategicTorpedoStrike(ctx, -999999.0, -999999.0));
        assertEquals(2, st.strategicTorpedoCharges);
        assertEquals(80, st.campaignAmmo);
        assertEquals(70, st.campaignFuel);
        assertEquals(60, st.campaignSupplies);
        assertEquals(0, st.strategicStrikeObjects.size());
    }

    @Test
    void everyStrategicStrikeStartsAtTheLaunchingFleet() throws Exception {
        assertStrategicStrikeStartsAtLauncher("TORPEDO");
        assertStrategicStrikeStartsAtLauncher("SORTIE");
        assertStrategicStrikeStartsAtLauncher("ATOMIC");
    }

    @Test
    void tacticalMissilesUseAValidEntryVectorInsteadOfSpawningOnTarget() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.CIWS_CORVETTE}
        );
        Ship target = firstLiveHostileShip(ctx);
        assertNotNull(target);
        ctx.player.x = target.x - 2400.0;
        ctx.player.y = target.y + 300.0;

        Missile torpedo = (Missile) invokePrivateStatic("spawnTacticalStrikeMissile",
                new Class<?>[]{GameContext.class, Class.forName("CampaignSystem$StrategicStrikePayload"),
                        Ship.class, double.class, double.class, double.class, int.class, double.class},
                ctx, enumConstant(Class.forName("CampaignSystem$StrategicStrikePayload"), "TORPEDO"),
                target, target.x, target.y, 0.0, 200, 120.0);
        Missile atomic = (Missile) invokePrivateStatic("spawnTacticalStrikeMissile",
                new Class<?>[]{GameContext.class, Class.forName("CampaignSystem$StrategicStrikePayload"),
                        Ship.class, double.class, double.class, double.class, int.class, double.class},
                ctx, enumConstant(Class.forName("CampaignSystem$StrategicStrikePayload"), "ATOMIC"),
                target, target.x, target.y, 0.0, 400, 640.0);

        assertNotNull(torpedo);
        assertNotNull(atomic);
        assertTrue(Math.hypot(torpedo.x - target.x, torpedo.y - target.y) >= 900.0);
        assertTrue(Math.hypot(atomic.x - target.x, atomic.y - target.y) >= atomic.blastRadius + 300.0);
        assertTrue(Math.hypot(torpedo.x - ctx.player.x, torpedo.y - ctx.player.y)
                        < Math.hypot(target.x - ctx.player.x, target.y - ctx.player.y),
                "torpedo must enter from the launching fleet's side of the target");
        assertTrue(ctx.audioEvents.stream().anyMatch(event -> "sfx.weapon.torpedo_launch".equals(event.eventId)
                        && event.variantSeed >= 0));
        assertTrue(ctx.audioEvents.stream().anyMatch(event -> "sfx.weapon.missile_launch".equals(event.eventId)
                        && event.variantSeed >= 0));
    }

    @Test
    void torpedoStrikeConsumesResourcesAndTriggersCounterplay() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        CampaignSystem.CampaignState st = ctx.campaign;
        Object taskForce = firstHostileTaskForce(st);
        assertNotNull(taskForce);

        int startingCharges = st.strategicTorpedoCharges;
        int startingAmmo = st.campaignAmmo;
        int startingFuel = st.campaignFuel;
        double startingAlert = st.enemyAlertLevel;
        double startingExposure = st.strategicExposureLevel;

        double x = taskForceCenterX(ctx, st, taskForce);
        double y = taskForceCenterY(ctx, st, taskForce);
        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx, x, y));

        assertTrue(st.strategicTorpedoCharges < startingCharges, "torpedo charge should be spent");
        assertTrue(st.campaignAmmo < startingAmmo, "torpedo strike should spend ammo");
        assertTrue(st.campaignFuel < startingFuel, "torpedo strike should spend fuel");
        assertTrue(st.enemyAlertLevel > startingAlert, "torpedo strike should raise alert");
        assertTrue(st.strategicExposureLevel > startingExposure, "torpedo strike should raise exposure");

        Object searchGroup = firstSearchGroup(st);
        assertNotNull(searchGroup);
        assertTrue(getBoolean(searchGroup, "visible"), "long-range strike should improve map intel");
    }

    @Test
    void higherIntelImprovesSortieStrikeEffectiveness() throws Exception {
        GameContext lowIntelCtx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.CARRIER_SUPPORT_TITAN, ShipRole.CIWS_CORVETTE}
        );
        lowIntelCtx.campaign.campaignIntelLevel = 8.0;
        Object lowTarget = firstHostileTaskForce(lowIntelCtx.campaign);
        assertNotNull(lowTarget);
        setBoolean(lowTarget, "encounterSpawned", false);
        double lowBefore = getDouble(lowTarget, "currentStrength");
        assertTrue(CampaignSystem.launchStrategicSortie(lowIntelCtx,
                taskForceCenterX(lowIntelCtx, lowIntelCtx.campaign, lowTarget),
                taskForceCenterY(lowIntelCtx, lowIntelCtx.campaign, lowTarget)));
        assertTrue(strategicStrikeObjectCount(lowIntelCtx.campaign) > 0, "low-intel sortie should queue a strike object");
        advanceStrikeObjects(lowIntelCtx, 240.0);
        assertEquals(0, strategicStrikeObjectCount(lowIntelCtx.campaign), "low-intel sortie should resolve after advance");
        double lowDamage = lowBefore - getDouble(lowTarget, "currentStrength");

        GameContext highIntelCtx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.CARRIER_SUPPORT_TITAN, ShipRole.CIWS_CORVETTE}
        );
        highIntelCtx.campaign.campaignIntelLevel = 86.0;
        Object highTarget = firstHostileTaskForce(highIntelCtx.campaign);
        assertNotNull(highTarget);
        setBoolean(highTarget, "encounterSpawned", false);
        double highBefore = getDouble(highTarget, "currentStrength");
        assertTrue(CampaignSystem.launchStrategicSortie(highIntelCtx,
                taskForceCenterX(highIntelCtx, highIntelCtx.campaign, highTarget),
                taskForceCenterY(highIntelCtx, highIntelCtx.campaign, highTarget)));
        assertTrue(strategicStrikeObjectCount(highIntelCtx.campaign) > 0, "high-intel sortie should queue a strike object");
        advanceStrikeObjects(highIntelCtx, 240.0);
        assertEquals(0, strategicStrikeObjectCount(highIntelCtx.campaign), "high-intel sortie should resolve after advance");
        double highDamage = highBefore - getDouble(highTarget, "currentStrength");

        assertTrue(highDamage > lowDamage,
                "better intel should improve sortie damage after counterplay; lowDamage="
                        + lowDamage + " highDamage=" + highDamage);
        assertTrue(highIntelCtx.campaign.campaignIntelLevel >= lowIntelCtx.campaign.campaignIntelLevel,
                "sorties should reinforce the threat picture rather than collapse it");
    }

    @Test
    void routeAssessmentImprovesWithBetterIntel() throws Exception {
        GameContext lowIntelCtx = initializedCampaignContext();
        GameContext highIntelCtx = initializedCampaignContext();
        lowIntelCtx.campaign.campaignIntelLevel = 8.0;
        highIntelCtx.campaign.campaignIntelLevel = 84.0;

        CampaignSystem.CampaignLocation destination = findLocation(lowIntelCtx, "poi-22");
        assertNotNull(destination);

        Object lowRoute = invokePrivateStatic("analyzeRoute",
                new Class<?>[]{CampaignSystem.CampaignState.class, GameContext.class, double.class, double.class, CampaignSystem.CampaignLocation.class},
                lowIntelCtx.campaign, lowIntelCtx, lowIntelCtx.campaign.playerGalaxyX, lowIntelCtx.campaign.playerGalaxyY, destination);
        Object highRoute = invokePrivateStatic("analyzeRoute",
                new Class<?>[]{CampaignSystem.CampaignState.class, GameContext.class, double.class, double.class, CampaignSystem.CampaignLocation.class},
                highIntelCtx.campaign, highIntelCtx, highIntelCtx.campaign.playerGalaxyX, highIntelCtx.campaign.playerGalaxyY, destination);

        assertTrue(getDouble(highRoute, "interceptionRisk") < getDouble(lowRoute, "interceptionRisk"),
                "better intel should reduce route danger estimates and practical interception risk");
        assertTrue(getDouble(highRoute, "logisticsPressure") < getDouble(lowRoute, "logisticsPressure"),
                "better intel should make route planning less punishing");
    }

    @Test
    void atomicStrikeCarriesPoliticalAndOperationalCost() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.CIWS_CORVETTE}
        );
        CampaignSystem.CampaignState st = ctx.campaign;
        st.greenContractFavor = 4;
        st.yellowLiberationFavor = 4;
        st.enemyAlertLevel = 20.0;
        st.strategicExposureLevel = 10.0;
        Object taskForce = firstHostileTaskForce(st);
        assertNotNull(taskForce);

        double startAlert = st.enemyAlertLevel;
        double startExposure = st.strategicExposureLevel;
        int startGreen = st.greenContractFavor;
        int startYellow = st.yellowLiberationFavor;

        assertTrue(CampaignSystem.launchStrategicAtomicStrike(ctx,
                taskForceCenterX(ctx, st, taskForce),
                taskForceCenterY(ctx, st, taskForce)));

        assertTrue(st.enemyAlertLevel > startAlert + 10.0, "atomic strike should sharply raise alert");
        assertTrue(st.strategicExposureLevel > startExposure + 10.0, "atomic strike should sharply raise exposure");
        assertTrue(st.greenContractFavor < startGreen, "atomic strike should damage Green standing");
        assertTrue(st.yellowLiberationFavor < startYellow, "atomic strike should damage Yellow standing");
    }

    @Test
    void torpedoStrikeStartsCampaignCinematicPresentation() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Object taskForce = firstHostileTaskForce(ctx.campaign);
        assertNotNull(taskForce);
        setBoolean(taskForce, "encounterSpawned", false);

        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx,
                taskForceCenterX(ctx, ctx.campaign, taskForce),
                taskForceCenterY(ctx, ctx.campaign, taskForce)));

        assertTrue(CampaignSystem.isCampaignStrikeCinematicActive(ctx),
                "strategic strikes should kick off a visible campaign cinematic instead of resolving invisibly");
        assertTrue(ctx.ui.mapOpen, "the campaign map should stay up so the player can follow the inbound weapon");
    }

    @Test
    void strikeReportsPersistAfterSuccessfulLaunch() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Object taskForce = firstHostileTaskForce(ctx.campaign);
        assertNotNull(taskForce);

        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx,
                taskForceCenterX(ctx, ctx.campaign, taskForce),
                taskForceCenterY(ctx, ctx.campaign, taskForce)));

        assertTrue(CampaignSystem.lastStrikeReportTitle(ctx).contains("TORPEDO REPORT"));
        assertTrue(CampaignSystem.lastStrikeReportDetail(ctx).contains("Heat"));
        assertTrue(CampaignSystem.campaignStrikeConsequenceLines(ctx).stream().anyMatch(line -> line.startsWith("Report: ")));
    }

    @Test
    void selectedTargetLockPersistsAcrossOvermapReconTransitions() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 180.0);
        setDouble(group, "y", st.playerGalaxyY + 80.0);
        setBoolean(group, "visible", true);
        setObject(group, "intelQuality", enumConstant(Class.forName("CampaignSystem$ContactIntelQuality"), "TRACKED"));
        setObject(group, "contactConfidence", enumConstant(Class.forName("CampaignSystem$GalaxyContactConfidence"), "CONFIRMED_HOSTILE"));

        CampaignSystem.selectCampaignContactTarget(ctx, "Tracked Return", "", "Tracked", getDouble(group, "x"), getDouble(group, "y"), true, true);
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;
        double lockedX = ctx.ui.selectedCampaignContactX;
        double lockedY = ctx.ui.selectedCampaignContactY;
        assertFalse(CampaignSystem.selectedCampaignContactLabel(ctx).isBlank());

        List<CampaignSystem.CampaignAction> strikeActions = CampaignSystem.campaignVisibleActions(ctx);
        assertFalse(strikeActions.stream().anyMatch(action -> "CARRIER_SORTIE".equals(action.id)));
        assertFalse(strikeActions.stream().anyMatch(action -> "TORPEDO_STRIKE".equals(action.id)));
        CampaignSystem.CampaignAction track = strikeActions.stream().filter(action -> "TRACK_TARGET".equals(action.id)).findFirst().orElse(null);
        assertNotNull(track);
        assertTrue(track.enabled);

        setObject(group, "intelQuality", enumConstant(Class.forName("CampaignSystem$ContactIntelQuality"), "TARGET_QUALITY"));
        CampaignSystem.selectCampaignContactTarget(ctx, "Tracked Return", "", "Target-Quality", getDouble(group, "x"), getDouble(group, "y"), true, true);
        assertEquals(lockedX, ctx.ui.selectedCampaignContactX);
        assertEquals(lockedY, ctx.ui.selectedCampaignContactY);
        List<CampaignSystem.CampaignAction> upgraded = CampaignSystem.campaignVisibleActions(ctx);
        assertFalse(upgraded.stream().anyMatch(action -> "TORPEDO_STRIKE".equals(action.id)));
        assertTrue(upgraded.stream().anyMatch(action -> "ENGAGE_CONTACT".equals(action.id)));
    }

    @Test
    void broadSweepCreatesUsableOvermapReconWindowWithoutPointBlankContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.campaignSupplies = 20;
        st.campaignIntelLevel = 24.0;
        st.strategicTorpedoCharges = 2;
        st.strategicSortiesLaunched = 0;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX + 1350.0);
        setDouble(group, "y", st.playerGalaxyY + 220.0);
        setBoolean(group, "hostile", true);
        setBoolean(group, "visible", false);
        setObject(group, "contactConfidence", enumConstant(Class.forName("CampaignSystem$GalaxyContactConfidence"), "POSSIBLE_PATROL"));
        setObject(group, "intelQuality", enumConstant(Class.forName("CampaignSystem$ContactIntelQuality"), "UNKNOWN"));
        setDouble(group, "trackIntegrity", 18.0);

        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));

        CampaignSystem.selectCampaignContactTarget(
                ctx,
                "Sweep Return",
                "",
                CampaignSystem.selectedCampaignContactIntelLabel(ctx),
                getDouble(group, "x"),
                getDouble(group, "y"),
                true,
                true);
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);
        CampaignSystem.CampaignAction track = actions.stream().filter(action -> "TRACK_TARGET".equals(action.id)).findFirst().orElse(null);
        assertNotNull(track);
        assertTrue(getBoolean(group, "visible"), "sweep should reveal the hostile at long range");
        assertTrue(track.enabled, "a single broad sweep should create a practical recon/intercept option");
        assertFalse(actions.stream().anyMatch(action -> "TORPEDO_STRIKE".equals(action.id)));
        assertFalse(actions.stream().anyMatch(action -> "CARRIER_SORTIE".equals(action.id)));
    }

    @Test
    void discoveryCachesRecoverLimitedStrikeStoresWithoutOverfilling() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.strategicTorpedoCharges = 0;
        st.strategicSortiesLaunched = 3;
        st.strategicAtomicCharges = 0;

        Object cache = newDiscoverySite("Dead Tender", "Recoverable strike pallets",
                "CACHE", st.playerGalaxyX + 20.0, st.playerGalaxyY + 20.0, 120.0);
        invokeResolveDiscoverySite(ctx, st, cache);

        assertEquals(1, st.strategicTorpedoCharges,
                "ordinary caches should recover one torpedo store when below cap");
        assertEquals(3, st.strategicSortiesLaunched,
                "ordinary caches should not silently refresh carrier sortie decks");
        assertEquals(0, st.strategicAtomicCharges,
                "ordinary caches should not restore atomic stores");

        Object supplyCache = newDiscoverySite("Fuel Locker", "Missile pallets and deck crews",
                "SUPPLY_CACHE", st.playerGalaxyX + 40.0, st.playerGalaxyY + 40.0, 120.0);
        invokeResolveDiscoverySite(ctx, st, supplyCache);

        assertEquals(2, st.strategicTorpedoCharges,
                "supply caches should recover a limited torpedo store");
        assertEquals(2, st.strategicSortiesLaunched,
                "supply caches should recover one committed sortie deck");

        st.strategicTorpedoCharges = 99;
        st.strategicAtomicCharges = 99;
        Object cappedCache = newDiscoverySite("Capped Cache", "Already full stores",
                "SUPPLY_CACHE", st.playerGalaxyX + 60.0, st.playerGalaxyY + 60.0, 120.0);
        invokeResolveDiscoverySite(ctx, st, cappedCache);

        assertEquals(6, st.strategicTorpedoCharges,
                "cache rewards should clamp to strategic torpedo capacity instead of stockpiling");
        assertEquals(1, st.strategicAtomicCharges,
                "cache rewards should clamp atomic stores even when an old save is overfilled");
    }

    @Test
    void staleSearchGroupMemoryDoesNotRenderAsLiveMapContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        double lastX = st.playerGalaxyX + 320.0;
        double lastY = st.playerGalaxyY + 180.0;
        setDouble(group, "x", st.playerGalaxyX + 4200.0);
        setDouble(group, "y", st.playerGalaxyY + 1300.0);
        setDouble(group, "lastKnownX", lastX);
        setDouble(group, "lastKnownY", lastY);
        setDouble(group, "lastKnownAgeSec", 24.0);
        setBoolean(group, "visible", true);
        setObject(group, "contactConfidence", enumConstant(Class.forName("CampaignSystem$GalaxyContactConfidence"), "LOST_CONTACT"));
        setObject(group, "intelQuality", enumConstant(Class.forName("CampaignSystem$ContactIntelQuality"), "CLASSIFIED"));

        assertFalse(CampaignSystem.activeSupportMarkers(ctx).stream()
                        .anyMatch(it -> it.subtitle.contains("Last-known sensor memory")),
                "lost search-group memory must not render as a physical fleet marker");
    }

    @Test
    void staleCampaignForceDoesNotDrawLivePursuitOrMovementVectors() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object force = firstEnemyCampaignForce(st);
        assertNotNull(force);

        double realX = st.playerGalaxyX + 3800.0;
        double realY = st.playerGalaxyY + 1200.0;
        double lastX = st.playerGalaxyX + 360.0;
        double lastY = st.playerGalaxyY + 210.0;
        setObject(force, "intent", enumConstant(Class.forName("CampaignSystem$CampaignForceIntent"), "INTERCEPTING"));
        setObject(force, "state", enumConstant(Class.forName("CampaignSystem$CampaignFleetState"), "PURSUING"));
        setObject(force, "contactState", enumConstant(Class.forName("CampaignSystem$CampaignForceContactState"), "STALE"));
        setDouble(force, "contactConfidence", 0.50);
        setDouble(force, "x", realX);
        setDouble(force, "y", realY);
        setDouble(force, "lastKnownX", lastX);
        setDouble(force, "lastKnownY", lastY);
        setDouble(force, "lastKnownAgeSec", 24.0);
        setDouble(force, "targetX", st.playerGalaxyX);
        setDouble(force, "targetY", st.playerGalaxyY);
        setBoolean(force, "visibleToPlayer", false);
        setBoolean(force, "simulationActive", true);

        assertTrue(CampaignSystem.confirmedPlayerInterceptLines(ctx).isEmpty(),
                "last-known contacts must not draw live player-intercept lines");

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker lastKnown = markers.stream()
                .filter(marker -> marker != null && marker.label.contains("Last Known Contact"))
                .findFirst()
                .orElse(null);
        assertNull(lastKnown, "stale physical forces must not remain visible as live map contacts");
        assertFalse(markers.stream().anyMatch(marker -> marker != null && marker.label.contains("Moving Blockade Line")),
                "last-known contacts must not draw live movement/blockade vectors");

        String readout = String.join("\n", CampaignSystem.campaignNearbyContactReadouts(ctx, 32).stream()
                .filter(contact -> Math.hypot(contact.x - lastX, contact.y - lastY) < 2.0)
                .map(contact -> contact.title + " " + contact.detail)
                .toList());
        assertFalse(readout.toLowerCase().contains("pursuit / intercept"),
                "sensor readout must not advertise stale contacts as active pursuers");
    }

    @Test
    void lostSearchGroupDoesNotPutCampaignIntoBeingHuntedState() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        setDouble(group, "x", st.playerGalaxyX + 90.0);
        setDouble(group, "y", st.playerGalaxyY + 60.0);
        setDouble(group, "lastKnownX", st.playerGalaxyX - 500.0);
        setDouble(group, "lastKnownY", st.playerGalaxyY - 300.0);
        setDouble(group, "lastKnownAgeSec", 48.0);
        setDouble(group, "trackIntegrity", 80.0);
        setBoolean(group, "visible", true);
        setObject(group, "behavior", enumConstant(Class.forName("CampaignSystem$GalaxySearchBehavior"), "INTERCEPTING"));
        setObject(group, "contactConfidence", enumConstant(Class.forName("CampaignSystem$GalaxyContactConfidence"), "LOST_CONTACT"));

        assertEquals("Clear", CampaignSystem.campaignHuntStatusReadout(ctx),
                "lost search-group shadows must not make the map report active pursuit");
    }

    @Test
    void lostContactExpiryScalesWithIntelAndInvalidatesSelection() throws Exception {
        String[] intelLevels = {"UNKNOWN", "CLASSIFIED", "IDENTIFIED", "TRACKED", "TARGET_QUALITY"};
        double[] windows = {15.0, 30.0, 45.0, 60.0, 75.0};
        for (int i = 0; i < intelLevels.length; i++) {
            GameContext ctx = initializedCampaignContext();
            Object group = firstSearchGroup(ctx.campaign);
            assertNotNull(group);
            setObject(group, "contactConfidence",
                    enumConstant(Class.forName("CampaignSystem$GalaxyContactConfidence"), "LOST_CONTACT"));
            setObject(group, "intelQuality",
                    enumConstant(Class.forName("CampaignSystem$ContactIntelQuality"), intelLevels[i]));
            setBoolean(group, "visible", true);
            setDouble(group, "lastKnownAgeSec", windows[i] - 0.5);
            setDouble(group, "lastKnownX", ctx.campaign.playerGalaxyX + 300.0);
            setDouble(group, "lastKnownY", ctx.campaign.playerGalaxyY + 200.0);
            assertFalse(CampaignSystem.activeSupportMarkers(ctx).stream()
                            .anyMatch(marker -> marker.subtitle.contains("Last-known sensor memory")),
                    intelLevels[i] + " memory must not be actionable as a live fleet contact");

            setDouble(group, "lastKnownAgeSec", windows[i] + 0.5);
            assertFalse(CampaignSystem.activeSupportMarkers(ctx).stream()
                            .anyMatch(marker -> marker.subtitle.contains("Last-known sensor memory")),
                    intelLevels[i] + " contact should leave the normal HUD after expiry");

            CampaignSystem.selectCampaignContactTarget(ctx, "Expired Contact", "", intelLevels[i],
                    getDouble(group, "x"), getDouble(group, "y"), true, true);
            assertFalse(CampaignSystem.hasSelectedCampaignContactTarget(ctx),
                    "expired contact must not remain selectable for strikes or navigation");
        }
    }

    @Test
    void tacticalStrikeTabDoesNotExposeStandOffStrikeActions() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        CampaignSystem.CampaignState st = ctx.campaign;
        Object taskForce = firstHostileTaskForce(st);
        assertNotNull(taskForce);

        double x = taskForceCenterX(ctx, st, taskForce);
        double y = taskForceCenterY(ctx, st, taskForce);
        CampaignSystem.selectCampaignContactTarget(ctx, "Strike Zone", "", "Tracked", x, y, true, true);
        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.CONTACT;
        ctx.ui.tacticalMapSelectionLabel = "Strike Zone";
        ctx.ui.tacticalMapSelectionSubtitle = "Hostile pocket";
        ctx.ui.tacticalMapSelectionDetail = "Tracked";
        ctx.ui.tacticalMapSelectionX = x;
        ctx.ui.tacticalMapSelectionY = y;
        ctx.ui.tacticalMapSelectionHostile = true;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.tacticalMapVisibleActions(ctx);
        CampaignSystem.CampaignAction torpedo = actions.stream().filter(action -> "TACTICAL_TORPEDO_STRIKE".equals(action.id)).findFirst().orElse(null);
        int torpedoesBefore = st.strategicTorpedoCharges;
        assertEquals(null, torpedo);
        assertFalse(CampaignSystem.executeTacticalMapAction(ctx, "TACTICAL_TORPEDO_STRIKE"));
        assertEquals(torpedoesBefore, st.strategicTorpedoCharges, "hidden tactical torpedo action should not spend a charge");
    }

    @Test
    void majorMissionThreatsAreVisibleAndStrikeableBeforeEntry() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation location = findLocation(ctx, "poi-06");
        assertNotNull(location);

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker marker = markers.stream()
                .filter(it -> it.type == CampaignSystem.SupportMarkerType.HAZARD)
                .filter(it -> it.label.contains(location.name))
                .findFirst()
                .orElse(null);
        assertNotNull(marker, "primary mission should advertise outside hostile contacts on the overmap");

        ctx.campaign.strategicTorpedoCharges = 2;
        ctx.campaign.campaignAmmo = 120;
        ctx.campaign.campaignFuel = 120;
        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx, marker.x, marker.y));
        advanceStrikeObjects(ctx, 240.0);
        assertTrue(location.missionOuterThreatSuppression > 0.0,
                "outside strike should soften the mission before tactical entry");

        invokePrivateStatic("launchCampaignLocationEncounter",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class},
                ctx, ctx.campaign, location);
        Object hostile = firstHostileTaskForce(ctx.campaign);
        assertNotNull(hostile);
        assertTrue(getDouble(hostile, "currentStrength") < getDouble(hostile, "maxStrength"),
                "pre-entry bombardment should carry into the mission task forces");
    }

    @Test
    void tacticalTorpedoActionIsBlockedEvenForRemoteEnemyShipSelection() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Ship hostile = ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.alive && !ship.dying)
                .filter(ship -> ship.faction != null && !ship.faction.isFriendlyTo(ctx.player.faction))
                .findFirst()
                .orElse(null);
        assertNotNull(hostile);

        int playerSubzone = CampaignSystem.currentLoadedMissionSubzone(ctx);
        int remoteSubzone = (playerSubzone == CampaignSystem.missionSubzoneIndex(5, 2))
                ? CampaignSystem.missionSubzoneIndex(0, 0)
                : CampaignSystem.missionSubzoneIndex(5, 2);
        double remoteX = CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, remoteSubzone);
        double remoteY = CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, remoteSubzone);
        hostile.x = remoteX;
        hostile.y = remoteY;
        hostile.campaignMissionSubzone = remoteSubzone;

        CampaignSystem.selectCampaignContactTarget(ctx, hostile.name, "", "Tracked", hostile.x, hostile.y, true, true);
        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.CONTACT;
        ctx.ui.tacticalMapSelectionLabel = hostile.name;
        ctx.ui.tacticalMapSelectionSubtitle = "Hostile hull";
        ctx.ui.tacticalMapSelectionDetail = "Tracked";
        ctx.ui.tacticalMapSelectionX = hostile.x;
        ctx.ui.tacticalMapSelectionY = hostile.y;
        ctx.ui.tacticalMapSelectionHostile = true;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.tacticalMapVisibleActions(ctx);
        CampaignSystem.CampaignAction torpedo = actions.stream()
                .filter(action -> "TACTICAL_TORPEDO_STRIKE".equals(action.id))
                .findFirst()
                .orElse(null);
        int projectilesBefore = ctx.projectiles.size();
        int torpedoesBefore = ctx.campaign.strategicTorpedoCharges;
        assertEquals(null, torpedo);
        assertFalse(CampaignSystem.executeTacticalMapAction(ctx, "TACTICAL_TORPEDO_STRIKE"));
        assertEquals(torpedoesBefore, ctx.campaign.strategicTorpedoCharges, "blocked tactical torpedo should not spend a charge");
        assertEquals(projectilesBefore, ctx.projectiles.size(),
                "blocked tactical torpedo should not create inbound objects");
    }

    @Test
    void tacticalStrikeActionsStayHiddenForHostileObjectiveSelection() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Ship hostile = firstLiveHostileShip(ctx);
        assertNotNull(hostile);
        CampaignSystem.clearSelectedCampaignContact(ctx);

        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.OBJECTIVE;
        ctx.ui.tacticalMapSelectionLabel = "Enemy Patrol";
        ctx.ui.tacticalMapSelectionSubtitle = "Hostile tactical objective";
        ctx.ui.tacticalMapSelectionDetail = "Tracked";
        ctx.ui.tacticalMapSelectionX = hostile.x;
        ctx.ui.tacticalMapSelectionY = hostile.y;
        ctx.ui.tacticalMapSelectionHostile = true;

        CampaignSystem.CampaignAction torpedo = CampaignSystem.tacticalMapVisibleActions(ctx).stream()
                .filter(action -> "TACTICAL_TORPEDO_STRIKE".equals(action.id))
                .findFirst()
                .orElse(null);
        assertEquals(null, torpedo);
        assertFalse(CampaignSystem.executeTacticalMapAction(ctx, "TACTICAL_TORPEDO_STRIKE"));
    }

    @Test
    void tacticalAtomicActionDoesNotOpenConfirmationFromMapSelection() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Ship hostile = firstLiveHostileShip(ctx);
        assertNotNull(hostile);
        ctx.campaign.strategicAtomicCharges = 1;
        ctx.campaign.campaignAmmo = 120;
        ctx.campaign.campaignFuel = 120;
        ctx.campaign.campaignSupplies = 120;
        CampaignSystem.clearSelectedCampaignContact(ctx);

        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.OBJECTIVE;
        ctx.ui.tacticalMapSelectionLabel = "Enemy Patrol";
        ctx.ui.tacticalMapSelectionSubtitle = "Hostile tactical objective";
        ctx.ui.tacticalMapSelectionDetail = "Target-Quality";
        ctx.ui.tacticalMapSelectionX = hostile.x;
        ctx.ui.tacticalMapSelectionY = hostile.y;
        ctx.ui.tacticalMapSelectionHostile = true;

        assertFalse(CampaignSystem.executeTacticalMapAction(ctx, "TACTICAL_ATOMIC_STRIKE"));
        assertFalse(ctx.ui.campaignActionConfirm.active);
        int projectilesBefore = ctx.projectiles.size();
        assertFalse(CampaignSystem.confirmCampaignAction(ctx));
        assertFalse(ctx.ui.campaignActionConfirm.active);
        assertEquals(1, ctx.campaign.strategicAtomicCharges);
        assertEquals(projectilesBefore, ctx.projectiles.size(),
                "blocked tactical atomic action should not spawn inbound objects");
    }

    @Test
    void tacticalSortieAndAtomicActionsRemainUnavailableOnHiddenStrikeTab() throws Exception {
        GameContext sortieCtx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.DRONE_CARRIER, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Ship sortieTarget = firstLiveHostileShip(sortieCtx);
        assertNotNull(sortieTarget);
        selectTacticalStrikeTarget(sortieCtx, sortieTarget);
        sortieCtx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        int sortieProjectilesBefore = sortieCtx.projectiles.size();
        long bombersBefore = sortieCtx.ships.stream().filter(ship -> ship.role == ShipRole.BOMBER && ship.faction == Faction.ALLY).count();
        assertFalse(CampaignSystem.executeTacticalMapAction(sortieCtx, "TACTICAL_CARRIER_SORTIE"));
        assertEquals(sortieProjectilesBefore, sortieCtx.projectiles.size(),
                "blocked carrier sortie should not put payload objects into tactical space");
        assertEquals(bombersBefore, sortieCtx.ships.stream().filter(ship -> ship.role == ShipRole.BOMBER && ship.faction == Faction.ALLY).count(),
                "blocked carrier sortie should not spawn friendly heavy bombers");

        GameContext atomicCtx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.CIWS_CORVETTE}
        );
        Ship atomicTarget = firstLiveHostileShip(atomicCtx);
        assertNotNull(atomicTarget);
        selectTacticalStrikeTarget(atomicCtx, atomicTarget);
        atomicCtx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        int atomicProjectilesBefore = atomicCtx.projectiles.size();
        assertFalse(CampaignSystem.executeTacticalMapAction(atomicCtx, "TACTICAL_ATOMIC_STRIKE"));
        assertFalse(atomicCtx.ui.campaignActionConfirm.active);
        assertFalse(CampaignSystem.confirmCampaignAction(atomicCtx));
        assertEquals(atomicProjectilesBefore, atomicCtx.projectiles.size(),
                "blocked atomic action should not spawn a visible inbound tactical device");
    }

    @Test
    void strategicStrikeImpactCreatesCampaignStrikeBattleEventSummary() throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.STEALTH_SHIP, ShipRole.FRIGATE, ShipRole.CIWS_CORVETTE}
        );
        Object taskForce = firstHostileTaskForce(ctx.campaign);
        assertNotNull(taskForce);

        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx,
                taskForceCenterX(ctx, ctx.campaign, taskForce),
                taskForceCenterY(ctx, ctx.campaign, taskForce)));
        assertTrue(strategicStrikeObjectCount(ctx.campaign) > 0, "torpedo strike should queue a strike object");
        advanceStrikeObjects(ctx, 240.0);
        assertEquals(0, strategicStrikeObjectCount(ctx.campaign), "torpedo strike should resolve after advance");

        String summary = CampaignSystem.campaignStrikeBattleEventSummary(ctx);
        assertTrue(summary.contains("TORPEDO IMPACT EVENT"), "summary=" + summary);
        assertTrue(summary.toLowerCase().contains("campaign strength"));
        assertTrue(CampaignSystem.campaignStrikeConsequenceLines(ctx).stream().anyMatch(line -> line.contains("IMPACT EVENT")));
    }

    @Test
    void campaignCombatUsesAuthoredPresenceInsteadOfGenericWaveSpawner() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.sector = 18;
        assertTrue(CampaignSystem.useAuthoredWaveSchedule(ctx),
                "campaign combat should rely on represented task-force pressure instead of generic pop-in waves");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static GameContext tacticalStrikeContext(int sector, ShipRole[] roles) throws Exception {
        GameContext ctx = initializedCampaignContext();
        replacePersistentFleet(ctx.campaign, roles);
        ctx.campaign.strategicOvermapMode = false;
        invokePrivateStatic("startSector", new Class<?>[]{GameContext.class, int.class}, ctx, sector);
        ctx.campaign.introSequenceActive = false;
        ctx.campaign.awaitingFleetHubChoice = false;
        ctx.campaign.awaitingEpisodeLaunch = false;
        ctx.campaign.transitionTimer = 0.0;
        UISystem.closeAllOverlays(ctx);
        ctx.campaign.campaignAmmo = 160;
        ctx.campaign.campaignFuel = 140;
        ctx.campaign.campaignSupplies = 110;
        ctx.campaign.strategicTorpedoCharges = 3;
        ctx.campaign.strategicAtomicCharges = 1;
        ctx.campaign.strategicSortiesLaunched = 0;
        ctx.campaign.campaignIntelLevel = 44.0;
        ctx.campaign.strategicExposureLevel = 10.0;
        ctx.campaign.recentStrikePressure = 0.0;
        return ctx;
    }

    private static void replacePersistentFleet(CampaignSystem.CampaignState st, ShipRole... roles) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) field.get(st);
        entries.clear();
        int slotId = 1;
        for (ShipRole role : roles) {
            entries.add(newPersistentEntry(slotId++, role, role.name().replace('_', ' ')));
        }
    }

    private static Object newPersistentEntry(int slotId, ShipRole role, String name) throws Exception {
        Class<?> entryClass = Class.forName("CampaignSystem$PersistentFleetEntry");
        Constructor<?> ctor = entryClass.getDeclaredConstructor(int.class, ShipRole.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(slotId, role, name);
    }

    private static Object newDiscoverySite(String label, String subtitle, String kind,
                                           double x, double y, double radius) throws Exception {
        Class<?> kindClass = Class.forName("CampaignSystem$DiscoveryKind");
        Class<?> siteClass = Class.forName("CampaignSystem$DiscoverySite");
        Constructor<?> ctor = siteClass.getDeclaredConstructor(
                String.class, String.class, kindClass, double.class, double.class, double.class);
        ctor.setAccessible(true);
        return ctor.newInstance(label, subtitle, enumConstant(kindClass, kind), x, y, radius);
    }

    private static void invokeResolveDiscoverySite(GameContext ctx, CampaignSystem.CampaignState st, Object site) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "resolveDiscoverySite",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                site.getClass());
        method.setAccessible(true);
        method.invoke(null, ctx, st, site);
    }

    private static Object firstHostileTaskForce(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicTaskForces");
        field.setAccessible(true);
        List<?> taskForces = (List<?>) field.get(st);
        for (Object taskForce : taskForces) {
            if (taskForce != null && getBoolean(taskForce, "hostile")) return taskForce;
        }
        return null;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static Object firstEnemyCampaignForce(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        List<?> forces = (List<?>) field.get(st);
        for (Object force : forces) {
            if (force == null) continue;
            Object faction = getObject(force, "faction");
            Object kind = getObject(force, "kind");
            if ("ENEMY".equals(String.valueOf(faction)) && !"PLAYER_FLEET".equals(String.valueOf(kind))) return force;
        }
        return null;
    }

    private static int strategicStrikeObjectCount(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicStrikeObjects");
        field.setAccessible(true);
        List<?> strikes = (List<?>) field.get(st);
        return strikes.size();
    }

    private static void assertStrategicStrikeStartsAtLauncher(String payload) throws Exception {
        GameContext ctx = tacticalStrikeContext(
                10,
                new ShipRole[]{ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.CIWS_CORVETTE}
        );
        Object taskForce = firstHostileTaskForce(ctx.campaign);
        assertNotNull(taskForce);
        double targetX = taskForceCenterX(ctx, ctx.campaign, taskForce);
        double targetY = taskForceCenterY(ctx, ctx.campaign, taskForce);
        boolean launched = switch (payload) {
            case "TORPEDO" -> CampaignSystem.launchStrategicTorpedoStrike(ctx, targetX, targetY);
            case "SORTIE" -> CampaignSystem.launchStrategicSortie(ctx, targetX, targetY);
            case "ATOMIC" -> CampaignSystem.launchStrategicAtomicStrike(ctx, targetX, targetY);
            default -> false;
        };
        assertTrue(launched);
        assertFalse(ctx.campaign.strategicStrikeObjects.isEmpty());
        Object strike = ctx.campaign.strategicStrikeObjects.get(0);
        assertEquals(ctx.player.x, getDouble(strike, "x"), 1e-9);
        assertEquals(ctx.player.y, getDouble(strike, "y"), 1e-9);
        assertTrue(Math.hypot(getDouble(strike, "x") - targetX, getDouble(strike, "y") - targetY) > 100.0);
    }

    private static double taskForceCenterX(GameContext ctx, CampaignSystem.CampaignState st, Object taskForce) throws Exception {
        return (double) invokePrivateStatic("missionSubzoneCenterX",
                new Class<?>[]{GameContext.class, int.class, int.class},
                ctx, st.sector, getInt(taskForce, "currentSubzone"));
    }

    private static double taskForceCenterY(GameContext ctx, CampaignSystem.CampaignState st, Object taskForce) throws Exception {
        return (double) invokePrivateStatic("missionSubzoneCenterY",
                new Class<?>[]{GameContext.class, int.class, int.class},
                ctx, st.sector, getInt(taskForce, "currentSubzone"));
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static Ship firstLiveHostileShip(GameContext ctx) {
        return ctx.ships.stream()
                .filter(ship -> ship != null && ship != ctx.player && ship.alive && !ship.dying && ship.hp > 0)
                .filter(ship -> ship.faction != null && !ship.faction.isFriendlyTo(ctx.player.faction))
                .findFirst()
                .orElse(null);
    }

    private static void selectTacticalStrikeTarget(GameContext ctx, Ship hostile) {
        CampaignSystem.selectCampaignContactTarget(ctx, hostile.name, "", "Target-Quality", hostile.x, hostile.y, true, true);
        ctx.ui.tacticalMapSelectionKind = UiState.TacticalMapSelectionKind.CONTACT;
        ctx.ui.tacticalMapSelectionLabel = hostile.name;
        ctx.ui.tacticalMapSelectionSubtitle = "Hostile hull";
        ctx.ui.tacticalMapSelectionDetail = "Target-Quality";
        ctx.ui.tacticalMapSelectionX = hostile.x;
        ctx.ui.tacticalMapSelectionY = hostile.y;
        ctx.ui.tacticalMapSelectionHostile = true;
    }

    private static Object invokePrivateStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object enumConstant(Class<?> type, String name) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
        return value;
    }

    private static void advanceStrikeObjects(GameContext ctx, double seconds) {
        int ticks = Math.max(1, (int) Math.ceil(seconds / 0.25));
        for (int i = 0; i < ticks; i++) {
            try {
                invokePrivateStatic("updateStrategicStrikeObjects",
                        new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                        ctx, ctx.campaign, 0.25);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static void advanceTacticalProjectiles(GameContext ctx, double seconds) {
        int ticks = Math.max(1, (int) Math.ceil(seconds / GameContext.DT));
        for (int i = 0; i < ticks; i++) {
            PhysicsSystem.update(ctx, GameContext.DT);
        }
    }
}
