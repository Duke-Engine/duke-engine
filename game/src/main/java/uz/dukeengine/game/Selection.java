package uz.dukeengine.game;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.CommandButton;
import uz.dukeengine.core.view.CommandPress;

/**
 * What the window has selected and grouped, the command bar worked out from it, and the presses that come back.
 * The window writes, the simulation reads: selection is the window's own idea, never the simulation's.
 */
final class Selection {

    private volatile List<Integer> selected = List.of();
    /** Who the player's orders go to, where the window says it is not what is selected; null where it is. */
    private volatile List<Integer> ordered;
    private volatile Map<Integer, List<Integer>> controlGroups = Map.of();
    /** The selection the game last asked the client for. */
    private final AtomicReference<DukeGame.Selecting> wanted = new AtomicReference<>();

    private Function<List<Integer>, List<CommandButton>> commandBar;
    private Consumer<CommandPress> pressed;
    private Consumer<CommandPress> refused;

    void commandBar(Function<List<Integer>, List<CommandButton>> buttons) {
        this.commandBar = buttons;
    }

    List<CommandButton> buttonsNow() {
        return commandBar == null ? List.of() : commandBar.apply(selected);
    }

    void set(List<Integer> unitIds) {
        this.selected = unitIds == null ? List.of() : List.copyOf(unitIds);
    }

    List<Integer> get() {
        return selected;
    }

    void setOrdered(List<Integer> unitIds) {
        this.ordered = unitIds == null ? null : List.copyOf(unitIds);
    }

    /** Who the player's orders go to: what the window said, or what is selected. */
    List<Integer> orderedTo() {
        var told = ordered;
        return told != null ? told : selected;
    }

    void request(List<ObjectId> ids, boolean answered) {
        wanted.set(new DukeGame.Selecting(List.copyOf(ids), answered));
    }

    DukeGame.Selecting takeRequest() {
        return wanted.getAndSet(null);
    }

    void setControlGroups(Map<Integer, List<Integer>> groups) {
        var copy = new TreeMap<Integer, List<Integer>>();
        groups.forEach((number, members) -> copy.put(number, List.copyOf(members)));
        this.controlGroups = Collections.unmodifiableMap(copy);
    }

    Map<Integer, List<Integer>> controlGroups() {
        return controlGroups;
    }

    void onPressed(Consumer<CommandPress> handler) {
        this.pressed = handler;
    }

    void onRefused(Consumer<CommandPress> handler) {
        this.refused = handler;
    }

    void press(String id, Coord3D place, float facing, int target) {
        tell(pressed, id, place, facing, target);
    }

    void refuse(String id, Coord3D place, float facing) {
        tell(refused, id, place, facing, -1);
    }

    private void tell(Consumer<CommandPress> handler, String id, Coord3D place, float facing, int target) {
        if (handler != null && id != null) {
            handler.accept(new CommandPress(id, selected, place, facing, target));
        }
    }
}
