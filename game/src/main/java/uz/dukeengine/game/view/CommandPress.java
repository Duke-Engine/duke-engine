package uz.dukeengine.game.view;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * A button of the command bar, pressed: which one, what was selected, and — for one that aims — where or at
 * what.
 *
 * @param id        the button's own word, as the game gave it
 * @param selection what was selected when it was pressed, in the order the window holds it
 * @param place     the place clicked, for a button that aims at the ground; null otherwise. Ground
 *                  coordinates — x and y across the map — as every order carries them
 * @param target    the id of the thing clicked, for a button that aims at one; -1 otherwise
 */
public record CommandPress(String id, List<Integer> selection, Coord3D place, int target) {

    public CommandPress {
        selection = selection == null ? List.of() : List.copyOf(selection);
    }
}
