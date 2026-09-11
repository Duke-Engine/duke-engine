package uz.duke.dungeon.run;

import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.gen.GeneratedDungeon;

/**
 * The endless descent: a new floor from the next seed along the chain.
 *
 * <p>The seed advances once per floor handed out and never anywhere else, so a
 * session's whole sequence of dungeons is a function of the one seed it started
 * with — which is what makes a run reproducible from its seed alone.
 */
final class GeneratedFloors implements Floors {

    private final DungeonSettings settings;
    private long seed;

    GeneratedFloors(long seed, DungeonSettings settings) {
        this.seed = seed;
        this.settings = settings;
    }

    @Override
    public GeneratedDungeon next(int depth) {
        seed = DungeonGenerator.nextSeed(seed);
        return DungeonGenerator.generate(seed, settings, depth);
    }

    @Override
    public long seed() {
        return seed;
    }

    /** The bosses are the floors: the last one stands on the bottom. */
    @Override
    public int lastDepth() {
        return settings.finalDepth();
    }
}
