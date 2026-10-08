package uz.dukeengine.rpg.item;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.LogicRandom;

/**
 * What a dead thing leaves behind, and how often.
 *
 * <p>The draw is the world's own, {@link uz.dukeengine.core.thing.World#random()}: the one stream whatever the
 * simulation decides by chance draws from, seeded with the match and saved with it. So every machine of a lock-step
 * match drops the same thing off the same death, and a replay of the same orders drops it again. {@code Math.random}
 * would make loot the one part of the game nobody could reproduce, which in a roguelike is the part players most
 * want to.
 */
public final class LootTable {

    private final List<Loot> catalogue;
    private final int dropPercent;
    private final int bossDropPercent;
    private final int valuePercentPerTier;

    /**
     * @param dropPercent         the chance in a hundred that an ordinary death drops anything
     * @param bossDropPercent     the same for a boss
     * @param valuePercentPerTier how much more an item is worth for each tier above the first, in percent
     */
    public LootTable(List<Loot> catalogue, int dropPercent, int bossDropPercent, int valuePercentPerTier) {
        this.catalogue = List.copyOf(catalogue);
        this.dropPercent = dropPercent;
        this.bossDropPercent = bossDropPercent;
        this.valuePercentPerTier = valuePercentPerTier;
    }

    /**
     * What this death leaves, or {@code null} for nothing — which is most of the time, and has to be: loot that
     * always drops is not loot, it is a tax on killing things.
     *
     * @param random the world's, drawn from on the simulation thread
     * @param tier   how far into the game, which decides both what may drop and what it is worth: a floor, a zone,
     *               a level, from 1
     * @param boss   whether this was a boss
     */
    public Loot dropFor(LogicRandom random, int tier, boolean boss) {
        int chance = boss ? bossDropPercent : dropPercent;
        if (random.nextInt(0, 99) >= chance) {
            return null;
        }
        var pool = new ArrayList<Loot>();
        for (var item : catalogue) {
            if (item.weight() > 0 && tier >= item.minTier()) {
                pool.add(item);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return atTier(pool.get(weightedPick(random, pool)), tier);
    }

    /**
     * The same item, worth what it is worth this far in — computed from the tier in one step rather than
     * compounded, so the tenth tier's sword is the same sword however the hero got there.
     */
    private Loot atTier(Loot item, int tier) {
        int grown = Math.max(0, tier - 1);
        int value = item.value() + item.value() * grown * valuePercentPerTier / 100;
        return value == item.value() ? item : item.worth(value);
    }

    private static int weightedPick(LogicRandom random, List<Loot> pool) {
        int total = 0;
        for (var item : pool) {
            total += item.weight();
        }
        int roll = random.nextInt(0, total - 1);
        for (int i = 0; i < pool.size(); i++) {
            roll -= pool.get(i).weight();
            if (roll < 0) {
                return i;
            }
        }
        return pool.size() - 1; // unreachable while every weight is positive
    }
}
