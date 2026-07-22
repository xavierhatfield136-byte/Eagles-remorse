import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Explicit UTF-8 protocol serialization helpers for V1 snapshots and reliable events. */
public final class MultiplayerSerializationV1 {
    private MultiplayerSerializationV1() {}

    public static byte[] encodeSnapshot(MultiplayerBattleSnapshot snapshot) {
        if (snapshot == null) snapshot = new MultiplayerBattleSnapshot(0L, List.of(), List.of());
        StringBuilder out = new StringBuilder();
        out.append("SNAP3|").append(snapshot.hostTick())
                .append('|').append(snapshot.lastProcessedInputSequence());
        out.append('|').append(snapshot.ships().size());
        for (MultiplayerBattleSnapshot.ShipSnapshot ship : snapshot.ships()) {
            out.append('|')
                    .append(ship.shipId()).append(',')
                    .append(ship.role().name()).append(',')
                    .append(ship.faction().name()).append(',')
                    .append(ship.x()).append(',')
                    .append(ship.y()).append(',')
                    .append(ship.vx()).append(',')
                    .append(ship.vy()).append(',')
                    .append(ship.angle()).append(',')
                    .append(ship.hp()).append(',')
                    .append(ship.shield()).append(',')
                    .append(ship.alive());
        }
        out.append('|').append(snapshot.slots().size());
        for (MultiplayerBattleSnapshot.SlotSnapshot slot : snapshot.slots()) {
            out.append('|')
                    .append(slot.slotId()).append(',')
                    .append(slot.teamId()).append(',')
                    .append(slot.controlledShipId()).append(',')
                    .append(slot.role().name()).append(',')
                    .append(slot.connectionState().name()).append(',')
                    .append(encodeText(slot.displayName()));
        }
        MultiplayerBattleSnapshot.ObjectiveSummarySnapshot objective = snapshot.objectiveSummary();
        out.append('|')
                .append(encodeText(objective.objectiveTypeId())).append(',')
                .append(objective.active()).append(',')
                .append(objective.complete()).append(',')
                .append(objective.owningTeamId()).append(',')
                .append(objective.progress()).append(',')
                .append(encodeText(objective.summary()));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static MultiplayerBattleSnapshot decodeSnapshot(byte[] payload) {
        validatePayload(payload);
        String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length < 4
                || (!"SNAP".equals(parts[0]) && !"SNAP2".equals(parts[0]) && !"SNAP3".equals(parts[0]))) {
            throw new IllegalArgumentException("Malformed multiplayer snapshot payload");
        }
        int index = 1;
        long hostTick = Long.parseLong(parts[index++]);
        long lastProcessedInputSequence = 0L;
        if ("SNAP2".equals(parts[0]) || "SNAP3".equals(parts[0])) {
            lastProcessedInputSequence = Long.parseLong(parts[index++]);
        }
        int shipCount = Integer.parseInt(parts[index++]);
        ArrayList<MultiplayerBattleSnapshot.ShipSnapshot> ships = new ArrayList<>();
        for (int i = 0; i < shipCount; i++) {
            String[] fields = parts[index++].split(",", -1);
            if (fields.length != 11) throw new IllegalArgumentException("Malformed ship snapshot");
            ships.add(new MultiplayerBattleSnapshot.ShipSnapshot(
                    Integer.parseInt(fields[0]),
                    ShipRole.valueOf(fields[1]),
                    Faction.valueOf(fields[2]),
                    Double.parseDouble(fields[3]),
                    Double.parseDouble(fields[4]),
                    Double.parseDouble(fields[5]),
                    Double.parseDouble(fields[6]),
                    Double.parseDouble(fields[7]),
                    Integer.parseInt(fields[8]),
                    Double.parseDouble(fields[9]),
                    Boolean.parseBoolean(fields[10])));
        }
        int slotCount = Integer.parseInt(parts[index++]);
        ArrayList<MultiplayerBattleSnapshot.SlotSnapshot> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            String[] fields = parts[index++].split(",", -1);
            if (fields.length != 6) throw new IllegalArgumentException("Malformed slot snapshot");
            slots.add(new MultiplayerBattleSnapshot.SlotSnapshot(
                    Integer.parseInt(fields[0]),
                    Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]),
                    MultiplayerRulesV1.PlayerRole.valueOf(fields[3]),
                    MultiplayerRulesV1.ConnectionState.valueOf(fields[4]),
                    decodeText(fields[5])));
        }
        MultiplayerBattleSnapshot.ObjectiveSummarySnapshot objective =
                MultiplayerBattleSnapshot.ObjectiveSummarySnapshot.none();
        if ("SNAP3".equals(parts[0])) {
            String[] fields = parts[index++].split(",", -1);
            if (fields.length != 6) throw new IllegalArgumentException("Malformed objective summary snapshot");
            objective = new MultiplayerBattleSnapshot.ObjectiveSummarySnapshot(
                    decodeText(fields[0]),
                    Boolean.parseBoolean(fields[1]),
                    Boolean.parseBoolean(fields[2]),
                    Integer.parseInt(fields[3]),
                    Double.parseDouble(fields[4]),
                    decodeText(fields[5]));
        }
        if (index != parts.length) throw new IllegalArgumentException("Trailing snapshot payload fields");
        return new MultiplayerBattleSnapshot(hostTick, lastProcessedInputSequence, ships, slots, objective);
    }

    public static byte[] encodeEvent(MultiplayerReplicationV1.AuthoritativeEvent event) {
        if (event == null) {
            event = new MultiplayerReplicationV1.AuthoritativeEvent(
                    MultiplayerReplicationV1.EventType.HIT_CONFIRMED,
                    null, 0L, 0L, 0, 0, "");
        }
        MultiplayerEntityIdAllocator.NetworkEntityId id = event.entityId();
        String entity = id == null ? "0,0" : id.index() + "," + id.generation();
        String out = "EVT|"
                + event.type().name() + '|'
                + entity + '|'
                + event.eventSequence() + '|'
                + event.hostTick() + '|'
                + event.sourceSlotId() + '|'
                + event.targetSlotId() + '|'
                + encodeText(event.detail());
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static MultiplayerReplicationV1.AuthoritativeEvent decodeEvent(byte[] payload) {
        validatePayload(payload);
        String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length != 8 || !"EVT".equals(parts[0])) {
            throw new IllegalArgumentException("Malformed multiplayer event payload");
        }
        String[] entityFields = parts[2].split(",", -1);
        if (entityFields.length != 2) throw new IllegalArgumentException("Malformed event entity id");
        int index = Integer.parseInt(entityFields[0]);
        int generation = Integer.parseInt(entityFields[1]);
        MultiplayerEntityIdAllocator.NetworkEntityId id = index <= 0 || generation <= 0
                ? null
                : new MultiplayerEntityIdAllocator.NetworkEntityId(index, generation);
        return new MultiplayerReplicationV1.AuthoritativeEvent(
                MultiplayerReplicationV1.EventType.valueOf(parts[1]),
                id,
                Long.parseLong(parts[3]),
                Long.parseLong(parts[4]),
                Integer.parseInt(parts[5]),
                Integer.parseInt(parts[6]),
                decodeText(parts[7]));
    }

    private static void validatePayload(byte[] payload) {
        MultiplayerProtocolV1.ProtocolValidation validation =
                MultiplayerProtocolV1.validatePayloadBytes(payload);
        if (!validation.accepted()) throw new IllegalArgumentException(validation.reason());
    }

    private static String encodeText(String text) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String text) {
        if (text == null || text.isBlank()) return "";
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
