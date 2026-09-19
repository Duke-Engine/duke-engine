package uz.duke.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component whose value names an animation clip: a name inside one of the model or animation
 * files the thing is drawn from — {@code Walk = Walking_A}.
 *
 * <p>The engine reads it as the string it is. It is here for an editor, which reads the names out of
 * those files — the ones written in the block and around it, and the ones of what it {@link Link}s —
 * and offers them, so a clip is picked rather than remembered.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.CLASS)
public @interface Clip {
}
