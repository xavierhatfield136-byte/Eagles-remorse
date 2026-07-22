public final class MultiplayerHostLaunchAdapter {
    private MultiplayerHostLaunchAdapter() {}

    public static MultiplayerRulesV1.BattleSetup toBattleSetup(MissionLaunchSpec spec,
                                                               String hostName,
                                                               String clientName) {
        if (spec == null) {
            spec = CustomMissionCatalog.resolveV1Duel(System.nanoTime(), 3600, 2200,
                    ShipRole.FRIGATE, ShipRole.FRIGATE);
        }
        MultiplayerMissionValidator.requireV1(spec);
        MissionSlotSpec host = slot(spec, MultiplayerRulesV1.HOST_SLOT_ID);
        MissionSlotSpec client = slot(spec, MultiplayerRulesV1.CLIENT_SLOT_ID);
        boolean aiShips = MultiplayerRulesV1.AI_SUPPORT_RULES_PROFILE_ID.equals(spec.rulesProfileId())
                || spec.resolvedRosters().stream().anyMatch(slot -> slot != null
                && (slot.controlMode() == MissionSlotControlMode.AI_ONLY
                || slot.controlMode() == MissionSlotControlMode.PLAYER_OR_AI));
        return new MultiplayerRulesV1.BattleSetup(
                spec.seed(),
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.HOST_SLOT_ID,
                        Faction.forTeamId(host.teamId()), host.defaultHull(), hostName),
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.CLIENT_SLOT_ID,
                        Faction.forTeamId(client.teamId()), client.defaultHull(), clientName),
                MultiplayerRulesV1.VictoryRule.ELIMINATION,
                false,
                aiShips,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    private static MissionSlotSpec slot(MissionLaunchSpec spec, int slotId) {
        for (MissionSlotSpec slot : spec.playerSlots()) {
            if (slot.slotId() == slotId) return slot;
        }
        return new MissionSlotSpec(slotId, slotId == MultiplayerRulesV1.HOST_SLOT_ID
                ? Faction.ALLY.teamId()
                : Faction.ENEMY.teamId(), ShipRole.FRIGATE,
                MissionSlotControlMode.PLAYER_REQUIRED, true, "duel");
    }
}
