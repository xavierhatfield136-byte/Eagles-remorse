import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerProtocolV1Test {

    @Test
    void localCompatibilityFingerprintAcceptsExactMatch() {
        MultiplayerProtocolV1.CompatibilityFingerprint host = MultiplayerProtocolV1.localFingerprint();
        MultiplayerProtocolV1.CompatibilityFingerprint client = MultiplayerProtocolV1.localFingerprint();

        MultiplayerProtocolV1.CompatibilityResult result =
                MultiplayerProtocolV1.validateCompatibility(host, client);

        assertTrue(result.accepted());
        assertEquals("Compatible", result.reason());
    }

    @Test
    void compatibilityRejectsProtocolBuildAndManifestMismatch() {
        MultiplayerProtocolV1.CompatibilityFingerprint host =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION, "host-build",
                        MultiplayerProtocolV1.defaultContentManifest());
        MultiplayerProtocolV1.CompatibilityFingerprint oldProtocol =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION + 1, "host-build",
                        MultiplayerProtocolV1.defaultContentManifest());
        MultiplayerProtocolV1.CompatibilityFingerprint wrongBuild =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION, "client-build",
                        MultiplayerProtocolV1.defaultContentManifest());
        MultiplayerProtocolV1.CompatibilityFingerprint wrongManifest =
                new MultiplayerProtocolV1.CompatibilityFingerprint(
                        MultiplayerProtocolV1.PROTOCOL_VERSION, "host-build",
                        new MultiplayerProtocolV1.ContentManifest(
                                "different-rules", "hulls", "weapons", "abilities",
                                "arena", "mods", "assets"));

        assertFalse(MultiplayerProtocolV1.validateCompatibility(host, oldProtocol).accepted());
        assertTrue(MultiplayerProtocolV1.validateCompatibility(host, oldProtocol).reason()
                .contains("Protocol mismatch"));
        assertFalse(MultiplayerProtocolV1.validateCompatibility(host, wrongBuild).accepted());
        assertTrue(MultiplayerProtocolV1.validateCompatibility(host, wrongBuild).reason()
                .contains("Game build mismatch"));
        assertFalse(MultiplayerProtocolV1.validateCompatibility(host, wrongManifest).accepted());
        assertTrue(MultiplayerProtocolV1.validateCompatibility(host, wrongManifest).reason()
                .contains("content manifest mismatch"));
    }

    @Test
    void messageFamiliesAndDeliveryModesAreExplicit() {
        assertEquals(MultiplayerProtocolV1.MessageFamily.HANDSHAKE,
                MultiplayerProtocolV1.MessageKind.HELLO.family());
        assertTrue(MultiplayerProtocolV1.MessageKind.MATCH_START.reliableOrdered());
        assertTrue(MultiplayerProtocolV1.MessageKind.SPAWN.reliableOrdered());
        assertTrue(MultiplayerProtocolV1.MessageKind.DESPAWN.reliableOrdered());
        assertTrue(MultiplayerProtocolV1.MessageKind.DEATH.reliableOrdered());
        assertTrue(MultiplayerProtocolV1.MessageKind.MATCH_END.reliableOrdered());
        assertTrue(MultiplayerProtocolV1.MessageKind.ERROR_MESSAGE.reliableOrdered());
        assertTrue(MultiplayerProtocolV1.MessageKind.FULL_SNAPSHOT.sequencedReplaceable());
        assertTrue(MultiplayerProtocolV1.MessageKind.CLIENT_INPUT_FRAME.sequencedReplaceable());
        assertEquals(MultiplayerProtocolV1.MessageFamily.ACKNOWLEDGEMENT,
                MultiplayerProtocolV1.MessageKind.INPUT_ACK.family());
    }

    @Test
    void envelopeAndPayloadValidationRejectMalformedData() {
        MultiplayerProtocolV1.Envelope ok = new MultiplayerProtocolV1.Envelope(
                MultiplayerProtocolV1.MessageKind.FULL_SNAPSHOT, 7L, 44L, 128);
        MultiplayerProtocolV1.Envelope tooLarge = new MultiplayerProtocolV1.Envelope(
                MultiplayerProtocolV1.MessageKind.FULL_SNAPSHOT, 7L, 44L,
                MultiplayerProtocolV1.MAX_MESSAGE_BYTES + 1);

        assertTrue(MultiplayerProtocolV1.validateEnvelope(ok).accepted());
        assertFalse(MultiplayerProtocolV1.validateEnvelope(tooLarge).accepted());
        assertEquals(MultiplayerProtocolV1.MalformedMessageAction.DISCONNECT_PEER,
                MultiplayerProtocolV1.validateEnvelope(tooLarge).action());
        assertFalse(MultiplayerProtocolV1.validatePayloadBytes(null).accepted());
        assertFalse(MultiplayerProtocolV1.validatePayloadBytes(new byte[]{
                (byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73
        }).accepted());
    }

    @Test
    void snapshotHeaderAndInputAcknowledgementClampToValidDomain() {
        MultiplayerProtocolV1.SnapshotHeader header =
                new MultiplayerProtocolV1.SnapshotHeader(-4L, -2L, -1L);
        MultiplayerProtocolV1.InputAck ack =
                new MultiplayerProtocolV1.InputAck(-2, -7L, -9L);

        assertEquals(0L, header.snapshotSequence());
        assertEquals(0L, header.hostTick());
        assertEquals(0L, header.lastProcessedInputSequence());
        assertEquals(0, ack.slotId());
        assertEquals(0L, ack.inputSequence());
        assertEquals(0L, ack.authoritativeTick());
    }

    @Test
    void unknownOrRetiredEntityUpdatesAreRejectedByContract() {
        MultiplayerEntityIdAllocator allocator = new MultiplayerEntityIdAllocator();
        MultiplayerEntityIdAllocator.NetworkEntityId unknown =
                new MultiplayerEntityIdAllocator.NetworkEntityId(99, 1);
        MultiplayerEntityIdAllocator.NetworkEntityId live = allocator.allocate();

        assertFalse(MultiplayerProtocolV1.acceptsEntityUpdate(allocator, unknown));
        assertTrue(MultiplayerProtocolV1.acceptsEntityUpdate(allocator, live));

        allocator.retire(live);

        assertFalse(MultiplayerProtocolV1.acceptsEntityUpdate(allocator, live));
    }
}
