package uz.dukeengine.core.view;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * A button of the command bar, pressed: which one, what was selected, and — for one that aims — where or at
 * what, and which way the thing put there faces.
 *
 * @param id        the button's own word, as the game gave it
 * @param selection what was selected when it was pressed, in the order the window holds it
 * @param place     the place clicked, for a button that aims at the ground; null otherwise. Ground
 *                  coordinates — x and y across the map — as every order carries them. Where the press went
 *                  down, which a drag to turn it does not move
 * @param facing    which way the thing put there faces, in the simulation's degrees: the button's own, or
 *                  the way the player dragged — so the game builds exactly what the ghost showed
 * @param target    the id of the thing clicked, for a button that aims at one; -1 otherwise
 */
public record CommandPress(String id, List<Integer> selection, Coord3D place, float facing, int target) {

    public CommandPress {
        selection = selection == null ? List.of() : List.copyOf(selection);
    }
}
