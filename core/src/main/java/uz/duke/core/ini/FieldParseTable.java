package uz.duke.core.ini;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
}
