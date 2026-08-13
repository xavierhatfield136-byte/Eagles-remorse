import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomShipGeneratorAndTeamETest {
    @TempDir
    Path tempDir;

    @Test
    void generatorPersistsPlayerChoicesIntoAuthoritativeDefinition() {
        CustomShipDefinition definition = CustomShipGenerator.generate(new CustomShipGenerationRequest(
                "Workshop Lance",
                "Fast picket frigate",
                CustomHullClass.FRIGATE,
                CustomCombatClassification.PICKET,
                CustomWeaponDoctrine.ENERGY,
                CustomDefenseBias.SHIELD_HEAVY,
                4
        ));

        assertEquals("Workshop Lance", definition.displayName);
        assertEquals("Fast picket frigate", definition.declaredShipClass);
        assertEquals(CustomHullClass.FRIGATE, definition.hullClass);
        assertEquals(CustomCombatClassification.PICKET, definition.combatClassification);
        assertEquals(CustomWeaponDoctrine.ENERGY, definition.weaponDoctrine);
        assertEquals(CustomDefenseBias.SHIELD_HEAVY, definition.defenseBias);
        assertEquals(4, definition.weapons.size());
        assertTrue(definition.shieldMax > 0.0);
        assertTrue(definition.validationFailures().isEmpty());
    }

    @Test
    void creationServiceImportsPngGeneratesDefinitionAndKeepsItLocal() throws Exception {
        Path source = tempDir.resolve("upload.png");
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(10, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 6; y++) {
            for (int x = 1; x < 9; x++) image.setRGB(x, y, 0xffc8f4ff);
        }
        javax.imageio.ImageIO.write(image, "png", source.toFile());
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipCreationService service = new CustomShipCreationService(registry, new CustomShipImageProcessor());

        CustomShipCreationService.CreationResult result = service.createFromPng(source,
                new CustomShipGenerationRequest("Imported E Ship", "Destroyer", CustomHullClass.CRUISER,
                        CustomCombatClassification.LINE, CustomWeaponDoctrine.BALANCED,
                        CustomDefenseBias.ARMOR_HEAVY, 5));

        assertNotNull(result.definition().id);
        assertTrue(result.folder().startsWith(registry.root()));
        assertTrue(java.nio.file.Files.isRegularFile(result.folder().resolve("definition.json")));
        assertTrue(java.nio.file.Files.isRegularFile(result.folder().resolve("hull.png")));
        assertTrue(java.nio.file.Files.isRegularFile(result.folder().resolve("thumbnail.png")));
        assertEquals(1, service.savedShips().size());
        assertEquals("Imported E Ship", service.savedShips().getFirst().displayName);
    }

    @Test
    void runtimeAdapterAppliesCustomStatsAndMountsWithoutChangingFactionAssignment() throws Exception {
        CustomShipRegistry registry = new CustomShipRegistry(tempDir.resolve("custom_ships"));
        CustomShipDefinition definition = sampleCustomDefinition(UUID.randomUUID());
        registry.save(definition);

        FleetShip ship = new FleetShip(definition.balanceTemplate, Faction.TEAM_E, 100.0, 120.0, definition, registry);

        assertEquals(Faction.TEAM_E, ship.faction);
        assertEquals(definition.id, ship.customShipDefinitionId);
        assertEquals(definition.displayName, ship.name);
        assertEquals(definition.radius, ship.radius);
        assertEquals(definition.hpMax, ship.hpMax);
        assertEquals(definition.weapons.size(), ship.turrets.size());
        assertTrue(ship.customHullImagePath.endsWith("hull.png"));
    }

    @Test
    void missionSlotCanReferenceCustomDefinitionWhileKeepingTemplateCompatibility() {
        UUID id = UUID.randomUUID();
        ShipDefinitionRef ref = ShipDefinitionRef.custom(id, ShipRole.CRUISER);

        MissionSlotSpec slot = new MissionSlotSpec(9, Faction.TEAM_E.teamId(), ShipRole.CRUISER, ref,
                MissionSlotControlMode.PLAYER_OR_AI, true, "custom-slot");

        assertEquals(ShipRole.CRUISER, slot.defaultHull());
        assertEquals(ShipRole.CRUISER, slot.definitionRef().templateRole());
        assertTrue(slot.definitionRef().isCustom());
        assertInstanceOf(ShipDefinitionRef.CustomShipRef.class, slot.definitionRef());
    }

    @Test
    void customBattleSpawnPlanCarriesCustomRefsForTeamE() {
        UUID id = UUID.randomUUID();
        MissionSlotSpec customSlot = new MissionSlotSpec(1, Faction.TEAM_E.teamId(), ShipRole.FRIGATE,
                ShipDefinitionRef.custom(id, ShipRole.FRIGATE),
                MissionSlotControlMode.PLAYER_REQUIRED, true, "player-custom");
        MissionSlotSpec enemySlot = new MissionSlotSpec(2, Faction.ENEMY.teamId(), ShipRole.FRIGATE,
                MissionSlotControlMode.AI_ONLY, true, "enemy");
        MissionLaunchSpec spec = new MissionLaunchSpec("test:custom", 1, 42L, 2400, 1800,
                List.of(customSlot, enemySlot), List.of(customSlot), "single-player:local");

        CustomBattleSpawnPlan plan = CustomBattleSpawnPlan.create(spec, new Random(42L));

        assertEquals(Faction.TEAM_E, plan.friendlyBase().faction());
        assertEquals(Faction.TEAM_E, plan.playerSpawn().faction());
        assertTrue(plan.playerSpawn().definitionRef().isCustom());
        assertTrue(plan.rosterSpawns().stream().anyMatch(spawn -> spawn.faction() == Faction.TEAM_E
                && spawn.definitionRef().isCustom()));
    }

    @Test
    void teamECanBeHostileCustomRosterInSinglePlayerCustomBattle() {
        UUID id = UUID.randomUUID();
        MissionSlotSpec playerSlot = new MissionSlotSpec(1, Faction.ALLY.teamId(), ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED, true, "player");
        MissionSlotSpec teamEHostile = new MissionSlotSpec(2, Faction.TEAM_E.teamId(), ShipRole.CRUISER,
                ShipDefinitionRef.custom(id, ShipRole.CRUISER),
                MissionSlotControlMode.AI_ONLY, true, "custom-hostile");
        MissionLaunchSpec spec = new MissionLaunchSpec("test:custom-hostile", 1, 42L, 2400, 1800,
                List.of(playerSlot, teamEHostile), List.of(playerSlot), "single-player:local");

        CustomBattleSpawnPlan plan = CustomBattleSpawnPlan.create(spec, new Random(12L));

        assertEquals(Faction.TEAM_E, plan.enemyBase().faction());
        assertTrue(plan.rosterSpawns().stream().anyMatch(spawn -> spawn.faction() == Faction.TEAM_E
                && spawn.definitionRef().isCustom()));
    }

    @Test
    void sameCustomHullCanSpawnUnderDifferentFactionAssignments() {
        UUID id = UUID.randomUUID();
        ShipDefinitionRef ref = ShipDefinitionRef.custom(id, ShipRole.FRIGATE);
        MissionSlotSpec allyCustom = new MissionSlotSpec(1, Faction.ALLY.teamId(), ShipRole.FRIGATE, ref,
                MissionSlotControlMode.PLAYER_REQUIRED, true, "ally-custom");
        MissionSlotSpec redCustom = new MissionSlotSpec(2, Faction.ENEMY.teamId(), ShipRole.FRIGATE, ref,
                MissionSlotControlMode.AI_ONLY, true, "red-custom");
        MissionLaunchSpec spec = new MissionLaunchSpec("test:custom-factions", 1, 42L, 2400, 1800,
                List.of(allyCustom, redCustom), List.of(allyCustom), "single-player:local");

        CustomBattleSpawnPlan plan = CustomBattleSpawnPlan.create(spec, new Random(7L));

        assertTrue(plan.rosterSpawns().stream().anyMatch(spawn -> spawn.faction() == Faction.ALLY
                && spawn.definitionRef().isCustom()));
        assertTrue(plan.rosterSpawns().stream().anyMatch(spawn -> spawn.faction() == Faction.ENEMY
                && spawn.definitionRef().isCustom()));
    }

    @Test
    void gameConfigKeepsTeamEIdsForCustomBattleLaunch() {
        GameConfig config = new GameConfig(GameMode.CUSTOM_BATTLES, 2400, 1800,
                true, 77L, false, Faction.TEAM_E.teamId(), false,
                Faction.ENEMY.teamId(), "", "FRIGATE=1");

        assertEquals(Faction.TEAM_E.teamId(), config.playerTeamId);
        MissionLaunchSpec spec = SinglePlayerLaunchAdapter.fromGameConfig(config);
        assertFalse(spec.resolvedRosters().isEmpty());
        assertEquals(Faction.TEAM_E.teamId(), spec.playerSlots().getFirst().teamId());
    }

    @Test
    void teamECapabilitiesStayCustomMissionOnly() {
        FactionCapabilities capabilities = FactionCapabilities.forFaction(Faction.TEAM_E);

        assertTrue(capabilities.playable());
        assertTrue(capabilities.selectableInCustomBattle());
        assertTrue(capabilities.canUseCustomHulls());
        assertFalse(capabilities.participatesInCampaign());
        assertFalse(capabilities.ownsTerritory());
        assertFalse(capabilities.canTrade());
    }

    @Test
    void campaignFactionListsDoNotIncludeTeamE() {
        assertFalse(List.of(Faction.fourTeamFactions()).contains(Faction.TEAM_E));
        assertFalse(FactionCapabilities.participatesInCampaign(Faction.TEAM_E));
    }

    private static CustomShipDefinition sampleCustomDefinition(UUID id) {
        return new CustomShipDefinition(
                id,
                "Local Spear",
                "Line Frigate",
                CustomShipDefinition.CURRENT_SCHEMA_VERSION,
                CustomShipGenerator.GENERATOR_VERSION,
                "hull.png",
                "thumbnail.png",
                CustomHullClass.FRIGATE,
                CustomCombatClassification.LINE,
                CustomWeaponDoctrine.GUNSHIP,
                CustomDefenseBias.BALANCED,
                ShipRole.FRIGATE,
                24.0,
                30,
                18.0,
                1.2,
                150.0,
                List.of(
                        new CustomWeaponMount("left", 0.62, 0.38, Turret.Kind.GUN, 0.5, 4, 900.0, 1100.0, 90),
                        new CustomWeaponMount("right", 0.62, 0.62, Turret.Kind.GUN, 0.5, 4, 900.0, 1100.0, 90)
                ),
                "standard"
        );
    }
}
