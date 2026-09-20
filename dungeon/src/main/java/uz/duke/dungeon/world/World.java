package uz.duke.dungeon.world;

import uz.duke.core.thing.Layered;
import uz.duke.core.thing.WorldTemplate;

/**
 * The world as the engine reads it: its name, and how far apart two storeys stand — every map this
 * world lays, the first floor and each one after it, stands its storeys that far apart. Everything
 * else of the world is the game's own, in blocks of their own.
 */
public record World(String name, float levelHeight) implements WorldTemplate, Layered {

    /** What a block leaves out. */
    public static final World DEFAULTS = new World("Dungeon", 6f);
}
