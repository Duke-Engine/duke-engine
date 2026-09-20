package uz.dukeengine.core.module;

import java.util.List;

/**
 * The module families any game has, as {@link ModuleGroup} spells them, and the way to
 * read a module's families back.
 *
 * <p>Like {@code RtsKinds}, a spelling contract rather than a closed set: {@code rts}
 * adds the families every RTS has, and a game may name its own.
 */
public final class ModuleGroups {

    /** Moving under its own power, or being carried somewhere. */
    public static final String MOVEMENT = "Movement";

    /** Health: holding it, losing it, getting it back. */
    public static final String BODY = "Body";

    /** Attacking, weapons and damage. */
    public static final String COMBAT = "Combat";

    /** Something that lasts a while: a status, a mark on the floor, a summoned creature's time. */
    public static final String EFFECT = "Effect";

    /** Behaviour decided by a script. */
    public static final String SCRIPT = "Script";

    private ModuleGroups() {
    }

    /** The families {@code module} is filed under, in the order it names them; empty if none. */
    public static List<String> of(Class<? extends Module> module) {
        var groups = module.getAnnotation(ModuleGroup.class);
        return groups == null ? List.of() : List.of(groups.value());
    }
}
