import app.support.AppInfo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Phase 5 protocol and replication contract for V1 custom-battle multiplayer. */
public final class MultiplayerProtocolV1 {
    public static final int PROTOCOL_VERSION = 1;
    public static final int SNAPSHOT_RATE_HZ = 20;
    public static final int MAX_MESSAGE_BYTES = 32 * 1024;
    public static final boolean USE_FULL_SNAPSHOTS_IN_V1 = true;
    public static final boolean DELTA_SNAPSHOTS_DEFERRED = true;

    private static final byte JAVA_SERIALIZATION_MAGIC_0 = (byte) 0xAC;
    private static final byte JAVA_SERIALIZATION_MAGIC_1 = (byte) 0xED;

    private MultiplayerProtocolV1() {}

    public enum MessageFamily {
        HANDSHAKE,
        LOBBY_CONTROL,
        CLIENT_INPUT,
        RELIABLE_EVENT,
        SNAPSHOT,
        ACKNOWLEDGEMENT,
        HEARTBEAT,
        ERROR,
        DISCONNECT
    }

    public enum Delivery {
        RELIABLE_ORDERED,
        SEQUENCED_REPLACEABLE,
        UNRELIABLE
    }

    public enum MessageKind {
        HELLO(MessageFamily.HANDSHAKE, Delivery.RELIABLE_ORDERED),
        HELLO_ACCEPTED(MessageFamily.HANDSHAKE, Delivery.RELIABLE_ORDERED),
        HELLO_REJECTED(MessageFamily.HANDSHAKE, Delivery.RELIABLE_ORDERED),
        LOBBY_STATE(MessageFamily.LOBBY_CONTROL, Delivery.RELIABLE_ORDERED),
        LOBBY_COMMAND(MessageFamily.LOBBY_CONTROL, Delivery.RELIABLE_ORDERED),
        MATCH_START(MessageFamily.LOBBY_CONTROL, Delivery.RELIABLE_ORDERED),
        CLIENT_INPUT_FRAME(MessageFamily.CLIENT_INPUT, Delivery.SEQUENCED_REPLACEABLE),
        DISCRETE_COMMAND(MessageFamily.CLIENT_INPUT, Delivery.RELIABLE_ORDERED),
        SPAWN(MessageFamily.RELIABLE_EVENT, Delivery.RELIABLE_ORDERED),
        DESPAWN(MessageFamily.RELIABLE_EVENT, Delivery.RELIABLE_ORDERED),
        DEATH(MessageFamily.RELIABLE_EVENT, Delivery.RELIABLE_ORDERED),
        AUTHORITATIVE_EVENT(MessageFamily.RELIABLE_EVENT, Delivery.RELIABLE_ORDERED),
        MATCH_END(MessageFamily.RELIABLE_EVENT, Delivery.RELIABLE_ORDERED),
        ERROR_MESSAGE(MessageFamily.ERROR, Delivery.RELIABLE_ORDERED),
        FULL_SNAPSHOT(MessageFamily.SNAPSHOT, Delivery.SEQUENCED_REPLACEABLE),
        INPUT_ACK(MessageFamily.ACKNOWLEDGEMENT, Delivery.UNRELIABLE),
        HEARTBEAT(MessageFamily.HEARTBEAT, Delivery.UNRELIABLE),
        DISCONNECT_NOTICE(MessageFamily.DISCONNECT, Delivery.RELIABLE_ORDERED);

        private final MessageFamily family;
        private final Delivery delivery;

        MessageKind(MessageFamily family, Delivery delivery) {
            this.family = family;
            this.delivery = delivery;
        }

        public MessageFamily family() {
            return family;
        }

        public Delivery delivery() {
            return delivery;
        }

        public boolean reliableOrdered() {
            return delivery == Delivery.RELIABLE_ORDERED;
        }

        public boolean sequencedReplaceable() {
            return delivery == Delivery.SEQUENCED_REPLACEABLE;
        }
    }

    public enum ReliableEventType {
        LOBBY_STATE_CHANGED,
        MATCH_CONFIGURATION_LOCKED,
        SHIP_SPAWNED,
        SHIP_DESPAWNED,
        SHIP_DESTROYED,
        WEAPON_FIRED,
        HIT_CONFIRMED,
        EXPLOSION_OCCURRED,
        OBJECTIVE_COMPLETED,
        PLAYER_DISCONNECTED,
        VICTORY_DECLARED,
        CONTROL_OWNERSHIP_CHANGED,
        ERROR
    }

    public enum MalformedMessageAction {
        DROP_AND_LOG,
        DISCONNECT_PEER
    }

    public record ContentManifest(String rulesHash,
                                  String hullDefinitionsHash,
                                  String weaponsHash,
                                  String abilitiesHash,
                                  String arenaHash,
                                  String enabledModsHash,
                                  String requiredAssetsHash) {
        public ContentManifest {
            rulesHash = normalizeHash(rulesHash);
            hullDefinitionsHash = normalizeHash(hullDefinitionsHash);
            weaponsHash = normalizeHash(weaponsHash);
            abilitiesHash = normalizeHash(abilitiesHash);
            arenaHash = normalizeHash(arenaHash);
            enabledModsHash = normalizeHash(enabledModsHash);
            requiredAssetsHash = normalizeHash(requiredAssetsHash);
        }
    }

    public record CompatibilityFingerprint(int protocolVersion,
                                          String gameBuild,
                                          ContentManifest manifest) {
        public CompatibilityFingerprint {
            gameBuild = normalizeBuild(gameBuild);
            if (manifest == null) manifest = defaultContentManifest();
        }
    }

    public record CompatibilityResult(boolean accepted, String reason) {
        public CompatibilityResult {
            reason = (reason == null || reason.isBlank())
                    ? (accepted ? "Compatible" : "Incompatible multiplayer build")
                    : reason.trim();
        }
    }

    public record Envelope(MessageKind kind,
                           long sequence,
                           long hostTick,
                           int payloadBytes) {
        public Envelope {
            if (kind == null) kind = MessageKind.ERROR_MESSAGE;
            sequence = Math.max(0L, sequence);
            hostTick = Math.max(0L, hostTick);
            payloadBytes = Math.max(0, payloadBytes);
        }
    }

    public record SnapshotHeader(long snapshotSequence,
                                 long hostTick,
                                 long lastProcessedInputSequence) {
        public SnapshotHeader {
            snapshotSequence = Math.max(0L, snapshotSequence);
            hostTick = Math.max(0L, hostTick);
            lastProcessedInputSequence = Math.max(0L, lastProcessedInputSequence);
        }
    }

    public record InputAck(int slotId, long inputSequence, long authoritativeTick) {
        public InputAck {
            slotId = Math.max(0, slotId);
            inputSequence = Math.max(0L, inputSequence);
            authoritativeTick = Math.max(0L, authoritativeTick);
        }
    }

    public record ProtocolValidation(boolean accepted,
                                     String reason,
                                     MalformedMessageAction action) {
        public ProtocolValidation {
            reason = (reason == null || reason.isBlank())
                    ? (accepted ? "Accepted" : "Malformed multiplayer message")
                    : reason.trim();
            if (action == null) action = accepted
                    ? MalformedMessageAction.DROP_AND_LOG
                    : MalformedMessageAction.DISCONNECT_PEER;
        }
    }

    public static CompatibilityFingerprint localFingerprint() {
        return new CompatibilityFingerprint(PROTOCOL_VERSION, AppInfo.VERSION, defaultContentManifest());
    }

    public static ContentManifest defaultContentManifest() {
        return new ContentManifest(
                stableHash("rules:v1:duel:no-ai:no-respawn:no-reconnect:no-pause"),
                stableHash("hulls:" + Arrays.toString(ShipRole.values())),
                stableHash("weapons:v1:authoritative-host"),
                stableHash("abilities:v1:direct-ship-only"),
                stableHash("arena:v1:single-fixed-custom-battle"),
                stableHash("mods:none"),
                stableHash("assets:v1:required-runtime-assets"));
    }

    public static CompatibilityResult validateCompatibility(CompatibilityFingerprint host,
                                                            CompatibilityFingerprint client) {
        if (host == null) return new CompatibilityResult(false, "Missing host compatibility fingerprint");
        if (client == null) return new CompatibilityResult(false, "Missing client compatibility fingerprint");
        if (host.protocolVersion() != client.protocolVersion()) {
            return new CompatibilityResult(false,
                    "Protocol mismatch: host " + host.protocolVersion() + ", client " + client.protocolVersion());
        }
        if (!Objects.equals(host.gameBuild(), client.gameBuild())) {
            return new CompatibilityResult(false,
                    "Game build mismatch: host " + host.gameBuild() + ", client " + client.gameBuild());
        }
        if (!Objects.equals(host.manifest(), client.manifest())) {
            return new CompatibilityResult(false, "Multiplayer content manifest mismatch");
        }
        return new CompatibilityResult(true, "Compatible");
    }

    public static ProtocolValidation validateEnvelope(Envelope envelope) {
        if (envelope == null) {
            return new ProtocolValidation(false, "Missing multiplayer message envelope",
                    MalformedMessageAction.DISCONNECT_PEER);
        }
        if (envelope.payloadBytes() > MAX_MESSAGE_BYTES) {
            return new ProtocolValidation(false, "Multiplayer message exceeds size limit",
                    MalformedMessageAction.DISCONNECT_PEER);
        }
        return new ProtocolValidation(true, "Accepted", MalformedMessageAction.DROP_AND_LOG);
    }

    public static ProtocolValidation validatePayloadBytes(byte[] payload) {
        if (payload == null) {
            return new ProtocolValidation(false, "Missing multiplayer message payload",
                    MalformedMessageAction.DROP_AND_LOG);
        }
        if (payload.length > MAX_MESSAGE_BYTES) {
            return new ProtocolValidation(false, "Multiplayer payload exceeds size limit",
                    MalformedMessageAction.DISCONNECT_PEER);
        }
        if (looksLikeJavaObjectStream(payload)) {
            return new ProtocolValidation(false, "Java object serialization is not allowed for multiplayer messages",
                    MalformedMessageAction.DISCONNECT_PEER);
        }
        return new ProtocolValidation(true, "Accepted", MalformedMessageAction.DROP_AND_LOG);
    }

    public static boolean acceptsEntityUpdate(MultiplayerEntityIdAllocator allocator,
                                              MultiplayerEntityIdAllocator.NetworkEntityId id) {
        return allocator != null && allocator.acceptsUpdate(id);
    }

    public static String stableHash(String value) {
        String normalized = (value == null) ? "" : value;
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return Long.toUnsignedString(hash, 16);
    }

    private static boolean looksLikeJavaObjectStream(byte[] payload) {
        return payload.length >= 4
                && payload[0] == JAVA_SERIALIZATION_MAGIC_0
                && payload[1] == JAVA_SERIALIZATION_MAGIC_1
                && payload[2] == 0x00
                && payload[3] == 0x05;
    }

    private static String normalizeHash(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? stableHash("") : trimmed;
    }

    private static String normalizeBuild(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? "dev" : trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
