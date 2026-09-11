package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;

/**
 * Whether there is room in front of something, and nothing more than that.
 *
 * <p>The answer to a shuffling monster, and simpler than every earlier attempt at
 * it. Those all asked "has it stopped getting closer" — a question about the last
 * two seconds, answered late and answered wrong while the thing it was chasing
 * kept moving. This asks the question the player asks when he watches it:
 * <b>is there room to go forward?</b> No means stand still. Yes means go.
 *
 * <p>Standing still is the whole of the fix. A locomotor with no room tries to
 * step aside instead — it finds a clear diagonal, takes it, is pushed back the
 * next frame, and tries again, thirty times a second. That is what the twitching
 * in a doorway was, and re-planning less often could never stop it, because the
 * shuffle happens between one order and the next rather than because of them.
 *
 * <p><b>Which way is forward</b> is the mover's own heading, not the direction of
 * whatever it is chasing. The locomotor turns to face its next corner every
 * frame, so the heading follows the route round a wall — where a straight line to
 * the quarry would point into the wall and call an open corridor blocked.
 *
 * <p><b>The spot is taken once and then watched.</b> A creature that has stopped
 * is no longer being turned by its route, and something else may turn it — the
 * hero turns to face what he is shooting while he waits. Re-reading the heading
 * every frame would let that swing the question round onto a piece of floor it
 * was never about. So the caller keeps the point it stopped short of and asks
 * about that same point until it clears, which is also the only honest reading of
 * what it is waiting for.
 *
 * <p>Only bodies count, never stone. A route never crosses a wall, so a wall
 * ahead means the route turns there and the step is the turn; a body ahead is
 * something the route did not know about, and is the only thing worth waiting
 * for.
 */
final class WayAhead {

    private WayAhead() {
    }

    /**
     * The spot it would be standing in a moment, if it carried on as it is.
     *
     * <p>{@code probe} is how far ahead to look, in world units: a little more
     * than a step, so the answer comes before the collision rather than after it.
     */
    static Coord3D justAhead(GameObject mover, float probe) {
        float heading = mover.getOrientation();
        var at = mover.getPosition();
        return new Coord3D(
                at.x() + (float) StrictMath.cos(heading) * probe,
                at.y() + (float) StrictMath.sin(heading) * probe,
                at.z());
    }

    /**
     * Whether a body stands in that spot.
     *
     * <p>Asked of the world with the mover's own size taken into account, so it
     * is the gap between their surfaces rather than between their middles.
     *
     * <p><b>What it is walking at does not count.</b> A monster's quarry is the
     * one body it is <em>meant</em> to end up against, and calling it an
     * obstruction would stop the monster a probe's length short of the hero,
     * outside its own fighting distance, for ever. Arriving is not being blocked;
     * how near it wants to get is {@code CloseDistance}'s business.
     */
    static boolean occupied(GameObject mover, Coord3D spot, GameObject quarry) {
        var world = mover == null || spot == null ? null : mover.getWorld();
        if (world == null) {
            return false;
        }
        var body = world.findBlocker(mover, spot);
        return body != null && body != quarry;
    }

    /**
     * Whether a spot something stopped short of is still worth standing short of.
     *
     * <p>Two ways a wait can end, and both have to be watched or the creature is
     * simply frozen. The body may leave, which is the one anybody thinks of. Or
     * the quarry may walk round behind it — and then the spot it has been staring
     * at is no longer between them, the road it stopped on is not the road any
     * more, and it would stand there for the rest of the run waiting for a door it
     * no longer needs to go through.
     *
     * <p>"Between them" is the whole of that second test, and it is a comparison
     * rather than a timer on purpose: a timer would have it twitch once every so
     * often for ever, which is a smaller version of the fault being fixed rather
     * than the end of it.
     */
    static boolean stillShut(GameObject mover, Coord3D spot, GameObject quarry) {
        if (mover == null || spot == null || quarry == null) {
            return false;
        }
        var him = quarry.getPosition();
        return spot.distance(him) < mover.getPosition().distance(him)
                && occupied(mover, spot, quarry);
    }
}
