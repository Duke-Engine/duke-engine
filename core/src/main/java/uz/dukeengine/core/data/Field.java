package uz.dukeengine.core.data;

/** {@code Key = value}, and the line it stands on. */
public record Field(String key, Value value, int line) {
}
