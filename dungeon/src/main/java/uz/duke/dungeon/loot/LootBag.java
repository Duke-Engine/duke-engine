package uz.duke.dungeon.loot;

import java.util.ArrayList;
import java.util.List;

/**
 * What the hero has picked up off the floor this run, and what it comes to.
 *
 * <p>Session state, beside {@link uz.duke.dungeon.level.HeroProgress} and for the
 * same reasons: a floor
 * gives him a new body, and a sword he found has to survive that; a death is
 * meant to take everything, and is told rather than left to infer.
 *
 * <p>There is no inventory and nothing to equip. What he picks up is his, at
 * once and for good — which is the whole shape of the decision the loot makes:
 * walking over to it is the decision, and there is no second one.
 *
 * <p>It also carries the words for the last thing he found, because something has
 * to say so and the run loop is what writes the line the panel reads.
 */
public final class LootBag {

    private final List<Loot> found = new ArrayList<>();

    /** The words for what he just picked up, and the frame they stop being said. */
    private String note = "";
    private int noteUntilFrame;

    /** Take one, and remember it long enough to say so. */
    public void take(Loot item, int frame, int noteFrames) {
        found.add(item);
        note = item.name();
        noteUntilFrame = frame + noteFrames;
    }

    /** Everything found so far, oldest first. */
    public List<Loot> getFound() {
        return List.copyOf(found);
    }

    /** Forget everything: a run has ended. */
    public void clear() {
        found.clear();
        note = "";
        noteUntilFrame = 0;
    }

    /** What to say about the last thing he picked up, or "" once it has been said. */
    public String noteAt(int frame) {
        return frame < noteUntilFrame ? note : "";
    }

    // ---- what it all comes to ----

    /** Percent added to his weapon damage by everything he has found. */
    public int attackPercent() {
        return totalOf(LootKind.ATTACK);
    }

    /** Flat maximum health added by everything he has found. */
    public int health() {
        return totalOf(LootKind.HEALTH);
    }

    /** Flat maximum mana added by everything he has found. */
    public int mana() {
        return totalOf(LootKind.MANA);
    }

    /** Percent of incoming damage removed by everything he has found. */
    public int armourPercent() {
        return totalOf(LootKind.ARMOUR);
    }

    /** Whole points of strength added by everything he has found. */
    public int strength() {
        return totalOf(LootKind.STRENGTH);
    }

    /** Whole points of agility added by everything he has found. */
    public int agility() {
        return totalOf(LootKind.AGILITY);
    }

    /** Whole points of intelligence added by everything he has found. */
    public int intelligence() {
        return totalOf(LootKind.INTELLIGENCE);
    }

    private int totalOf(LootKind kind) {
        int total = 0;
        for (var item : found) {
            if (item.kind() == kind) {
                total += item.value();
            }
        }
        return total;
    }
}
