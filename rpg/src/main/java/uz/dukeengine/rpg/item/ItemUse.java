package uz.dukeengine.rpg.item;

/**
 * What a thing in the bag does when it is used: a left click on it there, and then a click on what it is used on.
 *
 * <p>A thing that does nothing only counts while it is carried, which is all the dungeon leaves at random. The key is
 * the first that does something; a blink and illusions are to come on the same line of a {@code LootItem} block.
 */
public enum ItemUse {

    /** Nothing: it counts while it is carried, and a left click on it does nothing. */
    NONE,

    /** It opens the keep's gate, given to it, and is gone; on anything else it does nothing, and he keeps it. */
    UNLOCK
}
