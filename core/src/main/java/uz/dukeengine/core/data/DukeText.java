package uz.dukeengine.core.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads the text of a {@code .duke} file into its blocks: the syntax, and nothing of what it means.
 *
 * <pre>
 * Monster                            ; a line that is one word opens a block
 *   Name = Brute                     ; Key = value
 *   KindOf = [INFANTRY, CAN_ATTACK]  ; a list of values
 *   Geometry = Cylinder              ; a record, named by its class, its fields under it
 *     Radius = 6
 *     Height = 16
 *   End
 *   Modules = [                      ; a list of records, each named by its class
 *     MoveUpdate
 *       Speed = 10
 *     End
 *   ]
 * End                                ; closes the innermost open block
 * </pre>
 *
 * <p>A key is written once per block. A list of values may run over several lines, and a value
 * holding a comma, a bracket or a {@code ;} is quoted: {@code "like; this"}. {@code ;} starts a
 * comment outside quotes. A word alone inside a block opens a block of entries, named by its field:
 * {@code Armor} holding {@code FLAME = 0.5}.
 *
 * <p>{@code Key = Word} opens a block only when the next line is indented deeper; otherwise the word
 * is a value, as {@code Geometry = Sphere} is a sphere with nothing written in it, and a block so
 * opened keeps to that depth to its End. A {@code [} alone on its line holds blocks when its first
 * item is a word with its fields under it, or its End. Those are the only places indentation means
 * anything.
 *
 * <p>SAGE's INI reader is the ancestor: blocks closed by {@code End}, fields looked up by name. What
 * changed is that every line says by its shape what it is.
 */
public final class DukeText {

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern FIELD = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=(.*)");
    /** {@code Monster Brute}: the INI habit of naming a block in its header. */
    private static final Pattern NAMED_HEADER = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s+(\\S+)");
    private static final String END = "End";

    private DukeText() {
    }

    /** Every block {@code text} holds, in order; {@code source} is how errors name the text. */
    public static List<Block> parse(String text, String source) {
        return new Reader(text, source).blocks();
    }

    /** A line with code on it: its number, how deep it is indented, and its code without the comment. */
    private record Line(int number, int indent, String code) {

        boolean isWord() {
            return WORD.matcher(code).matches();
        }

        boolean isEnd() {
            return code.equalsIgnoreCase(END);
        }
    }

    private static final class Reader {
        private final String[] lines;
        private final String source;
        private int next;

        Reader(String text, String source) {
            this.lines = text.split("\r?\n", -1);
            this.source = source;
        }

        List<Block> blocks() {
            var top = new ArrayList<Block>();
            for (var line = nextCode(); line != null; line = nextCode()) {
                if (line.isEnd()) {
                    throw error(line.number(), "End with no block open");
                }
                if (line.isWord()) {
                    top.add(block(line.code(), line));
                    continue;
                }
                var field = FIELD.matcher(line.code());
                if (field.matches()) {
                    throw error(line.number(), "'" + field.group(1) + "' stands outside any block");
                }
                throw unreadable(line);
            }
            return top;
        }

        /** The block {@code opening} opens, called {@code word}: its fields and the blocks in it, to its End. */
        private Block block(String word, Line opening) {
            return block(word, opening, null);
        }

        /**
         * A block to its End. One written after a field's {@code =} ({@code key} its field) was opened by
         * the depth of the line under it, so it keeps to that: every line of it deeper than its first, and
         * its End no shallower. A line back at the field's own depth means the End is missing — or that
         * the word was a value and the line under it was indented by mistake — and it is said there,
         * rather than where the End it would otherwise take from the block around it runs out.
         */
        private Block block(String word, Line opening, String key) {
            var fields = new ArrayList<Field>();
            var blocks = new ArrayList<Block>();
            var keys = new HashMap<String, Integer>();
            for (var line = nextCode(); line != null; line = nextCode()) {
                if (key != null && (line.indent() < opening.indent()
                        || line.indent() == opening.indent() && !line.isEnd())) {
                    throw noEnd(word, opening, key);
                }
                if (line.isEnd()) {
                    return new Block(word, fields, blocks, source, opening.number());
                }
                if (line.isWord()) {
                    blocks.add(block(line.code(), line));
                    continue;
                }
                var field = FIELD.matcher(line.code());
                if (!field.matches()) {
                    throw unreadable(line);
                }
                var name = field.group(1);
                var first = keys.putIfAbsent(name.toLowerCase(Locale.ROOT), line.number());
                if (first != null) {
                    throw error(line.number(), "'" + name + "' is written twice in '" + word
                            + "', first on line " + first + "; a list is one value, [a, b]");
                }
                fields.add(new Field(name, value(name, field.group(2).strip(), line), line.number()));
            }
            throw key == null ? error(opening.number(), "'" + word + "' has no End") : noEnd(word, opening, key);
        }

        private DataException noEnd(String word, Line opening, String key) {
            return error(opening.number(), "'" + word + "' has no End; if " + word + " is a value, the line under '"
                    + key + " = " + word + "' is indented too deep");
        }

        private Value value(String key, String text, Line line) {
            if (text.equals("[") && itemsAreBlocks()) {
                return new Value.NestedList(blockList(key, line));
            }
            if (text.startsWith("[")) {
                return items(text, line.number());
            }
            if (text.startsWith("\"")) {
                int end = quoteEnd(text, 0, line.number());
                if (!text.substring(end).isBlank()) {
                    throw error(line.number(), "nothing may follow a quoted value");
                }
                return new Value.Text(unquote(text.substring(0, end)));
            }
            if (WORD.matcher(text).matches() && deeper(line)) {
                return new Value.Nested(block(text, line, key));
            }
            return new Value.Text(text);
        }

        /** Whether the next line with code is indented deeper than {@code line}, and so is its body. */
        private boolean deeper(Line line) {
            int at = codeFrom(next);
            return at >= 0 && lineAt(at).indent() > line.indent();
        }

        /** Whether the list a lone {@code [} opens holds blocks: its first item is a word with a body or an End. */
        private boolean itemsAreBlocks() {
            int first = codeFrom(next);
            if (first < 0 || !lineAt(first).isWord() || lineAt(first).isEnd()) {
                return false;
            }
            int second = codeFrom(first + 1);
            return second >= 0 && (lineAt(second).indent() > lineAt(first).indent() || lineAt(second).isEnd());
        }

        /** A block for each item, each closed by its End, then {@code ]} on a line of its own. */
        private List<Block> blockList(String key, Line opening) {
            var blocks = new ArrayList<Block>();
            for (var line = nextCode(); line != null; line = nextCode()) {
                if (line.code().equals("]")) {
                    return blocks;
                }
                if (!line.isWord() || line.isEnd()) {
                    throw error(line.number(), "'" + key + "' is a list of blocks, each closed by End,"
                            + " and ends with ']' on a line of its own, not '" + line.code() + "'");
                }
                blocks.add(block(line.code(), line));
            }
            throw error(opening.number(), "'" + key + " = [' is never closed by ']'");
        }

        /** {@code [a, b, c]}, read on over as many lines as it takes to close. */
        private Value items(String first, int line) {
            var text = new StringBuilder(first);
            int close;
            while ((close = closingBracket(text, line)) < 0) {
                if (next >= lines.length) {
                    throw error(line, "'[' is never closed by ']'");
                }
                text.append('\n').append(uncommented(lines[next], next + 1));
                next++;
            }
            if (!text.substring(close + 1).isBlank()) {
                throw error(line, "nothing may follow ']'");
            }
            var items = new ArrayList<String>();
            var inner = text.substring(1, close);
            int start = 0;
            for (int i = 0; i <= inner.length(); i++) {
                if (i < inner.length() && inner.charAt(i) == '"') {
                    i = quoteEnd(inner, i, line) - 1;
                    continue;
                }
                if (i < inner.length() && inner.charAt(i) != ',') {
                    continue;
                }
                var item = inner.substring(start, i).strip();
                start = i + 1;
                boolean last = i == inner.length();
                if (item.isEmpty()) {
                    // [] is empty, and a trailing comma closes nothing.
                    if (last) {
                        break;
                    }
                    throw error(line, "an empty item in a list");
                }
                items.add(item(item, line));
            }
            return new Value.Items(items);
        }

        private String item(String item, int line) {
            if (!item.startsWith("\"")) {
                if (item.indexOf('\n') >= 0) {
                    throw error(line, "a comma is missing between the items of a list");
                }
                return item;
            }
            if (quoteEnd(item, 0, line) != item.length()) {
                throw error(line, "nothing may follow a quoted item");
            }
            return unquote(item);
        }

        /** Where the list's ']' is, skipping quoted text; -1 while it is still open. */
        private int closingBracket(CharSequence text, int line) {
            for (int i = 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"') {
                    i = quoteEnd(text, i, line) - 1;
                } else if (c == '[') {
                    throw error(line, "a list inside a list");
                } else if (c == ']') {
                    return i;
                }
            }
            return -1;
        }

        /** The next line with code on it, which it moves past; null at the end of the text. */
        private Line nextCode() {
            int at = codeFrom(next);
            if (at < 0) {
                next = lines.length;
                return null;
            }
            next = at + 1;
            return lineAt(at);
        }

        /** The index of the first line from {@code from} with code on it, or -1. */
        private int codeFrom(int from) {
            for (int i = from; i < lines.length; i++) {
                if (!uncommented(lines[i], i + 1).isBlank()) {
                    return i;
                }
            }
            return -1;
        }

        private Line lineAt(int index) {
            var raw = lines[index];
            int indent = 0;
            while (indent < raw.length() && (raw.charAt(indent) == ' ' || raw.charAt(indent) == '\t')) {
                indent++;
            }
            return new Line(index + 1, indent, uncommented(raw, index + 1).strip());
        }

        /** The line up to its {@code ;}, a {@code ;} inside quotes being text. */
        private String uncommented(String raw, int line) {
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '"') {
                    i = quoteEnd(raw, i, line) - 1;
                } else if (c == ';') {
                    return raw.substring(0, i);
                }
            }
            return raw;
        }

        /** Just past the quote that closes the one at {@code start}. */
        private int quoteEnd(CharSequence text, int start, int line) {
            for (int i = start + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    return i + 1;
                }
            }
            throw error(line, "a quote is never closed");
        }

        private static String unquote(String quoted) {
            var text = new StringBuilder(quoted.length());
            for (int i = 1; i < quoted.length() - 1; i++) {
                char c = quoted.charAt(i);
                if (c == '\\' && i + 1 < quoted.length() - 1) {
                    c = quoted.charAt(++i);
                }
                text.append(c);
            }
            return text.toString();
        }

        private DataException unreadable(Line line) {
            if (line.code().equals("]")) {
                return error(line.number(), "']' with no list of blocks open");
            }
            var named = NAMED_HEADER.matcher(line.code());
            return error(line.number(), named.matches()
                    ? "a block opens with one word; its name goes inside it: '" + named.group(1)
                            + "', then 'Name = " + named.group(2) + "'"
                    : "expected a block's name, 'Key = value' or End, not '" + line.code() + "'");
        }

        private DataException error(int line, String message) {
            return new DataException(source + ":" + line, message);
        }
    }
}
