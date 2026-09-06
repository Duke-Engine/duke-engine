package uz.duke.core.ini;

/** Thrown for any malformed-INI condition, ported from SAGE's {@code INIException}. */
public final class IniException extends RuntimeException {

    public IniException(String message) {
        super(message);
    }

    public IniException(String message, Throwable cause) {
        super(message, cause);
    }
}
