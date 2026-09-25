package uz.dukeengine.core.content;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import uz.dukeengine.core.math.Coord3D;

/**
 * Where the named bones of a model file stand, for the simulation — read from the file itself, a glTF (.gltf) or
 * its binary form (.glb), in the model's own axes (y up, as the file has them) and its default pose: every node's own
 * translation, rotation and scale, from the scene's roots down to the one named. The reference's WeaponLaunchBone,
 * ExitBone, EXITSTART and DOCKACTION are read the same way.
 *
 * <p>Nothing of the client's: the file is read from the classpath as the client loads it, and worked out in plain
 * arithmetic, so every machine reads the same point to the last bit. A file is read once.
 */
public final class ModelBones {

    private static final Map<String, Map<String, Coord3D>> READ = new ConcurrentHashMap<>();

    private ModelBones() {
    }

    /**
     * Where {@code bone} stands in {@code path}'s own axes, or null where the file has no such node — or is not
     * there, or is no glTF.
     */
    public static Coord3D of(String path, String bone) {
        if (path == null || bone == null) {
            return null;
        }
        return READ.computeIfAbsent(path, ModelBones::read).get(bone);
    }

    private static Map<String, Coord3D> read(String path) {
        var loader = Thread.currentThread().getContextClassLoader();
        try (var in = loader == null ? ClassLoader.getSystemResourceAsStream(path) : loader.getResourceAsStream(path)) {
            return in == null ? Map.of() : bones(in.readAllBytes());
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    /** Every named node of a glTF or .glb file's bytes, first of a name winning, where it stands. */
    static Map<String, Coord3D> bones(byte[] bytes) {
        var gltf = (Map<?, ?>) new Json(jsonOf(bytes)).value();
        var nodes = (List<?>) gltf.get("nodes");
        var found = new LinkedHashMap<String, Coord3D>();
        if (nodes == null) {
            return found;
        }
        for (int root : roots(gltf, nodes)) {
            walk(nodes, root, IDENTITY, found);
        }
        return found;
    }

    /** The JSON of a .glb — its first chunk — or the whole of a .gltf. */
    private static String jsonOf(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length >= 20 && buffer.getInt(0) == 0x46546C67) { // "glTF"
            int length = buffer.getInt(12);
            return new String(bytes, 20, length, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** The scene's root nodes, or every node nothing lists as its child where there is no scene. */
    private static List<Integer> roots(Map<?, ?> gltf, List<?> nodes) {
        var scenes = (List<?>) gltf.get("scenes");
        if (scenes != null && !scenes.isEmpty()) {
            int chosen = gltf.get("scene") instanceof Number n ? n.intValue() : 0;
            var scene = (Map<?, ?>) scenes.get(Math.clamp(chosen, 0, scenes.size() - 1));
            return integers(scene.get("nodes"));
        }
        var children = new java.util.HashSet<Integer>();
        for (var node : nodes) {
            children.addAll(integers(((Map<?, ?>) node).get("children")));
        }
        var roots = new ArrayList<Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            if (!children.contains(i)) {
                roots.add(i);
            }
        }
        return roots;
    }

    private static void walk(List<?> nodes, int index, double[] parent, Map<String, Coord3D> found) {
        var node = (Map<?, ?>) nodes.get(index);
        var world = multiply(parent, local(node));
        if (node.get("name") instanceof String name) {
            found.putIfAbsent(name, new Coord3D((float) world[12], (float) world[13], (float) world[14]));
        }
        for (int child : integers(node.get("children"))) {
            walk(nodes, child, world, found);
        }
    }

    private static final double[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    /** A node's own transform, column-major as glTF writes a matrix: its matrix, or its translation, rotation, scale. */
    private static double[] local(Map<?, ?> node) {
        if (node.get("matrix") instanceof List<?> matrix && matrix.size() == 16) {
            var m = new double[16];
            for (int i = 0; i < 16; i++) {
                m[i] = ((Number) matrix.get(i)).doubleValue();
            }
            return m;
        }
        var t = numbers(node.get("translation"), 0, 0, 0);
        var r = numbers(node.get("rotation"), 0, 0, 0, 1);
        var s = numbers(node.get("scale"), 1, 1, 1);
        double x = r[0];
        double y = r[1];
        double z = r[2];
        double w = r[3];
        return new double[] {
            (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0,
            (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0,
            (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0,
            t[0], t[1], t[2], 1
        };
    }

    private static double[] multiply(double[] a, double[] b) {
        var product = new double[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[column * 4 + k];
                }
                product[column * 4 + row] = sum;
            }
        }
        return product;
    }

    private static double[] numbers(Object list, double... otherwise) {
        if (!(list instanceof List<?> values) || values.size() != otherwise.length) {
            return otherwise;
        }
        var numbers = new double[values.size()];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = ((Number) values.get(i)).doubleValue();
        }
        return numbers;
    }

    private static List<Integer> integers(Object list) {
        if (!(list instanceof List<?> values)) {
            return List.of();
        }
        var integers = new ArrayList<Integer>(values.size());
        for (var value : values) {
            integers.add(((Number) value).intValue());
        }
        return integers;
    }

    /** Just enough JSON for a glTF's head: objects, arrays, strings, numbers, and the three words. */
    private static final class Json {
        private final String text;
        private int at;

        Json(String text) {
            this.text = text;
        }

        Object value() {
            space();
            char next = text.charAt(at);
            return switch (next) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> word("true", Boolean.TRUE);
                case 'f' -> word("false", Boolean.FALSE);
                case 'n' -> word("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            var object = new LinkedHashMap<String, Object>();
            at++;
            space();
            if (text.charAt(at) == '}') {
                at++;
                return object;
            }
            while (true) {
                space();
                var key = string();
                space();
                at++; // ':'
                object.put(key, value());
                space();
                if (text.charAt(at++) == '}') {
                    return object;
                }
            }
        }

        private List<Object> array() {
            var array = new ArrayList<Object>();
            at++;
            space();
            if (text.charAt(at) == ']') {
                at++;
                return array;
            }
            while (true) {
                array.add(value());
                space();
                if (text.charAt(at++) == ']') {
                    return array;
                }
            }
        }

        private String string() {
            var built = new StringBuilder();
            at++; // the opening quote
            while (true) {
                char c = text.charAt(at++);
                if (c == '"') {
                    return built.toString();
                }
                if (c != '\\') {
                    built.append(c);
                    continue;
                }
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case 'n' -> built.append('\n');
                    case 't' -> built.append('\t');
                    case 'r' -> built.append('\r');
                    case 'b' -> built.append('\b');
                    case 'f' -> built.append('\f');
                    case 'u' -> {
                        built.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> built.append(escaped);
                }
            }
        }

        private Object word(String word, Object meaning) {
            at += word.length();
            return meaning;
        }

        private Double number() {
            int from = at;
            while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            return Double.parseDouble(text.substring(from, at));
        }

        private void space() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }
    }
}
