import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetBuildingSystemTest {
    @Test
    void everyHullHasReadableBattlefieldIdentityAndBudgets() {
        assertEquals(ShipRole.values().length, FleetBuildingSystem.hullRoster().size());
        for (ShipRole role : ShipRole.values()) {
            FleetBuildingSystem.HullProfile profile = FleetBuildingSystem.hullProfile(role);
            assertNotNull(profile, role + " should have a hull profile");
            assertFalse(profile.battlefieldRole.isBlank());
            assertFalse(profile.counter.isBlank());
            assertFalse(profile.weakness.isBlank());
            assertTrue(profile.silhouetteCheck.contains("combat zoom"));
            assertTrue(profile.budgets.weight > 0);
        }
    }

    @Test
    void liveFleetShipExposesIdentityCard() {
        FleetShip artillery = new FleetShip(ShipRole.ARTILLERY_SHIP, Faction.ALLY, 0.0, 0.0);
        assertTrue(artillery.battlefieldIdentityCard().contains("long-range fire support"));
        assertEquals(FleetBuildingSystem.HullFamily.ARTILLERY, artillery.hullProfile().family);
    }

    @Test
    void slotBudgetedRefitsSupportTemplatesCapturedTechAndFieldPenalties() {
        FleetBuildingSystem.RefitModule capturedEw = new FleetBuildingSystem.RefitModule(
                "captured-ew-suite", FleetBuildingSystem.ModuleSlot.SENSOR,
                FleetBuildingSystem.ModuleRarity.CAPTURED,
                new FleetBuildingSystem.HullBudgets(2, 3, 2, 1, 2),
                "Red", "jamming and lock disruption", true);
        FleetBuildingSystem.RefitTemplate template = new FleetBuildingSystem.RefitTemplate(
                "Ghost Spotter", List.of(capturedEw), "stealth artillery");
        FleetBuildingSystem.saveLoadout(template);

        FleetBuildingSystem.RefitAssessment yard = FleetBuildingSystem.assessRefit(
                ShipRole.STEALTH_SHIP, template, false, "Blue", "Blue");
        FleetBuildingSystem.RefitAssessment field = FleetBuildingSystem.assessRefit(
                ShipRole.STEALTH_SHIP, template, true, "Blue", "Blue");

        assertTrue(yard.valid);
        assertEquals(template, FleetBuildingSystem.savedLoadout("Ghost Spotter"));
        assertTrue(yard.warnings.stream().anyMatch(line -> line.contains("captured module")));
        assertTrue(yard.warnings.stream().anyMatch(line -> line.contains("compatibility")));
        assertTrue(field.fieldReliability < yard.fieldReliability);
        assertTrue(field.refitDays <= yard.refitDays);
    }

    @Test
    void persistentShipsAccumulateHistoryMoraleMemorialsAndSuccessors() {
        FleetBuildingSystem.FleetArchive archive = new FleetBuildingSystem.FleetArchive();
        FleetBuildingSystem.Captain captain = new FleetBuildingSystem.Captain(
                "Mira Venn", FleetBuildingSystem.CaptainPersonality.RESOURCEFUL);
        FleetBuildingSystem.PersistentShip ship = new FleetBuildingSystem.PersistentShip(
                "B-014", ShipRole.FRIGATE, "Blue Guard", captain);
        archive.commission(ship);

        ship.recordBattle(13, false, true);
        ship.recordRescue(24);
        ship.sufferShortage(80);
        assertTrue(ship.name.contains("\"Lifeline\"") || ship.name.contains("\"Linebreaker\""));
        assertTrue(ship.commendations.contains("Rescue Pennant"));
        assertTrue(captain.refusalRisk > 0);

        archive.lose("B-014", "lost covering an evacuation");
        FleetBuildingSystem.PersistentShip successor = archive.commissionSuccessor(
                "B-014", "B-052", ShipRole.FRIGATE);

        assertNotNull(successor);
        assertTrue(successor.name.contains("II"));
        assertEquals(1, archive.memorials().size());
        assertTrue(archive.screenLines().stream().anyMatch(line -> line.contains("MEMORIAL")));
    }

    @Test
    void constructionQueueAndDoctrineSuggestionsExposeStrategicCosts() {
        FleetBuildingSystem.ConstructionOrder order = new FleetBuildingSystem.ConstructionOrder(
                ShipRole.COMMAND_INTEL_TITAN, "Blue", 12, true);
        FleetBuildingSystem.queueConstruction(order);
        order.advanceDay();

        assertEquals(11, order.remainingDays);
        assertTrue(FleetBuildingSystem.constructionQueue().contains(order));
        assertTrue(FleetBuildingSystem.doctrineSuggestion("artillery").contains("spotter"));
        assertTrue(FleetBuildingSystem.doctrineSuggestion("rescue").contains("hospital"));
        assertTrue(FleetBuildingSystem.specialistPrograms().stream()
                .anyMatch(program -> program.family == FleetBuildingSystem.HullFamily.HOSPITAL));
        assertTrue(FleetBuildingSystem.specialistPrograms().stream()
                .anyMatch(program -> program.family == FleetBuildingSystem.HullFamily.PROTOTYPE
                        && program.maintenanceBurden >= 7));
        assertTrue(FleetBuildingSystem.shipyardCanConstruct("Green contract yards", FleetBuildingSystem.HullFamily.MINING));
        assertFalse(FleetBuildingSystem.shipyardCanConstruct("Green contract yards", FleetBuildingSystem.HullFamily.TITAN));
    }

    @Test
    void damagedModuleDecisionDistinguishesRepairFromReplacement() {
        FleetBuildingSystem.RefitModule common = new FleetBuildingSystem.RefitModule(
                "drive-coils", FleetBuildingSystem.ModuleSlot.ENGINE, FleetBuildingSystem.ModuleRarity.COMMON,
                new FleetBuildingSystem.HullBudgets(2, 2, 1, 1, 1), "open market", "mobility", false);
        FleetBuildingSystem.RefitModule prototype = new FleetBuildingSystem.RefitModule(
                "phase-drive", FleetBuildingSystem.ModuleSlot.ENGINE, FleetBuildingSystem.ModuleRarity.PROTOTYPE,
                new FleetBuildingSystem.HullBudgets(3, 3, 2, 1, 3), "Red", "phase mobility", false);

        assertTrue(FleetBuildingSystem.damagedModuleDecision(common, 80, true).startsWith("Replace"));
        assertTrue(FleetBuildingSystem.damagedModuleDecision(prototype, 80, true).startsWith("Repair"));
        assertEquals("REFUSAL RISK", FleetBuildingSystem.disciplineRiskLabel(40, 0));
        assertEquals("DESERTION RISK", FleetBuildingSystem.disciplineRiskLabel(20, 0));
        assertEquals("MUTINY RISK", FleetBuildingSystem.disciplineRiskLabel(5, 0));
    }

    @Test
    void campaignCheckpointPayloadPreservesSectionFourShipRecordsAndLoadsLegacyEntries() throws Exception {
        CampaignSystem.CampaignState state = new CampaignSystem.CampaignState();
        Object entry = addCampaignEntry(state, ShipRole.FRIGATE, "Blue Guard");
        setField(entry, "captainName", "Captain Mira Vale");
        setField(entry, "crewExperience", 14);
        setField(entry, "morale", 61);
        setField(entry, "scars", 2);
        setField(entry, "serviceHistory", "MAJOR HULL DAMAGE");

        String payload = serializeCampaignFleet(state.persistentBlueFleet);
        CampaignSystem.CampaignState restored = new CampaignSystem.CampaignState();
        restoreCampaignFleet(restored, payload);
        Object restoredEntry = restored.persistentBlueFleet.get(0);

        assertEquals("Captain Mira Vale", getField(restoredEntry, "captainName"));
        assertEquals(14, getField(restoredEntry, "crewExperience"));
        assertEquals(61, getField(restoredEntry, "morale"));
        assertEquals(2, getField(restoredEntry, "scars"));
        assertEquals("MAJOR HULL DAMAGE", getField(restoredEntry, "serviceHistory"));

        CampaignSystem.CampaignState legacy = new CampaignSystem.CampaignState();
        restoreCampaignFleet(legacy, "1,FRIGATE,false,Qmx1ZSBHdWFyZCBPbmU");
        assertEquals(1, legacy.persistentBlueFleet.size());
        assertTrue(((String) getField(legacy.persistentBlueFleet.get(0), "captainName")).startsWith("Captain "));
    }

    private static Object addCampaignEntry(CampaignSystem.CampaignState state, ShipRole role, String name) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("addPersistentFleetEntry",
                CampaignSystem.CampaignState.class, ShipRole.class, String.class);
        method.setAccessible(true);
        method.invoke(null, state, role, name);
        return state.persistentBlueFleet.get(0);
    }

    private static String serializeCampaignFleet(List<?> fleet) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("serializePersistentBlueFleet", List.class);
        method.setAccessible(true);
        return (String) method.invoke(null, fleet);
    }

    private static void restoreCampaignFleet(CampaignSystem.CampaignState state, String payload) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("restorePersistentBlueFleet",
                CampaignSystem.CampaignState.class, String.class);
        method.setAccessible(true);
        method.invoke(null, state, payload);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
