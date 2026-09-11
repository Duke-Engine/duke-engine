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

    @Override
    public int lastDepth() {
        return 1;
    }
}
