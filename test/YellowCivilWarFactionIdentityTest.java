import org.junit.jupiter.api.Test;
import app.config.GameConfig;
import app.config.GameMode;
import app.config.ExperienceSettings;
import app.persistence.CampaignCheckpointStore;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YellowCivilWarFactionIdentityTest {
    @Test
    void successorColorsRemainDistinctInEveryColorVisionPalette() {
        ExperienceSettings original = ExperienceRuntime.active().copy();
        try {
            for (ExperienceSettings.ColorblindPalette palette : ExperienceSettings.ColorblindPalette.values()) {
                if (palette == ExperienceSettings.ColorblindPalette.STANDARD) continue;
                ExperienceSettings settings = ExperienceSettings.defaults();
                settings.colorblindPalette = palette;
                ExperienceRuntime.activate(settings);
                assertNotEquals(ExperienceRuntime.factionColor(Faction.BRIGHT_YELLOW, true),
                        ExperienceRuntime.factionColor(Faction.DARK_YELLOW, true), palette.toString());
            }
        } finally {
            ExperienceRuntime.activate(original);
        }
    }

    @Test
    void territoryOverlayUsesNamesPatternsStatesSupplyFrontsOperationsAndBeachheads() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 440L, false));
        SpawnSystem.initWorld(ctx);
        CampaignSystem.CampaignTerritoryOverlayView bright = CampaignSystem.campaignTerritoryOverlayViews(ctx).stream()
                .filter(view -> view.faction() == Faction.BRIGHT_YELLOW).findFirst().orElseThrow();
        assertFalse(bright.name().isBlank());
        assertFalse(bright.insignia().isBlank());
        assertFalse(bright.pattern().isBlank());
        assertTrue(bright.statusText().contains(bright.controlState().name()));
        assertTrue(bright.statusText().contains(bright.supplyState().name()));
    }

    @Test
    void successorAlliancesAndHostilityMatchCoalitions() {
        assertTrue(Faction.BRIGHT_YELLOW.isFriendlyTo(Faction.PLAYER));
        assertTrue(Faction.BRIGHT_YELLOW.isFriendlyTo(Faction.ALLY));
        assertTrue(Faction.BRIGHT_YELLOW.isFriendlyTo(Faction.TEAM_C));
        assertTrue(Faction.DARK_YELLOW.isFriendlyTo(Faction.ENEMY));
        assertFalse(Faction.BRIGHT_YELLOW.isFriendlyTo(Faction.DARK_YELLOW));
        assertFalse(Faction.DARK_YELLOW.isFriendlyTo(Faction.BRIGHT_YELLOW));
        assertFalse(Faction.DARK_YELLOW.isFriendlyTo(Faction.PLAYER));
    }

    @Test
    void successorFactionsShareLegacyYellowMaterialCatalogByReference() {
        assertEquals(Faction.TEAM_D, Faction.BRIGHT_YELLOW.hullCatalogFaction());
        assertEquals(Faction.TEAM_D, Faction.DARK_YELLOW.hullCatalogFaction());
        assertEquals(ShipIdentityRegistry.factionTraitFor(Faction.TEAM_D),
                ShipIdentityRegistry.factionTraitFor(Faction.BRIGHT_YELLOW));
        assertEquals(ShipIdentityRegistry.factionTraitFor(Faction.TEAM_D),
                ShipIdentityRegistry.factionTraitFor(Faction.DARK_YELLOW));
        assertEquals(DoctrineRegistry.forFaction(Faction.TEAM_D),
                DoctrineRegistry.forFaction(Faction.BRIGHT_YELLOW));
        assertEquals(DoctrineRegistry.forFaction(Faction.TEAM_D),
                DoctrineRegistry.forFaction(Faction.DARK_YELLOW));
    }

    @Test
    void nonColorIdentityMetadataDistinguishesSuccessors() {
        assertNotEquals(Faction.BRIGHT_YELLOW.insigniaKey(), Faction.DARK_YELLOW.insigniaKey());
        assertNotEquals(Faction.BRIGHT_YELLOW.mapPatternKey(), Faction.DARK_YELLOW.mapPatternKey());
        assertNotEquals(Faction.BRIGHT_YELLOW.transponderPrefix(), Faction.DARK_YELLOW.transponderPrefix());
        assertNotEquals(Faction.BRIGHT_YELLOW.teamName(), Faction.DARK_YELLOW.teamName());
    }

    @Test
    void everyPairwiseRelationshipMatchesSuccessorCoalitions() {
        Faction.clearCampaignAlliances();
        for (Faction first : Faction.values()) {
            for (Faction second : Faction.values()) {
                boolean sameTeam = first.teamId() == second.teamId();
                boolean blueBright = (first == Faction.BRIGHT_YELLOW
                        && (second == Faction.PLAYER || second == Faction.ALLY || second == Faction.TEAM_C))
                        || (second == Faction.BRIGHT_YELLOW
                        && (first == Faction.PLAYER || first == Faction.ALLY || first == Faction.TEAM_C));
                boolean redDark = (first == Faction.DARK_YELLOW && second == Faction.ENEMY)
                        || (second == Faction.DARK_YELLOW && first == Faction.ENEMY);
                assertEquals(sameTeam || blueBright || redDark, first.isFriendlyTo(second),
                        first + " relationship to " + second);
            }
        }
    }

    @Test
    void everyHullRoleSpawnsForBothSuccessorsWithSharedYellowLayoutAndDoctrine() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 440L, false));
        SpawnSystem.initWorld(ctx);
        int index = 0;
        for (ShipRole role : ShipRole.values()) {
            Ship bright = SpawnSystem.spawnCatalogShip(ctx, role, Faction.BRIGHT_YELLOW,
                    300.0 + (index % 10) * 120.0, 300.0 + (index / 10) * 150.0);
            Ship dark = SpawnSystem.spawnCatalogShip(ctx, role, Faction.DARK_YELLOW,
                    2500.0 + (index % 10) * 120.0, 300.0 + (index / 10) * 150.0);
            assertTrue(bright != null, "Bright Yellow failed to spawn " + role);
            assertTrue(dark != null, "Dark Yellow failed to spawn " + role);
            assertEquals(role, bright.role);
            assertEquals(role, dark.role);
            assertEquals(Faction.BRIGHT_YELLOW, bright.faction);
            assertEquals(Faction.DARK_YELLOW, dark.faction);
            assertEquals(DoctrineRegistry.forFaction(Faction.TEAM_D), DoctrineRegistry.forFaction(bright.faction));
            assertEquals(DoctrineRegistry.forFaction(Faction.TEAM_D), DoctrineRegistry.forFaction(dark.faction));
            List<ShipRoomLayout.RoomId> legacyRooms = ShipRoomLayout.profileFor(role, Faction.TEAM_D).stream()
                    .map(room -> room.id).toList();
            assertEquals(legacyRooms, ShipRoomLayout.profileFor(role, bright.faction).stream().map(room -> room.id).toList());
            assertEquals(legacyRooms, ShipRoomLayout.profileFor(role, dark.faction).stream().map(room -> room.id).toList());
            assertEquals(ShipHullSilhouette.hullPolygon(role, 50.0, Faction.TEAM_D).npoints,
                    ShipHullSilhouette.hullPolygon(role, 50.0, bright.faction).npoints);
            assertEquals(ShipHullSilhouette.hullPolygon(role, 50.0, Faction.TEAM_D).npoints,
                    ShipHullSilhouette.hullPolygon(role, 50.0, dark.faction).npoints);
            index++;
        }
    }

    @Test
    void legacyTerritoriesSplitDeterministicallyAndSuccessorPlayerStateRoundTrips() throws Exception {
        StrategicCampaignExpansionSystem.State legacy = StrategicCampaignExpansionSystem.bootstrap(441L);
        StrategicCampaignExpansionSystem.Territory odd =
                new StrategicCampaignExpansionSystem.Territory("legacy-7", "Legacy Yellow Seven", "TEAM_D", "TEAM_D");
        odd.locationIds.add(odd.id.value());
        StrategicCampaignExpansionSystem.Territory even =
                new StrategicCampaignExpansionSystem.Territory("legacy-8", "Legacy Yellow Eight", "TEAM_D", "TEAM_D");
        even.locationIds.add(even.id.value());
        legacy.territories.add(odd);
        legacy.territories.add(even);
        StrategicCampaignExpansionSystem.State migrated = StrategicCampaignExpansionSystem.restore(
                StrategicCampaignExpansionSystem.serialize(legacy), 441L);
        assertEquals("BRIGHT_YELLOW", StrategicCampaignExpansionSystem.territory(migrated, odd.id.value()).controller);
        assertEquals("DARK_YELLOW", StrategicCampaignExpansionSystem.territory(migrated, even.id.value()).controller);

        GameContext source = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 442L, false));
        SpawnSystem.initWorld(source);
        source.player.faction = Faction.DARK_YELLOW;
        source.player.cargo = 37;
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 4);
        GameContext restored = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 442L, false));
        SpawnSystem.initWorld(restored);
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(Faction.DARK_YELLOW, restored.player.faction);
        assertEquals(37, restored.player.cargo);

        CampaignCheckpointStore.Checkpoint legacyCheckpoint = captureCheckpoint(source, 4);
        legacyCheckpoint.version = 2;
        legacyCheckpoint.sourceVersion = 2;
        legacyCheckpoint.playerFactionName = Faction.TEAM_D.name();
        GameContext migratedContext = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 442L, false));
        SpawnSystem.initWorld(migratedContext);
        assertTrue(applyCheckpoint(migratedContext, legacyCheckpoint));
        assertTrue(migratedContext.campaign.saveRecoveryMessage.contains("legacy Yellow"));
        assertTrue(CampaignSystem.mainCampaignLocations(migratedContext).stream()
                .filter(location -> location.missionIndex >= 7 && location.missionIndex <= 12)
                .noneMatch(location -> location.ownerFaction == Faction.TEAM_D));
        assertTrue(CampaignSystem.campaignForceSummaries(migratedContext).stream()
                .noneMatch(force -> force.faction == Faction.TEAM_D));
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }
}
