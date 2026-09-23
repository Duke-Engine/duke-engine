package uz.dukeengine.core.module;

/**
 * Runs when its object dies, ported from SAGE's {@code DieModule}.
 *
 * <p>This is the gameplay half of death: leave wreckage, damage everything
 * nearby, hand a bounty to the killer. It is called after the object has left
 * the world, so anything it spawns lands in a world that no longer contains the
 * corpse.
 *
 * <p>The presentation half is {@link uz.dukeengine.core.event.ObjectDied}, which is
 * posted for the same death. Keep them apart: a die module changes the
 * simulation and must be deterministic; an event only tells a renderer
 * something happened and never affects the outcome.
 *
 * <p>It is told how the death came — the death type the killing blow dealt and
 * whose blow it was — because that is what the reference's die modules decide
 * by: in the RTS this was measured in, 746 of them answer every death but being
 * run over, 148 only burning, and a wreck is left for a tank shelled and not for
 * one crushed.
 */
public interface DieModule {

    /** Called once, when the object's death is final, told how it came. */
    default void onDie(Death death) {
        onDie();
    }

    /** The form every die module was written against before a death said how it came. */
    default void onDie() {
    }
}
