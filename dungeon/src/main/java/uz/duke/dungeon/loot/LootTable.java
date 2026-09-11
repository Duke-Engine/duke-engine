package uz.duke.dungeon.loot;

import java.util.ArrayList;
import java.util.List;
import uz.duke.dungeon.gen.DeterministicRng;

/**
 * What a dead monster leaves behind, and how often.
 *
 * <p>Pure: a monster's id and the floor it died on go in, an item or nothing
 * comes out. Nothing here touches the world, so "what does that skeleton drop?"
 * is a question that can be asked without a dungeon — and, more to the point, the
 * same question always has the same answer.
 *
 * <p>Deterministic in the strong sense: the draw is seeded from the run's seed
 * and the monster's own id, not from a running generator. A monster therefore
 * drops the same thing whether it was killed first or last, and whether the
 * player killed the others at all. A generator advanced per death would be
 * reproducible too, but only if every death happened in the same order — which
 * makes a replay depend on the player's route rather than on his commands.
 *
 * <p>{@code Math.random} would make loot the one part of the game nobody could
 * reproduce, which in a roguelike is the part players most want to.
 */
public final class LootTable {

    /** Mixed into the seed so a monster's id, not the order of deaths, decides. */
    private static final long PER_MONSTER = 0x9E3779B97F4A7C15L;
    private static final long PER_DEPTH = 0xC2B2AE3D27D4EB4FL;

    private final List<Loot> catalogue;
    private final long seed;
    private final int dropPercent;
    private final int bossDropPercent;
    private final int valuePercentPerDepth;

    public LootTable(List<Loot> catalogue, long seed, int dropPercent, int bossDropPercent,
            int valuePercentPerDepth) {
        this.catalogue = List.copyOf(catalogue);
        this.seed = seed;
        this.dropPercent = dropPercent;
        this.bossDropPercent = bossDropPercent;
        this.valuePercentPerDepth = valuePercentPerDepth;
    }

    /**
     * What this monster leaves, or {@code null} for nothing — which is most of the
     * time, and has to be: loot that always drops is not loot, it is a tax on
     * killing things.
     *
     * @param objectId the dead monster's id, so the draw is its own
     * @param depth    which floor, which decides both what may drop and what it
     *                 is worth
     * @param boss     whether this was the thing guarding the way down
     */
    public Loot dropFor(int objectId, int depth, boolean boss) {
        var rng = new DeterministicRng(seed ^ (objectId * PER_MONSTER) ^ (depth * PER_DEPTH));
        int chance = boss ? bossDropPercent : dropPercent;
        if (rng.nextInt(100) >= chance) {
            return null;
        }
        var pool = new ArrayList<Loot>();
        for (var item : catalogue) {
            if (item.weight() > 0 && depth >= item.minDepth()) {
                pool.add(item);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return atDepth(pool.get(weightedPick(rng, pool)), depth);
    }

    /**
     * The same item, worth what it is worth this far down.
     *
     * <p>Computed from the depth in one step rather than compounded, like every
     * other figure in this game that grows: the tenth floor's sword is the same
     * sword however the hero got there.
     */
    private Loot atDepth(Loot item, int depth) {
        int grown = Math.max(0, depth - 1);
        int value = item.value() + item.value() * grown * valuePercentPerDepth / 100;
        return value == item.value() ? item
                : new Loot(item.id(), item.name(), item.icon(), item.kind(), value,
                        item.weight(), item.minDepth());
    }

    private static int weightedPick(DeterministicRng rng, List<Loot> pool) {
        int total = 0;
        for (var item : pool) {
            total += item.weight();
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= pool.get(i).weight();
            if (roll < 0) {
                return i;
            }
        }
        return pool.size() - 1; // unreachable while every weight is positive
    }
}
