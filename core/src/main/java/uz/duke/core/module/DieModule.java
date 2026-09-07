package uz.duke.core.module;

/**
 * Runs when its object dies, ported from SAGE's {@code DieModule}.
 *
 * <p>This is the gameplay half of death: leave wreckage, damage everything
 * nearby, hand a bounty to the killer. It is called after the object has left
 * the world, so anything it spawns lands in a world that no longer contains the
 * corpse.
 *
 * <p>The presentation half is {@link uz.duke.core.event.ObjectDied}, which is
 * posted for the same death. Keep them apart: a die module changes the
 * simulation and must be deterministic; an event only tells a renderer
 * something happened and never affects the outcome.
 */
public interface DieModule {

    /** Called once, when the object's death is final. */
    void onDie();
}
