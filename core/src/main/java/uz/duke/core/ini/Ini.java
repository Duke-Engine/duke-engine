package uz.duke.core.ini;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The INI reader, ported from SAGE's {@code INI} class.
 *
 * <p>SAGE's data is plain text organised into blocks:
 * <pre>{@code
 * Object AmericaTankCrusader
 *   Side = America          ; a comment
 *   MaxHealth = 480.0
 * End
 * }</pre>
 * The first token on a line names a block type; a registered {@link BlockParser}
 * consumes the rest of the block up to its {@code End} token, populating an
 * instance via a {@link FieldParseTable}.
 *
 * <p>Tokenisation matches the original exactly: separators are space, tab and
 * {@code =}; everything after {@code ;} on a line is a comment; control
 * characters become spaces. {@link #getNextToken(String)} re-tokenises the rest
 * of the current line with a custom separator set, which the original relies on
 * for forms like {@code X:1.0 Y:2.0} and quoted strings.
 *
 * <p>This reader is deterministic: it only walks the supplied lines in order and
 * never touches wall-clock time, so the same file always yields the same data.
 */
public final class Ini {

    /** Block/field separators, ported from SAGE's {@code m_seps} (newlines handled per-line). */
    public static final String SEPS = " \t=";

    /** Separators that stop at a quote or {@code =} — for quoted-string scanning. */
    public static final String SEPS_QUOTE = "\"=";

    /** Separators including the colon — for {@code X:1.0} style sub-tokens. */
    public static final String SEPS_COLON = " \t=:";

    /** Parses a top-level block; ported from SAGE's {@code INIBlockParse}. */
    @FunctionalInterface
    public interface BlockParser {
        void parse(Ini ini);
    }

    private static final String DEFAULT_END_TOKEN = "End";

    private final List<String> lines;
    private final Map<String, BlockParser> blockParsers;
    private final String sourceName;

    private int lineIndex = -1;
    private int lineNum;
    private boolean eof;
    private String line = "";
    private int pos;
    private String blockEndToken = DEFAULT_END_TOKEN;
    private String currentBlock = "<none>";

    private Ini(List<String> lines, Map<String, BlockParser> blockParsers, String sourceName) {
        this.lines = lines;
        this.blockParsers = blockParsers;
        this.sourceName = sourceName;
    }

    /** A reader over the given text, dispatching to the given block parsers. */
    public static Ini of(String text, Map<String, BlockParser> blockParsers) {
        return of(text, blockParsers, "<string>");
    }

    public static Ini of(String text, Map<String, BlockParser> blockParsers, String sourceName) {
        return new Ini(splitLines(text), new HashMap<>(lowercaseKeys(blockParsers)), sourceName);
    }

    private static List<String> splitLines(String text) {
        // Keep empty lines so line numbers match the source; trailing-newline safe.
        return List.of(text.split("\n", -1));
    }

    private static Map<String, BlockParser> lowercaseKeys(Map<String, BlockParser> in) {
        var out = new HashMap<String, BlockParser>();
        for (var e : in.entrySet()) {
            out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
        return out;
    }

    /** Load every block in the source, dispatching each to its registered parser. */
    public void load() {
        while (readLine()) {
            var token = getNextTokenOrNull();
            if (token == null) {
                continue; // blank or comment-only line
            }
            var parser = blockParsers.get(token.toLowerCase(Locale.ROOT));
            if (parser == null) {
                throw new IniException(location() + " unknown block '" + token + "'");
            }
            currentBlock = token;
            parser.parse(this);
        }
    }

    /**
     * Populate {@code instance} from the current block's fields, reading lines
     * until the block's {@code End} token. The block parser calls this after it
     * has consumed the block's name token.
     */
    public <T> void initFromIni(T instance, FieldParseTable<T> table) {
        while (true) {
            if (!readLine()) {
                throw new IniException(location() + " missing '" + blockEndToken
                        + "' token for block '" + currentBlock + "'");
            }
            var field = getNextTokenOrNull();
            if (field == null) {
                continue;
            }
            if (field.equalsIgnoreCase(blockEndToken)) {
                return;
            }
            var parser = table.find(field);
            if (parser == null) {
                throw new IniException(location() + " unknown field '" + field
                        + "' in block '" + currentBlock + "'");
            }
            try {
                parser.parse(this, instance);
            } catch (IniException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IniException(location() + " error reading field '" + field + "'", e);
            }
        }
    }

    /** Advance to the next source line, stripping comments; false at EOF. */
    public boolean readLine() {
        if (lineIndex + 1 >= lines.size()) {
            eof = true;
            line = "";
            pos = 0;
            return false;
        }
        lineIndex++;
        lineNum++;
        line = sanitize(lines.get(lineIndex));
        pos = 0;
        return true;
    }

    private static String sanitize(String raw) {
        int semicolon = raw.indexOf(';');
        var body = semicolon >= 0 ? raw.substring(0, semicolon) : raw;
        var sb = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            sb.append(c >= 0 && c < 32 ? ' ' : c);
        }
        return sb.toString();
    }

    /** Next token using the default separators, or {@code null} if the line is spent. */
    public String getNextTokenOrNull() {
        return getNextTokenOrNull(SEPS);
    }

    /** Next token using {@code seps}, or {@code null} if the line is spent. */
    public String getNextTokenOrNull(String seps) {
        while (pos < line.length() && seps.indexOf(line.charAt(pos)) >= 0) {
            pos++;
        }
        if (pos >= line.length()) {
            return null;
        }
        int start = pos;
        while (pos < line.length() && seps.indexOf(line.charAt(pos)) < 0) {
            pos++;
        }
        return line.substring(start, pos);
    }

    /** Next token using the default separators; throws if there is none. */
    public String getNextToken() {
        return getNextToken(SEPS);
    }

    /** Next token using {@code seps}; throws if there is none. */
    public String getNextToken(String seps) {
        var token = getNextTokenOrNull(seps);
        if (token == null) {
            throw new IniException(location() + " expected another token on line in block '" + currentBlock + "'");
        }
        return token;
    }

    /**
     * Return the rest of the current line as a single trimmed string,
     * consuming it. For free-text fields (e.g. display names) that may contain
     * spaces, where token-by-token reading would stop at the first space.
     */
    public String getRestOfLine() {
        while (pos < line.length() && SEPS.indexOf(line.charAt(pos)) >= 0) {
            pos++;
        }
        if (pos >= line.length()) {
            return "";
        }
        var rest = line.substring(pos).strip();
        pos = line.length();
        return rest;
    }

    /**
     * Read a sub-token of the form {@code name:value} and return its value.
     * Ported from SAGE's {@code getNextSubToken}, used for {@code X:1 Y:2 Z:3}.
     */
    public String getNextSubToken(String expectedName) {
        var name = getNextToken(SEPS_COLON);
        if (!name.equalsIgnoreCase(expectedName)) {
            throw new IniException(location() + " expected sub-token '" + expectedName + "' but got '" + name + "'");
        }
        return getNextToken(SEPS_COLON);
    }

    public void setBlockEndToken(String endToken) {
        this.blockEndToken = endToken;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getLineNum() {
        return lineNum;
    }

    public boolean isEof() {
        return eof;
    }

    private String location() {
        return "[" + sourceName + ":" + lineNum + "]";
    }

    // ---- static scan helpers, ported from SAGE's INI::scan* ----

    /** Parse an integer token. */
    public static int scanInt(String token) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            throw new IniException("invalid integer '" + token + "'", e);
        }
    }

    /** Parse a real (float) token. SAGE's Real is 32-bit. */
    public static float scanReal(String token) {
        try {
            return Float.parseFloat(token.trim());
        } catch (NumberFormatException e) {
            throw new IniException("invalid real '" + token + "'", e);
        }
    }

    /** Parse a boolean: {@code Yes}/{@code No} (case-insensitive), per SAGE. */
    public static boolean scanBool(String token) {
        if (token.equalsIgnoreCase("yes")) {
            return true;
        }
        if (token.equalsIgnoreCase("no")) {
            return false;
        }
        throw new IniException("invalid boolean '" + token + "' -- expected Yes or No");
    }

    /** Parse an enum constant by name, case-insensitively. */
    public static <E extends Enum<E>> E scanEnum(Class<E> type, String token) {
        for (var constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(token)) {
                return constant;
            }
        }
        throw new IniException("invalid " + type.getSimpleName() + " '" + token + "'");
    }

    // ---- common field parsers, factory style ----

    /** A field parser that reads one token and stores it via {@code setter}. */
    public static <T> FieldParser<T> string(java.util.function.BiConsumer<T, String> setter) {
        return (ini, instance) -> setter.accept(instance, ini.getNextToken());
    }

    /** A field parser that stores the rest of the line (may contain spaces). */
    public static <T> FieldParser<T> restOfLine(java.util.function.BiConsumer<T, String> setter) {
        return (ini, instance) -> setter.accept(instance, ini.getRestOfLine());
    }

    public static <T> FieldParser<T> integer(java.util.function.ObjIntConsumer<T> setter) {
        return (ini, instance) -> setter.accept(instance, scanInt(ini.getNextToken()));
    }

    public static <T> FieldParser<T> real(java.util.function.BiConsumer<T, Float> setter) {
        return (ini, instance) -> setter.accept(instance, scanReal(ini.getNextToken()));
    }

    public static <T> FieldParser<T> bool(java.util.function.BiConsumer<T, Boolean> setter) {
        return (ini, instance) -> setter.accept(instance, scanBool(ini.getNextToken()));
    }

    public static <T, E extends Enum<E>> FieldParser<T> enumeration(
            Class<E> type, java.util.function.BiConsumer<T, E> setter) {
        return (ini, instance) -> setter.accept(instance, scanEnum(type, ini.getNextToken()));
    }

    /** Convenience for building a small block registry inline. */
    public static Map<String, BlockParser> registry() {
        return new HashMap<>();
    }
}
