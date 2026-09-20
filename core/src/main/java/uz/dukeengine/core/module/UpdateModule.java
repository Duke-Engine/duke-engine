package uz.dukeengine.core.module;

import uz.dukeengine.core.thing.GameObject;

/**
 * A module that does per-frame work, ported from SAGE's {@code UpdateModule}.
 *
 * <p>Every {@link UpdateModule} on every live object is ticked once per logic
 * frame, in a deterministic order. This is where movement, AI, weapon cooldowns
 * and the like live. Keep {@link #update()} free of wall-clock reads and
 * unordered iteration so the simulation stays reproducible.
 */
public abstract class UpdateModule extends Module {

    protected UpdateModule(GameObject owner) {
        super(owner);
    }

    public abstract void update();
}
