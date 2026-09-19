package uz.duke.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that is a map's cells: a row of characters for each row of cells, one character a
 * cell — {@code Cells = ["#####", "#000#", …]}.
 *
 * <p>The engine reads it as the strings it is. It is here for an editor, which draws the rows as
 * the map they are, and every other component of the record that holds a cell — a record with an
 * {@code x} and a {@code y} — as a thing standing on it, placed and moved there by hand. A thing
 * that names what it is, {@code Skeleton 17 16}, names it by the {@link Link} on its component.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.CLASS)
public @interface Grid {
    /** The characters that are rock: drawn as it, and nothing is put down on them. */
    String solid() default "#";
}
