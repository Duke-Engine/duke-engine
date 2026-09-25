package uz.dukeengine.core.module;

import uz.dukeengine.core.thing.GameObject;

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

    /**
     * Told it has been taken off its thing — given up for a new order, swapped for another, anything that removes
     * it — on the simulation thread, before the thing's modules next update: so it can take back what it put on the
     * thing for as long as it was there, such as the words it set for its show. Nothing by default.
     */
    public void onRemoved() {
    }

    /**
     * Told once that its thing is made — after its owner, place and facing are set and before its first update — so
     * it may act on them at once where it would otherwise act a frame late: the reference's create modules, a unit
     * born a veteran or given an upgrade as it is made. On the simulation thread; not told again of a thing brought
     * back from a save. Nothing by default.
     */
    public void onCreated() {
    }

    /**
     * Whether this module has its thing at work where it stands — firing on something, loading, building, on an errand
     * — so that it is not asked to step aside for a mover, nor moved off ground it shares. Nothing by default.
     */
    public boolean keepsBusy() {
        return false;
    }
}
