import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiplayerPostV1TeamAuthorityTest {

    @Test
    void sameTeamAndAiExpansionStayDisabledUntilOpposingDuelIsStable() {
        MultiplayerPostV1TeamAuthority authority =
                new MultiplayerPostV1TeamAuthority(false, true, true);

        assertFalse(authority.sameTeamSlotsEnabled());
        assertFalse(authority.aiShipsEnabled());
        assertFalse(authority.opposingDuelStable());
    }

    @Test
    void sameTeamAndAiExpansionCanBeEnabledAfterOpposingDuelIsStable() {
        MultiplayerPostV1TeamAuthority authority =
                new MultiplayerPostV1TeamAuthority(true, true, true);

        assertTrue(authority.sameTeamSlotsEnabled());
        assertTrue(authority.aiShipsEnabled());
        assertEquals(MultiplayerPostV1TeamAuthority.SensorSharing.COMPLETE_TEAM_VISIBILITY,
                authority.sensorSharingForSameTeam());
    }

    @Test
    void fleetCommanderCanIssueTeamWideOrdersAndSeeCaptainAuthorityUi() {
        MultiplayerPostV1TeamAuthority authority =
                new MultiplayerPostV1TeamAuthority(true, true, true);
        authority.registerSlot(new MultiplayerPostV1TeamAuthority.SlotAuthority(
                1, Faction.ALLY.teamId(), 101,
                MultiplayerPostV1TeamAuthority.AuthorityRole.FLEET_COMMANDER,
                true));

        assertTrue(authority.validate(1, MultiplayerPostV1TeamAuthority.TeamCommand.FLEET_ORDER).accepted());
        assertTrue(authority.validate(1, MultiplayerPostV1TeamAuthority.TeamCommand.FORMATION).accepted());
        assertTrue(authority.validate(1, MultiplayerPostV1TeamAuthority.TeamCommand.ESCORT_ORDER).accepted());
        assertTrue(authority.validateAiEscortScope(1, Faction.ALLY.teamId()).accepted());
    }

    @Test
    void nonCommanderKeepsDirectShipControlButCannotOverwriteTeamOrders() {
        MultiplayerPostV1TeamAuthority authority =
                new MultiplayerPostV1TeamAuthority(true, true, true);
        authority.registerSlot(new MultiplayerPostV1TeamAuthority.SlotAuthority(
                2, Faction.ALLY.teamId(), 202,
                MultiplayerPostV1TeamAuthority.AuthorityRole.DIRECT_PILOT,
                true));

        assertTrue(authority.validate(2, MultiplayerPostV1TeamAuthority.TeamCommand.DIRECT_SHIP_INPUT).accepted());
        assertTrue(authority.validate(2, MultiplayerPostV1TeamAuthority.TeamCommand.TARGET_SELECTION).accepted());
        assertFalse(authority.validate(2, MultiplayerPostV1TeamAuthority.TeamCommand.FLEET_ORDER).accepted());
        assertFalse(authority.validate(2, MultiplayerPostV1TeamAuthority.TeamCommand.FORMATION).accepted());
        assertFalse(authority.validate(2, MultiplayerPostV1TeamAuthority.TeamCommand.ESCORT_ORDER).accepted());
    }

    @Test
    void aiEscortScopeRequiresEnabledAiCommanderAndSameTeamEscort() {
        MultiplayerPostV1TeamAuthority authority =
                new MultiplayerPostV1TeamAuthority(true, true, true);
        authority.registerSlot(new MultiplayerPostV1TeamAuthority.SlotAuthority(
                1, Faction.ALLY.teamId(), 101,
                MultiplayerPostV1TeamAuthority.AuthorityRole.FLEET_COMMANDER,
                true));

        assertFalse(authority.validateAiEscortScope(1, Faction.ENEMY.teamId()).accepted());

        MultiplayerPostV1TeamAuthority disabledAi =
                new MultiplayerPostV1TeamAuthority(true, true, false);
        disabledAi.registerSlot(new MultiplayerPostV1TeamAuthority.SlotAuthority(
                1, Faction.ALLY.teamId(), 101,
                MultiplayerPostV1TeamAuthority.AuthorityRole.FLEET_COMMANDER,
                true));

        assertFalse(disabledAi.validateAiEscortScope(1, Faction.ALLY.teamId()).accepted());
    }
}
