package uz.dukeengine.core.save;

/**
 * A save refused, or a load, because a module cannot be written or made again: neither {@link Saveable} nor declared
 * {@link Stateless}, added while the match ran with no reviver, or written differently from how it is read. It names the
 * module's class and the thing it is on.
 */
public final class UnsavedModuleException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String module;

    public UnsavedModuleException(String module, String message) {
        super(message);
        this.module = module;
    }

    /** The module's class, by its full name. */
    public String module() {
        return module;
    }
}
