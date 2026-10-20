package uz.dukeengine.core.module;

/**
 * A way of walking that takes the place of a route: handed to a mover ({@link MoveUpdate#steerBy}), it is asked for
 * each frame's step for as long as the mover walks by it — until the mover is stopped or sent somewhere. Asked only on
 * a frame the mover may walk: not while it is held, disabled, inside something or busy at work. The mover counts as
 * moving all the while, so it is drawn walking and its weapon fires as it would on the move.
 */
@FunctionalInterface
public interface Steering {

    /**
     * One frame of the walk: {@code step} is how far the mover goes this frame, slowed and quickened as a step along a
     * route is. Called on the simulation thread, so whatever it decides must be deterministic.
     *
     * @return whether it went anywhere — a step taken goes at {@code step}'s speed, none stands still
     */
    boolean step(MoveUpdate legs, float step);
}
