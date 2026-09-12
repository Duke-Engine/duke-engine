package uz.duke.dungeon.run;

import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.stage.Stage;

/**
 * One floor, and the same one every time it is asked for.
 *
 * <p>Which is the whole of what a stage is. The depth is ignored because there is
 * only one — there is nowhere to descend to, and the boss at the end of it ends
 * the game rather than opening a door.
 */
final class StageFloors implements Floors {

    private final Stage stage;

    StageFloors(Stage stage) {
        this.stage = stage;
    }

    @Override
    public GeneratedDungeon next(int depth) {
        return stage.floor();
    }

    @Override
    public long seed() {
        return stage.seed();
    }

    /**
     * The difficulty its author chose, which is the depth it is played at.
     *
     * <p>So a stage is not merely the same rooms every time — it is the same
     * rooms at the danger somebody picked for them. A stage built at seven is
     * fought against seventh-floor monsters and a seventh-floor boss, on a floor
     * the descent would never have reached that way.
     */
    @Override
    public int firstDepth() {
        return Math.max(1, stage.difficulty());
    }

    /**
     * The same depth it starts on: there is one floor, and the boss on it ends
     * the game rather than opening a door.
     */
    @Override
    public int lastDepth() {
        return firstDepth();
    }
}
