package uz.dukeengine.client3d;

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

    /** How far the mouse must travel before a press counts as a drag, in pixels, where the game names nothing else. */
    static final float DRAG_THRESHOLD_PIXELS = 5f;

    /** A thing as the screen sees it — already projected, so no camera is needed here. */
    record Candidate(int id, float screenX, float screenY, boolean own, boolean selectable, boolean structure) {

        /** A thing that is no building. */
        Candidate(int id, float screenX, float screenY, boolean own, boolean selectable) {
            this(id, screenX, screenY, own, selectable, false);
        }
    }

    private SelectionBox() {
    }

    /**
     * Whether a press at one point and a release at another is a drag rather than a click: {@code tolerance} pixels
     * or more apart, across or down — the reference's {@code DragTolerance}.
     */
    static boolean isDrag(float fromX, float fromY, float toX, float toY, float tolerance) {
        return Math.abs(toX - fromX) >= tolerance || Math.abs(toY - fromY) >= tolerance;
    }

    /**
     * What may be selected inside the box, whoever's it is, in the order given. Dragged in any direction — the
     * corners are sorted, so a box drawn right-to-left is the same box.
     */
    static List<Candidate> inside(float fromX, float fromY, float toX, float toY, List<Candidate> candidates) {
        float left = Math.min(fromX, toX);
        float right = Math.max(fromX, toX);
        float bottom = Math.min(fromY, toY);
        float top = Math.max(fromY, toY);

        var found = new ArrayList<Candidate>();
        for (var candidate : candidates) {
            if (candidate.selectable() && candidate.screenX() >= left && candidate.screenX() <= right
                    && candidate.screenY() >= bottom && candidate.screenY() <= top) {
                found.add(candidate);
            }
        }
        return found;
    }

    /** What a click chose: the selection after it, and the thing it took in to answer for, or -1 for none. */
    record Clicked(List<Integer> selection, int tookIn) {
    }

    /**
     * What a click on {@code hit} selects, null for open ground: it alone; with the add key held, added to his own —
     * or, one of his own already selected, taken out, silently, as the reference's shift-click on things all selected
     * takes them out ({@code SelectionTranslator}: {@code MSG_REMOVE_FROM_SELECTED_GROUP}).
     */
    static Clicked click(Candidate hit, List<Integer> selected, boolean add) {
        boolean mine = hit != null && hit.own();
        if (add && mine && hit.selectable() && selected.contains(hit.id())) {
            var left = new ArrayList<>(selected);
            left.remove(Integer.valueOf(hit.id()));
            return new Clicked(List.copyOf(left), -1);
        }
        var after = new java.util.LinkedHashSet<Integer>(add && mine ? selected : List.of());
        if (hit == null || !hit.selectable()) {
            return new Clicked(List.copyOf(after), -1);
        }
        boolean isNew = after.add(hit.id());
        return new Clicked(List.copyOf(after), isNew ? hit.id() : -1);
    }

    /**
     * The selection a box leaves ({@code SelectionTranslator}, {@code SelectionInfo}): of what may be selected in it,
     * the player's own units, his buildings left out — his one building where it is all of his there is, and none of
     * them where there are more, the selection let go; of nothing of his, the one thing of another side alone in it,
     * and more than one change nothing; and an empty box keeps the selection. With the add key held, the units it takes
     * are added to a selection of his units, and replace any other.
     *
     * @param box      what may be selected inside the box — see {@link #inside}
     * @param selected what is selected now, in the order it was chosen
     * @param hisUnits whether all of that is units of his: no building, nothing of another side's
     * @param add      whether the add key is held
     */
    static List<Integer> after(List<Candidate> box, List<Integer> selected, boolean hisUnits, boolean add) {
        if (box.isEmpty()) {
            return selected;
        }
        var own = box.stream().filter(Candidate::own).toList();
        if (own.isEmpty()) {
            return box.size() == 1 ? List.of(box.getFirst().id()) : selected;
        }
        var units = own.stream().filter(candidate -> !candidate.structure()).map(Candidate::id).toList();
        if (units.isEmpty()) {
            return own.size() == 1 ? List.of(own.getFirst().id()) : List.of();
        }
        if (!add || !hisUnits) {
            return units;
        }
        var added = new java.util.LinkedHashSet<>(selected);
        added.addAll(units);
        return List.copyOf(added);
    }
}
