import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMissionTransitionWeaponResetTest {

    @Test
    void pruneTransientUnitsClearsStalePrimaryGunLocksAcrossMissionTransitions() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 9000, 9000, true, 1234L, false));
        Player player = new Player(ShipRole.MOTHERSHIP, 1200, 1200);
        ctx.player = player;
        ctx.ships.add(player);

        player.firePrimary(player.x + 1600.0, player.y, GameContext.DT);

        boolean lockedBeforeTransition = player.turrets.stream()
                .filter(t -> t != null && t.primary && t.kind == Turret.Kind.GUN)
                .anyMatch(t -> !t.canFire());
        assertTrue(lockedBeforeTransition, "expected at least one mothership gun to be mid-cycle before sector cleanup");

        Method pruneTransientUnits = CampaignSystem.class.getDeclaredMethod("pruneTransientUnits", GameContext.class);
        pruneTransientUnits.setAccessible(true);
        pruneTransientUnits.invoke(null, ctx);

        boolean lockedAfterTransition = player.turrets.stream()
                .filter(t -> t != null && t.primary && t.kind == Turret.Kind.GUN)
                .anyMatch(t -> !t.canFire());
        assertFalse(lockedAfterTransition, "mission cleanup should release stale gun locks for the next sector");
    }
}
