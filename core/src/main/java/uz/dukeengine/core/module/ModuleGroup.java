package uz.dukeengine.core.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The families a module belongs to — what a tool offering modules, such as a wizard
 * adding one to a unit, files it under. The first is the one it is best known by.
 *
 * <p>Classification only: nothing in the simulation reads it. Whether a module runs
 * every frame is not a family either; that is {@link UpdateModule}, and the class
 * hierarchy already says it.
 *
 * <p>Names rather than an enum, for the reason {@link uz.dukeengine.core.thing.Kind} gives:
 * the engine cannot know every family a genre will want. {@link ModuleGroups} spells
 * the ones any game has, and a genre or a game adds its own the same way.
 *
 * <p>Inherited, so a subclass of a module is the same kind of module until it says
 * otherwise.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleGroup {
    String[] value();
}
