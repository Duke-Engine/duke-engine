package uz.duke.core.data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds records from blocks. A block is the record its word names, and each {@code Key = value} in
 * it the component of that name, read as the component's type — {@code MapWidth = 50} fills
 * {@code int mapWidth}. There is no table of fields to keep beside the record: the record is the
 * table, as SAGE's field tables were a name and a place to put it.
 *
 * <p>A block written inside another fills the component it is named after ({@code Look} for a
 * record component {@code look}), else the one whose type it names: a record of that name
 * ({@code Generation}), one of the records a sealed type permits ({@code Cylinder} for a
 * {@code Geometry}), or a word the game gave an open type ({@link #vocabulary}). A {@code List} of
 * such takes the block as often as it is written.
 *
 * <p>A value is read by the component's type: numbers ({@code 0x} for hex), {@code Yes}/{@code No},
 * enum constants, a {@code String} as written, a type with a static {@code of(String)} or
 * {@code valueOf(String)}, a {@code List} or {@code Set} from {@code [a, b]}, and a record from a
 * list of its components in order, {@code At = [0.1, 0, 0.2]}. A {@code Map} component is a block named like
 * the component, each line a key and its value: {@code Armor} holding {@code FLAME = 0.5}. A component the block does not write
 * takes the record's {@code static final DEFAULTS} value if it has one, else zero, {@code false},
 * {@code null} or an empty collection. What a record refuses in its constructor is an error at the
 * block that wrote it.
 */
public final class Binder {

    private static final ClassValue<Optional<Object>> DEFAULTS = new ClassValue<>() {
        @Override
        protected Optional<Object> computeValue(Class<?> type) {
            try {
                var field = type.getDeclaredField("DEFAULTS");
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
                    return Optional.empty();
                }
                field.setAccessible(true);
                return Optional.ofNullable(field.get(null));
            } catch (NoSuchFieldException e) {
                return Optional.empty();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    private final Map<Class<?>, Map<String, Class<?>>> vocabularies = new HashMap<>();

    /**
     * The blocks an open type may be written as, by word: for {@code ModuleData}, each module the
     * game has, {@code MoveUpdate} among them. A sealed type needs none; its records are its words.
     */
    public <T> Binder vocabulary(Class<T> type, Map<String, ? extends Class<? extends T>> byWord) {
        var words = new HashMap<String, Class<?>>();
        byWord.forEach((word, implementation) -> words.put(word.toLowerCase(Locale.ROOT), implementation));
        vocabularies.put(type, words);
        return this;
    }

    /** {@code block} as a {@code type}, which is a record. */
    public <R> R bind(Block block, Class<R> type) {
        return type.cast(record(block, type));
    }

    private record Slot(int index, Class<?> type, boolean many, boolean map) {
    }

    private Object record(Block block, Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }
        var components = type.getRecordComponents();
        var values = new Object[components.length];
        var given = new boolean[components.length];
        for (var field : block.fields()) {
            int i = named(components, field.key());
            if (i < 0) {
                throw new DataException(block.at(field.line()),
                        "'" + block.word() + "' has no field '" + field.key() + "'");
            }
            values[i] = value(field.value(), components[i].getGenericType(), block.at(field.line()), field.key());
            given[i] = true;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Object>[] gathered = new List[components.length];
        for (var inner : block.blocks()) {
            var slot = slotFor(components, inner.word());
            if (slot == null) {
                throw new DataException(block.at(inner.line()),
                        "'" + block.word() + "' holds no block '" + inner.word() + "'");
            }
            if (given[slot.index()] && (!slot.many() || gathered[slot.index()] == null)) {
                throw new DataException(block.at(inner.line()),
                        "'" + inner.word() + "' is written twice in '" + block.word() + "'");
            }
            var bound = slot.map() ? map(inner, components[slot.index()].getGenericType()) : record(inner, slot.type());
            if (slot.many()) {
                if (gathered[slot.index()] == null) {
                    gathered[slot.index()] = new ArrayList<>();
                }
                gathered[slot.index()].add(bound);
            } else {
                values[slot.index()] = bound;
            }
            given[slot.index()] = true;
        }
        for (int i = 0; i < components.length; i++) {
            if (gathered[i] != null) {
                values[i] = collection(components[i].getType(), gathered[i]);
            } else if (!given[i]) {
                values[i] = missing(type, components[i]);
            }
        }
        return construct(type, components, values, block);
    }

    private static int named(RecordComponent[] components, String key) {
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    private Slot slotFor(RecordComponent[] components, String word) {
        // A block named after the component that holds it: Look for a MonsterLook look.
        for (int i = 0; i < components.length; i++) {
            if (!components[i].getName().equalsIgnoreCase(word)) {
                continue;
            }
            var generic = components[i].getGenericType();
            var raw = raw(generic);
            if (raw == Map.class) {
                return new Slot(i, Map.class, false, true);
            }
            boolean many = raw == List.class || raw == Set.class;
            var type = many ? element(generic) : raw;
            if (type.isRecord()) {
                return new Slot(i, type, many, false);
            }
        }
        // Else after what it is: a Generation, a Cylinder of a Geometry, a module.
        for (int i = 0; i < components.length; i++) {
            var generic = components[i].getGenericType();
            var raw = raw(generic);
            if (raw == Map.class) {
                continue;
            }
            boolean many = raw == List.class || raw == Set.class;
            var type = accepting(many ? element(generic) : raw, word);
            if (type != null) {
                return new Slot(i, type, many, false);
            }
        }
        return null;
    }

    /** The record a block called {@code word} is, where a component of {@code type} holds it. */
    private Class<?> accepting(Class<?> type, String word) {
        if (type.isRecord() && type.getSimpleName().equalsIgnoreCase(word)) {
            return type;
        }
        if (type.isSealed()) {
            for (var permitted : type.getPermittedSubclasses()) {
                var found = accepting(permitted, word);
                if (found != null) {
                    return found;
                }
            }
        }
        var words = vocabularies.get(type);
        return words == null ? null : words.get(word.toLowerCase(Locale.ROOT));
    }

    /** A {@code Map} written as a block of its entries: each field's key and value, read as the map's types. */
    private Object map(Block block, Type type) {
        if (!block.blocks().isEmpty()) {
            var inner = block.blocks().getFirst();
            throw new DataException(block.at(inner.line()), "'" + block.word() + "' holds entries, not blocks");
        }
        var arguments = type instanceof ParameterizedType p ? p.getActualTypeArguments() : new Type[] {Object.class, Object.class};
        var entries = new LinkedHashMap<Object, Object>();
        for (var field : block.fields()) {
            var where = block.at(field.line());
            entries.put(scalar(field.key(), raw(arguments[0]), where, field.key()),
                    value(field.value(), arguments[1], where, field.key()));
        }
        return Collections.unmodifiableMap(entries);
    }

    private Object value(Value value, Type target, String where, String key) {
        var raw = raw(target);
        if (raw == List.class || raw == Set.class) {
            if (!(value instanceof Value.Items items)) {
                throw new DataException(where, "'" + key + "' is a list: write it [a, b]");
            }
            var element = element(target);
            var read = new ArrayList<Object>(items.items().size());
            for (var item : items.items()) {
                read.add(scalar(item, element, where, key));
            }
            return collection(raw, read);
        }
        return switch (value) {
            case Value.Items items when raw.isRecord() -> positional(items, raw, where, key);
            case Value.Items items -> throw new DataException(where, "'" + key + "' takes one value, not a list");
            case Value.Text text -> scalar(text.text(), raw, where, key);
        };
    }

    /** A record written as its components in order: {@code At = [0.12, 0.1, -0.22]}. */
    private Object positional(Value.Items items, Class<?> type, String where, String key) {
        var components = type.getRecordComponents();
        if (components.length != items.items().size()) {
            throw new DataException(where, "'" + key + "' takes " + components.length + " values, not "
                    + items.items().size());
        }
        var values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = scalar(items.items().get(i), components[i].getType(), where, key);
        }
        return construct(type, components, values, where);
    }

    private static Object scalar(String text, Class<?> type, String where, String key) {
        try {
            if (type == String.class) {
                return text;
            }
            if (type == int.class || type == Integer.class) {
                return hex(text) ? Integer.parseUnsignedInt(text.substring(2), 16) : Integer.parseInt(text);
            }
            if (type == long.class || type == Long.class) {
                return hex(text) ? Long.parseUnsignedLong(text.substring(2), 16) : Long.parseLong(text);
            }
            if (type == float.class || type == Float.class) {
                return Float.parseFloat(text);
            }
            if (type == double.class || type == Double.class) {
                return Double.parseDouble(text);
            }
            if (type == boolean.class || type == Boolean.class) {
                return yesOrNo(text, where, key);
            }
            if (type == char.class || type == Character.class) {
                if (text.length() != 1) {
                    throw new DataException(where, "'" + key + "' is one character, not '" + text + "'");
                }
                return text.charAt(0);
            }
            if (type.isEnum()) {
                return constant(type, text, where, key);
            }
            var factory = factoryOf(type);
            if (factory != null) {
                return factory.invoke(null, text);
            }
        } catch (NumberFormatException e) {
            throw new DataException(where, "'" + key + "' is a number, not '" + text + "'");
        } catch (InvocationTargetException e) {
            throw new DataException(where, "'" + key + "': " + e.getCause().getMessage(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        throw new DataException(where, "'" + key + "' cannot be read as " + type.getSimpleName());
    }

    private static boolean hex(String text) {
        return text.length() > 2 && text.charAt(0) == '0' && (text.charAt(1) == 'x' || text.charAt(1) == 'X');
    }

    private static boolean yesOrNo(String text, String where, String key) {
        if (text.equalsIgnoreCase("Yes") || text.equalsIgnoreCase("true")) {
            return true;
        }
        if (text.equalsIgnoreCase("No") || text.equalsIgnoreCase("false")) {
            return false;
        }
        throw new DataException(where, "'" + key + "' is Yes or No, not '" + text + "'");
    }

    private static Object constant(Class<?> type, String text, String where, String key) {
        var names = new ArrayList<String>();
        for (var constant : type.getEnumConstants()) {
            var name = ((Enum<?>) constant).name();
            if (name.equalsIgnoreCase(text)) {
                return constant;
            }
            names.add(name);
        }
        throw new DataException(where, "'" + key + "' is one of " + names + ", not '" + text + "'");
    }

    private static Method factoryOf(Class<?> type) {
        for (var name : List.of("of", "valueOf")) {
            try {
                var method = type.getMethod(name, String.class);
                if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            } catch (NoSuchMethodException e) {
                // try the next name
            }
        }
        return null;
    }

    private static Object collection(Class<?> type, List<Object> items) {
        return type == Set.class ? Collections.unmodifiableSet(new LinkedHashSet<>(items)) : List.copyOf(items);
    }

    /** What a component the block does not write holds: its record's default, else nothing. */
    private static Object missing(Class<?> record, RecordComponent component) {
        var defaults = DEFAULTS.get(record);
        if (defaults.isPresent()) {
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(defaults.get());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        var type = component.getType();
        if (type == List.class) {
            return List.of();
        }
        if (type == Set.class) {
            return Set.of();
        }
        if (type == Map.class) {
            return Map.of();
        }
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return 0;
    }

    private static Object construct(Class<?> type, RecordComponent[] components, Object[] values, Block block) {
        return construct(type, components, values, block.at(block.line()), block.word());
    }

    private static Object construct(Class<?> type, RecordComponent[] components, Object[] values, String where) {
        return construct(type, components, values, where, type.getSimpleName());
    }

    private static Object construct(Class<?> type, RecordComponent[] components, Object[] values,
            String where, String word) {
        var types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }
        try {
            var constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof DataException data) {
                throw data;
            }
            throw new DataException(where, "'" + word + "': " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build " + type.getName(), e);
        }
    }

    private static Class<?> raw(Type type) {
        return switch (type) {
            case Class<?> c -> c;
            case ParameterizedType p -> raw(p.getRawType());
            case WildcardType w -> raw(w.getUpperBounds()[0]);
            default -> Object.class;
        };
    }

    private static Class<?> element(Type collection) {
        return collection instanceof ParameterizedType p ? raw(p.getActualTypeArguments()[0]) : Object.class;
    }
}
