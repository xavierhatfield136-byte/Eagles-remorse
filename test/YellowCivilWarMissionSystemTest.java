import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YellowCivilWarMissionSystemTest {
    @Test
    void missionDeckCoversEveryAuthoredCivilWarFamily() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(9701L);

        List<StrategicCampaignExpansionSystem.CivilWarMission> missions =
                StrategicCampaignExpansionSystem.generateCivilWarMissionDeck(state, "frontier");

        assertEquals(EnumSet.allOf(StrategicCampaignExpansionSystem.CivilWarMissionType.class),
                missions.stream().map(item -> item.type).collect(
                        java.util.stream.Collectors.toCollection(() ->
                                EnumSet.noneOf(StrategicCampaignExpansionSystem.CivilWarMissionType.class))));
        assertTrue(missions.stream().allMatch(item -> !item.title.isBlank() && !item.objective.isBlank()));
        assertTrue(missions.stream().filter(item -> item.humanitarianLives > 0).count() >= 3);
        assertTrue(missions.stream().filter(item -> item.identityVerificationRequired).count() >= 5);
        assertTrue(missions.stream().anyMatch(item -> item.type
                == StrategicCampaignExpansionSystem.CivilWarMissionType.COALITION_INTERVENTION
                && item.collateralLimit <= 10 && item.legitimacyAtStake >= 20));
    }

    @Test
    void missionResolutionEnforcesCollateralIdentityAndPoliticalConsequences() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(9702L);
        StrategicCampaignExpansionSystem.Territory frontier =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        int legitimacy = frontier.legitimacy;
        StrategicCampaignExpansionSystem.CivilWarMission intervention =
                StrategicCampaignExpansionSystem.createCivilWarMission(state,
                        StrategicCampaignExpansionSystem.CivilWarMissionType.COALITION_INTERVENTION, "frontier");

        assertFalse(StrategicCampaignExpansionSystem.resolveCivilWarMission(
                state, intervention.id, true, intervention.collateralLimit + 1, true));
        assertEquals(StrategicCampaignExpansionSystem.CivilWarMissionStatus.FAILED, intervention.status);
        assertTrue(frontier.legitimacy < legitimacy);

        StrategicCampaignExpansionSystem.CivilWarMission identity =
                StrategicCampaignExpansionSystem.createCivilWarMission(state,
                        StrategicCampaignExpansionSystem.CivilWarMissionType.IDENTITY_VERIFICATION, "frontier");
        assertFalse(StrategicCampaignExpansionSystem.resolveCivilWarMission(state, identity.id, true, 0, false));
        assertTrue(identity.outcome.contains("Allegiance"));
    }

    @Test
    void identityIncidentsNeverAuthorizeByHullColorAndPersistVerifiedEvidence() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(9703L);
        for (StrategicCampaignExpansionSystem.IdentityIncidentType type
                : StrategicCampaignExpansionSystem.IdentityIncidentType.values()) {
            StrategicCampaignExpansionSystem.IdentityIncident incident =
                    StrategicCampaignExpansionSystem.createIdentityIncident(state, type, "frontier", true);
            assertNotNull(incident);
            assertFalse(incident.evidence.isBlank());
            assertTrue(StrategicCampaignExpansionSystem.verifyIdentityIncident(state, incident.id, "drive-signature"));
            assertTrue(incident.verified);
        }

        StrategicCampaignExpansionSystem.State restored = StrategicCampaignExpansionSystem.restore(
                StrategicCampaignExpansionSystem.serialize(state), 9703L);
        assertEquals(4, restored.identityIncidents.size());
        assertTrue(restored.identityIncidents.stream().allMatch(item -> item.verified));
        assertTrue(restored.warEvents.stream().anyMatch(item -> item.consequence.contains("hull color")));
    }

    @Test
    void missionAndPoliticalStateRoundTrip() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(9704L);
        StrategicCampaignExpansionSystem.CivilWarMission mission =
                StrategicCampaignExpansionSystem.createCivilWarMission(state,
                        StrategicCampaignExpansionSystem.CivilWarMissionType.PRISONER_EXCHANGE, "frontier");
        assertTrue(StrategicCampaignExpansionSystem.resolveCivilWarMission(state, mission.id, true, 0, true));

        StrategicCampaignExpansionSystem.State restored = StrategicCampaignExpansionSystem.restore(
                StrategicCampaignExpansionSystem.serialize(state), 9704L);
        assertEquals(1, restored.civilWarMissions.size());
        assertEquals(StrategicCampaignExpansionSystem.CivilWarMissionStatus.SUCCEEDED,
                restored.civilWarMissions.get(0).status);
        assertEquals(mission.outcome, restored.civilWarMissions.get(0).outcome);
        assertTrue(restored.brightPoliticalObligation > 0);
    }

    @Test
    void civilWarDirectorGeneratesRaidsInvasionsDefensesAndCeasefires() {
        StrategicCampaignExpansionSystem.State raidState = StrategicCampaignExpansionSystem.bootstrap(9705L);
        assertEquals(StrategicCampaignExpansionSystem.OperationType.RAID,
                StrategicCampaignExpansionSystem.advanceYellowCivilWar(raidState, 0, 0, false).type);

        StrategicCampaignExpansionSystem.State invasionState = StrategicCampaignExpansionSystem.bootstrap(9706L);
        invasionState.civilWarElapsedTicks = 2;
        assertEquals(StrategicCampaignExpansionSystem.OperationType.INVASION,
                StrategicCampaignExpansionSystem.advanceYellowCivilWar(invasionState, 0, 0, false).type);

        StrategicCampaignExpansionSystem.State defenseState = StrategicCampaignExpansionSystem.bootstrap(9707L);
        defenseState.civilWarElapsedTicks = 4;
        assertEquals(StrategicCampaignExpansionSystem.OperationType.DEFENSIVE_REINFORCEMENT,
                StrategicCampaignExpansionSystem.advanceYellowCivilWar(defenseState, 0, 0, false).type);

        StrategicCampaignExpansionSystem.State ceasefireState = StrategicCampaignExpansionSystem.bootstrap(9708L);
        ceasefireState.brightYellowExhaustion = 50;
        ceasefireState.darkYellowExhaustion = 50;
        assertEquals(null, StrategicCampaignExpansionSystem.advanceYellowCivilWar(ceasefireState, 0, 0, true));
        assertTrue(ceasefireState.civilWarCeasefire);
    }
}
