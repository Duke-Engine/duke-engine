package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component whose value is the {@code Name} of another block, one of {@link #value()}:
 * {@code Animations = Humanoid} names an {@code AnimationSet}. A thing many units share is declared
 * once and linked from each of them, rather than copied into every one.
 *
 * <p>The game resolves the name itself. This is for an editor: it offers the names there are, opens
 * the block a name is, says so when it is none, and follows the link — to the files a linked set's
 * {@link Clip}s come from.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.CLASS)
public @interface Link {

    /** The record the named block is. */
    Class<? extends Record> value();
}
