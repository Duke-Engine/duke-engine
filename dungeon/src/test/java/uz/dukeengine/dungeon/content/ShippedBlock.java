package uz.dukeengine.dungeon.content;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One unit's block as the shipped files write it, for a test that changes a line or two and
 * keeps the rest: a healer that mends at another threshold is still the healer it was.
 *
 * <p>A block a test writes from nothing replaces the shipped one of its name, and whatever it
 * leaves out is the record's default. This is the other half, for "the shipped one, except".
 */
public record ShippedBlock(String text) {

    /** Every shipped block, with the one {@code name} names setting {@code key} to {@code value}. */
    public static String dataWith(String name, String key, Object value) {
        var shipped = of(name);
        return Content.data().replace(shipped.text(), shipped.with(key, value).text());
    }

    /** The unit's block, from the word that opens it to its {@code End}. */
    public static ShippedBlock of(String unit) {
        var data = Content.data();
        int name = data.indexOf("\n  Name = " + unit + "\n");
        if (name < 0) {
            throw new AssertionError("the shipped files have no unit called " + unit);
        }
        int start = data.lastIndexOf('\n', name - 1) + 1;
        return new ShippedBlock(data.substring(start, data.indexOf("\nEnd\n", name) + "\nEnd\n".length()));
    }

    /** The same block with the one line that sets {@code key}, at whatever depth, saying {@code value}. */
    public ShippedBlock with(String key, Object value) {
        return new ShippedBlock(onlyLine(key)
                .replaceFirst(match -> Matcher.quoteReplacement(match.group(1) + key + " = " + value)));
    }

    /** What the one line that sets {@code key}, at whatever depth, says. */
    public String value(String key) {
        var line = onlyLine(key);
        line.find();
        return line.group(2).strip();
    }

    /** The line that sets {@code key}: a block that sets it twice, or never, is not the one the test means. */
    private Matcher onlyLine(String key) {
        var line = Pattern.compile("(?m)^( *)" + Pattern.quote(key) + " = (.*)$");
        long lines = line.matcher(text).results().count();
        if (lines != 1) {
            throw new AssertionError("the block sets " + key + " " + lines + " times, not once:\n" + text);
        }
        return line.matcher(text);
    }
}
