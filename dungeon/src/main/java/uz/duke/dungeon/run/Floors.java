package uz.duke.dungeon.run;

import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.stage.Stage;

/**
 * Where the next floor comes from.
 *
 * <p>Duke Dungeon has two answers now and they are the difference between its two
 * games. A descent draws a floor nobody has seen from a seed, and the run is
 * about how far down you get. A stage hands back the same floor every time, and
 * the run is about learning it — which room the archers are in, which corner the
 * boss comes round. The run loop is the same loop either way: the hero dies, the
 * world is torn down, a floor goes back in. Only this says which floor.
 *
 * <p><b>Stateful on purpose.</b> A descent's seed is a chain and it is advanced
 * by <em>endings</em> as well as by descending — die on floor three and the run
 * that follows starts on a floor nobody has played either. A pure
 * {@code floorAt(depth)} would have made every new run open on the same first
 * floor, which is a different game from the one this already is.
 */
public interface Floors {

    /** The floor to lay out next, at this depth. */
    GeneratedDungeon next(int depth);

    /**
     * The seed the floor now standing was drawn from.
     *
     * <p>Asked by everything that wants to look at a floor without changing it —
     * what stone it is built from, most of all. A stage answers with the seed it
     * was cut from, which is why it is written into the file: the same rooms with
     * a different look every time would be a stage only by half.
     */
    long seed();

    /**
     * The depth the game is won on, or {@code 0} if the descent has no bottom.
     *
     * <p>Here rather than read off the settings because it is a property of what
     * is being played rather than of how the game is tuned: a stage is one floor
     * deep whatever the file's list of bosses says.
     */
    int lastDepth();

    /** Floors drawn from a seed, each one deeper and nastier — the endless descent. */
    static Floors generated(long seed, DungeonSettings settings) {
        return new GeneratedFloors(seed, settings);
    }

    /** One floor, frozen, handed back however many times it is asked for. */
    static Floors ofStage(Stage stage) {
        return new StageFloors(stage);
    }
}
