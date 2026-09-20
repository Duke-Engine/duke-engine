package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where a record's fields begin a new group: this component and the ones after it, up to the next
 * that names a group of its own — {@code @Group("Look") String model}.
 *
 * <p>The engine reads nothing of it. It is here for an editor, which shows a record of thirty
 * fields as a handful of titled groups rather than one long list, and so it is written once, on the
 * first field of each group, in the order the record already keeps.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.CLASS)
public @interface Group {

    /** The group's title. */
    String value();
}
