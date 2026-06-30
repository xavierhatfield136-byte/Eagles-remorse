import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseTwoFleetPopulationTest {

    @Test
    void orderOfBattleExportsEveryFactionAssignmentBucketAndPassesAudit() {
        GameContext ctx = initializedCampaignContext();
        List<String> report = CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        String joined = String.join("\n", report);

        assertTrue(joined.contains("Blue starting command: MOTHERSHIP 1"));
        assertTrue(joined.contains("MINER 1"));
        assertTrue(joined.contains("Red starting inventory by role:"));
        assertTrue(joined.contains("Green starting inventory by role:"));
        assertTrue(joined.contains("Yellow starting inventory by role:"));
        assertTrue(joined.contains("garrisons"));
        assertTrue(joined.contains("convoys"));
        assertTrue(joined.contains("mining groups"));
        assertTrue(joined.contains("reserves/docked"));
        assertTrue(joined.contains("under construction"));
        assertTrue(joined.contains("unassigned"));
        assertTrue(joined.contains("AUDIT PASS"), joined);
        assertFalse(joined.contains("Emergency hull"));
    }

    @Test
    void blueStartingMinerIsPersistentVisibleOrderableAndCheckpointSafe() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignFleetRosterEntry miner = CampaignSystem.campaignFleetRosterEntries(ctx).stream()
                .filter(entry -> entry.role == ShipRole.MINER)
                .findFirst()
                .orElseThrow();

        assertTrue(miner.name.contains("PROSPECTOR"));
        assertTrue(CampaignSystem.campaignFleetRosterLines(ctx, 12).stream()
                .anyMatch(line -> line.contains("BLUE PROSPECTOR ONE")));
        assertTrue(CampaignSystem.selectCampaignFleetRosterSlot(ctx, miner.slotId));
        assertTrue(invokeCommitment(ctx, "COMMIT"));
        assertTrue(CampaignSystem.campaignFleetRosterEntries(ctx).stream()
                .anyMatch(entry -> entry.slotId == miner.slotId && entry.commitmentLabel.contains("COMMIT")));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 2);
        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertTrue(CampaignSystem.campaignFleetRosterEntries(restored).stream()
                .anyMatch(entry -> entry.role == ShipRole.MINER && entry.name.contains("PROSPECTOR")));

        long blueCombatants = CampaignSystem.campaignFleetRosterEntries(ctx).stream()
                .filter(entry -> entry.role != ShipRole.MINER)
                .count();
        assertTrue(blueCombatants <= 3, "starting fleet should remain a vulnerable picket-sized command");
    }

    @Test
    void factionPoolsContainFiniteCapitalLogisticsAndTitanDepthAcrossRealBases() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        Collection<?> records = poolRecords(ctx.campaign);

        assertFactionInventory(records, Faction.ENEMY, 250, 20, 5, 20, 20);
        assertFactionInventory(records, Faction.TEAM_C, 180, 12, 3, 20, 20);
        assertFactionInventory(records, Faction.BRIGHT_YELLOW, 170, 5, 2, 40, 12);
        assertFactionInventory(records, Faction.DARK_YELLOW, 170, 5, 2, 40, 12);

        for (Faction faction : List.of(Faction.ENEMY, Faction.TEAM_C,
                Faction.BRIGHT_YELLOW, Faction.DARK_YELLOW)) {
            Set<String> capitalBases = new HashSet<>();
            for (Object record : records) {
                if (read(record, "faction") != faction) continue;
                ShipRole role = (ShipRole) read(record, "role");
                if (!role.isCapitalCombatant() && !role.isTitanOrMothership()) continue;
                capitalBases.add(String.valueOf(read(record, "baseId")));
            }
            assertTrue(capitalBases.size() >= 2, faction + " capitals should be distributed across real locations");
            assertTrue(capitalBases.stream().allMatch(id -> !id.isBlank()));
        }
    }

    @Test
    void liveForceCompositionClaimsMissionAppropriateExistingInventoryWithoutMinting() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        int before = poolRecords(st).size();

        Object mining = firstForceByKind(st, "MINING_GROUP");
        Object convoy = firstForceByKind(st, "TRADE_GROUP");
        assertNotNull(mining);
        assertNotNull(convoy);
        assertTrue(forceRoles(st, mining).contains(ShipRole.MINER));
        assertTrue(forceRoles(st, convoy).stream()
                .anyMatch(role -> role == ShipRole.TRANSPORT || role == ShipRole.HAULER));

        Object taskForce = createForce(st, "TASK_FORCE", Faction.ENEMY,
                "Red Exceptional Titan Test Force", 1500.0, 1200.0);
        setDouble(taskForce, "strength", 100.0);
        setString(taskForce, "homeBaseId", firstBaseId(st, Faction.ENEMY));
        invokePrivate("reconcileForcePoolAssignments",
                new Class[]{CampaignSystem.CampaignState.class}, st);

        List<ShipRole> taskRoles = forceRoles(st, taskForce);
        assertTrue(taskRoles.stream().filter(ShipRole::isTitanOrMothership).count() >= 2,
                "exceptional late-region Red force should be able to contain several finite titans");
        assertTrue(taskRoles.stream().anyMatch(role -> !role.isTitanOrMothership()),
                "titan force should include finite escorts: " + taskRoles);
        assertEquals(before, poolRecords(st).size(), "force composition must never mint free hulls");
    }

    @Test
    void capitalsAreNaturalTitansAreRareAndEarlyPatrolsStayTitanFree() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        Collection<?> records = poolRecords(ctx.campaign);
        long activeCapitals = records.stream().filter(record -> isStatus(record, "ACTIVE"))
                .filter(record -> ((ShipRole) readUnchecked(record, "role")).isCapitalCombatant())
                .count();
        long activeTitans = records.stream().filter(record -> isStatus(record, "ACTIVE"))
                .filter(record -> ((ShipRole) readUnchecked(record, "role")).isTitanOrMothership())
                .count();
        long activeTotal = records.stream().filter(record -> isStatus(record, "ACTIVE")).count();

        assertTrue(activeCapitals > 0, "capital ships should occur naturally in seeded campaign forces");
        assertTrue(activeTitans > 0, "rare titan-bearing task forces should be discoverable");
        assertTrue(activeTitans * 5 < activeTotal, "titans should remain rare rather than routine traffic");

        for (Object force : forceList(ctx.campaign)) {
            if (!"PATROL_GROUP".equals(String.valueOf(read(force, "kind")))) continue;
            if ((double) read(force, "y") > 2850.0) {
                assertTrue(forceRoles(ctx.campaign, force).stream().noneMatch(ShipRole::isTitanOrMothership),
                        "ordinary early-region patrols must not contain titans");
            }
        }
    }

    @Test
    void contactDensityAndInspectionExposeUsefulIntelWithoutLowIntelLeaks() throws Exception {
        GameContext ctx = initializedCampaignContext();
        List<String> density = CampaignSystem.campaignContactDensityLines(ctx);
        assertTrue(density.stream().anyMatch(line -> line.contains("at least 1 nearby visible contact")));
        assertTrue(density.stream().anyMatch(line -> line.contains("5-8")));
        String nearby = density.stream().filter(line -> line.startsWith("Nearby traffic:")).findFirst().orElseThrow();
        int nearbyTotal = Integer.parseInt(nearby.replaceFirst(".*total (\\d+).*", "$1"));
        assertTrue(nearbyTotal >= 1, "ordinary play should usually expose at least one nearby contact");
        assertTrue(CampaignSystem.campaignForceSummaries(ctx).stream()
                .anyMatch(force -> force.faction == Faction.TEAM_C));
        assertTrue(CampaignSystem.campaignForceSummaries(ctx).stream()
                .anyMatch(force -> force.faction == Faction.BRIGHT_YELLOW));

        Object capitalForce = firstActiveCapitalForce(ctx.campaign);
        assertNotNull(capitalForce);
        int forceId = (int) read(capitalForce, "id");
        setDouble(capitalForce, "contactConfidence", 0.96);
        setObject(capitalForce, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        setBoolean(capitalForce, "visibleToPlayer", true);
        List<String> full = CampaignSystem.campaignTaskForceInspectionLines(ctx, forceId, 12);
        assertTrue(full.stream().anyMatch(line -> line.contains("estimate age")));
        assertTrue(full.stream().anyMatch(line -> line.startsWith("Composition:")));
        assertTrue(full.stream().anyMatch(line -> line.contains("CAPITAL WARNING") || line.contains("TITAN WARNING")));
        assertTrue(full.stream().anyMatch(line -> line.startsWith("Cargo:")));

        setDouble(capitalForce, "contactConfidence", 0.28);
        setObject(capitalForce, "contactState", CampaignSystem.CampaignForceContactState.SUSPECTED);
        List<String> low = CampaignSystem.campaignTaskForceInspectionLines(ctx, forceId, 12);
        assertTrue(low.stream().anyMatch(line -> line.contains("Fleet: Red")));
        assertTrue(low.stream().anyMatch(line -> line.equals("Logistics: unknown")));
        assertTrue(low.stream().noneMatch(line -> line.startsWith("Cargo:")));
        assertTrue(low.stream().noneMatch(line -> line.contains("CAPITAL WARNING") || line.contains("TITAN WARNING")));
    }

    @Test
    void phaseTwoContractsDefineTemplatesTitanRulesAndFiniteInventoryInvariant() {
        String composition = String.join("\n", CampaignSystem.campaignFleetCompositionContractLines());
        assertTrue(composition.contains("Small patrol"));
        assertTrue(composition.contains("Mining deployment"));
        assertTrue(composition.contains("Trade convoy"));
        assertTrue(composition.contains("Infrastructure defense"));
        assertTrue(composition.contains("Hunter-killer"));
        assertTrue(composition.contains("Capital task force"));
        assertTrue(composition.contains("Titan task force"));
        assertTrue(composition.contains("Mixed large fleet"));
        assertTrue(composition.contains("never deployed unless"));

        String titan = String.join("\n", CampaignSystem.campaignTitanDoctrineLines(initializedCampaignContext()));
        assertTrue(titan.contains("Construction:"));
        assertTrue(titan.contains("Deployment:"));
        assertTrue(titan.contains("Escort:"));
        assertTrue(titan.contains("Repair:"));
        assertTrue(titan.contains("Loss:"));
        assertTrue(titan.contains("Hunt:"));

        String capitals = String.join("\n", CampaignSystem.campaignCapitalPresenceLines(initializedCampaignContext()));
        assertTrue(capitals.contains("opening phase"));
        assertTrue(capitals.contains("middle phase"));
        assertTrue(capitals.contains("late phase"));
        assertTrue(capitals.contains("major salvage and reputation"));
    }

    @Test
    void capitalDamageRetreatIdentityAndTravelDensityMeasurementsSurviveCheckpoint() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        Object force = firstActiveCapitalForce(ctx.campaign);
        assertNotNull(force);
        List<Object> records = forceRecords(ctx.campaign, force);
        Object capital = records.stream()
                .filter(record -> {
                    ShipRole role = (ShipRole) readUnchecked(record, "role");
                    return role.isCapitalCombatant() || role.isTitanOrMothership();
                })
                .findFirst()
                .orElseThrow();
        int forceId = (int) read(force, "id");
        String shipName = String.valueOf(read(capital, "name"));

        CampaignSystem.CampaignLocation destination = ctx.campaign.galaxyMainPois.get(ctx.campaign.galaxyMainPois.size() - 1);
        ctx.campaign.selectedGalaxyLocationId = destination.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(ctx.campaign.transitContactTargetThisLeg > 0);
        ctx.campaign.transitContactEventsThisLeg = 3;
        setDouble(capital, "condition", 44.0);
        setDouble(force, "hullIntegrity", 44.0);
        setObject(force, "intent", CampaignSystem.CampaignForceIntent.RETREATING);

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 3);
        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        Object restoredForce = forceList(restored.campaign).stream()
                .filter(candidate -> forceId == (int) readUnchecked(candidate, "id"))
                .findFirst()
                .orElseThrow();
        Object restoredCapital = poolRecords(restored.campaign).stream()
                .filter(candidate -> shipName.equals(String.valueOf(readUnchecked(candidate, "name"))))
                .findFirst()
                .orElseThrow();

        assertEquals("RETREATING", String.valueOf(read(restoredForce, "intent")));
        assertEquals(44.0, (double) read(restoredForce, "hullIntegrity"), 1e-6);
        assertEquals(44.0, (double) read(restoredCapital, "condition"), 1e-6);
        assertEquals(3, restored.campaign.transitContactEventsThisLeg);
        assertEquals(ctx.campaign.transitContactTargetThisLeg, restored.campaign.transitContactTargetThisLeg);
    }

    @Test
    void fullIntelTitanContactOffersPersistentHuntAndYellowYardPreservesHullIdentity() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        Object titanForce = firstActiveTitanForce(ctx.campaign);
        assertNotNull(titanForce);
        setDouble(titanForce, "contactConfidence", 0.98);
        setObject(titanForce, "contactState", CampaignSystem.CampaignForceContactState.KNOWN);
        setBoolean(titanForce, "visibleToPlayer", true);
        int titanForceId = (int) read(titanForce, "id");
        assertTrue(CampaignSystem.campaignTaskForceInspectionLines(ctx, titanForceId, 12).stream()
                .anyMatch(line -> line.startsWith("Titan Hunt Opportunity:")));

        CampaignSystem.CampaignLocation yellowYard = ctx.campaign.galaxyMainPois.stream()
                .filter(location -> location.ownerFaction == Faction.BRIGHT_YELLOW)
                .findFirst()
                .orElseThrow();
        int before = ctx.campaign.persistentBlueFleet.size();
        assertTrue((boolean) invokePrivate("queueCampaignConstructionOrder",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class,
                        ShipRole.class, int.class, int.class, int.class},
                ctx, ctx.campaign, yellowYard, ShipRole.FRIGATE, 0, 0, 0));
        invokePrivate("advanceCampaignYardOrders",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, ctx.campaign, 10_000.0);
        assertEquals(before + 1, ctx.campaign.persistentBlueFleet.size());
        Object built = ctx.campaign.persistentBlueFleet.get(ctx.campaign.persistentBlueFleet.size() - 1);
        assertEquals(Faction.BRIGHT_YELLOW.name(), read(built, "factionName"),
                "purchased Yellow hulls should retain producing-faction identity under Blue command");
    }

    @Test
    void tacticalConversionUsesEveryPersistentManifestMemberExactlyOnceAndReconcilesLosses() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.campaignOrderOfBattleReportLines(ctx);
        Object force = firstActiveCapitalForce(ctx.campaign);
        assertNotNull(force);
        setDouble(force, "crewReadiness", 54.0);
        setDouble(force, "ammoLevel", 63.0);
        setObject(force, "intent", CampaignSystem.CampaignForceIntent.RETREATING);
        List<Object> records = forceRecords(ctx.campaign, force);
        assertTrue(records.size() >= 2);
        Object damaged = records.get(0);
        setDouble(damaged, "condition", 41.0);
        String damagedName = String.valueOf(read(damaged, "name"));
        ShipRole damagedRole = (ShipRole) read(damaged, "role");

        Object manifest = invokePrivate("encounterManifestForForce",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, nested("CampaignForce"), int.class},
                ctx, ctx.campaign, force, 1);
        List<?> entries = (List<?>) read(manifest, "ships");
        assertEquals(records.size(), entries.size(),
                "persistent manifests must not be truncated by tactical display limits");
        Object manifestEntry = entries.stream()
                .filter(entry -> damagedName.equals(readUnchecked(entry, "name")))
                .findFirst()
                .orElseThrow();
        assertEquals(damagedRole, read(manifestEntry, "role"));
        assertEquals(41.0, (double) read(manifestEntry, "condition"), 1e-6);
        assertEquals(54.0, (double) read(manifestEntry, "crewReadiness"), 1e-6);
        assertEquals(63.0, (double) read(manifestEntry, "ammoLevel"), 1e-6);
        assertTrue((boolean) read(manifestEntry, "retreatIntent"));

        int spawned = (int) invokePrivate("spawnEncounterForceManifest",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, nested("EncounterForceManifest"),
                        double.class, double.class, double.class, double.class, boolean.class},
                ctx, ctx.campaign, manifest, 2500.0, 2500.0, 100.0, 80.0, true);
        assertEquals(records.size(), spawned);
        assertEquals(0, invokePrivate("spawnEncounterForceManifest",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, nested("EncounterForceManifest"),
                        double.class, double.class, double.class, double.class, boolean.class},
                ctx, ctx.campaign, manifest, 2500.0, 2500.0, 100.0, 80.0, true),
                "a persistent record must never have duplicate strategic and tactical instances");

        Ship tacticalDamaged = ctx.ships.stream()
                .filter(ship -> ship != null && damagedName.equals(ship.name))
                .findFirst()
                .orElseThrow();
        assertEquals(damagedRole, tacticalDamaged.role);
        assertEquals(Faction.ENEMY, tacticalDamaged.faction);
        assertEquals(0.41, tacticalDamaged.hp / (double) tacticalDamaged.hpMax, 0.03);
        assertEquals(0.54, tacticalDamaged.crewReadiness(), 0.01);

        tacticalDamaged.hp = 0;
        tacticalDamaged.alive = false;
        invokePrivate("reconcileCampaignForceLiveMembership",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, nested("CampaignForce")},
                ctx, ctx.campaign, force);
        assertEquals("DESTROYED", String.valueOf(read(damaged, "status")));
        assertEquals(0.0, (double) read(damaged, "condition"), 1e-6);
        if (damagedRole.isCapitalCombatant() || damagedRole.isTitanOrMothership()) {
            assertFalse(ctx.campaign.recoverableWreckSites.isEmpty(),
                    "destroyed capital identities should create recovery targets");
        }
        assertTrue(records.stream().skip(1).allMatch(record -> !"DESTROYED".equals(String.valueOf(readUnchecked(record, "status")))),
                "surviving tactical members must return to their strategic force");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 24680L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void assertFactionInventory(Collection<?> records,
                                               Faction faction,
                                               int minimumTotal,
                                               int minimumCapitals,
                                               int minimumTitans,
                                               int minimumLogistics,
                                               int minimumMiners) throws Exception {
        int total = 0;
        int capitals = 0;
        int titans = 0;
        int logistics = 0;
        int miners = 0;
        for (Object record : records) {
            if (read(record, "faction") != faction) continue;
            total++;
            ShipRole role = (ShipRole) read(record, "role");
            if (role.isCapitalCombatant()) capitals++;
            if (role.isTitanOrMothership()) titans++;
            if (role == ShipRole.HAULER || role == ShipRole.TRANSPORT || role.isCarrierHull()) logistics++;
            if (role == ShipRole.MINER) miners++;
        }
        assertTrue(total >= minimumTotal, faction + " total inventory " + total);
        assertTrue(capitals >= minimumCapitals, faction + " capital inventory " + capitals);
        assertTrue(titans >= minimumTitans, faction + " titan inventory " + titans);
        assertTrue(logistics >= minimumLogistics, faction + " logistics inventory " + logistics);
        assertTrue(miners >= minimumMiners, faction + " miner inventory " + miners);
    }

    private static boolean invokeCommitment(GameContext ctx, String name) throws Exception {
        Class<?> type = nested("FleetCommitment");
        Object value = Enum.valueOf(type.asSubclass(Enum.class), name);
        return (boolean) invokePrivate("setCampaignFleetCommitment", new Class[]{GameContext.class, type}, ctx, value);
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        return (CampaignCheckpointStore.Checkpoint) invokePrivate("captureCheckpoint",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, int.class},
                ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        return (boolean) invokePrivate("applyCheckpoint",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class},
                ctx, ctx.campaign, checkpoint);
    }

    @SuppressWarnings("unchecked")
    private static Collection<?> poolRecords(CampaignSystem.CampaignState st) throws Exception {
        return ((Map<Integer, ?>) read(st, "campaignShipPool")).values();
    }

    @SuppressWarnings("unchecked")
    private static List<?> forceList(CampaignSystem.CampaignState st) throws Exception {
        return (List<?>) read(st, "campaignForces");
    }

    private static Object firstForceByKind(CampaignSystem.CampaignState st, String kind) throws Exception {
        for (Object force : forceList(st)) {
            if (kind.equals(String.valueOf(read(force, "kind")))) return force;
        }
        return null;
    }

    private static Object firstActiveCapitalForce(CampaignSystem.CampaignState st) throws Exception {
        for (Object record : poolRecords(st)) {
            if (!isStatus(record, "ACTIVE")) continue;
            ShipRole role = (ShipRole) read(record, "role");
            if (!role.isCapitalCombatant() && !role.isTitanOrMothership()) continue;
            int forceId = (int) read(record, "forceId");
            for (Object force : forceList(st)) {
                if ((int) read(force, "id") == forceId) return force;
            }
        }
        return null;
    }

    private static Object firstActiveTitanForce(CampaignSystem.CampaignState st) throws Exception {
        for (Object record : poolRecords(st)) {
            if (!isStatus(record, "ACTIVE")) continue;
            ShipRole role = (ShipRole) read(record, "role");
            if (!role.isTitanOrMothership()) continue;
            int forceId = (int) read(record, "forceId");
            for (Object force : forceList(st)) {
                if ((int) read(force, "id") == forceId) return force;
            }
        }
        return null;
    }

    private static List<ShipRole> forceRoles(CampaignSystem.CampaignState st, Object force) throws Exception {
        int forceId = (int) read(force, "id");
        java.util.ArrayList<ShipRole> roles = new java.util.ArrayList<>();
        for (Object record : poolRecords(st)) {
            if ((int) read(record, "forceId") == forceId && !isStatus(record, "DESTROYED")) {
                roles.add((ShipRole) read(record, "role"));
            }
        }
        return roles;
    }

    private static List<Object> forceRecords(CampaignSystem.CampaignState st, Object force) throws Exception {
        int forceId = (int) read(force, "id");
        java.util.ArrayList<Object> records = new java.util.ArrayList<>();
        for (Object record : poolRecords(st)) {
            if ((int) read(record, "forceId") == forceId && !isStatus(record, "DESTROYED")) records.add(record);
        }
        return records;
    }

    private static Object createForce(CampaignSystem.CampaignState st,
                                      String kind,
                                      Faction faction,
                                      String name,
                                      double x,
                                      double y) throws Exception {
        Object kindValue = Enum.valueOf(CampaignSystem.CampaignForceKind.class, kind);
        return invokePrivate("ensureCampaignForce",
                new Class[]{CampaignSystem.CampaignState.class, CampaignSystem.CampaignForceKind.class,
                        Faction.class, String.class, String.class, String.class, double.class, double.class},
                st, kindValue, faction, name, "phase-two-test-base", "Phase 2 composition contract", x, y);
    }

    private static String firstBaseId(CampaignSystem.CampaignState st, Faction faction) {
        for (CampaignSystem.CampaignLocation location : st.galaxyMainPois) {
            if (location != null && location.ownerFaction == faction && !location.destroyed) return location.id;
        }
        return "";
    }

    private static boolean isStatus(Object record, String status) {
        return status.equals(String.valueOf(readUnchecked(record, "status")));
    }

    private static Object read(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object readUnchecked(Object target, String fieldName) {
        try {
            return read(target, fieldName);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setString(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Class<?> nested(String simpleName) {
        for (Class<?> type : CampaignSystem.class.getDeclaredClasses()) {
            if (simpleName.equals(type.getSimpleName())) return type;
        }
        throw new AssertionError("Missing nested class " + simpleName);
    }
}
