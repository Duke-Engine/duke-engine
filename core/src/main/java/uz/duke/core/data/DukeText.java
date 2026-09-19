package uz.duke.core.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the text of a {@code .duke} file into its blocks: the syntax, and nothing of what it means.
 *
 * <pre>
 * Monster                          ; a lone word opens a block
 *   Name = Brute                   ; Key = value
 *   KindOf = [INFANTRY, CAN_ATTACK]
 *   MoveUpdate                     ; a block inside a block
 *     Speed = 10
 *   End                            ; closes the innermost open block
 * End
 * </pre>
 *
 * <p>A key is written once per block; a list is one value, {@code [a, b]}, and may run over several
 * lines. {@code ;} starts a comment outside quotes, and a value holding a comma, a bracket or a
 * {@code ;} is quoted: {@code "like; this"}.
 *
 * <p>SAGE's INI reader is the ancestor: blocks closed by {@code End}, fields looked up by name. What
 * changed is the header, which is one word only, so every line says by its shape what it is.
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

    private static final class Open {
        final String word;
        final int line;
        final List<Field> fields = new ArrayList<>();
        final List<Block> blocks = new ArrayList<>();
        final Map<String, Integer> keys = new HashMap<>();

        Open(String word, int line) {
            this.word = word;
            this.line = line;
        }
    }

    private static final class Reader {
        private final String[] lines;
        private final String source;
        private final Deque<Open> open = new ArrayDeque<>();
        private final List<Block> top = new ArrayList<>();
        private int next;

        Reader(String text, String source) {
            this.lines = text.split("\r?\n", -1);
            this.source = source;
        }

        List<Block> blocks() {
            while (next < lines.length) {
                int line = next + 1;
                var text = uncommented(lines[next++], line).strip();
                if (text.isEmpty()) {
                    continue;
                }
                if (WORD.matcher(text).matches()) {
                    if (text.equalsIgnoreCase(END)) {
                        close(line);
                    } else {
                        open.push(new Open(text, line));
                    }
                    continue;
                }
                var field = FIELD.matcher(text);
                if (!field.matches()) {
                    var named = NAMED_HEADER.matcher(text);
                    throw error(line, named.matches()
                            ? "a block opens with one word; its name goes inside it: '" + named.group(1)
                                    + "', then 'Name = " + named.group(2) + "'"
                            : "expected a block's name, 'Key = value' or End, not '" + text + "'");
                }
                var into = open.peek();
                if (into == null) {
                    throw error(line, "'" + field.group(1) + "' stands outside any block");
                }
                var first = into.keys.putIfAbsent(field.group(1).toLowerCase(Locale.ROOT), line);
                if (first != null) {
                    throw error(line, "'" + field.group(1) + "' is written twice in '" + into.word
                            + "', first on line " + first + "; a list is one value, [a, b]");
                }
                into.fields.add(new Field(field.group(1), value(field.group(2).strip(), line), line));
            }
            if (!open.isEmpty()) {
                var unclosed = open.peek();
                throw error(unclosed.line, "'" + unclosed.word + "' has no End");
            }
            return top;
        }

        private void close(int line) {
            var closing = open.poll();
            if (closing == null) {
                throw error(line, "End with no block open");
            }
            var block = new Block(closing.word, closing.fields, closing.blocks, source, closing.line);
            var parent = open.peek();
            if (parent == null) {
                top.add(block);
            } else {
                parent.blocks.add(block);
            }
        }

        private Value value(String text, int line) {
            if (text.startsWith("[")) {
                return items(text, line);
            }
            if (text.startsWith("\"")) {
                int end = quoteEnd(text, 0, line);
                if (!text.substring(end).isBlank()) {
                    throw error(line, "nothing may follow a quoted value");
                }
                return new Value.Text(unquote(text.substring(0, end)));
            }
            return new Value.Text(text);
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

        private DataException error(int line, String message) {
            return new DataException(source + ":" + line, message);
        }
    }
}
