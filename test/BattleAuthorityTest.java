import app.config.GameConfig;
import app.config.GameMode;
import app.config.MultiplayerLaunchConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleAuthorityTest {

    @Test
    void localBattleAuthorityAllowsSinglePlayerSimulationMutation() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CUSTOM_BATTLES, 5000, 5000, true, 123L, false));
        BattleAuthority authority = BattleAuthority.forContext(ctx);

        assertInstanceOf(LocalBattleAuthority.class, authority);
        assertTrue(authority.permits(BattleAuthorityOperation.MOVEMENT_INPUT));
        assertTrue(authority.permits(BattleAuthorityOperation.TARGET_SELECTION));
        assertTrue(authority.permits(BattleAuthorityOperation.ABILITY_ACTIVATION));
        assertTrue(authority.permits(BattleAuthorityOperation.ORDER_ISSUANCE));
        assertTrue(authority.permits(BattleAuthorityOperation.PAUSE_TIME_SCALE));
        assertTrue(authority.permits(BattleAuthorityOperation.SPAWNING));
        assertTrue(authority.permits(BattleAuthorityOperation.DAMAGE_APPLICATION));
        assertTrue(authority.permits(BattleAuthorityOperation.ENTITY_DELETION));
    }

    @Test
    void hostBattleAuthorityOwnsAuthoritativeMutationButNotClientPresentation() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        BattleAuthority authority = BattleAuthority.forContext(ctx);

        assertInstanceOf(HostBattleAuthority.class, authority);
        assertTrue(authority.permits(BattleAuthorityOperation.TARGET_SELECTION));
        assertTrue(authority.permits(BattleAuthorityOperation.ABILITY_ACTIVATION));
        assertTrue(authority.permits(BattleAuthorityOperation.SPAWNING));
        assertTrue(authority.permits(BattleAuthorityOperation.DAMAGE_APPLICATION));
        assertTrue(authority.permits(BattleAuthorityOperation.VICTORY_EVALUATION));
        assertTrue(authority.permits(BattleAuthorityOperation.ENTITY_DELETION));
        assertFalse(authority.permits(BattleAuthorityOperation.PAUSE_TIME_SCALE));
        assertFalse(authority.permits(BattleAuthorityOperation.CLIENT_PRESENTATION_UPDATE));
    }

    @Test
    void clientBattleAuthoritySubmitsIntentAndKeepsSpectatorCameraPresentationOnly() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        BattleAuthority authority = BattleAuthority.forContext(ctx);

        assertInstanceOf(ClientBattleAuthority.class, authority);
        assertTrue(authority.permits(BattleAuthorityOperation.CLIENT_COMMAND_SUBMISSION));
        assertTrue(authority.permits(BattleAuthorityOperation.CLIENT_PRESENTATION_UPDATE));
        assertTrue(authority.permits(BattleAuthorityOperation.LOCAL_SPECTATOR_CAMERA));
        assertFalse(authority.permits(BattleAuthorityOperation.TARGET_SELECTION));
        assertFalse(authority.permits(BattleAuthorityOperation.ABILITY_ACTIVATION));
        assertFalse(authority.permits(BattleAuthorityOperation.ORDER_ISSUANCE));
        assertFalse(authority.permits(BattleAuthorityOperation.SPAWNING));
        assertFalse(authority.permits(BattleAuthorityOperation.DAMAGE_APPLICATION));
        assertFalse(authority.permits(BattleAuthorityOperation.VICTORY_EVALUATION));
        assertFalse(authority.permits(BattleAuthorityOperation.ENTITY_DELETION));
    }

    @Test
    void discreteCommandsMapToAuthorityOperations() {
        assertTrue(HostBattleAuthority.INSTANCE.permits(BattleAuthorityOperation.forDiscreteCommand(
                MultiplayerCommandGate.DiscreteCommandType.SELECT_TARGET)));
        assertTrue(HostBattleAuthority.INSTANCE.permits(BattleAuthorityOperation.forDiscreteCommand(
                MultiplayerCommandGate.DiscreteCommandType.ACTIVATE_ABILITY)));
        assertFalse(ClientBattleAuthority.INSTANCE.permits(BattleAuthorityOperation.forDiscreteCommand(
                MultiplayerCommandGate.DiscreteCommandType.FLEET_ORDER)));
        assertFalse(ClientBattleAuthority.INSTANCE.permits(BattleAuthorityOperation.forDiscreteCommand(
                MultiplayerCommandGate.DiscreteCommandType.PAUSE)));
    }

    @Test
    void targetSelectionMutatesHostButNotClientPresentationState() {
        GameContext host = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        GameContext client = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));

        GameplayActions.cycleLockedTarget(host, 1);
        GameplayActions.cycleLockedTarget(client, 1);

        assertNotNull(host.lockedTarget);
        assertNull(client.lockedTarget);
        assertTrue(client.eventBanner.contains("Client must submit gameplay intent"));
    }

    @Test
    void abilityActivationDoesNotMutateClientPresentationStateLocally() {
        GameContext client = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        client.player.hasSuperweapon = true;
        int beforeProjectiles = client.projectiles.size();

        GameplayActions.trySuperweapon(client);

        assertTrue(client.player.hasSuperweapon);
        assertTrue(client.projectiles.size() == beforeProjectiles);
        assertTrue(client.eventBanner.contains("Client must submit gameplay intent"));
    }

    @Test
    void orderIssuanceDoesNotMutateClientPresentationStateLocally() {
        GameContext client = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        Ship.PowerPreset before = client.player.powerPreset;

        GameplayActions.cyclePowerPreset(client);

        assertEquals(before, client.player.powerPreset);
        assertTrue(client.eventBanner.contains("Client must submit gameplay intent"));
    }

    @Test
    void spawningDoesNotMutateClientPresentationStateLocally() {
        GameContext client = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        int beforeShips = client.ships.size();

        assertTrue(GameplayActions.tryHandleAllySpawnHotkey(client, java.awt.event.KeyEvent.VK_1));

        assertEquals(beforeShips, client.ships.size());
        assertTrue(client.eventBanner.contains("Client cannot mutate authoritative simulation state"));
    }

    @Test
    void entityDeletionDoesNotMutateClientPresentationStateLocally() {
        GameContext client = new GameContext(new GameConfig(
                GameMode.SHOOTING_RANGE, 5000, 5000, true, 123L, false));
        SpawnSystem.initWorld(client);
        client.multiplayerBattle = true;
        client.multiplayerAuthorityMode = MultiplayerAuthorityMode.CLIENT_PRESENTATION;
        int beforeShips = client.ships.size();
        java.awt.event.KeyEvent clearLayout = new java.awt.event.KeyEvent(
                new java.awt.Canvas(),
                java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                java.awt.event.InputEvent.SHIFT_DOWN_MASK,
                java.awt.event.KeyEvent.VK_BACK_SPACE,
                java.awt.event.KeyEvent.CHAR_UNDEFINED);

        assertTrue(SpawnSystem.hasShootingRangeTargets(client));
        assertTrue(GameplayActions.tryHandleShootingRangeHotkey(client, clearLayout));

        assertEquals(beforeShips, client.ships.size());
        assertTrue(client.eventBanner.contains("Client cannot mutate authoritative simulation state"));
    }
}
