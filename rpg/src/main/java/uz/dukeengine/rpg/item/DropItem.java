package uz.dukeengine.rpg.item;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;

/**
 * "Put that down there": a player's hero sent to lay what is in one slot of his bag on the floor. See
 * {@link ItemErrand}.
 *
 * @param slot  which of his bag's slots, from 0
 * @param place where on the floor
 */
public record DropItem(int playerIndex, int slot, Coord3D place) implements Command {
}
