import app.config.GameConfig;
import app.config.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Resolves legacy single-player custom battle config into the shared mission launch model. */
public final class SinglePlayerLaunchAdapter {
    public static final String SINGLE_PLAYER_RULES_PROFILE_ID = "single-player:local";
    public static final int CUSTOM_BATTLE_REVISION = 1;

    private SinglePlayerLaunchAdapter() {}

    public static MissionLaunchSpec fromGameConfig(GameConfig config) {
        if (config == null) throw new IllegalArgumentException("Missing game config");
        if (config.mode != GameMode.CUSTOM_BATTLES) {
            throw new IllegalArgumentException("Single-player launch adapter only supports custom battles");
        }
        ArrayList<MissionSlotSpec> roster = new ArrayList<>();
        int nextSlotId = 1;
        nextSlotId = appendRoster(roster, nextSlotId, config.playerTeamId,
                config.customBattleFriendlyRoster, MissionSlotControlMode.PLAYER_OR_AI, true);
        appendRoster(roster, nextSlotId, config.customBattleEnemyTeamId,
                config.customBattleEnemyRoster, MissionSlotControlMode.AI_ONLY, false);
        MissionSlotSpec playerSlot = firstPlayerSlot(roster, config.playerTeamId);
        return new MissionLaunchSpec(
                CustomMissionCatalog.CUSTOM_BATTLE_ID,
                CUSTOM_BATTLE_REVISION,
                Math.max(0L, config.seed),
                config.worldW,
                config.worldH,
                List.copyOf(roster),
                List.of(playerSlot),
                SINGLE_PLAYER_RULES_PROFILE_ID,
                "custom_battle",
                MultiplayerRulesV1.VictoryRule.ELIMINATION);
    }

    private static int appendRoster(ArrayList<MissionSlotSpec> out,
                                    int nextSlotId,
                                    int teamId,
                                    String rosterText,
                                    MissionSlotControlMode controlMode,
                                    boolean friendly) {
        List<RosterEntry> entries = parseRoster(rosterText);
        if (entries.isEmpty() && teamId == Faction.TEAM_E.teamId()) {
            entries = localCustomRoster();
        }
        if (entries.isEmpty()) entries = defaultRoster(friendly);
        for (RosterEntry entry : entries) {
            for (int i = 0; i < entry.count(); i++) {
                out.add(new MissionSlotSpec(nextSlotId++, teamId, entry.hull(), entry.definitionRef(), controlMode, true,
                        "single-player-" + teamId + "-" + entry.hull().name().toLowerCase(Locale.ROOT) + "-" + i));
            }
        }
        return nextSlotId;
    }

    private static MissionSlotSpec firstPlayerSlot(List<MissionSlotSpec> roster, int playerTeamId) {
        for (MissionSlotSpec slot : roster) {
            if (slot.teamId() == playerTeamId && slot.controlMode() == MissionSlotControlMode.PLAYER_OR_AI) {
                return new MissionSlotSpec(slot.slotId(), slot.teamId(), slot.defaultHull(),
                        slot.definitionRef(),
                        MissionSlotControlMode.PLAYER_REQUIRED, true, slot.spawnAnchorId());
            }
        }
        return new MissionSlotSpec(1, playerTeamId, ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED, true, "single-player-default-player");
    }

    private static List<RosterEntry> parseRoster(String rosterText) {
        ArrayList<RosterEntry> out = new ArrayList<>();
        String text = rosterText == null ? "" : rosterText.trim();
        if (text.isBlank()) return out;
        String[] entries = text.split("[;,\\n\\r]+");
        for (String entry : entries) {
            String clean = entry == null ? "" : entry.trim();
            if (clean.isBlank()) continue;
            String[] fields = clean.split("[:=]", 2);
            ShipDefinitionRef ref = parseDefinitionRef(fields[0]);
            ShipRole role = ref == null ? null : ref.templateRole();
            int count = fields.length > 1 ? parseCount(fields[1]) : 1;
            if (role != null && count > 0) out.add(new RosterEntry(role, ref, count));
        }
        return out;
    }

    private static List<RosterEntry> defaultRoster(boolean friendly) {
        if (friendly) {
            return List.of(
                    RosterEntry.builtin(ShipRole.FRIGATE, 4),
                    RosterEntry.builtin(ShipRole.CIWS_CORVETTE, 2),
                    RosterEntry.builtin(ShipRole.LIGHT_CRUISER, 2),
                    RosterEntry.builtin(ShipRole.BATTLECRUISER, 1),
                    RosterEntry.builtin(ShipRole.CARRIER, 1),
                    RosterEntry.builtin(ShipRole.SUPERSHIP, 1));
        }
        return List.of(
                RosterEntry.builtin(ShipRole.FRIGATE, 6),
                RosterEntry.builtin(ShipRole.MISSILE_BOAT, 3),
                RosterEntry.builtin(ShipRole.LIGHT_CRUISER, 2),
                RosterEntry.builtin(ShipRole.BATTLESHIP, 1),
                RosterEntry.builtin(ShipRole.INTERDICTION_TITAN, 1),
                RosterEntry.builtin(ShipRole.MOTHERSHIP, 1));
    }

    private static List<RosterEntry> localCustomRoster() {
        try {
            ArrayList<RosterEntry> entries = new ArrayList<>();
            for (CustomShipDefinition definition : new CustomShipRegistry().loadAll()) {
                if (definition == null) continue;
                entries.add(new RosterEntry(definition.balanceTemplate,
                        ShipDefinitionRef.custom(definition.id, definition.balanceTemplate), 1));
            }
            return entries;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static ShipDefinitionRef parseDefinitionRef(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.regionMatches(true, 0, "custom:", 0, "custom:".length())) {
            String idAndMaybeRole = clean.substring("custom:".length()).trim();
            String[] parts = idAndMaybeRole.split(":", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                ShipRole template = parts.length > 1 ? parseRole(parts[1]) : customTemplateFor(id);
                return ShipDefinitionRef.custom(id, template == null ? ShipRole.FRIGATE : template);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        ShipRole role = parseRole(clean);
        return role == null ? null : ShipDefinitionRef.builtin(role);
    }

    private static ShipRole customTemplateFor(UUID id) {
        if (id == null) return ShipRole.FRIGATE;
        try {
            return new CustomShipRegistry().load(id).map(def -> def.balanceTemplate).orElse(ShipRole.FRIGATE);
        } catch (RuntimeException ignored) {
            return ShipRole.FRIGATE;
        }
    }

    private static ShipRole parseRole(String text) {
        try {
            return ShipRole.valueOf((text == null ? "" : text.trim()).toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int parseCount(String text) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt((text == null ? "" : text.trim()))));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private record RosterEntry(ShipRole hull, ShipDefinitionRef definitionRef, int count) {
        static RosterEntry builtin(ShipRole hull, int count) {
            return new RosterEntry(hull, ShipDefinitionRef.builtin(hull), count);
        }
    }
}
