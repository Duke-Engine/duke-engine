package uz.dukeengine.core;

/**
 * A "magic cookie" handle for an interned string, ported from SAGE's
 * {@code NameKeyType}.
 *
 * <p>In the original engine this is an enum-typed int used purely to stop name
 * keys being mixed up with plain integers. Here it is a record so the type
 * safety is real: a {@code NameKeyType} can only be produced by
 * {@link NameKeyGenerator}, and two equal keys always denote the same string.
 * Comparing keys is a cheap int compare; comparing the original strings is not.
 */
public record NameKeyType(int id) {

    /** Always-legal sentinel for "no key". All other ids are assigned at runtime. */
    public static final NameKeyType INVALID = new NameKeyType(0);

    /** Ordinal ceiling — SAGE relies on keys fitting in 24 bits. */
    public static final int MAX = 1 << 23;

    public boolean isValid() {
        return id != 0;
    }
}
