package uz.dukeengine.core.module;

import java.util.function.Predicate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;

/**
 * What a ground mover's step meets and how its walk is measured: stone, solid things and scenery in the way, the floor
 * a step leads to, and the turns and distances every part of a {@link MoveUpdate} reckons by — through
 * {@link StrictMath}, so every peer gets the same bits.
 */
final class Steps {

    /** Half a full turn, in radians. */
    static final float HALF_TURN = (float) StrictMath.PI;

    private Steps() {
    }

    /** Whether a thing is a ground mover that keeps cells: walks, has a body, and is on the ground now. */
    static boolean isGroundMover(GameObject thing) {
        var world = thing.getWorld();
        return thing.getLocomotor() instanceof MoveUpdate && world != null && world.keepsCells(thing);
    }

    static boolean keepsCells(GameObject owner) {
        var world = owner.getWorld();
        return world != null && world.keepsCells(owner);
    }

    static float cellSize(GameObject owner) {
        var world = owner.getWorld();
        return world == null ? PathGrid.DEFAULT_CELL_SIZE : world.cellSize();
    }

    /**
     * Whether a mover may not stand at {@code position} — someone in the way, or
     * the ground itself.
     *
     * <p>Terrain used to be left entirely to pathfinding, on the reasoning that a
     * route never crosses a wall. That holds only while the mover follows its
     * route: swerving around a neighbour is a step the path never planned, and in
     * a corridor barely wider than the mover the only way past is sideways into
     * stone. Nothing refused it, and once a mover's centre was inside a wall it
     * could never leave — a search that starts on blocked ground has nowhere to
     * begin, so it returned no path at all, for ever.
     *
     * <p>A mover already standing in stone is not held there. Refusing its steps
     * too would make the wall it should be escaping into a cage.
     */
    static boolean isBlocked(GameObject mover, Coord3D position, int toward) {
        return mover.getWorld() != null && refused(mover, position, toward, candidate -> false);
    }

    /** Whether something solid — anything but a ground mover — or the ground itself refuses a step there. */
    static boolean solidInTheWay(GameObject owner, Coord3D to, int toward) {
        return refused(owner, to, toward, Steps::isGroundMover);
    }

    private static boolean refused(GameObject mover, Coord3D to, int toward, Predicate<GameObject> passing) {
        var world = mover.getWorld();
        if (world.findBlocker(mover, to, passing) != null || world.sceneryInTheWay(mover, mover.getPosition(), to)) {
            return true;
        }
        // Stone, or a floor this one is not joined to. A step onto a higher floor
        // is refused for the same reason a wall is: from here, it is not ground.
        return !world.canStep(mover, mover.getPosition(), to, toward)
                && !world.isGroundBlocked(mover, mover.getPosition());
    }

    /** A step to {@code position}: onto the floor the step leads to — a deck's, at its entry — and at its height. */
    static void stepTo(GameObject mover, Coord3D position, int toward) {
        var world = mover.getWorld();
        if (world != null) {
            mover.setFloor(world.floorAfter(mover, mover.getPosition(), position, toward));
        }
        mover.setPosition(onGround(mover, position));
    }

    /**
     * The same point, standing on the floor under it.
     *
     * <p>Where a mover is walking has always been decided in two dimensions and
     * still is; how high it stands while doing so is read off the ground beneath
     * it, every step. Read rather than carried, because the ground is what
     * decides — a mover that kept its own height would climb a stair and arrive
     * at the top still standing at the bottom's.
     *
     * <p>On flat ground this returns z = 0, which is where everything already was.
     */
    private static Coord3D onGround(GameObject mover, Coord3D position) {
        var world = mover.getWorld();
        return world == null
                ? position
                : new Coord3D(position.x(), position.y(), world.groundHeight(mover, position));
    }

    static float across(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static Coord3D headingVector(float angle) {
        return new Coord3D((float) StrictMath.cos(angle), (float) StrictMath.sin(angle), 0f);
    }

    /** Step {@code current} toward {@code target} by at most {@code maxStep} radians. */
    static float rotateToward(float current, float target, float maxStep) {
        float diff = angleBetween(current, target);
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }

    /** The signed turn from {@code from} to {@code to}, the short way round, in radians. */
    static float angleBetween(float from, float to) {
        return (float) StrictMath.atan2(StrictMath.sin(to - from), StrictMath.cos(to - from));
    }
}
