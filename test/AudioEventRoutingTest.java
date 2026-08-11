import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    @Test
    void redCannonFireAlwaysUsesAuthoredThudInsteadOfFallbackVariant() throws Exception {
        GameContext ctx = context();
        Ship source = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 0.0, 0.0);
        AudioSystem.setTelemetryOnly(true);

        for (int i = 0; i < 4; i++) {
            AudioSystem.onWeaponPrimary(ctx, source, List.of(new Bullet(0.0, 0.0, 0.0, GameContext.DT, Faction.ENEMY)));
            Thread.sleep(55L);
        }

        List<AudioEvent> redShots = ctx.audioEvents.stream()
                .filter(ev -> "sfx.weapon.red.small_fire".equals(ev.eventId))
                .toList();
        assertEquals(4, redShots.size(), "each red cannon shot should dispatch the red cannon cue");
        assertFalse(redShots.stream().anyMatch(ev -> ev.variantSeed != 0),
                "red cannon fire should stay on the authored thud variant instead of alternating to fallback");
        assertFalse(ctx.audioEvents.stream().anyMatch(ev -> "sfx.weapon.primary_fire".equals(ev.eventId)),
                "red cannon fire should not use the legacy generic primary cue");
    }

    @Test
    void factionWeaponFireUsesOnlyAuthoredPrimaryAudioVariant() throws Exception {
        for (String color : List.of("blue", "red", "green", "yellow")) {
            for (String size : List.of("small", "medium", "capital")) {
                String eventId = "weapon." + color + "." + size + "_fire";
                assertEquals(1, sfxVariantCount(eventId),
                        eventId + " should not alternate into the old fallback thud variant");
            }
        }
    }

    @Test
    void weaponLaunchAndCiwsCuesUseOnlyNewAuthoredVariant() throws Exception {
        for (String eventId : List.of("weapon.missile_launch", "weapon.torpedo_launch", "weapon.ciws_fire")) {
            assertEquals(1, sfxVariantCount(eventId),
                    eventId + " should not rotate into the old fallback launch/fire thud");
        }
    }

    @Test
    void greenCapitalBeamLoopPromotesOverSmallWeaponLoop() throws Exception {
        GameContext ctx = context();
        ctx.player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        AudioSystem.setTelemetryOnly(true);

        Ship small = new FleetShip(ShipRole.FIGHTER, Faction.TEAM_C, 0.0, 0.0);
        AudioSystem.onWeaponPrimary(ctx, small, List.of(new Bullet(0.0, 0.0, 0.0, GameContext.DT, Faction.TEAM_C)));
        assertEquals("weapon.green.small_fire", activeGreenLoopEventId(ctx));

        Ship capital = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.TEAM_C, 0.0, 0.0);
        PhaserBeam beam = new PhaserBeam(capital, null, 0.0, 3000.0, 16.0, 80.0, 90, Faction.TEAM_C);
        AudioSystem.onWeaponPrimary(ctx, capital, List.of(beam));
        assertEquals("weapon.green.capital_fire", activeGreenLoopEventId(ctx),
                "capital Green beams should replace an active small-weapon loop instead of reusing its thump");
        assertFalse(shouldSwitchGreenLoopEvent("weapon.green.capital_fire", "weapon.green.capital_fire", 10.0, 11.0),
                "repeated capital Green beams should extend the loop instead of restarting its attack transient");

        AudioSystem.onWeaponPrimary(ctx, small, List.of(new Bullet(0.0, 0.0, 0.0, GameContext.DT, Faction.TEAM_C)));
        assertEquals("weapon.green.capital_fire", activeGreenLoopEventId(ctx),
                "small Green shots should not immediately downgrade an active capital beam loop");
    }

    @Test
    void greenTitanBeamRolesUseCapitalBeamLoop() throws Exception {
        AudioSystem.setTelemetryOnly(true);
        List<ShipRole> roles = List.of(
                ShipRole.VANGUARD_TITAN,
                ShipRole.INTERDICTION_TITAN,
                ShipRole.BOARDING_RECOVERY_TITAN,
                ShipRole.SHIELD_BASTION_TITAN,
                ShipRole.ELITE_REINFORCEMENTS_TITAN,
                ShipRole.MOBILE_STATION_TITAN,
                ShipRole.HYPERWEAPON_TITAN,
                ShipRole.MOTHERSHIP
        );

        for (ShipRole role : roles) {
            GameContext ctx = context();
            ctx.player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
            Ship titan = new FleetShip(role, Faction.TEAM_C, 0.0, 0.0);
            PhaserBeam beam = new PhaserBeam(titan, null, 0.0, 3000.0, 16.0, 80.0, 90, Faction.TEAM_C);

            AudioSystem.onWeaponPrimary(ctx, titan, List.of(beam));

            assertEquals("weapon.green.capital_fire", activeGreenLoopEventId(ctx),
                    role + " Green beam weapons should use the capital laser loop");
        }
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

    private static int sfxVariantCount(String eventId) throws Exception {
        Class<?> assetLibrary = Class.forName("AudioSystem$AssetLibrary");
        Method method = assetLibrary.getDeclaredMethod("sfxVariantCount", SfxManifest.EventSpec.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, SfxManifest.byId(eventId));
    }

    private static String activeGreenLoopEventId(GameContext ctx) throws Exception {
        Method stateFor = AudioSystem.class.getDeclaredMethod("stateFor", GameContext.class);
        stateFor.setAccessible(true);
        Object state = stateFor.invoke(null, ctx);
        Field field = state.getClass().getDeclaredField("greenWeaponLoopEventId");
        field.setAccessible(true);
        return (String) field.get(state);
    }

    private static boolean shouldSwitchGreenLoopEvent(String activeEventId, String incomingEventId,
                                                      double now, double activeUntilBeforeTouch) throws Exception {
        Method method = AudioSystem.class.getDeclaredMethod(
                "shouldSwitchGreenLoopEvent", String.class, String.class, double.class, double.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, activeEventId, incomingEventId, now, activeUntilBeforeTouch);
    }
}
