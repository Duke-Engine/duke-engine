package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.view.UnitView;

/**
 * The client's selection and the game's, kept in step both ways. What the player selects is told to the game — a
 * command bar, a portrait, a production queue hang off it, whoever draws them — and what the game selects from its
 * own bar is taken up here, under the client's own rule of what may be selected together.
 *
 * <p>Held apart from the window so it can be checked without one.
 */
final class SelectionLink {

    private List<Integer> told = List.of();
    private java.util.Map<Integer, List<Integer>> toldGroups = java.util.Map.of();

    /** Tell the game which control group each of the player's things is in, whenever that has changed. */
    void tellGroups(java.util.Map<Integer, List<Integer>> groups, DukeGame game) {
        if (!groups.equals(toldGroups)) {
            toldGroups = groups;
            game.setControlGroups(groups);
        }
    }

    /** Tell the game what is selected, in the order it was selected, whenever that has changed since last told. */
    void tell(Collection<Integer> selected, DukeGame game) {
        var now = List.copyOf(selected);
        if (!now.equals(told)) {
            told = now;
            game.setSelection(now);
        }
    }

    /**
     * The game's pick, if it made one since the last frame ({@link DukeGame#select}): in place of what was selected,
     * what the client's rule admits of it, in the game's order.
     *
     * @return whether the game had picked
     */
    boolean takeUp(Set<Integer> selected, DukeGame game, List<UnitView> units, int localPlayer) {
        return takeUp(selected, game, units, localPlayer, unit -> { });
    }

    /** The same, {@code answer} told the first thing it took in where the game asked for its selection answered. */
    boolean takeUp(Set<Integer> selected, DukeGame game, List<UnitView> units, int localPlayer,
            java.util.function.IntConsumer answer) {
        var wanted = game.takeSelecting();
        if (wanted == null) {
            return false;
        }
        var admitted = admitted(wanted.ids(), units, localPlayer);
        selected.clear();
        selected.addAll(admitted);
        if (wanted.answered() && !admitted.isEmpty()) {
            answer.accept(admitted.getFirst());
        }
        return true;
    }

    /**
     * What of a pick the client may hold, as a click may: things standing and selectable, and then the player's own
     * — or one thing of someone else's, on its own, to look at. What is gone, or hidden from the player, is left out.
     */
    static List<Integer> admitted(List<ObjectId> wanted, List<UnitView> units, int localPlayer) {
        var standing = new ArrayList<UnitView>();
        for (var id : wanted) {
            for (var unit : units) {
                if (unit.id() == id.value() && unit.selectable()) {
                    standing.add(unit);
                    break;
                }
            }
        }
        var mine = standing.stream().filter(unit -> unit.playerIndex() == localPlayer).map(UnitView::id).toList();
        if (!mine.isEmpty() || standing.size() != 1) {
            return mine;
        }
        return List.of(standing.getFirst().id());
    }
}
