package uz.dukeengine.core.data;

import java.util.List;

/**
 * A component's type as the {@link Binder} reads it: the class, and what it is a list, set or map of.
 *
 * <p>Reflection gives this as a {@code ParameterizedType}, which not every platform the engine may run on can
 * answer, so a {@link Shape} says it in plain classes. Only what the binder looks at is kept: a wildcard is its
 * bound's class with nothing inside it, and a type variable is {@code Object} — as the binder always read them.
 */
public record TypeRef(Class<?> raw, List<TypeRef> arguments) {

    public static final TypeRef OBJECT = new TypeRef(Object.class);

    public TypeRef {
        arguments = List.copyOf(arguments);
    }

    public TypeRef(Class<?> raw) {
        this(raw, List.of());
    }
}
