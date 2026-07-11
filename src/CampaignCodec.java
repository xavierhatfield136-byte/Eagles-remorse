import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;

final class CampaignCodec {
    private CampaignCodec() {}

    static String encodeText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static String decodeText(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return encoded;
        }
    }

    static String serializePositiveIntSet(Collection<Integer> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer value : values) {
            if (value == null || value <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(value);
        }
        return sb.toString();
    }

    static void restorePositiveIntSet(Set<Integer> out, String raw) {
        if (out == null || raw == null || raw.isBlank()) return;
        for (String part : raw.split(",")) {
            int value = parseInt(part, 0);
            if (value > 0) out.add(value);
        }
    }

    static String serializeEncodedStringSet(Collection<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(encodeText(value.trim()));
        }
        return sb.toString();
    }

    static void restoreEncodedStringSet(Set<String> out, String raw) {
        if (out == null) return;
        out.clear();
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(",")) {
            String value = decodeText(part);
            if (!value.isBlank()) out.add(value);
        }
    }

    static int parseInt(String raw, int fallback) {
        try {
            if (raw == null || raw.isBlank()) return fallback;
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static double parseDouble(String raw, double fallback) {
        try {
            if (raw == null || raw.isBlank()) return fallback;
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static <E extends Enum<E>> E parseEnum(String name, E fallback) {
        if (fallback == null) return null;
        if (name == null || name.isBlank()) return fallback;
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), name.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
