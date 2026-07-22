import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SinglePlayerCustomBattleRegressionTest {

    @Test
    void legacyCustomBattleRosterStringsStillDriveSinglePlayerSpawns() {
        GameConfig config = new GameConfig(
                GameMode.CUSTOM_BATTLES,
                5000,
                5000,
                true,
                1234L,
                false,
                Faction.TEAM_C.teamId(),
                false,
                Faction.TEAM_D.teamId(),
                "FRIGATE=2;CRUISER=1",
                "MISSILE_BOAT=3;LIGHT_CRUISER=1");

        GameContext ctx = new GameContext(config);
        SpawnSystem.initWorld(ctx);

        assertFalse(ctx.multiplayerBattle);
        assertEquals(null, ctx.config.multiplayerLaunch);
        assertEquals(2, count(ctx, Faction.TEAM_C, ShipRole.FRIGATE));
        assertEquals(1, count(ctx, Faction.TEAM_C, ShipRole.CRUISER));
        assertEquals(3, count(ctx, Faction.TEAM_D, ShipRole.MISSILE_BOAT));
        assertEquals(1, count(ctx, Faction.TEAM_D, ShipRole.LIGHT_CRUISER));
        assertEquals(1, count(ctx, Faction.TEAM_C, ShipRole.BASE));
        assertEquals(1, count(ctx, Faction.TEAM_D, ShipRole.BASE));
    }

    @Test
    void singlePlayerLaunchAdapterPreservesLegacyCustomBattleRosterCounts() {
        GameConfig config = new GameConfig(
                GameMode.CUSTOM_BATTLES,
                5000,
                4200,
                true,
                1234L,
                false,
                Faction.TEAM_C.teamId(),
                false,
                Faction.TEAM_D.teamId(),
                "FRIGATE=2;CRUISER=1",
                "MISSILE_BOAT=3;LIGHT_CRUISER=1");

        MissionLaunchSpec spec = SinglePlayerLaunchAdapter.fromGameConfig(config);

        assertEquals(CustomMissionCatalog.CUSTOM_BATTLE_ID, spec.missionId());
        assertEquals(1234L, spec.seed());
        assertEquals(5000, spec.worldW());
        assertEquals(4200, spec.worldH());
        assertEquals(2, count(spec, Faction.TEAM_C, ShipRole.FRIGATE));
        assertEquals(1, count(spec, Faction.TEAM_C, ShipRole.CRUISER));
        assertEquals(3, count(spec, Faction.TEAM_D, ShipRole.MISSILE_BOAT));
        assertEquals(1, count(spec, Faction.TEAM_D, ShipRole.LIGHT_CRUISER));
        assertEquals(1, spec.playerSlots().size());
        assertEquals(MissionSlotControlMode.PLAYER_REQUIRED, spec.playerSlots().get(0).controlMode());
    }

    @Test
    void singlePlayerLaunchAdapterMaterializesDefaultRostersInLaunchSpec() {
        GameConfig config = new GameConfig(
                GameMode.CUSTOM_BATTLES,
                5000,
                4200,
                true,
                1234L,
                false,
                Faction.ALLY.teamId(),
                false,
                Faction.ENEMY.teamId(),
                "",
                "");

        MissionLaunchSpec spec = SinglePlayerLaunchAdapter.fromGameConfig(config);

        assertEquals(11, spec.resolvedRosters().stream()
                .filter(slot -> slot.teamId() == Faction.ALLY.teamId())
                .count());
        assertEquals(14, spec.resolvedRosters().stream()
                .filter(slot -> slot.teamId() == Faction.ENEMY.teamId())
                .count());
        assertEquals(4, count(spec, Faction.ALLY, ShipRole.FRIGATE));
        assertEquals(6, count(spec, Faction.ENEMY, ShipRole.FRIGATE));
    }

    @Test
    void customBattleSpawnPlanBuildsRosterPlacementsWithoutMutatingGameContext() {
        CustomBattleSpawnPlan plan = CustomBattleSpawnPlan.create(
                5000,
                4200,
                new Random(1234L),
                Faction.TEAM_C.teamId(),
                Faction.TEAM_D.teamId(),
                "FRIGATE=2;CRUISER=1",
                "MISSILE_BOAT=3;LIGHT_CRUISER=1");

        assertEquals(Faction.TEAM_C, plan.friendlyBase().faction());
        assertEquals(Faction.TEAM_D, plan.enemyBase().faction());
        assertEquals(ShipRole.MOTHERSHIP, plan.playerSpawn().role());
        assertEquals(Faction.TEAM_C, plan.playerSpawn().faction());
        assertEquals(7, plan.rosterSpawns().size());
        assertEquals(2, count(plan, Faction.TEAM_C, ShipRole.FRIGATE));
        assertEquals(1, count(plan, Faction.TEAM_C, ShipRole.CRUISER));
        assertEquals(3, count(plan, Faction.TEAM_D, ShipRole.MISSILE_BOAT));
        assertEquals(1, count(plan, Faction.TEAM_D, ShipRole.LIGHT_CRUISER));
    }

    @Test
    void customBattleSpawnPlanCanBeBuiltFromSinglePlayerMissionLaunchSpec() {
        GameConfig config = new GameConfig(
                GameMode.CUSTOM_BATTLES,
                5000,
                4200,
                true,
                1234L,
                false,
                Faction.TEAM_C.teamId(),
                false,
                Faction.TEAM_D.teamId(),
                "FRIGATE:2\nCRUISER=1",
                "MISSILE_BOAT=3;LIGHT_CRUISER:1");
        MissionLaunchSpec spec = SinglePlayerLaunchAdapter.fromGameConfig(config);

        CustomBattleSpawnPlan plan = CustomBattleSpawnPlan.create(spec, new Random(1234L));

        assertEquals(spec.worldW(), 5000);
        assertEquals(Faction.TEAM_C, plan.friendlyBase().faction());
        assertEquals(Faction.TEAM_D, plan.enemyBase().faction());
        assertEquals(7, plan.rosterSpawns().size());
        assertEquals(2, count(plan, Faction.TEAM_C, ShipRole.FRIGATE));
        assertEquals(1, count(plan, Faction.TEAM_C, ShipRole.CRUISER));
        assertEquals(3, count(plan, Faction.TEAM_D, ShipRole.MISSILE_BOAT));
        assertEquals(1, count(plan, Faction.TEAM_D, ShipRole.LIGHT_CRUISER));
    }

    private static long count(GameContext ctx, Faction faction, ShipRole role) {
        return ctx.ships.stream()
                .filter(ship -> ship != null)
                .filter(ship -> ship.faction == faction)
                .filter(ship -> ship.role == role)
                .count();
    }

    private static long count(MissionLaunchSpec spec, Faction faction, ShipRole role) {
        return spec.resolvedRosters().stream()
                .filter(slot -> slot != null)
                .filter(slot -> slot.teamId() == faction.teamId())
                .filter(slot -> slot.defaultHull() == role)
                .count();
    }

    private static long count(CustomBattleSpawnPlan plan, Faction faction, ShipRole role) {
        return plan.rosterSpawns().stream()
                .filter(spawn -> spawn != null)
                .filter(spawn -> spawn.faction() == faction)
                .filter(spawn -> spawn.role() == role)
                .count();
    }
}
