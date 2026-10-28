package uz.dukeengine.core.data;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Everything the {@link Binder} needs to know about one type, said once: what a record is made of, which
 * records a sealed type permits, the {@code of(String)} or {@code valueOf(String)} it is read with, and an
 * enum's constants. Each part is {@code null} where the type has no such thing.
 *
 * <p>On the JVM reflection answers all of it ({@link Shapes#reflected}). Where reflection cannot — a browser —
 * the shapes are written out at compile time by the engine's annotation processor and found through
 * {@link ShapeSource}; the binder reads both the same way and cannot tell them apart.
 */
public record Shape(Components components, List<Class<?>> permitted, Function<String, ?> factory,
        List<?> constants) {

    /**
     * A record's components in the order its header declares them: their names, their types as written and
     * as erased, the runtime annotations on each, how to build one from all its values and read them back, and
     * its {@code static final DEFAULTS}, or {@code null} where it has none.
     *
     * <p>{@code build} throws what the record's constructor throws, unwrapped.
     */
    public record Components(List<String> names, List<TypeRef> generics, List<Class<?>> types,
            List<List<Annotation>> marks, Function<Object[], Object> build, Function<Object, Object[]> read,
            Supplier<Object> defaults) {
    }
}
