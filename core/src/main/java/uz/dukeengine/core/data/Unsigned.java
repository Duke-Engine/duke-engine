package uz.dukeengine.core.data;

/**
 * {@code Integer.parseUnsignedInt} and {@code Long.parseUnsignedLong} written out, with the JDK's results, for
 * the platforms whose class library lacks them (TeaVM).
 */
public final class Unsigned {

    private Unsigned() {
    }

    /** As the JDK's {@code Integer.parseUnsignedInt}. */
    public static int parseInt(String text, int radix) {
        long value = parseLong(text, radix);
        if (value >>> 32 != 0) {
            throw new NumberFormatException("String value " + text + " exceeds range of unsigned int.");
        }
        return (int) value;
    }

    /** As the JDK's {@code Long.parseUnsignedLong}. */
    public static long parseLong(String text, int radix) {
        int start = text.startsWith("+") ? 1 : 0;
        if (start == text.length() || text.startsWith("-")) {
            throw new NumberFormatException("For input string: \"" + text + "\"");
        }
        long limit = Long.divideUnsigned(-1L, radix);
        long value = 0;
        for (int i = start; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), radix);
            if (digit < 0) {
                throw new NumberFormatException("For input string: \"" + text + "\"");
            }
            long next = value * radix + digit;
            if (Long.compareUnsigned(value, limit) > 0 || Long.compareUnsigned(next, value * radix) < 0) {
                throw new NumberFormatException("String value " + text + " exceeds range of unsigned long.");
            }
            value = next;
        }
        return value;
    }
}
