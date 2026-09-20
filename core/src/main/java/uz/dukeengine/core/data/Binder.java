package uz.dukeengine.core.data;

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
 * Builds records from blocks. A block is the record its word names, and each line in it one of that
 * record's components, by name — {@code MapWidth = 50} fills {@code int mapWidth}. There is no table
 * of fields to keep beside the record: the record is the table, as SAGE's field tables were a name
 * and a place to put it.
 *
 * <p>A value is read by its component's type: numbers ({@code 0x} for hex), {@code Yes}/{@code No},
 * enum constants, a {@code String} as written, a type with a static {@code of(String)} or
 * {@code valueOf(String)}, a {@code List} or {@code Set} from {@code [a, b]}, and a record from a list
 * of its components in order, {@code At = [0.1, 0, 0.2]}.
 *
 * <p>A record component is written {@code Geometry = Cylinder} with its fields under it: after the
 * {@code =}, the record itself, one of the records a sealed type permits, or a word the game gave an
 * open type ({@link #vocabulary}) — a module, for {@code ModuleData}. With nothing to write in it,
 * the word alone does: {@code Geometry = Sphere}. A list of records is {@code Modules = [} with a
 * block for each, named the same way, then {@code ]}. A {@code Map} is a block named after its
 * component, each line a key and its value: {@code Armor} holding {@code FLAME = 0.5}. So every line
 * of a block names one of its components, and no block is found by what it is.
 *
 * <p>A component the block does not write takes the record's {@code static final DEFAULTS} value if
 * it has one, else zero, {@code false}, {@code null} or an empty collection. What a record refuses in
 * its constructor is an error at the block that wrote it.
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
    private final Map<Class<?>, List<String>> vocabularyWords = new HashMap<>();

    /**
     * The records an open type may be written as, by word: for {@code ModuleData}, each module the
     * game has, {@code MoveUpdate} among them. A sealed type needs none; its records are its words.
     */
    public <T> Binder vocabulary(Class<T> type, Map<String, ? extends Class<? extends T>> byWord) {
        var words = new HashMap<String, Class<?>>();
        byWord.forEach((word, implementation) -> words.put(word.toLowerCase(Locale.ROOT), implementation));
        vocabularies.put(type, words);
        vocabularyWords.put(type, byWord.keySet().stream().sorted().toList());
        return this;
    }

    /** {@code block} as a {@code type}, which is a record. */
    public <R> R bind(Block block, Class<R> type) {
        return type.cast(record(block, type));
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
        for (var inner : block.blocks()) {
            int i = named(components, inner.word());
            if (i < 0 || raw(components[i].getGenericType()) != Map.class) {
                throw new DataException(block.at(inner.line()), misplaced(block, components, inner.word()));
            }
            if (given[i]) {
                throw new DataException(block.at(inner.line()),
                        "'" + inner.word() + "' is written twice in '" + block.word() + "'");
            }
            values[i] = map(inner, components[i].getGenericType());
            given[i] = true;
        }
        for (int i = 0; i < components.length; i++) {
            if (!given[i]) {
                values[i] = missing(type, components[i]);
            }
        }
        return construct(type, components, values, block.at(block.line()), block.word());
    }

    private static int named(RecordComponent[] components, String key) {
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Why a block written on its own inside another is not one of its maps, said as the line it
     * should have been: the files were written the other way first, so the way back is spelt out.
     */
    private String misplaced(Block holder, RecordComponent[] components, String word) {
        int named = named(components, word);
        if (named >= 0) {
            var generic = components[named].getGenericType();
            if (many(generic)) {
                return choosable(element(generic))
                        ? "'" + word + "' is a list of blocks: '" + word + " = [', a block for each, then ']'"
                        : "'" + word + "' is a list: write it " + word + " = [a, b]";
            }
            if (!choosable(raw(generic))) {
                return "'" + word + "' is a value: write it " + word + " = …";
            }
            return "'" + word + "' is written '" + word + " = " + String.join("' or '" + word + " = ",
                    words(raw(generic))) + "', its fields under it";
        }
        for (var component : components) {
            var generic = component.getGenericType();
            if (raw(generic) == Map.class) {
                continue;
            }
            var type = many(generic) ? element(generic) : raw(generic);
            if (accepting(type, word) != null) {
                var key = capitalized(component.getName());
                return many(generic)
                        ? "'" + word + "' goes in its list: '" + key + " = [', then " + word + " … End, then ']'"
                        : "'" + word + "' is the value of its field: " + key + " = " + word;
            }
        }
        return "'" + holder.word() + "' holds no block '" + word + "'";
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
        if (raw == Map.class) {
            throw new DataException(where, "'" + key + "' holds entries: write it as a block of its own, " + key + " … End");
        }
        if (many(target)) {
            var element = element(target);
            return switch (value) {
                case Value.Items items -> {
                    if (choosable(element)) {
                        throw new DataException(where,
                                "'" + key + "' is a list of blocks: '" + key + " = [', a block for each, then ']'");
                    }
                    var read = new ArrayList<Object>(items.items().size());
                    for (var item : items.items()) {
                        read.add(scalar(item, element, where, key));
                    }
                    yield collection(raw, read);
                }
                case Value.NestedList list -> {
                    // A record read from one line — {@code Skeleton 17 16} — may also be written as a block, for
                    // a list whose things have more to say than a line holds: a monster that starts asleep, a
                    // prop turned to face the door. One or the other for the whole list, not both in one.
                    if (!choosable(element) && !element.isRecord()) {
                        throw new DataException(where, "'" + key + "' is a list: write it [a, b]");
                    }
                    var read = new ArrayList<Object>(list.blocks().size());
                    for (var block : list.blocks()) {
                        read.add(nested(block, element, key));
                    }
                    yield collection(raw, read);
                }
                case Value.Text text -> throw new DataException(where, choosable(element)
                        ? "'" + key + "' is a list of blocks: '" + key + " = [', a block for each, then ']'"
                        : "'" + key + "' is a list: write it [a, b]");
                case Value.Nested nested -> throw new DataException(where,
                        "'" + key + "' is a list of blocks: '" + key + " = [', a block for each, then ']'");
            };
        }
        return switch (value) {
            case Value.Nested nested when !choosable(raw) ->
                    throw new DataException(where, "'" + key + "' is a value: write it " + key + " = …");
            case Value.Nested nested -> nested(nested.block(), raw, key);
            case Value.Items items when raw.isRecord() -> positional(items, raw, where, key);
            case Value.Items items -> throw new DataException(where, "'" + key + "' takes one value, not a list");
            case Value.NestedList list -> throw new DataException(where, "'" + key + "' takes one value, not a list");
            case Value.Text text -> choosable(raw) ? chosen(text.text(), raw, where, key) : scalar(text.text(), raw, where, key);
        };
    }

    /** A record written under its field, named by its class: the field's own record, or one it may choose. */
    private Object nested(Block block, Class<?> type, String key) {
        var record = accepting(type, block.word());
        if (record == null) {
            throw new DataException(block.at(block.line()), notOneOf(type, key, block.word()));
        }
        return record(block, record);
    }

    /** A record named by its word alone, with nothing written in it: {@code Geometry = Sphere}. */
    private Object chosen(String word, Class<?> type, String where, String key) {
        var record = accepting(type, word);
        if (record == null) {
            throw new DataException(where, notOneOf(type, key, word));
        }
        var components = record.getRecordComponents();
        var values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = missing(record, components[i]);
        }
        return construct(record, components, values, where, word);
    }

    private String notOneOf(Class<?> type, String key, String word) {
        return "'" + key + "' is one of " + words(type) + ", not '" + word + "'";
    }

    /**
     * Whether a type is written as a record named by its word: a record, a sealed type, or a type
     * the game gave words to. A type read from text by {@code of(String)} is a value, even a record.
     */
    private boolean choosable(Class<?> type) {
        return factoryOf(type) == null && (type.isRecord() || type.isSealed() || vocabularies.containsKey(type));
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

    /** Every word a component of {@code type} may be written with, for the message that lists them. */
    private List<String> words(Class<?> type) {
        var words = new ArrayList<String>();
        if (type.isRecord()) {
            words.add(type.getSimpleName());
        }
        if (type.isSealed()) {
            for (var permitted : type.getPermittedSubclasses()) {
                words.addAll(words(permitted));
            }
        }
        words.addAll(vocabularyWords.getOrDefault(type, List.of()));
        return words;
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
        return construct(type, components, values, where, type.getSimpleName());
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

    private static boolean many(Type type) {
        var raw = raw(type);
        return raw == List.class || raw == Set.class;
    }

    private static String capitalized(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
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
