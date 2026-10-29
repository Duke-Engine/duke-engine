package uz.dukeengine.core.save;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A module class declared to hold nothing a save needs: everything it reads is its data, its thing or its world, so a
 * fresh one from its template goes on exactly as the saved one would. Its thing's save names it and writes nothing for
 * it.
 *
 * <p>Not inherited: a subclass that adds a field of its own declares again, or writes it as a {@link Saveable}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Stateless {
}
