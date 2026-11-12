package emul.java.util;

public final class TStringJoiner {

    private final String delimiter;
    private final String prefix;
    private final String suffix;
    private StringBuilder joined;
    private String emptyValue;

    public TStringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public TStringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        this.delimiter = delimiter.toString();
        this.prefix = prefix.toString();
        this.suffix = suffix.toString();
    }

    public TStringJoiner setEmptyValue(CharSequence emptyValue) {
        this.emptyValue = emptyValue.toString();
        return this;
    }

    public TStringJoiner add(CharSequence part) {
        if (joined == null) {
            joined = new StringBuilder();
        } else {
            joined.append(delimiter);
        }
        joined.append(part);
        return this;
    }

    public int length() {
        return toString().length();
    }

    @Override
    public String toString() {
        if (joined == null && emptyValue != null) {
            return emptyValue;
        }
        return prefix + (joined == null ? "" : joined) + suffix;
    }
}
