import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseSpawnLayoutTest {

    @Test
    void showcaseSpawnsFullFactionBlocksWithReadableNames() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals("Showcase Camera", ctx.player.name);
        assertEquals(4, ctx.teamBases.size());
        assertNotNull(ctx.allyBase);
        assertNotNull(ctx.enemyBase);

        int expectedPerFaction = ShipRole.values().length;
        for (Faction faction : Faction.fourTeamFactions()) {
            long factionShipCount = ctx.ships.stream()
                    .filter(s -> s != null && s.faction == faction)
                    .count();
            assertEquals(expectedPerFaction, factionShipCount, "unexpected showcase count for " + faction);

            assertTrue(ctx.ships.stream().anyMatch(s -> s != null && s.faction == faction && s.role == ShipRole.BASE));
            assertTrue(ctx.ships.stream().anyMatch(s -> s != null && s.faction == faction && s.role == ShipRole.MOTHERSHIP));
            assertFalse(ctx.ships.stream().anyMatch(s -> s != null
                    && s.faction == faction
                    && (s.name == null || !s.name.startsWith(faction.teamName() + " "))));
        }
    }
}
