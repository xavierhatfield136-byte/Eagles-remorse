import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GlbModel {
    static final int MAX_RENDER_TRIANGLES = 900;

    final String name;
    final Path source;
    final List<Triangle> triangles;
    final double radius;
    final String issue;

    private GlbModel(String name, Path source, List<Triangle> triangles, double radius, String issue) {
        this.name = name;
        this.source = source;
        this.triangles = triangles;
        this.radius = radius;
        this.issue = issue;
    }

    boolean isRenderable() {
        return issue == null && triangles != null && !triangles.isEmpty() && radius > 0.0001;
    }

    static GlbModel load(Path path) {
        return load(path, MAX_RENDER_TRIANGLES);
    }

    static GlbModel load(Path path, int maxRenderTriangles) {
        String fileName = path == null || path.getFileName() == null ? "model" : path.getFileName().toString();
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length < 20) return failed(fileName, path, "file is too small for GLB");

            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            String magic = ascii(bytes, 0, 4);
            int version = buffer.getInt(4);
            int declaredLength = buffer.getInt(8);
            if (!"glTF".equals(magic) || version != 2) {
                return failed(fileName, path, "not a GLB v2 file");
            }
            if (declaredLength != bytes.length) {
                return failed(fileName, path, "GLB length mismatch");
            }

            String json = null;
            int binStart = -1;
            int binLength = 0;
            int offset = 12;
            while (offset + 8 <= bytes.length) {
                int chunkLength = buffer.getInt(offset);
                String chunkType = ascii(bytes, offset + 4, 4);
                int chunkStart = offset + 8;
                if (chunkStart < 0 || chunkLength < 0 || chunkStart + chunkLength > bytes.length) {
                    return failed(fileName, path, "GLB chunk overruns file");
                }
                if ("JSON".equals(chunkType)) {
                    json = new String(bytes, chunkStart, chunkLength, StandardCharsets.UTF_8).trim();
                } else if (chunkType.equals("BIN\0")) {
                    binStart = chunkStart;
                    binLength = chunkLength;
                }
                offset = chunkStart + chunkLength;
            }
            if (json == null || binStart < 0 || binLength <= 0) {
                return failed(fileName, path, "missing JSON or BIN chunk");
            }

            Object parsed = new JsonParser(json).parse();
            if (!(parsed instanceof Map<?, ?> root)) return failed(fileName, path, "GLB JSON root is not an object");

            List<Triangle> triangles = extractTriangles(root, bytes, binStart, binLength);
            if (triangles.isEmpty()) return failed(fileName, path, "no renderable POSITION mesh primitives");

            Bounds bounds = Bounds.from(triangles);
            List<Triangle> normalized = normalizeAndSample(triangles, bounds, maxRenderTriangles);
            return new GlbModel(fileName, path, normalized, bounds.radius(), null);
        } catch (Exception ex) {
            return failed(fileName, path, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static GlbModel failed(String name, Path source, String issue) {
        return new GlbModel(name, source, List.of(), 1.0, issue == null ? "unknown load issue" : issue);
    }

    private static List<Triangle> extractTriangles(Map<?, ?> root, byte[] bytes, int binStart, int binLength) throws IOException {
        List<?> meshes = asList(root.get("meshes"));
        List<?> accessors = asList(root.get("accessors"));
        List<?> bufferViews = asList(root.get("bufferViews"));
        if (meshes == null || accessors == null || bufferViews == null) return List.of();

        List<Triangle> out = new ArrayList<>();
        for (Object meshObj : meshes) {
            Map<?, ?> mesh = asMap(meshObj);
            if (mesh == null) continue;
            List<?> primitives = asList(mesh.get("primitives"));
            if (primitives == null) continue;
            for (Object primObj : primitives) {
                Map<?, ?> primitive = asMap(primObj);
                if (primitive == null) continue;
                Map<?, ?> attrs = asMap(primitive.get("attributes"));
                if (attrs == null) continue;
                Integer positionAccessor = intValue(attrs.get("POSITION"));
                if (positionAccessor == null) continue;

                double[][] positions = readPositions(accessors, bufferViews, bytes, binStart, binLength, positionAccessor);
                if (positions.length < 3) continue;

                Integer indexAccessor = intValue(primitive.get("indices"));
                int[] indices = indexAccessor == null
                        ? sequentialIndices(positions.length)
                        : readIndices(accessors, bufferViews, bytes, binStart, binLength, indexAccessor);
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    int a = indices[i];
                    int b = indices[i + 1];
                    int c = indices[i + 2];
                    if (a < 0 || b < 0 || c < 0 || a >= positions.length || b >= positions.length || c >= positions.length) {
                        continue;
                    }
                    out.add(new Triangle(positions[a], positions[b], positions[c]));
                }
            }
        }
        return out;
    }

    private static double[][] readPositions(List<?> accessors, List<?> bufferViews, byte[] bytes, int binStart,
                                            int binLength, int accessorIndex) throws IOException {
        Map<?, ?> accessor = mapAt(accessors, accessorIndex);
        if (accessor == null) return new double[0][0];
        int componentType = intValue(accessor.get("componentType"), -1);
        int count = intValue(accessor.get("count"), 0);
        String type = stringValue(accessor.get("type"));
        if (componentType != 5126 || count <= 0 || !"VEC3".equals(type)) return new double[0][0];

        View view = viewFor(accessor, bufferViews, binStart, binLength, 12);
        if (view == null) return new double[0][0];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double[][] out = new double[count][3];
        for (int i = 0; i < count; i++) {
            int base = view.offset + i * view.stride;
            if (base < 0 || base + 12 > bytes.length) break;
            out[i][0] = buffer.getFloat(base);
            out[i][1] = buffer.getFloat(base + 4);
            out[i][2] = buffer.getFloat(base + 8);
        }
        return out;
    }

    private static int[] readIndices(List<?> accessors, List<?> bufferViews, byte[] bytes, int binStart,
                                     int binLength, int accessorIndex) {
        Map<?, ?> accessor = mapAt(accessors, accessorIndex);
        if (accessor == null) return new int[0];
        int componentType = intValue(accessor.get("componentType"), -1);
        int count = intValue(accessor.get("count"), 0);
        if (count <= 0) return new int[0];
        int componentBytes = switch (componentType) {
            case 5121 -> 1;
            case 5123 -> 2;
            case 5125 -> 4;
            default -> 0;
        };
        if (componentBytes == 0) return new int[0];

        View view = viewFor(accessor, bufferViews, binStart, binLength, componentBytes);
        if (view == null) return new int[0];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int base = view.offset + i * view.stride;
            if (base < 0 || base + componentBytes > bytes.length) break;
            out[i] = switch (componentType) {
                case 5121 -> Byte.toUnsignedInt(buffer.get(base));
                case 5123 -> Short.toUnsignedInt(buffer.getShort(base));
                case 5125 -> (int) Integer.toUnsignedLong(buffer.getInt(base));
                default -> 0;
            };
        }
        return out;
    }

    private static View viewFor(Map<?, ?> accessor, List<?> bufferViews, int binStart, int binLength, int elementBytes) {
        Integer bufferViewIndex = intValue(accessor.get("bufferView"));
        if (bufferViewIndex == null) return null;
        Map<?, ?> view = mapAt(bufferViews, bufferViewIndex);
        if (view == null) return null;
        int accessorOffset = intValue(accessor.get("byteOffset"), 0);
        int viewOffset = intValue(view.get("byteOffset"), 0);
        int viewLength = intValue(view.get("byteLength"), 0);
        int stride = intValue(view.get("byteStride"), elementBytes);
        int offset = binStart + viewOffset + accessorOffset;
        if (offset < binStart || viewLength < 0 || viewOffset + viewLength > binLength) return null;
        return new View(offset, Math.max(elementBytes, stride));
    }

    private static int[] sequentialIndices(int vertexCount) {
        int[] out = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) out[i] = i;
        return out;
    }

    private static List<Triangle> normalizeAndSample(List<Triangle> raw, Bounds bounds, int maxRenderTriangles) {
        int limit = Math.max(64, maxRenderTriangles);
        int step = Math.max(1, (int) Math.ceil(raw.size() / (double) limit));
        double radius = Math.max(0.0001, bounds.radius());
        List<Triangle> out = new ArrayList<>(Math.min(raw.size(), limit));
        for (int i = 0; i < raw.size(); i += step) {
            Triangle t = raw.get(i);
            out.add(new Triangle(
                    normalize(t.a, bounds, radius),
                    normalize(t.b, bounds, radius),
                    normalize(t.c, bounds, radius)));
        }
        return out;
    }

    private static double[] normalize(double[] v, Bounds bounds, double radius) {
        return new double[]{
                (v[0] - bounds.cx) / radius,
                (v[1] - bounds.cy) / radius,
                (v[2] - bounds.cz) / radius
        };
    }

    private static String ascii(byte[] bytes, int offset, int len) {
        return new String(bytes, offset, len, StandardCharsets.US_ASCII);
    }

    private static Map<?, ?> mapAt(List<?> list, int index) {
        if (index < 0 || index >= list.size()) return null;
        return asMap(list.get(index));
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        return null;
    }

    private static int intValue(Object value, int fallback) {
        Integer v = intValue(value);
        return v == null ? fallback : v;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static final class Triangle {
        final double[] a;
        final double[] b;
        final double[] c;
        final double avgZ;

        Triangle(double[] a, double[] b, double[] c) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.avgZ = (a[2] + b[2] + c[2]) / 3.0;
        }
    }

    private record View(int offset, int stride) {}

    private static final class Bounds {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;
        final double cx;
        final double cy;
        final double cz;

        private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.cx = (minX + maxX) * 0.5;
            this.cy = (minY + maxY) * 0.5;
            this.cz = (minZ + maxZ) * 0.5;
        }

        double radius() {
            double dx = maxX - minX;
            double dy = maxY - minY;
            double dz = maxZ - minZ;
            return Math.max(0.0001, Math.max(dx, Math.max(dy, dz)) * 0.5);
        }

        static Bounds from(List<Triangle> triangles) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (Triangle t : triangles) {
                for (double[] v : new double[][]{t.a, t.b, t.c}) {
                    minX = Math.min(minX, v[0]);
                    minY = Math.min(minY, v[1]);
                    minZ = Math.min(minZ, v[2]);
                    maxX = Math.max(maxX, v[0]);
                    maxY = Math.max(maxY, v[1]);
                    maxZ = Math.max(maxZ, v[2]);
                }
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static final class JsonParser {
        private final String text;
        private int pos;

        JsonParser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parse() throws IOException {
            Object value = parseValue();
            skipWs();
            if (pos != text.length()) throw error("unexpected trailing JSON");
            return value;
        }

        private Object parseValue() throws IOException {
            skipWs();
            if (pos >= text.length()) throw error("unexpected end of JSON");
            char ch = text.charAt(pos);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() throws IOException {
            expect('{');
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                pos++;
                return out;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                out.put(key, parseValue());
                skipWs();
                if (peek('}')) {
                    pos++;
                    return out;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() throws IOException {
            expect('[');
            List<Object> out = new ArrayList<>();
            skipWs();
            if (peek(']')) {
                pos++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWs();
                if (peek(']')) {
                    pos++;
                    return out;
                }
                expect(',');
            }
        }

        private String parseString() throws IOException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char ch = text.charAt(pos++);
                if (ch == '"') return out.toString();
                if (ch == '\\') {
                    if (pos >= text.length()) throw error("bad escape");
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> out.append(esc);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) throw error("bad unicode escape");
                            String hex = text.substring(pos, pos + 4);
                            out.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw error("unknown escape " + esc);
                    }
                } else {
                    out.append(ch);
                }
            }
            throw error("unterminated string");
        }

        private Object parseNumber() throws IOException {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            if (peek('.')) {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (peek('e') || peek('E')) {
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (start == pos) throw error("expected number");
            String raw = text.substring(start, pos).toLowerCase(Locale.US);
            try {
                if (raw.contains(".") || raw.contains("e")) return Double.parseDouble(raw);
                return Long.parseLong(raw);
            } catch (NumberFormatException ex) {
                throw error("bad number");
            }
        }

        private Object literal(String literal, Object value) throws IOException {
            if (!text.startsWith(literal, pos)) throw error("expected " + literal);
            pos += literal.length();
            return value;
        }

        private void expect(char ch) throws IOException {
            skipWs();
            if (pos >= text.length() || text.charAt(pos) != ch) throw error("expected " + ch);
            pos++;
        }

        private boolean peek(char ch) {
            return pos < text.length() && text.charAt(pos) == ch;
        }

        private void skipWs() {
            while (pos < text.length()) {
                char ch = text.charAt(pos);
                if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') return;
                pos++;
            }
        }

        private IOException error(String message) {
            return new IOException(message + " at byte " + pos);
        }
    }
}
