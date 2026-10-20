package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;

/** The decks laid over the ground at run time, and what is told when one closes with things on it. */
final class Decks {

    private final Supplier<PathGrid> grid;
    private final Roster roster;
    private final List<BiConsumer<Integer, List<GameObject>>> closedListeners = new ArrayList<>();

    Decks(Supplier<PathGrid> grid, Roster roster) {
        this.grid = grid;
        this.roster = roster;
    }

    int add(Coord3D first, Coord3D second, Coord3D third, Coord3D fourth) {
        return grid.get().addDeck(first, second, third, fourth);
    }

    void setClearance(float clearance) {
        grid.get().setDeckClearance(clearance);
    }

    /** Open or close a deck; closed, every thing on it is handed back to the ground under it, and the listeners told. */
    void setOpen(int floor, boolean open) {
        var pathGrid = grid.get();
        pathGrid.setDeckOpen(floor, open);
        if (open) {
            return;
        }
        var fell = new ArrayList<GameObject>();
        for (var object : roster.list()) {
            if (object.getFloor() == floor) {
                fell.add(object);
                object.setFloor(0);
                var at = object.getPosition();
                object.setPosition(new Coord3D(at.x(), at.y(), pathGrid.groundHeight(at)));
            }
        }
        for (var listener : closedListeners) {
            listener.accept(floor, List.copyOf(fell));
        }
    }

    void onClosed(BiConsumer<Integer, List<GameObject>> listener) {
        closedListeners.add(listener);
    }
}
