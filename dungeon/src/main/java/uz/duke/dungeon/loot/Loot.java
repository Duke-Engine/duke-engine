package uz.duke.dungeon.loot;

/**
 * One thing that can be found on a dungeon floor, as the data file describes it.
 *
 * <p>Data and nothing else, like {@link uz.duke.dungeon.power.Power} beside it: a
 * new item is a block in {@code dungeon.ini} and no Java at all.
 *
 * <p>{@code name} is finished words — the client writes none of its own, here as
 * everywhere else — and for the same reason it may not carry the two characters
 * the status line separates its fields with.
 *
 * @param id       what the block is headed by; never seen by the player
 * @param name     what the message says he picked up, in the game's own language
 * @param kind     which of the three figures it moves
 * @param value    percent for {@code ATTACK} and {@code ARMOUR}, flat health for
 *                 {@code HEALTH} — the same convention {@code DungeonLeveling}
 *                 uses, because the engine's hooks are multipliers for two of
 *                 them and an amount for the third
 * @param weight   how often it is the one that drops; zero is never
 * @param minDepth the floor below which it is not found at all, which is what
 *                 makes going deeper worth the monsters
 */
public record Loot(String id, String name, LootKind kind, int value, int weight, int minDepth) {
}
