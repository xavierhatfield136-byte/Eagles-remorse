import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AudioEventRoutingTest {

    @AfterEach
    void restoreAudioMode() {
        AudioSystem.setTelemetryOnly(false);
    }

    @Test
    void secondaryGunFireUsesFactionWeaponEventsInsteadOfGenericFallback() {
        assertSecondaryGunEvent(Faction.ENEMY, "sfx.weapon.red.small_fire");
        assertSecondaryGunEvent(Faction.TEAM_D, "sfx.weapon.yellow.small_fire");

        GameContext greenCtx = context();
        Ship green = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 0.0, 0.0);
        greenCtx.player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        AudioSystem.setTelemetryOnly(true);
        AudioSystem.onWeaponSecondary(greenCtx, green, List.of(new EnergyBolt(0.0, 0.0, 0.0, GameContext.DT, Faction.TEAM_C)));

        assertFalse(greenCtx.audioEvents.stream().anyMatch(ev -> "sfx.weapon.secondary_fire".equals(ev.eventId)),
                "green secondary gunfire should not fall through to the legacy generic secondary cue");
    }

    private static void assertSecondaryGunEvent(Faction faction, String expectedEventId) {
        GameContext ctx = context();
        ctx.player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        Ship source = new FleetShip(ShipRole.FRIGATE, faction, 0.0, 0.0);
        AudioSystem.setTelemetryOnly(true);

        AudioSystem.onWeaponSecondary(ctx, source, List.of(new Bullet(0.0, 0.0, 0.0, GameContext.DT, faction)));

        assertFalse(ctx.audioEvents.stream().anyMatch(ev -> "sfx.weapon.secondary_fire".equals(ev.eventId)),
                "secondary gunfire should not use the legacy generic secondary cue");
        assertEquals(expectedEventId, ctx.audioEvents.get(ctx.audioEvents.size() - 1).eventId);
    }

    private static GameContext context() {
        return new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 91234L, false));
    }
}
