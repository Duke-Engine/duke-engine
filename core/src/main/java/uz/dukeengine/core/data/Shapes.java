package uz.dukeengine.core.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * The {@link Shape} of a type: the one written for it at compile time if there is one, else the engine's own for a
 * JDK type, else what reflection says, worked out once per type and kept.
 *
 * <p>Run with {@code -Dduke.shapes=reflection} and only reflection is asked, which is how the engine's tests
 * prove the two say the same.
 */
public final class Shapes {

    private static final boolean REFLECTION_ONLY = "reflection".equals(System.getProperty("duke.shapes"));

    /** The JDK types the binder asks about, as reflection sees them: no records, none sealed, a few factories. */
    private static final Map<Class<?>, Shape> BUILT_IN = jdkShapes();

    private Shapes() {
    }

    /** Loaded on first use, so a game that never binds never looks. */
    private static final class Generated {
        static final Map<Class<?>, Shape> ALL = load();

        private static Map<Class<?>, Shape> load() {
            var all = new HashMap<Class<?>, Shape>();
            for (var source : ServiceLoader.load(ShapeSource.class, Shapes.class.getClassLoader())) {
                all.putAll(source.shapes());
            }
            return Map.copyOf(all);
        }
    }

    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> type) {
            if (REFLECTION_ONLY) {
                return ReflectedShapes.of(type);
            }
            var written = Generated.ALL.get(type);
            if (written == null) {
                written = BUILT_IN.get(type);
            }
            return written != null ? written : ReflectedShapes.of(type);
        }
    };

    /** The shape the binder reads {@code type} by. */
    public static Shape of(Class<?> type) {
        return SHAPES.get(type);
    }

    /** The shape reflection gives {@code type}, whatever was written for it. */
    public static Shape reflected(Class<?> type) {
        return ReflectedShapes.of(type);
    }

    /** Every shape written at compile time and found on the class path, by type. */
    public static Map<Class<?>, Shape> generated() {
        return Generated.ALL;
    }

    /** The engine's own shapes for JDK types, by type. */
    public static Map<Class<?>, Shape> builtIn() {
        return BUILT_IN;
    }

    private static Map<Class<?>, Shape> jdkShapes() {
        var plain = new Shape(null, null, null, null);
        var all = new HashMap<Class<?>, Shape>();
        for (var type : List.of(Object.class, String.class, Character.class, boolean.class, byte.class, short.class,
                char.class, int.class, long.class, float.class, double.class, List.class, Set.class, Map.class)) {
            all.put(type, plain);
        }
        all.put(Boolean.class, new Shape(null, null, Boolean::valueOf, null));
        all.put(Byte.class, new Shape(null, null, Byte::valueOf, null));
        all.put(Short.class, new Shape(null, null, Short::valueOf, null));
        all.put(Integer.class, new Shape(null, null, Integer::valueOf, null));
        all.put(Long.class, new Shape(null, null, Long::valueOf, null));
        all.put(Float.class, new Shape(null, null, Float::valueOf, null));
        all.put(Double.class, new Shape(null, null, Double::valueOf, null));
        return Map.copyOf(all);
    }
}
