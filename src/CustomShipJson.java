import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CustomShipJson {
    private CustomShipJson() {}

    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) throw new IllegalArgumentException("Unexpected trailing JSON content");
        return value;
    }

    static String stringValue(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String text ? text : fallback;
    }

    static int intValue(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        if (value instanceof Number number) return number.intValue();
        return fallback;
    }

    static double doubleValue(Map<String, Object> object, String key, double fallback) {
        Object value = object.get(key);
        if (value instanceof Number number) return number.doubleValue();
        return fallback;
    }

    @SuppressWarnings("unchecked")
    static List<Object> arrayValue(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> objectValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static void writeValue(StringBuilder sb, Object value, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String text) {
            writeString(sb, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> object) {
            writeObject(sb, object, depth);
        } else if (value instanceof Iterable<?> iterable) {
            writeArray(sb, iterable, depth);
        } else if (value instanceof Enum<?> enumValue) {
            writeString(sb, enumValue.name());
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> object, int depth) {
        sb.append('{');
        if (!object.isEmpty()) {
            boolean first = true;
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (!first) sb.append(',');
                sb.append('\n');
                indent(sb, depth + 1);
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(": ");
                writeValue(sb, entry.getValue(), depth + 1);
                first = false;
            }
            sb.append('\n');
            indent(sb, depth);
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> array, int depth) {
        sb.append('[');
        boolean first = true;
        for (Object item : array) {
            if (!first) sb.append(',');
            sb.append('\n');
            indent(sb, depth + 1);
            writeValue(sb, item, depth + 1);
            first = false;
        }
        if (!first) {
            sb.append('\n');
            indent(sb, depth);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(Math.max(0, depth)));
    }

    private static final class Parser {
        private final String json;
        private int index;

        Parser(String json) {
            this.json = json == null ? "" : json;
        }

        Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) throw new IllegalArgumentException("Unexpected end of JSON");
            char ch = peek();
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (ch == '-' || Character.isDigit(ch)) yield parseNumber();
                    throw new IllegalArgumentException("Unexpected JSON character: " + ch);
                }
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return object;
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (consume('}')) return object;
                expect(',');
            }
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return array;
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) return array;
                expect(',');
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char ch = next();
                if (ch == '"') return sb.toString();
                if (ch == '\\') {
                    if (isAtEnd()) throw new IllegalArgumentException("Unterminated JSON escape");
                    char escaped = next();
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> sb.append(parseUnicodeEscape());
                        default -> throw new IllegalArgumentException("Unsupported JSON escape: " + escaped);
                    }
                } else {
                    sb.append(ch);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        Object parseLiteral(String literal, Object value) {
            if (!json.startsWith(literal, index)) {
                throw new IllegalArgumentException("Expected JSON literal " + literal);
            }
            index += literal.length();
            return value;
        }

        Number parseNumber() {
            int start = index;
            consume('-');
            while (!isAtEnd() && Character.isDigit(peek())) index++;
            boolean fractional = false;
            if (consume('.')) {
                fractional = true;
                while (!isAtEnd() && Character.isDigit(peek())) index++;
            }
            if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
                fractional = true;
                index++;
                if (!isAtEnd() && (peek() == '+' || peek() == '-')) index++;
                while (!isAtEnd() && Character.isDigit(peek())) index++;
            }
            String text = json.substring(start, index);
            if (fractional) return Double.parseDouble(text);
            return Long.parseLong(text);
        }

        char parseUnicodeEscape() {
            if (index + 4 > json.length()) throw new IllegalArgumentException("Incomplete unicode escape");
            String hex = json.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(peek())) index++;
        }

        boolean consume(char expected) {
            if (!isAtEnd() && peek() == expected) {
                index++;
                return true;
            }
            return false;
        }

        void expect(char expected) {
            if (!consume(expected)) throw new IllegalArgumentException("Expected JSON character: " + expected);
        }

        char peek() {
            return json.charAt(index);
        }

        char next() {
            return json.charAt(index++);
        }

        boolean isAtEnd() {
            return index >= json.length();
        }
    }
}
