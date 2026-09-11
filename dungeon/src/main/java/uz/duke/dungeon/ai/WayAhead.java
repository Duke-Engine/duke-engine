package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;

/**
 * Whether there is any way forward, and nothing more than that.
 *
 * <p>The answer to a shuffling monster, and simpler than every earlier attempt at
 * it. Those all asked "has it stopped getting closer" — a question about the last
 * two seconds, answered late and answered wrong while the thing it was chasing
 * kept moving. This asks the question the player asks when he watches it: <b>can
 * it go anywhere?</b> No means stand still. Yes means walk, and leave the walking
 * to the locomotor.
 *
 * <p><b>A body in front of you is not the same as no way forward</b>, and getting
 * that wrong is how the first version of this broke two tests that were right.
 * In an open room the locomotor steps round what is in its way, which is what
 * stepping aside is for. In a corridor one cell wide it takes the same step, is
 * pushed back, and tries again, thirty times a second. The difference between the
 * two is not the body at all — it is the walls beside it. So the question is not
 * "is something in front of me" but <b>"is there room beside it"</b>: a whole
 * body-width, left or right, that this could stand in. That is what getting past
 * something actually costs, and it is the one thing the locomotor never asks —
 * it looks a single step aside, finds that clear, and takes it, over and over.
 *
 * <p>Standing still is then the whole of the fix. No amount of re-planning less
 * often could have done it, because the shuffle happens between one order and the
 * next rather than because of them.
 *
 * <p><b>Which way is forward</b> is the mover's own heading, not the direction of
 * whatever it is chasing. The locomotor turns to face its next corner every
 * frame, so the heading follows the route round a wall — where a straight line to
 * the quarry would point into the wall and call an open corridor shut.
 *
 * <p><b>The spot is taken once and then watched.</b> A creature that has stopped
 * is no longer being turned by its route, and something else may turn it — the
 * hero turns to face what he is shooting while he waits. Re-reading the heading
 * every frame would let that swing the question round onto a piece of floor it
 * was never about. So the caller keeps the point it stopped short of, and the
 * question is asked again along the line to that same point.
 */
final class WayAhead {

    /** Square left and square right: the two ways round anything. */
    private static final float[] ASIDE = {
        (float) Math.toRadians(90), (float) Math.toRadians(-90),
    };

    private WayAhead() {
    }

    /**
     * The spot in front of it that it cannot get past, or {@code null} when it can
     * get on.
     *
     * <p>{@code probe} is how far ahead to look, in world units: a little more
     * than a step, so the answer comes before the collision rather than after it.
     */
    static Coord3D noWayPast(GameObject mover, float probe, GameObject quarry) {
        return mover == null ? null
                : noWayPast(mover, mover.getOrientation(), probe, quarry);
    }

    /**
     * Whether a spot it has already stopped short of is still worth standing short
     * of.
     *
     * <p>Two ways a wait can end, and both have to be watched or the creature is
     * simply frozen. The road may open, which is the one anybody thinks of. Or the
     * quarry may walk round behind it — and then the spot it has been staring at
     * is no longer between them, the road it stopped on is not the road any more,
     * and it would stand there for the rest of the run waiting for a door it no
     * longer needs to go through.
     *
     * <p>"Between them" is the whole of that second test, and it is a comparison
     * rather than a timer on purpose: a timer would have it twitch once every so
     * often for ever, which is a smaller version of the fault being fixed rather
     * than the end of it.
     */
    static boolean stillShut(GameObject mover, Coord3D spot, Coord3D towards, float probe,
            GameObject quarry) {
        if (mover == null || spot == null || towards == null) {
            return false;
        }
        var at = mover.getPosition();
        return spot.distance(towards) < at.distance(towards)
                && noWayPast(mover, angleFrom(at, spot), probe, quarry) != null;
    }

    private static Coord3D noWayPast(GameObject mover, float heading, float probe,
            GameObject quarry) {
        var world = mover.getWorld();
        if (world == null) {
            return null;
        }
        var ahead = spotAt(mover, heading, probe);
        if (!occupied(world, mover, ahead, quarry)) {
            return null; // clear road
        }
        // A whole body-width to the side, because that is what getting past
        // something costs. Asking at arm's length instead is what the first
        // version of this did, and in a corridor ten units across it found a clear
        // diagonal a step away, called it a way round, and let the twitch carry on
        // -- which is exactly the mistake the locomotor itself makes, and the one
        // this is here to catch. The width is the mover's own, off its template,
        // so a thing twice the size needs twice the gap without being told.
        float aside = 2f * mover.getTemplate().getGeometry().footprintRadius();
        for (float turn : ASIDE) {
            var side = spotAt(mover, heading + turn, aside);
            // Anything counts to the side, the quarry included: what it is walking
            // at is not an obstruction in front of it, but it is certainly not a
            // gap to squeeze through either. Stone counts too, and only here --
            // ahead of it a wall means the route turns at that wall, and the turn
            // is the step.
            if (!occupied(world, mover, side, null) && !world.isGroundBlocked(side)) {
                return null; // there is room beside it, so it can be walked round
            }
        }
        return ahead;
    }

    /**
     * Whether a body stands in that spot.
     *
     * <p>Asked of the world with the mover's own size taken into account, so it is
     * the gap between their surfaces rather than between their middles.
     *
     * <p><b>What it is walking at does not count</b> as something in front of it. A
     * monster's quarry is the one body it is <em>meant</em> to end up against, and
     * calling that an obstruction would stop it a probe's length short of the
     * hero, outside its own fighting distance, for ever. Arriving is not being
     * blocked; how near it wants to get is {@code CloseDistance}'s business.
     */
    private static boolean occupied(World world, GameObject mover, Coord3D spot,
            GameObject quarry) {
        var body = world.findBlocker(mover, spot);
        return body != null && body != quarry;
    }

    private static Coord3D spotAt(GameObject mover, float heading, float probe) {
        var at = mover.getPosition();
        return new Coord3D(
                at.x() + (float) StrictMath.cos(heading) * probe,
                at.y() + (float) StrictMath.sin(heading) * probe,
                at.z());
    }

    private static float angleFrom(Coord3D at, Coord3D spot) {
        return (float) StrictMath.atan2(spot.y() - at.y(), spot.x() - at.x());
    }
}
