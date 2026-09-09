package uz.duke.client3d;

import java.util.ArrayList;
import java.util.List;

/**
 * What a drag across the screen selects.
 *
 * <p>Two questions, both easy to get subtly wrong and neither needing a window to
 * answer: whether the mouse moved far enough for this to be a drag at all, and
 * which units ended up inside the box.
 *
 * <p>The first matters more than it looks. A click is a drag of zero pixels, and a
 * real hand never quite manages zero — without a threshold, every click on a unit
 * becomes a tiny empty box that selects nothing, and the player's clicks silently
 * stop working. So a short drag is a click, and the caller keeps its existing
 * click behaviour for that case.
 *
 * <p>Selection is the client's business alone: the simulation has no idea anything
 * is selected, and nothing here is sent to it. Only the orders that follow are.
 */
final class SelectionBox {

    /** How far the mouse must travel before a press counts as a drag, in pixels. */
    static final float DRAG_THRESHOLD_PIXELS = 5f;

    /** A unit as the screen sees it — already projected, so no camera is needed here. */
    record Candidate(int id, float screenX, float screenY, boolean own, boolean selectable) {
    }

    private SelectionBox() {
    }

    /** Whether a press at one point and a release at another is a drag rather than a click. */
    static boolean isDrag(float fromX, float fromY, float toX, float toY) {
        return Math.abs(toX - fromX) >= DRAG_THRESHOLD_PIXELS
                || Math.abs(toY - fromY) >= DRAG_THRESHOLD_PIXELS;
    }

    /**
     * The ids inside the box, in the order given.
     *
     * <p>Only the player's own selectable units: dragging a box over the dungeon
     * should never hand him a skeleton he cannot command. Dragged in any direction
     * — the corners are sorted, so a box drawn right-to-left is the same box.
     */
    static List<Integer> inside(float fromX, float fromY, float toX, float toY,
            List<Candidate> candidates) {
        float left = Math.min(fromX, toX);
        float right = Math.max(fromX, toX);
        float bottom = Math.min(fromY, toY);
        float top = Math.max(fromY, toY);

        var found = new ArrayList<Integer>();
        for (var candidate : candidates) {
            if (!candidate.own() || !candidate.selectable()) {
                continue;
            }
            if (candidate.screenX() >= left && candidate.screenX() <= right
                    && candidate.screenY() >= bottom && candidate.screenY() <= top) {
                found.add(candidate.id());
            }
        }
        return found;
    }
}
