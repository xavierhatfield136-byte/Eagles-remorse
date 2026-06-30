import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YellowFactionMigrationSystemTest {
    @Test
    void goldenLegacyShipPreservesIdentityDamageCargoCommanderHistoryAndMission() throws Exception {
        Properties fixture = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("test", "fixtures", "legacy_yellow_ship.properties"))) {
            fixture.load(in);
        }
        YellowFactionMigrationSystem.ShipSnapshot legacy = new YellowFactionMigrationSystem.ShipSnapshot(
                fixture.getProperty("id"), fixture.getProperty("name"), fixture.getProperty("faction"),
                fixture.getProperty("hull"), Integer.parseInt(fixture.getProperty("hullDamage")),
                Integer.parseInt(fixture.getProperty("armorDamage")), Integer.parseInt(fixture.getProperty("cargo")),
                fixture.getProperty("commander"), fixture.getProperty("serviceHistory"), fixture.getProperty("missionState"));

        YellowFactionMigrationSystem.ShipSnapshot migrated = YellowFactionMigrationSystem.migrateShip(legacy);

        assertNotEquals("TEAM_D", migrated.factionId());
        assertEquals(legacy.id(), migrated.id());
        assertEquals(legacy.name(), migrated.name());
        assertEquals(legacy.hullId(), migrated.hullId());
        assertEquals(legacy.hullDamage(), migrated.hullDamage());
        assertEquals(legacy.armorDamage(), migrated.armorDamage());
        assertEquals(legacy.cargo(), migrated.cargo());
        assertEquals(legacy.commander(), migrated.commander());
        assertEquals(legacy.serviceHistory(), migrated.serviceHistory());
        assertEquals(legacy.missionState(), migrated.missionState());
    }

    @Test
    void contentAliasesAreDeterministicAndWarningsTellAuthorsHowToRepairThem() {
        YellowFactionMigrationSystem.ContentReference first =
                YellowFactionMigrationSystem.resolveContentFaction("Yellow", "fleet-8");
        YellowFactionMigrationSystem.ContentReference again =
                YellowFactionMigrationSystem.resolveContentFaction("TEAM_D", "fleet-8");
        assertEquals(first.faction(), again.faction());
        assertTrue(first.migrated());
        assertTrue(first.warning().contains("BRIGHT_YELLOW or DARK_YELLOW"));
        assertFalse(YellowFactionMigrationSystem.resolveContentFaction("DARK_YELLOW", "fleet-8").migrated());
    }

    @Test
    void objectivesAndEveryAssetViewUseExplicitSuccessorIdentity() {
        String migrated = YellowFactionMigrationSystem.migrateObjectiveText(
                "Protect Yellow forces and recover Yellow territory");
        assertTrue(migrated.contains("Bright Yellow or Dark Orange-Yellow"));
        for (String view : new String[]{"shipyard", "encounter", "fleet builder", "salvage", "memorial", "archive"}) {
            String bright = YellowFactionMigrationSystem.presentationLabel(Faction.BRIGHT_YELLOW, "YRS Meridian", view);
            String dark = YellowFactionMigrationSystem.presentationLabel(Faction.DARK_YELLOW, "YRS Meridian", view);
            assertTrue(bright.contains("Bright Yellow") && bright.contains("BYC") && bright.contains("yellow_sunburst"));
            assertTrue(dark.contains("Dark Orange-Yellow") && dark.contains("DYC") && dark.contains("yellow_split_chevron"));
        }
    }

    @Test
    void neutralActorsFormationAndPoliticalDoctrineAreExplicit() {
        assertEquals(Faction.ExternalDisposition.PROTECTED,
                Faction.BRIGHT_YELLOW.perceivedBy(Faction.ExternalActor.CIVILIAN));
        assertEquals(Faction.ExternalDisposition.PREDATORY,
                Faction.DARK_YELLOW.perceivedBy(Faction.ExternalActor.PIRATE));
        assertEquals(Faction.ExternalDisposition.HOSTILE,
                Faction.BRIGHT_YELLOW.perceivedBy(Faction.ExternalActor.ROGUE_AI));
        assertNotEquals(Faction.BRIGHT_YELLOW.formationKey(), Faction.DARK_YELLOW.formationKey());
        assertNotEquals(Faction.BRIGHT_YELLOW.politicalDoctrineKey(), Faction.DARK_YELLOW.politicalDoctrineKey());
        assertTrue(Faction.BRIGHT_YELLOW.canAnswerSupportCallFrom(Faction.TEAM_C));
        assertTrue(Faction.DARK_YELLOW.canAnswerSupportCallFrom(Faction.ENEMY));
        assertFalse(Faction.DARK_YELLOW.canTradeWith(Faction.PLAYER));
        assertTrue(Faction.BRIGHT_YELLOW.isHostileTo(Faction.DARK_YELLOW));
    }

    @Test
    void goldenCampaignFixtureCoversTerritoryFleetDiplomacyAndActiveEncounter() throws Exception {
        Properties fixture = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("test", "fixtures", "legacy_yellow_campaign.properties"))) {
            fixture.load(in);
        }
        YellowFactionMigrationSystem.ContentReference territory = YellowFactionMigrationSystem.resolveContentFaction(
                fixture.getProperty("territory.owner"), fixture.getProperty("territory.id"));
        YellowFactionMigrationSystem.ContentReference fleet = YellowFactionMigrationSystem.resolveContentFaction(
                fixture.getProperty("fleet.faction"), fixture.getProperty("fleet.id"));
        YellowFactionMigrationSystem.ContentReference encounter = YellowFactionMigrationSystem.resolveContentFaction(
                fixture.getProperty("encounter.faction"), fixture.getProperty("encounter.id"));
        assertNotEquals(Faction.TEAM_D, territory.faction());
        assertNotEquals(Faction.TEAM_D, fleet.faction());
        assertNotEquals(Faction.TEAM_D, encounter.faction());
        assertEquals("DEFECTOR_ESCORT_ACTIVE", fixture.getProperty("encounter.mission"));
        assertTrue(Boolean.parseBoolean(fixture.getProperty("diplomacy.blueYellow")));
        assertTrue(Faction.BRIGHT_YELLOW.isFriendlyTo(Faction.PLAYER));
        assertTrue(Faction.DARK_YELLOW.isFriendlyTo(Faction.ENEMY));
    }
}
