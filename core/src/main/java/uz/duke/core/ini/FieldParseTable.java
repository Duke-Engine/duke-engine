package uz.duke.core.ini;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Maps a block's field tokens to their {@link FieldParser}s, ported from SAGE's
 * {@code FieldParse[]} parse tables.
 *
 * <p>One table describes how to populate one kind of block (an Object, a
 * Weapon, a Locomotor…). Tokens are matched case-insensitively, mirroring the
 * original's {@code stricmp}. Built once per block type and reused for every
 * instance of that block.
 *
 * @param <T> the block instance type this table populates
 */
public final class FieldParseTable<T> {

    private final Map<String, FieldParser<T>> fields = new LinkedHashMap<>();

    /** Register a parser for {@code token}. Returns {@code this} for chaining. */
    public FieldParseTable<T> add(String token, FieldParser<T> parser) {
        fields.put(token.toLowerCase(Locale.ROOT), parser);
        return this;
    }

    /** The parser for {@code token}, or {@code null} if the field is unknown. */
    public FieldParser<T> find(String token) {
        return fields.get(token.toLowerCase(Locale.ROOT));
    }

    /** The field tokens this table reads, lower case, in the order they were added. */
    public Set<String> names() {
        return Collections.unmodifiableSet(fields.keySet());
    }

    /** Every field of {@code other} as well. A token both tables read is a mistake, and is refused. */
    public FieldParseTable<T> addAll(FieldParseTable<T> other) {
        other.fields.forEach((token, parser) -> {
            if (fields.putIfAbsent(token, parser) != null) {
                throw new IllegalArgumentException("field '" + token + "' is read twice");
            }
        });
        return this;
    }

    /**
     * This table's fields read into the part of a bigger target that {@code part} picks
     * out: one block written into two objects, each field landing in its own.
     */
    public <U> FieldParseTable<U> on(Function<U, T> part) {
        var adapted = new FieldParseTable<U>();
        fields.forEach((token, parser) -> adapted.fields.put(token, (ini, target) -> parser.parse(ini, part.apply(target))));
        return adapted;
    }

    /** The same fields under names that start with {@code prefix}: {@code Hurt} becomes {@code PortraitHurt}. */
    public FieldParseTable<T> prefixed(String prefix) {
        var renamed = new FieldParseTable<T>();
        fields.forEach((token, parser) -> renamed.fields.put(prefix.toLowerCase(Locale.ROOT) + token, parser));
        return renamed;
    }

    /** A table that reads nothing: each of {@code tokens} is accepted and the rest of its line passed over. */
    public static <T> FieldParseTable<T> passingOver(Collection<String> tokens) {
        var table = new FieldParseTable<T>();
        for (var token : tokens) {
            table.add(token, (ini, target) -> ini.getRestOfLine());
        }
        return table;
    }
}
