package uz.duke.dungeon.power;

import java.util.ArrayList;
import java.util.List;
import uz.duke.dungeon.gen.DeterministicRng;

/**
 * Which three powers a level offers.
 *
 * <p>Drawn from the run's seed rather than at random, so a seed is still a whole
 * dungeon: two players given the same one are offered the same cards at the same
 * levels, and a run can be replayed. {@code Math.random} would make the offer the
 * one part of this game nobody could reproduce.
 *
 * <p>Pure, and pure on purpose — a list in, a list out. That is what makes "the
 * same seed offers the same three" a thing a test can hold still without building
 * a dungeon to find out.
 *
 * <p>Powers already taken as often as they may be are left out, so a run does not
 * spend its last levels being offered what it cannot use. If fewer than the
 * wanted number are left the offer is simply shorter — a dungeon that has run out
 * of things to give is not an error.
 */
public final class PowerDraft {

    /**
     * Mixed into the seed so that the level chooses the offer.
     *
     * <p>The odd bits of the golden ratio, the usual choice: without a mix, level
     * 2 and level 3 of the same run would draw from neighbouring states of the
     * generator and could visibly rhyme.
     */
    private static final long PER_LEVEL = 0x9E3779B97F4A7C15L;

    private PowerDraft() {
    }

    /**
     * The cards for one level: {@code count} of them, drawn by weight and without
     * repeating.
     *
     * @param seed       the run's seed — the same one the floors are drawn from
     * @param level      the level being reached, which is what makes each offer
     *                   different from the last
     * @param catalogue  every power the file describes, in file order
     * @param held       what has been taken already, so nothing full is offered
     */
    public static List<Power> offer(long seed, int level, List<Power> catalogue,
            PowerBook held, int count) {
        var pool = new ArrayList<Power>();
        for (var power : catalogue) {
            if (power.weight() > 0 && level >= power.minLevel() && !held.isFull(power)) {
                pool.add(power);
            }
        }
        var rng = new DeterministicRng(seed ^ (level * PER_LEVEL));
        var drawn = new ArrayList<Power>();
        while (drawn.size() < count && !pool.isEmpty()) {
            drawn.add(pool.remove(weightedPick(rng, pool)));
        }
        return List.copyOf(drawn);
    }

    /** An index into {@code pool}, each entry as likely as its weight says. */
    private static int weightedPick(DeterministicRng rng, List<Power> pool) {
        int total = 0;
        for (var power : pool) {
            total += power.weight();
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
