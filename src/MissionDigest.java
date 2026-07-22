import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class MissionDigest {
    private MissionDigest() {}

    public static String missionDefinitionDigest(CustomMissionDescriptor descriptor, MissionTemplate template) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("missionDefinition|");
        if (descriptor != null) {
            canonical.append("id=").append(descriptor.id()).append('|');
            canonical.append("revision=").append(descriptor.revision()).append('|');
            canonical.append("modes=").append(descriptor.supportedLaunchModes().stream()
                    .map(Enum::name)
                    .sorted()
                    .toList()).append('|');
            canonical.append("capabilities=").append(descriptor.requiredMultiplayerCapabilities().stream()
                    .map(Enum::name)
                    .sorted()
                    .toList()).append('|');
        }
        if (template != null) {
            canonical.append("world=").append(template.worldW()).append('x').append(template.worldH()).append('|');
            canonical.append("allowedWorld=1800..60000|");
            canonical.append("objective=").append(template.objectiveType()).append('|');
            canonical.append("victory=").append(template.victoryRule()).append('|');
            canonical.append("seedPolicy=").append(template.seedPolicy()).append('|');
            canonical.append("allowedSeed=0..9223372036854775807|");
            appendAllowedSettings(canonical, template.rosterTemplate());
            appendSlots(canonical, template.rosterTemplate());
        }
        return sha256(canonical.toString());
    }

    public static String lockedLaunchSpecDigest(MissionLaunchSpec spec, long lockedLobbyRevision) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("lockedLaunchSpec|");
        if (spec != null) {
            canonical.append("id=").append(spec.missionId()).append('|');
            canonical.append("revision=").append(spec.missionRevision()).append('|');
            canonical.append("rules=").append(spec.rulesProfileId()).append('|');
            canonical.append("world=").append(spec.worldW()).append('x').append(spec.worldH()).append('|');
            canonical.append("seed=").append(spec.seed()).append('|');
            canonical.append("lockedRevision=").append(Math.max(0L, lockedLobbyRevision)).append('|');
            canonical.append("objective=").append(spec.objectiveType()).append('|');
            canonical.append("victory=").append(spec.victoryRule()).append('|');
            appendSlots(canonical, spec.resolvedRosters());
            appendSlots(canonical, spec.playerSlots());
        }
        return sha256(canonical.toString());
    }

    private static void appendSlots(StringBuilder canonical, List<MissionSlotSpec> slots) {
        canonical.append("slots=[");
        if (slots != null) {
            slots.stream()
                    .sorted(Comparator
                            .comparingInt(MissionSlotSpec::teamId)
                            .thenComparingInt(MissionSlotSpec::slotId)
                            .thenComparing(slot -> slot.defaultHull().name())
                            .thenComparing(slot -> slot.controlMode().name())
                            .thenComparing(MissionSlotSpec::spawnAnchorId))
                    .forEach(slot -> canonical
                            .append(slot.teamId()).append(':')
                            .append(slot.slotId()).append(':')
                            .append(slot.defaultHull().name()).append(':')
                            .append(slot.controlMode().name()).append(':')
                            .append(slot.spawnAnchorId()).append(';'));
        }
        canonical.append("]|");
    }

    private static void appendAllowedSettings(StringBuilder canonical, List<MissionSlotSpec> slots) {
        canonical.append("allowedSettings=[");
        if (slots != null) {
            slots.stream()
                    .sorted(Comparator
                            .comparingInt(MissionSlotSpec::teamId)
                            .thenComparingInt(MissionSlotSpec::slotId)
                            .thenComparing(MissionSlotSpec::spawnAnchorId))
                    .forEach(slot -> canonical
                            .append(slot.teamId()).append(':')
                            .append(slot.slotId()).append(':')
                            .append("defaultHull=").append(slot.defaultHull().name()).append(':')
                            .append("control=").append(slot.controlMode().name()).append(':')
                            .append("required=").append(slot.required()).append(';'));
        }
        canonical.append("]|");
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }
}
