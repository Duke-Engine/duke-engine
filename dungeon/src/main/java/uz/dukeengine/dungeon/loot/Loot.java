package uz.dukeengine.dungeon.loot;

/**
 * One thing that can be found on a dungeon floor, as the data file describes it.
 *
 * <p>Data and nothing else: a new item is a LootItem block in {@code data/world/world.duke} and no
 * Java at all.
 *
 * <p>{@code name} is finished words — the client writes none of its own, here as
 * everywhere else — and for the same reason it may not carry the two characters
 * the status line separates its fields with.
 *
 * @param id        what the block is headed by; never seen by the player
 * @param name      what the message says he picked up, in the game's own language
 * @param icon      the drawing the panel puts in his bag, by the name the client
 *                  knows it under
 * @param kind      which figure it moves
 * @param value     percent for {@code ATTACK} and {@code ARMOUR}, flat health or mana
 *                  for {@code HEALTH} and {@code MANA}, whole points for
 *                  {@code ATTRIBUTE}
 * @param weight    how often it is the one that drops; zero is never
 * @param minDepth  the floor below which it is not found at all, which is what
 *                  makes going deeper worth the monsters
 * @param attribute which attribute an {@code ATTRIBUTE} item gives, as a hero's block
 *                  names it; empty for every other kind
 */
public record Loot(String id, String name, String icon, LootKind kind, int value, int weight,
        int minDepth, String attribute) {

    /** An item that gives no attribute, which is every kind but {@code ATTRIBUTE}. */
    public Loot(String id, String name, String icon, LootKind kind, int value, int weight,
            int minDepth) {
        this(id, name, icon, kind, value, weight, minDepth, "");
    }
}
