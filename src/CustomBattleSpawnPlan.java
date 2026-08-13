import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Pure custom-battle spawn placement plan; callers apply it to a GameContext. */
public record CustomBattleSpawnPlan(BaseSpawn friendlyBase,
                                    BaseSpawn enemyBase,
                                    ShipSpawn playerSpawn,
                                    List<ShipSpawn> rosterSpawns) {
    public CustomBattleSpawnPlan {
        rosterSpawns = rosterSpawns == null ? List.of() : List.copyOf(rosterSpawns);
    }

    public record BaseSpawn(Faction faction, double x, double y) {}

    public record ShipSpawn(ShipRole role,
                            ShipDefinitionRef definitionRef,
                            Faction faction,
                            double x,
                            double y,
                            double angle,
                            boolean playerShip) {
        public ShipSpawn(ShipRole role,
                         Faction faction,
                         double x,
                         double y,
                         double angle,
                         boolean playerShip) {
            this(role, ShipDefinitionRef.builtin(role), faction, x, y, angle, playerShip);
        }

        public ShipSpawn {
            if (role == null) role = ShipRole.FRIGATE;
            if (definitionRef == null) definitionRef = ShipDefinitionRef.builtin(role);
        }
    }

    public static CustomBattleSpawnPlan create(int worldW,
                                               int worldH,
                                               Random rng,
                                               int playerTeamId,
                                               int enemyTeamId,
                                               String friendlyRosterText,
                                               String enemyRosterText) {
        int safeWorldW = Math.max(1800, worldW);
        int safeWorldH = Math.max(1800, worldH);
        Random random = rng == null ? new Random(0L) : rng;
        int safeEnemyTeamId = enemyTeamId == playerTeamId
                ? (playerTeamId == 0 ? 1 : 0)
                : enemyTeamId;

        Faction playerFaction = playerFactionForTeamId(playerTeamId);
        Faction friendlyFaction = Faction.forTeamId(playerTeamId);
        Faction enemyFaction = Faction.forTeamId(safeEnemyTeamId);
        double[] friendlyBasePos = edgeBasePosition(safeWorldW, safeWorldH, true);
        double[] enemyBasePos = edgeBasePosition(safeWorldW, safeWorldH, false);
        BaseSpawn friendlyBase = new BaseSpawn(friendlyFaction, friendlyBasePos[0], friendlyBasePos[1]);
        BaseSpawn enemyBase = new BaseSpawn(enemyFaction, enemyBasePos[0], enemyBasePos[1]);

        double[] playerPos = inwardSpawnNearBase(safeWorldW, safeWorldH, friendlyBase.x(), friendlyBase.y());
        ShipSpawn playerSpawn = new ShipSpawn(ShipRole.MOTHERSHIP, ShipDefinitionRef.builtin(ShipRole.MOTHERSHIP), playerFaction,
                playerPos[0], playerPos[1], 0.0, true);

        LinkedHashMap<ShipRole, Integer> friendlyRoster = parseRoster(friendlyRosterText);
        LinkedHashMap<ShipRole, Integer> enemyRoster = parseRoster(enemyRosterText);
        if (friendlyRoster.isEmpty()) friendlyRoster = defaultRoster(true);
        if (enemyRoster.isEmpty()) enemyRoster = defaultRoster(false);

        ArrayList<ShipSpawn> rosterSpawns = new ArrayList<>();
        addRosterSpawns(rosterSpawns, safeWorldW, safeWorldH, random,
                friendlyFaction, friendlyBase, friendlyRoster, true);
        addRosterSpawns(rosterSpawns, safeWorldW, safeWorldH, random,
                enemyFaction, enemyBase, enemyRoster, false);
        return new CustomBattleSpawnPlan(friendlyBase, enemyBase, playerSpawn, rosterSpawns);
    }

    public static CustomBattleSpawnPlan create(MissionLaunchSpec spec, Random rng) {
        if (spec == null) {
            return create(1800, 1800, rng, 0, 1, "", "");
        }
        int playerTeamId = playerTeamId(spec);
        int enemyTeamId = enemyTeamId(spec, playerTeamId);
        int safeWorldW = Math.max(1800, spec.worldW());
        int safeWorldH = Math.max(1800, spec.worldH());
        Random random = rng == null ? new Random(0L) : rng;
        int safeEnemyTeamId = enemyTeamId == playerTeamId
                ? (playerTeamId == 0 ? 1 : 0)
                : enemyTeamId;

        Faction playerFaction = playerFactionForTeamId(playerTeamId);
        Faction friendlyFaction = Faction.forTeamId(playerTeamId);
        Faction enemyFaction = Faction.forTeamId(safeEnemyTeamId);
        double[] friendlyBasePos = edgeBasePosition(safeWorldW, safeWorldH, true);
        double[] enemyBasePos = edgeBasePosition(safeWorldW, safeWorldH, false);
        BaseSpawn friendlyBase = new BaseSpawn(friendlyFaction, friendlyBasePos[0], friendlyBasePos[1]);
        BaseSpawn enemyBase = new BaseSpawn(enemyFaction, enemyBasePos[0], enemyBasePos[1]);

        MissionSlotSpec playerSlot = firstPlayerSlot(spec, playerTeamId);
        ShipDefinitionRef playerRef = playerSlot == null ? ShipDefinitionRef.builtin(ShipRole.MOTHERSHIP) : playerSlot.definitionRef();
        ShipRole playerRole = playerRef != null && playerRef.isCustom()
                ? playerRef.templateRole()
                : ShipRole.MOTHERSHIP;
        double[] playerPos = inwardSpawnNearBase(safeWorldW, safeWorldH, friendlyBase.x(), friendlyBase.y());
        ShipSpawn playerSpawn = new ShipSpawn(playerRole, playerRef, playerFaction,
                playerPos[0], playerPos[1], 0.0, true);

        ArrayList<MissionSlotSpec> friendlySlots = rosterSlotsForTeam(spec, playerTeamId);
        ArrayList<MissionSlotSpec> enemySlots = rosterSlotsForTeam(spec, safeEnemyTeamId);
        if (friendlySlots.isEmpty()) friendlySlots = slotsFromRoster(playerTeamId, defaultRoster(true), 10_000);
        if (enemySlots.isEmpty()) enemySlots = slotsFromRoster(safeEnemyTeamId, defaultRoster(false), 20_000);

        ArrayList<ShipSpawn> rosterSpawns = new ArrayList<>();
        addSlotSpawns(rosterSpawns, safeWorldW, safeWorldH, random,
                friendlyFaction, friendlyBase, friendlySlots, true);
        addSlotSpawns(rosterSpawns, safeWorldW, safeWorldH, random,
                enemyFaction, enemyBase, enemySlots, false);
        return new CustomBattleSpawnPlan(friendlyBase, enemyBase, playerSpawn, rosterSpawns);
    }

    private static void addRosterSpawns(ArrayList<ShipSpawn> out,
                                        int worldW,
                                        int worldH,
                                        Random rng,
                                        Faction faction,
                                        BaseSpawn base,
                                        LinkedHashMap<ShipRole, Integer> roster,
                                        boolean playerSide) {
        ArrayList<ShipRole> roles = new ArrayList<>();
        for (Map.Entry<ShipRole, Integer> entry : roster.entrySet()) {
            ShipRole role = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            for (int i = 0; i < count; i++) roles.add(role);
        }
        roles.sort((a, b) -> Integer.compare(spawnWeight(b), spawnWeight(a)));
        if (roles.isEmpty()) return;

        double centerX = worldW * 0.5;
        double centerY = worldH * 0.5;
        double dx = centerX - base.x();
        double dy = centerY - base.y();
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) len = 1.0;
        double nx = dx / len;
        double ny = dy / len;
        double tx = -ny;
        double ty = nx;

        int columns = Math.max(4, (int) Math.ceil(Math.sqrt(roles.size())));
        for (int i = 0; i < roles.size(); i++) {
            ShipRole role = roles.get(i);
            int row = i / columns;
            int col = i % columns;
            double lane = col - (columns - 1) * 0.5;
            double size = spacingScale(role);
            double forward = 340.0 + row * (175.0 * size);
            double lateral = lane * (150.0 * size);
            double jitterX = (rng.nextDouble() - 0.5) * 26.0;
            double jitterY = (rng.nextDouble() - 0.5) * 26.0;
            double x = GameMath.clamp(base.x() + nx * forward + tx * lateral + jitterX, 20.0, worldW - 20.0);
            double y = GameMath.clamp(base.y() + ny * forward + ty * lateral + jitterY, 20.0, worldH - 20.0);
            double angle = playerSide
                    ? Math.atan2(centerY - y, centerX - x)
                    : Math.atan2(base.y() - y, base.x() - x);
            out.add(new ShipSpawn(role, ShipDefinitionRef.builtin(role), faction, x, y, angle, false));
        }
    }

    private static void addSlotSpawns(ArrayList<ShipSpawn> out,
                                      int worldW,
                                      int worldH,
                                      Random rng,
                                      Faction faction,
                                      BaseSpawn base,
                                      List<MissionSlotSpec> slots,
                                      boolean playerSide) {
        ArrayList<MissionSlotSpec> ordered = new ArrayList<>(slots == null ? List.of() : slots);
        ordered.sort((a, b) -> Integer.compare(spawnWeight(b.defaultHull()), spawnWeight(a.defaultHull())));
        if (ordered.isEmpty()) return;

        double centerX = worldW * 0.5;
        double centerY = worldH * 0.5;
        double dx = centerX - base.x();
        double dy = centerY - base.y();
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) len = 1.0;
        double nx = dx / len;
        double ny = dy / len;
        double tx = -ny;
        double ty = nx;

        int columns = Math.max(4, (int) Math.ceil(Math.sqrt(ordered.size())));
        for (int i = 0; i < ordered.size(); i++) {
            MissionSlotSpec slot = ordered.get(i);
            ShipRole role = slot.defaultHull();
            int row = i / columns;
            int col = i % columns;
            double lane = col - (columns - 1) * 0.5;
            double size = spacingScale(role);
            double forward = 340.0 + row * (175.0 * size);
            double lateral = lane * (150.0 * size);
            double jitterX = (rng.nextDouble() - 0.5) * 26.0;
            double jitterY = (rng.nextDouble() - 0.5) * 26.0;
            double x = GameMath.clamp(base.x() + nx * forward + tx * lateral + jitterX, 20.0, worldW - 20.0);
            double y = GameMath.clamp(base.y() + ny * forward + ty * lateral + jitterY, 20.0, worldH - 20.0);
            double angle = playerSide
                    ? Math.atan2(centerY - y, centerX - x)
                    : Math.atan2(base.y() - y, base.x() - x);
            out.add(new ShipSpawn(role, slot.definitionRef(), faction, x, y, angle, false));
        }
    }

    private static double[] edgeBasePosition(int worldW, int worldH, boolean ally) {
        double minDim = Math.min(worldW, worldH);
        double margin = Math.max(140.0, Math.min(minDim * 0.085, 560.0));
        double laneInset = Math.max(170.0, Math.min(worldH * 0.22, 640.0));
        double x = ally ? margin : (worldW - margin);
        double y = ally ? (worldH - laneInset) : laneInset;
        return new double[]{x, y};
    }

    private static double[] inwardSpawnNearBase(int worldW, int worldH, double baseX, double baseY) {
        double cx = worldW * 0.5;
        double cy = worldH * 0.5;
        double dx = cx - baseX;
        double dy = cy - baseY;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-9) len = 1.0;
        double ux = dx / len;
        double uy = dy / len;
        double forward = Math.max(220.0, worldW * 0.08);
        double lateral = Math.max(120.0, worldH * 0.04);
        double px = baseX + ux * forward - uy * lateral * 0.35;
        double py = baseY + uy * forward + ux * lateral * 0.35;
        return new double[]{
                GameMath.clamp(px, 40.0, worldW - 40.0),
                GameMath.clamp(py, 40.0, worldH - 40.0)};
    }

    private static LinkedHashMap<ShipRole, Integer> parseRoster(String encoded) {
        LinkedHashMap<ShipRole, Integer> roster = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return roster;
        String[] entries = encoded.split("[;,\\n\\r]+");
        for (String rawEntry : entries) {
            if (rawEntry == null) continue;
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("[:=]", 2);
            if (parts.length != 2) continue;
            String roleId = parts[0].trim().toUpperCase(Locale.US);
            String countText = parts[1].trim();
            if (roleId.isEmpty() || countText.isEmpty()) continue;
            try {
                ShipRole role = ShipRole.valueOf(roleId);
                int count = Integer.parseInt(countText);
                if (count > 0) roster.merge(role, count, Integer::sum);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return roster;
    }

    private static LinkedHashMap<ShipRole, Integer> defaultRoster(boolean friendly) {
        LinkedHashMap<ShipRole, Integer> roster = new LinkedHashMap<>();
        if (friendly) {
            roster.put(ShipRole.FRIGATE, 4);
            roster.put(ShipRole.CIWS_CORVETTE, 2);
            roster.put(ShipRole.LIGHT_CRUISER, 2);
            roster.put(ShipRole.BATTLECRUISER, 1);
            roster.put(ShipRole.CARRIER, 1);
            roster.put(ShipRole.SUPERSHIP, 1);
        } else {
            roster.put(ShipRole.FRIGATE, 6);
            roster.put(ShipRole.MISSILE_BOAT, 3);
            roster.put(ShipRole.LIGHT_CRUISER, 2);
            roster.put(ShipRole.BATTLESHIP, 1);
            roster.put(ShipRole.INTERDICTION_TITAN, 1);
            roster.put(ShipRole.MOTHERSHIP, 1);
        }
        return roster;
    }

    private static int playerTeamId(MissionLaunchSpec spec) {
        for (MissionSlotSpec slot : spec.playerSlots()) {
            if (slot != null) return slot.teamId();
        }
        for (MissionSlotSpec slot : spec.resolvedRosters()) {
            if (slot != null && slot.controlMode() == MissionSlotControlMode.PLAYER_OR_AI) return slot.teamId();
        }
        return 0;
    }

    private static int enemyTeamId(MissionLaunchSpec spec, int playerTeamId) {
        for (MissionSlotSpec slot : spec.resolvedRosters()) {
            if (slot != null && slot.teamId() != playerTeamId) return slot.teamId();
        }
        return playerTeamId == 0 ? 1 : 0;
    }

    private static MissionSlotSpec firstPlayerSlot(MissionLaunchSpec spec, int playerTeamId) {
        for (MissionSlotSpec slot : spec.playerSlots()) {
            if (slot != null && slot.teamId() == playerTeamId) return slot;
        }
        for (MissionSlotSpec slot : spec.resolvedRosters()) {
            if (slot != null && slot.teamId() == playerTeamId
                    && (slot.controlMode() == MissionSlotControlMode.PLAYER_REQUIRED
                    || slot.controlMode() == MissionSlotControlMode.PLAYER_OR_AI)) {
                return slot;
            }
        }
        return null;
    }

    private static ArrayList<MissionSlotSpec> rosterSlotsForTeam(MissionLaunchSpec spec, int teamId) {
        ArrayList<MissionSlotSpec> roster = new ArrayList<>();
        for (MissionSlotSpec slot : spec.resolvedRosters()) {
            if (slot == null || slot.teamId() != teamId) continue;
            roster.add(slot);
        }
        return roster;
    }

    private static ArrayList<MissionSlotSpec> slotsFromRoster(int teamId, LinkedHashMap<ShipRole, Integer> roster, int baseSlotId) {
        ArrayList<MissionSlotSpec> slots = new ArrayList<>();
        int nextSlotId = Math.max(1, baseSlotId);
        for (Map.Entry<ShipRole, Integer> entry : roster.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            for (int i = 0; i < entry.getValue(); i++) {
                slots.add(new MissionSlotSpec(nextSlotId++, teamId, entry.getKey(),
                        MissionSlotControlMode.AI_ONLY, true,
                        "default-" + teamId + "-" + entry.getKey().name().toLowerCase(Locale.ROOT) + "-" + i));
            }
        }
        return slots;
    }

    private static int spawnWeight(ShipRole role) {
        if (role == null) return 0;
        return SpawnSystem.requiredHangarTierForRole(role) * 100 + Math.max(0, roleMaxCountBias(role));
    }

    private static int roleMaxCountBias(ShipRole role) {
        if (role == null) return 0;
        return switch (role) {
            case MOTHERSHIP, BASE, MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, FLEET_TELEPORTER_TITAN,
                    SHIELD_BASTION_TITAN, ARTILLERY_TITAN, INTERDICTION_TITAN,
                    VANGUARD_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    CARRIER_SUPPORT_TITAN, BULWARK_TITAN, TRANSPORT_TITAN -> 12;
            case SUPERSHIP, DREADNOUGHT, BATTLESHIP, BATTLECRUISER -> 8;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER, CARRIER, DRONE_CARRIER -> 5;
            default -> 1;
        };
    }

    private static double spacingScale(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case BASE, MOTHERSHIP, MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, FLEET_TELEPORTER_TITAN,
                    SHIELD_BASTION_TITAN, ARTILLERY_TITAN, INTERDICTION_TITAN,
                    VANGUARD_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    CARRIER_SUPPORT_TITAN, BULWARK_TITAN, TRANSPORT_TITAN -> 1.55;
            case SUPERSHIP, DREADNOUGHT, BATTLESHIP, BATTLECRUISER -> 1.25;
            case CRUISER, MEDIUM_CRUISER, LIGHT_CRUISER, CARRIER, DRONE_CARRIER -> 1.1;
            default -> 1.0;
        };
    }

    private static Faction playerFactionForTeamId(int teamId) {
        return teamId == 0 ? Faction.PLAYER : Faction.forTeamId(teamId);
    }
}
