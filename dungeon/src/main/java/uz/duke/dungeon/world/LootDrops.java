package uz.duke.dungeon.world;

/**
 * When a floor gives something up, and how it is picked up.
 *
 * @param template             the creature a dropped item becomes; empty means nothing is ever
 *     dropped
 * @param dropPercent          the chance a monster drops something, out of a hundred
 * @param bossDropPercent      and a boss
 * @param pickupRange          how close he has to walk before it is his
 * @param valuePercentPerDepth how much more an item is worth each floor down
 * @param noteFrames           how long the panel says what he just found, in logic frames
 */
public record LootDrops(String template, int dropPercent, int bossDropPercent, float pickupRange,
        int valuePercentPerDepth, int noteFrames) {

    /** What a block leaves out. */
    public static final LootDrops DEFAULTS = new LootDrops("", 20, 100, 14f, 20, 90);
}
