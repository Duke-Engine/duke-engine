package uz.dukeengine.dungeon.ai;

import uz.dukeengine.core.math.Coord3D;

/**
 * When something already on its way is worth sending again.
 *
 * <p>Shared by the two brains because they had the same fault and it is the same
 * fault: both re-issued the order whenever a clock said so or the mover stopped,
 * and issuing one is not as free as it looks. Ordering a mover to the place it is
 * already heading <b>restarts</b> it, and a restart clears the locomotor's count
 * of how long it has gone without getting closer. That count is the one thing
 * that ends a hopeless walk — so a creature pressed against a body it could not
 * get past was stopped every two seconds and started again the same frame, and
 * spent its whole life in the walking state, treading the floor and going
 * nowhere. On screen that is a monster twitching in a doorway.
 *
 * <p>So the rule is that a chase is re-planned when the quarry has <em>moved</em>,
 * not when a clock says so and not merely because the mover has given up. A hero
 * standing still is a destination that has not changed, whatever the frame count
 * is, and the thing following him is left alone long enough to notice it is stuck
 * — and then left alone.
 *
 * <p><b>What that costs.</b> A creature that gave up because another body was in
 * the way does not try again when that body wanders off; it waits until its
 * quarry moves. In a fight the quarry moves constantly and half a cell is enough,
 * so the wait is short. Standing still for a moment too long is in any case the
 * cheaper mistake: the other way round, it twitches for ever.
 */
final class Chasing {

    private Chasing() {
    }

    /**
     * How far the quarry must have gone before the chase is worth re-planning, in
     * world units.
     *
     * <p>About half a cell. Small enough that a creature walking away is followed
     * without a visible lag, large enough that one standing in a fight — which
     * still drifts a little as it swings and is shoved — does not count as having
     * gone anywhere.
     */
    private static final float MOVED_ENOUGH = 5f;

    /**
     * Whether {@code now} is far enough from {@code sentAfter} to be a new order.
     *
     * <p>A {@code null} {@code sentAfter} means nothing has been ordered yet, so
     * anything is: that is how a fresh chase sets off on the frame it begins.
     */
    static boolean worthReplanning(Coord3D sentAfter, Coord3D now) {
        if (sentAfter == null) {
            return true;
        }
        float dx = now.x() - sentAfter.x();
        float dy = now.y() - sentAfter.y();
        return dx * dx + dy * dy > MOVED_ENOUGH * MOVED_ENOUGH;
    }
}
