package uz.dukeengine.core.data;

/** A {@code .duke} file the game cannot read. Every message starts with where: {@code file:line:}. */
public final class DataException extends RuntimeException {

    public DataException(String where, String message) {
        super(where + ": " + message);
    }

    public DataException(String where, String message, Throwable cause) {
        super(where + ": " + message, cause);
    }
}
