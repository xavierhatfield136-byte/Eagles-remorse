import app.config.MultiplayerMissionChoice;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomMissionCatalogTest {

    @Test
    void v1DuelIsFilteredByCapabilityRequirements() {
        assertTrue(CustomMissionCatalog.multiplayerEntries(CustomMissionCatalog.v1SupportedCapabilities())
                .stream()
                .anyMatch(descriptor -> CustomMissionCatalog.V1_DUEL_ID.equals(descriptor.id())));
        assertTrue(CustomMissionCatalog.multiplayerEntries(CustomMissionCatalog.v1SupportedCapabilities())
                .stream()
                .anyMatch(descriptor -> CustomMissionCatalog.HEAVY_DUEL_ID.equals(descriptor.id())));
        assertTrue(CustomMissionCatalog.multiplayerEntries(Set.of()).isEmpty());
        assertEquals(MultiplayerRulesV1.supportedCapabilities(), CustomMissionCatalog.v1SupportedCapabilities());
    }

    @Test
    void menuMissionChoicesResolveToCatalogEntries() {
        assertTrue(CustomMissionCatalog.multiplayerEntries(CustomMissionCatalog.v1SupportedCapabilities())
                .stream()
                .anyMatch(descriptor -> MultiplayerMissionChoice.V1_DUEL.missionId().equals(descriptor.id())));
        assertTrue(CustomMissionCatalog.multiplayerEntries(CustomMissionCatalog.v1SupportedCapabilities())
                .stream()
                .anyMatch(descriptor -> MultiplayerMissionChoice.HEAVY_DUEL.missionId().equals(descriptor.id())));
    }

    @Test
    void v1MultiplayerCatalogExposesEveryMenuMissionAsMultiplayerVariant() {
        Set<String> exposedIds = CustomMissionCatalog.multiplayerEntries(CustomMissionCatalog.v1SupportedCapabilities())
                .stream()
                .map(CustomMissionDescriptor::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Set<String> menuMissionIds = Arrays.stream(MultiplayerMissionChoice.values())
                .map(MultiplayerMissionChoice::missionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(menuMissionIds, exposedIds);
        assertTrue(exposedIds.contains(CustomMissionCatalog.CUSTOM_BATTLE_ID));
        assertTrue(exposedIds.contains(CustomMissionCatalog.LAST_STAND_ID));
        assertTrue(exposedIds.contains(CustomMissionCatalog.RESOURCE_RUSH_ID));
        assertTrue(exposedIds.contains(CustomMissionCatalog.FOUR_TEAM_DOMINATION_ID));
        assertTrue(exposedIds.contains(CustomMissionCatalog.SHOOTING_RANGE_ID));
        assertTrue(exposedIds.contains(CustomMissionCatalog.FLEET_SHOWCASE_ID));
    }

    @Test
    void reservedMissionIdsDoNotRequirePlayableDescriptors() {
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.V1_DUEL_ID));
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.CUSTOM_BATTLE_ID));
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.LAST_STAND_ID));
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.RESOURCE_RUSH_ID));
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.FOUR_TEAM_DOMINATION_ID));
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.SHOOTING_RANGE_ID));
        assertTrue(CustomMissionCatalog.isReservedMissionId(CustomMissionCatalog.FLEET_SHOWCASE_ID));
        assertTrue(CustomMissionCatalog.isExperimentalMissionId(CustomMissionCatalog.SHOOTING_RANGE_ID));
        assertTrue(CustomMissionCatalog.isExperimentalMissionId("experimental:prototype"));

        assertTrue(CustomMissionCatalog.descriptorFor(CustomMissionCatalog.CUSTOM_BATTLE_ID) != null);
        assertTrue(CustomMissionCatalog.descriptorFor(CustomMissionCatalog.RESOURCE_RUSH_ID) != null);
        assertTrue(CustomMissionCatalog.descriptorFor(CustomMissionCatalog.SHOOTING_RANGE_ID) != null);
    }

    @Test
    void everyMenuMissionChoiceResolvesToAcceptedMultiplayerSpec() {
        for (MultiplayerMissionChoice choice : MultiplayerMissionChoice.values()) {
            MissionLaunchSpec spec = CustomMissionCatalog.resolveMultiplayerMission(
                    choice.missionId(), 123L, 0, 0);

            assertEquals(choice.missionId(), spec.missionId());
            assertEquals("elimination", spec.objectiveType());
            assertEquals(MultiplayerRulesV1.VictoryRule.ELIMINATION, spec.victoryRule());
            assertTrue(MultiplayerMissionValidator.validateForV1(spec).accepted(),
                    choice + " should be accepted as a multiplayer mission");
        }
    }

    @Test
    void v1DuelLaunchSpecCarriesStableMissionIdentityAndSlots() {
        MissionLaunchSpec spec = CustomMissionCatalog.resolveV1Duel(
                1234L, 5000, 4200, ShipRole.CRUISER, ShipRole.BATTLECRUISER);

        assertEquals(CustomMissionCatalog.V1_DUEL_ID, spec.missionId());
        assertEquals(CustomMissionCatalog.V1_DUEL_REVISION, spec.missionRevision());
        assertEquals(CustomMissionCatalog.V1_RULES_PROFILE_ID, spec.rulesProfileId());
        assertEquals("elimination", spec.objectiveType());
        assertEquals(MultiplayerRulesV1.VictoryRule.ELIMINATION, spec.victoryRule());
        assertEquals(5000, spec.worldW());
        assertEquals(4200, spec.worldH());
        assertEquals(2, spec.playerSlots().size());
        assertEquals(ShipRole.CRUISER, spec.playerSlots().get(0).defaultHull());
        assertEquals(ShipRole.BATTLECRUISER, spec.playerSlots().get(1).defaultHull());
    }

    @Test
    void hostAdapterBuildsV1BattleSetupFromLaunchSpec() {
        MissionLaunchSpec spec = CustomMissionCatalog.resolveV1Duel(
                55L, 3600, 2200, ShipRole.CRUISER, ShipRole.BATTLECRUISER);

        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerHostLaunchAdapter.toBattleSetup(spec, "Ada", "Grace");

        assertEquals(55L, setup.seed());
        assertEquals("Ada", setup.hostSlot().displayName());
        assertEquals("Grace", setup.clientSlot().displayName());
        assertEquals(ShipRole.CRUISER, setup.hostSlot().hull());
        assertEquals(ShipRole.BATTLECRUISER, setup.clientSlot().hull());
        assertTrue(MultiplayerRulesV1.validate(setup).accepted());
    }

    @Test
    void heavyDuelVariesHullAndMapWithoutChangingRulesProfile() {
        MissionLaunchSpec spec = CustomMissionCatalog.resolveHeavyDuel(777L);

        assertEquals(CustomMissionCatalog.HEAVY_DUEL_ID, spec.missionId());
        assertEquals(CustomMissionCatalog.V1_RULES_PROFILE_ID, spec.rulesProfileId());
        assertEquals(5200, spec.worldW());
        assertEquals(3200, spec.worldH());
        assertEquals(ShipRole.CRUISER, spec.playerSlots().get(0).defaultHull());
        assertEquals(ShipRole.BATTLECRUISER, spec.playerSlots().get(1).defaultHull());

        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerHostLaunchAdapter.toBattleSetup(spec, "Host", "Client");

        assertTrue(MultiplayerRulesV1.validate(setup).accepted());
    }

    @Test
    void missionDefinitionAndLockedLaunchSpecDigestsHaveDifferentScopes() {
        String definition = MissionDigest.missionDefinitionDigest(
                CustomMissionCatalog.v1DuelDescriptor(),
                CustomMissionCatalog.v1DuelTemplate());
        MissionLaunchSpec first = CustomMissionCatalog.resolveV1Duel(
                100L, 3600, 2200, ShipRole.FRIGATE, ShipRole.FRIGATE);
        MissionLaunchSpec second = CustomMissionCatalog.resolveV1Duel(
                200L, 3600, 2200, ShipRole.FRIGATE, ShipRole.FRIGATE);
        MissionLaunchSpec alternateVictoryMetadata = new MissionLaunchSpec(
                first.missionId(),
                first.missionRevision(),
                first.seed(),
                first.worldW(),
                first.worldH(),
                first.resolvedRosters(),
                first.playerSlots(),
                first.rulesProfileId(),
                "control",
                MultiplayerRulesV1.VictoryRule.ELIMINATION);

        String firstLocked = MissionDigest.lockedLaunchSpecDigest(first, 1L);
        String secondLocked = MissionDigest.lockedLaunchSpecDigest(second, 1L);
        String alternateLocked = MissionDigest.lockedLaunchSpecDigest(alternateVictoryMetadata, 1L);

        assertFalse(definition.isBlank());
        assertFalse(firstLocked.isBlank());
        assertNotEquals(definition, firstLocked);
        assertNotEquals(firstLocked, secondLocked);
        assertNotEquals(firstLocked, alternateLocked);
        assertEquals(definition, MissionDigest.missionDefinitionDigest(
                CustomMissionCatalog.v1DuelDescriptor(),
                CustomMissionCatalog.v1DuelTemplate()));
    }

    @Test
    void clientAdapterPreparesPresentationMetadataWithoutAuthoritativeContext() {
        MissionLaunchSpec spec = CustomMissionCatalog.resolveV1Duel(
                100L, 3600, 2200, ShipRole.FRIGATE, ShipRole.FRIGATE);

        MultiplayerClientLaunchAdapter.ClientPresentationLaunch launch =
                MultiplayerClientLaunchAdapter.prepare(
                        spec,
                        CustomMissionCatalog.v1DuelDescriptor(),
                        CustomMissionCatalog.v1DuelTemplate(),
                        7L);

        assertEquals(spec, launch.spec());
        assertFalse(launch.missionDefinitionDigest().isBlank());
        assertFalse(launch.lockedLaunchSpecDigest().isBlank());
        assertThrows(IllegalArgumentException.class, () ->
                MultiplayerClientLaunchAdapter.prepare(null,
                        CustomMissionCatalog.v1DuelDescriptor(),
                        CustomMissionCatalog.v1DuelTemplate(),
                        7L));
    }
}
