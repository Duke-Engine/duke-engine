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

    /** Go to {@code destination}: on the ground, to the nearest place round it of its own — see {@code MoveUpdate}. */
    default void moveTo(Coord3D destination) {
    }

    /**
     * Go exactly to {@code destination}: a move into something — entering it, docking at it, closing on it — which
     * takes no place of its own there. A mover that keeps none goes as {@link #moveTo} goes.
     */
    default void moveExactlyTo(Coord3D destination) {
        moveTo(destination);
    }

    /**
     * Walk straight to {@code way}, out of whatever it was made inside, and then on to {@code destination}: how a
     * thing leaves its maker through the door. A mover with no such first leg goes straight to the destination.
     */
    default void leave(Coord3D way, Coord3D destination) {
        moveTo(destination);
    }

    /**
     * Go through the points of {@code way} in turn, exactly, and then to {@code place} — held as its own from the start:
     * a group's shared route walked in columns ({@code aiFollowPath}). A mover with no ground of its own goes straight to
     * the place.
     */
    default void moveThrough(java.util.List<Coord3D> way, Coord3D place) {
        moveTo(place);
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
    /**
     * How near the end of its way it counts as there — the reference's {@code CloseEnoughDist}: a mover an errand sent
     * beside a thing is beside it once it stands this near the point it was sent to ({@code World.isBeside}). None
     * for a mover that says nothing.
     */
    default float closeEnough() {
        return 0f;
    }

    /**
     * How fast it moved over the last frame, world units a second — what a client paces a walk's clip to, taken from
     * the simulation so every machine agrees. None for a mover that says nothing.
     */
    default float speedMoved() {
        return 0f;
    }

    default boolean flies() {
        return false;
    }

    /**
     * Go at {@code speed} (world units a second) and turn at {@code turnRate} (degrees a second) from now on, keeping
     * where it is going, the way it takes there and its first leg out of its maker — the reference's locomotor set
     * changed by an upgrade. Callable from any module's update, its own thing's included.
     */
    default void setSpeed(float speed, float turnRate) {
    }
}
