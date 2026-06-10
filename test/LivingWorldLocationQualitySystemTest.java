import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LivingWorldLocationQualitySystemTest {

    @Test
    void livingWorldLocationLinesCoverStationsRevisitsTrafficAndOrbitalLayers() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 101L, false));
        SpawnSystem.initWorld(ctx);

        List<String> lines = LivingWorldLocationQualitySystem.allLivingWorldLocationLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Station Modules  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Station Damage  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Station Rebuild  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Station Evacuation  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Installation Control  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Garrison Assignment  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Living Visuals  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Revisit Visuals  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Persistent Wreck Field  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Debris Ambush  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Traffic Season  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Station Commander  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Orbital Layer  |  ")
                && line.contains("low orbit") && line.contains("moon relays")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Location History  |  ")));
    }
}
