package emul.uz.dukeengine.core.data;

import uz.dukeengine.core.data.Shape;

/**
 * {@code ReflectedShapes} in a browser, where a record cannot be read by reflection: a type with no written or
 * built-in shape is an error that names it.
 */
public final class TReflectedShapes {

    private TReflectedShapes() {
    }

    public static Shape of(Class<?> type) {
        throw new IllegalStateException("no shape for " + type.getName() + ": in a browser every type the binder"
                + " reads needs one written at compile time (a private record gets none)");
    }
}
