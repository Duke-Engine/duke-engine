package uz.duke.dungeon.content;

import uz.duke.core.thing.Layered;

/**
 * The dungeon's {@code World} block as the engine reads it: its name, and how tall a storey
 * stands. Every other section of the block is the dungeon's own, read into
 * {@link DungeonSettings}.
 */
public record DungeonWorld(String name, float levelHeight) implements Layered {
}
