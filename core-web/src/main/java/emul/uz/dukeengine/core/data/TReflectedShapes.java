package emul.uz.dukeengine.core.data;

import java.util.List;
import uz.dukeengine.core.data.Shape;

/**
 * {@code ReflectedShapes} in a browser, where a record cannot be read by reflection. An open interface
 * ({@code ModuleData}, {@code Trigger.Action}) gets no written shape and needs none: the binder reads it through
 * its vocabulary. An enum from a module with no shape processor (the renderer's {@code Quality}) is read off its
 * constants, which a browser keeps. Any other type with no written or built-in shape is an error that names it.
 */
public final class TReflectedShapes {

    private static final Shape OPEN = new Shape(null, null, null, null);

    private TReflectedShapes() {
    }

    public static Shape of(Class<?> type) {
        if (type.isInterface()) {
            return OPEN;
        }
        if (type.isEnum()) {
            var constants = List.of(type.getEnumConstants());
            return new Shape(null, null, name -> constants.stream().filter(c -> ((Enum<?>) c).name().equals(name))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "No enum constant " + type.getName() + "." + name)), constants);
        }
        throw new IllegalStateException("no shape for " + type.getName() + ": in a browser every type the binder"
                + " reads needs one written at compile time (a private record gets none)");
    }
}
