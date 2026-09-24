package uz.dukeengine.core.module;

import uz.dukeengine.core.math.Coord3D;

/**
 * Marks a module that can move its object under its own power, ported in name
 * from SAGE's {@code Locomotor}.
 *
 * <p>The engine needs to answer one question it cannot otherwise ask: <em>is this
 * object part of the terrain?</em> Something that never moves can be baked into
 * the navigation grid so paths route around it; something that walks must not be,
 * or it would wall itself in.
 *
 * <p>Asking "is it a building?" would mean {@code core} knowing a game's
 * vocabulary. Asking "can anything move it?" is genre-neutral and simply true:
 * an object with no locomotor cannot move, whatever the game calls it. A game
 * that writes its own movement module implements this interface and the engine
 * classifies it correctly with no further wiring.
 *
 * <p>It is also how anything that gives orders talks to whatever moves a thing — walking over the ground
 * ({@link MoveUpdate}) or flying ({@link FlyUpdate}) — so an order, a weapon and a computer player treat the two
 * the same. A mover that takes no orders leaves these as they are: told to go, it does not.
 */
public interface Locomotor {

    /** Go to {@code destination}. */
    default void moveTo(Coord3D destination) {
    }

    /**
     * Walk straight to {@code way}, out of whatever it was made inside, and then on to {@code destination}: how a
     * thing leaves its maker through the door. A mover with no such first leg goes straight to the destination.
     */
    default void leave(Coord3D way, Coord3D destination) {
        moveTo(destination);
    }

    /** Stop where it is — or, for a thing that cannot stop in the air, circle there. */
    default void stop() {
    }

    /** Whether it is on its way somewhere it has not reached. */
    default boolean isMoving() {
        return false;
    }

    /** Whether it gave up short of where it was sent, having got as near as it can. */
    default boolean stoppedShort() {
        return false;
    }

    /** Whether it goes through the air, over what blocks the ground — and so straight to what it closes on. */
    default boolean flies() {
        return false;
    }
}
