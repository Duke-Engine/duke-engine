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
 *
 * <p>A bone is matched without case, as the client matches it: the reference's weapons name {@code LaserBoneName} and
 * {@code WeaponLaunchBone} in mixed case where its models write them in capitals. It may be read where a clip's last
 * frame puts it — an antenna the clip raises — and turned with a turret of the model it hangs under.
 */
public final class ModelBones {

    /** A file as read: its glTF head, its binary chunk where it has one, and where it came from. */
    private record Model(Map<?, ?> gltf, byte[] binary, String path) {
        static final Model NONE = new Model(Map.of(), null, null);
    }

    private static final Map<String, Model> READ = new ConcurrentHashMap<>();

    private ModelBones() {
    }

    /**
     * Where {@code bone} stands in {@code path}'s own axes, or null where the file has no such node — or is not
     * there, or is no glTF.
     */
    public static Coord3D of(String path, String bone) {
        return of(path, bone, null, null, 0f);
    }

    /**
     * Where {@code bone} stands in {@code path}'s own axes at the last frame of its clip {@code clip} — its default
     * pose for null — and, where it hangs under the node {@code turret}, turned with it by {@code turn} radians about
     * the up axis, the way a thing turns ({@code GameObject.getOrientation}); bone, clip and turret matched without
     * case. Null where the file has no such node, or is not there, or is no glTF; a clip it has not is its default
     * pose.
     */
    public static Coord3D of(String path, String bone, String clip, String turret, float turn) {
        if (path == null || bone == null) {
            return null;
        }
        var model = READ.computeIfAbsent(path, ModelBones::read);
        var nodes = (List<?>) model.gltf().get("nodes");
        if (nodes == null) {
            return null;
        }
        var posed = clip == null ? Map.<Integer, double[][]>of() : lastFrame(model, clip);
        var found = new LinkedHashMap<String, Coord3D>();
        for (int root : roots(model.gltf(), nodes)) {
            walk(nodes, root, IDENTITY, posed, turret, turn, found);
        }
        return found.get(bone.toUpperCase(java.util.Locale.ROOT));
    }

    private static Model read(String path) {
        var loader = Thread.currentThread().getContextClassLoader();
        try (var in = loader == null ? ClassLoader.getSystemResourceAsStream(path) : loader.getResourceAsStream(path)) {
            return in == null ? Model.NONE : model(in.readAllBytes(), path);
        } catch (IOException | RuntimeException e) {
            return Model.NONE;
        }
    }

    private static Model model(byte[] bytes, String path) {
        return new Model((Map<?, ?>) new Json(jsonOf(bytes)).value(), binaryOf(bytes), path);
    }

    /** Every named node of a glTF or .glb file's bytes, first of a name winning, where it stands. */
    static Map<String, Coord3D> bones(byte[] bytes) {
        var gltf = (Map<?, ?>) new Json(jsonOf(bytes)).value();
        var nodes = (List<?>) gltf.get("nodes");
        var found = new LinkedHashMap<String, Coord3D>();
        if (nodes == null) {
            return found;
        }
        var byCase = new LinkedHashMap<String, Coord3D>();
        for (int root : roots(gltf, nodes)) {
            walk(nodes, root, IDENTITY, Map.of(), null, 0f, byCase);
        }
        for (var node : nodes) {
            if (((Map<?, ?>) node).get("name") instanceof String name && !found.containsKey(name)) {
                var at = byCase.get(name.toUpperCase(java.util.Locale.ROOT));
                if (at != null) {
                    found.put(name, at);
                }
            }
        }
        return found;
    }

    /**
     * Each animated node's translation, rotation and scale at the last key of the file's clip named {@code clip} —
     * the three, any of them null where the clip leaves it as it stands. Read from the file's buffers: a .glb's binary
     * chunk, a data URI, or a file beside the model.
     */
    private static Map<Integer, double[][]> lastFrame(Model model, String clip) {
        var animations = (List<?>) model.gltf().get("animations");
        if (animations == null) {
            return Map.of();
        }
        for (var one : animations) {
            var animation = (Map<?, ?>) one;
            if (!(animation.get("name") instanceof String name) || !name.equalsIgnoreCase(clip)) {
                continue;
            }
            var posed = new java.util.HashMap<Integer, double[][]>();
            var samplers = (List<?>) animation.get("samplers");
            for (var channel : (List<?>) animation.get("channels")) {
                var target = (Map<?, ?>) ((Map<?, ?>) channel).get("target");
                var sampler = (Map<?, ?>) samplers.get(((Number) ((Map<?, ?>) channel).get("sampler")).intValue());
                if (!(target.get("node") instanceof Number node) || !(target.get("path") instanceof String path)) {
                    continue;
                }
                int slot = switch (path) {
                    case "translation" -> 0;
                    case "rotation" -> 1;
                    case "scale" -> 2;
                    default -> -1; // a morph target's weights moves no bone
                };
                var last = slot < 0 ? null : lastValue(model, ((Number) sampler.get("output")).intValue(),
                        "CUBICSPLINE".equals(sampler.get("interpolation")));
                if (last != null) {
                    posed.computeIfAbsent(node.intValue(), at -> new double[3][])[slot] = last;
                }
            }
            return posed;
        }
        return Map.of();
    }

    /** The last value an accessor of floats holds — the middle of the last three, for a cubic spline's. */
    private static double[] lastValue(Model model, int accessorIndex, boolean spline) {
        var accessor = (Map<?, ?>) ((List<?>) model.gltf().get("accessors")).get(accessorIndex);
        if (((Number) accessor.get("componentType")).intValue() != 5126) {
            return null; // not floats: a quantized clip, which no bone of the simulation's is read from
        }
        int components = switch ((String) accessor.get("type")) {
            case "SCALAR" -> 1;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> 0;
        };
        int count = ((Number) accessor.get("count")).intValue();
        var view = (Map<?, ?>) ((List<?>) model.gltf().get("bufferViews"))
                .get(((Number) accessor.get("bufferView")).intValue());
        var data = buffer(model, ((Number) view.get("buffer")).intValue());
        if (components == 0 || count == 0 || data == null) {
            return null;
        }
        int stride = view.get("byteStride") instanceof Number s ? s.intValue() : components * 4;
        int element = spline ? count - 2 : count - 1;
        int at = number(view.get("byteOffset")) + number(accessor.get("byteOffset")) + element * stride;
        var bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        var value = new double[components];
        for (int i = 0; i < components; i++) {
            value[i] = bytes.getFloat(at + i * 4);
        }
        return value;
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** A buffer's bytes: the .glb's binary chunk, a base64 data URI, or the file beside the model it names. */
    private static byte[] buffer(Model model, int index) {
        var buffer = (Map<?, ?>) ((List<?>) model.gltf().get("buffers")).get(index);
        if (!(buffer.get("uri") instanceof String uri)) {
            return model.binary();
        }
        if (uri.startsWith("data:")) {
            return java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
        }
        var beside = model.path() == null || model.path().lastIndexOf('/') < 0 ? uri
                : model.path().substring(0, model.path().lastIndexOf('/') + 1) + uri;
        var loader = Thread.currentThread().getContextClassLoader();
        try (var in = loader == null ? ClassLoader.getSystemResourceAsStream(beside)
                : loader.getResourceAsStream(beside)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** The binary chunk of a .glb, or null for a .gltf. */
    private static byte[] binaryOf(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 20 || buffer.getInt(0) != 0x46546C67) {
            return null;
        }
        int binAt = 20 + buffer.getInt(12);
        if (bytes.length < binAt + 8 || buffer.getInt(binAt + 4) != 0x004E4942) { // "BIN"
            return null;
        }
        int length = buffer.getInt(binAt);
        return java.util.Arrays.copyOfRange(bytes, binAt + 8, binAt + 8 + length);
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

    /** Every named node under {@code index}, in upper case, where it stands: posed by the clip, its turret turned. */
    private static void walk(List<?> nodes, int index, double[] parent, Map<Integer, double[][]> posed, String turret,
            float turn, Map<String, Coord3D> found) {
        var node = (Map<?, ?>) nodes.get(index);
        var local = local(node, posed.get(index));
        var name = node.get("name") instanceof String written ? written : null;
        if (name != null && turret != null && name.equalsIgnoreCase(turret)) {
            // The file is y up, and a thing's turn the way round the ground's x toward its y, which in the file's
            // axes is from x toward z: a turn about y the other way.
            local = multiply(local, turnedAboutUp(-turn));
        }
        var world = multiply(parent, local);
        if (name != null) {
            found.putIfAbsent(name.toUpperCase(java.util.Locale.ROOT),
                    new Coord3D((float) world[12], (float) world[13], (float) world[14]));
        }
        for (int child : integers(node.get("children"))) {
            walk(nodes, child, world, posed, turret, turn, found);
        }
    }

    /** A turn of {@code angle} radians about the file's up axis, column-major. */
    private static double[] turnedAboutUp(double angle) {
        double cos = StrictMath.cos(angle);
        double sin = StrictMath.sin(angle);
        return new double[] {cos, 0, -sin, 0, 0, 1, 0, 0, sin, 0, cos, 0, 0, 0, 0, 1};
    }

    private static final double[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    /**
     * A node's own transform, column-major as glTF writes a matrix: its matrix, or its translation, rotation, scale —
     * each of the three a clip's last key moves taken from {@code posed} instead.
     */
    private static double[] local(Map<?, ?> node, double[][] posed) {
        if (posed == null && node.get("matrix") instanceof List<?> matrix && matrix.size() == 16) {
            var m = new double[16];
            for (int i = 0; i < 16; i++) {
                m[i] = ((Number) matrix.get(i)).doubleValue();
            }
            return m;
        }
        var t = posed != null && posed[0] != null ? posed[0] : numbers(node.get("translation"), 0, 0, 0);
        var r = posed != null && posed[1] != null ? posed[1] : numbers(node.get("rotation"), 0, 0, 0, 1);
        var s = posed != null && posed[2] != null ? posed[2] : numbers(node.get("scale"), 1, 1, 1);
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
