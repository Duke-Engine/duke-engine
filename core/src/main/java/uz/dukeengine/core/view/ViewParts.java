package uz.dukeengine.core.view;

import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * What a kind of game adds to the picture of its world, asked on the simulation thread as each snapshot is made: the
 * parts of a thing's view only it knows — whether the thing is a structure, what it is making, what it carries, how far
 * it is built, how its turrets stand — and of the whole, a side's money and spare power and a rally point. A kind with
 * none of them says none, as {@link #NONE} does.
 */
public interface ViewParts {

    /** None of them: every thing picked as itself, making, carrying and turning nothing, whole. */
    ViewParts NONE = new ViewParts() {
    };

    /**
     * What a contained thing is shown on — the carrier it rides on top of, or one that shows its passengers — or null
     * for a thing out of sight inside.
     */
    default GameObject shownOn(GameObject contained) {
        return null;
    }

    /** Whether it is a structure: a building, drawn and picked as one. */
    default boolean structure(GameObject thing) {
        return false;
    }

    /** Whether a click or a box may pick it, as a thing of its kind. */
    default boolean selectable(GameObject thing) {
        return true;
    }

    /** How many things it has queued to make, or -1 for a thing that makes none. */
    default int queued(GameObject thing) {
        return -1;
    }

    /** What it carries, by id, in the order they got in. */
    default List<Integer> passengers(GameObject thing) {
        return List.of();
    }

    /** How far it is built: 1 whole, less while it goes up, down to -0.5 as a building sold comes down. */
    default float built(GameObject thing) {
        return 1f;
    }

    /** How its turrets stand, or null for a thing with none. */
    default Turrets turrets(GameObject thing) {
        return null;
    }

    /** Its rally point as it is shown while it is selected, or null for none. */
    default RallyView rally(GameObject thing) {
        return null;
    }

    /** Side {@code player}'s money, as the snapshot shows it. */
    default int money(World world, int player) {
        return 0;
    }

    /** Side {@code player}'s spare power. */
    default int powerSurplus(World world, int player) {
        return 0;
    }
}
