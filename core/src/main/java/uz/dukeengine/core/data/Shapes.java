package uz.dukeengine.core.data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@link Shape} of a type: the one written for it at compile time if there is one, else what reflection
 * says, worked out once per type and kept.
 *
 * <p>Run with {@code -Dduke.shapes=reflection} and only reflection is asked, which is how the engine's tests
 * prove the two say the same.
 */
public final class Shapes {

    private static final boolean REFLECTION_ONLY = "reflection".equals(System.getProperty("duke.shapes"));

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
            var written = REFLECTION_ONLY ? null : Generated.ALL.get(type);
            return written != null ? written : REFLECTED.get(type);
        }
    };

    private static final ClassValue<Shape> REFLECTED = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> type) {
            return new Shape(type.isRecord() ? components(type) : null,
                    type.isSealed() ? List.of(type.getPermittedSubclasses()) : null, factory(type),
                    type.isEnum() ? List.of(type.getEnumConstants()) : null);
        }
    };

    /** The shape the binder reads {@code type} by. */
    public static Shape of(Class<?> type) {
        return SHAPES.get(type);
    }

    /** The shape reflection gives {@code type}, whatever was written for it. */
    public static Shape reflected(Class<?> type) {
        return REFLECTED.get(type);
    }

    /** Every shape written at compile time and found on the class path, by type. */
    public static Map<Class<?>, Shape> generated() {
        return Generated.ALL;
    }

    private static Shape.Components components(Class<?> type) {
        var components = type.getRecordComponents();
        var names = new ArrayList<String>(components.length);
        var generics = new ArrayList<TypeRef>(components.length);
        var types = new Class<?>[components.length];
        var marks = new ArrayList<List<java.lang.annotation.Annotation>>(components.length);
        var accessors = new Method[components.length];
        for (int i = 0; i < components.length; i++) {
            names.add(components[i].getName());
            generics.add(ref(components[i].getGenericType()));
            types[i] = components[i].getType();
            marks.add(List.of(components[i].getAnnotations()));
            accessors[i] = components[i].getAccessor();
            accessors[i].setAccessible(true);
        }
        java.lang.reflect.Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("cannot build " + type.getName(), e);
        }
        Function<Object, Object[]> read = record -> {
            var values = new Object[accessors.length];
            for (int i = 0; i < accessors.length; i++) {
                var accessor = accessors[i];
                values[i] = call(() -> accessor.invoke(record));
            }
            return values;
        };
        return new Shape.Components(List.copyOf(names), List.copyOf(generics), List.of(types), List.copyOf(marks),
                values -> call(() -> constructor.newInstance(values)), read, defaults(type));
    }

    /** A record's {@code static final DEFAULTS} of its own type, read when first wanted; {@code null} if none. */
    private static Supplier<Object> defaults(Class<?> type) {
        try {
            var field = type.getDeclaredField("DEFAULTS");
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
                return null;
            }
            field.setAccessible(true);
            return () -> call(() -> field.get(null));
        } catch (NoSuchFieldException _) {
            return null;
        }
    }

    /**
     * A type's static {@code of(String)} or {@code valueOf(String)}, or none. Asked of every scalar and every
     * record-typed component, and the answer for most is "none" — which reflection gives by throwing, which is
     * why it is asked once.
     */
    private static Function<String, ?> factory(Class<?> type) {
        for (var name : List.of("of", "valueOf")) {
            try {
                var method = type.getMethod(name, String.class);
                if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                    return text -> call(() -> method.invoke(null, text));
                }
            } catch (NoSuchMethodException _) {
                // try the next name
            }
        }
        return null;
    }

    private static TypeRef ref(Type type) {
        return switch (type) {
            case Class<?> c -> new TypeRef(c);
            case ParameterizedType p -> new TypeRef(raw(p.getRawType()),
                    Arrays.stream(p.getActualTypeArguments()).map(Shapes::ref).toList());
            case WildcardType w -> new TypeRef(raw(w.getUpperBounds()[0]));
            default -> TypeRef.OBJECT;
        };
    }

    private static Class<?> raw(Type type) {
        return switch (type) {
            case Class<?> c -> c;
            case ParameterizedType p -> raw(p.getRawType());
            case WildcardType w -> raw(w.getUpperBounds()[0]);
            default -> Object.class;
        };
    }

    private interface Reflective {
        Object call() throws ReflectiveOperationException;
    }

    /**
     * A reflective call that throws what the method threw, as a direct call would. A checked exception cannot
     * be thrown from here as itself; it goes out inside an {@link IllegalStateException} with its message.
     */
    private static Object call(Reflective reflective) {
        try {
            return reflective.call();
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
