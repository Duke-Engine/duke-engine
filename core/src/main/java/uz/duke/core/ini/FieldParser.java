package uz.duke.core.ini;

/**
 * Parses one field of a data block into a target instance, ported from SAGE's
 * {@code INIFieldParseProc}.
 *
 * <p>SAGE used a C function pointer plus a struct offset to know <em>where</em>
 * to store the parsed value. Java has no raw offsets, so the destination is
 * captured by the lambda itself — typically a setter on {@code instance} — which
 * is both type-safe and clearer. The parser pulls its value(s) from {@code ini}
 * via {@code getNextToken}/{@code scan*}.
 *
 * @param <T> the block instance type being populated
 */
@FunctionalInterface
public interface FieldParser<T> {

    void parse(Ini ini, T instance);
}
