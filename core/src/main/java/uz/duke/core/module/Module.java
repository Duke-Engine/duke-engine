package uz.duke.core.module;

import uz.duke.core.thing.GameObject;

/**
 * A composable behaviour attached to a {@link GameObject}, ported from SAGE's
 * {@code Module}.
 *
 * <p>SAGE objects have almost no hard-coded behaviour of their own; what an
 * object <em>does</em> is the sum of its modules — a body that holds health, an
 * update that moves it, a die module that spawns wreckage, and so on. This
 * composition-over-inheritance design is what lets thousands of unit types be
 * defined entirely in data.
 */
public abstract class Module {

    private final GameObject owner;

    protected Module(GameObject owner) {
        this.owner = owner;
    }

    public final GameObject getOwner() {
        return owner;
    }
}
