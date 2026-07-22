import app.config.GameMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CustomMissionCatalog {
    public static final String V1_DUEL_ID = "core:v1_duel";
    public static final String HEAVY_DUEL_ID = "core:heavy_duel";
    public static final String CUSTOM_BATTLE_ID = "core:custom_battle";
    public static final String LAST_STAND_ID = "core:last_stand";
    public static final String RESOURCE_RUSH_ID = "core:resource_rush";
    public static final String FOUR_TEAM_DOMINATION_ID = "core:four_team_domination";
    public static final String SHOOTING_RANGE_ID = "debug:shooting_range";
    public static final String FLEET_SHOWCASE_ID = "showcase:fleet_showcase";
    public static final int V1_DUEL_REVISION = 1;
    public static final int HEAVY_DUEL_REVISION = 1;
    public static final int CUSTOM_BATTLE_REVISION = 1;
    public static final int LAST_STAND_REVISION = 1;
    public static final int RESOURCE_RUSH_REVISION = 1;
    public static final int FOUR_TEAM_DOMINATION_REVISION = 1;
    public static final int SHOOTING_RANGE_REVISION = 1;
    public static final int FLEET_SHOWCASE_REVISION = 1;
    public static final String V1_RULES_PROFILE_ID = MultiplayerRulesV1.RULES_PROFILE_ID;
    public static final Set<String> RESERVED_MISSION_IDS = Set.of(
            V1_DUEL_ID,
            HEAVY_DUEL_ID,
            CUSTOM_BATTLE_ID,
            LAST_STAND_ID,
            RESOURCE_RUSH_ID,
            FOUR_TEAM_DOMINATION_ID,
            SHOOTING_RANGE_ID,
            FLEET_SHOWCASE_ID);

    private static final MissionSlotSpec HOST_DUEL_SLOT = new MissionSlotSpec(
            MultiplayerRulesV1.HOST_SLOT_ID,
            Faction.ALLY.teamId(),
            ShipRole.FRIGATE,
            MissionSlotControlMode.PLAYER_REQUIRED,
            true,
            "duel-left");
    private static final MissionSlotSpec CLIENT_DUEL_SLOT = new MissionSlotSpec(
            MultiplayerRulesV1.CLIENT_SLOT_ID,
            Faction.ENEMY.teamId(),
            ShipRole.FRIGATE,
            MissionSlotControlMode.PLAYER_REQUIRED,
            true,
            "duel-right");
    private static final CustomMissionDescriptor V1_DUEL_DESCRIPTOR = new CustomMissionDescriptor(
            V1_DUEL_ID,
            V1_DUEL_REVISION,
            "V1 Duel",
            "Two opposing player ships, no AI, elimination victory.",
            Set.of(GameMode.CUSTOM_BATTLES),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate V1_DUEL_TEMPLATE = new MissionTemplate(
            3600,
            2200,
            List.of(HOST_DUEL_SLOT, CLIENT_DUEL_SLOT),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor CUSTOM_BATTLE_DESCRIPTOR = new CustomMissionDescriptor(
            CUSTOM_BATTLE_ID,
            CUSTOM_BATTLE_REVISION,
            "Custom Battles",
            "Two opposing players in the standard custom-battle multiplayer arena.",
            Set.of(GameMode.CUSTOM_BATTLES),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate CUSTOM_BATTLE_TEMPLATE = new MissionTemplate(
            5000,
            5000,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.FRIGATE, MissionSlotControlMode.PLAYER_REQUIRED, true, "custom-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.FRIGATE, MissionSlotControlMode.PLAYER_REQUIRED, true, "custom-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor LAST_STAND_DESCRIPTOR = new CustomMissionDescriptor(
            LAST_STAND_ID,
            LAST_STAND_REVISION,
            "Last Stand",
            "Two-player Last Stand multiplayer variant using host-authoritative elimination rules.",
            Set.of(GameMode.LAST_STAND),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate LAST_STAND_TEMPLATE = new MissionTemplate(
            4200,
            2800,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.BATTLESHIP, MissionSlotControlMode.PLAYER_REQUIRED, true, "last-stand-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.BATTLESHIP, MissionSlotControlMode.PLAYER_REQUIRED, true, "last-stand-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor RESOURCE_RUSH_DESCRIPTOR = new CustomMissionDescriptor(
            RESOURCE_RUSH_ID,
            RESOURCE_RUSH_REVISION,
            "Resource Rush",
            "Two-player Resource Rush multiplayer variant using V1-safe combat resolution.",
            Set.of(GameMode.RESOURCE_RUSH),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate RESOURCE_RUSH_TEMPLATE = new MissionTemplate(
            5000,
            5000,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.LIGHT_CRUISER, MissionSlotControlMode.PLAYER_REQUIRED, true, "resource-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.LIGHT_CRUISER, MissionSlotControlMode.PLAYER_REQUIRED, true, "resource-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor FOUR_TEAM_DOMINATION_DESCRIPTOR = new CustomMissionDescriptor(
            FOUR_TEAM_DOMINATION_ID,
            FOUR_TEAM_DOMINATION_REVISION,
            "4 Team Domination",
            "Two-player domination-themed multiplayer variant; full four-team objective replication is a later rules profile.",
            Set.of(GameMode.FOUR_TEAM_DOMINATION),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate FOUR_TEAM_DOMINATION_TEMPLATE = new MissionTemplate(
            6000,
            4200,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.CRUISER, MissionSlotControlMode.PLAYER_REQUIRED, true, "domination-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.CRUISER, MissionSlotControlMode.PLAYER_REQUIRED, true, "domination-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor SHOOTING_RANGE_DESCRIPTOR = new CustomMissionDescriptor(
            SHOOTING_RANGE_ID,
            SHOOTING_RANGE_REVISION,
            "Shooting Range",
            "Two-player shooting-range multiplayer variant for quick weapons checks.",
            Set.of(GameMode.SHOOTING_RANGE),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate SHOOTING_RANGE_TEMPLATE = new MissionTemplate(
            3600,
            2200,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.FRIGATE, MissionSlotControlMode.PLAYER_REQUIRED, true, "range-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.CIWS_CORVETTE, MissionSlotControlMode.PLAYER_REQUIRED, true, "range-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor FLEET_SHOWCASE_DESCRIPTOR = new CustomMissionDescriptor(
            FLEET_SHOWCASE_ID,
            FLEET_SHOWCASE_REVISION,
            "Showcase",
            "Two-player showcase multiplayer variant with large hulls and a wider arena.",
            Set.of(GameMode.SHOWCASE),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate FLEET_SHOWCASE_TEMPLATE = new MissionTemplate(
            7000,
            4200,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.SUPERSHIP, MissionSlotControlMode.PLAYER_REQUIRED, true, "showcase-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.BATTLESHIP, MissionSlotControlMode.PLAYER_REQUIRED, true, "showcase-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");
    private static final CustomMissionDescriptor HEAVY_DUEL_DESCRIPTOR = new CustomMissionDescriptor(
            HEAVY_DUEL_ID,
            HEAVY_DUEL_REVISION,
            "Heavy Duel",
            "Two opposing heavy player ships on a larger map, no AI, elimination victory.",
            Set.of(GameMode.CUSTOM_BATTLES),
            EnumSet.of(MultiplayerCapability.OPPOSING_PLAYERS));
    private static final MissionTemplate HEAVY_DUEL_TEMPLATE = new MissionTemplate(
            5200,
            3200,
            List.of(
                    new MissionSlotSpec(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(),
                            ShipRole.CRUISER, MissionSlotControlMode.PLAYER_REQUIRED, true, "duel-heavy-left"),
                    new MissionSlotSpec(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY.teamId(),
                            ShipRole.BATTLECRUISER, MissionSlotControlMode.PLAYER_REQUIRED, true, "duel-heavy-right")),
            "elimination",
            MultiplayerRulesV1.VictoryRule.ELIMINATION,
            "locked");

    private CustomMissionCatalog() {}

    public static List<CustomMissionDescriptor> multiplayerEntries(Set<MultiplayerCapability> supportedCapabilities) {
        Set<MultiplayerCapability> supported = supportedCapabilities == null ? Set.of() : supportedCapabilities;
        java.util.ArrayList<CustomMissionDescriptor> entries = new java.util.ArrayList<>();
        addIfSupported(entries, LAST_STAND_DESCRIPTOR, supported);
        addIfSupported(entries, RESOURCE_RUSH_DESCRIPTOR, supported);
        addIfSupported(entries, FOUR_TEAM_DOMINATION_DESCRIPTOR, supported);
        addIfSupported(entries, CUSTOM_BATTLE_DESCRIPTOR, supported);
        addIfSupported(entries, SHOOTING_RANGE_DESCRIPTOR, supported);
        addIfSupported(entries, FLEET_SHOWCASE_DESCRIPTOR, supported);
        addIfSupported(entries, V1_DUEL_DESCRIPTOR, supported);
        addIfSupported(entries, HEAVY_DUEL_DESCRIPTOR, supported);
        return List.copyOf(entries);
    }

    public static CustomMissionDescriptor v1DuelDescriptor() {
        return V1_DUEL_DESCRIPTOR;
    }

    public static MissionTemplate v1DuelTemplate() {
        return V1_DUEL_TEMPLATE;
    }

    public static CustomMissionDescriptor heavyDuelDescriptor() {
        return HEAVY_DUEL_DESCRIPTOR;
    }

    public static MissionTemplate heavyDuelTemplate() {
        return HEAVY_DUEL_TEMPLATE;
    }

    public static CustomMissionDescriptor descriptorFor(String missionId) {
        String clean = missionId == null ? "" : missionId.trim();
        if (V1_DUEL_ID.equals(clean)) return V1_DUEL_DESCRIPTOR;
        if (HEAVY_DUEL_ID.equals(clean)) return HEAVY_DUEL_DESCRIPTOR;
        if (CUSTOM_BATTLE_ID.equals(clean)) return CUSTOM_BATTLE_DESCRIPTOR;
        if (LAST_STAND_ID.equals(clean)) return LAST_STAND_DESCRIPTOR;
        if (RESOURCE_RUSH_ID.equals(clean)) return RESOURCE_RUSH_DESCRIPTOR;
        if (FOUR_TEAM_DOMINATION_ID.equals(clean)) return FOUR_TEAM_DOMINATION_DESCRIPTOR;
        if (SHOOTING_RANGE_ID.equals(clean)) return SHOOTING_RANGE_DESCRIPTOR;
        if (FLEET_SHOWCASE_ID.equals(clean)) return FLEET_SHOWCASE_DESCRIPTOR;
        return null;
    }

    public static MissionTemplate templateFor(String missionId) {
        String clean = missionId == null ? "" : missionId.trim();
        if (V1_DUEL_ID.equals(clean)) return V1_DUEL_TEMPLATE;
        if (HEAVY_DUEL_ID.equals(clean)) return HEAVY_DUEL_TEMPLATE;
        if (CUSTOM_BATTLE_ID.equals(clean)) return CUSTOM_BATTLE_TEMPLATE;
        if (LAST_STAND_ID.equals(clean)) return LAST_STAND_TEMPLATE;
        if (RESOURCE_RUSH_ID.equals(clean)) return RESOURCE_RUSH_TEMPLATE;
        if (FOUR_TEAM_DOMINATION_ID.equals(clean)) return FOUR_TEAM_DOMINATION_TEMPLATE;
        if (SHOOTING_RANGE_ID.equals(clean)) return SHOOTING_RANGE_TEMPLATE;
        if (FLEET_SHOWCASE_ID.equals(clean)) return FLEET_SHOWCASE_TEMPLATE;
        return V1_DUEL_TEMPLATE;
    }

    public static boolean isReservedMissionId(String missionId) {
        String clean = missionId == null ? "" : missionId.trim();
        return RESERVED_MISSION_IDS.contains(clean);
    }

    public static boolean isExperimentalMissionId(String missionId) {
        String clean = missionId == null ? "" : missionId.trim();
        return clean.startsWith("debug:") || clean.startsWith("experimental:");
    }

    public static MissionLaunchSpec resolveV1Duel(long seed, int worldW, int worldH,
                                                  ShipRole hostHull, ShipRole clientHull) {
        MissionSlotSpec host = new MissionSlotSpec(
                MultiplayerRulesV1.HOST_SLOT_ID,
                Faction.ALLY.teamId(),
                directHullOrDefault(hostHull),
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "duel-left");
        MissionSlotSpec client = new MissionSlotSpec(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                Faction.ENEMY.teamId(),
                directHullOrDefault(clientHull),
                MissionSlotControlMode.PLAYER_REQUIRED,
                true,
                "duel-right");
        return new MissionLaunchSpec(
                V1_DUEL_ID,
                V1_DUEL_REVISION,
                seed,
                worldW,
                worldH,
                List.of(host, client),
                List.of(host, client),
                V1_RULES_PROFILE_ID,
                V1_DUEL_TEMPLATE.objectiveType(),
                V1_DUEL_TEMPLATE.victoryRule());
    }

    public static MissionLaunchSpec resolveHeavyDuel(long seed) {
        return resolveHeavyDuel(seed, HEAVY_DUEL_TEMPLATE.worldW(), HEAVY_DUEL_TEMPLATE.worldH());
    }

    public static MissionLaunchSpec resolveHeavyDuel(long seed, int worldW, int worldH) {
        MissionSlotSpec host = HEAVY_DUEL_TEMPLATE.rosterTemplate().get(0);
        MissionSlotSpec client = HEAVY_DUEL_TEMPLATE.rosterTemplate().get(1);
        return new MissionLaunchSpec(
                HEAVY_DUEL_ID,
                HEAVY_DUEL_REVISION,
                seed,
                worldW,
                worldH,
                List.of(host, client),
                List.of(host, client),
                V1_RULES_PROFILE_ID,
                HEAVY_DUEL_TEMPLATE.objectiveType(),
                HEAVY_DUEL_TEMPLATE.victoryRule());
    }

    public static MissionLaunchSpec resolveMultiplayerMission(String missionId, long seed, int worldW, int worldH) {
        CustomMissionDescriptor descriptor = descriptorFor(missionId);
        if (descriptor == null) {
            return resolveV1Duel(seed, worldW, worldH, ShipRole.FRIGATE, ShipRole.FRIGATE);
        }
        MissionTemplate template = templateFor(descriptor.id());
        int resolvedWorldW = worldW > 0 ? worldW : template.worldW();
        int resolvedWorldH = worldH > 0 ? worldH : template.worldH();
        return new MissionLaunchSpec(
                descriptor.id(),
                descriptor.revision(),
                seed,
                resolvedWorldW,
                resolvedWorldH,
                template.rosterTemplate(),
                template.rosterTemplate().stream()
                        .filter(slot -> slot.controlMode() == MissionSlotControlMode.PLAYER_REQUIRED)
                        .toList(),
                V1_RULES_PROFILE_ID,
                template.objectiveType(),
                template.victoryRule());
    }

    public static Set<MultiplayerCapability> v1SupportedCapabilities() {
        return MultiplayerRulesV1.supportedCapabilities();
    }

    private static void addIfSupported(java.util.List<CustomMissionDescriptor> entries,
                                       CustomMissionDescriptor descriptor,
                                       Set<MultiplayerCapability> supportedCapabilities) {
        if (descriptor == null || entries == null || supportedCapabilities == null) return;
        if (supportedCapabilities.containsAll(descriptor.requiredMultiplayerCapabilities())) {
            entries.add(descriptor);
        }
    }

    private static ShipRole directHullOrDefault(ShipRole role) {
        if (role == null || role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return ShipRole.FRIGATE;
        return role;
    }
}
